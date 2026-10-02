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

    fun json(): String = "{\"name\":${ReceiverServer.q(name)},\"as\":${ReceiverServer.q(toName)},\"from\":${ReceiverServer.q(from.id)}," +
        "\"to\":${ReceiverServer.q(to.id)},\"done\":$done,\"total\":$total,\"state\":${ReceiverServer.q(state)}," +
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
    data class Marker(val name: String, val toName: String, val fromVolume: String, val size: Long, val verified: Boolean)

    fun write(dir: File, m: Marker) {
        val tmp = File(dir, "$FILE.tmp")
        java.io.FileOutputStream(tmp).use { it.write("${if (m.verified) "verified" else "copying"}\n${m.size}\n${m.fromVolume}\n${m.name}\n${m.toName}\n".toByteArray()); it.fd.sync() }
        val f = File(dir, FILE)
        if (!tmp.renameTo(f)) { f.delete(); if (!tmp.renameTo(f)) throw IOException("cannot write the move marker") }
    }

    fun read(dir: File): Marker? = runCatching {
        val l = File(dir, FILE).readLines()
        Marker(name = l[3], toName = l[4], fromVolume = l[2], size = l[1].toLong(), verified = l[0] == "verified")
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

    fun run(job: MoveJob, src: VolumeStore, dst: VolumeStore, alive: (StorageVolume) -> Boolean, bufferBytes: Int = 64 * 1024) {
        val dstDir = (dst as? FileStore)?.dir
        try {
            val srcSize = src.finalSize(job.name) ?: throw IOException("source missing")
            if (srcSize != job.total) throw IOException("source changed")
            val stamp = stampOf(src, job.name)
            var have = dst.partSize(job.toName)
            val meta = dstDir?.let { Meta.read(it, job.toName) }
            if (have > srcSize || (dstDir != null && have > 0 && meta != srcSize)) { dst.deletePart(job.toName); have = 0 }
            if (dstDir != null) { Meta.write(dstDir, job.toName, srcSize); MoveMarker.write(dstDir, MoveMarker.Marker(job.name, job.toName, job.from.id, srcSize, false)) }
            job.done = have
            if (have < srcSize) {
                src.open(job.name, have).use { input ->
                    dst.openPart(job.toName).use { out ->
                        val buf = ByteArray(bufferBytes)
                        while (job.done < srcSize) {
                            if (job.cancelled) { job.state = "cancelled"; return }
                            if (!alive(job.from) || !alive(job.to)) throw IOException("volume removed")
                            val r = input.read(buf, 0, minOf(buf.size.toLong(), srcSize - job.done).toInt())
                            if (r < 0) throw IOException("source ended early")
                            out.write(buf, 0, r)
                            job.done += r
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
            if (!sameEdges(src, job.name, dst, job.toName, srcSize)) {
                dst.deleteFinal(job.toName); dstDir?.let { Meta.delete(it, job.toName); MoveMarker.delete(it) }
                throw IOException("verification failed: the copy differs from the source")
            }
            if (dstDir != null) {
                Meta.delete(dstDir, job.toName)
                MoveMarker.write(dstDir, MoveMarker.Marker(job.name, job.toName, job.from.id, srcSize, true))
            }
            // Verified and recorded: only now is the source removed.
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
        if (s != m.size || !sameEdges(src, m.name, dst, m.toName, m.size)) { MoveMarker.delete(dir); return null }
        if (!src.deleteFinal(m.name)) return null
        MoveMarker.delete(dir)
        return "déplacement de « ${m.name} » terminé après une coupure"
    }
}
