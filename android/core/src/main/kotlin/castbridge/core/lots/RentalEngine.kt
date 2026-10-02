package castbridge.core.lots

import castbridge.core.owner.Activation
import castbridge.core.owner.TvClock

enum class RentalState {
    /** Acquired, its start date is still ahead (a post-dated rental): not usable yet. */
    NOT_STARTED,
    ACTIVE,
    /** Past the end date but inside the offline grace period: usable, the screen asks to reconnect. */
    GRACE,
    /** The TV's clock is in doubt (behind what it already saw, or far ahead): shown, NOT deleted, not usable until the time is right. */
    SUSPENDED,
    /** Ended (date or usage ceiling): content unusable, key destroyed then files removed by the sweep. */
    EXPIRED,
    /** More simultaneous rentals of this licence than the owner allowed: the oldest ones keep working. */
    OVER_LIMIT,
}

enum class ExpiryReason { DATE, USAGE }
enum class ClockDoubt { BEHIND, AHEAD }
enum class RentalWarning { NONE, DAYS_7, HOURS_24, HOUR_1 }

/** Tunable rules (docs/RENTAL-LOTS.md § 3). */
data class RentalConfig(
    /** A clock further than this behind what the TV already saw is a rollback. */
    val behindMarginMs: Long = 24L * 3600 * 1000,
    /** A single observation further than this ahead of what the TV saw is doubted (suspended) until the user confirms or a signed message proves it. */
    val aheadDoubtMs: Long = 45L * RentalLines.DAY_MS,
    /** A lesson in progress delays the deletion by at most this long after the end. */
    val lessonDeferralMs: Long = 15L * 60 * 1000,
    /** A rental starting up to this far in the future already counts as started (activation skew, same as activations). */
    val startSkewMs: Long = 24L * 3600 * 1000,
)

/** One rental contract as the TV sees it: every line of one product and period merged (renewals extend, identical lines count once). */
data class RentalContract(
    val key: String, val productId: String, val bundleIds: List<String>, val license: String, val period: Long,
    val startsAt: Long, val endsAt: Long, val graceMs: Long, /** 0 = no usage ceiling. */ val maxUsageMinutes: Long, val maxConcurrent: Int, val box: String,
) {
    val graceEndsAt get() = endsAt + graceMs
}

/** What a screen needs about one rental. */
data class RentalStatus(
    val contract: RentalContract, val state: RentalState, val reason: ExpiryReason?, val doubt: ClockDoubt?,
    /** Time left before the END date (ACTIVE), the end of the grace (GRACE), or null. */
    val remainingMs: Long?, val remainingUsageMinutes: Long?, val warning: RentalWarning, val message: String,
) {
    val key get() = contract.key
    val usable get() = state == RentalState.ACTIVE || state == RentalState.GRACE
}

/** What the TV knows for the evaluation: its clock, the minutes of use already counted, the contracts already ended and swept. */
class RentalInputs(val judged: JudgedTime, val usedMinutes: Map<String, Long> = emptyMap(), val expired: Map<String, ExpiryReason> = emptyMap(), /** The account was activated with the super administrator code (SUPER_UNLIMITED): its rentals never end and are never deleted. */ val superUnlimited: Boolean = false)

/** The time to trust and why (docs/RENTAL-LOTS.md § 3). [now] is NEVER earlier than the highest time ever seen: a rollback can only freeze, never extend. */
data class JudgedTime(val now: Long, val doubt: ClockDoubt?)

object RentalEngine {
    const val PERMANENT = "Location permanente"

    fun contractKey(productId: String, period: Long) = "$productId@$period"

    /** Is the `super` right (SUPER_UNLIMITED, the super administrator's code) among the installed activations? Then every rental of the account is permanent. */
    fun superUnlimited(activations: List<Activation>) = activations.any { a -> a.rights.any { it is Right.Super } }

