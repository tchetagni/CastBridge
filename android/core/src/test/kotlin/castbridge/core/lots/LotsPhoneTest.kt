package castbridge.core.lots

import java.io.File
import kotlin.test.*

class LotManifestTest {
    private val a = Kit.bytes(1, 100)
    private val ma = Kit.meta("learn", "cm2", 3, a, "Apprendre CM2 « é »")

    @Test fun signedCatalogVerifiesAndRoundTrips() {
        val c = Kit.sign(listOf(ma, Kit.meta("quiz", "cm2", 1, a)))
        val back = LotManifest.parse(c.toJson())
        assertEquals(c, back)
        assertTrue(back.signatureValid(Kit.pub))
        assertFalse(back.signatureValid(Kit.otherPub))
        assertTrue(back.signedByAny(listOf(Kit.otherPub, Kit.pub)))
        assertTrue(back.vouches(ma)); assertFalse(back.vouches(ma.copy(bytes = 101)))
    }

    @Test fun anyChangeBreaksTheSignature() {
        val c = Kit.sign(listOf(ma))
        for (t in listOf(c.copy(channel = "beta"), c.copy(feature = "quiz"), c.copy(generatedAt = "2027"),
            c.copy(lots = listOf(ma.copy(version = 4))), c.copy(lots = listOf(ma.copy(sha256 = "0".repeat(64)))),
            c.copy(lots = listOf(ma.copy(bytes = 1))), c.copy(lots = listOf(ma.copy(title = "x"))), c.copy(lots = listOf(ma.copy(minAppVersion = 9))), c.copy(lots = emptyList())))
            assertFalse(t.signatureValid(Kit.pub), "tampered: $t")
    }

    @Test fun orderOfEntriesDoesNotMatter() {
        val b = Kit.meta("quiz", "3e", 2, a)
        val c = Kit.sign(listOf(ma, b))
        assertTrue(c.copy(lots = listOf(b, ma)).signatureValid(Kit.pub))
    }

    @Test fun badEntriesAreRejected() {
        assertFailsWith<IllegalArgumentException> { LotManifest.parse("""{"channel":"stable","generatedAt":"x","signature":"s","lots":[{"feature":"../x","scope":"a","version":1,"bytes":1,"sha256":"${"0".repeat(64)}"}]}""") }
        assertFailsWith<IllegalArgumentException> { LotManifest.parse("{}") }
    }

    /** Built and signed by the server (backend LotsApiTest / LotCatalog.java, RFC 8032 test key 1): both sides build the same payload. */
    @Test fun verifiesACatalogSignedByTheServer() {
        val fixture = """{"channel":"stable","feature":"learn","generatedAt":"2026-10-01T13:05:24+01:00","lots":[{"feature":"learn","scope":"fx1","version":3,"bytes":6247,"sha256":"c599ce1dac301011732f3be6b2708958b2d6dcdd833811b9042f2b7cb545a7b3","title":"Apprendre « fx1 »","minAppVersion":0}],"keyId":"21fe31dfa154a261","signature":"TSo2cSUD5ePqLQePMPhwQBzuwn8TyKhFYNl41lg1CSyflVwj5MG7uxEbLYf7mS8+eQAjIjkpIMELWViiMPZIBw=="}"""
        val c = LotManifest.parse(fixture)
        assertTrue(c.signatureValid("11qYAYKxCrfVS/7TyWQHOg7hcvPapiMlrwIaaPcHURo="), c.canonicalPayload())
        assertFalse(c.copy(lots = c.lots.map { it.copy(bytes = it.bytes + 1) }).signatureValid("11qYAYKxCrfVS/7TyWQHOg7hcvPapiMlrwIaaPcHURo="))
        assertEquals(LotId("learn", "fx1"), c.lots.single().id); assertEquals("Apprendre « fx1 »", c.lots.single().title)
    }

    @Test fun lotNames() {
        assertEquals("castbridge-lot-learn-cm2-v3.lot", LotNames.fileName(ma))
        assertEquals(LotId("learn", "cm2") to 3, LotNames.parseFileName("castbridge-lot-learn-cm2-v3.lot"))
        assertNull(LotNames.parseFileName("castbridge-lot-../x-cm2-v3.lot")); assertNull(LotNames.parseFileName("movie.mp4"))
        assertEquals(LotId("quiz", "droit-l1"), LotNames.parseKey("quiz:droit-l1")); assertNull(LotNames.parseKey("quiz"))
    }
}

