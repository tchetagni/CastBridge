package castbridge.core.quiz.online

import castbridge.core.quiz.*
import java.util.PriorityQueue
import kotlin.test.*

/**
 * Exigence du propriétaire (2026-10-03) : « entre 2 questions laisse 1 ou 2 s de latence pour pouvoir synchroniser les parties en ligne ».
 * Table de périmètres + simulation déterministe (horloge factice) de 8 joueurs avec RTT 0 / 150 / 600 ms (+ un à 1 200 ms).
 */
class ServerRoomTimingTest {
    private val bank = EmbeddedQuestionSource().bank()
    private fun room(scope: PlayScope, count: Int = 4) =
        ServerRoom("r1", scope, bank, java.util.Random(7), createdAt = 0, settings = ServerRoom.Settings(duelCount = count))

    private fun List<ServerRoom.Out>.questions() = map { it.msg }.filterIsInstance<ServerMsg.Question>()
    private fun List<ServerRoom.Out>.acks() = map { it.msg }.filterIsInstance<ServerMsg.Ack>()

    private fun ServerRoom.lobby(vararg names: String): ServerRoom {
        handle("tv", ClientMsg.Create(null, "DUEL"), 0)
        names.forEachIndexed { i, n -> handle("c$i", ClientMsg.Join(code, n, null, null, false), 0) }
        handle("tv", ClientMsg.Act(null, "mode", null, "DUEL", 1), 0)
        return this
    }

    /** Joue un Duel sur l'horloge du SERVEUR : par question, (heure d'annonce, opensAt). */
    private fun announcements(scope: PlayScope, count: Int): List<Pair<Long, Long>> {
        val r = room(scope, count).lobby("Awa")
        var now = 1_000L
        val seen = LinkedHashMap<Int, Pair<Long, Long>>()
        fun note(os: List<ServerRoom.Out>, at: Long) = os.questions().forEach { seen.putIfAbsent(it.index, at to it.opensAtServerMs) }
        note(r.handle("tv", ClientMsg.Act(null, "start", null, "3", 2), now), now)
        var guard = 0
        while (r.phase() != ServerRoom.State.FINISHED && guard++ < 20_000) {
            now += 50
            note(r.tick(now), now)
            val q = r.currentQuestionId()
            if (q != null && r.table(0).duelPhase() == QuizDuel.Phase.QUESTION && now >= r.table(0).opensAtServerMs)
                r.handle("c0", ClientMsg.Act(q, "answer", 0, null, 9), now)
        }
        assertEquals(ServerRoom.State.FINISHED, r.phase())
        return seen.values.toList()
    }

    @Test fun gapTablePerScope() {
        for ((scope, expectedGap) in listOf(PlayScope.INTERNET to 1_500L, PlayScope.LAN to 0L, PlayScope.TV_ONLY to 0L)) {
            val a = announcements(scope, 4)
            assertEquals(4, a.size, "$scope : une annonce par question, aucune après la dernière")
            assertEquals(0L, a[0].second - a[0].first, "$scope : la première question n'a pas de délai")
            for (i in 1 until a.size) assertEquals(expectedGap, a[i].second - a[i].first, "$scope : délai avant la question ${i + 1}")
        }
    }

    @Test fun requestedGapIsClampedToOneToTwoSeconds() {
        for ((asked, got) in listOf(0L to 1_000L, 1_000L to 1_000L, 1_700L to 1_700L, 2_000L to 2_000L, 9_999L to 2_000L)) {
            val r = ServerRoom("r", PlayScope.INTERNET, bank, java.util.Random(7), 0, ServerRoom.Settings(duelCount = 3), gapRequestedMs = asked).lobby("Awa")
            var now = 0L
            r.handle("tv", ClientMsg.Act(null, "start", null, "3", 2), now)
            var q2: ServerMsg.Question? = null
            while (q2 == null && now < 600_000) { now += 50; q2 = r.tick(now).questions().firstOrNull { it.index == 1 } }
            assertEquals(got, q2!!.opensAtServerMs - now, "demandé $asked")
        }
    }

