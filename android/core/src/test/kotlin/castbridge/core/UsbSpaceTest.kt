package castbridge.core

import castbridge.core.tv.*
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.Random
import kotlin.test.*

private typealias Status = UsbAdoption.Status

/** Un espace par TV sur la clé (docs/coordination/DESIGN-USB-ESPACE-PAR-TV-2026-11-04.md). */
class UsbSpaceTest {
    private lateinit var base: File
    private lateinit var root: File           // <clé>/Download/CastBridge
    private val codeA = "K7QM-2D9X-4TRW-BH3P"
    private val codeB = "A2C4-E6G8-J0LN-PR5T"
    private val idA get() = UsbSpace.idOf(codeA)!!
    private val idB get() = UsbSpace.idOf(codeB)!!
    private val T0 = 1_800_000_000_000L

    @BeforeTest fun setUp() { base = Files.createTempDirectory("espace").toFile(); root = File(base, "Download/CastBridge").also { it.mkdirs() } }
    @AfterTest fun tearDown() { base.deleteRecursively() }

    private fun put(rel: String, size: Int = 1000, seed: Int = 1): File = File(root, rel).also { it.parentFile.mkdirs(); it.writeBytes(ByteArray(size) { i -> ((i * 31 + seed) % 251).toByte() }) }
    private fun adoption(id: String = idA, fs: Fs = Fs.EXFAT, writable: Boolean = true, fault: (String) -> Unit = {}, now: Long = T0, free: Long = Long.MAX_VALUE) =
        UsbAdoption(root, id, fs, writable, fault, { now }, { free })
    /** relatif -> (taille, hachage) de tous les fichiers sous [dir]. */
    private fun snapshot(dir: File): Map<String, String> = dir.walkTopDown().filter { it.isFile }.associate { it.relativeTo(dir).path to UsbMigration.sha256(it) }

    // ---------- identifiant ----------
    @Test fun idIsShortHexStableAndNeverTheCode() {
        val id = idA
        assertTrue(Regex("^[0-9a-f]{12}$").matches(id), id)
        assertEquals(id, UsbSpace.idOf(codeA))
        assertEquals(id, UsbSpace.idOf(" k7qm 2d9x-4trw bh3p "), "case, spaces and dashes do not matter")
        assertNotEquals(idA, idB)
        assertFalse(codeA.filter { it != '-' }.contains(id, ignoreCase = true))
        assertNull(UsbSpace.idOf("")); assertNull(UsbSpace.idOf("  - "))
    }
    @Test fun idIsKnownAnswer() = assertEquals("ecadd8ee5d0a", UsbSpace.idOf("TEST-CODE"), "SHA-256 de « castbridge-espace-v1|TESTCODE », 12 premiers hex")
    @Test fun onlyTwelveLowerHexIsAnId() {
        assertTrue(UsbSpace.isId("0123456789ab")); assertFalse(UsbSpace.isId("0123456789AB")); assertFalse(UsbSpace.isId("0123456789a"))
        assertFalse(UsbSpace.isId("Bibliotheque")); assertFalse(UsbSpace.isId("../0123456789")); assertFalse(UsbSpace.isId(""))
    }

