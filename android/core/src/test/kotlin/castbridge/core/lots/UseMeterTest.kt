package castbridge.core.lots

import kotlin.test.*

/** W16 (w16-02): the pure use counter. Fake monotone clock (milliseconds), no Android, no wall clock. */
class UseMeterTest {
    private val s = 1000L
    private val min = 60_000L
    private val lotA = LotId("learn", "cm2")
    private val lotB = LotId("learn", "cm1")

    @Test fun fiftyNineSecondsTwiceCountsOneMinuteAndCarriesTheRest() {
        val m = UseMeter()
        m.open(lotA, 0)
        assertEquals(Tick(lotA, 0), m.tick(59 * s), "59 s: no whole minute")
        assertEquals(Tick(lotA, 1), m.tick(118 * s), "118 s: one minute, 58 s carried")
        assertEquals(Tick(lotA, 0), m.tick(119 * s), "59 s of carry: still nothing")
        assertEquals(Tick(lotA, 1), m.tick(178 * s), "59 s + 59 s = 118 s: one more minute, 58 s carried")
    }

    @Test fun nothingIsEverRoundedUpNorCountedTwice() {
        val m = UseMeter()
        m.open(lotA, 0)
        var total = 0
        for (i in 1..600) {
            val now = i * 10 * s                                            // a tick every 10 s for 100 minutes
            total += m.tick(now).minutes
            if (i % 60 == 0) m.input(now)                                   // a key every 10 minutes keeps it alive
        }
        assertEquals(100, total, "100 minutes of use give exactly 100 minutes")
    }

    @Test fun tenOpeningsOfThirtySecondsCostFiveMinutes() {
        val m = UseMeter()
        var total = 0
        repeat(10) { i -> val t = i * 100 * s; m.open(lotA, t); total += m.close(t + 30 * s).minutes }
        assertEquals(5, total, "the carry of a lot survives close (never persisted): 10 x 30 s = 5 minutes, not 0")
    }

    @Test fun openingAndClosingEvery59SecondsStillAddsUp() {
        val m = UseMeter()
        var total = 0
        repeat(60) { i -> val t = i * 100 * s; m.open(lotA, t); total += m.close(t + 59 * s).minutes }
        assertEquals(59, total, "60 x 59 s = 3540 s = 59 minutes")
    }

    @Test fun theCarryNeverMovesToAnotherLot() {
        val free = LotId("learn", "langues-fr")
        val m = UseMeter()
        m.open(lotA, 0)
        assertEquals(Tick(lotA, 0), m.open(free, 59 * s))
        assertEquals(Tick(free, 0), m.open(lotA, 60 * s), "1 s on the free lot: nothing, and nothing taken from A")
        assertEquals(Tick(lotA, 1), m.tick(61 * s), "A's own 59 s + 1 s: A is debited its minute")
        val n = UseMeter(); var onA = 0; var onFree = 0
        n.open(lotA, 0)
        for (i in 0 until 50) {                                         // alternate A 59 s / free lot 1 s: A must be debited about 98 %
            val t = i * 60 * s
            val f = n.open(free, t + 59 * s); onA += f.minutes
            val g = n.open(lotA, t + 60 * s); onFree += g.minutes
        }
        assertTrue(onA >= 48, "A debited $onA of 49 minutes of real use")
        assertEquals(0, onFree)
    }

    @Test fun closeFlushesTheWholeMinutesAndTheMeterIsEmptyAfter() {
        val m = UseMeter()
        m.open(lotA, 0)
        assertEquals(Tick(lotA, 2), m.close(150 * s))
        assertEquals(Tick(null, 0), m.tick(10 * min), "nothing open after close")
    }

    @Test fun pauseLongerThanFiveMinutesStopsCounting() {
        val m = UseMeter()
        m.open(lotA, 0)
        m.pause(2 * min)                                                    // 2 min of use before the pause
        assertEquals(Tick(lotA, 2 + 5), m.tick(20 * min), "the first 5 minutes of a pause still count, then it stops")
        m.resume(20 * min)
        assertEquals(Tick(lotA, 3), m.tick(23 * min), "after resume it counts again, nothing of the long pause")
        val n = UseMeter(); n.open(lotA, 0); n.pause(min); n.resume(3 * min)
        assertEquals(Tick(lotA, 5), n.tick(5 * min), "a short pause counts entirely")
    }

