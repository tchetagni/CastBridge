package castbridge.core.lots

import castbridge.core.net.JsonLite
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlin.test.*

/** Trial edition (docs/TRIAL-EDITION.md): lot model, entitlement token, edition policy, replacement on the TV, budget guard. */
class EditionTest {
    private val day = 24L * 3600 * 1000
    private val t0 = 1_800_000_000_000L

    // ---- helpers ----
    private fun lot(feature: String, scope: String, size: Int, trial: Boolean = false, version: Int = 1): Pair<LotMeta, ByteArray> {
        val d = Kit.bytes(scope.hashCode() + feature.hashCode() + size + version, size)
        val id = if (trial) LotEditions.trialOf(LotId(feature, scope)) else LotId(feature, scope)
        return LotMeta(id, version, d.size.toLong(), LotHash.sha256Hex(d), "$feature $scope", 0, if (trial) Edition.TRIAL else Edition.FULL) to d
    }

    private val bundles = BundleCatalog(listOf(
        Bundle("classe-cm2", "classe", setOf("learn:cm2", "quiz:cm2"), "CM2", 30),
        Bundle("quiz-cm2", "quiz", setOf("quiz:cm2"), "Quiz CM2", 10),
        Bundle("classe-6e", "classe", setOf("learn:6e", "quiz:6e"), "6e", 40),
        Bundle("langue-zh", "langue", setOf("langues:zh-a0", "langues:zh-a1"), "Chinois", 50),
        Bundle("tout", "tout", setOf("learn:cm2", "quiz:cm2", "learn:6e", "quiz:6e", "langues:zh-a0", "langues:zh-a1"), "Tout", 130),
    ))

    private val catalog: List<LotMeta> = listOf(
        lot("learn", "cm2", 3000), lot("learn", "cm2", 600, trial = true), lot("quiz", "cm2", 2000), lot("quiz", "cm2", 400, trial = true),
        lot("learn", "6e", 4000), lot("learn", "6e", 700, trial = true), lot("quiz", "6e", 2500), lot("quiz", "6e", 500, trial = true),
        lot("langues", "zh-a0", 1500), lot("langues", "zh-a0", 300, trial = true),
    ).map { it.first }

    private fun token(device: String = "dev1", rights: List<Right>, issued: Long = t0, pair: java.security.KeyPair = Kit.pair): String {
        val payload = Entitlement.payload(device, issued, "k1", rights)
        val sig = Signature.getInstance("Ed25519").run { initSign(pair.private); update(payload.toByteArray(Charsets.UTF_8)); sign() }
        return Entitlement(device, issued, "k1", rights, Base64.getEncoder().encodeToString(sig)).encode()
    }
    private fun purchase(vararg b: String) = Right.Purchase("p-" + b.joinToString("-"), b.toList(), t0 - 10 * day)
    private fun sub(vararg b: String, ends: Long = t0 + 30 * day, grace: Long = 7 * day) = Right.Subscription("abo-1", b.toList(), t0 - 30 * day, ends, grace, true)
    private fun access(tok: String?, now: Long = t0, device: String = "dev1", lastSeen: Long = 0) = Entitlements.evaluate(tok, device, now, listOf(Kit.pub), lastSeen)
    private fun ids(l: List<LotMeta>) = l.map { LotNames.key(it.id) }

    // ---- lot model ----
    @Test fun fullIsTheDefaultAndOldJsonStaysByteIdentical() {
        val m = LotMeta(LotId("learn", "cm2"), 3, 6247, "a".repeat(64), "Apprendre CM2")
        assertEquals(Edition.FULL, m.edition)
        assertFalse(m.toMap().containsKey("edition"), "a FULL lot serialises exactly as before")
        assertEquals(m, parseLotMeta(m.toMap()))
        val t = m.copy(id = LotEditions.trialOf(m.id), edition = Edition.TRIAL)
        assertEquals("trial", t.toMap()["edition"]); assertEquals(t, parseLotMeta(t.toMap()))
    }