    // ---------- création ----------
    @Test fun ensureCreatesTheFourFoldersAndTheMark() {
        val r = UsbSpace.ensure(root, idA, T0)
        val dir = assertIs<UsbSpace.Ensured.Ready>(r).dir
        assertEquals(File(root, idA), dir)
        for (s in UsbLayout.SUBDIRS) assertTrue(File(dir, s).isDirectory, s)
        assertEquals(idA to T0, UsbSpace.readMark(dir))
        val text = File(dir, UsbSpace.MARK).readText()
        assertFalse(text.contains(codeA) || text.contains("K7QM"), "the device code is never written"); assertTrue(text.length < 200)
    }
    @Test fun ensureIsIdempotentAndKeepsTheCreationDate() {
        UsbSpace.ensure(root, idA, T0); put("$idA/Bibliotheque/x.mp4")
        assertIs<UsbSpace.Ensured.Ready>(UsbSpace.ensure(root, idA, T0 + 5000))
        assertEquals(T0, UsbSpace.readMark(File(root, idA))!!.second); assertTrue(File(root, "$idA/Bibliotheque/x.mp4").isFile)
    }
    @Test fun readOnlyDriveGetsNothingCreated() {
        assertEquals(UsbSpace.Ensured.ReadOnly, UsbSpace.ensure(root, idA, T0, writable = false))
        assertFalse(File(root, idA).exists())
    }
    @Test fun fullDriveIsRefusedAndNothingIsCreated() {
        assertEquals(UsbSpace.Ensured.Full, UsbSpace.ensure(root, idA, T0, free = 1000))
        assertFalse(File(root, idA).exists())
    }
    @Test fun aSpaceThatIsASymbolicLinkIsRefused() {
        val outside = File(base, "outside").also { it.mkdirs() }
        try { Files.createSymbolicLink(File(root, idA).toPath(), outside.toPath()) } catch (e: Exception) { return }
        assertIs<UsbSpace.Ensured.Failed>(UsbSpace.ensure(root, idA, T0)); assertTrue(outside.listFiles()!!.isEmpty())
    }
    @Test fun aMarkThatNamesAnotherIdIsRefused() {
        val dir = File(root, idA).also { it.mkdirs() }; File(dir, UsbSpace.MARK).writeText("""{"id":"$idB","createdMs":1}""")
        assertIs<UsbSpace.Ensured.Failed>(UsbSpace.ensure(root, idA, T0))
    }
    @Test fun anUnmarkedFolderWithFilesUnderMyIdIsNotTakenOver() {
        put("$idA/autre.txt"); assertIs<UsbSpace.Ensured.Failed>(UsbSpace.ensure(root, idA, T0))
    }

    // ---------- deux TV, une clé ----------
    @Test fun twoTvsOnTheSameKeyNeverOverlap() {
        val a = (UsbSpace.ensure(root, idA, T0) as UsbSpace.Ensured.Ready).dir
        val b = (UsbSpace.ensure(root, idB, T0) as UsbSpace.Ensured.Ready).dir
        assertNotEquals(a, b)
        File(a, "Bibliotheque/film.mp4").writeBytes(ByteArray(10) { 1 }); File(b, "Medias/autre.mp4").writeBytes(ByteArray(10) { 2 })
        assertTrue(snapshot(a).keys.none { it.startsWith("Medias/autre") }); assertTrue(snapshot(b).keys.none { it.startsWith("Bibliotheque/film") })
        assertEquals(listOf(idB), UsbSpace.foreign(root, idA)); assertEquals(listOf(idA), UsbSpace.foreign(root, idB))
        assertEquals(emptyList(), UsbSpace.foreign(root, "ffffffffffff").filter { it != idA && it != idB })
    }
    @Test fun foreignIgnoresPlainFoldersUnmarkedFoldersAndLinks() {
        UsbSpace.ensure(root, idA, T0)
        File(root, UsbLayout.LIBRARY).mkdirs(); File(root, "0123456789ab").mkdirs()          // legacy folder, unmarked id-like folder
        File(root, "fedcba987654").mkdirs().also { File(root, "fedcba987654/.espace").writeText("""{"id":"autre","createdMs":1}""") }
        assertEquals(emptyList(), UsbSpace.foreign(root, idA))
    }