    /** The doubt rules: the TV time is `max(wall, lastSeen, floor)`; behind that by more than a day = BEHIND; far ahead (or more than [TvClock.MAX_JUMP_MS], never believed) = AHEAD. */
    fun judge(tv: TvClock, wall: Long, cfg: RentalConfig = RentalConfig()): JudgedTime {
        val base = maxOf(tv.lastSeen, tv.floor)
        if (base == 0L) return JudgedTime(wall, null)
        return when {
            wall + cfg.behindMarginMs < base -> JudgedTime(maxOf(base, tv.monotonicNow()), ClockDoubt.BEHIND)      // a rolled-back wall clock never freezes the rental time: the TV time keeps advancing with the monotonic clock
            wall > base + minOf(cfg.aheadDoubtMs, TvClock.MAX_JUMP_MS) -> JudgedTime(base, ClockDoubt.AHEAD)
            else -> JudgedTime(maxOf(wall, base), null)
        }
    }

    /**
     * Merges every rental line of [activations] into contracts. Same (product, period) = one contract: identical lines count once, a line starting before the previous end
     * (plus grace) EXTENDS it (end = max(previous end, line start) + days), a line starting after is a LATE renewal and is ignored (the old rental is over: a new rental has a new period).
     * The usage ceiling adds up when every line has one, else there is none; the grace is the largest; the concurrency limit the smallest positive.
     */
    fun contracts(activations: List<Activation>): List<RentalContract> {
        val lines = activations.flatMap { a -> a.rights.filterIsInstance<Right.Rental>().map { a.license to it } }.distinct()
        return lines.groupBy { (_, r) -> r.productId to r.period }.map { (id, group) ->
            val sorted = group.sortedWith(compareBy({ it.second.startsAt }, { RentalLines.line(it.second) }))
            val first = sorted.first().second
            var end = first.endsAt
            val used = arrayListOf(first)
            for ((_, r) in sorted.drop(1)) {
                val grace = used.maxOf { it.graceMs }
                if (r.startsAt < end + grace) { end = maxOf(end, r.startsAt) + r.durationDays * RentalLines.DAY_MS; used += r }
            }
            val usage = if (used.all { it.maxUsageMinutes > 0 }) used.sumOf { it.maxUsageMinutes.toLong() } else 0L
            RentalContract(contractKey(id.first, id.second), id.first, used.flatMap { it.bundleIds }.distinct().sorted(), sorted.first().first, id.second,
                minOf(id.second, first.startsAt), end, used.maxOf { it.graceMs }, usage, used.map { it.maxConcurrent }.filter { it > 0 }.minOrNull() ?: 0,
                used.last().box)
        }.sortedWith(compareBy({ it.period }, { it.productId }))
    }

    /**
     * The state of every contract (pure). Order of the rules: a contract already swept stays EXPIRED; the usage ceiling ends it whatever the clock says (usage does not depend on the wall
     * clock); a clock in doubt SUSPENDS what would otherwise be usable (and ends nothing that the highest time seen does not already prove ended); then the dates; then the limit of
     * simultaneous rentals per licence.
     */
    fun evaluate(contracts: List<RentalContract>, inputs: RentalInputs, cfg: RentalConfig = RentalConfig()): List<RentalStatus> {
        val t = inputs.judged
        val base = contracts.map { c ->
            val used = inputs.usedMinutes[c.key] ?: 0L
            val leftUsage = if (c.maxUsageMinutes > 0) maxOf(0L, c.maxUsageMinutes - used) else null
            val over = inputs.expired[c.key]
            when {
                over != null -> status(c, RentalState.EXPIRED, over, t.doubt, null, leftUsage)                 // already swept: the key is gone, nothing can bring it back
                inputs.superUnlimited -> RentalStatus(c, RentalState.ACTIVE, null, null, null, null, RentalWarning.NONE, PERMANENT)   // super administrator account: no end, no usage ceiling, whatever the clock says
                leftUsage != null && leftUsage == 0L -> status(c, RentalState.EXPIRED, ExpiryReason.USAGE, t.doubt, null, 0L)
                t.now >= c.graceEndsAt -> status(c, RentalState.EXPIRED, ExpiryReason.DATE, t.doubt, null, leftUsage)
                t.doubt != null -> status(c, RentalState.SUSPENDED, null, t.doubt, null, leftUsage)
                t.now + cfg.startSkewMs < c.startsAt -> status(c, RentalState.NOT_STARTED, null, null, null, leftUsage)
                t.now >= c.endsAt -> status(c, RentalState.GRACE, null, null, c.graceEndsAt - t.now, leftUsage)
                else -> status(c, RentalState.ACTIVE, null, null, c.endsAt - t.now, leftUsage)
            }
        }
        // simultaneous limit, per licence: the oldest usable rentals keep their right, the others wait (they are not deleted)
        val demoted = HashSet<String>()
        if (!inputs.superUnlimited) base.filter { it.usable }.groupBy { it.contract.license }.forEach { (_, list) ->
            val limit = list.map { it.contract.maxConcurrent }.filter { it > 0 }.minOrNull() ?: return@forEach
            list.sortedWith(compareBy({ it.contract.period }, { it.contract.productId })).drop(limit).forEach { demoted += it.key }
        }
        return base.map { if (it.key in demoted) it.copy(state = RentalState.OVER_LIMIT, remainingMs = null, warning = RentalWarning.NONE,
            message = "Trop de locations en même temps : « ${it.contract.productId} » reprendra quand une autre sera terminée") else it }
    }

