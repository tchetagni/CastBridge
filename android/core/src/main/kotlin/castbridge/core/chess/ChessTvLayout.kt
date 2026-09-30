package castbridge.core.chess

/** A rectangle in pixels (left, top, right, bottom). */
data class Box(val l: Float, val t: Float, val r: Float, val b: Float) {
    val w get() = r - l
    val h get() = b - t
    fun overlaps(o: Box) = l < o.r && o.l < r && t < o.b && o.t < b
    fun inside(o: Box) = l >= o.l && t >= o.t && r <= o.r && b <= o.b
}

/**
 * Where everything goes on the TV's game screen, computed from the screen's pixels only (not from dp/sp): the same
 * proportions at 1280×720 mdpi and 1920×1080 xhdpi, so nothing can overlap when the density changes. Every text has
 * its own box and a size that fits the box's height; the drawing code ellipsizes to the box's width.
 *
 * Landscape: board on the left (full height), panel on the right: opponent card, status (2 lines), move list,
 * key hints, own card.
 */
class ChessTvLayout(val width: Float, val height: Float) {
    /** Base unit: 1 % of the screen height. */
    val u = height / 100f
    val margin = 3 * u
    val board: Box
    val panel: Box
    val topCard: Box
    val status: Box
    val moves: Box
    val hints: Box
    val bottomCard: Box

    // text sizes (px)
    val nameText = 4.4f * u
    val subText = 3.2f * u
    val clockText = 5.2f * u
    val statusText = 3.8f * u
    val moveText = 3.5f * u
    val moveRow = 4.6f * u
    val hintText = 2.9f * u
    val coordText = 2.4f * u
    val titleText = 6f * u
    val menuText = 3.9f * u
    val menuRow = 7.4f * u

    init {
        val minPanel = 58 * u
        val side = minOf(height - 2 * margin, width - 3 * margin - minPanel).coerceAtLeast(10 * u)
        val boardLeft = margin
        board = Box(boardLeft, (height - side) / 2, boardLeft + side, (height + side) / 2)
        panel = Box(board.r + margin, margin, width - margin, height - margin)
        val card = 16 * u
        topCard = Box(panel.l, panel.t, panel.r, panel.t + card)
        bottomCard = Box(panel.l, panel.b - card, panel.r, panel.b)
        status = Box(panel.l, topCard.b + 1.5f * u, panel.r, topCard.b + 1.5f * u + 2 * statusText * 1.3f)
        hints = Box(panel.l, bottomCard.t - 1.5f * u - 2 * hintText * 1.35f, panel.r, bottomCard.t - 1.5f * u)
        moves = Box(panel.l, status.b + 1.5f * u, panel.r, hints.t - 1.5f * u)
    }

    /** Rows of the move list that fit (one row = one white move and one black move). */
    val moveRows: Int get() = (moves.h / moveRow).toInt().coerceAtLeast(1)

    /** Every text box of the game screen, for the overlap test. */
    fun textBoxes(): List<Pair<String, Box>> = listOf("board" to board, "top" to topCard, "status" to status, "moves" to moves,
        "hints" to hints, "bottom" to bottomCard)

    /** A centred menu panel for [rows] entries and a title (+ an optional subtitle line). */
    fun menu(rows: Int, subtitle: Boolean = false): Box {
        val w = minOf(width - 2 * margin, 118 * u)
        val h = minOf(height - 2 * margin, 4 * u + titleText * 1.6f + (if (subtitle) subText * 1.6f else 0f) + rows * menuRow + 3 * u)
        return Box((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2)
    }

    /** Rows of a menu of [rows] entries inside [menu]'s box. */
    fun menuRows(box: Box, rows: Int, subtitle: Boolean = false): List<Box> {
        val top = box.t + 2 * u + titleText * 1.6f + (if (subtitle) subText * 1.6f else 0f)
        return List(rows) { i -> Box(box.l + 3 * u, top + i * menuRow, box.r - 3 * u, top + (i + 1) * menuRow - 0.8f * u) }
    }

    /** Lobby: QR code on the left, code and players on the right. */
    val lobbyQr: Box get() { val s = 56 * u; return Box(margin * 2, (height - s) / 2 - 4 * u, margin * 2 + s, (height + s) / 2 - 4 * u) }
    val lobbyInfo: Box get() = Box(lobbyQr.r + 4 * margin, margin * 2, width - margin * 2, height - margin * 2)
}