    @Test fun tooEarlyIsRefusedNeverCountedAndLateJoinerGetsTheAbsoluteOpensAt() {
        val r = room(PlayScope.INTERNET, 3).lobby("Awa", "Bello")
        var now = 0L
        r.handle("tv", ClientMsg.Act(null, "start", null, "3", 2), now)
        var q2: ServerMsg.Question? = null
        while (q2 == null) { now += 50; q2 = r.tick(now).questions().firstOrNull { it.index == 1 }; if (now > 600_000) fail("pas de deuxième question") }
        val reveal = now
        assertEquals(reveal + 1_500, q2.opensAtServerMs)
        val early = r.handle("c0", ClientMsg.Act(q2.questionId, "answer", 0, null, 3), reveal + 1_499).acks().single()
        assertEquals("TOO_EARLY", early.result)
        assertEquals(0, r.table(0).answeredCount(), "une réponse trop tôt n'est jamais comptée")
        val late = r.handle("late", ClientMsg.Join(r.code, "Carine", null, null, false), reveal + 500)
        assertEquals(q2.opensAtServerMs, late.filter { it.to == "late" }.questions().single().opensAtServerMs, "le retardataire reçoit l'opensAt ABSOLU")
        assertEquals("OK", r.handle("c0", ClientMsg.Act(q2.questionId, "answer", 0, null, 4), q2.opensAtServerMs + 1_000).acks().single().result)
        assertEquals(1_000L, r.table(0).lastElapsedMs("c0"), "temps compté depuis opensAtServerMs (RTT 0)")
        // le retardataire à 2 s après l'ouverture : accepté, temps depuis opensAt (pas depuis son arrivée)
        assertEquals("OK", r.handle("late", ClientMsg.Act(q2.questionId, "answer", 1, null, 1), q2.opensAtServerMs + 2_000).acks().single().result)
        assertEquals(2_000L, r.table(0).lastElapsedMs("late"))
    }

    @Test fun lanRoomIsUnchangedAnswersAcceptedAtOnce() {
        val r = room(PlayScope.LAN, 3).lobby("Awa")
        var now = 0L
        r.handle("tv", ClientMsg.Act(null, "start", null, "3", 2), now)
        var q2: ServerMsg.Question? = null
        while (q2 == null) { now += 50; q2 = r.tick(now).questions().firstOrNull { it.index == 1 }; if (now > 600_000) fail("pas de deuxième question") }
        assertEquals(now, q2.opensAtServerMs)
        assertEquals("OK", r.handle("c0", ClientMsg.Act(q2.questionId, "answer", 0, null, 3), now).acks().single().result)
    }

    // ------------------------------------------------------------------ simulation : 8 joueurs, RTT 0 / 150 / 600 (+ 1 200), horloge factice

    private class Ev(val at: Long, val n: Long, val run: () -> Unit)

    private class Sim(val r: ServerRoom, val rtts: List<Int>, val thinks: List<Long>) {
        val q = PriorityQueue<Ev>(compareBy<Ev>({ it.at }, { it.n }))
        var now = 0L; private var n = 0L
        val conns = rtts.indices.map { "c$it" }
        val seenOpens = HashMap<Int, MutableMap<String, Long>>()   // question -> conn -> opensAt reçu
        val announcedAt = HashMap<Int, Long>()                     // question -> heure serveur de l'annonce
        val opens = HashMap<Int, Long>()
        var running = true

        fun at(t: Long, f: () -> Unit) { q += Ev(t, n++, f) }
        fun oneWay(c: Int) = rtts[c] / 2L

        fun deliver(outs: List<ServerRoom.Out>) {
            val sentAt = now
            for (o in outs) {
                val c = conns.indexOf(o.to); if (c < 0) continue
                if (o.msg is ServerMsg.Question) { announcedAt.putIfAbsent(o.msg.index, sentAt); opens[o.msg.index] = o.msg.opensAtServerMs }
                at(sentAt + oneWay(c)) { clientReceives(c, o.msg) }
            }
        }

        private fun clientReceives(c: Int, m: ServerMsg) {
            when (m) {
                is ServerMsg.Ping -> at(now + oneWay(c)) { deliver(r.handle(conns[c], ClientMsg.Pong(m.id), now)) }
                is ServerMsg.Question -> {
                    seenOpens.getOrPut(m.index) { HashMap() }[conns[c]] = m.opensAtServerMs
                    // le client connaît son RTT (aller-retour ping/pong) : il attend (opensAt − heure serveur de l'annonce) moins le trajet déjà fait
                    val wait = (m.opensAtServerMs - m.serverNowMs - oneWay(c)).coerceAtLeast(0)
                    at(now + wait + thinks[c]) { at(now + oneWay(c)) { deliver(r.handle(conns[c], ClientMsg.Act(m.questionId, "answer", 0, null, 100), now)) } }
                }
                else -> {}
            }
        }

