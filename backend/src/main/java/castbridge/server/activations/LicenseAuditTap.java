package castbridge.server.activations;

import castbridge.server.common.Times;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Read-only taps (id cursors in act_cursor) on what the licence module records about licences and keys, copied into the history without ever writing to a {@code lic_*}
 * table: the imports of the signed registry (REGISTRY_IMPORT), the transfers (TRANSFERRED), the revocations of a key or a seat (REVOKED_KEY, REVOKED_SEAT: the activations they
 * cover are marked revoked) and the changes of a licence read in its audit (LICENSE_CHANGED with what the audit says changed, SEAT_RELEASED). Every 60 s.
 */
@Component
public class LicenseAuditTap {
    private static final Logger log = LoggerFactory.getLogger(LicenseAuditTap.class);
    private static final Set<String> CHANGES = Set.of("LICENSE_SUSPEND", "LICENSE_RESUME", "LICENSE_REVOKE", "LICENSE_EXTEND", "LICENSE_SEATS", "LICENSE_UPDATE", "LICENSE_EXPIRE", "LICENSE_ADD_PRODUCT");

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final EventLog eventLog;
    private final Inventory inventory;
    private final TvRef tvRef;
    private final Reconciler reconciler;
    private final ActivationsProperties props;
    private final ActClock clock;

    public LicenseAuditTap(JdbcTemplate jdbc, TransactionTemplate tx, EventLog eventLog, Inventory inventory, TvRef tvRef, Reconciler reconciler, ActivationsProperties props, ActClock clock) {
        this.clock = clock;
        this.jdbc = jdbc;
        this.tx = tx;
        this.eventLog = eventLog;
        this.inventory = inventory;
        this.tvRef = tvRef;
        this.reconciler = reconciler;
        this.props = props;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 40_000)
    void scheduled() {
        if (!props.enabled() || !tvRef.available()) return;
        try {
            runOnce();
        } catch (RuntimeException e) {
            log.error("licence tap failed: {}", e.getClass().getSimpleName());
        }
    }

    /** @return how many rows of the licence tables were taken */
    public int runOnce() {
        int total = 0;
        for (Function<Void, Integer> step : List.<Function<Void, Integer>>of(v -> imports(), v -> transfers(), v -> revocations(), v -> audit())) {
            while (true) {
                Integer n = tx.execute(s -> step.apply(null));
                if (n == null || n == 0) break;
                total += n;
            }
        }
        return total;
    }

    private static String q(String s) { return s == null ? "" : s.replaceAll("[\"\\\\\\p{Cntrl}]", "'"); }

    private int imports() {
        long cursor = Cursors.get(jdbc, "lic_ledger_import");
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM lic_ledger_import WHERE id > ? ORDER BY id LIMIT 200", cursor);
        int taken = 0;
        for (Map<String, Object> r : rows) {
            if (Cursors.recent(clock, r.get("imported_at"))) break;   // audit M7: not final yet
            taken++;
            long id = ((Number) r.get("id")).longValue();
            eventLog.append(new EventLog.NewEvent("REGISTRY_IMPORT", Times.ms(r.get("imported_at")), null, null, null, null, "ADMIN", (String) r.get("imported_by"), "REGISTRY", null,
                    "{\"entries\":" + r.get("entries") + ",\"applied\":" + r.get("applied") + ",\"duplicates\":" + r.get("duplicates") + ",\"rejected\":" + r.get("rejected") + ",\"conflicts\":" + r.get("conflicts")
                            + ",\"sha\":\"" + ((String) r.get("sha256")).substring(0, 8) + "\"}", "RI:" + id));
            Cursors.set(jdbc, "lic_ledger_import", id);
        }
        return taken;
    }

    private int transfers() {
        long cursor = Cursors.get(jdbc, "lic_transfer");
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT t.*, l.license_id AS wire FROM lic_transfer t JOIN lic_license l ON l.id = t.license_pk WHERE t.id > ? ORDER BY t.id LIMIT 200", cursor);
        int taken = 0;
        for (Map<String, Object> r : rows) {
            if (Cursors.recent(clock, r.get("at"))) break;   // audit M7
            taken++;
            long id = ((Number) r.get("id")).longValue();
            String from = tvRef.ofOrNull((String) r.get("from_device_code")), to = tvRef.ofOrNull((String) r.get("to_device_code"));
            String kid = (String) r.get("signed_by");
            boolean imported = r.get("ledger_import_id") != null;
            eventLog.append(new EventLog.NewEvent("TRANSFERRED", Times.ms(r.get("at")), null, to, (String) r.get("wire"), kid, "TOOL", kid, imported ? "REGISTRY" : "LICENSE", null,
                    "{\"seat\":\"" + r.get("seat_id") + "\",\"from\":" + (from == null ? "null" : "\"" + from + "\"") + ",\"to\":" + (to == null ? "null" : "\"" + to + "\"") + ",\"accepted\":" + r.get("accepted") + "}", "TF:" + id));
            Cursors.set(jdbc, "lic_transfer", id);
        }
        return taken;
    }

