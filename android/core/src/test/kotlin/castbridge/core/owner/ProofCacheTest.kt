package castbridge.core.owner

import java.io.File
import kotlin.test.*

class ProofCacheTest {
    private val t0 = 1_800_000_000_000L
    private val day = 24L * 3600 * 1000

    private class MemStore(var text: String? = null, var fail: Boolean = false) : ProofCacheStore {
        override fun load() = text
        override fun save(text: String) { if (fail) throw java.io.IOException("disque plein"); this.text = text }
    }

    private var mono = 0L
    private fun clock() = TvClock(mono = { mono })
    private fun proof(code: String = "AAAA-BBBB-CCCC-DDDD", seq: Long = 1, endsAt: Long? = null, name: String = "Salon") =
        Proof(code, name, "kid0kid0kid0kid0", endsAt, endsAt == null, 0L, seq, "cbx1.a.b")
    private val key = TrustedKey("0123456789abcdef", "cHVibGljLWtleS10ZXN0LW9ubHktMzItYnl0ZXMhIQ==")

    @Test fun aProofIsValidForFourteenDaysThenNeverAgain() {
        val c = ProofCache(MemStore(), clock())
        assertTrue(c.put(proof(), t0))
        assertEquals(1, c.validProofs(t0 + 13 * day).size)
        assertTrue(c.validProofs(t0 + 14 * day + 1).isEmpty())
        assertTrue(c.validProofs(t0 + 5 * day).isEmpty())   // once the 14 days were seen, an earlier wall time does not bring it back
        assertEquals(t0, c.proofOf("AAAA-BBBB-CCCC-DDDD")!!.verifiedAt)
    }

    @Test fun neverBeyondTheActivationEnd() {
        val c = ProofCache(MemStore(), clock())
        c.put(proof(endsAt = t0 + 5 * day), t0)
        assertEquals(1, c.validProofs(t0 + 5 * day - 1).size)
        assertTrue(c.validProofs(t0 + 5 * day).isEmpty())
    }

    @Test fun aWallClockWoundBackExtendsNothing() {
        val c = ProofCache(MemStore(), clock())
        c.put(proof(), t0)
        mono += 15 * day                                   // 15 days really pass, the wall clock is set back to t0 - 30 days
        assertTrue(c.validProofs(t0 - 30 * day).isEmpty())
    }

    @Test fun validUntilNeverMovesBackAndVerifiedAtComesFromTheClock() {
        val c = ProofCache(MemStore(), clock())
        c.put(proof(seq = 1), t0 + 10 * day)
        val first = c.validProofs(t0 + 10 * day).single()
        c.put(proof(seq = 2), t0 - 100 * day)              // phone clock far behind at the second link
        val second = c.validProofs(t0 + 10 * day).single()
        assertEquals(first.verifiedAt, second.verifiedAt)  // the clock read the high-water mark, not the wound-back wall clock
        assertTrue(second.validUntil >= first.validUntil)
    }

    @Test fun anOlderSequenceIsNotStored() {
        val c = ProofCache(MemStore(), clock())
        assertTrue(c.put(proof(seq = 7, name = "Neuf"), t0))
        assertFalse(c.put(proof(seq = 3, name = "Vieux"), t0 + day))
        assertEquals("Neuf", c.proofOf("AAAA-BBBB-CCCC-DDDD")!!.tvName); assertEquals(7L, c.lastSeq("AAAA-BBBB-CCCC-DDDD"))
        assertNull(c.lastSeq("ZZZZ-ZZZZ-ZZZZ-ZZZZ"))
    }

    @Test fun oneProofPerTvAndLinkedWhenAtLeastOneIsValid() {
        val c = ProofCache(MemStore(), clock())
        c.put(proof("AAAA-BBBB-CCCC-DDDD"), t0)
        c.put(proof("EEEE-FFFF-GGGG-HHHH", endsAt = t0 + day), t0)
        assertEquals(2, c.validProofs(t0 + day - 1).size)
        assertEquals(listOf("AAAA-BBBB-CCCC-DDDD"), c.validProofs(t0 + 2 * day).map { it.tvCode })
    }

