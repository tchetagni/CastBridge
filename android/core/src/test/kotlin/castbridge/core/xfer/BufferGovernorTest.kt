package castbridge.core.xfer

import kotlin.math.max
import kotlin.math.min
import kotlin.test.*

/**
 * R-15, régulation en boucle fermée : le tampon du lecteur (secondes d'avance) règle le débit d'écriture de la copie. Horloge fausse, lecteur simulé.
 * Ce qui n'est PAS prouvé ici : que libVLC de la vraie TV donne cette mesure (voir PlaybackHealth) ; seule la logique l'est.
 */
class BufferGovernorTest {
    private var now = 10_000_000L
    private val g = BufferGovernor { now }
    private val floor = PlaybackAwareCopyPolicy.FLOOR_BPS

    private fun feed(sec: Double?, steps: Int = 1, stepMs: Long = 500) {
        repeat(steps) { now += stepMs; g.sample(sec) }
    }

    // ---- bandes ----

    @Test fun withoutAMeasureTheLoopIsOpenAndTheStaticPolicyRules() {
        assertEquals(BufferGovernor.Band.OPEN, g.band)
        assertEquals(3_000_000, g.allowedBps(3_000_000)); assertEquals(0, g.allowedBps(0)); assertEquals(0, g.holdMs())
        feed(null, steps = 10)
        assertEquals(BufferGovernor.Band.OPEN, g.band); assertEquals(3_000_000, g.allowedBps(3_000_000)); assertEquals(0, g.holdMs())
    }

    @Test fun aMeasureThatGoesStaleFallsBackToOpenLoop() {
        feed(1.0, steps = 6)                                   // trouble: floor or hold
        assertNotEquals(BufferGovernor.Band.OPEN, g.band)
        now += 4_000                                           // the player no longer reports
        assertEquals(BufferGovernor.Band.OPEN, g.band)
        assertEquals(3_000_000, g.allowedBps(3_000_000)); assertEquals(0, g.holdMs())
    }

    @Test fun aComfortableBufferGivesFullSpeed() {
        feed(20.0, steps = 20)
        assertEquals(BufferGovernor.Band.FULL, g.band)
        assertEquals(3_000_000, g.allowedBps(3_000_000))
        assertEquals(0, g.allowedBps(0))
        assertEquals(0, g.holdMs())
    }

    @Test fun theMiddleBandIsProportionalBetweenTheFloorAndTheCeiling() {
        feed(20.0, steps = 20)
        feed(9.0, steps = 30)
        assertEquals(BufferGovernor.Band.PROPORTIONAL, g.band)
        val a = g.allowedBps(4_000_000)
        assertTrue(a in (floor + 1) until 4_000_000, "a=$a")
        feed(11.0, steps = 80)
        assertTrue(g.allowedBps(4_000_000) > a)
    }

    @Test fun aLowBufferGoesToTheFloorAndNeverBelow() {
        feed(20.0, steps = 20); feed(4.0, steps = 20)
        assertEquals(BufferGovernor.Band.LOW, g.band)
        assertEquals(floor, g.allowedBps(4_000_000)); assertEquals(floor, g.allowedBps(0))
        assertEquals(0, g.holdMs())
    }

    @Test fun anAlmostEmptyBufferHoldsTheCopyUntilTheBufferIsAboveEightSeconds() {
        feed(20.0, steps = 10); feed(1.0, steps = 8)
        assertEquals(BufferGovernor.Band.HOLD, g.band)
        g.passed(); assertTrue(g.holdMs() > 0)
        feed(5.0, steps = 20); assertEquals(BufferGovernor.Band.HOLD, g.band)       // 5 s: still not enough to restart
        feed(7.5, steps = 20); assertEquals(BufferGovernor.Band.HOLD, g.band)
        feed(9.0, steps = 20); assertNotEquals(BufferGovernor.Band.HOLD, g.band)
        assertEquals(0, g.holdMs())
    }

    @Test fun hysteresisKeepsAJitteringBufferFromFlappingBetweenBands() {
        feed(14.0, steps = 10)
        val before = g.transitions
        val v = doubleArrayOf(11.6, 12.4, 11.7, 12.3, 11.5, 12.5, 11.8, 12.2)
        repeat(5) { v.forEach { feed(it) } }
        assertTrue(g.transitions - before <= 2, "transitions=${g.transitions - before}")
    }

