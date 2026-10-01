package castbridge.core

import castbridge.core.library.agent.AgentRig
import castbridge.core.library.agent.TrashApi
import castbridge.core.net.JsonLite
import castbridge.core.tv.*
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlin.test.*

/** The power cut: not an exception the code may catch, the process is simply gone. */
class PowerCut : Error("simulated power cut")

private fun vol(id: String, dir: File, removable: Boolean) =
    StorageVolume(id, id, dir, if (removable) VolumeKind.REMOVABLE else VolumeKind.INTERNAL, if (removable) Fs.EXFAT else Fs.EXT4, 10L shl 30, 20L shl 30, removable)

/** A store whose writes / reads / removals fail on demand (slow, full, pulled USB key). */
private class FaultStore(v: StorageVolume) : FileStore(v, { 10L shl 30 }) {
    var failWriteAfter = Long.MAX_VALUE
    var failWith: () -> Throwable = { java.io.IOException("No space left on device") }
    var onDeleteFinal: () -> Unit = {}
    var onOpen: (Long) -> Unit = {}
    var corruptOnCommit = false
    override fun openPart(name: String): OutputStream {
        val inner = super.openPart(name)
        var written = 0L
        return object : OutputStream() {
            override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
            override fun write(b: ByteArray, off: Int, len: Int) {
                if (written + len > failWriteAfter) {
                    val ok = (failWriteAfter - written).toInt().coerceAtLeast(0)
                    if (ok > 0) inner.write(b, off, ok)
                    inner.flush(); written += ok
                    throw failWith()
                }
                inner.write(b, off, len); written += len
            }
            override fun flush() = inner.flush()
            override fun close() = inner.close()
        }
    }
    var readDelayMs = 0L
    override fun open(name: String, from: Long): InputStream {
        onOpen(from)
        val inner = super.open(name, from)
        if (readDelayMs == 0L) return inner
        return object : InputStream() {
            override fun read(): Int = inner.read()
            override fun read(b: ByteArray, off: Int, len: Int): Int { Thread.sleep(readDelayMs); return inner.read(b, off, len) }
            override fun close() = inner.close()
        }
    }
    override fun deleteFinal(name: String): Boolean { onDeleteFinal(); return super.deleteFinal(name) }
    override fun commit(name: String) {
        super.commit(name)
        if (corruptOnCommit) File(dir, diskName(name)).let { f -> java.io.RandomAccessFile(f, "rw").use { it.seek(3); it.write(0x55); it.write(0xAA) } }
    }
}

class MoverFaultTest {
    private val root = kotlin.io.path.createTempDirectory("mover").toFile()
    private val srcDir = File(root, "src").apply { mkdirs() }
    private val dstDir = File(root, "dst").apply { mkdirs() }
    private val srcV = vol("int", srcDir, false)
    private val dstV = vol("usb", dstDir, true)
    private val src = FaultStore(srcV)
    private val dst = FaultStore(dstV)
    private val data = Random(7).nextBytes(3_000_000)
    @AfterTest fun tearDown() { root.deleteRecursively() }

    private fun job(name: String = "Film.mkv") = MoveJob(name, srcV, dstV, name, data.size.toLong())
    private fun put() { File(srcDir, "Film.mkv").writeBytes(data) }
    private fun run(j: MoveJob = job(), alive: (StorageVolume) -> Boolean = { true }) = j.also { Mover.run(it, src, dst, alive, bufferBytes = 64 * 1024) }
    private fun stores(id: String): VolumeStore? = when (id) { "int" -> src; "usb" -> dst; else -> null }

    @Test fun aCleanMoveCopiesVerifiesThenRemovesTheSourceAndLeavesNoTrace() {
        put()
        val j = run()
        assertEquals("done", j.state, j.error)
        assertContentEquals(data, File(dstDir, "Film.mkv").readBytes())
        assertFalse(File(srcDir, "Film.mkv").exists())
        assertEquals(listOf("Film.mkv"), dstDir.list()!!.toList(), "no .part, no .meta, no marker left")
    }

