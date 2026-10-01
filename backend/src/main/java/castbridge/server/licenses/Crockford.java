package castbridge.server.licenses;

/**
 * Base32 Crockford and the check character of docs/ACTIVATION-FORMAT.md § 1.4 (device code, grouped text). Port of the reference
 * implementations, verified by the vectors of tools/activation/test-vectors.json (WireFormatVectorsTest).
 */
public final class Crockford {
    public static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

    private Crockford() {}

    /** Value 0..31 of a character (O→0, I and L→1, any case), or −1. */
    public static int value(char c) {
        char u = Character.toUpperCase(c);
        if (u == 'O') u = '0';
        else if (u == 'I' || u == 'L') u = '1';
        return ALPHABET.indexOf(u);
    }

    public static String encode(byte[] data) {
        StringBuilder out = new StringBuilder();
        int buf = 0, bits = 0;
        for (byte b : data) {
            buf = (buf << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                bits -= 5;
                out.append(ALPHABET.charAt((buf >> bits) & 31));
            }
            buf &= (1 << bits) - 1;
        }
        if (bits > 0) out.append(ALPHABET.charAt((buf << (5 - bits)) & 31));
        return out.toString();
    }

    /** The check character of {@code chars} with salt {@code salt} (device code: 0; grouped text: rank of the group). */
    public static char check(String chars, int salt) {
        int sum = 7 * salt;
        for (int i = 0; i < chars.length(); i++) sum += value(chars.charAt(i)) * (2 * i + 1);
        return ALPHABET.charAt(sum & 31);
    }
}
