package castbridge.server.tunnel;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Strict handling of SSH public keys: only ed25519, re-emitted in a normalized form (never the caller's text), so nothing can be injected into an authorized_keys line. */
public final class SshKeys {
    private static final String TYPE = "ssh-ed25519";

    private SshKeys() {}

    /** « ssh-ed25519 AAAA… » (comment dropped), or null when the text is not exactly one valid ed25519 public key. */
    public static String normalize(String text) {
        if (text == null) return null;
        String t = text.trim();
        if (t.length() > 400 || t.indexOf('\n') >= 0 || t.indexOf('\r') >= 0 || t.indexOf('\0') >= 0) return null;
        String[] p = t.split("\\s+", 3);
        if (p.length < 2 || !p[0].equals(TYPE) || !p[1].matches("[A-Za-z0-9+/]{68}")) return null;
        try {
            ByteBuffer b = ByteBuffer.wrap(Base64.getDecoder().decode(p[1]));
            int len = b.getInt();
            if (len != TYPE.length()) return null;
            byte[] type = new byte[len];
            b.get(type);
            if (!TYPE.equals(new String(type, StandardCharsets.US_ASCII)) || b.getInt() != 32 || b.remaining() != 32) return null;
        } catch (RuntimeException e) {
            return null;
        }
        return TYPE + " " + p[1];
    }
}
