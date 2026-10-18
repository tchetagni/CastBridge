package castbridge.core.tv

import castbridge.core.status.IconKind
import castbridge.core.xfer.CopyBadge
import kotlin.test.*

/** Icônes du lecteur (demande du propriétaire du 2026-10-04) : jamais une icône seule, lisibles à 3 m, dans la zone de sécurité de la TV. Le dessin Android n'est pas prouvé ici (P-54). R-53 (2026-10-18) : la barre d'état se retire pendant la vidéo et le MENU (P-95). */
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

    // ---- R-53 (2026-10-18) : « les icônes Bluetooth, Wi-Fi… d'extrême droite perdurent pendant le visionnage de la vidéo ou la navigation sur le menu » ----
    private val both = listOf(false, true)
    private fun shown(place: PlayerIcons.Place, controls: Boolean, recent: Boolean) = PlayerIcons.barVisible(place, controlsVisible = controls, changedRecently = recent)

    @Test fun homeKeepsTheBarWhateverTheControlsAndTheRecentChanges() {
        for (c in both) for (r in both) assertTrue(shown(PlayerIcons.Place.HOME, c, r), "accueil : commandes=$c, changement récent=$r")
    }
    @Test fun theOpenMenuHidesTheBarWhateverTheControlsAndTheRecentChanges() {
        for (c in both) for (r in both) assertFalse(shown(PlayerIcons.Place.PANEL, c, r), "MENU ouvert : commandes=$c, changement récent=$r")
    }
    @Test fun aVideoPlayingBehindTheOpenMenuHasNoBarEvenWithTheControlsOnAndAFreshChange() {
        assertFalse(shown(PlayerIcons.Place.PANEL, controls = true, recent = true))
        assertTrue(shown(PlayerIcons.Place.VIDEO, controls = true, recent = false))        // le MENU refermé, la zone revient avec la barre de commandes
    }
    @Test fun duringAVideoTheBarFollowsTheControlsOrAFreshChangeOnly() {
        assertFalse(shown(PlayerIcons.Place.VIDEO, controls = false, recent = false))
        assertTrue(shown(PlayerIcons.Place.VIDEO, true, false)); assertTrue(shown(PlayerIcons.Place.VIDEO, false, true)); assertTrue(shown(PlayerIcons.Place.VIDEO, true, true))
    }
    @Test fun aStandingAlertPinsNothingOnAVideoAfterItsFourSeconds() {
        // une pastille passe à l'orange ou au rouge à t = 0 et y reste : la zone vient avec ce changement (4 s), puis ne revient qu'avec la barre de commandes
        val showMs = 4_000L
        fun at(now: Long, controls: Boolean) = shown(PlayerIcons.Place.VIDEO, controls, recent = now < showMs)
        assertTrue(at(0, false)); assertTrue(at(3_999, false))
        assertFalse(at(4_000, false)); assertFalse(at(60_000, false)); assertFalse(at(3_600_000, false))
        assertTrue(at(60_000, true))                                                       // une touche : la barre de commandes revient, et la zone avec elle
    }
    @Test fun anotherScreenShowsTheBarOnlyAfterAChange() {
        assertFalse(shown(PlayerIcons.Place.OTHER, controls = false, recent = false))
        assertFalse(shown(PlayerIcons.Place.OTHER, controls = true, recent = false))       // les commandes du lecteur n'y sont pour rien
        assertTrue(shown(PlayerIcons.Place.OTHER, false, true)); assertTrue(shown(PlayerIcons.Place.OTHER, true, true))
    }
    @Test fun theTableCoversEveryPlaceExactly() {
        val rules = mapOf<PlayerIcons.Place, (Boolean, Boolean) -> Boolean>(
            PlayerIcons.Place.HOME to { _, _ -> true },
            PlayerIcons.Place.PANEL to { _, _ -> false },
            PlayerIcons.Place.VIDEO to { c, r -> c || r },
            PlayerIcons.Place.OTHER to { _, r -> r },
        )
        assertEquals(PlayerIcons.Place.values().toSet(), rules.keys, "un lieu ajouté sans règle ici")
        for ((place, rule) in rules) for (c in both) for (r in both) assertEquals(rule(c, r), shown(place, c, r), "$place : commandes=$c, changement récent=$r")
    }
}
