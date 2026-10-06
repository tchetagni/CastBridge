package castbridge.core.tv

import castbridge.core.trust.MemoryTrustPersistence
import kotlin.test.*

/**
 * R-19 : « Connectée » à l'écran et, en même temps, « Envoi … 0 % · En pause : TV introuvable — reprise automatique ». La reprise automatique doit
 * revenir seule quand les conditions redeviennent bonnes : découverte qui se relance (sérialisée, bornée), résolution sous délai de garde, nom tolérant,
 * adresse de la liaison de confiance et dernière adresse qui a répondu (validées par un « hello »), file qui relance l'envoi dès que la TV répond.
 */
class TvReachTest {
    private var now = 1_000_000L

    // ---- (a) reprise bornée ----

    @Test fun backoffIsOneTwoFiveTenThenFifteenForEver() {
        assertEquals(listOf(1_000L, 2_000L, 5_000L, 10_000L, 15_000L, 15_000L, 15_000L), (0..6).map { RetryBackoff.delayMs(it) })
        assertEquals(15_000L, RetryBackoff.delayMs(1_000))
        assertEquals(1_000L, RetryBackoff.delayMs(-3))
    }

    // ---- (a) découverte sérialisée ----

    private fun sup() = DiscoverySupervisor()

    @Test fun aStartFromIdleStartsOnce() {
        val s = sup()
        assertEquals(DiscoverySupervisor.Action.Start, s.start())
        assertEquals(DiscoverySupervisor.Action.None, s.start(), "never two discoveries at once")
        assertEquals(DiscoverySupervisor.Action.None, s.onStarted())
        assertEquals(DiscoverySupervisor.Phase.RUNNING, s.phase)
    }

    @Test fun aRestartStopsThenWaitsForTheRealStopBeforeStarting() {
        val s = sup(); s.start(); s.onStarted()
        assertEquals(DiscoverySupervisor.Action.Stop, s.restart())
        // the classic bug: start() at once while Android still stops the previous one → FAILURE_ALREADY_ACTIVE
        assertEquals(DiscoverySupervisor.Action.None, s.restart(), "a second restart during the stop is merged")
        assertEquals(DiscoverySupervisor.Action.None, s.start())
        assertEquals(DiscoverySupervisor.Action.Start, s.onStopped(), "start only once Android said it stopped")
        assertEquals(DiscoverySupervisor.Phase.STARTING, s.phase)
    }

    @Test fun aRestartDuringTheStartStopsAsSoonAsItStarted() {
        val s = sup(); s.start()
        assertEquals(DiscoverySupervisor.Action.None, s.restart())
        assertEquals(DiscoverySupervisor.Action.Stop, s.onStarted())
        assertEquals(DiscoverySupervisor.Action.Start, s.onStopped())
    }

    @Test fun aFailedStartIsRetriedWithTheBoundedBackoffWhileWanted() {
        val s = sup(); s.start()
        val waits = (0 until 6).map { i ->
            val a = s.onStartFailed()
            assertTrue(a is DiscoverySupervisor.Action.RetryIn, "a failure never kills the discovery for good (attempt $i): $a")
            assertEquals(DiscoverySupervisor.Action.None, s.start(), "the scheduled retry is not doubled")
            assertEquals(DiscoverySupervisor.Action.Start, s.onRetryDue())
            a.ms
        }
        assertEquals(listOf(1_000L, 2_000L, 5_000L, 10_000L, 15_000L, 15_000L), waits)
        s.onStarted()
        s.restart(); s.onStopped()
        assertEquals(DiscoverySupervisor.Action.RetryIn(1_000), s.onStartFailed(), "a success resets the backoff")
    }

    @Test fun nothingIsRetriedOnceStopped() {
        val s = sup(); s.start()
        s.onStartFailed()
        assertEquals(DiscoverySupervisor.Action.None, s.stop())
        assertEquals(DiscoverySupervisor.Action.None, s.onRetryDue())
        assertEquals(DiscoverySupervisor.Phase.IDLE, s.phase)
    }

