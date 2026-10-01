package castbridge.core.library.agent

/**
 * Library agent (docs/LIBRARY-AGENT.md): the data model shared by the reader, the rules engine, the planner and the executor.
 * Everything here is pure Kotlin (no Android, no network) so that it can be unit-tested on the JVM.
 *
 * Privacy rule of the whole package: the agent only ever handles METADATA (name, size, dates, duration, folder, extension).
 * It never reads the content of a file, except the optional partial fingerprint of duplicate candidates, which is read
 * locally and never leaves the phone (see [Fingerprinter]).
 */

/** What a file is, as far as the rules can tell. */
enum class Kind { SERIES, MOVIE, MUSIC, CLIP, COURSE, PERSONAL, PHOTO, DOCUMENT, APP, ARCHIVE, UNKNOWN }

/** Broad family of the file, from its extension. A subtitle keeps the [Kind] of the video it belongs to. */
enum class Media { VIDEO, AUDIO, IMAGE, DOC, APP, ARCHIVE, SUBTITLE, OTHER }

/** Where the library lives: the TV (flat list of files per volume) or the phone (real folders, picked with the system picker). */
enum class Origin { TV, PHONE }

/** Audio / subtitle version announced by the file name. */
enum class Audio(val tag: String) { VF("VF"), VOSTFR("VOSTFR"), VO("VO"), MULTI("MULTI") }

/** One file, as the agent sees it: metadata only. */
data class FileRef(
    val origin: Origin,
    val name: String,
    val size: Long,
    val mtime: Long = 0,
    /** Volume id (TV: "internal", "usb-…"; phone: "phone"). */
    val volumeId: String = "",
    /** Folder relative to the library root ("" = root). The TV library is flat: always "". */
    val folder: String = "",
    val durationMs: Long = 0,
    val watched: Boolean = false,
    val playedAtMs: Long = 0,
    val resumeMs: Long = 0,
    val playing: Boolean = false,
    /** Partial fingerprint (hash of head + tail), filled only for duplicate candidates. */
    val fingerprint: String? = null,
    /** Marked by the parental control (the TV said so, or a [ContentGuard] did): never renamed, moved, trashed, listed or sent. */
    val guarded: Boolean = false,
) {
    val key: String get() = "${origin.name}|$volumeId|$folder|$name"
    val loc: Loc get() = Loc(volumeId, folder, name)
    val ext: String get() = name.substringAfterLast('.', "").lowercase()
}

/** Place of a file: volume, folder (relative, "" = root) and name. */
data class Loc(val volume: String, val folder: String, val name: String)

data class VolumeInfo(
    val id: String,
    val label: String,
    /** "internal", "usb" (removable) or "phone". */
    val kind: String,
    val free: Long,
    val total: Long,
    val writable: Boolean = true,
    /** File system label ("FAT32", "exFAT", "ext4"…), "" = unknown. */
    val fs: String = "",
    val maxFileBytes: Long = Long.MAX_VALUE,
) {
    val removable: Boolean get() = kind == "usb"
    val usedRatio: Double get() = if (total <= 0 || free < 0) 0.0 else 1.0 - free.toDouble() / total
    /** FAT, exFAT and NTFS refuse `\ / : * ? " < > |`; a removable drive of unknown type gets the strict rules too. */
    val strictNames: Boolean get() = (kind == "usb" && fs.isEmpty()) || fs.equals("FAT32", true) || fs.equals("exFAT", true) || fs.equals("NTFS", true)
}

/** What the agent read: files and volumes at a given time. */
data class LibrarySnapshot(
    val origin: Origin, val files: List<FileRef>, val volumes: List<VolumeInfo>, val takenAtMs: Long = 0,
    /** A child profile is active on the TV: the agent only gives advice. */
    val childActive: Boolean = false,
    /** Files left out of [files] because the parental control protects them (only their number is kept, never their names). */
    val protectedCount: Int = 0,
    /** The TV did not say whether the parental control is on (older version): everything is treated as protected. */
    val guardUnsupported: Boolean = false,
)

/** A parental-control hook: marked content is never renamed, moved or trashed, and never sent anywhere. */
interface ContentGuard {
    fun isProtected(file: FileRef): Boolean
    /** A child profile is active: the agent only shows suggestions, it changes nothing. */
    val childProfileActive: Boolean
    companion object {
        val NONE = object : ContentGuard {
            override fun isProtected(file: FileRef) = false
            override val childProfileActive = false
        }
    }
}

/** Everything circumstantial the agent takes into account. */
data class AgentContext(
    /** Language of the interface and of the folders created ("fr" or "en"). */
    val uiLang: String = "fr",
    val nowMs: Long = System.currentTimeMillis(),
    val zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
    /** A move between volumes must leave at least that much free on the destination (the TV's own rule: 1 GB). */
    val minFreeAfterBytes: Long = 1L shl 30,
    /** Below that much free space on a volume the agent proposes to free or move something. */
    val lowSpaceBytes: Long = 2L shl 30,
    /** "Already watched a long time ago": watched and not played for that many days. */
    val watchedOldDays: Int = 90,
    val guard: ContentGuard = ContentGuard.NONE,
    /** The optional AI layer is allowed (separate, explicit consent). */
    val aiAllowed: Boolean = false,
    val habits: Habits = Habits.NONE,
    /** True when the source can hold real folders (phone); the TV library is flat. */
    val folders: Boolean = false,
    /** Year used to tell a release year from a number in a title. */
    val currentYear: Int = java.time.LocalDate.now().year,
)
