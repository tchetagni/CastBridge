package castbridge.core.lots

import castbridge.core.owner.Activation
import castbridge.core.owner.TvClock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
    /** A contract with a usage budget never exceeds this many minutes of use, however many renewal lines add up (96 h; 0 = no clamp). Contracts without a budget are untouched. */
    val maxUseMinutesPerContract: Long = 5760,
    /**
     * Per-unit alerts and sentences (hours of use / days, docs W16 § 1.3). On by default (the real path, RentalHub, uses the defaults). The vectors v1/v2 and the legacy tests pass `false`
     * EXPLICITLY: a usage-capped contract issued before W16 is indistinguishable from an hourly one (same signed line) and keeps its old sentences and thresholds there.
     */
    val perUnitMessages: Boolean = true,
)

/** What a contract measures: hours of use (usage budget), days (validity only) or the one-time trial window. Deduced from the signed line, no field of its own. */
enum class RentalUnit { HOURS, DAYS, TRIAL }

/** One rental contract as the TV sees it: every line of one product and period merged (renewals extend, identical lines count once). */
data class RentalContract(
    val key: String, val productId: String, val bundleIds: List<String>, val license: String, val period: Long,
    val startsAt: Long, val endsAt: Long, val graceMs: Long, /** 0 = no usage ceiling. */ val maxUsageMinutes: Long, val maxConcurrent: Int, val box: String,
    /** Why part of the signed lines does not apply (ceiling of 96 h, line of another unit ignored), in French, shown with the status. */
    val notes: List<String> = emptyList(),
) {
    val graceEndsAt get() = endsAt + graceMs
    val unit: RentalUnit get() = when {
        productId == RentalLines.TRIAL_PRODUCT -> RentalUnit.TRIAL
        maxUsageMinutes > 0 -> RentalUnit.HOURS
        else -> RentalUnit.DAYS
    }
}

