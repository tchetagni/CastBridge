package castbridge.core.store

import castbridge.core.lots.Bundle
import castbridge.core.lots.ClockDoubt
import castbridge.core.lots.ExpiryReason
import castbridge.core.lots.Kit
import castbridge.core.lots.LotFamilies
import castbridge.core.lots.MemoryQueueStore
import castbridge.core.lots.QueueStore
import castbridge.core.lots.RentalContract
import castbridge.core.lots.RentalState
import castbridge.core.lots.RentalStatus
import castbridge.core.lots.RentalWarning
import castbridge.core.net.JsonLite
import castbridge.core.tv.ApiReply
import java.io.File
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Les routes `/api/store*` de la TV (w17-04), appelées directement comme `RentalApiTest`, sans HTTP. */
class StoreApiTest {
    private val day = 24L * 3600 * 1000
    private val t0 = 1_800_000_000_000L
    private var clock = t0
    private var counter = 0
    private var enabled = true
    private var trial = false
    private var kid = false
    private var rentals: List<RentalStatus> = emptyList()
    private val tvId = "0123456789abcdef"
    private val dir = kotlin.io.path.createTempDirectory("storeapi").toFile().also { it.deleteOnExit() }
    private val files = StoreFiles(File(dir, "store"))
    private var queue: QueueStore = MemoryQueueStore()
    private val families = LotFamilies.explicit(free = emptySet(), reserved = setOf("learn:cm2", "quiz:cm2", "langues:zh-a0"))

    private fun facts(s: StoreCatalog.Store) = StoreView.Facts(s, clock, StoreView.Side.TV, trialTv = trial, kidActive = kid, rentals = rentals)
    private val requests by lazy { RentRequests(queue, tvId, { clock }, { "%08x".format(++counter) }) }
    private val api by lazy { StoreApi(files, listOf(Kit.pub), ::facts, requests, { enabled }, { families }, tvId) }

    private fun get(path: String, params: Map<String, String> = emptyMap()) = api.handle(path, "GET", params)
    private fun post(path: String, body: ByteArray, params: Map<String, String> = emptyMap()) = api.handleBody(path, "POST", params, body)
    private fun obj(r: ApiReply?): Map<String, Any?> = JsonLite.obj(assertNotNull(r).json)
    private fun list(v: Any?) = (v as List<*>).map { it.toString() }
    private fun seed() { files.install(StoreFiles.Doc.LOTS, StoreTestKit.lotsJson(), listOf(Kit.pub)); files.install(StoreFiles.Doc.BUNDLES, StoreTestKit.bundlesJson(), listOf(Kit.pub)) }

    private fun reqText(bundle: String = "classe-cm2", choice: String = "12h", origin: String = "phone", tv: String = tvId, nonce: String = "a1b2c3d4") =
        "castbridge-rent-request-v1\nat=$t0\nbundle=$bundle\nchoice=$choice\nkind=new\nnonce=$nonce\norigin=$origin\nperiod=0\ntv=$tv"

    private fun status(bundle: String) = RentalStatus(
        RentalContract("loc-$bundle@1", "loc-$bundle", listOf(bundle), "lic", 1, t0 - day, t0 + 10 * day, 0, 0, 3, ""),
        RentalState.ACTIVE, null, null as ClockDoubt?, null, null, RentalWarning.NONE, "")

    // ------------------------------------------------------------ drapeau éteint
    @Test fun aSwitchedOffStoreAnswersNothingOnEveryRoute() {
        enabled = false
        for (p in listOf("/api/store", "/api/store/catalog", "/api/store/requests", "/api/store/requests/ack", "/api/store/request")) {
            assertNull(api.handle(p, "GET", emptyMap()), p); assertNull(api.handle(p, "POST", emptyMap()), p)
            assertFalse(api.wantsBody(p), p)
            assertNull(api.handleBody(p, "POST", emptyMap(), "{}".toByteArray()), p)
        }
    }

    @Test fun otherPathsAreNotOurs() { assertNull(get("/api/lots")); assertNull(get("/api/storage")); assertNull(get("/api/storex")) }

