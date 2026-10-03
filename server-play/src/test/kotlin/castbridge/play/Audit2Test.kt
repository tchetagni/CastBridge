package castbridge.play

import castbridge.core.quiz.Json
import castbridge.core.quiz.online.ClientMsg
import castbridge.core.quiz.online.PlayCodec
import castbridge.core.quiz.online.PlayProtocol
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Second audit Opus de w20-03 : blocage collectif derrière un NAT, démarrage sans proxy déclaré, onglets, plafonds /48, tests renforcés. */
class Audit2Test {
    private val servers = ArrayList<PlayServer>()
    private val closeables = ArrayList<AutoCloseable>()
    private fun cfg(vararg o: Pair<String, Any?>) = PlayConfig(port = 0, trustedProxies = LOOPBACK, ticketPubKeys = listOf(TestKeys.pub))
    private fun server(c: PlayConfig = cfg()) = PlayServer(c).also { it.start(); servers += it }
    @AfterTest fun stop() { closeables.forEach { runCatching { it.close() } }; servers.forEach { it.close() }; closeables.clear(); servers.clear() }
    private fun ws(srv: PlayServer, ip: String) = WsWire(srv.port, xff = ip).also { w -> closeables += AutoCloseable { w.close() } }
    private fun host(srv: PlayServer, ip: String = "203.0.113.1"): Pair<WsWire, Map<*, *>> {
        val tv = ws(srv, ip)
        tv.send(PlayCodec.encode(ClientMsg.Hello(PlayProtocol.PROTO, PlayProtocol.CAPS, null, TestKeys.ticket()))); tv.send(PlayCodec.encode(ClientMsg.Create(null, "DUEL")))
        return tv to tv.await("welcome")!!
    }
    private fun env(vararg kv: Pair<String, String>): (String) -> String? = { k -> kv.toMap()[k] }

    // ---- (1) pas de blocage collectif d'un resume ; seuil de 30 codes faux de join par adresse ----

    @Test fun aPlayerResumesOverTheNetworkEvenWhenItsSharedAddressHasThirtyBadCodes() {
        val srv = server()
        val (_, w) = host(srv)
        val awa = ws(srv, "198.51.100.50")
        awa.send(PlayCodec.encode(ClientMsg.Join(w["code"] as String, "Awa", null, dev(), false)))
        val welcome = awa.await("welcome")!!
        awa.abort()
        // trois voisins du même NAT (même adresse publique) tapent 30 codes faux
        repeat(3) { n ->
            val c = ws(srv, "198.51.100.50")
            repeat(10) { i -> c.send(PlayCodec.encode(ClientMsg.Join("ZZZZ%04d".format(n * 10 + i), "X", null, dev(), false))); assertEquals("PLAY_BAD_CODE", c.await("error")?.get("reason")) }
        }
        val late = ws(srv, "198.51.100.50")
        late.send(PlayCodec.encode(ClientMsg.Join(w["code"] as String, "Neuf", null, dev(), false)))
        assertEquals("PLAY_BAD_CODE", late.await("error")?.get("reason"), "30 codes faux : l'adresse ne peut plus entrer")
        late.send(PlayCodec.encode(ClientMsg.Resume(welcome["roomId"] as String, welcome["token"] as String, 0)))
        assertEquals(welcome["token"], late.await("welcome")?.get("token"), "mais le joueur déjà assis reprend avec son jeton")
    }

    @Test fun wrongResumesAreNotCountedByTheService() {
        val srv = server()
        val (_, w) = host(srv)
        val c = ws(srv, "198.51.100.60")
        repeat(25) { i -> c.send(PlayCodec.encode(ClientMsg.Resume(w["roomId"] as String, "faux-%026d".format(i), 0))); assertEquals("PLAY_BAD_CODE", c.await("error")?.get("reason")) }
        val d = ws(srv, "198.51.100.60")
        d.send(PlayCodec.encode(ClientMsg.Join(w["code"] as String, "Awa", null, dev(), false)))
        assertEquals("PLAYER", d.await("welcome")?.get("role"), "des resume faux ne comptent pas comme des codes faux")
    }

    // ---- (2) connexion assise qui envoie des join faux ----

