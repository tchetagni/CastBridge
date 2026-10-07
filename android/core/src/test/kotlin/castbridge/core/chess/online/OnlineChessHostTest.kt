package castbridge.core.chess.online

import castbridge.core.chess.ChessHttp
import castbridge.core.chess.ChessRoom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** La vitrine des téléphones du foyer pendant une partie en ligne : regarder, jamais commander ni espionner ; même contrat de présence que la salle maison. */
class OnlineChessHostTest {
    private var now = 10_000L
    private val host = OnlineChessHost(clock = { now }, random = java.util.Random(5))

    private fun tvView(stage: String = "PLAYING", remaining: Long = 20_000L) = linkedMapOf<String, Any?>(
        "v" to 7L, "protocol" to 1, "stage" to stage, "code" to "ABCD-EFGH", "ply" to 2, "turn" to "w", "fen" to "startpos",
        "me" to mapOf("id" to "p1", "name" to "TV Salon", "color" to "w"), "legal" to listOf("e2e4", "d2d4"),
        "room" to mapOf("id" to "0123456789abcdef0123456789abcdef", "code" to "ABCD-EFGH", "away" to mapOf("w" to null, "b" to null), "forfeitMs" to 60_000L),
        "clock" to mapOf("perMoveMs" to 30_000L, "remainingMs" to remaining, "running" to true, "paused" to false, "turn" to "w"),
        "stake" to mapOf("cur" to "NDEM", "per" to 20L, "pot" to 40L, "settled" to false),
    )

    @Test fun aPhoneJoinsWithTheLocalFourDigitCodeNotTheOnlineOne() {
        assertTrue(Regex("^\\d{4}$").matches(host.code))
        assertEquals(ChessRoom.Join.BAD_CODE, host.join("ABCD-EFGH", "Papa").status)
        assertEquals(ChessRoom.Join.BAD_CODE, host.join(null, "Papa").status)
        assertEquals(ChessRoom.Join.BAD_NAME, host.join(host.code, "  <>  ").status)
        val j = host.join(" ${host.code} ", "Papa")
        assertEquals(ChessRoom.Join.OK, j.status); assertNotNull(j.player)
    }

    @Test fun theSameTokenTakesTheSeatBackAndNamesStayUnique() {
        val p = host.join(host.code, "Papa").player!!
        val q = host.join(host.code, "papa").player!!
        assertEquals("papa 2", q.name); assertNotEquals(p.token, q.token)
        host.leave(p.token)
        assertEquals(1, host.players().size)
        val back = host.join(host.code, "Papa", p.token).player!!
        assertTrue(back === p); assertEquals(2, host.players().size)
    }

    @Test fun capacityIsEnforcedLikeTheHomeRoom() {
        val h = OnlineChessHost(maxPlayers = 2)
        repeat(2) { assertEquals(ChessRoom.Join.OK, h.join(h.code, "P$it").status) }
        assertEquals(ChessRoom.Join.FULL, h.join(h.code, "P3").status)
    }

    @Test fun thePhoneViewIsSanitisedAndTheClockIsCorrectedForTheElapsedTime() {
        host.update(tvView())
        val p = host.join(host.code, "Papa").player!!
        now += 4_000L
        val v = host.view(p.token)
        assertEquals(emptyList<String>(), v["legal"]); assertFalse(v.containsKey("room")); assertEquals(true, v["online"]); assertEquals(host.code, v["code"])
        assertEquals(mapOf("id" to p.id, "name" to "Papa", "color" to null), v["me"])
        assertEquals(16_000L, (v["clock"] as Map<*, *>)["remainingMs"], "20 s − 4 s écoulées depuis la vue")
        assertEquals("PLAYING", v["stage"]); assertEquals(ChessRoom.Stage.PLAYING, host.stage)
        assertNull(host.view(null)["me"], "sans jeton : un visiteur sans siège")
        // la pendule arrêtée ne bouge pas
        val stopped = tvView(); stopped["clock"] = mapOf("perMoveMs" to 30_000L, "remainingMs" to 20_000L, "running" to false)
        host.update(stopped)
        now += 9_000L
        assertEquals(20_000L, (host.view(p.token)["clock"] as Map<*, *>)["remainingMs"])
    }

    @Test fun aPhoneCanNeverActAndAnUnknownTokenIsUnknown() {
        host.update(tvView())
        val p = host.join(host.code, "Papa").player!!
        for (action in listOf("move", "resign", "draw", "sit", "stand")) assertEquals(ChessRoom.Act.FORBIDDEN, host.act(p.token, action, "e2e4", 2), action)
        assertEquals(ChessRoom.Act.UNKNOWN_PLAYER, host.act("nope", "move", "e2e4", 2)); assertEquals(ChessRoom.Act.UNKNOWN_PLAYER, host.act(null, "move", "e2e4", 2))
        assertNull(host.colorOf(p))
    }

    @Test fun theVersionMovesOnEveryUpdateSoWaitingPhonesWakeUp() {
        val v0 = host.version
        host.update(tvView())
        assertTrue(host.version > v0)
        val seen = host.version
        val t = Thread { Thread.sleep(80); host.update(tvView("FINISHED")) }.apply { start() }
        val woke = host.awaitChange(seen, 3_000L)
        t.join()
        assertTrue(woke > seen); assertEquals(ChessRoom.Stage.FINISHED, host.stage)
        val now = host.version
        assertEquals(now, host.awaitChange(now, 50L), "rien de nouveau : l'attente expire sans changement")
    }

    @Test fun closingTellsThePhonesAndRefusesFurtherUpdatesAndJoins() {
        host.update(tvView())
        val p = host.join(host.code, "Papa").player!!
        host.close()
        assertEquals(ChessRoom.Stage.CLOSED, host.stage)
        host.update(tvView()); assertEquals(ChessRoom.Stage.CLOSED, host.stage, "fermée pour de bon")
        assertEquals(ChessRoom.Join.CLOSED, host.join(host.code, "Autre").status)
        assertEquals(ChessRoom.Act.CLOSED, host.act(p.token, "move", "e2e4", 2))
    }

    @Test fun presenceFollowsStreamsAndTheLastSeenTime() {
        host.update(tvView())
        val p = host.join(host.code, "Papa").player!!
        fun connected() = ((host.view(p.token)["players"] as List<*>).single() as Map<*, *>)["connected"]
        assertEquals(true, connected())
        now += ChessRoom.PRESENCE_MS + 1
        assertEquals(false, connected())
        host.streamOpened(p); assertEquals(true, connected())
        host.streamClosed(p); host.touch(p)
        assertEquals(true, connected())
    }

    @Test fun theTvRoutesServeTheOnlineHostThroughTheSameInterface() {
        // ChessHttp prend un ChessHost : la salle maison et la vitrine en ligne sont interchangeables
        var current: castbridge.core.chess.ChessHost? = host
        val http = ChessHttp({ current })
        assertNotNull(http)
        current = null
    }
}
