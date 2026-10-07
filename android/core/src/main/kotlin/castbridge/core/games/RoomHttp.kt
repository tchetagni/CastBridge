package castbridge.core.games

import castbridge.core.quiz.Json
import castbridge.core.quiz.QuizHttp
import castbridge.core.tv.PublicRoutes
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Les limites de débit d'un ensemble de routes de salle : requêtes par adresse (jetons qui se remplissent), codes faux par adresse sur 5 minutes, flux d'évènements au total. Une instance
 * peut être PARTAGÉE par plusieurs [RoomHttp] (les jeux de `/jeux/…` se partagent les mêmes limites).
 */
class RoomLimits(private val clock: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private class Bucket(var tokens: Double, var at: Long)
    class Failures(var count: Int, var since: Long)
    private val buckets = ConcurrentHashMap<String, Bucket>()
    private val badCodes = ConcurrentHashMap<String, Failures>()
    val streams = AtomicInteger()

    /** Une requête de plus de [ip] est-elle permise ? */
    fun allow(ip: String): Boolean {
        val now = clock()
        if (buckets.size > 256) buckets.entries.removeIf { now - it.value.at > 60_000 }
        val b = buckets.getOrPut(ip) { Bucket(RoomHttp.BURST, now) }
        return synchronized(b) {
            b.tokens = minOf(RoomHttp.BURST, b.tokens + (now - b.at) * RoomHttp.RATE_PER_MS); b.at = now
            if (b.tokens < 1) false else { b.tokens -= 1; true }
        }
    }

    /** [ip] a-t-il épuisé ses essais de code (10 codes faux en 5 minutes) ? */
    fun lockedOut(ip: String): Boolean {
        val f = badCodes[ip] ?: return false
        if (clock() - f.since > RoomHttp.CODE_WINDOW_MS) { badCodes.remove(ip); return false }
        return f.count >= RoomHttp.MAX_BAD_CODES
    }

    fun badCode(ip: String) { val f = badCodes.getOrPut(ip) { Failures(0, clock()) }; synchronized(f) { f.count++ } }
    fun goodCode(ip: String) { badCodes.remove(ip) }
}

/**
 * Les routes publiques d'une salle de la TV, servies sur le port 8765 SANS le code PIN d'administration (le code de la salle et le jeton du joueur tiennent lieu d'authentification) :
 *
 * - `GET  <prefix>`                      la page web mobile
 * - `GET  <prefix>/api/hello`            une salle est-elle ouverte ? (ni code ni noms)
 * - `POST <prefix>/api/join`             code, name [, token pour reprendre sa place] → token, id + ce que dit la salle (couleur, place)
 * - `GET  <prefix>/api/events?token=`    Server-Sent Events : l'état à chaque changement (+ un ping toutes les 15 s)
 * - `GET  <prefix>/api/state?token=&since=&wait=`   repli long-poll (≤ 25 s)
 * - `POST <prefix>/api/act?token=&action=&arg=&<seq>=`   une commande (voir `ChessRoom.act`, `RulesRoom.act`)
 * - `POST <prefix>/api/leave?token=`
 *
 * Les mêmes limites que le Quiz : requêtes par adresse, codes faux par adresse, flux par joueur et au total. UNE seule implémentation pour les échecs (`/chess`) et pour tous les jeux
 * (`/jeux/<id>`) : ni SSE ni limites recopiés par jeu.
 */
