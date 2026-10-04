package castbridge.core.tv.status

import castbridge.core.lots.Right
import castbridge.core.owner.Activation
import castbridge.core.owner.ActivationKind
import castbridge.core.owner.KeyBadge

/** Lit l'état et la durée de la clé de la TV pour la pastille Licence, avec le calcul de [KeyBadge] (aucune seconde règle de fin de clé). Pur : l'heure vient de l'appelant. */
object LicenceReader {
    data class Reading(val state: LicenceState, val timing: LicenceTiming?)

    /** [locked] : la TV exige une activation (build verrouillé) ; [pendingNotification] : activation en attente de notification au serveur. */
    fun read(activations: List<Activation>, nowMs: Long, locked: Boolean, pendingNotification: Boolean): Reading {
        if (!locked) return Reading(LicenceState.NOT_REQUIRED, null)
        if (activations.isEmpty()) return Reading(LicenceState.NO_KEY, null)
        val counting = KeyBadge.counting(activations, nowMs)
        if (counting.isEmpty()) return Reading(LicenceState.EXPIRED, null)
        val production = counting.filter { it.kind == ActivationKind.PRODUCTION }
        val relevant = production.ifEmpty { counting }
        val ends = KeyBadge.ends(relevant)
        val to = if (ends.any { it == null }) null else ends.filterNotNull().max()
        val from = relevant.minOf { a -> a.rights.filterIsInstance<Right.Usage>().minOfOrNull { it.startsAt } ?: (if (a.issuedAt > 0L) a.issuedAt else a.notBefore) }
        val state = when {
            pendingNotification -> LicenceState.PENDING_NOTIFICATION
            production.isEmpty() -> LicenceState.TRIAL
            else -> LicenceState.VALID
        }
        return Reading(state, LicenceTiming(from, to))
    }
}
