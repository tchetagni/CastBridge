package castbridge.server.licenses;

import castbridge.server.web.ApiException;
import java.util.Set;
import org.springframework.http.HttpStatus;

/** Signs with the keyring key through an {@link ActivationEncoder}. Wrap it in {@link ScopedActivationSigner} (the bean does). */
public final class Ed25519ActivationSigner implements ActivationSigner {
    private final LicenseKeyring keyring;
    private final ActivationEncoder encoder;

    public Ed25519ActivationSigner(LicenseKeyring keyring, ActivationEncoder encoder) {
        this.keyring = keyring;
        this.encoder = encoder;
    }

    @Override
    public String kid() { return keyring.kid(); }

    @Override
    public Set<SignerScope> scopes() { return Set.of(SignerScope.values()); }

    @Override
    public SignedActivation sign(ActivationRequest r) {
        if (!keyring.present()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Aucune clé de signature serveur : déposez-la dans le dossier des secrets (voir docs/LICENSE-ADMIN.md)");
        }
        byte[] payload = encoder.payload(r, keyring.kid());
        byte[] sig = keyring.sign(payload);
        String text = encoder.render(r, keyring.kid(), payload, sig);
        return new SignedActivation(text, keyring.kid(), java.util.HexFormat.of().formatHex(r.nonce()), Hashing.sha256Hex(text));
    }
}
