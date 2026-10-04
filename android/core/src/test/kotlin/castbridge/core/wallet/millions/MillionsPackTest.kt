package castbridge.core.wallet.millions

import castbridge.core.owner.TvClock
import castbridge.core.quiz.Json
import castbridge.core.wallet.TestMint
import castbridge.core.wallet.Verdict
import castbridge.core.wallet.WalletRefusal
import castbridge.core.wallet.WalletTestKeys
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MillionsPackTest {
    private val ring = WalletTestKeys.walletRing
    private val clock = TvClock()
    private fun verify(token: String?, id: String = MK.TV, wall: Long = MK.NOW, c: TvClock = clock) = MillionsPack.verify(token, ring, id, c, wall)
    private fun refused(v: Verdict<MillionsPack>) = (v as? Verdict.Rejected)?.reason

    @Test fun goldenPackRoundTrips() {
        val p = MK.pack()
        val v = verify(MK.packToken(p))
        val got = (v as Verdict.Accepted).value
        assertEquals(p, got)
        assertEquals(15, got.levels.size)
        assertEquals(MillionsLadder.DEFAULT, got.ladder)
    }

    @Test fun otherIdentityRefused() { assertEquals(WalletRefusal.OTHER_TV, refused(verify(MK.packToken(MK.pack(id = "tv-autre"))))) }

    @Test fun validityWindow() {
        assertEquals(WalletRefusal.EXPIRED, refused(verify(MK.packToken(), wall = MK.NOW + 7 * MK.DAY)))
        assertTrue(verify(MK.packToken(), wall = MK.NOW + 7 * MK.DAY - 1) is Verdict.Accepted)
        assertEquals(WalletRefusal.NOT_YET_VALID, refused(verify(MK.packToken(MK.pack(from = MK.NOW + 3_600_000)), wall = MK.NOW)))
        assertEquals(WalletRefusal.TOO_LONG_LIFE, refused(verify(MK.packToken(MK.pack(until = MK.NOW - 1000 + 14 * MK.DAY + 1)))))
        assertTrue(verify(MK.packToken(MK.pack(until = MK.NOW - 1000 + 14 * MK.DAY))) is Verdict.Accepted)
    }

    @Test fun usesTvClockNotOnlyWallClock() {
        val c = TvClock(lastSeen = MK.NOW + 8 * MK.DAY)     // la TV a déjà vu une heure postérieure : un retour en arrière ne rouvre rien
        assertEquals(WalletRefusal.EXPIRED, refused(verify(MK.packToken(), wall = MK.NOW, c = c)))
    }

    @Test fun signatureAndDomain() {
        val t = MK.packToken().split('.')
        val tampered = String(Base64.getUrlDecoder().decode(t[1])).replace("\"stake\":500", "\"stake\":501")
        assertEquals(WalletRefusal.BAD_SIGNATURE, refused(verify("${t[0]}.${Base64.getUrlEncoder().withoutPadding().encodeToString(tampered.toByteArray())}.${t[2]}")))
        assertEquals(WalletRefusal.BAD_SIGNATURE, refused(verify(MK.packToken(domain = "castbridge-wallet-snapshot-v1"))))
        assertEquals(WalletRefusal.BAD_SIGNATURE, refused(verify(MK.packToken(domain = ""))))
        assertEquals(WalletRefusal.UNKNOWN_KEY, refused(verify(MK.packToken(MK.pack(kid = WalletTestKeys.stranger.keyId), WalletTestKeys.stranger))))
        assertEquals(WalletRefusal.UNKNOWN_KEY, refused(verify(MK.packToken(MK.pack(kid = WalletTestKeys.result.keyId), WalletTestKeys.result))))
        assertEquals(WalletRefusal.UNREADABLE, refused(verify(null)))
        assertEquals(WalletRefusal.UNREADABLE, refused(verify("cbw1." + t[1] + "." + t[2])))
        assertEquals(WalletRefusal.UNREADABLE, refused(verify("x".repeat(MillionsPack.MAX_LENGTH + 1))))
    }

    @Test fun strictParsing() {
        val compact = Json.write(MK.pack().payload())
        fun raw(text: String) = TestMint.raw(MillionsPack.PREFIX, MillionsPack.DOMAIN, text, WalletTestKeys.wallet)
        assertEquals(WalletRefusal.UNREADABLE, refused(verify(raw(compact.dropLast(1) + ",\"extra\":1}"))))
        assertEquals(WalletRefusal.UNREADABLE, refused(verify(raw(compact.replace(",", ", ")))))
        assertEquals(WalletRefusal.UNREADABLE, refused(verify(raw(compact.dropLast(1) + ",\"stake\":1}"))))
        assertEquals(WalletRefusal.UNREADABLE, refused(verify(raw(compact.replace("\"stake\":500", "\"stake\":500.0")))))
    }

    @Test fun refusesInvalidContent() {
        val q = MK.question(1, 0)
        fun oob(p: MillionsPack) = assertEquals(WalletRefusal.OUT_OF_BOUNDS, refused(verify(MK.packToken(p))))
        oob(MK.pack(ladder = MillionsLadder(MillionsLadder.B.toMutableList().also { it[3] = it[2] }, 500)))
        oob(MK.pack(levels = MK.levels().toMutableList().also { it[0] = List(21) { i -> MK.question(1, i) } }))
        oob(MK.pack(levels = MK.levels().toMutableList().also { it[2] = emptyList() }))
        oob(MK.pack(levels = MK.levels().dropLast(1)))
        oob(MK.pack(levels = MK.levels().toMutableList().also { it[1] = listOf(MK.question(1, 0)) }))                            // qid en double entre niveaux
        oob(MK.pack(levels = MK.levels().toMutableList().also { it[0] = listOf(q.copy(fifty = listOf(q.correct, (q.correct + 1) % 4))) }))   // le 50:50 retire la bonne réponse
        oob(MK.pack(levels = MK.levels().toMutableList().also { it[0] = listOf(q.copy(fifty = listOf(1, 1))) }))
        oob(MK.pack(levels = MK.levels().toMutableList().also { it[0] = listOf(q.copy(choices = listOf("A", "A", "C", "D"))) }))
        oob(MK.pack(levels = MK.levels().toMutableList().also { it[0] = listOf(q.copy(text = "x".repeat(401))) }))
        oob(MK.pack(timeSec = 4)); oob(MK.pack(timeSec = 121)); oob(MK.pack(maxPlays = 0)); oob(MK.pack(limits = WinLimits(0, 10, 15)))
        oob(MK.pack(limits = WinLimits(5, 4, 15)))    // jour > semaine : incohérent
        oob(MK.pack(mw = ServerCounts("2026-09-20", -1, "2026-09-14", 0, "2026-09", 0)))
        oob(MK.pack(mw = ServerCounts("hier", 0, "2026-09-14", 0, "2026-09", 0)))
    }

    @Test fun packWithMaxContentFitsTheBound() {
        val big = List(15) { l -> List(20) { i -> MillionsQuestion("q-$l-$i-" + "x".repeat(20), "T".repeat(300), List(4) { c -> "c$c".padEnd(120, 'c') }, 0, listOf(1, 2)) } }
        val token = MK.packToken(MK.pack(levels = big))
        assertTrue(token.length <= MillionsPack.MAX_LENGTH, "${token.length}")
        assertTrue(verify(token) is Verdict.Accepted)
    }

    @Test fun nextIsFirstUnplayedOfLevelInPackOrder() {
        val p = MK.pack()
        assertEquals("q-4-0", p.next(4, emptySet())!!.qid)
        assertEquals("q-4-1", p.next(4, setOf("q-4-0"))!!.qid)
        assertEquals("q-4-0", p.next(4, setOf("q-4-1", "q-4-2"))!!.qid)
        assertNull(p.next(4, setOf("q-4-0", "q-4-1", "q-4-2")))
        assertNull(p.next(0, emptySet())); assertNull(p.next(16, emptySet()))
    }

    @Test fun locateFindsLevel() {
        val p = MK.pack()
        assertEquals(7, p.locate("q-7-2")!!.first); assertNull(p.locate("q-7-9"))
        assertNotNull(p.locate("q-15-0"))
    }
}
