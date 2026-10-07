package castbridge.core.wallet.ui

/**
 * Quand synchroniser le portefeuille (cahier w22-07a) : à l'ouverture de l'écran, après chaque opération, et toutes les [periodMs] (15 min) tant que la TV est en ligne. Jamais deux à la
 * fois, jamais hors ligne. Une horloge qui recule ne bloque pas la synchronisation suivante.
 *
 * R-46 (confirmé en production : 280 refus en 2 h sur le serveur, 2 appels par minute avec le réessai BIND_PROOF) : la date gardée était celle du dernier SUCCÈS ; quand le serveur refusait
 * (409 « TV non activée »), rien ne la mettait à jour et le tick de 60 s recommençait chaque minute. Maintenant la règle lit la dernière TENTATIVE, réussie ou non ([Attempt]) :
 *  - le tick respecte [periodMs] depuis la dernière tentative réussie ;
 *  - après un REFUS du serveur (il a répondu non : ce n'est pas le réseau) le tick attend de plus en plus : 1, 2, 4, 8 minutes puis [periodMs] au plus (le refus d'une TV non activée dure
 *    des heures) ;
 *  - après un échec RÉSEAU (injoignable) il n'insiste pas avant [UNREACHABLE_RETRY_MS] (2 minutes, jamais plus que [periodMs]) ;
 *  - l'ouverture de l'écran et la fin d'une opération restent immédiates (l'utilisateur est là : seul un débounce de [OPEN_DEBOUNCE_MS] contre la rafale d'une réouverture).
 */
class WalletSyncSchedule(val periodMs: Long = 15 * 60_000L) {
    enum class Trigger { OPEN, AFTER_OPERATION, TICK }

    /** Comment une tentative s'est terminée : [OK] ; [REFUSED] = le serveur a répondu non (409 non activée, 403, 5xx…) ; [UNREACHABLE] = pas de réponse (réseau, 502-504). */
    enum class Outcome { OK, REFUSED, UNREACHABLE }

    /** La dernière TENTATIVE : quand elle s'est terminée, comment, et combien de refus de suite (0 après autre chose qu'un refus). */
    data class Attempt(val at: Long, val outcome: Outcome, val refusals: Int = if (outcome == Outcome.REFUSED) 1 else 0)

    /** Ce qu'il faut retenir après une tentative ([prev] = la précédente) : un refus de plus de suite, ou la remise à zéro. */
    fun after(prev: Attempt?, nowMs: Long, outcome: Outcome): Attempt =
        Attempt(nowMs, outcome, if (outcome == Outcome.REFUSED) (prev?.takeIf { it.outcome == Outcome.REFUSED }?.refusals ?: 0) + 1 else 0)

    /** Le résultat d'un appel : réussi, injoignable (pas de réponse du serveur) ou refusé (le serveur a répondu). */
    fun outcomeOf(r: WalletResult<*>): Outcome = when {
        r is WalletResult.Ok -> Outcome.OK
        (r as WalletResult.Fail).network -> Outcome.UNREACHABLE
        else -> Outcome.REFUSED
    }

    /** Combien le tick attend après [last] avant de réessayer. */
    fun tickWaitMs(last: Attempt): Long = when (last.outcome) {
        Outcome.OK -> periodMs
        Outcome.UNREACHABLE -> minOf(UNREACHABLE_RETRY_MS, periodMs)
        Outcome.REFUSED -> minOf(periodMs, REFUSED_BASE_MS shl (last.refusals - 1).coerceIn(0, 20))      // 1, 2, 4, 8 minutes puis la période
    }

    /** La règle, d'après la dernière TENTATIVE ([last] : null = aucune encore). */
    fun shouldSyncAfter(trigger: Trigger, online: Boolean, nowMs: Long, last: Attempt?, inFlight: Boolean): Boolean {
        if (!online || inFlight) return false
        val clockWentBack = last != null && nowMs < last.at
        return when (trigger) {
            Trigger.AFTER_OPERATION -> true
            Trigger.OPEN -> last == null || clockWentBack || nowMs - last.at >= OPEN_DEBOUNCE_MS     // pas de rafale si l'écran est rouvert à la seconde
            Trigger.TICK -> last == null || clockWentBack || nowMs - last.at >= tickWaitMs(last)
        }
    }

    /** La forme d'avant : seule la date de la dernière synchronisation RÉUSSIE est connue (une tentative réussie à cette date). */
    fun shouldSync(trigger: Trigger, online: Boolean, nowMs: Long, lastSyncAt: Long?, inFlight: Boolean): Boolean =
        shouldSyncAfter(trigger, online, nowMs, lastSyncAt?.let { Attempt(it, Outcome.OK) }, inFlight)

    companion object {
        const val OPEN_DEBOUNCE_MS = 2_000L
        /** Après un échec réseau le tick n'insiste pas avant 2 minutes. */
        const val UNREACHABLE_RETRY_MS = 2 * 60_000L
        /** Premier palier après un refus du serveur : 1 minute, puis le double à chaque refus de suite. */
        const val REFUSED_BASE_MS = 60_000L
    }
}
