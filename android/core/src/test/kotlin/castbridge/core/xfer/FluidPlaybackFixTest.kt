package castbridge.core.xfer

import castbridge.core.FakePlayer
import castbridge.core.Rig
import castbridge.core.tv.*
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random
import kotlin.test.*

private const val MiB = 1 shl 20

/**
 * Correctifs de l'audit Opus de « la lecture d'abord » (R-06, docs/agent-reports/fluid-playback-fix.md). Chaque défaut a son test au niveau du vrai
 * `ReceiverServer` sur boucle locale (protocole réel : blocs complets envoyés avant de lire la réponse, lecteurs `/stream/` réels, `finish` long).
 * Toutes les attentes sont bornées (garde TestWatchdogGuardTest).
 */
class FluidPlaybackFixTest {
    private val rigs = ArrayList<Rig>()
    private val closers = ArrayList<() -> Unit>()
    private fun rig(): Rig = Rig().also { it.unplug(); rigs += it }
    /** A rig whose USB key is an exFAT volume (no preallocation: the kernel would zero-fill a gap); copies go there with `target=usb-1234`. */
    private fun usbRig(): Rig = Rig().also { rigs += it }
    private val usb = "usb-1234"
    @AfterTest fun tearDown() { closers.forEach { runCatching { it() } }; rigs.forEach { it.close() } }

    private fun sha(b: ByteArray) = Hash.hex(MessageDigest.getInstance("SHA-256").digest(b))

    private fun begin(r: Rig, name: String, size: Long, blockSize: Int, target: String? = null): Manifest {
        val (code, body) = r.call("POST", "/api/transfer/begin?name=$name&size=$size&blockSize=$blockSize" + (target?.let { "&target=$it" } ?: ""))
        assertEquals(200, code, body)
        return Manifest(name, size, blockSize)
    }

    /** One chunk the way the phone sends it: the whole body goes out BEFORE the answer is read. */
    private fun chunk(r: Rig, m: Manifest, idx: Int, body: ByteArray, conn: HttpConn = HttpConn(HttpConn.tcp("127.0.0.1", r.port)).also { c -> closers += { c.close() } }): HttpConn.Reply =
        conn.request("PUT", "/api/transfer/chunk?id=${m.id}&idx=$idx", "127.0.0.1:${r.port}",
            listOf("Content-Type: application/octet-stream", "X-CB-Sha256: ${sha(body)}"), body.size.toLong()) { out ->
            val bb = ByteBuffer.wrap(body); while (bb.hasRemaining()) out.write(bb)
        }

    private fun waitFor(ms: Long = 6000, what: String, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + ms
        while (!cond()) { assertTrue(System.currentTimeMillis() < end, "délai dépassé : $what"); Thread.sleep(20) }
    }

    private fun playing(r: Rig, name: String = "autre.mp4") {
        r.player.st = PlayerState("playing", name, 5_000, 600_000)
        waitFor(what = "la politique « lecture d'abord » s'allume") { r.call("GET", "/api/info").second.contains("\"playbackPriority\":{\"on\":true") }
    }

    // ================= (1) tête d'abord : aucun trou rempli de zéros sur FAT/exFAT =================

    @Test fun aBlockFarBeyondTheContiguousPrefixIsRefusedAndNeverZeroFillsTheGap() {
        val r = usbRig(); val bs = 4 * MiB
        val m = begin(r, "far.mkv", 96L * MiB, bs, usb)               // 24 blocks, exFAT key: the TV does not preallocate
        val data = File(r.usbDir, ".cbx/${m.id}.data")
        val body = Random(1).nextBytes(bs)
        var gaps = 0L                                                  // « fake sparse sink »: bytes the kernel would zero-fill (offset past the end of the file)
        fun send(idx: Int): Int {
            val before = data.length()
            val st = chunk(r, m, idx, body).status
            if (st == 200) gaps += maxOf(0L, m.offset(idx) - before)
            return st
        }
        assertEquals(429, send(23), "the last block first: refused, not written")
        assertEquals(200, send(0)); assertEquals(200, send(1))
        assertEquals(200, send(8))                                     // inside the window after the prefix
        assertEquals(429, send(23))
        assertTrue(gaps <= PlaybackPriority.headWindow(bs, 6), "zero-filled gaps: $gaps bytes")
    }

