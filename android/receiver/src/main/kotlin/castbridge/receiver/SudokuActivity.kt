package castbridge.receiver

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import castbridge.core.sudoku.Sudoku

/**
 * « Sudoku » on the TV, played with the remote: arrows move the cursor, the digit keys 1-9 set a value (or toggle a
 * note), 0/BACK clears the cell, MENU opens the menu (new game, difficulty, hint, notes, quit). One puzzle has a unique
 * solution (core/sudoku). Everything is drawn on one Canvas.
 */
class SudokuActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("castbridge_sudoku", MODE_PRIVATE) }
    private var difficulty = Sudoku.Difficulty.EASY
    private var puzzle = IntArray(81)
    private var board = IntArray(81)
    private var notes = Array(81) { HashSet<Int>() }
    private var notesMode = false
    private var cursor = 40
    private var menu: Menu? = null
    private var won = false
    private var flash: String? = null
    private var flashUntil = 0L
    private lateinit var view: BoardView

    private class Menu(val title: String, val items: List<Pair<String, () -> Unit>>, val onBack: () -> Unit) {
        var focus = 0
        fun move(d: Int) { focus = (focus + d + items.size) % items.size }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        difficulty = runCatching { Sudoku.Difficulty.valueOf(prefs.getString("difficulty", Sudoku.Difficulty.EASY.name)!!) }.getOrDefault(Sudoku.Difficulty.EASY)
        view = BoardView()
        setContentView(view)
        newGame()
    }

    private fun newGame() {
        puzzle = Sudoku.generate(difficulty)
        board = puzzle.copyOf()
        notes = Array(81) { HashSet() }
        notesMode = false; cursor = 40; won = false; menu = null
        view.invalidate()
    }

    private fun isGiven(i: Int) = puzzle[i] != 0

    private fun setDigit(i: Int, v: Int) {
        if (isGiven(i)) return
        if (notesMode) {
            if (!notes[i].remove(v)) notes[i].add(v)
        } else {
            board[i] = v
            notes[i].clear()
            if (Sudoku.isSolved(board)) { won = true; menu = null }
        }
        view.invalidate()
    }

    private fun clearCell(i: Int) { if (!isGiven(i)) { board[i] = 0; notes[i].clear(); view.invalidate() } }

    private fun hint() {
        val idx = (0 until 81).firstOrNull { !isGiven(it) && board[it] == 0 } ?: run { flash("Plus rien à révéler"); return }
        val sol = Sudoku.solve(board) ?: run { flash("Grille impossible"); return }
        setDigit(idx, sol[idx])
        flash("Une case révélée")
    }

    private fun showMenu() {
        menu = Menu("Sudoku", listOf(
            "Nouvelle partie" to { newGame() },
            "Difficulté : ${difficulty.label}" to {
                val d = Sudoku.Difficulty.values()
                difficulty = d[(d.indexOf(difficulty) + 1) % d.size]
                prefs.edit().putString("difficulty", difficulty.name).apply()
                showMenu()
            },
            "Mode notes : ${if (notesMode) "activé" else "coupé"}" to { notesMode = !notesMode; showMenu() },
            "Indice (révéler une case)" to { menu = null; hint() },
            "Quitter" to { finish() },
        )) { menu = null; view.invalidate() }
        view.invalidate()
    }

    private fun flash(m: String) { flash = m; flashUntil = System.currentTimeMillis() + 3500; view.invalidate() }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)
        val k = event.keyCode
        menu?.let { m ->
            when {
                k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN -> { m.move(if (k == KeyEvent.KEYCODE_DPAD_DOWN) 1 else -1); view.invalidate() }
                k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_BUTTON_A -> { val it = m.items[m.focus]; menu = null; it.second() }
                k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_ESCAPE || k == KeyEvent.KEYCODE_MENU || k == KeyEvent.KEYCODE_BUTTON_B -> m.onBack()
                else -> return super.dispatchKeyEvent(event)
            }
            return true
        }
        val dx = when (k) { KeyEvent.KEYCODE_DPAD_LEFT -> -1; KeyEvent.KEYCODE_DPAD_RIGHT -> 1; else -> 0 }
        val dy = when (k) { KeyEvent.KEYCODE_DPAD_UP -> -1; KeyEvent.KEYCODE_DPAD_DOWN -> 1; else -> 0 }
        when {
            won -> { if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) newGame(); return true }
            dx != 0 || dy != 0 -> {
                val r = cursor / 9; val c = cursor % 9
                cursor = (r + dy).coerceIn(0, 8) * 9 + (c + dx).coerceIn(0, 8)
                view.invalidate(); return true
            }
            k == KeyEvent.KEYCODE_MENU -> { showMenu(); return true }
            k in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                val v = k - KeyEvent.KEYCODE_0
                if (v == 0) clearCell(cursor) else setDigit(cursor, v)
                return true
            }
            k == KeyEvent.KEYCODE_DEL || k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_ESCAPE || k == KeyEvent.KEYCODE_BUTTON_B -> { clearCell(cursor); return true }
        }
        return super.dispatchKeyEvent(event)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { /* handled in dispatchKeyEvent */ }

    // ------------------------------------------------------------------ the board

    private inner class BoardView : View(this@SudokuActivity) {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val text = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(c: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            fill.shader = LinearGradient(0f, 0f, 0f, h, 0xFF16202C.toInt(), 0xFF07090D.toInt(), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, h, fill); fill.shader = null
            val side = minOf(w, h)
            val cell = side / 9f
            val ox = (w - side) / 2f
            val oy = (h - side) / 2f
            // cell backgrounds
            for (i in 0 until 81) {
                val r = i / 9; val col = i % 9
                val x = ox + col * cell; val y = oy + r * cell
                fill.color = when {
                    i == cursor -> 0xFF24435E.toInt()
                    (r / 3 + col / 3) % 2 == 0 -> 0xFF111B28.toInt()
                    else -> 0xFF16233A.toInt()
                }
                c.drawRect(x, y, x + cell, y + cell, fill)
            }
            // wrong cells in red
            for (i in 0 until 81) if (!isGiven(i) && board[i] != 0 && Sudoku.conflicts(board, i, board[i]).isNotEmpty()) {
                val r = i / 9; val col = i % 9
                fill.color = 0x40E0302A; c.drawRect(ox + col * cell, oy + r * cell, ox + (col + 1) * cell, oy + (r + 1) * cell, fill)
            }
            // grid lines
            for (i in 0..9) {
                line.strokeWidth = if (i % 3 == 0) 4f else 1f
                line.color = if (i % 3 == 0) 0xFF8896B0.toInt() else 0xFF33415A.toInt()
                c.drawLine(ox, oy + i * cell, ox + side, oy + i * cell, line)
                c.drawLine(ox + i * cell, oy, ox + i * cell, oy + side, line)
            }
            // numbers and notes
            for (i in 0 until 81) {
                val r = i / 9; val col = i % 9
                val x = ox + col * cell; val y = oy + r * cell
                if (board[i] != 0) {
                    text.textSize = cell * 0.55f
                    text.typeface = if (isGiven(i)) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    text.color = if (isGiven(i)) 0xFFF2F4F8.toInt() else 0xFF33B5E5.toInt()
                    text.textAlign = Paint.Align.CENTER
                    c.drawText(board[i].toString(), x + cell / 2, y + cell / 2 - (text.ascent() + text.descent()) / 2, text)
                } else if (notes[i].isNotEmpty()) {
                    text.textSize = cell * 0.28f
                    text.typeface = Typeface.DEFAULT; text.color = 0xFFA9B4C6.toInt(); text.textAlign = Paint.Align.LEFT
                    val ps = notes[i].sorted()
                    for (k in ps.indices) {
                        val n = ps[k]
                        c.drawText(n.toString(), x + (k % 3) * cell / 3 + cell * 0.06f, y + (k / 3 + 1) * cell / 3, text)
                    }
                }
            }
            // header
            text.textSize = cell * 0.42f; text.typeface = Typeface.DEFAULT_BOLD; text.color = 0xFFA9B4C6.toInt(); text.textAlign = Paint.Align.LEFT
            c.drawText("Sudoku · ${difficulty.label}", ox, maxOf(24f, oy - cell * 0.2f), text)
            text.textAlign = Paint.Align.RIGHT
            c.drawText(if (notesMode) "Mode notes" else "Chiffres", ox + side, maxOf(24f, oy - cell * 0.2f), text)
            // win overlay
            if (won) {
                fill.color = 0xC0000000.toInt(); c.drawRect(0f, 0f, w, h, fill)
                text.textSize = side * 0.09f; text.color = 0xFFFFC53D.toInt(); text.textAlign = Paint.Align.CENTER; text.typeface = Typeface.DEFAULT_BOLD
                c.drawText("Bravo, grille terminée !", w / 2, h / 2, text)
                text.textSize = side * 0.045f; text.color = 0xFFF2F4F8.toInt(); text.typeface = Typeface.DEFAULT
                c.drawText("OK : nouvelle partie", w / 2, h / 2 + side * 0.08f, text)
            }
            // menu
            menu?.let { m -> drawMenu(c, m, w, h) }
            flash?.takeIf { System.currentTimeMillis() < flashUntil }?.let { msg ->
                text.textSize = side * 0.045f; text.color = 0xFFFFB74D.toInt(); text.textAlign = Paint.Align.CENTER; text.typeface = Typeface.DEFAULT_BOLD
                c.drawText(msg, w / 2, h - 24f, text)
            }
        }

        private fun drawMenu(c: Canvas, m: Menu, w: Float, h: Float) {
            val rowH = 56f
            val boxH = m.items.size * rowH + 60f
            val boxW = minOf(w * 0.7f, 520f)
            val l = (w - boxW) / 2; val t = (h - boxH) / 2
            fill.color = 0xF0141B26.toInt(); c.drawRoundRect(l, t, l + boxW, t + boxH, 16f, 16f, fill)
            text.textSize = 26f; text.typeface = Typeface.DEFAULT_BOLD; text.color = 0xFFFFC53D.toInt(); text.textAlign = Paint.Align.CENTER
            c.drawText(m.title, w / 2, t + 40f, text)
            for (i in m.items.indices) {
                val y = t + 60f + i * rowH
                if (i == m.focus) { fill.color = 0xFF33B5E5.toInt(); c.drawRoundRect(l + 8f, y, l + boxW - 8f, y + rowH - 8f, 10f, 10f, fill) }
                text.textSize = 22f; text.typeface = Typeface.DEFAULT
                text.color = if (i == m.focus) 0xFF06131C.toInt() else 0xFFF2F4F8.toInt(); text.textAlign = Paint.Align.LEFT
                c.drawText(m.items[i].first, l + 24f, y + rowH / 2 + 8f, text)
            }
        }
    }
}