    @Test fun aStopDuringTheStartStopsOnceStartedAndNeverRestarts() {
        val s = sup(); s.start()
        assertEquals(DiscoverySupervisor.Action.None, s.stop())
        assertEquals(DiscoverySupervisor.Action.Stop, s.onStarted())
        assertEquals(DiscoverySupervisor.Action.None, s.onStopped())
        assertEquals(DiscoverySupervisor.Phase.IDLE, s.phase)
    }

    @Test fun aStopThatNeverReportsIsEndedByItsTimeout() {
        val s = sup(); s.start(); s.onStarted(); s.restart()
        assertEquals(DiscoverySupervisor.Action.Start, s.onStopTimeout(), "onDiscoveryStopped lost: the discovery is not dead for good")
        assertEquals(DiscoverySupervisor.Action.None, s.onStopped(), "the late callback changes nothing")
    }

    // ---- (a) événements réseau : regroupés, liste vidée seulement si l'adresse change ----

    @Test fun theCallAtRegistrationIsIgnored() {
        val f = NetworkChangeFilter("wlan0/192.168.1.5")
        f.onEvent(now, "wlan0/192.168.1.5")
        now += 5_000
        assertNull(f.poll(now))
    }

    @Test fun eventsAreDebouncedTwoSeconds() {
        val f = NetworkChangeFilter("wlan0/192.168.1.5")
        f.onEvent(now, null); now += 300; f.onEvent(now, "wlan0/192.168.1.5"); now += 300; f.onEvent(now, null); now += 300; f.onEvent(now, "wlan0/192.168.1.5")
        now += 1_999
        assertNull(f.poll(now), "still moving")
        now += 1
        assertEquals(NetworkChangeFilter.Decision(restart = true, clear = false), f.poll(now), "lost then back at the same address: restart, keep the list")
        assertNull(f.poll(now + 10_000), "once")
    }

    @Test fun aNewAddressClearsTheList() {
        val f = NetworkChangeFilter("wlan0/192.168.1.5")
        f.onEvent(now, "wlan0/192.168.1.77")       // new DHCP lease
        now += 2_000
        assertEquals(NetworkChangeFilter.Decision(restart = true, clear = true), f.poll(now))
        f.onEvent(now, "wlan0/192.168.1.77"); now += 2_000
        assertNull(f.poll(now), "the new address is now the reference")
    }

    @Test fun noNetworkAtTheEndWaitsForItsReturn() {
        val f = NetworkChangeFilter("wlan0/192.168.1.5")
        f.onEvent(now, null); now += 3_000
        assertNull(f.poll(now))
        f.onEvent(now, "wlan0/192.168.1.5"); now += 2_000
        assertEquals(NetworkChangeFilter.Decision(restart = true, clear = false), f.poll(now), "the loss is remembered until the network is back")
    }

    @Test fun wifiDirectThenBackToTheLanClears() {
        val f = NetworkChangeFilter("wlan0/192.168.1.5")
        f.onEvent(now, "p2p-wlan0-0/192.168.49.20"); now += 2_000
        assertEquals(true, f.poll(now)?.clear)
        f.onEvent(now, "wlan0/192.168.1.5"); now += 2_000
        assertEquals(true, f.poll(now)?.clear)
    }

    // ---- (b) délai de garde des résolutions ----

    @Test fun resolutionsAreSerialized() {
        val q = SerialResolveQueue<String>(guardMs = 5_000)
        val a = q.enqueue("A", now)
        assertEquals("A", a?.item)
        assertNull(q.enqueue("B", now), "one at a time")
        assertEquals("B", q.done(a!!.token, now)?.item)
    }

    @Test fun aResolutionThatNeverAnswersFreesTheQueueAfterFiveSeconds() {
        val q = SerialResolveQueue<String>(guardMs = 5_000)
        val a = q.enqueue("A", now)!!
        q.enqueue("B", now)
        assertNull(q.timeout(a.token, now + 4_999), "not yet")
        val b = q.timeout(a.token, now + 5_000)
        assertEquals("B", b?.item, "the queue moves on")
        assertNull(q.done(a.token, now + 6_000), "the late answer of A never advances the queue twice")
        assertNull(q.enqueue("C", now + 6_000))
        assertEquals("C", q.enqueue("D", now + 11_000)?.item, "a stuck resolution is also expired by the next enqueue")
    }

