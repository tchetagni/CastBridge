package castbridge.core

import castbridge.core.ssh.PeerRegistry
import castbridge.core.tunnel.LinkPool
import castbridge.core.tunnel.TcpTunnel
import castbridge.core.tunnel.TunnelGateway
import castbridge.core.tv.*
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.*

/**
 * One shared RFCOMM link per TV ("CastBridge API v2") with a fake Bluetooth transport: slow open, "already opened", late close,
 * a cut in the middle of an answer, two simultaneous requests, a TV that closes a silent link, an old TV without the v2 service.
 */
class BtMuxTunnelTest {
    private val dir = kotlin.io.path.createTempDirectory("btmux").toFile()
    private val peers = PeerRegistry()
    private val httpPort = ServerSocket(0).use { it.localPort }
    private val server = ReceiverServer(VolumeRegistry.single(dir), FakePlayer(), httpPort, profile = TvProfile(minFreeBytes = 0, minFreeAfterTransfer = 0),
        pin = "246810", peers = peers).apply { start(5000, false) }
    private val log = CopyOnWriteArrayList<String>()
    private val closeables = ArrayList<AutoCloseable>()

    @AfterTest fun tearDown() { closeables.reversed().forEach { runCatching { it.close() } }; server.stop(); dir.deleteRecursively() }

    /** A fake TV: two RFCOMM "services" (v1 byte tunnel, v2 shared link) on loopback sockets, with the failure modes of the real stack. */
    private inner class FakeTv(idleMs: Long = 30_000, val hasV2: Boolean = true) {
        val tunnel = TcpTunnel("api", peers, httpPort, 4, idleMs = { idleMs }, handshake = true, log = { log += "tv: $it" }, watchStepMs = 30)
        private val v1 = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1")).also { s -> closeables += AutoCloseable { s.close() } }
        private val v2 = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1")).also { s -> closeables += AutoCloseable { s.close() } }
        val sockets = CopyOnWriteArrayList<Socket>()
        val dials = AtomicInteger(); val dialsV2 = AtomicInteger(); val dialsV1 = AtomicInteger()
        val parallel = AtomicInteger(); val maxParallel = AtomicInteger()
        val dialTimes = CopyOnWriteArrayList<Long>()
        @Volatile var openDelayMs = 0L
        /** The next [failNext] connect() calls fail like Android's "already at opened state". */
        val failNext = AtomicInteger()
        @Volatile var unreachable = false

        init {
            accept(v1) { s -> tunnel.serve("AA:AA:AA:AA:AA:01", "Téléphone", s.getInputStream(), s.getOutputStream(), { s.close() }) }
            accept(v2) { s -> tunnel.serveMux("AA:AA:AA:AA:AA:01", "Téléphone", s.getInputStream(), s.getOutputStream(), { s.close() }) }
        }

        private fun accept(ss: ServerSocket, serve: (Socket) -> Unit) = thread(isDaemon = true) {
            while (!ss.isClosed) {
                val s = runCatching { ss.accept() }.getOrNull() ?: break
                sockets += s
                thread(isDaemon = true) { serve(s) }
            }
        }

        private fun connect(port: Int, v2: Boolean): Link {
            dials.incrementAndGet(); dialTimes += System.currentTimeMillis()
            if (v2) dialsV2.incrementAndGet() else dialsV1.incrementAndGet()
            val p = parallel.incrementAndGet(); maxParallel.accumulateAndGet(p, ::maxOf)
            try {
                if (openDelayMs > 0) Thread.sleep(openDelayMs)
                if (unreachable) throw IOException("read failed, socket might closed or timeout, read ret: -1")
                if (failNext.getAndUpdate { if (it > 0) it - 1 else 0 } > 0) throw IOException("RFCOMM_CreateConnection failed: already at opened state")
                if (v2 && !hasV2) throw IOException("Service discovery failed")
                val c = Socket("127.0.0.1", port)
                return object : Link { override val input = c.getInputStream(); override val output = c.getOutputStream(); override fun close() { c.close() } }
            } finally { parallel.decrementAndGet() }
        }

        fun dialV1() = connect(v1.localPort, false)
        fun dialV2() = connect(v2.localPort, true)
        /** The radio drops every link at once. */
        fun cut() { sockets.forEach { runCatching { it.close() } }; sockets.clear() }
        fun links() = tunnel.active().size
    }

