package castbridge.core

import castbridge.core.quiz.*
import kotlin.random.Random
import kotlin.test.*

class QuizGameTest {
    private val qs = (1..15).map { QuizBankTest.q("q$it", difficulty = 1 + (it - 1) / 3, answer = it % 4) }
    private fun game(timers: IntArray = QuizGame.NO_TIMERS, practice: Boolean = false) = QuizGame(qs, timers = timers, seed = 1, practice = practice).also { it.start(0) }

    private fun QuizGame.answer(right: Boolean, now: Long = 0) {
        val c = if (right) question.answer else (question.answer + 1) % 4
        assertTrue(select(c, now)); assertTrue(confirm(now)); assertTrue(reveal())
    }

    @Test fun ladderAndSafeLevels() {
        val l = Ladder.DEFAULT
        assertEquals(15, l.size); assertEquals(setOf(5, 10), l.safeLevels)
        assertEquals(0, l.safeAmount(4)); assertEquals(l.amount(5), l.safeAmount(9)); assertEquals(l.amount(10), l.safeAmount(14))
        assertEquals(l.amounts.sorted(), l.amounts, "the ladder only goes up")
        assertEquals("1 500 000 FCFA", Ladder.fcfa(1_500_000))
    }

    @Test fun winningAllFifteen() {
        val g = game()
        repeat(15) { g.answer(true); assertTrue(g.next(0)) }
        assertEquals(QuizGame.Phase.FINISHED, g.phase); assertEquals(QuizGame.End.WON, g.end)
        assertEquals(Ladder.DEFAULT.amount(15), g.winnings)
    }

    @Test fun wrongAnswerFallsBackToTheSafeLevel() {
        val g = game()
        repeat(7) { g.answer(true); g.next(0) }
        g.answer(false)
        assertEquals(false, g.lastCorrect)
        g.next(0)
        assertEquals(QuizGame.End.WRONG, g.end); assertEquals(Ladder.DEFAULT.amount(5), g.winnings)
        val g2 = game(); g2.answer(false); g2.next(0)
        assertEquals(0, g2.winnings, "nothing before the first safe level")
    }

    @Test fun walkingAwayKeepsTheLastWin() {
        val g = game()
        repeat(8) { g.answer(true); g.next(0) }
        assertTrue(g.walk())
        assertEquals(QuizGame.End.WALKED, g.end); assertEquals(Ladder.DEFAULT.amount(8), g.winnings)
        assertFalse(g.walk(), "only once")
    }

    @Test fun finalAnswerCanBeWithdrawnBeforeConfirming() {
        val g = game()
        assertTrue(g.select(0, 0)); assertEquals(QuizGame.Phase.CONFIRM, g.phase)
        assertTrue(g.cancel()); assertEquals(QuizGame.Phase.QUESTION, g.phase); assertNull(g.selected)
        assertFalse(g.confirm(0), "nothing to confirm")
        assertFalse(g.reveal(), "no reveal before lock")
    }

    @Test fun fiftyFiftyKeepsTheRightAnswerAndIsUsedOnce() {
        repeat(40) { seed ->
            val g = QuizGame(qs, timers = QuizGame.NO_TIMERS, seed = seed.toLong()).also { it.start(0) }
            assertTrue(g.useFifty())
            assertEquals(2, g.removed.size)
            assertFalse(g.question.answer in g.removed)
            assertFalse(g.select(g.removed.first(), 0), "a removed answer cannot be chosen")
            assertFalse(g.useFifty(), "once per game")
        }
    }

    @Test fun eachJokerOnlyOnce() {
        val g = game()
        assertTrue(g.beginJoker(Joker.AUDIENCE, 0)); assertEquals(QuizGame.Phase.JOKER, g.phase)
        assertFalse(g.select(0, 0), "no answer during the vote")
        assertTrue(g.finishAudience(intArrayOf(1, 5, 0, 2), 0))
        assertEquals(100, g.audience!!.sum()); assertTrue(g.audienceReal)
        assertFalse(g.beginJoker(Joker.AUDIENCE, 0))
        assertTrue(g.beginJoker(Joker.PHONE, 0)); assertTrue(g.finishPhone(2, "Ali", 0))
        assertEquals(2, g.phone!!.choice); assertFalse(g.phone!!.simulated)
        assertFalse(g.beginJoker(Joker.PHONE, 0))
        g.answer(true); g.next(0)
        assertFalse(g.canUse(Joker.AUDIENCE)); assertFalse(g.canUse(Joker.PHONE)); assertTrue(g.canUse(Joker.FIFTY))
    }

