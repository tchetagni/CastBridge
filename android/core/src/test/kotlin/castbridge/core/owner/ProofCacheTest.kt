package castbridge.core.owner

import castbridge.core.crypto.MemoryWrapper
import castbridge.core.crypto.PlainWrapper
import java.io.File
import kotlin.test.*

class ProofCacheTest {
    private val t0 = 1_800_000_000_000L
    private val day = 24L * 3600 * 1000

    private class MemStore(var text: String? = null, var fail: Boolean = false) : ProofCacheStore {
        override fun load() = text
        override fun save(text: String) { if (fail) throw java.io.IOException("disque plein"); this.text = text }
    }

    private val wrap = MemoryWrapper(ByteArray(32) { 3 })
    private var mono = 0L
    private fun clock() = TvClock(mono = { mono })
    private fun proof(code: String = "AAAA-BBBB-CCCC-DDDD", seq: Long = 1, endsAt: Long? = null, name: String = "Salon") =
        Proof(code, name, "kid0kid0kid0kid0", endsAt, endsAt == null, 0L, seq, "cbx1.a.b")
    private val key = TrustedKey("0123456789abcdef", "cHVibGljLWtleS10ZXN0LW9ubHktMzItYnl0ZXMhIQ==")

    @Test fun aProofIsValidForFourteenDaysThenNeverAgain() {
        val c = ProofCache(MemStore(), clock(), wrap)
        assertTrue(c.put(proof(), t0))
        assertEquals(1, c.validProofs(t0 + 13 * day).size)
        assertTrue(c.validProofs(t0 + 14 * day + 1).isEmpty())
        assertTrue(c.validProofs(t0 + 5 * day).isEmpty())   // once the 14 days were seen, an earlier wall time does not bring it back
        assertEquals(t0, c.proofOf("AAAA-BBBB-CCCC-DDDD")!!.verifiedAt)
    }

    @Test fun neverBeyondTheActivationEnd() {
        val c = ProofCache(MemStore(), clock(), wrap)
        c.put(proof(endsAt = t0 + 5 * day), t0)
        assertEquals(1, c.validProofs(t0 + 5 * day - 1).size)
        assertTrue(c.validProofs(t0 + 5 * day).isEmpty())
    }

    @Test fun aWallClockWoundBackExtendsNothing() {
        val c = ProofCache(MemStore(), clock(), wrap)
        c.put(proof(), t0)
        mono += 15 * day                                   // 15 days really pass, the wall clock is set back to t0 - 30 days
        assertTrue(c.validProofs(t0 - 30 * day).isEmpty())
    }

    @Test fun validUntilNeverMovesBackAndVerifiedAtComesFromTheClock() {
        val c = ProofCache(MemStore(), clock(), wrap)
        c.put(proof(seq = 1), t0 + 10 * day)
        val first = c.validProofs(t0 + 10 * day).single()
        c.put(proof(seq = 2), t0 - 100 * day)              // phone clock far behind at the second link
        val second = c.validProofs(t0 + 10 * day).single()
        assertEquals(first.verifiedAt, second.verifiedAt)  // the clock read the high-water mark, not the wound-back wall clock
        assertTrue(second.validUntil >= first.validUntil)
    }

    @Test fun anOlderSequenceIsNotStored() {
        val c = ProofCache(MemStore(), clock(), wrap)
        assertTrue(c.put(proof(seq = 7, name = "Neuf"), t0))
        assertFalse(c.put(proof(seq = 3, name = "Vieux"), t0 + day))
        assertEquals("Neuf", c.proofOf("AAAA-BBBB-CCCC-DDDD")!!.tvName); assertEquals(7L, c.lastSeq("AAAA-BBBB-CCCC-DDDD"))
        assertNull(c.lastSeq("ZZZZ-ZZZZ-ZZZZ-ZZZZ"))
    }

    @Test fun oneProofPerTvAndLinkedWhenAtLeastOneIsValid() {
        val c = ProofCache(MemStore(), clock(), wrap)
        c.put(proof("AAAA-BBBB-CCCC-DDDD"), t0)
        c.put(proof("EEEE-FFFF-GGGG-HHHH", endsAt = t0 + day), t0)
        assertEquals(2, c.validProofs(t0 + day - 1).size)
        assertEquals(listOf("AAAA-BBBB-CCCC-DDDD"), c.validProofs(t0 + 2 * day).map { it.tvCode })
    }

