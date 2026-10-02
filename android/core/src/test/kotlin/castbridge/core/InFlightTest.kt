package castbridge.core

import castbridge.core.remote.*
import castbridge.core.trust.*
import castbridge.core.tv.*
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlin.random.Random
import kotlin.test.*

/** TCP relay that cuts the phone->TV direction after an exact number of bytes (a Wi-Fi drop in the middle of a transfer). */
class CutProxy(private val targetPort: () -> Int) : Closeable {
    private val ss = ServerSocket(0)
    val port get() = ss.localPort
    @Volatile var cutAfter = -1L                         // bytes still allowed to flow phone->TV before the cut; -1 = never
    @Volatile var cutDownAfter = -1L                      // same, TV->phone (a download cut in the middle of the body)
    @Volatile var down = false                            // the TV is unreachable (connections are refused)
    val cuts = AtomicInteger()
    private val live = java.util.concurrent.CopyOnWriteArrayList<Socket>()

    init { thread(isDaemon = true) { while (!ss.isClosed) { val c = try { ss.accept() } catch (_: IOException) { break }; handle(c) } } }

    private fun handle(c: Socket) {
        if (down) { runCatching { c.close() }; return }
        val s = try { Socket("127.0.0.1", targetPort()) } catch (_: IOException) { runCatching { c.close() }; return }
        live += c; live += s
        thread(isDaemon = true) {                         // phone -> TV, counted
            try {
                val buf = ByteArray(8192); val i = c.getInputStream(); val o = s.getOutputStream()
                while (true) {
                    val n = i.read(buf); if (n < 0) break
                    val left = cutAfter
                    if (left in 0 until n) { o.write(buf, 0, left.toInt()); o.flush(); cutAfter = -1; cuts.incrementAndGet(); break }
                    if (left >= 0) cutAfter = left - n
                    o.write(buf, 0, n); o.flush()
                }
            } catch (_: IOException) {} finally { runCatching { c.close() }; runCatching { s.close() } }
        }
        thread(isDaemon = true) {                         // TV -> phone
            try {
                val buf = ByteArray(8192); val i = s.getInputStream(); val o = c.getOutputStream()
                while (true) {
                    val n = i.read(buf); if (n < 0) break
                    val left = cutDownAfter
                    if (left in 0 until n) { o.write(buf, 0, left.toInt()); o.flush(); cutDownAfter = -1; cuts.incrementAndGet(); break }
                    if (left >= 0) cutDownAfter = left - n
                    o.write(buf, 0, n); o.flush()
                }
            }
            catch (_: IOException) {} finally { runCatching { c.close() }; runCatching { s.close() } }
        }
    }

    fun dropAll() { live.forEach { runCatching { it.close() } }; live.clear() }
    override fun close() { runCatching { ss.close() }; dropAll() }
}

/** (iii) in-flight operations survive a link drop: uploads and downloads resume from the last confirmed offset, tokens expire during outages, the remote loses no key. */
class InFlightTest {
    private val dir = kotlin.io.path.createTempDirectory("inflight").toFile()
    private val clock = FakeClock()
    private val reg = TrustRegistry(MemoryTrustPersistence(), clock::now, tokenTtlMs = 3_600_000)
    private val PHONE = "AA:BB:CC:DD:EE:01"
    private val port = ServerSocket(0).use { it.localPort }
    private var server: ReceiverServer? = null
    private val tokenUses = java.util.concurrent.ConcurrentHashMap<String, AtomicInteger>()
    private val proxy = CutProxy { port }
    private val base get() = "http://127.0.0.1:${proxy.port}"

    init { reg.trust(PHONE, "Galaxy"); startTv() }
    @AfterTest fun tearDown() { proxy.close(); server?.stop(); dir.deleteRecursively() }

    private fun startTv() {
        server?.stop()
        var last: Exception? = null
        repeat(20) {
            try { server = ReceiverServer(VolumeRegistry.single(dir), FakePlayer(), port, pin = "482913",
                tokenAuth = { t -> tokenUses.getOrPut(t ?: "") { AtomicInteger() }.incrementAndGet(); reg.verifyToken(t) }).apply { start(5000, false) }; return }
            catch (e: Exception) { last = e; Thread.sleep(50) }
        }
        throw last!!
    }

