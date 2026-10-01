package castbridge.core.library.agent

import castbridge.core.net.JsonLite
import castbridge.core.tv.*
import java.io.File
import kotlin.test.*

class FolderIndexTest {
    private val tmp = kotlin.io.path.createTempDirectory("folders").toFile()
    @AfterTest fun tearDown() { tmp.deleteRecursively() }
    private fun ok(r: FolderIndex.Path) = (r as FolderIndex.Path.Ok).path

    @Test fun aFolderIsAPathOfAtMostFourSafeSegments() {
        val i = FolderIndex(null)
        assertEquals("Séries/Prison Break/Saison 01", ok(i.set("a.mkv", "  Séries / Prison Break /Saison 01 ")))
        listOf("../x", "Séries/../..", "/abs", "a//b".let { "a/./b" }, "a:b", "a/b/c/d/e", "x*y", "CON", "a/".let { "a/ ." }, "n".repeat(300)).forEach {
            assertTrue(i.set("b.mkv", it) is FolderIndex.Path.Bad, "should refuse: $it")
        }
        assertEquals("", i.folderOf("b.mkv"), "a refused folder changes nothing")
        assertEquals("Séries/Prison Break/Saison 01", i.folderOf("a.mkv"))
    }

    @Test fun theCaseOfAnExistingFolderWinsAndUnicodeIsNormalised() {
        val i = FolderIndex(null)
        i.set("a.mkv", "Séries/Lost")
        assertEquals("Séries/Lost", ok(i.set("b.mkv", "séries/LOST")))
        assertEquals("Séries", ok(i.set("c.mkv", "Séries")), "e + combining accent = the same folder as é")
    }

    @Test fun putBackToTheRootLeavesNoEmptyFolder() {
        val i = FolderIndex(null)
        i.set("a.mkv", "Films"); i.set("a.mkv", "")
        assertEquals(emptyList(), i.folders(setOf("a.mkv")))
    }

    @Test fun renameDeleteStashFollowTheFile() {
        val i = FolderIndex(null)
        i.set("a.mkv", "Films")
        i.renamed("a.mkv", "b.mkv"); assertEquals("Films", i.folderOf("b.mkv")); assertEquals("", i.folderOf("a.mkv"))
        i.stash("123-abcd", "b.mkv"); assertEquals("", i.folderOf("b.mkv"))
        assertEquals("Films", i.unstash("123-abcd")); assertNull(i.unstash("123-abcd"))
        i.set("c.mkv", "X"); i.deleted("c.mkv"); assertEquals("", i.folderOf("c.mkv"))
    }

    @Test fun renamingAFolderMovesWhatIsUnderItAndMergesWithoutLoss() {
        val i = FolderIndex(null)
        i.set("a", "Séries/Lost/Saison 1"); i.set("b", "Séries/Lost"); i.set("c", "Séries/Autre")
        assertEquals("Séries/Perdus", ok(i.renameFolder("Séries/Lost", "Séries/Perdus")))
        assertEquals("Séries/Perdus/Saison 1", i.folderOf("a")); assertEquals("Séries/Perdus", i.folderOf("b")); assertEquals("Séries/Autre", i.folderOf("c"))
        assertTrue(i.renameFolder("Séries/Perdus", "../x") is FolderIndex.Path.Bad)
        assertTrue(i.renameFolder("", "X") is FolderIndex.Path.Bad)
        assertEquals("Séries/Autre", ok(i.renameFolder("Séries/Perdus", "Séries/Autre")))
        assertEquals(setOf("Séries/Autre", "Séries/Autre/Saison 1"), setOf(i.folderOf("a"), i.folderOf("b"), i.folderOf("c")))
    }

    @Test fun itSurvivesARestartAndAHalfWrittenFileIsIgnored() {
        val f = File(tmp, "folders.db")
        FolderIndex(f).apply { set("a é.mkv", "Séries/Lost"); set("b.mkv", "Films"); stash("1-aaaa", "b.mkv") }
        val again = FolderIndex(f)
        assertEquals("Séries/Lost", again.folderOf("a é.mkv"))
        assertEquals("Films", again.unstash("1-aaaa"))
        // a cut while writing leaves only the temp file: the real one is untouched, the temp one is never read
        File(tmp, "folders.db.tmp").writeText("f\tgarbage")
        assertEquals("Séries/Lost", FolderIndex(f).folderOf("a é.mkv"))
        // a truncated / corrupt line is skipped, the rest is kept
        f.appendText("f\t%ZZ\tx\nbroken line\n")
        assertEquals("Séries/Lost", FolderIndex(f).folderOf("a é.mkv"))
    }

    @Test fun staleEntriesArePrunedWhenListed() {
        val i = FolderIndex(null)
        i.set("a", "X"); i.set("b", "X")
        assertEquals(listOf("X" to 1), i.folders(setOf("a")))
        assertEquals("", i.folderOf("b"))
    }
}

class TvFoldersTest {
    private val r = AgentRig(withFolders = true)
    @AfterTest fun tearDown() = r.close()
    private fun ctx() = AgentContext(zone = java.time.ZoneId.of("UTC"))

