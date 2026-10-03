package castbridge.core.quiz.online

import castbridge.core.quiz.*
import kotlin.test.*

class ServerRoomTest {
    private val bank = EmbeddedQuestionSource().bank()
    private fun room(scope: PlayScope = PlayScope.INTERNET, count: Int = 10, settings: ServerRoom.Settings = ServerRoom.Settings(duelCount = count)) =
        ServerRoom("r1", scope, bank, java.util.Random(11), createdAt = 0, settings = settings)

    private fun List<ServerRoom.Out>.msgs() = map { it.msg }
    private inline fun <reified T : ServerMsg> List<ServerRoom.Out>.one(): T = msgs().filterIsInstance<T>().first()
    private fun List<ServerRoom.Out>.error(): String? = msgs().filterIsInstance<ServerMsg.Error>().firstOrNull()?.reason
    private fun List<ServerRoom.Out>.ack(): String = msgs().filterIsInstance<ServerMsg.Ack>().single().result
    private fun ServerRoom.host() = handle("tv", ClientMsg.Create(null, "DUEL"), 0).one<ServerMsg.Welcome>()
    private fun ServerRoom.join(conn: String, name: String, now: Long = 0, spectate: Boolean = false, device: String? = null, ip: String? = null) =
        handle(conn, ClientMsg.Join(code, name, null, device, spectate), now, ip)
    private fun ServerRoom.act(conn: String, action: String, qid: String? = null, choice: Int? = null, arg: String? = null, now: Long = 0, seq: Long = 1) =
        handle(conn, ClientMsg.Act(qid, action, choice, arg, seq), now)

    @Suppress("UNCHECKED_CAST") private fun Map<String, Any?>.m(k: String) = this[k] as Map<String, Any?>
    private fun view(o: List<ServerRoom.Out>, to: String) = o.filter { it.to == to }.msgs().filterIsInstance<ServerMsg.State>().last().view

    @Test fun createJoinByCodeAndWelcome() {
        val r = room()
        val h = r.host()
        assertEquals(PlayRole.HOST, h.role); assertEquals(r.code, h.code); assertEquals(PlayProtocol.PROTO, h.proto)
        val w = r.join("a", "Awa").one<ServerMsg.Welcome>()
        assertEquals(PlayRole.PLAYER, w.role); assertNotNull(w.playerId); assertEquals(32, w.token.length)
        assertEquals(PlayReason.PLAY_BAD_CODE.name, r.handle("b", ClientMsg.Join("ZZZZZZZZ", "Bello", null, null, false), 0).error())
        assertEquals("PLAYER", r.handle("c", ClientMsg.Join(RoomCode.display(r.code).lowercase(), "Carine", null, null, false), 0).one<ServerMsg.Welcome>().role.name, "code tapé en minuscules avec tiret")
        assertEquals(2, r.seatCount())
    }

    @Test fun eighthPlayerFitsNinthIsRefusedThenSpectator() {
        val r = room(); r.host()
        repeat(8) { assertEquals(PlayRole.PLAYER, r.join("c$it", "J$it").one<ServerMsg.Welcome>().role, "joueur ${it + 1}") }
        assertEquals(PlayReason.PLAY_ROOM_FULL.name, r.join("c8", "J8").error(), "la table est complète : PLAY_ROOM_FULL")
        val s = r.join("c9", "Voyeur", spectate = true).one<ServerMsg.Welcome>()
        assertEquals(PlayRole.SPECTATOR, s.role)
        assertNull(view(r.join("c10", "Voyeur2", spectate = true), "c10")["me"], "un spectateur n'a pas de siège")
        assertEquals(8, r.seatCount())
    }

    @Test fun spectatorsCanBeForbiddenOrCapped() {
        val none = room(settings = ServerRoom.Settings(allowSpectators = false)); none.host()
        assertEquals(PlayReason.PLAY_ROOM_FULL.name, none.join("s", "Voyeur", spectate = true).error())
        val capped = room(settings = ServerRoom.Settings(maxSpectators = 2)); capped.host()
        repeat(2) { assertEquals(PlayRole.SPECTATOR, capped.join("s$it", "V$it", spectate = true).one<ServerMsg.Welcome>().role) }
        assertEquals(PlayReason.PLAY_ROOM_FULL.name, capped.join("s3", "V3", spectate = true).error())
    }

