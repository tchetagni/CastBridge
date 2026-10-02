package castbridge.core

import castbridge.core.tv.*
import castbridge.core.xfer.*
import java.io.File
import java.net.URL
import java.net.HttpURLConnection
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random
import kotlin.test.*

private const val MiB = 1L shl 20

/** The whole chain on loopback: TransferClient → lanes → real ReceiverServer → disk. */
class MultipathServerTest {
    private val rigs = ArrayList<Rig>()
    private fun rig(pin: String? = null) = Rig(pin = pin).also { it.unplug(); rigs += it }
    @AfterTest fun tearDown() { rigs.forEach { it.close() } }
    private val work = kotlin.io.path.createTempDirectory("src").toFile()
    @AfterTest fun clean() { work.deleteRecursively() }

    private fun src(name: String, bytes: ByteArray) = File(work, name).apply { writeBytes(bytes) }
    private fun client(r: Rig, f: File, name: String, pin: String? = null, k: Int? = 4, bt: Boolean = false, cancelled: () -> Boolean = { false },
                       wrap: (Lane) -> Lane = { it }, onProgress: (Long, Long) -> Unit = { _, _ -> }, base: String = r.base, hostPort: Int = r.port,
                       compress: Boolean = true, onDisk: (Long, String?) -> Unit = { _, _ -> }): Pair<TransferClient, FileChannel> {
        val ch = FileChannel.open(f.toPath(), StandardOpenOption.READ)
        val host = "127.0.0.1:$hostPort"
        val tc = TransferClient(HttpTransferApi(base) { pin }, FileBlockSource(ch), name, { id, max ->
            buildList {
                add(wrap(WifiLane("wifi", host, id, HttpConn.tcp("127.0.0.1", hostPort), { pin }, maxStreams = max, fixedK = k)))
                if (bt) add(BluetoothLane("bluetooth", host, id, HttpConn.tcp("127.0.0.1", hostPort), { pin }))
            }
        }, cancelled = cancelled, onProgress = onProgress, retryDelayMs = 50, compress = compress, onDisk = onDisk)
        return tc to ch
    }

    private fun finalFile(r: Rig, name: String) = File(r.internalDir, name)
    private fun randomBytes(n: Int, seed: Int = 1) = Random(seed).nextBytes(n)

    @Test fun aFileTravelsOverFourConnectionsAndLandsIntact() {
        val r = rig(); val data = randomBytes(9 * MiB.toInt() + 1234)
        val (tc, ch) = client(r, src("a.mkv", data), "a.mkv", k = 4); ch.use {
            val seen = ArrayList<Long>()
            val res = TransferClient::class.java.let { tc.run() }
            assertEquals(TransferClient.Result.Done, res)
        }
        assertContentEquals(data, finalFile(r, "a.mkv").readBytes())
        assertFalse(File(r.internalDir, "a.mkv.part").exists()); assertFalse(File(r.internalDir, ".cbx").let { it.exists() && it.list()!!.isNotEmpty() })
        assertTrue(r.notices.any { "Vidéo reçue" in it })
    }

    @Test fun progressReachesTheTotalAndNeverGoesBackwardsByMuch() {
        val r = rig(); val data = randomBytes(6 * MiB.toInt())
        val seen = java.util.Collections.synchronizedList(ArrayList<Long>())
        val (tc, ch) = client(r, src("p.mp4", data), "p.mp4", onProgress = { s, _ -> seen += s }); ch.use { assertEquals(TransferClient.Result.Done, tc.run()) }
        assertEquals(data.size.toLong(), seen.last()); assertTrue(seen.size >= 3)
    }

    @Test fun theTvRefusesWithoutTheRightCredentialAndNeverSeesItInAUrl() {
        val r = rig(pin = "123456"); val data = randomBytes(MiB.toInt())
        val (bad, c1) = client(r, src("x.bin", data), "x.bin", pin = "000000"); c1.use { val res = bad.run(); assertTrue(res is TransferClient.Result.Failed, res.toString()) }
        assertFalse(finalFile(r, "x.bin").exists())
        val (good, c2) = client(r, src("x.bin", data), "x.bin", pin = "123456"); c2.use { assertEquals(TransferClient.Result.Done, good.run()) }
        assertContentEquals(data, finalFile(r, "x.bin").readBytes())
        // the PIN travels in a header (X-CB-Pin), never as ?pin= : the lanes and the API client build their URLs without it
        val u = HttpTransferApi::class.java.declaredMethods.map { it.name }; assertFalse(u.any { "pin" in it.lowercase() })
    }

