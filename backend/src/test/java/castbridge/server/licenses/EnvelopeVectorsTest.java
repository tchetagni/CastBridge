package castbridge.server.licenses;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import castbridge.server.licenses.ActivationSigner.SignerScope;
import castbridge.server.licenses.DeviceIdentity.Factor;
import castbridge.server.web.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The envelope {@code cbx1} of docs/ACTIVATION-FORMAT.md played on the Java port: the vectors of tools/activation/test-vectors.json (also played by the Kotlin core and the
 * Python reference) must give the same bytes (build-*) or the same verdict and the same reason (activation, order, revocation). Plain JUnit, no Spring. The seeds are TEST keys.
 */
class EnvelopeVectorsTest {
    static JsonNode vectors;
    static final Map<String, JsonNode> KEYS = new HashMap<>();
    static final Map<String, JsonNode> DEVICES = new HashMap<>();

    @BeforeAll
    static void load() throws IOException {
        vectors = new ObjectMapper().readTree(Files.readString(Path.of("..", "tools", "activation", "test-vectors.json")));
        vectors.get("keys").forEach(k -> KEYS.put(k.get("name").asText(), k));
        vectors.get("devices").forEach(d -> DEVICES.put(d.get("name").asText(), d));
    }

    static Map<Factor, String> fp(String device) {
        Map<Factor, String> m = new EnumMap<>(Factor.class);
        DEVICES.get(device).get("fingerprints").fields().forEachRemaining(e -> m.put(Factor.valueOf(e.getKey()), e.getValue().asText()));
        return m;
    }

    static Set<SignerScope> scopes(String name) {
        Set<SignerScope> s = EnumSet.noneOf(SignerScope.class);
        KEYS.get(name).get("scopes").forEach(x -> s.add(SignerScope.valueOf(x.asText())));
        return s;
    }

    static LicenseKeyring ring(String name) { return new LicenseKeyring(new Ed25519PrivateKeyParameters(HexFormat.of().parseHex(KEYS.get(name).get("seed").asText()), 0)); }

    static EnvelopeVerifier.Ring trusted(JsonNode names, JsonNode revoked) {
        var r = new EnvelopeVerifier.Ring();
        names.forEach(n -> r.add(new EnvelopeVerifier.TrustedKey(KEYS.get(n.asText()).get("kid").asText(), Base64.getDecoder().decode(KEYS.get(n.asText()).get("publicKey").asText()), scopes(n.asText()))));
        if (revoked != null) revoked.forEach(n -> r.revoke(KEYS.get(n.asText()).get("kid").asText()));
        return r;
    }

    static EnvelopeVerifier.SeqState seq(JsonNode last) {
        var s = new EnvelopeVerifier.SeqState();
        last.fields().forEachRemaining(e -> s.record(KEYS.get(e.getKey()).get("kid").asText(), e.getValue().asLong()));
        return s;
    }

    @Test
    void everyActivationVectorGivesTheSameVerdictAndReason() {
        int n = 0;
        for (JsonNode c : vectors.get("cases")) {
            if (!c.get("type").asText().equals("activation")) continue;
            n++;
            String id = c.get("id").asText();
            Map<String, Long> seats = new TreeMap<>();
            c.get("revokedSeats").forEach(s -> seats.put(s.get("license").asText() + "|" + s.get("seat").asText(), s.get("at").asLong()));
            var v = new EnvelopeVerifier(trusted(c.get("trustedKeys"), c.get("revokedKeys")), new EnvelopeVerifier.Revocations(Set.of(), seats), seq(c.get("lastSeq")), c.get("expectSubject").asText());
            var r = v.verifyActivation(c.get("token").asText(), fp(c.get("device").asText()), c.get("nowMs").asLong());
            JsonNode e = c.get("expect");
            if (e.get("result").asText().equals("accepted")) {
                assertThat(r.reason()).as(id).isNull();
                assertThat(r.activation().kind()).as(id).isEqualTo(e.get("kind").asText());
                assertThat(r.activation().subject()).isEqualTo(e.get("subject").asText());
                assertThat(r.activation().license()).isEqualTo(e.get("license").asText());
                assertThat(r.activation().seat()).isEqualTo(e.get("seat").asText());
                assertThat(r.weakIdentity()).as(id + " identité faible").isEqualTo(e.get("weakIdentity").asBoolean());
            } else {
                assertThat(r.reason()).as(id).isNotNull();
                assertThat(r.reason().name()).as(id).isEqualTo(e.get("reason").asText());
                assertThat(r.suspect()).as(id + " suspect").isEqualTo(e.get("suspect").asBoolean());
            }
        }
        assertThat(n).isEqualTo(44);
    }

