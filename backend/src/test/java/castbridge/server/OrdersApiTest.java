package castbridge.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Deferred orders on the server (docs/ORDRES.md): creation with the closed list, signature, who sees what, acknowledgements, priority, expiry, audit chain. */
class OrdersApiTest extends ApiTestBase {
    @Autowired JdbcTemplate jdbc;

    @DynamicPropertySource
    static void orders(DynamicPropertyRegistry r) throws Exception {
        Path key = Files.createTempFile("cb-orders-key", ".b64");
        byte[] seed = new byte[32]; new java.util.Random(7).nextBytes(seed);              // a test-only key
        Files.writeString(key, Base64.getEncoder().encodeToString(seed));
        r.add("castbridge.orders.key-file", key::toString);
        r.add("castbridge.orders.enabled", () -> "true");
    }

    // device request of the reference test TV "tvA" (tools/activation/test-vectors.json): code, k and the five factors
    static final String TV_A = "ZX8P-S2TH-N5PG-V9V7";
    static final String INFO_A = "code=" + TV_A + "\nk=4\nfactor=FLASH|c48cacd81b9663d0ba97c221cbd17f4f\nfactor=ETHERNET|f91efd89e0025c2262da12cfbed43632\nfactor=WIFI|ea8b552e2240bbdd8c88c42313c4deab\n"
            + "factor=SYSTEM_SERIAL|774beacbf351b0d205f9166669e4d3c7\nfactor=BLUETOOTH|d365ea67e00585edaf8bef4d7378850d";

    String phone(String app) throws Exception {
        String body = """
                {"installId":"%s","app":"%s","versionCode":7,"versionName":"0.7","channel":"stable","abi":"arm64-v8a","supportedAbis":["arm64-v8a"],"sdk":34,"platform":"phone",
                 "manufacturer":"Test","model":"T1","deviceName":"tel","osName":"Android","osBuild":"b","fingerprint":"f","screen":"1080x2400","densityDpi":420,"ramTotalMb":4000,
                 "storageFreeMb":2048,"storageTotalMb":8192}""".formatted(UUID.randomUUID(), app);
        return body(mvc.perform(post("/api/v1/devices/register").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andReturn()).get("deviceToken").asText();
    }

    void pair(String token, String info) throws Exception {
        mvc.perform(post("/api/v1/orders/pair").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(java.util.Map.of("deviceInfo", info)))).andExpect(status().isNoContent());
    }

    JsonNode admin(String path, String body, int expected) throws Exception {
        var r = mvc.perform(post("/api/v1/admin/orders" + path).header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().is(expected)).andReturn();
        return r.getResponse().getContentAsByteArray().length == 0 ? null : body(r);
    }

    long create(String kind, String value, String action, String paramsJson, boolean hold, int priority) throws Exception {
        String v = value == null ? "null" : "\"" + value + "\"";
        return admin("", "{\"targetKind\":\"%s\",\"targetValue\":%s,\"action\":\"%s\",\"params\":%s,\"hold\":%s,\"priority\":%d}".formatted(kind, v, action, paramsJson, hold, priority), 201).get("id").asLong();
    }

    JsonNode fetch(String token, long since) throws Exception {
        return body(mvc.perform(get("/api/v1/orders?since=" + since).header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andReturn());
    }

    static String payloadOf(String token) { return new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8); }

    static String ack(String kid, long seq, String result, String reason) { return "kid=%s\nseq=%d\nnonce=aabbccdd\nresult=%s\nreason=%s\npolicyVersion=3\nat=1800000000000".formatted(kid, seq, result, reason); }

    void postAcks(String token, String tv, String ackText, int accepted) throws Exception {
        String b = json.writeValueAsString(java.util.Map.of("acks", java.util.List.of(java.util.Map.of("tv", tv, "ack", ackText))));
        assertEquals(accepted, body(mvc.perform(post("/api/v1/orders/acks").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(b)).andExpect(status().isOk()).andReturn()).get("accepted").asInt());
    }

    @org.junit.jupiter.api.BeforeEach
    void clean() {
        for (String t : new String[]{"order_delivery", "order_pairing", "order_membership", "order_msg", "order_tv", "order_audit", "order_key_seq"}) jdbc.update("delete from " + t);
    }

