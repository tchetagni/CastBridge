package castbridge.server.licenses;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import castbridge.server.licenses.ReportedActivationRegistrar.Presented;
import castbridge.server.licenses.ReportedActivationRegistrar.Registration;
import castbridge.server.licenses.ReportedActivationRegistrar.Status;
import castbridge.server.licenses.ReportedActivationRegistrar.Via;
import castbridge.server.wallet.Acts;
import com.fasterxml.jackson.databind.JsonNode;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Le chemin complet et la concurrence sur un VRAI MySQL 8.4 (l'image et les paramètres JDBC de production : {@code DATETIME} lus en {@code LocalDateTime}, verrous InnoDB), quand Docker est
 * disponible (sauté sinon : le rapport dit s'il a tourné). Ce que H2 ne montre pas : la migration V64, les 16 présentations simultanées d'une même licence à un poste, la lecture des dates
 * (leçon C1 de w23-01), le rattrapage sous synchronisations simultanées.
 */
@Testcontainers(disabledWithoutDocker = true)
class RegistrationMySqlTest extends RegistrarTestBase {
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

    private static final String KEY = rawPublic(pair());

    private MvcResult report(Registered dev, Acts.Tv tv, KeyPair install, String... tokens) throws Exception {
        StringBuilder sb = new StringBuilder("{\"v\":1,\"deviceCode\":\"").append(tv.code()).append("\",\"app\":{\"code\":1432,\"name\":\"0.14.32-beta\"},\"activations\":[");
        for (int i = 0; i < tokens.length; i++) sb.append(i > 0 ? "," : "").append('"').append(tokens[i]).append('"');
        sb.append("],\"state\":{\"edition\":\"PRODUCTION\",\"usageTo\":null,\"super\":false,\"openAllUntil\":0,\"unlockUntil\":0,\"trialResets\":0,\"installedAt\":{},\"commands\":[]},\"at\":")
                .append(System.currentTimeMillis()).append(",\"bind\":").append(bind(install, tv.code(), dev.publicId(), System.currentTimeMillis())).append('}');
        return mvc.perform(post("/api/v1/activations/report").header("Authorization", dev.auth()).contentType(MediaType.APPLICATION_JSON).content(sb.toString())).andReturn();
    }

    @Test
    void theFullPathOnMySqlNoLicenceThenSyncNoneThenReportThenUnlimitedAndReplaysPayOnce() throws Exception {
        clock.unfreeze();
        Registered dev = registerApp("tv");
        Acts.Tv tv = tv();
        KeyPair install = installOf(tv);
        String lic = licenseId();
        long issued = System.currentTimeMillis() - HOUR;
        JsonNode first = ok(sync(dev, tv, production(ISSUER, tv, "list", null, issued, null)));
        assertEquals("NONE", first.path("edition").path("ed").asText(), first.toString());
        assertTrue(noticeReasons(first).contains("LICENSE_PENDING"));
        assertEquals(0, count("SELECT COUNT(*) FROM lic_license WHERE license_id = ?", lic));
        String token = production(ISSUER, tv, lic, null, issued, null);
        MvcResult r = report(dev, tv, install, token);
        assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        assertEquals("REGISTERED", body(r).path("registration").get(0).path("status").asText(), body(r).toString());
        JsonNode next = ok(sync(dev, tv, token));
        assertEquals("UNLIMITED", next.path("edition").path("ed").asText(), next.toString());
        assertEquals(5000, balance(tv.code(), "NDEM"));
        assertEquals(50, balance(tv.code(), "MBOKO"));
        ok(sync(dev, tv, token));
        ok(sync(dev, tv, token));
        assertEquals(5000, balance(tv.code(), "NDEM"), "rejeu : aucun double versement");
        assertEquals(50, balance(tv.code(), "MBOKO"));
        assertEquals(1, count("SELECT COUNT(*) FROM lic_license WHERE license_id = ?", lic));
        assertEquals(1, count("SELECT COUNT(*) FROM lic_seat s JOIN lic_license l ON l.id = s.license_pk WHERE l.license_id = ?", lic));
    }

    @Test
    void sixteenThreadsOneLicenceOneSeatGiveExactlyOneSeatAndFifteenOverQuota() throws Exception {
        String lic = licenseId();
        int n = 16;
        List<Acts.Tv> tvs = new ArrayList<>();
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Acts.Tv tv = tv();
            tvs.add(tv);
            tokens.add(production(ISSUER, tv, lic, null, NOW - HOUR, 90));
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
        int registered = 0, over = 0;
        for (Future<Registration> f : fs) {
            Registration r = f.get();
            if (r.status() == Status.REGISTERED) registered++;
            else if (r.status() == Status.PENDING_DECISION && "OVER_QUOTA".equals(r.reason())) over++;
        }
        pool.shutdown();
        assertEquals(1, registered, "un seul poste");
        assertEquals(n - 1, over, "les autres : OVER_QUOTA");
        assertEquals(1, count("SELECT COUNT(*) FROM lic_license WHERE license_id = ?", lic));
        assertEquals(1, count("SELECT COUNT(*) FROM lic_seat s JOIN lic_license l ON l.id = s.license_pk WHERE l.license_id = ? AND s.state = 'ACTIVE'", lic));
        assertEquals(1, count("SELECT COUNT(*) FROM lic_issuance i JOIN lic_license l ON l.id = i.license_pk WHERE l.license_id = ?", lic));
        assertEquals(n, count("SELECT COUNT(*) FROM lic_registration WHERE license_id = ?", lic));
    }

    @Test
    void sixteenThreadsOneTokenCreateOneLicenceOneSeatOneIssuance() throws Exception {
        Acts.Tv tv = tv();
        String lic = licenseId();
        String token = production(ISSUER, tv, lic, null, NOW - HOUR, 90);
        int n = 16;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Registration>> fs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Callable<Registration> c = () -> {
                go.await();
                return registrar.register(new Presented(token, tv.code(), rawPublic(installOf(tv)), true), Via.WALLET, T0);
            };
            fs.add(pool.submit(c));
        }
        go.countDown();
        int registered = 0;
        for (Future<Registration> f : fs) {
            Registration r = f.get();
            assertTrue(r.registered(), r.status() + " " + r.reason());
            if (r.status() == Status.REGISTERED) registered++;
        }
        pool.shutdown();
        assertTrue(registered >= 1);
        assertEquals(1, count("SELECT COUNT(*) FROM lic_license WHERE license_id = ?", lic));
        assertEquals(1, count("SELECT COUNT(*) FROM lic_seat s JOIN lic_license l ON l.id = s.license_pk WHERE l.license_id = ?", lic));
        assertEquals(1, count("SELECT COUNT(*) FROM lic_issuance i JOIN lic_license l ON l.id = i.license_pk WHERE l.license_id = ?", lic));
        assertEquals(1, count("SELECT COUNT(*) FROM lic_registration WHERE license_id = ?", lic));
        assertEquals(1, count("SELECT COUNT(*) FROM lic_client WHERE name = ?", ReportedActivationRegistrar.CLIENT_NAME), "le client technique n'est créé qu'une fois");
    }

    @Test
    void theOwnersDecisionAndTheListReadDatesOnMySql() throws Exception {
        Acts.Tv tv = tv();
        String lic = licenseId();
        String token = production(ISSUER, tv, lic, null, NOW - 5 * DAY, null);
        Registration pending = registrar.register(new Presented(token, tv.code(), rawPublic(installOf(tv)), true), Via.WALLET, T0);
        assertEquals("INSTALL_TIME_UNKNOWN", pending.reason());
        assertTrue(registrar.pending(50).stream().anyMatch(p -> p.fp().equals(pending.fp()) && p.firstServerAt() != null));
        Registration done = registrar.decide(OWNER, pending.fp(), true, "TV vue chez le client", T0);
        assertEquals(Status.REGISTERED, done.status(), done.reason());
        assertEquals(1, count("SELECT COUNT(*) FROM lic_registration WHERE fp = ? AND declared = TRUE", pending.fp()));
    }

    @Test
    void orderIndependenceHoldsOnMySqlToo() throws Exception {
        Random rnd = new Random(42L);
        String expected = null;
        for (int it = 0; it < 12; it++) {
            Acts.Tv tv = tv();
            String lic = licenseId();
            long issuedA = NOW - 2 * HOUR;
            String nonceA = nonce16(), nonceB = nonce16();
            String seat = WireActivation.defaultSeat(lic, tv.factors());
            String tokA = production(ISSUER, tv, lic, null, issuedA, List.of("usage|duree|" + issuedA + "|" + (issuedA + 90 * DAY)), nonceA);
            String tokB = production(ISSUER, tv, lic, null, NOW - HOUR, List.of("usage|duree|" + issuedA + "|" + (issuedA + 120 * DAY)), nonceB);
            var events = List.of(licenseEvent(ISSUER, issuedA - 1000, lic, 1), issueEvent(ISSUER, issuedA, lic, seat, tv, nonceA));
            List<String> ops = new ArrayList<>(List.of("A", "B", "REG"));
            Collections.shuffle(ops, rnd);
            for (String op : ops) {
                if (op.equals("A")) registrar.register(new Presented(tokA, tv.code(), rawPublic(installOf(tv)), true), Via.WALLET, T0);
                else if (op.equals("B")) registrar.register(new Presented(tokB, tv.code(), rawPublic(installOf(tv)), true), Via.WALLET, T0);
                else importRegistry(events);
            }
            String state = jdbc.queryForObject("SELECT CONCAT(seats_allowed, '|', state, '|', transfer_cap, '|', DATE_FORMAT(start_at, '%Y%m%d%H%i%s'), '|', DATE_FORMAT(end_at, '%Y%m%d%H%i%s')) FROM lic_license WHERE license_id = ?", String.class, lic)
                    + "|" + count("SELECT COUNT(*) FROM lic_seat s JOIN lic_license l ON l.id = s.license_pk WHERE l.license_id = ?", lic)
                    + "|" + count("SELECT COUNT(*) FROM lic_issuance i JOIN lic_license l ON l.id = i.license_pk WHERE l.license_id = ?", lic);
            if (expected == null) expected = state;
            assertEquals(expected, state, "ordre " + ops);
        }
    }
}
