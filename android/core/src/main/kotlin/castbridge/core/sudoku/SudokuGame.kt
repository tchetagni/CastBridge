package castbridge.core.sudoku

import java.util.Random

/**
 * One Sudoku game in progress (pure Kotlin, testable on the JVM): the puzzle, the player's numbers, the pencil notes
 * (candidates), the clock, the hints used. The TV screen and the phone remote both drive this same object.
 *
 * The puzzle has a unique solution ([Sudoku.generate]), so "wrong" means exactly "different from the solution":
 * [wrongCells] is the error check, and the game is won when the board equals the solution.
 */
class SudokuGame private constructor(
    val level: Sudoku.Difficulty,
    val puzzle: IntArray,
    val solution: IntArray,
) {
    val board: IntArray = puzzle.copyOf()
    /** Notes per cell: bit v (1..9) set = digit v is noted. */
    val notes = IntArray(Sudoku.CELLS)
    var elapsedMs = 0L
        private set
    var hintsUsed = 0
        private set
    var won = false
        private set

    fun isGiven(i: Int) = puzzle[i] != Sudoku.EMPTY

    val hintsLeft get() = MAX_HINTS - hintsUsed

    /** Puts [v] (1..9) in cell [i], clearing its notes; ignored on a given or once the game is won. @return true if changed. */
    fun setDigit(i: Int, v: Int): Boolean {
        if (won || isGiven(i) || v !in 1..9 || board[i] == v) return false
        board[i] = v; notes[i] = 0
        // a placed digit disappears from the notes of its row, column and box
        for (k in Sudoku.peers(i)) notes[k] = notes[k] and (1 shl v).inv()
        checkWin()
        return true
    }

    fun clear(i: Int): Boolean {
        if (won || isGiven(i) || (board[i] == 0 && notes[i] == 0)) return false
        board[i] = 0; notes[i] = 0
        return true
    }

    /** Adds or removes the note [v] of an empty cell. */
    fun toggleNote(i: Int, v: Int): Boolean {
        if (won || isGiven(i) || board[i] != 0 || v !in 1..9) return false
        notes[i] = notes[i] xor (1 shl v)
        return true
    }

    fun hasNote(i: Int, v: Int) = notes[i] and (1 shl v) != 0

    /** Fills the notes of every empty cell with the digits that still fit (the "brouillon" helper). */
    fun fillNotes() {
        if (won) return
        for (i in 0 until Sudoku.CELLS) notes[i] = if (board[i] != 0) 0 else Sudoku.candidates(board, i).fold(0) { m, v -> m or (1 shl v) }
    }

    /** Filled cells (not givens) that differ from the solution. */
    fun wrongCells(): Set<Int> = (0 until Sudoku.CELLS).filter { !isGiven(it) && board[it] != 0 && board[it] != solution[it] }.toSet()

    /** Cells of the current board that break a rule right now (same digit in a row, column or box). */
    fun conflictCells(): Set<Int> = (0 until Sudoku.CELLS).filter { board[it] != 0 && Sudoku.conflicts(board, it, board[it]).isNotEmpty() }.toSet()

    /**
     * Reveals the solution of cell [i] (or of the first empty / wrong cell when [i] is null). Limited to [MAX_HINTS] per game.
     * @return the cell revealed, or null (no hint left, nothing to reveal).
     */
    fun hint(i: Int? = null): Int? {
        if (won || hintsUsed >= MAX_HINTS) return null
        val cell = i?.takeIf { !isGiven(it) && board[it] != solution[it] }
            ?: (0 until Sudoku.CELLS).firstOrNull { !isGiven(it) && board[it] != solution[it] }
            ?: return null
        hintsUsed++
        board[cell] = solution[cell]; notes[cell] = 0
        for (k in Sudoku.peers(cell)) notes[k] = notes[k] and (1 shl solution[cell]).inv()
        checkWin()
        return cell
    }

    /** Adds played time (the screen calls it from its clock while the game is visible and not won). */
    fun addTime(ms: Long) { if (!won && ms > 0) elapsedMs += ms }

    val filled get() = board.count { it != 0 }

    private fun checkWin() { if (board.contentEquals(solution)) won = true }

    // ---------------------------------------------------------------- saving

    /**
     * One line of text for SharedPreferences: `S1|LEVEL|elapsedMs|hints|puzzle(81 digits)|board(81 digits)|notes(81 x 3 hex)`.
     * Only a game in progress is worth saving; the solution is recomputed at [decode].
     */
    fun encode(): String = listOf("S1", level.name, elapsedMs, hintsUsed, digits(puzzle), digits(board),
        notes.joinToString("") { it.toString(16).padStart(3, '0') }).joinToString("|")

    companion object {
        const val MAX_HINTS = 3

        /** A new game: generates a puzzle with a unique solution (can take a moment: call it off the main thread). */
        fun create(level: Sudoku.Difficulty, random: Random = Random()): SudokuGame {
            val p = Sudoku.generate(level, random)
            return SudokuGame(level, p, Sudoku.solve(p)!!)
        }

        /** A game from a given [puzzle] (must have a unique solution), or null. */
        fun of(level: Sudoku.Difficulty, puzzle: IntArray): SudokuGame? {
            if (puzzle.size != Sudoku.CELLS || !Sudoku.hasUniqueSolution(puzzle)) return null
            return SudokuGame(level, puzzle.copyOf(), Sudoku.solve(puzzle) ?: return null)
        }

        /** The game saved by [encode], or null for anything malformed, inconsistent or already finished. */
        fun decode(text: String?): SudokuGame? = runCatching {
            val f = text!!.split("|")
            if (f.size != 7 || f[0] != "S1") return null
            val level = Sudoku.Difficulty.valueOf(f[1])
            val puzzle = undigits(f[4]) ?: return null
            val board = undigits(f[5]) ?: return null
            if (f[6].length != Sudoku.CELLS * 3) return null
            val g = of(level, puzzle) ?: return null
            for (i in 0 until Sudoku.CELLS) {
                if (puzzle[i] != 0 && board[i] != puzzle[i]) return null        // a given was altered
                g.board[i] = board[i]
                g.notes[i] = if (board[i] != 0) 0 else f[6].substring(i * 3, i * 3 + 3).toInt(16) and 0x3FE
            }
            g.elapsedMs = f[2].toLong().coerceIn(0, 100L * 3_600_000)
            g.hintsUsed = f[3].toInt().coerceIn(0, MAX_HINTS)
            g.checkWin()
            if (g.won) return null
            g
        }.getOrNull()

        private fun digits(a: IntArray) = a.joinToString("")
        private fun undigits(s: String): IntArray? =
            if (s.length != Sudoku.CELLS || s.any { it !in '0'..'9' }) null else IntArray(Sudoku.CELLS) { s[it] - '0' }
    }
}

