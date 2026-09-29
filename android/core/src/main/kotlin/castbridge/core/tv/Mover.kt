package castbridge.core.tv

import java.io.IOException

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
 * Copies a finished file to another volume, then deletes the source, and only after checking sizes:
 *  1. the copy goes to "<name>.part" on the target (a stream of [bufferBytes]; nothing is held in RAM);
 *  2. a partial copy left by a cancel, an error or a pulled drive is resumed from its current size, if its sidecar size
 *     matches the source (otherwise it restarts from zero);
 *  3. only when the partial copy has exactly the source size is it renamed to its final name, the final size is checked again,
 *     and only then is the source deleted. Any failure leaves the source untouched.
 * Everything here is synchronous; the server runs it on its own thread.
 */
object Mover {
    fun run(job: MoveJob, src: VolumeStore, dst: VolumeStore, alive: (StorageVolume) -> Boolean, bufferBytes: Int = 64 * 1024) {
        try {
            val srcSize = src.finalSize(job.name) ?: throw IOException("source missing")
            if (srcSize != job.total) throw IOException("source changed")
            val dstDir = (dst as? FileStore)?.dir
            var have = dst.partSize(job.toName)
            val meta = dstDir?.let { Meta.read(it, job.toName) }
            if (have > srcSize || (dstDir != null && have > 0 && meta != srcSize)) { dst.deletePart(job.toName); have = 0 }
            if (dstDir != null) Meta.write(dstDir, job.toName, srcSize)
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
            dst.commit(job.toName)
            if (dst.finalSize(job.toName) != srcSize) throw IOException("size mismatch after commit")
            if (dstDir != null) Meta.delete(dstDir, job.toName)
            // Verified: only now is the source removed.
            src.deleteFinal(job.name)
            job.state = "done"
        } catch (e: IOException) {
            job.state = "failed"; job.error = e.message ?: e.javaClass.simpleName
        } catch (e: RuntimeException) {
            job.state = "failed"; job.error = e.message ?: e.javaClass.simpleName
        }
    }
}
