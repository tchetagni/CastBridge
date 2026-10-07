package castbridge.core.games

import castbridge.core.chess.ChessRoom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `ChessRoom` garde son API historique (`ChessRoom.Stage`, `Join`, `Act`, `Seat`) au-dessus de la plateforme : ces énumérations sont une façade sur celles de la plateforme (mêmes noms,
 * convertis par nom). Ce test les épingle égales : si l'une change sans l'autre, la conversion casserait à l'exécution, ici elle casse à la compilation de la suite.
 */
class GameRoomFacadeTest {
    private fun <A : Enum<A>, B : Enum<B>> same(a: Array<A>, b: Array<B>, what: String) = assertEquals(b.map { it.name }, a.map { it.name }, what)

    @Test fun chessFacadeEnumsMirrorThePlatformOnes() {
        same(ChessRoom.Stage.values(), RoomStage.values(), "Stage")
        same(ChessRoom.Join.values(), JoinStatus.values(), "Join")
        same(ChessRoom.Act.values(), ActResult.values(), "Act")
        same(ChessRoom.Seat.values(), SeatKind.values(), "Seat")
        assertEquals(SeatKind.values().map { it.label }, ChessRoom.Seat.values().map { it.label }, "mêmes libellés d'écran")
    }

    @Test fun aChessRoomIsAGameRoomAndSpeaksTheSharedHttpPort() {
        val r = ChessRoom(autoTick = false)
        assertTrue(r is GameRoom && r is RoomEndpoint)
        assertEquals(GameRoom.DEFAULT_MAX_PLAYERS, r.maxPlayers, "dix personnes, comme avant")
        assertEquals(GameRoom.PRESENCE_MS, ChessRoom.PRESENCE_MS); assertEquals(GameRoom.MAX_NAME, ChessRoom.MAX_NAME)
        assertEquals(ChessRoom.cleanName("  Awa   <b>Koné</b> "), GameRoom.cleanName("  Awa   <b>Koné</b> "))
        assertEquals("Awa bKoné/b", GameRoom.cleanName("  Awa   <b>Koné</b> "), "chevrons et espaces nettoyés")
        r.close()
    }

    @Test fun everyActResultAnswersWithTheHttpStatusTheChessRoutesAlwaysUsed() {
        assertEquals(mapOf("OK" to 200, "IGNORED" to 200, "STALE" to 200, "ILLEGAL" to 400, "BAD_REQUEST" to 400, "NOT_YOUR_TURN" to 409, "FORBIDDEN" to 409, "UNKNOWN_PLAYER" to 401, "CLOSED" to 410),
            ActResult.values().associate { it.name to it.httpStatus })
    }
}
