package castbridge.core

import castbridge.core.ssh.PeerRegistry
import castbridge.core.tunnel.AttributedSocket
import castbridge.core.tunnel.BtDialException
import castbridge.core.tunnel.TcpTunnel
import castbridge.core.tunnel.TunnelGateway
import castbridge.core.tunnel.TunnelStatus
import castbridge.core.tv.*
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import kotlin.concurrent.thread
import kotlin.random.Random
import kotlin.test.*

/**
 * The TV's HTTP API through the Bluetooth tunnel, with TCP sockets standing in for RFCOMM: a "device" is a local listener whose
 * every connection is served by the TV-side [TcpTunnel] as that Bluetooth address; the phone side is a real [TunnelGateway].
 * The HTTP server is the real [ReceiverServer]: PIN, lockout and uploads behave exactly as on Wi-Fi.
 */
class BtApiTunnelTest {
    private val dir = kotlin.io.path.createTempDirectory("btapi").toFile()
    private val peers = PeerRegistry()
    private val httpPort = ServerSocket(0).use { it.localPort }
    private val server = ReceiverServer(VolumeRegistry.single(dir), FakePlayer(), httpPort, profile = TvProfile(minFreeBytes = 0, minFreeAfterTransfer = 0),
        pin = "246810", peers = peers).apply { start(5000, false) }
    private val tvLog = java.util.concurrent.CopyOnWriteArrayList<String>()
    private val closeables = ArrayList<AutoCloseable>()

    @AfterTest fun tearDown() { closeables.reversed().forEach { runCatching { it.close() } }; server.stop(); dir.deleteRecursively() }

    private fun tunnel(max: Int = 4, target: Int = httpPort, idleMs: () -> Long = { 30_000 }, evictIdleMs: Long = 60_000,
                       admit: (String) -> String? = { null }) =
        TcpTunnel("api", peers, target, max, idleMs = idleMs, evictIdleMs = evictIdleMs, handshake = true, admit = admit, log = { tvLog += it }, watchStepMs = 50)

    /** A fake Bluetooth device [addr] talking to [tunnel]; returns the function a phone uses to "dial" the TV's service. */
    private fun rfcomm(tunnel: TcpTunnel, addr: String): () -> Link {
        val ss = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1")).also { s -> closeables += AutoCloseable { s.close() } }
        thread(isDaemon = true) {
            while (!ss.isClosed) {
                val s = runCatching { ss.accept() }.getOrNull() ?: break
                thread(isDaemon = true) { tunnel.serve(addr, "Téléphone $addr", s.getInputStream(), s.getOutputStream(), { s.close() }) }
            }
        }
        return {
            val c = Socket("127.0.0.1", ss.localPort)
            object : Link { override val input = c.getInputStream(); override val output = c.getOutputStream(); override fun close() { c.close() } }
        }
    }

    /** The phone's gateway for one device; returns its local base URL. */
    private fun phone(dial: () -> Link, handshake: Boolean = true, idleMs: Long = 120_000, dialTimeoutMs: Long = 5000): Pair<TunnelGateway, String> {
        val gw = TunnelGateway("api", handshake, dial, dialTimeoutMs = dialTimeoutMs, idleMs = idleMs, handshakeTimeoutMs = 3000, log = { tvLog += "phone: $it" }, watchStepMs = 50)
        val port = gw.start("127.0.0.1", 0); closeables += AutoCloseable { gw.stop() }
        return gw to "http://127.0.0.1:$port"
    }

    private fun code(url: String, pin: String? = null, range: String? = null): Int = (URL(url).openConnection() as HttpURLConnection).run {
        connectTimeout = 5000; readTimeout = 10_000
        if (pin != null) setRequestProperty("X-CB-Pin", pin)
        if (range != null) setRequestProperty("Range", range)
        try { responseCode } finally { disconnect() }
    }

    private fun eventually(what: String, ms: Long = 5000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + ms
        while (!cond()) { if (System.currentTimeMillis() > end) fail("timeout: $what"); Thread.sleep(20) }
    }

