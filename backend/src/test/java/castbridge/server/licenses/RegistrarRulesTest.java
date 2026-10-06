package castbridge.server.licenses;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import castbridge.server.licenses.ReportedActivationRegistrar.Presented;
import castbridge.server.licenses.ReportedActivationRegistrar.Registration;
import castbridge.server.licenses.ReportedActivationRegistrar.Status;
import castbridge.server.licenses.ReportedActivationRegistrar.Via;
import castbridge.server.wallet.Acts;
import castbridge.server.web.ApiException;
import java.security.KeyPair;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Une ligne de test par règle de la conception W23-B § 3.2 (conditions communes) et § 3.4 (rattachement) : le statut ET le motif, et ce que la base contient ensuite. Le registrar est
 * appelé directement (les tests {@link RegistrarWalletPathTest} couvrent la route HTTP) ; la preuve de possession y est une clé d'installation de test.
 */
class RegistrarRulesTest extends RegistrarTestBase {

    private static final String KEY = rawPublic(pair());

    private Registration reg(String token, Acts.Tv tv) { return reg(token, tv, rawPublic(installOf(tv)), T0); }

    private Registration reg(String token, Acts.Tv tv, String installPub, Instant now) { return registrar.register(new Presented(token, tv.code(), installPub, installPub != null), Via.WALLET, now); }

    private void assertNothingWritten(String lic) {
        assertEquals(0, count("SELECT COUNT(*) FROM lic_license WHERE license_id = ?", lic), "aucune licence");
        assertEquals(0, count("SELECT COUNT(*) FROM lic_registration WHERE license_id = ?", lic), "aucune ligne d'enregistrement");
    }

    private long seats(String lic) { return count("SELECT COUNT(*) FROM lic_seat s JOIN lic_license l ON l.id = s.license_pk WHERE l.license_id = ? AND s.state = 'ACTIVE'", lic); }

    // ------------------------------------------------------------------ § 3.2

    @Test
    void anUnknownKeyIsRefusedAndLeavesNoTrace() {
        Acts.Tv tv = tv();
        String lic = licenseId();
        Registration r = reg(production(STRANGER, tv, lic, null, NOW - HOUR, null), tv);
        assertEquals(Status.REFUSED, r.status());
        assertEquals("UNKNOWN_KEY", r.reason());
        assertNothingWritten(lic);
    }

    @Test
    void aKnownKeyWithAForgedSignatureIsRefused() {
        Acts.Tv tv = tv();
        String lic = licenseId();
        // le kid de l'émetteur de confiance, la signature d'un étranger
        String kid = LicenseKeyring.kidOf(rawPublicBytes(ISSUER));
        WireActivation.Fields f = new WireActivation.Fields("production", "tv", kid, 1, nonce16(), NOW - HOUR, NOW - HOUR, NOW + 47 * HOUR, lic, WireActivation.defaultSeat(lic, tv.factors()),
                DeviceIdentity.kFor(tv.factors().size()), tv.factors(), List.of());
        String forged = WireActivation.token(f, Acts.sign(STRANGER, WireActivation.payload(f)));
        Registration r = reg(forged, tv);
        assertEquals(Status.REFUSED, r.status());
        assertEquals("BAD_SIGNATURE", r.reason());
        assertNothingWritten(lic);
    }

    @Test
    void aRevokedKeyIsRefused() {
        Acts.Tv tv = tv();
        String lic = licenseId();
        jdbc.update("INSERT INTO lic_revocation (kid, reason, revoked_by, revoked_at) VALUES (?, 'test', 'test', ?)", LicenseKeyring.kidOf(rawPublicBytes(BURNED)), java.sql.Timestamp.from(T0));
        Registration r = reg(production(BURNED, tv, lic, null, NOW - HOUR, null), tv);
        assertEquals(Status.REFUSED, r.status());
        assertEquals("REVOKED_KEY", r.reason());
        assertNothingWritten(lic);
    }

