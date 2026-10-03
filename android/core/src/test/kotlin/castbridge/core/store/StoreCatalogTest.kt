package castbridge.core.store

import castbridge.core.lots.Bundle
import castbridge.core.lots.BundleCatalog
import castbridge.core.lots.Edition
import castbridge.core.lots.LotFamilies
import castbridge.core.lots.LotFamily
import castbridge.core.lots.LotId
import castbridge.core.lots.LotMeta
import castbridge.core.lots.LotNames
import castbridge.core.store.StoreCatalog.Shelf
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Fusion des deux catalogues signés en articles de Boutique (w17-01), sur deux fixtures non signées et sur des cas construits. */
class StoreCatalogTest {
    private val dir = File("src/test/kotlin/castbridge/core/store/fixtures")
    private val lotsJson = File(dir, "store-lots-catalog.json").readText()
    private val bundlesJson = File(dir, "store-bundles-catalog.json").readText()
    private val families = LotFamilies.explicit(
        free = setOf("langues:zh-a0"),
        reserved = setOf("learn:cm2", "quiz:cm2", "learn:3e", "quiz:3e", "learn:droit-l1", "quiz:geo-afrique", "learn:x", "quiz:x"),
    )

    private fun meta(feature: String, scope: String, bytes: Long = 1000, title: String = "$feature $scope") = LotMeta(
        LotId(feature, scope), 1, bytes, "0".repeat(63) + "1", title, 0, if (scope.endsWith("-trial")) Edition.TRIAL else Edition.FULL)

    private fun bundle(id: String, type: String, vararg lots: String, title: String = id, raw: Long = 0) = Bundle(id, type, lots.toSet(), title, raw)

    private val store get() = StoreCatalog.fromJson(lotsJson, bundlesJson, families)
    private fun item(s: StoreCatalog.Store, id: String) = assertNotNull(s.items.find { it.id == id }, "article « $id » absent : ${s.items.map { it.id }}")
    private fun keys(l: List<LotMeta>) = l.map { LotNames.key(it.id) }

    // (a)
    @Test fun twoFixturesGiveThreeClassItemsOnTheLearnShelf() {
        val classes = store.items.filter { it.shelf == Shelf.APPRENDRE }
        assertEquals(listOf("classe-cm2", "classe-3e", "classe-droit-l1"), classes.map { it.id })
        assertEquals(listOf("Primaire", "Secondaire", "Supérieur"), classes.map { it.section })
        assertEquals(listOf("Classe CM2", "Classe 3e", "Droit L1"), classes.map { it.title })
    }

    @Test fun cm2SizeIsTheSumOfLotBytesWhenRawBytesIsZeroAndLatestVersionIsUsed() {
        val cm2 = item(store, "classe-cm2")
        assertEquals(listOf("learn:cm2", "quiz:cm2"), keys(cm2.lots))
        assertEquals(3, cm2.lots.first { it.id.feature == "learn" }.version)
        assertEquals(4_200_000L, cm2.bytes)
        assertEquals(LotFamily.RESERVED, cm2.family)
    }

    @Test fun rawBytesWinsWhenSet() {
        assertEquals(6_100_000L, item(store, "classe-3e").bytes)
    }

    // (c)
    @Test fun trialLotsAttachToTheirFullTwinAndNeverFormAnItem() {
        val s = store
        assertEquals(listOf("learn:cm2-trial", "quiz:cm2-trial"), keys(item(s, "classe-cm2").trialLots))
        assertTrue(s.items.none { it.lots.any { l -> l.edition == Edition.TRIAL } }, "un lot d'essai est dans lots")
        assertTrue(s.items.none { "trial" in it.id }, "un article d'essai seul")
    }

    @Test fun trialLotWithoutFullTwinIsOnlyAWarning() {
        val s = store
        assertTrue(s.warnings.any { "learn:cp-trial" in it }, s.warnings.toString())
    }

    // (b)
    @Test fun languageLotsWithoutBundleBecomeOneItemEach() {
        val langues = store.items.filter { it.shelf == Shelf.LANGUES }
        assertEquals(listOf("lot:langues:ar-a0", "lot:langues:zh-a0"), langues.map { it.id })
        assertEquals(LotFamily.FREE, langues.first { it.id.endsWith("zh-a0") }.family)
        assertNull(langues.first { it.id.endsWith("ar-a0") }.family)
        assertTrue(langues.all { it.bundle == null && it.lots.size == 1 })
        assertEquals(900_000L, langues.first { it.id.endsWith("zh-a0") }.bytes)
    }

