package castbridge.core.chess

import castbridge.core.quiz.Json
import castbridge.core.quiz.QuizHttp
import castbridge.core.tv.PublicRoutes
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * The chess routes, served by the TV on port 8765 without the admin PIN (room code + player token instead):
 *
 * - GET  /chess                        the mobile web page
 * - GET  /chess/api/hello              is a room open? (no code, no names)
 * - POST /chess/api/join               code, name [, token to take one's seat back] → token, id, colour
 * - GET  /chess/api/events?token=      Server-Sent Events: the state at every change (+ a ping every 15 s)
 * - GET  /chess/api/state?token=&since=&wait=   long-poll fallback (≤ 25 s)
 * - POST /chess/api/act?token=&action=&arg=&ply=   move / resign / draw / sit / stand (see [ChessRoom.act])
 * - POST /chess/api/leave?token=
 *
 * Same limits as the quiz: requests per IP, wrong codes per IP, event streams per player and in total.
 */
class ChessHttp(
    private val room: () -> ChessRoom?,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    private val streamMaxMs: Long = 10 * 60_000L,
    private val pingMs: Long = 15_000,
) : PublicRoutes {
    override val extraThreads: Int = MAX_STREAMS + 2

    private class Bucket(var tokens: Double, var at: Long)
    private class Failures(var count: Int, var since: Long)
    private val buckets = ConcurrentHashMap<String, Bucket>()
    private val badCodes = ConcurrentHashMap<String, Failures>()
    private val streams = AtomicInteger()

    override fun serve(s: NanoHTTPD.IHTTPSession): Response? {
        val path = s.uri
        if (path != "/chess" && !path.startsWith("/chess/")) return null
        val ip = s.remoteIpAddress ?: "?"
        if (!allow(ip)) return json(429, """{"error":"too many requests"}""")
        val p = s.parameters.mapValues { it.value.firstOrNull().orEmpty() }
        val get = s.method == NanoHTTPD.Method.GET
        val post = s.method == NanoHTTPD.Method.POST
        return when (path) {
            "/chess", "/chess/" -> if (get) page() else json(405, """{"error":"use GET"}""")
            "/chess/api/hello" -> hello()
            "/chess/api/join" -> if (post) join(ip, p) else json(405, """{"error":"use POST"}""")
            "/chess/api/events" -> if (get) events(p["token"]) else json(405, """{"error":"use GET"}""")
            "/chess/api/state" -> if (get) state(p) else json(405, """{"error":"use GET"}""")
            "/chess/api/act" -> if (post) act(p) else json(405, """{"error":"use POST"}""")
            "/chess/api/leave" -> if (post) { room()?.leave(p["token"]); json(200, """{"ok":true}""") } else json(405, """{"error":"use POST"}""")
            else -> json(404, """{"error":"not found"}""")
        }
    }

    private fun openRoom(): ChessRoom? = room()?.takeIf { it.stage != ChessRoom.Stage.CLOSED }

    private fun hello(): Response {
        val r = openRoom() ?: return json(200, """{"open":false,"protocol":${ChessRoom.PROTOCOL}}""")
        return json(200, """{"open":true,"protocol":${ChessRoom.PROTOCOL},"stage":"${r.stage}","players":${r.players().size},"max":${r.maxPlayers}}""")
    }

    private fun join(ip: String, p: Map<String, String>): Response {
        val r = openRoom() ?: return json(410, """{"error":"no room","message":"Aucune partie d'échecs ouverte sur la TV."}""")
        badCodes[ip]?.let { f ->
            if (clock() - f.since > CODE_WINDOW_MS) badCodes.remove(ip)
            else if (f.count >= MAX_BAD_CODES) return json(429, """{"error":"locked","message":"Trop de codes erronés : réessayez dans quelques minutes."}""")
        }
        val res = r.join(p["code"], p["name"], p["token"]?.takeIf { it.isNotEmpty() })
        return when (res.status) {
            ChessRoom.Join.OK -> {
                badCodes.remove(ip); val pl = res.player!!
                val color = r.colorOf(pl)?.let { "\"${ChessRoom.colorKey(it)}\"" } ?: "null"
                json(200, """{"token":"${pl.token}","id":"${pl.id}","name":${Json.quote(pl.name)},"color":$color}""")
            }
            ChessRoom.Join.BAD_CODE -> {
                val f = badCodes.getOrPut(ip) { Failures(0, clock()) }
                synchronized(f) { f.count++ }
                json(403, """{"error":"bad code","message":"Code de salle incorrect."}""")
            }
            ChessRoom.Join.FULL -> json(409, """{"error":"full","message":"La salle est complète (${r.maxPlayers} personnes)."}""")
            ChessRoom.Join.BAD_NAME -> json(400, """{"error":"bad name","message":"Choisissez un pseudo."}""")
            ChessRoom.Join.CLOSED -> json(410, """{"error":"closed","message":"La partie est terminée."}""")
        }
    }

    private fun state(p: Map<String, String>): Response {
        val r = openRoom() ?: return json(410, """{"error":"closed"}""")
        val pl = r.player(p["token"]) ?: return json(401, """{"error":"unknown player"}""")
        r.touch(pl)
        val since = p["since"]?.toLongOrNull() ?: 0
        val wait = (p["wait"]?.toLongOrNull() ?: 0).coerceIn(0, 25)
        if (wait > 0) r.awaitChange(since, wait * 1000)
        r.touch(pl)
        return json(200, r.viewJson(pl.token))
    }

    private fun act(p: Map<String, String>): Response {
        val r = openRoom() ?: return json(410, """{"error":"closed"}""")
        val res = r.act(p["token"], p["action"].orEmpty(), p["arg"], p["ply"]?.toIntOrNull())
        val code = when (res) {
            ChessRoom.Act.OK, ChessRoom.Act.IGNORED, ChessRoom.Act.STALE -> 200
            ChessRoom.Act.ILLEGAL, ChessRoom.Act.BAD_REQUEST -> 400
            ChessRoom.Act.NOT_YOUR_TURN, ChessRoom.Act.FORBIDDEN -> 409
            ChessRoom.Act.UNKNOWN_PLAYER -> 401
            ChessRoom.Act.CLOSED -> 410
        }
        val view = if (res == ChessRoom.Act.UNKNOWN_PLAYER) "null" else r.viewJson(p["token"])
        return json(code, """{"result":"$res","state":$view}""")
    }

    private fun events(token: String?): Response {
        val r = openRoom() ?: return json(410, """{"error":"closed"}""")
        val pl = r.player(token) ?: return json(401, """{"error":"unknown player"}""")
        if (pl.streams >= 2 || streams.get() >= MAX_STREAMS) return json(429, """{"error":"too many streams"}""")
        streams.incrementAndGet()
        r.streamOpened(pl)
        val res = NanoHTTPD.newChunkedResponse(Response.Status.OK, null, SseStream(r, pl))
        res.addHeader("Content-Type", "text/event-stream; charset=utf-8")
        res.addHeader("Cache-Control", "no-store")
        res.addHeader("X-Accel-Buffering", "no")
        return res
    }

    private inner class SseStream(private val r: ChessRoom, private val pl: ChessRoom.Player) : InputStream() {
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
                sent = r.version
                "retry: 2000\n" + event()
            } else {
                val v = r.awaitChange(sent, pingMs)
                when {
                    r.stage == ChessRoom.Stage.CLOSED -> { done = true; event() }
                    clock() - started > streamMaxMs -> { done = true; ": bye\n\n" }
                    v > sent -> { sent = v; r.touch(pl); event() }
                    else -> { r.touch(pl); ": ping\n\n" }
                }
            }
            buf = text.toByteArray(Charsets.UTF_8); pos = 0
            return true
        }

        private fun event(): String = "event: state\ndata: " + r.viewJson(pl.token) + "\n\n"

        override fun close() {
            if (closed) return
            closed = true
            streams.decrementAndGet()
            r.streamClosed(pl)
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
        NanoHTTPD.newFixedLengthResponse(QuizHttp.status(code), "application/json; charset=utf-8", body).also { it.addHeader("Cache-Control", "no-store") }

    companion object {
        const val MAX_STREAMS = 12
        const val BURST = 30.0
        const val RATE_PER_MS = 10.0 / 1000
        const val MAX_BAD_CODES = 10
        const val CODE_WINDOW_MS = 5 * 60_000L

        /** The page, with the piece shapes put in (one design for the TV, the app and the web). */
        val PAGE: String by lazy {
            (ChessHttp::class.java.getResourceAsStream("/castbridge/chess/play.html")?.use { String(it.readBytes(), Charsets.UTF_8) }
                ?: "<!doctype html><meta charset=utf-8><title>Échecs</title><p>Page des échecs indisponible.")
                .replace("/*PIECES*/null", ChessPieces.json())
        }
    }
}
