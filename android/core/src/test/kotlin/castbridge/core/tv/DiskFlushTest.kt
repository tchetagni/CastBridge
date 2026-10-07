package castbridge.core.tv

import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * « Écritures de CastBridge-TV » (2026-10-07, docs/STORAGE.md § « Clé USB mal éjectée ») : un fichier écrit sur une clé est fermé avec `fsync` EN FIN DE FICHIER (jamais un fsync par bloc : R-20 l'a
 * retiré) et le commit final demande un `sync` du système, au mieux (les entrées de dossier et les tables d'allocation qu'un fsync par fichier laisse rattraper au noyau).
 */
class DiskFlushTest {
    private val dirs = ArrayList<File>()
    private fun tmp(): File = kotlin.io.path.createTempDirectory("flush").toFile().also { dirs += it }
    @AfterTest fun clean() { dirs.forEach { it.deleteRecursively() } }

    private class CountingSync(val onSoon: () -> Unit = {}) : SystemSync {
        val soons = AtomicInteger(); val nows = AtomicInteger(); var nowResult = true
        override fun now(timeoutMs: Long): Boolean { nows.incrementAndGet(); return nowResult }
        override fun soon() { soons.incrementAndGet(); onSoon() }
    }

    private fun volume(dir: File, kind: VolumeKind) = StorageVolume(if (kind == VolumeKind.INTERNAL) "internal" else "usb-1", "Clé", dir, kind, Fs.EXFAT, 0, 0, kind != VolumeKind.INTERNAL)

    // ---- the system `sync`, asked soon and served once ----

    @Test fun `requests close together are served by one flush`() {
        val queue = ArrayList<Runnable>()
        var execs = 0
        val s = ShellSync(exec = { execs++; true }, minGapMs = 0, sleep = {}, spawn = { queue += it })
        repeat(5) { s.soon() }
        assertEquals(1, queue.size, "one worker, however many requests")
        assertEquals(0, execs, "nothing runs on the caller's thread")
        queue[0].run()
        assertEquals(1, execs, "five requests, one flush")
        s.soon()
        assertEquals(2, queue.size, "the worker ended: a later request starts another one")
        queue[1].run()
        assertEquals(2, execs)
    }

    @Test fun `a request made during a flush is served by another flush, never lost`() {
        val queue = ArrayList<Runnable>()
        var execs = 0
        lateinit var s: ShellSync
        s = ShellSync(exec = { execs++; if (execs == 1) s.soon(); true }, minGapMs = 0, sleep = {}, spawn = { queue += it })
        s.soon(); queue[0].run()
        assertEquals(2, execs, "what was written while the first flush ran may not be covered by it")
        assertEquals(1, queue.size, "served by the same worker")
    }

    @Test fun `flushes are spaced by the minimum gap`() {
        var t = 10_000L
        val slept = ArrayList<Long>()
        val queue = ArrayList<Runnable>()
        val s = ShellSync(exec = { true }, minGapMs = 3_000, clock = { t }, sleep = { slept += it; t += it }, spawn = { queue += it })
        s.now()                                          // ends at t = 10 000
        t += 500
        s.soon(); queue[0].run()
        assertEquals(listOf(2_500L), slept, "3 s since the end of the last flush, not since the request")
        slept.clear(); t += 10_000
        s.soon(); queue[1].run()
        assertEquals(emptyList(), slept, "long ago: no wait")
    }

    @Test fun `a command that fails or throws is a false, never an exception`() {
        assertFalse(ShellSync(exec = { false }).now())
        assertFalse(ShellSync(exec = { throw java.io.IOException("no sync here") }).now())
        val queue = ArrayList<Runnable>()
        val s = ShellSync(exec = { throw IllegalStateException() }, minGapMs = 0, sleep = {}, spawn = { queue += it })
        s.soon(); queue[0].run()                         // the worker survives and ends
        s.soon(); assertEquals(2, queue.size)
    }

    @Test fun `the none flusher does nothing and says so`() {
        assertFalse(SystemSync.NONE.now())
        SystemSync.NONE.soon()
    }

    @Test fun `the real command runs where the system has one`() {
        org.junit.Assume.assumeTrue(File("/bin/sync").exists() || File("/usr/bin/sync").exists())
        assertTrue(ShellSync.runSync(30_000), "sync exits with 0")
    }

    // ---- fsync of files ----

    @Test fun `a file is synced by its path, a missing one is a false`() {
        val d = tmp()
        val f = File(d, "a.bin").apply { writeBytes(ByteArray(10)) }
        assertTrue(DiskFlush.file(f))
        assertFalse(DiskFlush.file(File(d, "missing.bin")))
    }