    // ---------- anciennes données à plat ----------
    @Test fun legacyIsDetectedOnlyWhenAFileIsThere() {
        assertFalse(UsbSpace.hasLegacy(root)); UsbLayout.ensure(root); File(root, "Bibliotheque/vide/encore").mkdirs(); assertFalse(UsbSpace.hasLegacy(root), "empty folders, even nested, are not data")
        put("Bibliotheque/a.mp4"); assertTrue(UsbSpace.hasLegacy(root))
    }
    @Test fun aFileInsideASpaceIsNotLegacy() { UsbSpace.ensure(root, idA, T0); put("$idA/Bibliotheque/a.mp4"); assertFalse(UsbSpace.hasLegacy(root)) }

    // ---------- plan ----------
    @Test fun planListsMovesConflictsAndIgnoredWithoutTouchingAnything() {
        put("Bibliotheque/a.mp4", 500); put("Bibliotheque/sous/b.mkv", 700); put("Telechargements/c.mp4.part", 30); put("index/castbridge-store.json", 40)
        put("Bibliotheque/virus.apk", 10); put("Bibliotheque/run.sh", 10)
        put("$idA/Bibliotheque/a.mp4", 500)                                   // le même nom existe déjà dans l'espace
        put("Autre/x.txt"); put("activation", 5)                              // hors des quatre dossiers : jamais touchés
        val before = snapshot(root)
        val p = adoption().plan()
        assertEquals(setOf("Bibliotheque/sous/b.mkv", "Telechargements/c.mp4.part", "index/castbridge-store.json"), p.moves.map { it.rel }.toSet())
        assertEquals(700L + 30 + 40, p.bytes)
        assertEquals(listOf("Bibliotheque/a.mp4"), p.conflicts.map { it.rel })
        assertEquals(setOf("Bibliotheque/virus.apk", "Bibliotheque/run.sh"), p.ignored.map { it.rel }.toSet())
        assertEquals(before, snapshot(root), "planning changes nothing")
    }
    @Test fun namesDifferingOnlyByCaseCollideOnExfatButNotOnExt4() {
        put("Bibliotheque/Film.mp4", 10, 1); put("Bibliotheque/film.MP4", 20, 2)
        assertEquals(1, adoption(fs = Fs.EXFAT).plan().conflicts.size); assertEquals(0, adoption(fs = Fs.EXT4).plan().conflicts.size)
    }
    @Test fun forbiddenCharactersTooDeepAndSymlinksAreIgnoredNotFollowed() {
        put("Bibliotheque/a:b.mp4"); put("Bibliotheque/1/2/3/4/5/6/7/8/9/profond.mp4")
        val outside = File(base, "dehors").also { it.mkdirs(); File(it, "secret.mp4").writeText("x") }
        val link = File(root, "Bibliotheque/lien")
        val linked = try { Files.createSymbolicLink(link.toPath(), outside.toPath()); true } catch (e: Exception) { false }
        val p = adoption().plan()
        assertTrue(p.moves.isEmpty(), p.moves.toString())
        assertTrue(p.ignored.any { it.rel == "Bibliotheque/a:b.mp4" } && p.ignored.any { it.reason == "too deep" })
        if (linked) {
            assertTrue(p.ignored.any { it.rel == "Bibliotheque/lien" && it.reason == "symbolic link" })
            adoption().run(true); assertTrue(File(outside, "secret.mp4").isFile, "never follows a link out of the drive")
        }
    }
    @Test fun aNameOf255BytesIsAccepted() {
        val name = "é".repeat(75) + "x".repeat(101) + ".mp4"; assertEquals(255, name.toByteArray().size)
        put("Bibliotheque/$name"); assertEquals(1, adoption().plan().moves.size)
        assertEquals(Status.DONE, adoption().run(true).status); assertTrue(File(root, "$idA/Bibliotheque/$name").isFile)
    }

