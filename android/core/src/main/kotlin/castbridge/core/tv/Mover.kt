package castbridge.core.tv

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/** One file being moved from a volume to another. Fields are read by the HTTP threads while the copy thread writes them. */
class MoveJob(val name: String, val from: StorageVolume, val to: StorageVolume, val toName: String, val total: Long) {
    @Volatile var done = 0L
    /** running | done | failed | cancelled */
    @Volatile var state = "running"
    @Volatile var error: String? = null
    @Volatile var cancelled = false
    /** copy | verify (the full comparison of the copy with the source, before anything is deleted) */
    @Volatile var phase = "copy"
    /** Bytes of each file compared so far during [phase] "verify". */
    @Volatile var checked = 0L

    fun json(): String = "{\"name\":${ReceiverServer.q(name)},\"as\":${ReceiverServer.q(toName)},\"from\":${ReceiverServer.q(from.id)}," +
        "\"to\":${ReceiverServer.q(to.id)},\"done\":$done,\"total\":$total,\"state\":${ReceiverServer.q(state)},\"phase\":${ReceiverServer.q(phase)},\"checked\":$checked," +
        "\"error\":${error?.let(ReceiverServer::q) ?: "null"}}"
}

/**
 * Written on the destination volume (hidden file `.castbridge-move`) while a move runs, so that a cut (power, process killed, key
 * pulled) leaves enough to finish or clean up on the next start:
 *  - "copying": the copy is not finished; nothing to decide (the `.part` and its size sidecar let the copy resume);
 *  - "verified": the copy is complete and was compared with the source; only the removal of the source is left.
 */
object MoveMarker {
    const val FILE = ".castbridge-move"
    /** [safe]: bytes of the `.part` known to have reached the medium (fsync), 0 = unknown (older marker). Additive sixth line. */
    data class Marker(val name: String, val toName: String, val fromVolume: String, val size: Long, val verified: Boolean, val safe: Long = 0)

    fun write(dir: File, m: Marker) {
        val tmp = File(dir, "$FILE.tmp")
        java.io.FileOutputStream(tmp).use { it.write("${if (m.verified) "verified" else "copying"}\n${m.size}\n${m.fromVolume}\n${m.name}\n${m.toName}\n${m.safe}\n".toByteArray()); it.fd.sync() }
        val f = File(dir, FILE)
        if (!tmp.renameTo(f)) { f.delete(); if (!tmp.renameTo(f)) throw IOException("cannot write the move marker") }
    }

    fun read(dir: File): Marker? = runCatching {
        val l = File(dir, FILE).readLines()
        Marker(name = l[3], toName = l[4], fromVolume = l[2], size = l[1].toLong(), verified = l[0] == "verified", safe = l.getOrNull(5)?.toLongOrNull() ?: 0L)
    }.getOrNull()

    fun delete(dir: File) { File(dir, FILE).delete(); File(dir, "$FILE.tmp").delete() }
}

/**
 * Copies a finished file to another volume, then deletes the source, and only after checking it:
 *  1. the copy goes to "<name>.part" on the target (a stream of [bufferBytes]; nothing is held in RAM);
 *  2. a partial copy left by a cancel, an error or a pulled drive is resumed from its current size, if its sidecar size
 *     matches the source (otherwise it restarts from zero);
 *  3. if the source changed (size or modification time) while it was copied, the copy is dropped: nothing is committed;
 *  4. only when the partial copy has exactly the source size is it renamed to its final name (synced to the medium first on a
 *     removable drive), the final size is checked again, and the first and last [EDGE] bytes of both files are compared;
 *  5. a "verified" marker is written on the target, THEN the source is deleted, then the marker. A cut between any two of these
 *     steps loses nothing: before 5 the source is intact; after the marker [recover] finishes the removal at the next start.
 * Any failure leaves the source untouched. Everything here is synchronous; the server runs it on its own thread.
 */
object Mover {
    const val EDGE = 1 shl 20
    /** Resumed copy: the last bytes of the `.part` are compared with the source before the copy goes on. */
    const val TAIL_WINDOW = 8 shl 20
    /** The partial copy is flushed to the medium every so many bytes (and its safe size recorded in the marker). */
    const val SYNC_EVERY = 64L shl 20
    /** From this size (or after a resume) the whole copy is compared with the whole source, byte for byte (SHA-256), before the source is deleted. */
    const val FULL_PROOF_FROM = 100L shl 20

    private fun stampOf(st: VolumeStore, name: String): Long = (st as? FileStore)?.fileOf(name)?.lastModified() ?: 0L

    private fun edge(st: VolumeStore, name: String, from: Long, len: Int): ByteArray = st.open(name, from).use { readUpTo(it, len) }

