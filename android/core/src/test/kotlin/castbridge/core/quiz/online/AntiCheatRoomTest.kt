package castbridge.core.quiz.online

import castbridge.core.quiz.*
import kotlin.test.*

/** Anti-triche dans la salle (w20-07) : un robot parfait rend la partie NON CLASSÉE (action douce), rotation du code seulement sur frappe proche, retryAfterMs additif. */
class AntiCheatRoomTest {
    private val bank = EmbeddedQuestionSource().bank()
    private fun room(scope: PlayScope = PlayScope.INTERNET, count: Int = 10) = ServerRoom("r1", scope, bank, java.util.Random(7), createdAt = 0, settings = ServerRoom.Settings(duelCount = count))

    /** Joue un Duel complet : « c0 » répond comme `answerer`, « c1 » comme un humain lent et inexact. */
    private fun play(r: ServerRoom, robot: Boolean) {
        r.handle("tv", ClientMsg.Create(null, "DUEL"), 0)
        r.handle("c0", ClientMsg.Join(r.code, "Robot", null, dv(), false), 0, "203.0.113.5")
        humanToken = r.handle("c1", ClientMsg.Join(r.code, "Humain", null, dv(), false), 0, "203.0.113.6").map { it.msg }.filterIsInstance<ServerMsg.Welcome>().single().token
        r.handle("tv", ClientMsg.Act(null, "mode", null, "DUEL", 1), 0)
        var now = 1_000L
        r.handle("tv", ClientMsg.Act(null, "start", null, "3", 2), now)
        var guard = 0
        val done = HashSet<Pair<String, String>>()
        while (r.phase() != ServerRoom.State.FINISHED && guard++ < 40_000) {
            now += 50; r.tick(now)
            val q = r.currentQuestionId()
            val d = r.table(0).room.duel
            if (q != null && d != null && r.table(0).duelPhase() == QuizDuel.Phase.QUESTION) {
                val opens = r.table(0).opensAtServerMs
                val right = d.question.answer
                if (robot && now >= opens + 100 && done.add("c0" to q)) r.handle("c0", ClientMsg.Act(q, "answer", right, null, 9), now)
                if (now >= opens + 2_000 + (d.index * 137L) % 1_500 && done.add("c1" to q)) r.handle("c1", ClientMsg.Act(q, "answer", if (d.index % 3 == 0) right else (right + 1) % 4, null, 9), now)
            }
        }
        assertEquals(ServerRoom.State.FINISHED, r.phase())
    }

    private var humanToken = ""

    /** La vue `room` que reçoit le joueur « Humain » en se reconnectant (le jeton vient de son `welcome`). */
    @Suppress("UNCHECKED_CAST")
    private fun roomView(r: ServerRoom): Map<String, Any?> =
        r.handle("c1b", ClientMsg.Join(r.code, "Humain", humanToken, null, false), 1_000_000).filter { it.to == "c1b" }.map { it.msg }.filterIsInstance<ServerMsg.State>().last().view["room"] as Map<String, Any?>

    @Test fun aPerfectRobotLeavesOnlyItsOwnSeatOutOfTheRankingWithANeutralNote() {
        val r = room(); play(r, robot = true)
        val res = r.botResults()
        assertEquals(2, res.size)
        val flagged = res.filter { it.value.flagged }
        assertEquals(1, flagged.size, "seul le robot est marqué : $res")
        assertTrue(flagged.values.single().score >= 70)
        assertEquals(1, r.unrankedSeats())
        assertTrue(r.isRanked(res.keys.first { !res.getValue(it).flagged }) && !r.isRanked(flagged.keys.single()))
        val note = roomView(r)
        assertEquals(true, note["ranked"], "l'humain reste classé")
    }

    @Test fun anHonestGameStaysRanked() {
        val r = room(); play(r, robot = false)
        assertEquals(0, r.unrankedSeats(), r.botResults().toString())
        val note = roomView(r)
        assertEquals(true, note["ranked"]); assertNull(note["rankNote"])
    }

