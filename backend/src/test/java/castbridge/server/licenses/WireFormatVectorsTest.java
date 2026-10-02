package castbridge.server.licenses;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import castbridge.server.licenses.ActivationSigner.IssueKind;
import castbridge.server.licenses.ActivationSigner.SignerScope;
import castbridge.server.web.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Conformance of the Java port with docs/ACTIVATION-FORMAT.md: the vectors of tools/activation/test-vectors.json (also played by the Kotlin
 * core and by the Python reference) must give the same bytes. Plain JUnit, no Spring. The seeds in the vectors are TEST keys.
 */
class WireFormatVectorsTest {
    static JsonNode vectors;
    static final Map<String, JsonNode> KEYS = new HashMap<>();
    static final Map<String, JsonNode> DEVICES = new HashMap<>();

    @BeforeAll
    static void load() throws IOException {
        Path p = Path.of("..", "tools", "activation", "test-vectors.json");
        vectors = new ObjectMapper().readTree(Files.readString(p));
        vectors.get("keys").forEach(k -> KEYS.put(k.get("name").asText(), k));
        vectors.get("devices").forEach(d -> DEVICES.put(d.get("name").asText(), d));
    }

    static Map<DeviceIdentity.Factor, String> fp(JsonNode d) {
        Map<DeviceIdentity.Factor, String> m = new EnumMap<>(DeviceIdentity.Factor.class);
        d.get("fingerprints").fields().forEachRemaining(e -> m.put(DeviceIdentity.Factor.valueOf(e.getKey()), e.getValue().asText()));
        return m;
    }

    static String requestText(String code, Map<DeviceIdentity.Factor, String> fp) {
        List<String> l = new ArrayList<>(List.of("code=" + code, "k=" + DeviceIdentity.kFor(fp.size())));
        fp.forEach((f, h) -> l.add("factor=" + f.name() + "|" + h));
        return String.join("\n", l);
    }

    static LicenseKeyring ring(String name) { return new LicenseKeyring(new Ed25519PrivateKeyParameters(HexFormat.of().parseHex(KEYS.get(name).get("seed").asText()), 0)); }

    static java.util.Set<SignerScope> scopes(String name) {
        java.util.Set<SignerScope> s = EnumSet.noneOf(SignerScope.class);
        KEYS.get(name).get("scopes").forEach(x -> s.add(SignerScope.valueOf(x.asText())));
        return s;
    }

    @Test
    void keysAndDevicesOfTheVectorsAreCoherent() {
        for (var k : KEYS.values()) {
            LicenseKeyring r = ring(k.get("name").asText());
            assertThat(r.publicKeyBase64()).isEqualTo(k.get("publicKey").asText());
            assertThat(r.kid()).isEqualTo(k.get("kid").asText());
        }
        for (var d : DEVICES.values()) assertThat(DeviceIdentity.code(fp(d))).as(d.get("name").asText()).isEqualTo(d.get("code").asText());
    }

    @Test
    void base32AndDeviceCodeParsing() {
        for (JsonNode c : vectors.get("cases")) {
            switch (c.get("type").asText()) {
                case "base32" -> assertThat(Crockford.encode(HexFormat.of().parseHex(c.get("bytesHex").asText()))).isEqualTo(c.get("expectText").asText());
                case "device-code-parse" -> {
                    String got = DeviceIdentity.parseCode(c.get("text").asText());
                    JsonNode e = c.get("expect");
                    if (e.get("result").asText().equals("ok")) assertThat(got).as(c.get("id").asText()).isEqualTo(e.get("code").asText());
                    else assertThat(got).as(c.get("id").asText()).isNull();
                }
                case "fingerprints" -> {
                    JsonNode e = c.get("expect");
                    if (e.get("fingerprints").size() > 0) {
                        Map<DeviceIdentity.Factor, String> m = new EnumMap<>(DeviceIdentity.Factor.class);
                        e.get("fingerprints").fields().forEachRemaining(x -> m.put(DeviceIdentity.Factor.valueOf(x.getKey()), x.getValue().asText()));
                        assertThat(DeviceIdentity.code(m)).as(c.get("id").asText()).isEqualTo(e.get("code").asText());
                        assertThat(HexFormat.of().formatHex(DeviceIdentity.setHash(m))).isEqualTo(e.get("setHash").asText());
                        assertThat(DeviceIdentity.kFor(m.size())).isEqualTo(e.get("k").asInt());
                    }
                }
                default -> { }
            }
        }
    }

