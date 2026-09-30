package castbridge.core.sudoku

import java.util.Random

/**
 * Sudoku engine (pure Kotlin, no dependency): generation with a unique solution, candidate lists, validation, solving
 * and the solution counter that guarantees uniqueness. The grid is a flat [IntArray] of 81 cells (row-major), 0 = empty.
 *
 * Difficulty is expressed as the number of *target* clues: the generator fills a complete grid, then removes cells one
 * by one (in a random order) as long as the puzzle keeps exactly one solution, stopping at the target. The resulting
 * puzzle has at most [Difficulty.clues] givens and is always solvable with a unique solution.
 */
object Sudoku {
    const val SIZE = 9
    const val CELLS = 81
    const val EMPTY = 0

    /** Number of givens aimed at for each difficulty (a maximum: uniqueness may stop earlier). */
    enum class Difficulty(val label: String, val clues: Int) {
        EASY("Facile", 46),
        MEDIUM("Moyen", 36),
        HARD("Difficile", 30),
        EXPERT("Expert", 26),
    }

    /** Which cells would conflict with [value] at [index] (excluding [index] itself). */
    fun conflicts(grid: IntArray, index: Int, value: Int): List<Int> = buildList {
        val r = index / 9; val c = index % 9
        for (i in 0 until 9) {
            val ri = r * 9 + i; if (ri != index && grid[ri] == value) add(ri)
            val ci = i * 9 + c; if (ci != index && grid[ci] == value) add(ci)
        }
        val br = (r / 3) * 3; val bc = (c / 3) * 3
        for (i in 0 until 3) for (j in 0 until 3) {
            val k = (br + i) * 9 + (bc + j)
            if (k != index && grid[k] == value) add(k)
        }
    }

    /** The 20 cells sharing a row, column or box with [index]. */
    fun peers(index: Int): List<Int> = PEERS[index]

    private val PEERS: Array<List<Int>> = Array(CELLS) { i ->
        val r = i / 9; val c = i % 9
        (0 until CELLS).filter { k -> k != i && (k / 9 == r || k % 9 == c || (k / 9 / 3 == r / 3 && k % 9 / 3 == c / 3)) }
    }

    fun isValid(grid: IntArray, index: Int, value: Int): Boolean = conflicts(grid, index, value).isEmpty()

    /** The digits 1..9 that can still go into [index] given the rest of the grid. */
    fun candidates(grid: IntArray, index: Int): Set<Int> = (1..9).filter { isValid(grid, index, it) }.toSet()

    /** A grid is complete and respects every rule. */
    fun isSolved(grid: IntArray): Boolean {
        if (grid.any { it !in 1..9 }) return false
        for (i in 0 until CELLS) if (conflicts(grid, i, grid[i]).isNotEmpty()) return false
        return true
    }

    /** Every wrong cell of a *full* grid (a number that breaks its row, column or box). */
    fun mistakes(grid: IntArray): Set<Int> = buildSet {
        if (grid.any { it == EMPTY }) return@buildSet
        for (i in 0 until CELLS) if (conflicts(grid, i, grid[i]).isNotEmpty()) add(i)
    }

    /**
     * Number of solutions of [grid], counted up to [limit] (counting stops as soon as [limit] is reached). A puzzle is
     * fair only when this returns 1 with limit 2. A grid that already breaks a rule has 0 solutions.
     */
    fun solutions(grid: IntArray, limit: Int = 2): Int {
        val s = Search.of(grid) ?: return 0
        var count = 0
        s.run { count++; count >= limit }
        return count
    }

    fun hasUniqueSolution(grid: IntArray): Boolean = solutions(grid, 2) == 1

    /** Solves [grid], or returns null when it has no solution. The first solution found is returned. */
    fun solve(grid: IntArray): IntArray? {
        val s = Search.of(grid) ?: return null
        var out: IntArray? = null
        s.run { out = it.copyOf(); true }
        return out
    }

