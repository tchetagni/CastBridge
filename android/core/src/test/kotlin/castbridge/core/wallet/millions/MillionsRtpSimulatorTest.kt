package castbridge.core.wallet.millions

import castbridge.core.wallet.millions.MillionsRtpSimulator.Joker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MillionsRtpSimulatorTest {
    /** Probabilités de réussite SUPPOSÉES du § 13.2 (rien n'est mesuré). */
    private val p = listOf(97, 95, 93, 90, 87, 83, 79, 75, 70, 65, 60, 55, 50, 45, 40).map { it / 100.0 }
    private fun rtp(l: List<Long>, j: Joker, stop: Int? = null, probs: List<Double> = p) = MillionsRtpSimulator.rtp(l, 500, probs, j, stop)

    @Test fun ladderBOptimalPlayer() {
        assertEquals(0.79, rtp(MillionsLadder.B, Joker.NONE), 0.01)
        assertEquals(0.88, rtp(MillionsLadder.B, Joker.GUESS), 0.01)
        assertEquals(0.92, rtp(MillionsLadder.B, Joker.HALF), 0.01)
        assertEquals(1.06, rtp(MillionsLadder.B, Joker.SURE), 0.01)
    }

    @Test fun ladderAGenerousIsATap() { assertEquals(1.85, rtp(MillionsLadder.A, Joker.HALF), 0.02) }

    @Test fun ladderCPlatformAdvantage() { assertEquals(0.79, rtp(MillionsLadder.C, Joker.HALF), 0.02) }

    @Test fun imposedStopsOfLadderBWithHalfModel() {
        assertEquals(0.72, rtp(MillionsLadder.B, Joker.HALF, 5), 0.01)
        assertEquals(0.92, rtp(MillionsLadder.B, Joker.HALF, 8), 0.01)
        assertEquals(0.76, rtp(MillionsLadder.B, Joker.HALF, 10), 0.01)
        assertEquals(0.37, rtp(MillionsLadder.B, Joker.HALF, 13), 0.01)
        assertEquals(0.16, rtp(MillionsLadder.B, Joker.HALF, 15), 0.01)
    }

    @Test fun optimalIsAtLeastAnyImposedStop() {
        val best = rtp(MillionsLadder.B, Joker.HALF)
        for (s in listOf(5, 8, 10, 13, 15)) assertTrue(best + 1e-9 >= rtp(MillionsLadder.B, Joker.HALF, s), "arrêt $s")
    }

    @Test fun jokerNeverHurts() {
        val none = rtp(MillionsLadder.B, Joker.NONE)
        for (j in listOf(Joker.GUESS, Joker.HALF, Joker.SURE)) assertTrue(rtp(MillionsLadder.B, j) >= none, j.name)
        assertTrue(rtp(MillionsLadder.B, Joker.SURE) >= rtp(MillionsLadder.B, Joker.HALF))
        assertTrue(rtp(MillionsLadder.B, Joker.HALF) >= rtp(MillionsLadder.B, Joker.GUESS))
    }

    @Test fun boundaryProbabilities() {
        assertEquals(10_000.0 / 500, rtp(MillionsLadder.B, Joker.NONE, probs = List(15) { 1.0 }), 1e-9)
        assertEquals(0.0, rtp(MillionsLadder.B, Joker.HALF, probs = List(15) { 0.0 }).let { if (it < 1e-12) 0.0 else it }, 1e-9, "sans aucune chance : rien")
    }

    @Test fun sensitivityToSuccessRate() {
        val plus5 = p.map { minOf(1.0, it + 0.05) }
        val minus5 = p.map { it - 0.05 }
        assertEquals(1.35, rtp(MillionsLadder.B, Joker.HALF, probs = plus5), 0.03)
        assertEquals(0.60, rtp(MillionsLadder.B, Joker.HALF, probs = minus5), 0.03)
    }
}
