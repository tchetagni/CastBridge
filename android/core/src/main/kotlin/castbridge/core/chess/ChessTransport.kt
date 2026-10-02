package castbridge.core.chess

import castbridge.core.quiz.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** A seat in a hosted game: the token is the only secret (reconnection = join again with it). */
data class ChessSession(val code: String, val token: String, val playerId: String, val name: String, val color: String?)

/** Answer to a command: the host's verdict (OK, ILLEGAL, NOT_YOUR_TURN, STALE…) and the state after it. */
data class ChessAct(val result: String, val state: Map<String, Any?>?) {
    val ok: Boolean get() = result == "OK"
}

/** An HTTP error from the host, with a message fit for the screen when the host gave one. */
class ChessTransportException(val status: Int, message: String) : IOException(message)

/**
 * How a player's device talks to the game host. Two implementations share the same state format (see docs/CHESS.md):
 * [LanChessClient] — the TV of the house hosts the room (fully working), and [ChessRelayClient] — the project's
 * server relays a game between two places on the Internet (enabled by a flag once the server has the routes).
 * Blocking calls: run them off the UI thread.
 */
interface ChessTransport {
    /** Where the games are hosted, for the screens (« la TV du salon », « Internet »). */
    val label: String

    /** Creates a game and sits in it (Internet only: at home the TV creates the room). */
    fun create(name: String, perMoveSeconds: Int, color: String = "random", mode: ClockMode = ClockMode.COMPETITION): ChessSession =
        throw ChessTransportException(405, "Ici, c'est la TV qui ouvre la partie.")

    /** Joins with the code shown by the host; [token] takes one's seat back after a disconnection. */
    fun join(code: String, name: String, token: String? = null): ChessSession

    /** The state; with [waitSeconds] > 0, waits (long-poll) until its version exceeds [since]. */
    fun state(s: ChessSession, since: Long = 0, waitSeconds: Int = 0): Map<String, Any?>

    /** Sends a move in UCI (e2e4, e7e8q) answering half-move number [ply]: a late duplicate is refused as STALE. */
    fun move(s: ChessSession, uci: String, ply: Int): ChessAct
    fun resign(s: ChessSession): ChessAct
    /** [action] = offer, accept or decline. */
    fun draw(s: ChessSession, action: String): ChessAct
    fun leave(s: ChessSession)
}

/** Tiny blocking HTTP/JSON helper (HttpURLConnection: the same on Android and on the JVM of the tests). */
internal object HttpJson {
    fun call(method: String, url: String, body: String? = null, headers: Map<String, String> = emptyMap(),
             readTimeoutMs: Int = 10_000, connectTimeoutMs: Int = 5_000): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = connectTimeoutMs; c.readTimeout = readTimeoutMs
            c.setRequestProperty("Accept", "application/json")
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            } else if (method == "POST") { c.doOutput = true; c.setFixedLengthStreamingMode(0); c.outputStream.close() }
            val code = c.responseCode
            val text = (if (code >= 400) c.errorStream else c.inputStream)?.use { String(it.readBytes(), Charsets.UTF_8) }.orEmpty()
            return code to text
        } finally { c.disconnect() }
    }

    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    /** Parses a JSON object answer, or throws [ChessTransportException] with the host's « message » for an error status. */
    fun obj(r: Pair<Int, String>, accept: Set<Int> = setOf(200, 201)): Map<String, Any?> {
        val (code, text) = r
        val j = runCatching { Json.obj(text) }.getOrNull()
        if (code !in accept) throw ChessTransportException(code, (j?.get("message") ?: j?.get("error"))?.toString() ?: "Erreur $code")
        return j ?: throw ChessTransportException(code, "Réponse illisible")
    }

    @Suppress("UNCHECKED_CAST")
    fun act(r: Pair<Int, String>): ChessAct {
        val j = runCatching { Json.obj(r.second) }.getOrNull() ?: throw ChessTransportException(r.first, "Réponse illisible")
        val result = j["result"] as? String ?: throw ChessTransportException(r.first, (j["message"] ?: j["error"] ?: "Erreur ${r.first}").toString())
        return ChessAct(result, j["state"] as? Map<String, Any?>)
    }

    fun session(code: String, j: Map<String, Any?>) = ChessSession(code, j["token"] as? String ?: throw ChessTransportException(500, "jeton absent"),
        j["id"] as? String ?: "", j["name"] as? String ?: "", j["color"] as? String)
}

/** The TV of the house hosts the game: routes /chess/api/... of [base] (e.g. http://192.168.1.20:8765). */
class LanChessClient(private val base: String) : ChessTransport {
    override val label = "la TV"
    private fun u(path: String, q: Map<String, Any?>) =
        base.trimEnd('/') + path + q.entries.filter { it.value != null }.joinToString("&", "?") { "${it.key}=${HttpJson.enc(it.value.toString())}" }

    override fun join(code: String, name: String, token: String?): ChessSession =
        HttpJson.session(code, HttpJson.obj(HttpJson.call("POST", u("/chess/api/join", mapOf("code" to code, "name" to name, "token" to token)))))