    @Test fun aFullDriveFailsCleanlyKeepsTheSourceAndTheCopyResumesWhenRoomIsBack() {
        put()
        dst.failWriteAfter = 1_000_000
        val j = run()
        assertEquals("failed", j.state)
        assertTrue(File(srcDir, "Film.mkv").isFile, "the source is untouched")
        assertFalse(File(dstDir, "Film.mkv").exists(), "nothing half-written is ever visible under the final name")
        assertEquals(1_000_000L, File(dstDir, "Film.mkv.part").length())
        // room is back: the copy resumes from 1 MB, not from zero
        dst.failWriteAfter = Long.MAX_VALUE
        var firstOffset = -1L
        src.onOpen = { if (firstOffset < 0) firstOffset = it }
        val j2 = run()
        assertEquals("done", j2.state, j2.error)
        assertEquals(1_000_000L, firstOffset, "resumed where it stopped")
        assertContentEquals(data, File(dstDir, "Film.mkv").readBytes())
    }

    @Test fun aPowerCutDuringTheCopyLeavesTheSourceWholeAndTheNextRunResumes() {
        put()
        dst.failWriteAfter = 1_500_000
        dst.failWith = { PowerCut() }
        assertFailsWith<PowerCut> { run() }
        assertEquals(data.size.toLong(), File(srcDir, "Film.mkv").length())
        assertFalse(File(dstDir, "Film.mkv").exists())
        assertEquals("copying", MoveMarker.read(dstDir)?.let { if (it.verified) "verified" else "copying" })
        // restart: the recovery decides nothing (the copy was not verified), the move resumes
        assertNull(Mover.recover(dst, ::stores))
        assertTrue(File(srcDir, "Film.mkv").isFile && File(dstDir, "Film.mkv.part").isFile)
        dst.failWriteAfter = Long.MAX_VALUE
        val j = run()
        assertEquals("done", j.state, j.error)
        assertContentEquals(data, File(dstDir, "Film.mkv").readBytes())
        assertFalse(File(srcDir, "Film.mkv").exists())
    }

    @Test fun aPowerCutAfterTheVerifiedCopyButBeforeTheSourceRemovalIsFinishedByTheRecovery() {
        put()
        src.onDeleteFinal = { throw PowerCut() }
        assertFailsWith<PowerCut> { run() }
        assertTrue(File(srcDir, "Film.mkv").isFile && File(dstDir, "Film.mkv").isFile, "both exist: nothing lost")
        assertTrue(MoveMarker.read(dstDir)!!.verified)
        src.onDeleteFinal = {}
        val msg = Mover.recover(dst, ::stores)
        assertNotNull(msg); assertTrue(msg.contains("terminé après une coupure"))
        assertFalse(File(srcDir, "Film.mkv").exists())
        assertContentEquals(data, File(dstDir, "Film.mkv").readBytes())
        assertNull(MoveMarker.read(dstDir))
    }

    @Test fun theRecoveryNeverDeletesAFileItDidNotVerify() {
        put()
        File(dstDir, "Film.mkv").writeBytes(data)                              // the user's own identical copy, no verified marker
        assertNull(Mover.recover(dst, ::stores))
        assertTrue(File(srcDir, "Film.mkv").isFile)
        // an unverified ("copying") marker with both files present: still nothing deleted
        MoveMarker.write(dstDir, MoveMarker.Marker("Film.mkv", "Film.mkv", "int", data.size.toLong(), verified = false))
        assertNull(Mover.recover(dst, ::stores))
        assertTrue(File(srcDir, "Film.mkv").isFile && File(dstDir, "Film.mkv").isFile)
        // a verified marker but the source differs from the copy: nothing deleted, marker dropped
        File(srcDir, "Film.mkv").writeBytes(Random(9).nextBytes(data.size))
        MoveMarker.write(dstDir, MoveMarker.Marker("Film.mkv", "Film.mkv", "int", data.size.toLong(), verified = true))
        assertNull(Mover.recover(dst, ::stores))
        assertTrue(File(srcDir, "Film.mkv").isFile)
        assertNull(MoveMarker.read(dstDir))
    }

