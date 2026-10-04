package castbridge.server.activations;

import castbridge.server.licenses.LicenseProperties;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * THE HISTORY: append-only, chained by hashes (each row carries hash(previous hash | its own fields); the head row records the last id and hash). A changed row, a
 * removed row, a removed tail or a reordering breaks the chain and {@link #verify()} says where. When the file act-audit.key exists in the secrets folder the hash is an
 * HMAC with it, so that someone who can write to the database but does not hold the key cannot recompute a valid chain.
 *
 * <p>An append happens in the caller's transaction (the fact and its history line commit or fail together). {@code idem_key} is unique: replaying a source (a journal,
 * a report, a registry import, in any order, any number of times) adds nothing. A row never holds a whole device code, a token, a key, a challenge: the TV is its
 * {@code tv_ref}.
 */
@Service
public class EventLog {
    private static final int BATCH = 500;
    static final int HEAD = 1;

    /** The closed list of event types. */
    public enum Type {
        ISSUED, ISSUED_COMPACT, DELIVERED, ACTIVATED, SEEN, REPLACED, ENDED, REVOKED_KEY, REVOKED_SEAT, TRANSFERRED, TRIAL_RESET, COMMAND_UNLOCK, COMMAND_OPEN_ALL, COMMAND_SUPPORT,
        LICENSE_CHANGED, SEAT_RELEASED, APP_VERSION, DEVICE_LINKED, JOURNAL_BATCH, REGISTRY_IMPORT, ALERT_OPENED, ALERT_DECIDED, RECONCILED, POLICY_CHANGED, ERASED,
        /** an emission or a command that the tool or the TV refused (not in the design list: the journal carries refusals, they belong to the history) */
        REFUSED,
        /** the owner ordered the cold archive (not in the design list: an order of the owner is always a line of the history) */
        ARCHIVED
    }

    private static final Set<String> ACTOR_TYPES = Set.of("TOOL", "SERVER", "TV", "ADMIN", "JOB");
    private static final Set<String> SOURCES = Set.of("JOURNAL", "REGISTRY", "ISSUANCE", "REPORT", "COURIER", "CONSOLE_BT", "ADMIN", "RECONCILE", "LICENSE");

    /** @param atMs the moment of the action (not of the recording) */
    public record NewEvent(String type, long atMs, String fp, String tvRef, String licenseId, String kid, String actorType, String actor, String source, String before, String after, String idemKey) {}

    public record Event(long id, long atMs, long recordedMs, String type, String fp, String tvRef, String licenseId, String kid, String actorType, String actor, String source, String before, String after,
                        String idemKey, String prevHash, String hash) {}

    public record Verification(boolean ok, long rows, Long brokenAtId, String problem, String headHash) {}

    private final JdbcTemplate jdbc;
    private final ActClock clock;
    private final byte[] key;
    private final Checkpoints checkpoints;

    public EventLog(JdbcTemplate jdbc, LicenseProperties props, ActClock clock, @org.springframework.context.annotation.Lazy Checkpoints checkpoints) {
        this.checkpoints = checkpoints;
        this.jdbc = jdbc;
        this.clock = clock;
        this.key = Chains.readKey(props.secretsDir().resolve("act-audit.key"));
    }