    override fun state(s: ChessSession, since: Long, waitSeconds: Int): Map<String, Any?> =
        HttpJson.obj(HttpJson.call("GET", u("/chess/api/state", mapOf("token" to s.token, "since" to since, "wait" to waitSeconds.coerceIn(0, 25))),
            readTimeoutMs = (waitSeconds.coerceIn(0, 25) + 10) * 1000))

    private fun act(s: ChessSession, action: String, arg: String? = null, ply: Int? = null) =
        HttpJson.act(HttpJson.call("POST", u("/chess/api/act", mapOf("token" to s.token, "action" to action, "arg" to arg, "ply" to ply))))

    override fun move(s: ChessSession, uci: String, ply: Int) = act(s, "move", uci, ply)
    /** Before the game: take the white (w) or black (b) phone seat, or null to watch. */
    fun seat(s: ChessSession, color: String?) = if (color == null) act(s, "stand") else act(s, "sit", color)
    override fun resign(s: ChessSession) = act(s, "resign")
    override fun draw(s: ChessSession, action: String) = act(s, "draw", action)
    override fun leave(s: ChessSession) { runCatching { HttpJson.call("POST", u("/chess/api/leave", mapOf("token" to s.token))) } }
}

/**
 * Internet play through the project's server (relay): see docs/CHESS.md « Protocole du relais ». The server creates
 * games with a 6-character code, checks every move with its own engine and keeps the clock. Disabled ([enabled] =
 * false) until the server exposes the routes: every call then fails with a clear message and no network access.
 */
class ChessRelayClient(
    private val base: String = DEFAULT_URL,
    val enabled: Boolean = false,
    /** Connect timeout of the availability probe (tests lower it so they never wait for the 5 s default). */
    private val probeConnectTimeoutMs: Int = 5_000,
) : ChessTransport {
    override val label = "Internet"
    private fun u(path: String) = base.trimEnd('/') + API + path
    private fun auth(s: ChessSession) = mapOf("Authorization" to "Bearer ${s.token}")
    private fun on() { if (!enabled) throw ChessTransportException(503, DISABLED) }

    /** Does the server answer the chess protocol? (false when disabled, offline, or routes missing). */
    fun available(): Boolean = enabled && runCatching {
        val (c, t) = HttpJson.call("GET", u("/hello"), readTimeoutMs = 4_000, connectTimeoutMs = probeConnectTimeoutMs)
        c == 200 && (Json.obj(t)["protocol"] as? Number)?.toInt() == ChessRoom.PROTOCOL
    }.getOrDefault(false)

    override fun create(name: String, perMoveSeconds: Int, color: String, mode: ClockMode): ChessSession {
        on()
        val body = Json.write(linkedMapOf("name" to name, "perMoveSeconds" to MoveTimer.clamp(perMoveSeconds), "color" to color, "mode" to mode.name))
        val j = HttpJson.obj(HttpJson.call("POST", u("/games"), body))
        return HttpJson.session(j["code"] as? String ?: throw ChessTransportException(500, "code absent"), j)
    }

    override fun join(code: String, name: String, token: String?): ChessSession {
        on()
        val c = normalizeCode(code) ?: throw ChessTransportException(400, "Le code en ligne a 6 caractères (lettres et chiffres).")
        val body = Json.write(linkedMapOf("name" to name, "token" to token))
        return HttpJson.session(c, HttpJson.obj(HttpJson.call("POST", u("/games/$c/join"), body)))
    }

    override fun state(s: ChessSession, since: Long, waitSeconds: Int): Map<String, Any?> {
        on()
        val w = waitSeconds.coerceIn(0, 25)
        return HttpJson.obj(HttpJson.call("GET", u("/games/${s.code}/state?since=$since&wait=$w"), headers = auth(s), readTimeoutMs = (w + 10) * 1000))
    }

    private fun post(s: ChessSession, what: String, body: Map<String, Any?> = emptyMap()): ChessAct {
        on()
        return HttpJson.act(HttpJson.call("POST", u("/games/${s.code}/$what"), Json.write(body), auth(s)))
    }

    override fun move(s: ChessSession, uci: String, ply: Int) = post(s, "move", linkedMapOf("uci" to uci, "ply" to ply))
    override fun resign(s: ChessSession) = post(s, "resign")
    override fun draw(s: ChessSession, action: String) = post(s, "draw", linkedMapOf("action" to action))
    override fun leave(s: ChessSession) { if (enabled) runCatching { post(s, "leave") } }

    companion object {
        const val DEFAULT_URL = "https://bridge.sti-cm.com"
        const val API = "/api/chess/v1"
        const val DISABLED = "Le jeu en ligne n'est pas encore ouvert sur le serveur CastBridge."
        /** Code alphabet: no 0/O, 1/I/L (read aloud or typed on a phone without mistakes). */
        const val CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

        /** « abc 23k » → « ABC23K », or null if it is not 6 characters of the alphabet. */
        fun normalizeCode(code: String): String? = code.uppercase().filter { !it.isWhitespace() && it != '-' }
            .takeIf { it.length == 6 && it.all { c -> c in CODE_ALPHABET } }
    }
}
