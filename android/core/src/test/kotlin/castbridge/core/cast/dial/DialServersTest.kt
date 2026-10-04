package castbridge.core.cast.dial

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Random
import kotlin.test.*

private class FakeLauncher(var installed: Boolean = true) : DialLauncher {
    val launched = ArrayList<String>()
    var stops = 0
    private var run = false
    override fun installed() = installed
    override fun running() = run
    override fun launch(url: String): Boolean { launched += url; run = true; return true }
    override fun stop(): Boolean { stops++; run = false; return true }
}

/** Serveur HTTP DIAL réel sur 127.0.0.1 (port libre) et client en sockets bruts. */
class DialHttpServerTest {
    private val lo = InetAddress.getByName("127.0.0.1")
    private lateinit var srv: DialHttpServer
    private val launcher = FakeLauncher()
    private val logs = ArrayList<String>()
    private var now = 0L
    private val port get() = srv.localPort

    @BeforeTest fun up() {
        lateinit var self: DialHttpServer
        val router = DialRouter("dev1", "Acme", "TV-1", { setOf("127.0.0.1:${self.localPort}") }, launcher, LaunchRateLimiter(6, 60_000) { now }) { logs += it }
        self = DialHttpServer(router, 0, lo)
        srv = self
        assertTrue(srv.start() > 0)
    }
    @AfterTest fun down() { srv.stop() }

    class Resp(val raw: String) {
        val status = raw.substringBefore("\r\n").split(' ').getOrNull(1)?.toIntOrNull() ?: -1
        val body = raw.substringAfter("\r\n\r\n", "")
        fun header(n: String) = raw.substringBefore("\r\n\r\n").split("\r\n").drop(1).firstOrNull { it.startsWith("$n:", true) }?.substringAfter(':')?.trim()
    }

    private fun send(raw: String, readTimeout: Int = 3000): Resp {
        Socket(lo, port).use { s ->
            s.soTimeout = readTimeout
            s.getOutputStream().apply { write(raw.toByteArray(Charsets.ISO_8859_1)); flush() }
            val text = try { String(s.getInputStream().readBytes(), Charsets.UTF_8) } catch (e: java.net.SocketTimeoutException) { "" }
            return Resp(text)
        }
    }
    private fun req(method: String, path: String, host: String? = "127.0.0.1:$port", origin: String? = null, body: String? = null, ct: String? = "text/plain; charset=utf-8", extra: String = ""): Resp {
        val sb = StringBuilder("$method $path HTTP/1.1\r\n")
        if (host != null) sb.append("Host: $host\r\n")
        if (origin != null) sb.append("Origin: $origin\r\n")
        if (body != null) { if (ct != null) sb.append("Content-Type: $ct\r\n"); sb.append("Content-Length: ${body.length}\r\n") }
        sb.append(extra).append("\r\n")
        if (body != null) sb.append(body)
        return send(sb.toString())
    }

    @Test fun deviceDescriptionCarriesApplicationUrl() {
        val r = req("GET", "/dd.xml")
        assertEquals(200, r.status)
        assertEquals("http://127.0.0.1:$port/apps/", r.header("Application-URL"))
        assertTrue("<friendlyName>${DialRules.friendlyName("dev1")}</friendlyName>" in r.body)
        assertTrue(r.header("Content-Type")!!.startsWith("application/xml"))
    }

    @Test fun statusThenLaunchThenRunningThenStop() {
        val st = req("GET", "/apps/YouTube")
        assertEquals(200, st.status); assertTrue("<state>stopped</state>" in st.body)
        val l = req("POST", "/apps/YouTube", body = "pairingCode=SECRETCODE&theme=cl&v=2")
        assertEquals(201, l.status, l.raw)
        assertEquals("http://127.0.0.1:$port/apps/YouTube/run", l.header("Location"))
        assertEquals(listOf("https://www.youtube.com/tv?pairingCode=SECRETCODE&theme=cl&v=2"), launcher.launched)
        assertTrue(logs.none { "SECRETCODE" in it }, "le code d'appairage n'est jamais journalisé : $logs")
        assertTrue(logs.any { "pairingCode=***" in it })
        assertTrue("<state>running</state>" in req("GET", "/apps/YouTube").body)
        assertEquals(200, req("GET", "/apps/YouTube/run").status)
        assertEquals(200, req("DELETE", "/apps/YouTube/run").status)
        assertEquals(1, launcher.stops)
        assertEquals(404, req("DELETE", "/apps/YouTube/run").status, "plus d'instance")
        assertTrue("<state>stopped</state>" in req("GET", "/apps/YouTube").body)
    }

