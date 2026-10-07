package castbridge.receiver

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import androidx.core.graphics.PathParser
import castbridge.core.chess.Box
import castbridge.core.games.GameTvLayout
import castbridge.core.quiz.QrCode

/** What the lobby shows: QR code and code to join, and who is where (same shape as the chess lobby, whose small data classes [ChessMenu] / [ChessItem] / [ChessLobby] the games reuse). */
class GameTvState {
    enum class Screen { SETUP, LOBBY, GAME }
    var screen = Screen.SETUP
    var title = ""
    var menu: ChessMenu? = null
    var overlay: ChessMenu? = null
    var lobby: ChessLobby? = null
    /** The shared state (docs/GAMES.md) as the TV sees it: the room's `view(null)`. */
    var s: Map<String, Any?> = emptyMap()
    var sAt = 0L
    var footer: String? = null
    var flash: String? = null
    var flashUntil = 0L
}

/**
 * The whole screen of a card game of the TV on one Canvas, sized from the screen's pixels ([GameTvLayout]): the same layout at 1280x720 @160 dpi and at 1920x1080 @320 dpi, no text
 * over another. Seat panels (name, who plays, the pile), the table with the cards in play (face down ones show their back), the status line and the key hints; menus and the lobby
 * (QR code, room code). The cards and suits are drawn with shapes, not with font glyphs: a TV without symbol fonts would show empty boxes.
 */
class GameTvView(ctx: Context, val st: GameTvState) : View(ctx) {
    private var layout = GameTvLayout(1280f, 720f)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { layout = GameTvLayout(w.toFloat(), h.toFloat()) }

