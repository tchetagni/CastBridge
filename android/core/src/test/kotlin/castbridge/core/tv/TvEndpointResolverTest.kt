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

    @Test fun runFastLoopStopsOnCancel() {
        var n = 0
        val out = TvWait.until({ null }, MissingTvGate({ now }), { ++n > 3 }, { now += it }, { }, { })
        assertEquals(TvWait.Outcome.CANCELLED, out)
        assertTrue(n < 10)
    }
}
