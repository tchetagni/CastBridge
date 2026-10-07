package castbridge.core.games

import castbridge.core.chess.Box

/**
 * Où va chaque chose sur l'écran des jeux de cartes de la TV, calculé à partir des PIXELS de l'écran seulement (pas des dp) : les mêmes proportions à 1280×720 mdpi et à 1920×1080 xhdpi,
 * donc rien ne peut se chevaucher quand la densité change. Chaque texte a sa boîte et une taille qui tient dans sa hauteur ; le dessin coupe à la largeur de la boîte avec « … ». Même
 * méthode que `ChessTvLayout` (et `GameTvLayoutTest` vérifie l'absence de chevauchement sur six résolutions).
 *
 * Paysage : en haut le nom du jeu et le code de la salle ; à gauche et à droite un panneau par place (nom, qui joue, nombre de cartes du paquet) ; au milieu la table
 * (jusqu'à [TABLE_ROWS] × [TABLE_COLUMNS] cartes, chacune sous le nom de celui qui l'a posée) ; en bas la phrase d'état (2 lignes) puis les touches (2 lignes).
 */
class GameTvLayout(val width: Float, val height: Float) {
    /** Unité de base : 1 % de la hauteur de l'écran. */
    val u = height / 100f
    val margin = 3 * u

    // tailles de texte (px)
    val titleText = 6f * u
    val nameText = 4.4f * u
    val subText = 3.2f * u
    val countText = 5f * u
    val statusText = 3.8f * u
    val hintText = 2.9f * u
    val cardLabelText = 2.4f * u
    val menuText = 3.9f * u
    val menuRow = 7.4f * u

    val title: Box
    val room: Box
    val leftSeat: Box
    val rightSeat: Box
    val table: Box
    val status: Box
    val hints: Box

    // cartes de la table
    val cardGap = 1.5f * u
    val cardLabelH = 3.2f * u
    val cardW: Float
    val cardH: Float
    /** Hauteur de l'image du paquet (le dos d'une carte) dans un panneau de place. */
    val cardBackH = 14f * u

    init {
        val header = 8f * u
        title = Box(margin, margin, width * 0.62f, margin + header)
        room = Box(width * 0.62f + margin, margin, width - margin, margin + header)
        hints = Box(margin, height - margin - 2 * hintText * 1.35f, width - margin, height - margin)
        status = Box(margin, hints.t - 1.5f * u - 2 * statusText * 1.3f, width - margin, hints.t - 1.5f * u)
        val top = title.b + 2 * u
        val bottom = status.t - 2 * u
        val seatW = minOf(0.24f * width, 46 * u)
        leftSeat = Box(margin, top, margin + seatW, bottom)
        rightSeat = Box(width - margin - seatW, top, width - margin, bottom)
        table = Box(leftSeat.r + 2 * u, top, rightSeat.l - 2 * u, bottom)
        val fromWidth = (table.w - (TABLE_COLUMNS - 1) * cardGap) / TABLE_COLUMNS
        val fromHeight = ((table.h - (TABLE_ROWS - 1) * cardGap) / TABLE_ROWS - cardLabelH) / CARD_RATIO
        cardW = minOf(fromWidth, fromHeight, 13 * u).coerceAtLeast(1f)
        cardH = cardW * CARD_RATIO
    }

    /** La case (carte + nom dessous) de la ligne [row] et de la colonne [col] de la table, centrée dans la zone. */
    fun tableCell(row: Int, col: Int): Box {
        val total = TABLE_COLUMNS * cardW + (TABLE_COLUMNS - 1) * cardGap
        val l = table.l + (table.w - total) / 2 + col * (cardW + cardGap)
        val t = table.t + row * (cardH + cardLabelH + cardGap)
        return Box(l, t, l + cardW, t + cardH + cardLabelH)
    }

    /** Toutes les zones de texte de l'écran de jeu, pour le test de chevauchement. */
    fun textBoxes(): List<Pair<String, Box>> = listOf("title" to title, "room" to room, "left" to leftSeat, "right" to rightSeat, "table" to table, "status" to status, "hints" to hints)

    /** Un panneau de menu centré pour [rows] lignes et un titre (+ une ligne de sous-titre éventuelle). */
    fun menu(rows: Int, subtitle: Boolean = false): Box {
        val w = minOf(width - 2 * margin, 118 * u)
        val h = minOf(height - 2 * margin, 4 * u + titleText * 1.6f + (if (subtitle) subText * 1.6f else 0f) + rows * menuRow + 3 * u)
        return Box((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2)
    }

    /** Les lignes d'un menu de [rows] entrées dans la boîte de [menu]. */
    fun menuRows(box: Box, rows: Int, subtitle: Boolean = false): List<Box> {
        val top = box.t + 2 * u + titleText * 1.6f + (if (subtitle) subText * 1.6f else 0f)
        return List(rows) { i -> Box(box.l + 3 * u, top + i * menuRow, box.r - 3 * u, top + (i + 1) * menuRow - 0.8f * u) }
    }

    /** Salon : le QR code à gauche, le code et les places à droite. */
    val lobbyQr: Box get() { val s = 56 * u; return Box(margin * 2, (height - s) / 2 - 4 * u, margin * 2 + s, (height + s) / 2 - 4 * u) }
    val lobbyInfo: Box get() = Box(lobbyQr.r + 4 * margin, margin * 2, width - margin * 2, height - margin * 2)

    companion object {
        const val TABLE_COLUMNS = 6
        const val TABLE_ROWS = 2
        /** Une carte à jouer est un rectangle debout : hauteur / largeur. */
        const val CARD_RATIO = 1.4f
    }
}