    @Test
    void aTokenForAnotherHardwareIsACloneAndWritesNothing() {
        Acts.Tv a = tv(), b = tv();
        String lic = licenseId();
        Registration r = reg(production(ISSUER, a, lic, null, NOW - HOUR, null), b);
        assertEquals(Status.REFUSED, r.status());
        assertEquals("CLONE", r.reason());
        assertNothingWritten(lic);
    }

    @Test
    void withoutTheProofOfPossessionNothingIsRegistered() {
        Acts.Tv tv = tv();
        String lic = licenseId();
        Registration r = reg(production(ISSUER, tv, lic, null, NOW - HOUR, null), tv, null, T0);
        assertEquals(Status.REFUSED, r.status());
        assertEquals("BIND_PROOF", r.reason());
        assertNothingWritten(lic);
    }

    @Test
    void aTrialKeyOpensNoLicence() {
        Acts.Tv tv = tv();
        Registration r = reg(Acts.trialDays(ISSUER, tv, NOW - HOUR, 30), tv);
        assertEquals(Status.IGNORED, r.status());
        assertEquals("TRIAL", r.reason());
        assertEquals(0, count("SELECT COUNT(*) FROM lic_registration WHERE device_code = ?", tv.code()));
    }

    @Test
    void theSuperRightOpensNoLicence() {
        Acts.Tv tv = tv();
        String lic = licenseId();
        Registration r = reg(production(ISSUER, tv, lic, null, NOW - HOUR, List.of("super|tout|" + (NOW - HOUR)), nonce16()), tv);
        assertEquals(Status.IGNORED, r.status());
        assertEquals("SUPER", r.reason());
        assertNothingWritten(lic);
    }

    @Test
    void aKeyOfTheWrongScopeCannotCreateAndAReactivationKeyOnlyAttaches() {
        Acts.Tv tv = tv();
        String lic = licenseId();
        assertEquals("KEY_NOT_ALLOWED", reg(production(TRIAL_ONLY, tv, lic, null, NOW - HOUR, null), tv).reason(), "clé d'essai seulement");
        assertEquals("KEY_NOT_ALLOWED", reg(production(REACT, tv, lic, null, NOW - HOUR, null), tv).reason(), "REACTIVATE ne crée pas de licence");
        assertEquals(0, count("SELECT COUNT(*) FROM lic_license WHERE license_id = ?", lic), "aucune licence");
        // la licence et le poste existent (clé de production) : la clé de réactivation rattache le même matériel
        assertEquals(Status.REGISTERED, reg(production(ISSUER, tv, lic, null, NOW - HOUR, null), tv).status());
        Registration again = reg(production(REACT, tv, lic, null, NOW - HOUR + 1000, null), tv);
        assertEquals(Status.ATTACHED, again.status(), again.reason());
        assertEquals(1, seats(lic));
        // mais elle ne crée pas un second poste pour un autre matériel
        Acts.Tv other = tv();
        assertEquals("KEY_NOT_ALLOWED", reg(production(REACT, other, lic, null, NOW - HOUR, null), other).reason());
        assertEquals(1, seats(lic));
    }

    @Test
    void aSeatRevokedAfterTheIssueRefusesTheToken() {
        Acts.Tv tv = tv();
        String lic = licenseId();
        String seat = WireActivation.defaultSeat(lic, tv.factors());
        jdbc.update("INSERT INTO lic_revocation (license_id, seat_id, reason, revoked_by, revoked_at) VALUES (?, ?, 'test', 'test', ?)", lic, seat, java.sql.Timestamp.from(T0.minusSeconds(60)));
        Registration r = reg(production(ISSUER, tv, lic, null, NOW - HOUR, null), tv);
        assertEquals(Status.REFUSED, r.status());
        assertEquals("REVOKED_SEAT", r.reason());
        assertEquals(0, count("SELECT COUNT(*) FROM lic_license WHERE license_id = ?", lic));
    }