    @Test fun pinsAreKeptAndForgetRemovesEverything() {
        val c = ProofCache(MemStore(), clock())
        assertNull(c.pinned("AAAA-BBBB-CCCC-DDDD"))
        c.pin("AAAA-BBBB-CCCC-DDDD", key)
        c.put(proof(), t0)
        assertEquals(key, c.pinned("AAAA-BBBB-CCCC-DDDD"))
        c.dropProof("AAAA-BBBB-CCCC-DDDD")
        assertTrue(c.validProofs(t0).isEmpty()); assertEquals(key, c.pinned("AAAA-BBBB-CCCC-DDDD")); assertEquals(1L, c.lastSeq("AAAA-BBBB-CCCC-DDDD"))
        c.put(proof(seq = 2), t0)
        c.forget("AAAA-BBBB-CCCC-DDDD")
        assertNull(c.pinned("AAAA-BBBB-CCCC-DDDD")); assertTrue(c.validProofs(t0).isEmpty()); assertNull(c.proofOf("AAAA-BBBB-CCCC-DDDD"))
    }

    @Test fun dropIfEndedRemovesOnlyAnEndedProof() {
        val c = ProofCache(MemStore(), clock())
        c.put(proof(endsAt = t0 + 5 * day), t0)
        assertFalse(c.dropIfEnded("AAAA-BBBB-CCCC-DDDD", t0 + day))
        assertTrue(c.dropIfEnded("AAAA-BBBB-CCCC-DDDD", t0 + 6 * day))
        assertNull(c.proofOf("AAAA-BBBB-CCCC-DDDD"))
        assertFalse(c.dropIfEnded("AAAA-BBBB-CCCC-DDDD", t0 + 7 * day))
        c.put(proof("EEEE-FFFF-GGGG-HHHH"), t0)   // no end date: never dropped by this
        assertFalse(c.dropIfEnded("EEEE-FFFF-GGGG-HHHH", t0 + 900 * day))
    }

    @Test fun theStateSurvivesARestartAndACorruptFileStartsEmpty() {
        val s = MemStore()
        ProofCache(s, clock()).apply { pin("AAAA-BBBB-CCCC-DDDD", key); put(proof(endsAt = t0 + 9 * day, seq = 4), t0) }
        val r = ProofCache(s, clock())
        assertEquals(key, r.pinned("AAAA-BBBB-CCCC-DDDD")); assertEquals(4L, r.lastSeq("AAAA-BBBB-CCCC-DDDD"))
        assertEquals(t0 + 9 * day, r.proofOf("AAAA-BBBB-CCCC-DDDD")!!.endsAt); assertEquals("cbx1.a.b", r.proofOf("AAAA-BBBB-CCCC-DDDD")!!.activationToken)
        assertEquals(1, r.validProofs(t0 + day).size)
        assertTrue(ProofCache(MemStore("{ pas du json"), clock()).validProofs(t0).isEmpty())
    }

    @Test fun aFailedWriteThrowsButMemoryIsUpdated() {
        val s = MemStore(fail = true)
        val c = ProofCache(s, clock())
        assertFailsWith<java.io.IOException> { c.put(proof(), t0) }
        assertEquals(1, c.validProofs(t0).size)
    }

    @Test fun theFileStoreRoundTrips() {
        val dir = kotlin.io.path.createTempDirectory("proofcache").toFile()
        try {
            val f = File(dir, "proof/cache.json")
            ProofCache(FileProofCacheStore(f), clock()).put(proof(), t0)
            assertEquals(1, ProofCache(FileProofCacheStore(f), clock()).validProofs(t0 + day).size)
        } finally { dir.deleteRecursively() }
    }
}
