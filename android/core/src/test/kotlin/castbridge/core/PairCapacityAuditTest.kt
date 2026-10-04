package castbridge.core

import castbridge.core.trust.*
import castbridge.core.tv.BtProtocol
import castbridge.core.tv.HelloReply
import kotlin.concurrent.thread
import kotlin.test.*

/** Correctifs de l'audit Opus du plafond de 8 téléphones (I1 blocage, I2 demande remplacée, I3 homonymes, M3, M4, M5, course Decision.FULL). */
class PairCapacityAuditTest {
    private val clock = FakeClock()
    private val tv = FakeTv(clock)
    private val capacity = tv.useCapacity()
    private fun other(i: Int) = "AA:BB:CC:DD:00:%02X".format(i)
    private fun fill(n: Int = 8) = repeat(n) { clock.advance(1_000); tv.reg.trust(other(it), "Tel $it") }
    private fun err(r: HelloReply) = assertIs<HelloReply.Err>(r).code

    private class Flaky : TrustPersistence {
        var text: String? = null; var fail = false
        override fun load() = text
        override fun save(text: String) { if (fail) throw java.io.IOException("disque"); this.text = text }
    }

    // ---- I1 : un téléphone bloqué ne rouvre pas l'écran de remplacement
    @Test fun aBlockedPhoneFacingAFullTvIsToldBusyAndOpensNoRequest() {
        fill(); tv.pairing.open()
        repeat(3) { tv.pairing.recordDenial(tv.phone) }
        assertTrue(tv.pairing.isBlocked(tv.phone))
        assertEquals(BtProtocol.ERR_BUSY, err(tv.handler.handle(tv.phone, "Galaxy", true)))
        assertEquals(PairCapacityFlow.State.Idle, capacity.state(), "the owner's screen is not reopened for a blocked phone")
    }

    @Test fun theBlockEndsAfterTenMinutes() {
        repeat(3) { tv.pairing.recordDenial(tv.phone) }
        assertTrue(tv.pairing.isBlocked(tv.phone))
        clock.advance(10 * 60_000 + 1)
        assertFalse(tv.pairing.isBlocked(tv.phone))
    }

    @Test fun repeatedCancellationsBlockThePhone() {
        fill(); tv.pairing.open()
        repeat(3) {
            assertEquals(PairCapacityFlow.Answer.Pending, capacity.ask(tv.phone, "Galaxy", true))
            assertTrue(capacity.cancel())
            assertEquals(PairCapacityFlow.Answer.Cancelled, capacity.ask(tv.phone, "Galaxy", true))
        }
        assertTrue(tv.pairing.isBlocked(tv.phone), "three cancellations = three refusals")
        assertEquals(BtProtocol.ERR_BUSY, err(tv.handler.handle(tv.phone, "Galaxy", true)))
        assertEquals(PairCapacityFlow.State.Idle, capacity.state())
    }

    // ---- I2 : le propriétaire n'admet que le téléphone affiché
    @Test fun chooseForAReplacedRequestAdmitsNobody() {
        fill()
        capacity.ask(other(50), "Alice", true)
        clock.advance(121_000)
        assertEquals(PairCapacityFlow.Answer.Pending, capacity.ask(other(51), "Mallory", true), "Alice expired, Mallory opened a new request")
        assertEquals(PairCapacityFlow.Choice.NotPending, capacity.choose(other(0), expectedNew = other(50)))
        assertFalse(tv.reg.isTrusted(other(51))); assertTrue(tv.reg.isTrusted(other(0))); assertEquals(8, tv.reg.list().size)
        assertIs<PairCapacityFlow.Choice.Replaced>(capacity.choose(other(0), expectedNew = other(51)))
        assertTrue(tv.reg.isTrusted(other(51)))
    }

    // ---- I3 : homonymes
    @Test fun addressSuffixInTitleRowsAndConfirmation() {
        assertEquals("…:3F:A1", PhonesTexts.shortAddress("aa:bb:cc:dd:3f:a1"))
        assertTrue("Galaxy A12 (…:3F:A1)" in PhonesTexts.replaceTitle("Galaxy A12", "AA:BB:CC:DD:3F:A1"))
        assertTrue("(…:00:02)" in PhonesTexts.confirmRemoveTitle("Tel 2", other(2)))
        val t = PhonesTexts.confirmReplaceText("Tel 2", other(2), "Galaxy A12", "AA:BB:CC:DD:3F:A1")
        assertTrue("Tel 2 (…:00:02)" in t && "Galaxy A12 (…:3F:A1)" in t, t)
        fill(); val row = PhoneRoster.build(tv.reg.list(), emptySet(), clock.now()).rows.first()
        assertTrue("(…:00:" in row.label && "(…:00:" in row.description)
    }