    @Test fun theKeyIsNotThereYetTheRecoveryWaits() {
        put()
        src.onDeleteFinal = { throw PowerCut() }
        assertFailsWith<PowerCut> { run() }
        assertNull(Mover.recover(dst) { null })
        assertNotNull(MoveMarker.read(dstDir), "the marker stays until the source volume is back")
        assertTrue(File(srcDir, "Film.mkv").isFile)
    }

    @Test fun aSourceModifiedWhileCopyingIsNeverCommitted() {
        put()
        var reads = 0
        src.onOpen = { reads++ }
        dst.failWriteAfter = Long.MAX_VALUE
        src.readDelayMs = 1
        val j = job()
        // the file is rewritten (same size, new content and time) right in the middle of the copy
        val t = Thread { while (j.done < 500_000) Thread.sleep(1); File(srcDir, "Film.mkv").let { f -> java.io.RandomAccessFile(f, "rw").use { it.seek(10); it.write(Random(3).nextBytes(100)) }; f.setLastModified(System.currentTimeMillis() + 5_000) } }
        // slow the copy down enough for the writer to act: a tiny buffer
        t.start(); Mover.run(j, src, dst, { true }, bufferBytes = 4096); t.join()
        assertEquals("failed", j.state)
        assertTrue(j.error!!.contains("source changed"), j.error)
        assertFalse(File(dstDir, "Film.mkv").exists())
        assertTrue(File(srcDir, "Film.mkv").isFile)
    }

    @Test fun aCopyThatDiffersFromTheSourceIsRemovedAndTheSourceKept() {
        put()
        dst.corruptOnCommit = true
        val j = run()
        assertEquals("failed", j.state)
        assertTrue(j.error!!.contains("verification failed"), j.error)
        assertFalse(File(dstDir, "Film.mkv").exists(), "a bad copy is not left behind")
        assertContentEquals(data, File(srcDir, "Film.mkv").readBytes())
    }

    @Test fun aPulledKeyStopsTheCopyAndItResumesWhenTheKeyIsBack() {
        put()
        var seen = 0
        val j = job()
        Mover.run(j, src, dst, { v -> if (v.id == "usb" && ++seen > 20) false else true }, bufferBytes = 64 * 1024)
        assertEquals("failed", j.state)
        assertTrue(j.error!!.contains("volume removed"))
        assertTrue(File(srcDir, "Film.mkv").isFile)
        val part = File(dstDir, "Film.mkv.part").length()
        assertTrue(part in 1 until data.size)
        val j2 = run()
        assertEquals("done", j2.state, j2.error)
        assertContentEquals(data, File(dstDir, "Film.mkv").readBytes())
    }

    @Test fun aPartialCopyOfAnotherFileSizeIsNotMixedIn() {
        put()
        File(dstDir, "Film.mkv.part").writeBytes(ByteArray(900_000) { 1 })
        File(dstDir, "Film.mkv.meta").writeText("12345")                         // belongs to a different source size
        val j = run()
        assertEquals("done", j.state, j.error)
        assertContentEquals(data, File(dstDir, "Film.mkv").readBytes())
    }

    @Test fun renamingNeverOverwritesAnExistingFileOnTheStore() {
        File(srcDir, "a.mp4").writeBytes(byteArrayOf(1)); File(srcDir, "b.mp4").writeBytes(byteArrayOf(2))
        assertFalse(src.rename("a.mp4", "b.mp4"))
        assertEquals(1, File(srcDir, "a.mp4").readBytes()[0].toInt())
        assertEquals(2, File(srcDir, "b.mp4").readBytes()[0].toInt())
        assertFalse(src.rename("zzz.mp4", "c.mp4"))
        assertTrue(src.rename("a.mp4", "c.mp4"))
        // a case-only change on an exFAT key is allowed (same entry)
        File(dstDir, "film.mkv").writeBytes(byteArrayOf(3))
        assertTrue(dst.rename("film.mkv", "Film.mkv"))
        assertTrue(dstDir.list()!!.contains("Film.mkv"))
    }
}