    // ---------- exécution ----------
    @Test fun nothingMovesWithoutExplicitConfirmation() {
        put("Bibliotheque/a.mp4"); val before = snapshot(root)
        val r = adoption().run(confirmed = false)
        assertEquals(Status.NOT_CONFIRMED, r.status); assertEquals(before, snapshot(root)); assertFalse(File(root, idA).exists())
    }
    @Test fun adoptionMovesEverythingExactlyOnceAndKeepsContent() {
        put("Bibliotheque/a.mp4", 5000, 3); put("Bibliotheque/d/e.mp4", 4000, 4); put("Telechargements/f.part", 10); put("Medias/g.mp3", 99, 5); put("index/settings.json", 20); put("activation", 7)
        val before = snapshot(root)
        val r = adoption().run(true)
        assertEquals(Status.DONE, r.status); assertEquals(5, r.moved)
        val space = File(root, idA)
        val expected = before.filterKeys { it != "activation" }
        assertEquals(expected, snapshot(space).filterKeys { it != UsbSpace.MARK })
        assertFalse(UsbSpace.hasLegacy(root)); assertTrue(File(root, "activation").isFile, "the activation file stays at the root")
        for (s in UsbLayout.SUBDIRS) assertTrue(File(space, s).isDirectory)
        assertFalse(File(root, UsbSpace.CLAIM).exists())
        assertEquals(before.size, snapshot(root).size - 1, "one file more (the mark), none lost, none duplicated")
    }
    @Test fun conflictsAreKeptOnBothSidesAndReported() {
        put("Bibliotheque/a.mp4", 500, 1); put("$idA/Bibliotheque/a.mp4", 500, 2); put("Bibliotheque/b.mp4")
        val r = adoption().run(true)
        assertEquals(Status.DONE_WITH_CONFLICTS, r.status); assertEquals(1, r.moved)
        assertTrue(File(root, "Bibliotheque/a.mp4").isFile && File(root, "$idA/Bibliotheque/a.mp4").isFile, "neither is overwritten or deleted")
        assertFalse(File(root, "Bibliotheque/b.mp4").exists())
    }
    @Test fun executablesStayWhereTheyAreAndAreNeverMoved() {
        put("Bibliotheque/a.mp4"); put("Bibliotheque/x.apk")
        val r = adoption().run(true); assertEquals(Status.DONE, r.status)
        assertTrue(File(root, "Bibliotheque/x.apk").isFile); assertFalse(File(root, "$idA/Bibliotheque/x.apk").exists())
    }
    @Test fun aReadOnlyDriveIsRefusedAndNothingChanges() {
        put("Bibliotheque/a.mp4"); val before = snapshot(root)
        assertEquals(Status.REFUSED_READ_ONLY, adoption(writable = false).run(true).status); assertEquals(before, snapshot(root))
    }
    @Test fun aFullDriveIsRefusedAndNothingChanges() {
        put("Bibliotheque/a.mp4"); val before = snapshot(root)
        assertEquals(Status.REFUSED_FULL, adoption(free = 100).run(true).status); assertEquals(before, snapshot(root))
        put("Bibliotheque/b.mp4"); UsbSpace.ensure(root, idA, T0)
        assertEquals(Status.REFUSED_FULL, adoption(free = 100).run(true).status, "also when the space already exists")
    }
    @Test fun nothingToAdoptIsSaidSo() { UsbLayout.ensure(root); assertEquals(Status.NOTHING, adoption().run(true).status) }