    @Test fun simulatedAudienceIsPlausible() {
        val easy = QuizBankTest.q("e", difficulty = 1, answer = 2)
        var rightWins = 0
        repeat(200) { s ->
            val p = QuizGame.simulateAudience(easy, emptySet(), Random(s))
            assertEquals(100, p.sum())
            if (p.indices.maxBy { p[it] } == 2) rightWins++
        }
        assertTrue(rightWins > 190, "the audience is almost always right on easy questions ($rightWins/200)")
        val p = QuizGame.simulateAudience(easy, setOf(0, 1), Random(3))
        assertEquals(0, p[0] + p[1], "no vote for answers removed by 50:50")
        assertEquals(intArrayOf(34, 33, 33, 0).toList(), QuizGame.percentages(intArrayOf(1, 1, 1, 0)).toList())
    }

    @Test fun clockRunsOutAndPausesDuringJokers() {
        val g = QuizGame(qs, timers = IntArray(15) { 30 }, seed = 1).also { it.start(1_000) }
        assertEquals(30_000, g.remainingMs(1_000))
        assertTrue(g.beginJoker(Joker.PHONE, 11_000))
        assertFalse(g.tick(100_000), "paused during the call")
        g.finishPhone(null, null, 100_000)
        assertEquals(20_000, g.remainingMs(100_000))
        assertFalse(g.tick(119_000)); assertTrue(g.tick(120_000))
        assertEquals(QuizGame.End.TIMEOUT, g.end)
        // once locked, the clock no longer matters
        val h = QuizGame(qs, timers = IntArray(15) { 30 }, seed = 1).also { it.start(0) }
        h.select(h.question.answer, 1); h.confirm(2)
        assertFalse(h.tick(60_000)); assertTrue(h.reveal())
    }

    @Test fun rightAnswerNeverSerializedBeforeReveal() {
        val g = game()
        fun json() = Json.obj(g.toJson(0))
        @Suppress("UNCHECKED_CAST") fun ans() = (json()["question"] as Map<String, Any?>)["answer"]
        assertNull(ans()); g.useFifty(); assertNull(ans())
        g.select(g.question.answer, 0); assertNull(ans()); g.confirm(0); assertNull(ans(), "not even during the suspense")
        g.reveal(); assertEquals(g.question.answer.toLong(), ans())
        g.next(0); assertNull(ans(), "next question closed again")
        assertEquals("QUESTION", json()["phase"])
    }

    @Test fun practiceGoesOnAfterAMistake() {
        val g = game(practice = true)
        g.answer(false); assertTrue(g.next(0)); assertEquals(QuizGame.Phase.QUESTION, g.phase)
        repeat(14) { g.answer(true); g.next(0) }
        assertEquals(QuizGame.End.PRACTICE_DONE, g.end); assertEquals(14, g.correct); assertEquals(0, g.winnings)
    }
}

class QuizDuelTest {
    private val qs = (1..3).map { QuizBankTest.q("d$it", answer = 1) }

    @Test fun pointsDependOnServerSideSpeed() {
        assertEquals(1000, QuizDuel.points(0, 20_000)); assertEquals(750, QuizDuel.points(10_000, 20_000))
        assertEquals(500, QuizDuel.points(20_000, 20_000)); assertEquals(500, QuizDuel.points(99_000, 20_000))
        val d = QuizDuel(qs, questionMs = 20_000).also { it.addPlayer("fast"); it.addPlayer("slow"); it.addPlayer("wrong"); it.start(1_000) }
        assertEquals(QuizDuel.Result.OK, d.answer("fast", "d1", 1, 2_000))       // server times: 1 s after opening
        assertEquals(QuizDuel.Result.OK, d.answer("slow", "d1", 1, 16_000))
        assertEquals(QuizDuel.Result.OK, d.answer("wrong", "d1", 0, 1_500))
        assertTrue(d.tick(16_000, setOf("fast", "slow", "wrong")), "everyone answered: closes early")
        assertEquals(QuizDuel.Phase.REVEAL, d.phase)
        assertEquals(QuizDuel.points(1_000, 20_000), d.score("fast")); assertEquals(QuizDuel.points(15_000, 20_000), d.score("slow")); assertEquals(0, d.score("wrong"))
        assertEquals(listOf("fast", "slow", "wrong"), d.ranking().map { it.first })
        assertEquals(listOf(1, 2, 0, 0), d.distribution().toList())
    }

    @Test fun answersAreFinalAndIdempotent() {
        val d = QuizDuel(qs).also { it.addPlayer("a"); it.start(0) }
        assertEquals(QuizDuel.Result.OK, d.answer("a", "d1", 2, 100))
        assertEquals(QuizDuel.Result.SAME, d.answer("a", "d1", 2, 900), "a retry is harmless")
        assertEquals(QuizDuel.Result.ALREADY_ANSWERED, d.answer("a", "d1", 1, 900))
        assertEquals(QuizDuel.Result.UNKNOWN_QUESTION, d.answer("a", "d2", 1, 900))
        assertEquals(QuizDuel.Result.CLOSED, d.answer("b", "d1", 1, 30_000), "too late")
    }

