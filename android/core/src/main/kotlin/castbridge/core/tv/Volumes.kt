package castbridge.core.tv

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** Where a storage folder lives. SAF = a folder chosen by the user through the system picker (no java.io.File access). */
enum class VolumeKind { INTERNAL, REMOVABLE, SAF }

/**
 * File-system family of a volume, as read from /proc/mounts. Only what matters to us: the biggest file it can hold,
 * which characters its names may contain, and whether names are case-insensitive.
 */
enum class Fs(val label: String, val maxFileBytes: Long, val restrictiveNames: Boolean, val caseInsensitive: Boolean) {
    FAT32("FAT32", (4L shl 30) - 1, true, true),
    EXFAT("exFAT", Long.MAX_VALUE, true, true),
    NTFS("NTFS", Long.MAX_VALUE, true, true),
    EXT4("ext4", Long.MAX_VALUE, false, false),
    F2FS("f2fs", Long.MAX_VALUE, false, false),
    /** Not determined (FUSE wrapper, unreadable /proc/mounts...). Treated as unrestricted, but flagged. */
    UNKNOWN("?", Long.MAX_VALUE, false, false),
}

/** A place where videos can be stored. [free]/[writable]/[writeBps] are a snapshot: the registry refreshes them. */
data class StorageVolume(
    val id: String,
    val label: String,
    val dir: File,
    val kind: VolumeKind,
    val fs: Fs = Fs.UNKNOWN,
    /** Free bytes, -1 = unknown (SAF). */
    val free: Long = 0,
    val total: Long = 0,
    val removable: Boolean = kind != VolumeKind.INTERNAL,
    val writable: Boolean = true,
    /** Measured sequential write speed (bytes/s, fsync included), 0 = not measured. */
    val writeBps: Long = 0,
    val note: String? = null,
    /** Who made the drive, the USB speed negotiated, whether it shares its bus with the TV's Wi-Fi (null = unknown / not USB). */
    val usb: UsbKeyInfo? = null,
    /** `<clé>/Download/CastBridge/` when [dir] lives there (data that outlives the app), else null (app-private folder). */
    val heavyRoot: File? = null,
) {
    /** The internal volume is never limited by what we detect (the detection is only meaningful for removable media). */
    val maxFileBytes: Long get() = if (kind == VolumeKind.INTERNAL) Long.MAX_VALUE else fs.maxFileBytes
    val fsKnown: Boolean get() = kind == VolumeKind.INTERNAL || fs != Fs.UNKNOWN
    /** Stored name for a client-provided [name] on this volume (see [NameRules]); an undetected file system gets the strict rules. */
    fun storedName(name: String): String = NameRules.store(name, when { kind == VolumeKind.INTERNAL -> Fs.EXT4; fs == Fs.UNKNOWN -> Fs.EXFAT; else -> fs })
}

// ---------------------------------------------------------------------------------------------------------------
// File-system detection
// ---------------------------------------------------------------------------------------------------------------

object FsInfo {
    data class Mount(val device: String, val point: String, val type: String)

    fun parseMounts(text: String): List<Mount> = text.lineSequence().mapNotNull { l ->
        val p = l.trim().split(Regex("\\s+"))
        if (p.size >= 3) Mount(p[0], p[1].replace("\\040", " "), p[2].lowercase()) else null
    }.toList()

    private val WRAPPERS = setOf("fuse", "sdcardfs", "esdfs", "wrapfs", "fuse.sdcard", "fuseblk", "overlay")

    fun fromType(t: String): Fs? = when (t.lowercase()) {
        "vfat", "msdos", "fat", "fat32" -> Fs.FAT32
        "exfat" -> Fs.EXFAT
        "ntfs", "ntfs3", "tntfs", "ufsd", "ntfs-3g" -> Fs.NTFS
        "ext2", "ext3", "ext4" -> Fs.EXT4
        "f2fs" -> Fs.F2FS
        else -> null        // includes sdfat (FAT or exFAT, cannot tell) and fuseblk (NTFS-3G or exFAT-fuse)
    }