    @Test fun theTvAnnouncesAnOrderedFileAndThePhoneTakesTheSlowLaneFromTheHead() {
        val r = usbRig(); val m = Manifest("ord.mkv", 40L * MiB, 4 * MiB)
        val b = HttpTransferApi(r.base) { null }.begin(m, usb, false)
        assertTrue(b.ordered, "no preallocation on this volume: the phone is told to keep the blocks in order")
        val mm = Manifest("s.bin", 8L * MiB, MiB)
        val taken = java.util.Collections.synchronizedList(ArrayList<Int>())
        val lane = object : Lane {
            override val id = "bluetooth"; override val slow = true; override val maxWorkers = 1; override val sent = AtomicLong()
            override fun send(worker: Int, idx: Int, ctx: SendContext): Outcome { taken += idx; return Outcome.Ok(mm.length(idx).toLong()) }
        }
        val src = object : BlockSource { override val size = mm.size; override fun read(pos: Long, buf: ByteArray, off: Int, len: Int) = len }
        val res = Scheduler(mm, BlockMap(mm.blocks), listOf(lane), { c -> SendContext(mm, src, HashBook(mm, src), c, false) }, slowFromHead = true).run { false }
        assertEquals(Scheduler.Result.Done, res)
        assertEquals((0 until mm.blocks).toList(), taken.toList(), "head first")
    }

    @Test fun theSlowLaneStillTakesFromTheEndWhenTheTvPreallocates() {
        val mm = Manifest("s.bin", 4L * MiB, MiB)
        val taken = java.util.Collections.synchronizedList(ArrayList<Int>())
        val lane = object : Lane {
            override val id = "bluetooth"; override val slow = true; override val maxWorkers = 1; override val sent = AtomicLong()
            override fun send(worker: Int, idx: Int, ctx: SendContext): Outcome { taken += idx; return Outcome.Ok(mm.length(idx).toLong()) }
        }
        val src = object : BlockSource { override val size = mm.size; override fun read(pos: Long, buf: ByteArray, off: Int, len: Int) = len }
        Scheduler(mm, BlockMap(mm.blocks), listOf(lane), { c -> SendContext(mm, src, HashBook(mm, src), c, false) }).run { false }
        assertEquals(listOf(3, 2, 1, 0), taken.toList())
    }

    // ================= (2) connexions en surplus : 429 lisible, jamais une coupure =================

    @Test fun aPhoneLikeClientSendingAFullBodyToATvAtItsStreamLimitGetsBusyNotAReset() {
        val r = rig(); val bs = 8 * MiB
        val m = begin(r, "busy.mkv", 40L * MiB, bs)
        playing(r)
        // two slow connections hold the two streams the TV accepts while a video plays
        val holders = (0 until 2).map { i ->
            Socket("127.0.0.1", r.port).also { s ->
                closers += { s.close() }
                s.getOutputStream().apply {
                    write(("PUT /api/transfer/chunk?id=${m.id}&idx=$i HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: $bs\r\nX-CB-Sha256: ${"0".repeat(64)}\r\n\r\n").toByteArray())
                    write(ByteArray(64 * 1024)); flush()
                }
            }
        }
        waitFor(what = "two chunk requests in flight") { r.server.activeChunks == 2 }
        val conn = HttpConn(HttpConn.tcp("127.0.0.1", r.port)).also { closers += { it.close() } }
        val full = Random(2).nextBytes(bs)
        val reply = try { chunk(r, m, 2, full, conn) } catch (e: IOException) { fail("a reset instead of a 429 (the phone would book a failure): ${e.message}") }
        assertEquals(429, reply.status)
        val outcome = ChunkClient("127.0.0.1:${r.port}", m.id) { null }.outcome(reply, bs.toLong())
        assertTrue(outcome is Outcome.Busy, outcome.toString())
        assertTrue(reply.keepAlive, "the connection stays usable: no reconnect, no penalty")
        val again = chunk(r, m, 3, ByteArray(MiB), conn)                // same connection, still limited: a clean answer again
        assertEquals(429, again.status)
        assertEquals(2, holders.size)
    }

    @Test fun theTvAdvertisesTheStreamsItReallyAcceptsWhileThePolicyIsOn() {
        val r = rig()
        assertTrue(r.call("GET", "/api/transfer/caps").second.contains("\"maxStreams\":6"))
        playing(r)
        assertTrue(r.call("GET", "/api/transfer/caps").second.contains("\"maxStreams\":2"), r.call("GET", "/api/transfer/caps").second)
        val m = Manifest("adv.mkv", 8L * MiB, MiB)
        val b = HttpTransferApi(r.base) { null }.begin(m, null, false)
        assertEquals(2, b.maxStreams)
    }

    // ================= (3) lecteurs /stream/ : pas de fil zombie =================

