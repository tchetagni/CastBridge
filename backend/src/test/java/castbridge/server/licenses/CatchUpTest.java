package castbridge.server.licenses;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import castbridge.server.wallet.Acts;
import castbridge.server.wallet.CatchUpPolicy;
import castbridge.server.wallet.CatchUpPolicy.Verdict;
import com.fasterxml.jackson.databind.JsonNode;
import java.security.KeyPair;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/**
 * Le rattrapage du portefeuille quand la licence apparaît tard (règles du propriétaire du 2026-10-04, conception W23-B § 5) : une fois par (licence, période), rien pour un intervalle suspendu,
 * ouverture illimitée 5 000 + 50 puis 1 000 + 10 à partir du mois suivant, automatique pour les périodes commencées au plus 90 jours avant la première notification, jusqu'à 366 jours si
 * l'émission est déclarée ou acceptée par le propriétaire, jamais au-delà.
 */
class CatchUpTest extends RegistrarTestBase {

    @Test
    void thePolicyTableOnItsBoundaries() {
        Instant r = T0;
        for (boolean declared : new boolean[] {false, true}) {
            assertEquals(Verdict.PAY, CatchUpPolicy.judge(r, r, declared), "période commencée à la notification");
            assertEquals(Verdict.PAY, CatchUpPolicy.judge(r.plusSeconds(86_400L * 30), r, declared), "période future : jamais retenue");
            assertEquals(Verdict.PAY, CatchUpPolicy.judge(r.minusSeconds(86_400L * 89), r, declared));
            assertEquals(Verdict.PAY, CatchUpPolicy.judge(r.minusSeconds(86_400L * 90), r, declared), "90 jours exactement : automatique");
            assertEquals(Verdict.NEVER, CatchUpPolicy.judge(r.minusSeconds(86_400L * 366 + 1), r, declared), "au-delà de 366 jours : jamais");
            assertEquals(Verdict.NEVER, CatchUpPolicy.judge(r.minusSeconds(86_400L * 400), r, declared));
        }
        assertEquals(Verdict.HELD, CatchUpPolicy.judge(r.minusSeconds(86_400L * 90 + 1), r, false), "entre 90 et 366 jours, non déclarée : retenue");
        assertEquals(Verdict.HELD, CatchUpPolicy.judge(r.minusSeconds(86_400L * 366), r, false));
        assertEquals(Verdict.PAY, CatchUpPolicy.judge(r.minusSeconds(86_400L * 90 + 1), r, true), "déclarée : versée");
        assertEquals(Verdict.PAY, CatchUpPolicy.judge(r.minusSeconds(86_400L * 366), r, true), "366 jours exactement : versée");
    }

    @Test
    void anUndeclaredLateLicencePaysTheRecentPeriodsHoldsTheOldOnesAndReleasesThemOnceWhenDeclared() throws Exception {
        Registered dev = registerApp("tv");
        Acts.Tv tv = tv();
        String lic = licenseId();
        String nonce = nonce16();
        // clé à durée dont le droit usage commence 200 jours avant la notification (période 0 à -200 j, 1 à -170, …, 6 à -20)
        String token = production(ISSUER, tv, lic, null, NOW - HOUR, List.of("usage|duree|" + (NOW - 200 * DAY) + "|" + (NOW + 200 * DAY)), nonce);
        JsonNode s = ok(sync(dev, tv, token));
        assertEquals(3000, balance(tv.code(), "NDEM"), "périodes à -80, -50 et -20 jours : automatiques");
        assertEquals(30, balance(tv.code(), "MBOKO"));
        assertTrue(noticeReasons(s).contains("CATCHUP_HELD"), "jamais de retenue silencieuse : " + s);
        // rejeu : rien de plus
        ok(sync(dev, tv, token));
        assertEquals(3000, balance(tv.code(), "NDEM"));
        // l'outil du propriétaire déclare l'émission (registre) : les quatre périodes retenues sont versées, UNE fois
        String seat = WireActivation.defaultSeat(lic, tv.factors());
        importRegistry(List.of(issueEvent(ISSUER, NOW - HOUR, lic, seat, tv, nonce)));
        JsonNode after = ok(sync(dev, tv, token));
        assertEquals(7000, balance(tv.code(), "NDEM"), "7 périodes en tout");
        assertEquals(70, balance(tv.code(), "MBOKO"));
        assertFalse(noticeReasons(after).contains("CATCHUP_HELD"), after.toString());
        ok(sync(dev, tv, token));
        ok(sync(dev, tv, token));
        assertEquals(7000, balance(tv.code(), "NDEM"), "une seule pose par (licence, période)");
        assertEquals(70, balance(tv.code(), "MBOKO"));
    }