    override fun onDraw(c: Canvas) {
        val l = layout
        fill.shader = LinearGradient(0f, 0f, 0f, height.toFloat(), GamesColors.BG_TOP, GamesColors.BG, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
        fill.shader = null
        when (st.screen) {
            GameTvState.Screen.GAME -> drawGame(c, l)
            GameTvState.Screen.LOBBY -> drawLobby(c, l)
            GameTvState.Screen.SETUP -> drawSetupBackdrop(c, l)
        }
        st.menu?.let { drawMenu(c, l, it, dim = false) }
        st.overlay?.let { drawMenu(c, l, it, dim = true) }
        st.flash?.takeIf { SystemClock.uptimeMillis() < st.flashUntil }?.let { drawToast(c, l, it) }
    }

    // ------------------------------------------------------------------ helpers

    private fun textSize(px: Float, bold: Boolean = false, color: Int = GamesColors.TEXT_HIGH) {
        text.textSize = px; text.color = color; text.typeface = if (bold) TvFonts.bold else TvFonts.body; text.textAlign = Paint.Align.LEFT; text.letterSpacing = 0f
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
        val out = ArrayList<String>()
        var cur = ""
        for (w in s.split(' ')) {
            val cand = if (cur.isEmpty()) w else "$cur $w"
            if (text.measureText(cand) <= maxW || cur.isEmpty()) cur = cand
            else { out += cur; cur = w; if (out.size == maxLines) break }
        }
        if (out.size < maxLines && cur.isNotEmpty()) out += cur
        else if (out.size == maxLines && cur.isNotEmpty() && out.last() != cur) out[out.size - 1] = out.last() + " " + cur
        return out.take(maxLines).mapIndexed { i, t -> if (i == maxLines - 1) TextUtils.ellipsize(t, text, maxW, TextUtils.TruncateAt.END).toString() else t }
    }

    private fun panel(c: Canvas, b: Box, radius: Float, color: Int = GamesColors.SURFACE, border: Int = GamesColors.OUTLINE, borderW: Float = layout.u * 0.25f) {
        rect.set(b.l, b.t, b.r, b.b)
        fill.color = color; c.drawRoundRect(rect, radius, radius, fill)
        stroke.color = border; stroke.strokeWidth = borderW; c.drawRoundRect(rect, radius, radius, stroke)
    }

    @Suppress("UNCHECKED_CAST") private fun Map<String, Any?>.sub(k: String) = this[k] as? Map<String, Any?>
    @Suppress("UNCHECKED_CAST") private fun Map<String, Any?>.list(k: String) = (this[k] as? List<*>)?.filterIsInstance<Map<String, Any?>>().orEmpty()

    // ------------------------------------------------------------------ the game

    private fun drawGame(c: Canvas, l: GameTvLayout) {
        val s = st.s
        val seats = s.list("seats")
        val table = s.sub("table")
        textSize(l.titleText, true, GamesColors.PRIMARY); line(c, st.title, l.title.l, l.title.t, l.title.w)
        textSize(l.subText, false, GamesColors.TEXT_MEDIUM); line(c, "Salle ${s["code"] ?: ""}", l.room.r, l.room.t + (l.room.h - l.subText) / 2, l.room.w, Paint.Align.RIGHT)
        val playing = s["stage"] == "PLAYING"
        seats.getOrNull(0)?.let { drawSeat(c, l, l.leftSeat, it, table, playing) }
        seats.getOrNull(1)?.let { drawSeat(c, l, l.rightSeat, it, table, playing) }
        drawTable(c, l, table, seats)
        // status: who plays, or the end of the game; then the last round
        val lines = ArrayList<String>()
        val result = s.sub("result")
        if (result != null) lines += result["text"] as? String ?: "" else lines += statusLine(seats, playing)
        table?.sub("last")?.let { r -> val w = seatName(seats, r["winner"] as? String); lines += (if (r["battle"] == true) "Bataille ! " else "") + "$w gagne la levée (${r["won"]} cartes)" }
        textSize(l.statusText, true, if (result != null) GamesColors.PRIMARY else GamesColors.TEXT_HIGH)
        lines.take(2).forEachIndexed { i, t -> line(c, t, width / 2f, l.status.t + i * l.statusText * 1.3f, l.status.w, Paint.Align.CENTER); if (i == 0) textSize(l.statusText, false, GamesColors.TEXT_MEDIUM) }
        textSize(l.hintText, false, GamesColors.TEXT_MEDIUM)
        val hint = st.footer ?: ""
        wrap(hint, l.hints.w, 2).forEachIndexed { i, t -> line(c, t, width / 2f, l.hints.t + i * l.hintText * 1.35f, l.hints.w, Paint.Align.CENTER) }
    }

    private fun seatName(seats: List<Map<String, Any?>>, id: String?): String = seats.firstOrNull { it["id"] == id }?.get("name") as? String ?: (id ?: "")

    private fun statusLine(seats: List<Map<String, Any?>>, playing: Boolean): String {
        if (!playing) return "La partie va commencer"
        val turn = seats.firstOrNull { it["toMove"] == true } ?: return ""
        return if (turn["me"] == true) "À vous de jouer : appuyez sur OK" else "C'est à ${turn["name"]} de jouer"
    }

    private fun drawSeat(c: Canvas, l: GameTvLayout, b: Box, seat: Map<String, Any?>, table: Map<String, Any?>?, playing: Boolean) {
        val toMove = playing && seat["toMove"] == true
        panel(c, b, l.u * 1.6f, if (toMove) GamesColors.SURFACE_HIGH else GamesColors.SURFACE, if (toMove) GamesColors.PRIMARY else GamesColors.OUTLINE, if (toMove) l.u * 0.5f else l.u * 0.25f)
        val pad = l.u * 1.5f
        val inner = b.w - 2 * pad
        var y = b.t + l.u
        textSize(l.nameText, true, GamesColors.TEXT_HIGH); line(c, seat["name"] as? String ?: "", b.l + pad, y, inner); y += l.nameText * 1.25f
        val kind = when (seat["kind"]) { "PHONE" -> "Téléphone"; "REMOTE" -> "Télécommande"; else -> "Ordinateur" }
        textSize(l.subText, false, GamesColors.TEXT_MEDIUM); line(c, kind + if (seat["me"] == true) " · vous" else "", b.l + pad, y, inner); y += l.subText * 1.25f + l.u
        // the pile: the back of a card and the number of cards (nobody sees the content of a pile)
        val count = ((seat["id"] as? String)?.let { table?.sub("piles")?.get(it) } as? Number)?.toInt()
        val back = Box(b.l + (b.w - l.cardBackH / GameTvLayout.CARD_RATIO) / 2, y, b.l + (b.w + l.cardBackH / GameTvLayout.CARD_RATIO) / 2, y + l.cardBackH)
        if (count != null && count > 0) drawCard(c, l, back, null)
        y += l.cardBackH + l.u
        if (count != null) { textSize(l.countText, true, GamesColors.TEXT_HIGH); line(c, "$count carte${if (count > 1) "s" else ""}", b.l + b.w / 2, y, inner, Paint.Align.CENTER) }
        y += l.countText * 1.3f
        val away = seat["kind"] == "PHONE" && seat["connected"] == false && seat["playerId"] != null
        val tag = when {
            away -> "Déconnecté" + (seat["graceLeftMs"] as? Number)?.let { " · reprise possible " + (((it.toLong() - (SystemClock.uptimeMillis() - st.sAt)).coerceAtLeast(0) + 999) / 1000) + " s" }.orEmpty()
            seat["kind"] == "PHONE" && seat["playerId"] == null -> "Place libre"
            toMove -> if (seat["me"] == true) "À vous" else "Réfléchit…"
            else -> ""
        }
        if (tag.isNotEmpty()) { textSize(l.subText, true, if (away) GamesColors.ERROR else GamesColors.PRIMARY); line(c, tag, b.l + b.w / 2, y, inner, Paint.Align.CENTER) }
    }

    private fun drawTable(c: Canvas, l: GameTvLayout, table: Map<String, Any?>?, seats: List<Map<String, Any?>>) {
        val cards = table?.list("table").orEmpty().takeLast(GameTvLayout.TABLE_ROWS * GameTvLayout.TABLE_COLUMNS)
        if (cards.isEmpty()) {
            textSize(l.subText, false, GamesColors.TEXT_MEDIUM)
            line(c, if (st.s["stage"] == "PLAYING") "La table est vide" else "", l.table.l + l.table.w / 2, l.table.t + l.table.h / 2 - l.subText, l.table.w, Paint.Align.CENTER)
            return
        }
        cards.forEachIndexed { i, p ->
            val cell = l.tableCell(i / GameTvLayout.TABLE_COLUMNS, i % GameTvLayout.TABLE_COLUMNS)
            drawCard(c, l, Box(cell.l, cell.t, cell.r, cell.t + l.cardH), if (p["up"] == true) p["card"] as? String else null)
            textSize(l.cardLabelText, false, GamesColors.TEXT_MEDIUM)
            line(c, seatName(seats, p["player"] as? String), (cell.l + cell.r) / 2, cell.t + l.cardH + l.u * 0.3f, cell.w + l.cardGap, Paint.Align.CENTER)
        }
    }

    /** A playing card: its back when [code] is null, else the face (« 10H », « AS »…) with the rank at the top left and the suit big in the middle. */
    private fun drawCard(c: Canvas, l: GameTvLayout, b: Box, code: String?) {
        val r = b.w * 0.12f
        rect.set(b.l, b.t, b.r, b.b)
        if (code == null) {
            fill.color = 0xFF1D4F7A.toInt(); c.drawRoundRect(rect, r, r, fill)
            rect.inset(b.w * 0.1f, b.w * 0.1f); stroke.color = 0xFF8FB6D9.toInt(); stroke.strokeWidth = maxOf(1f, b.w * 0.04f); c.drawRoundRect(rect, r * 0.6f, r * 0.6f, stroke)
            return
        }
        fill.color = 0xFFF7F2E4.toInt(); c.drawRoundRect(rect, r, r, fill)
        stroke.color = 0xFFBDB59F.toInt(); stroke.strokeWidth = maxOf(1f, b.w * 0.03f); c.drawRoundRect(rect, r, r, stroke)
        val suit = code.last(); val rank = code.dropLast(1)
        val color = if (suit == 'H' || suit == 'D') 0xFFC4262E.toInt() else 0xFF161616.toInt()
        textSize(b.w * 0.34f, true, color); line(c, rank, b.l + b.w * 0.1f, b.t + b.w * 0.06f, b.w * 0.8f)
        drawSuit(c, suit, (b.l + b.r) / 2, b.t + b.h * 0.6f, b.w * 0.5f, color)
    }

    private val heart = PathParser.createPathFromPathData("M12,21 C12,21 3,14.5 3,8.5 C3,5.5 5.4,3.5 7.9,3.5 C9.6,3.5 11.2,4.4 12,5.9 C12.8,4.4 14.4,3.5 16.1,3.5 C18.6,3.5 21,5.5 21,8.5 C21,14.5 12,21 12,21 Z")
    private val diamond = PathParser.createPathFromPathData("M12,2 L21,12 L12,22 L3,12 Z")
    private val spade = PathParser.createPathFromPathData("M12,2 C12,2 3,9 3,14 C3,17 5.4,18.8 7.8,18.8 C9.6,18.8 11.1,18 11.6,16.6 C11.5,19 10.6,21 8.5,22 L15.5,22 C13.4,21 12.5,19 12.4,16.6 C12.9,18 14.4,18.8 16.2,18.8 C18.6,18.8 21,17 21,14 C21,9 12,2 12,2 Z")
    private val stem = PathParser.createPathFromPathData("M12,12 L9,22 L15,22 Z")

    /** A suit (H, D, S, C) of [size] centred on (cx, cy), drawn with shapes on a 24x24 grid. */
    private fun drawSuit(c: Canvas, suit: Char, cx: Float, cy: Float, size: Float, color: Int) {
        c.save(); c.translate(cx - size / 2, cy - size / 2); c.scale(size / 24f, size / 24f)
        fill.color = color
        when (suit) {
            'H' -> c.drawPath(heart, fill)
            'D' -> c.drawPath(diamond, fill)
            'S' -> c.drawPath(spade, fill)
            else -> { c.drawCircle(12f, 7f, 4.6f, fill); c.drawCircle(6.8f, 14f, 4.6f, fill); c.drawCircle(17.2f, 14f, 4.6f, fill); c.drawPath(stem, fill) }
        }
        c.restore()
    }

    // ------------------------------------------------------------------ lobby, setup, menus

    private fun drawSetupBackdrop(c: Canvas, l: GameTvLayout) {
        // a quiet row of cards at the bottom, far from the menu box
        val h = l.u * 14; val w = h / GameTvLayout.CARD_RATIO
        val menuBox = st.menu?.let { l.menu(it.items.size, it.subtitle != null) }
        val y = height - h - l.margin
        if (menuBox == null || y > menuBox.b + l.u) {
            val codes = listOf("AS", "KH", "QD", "JC", "10S")
            val total = w * codes.size + l.u * 2 * (codes.size - 1)
            codes.forEachIndexed { i, code -> drawCard(c, l, Box((width - total) / 2 + i * (w + l.u * 2), y, (width - total) / 2 + i * (w + l.u * 2) + w, y + h), code) }
        }
        st.footer?.let { f ->
            textSize(l.hintText, false, GamesColors.TEXT_MEDIUM)
            val top = menuBox?.b?.plus(l.u) ?: (height - l.margin - l.hintText * 1.4f)
            if (top + l.hintText * 1.3f < y - l.u) line(c, f, width / 2f, top, width - 2 * l.margin, Paint.Align.CENTER)
        }
    }

    private fun drawLobby(c: Canvas, l: GameTvLayout) {
        val lb = st.lobby ?: return
        val qrBox = l.lobbyQr
        lb.qr?.let { drawQr(c, it, qrBox) } ?: run {
            panel(c, qrBox, l.u * 2)
            textSize(l.subText, false, GamesColors.TEXT_MEDIUM)
            wrap("Pas de réseau local : les téléphones ne peuvent pas rejoindre.", qrBox.w - 4 * l.u, 4).forEachIndexed { i, t -> line(c, t, qrBox.l + 2 * l.u, qrBox.t + 2 * l.u + i * l.subText * 1.3f, qrBox.w - 4 * l.u) }
        }
        textSize(l.subText, false, GamesColors.TEXT_MEDIUM)
        line(c, "Scannez ce code avec le téléphone", (qrBox.l + qrBox.r) / 2, qrBox.b + l.u * 1.5f, qrBox.w + 2 * l.margin, Paint.Align.CENTER)
        val info = l.lobbyInfo
        var y = info.t
        textSize(l.titleText, true, GamesColors.PRIMARY); line(c, lb.title, info.l, y, info.w); y += l.titleText * 1.4f
        textSize(l.subText, false, GamesColors.TEXT_MEDIUM); line(c, "Code de la partie", info.l, y, info.w); y += l.subText * 1.3f
        textSize(l.titleText * 1.6f, true, GamesColors.TEXT_HIGH); text.letterSpacing = 0.25f; line(c, lb.code, info.l, y, info.w); text.letterSpacing = 0f; y += l.titleText * 1.6f * 1.25f
        lb.url?.let { textSize(l.subText, false, GamesColors.TEXT_MEDIUM); line(c, it, info.l, y, info.w); y += l.subText * 1.5f }
        for ((t, ok) in lb.lines) {
            textSize(l.statusText, ok, if (ok) GamesColors.TEXT_HIGH else GamesColors.TEXT_MEDIUM)
            line(c, (if (ok) "●  " else "○  ") + t, info.l, y, info.w); y += l.statusText * 1.4f
        }
        lb.note?.let { textSize(l.subText, false, GamesColors.PRIMARY); wrap(it, info.w, 2).forEach { t -> line(c, t, info.l, y, info.w); y += l.subText * 1.3f } }
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
    private fun menuBox(l: GameTvLayout, m: ChessMenu): Box {
        if (st.screen == GameTvState.Screen.LOBBY && m === st.menu) {
            val info = l.lobbyInfo
            val h = m.items.size * l.menuRow + l.u * 2
            return Box(info.l - l.u * 3, info.b - h, info.r, info.b)
        }
        return l.menu(m.items.size, m.subtitle != null)
    }

    private fun drawMenu(c: Canvas, l: GameTvLayout, m: ChessMenu, dim: Boolean) {
        if (dim) { fill.color = 0xC0000000.toInt(); c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill) }
        val box = menuBox(l, m)
        val lobbyInline = st.screen == GameTvState.Screen.LOBBY && m === st.menu
        val rows = if (lobbyInline) List(m.items.size) { i -> Box(box.l + 3 * l.u, box.t + l.u + i * l.menuRow, box.r, box.t + l.u + (i + 1) * l.menuRow - 0.8f * l.u) }
            else l.menuRows(box, m.items.size, m.subtitle != null)
        if (!lobbyInline) {
            panel(c, box, l.u * 2.2f, 0xF0151D37.toInt(), GamesColors.OUTLINE, l.u * 0.3f)
            textSize(l.titleText, true, GamesColors.PRIMARY)
            line(c, m.title, (box.l + box.r) / 2, box.t + 2 * l.u, box.w - 6 * l.u, Paint.Align.CENTER)
            m.subtitle?.let { textSize(l.subText, false, GamesColors.TEXT_MEDIUM); line(c, it, (box.l + box.r) / 2, box.t + 2 * l.u + l.titleText * 1.5f, box.w - 6 * l.u, Paint.Align.CENTER) }
        }
        for ((i, it) in m.items.withIndex()) {
            val r = rows[i]
            val focused = i == m.focus
            if (focused) { rect.set(r.l - l.u, r.t, r.r + l.u, r.b); fill.color = GamesColors.PRIMARY; c.drawRoundRect(rect, l.u * 1.2f, l.u * 1.2f, fill) }
            val color = when { !it.enabled -> GamesColors.OUTLINE; focused -> GamesColors.ON_PRIMARY; else -> GamesColors.TEXT_HIGH }
            val ty = r.t + (r.h - l.menuText * 1.2f) / 2
            val v = it.value?.invoke()
            textSize(l.menuText, focused, color)
            if (v == null) line(c, it.label, r.l + l.u, ty, r.w - 2 * l.u)
            else {
                val labelW = minOf(text.measureText(it.label), r.w * 0.38f)
                line(c, it.label, r.l + l.u, ty, labelW)
                val shown = if (it.change != null && it.enabled) "‹  $v  ›" else v
                textSize(l.menuText, true, color)
                line(c, shown, r.r - l.u, ty, r.w - labelW - 4 * l.u, Paint.Align.RIGHT)
            }
        }
    }

    private fun drawToast(c: Canvas, l: GameTvLayout, msg: String) {
        textSize(l.statusText, true, GamesColors.TEXT_HIGH)
        val maxW = width - 8 * l.margin
        val lines = wrap(msg, maxW, 2)
        val w = (lines.maxOfOrNull { text.measureText(it) } ?: 0f) + 6 * l.u
        val h = lines.size * l.statusText * 1.3f + 3 * l.u
        val box = Box((width - w) / 2, height - l.margin - h - l.u * 8, (width + w) / 2, height - l.margin - l.u * 8)
        panel(c, box, l.u * 1.5f, 0xF0151D37.toInt(), GamesColors.PRIMARY)
        lines.forEachIndexed { i, t -> line(c, t, width / 2f, box.t + 1.5f * l.u + i * l.statusText * 1.3f, maxW, Paint.Align.CENTER) }
    }
}
