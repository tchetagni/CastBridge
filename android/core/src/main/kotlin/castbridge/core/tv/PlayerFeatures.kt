package castbridge.core.tv

import java.util.Locale

/** One selectable track (audio or subtitle) as libVLC lists it; id -1 = "off". */
data class Track(val id: Int, val name: String)

data class Chapter(val name: String, val timeMs: Long)

/** What the video decoder reports, for the "technical info" panel. */
data class VideoInfo(val codec: String, val width: Int, val height: Int, val fps: Float, val bitrateBps: Long, val decoder: String)

/** Everything the settings panels (TV, phone, web) show about the playing file. */
data class PlayerTracks(
    val audio: List<Track> = emptyList(),
    val audioId: Int = -1,
    val subtitles: List<Track> = emptyList(),
    val subtitleId: Int = -1,
    /** External subtitle files found next to the video (same base name), selectable by [PlayerCommand.SubFile]. */
    val subtitleFiles: List<String> = emptyList(),
    val subDelayMs: Long = 0,
    val audioDelayMs: Long = 0,
    val subScale: Int = 100,
    val rate: Float = 1f,
    val aspect: String = "auto",
    val chapters: List<Chapter> = emptyList(),
    val chapter: Int = -1,
    val titles: Int = 0,
    val title: Int = -1,
    val hw: String = "auto",
    val video: VideoInfo? = null,
    val audioCodec: String? = null,
    val eqPreset: Int = -1,
    val eqPresets: List<String> = emptyList(),
    val repeat: String = "off",
    val queue: List<String> = emptyList(),
    val queueIndex: Int = -1,
) {
    fun toJson(): String {
        val q = ReceiverServer::q
        fun tracks(l: List<Track>) = l.joinToString(",", "[", "]") { """{"id":${it.id},"name":${q(it.name)}}""" }
        fun strs(l: List<String>) = l.joinToString(",", "[", "]") { q(it) }
        val v = video?.let { """{"codec":${q(it.codec)},"width":${it.width},"height":${it.height},"fps":${String.format(Locale.ROOT, "%.3f", it.fps)},"bitrate":${it.bitrateBps},"decoder":${q(it.decoder)}}""" } ?: "null"
        return """{"audio":${tracks(audio)},"audioId":$audioId,"subtitles":${tracks(subtitles)},"subtitleId":$subtitleId,"subtitleFiles":${strs(subtitleFiles)},""" +
            """"subDelayMs":$subDelayMs,"audioDelayMs":$audioDelayMs,"subScale":$subScale,"rate":${String.format(Locale.ROOT, "%.2f", rate)},"aspect":${q(aspect)},""" +
            """"chapters":${chapters.joinToString(",", "[", "]") { """{"name":${q(it.name)},"timeMs":${it.timeMs}}""" }},"chapter":$chapter,"titles":$titles,"title":$title,""" +
            """"hw":${q(hw)},"video":$v,"audioCodec":${audioCodec?.let(q) ?: "null"},"eqPreset":$eqPreset,"eqPresets":${strs(eqPresets)},""" +
            """"repeat":${q(repeat)},"queue":${strs(queue)},"queueIndex":$queueIndex,"aspects":${strs(PlayerParams.ASPECTS)}}"""
    }
}

/** A change asked by a remote (phone, web page, agent) or by the TV's own settings panel. Values are already validated. */
sealed class PlayerCommand {
    data class Audio(val id: Int) : PlayerCommand()
    /** -1 = subtitles off. */
    data class Subtitle(val id: Int) : PlayerCommand()
    /** A subtitle file stored next to the video, by its name on the TV (never a path). */
    data class SubFile(val name: String) : PlayerCommand()
    data class SubDelay(val ms: Long) : PlayerCommand()
    data class AudioDelay(val ms: Long) : PlayerCommand()
    data class SubScale(val percent: Int) : PlayerCommand()
    data class Rate(val rate: Float) : PlayerCommand()
    data class Aspect(val mode: String) : PlayerCommand()
    data class Chapter(val index: Int) : PlayerCommand()
    data class ChapterStep(val delta: Int) : PlayerCommand()
    data class Title(val index: Int) : PlayerCommand()
    data class Hw(val mode: String) : PlayerCommand()
    data class Eq(val preset: Int) : PlayerCommand()
}

