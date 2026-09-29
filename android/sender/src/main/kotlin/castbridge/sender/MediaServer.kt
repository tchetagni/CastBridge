package castbridge.sender

import android.content.ContentResolver
import android.net.Uri
import castbridge.core.tv.HttpRange
import fi.iki.elonen.NanoHTTPD
import java.io.FileInputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/** Serves phone files (content:// URIs) over HTTP with Range support, as DLNA renderers require. */
class MediaServer(private val cr: ContentResolver, port: Int = 8089) : NanoHTTPD(port) {
    class Item(val uri: Uri, val mime: String)

    private val items = ConcurrentHashMap<String, Item>()

    fun register(uri: Uri, mime: String): String {
        val id = Integer.toHexString(uri.toString().hashCode())
        items[id] = Item(uri, mime)
        return id
    }

    override fun serve(session: IHTTPSession): Response {
        val id = session.uri.removePrefix("/media/").substringBefore('.')
        val item = items[id] ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "not found")
        val pfd = try { cr.openFileDescriptor(item.uri, "r") } catch (e: Exception) { null }
            ?: return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "cannot open")
        val total = pfd.statSize
        if (total < 0) { pfd.close(); return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "unknown size") }
        val r = HttpRange.parse(session.headers["range"], total)
        if (r == HttpRange.R.Unsatisfiable) {
            pfd.close()
            return newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT, "").apply {
                addHeader("Content-Range", "bytes */$total"); addHeader("Accept-Ranges", "bytes")
            }
        }
        val start = (r as? HttpRange.R.Part)?.start ?: 0L
        val end = (r as? HttpRange.R.Part)?.end ?: (total - 1)
        val range = r as? HttpRange.R.Part
        val len = end - start + 1
        val dlnaHeaders = { resp: Response ->
            resp.addHeader("Accept-Ranges", "bytes")
            if (range != null) resp.addHeader("Content-Range", "bytes $start-$end/$total")
            resp.addHeader("transferMode.dlna.org", "Streaming")
            resp.addHeader("contentFeatures.dlna.org", FEATURES)
        }
        val status = if (range != null) Response.Status.PARTIAL_CONTENT else Response.Status.OK
        if (session.method == Method.HEAD) {
            // NanoHTTPD 2.3.1 sends the body even for HEAD: answer with the real length and an empty body.
            pfd.close()
            return newFixedLengthResponse(status, item.mime, java.io.ByteArrayInputStream(ByteArray(0)), 0).apply {
                addHeader("Content-Length", len.toString()); dlnaHeaders(this)
            }
        }
        val fis = FileInputStream(pfd.fileDescriptor).also { it.channel.position(start) }
        val body: InputStream = object : InputStream() {
            var left = len
            override fun read(): Int = if (left <= 0) -1 else fis.read().also { if (it >= 0) left-- }
            override fun read(b: ByteArray, off: Int, n: Int): Int {
                if (left <= 0) return -1
                val r = fis.read(b, off, minOf(n.toLong(), left).toInt())
                if (r > 0) left -= r
                return r
            }
            override fun close() { fis.close(); pfd.close() }
        }
        return newFixedLengthResponse(status, item.mime, body, len).apply { dlnaHeaders(this) }
    }

    companion object {
        const val FEATURES = "DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"
    }
}
