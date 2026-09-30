package castbridge.core

import castbridge.core.chess.*
import castbridge.core.quiz.Json
import castbridge.core.quiz.QuizHttp
import castbridge.core.quiz.QuizRoom
import castbridge.core.quiz.EmbeddedQuestionSource
import castbridge.core.tv.CombinedRoutes
import castbridge.core.tv.ReceiverServer
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import java.util.concurrent.Executor
import kotlin.test.*

/** The move countdown and the game state machine (fake clock). */
class ChessClockTest {
    @Test fun countdownIsClampedToAtMostOneMinute() {
        assertEquals(60, MoveTimer.clamp(600))
        assertEquals(60, MoveTimer.clamp(61))
        assertEquals(10, MoveTimer.clamp(0))
        assertEquals(10, MoveTimer.clamp(-5))
        assertEquals(45, MoveTimer.clamp(45))
        val g = ChessGame(perMoveSeconds = 3_600, now = 0)
        assertEquals(60_000, g.perMoveMs, "an hour asked, one minute given")
        assertEquals(60_000, g.remainingMs(-10_000), "never more than 60 s left, even with a clock going backwards")
        assertEquals(listOf(20, 30, 45, 60, 10), generateSequence(10) { MoveTimer.next(it, 1) }.drop(1).take(5).toList())
        assertEquals(60, MoveTimer.next(10, -1))
    }

    @Test fun competitionTimeoutLosesTheGame() {
        val g = ChessGame(perMoveSeconds = 10, now = 0)
        assertEquals(ChessGame.Play.OK, g.play("e2e4", Piece.WHITE, 9_000))
        assertEquals(10_000, g.remainingMs(9_000), "the countdown restarts at every move")
        assertFalse(g.tick(18_999))
        assertTrue(g.tick(19_000))
        assertEquals(GameResult.win(Piece.WHITE, EndReason.TIMEOUT), g.result)
        assertEquals(ChessGame.Play.OVER, g.play("e7e5", Piece.BLACK, 19_001))
    }

    @Test fun aMoveArrivingAfterTheDeadlineIsRefused() {
        val g = ChessGame(perMoveSeconds = 10, now = 0)
        assertEquals(ChessGame.Play.OVER, g.play("e2e4", Piece.WHITE, 10_500), "too late: the host's clock decides")
        assertEquals(Outcome.BLACK_WINS, g.result?.outcome)
    }

    @Test fun timeoutAgainstABareKingIsADraw() {
        val g = ChessGame(perMoveSeconds = 20, startFen = "4k3/8/8/8/8/8/8/R3K3 w - - 0 1", now = 0)
        g.tick(20_000)
        assertEquals(EndReason.TIMEOUT_DRAW, g.result?.reason)
    }

    @Test fun practiceTimeoutPlaysALegalMoveAndGoesOn() {
        val g = ChessGame(perMoveSeconds = 10, mode = ClockMode.PRACTICE, now = 0, random = java.util.Random(3))
        assertTrue(g.tick(10_000))
        assertNull(g.result)
        assertEquals(1, g.ply)
        assertEquals(setOf(0), g.autoPlayed)
        assertTrue(Position.start().parseUci(g.uci[0]) != 0, "a legal move")
        assertEquals(Piece.BLACK, g.turn)
    }

