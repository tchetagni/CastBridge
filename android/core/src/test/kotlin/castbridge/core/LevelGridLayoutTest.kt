package castbridge.core

import castbridge.core.quiz.*
import castbridge.core.quiz.LevelGridLayout.Dir
import castbridge.core.quiz.QuizLevelAvailability.Counts
import castbridge.core.quiz.QuizLevelAvailability.Kind
import kotlin.test.*

/** The level grid of CastBridge-TV (1280x720 @160 dpi): fixed compact cards, wrapped rows, every card reachable with the D-pad. */
class LevelGridLayoutTest {
    @Test fun columnsFollowTheScreenWidth() {
        assertEquals(5, LevelGridLayout.columns(1280))
        assertEquals(1, LevelGridLayout.columns(360)); assertEquals(1, LevelGridLayout.columns(0)); assertEquals(1, LevelGridLayout.columns(100))
        assertEquals(9, LevelGridLayout.columns(1920))
        assertEquals(3, LevelGridLayout.columns(800, cardWidthDp = 200))
        for (w in listOf(480, 720, 960, 1280, 1920)) {
            val c = LevelGridLayout.columns(w)
            assertTrue(c == 1 || c * LevelGridLayout.CARD_DP + (c - 1) * LevelGridLayout.GAP_DP + LevelGridLayout.MARGIN_DP <= w, "w=$w c=$c")
        }
    }

    @Test fun fourteenLevelsAreTwoToThreeRows() {
        val c = LevelGridLayout.columns(1280)
        assertTrue((14 + c - 1) / c in 2..3); assertTrue((12 + c - 1) / c in 2..3); assertTrue((24 + c - 1) / c <= 5)
    }

    private fun reach(count: Int, cols: Int, dirs: List<Dir>): Set<Int> {
        val seen = HashSet<Int>(); val todo = ArrayDeque(listOf(0)); seen += 0
        while (todo.isNotEmpty()) { val i = todo.removeFirst(); for (d in dirs) LevelGridLayout.move(i, d, count, cols)?.let { if (seen.add(it)) todo += it } }
        return seen
    }

    @Test fun everyLevelIsReachableForAnyCountAndWidth() {
        for (count in listOf(1, 4, 12, 13, 14, 24)) for (cols in listOf(1, 3, 5, 6)) {
            assertEquals((0 until count).toSet(), reach(count, cols, Dir.values().toList()), "count=$count cols=$cols")
            assertEquals((0 until count).toSet(), reach(count, cols, listOf(Dir.RIGHT)), "RIGHT alone visits all: count=$count cols=$cols")
            assertEquals((0 until count).toSet(), reach(count, cols, listOf(Dir.DOWN, Dir.RIGHT)), "DOWN/RIGHT: count=$count cols=$cols")
        }
    }

    @Test fun focusOrderWrapsRowsCoherently() {
        assertEquals(5, LevelGridLayout.move(4, Dir.RIGHT, 14, 5)); assertEquals(4, LevelGridLayout.move(5, Dir.LEFT, 14, 5))
        assertNull(LevelGridLayout.move(13, Dir.RIGHT, 14, 5)); assertNull(LevelGridLayout.move(0, Dir.LEFT, 14, 5))
        assertEquals(1, LevelGridLayout.move(0, Dir.RIGHT, 14, 5)); assertEquals(5, LevelGridLayout.move(0, Dir.DOWN, 14, 5))
        assertEquals(0, LevelGridLayout.move(5, Dir.UP, 14, 5)); assertNull(LevelGridLayout.move(2, Dir.UP, 14, 5))
        // down from a column with no card in the last (partial) row lands on the last card; the last row stays put
        assertEquals(13, LevelGridLayout.move(9, Dir.DOWN, 14, 5)); assertEquals(13, LevelGridLayout.move(8, Dir.DOWN, 14, 5))
        assertNull(LevelGridLayout.move(5, Dir.DOWN, 14, 5).let { LevelGridLayout.move(it!!, Dir.DOWN, 14, 5) })
        assertNull(LevelGridLayout.move(12, Dir.DOWN, 14, 5))
        assertEquals(23, LevelGridLayout.move(22, Dir.RIGHT, 24, 6)); assertEquals(18, LevelGridLayout.move(12, Dir.DOWN, 24, 6))
        assertEquals(3, LevelGridLayout.move(2, Dir.DOWN, 5, 1)); assertEquals(3, LevelGridLayout.move(2, Dir.RIGHT, 5, 1))
        for (i in 0 until 14) for (d in Dir.values()) LevelGridLayout.move(i, d, 14, 5)?.let { assertTrue(it in 0 until 14) }
    }

    private fun st(kind: Kind, total: Int, approved: Int = 0, alias: Boolean = false, trialCount: Int = 0) =
        QuizLevelAvailability.State(QuizCatalog.Level("CM2", "CM2", Track.PRIMARY), kind, Counts(approved, total - approved),
            if (alias) QuizLevelAvailability.aliases.first() else null, trialCount)

    @Test fun compactCardsAreOneShortLine() {
        assertEquals("2 000 questions", QuizLevelAvailability.compactText(st(Kind.AVAILABLE, 2000)))
        assertEquals("20 questions", QuizLevelAvailability.compactText(st(Kind.AVAILABLE, 20, 20)))
        assertEquals("Réservé", QuizLevelAvailability.compactText(st(Kind.RESERVED, 0)))
        assertEquals("bientôt", QuizLevelAvailability.compactText(st(Kind.SOON, 0)))
        assertEquals("800 questions", QuizLevelAvailability.compactText(st(Kind.AVAILABLE, 800, trialCount = 240)))
        assertEquals("2 000 questions", QuizLevelAvailability.compactText(st(Kind.AVAILABLE, 2000, alias = true)), "the alias is said in the detail line")
        for (n in listOf(7, 120, 2000, 12000)) assertTrue(QuizLevelAvailability.compactText(st(Kind.AVAILABLE, n)).length <= 20)
    }

    @Test fun detailLineCarriesTheLongText() {
        val d = QuizLevelAvailability.detailText(st(Kind.AVAILABLE, 2000, alias = true), goal = 30)
        assertTrue(d.startsWith("CM2 : "), d)
        assertTrue("2000 questions" in d && "30 parties sans répétition garanties" in d && "mêmes questions que le CP" in d && "contenu en cours de relecture" in d, d)
        assertFalse('\n' in d, "one line")
        assertTrue("dont 240 questions en essai" in QuizLevelAvailability.detailText(st(Kind.AVAILABLE, 800, trialCount = 240), 30))
        assertEquals("CM2 : Réservé · en location", QuizLevelAvailability.detailText(st(Kind.RESERVED, 0), 30))
        assertEquals("CM2 : bientôt", QuizLevelAvailability.detailText(st(Kind.SOON, 0), 30))
    }
}
