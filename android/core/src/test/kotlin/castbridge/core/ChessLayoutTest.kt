package castbridge.core

import castbridge.core.chess.Box
import castbridge.core.chess.ChessTvLayout
import kotlin.test.*

/**
 * The TV game screen at the target resolutions (1280×720 at 160 dpi, 1920×1080 at 320 dpi = 960×540 dp) and a few
 * others: no two areas overlap, all stay on screen, texts are big enough for the sofa and fit their boxes.
 */
class ChessLayoutTest {
    private val screens = listOf(1280f to 720f, 1920f to 1080f, 960f to 540f, 1024f to 600f, 3840f to 2160f, 1024f to 768f)

    @Test fun noOverlapAndEverythingOnScreen() {
        for ((w, h) in screens) {
            val l = ChessTvLayout(w, h)
            val screen = Box(0f, 0f, w, h)
            val boxes = l.textBoxes()
            for ((name, b) in boxes) {
                assertTrue(b.inside(screen), "$name inside ${w}x$h: $b")
                assertTrue(b.w > 0 && b.h > 0, "$name not empty at ${w}x$h: $b")
            }
            for (i in boxes.indices) for (j in i + 1 until boxes.size)
                assertFalse(boxes[i].second.overlaps(boxes[j].second), "${boxes[i].first} / ${boxes[j].first} overlap at ${w}x$h")
            assertEquals(l.board.w, l.board.h, 0.01f, "square board")
            assertTrue(l.moveRows >= 4, "at least 4 lines of moves at ${w}x$h (${l.moveRows})")
            assertTrue(l.panel.w >= 55 * l.u, "panel wide enough at ${w}x$h")
        }
    }

    @Test fun textsFitTheirBoxesAndAreReadableAt720p() {
        for ((w, h) in screens) {
            val l = ChessTvLayout(w, h)
            assertTrue(l.nameText * 1.25f + l.subText * 1.25f <= l.topCard.h, "card texts fit at ${w}x$h")
            assertTrue(l.clockText * 2.4f <= l.topCard.h, "clock ring fits the card")
            assertTrue(2 * l.statusText * 1.25f <= l.status.h + 0.5f, "two status lines fit")
            assertTrue(2 * l.hintText * 1.3f <= l.hints.h + 0.5f, "two hint lines fit")
            assertTrue(l.moveText <= l.moveRow * 0.85f, "move rows do not touch")
            assertTrue(l.menuText * 1.3f <= l.menuRow, "menu rows do not touch")
        }
        val tv = ChessTvLayout(1280f, 720f)
        assertTrue(tv.hintText >= 20f && tv.coordText >= 17f, "smallest texts ≥ 20 px (hints) and 17 px (coordinates) on the 720p TV")
        assertTrue(tv.nameText >= 30f)
    }

    @Test fun menusFitWithEightRows() {
        for ((w, h) in screens) {
            val l = ChessTvLayout(w, h)
            val box = l.menu(8, subtitle = true)
            val rows = l.menuRows(box, 8, subtitle = true)
            assertTrue(box.inside(Box(0f, 0f, w, h)), "menu on screen at ${w}x$h")
            rows.forEach { assertTrue(it.inside(box), "row inside the menu at ${w}x$h") }
            for (i in 0 until rows.size - 1) assertFalse(rows[i].overlaps(rows[i + 1]))
            assertFalse(l.lobbyQr.overlaps(l.lobbyInfo), "lobby QR / info at ${w}x$h")
        }
    }
}
