package castbridge.server.licenses;

import castbridge.server.licenses.DeviceIdentity.Factor;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * THE signed envelope {@code cbx1} (docs/ACTIVATION-FORMAT.md § 3): one codec for every signed message (activation, command, revocation, order); only the BODY differs
 * per type. Java port of {@code castbridge.core.owner.Envelope}: same canonical text, same token, same strict decoder (the text is rebuilt from the parsed fields and must be
 * identical byte for byte, or the token is refused). The signature is NOT checked here ({@link EnvelopeVerifier} does it).
 *
 * <pre>
 * castbridge-envelope-v1
 * type=&lt;activation|command|revocation|order|…&gt;
 * kid=&lt;16 hex&gt;  seq=&lt;n&gt;  nonce=&lt;8..64 hex&gt;  issuedAt=&lt;ms&gt;  notBefore=&lt;ms&gt;  expiresAt=&lt;ms&gt;
 * target=&lt;any|device|license:&lt;id&gt;|group:&lt;id&gt;&gt;   [k=&lt;n&gt; factor=&lt;TYPE&gt;|&lt;32 hex&gt; … when device]
 * --
 * &lt;body lines&gt;
 * </pre>
 */
public record Envelope(String type, String kid, long seq, String nonce, long issuedAt, long notBefore, long expiresAt, Target target, List<String> body, String signature) {
    public static final String FORMAT = "castbridge-envelope-v1";
    public static final String PREFIX = "cbx1";
    public static final String BODY_MARK = "--";
    public static final Pattern HEX = Pattern.compile("[0-9a-f]{8,64}");
    public static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");
    private static final Pattern FP = Pattern.compile("[0-9a-f]{32}");
    private static final Pattern TYPE = Pattern.compile("[a-z][a-z0-9-]{0,31}");

    /** Who the message is for: any device, one device (k of n factors must match), every device holding a seat of a licence, or a named group. */
    public record Target(Kind kind, String id, int k, Map<Factor, String> factors) {
        public enum Kind { ANY, DEVICE, LICENSE, GROUP }

        public static final Target ANY = new Target(Kind.ANY, null, 0, Map.of());

        public static Target device(int k, Map<Factor, String> factors) { return new Target(Kind.DEVICE, null, k, copy(factors)); }

        private static Map<Factor, String> copy(Map<Factor, String> m) {
            Map<Factor, String> c = new EnumMap<>(Factor.class);
            c.putAll(m);
            return c;
        }

        public static Target license(String id) { return new Target(Kind.LICENSE, id, 0, Map.of()); }

        public static Target group(String id) { return new Target(Kind.GROUP, id, 0, Map.of()); }
    }

    public String payload() { return payload(type, kid, seq, nonce, issuedAt, notBefore, expiresAt, target, body); }

    /** {@code cbx1.<payload base64url without padding>.<signature base64 with padding>} */
    public String token() {
        return PREFIX + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(payload().getBytes(StandardCharsets.UTF_8)) + "." + signature;
    }

    public Envelope withSignature(String sig) { return new Envelope(type, kid, seq, nonce, issuedAt, notBefore, expiresAt, target, body, sig); }

    public static String payload(String type, String kid, long seq, String nonce, long issuedAt, long notBefore, long expiresAt, Target target, List<String> body) {
        List<String> l = new ArrayList<>(List.of(FORMAT, "type=" + type, "kid=" + kid, "seq=" + seq, "nonce=" + nonce, "issuedAt=" + issuedAt, "notBefore=" + notBefore, "expiresAt=" + expiresAt));
        switch (target.kind()) {
            case ANY -> l.add("target=any");
            case DEVICE -> {
                l.add("target=device");
                l.add("k=" + target.k());
                Map<Factor, String> sorted = new EnumMap<>(Factor.class);
                sorted.putAll(target.factors());
                sorted.forEach((f, h) -> l.add("factor=" + f.name() + "|" + h));
            }
            case LICENSE -> l.add("target=license:" + target.id());
            case GROUP -> l.add("target=group:" + target.id());
        }
        l.add(BODY_MARK);
        l.addAll(body);
        return String.join("\n", l);
    }

    /** The envelope of a token, or null (the canonical text rebuilt from the parsed fields must equal the received one). The signature is not checked. */
    public static Envelope decode(String token) {
        try {
            String[] parts = token.trim().split("\\.", -1);
            if (parts.length != 3 || !parts[0].equals(PREFIX)) return null;
            String text = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            String[] lines = text.split("\n", -1);
            if (!lines[0].equals(FORMAT)) return null;
            String type = field(lines[1], "type");
            if (!TYPE.matcher(type).matches()) return null;
            String kid = field(lines[2], "kid");
            if (!HEX.matcher(kid).matches()) return null;
            long seq = Long.parseLong(field(lines[3], "seq"));
            if (seq < 0) return null;
            String nonce = field(lines[4], "nonce");
            if (!HEX.matcher(nonce).matches()) return null;
            long issuedAt = Long.parseLong(field(lines[5], "issuedAt")), notBefore = Long.parseLong(field(lines[6], "notBefore")), expiresAt = Long.parseLong(field(lines[7], "expiresAt"));
            int i = 9;
            String t = field(lines[8], "target");
            Target target;
            if (t.equals("any")) {
                target = Target.ANY;
            } else if (t.equals("device")) {
                int k = Integer.parseInt(field(lines[i++], "k"));
                Map<Factor, String> factors = new EnumMap<>(Factor.class);
                while (lines[i].startsWith("factor=")) {
                    String[] p = lines[i].substring(7).split("\\|", -1);
                    if (p.length != 2 || !FP.matcher(p[1]).matches()) return null;
                    factors.put(Factor.valueOf(p[0]), p[1]);
                    i++;
                }
                target = new Target(Target.Kind.DEVICE, null, k, factors);
            } else if (t.startsWith("license:") && ID.matcher(t.substring(8)).matches()) {
                target = Target.license(t.substring(8));
            } else if (t.startsWith("group:") && ID.matcher(t.substring(6)).matches()) {
                target = Target.group(t.substring(6));
            } else {
                return null;
            }
            if (!lines[i].equals(BODY_MARK)) return null;
            Envelope e = new Envelope(type, kid, seq, nonce, issuedAt, notBefore, expiresAt, target, new ArrayList<>(List.of(lines).subList(i + 1, lines.length)), parts[2]);
            return e.payload().equals(text) ? e : null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String field(String line, String key) {
        if (!line.startsWith(key + "=")) throw new IllegalArgumentException(key);
        return line.substring(key.length() + 1);
    }
}