    @Test fun sameNameIsFlaggedCaseInsensitively() {
        fill()
        capacity.ask(other(50), "TEL 3", true)
        assertTrue(assertIs<PairCapacityFlow.State.AwaitingRemoval>(capacity.state()).sameName)
        capacity.cancel()
        capacity.ask(other(50), "TEL 3", true)   // consumes the told outcome
        capacity.ask(other(51), "Galaxy", true)
        assertTrue(PhonesTexts.SAME_NAME_WARNING.startsWith("Même nom"))
        assertTrue(PhoneRoster.sameName("tel 3", tv.reg.list())); assertFalse(PhoneRoster.sameName("Galaxy", tv.reg.list()))
    }

    // ---- M3 : plus de 8 conservés
    @Test fun aRegistryHoldingMoreThanEightIsShownAsToRemoveAndReplaceStillWorks() {
        val p = MemoryTrustPersistence((0 until 12).joinToString("\n", postfix = "\n") { "P\t${other(it)}\t1\t${it + 1}\tTel $it" })
        val reg = TrustRegistry(p, clock::now)
        val v = PhoneRoster.build(reg.list(), emptySet(), clock.now())
        assertEquals(4, v.overBy); assertEquals("12 / 8", v.counter)
        assertTrue("retirez-en 4" in PhonesTexts.overText(v.overBy))
        assertIs<TrustResult.Added>(reg.replace(other(0), other(50), "Nouveau"))
        assertEquals(12, reg.list().size, "a replace never grows the list")
        assertIs<TrustResult.Full>(reg.trust(other(60), "Encore"))
    }

    // ---- M4 : la demande reste quand une place se libère ailleurs
    @Test fun theRequestIsKeptWhenARoomAppearsAndThePhoneTakesTheNormalPath() {
        fill()
        capacity.ask(other(50), "Alice", true)
        tv.reg.revoke(other(0))
        assertIs<PairCapacityFlow.State.AwaitingRemoval>(capacity.state(), "not erased silently")
        assertEquals(PairCapacityFlow.Answer.Room, capacity.ask(other(50), "Alice", true))
    }

    // ---- M5 : trust() dit quand l'écriture échoue
    @Test fun trustReportsAFailedWriteAndChangesNothing() {
        val d = Flaky(); val reg = TrustRegistry(d, clock::now)
        reg.trust(other(1), "Un")
        d.fail = true
        assertEquals(TrustResult.WriteFailed, reg.trust(other(2), "Deux"))
        assertFalse(reg.isTrusted(other(2)))
        assertEquals(TrustResult.WriteFailed, reg.trust(other(1), "Renommé"))
        assertEquals("Un", reg.get(other(1))!!.name)
        d.fail = false
        assertIs<TrustResult.Added>(reg.trust(other(2), "Deux"))
    }

    @Test fun anApprovalWhoseWriteFailsIsNotSaidApproved() {
        val d = Flaky(); d.fail = true
        tv.persistence = MemoryTrustPersistence()
        val reg = TrustRegistry(d, clock::now); val pairing = PairingSession(reg, clock::now)
        val h = HelloHandler(reg, pairing, { true }, { "TV" }, "1", { null }, { castbridge.core.tv.LinkInfo(8765, emptyList()) })
        pairing.open()
        val owner = thread(isDaemon = true) { val until = System.currentTimeMillis() + 5_000; while (System.currentTimeMillis() < until) { if (pairing.asking() != null) { pairing.approve(); return@thread }; Thread.sleep(3) } }
        assertEquals(BtProtocol.ERR_IO, err(h.handle(tv.phone, "Galaxy", true))); owner.join()
        assertFalse(reg.isTrusted(tv.phone))
    }

    // ---- course de deux approbations : Decision.FULL
    @Test fun anApprovalThatLosesTheRaceForTheLastSeatBecomesAReplacementRequest() {
        repeat(7) { tv.reg.trust(other(it), "Tel $it") }
        tv.pairing.open()
        var reply: HelloReply? = null
        val t = thread(isDaemon = true) { reply = tv.handler.handle(tv.phone, "Galaxy", true) }
        val until = System.currentTimeMillis() + 5_000
        while (tv.pairing.asking() == null && System.currentTimeMillis() < until) Thread.sleep(3)
        assertNotNull(tv.pairing.asking())
        tv.reg.trust(other(20), "Arrivé avant")        // the last seat goes to somebody else
        tv.pairing.approve(); t.join(5_000)
        assertEquals(BtProtocol.ERR_FULL, err(reply!!))
        assertFalse(tv.reg.isTrusted(tv.phone)); assertEquals(8, tv.reg.list().size)
        val st = assertIs<PairCapacityFlow.State.AwaitingRemoval>(capacity.state())
        assertEquals(tv.phone, st.request.address)
        assertIs<PairCapacityFlow.Choice.Replaced>(capacity.choose(other(0), tv.phone))
    }
}
