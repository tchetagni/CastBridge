package castbridge.core.tv

import castbridge.core.status.IconKind
import castbridge.core.xfer.CopyBadge
import kotlin.test.*

/** Icônes du lecteur (demande du propriétaire du 2026-10-04) : jamais une icône seule, lisibles à 3 m, dans la zone de sécurité de la TV. Le dessin Android n'est pas prouvé ici (P-54). */
class PlayerIconsTest {
    @Test fun everyStatusKindHasALegendEntryWithALabelAndAMeaning() {
        val l = PlayerIcons.legend()
        for (k in IconKind.values()) {
            val e = l.firstOrNull { it.kind == k }
            assertNotNull(e, "pas de légende pour ${k.wire}")
            assertTrue(e.label.isNotBlank() && e.meaning.length > 8, k.wire)
        }
        assertTrue(l.any { it.id == "copy" } && l.any { it.id == "edition" })
        assertEquals(l.size, l.map { it.id }.toSet().size); assertEquals(l.size, l.map { it.label }.toSet().size)
    }
    @Test fun copyCaptionSaysWhatIsHappeningAndNeverInventsAPercentage() {
        fun m(percent: Int?, n: Int, slow: Boolean = false) = CopyBadge.Model(true, percent, n, if (slow) CopyBadge.Tone.SLOWED else CopyBadge.Tone.NORMAL, percent?.let { "$it %" } ?: "", if (n > 1) "$n" else null, "")
        assertEquals("Copie en cours 42 %", PlayerIcons.copyCaption(m(42, 1)))
        assertEquals("Copie en cours", PlayerIcons.copyCaption(m(null, 1)))
        assertEquals("2 copies en cours 7 %", PlayerIcons.copyCaption(m(7, 2)))
        assertEquals("Copie en cours 42 % (ralentie)", PlayerIcons.copyCaption(m(42, 1, true)))
    }
    @Test fun sizesAreReadableFromThreeMetresOnA720pPanel() {
        assertTrue(PlayerIcons.TEXT_SP >= 28); assertTrue(PlayerIcons.ICON_DP >= 40)
    }
    @Test fun labelOnDarkPillKeepsContrastOverTheWhitestPicture() {
        assertTrue(PlayerIcons.contrastOverWhite(0xCC) >= PlayerIcons.MIN_CONTRAST, "pastille actuelle 0xCC")
        assertTrue(PlayerIcons.contrastOverWhite(PlayerIcons.PILL_ALPHA) >= PlayerIcons.MIN_CONTRAST)
        assertTrue(PlayerIcons.contrastOverWhite(0x40) < PlayerIcons.MIN_CONTRAST)          // une pastille trop transparente échoue
    }
    @Test fun safeZoneKeepsFivePercentOnEveryEdge() {
        val s = PlayerIcons.safe(1280, 720)
        assertEquals(64, s.horizontal); assertEquals(36, s.vertical)
        val k = PlayerIcons.safe(3840, 2160); assertEquals(192, k.horizontal); assertEquals(108, k.vertical)
    }
    @Test fun iconZoneShowsWithTheBarOnAnAlertOrAfterAChangeOnly() {
        assertFalse(PlayerIcons.zoneVisible(controlsVisible = false, alert = false, changedRecently = false))
        assertTrue(PlayerIcons.zoneVisible(true, false, false)); assertTrue(PlayerIcons.zoneVisible(false, true, false)); assertTrue(PlayerIcons.zoneVisible(false, false, true))
    }
}
