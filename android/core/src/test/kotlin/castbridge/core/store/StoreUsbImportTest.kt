package castbridge.core.store

import castbridge.core.lots.FakeConsumer
import castbridge.core.lots.Kit
import castbridge.core.lots.LotNames
import castbridge.core.lots.TvLotStore
import castbridge.core.store.StoreUsbImport.Kind
import castbridge.core.store.StoreUsbImport.Status
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Import du catalogue de la Boutique par clé USB (w17-12) : mêmes vérifications que `POST /api/store/catalog`, aucun contournement. */
class StoreUsbImportTest {
    private val roots = ArrayList<File>()
    private fun tmp() = kotlin.io.path.createTempDirectory("usb").toFile().also { roots += it }
    @AfterTest fun tearDown() { roots.forEach { it.deleteRecursively() } }

    private val keys = listOf(Kit.pub)
    private val learn = FakeConsumer("learn")
    private val tvDir = tmp()
    private val files = StoreFiles(File(tvDir, "store"))
    private val lotStore = TvLotStore(File(tvDir, "lots"), mapOf("learn" to learn), keys, 10, { 0L }, 10_000_000L, { 1L })
    private val importer = StoreUsbImport(files, lotStore, keys)
    private val stick = tmp()

    private fun put(name: String, text: String) = File(stick, name).also { it.writeText(text) }
    private fun item(r: StoreUsbImport.Result, name: String) = r.items.first { it.name == name }

    @Test fun aValidCatalogIsAcceptedAndWrittenLikeThePostRoute() {
        put("lots-catalog.json", StoreTestKit.lotsJson()); put("bundles-catalog.json", StoreTestKit.bundlesJson())
        val r = importer.import(stick)
        assertEquals(Status.ACCEPTED, item(r, "lots-catalog.json").status)
        assertEquals(Status.ACCEPTED, item(r, "bundles-catalog.json").status)
        assertEquals(StoreTestKit.lotsJson(), files.read(StoreFiles.Doc.LOTS))
        assertEquals("2026-09-28T10:00:00Z", files.keptAt(StoreFiles.Doc.BUNDLES))
    }

    @Test fun sameBytesGiveTheSameStateAsTheDirectInstall() {
        val other = StoreFiles(File(tmp(), "store"))
        other.install(StoreFiles.Doc.LOTS, StoreTestKit.lotsJson(), keys); other.install(StoreFiles.Doc.BUNDLES, StoreTestKit.bundlesJson(), keys)
        put("lots-catalog.json", StoreTestKit.lotsJson()); put("bundles-catalog.json", StoreTestKit.bundlesJson())
        importer.import(stick)
        for (d in listOf(StoreFiles.Doc.LOTS, StoreFiles.Doc.BUNDLES)) assertEquals(other.read(d), files.read(d))
    }

    @Test fun aBadSignatureIsRefusedAndNothingIsWritten() {
        put("lots-catalog.json", StoreTestKit.lotsJson().replace("\"title\":\"learn cm2\"", "\"title\":\"learn xx\""))
        val i = item(importer.import(stick), "lots-catalog.json")
        assertEquals(Status.REFUSED, i.status); assertContains(i.message, "ignature")
        assertNull(files.read(StoreFiles.Doc.LOTS))
        val wrongKey = StoreUsbImport(files, lotStore, listOf(Kit.otherPub))
        put("lots-catalog.json", StoreTestKit.lotsJson())
        assertEquals(Status.REFUSED, item(wrongKey.import(stick), "lots-catalog.json").status)
        assertEquals(Status.REFUSED, item(StoreUsbImport(files, lotStore, emptyList()).import(stick), "lots-catalog.json").status)
        assertNull(files.read(StoreFiles.Doc.LOTS))
    }

    @Test fun anOlderCatalogIsRefusedByTheAntiRollback() {
        files.install(StoreFiles.Doc.LOTS, StoreTestKit.lotsJson("2026-10-02T08:00:00Z"), keys)
        put("lots-catalog.json", StoreTestKit.lotsJson("2026-10-01T08:00:00Z"))
        val i = item(importer.import(stick), "lots-catalog.json")
        assertEquals(Status.REFUSED, i.status); assertContains(i.message, "plus ancien")
        assertEquals("2026-10-02T08:00:00Z", files.keptAt(StoreFiles.Doc.LOTS))
    }

