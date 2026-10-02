package castbridge.core

import castbridge.core.quiz.*
import kotlin.test.*

/** Boosts of the Millionnaire game (seconde chance, joker en plus, changer de question) and the « points de défi » unit. */
class QuizBoostsTest {
    /** Test double: a fixed balance, a price of 10 for everything, a log of the debits. */
    private class Fake(var tokens: Long = 100, val on: Boolean = true) : QuizBoosts {
        val charged = ArrayList<Pair<Boost, String>>()
        override fun available(b: Boost) = on
        override fun cost(b: Boost) = 10L
        override fun balance() = tokens
        override fun charge(b: Boost, gameId: String): Boolean {
            if (tokens < 10) return false
            tokens -= 10; charged += b to gameId; return true
        }
    }

    private val qs = (1..15).map { QuizBankTest.q("q$it", difficulty = 1 + (it - 1) / 3, answer = it % 4) }
    private var spare = 0
    private val swap = { q: Question -> QuizBankTest.q("s${spare++}", difficulty = q.difficulty, answer = 1) }

    private fun game(b: QuizBoosts, timers: IntArray = IntArray(15) { 20 }, practice: Boolean = false, provider: ((Question) -> Question?)? = swap) =
        QuizGame(qs, timers = timers, seed = 1, practice = practice, boosts = b, gameId = "g1", swapProvider = provider).also { it.start(0) }

    private fun QuizGame.answer(right: Boolean, now: Long = 0) {
        val c = if (right) question.answer else (question.answer + 1) % 4
        assertTrue(select(c, now)); assertTrue(confirm(now)); assertTrue(reveal())
    }

    @Test fun secondChanceAcceptedOnceThenRefused() {
        val f = Fake(); val g = game(f)
        g.answer(true); g.next(0)                       // question 2
        val id = g.question.id
        g.answer(false); assertTrue(g.next(100))
        assertEquals(QuizGame.Phase.LOST_OFFER, g.phase)
        assertTrue(g.canBoost(Boost.SECOND_CHANCE)); assertEquals(10_100L, g.remainingMs(100) + 100)
        assertTrue(g.applyBoost(Boost.SECOND_CHANCE, 200))
        assertEquals(QuizGame.Phase.QUESTION, g.phase); assertEquals(id, g.question.id, "same question")
        assertEquals(1, g.index); assertEquals(20_000L, g.remainingMs(200), "clock re-armed")
        assertEquals(90L, f.tokens); assertEquals(listOf(Boost.SECOND_CHANCE to "g1"), f.charged)
        g.answer(false, 200); g.next(300)
        assertEquals(QuizGame.Phase.FINISHED, g.phase, "a second loss ends the game: one second chance per game")
        assertEquals(QuizGame.End.WRONG, g.end); assertFalse(g.applyBoost(Boost.SECOND_CHANCE, 300))
        assertEquals(90L, f.tokens)
    }

    @Test fun secondChanceAfterTimeoutAndLostOfferExpires() {
        val f = Fake(); val g = game(f)
        assertTrue(g.tick(20_000)); assertEquals(QuizGame.Phase.LOST_OFFER, g.phase)
        assertEquals(0L, g.winnings, "winnings of the game as it stands")
        assertFalse(g.tick(29_999)); assertEquals(QuizGame.Phase.LOST_OFFER, g.phase)
        assertTrue(g.tick(30_000)); assertEquals(QuizGame.Phase.FINISHED, g.phase); assertEquals(QuizGame.End.TIMEOUT, g.end)
        assertEquals(100L, f.tokens, "nothing charged when the offer is ignored")
    }

    @Test fun declineEndsTheGame() {
        val g = game(Fake()); g.answer(false); g.next(0)
        assertFalse(g.walk()); assertFalse(g.select(0, 0), "no answering during the offer")
        assertTrue(g.declineBoost()); assertEquals(QuizGame.End.WRONG, g.end); assertFalse(g.declineBoost())
    }

    @Test fun withoutTokensNothingChanges() {
        val g = game(NoBoosts); g.answer(false)
        assertTrue(g.next(0)); assertEquals(QuizGame.Phase.FINISHED, g.phase, "no offer: as before")
        assertFalse(g.applyBoost(Boost.SECOND_CHANCE, 0))
        val h = game(NoBoosts); h.useFifty()
        Boost.values().forEach { assertFalse(h.canBoost(it)) }
        val noToken = game(Fake(on = false)); noToken.answer(false); noToken.next(0)
        assertEquals(QuizGame.Phase.FINISHED, noToken.phase, "provider says unavailable (TV in trial)")
    }

