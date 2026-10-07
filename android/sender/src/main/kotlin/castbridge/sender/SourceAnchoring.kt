package castbridge.sender

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.StatFs
import android.provider.MediaStore
import castbridge.core.phone.MediaKind
import castbridge.core.tv.MediaMatch
import java.io.File
import java.io.IOException

/**
 * R-22 (Android side, thin): keeps a queued file readable after the process dies. The decisions are core's ([castbridge.core.tv.SourceAnchor],
 * [MediaMatch], [castbridge.core.tv.CacheGuard]); this object only asks the system. Order: (a) persistable grant, (b) the same file in MediaStore,
 * (c) a copy in `cacheDir/queue/<id>` made while the grant exists, (d) « à repartager ».
 *
 * Package visibility (Android 11+): resolving a `content://` URI whose provider belongs to another app (Telegram) does NOT need `<queries>` while the
 * grant exists (a URI grant makes the provider visible to the receiving app). It is only after the grant is gone that the provider is no longer found
 * (« Failed to find provider info »): `<queries>` could not give the right to read back, so none is declared for it.
 */
object SourceAnchoring {
    fun cacheDir(app: Context) = File(app.cacheDir, "queue").also { it.mkdirs() }
    fun cacheFile(app: Context, id: Long) = File(cacheDir(app), id.toString())
    fun isCacheUri(app: Context, uri: Uri) = uri.scheme == "file" && uri.path?.startsWith(File(app.cacheDir, "queue").path) == true

    /** (a) The original URI itself stays readable after a restart. False for a FileProvider ACTION_SEND grant (Telegram). */
    fun tryPersist(app: Context, uri: Uri): Boolean {
        if (uri.scheme != "content") return false
        return runCatching { app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); true }.getOrDefault(false)
    }

    fun freeBytes(app: Context): Long = runCatching { StatFs(app.cacheDir.path).availableBytes }.getOrDefault(0L)

    /** The runtime permission that lets MediaStore show files of other apps for this kind (null = none needed / none for this kind). */
    fun mediaPermission(kind: MediaKind): String? = when {
        Build.VERSION.SDK_INT >= 33 -> when (kind) {
            MediaKind.VIDEO -> Manifest.permission.READ_MEDIA_VIDEO
            MediaKind.AUDIO -> Manifest.permission.READ_MEDIA_AUDIO
            MediaKind.IMAGE -> Manifest.permission.READ_MEDIA_IMAGES
            else -> null
        }
        else -> Manifest.permission.READ_EXTERNAL_STORAGE
    }

    fun hasMediaPermission(app: Context, kind: MediaKind): Boolean {
        val p = mediaPermission(kind) ?: return false
        return app.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    }

    private fun collections(): List<Uri> = buildList {
        add(MediaStore.Video.Media.EXTERNAL_CONTENT_URI); add(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI); add(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        if (Build.VERSION.SDK_INT >= 29) add(MediaStore.Downloads.EXTERNAL_CONTENT_URI)
    }

    /** Every MediaStore row of that exact `_display_name` and `_size` (what the system lets this app see: without the permission, only its own files). */
    fun candidates(app: Context, name: String, size: Long): List<MediaMatch.Candidate> {
        if (size <= 0) return emptyList()
        val out = ArrayList<MediaMatch.Candidate>()
        for (col in collections()) runCatching {
            app.contentResolver.query(col, arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE),
                "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.SIZE}=?", arrayOf(name, size.toString()), null)?.use { c ->
                while (c.moveToNext()) out += MediaMatch.Candidate(Uri.withAppendedPath(col, c.getLong(0).toString()).toString(), c.getString(1) ?: name, c.getLong(2))
            }
        }
        return out
    }

    private fun head(app: Context, uri: Uri): ByteArray? = runCatching {
        app.contentResolver.openInputStream(uri)?.use { s ->
            val buf = ByteArray(MediaMatch.HEAD_BYTES); var n = 0
            while (n < buf.size) { val r = s.read(buf, n, buf.size - n); if (r < 0) break; n += r }
            buf.copyOf(n)
        }
    }.getOrNull()

    /** (b) At queue time: the same file in MediaStore, checked by name, size AND the first 64 KiB of both. Null = not found. */
    fun findInMediaStore(app: Context, original: Uri, name: String, size: Long): Uri? {
        val cands = candidates(app, name, size)
        if (cands.isEmpty()) return null
        val mine = head(app, original) ?: return null
        return MediaMatch.pick(cands, name, size, mine) { head(app, Uri.parse(it.uri)) }?.let { Uri.parse(it.uri) }
    }

    /** On resume: the original is gone, so name + size only, and only when ONE row matches (never a guess between two). */
    fun findAgain(app: Context, name: String, size: Long): Uri? =
        MediaMatch.unique(candidates(app, name, size), name, size)?.let { Uri.parse(it.uri) }?.takeIf { readable(app, it) }

    fun readable(app: Context, uri: Uri): Boolean = runCatching { app.contentResolver.openFileDescriptor(uri, "r")!!.use { true } }.getOrDefault(false)

    /**
     * (c) Copies [from] to `cacheDir/queue/<id>`, progress 0..100 through [onPercent] (only when it changes), stopping (and deleting the partial file) when
     * [cancelled]. Returns the file URI, null when cancelled. Throws on a read or space error (the partial file is deleted).
     */
    fun copyToCache(app: Context, id: Long, from: Uri, size: Long, cancelled: () -> Boolean, onPercent: (Int) -> Unit): Uri? {
        val out = cacheFile(app, id)
        try {
            app.contentResolver.openInputStream(from)!!.use { inp ->
                out.outputStream().use { o ->
                    val buf = ByteArray(256 * 1024); var done = 0L; var last = -1
                    while (true) {
                        if (cancelled()) { out.delete(); return null }
                        val n = inp.read(buf); if (n < 0) break
                        o.write(buf, 0, n); done += n
                        val pct = if (size > 0) (done * 100 / size).toInt().coerceAtMost(99) else 0
                        if (pct != last) { last = pct; onPercent(pct) }
                    }
                }
            }
            if (size > 0 && out.length() != size) throw IOException("copie incomplète : ${out.length()} octets sur $size")
            return Uri.fromFile(out)
        } catch (t: Throwable) { out.delete(); throw t }
    }

    /** Deletes the cache files no item needs any more (at start, after a finished or cancelled file). */
    fun purge(app: Context, referenced: Set<String>) {
        val dir = File(app.cacheDir, "queue")
        val names = dir.list()?.toList() ?: return
        castbridge.core.tv.CacheGuard.orphans(names, referenced).forEach { runCatching { File(dir, it).delete() } }
    }

    fun cacheNameOf(uri: String): String? = uri.takeIf { it.startsWith("file:") && "/queue/" in it }?.substringAfterLast('/')
}
