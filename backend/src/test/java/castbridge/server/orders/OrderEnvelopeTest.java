package castbridge.server.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.junit.jupiter.api.Test;

/** The server's envelope is byte-identical to the Kotlin core and to the Python verifier (shared vectors), and its closed list equals the TV's (tools/orders/actions.json). */
class OrderEnvelopeTest {
    static final ObjectMapper JSON = new ObjectMapper();

    static JsonNode vectors() throws Exception { return JSON.readTree(Files.readString(Path.of("../tools/activation/test-vectors.json"))); }

    static ConfiguredOrderSigner signerOf(JsonNode v, String name) {
        for (JsonNode k : v.get("keys")) if (k.get("name").asText().equals(name)) return new ConfiguredOrderSigner(new Ed25519PrivateKeyParameters(HexFormat.of().parseHex(k.get("seed").asText()), 0));
        throw new IllegalStateException(name);
    }

    @Test
    void theBuiltOrderIsByteForByteTheSharedVector() throws Exception {
        JsonNode v = vectors();
        ConfiguredOrderSigner server = signerOf(v, "server");
        JsonNode c = null;
        for (JsonNode x : v.get("cases")) if (x.get("id").asText().equals("build-order-any")) c = x;
        JsonNode r = c.get("request");
        Map<String, String> params = new LinkedHashMap<>();
        r.get("params").fields().forEachRemaining(e -> params.put(e.getKey(), e.getValue().asText()));
        String payload = OrderEnvelope.payload(server.keyId(), r.get("seq").asLong(), r.get("nonce").asText(), r.get("issuedAt").asLong(), r.get("notBefore").asLong(), r.get("expiresAt").asLong(),
                OrderEnvelope.Target.any(), r.get("action").asText(), params);
        assertEquals(c.get("expect").get("token").asText(), OrderEnvelope.token(payload, server.sign(payload.getBytes(StandardCharsets.UTF_8))));
    }


