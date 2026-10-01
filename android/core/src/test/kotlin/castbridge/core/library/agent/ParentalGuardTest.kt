package castbridge.core.library.agent

import castbridge.core.net.JsonLite
import castbridge.core.parental.*
import castbridge.core.tv.*
import kotlin.test.*

private const val MB = 1L shl 20
private val CTX = AgentContext(nowMs = 1_800_000_000_000L, currentYear = 2026)

private fun tv(name: String, vol: String = "internal", size: Long = 700 * MB) = FileRef(Origin.TV, name, size, volumeId = vol)
private fun snap(vararg f: FileRef, child: Boolean = false) =
    LibrarySnapshot(Origin.TV, f.toList(), listOf(VolumeInfo("internal", "Mémoire interne", "internal", 20L shl 30, 32L shl 30)), childActive = child)

private fun cfg(active: String? = "kid", unrated: Rating = Rating.ADULT, vararg rules: RatingRule, age: AgeBand = AgeBand.KID, enabled: Boolean = true) = ParentalConfig(
    enabled = enabled, activeProfile = active, profiles = listOf(ChildProfile("kid", "Léa", age), ChildProfile("teen", "Paul", AgeBand.TEEN16)),
    rules = rules.toList(), unrated = unrated,
)

class ParentalGuardTest {
    private val ok = RatingRule(RuleKind.FILE, "Dessin Anime.mp4", Rating.ALL)
    private val adult = RatingRule(RuleKind.KEYWORD, "horreur", Rating.ADULT)

    // ------------------------------------------------------------------ the pure guard

    @Test fun unratedVideosAreAdultAndProtectedByDefault() {
        val g = ParentalContentGuard(cfg(), childActive = false)
        assertTrue(g.isProtected(tv("Film inconnu.mkv")), "not classified = adult = protected")
    }

    @Test fun aVideoRatedWithinTheProfileAgeIsNotProtectedButAboveItIs() {
        val c = cfg("kid", Rating.ADULT, RatingRule(RuleKind.KEYWORD, "pixar", Rating.ALL), RatingRule(RuleKind.KEYWORD, "action", Rating.U16), RatingRule(RuleKind.KEYWORD, "ado", Rating.U12))
        val g = ParentalContentGuard(c, false)
        assertFalse(g.isProtected(tv("Pixar Cars.mp4")))
        assertTrue(g.isProtected(tv("Action Max.mp4")))
        assertTrue(g.isProtected(tv("Ado story.mp4")), "-12 is above a child under 12")
        val teen = ParentalContentGuard(c.copy(activeProfile = "teen"), false)
        assertFalse(teen.isProtected(tv("Ado story.mp4")))
        assertFalse(teen.isProtected(tv("Action Max.mp4")), "-16 is fine for a 16-17 profile")
        assertTrue(teen.isProtected(tv("Film inconnu.mkv")))
    }

    @Test fun withoutAnActiveProfileTheYoungestProfileIsTheReference() {
        val g = ParentalContentGuard(cfg(active = null, unrated = Rating.ADULT, RatingRule(RuleKind.KEYWORD, "pixar", Rating.ALL)), childActive = false)
        assertTrue(g.isProtected(tv("Inconnu.mkv")))
        assertFalse(g.isProtected(tv("Pixar Cars.mp4")))
        assertFalse(g.childProfileActive)
    }

    @Test fun anAdultOnlyConfigurationProtectsNothing() {
        val c = ParentalConfig(enabled = true, profiles = listOf(ChildProfile("a", "Maman", AgeBand.ADULT)), activeProfile = "a")
        assertFalse(ParentalContentGuard(c, false).isProtected(tv("Inconnu.mkv")))
    }

    @Test fun aDisabledControlProtectsNothingAndNoChildIsActive() {
        val g = ParentalContentGuard(cfg(enabled = false), childActive = true)
        assertFalse(g.isProtected(tv("Inconnu.mkv")))
        assertFalse(g.childProfileActive)
    }

