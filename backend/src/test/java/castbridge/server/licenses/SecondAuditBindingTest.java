package castbridge.server.licenses;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import castbridge.server.licenses.ReportedActivationRegistrar.Presented;
import castbridge.server.licenses.ReportedActivationRegistrar.Registration;
import castbridge.server.licenses.ReportedActivationRegistrar.Status;
import castbridge.server.licenses.ReportedActivationRegistrar.Via;
import castbridge.server.wallet.Acts;
import com.fasterxml.jackson.databind.JsonNode;
import java.security.KeyPair;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Second audit Opus w23-05, HIGH-A (tests R1 et R2 de l'auditeur, réécrits) : la clé d'installation signée dans l'activation ({@code ik}) protège aussi le BÉNÉFICIAIRE, pas seulement la création de la
 * licence. Une activation {@code ik} PROUVÉE l'emporte sur une liaison prise par un voleur avec un jeton sans {@code ik} ; une licence {@code ik} ne paie jamais une identité dont la clé n'est pas {@code ik} ;
 * la réaffectation par le propriétaire ne lie QUE l'empreinte de clé lue sur l'écran de la TV. Staging = production : preuve de possession exigée.
 */
class SecondAuditBindingTest extends RegistrarTestBase {
    private long deviceId(Registered r) { return count("SELECT id FROM device WHERE public_id = ?", r.publicId()); }

    private long boundTo(Acts.Tv tv) { return count("SELECT api_device_id FROM wallet_identity WHERE holder = ?", tv.code()); }

    static String fingerprintOf(KeyPair install) { return ReportedActivationRegistrar.installKeyFingerprint(rawPublic(install)); }

    private MvcResult rebind(Admin admin, Acts.Tv tv, String fp) throws Exception {
        String body = "{\"identity\":\"" + tv.code() + "\",\"reason\":\"TV d'origine reconnue par le support\"" + (fp == null ? "" : ",\"installKeyFingerprint\":\"" + fp + "\"") + "}";
        return mvc.perform(admin.sign(post("/api/v1/admin/wallet/rebind").header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON).content(body))).andReturn();
    }

    @Test
    void r1_anIdentityCapturedWithALegacyTokenIsTakenBackByTheRealTvsProvenIkActivationAndPaidOnce() throws Exception {
        Acts.Tv tv = tv();
        String lic = licenseId();
        KeyPair thiefKey = pair();
        Registered thief = registerApp("tv"), real = registerApp("tv");
        String legacy = productionLegacy(ISSUER, tv, lic, null, NOW - HOUR, null);
        String withIk = production(ISSUER, tv, lic, null, NOW - HOUR, null);
        JsonNode t1 = ok(sync(thief, thiefKey, tv, legacy));
        assertEquals(deviceId(thief), boundTo(tv), "le voleur a lié l'identité : " + t1);
        assertEquals(0, balance(tv.code(), "NDEM"));

        JsonNode s = ok(sync(real, tv, withIk));
        assertFalse(s.path("edition").path("boundOther").asBoolean(), "la vraie TV, qui prouve la clé signée dans son activation, reprend l'identité : " + s);
        assertEquals(deviceId(real), boundTo(tv), "l'identité est liée à la vraie TV : " + s);
        assertEquals(rawPublic(installOf(tv)), jdbc.queryForObject("SELECT install_pub FROM wallet_identity WHERE holder = ?", String.class, tv.code()));
        assertEquals(5000, balance(tv.code(), "NDEM"), "aucun paiement n'est perdu");
        assertEquals(50, balance(tv.code(), "MBOKO"));
        assertEquals(1, count("SELECT COUNT(*) FROM lic_audit WHERE action = 'WALLET_REBIND_BY_IK' AND target_id = ?", tv.code()), "la reprise est auditée");
        assertEquals(1, count("SELECT COUNT(*) FROM lic_audit WHERE action = 'REGISTRATION_ALERT' AND reason = 'BINDING_TAKEN_OVER' AND target_id = ?", tv.code()), "alerte douce au propriétaire");

        JsonNode t2 = ok(sync(thief, thiefKey, tv, legacy));
        assertTrue(t2.path("edition").path("boundOther").asBoolean(), "le voleur ne reprend pas l'identité : " + t2);
        assertEquals(5000, balance(tv.code(), "NDEM"), "rien de plus n'est versé");
        assertEquals(deviceId(real), boundTo(tv));
        ok(sync(real, tv, withIk));
        assertEquals(5000, balance(tv.code(), "NDEM"));
        assertEquals(1, count("SELECT COUNT(*) FROM lic_audit WHERE action = 'WALLET_REBIND_BY_IK' AND target_id = ?", tv.code()), "une seule reprise");
    }

    @Test
    void r1b_aLicenceRegisteredWithIkNeverPaysAnIdentityWhoseKeyIsNotIk() throws Exception {
        Acts.Tv tv = tv();
        String lic = licenseId();
        KeyPair thiefKey = pair();
        Registered thief = registerApp("tv");
        String legacy = productionLegacy(ISSUER, tv, lic, null, NOW - HOUR, null);
        String withIk = production(ISSUER, tv, lic, null, NOW - HOUR, null);
        ok(sync(thief, thiefKey, tv, legacy));
        // la licence est ouverte par la vraie TV sur le chemin de notification (rapport) : elle porte ik, mais l'identité de portefeuille reste liée à la clé du voleur
        Registration r = registrar.register(new Presented(withIk, tv.code(), rawPublic(installOf(tv)), true), Via.REPORT, T0);
        assertEquals(Status.REGISTERED, r.status(), r.reason());
        JsonNode s = ok(sync(thief, thiefKey, tv, legacy));
        assertEquals(0, balance(tv.code(), "NDEM"), "la licence ik ne paie pas l'identité liée à une autre clé : " + s);
        assertEquals(0, balance(tv.code(), "MBOKO"));
        assertFalse("UNLIMITED".equals(s.path("edition").path("ed").asText()), "pas d'édition illimitée pour le voleur : " + s);
        assertEquals(0, count("SELECT COUNT(*) FROM wallet_license_claim WHERE license_id = ?", lic), "la licence n'est pas réclamée par l'identité du voleur");
        Registered real = registerApp("tv");
        ok(sync(real, tv, withIk));
        assertEquals(5000, balance(tv.code(), "NDEM"));
    }

    @Test
    void r2_afterTheOwnersRebindOnlyTheKeyWhoseFingerprintWasTypedCanBindTheIdentity() throws Exception {
        Acts.Tv tv = tv();
        String lic = licenseId();
        KeyPair thiefKey = pair();
        Registered thief = registerApp("tv"), real = registerApp("tv");
        String legacy = productionLegacy(ISSUER, tv, lic, null, NOW - HOUR, null);
        String withIk = production(ISSUER, tv, lic, null, NOW - HOUR, null);
        ok(sync(thief, thiefKey, tv, legacy));
        Admin owner = newAdmin();
        MvcResult missing = rebind(owner, tv, null);
        assertEquals(400, missing.getResponse().getStatus(), "l'empreinte de la clé d'installation (écran de la TV) est obligatoire : " + missing.getResponse().getContentAsString());
        assertEquals(400, rebind(owner, tv, "1234-5678").getResponse().getStatus(), "empreinte mal formée");
        assertEquals(deviceId(thief), boundTo(tv), "un rebind refusé ne change rien");
        MvcResult done = rebind(owner, tv, fingerprintOf(installOf(tv)).toUpperCase());
        assertEquals(200, done.getResponse().getStatus(), done.getResponse().getContentAsString());
        assertEquals(0, boundTo(tv));
        assertEquals(1, count("SELECT COUNT(*) FROM wallet_identity WHERE holder = ? AND expected_install_fp IS NOT NULL", tv.code()), "l'empreinte attendue est enregistrée");

        MvcResult stolen = sync(thief, thiefKey, tv, legacy);
        assertEquals(409, stolen.getResponse().getStatus(), "le voleur ne reprend pas l'identité avant la vraie TV : " + stolen.getResponse().getContentAsString());
        assertEquals(0, boundTo(tv), "toujours libre");

        JsonNode s = ok(sync(real, tv, withIk));
        assertEquals(deviceId(real), boundTo(tv), "la vraie TV, dont la clé a l'empreinte attendue, lie l'identité : " + s);
        assertFalse(s.path("edition").path("boundOther").asBoolean(), s.toString());
        assertEquals(5000, balance(tv.code(), "NDEM"));
        assertNull(jdbc.queryForObject("SELECT expected_install_fp FROM wallet_identity WHERE holder = ?", String.class, tv.code()), "l'attente est levée une fois liée");
        assertEquals(1, count("SELECT COUNT(*) FROM lic_audit WHERE action = 'WALLET_REBIND' AND target_id = ?", tv.code()));
    }

    @Test
    void r2b_aLegacyTokenOfTheRealTvWithTheExpectedKeyBindsAfterTheRebind() throws Exception {
        Acts.Tv tv = tv();
        KeyPair thiefKey = pair();
        Registered thief = registerApp("tv"), real = registerApp("tv");
        String legacy = productionLegacy(ISSUER, tv, licenseId(), null, NOW - HOUR, null);
        ok(sync(thief, thiefKey, tv, legacy));
        assertEquals(200, rebind(newAdmin(), tv, fingerprintOf(installOf(tv))).getResponse().getStatus());
        JsonNode s = ok(sync(real, tv, legacy));
        assertEquals(deviceId(real), boundTo(tv), s.toString());
    }

    @Test
    void r2c_aRebindWithAnotherFingerprintLeavesTheIdentityToTheTvThatOwnsThatKeyOnly() throws Exception {
        Acts.Tv tv = tv();
        KeyPair thiefKey = pair(), other = pair();
        Registered thief = registerApp("tv"), real = registerApp("tv");
        String legacy = productionLegacy(ISSUER, tv, licenseId(), null, NOW - HOUR, null);
        ok(sync(thief, thiefKey, tv, legacy));
        assertEquals(200, rebind(newAdmin(), tv, fingerprintOf(other)).getResponse().getStatus());
        assertEquals(409, sync(real, installOf(tv), tv, legacy).getResponse().getStatus(), "la clé de la TV n'a pas l'empreinte attendue : refusée");
        assertEquals(0, boundTo(tv));
        ok(sync(real, other, tv, legacy));
        assertEquals(deviceId(real), boundTo(tv));
    }
}
