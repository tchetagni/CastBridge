package castbridge.server.admin;

import castbridge.server.licenses.LicenseAccounts;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

/**
 * The admin login: password (BCrypt) THEN, for an account that enrolled TOTP, the 6-digit code of the form field "totp".
 * A wrong or reused code is a bad-credentials failure like a wrong password (same message, counted by the same lock-out).
 * This is the only AuthenticationProvider of the application, so the framework's default password-only provider is not used.
 */
public class AdminAuthenticationProvider extends DaoAuthenticationProvider {
    private final LicenseAccounts accounts;

    public AdminAuthenticationProvider(UserDetailsService users, PasswordEncoder encoder, LicenseAccounts accounts) {
        super(encoder);
        setUserDetailsService(users);
        this.accounts = accounts;
    }

    @Override
    protected void additionalAuthenticationChecks(UserDetails user, UsernamePasswordAuthenticationToken auth) {
        super.additionalAuthenticationChecks(user, auth);
        String code = auth.getDetails() instanceof TotpDetails d ? d.totp() : null;
        if (!accounts.checkLoginCode(user.getUsername(), code)) throw new BadCredentialsException("Bad credentials");
    }

    /** Carries the "totp" form field next to the usual web details. */
    public static final class TotpDetails extends WebAuthenticationDetails {
        private final String totp;

        public TotpDetails(HttpServletRequest request) {
            super(request);
            String t = request.getParameter("totp");
            this.totp = t == null ? null : t.replace(" ", "").trim();
        }

        public String totp() { return totp; }
    }
}
