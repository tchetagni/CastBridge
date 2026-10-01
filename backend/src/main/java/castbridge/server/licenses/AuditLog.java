package castbridge.server.licenses;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Append-only audit log chained by hashes: each row carries SHA-256(previous hash | its own fields), the head row records
 * the last id and hash. A changed row, a removed row, a removed tail or a reordering breaks the chain and
 * {@link #verify()} says where. If the file license-audit.key exists in the secrets folder the hash is an HMAC with it, so
 * that someone who can write to the database but does not hold the key cannot recompute a valid chain.
 *
 * <p>Rows hold identifiers only: no name, contact, activation or full device code (so erasing a client never touches the log).
 */
@Service
public class AuditLog {
    static final String GENESIS = "0".repeat(64);
    private static final int BATCH = 500;

    private final JdbcTemplate jdbc;
    private final byte[] hmacKey;

    public AuditLog(JdbcTemplate jdbc, LicenseProperties props) {
        this.jdbc = jdbc;
        this.hmacKey = readKey(props.secretsDir().resolve("license-audit.key"));
    }

    private static byte[] readKey(Path p) {
        try {
            return Files.isReadable(p) ? Hashing.sha256(Files.readAllBytes(p)) : null;
        } catch (java.io.IOException e) {
            return null;
        }
    }

    public record Entry(long id, Instant at, String actor, String role, String channel, String action, String targetType,
                        String targetId, String reason, String details, String prevHash, String hash) {}

    public record Verification(boolean ok, long rows, Long brokenAtId, String problem, String headHash) {}

    /** Appends one entry in the caller's transaction (the action and its audit line commit or fail together). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Actor actor, String action, String targetType, String targetId, String reason, Map<String, ?> details) {
        // whole seconds: the hash must not depend on the fractional-second precision of the database or of its driver
        Instant at = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        // the head row serializes every writer: concurrent appends cannot fork the chain
        Map<String, Object> head = jdbc.queryForMap("SELECT last_id, last_hash FROM lic_audit_head WHERE id = 1 FOR UPDATE");
        long lastId = ((Number) head.get("last_id")).longValue();
        String prev = (String) head.get("last_hash");
        String det = flatten(details);
        String rsn = clip(reason, 500);
        String role = actor.role() == null ? "-" : actor.role().name();
        String hash = hash(prev, at, actor.name(), role, actor.channel(), action, targetType, targetId, rsn, det);
        long id = lastId + 1;
        jdbc.update("INSERT INTO lic_audit (id, at, actor, role, channel, action, target_type, target_id, reason, details, prev_hash, hash)"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                id, Timestamp.from(at), clip(actor.name(), 64), role, actor.channel(), action, targetType, clip(targetId, 64), rsn, det, prev, hash);
        jdbc.update("UPDATE lic_audit_head SET last_id = ?, last_hash = ? WHERE id = 1", id, hash);
    }

    String hash(String prev, Instant at, String actor, String role, String channel, String action, String type, String target,
                String reason, String details) {
        String data = String.join("\u001f", prev, Long.toString(at.getEpochSecond()), nz(clip(actor, 64)), role, channel, action, type, nz(clip(target, 64)), nz(reason), nz(details));
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        return HexFormat.of().formatHex(hmacKey == null ? Hashing.sha256(bytes) : Hashing.hmac("HmacSHA256", hmacKey, bytes));
    }

    /** Walks the whole chain by batches of 500 rows (constant memory). */
    @Transactional(readOnly = true)
    public Verification verify() {
        String prev = GENESIS;
        long count = 0, lastId = 0;
        long after = 0;
        while (true) {
            List<Entry> rows = jdbc.query("SELECT * FROM lic_audit WHERE id > ? ORDER BY id LIMIT ?", (rs, i) -> map(rs), after, BATCH);
            if (rows.isEmpty()) break;
            for (Entry e : rows) {
                if (!e.prevHash().equals(prev)) {
                    return new Verification(false, count, e.id(), "Chaîne rompue avant la ligne " + e.id() + " (ligne supprimée ou modifiée)", null);
                }
                String expected = hash(prev, e.at(), e.actor(), e.role(), e.channel(), e.action(), e.targetType(), e.targetId(), e.reason(), e.details());
                if (!expected.equals(e.hash())) {
                    return new Verification(false, count, e.id(), "La ligne " + e.id() + " a été modifiée", null);
                }
                prev = e.hash();
                lastId = e.id();
                count++;
                after = e.id();
            }
        }
        Map<String, Object> head = jdbc.queryForMap("SELECT last_id, last_hash FROM lic_audit_head WHERE id = 1");
        if (((Number) head.get("last_id")).longValue() != lastId || !head.get("last_hash").equals(prev)) {
            return new Verification(false, count, lastId + 1, "La fin du journal ne correspond pas à la tête de chaîne (lignes retirées en fin de journal)", null);
        }
        return new Verification(true, count, null, null, prev);
    }

    /** Hash of the last entry: the owner can note it elsewhere (backup, e-mail) as an outside anchor of the chain. */
    public String headHash() { return jdbc.queryForObject("SELECT last_hash FROM lic_audit_head WHERE id = 1", String.class); }

    public record Filter(String actor, String action, String targetType, String targetId, Instant from, Instant to) {}

    public Page<Entry> search(Filter f, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (f.actor() != null && !f.actor().isBlank()) { where.append(" AND actor = ?"); args.add(f.actor().trim()); }
        if (f.action() != null && !f.action().isBlank()) { where.append(" AND action = ?"); args.add(f.action().trim()); }
        if (f.targetType() != null && !f.targetType().isBlank()) { where.append(" AND target_type = ?"); args.add(f.targetType().trim()); }
        if (f.targetId() != null && !f.targetId().isBlank()) { where.append(" AND target_id = ?"); args.add(f.targetId().trim()); }
        if (f.from() != null) { where.append(" AND at >= ?"); args.add(Timestamp.from(f.from())); }
        if (f.to() != null) { where.append(" AND at < ?"); args.add(Timestamp.from(f.to())); }
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM lic_audit" + where, Long.class, args.toArray());
        List<Object> a2 = new ArrayList<>(args);
        a2.add(size);
        a2.add((long) page * size);
        List<Entry> rows = jdbc.query("SELECT * FROM lic_audit" + where + " ORDER BY id DESC LIMIT ? OFFSET ?", (rs, i) -> map(rs), a2.toArray());
        return new Page<>(rows, page, size, total);
    }

    /** Entries about one licence (its full history). */
    public List<Entry> forTarget(String type, String id, int limit) {
        return jdbc.query("SELECT * FROM lic_audit WHERE target_type = ? AND target_id = ? ORDER BY id DESC LIMIT ?", (rs, i) -> map(rs), type, id, limit);
    }

    private static Entry map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Entry(rs.getLong("id"), rs.getTimestamp("at").toInstant(), rs.getString("actor"), rs.getString("role"),
                rs.getString("channel"), rs.getString("action"), rs.getString("target_type"), rs.getString("target_id"),
                rs.getString("reason"), rs.getString("details"), rs.getString("prev_hash"), rs.getString("hash"));
    }

    private static String nz(String s) { return s == null ? "" : s; }

    static String clip(String s, int max) {
        if (s == null) return null;
        String t = s.replaceAll("[\\p{Cntrl}]", " ").trim();
        return t.length() <= max ? t : t.substring(0, max);
    }

    static String flatten(Map<String, ?> details) {
        if (details == null || details.isEmpty()) return null;
        StringJoiner j = new StringJoiner("; ");
        details.forEach((k, v) -> j.add(k + "=" + clip(String.valueOf(v), 200)));
        return clip(j.toString(), 1500);
    }
}