/**
 * Best times per level ("Meilleurs temps"), stored as one line `EASY=83000;MEDIUM=…` (milliseconds; a level never won is
 * absent). Fewer hints do not count in the record: a game with hints is not a record.
 */
class SudokuRecords(private val times: MutableMap<Sudoku.Difficulty, Long> = LinkedHashMap()) {
    fun best(level: Sudoku.Difficulty): Long? = times[level]

    /** Records a win; a game that used hints never becomes a record. @return true if it is a new best. */
    fun record(level: Sudoku.Difficulty, ms: Long, hintsUsed: Int): Boolean {
        if (hintsUsed > 0 || ms <= 0) return false
        val old = times[level]
        if (old != null && old <= ms) return false
        times[level] = ms
        return true
    }

    fun encode(): String = times.entries.joinToString(";") { "${it.key.name}=${it.value}" }

    companion object {
        fun decode(text: String?): SudokuRecords {
            val m = LinkedHashMap<Sudoku.Difficulty, Long>()
            text.orEmpty().split(";").forEach { part ->
                val kv = part.split("=")
                if (kv.size != 2) return@forEach
                val d = Sudoku.Difficulty.values().firstOrNull { it.name == kv[0] } ?: return@forEach
                kv[1].toLongOrNull()?.takeIf { it > 0 }?.let { m[d] = it }
            }
            return SudokuRecords(m)
        }

        /** "3:07" / "1:02:15". */
        fun clock(ms: Long): String {
            val s = ms / 1000
            return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
        }
    }
}
