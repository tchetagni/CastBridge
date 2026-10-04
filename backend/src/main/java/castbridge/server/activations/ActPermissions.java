package castbridge.server.activations;

import castbridge.server.licenses.Actor;
import castbridge.server.licenses.Role;
import castbridge.server.web.ApiException;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;

/**
 * The permissions of the module (design 6.2), kept here and mapped by {@link Role} WITHOUT touching {@code Role.java} of the licence module. A sensitive permission needs
 * the second factor (TOTP) of an OWNER account while the licence module requires it; the admin bearer token is an OWNER with a "strong" second factor (a long secret).
 * A session of the owner phone console (channel "phone") never goes beyond reading and uploading a journal, whatever its role.
 */
public final class ActPermissions {
    private ActPermissions() {}

    public enum Perm {
        /** lists, fiches, dashboard, alerts, tools */
        ACT_READ(false),
        /** acknowledge an alert */
        ACT_ALERT_ACK(false),
        /** close an alert with a reason */
        ACT_ALERT_DECIDE(true),
        /** exports, checkpoints, the audit of the reads */
        ACT_EXPORT(true),
        /** upload a signed journal (the batch is signed: no second factor) */
        ACT_JOURNAL_UPLOAD(false),
        /** cold archive and removal from the database */
        ACT_ARCHIVE(true);

        public final boolean sensitive;

        Perm(boolean sensitive) { this.sensitive = sensitive; }
    }

    private static final Map<Role, Set<Perm>> BY_ROLE = Map.of(
            Role.OWNER, EnumSet.allOf(Perm.class),
            Role.SUPPORT, EnumSet.of(Perm.ACT_READ, Perm.ACT_ALERT_ACK, Perm.ACT_JOURNAL_UPLOAD),
            Role.READONLY, EnumSet.of(Perm.ACT_READ));

    private static final Set<Perm> CONSOLE_SCOPE = EnumSet.of(Perm.ACT_READ, Perm.ACT_JOURNAL_UPLOAD);

    /** Role-based answer, with the second factor required for the sensitive permissions of an OWNER. */
    public static boolean allows(Actor actor, Perm p) { return allows(actor, p, true); }

    public static boolean allows(Actor actor, Perm p, boolean requireTotp) {
        if (actor == null || actor.role() == null) return false;
        if ("phone".equals(actor.channel()) && !CONSOLE_SCOPE.contains(p)) return false;
        if (!BY_ROLE.getOrDefault(actor.role(), Set.of()).contains(p)) return false;
        return !(p.sensitive && actor.role() == Role.OWNER && requireTotp && !actor.strong());
    }

    /** Server-side check (never trust the page: hidden buttons are cosmetic): 403 with the reason. */
    public static void require(Actor actor, Perm p, boolean requireTotp) {
        if (actor == null || actor.role() == null || !BY_ROLE.getOrDefault(actor.role(), Set.of()).contains(p) || ("phone".equals(actor.channel()) && !CONSOLE_SCOPE.contains(p))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Votre rôle (" + (actor == null || actor.role() == null ? "aucun" : actor.role().label()) + ") ne permet pas cette action");
        }
        if (p.sensitive && actor.role() == Role.OWNER && requireTotp && !actor.strong()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Activez d'abord la double authentification (TOTP) de votre compte : Licences > Sécurité");
        }
    }
}
