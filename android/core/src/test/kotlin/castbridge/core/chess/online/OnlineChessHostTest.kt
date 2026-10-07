package castbridge.core.chess.online

import castbridge.core.chess.ChessRoom
import castbridge.core.games.ActResult
import castbridge.core.games.GameRoom
import castbridge.core.games.JoinStatus
import castbridge.core.games.RoomHttp
import castbridge.core.games.RoomStage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * La vitrine des téléphones du foyer pendant une partie en ligne : regarder, jamais commander ni espionner. C'est une salle de la plateforme de jeux (`GameRoom` sans place) : même contrat de code,
 * de jetons, de présence et d'attente que la salle maison, servie par les mêmes routes (`RoomHttp`).
 */
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
        assertEquals(JoinStatus.BAD_CODE, host.joinRoom("ABCD-EFGH", "Papa").status)
        assertEquals(JoinStatus.BAD_CODE, host.joinRoom(null, "Papa").status)
        assertEquals(JoinStatus.BAD_NAME, host.joinRoom(host.code, "  <>  ").status)
        val j = host.joinRoom(" ${host.code} ", "Papa")
        assertEquals(JoinStatus.OK, j.status); assertNotNull(j.player)
        assertEquals(",\"color\":null", host.joinReply(j.player!!), "un téléphone du foyer regarde : pas de couleur")
    }

    @Test fun theSameTokenTakesTheSeatBackAndNamesStayUnique() {
        val p = host.joinRoom(host.code, "Papa").player!!
        val q = host.joinRoom(host.code, "papa").player!!
        assertEquals("papa 2", q.name); assertNotEquals(p.token, q.token)
        host.leave(p.token)
        assertEquals(1, host.players().size)
        val back = host.joinRoom(host.code, "Papa", p.token).player!!
        assertTrue(back === p); assertEquals(2, host.players().size)
    }

    @Test fun capacityIsEnforcedLikeTheHomeRoom() {
        val h = OnlineChessHost(maxPlayers = 2)
        repeat(2) { assertEquals(JoinStatus.OK, h.joinRoom(h.code, "P$it").status) }
        assertEquals(JoinStatus.FULL, h.joinRoom(h.code, "P3").status)
    }

    @Test fun thePhoneViewIsSanitisedAndTheClockIsCorrectedForTheElapsedTime() {
        host.update(tvView())
        val p = host.joinRoom(host.code, "Papa").player!!
        now += 4_000L
        val v = host.view(p.token)
        assertEquals(emptyList<String>(), v["legal"]); assertFalse(v.containsKey("room")); assertEquals(true, v["online"]); assertEquals(host.code, v["code"])
        assertEquals(mapOf("id" to p.id, "name" to "Papa", "color" to null), v["me"])
        assertEquals(16_000L, (v["clock"] as Map<*, *>)["remainingMs"], "20 s − 4 s écoulées depuis la vue")
        assertEquals("PLAYING", v["stage"]); assertEquals(RoomStage.PLAYING, host.roomStage)
        assertNull(host.view(null)["me"], "sans jeton : un visiteur sans siège")
        // la pendule arrêtée ne bouge pas
        val stopped = tvView(); stopped["clock"] = mapOf("perMoveMs" to 30_000L, "remainingMs" to 20_000L, "running" to false)
        host.update(stopped)
        now += 9_000L
        assertEquals(20_000L, (host.view(p.token)["clock"] as Map<*, *>)["remainingMs"])
    }

    @Test fun beforeTheFirstViewThePhoneSeesAnEmptyLobbyNotAnError() {
        val p = host.joinRoom(host.code, "Papa").player!!
        val v = host.view(p.token)
        assertEquals("LOBBY", v["stage"]); assertEquals(emptyList<String>(), v["legal"]); assertEquals(RoomStage.LOBBY, host.roomStage)
    }

    @Test fun aPhoneCanNeverActAndAnUnknownTokenIsUnknown() {
        host.update(tvView())
        val p = host.joinRoom(host.code, "Papa").player!!
        for (action in listOf("move", "resign", "draw", "sit", "stand")) assertEquals(ActResult.FORBIDDEN, host.httpAct(p.token, action, "e2e4", 2), action)
        assertEquals(ActResult.UNKNOWN_PLAYER, host.httpAct("nope", "move", "e2e4", 2)); assertEquals(ActResult.UNKNOWN_PLAYER, host.httpAct(null, "move", "e2e4", 2))
        assertNull(host.seatOf(p), "aucune place")
    }

    @Test fun theVersionMovesOnEveryUpdateSoWaitingPhonesWakeUp() {
        val v0 = host.version
        host.update(tvView())
        assertTrue(host.version > v0)
        val seen = host.version
        val t = Thread { Thread.sleep(80); host.update(tvView("FINISHED")) }.apply { start() }
        val woke = host.awaitChange(seen, 3_000L)
        t.join()
        assertTrue(woke > seen); assertEquals(RoomStage.FINISHED, host.roomStage)
        val current = host.version
        assertEquals(current, host.awaitChange(current, 50L), "rien de nouveau : l'attente expire sans changement")
    }

    @Test fun closingTellsThePhonesAndRefusesFurtherUpdatesAndJoins() {
        host.update(tvView())
        val p = host.joinRoom(host.code, "Papa").player!!
        host.close()
        assertEquals(RoomStage.CLOSED, host.roomStage)
        host.update(tvView()); assertEquals(RoomStage.CLOSED, host.roomStage, "fermée pour de bon")
        assertEquals(JoinStatus.CLOSED, host.joinRoom(host.code, "Autre").status)
        assertEquals(ActResult.CLOSED, host.httpAct(p.token, "move", "e2e4", 2))
    }

    @Test fun presenceFollowsStreamsAndTheLastSeenTime() {
        host.update(tvView())
        val p = host.joinRoom(host.code, "Papa").player!!
        fun connected() = ((host.view(p.token)["players"] as List<*>).single() as Map<*, *>)["connected"]
        assertEquals(true, connected())
        now += GameRoom.PRESENCE_MS + 1
        assertEquals(false, connected())
        host.streamOpened(p); assertEquals(true, connected())
        host.streamClosed(p); host.touch(p)
        assertEquals(true, connected())
    }

    @Test fun theTvRoutesServeTheShowcaseThroughTheSameRoomHttpAsTheHomeRoom() {
        // les routes de la salle maison (`/chess`, paramètre de coup `ply`) servent la vitrine : une seule implémentation, aucun SSE ni limite recopiés
        val http = RoomHttp("/chess", { host }, { "<html></html>" }, ChessRoom.PROTOCOL, "Aucune partie d'échecs ouverte sur la TV.", helloExtra = ",\"online\":true", seqParam = "ply")
        assertNotNull(http)
        assertTrue(http.extraThreads > 0)
    }
}
