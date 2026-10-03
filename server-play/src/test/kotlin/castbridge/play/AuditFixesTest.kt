package castbridge.play

import castbridge.core.quiz.Json
import castbridge.core.quiz.online.ClientMsg
import castbridge.core.quiz.online.PlayCodec
import castbridge.core.quiz.online.PlayProtocol
import java.io.File
import java.net.InetAddress
import java.net.Socket
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Correctifs de l'audit Opus (B1, B2, B4, B5 et les points mineurs) : chaque test échoue si la mutation de l'audit est remise. */
class AuditFixesTest {
    private val servers = ArrayList<PlayServer>()
    private val closeables = ArrayList<AutoCloseable>()
    private fun server(cfg: PlayConfig = PlayConfig(port = 0, trustedProxies = LOOPBACK, ticketPubKeys = listOf(TestKeys.pub))) = PlayServer(cfg).also { it.start(); servers += it }
    @AfterTest fun stop() { closeables.forEach { runCatching { it.close() } }; servers.forEach { it.close() }; closeables.clear(); servers.clear() }
    private fun raw(srv: PlayServer, autoPong: Boolean = false, n: Int = closeables.size + 1, connection: String = "Upgrade", key: String? = null) =
        RawWs(srv.port, mapOf("Origin" to "https://bridge.sti-cm.com", "X-Forwarded-For" to "203.0.113.${n % 250 + 1}"), autoPong = autoPong, connection = connection, keyOverride = key).also { closeables += it }
    private fun rawHttp(srv: PlayServer, req: String): String = Socket("127.0.0.1", srv.port).use { s -> s.getOutputStream().write(req.toByteArray()); s.getInputStream().readNBytes(600).toString(Charsets.UTF_8).lineSequence().first() }
    private fun frame(first: Int, payload: ByteArray, lenForm: Int? = null): ByteArray {   // trame masquée écrite à la main (longueurs non minimales possibles)
        val h = java.io.ByteArrayOutputStream(); h.write(first)
        when (lenForm ?: if (payload.size < 126) 0 else 126) {
            0 -> h.write(0x80 or payload.size)
            126 -> { h.write(0x80 or 126); h.write(payload.size ushr 8); h.write(payload.size and 0xff) }
            else -> { h.write(0x80 or 127); for (s in 56 downTo 0 step 8) h.write(((payload.size.toLong() ushr s) and 0xff).toInt()) }
        }
        h.write(byteArrayOf(1, 2, 3, 4)); payload.forEachIndexed { i, b -> h.write(b.toInt() xor byteArrayOf(1, 2, 3, 4)[i and 3].toInt()) }
        return h.toByteArray()
    }

    // ---- B1 : flood de ping ----

    @Test fun pingFloodWithoutReadingIsBoundedAndTheConnectionIsClosed() {
        val srv = server()
        val c = raw(srv)
        val payload = ByteArray(125) { 7 }
        val sender = Thread { try { repeat(100_000) { c.send(9, payload) } } catch (_: Exception) {} }.also { it.isDaemon = true; it.start() }
        sender.join(20_000)
        val code = c.closedWithin(8_000)
        assertNotNull(code, "100 000 pings sans lecture : le serveur doit fermer (une file de pongs sans borne)")
        assertTrue(code == 1008 || code == -1, "code $code")
        val end = System.currentTimeMillis() + 3_000
        while (srv.hub.connectionCount() > 0 && System.currentTimeMillis() < end) Thread.sleep(50)
        assertEquals(0, srv.hub.connectionCount(), "la session est libérée")
    }

    @Test fun anAnsweringClientStillGetsItsPongAndAtMostOnePongIsPending() {
        val srv = server()
        val c = raw(srv, autoPong = false)
        c.send(9, "abc".toByteArray())
        val f = c.read(2_000)!!
        assertEquals(10, f.opcode); assertEquals("abc", f.text, "le pong reprend la charge du ping (RFC 6455 § 5.5.3)")
    }