    @Test fun movesAreValidatedDrawOffersAndUndo() {
        val g = ChessGame(now = 0)
        assertEquals(ChessGame.Play.NOT_YOUR_TURN, g.play("e7e5", Piece.BLACK, 1))
        assertEquals(ChessGame.Play.ILLEGAL, g.play("e2e5", Piece.WHITE, 1))
        assertEquals(ChessGame.Play.ILLEGAL, g.play("hello", Piece.WHITE, 1))
        assertEquals(ChessGame.Play.STALE, g.play("e2e4", Piece.WHITE, 1, expectedPly = 3))
        assertEquals(ChessGame.Play.OK, g.play("e2e4", Piece.WHITE, 1, expectedPly = 0))
        assertEquals(ChessGame.Play.STALE, g.play("e2e4", Piece.WHITE, 2, expectedPly = 0), "a retry of the same move is harmless")
        assertEquals(listOf("e4"), g.san)
        assertTrue(g.offerDraw(Piece.WHITE))
        assertFalse(g.answerDraw(Piece.WHITE, true), "not one's own offer")
        assertEquals(ChessGame.Play.OK, g.play("e7e5", Piece.BLACK, 3))
        assertNull(g.drawOffer, "moving instead of answering declines")
        assertTrue(g.offerDraw(Piece.WHITE)); assertTrue(g.answerDraw(Piece.BLACK, true))
        assertEquals(GameResult.draw(EndReason.AGREEMENT), g.result)
        assertTrue(g.undo(2, 10))
        assertNull(g.result); assertEquals(0, g.ply); assertEquals(Position.START_FEN, g.position.fen())
        assertTrue(g.resign(Piece.WHITE)); assertEquals(Outcome.BLACK_WINS, g.result?.outcome)
        assertTrue(g.pgn("A", "B").contains("[Result \"0-1\"]"))
    }
}

/** The TV's room (fake clock, the computer thinks on the calling thread). */
class ChessRoomTest {
    private var now = 1_000L
    private val direct = Executor { it.run() }
    private fun room(white: ChessRoom.Seat = ChessRoom.Seat.PHONE, black: ChessRoom.Seat = ChessRoom.Seat.PHONE, secs: Int = 30) =
        ChessRoom(clock = { now }, random = java.util.Random(5), maxPlayers = 4, autoTick = false, aiExecutor = direct,
            ai = ChessAi(ttBits = 12, random = java.util.Random(1))).also { it.configure(white, black, level = 1, perMoveSeconds = secs) }

    @Suppress("UNCHECKED_CAST") private fun Map<String, Any?>.m(k: String) = this[k] as Map<String, Any?>
    @Suppress("UNCHECKED_CAST") private fun Map<String, Any?>.l(k: String) = this[k] as List<Any?>

    @Test fun codeTokensSeatsAndReconnection() {
        val r = room()
        assertTrue(r.code.matches(Regex("\\d{4}")))
        assertEquals(ChessRoom.Join.BAD_CODE, r.join(if (r.code == "0000") "1111" else "0000", "Ali").status)
        assertEquals(ChessRoom.Join.BAD_NAME, r.join(r.code, " <> ").status)
        val a = r.join(r.code, "Ali").player!!
        val b = r.join(r.code, "Ali").player!!
        assertEquals("Ali 2", b.name)
        assertEquals(Piece.WHITE, r.colorOf(a)); assertEquals(Piece.BLACK, r.colorOf(b))
        val spectator = r.join(r.code, "Carine").player!!
        assertNull(r.colorOf(spectator))
        assertEquals(1, r.view(null)["spectators"])
        r.join(r.code, "Dan")
        assertEquals(ChessRoom.Join.FULL, r.join(r.code, "Eve").status)
        // reconnection with the token: same player, same colour, even during a game
        assertNull(r.start())
        val back = r.join(r.code, null, a.token).player!!
        assertSame(a, back); assertEquals(Piece.WHITE, r.colorOf(back))
        assertNotEquals(a.token, b.token)
        assertNull(r.view(spectator.token).m("me")["color"])
        assertFalse(r.viewJson(a.token).contains(b.token), "nobody sees another player's token")
    }

    @Test fun startNeedsThePhonePlayers() {
        val r = room()
        assertNotNull(r.start())
        val a = r.join(r.code, "Ali").player!!
        assertEquals("En attente du joueur des Noirs sur téléphone", r.start())
        r.join(r.code, "Bea")
        assertNull(r.start())
        assertEquals(ChessRoom.Stage.PLAYING, r.stage)
        assertEquals(ChessRoom.Act.FORBIDDEN, r.act(a.token, "sit", "b"), "no seat change during a game")
    }