    @Test fun phasesFollowTheClock() {
        val d = QuizDuel(qs, questionMs = 10_000, revealMs = 3_000, boardMs = 3_000).also { it.addPlayer("a"); it.start(0) }
        assertFalse(d.tick(5_000, setOf("a")))
        assertTrue(d.tick(10_000, setOf("a"))); assertEquals(QuizDuel.Phase.REVEAL, d.phase)
        assertTrue(d.tick(13_000, setOf("a"))); assertEquals(QuizDuel.Phase.BOARD, d.phase)
        assertTrue(d.tick(16_000, setOf("a"))); assertEquals(QuizDuel.Phase.QUESTION, d.phase); assertEquals(1, d.index)
        assertTrue(d.skip(16_500)); assertTrue(d.skip(16_600)); assertTrue(d.skip(16_700)); assertEquals(2, d.index)
        d.skip(17_000); d.skip(17_100)
        assertEquals(QuizDuel.Phase.FINISHED, d.phase, "no standings screen after the last reveal: straight to the podium")
    }
}

class WalletTest {
    @Test fun potSplitByRankingWithTies() {
        assertEquals(mapOf("a" to 300L, "b" to 150L, "c" to 50L), Pot.split(500, mapOf("a" to 900, "b" to 700, "c" to 100)))
        assertEquals(mapOf("a" to 140L, "b" to 60L), Pot.split(200, mapOf("a" to 10, "b" to 5)))
        val tie = Pot.split(400, mapOf("a" to 800, "b" to 800, "c" to 100, "d" to 0))
        assertEquals(tie["a"], tie["b"]); assertEquals(0L, tie["d"], "no points, no tokens"); assertEquals(400L, tie.values.sum())
        assertEquals(301L, Pot.split(301, mapOf("a" to 3, "b" to 2, "c" to 1)).values.sum(), "rounding leftovers are not lost")
        assertEquals(mapOf("a" to 0L, "b" to 0L), Pot.split(200, mapOf("a" to 0, "b" to 0)), "nobody scored: the caller refunds")
    }

    @Test fun virtualWalletStakesPaysAndRefunds() {
        val w = VirtualWallet(initial = 100)
        assertTrue(w.virtual)
        assertTrue(w.stake("g1", "a", 60)); assertFalse(w.stake("g1", "a", 60), "not enough left"); assertEquals(40, w.balance("a"))
        assertTrue(w.stake("g1", "b", 60))
        w.payout("g1", mapOf("a" to 120L)); w.payout("g1", mapOf("a" to 120L))
        assertEquals(160, w.balance("a"), "paid once"); assertEquals(40, w.balance("b"))
        assertTrue(w.stake("g2", "b", 40)); w.refund("g2"); assertEquals(40, w.balance("b"))
    }
}

class QrCodeTest {
    /** Matrices produced by the Python "qrcode" package (same data, version, level and mask): see qr-reference.txt. */
    @Test fun matchesAReferenceEncoder() {
        val text = javaClass.getResourceAsStream("/castbridge/quiz/qr-reference.txt")!!.use { String(it.readBytes(), Charsets.UTF_8) }
        val blocks = text.trim().split("\n\n")
        assertTrue(blocks.size >= 10)
        for (b in blocks) {
            val lines = b.trim().lines()
            val (ecl, ver, mask, hex) = lines[0].split(" ")
            val data = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val qr = QrCode.encode(data, QrCode.Ecl.valueOf(ecl), ver.toInt(), mask.toInt())
            val rows = lines.drop(1)
            assertEquals(rows.size, qr.size, "size v$ver")
            for (y in rows.indices) {
                val mine = (0 until qr.size).joinToString("") { x -> if (qr[x, y]) "#" else "." }
                assertEquals(rows[y], mine, "v$ver-$ecl mask $mask row $y")
            }
        }
    }

    @Test fun picksTheSmallestVersionAndAMask() {
        val url = "http://192.168.100.200:8765/quiz?code=0042"
        val qr = QrCode.encode(url)
        assertEquals(QrCode.Ecl.M, qr.ecl)
        assertEquals(42, url.length)
        assertEquals(3, qr.version, "42 bytes fit version 3 at level M (capacity 42)")
        assertEquals(4, QrCode.encode(url + "7").version, "one more byte needs version 4")
        assertTrue(qr.mask in 0..7)
        assertFailsWith<IllegalArgumentException> { QrCode.encode("x".repeat(400)) }
    }
}
