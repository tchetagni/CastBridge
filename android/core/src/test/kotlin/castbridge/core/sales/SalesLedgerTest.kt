package castbridge.core.sales

import castbridge.core.owner.AgentFixtures
import castbridge.core.sales.SalesLedger.ChainResult
import castbridge.core.sales.SalesLedger.Entry
import castbridge.core.sales.SalesLedger.Kind
import java.io.File
import kotlin.test.*

class SalesLedgerTest {
    private val agent = AgentFixtures.key("agent").signer
    private val pub = agent.publicKeyBase64
    private val t0 = AgentFixtures.T0
    private val fp = "ab".repeat(32)

    private fun sale(seq: Long, prev: String, item: String = "cle-production|90", price: Long = 5000, cash: Long = price, at: Long = t0 + seq) =
        Entry(seq, at, Kind.SALE, agent.keyId, device = "ABCD-EFGH-JKMN-PQR0", license = "lic-0001", seat = "0123456789abcdef", item = item, price = price, cash = cash, grid = "2026-10-02T09:00:00Z",
            receipt = Receipt.code(agent.keyId, seq, "ABCD-EFGH-JKMN-PQR0", at), fp = fp, prev = prev).sign(agent)
    private fun chain(n: Int): List<Entry> { val out = ArrayList<Entry>(); var prev = SalesLedger.GENESIS; for (i in 1..n) { out += sale(i.toLong(), prev); prev = out.last().hash }; return out }

    @Test fun signedEntryVerifiesAndRoundTripsThroughItsText() {
        val e = chain(1).single()
        assertTrue(e.verify(pub)); assertEquals(16, e.hash.length); assertEquals(e, Entry.parse(e.text())); assertEquals(e, Entry.fromLine(e.toLine()))
        assertFalse(e.verify(AgentFixtures.key("agent2").signer.publicKeyBase64))
        assertFalse(e.copy(cash = 1).verify(pub))
        assertFalse(e.toLine().contains('\n'))
        assertContains(e.text(), "prev=0"); assertContains(e.text(), "fp=$fp")
        assertFalse(Entry.parse(e.text().replace("cash=5000", "cash=4000"))!!.verify(pub))      // parsing is structural: the hash and the signature catch the change
    }

    @Test fun aGoodChainIsOkAndNamesTheNextHash() {
        val c = chain(4)
        val r = assertIs<ChainResult.Ok>(SalesLedger.chain(c, pub))
        assertEquals(4, r.count); assertEquals(c.last().hash, r.last); assertEquals(5L to c.last().hash, SalesLedger.next(c)); assertEquals(1L to "0", SalesLedger.next(emptyList()))
        assertEquals(ChainResult.Ok(0, "0"), SalesLedger.chain(emptyList(), pub))
    }

    @Test fun aMissingEntryIsAGapAtTheExpectedSeq() {
        val c = chain(4)
        assertEquals(ChainResult.Gap(2), SalesLedger.chain(listOf(c[0], c[2], c[3]), pub))
        assertEquals(ChainResult.Gap(1), SalesLedger.chain(c.drop(1), pub))
    }

    @Test fun aWrongPrevOrAlteredContentDivergesAtThatSeq() {
        val c = chain(4)
        val badPrev = sale(3, "00000000deadbeef")                       // signed and hashed correctly, but does not follow entry 2
        assertEquals(ChainResult.Diverged(3), SalesLedger.chain(listOf(c[0], c[1], badPrev, c[3]), pub))
        assertEquals(ChainResult.Diverged(2), SalesLedger.chain(listOf(c[0], c[1].copy(cash = 1), c[2]), pub))      // content changed without re-hashing
        assertEquals(ChainResult.Diverged(1), SalesLedger.chain(listOf(c[0], c[0]), pub))                            // duplicate seq
    }