    @Test fun `the partial files of a volume are found and synced, finished files are not touched`() {
        val d = tmp()
        File(d, "a.mp4.part").writeBytes(ByteArray(5)); File(d, "b.mkv.part").writeBytes(ByteArray(5))
        File(d, ".cbx").mkdirs(); File(d, ".cbx/id1.data").writeBytes(ByteArray(5)); File(d, ".cbx/id1.state").writeText("CBX1")
        File(d, "done.mp4").writeBytes(ByteArray(5))
        File(d, "Films").mkdirs(); File(d, "Films/filed.part").writeBytes(ByteArray(5))
        val r = DiskFlush.partials(d)
        assertEquals(4, r.count, "two .part files and the two files of the unfinished multi-connection transfer")
        assertTrue(r.allOk)
        assertEquals(0, DiskFlush.partials(tmp()).count)
        assertTrue(DiskFlush.partials(tmp()).allOk, "nothing to flush is a success")
        assertEquals(0, DiskFlush.partials(File(d, "nope")).count)
    }

    // ---- the store: end of file only ----

    @Test fun `the final commit on a removable volume asks for a system flush AFTER the rename`() {
        val d = tmp()
        var existedAtSoon = false
        val sync = CountingSync(onSoon = { existedAtSoon = File(d, "a.mp4").isFile && !File(d, "a.mp4.part").exists() })
        val st = FileStore(volume(d, VolumeKind.REMOVABLE), sync = sync)
        st.openPart("a.mp4").use { it.write(ByteArray(1000)) }
        assertEquals(0, sync.soons.get(), "nothing before the end of the file")
        st.commit("a.mp4")
        assertEquals(1, sync.soons.get())
        assertTrue(existedAtSoon, "the flush covers the renamed entry: it is asked once the final name is in place")
        assertEquals(1000L, File(d, "a.mp4").length())
    }

    @Test fun `internal storage never asks for a system flush`() {
        val d = tmp()
        val sync = CountingSync()
        val st = FileStore(volume(d, VolumeKind.INTERNAL), sync = sync)
        st.openPart("a.mp4").use { it.write(ByteArray(10)) }
        st.commit("a.mp4")
        assertEquals(0, sync.soons.get())
    }

    @Test fun `many blocks cost no flush at all, no fsync per block (R-20)`() {
        val d = tmp()
        val sync = CountingSync()
        val st = FileStore(volume(d, VolumeKind.REMOVABLE), sync = sync)
        st.openPart("big.mkv").use { o -> repeat(500) { o.write(ByteArray(64 * 1024)) } }
        assertEquals(0, sync.soons.get())
        assertEquals(0, sync.nows.get())
        st.commit("big.mkv")
        assertEquals(1, sync.soons.get(), "one at the end of the file")
        assertEquals(500L * 64 * 1024, File(d, "big.mkv").length())
    }

    @Test fun `a commit that fails asks for nothing`() {
        val d = tmp()
        val sync = CountingSync()
        val st = FileStore(volume(File(d, "gone/sub"), VolumeKind.REMOVABLE), sync = sync)
        try { st.commit("a.mp4") } catch (e: java.io.IOException) { /* expected: no partial file, the folder is gone */ }
        assertEquals(0, sync.soons.get())
    }

    @Test fun `flushing a store syncs every partial file and asks the system once`() {
        val d = tmp()
        File(d, "a.mp4.part").writeBytes(ByteArray(5))
        File(d, ".cbx").mkdirs(); File(d, ".cbx/id.data").writeBytes(ByteArray(5))
        val sync = CountingSync()
        val st = FileStore(volume(d, VolumeKind.REMOVABLE), sync = sync)
        assertTrue(st.flushAll())
        assertEquals(1, sync.nows.get())
        assertEquals(0, sync.soons.get(), "the preparation waits for the flush, it does not just ask for one")
    }

    @Test fun `a system sync that is not available does not make the flush fail, the files are synced`() {
        val d = tmp()
        File(d, "a.mp4.part").writeBytes(ByteArray(5))
        val sync = CountingSync().apply { nowResult = false }
        assertTrue(FileStore(volume(d, VolumeKind.REMOVABLE), sync = sync).flushAll(), "best effort: the fsync of each file is what is certain")
        assertTrue(FileStore(volume(d, VolumeKind.REMOVABLE)).flushAll(), "no sync at all: same")
    }

    @Test fun `a folder that is gone is not flushed, it is reported`() {
        val st = FileStore(volume(File(tmp(), "missing"), VolumeKind.REMOVABLE), sync = CountingSync())
        assertFalse(st.flushAll(), "the medium is not there: nothing can be said to have reached it")
    }

    @Test fun `a store that cannot guarantee anything says false by default`() {
        val none = object : VolumeStore {
            override val volume = volume(tmp(), VolumeKind.SAF)
            override fun freeBytes() = 0L; override fun finalSize(name: String): Long? = null; override fun partSize(name: String) = 0L
            override fun openPart(name: String) = java.io.ByteArrayOutputStream(); override fun commit(name: String) {}
            override fun open(name: String, from: Long) = java.io.ByteArrayInputStream(ByteArray(0)); override fun deleteFinal(name: String) = false
            override fun deletePart(name: String) {}; override fun rename(from: String, to: String) = false; override fun list() = emptyList<StoreEntry>()
            override val progressive get() = false
        }
        assertFalse(none.flushAll())
    }
}
