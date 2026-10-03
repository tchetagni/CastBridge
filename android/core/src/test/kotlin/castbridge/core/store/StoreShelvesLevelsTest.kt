package castbridge.core.store

import castbridge.core.lots.Bundle
import castbridge.core.lots.BundleCatalog
import castbridge.core.lots.LotFamilies
import castbridge.core.lots.LotId
import castbridge.core.lots.LotMeta
import castbridge.core.store.StoreCatalog.Shelf
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Rayons par niveau (décision du propriétaire du 2026-10-03) : Quiz et Langues rangés par niveau, sur les VRAIS lots publiés. */
class StoreShelvesLevelsTest {
    private val dir = File("src/test/kotlin/castbridge/core/store/fixtures")
    private val families = LotFamilies.explicit(free = emptySet(), reserved = emptySet())
    private val quiz = StoreCatalog.fromJson(File(dir, "store-quiz-real-lots.json").readText(), null, families)
    private val langues = StoreCatalog.fromJson(File(dir, "store-langues-real-lots.json").readText(), null, families)

    private fun meta(feature: String, scope: String) = LotMeta(LotId(feature, scope), 1, 1000, "0".repeat(63) + "1", "$feature $scope", 0)
    private fun sections(s: StoreCatalog.Store, shelf: Shelf) = s.items.filter { it.shelf == shelf }.groupBy { it.section }.mapValues { it.value.size }

    @Test fun everyRealQuizLotLandsInExactlyOneSubShelfAndNoneInOthers() {
        assertEquals(93, quiz.items.size)
        assertEquals(93, quiz.items.map { it.id }.toSet().size)
        assertTrue(quiz.items.all { it.shelf == Shelf.QUIZ })
        assertEquals(linkedMapOf("Primaire" to 11, "Secondaire" to 66, "Supérieur" to 13, "Culture générale" to 3), sections(quiz, Shelf.QUIZ))
        assertTrue(quiz.warnings.isEmpty(), quiz.warnings.toString())
    }

    @Test fun quizSubShelvesAreOrderedByLevel() {
        val ids = quiz.items.map { it.id.removePrefix("lot:quiz:") }
        assertEquals(listOf("Primaire", "Secondaire", "Supérieur", "Culture générale"), quiz.items.map { it.section }.distinct())
        val prim = quiz.items.filter { it.section == "Primaire" }.map { it.id.removePrefix("lot:quiz:") }
        assertEquals(listOf("cp", "ce1", "ce2", "cm1", "cm2"), prim.filter { !it.startsWith("class") })
        assertEquals(listOf("class-1", "class-2", "class-3", "class-4", "class-5", "class-6"), prim.filter { it.startsWith("class") })
        val sec = quiz.items.filter { it.section == "Secondaire" }.map { it.id.removePrefix("lot:quiz:") }
        assertEquals(listOf("6e", "5e", "4e", "3e"), sec.filter { Regex("^\\de$").matches(it) })
        fun idx(p: String) = sec.indexOfFirst { it.startsWith(p) }
        assertTrue(idx("6e") < idx("form-1") && idx("form-1") < idx("2nde") && idx("2nde") < idx("1re") && idx("1re") < idx("tle"), sec.toString())
        assertTrue(idx("lower-sixth") > idx("2nde") && idx("upper-sixth") > idx("lower-sixth"), sec.toString())
        val sup = quiz.items.filter { it.section == "Supérieur" }.map { it.id.removePrefix("lot:quiz:") }
        val lv = sup.map { Regex("l([123])$").find(it)!!.groupValues[1].toInt() }
        assertEquals(lv.sorted(), lv, sup.toString())
        assertTrue(ids.isNotEmpty())
    }

    @Test fun everyRealLanguesLotLandsInOneLevelSubShelf() {
        assertEquals(46, langues.items.size)
        assertTrue(langues.items.all { it.shelf == Shelf.LANGUES })
        assertEquals(linkedMapOf("A0" to 38, "B1" to 2, "B2" to 2, "C1" to 2, "C2" to 2), sections(langues, Shelf.LANGUES))
        assertEquals(listOf("A0", "B1", "B2", "C1", "C2"), langues.items.map { it.section }.distinct())
        assertTrue(langues.warnings.isEmpty(), langues.warnings.toString())
        // A0 reste groupé par langue (ordre stable de la portée)
        val a0 = langues.items.filter { it.section == "A0" }.map { it.id }
        assertEquals(a0.sortedBy { it.removePrefix("lot:langues:") }, a0)
    }

