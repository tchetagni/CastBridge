package castbridge.core.tv

import castbridge.core.dl.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.security.MessageDigest

/**
 * Heavy data on a USB drive, in `<drive>/Download/CastBridge/` (docs/STORAGE.md § « Données lourdes sur la clé »).
 * `Download/` is public storage: it survives the uninstall of the app, unlike `Android/data/<app>/`.
 * A drive is untrusted removable media: nothing here executes, and nothing reads outside that root.
 */
object UsbLayout {
    const val DOWNLOAD = "Download"
    const val ROOT_NAME = "CastBridge"
    const val LIBRARY = "Bibliotheque"
    const val DOWNLOADS = "Telechargements"
    const val MEDIA = "Medias"
    const val INDEX = "index"
    val SUBDIRS = listOf(LIBRARY, DOWNLOADS, MEDIA, INDEX)

    fun rootOf(driveRoot: File): File = File(File(driveRoot, DOWNLOAD), ROOT_NAME)
    fun libraryOf(root: File): File = File(root, LIBRARY)

    private val APP_DIR = Regex("^(/storage/[^/]+)/Android/")

    /** `/storage/ID` from an app folder such as `/storage/ID/Android/data/<pkg>/files/videos`; null if it does not look like one. */
    fun driveRootOf(appDir: File): File? = APP_DIR.find(appDir.path)?.groupValues?.get(1)?.let(::File)

    /** Creates the root and its sub-folders. False if the drive refuses (read-only, full...). */
    fun ensure(root: File): Boolean = try {
        (root.isDirectory || root.mkdirs()) && SUBDIRS.all { val d = File(root, it); d.isDirectory || d.mkdirs() }
    } catch (e: SecurityException) { false }
}

/** Names and paths coming from a drive (or from a phone) become file operations only through this. */
object UsbPaths {
    const val MAX_DEPTH = 8
    const val MAX_PATH_CHARS = 1024
    private const val MAX_SEGMENT_BYTES = 255

    /** Extensions never taken from a drive (executables and installers): ignored, never opened by the app, never installed. */
    private val EXECUTABLE = setOf("apk", "xapk", "apks", "apkm", "exe", "msi", "bat", "cmd", "com", "scr", "sh", "so", "dex", "jar", "bin", "run", "elf", "lnk")

    fun isExecutable(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in EXECUTABLE

    /** Why [segment] cannot be one path component, or null if it can. */
    fun badSegment(segment: String): String? = when {
        segment.isEmpty() || segment == "." || segment == ".." -> "invalid name"
        segment.any { NameRules.isBad(it) } -> "forbidden character"
        segment.toByteArray(Charsets.UTF_8).size > MAX_SEGMENT_BYTES -> "name too long"
        segment.endsWith(".") || segment.endsWith(" ") -> "trailing dot or space"
        else -> null
    }

    /**
     * [rel] ("a/b/c.mp4", always relative, always '/') resolved under [root], or null if it is unsafe: absolute, `..`, backslash,
     * NUL/control characters, too long, too deep, or it would leave [root] once symbolic links are followed.
     */
    fun resolve(root: File, rel: String): File? {
        if (rel.isEmpty() || rel.length > MAX_PATH_CHARS || rel.startsWith("/") || '\\' in rel) return null
        val parts = rel.split('/')
        if (parts.size > MAX_DEPTH || parts.any { badSegment(it) != null }) return null
        val f = File(root, rel)
        return try {
            val base = root.canonicalFile.toPath()
            if (!f.canonicalFile.toPath().startsWith(base)) null else f
        } catch (e: IOException) { null }
    }

    fun isSymlink(f: File): Boolean = try { Files.isSymbolicLink(f.toPath()) } catch (e: Exception) { true }

    /** Name to store for a phone-provided [name] on a drive: exFAT/FAT rules (never raises, never returns "" ). */
    fun storedName(name: String): String = NameRules.store(name, Fs.EXFAT)
}

/** Atomic small-file write: temporary file, fsync, rename. A cut leaves the old file or the new one, never half. */
object AtomicFile {
    fun write(target: File, bytes: ByteArray) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".tmp")
        FileOutputStream(tmp).use { it.write(bytes); it.fd.sync() }
        if (!tmp.renameTo(target)) {
            target.delete()
            if (!tmp.renameTo(target)) { tmp.delete(); throw IOException("cannot replace ${target.name}") }
        }
    }
}

