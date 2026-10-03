package castbridge.core.xfer

import kotlin.test.*

/**
 * R-16 : une image figée (ou des images perdues) pendant que le son et la tête de lecture avancent doit gouverner la copie comme un tampon vide.
 * Lecteur simulé (le vrai ne l'est pas ici : P-52).
 */
class VideoDistressGovernorTest {
    private var now = 5_000_000L
    private val health = PlaybackHealth()
    private var audio = 0L
    private var shown = 0

    /** [sec] secondes de lecture : la tête de lecture avance TOUJOURS ; les images avancent à [fps]. */
    private fun play(sec: Int, fps: Int = 25, loss: Int = 0, sink: () -> Unit = {}) {
        var lost = 0
        repeat(sec * 4) { i ->
            now += 250; audio += 250; health.onTime(now, audio)
            if (i % 4 == 3) { shown += fps; lost += loss; health.onVideoStats(now, shown, lost) }
            sink()
        }
    }

    @Test fun aFrozenPictureCutsTheComfortEvenThoughThePlayheadAdvances() {
        health.onPlaying(now); play(10)
        val ok = health.bufferSec(now)!!
        assertTrue(ok >= 10.0, "smooth: $ok")
        play(4, fps = 0)
        val frozen = health.bufferSec(now)!!
        assertTrue(frozen <= 1.0, "frozen picture: $frozen")
        assertEquals(1, health.videoDistress(now).coerceAtMost(1))
    }

    @Test fun droppedFramesGoToTheFloorBandNotTheHold() {
        health.onPlaying(now); play(10)
        play(8, fps = 22, loss = 3)
        val b = health.bufferSec(now)!!
        assertTrue(b < BufferGovernor.LOW_SEC, "lost frames: $b"); assertTrue(b >= BufferGovernor.HOLD_SEC, "floor, not hold: $b")
    }

    @Test fun theGovernorHoldsTheCopyOnAFreezeButNeverForeverAndRecovers() {
        val g = BufferGovernor { now }
        health.onPlaying(now); play(10) { g.sample(health.bufferSec(now)) }
        assertEquals(BufferGovernor.Band.FULL, g.band)
        play(6, fps = 0) { g.sample(health.bufferSec(now)) }
        assertEquals(BufferGovernor.Band.HOLD, g.band, "the picture is frozen: the copy pauses")
        g.passed(); val h = g.holdMs()
        assertTrue(h in 1..PlaybackAwareCopyPolicy.MAX_HOLD_MS)
        now += PlaybackAwareCopyPolicy.MAX_HOLD_MS; assertEquals(0, g.holdMs(), "a slice always passes within 10 s")
        play(30) { g.sample(health.bufferSec(now)) }
        assertNotEquals(BufferGovernor.Band.HOLD, g.band, "the picture is back: the copy resumes")
        assertTrue(g.allowedBps(3_000_000) >= PlaybackAwareCopyPolicy.FLOOR_BPS)
    }

    private val normal = PlaybackPriority.Normal(maxStreams = 6, syncEveryBytes = 64L shl 20)
    private fun sig(distress: Int, growing: Boolean = false) =
        PlaybackSignal("playing", "v.avi", growing = growing, playheadMs = 1000, durMs = 600_000, fileBytes = 400L shl 20, writeBps = 6_000_000,
            playingVolumeId = "usb", videoDistress = distress)

    @Test fun distressGivesCpuReliefToAnIndependentCopy() {
        val rest = PlaybackPriority.decide(sig(0), normal, "other.bin", "usb")
        assertFalse(rest.cpuRelief); assertEquals(0, rest.readCapBytes); assertEquals(0L, rest.sliceSleepMs)
        val d = PlaybackPriority.decide(sig(1), normal, "other.bin", "usb")
        assertTrue(d.cpuRelief); assertTrue(d.backgroundThreads)
        assertTrue(d.readCapBytes in 4_096..65_536, "${d.readCapBytes}"); assertTrue(d.sliceSleepMs > 0)
        assertTrue(d.verifyReadBps in 1..4_000_000, "the final check is slowed, never skipped: ${d.verifyReadBps}")
        assertTrue("video-distress" in d.reasons)
        val s = PlaybackPriority.decide(sig(2), normal, "other.bin", "other")
        assertTrue(s.sliceSleepMs >= d.sliceSleepMs); assertTrue(s.verifyReadBps in 1..4_000_000); assertTrue("video-distress:sustained" in s.reasons)
    }

    @Test fun theCopyThatFeedsTheVideoGetsReliefButIsNeverHeldBack() {
        val d = PlaybackPriority.decide(sig(2, growing = true), normal, "v.avi", "usb")
        assertTrue(d.cpuRelief, "lower priority and small slices help the decoder")
        assertFalse(d.backgroundThreads, "but it stays out of the closed-loop hold: it feeds the video")
        assertEquals(0L, d.receiveCapBps)
    }

    @Test fun restOrPausedPlayerNeverGetsRelief() {
        assertFalse(PlaybackPriority.decide(PlaybackSignal("paused", videoDistress = 2), normal, "x", "usb").cpuRelief)
    }

    private class Port : ThreadPriorityPort { var down = 0; var up = 0; override fun background() { down++ }; override fun normal() { up++ } }

    @Test fun theReceiveThreadReadsSmallSlicesAndSleepsAndRestoresItsPriority() {
        var t = 1_000_000L; val slept = arrayListOf<Long>(); val port = Port()
        val g = PlaybackGovernor({ sig(2, growing = true) }, normal, port, clock = { t }, sleep = { slept += it; t += it }, drainer = { it() })
        g.receive("v.avi", "usb").use { rx ->
            assertEquals(1, port.down, "the feeding copy's thread is lowered too")
            assertTrue(rx.readCap() in 4_096..65_536)
            repeat(10) { rx.onBytes(16 * 1024) }
        }
        assertEquals(1, port.up)
        assertTrue(slept.sum() > 0, "a short pause after each slice")
        val idle = PlaybackGovernor({ PlaybackSignal() }, normal, port, clock = { t }, sleep = { slept += it }, drainer = { it() })
        idle.receive("a", "usb").use { rx -> assertEquals(Int.MAX_VALUE, rx.readCap()) }
    }
}
