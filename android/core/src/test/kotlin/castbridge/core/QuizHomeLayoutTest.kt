package castbridge.core

import castbridge.core.quiz.LevelGridLayout.Dir
import castbridge.core.quiz.QuizHomeLayout
import kotlin.test.*

/** The Quiz home: the old 2-per-row look, a flow that wraps instead of overflowing, a D-pad that never wraps around inside a row. */
class QuizHomeLayoutTest {
    @Test fun oldLookIsTwoPerRowOnATv() {
        assertEquals(listOf(2, 2, 1), QuizHomeLayout.rows(1280, QuizHomeLayout.CARD_DP, 5))
        assertEquals(listOf(2, 2, 2), QuizHomeLayout.rows(1280, QuizHomeLayout.CARD_DP, 6))
        assertEquals(listOf(1), QuizHomeLayout.rows(1280, QuizHomeLayout.CARD_DP, 1))
        assertEquals(emptyList(), QuizHomeLayout.rows(1280, QuizHomeLayout.CARD_DP, 0))
        assertEquals(QuizHomeLayout.CARD_DP, QuizHomeLayout.cardWidthDp(1280, 2))
    }

    @Test fun narrowScreensWrapInsteadOfOverflowing() {
        assertEquals(listOf(1, 1, 1), QuizHomeLayout.rows(300, QuizHomeLayout.CARD_DP, 3))
        assertEquals(listOf(2, 2, 2), QuizHomeLayout.rows(640, QuizHomeLayout.CARD_DP, 6))
    }

    @Test fun everyCardIsInsideTheSafeAreaAndNeverTooNarrow() {
        for (w in listOf(480, 640, 720, 960, 1280, 1920)) for (n in 1..8) {
            val rows = QuizHomeLayout.rows(w, QuizHomeLayout.CARD_DP, n)
            assertEquals(n, rows.sum(), "w=$w n=$n")
            val card = QuizHomeLayout.cardWidthDp(w, rows.max())
            assertTrue(card >= QuizHomeLayout.MIN_CARD_DP, "w=$w n=$n card=$card")
            for (perRow in rows) {
                val rowWidth = perRow * (card + QuizHomeLayout.SLOT_MARGIN_DP)
                assertTrue(rowWidth <= w * 0.9, "w=$w n=$n row=$rowWidth")
            }
        }
    }

    @Test fun dpadStaysInsideItsRowAndKeepsTheColumn() {
        val rows = listOf(2, 2, 2)
        assertNull(QuizHomeLayout.move(0, Dir.LEFT, rows)); assertNull(QuizHomeLayout.move(1, Dir.RIGHT, rows))
        assertNull(QuizHomeLayout.move(2, Dir.LEFT, rows)); assertEquals(1, QuizHomeLayout.move(0, Dir.RIGHT, rows))
        assertEquals(3, QuizHomeLayout.move(1, Dir.DOWN, rows)); assertEquals(1, QuizHomeLayout.move(3, Dir.UP, rows))
        assertNull(QuizHomeLayout.move(0, Dir.UP, rows)); assertNull(QuizHomeLayout.move(5, Dir.DOWN, rows))
        // partial last row: the closest column
        assertEquals(4, QuizHomeLayout.move(3, Dir.DOWN, listOf(2, 2, 1)))
        assertEquals(2, QuizHomeLayout.move(4, Dir.UP, listOf(2, 2, 1)))
        assertNull(QuizHomeLayout.move(9, Dir.UP, rows))
    }
}