    @Test fun seatedSpectatorsSendingWrongJoinsNeverRotateTheRoomCode() {
        val srv = server()
        val (_, w) = host(srv)
        val code = w["code"] as String
        repeat(3) { n ->
            val s = ws(srv, "198.51.100.${70 + n}")
            s.send(PlayCodec.encode(ClientMsg.Join(code, "V$n", null, dev(), true))); assertNotNull(s.await("welcome"))
            repeat(20) { i -> s.send(PlayCodec.encode(ClientMsg.Join("ZZZZ%04d".format(n * 20 + i), "X", null, dev(), false))) }
        }
        Thread.sleep(500)
        assertEquals(code, RoomCodeOf(srv), "60 join faux de connexions assises : le code ne tourne pas")
    }
    private fun RoomCodeOf(srv: PlayServer) = srv.rooms().single().code

    // ---- (3) démarrage : proxy de confiance obligatoire ----

    @Test fun serviceRefusesToStartWithoutTrustedProxiesUnlessDirect() {
        val e = assertFailsWith<IllegalStateException> { PlayConfig.fromEnv(env()) }
        assertTrue(e.message!!.contains("CASTBRIDGE_PLAY_TRUSTED_PROXIES"), e.message)
        assertEquals(1, PlayConfig.fromEnv(env("CASTBRIDGE_PLAY_TRUSTED_PROXIES" to "172.18.0.5/32")).trustedProxies.size)
        assertTrue(PlayConfig.fromEnv(env("CASTBRIDGE_PLAY_DIRECT" to "1")).trustedProxies.isEmpty(), "staging : accès direct, aucun proxy")
        assertFailsWith<IllegalStateException>("DIRECT=0 n'exempte pas") { PlayConfig.fromEnv(env("CASTBRIDGE_PLAY_DIRECT" to "0")) }
    }

    @Test fun anInvalidTrustedProxyEntryIsRefusedNeverIgnored() {
        for (bad in listOf("172.18.0.5/32, nimporte-quoi", "172.18.0.5/33", "evil.example", "10.0.0.0/8,")) {
            val e = assertFailsWith<IllegalStateException>("entrée invalide « $bad »") { PlayConfig.fromEnv(env("CASTBRIDGE_PLAY_TRUSTED_PROXIES" to bad)) }
            assertTrue(e.message!!.contains("TRUSTED_PROXIES"), e.message)
        }
        assertFailsWith<IllegalStateException>("même en accès direct") { PlayConfig.fromEnv(env("CASTBRIDGE_PLAY_DIRECT" to "1", "CASTBRIDGE_PLAY_TRUSTED_PROXIES" to "x.y")) }
    }

    @Test fun anUnreadableForwardedForFromTheTrustedProxyIsRefusedWith400() {
        val peer = InetAddress.getByName("127.0.0.1")
        assertFailsWith<ForwardedForError> { ClientIp.resolve(peer, "pas-une-ip", LOOPBACK) }
        assertFailsWith<ForwardedForError> { ClientIp.resolve(peer, "1.2.3.4, evil.example", LOOPBACK) }
        assertEquals("203.0.113.9", ClientIp.resolve(peer, "1.2.3.4, 203.0.113.9", LOOPBACK))
        assertEquals("127.0.0.1", ClientIp.resolve(peer, null, LOOPBACK), "sans en-tête (sonde de santé locale) : l'adresse de la socket")
        assertEquals("127.0.0.1", ClientIp.resolve(peer, "pas-une-ip", emptyList()), "pair non fiable : l'en-tête est ignoré, même illisible")
        val srv = server()
        fun status(xff: String?) = Socket("127.0.0.1", srv.port).use { s -> s.getOutputStream().write(("GET /play/health HTTP/1.1\r\nHost: x\r\n" + (xff?.let { "X-Forwarded-For: $it\r\n" } ?: "") + "\r\n").toByteArray()); s.getInputStream().readNBytes(30).toString(Charsets.UTF_8) }
        assertTrue(status("evil.example").startsWith("HTTP/1.1 400"), "XFF illisible du proxy de confiance : 400")
        assertTrue(status("203.0.113.5").startsWith("HTTP/1.1 200"))
        assertTrue(status(null).startsWith("HTTP/1.1 200"))
    }

    // ---- (5) un cookie par onglet ----

