package castbridge.server.activations;

import castbridge.server.common.Times;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The inventory (act_key, act_tv): what the four sources (server issuances, imported registry, signed tool journals, TV reports) say about each activation and each TV,
 * merged so that the final rows do not depend on the order in which the sources arrive, nor on how many times they are replayed. Scalar facts are only ever FILLED IN
 * (never overwritten by a poorer source), flags only ever added; the state of an activation is DERIVED from the facts and the clock, never from a path.
 */
@Service
public class Inventory {
    public static final String EMISE = "EMISE", ACTIVATED = "ACTIVATED", EXPIRED_UNUSED = "EXPIRED_UNUSED", REPLACED = "REPLACED", ENDED = "ENDED", REVOKED = "REVOKED";

    /** Flags of act_key. */
    public static final String DECLARED_JOURNAL = "declared_journal", DECLARED_REGISTRY = "declared_registry", SERVER_ISSUED = "server_issued", SEEN_ON_TV = "seen_on_tv", DELIVERED_BT = "delivered_bt",
            UNDECLARED = "undeclared", CLONE = "clone", OUT_OF_WINDOW = "out_of_window";

    /** What a source knows of an activation (null = does not know). */
    public record KeyFacts(String fp, String form, String kid, String kind, String subject, String licenseId, String seatId, String tvRef, Integer k, Long aseq, String nonce, Instant issuedAt,
                           Instant notBefore, Instant expiresAt, Instant usageTo, Boolean unlimited, Boolean superFlag, String rights) {
        public static KeyFacts of(String fp, String form) { return new KeyFacts(fp, form, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null); }
    }

    private final JdbcTemplate jdbc;
    private final ActClock clock;
    private final EventLog log;

