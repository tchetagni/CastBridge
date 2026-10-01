package castbridge.server.orders;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The signed envelope `cbx1` of type `order` (docs/ACTIVATION-FORMAT.md § 3), built byte for byte like castbridge.core.owner.Envelope / Orders (checked against the shared test vector
 * `build-order-*` of tools/activation/test-vectors.json). One format, never a second.
 */
public final class OrderEnvelope {
    private OrderEnvelope() {}

    /** target = "any" | "device" | "license:<id>" | "group:<id>"; for "device", k and factors (TYPE -> 32 hex, in canonical order FLASH, ETHERNET, WIFI, SYSTEM_SERIAL, BLUETOOTH). */
    public record Target(String text, int k, Map<String, String> factors) {
        public static Target any() { return new Target("any", 0, Map.of()); }
        public static Target license(String id) { return new Target("license:" + id, 0, Map.of()); }
        public static Target group(String id) { return new Target("group:" + id, 0, Map.of()); }
        public static Target device(int k, Map<String, String> factors) { return new Target("device", k, factors); }
    }

    public static final List<String> FACTOR_ORDER = List.of("FLASH", "ETHERNET", "WIFI", "SYSTEM_SERIAL", "BLUETOOTH");

    public static String payload(String kid, long seq, String nonce, long issuedAt, long notBefore, long expiresAt, Target t, String action, Map<String, String> params) {
        List<String> l = new ArrayList<>();
        l.add("castbridge-envelope-v1"); l.add("type=order"); l.add("kid=" + kid); l.add("seq=" + seq); l.add("nonce=" + nonce);
        l.add("issuedAt=" + issuedAt); l.add("notBefore=" + notBefore); l.add("expiresAt=" + expiresAt);
        l.add("target=" + t.text());
        if (t.text().equals("device")) {
            l.add("k=" + t.k());
            for (String f : FACTOR_ORDER) if (t.factors().containsKey(f)) l.add("factor=" + f + "|" + t.factors().get(f));
        }
        l.add("--");
        l.add("action=" + action);
        new TreeMap<>(params).forEach((k, v) -> l.add("param=" + k + "|" + v));
        return String.join("\n", l);
    }

    /** "cbx1.<payload base64url without padding>.<signature base64>". */
    public static String token(String payload, byte[] signature) {
        return "cbx1." + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + Base64.getEncoder().encodeToString(signature);
    }
}
