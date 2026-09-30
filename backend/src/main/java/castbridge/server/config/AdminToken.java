package castbridge.server.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The admin bearer token. Compared in constant time (both sides hashed to SHA-256 first, so neither the content nor
 * the length leaks through timing). A missing or short token (< 32 characters) disables the admin routes entirely.
 */
@Component
public class AdminToken {
    private static final Logger log = LoggerFactory.getLogger(AdminToken.class);
    public static final int MIN_LENGTH = 32;

    private final byte[] expectedHash;

    public AdminToken(CastbridgeProperties props) {
        String token = props.adminToken();
        if (token == null || token.isBlank()) {
            log.warn("CASTBRIDGE_ADMIN_TOKEN is not set: admin routes are disabled");
            expectedHash = null;
        } else if (token.length() < MIN_LENGTH) {
            log.warn("CASTBRIDGE_ADMIN_TOKEN is shorter than {} characters: admin routes are disabled", MIN_LENGTH);
            expectedHash = null;
        } else {
            expectedHash = sha256(token);
        }
    }

    public boolean enabled() { return expectedHash != null; }

    /** @param authorization the raw Authorization header (may be null) */
    public boolean matchesHeader(String authorization) {
        if (expectedHash == null || authorization == null) return false;
        if (!authorization.regionMatches(true, 0, "Bearer ", 0, 7)) return false;
        return MessageDigest.isEqual(expectedHash, sha256(authorization.substring(7).trim()));
    }

    private static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
