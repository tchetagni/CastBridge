package castbridge.server.activations;

import castbridge.server.common.Times;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Reads {@code lic_issuance} (never writes it) by an id cursor and copies each new issuance into the tracker. The licence module publishes no Spring event for an issuance,
 * so a read-only tap is the hook (the cursor is kept in act_cursor). Issuances of the SERVER carry the fingerprint of the real token, hence an inventory row and the flag
 * {@code server_issued}. Issuances IMPORTED from a signed registry carry the fingerprint of the registry event, not of a token: they become an event and a
 * {@code (kid, nonce)} entry that lets the activation be recognised as declared when a TV later reports it.
 */
@Component
public class IssuanceTap {
    private static final Logger log = LoggerFactory.getLogger(IssuanceTap.class);
    static final String CURSOR = "lic_issuance";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ActClock clock;
    private final EventLog eventLog;
    private final Inventory inventory;
    private final TvRef tvRef;
    private final Reconciler reconciler;
    private final ActivationsProperties props;

    public IssuanceTap(JdbcTemplate jdbc, TransactionTemplate tx, ActClock clock, EventLog eventLog, Inventory inventory, TvRef tvRef, Reconciler reconciler, ActivationsProperties props) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.clock = clock;
        this.eventLog = eventLog;
        this.inventory = inventory;
        this.tvRef = tvRef;
        this.reconciler = reconciler;
        this.props = props;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    void scheduled() {
        if (!props.enabled() || !tvRef.available()) return;
        try {
            runOnce();
        } catch (RuntimeException e) {
            log.error("issuance tap failed: {}", e.getClass().getSimpleName());
        }
    }

    /** @return how many issuances were taken */
    public int runOnce() {
        int total = 0;
        while (true) {
            Integer n = tx.execute(s -> step());
            if (n == null || n == 0) break;
            total += n;
        }
        return total;
    }

    private int step() {
        long cursor = Cursors.get(jdbc, CURSOR);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT i.id, i.kind, i.subject, i.kid, i.nonce, i.issued_at, i.not_before, i.not_after, i.seat_id, i.device_code, i.token_fingerprint, i.source, l.license_id"
                + " FROM lic_issuance i JOIN lic_license l ON l.id = i.license_pk WHERE i.id > ? ORDER BY i.id LIMIT 200", cursor);
        Set<String> fps = new TreeSet<>(), tvs = new TreeSet<>(), licenses = new TreeSet<>();
        long last = cursor;
        int taken = 0;
        for (Map<String, Object> r : rows) {
            if (Cursors.recent(clock, r.get("issued_at"))) break;   // audit M7: not final yet
            taken++;
            long id = ((Number) r.get("id")).longValue();
            last = id;
            String kid = (String) r.get("kid"), license = (String) r.get("license_id"), kind = ((String) r.get("kind")).toUpperCase(Locale.ROOT);
            String ref = tvRef.ofOrNull((String) r.get("device_code"));
            Timestamp issued = Times.ts(r.get("issued_at")), notAfter = Times.ts(r.get("not_after")), notBefore = Times.ts(r.get("not_before"));
            if (ref != null) inventory.ensureTv(ref, TvRef.canonical((String) r.get("device_code")));
            if ("SERVER".equals(r.get("source"))) {
                String fp = (String) r.get("token_fingerprint");
                inventory.upsertKey(new Inventory.KeyFacts(fp, "ENVELOPE", kid, kind, (String) r.get("subject"), license, (String) r.get("seat_id"), ref, null, null, (String) r.get("nonce"),
                        issued.toInstant(), notBefore == null ? issued.toInstant() : notBefore.toInstant(), notAfter == null ? null : notAfter.toInstant(), null, null, null, null), Set.of(Inventory.SERVER_ISSUED));
                eventLog.append(new EventLog.NewEvent("ISSUED", issued.getTime(), fp, ref, license, kid, "SERVER", kid, "ISSUANCE", null, "{\"kind\":\"" + kind.toLowerCase(Locale.ROOT) + "\",\"seat\":\"" + r.get("seat_id") + "\"}",
                        "I:" + fp));
                fps.add(fp);
            } else {
                jdbc.update("DELETE FROM act_reg_issue WHERE kid = ? AND nonce = ?", kid, r.get("nonce"));
                jdbc.update("INSERT INTO act_reg_issue (kid, nonce, license_id, seat_id, kind, tv_ref, issued_at, not_after) VALUES (?,?,?,?,?,?,?,?)", kid, r.get("nonce"), license, r.get("seat_id"), kind, ref, issued, notAfter);
                eventLog.append(new EventLog.NewEvent("ISSUED", issued.getTime(), null, ref, license, kid, "TOOL", kid, "REGISTRY", null, "{\"kind\":\"" + kind.toLowerCase(Locale.ROOT) + "\",\"seat\":\"" + r.get("seat_id") + "\"}",
                        "I:imp:" + id));
                List<String> known = jdbc.queryForList("SELECT fp FROM act_key WHERE kid = ? AND nonce = ?", String.class, kid, r.get("nonce"));
                for (String fp : known) {
                    inventory.addFlags(fp, Set.of(Inventory.DECLARED_REGISTRY));
                    fps.add(fp);
                }
                if (known.isEmpty()) {
                    // audit M8: no token yet. The emission still gets an inventory row, under a SUBSTITUTE fingerprint, so that « never seen 48 h after the emission » (EXPIRED_UNUSED) applies;
                    // the row is merged into the real one when a token with this (kid, nonce) arrives (Inventory.upsertKey)
                    String sub = castbridge.server.licenses.Hashing.sha256Hex(("registry:" + kid + ":" + r.get("nonce")).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    inventory.upsertKey(new Inventory.KeyFacts(sub, "REGISTRY", kid, kind, (String) r.get("subject"), license, (String) r.get("seat_id"), ref, null, null, (String) r.get("nonce"),
                            issued.toInstant(), notBefore == null ? issued.toInstant() : notBefore.toInstant(), notAfter == null ? null : notAfter.toInstant(), null, null, null, null), Set.of(Inventory.DECLARED_REGISTRY));
                    fps.add(sub);
                }
            }
            if (ref != null) tvs.add(ref);
            licenses.add(license);
        }
        if (taken > 0) {
            Cursors.set(jdbc, CURSOR, last);
            reconciler.reconcile(Reconciler.Scope.of(fps, tvs, null, licenses));
        }
        return taken;
    }

    static List<String> list(Set<String> s) { return new ArrayList<>(s); }

    static Instant at(Timestamp t) { return t.toInstant(); }
}
