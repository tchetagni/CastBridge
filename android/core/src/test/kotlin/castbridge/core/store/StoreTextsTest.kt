package castbridge.core.store

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Les phrases de la Boutique (w17-02) : exactes là où le cahier l'exige, propres partout (pas de prix, pas de jargon technique). */
class StoreTextsTest {
    private val parametrised = listOf(
        StoreTexts.quotaPhone(3), StoreTexts.quotaTv(3), StoreTexts.alreadyRented("CM2", "7 jours"), StoreTexts.spacePhone(1_500_000), StoreTexts.spaceTv(1_500_000),
        StoreTexts.pilotBanner("01/11"), StoreTexts.endedOn("15/10"), StoreTexts.pending("03/10"), StoreTexts.usageLeft(320),
        StoreTexts.content(listOf("learn", "quiz"), 2, 4_200_000),
    )
    private val everything get() = StoreTexts.BUTTONS + StoreTexts.SHORT + StoreTexts.SENTENCES + parametrised

    @Test fun exactWordsOfTheDesign() {
        assertEquals("Louer gratuitement", StoreTexts.RENT_FREE)
        assertEquals("Demandez à un parent", StoreTexts.KID_TV)
        assertEquals("Prolonger", StoreTexts.EXTEND); assertEquals("Relouer", StoreTexts.RERENT)
    }

    @Test fun noPriceNoJargon() {
        val price = Regex("(?i)xaf|fcfa|€|\\$|\\d\\s?f\\b")
        for (t in everything) {
            assertFalse(price.containsMatchIn(t), "prix dans « $t »")
            for (bad in listOf("sender", "receiver", "adb", "http", "json")) assertFalse(t.lowercase().contains(bad), "« $bad » dans « $t »")
        }
    }

    @Test fun sentencesStartWithACapitalAndEndWithPunctuation() {
        val sentences = StoreTexts.SENTENCES + listOf(StoreTexts.quotaPhone(3), StoreTexts.alreadyRented("CM2", "7 jours"), StoreTexts.spacePhone(1_500_000), StoreTexts.pilotBanner("01/11"))
        assertTrue(sentences.size >= 12)
        for (t in sentences) {
            assertTrue(t.first().isUpperCase() || t.first().isDigit(), "majuscule : « $t »"); assertTrue(t.last() in listOf('.', '?', '…'), "ponctuation finale : « $t »")
        }
    }

    @Test fun shortPhrasesAndButtonsStartWithACapital() {
        val shorts = StoreTexts.SHORT + StoreTexts.BUTTONS + listOf(StoreTexts.quotaTv(3), StoreTexts.spaceTv(1_500_000), StoreTexts.pending("03/10"), StoreTexts.usageLeft(320))
        assertTrue(shorts.size >= 20)
        for (t in shorts) assertTrue(t.first().isUpperCase() || t.first().isDigit(), t)
    }

    @Test fun nothingSaysSenderOrReceiverAndTheStoreHasItsExactSentences() {
        assertTrue(StoreTexts.NO_TV_PHONE.contains("Ajouter ma TV"))
        assertEquals("Gratuit : rien à louer.", StoreTexts.FREE_NOTHING_TO_RENT)
        assertEquals("Cet article n'est pas louable pour le moment.", StoreTexts.UNKNOWN_FAMILY)
    }

    @Test fun parametrisedPhrases() {
        assertEquals("3 locations en cours sur cette TV : attendez la fin de l'une d'elles.", StoreTexts.quotaPhone(3))
        assertEquals("Demande en cours (03/10)", StoreTexts.pending("03/10"))
        assertEquals("Location terminée le 15/10 · Relouer ?", StoreTexts.endedOn("15/10"))
        assertEquals("Il vous reste 5 h 20 d'utilisation", StoreTexts.usageLeft(320))
        assertEquals("Leçons + exercices · 2 lots · 4,0 Mo", StoreTexts.content(listOf("learn", "quiz"), 2, 4_200_000))
        assertEquals("Cours de langue · 1 lot · 1,0 Mo", StoreTexts.content(listOf("langues"), 1, 1_100_000))
    }

    @Test fun shortLeftPicksTheNaturalUnit() {
        assertEquals("7 jours", StoreTexts.shortLeft(7 * 86_400_000L)); assertEquals("1 jour", StoreTexts.shortLeft(86_400_000L))
        assertEquals("5 h", StoreTexts.shortLeft(5 * 3_600_000L)); assertEquals("40 min", StoreTexts.shortLeft(40 * 60_000L))
    }

    @Test fun theViewKeepsNoPhraseOfItsOwn() {
        val src = java.io.File("src/main/kotlin/castbridge/core/store/StoreView.kt").readText()
        for (t in StoreTexts.SENTENCES + StoreTexts.SHORT + StoreTexts.BUTTONS) assertFalse(src.contains("\"$t\""), "phrase en dur dans StoreView : « $t »")
    }
}