    @Test
    void aRevokedLicenceRefusesTheToken() {
        Acts.Tv tv = tv(), other = tv();
        String lic = licenseId();
        assertEquals(Status.REGISTERED, reg(production(ISSUER, tv, lic, null, NOW - HOUR, null), tv).status());
        licenses.revoke(OWNER, lic, "test");
        Registration r = reg(production(ISSUER, other, lic, null, NOW - HOUR, null), other);
        assertEquals(Status.REFUSED, r.status());
        assertEquals("LICENSE_REVOKED", r.reason());
    }

    @Test
    void theInstallationWindowIsJudgedAgainstTheServerClock() {
        Acts.Tv tv = tv();
        String lic = licenseId();
        long issued = NOW - 48 * HOUR;   // la fenêtre se ferme à NOW, plus 5 minutes de tolérance
        String token = production(ISSUER, tv, lic, null, issued, null);
        assertEquals(Status.REGISTERED, reg(token, tv, rawPublic(installOf(tv)), T0.plusSeconds(5 * 60)).status(), "à la fermeture + 5 minutes : accepté");
        Acts.Tv late = tv();
        String lic2 = licenseId();
        Registration r = reg(production(ISSUER, late, lic2, null, issued, null), late, rawPublic(installOf(late)), T0.plusSeconds(5 * 60 + 1));
        assertEquals(Status.PENDING_DECISION, r.status());
        assertEquals("INSTALL_TIME_UNKNOWN", r.reason());
        assertEquals(0, count("SELECT COUNT(*) FROM lic_license WHERE license_id = ?", lic2));
        assertTrue(r.installTimeUnproven());
    }

    @Test
    void aDeclaredEmissionLiftsTheWindowRule() throws Exception {
        Acts.Tv tv = tv();
        String lic = licenseId();
        long issued = NOW - 10 * DAY;
        String nonce = nonce16();
        String token = production(TOOL, tv, lic, null, issued, List.of(), nonce);
        assertEquals("INSTALL_TIME_UNKNOWN", reg(token, tv).reason());
        // l'outil du propriétaire a écrit l'émission dans son registre (déclaration) : même licence, même poste, même nonce
        String seat = WireActivation.defaultSeat(lic, tv.factors());
        var rep = importRegistry(List.of(licenseEvent(issued - 1000, lic, 1), issueEvent(issued, lic, seat, tv, nonce)));
        assertEquals(2, rep.applied(), rep.toString());
        Registration r = reg(token, tv);
        assertEquals(Status.ATTACHED, r.status(), r.reason());
        assertTrue(r.registered());
    }

    @Test
    void aVeryLateNoticeWaitsForTheOwner() {
        Acts.Tv tv = tv();
        String lic = licenseId();
        Registration r = reg(production(ISSUER, tv, lic, null, NOW - 401 * DAY, null), tv);
        assertEquals(Status.PENDING_DECISION, r.status());
        assertEquals("LATE_NOTICE", r.reason());
        assertEquals(0, count("SELECT COUNT(*) FROM lic_license WHERE license_id = ?", lic));
    }

    @Test
    void aMalformedLicenceIdentifierIsRefused() {
        Acts.Tv tv = tv();
        Registration r = reg(production(ISSUER, tv, "list", null, NOW - HOUR, null), tv);   // mot réservé des routes
        assertEquals(Status.REFUSED, r.status());
        assertEquals("MALFORMED", r.reason());
        assertEquals("MALFORMED", reg(production(ISSUER, tv, WireActivation.TRIAL_LICENSE, null, NOW - HOUR, null), tv).reason(), "« trial » est réservé aux essais");
    }

    @Test
    void anImpossibleUsageRightIsRefused() {
        Acts.Tv tv = tv();
        assertEquals("BAD_RIGHTS", reg(production(ISSUER, tv, licenseId(), null, NOW - HOUR, List.of("usage|duree|" + NOW + "|" + (NOW - 1)), nonce16()), tv).reason(), "fin avant début");
        assertEquals("BAD_RIGHTS", reg(production(ISSUER, tv, licenseId(), null, NOW - HOUR, List.of("usage|duree|" + NOW + "|" + (NOW + 4000 * DAY)), nonce16()), tv).reason(), "plus de 3 660 jours");
        assertEquals("BAD_RIGHTS", reg(production(ISSUER, tv, licenseId(), null, NOW - HOUR, List.of("usage|duree|" + NOW + "|" + (NOW + DAY), "usage|duree|" + NOW + "|" + (NOW + 2 * DAY)), nonce16()), tv).reason(),
                "deux plafonds");
    }

