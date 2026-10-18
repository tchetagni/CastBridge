package castbridge.core.owner

import castbridge.core.lots.RentalEngine
import castbridge.core.lots.RentalLines
import castbridge.core.lots.RentalUnit
import castbridge.core.lots.RentalState
import castbridge.core.lots.RentalStatus
import castbridge.core.lots.Right
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** What the TV says about the activation key: the edition (essai / production / super illimité) and its properties. [lines] are short French sentences. When it is on screen: [KeyBadgeDisplay]. */
data class Badge(val title: String, val lines: List<String>, val ended: Boolean = false) {
    val text: String get() = (listOf(title) + lines).joinToString("  ·  ")
}

object KeyBadge {
    private val DAY = 24L * 3600 * 1000
    private fun date(ms: Long, zone: ZoneId) = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(zone).format(Instant.ofEpochMilli(ms))

    private fun left(ms: Long): String { val d = ms / DAY; val h = ms / 3_600_000L; return when { d >= 2 -> "$d j"; h >= 1 -> "$h h"; else -> "${maxOf(1L, ms / 60_000L)} min" } }
    private fun minutes(m: Long) = if (m >= 60) "${m / 60} h" + (if (m % 60 != 0L) " ${m % 60} min" else "") else "$m min"

    /** The "Location" lines: hours of use and days are never converted into each other; the contract with the FEWEST hours left (not the earliest date), none in grace. */
    private fun rentalLines(rentals: List<RentalStatus>): List<String> {
        val live = rentals.filter { it.usable && it.contract.productId != RentalLines.TRIAL_PRODUCT && it.remainingMs != null }
        if (live.none { it.perUnit }) return live.minByOrNull { it.remainingMs!! }?.let { listOf("Location : ${left(it.remainingMs!!)} restant(s)") }.orEmpty()
        val out = ArrayList<String>()
        live.filter { it.state == RentalState.ACTIVE && it.contract.unit == RentalUnit.HOURS }.minByOrNull { it.remainingUsageMinutes ?: Long.MAX_VALUE }
            ?.let { out += "Location : ${RentalEngine.hoursLeft(it.remainingUsageMinutes ?: 0)} d'utilisation restante(s)" }
        live.filter { it.state == RentalState.ACTIVE && it.contract.unit == RentalUnit.DAYS }.minByOrNull { it.remainingMs!! }
            ?.let { out += "Location : ${maxOf(1L, (it.remainingMs!! + DAY - 1) / DAY)} jour(s) restant(s)" }
        live.filter { it.state == RentalState.GRACE }.minByOrNull { it.remainingMs!! }?.let { out += "Location : ${left(it.remainingMs!!)} de tolérance" }
        return out
    }

    /** The activations whose usage right has not ended yet (shared with the status badge: one calculation). */
    fun counting(activations: List<Activation>, nowMs: Long): List<Activation> =
        activations.filter { a -> a.rights.filterIsInstance<Right.Usage>().none { nowMs >= it.endsAt } && TvGate.implicitUsageEnd(a).let { it == null || nowMs < it } }

    /** End of each activation's usage right (null = no ceiling). */
    fun ends(list: List<Activation>): List<Long?> = list.map { a -> a.rights.filterIsInstance<Right.Usage>().maxOfOrNull { it.endsAt } ?: TvGate.implicitUsageEnd(a) }

    /** [activations]: every verified activation installed; [rentals]: their rental statuses (the trial window is one of them). */
    fun of(activations: List<Activation>, nowMs: Long, rentals: List<RentalStatus> = emptyList(), zone: ZoneId = ZoneId.systemDefault()): Badge {
        if (activations.isEmpty()) return Badge("SANS CLÉ", listOf("Entrez un code d'activation"), ended = true)
        val counting = counting(activations, nowMs)
        if (counting.isEmpty()) return Badge("ACTIVATION TERMINÉE", listOf("Entrez un nouveau code valide"), ended = true)
        val rights = counting.flatMap { it.rights }
        val production = counting.filter { it.kind == ActivationKind.PRODUCTION }
        val lines = ArrayList<String>()
        // the key's duration: the latest end among the counting activations, or unlimited when one has no ceiling
        val ends = ends(production.ifEmpty { counting })
        lines += if (ends.any { it == null }) "Clé illimitée" else ends.filterNotNull().maxOrNull()!!.let { "Clé valable jusqu'au ${date(it, zone)} (${left(it - nowMs)})" }
        return when {
            rights.any { it is Right.Super } -> Badge("SUPER ILLIMITÉ", listOf("Tous les droits", "Clé permanente"))
            production.isNotEmpty() -> {
                val bundles = rights.filterIsInstance<Right.Purchase>().flatMap { it.bundleIds }.distinct()
                if (bundles.isNotEmpty()) lines += "${bundles.size} bouquet(s) acheté(s)"
                rights.filterIsInstance<Right.Subscription>().maxOfOrNull { it.endsAt }?.let { lines += "Abonnement jusqu'au ${date(it, zone)}" }
                rights.filterIsInstance<Right.OpenAll>().maxOfOrNull { it.endsAt }?.takeIf { it > nowMs }?.let { lines += "Tout ouvert jusqu'au ${date(it, zone)}" }
                lines += rentalLines(rentals)
                Badge("PRODUCTION", lines)
            }
            else -> {
                val w = rentals.firstOrNull { it.contract.productId == RentalLines.TRIAL_PRODUCT }
                lines += when {
                    w == null -> "Lots locatifs : 12 h d'essai, une seule fois"
                    w.state == RentalState.EXPIRED -> "Lots locatifs : fenêtre d'essai terminée"
                    else -> "Lots locatifs : ${minutes(w.remainingUsageMinutes ?: RentalLines.TRIAL_USAGE_MINUTES.toLong())} d'essai restantes"
                }
                lines += "Streaming, Sudoku et lots d'essai seulement · « Passer en production » pour tout débloquer"
                Badge("ESSAI", lines)
            }
        }
    }
}

/**
 * When the licence label is on screen (decision of the owner, 2026-10-18: « la bande verte de production ne doit pas être permanente et apparaître seulement lorsqu'on revient sur l'accueil pour 15 secondes »).
 * The green (production, super illimité) and orange (essai) label is NOT permanent: it shows for [HOME_SHOW_MS] each time the home of CastBridge-TV is (re)shown (launch, back from a video, MENU or library
 * closed, back from Quiz / Apprendre / Jeux…), then goes away; it is never shown on a video, in the MENU, in the library or in another activity.
 * The red label ([Badge.ended]: no key, key over) stays on every screen, always (assumption of 2026-10-18, the owner may reverse it: only [visible] changes).
 * Pure rule; the clock and the screens belong to `KeyBadgeOverlay` (receiver).
 */
object KeyBadgeDisplay {
    const val HOME_SHOW_MS = 15_000L

    /**
     * [ended]: [Badge.ended]; [onHome]: the label's screen is the home of CastBridge-TV (not a video, the MENU, the library or another activity);
     * [sinceHomeShownMs]: time since the home was last (re)shown (null = never, or the window is over).
     */
    fun visible(ended: Boolean, onHome: Boolean, sinceHomeShownMs: Long?): Boolean =
        ended || (onHome && sinceHomeShownMs != null && sinceHomeShownMs >= 0 && sinceHomeShownMs < HOME_SHOW_MS)
}