    @Test fun theSameCatalogTwiceIsUnchanged() {
        put("lots-catalog.json", StoreTestKit.lotsJson())
        importer.import(stick)
        assertEquals(Status.UNCHANGED, item(importer.import(stick), "lots-catalog.json").status)
    }

    @Test fun anOversizedDocumentIsRefusedBeforeAnySignatureWork() {
        put("lots-catalog.json", " ".repeat(StoreCatalog.MAX_LOTS_CATALOG_BYTES.toInt() + 1))
        put("bundles-catalog.json", " ".repeat(StoreCatalog.MAX_BUNDLES_CATALOG_BYTES.toInt() + 1))
        val r = importer.import(stick)
        for (n in listOf("lots-catalog.json", "bundles-catalog.json")) { assertEquals(Status.REFUSED, item(r, n).status); assertContains(item(r, n).message, "volumineux") }
        assertNull(files.read(StoreFiles.Doc.LOTS)); assertNull(files.read(StoreFiles.Doc.BUNDLES))
    }

    @Test fun anEmptyOrMissingFolderIsNotAnError() {
        val r = importer.import(stick)
        assertTrue(r.empty); assertEquals("Aucun catalogue sur la clé", r.summary())
        assertEquals("Aucun catalogue sur la clé", importer.import(null).summary())
        assertEquals("Aucun catalogue sur la clé", importer.import(File(stick, "absent")).summary())
        assertEquals("Aucun catalogue sur la clé", importer.import(put("fichier", "x")).summary())
    }

    @Test fun onlyTheValidDocumentIsKeptAndTheOtherIsExplained() {
        put("lots-catalog.json", StoreTestKit.lotsJson()); put("bundles-catalog.json", "{\"generatedAt\":\"2026-09-28T10:00:00Z\",\"signature\":\"AAAA\",\"bundles\":[]}")
        val r = importer.import(stick)
        assertEquals(Status.ACCEPTED, item(r, "lots-catalog.json").status)
        val b = item(r, "bundles-catalog.json"); assertEquals(Status.REFUSED, b.status); assertTrue(b.message.isNotBlank())
        assertNotNull(files.read(StoreFiles.Doc.LOTS)); assertNull(files.read(StoreFiles.Doc.BUNDLES))
        assertContains(r.summary(), "refusé")
    }

    @Test fun otherFilesAreIgnoredAndNeverCopied() {
        put("lots-catalog.json", StoreTestKit.lotsJson())
        put("works-catalog.json", "{}"); put("requests.json", "{}"); put("notes.txt", "x"); put("lots-catalog.json.bak", "x")
        val r = importer.import(stick)
        assertEquals(1, r.items.size); assertEquals(4, r.ignored)
        assertFalse(files.file(StoreFiles.Doc.WORKS).exists()); assertFalse(files.requestsFile().exists())
    }

    @Test fun fatStyleNamesInOtherCaseAreTolerated() {
        put("LOTS-CATALOG.JSON", StoreTestKit.lotsJson())
        val r = importer.import(stick)
        assertEquals(Status.ACCEPTED, r.items.single().status)
        assertEquals("lots-catalog.json", files.file(StoreFiles.Doc.LOTS).name)
    }

    @Test fun twoNamesThatDifferOnlyByCaseAreBothRefused() {
        // une clé ext4 peut porter les deux : on ne devine pas lequel est le bon
        val a = File(stick, "lots-catalog.json"); val b = File(stick, "LOTS-CATALOG.json")
        a.writeText(StoreTestKit.lotsJson("2026-10-01T08:00:00Z")); b.writeText(StoreTestKit.lotsJson("2026-10-03T08:00:00Z"))
        if (stick.list()!!.size < 2) return                                      // système de fichiers insensible à la casse : cas impossible ici
        val r = importer.import(stick)
        assertEquals(Status.REFUSED, r.items.single().status); assertContains(r.items.single().message, "casse"); assertNull(files.read(StoreFiles.Doc.LOTS))
    }

