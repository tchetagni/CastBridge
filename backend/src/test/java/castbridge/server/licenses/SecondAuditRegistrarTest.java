package castbridge.server.licenses;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import castbridge.server.licenses.ReportedActivationRegistrar.Presented;
import castbridge.server.licenses.ReportedActivationRegistrar.Registration;
import castbridge.server.licenses.ReportedActivationRegistrar.Status;
import castbridge.server.licenses.ReportedActivationRegistrar.Via;
import castbridge.server.wallet.Acts;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Second audit Opus w23-05 : MEDIUM-A (R4), LOW-A (R3), LOW-B (R6, règle documentée), LOW-D, et les mutations survivantes N5, N9, N10, N11 devenues des tests qui les tuent.
 * Chaque test décrit le comportement ATTENDU ; écrit avant le correctif, il échouait par assertion.
 */
class SecondAuditRegistrarTest extends RegistrarTestBase {
    private Registration reg(String token, Acts.Tv tv, Instant now) { return registrar.register(new Presented(token, tv.code(), rawPublic(installOf(tv)), true), Via.WALLET, now); }

    private static Instant after(long days) { return T0.plusSeconds(days * 86_400L); }

    // ================================================================== MEDIUM-A (R4) : la prolongation du propriétaire est payée

    @Test
    void r4_anOwnerExtensionOfAReportLicenceIsPaidAndTheEditionStaysProduction() throws Exception {
        Acts.Tv tv = tv();
        String lic = licenseId();
        Registered dev = registerApp("tv");
        String token = production(ISSUER, tv, lic, null, NOW - HOUR, 30);
        ok(sync(dev, tv, token));
        assertEquals(1000, balance(tv.code(), "NDEM"), "tranche d'ouverture de la clé de 30 jours");
        licenses.extend(OWNER, lic, after(90), "prolongation payée par le client");
        clock.freezeAt(after(65));
        JsonNode s = ok(sync(dev, tv, token));
        assertEquals("ACTIVE", s.path("edition").path("license").asText(), s.toString());
        assertEquals("PROD", s.path("edition").path("ed").asText(), "l'édition ne tombe pas à NONE tant que la licence est ACTIVE : " + s);
        assertEquals(3000, balance(tv.code(), "NDEM"), "les périodes de J+30 et J+60 de la prolongation sont payées : " + s);
        assertEquals(30, balance(tv.code(), "MBOKO"));
        ok(sync(dev, tv, token));
        assertEquals(3000, balance(tv.code(), "NDEM"), "une seule fois");
    }

    // ================================================================== LOW-A (R3) : une TV vue dans la fenêtre pendant une suspension a son poste à la reprise, même tard

    @Test
    void r3_aTvFirstSeenInsideItsWindowDuringASuspensionGetsItsSeatAtTheResumeEvenFiveDaysLater() {
        Acts.Tv a = tv(), b = tv();
        String lic = licenseId();
        assertEquals(Status.REGISTERED, reg(production(ISSUER, a, lic, null, NOW - HOUR, 90), a, T0).status());
        licenses.setSeats(OWNER, lic, 2, "deuxième poste");
        licenses.suspend(OWNER, lic, "impayé");
        String tokenB = production(ISSUER, b, lic, null, NOW - HOUR, 90);
        Registration during = reg(tokenB, b, T0);
        assertEquals("LICENSE_SUSPENDED", during.reason(), "vue pendant la suspension, dans sa fenêtre de 48 h");
        licenses.resume(OWNER, lic, "payé");
        Registration after = reg(tokenB, b, after(5));
        assertEquals(Status.REGISTERED, after.status(), "la première vue dans la fenêtre fait foi : " + after.reason());
        assertEquals(2, count("SELECT COUNT(*) FROM lic_seat s JOIN lic_license l ON l.id = s.license_pk WHERE l.license_id = ? AND s.state = 'ACTIVE'", lic));
    }

