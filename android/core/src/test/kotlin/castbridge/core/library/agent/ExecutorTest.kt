package castbridge.core.library.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val GB = 1L shl 30
private const val MB = 1L shl 20

class ExecutorTest {
    private val ctx = AgentContext(nowMs = 0, currentYear = 2026)
    private val internal = VolumeInfo("internal", "Mémoire interne", "internal", free = 10 * GB, total = 32 * GB)
    private val usb = VolumeInfo("usb1", "Clé USB", "usb", free = 40 * GB, total = 58 * GB, fs = "exFAT")

    private fun ref(name: String, size: Long = 100 * MB, vol: String = "internal", folder: String = "", origin: Origin = Origin.TV) = FileRef(origin, name, size, volumeId = vol, folder = folder)

    private fun fake(vararg f: FileRef, folders: Boolean = false): FakeOps = FakeOps(folders).also { o ->
        o.addVolume(internal); o.addVolume(usb)
        f.forEach { o.add(it.volumeId, it.name, it.size, it.folder) }
    }

    private fun rename(f: FileRef, to: String?, folder: String? = null, id: String = "r:${f.name}") =
        Change(id, ChangeType.RENAME, f, Kind.UNKNOWN, toName = to, toFolder = folder, reason = "test", confidence = 0.9, checked = true)

    private fun trash(f: FileRef, keep: FileRef? = null) = Change("t:${f.name}", ChangeType.TRASH, f, Kind.UNKNOWN, why = TrashWhy.DUPLICATE, keep = keep, reason = "test", confidence = 0.9)
    private fun move(f: FileRef, to: String) = Change("m:${f.name}", ChangeType.MOVE, f, Kind.UNKNOWN, toVolume = to, reason = "test", confidence = 0.9)

    private fun run(ops: FakeOps, journal: Journal, vararg c: Change, confirm: Boolean = false, ctx: AgentContext = this.ctx, runId: String = "run1"): RunResult {
        val plan = Plan(c.toList())
        return Executor(ops, journal, ctx).run(plan, plan.changes.map { it.id }.toSet(), confirm, runId)
    }

    // ------------------------------------------------------------------ the golden rule: nothing is deleted without a confirmation

    @Test fun nothingGoesToTheTrashWithoutExplicitConfirmation() {
        val a = ref("a.mkv"); val keep = ref("b.mkv")
        val ops = fake(a, keep)
        val j = MemoryJournal()
        val r = run(ops, j, trash(a, keep), confirm = false)
        assertEquals(State.SKIPPED, r.reports.single().state)
        assertTrue(ops.has("internal", "a.mkv"), "the file must still be there")
        assertTrue(ops.trashed.isEmpty())
        assertEquals("suppression non confirmée", j.entries().single().note)
    }

    @Test fun confirmedDeletionsGoToTheRecoverableTrashNotToNothing() {
        val a = ref("a.mkv"); val keep = ref("b.mkv")
        val ops = fake(a, keep)
        val j = MemoryJournal()
        val r = run(ops, j, trash(a, keep), confirm = true)
        assertEquals(State.DONE, r.reports.single().state)
        assertFalse(ops.has("internal", "a.mkv"))
        assertEquals(1, ops.trashed.size, "recoverable")
        // undo brings it back
        val u = Executor(ops, j, ctx).undo()
        assertEquals(1, u.restored)
        assertTrue(ops.has("internal", "a.mkv"))
        assertTrue(ops.trashed.isEmpty())
    }

    @Test fun theLastCopyIsNeverTrashed() {
        val a = ref("a.mkv"); val keep = ref("b.mkv")
        val ops = fake(a, keep)
        // the copy to keep vanished since the analysis
        ops.trash(keep.loc)
        val r = run(ops, MemoryJournal(), trash(a, keep), confirm = true)
        assertEquals(State.SKIPPED, r.reports.single().state)
        assertTrue(ops.has("internal", "a.mkv"))
    }

