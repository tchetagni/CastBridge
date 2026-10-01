package castbridge.core.content

import castbridge.core.telemetry.Telemetry
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** What is known about one item: how often it was shown, answered right, how long it took, how often it was reported. */
data class ItemStat(val shown: Int = 0, val correct: Int = 0, val totalMs: Long = 0, val reports: Int = 0) {
    val successRate: Double? get() = if (shown == 0) null else correct.toDouble() / shown
    val averageMs: Long? get() = if (shown == 0) null else totalMs / shown
    operator fun plus(o: ItemStat) = ItemStat(shown + o.shown, correct + o.correct, totalMs + o.totalMs, reports + o.reports)
}

/**
 * Device-side aggregation (docs/TELEMETRY.md « content_stat »): the app records each answer here, and [flush] turns the totals into
 * one `content_stat` event per item — ids and numbers only, never a text — and only when the user accepted the usage statistics
 * (Telemetry drops the event otherwise; [flush] then keeps nothing either, so no stat piles up without consent).
 */
class ItemStatsCollector(private val maxItems: Int = 1000) {
    private val stats = LinkedHashMap<Pair<ContentKind, String>, ItemStat>()

    /** One display of an item; [correct] null when it is not answered (a lesson read). */
    @Synchronized
    fun record(kind: ContentKind, id: String, correct: Boolean?, ms: Long) {
        if (!ValidationRecord.ID.matches(id)) return
        val key = kind to id
        if (key !in stats && stats.size >= maxItems) return
        stats[key] = (stats[key] ?: ItemStat()) + ItemStat(1, if (correct == true) 1 else 0, ms.coerceIn(0, MAX_MS))
    }

    @Synchronized
    fun recordReport(kind: ContentKind, id: String) {
        if (!ValidationRecord.ID.matches(id)) return
        val key = kind to id
        if (key !in stats && stats.size >= maxItems) return
        stats[key] = (stats[key] ?: ItemStat()) + ItemStat(reports = 1)
    }

    @Synchronized fun size() = stats.size

    /** Sends and forgets what was collected; returns the number of events queued (0 without the consent). */
    @Synchronized
    fun flush(telemetry: Telemetry): Int {
        var sent = 0
        for ((k, s) in stats) {
            if (telemetry.track("content_stat", mapOf("kind" to k.first.key, "item" to k.second, "shown" to s.shown, "correct" to s.correct,
                    "ms" to s.totalMs, "reports" to s.reports))) sent++
        }
        stats.clear()
        return sent
    }

    companion object { const val MAX_MS = 3_600_000L }
}

/** The suspicion of an item: a score in 0..1 and why. */
data class Suspicion(val score: Double, val reasons: List<String>) { val flagged: Boolean get() = score >= QualitySignals.FLAG_AT }

/**
 * « Suspicious item » detection, run after the beta period on the aggregated numbers (docs/CONTENT-VALIDATION.md § 5; the server
 * has the same function, tested with the same vectors).
 *
 * - expected success per difficulty 1..5 (4 choices): 90 / 78 / 65 / 50 / 35 %;
 * - the success rate counts only from [MIN_SHOWN] displays, and only when the expected rate lies OUTSIDE the 95 % Wilson interval of
 *   the observed one (so a small sample never flags); too low is twice as suspicious as too high (wrong key, ambiguity);
 * - below chance (25 %) with enough displays is a strong sign of a wrong answer key;
 * - every report counts, [REPORTS_FOR_MAX] reports saturate the report part; [REPORTS_FLAG] reports flag the item whatever the rate.
 */
object QualitySignals {
    const val MIN_SHOWN = 30
    const val FLAG_AT = 0.5
    const val REPORTS_FOR_MAX = 5
    const val REPORTS_FLAG = 3
    private val EXPECTED = doubleArrayOf(0.90, 0.78, 0.65, 0.50, 0.35)

    fun expected(difficulty: Int): Double = EXPECTED[difficulty.coerceIn(1, 5) - 1]

    /** 95 % Wilson interval of [correct] / [shown]. */
    fun wilson(correct: Int, shown: Int): Pair<Double, Double> {
        if (shown == 0) return 0.0 to 1.0
        val z = 1.96; val n = shown.toDouble(); val p = correct / n
        val d = 1 + z * z / n
        val centre = (p + z * z / (2 * n)) / d
        val half = z * sqrt(p * (1 - p) / n + z * z / (4 * n * n)) / d
        return max(0.0, centre - half) to min(1.0, centre + half)
    }

    fun suspicion(difficulty: Int, s: ItemStat): Suspicion {
        val reasons = ArrayList<String>()
        var rate = 0.0
        if (s.shown >= MIN_SHOWN) {
            val exp = expected(difficulty)
            val (lo, hi) = wilson(s.correct, s.shown)
            when {
                hi < exp -> { rate = min(1.0, (exp - hi) / 0.30); reasons += "réussite trop basse (${pct(s.correct, s.shown)} % pour ${pct(exp)} % attendus)" }
                lo > exp -> { rate = min(1.0, (lo - exp) / 0.30) / 2; reasons += "réussite trop haute (${pct(s.correct, s.shown)} % pour ${pct(exp)} % attendus)" }
            }
            if (hi < 0.25) { rate = 1.0; reasons += "réussite sous le hasard : clé de réponse probablement fausse" }
        }
        val rep = min(1.0, s.reports.toDouble() / REPORTS_FOR_MAX)
        if (s.reports > 0) reasons += "${s.reports} signalement(s)"
        var score = min(1.0, 0.6 * rate + 0.4 * rep)
        if (s.reports >= REPORTS_FLAG) score = max(score, FLAG_AT)
        return Suspicion(score, reasons)
    }

    private fun pct(c: Int, n: Int) = Math.round(100.0 * c / n).toInt()
    private fun pct(p: Double) = Math.round(100 * p).toInt()
}