    @Test fun httpApiThroughTheTunnelIsAnsweredByTheRealServer() {
        val (_, b) = phone(rfcomm(tunnel(), "AA:AA:AA:AA:AA:01"))
        assertEquals(200, code("$b/api/hello"))
        assertEquals(401, code("$b/api/info"), "the server still demands the PIN: nothing is bypassed")
        assertEquals(200, code("$b/api/info", pin = "246810"))
        assertEquals(200, TvClient(b, "246810").info().let { 200 })
    }

    @Test fun pinLockoutIsPerBluetoothDevice() {
        val t = tunnel()
        val (_, a) = phone(rfcomm(t, "AA:AA:AA:AA:AA:01")); val (_, b) = phone(rfcomm(t, "BB:BB:BB:BB:BB:02"))
        repeat(4) { assertEquals(401, code("$a/api/info", pin = "000000")) }
        // 5th failure locks device A ...
        assertEquals(401, code("$a/api/info", pin = "000000"))
        assertEquals(401, code("$a/api/info", pin = "246810"), "device A is locked, even with the right PIN")
        // ... and nobody else: not device B, not a Wi-Fi (direct) client, not the same device's other services
        assertEquals(200, code("$b/api/info", pin = "246810"))
        assertEquals(200, code("http://127.0.0.1:$httpPort/api/info", pin = "246810"))
        assertEquals(PeerRegistry().keyOfAddress("240.77.0.9"), "240.77.0.9", "unknown addresses are left as they are")
    }

    @Test fun anotherDeviceCannotUseTheLockedDevicesIdentity() {
        // Wi-Fi failures do not lock Bluetooth devices either: separate keys
        val (_, a) = phone(rfcomm(tunnel(), "AA:AA:AA:AA:AA:01"))
        repeat(5) { code("http://127.0.0.1:$httpPort/api/info", pin = "000000") }
        assertEquals(401, code("http://127.0.0.1:$httpPort/api/info", pin = "246810"), "direct 127.0.0.1 is locked")
        assertEquals(200, code("$a/api/info", pin = "246810"), "the Bluetooth device is not")
    }

    @Test fun attributionOnlyForRegisteredLoopbackLinks() {
        val reg = PeerRegistry()
        reg.register(41000, "bt:AA")
        val lo = InetAddress.getByName("127.0.0.1")
        val v1 = reg.virtualAddress(InetSocketAddress(lo, 41000))!!
        assertFalse(v1.isLoopbackAddress)
        assertEquals("bt:AA", reg.keyOfAddress(v1.hostAddress))
        assertEquals(v1, reg.virtualAddress(InetSocketAddress(lo, 41000)), "stable per device")
        reg.register(41001, "bt:BB")
        assertNotEquals(v1, reg.virtualAddress(InetSocketAddress(lo, 41001)))
        assertNull(reg.virtualAddress(InetSocketAddress(lo, 41002)), "unregistered port: not attributed")
        assertNull(reg.virtualAddress(InetSocketAddress(InetAddress.getByName("192.168.1.5"), 41000)), "only loopback")
        val real = Socket()
        assertSame(real, AttributedSocket.of(real, reg), "not connected: untouched")
    }