    private fun phone(tv: FakeTv, shared: Boolean = true, gapMs: Long = 200, ping: Long = 100_000, idleMs: Long = 120_000): Pair<TunnelGateway, String> {
        val gw = TunnelGateway("api", true, tv::dialV1, if (shared) tv::dialV2 else null, dialTimeoutMs = 5000, idleMs = idleMs, handshakeTimeoutMs = 3000,
            log = { log += "phone: $it" }, watchStepMs = 50, gapMs = gapMs, backoffMs = listOf(150, 300), pingEveryMs = ping)
        val port = gw.start("127.0.0.1", 0); closeables += AutoCloseable { gw.stop() }
        return gw to "http://127.0.0.1:$port"
    }

    private fun code(url: String, pin: String? = "246810"): Int = (URL(url).openConnection() as HttpURLConnection).run {
        connectTimeout = 5000; readTimeout = 10_000
        if (pin != null) setRequestProperty("X-CB-Pin", pin)
        setRequestProperty("Connection", "close")
        try { responseCode } finally { disconnect() }
    }

    private fun eventually(what: String, ms: Long = 5000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + ms
        while (!cond()) { if (System.currentTimeMillis() > end) fail("timeout: $what"); Thread.sleep(20) }
    }

    @Test fun manyRequestsReuseOneLinkAndTheServerStillChecksThePin() {
        val tv = FakeTv(); val (gw, b) = phone(tv)
        repeat(8) { assertEquals(200, code("$b/api/info")) }
        assertEquals(401, code("$b/api/info", pin = "000000"), "the TV's server still decides")
        assertEquals(1, tv.dials.get(), "one connect() for nine HTTP connections: ${log.filter { it.contains("open") }}")
        assertEquals(1, tv.dialsV2.get()); assertEquals(0, tv.dialsV1.get())
        assertEquals(1, gw.state.links); assertEquals(1, gw.state.linksOpened); assertEquals(true, gw.state.shared)
        assertEquals(1, tv.links(), "the TV sees one link")
    }

    @Test fun twoSimultaneousRequestsNeverConnectInParallel() {
        val tv = FakeTv(); tv.openDelayMs = 400
        val (_, b) = phone(tv)
        val res = (1..4).map { thread { assertEquals(200, code("$b/api/info")) } }
        res.forEach { it.join(15_000) }
        assertEquals(1, tv.dials.get(), "the lock makes the others wait for the one connect()")
        assertEquals(1, tv.maxParallel.get())
    }

    @Test fun slowOpenIsWaitedForNotRepeated() {
        val tv = FakeTv(); tv.openDelayMs = 700
        val (_, b) = phone(tv)
        assertEquals(200, code("$b/api/info")); assertEquals(200, code("$b/api/info"))
        assertEquals(1, tv.dials.get())
    }

    @Test fun alreadyOpenedIsRetriedWithAGapAndNeverInATightLoop() {
        val tv = FakeTv(); tv.failNext.set(2)
        val (gw, b) = phone(tv)
        assertEquals(200, code("$b/api/info"))
        assertEquals(3, tv.dials.get())
        val gaps = tv.dialTimes.zipWithNext { a, c -> c - a }
        assertTrue(gaps[0] >= 100 && gaps[1] >= 200, "growing waits between attempts: $gaps")
        assertEquals(1, tv.maxParallel.get())
        assertEquals(1, gw.state.linksOpened)
    }

    @Test fun threeFailuresGiveAClearStateThenCallersFailAtOnceWithoutDialling() {
        val tv = FakeTv(); tv.unreachable = true
        val (gw, b) = phone(tv)
        runCatching { code("$b/api/info") }                // the client just sees a closed connection or an error
        val n = tv.dials.get()
        assertTrue(n in 3..6, "bounded attempts (v2 x3 + one single-use probe): $n")
        assertEquals("Bluetooth : la TV ne répond pas", gw.state.message?.substringBefore(" ("))
        runCatching { code("$b/api/info") }
        assertEquals(n, tv.dials.get(), "within the cool-down nobody dials again")
        tv.unreachable = false
        Thread.sleep(8200)
        assertEquals(200, code("$b/api/info"), "after the cool-down the TV is reachable again")
    }

