package castbridge.play

import castbridge.core.quiz.EmbeddedQuestionSource
import castbridge.core.quiz.Question
import castbridge.core.quiz.QuizDuel
import castbridge.core.quiz.QuizRoom
import castbridge.core.quiz.online.LocalAuthority
import castbridge.core.quiz.online.PlayScope
import castbridge.core.quiz.online.PlayTransport
import castbridge.core.quiz.online.SafetyFacts
import castbridge.core.quiz.online.ServerAuthority
import castbridge.core.quiz.online.ServerRoom
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `AuthorityContractTest` sur SOCKET : le scénario à graine de w20-01/02 (Duel à 3 joueurs, double réponse, rejeu, réponse tardive) est joué par `LocalAuthority`
 * (QuizRoom direct) et par `ServerAuthority` sur un vrai `PlayTransport` WebSocket contre le service ⇒ mêmes résultats d'`act`, mêmes stage / phase / scores à chaque étape.
 * Périmètre LAN et horloge fournie par le test : le résultat ne dépend ni du réseau ni de l'heure réelle.
 */
class AuthorityContractSocketTest {
    private val bank = EmbeddedQuestionSource().bank()
    private var now = 1_000L
    private val servers = ArrayList<PlayServer>()
    private val transports = ArrayList<SyncSocketTransport>()
    @AfterTest fun stop() { transports.forEach { it.close() }; servers.forEach { it.close() } }

    /** `PlayTransport` sur un vrai WebSocket, rendu synchrone comme le transport en mémoire : `send` rend la main quand la réponse du serveur est arrivée et que le flux s'est calmé. */
    private class SyncSocketTransport(port: Int, xff: String) : PlayTransport {
        private val wire = WsWire(port, xff = xff)
        private var listener: ((String) -> Unit)? = null
        private val delivered = AtomicInteger()
        @Volatile private var running = true
        override val state get() = PlayTransport.Status.OPEN
        override fun onMessage(listener: (String) -> Unit) { this.listener = listener }
        private val reader = Thread { while (running) { val m = wire.next(100) ?: continue; listener?.invoke(m); lastDelivery.set(System.nanoTime()); delivered.incrementAndGet() } }.also { it.isDaemon = true; it.start() }

        override fun send(text: String) {
            val before = delivered.get()
            wire.send(text)
            if ("\"t\":\"pong\"" in text || "\"t\":\"hello\"" in text) return   // aucune réponse attendue
            val end = System.currentTimeMillis() + 3_000
            while (delivered.get() == before && System.currentTimeMillis() < end) Thread.sleep(2)
        }
        fun close() { running = false; wire.close() }
        companion object { val lastDelivery = AtomicLong(System.nanoTime()) }
    }

    /** Attend que plus aucun message n'arrive sur aucune socket (40 ms de calme). */
    private fun settle() { while ((System.nanoTime() - SyncSocketTransport.lastDelivery.get()) / 1_000_000 < 40) Thread.sleep(5) }

    @Suppress("UNCHECKED_CAST") private fun Map<String, Any?>.m(k: String) = this[k] as Map<String, Any?>

    private interface Harness {
        fun join(name: String): Int
        fun setupAndStart(seed: Long)
        fun act(player: Int, action: String, qid: String?, choice: Int?): QuizRoom.Act
        fun skip()
        fun phase(): QuizDuel.Phase
        fun question(): Question
        fun snapshot(): String
    }

    private fun scores(view: Map<String, Any?>): String = (view.m("duel")["ranking"] as List<*>).joinToString { r -> (r as Map<*, *>)["name"].toString() + "=" + r["score"] }

    private inner class LocalHarness : Harness {
        val room = QuizRoom(bank, clock = { now }, random = java.util.Random(42), autoTick = false)
        val auth = LocalAuthority(room) { SafetyFacts(PlayScope.LAN) }
        val tokens = ArrayList<String>()
        override fun join(name: String): Int { tokens += auth.join(room.code, name, null, null).player!!.token; return tokens.lastIndex }
        override fun setupAndStart(seed: Long) { assertTrue(room.setMode(QuizRoom.Mode.DUEL)); assertNull(room.startGame(seed)) }
        override fun act(player: Int, action: String, qid: String?, choice: Int?) = auth.act(tokens[player], action, qid, choice, null)
        override fun skip() { room.hostSkip() }
        override fun phase() = room.duel!!.phase
        override fun question() = room.duel!!.question
        override fun snapshot(): String { val v = auth.view(tokens[0]); return "stage=${v["stage"]} phase=${v.m("duel")["phase"]} ${scores(v)}" }
    }