    @Test fun insufficientBalanceLeavesTheGameUntouched() {
        val f = Fake(tokens = 5); val g = game(f); g.answer(false); g.next(0)
        assertEquals(QuizGame.Phase.LOST_OFFER, g.phase)
        assertFalse(g.applyBoost(Boost.SECOND_CHANCE, 10))
        assertEquals("Jetons insuffisants.", g.lastBoostRefusal)
        assertEquals(QuizGame.Phase.LOST_OFFER, g.phase); assertEquals(5L, f.tokens); assertTrue(f.charged.isEmpty())
    }

    @Test fun extraJokerTwiceThenRefusedAndNeverForPhone() {
        val f = Fake(); val g = game(f)
        assertFalse(g.canBoost(Boost.EXTRA_JOKER), "nothing spent yet")
        assertTrue(g.beginJoker(Joker.PHONE, 0)); assertTrue(g.finishPhone(null, null, 0))
        assertFalse(g.canBoost(Boost.EXTRA_JOKER), "the phone joker is never given back")
        assertTrue(g.useFifty()); assertFalse(g.canUse(Joker.FIFTY))
        assertTrue(g.applyBoost(Boost.EXTRA_JOKER, 0)); assertTrue(g.canUse(Joker.FIFTY)); assertTrue(Joker.PHONE in g.jokersUsed)
        assertTrue(g.useFifty())
        assertTrue(g.beginJoker(Joker.AUDIENCE, 0)); assertTrue(g.finishAudience(null, 0))
        assertTrue(g.applyBoost(Boost.EXTRA_JOKER, 0)); assertTrue(g.canUse(Joker.AUDIENCE) || g.canUse(Joker.FIFTY))
        assertFalse(g.canBoost(Boost.EXTRA_JOKER), "at most twice per game")
        assertFalse(g.applyBoost(Boost.EXTRA_JOKER, 0)); assertEquals(80L, f.tokens)
        assertEquals(2, g.boostsUsed[Boost.EXTRA_JOKER])
    }

    @Test fun swapQuestionOnceSameDifficultyAndNotAfterAJoker() {
        val f = Fake(); val g = game(f)
        val old = g.question
        assertTrue(g.applyBoost(Boost.SWAP_QUESTION, 5_000))
        assertNotEquals(old.id, g.question.id); assertEquals(old.difficulty, g.question.difficulty)
        assertEquals(0, g.index); assertEquals(20_000L, g.remainingMs(5_000), "new clock")
        assertEquals(15, g.levels)
        assertFalse(g.canBoost(Boost.SWAP_QUESTION)); assertFalse(g.applyBoost(Boost.SWAP_QUESTION, 5_000)); assertEquals(90L, f.tokens)
        val h = game(Fake()); h.useFifty()
        assertFalse(h.canBoost(Boost.SWAP_QUESTION), "not once a joker was used on this question")
        val none = game(Fake(), provider = { null })
        assertFalse(none.applyBoost(Boost.SWAP_QUESTION, 0)); assertEquals(100L, (none.boosts as Fake).tokens, "no replacement: not charged")
        val noProvider = game(Fake(), provider = null)
        assertFalse(noProvider.canBoost(Boost.SWAP_QUESTION))
    }

    @Test fun noBoostInPractice() {
        val g = game(Fake(), practice = true)
        g.answer(false); g.next(0)
        Boost.values().forEach { assertFalse(g.canBoost(it)) }
        assertEquals(QuizGame.Phase.QUESTION, g.phase, "practice goes on, no lost offer")
    }

    @Test fun boostedFlagAndJsonState() {
        val g = game(Fake())
        assertEquals(false, Json.obj(g.toJson(0))["boosted"])
        g.answer(false); g.next(0)
        @Suppress("UNCHECKED_CAST") val offer = Json.obj(g.toJson(0))
        assertEquals("LOST_OFFER", offer["phase"])
        @Suppress("UNCHECKED_CAST") val b = offer["boosts"] as Map<String, Any?>
        assertEquals(true, (b["available"] as Map<*, *>)["SECOND_CHANCE"]); assertEquals(10, (b["costs"] as Map<*, *>)["SECOND_CHANCE"].let { (it as Number).toInt() })
        assertTrue(g.applyBoost(Boost.SECOND_CHANCE, 0))
        repeat(15) { g.answer(true); g.next(0) }
        assertEquals(QuizGame.End.WON, g.end)
        val won = Json.obj(g.toJson(0))
        assertEquals(true, won["boosted"])
        @Suppress("UNCHECKED_CAST") assertEquals(1, ((won["boosts"] as Map<String, Any?>)["used"] as Map<*, *>)["SECOND_CHANCE"].let { (it as Number).toInt() })
    }