    @Test fun paddingAndControlBytesAreRefused() {
        File(stick, "lots-catalog.json").writeBytes(StoreTestKit.lotsJson().toByteArray() + ByteArray(16))
        val i = item(importer.import(stick), "lots-catalog.json")
        assertEquals(Status.REFUSED, i.status); assertContains(i.message, "octets")
        assertNull(files.read(StoreFiles.Doc.LOTS))
    }

    @Test fun invalidUtf8IsRefused() {
        File(stick, "bundles-catalog.json").writeBytes(byteArrayOf(0x7b, 0xC3.toByte(), 0x28, 0x7d))
        assertEquals(Status.REFUSED, importer.import(stick).items.single().status)
    }

    @Test fun aSymbolicLinkIsRefusedAndNotFollowed() {
        val outside = File(tmp(), "secret.json").also { it.writeText(StoreTestKit.lotsJson()) }
        Files.createSymbolicLink(File(stick, "lots-catalog.json").toPath(), outside.toPath())
        val r = importer.import(stick)
        assertEquals(Status.REFUSED, r.items.single().status); assertContains(r.items.single().message, "ien symbolique")
        assertNull(files.read(StoreFiles.Doc.LOTS))
    }

    @Test fun aSymbolicLinkedLotIsRefused() {
        val outside = File(tmp(), "x.lot").also { it.writeBytes(ByteArray(10)) }
        Files.createSymbolicLink(File(stick, "castbridge-lot-learn-cm2-v1.lot").toPath(), outside.toPath())
        assertEquals(Status.REFUSED, importer.import(stick).items.single().status)
    }

    @Test fun aDirectoryNamedLikeADocumentIsRefused() {
        File(stick, "lots-catalog.json").mkdir()
        assertEquals(Status.REFUSED, importer.import(stick).items.single().status)
    }

    @Test fun dotDotNamesAreRefusedNotIgnored() {
        File(stick, "..lots-catalog.json").writeText("x")
        val r = importer.import(stick)
        assertEquals(Status.REFUSED, r.items.single().status); assertContains(r.items.single().message, "suspect")
    }

    @Test fun storeDirIsFoundCaseInsensitivelyAndNeverThroughALink() {
        val root = tmp()
        assertNull(StoreUsbImport.storeDir(root))
        File(root, "CASTBRIDGE/STORE").mkdirs()
        assertEquals("STORE", StoreUsbImport.storeDir(root)!!.name)
        val root2 = tmp(); val real = tmp(); File(real, "store").mkdirs()
        Files.createSymbolicLink(File(root2, "CastBridge").toPath(), real.toPath())
        assertNull(StoreUsbImport.storeDir(root2))
        val root3 = tmp(); File(root3, "CastBridge").mkdirs(); Files.createSymbolicLink(File(root3, "CastBridge/store").toPath(), real.toPath())
        assertNull(StoreUsbImport.storeDir(root3))
    }

    // ------------------------------------------------------------------ lots

    private fun lotOnStick(scope: String = "cm2", version: Int = 1, proofFile: Boolean = true, size: Int = 3000, seed: Int = 7, upper: Boolean = false): Pair<String, ByteArray> {
        val data = Kit.bytes(seed, size); val m = Kit.meta("learn", scope, version, data)
        val name = LotNames.fileName(m)
        File(stick, if (upper) name.uppercase() else name).writeBytes(data)
        val proof = Kit.sign(listOf(m)).toJson()
        if (proofFile) File(stick, (if (upper) name.uppercase() else name) + LotNames.PROOF_SUFFIX).writeText(proof)
        return name to data
    }

    @Test fun aSignedLotGoesThroughInstallReceived() {
        val (name, _) = lotOnStick()
        val r = importer.import(stick)
        val i = item(r, name); assertEquals(Kind.LOT, i.kind); assertEquals(Status.ACCEPTED, i.status)
        assertEquals(1, learn.installs.size)
        assertEquals(0, lotStore.received(name))                                  // pas de reste dans la boîte de réception
    }

    @Test fun aCorruptedLotIsRefusedBySha256() {
        val (name, data) = lotOnStick()
        File(stick, name).writeBytes(data.also { it[5] = (it[5] + 1).toByte() })
        val i = item(importer.import(stick), name)
        assertEquals(Status.REFUSED, i.status); assertContains(i.message, "SHA-256")
        assertTrue(learn.installs.isEmpty())
    }

