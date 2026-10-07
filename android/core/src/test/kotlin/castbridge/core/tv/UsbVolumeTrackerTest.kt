package castbridge.core.tv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** L'historique d'une clé : ce que la TV retient des états qu'Android annonce, pour que [UsbVolumeState] dise la bonne chose au bon moment. */
class UsbVolumeTrackerTest {
    private var t = 1_000_000L
    private val saved = ArrayList<Set<String>>()
    private fun tracker(remembered: Set<String> = emptySet()) = UsbVolumeTracker({ t }, remembered) { saved += it }
    private fun UsbVolumeTracker.verdict(id: String = K, writing: Int = 0) = entries { writing }.firstOrNull { it.id == id }?.verdict
    private val K = "A379-E209"

    @Test fun `a key plugged in goes through the check to a ready key, said for a few seconds`() {
        val k = tracker()
        k.seen(K, "Lexar", MediaState.CHECKING)
        assertEquals(UsbPhase.CHECKING, k.verdict()!!.phase)
        t += 4_000
        val change = k.seen(K, "Lexar", MediaState.MOUNTED)!!
        assertEquals(MediaState.CHECKING, change.from)
        assertEquals(MediaState.MOUNTED, change.to)
        assertTrue(change.afterCheck)
        assertEquals("Clé « Lexar » prête", k.verdict()!!.line)
        assertEquals(UsbPhase.JUST_READY, k.verdict()!!.phase)
        t += UsbVolumeState.READY_NOTICE_MS
        assertEquals(UsbPhase.READY, k.verdict()!!.phase)
    }

    @Test fun `a key already mounted when the TV starts is ready, without a notice`() {
        val k = tracker()
        val change = k.seen(K, "Lexar", MediaState.MOUNTED)!!
        assertNull(change.from)
        assertFalse(change.afterCheck)
        assertEquals(UsbPhase.READY, k.verdict()!!.phase)
        assertFalse(k.verdict()!!.notable)
    }

    @Test fun `the same state seen again changes nothing and does not restart the clock`() {
        val k = tracker()
        k.seen(K, "Lexar", MediaState.CHECKING)
        t += 15_000
        assertNull(k.seen(K, "Lexar", MediaState.CHECKING), "the system read and the broadcast say the same thing")
        t += 10_000
        assertTrue("depuis 25 s" in k.verdict()!!.line, k.verdict()!!.line)
    }

    @Test fun `an unknown reading never overwrites what is known`() {
        val k = tracker()
        k.seen(K, "Lexar", MediaState.MOUNTED)
        assertNull(k.seen(K, "Lexar", MediaState.UNKNOWN))
        assertEquals(UsbPhase.READY, k.verdict()!!.phase)
        assertNull(tracker().seen("X", "x", MediaState.UNKNOWN), "nothing to track")
        assertTrue(tracker().also { it.seen("X", "x", MediaState.UNKNOWN) }.entries().isEmpty())
    }

    @Test fun `a key pulled without ejection keeps its notice, names the cut file, and the follow-ups do not erase it`() {
        val k = tracker()
        k.seen(K, "Lexar", MediaState.MOUNTED)
        t += 60_000
        val change = k.seen(K, "Lexar", MediaState.BAD_REMOVAL, interruptedCopy = "Film.mkv")!!
        assertEquals(MediaState.BAD_REMOVAL, change.to)
        assertEquals("Clé retirée pendant une copie : le fichier « Film.mkv » est incomplet, il sera repris", k.verdict()!!.line)
        // Android then says « unmounted » and « removed »: the notice stays
        assertNull(k.seen(K, "Lexar", MediaState.UNMOUNTED))
        assertNull(k.seen(K, "Lexar", MediaState.REMOVED))
        assertEquals(UsbPhase.REMOVED_BADLY, k.verdict()!!.phase)
        // and goes away after ten minutes
        t += UsbVolumeState.BAD_REMOVAL_KEEP_MS
        assertNull(k.verdict())
        assertTrue(k.entries().isEmpty(), "the key is forgotten")
    }