    @Test fun anEntryNotSignedByTheAgentKeyIsABadSignature() {
        val c = chain(3)
        val forged = c[1].copy(cash = 1).let { it.copy(hash = it.computeHash()) }          // right hash for the new content, signature of the old text
        assertEquals(ChainResult.BadSignature(2), SalesLedger.chain(listOf(c[0], forged, c[2]), pub))
        assertEquals(ChainResult.BadSignature(1), SalesLedger.chain(c, AgentFixtures.key("agent2").signer.publicKeyBase64))
        assertFailsWith<IllegalArgumentException> { c[0].sign(AgentFixtures.key("agent2").signer) }
    }

    @Test fun storeAppendsInOrderAndRefusesGapsWrongPrevAndOtherAgents() {
        val s = MemoryLedgerStore(); val c = chain(3)
        s.append(c[0]); s.append(c[1])
        assertFailsWith<IllegalArgumentException> { s.append(c[0]) }                    // seq already used
        assertFailsWith<IllegalArgumentException> { s.append(sale(4, c[1].hash)) }      // hole
        assertFailsWith<IllegalArgumentException> { s.append(sale(3, "0000000000000000")) }
        assertFailsWith<IllegalArgumentException> { s.append(c[2].copy(cash = 1)) }     // altered
        assertFailsWith<IllegalArgumentException> { s.append(sale(3, c[1].hash).copy(sig = "")) }
        s.append(c[2]); assertEquals(c, s.all())
        assertFailsWith<IllegalArgumentException> { MemoryLedgerStore().append(c[1]) }  // an empty ledger starts at 1 / prev 0
    }

    @Test fun balanceCountsSalesMinusRefundsMinusConfirmedRemittancesOnly() {
        val s1 = sale(1, "0", price = 5000); val s2 = sale(2, s1.hash, "cle-production|30", price = 2000, cash = 1500)
        val refund = Entry(3, t0 + 3, Kind.REFUND, agent.keyId, device = "ABCD", ref = 2, cash = 1500, prev = s2.hash).sign(agent)
        val remit = Entry(4, t0 + 4, Kind.REMIT, agent.keyId, cash = 3000, prev = refund.hash).sign(agent)
        val note = Entry(5, t0 + 5, Kind.NOTE, agent.keyId, note = "geste commercial sur la vente 2", prev = remit.hash).sign(agent)
        val all = listOf(s1, s2, refund, remit, note)
        assertIs<ChainResult.Ok>(SalesLedger.chain(all, pub))
        assertEquals(5000L, SalesLedger.balance(all, 0))             // the agent's own REMIT counts for nothing until confirmed
        assertEquals(2000L, SalesLedger.balance(all, 3000))
    }

    @Test fun w5ItemsAreAccepted() {
        val voucher = sale(1, "0", item = "bon|jetons-60|SN000123", price = 3000)
        val order = sale(2, voucher.hash, item = "commande|C-4F2A-9B1C|5000", price = 5000)
        assertIs<ChainResult.Ok>(SalesLedger.chain(listOf(voucher, order), pub))
        assertFailsWith<IllegalArgumentException> { sale(1, "0", item = "n'importe quoi") }
    }

    @Test fun incompleteEntriesAreRefusedAtConstruction() {
        assertFailsWith<IllegalArgumentException> { Entry(1, t0, Kind.SALE, agent.keyId) }
        assertFailsWith<IllegalArgumentException> { Entry(1, t0, Kind.NOTE, agent.keyId, note = " ") }
        assertFailsWith<IllegalArgumentException> { Entry(1, t0, Kind.REMIT, agent.keyId, cash = 0) }
        assertFailsWith<IllegalArgumentException> { Entry(0, t0, Kind.NOTE, agent.keyId, note = "x") }
        assertFailsWith<IllegalArgumentException> { Entry(1, t0, Kind.NOTE, agent.keyId, note = "a\nb") }
    }

    @Test fun ledgerOffersNoWayToRemoveOrRewriteAnEntry() {
        val src = File("src/main/kotlin/castbridge/core/sales/SalesLedger.kt").readText()
        assertFalse(Regex("fun (delete|remove|rewrite|clear|update)").containsMatchIn(src))
        assertEquals(setOf("append", "all"), LedgerStore::class.java.declaredMethods.map { it.name }.toSet())
    }
}