    @Test fun boostedScoreNeverBeatsAnUnboostedRecord() {
        val hs = HighScores(perBoard = 2)
        hs.add(HighScores.Entry("b", "A", 500, "", 1, boosted = true))
        hs.add(HighScores.Entry("b", "B", 100, "", 2))
        assertEquals(listOf("B", "A"), hs.top("b").map { it.name })
        hs.add(HighScores.Entry("b", "C", 50, "", 3))
        assertEquals(listOf("B", "C"), hs.top("b").map { it.name }, "the boosted entry is the one dropped")
        val back = HighScores.fromJson(HighScores(listOf(HighScores.Entry("b", "A", 5, "", 1, true), HighScores.Entry("b", "Z", 4, "", 2))).toJson())
        assertEquals(listOf(false, true), back.top("b").map { it.boosted }.sortedBy { it })
    }

    // ---------------------------------------------------------------- room

    private var now = 1_000L
    private val bank = EmbeddedQuestionSource().bank()
    private fun room(b: QuizBoosts) = QuizRoom(bank, clock = { now }, random = java.util.Random(7), autoTick = false, boosts = b)

    @Test fun aPhoneCannotBoostOnlyTheTvCan() {
        val f = Fake(); val r = room(f)
        val cand = r.join(r.code, "Candidat").player!!
        assertTrue(r.setCandidate(cand.id)); assertNull(r.startGame(seed = 5))
        val g = r.game!!
        assertTrue(g.beginJoker(Joker.AUDIENCE, now)); g.finishAudience(null, now)
        assertEquals(QuizRoom.Act.FORBIDDEN, r.act(cand.token, "boost", g.question.id, null, "EXTRA_JOKER"))
        assertEquals(QuizRoom.Act.FORBIDDEN, r.act(cand.token, "declineBoost", g.question.id, null))
        assertTrue(f.charged.isEmpty())
        assertNull(r.hostBoost(Boost.EXTRA_JOKER)); assertEquals(1, f.charged.size)
        assertEquals("Aucune partie en cours.", room(f).hostBoost(Boost.EXTRA_JOKER))
    }

    @Test fun hostSwapDrawsAnotherQuestionOfTheSameDifficultyAndSecondChanceAfterTimeout() {
        val f = Fake(); val r = room(f); assertNull(r.startGame(seed = 3))
        val g = r.game!!; val old = g.question
        assertNull(r.hostBoost(Boost.SWAP_QUESTION))
        assertNotEquals(old.id, g.question.id); assertEquals(old.difficulty, g.question.difficulty)
        now += 20_000; r.tick()
        assertEquals(QuizGame.Phase.LOST_OFFER, g.phase); assertEquals(QuizRoom.Stage.PLAYING, r.stage)
        assertNull(r.hostBoost(Boost.SECOND_CHANCE)); assertEquals(QuizGame.Phase.QUESTION, g.phase)
        now += 20_000; r.tick(); now += QuizGame.LOST_OFFER_MS; r.tick()
        assertEquals(QuizRoom.Stage.FINISHED, r.stage)
        val r2 = room(Fake(tokens = 0)); assertNull(r2.startGame(seed = 3))
        assertEquals("Jetons insuffisants.", r2.hostBoost(Boost.SWAP_QUESTION))
    }

    @Test fun stakeUnitIsChallengePointsNeverTokens() {
        val w = ChallengePointsWallet()
        assertEquals("points de défi", w.unit)
        assertEquals("Défi en points", QuizRoom.Play.STAKE.label)
        val r = room(NoBoosts); val a = r.join(r.code, "Awa").player!!
        @Suppress("UNCHECKED_CAST") val s = r.view(a.token)["settings"] as Map<String, Any?>
        assertEquals("points de défi", s["unit"])
        @Suppress("DEPRECATION") assertEquals("points de défi", VirtualWallet().unit)
        assertFalse(QuizRoom.TOKENS_LABEL.lowercase().contains("jeton"))
    }
}
