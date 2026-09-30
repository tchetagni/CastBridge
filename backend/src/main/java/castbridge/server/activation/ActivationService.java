package castbridge.server.activation;

import castbridge.server.config.CastbridgeProperties;
import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/**
 * Offline activation tokens (the seller's side): given the "machine code" a TV displays (derived from its MAC address),
 * compute the token the user enters on the TV. The algorithm is the exact mirror of
 * {@code android/core/.../activation/Activation.kt} (HMAC-SHA256, base32 without look-alike characters, 12 chars).
 */
@Service
public class ActivationService {
    /** Crockford base32 (same as the app: no I, L, O, U). */
    private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

    /** The Caesar shift the TV applies to the code it shows; the seller reverses it. */
    private static final int CAESAR_SHIFT = 3;

    private final String secret;

    public ActivationService(CastbridgeProperties props) {
        this.secret = props.activation().secret();
    }

    /** The generator works only when the shared secret is configured (CASTBRIDGE_ACTIVATION_SECRET). */
    public boolean configured() {
        return secret != null && !secret.isBlank();
    }

    /** The activation token for a machine code, exactly as the TV app verifies it (core/activation/Activation.kt). */
    public String token(String requestCode) {
        byte[] h = hmac(secret.getBytes(StandardCharsets.UTF_8), normalize(requestCode).getBytes(StandardCharsets.UTF_8));
        return group(base32(h).substring(0, 12), 4);
    }

    /**
     * The admin enters the code AS DISPLAYED ON THE TV (Caesar-shifted). This reverses the shift to get the real
     * machine code, then computes the token.
     */
    public String tokenFromDisplayed(String displayedCode) {
        return token(caesar(displayedCode, -CAESAR_SHIFT));
    }

    /** Caesar substitution over [ALPHABET] (separators stay); positive shift encodes, negative decodes. */
    public static String caesar(String s, int shift) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toUpperCase().toCharArray()) {
            int i = ALPHABET.indexOf(c);
            if (i < 0) sb.append(c);
            else sb.append(ALPHABET.charAt(((i + shift) % ALPHABET.length() + ALPHABET.length()) % ALPHABET.length()));
        }
        return sb.toString();
    }

    static String normalize(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c)) sb.append(Character.toUpperCase(c));
        }
        return sb.toString();
    }

    private static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 indisponible", e);
        }
    }

    private static String base32(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        int acc = 0;
        int bits = 0;
        for (byte b : bytes) {
            acc = (acc << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                bits -= 5;
                sb.append(ALPHABET.charAt((acc >> bits) & 0x1F));
            }
        }
        if (bits > 0) sb.append(ALPHABET.charAt((acc << (5 - bits)) & 0x1F));
        return sb.toString();
    }

    private static String group(String s, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i += n) {
            if (i > 0) sb.append('-');
            sb.append(s, i, Math.min(i + n, s.length()));
        }
        return sb.toString();
    }
}