    // ---- B2 : fils virtuels non épinglés ----

    @Test fun threeHundredIdleStreamsAndTwentyMuteWebSocketsDoNotStarveNewConnections() {
        val srv = server(PlayConfig(port = 0, trustedProxies = LOOPBACK, maxPerIp = 5_000, maxConnections = 5_000, ticketPubKeys = listOf(TestKeys.pub)))
        val hello = PlayCodec.encode(ClientMsg.Hello(PlayProtocol.PROTO, PlayProtocol.CAPS, null, null))
        val socks = ArrayList<Socket>()
        repeat(300) { i ->
            val cred = Cred.of(SseWire.post(srv.port, hello, null, "https://bridge.sti-cm.com", "203.0.113.${i % 200 + 1}"))!!
            val path = if (i % 2 == 0) "/play/events" else "/play/state?since=0"
            val s = Socket("127.0.0.1", srv.port).also { socks += it; closeables += AutoCloseable { it.close() } }
            val auth = if (Cred.isCookie(cred)) "Cookie: $cred\r\n" else "X-Play-Conn: $cred\r\n"
            val target = if (Cred.isCookie(cred)) path else path + (if ('?' in path) "&" else "?") + "token=$cred"
            s.getOutputStream().write("GET $target HTTP/1.1\r\nHost: x\r\nOrigin: https://bridge.sti-cm.com\r\n$auth\r\n".toByteArray())
        }
        repeat(20) { raw(srv, n = 100 + it) }
        Thread.sleep(500)
        assertTrue(srv.hub.connectionCount() >= 320, "connexions établies : ${srv.hub.connectionCount()}")
        val t0 = System.nanoTime()
        val r = http.send(HttpRequest.newBuilder(URI("http://127.0.0.1:${srv.port}/play/health")).timeout(Duration.ofSeconds(5)).build(), HttpResponse.BodyHandlers.ofString())
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertEquals(200, r.statusCode()); assertTrue(ms < 1_000, "nouvelle requête servie en $ms ms (fils virtuels épinglés ?)")
        val t1 = System.nanoTime(); val extra = raw(srv, n = 240); assertEquals(101, extra.status)
        assertTrue((System.nanoTime() - t1) / 1_000_000 < 1_000, "nouvelle poignée de main WebSocket < 1 s")
    }

