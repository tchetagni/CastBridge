package castbridge.server.activations;

import castbridge.server.licenses.Actor;
import castbridge.server.licenses.Hashing;
import castbridge.server.licenses.LicenseKeyring;
import castbridge.server.licenses.Role;
import castbridge.server.licenses.TrustedKeys;
import castbridge.server.web.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Option A of D-W23-3, OFF by default ({@code castbridge.activations.console-sessions}): the owner phone console, once online, proves it holds its tool key. The server
 * gives a 32-hex challenge (120 s, single use, 10 an hour per key); the console signs {@code "castbridge-console-session-v1\n" + challenge} with the key of the PHONE tool (the
 * same key the server already trusts for the registry); the server answers a read-only bearer valid 15 minutes (scopes ACT_READ and ACT_JOURNAL_UPLOAD, never an export nor a
 * decision). Revoking the key of the phone stops new sessions at once. Challenges and tokens live in memory only (a restart ends every session); only the SHA-256 of a token is held.
 *
 * <p>NOT WIRED to the security chain: the bearer filter of {@code SecurityConfig} (not owned by this module) must call {@link #authenticate} before the option can be used.
 */
@Service
public class ConsoleSession {
    static final String DOMAIN = "castbridge-console-session-v1\n";
    private static final Pattern KID = Pattern.compile("[0-9a-f]{16}");
    private static final SecureRandom RND = new SecureRandom();

    private record Challenge(String kid, long expiresMs) {}

    private record Token(String kid, long expiresMs) {}

    private final ActivationsProperties props;
    private final TrustedKeys trusted;
    private final ToolDirectory tools;
    private final ActivationsPolicy policy;
    private final ActClock clock;
    private final JdbcTemplate jdbc;
    private final Map<String, Challenge> challenges = new ConcurrentHashMap<>();
    private final Map<String, Token> tokens = new ConcurrentHashMap<>();

    public ConsoleSession(ActivationsProperties props, TrustedKeys trusted, ToolDirectory tools, ActivationsPolicy policy, ActClock clock, JdbcTemplate jdbc) {
        this.props = props;
        this.trusted = trusted;
        this.tools = tools;
        this.policy = policy;
        this.clock = clock;
        this.jdbc = jdbc;
    }

    private void enabled() {
        if (!props.consoleSessions()) throw ApiException.notFound("Cette adresse n'existe pas");
    }

    private boolean revoked(String kid) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM lic_revocation WHERE kid = ?", Integer.class, kid);
        return n != null && n > 0;
    }

    /** A challenge for the phone key {@code kid}: 400 for a bad id, 403 when it is not a trusted, unrevoked PHONE key, 429 past 10 an hour. */
    public String challenge(String kid) {
        enabled();
        if (kid == null || !KID.matcher(kid).matches()) throw ApiException.badRequest("Identifiant de clé invalide (16 chiffres hexadécimaux)");
        if (trusted.find(kid) == null || !tools.typeOf(kid).equals("PHONE") || revoked(kid)) throw new ApiException(HttpStatus.FORBIDDEN, "Cette clé n'est pas celle d'une console de téléphone de confiance");
        policy.limit("challenge:" + kid, 10, Duration.ofHours(1), "défis de session par clé");
        long now = clock.nowMs();
        challenges.values().removeIf(c -> c.expiresMs() < now);
        byte[] b = new byte[16];
        RND.nextBytes(b);
        String ch = HexFormat.of().formatHex(b);
        challenges.put(ch, new Challenge(kid, now + 120_000L));
        return ch;
    }

    public record Opened(String token, int expiresInSeconds) {}

    /** Exchanges a signed challenge for a bearer. The challenge is consumed by any attempt (a wrong signature does not leave it available to guess again). */
    public Opened open(String kid, String challenge, String signatureBase64) {
        enabled();
        Challenge c = challenge == null ? null : challenges.remove(challenge);
        if (c == null || c.expiresMs() < clock.nowMs()) throw ApiException.badRequest("Défi inconnu, déjà utilisé ou expiré : demandez-en un autre");
        TrustedKeys.Key key = kid == null ? null : trusted.find(kid);
        boolean ok;
        try {
            ok = key != null && kid.equals(c.kid()) && !revoked(kid) && LicenseKeyring.verify(key.publicKey(), (DOMAIN + challenge).getBytes(StandardCharsets.UTF_8), Base64.getDecoder().decode(signatureBase64));
        } catch (RuntimeException e) {
            ok = false;
        }
        if (!ok) throw new ApiException(HttpStatus.FORBIDDEN, "Signature du défi refusée");
        byte[] b = new byte[32];
        RND.nextBytes(b);
        String token = "act1." + Base64.getUrlEncoder().withoutPadding().encodeToString(b);
        long now = clock.nowMs();
        tokens.values().removeIf(t -> t.expiresMs() < now);
        tokens.put(Hashing.sha256Hex(token), new Token(kid, now + 15 * 60_000L));
        return new Opened(token, 900);
    }

    /** The actor of a console bearer (read and journal upload only), empty when unknown or expired. */
    public Optional<Actor> authenticate(String bearer) {
        if (bearer == null) return Optional.empty();
        String t = bearer.startsWith("Bearer ") ? bearer.substring(7).trim() : bearer.trim();
        Token tok = tokens.get(Hashing.sha256Hex(t));
        if (tok == null || tok.expiresMs() < clock.nowMs() || revoked(tok.kid())) return Optional.empty();
        return Optional.of(new Actor("phone:" + tok.kid(), Role.SUPPORT, "phone", true));
    }
}