    // ---- montée additive, descente multiplicative ----

    @Test fun theRateFallsFastWhenTheBufferCollapses() {
        feed(20.0, steps = 20)
        feed(3.0, steps = 4)              // 2 s of trouble
        assertEquals(floor, g.allowedBps(0), "floor within 4 samples")
    }

    @Test fun theRateComesBackGraduallyNeverInOneJump() {
        feed(20.0, steps = 20); feed(3.0, steps = 10)
        assertEquals(floor, g.allowedBps(4_000_000))
        feed(20.0, steps = 2)                                  // buffer refilled: 1 s later...
        val oneSecondLater = g.allowedBps(4_000_000)
        assertTrue(oneSecondLater < 2_000_000, "additive: $oneSecondLater")
        feed(20.0, steps = 40)
        assertEquals(4_000_000, g.allowedBps(4_000_000))
    }

    // ---- jamais plus de 10 s sans un octet ----

    @Test fun neverMoreThanTheMaxHoldWithoutAByteEvenIfTheBufferStaysEmpty() {
        feed(0.0, steps = 4)
        var lastPass = now; var worst = 0L; var passes = 0
        repeat(60 * 20) {                                       // 60 s, every 50 ms a writer asks
            now += 50; if (it % 10 == 0) g.sample(0.0)
            if (g.holdMs() == 0L) { worst = max(worst, now - lastPass); lastPass = now; g.passed(); passes++ }
        }
        assertTrue(worst <= PlaybackAwareCopyPolicy.MAX_HOLD_MS + 100, "worst gap $worst ms")
        assertTrue(passes >= 5, "passes=$passes: the copy must go on")
    }

    // ---- lecteur simulé ----

    private class Sim(val g: BufferGovernor, val clock: () -> Long, val setNow: (Long) -> Unit, val ceiling: Long, val diskBps: Long = 4_000_000, val videoBps: Long = 1_000_000,
                      val linkBps: Long = 6_000_000, val governed: Boolean = true) {
        var buffer = 14.0; var minBuffer = 99.0; var copied = 0L; var longestGapMs = 0L; var maxHold = 0L
        var lastByteAt = clock(); var holdingSince = -1L; var changes = 0; private var lastRate = -1L; private var lastDir = 0
        fun run(seconds: Int, tickMs: Long = 250, onTick: (Int) -> Unit = {}) {
            var sampleAcc = 0L
            repeat((seconds * 1000 / tickMs).toInt()) { t ->
                setNow(clock() + tickMs); sampleAcc += tickMs
                if (sampleAcc >= 500) { sampleAcc = 0; g.sample(buffer) }
                val hold = governed && g.holdMs() > 0
                val rate = if (!governed) (if (ceiling > 0) min(ceiling, linkBps) else linkBps)
                else if (hold) 0L else { val a = g.allowedBps(ceiling); if (a == 0L) linkBps else min(a, linkBps) }
                if (hold) { if (holdingSince < 0) holdingSince = clock(); maxHold = max(maxHold, clock() - holdingSince) } else holdingSince = -1
                if (rate > 0) { g.passed(); copied += rate * tickMs / 1000; lastByteAt = clock() }
                longestGapMs = max(longestGapMs, clock() - lastByteAt)
                val readerBw = max(0L, diskBps - rate)
                buffer = (buffer + (readerBw.toDouble() / videoBps - 1.0) * tickMs / 1000).coerceIn(0.0, 30.0)
                minBuffer = min(minBuffer, buffer)
                if (lastRate >= 0 && rate != lastRate) { val dir = if (rate > lastRate) 1 else -1; if (dir != lastDir) changes++; lastDir = dir }
                lastRate = rate
                onTick(t)
            }
        }
    }

    @Test fun withoutTheGovernorAContendedDiskStarvesThePlayer() {
        val sim = Sim(g, { now }, { now = it }, ceiling = 0, governed = false)
        sim.run(60)
        assertTrue(sim.minBuffer < 0.5, "witness: min=${sim.minBuffer}")
    }

