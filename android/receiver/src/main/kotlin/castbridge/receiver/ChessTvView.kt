package castbridge.receiver

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import androidx.core.graphics.PathParser
import castbridge.core.chess.Box
import castbridge.core.chess.ChessPieces
import castbridge.core.chess.ChessTvLayout
import castbridge.core.chess.Piece
import castbridge.core.quiz.QrCode

/** One entry of a remote-driven menu: a label, an optional value changed with ◀ ▶, an action on OK. */
class ChessItem(
    val label: String,
    val value: (() -> String)? = null,
    val enabled: Boolean = true,
    val change: ((Int) -> Unit)? = null,
    val ok: (() -> Unit)? = null,
)

/** A menu drawn on the canvas (no Android focus involved: the D-pad moves [focus]). */
class ChessMenu(val title: String, val subtitle: String?, val items: List<ChessItem>, val onBack: () -> Unit) {
    var focus = items.indexOfFirst { it.enabled }.coerceAtLeast(0)
    fun move(d: Int) {
        var i = focus
        repeat(items.size) {
            i = (i + d + items.size) % items.size
            if (items[i].enabled) { focus = i; return }
        }
    }
}

/** What the lobby shows: QR code and code to join, and who is where. */
class ChessLobby(val title: String, val code: String, val url: String?, val qr: QrCode?, val lines: List<Pair<String, Boolean>>, val note: String?, val noQrNote: String? = null)

/** Everything [ChessTvView] draws; [ChessActivity] changes it and calls invalidate(). */
class ChessTvState {
    enum class Screen { SETUP, LOBBY, GAME }
    var screen = Screen.SETUP
    var menu: ChessMenu? = null
    var overlay: ChessMenu? = null
    var promo: List<String>? = null
    var promoFocus = 0
    var s: Map<String, Any?> = emptyMap()
    var sAt = 0L
    var flipped = false
    var cursor = "e2"
    var selected: String? = null
    var lobby: ChessLobby? = null
    var footer: String? = null
    var flash: String? = null
    var flashUntil = 0L
    /** Colours played with this remote (to say « À vous » and to show the legal moves). */
    var remoteColors: Set<String> = emptySet()
}

/**
 * The whole chess screen of the TV on one Canvas: board with vector pieces, coordinates, last move, check, legal moves
 * of the selected piece and the remote's cursor; clocks as rings that empty and blink under 10 s; the move list in SAN;
 * menus and dialogs. Sizes come from [ChessTvLayout] (screen pixels), so the layout is the same at 720p and 1080p.
 */
class ChessTvView(ctx: Context, val st: ChessTvState) : View(ctx) {
    private var layout = ChessTvLayout(1280f, 720f)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val pieces: Map<Int, List<Pair<Path, Boolean>>> =
        ChessPieces.SHAPES.mapValues { (_, layers) -> layers.map { PathParser.createPathFromPathData(it.path) to it.body } }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { layout = ChessTvLayout(w.toFloat(), h.toFloat()) }