    @Suppress("UNCHECKED_CAST")
    private fun library() = JsonLite.obj(r.tv.library()).let { it to (it["files"] as List<Map<String, Any?>>).associateBy { f -> f["name"] as String } }

    @Test fun theLibraryAnnouncesFoldersAndOldClientsStillSeeEveryFileWithItsOwnName() {
        r.put("internal", "Prison Break – S01E01.mkv", 5000); r.put("usb", "Autre.mkv", 3000)
        assertEquals(200, r.call("POST", "/api/folders/set?name=${TvClient.enc("Prison Break – S01E01.mkv")}&folder=${TvClient.enc("Séries/Prison Break/Saison 01")}").first)
        val (j, files) = library()
        assertEquals(true, j["folders"])
        assertEquals("Séries/Prison Break/Saison 01", files["Prison Break – S01E01.mkv"]!!["folder"])
        assertEquals("", files["Autre.mkv"]!!["folder"])
        assertEquals(2L, j["count"], "the flat list is complete: a client that ignores `folder` loses nothing")
        // the old routes keep working by plain name, whatever the folder
        assertEquals(200, r.call("GET", "/stream/${TvClient.enc("Prison Break – S01E01.mkv")}").first)
        assertEquals(200, r.call("POST", "/api/play?name=${TvClient.enc("Prison Break – S01E01.mkv")}").first)
        // and on the disk nothing moved: the file is still flat
        assertTrue(File(r.internalDir, "Prison Break – S01E01.mkv").isFile)
        assertEquals(emptyList(), r.internalDir.listFiles()!!.filter { it.isDirectory })
    }

    @Test fun aTvWithoutFoldersKeepsTheOldJsonShape() {
        val old = AgentRig()
        try {
            old.put("internal", "a.mkv", 100)
            val j = JsonLite.obj(old.tv.library())
            assertNull(j["folders"]); @Suppress("UNCHECKED_CAST") assertNull((j["files"] as List<Map<String, Any?>>).first()["folder"])
            assertNotEquals(200, old.call("GET", "/api/folders").first)
        } finally { old.close() }
    }

    @Test fun theFolderFollowsRenameDeleteAndTheBin() {
        r.put("internal", "a.mkv", 100); r.put("internal", "b.mkv", 100)
        r.call("POST", "/api/folders/set?name=a.mkv&folder=Films"); r.call("POST", "/api/folders/set?name=b.mkv&folder=Films")
        assertEquals(200, r.call("POST", "/api/rename?name=a.mkv&to=c.mkv").first)
        assertEquals("Films", library().second["c.mkv"]!!["folder"])
        val id = JsonLite.obj(r.call("POST", "/api/trash/put?name=b.mkv").second)["id"] as String
        assertFalse(library().second.containsKey("b.mkv"))
        assertEquals("Films", JsonLite.obj(r.call("POST", "/api/trash/restore?id=$id").second)["folder"])
        assertEquals("Films", library().second["b.mkv"]!!["folder"], "restored into its folder")
        assertEquals(200, r.call("POST", "/api/delete?name=c.mkv").first)
        r.put("internal", "c.mkv", 50); Thread.sleep(1100)                    // a new file with the old name does not inherit the folder (the listing cache lasts 1 s)
        assertEquals("", library().second["c.mkv"]!!["folder"])
    }

    @Test fun movingToAnotherVolumeKeepsTheFolder() {
        r.put("internal", "m.mkv", 100)
        r.call("POST", "/api/folders/set?name=m.mkv&folder=Films")
        assertEquals(200, r.call("POST", "/api/storage/move?name=m.mkv&to=usb-1234").first)
        repeat(100) { if (File(r.usbDir, "m.mkv").exists() && !File(r.internalDir, "m.mkv").exists()) return@repeat; Thread.sleep(50) }
        val f = library().second["m.mkv"]!!
        assertEquals("usb-1234", f["volume"]); assertEquals("Films", f["folder"])
    }

    @Test fun badFoldersAndUnknownFilesAreRefused() {
        r.put("internal", "a.mkv", 100)
        assertEquals(400, r.call("POST", "/api/folders/set?name=a.mkv&folder=${TvClient.enc("../etc")}").first)
        assertEquals(400, r.call("POST", "/api/folders/set?name=${TvClient.enc("../a.mkv")}&folder=X").first)
        assertEquals(404, r.call("POST", "/api/folders/set?name=zzz.mkv&folder=X").first)
        assertEquals(405, r.call("GET", "/api/folders/set?name=a.mkv&folder=X").first)
        assertEquals(401, r.call("POST", "/api/folders/set?name=a.mkv&folder=X", pin = null).first, "behind the TV code like every route")
    }

    @Test fun aFolderOnAFileBeingPlayedIsHarmless() {
        r.put("internal", "a.mkv", 100)
        r.player.st = PlayerState("playing", "a.mkv", 0, 1000)
        assertEquals(200, r.call("POST", "/api/folders/set?name=a.mkv&folder=Films").first)
        assertEquals("playing", r.player.state().state)
    }

