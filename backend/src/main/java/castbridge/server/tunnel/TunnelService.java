package castbridge.server.tunnel;

import castbridge.server.config.CastbridgeProperties;
import castbridge.server.licenses.DeviceIdentity;
import castbridge.server.licenses.EnvelopeVerifier;
import castbridge.server.licenses.TrustedKeys;
import castbridge.server.licenses.WireActivation;
import castbridge.server.web.ApiException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Remote administration by SSH reverse tunnel (docs/REMOTE-TUNNEL.md): enrolment of a CastbridgeTV (activation verified like the TV does, one stable port per device),
 * the authorized_keys files of the host sshd, the probe of the tunnel ports, revocation. The server never holds a TV's or an expert's PRIVATE key.
 */
@Service
public class TunnelService {
    private static final Logger log = LoggerFactory.getLogger(TunnelService.class);
    public static final String TV_HEADER = "CastBridge-TV : tunnels inverses (généré par le serveur, ne pas modifier à la main)";
    public static final String EXPERTS_HEADER = "CastBridge : accès des experts aux ports des tunnels (généré par le serveur, ne pas modifier à la main)";

    private final TunnelProperties props;
    private final CastbridgeProperties cb;
    private final JdbcTemplate jdbc;
    private final TrustedKeys trusted;
    private final PortProbe probe;
    private final ConcurrentHashMap<String, long[]> attempts = new ConcurrentHashMap<>();
    private final Object lock = new Object();

    public TunnelService(TunnelProperties props, CastbridgeProperties cb, JdbcTemplate jdbc, TrustedKeys trusted, PortProbe probe) {
        this.props = props;
        this.cb = cb;
        this.jdbc = jdbc;
        this.trusted = trusted;
        this.probe = probe;
    }

    public boolean enabled() { return props.enabled(); }

    public void requireEnabled() {
        if (!props.enabled()) throw ApiException.notFound("L'administration à distance n'est pas activée sur ce serveur");
    }

    public TunnelProperties props() { return props; }

    // ------------------------------------------------------------------ files

    public Path authorizedKeysFile() { return props.authorizedKeysFile() != null ? props.authorizedKeysFile() : cb.storageDir().resolve("tunnel").resolve("authorized_keys"); }

    public Path expertsFile() { return props.expertsFile() != null ? props.expertsFile() : cb.storageDir().resolve("tunnel").resolve("experts.json"); }

    public Path expertsAuthorizedKeysFile() {
        return props.expertsAuthorizedKeysFile() != null ? props.expertsAuthorizedKeysFile() : cb.storageDir().resolve("tunnel").resolve("experts_authorized_keys");
    }

    private record Row(String code, int port, String key, String license, String edition, String state, Instant enrolledAt, Instant lastSeen, Instant lastProbe, boolean online) {}

    private List<Row> rows() {
        return jdbc.query("SELECT device_code, port, ssh_public_key, license_id, edition, state, enrolled_at, last_seen_at, last_probe_at, is_online FROM tunnel_device ORDER BY port",
                (rs, i) -> new Row(rs.getString(1), rs.getInt(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getTimestamp(7).toInstant(),
                        rs.getTimestamp(8) == null ? null : rs.getTimestamp(8).toInstant(), rs.getTimestamp(9) == null ? null : rs.getTimestamp(9).toInstant(), rs.getBoolean(10)));
    }

    /** Rewrites both authorized_keys files from the database (atomic; untouched when identical). Called after every change and on a schedule (expired experts). */
    public void regenerate() throws IOException {
        List<Row> active = rows().stream().filter(r -> r.state().equals("ACTIVE")).toList();
        List<String> tv = active.stream().map(r -> AuthorizedKeys.tvLine(r.port(), r.key(), r.code())).toList();
        AuthorizedKeys.writeAtomic(authorizedKeysFile(), AuthorizedKeys.file(TV_HEADER, tv));

        long now = System.currentTimeMillis();
        List<Integer> ports = active.stream().map(Row::port).toList();
        List<String> experts = new ArrayList<>();
        jdbc.query("SELECT expert_id, ssh_public_key, not_after FROM tunnel_expert ORDER BY expert_id", rs -> {
            long notAfter = rs.getLong(3);
            if (notAfter != 0 && notAfter <= now) return;
            String line = AuthorizedKeys.expertLine(rs.getString(1), rs.getString(2), ports);
            if (line.length() > AuthorizedKeys.OLD_SSHD_LINE_LIMIT - 512)
                log.warn("tunnel: the authorized_keys line of an expert is {} bytes (older sshd ignore lines over {}): see docs/REMOTE-TUNNEL.md « Limites »", line.length(), AuthorizedKeys.OLD_SSHD_LINE_LIMIT);
            experts.add(line);
        });
        AuthorizedKeys.writeAtomic(expertsAuthorizedKeysFile(), AuthorizedKeys.file(EXPERTS_HEADER, experts));
    }

    @EventListener(ApplicationReadyEvent.class)
    void onReady() {
        if (!props.enabled()) return;
        try {
            regenerate();
        } catch (IOException | RuntimeException e) {
            log.warn("tunnel: authorized_keys not written at start-up: {}", e.toString());
        }
    }

    // ------------------------------------------------------------------ enrolment

    public record EnrollRequest(String activation, String sshPublicKey, String deviceCode) {}

    public record Enrolled(String host, int sshPort, String user, int port, String hostKeyFingerprint, boolean created, boolean keyRotated) {
        public Map<String, Object> wire() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("host", host);
            m.put("sshPort", sshPort);
            m.put("user", user);
            m.put("port", port);
            if (hostKeyFingerprint != null && !hostKeyFingerprint.isBlank()) m.put("hostKeyFingerprint", hostKeyFingerprint);
            return m;
        }
    }

