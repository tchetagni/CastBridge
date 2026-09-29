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
    /** 6-digit PIN required (header X-CB-Pin or ?pin=) on every route but GET / and GET /api/hello. null = open (tests). */
    pin: String? = null,
    private val guard: PinGuard? = pin?.let { PinGuard(it) },
    private val device: Device? = null,
    /** Extra authenticated routes (SSH, USB import, ...). Return null if the route is not handled. */
    private val extension: ApiExtension? = null,
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
        if (s.method == Method.GET && path == "/") return page()
        if (s.method == Method.GET && path == "/api/hello")
            return ok("""{"app":"castbridge-tv","v":${q(VERSION)},"pinRequired":${guard != null}}""")
        denied(s, p)?.let { return it }
        val ext = if (path.startsWith("/api/")) extension?.handle(path, s.method.name, p) else null
        return when {
            // NanoHTTPD already percent-decoded the URI. A rejected upload leaves its body unread on the
            // socket, which would corrupt the next keep-alive request: close the connection instead.
            s.method == Method.PUT && path.startsWith("/upload/") -> upload(s, path.removePrefix("/upload/"), p).also {
                if (it.status != Response.Status.OK) it.addHeader("Connection", "close")
            }
            path == "/api/info" -> ok(info())
            path == "/api/part" -> named(p) { ok(part(it)) }
            path == "/api/sysinfo" -> sysinfo()
            ext != null -> json(status(ext.status), ext.json)
            s.method != Method.POST -> json(Response.Status.METHOD_NOT_ALLOWED, """{"error":"use POST"}""")
            path == "/api/volume" -> withDevice { d ->
                val pct = p["pct"]?.toIntOrNull()?.coerceIn(0, 100) ?: return@withDevice bad("pct required")
                d.setVolume(pct); sysinfo()
            }
            path == "/api/restart" -> withDevice { d ->
                Thread { Thread.sleep(400); runCatching { d.restartApp() } }.apply { isDaemon = true }.start()
                ok("""{"restarting":true}""")
            }
            path == "/api/rename" -> named(p) { from ->
                val to = safeName(p["to"].orEmpty()) ?: return@named bad("bad target name")
                val src = File(dir, from)
                when {
                    !src.isFile -> json(Response.Status.NOT_FOUND, """{"error":"not found"}""")
                    File(dir, to).exists() || partFile(to).exists() -> json(Response.Status.CONFLICT, """{"error":"target exists"}""")
                    else -> {
                        if (player.state().name == from) player.stop()
                        if (!src.renameTo(File(dir, to))) throw IOException("rename failed")
                        ok(info())
                    }
                }
            }
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

    /** Null when the request may proceed, else 401 (with Connection: close: an unread PUT body would corrupt keep-alive). */
    private fun denied(s: IHTTPSession, p: Map<String, String>): Response? {
        val g = guard ?: return null
        val ip = s.remoteIpAddress ?: "?"
        val given = s.headers["x-cb-pin"] ?: p["pin"]
        return when (g.check(ip, given)) {
            PinGuard.Result.OK -> null
            PinGuard.Result.BAD -> json(Response.Status.UNAUTHORIZED, """{"error":"bad pin"}""")
            PinGuard.Result.LOCKED -> json(Response.Status.UNAUTHORIZED,
                """{"error":"locked","retryAfter":${g.retryAfterSeconds(ip)}}""")
        }?.also { it.addHeader("Connection", "close") }
    }

    /** The admin web page: static, contains no data; every API call it makes carries the PIN. */
    private fun page(): Response = newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", ADMIN_HTML)
        .also { it.addHeader("Cache-Control", "no-store") }

    private fun upload(s: IHTTPSession, rawName: String, p: Map<String, String>): Response {
        val name = safeName(rawName) ?: return bad("bad name")
        val offset = p["offset"]?.toLongOrNull() ?: return bad("offset required")
        val total = p["total"]?.toLongOrNull()?.takeIf { it > 0 } ?: return bad("total required")
        val len = s.headers["content-length"]?.toLongOrNull() ?: return bad("content-length required")
        val final = File(dir, name)
        if (final.isFile && final.length() == total) return ok(part(name))
        synchronized(FileLocks.of(dir, name)) {
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

    private fun sysinfo(): Response = withDevice { d -> ok(d.sysinfo().toJson(d.volume())) }

    private inline fun withDevice(f: (Device) -> Response): Response =
        device?.let(f) ?: json(NOT_IMPLEMENTED, """{"error":"not supported on this device"}""")

    private fun status(code: Int): Response.IStatus = Response.Status.lookup(code) ?: object : Response.IStatus {
        override fun getDescription() = "$code"
        override fun getRequestStatus() = code
    }

    private inline fun named(p: Map<String, String>, f: (String) -> Response): Response =
        safeName(p["name"].orEmpty())?.let(f) ?: bad("bad name")

    private fun partFile(name: String) = File(dir, name + PART)

    private fun ok(body: String) = json(Response.Status.OK, body)
    private fun bad(msg: String) = json(Response.Status.BAD_REQUEST, """{"error":${q(msg)}}""")
    private fun json(st: Response.IStatus, body: String) = newFixedLengthResponse(st, "application/json; charset=utf-8", body)  // default would be US-ASCII

    companion object {
        const val PORT = 8765
        const val VERSION = "0.3"
        private val ADMIN_HTML: String by lazy {
            ReceiverServer::class.java.getResourceAsStream("/castbridge/admin.html")?.use { String(it.readBytes(), Charsets.UTF_8) }
                ?: "<!doctype html><meta charset=utf-8><title>CastBridge TV</title><h1>CastBridge TV</h1><p>Page d'administration indisponible.</p>"
        }
        const val SERVICE_TYPE = "_castbridge._tcp."
        private const val PART = ".part"
        private val NOT_IMPLEMENTED = Response.Status.NOT_IMPLEMENTED
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
