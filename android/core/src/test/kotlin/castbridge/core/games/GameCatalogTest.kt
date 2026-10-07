package castbridge.core.games

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Le catalogue des jeux de la plateforme : la Bataille (démonstration) est jouable, Fap-Fap et Agraham Tia sont « bientôt » (aucune règle inventée, aucune salle, non lançables). */
class GameCatalogTest {
    @Test fun theThreeGamesAndTheirNames() {
        assertEquals(listOf("bataille", "fap-fap", "agraham-tia"), GameCatalog.all.map { it.id })
        assertEquals(listOf("Bataille (démonstration)", "Fap-Fap", "Agraham Tia"), GameCatalog.all.map { it.name })
        assertEquals(listOf(true, false, false), GameCatalog.all.map { it.playable })
        assertEquals(GameCatalog.FAP_FAP, GameCatalog.entry("fap-fap")); assertNull(GameCatalog.entry("dames")); assertNull(GameCatalog.entry(null))
    }

    @Test fun gamesWaitingForTheirRulesAreNotLaunchableAndSayWhy() {
        assertEquals("Bientôt : règles en attente du propriétaire", GameCatalog.SOON)
        for (e in listOf(GameCatalog.FAP_FAP, GameCatalog.AGRAHAM_TIA)) {
            assertNull(GameCatalog.newRoom(e.id), "pas de règles, pas de salle : ${e.name}")
            assertEquals(GameCatalog.SOON, e.status)
        }
        assertEquals("Prêt à jouer", GameCatalog.BATAILLE.status)
        assertNull(GameCatalog.newRoom("inconnu"))
    }

    @Test fun everyPlayableGameHasItsRulesItsPageDrawingAndAValidIdentifier() {
        for (e in GameCatalog.all) {
            assertTrue(e.id.matches(Regex("^[a-z0-9-]{1,32}$")), "identifiant utilisable dans l'URL et dans un journal : ${e.id}")
            val js = GameCatalog::class.java.getResourceAsStream("/castbridge/games/${e.id}.js")
            assertEquals(e.playable, js != null, "un jeu jouable a son dessin de table ${e.id}.js, un jeu « bientôt » n'en a pas")
            js?.close()
            if (e.playable) { val r = GameCatalog.newRoom(e.id, autoTick = false); assertNotNull(r); assertEquals(e.id, r.rules.id); r.close() }
        }
        assertTrue(GameCatalog.all.map { it.id }.toSet().size == GameCatalog.all.size)
    }

    @Test fun theTrialEditionOpensNoneOfTheCardGamesNorTheirRoutes() {
        // en essai seul le Sudoku s'ouvre (TrialPolicy : liste blanche, tout le reste est fermé) : ni les jeux, ni la page des téléphones, ni les routes de l'app
        for (e in GameCatalog.all) assertFalse(castbridge.core.owner.TrialPolicy.gameAllowed(e.id), e.id)
        for (path in listOf("/jeux", "/jeux/bataille", "/jeux/bataille/api/join", "/jeux/fap-fap", "/api/games/room", "/api/games/journals"))
            assertTrue(castbridge.core.owner.TrialPolicy.routeBlocked(path), "« $path » doit rester fermé en version d'essai")
        assertTrue(castbridge.core.owner.TrialPolicy.routeAllowed("/api/games") && castbridge.core.owner.TrialPolicy.routeAllowed("/api/games/open"), "le hub existant reste ouvert")
    }

    @Test fun theOwnerNeverSawRulesForTheTwoCardGamesSoNothingIsInvented() {
        // la liste des jeux dont les règles existent est celle des jeux jouables : si quelqu'un code Fap-Fap ou Agraham Tia sans fiche du propriétaire, ce test le dit
        assertEquals(listOf("bataille"), GameCatalog.all.filter { it.playable }.map { it.id })
        assertFalse(GameCatalog.FAP_FAP.blurb.contains("règles :", ignoreCase = true))
    }
}