    @Test fun resumeAfterACutContinuesFromTheTvsBlockMap() {
        val r = rig(); val data = randomBytes(12 * MiB.toInt())
        val f = src("cut.mkv", data)
        val sent = AtomicInteger(); val stop = AtomicBoolean(false)
        // first run: the "network" dies after 5 blocks
        val (a, ca) = client(r, f, "cut.mkv", k = 2, cancelled = { stop.get() }, wrap = { l -> object : Lane by l {
            override fun send(worker: Int, idx: Int, ctx: SendContext): Outcome { if (sent.get() >= 5) { stop.set(true); return Outcome.Failed("coupé") }; return l.send(worker, idx, ctx).also { if (it is Outcome.Ok) sent.incrementAndGet() } }
        } })
        ca.use { assertEquals(TransferClient.Result.Cancelled, a.run()) }
        assertFalse(finalFile(r, "cut.mkv").exists())
        val held = File(r.internalDir, ".cbx").list()!!.size; assertEquals(2, held, "data + state stay for the resume")
        // second run: only the missing blocks travel
        val (b, cb) = client(r, f, "cut.mkv", k = 2); cb.use { assertEquals(TransferClient.Result.Done, b.run()) }
        assertContentEquals(data, finalFile(r, "cut.mkv").readBytes())
        val resent = b.perLane.values.sum()
        assertTrue(resent <= data.size - 5 * MiB + 2 * MiB, "second run sent $resent of ${data.size}")
    }

    @Test fun resumeSurvivesARestartOfTheTvApp() {
        val r = rig(); val data = randomBytes(8 * MiB.toInt()); val f = src("rest.mkv", data)
        val sent = AtomicInteger(); val stop = AtomicBoolean(false)
        val (a, ca) = client(r, f, "rest.mkv", k = 1, cancelled = { stop.get() }, wrap = { l -> object : Lane by l {
            override fun send(worker: Int, idx: Int, ctx: SendContext): Outcome { if (sent.get() >= 4) { stop.set(true); return Outcome.Failed("coupé") }; return l.send(worker, idx, ctx).also { if (it is Outcome.Ok) sent.incrementAndGet() } }
        } })
        ca.use { a.run() }
        r.server.stop()
        val port2 = java.net.ServerSocket(0).use { it.localPort }
        val s2 = ReceiverServer(r.registry, r.player, port2, profile = TvProfile(minFreeBytes = 0, minFreeAfterTransfer = 0)).apply { start(5000, false) }
        try {
            val (b, cb) = client(r, f, "rest.mkv", k = 1, base = "http://127.0.0.1:$port2", hostPort = port2); cb.use { assertEquals(TransferClient.Result.Done, b.run()) }
            assertTrue(b.perLane.values.sum() < data.size, "restarted TV kept the first blocks: sent ${b.perLane.values.sum()}")
        } finally { s2.stop() }
        assertContentEquals(data, finalFile(r, "rest.mkv").readBytes())
    }

    @Test fun aCorruptedBlockOnTheWireIsDetectedAndSentAgain() {
        val r = rig(); val data = randomBytes(5 * MiB.toInt()); val flips = AtomicInteger()
        // a lane that sends the right hash but wrong bytes once: the TV refuses (422), the scheduler resends
        val f = src("bad.mkv", data)
        val ch = FileChannel.open(f.toPath(), StandardOpenOption.READ)
        val flaky = object : BlockSource by FileBlockSource(ch) {
            val real = FileBlockSource(ch)
            override fun transferTo(pos: Long, count: Long, out: java.nio.channels.WritableByteChannel): Long {
                if (pos == 2 * MiB && flips.getAndIncrement() == 0) { val b = ByteArray(count.toInt()); real.read(pos, b, 0, b.size); b[10] = (b[10] + 1).toByte(); out.write(java.nio.ByteBuffer.wrap(b)); return count }
                return real.transferTo(pos, count, out)
            }
        }
        ch.use {
            val tc = TransferClient(HttpTransferApi(r.base) { null }, flaky, "bad.mkv", { id, max -> listOf(WifiLane("wifi", "127.0.0.1:${r.port}", id, HttpConn.tcp("127.0.0.1", r.port), { null }, maxStreams = max, fixedK = 2)) }, retryDelayMs = 50)
            assertEquals(TransferClient.Result.Done, tc.run())
        }
        assertTrue(flips.get() >= 2, "block was sent a second time"); assertContentEquals(data, finalFile(r, "bad.mkv").readBytes())
    }

    @Test fun wifiAndBluetoothLanesWorkTogetherThroughSlices() {
        val r = rig(); val data = randomBytes(10 * MiB.toInt()); val f = src("duo.mkv", data)
        val (tc, ch) = client(r, f, "duo.mkv", k = 2, bt = true, wrap = { l -> object : Lane by l {
            override fun send(worker: Int, idx: Int, ctx: SendContext): Outcome { Thread.sleep(40); return l.send(worker, idx, ctx) }   // wifi slower than usual so that the slow lane gets a block
        } })
        ch.use { assertEquals(TransferClient.Result.Done, tc.run()) }
        assertContentEquals(data, finalFile(r, "duo.mkv").readBytes())
        assertTrue((tc.perLane["bluetooth"] ?: 0) > 0, "bluetooth lane contributed: ${tc.perLane}")
        assertTrue((tc.perLane["wifi"] ?: 0) > 0)
    }