    @Test
    void r3b_aTvFirstSeenAfterItsWindowIsStillLeftToTheOwner() {
        Acts.Tv a = tv(), b = tv();
        String lic = licenseId();
        assertEquals(Status.REGISTERED, reg(production(ISSUER, a, lic, null, NOW - HOUR, 90), a, T0).status());
        licenses.setSeats(OWNER, lic, 2, "deuxième poste");
        String tokenB = production(ISSUER, b, lic, null, NOW - HOUR, 90);
        Registration late = reg(tokenB, b, after(5));
        assertEquals(Status.PENDING_DECISION, late.status());
        assertEquals("INSTALL_TIME_UNKNOWN", late.reason(), "jamais vue dans sa fenêtre : le propriétaire décide");
    }

    // ================================================================== LOW-B (R6) : règle des cases, documentée

    /**
     * Règle EXACTE (docs/DEPLOIEMENT-SERVEUR-1.2.1.md, « Périodes payées ») : une période d'une TV est payée UNE fois par monnaie ; deux périodes dont les débuts sont à moins d'une demi-période (15 jours)
     * l'une de l'autre sont la MÊME période, quelle que soit la licence. Un renouvellement anticipé de 40 jours (nouvelle licence commençant avant la fin de la précédente) paie donc les périodes
     * de la durée COUVERTE (union des deux droits, 140 jours : 5 périodes), comme le ferait le même identifiant de licence prolongé à J+140. 2 × 90 jours achetés avec 40 jours de recouvrement
     * ne donnent pas 6 tranches : le recouvrement n'est pas payé deux fois.
     */
    @Test
    void r6_anEarlyRenewalPaysTheCoveredPeriodsOnceNotTheOverlapTwice() throws Exception {
        Acts.Tv tv = tv();
        Registered dev = registerApp("tv");
        String l1 = licenseId(), l2 = licenseId();
        licenses.create(OWNER, new LicenseService.NewLicense(l1, clients.create(OWNER, "Client r6a", null, null).id(), "PAID", 1, T0.minusSeconds(0), after(90), null, null, null));
        licenses.create(OWNER, new LicenseService.NewLicense(l2, clients.create(OWNER, "Client r6b", null, null).id(), "PAID", 1, after(50), after(140), null, null, null));
        String t1 = production(ISSUER, tv, l1, null, NOW - HOUR, 90), t2 = production(ISSUER, tv, l2, null, NOW - HOUR, 90);
        ok(sync(dev, tv, t1, t2));   // les deux postes sont enregistrés dans leur fenêtre d'installation
        clock.freezeAt(after(145));
        ok(sync(dev, tv, t1, t2));
        assertEquals(5000, balance(tv.code(), "NDEM"), "périodes de J+0, 30, 60 (L1) et 90, 120 (L2 : ses périodes de J+50 et J+80 recouvrent celles de L1)");
        ok(sync(dev, tv, t2, t1));
        assertEquals(5000, balance(tv.code(), "NDEM"));
    }

    // ================================================================== LOW-D (N9) : l'avis « sans clé d'installation » ne reste pas sur une TV déjà payée et liée

    @Test
    void n9_theNoInstallKeyNoticeIsHiddenOnATvAlreadyPaidAndBoundButShownOnAnUnpaidOne() throws Exception {
        Acts.Tv tv = tv();
        Registered dev = registerApp("tv");
        String paid = licenseId(), other = licenseId();
        ok(sync(dev, tv, production(ISSUER, tv, paid, null, NOW - HOUR, null)));
        assertEquals(5000, balance(tv.code(), "NDEM"));
        JsonNode s = ok(sync(dev, tv, production(ISSUER, tv, paid, null, NOW - HOUR, null), productionLegacy(ISSUER, tv, other, null, NOW - HOUR, null)));
        assertEquals("PENDING_DECISION", s.path("registration").get(1).path("status").asText(), s.toString());
        assertFalse(noticeReasons(s).contains("REGISTRATION_REVIEW"), "TV déjà payée et liée : plus d'avis permanent : " + s.path("notices"));
        // une TV sans licence payée, avec un jeton sans clé d'installation : l'avis reste affiché
        Acts.Tv fresh = tv();
        Registered dev2 = registerApp("tv");
        JsonNode u = ok(sync(dev2, fresh, productionLegacy(ISSUER, fresh, licenseId(), null, NOW - HOUR, null)));
        boolean shown = false;
        for (JsonNode n : u.path("notices")) if (n.path("detail").asText().equals("NO_INSTALL_KEY")) shown = true;
        assertTrue(shown, "TV sans licence : l'avis reste : " + u.path("notices"));
    }

