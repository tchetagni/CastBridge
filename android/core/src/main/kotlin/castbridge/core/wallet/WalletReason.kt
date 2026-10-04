package castbridge.core.wallet

/**
 * Motifs de refus du portefeuille, IDENTIQUES sur CastBridge-TV et sur CastBridge (conception W22 § 7.6). Les clés sont celles de `WalletReason` côté serveur (cahier w22-01) : jamais renommées.
 * L'écran n'invente aucun texte : il affiche [text]. Le câblage dans `C/sync/Reason.kt` et l'affichage sur les deux applications relèvent du cahier w22-07.
 */
enum class WalletReason(private val template: String) {
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
    CLOCK("Vérifiez l'heure de la TV");

    /** Le texte français. Pour [INSUFFICIENT], [available] est le solde disponible (ex. 120 de [cur]) ; sans lui : « Solde insuffisant ». */
    fun text(available: Long? = null, cur: WalletCurrency = WalletCurrency.NDEM): String = when {
        this == INSUFFICIENT && available == null -> "Solde insuffisant"
        this == INSUFFICIENT -> template.format("${WalletView.thousands(available!!)} ${cur.name}")
        else -> template
    }
}