    @Test fun outsideInternetNobodyIsEverOutOfTheRanking() {
        val r = room(PlayScope.LAN); play(r, robot = true)
        assertEquals(0, r.unrankedSeats())
    }

    @Test fun aRematchDoesNotInheritTheAnswersOfThePreviousGame() {
        val r = room(); play(r, robot = true)
        assertEquals(1, r.unrankedSeats())
        r.handle("tv", ClientMsg.Act(null, "lobby", null, null, 3), 2_000_000)
        r.handle("tv", ClientMsg.Act(null, "start", null, "3", 4), 2_000_100)
        assertTrue(r.table(0).answerLog().isEmpty(), "le journal des réponses est vidé")
        assertEquals(0, r.unrankedSeats(), "et les signalements avec lui")
    }

    @Test fun nearMissRotatesTheCodeAtFiftyOnlyInTheLobbyAndAtMostOncePerMinute() {
        val r = room(); r.handle("tv", ClientMsg.Create(null, "DUEL"), 0)
        val first = r.code
        repeat(49) { assertFalse(r.noteNearMiss(1_000L + it)) }
        assertEquals(first, r.code)
        assertTrue(r.noteNearMiss(2_000)); assertNotEquals(first, r.code)
        val second = r.code
        repeat(49) { assertFalse(r.noteNearMiss(3_000L + it)) }
        assertFalse(r.noteNearMiss(4_000), "deuxième rotation trop tôt (moins d'une minute) : refusée")
        assertEquals(second, r.code)
        repeat(49) { r.noteNearMiss(70_000L + it) }
        assertTrue(r.noteNearMiss(80_000), "une minute plus tard, elle peut tourner"); assertNotEquals(second, r.code)
    }

    @Test fun noRotationOnceTheGameStarted() {
        val r = room(); r.handle("tv", ClientMsg.Create(null, "DUEL"), 0)
        r.handle("c0", ClientMsg.Join(r.code, "Awa", null, dv(), false), 0)
        r.handle("tv", ClientMsg.Act(null, "start", null, "3", 2), 1_000)
        assertEquals(ServerRoom.State.PLAYING, r.phase())
        val c = r.code
        repeat(200) { assertFalse(r.noteNearMiss(10_000L + it * 1_000)) }
        assertEquals(c, r.code)
    }

    @Test fun retryAfterMsIsStructuredAndAdditive() {
        assertEquals(15_000L, PlayReason.PLAY_BUSY.retryAfterMs)
        val busy = PlayCodec.encode(ServerMsg.Error(0, PlayReason.PLAY_BUSY.code, PlayReason.PLAY_BUSY.message, true, PlayReason.PLAY_BUSY.retryAfterMs))
        assertTrue("\"retryAfterMs\":15000" in busy, busy)
        val back = PlayCodec.decodeServer(busy) as ServerMsg.Error
        assertEquals(15_000L, back.retryAfterMs)
        val plain = PlayCodec.encode(ServerMsg.Error(0, "BAD_REQUEST", "x", false))
        assertFalse("retryAfterMs" in plain, "champ absent quand non précisé : les anciens clients et les golden ne changent pas")
        assertEquals(0L, (PlayCodec.decodeServer(plain) as ServerMsg.Error).retryAfterMs)
        assertEquals(0L, (PlayCodec.decodeServer("""{"t":"error","seq":1,"reason":"X","message":"m","retryable":true,"retryAfterMs":-5}""") as ServerMsg.Error).retryAfterMs)
        assertEquals(3_600_000L, (PlayCodec.decodeServer("""{"t":"error","seq":1,"reason":"X","message":"m","retryable":true,"retryAfterMs":99999999999}""") as ServerMsg.Error).retryAfterMs, "borné à une heure")
    }

    @Test fun badNameAndBusyAreStableReasons() {
        assertEquals("BAD_NAME", PlayReason.BAD_NAME.code); assertTrue(PlayReason.BAD_NAME.retryable)
        assertEquals(PlayReason.PLAY_BUSY, PlayReason.of("PLAY_BUSY")); assertEquals(PlayReason.BAD_NAME, PlayReason.of("BAD_NAME"))
    }
}