    @Test
    void aTokenIssuedInTheFutureIsRefused() {
        Acts.Tv tv = tv();
        Registration r = reg(production(ISSUER, tv, licenseId(), null, NOW + 3 * DAY, null), tv);
        assertEquals(Status.REFUSED, r.status());
        assertEquals("CLOCK", r.reason());
    }

    @Test
    void anExpiredKeyOpensNoLicence() {
        Acts.Tv tv = tv();
        String lic = licenseId();
        // clé de 10 jours émise il y a 40 jours, présentée dans sa fenêtre grâce à un début de droit antérieur : fin + 14 jours de grâce dépassées
        long issued = NOW - HOUR;
        Registration r = reg(production(ISSUER, tv, lic, null, issued, List.of("usage|duree|" + (NOW - 40 * DAY) + "|" + (NOW - 30 * DAY)), nonce16()), tv);
        assertEquals(Status.REFUSED, r.status());
        assertEquals("EXPIRED", r.reason());
        assertEquals(0, count("SELECT COUNT(*) FROM lic_license WHERE license_id = ?", lic));
    }

    @Test
    void theKeyRateCapIs10ADayAnd50AMonth() {
        // une clé réservée à ce test : le plafond compte les licences ouvertes par la clé
        for (int i = 0; i < 10; i++) {
            Acts.Tv tv = tv();
            assertEquals(Status.REGISTERED, reg(production(RATE, tv, licenseId(), null, NOW - HOUR, null), tv).status(), "licence " + (i + 1));
        }
        Acts.Tv eleventh = tv();
        String lic = licenseId();
        Registration r = reg(production(RATE, eleventh, lic, null, NOW - HOUR, null), eleventh);
        assertEquals(Status.PENDING_DECISION, r.status());
        assertEquals("KEY_RATE", r.reason());
        assertEquals(0, count("SELECT COUNT(*) FROM lic_license WHERE license_id = ?", lic));
        assertTrue(count("SELECT COUNT(*) FROM lic_audit WHERE action = 'REGISTRATION_ALERT' AND reason = 'KEY_RATE'") >= 1, "alerte douce");
        // le lendemain, le plafond du jour est vide mais pas celui du mois : 40 de plus (50 en tout), puis le 51e attend
        Instant tomorrow = T0.plusSeconds(86_400L + 60);
        for (int day = 1; day <= 4; day++) {
            for (int i = 0; i < 10; i++) {
                Acts.Tv tv = tv();
                Instant at = T0.plusSeconds(day * (86_400L + 900L) + 60L * i);
                long issued = at.toEpochMilli() - HOUR;
                Registration x = reg(production(RATE, tv, licenseId(), null, issued, null), tv, rawPublic(installOf(tv)), at);
                assertEquals(Status.REGISTERED, x.status(), "jour " + day + " licence " + i + " : " + x.reason());
            }
        }
        Acts.Tv last = tv();
        Instant at = T0.plusSeconds(5 * (86_400L + 900L));
        Registration m = reg(production(RATE, last, licenseId(), null, at.toEpochMilli() - HOUR, null), last, rawPublic(installOf(last)), at);
        assertEquals("KEY_RATE", m.reason(), "plafond mensuel");
        assertNotEquals(tomorrow, at);
    }

    // ------------------------------------------------------------------ § 3.4

