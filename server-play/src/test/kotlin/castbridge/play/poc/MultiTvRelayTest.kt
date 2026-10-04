package castbridge.play.poc

import castbridge.core.quiz.EmbeddedQuestionSource
import castbridge.core.quiz.Json
import castbridge.core.quiz.online.ClientMsg
import castbridge.core.quiz.online.PlayProtocol
import castbridge.core.quiz.online.ServerRoom
import castbridge.play.Bot
import castbridge.play.LOOPBACK
import castbridge.play.PlayConfig
import castbridge.play.PlayServer
import castbridge.play.RevocationsMode
import castbridge.play.TestKeys
import castbridge.play.TestRights
import castbridge.play.dev
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * w20-04b, sur le VRAI service (port aléatoire, transport de repli SSE + POST sans `Origin`, `X-Play-Ticket` / `X-Play-Conn`) : deux CastBridge-TV jouent un Duel ensemble ; chacune relaie
 * SES deux joueurs locaux ; une TV ne répond jamais pour les joueurs de l'autre ; plafond de 8 sièges relayés par TV ; perte d'une TV invitée sans pause de la table ; reprise par `resume`.
 */
class MultiTvRelayTest {
    private val servers = ArrayList<PlayServer>()
    private val bots = ArrayList<Bot>()
    @AfterTest fun stop() { bots.forEach { it.stop() }; servers.forEach { it.close() } }

    private val bank = EmbeddedQuestionSource().bank()
    private val tvA = TestRights.tv
    private val tvB = TestRights.otherTv
    private val seqs = AtomicLong(1_000)

    private fun server(cfg: PlayConfig = config(), duel: Int = 3): PlayServer =
        PlayServer(cfg, settings = ServerRoom.Settings(duelCount = duel, seatsPerTable = 16)).start().also { servers += it }

    /** [idleMs] : une session de repli sans nouvelle depuis ce délai est fermée (pour détecter vite une TV perdue) ; sans, les réglages de production. */
    private fun config(idleMs: Long? = null) = PlayConfig(requireProof = false, port = 0, trustedProxies = LOOPBACK, ticketPubKeys = listOf(TestKeys.pub), trustedKeys = TestRights.trustedKeys, createsPerIpPerHour = 10_000,
        webPlay = false, revocationsMode = RevocationsMode.OFF, maxRoomsPerSubject = 5, pollMs = if (idleMs == null) 25_000 else 300, fallbackIdleMs = idleMs ?: 40_000, tickMs = if (idleMs == null) 200 else 50)

    private fun ticket(tv: TestRights.Tv) = TestKeys.ticket(deviceCode = tv.code, lifeMs = 10 * 60_000L)

