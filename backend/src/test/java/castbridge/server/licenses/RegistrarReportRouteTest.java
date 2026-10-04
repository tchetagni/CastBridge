package castbridge.server.licenses;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import castbridge.server.wallet.Acts;
import com.fasterxml.jackson.databind.JsonNode;
import java.security.KeyPair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Le parcours complet de la TV 0.14.32 contre le serveur : aucune licence ni poste, le portefeuille dit « licence en attente » (état NONE du 2026-10-04) ; la TV (ou le téléphone relais)
 * présente l'activation vérifiée avec la preuve de possession à {@code POST /api/v1/activations/report} ; la licence et le poste existent ; la synchronisation suivante rend UNLIMITED avec
 * 5 000 NDEM + 50 MBOKO ; deux rejeux ne paient pas deux fois. Le temps est celui de l'horloge réelle (le module des activations a la sienne), aucun raccourci de test.
 */
@ExtendWith(OutputCaptureExtension.class)
class RegistrarReportRouteTest extends RegistrarTestBase {

    private MvcResult report(Registered dev, Acts.Tv tv, KeyPair install, String codeInBind, String... tokens) throws Exception {
        StringBuilder sb = new StringBuilder("{\"v\":1,\"deviceCode\":\"").append(tv.code()).append("\",\"app\":{\"code\":1432,\"name\":\"0.14.32-beta\"},\"activations\":[");
        for (int i = 0; i < tokens.length; i++) sb.append(i > 0 ? "," : "").append('"').append(tokens[i]).append('"');
        sb.append("],\"state\":{\"edition\":\"PRODUCTION\",\"usageTo\":null,\"super\":false,\"openAllUntil\":0,\"unlockUntil\":0,\"trialResets\":0,\"installedAt\":{},\"commands\":[]},\"at\":")
                .append(System.currentTimeMillis());
        if (install != null) sb.append(",\"bind\":").append(bind(install, codeInBind, dev.publicId(), System.currentTimeMillis()));
        sb.append('}');
        return mvc.perform(post("/api/v1/activations/report").header("Authorization", dev.auth()).contentType(MediaType.APPLICATION_JSON).content(sb.toString())).andReturn();
    }

    @Test
    void noLicenceThenSyncNoneThenReportThenUnlimitedWith5000And50AndReplaysPayOnce() throws Exception {
        clock.unfreeze();
        Registered dev = registerApp("tv");
        KeyPair install = pair();
        Acts.Tv tv = tv();
        String lic = licenseId();
        long issued = System.currentTimeMillis() - HOUR;
        // 1. aucune licence : la TV présente une activation que le portefeuille reconnaît (clé de confiance, ce matériel) mais que le serveur ne sait pas enregistrer (identifiant de licence
        //    réservé) : même état qu'au 2026-10-04, ni licence ni poste, le portefeuille dit « licence en attente »
        JsonNode first = ok(sync(dev, install, tv, production(ISSUER, tv, "list", null, issued, null)));
        assertEquals("NONE", first.path("edition").path("ed").asText(), first.toString());
        assertEquals("PENDING", first.path("edition").path("license").asText());
        assertTrue(noticeReasons(first).contains("LICENSE_PENDING"), first.toString());
        assertTrue(noticeReasons(first).contains("REGISTRATION_REVIEW"), "jamais de refus silencieux : " + first);
        assertEquals(0, count("SELECT COUNT(*) FROM lic_license WHERE license_id = ?", lic));
        assertEquals(0, balance(tv.code(), "NDEM"));
        // 2. l'activation vérifiée (clé de confiance, ce matériel, preuve de possession) est notifiée
        String token = production(ISSUER, tv, lic, null, issued, null);
        MvcResult r = report(dev, tv, install, tv.code(), token);
        assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        JsonNode reg = body(r).path("registration").get(0);
        assertEquals("REGISTERED", reg.path("status").asText(), body(r).toString());
        assertEquals(1, count("SELECT COUNT(*) FROM lic_license WHERE license_id = ? AND end_at IS NULL AND created_by LIKE 'report:%'", lic));
        assertEquals(1, count("SELECT COUNT(*) FROM lic_seat s JOIN lic_license l ON l.id = s.license_pk WHERE l.license_id = ? AND s.device_code = ? AND s.state = 'ACTIVE'", lic, tv.code()));
        // 3. la synchronisation suivante : illimitée, 5 000 + 50
        JsonNode next = ok(sync(dev, install, tv, token));
        assertEquals("UNLIMITED", next.path("edition").path("ed").asText(), next.toString());
        assertEquals(5000, balance(tv.code(), "NDEM"));
        assertEquals(50, balance(tv.code(), "MBOKO"));
        // 4. rejeux : ni une seconde licence, ni un second poste, ni un second versement
        ok(sync(dev, install, tv, token));
        ok(sync(dev, install, tv, token));
        assertEquals(1, count("SELECT COUNT(*) FROM lic_license WHERE license_id = ?", lic));
        assertEquals(1, count("SELECT COUNT(*) FROM lic_seat s JOIN lic_license l ON l.id = s.license_pk WHERE l.license_id = ?", lic));
        assertEquals(1, count("SELECT COUNT(*) FROM lic_issuance i JOIN lic_license l ON l.id = i.license_pk WHERE l.license_id = ?", lic));
        assertEquals(5000, balance(tv.code(), "NDEM"));
        assertEquals(50, balance(tv.code(), "MBOKO"));
    }