    @Test
    void aKnownSeatOfTheSameHardwareIsAttachedOnceWhateverTheNumberOfReplays() {
        Acts.Tv tv = tv();
        String lic = licenseId();
        String token = production(ISSUER, tv, lic, null, NOW - HOUR, null);
        Registration first = reg(token, tv);
        assertEquals(Status.REGISTERED, first.status());
        for (int i = 0; i < 4; i++) {
            Registration again = reg(token, tv);
            assertEquals(first.status(), again.status());
            assertEquals(first.licenseId(), again.licenseId());
            assertEquals(first.seatId(), again.seatId());
        }
        // un autre jeton (autre nonce) pour le même poste : rattaché, une seconde émission, toujours un poste
        Registration second = reg(production(ISSUER, tv, lic, null, NOW - HOUR + 1000, null), tv);
        assertEquals(Status.ATTACHED, second.status());
        assertEquals(1, seats(lic));
        assertEquals(2, count("SELECT COUNT(*) FROM lic_issuance i JOIN lic_license l ON l.id = i.license_pk WHERE l.license_id = ?", lic));
        assertEquals(2, count("SELECT COUNT(*) FROM lic_registration WHERE license_id = ?", lic));
    }

    @Test
    void theSameHardwareUnderAnotherSeatIdIsAnAliasAndCountsOnce() {
        Acts.Tv tv = tv();
        String lic = licenseId();
        assertEquals(Status.REGISTERED, reg(production(ISSUER, tv, lic, null, NOW - HOUR, null), tv).status());
        Registration r = reg(production(ISSUER, tv, lic, "00000000000000ff", NOW - HOUR + 1000, null), tv);
        assertEquals(Status.ATTACHED, r.status(), r.reason());
        assertEquals(1, seats(lic));
        assertEquals(1, count("SELECT COUNT(*) FROM lic_seat_alias a JOIN lic_license l ON l.id = a.license_pk WHERE l.license_id = ? AND a.alias_seat_id = '00000000000000ff'", lic));
    }

    @Test
    void aNewHardwareWithoutAFreeSeatWaitsAndNeverGetsASeat() {
        Acts.Tv a = tv(), b = tv();
        String lic = licenseId();
        assertEquals(Status.REGISTERED, reg(production(ISSUER, a, lic, null, NOW - HOUR, null), a).status());
        Registration r = reg(production(ISSUER, b, lic, null, NOW - HOUR, null), b);
        assertEquals(Status.PENDING_DECISION, r.status());
        assertEquals("OVER_QUOTA", r.reason());
        assertEquals(1, seats(lic));
        assertEquals(0, count("SELECT COUNT(*) FROM lic_seat WHERE device_code = ?", b.code()));
        // quota fermé : le propriétaire ne peut pas le relever, la TV reste en attente
        assertThrows(ApiException.class, () -> licenses.setSeats(OWNER, lic, 2, "deuxième TV du client"));
        Registration later = reg(production(ISSUER, b, lic, null, NOW - HOUR, null), b);
        assertEquals(Status.PENDING_DECISION, later.status());
        assertEquals(1, seats(lic));
    }

    @Test
    void aReleasedSeatComesBackOnlyByTheOwnersDecision() {
        Acts.Tv tv = tv();
        String lic = licenseId();
        String token1 = production(ISSUER, tv, lic, null, NOW - HOUR, null);
        Registration first = reg(token1, tv);
        licenses.releaseSeat(OWNER, lic, first.seatId(), "TV remplacée");
        // un nouveau jeton émis après la libération (la ligne de révocation est antérieure à son émission) : le même matériel attend la décision du propriétaire
        jdbc.update("DELETE FROM lic_revocation WHERE license_id = ?", lic);
        Registration r = reg(production(ISSUER, tv, lic, null, NOW + 60_000, null), tv, rawPublic(installOf(tv)), T0.plusSeconds(120));
        assertEquals(Status.PENDING_DECISION, r.status());
        assertEquals("SEAT_RELEASED", r.reason());
        assertEquals(0, seats(lic));
    }

