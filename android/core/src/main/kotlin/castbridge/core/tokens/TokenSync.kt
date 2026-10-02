package castbridge.core.tokens

import castbridge.core.net.JsonLite
import castbridge.core.net.JsonLite.long
import castbridge.core.net.JsonLite.str

/**
 * Protocole de réconciliation (conception § 6.5) : la TV produit un rapport ([TokenWallet.report], [TokenReport.toJson]) ; le serveur répond par une [Reply] (accusé, bons à créditer, solde serveur,
 * dépense hors ligne permise ou non) ; [apply] la rejoue sur le porte-jetons, sans effet double (un bon déjà crédité est ignoré, un accusé ne recule jamais).
 */
object TokenSync {
    const val REPLY_FORMAT = "castbridge-token-reply-v1"
    const val MAX_GRANTS = 50
    const val MAX_TOKEN_CHARS = 4096

    /** Réponse du serveur. [offlineAllowed] absent = faux (fermé par défaut) ; faux : le serveur n'envoie plus de bons hors ligne à cette installation (rejeu détecté) ; [message] est un texte français facultatif. */
    class Reply(val ackedSeq: Long, val grants: List<String>, val balanceServer: Long, val offlineAllowed: Boolean, val message: String?) {
        fun toJson(): String = JsonLite.write(linkedMapOf("format" to REPLY_FORMAT, "ackedSeq" to ackedSeq, "grants" to grants, "balanceServer" to balanceServer, "offlineAllowed" to offlineAllowed, "message" to message))

        companion object {
            /** La réponse du JSON [json], ou null si elle est hors schéma (bornes, types). */
            @Suppress("UNCHECKED_CAST")
            fun parse(json: String): Reply? = runCatching {
                val o = JsonLite.obj(json)
                val grants = (o["grants"] as? List<Any?> ?: emptyList()).map { it as String }
                require(grants.size <= MAX_GRANTS && grants.all { it.length in 1..MAX_TOKEN_CHARS })
                val acked = o.long("ackedSeq")!!; val bal = o.long("balanceServer") ?: 0L
                require(acked >= 0 && bal >= 0)
                Reply(acked, grants, bal, o["offlineAllowed"] as? Boolean ?: false, o.str("message")?.take(300))
            }.getOrNull()
        }
    }

    /** Ce que [apply] a fait : [credited] bons crédités, [skipped] déjà crédités (rejeu), [rejected] refusés (vérification, licence/installation, bon d'ouverture refusé ou attendu), [acked] l'accusé pris en compte, [reopened] le porte-jetons a été rouvert par un bon d'ouverture (non compté dans [credited]). */
    class Applied(val credited: Int, val skipped: Int, val rejected: Int, val acked: Boolean, val offlineAllowed: Boolean, val balanceServer: Long, val message: String?, val reopened: Boolean = false)

    /**
     * Rejoue [reply] sur [wallet]. [verifyGrant] vérifie un jeton (avec `TokenGrant.verify`, le dernier bon crédité et l'horloge de la TV) et rend le bon accepté, ou null. Le bon d'ouverture (`fresh=1`) passe d'abord, puis les bons ordinaires par
     * numéro croissant ; [nowMs] date la marque d'existence ; idempotent : rejouer la même réponse ne crédite rien de plus.
     */
    fun apply(reply: Reply, wallet: TokenWallet, verifyGrant: (String) -> TokenGrant?): Applied = apply(reply, wallet, verifyGrant, System.currentTimeMillis())

    fun apply(reply: Reply, wallet: TokenWallet, verifyGrant: (String) -> TokenGrant?, nowMs: Long): Applied {
        var credited = 0; var skipped = 0; var rejected = 0; var reopened = false
        val verified = reply.grants.mapNotNull { t -> verifyGrant(t)?.let { it to TokenGrant.fingerprint(t) } ?: run { rejected++; null } }.sortedWith(compareBy({ !it.first.fresh }, { it.first.grant }))
        for ((g, fp) in verified) when (wallet.credit(g, fp, nowMs)) { CreditResult.OK -> credited++; CreditResult.REOPENED -> reopened = true; CreditResult.STALE -> skipped++; else -> rejected++ }
        val acked = wallet.ack(reply.ackedSeq)
        return Applied(credited, skipped, rejected, acked, reply.offlineAllowed, reply.balanceServer, reply.message, reopened)
    }
}
