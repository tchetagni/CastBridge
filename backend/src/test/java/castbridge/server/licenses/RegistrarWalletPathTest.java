package castbridge.server.licenses;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import castbridge.server.wallet.Acts;
import com.fasterxml.jackson.databind.JsonNode;
import java.security.KeyPair;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Le trou constaté en production (2026-10-04) : une TV synchronisée à {@code /api/v1/wallet/sync} avec une activation de production vérifiée n'avait aucune licence ni aucun poste, donc
 * l'édition AUCUNE (« licence en attente »). Le propriétaire a dû enregistrer la licence à la main. Ici le serveur le fait seul, aux conditions de la conception W23-B § 3.2.
 */
class RegistrarWalletPathTest extends RegistrarTestBase {

    private Map<String, Object> licence(String id) { return jdbc.queryForMap("SELECT * FROM lic_license WHERE license_id = ?", id); }

    private static Instant at(Object ts) { return ts == null ? null : castbridge.server.common.Times.instant(ts); }

    @Test
    void anUnlimitedProductionKeyOnTheFirstSyncCreatesTheLicenceAndTheSeatAndPays5000And50() throws Exception {
        Registered dev = registerApp("tv");
        Acts.Tv tv = tv();
        String lic = licenseId();
        String token = production(ISSUER, tv, lic, null, NOW - HOUR, null);
        JsonNode s = ok(sync(dev, tv, token));
        assertEquals("UNLIMITED", s.path("edition").path("ed").asText(), s.toString());
        assertEquals(1, count("SELECT COUNT(*) FROM lic_license WHERE license_id = ?", lic), "la licence est créée");
        Map<String, Object> l = licence(lic);
        assertEquals("PAID", l.get("kind"));
        assertEquals("ACTIVE", l.get("state"));
        assertEquals(1, ((Number) l.get("seats_allowed")).intValue());
        assertEquals(0, ((Number) l.get("transfer_cap")).intValue(), "licence non transférable");
        assertNull(l.get("end_at"), "une clé illimitée donne une licence sans fin");
        assertEquals(Instant.ofEpochMilli(NOW - HOUR), at(l.get("start_at")));
        assertTrue(String.valueOf(l.get("created_by")).startsWith("report:"), String.valueOf(l.get("created_by")));
        assertEquals(1, count("SELECT COUNT(*) FROM lic_seat s JOIN lic_license l ON l.id = s.license_pk WHERE l.license_id = ? AND s.state = 'ACTIVE' AND s.device_code = ?", lic, tv.code()));
        assertEquals(5000, balance(tv.code(), "NDEM"));
        assertEquals(50, balance(tv.code(), "MBOKO"));
        assertFalse(noticeReasons(s).contains("LICENSE_PENDING"), s.toString());
    }

    @Test
    void replayingTheSameSyncThreeTimesChangesNothingAndNeverPaysTwice() throws Exception {
        Registered dev = registerApp("tv");
        Acts.Tv tv = tv();
        String lic = licenseId();
        String token = production(ISSUER, tv, lic, null, NOW - HOUR, null);
        for (int i = 0; i < 3; i++) ok(sync(dev, tv, token));
        assertEquals(1, count("SELECT COUNT(*) FROM lic_license WHERE license_id = ?", lic));
        assertEquals(1, count("SELECT COUNT(*) FROM lic_seat s JOIN lic_license l ON l.id = s.license_pk WHERE l.license_id = ?", lic));
        assertEquals(1, count("SELECT COUNT(*) FROM lic_issuance i JOIN lic_license l ON l.id = i.license_pk WHERE l.license_id = ?", lic));
        assertEquals(1, count("SELECT COUNT(*) FROM lic_registration WHERE license_id = ?", lic));
        assertEquals(5000, balance(tv.code(), "NDEM"));
        assertEquals(50, balance(tv.code(), "MBOKO"));
    }

