package castbridge.receiver

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.util.Log
import castbridge.core.tv.Fs
import castbridge.core.tv.FsInfo
import castbridge.core.tv.StorageVolume
import castbridge.core.tv.UsbKeyInfo
import castbridge.core.tv.UsbSysfs
import castbridge.core.tv.StoreEntry
import castbridge.core.tv.UsbLayout
import castbridge.core.tv.VolumeKind
import castbridge.core.tv.VolumeProvider
import castbridge.core.tv.VolumeRegistry
import castbridge.core.tv.VolumeStore
import castbridge.core.tv.FileStore
import castbridge.core.tv.WriteProbe
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Volumes as Android exposes them to an app without any storage permission (scoped storage):
 *
 *  1. internal: `getExternalFilesDir("videos")` (or `filesDir/videos`), what the app always used;
 *  2. removable: the app's own folder on every *removable, mounted* secondary volume (`getExternalFilesDirs`),
 *     checked by a real write test (a "mounted" volume may still be read-only), file system read from /proc/mounts;
 *  3. SAF: a folder the user picked with ACTION_OPEN_DOCUMENT_TREE (only if one was chosen and its permission persists).
 *
 * An "adopted" drive is reported by Android as internal (not removable): it is skipped here on purpose, nothing special to do.
 * How GaiaOS presents a USB drive to `getExternalFilesDirs` is unknown: see docs/STORAGE.md.
 */
class AndroidVolumeProvider(private val ctx: Context, private val prefs: TvPrefs) : VolumeProvider {
    private class Probe(val writable: Boolean, val bps: Long, val error: String?)
    private val probes = ConcurrentHashMap<String, Probe>()
    @Volatile var saf: SafStoreImpl? = null; private set

    // the end of a file written to a removable volume asks for a best-effort system `sync` (castbridge.core.tv.ShellSync, coalesced): docs/STORAGE.md « Clé USB mal éjectée »
    override fun storeFor(volume: StorageVolume): VolumeStore =
        if (volume.kind == VolumeKind.SAF) saf ?: throw IOException("no SAF folder") else FileStore(volume, sync = castbridge.core.tv.ShellSync.shared)

    override fun scan(remeasure: Boolean): List<StorageVolume> {
        val out = ArrayList<StorageVolume>()
        val dirs: List<File?> = runCatching { ctx.getExternalFilesDirs(null).toList() }.getOrDefault(emptyList())
        val internal = (dirs.firstOrNull()?.let { File(it, "videos") } ?: File(ctx.filesDir, "videos")).also { it.mkdirs() }
        out += StorageVolume(VolumeRegistry.INTERNAL_ID, "Mémoire interne", internal, VolumeKind.INTERNAL, Fs.UNKNOWN,
            internal.usableSpace, internal.totalSpace, removable = false)

        val mounts = runCatching { FsInfo.parseMounts(File("/proc/mounts").readText()) }.getOrDefault(emptyList())
        val seen = HashSet<String>()
        // Only secondary volumes that are removable AND mounted (adopted drives count as internal storage).
        for (base in dirs.drop(1)) {
            if (base == null) continue
            val removable = runCatching { Environment.isExternalStorageRemovable(base) }.getOrDefault(false)
            // « mounted » AND « mounted read-only »: a key Android could only mount read-only (write-protected, or after an unclean removal) is still READ (the library lists it, it is never written)
            val state = runCatching { Environment.getExternalStorageState(base) }.getOrDefault(Environment.MEDIA_UNKNOWN)
            val readOnlyMount = state == Environment.MEDIA_MOUNTED_READ_ONLY
            val mounted = state == Environment.MEDIA_MOUNTED || readOnlyMount
            if (!removable || !mounted) continue
            val appDir = File(base, "videos")
            // Heavy data in <clé>/Download/CastBridge/Bibliotheque (survives the uninstall), unless the user turned it off or the drive refuses it.
            val heavy = if (prefs.getBool("heavy_on_usb", true)) UsbLayout.driveRootOf(base)?.let(UsbLayout::rootOf)?.takeIf { UsbLayout.ensure(it) } else null
            val heavyLib = heavy?.let(UsbLayout::libraryOf)
            val id = volumeId(base)
            seen += id
            var p = probes[id]
            // Speed test only off the main thread (a slow drive would freeze the TV UI), once per mount unless asked again.
            val canMeasure = Looper.myLooper() != Looper.getMainLooper()
            fun probe(d: File): Probe { val r = WriteProbe.run(d, speedBytes = if (canMeasure) PROBE_BYTES else 0); return Probe(r.writable, if (canMeasure) r.bytesPerSec else 0, r.error) }
            var dir = heavyLib ?: appDir
            if (p == null || (remeasure && canMeasure) || (p.writable && p.bps <= 0 && canMeasure)) { p = probe(dir); probes[id] = p }
            // The shared Download folder refuses writes on some Android versions: fall back to the app's own folder on the drive (not for a read-only mount: nothing is writable there, the files are read where they are).
            if (readOnlyMount && !dir.isDirectory) continue
            if (heavyLib != null && !p.writable && !readOnlyMount) {
                dir = appDir
                if (!(dir.isDirectory || dir.mkdirs())) continue
                p = probe(dir); probes[id] = p
            }
            out += StorageVolume(id, label(base), dir, VolumeKind.REMOVABLE, FsInfo.detect(mounts, base.absolutePath),
                dir.usableSpace, dir.totalSpace, true, p.writable && dir.canWrite(), p.bps, p.error,
                heavyRoot = if (dir == heavyLib) heavy else null, usb = usbOf(mounts, base))
        }
        // A drive that went away is forgotten: on return it is probed again.
        probes.keys.filter { it !in seen }.forEach { probes.remove(it) }

        scanSaf()?.let { out += it }
        return out
    }

