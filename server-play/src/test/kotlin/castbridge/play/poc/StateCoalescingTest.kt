package castbridge.play.poc

import castbridge.core.quiz.EmbeddedQuestionSource
import castbridge.core.quiz.Json
import castbridge.core.quiz.online.ClientMsg
import castbridge.core.quiz.online.EventRing
import castbridge.core.quiz.online.PlayCodec
import castbridge.core.quiz.online.PlayProtocol
import castbridge.core.quiz.online.PlayRole
import castbridge.core.quiz.online.PlayScope
import castbridge.core.quiz.online.SafetyView
import castbridge.core.quiz.online.ServerMsg
import castbridge.core.quiz.online.ServerRoom
import castbridge.core.ux.SignalLevel
import castbridge.play.Bot
import castbridge.play.ConnectionLimits
import castbridge.play.FbConn
import castbridge.play.HubFixture
import castbridge.play.LOOPBACK
import castbridge.play.OriginCheck
import castbridge.play.PlayConfig
import castbridge.play.PlayFallbackController
import castbridge.play.PlayHub
import castbridge.play.PlayServer
import castbridge.play.RevocationsMode
import castbridge.play.TestKeys
import castbridge.play.TestRights
import castbridge.play.WsConn
import castbridge.play.dev
import castbridge.play.entitlement.TicketVerifier
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * w20-04b : coalescence « dernier état seulement » de la file de sortie de chaque connexion. Sur une liaison EDGE (40 kbps ≈ 5 Ko/s), une rafale de 8 réponses ne doit pas mettre en file
 * 8 vues complètes (≈ 5 Ko chacune) DEVANT la révélation : un `state` en file est remplacé par le suivant ; AUCUN autre type n'est jamais retiré ni réordonné.
 */
class StateCoalescingTest {
    private val closeables = ArrayList<AutoCloseable>()
    private val servers = ArrayList<PlayServer>()
    private val bots = ArrayList<Bot>()
    @AfterTest fun stop() { bots.forEach { it.stop() }; servers.forEach { it.close() }; closeables.forEach { runCatching { it.close() } } }

    private val cfg = PlayConfig(port = 0, ticketPubKeys = listOf(TestKeys.pub), trustedKeys = TestRights.trustedKeys, webPlay = true, revocationsMode = RevocationsMode.OFF)

    /** Une vue d'environ 5 Ko (huit joueurs). */
    private fun state(seq: Int) = PlayCodec.encode(ServerMsg.State(seq.toLong(), linkedMapOf("pad" to "x".repeat(5_000), "n" to seq), false))
    private val welcome = PlayCodec.encode(ServerMsg.Welcome(1, "r1", "K7M2QX4T", "t".repeat(32), PlayRole.SPECTATOR, null, PlayProtocol.PROTO, PlayProtocol.CAPS))
    private val safety = PlayCodec.encode(ServerMsg.Safety(1, SafetyView(PlayScope.INTERNET, SignalLevel.GREEN, "Partie sûre", "Internet", null, emptyList())))
    private fun question(i: Int) = PlayCodec.encode(ServerMsg.Question(i.toLong(), "q$i", i, 10, "Question $i ?", listOf("a", "b", "c", "d"), 1_000L, 900L, 20_000L))
    private fun reveal(i: Int) = PlayCodec.encode(ServerMsg.Reveal(i.toLong(), "q$i", i, 2, "parce que"))
    private fun ack(i: Int) = PlayCodec.encode(ServerMsg.Ack(i.toLong(), i.toLong(), "OK"))
    private val ping = PlayCodec.encode(ServerMsg.Ping(1, "p1", 1L))
    private val error = PlayCodec.encode(ServerMsg.Error(0, "PLAY_BUSY", "occupé", true))
    private val gone = PlayCodec.encode(ServerMsg.RoomGone(9, "IDLE"))
    private val replay = PlayCodec.encode(ServerMsg.Replay(2, listOf(EventRing.Event(1L, "joined", emptyMap()))))
    private fun type(text: String) = (Json.parse(text) as Map<*, *>)["t"] as String

    /** Ce que le service met en file pour une TV relais pendant une question : annonce, puis 8 × (accusé + vue), révélation + vue, tous les autres types, et l'ordre exact attendu. */
    private fun burst(): List<String> {
        val l = ArrayList<String>()
        l += welcome; l += safety; l += question(0); l += state(0); l += ping
        for (i in 1..8) { l += ack(i); l += state(i) }
        l += error; l += replay; l += reveal(0); l += state(9); l += ack(9); l += gone
        return l
    }

