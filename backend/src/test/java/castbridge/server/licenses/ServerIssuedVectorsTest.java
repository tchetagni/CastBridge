package castbridge.server.licenses;

import static org.assertj.core.api.Assertions.assertThat;

import castbridge.server.licenses.ActivationSigner.IssueKind;
import castbridge.server.licenses.DeviceIdentity.Factor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.junit.jupiter.api.Test;

/**
 * End to end, both ways. (1) Java side: what the SERVER code issues (the very signer the activation service uses, with the server scopes) is accepted by the Java port of the
 * device verifier. (2) Kotlin side: the same bytes are in tools/activation/server-issued.json and are checked by {@code ServerIssuedActivationTest} in module {@code core} with
 * the REAL {@code castbridge.core.owner.ActivationVerifier} (the coordinator runs it). This test also keeps the file honest: the server must still produce, byte for byte, the
 * tokens written in the file (Ed25519 is deterministic). Regenerate after an intentional change: {@code CASTBRIDGE_WRITE_SERVER_ISSUED=1 mvn test -Dtest=ServerIssuedVectorsTest}.
 * The key is the TEST key "server" of test-vectors.json (derived from a public text, worth nothing).
 */
class ServerIssuedVectorsTest {
    static final long T0 = 1_800_000_000_000L;
    static final long DAY = 86_400_000L;
    static final ObjectMapper JSON = new ObjectMapper();

    static JsonNode vectors;

    static Map<Factor, String> fp(String device) {
        Map<Factor, String> m = new EnumMap<>(Factor.class);
        for (JsonNode d : vectors.get("devices")) {
            if (d.get("name").asText().equals(device)) d.get("fingerprints").fields().forEachRemaining(e -> m.put(Factor.valueOf(e.getKey()), e.getValue().asText()));
        }
        return m;
    }

    static ObjectNode build() throws IOException {
        vectors = JSON.readTree(Files.readString(Path.of("..", "tools", "activation", "test-vectors.json")));
        JsonNode key = null;
        for (JsonNode k : vectors.get("keys")) if (k.get("name").asText().equals("server")) key = k;
        var keyring = new LicenseKeyring(new Ed25519PrivateKeyParameters(HexFormat.of().parseHex(key.get("seed").asText()), 0));
        var signer = ScopedActivationSigner.server(new Ed25519ActivationSigner(keyring));
        var issuer = EnvelopeIssuer.server(keyring);

        ObjectNode root = JSON.createObjectNode();
        root.put("format", "castbridge-server-issued-v1");
        root.put("warning", "Produit par le code du SERVEUR (backend, module licenses) avec la clé de TEST « server » de test-vectors.json : aucun secret. Vérifié par ServerIssuedActivationTest (core) avec le vrai ActivationVerifier.");
        root.put("nowMs", T0);
        ObjectNode server = root.putObject("server");
        server.put("name", "server");
        server.put("publicKey", keyring.publicKeyBase64());
        server.put("kid", keyring.kid());
        ArrayNode scopes = server.putArray("scopes");
        ScopedActivationSigner.SERVER_SCOPES.stream().map(Enum::name).sorted().forEach(scopes::add);
        ArrayNode cases = root.putArray("cases");

        String purchase = "purchase|p-classe-cm2|classe-cm2|" + (T0 - 10 * DAY);
        String sub = "subscription|abo-tout|tout|" + (T0 - DAY) + "|" + (T0 + 90 * DAY) + "|" + (7 * DAY) + "|0";
        String nonce = "00112233445566778899aabbccddeeff";
        record Spec(String id, String device, IssueKind kind, String subject, String license, String seat, List<String> rights, String nonce) {}
        String seatOfTvA = WireActivation.defaultSeat("lic-0001", fp("tvA"));
        List<Spec> specs = List.of(
                new Spec("server-trial", "tvA", IssueKind.TRIAL, "tv", "trial", null, List.of(), nonce),
                new Spec("server-production-purchase", "tvA", IssueKind.PRODUCTION, "tv", "lic-0001", null, List.of(purchase), nonce),
                new Spec("server-production-subscription", "tvA", IssueKind.PRODUCTION, "tv", "lic-0001", null, List.of(purchase, sub), "ffeeddccbbaa99887766554433221100"),
                new Spec("server-phone", "phoneP", IssueKind.PRODUCTION, "phone", "lic-0001", null, List.of(purchase), nonce),
                new Spec("server-reissue-module-replaced", "tvA-swapped-wifi", IssueKind.PRODUCTION, "tv", "lic-0001", seatOfTvA, List.of(purchase), "aabbccddeeff00112233445566778899"));
        for (Spec s : specs) {
            Map<Factor, String> f = fp(s.device());
            var device = new DeviceIdentity.Request(f, DeviceIdentity.code(f), DeviceIdentity.kFor(f.size()));
            var out = signer.sign(new ActivationSigner.ActivationRequest(s.kind(), s.subject(), s.license(), s.seat(), device, s.rights(), T0, T0 - 3_600_000L, 48, s.nonce()));
            ObjectNode c = cases.addObject();
            c.put("type", "activation");
            c.put("id", s.id());
            c.put("device", s.device());
            ObjectNode fps = c.putObject("fingerprints");
            f.forEach((k, v) -> fps.put(k.name(), v));
            c.put("subject", s.subject());
            c.put("token", out.text());
            ObjectNode e = c.putObject("expect");
            e.put("kind", s.kind().name().toLowerCase(java.util.Locale.ROOT));
            e.put("license", s.license());
            e.put("seat", out.seat());
            ArrayNode r = e.putArray("rights");
            s.rights().stream().sorted().forEach(r::add);
        }
        ObjectNode rev = cases.addObject();
        rev.put("type", "revocation");
        rev.put("id", "server-revocation");
        rev.put("token", issuer.revocation(T0, Set.of("df9e7c0d8cc2809d"), new TreeMap<>(Map.of("lic-0001|" + seatOfTvA, T0)), null, null));
        ObjectNode re = rev.putObject("expect");
        re.putArray("keys").add("df9e7c0d8cc2809d");
        re.putObject("seats").put("lic-0001|" + seatOfTvA, T0);
        ObjectNode ord = cases.addObject();
        ord.put("type", "order");
        ord.put("id", "server-order");
        ord.put("device", "tvA");
        ObjectNode ofp = ord.putObject("fingerprints");
        fp("tvA").forEach((k, v) -> ofp.put(k.name(), v));
        ord.put("token", issuer.order(10, "0102030405060708", T0, T0 - DAY, T0 + 30 * DAY, Envelope.Target.ANY, "refresh-rights", Map.of("reason", "periodic")));
        ObjectNode oe = ord.putObject("expect");
        oe.put("action", "refresh-rights");
        oe.putObject("params").put("reason", "periodic");
        oe.put("seq", 10);
        return root;
    }

