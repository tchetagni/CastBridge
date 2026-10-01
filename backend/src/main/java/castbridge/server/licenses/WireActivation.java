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

/** The canonical text and the token of an activation (docs/ACTIVATION-FORMAT.md § 3), plus a strict decoder (tests, inspection). */
public final class WireActivation {
    public static final String FORMAT = "castbridge-activation-v1";
    public static final String PREFIX = "cba1";
    public static final String TRIAL_LICENSE = "trial";
    public static final long DAY_MS = 86_400_000L;
    public static final int MAX_WINDOW_DAYS = 366;
    public static final long MAX_OPEN_ALL_MS = 30 * DAY_MS;
    public static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");
    public static final Pattern HEX = Pattern.compile("[0-9a-f]{8,64}");

    private WireActivation() {}

    public record Fields(String kind, String subject, String kid, String nonce, long issuedAt, long notBefore, long notAfter, String license, String seat, int k,
                         Map<Factor, String> factors, List<String> rights) {}

    public record Decoded(Fields fields, String text, byte[] signature) {}

    public static String payload(Fields f) {
        List<String> lines = new ArrayList<>(List.of(FORMAT, "kind=" + f.kind(), "subject=" + f.subject(), "kid=" + f.kid(), "nonce=" + f.nonce(), "issuedAt=" + f.issuedAt(),
                "notBefore=" + f.notBefore(), "notAfter=" + f.notAfter(), "license=" + f.license(), "seat=" + f.seat(), "k=" + f.k()));
        Map<Factor, String> sorted = new EnumMap<>(Factor.class);
        sorted.putAll(f.factors());
        sorted.forEach((fac, h) -> lines.add("factor=" + fac.name() + "|" + h));
        List<String> rights = new ArrayList<>(f.rights());
        Collections.sort(rights); // by the bytes of the UTF-8 text: the rights are ASCII, so the String order is the byte order
        rights.forEach(r -> lines.add("right=" + r));
        return String.join("\n", lines);
    }

    /** cba1.&lt;payload base64url without padding&gt;.&lt;signature base64 with padding&gt; */
    public static String token(String payload, byte[] signature) {
        return PREFIX + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + Base64.getEncoder().encodeToString(signature);
    }

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

    /** Strict decoder: the payload is re-built from the parsed fields and must be identical byte for byte, or the result is null. */
    public static Decoded decode(String token) {
        try {
            String[] parts = token.trim().split("\\.");
            if (parts.length != 3 || !parts[0].equals(PREFIX)) return null;
            String text = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            byte[] sig = Base64.getDecoder().decode(parts[2]);
            String[] lines = text.split("\n", -1);
            if (!lines[0].equals(FORMAT)) return null;
            Fields f = new Fields(val(lines[1], "kind"), val(lines[2], "subject"), val(lines[3], "kid"), val(lines[4], "nonce"), Long.parseLong(val(lines[5], "issuedAt")),
                    Long.parseLong(val(lines[6], "notBefore")), Long.parseLong(val(lines[7], "notAfter")), val(lines[8], "license"), val(lines[9], "seat"),
                    Integer.parseInt(val(lines[10], "k")), new EnumMap<>(Factor.class), new ArrayList<>());
            for (int i = 11; i < lines.length; i++) {
                String l = lines[i];
                if (l.startsWith("factor=")) {
                    String[] p = l.substring(7).split("\\|");
                    f.factors().put(Factor.valueOf(p[0]), p[1]);
                } else if (l.startsWith("right=")) {
                    if (!rightLineOk(l.substring(6))) return null;
                    f.rights().add(l.substring(6));
                } else {
                    return null;
                }
            }
            return payload(f).equals(text) ? new Decoded(f, text, sig) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String val(String line, String key) {
        if (!line.startsWith(key + "=")) throw new IllegalArgumentException(key);
        return line.substring(key.length() + 1);
    }
}
