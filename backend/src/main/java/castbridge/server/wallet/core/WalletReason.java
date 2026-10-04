package castbridge.server.wallet.core;

/** Motifs de refus fermés (conception W22 § 7.6) et leur texte français, identiques sur la TV et le téléphone. */
public enum WalletReason {
    INSUFFICIENT("Solde insuffisant : %s disponibles"),
    OFFLINE("Connexion Internet nécessaire"),
    BOUND_OTHER_TV("Ce compte est lié à une autre TV"),
    TRIAL_NO_MBOKO("Mises MBOKO : version complète"),
    STAKES_SUSPENDED("Mises suspendues pour maintenance : les parties sans mise restent ouvertes"),
    DAILY_CAP("Plafond du jour atteint : réessayez demain"),
    CODE_UNKNOWN("Code de réception inconnu"),
    CODE_EXPIRED("Code expiré : demandez-en un nouveau"),
    VOUCHER_USED("Bon déjà utilisé"),
    VOUCHER_OTHER_TV("Ce bon est destiné à une autre TV"),
    VOUCHER_EXPIRED("Bon expiré"),
    VOUCHER_BAD("Bon illisible"),
    FROZEN("Compte en vérification : contactez votre point focal"),
    ACTIVATE("Activez la TV pour recevoir des jetons"),
    CLOCK("Vérifiez l'heure de la TV"),
    // Motifs propres au cœur (erreurs de programmation, de rejeu ou de règlement : pas montrés tels quels au joueur)
    UNBALANCED("Transaction déséquilibrée : la somme des écritures doit être nulle pour chaque monnaie"),
    IDEM_CONFLICT("Cette clé d'opération a déjà servi pour une autre opération"),
    ESCROW_UNKNOWN("Blocage inconnu"),
    ESCROW_CLOSED("Blocage déjà réglé ou rendu"),
    BAD_TXN("Transaction invalide");

    private final String template;

    WalletReason(String template) { this.template = template; }

    /** Texte français ; pour INSUFFICIENT, {@code args[0]} = le solde disponible avec sa monnaie, ex. « 120 NDEM ». */
    public String text(Object... args) {
        if (!template.contains("%s")) return template;
        return args.length == 0 ? "Solde insuffisant" : String.format(template, args);
    }
}