class LotStoreTest {
    private val dir = Kit.tmp()
    private var clock = 1000L
    private fun store(max: Long = 1000) = LotStore(dir, max) { clock++ }
    @AfterTest fun tearDown() { dir.deleteRecursively() }

    private fun lot(scope: String, version: Int, size: Int, feature: String = "learn", seed: Int = scope.hashCode() + version) = Kit.bytes(seed, size).let { Kit.meta(feature, scope, version, it) to it }
    private fun stage(s: LotStore, m: LotMeta, d: ByteArray): File = s.partFile(m).also { it.parentFile.mkdirs(); it.writeBytes(d) }
    private fun put(s: LotStore, m: LotMeta, d: ByteArray, protect: (LotId) -> Boolean = { false }) = s.install(m, stage(s, m, d), "proof", protect)

    @Test fun installsVerifiesAndPersists() {
        val s = store(); val (m, d) = lot("cm2", 1, 100)
        assertEquals(LotStore.Install.Ok(emptyList(), null), put(s, m, d))
        assertEquals(100, s.usedBytes()); assertEquals("proof", s.proof(m.id)); assertContentEquals(d, s.open(m.id, 1)!!.readBytes())
        assertNull(s.open(m.id, 2))
        val again = store()                                       // phone restart
        assertEquals(listOf(m), again.catalog()); assertTrue(again.file(m.id, 1)!!.isFile)
    }

    @Test fun exactlyAtTheCapIsAccepted_oneByteOverIsFull() {
        val s = store(1000)
        val (a, da) = lot("a", 1, 500); val (b, db) = lot("b", 1, 500); val (c, dc) = lot("c", 1, 1)
        assertIs<LotStore.Install.Ok>(put(s, a, da)); assertIs<LotStore.Install.Ok>(put(s, b, db))
        assertEquals(1000, s.usedBytes()); assertEquals(0, s.freeBytes())
        val r = put(s, c, dc, protect = { true })                 // everything protected: nothing can be evicted
        assertIs<LotStore.Install.Full>(r); assertEquals(1, r.missingBytes); assertContains(r.reason, "Stockage plein")
        assertEquals(2, s.list().size, "nothing evicted when it would not be enough")
    }

    @Test fun evictsLeastRecentlyUsedFirstAndNeverProtected() {
        val s = store(1000)
        val (a, da) = lot("a", 1, 300); val (b, db) = lot("b", 1, 300); val (c, dc) = lot("c", 1, 300)
        put(s, a, da); put(s, b, db); put(s, c, dc)
        s.open(a.id, 1)!!.close()                                 // a used recently, so b is the oldest
        val (d, dd) = lot("d", 1, 300)
        val r = put(s, d, dd) as LotStore.Install.Ok
        assertEquals(listOf(b.id), r.evicted)
        assertEquals(setOf("a", "c", "d"), s.list().map { it.meta.id.scope }.toSet())
        val (e, de) = lot("e", 1, 600)
        val r2 = put(s, e, de, protect = { it.scope == "a" || it.scope == "d" })   // a and d protected: c goes, then nothing else can
        assertIs<LotStore.Install.Full>(r2)
        val (f, df) = lot("f", 1, 300)
        assertEquals(listOf(c.id), (put(s, f, df, protect = { it.scope == "a" || it.scope == "d" }) as LotStore.Install.Ok).evicted)
    }

    @Test fun corruptedOrTruncatedLotIsRefusedAndDeleted() {
        val s = store(); val (m, d) = lot("cm2", 1, 100)
        val bad = d.copyOf().also { it[5] = (it[5] + 1).toByte() }
        val f = stage(s, m, bad)
        assertContains((s.install(m, f) as LotStore.Install.Refused).reason, "corrompu"); assertFalse(f.exists())
        val g = stage(s, m, d.copyOf(60))
        assertContains((s.install(m, g) as LotStore.Install.Refused).reason, "incomplet"); assertTrue(s.list().isEmpty())
    }

