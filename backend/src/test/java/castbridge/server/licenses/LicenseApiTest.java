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
        api(get("/api/v1/admin/licenses/lic-inconnu-0000"), 404);
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
            assertThat(l.get("licenseId").asText()).startsWith("lic-");
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

        Dev dv = dev();
        JsonNode act = api(post("/api/v1/admin/licenses/" + first + "/activations").content(json.createObjectNode().put("deviceRequest", dv.text()).toString()), 200);
        assertThat(act.get("reused").asBoolean()).isFalse();
        assertThat(act.get("text").asText()).startsWith("cbx1.");
        String seat = act.get("seatId").asText();
        JsonNode again = api(post("/api/v1/admin/licenses/" + first + "/reissue").content(json.createObjectNode().put("seatId", seat).toString()), 200);
        assertThat(again.get("text").asText()).isEqualTo(act.get("text").asText());
        JsonNode viaRequest = api(post("/api/v1/admin/licenses/" + first + "/reissue").content(json.createObjectNode().put("deviceRequest", dv.text()).toString()), 200);
        assertThat(viaRequest.get("text").asText()).isEqualTo(act.get("text").asText());
        // a code alone is refused with a clear message (the request must carry the factors)
        assertThat(api(post("/api/v1/admin/licenses/" + first + "/activations").content(json.createObjectNode().put("deviceRequest", "code=" + dv.code()).toString()), 400).get("message").asText()).contains("sans facteur");
        assertThat(api(get("/api/v1/admin/licenses/devices/" + dv.code()), 200).get(0).get("licenseId").asText()).isEqualTo(first);
        JsonNode detail = api(get("/api/v1/admin/licenses/" + first), 200);
        assertThat(detail.get("seats")).hasSize(1);
        assertThat(detail.get("issuances")).hasSize(1);
        assertThat(detail.toString()).doesNotContain(act.get("text").asText()); // the activation itself is not kept

        // destructive routes demand a reason
        api(post("/api/v1/admin/licenses/" + first + "/revoke").content("{}"), 400);
        api(post("/api/v1/admin/licenses/" + first + "/seats/release").content("{\"seatId\":\"" + seat + "\"}"), 400);
        api(post("/api/v1/admin/licenses/" + first + "/seats/release").content("{\"seatId\":\"" + seat + "\",\"reason\":\"poste remplacé\"}"), 200);
        api(post("/api/v1/admin/licenses/" + first + "/seats").content("{\"seats\":4}"), 409); // closed quota: no increase
        api(post("/api/v1/admin/licenses/" + first + "/seats").content("{\"seats\":1,\"reason\":\"réduction\"}"), 200); // a decrease is allowed
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
        assertThat(signing.get("scopes").toString()).doesNotContain("TRANSFER").doesNotContain("COMMAND_OPEN_ALL");
        assertThat(signing.get("trialIssuance").asText()).isEqualTo("manual");
    }

    @Test
    void csvAndLedgerThroughTheApi() throws Exception {
        var l = license(2);
        mvc.perform(get("/api/v1/admin/licenses?format=csv").header("Authorization", ADMIN)).andExpect(status().isOk()).andExpect(header().string("Content-Type", containsString("text/csv")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(containsString(l.licenseId())));
        Dev d = dev();
        String wire = "lic-" + Long.toString(RND.nextLong() & 0xffffffL, 36);
        long at = Instant.now().minusSeconds(3600).toEpochMilli();
        byte[] file = registryFile(List.of(licenseEvent(DESKTOP, at, wire, 2, 2), issueEvent(DESKTOP, at + 1000, wire, seatOf(wire, d), "tv", "production", d, nonce())));
        JsonNode dry = body(mvc.perform(post("/api/v1/admin/licenses/ledger/import?dryRun=true").header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON).content(file))
                .andExpect(status().isOk()).andReturn());
        assertThat(dry.get("dryRun").asBoolean()).isTrue();
        assertThat(dry.get("applied").asInt()).isEqualTo(2);
        JsonNode real = body(mvc.perform(post("/api/v1/admin/licenses/ledger/import?policy=auto").header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON).content(file)).andExpect(status().isOk()).andReturn());
        assertThat(real.get("importId").asLong()).isPositive();
        assertThat(real.get("policy").asText()).isEqualTo("auto");
        // the same file again: an idempotent union, nothing applied, no error
        JsonNode again = body(mvc.perform(post("/api/v1/admin/licenses/ledger/import").header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON).content(file)).andExpect(status().isOk()).andReturn());
        assertThat(again.get("applied").asInt()).isZero();
        assertThat(again.get("duplicates").asInt()).isEqualTo(2);
        mvc.perform(post("/api/v1/admin/licenses/ledger/import?policy=bizarre").header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON).content(file)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/admin/licenses/ledger/import").header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON).content("{\"v\":1}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/licenses/ledger/export").header("Authorization", ADMIN)).andExpect(status().isOk()).andExpect(header().string("Content-Disposition", containsString("registre-")));
        api(get("/api/v1/admin/licenses/ledger/conflicts"), 200);
        api(get("/api/v1/admin/licenses/ledger/imports"), 200);
        api(post("/api/v1/admin/licenses/ledger/conflicts/999999/decision").content("{\"accept\":true,\"reason\":\"test\"}"), 404);
        api(post("/api/v1/admin/licenses/ledger/conflicts/1/decision").content("{\"reason\":\"test\"}"), 400);
        // a signing key can be revoked: it enters the signed revocation list
        api(post("/api/v1/admin/licenses/keys/revoke").content("{\"kid\":\"" + kid(STRANGER) + "\"}"), 400);
        api(post("/api/v1/admin/licenses/keys/revoke").content("{\"kid\":\"" + kid(STRANGER) + "\",\"reason\":\"clé perdue\"}"), 200);
        api(post("/api/v1/admin/licenses/keys/revoke").content("{\"kid\":\"" + kid(STRANGER) + "\",\"reason\":\"encore\"}"), 409);
        api(post("/api/v1/admin/licenses/keys/revoke").content("{\"kid\":\"zz\",\"reason\":\"mauvais kid\"}"), 400);
    }

    // ------------------------------------------------------------------ public routes

    private String registerDevice() throws Exception {
        String body = "{\"installId\":\"" + UUID.randomUUID() + "\",\"app\":\"tv\",\"versionCode\":7,\"versionName\":\"0.7\",\"channel\":\"stable\",\"abi\":\"armeabi-v7a\",\"sdk\":34,\"platform\":\"android-tv\"}";
        return body(mvc.perform(post("/api/v1/devices/register").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andReturn()).get("deviceToken").asText();
    }

    @Test
    void signedRevocationListAndEntitlementForADevice() throws Exception {
        var l = license(1);
        Dev d = dev();
        var act = issue(l.licenseId(), d);
        String token = registerDevice();

        // entitlement: needs the device token, answers only about the code asked
        mvc.perform(get("/api/v1/entitlements/me").param("deviceCode", d.code())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/entitlements/me").param("deviceCode", d.code()).header("Authorization", "Bearer " + token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.entitled").value(true)).andExpect(jsonPath("$.licenseId").value(l.licenseId())).andExpect(jsonPath("$.state").value("ACTIVE"))
                .andExpect(jsonPath("$.trialIssuance").value("manual")).andExpect(header().string("Cache-Control", containsString("no-store")));
        // the same hardware with a replaced module has another device code but its latest activation is the one on the seat: the old code still answers
        mvc.perform(get("/api/v1/entitlements/me").param("deviceCode", dev().code()).header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andExpect(jsonPath("$.entitled").value(false))
                .andExpect(jsonPath("$.licenseId").doesNotExist());
        mvc.perform(get("/api/v1/entitlements/me").param("deviceCode", "bad").header("Authorization", "Bearer " + token)).andExpect(status().isBadRequest());
        // sightings are recorded (hashed) for the abuse alerts
        assertThat(jdbc.queryForObject("select count(*) from lic_sighting where device_code = ?", Integer.class, d.code())).isGreaterThanOrEqualTo(2);
        assertThat(jdbc.queryForList("select source_ref from lic_sighting where device_code = ?", String.class, d.code())).allSatisfy(s -> assertThat(s).hasSize(16).doesNotContain("."));

        // revocation list: public, signed with the server key (REVOKE scope), an envelope cbx1 of type revocation (docs/ACTIVATION-FORMAT.md § 7)
        licenses.releaseSeat(OWNER, l.licenseId(), act.seatId(), "poste libéré pour le test");
        String lostKid = kid(key());
        licenses.revokeKey(OWNER, lostKid, "clé perdue (test)");
        String list = mvc.perform(get("/api/v1/revocations")).andExpect(status().isOk()).andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Content-Type", containsString("text/plain"))).andReturn().getResponse().getContentAsString();
        assertThat(list).startsWith("cbx1.").doesNotContain("\n");
        String[] parts = list.split("\\.");
        assertThat(parts).hasSize(3);
        byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
        byte[] sig = Base64.getDecoder().decode(parts[2]);
        assertThat(LicenseKeyring.verify(Base64.getDecoder().decode(keyring.publicKeyBase64()), payload, sig)).isTrue();
        Envelope env = Envelope.decode(list);
        assertThat(env).isNotNull();
        assertThat(env.type()).isEqualTo("revocation");
        assertThat(env.kid()).isEqualTo(keyring.kid());
        assertThat(env.target()).isEqualTo(Envelope.Target.ANY);
        assertThat(env.body()).contains("key=" + lostKid).anyMatch(x -> x.startsWith("seat=" + l.licenseId() + "|" + act.seatId() + "|"));
        // keys sorted, then seats sorted (canonical)
        assertThat(env.body()).isSorted().allMatch(x -> x.startsWith("key=") || x.startsWith("seat="));
        // a device that trusts the server key (REVOKE) reads it back with the verifier of the format
        var ring = new EnvelopeVerifier.Ring().add(new EnvelopeVerifier.TrustedKey(keyring.kid(), Base64.getDecoder().decode(keyring.publicKeyBase64()), ScopedActivationSigner.SERVER_SCOPES));
        var rev = new EnvelopeVerifier(ring, EnvelopeVerifier.Revocations.none(), new EnvelopeVerifier.SeqState(), "tv").verifyRevocation(list);
        assertThat(rev).isNotNull();
        assertThat(rev.keys()).contains(lostKid);
        assertThat(rev.seats()).containsKey(l.licenseId() + "|" + act.seatId());
        // the released seat no longer entitles the device
        mvc.perform(get("/api/v1/entitlements/me").param("deviceCode", d.code()).header("Authorization", "Bearer " + token)).andExpect(jsonPath("$.entitled").value(false));
        // a forged payload does not verify
        payload[payload.length - 3] ^= 1;
        assertThat(LicenseKeyring.verify(Base64.getDecoder().decode(keyring.publicKeyBase64()), payload, sig)).isFalse();
    }
}
