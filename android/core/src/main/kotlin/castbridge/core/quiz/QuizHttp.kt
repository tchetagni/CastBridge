package castbridge.core.quiz

import castbridge.core.quiz.online.GameAuthority
import castbridge.core.tv.PublicRoutes
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * The quiz's HTTP routes, served by the TV's [castbridge.core.tv.ReceiverServer] on port 8765 without the admin PIN:
 *
 * - GET  /quiz                     the mobile web page (static, no data)
 * - GET  /quiz/api/hello           is a room open? (no code, no names)
 * - POST /quiz/api/join            code, name [, token to take one's seat back] → token
 * - GET  /quiz/api/events?token=   Server-Sent Events: the player's view at every change (+ a ping every 15 s)
 * - GET  /quiz/api/state?token=&since=&wait=   long-poll fallback (≤ 25 s)
 * - POST /quiz/api/act?token=&action=&q=&choice=[&arg=]   commands, idempotent (see [QuizRoom.act])
 * - POST /quiz/api/leave?token=
 *
 * Limits: requests per IP (token bucket), wrong room codes per IP, event streams per player and in total.
 *
 * w20-05a: the same routes, codes and JSON work over a [GameAuthority] too (the TV relaying its phones into an Internet game): see [AuthoritySource].
 */
class QuizHttp private constructor(
    private val backend: () -> Backend?,
    private val clock: () -> Long,
    /** Longest a single event stream stays open (the browser reconnects by itself). */
    private val streamMaxMs: Long,
    private val pingMs: Long,
    @Suppress("UNUSED_PARAMETER") marker: Unit,
) : PublicRoutes {
    /** The room of today (behaviour unchanged). */
    constructor(
        room: () -> QuizRoom?,
        clock: () -> Long = { System.nanoTime() / 1_000_000 },
        /** Longest a single event stream stays open (the browser reconnects by itself). */
        streamMaxMs: Long = 10 * 60_000L,
        pingMs: Long = 15_000,
    ) : this({ room()?.let { RoomBackend(it) } }, clock, streamMaxMs, pingMs, Unit)

    /** The current authority, or null when no game is open (a distinct type: `() -> QuizRoom?` and `() -> GameAuthority?` have the same JVM signature). */
    fun interface AuthoritySource { fun current(): GameAuthority? }

    /** w20-05a: the same routes over a [GameAuthority] (a [castbridge.core.quiz.online.RelayAuthority] on the TV, or a `LocalAuthority`). */
    constructor(
        authority: AuthoritySource,
        clock: () -> Long = { System.nanoTime() / 1_000_000 },
        streamMaxMs: Long = 10 * 60_000L,
        pingMs: Long = 15_000,
    ) : this({ authority.current()?.let { AuthorityBackend(it) } }, clock, streamMaxMs, pingMs, Unit)

    /** What the routes need from a game: implemented over a [QuizRoom] (as always) or over a [GameAuthority]. */
    internal interface Backend {
        val closed: Boolean
        fun helloJson(): String
        fun maxPlayers(): Int
        fun join(code: String?, name: String?, token: String?, device: String?): QuizRoom.JoinResult
        fun knows(token: String?): Boolean
        fun touch(token: String?)
        fun act(token: String?, action: String, q: String?, choice: Int?, arg: String?): QuizRoom.Act
        fun viewJson(token: String?): String
        fun awaitChange(since: Long, timeoutMs: Long): Long
        fun version(): Long
        fun leave(token: String?)
        fun streams(token: String?): Int
        fun streamOpened(token: String?)
        fun streamClosed(token: String?)
    }

    private class RoomBackend(val r: QuizRoom) : Backend {
        override val closed get() = r.stage == QuizRoom.Stage.CLOSED
        override fun helloJson() = if (closed) """{"open":false}""" else
            """{"open":true,"stage":"${r.stage}","mode":"${r.mode}","players":${r.players().size},"max":${r.maxPlayers}}"""
        override fun maxPlayers() = r.maxPlayers
        override fun join(code: String?, name: String?, token: String?, device: String?) = r.join(code, name, token, device)
        override fun knows(token: String?) = r.player(token) != null
        override fun touch(token: String?) { r.player(token)?.let { r.touch(it) } }
        override fun act(token: String?, action: String, q: String?, choice: Int?, arg: String?) = r.act(token, action, q, choice, arg)
        override fun viewJson(token: String?) = r.viewJson(token)
        override fun awaitChange(since: Long, timeoutMs: Long) = r.awaitChange(since, timeoutMs)
        override fun version() = r.version
        override fun leave(token: String?) { r.leave(token) }
        override fun streams(token: String?) = r.player(token)?.streams ?: 0
        override fun streamOpened(token: String?) { r.player(token)?.let { r.streamOpened(it) } }
        override fun streamClosed(token: String?) { r.player(token)?.let { r.streamClosed(it) } }
    }

    private class AuthorityBackend(val a: GameAuthority) : Backend {
        private val open = ConcurrentHashMap<String, AtomicInteger>()
        override val closed get() = a.closed()
        override fun helloJson(): String {
            if (closed) return """{"open":false}"""
            val v = a.view(null)
            return """{"open":true,"stage":"${v["stage"]}","mode":"${v["mode"]}","players":${(v["players"] as? List<*>)?.size ?: 0},"max":${maxPlayers()}}"""
        }
        override fun maxPlayers() = (a.view(null)["maxPlayers"] as? Number)?.toInt() ?: 8
        override fun join(code: String?, name: String?, token: String?, device: String?) = a.join(code, name, token, device)
        override fun knows(token: String?) = a.knows(token)
        override fun touch(token: String?) = a.touch(token)
        override fun act(token: String?, action: String, q: String?, choice: Int?, arg: String?) = a.act(token, action, q, choice, arg)
        override fun viewJson(token: String?) = Json.write(a.view(token))
        override fun awaitChange(since: Long, timeoutMs: Long) = a.awaitChange(since, timeoutMs)
        override fun version() = a.awaitChange(-1L, 0L)
        override fun leave(token: String?) { a.leave(token) }
        override fun streams(token: String?) = token?.let { open[it]?.get() } ?: 0
        override fun streamOpened(token: String?) { token?.let { open.getOrPut(it) { AtomicInteger() }.incrementAndGet() }; a.touch(token) }
        override fun streamClosed(token: String?) { token?.let { open[it]?.decrementAndGet() }; a.touch(token) }
    }

    override val extraThreads: Int = MAX_STREAMS + 2

    private class Bucket(var tokens: Double, var at: Long)
    private class Failures(var count: Int, var since: Long)
    private val buckets = ConcurrentHashMap<String, Bucket>()
    private val badCodes = ConcurrentHashMap<String, Failures>()
    private val streams = AtomicInteger()

    override fun serve(s: NanoHTTPD.IHTTPSession): Response? {
        val path = s.uri
        if (path != "/quiz" && !path.startsWith("/quiz/")) return null
        val ip = s.remoteIpAddress ?: "?"
        if (!allow(ip)) return json(429, """{"error":"too many requests"}""")
        val p = s.parameters.mapValues { it.value.firstOrNull().orEmpty() }
        val get = s.method == NanoHTTPD.Method.GET
        val post = s.method == NanoHTTPD.Method.POST
        return when (path) {
            "/quiz", "/quiz/" -> if (get) page() else json(405, """{"error":"use GET"}""")
            "/quiz/api/hello" -> hello()
            "/quiz/api/join" -> if (post) join(ip, p) else json(405, """{"error":"use POST"}""")
            "/quiz/api/events" -> if (get) events(p["token"]) else json(405, """{"error":"use GET"}""")
            "/quiz/api/state" -> if (get) state(p) else json(405, """{"error":"use GET"}""")
            "/quiz/api/act" -> if (post) act(p) else json(405, """{"error":"use POST"}""")
            "/quiz/api/leave" -> if (post) { backend()?.leave(p["token"]); json(200, """{"ok":true}""") } else json(405, """{"error":"use POST"}""")
            else -> json(404, """{"error":"not found"}""")
        }
    }

    private fun hello(): Response {
        val b = backend()
        return json(200, if (b == null || b.closed) """{"open":false}""" else b.helloJson())
    }

    private fun join(ip: String, p: Map<String, String>): Response {
        val r = openBackend() ?: return json(410, """{"error":"no room","message":"Aucune partie ouverte sur la TV."}""")
        badCodes[ip]?.let { f ->
            if (clock() - f.since > CODE_WINDOW_MS) badCodes.remove(ip)
            else if (f.count >= MAX_BAD_CODES) return json(429, """{"error":"locked","message":"Trop de codes erronés : réessayez dans quelques minutes."}""")
        }
        val res = r.join(p["code"], p["name"], p["token"]?.takeIf { it.isNotEmpty() }, p["dev"])
        return when (res.status) {
            QuizRoom.Join.OK -> { badCodes.remove(ip); val pl = res.player!!
                json(200, """{"token":"${pl.token}","id":"${pl.id}","name":${Json.quote(pl.name)}}""") }
            QuizRoom.Join.BAD_CODE -> {
                val f = badCodes.getOrPut(ip) { Failures(0, clock()) }
                synchronized(f) { f.count++ }
                json(403, """{"error":"bad code","message":"Code de salle incorrect."}""")
            }
            QuizRoom.Join.FULL -> json(409, """{"error":"full","message":"La salle est complète (${r.maxPlayers()} joueurs)."}""")
            QuizRoom.Join.BAD_NAME -> json(400, """{"error":"bad name","message":"Choisissez un pseudo."}""")
            QuizRoom.Join.CLOSED -> json(410, """{"error":"closed","message":"La partie est terminée."}""")
        }
    }

    private fun openBackend(): Backend? = backend()?.takeIf { !it.closed }

    private fun state(p: Map<String, String>): Response {
        val r = openBackend() ?: return json(410, """{"error":"closed"}""")
        if (!r.knows(p["token"])) return json(401, """{"error":"unknown player"}""")
        r.touch(p["token"])
        val since = p["since"]?.toLongOrNull() ?: 0
        val wait = (p["wait"]?.toLongOrNull() ?: 0).coerceIn(0, 25)
        if (wait > 0) r.awaitChange(since, wait * 1000)
        r.touch(p["token"])
        return json(200, r.viewJson(p["token"]))
    }

    private fun act(p: Map<String, String>): Response {
        val r = openBackend() ?: return json(410, """{"error":"closed"}""")
        val res = r.act(p["token"], p["action"].orEmpty(), p["q"], p["choice"]?.toIntOrNull(), p["arg"])
        val code = when (res) {
            QuizRoom.Act.OK, QuizRoom.Act.IGNORED -> 200
            QuizRoom.Act.FORBIDDEN -> 409
            QuizRoom.Act.BAD_REQUEST -> 400
            QuizRoom.Act.UNKNOWN_PLAYER -> 401
            QuizRoom.Act.CLOSED -> 410
        }
        val view = if (res == QuizRoom.Act.UNKNOWN_PLAYER) "null" else r.viewJson(p["token"])
        val why = if (res == QuizRoom.Act.FORBIDDEN && (p["action"] == "boost" || p["action"] == "declineBoost")) ",\"error\":\"réservé à la TV\"" else ""
        return json(code, """{"result":"$res"$why,"state":$view}""")
    }

    private fun events(token: String?): Response {
        val r = openBackend() ?: return json(410, """{"error":"closed"}""")
        if (!r.knows(token)) return json(401, """{"error":"unknown player"}""")
        if (r.streams(token) >= 2 || streams.get() >= MAX_STREAMS) return json(429, """{"error":"too many streams"}""")
        streams.incrementAndGet()
        r.streamOpened(token)
        // mime type null + explicit header: NanoHTTPD would gzip any "text/" body (buffering the events).
        val res = NanoHTTPD.newChunkedResponse(Response.Status.OK, null, SseStream(r, token))
        res.addHeader("Content-Type", "text/event-stream; charset=utf-8")
        res.addHeader("Cache-Control", "no-store")
        res.addHeader("X-Accel-Buffering", "no")
        return res
    }

    /** Emits the player's view as an SSE "state" event at every room change, a comment line as keep-alive. */
    private inner class SseStream(private val r: Backend, private val token: String?) : InputStream() {
        private var buf = ByteArray(0)
        private var pos = 0
        private var sent = -1L
        private var done = false
        private var closed = false
        private val started = clock()

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) <= 0) -1 else one[0].toInt() and 0xff
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (pos >= buf.size && !fill()) return -1
            val n = minOf(len, buf.size - pos)
            System.arraycopy(buf, pos, b, off, n); pos += n
            return n
        }

        private fun fill(): Boolean {
            if (done || closed) return false
            val text = if (sent < 0) {
                sent = r.version()
                "retry: 2000\n" + event()
            } else {
                val v = r.awaitChange(sent, pingMs)
                when {
                    r.closed -> { done = true; event() }
                    clock() - started > streamMaxMs -> { done = true; ": bye\n\n" }
                    v > sent -> { sent = v; r.touch(token); event() }
                    else -> { r.touch(token); ": ping\n\n" }
                }
            }
            buf = text.toByteArray(Charsets.UTF_8); pos = 0
            return true
        }

        private fun event(): String = "event: state\ndata: " + r.viewJson(token) + "\n\n"

        override fun close() {
            if (closed) return
            closed = true
            streams.decrementAndGet()
            r.streamClosed(token)
        }
    }

    private fun allow(ip: String): Boolean {
        val now = clock()
        if (buckets.size > 256) buckets.entries.removeIf { now - it.value.at > 60_000 }
        val b = buckets.getOrPut(ip) { Bucket(BURST, now) }
        return synchronized(b) {
            b.tokens = minOf(BURST, b.tokens + (now - b.at) * RATE_PER_MS); b.at = now
            if (b.tokens < 1) false else { b.tokens -= 1; true }
        }
    }

    private fun page(): Response = NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", PAGE)
        .also { it.addHeader("Cache-Control", "no-store") }

    private fun json(code: Int, body: String): Response =
        NanoHTTPD.newFixedLengthResponse(status(code), "application/json; charset=utf-8", body).also { it.addHeader("Cache-Control", "no-store") }

    companion object {
        /** Event streams open at once (8 players + a few reconnecting). */
        const val MAX_STREAMS = 12
        const val BURST = 30.0
        const val RATE_PER_MS = 10.0 / 1000        // 10 requests per second sustained per IP
        const val MAX_BAD_CODES = 10
        const val CODE_WINDOW_MS = 5 * 60_000L

        private val PAGE: String by lazy {
            QuizHttp::class.java.getResourceAsStream("/castbridge/quiz/play.html")?.use { String(it.readBytes(), Charsets.UTF_8) }
                ?: "<!doctype html><meta charset=utf-8><title>Quiz</title><p>Page du quiz indisponible."
        }

        fun status(code: Int): Response.IStatus = Response.Status.lookup(code) ?: object : Response.IStatus {
            override fun getDescription() = "$code " + when (code) { 429 -> "Too Many Requests"; 410 -> "Gone"; else -> "Status" }
            override fun getRequestStatus() = code
        }
    }
}
