package castbridge.server.wallet;

import castbridge.server.licenses.LicenseKeyring;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * La clé Ed25519 « portefeuille » de l'API (fichier secret {@code castbridge.wallet.key-file}) : signe les instantanés {@code cbw1}, JAMAIS autre chose (ni ticket de jeu, ni
 * activation, ni mise de service de jeu). Fichier absent ou illisible = module en 503. La matière de la clé n'est jamais journalisée.
 */
public final class WalletKey {
    private static final Logger log = LoggerFactory.getLogger(WalletKey.class);
    private final Ed25519PrivateKeyParameters key;
    private final byte[] publicKey;
    private final String kid;

    private WalletKey(Ed25519PrivateKeyParameters key) {
        this.key = key;
        this.publicKey = key.generatePublicKey().getEncoded();
        this.kid = LicenseKeyring.kidOf(publicKey);
    }

    public static WalletKey fromSeed(byte[] seed) {
        if (seed == null || seed.length != 32) throw new IllegalArgumentException("graine de 32 octets attendue");
        return new WalletKey(new Ed25519PrivateKeyParameters(seed, 0));
    }

    /** PEM ou base64 d'un PKCS#8 Ed25519, ou base64 de la graine de 32 octets ; null si le chemin est vide, illisible ou invalide (jamais la clé dans le journal). */
    public static WalletKey fromFile(String file) {
        if (file == null || file.isBlank()) return null;
        try {
            Path f = Path.of(file);
            if (!Files.isReadable(f)) {
                log.warn("wallet : fichier de clé illisible");
                return null;
            }
            return parse(Files.readAllBytes(f));
        } catch (IOException | RuntimeException e) {
            log.error("wallet : fichier de clé invalide ({}) : le portefeuille est indisponible", e.getClass().getSimpleName());
            return null;
        }
    }

    static WalletKey parse(byte[] content) {
        String text = new String(content, StandardCharsets.US_ASCII).trim();
        byte[] der = Base64.getDecoder().decode(text.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", ""));
        if (der.length == 32) return fromSeed(der);
        // PKCS#8 d'une clé Ed25519 : 16 octets d'en-tête fixe puis la graine de 32 octets
        if (der.length == 48 && der[0] == 0x30 && der[1] == 0x2e) return fromSeed(Arrays.copyOfRange(der, 16, 48));
        throw new IllegalArgumentException("format de clé inconnu");
    }

    public String kid() { return kid; }

    public byte[] publicKey() { return publicKey.clone(); }

    public byte[] sign(byte[] message) {
        Ed25519Signer s = new Ed25519Signer();
        s.init(true, key);
        s.update(message, 0, message.length);
        return s.generateSignature();
    }

    public boolean verify(byte[] message, byte[] signature) { return LicenseKeyring.verify(publicKey, message, signature); }
}
