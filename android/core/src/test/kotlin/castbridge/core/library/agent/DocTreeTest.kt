package castbridge.core.library.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The phone-folder path (SAF), end to end on a simulated provider: read, plan, apply, trash, undo, and the awkward providers. */
class DocTreeTest {
    private val ctx = AgentContext(folders = true, uiLang = "fr", currentYear = 2026)

    private fun movies() = FakeDocProvider().apply {
        add("Movies/Inception.2010.1080p.BluRay.x264-RARBG.mkv", 3L shl 30)
        add("Movies/Sans titre.mp4", 40L shl 20)
        add("Download/Séries/Prison.Break.S01E04.720p.HDTV.x264.mkv", 400L shl 20)
        add("Download/Séries/Prison.Break.S01E05.720p.HDTV.x264.mkv", 410L shl 20)
        add("Download/Séries/Prison.Break.S01E05.720p.HDTV.x264.srt", 60_000)
        add("WhatsApp/Media/WhatsApp Video/VID-20240315-WA0012.mp4", 12L shl 20)
        add("WhatsApp/Media/WhatsApp Video/.nomedia", 0)
        add(".hidden/secret.mkv", 1L shl 20)
    }

    private fun analyze(lib: DocTreeLibrary): Analysis = LibraryAgent(ctx).analyze(lib.snapshot())

    private fun run(p: FakeDocProvider, journal: Journal = MemoryJournal(), select: (Plan) -> Set<String> = { it.allSafe() }, confirm: Boolean = false): Triple<DocTreeLibrary, Analysis, RunResult> {
        val lib = DocTreeLibrary(p)
        val a = analyze(lib)
        val ex = Executor(lib.Ops(), journal, ctx)
        return Triple(lib, a, ex.run(a.plan, select(a.plan), confirm, "phone-t"))
    }

    @Test fun `the walk reads every folder, skips hidden files and the bin, and tells the volume`() {
        val p = movies()
        p.add("Corbeille CastBridge/old.mkv", 1L shl 20)
        val seen = ArrayList<Int>()
        val snap = DocTreeLibrary(p).snapshot(onProgress = { seen += it.filesFound })
        val names = snap.files.map { (if (it.folder.isEmpty()) "" else it.folder + "/") + it.name }.sorted()
        assertEquals(6, names.size, names.toString())
        assertTrue("Download/Séries/Prison.Break.S01E04.720p.HDTV.x264.mkv" in names)
        assertFalse(names.any { it.startsWith(".hidden") || it.contains("Corbeille") || it.endsWith(".nomedia") })
        assertEquals("Carte SD", snap.volumes.single().label)
        assertTrue(seen.isNotEmpty() && seen.last() == 6, "progress is reported while walking: $seen")
    }

    @Test fun `apply renames and puts files in folders, then undo gives back every original path`() {
        val p = movies()
        val before = p.all()
        val journal = MemoryJournal()
        val (lib, a, r) = run(p, journal)
        assertTrue(a.plan.renames.isNotEmpty())
        assertTrue(r.done >= 4, r.reports.toString())
        val after = p.all()
        assertTrue(after.any { it.startsWith("Séries/Prison Break/") && it.contains("S01E04") }, after.toString())
        assertFalse(p.exists("Download/Séries/Prison.Break.S01E04.720p.HDTV.x264.mkv"))
        // the subtitle follows its video
        assertTrue(after.any { it.startsWith("Séries/Prison Break/") && it.endsWith(".srt") }, after.toString())
        val u = Executor(lib.Ops(), journal, ctx).undo("phone-t")
        assertEquals(0, u.failed, u.reports.toString())
        assertEquals(before.filter { !it.contains("Corbeille") }, p.all().filter { !it.contains("Corbeille") }, "everything is back where it was")
    }

    @Test fun `nothing is overwritten when the target name already exists`() {
        val p = FakeDocProvider()
        p.add("Séries/Prison Break/Saison 01/Prison Break – S01E04.mkv", 5L shl 20)
        p.add("Download/Prison.Break.S01E04.720p.mkv", 6L shl 20)
        val (_, _, r) = run(p)
        assertEquals(0, r.failed, r.reports.toString())
        assertEquals(2, p.all().size, "both files survive")
        assertEquals(5L shl 20, p.sizeOf("Séries/Prison Break/Saison 01/Prison Break – S01E04.mkv"), "the existing file is untouched")
    }

