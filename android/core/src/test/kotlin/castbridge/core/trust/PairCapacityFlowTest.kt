package castbridge.core.trust

import castbridge.core.FakeClock
import kotlin.test.*

/** Le 9e téléphone : le propriétaire choisit lequel retirer, ou annule ; délai de 2 minutes ; remplacement atomique. */
class PairCapacityFlowTest {
    private val clock = FakeClock()
    private fun addr(i: Int) = "AA:BB:CC:DD:EE:%02X".format(i)

    private class Flaky : TrustPersistence {
        var text: String? = null; var fail = false
        override fun load() = text
        override fun save(text: String) { if (fail) throw java.io.IOException("disque"); this.text = text }
    }

    private val disk = Flaky()
    private val reg = TrustRegistry(disk, clock::now)
    private val events = ArrayList<PairCapacityFlow.Event>()
    private val flow = PairCapacityFlow(reg, clock::now, timeoutMs = 120_000).also { f -> f.addListener { events += it } }
    private fun fill(n: Int) = repeat(n) { clock.advance(1_000); reg.trust(addr(it), "Tel $it") }

    @Test fun withRoomThePhoneGoesTheNormalWay() {
        fill(7)
        assertEquals(PairCapacityFlow.Answer.Room, flow.ask(addr(50), "Nouveau", windowOpen = true))
        assertEquals(PairCapacityFlow.State.Idle, flow.state())
    }

    @Test fun anAlreadyTrustedPhoneIsTrustedEvenWhenFull() {
        fill(8)
        assertEquals(PairCapacityFlow.Answer.Trusted, flow.ask(addr(3), "Tel 3", windowOpen = true))
    }

    @Test fun theNinthPhoneWaitsForTheOwnerAndTheRosterIsShown() {
        fill(8)
        assertEquals(PairCapacityFlow.Answer.Pending, flow.ask(addr(50), "Nouveau", windowOpen = true))
        val st = assertIs<PairCapacityFlow.State.AwaitingRemoval>(flow.state())
        assertEquals(addr(50), st.request.address); assertEquals("Nouveau", st.request.name)
        assertEquals(clock.now() + 120_000, st.request.deadline)
        assertEquals(8, st.roster.rows.size); assertEquals(addr(0), st.roster.suggestedAddress, "the least recently seen one is only suggested")
        assertEquals(8, reg.list().size, "nothing is removed or added by itself")
        assertEquals(PairCapacityFlow.Kind.REQUESTED, events.single().kind)
    }

    @Test fun noRequestIsOpenedWhileTheOwnerHasNotOpenedTheWindow() {
        fill(8)
        assertEquals(PairCapacityFlow.Answer.Room, flow.ask(addr(50), "Nouveau", windowOpen = false))   // the normal path then says "fermé"
        assertEquals(PairCapacityFlow.State.Idle, flow.state())
    }

    @Test fun anotherPhoneIsToldToWaitAndTheSamePhoneKeepsItsDeadline() {
        fill(8)
        flow.ask(addr(50), "Nouveau", true)
        val deadline = (flow.state() as PairCapacityFlow.State.AwaitingRemoval).request.deadline
        clock.advance(30_000)
        assertEquals(PairCapacityFlow.Answer.Busy, flow.ask(addr(51), "Autre", true))
        assertEquals(PairCapacityFlow.Answer.Pending, flow.ask(addr(50), "Nouveau", true))
        assertEquals(deadline, (flow.state() as PairCapacityFlow.State.AwaitingRemoval).request.deadline, "polling does not extend the wait")
    }

    @Test fun theOwnerPicksAPhoneAndTheNewOneIsTrustedAtomically() {
        fill(8)
        flow.ask(addr(50), "Nouveau", true)
        val res = flow.choose(addr(2), addr(50))
        val ok = assertIs<PairCapacityFlow.Choice.Replaced>(res)
        assertEquals("Tel 2", ok.removed.name); assertEquals("Nouveau", ok.added.name)
        assertEquals(8, reg.list().size); assertFalse(reg.isTrusted(addr(2))); assertTrue(reg.isTrusted(addr(50)))
        assertEquals(PairCapacityFlow.State.Idle, flow.state())
        assertEquals(PairCapacityFlow.Answer.Trusted, flow.ask(addr(50), "Nouveau", true), "the phone's next poll finds itself trusted")
        assertEquals(PairCapacityFlow.Kind.REPLACED, events.last().kind); assertEquals("Tel 2", events.last().removedName)
    }

