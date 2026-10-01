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
    /** Same, with the file when it lives in a real folder (its modification time keys the thumbnail cache). */
    fun meta(name: String, size: Long, file: File?): FileMeta = meta(name, size)
    /** Cached JPEG thumbnail, or null (not ready: the provider may start making it; clients ask again later). [file] is set for real folders. */
    fun thumb(name: String, size: Long, file: File?): ByteArray?
    /** True when no thumbnail can be made for this file (not a video, undecodable): the server answers 404 instead of 202. */
    fun thumbFailed(name: String, size: Long, file: File?): Boolean = false
    /** "Mark as watched / not watched" from a library screen. */
    fun setWatched(name: String, size: Long, watched: Boolean) {}
    /** The file was renamed or deleted through the API: saved positions follow it (or are forgotten). */
    fun renamed(from: String, to: String, size: Long) {}
    fun deleted(name: String, size: Long) {}
}

/**
 * What the parental control says about the library, for the phone's assistant: the names it must never touch, and whether a child
 * profile is active. Evaluated on the TV (which owns the configuration); the answer is put in `/api/library`.
 */
interface ContentFlags {
    fun childActive(): Boolean
    /** Names (of [items]) protected from the assistant. */
    fun protectedNames(items: List<LibraryItem>): Set<String>
}

/** Broad kind of a stored file, from its extension: the library shows videos and audio as cards, the rest under "Autres fichiers". */
enum class MediaType {
    VIDEO, AUDIO, OTHER;

    companion object {
        private val VIDEO_EXT = setOf("mp4", "m4v", "mkv", "webm", "avi", "mov", "ts", "m2ts", "mts", "mpg", "mpeg", "wmv", "flv", "3gp", "ogv", "vob", "divx", "rmvb")
        private val AUDIO_EXT = setOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "wav", "wma", "ac3", "mka")
        fun of(name: String): MediaType {
            val e = name.substringAfterLast('.', "").lowercase()
            return when (e) { in VIDEO_EXT -> VIDEO; in AUDIO_EXT -> AUDIO; else -> OTHER }
        }
    }
}

/** What library screens need to sort a file into sections (implemented by the server's items and by the phone's parsed JSON). */
interface LibraryEntry {
    val name: String
    val title: String
    val mtime: Long
    val resumeMs: Long
    val watched: Boolean
    val playedAtMs: Long
    val type: MediaType
    /** Virtual folder of the file ("" = root or not supported): the library screens add one row per folder. */
    val folder: String get() = ""
}

/** One finished file with everything the library screens show. */
data class LibraryItem(
    override val name: String,
    val size: Long,
    override val mtime: Long,
    val volumeId: String,
    val volumeLabel: String,
    val volumeKind: VolumeKind,
    val meta: FileMeta,
    val duplicate: Boolean = false,
    val playing: Boolean = false,
    /** Virtual folder ("Séries/Prison Break/Saison 01"), "" = root. The files themselves stay flat (see [FolderIndex]). */
    override val folder: String = "",
) : LibraryEntry {
    override val title: String get() = LibraryLogic.title(name)
    override val resumeMs: Long get() = meta.resumeMs
    override val watched: Boolean get() = meta.watched
    override val playedAtMs: Long get() = meta.playedAtMs
    override val type: MediaType get() = MediaType.of(name)
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

    /** Where to grab the thumbnail frame: 10 % in (past the intro), at most 5 min, at least 1 s; 5 s when the duration is unknown. */
    fun thumbTimeMs(durMs: Long): Long = if (durMs <= 0) 5_000 else (durMs / 10).coerceIn(minOf(1_000, durMs / 2), 300_000)
}

/** The sections of the library screens, from the same rules everywhere (TV, phone; the web page mirrors them). */
object LibrarySections {
    const val RESUME = "resume"
    const val RECENT = "recent"
    const val ALL = "all"
    const val OTHER = "other"
    /** Prefix of the section ids built from folders ("folder:Séries/Prison Break/Saison 01"). */
    const val FOLDER = "folder:"

    data class Section<T : LibraryEntry>(val id: String, val title: String, val items: List<T>)

    /**
     * "Reprendre" (started, not finished, last played first), "Récemment ajoutés" (the [recentCount] newest media),
     * "Toutes" (every video/audio file, by title), "Autres fichiers" (documents, APKs...). Empty sections are left out;
     * a file may appear in several sections (like on any TV home screen).
     */
    fun <T : LibraryEntry> build(items: List<T>, recentCount: Int = 12): List<Section<T>> {
        val media = items.filter { it.type != MediaType.OTHER }
        val resume = media.filter { it.resumeMs > 0 && !it.watched }.sortedWith(compareByDescending<T> { it.playedAtMs }.thenBy { it.title.lowercase() })
        val recent = LibraryLogic.sortNewestFirst(media, { it.mtime }, { it.name }).take(recentCount)
        val all = media.sortedWith(compareBy<T> { it.title.lowercase() }.thenBy { it.name })
        val other = items.filter { it.type == MediaType.OTHER }.sortedBy { it.name.lowercase() }
        // one row per folder (flat files stay in the sections above: a file is never hidden by being in a folder)
        val byFolder = items.filter { it.folder.isNotEmpty() }.groupBy { it.folder }.toSortedMap(String.CASE_INSENSITIVE_ORDER)
            .map { (f, l) -> Section(FOLDER + f, f.replace("/", " › "), l.sortedWith(compareBy<T> { it.name.lowercase() })) }
        return (listOf(
            Section(RESUME, "Reprendre", resume),
            // Only worth its own row when the library is bigger than the row itself.
            Section(RECENT, "Récemment ajoutés", if (media.size > recentCount / 2) recent else emptyList()),
            Section(ALL, "Toutes", all),
            Section(OTHER, "Autres fichiers", other),
        ) + byFolder).filter { it.items.isNotEmpty() }
    }
}
