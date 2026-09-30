package castbridge.core.phone

import castbridge.core.tv.LibraryLogic
import castbridge.core.tv.Progressive
import java.util.Locale

/*
 * Pure logic of the phone's own media player ("Ouvrir avec" -> play -> cast to a TV). Everything here runs on the JVM
 * without Android so it is unit-tested in :core; the sender app only wires it to Media3, MediaStore and the TV clients.
 */

/** What kind of media a file is, from its MIME type first, then its extension. */
enum class MediaKind {
    VIDEO, AUDIO, IMAGE, OTHER;

    companion object {
        private val VIDEO_EXT = setOf("mp4", "m4v", "mkv", "webm", "avi", "mov", "ts", "m2ts", "mts", "mpg", "mpeg", "wmv", "asf",
            "flv", "3gp", "3g2", "ogv", "vob", "divx", "rmvb", "rm", "m3u8", "mpd")
        private val AUDIO_EXT = setOf("mp3", "m4a", "m4b", "aac", "flac", "ogg", "oga", "opus", "wav", "wma", "ac3", "eac3", "dts", "mka", "amr", "mid", "midi")
        private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif")

        fun of(mime: String?, name: String?): MediaKind {
            val m = mime?.lowercase(Locale.ROOT).orEmpty()
            when {
                m.startsWith("video/") -> return VIDEO
                m.startsWith("audio/") -> return AUDIO
                m.startsWith("image/") -> return IMAGE
                m == "application/x-mpegurl" || m == "application/vnd.apple.mpegurl" || m == "application/dash+xml" -> return VIDEO
            }
            return when (ext(name)) {
                in VIDEO_EXT -> VIDEO
                in AUDIO_EXT -> AUDIO
                in IMAGE_EXT -> IMAGE
                else -> OTHER
            }
        }

        fun ext(name: String?): String = name.orEmpty().substringBefore('?').substringAfterLast('/').substringAfterLast('.', "").lowercase(Locale.ROOT)
    }
}

/** How an http(s) link must be opened by the player. */
enum class StreamType { PROGRESSIVE, HLS, DASH;
    companion object {
        fun of(url: String, mime: String?): StreamType {
            val m = mime?.lowercase(Locale.ROOT).orEmpty()
            val e = MediaKind.ext(url)
            return when {
                m == "application/x-mpegurl" || m == "application/vnd.apple.mpegurl" || m == "audio/mpegurl" || e == "m3u8" -> HLS
                m == "application/dash+xml" || e == "mpd" -> DASH
                else -> PROGRESSIVE
            }
        }
    }
}

/**
 * Formats that the phone's hardware/Media3 often cannot play, whereas the TV (libVLC) plays nearly everything. Only a hint
 * shown up-front; the real verdict comes from the player (see [PhoneFormats.explain]).
 */
object PhoneFormats {
    private val RISKY_CONTAINERS = setOf("wmv", "asf", "rm", "rmvb", "divx", "vob", "wma")
    /** Audio codecs Android phones usually lack a decoder for (Dolby/DTS licences). */
    private val RISKY_AUDIO = setOf("audio/ac3", "audio/eac3", "audio/eac3-joc", "audio/true-hd", "audio/vnd.dts", "audio/vnd.dts.hd", "audio/vnd.dts.uhd", "audio/x-ms-wma")

    fun riskyContainer(name: String?) = MediaKind.ext(name) in RISKY_CONTAINERS
    fun riskyAudio(sampleMime: String?) = sampleMime?.lowercase(Locale.ROOT) in RISKY_AUDIO

    /** Human name of a codec MIME ("video/hevc" -> "HEVC (H.265)"). */
    fun codecName(mime: String?): String = when (mime?.lowercase(Locale.ROOT)) {
        null, "" -> "inconnu"
        "video/hevc" -> "HEVC (H.265)"; "video/avc" -> "H.264"; "video/av01" -> "AV1"; "video/x-vnd.on2.vp9" -> "VP9"
        "video/mp4v-es" -> "MPEG-4 Part 2 (DivX/Xvid)"; "video/mpeg2" -> "MPEG-2"; "video/dolby-vision" -> "Dolby Vision"
        "video/wvc1", "video/x-ms-wmv" -> "VC-1 / WMV"
        "audio/ac3" -> "Dolby Digital (AC3)"; "audio/eac3", "audio/eac3-joc" -> "Dolby Digital Plus (E-AC3)"; "audio/true-hd" -> "Dolby TrueHD"
        "audio/vnd.dts", "audio/vnd.dts.hd", "audio/vnd.dts.uhd" -> "DTS"; "audio/mp4a-latm" -> "AAC"; "audio/mpeg" -> "MP3"; "audio/opus" -> "Opus"
        "audio/flac" -> "FLAC"; "audio/vorbis" -> "Vorbis"
        else -> mime.substringAfter('/').uppercase(Locale.ROOT)
    }