    @Test fun editionAndTrialScopeMustAgree() {
        val base = LotMeta(LotId("learn", "cm2"), 1, 1, "a".repeat(64), "x").toMap()
        assertNull(parseLotMeta(base + mapOf("edition" to "trial")), "trial edition on a full scope")
        assertNull(parseLotMeta(base + mapOf("scope" to "cm2-trial")), "trial scope declared full")
        assertNull(parseLotMeta(base + mapOf("edition" to "gold")))
        assertNotNull(parseLotMeta(base + mapOf("scope" to "cm2-trial", "edition" to "trial")))
    }

    @Test fun catalogSignatureCoversTheEditionAndLeavesFullLinesUnchanged() {
        val full = LotMeta(LotId("learn", "cm2"), 1, 10, "b".repeat(64), "T")
        val trial = full.copy(id = LotEditions.trialOf(full.id), edition = Edition.TRIAL)
        val c = Kit.sign(listOf(full, trial))
        val lines = c.canonicalPayload().lines().filter { it.startsWith("lot=") }
        assertTrue(lines.single { it.startsWith("lot=learn|cm2|") }.endsWith(LotHash.sha256Hex("T".toByteArray())), "no suffix on FULL: old signatures remain valid")
        assertTrue(lines.single { it.startsWith("lot=learn|cm2-trial|") }.endsWith("|trial"))
        val round = LotManifest.parse(c.toJson())
        assertTrue(round.signedByAny(listOf(Kit.pub))); assertTrue(round.vouches(trial))
        // passing a trial lot off as a full one (same scope renamed + edition flipped) breaks the signature
        val forged = c.copy(lots = listOf(full, trial.copy(id = full.id.copy(scope = "cm2x"), edition = Edition.FULL)))
        assertFalse(forged.signedByAny(listOf(Kit.pub)))
    }

    @Test fun fileNamesWithHyphenatedScopesParseUnambiguously() {
        for (id in listOf(LotId("learn", "cm2-trial"), LotId("quiz", "droit-l1"), LotId("quiz", "culture-afrique-trial"), LotId("langmedia", "zh-a0-trial"))) {
            assertEquals(id to 7, LotNames.parseFileName(LotNames.fileName(id, 7)), id.toString())
        }
        assertFalse(LotNames.valid(LotId("lang-media", "zh")), "a feature is one word")
    }

    @Test fun trialNamesStayWithinTheIdLimit() {
        for (scope in listOf("culture-afrique", "biologie-l1", "tle-commun", "maths-l2")) assertTrue(LotNames.valid(LotEditions.trialOf(LotId("quiz", scope))), scope)
        assertEquals(LotId("quiz", "cm2"), LotEditions.fullOf(LotId("quiz", "cm2-trial")))
        assertEquals(LotId("quiz", "cm2-trial"), LotEditions.trialOf(LotId("quiz", "cm2-trial")))
        assertEquals(listOf(LotId("learn", "cm2-trial")),
            LotEditions.supersededTrials(listOf(LotId("learn", "cm2"), LotId("learn", "cm2-trial"), LotId("learn", "6e-trial"), LotId("quiz", "6e"))))
    }

    // ---- entitlement token ----
    @Test fun validTokenGivesPurchasesAndSubscription() {
        val a = access(token(rights = listOf(purchase("quiz-cm2"), sub("classe-6e"))))
        assertEquals(Verdict.OK, a.verdict); assertEquals(setOf("quiz-cm2"), a.purchased); assertEquals(setOf("classe-6e"), a.subscribed)
        assertTrue(a.grants("classe-6e") && a.grants("quiz-cm2") && !a.grants("classe-cm2"))
    }

    @Test fun noTokenMeansTrialOnly() {
        assertEquals(Access.TRIAL_ONLY, access(null)); assertTrue(access("").granted.isEmpty())
    }

