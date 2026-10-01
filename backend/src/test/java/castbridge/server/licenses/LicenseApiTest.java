package castbridge.server.licenses;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** /api/v1/admin/licenses/** (bearer token) and the two public routes. */
class LicenseApiTest extends LicenseTestBase {
    @org.springframework.beans.factory.annotation.Autowired LicenseKeyring keyring;

    private JsonNode api(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b, int status) throws Exception {
        return body(mvc.perform(b.header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON)).andExpect(status().is(status)).andReturn());
    }

    private String json(String s) { return s; }

    @Test
    void tokenIsRequiredAndErrorsAreClear() throws Exception {
        mvc.perform(get("/api/v1/admin/licenses")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/licenses").header("Authorization", "Bearer nope")).andExpect(status().isUnauthorized());
        api(post("/api/v1/admin/licenses").content("{}"), 400);
        api(post("/api/v1/admin/licenses").content("{\"clientId\":1,\"seats\":0}"), 400); // invalid seat count
        JsonNode e = api(post("/api/v1/admin/licenses").content("{\"clientId\":99999,\"seats\":1}"), 404);
        assertThat(e.get("message").asText()).isNotBlank();
        api(post("/api/v1/admin/licenses").content("pas du json"), 400);
        api(get("/api/v1/admin/licenses/LIC-INCONNU-0000"), 404);
        api(get("/api/v1/admin/licenses/pas%20valide"), 400);
        api(get("/api/v1/admin/licenses?state=BIZARRE"), 400);
        api(get("/api/v1/admin/licenses?size=abc"), 400);
    }

    @Test
    void wholeLifecycleThroughTheApiWithPaginationAndFilters() throws Exception {
        JsonNode client = api(post("/api/v1/admin/licenses/clients").content("{\"name\":\"Client API\",\"contact\":\"api@example.invalid\"}"), 201);
        long clientId = client.get("id").asLong();
        api(post("/api/v1/admin/licenses/products").content("{\"productId\":\"api-pack\",\"title\":\"Pack API\",\"kind\":\"A_LA_CARTE\",\"lots\":[\"quiz/api\"]}"), 201);
        api(post("/api/v1/admin/licenses/products").content("{\"productId\":\"api-pack\",\"title\":\"Pack API\",\"kind\":\"A_LA_CARTE\"}"), 409);
        assertThat(api(get("/api/v1/admin/licenses/products"), 200).toString()).contains("api-pack").contains("quiz/api");

        String ids = "";
        for (int i = 0; i < 3; i++) {
            JsonNode l = api(post("/api/v1/admin/licenses").content("{\"clientId\":" + clientId + ",\"seats\":2,\"endAt\":\"2099-12-31\",\"productIds\":[\"api-pack\"]}"), 201);
            assertThat(l.get("licenseId").asText()).startsWith("LIC-");
            ids += l.get("licenseId").asText() + " ";
        }
        String first = ids.split(" ")[0];
        JsonNode page = api(get("/api/v1/admin/licenses?client=" + clientId + "&size=2&page=0&sort=id&dir=asc"), 200);
        assertThat(page.get("total").asInt()).isEqualTo(3);
        assertThat(page.get("items")).hasSize(2);
        assertThat(api(get("/api/v1/admin/licenses?client=" + clientId + "&size=2&page=1"), 200).get("items")).hasSize(1);
        assertThat(api(get("/api/v1/admin/licenses?client=" + clientId + "&product=api-pack&state=ACTIVE"), 200).get("total").asInt()).isEqualTo(3);
        assertThat(api(get("/api/v1/admin/licenses?client=" + clientId + "&product=autre"), 200).get("total").asInt()).isZero();
        assertThat(api(get("/api/v1/admin/licenses").param("q", "Client API"), 200).get("total").asInt()).isGreaterThanOrEqualTo(3);
        assertThat(api(get("/api/v1/admin/licenses?size=1000&client=" + clientId), 200).get("size").asInt()).isEqualTo(100); // page size is capped

        String dev = code();
        JsonNode act = api(post("/api/v1/admin/licenses/" + first + "/activations").content("{\"deviceCode\":\"" + dev + "\"}"), 200);
        assertThat(act.get("reused").asBoolean()).isFalse();
        JsonNode again = api(post("/api/v1/admin/licenses/" + first + "/reissue").content("{\"deviceCode\":\"" + dev.toLowerCase() + "\"}"), 200);
        assertThat(again.get("text").asText()).isEqualTo(act.get("text").asText());
        assertThat(api(get("/api/v1/admin/licenses/devices/" + dev), 200).get(0).get("licenseId").asText()).isEqualTo(first);
        JsonNode detail = api(get("/api/v1/admin/licenses/" + first), 200);
        assertThat(detail.get("seats")).hasSize(1);
        assertThat(detail.get("issuances")).hasSize(1);
        assertThat(detail.toString()).doesNotContain(act.get("text").asText()); // the activation itself is not kept

        // destructive routes demand a reason
        api(post("/api/v1/admin/licenses/" + first + "/revoke").content("{}"), 400);
        api(post("/api/v1/admin/licenses/" + first + "/seats/release").content("{\"deviceCode\":\"" + dev + "\"}"), 400);
        api(post("/api/v1/admin/licenses/" + first + "/seats/release").content("{\"deviceCode\":\"" + dev + "\",\"reason\":\"poste remplacé\"}"), 200);
        api(post("/api/v1/admin/licenses/" + first + "/seats").content("{\"seats\":4}"), 200);
        api(post("/api/v1/admin/licenses/" + first + "/extend").content("{\"endAt\":\"2100-06-30\"}"), 200);
        api(post("/api/v1/admin/licenses/" + first + "/settings").content("{\"graceDays\":30,\"transferCap\":3}"), 200);
        api(post("/api/v1/admin/licenses/" + first + "/suspend").content("{\"reason\":\"vérification\"}"), 200);
        api(post("/api/v1/admin/licenses/" + first + "/resume").content("{\"reason\":\"vérifié\"}"), 200);
        assertThat(api(post("/api/v1/admin/licenses/" + first + "/revoke").content("{\"reason\":\"fin de contrat\"}"), 200).get("state").asText()).isEqualTo("REVOKED");
        api(post("/api/v1/admin/licenses/" + first + "/revoke").content("{\"reason\":\"encore\"}"), 409);

        // client rights through the API
        assertThat(api(get("/api/v1/admin/licenses/clients/" + clientId + "/export"), 200).toString()).contains("Client API");
        api(post("/api/v1/admin/licenses/clients/" + clientId + "/erase").content("{\"reason\":\"demande du client\"}"), 200);
        assertThat(api(get("/api/v1/admin/licenses/clients/" + clientId), 200).get("name").asText()).startsWith("Client effacé");

        // dashboard, audit, verification
        assertThat(api(get("/api/v1/admin/licenses/dashboard"), 200).has("activeLicenses")).isTrue();
        assertThat(api(get("/api/v1/admin/licenses/audit?targetId=" + first + "&size=5"), 200).get("total").asInt()).isGreaterThanOrEqualTo(5);
        assertThat(api(get("/api/v1/admin/licenses/audit/verify"), 200).get("ok").asBoolean()).isTrue();
        JsonNode signing = api(get("/api/v1/admin/licenses/signing"), 200);
        assertThat(signing.get("keyLoaded").asBoolean()).isTrue();
        assertThat(signing.get("scopes").toString()).doesNotContain("TRANSFER").doesNotContain("OPEN_ALL");
        assertThat(signing.get("trialIssuance").asText()).isEqualTo("manual");
    }

    @Test
    void csvAndLedgerThroughTheApi() throws Exception {
        var l = license(2);
        mvc.perform(get("/api/v1/admin/licenses?format=csv").header("Authorization", ADMIN)).andExpect(status().isOk()).andExpect(header().string("Content-Type", containsString("text/csv")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(containsString(l.licenseId())));
        byte[] file = ledgerFile("desktop", DESKTOP, "desktop", List.of(issuanceEntry(l.licenseId(), code(), "PURCHASE", hex(70), fp("api"), Instant.now().minusSeconds(60))));
        JsonNode dry = body(mvc.perform(post("/api/v1/admin/licenses/ledger/import?dryRun=true").header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON).content(file))
                .andExpect(status().isOk()).andReturn());
        assertThat(dry.get("dryRun").asBoolean()).isTrue();
        assertThat(dry.get("applied").asInt()).isEqualTo(1);
        JsonNode real = body(mvc.perform(post("/api/v1/admin/licenses/ledger/import").header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON).content(file)).andExpect(status().isOk()).andReturn());
        assertThat(real.get("importId").asLong()).isPositive();
        mvc.perform(post("/api/v1/admin/licenses/ledger/import").header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON).content(file)).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/admin/licenses/ledger/import").header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON).content("{\"v\":1}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/licenses/ledger/export").header("Authorization", ADMIN)).andExpect(status().isOk()).andExpect(header().string("Content-Disposition", containsString("registre-")));
        api(get("/api/v1/admin/licenses/ledger/conflicts"), 200);
        api(get("/api/v1/admin/licenses/ledger/imports"), 200);
        api(post("/api/v1/admin/licenses/ledger/conflicts/999999/decision").content("{\"accept\":true,\"reason\":\"test\"}"), 404);
        api(post("/api/v1/admin/licenses/ledger/conflicts/1/decision").content("{\"reason\":\"test\"}"), 400);
    }

    // ------------------------------------------------------------------ public routes

    private String registerDevice() throws Exception {
        String body = "{\"installId\":\"" + UUID.randomUUID() + "\",\"app\":\"tv\",\"versionCode\":7,\"versionName\":\"0.7\",\"channel\":\"stable\",\"abi\":\"armeabi-v7a\",\"sdk\":34,\"platform\":\"android-tv\"}";
        return body(mvc.perform(post("/api/v1/devices/register").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andReturn()).get("deviceToken").asText();
    }

    @Test
    void signedRevocationListAndEntitlementForADevice() throws Exception {
        var l = license(1);
        String dev = code();
        issue(l.licenseId(), dev);
        String token = registerDevice();

        // entitlement: needs the device token, answers only about the code asked
        mvc.perform(get("/api/v1/entitlements/me").param("deviceCode", dev)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/entitlements/me").param("deviceCode", dev).header("Authorization", "Bearer " + token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.entitled").value(true)).andExpect(jsonPath("$.licenseId").value(l.licenseId())).andExpect(jsonPath("$.state").value("ACTIVE"))
                .andExpect(jsonPath("$.trialIssuance").value("manual")).andExpect(header().string("Cache-Control", containsString("no-store")));
        mvc.perform(get("/api/v1/entitlements/me").param("deviceCode", code()).header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andExpect(jsonPath("$.entitled").value(false))
                .andExpect(jsonPath("$.licenseId").doesNotExist());
        mvc.perform(get("/api/v1/entitlements/me").param("deviceCode", "bad").header("Authorization", "Bearer " + token)).andExpect(status().isBadRequest());
        // sightings are recorded (hashed) for the abuse alerts
        assertThat(jdbc.queryForObject("select count(*) from lic_sighting where device_code = ?", Integer.class, dev)).isGreaterThanOrEqualTo(2);
        assertThat(jdbc.queryForList("select source_ref from lic_sighting where device_code = ?", String.class, dev)).allSatisfy(s -> assertThat(s).hasSize(16).doesNotContain("."));

        // revocation list: public, signed with the server key, lists the revoked licence
        licenses.revoke(OWNER, l.licenseId(), "révocation de test");
        JsonNode env = body(mvc.perform(get("/api/v1/revocations")).andExpect(status().isOk()).andExpect(header().string("Cache-Control", containsString("no-store"))).andReturn());
        byte[] payload = Base64.getDecoder().decode(env.get("payload").asText());
        assertThat(LicenseKeyring.verify(Base64.getDecoder().decode(keyring.publicKeyBase64()), payload, Base64.getDecoder().decode(env.get("sig").asText()))).isTrue();
        assertThat(env.get("kid").asText()).isEqualTo(keyring.kid());
        JsonNode p = this.json.readTree(payload);
        assertThat(p.get("revoked").toString()).contains(l.licenseId());
        assertThat(p.get("seq").asLong()).isPositive();
        // the revoked licence no longer entitles the device
        mvc.perform(get("/api/v1/entitlements/me").param("deviceCode", dev).header("Authorization", "Bearer " + token)).andExpect(jsonPath("$.entitled").value(false));
        // a forged payload does not verify
        payload[payload.length - 3] ^= 1;
        assertThat(LicenseKeyring.verify(Base64.getDecoder().decode(keyring.publicKeyBase64()), payload, Base64.getDecoder().decode(env.get("sig").asText()))).isFalse();
    }
}