class TvHardeningRouteTest {
    private val r = AgentRig()
    @AfterTest fun tearDown() = r.close()

    private fun err(body: String) = JsonLite.obj(body)["error"]

    @Test fun theAssistantRenameRefusesAPlayingVideoWhileTheOldRenameStillStopsIt() {
        r.put("internal", "Film.mkv", 20_000)
        r.player.st = PlayerState("playing", "Film.mkv", 0, 1000)
        val (c, b) = r.call("POST", "/api/rename?name=Film.mkv&to=Autre.mkv&safe=1")
        assertEquals(409, c); assertEquals("playing", err(b))
        assertEquals("playing", r.player.state().state, "the video keeps playing")
        assertTrue(File(r.internalDir, "Film.mkv").isFile)
        // old clients (the TV's own screen, the web page): unchanged behaviour
        assertEquals(200, r.call("POST", "/api/rename?name=Film.mkv&to=Autre.mkv").first)
        assertEquals("idle", r.player.state().state)
    }

    @Test fun nothingIsRenamedOrBinnedUnderAReaderOrAnUpload() {
        File(r.internalDir, "Big.mkv").also { java.io.RandomAccessFile(it, "rw").use { f -> f.setLength(60L shl 20) } }
        val c = URL(r.base + "/stream/Big.mkv").openConnection() as HttpURLConnection
        c.setRequestProperty("X-CB-Pin", "123456")
        val reading = c.inputStream
        reading.read(ByteArray(1000))
        assertEquals("streaming", err(r.call("POST", "/api/rename?name=Big.mkv&to=B2.mkv").second))
        assertEquals(409, r.call("POST", "/api/rename?name=Big.mkv&to=B2.mkv").first)
        assertEquals(409, r.call("POST", "/api/trash/put?name=Big.mkv").first)
        assertEquals(409, r.call("POST", "/api/storage/move?name=Big.mkv&to=usb-1234").first)
        assertTrue(File(r.internalDir, "Big.mkv").isFile)
        reading.close(); c.disconnect()
        var done = false
        repeat(60) { if (!done) { done = r.call("POST", "/api/rename?name=Big.mkv&to=B2.mkv").first == 200; if (!done) Thread.sleep(100) } }
        assertTrue(done, "free again once the reader is gone")
        // an upload in progress (a partial copy of the same name on a volume) blocks too
        r.put("internal", "Up.mkv", 5_000)
        File(r.usbDir, "Up.mkv.part").writeBytes(ByteArray(10)); File(r.usbDir, "Up.mkv.meta").writeText("99999")
        assertEquals("uploading", err(r.call("POST", "/api/rename?name=Up.mkv&to=Up2.mkv&safe=1").second))
        assertEquals("uploading", err(r.call("POST", "/api/trash/put?name=Up.mkv").second))
    }

    @Test fun twoRenamesToTheSameNameNeverLoseAFile() {
        repeat(25) { round ->
            r.put("internal", "a$round.mp4", 1000, seed = 1); r.put("internal", "b$round.mp4", 1000, seed = 2)
            val start = CountDownLatch(1)
            val codes = java.util.Collections.synchronizedList(ArrayList<Int>())
            val ts = listOf("a$round.mp4", "b$round.mp4").map { from -> Thread { start.await(); codes += r.call("POST", "/api/rename?name=$from&to=T$round.mp4").first }.also { it.start() } }
            start.countDown(); ts.forEach { it.join() }
            assertEquals(listOf(200, 409), codes.sorted(), "exactly one wins")
            val names = r.listing().filter { it.endsWith("$round.mp4") }
            assertEquals(2, names.size, "both files are still there: $names")
            assertTrue(names.contains("T$round.mp4"))
        }
    }