    @Test fun idleThirtyMinutesStopsCounting() {
        val m = UseMeter()
        m.open(lotA, 0)
        assertEquals(Tick(lotA, 30), m.tick(3 * 60 * min), "three hours without a key: only the first 30 minutes")
        assertEquals(Tick(lotA, 0), m.tick(4 * 60 * min), "still idle: nothing")
        m.input(4 * 60 * min)
        assertEquals(Tick(lotA, 10), m.tick(4 * 60 * min + 10 * min), "a key press restarts the counter")
    }

    @Test fun inputKeepsTheCounterAliveIndefinitely() {
        val m = UseMeter()
        m.open(lotA, 0)
        var total = 0
        for (i in 1..20) { m.input(i * 20 * min); total += m.tick(i * 20 * min).minutes }
        assertEquals(400, total)
    }

    @Test fun monotonicGoingBackwardsNeverGivesNegative() {
        val m = UseMeter()
        m.open(lotA, 10 * min)
        assertEquals(Tick(lotA, 2), m.tick(12 * min))
        assertEquals(Tick(lotA, 0), m.tick(5 * min), "the clock went back: zero, never negative")
        assertEquals(Tick(lotA, 1), m.tick(6 * min), "counting resumes from the new reading, the minutes already given are not given again")
        assertEquals(Tick(lotA, 0), m.close(3 * min))
        m.input(1); m.pause(0); m.resume(0)
        assertEquals(Tick(null, 0), m.tick(0))
    }

    @Test fun inputCountsTheSilenceBeforeTheKeyPress() {
        val m = UseMeter()
        m.open(lotA, 0)
        m.input(60 * min)                                               // 30 min of silence: only 30 counted, the key press restarts the window
        assertEquals(Tick(lotA, 40), m.tick(70 * min), "30 before the key press + 10 after, not 70")
    }

    @Test fun resumeCountsOnlyFiveMinutesOfAPause() {
        val m = UseMeter()
        m.open(lotA, 0)
        m.pause(1 * min); m.resume(20 * min)
        assertEquals(Tick(lotA, 7), m.tick(21 * min), "1 min + 5 min of pause + 1 min after resume, not 21")
    }

    @Test fun rollbackMovesTheWindowsBackInsteadOfOpeningFreshOnes() {
        val m = UseMeter()
        m.open(lotA, 100 * min)
        m.tick(125 * min)                                               // 25 min since the last key press
        m.tick(105 * min)                                               // the reading goes back 20 min: the window moves back with it
        assertEquals(Tick(lotA, 5), m.tick(135 * min), "only 5 of the 30 minutes of the window were left, not 30 new ones")
        val p = UseMeter(); p.open(lotA, 100 * min); p.pause(100 * min); p.tick(103 * min); p.tick(0)
        assertEquals(Tick(lotA, 2), p.tick(10 * min), "3 min of the 5-minute pause were used: 2 left after the rollback")
    }

    @Test fun aTwoHourFilmWithoutAnyKeyCountsTwoHours() {
        val m = UseMeter()
        m.open(lotA, 0); m.startPlayback(0, 2 * 60 * min)
        var total = 0
        for (i in 1..120) total += m.tick(i * min, playing = true).minutes
        assertEquals(120, total, "playback is activity: no 30-minute stop")
        for (i in 121..400) total += m.tick(i * min, playing = true).minutes
        assertEquals(150, total, "then at most the length of the work plus 30 minutes")
    }

    @Test fun anAutomaticNextWorkDoesNotRenewTheWindow() {
        val m = UseMeter()
        m.open(lotA, 0); m.startPlayback(0, 60 * min)                   // window: 60 + 30 min
        m.tick(60 * min, playing = true)
        m.startPlayback(60 * min, 60 * min, userInitiated = false)      // autoplay of the next episode
        assertEquals(Tick(lotA, 30), m.tick(300 * min, playing = true), "stops at 90 min in all (60 already counted + 30)")
        m.startPlayback(300 * min, 60 * min)                            // the viewer chooses another work: new window
        assertEquals(Tick(lotA, 60), m.tick(360 * min, playing = true), "a chosen work counts again")
    }

    @Test fun pauseKeepsItsFiveMinuteLimitDuringPlayback() {
        val m = UseMeter()
        m.open(lotA, 0); m.startPlayback(0, 120 * min)
        m.pause(10 * min)
        assertEquals(Tick(lotA, 15), m.tick(60 * min), "10 min played + 5 min of pause, then stop")
        m.resume(60 * min)
        assertEquals(Tick(lotA, 10), m.tick(70 * min, playing = true), "playing again")
    }

