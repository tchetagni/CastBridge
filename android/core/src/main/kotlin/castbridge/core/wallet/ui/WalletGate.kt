package castbridge.core.wallet.ui

import castbridge.core.wallet.Snapshot
import castbridge.core.wallet.WalletReason

/** Ce que le serveur dit du module portefeuille : inconnu (jamais joint), ouvert, ou indisponible (404 / 503 sans motif). Gardé entre deux lancements. */
enum class ServerWallet { UNKNOWN, ENABLED, UNAVAILABLE }
enum class WalletOp { CONVERT, SEND, RECEIVE, HISTORY }

/** Une action est-elle permise maintenant ? Sinon [reason] est le texte français affiché sous le bouton (jamais un bouton grisé muet). */
data class OpGate(val allowed: Boolean, val reason: String?)

/** Visibilité de la carte et de l'écran, et état de chaque action (hors ligne, compte gelé, interrupteurs de la politique). Pur : aucune horloge, aucun réseau. */
object WalletGate {
    /** La TV est activée : au moins une activation acceptée et pas d'état verrouillé. */
    fun activated(activationCount: Int, locked: Boolean): Boolean = activationCount > 0 && !locked

    /**
     * La carte de l'accueil : TV activée ET réglage local `wallet.enabled`, sauf si le serveur a répondu « indisponible » (404 / 503 : carte cachée). « Toute activation donne lieu à un
     * portefeuille » (propriétaire, 2026-10-04) : la carte existe donc dès l'activation, même avant la première synchronisation (elle dit alors « en attente », sans aucun chiffre :
     * voir [WalletStatus]). [hasSnapshot] n'y change rien : il est gardé pour que l'appelant reste explicite.
     */
    @Suppress("UNUSED_PARAMETER")
    fun cardVisible(activated: Boolean, flag: Boolean, server: ServerWallet, hasSnapshot: Boolean): Boolean = activated && flag && server != ServerWallet.UNAVAILABLE

    /** L'écran « Mes jetons » s'ouvre même si le service est indisponible : c'est lui qui le dit. */
    fun screenOpenable(activated: Boolean, flag: Boolean): Boolean = activated && flag

    /**
     * L'état du service après une réponse : 2xx = ouvert ; 404 ou 503 SANS motif fermé = indisponible (module éteint ou clé absente) ; tout le reste (refus métier, 429, 500, 401, réseau coupé :
     * [status] null) ne dit rien sur l'ouverture du module : on garde l'état connu.
     */
    fun serverAfter(prev: ServerWallet, status: Int?, reason: String?): ServerWallet = when {
        status == null -> prev
        status in 200..299 -> ServerWallet.ENABLED
        (status == 404 || status == 503) && reason == null -> ServerWallet.UNAVAILABLE
        else -> prev
    }

    fun gate(op: WalletOp, online: Boolean, snapshot: Snapshot?, policy: PolicyView?, hasCachedHistory: Boolean = false): OpGate {
        fun no(text: String) = OpGate(false, text)
        if (!online) return if (op == WalletOp.HISTORY && hasCachedHistory) OpGate(true, null) else no(WalletReason.OFFLINE.text())
        return when (op) {
            WalletOp.RECEIVE, WalletOp.HISTORY -> OpGate(true, null)
            WalletOp.CONVERT, WalletOp.SEND -> when {
                snapshot == null -> no("Disponible après la première synchronisation")
                snapshot.flags.frozen -> no(WalletReason.FROZEN.text())
                op == WalletOp.CONVERT && policy?.convert == false -> no(WalletMessages.of(409, "CONVERT_SUSPENDED").text)
                op == WalletOp.SEND && policy?.transfer == false -> no(WalletMessages.of(409, "TRANSFER_SUSPENDED").text)
                else -> OpGate(true, null)
            }
        }
    }
}