    /** Maker, serial, negotiated USB speed and bus sharing of the drive mounted for [base] (null when unreadable or not USB). */
    private fun usbOf(mounts: List<FsInfo.Mount>, base: File): UsbKeyInfo? = runCatching {
        val m = mounts.firstOrNull { it.device.startsWith("/dev/block") && it.point.endsWith("/" + base.name) } ?: return null
        val (major, minor) = UsbSysfs.majorMinor(m.device) ?: return null
        UsbSysfs().infoForBlock(major, minor)
    }.getOrNull()

    private fun scanSaf(): StorageVolume? {
        val tree = prefs.getString("saf_tree")?.let(Uri::parse)
        if (tree == null) { saf = null; return null }
        val granted = runCatching { ctx.contentResolver.persistedUriPermissions.any { it.uri == tree && it.isWritePermission && it.isReadPermission } }.getOrDefault(false)
        if (!granted) { saf = null; return null }
        val st = saf?.takeIf { it.tree == tree } ?: SafStoreImpl(ctx, tree).also { saf = it }
        if (!st.reachable()) return null
        return st.volume
    }

    private fun label(base: File): String = runCatching {
        ctx.getSystemService(StorageManager::class.java).getStorageVolume(base)?.getDescription(ctx)
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Clé USB"

    /** Stable across remounts: the volume UUID in /storage/XXXX-XXXX/..., else a hash of the path. */
    private fun volumeId(base: File): String =
        "usb-" + (Regex("^/storage/([^/]+)/").find(base.absolutePath)?.groupValues?.get(1) ?: Integer.toHexString(base.absolutePath.hashCode()))

    companion object { const val PROBE_BYTES = 4L shl 20 }
}

/**
 * Folder chosen through the system picker, driven with ContentResolver / DocumentsContract only (no extra library).
 * Everything lives in a "CastBridge" sub-folder created inside the chosen tree: nothing else in the tree is ever read
 * or touched. Uploads there are "preload only" (no play while uploading): [progressive] is false.
 */
class SafStoreImpl(private val ctx: Context, val tree: Uri) : VolumeStore {
    private val cr = ctx.contentResolver
    @Volatile private var appDirId: String? = null

    override val volume: StorageVolume by lazy {
        StorageVolume("saf", "Dossier choisi", File("saf:"), VolumeKind.SAF, Fs.UNKNOWN, -1, 0, true, true, 0, null)
    }
    override val progressive get() = false
    override fun freeBytes() = -1L

    private class Doc(val id: String, val name: String, val size: Long, val dir: Boolean)

    private fun children(parentId: String): List<Doc> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val out = ArrayList<Doc>()
        cr.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { c ->
            while (c.moveToNext()) out += Doc(c.getString(0), c.getString(1) ?: "", if (c.isNull(2)) -1 else c.getLong(2),
                c.getString(3) == DocumentsContract.Document.MIME_TYPE_DIR)
        }
        return out
    }

    private fun docUri(id: String) = DocumentsContract.buildDocumentUriUsingTree(tree, id)

