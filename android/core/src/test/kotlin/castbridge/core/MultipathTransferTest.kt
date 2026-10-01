package castbridge.core

import castbridge.core.tv.*
import castbridge.core.xfer.*
import java.io.ByteArrayInputStream
import java.io.File
import java.net.ServerSocket
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random
import kotlin.test.*

private const val MiB = 1L shl 20
private const val GiB = 1L shl 30

private fun gz(b: ByteArray): ByteArray = java.io.ByteArrayOutputStream().also { o -> java.util.zip.GZIPOutputStream(o).use { it.write(b) } }.toByteArray()
private fun sha(b: ByteArray, off: Int = 0, len: Int = b.size) = Hash.hex(java.security.MessageDigest.getInstance("SHA-256").also { it.update(b, off, len) }.digest())

class ManifestTest {
    @Test fun blocksAndOffsetsAboveFourGiB() {
        val m = Manifest("film.mkv", 5 * GiB + 123, 8 * MiB.toInt())
        assertEquals(641, m.blocks)
        assertEquals(640L * 8 * MiB, m.offset(640)); assertTrue(m.offset(640) > 4 * GiB)
        assertEquals(5 * GiB + 123 - 640L * 8 * MiB, m.length(640).toLong())
        assertEquals((0 until m.blocks).sumOf { m.length(it).toLong() }, m.size)
    }
    @Test fun emptyFileHasNoBlocksAndAKnownRoot() {
        val m = Manifest.of("vide.txt", 0)
        assertEquals(0, m.blocks)
        assertEquals(sha(ByteArray(0)), Manifest.root(emptyList()))
    }
    @Test fun blockSizeGrowsWithTheFile() {
        assertEquals(1 * MiB.toInt(), Manifest.blockSizeFor(10 * MiB)); assertEquals(4 * MiB.toInt(), Manifest.blockSizeFor(500 * MiB)); assertEquals(8 * MiB.toInt(), Manifest.blockSizeFor(4 * GiB))
    }
    @Test fun idIsStableAndSecretFree() {
        val a = Manifest("a.mp4", 100, 1 shl 20); assertEquals(a.id, Manifest("a.mp4", 100, 1 shl 20).id); assertNotEquals(a.id, Manifest("a.mp4", 101, 1 shl 20).id)
        assertEquals(24, a.id.length); assertTrue(a.id.all { it in "0123456789abcdef" })
    }
    @Test fun blockMapRoundTripsAsHex() {
        val m = BlockMap(19); listOf(0, 3, 8, 18).forEach { m.set(it) }
        val r = BlockMap.fromHex(19, m.toHex())
        assertEquals(listOf(0, 3, 8, 18), (0 until 19).filter { r.has(it) }); assertEquals(15, r.missing().size)
        assertEquals(0, BlockMap.fromHex(19, "zz").count(), "garbage means nothing is done")
    }
    @Test fun noCompressionForMediaButForText() {
        assertFalse(Compression.worthTrying("film.MKV")); assertFalse(Compression.worthTrying("a.zip")); assertFalse(Compression.worthTrying("photo.jpg"))
        assertTrue(Compression.worthTrying("notes.txt")); assertTrue(Compression.worthTrying("x.json")); assertFalse(Compression.worthTrying("inconnu.xyz"))
    }
}

class KControllerTest {
    @Test fun climbsWhileItScalesThenSettlesOnTheBest() {
        val c = KController(1, 8, 2)
        // 4 connections' worth of Wi-Fi: rate = 5 MB/s per connection up to 4, flat after
        fun rate(k: Int) = minOf(k, 4) * 5e6
        var k = c.k
        repeat(12) { k = c.onSample(rate(k)) }
        assertTrue(k in 4..5, "no point in more than the 4 that help (5 only while probing): $k")
    }
    @Test fun backsOffWhenTheTvSaysBusyAndNeverGoesBelowOne() {
        val c = KController(1, 8, 4); c.onBusy(); assertEquals(3, c.k); repeat(10) { c.onBusy() }; assertEquals(1, c.k)
    }
    @Test fun probesUpwardsAgainAfterAHold() {
        val c = KController(1, 8, 2, holdWindows = 3)
        var k = c.k
        repeat(3) { k = c.onSample(10e6) }       // flat from the start: stays at the best (2)
        assertEquals(2, k)
        repeat(4) { k = c.onSample(10e6) }
        assertTrue(k in 2..3)
    }
    @Test fun maxFollowsWhatTheTvAllows() { val c = KController(1, 8, 6); c.limit(3); assertEquals(3, c.k) }
}

