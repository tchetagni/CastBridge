package castbridge.server.activations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** The immutable chained history (act_event): chain kept, every kind of tampering pointed at, idempotence by idem_key, one head lock for all writers. */
class EventLogTest extends ActTestBase {
    @Autowired EventLog log;
    @Autowired PlatformTransactionManager tx;

    @BeforeEach
    void clean() { resetModule(); }

    private EventLog.NewEvent ev(String idem) {
        return new EventLog.NewEvent("ISSUED", 1_759_600_000_000L, "f".repeat(64), "abcdef0123456789", "lic-1", "0011223344556677", "TOOL", "0011223344556677", "JOURNAL", null,
                "{\"k\":1}", idem);
    }

    private void append(int from, int to) {
        new TransactionTemplate(tx).executeWithoutResult(s -> {
            for (int i = from; i < to; i++) log.append(ev("idem-" + i));
        });
    }

    private void fill(int n) {
        for (int i = 0; i < n; i += 500) append(i, Math.min(n, i + 500));
    }

    @Test
    void theChainStaysVerifiableAfterTenThousandEvents() {
        fill(10_000);
        EventLog.Verification v = log.verify();
        assertTrue(v.ok(), v.problem());
        assertEquals(10_000, v.rows());
        assertEquals(v.headHash(), log.headHash());
        assertEquals(10_000, log.lastId());
    }

    @Test
    void aModifiedRowIsPointedAt() {
        fill(300);
        jdbc.update("update act_event set after_json = ? where id = 120", "{\"k\":2}");
        EventLog.Verification v = log.verify();
        assertFalse(v.ok());
        assertEquals(120L, v.brokenAtId());
    }

    @Test
    void aRemovedRowIsPointedAt() {
        fill(300);
        jdbc.update("delete from act_event where id = 77");
        EventLog.Verification v = log.verify();
        assertFalse(v.ok());
        assertEquals(78L, v.brokenAtId(), "the first row whose predecessor is gone");
    }

    @Test
    void aTruncatedTailIsDetected() {
        fill(300);
        jdbc.update("delete from act_event where id > 290");
        EventLog.Verification v = log.verify();
        assertFalse(v.ok());
        assertEquals(291L, v.brokenAtId());
    }

    @Test
    void theHmacKeyStopsSomeoneWhoCanWriteTheDatabaseButDoesNotHoldTheKey() {
        fill(50);
        // the attacker rewrites row 10 and recomputes the whole chain with plain SHA-256 (he has no act-audit.key)
        List<java.util.Map<String, Object>> rows = jdbc.queryForList("select * from act_event order by id");
        String prev = "0".repeat(64);
        for (var r : rows) {
            long id = ((Number) r.get("id")).longValue();
            String after = id == 10 ? "{\"k\":99}" : (String) r.get("after_json");
            String data = String.join("\u001f", prev, String.valueOf(((java.sql.Timestamp) r.get("at")).getTime()), String.valueOf(((java.sql.Timestamp) r.get("recorded_at")).getTime()),
                    (String) r.get("type"), nz(r.get("fp")), nz(r.get("tv_ref")), nz(r.get("license_id")), nz(r.get("kid")), (String) r.get("actor_type"), nz(r.get("actor")),
                    (String) r.get("source"), nz(r.get("before_json")), after == null ? "" : after, (String) r.get("idem_key"));
            String h = sha256(data);
            jdbc.update("update act_event set prev_hash = ?, hash = ?, after_json = ? where id = ?", prev, h, after, id);
            prev = h;
        }
        jdbc.update("update act_event_head set last_hash = ? where id = 1", prev);
        assertFalse(log.verify().ok(), "a chain recomputed without the key must not verify");
    }

    private static String nz(Object o) { return o == null ? "" : o.toString(); }

    @Test
    void replayingTheSameSourceAddsNothing() {
        append(0, 20);
        assertEquals(20, events());
        append(0, 20);
        assertEquals(20, events(), "the same idem_key twice writes one row");
        assertTrue(log.verify().ok());
        assertEquals(20, log.lastId(), "a refused duplicate does not advance the head");
    }

    @Test
    void theClosedListOfTypesIsEnforced() {
        var bad = new EventLog.NewEvent("INVENTED", 1L, null, null, null, null, "JOB", "x", "RECONCILE", null, null, "k");
        assertThrows(IllegalArgumentException.class, () -> new TransactionTemplate(tx).executeWithoutResult(s -> log.append(bad)));
    }

    @Test
    void aWholeDeviceCodeNeverEntersTheHistory() {
        var d = dev();
        var bad = new EventLog.NewEvent("SEEN", 1L, null, "abcdef0123456789", null, null, "TV", "tv", "REPORT", null, "{\"code\":\"" + d.code() + "\"}", "k-code");
        assertThrows(IllegalArgumentException.class, () -> new TransactionTemplate(tx).executeWithoutResult(s -> log.append(bad)));
        assertEquals(0, events());
    }

    @Test
    void sixteenWritersAtOnceCannotForkTheChain() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(16);
        List<Callable<Void>> jobs = new ArrayList<>();
        for (int t = 0; t < 16; t++) {
            final int base = t * 25;
            jobs.add(() -> {
                for (int i = 0; i < 25; i++) {
                    final int n = base + i;
                    new TransactionTemplate(tx).executeWithoutResult(s -> log.append(ev("c-" + n)));
                }
                return null;
            });
        }
        for (Future<Void> f : pool.invokeAll(jobs)) f.get();
        pool.shutdown();
        EventLog.Verification v = log.verify();
        assertTrue(v.ok(), v.problem());
        assertEquals(400, v.rows());
        assertNotNull(v.headHash());
        assertEquals(400, jdbc.queryForObject("select max(id) from act_event", Long.class));
    }

    @Test
    void theHashDoesNotDependOnTheDatabasePrecision() {
        append(0, 3);
        // read back and verify twice: the stored timestamps are whole milliseconds
        assertTrue(log.verify().ok());
        assertTrue(log.verify().ok());
    }

    static String sha256(String s) { return ActTestBase.sha256(s); }
}
