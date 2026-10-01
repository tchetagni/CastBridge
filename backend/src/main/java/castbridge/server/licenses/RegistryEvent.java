package castbridge.server.licenses;

import castbridge.server.licenses.DeviceIdentity.Factor;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One signed event of the licence registry (docs/ACTIVATION-FORMAT.md § 8.1): the text, the signature of its UTF-8 bytes, the id
 * {@code hex(SHA-256(kid + "|" + text)[0:8])}. The state of a licence is what replaying the events gives; merging two registries is a union by id.
 */
public record RegistryEvent(String id, String kid, String text, String signature) {
    public static final String FORMAT = "castbridge-licence-event-v1";

    public static String idOf(String kid, String text) {
        return java.util.HexFormat.of().formatHex(Hashing.sha256((kid + "|" + text).getBytes(StandardCharsets.UTF_8)), 0, 8);
    }

    public RegistryEvent(String kid, String text, String signature) { this(idOf(kid, text), kid, text, signature); }

    /** The event of a registry file entry, or null if its id does not match its text (never trusted) or it is malformed. */
    public static RegistryEvent fromJson(JsonNode n) {
        if (n == null || !n.isObject()) return null;
        String id = n.path("id").asText(null), kid = n.path("kid").asText(null), text = n.path("text").asText(null), sig = n.path("signature").asText(null);
        if (kid == null || text == null || sig == null || !text.startsWith(FORMAT + "\n") || !kid.matches("[0-9a-f]{16}")) return null;
        RegistryEvent e = new RegistryEvent(kid, text, sig);
        return id != null && !id.equals(e.id()) ? null : e;
    }

    public Map<String, String> fields() {
        Map<String, String> m = new LinkedHashMap<>();
        String[] lines = text.split("\n");
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].startsWith("factor=")) continue;
            int eq = lines[i].indexOf('=');
            if (eq > 0) m.put(lines[i].substring(0, eq), lines[i].substring(eq + 1));
        }
        return m;
    }

    public Map<Factor, String> factors() {
        Map<Factor, String> m = new EnumMap<>(Factor.class);
        for (String l : text.split("\n")) {
            if (!l.startsWith("factor=")) continue;
            String[] p = l.substring(7).split("\\|");
            m.put(Factor.valueOf(p[0]), p[1]);
        }
        return m;
    }

    public String type() { return fields().getOrDefault("type", ""); }

    public long at() {
        try {
            return Long.parseLong(fields().getOrDefault("at", "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public byte[] signatureBytes() { return Base64.getDecoder().decode(signature); }

    // ---- builders (events signed by the SERVER key; the server never builds a transfer) ----

    private static RegistryEvent sign(LicenseKeyring k, List<String> lines, Map<Factor, String> factors) {
        List<String> all = new ArrayList<>(lines);
        Map<Factor, String> sorted = new EnumMap<>(Factor.class);
        sorted.putAll(factors);
        sorted.forEach((f, h) -> all.add("factor=" + f.name() + "|" + h));
        String text = String.join("\n", all);
        return new RegistryEvent(k.kid(), text, Base64.getEncoder().encodeToString(k.sign(text.getBytes(StandardCharsets.UTF_8))));
    }

    private static List<String> base(String type, LicenseKeyring k, long at) { return new ArrayList<>(List.of(FORMAT, "type=" + type, "kid=" + k.kid(), "at=" + at)); }

    public static RegistryEvent license(LicenseKeyring k, long at, String license, int seats, int maxTransfersPerYear) {
        List<String> l = base("license", k, at);
        l.addAll(List.of("license=" + license, "seats=" + seats, "maxTransfersPerYear=" + maxTransfersPerYear));
        return sign(k, l, Map.of());
    }

    public static RegistryEvent issue(LicenseKeyring k, ActivationSigner.SignedActivation a, String subject, String kind, String license, int kk, Map<Factor, String> factors, long issuedAt) {
        List<String> l = base("issue", k, issuedAt);
        l.addAll(List.of("license=" + license, "seat=" + a.seat(), "subject=" + subject, "kind=" + kind, "nonce=" + a.nonce(), "seq=" + a.seq(), "notAfter=" + a.notAfter(), "k=" + kk));
        return sign(k, l, factors);
    }

    public static RegistryEvent revokeSeat(LicenseKeyring k, long at, String license, String seat) {
        List<String> l = base("revoke", k, at);
        l.addAll(List.of("target=seat", "value=" + license + "|" + seat));
        return sign(k, l, Map.of());
    }

    public static RegistryEvent revokeKey(LicenseKeyring k, long at, String kid) {
        List<String> l = base("revoke", k, at);
        l.addAll(List.of("target=key", "value=" + kid));
        return sign(k, l, Map.of());
    }
}
