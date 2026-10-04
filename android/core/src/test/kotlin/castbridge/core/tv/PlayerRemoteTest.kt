package castbridge.core.tv

import castbridge.core.tv.PlayerRemote.Act
import castbridge.core.tv.PlayerRemote.Key
import kotlin.test.*

/** Télécommande de base à 5 touches : toutes les options du lecteur restent atteignables. Le câblage Android (vues, focus) n'est pas prouvé ici (P-54). */
class PlayerRemoteTest {
    private fun d(k: Key, long: Boolean = false, bar: Boolean = false) = PlayerRemote.decide(k, long, bar)

    @Test fun okShowsTheBarThenLetsTheFocusedButtonActAndLongOkOpensThePanel() {
        assertEquals(Act.SHOW_BAR, d(Key.OK))
        assertEquals(Act.PASS, d(Key.OK, bar = true))
        assertEquals(Act.OPEN_PANEL, d(Key.OK, long = true))
    }
    @Test fun seekKeysAreUnchangedWhenTheBarIsHidden() {
        assertEquals(Act.SEEK_FWD_10, d(Key.RIGHT)); assertEquals(Act.SEEK_BACK_10, d(Key.LEFT))
        assertEquals(Act.SEEK_FWD_60, d(Key.UP)); assertEquals(Act.SEEK_BACK_60, d(Key.DOWN))
        assertEquals(Act.STOP, d(Key.BACK))
    }
    @Test fun barVisibleLeftRightMoveFocusDownOpensPanelBackOnlyHidesTheBar() {
        assertEquals(Act.PASS, d(Key.LEFT, bar = true)); assertEquals(Act.PASS, d(Key.RIGHT, bar = true))
        assertEquals(Act.OPEN_PANEL, d(Key.DOWN, bar = true)); assertEquals(Act.SEEK_FWD_60, d(Key.UP, bar = true))
        assertEquals(Act.HIDE_BAR, d(Key.BACK, bar = true))
    }
    @Test fun shortcutsStillWork() {
        for (bar in listOf(false, true)) { assertEquals(Act.OPEN_PANEL, d(Key.MENU, bar = bar)); assertEquals(Act.SHOW_INFO, d(Key.INFO, bar = bar)) }
    }
    @Test fun autoHideKeepsTheBarWhilePausedButNotTheDiagnosticLine() {
        val bar = AutoHide(PlayerRemote.BAR_MS); val diag = AutoHide(PlayerRemote.DIAG_MS)
        assertFalse(bar.visible(0, false)); bar.touch(1000); diag.touch(1000)
        assertTrue(bar.visible(1000 + PlayerRemote.BAR_MS - 1, false)); assertFalse(bar.visible(1000 + PlayerRemote.BAR_MS, false))
        assertTrue(bar.visible(1_000_000, true))                                  // en pause : reste affichée
        assertTrue(diag.visible(1000 + 5999, false)); assertFalse(diag.visible(1000 + 6000, false))
        assertEquals(5000L, PlayerRemote.BAR_MS); assertEquals(6000L, PlayerRemote.DIAG_MS)
        bar.touch(9000); assertTrue(bar.visible(9000 + 4999, false)); bar.clear(); assertFalse(bar.visible(9001, false))
    }
    @Test fun overlayLineNamesModeSourcePanelAndImage() {
        val r = VideoFit.Resolved(VideoFit.Mode.FILL, VideoFit.Source.DEFAULT)
        val p = VideoFit.decide(640, 480, 1, 1, 0, 1280, 720, VideoFit.Mode.FILL)
        assertEquals("Affichage : Remplir l'écran · source 640×480 · dalle 1280×720 · image ${p.shownW}×${p.shownH}", VideoFit.overlayLine(r, p, 640, 480, 1280, 720))
        assertEquals("Affichage : Remplir l'écran · source inconnue · dalle 1280×720 · image non appliquée", VideoFit.overlayLine(r, null, 0, 0, 1280, 720))
    }
}