    @Test
    void fullCycleCreateSignHandAcknowledge() throws Exception {
        String p = phone("phone"); pair(p, INFO_A);
        long id = create("device", TV_A, "flag.set", "{\"name\":\"learn.beta\",\"value\":\"1\"}", false, 0);
        JsonNode f = fetch(p, 0);
        assertEquals(1, f.get("orders").size());
        String token = f.get("orders").get(0).get("token").asText();
        assertEquals(TV_A, f.get("orders").get(0).get("tv").asText());
        String payload = payloadOf(token);
        assertTrue(token.startsWith("cbx1.") && payload.startsWith("castbridge-envelope-v1\ntype=order\nkid="), payload);
        assertTrue(payload.contains("\ntarget=device\nk=4\nfactor=FLASH|") && payload.endsWith("--\naction=flag.set\nparam=name|learn.beta\nparam=value|1"), payload);
        String kid = payload.split("\n")[2].substring(4); long seq = Long.parseLong(payload.split("\n")[3].substring(4));
        assertEquals(1, seq);
        assertEquals("HANDED", body(mvc.perform(get("/api/v1/admin/orders/" + id).header("Authorization", ADMIN)).andReturn()).get("deliveries").get(0).get("state").asText());

        postAcks(p, TV_A, ack(kid, seq, "applied", "APPLIED"), 1);
        postAcks(p, TV_A, ack(kid, seq, "applied", "APPLIED"), 1);                           // idempotent
        postAcks(p, TV_A, ack(kid, seq, "refused", "BAD_PARAMS"), 1);                        // a contradicting later answer does not change it
        JsonNode d = body(mvc.perform(get("/api/v1/admin/orders/" + id).header("Authorization", ADMIN)).andReturn()).get("deliveries").get(0);
        assertEquals("APPLIED", d.get("state").asText()); assertEquals(3, d.get("policyVersion").asInt());
        assertEquals(0, fetch(p, 0).get("orders").size(), "an acknowledged order is not handed out again");
        assertEquals(-1, body(mvc.perform(get("/api/v1/admin/orders/audit/verify").header("Authorization", ADMIN)).andReturn()).get("firstBrokenId").asLong());
    }

    @Test
    void aPhoneOnlySeesAndAcknowledgesTheTvsPairedWithIt() throws Exception {
        String p1 = phone("phone"), p2 = phone("phone");
        pair(p1, INFO_A);
        long id = create("any", null, "rights.refresh", "{\"reason\":\"periodic\"}", false, 0);
        assertEquals(1, fetch(p1, 0).get("orders").size());
        assertEquals(0, fetch(p2, 0).get("orders").size(), "p2 is paired with nothing");
        String kid = payloadOf(fetch(p1, 0).get("orders").get(0).get("token").asText()).split("\n")[2].substring(4);
        postAcks(p2, TV_A, ack(kid, 1, "applied", "APPLIED"), 0);                          // not paired: refused
        assertNotEquals("APPLIED", body(mvc.perform(get("/api/v1/admin/orders/" + id).header("Authorization", ADMIN)).andReturn()).get("deliveries").get(0).get("state").asText());
        postAcks(p1, TV_A, ack(kid, 999, "applied", "APPLIED"), 0);                        // unknown order
        postAcks(p1, TV_A, "garbage", 0);
    }

