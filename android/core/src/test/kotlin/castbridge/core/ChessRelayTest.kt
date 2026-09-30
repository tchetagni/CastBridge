package castbridge.core

import castbridge.core.chess.*
import castbridge.core.quiz.Json
import fi.iki.elonen.NanoHTTPD
import java.net.ServerSocket
import kotlin.test.*

/**
 * A fake of the central server's chess relay (docs/CHESS.md, « Protocole du relais »), built on the same engine:
 * it shows the protocol is implementable as documented and lets [ChessRelayClient] be tested end to end.
 */
class FakeRelay(port: Int, private val clock: () -> Long) : NanoHTTPD("127.0.0.1", port) {
    class Seat(val token: String, val id: String, val name: String, val color: Int?)
    class G(val code: String, val perMove: Int, val mode: ClockMode) {
        val seats = ArrayList<Seat>()
        var game: ChessGame? = null
        var v = 1L
    }
    val games = HashMap<String, G>()
    private var n = 0

    private fun json(code: Int, body: Any?): Response =
        newFixedLengthResponse(castbridge.core.quiz.QuizHttp.status(code), "application/json; charset=utf-8", Json.write(body))

    override fun serve(s: IHTTPSession): Response {
        val files = HashMap<String, String>()
        if (s.method == Method.POST) s.parseBody(files)
        val body = files["postData"]?.let { runCatching { Json.obj(it) }.getOrNull() } ?: emptyMap()
        val token = s.headers["authorization"]?.removePrefix("Bearer ")
        val path = s.uri.removePrefix(ChessRelayClient.API)
        if (path == "/hello") return json(200, mapOf("ok" to true, "protocol" to ChessRoom.PROTOCOL))
        if (path == "/games" && s.method == Method.POST) {
            val code = "ABC2${"%02d".format(n++)}".map { if (it.isDigit() && it < '2') 'Z' else it }.joinToString("").take(6)
            val g = G(code, MoveTimer.clamp((body["perMoveSeconds"] as Number).toInt()), ClockMode.valueOf(body["mode"] as String))
            games[code] = g
            val seat = Seat("t-$code-0", "p0", body["name"] as String, if (body["color"] == "black") Piece.BLACK else Piece.WHITE)
            g.seats += seat
            return json(201, mapOf("code" to code, "token" to seat.token, "id" to seat.id, "name" to seat.name, "color" to ChessRoom.colorKey(seat.color!!)))
        }
        val m = Regex("/games/([A-Z0-9]{6})/(\\w+)").matchEntire(path) ?: return json(404, mapOf("error" to "not found"))
        val g = games[m.groupValues[1]] ?: return json(404, mapOf("error" to "no game", "message" to "Aucune partie avec ce code."))
        val action = m.groupValues[2]
        if (action == "join") {
            (body["token"] as? String)?.let { t -> g.seats.firstOrNull { it.token == t }?.let { return json(200, seatJson(it)) } }
            val taken = g.seats.mapNotNull { it.color }
            val color = listOf(Piece.WHITE, Piece.BLACK).firstOrNull { it !in taken }
            val seat = Seat("t-${g.code}-${g.seats.size}", "p${g.seats.size}", body["name"] as String, color)
            g.seats += seat
            if (color != null && g.game == null) g.game = ChessGame(g.perMove, g.mode, now = clock())
            g.v++
            return json(200, seatJson(seat))
        }
        val me = g.seats.firstOrNull { it.token == token } ?: return json(401, mapOf("error" to "unknown player"))
        val game = g.game
        game?.tick(clock())
        fun act(r: String) = json(if (r == "ILLEGAL") 400 else if (r == "NOT_YOUR_TURN") 409 else 200, mapOf("result" to r, "state" to state(g, me)))
        return when (action) {
            "state" -> json(200, state(g, me))
            "move" -> {
                if (game == null) return act("IGNORED")
                if (me.color == null) return act("FORBIDDEN")
                val r = game.play(body["uci"] as String, me.color, clock(), (body["ply"] as Number).toInt())
                if (r == ChessGame.Play.OK) g.v++
                act(r.name)
            }
            "resign" -> { if (game != null && me.color != null && game.resign(me.color)) { g.v++; act("OK") } else act("IGNORED") }
            "draw" -> {
                val c = me.color ?: return act("FORBIDDEN")
                val ok = when (body["action"]) { "offer" -> game?.offerDraw(c); "accept" -> game?.answerDraw(c, true); else -> game?.answerDraw(c, false) } == true
                if (ok) g.v++
                act(if (ok) "OK" else "IGNORED")
            }
            "leave" -> act("OK")
            else -> json(404, mapOf("error" to "not found"))
        }
    }

    private fun seatJson(s: Seat) = mapOf("token" to s.token, "id" to s.id, "name" to s.name, "color" to s.color?.let(ChessRoom::colorKey))