    @Test fun textIsCompressedOnTheWireAndMediaIsNot() {
        val r = rig(); val text = ("une ligne de texte assez répétitive pour bien se compresser\n".repeat(60_000)).toByteArray()
        val wires = ArrayList<WifiLane>()
        val (tc, ch) = client(r, src("t.txt", text), "t.txt", k = 2, wrap = { l -> l.also { wires += it as WifiLane } }); ch.use { assertEquals(TransferClient.Result.Done, tc.run()) }
        assertContentEquals(text, finalFile(r, "t.txt").readBytes())
        assertTrue(wires.last().wire.get() < text.size / 4, "wire bytes ${wires.last().wire.get()} for ${text.size}")
        val media = randomBytes(2 * MiB.toInt())
        val (t2, c2) = client(r, src("m.mp4", media), "m.mp4", k = 2, wrap = { l -> l.also { wires += it as WifiLane } }); c2.use { assertEquals(TransferClient.Result.Done, t2.run()) }
        assertEquals(media.size.toLong(), wires.last().wire.get()); assertContentEquals(media, finalFile(r, "m.mp4").readBytes())
    }

    @Test fun anEmptyFileTransfers() {
        val r = rig(); val (tc, ch) = client(r, src("z.txt", ByteArray(0)), "z.txt"); ch.use { assertEquals(TransferClient.Result.Done, tc.run()) }
        assertTrue(finalFile(r, "z.txt").isFile); assertEquals(0L, finalFile(r, "z.txt").length())
    }

    @Test fun aFileTheTvAlreadyHoldsIsNotSentAgain() {
        val r = rig(); val data = randomBytes(2 * MiB.toInt()); val f = src("dup.mkv", data)
        val (a, ca) = client(r, f, "dup.mkv"); ca.use { assertEquals(TransferClient.Result.Done, a.run()) }
        val (b, cb) = client(r, f, "dup.mkv"); cb.use { assertEquals(TransferClient.Result.Done, b.run()) }
        assertTrue(b.perLane.values.sum() == 0L)
    }

    @Test fun anOldTvWithoutTheRouteMakesTheClientFallBack() {
        val port = java.net.ServerSocket(0).use { it.localPort }
        val old = object : fi.iki.elonen.NanoHTTPD(port) {
            override fun serve(session: IHTTPSession): Response = newFixedLengthResponse(Response.Status.NOT_FOUND, "application/json", """{"error":"not found"}""")
        }.apply { start(5000, false) }
        try {
            val f = src("o.mkv", randomBytes(100)); val ch = FileChannel.open(f.toPath(), StandardOpenOption.READ)
            ch.use { assertEquals(TransferClient.Result.Unsupported, TransferClient(HttpTransferApi("http://127.0.0.1:$port") { null }, FileBlockSource(ch), "o.mkv", { _, _ -> emptyList() }).run()) }
        } finally { old.stop() }
    }

    @Test fun theTvAnnouncesItsLimitsAndKeepsAbandonedTransfersCleanable() {
        val r = rig()
        val api = HttpTransferApi(r.base) { null }
        val caps = api.caps()!!; assertEquals(1, caps.version); assertTrue(caps.maxStreams in 2..8)
        val m = Manifest("w.bin", 5 * MiB, MiB.toInt()); val b = api.begin(m, null)
        assertFalse(b.done); assertEquals(5, b.map.missing().size); assertEquals(m.id, b.id)
        assertNotNull(api.state(b.id)); api.abort(b.id); assertNull(api.state(b.id))
        assertFalse(File(r.internalDir, ".cbx").let { it.exists() && it.list()!!.isNotEmpty() }, "abort removes the partial files")
    }

    @Test fun badRequestsAreRefusedCleanly() {
        val r = rig(); val api = HttpTransferApi(r.base) { null }
        fun raw(method: String, path: String): Int { val c = URL(r.base + path).openConnection() as HttpURLConnection; c.requestMethod = method; if (method != "GET") { c.doOutput = true; c.setFixedLengthStreamingMode(0); c.outputStream.close() }; return c.responseCode }
        assertEquals(400, raw("POST", "/api/transfer/begin?name=a.bin&size=10&blockSize=100"))
        assertEquals(400, raw("POST", "/api/transfer/begin?name=..%2Fa.bin&size=10&blockSize=1048576"))
        assertEquals(404, raw("GET", "/api/transfer/state?id=nope"))
        assertEquals(503, raw("POST", "/api/transfer/finish?id=nope&root=x"))     // not 404 (the phone would take it for a refusal): 503 makes it resume by begin
        val b = api.begin(Manifest("f.bin", 2 * MiB, MiB.toInt()), null)
        assertTrue(api.finish(b.id, "0".repeat(64)) is TransferApi.Finish.Missing)
        val c = URL(r.base + "/api/transfer/chunk?id=${b.id}&idx=0").openConnection() as HttpURLConnection
        c.requestMethod = "PUT"; c.doOutput = true; c.setFixedLengthStreamingMode(5); c.setRequestProperty("X-CB-Sha256", "0".repeat(64)); c.outputStream.use { it.write(ByteArray(5)) }
        assertEquals(400, c.responseCode, "a block must have the manifest's length")
    }

