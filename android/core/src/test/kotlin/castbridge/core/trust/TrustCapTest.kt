package castbridge.core.trust

import castbridge.core.FakeClock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.*

/** Règle du propriétaire (2026-10-04) : une TV ne se synchronise pas avec plus de 8 téléphones ; jamais d'éviction silencieuse. */
class TrustCapTest {
    private val clock = FakeClock()
    private fun addr(i: Int) = "AA:BB:CC:DD:EE:%02X".format(i)

    /** Garde chaque écriture : le nombre maximal de téléphones jamais écrits, et la possibilité de faire échouer l'écriture. */
    private class Recording : TrustPersistence {
        var text: String? = null
        var saves = 0
        var maxPhonesWritten = 0
        var fail = false
        override fun load() = text
        override fun save(text: String) {
            if (fail) throw java.io.IOException("disque plein")
            saves++
            maxPhonesWritten = maxOf(maxPhonesWritten, text.lines().count { it.startsWith("P\t") })
            this.text = text
        }
    }

    private fun registry(p: TrustPersistence = MemoryTrustPersistence()) = TrustRegistry(p, clock::now)
    private fun TrustRegistry.fill(n: Int) { repeat(n) { clock.advance(1_000); trust(addr(it), "Tel $it") } }

    @Test fun theConstantIsEight() = assertEquals(8, TrustRegistry.MAX_PHONES)

    @Test fun eightPhonesAreAccepted() {
        val r = registry()
        repeat(8) { assertIs<TrustResult.Added>(r.trust(addr(it), "Tel $it")) }
        assertEquals(8, r.list().size)
    }

    @Test fun theNinthPhoneIsRefusedWithTheListAndNothingChanges() {
        val p = Recording(); val r = registry(p)
        r.fill(8)
        val saves = p.saves
        val res = r.trust(addr(50), "Neuvième")
        assertIs<TrustResult.Full>(res)
        assertEquals(8, res.phones.size)
        assertEquals(8, r.list().size); assertFalse(r.isTrusted(addr(50)))
        assertEquals(saves, p.saves, "a refusal writes nothing")
        assertNull(r.issueToken(addr(50)))
    }

    @Test fun anAlreadyTrustedPhoneRefreshesEvenWhenFull() {
        val r = registry(); r.fill(8)
        val before = r.get(addr(3))!!
        clock.advance(5_000)
        val res = r.trust(addr(3), "Nouveau nom")
        assertIs<TrustResult.Refreshed>(res)
        assertEquals(before.addedAt, r.get(addr(3))!!.addedAt)
        assertEquals("Nouveau nom", r.get(addr(3))!!.name)
        assertTrue(r.get(addr(3))!!.lastSeen > before.lastSeen)
        assertEquals(8, r.list().size)
    }

    @Test fun concurrentAddsNeverExceedEight() {
        val p = Recording(); val r = registry(p)
        val added = AtomicInteger(); val full = AtomicInteger()
        val go = CountDownLatch(1)
        val threads = (0 until 40).map { i -> thread { go.await(); when (r.trust(addr(i), "T$i")) { is TrustResult.Added -> added.incrementAndGet(); is TrustResult.Full -> full.incrementAndGet(); else -> {} } } }
        go.countDown(); threads.forEach { it.join() }
        assertEquals(8, r.list().size)
        assertEquals(8, added.get()); assertEquals(32, full.get())
        assertTrue(p.maxPhonesWritten <= 8, "never more than 8 on disk: ${p.maxPhonesWritten}")
    }

    @Test fun replaceIsOneAtomicWriteAndNeverWritesNine() {
        val p = Recording(); val r = registry(p)
        r.fill(8)
        val old = r.issueToken(addr(0))!!
        val saves = p.saves
        val seen = ArrayList<Int>()
        r.addListener { seen += r.list().size }
        val res = r.replace(addr(0), addr(50), "Nouveau")
        assertIs<TrustResult.Added>(res)
        assertEquals(saves + 1, p.saves, "remove + add = ONE persisted write")
        assertTrue(p.maxPhonesWritten <= 8, "a window of nine was written: ${p.maxPhonesWritten}")
        assertEquals(8, r.list().size)
        assertTrue(r.isTrusted(addr(50))); assertFalse(r.isTrusted(addr(0)))
        assertNull(r.verifyToken(old.token), "the removed phone loses its tokens at once")
        assertTrue(seen.all { it <= 8 }, "$seen")
        // the file alone says the same after a restart
        val again = registry(p)
        assertEquals(8, again.list().size); assertTrue(again.isTrusted(addr(50))); assertFalse(again.isTrusted(addr(0)))
    }

    @Test fun aFailingWriteLeavesTheOwnerWithTheOldPhoneAndWithoutTheNewOne() {
        val p = Recording(); val r = registry(p)
        r.fill(8)
        val tok = r.issueToken(addr(0))!!
        p.fail = true
        val res = r.replace(addr(0), addr(50), "Nouveau")
        assertEquals(TrustResult.WriteFailed, res)
        assertTrue(r.isTrusted(addr(0)), "the phone the owner wanted to replace is still there")
        assertFalse(r.isTrusted(addr(50)))
        assertEquals(8, r.list().size)
        assertEquals(addr(0), r.verifyToken(tok.token), "its token still works: nothing was half done")
        p.fail = false
        assertIs<TrustResult.Added>(r.replace(addr(0), addr(50), "Nouveau"))   // retry works
    }

    @Test fun replacingAPhoneTheTvDoesNotKnowChangesNothing() {
        val r = registry(); r.fill(8)
        assertEquals(TrustResult.NotFound, r.replace(addr(40), addr(50), "Nouveau"))
        assertEquals(8, r.list().size); assertFalse(r.isTrusted(addr(50)))
    }

    @Test fun replacingByAnAlreadyTrustedPhoneJustRefreshesAndRemovesNobody() {
        val r = registry(); r.fill(8)
        assertIs<TrustResult.Refreshed>(r.replace(addr(0), addr(1), "Tel 1"))
        assertEquals(8, r.list().size); assertTrue(r.isTrusted(addr(0)))
    }

    @Test fun roomAfterARemovalAllowsANewPhone() {
        val r = registry(); r.fill(8)
        assertTrue(r.revoke(addr(2)))
        assertIs<TrustResult.Added>(r.trust(addr(50), "Nouveau"))
        assertIs<TrustResult.Full>(r.trust(addr(51), "Encore un"))
    }

    @Test fun aRegistryFileWithMoreThanEightIsKeptButNothingNewEntersUntilRoomIsMade() {
        // a TV updated from a version without the cap may already hold more: nobody is evicted silently, but nobody new is added
        val p = MemoryTrustPersistence((0 until 9).joinToString("\n", postfix = "\n") { "P\t${addr(it)}\t1\t1\tTel" })   // legacy file (no checksum)
        val r = registry(p)
        assertEquals(9, r.list().size, "nobody is evicted silently")
        assertIs<TrustResult.Full>(r.trust(addr(70), "Nouveau"))
    }
}
