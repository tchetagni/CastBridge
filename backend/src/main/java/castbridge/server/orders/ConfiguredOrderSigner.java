package castbridge.server.orders;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Default [OrderSigner]: key file (PEM or DER PKCS#8, or the base64 of the 32-byte seed) given by CASTBRIDGE_ORDERS_KEY_FILE; absent = the order service is off. */
@Component  // the licence module may declare its own OrderSigner @Primary
public class ConfiguredOrderSigner implements OrderSigner {
    private static final Logger log = LoggerFactory.getLogger(ConfiguredOrderSigner.class);
    private final Ed25519PrivateKeyParameters key;
    private final String kid;

    @org.springframework.beans.factory.annotation.Autowired
    public ConfiguredOrderSigner(@Value("${castbridge.orders.key-file:}") String keyFile) {
        this(load(keyFile));
    }

    public ConfiguredOrderSigner(Ed25519PrivateKeyParameters key) {
        this.key = key;
        this.kid = key == null ? null : HexFormat.of().formatHex(sha256(key.generatePublicKey().getEncoded())).substring(0, 16);
        if (key == null) log.info("orders: no signing key (CASTBRIDGE_ORDERS_KEY_FILE): the order service is off");
        else log.info("orders: signing with key {}", kid);
    }

    @Override public boolean enabled() { return key != null; }
    @Override public String keyId() { return kid; }

    @Override public byte[] sign(byte[] message) {
        if (key == null) throw new IllegalStateException("no signing key");
        Ed25519Signer s = new Ed25519Signer(); s.init(true, key); s.update(message, 0, message.length); return s.generateSignature();
    }

    private static Ed25519PrivateKeyParameters load(String file) {
        if (file == null || file.isBlank()) return null;
        try {
            Path f = Path.of(file);
            if (!Files.isReadable(f)) { log.warn("orders: key file not readable"); return null; }
            return parse(Files.readAllBytes(f));
        } catch (IOException | RuntimeException e) {
            log.error("orders: invalid key file ({})", e.getClass().getSimpleName());       // never the key material
            throw new IllegalStateException("Clé de signature des ordres invalide (CASTBRIDGE_ORDERS_KEY_FILE)");
        }
    }

    public static Ed25519PrivateKeyParameters parse(byte[] content) throws IOException {
        String text = new String(content, java.nio.charset.StandardCharsets.US_ASCII).trim();
        byte[] der;
        if (text.startsWith("-----BEGIN")) der = Base64.getDecoder().decode(text.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", ""));
        else if (content.length > 2 && content.length < 200 && content[0] == 0x30 && (content[1] & 0xff) == content.length - 2) der = content;
        else der = Base64.getDecoder().decode(text.replaceAll("\\s", ""));
        if (der.length == Ed25519PrivateKeyParameters.KEY_SIZE) return new Ed25519PrivateKeyParameters(der, 0);
        AsymmetricKeyParameter k = PrivateKeyFactory.createKey(PrivateKeyInfo.getInstance(der));
        if (k instanceof Ed25519PrivateKeyParameters ed) return ed;
        throw new IOException("not an Ed25519 private key");
    }

    private static byte[] sha256(byte[] b) {
        try { return MessageDigest.getInstance("SHA-256").digest(b); } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