    private inner class SocketHarness : Harness {
        val srv = PlayServer(PlayConfig(requireProof = false, webPlay = true, revocationsMode = castbridge.play.RevocationsMode.OFF, port = 0, trustedProxies = LOOPBACK, ticketPubKeys = listOf(TestKeys.pub), trustedKeys = TestRights.trustedKeys, createsPerIpPerHour = 10_000), clock = { now }, random = { java.util.Random(42) }, roomScope = PlayScope.LAN,
            settings = ServerRoom.Settings(duelCount = 10), bank = bank).start().also { servers += it }
        private fun transport(n: Int) = SyncSocketTransport(srv.port, "203.0.113.$n").also { transports += it }
        val host = ServerAuthority(transport(1), PlayScope.LAN).also { it.hello(null, TestKeys.ticket()); it.create(null, "DUEL", TestRights.PROD) }
        val players = ArrayList<ServerAuthority>()
        init { settle() }
        override fun join(name: String): Int {
            val a = ServerAuthority(transport(10 + players.size), PlayScope.LAN)
            assertEquals(QuizRoom.Join.OK, a.join(host.code, name, null, null).status); players += a; settle()
            return players.lastIndex
        }
        override fun setupAndStart(seed: Long) {
            assertEquals(QuizRoom.Act.OK, host.act(null, "mode", null, null, "DUEL")); assertEquals(QuizRoom.Act.OK, host.act(null, "start", null, null, seed.toString())); settle()
        }
        override fun act(player: Int, action: String, qid: String?, choice: Int?) = players[player].act(null, action, qid, choice, null).also { settle() }
        override fun skip() { host.act(null, "skip", null, null, null); settle() }
        override fun phase() = srv.rooms().single().table(0).room.duel!!.phase
        override fun question() = srv.rooms().single().table(0).room.duel!!.question
        override fun snapshot(): String { val v = players[0].view(null); return "stage=${v["stage"]} phase=${v.m("duel")["phase"]} ${scores(v)}" }
    }

    private fun play(h: Harness): List<String> {
        now = 1_000L
        val log = ArrayList<String>()
        val ps = listOf("Awa", "Bello", "Carine").map { h.join(it) }
        h.setupAndStart(2); log += "start " + h.snapshot()
        var guard = 0
        while (h.phase() != QuizDuel.Phase.FINISHED && guard++ < 200) {
            val q = h.question()
            if (h.phase() == QuizDuel.Phase.QUESTION) {
                now += 1_500
                log += "A:" + h.act(ps[0], "answer", q.id, q.answer); log += "A2:" + h.act(ps[0], "answer", q.id, q.answer)          // réponse en double
                log += "B:" + h.act(ps[1], "answer", q.id, (q.answer + 1) % 4); log += "Breplay:" + h.act(ps[1], "answer", q.id, (q.answer + 1) % 4)  // rejeu
                log += "late:" + h.act(ps[2], "answer", "q-inconnue", 0)
                log += "answered " + h.snapshot()
            }
            h.skip(); now += 500; log += "skip " + h.snapshot()
        }
        log += "final " + h.snapshot()
        return log
    }

    @Test fun sameScenarioSameResultsAndSameStagePhaseAndScoresOverARealSocket() {
        val local = play(LocalHarness()); val server = play(SocketHarness())
        assertEquals(local, server)
        assertTrue(local.size > 50 && local.last().startsWith("final stage=FINISHED"), local.last())
    }

    @Test fun twoClientsOfTheSameSeatTokenSeeTheSameState() {
        val h = SocketHarness(); val a = h.join("Awa")
        h.join("Bello"); h.setupAndStart(2)
        val token = h.players[a].token!!
        val second = ServerAuthority(transport(40), PlayScope.LAN)
        second.resume(h.players[a].roomId!!, token, 0); settle()
        assertEquals(h.players[a].view(null).m("duel")["phase"], second.view(null).m("duel")["phase"])
        assertEquals(h.players[a].lastSeq.coerceAtLeast(second.lastSeq), second.lastSeq, "la reprise donne l'état courant")
    }

    private fun transport(n: Int) = SyncSocketTransport(servers.single().port, "203.0.113.$n").also { transports += it }
}