    @Test
    void anActivationOlderThanTheLastSeenIsRefusedAndTheSameIsAccepted() {
        var c = vectors.get("cases");
        JsonNode prod = null;
        for (JsonNode x : c) if (x.get("id").asText().equals("act-production")) prod = x;
        var seqState = new EnvelopeVerifier.SeqState();
        var v = new EnvelopeVerifier(trusted(prod.get("trustedKeys"), null), EnvelopeVerifier.Revocations.none(), seqState, "tv");
        long now = prod.get("nowMs").asLong();
        assertThat(v.verifyActivation(prod.get("token").asText(), fp("tvA"), now).accepted()).isTrue();
        assertThat(v.verifyActivation(prod.get("token").asText(), fp("tvA"), now).accepted()).as("le même fichier réinstallé").isTrue();
        String kid = KEYS.get("desk").get("kid").asText();
        seqState.record(kid, Envelope.decode(prod.get("token").asText()).seq() + 1);
        assertThat(v.verifyActivation(prod.get("token").asText(), fp("tvA"), now).reason()).isEqualTo(EnvelopeVerifier.Reason.STALE_SEQUENCE);
    }

    @Test
    void everyRevocationVectorGivesTheSameVerdict() {
        int n = 0;
        for (JsonNode c : vectors.get("cases")) {
            if (!c.get("type").asText().equals("revocation")) continue;
            n++;
            String id = c.get("id").asText();
            var rev = new EnvelopeVerifier(trusted(c.get("trustedKeys"), null), EnvelopeVerifier.Revocations.none(), new EnvelopeVerifier.SeqState(), "tv").verifyRevocation(c.get("token").asText());
            JsonNode e = c.get("expect");
            if (e.get("result").asText().equals("accepted")) {
                assertThat(rev).as(id).isNotNull();
                List<String> keys = new ArrayList<>();
                e.get("keys").forEach(k -> keys.add(k.asText()));
                assertThat(rev.keys()).as(id).containsExactlyInAnyOrderElementsOf(keys);
                Map<String, Long> seats = new TreeMap<>();
                e.get("seats").fields().forEachRemaining(s -> seats.put(s.getKey(), s.getValue().asLong()));
                assertThat(rev.seats()).as(id).isEqualTo(seats);
            } else {
                assertThat(rev).as(id).isNull();
            }
        }
        assertThat(n).isEqualTo(5);
    }

    @Test
    void everyOrderVectorGivesTheSameVerdictAndReason() {
        int n = 0;
        for (JsonNode c : vectors.get("cases")) {
            if (!c.get("type").asText().equals("order")) continue;
            n++;
            String id = c.get("id").asText();
            Set<String> licenses = new java.util.HashSet<>(), groups = new java.util.HashSet<>();
            c.get("deviceLicenses").forEach(x -> licenses.add(x.asText()));
            c.get("deviceGroups").forEach(x -> groups.add(x.asText()));
            var v = new EnvelopeVerifier(trusted(c.get("trustedKeys"), c.get("revokedKeys")), EnvelopeVerifier.Revocations.none(), seq(c.get("lastSeq")), "tv");
            var r = v.verifyOrder(c.get("token").asText(), new EnvelopeVerifier.DeviceContext(fp(c.get("device").asText()), licenses, groups), c.get("nowMs").asLong());
            JsonNode e = c.get("expect");
            if (e.get("result").asText().equals("accepted")) {
                assertThat(r.reason()).as(id).isNull();
                assertThat(r.action()).isEqualTo(e.get("action").asText());
                Map<String, String> params = new TreeMap<>();
                e.get("params").fields().forEachRemaining(p -> params.put(p.getKey(), p.getValue().asText()));
                assertThat(r.params()).as(id).isEqualTo(params);
                assertThat(r.envelope().seq()).isEqualTo(e.get("seq").asLong());
            } else {
                assertThat(r.reason()).as(id).isNotNull();
                assertThat(r.reason().name()).as(id).isEqualTo(e.get("reason").asText());
            }
        }
        assertThat(n).isEqualTo(20);
    }

