package castbridge.core.wallet.ui

import castbridge.core.wallet.WalletCurrency
import castbridge.core.wallet.WalletReason

/**
 * Les refus du serveur en français, IDENTIQUES sur CastBridge-TV et CastBridge (conception W22 § 7.6) : le motif fermé du serveur (`details[0]`) donne le texte ; un motif inconnu (le serveur peut
 * en ajouter) n'est JAMAIS une erreur de programme : texte générique + code assaini. Aucun `valueOf` : la lecture est par comparaison de noms.
 */
object WalletMessages {
    enum class Kind { UNAVAILABLE, ACTIVATE, BIND_PROOF, NETWORK, RETRY_LATER, REFUSED, UNKNOWN }
    data class Shown(val text: String, val code: String?, val kind: Kind)

    const val UNAVAILABLE_TEXT = "Jetons : service indisponible"
    private val SAFE = Regex("^[A-Z0-9_]{1,40}$")

    /** Motifs que le serveur émet en plus de `WalletReason` du cœur (rapports w22-02 et w22-05) et leur texte. */
    private val EXTRA = linkedMapOf(
        "LICENSE_PENDING" to "Licence en attente d'enregistrement",
        "CONVERT_SUSPENDED" to "Conversion suspendue pour maintenance : réessayez plus tard",
        "TRANSFER_SUSPENDED" to "Transferts suspendus pour maintenance : réessayez plus tard",
        "TRIAL_LIMIT" to "Limite de la version d'essai atteinte : le transfert s'ouvrira plus tard",
        "RATE_LIMIT" to "Trop d'opérations à la suite : réessayez dans un instant",
        "SETTLE_CAP" to "Plafond de gains atteint : le règlement est reporté, contactez votre point focal",
        "RESULT_AFTER_REFUND" to "Cette partie a été remboursée : aucun gain à régler",
        "RESULT_BAD" to "Résultat de partie illisible : rien n'est réglé",
        "RESULT_FORGED" to "Résultat de partie non reconnu : rien n'est réglé",
        "CODE_LIMIT" to "Trois codes de réception sont déjà actifs : attendez l'expiration de l'un d'eux",
        "LOOKUP_LIMIT" to "Trop de codes essayés cette heure : réessayez plus tard",
        "IDEM_CONFLICT" to "Cette opération a déjà été envoyée avec d'autres valeurs : recommencez",
        "UNBALANCED" to "Opération refusée : écritures déséquilibrées",
        "BAD_TXN" to "Opération invalide : vérifiez les valeurs saisies",
        "ESCROW_UNKNOWN" to "Mise inconnue",
        "ESCROW_CLOSED" to "Mise déjà réglée ou rendue",
    )
    private val LATER = setOf("RATE_LIMIT", "CONVERT_SUSPENDED", "TRANSFER_SUSPENDED", "LOOKUP_LIMIT", "CODE_LIMIT")
    private const val BIND_TEXT = "Cette TV doit prouver qu'elle est bien la sienne : vérifiez l'heure de la TV, puis réessayez"

    /**
     * @param status code HTTP ; @param reason `details[0]` ou null ; @param serverMessage `message` du serveur (utilisé pour un 400 sans motif) ;
     * @param available solde disponible dans la monnaie de l'opération (pour « Solde insuffisant : 120 NDEM disponibles »), lu de l'instantané signé.
     */
    fun of(status: Int, reason: String?, serverMessage: String? = null, available: Long? = null, cur: WalletCurrency = WalletCurrency.NDEM): Shown {
        val code = reason?.takeIf { SAFE.matches(it) }
        if (code != null) {
            return when (code) {
                "ACTIVATE" -> Shown(WalletReason.ACTIVATE.text(), code, Kind.ACTIVATE)
                "BIND_PROOF" -> Shown(BIND_TEXT, code, Kind.BIND_PROOF)
                "OFFLINE" -> Shown(WalletReason.OFFLINE.text(), code, Kind.NETWORK)
                "INSUFFICIENT" -> Shown(WalletReason.INSUFFICIENT.text(available, cur), code, Kind.REFUSED)
                else -> {
                    val known = WalletReason.values().firstOrNull { it.name == code }
                    val extra = EXTRA[code]
                    when {
                        known != null -> Shown(known.text(), code, Kind.REFUSED)
                        extra != null -> Shown(extra, code, if (code in LATER) Kind.RETRY_LATER else Kind.REFUSED)
                        else -> Shown("Opération refusée (code $code)", code, Kind.UNKNOWN)
                    }
                }
            }
        }
        return when {
            status == 404 || status == 503 -> Shown(UNAVAILABLE_TEXT, null, Kind.UNAVAILABLE)
            status == 401 -> Shown("Cette TV n'est pas reconnue du serveur : réessayez plus tard", null, Kind.RETRY_LATER)
            status == 429 -> Shown(EXTRA.getValue("RATE_LIMIT"), null, Kind.RETRY_LATER)
            status == 400 -> Shown(serverMessage?.takeIf { it.isNotBlank() }?.take(200) ?: "Demande refusée", null, Kind.REFUSED)
            status >= 500 -> Shown("Le serveur ne répond pas correctement : réessayez plus tard", null, Kind.RETRY_LATER)
            else -> Shown("Opération refusée", null, Kind.UNKNOWN)
        }
    }

    /** Pas de réseau : toute opération attend la connexion. */
    fun offline(): Shown = Shown(WalletReason.OFFLINE.text(), null, Kind.NETWORK)
}
