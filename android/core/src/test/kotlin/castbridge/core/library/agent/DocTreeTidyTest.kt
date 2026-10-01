package castbridge.core.library.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DocTreeTidyTest {
    private val ctx = AgentContext(folders = true, currentYear = 2026)

    @Test fun `undo then prune leaves no empty folder behind but never touches a folder with something in it`() {
        val p = FakeDocProvider(); p.add("Download/Prison.Break.S01E04.720p.mkv", 5L shl 20); p.add("Séries/Autre/garde.txt", 10)
        val lib = DocTreeLibrary(p); val j = MemoryJournal()
        val a = LibraryAgent(ctx).analyze(lib.snapshot())
        Executor(lib.Ops(), j, ctx).run(a.plan, a.plan.allSafe(), false, "phone-1")
        val u = Executor(lib.Ops(), j, ctx).undo("phone-1")
        assertEquals(0, u.failed)
        val folders = j.entries().filter { it.op == Op.MOVE_FOLDER }.mapNotNull { it.to?.folder }
        assertTrue(lib.Ops().pruneEmpty(folders) >= 2)
        assertFalse(p.exists("Séries/Prison Break"))
        assertTrue(p.exists("Séries/Autre/garde.txt"), "a folder with something inside stays")
        assertTrue(p.exists("Download/Prison.Break.S01E04.720p.mkv"))
    }

    @Test fun `a picked folder called Series does not get another Series inside`() {
        val p = FakeDocProvider(); p.add("Narcos.S01E01.720p.mkv", 5L shl 20)
        val lib = DocTreeLibrary(p)
        val a = LibraryAgent(ctx).analyze(lib.snapshot())
        val plan = a.plan.withoutRootSegment("Series")
        assertEquals("Narcos/Saison 01", plan.renames.single().toFolder)
        val r = Executor(lib.Ops(), MemoryJournal(), ctx).run(plan, plan.allSafe(), false, "phone-2")
        assertEquals(1, r.done, r.reports.toString())
        assertTrue(p.exists("Narcos/Saison 01/Narcos – S01E01.mkv"), p.all().toString())
        assertEquals("Séries/Narcos/Saison 01", a.plan.withoutRootSegment("Mes films").renames.single().toFolder, "another name: unchanged")
    }
}
