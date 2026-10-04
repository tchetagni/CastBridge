package castbridge.server.activations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
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
 * Audit w23-01 M7. The taps read the licence tables by an id cursor; a row of a SMALLER id can be committed AFTER a row of a greater id that was already read (two concurrent
 * transactions): with a plain {@code id > cursor} it is skipped for ever (a revocation never marks its activations). A row is only taken once it is older than the safety lag, and
 * the tap stops at the first row that is not (so the cursor never goes past a row that may still appear).
 */
class CursorLagTest extends ActTestBase {
    @Autowired LicenseAuditTap auditTap;
    @Autowired PlatformTransactionManager txm;

    @BeforeEach
    void start() {
        resetModule();
        clock.setTapLag(Duration.ofSeconds(30));
    }

    @AfterEach
    void stop() { clock.reset(); }

    private void revoke(String kid, Instant at) {
        jdbc.update("insert into lic_revocation (kid, reason, revoked_by, revoked_at) values (?,?,?,?)", kid, "test M7", "test", Timestamp.from(at));
    }

    @Test
    void aRowCommittedLateWithASmallerIdIsNotSkipped() throws Exception {
        Instant real = Instant.now();
        clock.set(real.plusSeconds(1));
        CountDownLatch inserted = new CountDownLatch(1), commit = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        // transaction 1 inserts the row of a smaller id and stays open
        Future<?> t1 = pool.submit(() -> new TransactionTemplate(txm).executeWithoutResult(s -> {
            revoke(SPARE_KID, real);
            inserted.countDown();
            try {
                commit.await();
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }));
        inserted.await();
        revoke(BURNED_KID, real.plusMillis(5));   // transaction 2: a greater id, committed first
        auditTap.runOnce();                        // 1 s later: too recent to be final, nothing may be taken, the cursor stays behind both rows
        assertEquals(0, jdbc.queryForObject("select count(*) from act_event where type = 'REVOKED_KEY'", Integer.class), "a row that may still be joined by an older id is not final");
        commit.countDown();
        t1.get();
        pool.shutdown();
        clock.set(real.plus(Duration.ofSeconds(90)));
        auditTap.runOnce();
        assertEquals(2, jdbc.queryForObject("select count(*) from act_event where type = 'REVOKED_KEY'", Integer.class), "both revocations were taken, the late one included");
        int events = jdbc.queryForObject("select count(*) from act_event", Integer.class);
        auditTap.runOnce();
        assertEquals(events, jdbc.queryForObject("select count(*) from act_event", Integer.class), "the cursor remembers");
        assertTrue(jdbc.queryForObject("select val from act_cursor where name = 'lic_revocation'", Long.class) > 0);
    }

    static final String SPARE_KID = kid(SPARE), BURNED_KID = kid(BURNED);
}