    private fun status(c: RentalContract, s: RentalState, reason: ExpiryReason?, doubt: ClockDoubt?, remaining: Long?, leftUsage: Long?): RentalStatus {
        val warning = when (s) {
            RentalState.ACTIVE, RentalState.GRACE -> listOf(dateWarning(remaining ?: Long.MAX_VALUE), usageWarning(leftUsage)).maxByOrNull { it.ordinal }!!
            else -> RentalWarning.NONE
        }
        return RentalStatus(c, s, reason, doubt, remaining, leftUsage, warning, message(c, s, reason, doubt, remaining, leftUsage))
    }

    fun dateWarning(remainingMs: Long) = when {
        remainingMs <= 3600_000L -> RentalWarning.HOUR_1
        remainingMs <= 24 * 3600_000L -> RentalWarning.HOURS_24
        remainingMs <= 7 * RentalLines.DAY_MS -> RentalWarning.DAYS_7
        else -> RentalWarning.NONE
    }
    /** Usage ceiling: warned at 24 h, 5 h and 1 h of use left (the same three levels, counted in minutes of use). */
    fun usageWarning(left: Long?) = when {
        left == null -> RentalWarning.NONE
        left <= 60 -> RentalWarning.HOUR_1
        left <= 300 -> RentalWarning.HOURS_24
        left <= 1440 -> RentalWarning.DAYS_7
        else -> RentalWarning.NONE
    }

    /** "Il vous reste 12 jours" / "5 h" / "40 min". */
    fun countdown(ms: Long): String {
        val d = ms / RentalLines.DAY_MS; val h = ms / 3600_000L; val m = maxOf(1L, ms / 60_000L)
        return "Il vous reste " + when { d >= 2 -> "$d jours"; d == 1L -> "1 jour"; h >= 1 -> "$h h"; else -> "$m min" }
    }

    const val ENDED = "Location terminée : ce contenu n'est plus disponible. Reprendre la location ?"
    const val CHECK_CLOCK = "Vérifiez l'heure de la TV : la location est suspendue tant que l'heure n'est pas juste (rien n'est supprimé)."

    private fun message(c: RentalContract, s: RentalState, reason: ExpiryReason?, doubt: ClockDoubt?, remaining: Long?, leftUsage: Long?): String = when (s) {
        RentalState.EXPIRED -> ENDED
        RentalState.SUSPENDED -> CHECK_CLOCK + if (doubt == ClockDoubt.AHEAD) " L'heure semble très en avance." else " L'heure semble en retard."
        RentalState.NOT_STARTED -> "Location pas encore commencée"
        RentalState.GRACE -> "Location terminée : reconnectez le téléphone pour la renouveler (${countdown(remaining ?: 0).removePrefix("Il vous reste ")} de tolérance)"
        RentalState.OVER_LIMIT -> ""
        RentalState.ACTIVE -> countdown(remaining ?: 0) + (leftUsage?.let { " (ou ${if (it >= 60) "${it / 60} h" else "$it min"} d'utilisation)" } ?: "")
    }
}
