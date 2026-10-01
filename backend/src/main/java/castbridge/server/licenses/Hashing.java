package castbridge.server.licenses;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Small hashing helpers (SHA-256, HMAC, RFC 4648 base32 without padding). */
public final class Hashing {
    private static final String B32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private Hashing() {}

    public static byte[] sha256(byte[] b) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(b);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String sha256Hex(String s) { return HexFormat.of().formatHex(sha256(s.getBytes(StandardCharsets.UTF_8))); }

    public static String sha256Hex(byte[] b) { return HexFormat.of().formatHex(sha256(b)); }

    public static byte[] hmac(String algo, byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance(algo);
            mac.init(new SecretKeySpec(key, algo));
            return mac.doFinal(data);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String base32(byte[] data) {
        StringBuilder sb = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0, bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                sb.append(B32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) sb.append(B32.charAt((buffer << (5 - bits)) & 31));
        return sb.toString();
    }

    public static byte[] unbase32(String s) {
        String t = s.replace(" ", "").replace("-", "").toUpperCase(java.util.Locale.ROOT);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int buffer = 0, bits = 0;
        for (char c : t.toCharArray()) {
            int v = B32.indexOf(c);
            if (v < 0) throw new IllegalArgumentException("base32");
            buffer = (buffer << 5) | v;
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }

    /** First n hex digits: a short, non-reversible tag for logs and sightings. */
    public static String tag(String value, int n) { return sha256Hex(value).substring(0, n); }
}