class PartAssemblerTest {
    private val dir = kotlin.io.path.createTempDirectory("asm").toFile()
    @AfterTest fun tearDown() { dir.deleteRecursively() }
    private val rnd = Random(7)
    private fun blockOf(m: Manifest, data: ByteArray, i: Int) = data.copyOfRange(m.offset(i).toInt(), m.offset(i).toInt() + m.length(i))

    private fun file(size: Int): Pair<Manifest, ByteArray> { val d = rnd.nextBytes(size); return Manifest("clip.bin", size.toLong(), 1 shl 20) to d }
    private fun put(a: PartAssembler, m: Manifest, d: ByteArray, i: Int) = blockOf(m, d, i).let { b -> a.writeBlock(i, sha(b), ByteArrayInputStream(b), b.size.toLong(), false) }

    @Test fun blocksInAnyOrderThenFinishRenamesAtomically() {
        val (m, d) = file(3 * MiB.toInt() + 777)
        val a = PartAssembler.open(dir, m)
        listOf(3, 1, 0, 2).forEach { assertTrue(put(a, m, d, it) is PartAssembler.Block.Ok) }
        val hashes = (0 until m.blocks).map { sha(blockOf(m, d, it)) }
        val f = a.finish(Manifest.root(hashes), "clip.bin")
        assertTrue(f is PartAssembler.Finish.Done, f.toString())
        val part = File(dir, "clip.bin.part"); assertTrue(part.isFile); assertContentEquals(d, part.readBytes())
        assertFalse(File(dir, ".cbx/${m.id}.data").exists(), "the data file was renamed, not copied")
        assertFalse(File(dir, ".cbx/${m.id}.state").exists())
    }
    @Test fun aCorruptBlockIsRefusedAndNeverCounted() {
        val (m, d) = file(2 * MiB.toInt())
        val a = PartAssembler.open(dir, m)
        val b = blockOf(m, d, 0); val bad = b.clone().also { it[100] = (it[100] + 1).toByte() }
        assertTrue(a.writeBlock(0, sha(b), ByteArrayInputStream(bad), bad.size.toLong(), false) is PartAssembler.Block.Corrupt)
        assertFalse(a.map.has(0))
        assertTrue(put(a, m, d, 0) is PartAssembler.Block.Ok, "the good copy overwrites the bad bytes"); put(a, m, d, 1)
        val f = a.finish(Manifest.root((0 until 2).map { sha(blockOf(m, d, it)) }), "clip.bin")
        assertTrue(f is PartAssembler.Finish.Done); assertContentEquals(d, File(dir, "clip.bin.part").readBytes())
    }
    @Test fun wrongLengthShortBodyAndBadIndexAreRefused() {
        val (m, d) = file(MiB.toInt() + 10)
        val a = PartAssembler.open(dir, m); val b = blockOf(m, d, 0)
        assertTrue(a.writeBlock(0, sha(b), ByteArrayInputStream(b), b.size - 1L, false) is PartAssembler.Block.Bad)
        assertTrue(a.writeBlock(0, sha(b), ByteArrayInputStream(b.copyOf(100)), b.size.toLong(), false) is PartAssembler.Block.Bad)
        assertTrue(a.writeBlock(9, sha(b), ByteArrayInputStream(b), b.size.toLong(), false) is PartAssembler.Block.Bad)
        assertTrue(a.writeBlock(0, "nothex", ByteArrayInputStream(b), b.size.toLong(), false) is PartAssembler.Block.Bad)
        assertEquals(0, a.map.count())
    }
    @Test fun aBlockAlreadyHeldIsNotWrittenTwice() {
        val (m, d) = file(MiB.toInt()); val a = PartAssembler.open(dir, m)
        assertTrue(put(a, m, d, 0) is PartAssembler.Block.Ok); assertTrue(put(a, m, d, 0) is PartAssembler.Block.Already)
    }
    @Test fun finishListsMissingBlocksAndChecksTheRoot() {
        val (m, d) = file(2 * MiB.toInt()); val a = PartAssembler.open(dir, m)
        put(a, m, d, 1)
        assertEquals(listOf(0), (a.finish("x", "clip.bin") as PartAssembler.Finish.Missing).blocks)
        put(a, m, d, 0)
        assertTrue(a.finish(sha(ByteArray(3)), "clip.bin") is PartAssembler.Finish.RootMismatch)
    }
    @Test fun restartKeepsTheBlocksAlreadyHeld() {
        val (m, d) = file(4 * MiB.toInt()); var a = PartAssembler.open(dir, m)
        put(a, m, d, 2); put(a, m, d, 0); a.close()                 // the app is killed: the sidecar was written at close
        a = PartAssembler.open(dir, m)
        assertEquals(listOf(1, 3), a.map.missing())
        put(a, m, d, 1); put(a, m, d, 3)
        assertTrue(a.finish(Manifest.root((0 until 4).map { sha(blockOf(m, d, it)) }), "clip.bin") is PartAssembler.Finish.Done)
        assertContentEquals(d, File(dir, "clip.bin.part").readBytes())
    }
    @Test fun diskCorruptionBetweenReceiptAndFinishIsCaughtByTheReadBack() {
        val (m, d) = file(2 * MiB.toInt()); val a = PartAssembler.open(dir, m)
        put(a, m, d, 0); put(a, m, d, 1)
        java.io.RandomAccessFile(File(dir, ".cbx/${m.id}.data"), "rw").use { it.seek(MiB + 5); it.write(0x55); it.write(0x66) }   // a bad sector
        val f = a.finish(Manifest.root((0 until 2).map { sha(blockOf(m, d, it)) }), "clip.bin")
        assertEquals(listOf(1), (f as PartAssembler.Finish.Corrupt).blocks); assertEquals(listOf(1), a.map.missing())
        assertFalse(File(dir, "clip.bin.part").exists(), "never renamed while wrong")
    }
    @Test fun slicesAssembleABlockAndTheWholeBlockIsVerified() {
        val (m, d) = file(MiB.toInt()); val a = PartAssembler.open(dir, m); val b = blockOf(m, d, 0); val h = sha(b)
        val S = Manifest.SLICE
        assertEquals(4, m.slices(0))
        for (k in listOf(2, 0, 3)) assertTrue(a.writeSlice(0, k, h, ByteArrayInputStream(b, k * S, S), S.toLong()) is PartAssembler.Block.Ok)
        assertFalse(a.map.has(0)); assertEquals(listOf(0, 2, 3), a.slicesOf(0))
        assertTrue(a.writeSlice(0, 1, h, ByteArrayInputStream(b, S, S), S.toLong()) is PartAssembler.Block.Ok)
        assertTrue(a.map.has(0))
        assertTrue(a.finish(Manifest.root(listOf(h)), "clip.bin") is PartAssembler.Finish.Done); assertContentEquals(d, File(dir, "clip.bin.part").readBytes())
    }
    @Test fun aCorruptSliceMakesTheBlockFailAndStartOver() {
        val (m, d) = file(MiB.toInt()); val a = PartAssembler.open(dir, m); val b = blockOf(m, d, 0); val h = sha(b); val S = Manifest.SLICE
        for (k in 0 until 3) a.writeSlice(0, k, h, ByteArrayInputStream(b, k * S, S), S.toLong())
        val bad = b.copyOfRange(3 * S, 4 * S).also { it[0] = (it[0] + 1).toByte() }
        assertTrue(a.writeSlice(0, 3, h, ByteArrayInputStream(bad), S.toLong()) is PartAssembler.Block.Corrupt)
        assertFalse(a.map.has(0)); assertEquals(emptyList(), a.slicesOf(0))
    }
    @Test fun gzipBodyIsInflatedAndVerifiedAgainstTheRawHash() {
        val text = "ligne de sous-titre répétée\n".repeat(30_000).toByteArray()
        val m = Manifest("sous.srt", text.size.toLong(), 1 shl 20); val a = PartAssembler.open(dir, m)
        val raw = blockOf(m, text, 0); val z = gz(raw)
        assertTrue(z.size < raw.size / 4)
        assertTrue(a.writeBlock(0, sha(raw), ByteArrayInputStream(z), z.size.toLong(), true) is PartAssembler.Block.Ok)
        assertTrue(a.finish(Manifest.root(listOf(sha(raw))), "sous.srt") is PartAssembler.Finish.Done); assertContentEquals(text, File(dir, "sous.srt.part").readBytes())
    }
    @Test fun emptyFileFinishesWithNoBlock() {
        val m = Manifest("vide.txt", 0, 1 shl 20); val a = PartAssembler.open(dir, m)
        assertTrue(a.finish(Manifest.root(emptyList()), "vide.txt") is PartAssembler.Finish.Done)
        assertEquals(0L, File(dir, "vide.txt.part").length())
    }
    @Test fun aWriteBeyondFourGiBLandsAtTheRightOffsetInASparseFile() {
        val m = Manifest("enorme.bin", 4 * GiB + 3 * MiB, 1 shl 20)
        val a = try { PartAssembler.open(dir, m) } catch (e: java.io.IOException) { return }      // a file system without sparse files: nothing to test
        val b = ByteArray(MiB.toInt()) { 9 }
        assertTrue(a.writeBlock(4096, sha(b), ByteArrayInputStream(b), b.size.toLong(), false) is PartAssembler.Block.Ok)
        java.io.RandomAccessFile(File(dir, ".cbx/${m.id}.data"), "r").use { it.seek(4 * GiB + 5); assertEquals(9, it.read()); it.seek(4 * GiB - 1); assertEquals(0, it.read()) }
        a.discard()
    }
    @Test fun abandonedTransfersAreSweptAfterTheirAge() {
        val (m, _) = file(MiB.toInt()); PartAssembler.open(dir, m).close()
        assertEquals(0, PartAssembler.sweep(dir, 60_000))
        assertEquals(2, PartAssembler.sweep(dir, 60_000, now = System.currentTimeMillis() + 120_000))
        assertFalse(File(dir, ".cbx").exists())
    }
}