    /** One enrolment attempt per call counted per IP over a sliding hour. */
    void limit(String ip) {
        long now = System.currentTimeMillis();
        if (attempts.size() > 50_000) attempts.values().removeIf(a -> now - a[1] > 3_600_000L);
        long[] a = attempts.compute(ip == null ? "?" : ip, (k, v) -> {
            if (v == null || now - v[1] > 3_600_000L) return new long[] {1, now};
            v[0]++;
            return v;
        });
        if (a[0] > props.enrollPerHour()) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Trop de demandes d'enrôlement depuis cette adresse : réessayez dans une heure");
    }

    private static String why(EnvelopeVerifier.Reason r) {
        return switch (r) {
            case MALFORMED, UNKNOWN_TYPE -> "activation illisible";
            case UNKNOWN_KEY -> "clé de signature inconnue";
            case REVOKED_KEY -> "clé de signature révoquée";
            case BAD_SIGNATURE -> "signature invalide";
            case REVOKED_SEAT -> "poste révoqué";
            case WRONG_SUBJECT -> "activation qui n'est pas celle d'une TV";
            case KEY_NOT_ALLOWED, BAD_RIGHTS -> "activation non conforme à la clé qui l'a signée";
            default -> "activation non valide";
        };
    }

    private EnvelopeVerifier verifier() {
        EnvelopeVerifier.Ring ring = new EnvelopeVerifier.Ring();
        for (TrustedKeys.Key k : trusted.all()) ring.add(new EnvelopeVerifier.TrustedKey(k.kid(), k.publicKey(), k.scopes()));
        Set<String> keys = new HashSet<>();
        Map<String, Long> seats = new TreeMap<>();
        jdbc.query("SELECT license_id, seat_id, kid, revoked_at FROM lic_revocation LIMIT 50000", rs -> {
            if (rs.getString("kid") != null) keys.add(rs.getString("kid"));
            else seats.merge(rs.getString("license_id") + "|" + rs.getString("seat_id"), rs.getTimestamp("revoked_at").getTime(), Math::max);
        });
        return new EnvelopeVerifier(ring, new EnvelopeVerifier.Revocations(keys, seats), new EnvelopeVerifier.SeqState(), "tv");
    }

    public Enrolled enroll(EnrollRequest req, String ip) {
        requireEnabled();
        limit(ip);
        if (req == null || req.activation() == null || req.sshPublicKey() == null || req.deviceCode() == null) throw ApiException.badRequest("Demande incomplète : activation, sshPublicKey et deviceCode sont attendus");
        if (req.activation().length() > 8192) throw ApiException.badRequest("Activation trop longue");
        String code = DeviceIdentity.parseCode(req.deviceCode());
        if (code == null) throw ApiException.badRequest("Code d'appareil invalide : 16 caractères au format XXXX-XXXX-XXXX-XXXX, avec son caractère de contrôle");
        String key = SshKeys.normalize(req.sshPublicKey());
        if (key == null) throw ApiException.badRequest("Clé publique SSH invalide : seule une clé « ssh-ed25519 AAAA… » est acceptée");

        WireActivation.Decoded d = WireActivation.decode(req.activation());
        if (d == null) throw new ApiException(HttpStatus.FORBIDDEN, "Activation refusée : activation illisible");
        WireActivation.Fields a = d.fields();
        // the installation window of an activation is only for installing it: a tunnel is enrolled later. Everything else is checked as the TV does.
        EnvelopeVerifier.Result r = verifier().verifyActivation(req.activation(), a.factors(), Math.max(a.issuedAt(), a.notBefore()));
        if (!r.accepted()) {
            audit("ENROLL_REFUSED", code, "device", "raison " + r.reason());
            throw new ApiException(HttpStatus.FORBIDDEN, "Activation refusée : " + why(r.reason()));
        }
        if (!DeviceIdentity.code(a.factors()).equals(code)) {
            audit("ENROLL_REFUSED", code, "device", "code d'appareil différent de celui de l'activation");
            throw new ApiException(HttpStatus.FORBIDDEN, "Activation refusée : elle ne correspond pas à ce code d'appareil");
        }

        Enrolled out;
        synchronized (lock) {
            out = enrollLocked(code, key, a.license(), a.kind());
        }
        try {
            regenerate();
        } catch (IOException | RuntimeException e) {
            log.error("tunnel: authorized_keys not written after an enrolment: {}", e.toString());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Enrôlement enregistré mais fichier des clés non écrit : réessayez dans un instant");
        }
        return out;
    }