    // ------------------------------------------------------------ GET /api/store
    @Test fun theShowcaseWithoutCatalogIsAnEmptyStoreAndParses() {
        val r = assertNotNull(get("/api/store")); assertEquals(200, r.status)
        val screen = StoreView.parse(r.json)
        assertTrue(screen.shelves.isEmpty()); assertEquals(StoreTexts.CATALOG_ABSENT_TV, screen.banner)
        val o = obj(r)
        assertEquals(true, o["enabled"]); assertEquals(false, o["kidActive"]); assertEquals(false, o["trial"]); assertEquals(0L, (o["pending"] as Number).toLong())
        assertNull((o["catalogAt"] as Map<*, *>)["lots"]); assertNull((o["catalogAt"] as Map<*, *>)["bundles"])
    }

    @Test fun theShowcaseShowsTheKeptCatalogsWithTheirDates() {
        seed()
        val r = assertNotNull(get("/api/store"))
        val screen = StoreView.parse(r.json)
        assertEquals(listOf("classe-cm2"), screen.shelves.flatMap { it.cards }.map { it.id })
        val at = obj(r)["catalogAt"] as Map<*, *>
        assertEquals("2026-10-01T08:00:00Z", at["lots"]); assertEquals("2026-09-28T10:00:00Z", at["bundles"])
    }

    @Test fun trialAndKidFlagsAreReported() {
        trial = true; kid = true
        val o = obj(get("/api/store")); assertEquals(true, o["trial"]); assertEquals(true, o["kidActive"])
    }

    @Test fun aCorruptCatalogFileGivesAnEmptyStoreNotAnException() {
        files.file(StoreFiles.Doc.LOTS).parentFile.mkdirs()
        files.file(StoreFiles.Doc.LOTS).writeText("{ corrompu")
        files.file(StoreFiles.Doc.BUNDLES).writeText("""{"generatedAt":"2026-09-28T10:00:00Z","signature":"x","bundles":[]}""")
        val r = assertNotNull(get("/api/store")); assertEquals(200, r.status)
        assertTrue(StoreView.parse(r.json).shelves.isEmpty())
        files.file(StoreFiles.Doc.LOTS).writeText("""{"pas":"un catalogue"}""")
        assertEquals(200, assertNotNull(get("/api/store")).status)
    }

    @Test fun rebuildingTheStoreFromTheFixturesIsFast() {
        val fx = File("src/test/kotlin/castbridge/core/store/fixtures")
        files.file(StoreFiles.Doc.LOTS).parentFile.mkdirs()
        File(fx, "store-lots-catalog.json").copyTo(files.file(StoreFiles.Doc.LOTS)); File(fx, "store-bundles-catalog.json").copyTo(files.file(StoreFiles.Doc.BUNDLES))
        get("/api/store")
        val t = System.nanoTime(); repeat(5) { get("/api/store") }
        val ms = (System.nanoTime() - t) / 5 / 1_000_000
        println("reconstruction GET /api/store : $ms ms")
        assertTrue(StoreView.parse(assertNotNull(get("/api/store")).json).shelves.isNotEmpty())
        assertTrue(ms < 50, "$ms ms")
    }

    // ------------------------------------------------------------ POST /api/store/catalog
    @Test fun twoSignedDocumentsAreAcceptedAndWritten() {
        assertTrue(api.wantsBody("/api/store/catalog"))
        val r = assertNotNull(post("/api/store/catalog", StoreTestKit.body(StoreTestKit.lotsJson(), StoreTestKit.bundlesJson())))
        assertEquals(200, r.status)
        assertEquals(listOf("lots", "bundles"), list(obj(r)["accepted"]))
        assertTrue(files.file(StoreFiles.Doc.LOTS).isFile && files.file(StoreFiles.Doc.BUNDLES).isFile)
        assertEquals(1, StoreView.parse(assertNotNull(get("/api/store")).json).shelves.flatMap { it.cards }.size)
    }