    @Test fun `a key pulled after the preparation is told apart from an accident, and Android's next check is still announced`() {
        val k = tracker()
        k.seen(K, "Lexar", MediaState.MOUNTED)
        k.setPullReady(K, true)
        t += 20_000
        k.seen(K, "Lexar", MediaState.BAD_REMOVAL)                       // Android calls it « bad removal » even after the preparation: only an ejection is clean for it
        assertEquals(UsbPhase.REMOVED_PREPARED, k.verdict()!!.phase)
        assertEquals(listOf(setOf(K)), saved, "the volume was not unmounted: Android will check it, and says so")
        t += 5_000
        k.seen(K, "Lexar", MediaState.CHECKING)
        assertTrue("(elle a été retirée sans éjection)" in k.verdict()!!.line, k.verdict()!!.line)
        // the next accident is an accident again
        k.seen(K, "Lexar", MediaState.MOUNTED)
        k.seen(K, "Lexar", MediaState.BAD_REMOVAL)
        assertEquals(UsbPhase.REMOVED_BADLY, k.verdict()!!.phase, "not prepared this time")
    }

    @Test fun `a key that was not prepared when it was pulled is an accident, whatever happened before`() {
        val k = tracker()
        k.seen(K, "Lexar", MediaState.MOUNTED)
        k.setPullReady(K, true); k.setPullReady(K, false)               // prepared, then the owner took the key back into service
        k.seen(K, "Lexar", MediaState.BAD_REMOVAL)
        assertEquals(UsbPhase.REMOVED_BADLY, k.verdict()!!.phase)
        val j = tracker()
        j.seen(K, "Lexar", MediaState.CHECKING)
        j.setPullReady(K, true)                                          // refused: not mounted
        j.seen(K, "Lexar", MediaState.BAD_REMOVAL)
        assertEquals(UsbPhase.REMOVED_BADLY, j.verdict()!!.phase)
    }

    @Test fun `a removal with no copy running names no file`() {
        val k = tracker()
        k.seen(K, "Lexar", MediaState.MOUNTED)
        k.seen(K, "Lexar", MediaState.BAD_REMOVAL)
        assertFalse("pendant une copie" in k.verdict()!!.line)
        assertTrue("sans éjection" in k.verdict()!!.line)
    }

    @Test fun `the cut file is learned if it is told a moment after the removal`() {
        val k = tracker()
        k.seen(K, "Lexar", MediaState.MOUNTED)
        k.seen(K, "Lexar", MediaState.BAD_REMOVAL)
        k.seen(K, "Lexar", MediaState.BAD_REMOVAL, interruptedCopy = "A.mkv")
        assertTrue("« A.mkv »" in k.verdict()!!.line, k.verdict()!!.line)
    }

    @Test fun `the next check says why, then the memory is cleared by the mount`() {
        val k = tracker()
        k.seen(K, "Lexar", MediaState.MOUNTED)
        k.seen(K, "Lexar", MediaState.BAD_REMOVAL)
        assertEquals(listOf(setOf(K)), saved, "remembered, so that a restart of the TV does not forget it")
        t += 120_000
        k.seen(K, "Lexar", MediaState.CHECKING)
        assertEquals("Clé « Lexar » : vérification par Android (elle a été retirée sans éjection)… patientez", k.verdict()!!.line)
        k.seen(K, "Lexar", MediaState.MOUNTED)
        assertEquals(listOf(setOf(K), emptySet()), saved, "the check dealt with it")
        // a later check of the clean key does not accuse anybody
        k.seen(K, "Lexar", MediaState.EJECTING); k.seen(K, "Lexar", MediaState.UNMOUNTED); k.seen(K, "Lexar", MediaState.REMOVED)
        k.seen(K, "Lexar", MediaState.CHECKING)
        assertFalse("sans éjection" in k.verdict()!!.line, k.verdict()!!.line)
    }