    @Test fun quizLotAloneGoesToTheQuizShelf() {
        val q = item(store, "lot:quiz:geo-afrique")
        assertEquals(Shelf.QUIZ, q.shelf)
        assertEquals(LotFamily.RESERVED, q.family)
    }

    @Test fun quizLotWithoutBundleAndWithoutLearnTwinGoesToQuiz() {
        val s = StoreCatalog.build(listOf(meta("quiz", "cm2")), BundleCatalog(emptyList()), families)
        assertEquals(listOf(Shelf.QUIZ), s.items.map { it.shelf })
        assertTrue(!s.degraded)
    }

    // (d)
    @Test fun withoutBundleCatalogEveryFullLotIsAnItemAndTheStoreIsDegraded() {
        val s = StoreCatalog.fromJson(lotsJson, null, families)
        assertTrue(s.degraded)
        assertNull(s.catalogAtBundles)
        assertEquals(
            listOf("lot:learn:cm2", "lot:learn:3e", "lot:learn:droit-l1", "lot:langues:ar-a0", "lot:langues:zh-a0", "lot:quiz:cm2", "lot:quiz:geo-afrique", "lot:quiz:3e").toSet(),
            s.items.map { it.id }.toSet())
        assertEquals(Shelf.APPRENDRE, item(s, "lot:learn:cm2").shelf)
        assertEquals(Shelf.QUIZ, item(s, "lot:quiz:cm2").shelf)
        assertEquals(listOf("learn:cm2-trial"), keys(item(s, "lot:learn:cm2").trialLots))
        assertTrue(s.items.none { it.bundle != null })
    }

    // (e)
    @Test fun oversizedDocumentsAreRefusedInFrench() {
        val bigLots = " ".repeat((StoreCatalog.MAX_LOTS_CATALOG_BYTES + 1).toInt())
        val m1 = assertFailsWith<StoreCatalog.Refused> { StoreCatalog.fromJson(bigLots, bundlesJson, families) }.message.orEmpty()
        assertTrue("256 Ko" in m1 && "refusé" in m1, m1)
        val bigBundles = " ".repeat((StoreCatalog.MAX_BUNDLES_CATALOG_BYTES + 1).toInt())
        val m2 = assertFailsWith<StoreCatalog.Refused> { StoreCatalog.fromJson(lotsJson, bigBundles, families) }.message.orEmpty()
        assertTrue("64 Ko" in m2 && "refusé" in m2, m2)
    }

    @Test fun checkSizeIsNullAtTheLimitAndAPhraseAboveIt() {
        assertNull(StoreCatalog.checkSize("a".repeat(100), 100))
        val phrase = assertNotNull(StoreCatalog.checkSize("a".repeat(101), 100))
        assertTrue("refusé" in phrase, phrase)
        assertNotNull(StoreCatalog.checkSize("é".repeat(60), 100), "la taille compte les octets UTF-8, pas les caractères")
    }

    @Test fun unreadableDocumentIsRefusedNotThrownRaw() {
        assertFailsWith<StoreCatalog.Refused> { StoreCatalog.fromJson("pas du json", null, families) }
        assertFailsWith<StoreCatalog.Refused> { StoreCatalog.fromJson(lotsJson, "{\"x\":1}", families) }
    }

    // (f)
    @Test fun orderIsShelfThenSectionThenScopeThenId() {
        assertEquals(
            listOf("classe-cm2", "classe-3e", "classe-droit-l1", "lot:langues:ar-a0", "lot:langues:zh-a0", "lot:quiz:geo-afrique"),
            store.items.map { it.id })
    }

    @Test fun orderDoesNotDependOnInputOrder() {
        val lots = listOf(meta("learn", "cm2"), meta("quiz", "cm2"), meta("learn", "6e"), meta("learn", "cp"), meta("learn", "gce-ol"), meta("learn", "zzz"), meta("langues", "b"), meta("langues", "a"))
        val b = BundleCatalog(listOf(bundle("classe-cm2", "classe", "learn:cm2", "quiz:cm2")))
        val a = StoreCatalog.build(lots, b, families)
        val z = StoreCatalog.build(lots.reversed(), b, families)
        assertEquals(a.items.map { it.id }, z.items.map { it.id })
        assertEquals(listOf("Primaire", "Primaire", "Secondaire", "Supérieur", "Autres"), a.items.filter { it.shelf == Shelf.APPRENDRE }.map { it.section })
        assertEquals(listOf("classe-cm2", "lot:learn:cp", "lot:learn:6e", "lot:learn:gce-ol", "lot:learn:zzz"), a.items.filter { it.shelf == Shelf.APPRENDRE }.map { it.id })
    }

