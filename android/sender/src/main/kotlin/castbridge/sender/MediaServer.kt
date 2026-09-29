package castbridge.sender

import android.content.ContentResolver
import android.net.Uri
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
        val range = session.headers["range"]?.let { Regex("bytes=(\\d*)-(\\d*)").find(it) }
        var start = 0L
        var end = total - 1
        if (range != null) {
            val s = range.groupValues[1]
            val e = range.groupValues[2]
            if (s.isEmpty() && e.isNotEmpty()) start = maxOf(0, total - e.toLong())        // suffix range
            else { start = s.toLongOrNull() ?: 0; end = e.toLongOrNull()?.coerceAtMost(total - 1) ?: end }
            if (start > end || start >= total) {
                pfd.close()
                return newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT, "").apply {
                    addHeader("Content-Range", "bytes */$total")
                }
            }
        }
        val len = end - start + 1
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
        val status = if (range != null) Response.Status.PARTIAL_CONTENT else Response.Status.OK
        return newFixedLengthResponse(status, item.mime, body, len).apply {
            addHeader("Accept-Ranges", "bytes")
            if (range != null) addHeader("Content-Range", "bytes $start-$end/$total")
            addHeader("transferMode.dlna.org", "Streaming")
            addHeader("contentFeatures.dlna.org", FEATURES)
        }
    }

    companion object {
        const val FEATURES = "DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"
    }
}
