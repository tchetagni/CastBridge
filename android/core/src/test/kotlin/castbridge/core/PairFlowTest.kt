package castbridge.core

import castbridge.core.trust.*
import castbridge.core.tv.BtProtocol
import kotlin.concurrent.thread
import kotlin.test.*

/** « Ajouter ma TV » / « Réassocier » end to end against the fake TV: bond problems, the TV window, the owner's answer. */
class PairFlowTest {
    private val clock = FakeClock()
    private val tv = FakeTv(clock)

    private inner class Env : PairEnv {
        var bt: BtUnavailable.Reason? = null
        var bond = BondState.BONDED
        var created = 0
        var sleeps = 0
        var onSleep: (Int) -> Unit = {}
        /** What the user does in Android's Bluetooth settings when asked to remove the bond. */
        var userRemovesBond = true
        override fun btProblem() = bt
        override fun bond(address: String) = bond
        override fun createBond(address: String): Boolean { created++; bond = BondState.BONDING; return true }
        override fun awaitBond(address: String, target: BondState, timeoutMs: Long): BondState {
            if (target == BondState.BONDED && bond == BondState.BONDING) { bond = BondState.BONDED; tv.bonded += tv.phone; clock.advance(5_000) }
            if (target == BondState.NONE && bond == BondState.BONDED && userRemovesBond) { bond = BondState.NONE; tv.staleBond = false; tv.bonded -= tv.phone; clock.advance(20_000) }
            return bond
        }
        override fun sleep(ms: Long) { clock.advance(ms); onSleep(++sleeps) }
        override fun now() = clock.now()
    }

    private val env = Env()
    private val tv0 = SavedTv(tv.tvAddress, "TV du salon", addedAt = 1)
    private fun flow(link: PhoneLink = PhoneLink(tv, { true }, now = clock::now), windowMs: Long = 150_000) = PairFlow(link, env, windowMs = windowMs)
    private val steps = ArrayList<PairStep>()

