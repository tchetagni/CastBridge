package castbridge.core.tv

import kotlin.test.*

/**
 * R-18 : « En attente du réseau : TV introuvable » alors que la copie avance. Une seule source de vérité pour l'adresse de la TV
 * ([TvEndpointResolver]) et une porte qui ne déclare « introuvable » qu'après un silence réel ([MissingTvGate], [TvWait]).
 */
class TvEndpointResolverTest {
    private var now = 1_000_000L
    private val lan = "http://192.168.1.20:8765"
    private val wd = "http://192.168.49.1:8765"
    private val bt = "http://127.0.0.1:8766"

    private fun resolver(vararg bases: String?, stale: Long = 30_000) = TvEndpointResolver({ now }, stale) { bases.toList() }

    @Test fun kindIsReadFromTheAddress() {
        assertEquals(EndpointKind.LAN, TvEndpointResolver.kindOf(lan))
        assertEquals(EndpointKind.WIFI_DIRECT, TvEndpointResolver.kindOf(wd))
        assertEquals(EndpointKind.BLUETOOTH, TvEndpointResolver.kindOf(bt))
    }

    @Test fun priorityIsLanThenWifiDirectThenBluetooth() {
        assertEquals(lan, resolver(bt, wd, lan).current()?.base)
        assertEquals(wd, resolver(bt, wd).current()?.base)
        assertEquals(bt, resolver(bt).current()?.base)
        assertEquals(EndpointKind.LAN, resolver(wd, lan).current()?.kind)
    }

    @Test fun noSourceAndNothingKnownIsNull() {
        assertNull(resolver(null, null).current())
    }

    @Test fun aBaseThatAnsweredRecentlyIsNotLost() {
        val sources = arrayOf<String?>(lan)
        val r = TvEndpointResolver({ now }, 30_000) { sources.toList() }
        assertEquals(lan, r.current()?.base)
        r.answered()
        sources[0] = null                                   // discovery forgot the TV (network callback, mDNS lost)
        now += 29_000
        assertEquals(lan, r.current()?.base)
        assertEquals(EndpointKind.LAST_GOOD, r.current()?.kind)
    }

    @Test fun aStaleBaseIsForgotten() {
        val sources = arrayOf<String?>(lan)
        val r = TvEndpointResolver({ now }, 30_000) { sources.toList() }
        r.current(); r.answered()
        sources[0] = null
        now += 30_001
        assertNull(r.current())
    }

    @Test fun answeringAgainRefreshesTheStaleClock() {
        val sources = arrayOf<String?>(lan)
        val r = TvEndpointResolver({ now }, 30_000) { sources.toList() }
        r.current(); r.answered()
        sources[0] = null
        now += 20_000; assertNotNull(r.current()); r.answered()
        now += 20_000; assertNotNull(r.current())
    }

    @Test fun aLiveSourceBeatsTheLastGoodOne() {
        val sources = arrayOf<String?>(lan)
        val r = TvEndpointResolver({ now }, 30_000) { sources.toList() }
        r.current(); r.answered()
        sources[0] = wd
        assertEquals(EndpointKind.WIFI_DIRECT, r.current()?.kind)
    }

    // ---- la porte « introuvable » ----

    @Test fun aMissIsSilentWhileAnyLaneMadeProgressInTheLastTenSeconds() {
        val g = MissingTvGate({ now })
        g.progress()
        now += 9_000
        assertEquals(MissingTvGate.Verdict.SILENT, g.miss())
        now += 40_000                                       // no progress for 49 s: now it counts
        assertEquals(MissingTvGate.Verdict.REPORT, g.miss())
    }

    @Test fun aFreshMissIsSilentThenReportedAfterTheBound() {
        val g = MissingTvGate({ now }, silentMs = 15_000)
        assertEquals(MissingTvGate.Verdict.SILENT, g.miss())
        now += 14_999; assertEquals(MissingTvGate.Verdict.SILENT, g.miss())
        now += 2; assertEquals(MissingTvGate.Verdict.REPORT, g.miss())
    }

    @Test fun findingTheTvResetsTheBound() {
        val g = MissingTvGate({ now }, silentMs = 15_000)
        g.miss(); now += 20_000; assertEquals(MissingTvGate.Verdict.REPORT, g.miss())
        g.found()
        assertEquals(MissingTvGate.Verdict.SILENT, g.miss())
    }

    @Test fun theWaitEndsWithAVisibleFailureAfterTheGiveUpBound() {
        val g = MissingTvGate({ now }, silentMs = 15_000, giveUpMs = 600_000)
        g.miss(); now += 600_000
        assertEquals(MissingTvGate.Verdict.GIVE_UP, g.miss())
    }

    // ---- la boucle de runFast ----

    @Test fun runFastLoopNeverDeclaresMissingWhileAResolverBaseIsAlive() {
        val reports = ArrayList<String>()
        var calls = 0
        val g = MissingTvGate({ now })
        val out = TvWait.until({ if (++calls < 4) null else lan }, g, { false }, { now += it }, { reports += it }, { })
        assertEquals(TvWait.Outcome.FOUND, out)
        assertTrue(reports.isEmpty(), "3 transient misses must be retried silently: $reports")
    }

