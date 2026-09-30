package castbridge.core

import castbridge.core.sudoku.Sudoku
import kotlin.test.*

class SudokuTest {
    private fun grid(vararg rows: String): IntArray {
        require(rows.size == 9)
        return IntArray(81) { i ->
            val ch = rows[i / 9][i % 9]
            if (ch == '.') 0 else ch - '0'
        }
    }

    private val solved = grid(
        "534678912", "672195348", "198342567", "859761423", "426853791", "713924856",
        "961537284", "287419635", "345286179",
    )

    @Test fun validationDetectsRowColumnAndBoxConflicts() {
        val g = grid("5........", "5........", ".........", ".........", ".........", ".........", ".........", ".........", ".........")
        assertFalse(Sudoku.isValid(g, 9, 5), "same column")
        assertFalse(Sudoku.isValid(g, 1, 5), "same row")
        assertFalse(Sudoku.isValid(g, 10, 5), "same box")
        assertTrue(Sudoku.isValid(g, 30, 5), "no conflict")
    }

    @Test fun solvedGridIsRecognizedAndHasNoMistakes() {
        assertTrue(Sudoku.isSolved(solved))
        assertTrue(Sudoku.mistakes(solved).isEmpty())
    }

    @Test fun mistakesFindsWrongCells() {
        val g = solved.copyOf().also { it[0] = 9 }
        assertTrue(0 in Sudoku.mistakes(g))
        assertFalse(Sudoku.isSolved(g))
    }

    @Test fun candidatesExcludeUsedDigits() {
        // row 0 already has 5,3,4,6,7,8,9,1,2 -> only a filled cell; use a fresh grid
        val g = IntArray(81)
        assertEquals(setOf(1, 2, 3, 4, 5, 6, 7, 8, 9), Sudoku.candidates(g, 0))
        g[1] = 1; g[9] = 2; g[10] = 3
        assertFalse(1 in Sudoku.candidates(g, 0))
        assertFalse(2 in Sudoku.candidates(g, 0))
        assertFalse(3 in Sudoku.candidates(g, 0))
        assertTrue(4 in Sudoku.candidates(g, 0))
    }

    @Test fun solverCompletesAValidPuzzle() {
        val puzzle = solved.copyOf().also { g -> (0 until 81).forEach { if (it % 3 == 0) g[it] = 0 } }
        val out = Sudoku.solve(puzzle)
        assertNotNull(out)
        assertTrue(Sudoku.isSolved(out!!))
        for (i in 0 until 81) if (puzzle[i] != 0) assertEquals(puzzle[i], out[i], "solver must respect the givens")
    }

    @Test fun solutionsCountsMultiples() {
        val empty = IntArray(81)
        assertTrue(Sudoku.solutions(empty, 2) >= 2)
        assertEquals(1, Sudoku.solutions(solved, 2))
    }

    @Test fun generatedPuzzlesAreUniqueValidAndRespectDifficulty() {
        val rnd = java.util.Random(42)
        val clues = Sudoku.Difficulty.values().associateWith { d ->
            val p = Sudoku.generate(d, rnd)
            assertTrue(Sudoku.hasUniqueSolution(p), "${d.label}: not unique")
            assertFalse(Sudoku.isSolved(p), "${d.label}: already solved")
            assertTrue(p.count { it != 0 } in 17..d.clues + 4, "${d.label}: ${p.count { it != 0 }} givens")
            p.count { it != 0 }
        }
        val order = Sudoku.Difficulty.values()
        for (i in 1 until order.size) assertTrue(clues[order[i - 1]]!! >= clues[order[i]]!!, "givens must not increase with difficulty")
        assertTrue(clues[Sudoku.Difficulty.EASY]!! > clues[Sudoku.Difficulty.EXPERT]!!, "expert must be sparser than easy")
    }

    @Test fun filledGridsAreValid() {
        for (seed in 0 until 10) {
            val g = Sudoku.filled(java.util.Random(seed.toLong()))
            assertTrue(Sudoku.isSolved(g))
        }
    }
}