    /**
     * Backtracking over bit masks (one mask per row, column and box), always expanding the empty cell that has the fewest
     * candidates: an Expert puzzle is checked in well under a millisecond, which matters on a 1 GB TV box.
     */
    private class Search private constructor(val g: IntArray, val rows: IntArray, val cols: IntArray, val boxes: IntArray) {
        companion object {
            fun of(grid: IntArray): Search? {
                if (grid.size != CELLS) return null
                val s = Search(grid.copyOf(), IntArray(9), IntArray(9), IntArray(9))
                for (i in 0 until CELLS) {
                    val v = s.g[i]
                    if (v == EMPTY) continue
                    if (v !in 1..9) return null
                    val bit = 1 shl v
                    val r = i / 9; val c = i % 9; val b = (r / 3) * 3 + c / 3
                    if ((s.rows[r] or s.cols[c] or s.boxes[b]) and bit != 0) return null
                    s.rows[r] = s.rows[r] or bit; s.cols[c] = s.cols[c] or bit; s.boxes[b] = s.boxes[b] or bit
                }
                return s
            }
        }

        /** Calls [onSolution] for each solution; stops when it returns true. @return true if stopped. */
        fun run(onSolution: (IntArray) -> Boolean): Boolean {
            var best = -1; var bestMask = 0; var bestN = 10
            for (i in 0 until CELLS) {
                if (g[i] != EMPTY) continue
                val r = i / 9; val c = i % 9
                val free = (rows[r] or cols[c] or boxes[(r / 3) * 3 + c / 3]).inv() and 0x3FE
                val n = Integer.bitCount(free)
                if (n < bestN) { best = i; bestMask = free; bestN = n; if (n <= 1) break }
            }
            if (best < 0) return onSolution(g)
            if (bestN == 0) return false
            val r = best / 9; val c = best % 9; val b = (r / 3) * 3 + c / 3
            var m = bestMask
            while (m != 0) {
                val bit = m and -m; m = m xor bit
                g[best] = Integer.numberOfTrailingZeros(bit)
                rows[r] = rows[r] or bit; cols[c] = cols[c] or bit; boxes[b] = boxes[b] or bit
                val stop = run(onSolution)
                rows[r] = rows[r] xor bit; cols[c] = cols[c] xor bit; boxes[b] = boxes[b] xor bit
                g[best] = EMPTY
                if (stop) return true
            }
            return false
        }
    }

    /** A complete, valid grid (a solved puzzle), filled at random. */
    fun filled(random: Random = Random()): IntArray {
        val g = IntArray(CELLS)
        fill(g, random)
        return g
    }

    /**
     * A puzzle with a unique solution and at most [difficulty] givens. Removal ("digging") runs in several passes: a cell
     * that could not be removed early on may become removable once its neighbours are gone, so the loop restarts until the
     * target is reached or nothing more can be removed.
     */
    fun generate(difficulty: Difficulty, random: Random = Random()): IntArray {
        val puzzle = filled(random)
        var givens = CELLS
        while (givens > difficulty.clues) {
            var removedAny = false
            for (p in (0 until CELLS).toList().shuffled(random)) {
                if (givens <= difficulty.clues) break
                if (puzzle[p] == EMPTY) continue
                val backup = puzzle[p]
                puzzle[p] = EMPTY
                if (solutions(puzzle, 2) == 1) { givens--; removedAny = true } else puzzle[p] = backup
            }
            if (!removedAny) break
        }
        return puzzle
    }

    /** One row of the grid as a string of digits/dots, for logs and compact tests. */
    fun row(grid: IntArray, r: Int): String = (0 until 9).joinToString("") { (grid[r * 9 + it].let { c -> if (c == 0) "." else c.toString() }) }

    private fun fill(g: IntArray, random: Random): Boolean {
        val idx = g.indexOfFirst { it == EMPTY }
        if (idx < 0) return true
        val values = (1..9).toList().shuffled(random)
        for (v in values) {
            if (!isValid(g, idx, v)) continue
            g[idx] = v
            if (fill(g, random)) return true
            g[idx] = EMPTY
        }
        return false
    }
}
