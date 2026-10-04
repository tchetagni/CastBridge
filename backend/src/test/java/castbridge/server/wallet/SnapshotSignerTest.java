package castbridge.server.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** {@code cbw1} exactement au format du § 3.4 : octet pour octet identique aux vecteurs communs Kotlin ↔ Java (Ed25519 est déterministe). */
class SnapshotSignerTest {
    static final ObjectMapper JSON = new ObjectMapper();

    static JsonNode vectors() throws IOException { return JSON.readTree(Files.readString(Path.of("..", "tools", "wallet", "wallet-vectors.json"))); }

    static byte[] seedOf(JsonNode v, String role) {
        for (JsonNode k : v.get("keys")) if (k.get("role").asText().equals(role)) return HexFormat.of().parseHex(k.get("seedHex").asText());
        throw new IllegalStateException(role);
    }

    static SnapshotSigner.Snapshot fromPayload(JsonNode p) {
        JsonNode f = p.get("flags");
        return new SnapshotSigner.Snapshot(p.get("id").asText(), p.get("ed").asText(), p.get("n").asLong(), p.get("nb").asLong(), p.get("m").asLong(), p.get("mb").asLong(),
                p.get("seq").asLong(), p.get("at").asLong(), f.get("frozen").asBoolean(), f.get("stakesN").asBoolean(), f.get("stakesM").asBoolean());
    }

    static JsonNode payloadOf(String token) throws IOException { return JSON.readTree(Base64.getUrlDecoder().decode(token.split("\\.")[1])); }

    @Test
    void javaProducesTheKotlinGoldenTokensByteForByte() throws Exception {
        JsonNode v = vectors();
        SnapshotSigner signer = new SnapshotSigner(WalletKey.fromSeed(seedOf(v, "wallet")), null);
        int n = 0;
        for (JsonNode g : v.get("golden").get("cbw1")) {
            String golden = g.get("token").asText();
            assertEquals(golden, signer.sign(fromPayload(payloadOf(golden))), g.get("name").asText());
            n++;
        }
        assertEquals(3, n);
        assertEquals(v.get("keys").get(0).get("kid").asText(), signer.kid());
    }

    @Test
    void javaVerifiesTheKotlinGoldenAndRefusesTheRefusedVectors() throws Exception {
        JsonNode v = vectors();
        SnapshotSigner signer = new SnapshotSigner(WalletKey.fromSeed(seedOf(v, "wallet")), null);
        for (JsonNode g : v.get("golden").get("cbw1")) assertTrue(signer.verify(g.get("token").asText()).isPresent(), g.get("name").asText());
        for (JsonNode g : v.get("refused").get("cbw1")) {
            String why = g.get("expect").asText();
            if (why.equals("BAD_SIGNATURE") || why.equals("UNKNOWN_KEY") || why.equals("UNREADABLE")) {
                assertTrue(signer.verify(g.get("token").asText()).isEmpty(), g.get("name").asText());
            }
        }
    }

    @Test
    void compactJsonInTheSignedOrder() throws Exception {
        SnapshotSigner s = new SnapshotSigner(WalletKey.fromSeed(new byte[32]), null);
        String token = s.sign(new SnapshotSigner.Snapshot("1234-5678-9ABC-DEF0", "TRIAL", 100, 10, 0, 0, 5, 1790000000000L, false, true, false));
        String text = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
        assertEquals("{\"kid\":\"" + s.kid() + "\",\"id\":\"1234-5678-9ABC-DEF0\",\"ed\":\"TRIAL\",\"n\":100,\"nb\":10,\"m\":0,\"mb\":0,\"seq\":5,\"at\":1790000000000,"
                + "\"flags\":{\"frozen\":false,\"stakesN\":true,\"stakesM\":false}}", text);
        assertTrue(token.startsWith("cbw1."));
        assertFalse(token.contains("="), "base64url sans bourrage");
    }

    @Test
    void twoKidsAreAcceptedForRotation() {
        WalletKey a = WalletKey.fromSeed(filled(1)), b = WalletKey.fromSeed(filled(2));
        SnapshotSigner now = new SnapshotSigner(b, a);          // b signe, a reste accepté
        SnapshotSigner old = new SnapshotSigner(a, null);
        SnapshotSigner.Snapshot snap = new SnapshotSigner.Snapshot("1234-5678-9ABC-DEF0", "PROD", 1, 0, 0, 0, 1, 1, false, true, true);
        String signedByOld = old.sign(snap), signedByNew = now.sign(snap);
        assertNotEquals(signedByOld, signedByNew);
        assertTrue(now.verify(signedByOld).isPresent(), "l'ancienne clé reste acceptée pendant la rotation");
        assertTrue(now.verify(signedByNew).isPresent());
        assertTrue(old.verify(signedByNew).isEmpty(), "une clé inconnue est refusée");
        assertEquals(java.util.Set.of(a.kid(), b.kid()), now.acceptedKids());
    }

    @Test
    void tamperedAndForeignDomainTokensAreRefused() {
        SnapshotSigner s = new SnapshotSigner(WalletKey.fromSeed(filled(3)), null);
        String token = s.sign(new SnapshotSigner.Snapshot("1234-5678-9ABC-DEF0", "PROD", 10, 0, 0, 0, 1, 1, false, true, true));
        String[] p = token.split("\\.");
        String other = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"kid\":\"x\"}".getBytes(StandardCharsets.UTF_8));
        assertTrue(s.verify(p[0] + "." + other + "." + p[2]).isEmpty());
        assertTrue(s.verify("cbp1." + p[1] + "." + p[2]).isEmpty(), "un autre préfixe ne vaut jamais");
        assertTrue(s.verify(null).isEmpty());
        assertTrue(s.verify("n'importe quoi").isEmpty());
        Optional<Map<String, Object>> ok = s.verify(token);
        assertEquals(10L, ((Number) ok.orElseThrow().get("n")).longValue());
    }

    @Test
    void keyFileFormatsAndAbsence() throws Exception {
        java.security.KeyPair kp = WalletTestBase.pair();
        Path pem = WalletTestBase.writeKey(kp);
        WalletKey fromPem = WalletKey.fromFile(pem.toString());
        assertEquals(castbridge.server.licenses.LicenseKeyring.kidOf(WalletTestBase.rawPublicBytes(kp)), fromPem.kid());
        Path seed = Files.createTempFile("wallet-seed", ".key");
        Files.writeString(seed, Base64.getEncoder().encodeToString(filled(9)));
        assertEquals(WalletKey.fromSeed(filled(9)).kid(), WalletKey.fromFile(seed.toString()).kid());
        assertEquals(null, WalletKey.fromFile(""));
        assertEquals(null, WalletKey.fromFile("/nonexistent/wallet.key"));
        assertFalse(new SnapshotSigner(null, null).enabled());
    }

    private static byte[] filled(int b) {
        byte[] x = new byte[32];
        java.util.Arrays.fill(x, (byte) b);
        return x;
    }
}
