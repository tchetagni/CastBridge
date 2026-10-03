package castbridge.server.play;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import castbridge.server.ApiTestBase;
import castbridge.server.devices.DeviceService;
import castbridge.server.licenses.DeviceIdentity;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** {@code POST /api/v1/play/ticket}: 401 without Bearer, 403 blocked device, 200 with a verifiable Ed25519 signature (public key only), 429 at the 21st, never an edition or a right. */
class PlayTicketControllerTest extends ApiTestBase {
    static final KeyPair PAIR = newPair();
    static final Path KEY_FILE = writeKey(PAIR);
    private static final String DOMAIN = "castbridge-play-ticket-v1\n";

    @Autowired DeviceService devices;

    static KeyPair newPair() {
        try { return KeyPairGenerator.getInstance("Ed25519").generateKeyPair(); } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    static Path writeKey(KeyPair p) {
        try {
            Path f = Files.createTempFile("play-ticket", ".key");
            String pem = "-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(p.getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----\n";
            Files.writeString(f, pem);
            return f;
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    @DynamicPropertySource
    static void ticketKey(DynamicPropertyRegistry r) { r.add("castbridge.play.ticket-key-file", KEY_FILE::toString); r.add("castbridge.play.per-address-per-hour", () -> "100000"); }

    /** A valid device code: any set of factors gives one. */
    static String code() {
        return DeviceIdentity.code(Map.of(DeviceIdentity.Factor.FLASH, UUID.randomUUID().toString().replace("-", ""), DeviceIdentity.Factor.ETHERNET, "b".repeat(32)));
    }

    private JsonNode register() throws Exception {
        String id = UUID.randomUUID().toString();
        String report = """
                {"installId":"%s","androidIdHash":"%s","app":"tv","versionCode":7,"versionName":"0.7","channel":"stable","abi":"armeabi-v7a","supportedAbis":["armeabi-v7a"],
                 "sdk":34,"platform":"android-tv","manufacturer":"Hisense","model":"43A4"}""".formatted(id, ("%064x".formatted(id.hashCode() & 0xffffffffL)));
        return body(mvc.perform(post("/api/v1/devices/register").contentType(MediaType.APPLICATION_JSON).content(report).header("X-Test-Country", "cm"))
                .andExpect(status().isCreated()).andReturn());
    }

    private JsonNode ticket(String token, String body, int expected) throws Exception {
        var req = post("/api/v1/play/ticket").contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) req = req.header("Authorization", "Bearer " + token);
        return body(mvc.perform(req).andExpect(status().is(expected)).andReturn());
    }

    private static String bodyOf(String code) { return "{\"deviceCode\":\"" + code + "\"}"; }

    @Test
    void withoutOrWithAnUnknownBearerTheAnswerIs401() throws Exception {
        ticket(null, bodyOf(code()), 401);
        ticket("pas-un-jeton-d-appareil", bodyOf(code()), 401);
    }

    @Test
    void aMissingOrMalformedDeviceCodeIs400() throws Exception {
        String token = register().get("deviceToken").asText();
        ticket(token, "{}", 400);
        String good = code();
        ticket(token, bodyOf(good.substring(0, good.length() - 1) + (good.endsWith("0") ? "1" : "0")), 400);   // wrong check character
        ticket(token, bodyOf(good.substring(0, 10)), 400);                                                      // too short
        ticket(token, bodyOf("0000-0000-0000-000U"), 400);                                                      // a letter outside the alphabet
        mvc.perform(post("/api/v1/play/ticket").header("Authorization", "Bearer " + token)).andExpect(status().isBadRequest());
    }

    @Test
    void aBlockedDeviceGets403() throws Exception {
        JsonNode reg = register();
        String c = code();
        ticket(reg.get("deviceToken").asText(), bodyOf(c), 200);
        devices.setBlocked(reg.get("deviceId").asText(), true);
        ticket(reg.get("deviceToken").asText(), bodyOf(c), 403);
    }

    @Test
    void theTicketIsSignedByTheDedicatedKeyAndCarriesOnlyTheAttestation() throws Exception {
        JsonNode reg = register();
        long before = System.currentTimeMillis();
        String mine = code();
        String t = ticket(reg.get("deviceToken").asText(), bodyOf(mine.toLowerCase().replace("-", " ")), 200).get("ticket").asText();
        String[] parts = t.split("\\.");
        assertEquals(3, parts.length);
        assertEquals("cbp1", parts[0]);

        Signature s = Signature.getInstance("Ed25519");
        s.initVerify(PAIR.getPublic());
        s.update((DOMAIN + parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertTrue(s.verify(Base64.getUrlDecoder().decode(parts[2])), "signature valid with the PUBLIC key only");

        JsonNode p = json.readTree(Base64.getUrlDecoder().decode(parts[1]));
        assertEquals("castbridge-play", p.get("aud").asText());
        assertEquals(reg.get("deviceId").asText(), p.get("deviceId").asText());
        assertFalse(p.get("blocked").asBoolean());
        assertEquals("CM", p.get("country").asText());
        assertEquals(mine, p.get("deviceCode").asText(), "canonical code");
        assertEquals(600_000L, p.get("exp").asLong() - p.get("iat").asLong());
        assertTrue(p.get("iat").asLong() >= before && p.get("iat").asLong() <= System.currentTimeMillis());
        assertTrue(p.get("jti").asText().matches("[0-9a-f]{32}"), "128-bit random jti");
        assertEquals(8, p.size(), "only: aud, deviceId, blocked, country, deviceCode, iat, exp, jti");
        for (String forbidden : new String[] {"edition", "rights", "scopes", "plan"}) assertFalse(p.has(forbidden), forbidden);
    }

    @Test
    void everyTicketHasItsOwnJtiAndAnAlteredPayloadFailsTheSignature() throws Exception {
        String token = register().get("deviceToken").asText();
        String same = code();
        String a = ticket(token, bodyOf(same), 200).get("ticket").asText();
        String b = ticket(token, bodyOf(same), 200).get("ticket").asText();
        assertNotEquals(a, b);
        String[] parts = a.split("\\.");
        String forged = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8).replace("\"blocked\":false", "\"blocked\":true");
        Signature s = Signature.getInstance("Ed25519");
        s.initVerify(PAIR.getPublic());
        s.update((DOMAIN + "cbp1." + Base64.getUrlEncoder().withoutPadding().encodeToString(forged.getBytes(StandardCharsets.UTF_8))).getBytes(StandardCharsets.US_ASCII));
        assertFalse(s.verify(Base64.getUrlDecoder().decode(parts[2])));
    }

    @Test
    void theTwentyFirstTicketOfTheHourIs429ButAnotherDeviceIsNotAffected() throws Exception {
        String token = register().get("deviceToken").asText();
        String same = code();
        for (int i = 0; i < 20; i++) ticket(token, bodyOf(same), 200);
        ticket(token, bodyOf(same), 429);
        ticket(register().get("deviceToken").asText(), bodyOf(code()), 200);
    }

    @Test
    void aCopiedActivationCodeSpreadsToAtMostTwoDevicesAndADeviceKeepsItsFirstCode() throws Exception {
        String shared = code();
        String t1 = register().get("deviceToken").asText(), t2 = register().get("deviceToken").asText(), t3 = register().get("deviceToken").asText();
        ticket(t1, bodyOf(shared), 200);
        ticket(t2, bodyOf(shared), 200);
        ticket(t3, bodyOf(shared), 403);                 // a third device with the same code: refused
        ticket(t1, bodyOf(shared), 200);                 // the known ones keep working
        ticket(t1, bodyOf(code()), 403);                 // a device never changes its code
    }

    @Test
    void onlyATvCanGetATicket() throws Exception {
        String id = UUID.randomUUID().toString();
        String report = """
                {"installId":"%s","androidIdHash":"%s","app":"phone","versionCode":7,"versionName":"0.7","channel":"stable","abi":"arm64-v8a","supportedAbis":["arm64-v8a"],
                 "sdk":34,"platform":"android","manufacturer":"X","model":"Y"}""".formatted(id, ("%064x".formatted(id.hashCode() & 0xffffffffL)));
        String token = body(mvc.perform(post("/api/v1/devices/register").contentType(MediaType.APPLICATION_JSON).content(report)).andExpect(status().isCreated()).andReturn()).get("deviceToken").asText();
        ticket(token, bodyOf(code()), 403);
    }
}