    @Test fun illegalOrOutOfTurnMovesAreRefusedAndChangeNothing() {
        val r = room()
        val w = r.join(r.code, "W").player!!; val b = r.join(r.code, "B").player!!
        val s = r.join(r.code, "Spectateur").player!!
        assertNull(r.start())
        val v0 = r.view(w.token)
        assertTrue("e2e4" in v0.l("legal"), "legal moves are listed for the player to move")
        assertTrue(r.view(b.token).l("legal").isEmpty())
        assertEquals(ChessRoom.Act.ILLEGAL, r.act(w.token, "move", "e2e5", 0))
        assertEquals(ChessRoom.Act.NOT_YOUR_TURN, r.act(b.token, "move", "e7e5", 0))
        assertEquals(ChessRoom.Act.FORBIDDEN, r.act(s.token, "move", "e2e4", 0), "spectators cannot play")
        assertEquals(ChessRoom.Act.UNKNOWN_PLAYER, r.act("forged", "move", "e2e4", 0))
        assertEquals(ChessRoom.Act.NOT_YOUR_TURN, r.hostMove("e2e4"), "the remote does not play a phone's colour")
        assertEquals(Position.START_FEN, r.view(null)["fen"])
        assertEquals(ChessRoom.Act.OK, r.act(w.token, "move", "e2e4", 0))
        assertEquals(ChessRoom.Act.STALE, r.act(w.token, "move", "e2e4", 0), "a duplicate is not played twice")
        assertEquals(ChessRoom.Act.OK, r.act(b.token, "move", "e7e5", 1))
        val v = r.view(s.token)
        assertEquals(listOf("e4", "e5"), v.l("san"))
        assertEquals("e7e5", v["lastMove"])
        assertEquals(2, v["ply"])
    }

    @Test fun hostClockDecidesTimeouts() {
        val r = room(secs = 90)
        val w = r.join(r.code, "W").player!!; r.join(r.code, "B")
        assertNull(r.start())
        assertEquals(60_000L, r.view(w.token).m("clock")["perMoveMs"], "90 s asked, 60 s max")
        now += 59_999; r.tick(); assertEquals(ChessRoom.Stage.PLAYING, r.stage)
        now += 1; r.tick()
        assertEquals(ChessRoom.Stage.FINISHED, r.stage)
        val res = r.view(w.token).m("result")
        assertEquals("b", res["winner"]); assertEquals("TIMEOUT", res["reason"])
        assertTrue((r.view(null)["pgn"] as String).contains("0-1"))
    }

    @Test fun resignAndDrawBetweenPhones() {
        val r = room()
        val w = r.join(r.code, "W").player!!; val b = r.join(r.code, "B").player!!
        assertNull(r.start())
        assertEquals(ChessRoom.Act.OK, r.act(w.token, "draw", "offer"))
        assertEquals("w", r.view(b.token)["drawOffer"])
        assertEquals(ChessRoom.Act.IGNORED, r.act(w.token, "draw", "accept"), "cannot accept one's own offer")
        assertEquals(ChessRoom.Act.OK, r.act(b.token, "draw", "decline"))
        assertNull(r.view(b.token)["drawOffer"])
        assertEquals(ChessRoom.Act.OK, r.act(b.token, "resign"))
        assertEquals("w", r.view(w.token).m("result")["winner"])
        // another game with the same players
        assertTrue(r.backToLobby()); assertNull(r.start())
        r.act(w.token, "draw", "offer"); r.act(b.token, "draw", "accept")
        assertEquals("AGREEMENT", r.view(w.token).m("result")["reason"])
    }

