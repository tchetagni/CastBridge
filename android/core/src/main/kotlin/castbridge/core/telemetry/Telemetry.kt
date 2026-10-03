package castbridge.core.telemetry

import castbridge.core.net.JsonLite
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

/**
 * Closed catalog of the usage events (docs/TELEMETRY.md; the server validates the same list: backend EventCatalog).
 * Only the listed property keys are sent; an event carrying a forbidden key (file name, title, path, URL, contact,
 * secret, location…) is dropped on the device already.
 */
object EventCatalog {
    /** Stable feature ids (tiles / menu entries). Screens use the same ids, plus [OTHER_SCREENS]. */
    val TV_FEATURES = listOf("library", "quiz", "chess", "receive", "usb", "bluetooth", "internet", "wifi_direct", "admin", "downloads",
        "updates", "settings", "learn", "remote", "help", "dev_options", "games", "sudoku", "store")
    val PHONE_FEATURES = listOf("send", "move", "watch_on_tv", "tv_library", "file_exchange", "remote", "player", "cast", "quiz", "chess",
        "bt_gateway", "downloads", "updates", "settings", "learn", "games", "store")
    val OTHER_SCREENS = listOf("home", "onboarding", "player", "privacy")

    /** Kept even without the "usage statistics" consent: needed to maintain the apps (errors, updates). */
    val ESSENTIAL = setOf("error", "crash", "update_install")

    val EVENTS: Map<String, Set<String>> = mapOf(
        "session_start" to emptySet(),
        "session_end" to setOf("ms"),
        "screen_view" to setOf("screen"),
        "screen_time" to setOf("screen", "ms"),
        "feature_used" to setOf("feature", "source"),
        "cast_start" to setOf("channel", "mode", "bytes"),
        "cast_end" to setOf("channel", "mode", "bytes", "ms", "kbps", "ok", "error"),
        "playback_start" to setOf("codec", "resolution", "hw", "source"),
        "playback_end" to setOf("ms", "pct", "codec", "resolution", "hw", "source", "abandoned", "ok", "error"),
        "library_stats" to setOf("files", "bytes"),
        "quiz_game" to setOf("mode", "duel", "track", "level", "field", "players", "score", "ms", "jokers"),
        "quiz_answer" to setOf("question", "correct", "ms"),
        // Sudoku (docs/GAMES.md): one event per finished or abandoned game; level = EASY|MEDIUM|HARD|EXPERT, result = win|abandon
        "sudoku_game" to setOf("level", "ms", "result", "hints"),
        "chess_game" to setOf("mode", "ai_level", "time_control", "result", "moves", "ms"),
        "download" to setOf("type", "bytes", "ms", "ok", "error"),
        "gateway_session" to setOf("ms", "bytes"),
        "connectivity_check" to setOf("via", "ok", "latency_ms"),
        "update_install" to setOf("from", "to", "ok", "error"),
        "error" to setOf("screen", "type", "message"),
        "crash" to setOf("screen", "type", "message"),
        // « Apprendre » (docs/LEARN.md § 6): content ids only (packs, lessons, exercises), never a pupil's first name
        "learn" to setOf("action", "pack", "lesson", "subject", "exercise", "correct", "points", "max", "attempt", "box", "score",
            "out_of", "ms", "badge", "level", "version"),
        // Quality signals of the content (docs/CONTENT-VALIDATION.md § 5): per item totals since the last flush, ids and numbers only
        "content_stat" to setOf("kind", "item", "shown", "correct", "ms", "reports"),
    )

    /** Values of "action" in the "learn" event (the names of LearnProgress.EVENTS). */
    val LEARN_ACTIONS = setOf("profile_created", "lesson_view", "lesson_complete", "exercise_result", "review_result", "mock_exam_result",
        "badge_earned", "pack_installed")

    val FORBIDDEN = setOf("filename", "file", "file_name", "title", "path", "url", "uri", "email", "phone", "contact", "contacts", "password",
        "pin", "key", "token", "secret", "lat", "lon", "lng", "latitude", "longitude", "gps", "location", "ip", "ssid", "bssid", "mac",
        "imei", "serial", "account", "user", "username", "name")

    fun features(app: String) = if (app == "phone") PHONE_FEATURES else TV_FEATURES
}

enum class Consent {
    /** Identification, versions, errors: what the updates need. */
    ESSENTIAL,
    /** Plus the usage statistics (only if the user accepted them on the information screen). */
    USAGE,
}