    @Test fun twoGroupsCannotTrashEachOthersKeeper() {
        val a = ref("a.mkv"); val b = ref("b.mkv")
        val ops = fake(a, b)
        val r = run(ops, MemoryJournal(), trash(a, b), trash(b, a), confirm = true)
        assertEquals(1, r.done)
        assertEquals(1, r.skipped)
        assertTrue(ops.has("internal", "a.mkv") || ops.has("internal", "b.mkv"), "one copy must remain")
    }

    // ------------------------------------------------------------------ names, paths, overwrites

    @Test fun renamesNeverOverwriteATakenName() {
        val a = ref("a.mkv"); val other = ref("Show – S01E01.mkv", size = 5 * MB)
        val ops = fake(a, other)
        val r = run(ops, MemoryJournal(), rename(a, "Show – S01E01.mkv"))
        assertEquals(State.DONE, r.reports.single().state)
        assertTrue(ops.has("internal", "Show – S01E01.mkv"), "the other file is untouched")
        assertTrue(ops.has("internal", "Show – S01E01 (2).mkv"))
        assertEquals(5 * MB, ops.stat(Loc("internal", "", "Show – S01E01.mkv"))!!.size)
    }

    @Test fun theTvNameSpaceIsSharedAcrossVolumes() {
        val a = ref("a.mkv", vol = "usb1"); val other = ref("x.mkv", vol = "internal")
        val ops = fake(a, other)
        run(ops, MemoryJournal(), rename(a, "x.mkv"))
        assertTrue(ops.has("usb1", "x (2).mkv"))
    }

    @Test fun pathsOutsideTheLibraryAndForbiddenCharactersAreRefused() {
        val f = ref("a.mkv", folder = "", origin = Origin.PHONE, vol = "phone")
        val ops = FakeOps(true).also { it.addVolume(VolumeInfo("phone", "Téléphone", "phone", 5 * GB, 64 * GB)); it.add("phone", "a.mkv", 100 * MB) }
        val bad = listOf(
            rename(f, "../evil.mkv"), rename(f, "sub/evil.mkv"), rename(f, "a\\b.mkv"), rename(f, "evil:name.mkv"), rename(f, "star*.mkv"), rename(f, ".hidden.mkv"), rename(f, "x.mkv ", id = "r2"),
            rename(f, null, folder = "../outside"), rename(f, null, folder = "/etc"), rename(f, null, folder = "a/../../b"), rename(f, null, folder = "C:\\x"),
            rename(f, null, folder = "a/b/c/d/e/f"), rename(f, "CON.mkv"), rename(f, "x".repeat(300) + ".mkv"),
        ).mapIndexed { i, c -> c.copy(id = "bad$i") }
        val j = MemoryJournal()
        val r = Executor(ops, j, ctx).run(Plan(bad), bad.map { it.id }.toSet(), true, "r")
        assertEquals(bad.size, r.skipped, r.reports.toString())
        assertEquals(listOf("phone:a.mkv"), ops.paths(), "nothing may have changed")
        assertTrue(ops.log.isEmpty(), "no operation may even be attempted: ${ops.log}")
    }

    @Test fun aSourceNameWithASeparatorIsRefused() {
        val evil = ref("../../etc/passwd")
        val ops = fake()
        val r = run(ops, MemoryJournal(), trash(evil), confirm = true)
        assertEquals(State.SKIPPED, r.reports.single().state)
        assertTrue(ops.log.isEmpty())
    }

    @Test fun foldersAreCreatedAndFilesPutInThem() {
        val f = ref("Inception.2010.mkv", vol = "phone", origin = Origin.PHONE)
        val ops = FakeOps(true).also { it.addVolume(VolumeInfo("phone", "Téléphone", "phone", 5 * GB, 64 * GB)); it.add("phone", f.name, f.size) }
        val r = Executor(ops, MemoryJournal(), ctx).run(Plan(listOf(rename(f, "Inception (2010).mkv", "Films/Inception (2010)"))), setOf("r:${f.name}"), false, "r")
        assertEquals(State.DONE, r.reports.single().state, r.reports.toString())
        assertTrue(ops.has("phone", "Inception (2010).mkv", "Films/Inception (2010)"))
        assertTrue("phone:Films/Inception (2010)" in ops.dirs)
    }

