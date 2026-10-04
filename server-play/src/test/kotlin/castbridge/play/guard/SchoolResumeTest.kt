package castbridge.play.guard

import castbridge.core.quiz.online.ClientMsg
import castbridge.core.quiz.online.Limits
import castbridge.core.quiz.online.PlayCodec
import castbridge.core.quiz.online.ServerRoom
import castbridge.play.ConnectionLimits
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** R1 (ré-audit) : une classe de 30 élèves dont le Wi-Fi coupe se reconnecte par `resume` ; le plafond partagé ne retombe pas à 8. */
class SchoolResumeTest {
    @Test fun thirtyPlayersOfOneAddressCutThenResumedAreNotLockedOutByTheSharedCeiling() {
        var now = 1_000L
        val guardLimits = Limits({ now })
        val gate = ConnectionLimits(8, 1_000_000, 1_000, gate = guardLimits, maxPerIpShared = 64)
        val hub = GuardHarness.hub(ServerRoom.Settings(seatsPerTable = 40), clock = { now }, limits = gate)
        hub.guard = PlayGuard(guardLimits, LogRedactor.silent())
        val (_, code) = GuardHarness.host(hub)
        val school = "203.0.113.200"
        val players = (0 until 30).map { GuardHarness.join(hub, code, "Eleve$it", "p$it", ip = school) }
        assertTrue(players.all { it.welcomed() }, players.map { it.errors() }.filter { it.isNotEmpty() }.toString())
        val tokens = players.map { Regex("\"token\":\"([0-9a-f]{32})\"").find(it.got.first { m -> m.startsWith("{\"t\":\"welcome\"") })!!.groupValues[1] }
        val roomId = Regex("\"roomId\":\"([0-9a-f]{32})\"").find(players[0].got.first { it.startsWith("{\"t\":\"welcome\"") })!!.groupValues[1]
        now += 31_000
        assertEquals(38, guardLimits.openCeiling(school), "30 joueurs assis depuis 30 s : 8 + 30")
        players.forEach { hub.onClosed(it) }   // le Wi-Fi de la classe coupe
        assertEquals(38, guardLimits.openCeiling(school), "les sièges gardés pour la reprise comptent encore : le plafond ne retombe pas")
        now += 20_000
        for (i in 0 until 30) {
            assertEquals(ConnectionLimits.Verdict.OK, gate.acquire(school), "reconnexion n°${i + 1} : jamais 429 avant l'upgrade")
            val c = FakeConn("r$i", school).also { hub.register(it) }
            hub.onText(c, PlayCodec.encode(ClientMsg.Resume(roomId, tokens[i], 0)))
            assertTrue(c.welcomed(), "reprise n°${i + 1} : ${c.errors()}")
        }
        assertEquals(38, guardLimits.openCeiling(school), "après la reprise, le plafond est toujours celui de 30 joueurs")
    }
}
