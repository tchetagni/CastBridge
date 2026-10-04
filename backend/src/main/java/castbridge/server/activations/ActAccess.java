package castbridge.server.activations;

import castbridge.server.licenses.Actor;
import castbridge.server.licenses.LicenseAccounts;
import castbridge.server.licenses.LicenseProperties;
import castbridge.server.licenses.Role;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Who is calling and may they: the admin bearer token is an OWNER named {@code api-token} (channel api, second factor "strong": a long secret); a web session is the
 * account of the licence module (role from the database); the checks are those of {@link ActPermissions}, with the TOTP rule of the licence module.
 */
@Component
public class ActAccess {
    private final LicenseAccounts accounts;
    private final LicenseProperties props;

    public ActAccess(LicenseAccounts accounts, LicenseProperties props) {
        this.accounts = accounts;
        this.props = props;
    }

    public static final Actor API_TOKEN = new Actor("api-token", Role.OWNER, "api", true);

    /** The actor of an authenticated request. */
    public Actor actorOf(Authentication auth) {
        if (auth != null && auth.isAuthenticated() && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) return API_TOKEN;
        return accounts.actorOf(auth);
    }

    public void require(Actor actor, ActPermissions.Perm p) { ActPermissions.require(actor, p, props.requireTotp()); }

    public boolean requireTotp() { return props.requireTotp(); }
}
