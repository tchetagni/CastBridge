package castbridge.core

import castbridge.core.gateway.*
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import kotlin.concurrent.thread
import kotlin.random.Random
import kotlin.test.*

class GatewayTest {
    /** A byte link like RFCOMM: a connected socket pair (java.io pipes break when a writer thread ends). */
    private fun link(): Pair<Mux, Mux> {
        ServerSocket(0).use { ss ->
            val a = Socket("127.0.0.1", ss.localPort); val b = ss.accept()
            return Mux(a.getInputStream(), a.getOutputStream()) to Mux(b.getInputStream(), b.getOutputStream())
        }
    }

    /** "Internet": echoes back sha256(received) after the client half-closes, and streams [download] bytes on "GET". */
    private val download = Random(3).nextBytes(3_000_000)
    private val internet = ServerSocket(0).also { ss ->
        thread(isDaemon = true) {
            while (!ss.isClosed) {
                val c = runCatching { ss.accept() }.getOrNull() ?: break
                thread(isDaemon = true) {
                    c.use {
                        val cmd = ByteArray(3).also { b -> it.getInputStream().read(b) }
                        if (String(cmd) == "GET") it.getOutputStream().apply { write(download); flush() }
                        else {
                            val md = MessageDigest.getInstance("SHA-256"); md.update(cmd)
                            val buf = ByteArray(65536)
                            while (true) { val n = it.getInputStream().read(buf); if (n < 0) break; md.update(buf, 0, n) }
                            it.getOutputStream().write(md.digest())
                        }
                    }
                }
            }
        }
    }

    private val entry = Entry({ it == "123456" }, port = 0)
    private var socksPort = 0

    @BeforeTest fun setUp() { socksPort = entry.startSocks() }
    @AfterTest fun tearDown() { entry.stop(); internet.close() }

    private fun attach(pin: String = "123456"): Thread {
        val (tv, phone) = link()
        thread(isDaemon = true) { entry.attach(tv, "phone") }
        return thread(isDaemon = true) { runCatching { Exit(phone, pin).run() } }
    }

    private fun viaTv(): Socket = Socket(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort))).apply {
        soTimeout = 20_000; connect(InetSocketAddress("127.0.0.1", internet.localPort), 20_000)
    }

    private fun waitConnected() { repeat(100) { if (entry.connected) return; Thread.sleep(20) }; fail("gateway never attached") }

    @Test fun downloadAndUploadThroughThePhone() {
        attach(); waitConnected()
        viaTv().use { s ->
            s.getOutputStream().write("GET".toByteArray())
            val got = s.getInputStream().readBytes()
            assertContentEquals(download, got)
        }
        val up = Random(4).nextBytes(2_000_000)
        viaTv().use { s ->
            s.getOutputStream().write(up); s.shutdownOutput()
            val digest = s.getInputStream().readBytes()
            assertContentEquals(MessageDigest.getInstance("SHA-256").digest(up), digest)
        }
    }

    @Test fun manyParallelStreamsDoNotBlockEachOther() {
        attach(); waitConnected()
        val results = (1..6).map {
            val r = arrayOfNulls<ByteArray>(1)
            thread { viaTv().use { s -> s.getOutputStream().write("GET".toByteArray()); r[0] = s.getInputStream().readBytes() } } to r
        }
        results.forEach { (t, r) -> t.join(30_000); assertContentEquals(download, r[0]) }
    }

    @Test fun wrongPinIsRefusedAndNoGatewayFailsFast() {
        attach(pin = "000000"); Thread.sleep(300)
        assertFalse(entry.connected)
        assertFails { viaTv().use { it.getOutputStream().write("GET".toByteArray()); it.getInputStream().read() } }
    }

    @Test fun unreachableTargetReportsAnError() {
        attach(); waitConnected()
        val dead = ServerSocket(0).let { val p = it.localPort; it.close(); p }
        assertFails {
            Socket(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort))).use { it.connect(InetSocketAddress("127.0.0.1", dead), 10_000) }
        }
    }

    @Test fun diagnosticsRunOnThePhoneAndStreamBack() {
        val (tv, phone) = link()
        thread(isDaemon = true) { entry.attach(tv, "phone") }
        thread(isDaemon = true) { runCatching { Exit(phone, "123456", diag = { k, h, l -> l("$k 1 $h"); l("$k 2 $h") }).run() } }
        waitConnected()
        val lines = mutableListOf<String>()
        assertTrue(entry.diag("trace", "exemple.cm") { lines += it })
        assertEquals(listOf("trace 1 exemple.cm", "trace 2 exemple.cm"), lines)
        assertFails { entry.diag("ping", "a; rm -rf /") { } }
        assertFalse(Gw.validHost("x..y")); assertTrue(Gw.validHost("8.8.8.8")); assertTrue(Gw.validHost("2001:4860::8888"))
        assertNotNull(entry.tcpPing("127.0.0.1", internet.localPort))
    }

    @Test fun targetEncoding() {
        val t = GwTarget("exemple.cm", 443)
        assertEquals(t, GwTarget.decode(t.encode()))
    }
}
