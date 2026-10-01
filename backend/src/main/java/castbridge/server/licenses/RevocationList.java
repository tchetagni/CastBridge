package castbridge.server.licenses;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The signed revocation list {@code cbr1} (docs/ACTIVATION-FORMAT.md § 7): what devices must forget, keys (kid) and seats ({@code licence|poste|date ms}).
 * Signed by a key with the REVOKE scope (the server has it); canonical text, keys sorted then seats sorted.
 */
public final class RevocationList {
    public static final String FORMAT = "castbridge-revocation-v1";
    public static final String PREFIX = "cbr1";

    private RevocationList() {}

    public static String token(LicenseKeyring key, long issuedAt, Set<String> keys, Map<String, Long> seats) {
        List<String> lines = new ArrayList<>(List.of(FORMAT, "kid=" + key.kid(), "issuedAt=" + issuedAt));
        new TreeSet<>(keys).forEach(k -> lines.add("key=" + k));
        new TreeMap<>(seats).forEach((s, at) -> lines.add("seat=" + s + "|" + at));
        String text = String.join("\n", lines);
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        return PREFIX + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes) + "." + Base64.getEncoder().encodeToString(key.sign(bytes));
    }
}
