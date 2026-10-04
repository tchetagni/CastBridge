// Controle INDEPENDANT du vecteur dore de la preuve de possession : reprend a l'identique la logique de
// backend/.../wallet/BindProof.valid (message, fenetre +/- 5 min, cle 32 octets, signature 64 octets) avec le JDK seul.
// Usage : java tools/wallet/BindProofCheck.java tools/wallet/wallet-bind-vector.json
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BindProofCheck {
    static String field(String json, String name) {
        Matcher m = Pattern.compile("\"" + name + "\"\\s*:\\s*(\"((?:[^\"\\\\]|\\\\.)*)\"|-?\\d+)").matcher(json);
        if (!m.find()) throw new IllegalStateException("champ absent : " + name);
        return m.group(2) != null ? m.group(2).replace("\\n", "\n") : m.group(1);
    }

    static boolean valid(byte[] pub, String sigB64, String code, String device, long at, long now) throws Exception {
        if (Math.abs(now - at) > 5 * 60_000L) return false;
        byte[] sig = Base64.getDecoder().decode(sigB64);
        if (pub.length != 32 || sig.length != 64) return false;
        byte[] prefix = {0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00};
        byte[] der = new byte[44];
        System.arraycopy(prefix, 0, der, 0, 12);
        System.arraycopy(pub, 0, der, 12, 32);
        PublicKey k = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(der));
        Signature s = Signature.getInstance("Ed25519");
        s.initVerify(k);
        s.update(("castbridge-wallet-bind-v1\n" + code + "\n" + device + "\n" + at).getBytes(StandardCharsets.UTF_8));
        return s.verify(sig);
    }

    public static void main(String[] a) throws Exception {
        String j = Files.readString(Path.of(a[0]));
        byte[] pub = Base64.getDecoder().decode(field(j, "keyBase64"));
        String sig = field(j, "sigBase64"), code = field(j, "deviceCode"), dev = field(j, "apiDeviceId");
        long at = Long.parseLong(field(j, "at"));
        boolean ok = valid(pub, sig, code, dev, at, at + 299000)
                && !valid(pub, sig, "2B5D-8FGH-1JKM-NPQ5", dev, at, at + 1000)
                && !valid(pub, sig, code, "00000000-0000-4000-8000-000000000000", at, at + 1000)
                && !valid(pub, sig, code, dev, at, at + 300001);
        System.out.println(ok ? "VECTEUR OK (logique de BindProof.valid)" : "VECTEUR REFUSE");
        if (!ok) System.exit(1);
    }
}