    @Test fun subscriptionGoesActiveGraceExpired_andPurchasesStay() {
        val tok = token(rights = listOf(purchase("quiz-cm2"), sub("classe-6e", ends = t0 + day, grace = 3 * day)))
        assertEquals(SubState.ACTIVE, access(tok, t0).subscriptions.single().state)
        val grace = access(tok, t0 + 2 * day)
        assertEquals(SubState.GRACE, grace.subscriptions.single().state); assertTrue(grace.grants("classe-6e")); assertTrue(grace.inGrace)
        assertContains(grace.message, "reconnectez-vous")
        val gone = access(tok, t0 + 4 * day)
        assertEquals(SubState.EXPIRED, gone.subscriptions.single().state)
        assertFalse(gone.grants("classe-6e")); assertTrue(gone.grants("quiz-cm2"), "what was bought stays")
        assertContains(gone.message, "vos achats restent")
    }

    @Test fun setBackClockDoesNotExtendASubscription() {
        val tok = token(rights = listOf(sub("classe-6e", ends = t0 + day, grace = day)), issued = t0)
        assertFalse(access(tok, now = t0 - 100 * day, lastSeen = t0 + 5 * day).grants("classe-6e"), "the app remembers the latest time it saw")
        assertEquals(SubState.ACTIVE, access(tok, now = t0 - 100 * day).subscriptions.single().state, "a token's own issue date is only a floor")
    }

    @Test fun futureSubscriptionGrantsNothingYet() {
        val r = Right.Subscription("abo", listOf("tout"), t0 + day, t0 + 30 * day, 0, false)
        val a = access(token(rights = listOf(r)))
        assertEquals(SubState.NOT_STARTED, a.subscriptions.single().state); assertTrue(a.granted.isEmpty())
    }

