package castbridge.play

import castbridge.core.quiz.online.ClientMsg
import castbridge.core.quiz.online.PlayCodec
import castbridge.core.quiz.online.PlayProtocol
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Le service sur une VRAIE socket (port aléatoire, 127.0.0.1) : Duel de 10 questions à graine, TROIS transports dans la même salle (WebSocket,
 * SSE + POST, long-poll) ⇒ même classement, délai de 1 à 2 s entre questions, aucune fuite, équité (DESIGN-W20 § 2.6).
 */
class PlayLoopbackTest {
    private val servers = ArrayList<PlayServer>()
    private fun server(cfg: PlayConfig = PlayConfig(webPlay = true, revocationsMode = castbridge.play.RevocationsMode.OFF, port = 0, trustedProxies = LOOPBACK, ticketPubKeys = listOf(TestKeys.pub), trustedKeys = TestRights.trustedKeys, createsPerIpPerHour = 10_000)) = PlayServer(cfg).also { it.start(); servers += it }
    @AfterTest fun stop() { servers.forEach { it.close() }; servers.clear() }

    @Test fun threeTransportsPlayTheSameDuelWithTheSameResult() {
        val srv = server()
        val run = GameRun(srv, WsWire(srv.port), listOf(
            Seat("Awa", WsWire(srv.port), rank = 0, delayMs = 100),
            Seat("Bello", SseWire(srv.port), rank = 5, delayMs = 200, cheatAtIndex = 3),
            Seat("Carine", PollWire(srv.port), rank = 10, delayMs = 300)))
        try { run.play().verify() } finally { run.stop() }
    }

    @Test fun expiredTicketIsRefusedAndNoRoomIsCreated() {
        val srv = server()
        val w = WsWire(srv.port)
        w.send(PlayCodec.encode(ClientMsg.Hello(PlayProtocol.PROTO, PlayProtocol.CAPS, null, TestKeys.ticket(now = System.currentTimeMillis() - 120_000, lifeMs = 60_000))))
        w.send(PlayCodec.encode(TestRights.create()))
        assertEquals("PLAY_TICKET_REFUSED", w.await("error")?.get("reason"))
        assertTrue(srv.rooms().isEmpty(), "aucune salle sans ticket valide")
        // sans ticket du tout
        val w2 = WsWire(srv.port); w2.send(PlayCodec.encode(TestRights.create()))
        assertEquals("PLAY_TICKET_REFUSED", w2.await("error")?.get("reason")); assertTrue(srv.rooms().isEmpty())
        // ticket signé par une AUTRE clé
        val other = java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val w3 = WsWire(srv.port)
        w3.send(PlayCodec.encode(ClientMsg.Hello(PlayProtocol.PROTO, PlayProtocol.CAPS, null, TestKeys.ticket(pair = other))))
        w3.send(PlayCodec.encode(TestRights.create()))
        assertEquals("PLAY_TICKET_REFUSED", w3.await("error")?.get("reason")); assertTrue(srv.rooms().isEmpty())
        listOf(w, w2, w3).forEach { it.close() }
    }

    @Test fun unknownProtocolVersionIsRefusedAtHello() {
        val srv = server()
        val w = WsWire(srv.port)
        w.send(PlayCodec.encode(ClientMsg.Hello(2, PlayProtocol.CAPS, null, TestKeys.ticket())))
        val e = w.await("error")!!
        assertEquals("UNSUPPORTED", e["reason"]); assertEquals(false, e["retryable"])
        w.send(PlayCodec.encode(TestRights.create()))
        assertEquals("PLAY_TICKET_REFUSED", w.await("error")?.get("reason"), "le ticket d'un hello refusé n'est pas retenu")
        w.close()
    }

    @Test fun validTicketCreatesTheRoomAndAnUnknownCodeIsRefused() {
        val srv = server()
        val tv = WsWire(srv.port)
        tv.send(PlayCodec.encode(ClientMsg.Hello(PlayProtocol.PROTO, PlayProtocol.CAPS, null, TestKeys.ticket())))
        tv.send(PlayCodec.encode(TestRights.create()))
        val w = tv.await("welcome")!!
        assertEquals("HOST", w["role"]); assertEquals(1, srv.rooms().size)
        val p = WsWire(srv.port)
        p.send(PlayCodec.encode(ClientMsg.Join("ZZZZZZZZ", "Awa", null, dev(), false)))
        assertEquals("PLAY_BAD_CODE", p.await("error")?.get("reason"))
        p.send(PlayCodec.encode(ClientMsg.Join(w["code"] as String, "Awa", null, dev(), false)))
        assertEquals("PLAYER", p.await("welcome")?.get("role"))
        assertNull(p.closeCode)
        tv.close(); p.close()
    }
}