/** Validation and bounds of every player setting (shared by the HTTP API and the TV panels). Pure. */
object PlayerParams {
    const val DELAY_STEP_MS = 50L
    const val MAX_DELAY_MS = 30_000L
    const val MIN_RATE = 0.5f
    const val MAX_RATE = 2f
    val RATES = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
    val SUB_SCALES = listOf(50, 75, 100, 125, 150, 200, 250, 300)
    /** auto = libVLC's best fit; fill = stretch to the screen; crop = fill the screen keeping proportions (edges cut). */
    val ASPECTS = listOf("auto", "16:9", "4:3", "fill", "crop")
    val HW_MODES = listOf("auto", "on", "off")

    fun clampDelay(ms: Long) = ms.coerceIn(-MAX_DELAY_MS, MAX_DELAY_MS)
    fun clampRate(r: Float) = if (r.isNaN()) 1f else r.coerceIn(MIN_RATE, MAX_RATE)
    fun clampSubScale(p: Int) = p.coerceIn(25, 400)

    /** A new delay from ?ms= (absolute) or ?delta= (steps of 50 ms by default, also any ms value); null = invalid. */
    fun delay(current: Long, p: Map<String, String>): Long? {
        p["ms"]?.let { return it.toLongOrNull()?.let(::clampDelay) }
        p["delta"]?.let { return it.toLongOrNull()?.let { d -> clampDelay(current + d) } }
        return null
    }

    fun rate(v: String?): Float? = v?.replace(',', '.')?.toFloatOrNull()?.takeIf { !it.isNaN() && !it.isInfinite() }?.let(::clampRate)

    fun aspect(v: String?): String? = v?.lowercase(Locale.ROOT)?.takeIf { it in ASPECTS }
    fun hw(v: String?): String? = v?.lowercase(Locale.ROOT)?.takeIf { it in HW_MODES }

    /** Parses a POST /api/player/<what> request; null = bad parameters. */
    fun command(what: String, p: Map<String, String>, cur: PlayerTracks): PlayerCommand? = when (what) {
        "audio" -> p["id"]?.toIntOrNull()?.let { PlayerCommand.Audio(it) }
        "subtitle" -> p["id"]?.toIntOrNull()?.let { PlayerCommand.Subtitle(it.coerceAtLeast(-1)) }
            ?: p["file"]?.let { f -> ReceiverServer.safeName(f)?.let { PlayerCommand.SubFile(it) } }
        "subdelay" -> delay(cur.subDelayMs, p)?.let { PlayerCommand.SubDelay(it) }
        "audiodelay" -> delay(cur.audioDelayMs, p)?.let { PlayerCommand.AudioDelay(it) }
        "subsize" -> p["value"]?.toIntOrNull()?.let { PlayerCommand.SubScale(clampSubScale(it)) }
        "rate" -> rate(p["value"])?.let { PlayerCommand.Rate(it) }
        "aspect" -> aspect(p["value"])?.let { PlayerCommand.Aspect(it) }
        "chapter" -> p["index"]?.toIntOrNull()?.let { PlayerCommand.Chapter(it) } ?: p["delta"]?.toIntOrNull()?.let { PlayerCommand.ChapterStep(it.coerceIn(-1, 1)) }
        "title" -> p["index"]?.toIntOrNull()?.let { PlayerCommand.Title(it) }
        "hw" -> hw(p["value"])?.let { PlayerCommand.Hw(it) }
        "eq" -> p["preset"]?.toIntOrNull()?.let { PlayerCommand.Eq(it.coerceAtLeast(-1)) }
        else -> null
    }

    fun delayLabel(ms: Long) = (if (ms > 0) "+" else "") + "$ms ms"
    fun rateLabel(r: Float) = String.format(Locale.ROOT, "%.2f", r).trimEnd('0').trimEnd('.').replace('.', ',') + "x"
    fun aspectLabel(a: String) = when (a) { "auto" -> "Automatique"; "fill" -> "Remplir (étiré)"; "crop" -> "Rogner (plein écran)"; else -> a }
    fun hwLabel(h: String) = when (h) { "on" -> "Matériel forcé"; "off" -> "Logiciel"; else -> "Automatique (matériel, repli logiciel)" }
}

/**
 * Per-file memory of the player settings the viewer chose (audio track, subtitles, delays, size, speed, picture format),
 * stored in [LibraryDb.Entry.prefs] as "a=2;s=-1;sf=film.fr.srt;sd=150;ad=-50;ss=125;r=1.25;ar=16:9". Only what differs
 * from the defaults is written; unknown keys are ignored (older/newer app versions).
 */