    @Test fun noSpaceIsRefusedBeforeTheFirstByte() {
        val r = rig(); r.capacity["internal"] = 1 * MiB; r.unplug()
        val api = HttpTransferApi(r.base) { null }
        val e = assertFailsWith<TransferApi.Refused> { api.begin(Manifest("big.mkv", 50 * MiB, 4 * MiB.toInt()), null) }
        assertEquals(507, e.http)
        assertFalse(File(r.internalDir, ".cbx").exists())
    }

    @Test fun backPressureAnswersBusyWhenTheDiskIsBehind() {
        val h = TransferHost(maxStreams = 4)
        assertTrue(h.mayAccept(4 * MiB))
        h.stats.queue(40 * MiB)
        assertFalse(h.mayAccept(4 * MiB), "40 MiB queued with an unknown disk speed: no more")
        h.stats.queue(-40 * MiB); assertTrue(h.mayAccept(4 * MiB))
        h.stats.record(1 shl 20, 1_000_000_000L)       // the disk absorbs 1 MiB/s
        assertTrue(h.maxInflight(4 * MiB) <= 6 * 4 * MiB && h.maxInflight(4 * MiB) >= 2 * 4 * MiB)
    }

    @Test fun networkAloneRunStoresNothingOnTheTv() {
        val r = rig(); val data = randomBytes(5 * MiB.toInt()); val f = src("net.bin", data)
        val ch = FileChannel.open(f.toPath(), StandardOpenOption.READ)
        ch.use {
            val tc = TransferClient(HttpTransferApi(r.base) { null }, FileBlockSource(ch), "net.bin", { id, max -> listOf(WifiLane("wifi", "127.0.0.1:${r.port}", id, HttpConn.tcp("127.0.0.1", r.port), { null }, maxStreams = max, fixedK = 2)) }, discard = true, retryDelayMs = 50)
            assertEquals(TransferClient.Result.Done, tc.run())
        }
        assertFalse(finalFile(r, "net.bin").exists()); assertFalse(File(r.internalDir, "net.bin.part").exists()); assertFalse(File(r.internalDir, ".cbx").exists())
        assertEquals(0, r.server.activeTransfers())
    }

    @Test fun aSlowDiskIsReportedInFrenchAndNotTheWifiBlamed() {
        val h = TransferHost(); val dir = kotlin.io.path.createTempDirectory("note").toFile()
        try {
            val m = Manifest("n.bin", 4 * MiB, MiB.toInt())
            val s = (h.begin(m) { Allocation.At(dir, "n.bin", "internal") } as TransferHost.Begin.Ok).s
            assertNull(h.note(s), "nothing is said before anything is measured")
            val d = randomBytes(MiB.toInt())
            repeat(2) { s.assembler.writeBlock(it, Hash.hex(java.security.MessageDigest.getInstance("SHA-256").digest(d)), java.io.ByteArrayInputStream(d), d.size.toLong(), false) }
            repeat(60) { h.stats.record(1 shl 20, 1_000_000_000L) }       // the disk absorbs 1 MiB per second, steadily
            val n = h.note(s)!!
            assertTrue("Mo/s" in n && "pas le Wi-Fi" in n, n); assertTrue("\"note\"" in h.stateJson(s))
        } finally { dir.deleteRecursively() }
    }

    @Test fun theTvsDiskSpeedReachesThePhoneWhileCopying() {
        val r = rig(); val data = randomBytes(8 * MiB.toInt())
        val speeds = java.util.Collections.synchronizedList(ArrayList<Long>())
        val (tc, ch) = client(r, src("sp.mkv", data), "sp.mkv", k = 1, onDisk = { bps, _ -> speeds += bps },
            wrap = { l -> object : Lane by l { override fun send(worker: Int, idx: Int, ctx: SendContext): Outcome { Thread.sleep(500); return l.send(worker, idx, ctx) } } })
        ch.use { assertEquals(TransferClient.Result.Done, tc.run()) }
        assertTrue(speeds.any { it > 0 }, "the phone saw the measured disk speed: $speeds")
    }
}
