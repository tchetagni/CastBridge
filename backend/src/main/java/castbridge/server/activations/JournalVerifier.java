package castbridge.server.activations;

import castbridge.server.licenses.Envelope;
import castbridge.server.licenses.Hashing;
import castbridge.server.licenses.LicenseKeyring;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * The journal of an offline tool, as a PURE function (no database, no clock): the signed {@code cbx1} envelope of type {@code journal} is decoded by the one codec of the
 * format, the key must be in the ring and not revoked, the signature must hold, then the chain is checked against what is already known (a {@link State}).
 * Docs: docs/ACTIVATION-TRACKING.md. Vectors: tools/activation/journal-vectors.json (consumed by the Kotlin tools).
 *
 * <pre>
 * body:  tool=&lt;desk|phone|agent|…&gt;  app=&lt;version&gt;  from=&lt;n&gt;  to=&lt;n&gt;  prev=&lt;64 hex&gt;  then one line per entry: e=&lt;n&gt;|&lt;at ms&gt;|&lt;type&gt;|&lt;name=value&gt;|…
 * entry hash h(n) = SHA-256( h(n-1) + "|" + the entry line ),  h(0) = 64 zeros,  the batch says prev = h(from-1)
 * </pre>
 *
 * <p>Outcomes: {@code OK} (new entries accepted, maybe a hole opened), {@code DUPLICATE} (everything already known, identical), {@code REJECT} (malformed, too big, batch
 * number going back: nothing is kept), {@code QUARANTINE} (unknown or revoked key, bad signature, rewritten entry, broken chain: kept as proof, never applied).
 */
public final class JournalVerifier {
    public static final int MAX_ENTRIES = 500;
    public static final int MAX_TOKEN_CHARS = 180_000;
    public static final String GENESIS = Chains.GENESIS;
    public static final Set<String> TYPES = Set.of("issue", "compact", "deliver", "command", "license", "revoke", "transfer", "refused");
    private static final Pattern HEX64 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern NAME = Pattern.compile("[a-z][A-Za-z0-9]{0,23}");
    private static final Pattern TOOL = Pattern.compile("[a-z]{2,10}");

    public enum Status { OK, DUPLICATE, QUARANTINE, REJECT }

    public enum Reason { MALFORMED, TOO_BIG, UNKNOWN_KEY, REVOKED_KEY, BAD_SIGNATURE, SEQ_NOT_INCREASING, REWRITTEN, PREV_MISMATCH }

    /** Entries from..to never received. */
    public record Gap(long from, long to) {}

    public record Entry(long n, long atMs, String type, Map<String, String> fields, String line, String prev, String hash) {}

    public record Batch(String kid, long seq, String tool, String app, long from, long to, String prev, List<Entry> entries, String sha256) {}

    /**
     * @param fresh the entries not known before (to project), in order
     */
    public record Verdict(Status status, Reason reason, String kid, int accepted, int duplicate, Gap gap, Batch batch, List<Entry> fresh) {
        static Verdict no(Status s, Reason r, String kid, Batch b) { return new Verdict(s, r, kid, 0, 0, null, b, List.of()); }
    }

    /** What is already known about the journals of the tools. */
    public interface State {
        long lastBatchSeq(String kid);

        /** Highest entry number known for this tool (0: none). */
        long maxEntry(String kid);

        /** Hash of entry n of this tool, null if not received. */
        String entryHash(String kid, long n);

        /** The prev hash recorded with entry n, null if not received. */
        String entryPrev(String kid, long n);
    }

    /** In-memory state, for the vectors and the tools. */
    public static final class MemoryState implements State {
        private final Map<String, Long> seq = new HashMap<>();
        private final Map<String, Map<Long, String[]>> entries = new HashMap<>();

        @Override
        public long lastBatchSeq(String kid) { return seq.getOrDefault(kid, 0L); }

        @Override
        public long maxEntry(String kid) { return entries.getOrDefault(kid, Map.of()).keySet().stream().mapToLong(Long::longValue).max().orElse(0L); }

        @Override
        public String entryHash(String kid, long n) {
            String[] e = entries.getOrDefault(kid, Map.of()).get(n);
            return e == null ? null : e[1];
        }

        @Override
        public String entryPrev(String kid, long n) {
            String[] e = entries.getOrDefault(kid, Map.of()).get(n);
            return e == null ? null : e[0];
        }

        /** Remembers what an accepted verdict brought. */
        public void apply(Verdict v) {
            if (v.status() != Status.OK) return;
            for (Entry e : v.fresh()) entries.computeIfAbsent(v.kid(), k -> new HashMap<>()).put(e.n(), new String[] {e.prev(), e.hash()});
            seq.merge(v.kid(), v.batch().seq(), Math::max);
        }
    }

    private final Function<String, byte[]> keyOf;
    private final Predicate<String> revoked;

    /**
     * @param keyOf   raw 32-byte Ed25519 public key of a trusted tool by kid, or null
     * @param revoked true for a revoked kid
     */
    public JournalVerifier(Function<String, byte[]> keyOf, Predicate<String> revoked) {
        this.keyOf = keyOf;
        this.revoked = revoked;
    }

