package castbridge.server.licenses;

import java.util.List;
import java.util.Set;

/**
 * Signs an activation (the {@code cbx1} token a TV or phone verifies offline, docs/ACTIVATION-FORMAT.md). The wire format is the one of
 * {@code castbridge.core.owner.ActivationIssuer} (module {@code core}); the server is a Java tool, so {@link Ed25519ActivationSigner} is a
 * port of the specification, verified byte for byte against tools/activation/test-vectors.json (WireFormatVectorsTest): same inputs, same bytes.
 *
 * <p>The scope of the key is part of the contract: see {@link SignerScope} and {@link ScopedActivationSigner}.
 */
public interface ActivationSigner {

    /** Identifier of the key (first 8 bytes of SHA-256 of the raw public key, hex). */
    String kid();

    /** What this key is allowed to sign. */
    Set<SignerScope> scopes();

    /** Signs; throws {@link castbridge.server.web.ApiException} (403) when the request is outside the scope of the key, (400) when it breaks a rule of the format. */
    SignedActivation sign(ActivationRequest request);

    /** The scopes of the format (§ 2). The server key never holds TRANSFER nor COMMAND_OPEN_ALL. */
    enum SignerScope { ISSUE_TRIAL, ISSUE_PRODUCTION, COMMAND_SUPPORT, COMMAND_UNLOCK, COMMAND_OPEN_ALL, TRANSFER, REVOKE, REGISTRY, REACTIVATE, POLICY }

    /** What an issuance is for; each kind needs one scope. TRANSFER and OPEN_ALL exist only to be refused to the server key. */
    enum IssueKind {
        TRIAL(SignerScope.ISSUE_TRIAL), PRODUCTION(SignerScope.ISSUE_PRODUCTION), TRANSFER(SignerScope.TRANSFER), OPEN_ALL(SignerScope.COMMAND_OPEN_ALL);

        private final SignerScope required;

        IssueKind(SignerScope required) { this.required = required; }

        public SignerScope required() { return required; }
    }

    /**
     * @param subject    "tv" or "phone"
     * @param license    licence identifier, "trial" for a trial key
     * @param seat       seat identifier (16 hex), null = the default one for a new seat
     * @param rights     right lines ({@code purchase|…}, {@code subscription|…}, {@code openall|…}), empty for a trial
     * @param issuedAt   real time of the issuance, in ms (never in the future)
     * @param notBefore  start of the installation window, in ms (≤ issuedAt)
     * @param windowDays installation window, 1 to 366 days
     * @param nonce      8 to 64 hexadecimal digits, unique per issuance
     * @param seq        sequence number of the key (never goes back: a device refuses an activation older than the last one it saw for this key); null = {@code issuedAt}
     */
    record ActivationRequest(IssueKind kind, String subject, String license, String seat, DeviceIdentity.Request device, List<String> rights, long issuedAt,
                             long notBefore, int windowDays, String nonce, Long seq) {
        public ActivationRequest(IssueKind kind, String subject, String license, String seat, DeviceIdentity.Request device, List<String> rights, long issuedAt, long notBefore,
                                 int windowDays, String nonce) {
            this(kind, subject, license, seat, device, rights, issuedAt, notBefore, windowDays, nonce, null);
        }
    }

    /** @param text the activation to give to the owner (copy / file / QR); never logged, only its fingerprint is kept */
    record SignedActivation(String text, String kid, String nonce, String fingerprint, String seat, long notBefore, long notAfter, long seq) {
        public SignedActivation(String text, String kid, String nonce, String fingerprint, String seat, long notBefore, long notAfter) {
            this(text, kid, nonce, fingerprint, seat, notBefore, notAfter, 0L);
        }
    }
}
