package castbridge.server.admin;

import castbridge.server.config.CastbridgeProperties;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin web accounts: login lookup, lock after {@value #MAX_FAILURES} wrong passwords for {@value #LOCK_MINUTES} minutes,
 * initial account from CASTBRIDGE_WEB_ADMIN_USER / CASTBRIDGE_WEB_ADMIN_PASSWORD at start-up.
 */
@Service
public class AdminAccounts implements UserDetailsService, ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminAccounts.class);
    static final int MAX_FAILURES = 5;
    static final int LOCK_MINUTES = 15;
    static final int MIN_PASSWORD = 12;

    private final AdminUserRepository repo;
    private final PasswordEncoder encoder;
    private final CastbridgeProperties props;

    public AdminAccounts(AdminUserRepository repo, PasswordEncoder encoder, CastbridgeProperties props) {
        this.repo = repo;
        this.encoder = encoder;
        this.props = props;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        AdminUser u = repo.findByUsername(username == null ? "" : username.trim())
                .orElseThrow(() -> new UsernameNotFoundException("unknown"));
        boolean locked = u.lockedUntil != null && u.lockedUntil.isAfter(Instant.now());
        return User.withUsername(u.username).password(u.passwordHash).accountLocked(locked)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_WEBADMIN"))).build();
    }

    @EventListener
    @Transactional
    public void onFailure(AuthenticationFailureBadCredentialsEvent e) {
        repo.findByUsername(String.valueOf(e.getAuthentication().getPrincipal()).trim()).ifPresent(u -> {
            u.failedAttempts++;
            if (u.failedAttempts >= MAX_FAILURES) {
                u.lockedUntil = Instant.now().plus(LOCK_MINUTES, ChronoUnit.MINUTES);
                u.failedAttempts = 0;
                log.warn("admin account '{}' locked for {} minutes after {} failed logins", u.username, LOCK_MINUTES, MAX_FAILURES);
            }
            repo.save(u);
        });
    }

    @EventListener
    @Transactional
    public void onSuccess(AuthenticationSuccessEvent e) {
        if (!(e.getAuthentication().getPrincipal() instanceof UserDetails ud)) return;
        repo.findByUsername(ud.getUsername()).ifPresent(u -> {
            u.failedAttempts = 0;
            u.lockedUntil = null;
            u.lastLogin = Instant.now();
            repo.save(u);
        });
    }

    /** Creates the initial account (or resets its password on request). The password itself is never logged. */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String user = props.web().adminUser(), pass = props.web().adminPassword();
        if (user == null || user.isBlank()) {
            if (repo.count() == 0) log.warn("no admin web account: set CASTBRIDGE_WEB_ADMIN_USER and CASTBRIDGE_WEB_ADMIN_PASSWORD");
            return;
        }
        Optional<AdminUser> existing = repo.findByUsername(user.trim());
        if (existing.isPresent() && !props.web().resetPassword()) return;
        if (pass == null || pass.length() < MIN_PASSWORD) {
            log.warn("CASTBRIDGE_WEB_ADMIN_PASSWORD missing or shorter than {} characters: admin account '{}' not created", MIN_PASSWORD, user);
            return;
        }
        AdminUser u = existing.orElseGet(() -> {
            AdminUser n = new AdminUser();
            n.username = user.trim();
            n.createdAt = Instant.now();
            return n;
        });
        u.passwordHash = encoder.encode(pass);
        u.failedAttempts = 0;
        u.lockedUntil = null;
        repo.save(u);
        log.info("admin web account '{}' {}", u.username, existing.isPresent() ? "password reset" : "created");
    }
}
