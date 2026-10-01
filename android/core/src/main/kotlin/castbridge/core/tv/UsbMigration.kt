package castbridge.core.tv

import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest

/**
 * « Déplacer les contenus vers la clé » (internal storage -> `Download/CastBridge/Bibliotheque`) and its reverse « Rapatrier ».
 * Both are the same operation between two folders:
 *  1. each finished file is copied to `<name>.part` in the target (resumes from the `.part` already there, synced to the medium);
 *  2. the copy is verified in full (size AND SHA-256, source against copy) before it takes its final name;
 *  3. the source is deleted only if [Options.deleteSources] is true, i.e. the user confirmed; otherwise it is kept (and the copy too).
 * Nothing is ever overwritten: a different file already at the target is a conflict, reported and skipped.
 * A cut at any step leaves the source intact (it is deleted last, after the verified rename) and the next run finishes the job.
 */
class UsbMigration(
    private val from: File,
    private val to: File,
    private val toFs: Fs,
    private val options: Options = Options(),
    /** Test hook: called with "step:name" before every step; may throw to simulate a cut. */
    private val fault: (String) -> Unit = {},
) {
    data class Options(
        val deleteSources: Boolean = false,
        val minFreeBytes: Long = 100L shl 20,
        val bufferBytes: Int = 64 * 1024,
    )

    sealed class Outcome(val name: String) {
        class Moved(name: String) : Outcome(name)
        class CopiedKept(name: String) : Outcome(name)            // copied and verified, source kept (no confirmation)
        class Skipped(name: String, val reason: String) : Outcome(name)
        class Failed(name: String, val reason: String) : Outcome(name)
    }

    @Volatile var cancelled = false
    @Volatile var doneBytes = 0L; private set
    @Volatile var totalBytes = 0L; private set

    /** What would be moved, without touching anything (shown to the user before the confirmation). */
    fun plan(): List<File> = candidates()

    private fun candidates(): List<File> = from.listFiles().orEmpty()
        .filter { it.isFile && !UsbPaths.isSymlink(it) && !it.name.startsWith(".") && !it.name.endsWith(Storage.PART) &&
            !it.name.endsWith(Meta.SUFFIX) && !UsbPaths.isExecutable(it.name) }
        .sortedBy { it.name }

    /** Runs the whole migration; [alive] says whether both folders are still reachable (drive pulled -> stop, nothing lost). */
    fun run(alive: () -> Boolean = { true }, progress: (done: Long, total: Long) -> Unit = { _, _ -> }): List<Outcome> {
        val files = candidates()
        totalBytes = files.sumOf { it.length() }
        val out = ArrayList<Outcome>()
        if (!(to.isDirectory || to.mkdirs())) return files.map { Outcome.Failed(it.name, "destination folder unavailable") }
        for (src in files) {
            if (cancelled) break
            if (!alive()) { out += Outcome.Failed(src.name, "volume removed"); break }
            val r = try { one(src, alive) { d -> doneBytes += d; progress(doneBytes, totalBytes) } }
                    catch (e: IOException) { Outcome.Failed(src.name, e.message ?: e.javaClass.simpleName) }
                    catch (e: RuntimeException) { Outcome.Failed(src.name, e.message ?: e.javaClass.simpleName) }
            out += r
            if (r is Outcome.Failed && r.reason == "volume removed") break
        }
        return out
    }

    private fun one(src: File, alive: () -> Boolean, add: (Long) -> Unit): Outcome {
        val size = src.length()
        val dstName = if (toFs.restrictiveNames) UsbPaths.storedName(src.name) else src.name
        if (UsbPaths.badSegment(dstName) != null) return Outcome.Skipped(src.name, "invalid name")
        if (size > toFs.maxFileBytes) return Outcome.Skipped(src.name, "file too large for ${toFs.label}")
        val dst = File(to, dstName)
        val part = File(to, dstName + Storage.PART)
        val stamp = src.lastModified()

        if (dst.exists()) {
            if (dst.length() == size && sha256(dst) == sha256(src)) return finish(src, size, add, alreadyThere = true)
            return Outcome.Skipped(src.name, "a different file with that name already exists on the destination")
        }
        var have = if (part.isFile) part.length() else 0L
        if (have > size) { part.delete(); have = 0 }
        if (to.usableSpace - (size - have) < options.minFreeBytes) return Outcome.Failed(src.name, "not enough space on the destination")

        fault("copy:${src.name}")
        FileInputStream(src).use { input ->
            if (have > 0) input.channel.position(have)
            RandomAccessFile(part, "rw").use { out ->
                out.seek(have)
                val buf = ByteArray(options.bufferBytes)
                var left = size - have
                while (left > 0) {
                    if (cancelled) return Outcome.Skipped(src.name, "cancelled")
                    if (!alive()) throw IOException("volume removed")
                    val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                    if (n < 0) throw IOException("source ended early")
                    out.write(buf, 0, n); left -= n; add(n.toLong())
                }
                out.fd.sync()
            }
        }
        if (src.length() != size || src.lastModified() != stamp) { part.delete(); return Outcome.Failed(src.name, "source changed during the copy") }
        fault("verify:${src.name}")
        if (part.length() != size || sha256(part) != sha256(src)) { part.delete(); return Outcome.Failed(src.name, "verification failed: the copy differs from the source") }
        fault("commit:${src.name}")
        if (!part.renameTo(dst)) throw IOException("rename failed")
        runCatching { FileInputStream(dst).use { it.fd.sync() } }
        return finish(src, size, add, alreadyThere = false)
    }

    private fun finish(src: File, size: Long, add: (Long) -> Unit, alreadyThere: Boolean): Outcome {
        if (alreadyThere) add(size)
        if (!options.deleteSources) return Outcome.CopiedKept(src.name)
        fault("delete:${src.name}")
        if (!src.delete()) return Outcome.Failed(src.name, "copied and verified, but the source could not be removed")
        File(from, src.name + Meta.SUFFIX).delete()
        return Outcome.Moved(src.name)
    }

    companion object {
        fun sha256(f: File): String {
            val md = MessageDigest.getInstance("SHA-256")
            FileInputStream(f).use { i -> val b = ByteArray(1 shl 16); while (true) { val n = i.read(b); if (n < 0) break; md.update(b, 0, n) } }
            return md.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
