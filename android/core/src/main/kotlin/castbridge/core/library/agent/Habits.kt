package castbridge.core.library.agent

import java.time.Instant
import java.time.ZoneId

/**
 * What the library says about how it is used, computed locally from the play marks the TV already keeps
 * (last played time, watched flag). Used to pick the default language tag, to rank "already watched" files, and to
 * avoid heavy operations when the TV is usually being watched.
 */
data class Habits(
    /** Language version the user watches most ("VF", "VOSTFR"…), null = not enough data. */
    val audioPref: Audio?,
    val playedByKind: Map<Kind, Int>,
    /** Number of plays started in each hour of the day (24 values). */
    val hourHistogram: List<Int>,
    val plays: Int,
) {
    /** The language tag that is NOT worth writing in a name because it is what the user usually watches. */
    val defaultAudio: Audio get() = audioPref ?: Audio.VF

    /** Hours of the day when the TV is most used (at least 3 plays and 60 % of the busiest hour). */
    fun busyHours(): Set<Int> {
        val max = hourHistogram.maxOrNull() ?: 0
        if (max < 3) return emptySet()
        return hourHistogram.indices.filter { hourHistogram[it] >= 3 && hourHistogram[it] >= max * 0.6 }.toSet()
    }

    fun isBusy(hour: Int) = hour in busyHours()

    /** The kind the user watches most, if any. */
    fun favoriteKind(): Kind? = playedByKind.maxByOrNull { it.value }?.takeIf { it.value >= 3 }?.key

    companion object {
        val NONE = Habits(null, emptyMap(), List(24) { 0 }, 0)

        fun from(files: List<FileRef>, parse: (FileRef) -> Parsed, zone: ZoneId = ZoneId.systemDefault(), minPlays: Int = 5): Habits {
            val played = files.filter { it.playedAtMs > 0 }
            if (played.isEmpty()) return NONE
            val hours = IntArray(24)
            val kinds = HashMap<Kind, Int>()
            val audio = HashMap<Audio, Int>()
            for (f in played) {
                hours[Instant.ofEpochMilli(f.playedAtMs).atZone(zone).hour]++
                val p = parse(f)
                kinds.merge(p.kind, 1, Int::plus)
                p.audio?.let { audio.merge(it, 1, Int::plus) }
            }
            val pref = if (played.size >= minPlays) audio.maxByOrNull { it.value }?.takeIf { it.value >= 3 }?.key else null
            return Habits(pref, kinds, hours.toList(), played.size)
        }
    }
}
