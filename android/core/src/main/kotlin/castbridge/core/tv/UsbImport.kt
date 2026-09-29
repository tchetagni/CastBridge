package castbridge.core.tv

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

/** One file offered for import (from a mounted volume or from a Storage Access Framework tree). */
class ImportEntry(val name: String, val size: Long, val open: () -> InputStream)

data class ImportProgress(
    val index: Int, val count: Int, val name: String,
    val fileDone: Long, val fileTotal: Long, val bytesDone: Long, val bytesTotal: Long,
)

data class ImportResult(val copied: Int, val skipped: Int, val failed: List<String>, val cancelled: Boolean, val bytes: Long)

/**
 * Copies videos from a USB drive into the app's private folder. Only pure file logic here (testable on
 * the JVM); the Android side supplies the entries (getExternalFilesDirs scan or a DocumentsContract tree).
 */
object UsbImport {
    val VIDEO_EXT = setOf("mp4", "mkv", "avi", "mov", "m4v", "webm", "ts", "m2ts", "mts", "mpg", "mpeg", "wmv", "flv", "3gp", "ogv", "vob")

    fun isVideo(name: String) = name.substringAfterLast('.', "").lowercase() in VIDEO_EXT

    /** Makes a foreign file name acceptable to [ReceiverServer.safeName]. */
    fun sanitize(name: String): String? {
        val n = name.replace(Regex("[/\\\\\u0000]"), "_").trim().let { if (it.length > 200) it.takeLast(200) else it }
        return ReceiverServer.safeName(n)
    }

    /** Recursively lists video files under [roots] (depth-limited, symlink cycles ignored). Hidden and system folders are skipped. */
    fun scan(roots: List<File>, maxDepth: Int = 8): List<ImportEntry> {
        val out = ArrayList<ImportEntry>()
        fun walk(f: File, depth: Int) {
            if (depth > maxDepth) return
            val kids = f.listFiles() ?: return
            for (k in kids.sortedBy { it.name }) {
                if (k.name.startsWith(".")) continue
                if (k.isDirectory) walk(k, depth + 1)
                else if (k.isFile && isVideo(k.name)) out += ImportEntry(k.name, k.length()) { FileInputStream(k) }
            }
        }
        roots.filter { it.isDirectory }.forEach { walk(it, 0) }
        return out
    }

    /** Free space required before starting: everything not already present, plus [minFreeBytes]. */
    fun copyAll(
        entries: List<ImportEntry>, dir: File, minFreeBytes: Long = 100L shl 20,
        cancelled: () -> Boolean = { false }, onProgress: (ImportProgress) -> Unit = {},
    ): ImportResult {
        dir.mkdirs()
        val total = entries.sumOf { it.size }
        var done = 0L; var copied = 0; var skipped = 0
        val failed = ArrayList<String>()
        for ((i, e) in entries.withIndex()) {
            if (cancelled()) return ImportResult(copied, skipped, failed, true, done)
            val name = sanitize(e.name)
            if (name == null || e.size <= 0) { failed += e.name; continue }
            val final = File(dir, name)
            // Same name and size: already imported. Same name, different content: keep both.
            val target = if (final.isFile && final.length() == e.size) { skipped++; done += e.size; continue }
            else uniqueName(dir, name)
            try {
                synchronized(FileLocks.of(dir, target.name)) {
                    val pf = File(dir, target.name + ".part")
                    var have = if (pf.isFile && pf.length() <= e.size) pf.length() else 0L
                    if (have == 0L) pf.delete()
                    if (dir.usableSpace - (e.size - have) < minFreeBytes) throw IOException("espace insuffisant")
                    e.open().use { input ->
                        if (have > 0 && skipFully(input, have) != have) { have = 0; pf.delete() }
                        FileOutputStream(pf, true).use { out ->
                            val buf = ByteArray(256 * 1024)
                            var fileDone = have
                            while (fileDone < e.size) {
                                if (cancelled()) return ImportResult(copied, skipped, failed, true, done + fileDone)
                                val r = input.read(buf, 0, minOf(buf.size.toLong(), e.size - fileDone).toInt())
                                if (r < 0) throw IOException("fichier tronqué")
                                out.write(buf, 0, r); fileDone += r
                                onProgress(ImportProgress(i + 1, entries.size, name, fileDone, e.size, done + fileDone, total))
                            }
                        }
                    }
                    if (!pf.renameTo(target)) throw IOException("renommage impossible")
                }
                copied++; done += e.size
            } catch (ex: IOException) {
                failed += "${e.name} (${ex.message})"
                done += e.size
            }
        }
        return ImportResult(copied, skipped, failed, false, done)
    }

    private fun skipFully(input: InputStream, n: Long): Long {
        var left = n
        while (left > 0) {
            val s = input.skip(left)
            if (s <= 0) { if (input.read() < 0) break else left-- } else left -= s
        }
        return n - left
    }

    fun uniqueName(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val base = name.substringBeforeLast('.', name); val ext = name.substringAfterLast('.', "")
        var n = 1
        while (f.exists()) { f = File(dir, if (ext.isEmpty()) "$base ($n)" else "$base ($n).$ext"); n++ }
        return f
    }
}