    @Test fun onlyTheYouTubeEntryExists() {
        for (p in listOf("/apps/Netflix", "/apps/youtube", "/apps/", "/apps/..%2F", "/apps/YouTube2", "/apps/YouTube/other", "/other", "/"))
            assertEquals(404, req("GET", p).status, p)
        assertEquals(404, req("POST", "/apps/Netflix", body = "a=b").status)
        assertEquals(404, req("POST", "/apps/Settings", body = "a=b").status)
        assertTrue(launcher.launched.isEmpty())
    }

    @Test fun missingYouTubeTvAnswers404AndLogsInFrench() {
        launcher.installed = false
        assertEquals(404, req("GET", "/apps/YouTube").status)
        assertEquals(404, req("POST", "/apps/YouTube", body = "a=b").status)
        assertTrue(logs.any { "Application YouTube TV introuvable sur cette TV" in it })
        assertTrue(launcher.launched.isEmpty())
    }

    @Test fun wrongHostIsRefused() {
        assertEquals(403, req("GET", "/dd.xml", host = "evil.example:$port").status)
        assertEquals(403, req("GET", "/dd.xml", host = "127.0.0.1").status)
        assertEquals(403, req("GET", "/dd.xml", host = null).status)
        assertEquals(403, req("POST", "/apps/YouTube", host = "attacker.test", body = "a=b").status)
        assertTrue(launcher.launched.isEmpty())
    }

    @Test fun anyOtherOriginIsRefusedEverywhere() {
        for (o in listOf("http://evil.example", "https://www.youtube.com", "null", "package:com.evil"))
            for ((m, p) in listOf("GET" to "/dd.xml", "GET" to "/apps/YouTube", "POST" to "/apps/YouTube", "DELETE" to "/apps/YouTube/run"))
                assertEquals(403, req(m, p, origin = o, body = if (m == "POST") "a=b" else null).status, "$m $p $o")
        assertTrue(launcher.launched.isEmpty() && launcher.stops == 0)
        assertEquals(201, req("POST", "/apps/YouTube", origin = "package:com.google.android.youtube", body = "a=b").status)
    }

    @Test fun bodyIsCapped() {
        assertEquals(413, req("POST", "/apps/YouTube", body = "a=" + "x".repeat(4100)).status)
        // Content-Length annoncé énorme : refusé sans lire le corps.
        assertEquals(413, send("POST /apps/YouTube HTTP/1.1\r\nHost: 127.0.0.1:$port\r\nContent-Type: text/plain\r\nContent-Length: 99999999\r\n\r\n").status)
        assertEquals(413, req("POST", "/apps/YouTube", body = "a=" + "x".repeat(1100)).status, "plus de 1 Ko de paramètres")
        assertTrue(launcher.launched.isEmpty())
    }

    @Test fun contentTypeAndBodyValidation() {
        assertEquals(415, req("POST", "/apps/YouTube", body = "a=b", ct = "application/json").status)
        assertEquals(415, req("POST", "/apps/YouTube", body = "a=b", ct = null).status)
        assertEquals(201, req("POST", "/apps/YouTube", body = "a=b", ct = "application/x-www-form-urlencoded").status)
        assertEquals(400, req("POST", "/apps/YouTube", body = "a=https://evil").status)
        assertEquals(400, req("POST", "/apps/YouTube", body = "a=%0d%0aX").status)
        assertEquals(1, launcher.launched.size)
    }

    @Test fun launchesAreRateLimitedToSixPerMinute() {
        repeat(6) { assertEquals(201, req("POST", "/apps/YouTube", body = "a=$it").status, "lancement $it") }
        val r = req("POST", "/apps/YouTube", body = "a=7")
        assertEquals(429, r.status); assertNotNull(r.header("Retry-After"))
        assertEquals(6, launcher.launched.size)
        now = 61_000
        assertEquals(201, req("POST", "/apps/YouTube", body = "a=8").status)
    }

    @Test fun methodsAndMalformedRequests() {
        assertEquals(405, req("PUT", "/apps/YouTube", body = "a=b").status)
        assertEquals(405, req("POST", "/dd.xml", body = "a=b").status)
        assertEquals(405, req("POST", "/apps/YouTube/run", body = "a=b").status)
        assertEquals(400, send("garbage\r\n\r\n").status)
        assertEquals(501, send("POST /apps/YouTube HTTP/1.1\r\nHost: 127.0.0.1:$port\r\nTransfer-Encoding: chunked\r\n\r\n").status)
        assertEquals(431, send("GET /dd.xml HTTP/1.1\r\nHost: 127.0.0.1:$port\r\nX: " + "a".repeat(9000) + "\r\n\r\n").status)
        assertEquals(200, req("GET", "/dd.xml").status, "le serveur survit")
    }