    @Test fun soloAgainstTheComputerWithUndo() {
        val r = room(white = ChessRoom.Seat.REMOTE, black = ChessRoom.Seat.AI)
        assertNull(r.start())
        assertTrue("e2e4" in r.view(null).l("legal"), "the TV lists the remote's legal moves")
        assertEquals(ChessRoom.Act.OK, r.hostMove("e2e4"))
        assertEquals(2, r.game!!.ply, "the computer answered")
        assertEquals(Piece.WHITE, r.game!!.turn)
        assertTrue(r.hostUndo())
        assertEquals(0, r.game!!.ply, "the computer's answer and our move are taken back")
        assertFalse(r.hostUndo(), "nothing left to take back")
        assertFalse(r.hostOfferDraw(), "the computer refuses a draw in the starting position")
        assertNotNull(r.view(null)["notice"])
        assertTrue(r.hostResign())
        assertEquals("b", r.view(null).m("result")["winner"])
    }

    @Test fun computerPlaysWhiteWhenTheHumanTakesBlack() {
        val r = room(white = ChessRoom.Seat.AI, black = ChessRoom.Seat.REMOTE)
        assertNull(r.start())
        assertEquals(1, r.game!!.ply, "the computer opened")
        assertTrue(r.view(null).l("legal").isNotEmpty())
        assertFalse(r.hostUndo(), "nothing of ours to take back yet")
    }

    @Test fun twoPlayersAtTheTvAgreeADrawAtOnce() {
        val r = room(white = ChessRoom.Seat.REMOTE, black = ChessRoom.Seat.REMOTE)
        assertNull(r.start())
        assertEquals(ChessRoom.Act.OK, r.hostMove("e2e4"))
        assertEquals(ChessRoom.Act.OK, r.hostMove("e7e5"), "the remote plays both colours")
        assertFalse(r.hostUndo(), "no take-back between two humans")
        assertTrue(r.hostOfferDraw())
        assertEquals("AGREEMENT", r.view(null).m("result")["reason"])
    }

    @Test fun remoteAgainstPhoneWithDrawOfferAnsweredByThePhone() {
        val r = room(white = ChessRoom.Seat.REMOTE, black = ChessRoom.Seat.PHONE)
        val b = r.join(r.code, "Tel").player!!
        assertEquals(Piece.BLACK, r.colorOf(b))
        assertNull(r.start())
        assertEquals(ChessRoom.Act.OK, r.hostMove("d2d4"))
        assertEquals(ChessRoom.Act.OK, r.act(b.token, "move", "d7d5", 1))
        assertTrue(r.hostOfferDraw())
        assertEquals("w", r.view(b.token)["drawOffer"])
        assertEquals(ChessRoom.Act.OK, r.act(b.token, "draw", "accept"))
        assertEquals(ChessRoom.Stage.FINISHED, r.stage)
    }

    @Test fun practiceModePlaysForTheLatePlayer() {
        val r = room(white = ChessRoom.Seat.REMOTE, black = ChessRoom.Seat.REMOTE)
        r.configure(ChessRoom.Seat.REMOTE, ChessRoom.Seat.REMOTE, perMoveSeconds = 10, mode = ClockMode.PRACTICE)
        assertNull(r.start())
        now += 10_000; r.tick()
        assertEquals(ChessRoom.Stage.PLAYING, r.stage)
        assertEquals(1, r.game!!.ply)
        assertTrue((r.view(null)["notice"] as String).contains("d'office"))
    }

    @Test fun closedRoomRefusesEverything() {
        val r = room()
        val a = r.join(r.code, "A").player!!
        r.close()
        assertEquals(ChessRoom.Join.CLOSED, r.join(r.code, "B").status)
        assertEquals(ChessRoom.Act.CLOSED, r.act(a.token, "move", "e2e4", 0))
        assertFailsWith<IllegalArgumentException> { room().configure(ChessRoom.Seat.AI, ChessRoom.Seat.AI) }
    }
}

