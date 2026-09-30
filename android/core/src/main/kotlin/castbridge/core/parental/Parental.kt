package castbridge.core.parental

import castbridge.core.net.JsonLite
import java.io.File
import java.io.IOException

/**
 * Parental control, kept entirely on the TV: a local activity log and the restrictions. Nothing is sent to the server.
 * The log records what the TV does (CastBridge) and which app is in the foreground (via the optional accessibility
 * service); it never contains URLs, secrets or account data.
 */
object Parental {
    /** Entry types. */
    const val TYPE_PLAY = "play"          // a video started (label = title)
    const val TYPE_STOP = "stop"          // a video ended (label = title + duration watched)
    const val TYPE_FILE = "file"          // a file was sent / deleted (label = description)
    const val TYPE_DOWNLOAD = "download"  // a download finished (label = description)
    const val TYPE_APP = "app"            // an app came to the foreground (label = app label)
    const val TYPE_GAME = "game"          // quiz / chess / learn (label = description)
    const val TYPE_SETTINGS = "settings"  // a restriction or a setting changed

    const val MAX_LABEL = 200
}

/** One recorded line of activity. */
data class ActivityEntry(val ts: Long, val type: String, val label: String) {
    fun toJson(): String = JsonLite.write(linkedMapOf("ts" to ts, "type" to type, "label" to label))

    companion object {
        fun parse(json: String): ActivityEntry? = runCatching {
            val o = JsonLite.obj(json)
            val ts = (o["ts"] as? Number)?.toLong() ?: return null
            val type = o["type"] as? String ?: return null
            val label = o["label"] as? String ?: ""
            ActivityEntry(ts, type, label)
        }.getOrNull()
    }
}

/**
 * A bounded, persistent log (one JSON per line). When it exceeds [maxEntries], the oldest entries are dropped.
 * Survives restarts; nothing leaves the device.
 */
class ActivityLog(private val file: File, private val maxEntries: Int = 2000) {
    @Synchronized
    fun record(type: String, label: String, ts: Long = System.currentTimeMillis()) {
        file.parentFile?.mkdirs()
        file.appendText(ActivityEntry(ts, type, label.take(Parental.MAX_LABEL)).toJson().replace('\n', ' ') + "\n", Charsets.UTF_8)
        trim()
    }

    /** Entries, oldest first. */
    @Synchronized
    fun list(): List<ActivityEntry> = readLines().mapNotNull { ActivityEntry.parse(it) }

    @Synchronized
    fun clear() { file.delete() }

    @Synchronized
    fun size(): Int = readLines().size

    private fun trim() {
        val lines = readLines()
        if (lines.size <= maxEntries) return
        rewrite(lines.drop(lines.size - maxEntries))
    }

    private fun readLines(): List<String> = if (file.isFile) file.readLines(Charsets.UTF_8).filter { it.isNotBlank() } else emptyList()

    private fun rewrite(lines: List<String>) {
        val tmp = File(file.path + ".tmp")
        tmp.writeText(if (lines.isEmpty()) "" else lines.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
        if (!tmp.renameTo(file)) { file.delete(); if (!tmp.renameTo(file)) throw IOException("rename failed") }
    }
}

/**
 * The rules a parent can set, and the decision helpers. Times are minutes since midnight (local time).
 * [blockedBefore] and [blockedAfter] define a nightly window (e.g. blocked from 22:00 to 06:00).
 * [dailyLimitMin] limits total playback to N minutes per day (0 or negative = no limit).
 */
data class Restrictions(
    val enabled: Boolean = false,
    /** Playback is blocked from this minute (inclusive)… */
    val blockedAfterMin: Int = -1,
    /** …until this minute (exclusive). A window crossing midnight uses after > before. */
    val blockedBeforeMin: Int = -1,
    /** Playing any video asks for the parental PIN. */
    val requirePinForPlayback: Boolean = false,
    /** Maximum playback minutes per day; 0 or negative = unlimited. */
    val dailyLimitMin: Int = 0,
    /** Blocking other apps is only possible through the launcher; not implemented by the app. */
) {
    fun isNightWindow(nowMin: Int): Boolean {
        val after = blockedAfterMin; val before = blockedBeforeMin
        if (after < 0 || before < 0) return false
        return if (after <= before) nowMin in after until before
        else nowMin >= after || nowMin < before
    }

    /** null = allowed, else why not (French, for the screen). */
    fun playbackBlocked(nowMin: Int): String? = when {
        !enabled -> null
        isNightWindow(nowMin) -> "Lecture bloquée pendant les heures calmes (${fmt(blockedAfterMin)}–${fmt(blockedBeforeMin)})."
        else -> null
    }

    /** null = allowed, else why not (daily limit). */
    fun dailyLimitReached(usedMinutes: Int): String? = when {
        !enabled -> null
        dailyLimitMin <= 0 -> null
        usedMinutes >= dailyLimitMin -> "Temps d'écran épuisé pour aujourd'hui ($dailyLimitMin min autorisées)."
        else -> null
    }

    /** null = allowed, else why not. */
    fun needsPin(nowMin: Int): String? = when {
        !enabled -> null
        requirePinForPlayback -> "Un code parental est demandé pour lire une vidéo."
        else -> null
    }

    companion object {
        /** "22:00" -> 1320 ; null on garbage. */
        fun parseMin(s: String?): Int? = runCatching {
            val p = s!!.split(':')
            val h = p[0].toInt(); val m = p.getOrNull(1)?.toInt() ?: 0
            if (h in 0..23 && m in 0..59) h * 60 + m else null
        }.getOrNull()

        fun fmt(min: Int): String = if (min < 0) "—" else "%02d:%02d".format(min / 60, min % 60)
    }
}

/**
 * Tracks daily playback time. Resets automatically when the day changes.
 * Thread-safe; all state is in-memory (resets on TV restart — conservative, no over-blocking).
 */
class DailyUsage {
    @Volatile private var dayKey: Int = 0        // yyyyMMdd
    @Volatile private var usedMs: Long = 0
    @Volatile private var sessionStartMs: Long = 0

    private fun today(): Int {
        val c = java.util.Calendar.getInstance()
        return c.get(java.util.Calendar.YEAR) * 10000 + (c.get(java.util.Calendar.MONTH) + 1) * 100 + c.get(java.util.Calendar.DAY_OF_MONTH)
    }

    @Synchronized
    private fun rollDay() { val d = today(); if (d != dayKey) { dayKey = d; usedMs = 0 } }

    /** Call when a video starts playing. */
    @Synchronized fun playStarted() { rollDay(); sessionStartMs = System.currentTimeMillis() }

    /** Call when a video stops; adds the elapsed time to today's total. */
    @Synchronized fun playStopped() {
        if (sessionStartMs > 0) { rollDay(); usedMs += System.currentTimeMillis() - sessionStartMs; sessionStartMs = 0 }
    }

    /** Today's total playback in minutes (including a running session). */
    @Synchronized fun usedMinutes(): Int {
        rollDay()
        val running = if (sessionStartMs > 0) System.currentTimeMillis() - sessionStartMs else 0
        return ((usedMs + running) / 60_000).toInt()
    }

    /** Remaining minutes today, or Int.MAX_VALUE if no limit. */
    fun remainingMinutes(limit: Int): Int = if (limit <= 0) Int.MAX_VALUE else (limit - usedMinutes()).coerceAtLeast(0)
}