    private class Tv {
        val dir = kotlin.io.path.createTempDirectory("tvstream").toFile()
        val player = FakePlayer()
        val use = StreamUse()
        val server = ReceiverServer(VolumeRegistry.single(dir), player, 0, profile = TvProfile(minFreeBytes = 0, minFreeAfterTransfer = 0), streamUse = use).apply { start(5000, false) }
        val base = "http://127.0.0.1:${server.listeningPort}"
        fun close() { server.stop(); dir.deleteRecursively() }

        /** A partial copy: [have] bytes of a [total]-byte file (the PUT ends short, the way a cut upload leaves it). */
        fun partial(name: String, have: Int, total: Int) {
            val c = URL("$base/upload/$name?offset=0&total=$total").openConnection() as HttpURLConnection
            c.requestMethod = "PUT"; c.doOutput = true; c.setFixedLengthStreamingMode(have)
            c.outputStream.use { it.write(ByteArray(have) { 7 }) }
            c.responseCode
        }
        fun streamSocket(name: String, from: Long): Socket = Socket("127.0.0.1", server.listeningPort).also { s ->
            s.soTimeout = 1000
            s.getOutputStream().apply { write("GET /stream/$name HTTP/1.1\r\nHost: 127.0.0.1\r\nRange: bytes=$from-\r\n\r\n".toByteArray()); flush() }
        }
        fun get(path: String, range: String? = null): Pair<Int, String> {
            val c = URL(base + path).openConnection() as HttpURLConnection
            range?.let { c.setRequestProperty("Range", it) }
            c.readTimeout = 5000
            val code = c.responseCode
            return code to ((if (code < 400) c.inputStream else c.errorStream)?.readBytes()?.decodeToString().orEmpty())
        }
    }
    private fun tv() = Tv().also { closers += { it.close() } }

    /** True once the server hung up (EOF or reset) within [ms]; false if it kept the connection open. */
    private fun closedByServer(s: Socket, ms: Long = 8000): Boolean {
        val end = System.currentTimeMillis() + ms
        val buf = ByteArray(4096)
        while (System.currentTimeMillis() < end) {
            try { if (s.getInputStream().read(buf) < 0) return true } catch (e: SocketTimeoutException) { } catch (e: IOException) { return true }
        }
        return false
    }

    @Test fun aNewStreamRequestClosesTheOldestReaderOfTheSameFile() {
        val t = tv(); t.partial("film.mkv", 1_000_000, 5_000_000)
        val s1 = t.streamSocket("film.mkv", 2_000_000).also { x -> closers += { x.close() } }
        waitFor(what = "reader 1") { t.use.openCount("film.mkv") == 1 }
        val s2 = t.streamSocket("film.mkv", 2_100_000).also { x -> closers += { x.close() } }
        waitFor(what = "reader 2") { t.use.openCount("film.mkv") == 2 }
        val s3 = t.streamSocket("film.mkv", 2_200_000).also { x -> closers += { x.close() } }
        assertTrue(closedByServer(s1), "the oldest reader (a seek of the player left it behind) must be closed, not left waiting 2 minutes")
        waitFor(what = "two readers left") { t.use.openCount("film.mkv") == 2 }
        assertFalse(closedByServer(s2, 1500)); assertFalse(closedByServer(s3, 500))
    }

    @Test fun aReaderWhoseClientHungUpIsReleasedWhileItWaits() {
        val t = tv(); t.partial("film.mkv", 1_000_000, 5_000_000)
        val s = t.streamSocket("film.mkv", 2_000_000)
        waitFor(what = "reader open") { t.use.openCount("film.mkv") == 1 }
        s.close()                                                    // libVLC closes the connection (seek, stop)
        waitFor(8000, "the waiting thread notices the closed socket") { t.use.openCount("film.mkv") == 0 }
    }

    @Test fun growingStreamStopsWaitingWhenTheClientIsGone() {
        val dir = kotlin.io.path.createTempDirectory("gs").toFile(); closers += { dir.deleteRecursively() }
        File(dir, "a.mp4.part").writeBytes(ByteArray(100))
        var t = 0L; val gone = java.util.concurrent.atomic.AtomicBoolean(false)
        val g = GrowingStream(dir, "a.mp4", 100, 1000, clock = { t }, sleep = { t += 50; if (t >= 3000) gone.set(true) }, stillComing = { true },
            maxWaitMs = 120_000, clientGone = { gone.get() })
        val e = assertFailsWith<IOException> { g.read(ByteArray(10)) }
        assertTrue(t < 10_000, "gave up long before the 2 minutes: t=$t (${e.message})")
    }