    @Test fun aNameAlreadyInTheTargetFolderIsMadeUnique() {
        val f = ref("a.mkv", vol = "phone", origin = Origin.PHONE)
        val ops = FakeOps(true).also { it.addVolume(VolumeInfo("phone", "Téléphone", "phone", 5 * GB, 64 * GB)); it.add("phone", "a.mkv", 100 * MB); it.add("phone", "a.mkv", 7 * MB, "Films") }
        Executor(ops, MemoryJournal(), ctx).run(Plan(listOf(rename(f, null, "Films"))), setOf("r:a.mkv"), false, "r")
        assertTrue(ops.has("phone", "a (2).mkv", "Films"))
        assertEquals(7 * MB, ops.stat(Loc("phone", "Films", "a.mkv"))!!.size, "the file already in the folder is untouched")
    }

    // ------------------------------------------------------------------ playing, protected, changed files

    @Test fun nothingHappensToAFilePlayingNow() {
        val a = ref("a.mkv")
        val ops = fake(a); ops.playing += "a.mkv"
        val r = run(ops, MemoryJournal(), rename(a, "b.mkv"))
        assertEquals("en cours de lecture", r.reports.single().note)
        assertTrue(ops.has("internal", "a.mkv"))
    }

    @Test fun aFileMarkedPlayingInTheAnalysisIsSkippedEvenIfTheLiveCheckFails() {
        val a = ref("a.mkv").copy(playing = true)
        val ops = fake(a)
        assertEquals(State.SKIPPED, run(ops, MemoryJournal(), rename(a, "b.mkv")).reports.single().state)
    }

    @Test fun protectedFilesAndChildProfilesBlockEverything() {
        val a = ref("secret.mkv")
        val guard = object : ContentGuard { override fun isProtected(file: FileRef) = file.name == "secret.mkv"; override val childProfileActive = false }
        val ops = fake(a, ref("ok.mkv"))
        assertEquals("protégé par le contrôle parental", run(ops, MemoryJournal(), rename(a, "b.mkv"), ctx = ctx.copy(guard = guard)).reports.single().note)
        val child = object : ContentGuard { override fun isProtected(file: FileRef) = false; override val childProfileActive = true }
        val r = run(ops, MemoryJournal(), rename(ref("ok.mkv"), "z.mkv"), trash(ref("ok.mkv")), confirm = true, ctx = ctx.copy(guard = child))
        assertEquals(2, r.skipped)
        assertTrue(ops.has("internal", "ok.mkv") && ops.has("internal", "secret.mkv"))
    }

    @Test fun aFileChangedOrGoneSinceTheAnalysisIsLeftAlone() {
        val a = ref("a.mkv", size = 100 * MB)
        val ops = fake(ref("a.mkv", size = 101 * MB))
        assertEquals("le fichier a changé depuis l'analyse", run(ops, MemoryJournal(), rename(a, "b.mkv")).reports.single().note)
        assertTrue(run(fake(), MemoryJournal(), rename(a, "b.mkv")).reports.single().note!!.startsWith("fichier introuvable"))
    }

    @Test fun onlySelectedChangesRun() {
        val a = ref("a.mkv"); val b = ref("b.mkv")
        val ops = fake(a, b)
        val plan = Plan(listOf(rename(a, "A.mkv2"), rename(b, "B.mkv2")))
        Executor(ops, MemoryJournal(), ctx).run(plan, setOf("r:a.mkv"), false, "r")
        assertTrue(ops.has("internal", "A.mkv2") && ops.has("internal", "b.mkv"))
    }

    // ------------------------------------------------------------------ volumes and space

    @Test fun movesKeepOneGigabyteFreeOnTheDestinationAndCanBeUndone() {
        val big = ref("big.mkv", size = 2 * GB)
        val ops = fake(big)
        ops.addVolume(usb.copy(free = 3 * GB + 100 * MB))      // 3.1 GB: 2 GB fits but leaves 1.1 GB: ok
        val j = MemoryJournal()
        assertEquals(State.DONE, run(ops, j, move(big, "usb1")).reports.single().state)
        assertTrue(ops.has("usb1", "big.mkv"))
        assertEquals(1, Executor(ops, j, ctx).undo().restored)
        assertTrue(ops.has("internal", "big.mkv"))

        val big2 = ref("big2.mkv", size = 2 * GB)
        val ops2 = fake(big2); ops2.addVolume(usb.copy(free = 2 * GB + 900 * MB))   // would leave 0.9 GB
        val r = run(ops2, MemoryJournal(), move(big2, "usb1"))
        assertEquals(State.SKIPPED, r.reports.single().state)
        assertTrue(r.reports.single().note!!.startsWith("pas assez de place"), r.reports.single().note)
        assertTrue(ops2.has("internal", "big2.mkv"))
    }