    @Test fun `a cause remembered from a previous run is used at the first check`() {
        val k = tracker(remembered = setOf(K))
        k.seen(K, "Lexar", MediaState.CHECKING)
        assertTrue("(elle a été retirée sans éjection)" in k.verdict()!!.line, k.verdict()!!.line)
        assertTrue(saved.isEmpty(), "nothing changed yet")
    }

    @Test fun `a key found mounted forgets an old cause, a clean ejection too`() {
        val a = tracker(remembered = setOf(K))
        a.seen(K, "Lexar", MediaState.MOUNTED)
        assertEquals(listOf(emptySet<String>()), saved, "mounted now: whatever was dirty has been dealt with")
        saved.clear()
        val b = tracker(remembered = setOf(K))
        b.seen("other", "x", MediaState.MOUNTED)
        assertTrue(saved.isEmpty(), "another key says nothing about this one")
        b.seen(K, "Lexar", MediaState.EJECTING)
        assertEquals(listOf(emptySet<String>()), saved, "ejected properly: the next check is a normal one")
    }

    @Test fun `the memory of unclean removals is bounded`() {
        val k = tracker()
        for (i in 1..12) { k.seen("K$i", "k", MediaState.MOUNTED); k.seen("K$i", "k", MediaState.BAD_REMOVAL) }
        assertEquals(8, saved.last().size)
        assertTrue("K12" in saved.last() && "K1" !in saved.last(), "the oldest are forgotten first")
    }

    @Test fun `an unmountable key is damaged until it is plugged in again and checked`() {
        val k = tracker()
        k.seen(K, "", MediaState.CHECKING)
        t += 30_000
        k.seen(K, "", MediaState.UNMOUNTABLE)
        assertEquals(UsbPhase.DAMAGED, k.verdict()!!.phase)
        assertTrue(k.verdict()!!.line.startsWith("Clé illisible"))
        k.seen(K, "", MediaState.REMOVED)
        assertNull(k.verdict(), "pulled: nothing to say")
        k.seen(K, "Lexar", MediaState.CHECKING)
        t += 2_000
        val change = k.seen(K, "Lexar", MediaState.MOUNTED)!!
        assertTrue(change.afterCheck)
        assertEquals(UsbPhase.JUST_READY, k.verdict()!!.phase)
    }

    @Test fun `a key without a name is named once Android knows it`() {
        val k = tracker()
        k.seen(K, "", MediaState.CHECKING)
        assertTrue(k.verdict()!!.line.startsWith("Clé : vérification"), k.verdict()!!.line)
        k.seen(K, "Lexar", MediaState.CHECKING)
        assertTrue(k.verdict()!!.line.startsWith("Clé « Lexar » : vérification"), k.verdict()!!.line)
        k.seen(K, "", MediaState.CHECKING)
        assertTrue(k.verdict()!!.line.startsWith("Clé « Lexar » : vérification"), "a blank name never erases a known one")
    }

    @Test fun `an ejection ends in a notice that the key may be pulled, then the key is forgotten`() {
        val k = tracker()
        k.seen(K, "Lexar", MediaState.MOUNTED)
        k.seen(K, "Lexar", MediaState.EJECTING)
        assertEquals(UsbPhase.EJECTING, k.verdict()!!.phase)
        k.seen(K, "Lexar", MediaState.UNMOUNTED)
        assertEquals(UsbPhase.EJECTED, k.verdict()!!.phase)
        k.seen(K, "Lexar", MediaState.REMOVED)
        assertNull(k.verdict(), "a clean removal is not an event")
        assertTrue(k.entries().isEmpty())
    }

    @Test fun `pulled during the check the key simply disappears`() {
        val k = tracker()
        k.seen(K, "Lexar", MediaState.CHECKING)
        val change = k.seen(K, "Lexar", MediaState.REMOVED)!!
        assertEquals(MediaState.REMOVED, change.to)
        assertTrue(k.entries().isEmpty())
    }

