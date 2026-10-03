package castbridge.core.store

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Alias courts de la Boutique (w17-01) : table de règles, déterminisme, collisions. */
class StoreAliasTest {
    private val table = listOf(
        Triple("Classe CM2", "classe-cm2", "CM2"),
        Triple("Terminale C", "classe-tle-c", "TLEC"),
        Triple("Droit L1", "classe-droit-l1", "DRL1"),
        Triple("Classe 3e", "classe-3e", "3E"),
        Triple("Classe 6e", "classe-6e", "6E"),
        Triple("Mathématiques", "maths", "MATHEM"),
        Triple("Chinois A0", "langues-zh-a0", "CHA0"),
        Triple("Quiz Géographie Afrique", "quiz-geo", "QUGEAF"),
        Triple("", "classe-cm1", "CM1"),
        Triple("   ", "", "BQ"),
        Triple("Première S", "classe-1ere-s", "1RES"),
        Triple("Classe Seconde", "classe-2nde", "2DE"),
        Triple("L'Éducation civique", "educ", "EDCI"),
        Triple("CP", "classe-cp", "CP"),
    )

    @Test fun tableOfRules() {
        for ((title, id, expected) in table) assertEquals(expected, StoreAlias.of(title, id, emptySet()), "titre « $title », id « $id »")
    }

    @Test fun aliasIsShortAndUppercaseAscii() {
        for ((title, id, _) in table) {
            val a = StoreAlias.of(title, id, emptySet())
            assertTrue(a.isNotEmpty() && a.length <= 6 && a.all { it in 'A'..'Z' || it in '0'..'9' }, "alias invalide « $a »")
        }
    }

    @Test fun sameInputSameAlias() {
        val taken = setOf("AB")
        repeat(3) { assertEquals(StoreAlias.of("Classe CM2", "classe-cm2", taken), StoreAlias.of("Classe CM2", "classe-cm2", taken)) }
    }

    @Test fun collisionAddsNumericSuffix() {
        assertEquals("CM22", StoreAlias.of("Classe CM2", "classe-cm2", setOf("CM2")))
        assertEquals("CM23", StoreAlias.of("Classe CM2", "classe-cm2", setOf("CM2", "CM22")))
    }

    @Test fun collisionKeepsSixCharacters() {
        assertEquals("MATHE2", StoreAlias.of("Mathématiques", "maths", setOf("MATHEM")))
    }
}
