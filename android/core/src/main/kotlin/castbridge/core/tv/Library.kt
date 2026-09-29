package castbridge.core.tv

import java.io.File

/** What the app remembers about a stored video (all optional: a file never opened has none of it). */
data class FileMeta(
    val durationMs: Long = 0,
    /** Where to resume (0 = from the start). Already normalised by [LibraryLogic.resumeFrom]. */
    val resumeMs: Long = 0,
    val watched: Boolean = false,
    val hasThumb: Boolean = false,
    /** Epoch ms of the last playback start, 0 = never played. */
    val playedAtMs: Long = 0,
)

/** Supplied by the TV app (Android: thumbnails, saved positions); the server only asks. Called on HTTP threads. */
interface LibraryMeta {
    fun meta(name: String, size: Long): FileMeta
    /** Cached JPEG thumbnail, or null (not ready: the provider may start making it; clients ask again later). [file] is set for real folders. */
    fun thumb(name: String, size: Long, file: File?): ByteArray?
}

/** Pure rules for "resume where I stopped" and "already watched", shared by the TV screen, phone and web page. */
object LibraryLogic {
    const val MIN_RESUME_MS = 10_000L          // less than that from the start: just start over
    const val END_MARGIN_MS = 30_000L          // the last half minute is credits: consider it watched
    const val WATCHED_RATIO = 0.95

    fun isWatched(posMs: Long, durMs: Long): Boolean =
        durMs > 0 && (posMs >= durMs - END_MARGIN_MS || posMs >= durMs * WATCHED_RATIO)

    /** The position to store/offer: 0 when the viewer barely started or finished the video. */
    fun resumeFrom(posMs: Long, durMs: Long): Long = when {
        posMs < MIN_RESUME_MS -> 0
        isWatched(posMs, durMs) -> 0
        else -> posMs
    }

    /** 0..1 progress for the little bar under a thumbnail. */
    fun progress(resumeMs: Long, durMs: Long): Float = if (durMs <= 0) 0f else (resumeMs.toFloat() / durMs).coerceIn(0f, 1f)

    /** "1:23:45" or "12:34". */
    fun clock(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    }

    /** File name without extension and with separators turned into spaces, for a readable card title. */
    fun title(fileName: String): String {
        val base = fileName.substringBeforeLast('.', fileName)
        val t = base.replace('_', ' ').replace(Regex("\\s+"), " ").trim()
        return t.ifEmpty { fileName }
    }

    /** Recently added first; the caller passes each file's modification time. */
    fun <T> sortNewestFirst(items: List<T>, mtime: (T) -> Long, name: (T) -> String): List<T> =
        items.sortedWith(compareByDescending<T> { mtime(it) }.thenBy { name(it).lowercase() })
}