    public Verdict verify(String token, State state) {
        if (token == null || token.length() > MAX_TOKEN_CHARS) return Verdict.no(Status.REJECT, Reason.TOO_BIG, null, null);
        Envelope env = Envelope.decode(token);
        if (env == null || !env.type().equals("journal") || env.target().kind() != Envelope.Target.Kind.ANY) return Verdict.no(Status.REJECT, Reason.MALFORMED, null, null);
        String kid = env.kid();
        Batch b;
        try {
            b = parse(env, token);
        } catch (TooBig e) {
            return Verdict.no(Status.REJECT, Reason.TOO_BIG, kid, null);
        } catch (RuntimeException e) {
            return Verdict.no(Status.REJECT, Reason.MALFORMED, kid, null);
        }
        byte[] pub = keyOf.apply(kid);
        if (pub == null) return Verdict.no(Status.QUARANTINE, Reason.UNKNOWN_KEY, kid, b);
        if (revoked.test(kid)) return Verdict.no(Status.QUARANTINE, Reason.REVOKED_KEY, kid, b);
        if (!signatureOk(pub, env)) return Verdict.no(Status.QUARANTINE, Reason.BAD_SIGNATURE, kid, b);

        long max = state.maxEntry(kid);
        List<Entry> fresh = new ArrayList<>();
        int known = 0;
        for (Entry e : b.entries()) {
            String h = state.entryHash(kid, e.n());
            if (h == null) fresh.add(e);
            else if (h.equals(e.hash())) known++;
            else return Verdict.no(Status.QUARANTINE, Reason.REWRITTEN, kid, b);
        }
        // neighbours: what comes just before and just after must fit when it is known
        long before = b.from() - 1;
        String beforeHash = before == 0 ? GENESIS : state.entryHash(kid, before);
        if (beforeHash != null && !beforeHash.equals(b.prev())) return Verdict.no(Status.QUARANTINE, Reason.PREV_MISMATCH, kid, b);
        String afterPrev = state.entryPrev(kid, b.to() + 1);
        if (afterPrev != null && !afterPrev.equals(b.entries().get(b.entries().size() - 1).hash())) return Verdict.no(Status.QUARANTINE, Reason.PREV_MISMATCH, kid, b);
        if (fresh.isEmpty()) return new Verdict(Status.DUPLICATE, null, kid, 0, known, null, b, List.of());
        // new entries beyond everything known need a batch number that goes up (a batch that fills a hole may be older)
        boolean extends_ = fresh.stream().anyMatch(e -> e.n() > max);
        if (extends_ && b.seq() <= state.lastBatchSeq(kid)) return Verdict.no(Status.REJECT, Reason.SEQ_NOT_INCREASING, kid, b);
        Gap gap = b.from() > max + 1 ? new Gap(max + 1, b.from() - 1) : null;
        return new Verdict(Status.OK, null, kid, fresh.size(), known, gap, b, fresh);
    }

    private static final class TooBig extends RuntimeException {}

    private static Batch parse(Envelope env, String token) {
        List<String> body = env.body();
        if (body.size() < 6) throw new IllegalArgumentException("body");
        String tool = value(body.get(0), "tool"), app = value(body.get(1), "app"), prevS = value(body.get(4), "prev");
        long from = Long.parseLong(value(body.get(2), "from")), to = Long.parseLong(value(body.get(3), "to"));
        if (!TOOL.matcher(tool).matches() || app.isEmpty() || app.length() > 32 || !HEX64.matcher(prevS).matches() || from < 1 || to < from) throw new IllegalArgumentException("header");
        if (body.size() - 5 > MAX_ENTRIES) throw new TooBig();
        if (to - from + 1 != body.size() - 5) throw new IllegalArgumentException("count");
        List<Entry> entries = new ArrayList<>();
        String h = prevS;
        long expected = from;
        for (String line : body.subList(5, body.size())) {
            if (!line.startsWith("e=") || line.length() > 2000) throw new IllegalArgumentException("entry");
            String[] p = line.substring(2).split("\\|", -1);
            if (p.length < 3) throw new IllegalArgumentException("entry");
            long n = Long.parseLong(p[0]), at = Long.parseLong(p[1]);
            if (n != expected || at < 0 || !TYPES.contains(p[2])) throw new IllegalArgumentException("entry");
            expected++;
            Map<String, String> fields = new LinkedHashMap<>();
            for (int i = 3; i < p.length; i++) {
                int eq = p[i].indexOf('=');
                if (eq < 1 || !NAME.matcher(p[i].substring(0, eq)).matches() || fields.put(p[i].substring(0, eq), p[i].substring(eq + 1)) != null) throw new IllegalArgumentException("field");
            }
            String fp = fields.get("fp");
            if (fp != null && !HEX64.matcher(fp).matches()) throw new IllegalArgumentException("fp");
            String entryPrev = h;
            h = Hashing.sha256Hex(h + "|" + line);
            entries.add(new Entry(n, at, p[2], fields, line, entryPrev, h));
        }
        return new Batch(env.kid(), env.seq(), tool, app, from, to, prevS, entries, Hashing.sha256Hex(token.getBytes(StandardCharsets.UTF_8)));
    }

    private static String value(String line, String key) {
        if (!line.startsWith(key + "=")) throw new IllegalArgumentException(key);
        return line.substring(key.length() + 1);
    }

    private static boolean signatureOk(byte[] pub, Envelope e) {
        try {
            return LicenseKeyring.verify(pub, e.payload().getBytes(StandardCharsets.UTF_8), Base64.getDecoder().decode(e.signature()));
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