/**
 * `index/castbridge-store.json`: format version, id of the drive, date. Only a hint: the files are the truth, so an absent,
 * oversized or corrupt index is simply rebuilt ([UsbReadopt]). The date of a TV without a clock (1970, or before 2020) is not trusted.
 */
object StoreIndex {
    const val FILE = "castbridge-store.json"
    const val FORMAT = 1
    const val MAX_BYTES = 256 * 1024
    private const val MIN_PLAUSIBLE_MS = 1_577_836_800_000L          // 2020-01-01

    data class Data(val version: Int, val volumeId: String, val updatedMs: Long?, val files: Int)
    enum class State { PRESENT, ABSENT, CORRUPT }

    fun file(root: File) = File(File(root, UsbLayout.INDEX), FILE)

    fun plausible(ms: Long): Long? = ms.takeIf { it >= MIN_PLAUSIBLE_MS }

    fun write(root: File, volumeId: String, files: Int, nowMs: Long) {
        val map = linkedMapOf<String, Any?>("version" to FORMAT, "volumeId" to volumeId.take(64), "files" to files.toLong(),
            "updatedMs" to plausible(nowMs))
        AtomicFile.write(file(root), Json.write(map).toByteArray(Charsets.UTF_8))
    }

    fun read(root: File): Pair<State, Data?> {
        val f = file(root)
        if (!f.isFile || UsbPaths.isSymlink(f)) return State.ABSENT to null
        if (f.length() > MAX_BYTES) return State.CORRUPT to null
        return try {
            val m = Json.parse(f.readText(Charsets.UTF_8)) as? Map<*, *> ?: return State.CORRUPT to null
            val version = (m["version"] as? Number)?.toInt() ?: return State.CORRUPT to null
            if (version < 1 || version > FORMAT) return State.CORRUPT to null
            val id = (m["volumeId"] as? String)?.takeIf { it.length <= 64 } ?: return State.CORRUPT to null
            val files = (m["files"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0
            val ms = (m["updatedMs"] as? Number)?.toLong()?.let(::plausible)
            State.PRESENT to Data(version, id, ms, files)
        } catch (e: Exception) { State.CORRUPT to null }
    }
}

/**
 * `index/settings.json`: optional backup of NON-secret preferences only. Whitelist: anything else is refused, and the forbidden
 * words (PIN, tokens, SSH keys, parental code, trusted phones, parental reports, pairing tokens) are refused even if someone adds them to the list by mistake.
 */
object SafeSettings {
    const val FILE = "settings.json"
    const val MAX_BYTES = 16 * 1024
    val ALLOWED = setOf("language", "storageProfile", "quotaMb", "tiles", "heavyOnUsb")
    private val FORBIDDEN = listOf("pin", "token", "ssh", "secret", "password", "parental", "parent", "trusted", "report", "pairing", "key", "code", "phones")

    fun forbidden(key: String): Boolean = key.lowercase().let { k -> FORBIDDEN.any { it in k } }
    fun accepts(key: String): Boolean = key in ALLOWED && !forbidden(key)

    fun file(root: File) = File(File(root, UsbLayout.INDEX), FILE)

    /** Values: short strings, numbers, booleans, lists of short strings. Everything else is dropped. */
    private fun cleanValue(v: Any?): Any? = when (v) {
        is Boolean, is Int, is Long -> v
        is String -> v.takeIf { it.length <= 200 && it.none { c -> c.code < 0x20 } }
        is List<*> -> v.mapNotNull { (it as? String)?.takeIf { s -> s.length <= 64 } }.take(64)
        else -> null
    }

    /** Throws if [values] holds a key that is not allowed (a programming error that must never reach the drive). */
    fun write(root: File, values: Map<String, Any?>) {
        for (k in values.keys) require(accepts(k)) { "setting not allowed on the drive: $k" }
        val clean = LinkedHashMap<String, Any?>()
        for ((k, v) in values) cleanValue(v)?.let { clean[k] = it }
        AtomicFile.write(file(root), Json.write(clean).toByteArray(Charsets.UTF_8))
    }

    /** What the drive holds, filtered the same way (a file edited by hand or by another app cannot inject anything). */
    fun read(root: File): Map<String, Any?> {
        val f = file(root)
        if (!f.isFile || UsbPaths.isSymlink(f) || f.length() > MAX_BYTES) return emptyMap()
        return try {
            val m = Json.parse(f.readText(Charsets.UTF_8)) as? Map<*, *> ?: return emptyMap()
            val out = LinkedHashMap<String, Any?>()
            for ((k, v) in m) if (k is String && accepts(k)) cleanValue(v)?.let { out[k] = it }
            out
        } catch (e: Exception) { emptyMap() }
    }
}

/**
 * Re-adoption: at start-up and when a drive is plugged in, look at `Download/CastBridge/` ONLY (never the rest of the drive),
 * bounded in depth, entries and time, and say what is there: media, resumable partial transfers, duplicates, ignored entries.
 * Nothing is deleted, nothing executed. The library, the virtual folders and the thumbnails (a regenerable cache) are rebuilt by
 * the caller from [Result.files].
 */
object UsbReadopt {
    data class Limits(val maxDepth: Int = 6, val maxEntries: Int = 20_000, val maxMillis: Long = 5_000)
    data class Found(val path: String, val size: Long)
    data class Ignored(val path: String, val reason: String)
    data class Result(
        val files: List<Found>, val parts: List<Found>, val duplicates: List<List<String>>, val ignored: List<Ignored>,
        val truncated: Boolean, val index: StoreIndex.State, val rootMissing: Boolean = false,
    )

    private const val EDGE = 64 * 1024

    fun scan(root: File, limits: Limits = Limits(), now: () -> Long = System::currentTimeMillis): Result {
        val index = StoreIndex.read(root).first
        if (!root.isDirectory || UsbPaths.isSymlink(root)) return Result(emptyList(), emptyList(), emptyList(), emptyList(), false, index, rootMissing = true)
        val start = now()
        val files = ArrayList<Found>(); val parts = ArrayList<Found>(); val ignored = ArrayList<Ignored>()
        var entries = 0; var truncated = false

        fun walk(dir: File, rel: String, depth: Int) {
            val list = try { dir.listFiles() } catch (e: SecurityException) { null } ?: return
            for (f in list.sortedBy { it.name }) {
                if (truncated) return
                if (++entries > limits.maxEntries || now() - start > limits.maxMillis) { truncated = true; return }
                val path = if (rel.isEmpty()) f.name else "$rel/${f.name}"
                when {
                    UsbPaths.isSymlink(f) -> ignored += Ignored(path, "symbolic link")
                    rel.isEmpty() && f.name == UsbLayout.INDEX -> {}                       // our own index: read separately
                    f.name.startsWith(".") && !f.name.endsWith(Storage.PART) -> {}         // hidden/metadata (.played, probes)
                    UsbPaths.badSegment(f.name) != null -> ignored += Ignored(path, UsbPaths.badSegment(f.name)!!)
                    f.isDirectory -> if (depth + 1 > limits.maxDepth) ignored += Ignored(path, "too deep") else walk(f, path, depth + 1)
                    f.isFile -> when {
                        UsbPaths.isExecutable(f.name) -> ignored += Ignored(path, "executable ignored")
                        f.name.endsWith(Meta.SUFFIX) || f.name.endsWith(".aria2") -> {}
                        f.name.endsWith(Storage.PART) -> parts += Found(path, f.length())
                        else -> files += Found(path, f.length())
                    }
                }
            }
        }
        walk(root, "", 1)

        val dups = files.groupBy { it.size }.filter { it.key > 0 && it.value.size > 1 }.values.flatMap { group ->
            group.groupBy { edgeHash(File(root, it.path)) }.filterKeys { it != null }.values.filter { it.size > 1 }.map { g -> g.map { it.path } }
        }
        return Result(files, parts, dups, ignored, truncated, index)
    }

    private fun edgeHash(f: File): String? = try {
        val md = MessageDigest.getInstance("SHA-256")
        java.io.RandomAccessFile(f, "r").use { r ->
            val buf = ByteArray(EDGE)
            var n = r.read(buf); if (n > 0) md.update(buf, 0, n)
            if (r.length() > 2L * EDGE) { r.seek(r.length() - EDGE); n = r.read(buf); if (n > 0) md.update(buf, 0, n) }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    } catch (e: IOException) { null }

    /** Rewrites the index after a scan (atomic). Best effort: a read-only drive just keeps the old one. */
    fun refreshIndex(root: File, volumeId: String, r: Result, nowMs: Long): Boolean =
        try { StoreIndex.write(root, volumeId, r.files.size, nowMs); true } catch (e: IOException) { false }
}

/** Where heavy content (videos, downloads, media batches) goes, and what to tell the user. Pure. */
object HeavyStorage {
    enum class Where { USB, INTERNAL }
    data class Decision(val where: Where, val volume: StorageVolume?, val warnings: List<String>)

    /**
     * [enabled] = « Contenus lourds sur la clé USB » (yes by default when a drive is there). [preferredId] = the drive chosen by the user
     * among several ("" = automatic: the one with the most free space). A drive qualifies if it is writable and big enough for the next
     * [need] bytes (0 = do not check): a drive that cannot be written is never chosen, with the reason in the warnings.
     */
    fun decide(volumes: List<StorageVolume>, enabled: Boolean, preferredId: String = "", need: Long = 0): Decision {
        val internal = volumes.firstOrNull { it.kind == VolumeKind.INTERNAL }
        if (!enabled) return Decision(Where.INTERNAL, internal, emptyList())
        val drives = volumes.filter { it.kind == VolumeKind.REMOVABLE }
        val warnings = ArrayList<String>()
        val ok = drives.filter { v ->
            when {
                !v.writable -> { warnings += "${v.label} : lecture seule, ignorée"; false }
                v.free in 0 until need -> { warnings += "${v.label} : pas assez de place"; false }
                else -> true
            }
        }
        val pick = ok.firstOrNull { it.id == preferredId } ?: ok.maxByOrNull { it.free }
        if (pick == null) {
            warnings += if (drives.isEmpty()) "Aucune clé USB : les contenus lourds restent sur la mémoire interne, qui est petite."
                        else "Aucune clé USB utilisable : les contenus lourds restent sur la mémoire interne, qui est petite."
            return Decision(Where.INTERNAL, internal, warnings)
        }
        if (preferredId.isNotEmpty() && pick.id != preferredId) warnings += "La clé choisie est absente ou inutilisable : ${pick.label} est utilisée."
        if (pick.writeBps in 1 until StoragePolicy.SLOW_BPS) warnings += "${pick.label} est lente (${pick.writeBps / 1000} ko/s en écriture) : les envois seront longs, la lecture peut saccader."
        if (pick.fs == Fs.FAT32) warnings += "${pick.label} est en FAT32 : fichiers de 4 Go maximum."
        return Decision(Where.USB, pick, warnings)
    }
}