    // ---------- reprise à chaque étape ----------
    @Test fun interruptionAtEveryStepLosesNothingDuplicatesNothingAndResumes() {
        fun build() { root.deleteRecursively(); root.mkdirs(); put("Bibliotheque/a.mp4", 3000, 1); put("Bibliotheque/d/b.mp4", 2000, 2); put("Telechargements/c.part", 10, 3); put("index/settings.json", 20, 4) }
        build(); val before = snapshot(root)
        var steps = 0; adoption(fault = { steps++ }).run(true); assertTrue(steps >= 8, "$steps fault points")
        for (cut in 1..steps) {
            build(); var n = 0
            val r1 = adoption(fault = { if (++n == cut) throw IOException("cut at $cut") }).run(true)
            assertEquals(Status.INTERRUPTED, r1.status, "cut $cut")
            assertEquals(before, snapshot(root).filterKeys { !it.endsWith(UsbSpace.MARK) && !it.endsWith(UsbSpace.CLAIM) }.mapKeys { it.key.removePrefix("$idA/") }, "cut $cut: every file exactly once, same content")
            assertEquals(before.size, root.walkTopDown().filter { it.isFile && it.name != UsbSpace.MARK && it.name != UsbSpace.CLAIM }.count(), "cut $cut: no duplicate")
            val r2 = adoption(fault = {}, now = T0 + 60_000).run(true)
            assertTrue(r2.status == Status.DONE || r2.status == Status.NOTHING, "cut $cut resumes: ${r2.status}")
            assertEquals(before, snapshot(File(root, idA)).filterKeys { it != UsbSpace.MARK }, "cut $cut: all in the space after the resume")
            assertFalse(UsbSpace.hasLegacy(root), "cut $cut"); assertFalse(File(root, UsbSpace.CLAIM).exists(), "cut $cut")
        }
    }
    @Test fun aKeyPulledAfterTwoMovesStopsCleanlyAndResumes() {
        put("Bibliotheque/a.mp4", 100, 1); put("Bibliotheque/b.mp4", 100, 2); put("Bibliotheque/c.mp4", 100, 3); put("Bibliotheque/d.mp4", 100, 4)
        var calls = 0; val before = snapshot(root)
        val r = adoption().run(true, alive = { ++calls <= 2 })
        assertEquals(Status.INTERRUPTED, r.status); assertEquals(2, r.moved)
        assertEquals(Status.DONE, adoption().run(true).status)
        assertEquals(before, snapshot(File(root, idA)).filterKeys { it != UsbSpace.MARK }.mapKeys { it.key })
    }
    @Test fun aFileThatVanishedBetweenPlanAndMoveIsSkippedNotFatal() {
        put("Bibliotheque/a.mp4"); put("Bibliotheque/b.mp4")
        val r = adoption(fault = { if (it == "move:Bibliotheque/b.mp4") File(root, "Bibliotheque/b.mp4").delete() }).run(true)
        assertEquals(1, r.moved); assertTrue(r.status == Status.DONE || r.status == Status.DONE_WITH_CONFLICTS)
    }

    // ---------- concurrence entre deux TV ----------
    @Test fun anotherTvHoldingAFreshClaimBlocksTheAdoption() {
        put("Bibliotheque/a.mp4"); val before = snapshot(root)
        adoption(id = idA, fault = { if (it == "move:Bibliotheque/a.mp4") throw IOException("cut") }).run(true)
        val r = adoption(id = idB, now = T0 + 3_600_000).run(true)
        assertEquals(Status.REFUSED_CLAIMED, r.status); assertFalse(File(root, idB).exists() && File(root, idB).listFiles()!!.any { it.isDirectory && it.listFiles()!!.isNotEmpty() })
        assertEquals(before.keys, snapshot(root).keys.filter { !it.endsWith(UsbSpace.MARK) && !it.endsWith(UsbSpace.CLAIM) }.map { it.removePrefix("$idA/") }.toSet())
    }
    @Test fun aStaleClaimOfAnotherTvCanBeTakenOver() {
        put("Bibliotheque/a.mp4")
        adoption(id = idA, fault = { if (it == "move:Bibliotheque/a.mp4") throw IOException("cut") }).run(true)
        val r = adoption(id = idB, now = T0 + UsbSpace.CLAIM_STALE_MS + 1).run(true)
        assertEquals(Status.DONE, r.status); assertTrue(File(root, "$idB/Bibliotheque/a.mp4").isFile)
    }
    @Test fun theSameTvResumesItsOwnClaim() {
        put("Bibliotheque/a.mp4")
        adoption(fault = { if (it == "move:Bibliotheque/a.mp4") throw IOException("cut") }).run(true)
        assertEquals(Status.DONE, adoption(now = T0 + 10).run(true).status)
    }
    @Test fun adoptionNeverReadsOrTouchesAnotherTvSpace() {
        UsbSpace.ensure(root, idB, T0); put("$idB/Bibliotheque/theirs.mp4", 777, 9); put("$idB/index/settings.json")
        put("Bibliotheque/mine.mp4"); val theirs = snapshot(File(root, idB))
        val p = adoption().plan(); assertEquals(listOf("Bibliotheque/mine.mp4"), p.moves.map { it.rel }); assertTrue(p.conflicts.isEmpty())
        adoption().run(true); assertEquals(theirs, snapshot(File(root, idB)))
    }

