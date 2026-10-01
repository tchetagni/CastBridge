package castbridge.server.orders;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The device code `XXXX-XXXX-XXXX-XXXX` from a fingerprint set (docs/ACTIVATION-FORMAT.md § 1.2, 1.4): the server recomputes it to check what a phone registers for a TV. */
public final class DeviceCodes {
    private DeviceCodes() {}
    static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

    public static String of(Map<String, String> factors) {
        int mask = 0; List<String> lines = new ArrayList<>();
        for (int i = 0; i < OrderEnvelope.FACTOR_ORDER.size(); i++) {
            String f = OrderEnvelope.FACTOR_ORDER.get(i);
            if (factors.containsKey(f)) { mask |= 1 << i; lines.add(f + "=" + factors.get(f)); }
        }
        byte[] h = sha256(String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
        String body = ALPHABET.charAt(mask & 31) + base32(h).substring(0, 14);
        String full = body + check(body, 0);
        return full.substring(0, 4) + "-" + full.substring(4, 8) + "-" + full.substring(8, 12) + "-" + full.substring(12, 16);
    }

    static String base32(byte[] b) {
        StringBuilder out = new StringBuilder(); int acc = 0, bits = 0;
        for (byte x : b) { acc = (acc << 8) | (x & 0xff); bits += 8; while (bits >= 5) { out.append(ALPHABET.charAt((acc >> (bits - 5)) & 31)); bits -= 5; } acc &= (1 << bits) - 1; }
        if (bits > 0) out.append(ALPHABET.charAt((acc << (5 - bits)) & 31));
        return out.toString();
    }

    static char check(String s, int salt) {
        int sum = 7 * salt;
        for (int i = 0; i < s.length(); i++) sum += ALPHABET.indexOf(s.charAt(i)) * (2 * i + 1);
        return ALPHABET.charAt(sum & 31);
    }

    static byte[] sha256(byte[] b) {
        try { return MessageDigest.getInstance("SHA-256").digest(b); } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