    @Test fun updateReplacesAtomicallyAndAFailedUpdateKeepsThePreviousVersion() {
        val s = store(); val (v1, d1) = lot("cm2", 1, 100); val (v2, d2) = lot("cm2", 2, 120)
        put(s, v1, d1)
        // update arrives corrupted: rollback = version 1 stays
        stage(s, v2, d2.copyOf().also { it[0] = (it[0] + 1).toByte() }).let { assertIs<LotStore.Install.Refused>(s.install(v2, it)) }
        assertEquals(1, s.get(v1.id)!!.meta.version); assertContentEquals(d1, s.open(v1.id, 1)!!.readBytes())
        assertEquals(LotStore.Install.Ok(emptyList(), 1), put(s, v2, d2))
        assertEquals(120, s.usedBytes()); assertNull(s.file(v1.id, 1)); assertContentEquals(d2, s.open(v1.id, 2)!!.readBytes())
        val old = stage(s, v1, d1)
        assertContains((s.install(v1, old) as LotStore.Install.Refused).reason, "plus récente")   // no downgrade
    }

    @Test fun updateOfTheOnlyLotFitsEvenWhenFull() {
        val s = store(100); val (v1, d1) = lot("cm2", 1, 100); val (v2, d2) = lot("cm2", 2, 100)
        put(s, v1, d1); assertIs<LotStore.Install.Ok>(put(s, v2, d2))
        assertEquals(100, s.usedBytes())
    }

    @Test fun aLotBiggerThanTheWholeStoreIsExplained() {
        val s = store(100); val (m, d) = lot("cm2", 1, 101)
        assertContains((put(s, m, d) as LotStore.Install.Full).reason, "dépasse")
    }

    @Test fun vanishedFileIsForgottenAtStartup() {
        val s = store(); val (m, d) = lot("cm2", 1, 10); put(s, m, d)
        s.file(m.id, 1)!!.delete()
        assertTrue(store().list().isEmpty())
    }
}

class LotSyncTest {
    private val dir = Kit.tmp()
    private lateinit var store: LotStore
    private val remote = FakeRemote()
    private var net = Net.UNMETERED
    private val pause = ArrayList<Long>()
    private val a = Kit.bytes(1, 5000); private val b = Kit.bytes(2, 3000); private val c = Kit.bytes(3, 4000)
    private val ma = Kit.meta("learn", "cm2", 1, a); private val mb = Kit.meta("learn", "3e", 1, b); private val mc = Kit.meta("quiz", "cm2", 1, c)

    @BeforeTest fun setUp() { store = LotStore(dir, 100_000); remote.published = listOf(Published(ma, a), Published(mb, b), Published(mc, c)) }
    @AfterTest fun tearDown() { dir.deleteRecursively() }
    private fun sync(app: Int = 10, keys: List<String> = listOf(Kit.pub)) = LotSync(store, remote, keys, app, { net }, { pause += it })

    @Test fun firstSyncDownloadsTheSelectionOnly() {
        val r = sync().sync(listOf(ma.id, mc.id))
        assertTrue(r.ok); assertEquals(listOf(LotSync.Outcome.INSTALLED, LotSync.Outcome.INSTALLED), r.results.map { it.outcome })
        assertEquals(setOf(ma.id, mc.id), store.list().map { it.meta.id }.toSet())
        assertTrue(store.proof(ma.id)!!.contains("signature"), "the catalog that vouched for it is kept")
        assertTrue(LotManifest.parse(store.proof(ma.id)!!).vouches(ma))
    }

    @Test fun laterSyncsFetchOnlyWhatChanged() {
        sync().sync(listOf(ma.id, mb.id))
        remote.downloads.clear()
        val a2 = Kit.bytes(9, 5200); val ma2 = Kit.meta("learn", "cm2", 2, a2)
        remote.published = listOf(Published(ma2, a2), Published(mb, b), Published(mc, c))
        val r = sync().updateAll()
        assertEquals(mapOf(ma.id to LotSync.Outcome.UPDATED, mb.id to LotSync.Outcome.UP_TO_DATE), r.results.associate { it.id to it.outcome })
        assertEquals(listOf("learn:cm2@0"), remote.downloads, "one lot, nothing else")
        assertEquals(2, store.get(ma.id)!!.meta.version)
    }

