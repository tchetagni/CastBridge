package castbridge.server.play;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The DEDICATED Ed25519 key of the play tickets (secret file {@code play-ticket.key}, {@code castbridge.play.ticket-key-file}). Never the key of the update manifests, never a
 * licence key: a leak of this key can only forge tickets (which carry no right), not updates or activations. Absent or unreadable file = the ticket route answers 503
 * ("ticket désactivé"). The private key lives ONLY here: the {@code castbridge-play} service holds the public key. The key material is never logged.
 */
@Component
public class PlayTicketKey {
    private static final Logger log = LoggerFactory.getLogger(PlayTicketKey.class);
    private static final byte[] PKCS8_PREFIX = {0x30, 0x2e, 0x02, 0x01, 0x00, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x04, 0x22, 0x04, 0x20};
    private final PrivateKey key;

    @Autowired
    public PlayTicketKey(@Value("${castbridge.play.ticket-key-file:}") String keyFile) {
        this.key = load(keyFile);
        if (key == null) log.info("play: no ticket key (castbridge.play.ticket-key-file): the ticket route answers 503");
        else log.info("play: tickets are signed with the dedicated play key");
    }

    /** For tests and tools: a key already in memory. */
    public PlayTicketKey(PrivateKey key) { this.key = key; }

    public boolean enabled() { return key != null; }

    public byte[] sign(byte[] message) {
        if (key == null) throw new IllegalStateException("no ticket key");
        try {
            Signature s = Signature.getInstance("Ed25519");
            s.initSign(key);
            s.update(message);
            return s.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("ticket signature failed", e);
        }
    }

    private static PrivateKey load(String file) {
        if (file == null || file.isBlank()) return null;
        try {
            Path f = Path.of(file);
            if (!Files.isReadable(f)) { log.warn("play: ticket key file is not readable"); return null; }
            return parse(Files.readAllBytes(f));
        } catch (IOException | GeneralSecurityException | RuntimeException e) {
            log.error("play: invalid ticket key file ({}): tickets are off", e.getClass().getSimpleName());   // never the key material
            return null;
        }
    }

    /** PEM or base64 PKCS#8 of an Ed25519 key, or the base64 of its 32-byte seed. */
    static PrivateKey parse(byte[] content) throws GeneralSecurityException {
        String text = new String(content, StandardCharsets.US_ASCII).trim();
        byte[] der = Base64.getDecoder().decode(text.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", ""));
        if (der.length == 32) {
            byte[] full = new byte[PKCS8_PREFIX.length + 32];
            System.arraycopy(PKCS8_PREFIX, 0, full, 0, PKCS8_PREFIX.length);
            System.arraycopy(der, 0, full, PKCS8_PREFIX.length, 32);
            der = full;
        }
        return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(der));
    }
}
