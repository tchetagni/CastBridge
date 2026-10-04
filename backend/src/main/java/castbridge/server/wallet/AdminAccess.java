package castbridge.server.wallet;

import castbridge.server.licenses.Actor;
import castbridge.server.licenses.AuditLog;
import castbridge.server.licenses.LicenseAccounts;
import castbridge.server.licenses.Role;
import castbridge.server.web.ApiException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Second facteur et journal d'audit des actions SENSIBLES du portefeuille (dons, réaffectation de liaison ; audit Opus H2) : réutilise tel quel le module des licences. Le jeton porteur ne suffit
 * plus (il ouvre seulement la route) : l'administrateur s'identifie par son COMPTE ({@code X-Admin-User}) et un code TOTP à usage unique ({@code X-Totp}, même coffre et même vérificateur que la
 * connexion web), doit être {@code OWNER} avec le TOTP activé ; chaque action est inscrite dans le journal d'audit CHAÎNÉ des licences ({@code lic_audit}), au nom de la personne.
 */
@Component
@WalletModuleConfig.Enabled
public class AdminAccess {
    private final LicenseAccounts accounts;
    private final AuditLog audit;
    private final TransactionTemplate tx;

    public AdminAccess(LicenseAccounts accounts, AuditLog audit, PlatformTransactionManager tm) {
        this.accounts = accounts;
        this.audit = audit;
        this.tx = new TransactionTemplate(tm);
    }

    /** L'administrateur nommé, ou 403 : compte inconnu, rôle autre que propriétaire, TOTP non activé, code faux ou déjà utilisé. Le code accepté est consommé. */
    public Actor verify(String user, String totp) {
        if (user == null || user.isBlank() || totp == null || totp.isBlank()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Double authentification requise : en-têtes X-Admin-User (compte) et X-Totp (code à usage unique)");
        }
        String name = user.trim();
        Role role = accounts.roleOf(name);
        if (role != Role.OWNER) throw new ApiException(HttpStatus.FORBIDDEN, "Seul un compte propriétaire peut faire cette action");
        if (!accounts.totpEnabled(name)) throw new ApiException(HttpStatus.FORBIDDEN, "Activez d'abord la double authentification (TOTP) de votre compte : Licences > Sécurité");
        if (!accounts.checkLoginCode(name, totp.trim())) throw new ApiException(HttpStatus.FORBIDDEN, "Code TOTP incorrect ou déjà utilisé");
        return new Actor(name, Role.OWNER, "api", true);
    }

    /** Inscrit l'action au journal d'audit chaîné (aucune clé, aucun jeton : identité, monnaie, montant, motif). */
    public void record(Actor actor, String action, String identity, String reason, Map<String, ?> details) {
        tx.executeWithoutResult(s -> audit.record(actor, action, "WALLET", identity, reason, details));
    }
}
