package castbridge.core.quiz.online

import kotlin.test.*

/** Owner addition 2026-10-03: « entre 2 questions laisse 1 ou 2 s de latence pour pouvoir synchroniser les parties en ligne ». */
class PlayTimingTest {
    private var serverNow = 100_000L   // the SERVER clock (fake); no client clock exists in these functions

    @Test fun defaultsAndBounds() {
        assertEquals(1_500L, PlayTiming.INTER_QUESTION_GAP_MS)
        assertEquals(1_000L, PlayTiming.MIN_GAP_MS); assertEquals(2_000L, PlayTiming.MAX_GAP_MS)
    }

    @Test fun gapIsClampedToOneToTwoSeconds() {
        listOf(Long.MIN_VALUE to 1_000L, -5L to 1_000L, 0L to 1_000L, 999L to 1_000L, 1_000L to 1_000L, 1_500L to 1_500L, 2_000L to 2_000L,
            2_001L to 2_000L, 60_000L to 2_000L, Long.MAX_VALUE to 2_000L).forEach { (asked, got) -> assertEquals(got, PlayTiming.gap(asked), "gap($asked)") }
    }

    @Test fun lanAndTvOnlyKeepTheirCurrentBehaviourWithNoGap() {
        assertEquals(0L, PlayTiming.gapFor(PlayScope.TV_ONLY)); assertEquals(0L, PlayTiming.gapFor(PlayScope.LAN))
        assertEquals(0L, PlayTiming.gapFor(PlayScope.LAN, 2_000L), "even if a gap is requested, a local game is unchanged")
        assertEquals(1_500L, PlayTiming.gapFor(PlayScope.INTERNET))
        assertEquals(2_000L, PlayTiming.gapFor(PlayScope.INTERNET, 9_999L)); assertEquals(1_000L, PlayTiming.gapFor(PlayScope.INTERNET, 10L))
        assertEquals(serverNow, PlayTiming.opensAt(serverNow, PlayScope.LAN)); assertEquals(serverNow, PlayTiming.opensAt(serverNow, PlayScope.TV_ONLY))
    }

    @Test fun opensAtIsRevealPlusGapOnTheServerClock() {
        assertEquals(serverNow + 1_500, PlayTiming.opensAt(serverNow, PlayScope.INTERNET))
        assertEquals(serverNow + 1_000, PlayTiming.opensAt(serverNow, PlayScope.INTERNET, 0))
        assertEquals(serverNow + 2_000, PlayTiming.opensAt(serverNow, PlayScope.INTERNET, 5_000))
        val a = PlayTiming.announce("q42", serverNow, PlayScope.INTERNET)
        assertEquals("q42", a.questionId); assertEquals(serverNow + 1_500, a.opensAtServerMs)
    }

    @Test fun noAnswerIsAcceptedBeforeOpensAt() {
        val opens = PlayTiming.opensAt(serverNow, PlayScope.INTERNET)
        assertEquals(PlayTiming.Gate.TOO_EARLY, PlayTiming.gate(serverNow, opens))
        assertEquals(PlayTiming.Gate.TOO_EARLY, PlayTiming.gate(opens - 1, opens))
        assertEquals(PlayTiming.Gate.OPEN, PlayTiming.gate(opens, opens))
        assertEquals(PlayTiming.Gate.OPEN, PlayTiming.gate(opens + 3_000, opens))
        assertNull(PlayTiming.scoringElapsedMs(opens, opens - 1), "an early answer has no scoring time")
    }

    @Test fun scoringStartsAtOpensAtNeverAtMessageArrival() {
        val opens = PlayTiming.opensAt(serverNow, PlayScope.INTERNET)
        // the announcement reached the players at serverNow (1,5 s ahead); the answer arrives 2 s after opensAt
        assertEquals(2_000L, PlayTiming.scoringElapsedMs(opens, opens + 2_000))
        assertEquals(0L, PlayTiming.scoringElapsedMs(opens, opens))
    }

    @Test fun countdownLabelIsFrench() {
        assertEquals("Question suivante dans 1,5 s", PlayTiming.countdownLabel(1_500))
        assertEquals("Question suivante dans 1 s", PlayTiming.countdownLabel(1_000))
        assertEquals("Question suivante dans 0,1 s", PlayTiming.countdownLabel(40))
        assertEquals("Question suivante dans 2 s", PlayTiming.countdownLabel(2_000))
        assertNull(PlayTiming.countdownLabel(0)); assertNull(PlayTiming.countdownLabel(-3))
    }

    @Test fun onTimeAnnouncementWaitsTheFullGapThenGetsTheFullWindow() {
        val opens = PlayTiming.opensAt(serverNow, PlayScope.INTERNET)
        val s = PlayTiming.start(opens, windowMs = 20_000, announcementServerNowMs = serverNow)
        assertEquals(1_500L, s.waitMs); assertEquals(20_000L, s.remainingMs); assertFalse(s.startNow)
    }

    @Test fun lateAnnouncementStartsAtOnceAndShortensTheWindowOnlyByServerMeasuredTime() {
        val opens = PlayTiming.opensAt(serverNow, PlayScope.INTERNET)
        // the server stamps the announcement with ITS clock: 600 ms after opensAt
        val s = PlayTiming.start(opens, windowMs = 20_000, announcementServerNowMs = opens + 600)
        assertTrue(s.startNow); assertEquals(0L, s.waitMs); assertEquals(19_400L, s.remainingMs)
        val gone = PlayTiming.start(opens, windowMs = 20_000, announcementServerNowMs = opens + 25_000)
        assertTrue(gone.startNow); assertEquals(0L, gone.remainingMs)
        val exact = PlayTiming.start(opens, windowMs = 20_000, announcementServerNowMs = opens)
        assertTrue(exact.startNow); assertEquals(20_000L, exact.remainingMs)
    }
}
