package castbridge.core.quiz.online

import castbridge.core.quiz.*
import castbridge.core.ux.SignalLevel
import kotlin.test.*

class ServerRoomResumeTest {
    private val bank = EmbeddedQuestionSource().bank()
    private fun room(count: Int = 10, seed: Long = 5) = ServerRoom("r1", PlayScope.INTERNET, bank, java.util.Random(seed), createdAt = 0, settings = ServerRoom.Settings(duelCount = count))
    private inline fun <reified T : ServerMsg> List<ServerRoom.Out>.one(): T = map { it.msg }.filterIsInstance<T>().first()
    @Suppress("UNCHECKED_CAST") private fun Map<String, Any?>.m(k: String) = this[k] as Map<String, Any?>

    private class Game(val r: ServerRoom, val hostToken: String, val tokens: List<String>)

    private fun game(players: Int = 2, count: Int = 10, seed: Long = 5): Game {
        val r = room(count, seed)
        val h = r.handle("tv", ClientMsg.Create(null, "DUEL"), 0).one<ServerMsg.Welcome>()
        val toks = (0 until players).map { r.handle("c$it", ClientMsg.Join(r.code, "J$it", null, null, false), 0).one<ServerMsg.Welcome>().token }
        r.handle("tv", ClientMsg.Act(null, "mode", null, "DUEL", 1), 0); r.handle("tv", ClientMsg.Act(null, "start", null, "5", 2), 0)
        return Game(r, h.token, toks)
    }

    /** Fait jouer `c1` seul (il répond juste dès l'ouverture) de `from` à `to`. */
    private fun playOn(r: ServerRoom, from: Long, to: Long): Long {
        var now = from
        while (now < to) {
            now += 500
            val d = r.table(0).room.duel!!
            if (d.phase == QuizDuel.Phase.QUESTION && now >= r.table(0).opensAtServerMs) r.handle("c1", ClientMsg.Act(d.question.id, "answer", d.question.answer, null, 1), now)
            r.tick(now)
        }
        return now
    }

    @Test fun playerLostThirtySecondsCatchesUpByLastSeq() {
        val g = game(); val r = g.r
        val lastSeen = r.seq()
        r.disconnect("c0", 1_000)
        val now = playOn(r, 1_000, 31_000)
        assertTrue(r.seq() > lastSeen + 2)
        val back = r.handle("c0b", ClientMsg.Resume("r1", g.tokens[0], lastSeen), now)
        val w = back.one<ServerMsg.Welcome>()
        assertEquals(PlayRole.PLAYER, w.role)
        val replay = back.one<ServerMsg.Replay>()
        assertTrue(replay.events.isNotEmpty() && replay.events.all { it.seq > lastSeen }, "rattrapage des évènements manqués")
        assertEquals(replay.events.map { it.seq }, replay.events.map { it.seq }.sorted())
        assertTrue(replay.events.any { it.kind == "reveal" || it.kind == "question" })
        val st = back.one<ServerMsg.State>()
        assertFalse(st.full, "dans l'anneau : pas de resynchronisation complète")
        assertEquals(w.playerId, st.view.m("me")["id"], "même siège, même joueur")
        assertTrue(back.indexOfFirst { it.msg is ServerMsg.Replay } < back.indexOfFirst { it.msg is ServerMsg.State }, "d'abord le rattrapage, puis l'état")
    }

    @Test fun resumeDuringTheGapGetsTheAbsoluteOpensAt() {
        val g = game(); val r = g.r
        var now = 0L; var q2: ServerMsg.Question? = null
        r.disconnect("c0", 100)
        while (q2 == null && now < 600_000) {
            now += 50
            val d = r.table(0).room.duel!!
            if (d.phase == QuizDuel.Phase.QUESTION && now >= r.table(0).opensAtServerMs) r.handle("c1", ClientMsg.Act(d.question.id, "answer", d.question.answer, null, 1), now)
            q2 = r.tick(now).map { it.msg }.filterIsInstance<ServerMsg.Question>().firstOrNull { it.index == 1 }
        }
        val back = r.handle("c0b", ClientMsg.Resume("r1", g.tokens[0], 0), now + 300)
        assertEquals(q2!!.opensAtServerMs, back.one<ServerMsg.Question>().opensAtServerMs)
    }

    @Test fun tooOldLastSeqGetsAFullState() {
        val g = game(); val r = g.r
        r.disconnect("c0", 10)
        repeat(60) { r.handle("tv", ClientMsg.Mute("p1", it % 2 == 0), 20L + it) }     // plus de 50 évènements : l'anneau a oublié le début
        val back = r.handle("c0b", ClientMsg.Resume("r1", g.tokens[0], 1), 500)
        assertTrue(back.none { it.msg is ServerMsg.Replay }, "plus rien à rejouer")
        assertTrue(back.one<ServerMsg.State>().full, "state complet")
        assertNull(r.eventsSince(1))
    }

    @Test fun badTokenOrOtherRoomTokenGivesTheSameAnswerWithoutRevealingTheRoom() {
        val a = game(seed = 5); val b = game(seed = 6)
        val unknown = a.r.handle("x", ClientMsg.Resume("r1", "0".repeat(32), 0), 100).one<ServerMsg.Error>()
        val otherRoomToken = a.r.handle("y", ClientMsg.Resume("r1", b.tokens[0], 0), 100).one<ServerMsg.Error>()
        val wrongRoomId = a.r.handle("z", ClientMsg.Resume("autre", a.tokens[0], 0), 100).one<ServerMsg.Error>()
        for (e in listOf(unknown, otherRoomToken, wrongRoomId)) assertEquals(PlayReason.PLAY_BAD_CODE.name, e.reason)
        assertEquals(unknown.message, otherRoomToken.message); assertEquals(unknown.message, wrongRoomId.message)
    }

