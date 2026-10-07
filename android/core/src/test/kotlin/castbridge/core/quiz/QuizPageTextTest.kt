package castbridge.core.quiz

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Les textes de la page web `/quiz` des téléphones (games-G5) : une compétition entre téléphones d'une même TV se joue en POINTS sans valeur (jamais des « jetons »), une partie Internet MISÉE dit sa mise avec
 * sa monnaie (« 20 NDEM », « 5 MBOKO ») d'après le bloc `stake` de la vue ; la page n'écrit jamais « jetons » seul pour une mise. Lecture statique de la ressource (le rendu a été vérifié avec un faux DOM hors dépôt).
 */
class QuizPageTextTest {
    private val page = QuizPageTextTest::class.java.getResourceAsStream("/castbridge/quiz/play.html")!!.use { String(it.readBytes(), Charsets.UTF_8) }

    @Test fun thePageNeverCallsAStakeTokens() {
        // seule mention permise : le nom du portefeuille de la TV, « Jetons », où se lit le règlement d'une partie misée
        val text = page.replace("« Jetons »", "")
        assertFalse(Regex("(?i)jeton").containsMatchIn(text), "aucun « jeton » dans la page : les points sont des points, les mises des NDEM ou des MBOKO")
        assertTrue(page.contains("« Jetons »"), "le règlement d'une partie misée renvoie au portefeuille de la TV")
    }

    @Test fun theCompetitionByPointsSaysPointsAndNothingToPayOrToWin() {
        assertTrue(page.contains("Enjeu : \" + st.stake + \" points par joueur"), "l'enjeu d'une compétition à points est en points")
        assertTrue(page.contains("Points gagnés : +"), "ce que gagne un joueur est en points")
        assertTrue(page.contains("rien à payer, rien à gagner"), "ces points n'ont aucune valeur")
        assertTrue(page.contains("\" points\" : \"\""), "le solde de points affiché dit « points »")
    }

    @Test fun anOnlineStakedRoomSaysItsStakeWithItsCurrencyFromTheViewBlock() {
        assertTrue(page.contains("const bet = s.stake"), "le bloc `stake` de la vue du service")
        assertTrue(page.contains("\"Mise : \" + nb(bet.per) + \" \" + bet.cur + \" par joueur"), "« Mise : 20 NDEM par joueur »")
        assertTrue(page.contains("Votre part de la cagnotte : "), "la part du joueur, avant les frais éventuels de la plateforme")
        assertTrue(page.contains("Mise rendue : "), "partie interrompue ou personne n'a marqué : la mise est rendue")
        for (o in listOf("SPLIT", "ABORT")) assertTrue(page.contains("\"$o\""), o)
    }

    @Test fun theFinalDuelScreenNeverReadsAnUndefinedName() {
        // régression : `renderDuel` lisait `demo`, défini seulement dans `render` : « demo is not defined » à la fin de chaque Duel
        assertEquals(1, Regex("const demo = demoPill\\(s\\)").findAll(page).count(), "`demo` est défini une fois, dans `render`, et ne se lit que là")
        assertEquals(1, Regex("function demoPill\\(s\\)").findAll(page).count(), "un seul constructeur du rappel « points sans valeur », appelé par les deux écrans")
        assertTrue(page.contains("list, demoPill(s),"), "l'écran de fin de Duel l'appelle")
    }
}