/** A lane that sleeps instead of sending: speeds, latencies, losses and deaths are scripted. */
private class SimLane(override val id: String, val bytesPerSec: Double, override val slow: Boolean = false, override val maxWorkers: Int = 1,
                      val dieAfter: Int = Int.MAX_VALUE, val reviveAt: Long = Long.MAX_VALUE, val corruptEvery: Int = 0, val busyEvery: Int = 0) : Lane {
    override val sent = AtomicLong()
    val calls = AtomicInteger(); val oks = AtomicInteger(); val t0 = System.nanoTime()
    override fun send(worker: Int, idx: Int, ctx: SendContext): Outcome {
        val n = calls.incrementAndGet()
        val dead = n > dieAfter && (System.nanoTime() - t0) / 1_000_000 < reviveAt
        if (dead) { Thread.sleep(5); return Outcome.Failed("coupée") }
        if (busyEvery > 0 && n % busyEvery == 0) return Outcome.Busy(10)
        if (corruptEvery > 0 && n % corruptEvery == 0) return Outcome.Corrupt("sim")
        val len = ctx.manifest.length(idx)
        val end = System.nanoTime() + (len / bytesPerSec * 1e9).toLong()
        while (System.nanoTime() < end) { if (ctx.cancelled()) return Outcome.Cancelled; Thread.sleep(1) }
        oks.incrementAndGet(); return Outcome.Ok(len.toLong())
    }
}

