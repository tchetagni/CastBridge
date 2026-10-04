package castbridge.server.licenses;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import castbridge.server.wallet.Acts;
import com.fasterxml.jackson.databind.JsonNode;
import java.security.KeyPair;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Second audit Opus w23-05, MEDIUM-C : la demande d'appareil n'est pas signée, un intermédiaire peut remplacer {@code install_sig} par sa clé. Le serveur ne peut pas l'empêcher, mais le refus
 * {@code INSTALL_KEY_MISMATCH} n'est plus jamais silencieux : une alerte pour le propriétaire avec les deux empreintes, et un avis sur la TV qui dit l'empreinte attendue.
 */
class SecondAuditInstallKeyTest extends RegistrarTestBase {
    private static String swapped(Acts.Tv tv, String license, KeyPair middleman) {
        return productionRaw(ISSUER, tv, license, null, NOW - HOUR, List.of(ikLine(middleman)), nonce16());
    }

    @Test
    void m3_aSwappedInstallKeyRefusalIsVisibleToTheTvAndToTheOwnerWithBothFingerprints() throws Exception {
        Acts.Tv tv = tv();
        String lic = licenseId();
        KeyPair middleman = pair();
        String token = swapped(tv, lic, middleman);
        Registered dev = registerApp("tv");
        String expected = InstallKeyFingerprint.ofBase64(rawPublic(middleman)), own = InstallKeyFingerprint.ofBase64(rawPublic(installOf(tv)));
        MvcResult refused = sync(dev, tv, token);
        assertEquals(409, refused.getResponse().getStatus(), refused.getResponse().getContentAsString());
        JsonNode err = body(refused);
        assertTrue(err.path("message").asText().contains("Activation non reconnue"), "un message clair sur la TV, jamais un refus silencieux : " + err);
        assertTrue(err.path("message").asText().contains(expected), "l'empreinte attendue figure dans le message : " + err);
        assertTrue(err.path("message").asText().contains(own), "l'empreinte de cette TV aussi : " + err);
        assertEquals("ACTIVATE", err.path("details").get(0).asText(), "le motif fermé reste ACTIVATE en tête : " + err);
        assertEquals("INSTALL_KEY_MISMATCH", err.path("details").get(1).asText(), err.toString());
        assertEquals(0, count("SELECT COUNT(*) FROM lic_license WHERE license_id = ?", lic), "rien n'est créé");
        assertEquals(0, balance(tv.code(), "NDEM"));
        JsonNode list = body(mvc.perform(get("/api/v1/admin/licenses/registrations").header("Authorization", ADMIN)).andReturn());
        JsonNode alert = null;
        for (JsonNode a : list.path("alerts")) if ("INSTALL_KEY_MISMATCH".equals(a.path("kind").asText()) && lic.equals(a.path("licenseId").asText())) alert = a;
        assertTrue(alert != null, "une ligne pour le propriétaire : " + list.path("alerts"));
        assertEquals(expected, alert.path("expectedFingerprint").asText(), alert.toString());
        assertEquals(own, alert.path("presentedFingerprint").asText(), alert.toString());
        sync(dev, tv, token);
        assertEquals(1, count("SELECT COUNT(*) FROM lic_audit WHERE action = 'REGISTRATION_ALERT' AND reason = 'INSTALL_KEY_MISMATCH' AND target_id = ?", lic), "une seule ligne par activation");
    }

    @Test
    void m3b_theRealTvIsNotBlockedByTheRecordedMismatchOfItsSwappedToken() throws Exception {
        Acts.Tv tv = tv();
        String lic = licenseId();
        String swapped = swapped(tv, lic, pair());
        Registered dev = registerApp("tv");
        assertEquals(409, sync(dev, tv, swapped).getResponse().getStatus());
        String good = production(ISSUER, tv, lic, null, NOW - HOUR, null);
        JsonNode s = ok(sync(dev, tv, good));
        assertEquals("REGISTERED", s.path("registration").get(0).path("status").asText(), s.toString());
        assertEquals(5000, balance(tv.code(), "NDEM"));
    }

    @Test
    void m3c_aPresentationWithoutProofWritesNoAlert() throws Exception {
        Acts.Tv tv = tv();
        String lic = licenseId();
        Registered dev = registerApp("tv");
        sync(dev, null, tv, swapped(tv, lic, pair()));
        assertFalse(count("SELECT COUNT(*) FROM lic_audit WHERE action = 'REGISTRATION_ALERT' AND reason = 'INSTALL_KEY_MISMATCH' AND target_id = ?", lic) > 0, "sans preuve de possession, rien n'est écrit");
    }

    @Test
    void m3d_aTvWithAnotherValidActivationSeesTheMismatchAsANoticeOnTheNormalAnswer() throws Exception {
        Acts.Tv tv = tv();
        Registered dev = registerApp("tv");
        KeyPair middleman = pair();
        String good = production(ISSUER, tv, licenseId(), null, NOW - HOUR, null);
        JsonNode s = ok(sync(dev, tv, good, swapped(tv, licenseId(), middleman)));
        JsonNode notice = null;
        for (JsonNode n : s.path("notices")) if ("INSTALL_KEY_MISMATCH".equals(n.path("detail").asText())) notice = n;
        assertTrue(notice != null, "avis sur la réponse normale : " + s.path("notices"));
        assertTrue(notice.path("text").asText().contains(InstallKeyFingerprint.ofBase64(rawPublic(middleman))), notice.toString());
        assertEquals(5000, balance(tv.code(), "NDEM"), "l'activation valide est payée normalement");
    }
}