/** What a screen needs about one rental. */
data class RentalStatus(
    val contract: RentalContract, val state: RentalState, val reason: ExpiryReason?, val doubt: ClockDoubt?,
    /** Time left before the END date (ACTIVE), the end of the grace (GRACE), or null. */
    val remainingMs: Long?, val remainingUsageMinutes: Long?, val warning: RentalWarning, val message: String,
    /** Minutes of use already counted and the (clamped) budget, 0 when the contract has none. */
    val usedMinutes: Long = 0, val maxUsageMinutes: Long = 0,
    /** True when [message] and [warning] follow the per-unit rules ([RentalConfig.perUnitMessages]). */
    val perUnit: Boolean = false,
) {
    val key get() = contract.key
    val notes get() = contract.notes
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
     * A line whose unit (hours of use / days) differs from the first line of the contract is IGNORED (noted in [RentalContract.notes]): units are never mixed, so a renewal without budget cannot
     * unlock an hourly rental. The usage ceiling adds up over the lines and is clamped at [RentalConfig.maxUseMinutesPerContract] (96 h, an engine rule for every hourly rental, pinned by a test;
     * the excess is noted); the grace is the largest; the concurrency limit the smallest positive.
     */
    fun contracts(activations: List<Activation>, cfg: RentalConfig = RentalConfig()): List<RentalContract> {
        val lines = activations.flatMap { a -> a.rights.filterIsInstance<Right.Rental>().map { a.license to it } }.distinct()
        return lines.groupBy { (_, r) -> r.productId to r.period }.map { (id, group) ->
            val sorted = group.sortedWith(compareBy({ it.second.startsAt }, { RentalLines.line(it.second) }))
            val first = sorted.first().second
            var end = first.endsAt
            val used = arrayListOf(first)
            val notes = arrayListOf<String>()
            for ((_, r) in sorted.drop(1)) {
                val grace = used.maxOf { it.graceMs }
                if ((r.maxUsageMinutes > 0) != (first.maxUsageMinutes > 0)) {      // another unit than the first line: never mixed, the line does not apply
                    "Une ligne de renouvellement en ${if (r.maxUsageMinutes > 0) "heures" else "jours"} a été ignorée : on ne mélange pas les heures et les jours".let { if (it !in notes) notes += it }
                } else if (r.startsAt < end + grace) { end = maxOf(end, r.startsAt) + r.durationDays * RentalLines.DAY_MS; used += r }
            }
            val sum = if (first.maxUsageMinutes > 0) used.sumOf { it.maxUsageMinutes.toLong() } else 0L
            val usage = if (cfg.maxUseMinutesPerContract > 0) minOf(sum, cfg.maxUseMinutesPerContract) else sum
            if (usage < sum) notes += "${hoursLeft(sum - usage)} non applicables : plafond de ${hoursLeft(usage)} par location"
            RentalContract(contractKey(id.first, id.second), id.first, used.flatMap { it.bundleIds }.distinct().sorted(), sorted.first().first, id.second,
                minOf(id.second, first.startsAt), end, used.maxOf { it.graceMs }, usage, used.map { it.maxConcurrent }.filter { it > 0 }.minOrNull() ?: 0,
                used.last().box, notes)
        }.sortedWith(compareBy({ it.period }, { it.productId }))
    }

    /**
     * The state of every contract (pure). Order of the rules: a contract already swept stays EXPIRED; the usage ceiling ends it whatever the clock says (usage does not depend on the wall
     * clock); a clock in doubt SUSPENDS what would otherwise be usable (and ends nothing that the highest time seen does not already prove ended); then the dates; then the limit of
     * simultaneous rentals per licence.
     */
    fun evaluate(contracts: List<RentalContract>, inputs: RentalInputs, cfg: RentalConfig = RentalConfig(), formatDate: (Long) -> String = ::defaultDate): List<RentalStatus> {
        val t = inputs.judged
        val base = contracts.map { c ->
            val used = inputs.usedMinutes[c.key] ?: 0L
            val leftUsage = if (c.maxUsageMinutes > 0) maxOf(0L, c.maxUsageMinutes - used) else null
            val over = inputs.expired[c.key]
            when {
                over != null -> status(c, used, cfg, formatDate, RentalState.EXPIRED, over, t.doubt, null, leftUsage)                 // already swept: the key is gone, nothing can bring it back
                inputs.superUnlimited -> RentalStatus(c, RentalState.ACTIVE, null, null, null, null, RentalWarning.NONE, PERMANENT, used, c.maxUsageMinutes)   // super administrator account: no end, no usage ceiling, whatever the clock says
                leftUsage != null && leftUsage == 0L -> status(c, used, cfg, formatDate, RentalState.EXPIRED, ExpiryReason.USAGE, t.doubt, null, 0L)
                t.now >= c.graceEndsAt -> status(c, used, cfg, formatDate, RentalState.EXPIRED, ExpiryReason.DATE, t.doubt, null, leftUsage)
                t.doubt != null -> status(c, used, cfg, formatDate, RentalState.SUSPENDED, null, t.doubt, null, leftUsage)
                t.now + cfg.startSkewMs < c.startsAt -> status(c, used, cfg, formatDate, RentalState.NOT_STARTED, null, null, null, leftUsage)
                t.now >= c.endsAt -> status(c, used, cfg, formatDate, RentalState.GRACE, null, null, c.graceEndsAt - t.now, leftUsage)
                else -> status(c, used, cfg, formatDate, RentalState.ACTIVE, null, null, c.endsAt - t.now, leftUsage)
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

    private fun status(c: RentalContract, used: Long, cfg: RentalConfig, fmt: (Long) -> String, s: RentalState, reason: ExpiryReason?, doubt: ClockDoubt?, remaining: Long?, leftUsage: Long?): RentalStatus {
        val perUnit = cfg.perUnitMessages
        val warning = when (s) {
            RentalState.ACTIVE, RentalState.GRACE -> when {
                perUnit && c.unit == RentalUnit.HOURS -> usageWarningFor(leftUsage, c.maxUsageMinutes)
                perUnit && c.unit == RentalUnit.DAYS -> daysWarning(remaining ?: Long.MAX_VALUE, (c.endsAt - c.startsAt) / RentalLines.DAY_MS)
                else -> listOf(dateWarning(remaining ?: Long.MAX_VALUE), usageWarning(leftUsage)).maxByOrNull { it.ordinal }!!
            }
            else -> RentalWarning.NONE
        }
        val text = if (perUnit && c.unit != RentalUnit.TRIAL) unitMessage(c, s, reason, remaining, leftUsage, fmt) else null
        return RentalStatus(c, s, reason, doubt, remaining, leftUsage, warning, text ?: message(c, s, reason, doubt, remaining, leftUsage), used, c.maxUsageMinutes, perUnit)
    }

    /** The per-unit sentence of a state, or null to keep the generic one (grace, clock doubt, not started, over limit). */
    private fun unitMessage(c: RentalContract, s: RentalState, reason: ExpiryReason?, remaining: Long?, leftUsage: Long?, fmt: (Long) -> String): String? = when {
        s == RentalState.ACTIVE && c.unit == RentalUnit.HOURS -> hoursActive(hoursLeft(leftUsage ?: 0), fmt(c.endsAt))
        s == RentalState.EXPIRED && c.unit == RentalUnit.HOURS -> if (reason == ExpiryReason.USAGE) hoursSpent(c.maxUsageMinutes) else hoursExpired(fmt(c.endsAt))
        s == RentalState.EXPIRED && c.unit == RentalUnit.DAYS -> daysEnded(daysOf(c), fmt(c.endsAt))
        else -> null
    }

    private fun daysOf(c: RentalContract) = maxOf(1L, (c.endsAt - c.startsAt + RentalLines.DAY_MS / 2) / RentalLines.DAY_MS)

    /** Hourly sentences (public: reused by the delivery and the screens). */
    fun hoursActive(left: String, before: String) = "Il vous reste $left d'utilisation · à utiliser avant le $before"
    fun hoursSpent(maxMinutes: Long) = (if (maxMinutes % 60 == 0L) (maxMinutes / 60).let { if (it == 1L) "Votre 1 heure d'utilisation est épuisée" else "Vos $it heures d'utilisation sont épuisées" }
        else "Vos $maxMinutes minutes d'utilisation sont épuisées") + " : ce contenu n'est plus disponible. Relouer ?"
    fun hoursExpired(date: String) = "Vos heures non utilisées ont expiré le $date. Relouer ?"
    fun daysEnded(days: Long, until: String) = "Location terminée (${if (days == 1L) "1 jour" else "$days jours"}, jusqu'au $until) : ce contenu n'est plus disponible. Relouer ?"

    /** "5 h 20", "5 h", "45 min": minutes of use left, never converted to days. */
    fun hoursLeft(minutes: Long): String = when {
        minutes >= 60 -> "${minutes / 60} h" + (if (minutes % 60 != 0L) " " + (minutes % 60).toString().padStart(2, '0') else "")
        else -> "${maxOf(0L, minutes)} min"
    }

    /** Douala (UTC+1, no daylight saving): the zone of the pilot's dates. */
    val DOUALA: ZoneId = ZoneId.of("Africa/Douala")

    /** dd/MM in Douala: the default date format of the sentences (injectable to stay pure in tests). */
    fun defaultDate(ms: Long): String = DateTimeFormatter.ofPattern("dd/MM").withZone(DOUALA).format(Instant.ofEpochMilli(ms))

    /**
     * Warning of an hourly contract, relative to its budget [max] (minutes): HOUR_1 at 10 min left, HOURS_24 at 60 min (or a quarter of the budget when smaller, so a 1 h rental is not
     * warned when opened), DAYS_7 at a quarter of the budget.
     */
    fun usageWarningFor(left: Long?, max: Long) = when {
        left == null || max <= 0 -> RentalWarning.NONE
        left <= 10 -> RentalWarning.HOUR_1
        left <= minOf(60L, max / 4) -> RentalWarning.HOURS_24
        left * 4 <= max -> RentalWarning.DAYS_7
        else -> RentalWarning.NONE
    }

    /** Date alert of a rental in days, relative to its length: "7 days" only for 14 days or more, "last 24 h" only above 2 days, "last hour" always. HOURS contracts get no date alert (W16-10's banner covers the safety date). */
    fun daysWarning(remainingMs: Long, lengthDays: Long) = when (val w = dateWarning(remainingMs)) {
        RentalWarning.DAYS_7 -> if (lengthDays >= 14) w else RentalWarning.NONE
        RentalWarning.HOURS_24 -> if (lengthDays > 2) w else RentalWarning.NONE
        else -> w
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
