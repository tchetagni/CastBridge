package castbridge.core.xfer

import castbridge.core.xfer.VideoStallDetector.Level
import kotlin.test.*

/**
 * R-16 : l'image figée alors que le son continue (AVI décodé en logiciel sur 4 petits coeurs). Horloge fausse, compteurs libVLC simulés.
 * Ce qui n'est PAS prouvé ici : que libVLC de la vraie TV renseigne displayedPictures / lostPictures (voir docs/test-plans P-52).
 */
class VideoStallDetectorTest {
    private val d = VideoStallDetector()
    private var t = 1_000_000L
    private var audio = 0L
    private var shown = 0
    private var lost = 0

    /** Avance de [sec] secondes : le son avance toujours (sauf [audioMoves] faux), [fps] images affichées par seconde dont [loss] perdues. */
    private fun run(sec: Int, fps: Int = 25, loss: Int = 0, audioMoves: Boolean = true) {
        repeat(sec * 4) { i ->
            t += 250
            if (audioMoves) { audio += 250; d.onAudioTime(t, audio) }
            if (i % 4 == 3) { shown += fps; lost += loss; d.onStats(t, shown, lost) }
        }
    }

    @Test fun smoothVideoIsOk() {
        d.onPlaying(t); run(10)
        assertEquals(Level.OK, d.level(t)); assertFalse(d.sustained(t))
    }

    @Test fun aFrozenPictureWithAdvancingAudioIsDetectedAfterOneAndAHalfSecond() {
        d.onPlaying(t); run(8)
        run(1, fps = 0); assertEquals(Level.OK, d.level(t), "1 s only")
        run(1, fps = 0); assertEquals(Level.FROZEN, d.level(t), "2 s without a new picture while the audio runs")
    }

    @Test fun aPausedPlayerIsNotAFreezeAndResumingGivesAGrace() {
        d.onPlaying(t); run(8)
        d.onPaused(); t += 20_000
        assertEquals(Level.OK, d.level(t))
        d.onPlaying(t); run(3, fps = 0)                     // the decoder restarts: nothing shown for 3 s
        assertEquals(Level.OK, d.level(t), "startup grace")
    }

    @Test fun theStartupGraceCoversTheFirstSecondsWithoutPictures() {
        d.onPlaying(t); run(3, fps = 0)
        assertEquals(Level.OK, d.level(t))
        run(3, fps = 0)
        assertEquals(Level.FROZEN, d.level(t), "the grace is over and still no picture")
    }

    @Test fun anAudioStallIsNotAVideoFreeze() {
        d.onPlaying(t); run(8)
        run(4, fps = 0, audioMoves = false)                  // buffering: audio and video both stopped
        assertEquals(Level.OK, d.level(t))
    }

    @Test fun aFileWithoutVideoIsNeverFrozen() {
        d.onPlaying(t)
        repeat(40) { t += 250; audio += 250; d.onAudioTime(t, audio); if (it % 4 == 3) d.onStats(t, 0, 0, hasVideo = false) }
        assertEquals(Level.OK, d.level(t))
    }

    @Test fun aSeekDoesNotLookLikeAFreeze() {
        d.onPlaying(t); run(8)
        audio += 600_000; d.onAudioTime(t + 10, audio)       // jump of the audio clock
        run(1, fps = 0)
        assertEquals(Level.OK, d.level(t))
    }

    @Test fun droppedFramesAboveFivePercentAreDistress() {
        d.onPlaying(t); run(6)
        run(6, fps = 22, loss = 3)                           // 12 % lost
        assertEquals(Level.DROPPING, d.level(t)); assertTrue(d.droppedRatio() > 0.05)
    }

    @Test fun fourPercentDroppedIsNotDistress() {
        d.onPlaying(t); run(6)
        run(8, fps = 24, loss = 1)
        assertEquals(Level.OK, d.level(t)); assertTrue(d.droppedRatio() < 0.05)
    }

    @Test fun distressLingersAfterTheVideoRecoversThenClears() {
        d.onPlaying(t); run(8); run(3, fps = 0)
        assertEquals(Level.FROZEN, d.level(t))
        run(2)                                               // pictures flow again
        assertEquals(Level.DROPPING, d.level(t), "the copy must not jump back to full speed at once")
        run(VideoStallDetector.LINGER_MS.toInt() / 1000 + 6)
        assertEquals(Level.OK, d.level(t))
    }

    @Test fun distressBecomesSustainedAfterTenSecondsAndRecovers() {
        d.onPlaying(t); run(8)
        run(6, fps = 0); assertFalse(d.sustained(t))
        run(8, fps = 0); assertTrue(d.sustained(t))
        run(40); assertFalse(d.sustained(t))
    }

    @Test fun staleStatsMeanUnknownNotFrozen() {
        d.onPlaying(t); run(8); run(2, fps = 0)
        t += 10_000; audio += 10_000; d.onAudioTime(t, audio)  // libVLC stopped reporting its statistics
        assertNotEquals(Level.FROZEN, d.level(t))
    }
}