private object NullSource : BlockSource { override val size = Long.MAX_VALUE; override fun read(pos: Long, buf: ByteArray, off: Int, len: Int) = len }

class SchedulerTest {
    private fun run(blocks: Int, lanes: List<SimLane>, done: BlockMap? = null, cancelled: () -> Boolean = { false }, benchMs: Long = 100): Pair<Scheduler.Result, Scheduler> {
        val m = Manifest("sim.bin", blocks * MiB, MiB.toInt())
        val map = done ?: BlockMap(m.blocks)
        val s = Scheduler(m, map, lanes, { c -> SendContext(m, NullSource, HashBook(m, NullSource), c, false) }, benchBaseMs = benchMs)
        return s.run(cancelled) to s
    }

    @Test fun theFasterLaneDoesProportionallyMoreWork() {
        val fast = SimLane("wifi", 200e6, maxWorkers = 2); val mid = SimLane("wifi2", 100e6); val slow = SimLane("bt", 10e6, slow = true)
        val (r, s) = run(60, listOf(fast, mid, slow))
        assertEquals(Scheduler.Result.Done, r)
        val f = s.blocksBy("wifi"); val md = s.blocksBy("wifi2"); val sl = s.blocksBy("bt")
        assertTrue(f + md + sl >= 60)
        assertTrue(f > md, "wifi (2 workers x 200 MB/s) $f vs wifi2 $md"); assertTrue(md > sl, "wifi2 $md vs bt $sl"); assertTrue(sl >= 1, "the slow lane still contributes")
    }

    @Test fun aSlowLaneNeverHoldsTheLastBlocksHostage() {
        // The slow lane takes from the back; 1 MiB at 0.1 MB/s would be 10 s. The fast lane must copy that block and finish at its own pace.
        val t0 = System.nanoTime()
        val (r, s) = run(12, listOf(SimLane("wifi", 100e6), SimLane("bt", 0.1e6, slow = true)))
        val secs = (System.nanoTime() - t0) / 1e9
        assertEquals(Scheduler.Result.Done, r); assertTrue(secs < 3.0, "took $secs s"); assertTrue(s.duplicates.get() >= 1, "the last block was copied")
    }

