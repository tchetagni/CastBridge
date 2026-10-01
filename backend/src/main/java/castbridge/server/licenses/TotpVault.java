package castbridge.server.licenses;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Protects the TOTP secrets at rest (AES-256-GCM). The key is CASTBRIDGE_LICENSES_TOTP_KEY (base64) or the file
 * license-totp.key of the secrets folder; without a key TOTP cannot be enrolled (a clear message says so) and nothing is stored in clear.
 */
@Component
public class TotpVault {
    private final byte[] key;
    private final SecureRandom random = new SecureRandom();

    public TotpVault(LicenseProperties props) {
        byte[] k = null;
        try {
            if (props.totpKey() != null && !props.totpKey().isBlank()) k = Base64.getDecoder().decode(props.totpKey().trim());
            else {
                Path f = props.secretsDir().resolve("license-totp.key");
                if (Files.isReadable(f)) k = Base64.getDecoder().decode(Files.readString(f).trim());
            }
        } catch (IOException | IllegalArgumentException e) {
            k = null;
        }
        this.key = k != null && k.length == 32 ? k : null;
    }

    public boolean available() { return key != null; }

    public String seal(byte[] secret) {
        if (key == null) throw new IllegalStateException("no key");
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            byte[] ct = c.doFinal(secret);
            byte[] all = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, all, 0, iv.length);
            System.arraycopy(ct, 0, all, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(all);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public byte[] open(String sealed) {
        if (key == null) throw new IllegalStateException("no key");
        try {
            byte[] all = Base64.getDecoder().decode(sealed);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, all, 0, 12));
            return c.doFinal(all, 12, all.length - 12);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("TOTP secret unreadable", e);
        }
    }
}
