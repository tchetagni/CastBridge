package castbridge.server.tunnel;

import castbridge.server.licenses.ActivationSigner.SignerScope;
import castbridge.server.licenses.LicenseKeyring;
import castbridge.server.licenses.TrustedKeys;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The experts list, signed OFFLINE by the owner (the server only relays it and checks it): {@code {"generatedAt":ms,"keyId":"…","experts":[{"id","publicKey","notAfter"}],"signature":"base64"}},
 * signed text {@code castbridge-experts-v1\ngeneratedAt=<ms>\nexpert=<id>|<publicKey>|<notAfter>…} (experts sorted by id). It is used to let the experts through the SERVER's sshd only
 * when its signature verifies with a trusted key holding the REGISTRY scope and when it is not older than the last accepted list.
 */
@Service
public class ExpertsService {
    private static final Logger log = LoggerFactory.getLogger(ExpertsService.class);
    public static final String FORMAT = "castbridge-experts-v1";
    static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,31}");

    public enum Outcome { ACCEPTED, UNCHANGED, ABSENT, REJECTED }

    public record Result(Outcome outcome, String message) {}

    private final TunnelService tunnel;
    private final TrustedKeys trusted;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final TransactionTemplate tx;

    public ExpertsService(TunnelService tunnel, TrustedKeys trusted, JdbcTemplate jdbc, ObjectMapper json, TransactionTemplate tx) {
        this.tunnel = tunnel;
        this.trusted = trusted;
        this.jdbc = jdbc;
        this.json = json;
        this.tx = tx;
    }

    /** The text that is signed. {@code experts} = id → "publicKey|notAfter" as written in the file. */
    public static String signedText(long generatedAt, TreeMap<String, String> experts) {
        List<String> l = new ArrayList<>(List.of(FORMAT, "generatedAt=" + generatedAt));
        experts.forEach((id, rest) -> l.add("expert=" + id + "|" + rest));
        return String.join("\n", l);
    }

    private String meta(String k) {
        List<String> v = jdbc.queryForList("SELECT v FROM tunnel_meta WHERE k = ?", String.class, k);
        return v.isEmpty() ? null : v.get(0);
    }

    private void setMeta(String k, String v) {
        if (jdbc.update("UPDATE tunnel_meta SET v = ? WHERE k = ?", v, k) == 0) jdbc.update("INSERT INTO tunnel_meta (k, v) VALUES (?, ?)", k, v);
    }

    private Result reject(String why) {
        log.warn("tunnel: experts list rejected: {}", why);
        tunnel.audit("EXPERTS_REJECTED", null, "system", why);
        setMeta("experts_status", "Refusée : " + why);
        setMeta("experts_checked_at", Long.toString(System.currentTimeMillis()));
        return new Result(Outcome.REJECTED, why);
    }

    /** Reads experts.json, verifies it and, if it is valid and not older than the last accepted one, stores it and rewrites the experts authorized_keys. */
    public synchronized Result refresh() {
        Path f = tunnel.expertsFile();
        if (!Files.isRegularFile(f)) {
            setMeta("experts_status", "Aucune liste publiée sur le serveur");
            return new Result(Outcome.ABSENT, "Aucune liste d'experts publiée");
        }
        JsonNode n;
        try {
            byte[] raw = Files.readAllBytes(f);
            if (raw.length > 1_000_000) return reject("fichier trop gros");
            n = json.readTree(raw);
        } catch (IOException e) {
            return reject("fichier illisible");
        }
        if (n == null || !n.path("generatedAt").canConvertToLong() || !n.path("generatedAt").isNumber() || !n.path("experts").isArray() || !n.path("signature").isTextual() || !n.path("keyId").isTextual())
            return reject("liste incomplète");
        long generatedAt = n.get("generatedAt").asLong();
        TreeMap<String, String> signed = new TreeMap<>();
        TreeMap<String, String[]> parsed = new TreeMap<>();
        for (JsonNode e : n.get("experts")) {
            if (!e.path("id").isTextual() || !e.path("publicKey").isTextual() || !e.path("notAfter").isNumber() || !e.path("notAfter").canConvertToLong()) return reject("expert mal formé");
            String id = e.get("id").asText(), pk = e.get("publicKey").asText();
            long notAfter = e.get("notAfter").asLong();
            if (!ID.matcher(id).matches() || pk.contains("\n") || pk.contains("|") || notAfter < 0) return reject("expert mal formé");
            if (signed.put(id, pk + "|" + notAfter) != null) return reject("identifiant d'expert en double");
            String key = SshKeys.normalize(pk);
            if (key == null) return reject("clé publique invalide pour l'expert " + id);
            parsed.put(id, new String[] {key, Long.toString(notAfter)});
        }
        if (parsed.size() > 200) return reject("trop d'experts");

        String kid = n.get("keyId").asText();
        TrustedKeys.Key k = trusted.find(kid);
        if (k == null || !k.allows(SignerScope.REGISTRY)) return reject("clé de signature inconnue ou sans droit sur le registre");
        Integer revoked = jdbc.queryForObject("SELECT COUNT(*) FROM lic_revocation WHERE kid = ?", Integer.class, kid);
        if (revoked != null && revoked > 0) return reject("clé de signature révoquée");
        boolean ok;
        try {
            ok = LicenseKeyring.verify(k.publicKey(), signedText(generatedAt, signed).getBytes(StandardCharsets.UTF_8), Base64.getDecoder().decode(n.get("signature").asText()));
        } catch (RuntimeException e) {
            ok = false;
        }
        if (!ok) return reject("signature invalide");

        String last = meta("experts_generated_at");
        long lastAt = last == null ? -1 : Long.parseLong(last);
        if (generatedAt < lastAt) return reject("liste plus ancienne que celle déjà acceptée");
        boolean same = generatedAt == lastAt;
        if (!same) {
            tx.executeWithoutResult(s -> {
                jdbc.update("DELETE FROM tunnel_expert");
                parsed.forEach((id, v) -> jdbc.update("INSERT INTO tunnel_expert (expert_id, ssh_public_key, not_after) VALUES (?,?,?)", id, v[0], Long.parseLong(v[1])));
                setMeta("experts_generated_at", Long.toString(generatedAt));
                setMeta("experts_key_id", kid);
            });
            tunnel.audit("EXPERTS_ACCEPTED", null, "system", parsed.size() + " expert(s), clé " + kid);
        }
        setMeta("experts_status", "Acceptée");
        setMeta("experts_checked_at", Long.toString(System.currentTimeMillis()));
        try {
            tunnel.regenerate();
        } catch (IOException | RuntimeException e) {
            log.error("tunnel: experts authorized_keys not written: {}", e.toString());
            return new Result(Outcome.REJECTED, "liste valide mais fichier des clés non écrit");
        }
        return new Result(same ? Outcome.UNCHANGED : Outcome.ACCEPTED, parsed.size() + " expert(s)");
    }

    public record Status(String text, Instant generatedAt, String keyId, Instant checkedAt, int experts, int active) {}

    public Status status() {
        String at = meta("experts_generated_at"), chk = meta("experts_checked_at"), st = meta("experts_status");
        long now = System.currentTimeMillis();
        int total = jdbc.queryForObject("SELECT COUNT(*) FROM tunnel_expert", Integer.class);
        int active = jdbc.queryForObject("SELECT COUNT(*) FROM tunnel_expert WHERE not_after = 0 OR not_after > ?", Integer.class, now);
        return new Status(st == null ? "Jamais vérifiée" : st, at == null ? null : Instant.ofEpochMilli(Long.parseLong(at)), meta("experts_key_id"),
                chk == null ? null : Instant.ofEpochMilli(Long.parseLong(chk)), total, active);
    }

    public record ExpertView(String id, Instant notAfter, boolean expired) {}

    public List<ExpertView> experts() {
        long now = System.currentTimeMillis();
        return jdbc.query("SELECT expert_id, not_after FROM tunnel_expert ORDER BY expert_id",
                (rs, i) -> new ExpertView(rs.getString(1), rs.getLong(2) == 0 ? null : Instant.ofEpochMilli(rs.getLong(2)), rs.getLong(2) != 0 && rs.getLong(2) <= now));
    }

    /** Every cycle: re-reads the list (a newer one is picked up) and rewrites the files (an expired expert disappears). */
    @Scheduled(initialDelayString = "${castbridge.tunnel.probe-seconds:60}", fixedDelayString = "${castbridge.tunnel.probe-seconds:60}", timeUnit = java.util.concurrent.TimeUnit.SECONDS)
    void scheduled() {
        if (!tunnel.enabled()) return;
        try {
            refresh();
            tunnel.regenerate();
        } catch (IOException | RuntimeException e) {
            log.warn("tunnel: experts refresh failed: {}", e.toString());
        }
    }
}