    private int revocations() {
        long cursor = Cursors.get(jdbc, "lic_revocation");
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM lic_revocation WHERE id > ? ORDER BY id LIMIT 200", cursor);
        Set<String> fps = new TreeSet<>(), tvs = new TreeSet<>(), kids = new TreeSet<>(), licenses = new TreeSet<>();
        int taken = 0;
        for (Map<String, Object> r : rows) {
            if (Cursors.recent(clock, r.get("revoked_at"))) break;   // audit M7
            taken++;
            long id = ((Number) r.get("id")).longValue();
            Timestamp at = Times.ts(r.get("revoked_at"));
            String by = (String) r.get("revoked_by"), kid = (String) r.get("kid"), license = (String) r.get("license_id"), seat = (String) r.get("seat_id");
            List<Map<String, Object>> hit;
            if (kid != null) {
                hit = jdbc.queryForList("SELECT fp, tv_ref FROM act_key WHERE kid = ?", kid);
                jdbc.update("UPDATE act_key SET revoked_at = COALESCE(revoked_at, ?) WHERE kid = ?", at, kid);
                eventLog.append(new EventLog.NewEvent("REVOKED_KEY", at.getTime(), null, null, null, kid, "ADMIN", by, "LICENSE", null, "{\"kid\":\"" + kid + "\"}", "RV:" + id));
                kids.add(kid);
            } else {
                hit = jdbc.queryForList("SELECT fp, tv_ref FROM act_key WHERE license_id = ? AND seat_id = ? AND issued_at <= ?", license, seat, at);
                jdbc.update("UPDATE act_key SET revoked_at = COALESCE(revoked_at, ?) WHERE license_id = ? AND seat_id = ? AND issued_at <= ?", at, license, seat, at);
                List<String> t = jdbc.queryForList("SELECT tv_ref FROM act_key WHERE license_id = ? AND seat_id = ? AND tv_ref IS NOT NULL ORDER BY issued_at DESC LIMIT 1", String.class, license, seat);
                eventLog.append(new EventLog.NewEvent("REVOKED_SEAT", at.getTime(), null, t.isEmpty() ? null : t.get(0), license, null, "ADMIN", by, "LICENSE", null, "{\"seat\":\"" + seat + "\"}", "RV:" + id));
                licenses.add(license);
            }
            for (Map<String, Object> h : hit) {
                fps.add((String) h.get("fp"));
                if (h.get("tv_ref") != null) tvs.add((String) h.get("tv_ref"));
            }
            Cursors.set(jdbc, "lic_revocation", id);
        }
        if (taken > 0) reconciler.reconcile(Reconciler.Scope.of(fps, tvs, kids, licenses));
        return taken;
    }

    private int audit() {
        long cursor = Cursors.get(jdbc, "lic_audit");
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM lic_audit WHERE id > ? ORDER BY id LIMIT 200", cursor);
        int taken = 0;
        for (Map<String, Object> r : rows) {
            if (Cursors.recent(clock, r.get("at"))) break;   // audit M7 (the audit chain serialises its inserts, the lag costs nothing here)
            taken++;
            long id = ((Number) r.get("id")).longValue();
            String action = (String) r.get("action"), license = (String) r.get("target_id"), actor = (String) r.get("actor");
            long at = Times.ms(r.get("at"));
            String details = (String) r.get("details");
            if (CHANGES.contains(action)) {
                String state = switch (action) {
                    case "LICENSE_SUSPEND" -> "SUSPENDED";
                    case "LICENSE_RESUME" -> "ACTIVE";
                    case "LICENSE_REVOKE" -> "REVOKED";
                    case "LICENSE_EXPIRE" -> "EXPIRED";
                    default -> null;
                };
                eventLog.append(new EventLog.NewEvent("LICENSE_CHANGED", at, null, null, license, null, "ADMIN", actor, "LICENSE", null,
                        "{\"action\":\"" + action + "\"" + (state == null ? "" : ",\"state\":\"" + state + "\"") + (details == null ? "" : ",\"details\":\"" + q(details) + "\"") + "}", "LA:" + id));
            } else if (action.equals("SEAT_RELEASE")) {
                String seat = details != null && details.startsWith("seat=") ? details.substring(5).split(";")[0].trim() : null;
                List<String> t = seat == null ? List.of() : jdbc.queryForList("SELECT tv_ref FROM act_key WHERE license_id = ? AND seat_id = ? AND tv_ref IS NOT NULL ORDER BY issued_at DESC LIMIT 1", String.class, license, seat);
                eventLog.append(new EventLog.NewEvent("SEAT_RELEASED", at, null, t.isEmpty() ? null : t.get(0), license, null, "ADMIN", actor, "LICENSE", null, "{\"seat\":\"" + q(seat) + "\"}", "LA:" + id));
            }
            Cursors.set(jdbc, "lic_audit", id);
        }
        return taken;
    }
}