    @Test fun aFileRuleKeepsTheFileFromBeingRenamedEvenWhenRatedForAll() {
        val g = ParentalContentGuard(cfg("kid", Rating.ADULT, ok), false)
        assertTrue(g.isProtected(tv("dessin anime.MP4")), "renaming would drop the classification, which follows the name")
    }

    @Test fun onlyVideosAreClassifiedSoMusicPhotosAndDocumentsAreFree() {
        val g = ParentalContentGuard(cfg(), false)
        listOf("Chanson.mp3", "Photo.jpg", "CV.pdf", "App.apk", "Archive.zip").forEach { assertFalse(g.isProtected(tv(it)), it) }
    }

    @Test fun aSubtitleFollowsItsProtectedVideo() {
        val files = listOf(tv("Horreur 2010.mkv"), tv("Horreur 2010.fr.srt"), tv("Pixar Cars.mp4"), tv("Pixar Cars.srt"), tv("Autre.srt"))
        val c = cfg("kid", Rating.ADULT, RatingRule(RuleKind.KEYWORD, "pixar", Rating.ALL))
        val g = ParentalContentGuard(c, false, allFiles = files)
        assertTrue(g.isProtected(files[1]))
        assertFalse(g.isProtected(files[3]))
        assertFalse(g.isProtected(files[4]), "a subtitle without a video is not classified")
    }

    @Test fun volumeRulesUseTheVolumeLabel() {
        val c = cfg("kid", Rating.ADULT, RatingRule(RuleKind.VOLUME, "Clé enfants", Rating.ALL))
        val g = ParentalContentGuard(c, false, volumeLabel = { if (it == "usb") "Clé enfants" else "Mémoire" })
        assertFalse(g.isProtected(tv("X.mp4", "usb")))
        assertTrue(g.isProtected(tv("X.mp4", "internal")))
    }

    // ------------------------------------------------------------------ the TV side

    private fun engine(c: ParentalConfig, pin: Boolean = true): ParentalEngine {
        val e = ParentalEngine(MemoryKv())
        if (pin) assertNull(e.createPin("2580"))
        e.saveConfig(c)
        return e
    }

    @Test fun theTvFlagsFollowTheEngine() {
        val items = listOf(LibraryItem("Film inconnu.mkv", 1, 0, "internal", "Mémoire", VolumeKind.INTERNAL, FileMeta()),
            LibraryItem("Pixar Cars.mp4", 1, 0, "internal", "Mémoire", VolumeKind.INTERNAL, FileMeta()),
            LibraryItem("Chanson.mp3", 1, 0, "internal", "Mémoire", VolumeKind.INTERNAL, FileMeta()))
        val e = engine(cfg("kid", Rating.ADULT, RatingRule(RuleKind.KEYWORD, "pixar", Rating.ALL)))
        val f = EngineContentFlags(e)
        assertEquals(setOf("Film inconnu.mkv"), f.protectedNames(items))
        assertTrue(f.childActive())
        e.startSession()
        assertFalse(f.childActive(), "parent session: no child active, but the unrated video stays protected")
        assertEquals(setOf("Film inconnu.mkv"), f.protectedNames(items))
    }

    @Test fun withoutAParentalPinTheControlIsInactiveSoNothingIsProtected() {
        val f = EngineContentFlags(engine(cfg(), pin = false))
        assertEquals(emptySet(), f.protectedNames(listOf(LibraryItem("X.mkv", 1, 0, "internal", "M", VolumeKind.INTERNAL, FileMeta()))))
    }

    // ------------------------------------------------------------------ the agent never lists / plans / sends a protected file

    private val files = arrayOf(tv("Horreur.Nocturne.2019.1080p.x264.mkv"), tv("Pixar.Cars.2006.720p.BluRay.mkv"), tv("prison.break.s01e01.mkv"))
    private val guard get() = ParentalContentGuard(cfg("kid", Rating.ADULT, RatingRule(RuleKind.KEYWORD, "pixar", Rating.ALL)), false, allFiles = files.toList())