    @Test fun aCaseOnlyRenameWorks() {
        r.put("usb-1234".let { "usb" }, "film.mkv", 3000)
        assertEquals(200, r.call("POST", "/api/rename?name=film.mkv&to=Film.mkv").first)
        assertTrue(r.listing().contains("Film.mkv"))
    }

    @Test fun restoreFromTheBinNeverOverwritesEvenWhenTheNameWasTakenMeanwhile() {
        r.put("internal", "Film.mkv", 5000, seed = 1)
        val id = JsonLite.obj(r.call("POST", "/api/trash/put?name=Film.mkv").second)["id"] as String
        r.put("usb", "Film.mkv", 7000, seed = 2)                                    // a new file took the name
        val rs = JsonLite.obj(r.call("POST", "/api/trash/restore?id=$id").second)
        assertEquals("Film (restauré).mkv", rs["name"])
        assertEquals(7000L, File(r.usbDir, "Film.mkv").length())
        assertEquals(5000L, File(r.internalDir, "Film (restauré).mkv").length())
    }

    @Test fun theTvMoveKeepsOneGigabyteFreeLikeAnUpload() {
        // a real TV profile: 1 GB must remain after the transfer
        val rig2 = TwoVolumeRig(TvProfile())
        try {
            rig2.internal("Big.mkv", 300L shl 20)
            rig2.capacity["usb"] = (1L shl 30) + (200L shl 20)                       // 1.2 GB: only 0.9 GB would remain
            rig2.registry.refresh()
            val (c, b) = rig2.call("POST", "/api/storage/move?name=Big.mkv&to=usb")
            assertEquals(507, c, b)
            assertTrue(File(rig2.internalDir, "Big.mkv").isFile)
            rig2.capacity["usb"] = (1L shl 30) + (400L shl 20)                       // 1.4 GB: 1.1 GB remains
            rig2.registry.refresh()
            assertEquals(200, rig2.call("POST", "/api/storage/move?name=Big.mkv&to=usb").first)
        } finally { rig2.close() }
    }
}

/** Two folders (internal + USB exFAT) and a profile of your choice (the shared rig turns the space rules off). */
class TwoVolumeRig(profile: TvProfile) {
    val root = kotlin.io.path.createTempDirectory("two").toFile()
    val internalDir = File(root, "i").apply { mkdirs() }
    val usbDir = File(root, "u").apply { mkdirs() }
    val capacity = mutableMapOf<String, Long>()
    private fun used(d: File) = d.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    val registry = VolumeRegistry(StaticVolumes {
        listOf(StorageVolume("internal", "Mémoire interne", internalDir, VolumeKind.INTERNAL, Fs.UNKNOWN, 0, 0, false),
            StorageVolume("usb", "Clé USB", usbDir, VolumeKind.REMOVABLE, Fs.EXFAT, 0, 0, true, true, 0))
    }) { v -> capacity[v.id]?.let { it - used(v.dir) } ?: (50L shl 30) }.also { it.refresh() }
    private val port = java.net.ServerSocket(0).use { it.localPort }
    private val server = ReceiverServer(registry, FakePlayer(), port, profile = profile, pin = "123456", guard = PinGuard("123456", maxFailures = 1000)).apply { start(5000, false) }
    private val base = "http://127.0.0.1:$port"
    fun internal(name: String, size: Long) { java.io.RandomAccessFile(File(internalDir, name), "rw").use { it.setLength(size) } }
    fun call(method: String, path: String): Pair<Int, String> {
        val c = URL(base + path).openConnection() as HttpURLConnection
        c.requestMethod = method; c.setRequestProperty("X-CB-Pin", "123456")
        if (method == "POST") { c.doOutput = true; c.setFixedLengthStreamingMode(0); c.outputStream.close() }
        val code = c.responseCode
        return code to ((if (code < 400) c.inputStream else c.errorStream)?.readBytes()?.decodeToString() ?: "")
    }
    fun close() { server.stop(); root.deleteRecursively() }
}