    private fun state(g: G, me: Seat): Map<String, Any?> {
        val game = g.game
        val t = clock()
        fun side(c: Int) = g.seats.firstOrNull { it.color == c }?.let { mapOf("kind" to "ONLINE", "name" to it.name, "connected" to true) }
        return linkedMapOf("v" to g.v, "protocol" to 1, "stage" to (if (game == null) "LOBBY" else if (game.over) "FINISHED" else "PLAYING"),
            "code" to g.code, "white" to side(Piece.WHITE), "black" to side(Piece.BLACK),
            "me" to mapOf("id" to me.id, "name" to me.name, "color" to me.color?.let(ChessRoom::colorKey)),
            "fen" to (game?.position?.fen() ?: Position.START_FEN), "ply" to (game?.ply ?: 0), "turn" to ChessRoom.colorKey(game?.turn ?: 0),
            "san" to (game?.san ?: emptyList<String>()), "uci" to (game?.uci ?: emptyList<String>()), "lastMove" to game?.lastMove(),
            "legal" to (if (game != null && !game.over && me.color == game.turn) game.position.legalMoves().map(Move::uci) else emptyList<String>()),
            "clock" to mapOf("perMoveMs" to g.perMove * 1000L, "remainingMs" to game?.remainingMs(t), "running" to (game != null && !game.over)),
            "drawOffer" to game?.drawOffer?.let(ChessRoom::colorKey),
            "result" to game?.result?.let { mapOf("winner" to it.winner?.let(ChessRoom::colorKey), "reason" to it.reason.name, "text" to it.text) })
    }
}

class ChessRelayTest {
    private var now = 0L
    private val port = ServerSocket(0).use { it.localPort }
    private val relay = FakeRelay(port, { now }).apply { start(5000, false) }
    private val base = "http://127.0.0.1:$port"
    private val tv = ChessRelayClient(base, enabled = true)
    private val phone = ChessRelayClient(base, enabled = true)

    @AfterTest fun tearDown() { relay.stop() }

    @Test fun disabledByDefaultWithoutTouchingTheNetwork() {
        val off = ChessRelayClient("http://127.0.0.1:1")          // nothing listens there: a network call would fail differently
        assertFalse(off.enabled)
        assertFalse(off.available())
        val e = assertFailsWith<ChessTransportException> { off.create("Esaie", 30) }
        assertEquals(503, e.status); assertEquals(ChessRelayClient.DISABLED, e.message)
        assertFailsWith<ChessTransportException> { off.join("ABC234", "X") }
        assertEquals(ChessRelayClient.DEFAULT_URL, "https://bridge.sti-cm.com")
    }

    @Test fun availabilityProbe() {
        assertTrue(tv.available())
        assertFalse(ChessRelayClient("$base/absent", enabled = true).available(), "routes not there yet")
    }

    @Test fun codesAreSixUnambiguousCharacters() {
        assertEquals("ABC23K", ChessRelayClient.normalizeCode(" abc-23k "))
        assertNull(ChessRelayClient.normalizeCode("ABC12K"), "1 is not in the alphabet")
        assertNull(ChessRelayClient.normalizeCode("ABCDE"))
        assertNull(ChessRelayClient.normalizeCode("ABCD0O"))
        assertFailsWith<ChessTransportException> { phone.join("ABC", "X") }.also { assertEquals(400, it.status) }
    }

    @Test fun onlineGameThroughTheRelay() {
        val host = tv.create("Esaie (TV)", perMoveSeconds = 300, color = "white")
        assertEquals(6, host.code.length)
        assertEquals("w", host.color)
        val guest = phone.join(host.code.lowercase(), "Ami")
        assertEquals("b", guest.color)
        @Suppress("UNCHECKED_CAST") val clock = tv.state(host)["clock"] as Map<String, Any?>
        assertEquals(60_000L, clock["perMoveMs"], "the server clamps the countdown too")
        assertEquals("ILLEGAL", tv.move(host, "e2e5", 0).result)
        assertEquals("NOT_YOUR_TURN", phone.move(guest, "e7e5", 0).result)
        assertTrue(tv.move(host, "e2e4", 0).ok)
        assertEquals("STALE", tv.move(host, "e2e4", 0).result)
        assertTrue(phone.move(guest, "c7c5", 1).ok)
        // the phone drops and comes back with its token
        val back = phone.join(host.code, "Ami", guest.token)
        assertEquals("b", back.color)
        assertEquals(listOf("e4", "c5"), phone.state(back)["san"])
        assertEquals("OK", phone.draw(back, "offer").result)
        assertEquals("OK", tv.draw(host, "decline").result)
        assertEquals("OK", tv.resign(host).result)
        @Suppress("UNCHECKED_CAST") val result = phone.state(back)["result"] as Map<String, Any?>
        assertEquals("b", result["winner"]); assertEquals("RESIGNATION", result["reason"])
        assertFailsWith<ChessTransportException> { phone.state(ChessSession(host.code, "forged", "", "", null)) }.also { assertEquals(401, it.status) }
        assertFailsWith<ChessTransportException> { phone.join("ZZZZZZ", "X") }.also { assertEquals(404, it.status) }
    }

    @Test fun serverClockMakesTheLatePlayerLose() {
        val host = tv.create("TV", perMoveSeconds = 10, color = "black")
        val guest = phone.join(host.code, "Ami")
        assertEquals("w", guest.color)
        now += 10_001
        @Suppress("UNCHECKED_CAST") val result = tv.state(host)["result"] as Map<String, Any?>
        assertEquals("b", result["winner"]); assertEquals("TIMEOUT", result["reason"])
        assertEquals("IGNORED", phone.move(guest, "e2e4", 0).result.let { if (it == "OVER") "IGNORED" else it })
    }
}