    @Test fun clearForgetsThePendingResolution() {
        val q = SerialResolveQueue<String>()
        val a = q.enqueue("A", now)!!
        q.enqueue("B", now)
        q.clear()
        assertNull(q.done(a.token, now))
        assertEquals("C", q.enqueue("C", now)?.item)
    }

    // ---- (c) nom tolérant ----

    @Test fun exactNameFirst() {
        assertEquals(TvNameMatch.Pick("SMART_TV", TvNameMatch.How.EXACT), TvNameMatch.pick("SMART_TV", listOf("SMART_TV (2)", "SMART_TV")))
    }

    @Test fun androidRenamingAndPrefixAreTolerated() {
        assertEquals("SMART_TV (2)", TvNameMatch.pick("SMART_TV", listOf("SMART_TV (2)", "Salon"))?.name)
        assertEquals(TvNameMatch.How.BASE, TvNameMatch.pick("SMART_TV", listOf("SMART_TV (2)", "Salon"))?.how)
        assertEquals("CastBridge TV SMART_TV", TvNameMatch.pick("SMART_TV", listOf("CastBridge TV SMART_TV", "Salon"))?.name)
        assertEquals("SMART_TV", TvNameMatch.pick("CastBridge TV SMART_TV (3)", listOf("SMART_TV", "Salon"))?.name)
        assertEquals("SMART_TV\\032(2)", TvNameMatch.pick("smart_tv", listOf("SMART_TV\\032(2)", "Salon"))?.name, "mDNS escaped space")
    }

    @Test fun twoTvsOfTheSameModelAreNeverGuessed() {
        assertNull(TvNameMatch.pick("SMART_TV", listOf("SMART_TV (2)", "SMART_TV (3)")))
        assertNull(TvNameMatch.pick("SMART_TV (4)", listOf("SMART_TV (2)", "SMART_TV")), "a third name of the model: neither of the two")
    }

    @Test fun theOnlyTvOfTheNetworkIsTaken() {
        assertEquals(TvNameMatch.Pick("Salon", TvNameMatch.How.SOLE), TvNameMatch.pick("SMART_TV", listOf("Salon")))
        assertEquals(TvNameMatch.Pick("Salon", TvNameMatch.How.SOLE), TvNameMatch.pick("SMART_TV", listOf("Salon", "Salon")), "the same announce twice is one TV")
        assertNull(TvNameMatch.pick("SMART_TV", listOf("Salon", "Chambre")))
        assertNull(TvNameMatch.pick("SMART_TV", emptyList()))
    }

    @Test fun aTvKnownAsAnotherOneIsNeverTaken() {
        val other = { n: String -> n == "Salon" }
        assertNull(TvNameMatch.pick("SMART_TV", listOf("Salon"), other))
        assertNull(TvNameMatch.pick("SMART_TV", listOf("SMART_TV (2)"), { it == "SMART_TV (2)" }))
        assertEquals("SMART_TV", TvNameMatch.pick("SMART_TV", listOf("SMART_TV"), { true })?.name, "the exact name is always this TV")
    }

    @Test fun baseNameStripsDecorations() {
        assertEquals("smart_tv", TvNameMatch.base("  CastBridge TV SMART_TV (2) (Bluetooth) "))
        assertEquals("smart_tv", TvNameMatch.base("CastBridge-TV SMART_TV"))
        assertEquals("tv (salon)", TvNameMatch.base("TV (salon)"), "only a number in brackets is a suffix")
    }

    // ---- (d) dernière adresse qui a répondu, persistée 10 min ----

