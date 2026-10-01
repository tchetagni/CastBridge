package castbridge.core.parental.tab

import java.time.LocalDate
import java.time.ZoneId

/** A number with its quality. [value] is null when the quality is UNAVAILABLE: a screen must show [note] / « indisponible », never 0. */
data class Figure(val value: Long?, val quality: Quality, val note: String? = null) {
    fun text(unit: (Long) -> String = Fmt::min): String = value?.let(unit) ?: "indisponible"
    companion object { val NA = Figure(null, Quality.UNAVAILABLE) }
}

/** One calendar day of a summary. [covered] false = the TV reported nothing for that day (a gap, not a zero). [appsMin] null = other apps not measured. */
data class DayRow(
    val day: LocalDate, val covered: Boolean, val playMin: Long = 0, val gamesMin: Long = 0, val downloadsMin: Long = 0, val appsMin: Long? = null,
    val unsplitMin: Long = 0, val limitMin: Int? = null, val supervision: String? = null,
) {
    val measuredMin: Long get() = playMin + gamesMin + downloadsMin
    /** Everything the reports counted, other apps and unsplit included; a lower bound when [appsMin] is null. null on a gap. */
    val knownMin: Long? get() = if (covered) measuredMin + (appsMin ?: 0) + unsplitMin else null
}

data class AppAgg(val pkg: String, val label: String, val min: Long, val days: Int)
data class TitleAgg(val title: String, val type: EventType, val count: Int, val min: Long)

/** The numbers of one period for one profile (or all, [profileId] null), each with its quality. */
data class PeriodSummary(
    val period: Period, val profileId: String?, val days: List<DayRow>,
    val measured: Figure, val play: Figure, val games: Figure, val downloads: Figure, val otherApps: Figure,
    val blocks: Figure, val unlockAttempts: Figure, val tamper: Figure,
    val apps: List<AppAgg>, val coveredDays: Int, val missingDays: List<LocalDate>, val appsDays: Int,
    val events: List<ActivityEvent>,
) {
    val complete: Boolean get() = missingDays.isEmpty()
    fun coverageText(): String = if (coveredDays == 0) "Aucune donnée reçue sur cette période."
        else if (complete) "Période complète (${period.dayCount} j)."
        else "Période incomplète : $coveredDays j sur ${period.dayCount} reçus (la TV n'était pas joignable ou éteinte les autres jours)."
}

/**
 * Events and day facts -> summaries (day / week / month / custom range, per profile or all). Linear in the number of events (a TV with
 * 100 000 events summarises in well under a second). Pure.
 */
object ReportAggregator {
    fun summarize(facts: List<DayFact>, events: List<ActivityEvent>, period: Period, profileId: String?, zone: ZoneId, tv: String? = null): PeriodSummary {
        val byDay = HashMap<LocalDate, MutableList<DayFact>>()
        for (f in facts) {
            if (profileId != null && f.profileId != profileId) continue
            if (tv != null && f.tv != tv) continue
            val d = Clock.parseDay(f.day) ?: continue
            if (d in period) byDay.getOrPut(d) { ArrayList() } += f
        }
        val rows = period.days().map { d ->
            val l = byDay[d]
            if (l.isNullOrEmpty()) DayRow(d, false)
            else {
                // appsMin: a number only if at least one fact measured it; null otherwise (not zero)
                val am = l.mapNotNull { it.appsMin }
                DayRow(d, true, l.sumOf { it.play }, l.sumOf { it.games }, l.sumOf { it.downloads }, if (am.isEmpty()) null else am.sum(), l.sumOf { it.unsplitMin },
                    if (profileId != null) l.firstOrNull { it.limitMin > 0 }?.limitMin ?: 0 else null, l.firstOrNull { it.supervision != null }?.supervision)
            }
        }
        val covered = rows.filter { it.covered }
        val appRows = covered.filter { it.appsMin != null }
        val ev = events.filter { (profileId == null || it.profileId == profileId || (it.profileId == null && profileId == null)) && (tv == null || it.tv == tv) && period.containsTs(it.ts, zone) }
        val any = covered.isNotEmpty()
        fun meas(v: Long) = if (any) Figure(v, Quality.MEASURED) else Figure.NA
        val appsFigure = when {
            appRows.isEmpty() -> Figure(null, Quality.UNAVAILABLE, "Surveillance de toute la TV inactive ou non autorisée : minutes des autres applications indisponibles.")
            appRows.size < covered.size -> Figure(appRows.sumOf { it.appsMin!! }, Quality.BEST_EFFORT, "Estimation sur ${appRows.size} jour(s) sur ${covered.size} : la surveillance n'était pas active les autres jours.")
            else -> Figure(appRows.sumOf { it.appsMin!! }, Quality.BEST_EFFORT)
        }
        val journal = ev.any { it.id.startsWith("j:") }
        val tamper = ev.count { it.id.startsWith("tmp:") || (it.type == EventType.ALERT && it.severity == Severity.CRITICAL) }
        val appAgg = LinkedHashMap<String, AppAgg>()
        for ((d, l) in byDay) for (f in l) for (a in f.byApp) { val c = appAgg[a.pkg]; appAgg[a.pkg] = AppAgg(a.pkg, a.label.ifBlank { a.pkg }, (c?.min ?: 0) + a.min, (c?.days ?: 0) + 1) }
        return PeriodSummary(
            period, profileId, rows, meas(covered.sumOf { it.measuredMin }), meas(covered.sumOf { it.playMin }), meas(covered.sumOf { it.gamesMin }), meas(covered.sumOf { it.downloadsMin }), appsFigure,
            meas(ev.count { it.type == EventType.BLOCK }.toLong()),
            if (journal) Figure(ev.count { it.type == EventType.UNLOCK }.toLong(), Quality.MEASURED) else Figure(null, Quality.UNAVAILABLE, "Journal détaillé de la TV non reçu."),
            meas(tamper.toLong()),
            appAgg.values.sortedByDescending { it.min }, covered.size, rows.filter { !it.covered }.map { it.day }, appRows.size, ev.sortedBy { it.ts },
        )
    }

    /** One summary per profile id present in the facts of the period. */
    fun perProfile(facts: List<DayFact>, events: List<ActivityEvent>, period: Period, zone: ZoneId, tv: String? = null): Map<String, PeriodSummary> =
        facts.filter { (tv == null || it.tv == tv) && Clock.parseDay(it.day)?.let { d -> d in period } == true }.map { it.profileId }.distinct().sorted()
            .associateWith { summarize(facts, events, period, it, zone, tv) }

    /** Top-N of what was watched / played / learned, by number of occurrences then minutes. */
    fun top(events: List<ActivityEvent>, types: Set<EventType>, n: Int): List<TitleAgg> =
        events.filter { it.type in types }.groupBy { it.type to it.title }
            .map { (k, l) -> TitleAgg(k.second, k.first, l.size, l.sumOf { (it.durMin ?: 0).toLong() }) }
            .sortedWith(compareByDescending<TitleAgg> { it.min }.thenByDescending { it.count }.thenBy { it.title }).take(n)
}