    @Test
    void aDeviceTargetedOrderIsByteIdenticalToTheKotlinCore() throws Exception {
        ConfiguredOrderSigner server = signerOf(vectors(), "server");
        Map<String, String> f = new LinkedHashMap<>();
        f.put("FLASH", "c48cacd81b9663d0ba97c221cbd17f4f"); f.put("ETHERNET", "f91efd89e0025c2262da12cfbed43632"); f.put("WIFI", "ea8b552e2240bbdd8c88c42313c4deab");
        f.put("SYSTEM_SERIAL", "774beacbf351b0d205f9166669e4d3c7"); f.put("BLUETOOTH", "d365ea67e00585edaf8bef4d7378850d");
        String payload = OrderEnvelope.payload(server.keyId(), 7, "0102030405060708", 1800000000000L, 1800000000000L, 1802592000000L, OrderEnvelope.Target.device(4, f), "flag.set", Map.of("value", "1", "name", "learn.beta"));
        // same string as DeviceTargetGoldenTest.kt (built by the Kotlin core)
        assertEquals("cbx1.Y2FzdGJyaWRnZS1lbnZlbG9wZS12MQp0eXBlPW9yZGVyCmtpZD1jZTIwMmRmZGJlNTdiOTlhCnNlcT03Cm5vbmNlPTAxMDIwMzA0MDUwNjA3MDgKaXNzdWVkQXQ9MTgwMDAwMDAwMDAwMApub3RCZWZvcmU9MTgwMDAwMDAwMDAwMApleHBpcmVzQXQ9MTgwMjU5MjAwMDAwMAp0YXJnZXQ9ZGV2aWNlCms9NApmYWN0b3I9RkxBU0h8YzQ4Y2FjZDgxYjk2NjNkMGJhOTdjMjIxY2JkMTdmNGYKZmFjdG9yPUVUSEVSTkVUfGY5MWVmZDg5ZTAwMjVjMjI2MmRhMTJjZmJlZDQzNjMyCmZhY3Rvcj1XSUZJfGVhOGI1NTJlMjI0MGJiZGQ4Yzg4YzQyMzEzYzRkZWFiCmZhY3Rvcj1TWVNURU1fU0VSSUFMfDc3NGJlYWNiZjM1MWIwZDIwNWY5MTY2NjY5ZTRkM2M3CmZhY3Rvcj1CTFVFVE9PVEh8ZDM2NWVhNjdlMDA1ODVlZGFmOGJlZjRkNzM3ODg1MGQKLS0KYWN0aW9uPWZsYWcuc2V0CnBhcmFtPW5hbWV8bGVhcm4uYmV0YQpwYXJhbT12YWx1ZXwx.FFwxZHf+FsiqluuOZ6Zxt8w/ayCTwF7suCDrEurolk32etrdYD5OczSLpxWX9kvcHmu7OzUje6JCRtTy5SR3BQ==", OrderEnvelope.token(payload, server.sign(payload.getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void theDeviceCodeIsRecomputedLikeTheCore() throws Exception {
        JsonNode v = vectors();
        for (JsonNode d : v.get("devices")) {
            Map<String, String> f = new LinkedHashMap<>();
            d.get("fingerprints").fields().forEachRemaining(e -> f.put(e.getKey(), e.getValue().asText()));
            assertEquals(d.get("code").asText(), DeviceCodes.of(f), d.get("name").asText());
        }
    }

    private static Set<String> set(JsonNode a) { Set<String> s = new TreeSet<>(); a.forEach(x -> s.add(x.asText())); return s; }

    @Test
    void theClosedListEqualsTheSharedActionsFile() throws Exception {
        JsonNode a = JSON.readTree(Files.readString(Path.of("../tools/orders/actions.json")));
        assertEquals(set(a.get("actions")), new TreeSet<>(PolicyCatalog.ACTIONS));
        assertEquals(set(a.get("flags")), new TreeSet<>(PolicyCatalog.FLAGS));
        assertEquals(set(a.get("channels")), new TreeSet<>(PolicyCatalog.CHANNELS));
        assertEquals(set(a.get("levels")), new TreeSet<>(PolicyCatalog.LEVELS));
        a.get("budgets").fields().forEachRemaining(e -> {
            assertEquals(e.getValue().get(0).asLong(), PolicyCatalog.BUDGETS.get(e.getKey())[0]); assertEquals(e.getValue().get(1).asLong(), PolicyCatalog.BUDGETS.get(e.getKey())[1]);
        });
        assertEquals(a.get("budgets").size(), PolicyCatalog.BUDGETS.size());
        for (JsonNode n : a.get("never")) assertNotNull(PolicyCatalog.check(n.asText(), Map.of("path", "/sdcard"), 0), n.asText());
        assertTrue(PolicyCatalog.ACTIONS.stream().noneMatch(x -> x.contains("delete") || x.contains("wipe") || x.contains("exec") || x.contains("shell") || x.contains("remote") || x.contains("file") || x.contains("ssh") || x.contains("reset") || x.contains("uninstall")));
    }

    @Test
    void parametersAreCheckedStrictly() {
        long now = 1_800_000_000_000L;
        assertNull(PolicyCatalog.check("flag.set", Map.of("name", "learn.beta", "value", "1"), now));
        assertNotNull(PolicyCatalog.check("flag.set", Map.of("name", "update.verify", "value", "0"), now), "no flag for the signed update check");
        assertNotNull(PolicyCatalog.check("flag.set", Map.of("name", "learn.beta", "value", "1", "x", "y"), now));
        assertNotNull(PolicyCatalog.check("license.suspend", Map.of("license", "Lic 1"), now));
        assertNull(PolicyCatalog.check("license.suspend", Map.of("license", "lic-1"), now));
        assertNotNull(PolicyCatalog.check("license.extend", Map.of("license", "lic-1", "until", Long.toString(now + 900L * 86_400_000)), now), "no extension to forever");
        assertNull(PolicyCatalog.check("license.extend", Map.of("license", "lic-1", "until", Long.toString(now + 100L * 86_400_000)), now));
        assertNotNull(PolicyCatalog.check("message.show", Map.of("id", "m", "text", "Voir http://evil.example"), now), "a message is plain text");
        assertNull(PolicyCatalog.check("message.show", Map.of("id", "m", "text", "Une mise à jour est conseillée."), now));
        assertNotNull(PolicyCatalog.check("budget.set", Map.of("name", "lots_mb", "value", "999999999"), now));
        assertNotNull(PolicyCatalog.check("catalog.retire", Map.of("lots", "learn:a,"), now));
        assertNull(PolicyCatalog.check("catalog.retire", Map.of("lots", "learn:cm2,quiz:cm2"), now));
        assertNotNull(PolicyCatalog.check("revocation.add", Map.of("kid", "zz"), now));
        assertNull(PolicyCatalog.check("revocation.add", Map.of("kid", "0123456789abcdef"), now));
        assertNotNull(PolicyCatalog.check(null, Map.of(), now));
    }

    @Test
    void auditChainDetectsAnyChange() {
        java.time.Instant t = java.time.Instant.parse("2026-10-01T10:00:00Z");
        String h1 = OrderService.chain("0".repeat(64), t, "ORDER_CREATED", 1L, null, "admin", "x");
        assertEquals(h1, OrderService.chain("0".repeat(64), t, "ORDER_CREATED", 1L, null, "admin", "x"));
        assertTrue(!h1.equals(OrderService.chain("0".repeat(64), t, "ORDER_CREATED", 1L, null, "admin", "y")));
        assertTrue(!h1.equals(OrderService.chain("1".repeat(64), t, "ORDER_CREATED", 1L, null, "admin", "x")));
    }
}
