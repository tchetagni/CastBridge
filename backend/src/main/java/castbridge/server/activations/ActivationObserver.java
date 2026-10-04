package castbridge.server.activations;

import castbridge.server.common.Times;
import castbridge.server.licenses.DeviceIdentity;
import castbridge.server.licenses.Envelope;
import castbridge.server.licenses.Hashing;
import castbridge.server.licenses.LicenseKeyring;
import castbridge.server.licenses.TrustedKeys;
import castbridge.server.licenses.WireActivation;
import castbridge.server.web.ApiException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a TV says it carries (design 3.3): the activations installed on it, the owner commands still active, its version. Each token is VERIFIED (key of the ring, signature,
 * the device the token targets), reduced to its SHA-256 fingerprint and its parsed fields, then THROWN AWAY: no token is ever stored or logged. A report writes events only
 * when something changed. Public entry point shared by the report route, the courier phone (w23-04) and, later, the wallet synchronisation (W22): {@code observe(deviceId,
 * tokens, state, via)}. A report never creates nor removes a right: it can only inform and raise soft alerts.
 */
@Service
public class ActivationObserver {
    private static final Pattern HEX8 = Pattern.compile("[0-9a-f]{8}");
    private static final Set<String> EDITIONS = Set.of("TRIAL", "PRODUCTION", "SUPER", "NONE", "ENDED");
    private static final Set<String> POWERS = Set.of("support", "unlock", "open_all");
    private static final long HOUR_MS = 3_600_000L, DAY_MS = 86_400_000L;

    public record Cmd(String power, String challenge, long atMs, int days) {}

    /** @param installedAt installation times the TV knows, by the 8 first hex of the fingerprint */
    public record ReportState(String deviceCode, Integer appCode, String appName, String edition, Long usageTo, boolean superFlag, long openAllUntil, long unlockUntil, int trialResets,
                              Map<String, Long> installedAt, List<Cmd> commands, long atMs, List<String> compact) {}

    public record Observation(int accepted, int ignored) {}

    private final JdbcTemplate jdbc;
    private final ActClock clock;
    private final EventLog log;
    private final Inventory inventory;
    private final TvRef tvRef;
    private final TrustedKeys trusted;
    private final AlertService alerts;
    private final Reconciler reconciler;