    @Test
    void aReportWithoutTheProofOfPossessionOrWithAnotherCodeRegistersNothingAndSaysWhy() throws Exception {
        clock.unfreeze();
        Acts.Tv tv = tv();
        String lic = licenseId();
        String token = production(ISSUER, tv, lic, null, System.currentTimeMillis() - HOUR, null);
        Registered dev = registerApp("tv");
        MvcResult noBind = report(dev, tv, null, null, token);
        assertEquals(200, noBind.getResponse().getStatus(), noBind.getResponse().getContentAsString());
        assertEquals("BIND_PROOF", body(noBind).path("registration").get(0).path("reason").asText(), body(noBind).toString());
        Registered dev2 = registerApp("tv");
        MvcResult wrong = report(dev2, tv, pair(), Acts.Tv.random().code(), token);
        assertEquals("BIND_PROOF", body(wrong).path("registration").get(0).path("reason").asText(), body(wrong).toString());
        assertEquals(0, count("SELECT COUNT(*) FROM lic_license WHERE license_id = ?", lic));
    }

    @Test
    void theOwnerListsAndDecidesPendingRegistrationsThroughTheAdminApi() throws Exception {
        clock.unfreeze();
        Registered dev = registerApp("tv");
        KeyPair install = pair();
        Acts.Tv tv = tv();
        String lic = licenseId();
        String token = production(ISSUER, tv, lic, null, System.currentTimeMillis() - 5 * DAY, null);   // hors fenêtre : attend le propriétaire
        JsonNode s = ok(sync(dev, install, tv, token));
        assertTrue(noticeReasons(s).contains("REGISTRATION_REVIEW"), s.toString());
        String fp = Hashing.sha256Hex(token);
        mvc.perform(get("/api/v1/admin/licenses/registrations")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        JsonNode list = body(mvc.perform(get("/api/v1/admin/licenses/registrations").header("Authorization", ADMIN)).andReturn());
        boolean found = false;
        for (JsonNode p : list.path("items")) {
            if (p.path("fp").asText().equals(fp)) {
                found = true;
                assertEquals("INSTALL_TIME_UNKNOWN", p.path("reason").asText());
                assertTrue(p.path("deviceCode").asText().endsWith("-****"));
            }
        }
        assertTrue(found, list.toString());
        // motif obligatoire
        assertEquals(400, mvc.perform(post("/api/v1/admin/licenses/registrations/" + fp + "/decision").header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON)
                .content("{\"accept\":true,\"reason\":\"\"}")).andReturn().getResponse().getStatus());
        assertEquals(400, mvc.perform(post("/api/v1/admin/licenses/registrations/zz/decision").header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON)
                .content("{\"accept\":true,\"reason\":\"x\"}")).andReturn().getResponse().getStatus());
        MvcResult d = mvc.perform(post("/api/v1/admin/licenses/registrations/" + fp + "/decision").header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON)
                .content("{\"accept\":true,\"reason\":\"TV vue chez le client\"}")).andReturn();
        assertEquals(200, d.getResponse().getStatus(), d.getResponse().getContentAsString());
        assertEquals("REGISTERED", body(d).path("status").asText(), body(d).toString());
        JsonNode after = ok(sync(dev, install, tv, token));
        assertEquals("UNLIMITED", after.path("edition").path("ed").asText(), after.toString());
        // une décision ne se rejoue pas
        assertEquals(409, mvc.perform(post("/api/v1/admin/licenses/registrations/" + fp + "/decision").header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON)
                .content("{\"accept\":true,\"reason\":\"encore\"}")).andReturn().getResponse().getStatus());
    }

    @Test
    void noTokenAndNoFullDeviceCodeInTheLogs(CapturedOutput out) throws Exception {
        clock.unfreeze();
        Registered dev = registerApp("tv");
        KeyPair install = pair();
        Acts.Tv tv = tv();
        String lic = licenseId();
        long issued = System.currentTimeMillis() - HOUR;
        String token = production(ISSUER, tv, lic, null, issued, 90);
        ok(sync(dev, install, tv, token));
        ok(sync(dev, install, tv, production(ISSUER, tv, "list", null, issued, null)));
        ok(sync(dev, install, tv, production(ISSUER, tv, licenseId(), null, issued - 5 * DAY, null)));
        // une alerte et un refus ont eu lieu : leurs lignes de journal ne portent ni jeton ni code d'appareil entier
        String all = out.getAll();
        assertFalse(all.contains(token), "le jeton complet n'est jamais journalisé");
        assertFalse(all.contains(token.split("\\.")[1]), "ni sa charge utile");
        assertFalse(all.contains(tv.code()), "ni le code d'appareil entier");
        assertFalse(all.contains(rawPublic(install)), "ni la clé d'installation");
    }
}
