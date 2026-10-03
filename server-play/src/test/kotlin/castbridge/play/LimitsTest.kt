package castbridge.play

import castbridge.play.entitlement.TicketVerifier
import castbridge.core.quiz.online.ClientMsg
import castbridge.core.quiz.online.PlayCodec
import castbridge.core.quiz.online.PlayProtocol
import java.net.InetAddress
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Limites du service sur socket réelle : par IP, débit, taille, origine, plafonds de salles et de connexions, X-Forwarded-For. */
class LimitsTest {
    private val servers = ArrayList<PlayServer>()
    private val closeables = ArrayList<AutoCloseable>()
    private fun server(cfg: PlayConfig = PlayConfig(port = 0, trustedProxies = LOOPBACK, ticketPubKeys = listOf(TestKeys.pub), trustedKeys = TestRights.trustedKeys, createsPerIpPerHour = 10_000)) = PlayServer(cfg).also { it.start(); servers += it }
    @AfterTest fun stop() { closeables.forEach { runCatching { it.close() } }; servers.forEach { it.close() }; servers.clear(); closeables.clear() }
    private fun ws(srv: PlayServer, xff: String? = "203.0.113.7", origin: String? = "https://bridge.sti-cm.com", ticket: String? = null) = WsWire(srv.port, origin, xff, ticket).also { closeables += AutoCloseable { it.close() } }

    @Test fun ninthConnectionFromTheSameIpIsRefusedWith429BeforeTheUpgrade() {
        val srv = server()
        repeat(8) { ws(srv) }
        val e = assertFailsWith<WsRefused> { ws(srv) }
        assertEquals(429, e.status, "la 9e connexion de la même adresse : 429, avant l'upgrade")
        ws(srv, xff = "203.0.113.8")   // une autre adresse passe
    }

    @Test fun fallbackSessionsCountInTheSameIpLimit() {
        val srv = server()
        repeat(8) { ws(srv) }
        val r = SseWire.post(srv.port, PlayCodec.encode(ClientMsg.Hello(1, listOf("play1"), null, null)), null, "https://bridge.sti-cm.com", "203.0.113.7")
        assertEquals(429, r.statusCode())
    }

    @Test fun slotIsFreedWhenAConnectionCloses() {
        val srv = server(PlayConfig(port = 0, trustedProxies = LOOPBACK, maxPerIp = 1, ticketPubKeys = listOf(TestKeys.pub), trustedKeys = TestRights.trustedKeys, createsPerIpPerHour = 10_000))
        val a = ws(srv); assertFailsWith<WsRefused> { ws(srv) }
        a.close(); Thread.sleep(300)
        ws(srv)
    }

    @Test fun totalConnectionCapAnswers503() {
        val srv = server(PlayConfig(port = 0, trustedProxies = LOOPBACK, maxConnections = 2, ticketPubKeys = listOf(TestKeys.pub), trustedKeys = TestRights.trustedKeys, createsPerIpPerHour = 10_000))
        ws(srv, xff = "198.51.100.1"); ws(srv, xff = "198.51.100.2")
        assertEquals(503, assertFailsWith<WsRefused> { ws(srv, xff = "198.51.100.3") }.status)
    }

    @Test fun thirtyOneMessagesInOneSecondCloseWith1008() {
        val srv = server()
        RawWs(srv.port).use { c ->
            val ping = PlayCodec.encode(ClientMsg.Pong("x"))
            repeat(31) { c.sendText(ping) }
            assertEquals(1008, c.closedWithin(3_000), "31 messages en 1 s : fermeture 1008 (rafale 30, 10/s)")
        }
    }

    @Test fun thirtyMessagesBurstIsAccepted() {
        val srv = server()
        RawWs(srv.port).use { c ->
            val hello = PlayCodec.encode(ClientMsg.Hello(PlayProtocol.PROTO, PlayProtocol.CAPS, null, null))   // aucune réponse attendue
            repeat(29) { c.sendText(hello) }
            c.sendText(PlayCodec.encode(ClientMsg.Join("ZZZZZZZZ", "Awa", null, dev(), false)))   // le 30e message de la rafale passe encore
            assertEquals("PLAY_BAD_CODE", c.await("error", 2_000)?.get("reason") ?: "pas de réponse")
        }
    }

