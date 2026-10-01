package castbridge.core.parental.tab

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Minutes of use by weekday (0 = Monday) and hour (0..23), from timed events only (the TV's own measures). [hasData] false = nothing timed. */
class Heatmap(val ms: Array<LongArray>, val hasData: Boolean) {
    fun min(dow: Int, hour: Int): Long = ms[dow][hour] / 60_000
    fun max(): Long = ms.maxOf { r -> r.max() } / 60_000
    fun hourTotals(): LongArray = LongArray(24) { h -> (0 until 7).sumOf { ms[it][h] } / 60_000 }
    fun totalMin(): Long = ms.sumOf { it.sum() } / 60_000

    /** The [n] busiest hours of the day, as (hour, minutes), busiest first; empty without data. */
    fun peakHours(n: Int = 3): List<Pair<Int, Long>> = if (!hasData) emptyList() else hourTotals().withIndex().filter { it.value > 0 }.sortedByDescending { it.value }.take(n).map { it.index to it.value }

    /** Minutes in the night band [fromHour] (included) .. [toHour] (excluded), wrapping midnight. Null without data (not 0). */
    fun lateNightMin(fromHour: Int = LATE_FROM, toHour: Int = LATE_TO): Long? =
        if (!hasData) null else hourTotals().withIndex().filter { (h, _) -> if (fromHour > toHour) h >= fromHour || h < toHour else h in fromHour until toHour }.sumOf { it.value }

    companion object {
        const val LATE_FROM = 22
        const val LATE_TO = 6
        /** A timed event longer than this is cut (a TV left on a video all night must not paint the whole map). */
        const val MAX_EVENT_MIN = 12 * 60
        private val TIMED = setOf(EventType.VIDEO, EventType.GAME, EventType.LEARN, EventType.QUIZ, EventType.DOWNLOAD, EventType.APP, EventType.SESSION)

        /** Spreads every event over the hours it covers, in [zone] (daylight-saving days included: the repeated hour is counted where the clock says it is). */
        fun build(events: Collection<ActivityEvent>, zone: ZoneId): Heatmap {
            val ms = Array(7) { LongArray(24) }; var any = false
            for (e in events) {
                val dur = e.durMin ?: continue
                if (dur <= 0 || e.type !in TIMED) continue
                any = true
                var cur = Instant.ofEpochMilli(e.ts); val end = cur.plusSeconds(dur.coerceAtMost(MAX_EVENT_MIN) * 60L)
                while (cur < end) {
                    val z = cur.atZone(zone)
                    val next = z.truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant()
                    val seg = if (next < end) next else end
                    ms[z.dayOfWeek.value - 1][z.hour] += seg.toEpochMilli() - cur.toEpochMilli()
                    cur = seg
                }
            }
            return Heatmap(ms, any)
        }
    }
}

/** Longest sessions (timed events), the late-night use and the use outside the allowed hours. */
object UseStats {
    fun longest(events: Collection<ActivityEvent>, n: Int = 5): List<ActivityEvent> =
        events.filter { (it.durMin ?: 0) > 0 && it.type in setOf(EventType.VIDEO, EventType.GAME, EventType.LEARN, EventType.QUIZ, EventType.SESSION) }.sortedByDescending { it.durMin }.take(n)

    /**
     * Minutes of timed events outside the allowed hours [window] ("HH:MM à HH:MM", as the TV writes it). Null when there is no window or no timed
     * event at all (unknown, not zero).
     */
    fun outsideWindowMin(events: Collection<ActivityEvent>, window: String?, zone: ZoneId): Long? {
        val w = parseWindow(window) ?: return null
        val timed = events.filter { (it.durMin ?: 0) > 0 && it.type != EventType.APP }
        if (timed.isEmpty()) return null
        var out = 0L
        for (e in timed) {
            val z = Instant.ofEpochMilli(e.ts).atZone(zone)
            val start = z.hour * 60 + z.minute; val dur = e.durMin!!.coerceAtMost(Heatmap.MAX_EVENT_MIN)
            out += dur - overlap(start, dur, w.first, w.second)
        }
        return out
    }

    fun parseWindow(s: String?): Pair<Int, Int>? {
        val m = Regex("""(\d{1,2}):(\d{2})\s*à\s*(\d{1,2}):(\d{2})""").find(s ?: return null) ?: return null
        val (a, b, c, d) = m.destructured
        val f = a.toInt() * 60 + b.toInt(); val t = c.toInt() * 60 + d.toInt()
        return if (f in 0..1439 && t in 0..1439 && f != t) f to t else null
    }

    /** Minutes of [start, start+dur) (minutes since midnight, may wrap) that fall inside the allowed window [from, to) (may wrap). */
    private fun overlap(start: Int, dur: Int, from: Int, to: Int): Int {
        val allowed = if (from < to) listOf(from to to) else listOf(from to 1440, 0 to to)
        var s = 0
        for (day in 0..1) for ((a, b) in allowed) {
            val lo = maxOf(start, a + day * 1440); val hi = minOf(start + dur, b + day * 1440)
            if (hi > lo) s += hi - lo
        }
        return s
    }
}
