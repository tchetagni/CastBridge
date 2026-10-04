package castbridge.core.wallet.millions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MillionsLadderTest {
    private val b = MillionsLadder.B

    @Test fun defaultIsLadderBWithStake500() {
        assertEquals(listOf(50L, 100, 200, 300, 500, 700, 900, 1200, 1600, 2000, 2700, 3600, 5000, 7000, 10000), MillionsLadder.DEFAULT.values)
        assertEquals(500L, MillionsLadder.DEFAULT.stake)
        assertEquals(setOf(5, 8, 10, 13), MillionsLadder.DEFAULT.stops)
        assertNull(MillionsLadder.DEFAULT.validate())
    }

    @Test fun refusesWrongSize() { assertNotNull(MillionsLadder(b.dropLast(1), 500).validate()); assertNotNull(MillionsLadder(b + 10_000L, 500).validate()) }

    @Test fun refusesNonStrictlyIncreasing() {
        val v = b.toMutableList().also { it[3] = it[2] }   // 200, 200
        assertTrue(MillionsLadder(v, 500).validate()!!.contains("croissante"))
    }

    @Test fun refusesDecreasingIncrements() {
        val v = b.toMutableList().also { it[5] = 520 }     // incrément du rang 6 : 20, après 200
        assertTrue(MillionsLadder(v, 500).validate()!!.contains("incréments"))
    }

    @Test fun refusesTopOtherThan10000() {
        val v = b.toMutableList().also { it[14] = 10_001 }
        assertTrue(MillionsLadder(v, 500).validate()!!.contains("10 000"))
        val w = b.toMutableList().also { it[14] = 9_999 }
        assertNotNull(MillionsLadder(w, 500).validate())
    }

    @Test fun refusesStopFiveAboveStake() {
        assertNull(MillionsLadder(b, 500).validate())          // palier 5 = 500 = mise : permis
        assertTrue(MillionsLadder(b, 499).validate()!!.contains("mise"))
    }

    @Test fun refusesNonPositiveStakeAndBadStops() {
        assertNotNull(MillionsLadder(b, 0).validate())
        assertNotNull(MillionsLadder(b, 500, setOf(5, 15)).validate())
        assertNotNull(MillionsLadder(b, 500, emptySet()).validate())
    }

    @Test fun gainAfter() { assertEquals(1200L, MillionsLadder.DEFAULT.gainAfter(8)); assertEquals(10_000L, MillionsLadder.DEFAULT.gainAfter(15)) }
}