    @Test fun cutInTheMiddleOfAnAnswerThenTheNextRequestReopensAfterTheGap() {
        val tv = FakeTv(); val (gw, b) = phone(tv, gapMs = 400)
        assertEquals(200, code("$b/api/info"))
        tv.cut()
        eventually("phone notices") { gw.state.links == 0 }
        val t0 = System.currentTimeMillis()
        assertEquals(200, code("$b/api/info"))
        assertEquals(2, tv.dials.get())
        assertTrue(tv.dialTimes[1] - t0 >= -50 && tv.dialTimes[1] - tv.dialTimes[0] >= 400, "reopened no sooner than the gap")
        assertNotNull(gw.state.lastClose); assertEquals(2, gw.state.linksOpened)
    }

    @Test fun aTvThatClosesASilentLinkIsReconnectedOnDemand() {
        val tv = FakeTv(idleMs = 400)                     // the real TV: 30 s, scaled down
        val (gw, b) = phone(tv, ping = 100_000)           // no keep-alive in this test
        assertEquals(200, code("$b/api/info"))
        eventually("TV closed the silent link", 3000) { tv.links() == 0 }
        assertEquals(200, code("$b/api/info"))
        assertEquals(2, tv.dials.get()); assertEquals(2, gw.state.linksOpened)
    }

    @Test fun keepAlivePingsKeepTheLinkOpenPastTheTvIdleLimit() {
        val tv = FakeTv(idleMs = 500)
        val (gw, b) = phone(tv, ping = 100)
        gw.keepAlive = { true }
        assertEquals(200, code("$b/api/info"))
        Thread.sleep(1800)                                // 3.6 x the TV's limit with nothing but pings
        assertEquals(1, tv.links(), "the TV still has the link: ${log.takeLast(6)}")
        assertEquals(200, code("$b/api/info"))
        assertEquals(1, tv.dials.get(), "no reconnection at all")
    }

    @Test fun anOldTvWithoutTheSharedServiceStillWorksOneLinkPerRequest() {
        val tv = FakeTv(hasV2 = false)
        val (gw, b) = phone(tv, gapMs = 100)
        assertEquals(200, code("$b/api/info")); assertEquals(200, code("$b/api/info")); assertEquals(200, code("$b/api/info"))
        assertEquals(1, tv.dialsV2.get(), "the missing service is probed once, then remembered")
        assertEquals(3, tv.dialsV1.get())
        assertEquals(1, tv.maxParallel.get())
        assertEquals(false, gw.state.shared)
    }

    @Test fun anOldPhoneStillUsesTheSingleUseServiceOfTheNewTv() {
        val tv = FakeTv()
        val (_, b) = phone(tv, shared = false, gapMs = 100)
        assertEquals(200, code("$b/api/info")); assertEquals(200, code("$b/api/hello"))
        assertEquals(0, tv.dialsV2.get()); assertEquals(2, tv.dialsV1.get())
    }

    @Test fun aLargeAnswerAndAConcurrentSmallOneShareTheLink() {
        val tv = FakeTv(); val (_, b) = phone(tv)
        java.io.File(dir, "big.bin").writeBytes(ByteArray(3 * 1024 * 1024) { it.toByte() })
        val big = thread { val c = URL("$b/stream/big.bin").openConnection() as HttpURLConnection; c.setRequestProperty("X-CB-Pin", "246810"); assertEquals(3 * 1024 * 1024, c.inputStream.readBytes().size) }
        Thread.sleep(100)
        assertEquals(200, code("$b/api/hello"))
        big.join(30_000); assertFalse(big.isAlive)
        assertEquals(1, tv.dials.get())
    }

    @Test fun diagnosticCountsLinksAndSaysWhyTheLastOneClosed() {
        val tv = FakeTv(); val (gw, b) = phone(tv, gapMs = 100)
        assertEquals(200, code("$b/api/info")); tv.cut()
        eventually("closed") { gw.state.links == 0 }
        assertTrue(gw.state.lastClose!!.isNotBlank(), "a reason is kept: ${gw.state.lastClose}")
        assertEquals(1, gw.state.linksOpened)
    }
}