    @Test fun largeResumableUploadAndRangeStreamingThroughTheTunnel() {
        val (_, b) = phone(rfcomm(tunnel(), "AA:AA:AA:AA:AA:01"))
        val data = Random(7).nextBytes(6 * 1024 * 1024)
        // first try breaks in the middle (the phone's source fails): the TV keeps the .part file
        val tv = TvClient(b, "246810")
        assertFails { tv.upload("big.bin", 0, data.size.toLong(), object : java.io.InputStream() {
            var n = 0
            override fun read(): Int = throw IOException("x")
            override fun read(buf: ByteArray, off: Int, len: Int): Int { if (n >= 2_000_000) throw IOException("link lost"); val r = minOf(len, 2_000_000 - n); System.arraycopy(data, n, buf, off, r); n += r; return r }
        }) {} }
        val res = ResumableUpload("big.bin", data.size.toLong(), { b }, { off -> ByteArrayInputStream(data, off.toInt(), data.size - off.toInt()) }, sleep = {}, pin = "246810").run {}
        assertEquals(ResumableUpload.State.Done, res)
        assertContentEquals(data, File(dir, "big.bin").readBytes())
        // Range streaming
        val c = URL("$b/stream/big.bin").openConnection() as HttpURLConnection
        c.setRequestProperty("X-CB-Pin", "246810"); c.setRequestProperty("Range", "bytes=1000-1999")
        assertEquals(206, c.responseCode)
        assertContentEquals(data.copyOfRange(1000, 2000), c.inputStream.readBytes())
    }

    @Test fun slotsAreLimitedAndFreedAfterAbandonedLinks() {
        val t = tunnel(max = 2)
        val dial = rfcomm(t, "AA:AA:AA:AA:AA:01")
        val (gw, b) = phone(dial)
        val idle = (1..2).map { Socket("127.0.0.1", b.substringAfterLast(':').toInt()) }
        eventually("two links open") { t.active().size == 2 }
        // a third request is refused by the TV, and the phone says why
        assertTrue(runCatching { code("$b/api/hello") }.getOrDefault(-1) != 200)
        eventually("message") { gw.state.message?.contains("trop de liaisons") == true }
        assertTrue(tvLog.any { "refused" in it && "occupées" in it }, tvLog.toString())
        assertNotNull(t.lastError)
        // the abandoned links are closed: slots come back, no stuck slot
        idle.forEach { it.close() }
        eventually("slots freed") { t.active().isEmpty() }
        assertEquals(0, peers.size())
        assertEquals(200, code("$b/api/hello"))
    }

    @Test fun aSilentLinkIsEvictedWhenTheTunnelIsFull() {
        val t = tunnel(max = 1, evictIdleMs = 300)
        val (_, b) = phone(rfcomm(t, "AA:AA:AA:AA:AA:01"))
        val port = b.substringAfterLast(':').toInt()
        val zombie = Socket("127.0.0.1", port)
        eventually("zombie open") { t.active().size == 1 }
        assertNotEquals(200, runCatching { code("$b/api/hello") }.getOrDefault(-1), "full and not yet stale")
        Thread.sleep(400)
        assertEquals(200, code("$b/api/hello"), "the silent link made room")
        assertTrue(tvLog.any { "closed to make room" in it })
        zombie.close()
    }

    @Test fun idleWatchdogClosesASilentLink() {
        val t = tunnel(idleMs = { 300 })
        val (_, b) = phone(rfcomm(t, "AA:AA:AA:AA:AA:01"))
        val s = Socket("127.0.0.1", b.substringAfterLast(':').toInt()).apply { soTimeout = 5000 }
        eventually("open") { t.active().size == 1 }
        assertEquals(-1, s.getInputStream().read(), "closed by the watchdog")
        eventually("freed") { t.active().isEmpty() }
        assertTrue(t.lastError!!.contains("sans échange"), t.lastError)
    }

    @Test fun anActiveTransferIsNotCutByTheWatchdog() {
        val t = tunnel(idleMs = { 500 })
        val (_, b) = phone(rfcomm(t, "AA:AA:AA:AA:AA:01"))
        val data = Random(3).nextBytes(1_500_000)
        val res = ResumableUpload("a.bin", data.size.toLong(), { b }, { off -> ByteArrayInputStream(data, off.toInt(), data.size - off.toInt()) }, sleep = {}, pin = "246810").run {}
        assertEquals(ResumableUpload.State.Done, res)
    }