    @Test fun aLotWithoutAnyProofIsRefused() {
        val (name, _) = lotOnStick(proofFile = false)
        val i = item(importer.import(stick), name)
        assertEquals(Status.REFUSED, i.status); assertContains(i.message, "signé")
        assertTrue(learn.installs.isEmpty())
    }

    @Test fun theLotsCatalogOfTheStickServesAsProofWhenThereIsNoProofFile() {
        val data = Kit.bytes(3, 2000); val m = Kit.meta("learn", "cm2", 1, data)
        File(stick, LotNames.fileName(m)).writeBytes(data); put("lots-catalog.json", Kit.sign(listOf(m)).toJson())
        val r = importer.import(stick)
        assertEquals(Status.ACCEPTED, item(r, LotNames.fileName(m)).status); assertEquals(1, learn.installs.size)
    }

    @Test fun aProofSignedByAnotherKeyIsRefused() {
        val data = Kit.bytes(3, 2000); val m = Kit.meta("learn", "cm2", 1, data)
        File(stick, LotNames.fileName(m)).writeBytes(data)
        val forged = Kit.sign(listOf(m)).toJson()
        File(stick, LotNames.fileName(m) + ".json").writeText(forged)
        val strict = TvLotStore(File(tvDir, "lots2"), mapOf("learn" to learn), listOf(Kit.otherPub), 10, { 0L }, 10_000_000L, { 1L })
        val i = item(StoreUsbImport(files, strict, listOf(Kit.otherPub)).import(stick), LotNames.fileName(m))
        assertEquals(Status.REFUSED, i.status); assertTrue(learn.installs.isEmpty())
    }

    @Test fun anOversizedLotIsRefusedWithoutReadingIt() {
        val name = "castbridge-lot-learn-cm2-v1.lot"
        val small = TvLotStore(File(tvDir, "lots3"), mapOf("learn" to learn), keys, 10, { 0L }, 1000L, { 1L })
        File(stick, name).writeBytes(ByteArray(5000)); put("$name.json", "{}")
        val i = item(StoreUsbImport(files, small, keys).import(stick), name)
        assertEquals(Status.REFUSED, i.status); assertContains(i.message, "trop gros")
    }

    @Test fun lotFileNamesInOtherCaseAreTolerated() {
        val (name, _) = lotOnStick(upper = true)
        val r = importer.import(stick)
        assertEquals(Status.ACCEPTED, r.items.single().status); assertEquals(name, r.items.single().name)
    }

    @Test fun withoutALotStoreLotsAreRefusedButCatalogsStillWork() {
        val (name, _) = lotOnStick(); put("lots-catalog.json", StoreTestKit.lotsJson())
        val r = StoreUsbImport(files, null, keys).import(stick)
        assertEquals(Status.ACCEPTED, item(r, "lots-catalog.json").status); assertEquals(Status.REFUSED, item(r, name).status)
    }

    @Test fun anInstallRefusedByTheConsumerIsReportedNotThrown() {
        learn.failInstall = true
        val (name, _) = lotOnStick()
        assertEquals(Status.REFUSED, item(importer.import(stick), name).status)
    }

    @Test fun anAlreadyInstalledLotIsAcceptedWithoutReinstall() {
        val (name, _) = lotOnStick()
        importer.import(stick); lotOnStick()
        val i = item(importer.import(stick), name)
        assertTrue(i.status == Status.ACCEPTED || i.status == Status.UNCHANGED); assertEquals(1, learn.installs.size)
    }

    @Test fun neverTouchesFilesOutsideTheStoreFolders() {
        put("lots-catalog.json", StoreTestKit.lotsJson()); lotOnStick()
        importer.import(stick)
        val written = tvDir.walkTopDown().filter { it.isFile }.map { it.relativeTo(tvDir).path }.toList()
        assertTrue(written.all { it.startsWith("store/") || it.startsWith("lots/") }, written.toString())
        assertEquals(3, stick.list()!!.size)                                      // la clé n'est jamais modifiée
    }
}