    /** The app folder inside the tree, created on first use. */
    private fun appDir(create: Boolean): String? {
        appDirId?.let { return it }
        val root = DocumentsContract.getTreeDocumentId(tree)
        children(root).firstOrNull { it.dir && it.name == APP_DIR }?.let { appDirId = it.id; return it.id }
        if (!create) return null
        val u = DocumentsContract.createDocument(cr, docUri(root), DocumentsContract.Document.MIME_TYPE_DIR, APP_DIR) ?: throw IOException("cannot create folder")
        return DocumentsContract.getDocumentId(u).also { appDirId = it }
    }

    private fun find(name: String): Doc? = try { appDir(false)?.let { d -> children(d).firstOrNull { !it.dir && it.name == name } } }
        catch (e: Exception) { null }

    override fun reachable(): Boolean = try {
        cr.query(docUri(DocumentsContract.getTreeDocumentId(tree)), arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)?.use { it.moveToFirst() } == true
    } catch (e: Exception) { false }

    override fun finalSize(name: String): Long? = find(name)?.size?.takeIf { it >= 0 }
    override fun partSize(name: String): Long = find(name + PART)?.size?.coerceAtLeast(0) ?: 0

    override fun openPart(name: String): OutputStream = wrap {
        val dir = appDir(true) ?: throw IOException("no folder")
        val existing = find(name + PART)
        val uri = existing?.let { docUri(it.id) } ?: DocumentsContract.createDocument(cr, docUri(dir), "application/octet-stream", name + PART)
            ?: throw IOException("cannot create file")
        // "wa" = append, so a resumed upload continues after what is there. Providers that ignore append are not validated (docs/STORAGE.md).
        cr.openOutputStream(uri, "wa") ?: throw IOException("cannot open for writing")
    }

    override fun commit(name: String) = wrap {
        find(name)?.let { DocumentsContract.deleteDocument(cr, docUri(it.id)) }
        val part = find(name + PART) ?: throw IOException("partial file missing")
        DocumentsContract.renameDocument(cr, docUri(part.id), name) ?: throw IOException("rename failed")
        Unit
    }

    override fun open(name: String, from: Long): InputStream = wrap {
        val d = find(name) ?: throw FileNotFoundException(name)
        val pfd = cr.openFileDescriptor(docUri(d.id), "r") ?: throw IOException("cannot open")
        object : FileInputStream(pfd.fileDescriptor) {
            init { if (from > 0) channel.position(from) }
            override fun close() { try { super.close() } finally { pfd.close() } }
        }
    }

    /** Descriptor for libVLC (`Media(libVlc, FileDescriptor)`); the caller closes it when playback is released. */
    fun openFd(name: String): ParcelFileDescriptor {
        val d = find(name) ?: throw FileNotFoundException(name)
        return cr.openFileDescriptor(docUri(d.id), "r") ?: throw IOException("cannot open")
    }

    override fun deleteFinal(name: String): Boolean = try { find(name)?.let { DocumentsContract.deleteDocument(cr, docUri(it.id)) } == true } catch (e: Exception) { false }
    override fun deletePart(name: String) { try { find(name + PART)?.let { DocumentsContract.deleteDocument(cr, docUri(it.id)) } } catch (e: Exception) { Log.w(TAG, "deletePart: ${e.javaClass.simpleName}") } }
    override fun rename(from: String, to: String): Boolean = try { find(from)?.let { DocumentsContract.renameDocument(cr, docUri(it.id), to) != null } == true } catch (e: Exception) { false }

    override fun list(): List<StoreEntry> = try {
        appDir(false)?.let { d -> children(d).filter { !it.dir && !it.name.startsWith(".") && it.size >= 0 }
            .map { if (it.name.endsWith(PART)) StoreEntry(it.name.removeSuffix(PART), it.size, true) else StoreEntry(it.name, it.size, false) } }.orEmpty()
    } catch (e: Exception) { emptyList() }

    /** ContentResolver failures (SecurityException, IllegalArgumentException, provider errors) surface as IOException. */
    private inline fun <T> wrap(f: () -> T): T = try { f() } catch (e: IOException) { throw e }
        catch (e: Exception) { throw IOException(e.message ?: e.javaClass.simpleName, e) }

    companion object { const val APP_DIR = "CastBridge"; private const val PART = ".part"; private const val TAG = "SafStore" }
}
