package castbridge.server.licenses;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import castbridge.server.licenses.ReportedActivationRegistrar.Presented;
import castbridge.server.licenses.ReportedActivationRegistrar.Registration;
import castbridge.server.licenses.ReportedActivationRegistrar.Status;
import castbridge.server.licenses.ReportedActivationRegistrar.Via;
import castbridge.server.wallet.Acts;
import castbridge.server.wallet.CatchUpPolicy;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Correctifs de l'audit Opus w23-05 (docs/agent-reports/audit-opus-w23-05.md). Chaque test décrit le comportement ATTENDU : écrit AVANT le correctif, il échouait par assertion sur le code audité.
 * Les clés d'installation, d'émetteur et d'administrateur sont fabriquées ici (aucune clé réelle). Staging = production : la preuve de possession est exigée, aucun raccourci.
 */
class AuditW2305FixTest extends RegistrarTestBase {

    private Registration reg(String token, Acts.Tv tv) { return reg(token, tv, T0); }

    private Registration reg(String token, Acts.Tv tv, Instant now) { return regWith(token, tv, installOf(tv), now); }

    private Registration regWith(String token, Acts.Tv tv, KeyPair key, Instant now) { return registrar.register(new Presented(token, tv.code(), rawPublic(key), true), Via.WALLET, now); }

    private Map<String, Object> licence(String id) { return jdbc.queryForMap("SELECT * FROM lic_license WHERE license_id = ?", id); }

    private static Instant at(Object ts) { return ts == null ? null : castbridge.server.common.Times.instant(ts); }

    private long seats(String lic) { return count("SELECT COUNT(*) FROM lic_seat s JOIN lic_license l ON l.id = s.license_pk WHERE l.license_id = ? AND s.state = 'ACTIVE'", lic); }

    private static String fingerprintOf(KeyPair install) {
        byte[] d = Hashing.sha256(rawPublicBytes(install));
        String hex = java.util.HexFormat.of().formatHex(d, 0, 16);
        return String.join("-", hex.substring(0, 4), hex.substring(4, 8), hex.substring(8, 12), hex.substring(12, 16), hex.substring(16, 20), hex.substring(20, 24), hex.substring(24, 28), hex.substring(28, 32));
    }

    private static String windowed(KeyPair issuer, Acts.Tv tv, String license, long issuedAt, long windowMs) {
        String kid = LicenseKeyring.kidOf(rawPublicBytes(issuer));
        WireActivation.Fields f = new WireActivation.Fields("production", "tv", kid, 1, nonce16(), issuedAt, issuedAt, issuedAt + windowMs, license, WireActivation.defaultSeat(license, tv.factors()),
                DeviceIdentity.kFor(tv.factors().size()), tv.factors(), List.of(ikLine(installOf(tv))));
        return WireActivation.token(f, Acts.sign(issuer, WireActivation.payload(f)));
    }

    // ================================================================== HIGH-1 : l'activation est liée à la clé d'installation de la TV

    @Test
    void f1a_aCopiedLegacyTokenWithTheAttackersKeyPaysNothingAndWaitsForTheOwner() throws Exception {
        Acts.Tv tv = tv();
        String lic = licenseId();
        String legacy = productionLegacy(ISSUER, tv, lic, null, NOW - HOUR, null);   // une activation déjà émise : aucune clé d'installation signée
        Registered attacker = registerApp("tv");
        JsonNode s = ok(sync(attacker, pair(), tv, legacy));
        assertEquals(0, balance(tv.code(), "NDEM"), "un jeton sans clé d'installation ne paie jamais seul : " + s);
        assertEquals(0, balance(tv.code(), "MBOKO"));
        assertEquals(0, count("SELECT COUNT(*) FROM lic_license WHERE license_id = ?", lic), "aucune licence créée sans décision du propriétaire");
        JsonNode reg = s.path("registration").get(0);
        assertEquals("PENDING_DECISION", reg.path("status").asText(), s.toString());
        assertEquals("NO_INSTALL_KEY", reg.path("reason").asText(), s.toString());
        boolean visible = false;
        for (JsonNode n : s.path("notices")) if (n.path("reason").asText().equals("REGISTRATION_REVIEW") && n.path("detail").asText().equals("NO_INSTALL_KEY") && n.path("text").asText().contains("clé d'installation")) visible = true;
        assertTrue(visible, "avis visible sur la TV : " + s.path("notices"));
    }

