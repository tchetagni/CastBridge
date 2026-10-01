package castbridge.core.parental.tab

enum class Dir(val label: String, val arrow: String) { UP("en hausse", "▲"), DOWN("en baisse", "▼"), FLAT("stable", "►"), UNKNOWN("comparaison impossible", "–") }

/** Change between two periods, compared per COVERED day (a period with missing days must not look like a drop). */
data class Delta(val dir: Dir, val curAvgMin: Long?, val prevAvgMin: Long?, val deltaMin: Long?, val pct: Int?, val note: String) {
    fun text(): String = when (dir) {
        Dir.UNKNOWN -> note
        else -> "${dir.arrow} ${if ((deltaMin ?: 0) >= 0) "+" else "−"}${Fmt.min(kotlin.math.abs(deltaMin ?: 0))}/jour${pct?.let { " (${if (it >= 0) "+" else "−"}${kotlin.math.abs(it)} %)" } ?: ""} par rapport à la période précédente"
    }
}

object Trends {
    /** Under this change (percent) the trend is « stable ». */
    const val FLAT_PCT = 5

    fun measuredDelta(cur: PeriodSummary, prev: PeriodSummary): Delta = compare(cur.measured.value, cur.coveredDays, prev.measured.value, prev.coveredDays)

    fun compare(cur: Long?, curDays: Int, prev: Long?, prevDays: Int): Delta {
        if (cur == null || prev == null || curDays == 0 || prevDays == 0)
            return Delta(Dir.UNKNOWN, null, null, null, null, if (prevDays == 0) "Pas de données sur la période précédente : comparaison impossible." else "Pas de données sur cette période : comparaison impossible.")
        val a = cur / curDays; val b = prev / prevDays
        val d = a - b
        val pct = if (b > 0) Math.round(d * 100.0 / b).toInt() else null
        val dir = when { kotlin.math.abs(d) < 3 || (pct != null && kotlin.math.abs(pct) < FLAT_PCT) -> Dir.FLAT; d > 0 -> Dir.UP; else -> Dir.DOWN }
        val note = if (curDays != prevDays) "Comparé par jour reçu ($curDays j contre $prevDays j)." else ""
        return Delta(dir, a, b, d, pct, note)
    }
}

enum class GoalStatus(val label: String) { NO_LIMIT("Pas de limite"), OK("Dans la limite"), NEAR("Proche de la limite"), OVER("Limite dépassée"), UNKNOWN("Pas de donnée") }

/** [partial]: other apps were not measured, so [usedMin] is a minimum (« au moins »). */
data class GoalDay(val day: java.time.LocalDate, val limitMin: Int, val usedMin: Long?, val partial: Boolean, val status: GoalStatus)
data class GoalSummary(val days: List<GoalDay>, val over: Int, val near: Int, val ok: Int, val unknown: Int)

/** Limits versus what was used, day by day, for a single profile summary. */
object Goals {
    fun check(s: PeriodSummary): GoalSummary {
        val days = s.days.map { r ->
            val lim = r.limitMin ?: 0
            val used = r.knownMin
            val partial = r.covered && r.appsMin == null
            val st = when {
                !r.covered -> GoalStatus.UNKNOWN
                lim <= 0 -> GoalStatus.NO_LIMIT
                used!! > lim -> GoalStatus.OVER
                used * 100 >= lim * 80L -> GoalStatus.NEAR
                else -> GoalStatus.OK
            }
            GoalDay(r.day, lim, used, partial, st)
        }
        return GoalSummary(days, days.count { it.status == GoalStatus.OVER }, days.count { it.status == GoalStatus.NEAR }, days.count { it.status == GoalStatus.OK }, days.count { it.status == GoalStatus.UNKNOWN })
    }
}