    private fun source(n: Int, seed: Int = 1) = Random(seed).nextBytes(n)
    private fun upload(data: ByteArray, name: String, cred: () -> String?, sleep: (Long) -> Unit, states: MutableList<ResumableUpload.State>) =
        ResumableUpload(name, data.size.toLong(), { base }, { off -> ByteArrayInputStream(data, off.toInt(), data.size - off.toInt()) },
            sleep = sleep, credential = cred).run { states += it }

    @Test fun uploadResumesFromTheLastConfirmedByteAfterEveryCut() {
        val data = source(900_000)
        val token = reg.issueToken(PHONE)!!.token
        val cutsAt = ArrayDeque(listOf(2_000L, 60_000L, 333_333L, 400_000L, 1L, 700_000L))     // body bytes allowed before each cut
        val states = ArrayList<ResumableUpload.State>()
        proxy.cutAfter = cutsAt.removeFirst() + 600                                              // + the pre-flight requests
        val offsets = ArrayList<Long>()
        val r = upload(data, "film é.mkv", { token }, { _ -> proxy.cutAfter = cutsAt.removeFirstOrNull()?.let { it + 900 } ?: -1; offsets += File(dir, "film é.mkv.part").length() }, states)
        assertEquals(ResumableUpload.State.Done, r)
        assertContentEquals(data, File(dir, "film é.mkv").readBytes(), "byte for byte, nothing duplicated or lost")
        assertFalse(File(dir, "film é.mkv.part").exists())
        assertTrue(proxy.cuts.get() >= 4, "cuts: ${proxy.cuts.get()}")
        assertEquals(offsets.sorted(), offsets, "the confirmed offset only ever grows: $offsets")
        val waits = states.filterIsInstance<ResumableUpload.State.Waiting>()
        assertTrue(waits.isNotEmpty())
        for (w in waits) LinkMachineTest.assertNoTechnicalWords(w.reason)
        assertFalse(waits.any { it.reason.contains("Exception") }, "no raw exception text")
    }

    @Test fun tvRebootWhileTheTransferRunsThePartFileSurvivesAndTheUploadContinues() {
        val data = source(800_000, 2)
        val token = reg.issueToken(PHONE)!!.token
        val states = ArrayList<ResumableUpload.State>()
        proxy.cutAfter = 300_000
        var restarted = false
        val r = upload(data, "reboot.mkv", { token }, { _ -> if (!restarted) { restarted = true; proxy.down = true; server?.stop(); Thread.sleep(20); startTv(); proxy.down = false } }, states)
        assertEquals(ResumableUpload.State.Done, r)
        assertContentEquals(data, File(dir, "reboot.mkv").readBytes())
        assertTrue(restarted)
    }

    @Test fun phoneRebootResumesTheSameFileFromThePartAndNeverDuplicates() {
        val data = source(500_000, 3); val token = reg.issueToken(PHONE)!!.token
        proxy.cutAfter = 250_000 + 700
        val states = ArrayList<ResumableUpload.State>()
        val first = ResumableUpload("p.mkv", data.size.toLong(), { base }, { off -> ByteArrayInputStream(data, off.toInt(), data.size - off.toInt()) }, cancelled = { proxy.cuts.get() > 0 }, sleep = {}, credential = { token }).run { states += it }
        assertIs<ResumableUpload.State>(first)                               // "the process died here"
        var have = -1L; val f = File(dir, "p.mkv.part")
        repeat(40) { val now = f.length(); if (now == have && now > 0) return@repeat; have = now; Thread.sleep(75) }   // the TV flushes what arrived
        assertTrue(have in 1 until data.size, "partial: $have")
        val second = upload(data, "p.mkv", { token }, {}, states)
        assertEquals(ResumableUpload.State.Done, second)
        assertContentEquals(data, File(dir, "p.mkv").readBytes())
    }

