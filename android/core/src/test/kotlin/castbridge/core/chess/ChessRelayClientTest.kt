package castbridge.core.chess

import castbridge.core.quiz.online.ClientMsg
import castbridge.core.quiz.online.PlayRole
import castbridge.core.quiz.online.PlayTvSession
import castbridge.core.quiz.online.ScriptedTransport
import castbridge.core.quiz.online.ServerMsg
import castbridge.core.quiz.online.StakeSpec
import castbridge.core.quiz.online.TvLink
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Le client des échecs en ligne de la TV, seul, face à un « service » scripté : ce qu'il fait d'un silence, d'un refus, d'une salle fermée, d'un coup sans accusé, et son battement. Les parties complètes
 * à deux TV sont dans `ChessOnlineGameTest` (vrai service en mémoire) et `ChessSocketsTest` (vraies sockets).
 */
class ChessRelayClientTest {
    private var now = 1_000L
    private val clients = ArrayList<ChessRelayClient>()
    private var transport: ScriptedTransport? = null
    private val view = linkedMapOf<String, Any?>("v" to 1L, "stage" to "LOBBY", "ply" to 0, "me" to mapOf("id" to "p1", "name" to "TV", "color" to "w"))

    private fun client(reply: (ScriptedTransport, ClientMsg) -> Unit = { _, _ -> }, beat: () -> Unit = {}, beatMs: Long = 4_000L): ChessRelayClient {
        val c = ChessRelayClient(
            newSession = { PlayTvSession(clock = { now }, transports = { ScriptedTransport().also { t -> t.reply = { m -> reply(t, m) }; transport = t } }, ticket = { "cbp1.test" }) },
            activation = { "cbx1.act" }, deviceHash = { "dev-test-000001" }, openTimeoutMs = 400L, ackTimeoutMs = 300L, tickMs = 10L, beat = beat, beatMs = beatMs)
        clients += c
        return c
    }

    private fun welcome(t: ScriptedTransport) {
        t.push(ServerMsg.Welcome(1, "0123456789abcdef0123456789abcdef", "K7M2QX4T", "seat-token", PlayRole.HOST, "p1", 1, listOf("chess"), "chess"))
        t.push(ServerMsg.State(2, view, true))
    }

    private val seatsOnCreate = { t: ScriptedTransport, m: ClientMsg -> if (m is ClientMsg.Create) welcome(t) }

    @kotlin.test.AfterTest fun stop() { clients.forEach { it.close() } }

    @Test fun createdGameCarriesTheChessOptionsTheStakeAndTheEscrowOnTheWire() {
        val c = client(seatsOnCreate)
        val s = c.createGame("TV Salon", 45, "black", ClockMode.PRACTICE, StakeSpec("MBOKO", 5), "cbe1.x.y")
        assertEquals("K7M2-QX4T", s.code); assertEquals("w", s.color); assertEquals("0123456789abcdef0123456789abcdef", s.roomId); assertEquals("seat-token", s.token)
        val m = transport!!.all<ClientMsg.Create>().single()
        assertEquals("chess", m.game); assertEquals(45, m.chess!!.perMoveSeconds); assertEquals("PRACTICE", m.chess!!.mode); assertEquals("black", m.chess!!.color)
        assertEquals(StakeSpec("MBOKO", 5), m.stake); assertEquals("cbe1.x.y", m.escrow); assertEquals("cbx1.act", m.activation)
    }

    @Test fun theClockOfAFreeGameIsClampedToTenToSixtySecondsAndThereIsNoStake() {
        val c = client(seatsOnCreate)
        c.createGame("TV", 5, "random", ClockMode.COMPETITION)
        assertEquals(10, transport!!.all<ClientMsg.Create>().single().chess!!.perMoveSeconds)
        assertNull(transport!!.all<ClientMsg.Create>().single().stake); assertNull(transport!!.all<ClientMsg.Create>().single().escrow)
    }

    @Test fun aServiceThatNeverAnswersEndsInATimeoutWithAFrenchText() {
        val c = client()
        val e = assertFailsWith<ChessTransportException> { c.createGame("TV", 30, "white", ClockMode.COMPETITION) }
        assertEquals(504, e.status); assertTrue(e.message!!.contains("ne répond pas"), e.message)
    }

    @Test fun aRefusalOfTheServiceBecomesItsStatusItsReasonAndItsFrenchText() {
        val c = client({ t, m -> if (m is ClientMsg.Join) t.push(ServerMsg.Error(3, "PLAY_BAD_CODE", "Ce code de salle n'existe pas.", true)) })
        val e = assertFailsWith<ChessTransportException> { c.joinGame("K7M2-QX4T", "TV") }
        assertEquals(404, e.status); assertEquals("PLAY_BAD_CODE", e.reason); assertTrue(e.message!!.contains("code de salle"), e.message)
        assertNotNull(c.lastRefusalText())
    }