    @Test
    void pairingChecksTheCodeAgainstTheFactors() throws Exception {
        String p = phone("phone");
        mvc.perform(post("/api/v1/orders/pair").header("Authorization", "Bearer " + p).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("deviceInfo", INFO_A.replace(TV_A, "ZX8P-S2TH-N5PG-V9V8"))))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/orders/pair").header("Authorization", "Bearer " + p).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("deviceInfo", INFO_A.replace("k=4", "k=2"))))).andExpect(status().isBadRequest());
    }

    @Test
    void theServerSignsNothingOutsideTheClosedList() throws Exception {
        for (String bad : new String[]{"exec", "shell", "library.delete", "wipe", "files.read", "remote.open", "ssh.enable", "update.disable", "pii.read", "factory.reset"})
            admin("", "{\"targetKind\":\"any\",\"action\":\"%s\",\"params\":{\"path\":\"/sdcard\"}}".formatted(bad), 400);
        admin("", "{\"targetKind\":\"any\",\"action\":\"flag.set\",\"params\":{\"name\":\"update.verify\",\"value\":\"0\"}}", 400);
        admin("", "{\"targetKind\":\"any\",\"action\":\"message.show\",\"params\":{\"id\":\"m\",\"text\":\"http://evil.example\"}}", 400);
        admin("", "{\"targetKind\":\"device\",\"targetValue\":\"AAAA-AAAA-AAAA-AAAA\",\"action\":\"rights.refresh\",\"params\":{}}", 400);     // unknown TV
        admin("", "{\"targetKind\":\"nobody\",\"action\":\"rights.refresh\",\"params\":{}}", 400);
        admin("", "{\"targetKind\":\"any\",\"action\":\"rights.refresh\",\"params\":{},\"expiresInHours\":99999}", 400);
    }

    @Test
    void heldOrdersAreSignedByPriorityAndNeverSkippedByTheCursor() throws Exception {
        String p = phone("phone"); pair(p, INFO_A);
        long low = create("any", null, "app.min_version", "{\"version\":\"1\"}", true, 0);
        long high = create("any", null, "app.min_version", "{\"version\":\"2\"}", true, 5);
        JsonNode before = fetch(p, 0);
        assertEquals(0, before.get("orders").size()); assertTrue(before.get("cursor").asLong() < low, "the cursor stays before the held orders");
        assertEquals(2, admin("/release", "{}", 200).get("released").asInt());
        JsonNode after = fetch(p, before.get("cursor").asLong());
        assertEquals(2, after.get("orders").size());
        long seqLow = body(mvc.perform(get("/api/v1/admin/orders/" + low).header("Authorization", ADMIN)).andReturn()).get("seq").asLong();
        long seqHigh = body(mvc.perform(get("/api/v1/admin/orders/" + high).header("Authorization", ADMIN)).andReturn()).get("seq").asLong();
        assertTrue(seqHigh < seqLow, "higher priority = lower sequence number = applied first");
    }

    @Test
    void sequenceNumbersNeverRepeatEvenUnderConcurrentReleases() throws Exception {
        for (int i = 0; i < 6; i++) create("any", null, "rights.refresh", "{\"reason\":\"r%d\"}".formatted(i), true, 0);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(4);
        var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
        for (int i = 0; i < 4; i++) futures.add(pool.submit(() -> { try { admin("/release", "{}", 200); } catch (Exception e) { throw new RuntimeException(e); } }));
        for (var f : futures) f.get();
        pool.shutdown();
        assertEquals(0, jdbc.queryForObject("select count(*) from (select kid, seq from order_msg where seq is not null group by kid, seq having count(*) > 1) x", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from order_msg where state = 'QUEUED'", Integer.class));
    }

    @Test
    void groupAndLicenceTargetsUseMembershipAndExpiredOrdersAreNotHanded() throws Exception {
        String p = phone("phone"); pair(p, INFO_A);
        long g = create("group", "beta", "flag.set", "{\"name\":\"bt.tunnel\",\"value\":\"1\"}", false, 0);
        assertEquals(0, fetch(p, 0).get("orders").size(), "TV not in the group");
        admin("/membership", "{\"kind\":\"group\",\"ref\":\"beta\",\"tv\":\"" + TV_A + "\",\"member\":true}", 204);
        assertTrue(fetch(p, 0).get("orders").size() >= 1);
        jdbc.update("update order_msg set expires_at = ? where id = ?", java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(60)), g);
        long still = 0;
        for (JsonNode o : fetch(p, 0).get("orders")) if (payloadOf(o.get("token").asText()).contains("bt.tunnel")) still++;
        assertEquals(0, still, "expired = never handed");
        assertEquals("EXPIRED", body(mvc.perform(get("/api/v1/admin/orders/" + g).header("Authorization", ADMIN)).andReturn()).get("state").asText());
    }

    @Test
    void cancelledOrdersStopBeingHanded() throws Exception {
        String p = phone("phone"); pair(p, INFO_A);
        long id = create("device", TV_A, "update.channel", "{\"channel\":\"beta\"}", true, 0);
        mvc.perform(post("/api/v1/admin/orders/" + id + "/cancel").header("Authorization", ADMIN)).andExpect(status().isNoContent());
        admin("/release", "{}", 200);
        for (JsonNode o : fetch(p, 0).get("orders")) assertFalse(payloadOf(o.get("token").asText()).contains("update.channel"));
    }

    @Test
    void authenticationAndRoles() throws Exception {
        mvc.perform(get("/api/v1/orders")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/orders").header("Authorization", "Bearer forged")).andExpect(status().isUnauthorized());
        String tv = phone("tv");
        mvc.perform(get("/api/v1/orders").header("Authorization", "Bearer " + tv)).andExpect(status().isForbidden());      // a TV never calls this route
        mvc.perform(post("/api/v1/admin/orders").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/orders")).andExpect(status().isUnauthorized());
    }

    @Test
    void auditChainIsIntactThenDetectsATamperedLine() throws Exception {
        create("any", null, "rights.refresh", "{\"reason\":\"audit\"}", false, 0);
        assertEquals(-1, body(mvc.perform(get("/api/v1/admin/orders/audit/verify").header("Authorization", ADMIN)).andReturn()).get("firstBrokenId").asLong());
        long victim = jdbc.queryForObject("select min(id) from order_audit", Long.class);
        String original = jdbc.queryForObject("select detail from order_audit where id = ?", String.class, victim);
        jdbc.update("update order_audit set detail = 'falsifié' where id = ?", victim);
        assertEquals(victim, body(mvc.perform(get("/api/v1/admin/orders/audit/verify").header("Authorization", ADMIN)).andReturn()).get("firstBrokenId").asLong());
        jdbc.update("update order_audit set detail = ? where id = ?", original, victim);          // the chain is shared by the other tests of the class
    }
}