        fun tickLoop(from: Long) { at(from) { if (running) { deliver(r.tick(now)); tickLoop(now + 50) } } }
        fun run(limit: Long) { while (q.isNotEmpty() && now < limit && r.phase() != ServerRoom.State.FINISHED) { val e = q.poll(); now = e.at; e.run() } }
    }

    @Test fun eightPlayersWithDifferentLatencyAreSynchronisedAndFair() {
        val rtts = listOf(0, 0, 0, 150, 150, 150, 600, 1_200)
        val thinks = listOf(900L, 1_300L, 1_700L, 800L, 1_200L, 1_600L, 1_100L, 1_000L)   // vrai temps entre « je peux répondre » et l'appui
        val r = room(PlayScope.INTERNET, 5)
        val s = Sim(r, rtts, thinks)
        r.handle("tv", ClientMsg.Create(null, "DUEL"), 0)
        s.conns.forEachIndexed { i, c -> r.handle(c, ClientMsg.Join(r.code, "J$i", null, null, false), 0) }
        r.handle("tv", ClientMsg.Act(null, "mode", null, "DUEL", 1), 0)
        s.tickLoop(50)
        s.at(31_000) { s.deliver(r.handle("tv", ClientMsg.Act(null, "start", null, "11", 5), s.now)) }   // 30 s de ping/pong d'abord (RttBook)
        s.run(900_000)
        s.running = false
        assertEquals(ServerRoom.State.FINISHED, r.phase())
        s.conns.forEachIndexed { i, c -> assertEquals(rtts[i].toLong(), r.rtt.rtt(c), "RTT mesuré par le serveur pour $c") }

        // 1) tous les clients qui ont reçu l'annonce voient le MÊME opensAt = heure serveur de l'annonce + 1,5 s (sauf la première question)
        assertEquals(5, s.seenOpens.size)
        for ((idx, byConn) in s.seenOpens) {
            assertEquals(8, byConn.size, "question $idx : annonce reçue par les 8 clients")
            assertEquals(1, byConn.values.toSet().size, "question $idx : un seul opensAt pour tous : $byConn")
            val expectedGap = if (idx == 0) 0L else PlayTiming.INTER_QUESTION_GAP_MS
            assertEquals(s.announcedAt[idx]!! + expectedGap, byConn.values.first(), "question $idx : opensAt = annonce + délai")
        }
        // 2) aucune réponse acceptée avant opensAt ; le temps compté part de opensAt
        val log = r.table(0).answerLog()
        assertTrue(log.size >= 8 * 4, "des réponses ont été comptées : ${log.size}")
        for (a in log) assertTrue(a.arrivedAtServerMs >= s.opens[a.questionIndex]!!, "réponse acceptée avant l'ouverture : $a")
        // 3) équité : jamais compté plus rapide que la vérité ; perte bornée (0 si rtt/2 ≤ 400 ms, sinon rtt/2 − 400) dès que le délai a permis de se synchroniser
        for (a in log) {
            val p = s.conns.indexOf(a.conn)
            if (a.questionIndex == 0) {
                // pas de délai avant la première : on voit la question rtt/2 plus tard (c'est justement ce que le délai évite ensuite)
                assertTrue(a.elapsedMs >= thinks[p], "q0 joueur $p : compté ${a.elapsedMs} < vrai ${thinks[p]} (gain par la latence)")
                continue
            }
            val bound = maxOf(0L, rtts[p] / 2L - RttBook.MAX_COMPENSATION_MS)
            assertTrue(a.elapsedMs >= thinks[p], "q${a.questionIndex} joueur $p : compté ${a.elapsedMs} < vrai ${thinks[p]} (gain par la latence)")
            assertTrue(a.elapsedMs <= thinks[p] + bound, "q${a.questionIndex} joueur $p : compté ${a.elapsedMs}, vrai ${thinks[p]}, perte permise $bound")
        }
        // 4) le classement suit la vérité : à temps de réflexion plus court, plus de points sur une même question, quelle que soit la latence
        for (qi in 1 until 5) {
            val byThink = log.filter { it.questionIndex == qi }.sortedBy { thinks[s.conns.indexOf(it.conn)] }
            val counted = byThink.map { it.elapsedMs }
            val trueOrder = byThink.map { thinks[s.conns.indexOf(it.conn)] }
            // seul le joueur à 1 200 ms (perte 200 ms) peut se faire dépasser, et seulement par un écart < 200 ms
            for (i in 1 until counted.size) if (counted[i] < counted[i - 1]) assertTrue(trueOrder[i] - trueOrder[i - 1] < 200, "q$qi : inversion par la latence plus grande que la borne")
        }
    }
}
