package castbridge.core.quiz

/**
 * Home of the Quiz on CastBridge-TV. The look of tv-0.14.28-beta (cards of 300 dp, two per row) is kept; when the screen is too narrow
 * the cards wrap onto more rows instead of overflowing, and every row stays inside the safe area (5 % margins). Pure: the screen only applies it.
 */
object QuizHomeLayout {
    /** Card width of the old home (its minimum width), the narrowest card (longest word « Compétition » + padding), the margin around each card, the usual cards per row. */
    const val CARD_DP = 300
    const val MIN_CARD_DP = 220
    const val SLOT_MARGIN_DP = 16
    const val PREFERRED_PER_ROW = 2

    private fun safeWidth(screenWidthDp: Int): Int = screenWidthDp * 90 / 100

    /** The number of cards of each row for [count] cards: [preferred] per row, fewer when [cardWidthDp] (floored at [MIN_CARD_DP]) would leave the safe area. */
    fun rows(screenWidthDp: Int, cardWidthDp: Int, count: Int, preferred: Int = PREFERRED_PER_ROW): List<Int> {
        if (count <= 0) return emptyList()
        val slot = maxOf(cardWidthDp, MIN_CARD_DP) + SLOT_MARGIN_DP
        val fit = maxOf(1, safeWidth(screenWidthDp) / slot)
        // a card shrinks (down to the minimum) before a row loses a card
        val shrinkFit = maxOf(1, safeWidth(screenWidthDp) / (MIN_CARD_DP + SLOT_MARGIN_DP))
        val perRow = minOf(preferred, maxOf(fit, shrinkFit)).coerceAtLeast(1)
        return List((count + perRow - 1) / perRow) { r -> minOf(perRow, count - r * perRow) }
    }

    /** The width of a card for [perRow] cards on a row: [CARD_DP] when it fits, otherwise the widest that fits the safe area (never under [MIN_CARD_DP]). */
    fun cardWidthDp(screenWidthDp: Int, perRow: Int): Int {
        val n = maxOf(perRow, 1)
        val room = safeWidth(screenWidthDp) / n - SLOT_MARGIN_DP
        return room.coerceIn(MIN_CARD_DP, CARD_DP)
    }

    /**
     * The card to focus after a D-pad press from card [index]: LEFT / RIGHT stay inside the row (null at its edges, no wrap-around);
     * UP / DOWN go to the neighbouring row keeping the closest column; null = stay.
     */
    fun move(index: Int, dir: LevelGridLayout.Dir, rows: List<Int>): Int? {
        var start = 0
        val r = rows.indexOfFirst { n -> (index in start until start + n).also { if (!it) start += n } }
        if (r < 0) return null
        val col = index - start
        return when (dir) {
            LevelGridLayout.Dir.LEFT -> if (col > 0) index - 1 else null
            LevelGridLayout.Dir.RIGHT -> if (col < rows[r] - 1) index + 1 else null
            LevelGridLayout.Dir.UP -> if (r > 0) (start - rows[r - 1]) + minOf(col, rows[r - 1] - 1) else null
            LevelGridLayout.Dir.DOWN -> if (r < rows.size - 1) (start + rows[r]) + minOf(col, rows[r + 1] - 1) else null
        }
    }
}