    /**
     * The message shown when the phone cannot play (part of) a file. [unsupportedVideo]/[unsupportedAudio] are the codec
     * MIME types the player reported as unsupported; [fatal] = nothing plays at all.
     */
    fun explain(unsupportedVideo: List<String>, unsupportedAudio: List<String>, fatal: Boolean, name: String?): String {
        val parts = mutableListOf<String>()
        if (unsupportedVideo.isNotEmpty()) parts += "l'image (${unsupportedVideo.joinToString { codecName(it) }})"
        if (unsupportedAudio.isNotEmpty()) parts += "le son (${unsupportedAudio.joinToString { codecName(it) }})"
        val what = when {
            parts.isNotEmpty() -> "Ce téléphone ne sait pas décoder " + parts.joinToString(" ni ") + "."
            fatal && riskyContainer(name) -> "Le format « .${MediaKind.ext(name)} » n'est pas lu par ce téléphone."
            fatal -> "Ce fichier ne peut pas être lu par ce téléphone."
            else -> return ""
        }
        return "$what La TV CastBridge lit presque tout : « Caster vers la TV »."
    }
}

/**
 * Where to resume each file. Keyed by name + size (the same video opened from the Gallery, Files or WhatsApp has a
 * different URI each time but the same name and size); the URI is the fallback when the size is unknown.
 * The rule is the TV's own ([LibraryLogic.resumeFrom]): nothing is kept for the first 10 s or once the video is watched.
 * At most [max] entries, the least recently used ones are dropped.
 */
class ResumeBook(private val max: Int = 500) {
    private val map = LinkedHashMap<String, Long>(16, 0.75f, true)

    fun get(key: String): Long = synchronized(map) { map[key] ?: 0 }

    /** Records the position reached; returns what was kept (0 = forgotten). */
    fun update(key: String, posMs: Long, durMs: Long): Long = synchronized(map) {
        val keep = LibraryLogic.resumeFrom(posMs, durMs)
        if (keep <= 0) map.remove(key) else {
            map[key] = keep
            while (map.size > max) map.remove(map.keys.first())
        }
        keep
    }

    /** Where to start [key] (0 = from the beginning); a saved position beyond the real duration is ignored. */
    fun startAt(key: String, durMs: Long): Long {
        val p = get(key)
        return if (durMs > 0 && p >= durMs) 0 else p
    }

    fun size() = synchronized(map) { map.size }

    /** One "key\tpos" per line, oldest first (the order is kept when read back). */
    fun serialize(): String = synchronized(map) { map.entries.joinToString("\n") { "${it.key.replace('\t', ' ').replace('\n', ' ')}\t${it.value}" } }

    companion object {
        fun key(name: String?, size: Long, uri: String): String =
            if (!name.isNullOrBlank() && size > 0) "${name.lowercase(Locale.ROOT)}|$size" else uri

        fun parse(text: String?, max: Int = 500): ResumeBook = ResumeBook(max).also { b ->
            text.orEmpty().lineSequence().forEach { line ->
                val k = line.substringBeforeLast('\t', ""); val v = line.substringAfterLast('\t').toLongOrNull()
                if (k.isNotEmpty() && v != null && v > 0) synchronized(b.map) { b.map[k] = v }
            }
        }
    }
}

/** The file currently open on the phone, as far as casting is concerned. */
data class CastSource(
    val kind: MediaKind,
    /** "content", "file", "http", "https". */
    val scheme: String,
    /** Content provider authority for content:// URIs (decides whether a move may delete the original). */
    val authority: String? = null,
    val sizeBytes: Long = -1,
) {
    val isWeb get() = scheme == "http" || scheme == "https"
}

enum class TargetKind { CASTBRIDGE, DLNA }

enum class CastAction(val label: String) {
    /** The TV reads the file from the phone (or the web link itself): nothing is stored on the TV. */
    LIVE("Lire en direct (sans copier)"),
    /** The file is copied to the TV, which starts playing as soon as it holds enough. */
    COPY("Copier sur la TV et lire"),
    /** Copy, then delete from the phone once the TV confirms a complete copy of the exact size. */
    MOVE("Déplacer vers la TV"),
}