    // ---------- propriétés ----------
    private fun randomTree(rnd: Random, n: Int) {
        val dirs = listOf("Bibliotheque", "Telechargements", "Medias", "index")
        repeat(n) {
            val depth = rnd.nextInt(3)
            val rel = dirs[rnd.nextInt(4)] + (1..depth).joinToString("") { "/d${rnd.nextInt(3)}" } + "/f${rnd.nextInt(40)}" + listOf(".mp4", ".part", ".json", ".apk", "")[rnd.nextInt(5)]
            File(root, rel).also { f -> f.parentFile.mkdirs(); if (!f.isDirectory) f.writeBytes(ByteArray(rnd.nextInt(2000)) { rnd.nextInt().toByte() }) }
        }
    }
    @Test fun randomTreesAreAdoptedWithoutLossOrDuplicateEvenWhenCutAnywhere() {
        for (seed in 1..40) {
            root.deleteRecursively(); root.mkdirs(); val rnd = Random(seed.toLong()); randomTree(rnd, 5 + rnd.nextInt(25))
            val before = snapshot(root); val movable = before.filterKeys { !it.endsWith(".apk") }; val stay = before.filterKeys { it.endsWith(".apk") }
            var total = 0; adoption(fault = { total++ }).run(true); root.deleteRecursively(); root.mkdirs(); val rnd2 = Random(seed.toLong()); randomTree(rnd2, 5 + rnd2.nextInt(25))
            val cut = 1 + rnd.nextInt(maxOf(total, 1)); var n = 0
            adoption(fault = { if (++n == cut) throw IOException("cut") }).run(true)
            adoption().run(true)
            val space = File(root, idA)
            assertEquals(movable, snapshot(space).filterKeys { it != UsbSpace.MARK }, "seed $seed cut $cut")
            assertEquals(stay, snapshot(root).filterKeys { !it.startsWith("$idA/") }, "seed $seed: executables stay, nothing else remains")
        }
    }
    @Test fun twoTvsWritingRandomlyOnTheSameKeyNeverSeeEachOthersFiles() {
        for (seed in 1..20) {
            root.deleteRecursively(); root.mkdirs(); val rnd = Random(seed.toLong())
            val dirs = listOf(idA, idB).map { (UsbSpace.ensure(root, it, T0) as UsbSpace.Ensured.Ready).dir }
            val written = listOf(HashSet<String>(), HashSet<String>())
            repeat(60) {
                val who = rnd.nextInt(2); val sub = UsbLayout.SUBDIRS[rnd.nextInt(4)]; val rel = "$sub/w${who}_${rnd.nextInt(20)}.bin"
                File(dirs[who], rel).writeBytes(ByteArray(rnd.nextInt(300)) { rnd.nextInt().toByte() }); written[who] += rel
            }
            for (who in 0..1) assertEquals(written[who], snapshot(dirs[who]).keys.filter { it != UsbSpace.MARK }.toSet(), "seed $seed TV $who")
            assertTrue(written[0].all { it.contains("w0_") } && snapshot(dirs[1]).keys.none { it.contains("w0_") })
        }
    }
}