    @Test fun movesRefuseAMissingReadOnlyOrFat32Destination() {
        val big = ref("big.mkv", size = 5 * GB)
        val ops = fake(big)
        assertTrue(run(ops, MemoryJournal(), move(big, "usb9")).reports.single().note!!.startsWith("destination absente"))
        ops.addVolume(usb.copy(writable = false))
        assertEquals("destination en lecture seule", run(ops, MemoryJournal(), move(big, "usb1")).reports.single().note)
        ops.addVolume(usb.copy(fs = "FAT32", maxFileBytes = 4 * GB - 1))
        assertTrue(run(ops, MemoryJournal(), move(big, "usb1")).reports.single().note!!.startsWith("fichier trop gros"))
        assertTrue(ops.has("internal", "big.mkv"))
    }

    @Test fun aMoveNeverOverwritesAFileOfTheSameNameOnTheDestination() {
        val a = ref("a.mkv"); val same = ref("a.mkv", vol = "usb1", size = 5 * MB)
        val ops = FakeOps(false, flat = false).also { it.addVolume(internal); it.addVolume(usb); it.add("internal", a.name, a.size); it.add("usb1", same.name, same.size) }
        assertTrue(run(ops, MemoryJournal(), move(a, "usb1")).reports.single().note!!.startsWith("un fichier du même nom"))
        assertEquals(5 * MB, ops.stat(Loc("usb1", "", "a.mkv"))!!.size)
    }

    // ------------------------------------------------------------------ journal, cuts and undo

    @Test fun everyStepIsInTheJournalAndUndoRestoresNamesNewestFirst() {
        val a = ref("a.mkv"); val b = ref("b.mkv")
        val ops = fake(a, b)
        val j = MemoryJournal()
        run(ops, j, rename(a, "A1.mkv"), rename(b, "B1.mkv"))
        assertEquals(2, j.entries().count { it.state == State.DONE })
        val u = Executor(ops, j, ctx).undo()
        assertEquals(2, u.restored)
        assertEquals(listOf("internal:a.mkv", "internal:b.mkv"), ops.paths())
        assertEquals(2, j.entries().count { it.state == State.UNDONE })
        assertEquals(0, Executor(ops, j, ctx).undo().reports.size, "a second undo has nothing left to do")
    }

    @Test fun undoNeverOverwritesWhenTheOriginalNameWasTakenMeanwhile() {
        val a = ref("a.mkv")
        val ops = fake(a)
        val j = MemoryJournal()
        run(ops, j, rename(a, "A1.mkv"))
        ops.add("internal", "a.mkv", 3 * MB)                     // someone created a new a.mkv
        val u = Executor(ops, j, ctx).undo()
        assertEquals(1, u.failed)
        assertTrue(ops.has("internal", "A1.mkv"))
        assertEquals(3 * MB, ops.stat(Loc("internal", "", "a.mkv"))!!.size)
    }

    @Test fun undoSkipsAFilePlayingNow() {
        val a = ref("a.mkv")
        val ops = fake(a)
        val j = MemoryJournal()
        run(ops, j, rename(a, "A1.mkv"))
        ops.playing += "A1.mkv"
        assertEquals(1, Executor(ops, j, ctx).undo().failed)
        assertTrue(ops.has("internal", "A1.mkv"))
    }