    private fun welcomes(b: Bot) = b.raws.map { Json.parse(it) as Map<*, *> }.filter { it["t"] == "welcome" }
    private fun waitUntil(what: String, ms: Long = 10_000, f: () -> Boolean) {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) { if (f()) return; Thread.sleep(20) }
        fail(what)
    }

    /** La TV hôte : `hello` (ticket) puis `create` (activation) ; rend le code de la salle. */
    private fun openHost(srv: PlayServer): Pair<Bot, String> {
        val t = ticket(tvA)
        val a = Bot(null, TvWire(srv.port, t), bank, host = true).also { bots += it }
        castbridge.play.hostStart(a, t)
        waitUntil("salle non créée : ${a.errors} ${a.wire.failure}") { a.roomCode != null }
        return a to a.roomCode!!
    }

    /** La TV invitée : `hello` (ticket) puis `join` AVEC son activation ; spectatrice relais. */
    private fun openGuest(srv: PlayServer, code: String, tv: TestRights.Tv = tvB): Bot {
        val t = ticket(tv)
        val b = Bot(null, TvWire(srv.port, t), bank).also { bots += it }
        b.send(ClientMsg.Hello(PlayProtocol.PROTO, PlayProtocol.CAPS, null, t))
        b.send(ClientMsg.Join(code, "TV ${tv.name}", null, dev(), true, TestRights.activation(device = tv)))
        waitUntil("la TV invitée n'entre pas : ${b.errors} ${b.wire.failure}") { welcomes(b).isNotEmpty() || b.errors.isNotEmpty() }
        return b
    }

    /** Un joueur local relayé par la TV [b] ; rend son jeton de siège. */
    private fun relayJoin(b: Bot, code: String, name: String): String {
        val n = welcomes(b).size
        b.send(ClientMsg.Join(code, name, null, dev(), false))
        waitUntil("$name : pas de siège relayé (${b.errors})") { welcomes(b).size > n }
        return welcomes(b)[n]["token"] as String
    }

    private fun rightIndex(qid: String, choices: List<String>): Int { val q = bank.all.first { it.id == qid }; return choices.indexOf(q.choices[q.answer]) }

    /** Les téléphones d'une TV répondent par elle : un joueur, son jeton, la réussite de la question i, son délai. */
    private class Phone(val name: String, val token: String, val correct: (Int) -> Boolean, val delayMs: Long)

    private fun drive(tv: Bot, phones: List<Phone>, questions: Int): Thread = Thread {
        for (i in 0 until questions) {
            val end = System.currentTimeMillis() + 30_000
            while (tv.seen[i] == null && System.currentTimeMillis() < end) Thread.sleep(10)
            val sq = tv.seen[i] ?: return@Thread
            for (p in phones) Thread {
                val wait = sq.receivedLocalMs + (sq.opensAtServerMs - sq.serverNowMs) + p.delayMs - System.currentTimeMillis()
                if (wait > 0) Thread.sleep(wait)
                val right = rightIndex(sq.questionId, sq.choices)
                tv.send(ClientMsg.RelayAct(p.token, sq.questionId, if (p.correct(i)) right else (right + 1) % 4, p.delayMs, seqs.incrementAndGet()))
            }.also { it.isDaemon = true }.start()
        }
    }.also { it.isDaemon = true; it.start() }

    @Test fun twoTvsPlayOneDuelEachRelayingItsOwnPhonesToACommonRanking() {
        val srv = server()
        val (a, code) = openHost(srv)
        val b = openGuest(srv, code)
        assertEquals("SPECTATOR", welcomes(b).first()["role"], "la TV invitée est une spectatrice relais")
        val a1 = relayJoin(a, code, "Alice"); val a2 = relayJoin(a, code, "Ann")
        val b1 = relayJoin(b, code, "Carl"); val b2 = relayJoin(b, code, "Dan")
        // la TV B tente de répondre pour Alice (joueur de la TV A) : refusé, rien n'est compté
        val probe = seqs.incrementAndGet()
        b.send(ClientMsg.RelayAct(a1, "q-quelconque", 0, 100, probe))
        waitUntil("ack de la TV B") { b.ackOf(probe) != null }
        assertEquals("FORBIDDEN", b.ackOf(probe), "une TV ne relaie jamais les joueurs d'une autre")
        a.send(ClientMsg.Act(null, "start", null, "5", 99))
        val da = drive(a, listOf(Phone("Alice", a1, { true }, 200), Phone("Ann", a2, { it < 1 }, 220)), 3)
        val db = drive(b, listOf(Phone("Carl", b1, { it < 2 }, 210), Phone("Dan", b2, { false }, 230)), 3)
        a.waitFinished(90_000); b.waitFinished(90_000)
        da.join(2_000); db.join(2_000)
        assertEquals(listOf("Alice", "Carl", "Ann", "Dan"), a.finalRanking, "classement commun vu par la TV hôte")
        assertEquals(a.finalRanking, b.finalRanking, "et par la TV invitée")
        assertEquals(1, srv.rooms().size)
        assertTrue(a.errors.isEmpty() && b.errors.isEmpty(), "aucune erreur : A ${a.errors} B ${b.errors}")
    }

    @Test fun aTvCannotRelayMoreThanEightSeatsEvenWithSixteenSeatsAtTheTable() {
        val srv = server()
        val (_, code) = openHost(srv)
        val b = openGuest(srv, code)
        repeat(8) { relayJoin(b, code, "Joueur${it + 1}") }
        val n = welcomes(b).size
        b.send(ClientMsg.Join(code, "Neuvieme", null, dev(), false))
        waitUntil("9e siège : ni refus ni siège") { b.errors.isNotEmpty() || welcomes(b).size > n }
        assertEquals(n, welcomes(b).size, "le 9e siège n'est pas accordé")
        assertEquals("PLAY_ROOM_FULL", b.errors.last())
        assertEquals(8, srv.rooms().single().table(0).room.players().size)
    }

    @Test fun aLostGuestTvLeavesItsPlayersAbsentThenResumeBringsThemBackWithoutPausingTheTable() {
        val srv = server(config(idleMs = 1_200))
        val (a, code) = openHost(srv)
        val b = openGuest(srv, code)
        relayJoin(a, code, "Alice"); relayJoin(a, code, "Ann"); relayJoin(b, code, "Carl"); relayJoin(b, code, "Dan")
        val room = srv.rooms().single()
        fun streams(name: String) = room.table(0).room.players().first { it.name == name }.streams
        assertEquals(listOf(1, 1, 1, 1), listOf("Alice", "Ann", "Carl", "Dan").map { streams(it) })
        val tokenB = welcomes(b).first()["token"] as String
        (b.wire as TvWire).abort()
        waitUntil("la TV B n'est pas détectée perdue", 15_000) { streams("Carl") == 0 && streams("Dan") == 0 }
        assertEquals(1, streams("Alice")); assertEquals(1, streams("Ann"), "les joueurs de la TV A restent présents")
        assertNull(room.table(0).pausedAt, "la perte d'une TV invitée ne met pas la table en pause")
        // la TV B revient par `resume` (jeton de siège de 128 bits, aucun ticket redemandé pour la reprise elle-même)
        val t = ticket(tvB)
        val b2 = Bot(null, TvWire(srv.port, t), bank).also { bots += it }
        b2.send(ClientMsg.Resume(room.roomId, tokenB, 0))
        waitUntil("reprise : les joueurs de la TV B ne reviennent pas (${b2.errors})", 10_000) { streams("Carl") == 1 && streams("Dan") == 1 }
        assertEquals(1, streams("Alice")); assertNull(room.table(0).pausedAt)
    }

    @Test fun aLostHostWithAnotherTvsPlayersPresentDoesNotPauseTheTable() {
        val srv = server(config(idleMs = 1_200))
        val (a, code) = openHost(srv)
        val b = openGuest(srv, code)
        relayJoin(a, code, "Alice"); relayJoin(b, code, "Carl"); relayJoin(b, code, "Dan")
        a.send(ClientMsg.Act(null, "start", null, "5", 99))
        val room = srv.rooms().single()
        waitUntil("la partie ne démarre pas") { room.phase() == ServerRoom.State.PLAYING }
        (a.wire as TvWire).abort()
        waitUntil("la TV hôte n'est pas détectée perdue", 15_000) { room.table(0).room.players().first { it.name == "Alice" }.streams == 0 }
        Thread.sleep(500)
        assertNull(room.table(0).pausedAt, "des joueurs d'une autre TV sont présents : pas de pause (abandon à 60 s, règle existante)")
        assertNull(room.table(0).abandoned)
        assertNotNull(room.table(0).room.players().first { it.name == "Carl" })
    }
}
