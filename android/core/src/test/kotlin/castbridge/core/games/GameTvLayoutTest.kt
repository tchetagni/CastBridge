package castbridge.core.games

import castbridge.core.chess.Box
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * L'écran des jeux de cartes de la TV aux résolutions cibles (1280×720 à 160 dpi, 1920×1080 à 320 dpi = 960×540 dp) et quelques autres : aucune zone ne se chevauche, tout reste à
 * l'écran, les textes sont assez grands pour le canapé et tiennent dans leur boîte, la table loge les cartes. Même méthode que `ChessLayoutTest`.
 */
class GameTvLayoutTest {
    private val screens = listOf(1280f to 720f, 1920f to 1080f, 960f to 540f, 1024f to 600f, 3840f to 2160f, 1024f to 768f)

    @Test fun noOverlapAndEverythingOnScreen() {
        for ((w, h) in screens) {
            val l = GameTvLayout(w, h)
            val screen = Box(0f, 0f, w, h)
            val boxes = l.textBoxes()
            for ((name, b) in boxes) {
                assertTrue(b.inside(screen), "$name inside ${w}x$h: $b")
                assertTrue(b.w > 0 && b.h > 0, "$name not empty at ${w}x$h: $b")
            }
            for (i in boxes.indices) for (j in i + 1 until boxes.size)
                assertFalse(boxes[i].second.overlaps(boxes[j].second), "${boxes[i].first} / ${boxes[j].first} overlap at ${w}x$h")
        }
    }

    @Test fun textsFitTheirBoxesAndAreReadableAt720p() {
        for ((w, h) in screens) {
            val l = GameTvLayout(w, h)
            assertTrue(l.titleText * 1.3f <= l.title.h, "title fits at ${w}x$h"); assertTrue(l.subText * 1.3f <= l.room.h, "room line fits")
            assertTrue(l.nameText * 1.25f + l.subText * 1.25f + l.countText * 1.3f + l.cardBackH <= l.leftSeat.h, "seat panel content fits at ${w}x$h")
            assertTrue(2 * l.statusText * 1.25f <= l.status.h + 0.5f, "two status lines fit"); assertTrue(2 * l.hintText * 1.3f <= l.hints.h + 0.5f, "two hint lines fit")
            assertTrue(l.menuText * 1.3f <= l.menuRow, "menu rows do not touch")
            assertEquals(l.leftSeat.w, l.rightSeat.w, 0.01f, "the two seats are the same size")
        }
        val tv = GameTvLayout(1280f, 720f)
        assertTrue(tv.hintText >= 20f && tv.subText >= 22f && tv.nameText >= 30f, "smallest texts ≥ 20 px on the 720p TV")
    }

    @Test fun theTableHoldsTwoRowsOfSixCardsWithTheirNames() {
        for ((w, h) in screens) {
            val l = GameTvLayout(w, h)
            assertTrue(GameTvLayout.TABLE_COLUMNS * l.cardW + (GameTvLayout.TABLE_COLUMNS - 1) * l.cardGap <= l.table.w + 0.5f, "six cards across at ${w}x$h")
            assertTrue(GameTvLayout.TABLE_ROWS * (l.cardH + l.cardLabelH) + (GameTvLayout.TABLE_ROWS - 1) * l.cardGap <= l.table.h + 0.5f, "two rows with names at ${w}x$h")
            assertTrue(l.cardH > l.cardW, "cards are portrait")
            val cell = l.tableCell(0, 0); val last = l.tableCell(GameTvLayout.TABLE_ROWS - 1, GameTvLayout.TABLE_COLUMNS - 1)
            assertTrue(cell.inside(l.table) && last.inside(l.table), "first and last cells inside the table")
            for (r in 0 until GameTvLayout.TABLE_ROWS) for (c in 0 until GameTvLayout.TABLE_COLUMNS - 1) assertFalse(l.tableCell(r, c).overlaps(l.tableCell(r, c + 1)), "cells do not overlap")
        }
        assertEquals(12, GameTvLayout.TABLE_COLUMNS * GameTvLayout.TABLE_ROWS, "douze cartes : une bataille de plusieurs tours reste lisible")
    }

    @Test fun menusAndLobbyFitWithEightRows() {
        for ((w, h) in screens) {
            val l = GameTvLayout(w, h)
            val box = l.menu(8, subtitle = true)
            val rows = l.menuRows(box, 8, subtitle = true)
            assertTrue(box.inside(Box(0f, 0f, w, h)), "menu on screen at ${w}x$h")
            rows.forEach { assertTrue(it.inside(box), "row inside the menu at ${w}x$h") }
            for (i in 0 until rows.size - 1) assertFalse(rows[i].overlaps(rows[i + 1]))
            assertFalse(l.lobbyQr.overlaps(l.lobbyInfo), "lobby QR / info at ${w}x$h")
            assertTrue(l.lobbyQr.inside(Box(0f, 0f, w, h)) && l.lobbyInfo.inside(Box(0f, 0f, w, h)))
            assertEquals(l.lobbyQr.w, l.lobbyQr.h, 0.01f, "the QR code is square")
            assertTrue(l.lobbyInfo.w >= 40 * l.u, "room for the code and the seats at ${w}x$h")
        }
    }
}