    @Test fun growingStreamGivesUpQuicklyOnceTheCopyIsDead() {
        val dir = kotlin.io.path.createTempDirectory("gs").toFile(); closers += { dir.deleteRecursively() }
        File(dir, "a.mp4.part").writeBytes(ByteArray(100))
        var t = 0L
        val g = GrowingStream(dir, "a.mp4", 100, 1000, clock = { t }, sleep = { t += 50 }, stillComing = { false }, deadWaitMs = 5_000)
        assertFailsWith<IOException> { g.read(ByteArray(10)) }
        assertTrue(t in 5_000..6_000, "t=$t")
    }

    @Test fun streamUseClosesTheOldestReadersBeyondTheCap() {
        val use = StreamUse(); val closed = ArrayList<Int>()
        fun reader(i: Int) = object : java.io.InputStream() { override fun read() = -1; override fun close() { closed += i } }
        val a = use.track("x", reader(1), maxOpen = 2); val b = use.track("x", reader(2), maxOpen = 2); val c = use.track("x", reader(3), maxOpen = 2)
        assertEquals(listOf(1), closed)
        assertEquals(2, use.openCount("x"))
        a.close(); b.close(); c.close()                                // closing again is harmless
        assertEquals(0, use.openCount("x"))
        assertEquals(listOf(1, 2, 3), closed.distinct().sorted())
    }

    // ================= (4) finish : second appel immédiat, session perdue = reprise =================

    @Test fun aSecondFinishWhileTheFirstVerifiesAnswersAtOnceWithoutBlockingAThread() {
        val r = rig(); val bs = 4 * MiB; val n = 12
        val m = begin(r, "long.mkv", n.toLong() * bs, bs)
        val blocks = (0 until n).map { Random(it + 10).nextBytes(bs) }
        blocks.forEachIndexed { i, b -> assertEquals(200, chunk(r, m, i, b).status) }
        val root = Manifest.root(blocks.map { sha(it) })
        playing(r)                                                   // the read-back is paced at 12 MB/s: about 4 s for 48 MiB
        val first = AtomicReference<Pair<Int, String>>()
        val th = Thread { first.set(r.call("POST", "/api/transfer/finish?id=${m.id}&root=$root")) }.apply { isDaemon = true; start() }
        waitFor(what = "the first finish is verifying") { r.server.finishingNow(m.id) }
        val t0 = System.nanoTime()
        val (code, body) = r.call("POST", "/api/transfer/finish?id=${m.id}&root=$root")
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertEquals(503, code, body); assertTrue("verifying" in body, body)
        assertTrue(ms < 1500, "the second finish waited $ms ms for the lock")
        th.join(30_000)
        assertEquals(200, first.get()?.first, first.get()?.second)
        assertContentEquals(blocks.reduce { a, b -> a + b }, File(r.internalDir, "long.mkv").readBytes())   // nothing declared complete before the full check
    }

    @Test fun aFinishForATransferTheTvLostMakesThePhoneResumeByBeginNotFailForGood() {
        val r = rig()
        val (code, body) = r.call("POST", "/api/transfer/finish?id=0123456789abcdef01234567&root=${"0".repeat(64)}")
        assertNotEquals(404, code, body)
        assertTrue(code in 500..599, "$code $body")
        assertTrue("unknown transfer" in body, body)
        val api = HttpTransferApi(r.base) { null }
        assertFailsWith<IOException>("the phone must retry (begin), not give up with a message") { api.finish("0123456789abcdef01234567", "0".repeat(64)) }
    }

    @Test fun aFinishAfterTheFirstConcludedAnswersDone() {
        val r = rig(); val bs = MiB
        val m = begin(r, "done.mkv", 3L * bs, bs)
        val blocks = (0 until 3).map { Random(it).nextBytes(bs) }
        blocks.forEachIndexed { i, b -> assertEquals(200, chunk(r, m, i, b).status) }
        val root = Manifest.root(blocks.map { sha(it) })
        assertEquals(200, r.call("POST", "/api/transfer/finish?id=${m.id}&root=$root").first)
        // the session is gone: the phone is told to resume (not a refusal), and its begin finds the finished file
        val (code, body) = r.call("POST", "/api/transfer/finish?id=${m.id}&root=$root")
        assertEquals(503, code, body)
        assertTrue(HttpTransferApi(r.base) { null }.begin(m, null, false).done)
    }

    // ================= (5) lecture qui ne repart pas : la politique se relâche =================

    private val normal = PlaybackPriority.Normal(maxStreams = 6, syncEveryBytes = 64L shl 20)