    @Test
    void everyBuildActivationVectorGivesTheSameBytesOrTheSameRefusal() {
        int n = 0;
        for (JsonNode c : vectors.get("cases")) {
            if (!c.get("type").asText().equals("build-activation")) continue;
            n++;
            String id = c.get("id").asText();
            JsonNode r = c.get("request");
            JsonNode dev = DEVICES.get(r.get("device").asText());
            ScopedActivationSigner signer = new ScopedActivationSigner(new Ed25519ActivationSigner(ring(c.get("signer").asText())), scopes(c.get("signer").asText()));
            List<String> rights = new ArrayList<>();
            r.get("rights").forEach(x -> rights.add(x.asText()));
            boolean refused = c.get("expect").path("refused").asBoolean(false);
            try {
                String code = r.hasNonNull("deviceCodeOverride") ? r.get("deviceCodeOverride").asText() : dev.get("code").asText();
                var device = DeviceIdentity.parseRequest(requestText(code, fp(dev)));
                IssueKind kind = r.get("kind").asText().equals("trial") ? IssueKind.TRIAL : IssueKind.PRODUCTION;
                var req = new ActivationSigner.ActivationRequest(kind, r.get("subject").asText(), r.get("license").asText(), r.hasNonNull("seat") ? r.get("seat").asText() : null, device, rights,
                        r.get("issuedAt").asLong(), r.get("notBefore").asLong(), r.get("windowHours").asInt(), r.get("nonce").asText());
                var out = signer.sign(req);
                assertThat(refused).as(id + " devait être refusé").isFalse();
                assertThat(out.text()).as(id).isEqualTo(c.get("expect").get("token").asText());
                // and the strict decoder reads it back
                var dec = WireActivation.decode(out.text());
                assertThat(dec).as(id + " décodable").isNotNull();
                assertThat(dec.fields().license()).isEqualTo(r.get("license").asText());
                assertThat(dec.fields().factors()).isEqualTo(fp(dev));
            } catch (ApiException e) {
                assertThat(refused).as(id + " refus inattendu : " + e.getMessage()).isTrue();
            }
        }
        assertThat(n).isEqualTo(13);
    }

    @Test
    void serverKeyCannotSignOpenAllNorTransferEvenWithAnOpenAllRight() {
        var server = ScopedActivationSigner.server(new Ed25519ActivationSigner(ring("server")));
        JsonNode dev = DEVICES.get("tvA");
        var device = DeviceIdentity.parseRequest(requestText(dev.get("code").asText(), fp(dev)));
        long now = vectors.get("nowMs").asLong();
        var ok = new ActivationSigner.ActivationRequest(IssueKind.PRODUCTION, "tv", "lic-0001", null, device, List.of("purchase|p-classe-cm2|classe-cm2|" + now), now, now, 30, "00112233445566778899aabbccddeeff");
        assertThat(server.sign(ok).text()).startsWith("cbx1.");
        var openAll = new ActivationSigner.ActivationRequest(IssueKind.PRODUCTION, "tv", "lic-0001", null, device, List.of("openall|tout|" + now + "|" + (now + 86_400_000L)), now, now, 30, "00112233445566778899aabbccddeeff");
        assertThatThrownBy(() -> server.sign(openAll)).isInstanceOf(ApiException.class).hasMessageContaining("tout ouvrir");
        // not even with the "all open" power asked for in another way: the kind OPEN_ALL
        var openAllKind = new ActivationSigner.ActivationRequest(IssueKind.OPEN_ALL, "tv", "lic-0001", null, device, List.of(), now, now, 30, "00112233445566778899aabbccddeeff");
        assertThatThrownBy(() -> server.sign(openAllKind)).isInstanceOf(ApiException.class).hasMessageContaining("tout ouvrir");
        var transfer = new ActivationSigner.ActivationRequest(IssueKind.TRANSFER, "tv", "lic-0001", null, device, List.of(), now, now, 30, "00112233445566778899aabbccddeeff");
        assertThatThrownBy(() -> server.sign(transfer)).isInstanceOf(ApiException.class).hasMessageContaining("transfert");
        // the desk key (all scopes) may sign the open-all right, which proves the refusal comes from the scope and not from a format rule
        var desk = new ScopedActivationSigner(new Ed25519ActivationSigner(ring("desk")), scopes("desk"));
        assertThat(desk.sign(openAll).text()).startsWith("cbx1.");
        assertThat(ScopedActivationSigner.SERVER_SCOPES).isEqualTo(scopes("server"));
        assertThat(ScopedActivationSigner.SERVER_SCOPES).contains(SignerScope.ISSUE_TRIAL, SignerScope.ISSUE_PRODUCTION, SignerScope.REVOKE, SignerScope.POLICY)
                .doesNotContain(SignerScope.TRANSFER, SignerScope.COMMAND_OPEN_ALL, SignerScope.COMMAND_UNLOCK, SignerScope.COMMAND_SUPPORT);
    }