    @Test fun documentsGivenAsJsonStringsAreAcceptedToo() {
        val body = JsonLite.write(linkedMapOf("lots" to StoreTestKit.lotsJson(), "bundles" to StoreTestKit.bundlesJson())).toByteArray()
        assertEquals(listOf("lots", "bundles"), list(obj(post("/api/store/catalog", body))["accepted"]))
    }

    @Test fun theSameSendAgainAcceptsNothingAndIsNotAnError() {
        val body = StoreTestKit.body(StoreTestKit.lotsJson(), StoreTestKit.bundlesJson())
        post("/api/store/catalog", body)
        val r = assertNotNull(post("/api/store/catalog", body)); assertEquals(200, r.status)
        assertEquals(emptyList(), list(obj(r)["accepted"])); assertTrue((obj(r)["refused"] as Map<*, *>).isEmpty())
    }

    @Test fun anOlderDocumentIsRefusedWithAFrenchReasonAndTheFileIsUntouched() {
        post("/api/store/catalog", StoreTestKit.body(StoreTestKit.lotsJson("2026-10-05T08:00:00Z")))
        val before = files.file(StoreFiles.Doc.LOTS).readText()
        val r = obj(post("/api/store/catalog", StoreTestKit.body(StoreTestKit.lotsJson("2026-10-01T08:00:00Z"))))
        assertContains((r["refused"] as Map<*, *>)["lots"].toString(), "Le téléphone propose un catalogue plus ancien")
        assertEquals(emptyList(), list(r["accepted"]))
        assertEquals(before, files.file(StoreFiles.Doc.LOTS).readText())
    }

    @Test fun aWrongSignatureIsRefusedAndTheFileIsUntouched() {
        post("/api/store/catalog", StoreTestKit.body(StoreTestKit.lotsJson("2026-10-01T08:00:00Z")))
        val before = files.file(StoreFiles.Doc.LOTS).readText()
        val bad = StoreTestKit.lotsJson("2026-10-09T08:00:00Z").replace("\"cm2\"", "\"cm3\"")
        val r = obj(post("/api/store/catalog", StoreTestKit.body(bad, StoreTestKit.bundlesJson())))
        assertContains((r["refused"] as Map<*, *>)["lots"].toString(), "ignature")
        assertEquals(listOf("bundles"), list(r["accepted"]))
        assertEquals(before, files.file(StoreFiles.Doc.LOTS).readText())
    }

    @Test fun aWorksCatalogIsIgnoredAndSaidSo() {
        val r = obj(post("/api/store/catalog", StoreTestKit.body(StoreTestKit.lotsJson(), works = StoreTestKit.lotsJson())))
        assertContains((r["refused"] as Map<*, *>)["works"].toString(), "non pris en charge")
        assertEquals(listOf("lots"), list(r["accepted"]))
    }

    @Test fun anOversizedBodyIs413AndNothingIsWritten() {
        val r = assertNotNull(post("/api/store/catalog", ByteArray(StoreApi.MAX_CATALOG_BODY + 1) { 'x'.code.toByte() }))
        assertEquals(413, r.status); assertContains(obj(r)["error"].toString(), "volumineux")
        assertFalse(files.file(StoreFiles.Doc.LOTS).exists())
    }

    @Test fun anOversizedDocumentInsideTheBodyIsRefusedPerDocument() {
        val big = JsonLite.write(linkedMapOf("lots" to "x".repeat(StoreCatalog.MAX_LOTS_CATALOG_BYTES.toInt() + 1))).toByteArray()
        assertTrue(big.size <= StoreApi.MAX_CATALOG_BODY)
        assertContains(((obj(post("/api/store/catalog", big))["refused"]) as Map<*, *>)["lots"].toString().lowercase(), "volumineux")
    }

    @Test fun aBadBodyIs400() {
        assertEquals(400, assertNotNull(post("/api/store/catalog", "pas du json".toByteArray())).status)
        assertEquals(400, assertNotNull(post("/api/store/catalog", "{}".toByteArray())).status)
        assertEquals(400, assertNotNull(post("/api/store/catalog", "[1]".toByteArray())).status)
    }

