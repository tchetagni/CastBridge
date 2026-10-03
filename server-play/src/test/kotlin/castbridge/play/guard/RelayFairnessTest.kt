package castbridge.play.guard

import castbridge.core.quiz.online.ClientMsg
import castbridge.core.quiz.online.PlayScope
import castbridge.core.quiz.online.ServerMsg
import castbridge.core.quiz.online.ServerRoom
import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * T-14, hôte tricheur : la TV ne peut qu'ALLONGER le temps d'un joueur relayé (DESIGN-W20 § 2.6). Une TV modifiée qui annonce `localElapsedMono = 0` obtient
 * `temps serveur − min(rttTV, 400 ms)` : jamais moins, jamais négatif ; une TV honnête qui annonce plus long est crue. (Que la TV ne voie pas la bonne réponse avant
 * les autres : `NoAnswerLeakTest` du cœur.)
 */
class RelayFairnessTest {
    private class Setup(val r: ServerRoom, val relayToken: String, val remoteToken: String)

    private fun setup(tvRttMs: Long): Setup {
        val r = ServerRoom("r1", PlayScope.INTERNET, GuardHarness.bank, Random(9), createdAt = 0, settings = ServerRoom.Settings(duelCount = 3))
        r.handle("tv", ClientMsg.Create(null, "DUEL"), 0)
        val remote = r.handle("c0", ClientMsg.Join(r.code, "Awa", null, GuardHarness.dev(), false), 0).map { it.msg }.filterIsInstance<ServerMsg.Welcome>().single().token
        // le joueur local « Canapé » est enregistré par la TV (siège relayé, sans connexion propre)
        val token = r.handle("tv", ClientMsg.Join(r.code, "Canapé", null, null, false), 0).map { it.msg }.filterIsInstance<ServerMsg.Welcome>().single().token
        r.handle("tv", ClientMsg.Act(null, "mode", null, "DUEL", 1), 0)
        val ping = r.tick(5_000).filter { it.to == "tv" && it.msg is ServerMsg.Ping }.map { (it.msg as ServerMsg.Ping).id }.first()
        if (tvRttMs > 0) r.handle("tv", ClientMsg.Pong(ping), 5_000 + tvRttMs)
        r.handle("tv", ClientMsg.Act(null, "start", null, "3", 2), 6_000)
        r.tick(6_000)
        return Setup(r, token, remote)
    }

    private fun question(s: Setup) = s.r.currentQuestionId()!! to s.r.table(0).room.duel!!.question.answer

    /** La TV relaie la réponse du joueur local `arrival` ms après l'ouverture, en annonçant `local` ; rend le temps retenu par le serveur. */
    private fun relayAt(s: Setup, arrival: Long, local: Long): Long {
        val opens = s.r.table(0).opensAtServerMs
        val (q, right) = question(s)
        var now = 6_000L
        while (now < opens + arrival - 60) { now += 50; s.r.tick(now) }
        now = opens + arrival
        val ack = s.r.handle("tv", ClientMsg.RelayAct(s.relayToken, q, right, local, 3), now).map { it.msg }.filterIsInstance<ServerMsg.Ack>().single().result
        assertEquals("OK", ack)
        return s.r.table(0).answerLog().last().elapsedMs
    }

    @Test fun aTvAnnouncingZeroCannotGoBelowTheServerTimeMinusItsRtt() {
        assertEquals(1_000L, relayAt(setup(0), 1_000, 0), "RTT de la TV inconnu : le temps du serveur, pas 0")
        assertEquals(700L, relayAt(setup(300), 1_000, 0), "RTT 300 : 1 000 − 300")
        assertEquals(600L, relayAt(setup(900), 1_000, 0), "RTT 900 plafonné à 400 : 1 000 − 400, jamais moins")
        assertEquals(0L, relayAt(setup(900), 300, 0), "jamais négatif : 300 − 400 devient 0")
    }

    @Test fun anHonestSlowerTimeFromTheTvIsBelievedButNeverBelowTheNetworkFloor() {
        assertEquals(5_950L, relayAt(setup(100), 6_000, 5_950), "la TV annonce plus long que le plancher réseau (5 900) : crue")
        assertEquals(5_900L, relayAt(setup(100), 6_000, 5_000), "la TV annonce plus court : le plancher réseau s'applique")
    }

    @Test fun onlyTheHostCanRelayAndOnlyForSeatsRelayedByTheTv() {
        val s = setup(0)
        val (q, right) = question(s)
        val ack = { c: String, token: String -> s.r.handle(c, ClientMsg.RelayAct(token, q, right, 100, 4), 7_000).map { it.msg }.filterIsInstance<ServerMsg.Ack>().single().result }
        assertEquals("FORBIDDEN", ack("c0", s.relayToken), "un joueur distant ne relaie pas")
        assertEquals("UNKNOWN_PLAYER", ack("tv", "00000000000000000000000000000000"), "jeton inconnu")
        assertEquals("UNKNOWN_PLAYER", ack("tv", s.remoteToken), "la TV ne répond pas à la place d'un joueur qui a sa propre connexion")
        assertTrue(s.r.table(0).answerLog().isEmpty(), "aucun de ces refus ne laisse de trace de réponse")
    }
}