    @Test fun tokenExpiringDuringAnOutageIsReplacedWithoutEverTouchingThePinOrLockingTheTv() {
        val data = source(300_000, 4)
        val old = reg.issueToken(PHONE)!!.token
        var current: String = old
        val states = ArrayList<ResumableUpload.State>()
        proxy.cutAfter = 100_000 + 700
        var sleeps = 0
        val r = upload(data, "late.mkv", { current }, { _ ->
            sleeps++
            if (sleeps == 1) { proxy.down = true; clock.advance(2 * 3600_000L) }                 // outage longer than the token's life
            if (sleeps == 3) { proxy.down = false }
            if (sleeps == 6) current = reg.issueToken(PHONE)!!.token                             // the link layer renewed it by HELLO
        }, states)
        assertEquals(ResumableUpload.State.Done, r, states.last().toString())
        assertContentEquals(data, File(dir, "late.mkv").readBytes())
        assertTrue((tokenUses[old]?.get() ?: 0) <= 4, "the expired token was not hammered: ${tokenUses[old]}")
        assertTrue(states.any { it is ResumableUpload.State.Waiting && it.reason.contains("renouveler") }, "calm explanation while waiting for the new token")
        // the PIN was never sent, so nothing counted against the phone: the PIN still opens the TV
        assertEquals(200, TvClient(base, "482913").info().let { 200 })
    }

    @Test fun refusedPinStopsTheUploadAtOnceInsteadOfLoopingIntoALockout() {
        val data = source(1000)
        var sent = 0
        val states = ArrayList<ResumableUpload.State>()
        val r = ResumableUpload("x.mkv", 1000, { sent++; base }, { ByteArrayInputStream(data) }, sleep = {}, pin = "000000").run { states += it }
        assertIs<ResumableUpload.State.Failed>(r); assertEquals(1, sent, "one try")
        assertEquals("Code de la TV incorrect.", r.reason)
        assertEquals(200, TvClient(base, "482913").info().let { 200 }, "one wrong try is not a lockout")
    }

    @Test fun downloadResumesAfterCutsAndAfterATokenRenewal() {
        val data = source(700_000, 5)
        File(dir, "dl.mkv").writeBytes(data)
        val token = reg.issueToken(PHONE)!!.token
        val got = java.io.ByteArrayOutputStream()
        val states = ArrayList<ResumableDownload.State>()
        var current = token
        proxy.cutDownAfter = 200_000                                                        // cut in the middle of the body
        var sleeps = 0
        val r = ResumableDownload("dl.mkv", { base }, null, { got.size().toLong() }, { off -> require(off == got.size().toLong()); got },
            sleep = { _ -> sleeps++; if (sleeps == 1) { clock.advance(2 * 3600_000L); current = reg.issueToken(PHONE)!!.token; proxy.cutDownAfter = 150_000 } else if (sleeps == 2) proxy.cutDownAfter = 100_000 else proxy.cutDownAfter = -1 },
            credential = { current }).run { states += it }
        assertIs<ResumableDownload.State.Done>(r, r.toString())
        assertContentEquals(data, got.toByteArray())
        assertTrue(states.any { it is ResumableDownload.State.Waiting })
    }

    @Test fun btUploadResumesAtTheExactOffsetTheTvReports() {
        val data = source(400_000, 6)
        val guard = PinGuard("482913")
        var cut = longArrayOf(10_000, 123_456, 350_000).toMutableList()
        val offsetsSeen = ArrayList<Long>()
        fun connect(): Link {
            val c2s = PipedOutputStream(); val tvIn = PipedInputStream(c2s, 1 shl 16)
            val s2c = PipedOutputStream(); val clIn = PipedInputStream(s2c, 1 shl 16)
            thread(isDaemon = true) { try { BtProtocol.serve(dir, tvIn, s2c, guard, PHONE, 0, trusted = { true }) } catch (_: Exception) {} finally { runCatching { s2c.close() } } }
            val limit = cut.removeFirstOrNull()
            return object : Link {
                var written = 0L
                override val input = clIn
                override val output = object : OutputStream() {
                    override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
                    override fun write(b: ByteArray, off: Int, len: Int) { if (limit != null && written + len > limit + 64) { c2s.close(); throw IOException("write failed: broken pipe") }; written += len; c2s.write(b, off, len) }
                    override fun flush() = c2s.flush()
                }
                override fun close() { runCatching { c2s.close() } }
            }
        }
        val up = ResumableBtUpload("bt.mp4", data.size.toLong(), TvAuth.NO_PIN, ::connect, { off -> ByteArrayInputStream(data, off.toInt(), data.size - off.toInt()) }, sleep = {})
        val r = up.run { if (it is ResumableUpload.State.Uploading) offsetsSeen += it.sent }
        assertEquals(ResumableUpload.State.Done, r)
        assertContentEquals(data, File(dir, "bt.mp4").readBytes())
    }

    // ---------------------------------------------------------------------------------------- remote key stream