    override fun onDraw(c: Canvas) {
        val l = layout
        fill.shader = LinearGradient(0f, 0f, 0f, height.toFloat(), BG_TOP, BG_BOTTOM, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
        fill.shader = null
        when (st.screen) {
            ChessTvState.Screen.GAME -> drawGame(c, l)
            ChessTvState.Screen.LOBBY -> drawLobby(c, l)
            ChessTvState.Screen.SETUP -> drawSetupBackdrop(c, l)
        }
        st.menu?.let { drawMenu(c, l, it, dim = false) }
        st.overlay?.let { drawMenu(c, l, it, dim = true) }
        st.promo?.let { drawPromotion(c, l, it) }
        val now = SystemClock.uptimeMillis()
        st.flash?.takeIf { now < st.flashUntil }?.let { drawToast(c, l, it) }
    }

    // ------------------------------------------------------------------ helpers

    private fun textSize(px: Float, bold: Boolean = false, color: Int = TEXT) {
        text.textSize = px; text.color = color; text.typeface = if (bold) TvFonts.chessBold else TvFonts.chess; text.textAlign = Paint.Align.LEFT
    }

    /** Draws [s] on one line ellipsized to [maxW]; [y] is the top of the line. Returns the drawn width. */
    private fun line(c: Canvas, s: String, x: Float, y: Float, maxW: Float, align: Paint.Align = Paint.Align.LEFT): Float {
        val t = TextUtils.ellipsize(s, text, maxW, TextUtils.TruncateAt.END).toString()
        val w = text.measureText(t)
        val dx = when (align) { Paint.Align.CENTER -> x - w / 2; Paint.Align.RIGHT -> x - w; else -> x }
        c.drawText(t, dx, y - text.ascent(), text)
        return w
    }

    /** Word-wraps [s] into at most [maxLines] lines of [maxW] (the last one ellipsized). */
    private fun wrap(s: String, maxW: Float, maxLines: Int): List<String> {
        val words = s.split(' ')
        val out = ArrayList<String>()
        var cur = ""
        for (w in words) {
            val cand = if (cur.isEmpty()) w else "$cur $w"
            if (text.measureText(cand) <= maxW || cur.isEmpty()) cur = cand
            else { out += cur; cur = w; if (out.size == maxLines) break }
        }
        if (out.size < maxLines && cur.isNotEmpty()) out += cur
        else if (out.size == maxLines && cur.isNotEmpty() && out.last() != cur) out[out.size - 1] = out.last() + " " + cur
        return out.take(maxLines).mapIndexed { i, t -> if (i == maxLines - 1) TextUtils.ellipsize(t, text, maxW, TextUtils.TruncateAt.END).toString() else t }
    }

    private fun panel(c: Canvas, b: Box, radius: Float, color: Int = PANEL, border: Int = BORDER, borderW: Float = layout.u * 0.25f) {
        rect.set(b.l, b.t, b.r, b.b)
        fill.color = color; c.drawRoundRect(rect, radius, radius, fill)
        stroke.color = border; stroke.strokeWidth = borderW; c.drawRoundRect(rect, radius, radius, stroke)
    }

    private fun drawPiece(c: Canvas, type: Int, white: Boolean, x: Float, y: Float, size: Float) {
        val layers = pieces[type] ?: return
        c.save(); c.translate(x, y); c.scale(size / 100f, size / 100f)
        stroke.strokeWidth = 3.2f
        for ((p, body) in layers) {
            if (body) { fill.color = if (white) ChessPieces.WHITE_FILL else ChessPieces.BLACK_FILL; c.drawPath(p, fill) }
            stroke.color = if (white) ChessPieces.WHITE_LINE else ChessPieces.BLACK_LINE
            c.drawPath(p, stroke)
        }
        c.restore()
    }

    // ------------------------------------------------------------------ board

    private fun sqXY(sq: String): Pair<Int, Int> {
        val f = sq[0] - 'a'; val r = sq[1] - '1'
        return if (st.flipped) (7 - f) to r else f to (7 - r)
    }

    private fun parseFen(fen: String): Map<String, Char> {
        val b = HashMap<String, Char>()
        fen.substringBefore(' ').split('/').forEachIndexed { i, row ->
            var f = 0
            for (ch in row) if (ch.isDigit()) f += ch - '0' else { if (f < 8) b["${'a' + f}${8 - i}"] = ch; f++ }
        }
        return b
    }

    @Suppress("UNCHECKED_CAST")
    private fun drawBoard(c: Canvas, l: ChessTvLayout) {
        val s = st.s
        val bd = l.board
        val q = bd.w / 8
        // frame
        rect.set(bd.l - l.u * 0.6f, bd.t - l.u * 0.6f, bd.r + l.u * 0.6f, bd.b + l.u * 0.6f)
        fill.color = 0xFF3A2A1C.toInt(); c.drawRoundRect(rect, l.u, l.u, fill)
        for (y in 0 until 8) for (x in 0 until 8) {
            fill.color = if ((x + y) % 2 == 0) ChessPieces.LIGHT else ChessPieces.DARK
            c.drawRect(bd.l + x * q, bd.t + y * q, bd.l + (x + 1) * q, bd.t + (y + 1) * q, fill)
        }
        val board = parseFen(s["fen"] as? String ?: castbridge.core.chess.Position.START_FEN)
        (s["lastMove"] as? String)?.takeIf { it.length >= 4 }?.let { m ->
            fill.color = ChessPieces.LAST_MOVE
            for (sq in listOf(m.substring(0, 2), m.substring(2, 4))) { val (x, y) = sqXY(sq); c.drawRect(bd.l + x * q, bd.t + y * q, bd.l + (x + 1) * q, bd.t + (y + 1) * q, fill) }
        }
        if (s["check"] == true) {
            val king = if (s["turn"] == "w") 'K' else 'k'
            board.entries.firstOrNull { it.value == king }?.let { (sq, _) ->
                val (x, y) = sqXY(sq); val cx = bd.l + (x + 0.5f) * q; val cy = bd.t + (y + 0.5f) * q
                fill.shader = RadialGradient(cx, cy, q * 0.7f, ChessPieces.CHECK, 0x00E0302A, Shader.TileMode.CLAMP)
                c.drawRect(bd.l + x * q, bd.t + y * q, bd.l + (x + 1) * q, bd.t + (y + 1) * q, fill); fill.shader = null
            }
        }
        st.selected?.let { sq -> val (x, y) = sqXY(sq); fill.color = ChessPieces.SELECTED; c.drawRect(bd.l + x * q, bd.t + y * q, bd.l + (x + 1) * q, bd.t + (y + 1) * q, fill) }
        // coordinates, inside the edge squares, in the other square colour
        textSize(l.coordText, true)
        for (i in 0 until 8) {
            val rank = if (st.flipped) i + 1 else 8 - i
            text.color = if (i % 2 == 0) ChessPieces.DARK else ChessPieces.LIGHT
            c.drawText(rank.toString(), bd.l + q * 0.06f, bd.t + i * q + q * 0.05f - text.ascent(), text)
            val file = if (st.flipped) "hgfedcba"[i] else "abcdefgh"[i]
            text.color = if ((i + 7) % 2 == 0) ChessPieces.DARK else ChessPieces.LIGHT
            val fw = text.measureText(file.toString())
            c.drawText(file.toString(), bd.l + (i + 1) * q - fw - q * 0.06f, bd.b - q * 0.06f, text)
        }
        for ((sq, ch) in board) {
            val (x, y) = sqXY(sq)
            drawPiece(c, " pnbrqk".indexOf(ch.lowercaseChar()), ch.isUpperCase(), bd.l + x * q + q * 0.05f, bd.t + y * q + q * 0.04f, q * 0.9f)
        }
        // legal destinations of the selected piece
        val legal = (s["legal"] as? List<String>).orEmpty()
        st.selected?.let { from ->
            fill.color = ChessPieces.HINT; stroke.color = ChessPieces.HINT; stroke.strokeWidth = q * 0.08f
            for (m in legal.filter { it.startsWith(from) }.map { it.substring(2, 4) }.distinct()) {
                val (x, y) = sqXY(m); val cx = bd.l + (x + 0.5f) * q; val cy = bd.t + (y + 0.5f) * q
                if (board.containsKey(m)) c.drawCircle(cx, cy, q * 0.44f, stroke) else c.drawCircle(cx, cy, q * 0.16f, fill)
            }
        }
        // the remote's cursor: thick gold frame with a white inner line, visible on both square colours
        if (st.overlay == null && st.promo == null && st.screen == ChessTvState.Screen.GAME && s["stage"] == "PLAYING") {
            val (x, y) = sqXY(st.cursor)
            val w = q * 0.09f
            rect.set(bd.l + x * q + w / 2, bd.t + y * q + w / 2, bd.l + (x + 1) * q - w / 2, bd.t + (y + 1) * q - w / 2)
            stroke.strokeWidth = w; stroke.color = CURSOR; c.drawRoundRect(rect, w, w, stroke)
            rect.inset(w * 0.9f, w * 0.9f); stroke.strokeWidth = w * 0.35f; stroke.color = 0xFFFFFFFF.toInt(); c.drawRoundRect(rect, w, w, stroke)
        }
    }

    // ------------------------------------------------------------------ game screen

    @Suppress("UNCHECKED_CAST")
    private fun drawGame(c: Canvas, l: ChessTvLayout) {
        val s = st.s
        drawBoard(c, l)
        val bottom = if (st.flipped) "b" else "w"
        val top = if (bottom == "w") "b" else "w"
        drawCard(c, l, l.topCard, top)
        drawCard(c, l, l.bottomCard, bottom)
        // status: whose turn / result, then the notice
        val stage = s["stage"] as? String
        val turn = s["turn"] as? String ?: "w"
        val result = s["result"] as? Map<String, Any?>
        val main = when {
            result != null -> result["text"] as? String ?: "Partie terminée"
            stage != "PLAYING" -> "En attente…"
            turn in st.remoteColors -> (if (st.remoteColors.size == 2) "Aux ${if (turn == "w") "Blancs" else "Noirs"} de jouer" else "À vous de jouer") + (if (s["check"] == true) " — échec !" else "")
            s["thinking"] == true -> "L'ordinateur réfléchit…"
            else -> "Trait aux ${if (turn == "w") "Blancs" else "Noirs"}" + (if (s["check"] == true) " — échec !" else "")
        }
        textSize(l.statusText, true, if (result != null) GOLD else TEXT)
        line(c, main, l.status.l, l.status.t, l.status.w)
        (s["notice"] as? String ?: st.footer)?.let { n ->
            textSize(l.statusText * 0.86f, false, WARN)
            line(c, n, l.status.l, l.status.t + l.statusText * 1.3f, l.status.w)
        }
        drawMoves(c, l, (s["san"] as? List<String>).orEmpty(), (s["auto"] as? List<Number>).orEmpty().map { it.toInt() }.toSet(),
            (s["startFen"] as? String)?.split(' ')?.getOrNull(1) == "b")
        // key hints
        textSize(l.hintText, false, MUTED)
        val hint1 = if (st.selected != null) "Flèches : choisir la case · OK : poser la pièce" else "Flèches : déplacer le curseur · OK : prendre une pièce"
        val hint2 = if (st.selected != null) "RETOUR : annuler la sélection" else "RETOUR : menu (nulle, abandon, annuler le coup…)"
        line(c, hint1, l.hints.l, l.hints.t, l.hints.w)
        line(c, hint2, l.hints.l, l.hints.t + l.hintText * 1.35f, l.hints.w)
    }

    @Suppress("UNCHECKED_CAST")
    private fun drawCard(c: Canvas, l: ChessTvLayout, b: Box, color: String) {
        val s = st.s
        val side = s[if (color == "w") "white" else "black"] as? Map<String, Any?> ?: emptyMap()
        val turn = s["turn"] == color && s["stage"] == "PLAYING"
        panel(c, b, l.u * 1.6f, if (turn) PANEL_ACTIVE else PANEL, if (turn) ACCENT else BORDER, if (turn) l.u * 0.5f else l.u * 0.25f)
        val pad = l.u * 1.6f
        val icon = b.h - 2 * pad
        rect.set(b.l + pad, b.t + pad, b.l + pad + icon, b.t + pad + icon)
        fill.color = if (color == "w") 0xFF3A4452.toInt() else 0xFFCFC6B4.toInt(); c.drawRoundRect(rect, l.u, l.u, fill)
        drawPiece(c, Piece.KING, color == "w", rect.left + icon * 0.08f, rect.top + icon * 0.06f, icon * 0.84f)
        // clock ring on the right
        val clock = s["clock"] as? Map<String, Any?> ?: emptyMap()
        val per = (clock["perMoveMs"] as? Number)?.toLong() ?: 30_000L
        val running = clock["running"] == true && turn
        val frozen = clock["paused"] == true && s["turn"] == color
        val left = when {
            running -> ((clock["remainingMs"] as? Number)?.toLong() ?: per) - (SystemClock.uptimeMillis() - st.sAt)
            frozen -> (clock["remainingMs"] as? Number)?.toLong() ?: per
            else -> per
        }
        val leftMs = left.coerceIn(0, per)
        val ring = b.h - 2 * pad
        val cx = b.r - pad - ring / 2; val cy = b.t + b.h / 2
        val low = running && leftMs < 10_000
        val blinkOn = !low || (SystemClock.uptimeMillis() / 400) % 2 == 0L
        stroke.strokeWidth = l.u * 1.1f; stroke.color = 0x33FFFFFF
        rect.set(cx - ring / 2 + stroke.strokeWidth, cy - ring / 2 + stroke.strokeWidth, cx + ring / 2 - stroke.strokeWidth, cy + ring / 2 - stroke.strokeWidth)
        c.drawOval(rect, stroke)
        stroke.color = if (low) RED else if (running) ACCENT else MUTED
        c.drawArc(rect, -90f, 360f * leftMs / per, false, stroke)
        textSize(l.clockText, true, if (low) RED else if (running) TEXT else MUTED)
        if (blinkOn) { val secs = ((leftMs + 999) / 1000).toString(); text.textAlign = Paint.Align.CENTER; c.drawText(secs, cx, cy - (text.ascent() + text.descent()) / 2, text) }
        // name and details
        val x = b.l + pad * 2 + icon
        val maxW = cx - ring / 2 - pad - x
        textSize(l.nameText, true)
        line(c, side["name"] as? String ?: "", x, b.t + pad * 0.7f, maxW)
        val kind = side["kind"] as? String
        val sub = buildString {
            append(if (color == "w") "Blancs" else "Noirs")
            when (kind) {
                "PHONE" -> append(if (side["connected"] == true) " · téléphone" else " · téléphone déconnecté")
                "REMOTE" -> append(" · télécommande")
                "AI" -> if (turn && s["thinking"] == true) append(" · réfléchit…")
                "ONLINE" -> append(" · en ligne")
            }
            if (s["drawOffer"] == color) append(" · propose la nulle")
        }
        textSize(l.subText, false, MUTED)
        line(c, sub, x, b.t + pad * 0.7f + l.nameText * 1.25f, maxW)
    }

    private fun drawMoves(c: Canvas, l: ChessTvLayout, san: List<String>, auto: Set<Int>, blackFirst: Boolean) {
        val b = l.moves
        panel(c, b, l.u * 1.2f, 0x66112A1D, BORDER)
        val list: List<String?> = (if (blackFirst) listOf<String?>(null) else emptyList()) + san
        val rows = (list.size + 1) / 2
        val visible = l.moveRows - 0
        val first = maxOf(0, rows - visible)
        textSize(l.moveText)
        val colN = b.l + l.u * 1.5f
        val numW = text.measureText("000.") + l.u
        val colW = minOf((b.w - numW - l.u * 3f) / 2, l.u * 17)
        val lastIdx = san.size - 1
        if (san.isEmpty()) { textSize(l.moveText, false, MUTED); line(c, "Les coups joués s'afficheront ici.", colN, b.t + l.u, b.w - 3 * l.u); return }
        for (r in first until rows) {
            val y = b.t + l.u * 0.8f + (r - first) * l.moveRow
            textSize(l.moveText, false, MUTED)
            line(c, "${r + 1}.", colN, y, numW)
            for (k in 0..1) {
                val i = r * 2 + k
                val m = list.getOrNull(i) ?: if (i < list.size) "…" else continue
                val idx = i - (if (blackFirst) 1 else 0)
                textSize(l.moveText, idx == lastIdx, when { idx == lastIdx -> ACCENT; idx in auto -> WARN; else -> TEXT })
                line(c, m, colN + numW + k * colW, y, colW - l.u)
            }
        }
    }

    // ------------------------------------------------------------------ lobby, setup, menus

    private fun drawSetupBackdrop(c: Canvas, l: ChessTvLayout) {
        // a quiet decorative row of pieces at the bottom, far from the menu box
        val size = l.u * 9
        val y = height - size - l.margin
        val order = intArrayOf(Piece.ROOK, Piece.KNIGHT, Piece.BISHOP, Piece.QUEEN, Piece.KING, Piece.BISHOP, Piece.KNIGHT, Piece.ROOK)
        val menuBox = st.menu?.let { l.menu(it.items.size, it.subtitle != null) }
        if (menuBox == null || y > menuBox.b + l.u) {
            val total = size * order.size
            for ((i, t) in order.withIndex()) drawPiece(c, t, i % 2 == 0, (width - total) / 2 + i * size, y, size)
        }
        st.footer?.let { f ->
            textSize(l.hintText, false, MUTED)
            val top = menuBox?.b?.plus(l.u) ?: (height - l.margin - l.hintText * 1.4f)
            if (top + l.hintText * 1.3f < height) line(c, f, width / 2f, minOf(top, height - l.hintText * 1.4f), width - 2 * l.margin, Paint.Align.CENTER)
        }
    }

    private fun drawLobby(c: Canvas, l: ChessTvLayout) {
        val lb = st.lobby ?: return
        val qrBox = l.lobbyQr
        lb.qr?.let { drawQr(c, it, qrBox) } ?: run {
            panel(c, qrBox, l.u * 2)
            textSize(l.subText, false, MUTED)
            wrap(lb.noQrNote ?: "Pas de réseau local : les téléphones ne peuvent pas rejoindre.", qrBox.w - 4 * l.u, 4).forEachIndexed { i, t -> line(c, t, qrBox.l + 2 * l.u, qrBox.t + 2 * l.u + i * l.subText * 1.3f, qrBox.w - 4 * l.u) }
        }
        if (lb.qr != null || lb.noQrNote == null) {      // an Internet game has no QR code: the friend types the code
            textSize(l.subText, false, MUTED)
            line(c, "Scannez ce code avec le téléphone", (qrBox.l + qrBox.r) / 2, qrBox.b + l.u * 1.5f, qrBox.w + 2 * l.margin, Paint.Align.CENTER)
        }
        val info = l.lobbyInfo
        var y = info.t
        textSize(l.titleText, true, GOLD); line(c, lb.title, info.l, y, info.w); y += l.titleText * 1.4f
        textSize(l.subText, false, MUTED); line(c, "Code de la partie", info.l, y, info.w); y += l.subText * 1.3f
        textSize(l.titleText * 1.6f, true, TEXT); text.letterSpacing = 0.25f; line(c, lb.code, info.l, y, info.w); text.letterSpacing = 0f; y += l.titleText * 1.6f * 1.25f
        lb.url?.let { textSize(l.subText, false, MUTED); line(c, it, info.l, y, info.w); y += l.subText * 1.5f }
        for ((t, ok) in lb.lines) {
            textSize(l.statusText, ok, if (ok) TEXT else MUTED)
            line(c, (if (ok) "●  " else "○  ") + t, info.l, y, info.w); y += l.statusText * 1.4f
        }
        lb.note?.let { textSize(l.subText, false, WARN); wrap(it, info.w, 2).forEach { t -> line(c, t, info.l, y, info.w); y += l.subText * 1.3f } }
    }

    private fun drawQr(c: Canvas, q: QrCode, b: Box) {
        val n = q.size + 8
        val side = minOf(b.w, b.h)
        val cell = (side / n).toInt().coerceAtLeast(1).toFloat()
        val total = cell * n
        val ox = b.l + (b.w - total) / 2; val oy = b.t + (b.h - total) / 2
        fill.color = 0xFFFFFFFF.toInt(); c.drawRect(ox, oy, ox + total, oy + total, fill)
        fill.color = 0xFF000000.toInt()
        for (y in 0 until q.size) for (x in 0 until q.size) if (q[x, y]) c.drawRect(ox + (x + 4) * cell, oy + (y + 4) * cell, ox + (x + 5) * cell, oy + (y + 5) * cell, fill)
    }

    /** Lobby menus sit under the lobby text on the right; the others are centred. */
    private fun menuBox(l: ChessTvLayout, m: ChessMenu): Box {
        if (st.screen == ChessTvState.Screen.LOBBY && m === st.menu) {
            val info = l.lobbyInfo
            val h = m.items.size * l.menuRow + l.u * 2
            return Box(info.l - l.u * 3, info.b - h, info.r, info.b)
        }
        return l.menu(m.items.size, m.subtitle != null)
    }

    private fun drawMenu(c: Canvas, l: ChessTvLayout, m: ChessMenu, dim: Boolean) {
        if (dim) { fill.color = 0xC0000000.toInt(); c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill) }
        val box = menuBox(l, m)
        val lobbyInline = st.screen == ChessTvState.Screen.LOBBY && m === st.menu
        val rows = if (lobbyInline) List(m.items.size) { i -> Box(box.l + 3 * l.u, box.t + l.u + i * l.menuRow, box.r, box.t + l.u + (i + 1) * l.menuRow - 0.8f * l.u) }
            else l.menuRows(box, m.items.size, m.subtitle != null)
        if (!lobbyInline) {
            panel(c, box, l.u * 2.2f, 0xF00C1B14.toInt(), BORDER, l.u * 0.3f)
            textSize(l.titleText, true, GOLD)
            line(c, m.title, (box.l + box.r) / 2, box.t + 2 * l.u, box.w - 6 * l.u, Paint.Align.CENTER)
            m.subtitle?.let { textSize(l.subText, false, MUTED); line(c, it, (box.l + box.r) / 2, box.t + 2 * l.u + l.titleText * 1.5f, box.w - 6 * l.u, Paint.Align.CENTER) }
        }
        for ((i, it) in m.items.withIndex()) {
            val r = rows[i]
            val focused = i == m.focus
            if (focused) { rect.set(r.l - l.u, r.t, r.r + l.u, r.b); fill.color = ACCENT; c.drawRoundRect(rect, l.u * 1.2f, l.u * 1.2f, fill) }
            val color = when { !it.enabled -> DISABLED; focused -> BG_BOTTOM; else -> TEXT }
            val ty = r.t + (r.h - l.menuText * 1.2f) / 2
            val v = it.value?.invoke()
            textSize(l.menuText, focused, color)
            if (v == null) line(c, it.label, r.l + l.u, ty, r.w - 2 * l.u)
            else {
                val labelW = minOf(text.measureText(it.label), r.w * 0.45f)
                line(c, it.label, r.l + l.u, ty, labelW)
                val shown = if (it.change != null && it.enabled) "‹  $v  ›" else v
                textSize(l.menuText, true, color)
                line(c, shown, r.r - l.u, ty, r.w - labelW - 4 * l.u, Paint.Align.RIGHT)
            }
        }
    }