    @Test fun noKeyMeansEverythingIsRefused() {
        val noKeys = StoreApi(files, emptyList(), ::facts, requests, { enabled }, { families }, tvId)
        val r = JsonLite.obj(noKeys.handleBody("/api/store/catalog", "POST", emptyMap(), StoreTestKit.body(StoreTestKit.lotsJson()))!!.json)
        assertContains((r["refused"] as Map<*, *>)["lots"].toString(), "Aucune clé de vérification")
    }

    @Test fun wrongMethodsAre405() {
        assertEquals(405, assertNotNull(api.handle("/api/store/catalog", "GET", emptyMap())).status)
        assertEquals(405, assertNotNull(api.handle("/api/store", "POST", emptyMap())).status)
        assertEquals(405, assertNotNull(api.handle("/api/store/requests", "POST", emptyMap())).status)
        assertEquals(405, assertNotNull(api.handle("/api/store/requests/ack", "GET", emptyMap())).status)
        assertEquals(405, assertNotNull(api.handle("/api/store/request", "GET", emptyMap())).status)
        assertEquals(404, assertNotNull(api.handle("/api/store/zzz", "GET", emptyMap())).status)
    }

    // ------------------------------------------------------------ demandes
    @Test fun aPhoneRequestIsDepositedAndListed() {
        seed()
        assertTrue(api.wantsBody("/api/store/request"))
        val r = assertNotNull(post("/api/store/request", reqText().toByteArray())); assertEquals(200, r.status)
        val nonce = obj(r)["nonce"].toString()
        assertEquals("PENDING", obj(r)["state"]); assertTrue(obj(r)["code"].toString().startsWith("CM2-12H-"), obj(r)["code"].toString())
        val l = obj(get("/api/store/requests"))
        val item = (l["items"] as List<*>).single() as Map<*, *>
        assertEquals(nonce, item["nonce"]); assertEquals("PENDING", item["state"]); assertEquals("phone", item["origin"]); assertEquals("classe-cm2", item["bundle"])
        assertEquals(1L, (l["pending"] as Number).toLong())
        assertEquals(1L, (obj(get("/api/store"))["pending"] as Number).toLong())
        val card = StoreView.parse(get("/api/store")!!.json).shelves.flatMap { it.cards }.single()
        assertTrue(card.lines.any { it.startsWith("Demande en cours") }, "${card.lines}")
    }

    @Test fun theRequestsRouteListsDecidedOnesToo() {
        seed()
        val n = obj(post("/api/store/request", reqText().toByteArray()))["nonce"].toString()
        api.handle("/api/store/requests/ack", "POST", mapOf("nonce" to n, "state" to "ACCEPTED"))
        val item = (obj(get("/api/store/requests"))["items"] as List<*>).single() as Map<*, *>
        assertEquals("ACCEPTED", item["state"])
    }

    @Test fun ackIsIdempotentAndRefusesContraryAnswers() {
        seed()
        val n = obj(post("/api/store/request", reqText().toByteArray()))["nonce"].toString()
        fun ack(state: String, nonce: String = n) = assertNotNull(api.handle("/api/store/requests/ack", "POST", mapOf("nonce" to nonce, "state" to state)))
        val first = ack("ACCEPTED"); assertEquals(200, first.status); assertEquals("APPLIED", obj(first)["outcome"])
        val again = ack("ACCEPTED"); assertEquals(200, again.status); assertEquals("SAME", obj(again)["outcome"]); assertEquals("ACCEPTED", obj(again)["state"])
        assertEquals(409, ack("REFUSED").status)
        assertEquals(404, ack("ACCEPTED", "ffffffff").status)
        assertEquals(400, ack("MAYBE").status)
        assertEquals(400, assertNotNull(api.handle("/api/store/requests/ack", "POST", mapOf("state" to "ACCEPTED"))).status)
    }

    @Test fun theFirstAckSaysApplied() {
        seed()
        val n = obj(post("/api/store/request", reqText().toByteArray()))["nonce"].toString()
        val r = assertNotNull(api.handle("/api/store/requests/ack", "POST", mapOf("nonce" to n, "state" to "REFUSED")))
        assertEquals("APPLIED", obj(r)["outcome"]); assertEquals("REFUSED", obj(r)["state"])
    }