    @Test fun resumesAfterACutAtEveryOffset() {
        val m = ma
        for (cut in listOf(0, 1, 2, 777, 2500, 4998, 4999)) {
            store.remove(m.id); store.partFile(m).delete()
            remote.cutAfter = cut; remote.downloads.clear(); pause.clear()
            val r = sync().sync(listOf(m.id))
            assertTrue(r.ok, "cut at $cut: $r")
            assertContentEquals(a, store.open(m.id, 1)!!.readBytes())
            assertEquals(2, remote.downloads.size, "cut at $cut"); assertEquals("learn:cm2@$cut", remote.downloads[1], "resumes from the bytes already received")
            assertEquals(listOf(2000L), pause)
        }
    }

    @Test fun neverAPartialInstallWhenTheServerKeepsCutting() {
        val s = LotSync(store, object : LotRemote by remote {
            override fun open(m: LotMeta, offset: Long): LotRemote.Stream { remote.cutAfter = 100; return remote.open(m, offset) }
        }, listOf(Kit.pub), 10, { net }, { })
        val r = s.sync(listOf(ma.id), opt = LotSync.Options(maxAttempts = 3))
        // each attempt gets 100 more bytes: not finished, never installed
        assertEquals(LotSync.Outcome.FAILED, r.results.single().outcome); assertNull(store.get(ma.id))
        assertEquals(300, store.partFile(ma).length(), "the partial file is kept for the next try")
    }

    @Test fun corruptedDownloadIsRefusedThenFetchedClean() {
        val bad = a.copyOf().also { it[10] = (it[10] + 1).toByte() }
        remote.published = listOf(Published(ma, bad))
        val r = sync().sync(listOf(ma.id))
        assertEquals(LotSync.Outcome.FAILED, r.results.single().outcome); assertContains(r.results.single().message, "corrompu")
        assertNull(store.get(ma.id)); assertFalse(store.partFile(ma).exists())
        remote.published = listOf(Published(ma, a))
        assertTrue(sync().sync(listOf(ma.id)).ok)
    }

    @Test fun wifiOnlyAndOffline() {
        net = Net.METERED
        val r = sync().sync(listOf(ma.id)); assertContains(r.blocked!!, "Wi-Fi"); assertEquals(0, remote.catalogCalls)
        assertTrue(sync().sync(listOf(ma.id), opt = LotSync.Options(wifiOnly = false)).ok)
        net = Net.NONE
        assertContains(sync().sync(listOf(mb.id)).blocked!!, "Pas de connexion")
        net = Net.UNMETERED; remote.down = true
        assertContains(sync().sync(listOf(mb.id)).blocked!!, "injoignable")
        assertEquals(1, store.list().size, "what is stored stays usable")
    }

    @Test fun unsignedOrForeignCatalogIsIgnored() {
        assertContains(sync(keys = listOf(Kit.otherPub)).sync(listOf(ma.id)).blocked!!, "Signature")
        remote.signWith = { Kit.sign(it).copy(signature = "AAAA") }
        assertContains(sync().sync(listOf(ma.id)).blocked!!, "Signature"); assertTrue(store.list().isEmpty())
    }

    @Test fun fullStoreSkipsWithAReasonAndTooNewLotsWait() {
        store = LotStore(dir, 6000)
        val r = sync().sync(listOf(ma.id, mb.id), protect = { true })
        assertEquals(LotSync.Outcome.INSTALLED, r.results[0].outcome); assertEquals(LotSync.Outcome.SKIPPED_FULL, r.results[1].outcome)
        assertContains(r.results[1].message, "Stockage plein")
        val m5 = Kit.meta("learn", "5e", 1, b, minApp = 99); remote.published += Published(m5, b)
        assertEquals(LotSync.Outcome.SKIPPED_APP_TOO_OLD, sync().sync(listOf(m5.id)).results.single().outcome)
        assertEquals(LotSync.Outcome.NOT_IN_CATALOG, sync().sync(listOf(LotId("learn", "nope"))).results.single().outcome)
    }