    public ActivationObserver(JdbcTemplate jdbc, ActClock clock, EventLog log, Inventory inventory, TvRef tvRef, TrustedKeys trusted, AlertService alerts, Reconciler reconciler) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.log = log;
        this.inventory = inventory;
        this.tvRef = tvRef;
        this.trusted = trusted;
        this.alerts = alerts;
        this.reconciler = reconciler;
    }

    /**
     * @param deviceId the API installation that reported (device.id), bound to the device code
     * @param tokens   the installed activations, the current one first (≤ 4)
     * @param via      direct, courier or gateway
     */
    @Transactional
    public Observation observe(long deviceId, List<String> tokens, ReportState state, String via) {
        String code = TvRef.canonical(state.deviceCode());
        if (code == null) throw ApiException.badRequest("Code d'appareil invalide : 16 caractères au format XXXX-XXXX-XXXX-XXXX, avec son caractère de contrôle");
        String ref = tvRef.of(code);
        String route = via.equals("courier") ? "COURIER" : "REPORT";
        Instant now = clock.now();
        inventory.ensureTv(ref, code);
        List<String> fps = new ArrayList<>(), licenses = new ArrayList<>();
        List<String> acceptedFps = new ArrayList<>();
        int ignored = 0;

        linkDevice(ref, deviceId, route, now);

        for (String token : tokens) {
            String fp = accept(token, code, ref, state, via, now);
            if (fp == null) ignored++;
            else {
                acceptedFps.add(fp);
                fps.add(fp);
                String lic = jdbc.queryForList("SELECT license_id FROM act_key WHERE fp = ?", String.class, fp).get(0);
                if (lic != null) licenses.add(lic);
            }
        }
        for (String c : state.compact() == null ? List.<String>of() : state.compact()) {
            String fp = acceptCompact(c, code, ref, via, now);
            if (fp == null) ignored++;
            else {
                acceptedFps.add(fp);
                fps.add(fp);
            }
        }
        // a second installation with another hardware id behind the same code, in 30 days: a copy
        cloneByInstallation(ref, acceptedFps.isEmpty() ? null : acceptedFps.get(0), now);

        updateTv(ref, state, acceptedFps.isEmpty() ? null : acceptedFps.get(0), via, route, now);
        commands(ref, state, route, now);
        jdbc.update("INSERT INTO act_report (device_id, tv_ref, received_at, via, sha, app_code, n_activations) VALUES (?,?,?,?,?,?,?)", deviceId, ref, Timestamp.from(now), via, reportHash(state, fps),
                state.appCode(), acceptedFps.size());
        reconciler.reconcile(Reconciler.Scope.of(fps, List.of(ref), null, licenses));
        return new Observation(acceptedFps.size(), ignored);
    }

    // ------------------------------------------------------------------ the device link and the clones

    private void linkDevice(String ref, long deviceId, String route, Instant now) {
        Timestamp ts = Timestamp.from(now);
        int n = jdbc.update("UPDATE act_tv_device SET last_at = ? WHERE tv_ref = ? AND device_id = ?", ts, ref, deviceId);
        if (n == 0) {
            jdbc.update("INSERT INTO act_tv_device (tv_ref, device_id, first_at, last_at) VALUES (?,?,?,?)", ref, deviceId, ts, ts);
            log.append(new EventLog.NewEvent("DEVICE_LINKED", now.toEpochMilli(), null, ref, null, null, "TV", "tv", route, null, null, "D:" + ref + ":" + deviceId));
        }
        jdbc.update("UPDATE act_tv SET api_devices = (SELECT COUNT(*) FROM act_tv_device WHERE tv_ref = ?), android_ids = (SELECT COUNT(DISTINCT d.android_id_hash) FROM act_tv_device x JOIN device d ON d.id = x.device_id"
                + " WHERE x.tv_ref = ? AND d.android_id_hash IS NOT NULL) WHERE tv_ref = ?", ref, ref, ref);
    }

    private void cloneByInstallation(String ref, String fp, Instant now) {
        Integer ids = jdbc.queryForObject("SELECT COUNT(DISTINCT d.android_id_hash) FROM act_tv_device x JOIN device d ON d.id = x.device_id WHERE x.tv_ref = ? AND x.last_at >= ? AND d.android_id_hash IS NOT NULL",
                Integer.class, ref, Timestamp.from(now.minus(Duration.ofDays(30))));
        if (ids != null && ids > 1) {
            alerts.raise(AlertService.Type.CLONE, fp, ref, null, null, "Le même code d'appareil est rapporté par " + ids + " installations de matériels différents en 30 jours", "ids:" + ids);
            if (fp != null) inventory.addFlags(fp, Set.of(Inventory.CLONE));
        }
    }

    // ------------------------------------------------------------------ one token

    /** @return the fingerprint of the accepted activation, or null when the token was ignored (an alert says why) */
    private String accept(String token, String code, String ref, ReportState state, String via, Instant now) {
        String fp = Hashing.sha256Hex(token.getBytes(StandardCharsets.UTF_8));
        Envelope env = Envelope.decode(token);
        WireActivation.Fields f = env == null ? null : WireActivation.fieldsOf(env);
        if (f == null) {
            alerts.raise(AlertService.Type.BAD_TOKEN, null, ref, null, null, "Jeton d'activation malformé rapporté par la TV (ignoré)", fp.substring(0, 8));
            return null;
        }
        TrustedKeys.Key key = trusted.find(f.kid());
        if (key == null) {
            alerts.raise(AlertService.Type.UNKNOWN_KEY, fp, ref, f.kid(), null, "Activation signée par une clé hors de l'anneau du serveur (ignorée)", fp.substring(0, 8));
            return null;
        }
        boolean signed;
        try {
            signed = LicenseKeyring.verify(key.publicKey(), env.payload().getBytes(StandardCharsets.UTF_8), Base64.getDecoder().decode(env.signature()));
        } catch (RuntimeException e) {
            signed = false;
        }
        if (!signed) {
            alerts.raise(AlertService.Type.BAD_TOKEN, fp, ref, f.kid(), null, "Signature fausse sur une activation rapportée par la TV (ignorée)", fp.substring(0, 8));
            return null;
        }
        // the token must be for THIS device: its factors give the device code (or the activation was already bound to this TV by a tool: hardware changed since)
        boolean mine = DeviceIdentity.code(f.factors()).equals(code);
        if (!mine) {
            List<String> bound = jdbc.queryForList("SELECT tv_ref FROM act_key WHERE fp = ?", String.class, fp);
            mine = !bound.isEmpty() && ref.equals(bound.get(0));
        }
        if (!mine) {
            alerts.raise(AlertService.Type.CLONE, fp, ref, f.kid(), null, "Activation d'un autre appareil rapportée par cette TV : copie probable (ignorée)", "dev:" + ref.substring(0, 8));
            inventory.addFlags(fp, Set.of(Inventory.CLONE));
            return null;
        }
        Instant issued = Instant.ofEpochMilli(f.issuedAt());
        Instant usageTo = usageEnd(f);
        boolean unlimited = f.kind().equals("production") && f.rights().stream().noneMatch(WireActivation::isUsage);
        Set<String> flags = new TreeSet<>(Set.of(Inventory.SEEN_ON_TV));
        long window = f.notAfter() - f.notBefore();
        Long installed = state.installedAt() == null ? null : state.installedAt().get(fp.substring(0, 8));
        boolean outOfWindow = window > 48 * HOUR_MS || (installed != null && installed > f.notAfter() + 24 * HOUR_MS);
        if (outOfWindow) flags.add(Inventory.OUT_OF_WINDOW);
        inventory.upsertKey(new Inventory.KeyFacts(fp, "ENVELOPE", f.kid(), f.kind().toUpperCase(Locale.ROOT), f.subject(), f.license(), f.seat(), ref, f.k(), f.seq(), f.nonce(), issued,
                Instant.ofEpochMilli(f.notBefore()), Instant.ofEpochMilli(f.notAfter()), usageTo, usageTo == null ? (unlimited ? Boolean.TRUE : null) : Boolean.FALSE,
                f.rights().stream().anyMatch(WireActivation::isSuper), rightsSummary(f)), flags);
        jdbc.update("UPDATE act_key SET first_seen_tv_at = COALESCE(first_seen_tv_at, ?), last_seen_tv_at = CASE WHEN last_seen_tv_at IS NULL OR last_seen_tv_at < ? THEN ? ELSE last_seen_tv_at END,"
                + " last_seen_via = ?, installed_at = COALESCE(installed_at, ?) WHERE fp = ?", Timestamp.from(now), Timestamp.from(now), Timestamp.from(now), via,
                installed == null ? null : new Timestamp(installed), fp);
        if (outOfWindow) {
            alerts.raise(AlertService.Type.OUT_OF_WINDOW, fp, ref, f.kid(), null, window > 48 * HOUR_MS ? "Fenêtre d'installation de plus de 48 h" : "Activation installée après la fermeture de sa fenêtre de 48 h",
                    window > 48 * HOUR_MS ? "long" : "late");
        }
        return fp;
    }

    /** End of the usage ceiling: the `usage` right, else the implicit 30 days of a trial, else none (production without usage is unlimited). */
    static Instant usageEnd(WireActivation.Fields f) {
        for (String r : f.rights()) {
            if (WireActivation.isUsage(r)) {
                String[] p = r.split("\\|", -1);
                return Instant.ofEpochMilli(Long.parseLong(p[3]));
            }
        }
        Long implicit = WireActivation.implicitUsageEnd(f);
        return implicit == null ? null : Instant.ofEpochMilli(implicit);
    }

    /** « purchase:1,subscription:2 »: the kinds of rights and their number, no commercial detail. */
    static String rightsSummary(WireActivation.Fields f) {
        Map<String, Integer> n = new TreeMap<>();
        for (String r : f.rights()) {
            String kind = r.contains("|") ? r.substring(0, r.indexOf('|')) : r;
            n.merge(kind, 1, Integer::sum);
        }
        if (n.isEmpty()) return null;
        List<String> parts = new ArrayList<>();
        n.forEach((k, v) -> parts.add(k + ":" + v));
        return Chains.clip(String.join(",", parts), 200);
    }

    // ------------------------------------------------------------------ the compact key (82 bytes) a TV may keep

    private String acceptCompact(String b64, String code, String ref, String via, Instant now) {
        byte[] b;
        try {
            b = Base64.getDecoder().decode(b64);
        } catch (RuntimeException e) {
            b = null;
        }
        String fp = b == null ? Hashing.sha256Hex(b64.getBytes(StandardCharsets.UTF_8)) : Hashing.sha256Hex(b);
        if (b == null || b.length != 82 || (b[0] != 1 && b[0] != 2)) {
            alerts.raise(AlertService.Type.BAD_TOKEN, null, ref, null, null, "Clé compacte malformée rapportée par la TV (ignorée)", fp.substring(0, 8));
            return null;
        }
        byte[] bind = java.util.Arrays.copyOfRange(Hashing.sha256(("castbridge-bind|" + code).getBytes(StandardCharsets.UTF_8)), 0, 8);
        if (!java.util.Arrays.equals(bind, java.util.Arrays.copyOfRange(b, 10, 18))) {
            alerts.raise(AlertService.Type.CLONE, fp, ref, null, null, "Clé compacte d'un autre appareil rapportée par cette TV : copie probable (ignorée)", "dev:" + ref.substring(0, 8));
            return null;
        }
        String signer = null;
        byte[] header = java.util.Arrays.copyOfRange(b, 0, 18), sig = java.util.Arrays.copyOfRange(b, 18, 82);
        for (TrustedKeys.Key k : trusted.all()) {
            byte[] tag = java.util.Arrays.copyOfRange(Hashing.sha256(k.kid().getBytes(StandardCharsets.US_ASCII)), 0, 2);
            if (tag[0] == b[2] && tag[1] == b[3]
                    && LicenseKeyring.verify(k.publicKey(), ("castbridge-activation-compact-v1\n" + HexFormat.of().formatHex(header)).getBytes(StandardCharsets.US_ASCII), sig)) {
                signer = k.kid();
                break;
            }
        }
        if (signer == null) {
            alerts.raise(AlertService.Type.UNKNOWN_KEY, fp, ref, null, null, "Clé compacte non signée par une clé de l'anneau du serveur (ignorée)", fp.substring(0, 8));
            return null;
        }
        int startUnits = ((b[4] & 0xff) << 8) | (b[5] & 0xff), lenUnits = ((b[6] & 0xff) << 8) | (b[7] & 0xff);
        long unit = b[0] == 2 ? HOUR_MS : DAY_MS;
        Instant start = Instant.ofEpochMilli(JournalService.EPOCH_2026_MS + startUnits * unit);
        Instant end = start.plusMillis(lenUnits * unit);
        inventory.upsertKey(new Inventory.KeyFacts(fp, "COMPACT", signer, b[1] == 0 ? "TRIAL" : "PRODUCTION", "tv", null, null, ref, null, null, null, start, start, end, null, null, false,
                "set:" + (((b[8] & 0xff) << 8) | (b[9] & 0xff))), Set.of(Inventory.SEEN_ON_TV));
        jdbc.update("UPDATE act_key SET first_seen_tv_at = COALESCE(first_seen_tv_at, ?), last_seen_tv_at = CASE WHEN last_seen_tv_at IS NULL OR last_seen_tv_at < ? THEN ? ELSE last_seen_tv_at END, last_seen_via = ? WHERE fp = ?",
                Timestamp.from(now), Timestamp.from(now), Timestamp.from(now), via, fp);
        return fp;
    }

    // ------------------------------------------------------------------ what changed on the TV

    private void updateTv(String ref, ReportState s, String currentFp, String via, String route, Instant now) {
        Map<String, Object> old = jdbc.queryForMap("SELECT * FROM act_tv WHERE tv_ref = ?", ref);
        boolean firstReport = old.get("last_report_at") == null;
        String edition = s.edition() == null || !EDITIONS.contains(s.edition()) ? "NONE" : s.edition();
        Timestamp usageTo = s.usageTo() == null ? null : new Timestamp(s.usageTo());
        Timestamp openAll = maxTs(Times.ts(old.get("open_all_until")), s.openAllUntil()), unlock = maxTs(Times.ts(old.get("unlock_until")), s.unlockUntil());
        int oldResets = ((Number) old.get("trial_resets")).intValue();
        int resets = Math.max(oldResets, Math.max(0, s.trialResets()));
        Integer oldCode = old.get("app_code") == null ? null : ((Number) old.get("app_code")).intValue();
        String appName = s.appName() == null ? null : Chains.clip(s.appName(), 64);

        if (s.appCode() != null && !s.appCode().equals(oldCode)) {
            log.append(new EventLog.NewEvent("APP_VERSION", now.toEpochMilli(), null, ref, null, null, "TV", "tv", route, oldCode == null ? null : "{\"code\":" + oldCode + "}",
                    "{\"code\":" + s.appCode() + ",\"name\":\"" + (appName == null ? "" : appName.replaceAll("[\"\\\\]", "'")) + "\"}", "V:" + ref + ":" + s.appCode()));
        }
        for (int n = oldResets + 1; n <= Math.min(resets, oldResets + 20); n++) {
            log.append(new EventLog.NewEvent("TRIAL_RESET", now.toEpochMilli(), null, ref, null, null, "TV", "tv", route, null, "{\"n\":" + n + "}", "TR:" + ref + ":" + n));
        }
        String after = "{\"edition\":\"" + edition + "\",\"usageTo\":" + (usageTo == null ? "null" : usageTo.getTime()) + ",\"super\":" + s.superFlag() + ",\"openAll\":" + (openAll == null ? "null" : openAll.getTime())
                + ",\"unlock\":" + (unlock == null ? "null" : unlock.getTime()) + "}";
        String before = "{\"edition\":\"" + old.get("edition") + "\",\"usageTo\":" + (old.get("usage_to") == null ? "null" : Times.ms(old.get("usage_to"))) + ",\"super\":false,\"openAll\":"
                + (old.get("open_all_until") == null ? "null" : Times.ms(old.get("open_all_until"))) + ",\"unlock\":" + (old.get("unlock_until") == null ? "null" : Times.ms(old.get("unlock_until"))) + "}";
        boolean changed = !edition.equals(old.get("edition")) || !java.util.Objects.equals(usageTo, old.get("usage_to")) || !java.util.Objects.equals(openAll, old.get("open_all_until"))
                || !java.util.Objects.equals(unlock, old.get("unlock_until"));
        if (!firstReport && changed) {
            log.append(new EventLog.NewEvent("SEEN", now.toEpochMilli(), null, ref, null, null, "TV", "tv", route, before, after, "SEEN:" + ref + ":" + s.atMs() + ":" + Hashing.sha256Hex(after).substring(0, 8)));
        }
        jdbc.update("UPDATE act_tv SET edition = ?, usage_to = ?, open_all_until = ?, unlock_until = ?, trial_resets = ?, last_report_at = ?, last_report_via = ?, app_code = COALESCE(?, app_code),"
                + " app_name = COALESCE(?, app_name), current_fp = COALESCE(?, current_fp) WHERE tv_ref = ?", edition, usageTo, openAll, unlock, resets, Timestamp.from(now), via, s.appCode(), appName, currentFp, ref);
    }

    private static Timestamp maxTs(Timestamp old, long reportedMs) {
        if (reportedMs <= 0) return old;
        return old == null || old.getTime() < reportedMs ? new Timestamp(reportedMs) : old;
    }

    private void commands(String ref, ReportState s, String route, Instant now) {
        if (s.commands() == null) return;
        for (Cmd c : s.commands()) {
            if (!POWERS.contains(c.power()) || !HEX8.matcher(c.challenge()).matches()) continue;
            int n = jdbc.update("UPDATE act_command SET reported = TRUE, reported_at = COALESCE(reported_at, ?), at_ms = COALESCE(at_ms, ?), days = COALESCE(days, ?) WHERE tv_ref = ? AND challenge = ? AND reported = FALSE",
                    Timestamp.from(now), c.atMs(), c.days(), ref, c.challenge());
            boolean fresh = n > 0;
            if (n == 0 && jdbc.queryForObject("SELECT COUNT(*) FROM act_command WHERE tv_ref = ? AND challenge = ?", Integer.class, ref, c.challenge()) == 0) {
                jdbc.update("INSERT INTO act_command (tv_ref, challenge, power, at_ms, days, declared, reported, reported_at) VALUES (?,?,?,?,?,FALSE,TRUE,?)", ref, c.challenge(), c.power(), c.atMs(), c.days(), Timestamp.from(now));
                fresh = true;
            }
            if (fresh) {
                String type = switch (c.power()) {
                    case "open_all" -> "COMMAND_OPEN_ALL";
                    case "unlock" -> "COMMAND_UNLOCK";
                    default -> "COMMAND_SUPPORT";
                };
                log.append(new EventLog.NewEvent(type, c.atMs() > 0 ? c.atMs() : now.toEpochMilli(), null, ref, null, null, "TV", "tv", route, null, "{\"days\":" + c.days() + "}", "C:" + ref + ":" + c.challenge()));
            }
        }
    }

    private static String reportHash(ReportState s, List<String> fps) {
        List<String> sorted = new ArrayList<>(fps);
        java.util.Collections.sort(sorted);
        return Hashing.sha256Hex(String.join("|", String.join(",", sorted), String.valueOf(s.appCode()), String.valueOf(s.edition()), String.valueOf(s.usageTo()), String.valueOf(s.openAllUntil()),
                String.valueOf(s.unlockUntil()), String.valueOf(s.trialResets()), String.valueOf(s.atMs())));
    }
}
