package castbridge.core.quiz.online

import java.util.Random
import kotlin.test.*

class BotScoreTest {
    private fun trace(seat: String, ip: String? = null, device: String? = null, n: Int = 10, ms: (Int) -> Long, ok: (Int) -> Boolean, choice: (Int) -> Int = { 0 }) =
        BotScore.Trace(seat, ip, device, (0 until n).map { BotScore.Answer(it, ms(it), ok(it), choice = choice(it)) })

    @Test fun perfectRobotIsFlaggedAndTheGameIsUnranked() {
        val robot = BotScore.score(trace("r", ms = { 100L }, ok = { true }))
        assertTrue(robot.score >= 70, "robot parfait : ${robot.score}")
        assertTrue(robot.flagged)
        assertTrue("BOT_FAST" in robot.reasons && "BOT_ACCURATE" in robot.reasons, robot.reasons.toString())
        assertFalse("BOT_UNIFORM" in robot.reasons, "moyenne sous 150 ms : pas de « régularité » (temps ramenés à 0 par la compensation)")
        assertEquals(setOf("r"), BotScore.scoreAll(listOf(trace("r", ms = { 100L }, ok = { true }), trace("h", ms = { 2_500L + it * 100 }, ok = { it % 2 == 0 }))).filterValues { it.flagged }.keys)
    }

    @Test fun jitteryRobotIsStillFlagged() {
        val rnd = Random(7)
        val r = BotScore.score(trace("r", ms = { 80L + rnd.nextInt(70) }, ok = { true }))
        assertTrue(r.score >= 70, "robot avec gigue : ${r.score}")
    }

    @Test fun humanAtTwoSecondsAnd70PercentStaysUnderThirty() {
        repeat(200) { seed ->
            val rnd = Random(seed.toLong())
            val h = BotScore.score(trace("h", ms = { (2_000 + rnd.nextGaussian() * 1_000).toLong().coerceAtLeast(600) }, ok = { rnd.nextDouble() < 0.7 }))
            assertTrue(h.score < 30, "humain (graine $seed) : ${h.score} ${h.reasons}")
            assertFalse(h.flagged)
        }
    }

    @Test fun accuracyAloneIsNotEnough() {
        val rnd = Random(3)
        val r = BotScore.score(trace("f", ms = { 1_200L + rnd.nextInt(2_000) }, ok = { true }))
        assertTrue(r.score < BotScore.THRESHOLD, "exactitude seule (10 questions) : ${r.score}")
    }

    @Test fun tooFewAnswersNeverReachTheThreshold() {
        assertEquals(0, BotScore.score(trace("r", n = 4, ms = { 50L }, ok = { true })).score.let { if (it >= BotScore.THRESHOLD) it else 0 })
        assertTrue(BotScore.score(trace("r", n = 3, ms = { 50L }, ok = { true })).score < BotScore.THRESHOLD)
    }

    @Test fun twinSeatsAnsweringTheSameAtTheSameTimeAreFlaggedTogether() {
        val rnd = Random(11)
        val times = (0 until 10).map { 2_000L + rnd.nextInt(3_000) }
        val a = trace("a", ip = "203.0.113.5", device = "dA", ms = { times[it] }, ok = { it % 3 != 0 }, choice = { it % 4 })
        val b = trace("b", ip = "198.51.100.77", device = "dA", ms = { times[it] + 10 }, ok = { it % 3 != 0 }, choice = { it % 4 })
        val other = trace("c", ip = "198.51.100.1", device = "dC", ms = { 1_500L + rnd.nextInt(3_000) }, ok = { it % 2 == 0 }, choice = { (it + 1) % 4 })
        val all = BotScore.scoreAll(listOf(a, b, other))
        assertTrue("BOT_TWINS" in all.getValue("a").reasons && "BOT_TWINS" in all.getValue("b").reasons)
        assertTrue(all.getValue("c").reasons.isEmpty(), "un autre siège n'est pas touché")
        // deux frères sur la même box qui répondent différemment : pas de jumeaux
        val brother = trace("b2", ip = "203.0.113.5", device = "dB2", ms = { 1_000L + rnd.nextInt(4_000) }, ok = { it % 2 == 0 }, choice = { (it + 2) % 4 })
        assertFalse("BOT_TWINS" in BotScore.scoreAll(listOf(a, brother)).getValue("a").reasons)
        // des réponses identiques sans appareil commun ne sont pas accusées, même sur une adresse commune (école, CGNAT)
        val far = trace("f", ip = "203.0.113.5", device = "dF", ms = { times[it] }, ok = { it % 3 != 0 }, choice = { it % 4 })
        assertFalse("BOT_TWINS" in BotScore.scoreAll(listOf(a, far)).getValue("a").reasons)
    }

    @Test fun softActionsOnlyNeutralTextNoAccusation() {
        assertEquals("Classement non pris en compte.", BotScore.NEUTRAL_TEXT)
        val t = BotScore.NEUTRAL_TEXT.lowercase()
        assertTrue(listOf("triche", "robot", "bot", "banni", "suspect").none { it in t })
        assertTrue(BotScore.score(trace("r", ms = { 50L }, ok = { true })).score <= 100, "borné à 100")
    }
}