    @Test fun aBufferingPlayerThatNeverAdvancesStopsCountingAsPlayingAfterThirtySeconds() {
        var t = 0L; var state = "buffering"; var head = 5_000L; val ran = ArrayList<String>()
        val g = PlaybackGovernor({ PlaybackSignal(state, "v.mp4", playheadMs = head) }, normal, clock = { t }, refreshMs = 0)
        g.deferred.defer("sync") { ran += "sync" }
        assertTrue(g.refresh().on)
        t = 29_000; assertTrue(g.refresh().on, "still within the grace")
        t = 31_000; assertFalse(g.refresh().on, "player crashed or stuck: the copies go back to full speed")
        assertEquals(listOf("sync"), ran, "the deferred fsync leaves")
        t = 60_000; assertFalse(g.refresh().on)
        head = 9_000; t = 61_000
        assertTrue(g.refresh().on, "the playhead moved again: the policy is back")
    }

    @Test fun aBufferingPlayerWhoseHeadMovesIsNeverCutOff() {
        var t = 0L; var head = 0L
        val g = PlaybackGovernor({ PlaybackSignal("buffering", "v.mp4", playheadMs = head) }, normal, clock = { t }, refreshMs = 0)
        for (i in 1..40) { t += 10_000; head += 1_000; assertTrue(g.refresh().on, "step $i") }
    }

    @Test fun aFlappingPlayerThatMakesNoProgressIsLatchedOffUntilItReallyAdvances() {
        var t = 0L; var state = "playing"; var head = 1_000L
        val g = PlaybackGovernor({ PlaybackSignal(state, "v.mp4", playheadMs = head) }, normal, clock = { t }, refreshMs = 0)
        assertTrue(g.refresh().on)
        var offSeen = false
        for (i in 1..80) {
            t += 1_000; state = if (i % 2 == 0) "playing" else "buffering"       // the head never moves
            val on = g.refresh().on
            if (t > 40_000) assertFalse(on, "latched off at t=$t ($state)")
            if (!on) offSeen = true
        }
        assertTrue(offSeen)
        head = 3_000; t += 1_000; state = "playing"
        assertTrue(g.refresh().on)
    }

    @Test fun aStreamForADeadCopyAnswersAnErrorInsteadOfWaitingTwoMinutes() {
        val t = tv(); t.partial("film.mkv", 1_000_000, 5_000_000)
        File(t.dir, "film.mkv.part").setLastModified(System.currentTimeMillis() - 10 * 60_000)    // nobody wrote for 10 min: the copy is dead
        t.server.progress.abort("put:film.mkv", "test")                  // and the reception sweep has dropped it from the live list
        val t0 = System.nanoTime()
        val (code, body) = try { t.get("/stream/film.mkv", "bytes=2000000-") } catch (e: SocketTimeoutException) { fail("no answer after 5 s: the reader of a dead copy waits instead of failing") }
        assertTrue((System.nanoTime() - t0) / 1_000_000 < 5_000, "answered at once")
        assertEquals(503, code, body)
        val (c2, _) = t.get("/stream/film.mkv", "bytes=0-99")          // what already arrived stays playable
        assertEquals(206, c2)
    }

    // ================= (6) info ne fait jamais un fsync ; (7) pas de .part vide ; (8) =================

    @Test fun jsonNeverRunsTheDeferredWorkOnTheCallersThread() {
        var t = 0L
        val g = PlaybackGovernor({ PlaybackSignal() }, normal, clock = { t }, refreshMs = 0)
        val ranOn = AtomicReference<Thread>(); val started = CountDownLatch(1)
        g.deferred.defer("sync") { ranOn.set(Thread.currentThread()); started.countDown() }
        t = 10
        g.json()                                                       // the /api/info thread
        assertTrue(started.await(5, TimeUnit.SECONDS), "the work still runs, soon")
        assertNotSame(Thread.currentThread(), ranOn.get(), "json() must not fsync on the /api/info thread")
    }

    @Test fun syncingAPartThatWasRenamedDoesNotCreateAnEmptyPart() {
        val dir = kotlin.io.path.createTempDirectory("sp").toFile(); closers += { dir.deleteRecursively() }
        val st = FileStore(StorageVolume("internal", "Mémoire interne", dir, VolumeKind.INTERNAL, Fs.UNKNOWN, 0, 0, false))
        assertFalse(st.syncPart("gone.mp4"))
        assertFalse(File(dir, "gone.mp4.part").exists(), "a late sync must not resurrect an empty .part")
        File(dir, "here.mp4.part").writeBytes(ByteArray(10))
        assertTrue(st.syncPart("here.mp4"))
    }
}
