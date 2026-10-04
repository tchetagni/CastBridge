package castbridge.server.licenses;

import castbridge.server.web.ApiException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Roles and second factor of the admin web accounts: who is OWNER, SUPPORT or READONLY, TOTP enrolment and check at login
 * (a wrong code counts as a wrong password for the lock-out), resolution of the {@link Actor} of a web request.
 */
@Service
public class LicenseAccounts {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final TotpVault vault;
    private final AuditLog audit;
    private final LicenseProperties props;

    public LicenseAccounts(JdbcTemplate jdbc, PasswordEncoder encoder, TotpVault vault, AuditLog audit, LicenseProperties props) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.vault = vault;
        this.audit = audit;
        this.props = props;
    }

    public record AccountRow(String username, Role role, boolean totpEnabled, Instant lastLogin, Instant lockedUntil) {}

    public record Enrollment(String secret, String uri) {}

    public List<AccountRow> list() {
        return jdbc.query("SELECT username, role, totp_enabled, last_login, locked_until FROM admin_user ORDER BY username LIMIT 200",
                (rs, i) -> new AccountRow(rs.getString("username"), Role.parse(rs.getString("role")), rs.getBoolean("totp_enabled"),
                        LicenseService.inst(rs, "last_login"), LicenseService.inst(rs, "locked_until")));
    }

    /**
     * L'administrateur NOMMÉ d'une action sensible par l'API (dons du portefeuille, décision sur une activation notifiée) : en-têtes {@code X-Admin-User} (compte) et {@code X-Totp} (code à usage
     * unique, même coffre et même vérificateur que la connexion web). Compte inconnu, rôle autre que propriétaire, TOTP non activé, code faux ou déjà utilisé : 403. Le code accepté est consommé.
     */
    public Actor verifyNamedAdmin(String user, String totp) {
        if (user == null || user.isBlank() || totp == null || totp.isBlank()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Double authentification requise : en-têtes X-Admin-User (compte) et X-Totp (code à usage unique)");
        }
        String name = user.trim();
        Role role = roleOf(name);
        if (role != Role.OWNER) throw new ApiException(HttpStatus.FORBIDDEN, "Seul un compte propriétaire peut faire cette action");
        if (!totpEnabled(name)) throw new ApiException(HttpStatus.FORBIDDEN, "Activez d'abord la double authentification (TOTP) de votre compte : Licences > Sécurité");
        if (!checkLoginCode(name, totp.trim())) throw new ApiException(HttpStatus.FORBIDDEN, "Code TOTP incorrect ou déjà utilisé");
        return new Actor(name, Role.OWNER, "api", true);
    }

    public Role roleOf(String username) {
        List<String> r = jdbc.queryForList("SELECT role FROM admin_user WHERE username = ?", String.class, username);
        return r.isEmpty() ? null : Role.parse(r.get(0));
    }

    public boolean totpEnabled(String username) {
        List<Boolean> r = jdbc.queryForList("SELECT totp_enabled FROM admin_user WHERE username = ?", Boolean.class, username);
        return !r.isEmpty() && Boolean.TRUE.equals(r.get(0));
    }

    /** The actor of a web request: the role comes from the database (not from what the page claims). Unknown account = no role. */
    public Actor actorOf(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) return new Actor("anonymous", null, "web", false);
        String name = auth.getName();
        Role role = roleOf(name);
        return new Actor(name, role, "web", totpEnabled(name));
    }

    @Transactional
    public AccountRow create(Actor actor, String username, String password, String role) {
        actor.require(Role.Permission.ACCOUNT_ADMIN, props.requireTotp());
        if (username == null || !Validate.USERNAME.matcher(username.trim()).matches()) throw ApiException.badRequest("Identifiant : 3 à 63 caractères (lettres, chiffres, . _ @ -)");
        if (password == null || password.length() < 12) throw ApiException.badRequest("Mot de passe : 12 caractères au minimum");
        Role r = Role.parse(role);
        if (r == null) throw ApiException.badRequest("Rôle : OWNER, SUPPORT ou READONLY");
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM admin_user WHERE username = ?", Integer.class, username.trim());
        if (n != null && n > 0) throw ApiException.conflict("Cet identifiant existe déjà");
        jdbc.update("INSERT INTO admin_user (username, password_hash, failed_attempts, created_at, role, totp_enabled) VALUES (?,?,0,?,?,FALSE)", username.trim(),
                encoder.encode(password), Timestamp.from(Instant.now()), r.name());
        audit.record(actor, "ACCOUNT_CREATE", "ACCOUNT", username.trim(), null, Map.of("role", r.name()));
        return list().stream().filter(a -> a.username().equals(username.trim())).findFirst().orElseThrow();
    }

    @Transactional
    public void setRole(Actor actor, String username, String role, String reason) {
        actor.require(Role.Permission.ACCOUNT_ADMIN, props.requireTotp());
        Role r = Role.parse(role);
        if (r == null) throw ApiException.badRequest("Rôle : OWNER, SUPPORT ou READONLY");
        Role current = roleOf(username);
        if (current == null) throw ApiException.notFound("Compte introuvable");
        if (current == Role.OWNER && r != Role.OWNER) {
            Integer owners = jdbc.queryForObject("SELECT COUNT(*) FROM admin_user WHERE role = 'OWNER'", Integer.class);
            if (owners != null && owners <= 1) throw ApiException.conflict("Il doit rester au moins un propriétaire");
        }
        jdbc.update("UPDATE admin_user SET role = ? WHERE username = ?", r.name(), username);
        audit.record(actor, "ACCOUNT_ROLE", "ACCOUNT", username, Validate.text(reason, "Motif", 500, false), Map.of("from", current.name(), "to", r.name()));
    }

    /** Starts (or restarts, while not yet confirmed) the TOTP enrolment of the actor's own account. */
    @Transactional
    public Enrollment startEnrollment(Actor actor) {
        if (!vault.available()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Clé de chiffrement TOTP absente (CASTBRIDGE_LICENSES_TOTP_KEY ou fichier license-totp.key dans le dossier des secrets)");
        }
        if (actor.role() == null) throw new ApiException(HttpStatus.FORBIDDEN, "Compte inconnu");
        if (totpEnabled(actor.name())) throw ApiException.conflict("La double authentification est déjà active sur ce compte");
        byte[] secret = Totp.newSecret();
        jdbc.update("UPDATE admin_user SET totp_secret_enc = ?, totp_enabled = FALSE, totp_last_step = NULL WHERE username = ?", vault.seal(secret), actor.name());
        return new Enrollment(Hashing.base32(secret), Totp.uri("CastBridge", actor.name(), secret));
    }

    @Transactional
    public void confirmEnrollment(Actor actor, String code) {
        List<Map<String, Object>> r = jdbc.queryForList("SELECT totp_secret_enc FROM admin_user WHERE username = ? AND totp_enabled = FALSE", actor.name());
        if (r.isEmpty() || r.get(0).get("totp_secret_enc") == null) throw ApiException.conflict("Aucune activation en cours : recommencez");
        byte[] secret = vault.open((String) r.get(0).get("totp_secret_enc"));
        long step = Totp.verify(secret, code, Totp.stepAt(Instant.now().getEpochSecond()), null);
        if (step < 0) throw ApiException.badRequest("Code incorrect : vérifiez l'heure de votre téléphone et recommencez");
        jdbc.update("UPDATE admin_user SET totp_enabled = TRUE, totp_last_step = ? WHERE username = ?", step, actor.name());
        audit.record(actor, "TOTP_ENABLE", "ACCOUNT", actor.name(), null, null);
    }

    /** An owner switches the second factor of an account off (lost phone): reason mandatory, audited. */
    @Transactional
    public void disableTotp(Actor actor, String username, String reason) {
        actor.require(Role.Permission.ACCOUNT_ADMIN, props.requireTotp());
        String why = Validate.reason(reason);
        int n = jdbc.update("UPDATE admin_user SET totp_enabled = FALSE, totp_secret_enc = NULL, totp_last_step = NULL WHERE username = ?", username);
        if (n == 0) throw ApiException.notFound("Compte introuvable");
        audit.record(actor, "TOTP_DISABLE", "ACCOUNT", username, why, null);
    }

    /**
     * Login check, called by the authentication provider after the password: true when no TOTP is enrolled, or when the code is
     * right and not yet used (replay refused). Each accepted code is single-use.
     */
    @Transactional
    public boolean checkLoginCode(String username, String code) {
        List<Map<String, Object>> r = jdbc.queryForList("SELECT totp_enabled, totp_secret_enc, totp_last_step FROM admin_user WHERE username = ? FOR UPDATE", username);
        if (r.isEmpty() || !Boolean.TRUE.equals(r.get(0).get("totp_enabled"))) return true;
        if (!vault.available() || r.get(0).get("totp_secret_enc") == null) return false; // enrolled but unreadable: closed, not open
        Number last = (Number) r.get(0).get("totp_last_step");
        long step = Totp.verify(vault.open((String) r.get(0).get("totp_secret_enc")), code, Totp.stepAt(Instant.now().getEpochSecond()), last == null ? null : last.longValue());
        if (step < 0) return false;
        jdbc.update("UPDATE admin_user SET totp_last_step = ? WHERE username = ?", step, username);
        return true;
    }
}