    @Test
    void aNinetyDayKeyIsPaidAsNinetyDaysNeverAsUnlimited() throws Exception {
        Registered dev = registerApp("tv");
        Acts.Tv tv = tv();
        String lic = licenseId();
        long issued = NOW - HOUR;
        String token = production(ISSUER, tv, lic, null, issued, 90);
        JsonNode s = ok(sync(dev, tv, token));
        assertEquals("PROD", s.path("edition").path("ed").asText(), "une clé de 90 jours n'est pas illimitée : " + s);
        Map<String, Object> l = licence(lic);
        assertEquals(Instant.ofEpochMilli(issued), at(l.get("start_at")));
        assertEquals(Instant.ofEpochMilli(issued + 90 * DAY), at(l.get("end_at")), "end_at = fin du droit usage de la clé");
        assertEquals(1000, balance(tv.code(), "NDEM"), "première tranche seulement : jamais l'ouverture illimitée");
        assertEquals(10, balance(tv.code(), "MBOKO"));
        // au bout de 61 jours (périodes 0, 30 et 60) : trois tranches de 1 000 + 10, rien au-delà de la fin de la clé même 200 jours plus tard
        clock.freezeAt(T0.plusSeconds(61 * 86_400L));
        ok(sync(dev, tv, token));
        assertEquals(3000, balance(tv.code(), "NDEM"));
        assertEquals(30, balance(tv.code(), "MBOKO"));
        clock.freezeAt(T0.plusSeconds(200 * 86_400L));
        ok(sync(dev, tv, token));
        assertEquals(3000, balance(tv.code(), "NDEM"), "la clé est finie : plus aucune tranche");
        assertEquals(30, balance(tv.code(), "MBOKO"));
    }

    @Test
    void aTokenPresentedAfterItsInstallationWindowAndNotDeclaredWaitsForTheOwner() throws Exception {
        Registered dev = registerApp("tv");
        Acts.Tv tv = tv();
        String lic = licenseId();
        String token = production(ISSUER, tv, lic, null, NOW - 5 * DAY, null);
        JsonNode s = ok(sync(dev, tv, token));
        assertEquals("NONE", s.path("edition").path("ed").asText(), s.toString());
        assertTrue(noticeReasons(s).contains("REGISTRATION_REVIEW"), "jamais de refus silencieux : " + s);
        assertEquals(0, count("SELECT COUNT(*) FROM lic_license WHERE license_id = ?", lic), "aucune licence tant que le propriétaire n'a pas décidé");
        assertEquals(1, count("SELECT COUNT(*) FROM lic_registration WHERE license_id = ? AND status = 'PENDING_DECISION' AND reason = 'INSTALL_TIME_UNKNOWN'", lic));
        assertEquals(0, balance(tv.code(), "NDEM"));
    }

    @Test
    void theSecondTvOfAOneSeatLicenceGetsNoSeatNoTokensAndAClearReason() throws Exception {
        String lic = licenseId();
        Registered devA = registerApp("tv"), devB = registerApp("tv");
        Acts.Tv a = tv(), b = tv();
        ok(sync(devA, a, production(ISSUER, a, lic, null, NOW - HOUR, null)));
        JsonNode s = ok(sync(devB, b, production(ISSUER, b, lic, null, NOW - HOUR, null)));
        assertEquals("NONE", s.path("edition").path("ed").asText(), s.toString());
        assertTrue(noticeReasons(s).contains("SEAT_OVER_QUOTA"), s.toString());
        assertEquals(1, count("SELECT COUNT(*) FROM lic_seat s JOIN lic_license l ON l.id = s.license_pk WHERE l.license_id = ?", lic), "un seul poste");
        assertEquals(0, balance(b.code(), "NDEM"));
        assertEquals(5000, balance(a.code(), "NDEM"), "la première TV garde sa licence");
        assertEquals(1, count("SELECT COUNT(*) FROM lic_registration WHERE license_id = ? AND status = 'PENDING_DECISION' AND reason = 'OVER_QUOTA'", lic));
    }

