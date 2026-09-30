package castbridge.server.updates;

import castbridge.server.config.CastbridgeProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Signs the update manifests with Ed25519. The private key comes from CASTBRIDGE_SIGNING_KEY (base64 PKCS#8 DER, PEM
 * text, or the raw 32-byte seed in base64) or CASTBRIDGE_SIGNING_KEY_FILE (PEM or DER file, e.g. a Docker secret).
 * The apps embed the matching public key (raw 32 bytes, base64) and refuse any manifest it does not verify.
 */
@Component
public class ManifestSigner {
    private static final Logger log = LoggerFactory.getLogger(ManifestSigner.class);

    private final Ed25519PrivateKeyParameters key;
    private final byte[] publicKey;

    @org.springframework.beans.factory.annotation.Autowired
    public ManifestSigner(CastbridgeProperties props) {
        this(load(props.signing()));
    }

    ManifestSigner(Ed25519PrivateKeyParameters key) {
        this.key = key;
        this.publicKey = key == null ? null : key.generatePublicKey().getEncoded();
        if (key == null) log.warn("no Ed25519 signing key (CASTBRIDGE_SIGNING_KEY / _FILE): update manifests are unavailable");
        else log.info("update manifests signed with Ed25519 key {}", keyId());
    }

    public boolean enabled() { return key != null; }

    /** Raw public key (32 bytes) in base64: the constant to embed in the apps. */
    public String publicKeyBase64() { return publicKey == null ? null : Base64.getEncoder().encodeToString(publicKey); }

    /** First 16 hex digits of SHA-256(public key): tells which key signed a manifest. */
    public String keyId() { return publicKey == null ? null : HexFormat.of().formatHex(sha256(publicKey)).substring(0, 16); }

    public String signBase64(String payload) {
        if (key == null) throw new IllegalStateException("no signing key");
        Ed25519Signer signer = new Ed25519Signer();
        signer.init(true, key);
        byte[] msg = payload.getBytes(StandardCharsets.UTF_8);
        signer.update(msg, 0, msg.length);
        return Base64.getEncoder().encodeToString(signer.generateSignature());
    }

    static Ed25519PrivateKeyParameters load(CastbridgeProperties.Signing s) {
        try {
            if (s.key() != null && !s.key().isBlank()) return parse(s.key().getBytes(StandardCharsets.US_ASCII));
            if (s.keyFile() != null && !s.keyFile().toString().isBlank()) {
                Path f = s.keyFile();
                if (!Files.isReadable(f)) {
                    log.warn("signing key file {} is not readable", f);
                    return null;
                }
                return parse(Files.readAllBytes(f));
            }
        } catch (IOException | RuntimeException e) {
            // never log the key material itself
            log.error("invalid Ed25519 signing key: {}", e.getClass().getSimpleName());
            throw new IllegalStateException("Clé de signature Ed25519 invalide (CASTBRIDGE_SIGNING_KEY / _FILE)");
        }
        return null;
    }

    /** Accepts PEM ("-----BEGIN PRIVATE KEY-----"), DER PKCS#8, base64 of DER PKCS#8, or base64 of the 32-byte seed. */
    static Ed25519PrivateKeyParameters parse(byte[] content) throws IOException {
        byte[] der;
        String text = new String(content, StandardCharsets.US_ASCII).trim();
        if (text.startsWith("-----BEGIN")) {
            String body = text.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
            der = Base64.getDecoder().decode(body);
        } else if (content.length > 2 && content.length < 200 && content[0] == 0x30 && (content[1] & 0xff) == content.length - 2) {
            der = content; // binary DER (a SEQUENCE spanning the whole file)
        } else {
            der = Base64.getDecoder().decode(text.replaceAll("\\s", ""));
        }
        if (der.length == Ed25519PrivateKeyParameters.KEY_SIZE) return new Ed25519PrivateKeyParameters(der, 0);
        AsymmetricKeyParameter k = PrivateKeyFactory.createKey(PrivateKeyInfo.getInstance(der));
        if (k instanceof Ed25519PrivateKeyParameters ed) return ed;
        throw new IOException("not an Ed25519 private key");
    }

    static byte[] sha256(byte[] b) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(b);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