    @Test fun everyFailureHasAFrenchMessageAndALogLine() {
        // 1. the phone cannot open the RFCOMM link
        val (gw1, b1) = phone({ throw BtDialException("TV injoignable en Bluetooth (appareils appairés ?)") })
        assertNotEquals(200, runCatching { code("$b1/api/hello") }.getOrDefault(-1))
        eventually("dial message") { gw1.state.message?.contains("injoignable") == true }
        // 2. the dial hangs: abandoned after the timeout, late link closed
        val late = java.util.concurrent.atomic.AtomicBoolean()
        val (gw2, b2) = phone({ Thread.sleep(600); object : Link { override val input get() = ByteArrayInputStream(ByteArray(0)); override val output = java.io.ByteArrayOutputStream(); override fun close() { late.set(true) } } }, dialTimeoutMs = 200)
        assertNotEquals(200, runCatching { code("$b2/api/hello") }.getOrDefault(-1))
        eventually("timeout message") { gw2.state.message?.contains("ne répond pas") == true }
        eventually("late link closed") { late.get() }
        // 3. the TV's HTTP server is down
        val down = ServerSocket(0).use { it.localPort }
        val (gw3, b3) = phone(rfcomm(tunnel(target = down), "AA:AA:AA:AA:AA:01"))
        assertNotEquals(200, runCatching { code("$b3/api/hello") }.getOrDefault(-1))
        eventually("target message") { gw3.state.message?.contains(TunnelStatus.describe(TunnelStatus.TARGET_DOWN)) == true }
        // 4. device not admitted (switch off, not trusted): TV says why, logs it, never connects to the server
        val t4 = tunnel(admit = { "API par Bluetooth coupée sur la TV" })
        val (gw4, b4) = phone(rfcomm(t4, "CC:CC:CC:CC:CC:03"))
        assertNotEquals(200, runCatching { code("$b4/api/hello") }.getOrDefault(-1))
        eventually("refused message") { gw4.state.message?.contains("n'autorise pas") == true }
        assertEquals("API par Bluetooth coupée sur la TV", t4.lastError)
        assertTrue(tvLog.any { "refused" in it && "coupée" in it })
        assertEquals(0, t4.active().size)
    }

    @Test fun rawServiceClosedByTheTvBeforeAnyDataIsExplained() {
        // the observed SSH failure: the phone's RFCOMM link opens, the TV closes it at once (here: target down)
        val down = ServerSocket(0).use { it.localPort }
        val t = TcpTunnel("ssh", peers, down, 2, handshake = false, log = { tvLog += it })
        val (gw, b) = phone(rfcomm(t, "AA:AA:AA:AA:AA:01"), handshake = false)
        val s = Socket("127.0.0.1", b.substringAfterLast(':').toInt()).apply { soTimeout = 5000 }
        assertEquals(-1, s.getInputStream().read())
        eventually("explained") { gw.state.message?.contains("avant toute donnée") == true }
        assertTrue(tvLog.any { "refused" in it && "SSH" in it || "refused" in it }, tvLog.toString())
        assertEquals(0, gw.state.active)
    }

    @Test fun uploadsOfSeveralDevicesRunConcurrently() {
        val t = tunnel()
        val a = phone(rfcomm(t, "AA:AA:AA:AA:AA:01")).second; val b = phone(rfcomm(t, "BB:BB:BB:BB:BB:02")).second
        val d1 = Random(1).nextBytes(800_000); val d2 = Random(2).nextBytes(800_000)
        val results = listOf(a to ("one.bin" to d1), b to ("two.bin" to d2)).map { (base, nd) ->
            val r = arrayOfNulls<Any>(1)
            val th = thread { r[0] = ResumableUpload(nd.first, nd.second.size.toLong(), { base }, { off -> ByteArrayInputStream(nd.second, off.toInt(), nd.second.size - off.toInt()) }, sleep = {}, pin = "246810").run {} }
            th to r
        }
        results.forEach { it.first.join(30_000) }
        assertTrue(results.all { it.second[0] == ResumableUpload.State.Done }, results.map { it.second[0] }.toString())
        assertContentEquals(d1, File(dir, "one.bin").readBytes()); assertContentEquals(d2, File(dir, "two.bin").readBytes())
    }
}