    /**
     * Appends one event in the caller's transaction; false (nothing written) when the idem_key is already there.
     *
     * @throws IllegalArgumentException an unknown type, actor type or source, or a whole device code in a field
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean append(NewEvent e) {
        Type.valueOf(e.type());
        if (!ACTOR_TYPES.contains(e.actorType()) || !SOURCES.contains(e.source())) throw new IllegalArgumentException("actor type or source");
        for (String s : new String[] {e.fp(), e.tvRef(), e.licenseId(), e.kid(), e.actor(), e.before(), e.after()}) {
            if (s != null && Chains.DEVICE_CODE.matcher(s).find()) throw new IllegalArgumentException("a whole device code never enters the history");
        }
        if (e.idemKey() == null || e.idemKey().isBlank() || e.idemKey().length() > 96) throw new IllegalArgumentException("idem_key");
        Object[] head = Chains.lockHead(jdbc, HEAD);
        long id = (Long) head[0] + 1;
        String prev = (String) head[1];
        long recordedMs = clock.nowMs();
        String fp = e.fp(), tv = e.tvRef(), lic = Chains.clip(e.licenseId(), 64), kid = Chains.clip(e.kid(), 64), actor = Chains.clip(e.actor(), 64);
        String before = Chains.clip(e.before(), 1000), after = Chains.clip(e.after(), 1000);
        String hash = hash(prev, e.atMs(), recordedMs, e.type(), fp, tv, lic, kid, e.actorType(), actor, e.source(), before, after, e.idemKey());
        try {
            jdbc.update("INSERT INTO act_event (id, at, recorded_at, type, fp, tv_ref, license_id, kid, actor_type, actor, source, before_json, after_json, idem_key, prev_hash, hash)"
                            + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    id, new Timestamp(e.atMs()), new Timestamp(recordedMs), e.type(), fp, tv, lic, kid, e.actorType(), actor, e.source(), before, after, e.idemKey(), prev, hash);
        } catch (DuplicateKeyException dup) {
            return false;   // the same fact already recorded: the head is untouched
        }
        Chains.setHead(jdbc, HEAD, id, hash);
        return true;
    }

    private String hash(String prev, long atMs, long recordedMs, String type, String fp, String tv, String lic, String kid, String actorType, String actor, String source, String before, String after,
                        String idem) {
        return Chains.hash(key, prev, Long.toString(atMs), Long.toString(recordedMs), type, Chains.nz(fp), Chains.nz(tv), Chains.nz(lic), Chains.nz(kid), actorType, Chains.nz(actor), source,
                Chains.nz(before), Chains.nz(after), idem);
    }

    /** Walks the whole chain by batches of 500 rows (constant memory), from the anchor left by a cold archive if there is one. */
    @Transactional(readOnly = true)
    public Verification verify() {
        Object[] anchor = Chains.anchor(jdbc, "act_event");
        long after = (Long) anchor[0];
        String prev = (String) anchor[1];
        long count = 0, lastId = after;
        boolean first = true;
        while (true) {
            List<Event> rows = jdbc.query("SELECT * FROM act_event WHERE id > ? ORDER BY id LIMIT ?", (rs, i) -> map(rs), after, BATCH);
            if (rows.isEmpty()) break;
            for (Event e : rows) {
                if (!e.prevHash().equals(prev) || (first && after > 0 && e.id() != after + 1)) {
                    return new Verification(false, count, e.id(), "Chaîne rompue avant la ligne " + e.id() + " (ligne supprimée ou modifiée)", null);
                }
                first = false;
                String expected = hash(prev, e.atMs(), e.recordedMs(), e.type(), e.fp(), e.tvRef(), e.licenseId(), e.kid(), e.actorType(), e.actor(), e.source(), e.before(), e.after(), e.idemKey());
                if (!expected.equals(e.hash())) return new Verification(false, count, e.id(), "La ligne " + e.id() + " a été modifiée", null);
                prev = e.hash();
                lastId = e.id();
                count++;
                after = e.id();
            }
        }
        var head = jdbc.queryForMap("SELECT last_id, last_hash FROM act_event_head WHERE id = ?", HEAD);
        if (((Number) head.get("last_id")).longValue() != lastId || !head.get("last_hash").equals(prev)) {
            return new Verification(false, count, lastId + 1, "La fin de l'historique ne correspond pas à la tête de chaîne (lignes retirées en fin d'historique)", null);
        }
        String cross = checkpoints.crossCheck("act_event", lastId);   // audit H3: the head is in the same database as the rows: the signed checkpoints and anchors say the rest
        if (cross != null) return new Verification(false, count, null, cross, null);
        return new Verification(true, count, null, null, prev);
    }

    public String headHash() { return jdbc.queryForObject("SELECT last_hash FROM act_event_head WHERE id = ?", String.class, HEAD); }

    public long lastId() { return jdbc.queryForObject("SELECT last_id FROM act_event_head WHERE id = ?", Long.class, HEAD); }

    static Event map(ResultSet rs) throws SQLException {
        return new Event(rs.getLong("id"), rs.getTimestamp("at").getTime(), rs.getTimestamp("recorded_at").getTime(), rs.getString("type"), rs.getString("fp"), rs.getString("tv_ref"),
                rs.getString("license_id"), rs.getString("kid"), rs.getString("actor_type"), rs.getString("actor"), rs.getString("source"), rs.getString("before_json"), rs.getString("after_json"),
                rs.getString("idem_key"), rs.getString("prev_hash"), rs.getString("hash"));
    }

    /** All the types, for the pages. */
    public static Set<Type> types() { return EnumSet.allOf(Type.class); }
}
