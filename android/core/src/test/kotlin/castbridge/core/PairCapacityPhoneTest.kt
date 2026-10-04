package castbridge.core

import castbridge.core.trust.*
import castbridge.core.tv.BtProtocol
import castbridge.core.tv.HelloInfo
import castbridge.core.tv.LinkInfo
import castbridge.core.ux.PairStatusLine
import castbridge.core.ux.SignalLevel
import kotlin.concurrent.thread
import kotlin.test.*

/** Le téléphone face à une TV qui a déjà 8 téléphones : message clair, attente bornée, réussite, annulation, délai, retrait, compatibilité. */
class PairCapacityPhoneTest {
    private val clock = FakeClock()
    private val tv = FakeTv(clock)
    private fun other(i: Int) = "AA:BB:CC:DD:00:%02X".format(i)
    private val capacity = tv.useCapacity()
    private val tv0 = SavedTv(tv.tvAddress, "TV du salon", addedAt = 1)
    private val steps = ArrayList<PairStep>()

    private fun fullTv() { repeat(8) { clock.advance(1_000); tv.reg.trust(other(it), "Tel $it") }; tv.pairing.open() }

    private inner class Env(val onSleep: (Int) -> Unit = {}) : PairEnv {
        var sleeps = 0
        override fun btProblem() = null
        override fun bond(address: String) = BondState.BONDED
        override fun createBond(address: String) = true
        override fun awaitBond(address: String, target: BondState, timeoutMs: Long) = BondState.BONDED
        override fun sleep(ms: Long) { clock.advance(ms); onSleep(++sleeps) }
        override fun now() = clock.now()
    }
    private fun run(env: Env) = PairFlow(PhoneLink(tv, { true }, now = clock::now), env).run(tv0) { steps += it }

    @Test fun theNinthPhoneSeesTheMessageThenWaitsThenIsAddedWhenTheOwnerReplacesOne() {
        fullTv()
        val r = run(Env { n -> if (n == 3) assertIs<PairCapacityFlow.Choice.Replaced>(capacity.choose(other(0))) })
        val done = assertIs<PairStep.Done>(r)
        val waits = steps.filterIsInstance<PairStep.WaitingReplace>()
        assertTrue(waits.size >= 2, "a live « en attente » state, not a frozen screen: $steps")
        waits.forEach {
            assertEquals("En attente de la TV…", it.advice.title)
            assertTrue("Cette TV a déjà 8 téléphones. Sur la TV, choisissez le téléphone à retirer, puis réessayez." in it.advice.detail, it.advice.detail)
        }
        assertEquals(8, tv.reg.list().size); assertFalse(tv.reg.isTrusted(other(0))); assertEquals(tv.phone, tv.reg.verifyToken(done.session.credential))
        assertNull(steps.filterIsInstance<PairStep.Failed>().firstOrNull())
    }

    @Test fun theOwnerCancelsAndThePhoneGetsTheReason() {
        fullTv()
        val r = run(Env { n -> if (n == 2) capacity.cancel() })
        val f = assertIs<PairStep.Failed>(r)
        assertEquals(LinkText.refused(BtProtocol.ERR_FULL_CANCELED), f.advice)
        assertTrue(f.canRetry); assertFalse(tv.reg.isTrusted(tv.phone)); assertEquals(8, tv.reg.list().size)
    }

    @Test fun nobodyAnswersOnTheTvThePhoneStopsAfterABoundedTimeWithTheReason() {
        fullTv()
        val r = run(Env())
        val f = assertIs<PairStep.Failed>(r)
        assertTrue(f.advice == LinkText.refused(BtProtocol.ERR_FULL_TIMEOUT) || f.advice == LinkText.refused(BtProtocol.ERR_FULL), "${f.advice}")
        assertTrue(f.canRetry); assertFalse(tv.reg.isTrusted(tv.phone))
        assertTrue(tv.hellos in 5..80, "polling is steady and bounded: ${tv.hellos}")
        assertTrue(clock.now() < 1_000_000L + 300_000, "never an endless wait")
    }