    @Test fun twoTabsOfOneBrowserKeepTwoIndependentSessions() {
        val srv = server()
        val (_, w) = host(srv)
        val code = w["code"] as String
        fun post(tab: String, jar: List<String>, msg: String): HttpResponse<String> {
            val b = HttpRequest.newBuilder(URI("http://127.0.0.1:${srv.port}/play/act?tab=$tab")).header("Origin", "https://bridge.sti-cm.com").header("X-Forwarded-For", "198.51.100.80")
                .POST(HttpRequest.BodyPublishers.ofString(msg)).timeout(Duration.ofSeconds(5))
            if (jar.isNotEmpty()) b.header("Cookie", jar.joinToString("; "))
            return http.send(b.build(), HttpResponse.BodyHandlers.ofString())
        }
        fun poll(tab: String, jar: List<String>): Map<*, *> {
            val b = HttpRequest.newBuilder(URI("http://127.0.0.1:${srv.port}/play/state?tab=$tab&since=0")).header("Origin", "https://bridge.sti-cm.com").header("X-Forwarded-For", "198.51.100.80")
                .header("Cookie", jar.joinToString("; ")).timeout(Duration.ofSeconds(5)).GET()
            val r = http.send(b.build(), HttpResponse.BodyHandlers.ofString())
            assertEquals(200, r.statusCode(), "onglet $tab : ${r.body()}")
            return Json.parse(r.body()) as Map<*, *>
        }
        val jar = ArrayList<String>()   // le « navigateur » : TOUS les cookies partent avec chaque requête
        val a = post("aaaaaaaa1111", jar, PlayCodec.encode(ClientMsg.Join(code, "Onglet A", null, dev(), false))); jar += Cred.of(a)!!
        val b = post("bbbbbbbb2222", jar, PlayCodec.encode(ClientMsg.Join(code, "Onglet B", null, dev(), false))); jar += Cred.of(b)!!
        assertNotEquals(Cred.of(a), Cred.of(b), "deux onglets : deux sessions, deux cookies")
        val ma = (poll("aaaaaaaa1111", jar)["msgs"] as List<*>).map { it as Map<*, *> }; val mb = (poll("bbbbbbbb2222", jar)["msgs"] as List<*>).map { it as Map<*, *> }
        val wa = ma.first { it["t"] == "welcome" }; val wb = mb.first { it["t"] == "welcome" }
        assertNotEquals(wa["token"], wb["token"], "chaque onglet a son siège")
        assertEquals(2, srv.rooms().single().seatCount(), "deux joueurs assis, pas un seul")
        // l'onglet A n'a pas été coupé par l'ouverture de B : il continue de parler
        post("aaaaaaaa1111", jar, PlayCodec.encode(ClientMsg.Pong("x"))).also { assertEquals(200, it.statusCode()) }
        // un nonce invalide retombe sur le nom par défaut, jamais sur une injection
        val odd = post("../../x", emptyList(), PlayCodec.encode(ClientMsg.Pong("x")))
        assertTrue(odd.headers().firstValue("set-cookie").orElse("").startsWith("__Host-cbp="), "nonce invalide ignoré : ${odd.headers().firstValue("set-cookie")}")
    }

    // ---- (6) tests renforcés ----