    @Test fun theLastAddressIsKeptTenMinutesAcrossInstances() {
        val store = MemoryTrustPersistence()
        TvAddressMemory(store, { now }).remember("SMART_TV", "http://192.168.1.20:8765")
        val m = TvAddressMemory(store, { now })           // the process was killed and the queue resumes
        now += 599_000
        assertEquals("http://192.168.1.20:8765", m.recall("SMART_TV"))
        assertEquals("http://192.168.1.20:8765", m.recall("SMART_TV (2)"), "the same TV under its renamed name")
        now += 2_000
        assertNull(m.recall("SMART_TV"), "older than 10 min: never used")
    }

    @Test fun theMemoryIgnoresTheLoopbackAndOtherTvs() {
        val m = TvAddressMemory(MemoryTrustPersistence(), { now })
        m.remember("SMART_TV", "http://127.0.0.1:18765")
        assertNull(m.recall("SMART_TV"))
        m.remember("Salon", "http://192.168.1.30:8765")
        assertNull(m.recall("SMART_TV"))
    }

    @Test fun theMemoryWritesAtMostEveryFifteenSecondsForTheSameAddress() {
        var writes = 0
        val store = object : castbridge.core.trust.TrustPersistence { var t: String? = null
            override fun load() = t; override fun save(text: String) { writes++; t = text } }
        val m = TvAddressMemory(store, { now })
        repeat(100) { m.remember("SMART_TV", "http://192.168.1.20:8765"); now += 100 }
        assertEquals(1, writes)
        now += 15_000; m.remember("SMART_TV", "http://192.168.1.20:8765"); assertEquals(2, writes)
        m.remember("SMART_TV", "http://192.168.1.21:8765"); assertEquals(3, writes, "a new address is written at once")
    }

    @Test fun aCorruptStoreIsEmpty() {
        val m = TvAddressMemory(MemoryTrustPersistence("garbage\n\tx\ty\n"), { now })
        assertNull(m.recall("SMART_TV"))
    }

    // ---- (f) la file relance l'envoi dès qu'une adresse répond ----

    @Test fun theQueueRelaunchesAsSoonAsTheTvAnswers() {
        var probes = 0
        val slept = ArrayList<Long>()
        val ok = ResumeWait.until(reachable = { ++probes >= 4 }, stop = { false }, sleep = { slept += it; now += it }, clock = { now })
        assertEquals(ResumeWait.Result.REACHABLE, ok)
        assertEquals(4, probes)
        assertEquals(1_000L + 2_000 + 5_000, slept.sum(), "1 s, 2 s, 5 s between the tries: $slept")
    }

    @Test fun theQueueWaitStopsOnCancelOrPause() {
        var n = 0
        assertEquals(ResumeWait.Result.STOPPED, ResumeWait.until({ false }, { ++n > 5 }, { now += it }, { now }))
        assertTrue(n < 50)
    }

    @Test fun theQueueWaitIsBoundedAndNeverSilentForEver() {
        var probes = 0
        val r = ResumeWait.until({ probes++; false }, { false }, { now += it }, { now }, maxMs = 3_600_000)
        assertEquals(ResumeWait.Result.GAVE_UP, r)
        assertTrue(probes in 200..300, "about one try every 15 s for an hour: $probes")
    }

    @Test fun theUnreachableOutcomesAreRecognised() {
        assertTrue(ResumeWait.isUnreachable(TvWait.GAVE_UP_TEXT))
        assertTrue(ResumeWait.isUnreachable(TvWait.WAITING_REASON))
        assertFalse(ResumeWait.isUnreachable("refusé par la TV"))
        assertFalse(ResumeWait.isUnreachable(null))
        assertEquals(QueueOutcome.Kind.WAIT_FOR_TV, QueueOutcome.of(TvWait.GAVE_UP_TEXT, cancelAsked = false, backgroundRefusal = false))
        assertEquals(QueueOutcome.Kind.CANCELLED, QueueOutcome.of(TvWait.GAVE_UP_TEXT, cancelAsked = true, backgroundRefusal = false))
        assertEquals(QueueOutcome.Kind.FAILED, QueueOutcome.of("refusé par la TV", cancelAsked = false, backgroundRefusal = false))
    }
}
