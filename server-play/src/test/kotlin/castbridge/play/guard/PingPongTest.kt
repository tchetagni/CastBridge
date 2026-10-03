package castbridge.play.guard

import castbridge.core.quiz.online.ClientMsg
import castbridge.core.quiz.online.PlayScope
import castbridge.core.quiz.online.RttBook
import castbridge.core.quiz.online.ServerMsg
import castbridge.core.quiz.online.ServerRoom
import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Ping/pong d'application et RTT câblés (w20-03 les a posés ; w20-07 en prouve les garanties contre un client qui ruse). Le rythme 5 s vaut en salle d'attente ET en jeu
 * (un RTT doit être connu avant la première question) ; il s'arrête quand la partie est finie. La trame de ping du transport (25 s) est un autre mécanisme (`WsConn`).
 */
class PingPongTest {
    private val bank = GuardHarness.bank
    private fun room() = ServerRoom("r1", PlayScope.INTERNET, bank, Random(5), createdAt = 0, settings = ServerRoom.Settings(duelCount = 3, duelQuestionMs = 20_000))

    private fun List<ServerRoom.Out>.pings() = filter { it.msg is ServerMsg.Ping }.map { it.to to (it.msg as ServerMsg.Ping).id }
    private fun ServerRoom.lobby(): ServerRoom {
        handle("tv", ClientMsg.Create(null, "DUEL"), 0)
        handle("c0", ClientMsg.Join(code, "Awa", null, GuardHarness.dev(), false), 0)
        handle("c1", ClientMsg.Join(code, "Bello", null, GuardHarness.dev(), false), 0)
        handle("tv", ClientMsg.Act(null, "mode", null, "DUEL", 1), 0)
        return this
    }

    @Test fun pingsEveryFiveSecondsInTheLobbyAndInGameThenStopsWhenFinished() {
        val r = room().lobby()
        var pings = 0L; var now = 0L
        val at = ArrayList<Long>()
        r.handle("tv", ClientMsg.Act(null, "start", null, "3", 2), 100)
        while (r.phase() != ServerRoom.State.FINISHED && now < 400_000) {
            now += 50
            val p = r.tick(now).pings()
            if (p.isNotEmpty()) { at += now; pings += p.size }
            val q = r.currentQuestionId()
            if (q != null && r.table(0).duelPhase() == castbridge.core.quiz.QuizDuel.Phase.QUESTION && now >= r.table(0).opensAtServerMs) {
                val right = r.table(0).room.duel!!.question.answer
                r.handle("c0", ClientMsg.Act(q, "answer", right, null, 1), now)   // « c1 » ne répond jamais : chaque question dure 20 s
            }
        }
        assertEquals(ServerRoom.State.FINISHED, r.phase())
        assertTrue(at.size >= 2, "au moins deux séries de pings : $at")
        at.zipWithNext().forEach { (a, b) -> assertTrue(b - a in 5_000..5_100, "5 s entre deux séries : $a -> $b") }
        val after = (1..200).sumOf { r.tick(now + it * 100L).pings().size }
        assertEquals(0, after, "plus aucun ping une fois la partie finie")
        assertEquals(0, pings % 3, "chaque série atteint les trois connexions (TV, deux joueurs) : $pings")
    }

    @Test fun aClientThatDelaysItsPongsGainsNothing() {
        val r = room().lobby()
        // 7 pongs retardés de 1,8 s, un seul pong honnête (80 ms) : le RTT retenu est le minimum des 8 derniers
        var now = 0L
        repeat(8) { i ->
            now += 5_000
            val ping = r.tick(now).pings().first { it.first == "c0" }.second
            val delay = if (i == 5) 80L else 1_800L
            r.handle("c0", ClientMsg.Pong(ping), now + delay)
        }
        assertEquals(80L, r.rtt.rtt("c0"))
        assertEquals(40L, r.rtt.compensationMs("c0"))
        repeat(8) { i ->
            now += 5_000
            val ping = r.tick(now).pings().first { it.first == "c1" }.second
            r.handle("c1", ClientMsg.Pong(ping), now + 1_900)
        }
        assertEquals(1_900L, r.rtt.rtt("c1"), "un client qui ne répond jamais vite garde son vrai (mauvais) RTT")
        assertEquals(100L, r.rtt.compensationMs("c1"), "la compensation plafonne à 100 ms : gonfler son RTT ne rapporte rien de plus")
        assertEquals(1_000L, r.rtt.graceMs("c1"), "la grâce plafonne à 1 s")
        assertEquals(RttBook.MAX_COMPENSATION_RTT_MS / 2, r.rtt.compensationMs("c1"))
    }

    @Test fun aPongWithAWrongIdOrFromAnotherConnectionIsIgnored() {
        val r = room().lobby()
        val pings = r.tick(5_000).pings()
        val mine = pings.first { it.first == "c0" }.second
        val theirs = pings.first { it.first == "c1" }.second
        r.handle("c0", ClientMsg.Pong(theirs), 5_050); r.handle("c0", ClientMsg.Pong("p9999"), 5_050)
        r.handle("zz", ClientMsg.Pong(mine), 5_050)
        assertEquals(0L, r.rtt.rtt("c0"), "ni l'identifiant d'une autre connexion, ni un inconnu, ni un inconnu de la salle ne donnent un échantillon")
        r.handle("c0", ClientMsg.Pong(mine), 5_120)
        assertEquals(120L, r.rtt.rtt("c0"))
        r.handle("c0", ClientMsg.Pong(mine), 9_000)   // rejeu du même pong
        assertEquals(120L, r.rtt.rtt("c0"), "un pong rejoué n'ajoute rien")
    }

    @Test fun answerArrivingInTheGraceWindowIsAcceptedOnlyForAConnectionWithARtt() {
        fun play(rttMs: Long): String {
            val r = room().lobby()
            val ping = r.tick(5_000).pings().first { it.first == "c0" }.second
            if (rttMs > 0) r.handle("c0", ClientMsg.Pong(ping), 5_000 + rttMs)
            r.handle("tv", ClientMsg.Act(null, "start", null, "3", 2), 6_000)
            r.tick(6_000)
            val q = r.currentQuestionId()!!
            val opens = r.table(0).opensAtServerMs
            var now = 6_000L
            while (now < opens + 20_000 - 60) { now += 50; r.tick(now) }   // la question est encore ouverte
            // la question se ferme à opens + 20 000 ms ; le joueur à gros RTT répond à ce moment-là : il a appuyé AVANT la clôture
            now = opens + 20_000 + 40
            r.tick(now)
            val right = r.table(0).room.duel!!.question.answer
            return r.handle("c0", ClientMsg.Act(q, "answer", right, null, 7), now).map { it.msg }.filterIsInstance<ServerMsg.Ack>().single().result
        }
        assertEquals("OK", play(600), "RTT 600 ms : la question est tenue ouverte pendant la grâce, la réponse (appuyée avant la clôture) compte")
        assertEquals("CLOSED", play(0), "sans RTT mesuré (0), pas de grâce : la réponse tardive est refusée")
    }
}