    /**
     * File system holding [path]. On Android the visible mount (/storage/XXXX-XXXX) is usually a FUSE or sdcardfs
     * wrapper: the real type is then looked up on the other mount points that carry the same volume id
     * (/mnt/media_rw/XXXX-XXXX, /mnt/runtime/...). Returns UNKNOWN whenever this cannot be established.
     */
    fun detect(mounts: List<Mount>, path: String): Fs {
        val p = path.trimEnd('/')
        val own = mounts.filter { p == it.point || p.startsWith(it.point.trimEnd('/') + "/") }.maxByOrNull { it.point.length } ?: return Fs.UNKNOWN
        fromType(own.type)?.let { return it }
        if (own.type !in WRAPPERS) return Fs.UNKNOWN
        val id = Regex("^/(?:storage|mnt/media_rw|mnt/runtime/[a-z]+|mnt/expand)/([^/]+)").find(p)?.groupValues?.get(1) ?: return Fs.UNKNOWN
        if (id == "emulated" || id == "self") return Fs.UNKNOWN
        val real = mounts.filter { it.point.trimEnd('/').endsWith("/$id") }.mapNotNull { fromType(it.type) }
        return real.firstOrNull() ?: Fs.UNKNOWN
    }

    fun detect(path: String, mountsFile: File = File("/proc/mounts")): Fs =
        runCatching { detect(parseMounts(mountsFile.readText()), path) }.getOrDefault(Fs.UNKNOWN)
}

// ---------------------------------------------------------------------------------------------------------------
// File names
// ---------------------------------------------------------------------------------------------------------------

/**
 * Names on the volumes: [ReceiverServer.safeName] still guards what the client may send; this maps an accepted name to
 * one the file system can store. FAT32/exFAT/NTFS refuse `\ / : * ? " < > |` and control characters, and cannot end with
 * a dot or a space; every file system (ext4 included) limits a name to 255 *bytes* and we append ".part" / ".meta".
 *
 * A name that had to be changed gets a stable "~xxxxxx" suffix (hash of the original) before its extension, so that
 * "a:b.mp4" and "a_b.mp4" stay two different files, the same client name always maps to the same stored name, and a
 * stored name maps to itself (idempotent: the phone can also ask for it by its stored name, as shown in listings).
 */
object NameRules {
    private const val MAX_BYTES = 255
    private const val RESERVE = 5           // ".part" is the longest suffix we append
    private const val BAD = "\\/:*?\"<>|"

    fun isBad(c: Char) = c in BAD || c.code < 0x20 || c.code == 0x7f

    fun store(name: String, fs: Fs): String {
        var n = name
        if (fs.restrictiveNames) {
            n = n.map { if (isBad(it)) '_' else it }.joinToString("").trimEnd('.', ' ')
            if (n.isEmpty()) n = "_"
        }
        val limit = MAX_BYTES - RESERVE
        if (n == name && n.toByteArray(Charsets.UTF_8).size <= limit) return n
        val tag = "~" + hash(name)
        val dot = n.lastIndexOf('.')
        val ext = if (dot > 0 && n.length - dot <= 12) n.substring(dot) else ""
        var base = n.removeSuffix(ext)
        while ((base + tag + ext).toByteArray(Charsets.UTF_8).size > limit && base.isNotEmpty())
            base = base.substring(0, base.offsetByCodePoints(base.length, -1))
        return base + tag + ext
    }

    /** True if [name] would be stored unchanged. */
    fun isStable(name: String, fs: Fs) = store(name, fs) == name

    private fun hash(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray(Charsets.UTF_8)).take(3).joinToString("") { "%02x".format(it) }
}

// ---------------------------------------------------------------------------------------------------------------
// Write probe (writability + throughput)
// ---------------------------------------------------------------------------------------------------------------

data class ProbeResult(val writable: Boolean, val bytesPerSec: Long = 0, val error: String? = null)

/**
 * Real write test, since Android's "mounted" state says nothing about a read-only or write-protected drive: creates a
 * small file in [dir], writes, fsyncs, reads its size back and deletes it. With [speedBytes] > 0 it also times a
 * sequential write of that many bytes (fsync included, so the page cache does not flatter the drive). Touches only its own
 * temporary file.
 */