    @Test fun requestsAreRefusedWithTheSameRulesAsTheTv() {
        seed()
        kid = true
        assertRefusal(reqText(), 422, "KID_PROFILE")
        kid = false; trial = true
        assertRefusal(reqText(), 422, "TRIAL_TV")
        trial = false
        assertRefusal(reqText(bundle = "inconnu"), 422, "UNKNOWN_FAMILY")
        assertRefusal(reqText(choice = "5h"), 422, "BAD_CHOICE")
        assertRefusal(reqText(origin = "tv"), 400, null)
        assertRefusal(reqText(tv = "fedcba9876543210"), 409, null)
        assertRefusal("n'importe quoi", 400, null)
        assertRefusal(reqText() + "x".repeat(1100), 413, null)
        assertEquals(0, ((obj(get("/api/store/requests"))["items"]) as List<*>).size)
    }

    private fun assertRefusal(text: String, status: Int, reason: String?) {
        val r = assertNotNull(post("/api/store/request", text.toByteArray())); assertEquals(status, r.status, r.json)
        assertTrue(obj(r)["error"].toString().isNotBlank())
        if (reason != null) assertEquals(reason, obj(r)["reason"])
    }

    @Test fun aSecondIdenticalRequestIsADuplicate() {
        seed()
        post("/api/store/request", reqText().toByteArray())
        assertRefusal(reqText(nonce = "deadbeef"), 409, "DUPLICATE")
    }

    @Test fun aLanguesBundleIsFreeEvenIfItsLotIsReserved() {
        files.install(StoreFiles.Doc.LOTS, StoreTestKit.lotsJson(lots = listOf(StoreTestKit.lot("langues", "zh-a0"))), listOf(Kit.pub))
        files.install(StoreFiles.Doc.BUNDLES, StoreTestKit.bundlesJson(bundles = listOf(Bundle("langue-zh", "langues", setOf("langues:zh-a0"), "Chinois A0", 0, 30))), listOf(Kit.pub))
        assertRefusal(reqText(bundle = "langue-zh"), 422, "FREE_BUNDLE")
    }

    @Test fun aStorageFailureIsA5xxNeverA200() {
        seed()
        queue = object : QueueStore { override fun load(): String? = null; override fun save(json: String) { throw IOException("disque plein") } }
        val r = assertNotNull(post("/api/store/request", reqText().toByteArray()))
        assertEquals(503, r.status); assertEquals("STORAGE", obj(r)["reason"])
    }

    @Test fun anAckStorageFailureIsA5xx() {
        seed()
        val failing = object : QueueStore {
            var broken = false; val inner = MemoryQueueStore()
            override fun load(): String? = inner.load()
            override fun save(json: String) { if (broken) throw IOException("disque plein"); inner.save(json) }
        }
        queue = failing
        val n = obj(post("/api/store/request", reqText().toByteArray()))["nonce"].toString()
        failing.broken = true
        assertEquals(503, assertNotNull(api.handle("/api/store/requests/ack", "POST", mapOf("nonce" to n, "state" to "ACCEPTED"))).status)
    }

    @Test fun theClockIsTheInjectedOneSoAPendingRequestExpiresAfterSevenDays() {
        seed()
        post("/api/store/request", reqText().toByteArray())
        clock += 8 * day
        val o = obj(get("/api/store")); assertEquals(0L, (o["pending"] as Number).toLong())
        assertEquals("EXPIRED", ((obj(get("/api/store/requests"))["items"] as List<*>).single() as Map<*, *>)["state"])
    }

    @Test fun aRentalThatCoversTheBundleFulfilsTheRequest() {
        seed()
        post("/api/store/request", reqText().toByteArray())
        rentals = listOf(status("classe-cm2"))
        get("/api/store")
        assertEquals("FULFILLED", ((obj(get("/api/store/requests"))["items"] as List<*>).single() as Map<*, *>)["state"])
    }
}
