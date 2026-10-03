package castbridge.core.quiz.online

import castbridge.core.ux.Shape
import castbridge.core.ux.SignalLevel

enum class TlsState { OK, INVALID }

/** État d'une liaison : bonne, en reprise ou perdue. */
enum class Link3 { OK, RESUMING, LOST }

/** Ce que l'autorité sait de la partie (docs/coordination/DESIGN-W20-QUIZ-EN-LIGNE-2026-10-03.md § 1.3). Les valeurs par défaut sont « tout va bien ». */
data class SafetyFacts(
    val scope: PlayScope,
    val tls: TlsState = TlsState.OK,
    val serverLink: Link3 = Link3.OK,
    /** Secondes écoulées depuis la perte du serveur (utile si [serverLink] n'est pas OK). */
    val serverLostSec: Int = 0,
    val lanLink: Link3 = Link3.OK,
    val remotePlayers: Int = 0,
    val localPlayers: Int = 0,
    val guestsViaQr: Int = 0,
    val kidProfile: Boolean = false,
    val internetAllowedByParent: Boolean = false,
    val ticketValid: Boolean = true,
    val clockDoubt: Boolean = false,
    val tvHasInternet: Boolean = true,
    val internetDisabledByOwner: Boolean = false,
    /** Joueur distant en reprise, s'il y en a un. */
    val resumingPlayer: String? = null,
    /** Joueur local déconnecté depuis plus de 20 s pendant une question. */
    val disconnectedPlayer: String? = null,
    val rttMs: Int = 0,
)

/** Le signe « Partie sûre » : identique sur CastBridge et CastBridge-TV ; la forme et le mot accompagnent toujours la couleur. */
data class SafetyView(val scope: PlayScope, val level: SignalLevel, val word: String, val text: String, val action: String?, val detail: List<String>) {
    val shape: Shape get() = level.shape
}

/** Table figée du § 1.3 : la première cause par gravité est dite, `detail` les liste toutes. Pure. */
object SafetySign {
    const val LOST_AFTER_SEC = 60
    const val SLOW_RTT_MS = 1_500
    private const val WIFI_ACTION = "Changer le mot de passe Wi-Fi Direct à la fin"

    private class Cause(val level: SignalLevel, val text: String, val action: String? = null)

    fun of(f: SafetyFacts): SafetyView {
        val causes = when (f.scope) {
            PlayScope.TV_ONLY -> listOf(Cause(SignalLevel.GREEN, "TV seule · personne d'autre ne peut entrer"))
            PlayScope.LAN -> lan(f)
            PlayScope.INTERNET -> internet(f)
        }
        val head = causes.maxByOrNull { it.level.severity }!!.let { top -> causes.first { it.level.severity == top.level.severity } }
        val word = if (head.level == SignalLevel.GREEN) "Partie sûre" else head.level.word
        return SafetyView(f.scope, head.level, word, head.text, head.action, causes.map { it.text })
    }

    private fun lan(f: SafetyFacts): List<Cause> {
        val c = ArrayList<Cause>()
        if (f.lanLink == Link3.LOST) c += Cause(SignalLevel.RED, "Réseau local perdu : les téléphones ne peuvent plus répondre", "Télécommande : continuer seul, ou attendre")
        if (f.guestsViaQr >= 1) c += Cause(SignalLevel.ORANGE, "Réseau local · ${f.guestsViaQr} invité${if (f.guestsViaQr > 1) "s" else ""} dans le Wi-Fi de la TV", WIFI_ACTION)
        f.disconnectedPlayer?.let { c += Cause(SignalLevel.ORANGE, "Réseau local · $it ne répond plus") }
        if (c.isEmpty()) c += Cause(SignalLevel.GREEN, "Réseau local · rien ne sort de la maison")
        return c
    }

    private fun internet(f: SafetyFacts): List<Cause> {
        // Inactif : Internet n'est pas ouvrable ; le noir passe avant tout (la TV sans Internet n'est jamais rouge).
        if (!f.tvHasInternet) return listOf(Cause(SignalLevel.BLACK, "Internet : la TV n'est pas connectée", "Connexion & réglages"))
        if (f.internetDisabledByOwner) return listOf(Cause(SignalLevel.BLACK, "Internet : désactivé", "Contrôle parental"))
        if (f.kidProfile && !f.internetAllowedByParent) return listOf(Cause(SignalLevel.BLACK, "Internet : réservé aux adultes (code parental)", "Contrôle parental"))
        if (f.clockDoubt) return listOf(Cause(SignalLevel.BLACK, "Internet : vérifiez l'heure de la TV", "Connexion & réglages"))
        val c = ArrayList<Cause>()
        if (f.tls == TlsState.INVALID) c += Cause(SignalLevel.RED, "Internet : impossible · certificat non valide", "Fermer Internet")
        if (!f.ticketValid) c += Cause(SignalLevel.RED, "Internet : impossible · autorisation refusée", "Réessayer")
        if (f.serverLink != Link3.OK) {
            if (f.serverLink == Link3.LOST && f.serverLostSec >= LOST_AFTER_SEC) c += Cause(SignalLevel.RED, "Internet perdu : la partie continue en local", "Réessayer")
            else c += Cause(SignalLevel.ORANGE, "Internet · liaison en reprise (${f.serverLostSec} s)")
        }
        f.resumingPlayer?.let { c += Cause(SignalLevel.ORANGE, "Internet · $it en reprise") }
        if (f.rttMs > SLOW_RTT_MS) c += Cause(SignalLevel.ORANGE, "Internet · réseau lent")
        if (c.isEmpty()) c += Cause(SignalLevel.GREEN, "Internet · partie sûre · chiffrée, pseudonymes seulement")
        return c
    }
}