/** End to end over HTTP through the TV's real server, with the phone app's client ([LanChessClient]). */
class ChessHttpTest {
    private val dir = kotlin.io.path.createTempDirectory("chesshttp").toFile()
    private val port = ServerSocket(0).use { it.localPort }
    @Volatile private var room: ChessRoom? = ChessRoom(maxPlayers = 3).also { it.configure(ChessRoom.Seat.PHONE, ChessRoom.Seat.PHONE) }
    private val quizRoom = QuizRoom(EmbeddedQuestionSource().bank())
    private val server = ReceiverServer(castbridge.core.tv.VolumeRegistry.single(dir), FakePlayer(), port, pin = "123456",
        publicRoutes = CombinedRoutes(QuizHttp({ quizRoom }), ChessHttp({ room }, pingMs = 300))).apply { start(5000, false) }
    private val base = "http://127.0.0.1:$port"
    private val client = LanChessClient(base)

    @AfterTest fun tearDown() { room?.close(); quizRoom.close(); server.stop(); dir.deleteRecursively() }

    private fun get(path: String): Pair<Int, String> {
        val c = URL(base + path).openConnection() as HttpURLConnection
        val code = c.responseCode
        return code to ((if (code < 400) c.inputStream else c.errorStream)?.use { String(it.readBytes(), Charsets.UTF_8) }.orEmpty())
    }

    @Test fun pageHelloAndAdminStillNeedsThePin() {
        val (code, page) = get("/chess")
        assertEquals(200, code)
        assertTrue(page.contains("\"1\":[[\"M50,18"), "the piece shapes are put in the page: ${page.take(300)}")
        assertFalse(page.contains("/*PIECES*/"))
        val hello = Json.obj(get("/chess/api/hello").second)
        assertEquals(true, hello["open"]); assertNull(hello["code"])
        assertEquals(200, get("/quiz").first, "the quiz still answers next to the chess")
        assertEquals(401, get("/api/info").first)
    }

    @Test fun twoPhonesPlayOverHttp() {
        val r = room!!
        val w = client.join(r.code, "Awa")
        val b = client.join(r.code, "Bello")
        assertEquals("w", w.color); assertEquals("b", b.color)
        assertFailsWith<ChessTransportException> { client.join("99999", "X") }.also { assertEquals(403, it.status) }
        assertNull(r.start())
        val illegal = client.move(w, "e2e5", 0)
        assertEquals("ILLEGAL", illegal.result); assertFalse(illegal.ok)
        assertEquals("NOT_YOUR_TURN", client.move(b, "e7e5", 0).result)
        assertTrue(client.move(w, "e2e4", 0).ok)
        // long-poll wakes up on the next change
        val v = (client.state(b)["v"] as Number).toLong()
        val t = Thread { Thread.sleep(200); client.move(b, "e7e5", 1) }.apply { start() }
        val next = client.state(w, since = v, waitSeconds = 5)
        t.join()
        assertTrue((next["v"] as Number).toLong() > v)
        // reconnection: same seat with the token
        val again = client.join(r.code, "Awa", w.token)
        assertEquals("w", again.color); assertEquals(w.playerId, again.playerId)
        assertEquals(listOf("e4", "e5"), client.state(again)["san"])
        assertEquals("OK", client.draw(w, "offer").result)
        assertEquals("OK", client.resign(b).result)
        @Suppress("UNCHECKED_CAST") val res = client.state(w)["result"] as Map<String, Any?>
        assertEquals("w", res["winner"])
        assertFailsWith<ChessTransportException> { client.state(ChessSession(r.code, "forged", "", "", null)) }.also { assertEquals(401, it.status) }
    }

    @Test fun eventStreamSendsTheState() {
        val r = room!!
        val s = client.join(r.code, "Awa")
        val c = URL("$base/chess/api/events?token=${s.token}").openConnection() as HttpURLConnection
        c.readTimeout = 5000
        val first = c.inputStream.bufferedReader().let { rd -> generateSequence { rd.readLine() }.first { it.startsWith("data: ") } }
        assertTrue(first.contains("\"stage\":\"LOBBY\""))
        c.disconnect()
    }
}
