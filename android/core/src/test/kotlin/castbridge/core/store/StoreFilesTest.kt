package castbridge.core.store

import castbridge.core.lots.Kit
import castbridge.core.store.StoreFiles.Doc
import castbridge.core.store.StoreFiles.Installed
import java.io.File
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Écriture sûre, vérifiée et anti-retour des catalogues de la Boutique sur la TV (w17-04), chemin réutilisable par l'import USB (w17-12). */
class StoreFilesTest {
    private val dir = kotlin.io.path.createTempDirectory("store").toFile().also { it.deleteOnExit() }
    private val files = StoreFiles(File(dir, "store"))
    private val keys = listOf(Kit.pub)

    @Test fun aSignedLotsCatalogIsWrittenAndReadBack() {
        val json = StoreTestKit.lotsJson()
        val r = assertIs<Installed.Written>(files.install(Doc.LOTS, json, keys))
        assertEquals("2026-10-01T08:00:00Z", r.generatedAt)
        assertEquals(json, files.read(Doc.LOTS))
        assertEquals("2026-10-01T08:00:00Z", files.keptAt(Doc.LOTS))
        assertEquals("lots-catalog.json", files.file(Doc.LOTS).name)
        assertEquals("requests.json", files.requestsFile().name)
    }

    @Test fun aSignedBundlesCatalogIsWritten() {
        assertIs<Installed.Written>(files.install(Doc.BUNDLES, StoreTestKit.bundlesJson(), keys))
        assertEquals("2026-09-28T10:00:00Z", files.keptAt(Doc.BUNDLES))
    }

    @Test fun theSameDocumentTwiceIsUnchangedNotAnError() {
        val json = StoreTestKit.lotsJson()
        files.install(Doc.LOTS, json, keys)
        val before = files.file(Doc.LOTS).readText()
        assertEquals(Installed.Unchanged, files.install(Doc.LOTS, json, keys))
        assertEquals(before, files.file(Doc.LOTS).readText())
    }

    @Test fun anOlderDocumentIsRefusedAndTheFileIsUntouched() {
        files.install(Doc.LOTS, StoreTestKit.lotsJson("2026-10-02T08:00:00Z"), keys)
        val before = files.file(Doc.LOTS).readText()
        val r = assertIs<Installed.Refused>(files.install(Doc.LOTS, StoreTestKit.lotsJson("2026-10-01T08:00:00Z"), keys))
        assertContains(r.message, "plus ancien")
        assertEquals(before, files.file(Doc.LOTS).readText())
        files.install(Doc.BUNDLES, StoreTestKit.bundlesJson("2026-09-30T10:00:00Z"), keys)
        assertContains(assertIs<Installed.Refused>(files.install(Doc.BUNDLES, StoreTestKit.bundlesJson("2026-09-28T10:00:00Z"), keys)).message, "plus ancien")
    }

    @Test fun aNewerDocumentReplacesTheKeptOne() {
        files.install(Doc.LOTS, StoreTestKit.lotsJson("2026-10-01T08:00:00Z"), keys)
        assertIs<Installed.Written>(files.install(Doc.LOTS, StoreTestKit.lotsJson("2026-10-03T08:00:00Z"), keys))
        assertEquals("2026-10-03T08:00:00Z", files.keptAt(Doc.LOTS))
    }

    @Test fun aWrongSignatureOrAnUnsignedDocumentIsRefusedAndNothingIsWritten() {
        val r = assertIs<Installed.Refused>(files.install(Doc.LOTS, StoreTestKit.lotsJson(), listOf(Kit.otherPub)))
        assertContains(r.message, "ignature")
        assertFalse(files.file(Doc.LOTS).exists())
        val unsigned = StoreTestKit.lotsJson().replace(Regex("\"signature\"\\s*:\\s*\"[^\"]*\""), "\"signature\":\"UNSIGNED\"")
        assertContains(assertIs<Installed.Refused>(files.install(Doc.LOTS, unsigned, keys)).message, "non signé")
        assertContains(assertIs<Installed.Refused>(files.install(Doc.BUNDLES, StoreTestKit.bundlesJson(), listOf(Kit.otherPub))).message, "ignature")
        assertFalse(files.file(Doc.BUNDLES).exists())
        assertContains(assertIs<Installed.Refused>(files.install(Doc.LOTS, "pas du json", keys)).message, "illisible")
    }

