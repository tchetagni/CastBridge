package castbridge.core.tokens

import castbridge.core.owner.Envelope
import kotlin.test.*

class TokenSyncPolicyTest {
    private val k = TokenKit()
    @AfterTest fun cleanup() = k.close()

    private fun verifier(w: TokenWallet) = { t: String -> (k.verify(t, w.lastGrant()) as? TokenGrantResult.Accepted)?.grant }

    @Test fun applyingAReplyCreditsInOrderAndIsIdempotent() {
        val w = k.wallet()
        val reply = TokenSync.Reply(0, listOf(k.token(2, 30), k.token(1, 20)), 80, true, "Bienvenue")
        val a = TokenSync.apply(reply, w, verifier(w))
        assertEquals(2, a.credited); assertEquals(0, a.rejected); assertEquals(50, w.balance()); assertEquals(2, w.lastGrant()); assertEquals("Bienvenue", a.message)
        val b = TokenSync.apply(reply, w, verifier(w))
        assertEquals(0, b.credited); assertEquals(2, b.rejected); assertEquals(50, w.balance())       // already credited: the verifier refuses them (STALE), nothing is added
        val c = TokenSync.apply(reply, w) { t -> TokenGrant.parseBody(Envelope.decode(t)!!.body) }       // even a verifier that lets them through cannot credit twice
        assertEquals(0, c.credited); assertEquals(2, c.skipped); assertEquals(50, w.balance())
    }

    @Test fun anUnverifiableGrantIsRejectedAndTheAckIsApplied() {
        val w = k.wallet(); k.credit(w, 1, 20)
        w.spend("second-chance", 5, 1, "op-1"); w.spend("extra-joker", 2, 2, "op-2")
        val a = TokenSync.apply(TokenSync.Reply(1, listOf("cbx1.xx.yy", k.token(2, 10)), 0, false, null), w, verifier(w))
        assertEquals(1, a.credited); assertEquals(1, a.rejected); assertTrue(a.acked); assertFalse(a.offlineAllowed); assertEquals(23, w.balance()) // 20 - 7 + 10
        assertFalse(TokenSync.apply(TokenSync.Reply(9, emptyList(), 0, true, null), w, verifier(w)).acked, "an ack beyond the local sequence is ignored")
    }

    @Test fun replyJsonRoundTripAndBounds() {
        val r = TokenSync.Reply(3, listOf("cbx1.a.b"), 12, false, "Message")
        val p = TokenSync.Reply.parse(r.toJson())!!
        assertEquals(3, p.ackedSeq); assertEquals(listOf("cbx1.a.b"), p.grants); assertEquals(12, p.balanceServer); assertFalse(p.offlineAllowed); assertEquals("Message", p.message)
        assertNull(TokenSync.Reply.parse("""{"ackedSeq":-1}"""))
        assertNull(TokenSync.Reply.parse("""{"ackedSeq":1,"grants":[1]}"""))
        assertNull(TokenSync.Reply.parse("""{"grants":[]}"""))
        assertNull(TokenSync.Reply.parse("pas du json"))
        assertNull(TokenSync.Reply.parse("""{"ackedSeq":1,"grants":[${(1..51).joinToString(",") { "\"x\"" }}]}"""))
        assertTrue(TokenSync.Reply.parse("""{"ackedSeq":0}""")!!.offlineAllowed)
    }

    @Test fun kidAllowance() {
        val a = KidAllowance(10, 4, "2026-10-02")
        assertTrue(a.canSpend(6)); assertFalse(a.canSpend(7)); assertFalse(a.canSpend(0)); assertFalse(a.canSpend(-1))
        assertFalse(KidAllowance(0, 0, "d").canSpend(1), "allocation 0 = code parental à chaque dépense")
        assertEquals(9, a.after(5).spentToday); assertEquals(0, a.forDay("2026-10-03").spentToday); assertEquals(4, a.forDay("2026-10-02").spentToday)
        assertFalse(KidAllowance(Int.MAX_VALUE, Int.MAX_VALUE, "d").canSpend(1))
    }

    @Test fun policyTextsAndLimits() {
        val p = TokenPolicy()
        assertEquals("Utiliser 5 jetons (cinq) pour « Seconde chance » ? Solde : 23 jetons", p.confirmText(TokenItem.SECOND_CHANCE, 23))
        assertEquals(listOf(1, 2, 1), TokenItem.values().map { p.maxPerGame(it) }); assertEquals(listOf(5L, 2L, 3L), TokenItem.values().map { p.cost(it) })
        assertTrue(TokenItem.values().all { p.explain(it).endsWith(".") })
        assertFailsWith<IllegalArgumentException> { TokenSettings(secondChance = 0) }
        assertEquals("quiz:g1:second-chance:2", TokenPolicy.opKey("g1", TokenItem.SECOND_CHANCE, 2))
        assertFailsWith<IllegalArgumentException> { TokenPolicy.opKey("g|1", TokenItem.SECOND_CHANCE, 1) }
        assertEquals("quiz:g1:extra-joker:", TokenPolicy.opPrefix("g1", TokenItem.EXTRA_JOKER))
    }

    @Test fun frenchNumbers() {
        val want = mapOf(0 to "zéro", 16 to "seize", 17 to "dix-sept", 22 to "vingt-deux", 31 to "trente et un", 61 to "soixante et un", 70 to "soixante-dix", 72 to "soixante-douze", 77 to "soixante-dix-sept",
            90 to "quatre-vingt-dix", 100 to "cent", 180 to "cent quatre-vingts", 234 to "deux cent trente-quatre", 300 to "trois cents", 1001 to "mille un", 1999 to "mille neuf cent quatre-vingt-dix-neuf",
            2000 to "deux mille", 2200 to "deux mille deux cents", 80_00 to "huit mille", 10000 to "dix mille")
        want.forEach { (n, w) -> assertEquals(w, FrenchNumbers.words(n), "$n") }
        assertEquals(10_001, (0..10_000).map { FrenchNumbers.words(it) }.toSet().size, "chaque nombre a un texte distinct")
        assertFailsWith<IllegalArgumentException> { FrenchNumbers.words(10_001L) }
        assertFailsWith<IllegalArgumentException> { FrenchNumbers.words(-1L) }
    }

    @Test fun grantBodyIsCanonicalAndIssueRefusesLongWindows() {
        val g = TokenGrant("lic-1", 3, 20, k.install.pub, 0)
        assertEquals(g, TokenGrant.parseBody(g.body()))
        assertNull(TokenGrant.parseBody(g.body().reversed())); assertNull(TokenGrant.parseBody(g.body().map { it.replace("grant=3", "grant=03") })); assertNull(TokenGrant.parseBody(g.body().drop(1)))
        val t = Envelope.Target.Device(DeviceIdentity_k(), k.dev.byKind)
        assertFailsWith<IllegalArgumentException> { TokenGrant.issue(k.server, 1, "00000000000000aa", k.t0, k.t0, k.t0 + 31L * 86_400_000, t, g) }
        assertFailsWith<IllegalArgumentException> { TokenGrant.issue(k.server, 1, "zz", k.t0, k.t0, k.t0 + 1000, t, g) }
        assertEquals(k.install.installId, g.installId)
    }

    private fun DeviceIdentity_k() = castbridge.core.owner.DeviceIdentity.kFor(k.dev.n)
}