    @Test fun `copies in progress are asked at the moment of the question`() {
        val k = tracker()
        k.seen(K, "Lexar", MediaState.MOUNTED)
        assertEquals(UsbPhase.READY, k.verdict(writing = 0)!!.phase)
        assertEquals(UsbPhase.WRITING, k.verdict(writing = 2)!!.phase)
        assertEquals("Ne retirez pas la clé : 2 copies en cours", k.verdict(writing = 2)!!.line)
        assertEquals(UsbPhase.READY, k.verdict(writing = 0)!!.phase)
        // asked per key
        k.seen("B", "Autre", MediaState.MOUNTED)
        val e = k.entries { id -> if (id == "B") 1 else 0 }
        assertEquals(UsbPhase.READY, e.first { it.id == K }.verdict.phase)
        assertEquals(UsbPhase.WRITING, e.first { it.id == "B" }.verdict.phase)
    }

    @Test fun `the preparation for removal lasts while the key stays mounted and no longer`() {
        val k = tracker()
        k.seen(K, "Lexar", MediaState.MOUNTED)
        k.setPullReady(K, true)
        assertTrue(k.isPullReady(K))
        assertEquals(UsbPhase.PULL_READY, k.verdict()!!.phase)
        k.setPullReady(K, false)
        assertEquals(UsbPhase.READY, k.verdict()!!.phase)
        k.setPullReady(K, true)
        k.seen(K, "Lexar", MediaState.EJECTING)
        assertFalse(k.isPullReady(K), "any change of state ends it")
        k.seen(K, "Lexar", MediaState.UNMOUNTED)
        k.seen(K, "Lexar", MediaState.MOUNTED)
        assertEquals(UsbPhase.READY, k.verdict()!!.phase)
        k.setPullReady("nobody", true)
        assertFalse(k.isPullReady("nobody"))
    }

    @Test fun `a key that is not mounted cannot be prepared for removal`() {
        val k = tracker()
        k.seen(K, "Lexar", MediaState.CHECKING)
        k.setPullReady(K, true)
        assertFalse(k.isPullReady(K), "nothing to pull out of a key Android has not mounted")
        k.seen(K, "Lexar", MediaState.MOUNTED)
        assertFalse(k.isPullReady(K), "and the request is not kept for later")
    }

    @Test fun `a removal seen first, with its cut file, is said as it is`() {
        val k = tracker()
        k.seen(K, "Lexar", MediaState.BAD_REMOVAL, interruptedCopy = "A.mkv")
        assertEquals("Clé retirée pendant une copie : le fichier « A.mkv » est incomplet, il sera repris", k.verdict()!!.line)
        assertEquals(listOf(setOf(K)), saved)
    }

    @Test fun `keys the system no longer lists are dropped, except a notice that is still worth saying`() {
        val k = tracker()
        k.seen("A", "a", MediaState.MOUNTED)
        k.seen("B", "b", MediaState.CHECKING)
        k.seen("C", "c", MediaState.MOUNTED); k.seen("C", "c", MediaState.BAD_REMOVAL)
        k.seen("D", "d", MediaState.MOUNTED); k.seen("D", "d", MediaState.EJECTING); k.seen("D", "d", MediaState.UNMOUNTED)
        val changes = k.gone(setOf("A"))
        assertEquals(setOf("B"), changes.map { it.id }.toSet(), "the check of a key that left ends; the notices stay")
        assertEquals(setOf("A", "C", "D"), k.entries().map { it.id }.toSet())
        assertEquals(MediaState.REMOVED, changes.single().to)
    }

    @Test fun `a changed state is reported once with where it came from`() {
        val k = tracker()
        assertNull(k.stateOf(K))
        val first = k.seen(K, "Lexar", MediaState.CHECKING)!!
        assertNull(first.from); assertEquals(K, first.id)
        assertEquals(MediaState.CHECKING, k.stateOf(K))
        val second = k.seen(K, "Lexar", MediaState.MOUNTED_READ_ONLY)!!
        assertEquals(MediaState.CHECKING, second.from)
        assertTrue(second.afterCheck, "read-only is still a mount")
        assertEquals(UsbPhase.READ_ONLY, k.verdict()!!.phase)
    }
}