    @Test fun chooseWithoutARequestOrWithAnUnknownPhoneChangesNothing() {
        fill(8)
        assertEquals(PairCapacityFlow.Choice.NotPending, flow.choose(addr(2), addr(50)))
        flow.ask(addr(50), "Nouveau", true)
        assertEquals(PairCapacityFlow.Choice.NotFound, flow.choose(addr(99), addr(50)))
        assertEquals(8, reg.list().size); assertTrue(reg.isTrusted(addr(2)))
        assertIs<PairCapacityFlow.State.AwaitingRemoval>(flow.state(), "still waiting: the owner may choose again")
    }

    @Test fun aFailingWriteKeepsTheOldPhoneAndTheRequestSoTheOwnerCanTryAgain() {
        fill(8)
        flow.ask(addr(50), "Nouveau", true)
        disk.fail = true
        assertEquals(PairCapacityFlow.Choice.WriteFailed, flow.choose(addr(2), addr(50)))
        assertTrue(reg.isTrusted(addr(2))); assertFalse(reg.isTrusted(addr(50))); assertEquals(8, reg.list().size)
        assertIs<PairCapacityFlow.State.AwaitingRemoval>(flow.state())
        disk.fail = false
        assertIs<PairCapacityFlow.Choice.Replaced>(flow.choose(addr(2), addr(50)))
    }

    @Test fun cancelEndsTheRequestWithAVisibleReasonSeenOnceByThePhone() {
        fill(8)
        flow.ask(addr(50), "Nouveau", true)
        assertTrue(flow.cancel())
        assertEquals(PairCapacityFlow.State.Idle, flow.state())
        assertEquals(PairCapacityFlow.Kind.CANCELLED, events.last().kind)
        assertEquals(8, reg.list().size); assertFalse(reg.isTrusted(addr(50)))
        assertEquals(PairCapacityFlow.Answer.Cancelled, flow.ask(addr(50), "Nouveau", true))
        assertEquals(PairCapacityFlow.Answer.Pending, flow.ask(addr(50), "Nouveau", true), "told once; a new try is a new request")
        assertFalse(PairCapacityFlow(reg, clock::now).cancel(), "nothing to cancel")
    }

    @Test fun theRequestTimesOutAfterTwoMinutesWithAVisibleReason() {
        fill(8)
        flow.ask(addr(50), "Nouveau", true)
        clock.advance(119_000)
        assertIs<PairCapacityFlow.State.AwaitingRemoval>(flow.state())
        clock.advance(2_000)
        assertEquals(PairCapacityFlow.State.Idle, flow.state())
        assertEquals(PairCapacityFlow.Kind.TIMED_OUT, events.last().kind)
        assertEquals(PairCapacityFlow.Choice.NotPending, flow.choose(addr(2), addr(50)), "too late: the owner cannot replace after the timeout")
        assertEquals(PairCapacityFlow.Answer.TimedOut, flow.ask(addr(50), "Nouveau", true))
        assertEquals(8, reg.list().size); assertFalse(reg.isTrusted(addr(50)))
    }

    @Test fun timeoutIsNoticedByThePhonePollEvenIfNobodyLooksAtTheTvScreen() {
        fill(8)
        flow.ask(addr(50), "Nouveau", true)
        clock.advance(121_000)
        assertEquals(PairCapacityFlow.Answer.TimedOut, flow.ask(addr(50), "Nouveau", true))
        assertEquals(PairCapacityFlow.Kind.TIMED_OUT, events.last().kind)
    }

    @Test fun eventsHaveTvTextsThatNameThePhone() {
        val e = listOf(
            PairCapacityFlow.Event(PairCapacityFlow.Kind.REQUESTED, "Galaxy"),
            PairCapacityFlow.Event(PairCapacityFlow.Kind.REPLACED, "Galaxy", "Vieux"),
            PairCapacityFlow.Event(PairCapacityFlow.Kind.CANCELLED, "Galaxy"),
            PairCapacityFlow.Event(PairCapacityFlow.Kind.TIMED_OUT, "Galaxy"))
        e.forEach { assertTrue("Galaxy" in PhonesTexts.tvMessage(it), PhonesTexts.tvMessage(it)) }
        assertTrue("Vieux" in PhonesTexts.tvMessage(e[1]))
    }
}
