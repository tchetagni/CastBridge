package castbridge.core.chess

import castbridge.core.quiz.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** A seat in a hosted game: the token is the only secret (reconnection = join again with it). */
data class ChessSession(val code: String, val token: String, val playerId: String, val name: String, val color: String?,
                        /** Online only (play-v1): the room's identifier, to come back after a long cut with the seat token. */ val roomId: String = "")

/** Answer to a command: the host's verdict (OK, ILLEGAL, NOT_YOUR_TURN, STALE…) and the state after it. */
data class ChessAct(val result: String, val state: Map<String, Any?>?) {
    val ok: Boolean get() = result == "OK"
}

/** An HTTP error from the host, with a message fit for the screen when the host gave one. */
open class ChessTransportException(val status: Int, message: String, /** The service's stable refusal code (online play), or null. */ val reason: String? = null) : IOException(message)

/**
 * How a player's device talks to the game host. Two implementations share the same state format (see docs/CHESS.md):
 * [LanChessClient] — the TV of the house hosts the room (a phone of the house), and [ChessRelayClient] — a TV plays another TV through the
 * online service `castbridge-play` (`/play/`, docs/PLAY-PROTOCOL.md « salle game:chess »). No phone ever talks to the service.
 * Blocking calls: run them off the UI thread.
 */
interface ChessTransport {
    /** Where the games are hosted, for the screens (« la TV du salon », « Internet »). */
    val label: String

    /** Creates a game and sits in it (Internet only, by a TV: at home the TV creates the room). */
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