    @Test fun runFastLoopStaysSilentWhileALaneProgresses() {
        val reports = ArrayList<String>()
        val g = MissingTvGate({ now })
        var calls = 0
        val out = TvWait.until({ if (++calls > 60) lan else null }, g, { false }, { now += it; g.progress() }, { reports += it }, { })
        assertEquals(TvWait.Outcome.FOUND, out)
        assertTrue(reports.isEmpty(), "progress every second: never « introuvable » ($reports)")
    }

    @Test fun runFastLoopReportsOnceTheBoundIsPassedAndGivesUpVisibly() {
        val reports = ArrayList<String>()
        var gaveUp = 0
        val g = MissingTvGate({ now }, silentMs = 15_000, giveUpMs = 60_000)
        val out = TvWait.until({ null }, g, { false }, { now += it }, { reports += it }, { gaveUp++ })
        assertEquals(TvWait.Outcome.GAVE_UP, out)
        assertEquals(1, gaveUp)
        assertTrue(reports.isNotEmpty() && reports.size < 60, "reported after the silent bound, then bounded: ${reports.size}")
        assertTrue(reports.all { it == TvWait.WAITING_REASON })
    }

    // ---- R-19 : adresses de repli (liaison de confiance, dernière adresse persistée), validées par un « hello » ----

    private val session = "http://192.168.1.77:8765"
    private val remembered = "http://192.168.1.20:8765"

    @Test fun theTrustedLinkAddressIsTriedBeforeSayingMissing() {
        val asked = ArrayList<String>()
        val r = TvEndpointResolver({ now }, 30_000, sources = { listOf(null) }, fallbacks = { listOf(session, remembered) }, probe = { asked += it; it == session })
        assertEquals(session, r.current()?.base, "the screen says « Connectée » at this address: the upload uses it, never « TV introuvable »")
        assertEquals(listOf(session), asked)
    }

    @Test fun aFallbackIsUsedOnlyIfItAnswersTheHello() {
        val asked = ArrayList<String>()
        val r = TvEndpointResolver({ now }, 30_000, sources = { emptyList() }, fallbacks = { listOf(session, remembered) }, probe = { asked += it; false })
        assertNull(r.current(), "nothing answers: then (only then) missing")
        assertEquals(listOf(session, remembered), asked, "every fallback was tried first")
    }

    @Test fun freshDiscoveryComesBeforeTheFallbacks() {
        val r = TvEndpointResolver({ now }, 30_000, sources = { listOf(lan) }, fallbacks = { listOf(session) }, probe = { true })
        assertEquals(lan, r.current()?.base)
    }

    @Test fun aStaleLiveAddressGivesWayToAFallbackThatAnswers() {
        // a manual/hint address from before the DHCP change no longer answers: the trusted link's new address does
        val r = TvEndpointResolver({ now }, 30_000, sources = { listOf(lan) }, fallbacks = { listOf(session) }, probe = { it == session })
        assertEquals(session, r.current()?.base)
    }

    @Test fun aLiveAddressThatDoesNotAnswerIsStillTriedWhenNothingElseAnswers() {
        val r = TvEndpointResolver({ now }, 30_000, sources = { listOf(lan) }, fallbacks = { listOf(session) }, probe = { false })
        assertEquals(lan, r.current()?.base, "the copy's own retries say why (never null while an address exists)")
    }

    @Test fun probesAreCachedAndNotRepeatedWhileTheTvAnswers() {
        var probes = 0
        val r = TvEndpointResolver({ now }, 30_000, sources = { listOf(lan) }, probe = { probes++; true }, probeCacheMs = 5_000)
        repeat(50) { r.current() }
        assertEquals(1, probes, "one hello for 50 resolutions within the cache")
        r.answered()
        now += 20_000
        repeat(50) { r.current() }
        assertEquals(1, probes, "the address that just answered needs no hello")
        now += 31_000
        r.current()
        assertEquals(2, probes)
    }

    @Test fun aFailedProbeIsRetriedAfterItsCache() {
        var up = false; var probes = 0
        val r = TvEndpointResolver({ now }, 30_000, sources = { emptyList() }, fallbacks = { listOf(session) }, probe = { probes++; up }, probeCacheMs = 5_000)
        assertNull(r.current())
        up = true
        now += 1_000; assertNull(r.current(), "cached refusal")
        now += 4_500; assertEquals(session, r.current()?.base, "the TV answers again: the address comes back by itself")
        assertTrue(probes <= 3)
    }

    @Test fun withoutProbeFallbacksAreTakenAsIs() {
        val r = TvEndpointResolver({ now }, 30_000, sources = { emptyList() }, fallbacks = { listOf(null, session) })
        assertEquals(session, r.current()?.base)
        assertEquals(EndpointKind.LAN, r.current()?.kind)
    }

    @Test fun runFastLoopStopsOnCancel() {
        var n = 0
        val out = TvWait.until({ null }, MissingTvGate({ now }), { ++n > 3 }, { now += it }, { }, { })
        assertEquals(TvWait.Outcome.CANCELLED, out)
        assertTrue(n < 10)
    }
}
