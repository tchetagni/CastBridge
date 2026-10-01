package castbridge.core.parental.tab

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class PeriodKind(val label: String) { DAY("Jour"), WEEK("Semaine"), MONTH("Mois"), CUSTOM("Période") }

/**
 * A range of calendar days [from]..[to] (both included), in the phone's time zone. Days are calendar days, never "24 h": a day of a daylight-saving
 * change has 23 or 25 hours and [startMs] / [endMs] are computed from the zone, not by adding 86 400 000.
 */
data class Period(val kind: PeriodKind, val from: LocalDate, val to: LocalDate) {
    init { require(!to.isBefore(from)) { "période inversée" } }

    val dayCount: Int get() = (ChronoUnit.DAYS.between(from, to) + 1).toInt()
    fun days(): List<LocalDate> = (0 until dayCount).map { from.plusDays(it.toLong()) }
    operator fun contains(d: LocalDate) = !d.isBefore(from) && !d.isAfter(to)
    fun startMs(z: ZoneId): Long = from.atStartOfDay(z).toInstant().toEpochMilli()
    /** First millisecond AFTER the period. */
    fun endMs(z: ZoneId): Long = to.plusDays(1).atStartOfDay(z).toInstant().toEpochMilli()
    fun containsTs(ts: Long, z: ZoneId) = ts >= startMs(z) && ts < endMs(z)

    /** The period of the same length right before this one (days / custom) or the previous week / month (calendar kinds). */
    fun previous(): Period = when (kind) {
        PeriodKind.MONTH -> { val f = from.minusMonths(1).withDayOfMonth(1); Period(kind, f, f.with(TemporalAdjusters.lastDayOfMonth())) }
        else -> Period(kind, from.minusDays(dayCount.toLong()), from.minusDays(1))
    }

    fun label(): String = when (kind) {
        PeriodKind.DAY -> from.toString()
        else -> "$from → $to"
    }

    companion object {
        fun day(d: LocalDate) = Period(PeriodKind.DAY, d, d)
        /** ISO week (Monday..Sunday) containing [d]. */
        fun week(d: LocalDate) = Period(PeriodKind.WEEK, d.with(DayOfWeek.MONDAY), d.with(DayOfWeek.SUNDAY))
        fun month(d: LocalDate) = Period(PeriodKind.MONTH, d.withDayOfMonth(1), d.with(TemporalAdjusters.lastDayOfMonth()))
        /** Reversed bounds are swapped; a range longer than [MAX_DAYS] is cut (bounded work and memory). */
        fun custom(a: LocalDate, b: LocalDate): Period { val f = minOf(a, b); val t = maxOf(a, b); return Period(PeriodKind.CUSTOM, f, if (ChronoUnit.DAYS.between(f, t) >= MAX_DAYS) f.plusDays(MAX_DAYS - 1L) else t) }
        const val MAX_DAYS = 366

        fun of(kind: PeriodKind, ref: LocalDate): Period = when (kind) { PeriodKind.DAY -> day(ref); PeriodKind.WEEK -> week(ref); PeriodKind.MONTH -> month(ref); PeriodKind.CUSTOM -> custom(ref.minusDays(13), ref) }
    }
}

object Clock {
    fun dayOf(ts: Long, z: ZoneId): LocalDate = Instant.ofEpochMilli(ts).atZone(z).toLocalDate()
    fun parseDay(s: String?): LocalDate? = runCatching { LocalDate.parse(s) }.getOrNull()
}
