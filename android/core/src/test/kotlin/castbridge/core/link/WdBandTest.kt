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
}