    @Test fun foldersRouteListsFoldersInUseWithCounts() {
        r.put("internal", "a.mkv", 10); r.put("internal", "b.mkv", 10); r.put("internal", "c.mkv", 10)
        r.call("POST", "/api/folders/set?name=a.mkv&folder=X/Y"); r.call("POST", "/api/folders/set?name=b.mkv&folder=X/Y"); r.call("POST", "/api/folders/set?name=c.mkv&folder=Z")
        assertEquals("""{"folders":[{"path":"X/Y","count":2},{"path":"Z","count":1}]}""", r.call("GET", "/api/folders").second)
        assertEquals(200, r.call("POST", "/api/folders/rename?from=X&to=W").first)
        assertTrue(r.call("GET", "/api/folders").second.contains("\"W/Y\""))
    }

    // ------------------------------------------------------------------ the assistant, end to end

    @Test fun theAssistantFilesSeriesIntoFoldersOnTheRealTvAndUndoPutsThemBack() {
        r.put("internal", "Prison.Break.S01E04.FRENCH.DVDRip.avi", 4000); r.put("internal", "Prison.Break.S01E05.FRENCH.DVDRip.avi", 4000)
        r.put("usb", "Inception.2010.1080p.BluRay.mkv", 5000)
        val a = LibraryAgent(ctx(), fingerprinter = TvFingerprinter(r.tv)).analyze(TvSnapshot.read(r.tv))
        assertTrue(a.snapshot.foldersSupported)
        val folders = a.plan.renames.mapNotNull { it.toFolder }
        assertTrue(folders.any { it == "Séries/Prison Break/Saison 01" }, folders.toString())
        val ops = TvLibraryOps(r.tv)
        assertTrue(ops.folders)
        val j = MemoryJournal()
        val ex = Executor(ops, j, ctx())
        val run = ex.run(a.plan, a.plan.defaultSelection(), confirmDeletions = false)
        assertEquals(a.plan.defaultSelection().size, run.done, run.reports.toString())
        // flat on the disks, folders in the library
        assertEquals(listOf("Inception (2010).mkv", "Prison Break – S01E04.avi", "Prison Break – S01E05.avi"), r.listing())
        assertEquals(emptyList(), listOf(r.internalDir, r.usbDir).flatMap { it.listFiles()!!.filter { f -> f.isDirectory && !f.name.startsWith(".") } })
        assertEquals("Séries/Prison Break/Saison 01", library().second["Prison Break – S01E04.avi"]!!["folder"])
        // a second look finds nothing left to do: stable
        val again = LibraryAgent(ctx()).analyze(TvSnapshot.read(r.tv))
        assertEquals(emptyList(), again.plan.renames.map { it.after }, "no flip-flop")
        // undo: names and folders back
        val u = ex.undo()
        assertEquals(run.done > 0, u.restored > 0, u.reports.toString())
        assertEquals(0, u.failed, u.reports.toString())
        assertEquals(listOf("Inception.2010.1080p.BluRay.mkv", "Prison.Break.S01E04.FRENCH.DVDRip.avi", "Prison.Break.S01E05.FRENCH.DVDRip.avi"), r.listing())
        assertTrue(library().second.values.all { it["folder"] == "" })
    }

    @Test fun anOldTvGetsNoFolderProposalAndNothingFails() {
        val old = AgentRig()
        try {
            old.put("internal", "Prison.Break.S01E04.FRENCH.DVDRip.avi", 4000)
            val a = LibraryAgent(ctx()).analyze(TvSnapshot.read(old.tv))
            assertFalse(a.snapshot.foldersSupported)
            assertTrue(a.plan.renames.all { it.toFolder == null })
            val ops = TvLibraryOps(old.tv)
            assertFalse(ops.folders)
            assertTrue(ops.moveToFolder(Loc("internal", "", "x"), "Séries") is OpResult.Fail)
        } finally { old.close() }
    }

    @Test fun libraryScreensGetOneRowPerFolderAndEveryFileStaysInTheFlatSections() {
        data class E(override val name: String, override val folder: String, override val mtime: Long = 0) : LibraryEntry {
            override val title get() = name; override val resumeMs = 0L; override val watched = false; override val playedAtMs = 0L; override val type = MediaType.VIDEO
        }
        val items = listOf(E("S01E02.mkv", "Séries/Lost/Saison 01"), E("S01E01.mkv", "Séries/Lost/Saison 01"), E("Film.mkv", ""), E("Clip.mkv", "Clips"))
        val s = LibrarySections.build(items)
        assertEquals(listOf("S01E01.mkv", "S01E02.mkv"), s.first { it.id == LibrarySections.FOLDER + "Séries/Lost/Saison 01" }.items.map { it.name }, "in episode order")
        assertEquals("Séries › Lost › Saison 01", s.first { it.id.startsWith(LibrarySections.FOLDER + "Séries") }.title)
        assertEquals(4, s.first { it.id == LibrarySections.ALL }.items.size, "a file in a folder is still in 'Toutes'")
    }
}
