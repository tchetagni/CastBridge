package castbridge.core.trust

import kotlin.test.*

class CopyJournalTest {
    private var t = 10_000L
    private val ring = MemoryTrustPersistence(); private val hist = MemoryTrustPersistence()
    private fun j() = CopyJournal(ring, hist) { t }
    private fun e(name: String, ok: Boolean, at: Long = t) = CopyEntry(at, name, ok, if (ok) "OK" else "CONNECTION_LOST", "envoi", if (ok) 100 else 63, "texte")

    @Test fun ringKeepsTheLast200LinesWithTimestampStepAndCause() {
        val j = j(); repeat(250) { t++; j.log(CopyStep.SEND, "ligne $it") }
        val l = j().lines()
        assertEquals(200, l.size); assertTrue(l.last().contains("ligne 249")); assertTrue(l.first().contains("ligne 50"))
        assertTrue(l.last().startsWith("10250\tenvoi\t"), l.last())
    }

    @Test fun ringLinesCarryNoPathPinOrToken() {
        val j = j()
        j.log(CopyStep.CONNECT, "échec /storage/emulated/0/x.mp4 content://a/b pin 482913 Bearer cbt_aabbccddeeff0011\nsuite")
        val l = j().lines().single()
        assertFalse("/storage" in l || "content://" in l || "482913" in l || "cbt_" in l || "Bearer" in l, l)
        assertEquals(1, ring.text!!.lines().filter { it.isNotEmpty() }.size)
    }

    @Test fun historyKeepsTheLast20NewestFirst() {
        val j = j(); repeat(25) { t++; j.record(e("f$it.mp4", it % 2 == 0)) }
        val r = j().recent()
        assertEquals(20, r.size); assertEquals("f24.mp4", r.first().name); assertEquals("f5.mp4", r.last().name)
        assertTrue(r.first().ok); assertFalse(r[1].ok)
    }

    @Test fun historySurvivesARestartAndABrokenLine() {
        j().record(e("a.mp4", false))
        hist.text = hist.text + "\ncassé\n\t\t\n"
        val r = j().recent(); assertEquals(1, r.size); assertEquals("a.mp4", r.single().name); assertEquals(63, r.single().percent)
    }

    @Test fun nameIsABaseNameNeverAPath() {
        j().record(e("/storage/emulated/0/Movies/secret.mp4", true))
        assertEquals("secret.mp4", j().recent().single().name)
    }

    @Test fun lastFailureForAFileWithinTheWindow() {
        val j = j(); t = 1_000; j.record(e("a.mp4", false)); t = 2_000; j.record(e("b.mp4", false)); t = 3_000; j.record(e("a.mp4", true))
        t = 4_000
        assertNull(j().lastFailureFor("a.mp4", 10_000), "the latest result of that file is a success")
        assertEquals("b.mp4", j().lastFailureFor("b.mp4", 10_000)?.name)
        t = 100_000
        assertNull(j().lastFailureFor("b.mp4", 10_000), "too old")
    }
}
