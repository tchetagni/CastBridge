package castbridge.core.tv

import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.IOException

/** Playback side of the receiver, implemented with libVLC on the TV. Called from HTTP threads. */
interface Player {
    fun play(file: File, posMs: Long)
    /** Plays [name] from [url] (the server's own /stream/ route) while its upload is still in progress. */
    fun playStream(url: String, name: String, posMs: Long)
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
    profile: TvProfile = TvProfile(),
    /** Called when settings change through the API, so the app can persist them. */
    private val onSettings: (TvProfile) -> Unit = {},
    /** 6-digit PIN required (header X-CB-Pin or ?pin=) on every route but GET / and GET /api/hello. null = open (tests). */
    pin: String? = null,
    private val guard: PinGuard? = pin?.let { PinGuard(it) },
    private val device: Device? = null,
    /** Extra authenticated routes (SSH, USB import, ...). Return null if the route is not handled. */
    private val extension: ApiExtension? = null,
) : NanoHTTPD(port) {

    @Volatile private var cfg = profile
    /** Random per run: lets the TV's own player read /stream/ on loopback without the PIN (never leaves the process). */
    private val streamToken: String = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    private val meters = java.util.concurrent.ConcurrentHashMap<String, RateMeter>()

    fun streamUrl(name: String) = "http://127.0.0.1:$listeningPort/stream/${java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20")}?t=$streamToken"

    /** (received, total) for a file being uploaded or complete; null if unknown. */
    fun progress(name: String): Pair<Long, Long>? {
        val f = File(dir, name)
        if (f.isFile) return f.length() to f.length()
        val total = Meta.read(dir, name) ?: return null
        return partFile(name).takeIf { it.isFile }?.let { it.length() to total }
    }

    init {
        dir.mkdirs()
        Storage.cleanOrphans(dir, cfg.orphanPartMaxAgeMs)     // abandoned partial uploads must not eat scarce flash
        setAsyncRunner(BoundedRunner(cfg.maxHttpThreads))
    }

    /** Called by the app when a file played to the end: honours the "delete after play" setting. */
    fun onPlaybackEnded(name: String) {
        if (!cfg.deleteAfterPlay) return
        safeName(name)?.let { n -> synchronized(FileLocks.of(dir, n)) { File(dir, n).delete(); Storage.forget(dir, n); invalidate() } }
    }

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
        val isStream = (s.method == Method.GET || s.method == Method.HEAD) && path.startsWith("/stream/")
        val loopbackStream = isStream && p["t"] == streamToken && s.remoteIpAddress.let { it == "127.0.0.1" || it == "::1" || it == "0:0:0:0:0:0:0:1" }
        if (!loopbackStream) denied(s, p)?.let { return it }
        if (isStream) return stream(s, path.removePrefix("/stream/"))
        val ext = if (path.startsWith("/api/")) extension?.handle(path, s.method.name, p) else null
        return when {
            // NanoHTTPD already percent-decoded the URI. A rejected upload leaves its body unread on the
            // socket, which would corrupt the next keep-alive request: close the connection instead.
            s.method == Method.PUT && path.startsWith("/upload/") -> upload(s, path.removePrefix("/upload/"), p).also {
                if (it.status != Response.Status.OK) it.addHeader("Connection", "close")
            }
            path == "/api/info" -> ok(info())
            path == "/api/storage" -> storage(s.method, p)
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
                        if (from in Storage.playedNames(dir)) { Storage.forget(dir, from); Storage.markPlayed(dir, to) }
                        invalidate()
                        ok(info())
                    }
                }
            }
            path == "/api/reset" -> named(p) { partFile(it).delete(); Meta.delete(dir, it); meters.remove(it); invalidate(); ok(part(it)) }
            path == "/api/delete" -> named(p) {
                if (player.state().name == it) player.stop()
                File(dir, it).delete(); partFile(it).delete(); Meta.delete(dir, it); meters.remove(it); Storage.forget(dir, it); invalidate(); ok(info())
            }
            path == "/api/play" -> named(p) {
                val f = File(dir, it)
                val pos = p["pos"]?.toLongOrNull() ?: 0
                if (f.isFile) { Storage.markPlayed(dir, it); player.play(f, pos); ok(info()) }
                else playIncomplete(it, pos)
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
            if (cur == 0L && offset == 0L) Meta.delete(dir, name)      // a fresh upload: forget any stale size
            if (offset != cur || offset + len > total) return json(Response.Status.CONFLICT, part(name))
            // Refuse before writing anything: free space, then quota (evicting old played files if allowed).
            ensureRoom(total - cur)?.let { why ->
                return json(INSUFFICIENT_STORAGE, """{"error":${q(why)},"free":${dir.usableSpace},"quota":${Storage.quota(dir, cfg)}}""")
            }
            // Append as bytes arrive: whatever reached the disk before a network cut is kept for resume.
            Meta.write(dir, name, total)      // lets /stream/ announce the final size before the last byte arrives
            val meter = meters.getOrPut(name) { RateMeter() }
            val input = s.inputStream
            java.io.FileOutputStream(pf, true).use { out ->
                val buf = ByteArray(cfg.ioBufferBytes)
                var left = len
                while (left > 0) {
                    val r = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                    if (r < 0) break
                    out.write(buf, 0, r)
                    meter.add(r.toLong())
                    left -= r
                }
            }
            if (pf.length() == total) {
                final.delete()
                if (!pf.renameTo(final)) throw IOException("rename failed")
                Storage.forget(dir, name)
                Meta.delete(dir, name); meters.remove(name)
            }
            invalidate()
            return ok(part(name))
        }
    }

    private fun part(name: String): String {
        val f = File(dir, name)
        val done = f.isFile
        return """{"name":${q(name)},"length":${if (done) f.length() else partFile(name).length()},"done":$done}"""
    }

    // /api/info is polled every second by phones and the web page: list the folder at most once per infoCacheMs.
    private class Listing(val at: Long, val filesJson: String, val used: Long)
    @Volatile private var listing: Listing? = null
    private fun invalidate() { listing = null }

    private fun listing(): Listing {
        val now = System.nanoTime() / 1_000_000
        listing?.let { if (now - it.at < cfg.infoCacheMs) return it }
        val all = dir.listFiles().orEmpty().filter { it.isFile && !it.name.startsWith(".") }
        // name, size (final), received, complete: partial uploads are listed too, so clients can play them early
        val entries = ArrayList<Triple<String, Long, Pair<Long, Boolean>>>()
        for (f in all) when {
            f.name.endsWith(Meta.SUFFIX) -> {}
            f.name.endsWith(PART) -> {
                val base = f.name.removeSuffix(PART)
                if (!File(dir, base).isFile) Meta.read(dir, base)?.let { entries += Triple(base, it, f.length() to false) }
            }
            else -> entries += Triple(f.name, f.length(), f.length() to true)
        }
        val sb = StringBuilder("[")
        entries.sortedBy { it.first }.forEachIndexed { i, (n, size, rc) ->
            if (i > 0) sb.append(',')
            sb.append("{\"name\":").append(q(n)).append(",\"size\":").append(size)
                .append(",\"received\":").append(rc.first).append(",\"complete\":").append(rc.second).append('}')
        }
        return Listing(now, sb.append(']').toString(), all.sumOf { it.length() }).also { listing = it }
    }

    private fun info(): String {
        val l = listing()
        val ps = player.state()
        return """{"files":${l.filesJson},"free":${dir.usableSpace},"used":${l.used},"quota":${Storage.quota(dir, cfg, l.used)},""" +
            """"player":{"state":${q(ps.state)},"name":${ps.name?.let(::q) ?: "null"},"pos":${ps.posMs},"dur":${ps.durMs}}}"""
    }

    /** Null if [incoming] bytes can be stored, else the reason. May delete old *played* files when eviction is on. */
    private fun ensureRoom(incoming: Long): String? {
        val why = Storage.refusal(dir, cfg, incoming) ?: return null
        if (!cfg.evictPlayed) return why
        val used = Storage.used(dir)
        val needed = maxOf(used + incoming - Storage.quota(dir, cfg, used), incoming + cfg.minFreeBytes - dir.usableSpace, 1L)
        val plan = Storage.evictionPlan(dir, needed, player.state().name) ?: return why
        plan.forEach { it.delete(); Storage.forget(dir, it.name) }
        invalidate()
        return Storage.refusal(dir, cfg, incoming)
    }

    private fun storage(method: Method, p: Map<String, String>): Response {
        if (method == Method.POST) {
            var c = cfg
            p["deleteAfterPlay"]?.let { c = c.copy(deleteAfterPlay = it == "true" || it == "1") }
            p["evictPlayed"]?.let { c = c.copy(evictPlayed = it == "true" || it == "1") }
            p["quotaMb"]?.let { v -> c = c.copy(quotaBytes = (v.toLongOrNull() ?: return bad("quotaMb must be a number")).coerceAtLeast(0) shl 20) }
            cfg = c; onSettings(c)
        } else if (method != Method.GET) return json(Response.Status.METHOD_NOT_ALLOWED, """{"error":"use GET or POST"}""")
        val used = Storage.used(dir)
        return ok("""{"used":$used,"free":${dir.usableSpace},"quota":${Storage.quota(dir, cfg, used)},"quotaMb":${cfg.quotaBytes shr 20},""" +
            """"deleteAfterPlay":${cfg.deleteAfterPlay},"evictPlayed":${cfg.evictPlayed},"minFreeMb":${cfg.minFreeBytes shr 20}}""")
    }

    /** GET/HEAD /stream/<name>: the finished file, or the .part still growing, with Range support (206/416). */
    private fun stream(s: IHTTPSession, rawName: String): Response {
        val name = safeName(rawName) ?: return bad("bad name")
        val fin = File(dir, name)
        val total = if (fin.isFile) fin.length() else Meta.read(dir, name)?.takeIf { partFile(name).isFile }
            ?: return json(Response.Status.NOT_FOUND, """{"error":"not found"}""")
        val mime = mimeOf(name)
        // NanoHTTPD 2.3.1 would send the body of a HEAD answer: give HEAD an empty body but the real Content-Length.
        val head = s.method == Method.HEAD
        fun reply(st: Response.IStatus, from: Long, to: Long): Response {
            val len = to - from + 1
            val r = if (head) newFixedLengthResponse(st, mime, java.io.ByteArrayInputStream(ByteArray(0)), 0)
                    else newFixedLengthResponse(st, mime, GrowingStream(dir, name, from, to), len)
            if (head) r.addHeader("Content-Length", len.toString())
            return r.also { it.addHeader("Accept-Ranges", "bytes") }
        }
        return when (val r = HttpRange.parse(s.headers["range"], total)) {
            HttpRange.R.Unsatisfiable -> newFixedLengthResponse(status(416), MIME_PLAINTEXT, "")
                .also { it.addHeader("Content-Range", "bytes */$total"); it.addHeader("Accept-Ranges", "bytes") }
            HttpRange.R.Full -> reply(Response.Status.OK, 0, total - 1)
            is HttpRange.R.Part -> reply(Response.Status.PARTIAL_CONTENT, r.start, r.end)
                .also { it.addHeader("Content-Range", "bytes ${r.start}-${r.end}/$total") }
        }
    }

    /** Plays a file that is still arriving, once enough is there to start (else 409 buffering). */
    private fun playIncomplete(name: String, pos: Long): Response {
        val (received, total) = progress(name) ?: return json(Response.Status.NOT_FOUND, """{"error":"not uploaded"}""")
        val needed = Progressive.bootstrapBytes(total, meters[name]?.bytesPerSec() ?: 0)
        if (received < needed)
            return json(Response.Status.CONFLICT, """{"error":"buffering","received":$received,"needed":$needed,"size":$total}""")
        Storage.markPlayed(dir, name)
        player.playStream(streamUrl(name), name, pos)
        return ok(info())
    }

    private fun mimeOf(name: String) = when (name.substringAfterLast('.', "").lowercase()) {
        "mp4", "m4v" -> "video/mp4"; "mkv" -> "video/x-matroska"; "webm" -> "video/webm"; "avi" -> "video/x-msvideo"
        "mov" -> "video/quicktime"; "ts", "m2ts", "mts" -> "video/mp2t"; "mpg", "mpeg" -> "video/mpeg"
        "mp3" -> "audio/mpeg"; "m4a" -> "audio/mp4"; "flac" -> "audio/flac"; "ogg" -> "audio/ogg"
        else -> "application/octet-stream"
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
            it.isNotEmpty() && it.length <= 200 && !it.startsWith(".") && !it.endsWith(PART) && !it.endsWith(Meta.SUFFIX) &&
                it.none { c -> c == '/' || c == '\\' || c == '\u0000' }
        }

        fun q(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\""
    }
}