    private Enrolled enrollLocked(String code, String key, String license, String edition) {
        Instant now = Instant.now();
        List<Map<String, Object>> existing = jdbc.queryForList("SELECT port, ssh_public_key, state FROM tunnel_device WHERE device_code = ?", code);
        if (!existing.isEmpty()) {
            Map<String, Object> e = existing.get(0);
            int port = ((Number) e.get("port")).intValue();
            if ("REVOKED".equals(e.get("state"))) {
                audit("ENROLL_REFUSED", code, "device", "tunnel révoqué");
                throw new ApiException(HttpStatus.FORBIDDEN, "Le tunnel de cet appareil a été désactivé par l'administrateur");
            }
            boolean rotated = !key.equals(e.get("ssh_public_key"));
            jdbc.update("UPDATE tunnel_device SET ssh_public_key = ?, license_id = ?, edition = ?, key_updated_at = CASE WHEN ? THEN ? ELSE key_updated_at END WHERE device_code = ?",
                    key, license, edition, rotated, Timestamp.from(now), code);
            if (rotated) audit("KEY_ROTATED", code, "device", "port " + port);
            return result(port, false, rotated);
        }
        int[] range = props.range();
        Set<Integer> used = new HashSet<>(jdbc.queryForList("SELECT port FROM tunnel_device", Integer.class));
        int port = -1;
        for (int p = range[0]; p <= range[1]; p++) if (!used.contains(p)) { port = p; break; }
        if (port < 0) {
            audit("ENROLL_REFUSED", code, "device", "plage de ports épuisée");
            log.error("tunnel: port range {}-{} exhausted", range[0], range[1]);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Plus de port disponible pour de nouveaux tunnels : contactez l'administrateur");
        }
        try {
            jdbc.update("INSERT INTO tunnel_device (device_code, port, ssh_public_key, license_id, edition, state, enrolled_at, key_updated_at, is_online) VALUES (?,?,?,?,?,'ACTIVE',?,?,FALSE)",
                    code, port, key, license, edition, Timestamp.from(now), Timestamp.from(now));
        } catch (DuplicateKeyException e) {
            throw new ApiException(HttpStatus.CONFLICT, "Enrôlement concurrent : réessayez");
        }
        audit("ENROLLED", code, "device", "port " + port + " édition " + edition);
        return result(port, true, false);
    }

    private Enrolled result(int port, boolean created, boolean rotated) {
        return new Enrolled(props.host(), props.sshPort(), "cbtunnel", port, hostKeyFingerprint(), created, rotated);
    }

    String hostKeyFingerprint() {
        String v = props.hostKeyFingerprint().trim();
        if (v.isEmpty()) return "";
        try {
            Path f = Path.of(v);
            if (Files.isRegularFile(f)) v = Files.readString(f).lines().map(String::trim).filter(s -> !s.isEmpty() && !s.startsWith("#")).findFirst().orElse("");
        } catch (IOException | RuntimeException e) {
            // a literal fingerprint
        }
        return v.matches("SHA256:[A-Za-z0-9+/]{43}=*") ? v : "";
    }

    // ------------------------------------------------------------------ admin

    public record View(String deviceCode, String licenseId, String edition, int port, String state, boolean online, Instant lastSeen, Instant lastProbe, Instant enrolledAt, String command) {}

    public record Counters(int total, int active, int online, int offline, int revoked, int freePorts) {}

    public List<View> views() {
        return rows().stream().map(r -> new View(r.code(), r.license(), r.edition(), r.port(), r.state(), r.online(), r.lastSeen(), r.lastProbe(), r.enrolledAt(), sshCommand(r.port()))).toList();
    }