    private fun expectedWithoutStaleStates(offered: List<String>): List<String> {
        val lastState = offered.last { type(it) == "state" }
        return offered.filter { type(it) != "state" || it === lastState }
    }

    private fun fbConn(): FbConn {
        val hub = HubFixture.hub(cfg)
        val owner = PlayFallbackController(cfg, hub, ConnectionLimits(100, 100), TicketVerifier(emptyList()), OriginCheck(emptySet()))
        return FbConn("fb1", "203.0.113.5", cfg, owner)
    }

    private fun wsConn(): WsConn {
        val ss = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).also { closeables += it }
        val client = Socket("127.0.0.1", ss.localPort).also { closeables += it }
        val server = ss.accept().also { closeables += it }
        return WsConn("w1", "203.0.113.6", server, cfg, HubFixture.hub(cfg))
    }

    @Test fun aSlowFallbackConnectionQueuesAtMostOneStateAndNeverReordersOrDropsTheOthers() {
        val c = fbConn()
        val offered = burst()
        for (m in offered) assertTrue(c.offer(m), "la file n'a pas débordé")
        val items = c.poll(0, 0)!!
        assertEquals(1, items.count { type(it.text) == "state" }, "au plus UN état en file")
        // aucun état périmé devant la révélation : le seul état est APRÈS elle
        val iReveal = items.indexOfFirst { type(it.text) == "reveal" }
        assertTrue(items.indexOfFirst { type(it.text) == "state" } > iReveal, "l'état est celui d'après la révélation")
        // tous les autres types : présents, dans l'ordre exact (question < reveal < ack conservé), jamais retirés
        assertEquals(expectedWithoutStaleStates(offered), items.map { it.text })
        val types = items.map { type(it.text) }
        assertTrue(types.indexOf("question") < types.indexOf("reveal") && types.indexOf("reveal") < types.lastIndexOf("ack"))
        // indices strictement croissants (un trou est permis)
        assertTrue(items.zipWithNext().all { (x, y) -> x.idx < y.idx }, items.map { it.idx }.toString())
        // octets en file ≤ 2 × un état + les messages non coalescibles
        val stateBytes = state(0).toByteArray().size
        val others = items.filter { type(it.text) != "state" }.sumOf { it.bytes }
        assertTrue(items.sumOf { it.bytes } <= 2 * stateBytes + others, "octets en file : ${items.sumOf { it.bytes }} pour un état de $stateBytes")
        assertTrue(items.sumOf { it.bytes } < offered.sumOf { it.toByteArray().size } / 3, "bien moins que les 9 états offerts")
    }

    @Test fun longPollAfterCoalescenceLosesNoNonStateMessage() {
        val c = fbConn()
        val received = ArrayList<String>()
        val offered = ArrayList<String>()
        fun offer(m: String) { offered += m; assertTrue(c.offer(m)) }
        fun pollOnce(since: Long): Long { val it = c.poll(since, 0)!!; received += it.filter { it.idx > since }.map { it.text }; return it.lastOrNull()?.idx ?: since }
        offer(welcome); offer(question(0)); offer(state(0))
        var since = pollOnce(0)                        // le client a reçu welcome, question, état 0 (pas encore accusés)
        offer(ack(1)); offer(state(1)); offer(state(2)); offer(reveal(0)); offer(state(3))
        since = pollOnce(since)                        // accuse jusqu'à `since`, reçoit la suite : l'état périmé a été remplacé
        offer(question(1)); offer(state(4)); offer(ack(2))
        pollOnce(since)
        assertEquals(offered.filter { type(it) != "state" }, received.filter { type(it) != "state" }, "aucun message non-état perdu ni réordonné, rien reçu deux fois")
        assertEquals("state", type(received.last { type(it) == "state" }), "le dernier état est bien arrivé")
        val last = received.last { type(it) == "state" }
        assertEquals(state(4), last, "et c'est le DERNIER état offert")
    }

    @Test fun aWebSocketQueueKeepsOneStateToo() {
        val c = wsConn()
        val offered = burst()
        for (m in offered) assertTrue(c.offer(m))
        val queued = c.queuedTexts()
        assertEquals(1, queued.count { type(it) == "state" }, "au plus un état en file d'écriture")
        assertEquals(expectedWithoutStaleStates(offered), queued, "les autres types intacts et dans l'ordre")
        val stateBytes = state(0).toByteArray().size
        val others = queued.filter { type(it) != "state" }.sumOf { it.toByteArray().size }
        assertTrue(c.queuedBytes() <= 2 * stateBytes + others, "octets en file : ${c.queuedBytes()}")
        assertEquals(queued.sumOf { it.toByteArray().size }, c.queuedBytes(), "le compte d'octets est corrigé à chaque remplacement")
    }

    @Test fun aSlowTvInARealRoomKeepsOneStateBehindTheRevealAfterAnEightAnswerBurst() {
        val srv = PlayServer(PlayConfig(port = 0, trustedProxies = LOOPBACK, ticketPubKeys = listOf(TestKeys.pub), trustedKeys = TestRights.trustedKeys, createsPerIpPerHour = 10_000,
            webPlay = false, revocationsMode = RevocationsMode.OFF), settings = ServerRoom.Settings(duelCount = 3, seatsPerTable = 16)).start().also { servers += it }
        val bank = EmbeddedQuestionSource().bank()
        val ta = TestKeys.ticket(deviceCode = TestRights.tv.code, lifeMs = 600_000)
        val a = Bot(null, TvWire(srv.port, ta), bank, host = true).also { bots += it }
        castbridge.play.hostStart(a, ta)
        val end = System.currentTimeMillis() + 10_000
        while (a.roomCode == null && System.currentTimeMillis() < end) Thread.sleep(20)
        val code = a.roomCode ?: fail("salle non créée ${a.errors}")
        // la TV B est LENTE : elle parle (POST) mais n'ouvre aucun flux, sa file de sortie ne se vide jamais
        val tb = TestKeys.ticket(deviceCode = TestRights.otherTv.code, lifeMs = 600_000)
        val b = TvWire(srv.port, tb, openStream = false); closeables += AutoCloseable { b.abort() }
        b.send(PlayCodec.encode(ClientMsg.Hello(PlayProtocol.PROTO, PlayProtocol.CAPS, null, tb)))
        b.send(PlayCodec.encode(ClientMsg.Join(code, "TV lente", null, dev(), true, TestRights.activation(device = TestRights.otherTv))))
        repeat(8) { b.send(PlayCodec.encode(ClientMsg.Join(code, "Joueur${it + 1}", null, dev(), false))) }
        a.send(ClientMsg.Act(null, "start", null, "5", 99))
        val waitQ = System.currentTimeMillis() + 10_000
        while (a.seen[0] == null && System.currentTimeMillis() < waitQ) Thread.sleep(20)
        val sq = a.seen[0] ?: fail("pas de question")
        val tokens = b.backlog().filter { it["t"] == "welcome" }.drop(1).map { it["token"] as String }
        assertEquals(8, tokens.size)
        val right = bank.all.first { it.id == sq.questionId }.let { q -> sq.choices.indexOf(q.choices[q.answer]) }
        Thread.sleep(maxOf(0L, sq.receivedLocalMs + (sq.opensAtServerMs - sq.serverNowMs) + 300 - System.currentTimeMillis()))
        val seqs = AtomicLong(5_000)
        val mine = tokens.map { tok -> seqs.incrementAndGet().also { s -> b.send(PlayCodec.encode(ClientMsg.RelayAct(tok, sq.questionId, right, 400, s))) } }   // la rafale : 8 réponses relayées
        val deadline = System.currentTimeMillis() + 10_000
        while (a.raws.none { type(it) == "reveal" } && System.currentTimeMillis() < deadline) Thread.sleep(20)
        Thread.sleep(300)
        val q = b.backlog()
        val types = q.map { it["t"] as String }
        assertEquals(1, types.count { it == "state" }, "la TV lente a UN seul état en file : $types")
        assertTrue(types.indexOf("state") > types.indexOf("reveal"), "aucun état périmé devant la révélation : $types")
        assertEquals(8, q.count { it["t"] == "ack" && (it["ref"] as Number).toLong() in mine && it["result"] == "OK" }, "les 8 accusés sont tous là")
        assertTrue(types.indexOf("question") < types.indexOf("reveal"))
    }
}
