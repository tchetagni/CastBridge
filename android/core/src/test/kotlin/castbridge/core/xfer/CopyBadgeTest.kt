package castbridge.core.xfer

import castbridge.core.xfer.CopyBadge.Tone
import castbridge.core.xfer.TransferProgress.Item
import castbridge.core.xfer.TransferProgress.Phase
import castbridge.core.xfer.TransferProgress.Transport
import kotlin.test.*

/** B (R-16) : la petite icône de copie du lecteur. Pur : aucune vue ici, seulement ce qu'elle doit dessiner. */
class CopyBadgeTest {
    private fun item(id: String = "a", total: Long = 100, got: Long = 42, phase: Phase = Phase.RUNNING, slowed: Boolean = false) =
        Item(id, 1, "film.avi", total, got, Transport.WIFI, null, 0, 0, 0, phase, null, slowed)

    @Test fun noCopyMeansHidden() {
        assertFalse(CopyBadge.of(emptyList()).visible)
        assertFalse(CopyBadge.of(listOf(item(phase = Phase.DONE), item("b", phase = Phase.FAILED), item("c", phase = Phase.ABORTED))).visible)
    }

    @Test fun oneCopyShowsItsPercentAndDescription() {
        val m = CopyBadge.of(listOf(item()))
        assertTrue(m.visible); assertEquals(42, m.percent); assertEquals("42 %", m.label); assertNull(m.countLabel); assertEquals(1, m.count)
        assertEquals(Tone.NORMAL, m.tone); assertEquals("Copie en cours : 42 %", m.description)
    }

    @Test fun severalCopiesAreAggregatedBySumNotAverage() {
        val m = CopyBadge.of(listOf(item("a", total = 100, got = 100), item("b", total = 900, got = 0)))
        assertEquals(10, m.percent); assertEquals("2", m.countLabel); assertEquals(2, m.count)
        assertEquals("2 copies en cours : 10 %", m.description)
    }

    @Test fun endedCopiesAreIgnoredInTheAggregate() {
        val m = CopyBadge.of(listOf(item("a", got = 50), item("b", total = 10, got = 10, phase = Phase.DONE)))
        assertEquals(50, m.percent); assertEquals(1, m.count)
    }

    @Test fun anUnknownSizeMakesItIndeterminate() {
        val m = CopyBadge.of(listOf(item(total = 0, got = 5000)))
        assertTrue(m.visible); assertNull(m.percent); assertEquals("", m.label); assertEquals("Copie en cours", m.description)
        assertNull(CopyBadge.of(listOf(item("a"), item("b", total = 0, got = 7))).percent)
    }

    @Test fun aSlowedCopyTurnsTheBadgeOrangeAndSaysSo() {
        val m = CopyBadge.of(listOf(item("a"), item("b", slowed = true)))
        assertEquals(Tone.SLOWED, m.tone); assertTrue(m.description.contains("Copie ralentie"))
        assertEquals(Tone.NORMAL, CopyBadge.of(listOf(item())).tone)
    }

    @Test fun neverShowsOneHundredBeforeTheLastByteAndKeepsItWhileFinishing() {
        assertEquals(99, CopyBadge.of(listOf(item(total = 1000, got = 999))).percent)
        val full = CopyBadge.of(listOf(item(total = 1000, got = 1000)))
        assertTrue(full.visible); assertEquals(100, full.percent)                    // the final check is still running
        assertFalse(CopyBadge.of(listOf(item(total = 1000, got = 1000, phase = Phase.DONE))).visible)
    }

    @Test fun theProgressOverlayHidesTheBadgeSoNothingOverlapsTheSeekBar() {
        assertFalse(CopyBadge.of(listOf(item()), overlayVisible = true).visible)
        assertTrue(CopyBadge.of(listOf(item()), overlayVisible = false).visible)
    }
}
