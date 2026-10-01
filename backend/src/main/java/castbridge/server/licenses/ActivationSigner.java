package castbridge.server.licenses;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Signs an activation (the token a TV or phone verifies offline). Implemented with the {@code ActivationIssuer} of
 * {@code core} once the wire format of branch claude/trial-edition (docs/ACTIVATION-FORMAT.md) is published: until then
 * {@link ProvisionalActivationEncoder} only exercises the flow on staging and no real device accepts its output.
 *
 * <p>The scope of the key is part of the contract: see {@link SignerScope} and {@link ScopedActivationSigner}.
 */
public interface ActivationSigner {

    /** Identifier of the key (first 8 bytes of SHA-256 of the public key, hex). */
    String kid();

    /** What this key is allowed to issue. */
    Set<SignerScope> scopes();

    /** Signs; throws {@link castbridge.server.web.ApiException} (403) when the request is outside the scope of the key. */
    SignedActivation sign(ActivationRequest request);

    /** The powers a signing key can hold. The server key never holds TRANSFER nor OPEN_ALL. */
    enum SignerScope { ISSUE_TRIAL, ISSUE_PURCHASE, ISSUE_SUBSCRIPTION, REACTIVATE, TRANSFER, OPEN_ALL }

    /** What an issuance is for; each kind needs one scope. */
    enum IssueKind {
        TRIAL(SignerScope.ISSUE_TRIAL), PURCHASE(SignerScope.ISSUE_PURCHASE), SUBSCRIPTION(SignerScope.ISSUE_SUBSCRIPTION),
        REACTIVATION(SignerScope.REACTIVATE), TRANSFER(SignerScope.TRANSFER), OPEN_ALL(SignerScope.OPEN_ALL);

        private final SignerScope required;

        IssueKind(SignerScope required) { this.required = required; }

        public SignerScope required() { return required; }
    }

    /**
     * @param productIds   bouquets granted ("*" asks for everything: that is OPEN_ALL, never served by the server)
     * @param expiresAt    null = no end date
     * @param nonce        16 bytes, unique per issuance (derived from the idempotence key so that re-issuing gives the same token)
     * @param seatsAllowed seats of the licence (the "licence id + seat count" rule)
     */
    record ActivationRequest(String licenseId, String deviceCode, IssueKind kind, List<String> productIds, Instant issuedAt,
                             Instant expiresAt, byte[] nonce, int seatsAllowed) {}

    /** @param text the activation to give to the owner (copy / file / QR) ; never logged, only its fingerprint is kept */
    record SignedActivation(String text, String kid, String nonceHex, String fingerprint) {}
}
