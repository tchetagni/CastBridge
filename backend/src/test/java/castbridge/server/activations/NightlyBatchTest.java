package castbridge.server.activations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionExecution;
import org.springframework.transaction.TransactionExecutionListener;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;

/**
 * Audit w23-01 M2. The nightly reconciliation works in bounded batches, one transaction per batch: it never holds the head lock of the chain (taken by the first event it
 * writes) for the whole job, which would make every report and every journal wait with a pooled connection open.
 */
class NightlyBatchTest extends ActTestBase {
    @Autowired Reconciler reconciler;
    @Autowired PlatformTransactionManager txm;

    static final int KEYS = 1_300;

    @BeforeEach
    void start() { resetModule(); }

    @AfterEach
    void stop() {
        clock.reset();
        jdbc.update("delete from act_key");
    }

    private void seedEndedActivations(Instant now) {
        Timestamp past = Timestamp.from(now.minus(Duration.ofDays(2)));
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < KEYS; i++) {
            String fp = "%064x".formatted(0xABC000L + i);
            rows.add(new Object[] {fp, fp.substring(0, 8), past, past, Timestamp.from(now.minus(Duration.ofDays(1))), past});
        }
        jdbc.batchUpdate("insert into act_key (fp, tag, form, kind, subject, state, state_at, flags, super, issued_at, expires_at, usage_to, unlimited, first_seen_tv_at, last_seen_tv_at, kid) "
                + "values (?,?,'ENVELOPE','TRIAL','tv','ACTIVATED',?,',seen_on_tv,declared_journal,',false,?,?,?,false,?,?,'0011223344556677')", rows.stream()
                .map(r -> new Object[] {r[0], r[1], r[2], r[3], r[3], r[4], r[5], r[5]}).toList());
    }

    @Test
    void theNightlyJobCommitsInBatchesAndNeverHoldsTheHeadLockForTheWholeRun() {
        Instant now = Instant.parse("2026-10-10T08:00:00Z");
        clock.set(now);
        seedEndedActivations(now);
        List<Integer> perCommit = new ArrayList<>();
        int[] last = {jdbc.queryForObject("select count(*) from act_event", Integer.class)};
        TransactionExecutionListener l = new TransactionExecutionListener() {
            @Override
            public void afterCommit(TransactionExecution tx, Throwable failure) {
                int n = jdbc.queryForObject("select count(*) from act_event", Integer.class);
                if (n != last[0]) perCommit.add(n - last[0]);
                last[0] = n;
            }
        };
        ((AbstractPlatformTransactionManager) txm).addListener(l);
        try {
            reconciler.reconcileAll();
        } finally {
            ((AbstractPlatformTransactionManager) txm).setTransactionExecutionListeners(List.of());
        }
        assertEquals(KEYS, jdbc.queryForObject("select count(*) from act_key where state = 'ENDED'", Integer.class), "every ended activation was seen");
        int events = perCommit.stream().mapToInt(Integer::intValue).sum();
        assertTrue(events >= KEYS, "one ENDED event per activation at least: " + events);
        int max = perCommit.stream().mapToInt(Integer::intValue).max().orElse(0);
        assertTrue(max <= 600, "no transaction writes more than a batch of events (it holds the head lock until it ends): " + perCommit);
        assertTrue(perCommit.size() >= 3, "several commits: " + perCommit);
        assertTrue(log().verify().ok());
    }

    @Autowired EventLog logBean;

    private EventLog log() { return logBean; }

    @Test
    void theJobIsIdempotentAndASecondRunWritesOnlyItsOwnLine() {
        Instant now = Instant.parse("2026-10-10T08:00:00Z");
        clock.set(now);
        seedEndedActivations(now);
        reconciler.reconcileAll();
        int after = jdbc.queryForObject("select count(*) from act_event", Integer.class);
        reconciler.reconcileAll();
        assertEquals(after, jdbc.queryForObject("select count(*) from act_event", Integer.class), "the same day: RECONCILED has an idempotence key, nothing else changed");
    }
}