    @Test
    void anEmissionAcceptedByTheOwnerAfter200DaysPaysTheOpeningAndEveryPeriod() throws Exception {
        Registered dev = registerApp("tv");
        Acts.Tv tv = tv();
        String lic = licenseId();
        String token = production(ISSUER, tv, lic, null, NOW - 200 * DAY, null);
        JsonNode s = ok(sync(dev, tv, token));
        assertEquals("NONE", s.path("edition").path("ed").asText());
        assertTrue(noticeReasons(s).contains("REGISTRATION_REVIEW"));
        assertEquals(0, balance(tv.code(), "NDEM"));
        Instant decidedAt = T0;
        var r = registrar.decide(OWNER, Hashing.sha256Hex(token), true, "TV vue chez le client", decidedAt);
        assertEquals(ReportedActivationRegistrar.Status.REGISTERED, r.status(), r.reason());
        ok(sync(dev, tv, token));
        // ouverture illimitée 5 000 + 50, puis 1 000 + 10 pour chacune des 6 périodes écoulées (jamais de période 0)
        assertEquals(5000 + 6 * 1000, balance(tv.code(), "NDEM"));
        assertEquals(50 + 6 * 10, balance(tv.code(), "MBOKO"));
        ok(sync(dev, tv, token));
        assertEquals(5000 + 6 * 1000, balance(tv.code(), "NDEM"), "rejeu : rien de plus");
    }

    @Test
    void nothingIsEverPaidBeyond366DaysEvenWhenTheOwnerAccepts() throws Exception {
        Registered dev = registerApp("tv");
        Acts.Tv tv = tv();
        String lic = licenseId();
        String token = production(ISSUER, tv, lic, null, NOW - 401 * DAY, null);
        JsonNode s = ok(sync(dev, tv, token));
        assertTrue(noticeReasons(s).contains("REGISTRATION_REVIEW"));
        assertEquals(0, balance(tv.code(), "NDEM"));
        var r = registrar.decide(OWNER, Hashing.sha256Hex(token), true, "TV restée hors ligne plus d'un an", T0);
        assertEquals(ReportedActivationRegistrar.Status.REGISTERED, r.status(), r.reason());
        ok(sync(dev, tv, token));
        // l'ouverture (début à -401 j) et la période 1 (-371 j) sont au-delà de 366 jours : jamais ; périodes 2 à 13 (-341 à -11 j) : 12 tranches
        assertEquals(12 * 1000, balance(tv.code(), "NDEM"), "ni l'ouverture ni les périodes plus vieilles que 366 jours");
        assertEquals(12 * 10, balance(tv.code(), "MBOKO"));
        ok(sync(dev, tv, token));
        assertEquals(12 * 1000, balance(tv.code(), "NDEM"));
    }

    @Test
    void aSuspendedIntervalIsNeverPaidWhateverTheCatchUp() throws Exception {
        Registered dev = registerApp("tv");
        Acts.Tv tv = tv();
        String lic = licenseId();
        String token = production(ISSUER, tv, lic, null, NOW - 200 * DAY, null);
        ok(sync(dev, tv, token));
        registrar.decide(OWNER, Hashing.sha256Hex(token), true, "TV vue chez le client", T0);
        // la licence est suspendue depuis 100 jours (aucun journal d'audit : première observation = updated_at)
        jdbc.update("UPDATE lic_license SET state = 'SUSPENDED', updated_at = ? WHERE license_id = ?", Timestamp.from(T0.minusSeconds(100 * 86_400L)), lic);
        ok(sync(dev, tv, token));
        // périodes à -170, -140 et -110 jours (actives) : payées ; -80, -50 et -20 jours (suspendue) : rien ; pas d'ouverture illimitée (la licence n'est plus active)
        assertEquals(3000, balance(tv.code(), "NDEM"));
        assertEquals(30, balance(tv.code(), "MBOKO"));
    }

    @Test
    void severalNotificationsAtTheSameTimePayEachPeriodOnce() throws Exception {
        Registered dev = registerApp("tv");
        Acts.Tv tv = tv();
        String lic = licenseId();
        String token = production(ISSUER, tv, lic, null, NOW - HOUR, 90);
        ExecutorService pool = Executors.newFixedThreadPool(6);
        List<Future<Integer>> fs = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            Callable<Integer> c = () -> sync(dev, tv, token).getResponse().getStatus();
            fs.add(pool.submit(c));
        }
        int okCount = 0;
        for (Future<Integer> f : fs) if (f.get() == 200) okCount++;
        pool.shutdown();
        assertTrue(okCount >= 1, "au moins une synchronisation aboutit");
        ok(sync(dev, tv, token));
        assertEquals(1, count("SELECT COUNT(*) FROM lic_license WHERE license_id = ?", lic));
        assertEquals(1, count("SELECT COUNT(*) FROM lic_seat s JOIN lic_license l ON l.id = s.license_pk WHERE l.license_id = ?", lic));
        assertEquals(1000, balance(tv.code(), "NDEM"), "une seule pose par (licence, période)");
        assertEquals(10, balance(tv.code(), "MBOKO"));
    }
}
