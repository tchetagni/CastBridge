package castbridge.server.activations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Audit w23-01 M6. Two contradictory journals of the same tool (same entry numbers, other content: a cloned phone, a copied key) uploaded AT THE SAME TIME: uploads of one tool
 * chain are serialised (the row of the tool is locked first), so the second one is judged against the first and quarantined, never both applied.
 */
class ConcurrentJournalTest extends ActTestBase {
    @Autowired PlatformTransactionManager txm;
    @Autowired JournalService journals;

    @BeforeEach
    void start() {
        resetModule();
        clock.set(Instant.parse("2026-10-10T08:00:00Z"));
    }

    @AfterEach
    void stop() { clock.reset(); }

    @Test
    void twoContradictoryUploadsAtTheSameTimeNeverBothApply() throws Exception {
        for (int round = 0; round < 6; round++) {
            resetModule();
            final long seq = 1;
            Dev d1 = dev(), d2 = dev();
            String t1 = trialToken(DESK, d1, clock.nowMs() - 3_600_000L), t2 = trialToken(DESK, d2, clock.nowMs() - 3_600_000L);
            String j1 = new JournalBuilder(DESK, seq, "desk", clock.nowMs(), 1).entry(clock.nowMs(), "issue", JournalBuilder.issueFields(t1, null)).build();
            String j2 = new JournalBuilder(DESK, seq + 1, "desk", clock.nowMs(), 1).entry(clock.nowMs() + 1, "issue", JournalBuilder.issueFields(t2, null)).build();
            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch go = new CountDownLatch(1);
            List<Callable<Integer>> jobs = new ArrayList<>();
            for (String j : List.of(j1, j2)) {
                jobs.add(() -> {
                    go.await();
                    return uploadJournal(j).getResponse().getStatus();
                });
            }
            List<Future<Integer>> fs = new ArrayList<>();
            for (Callable<Integer> c : jobs) fs.add(pool.submit(c));
            go.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : fs) statuses.add(f.get());
            pool.shutdown();
            assertTrue(statuses.contains(200), "one of them is applied: " + statuses);
            assertEquals(1, jdbc.queryForObject("select count(*) from act_event where idem_key like 'J:%:1'", Integer.class), "entry 1 of the tool exists once: " + statuses);
            assertEquals(1, jdbc.queryForObject("select count(*) from act_key", Integer.class), "only the winner's activation is in the inventory: " + statuses);
            assertTrue(statuses.stream().anyMatch(s -> s == 422 || s == 409), "the loser is quarantined or refused, not applied: " + statuses);
            assertTrue(jdbc.queryForObject("select chain_ok from act_tool", Boolean.class) || jdbc.queryForObject("select count(*) from act_alert where type = 'JOURNAL_BROKEN'", Integer.class) >= 1);
        }
    }
}