/** Which ways of sending a file to a given TV make sense. Pure rules, shown in the "Diffuser sur" sheet. */
object CastPlan {
    /** Providers whose files the app can delete after a move (itself or through Android's own confirmation). */
    private val DELETABLE_AUTHORITIES = setOf("media", "com.android.providers.media.documents", "com.android.externalstorage.documents",
        "com.android.providers.downloads.documents")

    fun canMove(src: CastSource): Boolean = when (src.scheme) {
        "content" -> src.authority in DELETABLE_AUTHORITIES
        else -> false            // file:// from another app, web links: never delete what we do not own
    }

    fun actions(target: TargetKind, src: CastSource): List<CastAction> = when {
        src.kind == MediaKind.OTHER -> emptyList()
        src.isWeb -> listOf(CastAction.LIVE)                       // a link cannot be uploaded: the TV opens it itself
        target == TargetKind.DLNA -> listOf(CastAction.LIVE)       // a plain DLNA renderer has no storage of its own
        else -> listOfNotNull(CastAction.LIVE, CastAction.COPY, CastAction.MOVE.takeIf { canMove(src) })
    }

    /** The phone's small HTTP server (MediaServer) must serve the file for this choice. */
    fun needsPhoneServer(action: CastAction, src: CastSource) = action == CastAction.LIVE && !src.isWeb

    /** Whether playback on the TV starts (and the phone becomes a remote) after this action. Photos are only stored. */
    fun playsOnTv(action: CastAction, src: CastSource) = action == CastAction.LIVE || src.kind != MediaKind.IMAGE

    /** The phone keeps playing until the TV really starts (copy: the TV must first receive enough). */
    fun phoneWaitsForTv(action: CastAction) = action != CastAction.LIVE
}

/** Position hand-over between the phone and the TV. */
object Handoff {
    /** Replayed on the TV so no word is lost during the switch (the TV takes a moment to start). */
    const val REWIND_MS = 2_000L
    /** Closer than this to the end: start the TV from the beginning instead of showing the last seconds. */
    const val END_MS = 10_000L

    /** Where the TV should start, from the phone's position. */
    fun phoneToTv(phonePosMs: Long, durMs: Long): Long = when {
        phonePosMs <= REWIND_MS -> 0
        durMs > 0 && phonePosMs >= durMs - END_MS -> 0
        else -> phonePosMs - REWIND_MS
    }

    /** DLNA Seek takes whole seconds. */
    fun toDlnaSeconds(ms: Long): Long = (ms.coerceAtLeast(0) / 1000)

    /**
     * Where the phone resumes after "Revenir sur le téléphone": the TV's last reported position (if the TV already
     * stopped or ended, its last known one), clamped to the local duration; the end of the file means the beginning.
     */
    fun tvToPhone(tvPosMs: Long, lastKnownTvPosMs: Long, localDurMs: Long): Long {
        val p = if (tvPosMs > 0) tvPosMs else lastKnownTvPosMs
        return when {
            p <= 0 -> 0
            localDurMs > 0 && p >= localDurMs - 1_000 -> 0
            localDurMs > 0 -> p.coerceAtMost(localDurMs)
            else -> p
        }
    }

    /**
     * Copy/move: may the TV start now at the phone's current position? True once it holds the data up to 30 s beyond it
     * (the phone keeps playing meanwhile and only pauses when the TV takes over). An MP4 with its index at the end needs
     * the whole file.
     */
    fun copyReady(received: Long, total: Long, durMs: Long, phonePosMs: Long, moovAtEnd: Boolean, uploadBytesPerSec: Long): Boolean =
        total > 0 && (received >= total ||
            received >= Progressive.handoffBytes(total, durMs, phoneToTv(phonePosMs, durMs), 30_000, moovAtEnd, uploadBytesPerSec))
}

/**
 * Progress shown on the phone while the TV plays: the TV is polled every second or so, in between the position moves on
 * by itself (when playing) so the bar is smooth; never past the duration.
 */
data class RemoteClock(val posMs: Long = 0, val durMs: Long = 0, val playing: Boolean = false, val atMs: Long = 0) {
    fun now(nowMs: Long): Long {
        val p = if (playing) posMs + (nowMs - atMs).coerceAtLeast(0) else posMs
        return if (durMs > 0) p.coerceIn(0, durMs) else p.coerceAtLeast(0)
    }
}

