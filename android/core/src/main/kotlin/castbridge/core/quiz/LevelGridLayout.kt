package castbridge.core.quiz

/**
 * The level / field grid of « Quel niveau ? » on CastBridge-TV: fixed-width compact cards in rows that wrap, so that 14 secondary levels
 * (or 24) never overflow the screen, and a D-pad order that reaches every card. Pure: the screen only applies it.
 */
object LevelGridLayout {
    /** Width of a card (it never grows when focused), the space between two cards and the side margins of the screen, in dp. */
    const val CARD_DP = 190
    const val GAP_DP = 16
    const val MARGIN_DP = 80

    enum class Dir { LEFT, RIGHT, UP, DOWN }

    /** How many cards fit on a row of a [widthDp] screen (at least one). */
    fun columns(widthDp: Int, cardWidthDp: Int = CARD_DP, gapDp: Int = GAP_DP, marginDp: Int = MARGIN_DP): Int =
        maxOf(1, (widthDp - marginDp + gapDp) / (cardWidthDp + gapDp))

    /**
     * The card to focus after a D-pad press from card [index] of [count] cards laid out on [cols] columns, null = stay (an edge).
     * LEFT / RIGHT walk the reading order, wrapping from the end of a row to the start of the next (and back); UP / DOWN move one row;
     * DOWN from a column that has no card in the last, partial row lands on the last card.
     */
    fun move(index: Int, dir: Dir, count: Int, cols: Int): Int? {
        if (index !in 0 until count) return null
        val c = maxOf(cols, 1)
        return when (dir) {
            Dir.RIGHT -> (index + 1).takeIf { it < count }
            Dir.LEFT -> (index - 1).takeIf { it >= 0 }
            Dir.UP -> (index - c).takeIf { it >= 0 }
            Dir.DOWN -> when {
                index + c < count -> index + c
                index / c < (count - 1) / c -> count - 1
                else -> null
            }
        }
    }
}