    @Test fun pinsAreKeptAndForgetRemovesEverything() {
        val c = ProofCache(MemStore(), clock(), wrap)
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
        val c = ProofCache(MemStore(), clock(), wrap)
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
        ProofCache(s, clock(), wrap).apply { pin("AAAA-BBBB-CCCC-DDDD", key); put(proof(endsAt = t0 + 9 * day, seq = 4), t0) }
        val r = ProofCache(s, clock(), wrap)
        assertEquals(key, r.pinned("AAAA-BBBB-CCCC-DDDD")); assertEquals(4L, r.lastSeq("AAAA-BBBB-CCCC-DDDD"))
        assertEquals(t0 + 9 * day, r.proofOf("AAAA-BBBB-CCCC-DDDD")!!.endsAt); assertEquals("cbx1.a.b", r.proofOf("AAAA-BBBB-CCCC-DDDD")!!.activationToken)
        assertEquals(1, r.validProofs(t0 + day).size)
        assertTrue(ProofCache(MemStore("{ pas du json"), clock(), wrap).validProofs(t0).isEmpty())
    }

    @Test fun aFailedWriteThrowsButMemoryIsUpdated() {
        val s = MemStore(fail = true)
        val c = ProofCache(s, clock(), wrap)
        assertFailsWith<java.io.IOException> { c.put(proof(), t0) }
        assertEquals(1, c.validProofs(t0).size)
    }

    @Test fun theFileStoreRoundTrips() {
        val dir = kotlin.io.path.createTempDirectory("proofcache").toFile()
        try {
            val f = File(dir, "proof/cache.json")
            ProofCache(FileProofCacheStore(f), clock(), wrap).put(proof(), t0)
            assertEquals(1, ProofCache(FileProofCacheStore(f), clock(), wrap).validProofs(t0 + day).size)
        } finally { dir.deleteRecursively() }
    }

    // ---- audit w6-03 ----
    private val key2 = TrustedKey("fedcba9876543210", "b3RoZXItcHVibGljLWtleS10ZXN0LW9ubHktMzItYnl0ZXM=")
    private val code = "AAAA-BBBB-CCCC-DDDD"

    @Test fun rePinThenSeqZeroIsAccepted() {
        val c = ProofCache(MemStore(), clock(), wrap)
        c.pin(code, key); c.put(proof(seq = 40), t0)
        c.pin(code, key)                                  // same key: nothing is reset
        assertEquals(40L, c.lastSeq(code)); assertNotNull(c.proofOf(code))
        c.pin(code, key2)                                 // reinstalled TV, other key
        assertNull(c.lastSeq(code)); assertNull(c.proofOf(code)); assertTrue(c.validProofs(t0).isEmpty()); assertEquals(key2, c.pinned(code))
        assertTrue(c.put(proof(seq = 0), t0 + day)); assertEquals(1, c.validProofs(t0 + day).size)
    }

    @Test fun theActivationTokenIsNotStoredInClearAndSurvivesARestart() {
        val s = MemStore()
        ProofCache(s, clock(), wrap).put(proof(), t0)
        assertFalse(s.text!!.contains("cbx1.a.b")); assertFalse(s.text!!.contains("\"activation\""))
        assertEquals("cbx1.a.b", ProofCache(s, clock(), wrap).proofOf(code)!!.activationToken)
    }

    @Test fun anOldClearCacheIsStillReadAndSealedAtTheNextWrite() {
        val s = MemStore()
        ProofCache(s, clock(), PlainWrapper()).put(proof(), t0)                       // PlainWrapper: the token is sealed as itself, still base64
        val legacy = s.text!!.replace(Regex("\"activationSealed\": ?\"[^\"]*\""), "\"activation\":\"cbx1.a.b\"")
        assertTrue(legacy.contains("\"activation\":\"cbx1.a.b\""))
        s.text = legacy
        val c = ProofCache(s, clock(), wrap)
        assertEquals("cbx1.a.b", c.proofOf(code)!!.activationToken)
        c.pin(code, key)
        assertFalse(s.text!!.contains("cbx1.a.b"))
    }

    @Test fun aLostWrapperKeyDropsTheProofButKeepsThePin() {
        val s = MemStore()
        ProofCache(s, clock(), wrap).apply { pin(code, key); put(proof(seq = 6), t0) }
        val r = ProofCache(s, clock(), MemoryWrapper(ByteArray(32) { 4 }))
        assertNull(r.proofOf(code)); assertEquals(key, r.pinned(code)); assertTrue(r.validProofs(t0).isEmpty())
    }

    @Test fun theNewestActivationSeenIsRemembered() {
        val c = ProofCache(MemStore(), clock(), wrap)
        assertNull(c.activationMark(code))
        val fake = proof()
        assertTrue(c.put(fake, t0)); assertNull(c.activationMark(code))                 // "cbx1.a.b" is not a decodable activation
    }
}
