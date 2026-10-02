package castbridge.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import castbridge.server.config.CastbridgeProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** GET /api/v1/catalog/bundles: the owner's signed bundle catalogue is relayed as is (public, 404 when absent, signature checkable). */
class BundleCatalogApiTest extends ApiTestBase {
    @Autowired CastbridgeProperties props;

    static String sha(String s) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))); }

    /** Same text as castbridge.core.lots.SignedBundleCatalog.canonicalPayload and tools/trial-edition canonical_bundles. */
    static String canonical(JsonNode c) throws Exception {
        List<String> lines = new ArrayList<>(List.of("castbridge-bundle-catalog-v1", "generatedAt=" + c.get("generatedAt").asText()));
        List<JsonNode> bs = new ArrayList<>(); c.get("bundles").forEach(bs::add);
        bs.sort((a, b) -> a.get("id").asText().compareTo(b.get("id").asText()));
        for (JsonNode b : bs) {
            List<String> lots = new ArrayList<>(); b.get("lots").forEach(l -> lots.add(l.asText())); lots.sort(String::compareTo);
            lines.add("bundle=" + b.get("id").asText() + "|" + b.get("type").asText() + "|" + b.get("rawBytes").asLong() + "|" + b.get("rentalDays").asInt() + "|"
                    + sha(b.get("title").asText()) + "|" + String.join(",", lots));
        }
        return String.join("\n", lines);
    }

    @Test void absentThenPresentAndSignatureRoundtrip() throws Exception {
        Path f = props.catalog().bundlesFile();
        Files.deleteIfExists(f);
        String msg = mvc.perform(get("/api/v1/catalog/bundles")).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(msg.contains("Catalogue des bouquets"), msg);

        KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String unsigned = "{\"bundles\":[{\"id\":\"quiz-cm2\",\"type\":\"quiz\",\"lots\":[\"quiz:cm2\"],\"title\":\"Quiz « CM2 »\",\"rawBytes\":3000,\"rentalDays\":30},"
                + "{\"id\":\"classe-cm2\",\"type\":\"classe\",\"lots\":[\"learn:cm2\",\"quiz:cm2\"],\"title\":\"Classe CM2\",\"rawBytes\":9000,\"rentalDays\":14}],\"generatedAt\":\"2026-10-02T10:00:00Z\"}";
        Signature s = Signature.getInstance("Ed25519"); s.initSign(kp.getPrivate());
        s.update(canonical(json.readTree(unsigned)).getBytes(StandardCharsets.UTF_8));
        String signed = unsigned.substring(0, unsigned.length() - 1) + ",\"keyId\":\"test\",\"signature\":\"" + Base64.getEncoder().encodeToString(s.sign()) + "\"}";
        Files.createDirectories(f.getParent());
        Files.writeString(f, signed);

        var first = mvc.perform(get("/api/v1/catalog/bundles")).andExpect(status().isOk()).andReturn();
        String etag = first.getResponse().getHeader("ETag");
        assertTrue(etag != null && etag.matches("\"[0-9a-f]{16}\""), etag);
        assertEquals("no-cache", first.getResponse().getHeader("Cache-Control"));
        mvc.perform(get("/api/v1/catalog/bundles").header("If-None-Match", etag)).andExpect(status().isNotModified());
        mvc.perform(get("/api/v1/catalog/bundles").header("If-None-Match", "\"autre\"")).andExpect(status().isOk());
        JsonNode c = body(first);
        assertEquals(2, c.get("bundles").size());
        Signature v = Signature.getInstance("Ed25519"); v.initVerify(kp.getPublic());
        v.update(canonical(c).getBytes(StandardCharsets.UTF_8));
        assertTrue(v.verify(Base64.getDecoder().decode(c.get("signature").asText())), "the relayed file verifies");

        Files.writeString(f, signed.replace("Classe CM2", "Classe CM2 bis"));
        String etag2 = mvc.perform(get("/api/v1/catalog/bundles")).andExpect(status().isOk()).andReturn().getResponse().getHeader("ETag");
        assertTrue(!etag.equals(etag2), "content change gives a new ETag");

        Files.writeString(f, "pas du json");
        mvc.perform(get("/api/v1/catalog/bundles")).andExpect(status().isServiceUnavailable());
        Files.writeString(f, "x".repeat(1024 * 1024 + 1));
        mvc.perform(get("/api/v1/catalog/bundles")).andExpect(status().isPayloadTooLarge());
        Files.deleteIfExists(f);
    }
}