    @Test fun protectedFilesAreNeitherListedNorPlannedNorFingerprinted() {
        var fingerprinted = ArrayList<String>()
        val fp = object : Fingerprinter { override fun fingerprint(file: FileRef): String? { fingerprinted += file.name; return "x" } }
        val a = LibraryAgent(CTX.copy(guard = guard), fingerprinter = fp).analyze(snap(*files))
        val everythingShown = (a.snapshot.files.map { it.name } + a.plan.changes.map { it.file.name } + a.plan.skipped.map { it.file.name } +
            a.items.map { it.file.name }).joinToString("|") + a.plan.notes.joinToString() + a.insights.joinToString { it.text }
        assertEquals(listOf("Pixar.Cars.2006.720p.BluRay.mkv"), a.snapshot.files.map { it.name })
        assertFalse(everythingShown.contains("Horreur", true))
        assertFalse(everythingShown.contains("prison", true))
        assertTrue(fingerprinted.none { it.contains("Horreur") || it.contains("prison") })
        assertEquals(2, a.snapshot.protectedCount)
        assertTrue(a.plan.notes.any { it.contains("2 fichiers protégés") })
    }

    @Test fun protectedFilesNeverReachTheServerEvenWhenTheNameIsAmbiguous() {
        val amb = tv("horreur nocturne ep final cut.mp4")                    // ambiguous AND unrated: protected
        val free = tv("kaduna nights ep final cut.mp4")
        val c = cfg("kid", Rating.ADULT, RatingRule(RuleKind.KEYWORD, "kaduna", Rating.ALL))
        val sent = ArrayList<String>()
        val m = object : NamingModel {
            override val id = "mock"; override val remote = true
            override fun suggest(req: SuggestRequest): SuggestResponse { sent += req.items.map { it.text }; return SuggestResponse("mock", true, emptyList()) }
        }
        LibraryAgent(CTX.copy(aiAllowed = true, guard = ParentalContentGuard(c, false, allFiles = listOf(amb, free))), model = m).analyze(snap(amb, free))
        assertEquals(1, sent.size, sent.toString())
        assertTrue(sent.single().contains("kaduna", true))
        assertTrue(sent.none { it.contains("horreur", true) })
    }

    @Test fun theTvFlagsAloneAreEnoughEvenWithoutAGuardInTheContext() {
        val flagged = files.map { it.copy(guarded = it.name.contains("Horreur") || it.name.contains("prison")) }
        val a = LibraryAgent(CTX).analyze(snap(*flagged.toTypedArray()))
        assertEquals(1, a.snapshot.files.size)
        assertEquals(2, a.snapshot.protectedCount)
    }

    @Test fun aChildProfileActiveMeansAdviceOnlyAndNoChangeIsExecuted() {
        val a = LibraryAgent(CTX).analyze(snap(tv("Pixar.Cars.2006.720p.BluRay.mkv"), child = true))
        assertTrue(a.plan.childActive)
        assertTrue(a.plan.notes.any { it.contains("profil enfant", true) })
        val ops = FakeOps().also { it.addVolume(VolumeInfo("internal", "I", "internal", 20L shl 30, 32L shl 30)); it.add("internal", "Pixar.Cars.2006.720p.BluRay.mkv", 700 * MB) }
        val res = Executor(ops, MemoryJournal(), CTX).run(a.plan, a.plan.allSafe().ifEmpty { a.plan.changes.map { it.id }.toSet() }, confirmDeletions = true)
        assertTrue(ops.log.isEmpty(), "nothing touched: ${ops.log}")
        assertEquals(0, res.done)
    }

    @Test fun theExecutorRefusesAFlaggedFileEvenIfAPlanSomehowContainsIt() {
        val f = tv("Horreur.2019.mkv").copy(guarded = true)
        val ops = FakeOps().also { it.addVolume(VolumeInfo("internal", "I", "internal", 20L shl 30, 32L shl 30)); it.add("internal", f.name, f.size) }
        val plan = Plan(listOf(Change("c1", ChangeType.RENAME, f, Kind.MOVIE, toName = "Horreur (2019).mkv", reason = "r", confidence = 1.0)))
        val res = Executor(ops, MemoryJournal(), CTX).run(plan, setOf("c1"), false)
        assertEquals(1, res.skipped)
        assertTrue(ops.log.isEmpty())
        assertTrue(ops.has("internal", f.name))
    }

