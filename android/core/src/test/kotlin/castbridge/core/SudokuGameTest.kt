package castbridge.core

import castbridge.core.sudoku.Sudoku
import castbridge.core.sudoku.SudokuGame
import castbridge.core.sudoku.SudokuRecords
import kotlin.test.*

class SudokuGameTest {
    private fun game(level: Sudoku.Difficulty = Sudoku.Difficulty.EASY, seed: Long = 7) = SudokuGame.create(level, java.util.Random(seed))

    @Test fun everyLevelHasAUniqueSolutionAndAGradedNumberOfGivens() {
        val givens = Sudoku.Difficulty.values().map { d ->
            var worst = 0
            for (seed in 1L..5L) {
                val g = game(d, seed)
                assertTrue(Sudoku.hasUniqueSolution(g.puzzle), "${d.label} seed $seed not unique")
                assertTrue(Sudoku.isSolved(g.solution))
                for (i in 0 until 81) if (g.puzzle[i] != 0) assertEquals(g.puzzle[i], g.solution[i])
                worst = maxOf(worst, g.puzzle.count { it != 0 })
            }
            assertTrue(worst <= d.clues + 3, "${d.label}: $worst givens for a target of ${d.clues}")
            worst
        }
        assertTrue(givens.zipWithNext().all { (a, b) -> a > b }, "each level must be sparser than the previous one: $givens")
    }

    @Test fun generationIsFast() {
        val t = System.nanoTime()
        repeat(20) { Sudoku.generate(Sudoku.Difficulty.EXPERT, java.util.Random(it.toLong())) }
        assertTrue((System.nanoTime() - t) / 1_000_000 < 20_000, "20 expert grids should take seconds at most")
    }

    @Test fun solutionsDetectsMultipleAndNoSolution() {
        val g = game()
        val open = g.puzzle.copyOf()
        (0 until 81).filter { open[it] != 0 }.take(40).forEach { open[it] = 0 }
        assertEquals(2, Sudoku.solutions(open, 2))
        val bad = g.puzzle.copyOf()
        val e = bad.indexOfFirst { it == 0 }
        val given = (0 until 9).map { e / 9 * 9 + it }.firstOrNull { bad[it] != 0 } ?: error("no given in the row")
        bad[e] = bad[given]                       // same digit twice in a row: no solution
        assertEquals(0, Sudoku.solutions(bad))
        assertNull(Sudoku.solve(bad))
    }

    @Test fun playingAndWinning() {
        val g = game()
        val empties = (0 until 81).filter { !g.isGiven(it) }
        val first = empties[0]
        val wrong = (1..9).first { it != g.solution[first] }
        assertTrue(g.setDigit(first, wrong))
        assertEquals(setOf(first), g.wrongCells())
        assertFalse(g.setDigit(first, wrong), "same digit: no change")
        assertTrue(g.clear(first)); assertTrue(g.wrongCells().isEmpty())
        val given = (0 until 81).first { g.isGiven(it) }
        assertFalse(g.setDigit(given, (1..9).first { it != g.puzzle[given] }), "a given cannot change")
        empties.forEach { g.setDigit(it, g.solution[it]) }
        assertTrue(g.won)
        assertFalse(g.clear(empties[0]), "no move after the win")
    }

    @Test fun notesAreKeptAndClearedByPlacedDigits() {
        val g = game()
        val e = (0 until 81).first { !g.isGiven(it) }
        assertTrue(g.toggleNote(e, 4)); assertTrue(g.hasNote(e, 4))
        assertTrue(g.toggleNote(e, 4)); assertFalse(g.hasNote(e, 4))
        g.fillNotes()
        for (i in 0 until 81) if (g.board[i] == 0) assertEquals(Sudoku.candidates(g.board, i), (1..9).filter { g.hasNote(i, it) }.toSet())
        val peer = Sudoku.peers(e).first { !g.isGiven(it) && g.board[it] == 0 }
        g.notes[peer] = g.notes[peer] or (1 shl g.solution[e])
        g.setDigit(e, g.solution[e])
        assertFalse(g.hasNote(peer, g.solution[e]), "the placed digit leaves the notes of its peers")
        assertEquals(0, g.notes[e])
    }

