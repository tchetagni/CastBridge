package castbridge.core

import castbridge.core.phone.CopyProgress
import castbridge.core.phone.Handoff
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CopyProgressTest {
    @Test fun waitFormatting() {
        assertEquals("1 s", CopyProgress.wait(10))
        assertEquals("45 s", CopyProgress.wait(45_000))
        assertEquals("2 min 05 s", CopyProgress.wait(125_000))
        assertEquals("1 h 02 min", CopyProgress.wait(3_720_000))
    }

    @Test fun percentAndFullEta() {
        val c = CopyProgress(sent = 250, total = 1000, bytesPerSec = 50, handoffInMs = null)
        assertEquals(25, c.percent)
        assertEquals(15_000L, c.fullInMs)                      // 750 bytes left at 50 B/s
        assertEquals("Copie vers TV : 25 %", c.title("TV"))
        assertEquals(0L, CopyProgress(1000, 1000, 50, 0L).fullInMs)
    }

    @Test fun unknownSpeedSaysSoInsteadOfInventingANumber() {
        val c = CopyProgress(0, 1_000_000, 0, null)
        assertNull(c.fullInMs)
        assertTrue("estimation" in c.detail() && "assez d'avance" in c.detail(), c.detail())
    }

    @Test fun waitUntilHandoffIsZeroOnceReadyAndShrinksAsTheCopyAdvances() {
        val total = 500_000_000L; val dur = 2_400_000L; val speed = 4_000_000L
        assertNull(Handoff.waitMs(0, total, dur, 0, false, 0), "no speed measured yet")
        val early = Handoff.waitMs(0, total, dur, 60_000, false, speed)
        val later = Handoff.waitMs(60_000_000, total, dur, 60_000, false, speed)
        assertNotNull(early); assertNotNull(later)
        assertTrue(later!! <= early!!, "later $later <= early $early")
        assertEquals(0L, Handoff.waitMs(total, total, dur, 60_000, false, speed))
        // MP4 with its index at the end: the whole file is needed, so the wait is the time to copy the rest
        val w = Handoff.waitMs(100_000_000, total, dur, 60_000, true, speed)
        assertEquals((total - 100_000_000) * 1000 / speed, w)
    }

    @Test fun handoffTextIsAFullSentence() {
        val d = CopyProgress(1, 10, 1000, 42_000).detail()
        assertTrue(d.startsWith("La TV prend le relais dans 42 s"), d)
    }
}
