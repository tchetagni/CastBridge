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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The module against the real MySQL 8.4 of production, when Docker is available (skipped otherwise: the H2 tests still run). What H2 cannot show: the V63 migration on
 * MySQL, the head lock of the chains under 16 concurrent writers (InnoDB row lock), a journal and a report end to end. Runs on a real MySQL 8.4 with the production JDBC parameters (Docker needed).
 */
@Testcontainers(disabledWithoutDocker = true)
class ActivationsMySqlTest extends ActTestBase {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withCommand("--innodb-buffer-pool-size=128M", "--performance-schema=OFF", "--character-set-server=utf8mb4", "--innodb-redo-log-capacity=16M")
            .withTmpFs(java.util.Map.of("/var/lib/mysql", "rw,size=512m"));

    /** The production URL parameters (application.yml), so that DATETIME columns are read exactly as in production: Connector/J 9.x gives java.time.LocalDateTime. */
    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> MYSQL.getJdbcUrl() + "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true");
        r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);
    }

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

    @Autowired IssuanceTap issuanceTap;
    @Autowired LicenseAuditTap auditTap;
    @Autowired Reconciler reconciler;
    @Autowired Snapshots snapshots;
    @Autowired Checkpoints checkpoints;
    @Autowired ErasureHook erasure;

    private String get(String path) throws Exception {
        var r = mvc.perform(adminGet(path)).andReturn().getResponse();
        assertEquals(200, r.getStatus(), path + " -> " + r.getContentAsString());
        return r.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }

    /** C1: every untyped DATETIME read of the module (cursor taps, report projection, state refresh, lists, fiches, exports, archive, nightly job) on MySQL 8.4 / Connector/J 9.x. */
    @Test
    void theWholeApiJournalReportAndJobPathReadsDatetimesOnMySql() throws Exception {
        resetModule();
        clock.set(Instant.now());
        Dev d = dev();
        String serverToken = serverIssuedProduction(d);   // a server issuance: lic_issuance row for the issuance tap
        String t = trialToken(DESK, dev(), clock.nowMs() - 3_600_000L);
        String journal = new JournalBuilder(DESK, 1, "desk", clock.nowMs(), 1).entry(clock.nowMs(), "issue", JournalBuilder.issueFields(t, null)).build();
        assertEquals(200, uploadJournal(journal).getResponse().getStatus());
        assertTrue(issuanceTap.runOnce() >= 1, "the issuance tap reads issued_at / not_after / not_before");
        var i = install();
        assertEquals(200, report(i, reportJson(d, clock.nowMs(), "PRODUCTION", List.of(serverToken), null)).getResponse().getStatus());
        // a second report of the same TV later: the state refresh reads the previous values (open_all_until, usage_to...) back
        clock.set(Instant.now().plusSeconds(3 * 3600));
        assertEquals(200, report(i, reportJson(d, clock.nowMs(), "PRODUCTION", List.of(serverToken), null)).getResponse().getStatus());

        var lic = jdbc.queryForObject("select license_id from act_key where fp = ?", String.class, sha256(serverToken));
        licenses.suspend(OWNER, lic, "test MySQL");
        licenses.revokeKey(OWNER, kid(BURNED), "test MySQL");
        assertTrue(auditTap.runOnce() >= 1, "the licence audit tap reads at / revoked_at");

        assertTrue(get("/api/v1/admin/activations/activations").contains(sha256(serverToken)));
        assertTrue(get("/api/v1/admin/activations/activations?seen=true&limit=1").contains("items"));
        get("/api/v1/admin/activations/activations/" + sha256(serverToken));
        String tvs = get("/api/v1/admin/activations/tvs");
        assertTrue(tvs.contains("lastReportAt"), tvs);
        get("/api/v1/admin/activations/tvs?limit=1");
        get("/api/v1/admin/activations/tvs/" + d.code());
        get("/api/v1/admin/activations/dashboard");
        get("/api/v1/admin/activations/alerts");
        get("/api/v1/admin/activations/tools");
        String integrity = get("/api/v1/admin/activations/integrity");
        assertTrue(integrity.contains("\"ok\":true"), integrity);
        get("/api/v1/admin/activations/checkpoints");
        get("/api/v1/admin/activations/read-audit");
        get("/api/v1/admin/activations/changes?after=0");
        for (String what : List.of("activations", "tvs", "events", "alerts", "reads")) {
            for (String fmt : List.of("jsonl", "csv")) get("/api/v1/admin/activations/export?what=" + what + "&format=" + fmt);
        }
        snapshots.snapshotDay(java.time.LocalDate.now(java.time.ZoneOffset.UTC));
        snapshots.snapshotMonth(java.time.YearMonth.now(java.time.ZoneOffset.UTC));
        get("/api/v1/admin/activations/dashboard");
        checkpoints.create(java.time.LocalDate.now(java.time.ZoneOffset.UTC));
        get("/api/v1/admin/activations/checkpoints");
        reconciler.reconcileAll();
        erasure.runOnce();
        assertEquals(200, mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/admin/activations/archive").header("Authorization", ADMIN)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"reason\":\"test MySQL\",\"remove\":false}")).andReturn().getResponse().getStatus());
        clock.set(Instant.now().plus(java.time.Duration.ofDays(900)));
        assertEquals(200, mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/admin/activations/archive").header("Authorization", ADMIN)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"reason\":\"test MySQL retrait\",\"remove\":true}")).andReturn().getResponse().getStatus());
        assertTrue(log.verify().ok(), log.verify().problem());
        assertTrue(readAudit.verify().ok());
    }
}