    @Test
    void deviceRequestIsValidatedStrictly() {
        JsonNode dev = DEVICES.get("tvA");
        Map<DeviceIdentity.Factor, String> f = fp(dev);
        String code = dev.get("code").asText();
        assertThat(DeviceIdentity.parseRequest(requestText(code.toLowerCase().replace("-", " "), f)).code()).isEqualTo(code);
        assertThatThrownBy(() -> DeviceIdentity.parseRequest("")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> DeviceIdentity.parseRequest("code=" + code)).hasMessageContaining("sans facteur");
        assertThatThrownBy(() -> DeviceIdentity.parseRequest(requestText(code, f) + "\nfactor=WIFI|zz")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> DeviceIdentity.parseRequest(requestText(code, f) + "\nfactor=ETHERNET|" + "a".repeat(32))).hasMessageContaining("double");
        assertThatThrownBy(() -> DeviceIdentity.parseRequest(requestText(code, f).replace("k=4", "k=2"))).hasMessageContaining("k=2");
        assertThatThrownBy(() -> DeviceIdentity.parseRequest(requestText("ZX8P-S2TH-N5PG-V9V8", f))).hasMessageContaining("invalide");
        assertThatThrownBy(() -> DeviceIdentity.parseRequest(requestText(DEVICES.get("tvC-weak").get("code").asText(), f))).hasMessageContaining("ne correspond pas");
        assertThatThrownBy(() -> DeviceIdentity.parseRequest(requestText(code, f) + "\nrm -rf /")).hasMessageContaining("inattendue");
        assertThatThrownBy(() -> DeviceIdentity.parseRequest(requestText(code, f) + "\nfactor=GPS|" + "a".repeat(32))).hasMessageContaining("inconnu");
    }

    @Test
    void kOfNMatchingFollowsTheFormat() {
        JsonNode a = DEVICES.get("tvA");
        Map<DeviceIdentity.Factor, String> f = fp(a);
        assertThat(DeviceIdentity.matches(f, 4, f)).isTrue();
        Map<DeviceIdentity.Factor, String> oneModuleChanged = new EnumMap<>(f);
        oneModuleChanged.put(DeviceIdentity.Factor.WIFI, "b".repeat(32));
        assertThat(DeviceIdentity.matches(f, 4, oneModuleChanged)).isTrue();  // 4 of 5 still match
        oneModuleChanged.put(DeviceIdentity.Factor.BLUETOOTH, "c".repeat(32));
        assertThat(DeviceIdentity.matches(f, 4, oneModuleChanged)).isFalse(); // only 3 of 5
        Map<DeviceIdentity.Factor, String> noSoldered = new EnumMap<>(f);
        noSoldered.put(DeviceIdentity.Factor.FLASH, "d".repeat(32));
        noSoldered.put(DeviceIdentity.Factor.ETHERNET, "e".repeat(32));
        assertThat(DeviceIdentity.matches(f, 3, noSoldered)).isFalse();       // 3 weak ones match but no soldered one
        assertThat(DeviceIdentity.matches(Map.of(), 1, f)).isFalse();
        assertThat(DeviceIdentity.kFor(1)).isEqualTo(1);
        assertThat(DeviceIdentity.kFor(2)).isEqualTo(2);
        assertThat(DeviceIdentity.kFor(3)).isEqualTo(2);
        assertThat(DeviceIdentity.kFor(5)).isEqualTo(4);
    }
}
