package castbridge.server.licenses;

import static org.assertj.core.api.Assertions.assertThat;

import castbridge.server.licenses.ActivationSigner.SignerScope;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The Java side of tools/activation/rental-vectors.json (docs/RENTAL-LOTS.md 8 and 10), the file the Kotlin core, the desk tool and tools/activation/verify_vectors.py also replay.
 * Replayed here: {@code build-activation} (the expected token parses, rebuilds byte for byte, is accepted by {@link EnvelopeVerifier}, carries the requested rentals within the
 * bounds; the refused requests are refused by the same bounds or by the scope of the signing key), {@code rental-state} (every token installs; the clock rules stay with the
 * Kotlin RentalEngine) and {@code old-device} (an unknown right kind is kept verbatim, grants nothing, the form stays canonical). NOT replayed: {@code box} and {@code seal} (no
 * HKDF / AES-GCM port in backend/src/main yet: work of w2-10), {@code lot-policy} (a TV rule). Also the implicit usage ceiling of a trial key without `usage`
 * (30 days, same rule as Activation.implicitUsageEnd) on hand-written values and on the server-issued vectors.
 */
class RentalVectorsTest {
    static final ObjectMapper JSON = new ObjectMapper();
    static final long DAY = 86_400_000L;
    static JsonNode vectors;
    static final Map<String, Set<SignerScope>> SCOPES = new HashMap<>();
    static final Map<String, byte[]> PUBLIC = new HashMap<>();

    @BeforeAll
    static void load() throws IOException {
        vectors = JSON.readTree(Files.readString(Path.of("..", "tools", "activation", "rental-vectors.json")));
        assertThat(vectors.get("format").asText()).isEqualTo("castbridge-rental-vectors-v1");
        for (JsonNode k : vectors.get("keys")) {
            Set<SignerScope> s = EnumSet.noneOf(SignerScope.class);
            k.get("scopes").forEach(x -> s.add(SignerScope.valueOf(x.asText())));
            SCOPES.put(k.get("name").asText(), s);
            PUBLIC.put(k.get("name").asText(), new Ed25519PrivateKeyParameters(HexFormat.of().parseHex(k.get("seed").asText()), 0).generatePublicKey().getEncoded());
        }
    }

    static EnvelopeVerifier verifier(String keyName, Set<SignerScope> scopes) {
        byte[] pub = PUBLIC.get(keyName);
        var ring = new EnvelopeVerifier.Ring().add(new EnvelopeVerifier.TrustedKey(LicenseKeyring.kidOf(pub), pub, scopes));
        return new EnvelopeVerifier(ring, EnvelopeVerifier.Revocations.none(), new EnvelopeVerifier.SeqState(), "tv");
    }

    static List<String> rentalLines(WireActivation.Fields f) { return f.rights().stream().filter(WireActivation::isRental).toList(); }

    /** The check that every token of the vectors is a canonical activation accepted at [now] by a device of its own factors. */
    static WireActivation.Fields installs(String id, String token, String signer, long now) {
        WireActivation.Decoded d = WireActivation.decode(token);
        assertThat(d).as(id + ": decodes").isNotNull();
        assertThat(WireActivation.token(d.fields(), d.signature())).as(id + ": canonical form rebuilds byte for byte").isEqualTo(token);
        var r = verifier(signer, SCOPES.get(signer)).verifyActivation(token, d.fields().factors(), now);
        assertThat(r.reason()).as(id + ": accepted").isNull();
        return d.fields();
    }