    public Inventory(JdbcTemplate jdbc, ActClock clock, EventLog log) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.log = log;
    }

    // ---------------------------------------------------------------- flags

    public static Set<String> flagsOf(String s) {
        Set<String> out = new TreeSet<>();
        if (s != null) for (String f : s.split(",")) if (!f.isBlank()) out.add(f);
        return out;
    }

    public static String flagsToString(Set<String> flags) { return flags.isEmpty() ? "" : "," + String.join(",", new TreeSet<>(flags)) + ","; }

    // ---------------------------------------------------------------- TVs

    /** Makes sure the TV exists (with its readable code unless it was erased since). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void ensureTv(String tvRef, String deviceCode) {
        int n = jdbc.update("UPDATE act_tv SET first_seen_at = COALESCE(first_seen_at, ?) WHERE tv_ref = ?", Timestamp.from(clock.now()), tvRef);
        if (n == 0) {
            try {
                jdbc.update("INSERT INTO act_tv (tv_ref, device_code, first_seen_at, reco) VALUES (?,?,?,'NEVER')", tvRef, deviceCode, Timestamp.from(clock.now()));
            } catch (org.springframework.dao.DuplicateKeyException e) {
                // already there (a concurrent writer)
            }
        }
        // an erased code is never put back by a later source: the right to erasure wins (the code stays NULL once an ERASED event exists)
        if (deviceCode != null && jdbc.queryForList("SELECT id FROM act_event WHERE idem_key = ? LIMIT 1", Long.class, "E:" + tvRef).isEmpty()) {
            jdbc.update("UPDATE act_tv SET device_code = ? WHERE tv_ref = ? AND device_code IS NULL", deviceCode, tvRef);
        }
    }

    // ---------------------------------------------------------------- activations

    /**
     * Inserts or completes an activation. {@code rich} = the source is the signed token itself or a journal (more complete): its values fill in what is missing and replace
     * a placeholder; a poorer source never replaces a known value.
     *
     * @return true when the row is new
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean upsertKey(KeyFacts f, Set<String> addFlags) {
        String tag = f.fp().substring(0, 8);
        List<Map<String, Object>> existing = jdbc.queryForList("SELECT flags, kind FROM act_key WHERE fp = ?", f.fp());
        Timestamp now = Timestamp.from(clock.now());
        if (existing.isEmpty()) {
            Set<String> flags = new TreeSet<>(addFlags);
            addRegistryFlag(f, flags);
            // issued_at is never null (a row known only by a delivery is placed at the moment it was first heard of): the lists sort on it
            jdbc.update("INSERT INTO act_key (fp, tag, form, kid, kind, subject, license_id, seat_id, tv_ref, k, aseq, nonce, issued_at, not_before, expires_at, usage_to, unlimited, super, rights, state, state_at, flags)"
                            + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    f.fp(), tag, f.form(), f.kid(), f.kind() == null ? "UNKNOWN" : f.kind(), f.subject() == null ? "tv" : f.subject(), f.licenseId(), f.seatId(), f.tvRef(), f.k(), f.aseq(), f.nonce(),
                    f.issuedAt() == null ? now : ts(f.issuedAt()), ts(f.notBefore()), ts(f.expiresAt()), ts(f.usageTo()), f.unlimited(), f.superFlag() != null && f.superFlag(), f.rights(), EMISE, now,
                    flagsToString(flags));
            applyRevocations(f.fp());
            syncContact(f.fp());
            return true;
        }
        Set<String> flags = flagsOf((String) existing.get(0).get("flags"));
        flags.addAll(addFlags);
        addRegistryFlag(f, flags);
        // facts are only filled in: a known value is kept; a placeholder (a row known only by a delivery: kind UNKNOWN) takes the values of the first real source
        boolean placeholder = "UNKNOWN".equals(existing.get(0).get("kind"));
        String dates = placeholder ? "issued_at = COALESCE(?, issued_at), not_before = COALESCE(?, not_before), expires_at = COALESCE(?, expires_at)"
                : "issued_at = COALESCE(issued_at, ?), not_before = COALESCE(not_before, ?), expires_at = COALESCE(expires_at, ?)";
        jdbc.update("UPDATE act_key SET form = COALESCE(form, ?), kid = COALESCE(kid, ?), license_id = COALESCE(license_id, ?), seat_id = COALESCE(seat_id, ?), tv_ref = COALESCE(tv_ref, ?),"
                        + " k = COALESCE(k, ?), aseq = COALESCE(aseq, ?), nonce = COALESCE(nonce, ?), " + dates + ", usage_to = COALESCE(usage_to, ?), unlimited = COALESCE(unlimited, ?),"
                        + " rights = COALESCE(rights, ?), flags = ?, kind = " + (placeholder ? "COALESCE(?, kind)" : "COALESCE(kind, ?)") + " WHERE fp = ?",
                f.form(), f.kid(), f.licenseId(), f.seatId(), f.tvRef(), f.k(), f.aseq(), f.nonce(), ts(f.issuedAt()), ts(f.notBefore()), ts(f.expiresAt()), ts(f.usageTo()), f.unlimited(), f.rights(),
                flagsToString(flags), f.kind(), f.fp());
        if (f.superFlag() != null && f.superFlag()) jdbc.update("UPDATE act_key SET super = TRUE WHERE fp = ?", f.fp());
        applyRevocations(f.fp());
        syncContact(f.fp());
        return false;
    }

    /**
     * A delivery acknowledged by the TV (Bluetooth) is a contact of that TV: when the activation learns its TV AFTER the delivery was heard of (the journals arrive in any order),
     * the contact date still reaches the TV. */
    public void syncContact(String fp) {
        List<Map<String, Object>> r = jdbc.queryForList("SELECT tv_ref, delivered_bt_at FROM act_key WHERE fp = ?", fp);
        if (r.isEmpty() || r.get(0).get("tv_ref") == null || r.get(0).get("delivered_bt_at") == null) return;
        Object at = r.get(0).get("delivered_bt_at");
        jdbc.update("UPDATE act_tv SET last_bt_at = CASE WHEN last_bt_at IS NULL OR last_bt_at < ? THEN ? ELSE last_bt_at END WHERE tv_ref = ?", at, at, r.get(0).get("tv_ref"));
    }

    /**
     * An activation that arrives AFTER the revocation of its key, or of its seat at or after its issue date, is revoked at once (the revocation list is read, never written). */
    private void applyRevocations(String fp) {
        jdbc.update("UPDATE act_key SET revoked_at = (SELECT MIN(r.revoked_at) FROM lic_revocation r WHERE r.kid = act_key.kid OR (r.license_id = act_key.license_id AND r.seat_id = act_key.seat_id"
                + " AND act_key.issued_at <= r.revoked_at)) WHERE fp = ? AND revoked_at IS NULL", fp);
    }

    /** An imported registry issuance for this (kid, nonce) declares the activation. */
    private void addRegistryFlag(KeyFacts f, Set<String> flags) {
        if (f.kid() == null || f.nonce() == null) return;
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM act_reg_issue WHERE kid = ? AND nonce = ?", Integer.class, f.kid(), f.nonce());
        if (n != null && n > 0) flags.add(DECLARED_REGISTRY);
    }

    private static Timestamp ts(Instant i) { return i == null ? null : Timestamp.from(i); }

    @Transactional(propagation = Propagation.MANDATORY)
    public void addFlags(String fp, Set<String> add) {
        List<String> ex = jdbc.queryForList("SELECT flags FROM act_key WHERE fp = ?", String.class, fp);
        if (ex.isEmpty()) return;
        Set<String> flags = flagsOf(ex.get(0));
        if (flags.addAll(add)) jdbc.update("UPDATE act_key SET flags = ? WHERE fp = ?", flagsToString(flags), fp);
    }

    // ---------------------------------------------------------------- state

    /** The state of an activation from its facts and the clock. */
    static String derive(Map<String, Object> k, boolean replaced, Instant now) {
        if (k.get("revoked_at") != null) return REVOKED;
        Set<String> flags = flagsOf((String) k.get("flags"));
        boolean seen = flags.contains(SEEN_ON_TV) || flags.contains(DELIVERED_BT);
        if (seen && replaced) return REPLACED;
        Timestamp usageTo = Times.ts(k.get("usage_to"));
        if (seen && usageTo != null && now.isAfter(usageTo.toInstant())) return ENDED;
        if (seen) return ACTIVATED;
        Timestamp exp = Times.ts(k.get("expires_at"));
        if (exp != null && now.isAfter(exp.toInstant())) return EXPIRED_UNUSED;
        return EMISE;
    }

    /** Recomputes the state of one activation (and writes the event of a change: ACTIVATED, REPLACED, ENDED). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void refreshState(String fp) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM act_key WHERE fp = ?", fp);
        if (rows.isEmpty()) return;
        Map<String, Object> k = rows.get(0);
        Instant now = clock.now();
        // « undeclared » = seen on a TV and declared by no journal, no registry, no server issuance (recomputed: a late declaration lifts it)
        Set<String> fl = flagsOf((String) k.get("flags"));
        boolean undeclared = fl.contains(SEEN_ON_TV) && !fl.contains(DECLARED_JOURNAL) && !fl.contains(DECLARED_REGISTRY) && !fl.contains(SERVER_ISSUED);
        if (undeclared != fl.contains(UNDECLARED)) {
            if (undeclared) fl.add(UNDECLARED); else fl.remove(UNDECLARED);
            jdbc.update("UPDATE act_key SET flags = ? WHERE fp = ?", flagsToString(fl), fp);
            k.put("flags", flagsToString(fl));
        }
        boolean replaced = false;
        String tv = (String) k.get("tv_ref");
        Timestamp issued = Times.ts(k.get("issued_at"));
        Set<String> flags = flagsOf((String) k.get("flags"));
        if (tv != null && issued != null && (flags.contains(SEEN_ON_TV) || flags.contains(DELIVERED_BT))) {
            Integer newer = jdbc.queryForObject("SELECT COUNT(*) FROM act_key WHERE tv_ref = ? AND fp <> ? AND (flags LIKE '%,seen_on_tv,%' OR flags LIKE '%,delivered_bt,%') AND revoked_at IS NULL"
                    + " AND (issued_at > ? OR (issued_at = ? AND fp > ?))", Integer.class, tv, fp, issued, issued, fp);
            replaced = newer != null && newer > 0;
        }
        String next = derive(k, replaced, now);
        String old = (String) k.get("state");
        if (next.equals(old)) return;
        jdbc.update("UPDATE act_key SET state = ?, state_at = ? WHERE fp = ?", next, Timestamp.from(now), fp);
        String type = switch (next) {
            case ACTIVATED -> "ACTIVATED";
            case REPLACED -> "REPLACED";
            case ENDED -> "ENDED";
            default -> null;   // EMISE, EXPIRED_UNUSED, REVOKED: derived states, the cause has its own event
        };
        if (type != null) {
            long at = Times.ms(k.get("first_seen_tv_at") != null ? k.get("first_seen_tv_at") : k.get("delivered_bt_at") != null ? k.get("delivered_bt_at") : now);
            log.append(new EventLog.NewEvent(type, type.equals("ACTIVATED") ? at : now.toEpochMilli(), fp, tv, (String) k.get("license_id"), (String) k.get("kid"), "JOB", "inventory", "RECONCILE", old,
                    "{\"state\":\"" + next + "\"}", "S:" + fp + ":" + type.charAt(0)));
        }
    }

    /** Refreshes the siblings of an activation too (an activation replaced by a newer one on the same TV). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void refreshTvKeys(String tvRef) {
        if (tvRef == null) return;
        for (String fp : jdbc.queryForList("SELECT fp FROM act_key WHERE tv_ref = ?", String.class, tvRef)) refreshState(fp);
    }

    // ---------------------------------------------------------------- reading helpers

    static List<String> col(ResultSet rs, String c, List<String> out) throws SQLException {
        out.add(rs.getString(c));
        return out;
    }

    public List<String> fpsOfTv(String tvRef) { return jdbc.query("SELECT fp FROM act_key WHERE tv_ref = ?", (rs, i) -> rs.getString(1), tvRef); }

    public static List<String> toList(Set<String> s) { return new ArrayList<>(s); }
}