    @Test fun noPublicKeyMeansEverythingIsRefused() {
        for (k in listOf(emptyList(), listOf(" "))) {
            assertContains(assertIs<Installed.Refused>(files.install(Doc.LOTS, StoreTestKit.lotsJson(), k)).message, "Aucune clé de vérification")
            assertContains(assertIs<Installed.Refused>(files.install(Doc.BUNDLES, StoreTestKit.bundlesJson(), k)).message, "Aucune clé de vérification")
        }
        assertFalse(files.file(Doc.LOTS).exists())
    }

    @Test fun theSizeCeilingIsAppliedBeforeAnySignatureWork() {
        val big = "x".repeat(StoreCatalog.MAX_LOTS_CATALOG_BYTES.toInt() + 1)
        assertContains(assertIs<Installed.Refused>(files.install(Doc.LOTS, big, keys)).message.lowercase(), "volumineux")
        val bigBundles = "x".repeat(StoreCatalog.MAX_BUNDLES_CATALOG_BYTES.toInt() + 1)
        assertContains(assertIs<Installed.Refused>(files.install(Doc.BUNDLES, bigBundles, keys)).message.lowercase(), "volumineux")
        // même sans clé : le plafond de taille répond d'abord
        assertContains(assertIs<Installed.Refused>(files.install(Doc.LOTS, big, emptyList())).message.lowercase(), "volumineux")
    }

    @Test fun theWorksCatalogIsNotSupportedYet() {
        assertContains(assertIs<Installed.Refused>(files.install(Doc.WORKS, "{}", keys)).message, "non pris en charge")
        assertFalse(files.file(Doc.WORKS).exists())
    }

    @Test fun aMissingOrCorruptFileReadsAsNothing() {
        assertNull(files.read(Doc.LOTS)); assertNull(files.keptAt(Doc.LOTS))
        files.file(Doc.LOTS).parentFile.mkdirs(); files.file(Doc.LOTS).writeText("{ pas du json")
        assertNull(files.read(Doc.LOTS)); assertNull(files.keptAt(Doc.LOTS))
    }

    @Test fun aCorruptMainFileFallsBackToTheBackupAndIsReplacedByANewerDocument() {
        files.install(Doc.LOTS, StoreTestKit.lotsJson("2026-10-01T08:00:00Z"), keys)
        files.install(Doc.LOTS, StoreTestKit.lotsJson("2026-10-02T08:00:00Z"), keys)
        files.file(Doc.LOTS).writeText("truncated {")
        assertEquals("2026-10-01T08:00:00Z", files.keptAt(Doc.LOTS))
        assertIs<Installed.Written>(files.install(Doc.LOTS, StoreTestKit.lotsJson("2026-10-02T08:00:00Z"), keys))
    }

    @Test fun concurrentInstallsNeverGoBackwards() {
        val docs = (1..8).map { StoreTestKit.lotsJson("2026-10-0${it}T08:00:00Z") }
        val go = CountDownLatch(1)
        val results = java.util.Collections.synchronizedList(ArrayList<Installed>())
        val threads = docs.shuffled().map { d -> thread { go.await(); results += files.install(Doc.LOTS, d, keys) } }
        go.countDown(); threads.forEach { it.join() }
        assertEquals("2026-10-08T08:00:00Z", files.keptAt(Doc.LOTS))
        assertTrue(results.none { it is Installed.Failed }, "$results")
        assertTrue(results.any { it is Installed.Written })
    }
}