    @Test fun twoSimultaneousJoinsForTheLastSeatOnlyOneWins() {
        repeat(20) { round ->
            val r = room(); r.host()
            repeat(7) { r.join("c$it", "J$it") }
            val results = java.util.concurrent.ConcurrentLinkedQueue<String>()
            val start = java.util.concurrent.CountDownLatch(1)
            val ts = listOf("x", "y").map { c -> Thread { start.await(); results += r.join(c, "Z$c").let { o -> o.error() ?: o.one<ServerMsg.Welcome>().role.name } } }
            ts.forEach { it.start() }; start.countDown(); ts.forEach { it.join() }
            assertEquals(1, results.count { it == "PLAYER" }, "tour $round : $results"); assertEquals(1, results.count { it == PlayReason.PLAY_ROOM_FULL.name }, "tour $round : $results")
            assertEquals(8, r.seatCount())
        }
    }

    @Test fun deviceHashHoldsOneSeatTheSecondBecomesSpectator() {
        val r = room(); r.host()
        assertEquals(PlayRole.PLAYER, r.join("a", "Awa", device = "device-abcdef01").one<ServerMsg.Welcome>().role)
        assertEquals(PlayRole.SPECTATOR, r.join("b", "Awa bis", device = "device-abcdef01").one<ServerMsg.Welcome>().role)
    }

    /** Joue un Duel complet (10 questions, graine fixe) ; 3 joueurs : Awa toujours juste, Bello une fois sur deux, Carine jamais. */
    private fun playDuel(scope: PlayScope): Pair<ServerRoom, List<ServerRoom.Out>> {
        val r = room(scope); r.host()
        listOf("Awa", "Bello", "Carine").forEachIndexed { i, n -> r.join("c$i", n) }
        assertEquals("OK", r.act("tv", "mode", arg = "DUEL").ack()); assertEquals(ServerRoom.State.OPEN, r.phase())
        assertEquals("OK", r.act("tv", "start", arg = "5").ack()); assertEquals(ServerRoom.State.PLAYING, r.phase())
        var now = 0L; var last: List<ServerRoom.Out> = emptyList(); var guard = 0
        while (r.phase() == ServerRoom.State.PLAYING && guard++ < 10_000) {
            val d = r.table(0).room.duel!!
            if (d.phase == QuizDuel.Phase.QUESTION && now >= r.table(0).opensAtServerMs) {
                val q = d.question
                now += 1_000; r.act("c0", "answer", q.id, q.answer, now = now)
                now += 1_000; r.act("c1", "answer", q.id, if (d.index % 2 == 0) q.answer else (q.answer + 1) % 4, now = now)
                now += 1_000; last = r.act("c2", "answer", q.id, (q.answer + 1) % 4, now = now)
            }
            now += 500
            last = r.tick(now).ifEmpty { last }
            if (d.phase == QuizDuel.Phase.BOARD || d.phase == QuizDuel.Phase.REVEAL) last = r.act("tv", "skip", now = now)
        }
        return r to last
    }

    @Test fun duelOfTenQuestionsWithSeedEndsWithTheRightRanking() {
        for (scope in listOf(PlayScope.INTERNET, PlayScope.LAN)) {
            val (r, last) = playDuel(scope)
            assertEquals(ServerRoom.State.FINISHED, r.phase(), "$scope")
            val d = r.table(0).room.duel!!
            assertEquals(QuizDuel.Phase.FINISHED, d.phase); assertEquals(10, d.questions.size)
            val ranking = d.ranking()
            assertEquals(3, ranking.size); assertTrue(ranking[0].second > ranking[1].second && ranking[1].second > ranking[2].second, "$ranking")
            assertEquals(0, ranking[2].second, "Carine ne marque jamais")
            val finalView = last.filter { it.msg is ServerMsg.State }.map { it.msg as ServerMsg.State }.last().view
            assertEquals("FINISHED", finalView.m("room")["state"])
            val names = (finalView.m("duel")["ranking"] as List<*>).map { (it as Map<*, *>)["name"] }
            assertEquals(listOf("Awa", "Bello", "Carine"), names)
        }
    }