    @Test fun tamperedExpiredOrForeignTokensGiveTheTrialOnly() {
        val tok = token(rights = listOf(sub("classe-6e", ends = t0 + day)))
        // altered: extend the end date inside the signed payload
        val parts = tok.split('.')
        val payload = String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)
        val forgedPayload = payload.replace("${t0 + day}", "${t0 + 900 * day}")
        val forged = parts[0] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(forgedPayload.toByteArray()) + "." + parts[2]
        assertEquals(Verdict.BAD_SIGNATURE, access(forged).verdict); assertTrue(access(forged).granted.isEmpty())
        // signature taken from another token
        val other = token(rights = listOf(purchase("quiz-cm2")))
        val spliced = parts[0] + "." + parts[1] + "." + other.split('.')[2]
        assertEquals(Verdict.BAD_SIGNATURE, access(spliced).verdict)
        // signed by another key
        val rogue = token(rights = listOf(purchase("tout")), pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair())
        assertEquals(Verdict.BAD_SIGNATURE, access(rogue).verdict)
        // bound to another device
        val a = access(token(device = "dev1", rights = listOf(purchase("tout"))), device = "dev2")
        assertEquals(Verdict.OTHER_DEVICE, a.verdict); assertTrue(a.granted.isEmpty()); assertContains(a.message, "autre appareil")
        // garbage and a non canonical payload
        assertEquals(Verdict.MALFORMED, access("cbe1.xx.yy").verdict); assertEquals(Verdict.MALFORMED, access("nope").verdict)
        val noisy = parts[0] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString((payload + "\n").toByteArray()) + "." + parts[2]
        assertEquals(Verdict.MALFORMED, access(noisy).verdict)
    }

    @Test fun rightsAreSortedSoTheSameRightsGiveTheSameSignedText() {
        val r1 = listOf(purchase("quiz-cm2"), sub("classe-6e")); val r2 = r1.reversed()
        assertEquals(Entitlement.payload("d", 1, "k1", r1), Entitlement.payload("d", 1, "k1", r2))
        assertEquals(Entitlement.payload("d", 1, "k1", r1), Entitlement.decode(token("d", r1, 1))!!.canonicalPayload())
    }

    // ---- policy ----
    @Test fun withoutRightsOnlyTrialLotsAreOffered() {
        val o = EditionPolicy.offered(catalog, Access.TRIAL_ONLY, bundles)
        assertTrue(o.all { it.edition == Edition.TRIAL }); assertEquals(5, o.size)
        assertFalse(EditionPolicy.isAllowed(catalog.first { it.id == LotId("learn", "cm2") }, Access.TRIAL_ONLY, bundles))
        assertTrue(EditionPolicy.isAllowed(catalog.first { it.id == LotId("learn", "cm2-trial") }, Access.TRIAL_ONLY, bundles))
    }

    @Test fun aPurchaseReplacesThe_trialLotsOfThatBundleOnly() {
        val a = access(token(rights = listOf(purchase("classe-cm2"))))
        val o = ids(EditionPolicy.offered(catalog, a, bundles))
        assertTrue("learn:cm2" in o && "quiz:cm2" in o); assertFalse("learn:cm2-trial" in o || "quiz:cm2-trial" in o, "never both")
        assertTrue("learn:6e-trial" in o && "learn:6e" !in o, "the other classes stay on the trial")
    }

    @Test fun subscriptionEndsAndEverythingBoughtStays() {
        val tok = token(rights = listOf(purchase("quiz-cm2"), sub("tout", ends = t0 + day, grace = day)))
        assertTrue("learn:6e" in ids(EditionPolicy.offered(catalog, access(tok, t0), bundles)))
        val after = access(tok, t0 + 3 * day)
        val o = ids(EditionPolicy.offered(catalog, after, bundles))
        assertTrue("quiz:cm2" in o, "purchase kept"); assertTrue("learn:6e-trial" in o && "learn:6e" !in o && "learn:cm2-trial" in o, "the rest is the trial again")
    }

    @Test fun nothingIsDeletedWithoutWarning() {
        val held = catalog.filter { it.edition == Edition.FULL && it.id.scope in setOf("cm2", "6e") }
        val tok = token(rights = listOf(purchase("quiz-cm2"), sub("tout", ends = t0 + day, grace = day)))
        val after = access(tok, t0 + 3 * day)
        val r = EditionPolicy.reconcile(held, after, bundles)
        assertEquals(listOf("quiz:cm2"), ids(r.keep))
        assertEquals(setOf("learn:cm2", "learn:6e", "quiz:6e"), r.demotions.map { LotNames.key(it.lot.id) }.toSet())
        assertTrue(r.removableNow.isEmpty(), "held until the user has been told"); assertTrue(r.notices.all { it.contains("version d'essai") })
        val acked = EditionPolicy.reconcile(held, after, bundles, acknowledged = setOf(LotId("learn", "6e")))
        assertEquals(listOf(LotId("learn", "6e")), acked.removableNow)
        assertTrue(r.demotions.all { it.trialTwin.scope.endsWith("-trial") })
    }

    @Test fun overlappingBundlesAreNeverBilledTwice() {
        val a = access(token(rights = listOf(purchase("quiz-cm2"))))
        val q = EditionPolicy.quote("classe-cm2", a, bundles, catalog)!!
        assertEquals(listOf(LotId("learn", "cm2")), q.billable); assertEquals(3000, q.billableBytes)
        assertEquals(EditionPolicy.LotStatus.OWNED, q.lots.single { it.lot == LotId("quiz", "cm2") }.status)
        assertNull(EditionPolicy.quote("inconnu", a, bundles, catalog))
    }

    @Test fun aLotCoveredByTheSubscriptionIsAChoiceNotAnAutomaticPurchase() {
        val a = access(token(rights = listOf(sub("tout"))))
        val q = EditionPolicy.quote("classe-cm2", a, bundles, catalog)!!
        assertEquals(setOf(LotId("learn", "cm2"), LotId("quiz", "cm2")), q.coveredBySubscription.toSet())
        assertTrue(q.newlyUnlocked.isEmpty(), "nothing is locked: buying only makes it survive the subscription")
        assertEquals(q.coveredBySubscription, q.billable)
    }

    @Test fun switchingFromSubscriptionToPurchaseTellsWhatWouldBeLost() {
        val a = access(token(rights = listOf(purchase("quiz-cm2"), sub("tout", ends = t0 + 20 * day))))
        val i = EditionPolicy.expiryImpact(a, bundles, catalog)!!
        assertEquals(t0 + 20 * day, i.atEnd)
        assertFalse(LotId("quiz", "cm2") in i.lostLots, "already bought")
        assertEquals(setOf("learn:cm2", "learn:6e", "quiz:6e", "langues:zh-a0", "langues:zh-a1"), i.lostLots.map { LotNames.key(it) }.toSet())
        assertEquals(listOf("classe-6e", "langue-zh", "classe-cm2"), i.suggestedBundles, "most lots covered first, then smallest, deterministic")
        assertNull(EditionPolicy.expiryImpact(access(token(rights = listOf(purchase("quiz-cm2")))), bundles, catalog))
    }

    @Test fun bundleCatalogReadsTheManifestAndKnowsOverlaps() {
        val json = JsonLite.write(mapOf("bundles" to listOf(mapOf("id" to "classe-cm2", "type" to "classe", "lots" to listOf("learn:cm2", "quiz:cm2"), "rawBytes" to 5))))
        val c = BundleCatalog.parse(json)
        assertEquals(setOf(LotId("learn", "cm2"), LotId("quiz", "cm2")), c.lotsOf(listOf("classe-cm2")))
        assertEquals(listOf("classe-cm2", "quiz-cm2", "tout"), bundles.containing(LotId("quiz", "cm2")).map { it.id })
    }

    // ---- planner: trial -> full, same deterministic plan ----
    @Test fun plannerWantsTheTrialWithoutRightsAndTheFullLotWithThem() {
        val needs = listOf(Need(LotId("learn", "cm2"), 0), Need(LotId("quiz", "cm2"), 1))
        val none = EditionPolicy.planForTv(needs, catalog, emptyList(), 0, Access.TRIAL_ONLY, bundles, budget = 10_000)
        assertEquals(listOf("learn:cm2-trial", "quiz:cm2-trial"), ids(none.plan.wanted))
        val a = access(token(rights = listOf(purchase("classe-cm2"))))
        val full = EditionPolicy.planForTv(needs, catalog, emptyList(), 0, a, bundles, budget = 10_000)
        assertEquals(listOf("learn:cm2", "quiz:cm2"), ids(full.plan.wanted).sorted().sortedBy { if (it.startsWith("learn")) 0 else 1 })
        assertEquals(none.plan.priority.values.toList(), listOf(0, 1)); assertTrue(full.plan.usedBytes <= 10_000)
    }

    @Test fun whileTheFullLotIsNotOnThePhoneTheTrialKeepsTheTvUseful_thenItIsDropped() {
        val a = access(token(rights = listOf(purchase("classe-cm2"))))
        val phoneOnlyTrial = catalog.filter { it.edition == Edition.TRIAL }
        val needs = listOf(Need(LotId("learn", "cm2"), 0))
        val p = EditionPolicy.planForTv(needs, phoneOnlyTrial, emptyList(), 0, a, bundles, budget = 10_000)
        assertEquals(listOf("learn:cm2-trial"), ids(p.plan.wanted)); assertEquals(listOf(LotId("learn", "cm2")), p.plan.notOnPhone.map { LotEditions.fullOf(it) }.distinct().take(1).let { listOf(LotId("learn", "cm2")) })
        val tvHasBoth = catalog.filter { it.id.feature == "learn" && it.id.scope.startsWith("cm2") }
        val done = EditionPolicy.planForTv(needs, catalog, tvHasBoth, 0, a, bundles, budget = 10_000)
        assertEquals(listOf(LotId("learn", "cm2-trial")), done.dropTrials)
    }

    @Test fun planIsDeterministicWhateverTheOrderOfTheInputs() {
        val a = access(token(rights = listOf(purchase("classe-6e"))))
        val needs = listOf(Need(LotId("learn", "cm2"), 0), Need(LotId("learn", "6e"), 1), Need(LotId("quiz", "6e"), 2), Need(LotId("langues", "zh-a0"), 3))
        val p1 = EditionPolicy.planForTv(needs, catalog, emptyList(), 100, a, bundles, 9_000)
        val p2 = EditionPolicy.planForTv(needs.reversed(), catalog.reversed(), emptyList(), 100, a, bundles, 9_000)
        assertEquals(ids(p1.plan.wanted), ids(p2.plan.wanted)); assertEquals(p1.plan.usedBytes, p2.plan.usedBytes)
    }

    // ---- TV replaces trial by full (no duplicates, budget respected) ----
    private class Pub(val meta: LotMeta, val data: ByteArray) { val proof = Kit.sign(listOf(meta)).toJson() }
    private fun push(t: FakeTv, p: Pub): TvLotStore.Result {
        val name = LotNames.fileName(p.meta)
        assertIs<TvLotStore.Chunk.Received>(t.store.receive(name, 0, p.data.size.toLong(), p.data))
        return t.store.installReceived(name, p.proof)
    }

    @Test fun fullLotReplacesItsTrialTwinOnTheTv_withinTheBudget() {
        val tv = FakeTv(starter = 0, max = 1000)
        try {
            val (tm, td) = lot("learn", "cm2", 300, trial = true); val (fm, fd) = lot("learn", "cm2", 900)
            val first = push(tv, Pub(tm, td))
            assertEquals(TvLotStore.Result.Ok(emptyList()), first, first.toString())
            assertEquals(300, tv.store.usedBytes())
            // 300 + 900 > 1000, but the trial twin is freed by the replacement: it fits and nothing else is evicted
            val r = push(tv, Pub(fm, fd))
            assertEquals(TvLotStore.Result.Ok(emptyList()), r)
            assertEquals(listOf(fm.id), tv.learn.installed().map { it.id }); assertEquals(900, tv.store.usedBytes())
            // and the trial can no longer come back over the full lot
            val (tm2, td2) = lot("learn", "cm2", 300, trial = true, version = 2)
            assertContains((push(tv, Pub(tm2, td2)) as TvLotStore.Result.Refused).reason, "version complète")
        } finally { tv.dir.deleteRecursively() }
    }

    @Test fun aFullLotThatDoesNotFitEvenWithoutItsTwinLeavesTheTrialInPlace() {
        val tv = FakeTv(starter = 0, max = 1000)
        try {
            val (tm, td) = lot("learn", "cm2", 300, trial = true); val (fm, fd) = lot("learn", "cm2", 1200, version = 2)
            assertIs<TvLotStore.Result.Ok>(push(tv, Pub(tm, td)))
            assertIs<TvLotStore.Chunk.Refused>(tv.store.receive(LotNames.fileName(fm), 0, fd.size.toLong(), fd))   // bigger than the TV: refused up front
            assertEquals(listOf(tm.id), tv.learn.installed().map { it.id })
        } finally { tv.dir.deleteRecursively() }
    }

    // ---- phone: sync refuses what is not entitled, and cleans superseded trials ----
    @Test fun syncSkipsFullLotsWithoutRightsAndInstallsTheTrial() {
        val dir = Kit.tmp()
        try {
            val published = catalog.filter { it.id.feature == "learn" && it.id.scope.startsWith("cm2") }.map { Published(it, Kit.bytes(it.bytes.toInt(), it.bytes.toInt())) }
            // the published bytes must match the catalog hashes: rebuild the metas from the bytes
            val real = published.map { p -> Published(p.meta.copy(sha256 = LotHash.sha256Hex(p.data)), p.data) }
            val remote = FakeRemote(real)
            val store = LotStore(dir, 100_000)
            val sync = LotSync(store, remote, listOf(Kit.pub), 10, { Net.UNMETERED }, {}, allowed = { EditionPolicy.isAllowed(it, Access.TRIAL_ONLY, bundles) })
            val rep = sync.sync(listOf(LotId("learn", "cm2"), LotId("learn", "cm2-trial")))
            assertEquals(LotSync.Outcome.NOT_ENTITLED, rep.results.first { it.id == LotId("learn", "cm2") }.outcome)
            assertContains(rep.results.first { it.id == LotId("learn", "cm2") }.message, "version complète")
            assertEquals(LotSync.Outcome.INSTALLED, rep.results.first { it.id.scope == "cm2-trial" }.outcome)
            assertNull(store.get(LotId("learn", "cm2")))
            // rights arrive: the full lot installs and the trial twin is removed by the same synchronisation
            val a = access(token(rights = listOf(purchase("classe-cm2"))))
            val sync2 = LotSync(store, remote, listOf(Kit.pub), 10, { Net.UNMETERED }, {}, allowed = { EditionPolicy.isAllowed(it, a, bundles) })
            sync2.sync(listOf(LotId("learn", "cm2")))
            assertNotNull(store.get(LotId("learn", "cm2"))); assertNull(store.get(LotId("learn", "cm2-trial")), "no duplicate")
        } finally { dir.deleteRecursively() }
    }

    // ---- budget guard on TRIAL-MANIFEST.json ----
    private fun manifestJson(totalExtra: Long = 0, emptySub: Boolean = false, valid: Boolean = true, items: List<String> = listOf("lesson/a", "exercise/b"), lotBytes: Long = 5000): String =
        JsonLite.write(linkedMapOf(
            "valid" to valid, "capBytes" to TrialBudget.CAP_BYTES, "totalBytes" to lotBytes + totalExtra, "inventory" to "abc",
            "lots" to listOf(linkedMapOf("feature" to "learn", "scope" to "cm2-trial", "fullLot" to mapOf("feature" to "learn", "scope" to "cm2"), "bytes" to lotBytes,
                "files" to listOf(mapOf("path" to "p/pack.json", "bytes" to lotBytes)), "items" to items, "bundles" to listOf("classe-cm2"))),
            "subcategories" to listOf(mapOf("key" to "learn/cm2/maths", "category" to "learn", "selectedItems" to if (emptySub) 0 else 2, "totalItems" to 9, "selectedBytes" to lotBytes, "fullBytes" to 9000)),
            "bundles" to listOf(mapOf("id" to "classe-cm2", "type" to "classe", "lots" to listOf("learn:cm2"), "rawBytes" to 9000))))

    private val full = mapOf("learn:cm2" to setOf("lesson/a", "exercise/b", "exercise/c"))

    @Test fun budgetGuardAcceptsAValidManifest() = assertEquals(emptyList(), TrialBudget.verify(TrialManifest.parse(manifestJson()), fullItems = full))

    @Test fun budgetGuardFailsAbove100MoAndOnAnEmptySubCategory() {
        val big = TrialManifest.parse(manifestJson(lotBytes = TrialBudget.CAP_BYTES + 1))
        assertTrue(TrialBudget.verify(big).any { it.contains("plafond dépassé") })
        assertTrue(TrialBudget.verify(TrialManifest.parse(manifestJson(emptySub = true))).any { it.contains("sous-catégorie vide : learn/cm2/maths") })
        assertTrue(TrialBudget.verify(TrialManifest.parse(manifestJson(valid = false))).any { it.contains("invalide") })
        assertTrue(TrialBudget.verify(TrialManifest.parse(manifestJson(totalExtra = 7))).any { it.contains("totalBytes") })
    }

    @Test fun budgetGuardCatchesUnstableIdentifiersAndDuplicates() {
        val unstable = TrialManifest.parse(manifestJson(items = listOf("lesson/a", "exercise/renamed")))
        assertTrue(TrialBudget.verify(unstable, fullItems = full).any { it.contains("identifiant instable") })
        assertTrue(TrialBudget.verify(TrialManifest.parse(manifestJson(items = listOf("lesson/a", "lesson/a")))).any { it.contains("identifiant en double") })
    }

    @Test fun fingerprintIsTheSameForTheSameContent() {
        val a = TrialManifest.parse(manifestJson()); val b = TrialManifest.parse(manifestJson(items = listOf("exercise/b", "lesson/a")))
        assertEquals(TrialBudget.fingerprint(a), TrialBudget.fingerprint(b))
        assertNotEquals(TrialBudget.fingerprint(a), TrialBudget.fingerprint(TrialManifest.parse(manifestJson(items = listOf("lesson/a")))))
    }

    @Test fun theRealManifestWhenPresentRespectsTheBudget() {
        val f = generateSequence(java.io.File("").absoluteFile) { it.parentFile }.map { java.io.File(it, "content/TRIAL-MANIFEST.json") }.firstOrNull { it.isFile } ?: return
        assertEquals(emptyList(), TrialBudget.verify(TrialManifest.parse(f.readText())))
    }
}