    @Test
    void everyBuildCommandOrderAndRevocationVectorGivesTheSameBytesOrTheSameRefusal() {
        int n = 0;
        for (JsonNode c : vectors.get("cases")) {
            String type = c.get("type").asText();
            if (!List.of("build-command", "build-order", "build-revocation").contains(type)) continue;
            n++;
            String id = c.get("id").asText();
            String signer = c.get("signer").asText();
            var issuer = new EnvelopeIssuer(ring(signer), scopes(signer));
            JsonNode r = c.get("request");
            boolean refused = c.get("expect").path("refused").asBoolean(false);
            try {
                String token;
                switch (type) {
                    case "build-command" -> {
                        JsonNode dev = DEVICES.get(r.get("device").asText());
                        var device = new DeviceIdentity.Request(fp(r.get("device").asText()), dev.get("code").asText(), DeviceIdentity.kFor(fp(r.get("device").asText()).size()));
                        List<String> bundles = new ArrayList<>(), lots = new ArrayList<>();
                        r.get("bundles").forEach(x -> bundles.add(x.asText()));
                        r.get("lots").forEach(x -> lots.add(x.asText()));
                        token = issuer.command(EnvelopeIssuer.Power.valueOf(r.get("power").asText().toUpperCase()), device, r.get("challenge").asText(), r.get("issuedAt").asLong(), r.get("days").asInt(),
                                r.get("action").asText(), bundles, lots, null);
                    }
                    case "build-order" -> {
                        Map<String, String> params = new TreeMap<>();
                        r.get("params").fields().forEachRemaining(p -> params.put(p.getKey(), p.getValue().asText()));
                        String t = r.get("target").asText();
                        Envelope.Target target = t.equals("any") ? Envelope.Target.ANY : t.startsWith("license:") ? Envelope.Target.license(t.substring(8)) : Envelope.Target.group(t.substring(6));
                        token = issuer.order(r.get("seq").asLong(), r.get("nonce").asText(), r.get("issuedAt").asLong(), r.get("notBefore").asLong(), r.get("expiresAt").asLong(), target,
                                r.get("action").asText(), params);
                    }
                    default -> {
                        Set<String> keys = new java.util.TreeSet<>();
                        r.get("keys").forEach(k -> keys.add(k.asText()));
                        Map<String, Long> seats = new TreeMap<>();
                        r.get("seats").fields().forEachRemaining(s -> seats.put(s.getKey(), s.getValue().asLong()));
                        token = issuer.revocation(r.get("at").asLong(), keys, seats, null, null);
                    }
                }
                assertThat(refused).as(id + " devait être refusé").isFalse();
                assertThat(token).as(id).isEqualTo(c.get("expect").get("token").asText());
            } catch (ApiException e) {
                assertThat(refused).as(id + " refus inattendu : " + e.getMessage()).isTrue();
            }
        }
        assertThat(n).isEqualTo(4);
    }

    @Test
    void theServerKeyCanDoRevokeAndPolicyButNoOwnerCommand() {
        var issuer = EnvelopeIssuer.server(ring("server"));
        JsonNode dev = DEVICES.get("tvA");
        var device = new DeviceIdentity.Request(fp("tvA"), dev.get("code").asText(), 4);
        for (var p : EnvelopeIssuer.Power.values()) {
            assertThatThrownBy(() -> issuer.command(p, device, "0123456789abcdef0123456789abcdef", 1800000000000L, p == EnvelopeIssuer.Power.SUPPORT ? 0 : 7, p == EnvelopeIssuer.Power.SUPPORT ? "diagnostic" : "",
                    List.of("classe-cm2"), List.of(), null)).isInstanceOf(ApiException.class).hasMessageContaining("portée");
        }
        assertThat(issuer.order(1, "0102030405060708", 1800000000000L, 1800000000000L, 1800086400000L, Envelope.Target.ANY, "refresh-rights", Map.of())).startsWith("cbx1.");
        assertThat(issuer.revocation(1800000000000L, Set.of(), Map.of(), null, null)).startsWith("cbx1.");
    }

    @Test
    void theStrictDecoderRefusesWhatIsNotTheCanonicalText() {
        String ok = null;
        for (JsonNode x : vectors.get("cases")) if (x.get("id").asText().equals("act-production")) ok = x.get("token").asText();
        assertThat(Envelope.decode(ok)).isNotNull();
        assertThat(Envelope.decode(ok.replace("cbx1.", "cba1."))).as("l'ancien préfixe n'est plus lu").isNull();
        assertThat(Envelope.decode(ok + ".x")).isNull();
        assertThat(Envelope.decode("cbx1.")).isNull();
        String[] p = ok.split("\\.");
        String text = new String(Base64.getUrlDecoder().decode(p[1]), java.nio.charset.StandardCharsets.UTF_8);
        for (String bad : List.of(text + "\n", text.replace("seq=", "seq= "), text.replace("\nkid=", "\n\nkid="), text.replace("type=activation", "type=Activation"))) {
            String t = "cbx1." + Base64.getUrlEncoder().withoutPadding().encodeToString(bad.getBytes(java.nio.charset.StandardCharsets.UTF_8)) + "." + p[2];
            assertThat(WireActivation.decode(t)).as(bad.substring(0, Math.min(40, bad.length()))).isNull();
        }
    }
}