    @Test
    void aSuspendedLicenceRecordsTheEmissionAndChangesNoSeat() {
        Acts.Tv a = tv(), b = tv();
        String lic = licenseId();
        assertEquals(Status.REGISTERED, reg(production(ISSUER, a, lic, null, NOW - HOUR, null), a).status());
        licenses.suspend(OWNER, lic, "impayé");
        Registration same = reg(production(ISSUER, a, lic, null, NOW - HOUR + 1000, null), a);
        assertEquals(Status.ATTACHED, same.status());
        assertEquals("LICENSE_SUSPENDED", same.reason());
        Registration newcomer = reg(production(ISSUER, b, lic, null, NOW - HOUR, null), b);
        // changed with the w23-05 audit (MEDIUM-4): a TV seen during a suspension waits (rejudged at each presentation) instead of being « ATTACHED » for good without a seat
        assertEquals(Status.PENDING_DECISION, newcomer.status());
        assertEquals("LICENSE_SUSPENDED", newcomer.reason());
        assertEquals(1, seats(lic), "aucun poste de plus");
    }

    @Test
    void aTrialLicenceWithAProductionTokenIsAnInconsistency() {
        Acts.Tv tv = tv();
        var trial = licenses.create(OWNER, new LicenseService.NewLicense("lic-essai-" + nonce16().substring(0, 6), clients.create(OWNER, "Client d'essai", null, null).id(), "TRIAL", null, null, null, null, null, null));
        Registration r = reg(production(ISSUER, tv, trial.licenseId(), null, NOW - HOUR, null), tv);
        assertEquals(Status.PENDING_DECISION, r.status());
        assertEquals("KIND_MISMATCH", r.reason());
    }

    @Test
    void renewalsGiveSeveralLicencesToTheSameTvWithoutAnyAlert() {
        Acts.Tv tv = tv();
        String l1 = licenseId(), l2 = licenseId();
        assertEquals(Status.REGISTERED, reg(production(ISSUER, tv, l1, null, NOW - HOUR, 90), tv).status());
        assertEquals(Status.REGISTERED, reg(production(ISSUER, tv, l2, null, NOW - 30 * 60_000L, null), tv).status());
        assertEquals(0, count("SELECT COUNT(*) FROM lic_audit WHERE action = 'REGISTRATION_ALERT' AND target_id IN (?, ?) AND reason <> 'INSTALL_TIME_UNPROVEN'", l1, l2));
    }

    @Test
    void theSameSeatClaimedByAnotherHardwareIsNeverTransferred() {
        Acts.Tv a = tv(), b = tv();
        String lic = licenseId();
        String seat = WireActivation.defaultSeat(lic, a.factors());
        assertEquals(Status.REGISTERED, reg(production(ISSUER, a, lic, seat, NOW - HOUR, null), a).status());
        Registration r = reg(production(ISSUER, b, lic, seat, NOW - HOUR, null), b);
        assertEquals(Status.PENDING_DECISION, r.status());
        assertEquals("TRANSFER_CAP", r.reason());
        assertEquals(1, seats(lic));
        assertEquals(0, count("SELECT COUNT(*) FROM lic_seat WHERE device_code = ?", b.code()));
    }

    @Test
    void theSameLegacyTokenWithAnotherInstallationKeyChangesNothingAndRaisesASoftAlert() {
        Acts.Tv tv = tv();
        String lic = licenseId();
        // une activation sans clé d'installation signée : la première clé vue fait foi (« premier gagne »)
        String token = productionLegacy(ISSUER, tv, lic, null, NOW - HOUR, null);
        Registration first = reg(token, tv, rawPublic(installOf(tv)), T0);
        assertEquals(Status.PENDING_DECISION, first.status());
        assertEquals("NO_INSTALL_KEY", first.reason());
        Registration r = reg(token, tv, rawPublic(pair()), T0);
        assertEquals(Status.PENDING_DECISION, r.status());
        assertEquals("BIND_MISMATCH", r.reason());
        assertEquals("PENDING_DECISION", jdbc.queryForObject("SELECT status FROM lic_registration WHERE license_id = ?", String.class, lic), "la ligne d'origine ne bouge pas");
        assertEquals(rawPublic(installOf(tv)), jdbc.queryForObject("SELECT install_pub FROM lic_registration WHERE license_id = ?", String.class, lic));
        reg(token, tv, rawPublic(pair()), T0);
        assertEquals(1, count("SELECT COUNT(*) FROM lic_audit WHERE action = 'REGISTRATION_ALERT' AND reason = 'BIND_MISMATCH' AND target_id = ?", lic), "une seule alerte par jeton");
    }