    // ================================================================== N10 : un jeton sans ik ne change jamais la durée d'une licence

    @Test
    void n10_aLegacyTokenOf90DaysNeverChangesTheEndOfAReportLicenceOf30Days() {
        Acts.Tv tv = tv();
        String lic = licenseId();
        assertEquals(Status.REGISTERED, reg(production(ISSUER, tv, lic, null, NOW - HOUR, 30), tv, T0).status());
        java.sql.Timestamp end = jdbc.queryForObject("SELECT end_at FROM lic_license WHERE license_id = ?", java.sql.Timestamp.class, lic);
        Registration r = reg(productionLegacy(ISSUER, tv, lic, null, NOW - HOUR, 90), tv, T0);
        assertEquals(Status.PENDING_DECISION, r.status());
        assertEquals(ReportedActivationRegistrar.NO_INSTALL_KEY, r.reason());
        assertEquals(end, jdbc.queryForObject("SELECT end_at FROM lic_license WHERE license_id = ?", java.sql.Timestamp.class, lic), "la fin de la licence ne bouge pas");
    }

    // ================================================================== N5 : le renouvellement dans la grâce ne perd pas la période suivante

    @Test
    void n5_aRenewalThreeDaysAfterTheEndStaysInTheSameWindowAndThePeriodInTheGapIsPaid() throws Exception {
        Acts.Tv tv = tv();
        String lic = licenseId();
        Registered dev = registerApp("tv");
        String first = production(ISSUER, tv, lic, null, NOW - HOUR, 30);
        ok(sync(dev, tv, first));
        clock.freezeAt(after(33));
        long from2 = clock.now().toEpochMilli() - HOUR;
        String renewal = production(ISSUER, tv, lic, null, from2, List.of("usage|duree|" + from2 + "|" + (from2 + 30 * DAY)), nonce16());
        ok(sync(dev, tv, renewal, first));
        clock.freezeAt(after(65));
        ok(sync(dev, tv, renewal, first));
        assertEquals(3000, balance(tv.code(), "NDEM"), "la période de J+30 tombe dans l'écart de 3 jours (≤ grâce de 14 jours) : elle est payée, comme celle de J+60");
    }

    // ================================================================== N11 : la conversion des versements d'avant les cases

    @Test
    void n11_paymentsMadeBeforeTheClaimTableBecomeClaimsAndASecondLicenceDoesNotRepayThem() throws Exception {
        Acts.Tv tv = tv();
        Registered dev = registerApp("tv");
        String l1 = licenseId(), l2 = licenseId();
        licenses.create(OWNER, new LicenseService.NewLicense(l1, clients.create(OWNER, "Client n11", null, null).id(), "PAID", 1, T0.minusSeconds(65 * 86_400L), null, null, null, null));
        String t1 = production(ISSUER, tv, l1, null, NOW - HOUR, null);
        ok(sync(dev, tv, t1));
        assertEquals(7000, balance(tv.code(), "NDEM"));
        jdbc.update("DELETE FROM wallet_period_claim WHERE holder = ?", tv.code());   // l'état d'avant V65 : versements faits, aucune case
        String t2 = production(ISSUER, tv, l2, null, NOW - 65 * DAY - 2 * HOUR, null);
        ok(sync(dev, tv, t1, t2));
        Registration r = registrar.decide(OWNER, Hashing.sha256Hex(t2), true, "TV vue chez le client", T0);
        assertEquals(Status.REGISTERED, r.status(), r.reason());
        for (int i = 0; i < 3; i++) ok(sync(dev, tv, t1, t2));
        assertEquals(7000, balance(tv.code(), "NDEM"), "les versements d'avant la table sont convertis en cases : rien n'est versé deux fois");
        assertEquals(70, balance(tv.code(), "MBOKO"));
        assertEquals(5, count("SELECT COUNT(*) FROM wallet_period_claim WHERE holder = ?", tv.code()), "ouverture + deux périodes en NDEM + deux en MBOKO");
    }
}
