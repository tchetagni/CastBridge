package castbridge.server.licenses;

import java.nio.ByteBuffer;
import java.security.SecureRandom;

/** RFC 6238 TOTP (HMAC-SHA1, 6 digits, 30 s), the format every authenticator app reads. No external service. */
public final class Totp {
    public static final int STEP_SECONDS = 30;
    private static final SecureRandom RANDOM = new SecureRandom();

    private Totp() {}

    public static byte[] newSecret() {
        byte[] b = new byte[20];
        RANDOM.nextBytes(b);
        return b;
    }

    public static String code(byte[] secret, long step) {
        byte[] h = Hashing.hmac("HmacSHA1", secret, ByteBuffer.allocate(8).putLong(step).array());
        int o = h[h.length - 1] & 0x0f;
        int bin = ((h[o] & 0x7f) << 24) | ((h[o + 1] & 0xff) << 16) | ((h[o + 2] & 0xff) << 8) | (h[o + 3] & 0xff);
        return String.format("%06d", bin % 1_000_000);
    }

    public static long stepAt(long epochSeconds) { return epochSeconds / STEP_SECONDS; }

    /**
     * @param lastStep the last step already accepted (a code is valid once: replay refused); null if none
     * @return the step that matched (now −1, now, now +1: clock drift), or −1
     */
    public static long verify(byte[] secret, String code, long nowStep, Long lastStep) {
        if (code == null || !code.trim().matches("\\d{6}")) return -1;
        String c = code.trim();
        long found = -1;
        for (long s = nowStep - 1; s <= nowStep + 1; s++) {
            if (lastStep != null && s <= lastStep) continue;
            // constant-time compare of the 6 digits, and keep scanning so that timing does not tell which step matched
            if (java.security.MessageDigest.isEqual(code(secret, s).getBytes(), c.getBytes())) found = s;
        }
        return found;
    }

    public static String uri(String issuer, String account, byte[] secret) {
        return "otpauth://totp/" + enc(issuer) + ":" + enc(account) + "?secret=" + Hashing.base32(secret) + "&issuer=" + enc(issuer) + "&algorithm=SHA1&digits=6&period=30";
    }

    private static String enc(String s) { return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20"); }
}