    private fun readUpTo(i: InputStream, len: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream(minOf(len, 1 shl 16))
        val buf = ByteArray(1 shl 16)
        var left = len
        while (left > 0) { val n = i.read(buf, 0, minOf(buf.size, left)); if (n < 0) break; out.write(buf, 0, n); left -= n }
        return out.toByteArray()
    }

    /** Same size already known: do the first and the last [EDGE] bytes match? (A cheap check, not a full comparison: the sizes were compared and every byte was written by this process.) */
    fun sameEdges(a: VolumeStore, an: String, b: VolumeStore, bn: String, size: Long): Boolean {
        fun digest(st: VolumeStore, n: String, from: Long) = MessageDigest.getInstance("SHA-256").digest(edge(st, n, from, EDGE))
        if (!digest(a, an, 0).contentEquals(digest(b, bn, 0))) return false
        val tail = maxOf(0L, size - EDGE)
        return tail == 0L || digest(a, an, tail).contentEquals(digest(b, bn, tail))
    }

    /** SHA-256 of the whole file, in blocks; null when [cancelled] became true. [progress] gets the bytes read so far. */
    private fun fullDigest(st: VolumeStore, name: String, cancelled: () -> Boolean, progress: (Long) -> Unit): ByteArray? {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(1 shl 18)
        var n = 0L
        st.open(name, 0).use { i ->
            while (true) {
                if (cancelled()) return null
                val r = i.read(buf); if (r < 0) break
                md.update(buf, 0, r); n += r; progress(n)
            }
        }
        return md.digest()
    }

    /**
     * Resumed partial copy: compares its last [window] bytes with the same bytes of the source, block by block, and returns the length up to which the
     * copy is identical (the first differing block, or a short read, cuts it there). Never more than [have]. 0 when the copy cannot be read here.
     */
    private fun identicalPrefix(src: VolumeStore, srcName: String, dst: VolumeStore, dstName: String, have: Long, window: Int): Long {
        val block = 1 shl 20
        var pos = maxOf(0L, have - window)
        pos -= pos % block
        val dstFile = (dst as? FileStore)?.let { File(it.dir, it.diskName(dstName) + Storage.PART) } ?: return 0L
        java.io.RandomAccessFile(dstFile, "r").use { raf ->
            src.open(srcName, pos).use { si ->
                var left = have - pos
                raf.seek(pos)
                while (left > 0) {
                    val want = minOf(block.toLong(), left).toInt()
                    val x = readUpTo(si, want)
                    val y = ByteArray(want)
                    val got = raf.read(y, 0, want)
                    if (got != want || x.size != want || !x.contentEquals(y)) return pos
                    pos += want; left -= want
                }
            }
        }
        return have
    }

    /** Cuts the partial copy to [len] bytes (a real folder only: elsewhere the caller deletes it and restarts from zero). */
    private fun truncatePart(dst: VolumeStore, name: String, len: Long): Boolean {
        val fs = dst as? FileStore ?: return false
        return runCatching { java.io.RandomAccessFile(File(fs.dir, fs.diskName(name) + Storage.PART), "rw").use { it.setLength(len); it.fd.sync() }; true }.getOrDefault(false)
    }

