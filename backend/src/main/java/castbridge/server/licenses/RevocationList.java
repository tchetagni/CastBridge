package castbridge.server.licenses;

import java.util.Map;
import java.util.Set;

/**
 * The signed revocation list (docs/ACTIVATION-FORMAT.md § 7): an {@link Envelope} {@code cbx1} of type {@code revocation}, target any device, signed by a key with the
 * REVOKE scope (the server has it). Body = the {@code key=<kid>} lines sorted, then the {@code seat=<licence>|<poste>|<date ms>} lines sorted.
 */
public final class RevocationList {
    public static final String TYPE = "revocation";
    public static final String PREFIX = Envelope.PREFIX;

    private RevocationList() {}

    public static String token(LicenseKeyring key, long issuedAt, Set<String> keys, Map<String, Long> seats) {
        return EnvelopeIssuer.server(key).revocation(issuedAt, keys, seats, null, null);
    }
}
