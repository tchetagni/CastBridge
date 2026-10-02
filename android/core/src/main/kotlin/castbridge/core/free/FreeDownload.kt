package castbridge.core.free

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** One answer of the archive endpoint. [status] 206 = the range was honoured, 200 = whole file, 416 = range not satisfiable. */
class FreeResponse(val status: Int, val body: InputStream?, private val onClose: () -> Unit = {}) : AutoCloseable {
    override fun close() { runCatching { body?.close() }; runCatching { onClose() } }
}

/** What the download needs from the network (a fake in the tests). Throws IOException when the server cannot be reached. */
interface FreeTransport {
    fun info(): String
    /** [rangeStart] > 0 asks `Range: bytes=rangeStart-`. */
    fun archive(rangeStart: Long): FreeResponse
}

/** HttpURLConnection implementation (the PHONE downloads; the TV never does). [base] = server root without /api/v1. */
class HttpFreeTransport(base: String, private val proxy: java.net.Proxy? = null) : FreeTransport {
    private val root = base.trimEnd('/')
    private fun open(path: String): HttpURLConnection {
        val c = (if (proxy != null) URL(root + path).openConnection(proxy) else URL(root + path).openConnection()) as HttpURLConnection
        c.connectTimeout = 15_000; c.readTimeout = 30_000; c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "CastBridge-phone")
        c.setRequestProperty("Accept-Encoding", "identity") // exact bytes, so Range and sha256 stay meaningful
        return c
    }

    override fun info(): String {
        val c = open("/api/v1/free-content/info")
        try {
            val code = c.responseCode
            if (code != 200) throw IOException("Le serveur répond « $code ».")
            return c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally { c.disconnect() }
    }

    override fun archive(rangeStart: Long): FreeResponse {
        val c = open("/api/v1/free-content")
        if (rangeStart > 0) c.setRequestProperty("Range", "bytes=$rangeStart-")
        val code = try { c.responseCode } catch (e: IOException) { c.disconnect(); throw e }
        val ok = code == 200 || code == 206
        return FreeResponse(code, if (ok) c.inputStream else null) { c.disconnect() }
    }
}

sealed class FreeOutcome {
    /** The complete, verified archive is in [file]. */
    class Done(val file: File, val bytes: Long) : FreeOutcome()
    /** Network cut: the `.part` is kept, a new try resumes. */
    class Interrupted(val message: String, val bytes: Long) : FreeOutcome()
    /** Cancelled by the user: the `.part` is kept. */
    class Cancelled(val bytes: Long) : FreeOutcome()
    class Failed(val message: String) : FreeOutcome()
}

/**
 * Resume/verify state machine. The `.part` file is kept between tries; a try asks `Range` from its length, appends on 206, restarts on 200/416,
 * checks size and sha256 at the end; on mismatch the file is deleted and downloaded again once, then the failure is reported.
 */
object DownloadPlan {
    /** Where to start: 0 when there is no usable partial, [size] when it is already complete (only the checksum remains). */
    fun startOffset(partLength: Long, serverSize: Long): Long = if (partLength <= 0 || serverSize <= 0 || partLength > serverSize) 0 else partLength

    fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { i -> val b = ByteArray(64 * 1024); while (true) { val n = i.read(b); if (n < 0) break; md.update(b, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    const val MISMATCH = "L'archive téléchargée est corrompue (empreinte différente) : elle a été supprimée. Réessayez plus tard."

    fun run(
        transport: FreeTransport, info: FreeContentInfo, part: File,
        progress: (Long, Long) -> Unit = { _, _ -> }, cancelled: () -> Boolean = { false },
    ): FreeOutcome {
        if (!info.downloadable) return FreeOutcome.Failed("L'archive des contenus libres n'est pas encore publiée.")
        var fresh = 0 // restarts from zero after a checksum mismatch
        while (true) {
            val r = attempt(transport, info, part, progress, cancelled)
            if (r !is Mismatch) return r as FreeOutcome
            part.delete()
            if (fresh++ >= 1) return FreeOutcome.Failed(MISMATCH)
        }
    }

    private object Mismatch

    private fun attempt(transport: FreeTransport, info: FreeContentInfo, part: File, progress: (Long, Long) -> Unit, cancelled: () -> Boolean): Any {
        val size = info.sizeBytes
        var start = startOffset(if (part.isFile) part.length() else 0, size)
        if (part.isFile && part.length() != start) { part.delete(); start = 0 }
        if (cancelled()) return FreeOutcome.Cancelled(start)
        if (start < size) {
            var resp: FreeResponse
            try { resp = transport.archive(start) } catch (e: IOException) { return FreeOutcome.Interrupted("Connexion impossible : ${e.message ?: "réseau"}.", start) }
            resp.use {
                if (resp.status == 416 && start > 0) { part.delete(); start = 0; return attempt(transport, info, part, progress, cancelled) }
                if (resp.status != 200 && resp.status != 206 || resp.body == null) return FreeOutcome.Failed("Le serveur répond « ${resp.status} ».")
                if (resp.status == 200 && start > 0) { part.delete(); start = 0 } // range ignored: whole file again
                part.parentFile?.mkdirs()
                var done = start
                try {
                    RandomAccessFile(part, "rw").use { raf ->
                        raf.setLength(start); raf.seek(start)
                        val buf = ByteArray(64 * 1024); var lastTick = -1L
                        progress(done, size)
                        while (true) {
                            if (cancelled()) return FreeOutcome.Cancelled(done)
                            val n = resp.body.read(buf)
                            if (n < 0) break
                            raf.write(buf, 0, n); done += n
                            if (done > size) { return Mismatch }
                            if (done - lastTick >= 32 * 1024 || done == size) { lastTick = done; progress(done, size) }
                        }
                    }
                } catch (e: IOException) {
                    return FreeOutcome.Interrupted("Connexion interrompue : ${e.message ?: "réseau"}. Vous pourrez reprendre là où le téléchargement s'est arrêté.", done)
                }
                if (done < size) return FreeOutcome.Interrupted("Connexion interrompue avant la fin. Vous pourrez reprendre là où le téléchargement s'est arrêté.", done)
            }
        }
        progress(size, size)
        if (part.length() != size) return Mismatch
        if (info.sha256.isNotEmpty() && sha256(part) != info.sha256) return Mismatch
        return FreeOutcome.Done(part, size)
    }
}