    @Test fun hostLostSixtySecondsWithRemotePlayersAbandonsTheTable() {
        val g = game(); val r = g.r
        r.disconnect("tv", 10_000)
        r.tick(10_000 + ServerRoom.HOST_LOST_MS - 1)
        assertNull(r.table(0).abandoned); assertEquals(ServerRoom.State.PLAYING, r.phase())
        val out = r.tick(10_000 + ServerRoom.HOST_LOST_MS)
        assertEquals("HOST_LOST", r.table(0).abandoned); assertEquals(ServerRoom.State.FINISHED, r.phase())
        assertEquals("HOST_LOST", out.filter { it.to == "c0" }.map { it.msg }.filterIsInstance<ServerMsg.State>().last().view.m("room")["abandoned"])
        assertTrue(r.eventsSince(0)!!.any { it.kind == "abandoned" })
        val late = r.handle("c1", ClientMsg.Act(r.currentQuestionId(), "answer", 0, null, 1), 80_000)
        assertEquals("CLOSED", late.one<ServerMsg.Ack>().result)
        assertTrue(r.tick(10_000 + ServerRoom.HOST_LOST_MS + ServerRoom.PURGE_MS).any { it.msg is ServerMsg.RoomGone }, "salle purgée à 10 minutes")
    }

    @Test fun hostComingBackInTimeKeepsTheGameAndTheSafetySignTellsTheStory() {
        val g = game(); val r = g.r
        r.disconnect("tv", 10_000)
        val orange = r.tick(20_000).filter { it.to == "c0" }.map { it.msg }.filterIsInstance<ServerMsg.Safety>().last().view
        assertEquals(SignalLevel.ORANGE, orange.level); assertTrue(orange.text.contains("reprise"))
        r.handle("tv2", ClientMsg.Resume("r1", g.hostToken, 0), 50_000)
        r.tick(10_000 + ServerRoom.HOST_LOST_MS + 5_000)
        assertNull(r.table(0).abandoned, "l'hôte est revenu avant 60 s")
        assertEquals(ServerRoom.State.PLAYING, r.phase())
    }

    @Test fun onlyLocalPlayersBehindTheTvPauseTheTableAndResumeKeepsTheClock() {
        val r = room()
        val h = r.handle("tv", ClientMsg.Create(null, "DUEL"), 0).one<ServerMsg.Welcome>()
        r.handle("tv", ClientMsg.Join(r.code, "Voisin", null, null, false), 0)      // joueur local relayé par la TV
        r.handle("tv", ClientMsg.Act(null, "mode", null, "DUEL", 1), 0); r.handle("tv", ClientMsg.Act(null, "start", null, "5", 2), 0)
        val q = r.table(0).room.duel!!.question
        r.disconnect("tv", 5_000)
        r.tick(5_100)
        val remainingBefore = (r.table(0).room.view(null).m("duel")["remainingMs"] as Number).toLong()
        assertNotNull(r.table(0).pausedAt, "tous les joueurs sont derrière la TV : la table est en pause")
        val during = r.tick(40_000)     // 35 s de pause : la salle n'avance pas
        assertEquals(QuizDuel.Phase.QUESTION, r.table(0).duelPhase()); assertTrue(during.none { it.msg is ServerMsg.Reveal })
        val back = r.handle("tv2", ClientMsg.Resume("r1", h.token, 0), 40_000)
        assertNull(r.table(0).pausedAt); assertNull(r.table(0).abandoned)
        val view = back.one<ServerMsg.State>().view
        assertEquals(remainingBefore, (view.m("duel")["remainingMs"] as Number).toLong(), "le temps restant n'a pas bougé pendant la pause")
        assertEquals(q.id, r.table(0).room.duel!!.question.id)
    }

    @Test fun pausedTableLongerThanSixtySecondsIsAbandoned() {
        val r = room()
        r.handle("tv", ClientMsg.Create(null, "DUEL"), 0); r.handle("tv", ClientMsg.Join(r.code, "Voisin", null, null, false), 0)
        r.handle("tv", ClientMsg.Act(null, "mode", null, "DUEL", 1), 0); r.handle("tv", ClientMsg.Act(null, "start", null, "5", 2), 0)
        r.disconnect("tv", 1_000); r.tick(1_100)
        r.tick(1_000 + ServerRoom.HOST_LOST_MS)
        assertEquals("HOST_LOST", r.table(0).abandoned)
    }

    @Test fun autoHostKeepsTheTableGoingWhenTheHostLeaves() {
        val g = game(); val r = g.r
        assertEquals("OK", r.handle("tv", ClientMsg.Act(null, "autohost", null, null, 3), 100).one<ServerMsg.Ack>().result)
        r.disconnect("tv", 200)
        r.tick(200 + 2 * ServerRoom.HOST_LOST_MS)
        assertNull(r.table(0).abandoned); assertEquals(ServerRoom.State.PLAYING, r.phase())
    }
}