/** One event as sent to the server ("ts" in epoch milliseconds, device clock). */
data class TelemetryEvent(val id: String, val ts: Long, val sessionId: String?, val name: String, val versionCode: Int,
                          val props: Map<String, Any?>) {
    fun toJson(): String = JsonLite.write(linkedMapOf("id" to id, "ts" to ts, "sessionId" to sessionId, "name" to name,
        "versionCode" to versionCode, "props" to props))
}

/**
 * Bounded persistent queue of events (one JSON per line in [file], at most [maxBytes], 2 MB by default): when full,
 * the oldest events are dropped. Survives restarts; events leave it only once the server acknowledged them.
 */
class EventQueue(private val file: File, private val maxBytes: Long = 2L shl 20) {
    @Synchronized
    fun add(json: String) {
        file.parentFile?.mkdirs()
        file.appendText(json.replace('\n', ' ') + "\n", Charsets.UTF_8)
        if (file.length() > maxBytes) {
            // drop the oldest lines until 90 % of the limit
            val lines = readLines()
            var size = lines.sumOf { it.toByteArray().size + 1L }
            var from = 0
            while (from < lines.size && size > maxBytes * 9 / 10) { size -= lines[from].toByteArray().size + 1L; from++ }
            rewrite(lines.subList(from, lines.size))
        }
    }

    /** The oldest [max] events, and at most [maxBytes] of them. */
    @Synchronized
    fun peek(max: Int = 500, maxBytes: Int = 1_500_000): List<String> {
        val out = ArrayList<String>()
        var size = 0
        for (l in readLines()) {
            if (out.size >= max || size + l.length > maxBytes) break
            out += l; size += l.length + 1
        }
        return out
    }

    /** Removes the first [count] events (after the server acknowledged them). */
    @Synchronized
    fun drop(count: Int) {
        if (count <= 0) return
        val lines = readLines()
        rewrite(lines.drop(count))
    }

    /** Removes the queued events matching [drop] (e.g. usage events when the consent is withdrawn). */
    @Synchronized
    fun removeIf(drop: (String) -> Boolean) {
        val lines = readLines()
        val kept = lines.filterNot(drop)
        if (kept.size != lines.size) rewrite(kept)
    }

    @Synchronized
    fun size(): Int = readLines().size

    @Synchronized
    fun clear() { file.delete() }

    fun bytes(): Long = if (file.isFile) file.length() else 0

    private fun readLines(): List<String> = if (file.isFile) file.readLines(Charsets.UTF_8).filter { it.isNotBlank() } else emptyList()