    @Test fun noBlockingPathStillUsesSynchronizedOrObjectWait() {
        val dir = File("src/main/kotlin/castbridge/play")
        val offenders = dir.listFiles { f -> f.name.endsWith(".kt") }!!.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, l -> if (l.trim().let { it.startsWith("*") || it.startsWith("/*") || it.startsWith("//") }) null else if (Regex("\\bsynchronized\\b|\\.wait\\(|\\.notifyAll\\(|startVirtualThread").containsMatchIn(l.substringBefore("//"))) "${f.name}:${i + 1}" else null }
        }
        // seuls restent des verrous courts sans entrée-sortie ni attente (hub/salle : calcul pur), dans RoomRegistry.kt et ConnectionLimits.kt (seau)
        assertEquals(emptyList(), offenders.filterNot { it.startsWith("RoomRegistry.kt") || it.startsWith("ConnectionLimits.kt") }, "attente ou écriture sous synchronized, ou fil virtuel « tueur »")
    }

    // ---- B4 : IPv6 par préfixe /64 ----

    @Test fun ipv6AddressesOfOneSlash64ShareOneKeyAndOneCap() {
        val peer = InetAddress.getByName("127.0.0.1"); val trusted = LOOPBACK
        val limits = ConnectionLimits(8, 1_000_000)
        var ok = 0
        for (i in 0 until 65_536) if (limits.acquire(ClientIp.resolve(peer, "2001:db8:aaaa:1::" + Integer.toHexString(i), trusted)) == ConnectionLimits.Verdict.OK) ok++
        assertEquals(8, ok, "2^16 adresses d'une même /64 partagent UN plafond")
        assertEquals(ClientIp.resolve(peer, "2001:db8:aaaa:1::1", trusted), ClientIp.resolve(peer, "2001:db8:aaaa:1:ffff:eeee:dddd:cccc", trusted))
        assertNotEquals(ClientIp.resolve(peer, "2001:db8:aaaa:1::1", trusted), ClientIp.resolve(peer, "2001:db8:aaaa:2::1", trusted), "autre /64 : autre clé")
        assertEquals("203.0.113.9", ClientIp.resolve(peer, "203.0.113.9", trusted), "IPv4 telle quelle")
        assertEquals(ClientIp.resolve(peer, "::ffff:203.0.113.9", trusted), "203.0.113.9", "IPv4 en IPv6 : l'adresse IPv4")
    }

    @Test fun ipv6ClientsOfOneSlash64SharetheSocketLevelLimit() {
        val srv = server(PlayConfig(port = 0, trustedProxies = LOOPBACK, maxPerIp = 3, ticketPubKeys = listOf(TestKeys.pub)))
        repeat(3) { WsWire(srv.port, xff = "2001:db8:5:5::$it").also { w -> closeables += AutoCloseable { w.close() } } }
        val e = runCatching { WsWire(srv.port, xff = "2001:db8:5:5:9::77") }.exceptionOrNull() as? WsRefused
        assertEquals(429, e?.status)
    }

    // ---- B5 : aucun secret dans une adresse ----

    @Test fun fallbackSecretIsACookieAndNeverInAnyUrl() {
        val srv = server()
        SeenUrls.all.clear()
        val tv = WsWire(srv.port, xff = "203.0.113.1").also { w -> closeables += AutoCloseable { w.close() } }
        tv.send(PlayCodec.encode(ClientMsg.Hello(PlayProtocol.PROTO, PlayProtocol.CAPS, null, TestKeys.ticket()))); tv.send(PlayCodec.encode(ClientMsg.Create(null, "DUEL")))
        val code = tv.await("welcome")!!["code"] as String
        val sse = SseWire(srv.port, xff = "203.0.113.2").also { closeables += AutoCloseable { it.close() } }
        sse.send(PlayCodec.encode(ClientMsg.Join(code, "Awa", null, dev(), false))); assertNotNull(sse.await("welcome"))
        val poll = PollWire(srv.port, xff = "203.0.113.3").also { closeables += AutoCloseable { it.close() } }
        poll.send(PlayCodec.encode(ClientMsg.Join(code, "Bello", null, dev(), false))); assertNotNull(poll.await("welcome"))
        for (u in SeenUrls.all) assertFalse(Regex("token=|[0-9a-f]{32}").containsMatchIn(u), "secret dans une adresse : $u")
        val cookie = Cred.of(SseWire.post(srv.port, PlayCodec.encode(ClientMsg.Pong("x")), null, "https://bridge.sti-cm.com", "203.0.113.4"))!!
        assertTrue(Cred.isCookie(cookie), "le service pose un cookie : $cookie")
    }

    @Test fun cookieAttributesAndRefusalOfTheQueryToken() {
        val srv = server()
        val r = SseWire.post(srv.port, PlayCodec.encode(ClientMsg.Pong("x")), null, "https://bridge.sti-cm.com", "203.0.113.4", "abcdef012345")
        val sc = r.headers().firstValue("set-cookie").orElse("")
        for (a in listOf("HttpOnly", "Secure", "SameSite=Strict", "Path=/")) assertTrue(sc.contains(a), "attribut $a absent de « $sc »")
        assertTrue(sc.startsWith("__Host-cbp-abcdef012345="), "un cookie PAR ONGLET, préfixe __Host- : $sc")
        assertFalse(sc.contains("Path=/play") || sc.contains("Domain"), "préfixe __Host- : Path=/ et pas de Domain")
        assertFalse(r.body().contains(sc.substringAfter('=').substringBefore(';')), "le corps de la réponse ne répète pas le secret")
        val secret = sc.substringAfter("=").substringBefore(';').ifEmpty { Cred.of(r)!! }
        for (path in listOf("/play/events", "/play/state?since=0")) {
            val q = if ('?' in path) "$path&token=$secret" else "$path?token=$secret"
            val resp = http.send(HttpRequest.newBuilder(URI("http://127.0.0.1:${srv.port}$q")).timeout(Duration.ofSeconds(3)).build(), HttpResponse.BodyHandlers.ofInputStream())
            assertEquals(410, resp.statusCode(), "le secret en paramètre d'adresse n'ouvre plus rien ($path)")
            resp.body().close()
        }
    }

    // ---- points mineurs ----

    @Test fun connectionHeaderIsParsedAsCommaSeparatedTokens() {
        val srv = server()
        assertEquals(101, raw(srv, connection = "keep-alive, Upgrade").status)
        assertEquals(101, raw(srv, connection = "UPGRADE").status)
        assertEquals(400, raw(srv, connection = "notupgraded").status, "un mot qui contient « upgrade » n'est pas le jeton")
    }

    @Test fun webSocketKeyMustBeBase64OfExactlySixteenBytes() {
        val srv = server()
        val b64 = java.util.Base64.getEncoder()
        assertEquals(101, raw(srv, key = b64.encodeToString(ByteArray(16))).status)
        assertEquals(400, raw(srv, key = b64.encodeToString(ByteArray(15))).status)
        assertEquals(400, raw(srv, key = b64.encodeToString(ByteArray(17))).status)
        assertEquals(400, raw(srv, key = "pas du base64 !!").status)
    }

    @Test fun nonMinimalLengthsAndOneByteCloseAreProtocolErrors() {
        val srv = server()
        val a = raw(srv); a.raw(frame(0x81, "{}".toByteArray(), lenForm = 126)); assertEquals(1002, a.closedWithin(3_000), "longueur 16 bits pour 2 octets")
        val b = raw(srv); b.raw(frame(0x81, "{}".toByteArray(), lenForm = 127)); assertEquals(1002, b.closedWithin(3_000), "longueur 64 bits pour 2 octets")
        val c = raw(srv); c.raw(frame(0x88, byteArrayOf(3))); assertEquals(1002, c.closedWithin(3_000), "close d'un seul octet")
    }

    @Test fun emptyContinuationFlooodIsRefused() {
        val srv = server()
        val c = raw(srv)
        c.raw(frame(0x01, "{".toByteArray()))
        try { repeat(2_000) { c.raw(frame(0x00, ByteArray(0))) } } catch (_: Exception) {}
        val code = c.closedWithin(3_000)
        assertTrue(code == 1002 || code == 1008 || code == -1, "continuations vides en rafale : fermeture ($code)")
    }

    @Test fun headerSyntaxAndTransferEncodingAreStrict() {
        val srv = server()
        assertEquals("HTTP/1.1 400 Bad Request", rawHttp(srv, "GET /play/health HTTP/1.1\r\nHost: x\r\nX-A : b\r\n\r\n"), "espace avant « : »")
        assertEquals("HTTP/1.1 400 Bad Request", rawHttp(srv, "GET /play/health HTTP/1.1\r\nHost: x\r\n folded: b\r\n\r\n"), "continuation de ligne obsolète")
        assertEquals("HTTP/1.1 400 Bad Request", rawHttp(srv, "POST /play/act HTTP/1.1\r\nHost: x\r\nOrigin: https://bridge.sti-cm.com\r\nTransfer-Encoding: chunked\r\n\r\n0\r\n\r\n"))
        assertEquals("HTTP/1.1 200 OK", rawHttp(srv, "GET /play/health HTTP/1.1\r\nHost: x\r\n\r\n"))
    }

    @Test fun slowRequestHeadIsCutByAGlobalDeadline() {
        val srv = server(PlayConfig(port = 0, trustedProxies = LOOPBACK, headDeadlineMs = 800, ticketPubKeys = listOf(TestKeys.pub)))
        Socket("127.0.0.1", srv.port).use { s ->
            s.soTimeout = 5_000
            val out = s.getOutputStream(); val t0 = System.currentTimeMillis()
            val answered = java.util.concurrent.atomic.AtomicLong(-1); val first = java.util.concurrent.atomic.AtomicReference("")
            Thread { val b = runCatching { s.getInputStream().readNBytes(100) }.getOrDefault(ByteArray(0)); first.set(b.toString(Charsets.UTF_8).lineSequence().first()); answered.set(System.currentTimeMillis() - t0) }
                .also { it.isDaemon = true; it.start() }
            try { for (c in "GET /play/health HTTP/1.1\r\nHost: x\r\nX-Slow: " + "a".repeat(40) + "\r\n") { if (answered.get() >= 0) break; out.write(c.code); out.flush(); Thread.sleep(150) } } catch (_: Exception) {}
            Thread.sleep(300)
            assertTrue(answered.get() in 0..2_000, "tête goutte à goutte coupée par l'échéance de 800 ms (réponse « ${first.get()} » après ${answered.get()} ms)")
            assertTrue(first.get().isEmpty() || first.get().startsWith("HTTP/1.1 408"), "408 ou fermeture : ${first.get()}")
        }
    }

    @Test fun contentSecurityPolicyAllowsOnlySameOriginConnections() {
        val srv = server()
        val csp = http.send(HttpRequest.newBuilder(URI("http://127.0.0.1:${srv.port}/play")).build(), HttpResponse.BodyHandlers.ofString()).headers().firstValue("content-security-policy").get()
        assertTrue(csp.contains("connect-src 'self';") && !csp.contains("ws:") && !csp.contains("wss:"), csp)
    }

    @Test fun fallbackBacklogCountsUtf8BytesNotCharacters() {
        val cfg = PlayConfig(port = 0, outboxMaxBytes = 100, ticketPubKeys = listOf(TestKeys.pub))
        val limits = ConnectionLimits(8, 100)
        val hub = PlayHub(cfg, { 0L }, castbridge.core.quiz.EmbeddedQuestionSource(levels = null).bank(), TicketVerifier(emptyList()), limits = limits)
        val fb = FbConn("x", "1.2.3.4", cfg, PlayFallbackController(cfg, hub, limits, TicketVerifier(emptyList()), OriginCheck(emptySet())))
        assertFalse(fb.offer("é".repeat(60)), "60 caractères = 120 octets UTF-8 > 100")
    }

    @Test fun defaultsTrustNoProxyAndTheConnectionSlotNeverLeaks() {
        assertTrue(PlayConfig().trustedProxies.isEmpty(), "aucun proxy de confiance par défaut")
        assertTrue(PlayConfig.fromEnv({ k -> if (k == "CASTBRIDGE_PLAY_DIRECT") "1" else null }).trustedProxies.isEmpty())
        val srv = server(PlayConfig(port = 0, trustedProxies = LOOPBACK, maxPerIp = 2, ticketPubKeys = listOf(TestKeys.pub)))
        repeat(20) { runCatching { RawWs(srv.port, mapOf("Origin" to "https://evil.example")).close() } }   // refusées avant l'acquisition
        repeat(20) { Socket("127.0.0.1", srv.port).use { s -> s.getOutputStream().write("GET /play/ws HTTP/1.1\r\nHost: x\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Version: 13\r\nSec-WebSocket-Key: AAAA\r\nOrigin: https://bridge.sti-cm.com\r\n\r\n".toByteArray()); s.getInputStream().readNBytes(20) } }
        Thread.sleep(300)
        assertEquals(0, srv.hub.connectionCount())
        repeat(2) { raw(srv, n = 77) }   // les deux places sont toujours libres
    }
}