    // ------------------------------------------------------------------ audit, décision, secrets

    @Test
    void everyAutoRegistrationIsAuditedByTheRegistrarWithTheFingerprint() {
        Acts.Tv tv = tv();
        String lic = licenseId();
        Registration r = reg(production(ISSUER, tv, lic, null, NOW - HOUR, 90), tv);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT actor, role, channel, details FROM lic_audit WHERE target_id = ? AND action = 'LICENSE_CREATE_FROM_REPORT'", lic);
        assertEquals(1, rows.size());
        assertEquals("registrar", rows.get(0).get("actor"));
        assertTrue(String.valueOf(rows.get(0).get("details")).contains("fp=" + r.fp()), "empreinte dans l'audit");
        assertFalse(String.valueOf(rows.get(0).get("details")).contains(tv.code()), "jamais le code d'appareil entier");
        assertEquals(1, count("SELECT COUNT(*) FROM lic_audit WHERE target_id = ? AND action = 'SEAT_ATTACH_FROM_REPORT'", lic));
        assertTrue(audit.verify().ok(), "la chaîne d'audit reste intacte");
    }

    @org.springframework.beans.factory.annotation.Autowired AuditLog audit;

    @Test
    void theOwnerAcceptsAPendingRegistrationWithAReasonAndOnlyHeCan() {
        Acts.Tv tv = tv();
        String lic = licenseId();
        Registration pending = reg(production(ISSUER, tv, lic, null, NOW - 5 * DAY, null), tv);
        assertEquals("INSTALL_TIME_UNKNOWN", pending.reason());
        assertThrows(ApiException.class, () -> registrar.decide(SUPPORT, pending.fp(), true, "essai", T0), "le support ne décide pas");
        assertThrows(ApiException.class, () -> registrar.decide(OWNER, pending.fp(), true, "  ", T0), "motif obligatoire");
        Registration done = registrar.decide(OWNER, pending.fp(), true, "TV vue chez le client le 29/09", T0);
        assertEquals(Status.REGISTERED, done.status(), done.reason());
        assertEquals(1, seats(lic));
        assertEquals(1, count("SELECT COUNT(*) FROM lic_registration WHERE fp = ? AND declared = TRUE AND decided_by = 'owner-test'", pending.fp()));
        assertEquals(1, count("SELECT COUNT(*) FROM lic_audit WHERE action = 'REGISTRATION_DECIDED' AND target_id = ?", lic));
        assertThrows(ApiException.class, () -> registrar.decide(OWNER, pending.fp(), true, "encore", T0), "une décision ne se rejoue pas");
    }

    @Test
    void theOwnerRefusesAPendingRegistration() {
        Acts.Tv tv = tv();
        String lic = licenseId();
        Registration pending = reg(production(ISSUER, tv, lic, null, NOW - 5 * DAY, null), tv);
        Registration r = registrar.decide(OWNER, pending.fp(), false, "inconnu du client", T0);
        assertEquals(Status.REFUSED, r.status());
        assertEquals("OWNER_REFUSED", r.reason());
        assertEquals(0, count("SELECT COUNT(*) FROM lic_license WHERE license_id = ?", lic));
    }

    @Test
    void pendingRowsListNoTokenAndAMaskedDevice() {
        Acts.Tv tv = tv();
        reg(production(ISSUER, tv, licenseId(), null, NOW - 5 * DAY, null), tv);
        var rows = registrar.pending(500);
        assertFalse(rows.isEmpty());
        for (var p : rows) {
            assertTrue(p.deviceCode().endsWith("-****"), p.deviceCode());
            assertEquals(64, p.fp().length());
        }
    }
}
