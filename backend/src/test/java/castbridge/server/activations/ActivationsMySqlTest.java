package castbridge.server.activations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The module against the real MySQL 8.4 of production, when Docker is available (skipped otherwise: the H2 tests still run). What H2 cannot show: the V65 migration on
 * MySQL, the head lock of the chains under 16 concurrent writers (InnoDB row lock), a journal and a report end to end. NOT run in the session that wrote it (no Docker daemon).
 */
@Testcontainers(disabledWithoutDocker = true)
class ActivationsMySqlTest extends ActTestBase {
    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withCommand("--innodb-buffer-pool-size=128M", "--performance-schema=OFF", "--character-set-server=utf8mb4", "--innodb-redo-log-capacity=16M")
            .withTmpFs(java.util.Map.of("/var/lib/mysql", "rw,size=512m"));

    @Autowired EventLog log;
    @Autowired ReadAudit readAudit;
    @Autowired PlatformTransactionManager tx;

    @AfterEach
    void stop() { clock.reset(); }

    @Test
    void sixteenWritersCannotForkTheChainsOnMySql() throws Exception {
        resetModule();
        ExecutorService pool = Executors.newFixedThreadPool(16);
        List<Callable<Void>> jobs = new ArrayList<>();
        for (int t = 0; t < 16; t++) {
            final int base = t * 25;
            jobs.add(() -> {
                for (int i = 0; i < 25; i++) {
                    final int n = base + i;
                    new TransactionTemplate(tx).executeWithoutResult(s -> log.append(new EventLog.NewEvent("ISSUED", 1_759_600_000_000L, "f".repeat(64), "abcdef0123456789", "lic-1", "0011223344556677", "TOOL",
                            "0011223344556677", "JOURNAL", null, "{\"k\":1}", "mysql-" + n)));
                    readAudit.record("api-token", "OWNER", "api", "/api/v1/admin/activations/dashboard", null, null, 1, false);
                }
                return null;
            });
        }
        for (Future<Void> f : pool.invokeAll(jobs)) f.get();
        pool.shutdown();
        assertTrue(log.verify().ok(), log.verify().problem());
        assertEquals(400, log.verify().rows());
        assertTrue(readAudit.verify().ok());
        assertEquals(400, readAudit.verify().rows());
    }

    @Test
    void aJournalAndAReportEndToEndOnMySql() throws Exception {
        resetModule();
        clock.set(Instant.now());
        var d = dev();
        String t = trialToken(DESK, d, clock.nowMs() - 3_600_000L);
        String journal = new JournalBuilder(DESK, 1, "desk", clock.nowMs(), 1).entry(clock.nowMs(), "issue", JournalBuilder.issueFields(t, null)).build();
        assertEquals(200, uploadJournal(journal).getResponse().getStatus());
        var i = install();
        assertEquals(200, report(i, reportJson(d, clock.nowMs(), "TRIAL", List.of(t), null)).getResponse().getStatus());
        assertEquals("ACTIVATED", jdbc.queryForObject("select state from act_key where fp = ?", String.class, sha256(t)));
        assertTrue(log.verify().ok());
        assertEquals(0, jdbc.queryForObject("select count(*) from act_alert", Integer.class));
    }
}
