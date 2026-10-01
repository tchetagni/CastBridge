package castbridge.server.licenses;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HexFormat;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The SERVER Ed25519 key of the licence module, read from the secrets folder (CASTBRIDGE_LICENSES_SECRETS_DIR, file
 * license-signing.key): never from the image, never logged, never in the repository. No file = no key = the signing
 * features answer 503 and everything else works. It is not the key of the update manifests.
 */
@Component
public class LicenseKeyring {
    private static final Logger log = LoggerFactory.getLogger(LicenseKeyring.class);

    private final Ed25519PrivateKeyParameters key;
    private final byte[] publicKey;

    @org.springframework.beans.factory.annotation.Autowired
    public LicenseKeyring(LicenseProperties props) {
        this(load(props));
    }

    LicenseKeyring(Ed25519PrivateKeyParameters key) {
        this.key = key;
        this.publicKey = key == null ? null : key.generatePublicKey().getEncoded();
        if (key == null) log.info("licence module: no server signing key (secrets folder): activations cannot be issued");
        else log.info("licence module: server signing key {} loaded", kid());
    }

    public boolean present() { return key != null; }

    public String kid() { return publicKey == null ? null : kidOf(publicKey); }

    public String publicKeyBase64() { return publicKey == null ? null : Base64.getEncoder().encodeToString(publicKey); }

    public static String kidOf(byte[] publicKey) { return HexFormat.of().formatHex(Hashing.sha256(publicKey)).substring(0, 16); }

    public byte[] sign(byte[] message) {
        if (key == null) throw new IllegalStateException("no key");
        Ed25519Signer s = new Ed25519Signer();
        s.init(true, key);
        s.update(message, 0, message.length);
        return s.generateSignature();
    }

    public static boolean verify(byte[] publicKey, byte[] message, byte[] signature) {
        try {
            Ed25519Signer s = new Ed25519Signer();
            s.init(false, new Ed25519PublicKeyParameters(publicKey, 0));
            s.update(message, 0, message.length);
            return s.verifySignature(signature);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static Ed25519PrivateKeyParameters load(LicenseProperties props) {
        Path f = props.secretsDir().resolve(props.signingKeyFile()).normalize();
        if (!f.startsWith(props.secretsDir().normalize()) || !Files.isReadable(f)) return null;
        try {
            return parse(Files.readAllBytes(f));
        } catch (IOException | RuntimeException e) {
            // never log the key material itself
            log.error("invalid licence signing key file: {}", e.getClass().getSimpleName());
            throw new IllegalStateException("Clé de signature des licences invalide (dossier des secrets)");
        }
    }

    /** PEM, DER PKCS#8, base64 of DER PKCS#8, or base64 of the 32-byte seed. */
    static Ed25519PrivateKeyParameters parse(byte[] content) throws IOException {
        byte[] der;
        String text = new String(content, StandardCharsets.US_ASCII).trim();
        if (text.startsWith("-----BEGIN")) der = Base64.getDecoder().decode(text.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", ""));
        else if (content.length > 2 && content.length < 200 && content[0] == 0x30 && (content[1] & 0xff) == content.length - 2) der = content;
        else der = Base64.getDecoder().decode(text.replaceAll("\\s", ""));
        if (der.length == Ed25519PrivateKeyParameters.KEY_SIZE) return new Ed25519PrivateKeyParameters(der, 0);
        AsymmetricKeyParameter k = PrivateKeyFactory.createKey(PrivateKeyInfo.getInstance(der));
        if (k instanceof Ed25519PrivateKeyParameters ed) return ed;
        throw new IOException("not an Ed25519 private key");
    }
}