    @Test fun aTvWithoutTheFlowStillRefusesTheNinthWithErrFullAndNothingIsAdded() {
        tv.capacity = null; tv.restartApp()
        repeat(8) { tv.reg.trust(other(it), "Tel $it") }
        tv.pairing.open()
        val owner = thread(isDaemon = true) { val until = System.currentTimeMillis() + 5_000; while (System.currentTimeMillis() < until) { if (tv.pairing.asking() != null) { tv.pairing.approve(); return@thread }; Thread.sleep(3) } }
        val reply = tv.handler.handle(tv.phone, "Galaxy", requestTrust = true)
        owner.join()
        assertEquals(BtProtocol.ERR_FULL, assertIs<castbridge.core.tv.HelloReply.Err>(reply).code)
        assertEquals(8, tv.reg.list().size); assertFalse(tv.reg.isTrusted(tv.phone))
    }

    @Test fun aPhoneThatDoesNotAskForTrustIsNotAffectedByTheCap() {
        fullTv()
        val reply = tv.handler.handle(tv.phone, "Galaxy", requestTrust = false)
        assertEquals(BtProtocol.ERR_UNTRUSTED, assertIs<castbridge.core.tv.HelloReply.Err>(reply).code)
        assertEquals(PairCapacityFlow.State.Idle, capacity.state(), "only « Ajouter ma TV » opens a replacement request")
    }

    @Test fun aTrustedPhoneKeepsConnectingWhenTheTvIsFull() {
        repeat(7) { tv.reg.trust(other(it), "Tel $it") }
        tv.pairing.open()
        val owner = thread(isDaemon = true) { val until = System.currentTimeMillis() + 5_000; while (System.currentTimeMillis() < until) { if (tv.pairing.asking() != null) { tv.pairing.approve(); return@thread }; Thread.sleep(3) } }
        assertIs<PairStep.Done>(run(Env())); owner.join()
        assertEquals(8, tv.reg.list().size)
        val again = PhoneLink(tv, { true }, now = clock::now).connect(tv0)
        assertIs<PhoneLink.Result.Connected>(again)
    }

    @Test fun theRemovedPhoneIsToldTheTvRemovedItNotAStaleState() {
        val p = PhoneLink(tv, { true }, now = clock::now)
        tv.pairing.open(); val owner = thread(isDaemon = true) { val until = System.currentTimeMillis() + 5_000; while (System.currentTimeMillis() < until) { if (tv.pairing.asking() != null) { tv.pairing.approve(); return@thread }; Thread.sleep(3) } }
        val first = assertIs<PhoneLink.Result.Connected>(p.connect(tv0.copy(), requestTrust = true)); owner.join()
        tv.reg.revoke(tv.phone)
        val r = assertIs<PhoneLink.Result.Refused>(p.connect(first.session.tv))
        assertEquals(BtProtocol.HINT_SAME_INSTALL, r.hint)
        val a = LinkText.refused(r.code, r.hint)
        assertEquals("Cette TV vous a retiré", a.title); assertTrue("Ajoutez-la à nouveau" in a.detail); assertEquals(LinkAction.REASSOCIATE, a.action)
    }

    @Test fun oldPhoneUnknownCodeStillReadsAsAReadableRefusal() {
        // an old phone has no wording for 14/15/16: it falls on the generic text and on BtProtocol.describe, both readable
        for (c in listOf(BtProtocol.ERR_FULL, BtProtocol.ERR_FULL_CANCELED, BtProtocol.ERR_FULL_TIMEOUT)) {
            assertFalse(BtProtocol.describe(c).startsWith("erreur TV"), "describe($c)")
            val msg = LinkText.failure(BtProtocol.Refused(c))
            assertTrue(msg.isNotBlank() && "code $c" !in msg, msg)
        }
        assertTrue("8 téléphones" in LinkText.failure(BtProtocol.Refused(BtProtocol.ERR_FULL)))
    }

