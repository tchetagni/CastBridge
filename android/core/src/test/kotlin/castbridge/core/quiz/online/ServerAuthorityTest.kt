package castbridge.core.quiz.online

import castbridge.core.quiz.*
import castbridge.core.ux.SignalLevel
import kotlin.test.*

/**
 * `AuthorityContractTest` version mémoire : le MÊME scénario à graine (Duel à 3 joueurs, double réponse, rejeu) est joué par `LocalAuthority`
 * (QuizRoom direct) et par `ServerAuthority` sur un `PlayTransport` en mémoire branché sur `ServerRoom` ⇒ mêmes stage/phase/scores à chaque étape.
 * w20-03 rejouera la même chose sur socket.
 */
class ServerAuthorityTest {
    private val bank = EmbeddedQuestionSource().bank()
    private var now = 1_000L

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

    private inner class ServerHarness : Harness {
        val room = ServerRoom("r1", PlayScope.LAN, bank, java.util.Random(42), createdAt = 0, settings = ServerRoom.Settings(duelCount = 10))
        val hub = MemoryPlayHub(room) { now }
        val host = ServerAuthority(hub.connect("tv"), PlayScope.LAN).also { it.create(null, "DUEL") }
        val players = ArrayList<ServerAuthority>()
        override fun join(name: String): Int {
            val a = ServerAuthority(hub.connect("p${players.size}"), PlayScope.LAN)
            assertEquals(QuizRoom.Join.OK, a.join(room.code, name, null, null).status); players += a
            return players.lastIndex
        }
        override fun setupAndStart(seed: Long) {
            assertEquals(QuizRoom.Act.OK, host.act(null, "mode", null, null, "DUEL")); assertEquals(QuizRoom.Act.OK, host.act(null, "start", null, null, seed.toString()))
        }
        override fun act(player: Int, action: String, qid: String?, choice: Int?) = players[player].act(null, action, qid, choice, null)
        override fun skip() { host.act(null, "skip", null, null, null) }
        override fun phase() = room.table(0).room.duel!!.phase
        override fun question() = room.table(0).room.duel!!.question
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

    @Test fun sameScenarioSameStagePhaseAndScoresAtEveryStep() {
        val local = play(LocalHarness()); val server = play(ServerHarness())
        assertEquals(local, server)
        assertTrue(local.size > 50 && local.last().startsWith("final stage=FINISHED"))
    }

    @Test fun viewCarriesTheAdditiveSafetyKeyLikeLocalAuthority() {
        val h = ServerHarness(); h.join("Awa")
        val v = h.players[0].view(null)
        assertEquals("LAN", v.m("safety")["scope"]); assertEquals("GREEN", v.m("safety")["level"])
        assertEquals(SignalLevel.GREEN, h.players[0].safety().level)
    }

    @Test fun joinErrorsAreMappedToTheRoomEnums() {
        val h = ServerHarness()
        val a = ServerAuthority(h.hub.connect("x"), PlayScope.LAN)
        assertEquals(QuizRoom.Join.BAD_CODE, a.join("ZZZZZZZZ", "Awa", null, null).status)
        assertEquals(QuizRoom.Join.BAD_NAME, a.join(h.room.code, "   ", null, null).status)
        repeat(8) { h.join("J$it") }
        assertEquals(QuizRoom.Join.FULL, ServerAuthority(h.hub.connect("y"), PlayScope.LAN).join(h.room.code, "Neuvième", null, null).status)
    }

    @Test fun awaitChangeReturnsTheRoomSeqAndSendsNothingToTheSocketLayer() {
        val h = ServerHarness(); h.join("Awa")
        val before = h.players[0].lastSeq
        h.join("Bello")
        assertTrue(h.players[0].awaitChange(before, 50) > before)
        val now = h.players[0].lastSeq
        assertEquals(now, h.players[0].awaitChange(now, 20), "pas de changement : rend la main au bout du délai")
    }

    @Test fun repliesToPingsAndTheServerMeasuresTheRttFromThem() {
        val h = ServerHarness(); h.join("Awa")
        now = 6_000; h.hub.tick()
        assertTrue(h.room.rtt.known("p0"), "le client a répondu au ping par un pong")
    }

    @Test fun localOutboxIsBoundedWhileConnecting() {
        val sent = ArrayList<String>()
        val t = object : PlayTransport {
            override var state = PlayTransport.Status.CONNECTING
            override fun send(text: String) { sent += text }
            override fun onMessage(listener: (String) -> Unit) {}
        }
        val a = ServerAuthority(t)
        repeat(30) { assertEquals(QuizRoom.Act.IGNORED, a.act(null, "answer", "q$it", 1, null)) }
        assertTrue(sent.isEmpty())
        t.state = PlayTransport.Status.OPEN; a.onTransportOpen()
        assertEquals(ServerAuthority.MAX_OUTBOX, sent.size, "file bornée")
        assertTrue(sent.first().contains("\"q10\"") && sent.last().contains("\"q29\""), "les plus anciens sont perdus, l'ordre est gardé")
        t.state = PlayTransport.Status.CLOSED
        assertEquals(QuizRoom.Act.CLOSED, a.act(null, "answer", "q", 1, null))
    }

    @Test fun badMessageOverTheMemoryTransportAnswersAnErrorAndNeverReachesTheRoom() {
        val h = ServerHarness()
        val got = ArrayList<ServerMsg?>()
        val t = h.hub.connect("raw"); t.onMessage { got += PlayCodec.decodeServer(it) }
        t.send("""{"t":"teleport"}""")
        assertEquals(PlayProtocol.UNSUPPORTED, (got.single() as ServerMsg.Error).reason)
        t.send("x".repeat(3_000))
        assertEquals(PlayProtocol.BAD_REQUEST, (got.last() as ServerMsg.Error).reason)
    }
}