    private fun wsConnOver(cfg: PlayConfig): Triple<WsConn, Socket, Socket> {
        val ss = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).also { closeables += AutoCloseable { it.close() } }
        val client = Socket("127.0.0.1", ss.localPort).also { closeables += AutoCloseable { it.close() } }
        val server = ss.accept().also { closeables += AutoCloseable { it.close() } }
        val limits = ConnectionLimits(100, 100)
        val hub = PlayHub(cfg, { 0L }, castbridge.core.quiz.EmbeddedQuestionSource(levels = null).bank(), TicketVerifier(emptyList()), limits = limits)
        return Triple(WsConn("w1", "203.0.113.1", server, cfg, hub), server, client)
    }

    @Test fun aPingFloodLeavesAtMostOnePongPendingAndBoundedBytes() {
        val (conn, _, _) = wsConnOver(cfg())
        val payload = ByteArray(125) { 1 }
        repeat(100_000) { conn.onClientPing(payload) }
        assertEquals(1, conn.pendingPongs(), "UN SEUL pong en attente (le dernier)")
        assertTrue(conn.queuedBytes() <= 125 + 2, "octets de contrôle comptés et bornés : ${conn.queuedBytes()}")
        conn.onClientPing(ByteArray(3) { 9 })
        assertEquals(1, conn.pendingPongs()); assertEquals(3 + 2, conn.queuedBytes(), "le dernier payload remplace le précédent")
    }

    @Test fun aClientThatStopsReadingIsCutAfterTheWriteDeadline() {
        val c = PlayConfig(port = 0, outboxMaxBytes = 200_000_000, writeTimeoutMs = 300, ticketPubKeys = listOf(TestKeys.pub))
        val (conn, server, _) = wsConnOver(c)   // le client ne lit JAMAIS
        val runner = Thread { conn.run(server.getInputStream()) }.also { it.isDaemon = true; it.start() }
        val big = "x".repeat(100_000)
        repeat(300) { conn.offer(big) }   // 30 Mo : bien au-delà des tampons de la socket, l'écrivain se bloque
        Thread.sleep(700)
        conn.housekeeping(System.currentTimeMillis())   // le tick du service : écriture bloquée depuis plus de 300 ms
        runner.join(5_000)
        assertFalse(runner.isAlive, "la session est coupée après l'échéance d'écriture")
        assertTrue(server.isClosed)
    }

    @Test fun carriersStayFewAndLatencyLowUnderThreeHundredIdleStreams() {
        val srv = server(PlayConfig(port = 0, trustedProxies = LOOPBACK, maxPerIp = 5_000, maxConnections = 5_000, ticketPubKeys = listOf(TestKeys.pub)))
        val hello = PlayCodec.encode(ClientMsg.Hello(PlayProtocol.PROTO, PlayProtocol.CAPS, null, null))
        repeat(300) { i ->
            val cred = Cred.of(SseWire.post(srv.port, hello, null, "https://bridge.sti-cm.com", "203.0.113.${i % 200 + 1}"))!!
            val s = Socket("127.0.0.1", srv.port).also { closeables += AutoCloseable { it.close() } }
            val path = if (i % 2 == 0) "/play/events" else "/play/state?since=0"
            s.getOutputStream().write("GET $path HTTP/1.1\r\nHost: x\r\nOrigin: https://bridge.sti-cm.com\r\nCookie: $cred\r\n\r\n".toByteArray())
        }
        Thread.sleep(500)
        val lat = (1..20).map { val t = System.nanoTime(); assertEquals(200, http.send(HttpRequest.newBuilder(URI("http://127.0.0.1:${srv.port}/play/health")).build(), HttpResponse.BodyHandlers.ofString()).statusCode()); (System.nanoTime() - t) / 1_000_000 }.sorted()
        assertTrue(lat[18] < 300, "latence p95 sous charge : ${lat[18]} ms")
        val carriers = Thread.getAllStackTraces().keys.count { it.name.startsWith("ForkJoinPool-") && it.name.contains("worker") }
        assertTrue(carriers <= Runtime.getRuntime().availableProcessors() + 8, "porteurs de fils virtuels : $carriers (un fil bloqué qui épingle fait grossir ce nombre)")
    }

    // ---- (7) un /48 IPv6 donne 65 536 clés /64 : second plafond ----

    @Test fun oneIpv6Slash48SharesASecondConnectionCapOfSixtyFour() {
        val peer = InetAddress.getByName("127.0.0.1")
        val limits = ConnectionLimits(8, 1_000_000, 64)
        var ok = 0
        for (i in 0 until 65_536) if (limits.acquire(ClientIp.resolve(peer, "2001:db8:abcd:" + Integer.toHexString(i) + "::1", LOOPBACK)) == ConnectionLimits.Verdict.OK) ok++
        assertEquals(64, ok, "65 536 /64 d'un même /48 : au plus 64 connexions")
        assertEquals(ConnectionLimits.Verdict.OK, limits.acquire(ClientIp.resolve(peer, "2001:db8:abce::1", LOOPBACK)), "un autre /48 n'est pas touché")
        assertEquals(64, ConnectionLimits(1_000, 1_000_000).let { l -> (0 until 100).count { l.acquire(ClientIp.resolve(peer, "2001:db8:abcd:$it::1", LOOPBACK)) == ConnectionLimits.Verdict.OK } }, "plafond par défaut : 64")
    }

    @Test fun badCodesAreAlsoCappedPerSlash48() {
        val limits = ConnectionLimits(100_000, 100_000)
        val hub = PlayHub(PlayConfig(trustedProxies = LOOPBACK, ticketPubKeys = listOf(TestKeys.pub)), { 1_000L }, castbridge.core.quiz.EmbeddedQuestionSource(levels = null).bank(), TicketVerifier(listOf(TestKeys.pub)), limits = limits)
        class Fake(id: String, ip: String) : PlayConn(id, ip, 1_000_000, 1_000_000) {
            val got = ArrayList<String>()
            override fun offer(text: String): Boolean { got += text; return true }
            override fun close(code: Int, reason: String) {}
        }
        val peer = InetAddress.getByName("127.0.0.1")
        fun key(i: Int, p48: String = "abcd") = ClientIp.resolve(peer, "2001:db8:$p48:" + Integer.toHexString(i) + "::1", LOOPBACK)
        val tv = Fake("tv", "203.0.113.1").also { it.ticket = TestKeys.ticket(); hub.register(it) }
        hub.onText(tv, PlayCodec.encode(ClientMsg.Create(null, "DUEL")))
        val code = Regex("\"code\":\"([0-9A-Z]{8})\"").find(tv.got.first { it.startsWith("{\"t\":\"welcome\"") })!!.groupValues[1]
        repeat(PlayProtocol.MAX_BAD_CODES_PER_IP * 4) { i -> hub.onText(Fake("b$i", key(i)).also { hub.register(it) }, PlayCodec.encode(ClientMsg.Join("ZZZZ%04d".format(i), "X", null, dev(), false))) }
        val same48 = Fake("late1", key(5_000)).also { hub.register(it) }; hub.onText(same48, PlayCodec.encode(ClientMsg.Join(code, "Awa", null, dev(), false)))
        assertTrue(same48.got.any { it.contains("PLAY_BAD_CODE") }, "120 codes faux depuis un même /48 (par /64 différents) : tout le /48 est bloqué")
        val other48 = Fake("late2", key(1, "abce")).also { hub.register(it) }; hub.onText(other48, PlayCodec.encode(ClientMsg.Join(code, "Bello", null, dev(), false)))
        assertTrue(other48.got.any { it.startsWith("{\"t\":\"welcome\"") }, "un autre /48 entre")
    }

    // ---- (6) arrêt : crochet SIGTERM et ticker ----

    @Test fun theShutdownHookDrainsRoomsThenStopsTheTickerAndTheListener() {
        val srv = PlayServer(cfg()).start()
        val (tv, w) = host(srv)
        val hook = shutdownHook(srv, 300)
        val t0 = System.currentTimeMillis()
        val runner = Thread(hook).also { it.start() }
        assertEquals("PLAY_MAINTENANCE", tv.await("error")?.get("reason"), "les salles sont prévenues par le crochet SIGTERM")
        runner.join(5_000)
        assertFalse(runner.isAlive); assertTrue(System.currentTimeMillis() - t0 >= 250, "délai de grâce respecté")
        assertTrue(srv.tickerStopped(), "le fil du tick est arrêté")
        assertFailsWith<java.io.IOException> { Socket("127.0.0.1", srv.port).use { } }
        assertNotNull(w)
    }

    // ---- (8) construction ----

    @Test fun dockerfileAndBuildAreExplicitAndHonest() {
        val docker = File("Dockerfile").readText()
        assertTrue(Regex("(?m)^ARG RUNTIME_IMAGE=eclipse-temurin:25-jre").containsMatchIn(docker) && docker.contains("FROM \${RUNTIME_IMAGE}"), "runtime paramétrable : épinglable par digest")
        assertTrue(docker.contains("docker inspect --format '{{index .RepoDigests 0}}'"), "comment obtenir le digest est documenté")
        assertFalse(Regex("@sha256:[0-9a-f]{64}").containsMatchIn(docker), "aucun digest inventé")
        assertTrue(docker.contains("HEALTHCHECK") && docker.contains("USER 10002:10002"))
        val gradle = File("build.gradle.kts").readText()
        assertTrue(gradle.contains("JvmTarget.JVM_21") && gradle.contains("release"), "cible JVM 21 explicite (le runtime peut être 25)")
    }
}