    @Test
    void f1b_aTokenBoundToTheTvKeyPaysNobodyElseAndTheRealTvIsStillPaid() throws Exception {
        Acts.Tv tv = tv();
        String lic = licenseId();
        String token = production(ISSUER, tv, lic, null, NOW - HOUR, null);   // la clé d'installation de la vraie TV est SIGNÉE dans l'activation
        Registered attacker = registerApp("tv");
        sync(attacker, pair(), tv, token);
        assertEquals(0, balance(tv.code(), "NDEM"), "l'attaquant, avec sa propre clé, ne reçoit rien");
        assertEquals(0, count("SELECT COUNT(*) FROM lic_license WHERE license_id = ?", lic), "ni licence");
        assertEquals(0, count("SELECT COUNT(*) FROM wallet_identity WHERE holder = ? AND api_device_id > 0", tv.code()), "ni l'identité de portefeuille de la victime");
        Registration direct = regWith(token, tv, pair(), T0);
        assertEquals(Status.REFUSED, direct.status());
        assertEquals("INSTALL_KEY_MISMATCH", direct.reason());
        assertEquals(0, count("SELECT COUNT(*) FROM lic_registration WHERE license_id = ?", lic), "aucune ligne n'est écrite pour la clé de l'attaquant : la vraie TV n'est pas bloquée");
        Registered real = registerApp("tv");
        JsonNode s = ok(sync(real, tv, token));
        assertFalse(s.path("edition").path("boundOther").asBoolean(), s.toString());
        assertEquals("UNLIMITED", s.path("edition").path("ed").asText(), s.toString());
        assertEquals(5000, balance(tv.code(), "NDEM"));
        assertEquals(50, balance(tv.code(), "MBOKO"));
        assertEquals("REGISTERED", s.path("registration").get(0).path("status").asText(), s.toString());
    }

    @Test
    void f1c_aSignedWindowLongerThan48HoursIsRefusedForAutomaticRegistration() {
        Acts.Tv tv = tv();
        String lic = licenseId();
        Registration r = reg(windowed(ISSUER, tv, lic, NOW - HOUR, 30 * DAY), tv);
        assertEquals(Status.REFUSED, r.status());
        assertEquals("WINDOW_TOO_LONG", r.reason());
        assertEquals(0, count("SELECT COUNT(*) FROM lic_license WHERE license_id = ?", lic));
        assertEquals(Status.REGISTERED, reg(windowed(ISSUER, tv, licenseId(), NOW - HOUR, 48 * HOUR), tv).status(), "48 heures exactement : accepté");
    }

    @Test
    void f1d_anIssueDateMoreThanAnHourInTheFutureIsRefused() {
        Acts.Tv tv = tv();
        String lic = licenseId();
        Registration r = reg(production(ISSUER, tv, lic, null, NOW + 2 * HOUR, null), tv);
        assertEquals(Status.REFUSED, r.status());
        assertEquals("CLOCK", r.reason());
        assertEquals(0, count("SELECT COUNT(*) FROM lic_license WHERE license_id = ?", lic));
    }