    @Test fun `trash moves to the bin folder, restore returns to the ORIGINAL folder, purge deletes only the bin item`() {
        val p = FakeDocProvider()
        p.add("A/Film.mkv", 100L shl 20); p.add("B/Film.mkv", 100L shl 20)
        val lib = DocTreeLibrary(p); val ops = lib.Ops()
        val t1 = ops.trash(Loc("phone", "A", "Film.mkv")) as OpResult.Ok
        val t2 = ops.trash(Loc("phone", "B", "Film.mkv")) as OpResult.Ok       // same name: unique in the bin
        assertEquals(setOf("Film.mkv", "Film (2).mkv"), setOf(t1.trashId, t2.trashId))
        assertEquals(listOf("Corbeille CastBridge/Film (2).mkv", "Corbeille CastBridge/Film.mkv"), p.all())
        assertNotNull(ops.restore(t2.trashId!!, Loc("phone", "B", "Film.mkv")).let { it as? OpResult.Ok })
        assertEquals(listOf("B/Film.mkv", "Corbeille CastBridge/Film.mkv"), p.all())
        assertTrue(ops.purge("Film.mkv"))
        assertEquals(listOf("B/Film.mkv"), p.all(), "only the bin item was deleted")
    }

    @Test fun `restore when the original name was taken meanwhile never overwrites`() {
        val p = FakeDocProvider(); p.add("A/Film.mkv", 100L shl 20)
        val ops = DocTreeLibrary(p).Ops()
        val t = ops.trash(Loc("phone", "A", "Film.mkv")) as OpResult.Ok
        p.add("A/Film.mkv", 7L shl 20)
        val r = ops.restore(t.trashId!!, Loc("phone", "A", "Film.mkv")) as OpResult.Ok
        assertEquals("Film (restauré).mkv", r.loc.name)
        assertEquals(setOf("A/Film.mkv", "A/Film (restauré).mkv"), p.all().toSet())
        assertEquals(7L shl 20, p.sizeOf("A/Film.mkv"))
    }

    @Test fun `a provider without move support refuses the trash and leaves the file exactly as it was`() {
        val p = FakeDocProvider(supportsMove = false)
        p.add("A/Film.mkv", 100L shl 20); p.add("Corbeille CastBridge/Film.mkv", 1L shl 20)    // forces the "unique name in the bin" path
        val ops = DocTreeLibrary(p).Ops()
        val r = ops.trash(Loc("phone", "A", "Film.mkv"))
        assertTrue(r is OpResult.Fail && "rien n'a été supprimé" in r.reason, r.toString())
        assertTrue(p.exists("A/Film.mkv"), "the file is back under its own name, not left renamed")
        assertFalse(p.exists("A/Film (2).mkv"))
        assertEquals(setOf("A/Film.mkv", "Corbeille CastBridge/Film.mkv"), p.all().toSet())
    }

    @Test fun `without move support a rename still works and a folder move is reported, not faked`() {
        val p = FakeDocProvider(supportsMove = false); p.add("Download/Prison.Break.S01E04.720p.mkv", 5L shl 20)
        val (_, _, r) = run(p)
        // the rename in place happens; putting it in a folder is refused by the provider: reported as a failure, nothing lost
        assertEquals(1, p.all().size)
        assertTrue(r.reports.single().let { it.state == State.FAILED && it.note!!.contains("déplacer") }, r.reports.toString())
    }

    @Test fun `a provider that silently renames is followed by the journal so that undo still works`() {
        val p = FakeDocProvider(silentRename = true)
        p.add("Download/Inception 2010 1080p.mkv", 3L shl 30)
        val lib = DocTreeLibrary(p); val ops = lib.Ops()
        val flat = AgentContext(folders = false, currentYear = 2026)
        val a = LibraryAgent(flat).analyze(lib.snapshot())
        val wanted = a.plan.renames.single().toName!!
        p.add("Download/$wanted", 1L shl 20)      // created by another app after the analysis (the library's listing is not aware of it)
        val journal = MemoryJournal()
        val r = Executor(ops, journal, flat).run(a.plan, a.plan.allSafe(), false, "phone-s")
        assertEquals(0, r.failed, r.reports.toString())
        val actual = p.all().single { it != "Download/$wanted" }.substringAfter("Download/")
        assertTrue(actual != wanted && actual != "Inception 2010 1080p.mkv", "the provider changed the name: $actual")
        assertEquals(actual, journal.entries().last { it.state == State.DONE }.to!!.name, "the journal records the REAL name")
        val u = Executor(ops, journal, flat).undo("phone-s")
        assertEquals(0, u.failed, u.reports.toString())
        assertTrue(p.exists("Download/Inception 2010 1080p.mkv"))
    }