    @Test fun helloCarriesTheCapAdditivelyAndAnOldTvHasNone() {
        val info = HelloInfo("TV", "1", null, "cbk_" + "a".repeat(64), 3600, LinkInfo(8765, listOf("192.168.1.2")), "0123456789abcdef0123456789abcdef", maxPhones = TrustRegistry.MAX_PHONES)
        val text = info.encode()
        assertTrue("maxphones=8" in text)
        assertEquals(8, HelloInfo.decode(text)!!.maxPhones)
        val old = text.lines().filterNot { it.startsWith("maxphones=") }.joinToString("\n")
        assertNull(HelloInfo.decode(old)!!.maxPhones, "an old TV advertises nothing: the phone behaves as before")
        assertEquals("TV", HelloInfo.decode(old)!!.tvName)
        // an old phone ignores the unknown key: every other field decodes the same
        val kv = text.lines().filterNot { it.startsWith("maxphones=") }
        assertEquals(old.lines(), kv)
    }

    @Test fun theHelloOfAPhoneWithRoomAdvertisesTheCap() {
        tv.pairing.open()
        val owner = thread(isDaemon = true) { val until = System.currentTimeMillis() + 5_000; while (System.currentTimeMillis() < until) { if (tv.pairing.asking() != null) { tv.pairing.approve(); return@thread }; Thread.sleep(3) } }
        val r = PhoneLink(tv, { true }, now = clock::now).connect(tv0, requestTrust = true); owner.join()
        assertEquals(8, assertIs<PhoneLink.Result.Connected>(r).session.info.maxPhones)
    }

    @Test fun statusLineTable() {
        val cases = listOf(
            PairStep.WaitingReplace(90) to (SignalLevel.ORANGE to "En attente de la TV…"),
            PairStep.WaitingReplace(5) to (SignalLevel.ORANGE to "Cette TV a déjà 8 téléphones. Sur la TV, choisissez le téléphone à retirer, puis réessayez."),
            PairStep.Failed(LinkText.refused(BtProtocol.ERR_FULL), true) to (SignalLevel.RED to "Cette TV a déjà 8 téléphones"),
            PairStep.Failed(LinkText.refused(BtProtocol.ERR_FULL_CANCELED), true) to (SignalLevel.RED to "annulé"),
            PairStep.Failed(LinkText.refused(BtProtocol.ERR_FULL_TIMEOUT), true) to (SignalLevel.RED to "2 minutes"),
            PairStep.Failed(LinkText.untrustedAdvice(BtProtocol.HINT_SAME_INSTALL), true) to (SignalLevel.RED to "Cette TV vous a retiré"),
            PairStep.WaitingOwner to (SignalLevel.ORANGE to "Validez sur la TV"),
        )
        for ((step, exp) in cases) {
            val line = PairStatusLine.of(step)
            assertEquals(exp.first, line.level, "$step")
            assertTrue(exp.second in line.text, "$step -> ${line.text}")
            assertEquals(line.text, PairStatusLine.forScreen(step)); assertEquals(line.text, PairStatusLine.forNotification(step), "one text for screen and notification")
        }
        val done = PairStatusLine.of(PairStep.Done(LinkSession(tv0, castbridge.core.tv.LinkPlanner.Route.Bluetooth, "x", 1, HelloInfo("TV", "1", null, "t", 1, LinkInfo(8765, emptyList())))))
        assertEquals(SignalLevel.GREEN, done.level); assertTrue("TV du salon est ajoutée" in done.text || "TV est ajoutée" in done.text, done.text)
    }

    @Test fun everyNewCodeHasItsOwnWording() {
        for (c in listOf(BtProtocol.ERR_FULL, BtProtocol.ERR_FULL_CANCELED, BtProtocol.ERR_FULL_TIMEOUT)) {
            assertTrue(c in LinkText.explicitCodes, "code $c")
            assertNotEquals(LinkText.refused(999).title, LinkText.refused(c).title)
        }
    }
}