    // (g)
    @Test fun catalogDateIsTheOldestOfTheTwoDocuments() {
        val s = store
        assertEquals("2026-10-01T08:00:00Z", s.catalogAtLots)
        assertEquals("2026-09-28T10:00:00Z", s.catalogAtBundles)
        assertEquals("2026-09-28T10:00:00Z", s.catalogAt)
        assertEquals("Catalogue du 28/09", s.catalogLabel)
    }

    @Test fun degradedStoreShowsTheLotsDate() {
        assertEquals("Catalogue du 01/10", StoreCatalog.fromJson(lotsJson, null, families).catalogLabel)
        assertEquals("", StoreCatalog.build(emptyList(), null, families).catalogLabel)
    }

    // cas limites
    @Test fun bundleCitingAnAbsentLotIgnoresItAndWarns() {
        val s = store
        assertEquals(listOf("learn:3e", "quiz:3e"), keys(item(s, "classe-3e").lots))
        assertTrue(s.warnings.any { "learn:3e-annales" in it && "classe-3e" in it }, s.warnings.toString())
    }

    @Test fun lotInTwoBundlesBelongsToBothAndIsNotAnOrphanItem() {
        val lots = listOf(meta("learn", "x"), meta("quiz", "x"))
        val b = BundleCatalog(listOf(bundle("a", "classe", "learn:x", "quiz:x"), bundle("b", "classe", "learn:x")))
        val s = StoreCatalog.build(lots, b, families)
        assertEquals(listOf("a", "b"), s.items.map { it.id }.sorted())
        assertEquals(listOf("learn:x"), keys(item(s, "b").lots))
        assertEquals(listOf("learn:x", "quiz:x"), keys(item(s, "a").lots))
        assertTrue(s.items.none { it.id.startsWith("lot:") })
    }

    @Test fun emptyCatalogsNeverThrow() {
        val s = StoreCatalog.build(emptyList(), BundleCatalog(emptyList()), families)
        assertEquals(emptyList(), s.items)
        assertTrue(!s.degraded)
        assertEquals(emptyList(), StoreCatalog.build(emptyList(), null, families).items)
    }

    @Test fun mixedOrUnknownFamilyIsNeverFree() {
        val lots = listOf(meta("langues", "zh-a0"), meta("learn", "x"), meta("langues", "ar-a0"))
        val b = BundleCatalog(listOf(bundle("m", "", "langues:zh-a0", "learn:x"), bundle("u", "", "langues:zh-a0", "langues:ar-a0")))
        val s = StoreCatalog.build(lots, b, families)
        assertEquals(LotFamily.RESERVED, item(s, "m").family)
        assertNull(item(s, "u").family)
    }

    @Test fun bundleTypeDecidesTheShelfElseTheFunctionOfItsLots() {
        val lots = listOf(meta("langues", "zh-a0"), meta("learn", "x"), meta("quiz", "x"), meta("misc", "z"))
        val b = BundleCatalog(listOf(bundle("t1", "langues", "learn:x"), bundle("t2", "", "langues:zh-a0"), bundle("t3", "quiz", "quiz:x"), bundle("t4", "", "misc:z"), bundle("t5", "", "learn:x", "quiz:x")))
        val s = StoreCatalog.build(lots, b, families)
        assertEquals(Shelf.LANGUES, item(s, "t1").shelf)
        assertEquals(Shelf.LANGUES, item(s, "t2").shelf)
        assertEquals(Shelf.QUIZ, item(s, "t3").shelf)
        assertEquals(Shelf.AUTRES, item(s, "t4").shelf)
        assertEquals(Shelf.APPRENDRE, item(s, "t5").shelf)
    }

    // alias dans les articles
    @Test fun itemsCarryDeterministicUniqueAliases() {
        val s = store
        assertEquals("CM2", item(s, "classe-cm2").alias)
        assertEquals("3E", item(s, "classe-3e").alias)
        assertEquals("DRL1", item(s, "classe-droit-l1").alias)
        assertEquals(s.items.size, s.items.map { it.alias }.toSet().size, "alias en double")
        assertEquals(s.items.map { it.alias }, store.items.map { it.alias })
    }

    @Test fun twoBundlesWithTheSameTitleGetDistinctAliasesInCatalogOrder() {
        val lots = listOf(meta("learn", "x"), meta("learn", "y"))
        val b = BundleCatalog(listOf(bundle("b1", "classe", "learn:x", title = "Classe CM2"), bundle("b2", "classe", "learn:y", title = "Classe CM2")))
        val s = StoreCatalog.build(lots, b, families)
        assertEquals("CM2", item(s, "b1").alias)
        assertEquals("CM22", item(s, "b2").alias)
    }
}