    @Test
    void buildActivationTokensParseRebuildAndVerify_andRefusedRequestsAreOutOfBounds() {
        int accepted = 0, refused = 0;
        String validToken = null;
        for (JsonNode c : vectors.get("cases")) {
            if (!c.get("type").asText().equals("build-activation")) continue;
            String id = c.get("id").asText(), signer = c.get("signer").asText();
            JsonNode req = c.get("request"), exp = c.get("expect");
            if (exp.has("token")) {
                WireActivation.Fields f = installs(id, exp.get("token").asText(), signer, req.get("issuedAt").asLong());
                assertThat(f.kind()).isEqualTo("production");
                assertThat(f.license()).isEqualTo(req.get("license").asText());
                assertThat(f.k()).isEqualTo(DeviceIdentity.kFor(f.factors().size()));
                assertThat(rentalLines(f)).as(id + ": one rental line per requested rental").hasSize(req.get("rentals").size());
                for (String line : rentalLines(f)) assertThat(WireActivation.rentalBounds(line)).as(id + ": " + line).isNull();
                // the requested rentals appear in the signed lines (box excluded: only the TV can open it)
                for (JsonNode r : req.get("rentals")) {
                    String[] l = rentalLines(f).stream().map(x -> x.split("\\|", -1)).filter(x -> x[1].equals(r.get("product").asText())).findFirst().orElseThrow();
                    assertThat(l[3]).isEqualTo(r.get("startsAt").asText());
                    assertThat(l[4]).isEqualTo(r.has("period") ? r.get("period").asText() : r.get("startsAt").asText());
                    assertThat(l[5]).isEqualTo(r.get("days").asText());
                    assertThat(l[6]).isEqualTo(r.get("graceMs").asText());
                    assertThat(l[7]).isEqualTo(r.get("usage").asText());
                    assertThat(l[8]).isEqualTo(r.get("concurrent").asText());
                }
                if (validToken == null) validToken = exp.get("token").asText();
                accepted++;
            } else {
                assertThat(exp.get("refused").asBoolean()).as(id).isTrue();
                refused++;
            }
        }
        assertThat(accepted).isEqualTo(5);
        assertThat(refused).isEqualTo(6);
        // refused: out of bounds (the same bounds the verifier and the issuer apply)
        for (JsonNode c : vectors.get("cases")) {
            if (!c.get("type").asText().equals("build-activation") || !c.get("expect").has("refused")) continue;
            String id = c.get("id").asText(), signer = c.get("signer").asText();
            if (!SCOPES.get(signer).contains(SignerScope.ISSUE_PRODUCTION) && !SCOPES.get(signer).contains(SignerScope.REACTIVATE)) {
                // the key may not sign a production activation: the same token, trusted with the scopes of that key, is refused
                var f = WireActivation.decode(validToken).fields();
                var r = verifier("desk", SCOPES.get(signer)).verifyActivation(validToken, f.factors(), f.issuedAt());
                assertThat(r.reason()).as(id).isEqualTo(EnvelopeVerifier.Reason.KEY_NOT_ALLOWED);
                continue;
            }
            List<String> reasons = new ArrayList<>();
            for (JsonNode r : c.get("request").get("rentals")) {
                String line = "rental|" + r.get("product").asText() + "|" + String.join(",", toList(r.get("bundles"))) + "|" + r.get("startsAt").asText() + "|"
                        + (r.has("period") ? r.get("period").asText() : r.get("startsAt").asText()) + "|" + r.get("days").asText() + "|" + r.get("graceMs").asText() + "|"
                        + r.get("usage").asText() + "|" + r.get("concurrent").asText() + "|A";
                assertThat(WireActivation.rightLineOk(line)).as(id + ": well formed").isTrue();
                String why = WireActivation.rentalBounds(line);
                if (why != null) reasons.add(why);
            }
            assertThat(reasons).as(id + ": refused by the bounds").isNotEmpty();
        }
    }

    static List<String> toList(JsonNode a) {
        List<String> l = new ArrayList<>();
        a.forEach(x -> l.add(x.asText()));
        return l;
    }

    @Test
    void everyTokenOfTheRentalStateVectorsInstalls_andTheContractsAreTheRentalLines() {
        int n = 0;
        for (JsonNode c : vectors.get("cases")) {
            if (!c.get("type").asText().equals("rental-state")) continue;
            String id = c.get("id").asText();
            Set<String> contracts = new HashSet<>();
            for (JsonNode t : c.get("tokens")) {
                String token = t.asText();
                long issuedAt = WireActivation.decode(token).fields().issuedAt();
                for (String line : rentalLines(installs(id, token, "desk", issuedAt))) {
                    String[] f = line.split("\\|", -1);
                    contracts.add(f[1] + "@" + f[4]);
                }
            }
            for (JsonNode e : c.get("expect")) assertThat(contracts).as(id).contains(e.get("key").asText());
            n++;
        }
        assertThat(n).isEqualTo(22);
    }