    @Test fun hintsAreLimited() {
        val g = game()
        repeat(SudokuGame.MAX_HINTS) { assertNotNull(g.hint()) }
        assertEquals(0, g.hintsLeft)
        assertNull(g.hint(), "no hint left")
        assertEquals(SudokuGame.MAX_HINTS, g.hintsUsed)
    }

    @Test fun hintRevealsTheRightDigitInTheGivenCell() {
        val g = game()
        val e = (0 until 81).last { !g.isGiven(it) }
        assertEquals(e, g.hint(e)); assertEquals(g.solution[e], g.board[e])
    }

    @Test fun saveAndRestoreKeepsEverything() {
        val g = game(Sudoku.Difficulty.HARD, 3)
        val es = (0 until 81).filter { !g.isGiven(it) }
        g.setDigit(es[0], g.solution[es[0]]); g.toggleNote(es[1], 2); g.toggleNote(es[1], 7); g.hint(es[2]); g.addTime(75_000)
        val r = SudokuGame.decode(g.encode())!!
        assertEquals(g.level, r.level); assertContentEquals(g.puzzle, r.puzzle); assertContentEquals(g.board, r.board)
        assertContentEquals(g.notes, r.notes); assertContentEquals(g.solution, r.solution)
        assertEquals(75_000, r.elapsedMs); assertEquals(1, r.hintsUsed)
    }

    @Test fun corruptedSavesAreRefused() {
        val g = game(); val ok = g.encode()
        assertNull(SudokuGame.decode(null)); assertNull(SudokuGame.decode("")); assertNull(SudokuGame.decode("garbage"))
        assertNull(SudokuGame.decode(ok.replace("S1|", "S2|")))
        assertNull(SudokuGame.decode(ok.replace("|EASY|", "|IMPOSSIBLE|")))
        val f = ok.split("|").toMutableList()
        val k = f[5].indexOfFirst { it != '0' }
        f[5] = f[5].substring(0, k) + (if (f[5][k] == '5') '6' else '5') + f[5].substring(k + 1)   // a given altered
        assertNull(SudokuGame.decode(f.joinToString("|")))
        assertNull(SudokuGame.decode(ok.substring(0, ok.length - 5)))
    }

    @Test fun aFinishedGameIsNotResumed() {
        val g = game(); (0 until 81).forEach { if (!g.isGiven(it)) g.setDigit(it, g.solution[it]) }
        assertNull(SudokuGame.decode(g.encode()))
    }

    @Test fun bestTimesPerLevel() {
        val r = SudokuRecords()
        assertNull(r.best(Sudoku.Difficulty.EASY))
        assertTrue(r.record(Sudoku.Difficulty.EASY, 300_000, 0))
        assertFalse(r.record(Sudoku.Difficulty.EASY, 400_000, 0))
        assertTrue(r.record(Sudoku.Difficulty.EASY, 250_000, 0))
        assertFalse(r.record(Sudoku.Difficulty.HARD, 100_000, 1), "a game with a hint is not a record")
        assertNull(r.best(Sudoku.Difficulty.HARD))
        assertTrue(r.record(Sudoku.Difficulty.EXPERT, 900_000, 0))
        val back = SudokuRecords.decode(r.encode())
        assertEquals(250_000L, back.best(Sudoku.Difficulty.EASY)); assertEquals(900_000L, back.best(Sudoku.Difficulty.EXPERT))
        assertNull(SudokuRecords.decode("EASY=abc;BOGUS=5;;EXPERT=-3").best(Sudoku.Difficulty.EXPERT))
        assertEquals("3:07", SudokuRecords.clock(187_000)); assertEquals("1:02:15", SudokuRecords.clock(3_735_000))
    }
}