    fun run(job: MoveJob, src: VolumeStore, dst: VolumeStore, alive: (StorageVolume) -> Boolean, bufferBytes: Int = 64 * 1024,
            tailWindow: Int = TAIL_WINDOW, fullProofFrom: Long = FULL_PROOF_FROM) {
        val dstDir = (dst as? FileStore)?.dir
        try {
            val srcSize = src.finalSize(job.name) ?: throw IOException("source missing")
            if (srcSize != job.total) throw IOException("source changed")
            val stamp = stampOf(src, job.name)
            var have = dst.partSize(job.toName)
            val meta = dstDir?.let { Meta.read(it, job.toName) }
            if (have > srcSize || (dstDir != null && have > 0 && meta != srcSize)) { dst.deletePart(job.toName); have = 0 }
            // A resumed partial copy is never trusted as it stands: what was not flushed is cut, the tail is compared with the source, and the whole copy is
            // compared (full proof) before the source can be deleted.
            val resumed = have > 0
            if (resumed) {
                val mk = dstDir?.let { MoveMarker.read(it) }?.takeIf { !it.verified && it.name == job.name && it.toName == job.toName && it.size == srcSize }
                if (mk != null && mk.safe in 1 until have) { if (truncatePart(dst, job.toName, mk.safe)) have = mk.safe else { dst.deletePart(job.toName); have = 0 } }
                if (have > 0) {
                    val ok = identicalPrefix(src, job.name, dst, job.toName, have, tailWindow)
                    if (ok < have) { if (truncatePart(dst, job.toName, ok)) have = ok else { dst.deletePart(job.toName); have = 0 } }
                }
            }
            if (dstDir != null) { Meta.write(dstDir, job.toName, srcSize); MoveMarker.write(dstDir, MoveMarker.Marker(job.name, job.toName, job.from.id, srcSize, false, have)) }
            job.done = have
            if (have < srcSize) {
                src.open(job.name, have).use { input ->
                    dst.openPart(job.toName).use { out ->
                        val buf = ByteArray(bufferBytes)
                        var sinceSync = 0L
                        while (job.done < srcSize) {
                            if (job.cancelled) { job.state = "cancelled"; return }
                            if (!alive(job.from) || !alive(job.to)) throw IOException("volume removed")
                            val r = input.read(buf, 0, minOf(buf.size.toLong(), srcSize - job.done).toInt())
                            if (r < 0) throw IOException("source ended early")
                            out.write(buf, 0, r)
                            job.done += r; sinceSync += r
                            if (sinceSync >= SYNC_EVERY) {
                                sinceSync = 0
                                (out as? java.io.FileOutputStream)?.let { fo ->
                                    runCatching { fo.fd.sync() }.onSuccess {
                                        dstDir?.let { d -> MoveMarker.write(d, MoveMarker.Marker(job.name, job.toName, job.from.id, srcSize, false, job.done)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (dst.partSize(job.toName) != srcSize) throw IOException("size mismatch after copy")
            if (src.finalSize(job.name) != srcSize || stampOf(src, job.name) != stamp) {
                dst.deletePart(job.toName); dstDir?.let { Meta.delete(it, job.toName); MoveMarker.delete(it) }
                throw IOException("source changed during the copy")
            }
            dst.commit(job.toName)
            if (dst.finalSize(job.toName) != srcSize) throw IOException("size mismatch after commit")
            // The proof: same size (above), same edges, and for a resumed copy or a big file the SHA-256 of the whole of both. Nothing is deleted without it.
            var proven = sameEdges(src, job.name, dst, job.toName, srcSize)
            if (proven && (resumed || srcSize >= fullProofFrom)) {
                job.phase = "verify"; job.checked = 0
                val a = fullDigest(src, job.name, { job.cancelled }) { job.checked = it }
                val b = if (a == null) null else fullDigest(dst, job.toName, { job.cancelled }) { job.checked = it }
                if (a == null || b == null) {
                    // cancelled during the proof: our own unproven copy is dropped, the source is untouched
                    dst.deleteFinal(job.toName); dstDir?.let { Meta.delete(it, job.toName); MoveMarker.delete(it) }
                    job.state = "cancelled"; return
                }
                proven = a.contentEquals(b)
                if (proven && (src.finalSize(job.name) != srcSize || stampOf(src, job.name) != stamp)) proven = false     // the source moved under the proof
            }
            if (!proven) {
                dst.deleteFinal(job.toName); dstDir?.let { Meta.delete(it, job.toName); MoveMarker.delete(it) }
                throw IOException("verification failed: the copy differs from the source")
            }
            if (dstDir != null) {
                Meta.delete(dstDir, job.toName)
                MoveMarker.write(dstDir, MoveMarker.Marker(job.name, job.toName, job.from.id, srcSize, true))
            }
            // Proven and recorded: only now is the source removed.
            check(proven) { "refusing to delete an unproven source" }
            if (!src.deleteFinal(job.name)) throw IOException("copied and verified, but the source could not be removed (the next start retries)")
            dstDir?.let { MoveMarker.delete(it) }
            job.state = "done"
        } catch (e: IOException) {
            job.state = "failed"; job.error = e.message ?: e.javaClass.simpleName
        } catch (e: RuntimeException) {
            job.state = "failed"; job.error = e.message ?: e.javaClass.simpleName
        }
    }

    /**
     * After a cut: finishes a move whose copy was verified but whose source removal did not happen. [stores] gives the store of a
     * volume id (null if absent). Returns a message for the user when something was finished, else null. Never deletes anything
     * that was not verified: a "copying" marker only cleans itself up when no copy remains.
     */
    fun recover(dst: VolumeStore, stores: (String) -> VolumeStore?): String? {
        val dir = (dst as? FileStore)?.dir ?: return null
        val m = MoveMarker.read(dir) ?: return null
        if (!m.verified) {
            if (dst.partSize(m.toName) == 0L && dst.finalSize(m.toName) == null) MoveMarker.delete(dir)
            return null
        }
        val have = dst.finalSize(m.toName)
        if (have != m.size) { MoveMarker.delete(dir); return null }                 // the copy is gone or changed: the source is the only truth
        val src = stores(m.fromVolume) ?: return null                               // key not here now: decide when it is back
        val s = src.finalSize(m.name)
        if (s == null) { MoveMarker.delete(dir); return "déplacement de « ${m.name} » déjà terminé" }
        val proven = s == m.size && sameEdges(src, m.name, dst, m.toName, m.size)
        if (!proven) { MoveMarker.delete(dir); return null }
        if (!src.deleteFinal(m.name)) return null
        MoveMarker.delete(dir)
        return "déplacement de « ${m.name} » terminé après une coupure"
    }
}