    @Test
    void theSameSeatClaimedByAnotherHardwareIsNotTransferredAndPaysNothing() throws Exception {
        String lic = licenseId();
        Registered devA = registerApp("tv"), devB = registerApp("tv");
        Acts.Tv a = tv(), b = tv();
        String seatA = WireActivationSeat.of(lic, a);
        ok(sync(devA, a, production(ISSUER, a, lic, seatA, NOW - HOUR, null)));
        // un outil mal réglé (ou un faussaire disposant de la clé) signe le MÊME poste pour un autre matériel : une licence ne se transfère pas
        JsonNode s = ok(sync(devB, b, production(ISSUER, b, lic, seatA, NOW - HOUR, null)));
        assertEquals("NONE", s.path("edition").path("ed").asText(), s.toString());
        assertEquals(0, balance(b.code(), "NDEM"));
        assertEquals(1, count("SELECT COUNT(*) FROM lic_seat s JOIN lic_license l ON l.id = s.license_pk WHERE l.license_id = ? AND s.device_code = ?", lic, a.code()));
        assertEquals(0, count("SELECT COUNT(*) FROM lic_seat s JOIN lic_license l ON l.id = s.license_pk WHERE l.license_id = ? AND s.device_code = ?", lic, b.code()));
        assertEquals(1, count("SELECT COUNT(*) FROM lic_registration WHERE license_id = ? AND status = 'PENDING_DECISION' AND device_code = ?", lic, b.code()));
        assertTrue(count("SELECT COUNT(*) FROM lic_audit WHERE action = 'REGISTRATION_ALERT' AND target_id = ?", lic) >= 1, "alerte douce journalisée");
    }

    /** Le poste par défaut d'une licence sur un matériel (le même calcul que les outils). */
    static final class WireActivationSeat {
        static String of(String lic, Acts.Tv tv) { return WireActivation.defaultSeat(lic, tv.factors()); }
    }

    @Test
    void aRenewalLicenceOfTheSameTvNeverPaysTheUnlimitedOpeningTwice() throws Exception {
        Registered dev = registerApp("tv");
        Acts.Tv tv = tv();
        String first = licenseId(), second = licenseId();
        String t1 = production(ISSUER, tv, first, null, NOW - 2 * HOUR, null);
        ok(sync(dev, tv, t1));
        assertEquals(5000, balance(tv.code(), "NDEM"));
        // le propriétaire renouvelle : un NOUVEL identifiant de licence pour la même TV (permis, aucune alerte), même plafond illimité
        String t2 = production(ISSUER, tv, second, null, NOW - HOUR, null);
        JsonNode s = ok(sync(dev, tv, t2, t1));
        assertEquals(1, count("SELECT COUNT(*) FROM lic_license WHERE license_id = ?", second), "la seconde licence est enregistrée");
        assertEquals(5000, balance(tv.code(), "NDEM"), "l'ouverture illimitée n'est versée qu'une fois par identité : " + s);
        assertEquals(50, balance(tv.code(), "MBOKO"));
        ok(sync(dev, tv, t2, t1));
        assertEquals(5000, balance(tv.code(), "NDEM"));
    }

    @Test
    void aSyncWithoutProofOfPossessionRegistersNothing() throws Exception {
        Registered dev = registerApp("tv");
        Acts.Tv tv = tv();
        String lic = licenseId();
        assertEquals(409, sync(dev, null, tv, production(ISSUER, tv, lic, null, NOW - HOUR, null)).getResponse().getStatus());
        assertEquals(0, count("SELECT COUNT(*) FROM lic_license WHERE license_id = ?", lic));
        assertEquals(0, count("SELECT COUNT(*) FROM lic_registration WHERE license_id = ?", lic));
    }

    @Test
    void noFullTokenIsStoredAnywhere() throws Exception {
        Registered dev = registerApp("tv");
        Acts.Tv tv = tv();
        String lic = licenseId();
        String token = production(ISSUER, tv, lic, null, NOW - HOUR, 90);
        ok(sync(dev, tv, token));
        String payload = token.split("\\.")[1];
        for (String table : List.of("lic_registration", "lic_issuance", "lic_audit", "lic_seat", "lic_license")) {
            for (Map<String, Object> row : jdbc.queryForList("SELECT * FROM " + table)) {
                for (Object v : row.values()) {
                    if (v == null) continue;
                    String t = String.valueOf(v);
                    assertFalse(t.contains(token) || t.contains(payload), table + " ne doit contenir aucun jeton complet");
                }
            }
        }
        assertEquals(64, jdbc.queryForObject("SELECT fp FROM lic_registration WHERE license_id = ?", String.class, lic).length(), "empreinte SHA-256 seulement");
        assertTrue(Timestamp.from(T0).getTime() > 0);
    }
}
