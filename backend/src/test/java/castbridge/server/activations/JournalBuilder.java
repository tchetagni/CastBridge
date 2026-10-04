package castbridge.server.activations;

import castbridge.server.licenses.Envelope;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;

/**
 * Builds a signed {@code cbx1} envelope of type {@code journal} exactly as docs/ACTIVATION-TRACKING.md describes it (independent of the server code,
 * written from the design: entry hash h(n) = SHA-256(h(n-1) + "|" + entry line), lines tool/app/from/to/prev then one "e=" line per entry).
 */
final class JournalBuilder {
    static final String GENESIS = "0".repeat(64);

    private final Ed25519PrivateKeyParameters key;
    private final long seq;
    private final String tool;
    private final long issuedAt;
    private final long from;
    private String prev = GENESIS;
    private final List<String> lines = new ArrayList<>();
    private String nonce;

    JournalBuilder(Ed25519PrivateKeyParameters key, long seq, String tool, long issuedAtMs, long from) {
        this.key = key;
        this.seq = seq;
        this.tool = tool;
        this.issuedAt = issuedAtMs;
        this.from = from;
    }

    JournalBuilder prev(String hex) {
        this.prev = hex;
        return this;
    }

    JournalBuilder nonce(String hex16) {
        this.nonce = hex16;
        return this;
    }

    /** Appends a raw entry line (used to build malformed journals). */
    JournalBuilder entryRaw(String line) {
        lines.add(line);
        return this;
    }

    /** Appends an entry; {@code kv} are "name=value" strings. */
    JournalBuilder entry(long atMs, String type, String... kv) {
        long n = from + lines.size();
        lines.add("e=" + n + "|" + atMs + "|" + type + (kv.length == 0 ? "" : "|" + String.join("|", kv)));
        return this;
    }

    /** Hash chain value after the entry at {@code index} (0-based) of this batch, given the batch's prev. */
    String hashAt(int index) {
        String h = prev;
        for (int i = 0; i <= index; i++) h = hash(h, lines.get(i));
        return h;
    }

    String lastHash() { return hashAt(lines.size() - 1); }

    static String hash(String prev, String line) { return ActTestBase.sha256(prev + "|" + line); }

    String build() { return build(false); }

    String build(boolean badSignature) {
        List<String> body = new ArrayList<>(List.of("tool=" + tool, "app=0.14.12", "from=" + from, "to=" + (from + lines.size() - 1), "prev=" + prev));
        body.addAll(lines);
        String kid = ActTestBase.kid(key);
        String nonce = this.nonce != null ? this.nonce : ActTestBase.rnd32().substring(0, 16);
        long expires = issuedAt + 365L * 86_400_000L;
        String payload = Envelope.payload("journal", kid, seq, nonce, issuedAt, issuedAt, expires, Envelope.Target.ANY, body);
        Ed25519Signer s = new Ed25519Signer();
        s.init(true, key);
        byte[] b = payload.getBytes(StandardCharsets.UTF_8);
        s.update(b, 0, b.length);
        byte[] sig = s.generateSignature();
        if (badSignature) sig[0] ^= 1;
        return new Envelope("journal", kid, seq, nonce, issuedAt, issuedAt, expires, Envelope.Target.ANY, body, Base64.getEncoder().encodeToString(sig)).token();
    }

    /** The "issue" entry of a token as a tool writes it (every field is read from the token itself). */
    static String[] issueFields(String token, String via) {
        var f = ActTestBase.fields(token);
        String usageTo = f.rights().stream().filter(r -> r.startsWith("usage|")).map(r -> r.split("\\|")[3]).findFirst().orElse(null);
        Long implicit = castbridge.server.licenses.WireActivation.implicitUsageEnd(f);
        String dev = castbridge.server.licenses.DeviceIdentity.code(f.factors());
        List<String> kv = new ArrayList<>(List.of("fp=" + ActTestBase.sha256(token), "form=envelope", "kind=" + f.kind(), "subject=" + f.subject(), "license=" + f.license(), "seat=" + f.seat(),
                "device=" + dev, "k=" + f.k(), "nonce=" + f.nonce(), "aseq=" + f.seq(), "issuedAt=" + f.issuedAt(), "expiresAt=" + f.notAfter(),
                usageTo != null ? "usageTo=" + usageTo : implicit != null ? "usageTo=" + implicit : "unlimited=1", "super=0", "rights=-"));
        if (via != null) kv.add("registry=" + via);
        return kv.toArray(new String[0]);
    }
}