    @Test fun threeKiloByteMessageIsRejectedNotExecuted() {
        val srv = server()
        RawWs(srv.port).use { c ->
            val big = """{"t":"join","code":"ZZZZZZZZ","name":"${"a".repeat(3_000)}"}"""
            c.sendText(big)
            val e = c.await("error", 2_000)
            assertEquals("BAD_REQUEST", e?.get("reason"), "message de 3 Ko refusé par le codec")
            assertTrue(srv.rooms().isEmpty())
        }
    }

    @Test fun frameBeyondTheHardCapClosesWith1009() {
        val srv = server()
        RawWs(srv.port).use { c ->
            c.sendText("""{"t":"pong","id":"${"a".repeat(25_000)}"}""")
            assertEquals(1009, c.closedWithin(3_000))
        }
    }

    @Test fun binaryAndUnmaskedAreRefused() {
        val srv = server()
        RawWs(srv.port).use { c -> c.send(2, byteArrayOf(1, 2, 3)); assertEquals(1003, c.closedWithin(3_000)) }
    }

    @Test fun foreignOriginIsRefusedWith403() {
        val srv = server()
        assertEquals(403, assertFailsWith<WsRefused> { ws(srv, origin = "https://evil.example") }.status)
        assertEquals(403, assertFailsWith<WsRefused> { ws(srv, origin = "https://bridge.sti-cm.com.evil.example") }.status)
        assertEquals(403, assertFailsWith<WsRefused> { ws(srv, origin = null) }.status, "sans Origin et sans ticket : refusé")
        assertEquals(403, assertFailsWith<WsRefused> { ws(srv, origin = "null") }.status)
        assertEquals(403, assertFailsWith<WsRefused> { ws(srv, origin = null, ticket = TestKeys.ticket(now = 0)) }.status, "ticket périmé : comme pas de ticket")
        ws(srv, origin = null, ticket = TestKeys.ticket())   // client natif avec ticket valide
        ws(srv, origin = "https://bridge.sti-cm.com")
        assertEquals(403, SseWire.post(srv.port, "{}", null, "https://evil.example", null).statusCode(), "POST de repli : même contrôle")
    }

    @Test fun roomCapAnswersPlayBusyAndKeepsTheOtherRoom() {
        val srv = server(PlayConfig(port = 0, trustedProxies = LOOPBACK, maxRooms = 1, ticketPubKeys = listOf(TestKeys.pub), trustedKeys = TestRights.trustedKeys, createsPerIpPerHour = 10_000))
        val a = ws(srv); a.send(PlayCodec.encode(ClientMsg.Hello(PlayProtocol.PROTO, PlayProtocol.CAPS, null, TestKeys.ticket()))); a.send(PlayCodec.encode(TestRights.create()))
        assertEquals("HOST", a.await("welcome")?.get("role"))
        val b = ws(srv, xff = "203.0.113.9"); b.send(PlayCodec.encode(ClientMsg.Hello(PlayProtocol.PROTO, PlayProtocol.CAPS, null, TestKeys.ticket()))); b.send(PlayCodec.encode(TestRights.create()))
        val e = b.await("error")!!
        assertEquals("PLAY_BUSY", e["reason"]); assertEquals(true, e["retryable"]); assertEquals(1, srv.rooms().size)
    }

    @Test fun tenWrongCodesFromOneIpThenEvenTheRightCodeIsRefused() {
        val srv = server()
        val tv = ws(srv, xff = "203.0.113.50"); tv.send(PlayCodec.encode(ClientMsg.Hello(PlayProtocol.PROTO, PlayProtocol.CAPS, null, TestKeys.ticket()))); tv.send(PlayCodec.encode(TestRights.create()))
        val code = tv.await("welcome")!!["code"] as String
        repeat(3) { n -> val c = ws(srv, xff = "203.0.113.51"); repeat(10) { i -> c.send(PlayCodec.encode(ClientMsg.Join("ZZZZ%04d".format(n * 10 + i), "Awa", null, dev(), false))); assertEquals("PLAY_BAD_CODE", c.await("error")?.get("reason")) } }
        val p = ws(srv, xff = "203.0.113.51")
        p.send(PlayCodec.encode(ClientMsg.Join(code, "Awa", null, dev(), false)))
        assertEquals("PLAY_BAD_CODE", p.await("error")?.get("reason"), "30 codes faux en 5 min : l'adresse est bloquée, même pour le bon code")
        val q = ws(srv, xff = "203.0.113.52")
        q.send(PlayCodec.encode(ClientMsg.Join(code, "Bello", null, dev(), false)))
        assertEquals("PLAYER", q.await("welcome")?.get("role"), "une autre adresse n'est pas touchée")
    }

