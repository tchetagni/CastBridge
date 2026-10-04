package castbridge.server.activations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import castbridge.server.licenses.LicenseKeyring;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.junit.jupiter.api.Test;

/**
 * {@code tools/activation/journal-vectors.json} ({@code castbridge-journal-vectors-v1}): produced HERE with test keys derived from public texts, with the expected
 * result of every step written by hand (never computed by the verifier), then CONSUMED the way the Kotlin tools (w23-03) will: the committed file is parsed and each
 * step is run through {@link JournalVerifier}. Run with -Dcastbridge.vectors.write=true to regenerate the file.
 */
@org.junit.jupiter.api.TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
class JournalVectorsTest {
    static final Path FILE = Path.of("..", "tools", "activation", "journal-vectors.json");
    static final ObjectMapper JSON = new ObjectMapper();
    static final long T0 = 1_759_600_000_000L;

    static Ed25519PrivateKeyParameters keyOf(String name) {
        byte[] seed = castbridge.server.licenses.Hashing.sha256(("castbridge-journal-vectors-v1|" + name).getBytes(StandardCharsets.UTF_8));
        return new Ed25519PrivateKeyParameters(seed, 0);
    }

    static String pub(Ed25519PrivateKeyParameters k) { return Base64.getEncoder().encodeToString(k.generatePublicKey().getEncoded()); }

    static String kid(Ed25519PrivateKeyParameters k) { return LicenseKeyring.kidOf(k.generatePublicKey().getEncoded()); }

    static String nonce(String name, int step) { return ActTestBase.sha256("nonce|" + name + "|" + step).substring(0, 16); }

    private final Ed25519PrivateKeyParameters desk = keyOf("desk"), phone = keyOf("phone"), revoked = keyOf("revoked"), rogue = keyOf("rogue");

    private JournalBuilder b(String name, int step, Ed25519PrivateKeyParameters k, long seq, long from) {
        return new JournalBuilder(k, seq, k == phone ? "phone" : "desk", T0 + step * 1000L, from).nonce(nonce(name, step));
    }

    private static final String FP1 = "a".repeat(64), FP2 = "b".repeat(64), FP3 = "c".repeat(64);
    /** A device whose fingerprints are derived from a public text: the same bytes on every run. */
    static ActTestBase.Dev detDev(String name) {
        Map<castbridge.server.licenses.DeviceIdentity.Factor, String> m = new java.util.EnumMap<>(castbridge.server.licenses.DeviceIdentity.Factor.class);
        for (var f : castbridge.server.licenses.DeviceIdentity.Factor.values()) m.put(f, ActTestBase.sha256("vector-device|" + name + "|" + f.name()).substring(0, 32));
        return new ActTestBase.Dev(m);
    }

    /** A signed trial activation with every field fixed (deterministic Ed25519 signature). */
    String detToken(String name, Ed25519PrivateKeyParameters k) {
        var d = detDev(name);
        var f = new castbridge.server.licenses.WireActivation.Fields("trial", "tv", kid(k), T0, ActTestBase.sha256("det|" + name).substring(0, 16), T0, T0, T0 + 48 * 3_600_000L,
                "trial", castbridge.server.licenses.WireActivation.defaultSeat("trial", d.fp()), d.k(), d.fp(), java.util.List.of());
        return castbridge.server.licenses.WireActivation.token(f, ActTestBase.sign(k, castbridge.server.licenses.WireActivation.payload(f)));
    }

    private final String DEV = detDev("a").code();

    private ObjectNode step(String token, String status, String reason, int accepted, int duplicate, Long gapFrom, Long gapTo, long maxN) {
        ObjectNode s = JSON.createObjectNode();
        s.put("token", token);
        ObjectNode e = s.putObject("expect");
        e.put("status", status);
        if (reason == null) e.putNull("reason"); else e.put("reason", reason);
        e.put("accepted", accepted);
        e.put("duplicate", duplicate);
        if (gapFrom == null) e.putNull("gapFrom"); else e.put("gapFrom", gapFrom);
        if (gapTo == null) e.putNull("gapTo"); else e.put("gapTo", gapTo);
        e.put("maxN", maxN);
        return s;
    }

    private ObjectNode caze(String name, String why, ObjectNode... steps) {
        ObjectNode c = JSON.createObjectNode();
        c.put("name", name);
        c.put("why", why);
        ArrayNode a = c.putArray("steps");
        for (ObjectNode s : steps) a.add(s);
        return c;
    }

