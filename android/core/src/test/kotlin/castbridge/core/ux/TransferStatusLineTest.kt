package castbridge.core.ux

import castbridge.core.xfer.PlaybackAwareCopyPolicy
import kotlin.test.*

/** R-18 : l'écran de diffusion et la notification disent la MÊME chose, avec les mêmes mots et les mêmes couleurs. */
class TransferStatusLineTest {
    private fun f(percent: Int? = null, route: CopyRouteKind? = null, copying: Boolean = false, slowed: Boolean = false,
                  waiting: Boolean = false, failure: String? = null, playable: Boolean = false) =
        TransferFacts(percent, route, copying, slowed, waiting, failure, playable)

    @Test fun copyingShowsPercentAndTheRouteActuallyUsed() {
        assertEquals("Copie en cours · 65 % · par le Wi-Fi", TransferStatusLine.of(f(65, CopyRouteKind.WIFI, copying = true)).text)
        assertEquals("Copie en cours · 65 % · par le Wi-Fi Direct", TransferStatusLine.of(f(65, CopyRouteKind.WIFI_DIRECT, copying = true)).text)
        assertEquals("Copie en cours · 3 % · par le Bluetooth", TransferStatusLine.of(f(3, CopyRouteKind.BLUETOOTH, copying = true)).text)
        assertEquals(SignalLevel.GREEN, TransferStatusLine.of(f(65, CopyRouteKind.WIFI, copying = true)).level)
    }

    @Test fun copyingWithoutKnownRouteOrPercentIsStillTrue() {
        assertEquals("Copie en cours", TransferStatusLine.of(f(copying = true)).text)
        assertEquals("Copie en cours · 10 %", TransferStatusLine.of(f(10, copying = true)).text)
    }

    @Test fun copyingBeatsWaitingNoStaleWaitingTextOnAProgressingCopy() {
        val l = TransferStatusLine.of(f(65, CopyRouteKind.WIFI, copying = true, waiting = true))
        assertFalse("En attente" in l.text, l.text)
        assertEquals(SignalLevel.GREEN, l.level)
    }

    @Test fun slowedIsKeptNextToCopyingAndOrange() {
        val l = TransferStatusLine.of(f(40, CopyRouteKind.WIFI, copying = true, slowed = true))
        assertEquals("Copie en cours · 40 % · par le Wi-Fi · ${PlaybackAwareCopyPolicy.SLOWED_TEXT}", l.text)
        assertEquals(SignalLevel.ORANGE, l.level)
        assertEquals(PlaybackAwareCopyPolicy.SLOWED_TEXT, TransferStatusLine.of(f(slowed = true)).text)
    }

    @Test fun waitingIsOrangeAndSaysItResumesByItself() {
        val l = TransferStatusLine.of(f(waiting = true))
        assertEquals("En attente de la TV (reprise automatique)", l.text)
        assertEquals(SignalLevel.ORANGE, l.level)
    }

    @Test fun aRealFailureIsRedAndKeepsItsReason() {
        val l = TransferStatusLine.of(f(failure = "la TV n'a plus de place (507)"))
        assertEquals("la TV n'a plus de place (507)", l.text)
        assertEquals(SignalLevel.RED, l.level)
        assertEquals(SignalLevel.RED, TransferStatusLine.of(f(failure = "x", waiting = true)).level)
    }

    @Test fun playableWhileCopyingSaysSo() {
        val l = TransferStatusLine.of(f(30, CopyRouteKind.WIFI, copying = true, playable = true))
        assertEquals("Lecture possible · Copie en cours · 30 % · par le Wi-Fi", l.text)
        assertEquals(SignalLevel.GREEN, l.level)
    }

    @Test fun nothingKnownIsNeverAFalseAlarm() {
        val l = TransferStatusLine.of(f())
        assertEquals(SignalLevel.BLACK, l.level)
        assertTrue(l.text.isNotBlank())
        assertFalse("introuvable" in l.text)
    }

    @Test fun screenAndNotificationReturnTheSameTextForAllFacts() {
        for (p in listOf(null, 0, 65, 100)) for (r in listOf(null) + CopyRouteKind.values()) for (c in listOf(true, false))
            for (s in listOf(true, false)) for (w in listOf(true, false)) for (fail in listOf(null, "refusé (403)")) for (pl in listOf(true, false)) {
                val facts = f(p, r, c, s, w, fail, pl)
                assertTrue(TransferStatusLine.of(facts).text.isNotBlank(), "$facts")
                assertEquals(TransferStatusLine.of(facts).text, TransferStatusLine.forScreen(facts), "$facts")
                assertEquals(TransferStatusLine.forScreen(facts), TransferStatusLine.forNotification(facts), "$facts")
                assertFalse(c && "introuvable" in TransferStatusLine.forScreen(facts), "a copying transfer is never « introuvable »: $facts")
            }
    }
}