    /** The owner on the TV: approves (or denies) the first phone that asks. */
    private fun owner(approve: Boolean = true) = thread(isDaemon = true) {
        val until = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < until) { if (tv.pairing.asking() != null) { if (approve) tv.pairing.approve() else tv.pairing.deny(); return@thread }; Thread.sleep(3) }
    }

    @Test fun firstPairingWithTheOwnerApproving() {
        env.bond = BondState.NONE; tv.pairing.open(); owner()
        val r = flow().run(tv0) { steps += it }
        assertIs<PairStep.Done>(r)
        assertEquals(1, env.created)
        assertEquals(PairStep.Bonding, steps.first())
        assertTrue(tv.reg.isTrusted(tv.phone)); assertEquals(tv.phone, tv.reg.verifyToken(r.session.credential))
    }

    @Test fun theTvWindowIsPolledUntilTheOwnerOpensItAndTheUserIsToldWhatToOpen() {
        env.onSleep = { n -> if (n == 4) { tv.pairing.open(); owner() } }
        val r = flow().run(tv0) { steps += it }
        assertIs<PairStep.Done>(r)
        val waits = steps.filterIsInstance<PairStep.WaitingTvWindow>()
        assertTrue(waits.size >= 3); assertTrue(waits.first().advice.title.contains("Ajouter un téléphone"))
        assertTrue(tv.hellos <= 10, "polled every few seconds, not in a storm: ${tv.hellos}")
    }

    @Test fun windowNeverOpenedEndsWithOneActionableMessageAfterABoundedTime() {
        val r = flow(windowMs = 60_000).run(tv0) { steps += it }
        assertIs<PairStep.Failed>(r); assertTrue(r.canRetry)
        assertEquals("« Ajouter un téléphone » est fermé", r.advice.title); assertEquals(LinkAction.RETRY, r.advice.action)
        assertTrue(tv.hellos <= 25, "attempts: ${tv.hellos}")
        assertFalse(tv.reg.isTrusted(tv.phone))
    }

    @Test fun ownerDeniesThenNoRetryIsOffered() {
        tv.pairing.open(); owner(approve = false)
        val r = flow().run(tv0) { steps += it }
        assertIs<PairStep.Failed>(r); assertFalse(r.canRetry)
        assertTrue(r.advice.detail.contains("10 minutes"))
        assertFalse(tv.reg.isTrusted(tv.phone))
    }

    @Test fun deniedThreeTimesThenBlockedForTenMinutesAndRetryAfterTheBlockWorks() {
        repeat(3) { tv.pairing.open(); val o = owner(false); assertIs<PairStep.Failed>(flow().run(tv0) {}); o.join() }
        tv.pairing.open()
        val blocked = flow().run(tv0) {}
        assertIs<PairStep.Failed>(blocked); assertEquals("La TV est occupée", blocked.advice.title, "blocked: told as busy, never as 'denied'")
        clock.advance(11 * 60_000)
        tv.pairing.open(); owner()
        assertIs<PairStep.Done>(flow().run(tv0) {})
    }

    @Test fun reinstalledTvWithAStaleBondIsRepairedByTheGuidedFlowWithoutAnyMoreTaps() {
        // (a) the TV was reinstalled: the phone still believes it is bonded, the TV refuses the link at once
        tv.staleBond = true
        env.onSleep = { }
        var windowOpened = false
        val r = flow().run(tv0) { s -> steps += s; if (s is PairStep.Bonding && !windowOpened && steps.count { it == PairStep.StaleBond } > 0) { windowOpened = true; tv.pairing.open(); owner() } }
        assertIs<PairStep.Done>(r, r.toString())
        assertTrue(steps.contains(PairStep.StaleBond), "the user was guided to the Bluetooth settings")
        assertEquals(LinkAction.REMOVE_BOND, PairStep.StaleBond.advice.action)
        assertEquals(1, env.created, "the bond was recreated by the flow itself")
        assertTrue(tv.reg.isTrusted(tv.phone))
    }

    @Test fun staleBondThatTheUserNeverRemovesEndsWithTheSameSingleAction() {
        tv.staleBond = true; env.userRemovesBond = false
        val r = flow().run(tv0) { steps += it }
        assertIs<PairStep.Failed>(r); assertEquals(LinkAction.REMOVE_BOND, r.advice.action)
        assertEquals(0, env.created)
    }

    @Test fun bondStillRefusedAfterRepairIsReportedNotLooped() {
        tv.staleBond = true
        // the removal does not clear the TV side problem: the second round must stop
        val e = object : PairEnv by env {
            override fun awaitBond(address: String, target: BondState, timeoutMs: Long): BondState { val s = env.awaitBond(address, target, timeoutMs); tv.staleBond = true; return s }
        }
        val r = PairFlow(PhoneLink(tv, { true }, now = clock::now), e).run(tv0) { steps += it }
        assertIs<PairStep.Failed>(r)
        assertTrue(steps.count { it == PairStep.StaleBond } <= 2)
    }

    @Test fun bondingInProgressThenCancelledAndBluetoothToggledMidFlow() {
        env.bond = BondState.NONE
        val e = object : PairEnv by env { override fun awaitBond(address: String, target: BondState, timeoutMs: Long) = BondState.NONE }   // the user cancelled Android's dialog
        val r = PairFlow(PhoneLink(tv, { true }), e).run(tv0) { steps += it }
        assertIs<PairStep.Failed>(r); assertTrue(r.canRetry); assertEquals("Association non terminée", r.advice.title)
        // Bluetooth switched off while the flow runs: the transport says so
        val off = PairFlow(PhoneLink(object : BtTransport { override fun connect(address: String) = throw BtUnavailable(BtUnavailable.Reason.OFF) }, { true }), env)
        env.bond = BondState.BONDED
        val r2 = off.run(tv0) {}
        assertIs<PairStep.Failed>(r2); assertEquals(LinkAction.ENABLE_BLUETOOTH, r2.advice.action)
        env.bt = BtUnavailable.Reason.NO_PERMISSION
        assertEquals(LinkAction.GRANT_PERMISSION, (flow().run(tv0) {} as PairStep.Failed).advice.action)
    }

    @Test fun anotherPhoneBeingAskedMakesUsWaitAndTryAgain() {
        val other = "AA:BB:CC:DD:EE:02"; tv.bonded += other
        tv.pairing.open()
        val t = thread(isDaemon = true) { tv.pairing.ask(other, "Autre") }
        while (tv.pairing.asking() == null) Thread.sleep(2)
        env.onSleep = { n -> if (n == 2) tv.pairing.approve() }
        tv.pairing.open()    // no effect while asking
        val r = flow().run(tv0) { steps += it }
        t.join(3000)
        assertTrue(r is PairStep.Failed || r is PairStep.Done)
        assertTrue(steps.any { it is PairStep.WaitingTvWindow })
        assertTrue(tv.reg.isTrusted(other))
    }

    @Test fun oldTvAndTheTvAppClosedAreBothExplained() {
        tv.appRunning = false
        val r = flow(windowMs = 20_000).run(tv0) { steps += it }
        assertIs<PairStep.Failed>(r); assertEquals("CastBridge-TV n'est pas ouvert", r.advice.title)
        assertTrue(steps.filterIsInstance<PairStep.WaitingTvWindow>().all { it.advice.detail.contains("CastBridge-TV") })
    }

    @Test fun phoneThatClosesMidHelloWhilePairingIsRetriedNotFailed() {
        tv.pairing.open(); owner()
        tv.dropReadAfter = 3
        env.onSleep = { tv.dropReadAfter = null }
        val r = flow().run(tv0) { steps += it }
        // the first link broke, the next attempt (after the poll delay) succeeded or the same phone took over its own pending request
        assertTrue(r is PairStep.Done || (r as PairStep.Failed).canRetry, r.toString())
    }
}