    private fun drawPromotion(c: Canvas, l: ChessTvLayout, moves: List<String>) {
        fill.color = 0xB0000000.toInt(); c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
        val cell = l.u * 16
        val w = cell * moves.size + l.u * 3 * (moves.size + 1)
        val h = cell + l.titleText * 1.6f + l.u * 6
        val box = Box((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2)
        panel(c, box, l.u * 2, 0xF00C1B14.toInt())
        textSize(l.titleText * 0.8f, true, GOLD)
        line(c, "Promotion : quelle pièce ?", (box.l + box.r) / 2, box.t + 2 * l.u, box.w - 4 * l.u, Paint.Align.CENTER)
        val white = st.s["turn"] == "w"
        for ((i, m) in moves.withIndex()) {
            val x = box.l + l.u * 3 + i * (cell + l.u * 3); val y = box.b - cell - l.u * 3
            rect.set(x, y, x + cell, y + cell)
            fill.color = ChessPieces.LIGHT; c.drawRoundRect(rect, l.u, l.u, fill)
            if (i == st.promoFocus) { stroke.color = CURSOR; stroke.strokeWidth = l.u * 1.2f; c.drawRoundRect(rect, l.u, l.u, stroke) }
            drawPiece(c, " pnbrqk".indexOf(m.last()), white, x + cell * 0.06f, y + cell * 0.05f, cell * 0.88f)
        }
    }

    private fun drawToast(c: Canvas, l: ChessTvLayout, msg: String) {
        textSize(l.statusText, true, TEXT)
        val maxW = width - 8 * l.margin
        val lines = wrap(msg, maxW, 2)
        val w = (lines.maxOfOrNull { text.measureText(it) } ?: 0f) + 6 * l.u
        val h = lines.size * l.statusText * 1.3f + 3 * l.u
        val box = Box((width - w) / 2, height - l.margin - h - l.u * 8, (width + w) / 2, height - l.margin - l.u * 8)
        panel(c, box, l.u * 1.5f, 0xF0112A1D.toInt(), WARN)
        lines.forEachIndexed { i, t -> line(c, t, width / 2f, box.t + 1.5f * l.u + i * l.statusText * 1.3f, maxW, Paint.Align.CENTER) }
    }

    companion object {
        private val E = castbridge.core.brand.BrandTokens.Echecs
        const val BG_TOP = 0xFF143122.toInt()        // forest background lightened toward the green
        const val BG_BOTTOM = castbridge.core.brand.BrandTokens.Echecs.BACKGROUND
        const val PANEL = 0xE0112A1D.toInt()
        const val PANEL_ACTIVE = 0xF01B4A32.toInt()
        const val BORDER = 0xFF2C5A40.toInt()
        const val ACCENT = castbridge.core.brand.BrandTokens.Echecs.PRIMARY
        const val GOLD = castbridge.core.brand.BrandTokens.Echecs.SECONDARY
        const val CURSOR = castbridge.core.brand.BrandTokens.Echecs.SECONDARY
        const val TEXT = castbridge.core.brand.BrandTokens.Dark.TEXT_HIGH
        const val MUTED = castbridge.core.brand.BrandTokens.Dark.TEXT_MEDIUM
        const val DISABLED = castbridge.core.brand.BrandTokens.Dark.TEXT_LOW
        const val WARN = castbridge.core.brand.BrandTokens.Echecs.ACCENT
        const val RED = castbridge.core.brand.BrandTokens.Semantic.ERROR_DARK
    }
}