    public Counters counters() {
        List<Row> rs = rows();
        int active = (int) rs.stream().filter(r -> r.state().equals("ACTIVE")).count();
        int online = (int) rs.stream().filter(r -> r.state().equals("ACTIVE") && r.online()).count();
        int[] range = props.range();
        return new Counters(rs.size(), active, online, active - online, rs.size() - active, Math.max(0, range[1] - range[0] + 1 - rs.size()));
    }

    /** The command an expert (or the owner) types: a jump through the server's restricted account, then the TV's own sshd on its tunnel port. */
    public String sshCommand(int port) { return "ssh -J " + props.expertUser() + "@" + props.host() + ":" + props.sshPort() + " -p " + port + " tv@127.0.0.1"; }

    /** Probes every active tunnel port (or one device); records the time of the last success. Returns the number of tunnels found connected. */
    public int probeAll() { return probe(null); }

    public int probe(String onlyCode) {
        var up = probe.snapshot();
        int online = 0;
        Timestamp now = Timestamp.from(Instant.now());
        for (Row r : rows()) {
            if (!r.state().equals("ACTIVE") || (onlyCode != null && !r.code().equals(onlyCode))) continue;
            boolean on = up.test(r.port());
            if (on) {
                online++;
                jdbc.update("UPDATE tunnel_device SET is_online = TRUE, last_probe_at = ?, last_seen_at = ? WHERE device_code = ?", now, now, r.code());
            } else {
                jdbc.update("UPDATE tunnel_device SET is_online = FALSE, last_probe_at = ? WHERE device_code = ?", now, r.code());
            }
        }
        return online;
    }

    @Scheduled(initialDelayString = "${castbridge.tunnel.probe-seconds:60}", fixedDelayString = "${castbridge.tunnel.probe-seconds:60}", timeUnit = java.util.concurrent.TimeUnit.SECONDS)
    void scheduledProbe() {
        if (!props.enabled()) return;
        try {
            probeAll();
        } catch (RuntimeException e) {
            log.warn("tunnel: probe failed: {}", e.toString());
        }
    }

    /** Kill switch for one TV: its key line disappears (and its port from the experts' lines); the port stays reserved for the device. */
    public void revoke(String rawCode, String actor) {
        requireEnabled();
        String code = DeviceIdentity.normalize(rawCode);
        synchronized (lock) {
            int n = jdbc.update("UPDATE tunnel_device SET state = 'REVOKED', revoked_at = ?, is_online = FALSE WHERE device_code = ? AND state = 'ACTIVE'", Timestamp.from(Instant.now()), code);
            if (n == 0) throw ApiException.notFound("Aucun tunnel actif pour cet appareil");
            audit("REVOKED", code, actor, "");
        }
        writeFiles("révocation");
    }

    /** Lifts a revocation (the device must enrol again or simply reconnect with its key: the line comes back). */
    public void restore(String rawCode, String actor) {
        requireEnabled();
        String code = DeviceIdentity.normalize(rawCode);
        synchronized (lock) {
            int n = jdbc.update("UPDATE tunnel_device SET state = 'ACTIVE', revoked_at = NULL WHERE device_code = ? AND state = 'REVOKED'", code);
            if (n == 0) throw ApiException.notFound("Aucun tunnel révoqué pour cet appareil");
            audit("RESTORED", code, actor, "");
        }
        writeFiles("rétablissement");
    }

    private void writeFiles(String what) {
        try {
            regenerate();
        } catch (IOException | RuntimeException e) {
            log.error("tunnel: authorized_keys not written after {}: {}", what, e.toString());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Changement enregistré mais fichier des clés non écrit : vérifiez le dossier du tunnel");
        }
    }

    // ------------------------------------------------------------------ audit

    /** Identifiers only: never a key, an activation or a full device code in the log line (the table keeps the code for the console). */
    void audit(String event, String code, String actor, String detail) {
        jdbc.update("INSERT INTO tunnel_audit (at, event, device_code, actor, detail) VALUES (?,?,?,?,?)", Timestamp.from(Instant.now()), event, code,
                actor == null ? "?" : actor.length() > 64 ? actor.substring(0, 64) : actor, detail.length() > 250 ? detail.substring(0, 250) : detail);
        log.info("tunnel audit event={} device={} actor={} {}", event, DeviceIdentity.masked(code), actor, detail);
    }

    public record AuditRow(Instant at, String event, String deviceCode, String actor, String detail) {}

    public List<AuditRow> recentAudit(int limit) {
        return jdbc.query("SELECT at, event, device_code, actor, detail FROM tunnel_audit ORDER BY id DESC LIMIT ?",
                (rs, i) -> new AuditRow(rs.getTimestamp(1).toInstant(), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5)), limit);
    }
}