    @Test
    void theFileOfServerIssuedTokensIsWhatTheServerProducesAndTheJavaVerifierAcceptsIt() throws IOException {
        ObjectNode built = build();
        Path file = Path.of("..", "tools", "activation", "server-issued.json");
        String text = JSON.writerWithDefaultPrettyPrinter().writeValueAsString(built) + "\n";
        if (System.getenv("CASTBRIDGE_WRITE_SERVER_ISSUED") != null) Files.writeString(file, text);
        assertThat(file).as("tools/activation/server-issued.json (CASTBRIDGE_WRITE_SERVER_ISSUED=1 pour l'écrire)").exists();
        JsonNode onDisk = JSON.readTree(Files.readString(file));
        assertThat(onDisk).as("le serveur doit produire exactement les octets du fichier").isEqualTo(built);

        // the Java port of the device verifier accepts what the server issued (the Kotlin one does it in ServerIssuedActivationTest)
        var ring = new EnvelopeVerifier.Ring().add(new EnvelopeVerifier.TrustedKey(built.get("server").get("kid").asText(), Base64.getDecoder().decode(built.get("server").get("publicKey").asText()),
                ScopedActivationSigner.SERVER_SCOPES));
        for (JsonNode c : built.get("cases")) {
            String id = c.get("id").asText();
            switch (c.get("type").asText()) {
                case "activation" -> {
                    var v = new EnvelopeVerifier(ring, EnvelopeVerifier.Revocations.none(), new EnvelopeVerifier.SeqState(), c.get("subject").asText());
                    var r = v.verifyActivation(c.get("token").asText(), fp(c.get("device").asText()), T0);
                    assertThat(r.reason()).as(id).isNull();
                    assertThat(r.activation().seat()).isEqualTo(c.get("expect").get("seat").asText());
                }
                case "revocation" -> assertThat(new EnvelopeVerifier(ring, EnvelopeVerifier.Revocations.none(), new EnvelopeVerifier.SeqState(), "tv").verifyRevocation(c.get("token").asText())).as(id).isNotNull();
                default -> {
                    var r = new EnvelopeVerifier(ring, EnvelopeVerifier.Revocations.none(), new EnvelopeVerifier.SeqState(), "tv").verifyOrder(c.get("token").asText(),
                            new EnvelopeVerifier.DeviceContext(fp("tvA"), Set.of(), Set.of()), T0);
                    assertThat(r.reason()).as(id).isNull();
                }
            }
        }
        // the module-replaced re-issue keeps the seat of the first activation: no seat consumed
        assertThat(built.get("cases").get(4).get("expect").get("seat").asText()).isEqualTo(built.get("cases").get(1).get("expect").get("seat").asText());
    }
}
