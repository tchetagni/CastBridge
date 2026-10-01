package castbridge.server.licenses;

import java.util.EnumSet;
import java.util.Set;

/** Roles of the admin web accounts for the licence module (OWNER also stands for the admin bearer token of scripts). */
public enum Role {
    /** owner: everything (writes need TOTP when required) */
    OWNER(EnumSet.allOf(Permission.class)),
    /** support: reads and re-issues an existing activation (same device, no new seat) */
    SUPPORT(EnumSet.of(Permission.LICENSE_READ, Permission.DASHBOARD, Permission.AUDIT_READ, Permission.REISSUE)),
    /** read only: lists, files and dashboard */
    READONLY(EnumSet.of(Permission.LICENSE_READ, Permission.DASHBOARD));

    private final Set<Permission> permissions;

    Role(Set<Permission> permissions) { this.permissions = permissions; }

    public boolean can(Permission p) { return permissions.contains(p); }

    public static Role parse(String s) {
        if (s == null) return null;
        try {
            return valueOf(s.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public String label() {
        return switch (this) {
            case OWNER -> "Propriétaire";
            case SUPPORT -> "Support";
            case READONLY -> "Lecture seule";
        };
    }

    /** What a role may do. {@code sensitive} = changes something: refused to an OWNER without TOTP when TOTP is required. */
    public enum Permission {
        LICENSE_READ(false), DASHBOARD(false), AUDIT_READ(false),
        REISSUE(false),
        ISSUE_NEW(true), LICENSE_WRITE(true), CLIENT_WRITE(true), CLIENT_PRIVACY(true), PRODUCT_WRITE(true),
        LEDGER_EXPORT(true), LEDGER_IMPORT(true), ACCOUNT_ADMIN(true);

        public final boolean sensitive;

        Permission(boolean sensitive) { this.sensitive = sensitive; }
    }
}