    @Test
    void anUnknownRightKindIsKeptVerbatimAndGrantsNothing_theFormStaysCanonical() {
        int n = 0;
        for (JsonNode c : vectors.get("cases")) {
            if (!c.get("type").asText().equals("old-device")) continue;
            String id = c.get("id").asText();
            var f = installs(id, c.get("token").asText(), "desk", c.get("nowMs").asLong());
            Set<String> granted = new HashSet<>();
            for (String line : f.rights()) {
                String[] p = line.split("\\|", -1);
                if (p[0].equals("purchase")) for (String b : p[2].split(",")) if (!b.isEmpty()) granted.add(b);
            }
            assertThat(granted).as(id).containsExactlyInAnyOrderElementsOf(toList(c.get("expect").get("granted")));
            n++;
        }
        assertThat(n).isEqualTo(2);
        assertThat(WireActivation.rightLineOk("hologram|prod|a,b|1|2")).isTrue();
        assertThat(WireActivation.rightLineOk("Hologram|x")).isFalse();      // not a kind name
        assertThat(WireActivation.rightLineOk("purchase|bad id|a|1")).isFalse();      // a known kind stays strict
    }

    private static WireActivation.Fields fields(String kind, long issuedAt, long notBefore, List<String> rights) {
        return new WireActivation.Fields(kind, "tv", "0011223344556677", 1, "00112233445566778899aabbccddeeff", issuedAt, notBefore, notBefore + DAY, "lic-0001", "0123456789abcdef", 1,
                new java.util.EnumMap<>(DeviceIdentity.Factor.class), rights);
    }

    @Test
    void implicitUsageEndOfATrialKeyWithoutUsageIsThirtyDays_sameRuleAsKotlin() {
        long t0 = 1_800_000_000_000L;
        assertThat(WireActivation.implicitUsageEnd(fields("trial", t0, t0 - 3_600_000L, List.of()))).isEqualTo(t0 + 30 * DAY);
        assertThat(WireActivation.implicitUsageEnd(fields("trial", 0, t0, List.of()))).as("no issue time: notBefore").isEqualTo(t0 + 30 * DAY);
        assertThat(WireActivation.implicitUsageEnd(fields("trial", t0, t0, List.of("usage|duree|" + t0 + "|" + (t0 + DAY))))).as("an explicit usage right wins").isNull();
        assertThat(WireActivation.implicitUsageEnd(fields("trial", t0, t0, List.of("rental|essai|a|1|1|3|0|720|0|A")))).as("the essai window alone does not lift the cap").isEqualTo(t0 + 30 * DAY);
        assertThat(WireActivation.implicitUsageEnd(fields("production", t0, t0, List.of()))).as("production without usage is unlimited").isNull();
    }

    @Test
    void theServerIssuedTrialCarriesTheImplicitCeiling_theVerifierReportsIt() throws IOException {
        JsonNode v = JSON.readTree(Files.readString(Path.of("..", "tools", "activation", "server-issued.json")));
        JsonNode server = v.get("server");
        byte[] pub = Base64.getDecoder().decode(server.get("publicKey").asText());
        Set<SignerScope> scopes = EnumSet.noneOf(SignerScope.class);
        server.get("scopes").forEach(x -> scopes.add(SignerScope.valueOf(x.asText())));
        int n = 0;
        for (JsonNode c : v.get("cases")) {
            if (!c.get("type").asText().equals("activation")) continue;
            var ring = new EnvelopeVerifier.Ring().add(new EnvelopeVerifier.TrustedKey(server.get("kid").asText(), pub, scopes));
            var d = WireActivation.decode(c.get("token").asText());
            var r = new EnvelopeVerifier(ring, EnvelopeVerifier.Revocations.none(), new EnvelopeVerifier.SeqState(), c.get("subject").asText()).verifyActivation(c.get("token").asText(), d.fields().factors(), v.get("nowMs").asLong());
            assertThat(r.reason()).as(c.get("id").asText()).isNull();
            if (c.get("expect").get("kind").asText().equals("trial")) assertThat(r.usageEnd()).isEqualTo(d.fields().issuedAt() + 30 * DAY);
            else assertThat(r.usageEnd()).isNull();
            n++;
        }
        assertThat(n).isEqualTo(5);
    }
}