    @Test fun theBufferNeverDropsBelowTwoSecondsOnAContendedDiskAndTheCopyFinishes() {
        for (ceiling in listOf(0L, 3_000_000L)) {
            now = 10_000_000L
            val gg = BufferGovernor { now }
            val sim = Sim(gg, { now }, { now = it }, ceiling)
            sim.run(300)
            assertTrue(sim.minBuffer >= 2.0, "ceiling=$ceiling min buffer=${sim.minBuffer}")
            assertTrue(sim.copied >= 100L shl 20, "ceiling=$ceiling copied only ${sim.copied} bytes in 300 s: a 100 MB copy must finish")
            assertTrue(sim.copied >= floor * 300 * 9 / 10)
            assertTrue(sim.longestGapMs <= PlaybackAwareCopyPolicy.MAX_HOLD_MS + 1_000, "gap ${sim.longestGapMs}")
            assertTrue(sim.maxHold <= PlaybackAwareCopyPolicy.MAX_HOLD_MS + 500, "hold ${sim.maxHold}")
        }
    }

    @Test fun theRateDoesNotOscillateMoreThanTwelveDirectionChangesPerMinute() {
        val sim = Sim(g, { now }, { now = it }, 3_000_000)
        sim.run(60)                                             // the transient
        sim.changes = 0
        sim.run(180)
        assertTrue(sim.changes <= 36, "direction changes in 3 min: ${sim.changes}")
        assertTrue(g.transitions < 200)
    }

    @Test fun anUnfriendlyPlayerWithNoPlayheadKeepsTheCopyAliveAtTheFloor() {
        val sim = Sim(g, { now }, { now = it }, 3_000_000, diskBps = 700_000, videoBps = 1_000_000)   // the disk can not even feed the video
        sim.run(120)
        assertTrue(sim.copied > 0)
        assertTrue(sim.longestGapMs <= PlaybackAwareCopyPolicy.MAX_HOLD_MS + 1_000)
    }

    // ---- la mesure du lecteur (libVLC ne donne pas son niveau de tampon) ----

    @Test fun healthIsUnknownUntilThePlayerReportsTime() {
        val h = PlaybackHealth()
        assertNull(h.bufferSec(1_000))
        h.onPlaying(1_000); assertNull(h.bufferSec(1_000))
        h.onTime(1_250, 250); assertNotNull(h.bufferSec(1_250))
    }

    @Test fun smoothPlaybackBuildsComfortUpToTheCap() {
        val h = PlaybackHealth(); h.onPlaying(0)
        var t = 0L; var pos = 0L
        repeat(120) { t += 250; pos += 250; h.onTime(t, pos) }
        assertEquals(PlaybackHealth.CAP_SEC, h.bufferSec(t)!!, 0.01)
    }

    @Test fun aPlayheadThatSlipsAgainstTheWallClockEatsTheComfort() {
        val h = PlaybackHealth(); h.onPlaying(0)
        var t = 0L; var pos = 0L
        repeat(60) { t += 250; pos += 250; h.onTime(t, pos) }
        val before = h.bufferSec(t)!!
        repeat(8) { t += 250; pos += 100; h.onTime(t, pos) }    // 40 % speed: stutter
        assertTrue(h.bufferSec(t)!! < before - 3, "before=$before after=${h.bufferSec(t)}")
    }

    @Test fun aBufferingEventDropsTheComfortAtOnceAndASeekIsNotAStutter() {
        val h = PlaybackHealth(); h.onPlaying(0)
        var t = 0L; var pos = 0L
        repeat(60) { t += 250; pos += 250; h.onTime(t, pos) }
        t += 250; pos += 600_000; h.onTime(t, pos)               // seek forward by 10 minutes
        assertTrue(h.bufferSec(t)!! > 10, "a seek is not a stutter")
        h.onBuffering(t, 40f); assertTrue(h.bufferSec(t)!! <= 1.0)
        h.onBuffering(t + 100, 100f)
    }

    @Test fun silenceFromAPlayingPlayerDecaysAndStopClearsIt() {
        val h = PlaybackHealth(); h.onPlaying(0)
        var t = 0L; var pos = 0L
        repeat(60) { t += 250; pos += 250; h.onTime(t, pos) }
        val ok = h.bufferSec(t)!!
        assertTrue(h.bufferSec(t + 6_000)!! < ok - 5)
        h.onStopped(); assertNull(h.bufferSec(t + 6_000))
    }
}