    @Test fun outsideSourceGetsNoAnswer() {
        val router = DialRouter("d", "m", "x", { setOf("h:1") }, launcher)
        val outside = DialRequest("GET", "/dd.xml", mapOf("host" to listOf("h:1")), ByteArray(0), InetAddress.getByName("8.8.8.8"))
        assertNull(router.handle(outside))
        val inside = DialRequest("GET", "/dd.xml", mapOf("host" to listOf("h:1")), ByteArray(0), InetAddress.getByName("192.168.1.5"))
        assertEquals(200, router.handle(inside)!!.status)
    }
}

/** Répondeur SSDP réel sur la boucle locale (sans multicast). */
class SsdpServerTest {
    private val lo = InetAddress.getByName("127.0.0.1")
    private val udn = DialRules.udn("dev1")
    private val search = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 1\r\nST: %s\r\n\r\n"

    private fun exchange(st: String, srv: SsdpServer, wait: Int = 1600): String? {
        DatagramSocket(0, lo).use { c ->
            c.soTimeout = wait
            val d = (search.format(st)).toByteArray()
            c.send(DatagramPacket(d, d.size, lo, srv.localPort))
            val buf = ByteArray(2048); val p = DatagramPacket(buf, buf.size)
            return try { c.receive(p); String(p.data, 0, p.length) } catch (e: java.net.SocketTimeoutException) { null }
        }
    }
    private fun server(notify: InetSocketAddress = InetSocketAddress(lo, 9), alive: Long = 0) =
        SsdpServer(udn, { "http://127.0.0.1:30765/dd.xml" }, 0, false, lo, notify, Random(1), alive).also { assertTrue(it.start()) }

    @Test fun answersDialAndRootAndAllWithinMx() {
        val s = server()
        try {
            val t0 = System.currentTimeMillis()
            val r = exchange(DialRules.SEARCH_TARGET, s)!!
            assertTrue(System.currentTimeMillis() - t0 < 1500, "délai < MX")
            assertTrue(r.startsWith("HTTP/1.1 200 OK\r\n") && "LOCATION: http://127.0.0.1:30765/dd.xml\r\n" in r && "ST: ${DialRules.SEARCH_TARGET}\r\n" in r &&
                "USN: $udn::${DialRules.SEARCH_TARGET}\r\n" in r && "CACHE-CONTROL: max-age=1800\r\n" in r, r)
            assertTrue("ST: upnp:rootdevice\r\n" in exchange("upnp:rootdevice", s)!!)
            assertTrue("ST: ${DialRules.SEARCH_TARGET}\r\n" in exchange("ssdp:all", s)!!)
        } finally { s.stop() }
    }

    @Test fun staysSilentForOtherTargetsAndGarbage() {
        val s = server()
        try {
            assertNull(exchange("urn:schemas-upnp-org:device:MediaRenderer:1", s, 1300))
            DatagramSocket(0, lo).use { c ->
                c.soTimeout = 1300
                val d = "hello".toByteArray(); c.send(DatagramPacket(d, d.size, lo, s.localPort))
                assertFailsWith<java.net.SocketTimeoutException> { c.receive(DatagramPacket(ByteArray(100), 100)) }
            }
        } finally { s.stop() }
    }

    @Test fun announcesAliveThenByebye() {
        DatagramSocket(0, lo).use { listener ->
            listener.soTimeout = 2000
            val s = server(InetSocketAddress(lo, listener.localPort))
            try {
                s.announce(true)
                val alive = (1..3).map { val p = DatagramPacket(ByteArray(2048), 2048); listener.receive(p); String(p.data, 0, p.length) }
                assertEquals(Ssdp.notifyTargets(udn).toSet(), alive.map { Regex("NT: (.*)\r\n").find(it)!!.groupValues[1] }.toSet())
                assertTrue(alive.all { "NTS: ssdp:alive\r\n" in it && "LOCATION: http://127.0.0.1:30765/dd.xml\r\n" in it })
            } finally { s.stop() }
            val bye = (1..3).map { val p = DatagramPacket(ByteArray(2048), 2048); listener.receive(p); String(p.data, 0, p.length) }
            assertTrue(bye.all { "NTS: ssdp:byebye\r\n" in it && "LOCATION" !in it }, bye.toString())
            assertEquals(Ssdp.notifyTargets(udn).toSet(), bye.map { Regex("NT: (.*)\r\n").find(it)!!.groupValues[1] }.toSet())
        }
    }
}
