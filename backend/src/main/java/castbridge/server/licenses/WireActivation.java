package castbridge.server.licenses;

import castbridge.server.licenses.DeviceIdentity.Factor;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** The activation, type {@code activation} of the {@link Envelope} {@code cbx1} (docs/ACTIVATION-FORMAT.md § 3.3), plus a strict decoder (tests, inspection). */
public final class WireActivation {
    public static final String TYPE = "activation";
    public static final String PREFIX = Envelope.PREFIX;
    public static final String TRIAL_LICENSE = "trial";
    public static final long DAY_MS = 86_400_000L;
    public static final int MAX_WINDOW_DAYS = 366;
    public static final long MAX_OPEN_ALL_MS = 30 * DAY_MS;
    public static final Pattern ID = Envelope.ID;
    public static final Pattern HEX = Envelope.HEX;

    private WireActivation() {}

    /** @param notAfter the {@code expiresAt} of the envelope (end of the installation window); @param seq sequence number of the key */
    public record Fields(String kind, String subject, String kid, long seq, String nonce, long issuedAt, long notBefore, long notAfter, String license, String seat, int k,
                         Map<Factor, String> factors, List<String> rights) {}

    public record Decoded(Fields fields, Envelope envelope, byte[] signature) {
        public String text() { return envelope.payload(); }
    }

    /** The unsigned envelope of an activation (type {@code activation}, target = the device, body = kind, subject, licence, seat, sorted rights). */
    public static Envelope envelope(Fields f, String signature) {
        Map<Factor, String> sorted = new EnumMap<>(Factor.class);
        sorted.putAll(f.factors());
        List<String> body = new ArrayList<>(List.of("kind=" + f.kind(), "subject=" + f.subject(), "license=" + f.license(), "seat=" + f.seat()));
        List<String> rights = new ArrayList<>(f.rights());
        Collections.sort(rights); // by the bytes of the UTF-8 text: the rights are ASCII, so the String order is the byte order
        rights.forEach(r -> body.add("right=" + r));
        return new Envelope(TYPE, f.kid(), f.seq(), f.nonce(), f.issuedAt(), f.notBefore(), f.notAfter(), Envelope.Target.device(f.k(), sorted), body, signature);
    }

    public static String payload(Fields f) { return envelope(f, "").payload(); }

    /** cbx1.&lt;payload base64url without padding&gt;.&lt;signature base64 with padding&gt; */
    public static String token(Fields f, byte[] signature) { return envelope(f, Base64.getEncoder().encodeToString(signature)).token(); }

    /** Default seat identifier of a NEW seat: hex(SHA-256("castbridge-seat|" + license + "|" + hex(set hash))[0:8]). */
    public static String defaultSeat(String license, Map<Factor, String> factors) {
        byte[] h = Hashing.sha256(("castbridge-seat|" + license + "|" + java.util.HexFormat.of().formatHex(DeviceIdentity.setHash(factors))).getBytes(StandardCharsets.UTF_8));
        return java.util.HexFormat.of().formatHex(h, 0, 8);
    }

    /** A right line is valid when it has the right number of fields and valid identifiers (§ 3.3). */
    public static boolean rightLineOk(String line) {
        String[] f = line.split("\\|", -1);
        try {
            switch (f[0]) {
                case "purchase" -> {
                    if (f.length != 4 || !ID.matcher(f[1]).matches() || !idsOk(f[2])) return false;
                    Long.parseLong(f[3]);
                    return true;
                }
                case "subscription" -> {
                    if (f.length != 7 || !ID.matcher(f[1]).matches() || !idsOk(f[2]) || !(f[6].equals("0") || f[6].equals("1"))) return false;
                    Long.parseLong(f[3]);
                    Long.parseLong(f[4]);
                    Long.parseLong(f[5]);
                    return true;
                }
                case "openall" -> {
                    if (f.length != 4 || !ID.matcher(f[1]).matches()) return false;
                    Long.parseLong(f[2]);
                    Long.parseLong(f[3]);
                    return true;
                }
                default -> {
                    return false;
                }
            }
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean idsOk(String csv) {
        if (csv.isEmpty()) return true;
        for (String s : csv.split(",", -1)) if (!ID.matcher(s).matches()) return false;
        return true;
    }

    /** The activation view of an envelope of type {@code activation}, or null (wrong type, target not a device, body not canonical). */
    public static Fields fieldsOf(Envelope e) {
        try {
            if (!e.type().equals(TYPE) || e.target().kind() != Envelope.Target.Kind.DEVICE) return null;
            List<String> b = e.body();
            String seat = val(b.get(3), "seat"), license = val(b.get(2), "license");
            if (!HEX.matcher(seat).matches() || !ID.matcher(license).matches()) return null;
            List<String> rights = new ArrayList<>();
            for (String l : b.subList(4, b.size())) {
                if (!l.startsWith("right=") || !rightLineOk(l.substring(6))) return null;
                rights.add(l.substring(6));
            }
            String kind = val(b.get(0), "kind"), subject = val(b.get(1), "subject");
            if (!kind.equals("trial") && !kind.equals("production")) return null;
            if (!subject.equals("tv") && !subject.equals("phone")) return null;
            Fields f = new Fields(kind, subject, e.kid(), e.seq(), e.nonce(), e.issuedAt(), e.notBefore(), e.expiresAt(), license, seat, e.target().k(), e.target().factors(), rights);
            return envelope(f, e.signature()).payload().equals(e.payload()) ? f : null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /** Strict decoder: the envelope is rebuilt from the parsed fields and must be identical byte for byte, or the result is null. */
    public static Decoded decode(String token) {
        try {
            Envelope e = Envelope.decode(token);
            Fields f = e == null ? null : fieldsOf(e);
            return f == null ? null : new Decoded(f, e, Base64.getDecoder().decode(e.signature()));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String val(String line, String key) {
        if (!line.startsWith(key + "=")) throw new IllegalArgumentException(key);
        return line.substring(key.length() + 1);
    }
}
