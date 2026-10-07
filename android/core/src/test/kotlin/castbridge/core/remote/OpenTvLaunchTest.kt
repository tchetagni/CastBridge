package castbridge.core.remote

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * R-34 (audit anti-régression 2026-10-07 b, I-15) : le raccourci « Ouvrir CastBridge-TV » ne rejoue pas l'ouverture sans geste. Raccourci, mort du processus, retour par les récents :
 * Android recrée l'activité avec l'intention d'origine (le lien y est encore), un état sauvegardé, et le drapeau « lancée depuis l'historique ». Aucun de ces trois n'est un geste.
 */
class OpenTvLaunchTest {
    @Test fun aFreshLaunchIsTheOnlyGesture() {
        assertTrue(OpenTvLaunch.fires(restored = false, fromHistory = false), "appui sur le raccourci ou sur un lien castbridge://open-tv : un geste")
    }

    @Test fun aRecreatedActivityIsNeverAGesture() {
        assertFalse(OpenTvLaunch.fires(restored = true, fromHistory = false), "savedInstanceState rempli : rotation, ou processus tué puis retour")
    }

    @Test fun aReturnThroughTheRecentAppsIsNeverAGesture() {
        assertFalse(OpenTvLaunch.fires(restored = false, fromHistory = true), "FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY : Android redonne l'intention d'ORIGINE de la tâche")
    }

    @Test fun theAuditScenarioShortcutThenProcessDeathThenRecents() {
        // 1. raccourci : intention neuve ⇒ la TV s'ouvre ; 2. processus tué ; 3. retour par les récents : l'intention d'origine, l'état et le drapeau reviennent ⇒ plus rien ne part
        assertTrue(OpenTvLaunch.fires(restored = false, fromHistory = false))
        assertFalse(OpenTvLaunch.fires(restored = true, fromHistory = true))
    }

    @Test fun onlyTheFreshCaseFiresOutOfTheFourCombinations() {
        val fired = listOf(false, true).flatMap { r -> listOf(false, true).map { h -> (r to h) to OpenTvLaunch.fires(r, h) } }.filter { it.second }.map { it.first }
        assertTrue(fired == listOf(false to false), fired.toString())
    }
}
