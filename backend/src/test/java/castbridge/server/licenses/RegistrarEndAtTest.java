package castbridge.server.licenses;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import castbridge.server.common.Times;
import castbridge.server.licenses.ReportedActivationRegistrar.Presented;
import castbridge.server.licenses.ReportedActivationRegistrar.Status;
import castbridge.server.licenses.ReportedActivationRegistrar.Via;
import castbridge.server.wallet.Acts;
import java.security.KeyPair;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * D-W23B-3 (audit R-1) : {@code end_at} d'une licence = fin du droit signé {@code usage}, quelle que soit la voie (émission du serveur : {@link EndAtTest} ; notification : ici ; registre
 * importé : ici). NULL seulement pour une clé vraiment illimitée. Chaque correction du registrar est auditée, avant et après.
 */
class RegistrarEndAtTest extends RegistrarTestBase {
    private static final String KEY = rawPublic(pair());

    private Map<String, Object> licence(String id) { return jdbc.queryForMap("SELECT start_at, end_at, created_by FROM lic_license WHERE license_id = ?", id); }

    private Status present(String token, Acts.Tv tv) { return registrar.register(new Presented(token, tv.code(), KEY, true), Via.WALLET, T0).status(); }

    @Test
    void aLicenceOpenedByANoticeEndsWithTheKey() {
        Acts.Tv tv = tv();
        String lic = licenseId();
        long issued = NOW - HOUR;
        assertEquals(Status.REGISTERED, present(production(ISSUER, tv, lic, null, issued, 90), tv));
        Map<String, Object> l = licence(lic);
        assertEquals(Instant.ofEpochMilli(issued), Times.instant(l.get("start_at")));
        assertEquals(Instant.ofEpochMilli(issued + 90 * DAY), Times.instant(l.get("end_at")));
    }

    @Test
    void anUnlimitedKeyGivesALicenceWithoutEnd() {
        Acts.Tv tv = tv();
        String lic = licenseId();
        assertEquals(Status.REGISTERED, present(production(ISSUER, tv, lic, null, NOW - HOUR, null), tv));
        assertNull(licence(lic).get("end_at"));
    }

    @Test
    void aRegistryLicenceHasNoDurationAndTheFirstVerifiedKeyFixesItsEnd() throws Exception {
        Acts.Tv tv = tv();
        String lic = licenseId();
        long issued = NOW - HOUR;
        String nonce = nonce16();
        String seat = WireActivation.defaultSeat(lic, tv.factors());
        importRegistry(List.of(licenseEvent(ISSUER, issued - 1000, lic, 1), issueEvent(ISSUER, issued, lic, seat, tv, nonce)));
        assertNull(licence(lic).get("end_at"), "le registre ne porte aucune durée");
        assertTrue(String.valueOf(licence(lic).get("created_by")).startsWith("import:"));
        assertEquals(Status.ATTACHED, present(production(ISSUER, tv, lic, null, issued, List.of("usage|duree|" + issued + "|" + (issued + 90 * DAY)), nonce), tv));
        Map<String, Object> l = licence(lic);
        assertEquals(Instant.ofEpochMilli(issued + 90 * DAY), Times.instant(l.get("end_at")), "end_at corrigé d'après le jeton");
        assertEquals(Instant.ofEpochMilli(issued), Times.instant(l.get("start_at")), "le jeton signé fait foi pour le début");
        List<Map<String, Object>> audits = jdbc.queryForList("SELECT actor, details FROM lic_audit WHERE action = 'LICENSE_END_FROM_TOKEN' AND target_id = ?", lic);
        assertEquals(1, audits.size());
        assertEquals("registrar", audits.get(0).get("actor"));
        assertTrue(String.valueOf(audits.get(0).get("details")).contains("from=null"), "avant");
        assertTrue(String.valueOf(audits.get(0).get("details")).contains("to=" + Instant.ofEpochMilli(issued + 90 * DAY)), "après");
    }