    @Test fun aStakedRoomAsksForAnEscrowKeepsTheLinkAndTheSecondTryUsesTheSameConnection() {
        val opened = AtomicInteger()
        val c = client({ t, m ->
            if (m is ClientMsg.Join && m.escrow == null) t.push(ServerMsg.Error(3, "STAKE_ESCROW_REQUIRED", "mise", true, 0L, mapOf("game" to "chess", "cur" to "NDEM", "per" to 20L)))
            if (m is ClientMsg.Join && m.escrow != null) { opened.incrementAndGet(); t.push(ServerMsg.Welcome(4, "0123456789abcdef0123456789abcdef", "K7M2QX4T", "seat-b", PlayRole.PLAYER, "p2", 1, listOf("chess"), "chess")); t.push(ServerMsg.State(5, view, true)) }
        })
        val e = assertFailsWith<ChessStakeRequired> { c.joinGame("K7M2-QX4T", "TV B") }
        assertEquals(StakeSpec("NDEM", 20), e.spec)
        val first = transport
        val s = c.joinGame("K7M2-QX4T", "TV B", "cbe1.a.b")
        assertTrue(transport === first, "aucune nouvelle liaison : le même transport sert")
        assertEquals(1, opened.get()); assertEquals("seat-b", s.token)
        assertEquals("cbe1.a.b", transport!!.all<ClientMsg.Join>().last().escrow)
    }

    @Test fun aMoveWithoutAnAnswerFromTheServiceTimesOutInsteadOfHanging() {
        val c = client(seatsOnCreate)
        val s = c.createGame("TV", 30, "white", ClockMode.COMPETITION)
        val e = assertFailsWith<ChessTransportException> { c.move(s, "e2e4", 0) }
        assertEquals(504, e.status)
        val sent = transport!!.all<ClientMsg.GameAct>().single()
        assertEquals("move", sent.op); assertEquals("e2e4", sent.arg); assertEquals(0, sent.ply)
    }

    @Test fun anAckAnswersTheCommandThatWasSent() {
        val c = client({ t, m ->
            if (m is ClientMsg.Create) welcome(t)
            if (m is ClientMsg.GameAct) t.push(ServerMsg.Ack(9, m.seq, if (m.op == "move") "ILLEGAL" else "OK"))
        })
        val s = c.createGame("TV", 30, "white", ClockMode.COMPETITION)
        assertEquals("ILLEGAL", c.move(s, "e2e5", 0).result)
        assertTrue(c.resign(s).ok)
        assertTrue(c.draw(s, "offer").ok)
        assertTrue(c.cancel().ok)
        assertEquals(listOf("move", "resign", "draw", "cancel"), transport!!.all<ClientMsg.GameAct>().map { it.op })
    }

    @Test fun theSignedResultIsKeptAndHandedToTheListenerOnce() {
        val c = client(seatsOnCreate)
        val got = ArrayList<String>()
        c.onResult = { got += it }
        c.createGame("TV", 30, "white", ClockMode.COMPETITION)
        transport!!.push(ServerMsg.Result(10, "cbr1.payload.sig"))
        assertEquals(listOf("cbr1.payload.sig"), got); assertEquals("cbr1.payload.sig", c.resultToken)
    }

    @Test fun aClosedRoomIsReportedAndTheLastViewStaysReadable() {
        val c = client(seatsOnCreate)
        val s = c.createGame("TV", 30, "white", ClockMode.COMPETITION)
        transport!!.push(ServerMsg.RoomGone(11, "EXPIRED"))
        assertEquals("EXPIRED", c.roomGoneReason)
        assertEquals("LOBBY", c.state(s, since = 0)["stage"], "la dernière vue reste lisible")
    }

    @Test fun theLatestViewIsACopyAndThePhoneOnlyLinkViewFollowsTheSession() {
        val c = client(seatsOnCreate)
        assertEquals(TvLink.Lost, c.link(), "pas de liaison avant l'ouverture")
        c.createGame("TV", 30, "white", ClockMode.COMPETITION)
        val v = c.latest(); (v as MutableMap<String, Any?>)["stage"] = "HACKED"
        assertEquals("LOBBY", c.latest()["stage"], "la copie ne change pas la vue du client")
        assertEquals(TvLink.Online, c.link())
    }

    @Test fun theBeatRunsWhileTheLinkLivesAndStopsWhenItIsClosed() {
        val beats = AtomicInteger()
        val c = client(seatsOnCreate, beat = { beats.incrementAndGet() }, beatMs = 30L)
        c.createGame("TV", 30, "white", ClockMode.COMPETITION)
        val end = System.currentTimeMillis() + 3_000
        while (beats.get() < 3 && System.currentTimeMillis() < end) Thread.sleep(10)
        assertTrue(beats.get() >= 3, "le battement tient la demande de tuyau vivante")
        c.close()
        Thread.sleep(80)
        val after = beats.get()
        Thread.sleep(150)
        assertEquals(after, beats.get(), "plus de battement une fois fermé")
    }

    @Test fun aClosedClientRefusesNewGames() {
        val c = client(seatsOnCreate)
        c.close()
        assertFailsWith<IllegalStateException> { c.createGame("TV", 30, "white", ClockMode.COMPETITION) }
    }
}
