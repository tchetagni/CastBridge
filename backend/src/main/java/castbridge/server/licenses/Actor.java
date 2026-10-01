package castbridge.server.licenses;

import castbridge.server.web.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Who acts: the web account (role + whether its TOTP is enrolled) or the admin token of the scripts.
 *
 * @param name    login or "admin-token" (written to the audit log)
 * @param channel "web" or "api"
 * @param strong  second factor in place (TOTP enrolled) or bearer token (a long secret)
 */
public record Actor(String name, Role role, String channel, boolean strong) {

    public static Actor token() { return new Actor("admin-token", Role.OWNER, "api", true); }

    /** Server-side check of a permission (never trust the page: hidden buttons are cosmetic). */
    public void require(Role.Permission p, boolean requireTotp) {
        if (role == null || !role.can(p)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Votre rôle (" + (role == null ? "aucun" : role.label()) + ") ne permet pas cette action");
        }
        if (p.sensitive && role == Role.OWNER && requireTotp && !strong) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "Activez d'abord la double authentification (TOTP) de votre compte : Licences > Sécurité");
        }
    }
}
