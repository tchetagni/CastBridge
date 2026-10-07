package castbridge.core.link

import kotlin.test.*

class WdBandTest {
    @Test fun fiveGhzIsAskedFirstFromApi29() {
        assertEquals(WdBand.Band.GHZ5, WdBand.first(29))
        assertEquals(WdBand.Band.GHZ5, WdBand.first(34))
        assertEquals(WdBand.Band.AUTO, WdBand.first(28))
    }

    @Test fun aTvWithoutA5GhzRadioOrARememberedFailureNeverTries5Ghz() {
        assertEquals(WdBand.Band.AUTO, WdBand.first(34, radio5GHz = false))
        assertEquals(WdBand.Band.AUTO, WdBand.first(34, radio5GHz = true, failedBefore = true))
        assertEquals(WdBand.Band.GHZ5, WdBand.first(34, radio5GHz = null), "unknown radio: try, the fallback covers a refusal")
        assertEquals(WdBand.Band.GHZ5, WdBand.first(34, radio5GHz = true))
    }

    @Test fun aRefused5GhzGroupIsRetriedOnTheAutomaticBandOnce() {
        assertEquals(WdBand.Band.AUTO, WdBand.retry(WdBand.Band.GHZ5, 0))
        assertEquals(WdBand.Band.AUTO, WdBand.retry(WdBand.Band.GHZ5, 3))
        assertNull(WdBand.retry(WdBand.Band.AUTO, 0), "the automatic band is the last resort")
    }

    @Test fun busyAndUnsupportedAreNotBandProblems() {
        assertNull(WdBand.retry(WdBand.Band.GHZ5, WdBand.REASON_BUSY))
        assertNull(WdBand.retry(WdBand.Band.GHZ5, WdBand.REASON_UNSUPPORTED))
    }

    @Test fun frequencyIsSaidInFrench() {
        assertEquals("5 GHz (5180 MHz)", WdBand.frequencyLabel(5180))
        assertEquals("2,4 GHz (2437 MHz)", WdBand.frequencyLabel(2437))
        assertNull(WdBand.frequencyLabel(0))
        assertEquals("5 GHz", WdBand.label(WdBand.Band.GHZ5))
    }

    @Test fun theAutomaticGroupForAPhoneKeepsTheAutomaticBand() {
        assertEquals(WdBand.Band.AUTO, WdBand.first(34, radio5GHz = true, forPhone = true))
        assertEquals(WdBand.Band.AUTO, WdBand.first(34, radio5GHz = null, forPhone = true))
        assertEquals(WdBand.Band.GHZ5, WdBand.first(34, radio5GHz = true, forPhone = false), "the owner's group still asks for 5 GHz")
    }

    @Test fun aFiveGhzGroupNobodyJoinedIsRecreatedOnceOnAuto() {
        assertTrue(WdBand.recreateAuto(WdBand.Band.GHZ5, 0, 45_000, false))
        assertFalse(WdBand.recreateAuto(WdBand.Band.GHZ5, 0, 44_999, false), "not before 45 s")
        assertFalse(WdBand.recreateAuto(WdBand.Band.GHZ5, 1, 60_000, false), "a client joined")
        assertFalse(WdBand.recreateAuto(WdBand.Band.GHZ5, null, 60_000, false), "unknown: no guess")
        assertFalse(WdBand.recreateAuto(WdBand.Band.GHZ5, 0, 60_000, true), "only once")
        assertFalse(WdBand.recreateAuto(WdBand.Band.AUTO, 0, 60_000, false))
    }
}