    @Test
    void anImportedLicenceStaysUnlimitedWhenAnUnlimitedKeyIsKnown() throws Exception {
        Acts.Tv tv = tv();
        String lic = licenseId();
        long issued = NOW - HOUR;
        String seat = WireActivation.defaultSeat(lic, tv.factors());
        importRegistry(List.of(licenseEvent(ISSUER, issued - 1000, lic, 1), issueEvent(ISSUER, issued, lic, seat, tv, nonce16())));
        assertEquals(Status.ATTACHED, present(production(ISSUER, tv, lic, null, issued, null), tv));
        assertEquals(Status.ATTACHED, present(production(ISSUER, tv, lic, null, issued + 1000, List.of("usage|duree|" + issued + "|" + (issued + 90 * DAY)), nonce16()), tv));
        assertNull(licence(lic).get("end_at"), "une clé illimitée existe pour cette licence : jamais de fin déduite d'une autre");
    }

    @Test
    void theOwnersExtensionAlwaysWins() throws Exception {
        Acts.Tv tv = tv();
        String lic = licenseId();
        long issued = NOW - HOUR;
        assertEquals(Status.REGISTERED, present(production(ISSUER, tv, lic, null, issued, 90), tv));
        Instant owner = Instant.ofEpochMilli(issued + 30 * DAY);
        licenses.extend(OWNER, lic, owner, "remboursement partiel : la licence est raccourcie");
        assertEquals(Status.ATTACHED, present(production(ISSUER, tv, lic, null, issued, List.of("usage|duree|" + issued + "|" + (issued + 365 * DAY)), nonce16()), tv));
        assertEquals(owner, Times.instant(licence(lic).get("end_at")), "la décision du propriétaire n'est jamais défaite par un jeton");
    }

    @Test
    void ninetyDaysPaidAsThreeSlicesNeverAsTheUnlimitedOpeningWhateverTheRoute() throws Exception {
        // voie du registre : licence importée sans durée, TV notifiée ensuite, puis trois périodes écoulées
        Registered dev = registerApp("tv");
        KeyPair install = pair();
        Acts.Tv tv = tv();
        String lic = licenseId();
        long issued = NOW - HOUR;
        String nonce = nonce16();
        String seat = WireActivation.defaultSeat(lic, tv.factors());
        importRegistry(List.of(licenseEvent(ISSUER, issued - 1000, lic, 1), issueEvent(ISSUER, issued, lic, seat, tv, nonce)));
        String token = production(ISSUER, tv, lic, null, issued, List.of("usage|duree|" + issued + "|" + (issued + 90 * DAY)), nonce);
        ok(sync(dev, install, tv, token));
        assertEquals(1000, balance(tv.code(), "NDEM"), "première tranche : jamais 5 000");
        clock.freezeAt(T0.plusSeconds(61 * 86_400L));
        ok(sync(dev, install, tv, token));
        assertEquals(3000, balance(tv.code(), "NDEM"));
        assertEquals(30, balance(tv.code(), "MBOKO"));
        clock.freezeAt(T0.plusSeconds(300 * 86_400L));
        ok(sync(dev, install, tv, token));
        assertEquals(3000, balance(tv.code(), "NDEM"), "rien après la fin de la clé");
    }

    @Test
    void theReportedSeatCountIsRaisedByTheRegistryNeverLowered() throws Exception {
        Acts.Tv tv = tv();
        String lic = licenseId();
        long issued = NOW - HOUR;
        assertEquals(Status.REGISTERED, present(production(ISSUER, tv, lic, null, issued, 90), tv));
        assertEquals(1, licenses.get(lic).seatsAllowed());
        importRegistry(List.of(licenseEvent(ISSUER, issued, lic, 3)));
        assertEquals(3, licenses.get(lic).seatsAllowed(), "le registre de l'outil relève le nombre de postes d'une licence ouverte par notification");
        assertEquals(1, count("SELECT COUNT(*) FROM lic_audit WHERE action = 'LICENSE_SEATS_FROM_REGISTRY' AND target_id = ?", lic));
        importRegistry(List.of(licenseEvent(ISSUER, issued + 1, lic, 2)));
        assertEquals(3, licenses.get(lic).seatsAllowed(), "jamais abaissé");
    }
}