/** Swipe gestures of the player, as pure arithmetic. */
object Gestures {
    /** A swipe across the whole width seeks by 90 s (less for short clips: at most the whole duration). */
    fun seekDelta(dxPx: Float, widthPx: Float, durMs: Long): Long {
        if (widthPx <= 0) return 0
        val full = if (durMs > 0) minOf(90_000L, durMs) else 90_000L
        return (dxPx / widthPx * full).toLong()
    }

    /** Brightness/volume level after a vertical swipe of [dyPx] (up = negative = louder), full height = the whole range. */
    fun level(start: Float, dyPx: Float, heightPx: Float): Float =
        if (heightPx <= 0) start else (start - dyPx / heightPx).coerceIn(0f, 1f)

    enum class Zone { BRIGHTNESS, VOLUME }
    /** Left half of the screen = brightness, right half = volume. */
    fun zone(xPx: Float, widthPx: Float) = if (xPx < widthPx / 2) Zone.BRIGHTNESS else Zone.VOLUME

    /** Double tap: left third = -10 s, right third = +10 s, middle = play/pause (0). */
    fun doubleTapSkip(xPx: Float, widthPx: Float): Long = when {
        xPx < widthPx / 3 -> -10_000
        xPx > widthPx * 2 / 3 -> 10_000
        else -> 0
    }
}

/** Playback speeds offered by the player. */
object Speeds {
    val ALL = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
    fun label(s: Float) = if (s == 1f) "Normale" else (if (s % 1f == 0f) "${s.toInt()}" else "$s".replace('.', ',')) + "x"
}

/**
 * "Next / previous in the same folder". Items of the same family only (videos with videos, songs with songs, photos with
 * photos), in natural order (episode 2 before episode 10).
 */
object FolderPlaylist {
    data class Entry(val id: String, val name: String, val kind: MediaKind)

    fun family(k: MediaKind) = k

    /**
     * The playlist for [current] among [siblings] (the folder's content, any order). [current] is always part of the result,
     * even if the folder listing did not contain it (a file shared by another app). Returns the items and the index of [current].
     */
    fun build(siblings: List<Entry>, current: Entry): Pair<List<Entry>, Int> {
        val same = siblings.filter { family(it.kind) == family(current.kind) && it.id != current.id }
            .distinctBy { it.id } + current
        val sorted = same.sortedWith(Comparator<Entry> { a, b -> NaturalOrder.compare(a.name, b.name) }.thenBy { it.id })
        return sorted to sorted.indexOfFirst { it.id == current.id }
    }

    fun next(size: Int, index: Int): Int? = (index + 1).takeIf { it in 0 until size }
    fun prev(size: Int, index: Int): Int? = (index - 1).takeIf { it in 0 until size }
}

/** Natural, case-insensitive order: "Episode 2" < "Episode 10", accents ignored for sorting. */
object NaturalOrder : Comparator<String> {
    private val chunk = Regex("\\d+|\\D+")
    private fun norm(s: String) = java.text.Normalizer.normalize(s.lowercase(Locale.ROOT), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")

    override fun compare(a: String, b: String): Int {
        val x = chunk.findAll(norm(a)).map { it.value }.toList()
        val y = chunk.findAll(norm(b)).map { it.value }.toList()
        for (i in 0 until minOf(x.size, y.size)) {
            val p = x[i]; val q = y[i]
            val c = if (p[0].isDigit() && q[0].isDigit()) {
                val pn = p.trimStart('0'); val qn = q.trimStart('0')
                if (pn.length != qn.length) pn.length - qn.length else pn.compareTo(qn).takeIf { it != 0 } ?: (p.length - q.length)
            } else p.compareTo(q)
            if (c != 0) return c
        }
        return x.size - y.size
    }
}

/** External subtitle MIME types understood by the phone player. */
object SubtitleTypes {
    fun mimeOf(name: String): String? = when (MediaKind.ext(name)) {
        "srt" -> "application/x-subrip"
        "ass", "ssa" -> "text/x-ssa"
        "vtt" -> "text/vtt"
        "ttml", "dfxp", "xml" -> "application/ttml+xml"
        else -> null
    }

    /** Language code from "film.fr.srt" / "film.eng.srt", or null. */
    fun language(video: String, sub: String): String? {
        val base = video.substringBeforeLast('.').lowercase(Locale.ROOT)
        val stem = sub.substringBeforeLast('.').lowercase(Locale.ROOT)
        if (!stem.startsWith("$base.")) return null
        return stem.removePrefix("$base.").takeIf { it.length in 2..3 && it.all(Char::isLetter) }
    }
}