    private JsonNode generate() {
        ObjectNode root = JSON.createObjectNode();
        root.put("format", "castbridge-journal-vectors-v1");
        root.put("note", "Test keys derived from the public text castbridge-journal-vectors-v1|<name> (seed = SHA-256 of that text). Entry hash h(n) = SHA-256(h(n-1) + '|' + entry line), h(0) = 64 zeros. Never a real key.");
        ArrayNode keys = root.putArray("keys");
        for (var e : new LinkedHashMap<>(Map.of("desk", desk)).entrySet()) addKey(keys, e.getKey(), e.getValue());
        addKey(keys, "phone", phone);
        addKey(keys, "revoked", revoked);
        addKey(keys, "rogue", rogue);
        root.putArray("trustedNames").add("desk").add("phone").add("revoked");
        root.putArray("revokedNames").add("revoked");
        ArrayNode cases = root.putArray("cases");

        // every type of entry
        var all = b("all-types", 0, desk, 1, 1)
                .entry(T0 + 1, "issue", JournalBuilder.issueFields(detToken("a", desk), null))
                .entry(T0 + 2, "compact", "fp=" + FP1, "form=compact", "kind=trial", "device=" + DEV, "windowStartHour=6000", "set=0")
                .entry(T0 + 3, "deliver", "fp=" + FP1, "way=bt", "tv=ok")
                .entry(T0 + 4, "command", "power=open_all", "action=-", "bundles=2", "lots=-", "days=30", "clamped=0", "device=" + DEV, "challenge=0a1b2c3d", "result=ok")
                .entry(T0 + 5, "license", "registry=0123456789abcdef")
                .entry(T0 + 6, "revoke", "registry=0123456789abcdee", "target=seat")
                .entry(T0 + 7, "transfer", "registry=0123456789abcdec")
                .entry(T0 + 8, "refused", "reason=quota", "device=" + DEV);
        cases.add(caze("valid-batch-every-entry-type", "one batch with an entry of each of the eight types", step(all.build(), "OK", null, 8, 0, null, null, 8)));

        var c1 = b("two-batches", 1, desk, 1, 1).entry(T0, "deliver", "fp=" + FP1, "way=qr", "tv=-").entry(T0 + 1, "deliver", "fp=" + FP2, "way=qr", "tv=-");
        var c2 = b("two-batches", 2, desk, 2, 3).prev(c1.lastHash()).entry(T0 + 2, "deliver", "fp=" + FP3, "way=text", "tv=-").entry(T0 + 3, "refused", "reason=x", "device=" + DEV);
        cases.add(caze("two-chained-batches", "the second batch continues the chain: prev is the last hash of the first",
                step(c1.build(), "OK", null, 2, 0, null, null, 2), step(c2.build(), "OK", null, 2, 0, null, null, 4)));

        var r1 = b("replay", 1, desk, 1, 1).entry(T0, "deliver", "fp=" + FP1, "way=bt", "tv=ok").entry(T0 + 1, "deliver", "fp=" + FP2, "way=bt", "tv=ok");
        String r1t = r1.build();
        cases.add(caze("replay-same-batch", "the same signed batch sent twice changes nothing",
                step(r1t, "OK", null, 2, 0, null, null, 2), step(r1t, "DUPLICATE", null, 0, 2, null, null, 2)));

        var g1 = b("gap", 1, desk, 1, 1).entry(T0, "deliver", "fp=" + FP1, "way=bt", "tv=ok").entry(T0 + 1, "deliver", "fp=" + FP2, "way=bt", "tv=ok");
        var g2 = b("gap", 2, desk, 3, 5).prev("d".repeat(64)).entry(T0 + 2, "deliver", "fp=" + FP3, "way=bt", "tv=ok").entry(T0 + 3, "refused", "reason=x", "device=" + DEV);
        cases.add(caze("gap-between-batches", "entries 3 and 4 were never received: the later batch is kept and the hole is reported",
                step(g1.build(), "OK", null, 2, 0, null, null, 2), step(g2.build(), "OK", null, 2, 0, 3L, 4L, 6)));

        var f1 = b("gap-filled", 1, desk, 1, 1).entry(T0, "deliver", "fp=" + FP1, "way=bt", "tv=ok").entry(T0 + 1, "deliver", "fp=" + FP2, "way=bt", "tv=ok");
        var f2 = b("gap-filled", 2, desk, 2, 3).prev(f1.lastHash()).entry(T0 + 2, "deliver", "fp=" + FP3, "way=bt", "tv=ok").entry(T0 + 3, "refused", "reason=x", "device=" + DEV);
        var f3 = b("gap-filled", 3, desk, 3, 5).prev(f2.lastHash()).entry(T0 + 4, "deliver", "fp=" + FP1, "way=qr", "tv=-");
        // f3 arrives before f2 (with the right prev): a hole 3-4 opens, then f2 fills it (a lower seq is accepted because it fills a hole)
        cases.add(caze("gap-filled-later", "the missing batch arrives afterwards and fills the hole",
                step(f1.build(), "OK", null, 2, 0, null, null, 2), step(f3.build(), "OK", null, 1, 0, 3L, 4L, 5), step(f2.build(), "OK", null, 2, 0, null, null, 5)));

        // audit w23-01 (mutation M10): the batch that FILLS a hole must also fit the batch AFTER it (its last hash is the prev of the next one)
        var n1 = b("gap-fill-next", 1, desk, 1, 1).entry(T0, "deliver", "fp=" + FP1, "way=bt", "tv=ok").entry(T0 + 1, "deliver", "fp=" + FP2, "way=bt", "tv=ok");
        var n3 = b("gap-fill-next", 3, desk, 3, 5).prev("d".repeat(64)).entry(T0 + 4, "deliver", "fp=" + FP3, "way=bt", "tv=ok").entry(T0 + 5, "refused", "reason=x", "device=" + DEV);
        var n2 = b("gap-fill-next", 2, desk, 2, 3).prev(n1.lastHash()).entry(T0 + 2, "deliver", "fp=" + FP3, "way=bt", "tv=ok").entry(T0 + 3, "refused", "reason=x", "device=" + DEV);
        cases.add(caze("gap-fill-next-mismatch", "the batch that fills the hole 3-4 does not end on the hash the next batch (5-6) says it follows: quarantine",
                step(n1.build(), "OK", null, 2, 0, null, null, 2), step(n3.build(), "OK", null, 2, 0, 3L, 4L, 6), step(n2.build(), "QUARANTINE", "PREV_MISMATCH", 0, 0, null, null, 6)));

        var w1 = b("rewrite", 1, desk, 1, 1).entry(T0, "deliver", "fp=" + FP1, "way=bt", "tv=ok").entry(T0 + 1, "deliver", "fp=" + FP2, "way=bt", "tv=ok").entry(T0 + 2, "deliver", "fp=" + FP3, "way=bt", "tv=ok");
        var w2 = b("rewrite", 2, desk, 2, 3).prev(w1.hashAt(1)).entry(T0 + 2, "deliver", "fp=" + FP3, "way=bt", "tv=-");
        cases.add(caze("rewritten-entry", "same entry number, different content: the batch goes to quarantine",
                step(w1.build(), "OK", null, 3, 0, null, null, 3), step(w2.build(), "QUARANTINE", "REWRITTEN", 0, 0, null, null, 3)));

        var p1 = b("prev", 1, desk, 1, 1).entry(T0, "deliver", "fp=" + FP1, "way=bt", "tv=ok").entry(T0 + 1, "deliver", "fp=" + FP2, "way=bt", "tv=ok");
        var p2 = b("prev", 2, desk, 2, 3).prev("e".repeat(64)).entry(T0 + 2, "deliver", "fp=" + FP3, "way=bt", "tv=ok");
        cases.add(caze("prev-mismatch", "the batch claims to follow another entry than the last one known: quarantine",
                step(p1.build(), "OK", null, 2, 0, null, null, 2), step(p2.build(), "QUARANTINE", "PREV_MISMATCH", 0, 0, null, null, 2)));

        var u = b("unknown-key", 1, rogue, 1, 1).entry(T0, "deliver", "fp=" + FP1, "way=bt", "tv=ok");
        cases.add(caze("unknown-key", "signed by a key outside the ring of the server", step(u.build(), "QUARANTINE", "UNKNOWN_KEY", 0, 0, null, null, 0)));

        var rv = b("revoked-key", 1, revoked, 1, 1).entry(T0, "deliver", "fp=" + FP1, "way=bt", "tv=ok");
        cases.add(caze("revoked-key", "signed by a trusted key that has been revoked", step(rv.build(), "QUARANTINE", "REVOKED_KEY", 0, 0, null, null, 0)));

        var bs = b("bad-signature", 1, desk, 1, 1).entry(T0, "deliver", "fp=" + FP1, "way=bt", "tv=ok");
        cases.add(caze("bad-signature", "one bit of the signature flipped", step(bs.build(true), "QUARANTINE", "BAD_SIGNATURE", 0, 0, null, null, 0)));

        var s1 = b("seq", 1, desk, 5, 1).entry(T0, "deliver", "fp=" + FP1, "way=bt", "tv=ok").entry(T0 + 1, "deliver", "fp=" + FP2, "way=bt", "tv=ok");
        var s2 = b("seq", 2, desk, 4, 3).prev(s1.lastHash()).entry(T0 + 2, "deliver", "fp=" + FP3, "way=bt", "tv=ok");
        cases.add(caze("batch-number-goes-back", "new entries under a batch number lower than the last accepted one are refused",
                step(s1.build(), "OK", null, 2, 0, null, null, 2), step(s2.build(), "REJECT", "SEQ_NOT_INCREASING", 0, 0, null, null, 2)));

        var m = b("not-dense", 1, desk, 1, 1).entry(T0, "deliver", "fp=" + FP1, "way=bt", "tv=ok").entryRaw("e=3|" + (T0 + 1) + "|deliver|fp=" + FP2 + "|way=bt|tv=ok");
        cases.add(caze("entry-numbers-not-dense", "entry numbers 1 then 3 inside one batch", step(m.build(), "REJECT", "MALFORMED", 0, 0, null, null, 0)));

        cases.add(caze("not-a-journal", "an activation envelope is not a journal",
                step(detToken("a", desk), "REJECT", "MALFORMED", 0, 0, null, null, 0), step("garbage", "REJECT", "MALFORMED", 0, 0, null, null, 0)));

        var big = b("too-big", 1, desk, 1, 1);
        for (int i = 0; i < 501; i++) big.entry(T0 + i, "refused", "reason=x", "device=" + DEV);
        cases.add(caze("more-than-500-entries", "a batch holds at most 500 entries", step(big.build(), "REJECT", "TOO_BIG", 0, 0, null, null, 0)));
        return root;
    }

