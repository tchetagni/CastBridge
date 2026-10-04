package castbridge.server.licenses;

import static org.junit.jupiter.api.Assertions.assertEquals;

import castbridge.server.licenses.ReportedActivationRegistrar.Presented;
import castbridge.server.licenses.ReportedActivationRegistrar.Registration;
import castbridge.server.licenses.ReportedActivationRegistrar.Status;
import castbridge.server.licenses.ReportedActivationRegistrar.Via;
import castbridge.server.wallet.Acts;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Audit Opus w23-05, MEDIUM-3 : le plafond de 10 licences par jour et par clé doit être EXACT sous concurrence, sur le vrai MySQL 8.4 (même image et mêmes paramètres JDBC que la production).
 * L'audit a mesuré 12 licences créées par la même clé avec 16 présentations simultanées (pool de 6) : le plafond était compté sans verrou par clé.
 */
@Testcontainers(disabledWithoutDocker = true)
class AuditW2305RateRaceMySqlTest extends RegistrarTestBase {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withCommand("--innodb-buffer-pool-size=128M", "--performance-schema=OFF", "--character-set-server=utf8mb4", "--innodb-redo-log-capacity=16M")
            .withTmpFs(java.util.Map.of("/var/lib/mysql", "rw,size=512m"));

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> MYSQL.getJdbcUrl() + "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true");
        r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Test
    void sixteenDifferentLicencesOfTheSameKeyInParallelCreateExactlyTen() throws Exception {
        String kid = LicenseKeyring.kidOf(rawPublicBytes(RATE));
        int n = 16;
        List<Acts.Tv> tvs = new ArrayList<>();
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Acts.Tv tv = tv();
            tvs.add(tv);
            tokens.add(production(RATE, tv, licenseId(), null, NOW - HOUR, null));
        }
        for (int round = 0; round < 3; round++) {
            jdbc.update("DELETE FROM lic_registration WHERE kid = ?", kid);
            // chaque tour repart de zéro : de nouvelles licences, donc de nouveaux jetons
            tokens.clear();
            tvs.clear();
            for (int i = 0; i < n; i++) {
                Acts.Tv tv = tv();
                tvs.add(tv);
                tokens.add(production(RATE, tv, licenseId(), null, NOW - HOUR, null));
            }
            ExecutorService pool = Executors.newFixedThreadPool(n);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Registration>> fs = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                final int k = i;
                Callable<Registration> c = () -> {
                    go.await();
                    return registrar.register(new Presented(tokens.get(k), tvs.get(k).code(), rawPublic(installOf(tvs.get(k))), true), Via.WALLET, T0);
                };
                fs.add(pool.submit(c));
            }
            go.countDown();
            int registered = 0, capped = 0;
            for (Future<Registration> f : fs) {
                Registration r = f.get();
                if (r.status() == Status.REGISTERED) registered++;
                else if ("KEY_RATE".equals(r.reason())) capped++;
            }
            pool.shutdown();
            assertEquals(10, registered, "tour " + round + " : exactement 10 licences créées par la clé en un jour (le plafond)");
            assertEquals(n - 10, capped, "tour " + round + " : les autres attendent (KEY_RATE)");
            assertEquals(10, count("SELECT COUNT(*) FROM lic_registration WHERE kid = ? AND status = 'REGISTERED'", kid));
        }
    }

    /**
     * HIGH-3 sous concurrence, sur MySQL : deux licences de la même TV (décalées de 2 heures, mêmes périodes) synchronisées par 12 fils en même temps paient chaque période UNE fois (case unique
     * identité, monnaie, période), et l'ouverture illimitée une fois.
     */
    @Test
    void twoLicencesOfOneTvSyncedByTwelveThreadsPayEachPeriodOnce() throws Exception {
        for (int round = 0; round < 8; round++) oneRound(round);
    }

    private void oneRound(int round) throws Exception {
        jdbc.update("DELETE FROM lic_registration WHERE kid <> 'none'");   // le plafond par clé (10 par jour) compte les licences des tours précédents : chaque tour repart de zéro
        Acts.Tv tv = tv();
        Registered dev = registerApp("tv");
        String l1 = licenseId(), l2 = licenseId();
        licenses.create(OWNER, new LicenseService.NewLicense(l1, clients.create(OWNER, "Client race 1", null, null).id(), "PAID", 1, T0.minusSeconds(65 * 86_400L), null, null, null, null));
        licenses.create(OWNER, new LicenseService.NewLicense(l2, clients.create(OWNER, "Client race 2", null, null).id(), "PAID", 1, T0.minusSeconds(65 * 86_400L + 7200), null, null, null, null));
        String t1 = production(ISSUER, tv, l1, null, NOW - HOUR, null), t2 = production(ISSUER, tv, l2, null, NOW - HOUR, null);
        int n = 12;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> fs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            final boolean flip = i % 2 == 0;
            Callable<Integer> c = () -> {
                go.await();
                return (flip ? sync(dev, tv, t1, t2) : sync(dev, tv, t2, t1)).getResponse().getStatus();
            };
            fs.add(pool.submit(c));
        }
        go.countDown();
        int okCount = 0;
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : fs) {
            int st = f.get();
            statuses.add(st);
            if (st == 200) okCount++;
        }
        pool.shutdown();
        assertEquals(true, okCount >= 1, "tour " + round + " : au moins une synchronisation aboutit");
        com.fasterxml.jackson.databind.JsonNode last = ok(sync(dev, tv, t1, t2));
        assertEquals(5000 + 2 * 1000, balance(tv.code(), "NDEM"), "tour " + round + " " + last + " statuts des synchronisations parallèles " + statuses + " : ouverture + deux périodes, une seule fois chacune, quelle que soit la licence");
        assertEquals(50 + 2 * 10, balance(tv.code(), "MBOKO"), "tour " + round);
        assertEquals(3, count("SELECT COUNT(*) FROM wallet_period_claim WHERE holder = ? AND cur IN ('NDEM', 'OPEN')", tv.code()), "tour " + round + " : une case par (monnaie, période) et l'ouverture");
    }
}