    @Test fun repeatedActIsOneEffectClosedAndFutureQuestionsAreRefused() {
        val r = room(); r.host(); r.join("c0", "Awa"); r.join("c1", "Bello"); r.act("tv", "mode", arg = "DUEL"); r.act("tv", "start", arg = "5")
        val d = r.table(0).room.duel!!
        val q0 = d.question
        assertEquals("OK", r.act("c0", "answer", q0.id, 1, now = 1_000).ack())
        assertEquals("SAME", r.act("c0", "answer", q0.id, 1, now = 1_100).ack()); assertEquals("SAME", r.act("c0", "answer", q0.id, 1, now = 1_200).ack())
        assertEquals(1, r.table(0).answeredCount(), "rejoué ×3 ⇒ un seul effet")
        assertEquals("FORBIDDEN", r.act("c0", "answer", q0.id, 2, now = 1_300).ack(), "une seule réponse définitive")
        val future = d.questions[1].id
        assertEquals("UNKNOWN_QUESTION", r.act("c1", "answer", future, 0, now = 1_400).ack(), "question future : jamais acceptée")
        assertEquals("UNKNOWN_QUESTION", r.act("c1", "answer", "q-inconnue", 0, now = 1_400).ack())
        assertEquals(1, r.table(0).answeredCount())
        r.act("c1", "answer", q0.id, 0, now = 2_000)         // tout le monde a répondu ⇒ clôture
        assertEquals(QuizDuel.Phase.REVEAL, d.phase)
        assertEquals("CLOSED", r.act("c1", "answer", q0.id, 0, now = 2_100).ack(), "question fermée")
        var now = 2_100L; while (r.table(0).room.duel!!.index == 0 && now < 60_000) { now += 50; r.tick(now) }
        assertEquals("CLOSED", r.act("c1", "answer", q0.id, 0, now = now).ack(), "question passée : fermée")
    }

    @Test fun rttCompensationAppliedBeforeTheCore() {
        fun elapsedFor(rtt: Long): Long {
            val r = room(); r.host(); r.join("c0", "Awa"); r.join("c1", "Bello"); r.act("tv", "mode", arg = "DUEL"); r.act("tv", "start", arg = "5")
            r.rtt.sample("c0", rtt)
            val q = r.table(0).room.duel!!.question
            assertEquals("OK", r.act("c0", "answer", q.id, q.answer, now = 10_000).ack())
            return r.table(0).lastElapsedMs("c0")!!
        }
        assertEquals(10_000, elapsedFor(0)); assertEquals(9_700, elapsedFor(600)); assertEquals(9_600, elapsedFor(2_000))
    }

    @Test fun tvRelayUsesMaxOfLocalAndServerMinusTvRtt() {
        fun run(local: Long, sender: String = "tv"): Pair<String, Long?> {
            val r = room(); r.host()
            val j = r.handle("tv", ClientMsg.Join(r.code, "Voisin", null, null, false), 0).one<ServerMsg.Welcome>()
            r.join("c1", "Bello"); r.act("tv", "mode", arg = "DUEL"); r.act("tv", "start", arg = "5")
            r.rtt.sample("tv", 300)
            val q = r.table(0).room.duel!!.question
            val ack = r.handle(sender, ClientMsg.RelayAct(j.token, q.id, q.answer, local, 5), 10_000).ack()
            return ack to r.table(0).lastElapsedMs("tv")
        }
        assertEquals("OK" to 9_900L, run(9_900), "max(9 900, 9 700)")
        assertEquals("OK" to 9_700L, run(9_000), "la TV ne peut pas raccourcir")
        assertEquals("FORBIDDEN", run(9_900, "c1").first, "un joueur ordinaire ne relaie pas")
    }

    @Test fun nonHostCannotStartAndSpectatorCannotAct() {
        val r = room(); r.host(); r.join("c0", "Awa"); r.join("s", "V", spectate = true)
        assertEquals("FORBIDDEN", r.act("c0", "start").ack()); assertEquals("FORBIDDEN", r.act("s", "answer", "q", 0).ack())
        assertEquals("UNKNOWN_PLAYER", r.act("nobody", "answer", "q", 0).ack())
    }