    @Test fun resumeShiftsThePlaybackWindowByTheRealLengthOfThePause() {
        val m = UseMeter()
        m.open(lotA, 0); m.startPlayback(0, 120 * min)                  // a 2-hour film
        var total = 0
        for (i in 1..59) total += m.tick(i * min, playing = true).minutes
        m.pause(60 * min); total += m.tick(120 * min).minutes           // a pause of 60 minutes: 1 min of play + 5 of pause
        m.resume(120 * min)
        for (i in 121..180) total += m.tick(i * min, playing = true).minutes
        assertEquals(60 + 5 + 60, total, "the 60 last minutes of the film count: the window moved by the pause")
    }

    @Test fun aKeyDuringADeclaredPauseDoesNotLiftIt() {
        val m = UseMeter()
        m.open(lotA, 0); m.pause(1 * min)
        m.input(3 * min)                                                // a D-pad key on the pause menu: the player did not resume
        assertEquals(Tick(lotA, 1 + 5), m.tick(20 * min), "1 min + 5 min of pause, then stop, whatever keys were pressed")
        val n = UseMeter(); n.open(lotA, 0); n.pause(1 * min); n.resume(2 * min); n.input(10 * min)
        assertEquals(Tick(lotA, 12), n.tick(12 * min), "after resume a key is an ordinary key press: counting goes on")
    }

    @Test fun aPauseIsAKeyPressForTheIdleWindow() {
        val m = UseMeter()
        m.open(lotA, 0); m.startPlayback(0, 240 * min)
        m.pause(100 * min)                                              // 100 minutes after the last key: the pause itself is a key press
        assertEquals(Tick(lotA, 105), m.tick(150 * min), "100 min played + 5 min of pause")
    }

    @Test fun playbackOfAnUnknownDurationFallsBackToThreeHoursPlusIdle() {
        val m = UseMeter()
        m.open(lotA, 0); m.startPlayback(0, null)
        var total = 0
        for (i in 1..400) total += m.tick(i * min, playing = true).minutes
        assertEquals(3 * 60 + 30, total)
    }

    @Test fun hugeGapNeverExceedsTheLedgerBound() {
        val m = UseMeter()
        m.open(lotA, 0)
        val t = m.tick(Long.MAX_VALUE / 2)
        assertTrue(t.minutes in 0..1440, "at most 1440: ${t.minutes}")
        assertEquals(30, t.minutes, "bounded by the inactivity stop")
    }

    @Test fun tickWithoutOpenLotIsZero() {
        val m = UseMeter()
        assertEquals(Tick(null, 0), m.tick(10 * min))
        assertEquals(Tick(null, 0), m.close(20 * min))
        m.pause(30 * min); m.resume(40 * min); m.input(50 * min)
        assertEquals(Tick(null, 0), m.tick(60 * min), "pause/resume/input without a lot do nothing")
    }

    @Test fun rebootRecreatesAnEmptyMeter() {
        val before = UseMeter(); before.open(lotA, 100 * min)
        assertEquals(1, before.tick(101 * min).minutes)
        val after = UseMeter()                                              // power cut: nothing is persisted, elapsedRealtime restarts at 0
        assertEquals(Tick(null, 0), after.tick(0)); assertEquals(Tick(null, 0), after.tick(5 * min), "nothing opened yet: nothing counted, nothing counted twice")
        after.open(lotA, 6 * min)
        assertEquals(Tick(lotA, 1), after.tick(7 * min))
    }

    @Test fun switchingLotAttributesEachMinuteToTheLotReallyOpen() {
        val m = UseMeter()
        m.open(lotA, 0)
        assertEquals(Tick(lotA, 2), m.open(lotB, 150 * s), "the 2 whole minutes spent on A are flushed to A when B opens")
        assertEquals(Tick(lotB, 1), m.tick(150 * s + 70 * s), "B gets what follows (70 s)")
    }

    @Test fun twoLotsEachKeepTheirOwnCarry() {
        val m = UseMeter()
        m.open(lotA, 0)
        val a = m.open(lotB, 30 * s); val b = m.close(60 * s)
        assertEquals(0, a.minutes + b.minutes, "30 s on each lot: no whole minute on either")
        m.open(lotA, 100 * s)
        assertEquals(Tick(lotA, 1), m.close(130 * s), "A's first 30 s were kept: 30 + 30 s")
    }

    @Test fun reopeningTheSameLotKeepsCounting() {
        val m = UseMeter()
        m.open(lotA, 0)
        assertEquals(0, m.open(lotA, 90 * s).minutes, "same lot again: nothing flushed, carry kept")
        assertEquals(Tick(lotA, 2), m.tick(150 * s), "150 s in all")
    }
}
