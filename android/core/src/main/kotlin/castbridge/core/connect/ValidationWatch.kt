package castbridge.core.connect

/**
 * Le verdict « Internet validé » du SYSTÈME (`NET_CAPABILITY_VALIDATED`), suivi réseau par réseau, et le moment où il CHANGE (R-41, audit anti-régression 2026-10-07 b, I-2).
 *
 * La TV ne lisait ce verdict qu'au tour de 60 s de sa boucle réseau : au retour du Wi-Fi, ou quand Android constate que la box a perdu Internet (ou l'a retrouvé), elle restait sur
 * l'ancien verdict jusqu'à une minute. Elle écoute maintenant `onCapabilitiesChanged` : ce rappel part aussi à chaque variation de débit ou de signal, DONC seul un changement du
 * verdict d'ensemble compte (« au moins un réseau validé »), jamais le rappel lui-même (sinon une sonde partirait à chaque variation de débit du Wi-Fi). Le premier rapport ne fait que
 * poser la référence (l'arrivée du réseau a déjà demandé une revérification).
 *
 * Pur : [N] est l'identité d'un réseau (`android.net.Network` dans l'application).
 */
class ValidationWatch<N : Any> {
    private val byNetwork = HashMap<N, Boolean>()
    private var last: Boolean? = null

    /** Les capacités de [network] ont été rapportées : vrai quand le verdict d'ensemble vient de CHANGER (pas au tout premier rapport). */
    @Synchronized fun reported(network: N, validated: Boolean): Boolean { byNetwork[network] = validated; return changed() }

    /** [network] a disparu : vrai quand le verdict d'ensemble en est changé. */
    @Synchronized fun lost(network: N): Boolean { byNetwork.remove(network); return changed() }

    private fun changed(): Boolean {
        val now = byNetwork.values.any { it }
        val was = last
        last = now
        return was != null && was != now
    }
}