class RoomHttp(
    /** `/chess` ou `/jeux/<id>`. */
    private val prefix: String,
    private val room: () -> RoomEndpoint?,
    private val page: () -> String,
    private val protocol: Int,
    /** Message d'écran quand personne n'a ouvert de salle (« Aucune partie d'échecs ouverte sur la TV. »). */
    private val noRoomMessage: String,
    /** Membres JSON ajoutés à la réponse de `hello`, après `protocol` (par exemple `,"game":"bataille"`). */
    private val helloExtra: String = "",
    /** Nom du paramètre qui porte le numéro de coup : `ply` pour les échecs, `seq` pour les jeux à règles. */
    private val seqParam: String = "seq",
    private val limits: RoomLimits = RoomLimits(),
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    private val streamMaxMs: Long = 10 * 60_000L,
    private val pingMs: Long = 15_000,
) : PublicRoutes {
    override val extraThreads: Int = MAX_STREAMS + 2

    override fun serve(s: NanoHTTPD.IHTTPSession): Response? {
        val path = s.uri
        if (path != prefix && !path.startsWith("$prefix/")) return null
        val ip = s.remoteIpAddress ?: "?"
        if (!limits.allow(ip)) return json(429, """{"error":"too many requests"}""")
        val p = s.parameters.mapValues { it.value.firstOrNull().orEmpty() }
        val get = s.method == NanoHTTPD.Method.GET
        val post = s.method == NanoHTTPD.Method.POST
        return when (path) {
            prefix, "$prefix/" -> if (get) page() else json(405, """{"error":"use GET"}""")
            "$prefix/api/hello" -> hello()
            "$prefix/api/join" -> if (post) join(ip, p) else json(405, """{"error":"use POST"}""")
            "$prefix/api/events" -> if (get) events(p["token"]) else json(405, """{"error":"use GET"}""")
            "$prefix/api/state" -> if (get) state(p) else json(405, """{"error":"use GET"}""")
            "$prefix/api/act" -> if (post) act(p) else json(405, """{"error":"use POST"}""")
            "$prefix/api/leave" -> if (post) { room()?.leave(p["token"]); json(200, """{"ok":true}""") } else json(405, """{"error":"use POST"}""")
            else -> json(404, """{"error":"not found"}""")
        }
    }

    private fun openRoom(): RoomEndpoint? = room()?.takeIf { it.roomStage != RoomStage.CLOSED }

    private fun hello(): Response {
        val r = openRoom() ?: return json(200, """{"open":false,"protocol":$protocol$helloExtra}""")
        return json(200, """{"open":true,"protocol":$protocol$helloExtra,"stage":"${r.roomStage}","players":${r.players().size},"max":${r.maxPlayers}}""")
    }

    private fun join(ip: String, p: Map<String, String>): Response {
        val r = openRoom() ?: return json(410, """{"error":"no room","message":${Json.quote(noRoomMessage)}}""")
        if (limits.lockedOut(ip)) return json(429, """{"error":"locked","message":"Trop de codes erronés : réessayez dans quelques minutes."}""")
        val res = r.joinRoom(p["code"], p["name"], p["token"]?.takeIf { it.isNotEmpty() })
        return when (res.status) {
            JoinStatus.OK -> {
                limits.goodCode(ip); val pl = res.player!!
                json(200, """{"token":"${pl.token}","id":"${pl.id}","name":${Json.quote(pl.name)}${r.joinReply(pl)}}""")
            }
            JoinStatus.BAD_CODE -> { limits.badCode(ip); json(403, """{"error":"bad code","message":"Code de salle incorrect."}""") }
            JoinStatus.FULL -> json(409, """{"error":"full","message":"La salle est complète (${r.maxPlayers} personnes)."}""")
            JoinStatus.BAD_NAME -> json(400, """{"error":"bad name","message":"Choisissez un pseudo."}""")
            JoinStatus.CLOSED -> json(410, """{"error":"closed","message":"La partie est terminée."}""")
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
        val res = r.httpAct(p["token"], p["action"].orEmpty(), p["arg"], p[seqParam]?.toIntOrNull())
        val view = if (res == ActResult.UNKNOWN_PLAYER) "null" else r.viewJson(p["token"])
        return json(res.httpStatus, """{"result":"$res","state":$view}""")
    }

    private fun events(token: String?): Response {
        val r = openRoom() ?: return json(410, """{"error":"closed"}""")
        val pl = r.player(token) ?: return json(401, """{"error":"unknown player"}""")
        if (pl.streams >= 2 || limits.streams.get() >= MAX_STREAMS) return json(429, """{"error":"too many streams"}""")
        limits.streams.incrementAndGet()
        r.streamOpened(pl)
        val res = NanoHTTPD.newChunkedResponse(Response.Status.OK, null, SseStream(r, pl))
        res.addHeader("Content-Type", "text/event-stream; charset=utf-8")
        res.addHeader("Cache-Control", "no-store")
        res.addHeader("X-Accel-Buffering", "no")
        return res
    }

    private inner class SseStream(private val r: RoomEndpoint, private val pl: RoomPlayer) : InputStream() {
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
                    r.roomStage == RoomStage.CLOSED -> { done = true; event() }
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
            limits.streams.decrementAndGet()
            r.streamClosed(pl)
        }
    }

    private fun page(): Response = NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", page.invoke())
        .also { it.addHeader("Cache-Control", "no-store") }

    private fun json(code: Int, body: String): Response =
        NanoHTTPD.newFixedLengthResponse(QuizHttp.status(code), "application/json; charset=utf-8", body).also { it.addHeader("Cache-Control", "no-store") }

    companion object {
        const val MAX_STREAMS = 12
        const val BURST = 30.0
        const val RATE_PER_MS = 10.0 / 1000
        const val MAX_BAD_CODES = 10
        const val CODE_WINDOW_MS = 5 * 60_000L
    }
}