data class PlayerPrefs(
    val audio: Int? = null,
    val subtitle: Int? = null,
    val subFile: String? = null,
    val subDelayMs: Long = 0,
    val audioDelayMs: Long = 0,
    val subScale: Int = 100,
    val rate: Float = 1f,
    val aspect: String = "auto",
) {
    /** True when this file needs libVLC's subtitle engine (off by default to save memory on the TV). */
    val wantsSubtitles: Boolean get() = (subtitle ?: -1) >= 0 || subFile != null

    fun encode(): String = buildList {
        audio?.let { add("a=$it") }
        subtitle?.let { add("s=$it") }
        subFile?.let { add("sf=" + java.net.URLEncoder.encode(it, "UTF-8")) }
        if (subDelayMs != 0L) add("sd=$subDelayMs")
        if (audioDelayMs != 0L) add("ad=$audioDelayMs")
        if (subScale != 100) add("ss=$subScale")
        if (rate != 1f) add("r=" + String.format(Locale.ROOT, "%.2f", rate))
        if (aspect != "auto") add("ar=$aspect")
    }.joinToString(";")

    companion object {
        fun decode(s: String?): PlayerPrefs {
            if (s.isNullOrBlank()) return PlayerPrefs()
            val m = s.split(';').mapNotNull { kv -> kv.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
            return PlayerPrefs(
                audio = m["a"]?.toIntOrNull(),
                subtitle = m["s"]?.toIntOrNull(),
                subFile = m["sf"]?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrNull() }?.let(ReceiverServer::safeName),
                subDelayMs = PlayerParams.clampDelay(m["sd"]?.toLongOrNull() ?: 0),
                audioDelayMs = PlayerParams.clampDelay(m["ad"]?.toLongOrNull() ?: 0),
                subScale = PlayerParams.clampSubScale(m["ss"]?.toIntOrNull() ?: 100),
                rate = PlayerParams.rate(m["r"]) ?: 1f,
                aspect = PlayerParams.aspect(m["ar"]) ?: "auto",
            )
        }
    }
}

/** External subtitles next to a video: "Film.srt", "Film.fr.srt", "Film.en.ass"... (same volume, same folder). Pure. */
object SubtitleFinder {
    val EXTENSIONS = setOf("srt", "ass", "ssa", "vtt", "sub", "smi", "txt")

    fun isSubtitle(name: String) = name.substringAfterLast('.', "").lowercase(Locale.ROOT) in EXTENSIONS - "txt"

    /** Subtitle files of [video] among [names]: exact base name first, then language variants, French first. */
    fun find(video: String, names: Collection<String>, preferred: String = "fr"): List<String> {
        val base = video.substringBeforeLast('.', video).lowercase(Locale.ROOT)
        return names.filter { n ->
            val ext = n.substringAfterLast('.', "").lowercase(Locale.ROOT)
            if (ext !in EXTENSIONS || n.equals(video, true)) return@filter false
            val stem = n.substringBeforeLast('.').lowercase(Locale.ROOT)
            stem == base || (stem.startsWith("$base.") && stem.length - base.length in 2..8)
        }.sortedWith(compareBy<String>({
            val stem = it.substringBeforeLast('.').lowercase(Locale.ROOT)
            when { stem == base -> 0; stem.endsWith(".$preferred") || stem.contains(".$preferred.") -> 1; else -> 2 }
        }, { it.lowercase(Locale.ROOT) }))
    }
}

/**
 * "Play the folder / the selection one after the other", with repeat. Pure: the server asks it what comes next when a file
 * ends (or on next/previous).
 */
class Playlist(val items: List<String>, start: Int = 0, var repeat: Repeat = Repeat.OFF) {
    enum class Repeat { OFF, ALL, ONE; val label get() = name.lowercase(Locale.ROOT)
        companion object { fun of(s: String?) = values().firstOrNull { it.label == s?.lowercase(Locale.ROOT) } } }

    var index: Int = start.coerceIn(0, (items.size - 1).coerceAtLeast(0)); private set
    val current: String? get() = items.getOrNull(index)

    /** The item after the current one ([auto] = the current one ended by itself: "repeat one" plays it again). */
    fun next(auto: Boolean = true): String? {
        if (items.isEmpty()) return null
        if (auto && repeat == Repeat.ONE) return current
        if (index + 1 < items.size) { index++; return current }
        if (repeat == Repeat.ALL || repeat == Repeat.ONE) { index = 0; return current }
        return null
    }

    fun previous(): String? {
        if (items.isEmpty()) return null
        if (index > 0) { index--; return current }
        if (repeat != Repeat.OFF) { index = items.size - 1; return current }
        return current
    }

    /** Keeps the list consistent when a file was deleted or renamed. */
    fun contains(name: String) = items.any { it == name }
}
