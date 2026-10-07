package castbridge.core.dl

import castbridge.core.tv.SystemSync
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * « Préparer le retrait de la clé USB » et les téléchargements (docs/STORAGE.md § « Retrait sûr ») : aria2 écrit lui aussi sur la clé. Retenir la clé met ses téléchargements en pause tout de suite,
 * les garde en pause tant qu'elle est retenue, n'y place aucun nouveau téléchargement, puis les reprend tout seuls quand elle est rendue. Un fichier téléchargé qui arrive dans la bibliothèque d'une
 * clé demande un vidage du système, une fois, à la fin de son fichier.
 */
class DownloadHoldTest {
    private fun speedUp(r: DlRig, id: String, completed: Long = 400, total: Long = 1000) {
        val d = r.fake.dls[r.gidOf(id)]!!; d.total = total; d.completed = completed; d.speed = 50_000
    }

    @Test fun `holding a drive pauses its downloads at once and keeps them paused while it stays plugged`() {
        val r = DlRig(); r.dm.acceptWarning()
        val id = r.ok(r.dm.addLink("https://example.org/film.mkv"))
        val gid = r.gidOf(id)
        r.dm.hold("usb", true)
        assertEquals("paused", r.fake.dls[gid]!!.status, "paused at once, not at the next tick")
        repeat(3) { r.dm.tick() }
        assertEquals("paused", r.fake.dls[gid]!!.status, "the drive is still there, but held: nothing resumes it")
        assertEquals(DlState.WAITING_DRIVE, r.task(id).state)
    }

    @Test fun `lifting the hold resumes the downloads by themselves`() {
        val r = DlRig(); r.dm.acceptWarning()
        val id = r.ok(r.dm.addLink("https://example.org/film.mkv"))
        val gid = r.gidOf(id)
        r.dm.hold("usb", true); r.dm.tick()
        r.dm.hold("usb", false)
        r.dm.tick()
        assertEquals("active", r.fake.dls[gid]!!.status)
        assertNotEquals(DlState.WAITING_DRIVE, r.task(id).state)
    }

    @Test fun `a download the owner paused stays paused after the hold`() {
        val r = DlRig(); r.dm.acceptWarning()
        val id = r.ok(r.dm.addLink("https://example.org/film.mkv"))
        r.ok(r.dm.pause(id))
        r.dm.hold("usb", true); r.dm.tick()
        r.dm.hold("usb", false); r.dm.tick()
        assertEquals(DlState.PAUSED, r.task(id).state, "his pause is his: the removal did not take it")
    }

    @Test fun `only the held drive is touched`() {
        val r = DlRig(); r.dm.acceptWarning()
        r.free["internal"] = 5 * (1L shl 30)
        val onUsb = r.ok(r.dm.addLink("https://example.org/a.mkv", volume = "usb"))
        val onInternal = r.ok(r.dm.addLink("https://example.org/b.mkv", volume = "internal"))
        r.dm.hold("usb", true); r.dm.tick()
        assertEquals("paused", r.fake.dls[r.gidOf(onUsb)]!!.status)
        assertEquals("active", r.fake.dls[r.gidOf(onInternal)]!!.status)
    }

    @Test fun `no new download is placed on a held drive`() {
        val r = DlRig(); r.dm.acceptWarning()
        r.dm.hold("usb", true)
        val no = r.dm.addLink("https://example.org/film.mkv")
        assertIs<DownloadManager.Result.Refused>(no, "the internal memory of the rig has no room: nothing may go to the held drive instead")
        assertTrue(r.fake.dls.isEmpty())
        r.dm.hold("usb", false)
        r.ok(r.dm.addLink("https://example.org/film.mkv"))
        assertEquals(1, r.fake.dls.size)
    }

    @Test fun `a held drive's download is not resumed by hand either`() {
        val r = DlRig(); r.dm.acceptWarning()
        val id = r.ok(r.dm.addLink("https://example.org/film.mkv"))
        r.dm.hold("usb", true)
        val no = r.dm.resume(id) as DownloadManager.Result.Refused
        assertTrue("retrait" in no.message, no.message)
        assertEquals("paused", r.fake.dls[r.gidOf(id)]!!.status)
    }

    @Test fun `the downloads writing to a drive are listed from the last tick, paused ones are not`() {
        val r = DlRig(); r.dm.acceptWarning()
        val id = r.ok(r.dm.addLink("https://example.org/film.mkv"))
        speedUp(r, id)
        r.dm.tick()
        assertEquals(listOf("film.mkv" to 40), r.dm.writingOn("usb"))
        assertEquals(emptyList(), r.dm.writingOn("internal"))
        r.dm.hold("usb", true)
        assertEquals(emptyList(), r.dm.writingOn("usb"), "paused: it writes nothing")
        r.dm.tick()
        assertEquals(emptyList(), r.dm.writingOn("usb"))
    }

    @Test fun `a queued download writes nothing, so it does not hold the drive`() {
        val r = DlRig(); r.dm.acceptWarning()
        val id = r.ok(r.dm.addLink("https://example.org/film.mkv"))
        r.fake.dls[r.gidOf(id)]!!.status = "waiting"
        r.dm.tick()
        assertEquals(emptyList(), r.dm.writingOn("usb"))
    }

    @Test fun `a download that is not under way yet does not hold the drive`() {
        val r = DlRig(); r.dm.acceptWarning()
        r.ok(r.dm.addLink("https://example.org/film.mkv"))
        // before any tick nothing is known of its progress: not listed (no call to aria2 from this question)
        assertEquals(emptyList(), r.dm.writingOn("usb"))
    }

    @Test fun `a finished download on a removable drive asks for one system flush, on the internal memory none`() {
        val r = DlRig(); r.dm.acceptWarning()
        val soons = AtomicInteger()
        r.dm.systemSync = object : SystemSync { override fun now(timeoutMs: Long) = true; override fun soon() { soons.incrementAndGet() } }
        r.free["internal"] = 5 * (1L shl 30)
        val a = r.ok(r.dm.addLink("https://example.org/a.mkv", volume = "internal"))
        val ga = r.gidOf(a)
        r.fake.complete(ga); r.dm.tick()
        assertEquals(0, soons.get(), "internal memory: no removable medium")
        val b = r.ok(r.dm.addLink("https://example.org/b.mkv", volume = "usb"))
        r.fake.complete(r.gidOf(b)); r.dm.tick()
        assertTrue(File(r.usbDir, "b.mkv").exists() || r.dm.finished().isNotEmpty())
        assertEquals(1, soons.get(), "once, at the end of the file")
    }
}