    @Test fun aPowerCutAfterTheRenameIsRecoveredAsDone() {
        val a = ref("a.mkv")
        val ops = fake(a); ops.crashAfter = "rename"
        val j = MemoryJournal()
        assertFailsWith<FakeOps.Crash> { run(ops, j, rename(a, "A1.mkv")) }
        assertEquals(State.PENDING, j.entries().single().state, "the intention was written before the operation")
        val rec = Executor(ops, j, ctx).recover()
        assertEquals(State.DONE, rec.single().state)
        assertEquals(State.DONE, j.entries().single().state)
        // and it can be undone
        assertEquals(1, Executor(ops, j, ctx).undo().restored)
        assertTrue(ops.has("internal", "a.mkv"))
    }

    @Test fun aCutBeforeTheOperationLeavesTheFileAloneAndTheJournalHonest() {
        val a = ref("a.mkv")
        val ops = fake(a)
        val j = MemoryJournal()
        j.append(Entry(1, "r", 0, Op.RENAME, "r:a.mkv", a.loc, a.loc.copy(name = "A1.mkv"), State.PENDING, size = a.size))
        val rec = Executor(ops, j, ctx).recover()
        assertEquals(State.FAILED, rec.single().state)
        assertTrue(ops.has("internal", "a.mkv"))
    }

    @Test fun aCutDuringTrashIsRecoveredWithItsTrashId() {
        val a = ref("a.mkv"); val keep = ref("b.mkv")
        val ops = fake(a, keep); ops.crashAfter = "trash"
        val j = MemoryJournal()
        assertFailsWith<FakeOps.Crash> { run(ops, j, trash(a, keep), confirm = true) }
        val rec = Executor(ops, j, ctx).recover()
        assertEquals(State.DONE, rec.single().state)
        assertEquals(1, Executor(ops, j, ctx).undo().restored)
        assertTrue(ops.has("internal", "a.mkv"))
    }

    @Test fun resumeSkipsWhatIsAlreadyDone() {
        val a = ref("a.mkv"); val b = ref("b.mkv")
        val ops = fake(a, b)
        val j = MemoryJournal()
        val plan = Plan(listOf(rename(a, "A1.mkv"), rename(b, "B1.mkv")))
        ops.failOn = { op, l -> if (op == "rename" && l.name == "b.mkv") "disque occupé" else null }
        val ex = Executor(ops, j, ctx)
        val first = ex.run(plan, plan.changes.map { it.id }.toSet(), false, "run")
        assertEquals(1, first.done); assertEquals(1, first.failed)
        ops.failOn = { _, _ -> null }
        val second = ex.run(plan, plan.changes.map { it.id }.toSet(), false, "run")
        assertEquals(2, second.done)
        assertEquals(1, ops.log.count { it == "rename a.mkv -> A1.mkv" }, "the finished step is not redone")
    }

    @Test fun cancellingStopsBetweenSteps() {
        val a = ref("a.mkv"); val b = ref("b.mkv")
        val ops = fake(a, b)
        var n = 0
        val plan = Plan(listOf(rename(a, "A1.mkv"), rename(b, "B1.mkv")))
        Executor(ops, MemoryJournal(), ctx).run(plan, plan.changes.map { it.id }.toSet(), false, "r", cancelled = { n++ >= 1 })
        assertTrue(ops.has("internal", "A1.mkv") && ops.has("internal", "b.mkv"))
    }

    @Test fun fileJournalSurvivesARestartAndATornLastLine() {
        val dir = java.nio.file.Files.createTempDirectory("journal").toFile()
        val file = java.io.File(dir, "journal.jsonl")
        val j = FileJournal(file)
        val e = Entry(j.nextSeq(), "run", System.currentTimeMillis(), Op.RENAME, "r:a", Loc("internal", "", "a.mkv"), Loc("internal", "", "b – é.mkv"), State.PENDING, size = 5)
        j.append(e)
        j.append(e.copy(state = State.DONE, note = "ok"))
        file.appendText("{\"s\":2,\"r\":\"run\",\"t\":1,\"o\":\"REN")           // power cut while writing
        val again = FileJournal(file)
        val got = again.entries().single()
        assertEquals(State.DONE, got.state)
        assertEquals("b – é.mkv", got.to!!.name)
        assertEquals(2, again.nextSeq())
        again.compact()
        assertEquals(1, FileJournal(file).entries().size)
        dir.deleteRecursively()
    }
}
