package castbridge.core.tv

import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.IOException

/** Playback side of the receiver, implemented with libVLC on the TV. Called from HTTP threads. */
interface Player {
    fun play(file: File, posMs: Long)
    fun pause()
    fun resume()
    fun seek(posMs: Long)
    fun stop()
    fun state(): PlayerState
}

data class PlayerState(val state: String = "idle", val name: String? = null, val posMs: Long = 0, val durMs: Long = 0)

/**
 * CastBridge TV HTTP API (port 8765). Videos are uploaded whole, resumably, into [dir] so playback
 * no longer depends on the phone or the network. See docs/NEXT-preload.md.
 */
class ReceiverServer(
    private val dir: File,
    private val player: Player,
    port: Int = PORT,
    private val minFreeBytes: Long = 100L shl 20,
) : NanoHTTPD(port) {

    init { dir.mkdirs() }

    override fun serve(s: IHTTPSession): Response = try {
        route(s)
    } catch (e: Exception) {
        json(Response.Status.INTERNAL_ERROR, """{"error":${q(e.message ?: e.javaClass.simpleName)}}""")
    }

    private fun route(s: IHTTPSession): Response {
        val p = s.parameters.mapValues { it.value.firstOrNull().orEmpty() }
        val path = s.uri
        return when {
            // NanoHTTPD already percent-decoded the URI. A rejected upload leaves its body unread on the
            // socket, which would corrupt the next keep-alive request: close the connection instead.
            s.method == Method.PUT && path.startsWith("/upload/") -> upload(s, path.removePrefix("/upload/"), p).also {
                if (it.status != Response.Status.OK) it.addHeader("Connection", "close")
            }
            path == "/api/info" -> ok(info())
            path == "/api/part" -> named(p) { ok(part(it)) }
            s.method != Method.POST -> json(Response.Status.METHOD_NOT_ALLOWED, """{"error":"use POST"}""")
            path == "/api/reset" -> named(p) { partFile(it).delete(); ok(part(it)) }
            path == "/api/delete" -> named(p) {
                if (player.state().name == it) player.stop()
                File(dir, it).delete(); partFile(it).delete(); ok(info())
            }
            path == "/api/play" -> named(p) {
                val f = File(dir, it)
                if (!f.isFile) json(Response.Status.NOT_FOUND, """{"error":"not uploaded"}""")
                else { player.play(f, p["pos"]?.toLongOrNull() ?: 0); ok(info()) }
            }
            path == "/api/pause" -> { player.pause(); ok(info()) }
            path == "/api/resume" -> { player.resume(); ok(info()) }
            path == "/api/stop" -> { player.stop(); ok(info()) }
            path == "/api/seek" -> { player.seek(p["pos"]?.toLongOrNull() ?: 0); ok(info()) }
            else -> json(Response.Status.NOT_FOUND, """{"error":"not found"}""")
        }
    }

    private fun upload(s: IHTTPSession, rawName: String, p: Map<String, String>): Response {
        val name = safeName(rawName) ?: return bad("bad name")
        val offset = p["offset"]?.toLongOrNull() ?: return bad("offset required")
        val total = p["total"]?.toLongOrNull()?.takeIf { it > 0 } ?: return bad("total required")
        val len = s.headers["content-length"]?.toLongOrNull() ?: return bad("content-length required")
        val final = File(dir, name)
        if (final.isFile && final.length() == total) return ok(part(name))
        synchronized(lockFor(name)) {
            val pf = partFile(name)
            val cur = pf.length()
            if (offset != cur || offset + len > total) return json(Response.Status.CONFLICT, part(name))
            if (dir.usableSpace - (total - cur) < minFreeBytes)
                return json(INSUFFICIENT_STORAGE, """{"error":"not enough space","free":${dir.usableSpace}}""")
            // Append as bytes arrive: whatever reached the disk before a network cut is kept for resume.
            val input = s.inputStream
            java.io.FileOutputStream(pf, true).use { out ->
                val buf = ByteArray(256 * 1024)
                var left = len
                while (left > 0) {
                    val r = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                    if (r < 0) break
                    out.write(buf, 0, r)
                    left -= r
                }
            }
            if (pf.length() == total) {
                final.delete()
                if (!pf.renameTo(final)) throw IOException("rename failed")
            }
            return ok(part(name))
        }
    }

    private fun part(name: String): String {
        val f = File(dir, name)
        val done = f.isFile
        return """{"name":${q(name)},"length":${if (done) f.length() else partFile(name).length()},"done":$done}"""
    }

    private fun info(): String {
        val files = dir.listFiles().orEmpty().filter { it.isFile && !it.name.endsWith(PART) }.sortedBy { it.name }
        val ps = player.state()
        return """{"files":[${files.joinToString(",") { """{"name":${q(it.name)},"size":${it.length()}}""" }}],""" +
            """"free":${dir.usableSpace},"player":{"state":${q(ps.state)},"name":${ps.name?.let(::q) ?: "null"},""" +
            """"pos":${ps.posMs},"dur":${ps.durMs}}}"""
    }

    private inline fun named(p: Map<String, String>, f: (String) -> Response): Response =
        safeName(p["name"].orEmpty())?.let(f) ?: bad("bad name")

    private fun partFile(name: String) = File(dir, name + PART)
    private val locks = java.util.concurrent.ConcurrentHashMap<String, Any>()
    private fun lockFor(name: String) = locks.getOrPut(name) { Any() }

    private fun ok(body: String) = json(Response.Status.OK, body)
    private fun bad(msg: String) = json(Response.Status.BAD_REQUEST, """{"error":${q(msg)}}""")
    private fun json(st: Response.IStatus, body: String) = newFixedLengthResponse(st, "application/json; charset=utf-8", body)  // default would be US-ASCII

    companion object {
        const val PORT = 8765
        const val SERVICE_TYPE = "_castbridge._tcp."
        private const val PART = ".part"
        private val INSUFFICIENT_STORAGE = object : Response.IStatus {
            override fun getDescription() = "507 Insufficient Storage"
            override fun getRequestStatus() = 507
        }

        fun safeName(n: String): String? = n.trim().takeIf {
            it.isNotEmpty() && it.length <= 200 && it != "." && it != ".." && !it.endsWith(PART) &&
                it.none { c -> c == '/' || c == '\\' || c == '\u0000' }
        }

        fun q(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\""
    }
}