    private void addKey(ArrayNode keys, String name, Ed25519PrivateKeyParameters k) {
        ObjectNode n = keys.addObject();
        n.put("name", name);
        n.put("seed", HexFormat.of().formatHex(castbridge.server.licenses.Hashing.sha256(("castbridge-journal-vectors-v1|" + name).getBytes(StandardCharsets.UTF_8))));
        n.put("publicKey", pub(k));
        n.put("kid", kid(k));
    }

    @Test
    @org.junit.jupiter.api.Order(1)
    void theCommittedFileIsTheOneThisTestProduces() throws IOException {
        String generated = JSON.writerWithDefaultPrettyPrinter().writeValueAsString(generate()) + "\n";
        if (System.getProperty("castbridge.vectors.write") != null) Files.writeString(FILE, generated);
        assertTrue(Files.exists(FILE), "run once with -Dcastbridge.vectors.write=true");
        assertEquals(generated, Files.readString(FILE), "tools/activation/journal-vectors.json is stale: regenerate with -Dcastbridge.vectors.write=true");
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    void everyStepOfTheCommittedFileGivesTheExpectedResult() throws IOException {
        JsonNode root = JSON.readTree(Files.readString(FILE));
        assertEquals("castbridge-journal-vectors-v1", root.get("format").asText());
        Map<String, byte[]> ring = new HashMap<>();
        java.util.Set<String> revokedKids = new java.util.HashSet<>();
        Map<String, String> kidOfName = new HashMap<>();
        for (JsonNode k : root.get("keys")) kidOfName.put(k.get("name").asText(), k.get("kid").asText());
        for (JsonNode k : root.get("keys")) {
            String name = k.get("name").asText();
            boolean trusted = false;
            for (JsonNode t : root.get("trustedNames")) trusted |= t.asText().equals(name);
            if (trusted) ring.put(k.get("kid").asText(), Base64.getDecoder().decode(k.get("publicKey").asText()));
        }
        for (JsonNode r : root.get("revokedNames")) revokedKids.add(kidOfName.get(r.asText()));
        int steps = 0;
        for (JsonNode c : root.get("cases")) {
            JournalVerifier verifier = new JournalVerifier(ring::get, revokedKids::contains);
            JournalVerifier.MemoryState state = new JournalVerifier.MemoryState();
            for (JsonNode s : c.get("steps")) {
                JournalVerifier.Verdict v = verifier.verify(s.get("token").asText(), state);
                JsonNode e = s.get("expect");
                String where = c.get("name").asText();
                assertEquals(e.get("status").asText(), v.status().name(), where + " status");
                assertEquals(e.get("reason").isNull() ? null : e.get("reason").asText(), v.reason() == null ? null : v.reason().name(), where + " reason");
                assertEquals(e.get("accepted").asInt(), v.accepted(), where + " accepted");
                assertEquals(e.get("duplicate").asInt(), v.duplicate(), where + " duplicate");
                assertEquals(e.get("gapFrom").isNull() ? null : e.get("gapFrom").asLong(), v.gap() == null ? null : v.gap().from(), where + " gapFrom");
                assertEquals(e.get("gapTo").isNull() ? null : e.get("gapTo").asLong(), v.gap() == null ? null : v.gap().to(), where + " gapTo");
                state.apply(v);
                assertEquals(e.get("maxN").asLong(), state.maxEntry(v.kid() == null ? "" : v.kid()), where + " maxN");
                steps++;
            }
        }
        assertTrue(steps >= 20);
        assertNotNull(root.get("cases"));
    }
}
