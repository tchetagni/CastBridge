package castbridge.core.quiz.online

import castbridge.core.connect.Routes
import castbridge.core.ux.SignalLevel

/** Liaison de CETTE TV avec le service : bonne, en reprise (secondes écoulées) ou perdue (60 s). */
sealed class TvLink {
    object Online : TvLink() { override fun toString() = "Online" }
    data class Resuming(val sec: Int) : TvLink()
    object Lost : TvLink() { override fun toString() = "Lost" }
}

/**
 * La cause LOCALE « liaison de cette TV » (DESIGN-W20-AMENDEMENT § 2.7) : le service ne voit pas que la TV passe par la passerelle Bluetooth d'un téléphone
 * ni que sa liaison reprend ; la TV le sait. Elle ne peut que BAISSER le niveau reçu du serveur (jamais le relever) : dégradé ≠ panne, donc orange pour la
 * passerelle et la reprise, rouge seulement à la perte ou au certificat invalide, noir sans réseau hors partie. Pure : le mot et la forme suivent toujours le niveau.
 */
object LinkCause {
    const val GATEWAY_TEXT = "Internet par le téléphone (Bluetooth) · lent"
    const val LOST_TEXT = "Partie Internet perdue"
    const val TLS_TEXT = "Internet : impossible · certificat non valide"
    const val NO_NETWORK_TEXT = "Internet : la TV n'est pas connectée"

    /** Ordre de gravité d'AFFICHAGE : vert < orange < rouge < noir (le noir « sans réseau » prime : il n'y a plus de partie à montrer). */
    private fun rank(l: SignalLevel) = when (l) { SignalLevel.GREEN -> 0; SignalLevel.ORANGE -> 1; SignalLevel.RED -> 2; SignalLevel.BLACK -> 3 }

    private class Cause(val level: SignalLevel, val text: String, val action: String? = null)

    fun lower(server: SafetyView, via: Routes.Via?, link: TvLink, tvHasNetwork: Boolean, tlsInvalid: Boolean = false): SafetyView {
        val causes = ArrayList<Cause>()
        if (!tvHasNetwork) causes += Cause(SignalLevel.BLACK, NO_NETWORK_TEXT, "Connexion & réglages")
        if (tlsInvalid) causes += Cause(SignalLevel.RED, TLS_TEXT, "Fermer Internet")
        when (link) {
            TvLink.Lost -> causes += Cause(SignalLevel.RED, LOST_TEXT, "Réessayer")
            is TvLink.Resuming -> causes += Cause(SignalLevel.ORANGE, "Internet · liaison en reprise (${link.sec} s)")
            TvLink.Online -> {}
        }
        if (via == Routes.Via.GATEWAY) causes += Cause(SignalLevel.ORANGE, GATEWAY_TEXT)
        val top = causes.maxByOrNull { rank(it.level) } ?: return server
        if (rank(top.level) < rank(server.level)) {
            // Le serveur est déjà plus bas : rien ne change, mais les causes locales sont ajoutées au détail. (À gravité égale, la cause locale parle : elle est plus précise.)
            return if (causes.isEmpty()) server else server.copy(detail = server.detail + causes.map { it.text })
        }
        val head = causes.first { rank(it.level) == rank(top.level) }
        val word = if (head.level == SignalLevel.GREEN) "Partie sûre" else head.level.word
        return SafetyView(server.scope, head.level, word, head.text, head.action, causes.map { it.text } + server.detail)
    }
}
