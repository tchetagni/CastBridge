package castbridge.core.wallet.millions

import castbridge.core.wallet.millions.MillionsWinCaps.CapStatus

/**
 * Ce que la TV peut miser HORS LIGNE (conception W22 § 13.4). [confirmedNdem] = NDEM confirmés du dernier instantané `cbw1` (Snapshot.n) ; [unconfirmed] = les parties dont le serveur n'a pas encore
 * confirmé le journal (en cours ou finies).
 *
 *  - [available] = confirmés − mises des parties non confirmées (jamais négatif) : seul ce nombre finance une mise.
 *  - [pendingGains] = gains AFFICHÉS « en attente » des parties finies non confirmées : exposés SÉPARÉMENT, jamais ajoutés au disponible (seul le serveur crédite).
 *
 * Aucune méthode ne crédite : le seul moyen d'AUGMENTER [available] est de construire un nouveau budget à partir d'un nouvel instantané signé (le serveur a confirmé), ce qui est l'affaire de l'appelant.
 */
class MillionsOfflineBudget(val confirmedNdem: Long, val unconfirmed: List<Unconfirmed>) {
    /** Une partie non confirmée : [stake] misée, [gain] affiché (0 tant qu'elle est en cours, ou perdue), [finished]. */
    data class Unconfirmed(val gameId: String, val stake: Long, val gain: Long, val finished: Boolean)

    enum class Reason { WIN_CAP, DAILY_PLAYS, NOT_ENOUGH }

    sealed class Decision {
        object Allowed : Decision()
        data class Refused(val reason: Reason, val text: String) : Decision()
    }

    fun available(): Long = maxOf(0L, confirmedNdem - unconfirmed.sumOf { it.stake })

    fun pendingGains(): Long = unconfirmed.filter { it.finished }.sumOf { it.gain }

    /**
     * Peut-on miser [stake] maintenant ? Refus, dans l'ordre : un plafond de parties gagnées ferme le mode ([caps], phrase exacte de [MillionsWinCaps]) ; [maxPlaysPerDay] parties jouées aujourd'hui
     * ([playsToday]) ; disponible < mise.
     */
    fun decide(stake: Long, playsToday: Int, maxPlaysPerDay: Int, caps: CapStatus): Decision {
        if (caps is CapStatus.Closed) return Decision.Refused(Reason.WIN_CAP, caps.text)
        if (playsToday >= maxPlaysPerDay) return Decision.Refused(Reason.DAILY_PLAYS, "Trop de parties aujourd'hui : $maxPlaysPerDay au maximum. Réessayez demain.")
        if (stake > available()) return Decision.Refused(Reason.NOT_ENOUGH, "Solde insuffisant : il faut $stake NDEM disponibles pour miser.")
        return Decision.Allowed
    }

    /** Le même budget avec une partie de plus à confirmer (la mise est retenue immédiatement). */
    fun withGame(game: Unconfirmed) = MillionsOfflineBudget(confirmedNdem, unconfirmed + game)

    /** Le même budget sans la partie [gameId] (le serveur l'a confirmée ; le nouvel instantané apporte le solde à jour). */
    fun withoutGame(gameId: String) = MillionsOfflineBudget(confirmedNdem, unconfirmed.filterNot { it.gameId == gameId })
}