    private class TvRemote(val filter: SeqFilter = SeqFilter()) {
        val applied = ArrayList<String>()
        var validToken: String = "cbk_ok"
        fun handle(route: String, query: String, token: String?): RemoteReply {
            if (token != validToken) return RemoteReply(401, """{"error":"bad token"}""")
            val p = RemoteWire.parseQuery(query)
            if (route == "state") return RemoteReply(200, "{}")
            if (filter.accept(p["sid"], p["seq"]?.toLong())) applied += p["code"] ?: route
            return RemoteReply(200, "{}")
        }
    }

    @Test fun keyStreamDropsAfterApplyButBeforeTheReplyAreSentOnceAndInOrder() {
        val tv = TvRemote(); val drops = AtomicInteger()
        val sent = AtomicInteger()
        val queue = RemoteQueue(maxAgeMs = 60_000)
        val statuses = ArrayList<RemoteSession.Status>()
        val session = RemoteSession(queue, {
            object : RemoteTransport {
                override val name = "Wi-Fi"
                override fun send(method: String, route: String, query: String): RemoteReply {
                    val r = tv.handle(route, query, "cbk_ok")
                    if (route == "key" && sent.incrementAndGet() % 3 == 0) { drops.incrementAndGet(); throw IOException("connection reset") }   // applied, reply lost
                    return r
                }
                override fun close() {}
            } }, object : RemoteSession.Listener { override fun status(s: RemoteSession.Status) { statuses += s } }, pingMs = 60_000)
        session.start()
        val keys = listOf(RemoteKey.DPAD_UP, RemoteKey.DPAD_DOWN, RemoteKey.DPAD_LEFT, RemoteKey.DPAD_RIGHT, RemoteKey.DPAD_CENTER, RemoteKey.BACK, RemoteKey.DPAD_UP, RemoteKey.DPAD_CENTER)
        keys.forEach { session.key(it) }
        val end = System.currentTimeMillis() + 10_000
        while (queue.size() > 0 && System.currentTimeMillis() < end) Thread.sleep(10)
        session.stop()
        assertEquals(keys.map { it.wire }, tv.applied, "every key exactly once, in order")
        assertTrue(drops.get() >= 2)
        assertFalse(statuses.any { it.link == RemoteSession.Link.BAD_PIN })
    }

    @Test fun anExpiredTokenInTheMiddleOfTheRemoteIsRenewedNotTreatedAsAWrongPin() {
        val tv = TvRemote(); var credential = "cbk_old"; val rejected = AtomicInteger()
        tv.validToken = "cbk_new"
        val queue = RemoteQueue(maxAgeMs = 60_000)
        val statuses = java.util.concurrent.CopyOnWriteArrayList<RemoteSession.Status>()
        val session = RemoteSession(queue, {
            val c = credential
            object : RemoteTransport { override val name = "Wi-Fi"; override fun send(method: String, route: String, query: String) = tv.handle(route, query, c); override fun close() {} } },
            object : RemoteSession.Listener { override fun status(s: RemoteSession.Status) { statuses += s } }, pingMs = 60_000,
            onTokenRejected = { rejected.incrementAndGet(); credential = "cbk_new" })
        session.start()
        session.key(RemoteKey.DPAD_CENTER); session.key(RemoteKey.DPAD_DOWN)
        val end = System.currentTimeMillis() + 10_000
        while (queue.size() > 0 && System.currentTimeMillis() < end) Thread.sleep(10)
        session.stop()
        assertEquals(listOf(RemoteKey.DPAD_CENTER.wire, RemoteKey.DPAD_DOWN.wire), tv.applied, "no key lost, none doubled")
        assertEquals(1, rejected.get())
        assertFalse(statuses.any { it.link == RemoteSession.Link.BAD_PIN }, "never 'code PIN refusé' for an expired token")
        assertTrue(statuses.any { it.message?.contains("renouveler") == true })
    }

