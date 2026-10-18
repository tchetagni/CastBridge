package castbridge.core.owner

import castbridge.core.PhoneSources
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Présence de l'étiquette de licence de CastBridge-TV (décision du propriétaire du 2026-10-18) : la bande verte (production) et la bande orange (essai) ne sont plus permanentes, elles
 * apparaissent 15 s à chaque retour à l'accueil et jamais ailleurs ; la bande rouge (clé finie ou absente) reste partout, toujours (hypothèse à confirmer). Règle pure : [KeyBadgeDisplay].
 * La seconde partie est une garde de source : l'assemblage Android n'a pas de test JVM, donc une règle juste peut rester débranchée et la suite rester verte ; un fichier absent fait échouer le test
 * et les commentaires ne comptent pas.
 */
class KeyBadgeDisplayTest {
    private fun shown(sinceMs: Long?, onHome: Boolean = true, ended: Boolean = false) = KeyBadgeDisplay.visible(ended, onHome, sinceMs)

    @Test fun theWindowIsFifteenSeconds() = assertTrue(KeyBadgeDisplay.HOME_SHOW_MS == 15_000L)

    @Test fun visibleOnTheHomeFromTheFirstInstantToJustBeforeFifteenSeconds() {
        for (t in listOf(0L, 1L, 5_000L, 14_999L)) assertTrue(shown(t), "à $t ms")
    }

    @Test fun hiddenOnTheHomeFromFifteenSecondsOn() {
        for (t in listOf(15_000L, 15_001L, 60_000L, 3_600_000L)) assertFalse(shown(t), "à $t ms")
    }

    @Test fun hiddenOffTheHomeEvenAtTheFirstInstant() {
        for (t in listOf(0L, 1L, 14_999L)) assertFalse(shown(t, onHome = false), "hors accueil, à $t ms")
        assertFalse(shown(null, onHome = false))
    }

    @Test fun hiddenOnTheHomeWhenNoStartIsKnownOrTheClockWentBack() {
        assertFalse(shown(null)); assertFalse(shown(-1L)); assertFalse(shown(Long.MIN_VALUE))
    }

    @Test fun comingBackToTheHomeStartsANewWindow() {
        // l'appelant mesure le temps depuis le DERNIER affichage de l'accueil
        var shownAt = 0L
        fun at(now: Long) = shown(now - shownAt)
        assertTrue(at(0)); assertTrue(at(14_999)); assertFalse(at(15_000)); assertFalse(at(90_000))
        shownAt = 100_000                                                   // retour d'une vidéo, du MENU, de la bibliothèque ou d'une autre activité
        assertTrue(at(100_000)); assertTrue(at(114_999)); assertFalse(at(115_000))
        shownAt = 114_000                                                   // un second retour avant la fin : les 15 s repartent de lui, pas du premier
        assertTrue(at(115_000)); assertTrue(at(128_999)); assertFalse(at(129_000))
    }

    @Test fun theRedLabelIsOnEveryScreenAlways() {
        for (onHome in listOf(true, false)) for (t in listOf(null, -1L, 0L, 14_999L, 15_000L, 3_600_000L))
            assertTrue(shown(t, onHome = onHome, ended = true), "onHome=$onHome depuis=$t")
    }

    @Test fun theRealBadgesFollowTheRule() {
        val noKey = KeyBadge.of(emptyList(), 1_800_000_000_000L)                            // « SANS CLÉ » : rouge, partout
        assertTrue(noKey.ended); assertTrue(shown(null, onHome = false, ended = noKey.ended))
        val transient = listOf(Badge("PRODUCTION", listOf("Clé illimitée")), Badge("ESSAI", listOf("Clé valable jusqu'au 01/01/2027 (30 j)")), Badge("SUPER ILLIMITÉ", listOf("Tous les droits", "Clé permanente")))
        for (b in transient) {
            assertFalse(b.ended, b.title)
            assertTrue(shown(0, ended = b.ended), b.title); assertFalse(shown(15_000, ended = b.ended), b.title); assertFalse(shown(0, onHome = false, ended = b.ended), b.title)
        }
    }

    // ---- garde de source : le branchement Android appelle bien la règle ----

    private val dir = "receiver/src/main/kotlin/castbridge/receiver"
    private val player by lazy { PhoneSources.code("$dir/PlayerActivity.kt") }
    private val overlay by lazy { PhoneSources.code("$dir/KeyBadgeOverlay.kt") }

    /** Le texte entre le début de [from] et le début de [until] (texte brut, sans compter les accolades). */
    private fun between(code: String, from: String, until: String): String {
        val a = code.indexOf(from); assertTrue(a >= 0, "« $from » introuvable")
        val b = code.indexOf(until, a + from.length); assertTrue(b > a, "« $until » introuvable après « $from »")
        return code.substring(a, b)
    }

    @Test fun theOverlayAsksTheRuleAndKnowsOnlyPlayerActivityHostsTheHome() {
        assertTrue("KeyBadgeDisplay.visible(" in overlay, "l'affichage vient de la règle pure")
        assertTrue("KeyBadgeDisplay.HOME_SHOW_MS" in overlay, "le masquage est programmé à la fin de la fenêtre de la règle")
        assertTrue(Regex("""onHome\s*&&\s*a is PlayerActivity""").containsMatchIn(overlay), "une autre activité n'est jamais « à l'accueil »")
        assertTrue("fun homeShown()" in overlay && "fun homeLeft()" in overlay)
    }

    @Test fun thePlayerTellsTheOverlayWhenTheHomeAppearsAndLeaves() {
        assertTrue("KeyBadgeOverlay.homeShown()" in between(player, "private fun showHome()", "private fun hideScreens()"), "showHome() : l'accueil (re)montré")
        assertTrue("KeyBadgeOverlay.homeLeft()" in between(player, "private fun hideScreens()", "private fun showLibrary()"), "hideScreens() : une vidéo démarre")
        assertTrue("KeyBadgeOverlay.homeLeft()" in between(player, "private fun showLibrary()", "private fun afterPlayback("), "showLibrary() : la bibliothèque s'ouvre")
        assertTrue("KeyBadgeOverlay.homeLeft()" in between(player, "settingsPanel?.let { it.show(info, menuItems())", "enterScreen(\"settings\")"), "le MENU s'ouvre")
        val resume = between(player, "override fun onResume()", "override fun onStart()")
        assertTrue(Regex("""home\?\.visible == true\s*&&\s*settingsPanel\?\.visible != true\s*&&\s*libScreen\?\.visible != true\)\s*KeyBadgeOverlay\.homeShown\(\)""").containsMatchIn(resume),
            "onResume() : retour d'une autre activité, l'accueil visible sans MENU ni bibliothèque")
    }
}