    @Test fun unpublishedLotStopsWithoutRetry() {
        remote.gone = true
        val r = sync().sync(listOf(ma.id)); assertEquals(LotSync.Outcome.FAILED, r.results.single().outcome); assertTrue(pause.isEmpty())
    }
}

class LotPlannerTest {
    private fun m(f: String, s: String, size: Long, v: Int = 1) = LotMeta(LotId(f, s), v, size, "ab".repeat(32), "$f $s")
    private val mb = 1L shl 20

    @Test fun fitsExactlyAtTheCapAndSkipsOneByteOver() {
        val onPhone = listOf(m("learn", "cm2", 6 * mb), m("quiz", "cm2", 4 * mb - 0))
        val needs = LotPlanner.needsOf(listOf(ProfileNeed(listOf("cm2"), active = true)))
        val p = LotPlanner.plan(needs, onPhone, emptyList(), starterBytes = 0)
        assertEquals(10 * mb, p.usedBytes); assertEquals(2, p.wanted.size); assertTrue(p.skipped.isEmpty()); assertEquals(0, p.remainingBytes)
        val over = LotPlanner.plan(needs, onPhone.map { if (it.id.feature == "quiz") it.copy(bytes = it.bytes + 1) else it }, emptyList(), 0)
        // smallest first: quiz (4 Mo + 1) is kept, learn (6 Mo) is the one that no longer fits
        assertEquals(listOf(LotId("learn", "cm2")), over.skipped.map { it.meta.id }); assertContains(over.skipped.single().reason, "il manque")
        assertEquals(listOf(LotId("quiz", "cm2")), over.skipped.single().dropSuggestion, "the phone proposes what could go")
    }

    @Test fun starterDataCountsInTheBudget() {
        val onPhone = listOf(m("learn", "cm2", 5 * mb))
        val needs = listOf(Need(LotId("learn", "cm2"), 0))
        assertEquals(1, LotPlanner.plan(needs, onPhone, emptyList(), starterBytes = 5 * mb).wanted.size)
        val p = LotPlanner.plan(needs, onPhone, emptyList(), starterBytes = 5 * mb + 1)
        assertTrue(p.wanted.isEmpty()); assertEquals(1, p.skipped.size)
        assertTrue(LotPlanner.plan(needs, onPhone, emptyList(), starterBytes = 11 * mb).skipped.single().reason.contains("même vide"))
    }

    @Test fun priorityThenSmallestAndDeterministic() {
        val onPhone = listOf(m("learn", "3e", 4 * mb), m("quiz", "3e", mb), m("learn", "cm2", 6 * mb), m("quiz", "cm2", 2 * mb))
        val needs = LotPlanner.needsOf(listOf(ProfileNeed(listOf("cm2")), ProfileNeed(listOf("3e"), active = true)))
        val a = LotPlanner.plan(needs, onPhone, emptyList(), 0)
        // active profile (3e) first, then cm2: 3e quiz 1 + 3e learn 4 + cm2 quiz 2 = 7 Mo; cm2 learn (6) does not fit
        assertEquals(listOf("quiz 3e", "learn 3e", "quiz cm2"), a.wanted.map { "${it.id.feature} ${it.id.scope}" })
        assertEquals(listOf(LotId("learn", "cm2")), a.skipped.map { it.meta.id })
        assertEquals(a, LotPlanner.plan(needs.reversed(), onPhone.reversed(), emptyList(), 0), "same plan whatever the order of the inputs")
    }

    @Test fun diffAgainstTheTvAndMissingLots() {
        val have = m("learn", "cm2", mb)
        val onPhone = listOf(have.copy(version = 2), m("quiz", "cm2", mb))
        val tv = listOf(have)
        val p = LotPlanner.plan(listOf(Need(have.id, 0), Need(LotId("quiz", "cm2"), 1), Need(LotId("learn", "6e"), 2)), onPhone, tv, 0)
        assertEquals(2, p.toSend.size, "older version on the TV: send the newest, directly"); assertEquals(listOf(LotId("learn", "6e")), p.notOnPhone)
        val same = LotPlanner.plan(listOf(Need(have.id, 0)), listOf(have), tv, 0)
        assertTrue(same.toSend.isEmpty() && same.wanted.size == 1)
    }
}