    @Test
    void f1e_theOwnerDecidesALegacyTokenWithTheFingerprintsInFrontOfHimAndItPaysOnce() throws Exception {
        Acts.Tv tv = tv();
        KeyPair realKey = installOf(tv);
        String lic = licenseId();
        String legacy = productionLegacy(ISSUER, tv, lic, null, NOW - HOUR, null);
        Registered dev = registerApp("tv");
        JsonNode s = ok(sync(dev, tv, legacy));
        assertEquals(0, balance(tv.code(), "NDEM"));
        String fp = Hashing.sha256Hex(legacy);
        JsonNode list = body(mvc.perform(get("/api/v1/admin/licenses/registrations").header("Authorization", ADMIN)).andReturn());
        JsonNode item = null;
        for (JsonNode p : list.path("items")) if (p.path("fp").asText().equals(fp)) item = p;
        assertTrue(item != null, list.toString());
        assertEquals("NO_INSTALL_KEY", item.path("reason").asText());
        assertEquals(fingerprintOf(realKey), item.path("installKeyFingerprint").asText(), "l'empreinte lisible de la clé d'installation, comparable à l'écran de la TV : " + item);
        assertFalse(item.path("installKeySigned").asBoolean(true), "le jeton ne porte aucune clé d'installation signée");
        assertTrue(item.path("deviceCode").asText().endsWith("-****"));
        assertTrue(item.path("factors").asText().contains("FLASH"), "résumé des facteurs : " + item);
        assertEquals(fp, item.path("tokenFingerprint").asText(), "l'empreinte du jeton");
        Admin owner = newAdmin();
        MvcResult d = mvc.perform(owner.sign(post("/api/v1/admin/licenses/registrations/" + fp + "/decision").header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON)
                .content("{\"accept\":true,\"reason\":\"TV du client, empreinte vérifiée\"}"))).andReturn();
        assertEquals(200, d.getResponse().getStatus(), d.getResponse().getContentAsString());
        ok(sync(dev, tv, legacy));
        assertEquals(5000, balance(tv.code(), "NDEM"));
        assertEquals(50, balance(tv.code(), "MBOKO"));
        ok(sync(dev, tv, legacy));
        assertEquals(5000, balance(tv.code(), "NDEM"), "rejeu : rien de plus");
    }

    // ================================================================== HIGH-2 : REACTIVATE ne change ni début ni fin et ne crée rien

    @Test
    void f3_aReactivateOnlyKeyNeverChangesTheEndOfALicence() throws Exception {
        Acts.Tv tv = tv();
        String lic = licenseId();
        Registered dev = registerApp("tv");
        String ninety = production(ISSUER, tv, lic, null, NOW - HOUR, 90);
        ok(sync(dev, tv, ninety));
        assertEquals(1000, balance(tv.code(), "NDEM"));
        Instant end0 = at(licence(lic).get("end_at")), start0 = at(licence(lic).get("start_at"));
        assertEquals(Instant.ofEpochMilli(NOW - HOUR + 90 * DAY), end0);
        String react = production(REACT, tv, lic, null, NOW - 30 * 60_000L, null);   // une clé de réactivation (support) illimitée
        JsonNode s = ok(sync(dev, tv, react, ninety));
        assertEquals(end0, at(licence(lic).get("end_at")), "la fin de la licence n'a pas bougé : " + s);
        assertEquals(start0, at(licence(lic).get("start_at")));
        assertEquals("PROD", s.path("edition").path("ed").asText(), "jamais ILLIMITÉE par une clé de réactivation : " + s);
        assertEquals(1000, balance(tv.code(), "NDEM"), "aucun versement de plus");
        assertEquals(1, count("SELECT COUNT(*) FROM lic_license WHERE license_id = ?", lic));
        // même sans aucune clé de création : la fin ne dépend que des clés qui créent
        Acts.Tv other = tv();
        assertNotEquals(Status.REGISTERED, reg(production(REACT, other, lic, null, NOW - HOUR, null), other).status(), "une clé de réactivation ne crée aucun poste");
        assertEquals(1, seats(lic));
    }

    // ================================================================== HIGH-3 : une période déjà payée à cette TV ne se repaie jamais, quelle que soit la licence

    @Test
    void f4_aSecondLicenceForTheSameTvNeverRepaysAPeriodAlreadyPaid_7000StaysSevenThousand() throws Exception {
        Acts.Tv tv = tv();
        Registered dev = registerApp("tv");
        String l1 = licenseId(), l2 = licenseId();
        // L1 : la licence du propriétaire (début 65 jours avant), sans fin, un poste
        licenses.create(OWNER, new LicenseService.NewLicense(l1, clients.create(OWNER, "Client f4", null, null).id(), "PAID", 1, T0.minusSeconds(65 * 86_400L), null, null, null, null));
        String t1 = production(ISSUER, tv, l1, null, NOW - HOUR, null);
        ok(sync(dev, tv, t1));
        assertEquals(5000 + 2 * 1000, balance(tv.code(), "NDEM"), "ouverture + périodes à -35 et -5 jours");
        assertEquals(50 + 2 * 10, balance(tv.code(), "MBOKO"));
        // L2 : une activation émise 2 heures AVANT le début de L1, sous un autre identifiant, hors fenêtre : elle attend le propriétaire
        String t2 = production(ISSUER, tv, l2, null, NOW - 65 * DAY - 2 * HOUR, null);
        JsonNode s = ok(sync(dev, tv, t1, t2));
        assertEquals("INSTALL_TIME_UNKNOWN", s.path("registration").get(1).path("reason").asText(), s.toString());
        Registration r = registrar.decide(OWNER, Hashing.sha256Hex(t2), true, "TV vue chez le client", T0);
        assertEquals(Status.REGISTERED, r.status(), r.reason());
        ok(sync(dev, tv, t1, t2));
        assertEquals(7000, balance(tv.code(), "NDEM"), "7000 -> 9000 ne doit PAS arriver");
        assertEquals(70, balance(tv.code(), "MBOKO"));
        ok(sync(dev, tv, t2, t1));
        assertEquals(7000, balance(tv.code(), "NDEM"));
        // un mois plus tard : UNE seule tranche pour la nouvelle période, jamais une par licence
        clock.freezeAt(T0.plusSeconds(31 * 86_400L));
        ok(sync(dev, tv, t1, t2));
        assertEquals(8000, balance(tv.code(), "NDEM"), "une période, une tranche");
        assertEquals(80, balance(tv.code(), "MBOKO"));
        ok(sync(dev, tv, t2, t1));
        assertEquals(8000, balance(tv.code(), "NDEM"));
    }

    // ================================================================== MEDIUM-1 : le refus du propriétaire est définitif

    @Test
    void f2_anOwnerRefusalForOverQuotaStaysFinalEvenWhenTheSeatsAreRaised() {
        Acts.Tv a = tv(), b = tv();
        String lic = licenseId();
        assertEquals(Status.REGISTERED, reg(production(ISSUER, a, lic, null, NOW - HOUR, 90), a).status());
        String tokenB = production(ISSUER, b, lic, null, NOW - HOUR, 90);
        Registration pending = reg(tokenB, b);
        assertEquals("OVER_QUOTA", pending.reason());
        Registration refused = registrar.decide(OWNER, pending.fp(), false, "pas notre client", T0);
        assertEquals("OWNER_REFUSED", refused.reason());
        jdbc.update("UPDATE lic_license SET seats_allowed = 2 WHERE license_id = ?", lic);
        Registration again = reg(tokenB, b);
        assertEquals(Status.REFUSED, again.status());
        assertEquals("OWNER_REFUSED", again.reason(), "un refus ne se rouvre pas parce qu'une place s'est libérée");
        assertEquals(1, seats(lic));
    }

    @Test
    void f2b_anOwnerRefusalForAnOutOfWindowTokenIsNotReopenedByTheNextSync() {
        Acts.Tv tv = tv();
        String lic = licenseId();
        String token = production(ISSUER, tv, lic, null, NOW - 5 * DAY, null);
        Registration pending = reg(token, tv);
        assertEquals("INSTALL_TIME_UNKNOWN", pending.reason());
        registrar.decide(OWNER, pending.fp(), false, "inconnu", T0);
        Registration later = reg(token, tv, T0.plusSeconds(15 * 60));
        assertEquals(Status.REFUSED, later.status());
        assertEquals("OWNER_REFUSED", later.reason());
        assertEquals("REFUSED", jdbc.queryForObject("SELECT status FROM lic_registration WHERE fp = ?", String.class, pending.fp()));
        assertEquals(0, count("SELECT COUNT(*) FROM lic_registration WHERE status = 'PENDING_DECISION' AND license_id = ?", lic), "la ligne ne revient pas dans la liste");
    }

    // ================================================================== MEDIUM-2 : le renouvellement après une interruption ne paie pas l'interruption

    @Test
    void f8_aRenewalUnderTheSameLicenceAfterAGapDoesNotPayTheGap() throws Exception {
        Acts.Tv tv = tv();
        String lic = licenseId();
        Registered dev = registerApp("tv");
        long from1 = NOW - HOUR;
        String first = production(ISSUER, tv, lic, null, from1, 30);
        ok(sync(dev, tv, first));
        assertEquals(1000, balance(tv.code(), "NDEM"));
        // 100 jours plus tard : la même licence est renouvelée pour 90 jours ; aucune clé ne valait entre J+30 et J+100
        clock.freezeAt(T0.plusSeconds(100 * 86_400L));
        long from2 = clock.now().toEpochMilli() - HOUR;
        String renewal = production(ISSUER, tv, lic, null, from2, List.of("usage|duree|" + from2 + "|" + (from2 + 90 * DAY)), nonce16());
        ok(sync(dev, tv, renewal, first));
        assertEquals(1000, balance(tv.code(), "NDEM"), "les périodes de J+30, J+60 et J+90 ne sont pas payées : aucune clé ne valait");
        // la première période de la grille tombant dans le nouveau droit (J+120) est payée une fois
        clock.freezeAt(T0.plusSeconds(125 * 86_400L));
        ok(sync(dev, tv, renewal, first));
        assertEquals(2000, balance(tv.code(), "NDEM"));
        ok(sync(dev, tv, renewal, first));
        assertEquals(2000, balance(tv.code(), "NDEM"));
    }

    // ================================================================== MEDIUM-4 : une TV vue pendant une suspension a son poste après la reprise

    @Test
    void f5_aTvSeenDuringASuspensionGetsItsSeatAfterTheResume() {
        Acts.Tv a = tv(), b = tv();
        String lic = licenseWithSeats(2);
        assertEquals(Status.REGISTERED, reg(production(ISSUER, a, lic, null, NOW - HOUR, 90), a).status());
        licenses.suspend(OWNER, lic, "impayé");
        String tokenB = production(ISSUER, b, lic, null, NOW - HOUR, 90);
        Registration during = reg(tokenB, b);
        assertEquals(Status.PENDING_DECISION, during.status());
        assertEquals("LICENSE_SUSPENDED", during.reason());
        assertEquals(1, seats(lic));
        licenses.resume(OWNER, lic, "payé");
        Registration after = reg(tokenB, b);
        assertEquals(Status.REGISTERED, after.status(), after.reason());
        assertEquals(2, seats(lic), "le poste existe après la reprise");
    }

    // ================================================================== LOW-2 : le plafond mensuel se lève par la décision du propriétaire

    @Test
    void f6_theOwnerAcceptanceLiftsTheMonthlyCap() {
        fillRegistered(LicenseKeyring.kidOf(rawPublicBytes(RATE)), 50, T0.minusSeconds(3 * 86_400L));
        Acts.Tv tv = tv();
        String lic = licenseId();
        String token = production(RATE, tv, lic, null, NOW - HOUR, null);
        Registration capped = reg(token, tv);
        assertEquals("KEY_RATE", capped.reason());
        Registration decided = registrar.decide(OWNER, capped.fp(), true, "clé vérifiée, plafond levé", T0);
        assertEquals(Status.REGISTERED, decided.status(), "la décision du propriétaire lève le plafond : " + decided.reason());
        assertEquals(1, count("SELECT COUNT(*) FROM lic_license WHERE license_id = ?", lic));
    }

    // ================================================================== LOW-4 : l'avis « jetons à venir » ne ment pas

    @Test
    void low4_aDefinitiveRefusalDoesNotPromiseTokensToCome() throws Exception {
        Acts.Tv tv = tv();
        Registered dev = registerApp("tv");
        // une clé dont le droit d'usage a pris fin depuis longtemps (au-delà de la grâce) : refus définitif (EXPIRED), le portefeuille lit pourtant l'activation
        String ended = production(ISSUER, tv, licenseId(), null, NOW - HOUR, List.of("usage|duree|" + (NOW - 100 * DAY) + "|" + (NOW - 50 * DAY)), nonce16());
        JsonNode s = ok(sync(dev, tv, ended));
        assertEquals("REFUSED", s.path("registration").get(0).path("status").asText(), s.toString());
        assertEquals("EXPIRED", s.path("registration").get(0).path("reason").asText(), s.toString());
        assertFalse(noticeReasons(s).contains("REGISTRATION_REVIEW"), "un refus définitif n'annonce pas de jetons à venir : " + s.path("notices"));
    }

    @Test
    void low4_aTvAlreadyPaidByAnotherLicenceIsNotToldThatTokensAreComing() throws Exception {
        Acts.Tv tv = tv();
        Registered dev = registerApp("tv");
        String l1 = licenseId(), l2 = licenseId();
        ok(sync(dev, tv, production(ISSUER, tv, l1, null, NOW - HOUR, null)));
        JsonNode s = ok(sync(dev, tv, production(ISSUER, tv, l2, null, NOW - 5 * DAY, null)));   // hors fenêtre : attend le propriétaire, mais la TV a déjà sa licence
        assertEquals("INSTALL_TIME_UNKNOWN", s.path("registration").get(0).path("reason").asText(), s.toString());
        assertFalse(noticeReasons(s).contains("REGISTRATION_REVIEW"), "la TV est déjà payée par une autre licence : " + s.path("notices"));
    }

    // ================================================================== LOW-5 et LOW-6 : la décision par l'API

    @Test
    void low5_aDecisionOnAnUnknownFingerprintIs404() throws Exception {
        Admin owner = newAdmin();
        MvcResult r = mvc.perform(owner.sign(post("/api/v1/admin/licenses/registrations/" + "ab".repeat(32) + "/decision").header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON)
                .content("{\"accept\":true,\"reason\":\"inconnue\"}"))).andReturn();
        assertEquals(404, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    }

    @Test
    void low6_theDecisionNeedsTheNamedAdministratorAndAFreshTotpAndIsAudited() throws Exception {
        Acts.Tv tv = tv();
        String lic = licenseId();
        String token = production(ISSUER, tv, lic, null, NOW - 5 * DAY, null);
        Registration pending = reg(token, tv);
        String url = "/api/v1/admin/licenses/registrations/" + pending.fp() + "/decision";
        String body = "{\"accept\":true,\"reason\":\"TV vue chez le client\"}";
        assertEquals(403, mvc.perform(post(url).header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus(), "le jeton porteur seul ne décide pas");
        Admin owner = newAdmin();
        assertEquals(403, mvc.perform(post(url).header("Authorization", ADMIN).header("X-Admin-User", owner.name).header("X-Totp", "000000").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getStatus(), "code TOTP faux");
        MvcResult ok = mvc.perform(owner.sign(post(url).header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON).content(body))).andReturn();
        assertEquals(200, ok.getResponse().getStatus(), ok.getResponse().getContentAsString());
        assertEquals(1, count("SELECT COUNT(*) FROM lic_audit WHERE action = 'REGISTRATION_DECIDED' AND actor = ?", owner.name), "décision inscrite au nom de la personne");
        assertEquals(0, count("SELECT COUNT(*) FROM lic_audit WHERE action = 'REGISTRATION_DECIDED' AND actor = 'admin-token'"));
    }

    // ================================================================== LOW-7 et LOW-8

    @Test
    void low7_theV64MigrationHasItsOwnRollbackScript() throws Exception {
        Path dir = Path.of("src/main/resources/db/rollback");
        Path u64 = dir.resolve("U64__activation_registration_rollback.sql");
        assertTrue(Files.exists(u64), "retour arrière V64 séparé");
        String text = Files.readString(u64, StandardCharsets.UTF_8);
        assertTrue(text.contains("DROP TABLE IF EXISTS lic_registration"), text);
        assertTrue(text.contains("version = '64'"), "dit comment effacer la ligne de flyway_schema_history");
    }

    @Test
    void low8_theLateNoticeLimitIsTheCatchUpMaximum() {
        assertEquals(CatchUpPolicy.MAXIMUM.toDays(), ReportedActivationRegistrar.LATE_NOTICE_DAYS, "400 j contre 366 j : une clé illimitée acceptée entre les deux n'aurait jamais son ouverture");
    }

    // ================================================================== les 6 mutations qui survivaient (MB..MG)

    @Test
    void mb_aProductionTokenForAPhoneIsRefusedWrongSubject() {
        Acts.Tv tv = tv();
        String lic = licenseId();
        String kid = LicenseKeyring.kidOf(rawPublicBytes(ISSUER));
        long issued = NOW - HOUR;
        WireActivation.Fields f = new WireActivation.Fields("production", "phone", kid, 1, nonce16(), issued, issued, issued + 48 * HOUR, lic, WireActivation.defaultSeat(lic, tv.factors()),
                DeviceIdentity.kFor(tv.factors().size()), tv.factors(), List.of(ikLine(installOf(tv))));
        Registration r = reg(WireActivation.token(f, Acts.sign(ISSUER, WireActivation.payload(f))), tv);
        assertEquals(Status.REFUSED, r.status());
        assertEquals("WRONG_SUBJECT", r.reason());
        assertEquals(0, count("SELECT COUNT(*) FROM lic_license WHERE license_id = ?", lic));
    }

    @Test
    void mc_aLicenceInGraceCreatesNoSeatForNewHardware() {
        Acts.Tv a = tv(), b = tv();
        String lic = licenseId();
        assertEquals(Status.REGISTERED, reg(production(ISSUER, a, lic, null, NOW - HOUR, 90), a).status());
        jdbc.update("UPDATE lic_license SET seats_allowed = 2, end_at = ?, grace_days = 30 WHERE license_id = ?", Timestamp.from(T0.minusSeconds(5 * 86_400L)), lic);
        Registration r = reg(production(ISSUER, b, lic, null, NOW - HOUR, 90), b);
        assertEquals(Status.PENDING_DECISION, r.status());
        assertEquals("EXPIRED", r.reason());
        assertEquals(1, seats(lic), "aucun nouveau poste pendant la grâce");
    }

    @Test
    void md_aDeclaredEmissionIsNotStoppedByTheDailyCapButByTheMonthlyOne() {
        String kid = LicenseKeyring.kidOf(rawPublicBytes(RATE));
        fillRegistered(kid, 10, T0.minusSeconds(3600));   // 10 licences ouvertes aujourd'hui par cette clé : plafond du jour atteint
        Acts.Tv tv = tv();
        String lic = licenseId();
        licenses.create(OWNER, new LicenseService.NewLicense(lic, clients.create(OWNER, "Client md", null, null).id(), "PAID", 3, T0.minusSeconds(3600), null, null, null, null));
        long pk = jdbc.queryForObject("SELECT id FROM lic_license WHERE license_id = ?", Long.class, lic);
        String nonce = nonce16();
        String token = production(RATE, tv, lic, null, NOW - HOUR, List.of(), nonce);
        declare(pk, lic, tv, kid, nonce, "ab");
        Registration r = reg(token, tv);
        assertEquals(Status.REGISTERED, r.status(), "émission déclarée : le plafond du jour ne s'applique pas (" + r.reason() + ")");
        // le plafond du mois, lui, s'applique encore à une émission déclarée
        fillRegistered(kid, 40, T0.minusSeconds(3 * 86_400L));
        Acts.Tv other = tv();
        String nonce2 = nonce16();
        declare(pk, lic, other, kid, nonce2, "cd");
        assertEquals("KEY_RATE", reg(production(RATE, other, lic, null, NOW - HOUR, List.of(), nonce2), other).reason(), "le plafond mensuel vaut aussi pour une émission déclarée");
    }

    /** Une émission DÉCLARÉE : la ligne d'émission du registre (source IMPORT) pour (clé, nonce) de la licence. */
    private void declare(long licensePk, String lic, Acts.Tv tv, String kid, String nonce, String fpDigit) {
        jdbc.update("INSERT INTO lic_issuance (license_pk, seat_pk, seat_id, device_code, kind, subject, kid, nonce, issued_at, not_before, not_after, issuer, channel, token_fingerprint, source)"
                + " VALUES (?,NULL,?,?,'PRODUCTION','tv',?,?,?,?,?,'tool','ledger-test',?,'IMPORT')", licensePk, WireActivation.defaultSeat(lic, tv.factors()), tv.code(), kid, nonce, Timestamp.from(T0.minusSeconds(3600)),
                Timestamp.from(T0.minusSeconds(3600)), Timestamp.from(T0.plusSeconds(47 * 3600)), fpDigit.repeat(32));
    }

    /** {@code n} enregistrements fictifs « REGISTERED » de la clé, à l'instant donné (pour remplir un plafond sans créer n licences). */
    private void fillRegistered(String kid, int n, Instant when) {
        for (int i = 0; i < n; i++) {
            String fp = (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "").substring(0, 64);
            jdbc.update("INSERT INTO lic_registration (fp, kid, nonce, license_id, seat_id, device_code, factors, k, issued_at, expires_at, unlimited, first_server_at, last_server_at, registered_at, via, status)"
                    + " VALUES (?,?,?,?,?,?,?,?,?,?,TRUE,?,?,?,'wallet','REGISTERED')", fp, kid, nonce16(), "lic-synth" + UUID.randomUUID().toString().substring(0, 8), "00".repeat(8), "AAAA-AAAA-AAAA-AAAA",
                    "FLASH|00", 2, Timestamp.from(when), Timestamp.from(when), Timestamp.from(when), Timestamp.from(when), Timestamp.from(when));
        }
    }

    @Test
    void me_anOldImportedLicenceOfTheOwnerHasNoCatchUpLimit() throws Exception {
        Acts.Tv tv = tv();
        String lic = licenseId();
        Registered dev = registerApp("tv");
        // licence du propriétaire (pas « report: »), début 200 jours avant : toutes ses périodes sont dues, aucune limite de rattrapage
        licenses.create(OWNER, new LicenseService.NewLicense(lic, clients.create(OWNER, "Client me", null, null).id(), "PAID", 1, T0.minusSeconds(200 * 86_400L), null, null, null, null));
        ok(sync(dev, tv, production(ISSUER, tv, lic, null, NOW - HOUR, null)));
        assertEquals(5000 + 6 * 1000, balance(tv.code(), "NDEM"), "l'ouverture et les 6 périodes écoulées : la limite de rattrapage ne vise que les licences ouvertes par notification");
    }

    @Test
    void mf_aTokenIssuedAfterTheSeatRevocationIsAccepted() {
        Acts.Tv tv = tv();
        String lic = licenseId();
        assertEquals(Status.REGISTERED, reg(production(ISSUER, tv, lic, null, NOW - 10 * HOUR, 90), tv).status());
        String seat = WireActivation.defaultSeat(lic, tv.factors());
        jdbc.update("INSERT INTO lic_revocation (license_id, seat_id, reason, revoked_by, revoked_at) VALUES (?, ?, 'test', 'test', ?)", lic, seat, Timestamp.from(T0.minusSeconds(3 * 3600)));
        Registration before = reg(production(ISSUER, tv, lic, null, NOW - 5 * HOUR, 90), tv);
        assertEquals("REVOKED_SEAT", before.reason(), "émise avant la révocation");
        Registration after = reg(production(ISSUER, tv, lic, null, NOW - HOUR, 90), tv);
        assertNotEquals("REVOKED_SEAT", after.reason(), "émise APRÈS la révocation : acceptée au jugement");
    }

    @Test
    void mg_aTokenThatMakesTheDatabaseFailDoesNotBreakTheOthers() {
        Acts.Tv bad = tv(), good = tv();
        String badToken = production(ISSUER, bad, licenseId(), null, NOW - HOUR, 90);
        String goodToken = production(ISSUER, good, licenseId(), null, NOW - HOUR, 90);
        // la base refuse l'écriture de CE matériel seulement (contrainte posée pour le test)
        jdbc.execute("ALTER TABLE lic_registration ADD CONSTRAINT ck_test_mg CHECK (device_code <> '" + bad.code() + "')");
        try {
            List<Registration> out = registrar.registerAll(List.of(new Presented(badToken, bad.code(), rawPublic(installOf(bad)), true), new Presented(goodToken, good.code(), rawPublic(installOf(good)), true)),
                    Via.WALLET, T0);
            assertEquals(2, out.size());
            assertEquals("UNAVAILABLE", out.get(0).reason(), "l'échec d'un jeton est isolé");
            assertEquals(Status.REGISTERED, out.get(1).status(), "le second jeton est traité malgré l'échec du premier");
        } finally {
            jdbc.execute("ALTER TABLE lic_registration DROP CONSTRAINT ck_test_mg");
        }
    }
}