object WriteProbe {
    fun run(dir: File, speedBytes: Long = 0, now: () -> Long = System::nanoTime): ProbeResult {
        val f = File(dir, ".cb-probe-${System.nanoTime()}")
        try {
            if (!dir.isDirectory && !dir.mkdirs()) return ProbeResult(false, error = "folder missing")
            FileOutputStream(f).use { o -> o.write(ByteArray(4096) { it.toByte() }); o.fd.sync() }
            if (f.length() != 4096L) return ProbeResult(false, error = "size mismatch after write")
            var bps = 0L
            if (speedBytes > 0 && dir.usableSpace > speedBytes + (16L shl 20)) {
                val buf = ByteArray(64 * 1024) { (it * 31).toByte() }
                val t0 = now()
                FileOutputStream(f).use { o ->
                    var left = speedBytes
                    while (left > 0) { val n = minOf(left, buf.size.toLong()).toInt(); o.write(buf, 0, n); left -= n }
                    o.fd.sync()
                }
                val dt = (now() - t0).coerceAtLeast(1_000_000)
                bps = speedBytes * 1_000_000_000L / dt
            }
            return ProbeResult(true, bps)
        } catch (e: IOException) {
            return ProbeResult(false, error = e.message ?: e.javaClass.simpleName)
        } catch (e: SecurityException) {
            return ProbeResult(false, error = "denied")
        } finally { runCatching { f.delete() } }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Volume store: what the server needs from a volume (java.io.File for real folders, ContentResolver for SAF)
// ---------------------------------------------------------------------------------------------------------------

/** [folder] = the real sub-folder of a filed file ("" = the folder of the volume); [origin] = the name the phone sent it under, when the file was renamed on reception. */
data class StoreEntry(val name: String, val size: Long, val part: Boolean, val folder: String = "", val origin: String? = null)

interface VolumeStore {
    val volume: StorageVolume
    /** Free bytes, -1 = unknown. */
    fun freeBytes(): Long
    fun finalSize(name: String): Long?
    /** Size of the partial upload (0 if none). */
    fun partSize(name: String): Long
    /** Appends to the partial upload. */
    fun openPart(name: String): OutputStream
    /** Turns the partial upload into the final file (replacing an older one) and makes sure it reached the medium. */
    fun commit(name: String)
    /**
     * Forces the bytes of the partial upload [name] onto the medium (fsync) and returns true only when that is certain. A store that cannot
     * guarantee it answers false (the default): the Mover then refuses to delete its source.
     */
    fun syncPart(name: String): Boolean = false
    fun open(name: String, from: Long = 0): InputStream
    fun deleteFinal(name: String): Boolean
    fun deletePart(name: String)
    fun rename(from: String, to: String): Boolean
    fun list(): List<StoreEntry>
    /** True for real folders: a growing file there can be read while it is written (play during upload). */
    val progressive: Boolean
    /** False when the medium behind this store is gone (asked after a write error, to tell "drive pulled" from other failures). */
    fun reachable(): Boolean = true
}

/** [VolumeStore] over a plain folder (internal storage or a removable volume's app folder). */
open class FileStore(override val volume: StorageVolume, private val free: () -> Long = { volume.dir.usableSpace }) : VolumeStore {
    val dir: File get() = volume.dir
    override val progressive get() = true

    /** The name as it exists on disk (FAT/exFAT/NTFS look names up case-insensitively). */
    fun diskName(name: String): String {
        if (!volume.fs.caseInsensitive || volume.kind == VolumeKind.INTERNAL) return name
        val l = dir.list() ?: return name
        l.firstOrNull { it.equals(name, ignoreCase = true) }?.let { return it }
        l.firstOrNull { it.equals(name + Storage.PART, ignoreCase = true) }?.let { return it.dropLast(Storage.PART.length) }
        return name
    }
    /** Real sub-folders of the received files (docs/STORAGE.md, « Rangement à la réception »): which folder holds each file, and under which name the phone sent it. */
    val filing: FiledIndex by lazy { FiledIndex(dir) }

    /**
     * The file called [name]: flat in the volume folder, or filed in a category folder (the name is the key, unique on the TV). A name that is not
     * there is the flat path (where a new file would go).
     */
    fun fileOf(name: String): File {
        val flat = File(dir, diskName(name))
        if (flat.isFile) return flat
        filing.locate(name)?.let { return File(dir, it) }
        return flat
    }

    private fun f(name: String) = fileOf(name)
    private fun p(name: String) = File(dir, diskName(name) + Storage.PART)

    override fun freeBytes() = free()
    override fun reachable() = dir.isDirectory
    override fun finalSize(name: String): Long? = f(name).takeIf { it.isFile }?.length()
    override fun partSize(name: String): Long = p(name).takeIf { it.isFile }?.length() ?: 0
    override fun openPart(name: String): OutputStream = FileOutputStream(File(dir, diskName(name) + Storage.PART), true)
    override fun syncPart(name: String): Boolean =
        runCatching { RandomAccessFile(File(dir, diskName(name) + Storage.PART), "rw").use { it.fd.sync() }; true }.getOrDefault(false)
    override fun commit(name: String) {
        val part = File(dir, diskName(name) + Storage.PART)
        RandomAccessFile(part, "rw").use { it.fd.sync() }   // on every kind of volume: the data must be on the medium before the rename (and, for a move, before the source goes)
        val fin = File(dir, diskName(name))
        if (fin.exists()) fin.delete()
        if (!part.renameTo(fin)) throw IOException("rename failed")
    }

    /**
     * Files the finished flat file [name] into [folder] ("Films", "Séries/Titre/Saison 01") under the clean name [finalName]: one atomic rename on
     * the same volume, never over an existing file. The index entry is written first (see [FiledIndex]). False = nothing changed, the file stays flat and valid.
     */
    fun fileInto(name: String, folder: String, finalName: String, origin: String?): Boolean = synchronized(filing) {
        val src = File(dir, diskName(name))
        if (!src.isFile) return@synchronized false
        val rel = if (folder.isEmpty()) finalName else "$folder/$finalName"
        val target = UsbPaths.resolve(dir, rel) ?: return@synchronized false
        if (target.exists()) return@synchronized false
        if (filing.locate(finalName) != null) return@synchronized false
        target.parentFile?.let { if (!it.isDirectory && !it.mkdirs()) return@synchronized false }
        filing.put(finalName, rel, origin)
        if (!src.renameTo(target)) { filing.forget(finalName); return@synchronized false }
        if (volume.kind != VolumeKind.INTERNAL) runCatching { java.io.FileInputStream(target).use { it.fd.sync() } }
        true
    }

    override fun open(name: String, from: Long): InputStream = java.io.FileInputStream(f(name)).also { if (from > 0) it.channel.position(from) }
    override fun deleteFinal(name: String): Boolean = f(name).delete().also { if (it && filing.isFiled(name)) filing.forget(name) }
    override fun deletePart(name: String) { p(name).delete() }
    /**
     * Renames in place and never replaces anything (a POSIX rename silently overwrites an existing target: two renames to the same
     * name would lose a file). On a case-insensitive volume a change of case only ("film.mkv" -> "Film.mkv") is the same entry: allowed.
     * A filed file stays in its folder. On a removable drive the renamed entry is synced to the medium before returning (a drive is often pulled right after).
     */
    override fun rename(from: String, to: String): Boolean = synchronized(filing) {
        val src = f(from)
        val dst = File(src.parentFile ?: dir, to)
        if (!src.isFile) return@synchronized false
        val caseOnly = volume.fs.caseInsensitive && src.name.equals(to, ignoreCase = true) && src.name != to
        if (dst.exists() && !caseOnly) return@synchronized false
        if (!from.equals(to, ignoreCase = true) && filing.isFiled(to)) return@synchronized false          // one name space across the folders
        val oldRel = filing.locate(from).takeIf { src.parentFile?.canonicalFile != dir.canonicalFile }
        val newRel = oldRel?.let { r -> r.substringBeforeLast('/', "").let { d -> if (d.isEmpty()) to else "$d/$to" } }
        if (oldRel != null && newRel != null) filing.renamed(from, to, newRel)
        if (!src.renameTo(dst)) { if (oldRel != null) filing.renamed(to, from, oldRel); return@synchronized false }
        if (volume.kind != VolumeKind.INTERNAL) runCatching { java.io.FileInputStream(dst).use { it.fd.sync() } }
        true
    }
    override fun list(): List<StoreEntry> {
        val flat = dir.listFiles().orEmpty()
            .filter { it.isFile && !it.name.startsWith(".") && !it.name.endsWith(Meta.SUFFIX) }
            .map { if (it.name.endsWith(Storage.PART)) StoreEntry(it.name.removeSuffix(Storage.PART), it.length(), true) else StoreEntry(it.name, it.length(), false) }
        val entries = filing.entries()
        if (entries.isEmpty()) return flat
        val origins = filing.origins()
        val names = flat.mapTo(HashSet()) { it.name.lowercase() }
        val filed = entries.mapNotNull { (k, r) ->
            if (k.lowercase() in names) return@mapNotNull null
            File(dir, r).takeIf { it.isFile }?.let { StoreEntry(k, it.length(), false, r.substringBeforeLast('/', ""), origins[k.lowercase()]) }
        }
        return flat + filed
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Registry of volumes (hot plug aware)
// ---------------------------------------------------------------------------------------------------------------

/** Platform side: enumerates the volumes currently usable. The Android implementation lives in :receiver. */
interface VolumeProvider {
    /** [remeasure] forces a new write-speed measurement of every volume (else measured once per mount). */
    fun scan(remeasure: Boolean = false): List<StorageVolume>
    fun storeFor(volume: StorageVolume): VolumeStore = FileStore(volume)
}

class StaticVolumes(private val list: () -> List<StorageVolume>) : VolumeProvider {
    override fun scan(remeasure: Boolean) = list()
}

data class VolumeEvent(val volume: StorageVolume, val present: Boolean)

/**
 * The volumes the server may use right now, plus what it must remember about the ones that are gone: a removed drive
 * holding a partial upload must not make the same upload silently restart elsewhere (that would leave two partial
 * copies once the drive comes back), so uploads of such a name are answered "volume removed" until it returns.
 */
class VolumeRegistry(
    private val provider: VolumeProvider,
    /** Live free space of a volume (overridable in tests). -1 = unknown. */
    private val freeSpace: (StorageVolume) -> Long = { -2 },
) {
    private class Gone(val volume: StorageVolume, val parts: Set<String>)

    @Volatile private var current: List<StorageVolume> = emptyList()
    private val gone = ConcurrentHashMap<String, Gone>()
    private val parts = ConcurrentHashMap<String, MutableSet<String>>()     // volume id -> partial uploads last seen there
    private val stores = ConcurrentHashMap<String, VolumeStore>()
    private val listeners = CopyOnWriteArrayList<(VolumeEvent) -> Unit>()

    fun addListener(l: (VolumeEvent) -> Unit) { listeners += l }

    fun volumes(): List<StorageVolume> = current
    operator fun get(id: String): StorageVolume? = current.firstOrNull { it.id == id }
    fun alive(v: StorageVolume): Boolean = current.any { it.id == v.id } && (v.kind == VolumeKind.SAF || v.dir.isDirectory)
    fun store(v: StorageVolume): VolumeStore = stores.getOrPut(v.id) { provider.storeFor(v) }
    fun free(v: StorageVolume): Long = freeSpace(v).let { if (it == -2L) store(v).freeBytes() else it }

    /** Volumes with their live free space, for policy decisions. */
    fun snapshot(): List<StorageVolume> = current.map { it.copy(free = free(it)) }

    @Synchronized fun refresh(remeasure: Boolean = false): List<StorageVolume> {
        val now = provider.scan(remeasure)
        val old = current
        val ids = now.map { it.id }.toSet()
        // Anything that vanished: remember its partial uploads.
        for (v in old) if (v.id !in ids) rememberGone(v)
        for (v in now) {
            val prev = old.firstOrNull { it.id == v.id }
            if (prev == null || prev.dir != v.dir || prev.fs != v.fs || prev.kind != v.kind) stores.remove(v.id)
        }
        current = now
        for (v in now) {
            if (gone.remove(v.id) != null) listeners.forEach { it(VolumeEvent(v, true)) }
            else if (old.none { it.id == v.id } && old.isNotEmpty()) listeners.forEach { it(VolumeEvent(v, true)) }
            if (v.kind != VolumeKind.SAF) runCatching { parts[v.id] = store(v).list().filter { it.part }.map { it.name }.toMutableSet() }
        }
        old.filter { it.id !in ids }.forEach { v -> listeners.forEach { it(VolumeEvent(v, false)) } }
        return now
    }

    /** Immediate removal (system "unmounted/eject" broadcast, or an I/O error proving the medium is gone). */
    @Synchronized fun markRemoved(id: String) {
        val v = current.firstOrNull { it.id == id } ?: return
        rememberGone(v)
        current = current.filter { it.id != id }
        stores.remove(id)
        listeners.forEach { it(VolumeEvent(v, false)) }
    }

    private fun rememberGone(v: StorageVolume) { gone[v.id] = Gone(v, parts[v.id]?.toSet().orEmpty()) }

    /** Called when a partial upload starts on [v], so that its loss is remembered even before the next refresh. */
    fun notePart(v: StorageVolume, name: String) { parts.getOrPut(v.id) { java.util.concurrent.ConcurrentHashMap.newKeySet() }.add(name) }
    fun forgetPart(v: StorageVolume, name: String) { parts[v.id]?.remove(name) }

    /** Volume (currently absent) that held a partial upload of [name] (compared by stored name), if any. */
    fun missingOwner(name: String): StorageVolume? = gone.values.firstOrNull { g ->
        val stored = g.volume.storedName(name)
        g.parts.any { it.equals(stored, ignoreCase = g.volume.fs.caseInsensitive) }
    }?.volume

    fun forgetMissing(name: String) {
        gone.values.forEach { g ->
            val stored = g.volume.storedName(name)
            if (g.parts.any { it.equals(stored, ignoreCase = true) })
                gone[g.volume.id] = Gone(g.volume, g.parts.filterNot { it.equals(stored, ignoreCase = true) }.toSet())
        }
    }

    fun missingVolumes(): List<StorageVolume> = gone.values.map { it.volume }

    companion object {
        const val INTERNAL_ID = "internal"

        /** A registry with one internal folder (tests, and the single-folder constructor of [ReceiverServer]). */
        fun single(dir: File): VolumeRegistry {
            dir.mkdirs()
            return VolumeRegistry(StaticVolumes {
                listOf(StorageVolume(INTERNAL_ID, "Mémoire interne", dir, VolumeKind.INTERNAL, Fs.UNKNOWN, dir.usableSpace, dir.totalSpace, false))
            }).also { it.refresh() }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Target selection policy
// ---------------------------------------------------------------------------------------------------------------

/**
 * Chooses where a new upload goes. Pure function of the volumes' state, so it is fully unit-tested.
 *
 * Target setting: "auto" (a removable volume when it can take the file, else internal), "internal", or a volume id.
 * Hard rules: not writable, file bigger than the file system's limit (FAT32: 4 GiB - 1 byte), not enough free space.
 * In "auto", a removable drive that measured slower than [SLOW_BPS] or whose file system is unknown for a > 4 GiB file is
 * only used if internal storage cannot take the file.
 */
object StoragePolicy {
    const val AUTO = "auto"
    const val INTERNAL = "internal"
    const val SLOW_BPS = 1_500_000L
    /** Below this a video cannot be played while it uploads unless its bitrate is low (used when the duration is unknown). */
    const val COMFORT_BPS = 2_500_000L
    const val FOUR_GIB = 4L shl 30

    data class Skipped(val volumeId: String, val reason: String)
    data class Candidate(val volume: StorageVolume, val warnings: List<String>)
    data class Refusal(val http: Int, val message: String)
    data class Plan(val candidates: List<Candidate>, val refusal: Refusal?, val skipped: List<Skipped>)

    fun isValidTarget(v: String, volumes: List<StorageVolume>) =
        v == AUTO || v == INTERNAL || volumes.any { it.id == v && it.kind != VolumeKind.INTERNAL }

    /** [size] = final size of the file; [durMs] > 0 lets us judge the drive's speed against the video bitrate. */
    fun plan(volumes: List<StorageVolume>, target: String, size: Long, minFree: Long, durMs: Long = 0,
             /** Bytes the server could free on a volume by evicting played files (0 when eviction is off). */
             reclaimable: (StorageVolume) -> Long = { 0 }): Plan {
        val skipped = ArrayList<Skipped>()
        fun reject(v: StorageVolume): String? = when {
            !v.writable -> "not writable" + (v.note?.let { " ($it)" } ?: "")
            size > v.maxFileBytes -> "file too large for ${v.fs.label} (max ${v.maxFileBytes shr 20} MiB, i.e. 4 GB - 1 byte)"
            v.free >= 0 && v.free + reclaimable(v) - size < minFree -> "not enough space"
            else -> null
        }
        fun warn(v: StorageVolume): List<String> = buildList {
            if (!v.fsKnown && size > FOUR_GIB - 1) add("file system unknown: a file over 4 GB may fail on FAT32")
            if (v.kind == VolumeKind.SAF) add("no play-while-uploading on a folder chosen through the system picker")
            if (v.writeBps > 0 && v.kind != VolumeKind.SAF) {
                val bitrate = if (durMs > 0) size * 1000 / durMs else 0
                val need = if (bitrate > 0) bitrate * 3 / 2 else COMFORT_BPS
                if (v.writeBps < need) add("slow drive (${v.writeBps / 1000} kB/s written): playing during the upload may stall")
            }
        }

        val internal = volumes.filter { it.kind == VolumeKind.INTERNAL }
        val removable = volumes.filter { it.kind == VolumeKind.REMOVABLE }
        val saf = volumes.filter { it.kind == VolumeKind.SAF }
        val pool: List<StorageVolume> = when (target) {
            AUTO -> removable.sortedByDescending { it.free } + saf + internal
            INTERNAL -> internal
            else -> {
                val v = volumes.firstOrNull { it.id == target }
                    ?: return Plan(emptyList(), Refusal(503, "volume unavailable"), emptyList())
                listOf(v)
            }
        }
        val ok = ArrayList<Candidate>()
        for (v in pool) {
            val why = reject(v)
            if (why != null) { skipped += Skipped(v.id, why); continue }
            ok += Candidate(v, warn(v))
        }
        if (target == AUTO) {
            // Demote what is risky: slow drives and unknown file systems with a huge file.
            val risky = { c: Candidate -> c.volume.kind == VolumeKind.REMOVABLE &&
                ((c.volume.writeBps in 1 until SLOW_BPS) || (!c.volume.fsKnown && size > FOUR_GIB - 1)) }
            val sorted = ok.filterNot(risky) + ok.filter(risky)
            ok.clear(); ok += sorted
        }
        if (ok.isNotEmpty()) return Plan(ok, null, skipped)
        val refusal = when {
            skipped.any { it.reason.startsWith("file too large") } ->      // the most actionable answer: reformat, or use internal
                Refusal(413, skipped.first { it.reason.startsWith("file too large") }.reason)
            skipped.isNotEmpty() && skipped.all { it.reason.startsWith("not writable") } -> Refusal(503, skipped.first().reason)
            skipped.any { it.reason == "not enough space" } -> Refusal(507, "not enough space")
            else -> Refusal(507, "no storage available")
        }
        return Plan(emptyList(), refusal, skipped)
    }
}

/** One-line summary for the TV's waiting screen, e.g. "Stockage : Clé USB (exFAT) 28 Go libres  |  Mémoire interne 3.1 Go libres". */
object StorageLine {
    fun size(b: Long): String = when {
        b >= 10L shl 30 -> "${b shr 30} Go"
        b >= 1L shl 30 -> String.format(java.util.Locale.ROOT, "%.1f Go", b / (1L shl 30).toDouble())
        else -> "${b shr 20} Mo"
    }

    /** The volume the next upload would go to comes first. */
    fun render(volumes: List<StorageVolume>, primaryId: String?, absent: List<StorageVolume> = emptyList()): String {
        if (volumes.isEmpty()) return "Stockage : aucun volume utilisable"
        val ordered = volumes.sortedBy { if (it.id == primaryId) 0 else 1 }
        val parts = ordered.map { v ->
            val what = if (v.kind == VolumeKind.INTERNAL) v.label else "${v.label}${if (v.fs != Fs.UNKNOWN) " (${v.fs.label})" else ""}"
            when {
                !v.writable -> "$what : lecture seule, ignorée"
                v.free >= 0 -> "$what ${size(v.free)} libres"
                else -> what
            }
        } + absent.map { "${it.label} : retirée" }
        return "Stockage : " + parts.joinToString("  |  ")
    }
}