    @Test fun aDeadLaneIsSetAsideAndTheOthersFinish() {
        val dead = SimLane("wifi2", 100e6, dieAfter = 2); val good = SimLane("wifi", 50e6)
        val (r, s) = run(30, listOf(good, dead))
        assertEquals(Scheduler.Result.Done, r); assertTrue(dead.oks.get() <= 2); assertTrue(good.oks.get() >= 28)
        assertTrue(dead.calls.get() < 40, "a dead lane is not hammered: ${dead.calls.get()} calls")
    }

    @Test fun aLaneThatComesBackIsUsedAgain() {
        val flaky = SimLane("wifi2", 100e6, dieAfter = 1, reviveAt = 300); val good = SimLane("wifi", 5e6)
        val (r, _) = run(40, listOf(good, flaky), benchMs = 50)
        assertEquals(Scheduler.Result.Done, r)
        assertTrue(flaky.oks.get() >= 3, "revived lane did ${flaky.oks.get()} blocks")
    }

    @Test fun corruptBlocksAreSentAgainButNotForever() {
        val (r, _) = run(20, listOf(SimLane("wifi", 100e6, corruptEvery = 3)))
        assertEquals(Scheduler.Result.Done, r)
        val always = object : Lane {
            override val id = "x"; override val maxWorkers = 1; override val sent = AtomicLong()
            override fun send(worker: Int, idx: Int, ctx: SendContext) = Outcome.Corrupt("always")
        }
        val m = Manifest("a", 2 * MiB, MiB.toInt())
        val res = Scheduler(m, BlockMap(2), listOf(always), { c -> SendContext(m, NullSource, HashBook(m, NullSource), c, false) }, sleepMs = { }).run { false }
        assertTrue(res is Scheduler.Result.Failed && "refusé" in res.reason, res.toString())
    }

    @Test fun busyAnswersSlowTheLaneDownButDoNotLoseBlocks() {
        val (r, _) = run(20, listOf(SimLane("wifi", 100e6, busyEvery = 2)))
        assertEquals(Scheduler.Result.Done, r)
    }

    @Test fun resumeSkipsWhatTheTvAlreadyHolds() {
        val m = BlockMap(10); (0 until 6).forEach { m.set(it) }
        val l = SimLane("wifi", 100e6)
        val (r, _) = run(10, listOf(l), done = m)
        assertEquals(Scheduler.Result.Done, r); assertEquals(4, l.oks.get())
    }

    @Test fun cancelStopsPromptly() {
        val stop = AtomicBoolean(false)
        Thread { Thread.sleep(100); stop.set(true) }.start()
        val t0 = System.nanoTime()
        val (r, _) = run(1000, listOf(SimLane("wifi", 5e6)), cancelled = { stop.get() })
        assertEquals(Scheduler.Result.Cancelled, r); assertTrue((System.nanoTime() - t0) / 1e9 < 3)
    }

    @Test fun aFatalErrorStopsTheTransferWithItsReason() {
        val lane = object : Lane { override val id = "x"; override val maxWorkers = 2; override val sent = AtomicLong()
            override fun send(worker: Int, idx: Int, ctx: SendContext) = Outcome.Failed("plus de place", fatal = true) }
        val m = Manifest("a", 5 * MiB, MiB.toInt())
        val res = Scheduler(m, BlockMap(5), listOf(lane), { c -> SendContext(m, NullSource, HashBook(m, NullSource), c, false) }).run { false }
        assertEquals("plus de place", (res as Scheduler.Result.Failed).reason)
    }

    @Test fun hugeFileIsPlannedWithoutOverflow() {
        // 6 GiB in 8 MiB blocks = 768 blocks, scheduled and "sent" by a fast simulated lane
        val m = Manifest("enorme.mkv", 6 * GiB, 8 * MiB.toInt()); assertEquals(768, m.blocks)
        val lane = object : Lane { override val id = "x"; override val maxWorkers = 4; override val sent = AtomicLong()
            override fun send(worker: Int, idx: Int, ctx: SendContext): Outcome { assertEquals(8 * MiB, m.length(idx).toLong()); return Outcome.Ok(m.length(idx).toLong()).also { sent.addAndGet(m.length(idx).toLong()) } } }
        val res = Scheduler(m, BlockMap(m.blocks), listOf(lane), { c -> SendContext(m, NullSource, HashBook(m, NullSource), c, false) }).run { false }
        assertEquals(Scheduler.Result.Done, res); assertTrue(lane.sent.get() >= 6 * GiB)
    }
}