    // ---- X-Forwarded-For : jamais cru hors du réseau de confiance, dernier saut seulement ----

    @Test fun clientIpTakesTheLastHopOnlyFromATrustedProxy() {
        val trusted = listOf("127.0.0.0/8", "::1/128", "172.16.0.0/12").mapNotNull { Cidr.parse(it) }
        val proxy = InetAddress.getByName("172.18.0.5"); val stranger = InetAddress.getByName("198.51.100.77")
        assertEquals("203.0.113.9", ClientIp.resolve(proxy, "1.2.3.4, 203.0.113.9", trusted), "le dernier saut (celui que nginx a écrit)")
        assertEquals("203.0.113.9", ClientIp.resolve(InetAddress.getByName("127.0.0.1"), "203.0.113.9", trusted))
        assertEquals("198.51.100.77", ClientIp.resolve(stranger, "203.0.113.9", trusted), "pair non fiable : l'en-tête est ignoré")
        assertFailsWith<ForwardedForError>("saut invalide du proxy de confiance : refus (400)") { ClientIp.resolve(proxy, "pas-une-ip", trusted) }
        assertFailsWith<ForwardedForError>("jamais de résolution DNS") { ClientIp.resolve(proxy, "evil.example", trusted) }
        assertEquals("172.18.0.5", ClientIp.resolve(proxy, null, trusted))
        assertEquals("v6:2001:0db8:0000:0000::/64", ClientIp.resolve(proxy, "2001:db8::1", trusted), "IPv6 : préfixe /64")
        assertFalse(trusted.any { it.contains(InetAddress.getByName("8.8.8.8")) })
    }

    @Test fun forwardedForIsIgnoredWhenTheSocketPeerIsNotTrusted() {
        // le pair de test est 127.0.0.1 : sans réseau de confiance, tout le monde partage la même adresse, quel que soit X-Forwarded-For
        val srv = server(PlayConfig(port = 0, trustedProxies = emptyList(), maxPerIp = 2, ticketPubKeys = listOf(TestKeys.pub), trustedKeys = TestRights.trustedKeys, createsPerIpPerHour = 10_000))
        ws(srv, xff = "203.0.113.1"); ws(srv, xff = "203.0.113.2")
        assertEquals(429, assertFailsWith<WsRefused> { ws(srv, xff = "203.0.113.3") }.status, "un en-tête forgé ne donne pas une IP neuve")
    }

    @Test fun overflowingOutboxClosesTheConnectionAndKeepsTheSeat() {
        val limits = ConnectionLimits(8, 100)
        val hub = PlayHub(PlayConfig(ticketPubKeys = listOf(TestKeys.pub), trustedKeys = TestRights.trustedKeys, createsPerIpPerHour = 10_000), { 1_000L }, castbridge.core.quiz.EmbeddedQuestionSource(levels = null).bank(), TicketVerifier(listOf(TestKeys.pub)), limits = limits)
        val log = ArrayList<String>()
        class Full(id: String) : PlayConn(id, "198.51.100.1", 10, 30) {
            var full = false; var closedWith: Int? = null
            override fun offer(text: String): Boolean { log += text; return !full }
            override fun close(code: Int, reason: String) { closedWith = code }
        }
        val tv = Full("tv"); limits.acquire("198.51.100.1"); hub.register(tv); tv.ticket = TestKeys.ticket()
        hub.onText(tv, PlayCodec.encode(TestRights.create()))
        val code = (castbridge.core.quiz.Json.parse(log.first { it.startsWith("{\"t\":\"welcome\"") }) as Map<*, *>)["code"] as String
        val p = Full("p"); limits.acquire("198.51.100.1"); hub.register(p)
        hub.onText(p, PlayCodec.encode(ClientMsg.Join(code, "Awa", null, dev(), false)))
        assertEquals(1, hub.rooms().single().seatCount(), "joueur assis")
        p.full = true
        hub.onText(tv, PlayCodec.encode(ClientMsg.Act(null, "mode", null, "MILLIONAIRE", 1)))   // fait partir un état vers le joueur : sa file est pleine
        assertEquals(1008, p.closedWith, "file de sortie pleine : fermeture 1008")
        assertEquals(1, hub.rooms().single().seatCount(), "le siège du joueur est gardé pour la reprise")
        assertEquals(1, limits.of("198.51.100.1"), "sa place de connexion est libérée")
    }
}