    @Test fun closeInternetRefusedDuringQuestionAllowedInLobbyThenSpectatorsThenGone() {
        val r = room(); r.host(); r.join("c0", "Awa"); r.act("tv", "mode", arg = "DUEL"); r.act("tv", "start", arg = "5")
        val refused = r.handle("tv", ClientMsg.Scope(false), 1_000)
        assertEquals(PlayProtocol.FORBIDDEN, refused.error()); assertTrue(refused.msgs().filterIsInstance<ServerMsg.Error>().first().message.contains("salle d'attente"))
        val r2 = room(); r2.host(); r2.join("c0", "Awa")
        val closing = r2.handle("tv", ClientMsg.Scope(false), 5_000)
        assertEquals(PlayScope.INTERNET, r2.scope)
        val v = view(closing, "c0")
        assertEquals("SPECTATOR", v.m("room")["role"], "les joueurs distants deviennent spectateurs")
        assertEquals(PlayReason.PLAY_SCOPE_FORBIDDEN.name, r2.join("late", "Tard", now = 6_000).error())
        assertTrue(r2.tick(5_000 + ServerRoom.SPECTATOR_GRACE_MS - 1).none { it.msg is ServerMsg.RoomGone })
        val gone = r2.tick(5_000 + ServerRoom.SPECTATOR_GRACE_MS)
        assertEquals("HOST_CLOSED_INTERNET", gone.first { it.to == "c0" }.msg.let { (it as ServerMsg.RoomGone).reason })
        assertEquals(ServerRoom.State.GONE, r2.phase())
    }

    @Test fun reopenInternetCancelsTheClosing() {
        val r = room(); r.host(); r.join("c0", "Awa")
        r.handle("tv", ClientMsg.Scope(false), 1_000); r.handle("tv", ClientMsg.Scope(true), 2_000)
        assertTrue(r.tick(1_000 + ServerRoom.SPECTATOR_GRACE_MS + 1).none { it.msg is ServerMsg.RoomGone })
    }

    @Test fun kickBansTheDevice() {
        val r = room(); r.host()
        val w = r.join("c0", "Awa", device = "device-abcdef01").one<ServerMsg.Welcome>()
        val out = r.handle("tv", ClientMsg.Kick(w.playerId!!), 0)
        assertEquals(PlayReason.PLAY_BANNED.name, out.filter { it.to == "c0" }.error())
        assertEquals(PlayReason.PLAY_BANNED.name, r.join("c0b", "Awa", device = "device-abcdef01").error())
        assertEquals(PlayProtocol.FORBIDDEN, r.handle("c1", ClientMsg.Kick("p1"), 0).error())
    }

    @Test fun safetyAndTimingKeysAreAdditiveInTheView() {
        val r = room(); r.host()
        val outs = r.join("c0", "Awa")
        assertTrue(outs.any { it.to == "c0" && it.msg is ServerMsg.Safety })
        val v = view(outs, "c0")
        assertEquals(1_500L, v.m("timing")["gapMs"]); assertEquals("INTERNET", v.m("room")["scope"])
        val lan = room(PlayScope.LAN); lan.host()
        assertEquals(0L, view(lan.join("c0", "Awa"), "c0").m("timing")["gapMs"])
    }

    @Test fun lanAndTvOnlyRoomsNeverCompensateOrHold() {
        for (scope in listOf(PlayScope.LAN, PlayScope.TV_ONLY)) {
            val r = room(scope); r.host(); r.join("c0", "Awa"); r.act("tv", "mode", arg = "DUEL"); r.act("tv", "start", arg = "5")
            r.rtt.sample("c0", 600)
            val q = r.table(0).room.duel!!.question
            assertEquals("OK", r.act("c0", "answer", q.id, q.answer, now = 10_000).ack())
            assertEquals(10_000, r.table(0).lastElapsedMs("c0"), "$scope : temps brut, sans compensation")
        }
    }
}