    @Test fun `the name given by the system is reported by rename`() {
        val p = FakeDocProvider(silentRename = true); p.add("Download/x.mkv", 1L shl 20)
        val ops = DocTreeLibrary(p).Ops()
        ops.stat(Loc("phone", "Download", "x.mkv"))           // primes the listing
        p.add("Download/Neuf.mkv", 1L shl 20)                 // behind its back
        val r = ops.rename(Loc("phone", "Download", "x.mkv"), "Neuf.mkv") as OpResult.Ok
        assertEquals("Neuf (2).mkv", r.loc.name)
    }

    @Test fun `file modified since the analysis is left alone`() {
        val p = movies(); val lib = DocTreeLibrary(p)
        val a = analyze(lib)
        p.setSize("Movies/Inception.2010.1080p.BluRay.x264-RARBG.mkv", 123)
        val r = Executor(lib.Ops(), MemoryJournal(), ctx).run(a.plan, a.plan.allSafe(), false, "r")
        assertTrue(p.exists("Movies/Inception.2010.1080p.BluRay.x264-RARBG.mkv"))
        assertTrue(r.reports.any { it.state == State.SKIPPED && it.note!!.contains("changé") }, r.reports.toString())
    }

    @Test fun `SD card removed during the run gives clean reports and no exception`() {
        val p = movies(); val lib = DocTreeLibrary(p)
        val a = analyze(lib)
        p.mounted = false
        val r = Executor(lib.Ops(), MemoryJournal(), ctx).run(a.plan, a.plan.allSafe(), false, "r")
        assertEquals(0, r.done)
        assertTrue(r.reports.all { it.state == State.SKIPPED || it.state == State.FAILED })
        p.mounted = true
        assertTrue(p.all().contains("Movies/Sans titre.mp4"), "nothing was lost")
    }

    @Test fun `read-only volume is refused with a message`() {
        val p = FakeDocProvider(); p.add("Download/Prison.Break.S01E04.720p.mkv", 5L shl 20); p.readOnly = true
        val (_, _, r) = run(p)
        assertEquals(0, r.done); assertEquals(1, r.failed)
        assertEquals(listOf("Download/Prison.Break.S01E04.720p.mkv"), p.all())
    }

    @Test fun `names are case-insensitive like on an SD card`() {
        val p = FakeDocProvider(); p.add("Download/FILM.MKV", 5L shl 20)
        val ops = DocTreeLibrary(p).Ops()
        assertNotNull(ops.stat(Loc("phone", "download", "film.mkv")))
        assertTrue(ops.nameTaken(Loc("phone", "Download", "Film.mkv")))
    }

    @Test fun `durations are read once and the next analysis only reads what is new`() {
        val p = FakeDocProvider()
        for (i in 1..40) p.add("Videos/Clip $i.mp4", 20L shl 20, duration = 60_000L + i)
        val cache = MapDurationCache()
        val s1 = DocTreeLibrary(p, cache).snapshot(maxNewDurations = 15)
        assertEquals(15, p.durationCalls)
        assertEquals(15, s1.files.count { it.durationMs > 0 })
        val s2 = DocTreeLibrary(p, cache).snapshot(maxNewDurations = 15)
        assertEquals(30, p.durationCalls, "second analysis reads the next 15 only")
        assertEquals(30, s2.files.count { it.durationMs > 0 })
        val s3 = DocTreeLibrary(p, cache).snapshot(maxNewDurations = 100)
        assertEquals(40, p.durationCalls)
        assertEquals(40, s3.files.count { it.durationMs > 0 })
        p.add("Videos/Nouveau.mp4", 20L shl 20, duration = 5)
        DocTreeLibrary(p, cache).snapshot(maxNewDurations = 100)
        assertEquals(41, p.durationCalls, "only the new file")
    }

    @Test fun `thousands of files rename without listing the folder again for each one`() {
        val p = FakeDocProvider()
        for (i in 1..1500) p.add("Download/Serie.X.S01E${"%03d".format(i % 999 + 1)}.${i}.720p.HDTV.mkv", (100L + i) shl 20)
        val lib = DocTreeLibrary(p)
        val snap = lib.snapshot(withDurations = false)
        val calls0 = p.childrenCalls
        val a = LibraryAgent(AgentContext(folders = false, currentYear = 2026)).analyze(snap)
        val sel = a.plan.renames.take(500).map { it.id }.toSet()
        val r = Executor(lib.Ops(), MemoryJournal(), AgentContext(folders = false)).run(a.plan, sel, false, "big")
        assertEquals(500, r.done + r.skipped + r.failed)
        assertTrue(r.done >= 400, "done=${r.done} failed=${r.failed}")
        assertTrue(p.childrenCalls - calls0 < 400, "listings during the run: ${p.childrenCalls - calls0} for 500 renames")
    }
}
