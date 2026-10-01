package castbridge.server.licenses;

import castbridge.server.web.ApiException;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.http.HttpStatus;

/**
 * Enforces the scope of a key IN CODE, before the delegate signs: a request whose kind needs a scope the key does not hold is refused,
 * whatever the caller (page, API, import) and whatever a configuration file says.
 */
public final class ScopedActivationSigner implements ActivationSigner {
    /**
     * Powers of the server key, fixed in code (docs/ACTIVATION-FORMAT.md § 2): trial, production (à la carte, abonnement, ré-activation), revocation
     * lists and registry. NEVER transfer, NEVER "tout ouvert" (COMMAND_OPEN_ALL), no owner command.
     */
    public static final Set<SignerScope> SERVER_SCOPES = java.util.Collections.unmodifiableSet(
            EnumSet.of(SignerScope.ISSUE_TRIAL, SignerScope.ISSUE_PRODUCTION, SignerScope.REVOKE, SignerScope.REGISTRY));

    private final ActivationSigner delegate;
    private final Set<SignerScope> scopes;

    public ScopedActivationSigner(ActivationSigner delegate, Set<SignerScope> scopes) {
        this.delegate = delegate;
        this.scopes = java.util.Collections.unmodifiableSet(scopes.isEmpty() ? EnumSet.noneOf(SignerScope.class) : EnumSet.copyOf(scopes));
    }

    public static ScopedActivationSigner server(ActivationSigner delegate) { return new ScopedActivationSigner(delegate, SERVER_SCOPES); }

    @Override
    public String kid() { return delegate.kid(); }

    @Override
    public Set<SignerScope> scopes() { return scopes; }

    @Override
    public SignedActivation sign(ActivationRequest r) {
        check(r);
        return delegate.sign(r);
    }

    public boolean allows(SignerScope s) { return scopes.contains(s); }

    /** The scope check alone (used before touching the database). */
    public void check(ActivationRequest r) {
        boolean openAll = r.kind() == IssueKind.OPEN_ALL || r.rights().stream().anyMatch(x -> x.startsWith("openall|"));
        if (openAll && !scopes.contains(SignerScope.COMMAND_OPEN_ALL)) throw openAllRefused();
        if (r.kind() == IssueKind.TRANSFER && !scopes.contains(SignerScope.TRANSFER)) throw transferRefused();
        if (!scopes.contains(r.kind().required())) throw new ApiException(HttpStatus.FORBIDDEN, "Cette clé n'a pas la portée " + r.kind().required());
    }

    public static ApiException openAllRefused() {
        return new ApiException(HttpStatus.FORBIDDEN, "La clé du serveur ne peut pas tout ouvrir : seul le propriétaire le fait, avec ses outils hors serveur");
    }

    public static ApiException transferRefused() {
        return new ApiException(HttpStatus.FORBIDDEN, "Le serveur ne signe jamais un transfert de licence : il l'enregistre seulement depuis le registre hors ligne");
    }
}
