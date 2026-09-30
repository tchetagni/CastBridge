package castbridge.core

import castbridge.core.parental.ActivityLog
import castbridge.core.parental.Restrictions
import kotlin.test.*
import java.io.File

class ParentalTest {
    @Test fun logKeepsEntriesInOrderAndDropsOldest() {
        val dir = kotlin.io.path.createTempDirectory("parental").toFile()
        val log = ActivityLog(File(dir, "activity.log"), maxEntries = 3)
        log.record("play", "film.mp4", 1000)
        log.record("app", "Netflix", 2000)
        log.record("stop", "film.mp4", 3000)
        log.record("app", "YouTube", 4000)
        val l = log.list()
        assertEquals(3, l.size)
        assertEquals("app", l[0].type)
        assertEquals("stop", l[1].type)
        assertEquals("app", l[2].type)
        assertEquals("YouTube", l[2].label)
        dir.deleteRecursively()
    }

    @Test fun logSurvivesReloadAndClears() {
        val dir = kotlin.io.path.createTempDirectory("parental").toFile()
        val f = File(dir, "activity.log")
        val a = ActivityLog(f, maxEntries = 10)
        a.record("play", "x", 1)
        a.record("file", "y", 2)
        val b = ActivityLog(f, maxEntries = 10)
        assertEquals(listOf("play", "file"), b.list().map { it.type })
        b.clear()
        assertTrue(b.list().isEmpty())
        dir.deleteRecursively()
    }

    @Test fun labelsAreTruncated() {
        val dir = kotlin.io.path.createTempDirectory("parental").toFile()
        val log = ActivityLog(File(dir, "a.log"))
        log.record("play", "x".repeat(500), 1)
        assertTrue(log.list().single().label.length <= 200)
        dir.deleteRecursively()
    }

    @Test fun nightWindowBlocksPlayback() {
        // 22:00 -> 06:00
        val r = Restrictions(enabled = true, blockedAfterMin = 22 * 60, blockedBeforeMin = 6 * 60)
        assertTrue(r.isNightWindow(23 * 60))
        assertTrue(r.isNightWindow(0))
        assertTrue(r.isNightWindow(5 * 60 + 59))
        assertFalse(r.isNightWindow(6 * 60))
        assertFalse(r.isNightWindow(12 * 60))
        assertFalse(r.isNightWindow(21 * 60 + 59))
        assertNotNull(r.playbackBlocked(23 * 60))
        assertNull(r.playbackBlocked(12 * 60))
    }

    @Test fun disabledRestrictionsAllowEverything() {
        val r = Restrictions()
        assertNull(r.playbackBlocked(23 * 60))
        assertNull(r.needsPin(12 * 60))
    }

    @Test fun pinRequirement() {
        val r = Restrictions(enabled = true, requirePinForPlayback = true)
        assertNotNull(r.needsPin(12 * 60))
        val r2 = Restrictions(enabled = true)
        assertNull(r2.needsPin(12 * 60))
    }

    @Test fun parseMinutes() {
        assertEquals(22 * 60, Restrictions.parseMin("22:00"))
        assertEquals(6 * 60, Restrictions.parseMin("06:00"))
        assertEquals(6 * 60 + 5, Restrictions.parseMin("6:05"))
        assertNull(Restrictions.parseMin("25:00"))
        assertNull(Restrictions.parseMin("abc"))
        assertEquals("22:00", Restrictions.fmt(22 * 60))
    }
}