    @Test fun theExecutorRefusesWhenTheContextGuardSaysProtectedOrChild() {
        val f = tv("Horreur.2019.mkv")
        fun run(g: ContentGuard): RunResult {
            val ops = FakeOps().also { it.addVolume(VolumeInfo("internal", "I", "internal", 20L shl 30, 32L shl 30)); it.add("internal", f.name, f.size) }
            val plan = Plan(listOf(Change("c1", ChangeType.TRASH, f, Kind.MOVIE, reason = "r", confidence = 1.0)))
            return Executor(ops, MemoryJournal(), CTX.copy(guard = g)).run(plan, setOf("c1"), true).also { assertTrue(ops.log.isEmpty()) }
        }
        assertEquals(1, run(ParentalContentGuard(cfg(), false)).skipped)
        assertEquals(1, run(ParentalContentGuard(cfg(), true)).skipped)
    }

    // ------------------------------------------------------------------ over HTTP: what the TV tells the phone

    private fun rig(flags: ContentFlags?): Pair<AgentRig2, TvClient> { val r = AgentRig2(flags); return r to r.tv }

    @Test fun libraryJsonCarriesTheFlagsAndTheSnapshotReadsThem() {
        val e = engine(cfg("kid", Rating.ADULT, RatingRule(RuleKind.KEYWORD, "pixar", Rating.ALL)))
        val (r, tvc) = rig(EngineContentFlags(e))
        try {
            r.put("Film inconnu.mkv"); r.put("Pixar Cars.mp4")
            val j = JsonLite.obj(tvc.library())
            assertEquals(true, j["guard"]); assertEquals(true, j["childActive"])
            @Suppress("UNCHECKED_CAST") val rows = (j["files"] as List<Map<String, Any?>>).associate { it["name"] as String to it["protected"] }
            assertEquals(mapOf("Film inconnu.mkv" to true, "Pixar Cars.mp4" to false), rows)
            val s = TvSnapshot.read(tvc)
            assertTrue(s.childActive)
            assertFalse(s.guardUnsupported)
            assertEquals(listOf(true, false), s.files.sortedBy { it.name }.map { it.guarded })
            val a = LibraryAgent(CTX).analyze(s)
            assertTrue(a.snapshot.files.none { it.name.startsWith("Film inconnu") })
        } finally { r.close() }
    }

    @Test fun aTvThatDoesNotSayTreatsEverythingAsProtected() {
        val (r, tvc) = rig(null)
        try {
            r.put("Pixar Cars.mp4")
            val s = TvSnapshot.read(tvc)
            assertTrue(s.guardUnsupported)
            assertTrue(s.files.all { it.guarded })
            val a = LibraryAgent(CTX).analyze(s)
            assertTrue(a.snapshot.files.isEmpty())
            assertTrue(a.plan.changes.isEmpty())
            assertTrue(a.plan.notes.any { it.contains("mettez CastBridge-TV à jour") })
        } finally { r.close() }
    }
}

/** Minimal real TV server (one folder) with a parental-flags hook. */
class AgentRig2(flags: ContentFlags?) {
    val dir = kotlin.io.path.createTempDirectory("guard").toFile()
    private val port = java.net.ServerSocket(0).use { it.localPort }
    val server = ReceiverServer(VolumeRegistry.single(dir), castbridge.core.FakePlayer(), port, pin = "123456",
        guard = PinGuard("123456", maxFailures = 1000), contentFlags = flags).apply { start(5000, false) }
    val tv = TvClient("http://127.0.0.1:$port", "123456")
    fun put(name: String) = java.io.File(dir, name).writeBytes(ByteArray(2000) { it.toByte() })
    fun close() { server.stop(); dir.deleteRecursively() }
}