    @Test fun languesStayFreeEvenWhenFamilyIsReserved() {
        val strict = LotFamilies.explicit(free = emptySet(), reserved = setOf("langues:zh-a0-salut-fr"))
        val s = StoreCatalog.build(listOf(meta("langues", "zh-a0-salut-fr")), null, strict)
        assertEquals(Shelf.LANGUES, s.items.single().shelf)
        assertEquals("A0", s.items.single().section)
    }

    @Test fun allCefrLevelsIncludingNatifAreOrderedAndInputOrderDoesNotMatter() {
        val scopes = listOf("fr-natif-x", "fr-c2-x", "fr-c1-x", "fr-b2-x", "fr-b1-x", "fr-a2-x", "fr-a1-x", "ar-a0-x")
        val a = StoreCatalog.build(scopes.map { meta("langues", it) }, null, families)
        val b = StoreCatalog.build(scopes.reversed().map { meta("langues", it) }, null, families)
        assertEquals(listOf("A0", "A1", "A2", "B1", "B2", "C1", "C2", "NATIF"), a.items.map { it.section })
        assertEquals(a.items.map { it.id }, b.items.map { it.id })
    }

    @Test fun unknownLevelGoesToAutresWithAWarning() {
        val s = StoreCatalog.build(listOf(meta("langues", "fr-zz-x"), meta("langues", "fr"), meta("quiz", "zzz"), meta("quiz", "cm2")), null, families)
        val l = s.items.filter { it.shelf == Shelf.LANGUES }
        assertEquals(listOf("Autres", "Autres"), l.map { it.section })
        assertEquals(listOf("Primaire", "Autres"), s.items.filter { it.shelf == Shelf.QUIZ }.map { it.section })
        assertEquals(3, s.warnings.count { it.contains("niveau inconnu") }, s.warnings.toString())
        assertTrue(s.warnings.any { it.contains("lot:quiz:zzz") })
    }

    @Test fun cultureScopesAreGeneralCultureOnlyOnTheQuizShelf() {
        val s = StoreCatalog.build(listOf("culture-cm", "geo-afrique", "monde", "afrique", "general").map { meta("quiz", it) } + meta("learn", "culture-cm"), null, families)
        assertEquals(setOf("Culture générale"), s.items.filter { it.shelf == Shelf.QUIZ }.map { it.section }.toSet())
        assertEquals("Autres", s.items.single { it.shelf == Shelf.APPRENDRE }.section)
    }

    @Test fun learnShelfUsesTheSameLevelsForAnglophoneAndHigherScopes() {
        val scopes = listOf("class-3", "form-2", "lower-sixth", "upper-sixth", "2nde", "1re", "tle", "l2-x", "droit-l3", "gce-al", "cp")
        val s = StoreCatalog.build(scopes.map { meta("learn", it) }, null, families)
        val bySection = s.items.groupBy { it.section }.mapValues { e -> e.value.map { it.id.removePrefix("lot:learn:") } }
        assertEquals(listOf("class-3", "cp"), bySection["Primaire"]!!.sorted())
        assertEquals(setOf("form-2", "lower-sixth", "upper-sixth", "2nde", "1re", "tle"), bySection["Secondaire"]!!.toSet())
        assertEquals(setOf("droit-l3", "gce-al"), bySection["Supérieur"]!!.toSet())
        assertEquals(listOf("l2-x"), bySection["Autres"])
        assertEquals(listOf("form-2", "2nde", "1re", "tle").let { it }, bySection["Secondaire"]!!.filter { it in setOf("form-2", "2nde", "1re", "tle") })
    }

    @Test fun bundleLevelFollowsItsLotsAndAliasesStillAreUnique() {
        val b = BundleCatalog(listOf(Bundle("pack-a", "quiz", setOf("quiz:cm2", "quiz:cp"), "Pack A", 0), Bundle("pack-b", "langues", setOf("langues:fr-b2-x", "langues:fr-a1-y"), "Pack B", 0)))
        val s = StoreCatalog.build(listOf(meta("quiz", "cm2"), meta("quiz", "cp"), meta("langues", "fr-b2-x"), meta("langues", "fr-a1-y")), b, families)
        assertEquals("Primaire", s.items.single { it.id == "pack-a" }.section)
        assertEquals("A1", s.items.single { it.id == "pack-b" }.section)
        assertEquals(s.items.size, s.items.map { it.alias }.toSet().size)
        assertEquals(quiz.items.size, quiz.items.map { it.alias }.toSet().size)
    }

    @Test fun viewGroupsOneShelfViewPerSubShelfInOrder() {
        val groups = ArrayList<Pair<Shelf, String>>()
        quiz.items.forEach { if (groups.lastOrNull() != it.shelf to it.section) groups += it.shelf to it.section }
        assertEquals(4, groups.size)
        assertEquals(groups.distinct(), groups)
    }
}