    @Test fun aTokenThatNeverBecomesValidEndsInOneClearMessageNotALoop() {
        val tv = TvRemote(); tv.validToken = "never"; val rejected = AtomicInteger()
        val queue = RemoteQueue(maxAgeMs = 60_000)
        val statuses = java.util.concurrent.CopyOnWriteArrayList<RemoteSession.Status>()
        val session = RemoteSession(queue, { object : RemoteTransport { override val name = "Wi-Fi"; override fun send(method: String, route: String, query: String) = tv.handle(route, query, "cbk_x"); override fun close() {} } },
            object : RemoteSession.Listener { override fun status(s: RemoteSession.Status) { statuses += s } }, pingMs = 60_000, onTokenRejected = { rejected.incrementAndGet() }, maxTokenRetries = 3)
        session.start(); session.key(RemoteKey.DPAD_CENTER)
        val end = System.currentTimeMillis() + 15_000
        while (session.isRunning && System.currentTimeMillis() < end) Thread.sleep(20)
        assertFalse(session.isRunning)
        assertEquals(RemoteSession.Link.BAD_PIN, statuses.last().link); assertTrue(statuses.last().message!!.contains("réassociez"))
        assertTrue(rejected.get() in 3..4)
    }

    // ---- w15-05: nothing is taken for « already there » or appended to without proof of the same content ----
    private fun tvDirect() = TvClient("http://127.0.0.1:$port", "482913")

    @Test fun aFinishedHomonymOfAnotherSizeIsNotDone() {
        val old = source(10_000, 7); File(dir, "same.mkv").writeBytes(old)
        val data = source(12_000, 8)
        assertFalse(tvDirect().part("same.mkv", 12_000).done, "a finished file of another size is not this file")
        assertTrue(tvDirect().part("same.mkv", 10_000).done, "the same size is already there")
        val states = ArrayList<ResumableUpload.State>()
        val r = ResumableUpload("same.mkv", data.size.toLong(), { "http://127.0.0.1:$port" }, { off -> ByteArrayInputStream(data, off.toInt(), data.size - off.toInt()) }, sleep = {}, pin = "482913").run { states += it }
        val failed = assertIs<ResumableUpload.State.Failed>(r)
        assertTrue(failed.reason.contains("même nom"), failed.reason)
        assertTrue(states.none { it == ResumableUpload.State.Done }, "never Done without a byte sent")
        assertContentEquals(old, File(dir, "same.mkv").readBytes(), "the old file is untouched")
        val e = assertFailsWith<TvClient.NameTaken> { tvDirect().upload("same.mkv", 0, 12_000, ByteArrayInputStream(data)) {} }
        assertNotNull(e)
        assertContentEquals(old, File(dir, "same.mkv").readBytes())
    }

    @Test fun aPartOfAnotherContentIsNeverResumed() {
        File(dir, "p2.mkv.part").writeBytes(source(4_000, 1)); File(dir, "p2.mkv.meta").writeText("10000")
        val data = source(12_000, 2)
        val before = File(dir, "p2.mkv.part").readBytes()
        assertFailsWith<TvClient.PartOther> { tvDirect().upload("p2.mkv", 4_000, 12_000, ByteArrayInputStream(data, 4_000, 8_000)) {} }
        assertContentEquals(before, File(dir, "p2.mkv.part").readBytes(), "nothing appended to a part of another content")
        assertEquals("PART_OTHER", tvDirect().part("p2.mkv", 12_000).code)
        // the resumable upload drops that partial copy and sends the whole file, never a chimera (fix w15-05: only once that partial copy is idle, the TV
        // refuses /api/reset on a part written less than a minute ago)
        File(dir, "p2.mkv.part").setLastModified(System.currentTimeMillis() - 10 * 60_000)
        val states = ArrayList<ResumableUpload.State>()
        val r = ResumableUpload("p2.mkv", data.size.toLong(), { "http://127.0.0.1:$port" }, { off -> ByteArrayInputStream(data, off.toInt(), data.size - off.toInt()) }, sleep = {}, pin = "482913").run { states += it }
        assertEquals(ResumableUpload.State.Done, r)
        assertContentEquals(data, File(dir, "p2.mkv").readBytes())
    }

    @Test fun aRenamedFileResumesByOriginAndSizeNotRefused() {
        // the strict name is free: no 409 NAME_TAKEN, and a part of the same content still resumes
        val data = source(6_000, 3)
        File(dir, "ok.mkv.part").writeBytes(data.copyOf(2_000)); File(dir, "ok.mkv.meta").writeText("6000")
        val r = ResumableUpload("ok.mkv", data.size.toLong(), { "http://127.0.0.1:$port" }, { off -> ByteArrayInputStream(data, off.toInt(), data.size - off.toInt()) }, sleep = {}, pin = "482913").run { }
        assertEquals(ResumableUpload.State.Done, r)
        assertContentEquals(data, File(dir, "ok.mkv").readBytes())
    }
}
