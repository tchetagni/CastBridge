package castbridge.server.activations;

import castbridge.server.licenses.DeviceIdentity;
import castbridge.server.licenses.Hashing;
import castbridge.server.licenses.LicenseProperties;
import castbridge.server.web.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * {@code tv_ref = hex(HMAC-SHA-256(act-ref.key, normalized device code)[0:8])}: the only way a TV is designated in the immutable history, so that erasing the readable
 * code (right to erasure) never breaks the chain. The key is a file of the secrets folder of the licence module; without it the whole module answers 503.
 */
@Component
public class TvRef {
    private final byte[] key;

    public TvRef(LicenseProperties props) {
        this.key = Chains.readKey(props.secretsDir().resolve("act-ref.key"));
    }

    /** Value that identifies the key without revealing it (audit M4): kept at first use, compared at every start. */
    String checkValue() { return key == null ? null : HexFormat.of().formatHex(Hashing.hmac("HmacSHA256", key, "castbridge-act-ref-check-v1".getBytes(StandardCharsets.UTF_8))); }

    public boolean available() { return key != null; }

    /** The reference of a device code (any typing accepted: lower case, spaces, dashes). 400 when the code is not valid, 503 without the key. */
    public String of(String code) {
        if (key == null) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Suivi des activations indisponible");
        String c = DeviceIdentity.parseCode(code);
        if (c == null) throw ApiException.badRequest("Code d'appareil invalide : 16 caractères au format XXXX-XXXX-XXXX-XXXX, avec son caractère de contrôle");
        return HexFormat.of().formatHex(Hashing.hmac("HmacSHA256", key, c.getBytes(StandardCharsets.UTF_8)), 0, 8);
    }

    /** Like {@link #of} but null for a code that is not valid (anonymized codes, garbage). */
    public String ofOrNull(String code) {
        if (key == null || DeviceIdentity.parseCode(code) == null) return null;
        return of(code);
    }

    /** The canonical code, or null. */
    public static String canonical(String code) { return DeviceIdentity.parseCode(code); }
}