    private fun rewrite(lines: List<String>) {
        val tmp = File(file.path + ".tmp")
        tmp.writeText(if (lines.isEmpty()) "" else lines.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
        if (!tmp.renameTo(file)) { file.delete(); if (!tmp.renameTo(file)) throw IOException("rename failed") }
    }
}

/**
 * What the app calls to record usage. Filters by consent (only the essential events without "usage statistics"),
 * keeps only the catalog's keys, drops events with a forbidden key, cuts long texts, and queues the rest.
 *
 * @param consent current choice of the user (read at each event: a change applies at once)
 * @param clock milliseconds since the epoch (replaced in tests)
 */
class Telemetry(
    private val app: String,
    private val versionCode: Int,
    private val queue: EventQueue,
    private val consent: () -> Consent,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    @Volatile var sessionId: String? = null
        private set
    @Volatile private var sessionStart = 0L

    /** @return true if queued, false if filtered out (consent, catalog, forbidden key) */
    fun track(name: String, props: Map<String, Any?> = emptyMap()): Boolean {
        val allowed = EventCatalog.EVENTS[name] ?: return false
        if (name !in EventCatalog.ESSENTIAL && consent() != Consent.USAGE) return false
        if (props.keys.any { it.lowercase() in EventCatalog.FORBIDDEN }) return false
        val kept = LinkedHashMap<String, Any?>()
        for ((k, v) in props) {
            if (k !in allowed || v == null) continue
            kept[k] = when (v) {
                // the server accepts short codes only ([A-Za-z0-9_.:+/-], ≤ 64), free text only in "message"
                is String -> if (k == "message") v.take(200) else code(v) ?: continue
                is Boolean -> v
                is Int, is Long -> (v as Number).toLong().coerceAtLeast(0)
                is Double, is Float -> (v as Number).toDouble().takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: continue
                is Number -> v.toLong().coerceAtLeast(0)
                else -> continue
            }
        }
        if (name == "feature_used" && kept["feature"] !in EventCatalog.features(app)) return false
        if ((name == "screen_view" || name == "screen_time") && kept["screen"] !in EventCatalog.features(app) && kept["screen"] !in EventCatalog.OTHER_SCREENS) return false
        if (name == "learn" && kept["action"] !in EventCatalog.LEARN_ACTIONS) return false
        queue.add(TelemetryEvent(UUID.randomUUID().toString(), clock(), sessionId, name, versionCode, kept).toJson())
        return true
    }

    fun startSession() {
        sessionId = UUID.randomUUID().toString()
        sessionStart = clock()
        track("session_start")
    }

    /** [at]: when the session really ended (the app was hidden then), by default now. */
    fun endSession(at: Long = clock()) {
        if (sessionId == null) return
        track("session_end", mapOf("ms" to (at - sessionStart).coerceAtLeast(0)))
        sessionId = null
    }

    /** A tile / menu entry was used ([feature] from [EventCatalog.TV_FEATURES] / [EventCatalog.PHONE_FEATURES]). */
    fun featureUsed(feature: String, source: String = "tile") = track("feature_used", mapOf("feature" to feature, "source" to source))

    fun screenView(screen: String) = track("screen_view", mapOf("screen" to screen))

    fun screenTime(screen: String, ms: Long) = track("screen_time", mapOf("screen" to screen, "ms" to ms))

    fun error(screen: String?, type: String, message: String) = track("error", mapOf("screen" to screen, "type" to type, "message" to message))

    companion object {
        /** A short code as the server accepts it: other characters become "_", at most 64; null if nothing is left. */
        fun code(v: String): String? = v.trim().replace(Regex("[^A-Za-z0-9_.:+/-]+"), "_").trim('_').take(64).ifEmpty { null }

        /**
         * To count distinct things (e.g. distinct videos watched) without ever sending their names: SHA-256 of
         * [deviceSalt] + value, first 16 hex digits. [deviceSalt] is random per device and never sent.
         */
        fun hashForCounting(value: String, deviceSalt: String): String =
            MessageDigest.getInstance("SHA-256").digest("$deviceSalt:$value".toByteArray()).take(8).joinToString("") { "%02x".format(it) }
    }
}

/**
 * Time spent on each screen: [enter] sends screen_view, and the screen left gets a screen_time with its duration (at most
 * [maxMs]: a TV left on). Screens are feature ids or [EventCatalog.OTHER_SCREENS].
 */
class ScreenClock(private val telemetry: () -> Telemetry?, private val clock: () -> Long = { System.currentTimeMillis() },
                  private val maxMs: Long = 4 * 3_600_000L) {
    private var current: String? = null
    private var since = 0L

    @Synchronized fun enter(screen: String) {
        if (screen == current) return
        leave()
        current = screen; since = clock()
        telemetry()?.screenView(screen)
    }

    @Synchronized fun leave() {
        val c = current ?: return
        current = null
        val ms = clock() - since
        if (ms >= 1000) telemetry()?.screenTime(c, minOf(ms, maxMs))
    }

    val screen: String? @Synchronized get() = current
}

/**
 * App session: starts when the first screen of the app is shown, ends [graceMs] after the last one was hidden (a screen
 * replacing another is not a new session). The app calls [shown] / [hidden] from its activities' onStart / onStop, and
 * [check] from a timer.
 */
class SessionTracker(private val telemetry: () -> Telemetry?, private val clock: () -> Long = { System.currentTimeMillis() },
                     private val graceMs: Long = 30_000) {
    private var visible = 0
    private var hiddenAt = 0L

    @Synchronized fun shown() {
        if (visible++ == 0) {
            val t = telemetry() ?: return
            if (t.sessionId != null && hiddenAt > 0 && clock() - hiddenAt > graceMs) t.endSession(hiddenAt)
            if (t.sessionId == null) t.startSession()
            hiddenAt = 0
        }
    }

    @Synchronized fun hidden() {
        if (visible > 0 && --visible == 0) hiddenAt = clock()
    }

    @Synchronized fun check() {
        if (visible == 0 && hiddenAt > 0 && clock() - hiddenAt > graceMs) {
            telemetry()?.let { if (it.sessionId != null) it.endSession(hiddenAt) }
            hiddenAt = 0
        }
    }
}
