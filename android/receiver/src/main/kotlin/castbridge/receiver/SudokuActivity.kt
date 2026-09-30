package castbridge.receiver

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import castbridge.core.sudoku.Sudoku
import castbridge.core.sudoku.SudokuGame
import castbridge.core.sudoku.SudokuRecords
import castbridge.core.tv.ReceiverServer

/**
 * « Sudoku » on the TV (docs/GAMES.md), played with the remote or from the phone (see [SudokuHub]).
 *
 * Remote: arrows move the cursor, OK opens the digit pad of the cell (digit keys 0-9 also work when the remote has them),
 * BACK opens the menu (resume, check, hint, notes, new game, quit; BACK again leaves: the game is saved at every move).
 * Coloured keys: red = check, green = hint, yellow = notes, blue = menu.
 *
 * Layout: the board is one custom view of fixed proportions (design 1920x1080, see [Dx]); everything else is ordinary views in
 * a column, so text can never be drawn over the board or over other text, at 1280x720 @160 dpi as at 1920x1080 @320 dpi.
 * The puzzle logic, notes, hints, save and records are in core/sudoku ([SudokuGame], [SudokuRecords]).
 */
class SudokuActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var dx: Dx
    private lateinit var store: SudokuStore
    private lateinit var board: BoardView
    private lateinit var records: SudokuRecords

    private var game: SudokuGame? = null
    private var cursor = 40
    private var notesMode = false
    private var wrong: Set<Int> = emptySet()             // result of the last check, until the next move
    private var generating: Sudoku.Difficulty? = null
    private var generationId = 0
    private var resumed = false
    private var lastTick = 0L
    private var ticks = 0

    private lateinit var overlay: FrameLayout
    private var overlayKind: Overlay? = null
    private var overlayBack: () -> Unit = {}

    private lateinit var tvLevel: TextView
    private lateinit var tvTime: TextView
    private lateinit var tvFilled: TextView
    private lateinit var tvHints: TextView
    private lateinit var tvMode: TextView
    private lateinit var tvBest: TextView
    private lateinit var tvMessage: TextView

    private enum class Overlay { START, MENU, PICKER, WIN, BUSY }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        dx = Dx(this)
        store = SudokuStore(this)
        records = store.records()
        buildUi()

        val wanted = Sudoku.Difficulty.values().firstOrNull { it.name == intent?.getStringExtra("level") }
        val saved = store.saved()
        when {
            wanted != null -> startNew(wanted)
            saved != null -> { game = saved; refresh(); showMenu() }
            else -> showStart(canCancel = false)
        }
        SudokuHub.active = this
    }

    // ------------------------------------------------------------------ UI

    private fun buildUi() {
        val root = FrameLayout(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(GamesColors.BG_TOP, GamesColors.BG))
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dx.px(96), dx.px(40), dx.px(96), dx.px(40))
        }
        board = BoardView()
        row.addView(board, LinearLayout.LayoutParams(dx.px(BOARD), dx.px(BOARD)))

        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dx.px(72), 0, 0, 0) }
        fun line(size: Int, color: Int, bold: Boolean = false, top: Int = 0): TextView =
            dx.text(TextView(this).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }, size, color, bold).also {
                info.addView(it, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dx.px(top) })
            }
        line(64, GamesColors.TEXT_HIGH, true).text = "Sudoku"
        tvLevel = line(40, GamesColors.SUDOKU, true, 8)
        line(28, GamesColors.TEXT_MEDIUM, false, 44).text = "Temps"
        tvTime = line(96, GamesColors.TEXT_HIGH, true, 4)
        tvFilled = line(34, GamesColors.TEXT_HIGH, false, 36)
        tvHints = line(34, GamesColors.TEXT_HIGH, false, 12)
        tvMode = line(34, GamesColors.TEXT_HIGH, false, 12)
        tvBest = line(34, GamesColors.TEXT_MEDIUM, false, 12)
        tvMessage = dx.text(TextView(this).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END; minLines = 2 }, 32, GamesColors.PRIMARY, true)
        info.addView(tvMessage, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dx.px(28) })
        info.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        line(26, GamesColors.TEXT_MEDIUM).text = "OK : choisir un chiffre"
        line(26, GamesColors.TEXT_MEDIUM, false, 6).text = "Touches 1 à 9 : saisir  ·  0 : effacer"
        line(26, GamesColors.TEXT_MEDIUM, false, 6).text = "Retour : menu (partie sauvegardée)"
        row.addView(info, LinearLayout.LayoutParams(0, -1, 1f))
        root.addView(row, FrameLayout.LayoutParams(-1, -1))

        overlay = FrameLayout(this).apply { visibility = View.GONE; setBackgroundColor(0xCC0A0F1E.toInt()) }
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
    }

    private fun refresh() {
        val g = game
        tvLevel.text = g?.level?.let { "Niveau : ${it.label}" } ?: ""
        tvTime.text = SudokuRecords.clock(g?.elapsedMs ?: 0)
        tvFilled.text = g?.let { "Cases remplies : ${it.filled} / 81" } ?: ""
        tvHints.text = g?.let { "Indices restants : ${it.hintsLeft} / ${SudokuGame.MAX_HINTS}" } ?: ""
        tvMode.text = "Saisie : " + if (notesMode) "brouillon (notes)" else "chiffres"
        tvBest.text = g?.let { gg -> "Meilleur temps : " + (records.best(gg.level)?.let { SudokuRecords.clock(it) } ?: "—") } ?: ""
        board.invalidate()
    }

    private fun say(m: String) {
        tvMessage.text = m
        main.removeCallbacks(clearMessage); main.postDelayed(clearMessage, 5000)
    }

    private val clearMessage = Runnable { tvMessage.text = "" }

    // ------------------------------------------------------------------ overlays (menus are real views: the D-pad focus works by itself)

    private fun closeOverlay() {
        overlay.removeAllViews(); overlay.visibility = View.GONE; overlayKind = null
        lastTick = SystemClock.elapsedRealtime()
    }

    private fun showPanel(kind: Overlay, title: String, subtitle: String?, back: () -> Unit, build: (LinearLayout) -> Unit, focus: Int = 0) {
        overlay.removeAllViews()
        overlayKind = kind; overlayBack = back
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dx.px(56), dx.px(44), dx.px(56), dx.px(48))
            background = dx.rounded(GamesColors.SURFACE, 36, GamesColors.OUTLINE, 2)
        }
        panel.addView(dx.text(TextView(this).apply { text = title; gravity = Gravity.CENTER; maxLines = 1; ellipsize = TextUtils.TruncateAt.END }, 52, GamesColors.TEXT_HIGH, true),
            LinearLayout.LayoutParams(-1, -2))
        if (subtitle != null) panel.addView(dx.text(TextView(this).apply { text = subtitle; gravity = Gravity.CENTER; maxLines = 2; ellipsize = TextUtils.TruncateAt.END }, 30, GamesColors.TEXT_MEDIUM),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dx.px(10) })
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        panel.addView(body, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dx.px(28) })
        build(body)
        overlay.addView(panel, FrameLayout.LayoutParams(dx.px(820), -2, Gravity.CENTER))
        overlay.visibility = View.VISIBLE
        fun focusables(v: View): List<View> = if (v is android.view.ViewGroup) (0 until v.childCount).flatMap { focusables(v.getChildAt(it)) } else if (v.isFocusable) listOf(v) else emptyList()
        focusables(body).getOrNull(focus)?.requestFocus()
    }

    private fun button(parent: LinearLayout, text: String, enabled: Boolean = true, onClick: () -> Unit): TextView {
        val b = dx.text(TextView(this).apply {
            this.text = text; isFocusable = true; isFocusableInTouchMode = true; isClickable = true; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL; setPadding(dx.px(32), 0, dx.px(32), 0)
            background = dx.focusable(GamesColors.SURFACE_HIGH, GamesColors.BG_TOP, 22)
            setOnClickListener { onClick() }
        }, 36, if (enabled) GamesColors.TEXT_HIGH else GamesColors.TEXT_MEDIUM, true)
        parent.addView(b, LinearLayout.LayoutParams(-1, dx.px(96)).apply { topMargin = dx.px(14) })
        return b
    }

    private fun showStart(canCancel: Boolean) {
        val g = game
        showPanel(Overlay.START, if (g == null) "Sudoku" else "Nouvelle partie", "Choisissez le niveau. Chaque grille a une seule solution.",
            { if (canCancel) showMenu() else finish() }, { body ->
                val r = records
                Sudoku.Difficulty.values().forEach { d ->
                    button(body, d.label + (r.best(d)?.let { "   ·   meilleur ${SudokuRecords.clock(it)}" } ?: "")) { startNew(d) }
                }
            }, focus = Sudoku.Difficulty.values().indexOf(store.level))
    }

    private fun showMenu(focus: Int = 0) {
        val g = game ?: return showStart(false)
        store.save(g)
        showPanel(Overlay.MENU, "Sudoku", "${g.level.label}  ·  ${SudokuRecords.clock(g.elapsedMs)}", { leave() }, { body ->
            button(body, "Reprendre la partie") { closeOverlay() }
            button(body, "Vérifier les erreurs") { closeOverlay(); check() }
            button(body, "Indice  (${g.hintsLeft} restant${if (g.hintsLeft > 1) "s" else ""})", g.hintsLeft > 0) { closeOverlay(); hint() }
            button(body, "Saisie : " + if (notesMode) "brouillon" else "chiffres") { notesMode = !notesMode; refresh(); showMenu(3) }
            button(body, "Remplir le brouillon") { closeOverlay(); g.fillNotes(); notesMode = true; changed(); say("Brouillon rempli : chiffres possibles de chaque case") }
            button(body, "Nouvelle partie…") { showStart(canCancel = true) }
            button(body, "Quitter") { leave() }
        }, focus)
    }

    private fun showPicker() {
        val g = game ?: return
        if (g.isGiven(cursor)) { say("Case de départ : elle ne peut pas changer"); return }
        showPanel(Overlay.PICKER, if (notesMode) "Brouillon : notes" else "Choisir un chiffre", if (notesMode) "OK ajoute ou retire la note" else null,
            { closeOverlay() }, { body ->
                val placed = IntArray(10).also { c -> g.board.forEach { if (it != 0) c[it]++ } }
                for (r in 0 until 3) {
                    val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                    for (c in 0 until 3) {
                        val v = r * 3 + c + 1
                        val done = placed[v] >= 9 && !notesMode
                        val t = dx.text(TextView(this).apply {
                            text = if (notesMode && g.hasNote(cursor, v)) "$v ✓" else "$v"; gravity = Gravity.CENTER
                            isFocusable = true; isFocusableInTouchMode = true; isClickable = true
                            background = dx.focusable(GamesColors.SURFACE_HIGH, GamesColors.BG_TOP, 22)
                            setOnClickListener { enter(v) }
                        }, 60, if (done) GamesColors.OUTLINE else GamesColors.TEXT_HIGH, true)
                        line.addView(t, LinearLayout.LayoutParams(0, dx.px(120), 1f).apply { if (c > 0) leftMargin = dx.px(14) })
                    }
                    body.addView(line, LinearLayout.LayoutParams(-1, -2).apply { if (r > 0) topMargin = dx.px(14) })
                }
                button(body, "Effacer la case") { closeOverlay(); game?.clear(cursor); changed() }
                button(body, if (notesMode) "Passer aux chiffres" else "Passer au brouillon") { notesMode = !notesMode; refresh(); showPicker() }
            }, focus = ((game?.board?.get(cursor) ?: 1).let { if (it in 1..9) it - 1 else 0 }))
    }

    private fun showWin(newBest: Boolean) {
        val g = game ?: return
        showPanel(Overlay.WIN, "Bravo, grille terminée !", "Temps : ${SudokuRecords.clock(g.elapsedMs)}" +
            (if (newBest) "  ·  nouveau record !" else if (g.hintsUsed > 0) "  ·  avec ${g.hintsUsed} indice${if (g.hintsUsed > 1) "s" else ""} (pas de record)" else ""),
            { finish() }, { body ->
                button(body, "Nouvelle partie") { game = null; refresh(); showStart(canCancel = false) }
                button(body, "Quitter") { finish() }
            })
    }

    private fun showBusy(level: Sudoku.Difficulty) {
        showPanel(Overlay.BUSY, "Préparation de la grille…", "Niveau ${level.label}", { generationId++; generating = null; if (game == null) showStart(false) else showMenu() }, { })
    }

    // ------------------------------------------------------------------ game actions

    private fun startNew(level: Sudoku.Difficulty) {
        store.level = level
        abandonIfStarted()
        val id = ++generationId
        generating = level
        showBusy(level)
        Thread({
            val g = runCatching { SudokuGame.create(level) }.getOrNull()
            main.post {
                if (isDestroyed || id != generationId) return@post
                generating = null
                if (g == null) { showStart(game != null); say("Impossible de créer la grille"); return@post }
                game = g; cursor = 40; notesMode = false; wrong = emptySet()
                store.save(g); closeOverlay(); refresh()
            }
        }, "cb-sudoku-gen").apply { isDaemon = true }.start()
    }

    private fun abandonIfStarted() {
        val g = game ?: return
        if (!g.won && (g.filled > g.puzzle.count { it != 0 } || g.elapsedMs > 15_000)) SudokuHub.record(g, "abandon")
    }

    /** The player put [v] in the cursor cell (or noted it). */
    private fun enter(v: Int) {
        val g = game ?: return
        if (g.isGiven(cursor)) { say("Case de départ : elle ne peut pas changer"); return }
        if (notesMode && g.board[cursor] == 0) g.toggleNote(cursor, v) else g.setDigit(cursor, v)
        if (!notesMode || g.board[cursor] != 0) { if (overlayKind == Overlay.PICKER) closeOverlay() }
        else if (overlayKind == Overlay.PICKER) showPicker()
        changed()
    }

    private fun changed() {
        wrong = emptySet()
        val g = game ?: return
        if (g.won) win(g) else store.save(g)
        refresh()
    }

    private fun win(g: SudokuGame) {
        val best = records.record(g.level, g.elapsedMs, g.hintsUsed)
        store.saveRecords(records); store.clearSave()
        SudokuHub.record(g, "win")
        refresh()
        showWin(best)
    }

    private fun check() {
        val g = game ?: return
        wrong = g.wrongCells()
        board.invalidate()
        say(if (wrong.isEmpty()) "Aucune erreur pour l'instant" else "${wrong.size} case${if (wrong.size > 1) "s" else ""} fausse${if (wrong.size > 1) "s" else ""} (en rouge)")
    }

    private fun hint() {
        val g = game ?: return
        val cell = g.hint(cursor.takeIf { !g.isGiven(it) && g.board[it] != g.solution[it] })
        if (cell == null) say(if (g.hintsLeft == 0) "Plus d'indice pour cette grille" else "Rien à révéler")
        else { cursor = cell; say("Une case révélée  ·  ${g.hintsLeft} indice${if (g.hintsLeft > 1) "s" else ""} restant${if (g.hintsLeft > 1) "s" else ""}"); changed() }
    }

    private fun leave() { game?.let { if (!it.won) store.save(it) }; finish() }

    private fun move(dc: Int, dr: Int) {
        val r = Math.floorMod(cursor / 9 + dr, 9); val c = Math.floorMod(cursor % 9 + dc, 9)
        cursor = r * 9 + c
        board.invalidate()
    }

    /** Orders of the phone ([SudokuHub]); runs on the UI thread. @return false for an unknown order. */
    fun remote(cmd: String, v: Int?, level: String?): Boolean {
        val known = setOf("left", "right", "up", "down", "cell", "digit", "clear", "notes", "hint", "check", "autonotes", "menu", "new", "quit")
        if (cmd !in known) return false
        main.post {
            if (overlayKind == Overlay.PICKER || (overlayKind == Overlay.MENU && cmd != "menu" && cmd != "new" && cmd != "quit")) closeOverlay()
            if (overlayKind == Overlay.WIN && cmd != "new" && cmd != "quit") return@post
            val g = game
            when (cmd) {
                "left" -> move(-1, 0); "right" -> move(1, 0); "up" -> move(0, -1); "down" -> move(0, 1)
                "cell" -> { if (v != null && v in 0..80) { cursor = v; board.invalidate() } }
                "digit" -> if (v != null && v in 1..9 && g != null && overlayKind == null) enter(v)
                "clear" -> if (g != null && g.clear(cursor)) changed()
                "notes" -> { notesMode = !notesMode; refresh() }
                "hint" -> hint()
                "check" -> check()
                "autonotes" -> { g?.fillNotes(); notesMode = true; changed() }
                "menu" -> if (overlayKind == Overlay.MENU) closeOverlay() else if (overlayKind == null) showMenu()
                "new" -> Sudoku.Difficulty.values().firstOrNull { it.name == level }?.let { startNew(it) } ?: showStart(game != null)
                "quit" -> leave()
            }
        }
        return true
    }

    /** JSON state for the phone (read from an HTTP thread: plain reads, a slightly stale value is harmless). */
    fun snapshot(): String {
        val g = game
        val head = "{\"open\":true,\"busy\":${generating != null},\"menu\":${overlayKind != null},\"notesMode\":$notesMode,\"cursor\":$cursor"
        if (g == null) return "$head,\"level\":null}"
        return head + ",\"level\":\"${g.level.name}\",\"levelLabel\":${ReceiverServer.q(g.level.label)},\"elapsedMs\":${g.elapsedMs}," +
            "\"filled\":${g.filled},\"hintsLeft\":${g.hintsLeft},\"won\":${g.won}," +
            "\"puzzle\":\"${g.puzzle.joinToString("")}\",\"board\":\"${g.board.joinToString("")}\"," +
            "\"best\":${records.best(g.level) ?: "null"}}"
    }

    // ------------------------------------------------------------------ keys and clock

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        val k = e.keyCode
        if (k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_ESCAPE || k == KeyEvent.KEYCODE_BUTTON_B) {
            if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) {
                if (overlayKind != null) overlayBack() else if (game != null) showMenu() else finish()
            }
            return true
        }
        if (e.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(e)
        if (overlayKind != null) {
            if (overlayKind == Overlay.PICKER && k in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9) { enter(k - KeyEvent.KEYCODE_0); return true }
            if (overlayKind == Overlay.MENU && (k == KeyEvent.KEYCODE_MENU || k == KeyEvent.KEYCODE_PROG_BLUE)) { closeOverlay(); return true }
            return super.dispatchKeyEvent(e)            // the focused button of the panel handles OK and the arrows
        }
        when (k) {
            KeyEvent.KEYCODE_DPAD_LEFT -> move(-1, 0)
            KeyEvent.KEYCODE_DPAD_RIGHT -> move(1, 0)
            KeyEvent.KEYCODE_DPAD_UP -> move(0, -1)
            KeyEvent.KEYCODE_DPAD_DOWN -> move(0, 1)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A -> if (e.repeatCount == 0) showPicker()
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_PROG_BLUE, KeyEvent.KEYCODE_BUTTON_START -> showMenu()
            KeyEvent.KEYCODE_PROG_RED -> check()
            KeyEvent.KEYCODE_PROG_GREEN -> hint()
            KeyEvent.KEYCODE_PROG_YELLOW -> { notesMode = !notesMode; refresh() }
            KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL, KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_NUMPAD_0 -> { if (game?.clear(cursor) == true) changed() }
            in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9 -> enter(k - KeyEvent.KEYCODE_0)
            in KeyEvent.KEYCODE_NUMPAD_1..KeyEvent.KEYCODE_NUMPAD_9 -> enter(k - KeyEvent.KEYCODE_NUMPAD_0)
            else -> return super.dispatchKeyEvent(e)
        }
        return true
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!resumed) return
            val now = SystemClock.elapsedRealtime()
            val g = game
            if (g != null && !g.won && overlayKind == null) {
                g.addTime(now - lastTick)
                tvTime.text = SudokuRecords.clock(g.elapsedMs)
                if (++ticks % 15 == 0) store.save(g)
            }
            lastTick = now
            main.postDelayed(this, 1000)
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true; GamesHub.foreground = this
        lastTick = SystemClock.elapsedRealtime()
        main.removeCallbacks(tick); main.postDelayed(tick, 1000)
    }

    override fun onPause() {
        resumed = false; main.removeCallbacks(tick)
        game?.let { if (!it.won) store.save(it) }
        if (GamesHub.foreground === this) GamesHub.foreground = null
        super.onPause()
    }

    override fun onDestroy() {
        if (SudokuHub.active === this) SudokuHub.active = null
        generationId++
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    // ------------------------------------------------------------------ the board

    private inner class BoardView : View(this@SudokuActivity) {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

        override fun onDraw(c: Canvas) {
            val side = minOf(width, height).toFloat()
            val cell = side / 9f
            val g = game
            val cur = cursor
            val curDigit = g?.board?.get(cur) ?: 0
            // background of the cells: box checkerboard, cursor row/column/box, same digit as the cursor
            for (i in 0 until 81) {
                val r = i / 9; val col = i % 9
                fill.color = when {
                    i == cur -> 0xFF3B4F86.toInt()
                    g != null && curDigit != 0 && g.board[i] == curDigit -> 0xFF2C4A6B.toInt()
                    r == cur / 9 || col == cur % 9 || (r / 3 == cur / 9 / 3 && col / 3 == cur % 9 / 3) -> 0xFF1E2A4A.toInt()
                    (r / 3 + col / 3) % 2 == 0 -> GamesColors.SURFACE
                    else -> GamesColors.SURFACE_HIGH
                }
                c.drawRect(col * cell, r * cell, (col + 1) * cell, (r + 1) * cell, fill)
                if (i in wrong) { fill.color = 0x66FF6B6B; c.drawRect(col * cell, r * cell, (col + 1) * cell, (r + 1) * cell, fill) }
            }
            // grid
            for (i in 0..9) {
                val thick = i % 3 == 0
                line.strokeWidth = if (thick) dx.pxf(5f) else dx.pxf(2f)
                line.color = if (thick) 0xFF8B98B8.toInt() else GamesColors.OUTLINE
                c.drawLine(0f, i * cell, side, i * cell, line); c.drawLine(i * cell, 0f, i * cell, side, line)
            }
            // cursor ring (charter focus ring)
            line.strokeWidth = dx.pxf(7f); line.color = GamesColors.FOCUS_RING
            val cx = (cur % 9) * cell; val cy = (cur / 9) * cell
            val m = line.strokeWidth / 2
            c.drawRect(cx + m, cy + m, cx + cell - m, cy + cell - m, line)
            if (g == null) return
            val conflicts = g.conflictCells()
            for (i in 0 until 81) {
                val x = (i % 9) * cell; val y = (i / 9) * cell
                val v = g.board[i]
                if (v != 0) {
                    text.textSize = cell * 0.62f
                    text.typeface = if (g.isGiven(i)) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    text.color = when { i in wrong || i in conflicts -> GamesColors.ERROR; g.isGiven(i) -> GamesColors.TEXT_HIGH; else -> GamesColors.PRIMARY }
                    c.drawText(v.toString(), x + cell / 2, y + cell / 2 - (text.ascent() + text.descent()) / 2, text)
                } else if (g.notes[i] != 0) {
                    text.textSize = cell * 0.28f; text.typeface = Typeface.DEFAULT; text.color = GamesColors.TEXT_MEDIUM
                    for (d in 1..9) if (g.hasNote(i, d)) {
                        val nx = x + ((d - 1) % 3 + 0.5f) * cell / 3f; val ny = y + ((d - 1) / 3 + 0.5f) * cell / 3f
                        c.drawText(d.toString(), nx, ny - (text.ascent() + text.descent()) / 2, text)
                    }
                }
            }
        }
    }

    private companion object { const val BOARD = 920 }
}
