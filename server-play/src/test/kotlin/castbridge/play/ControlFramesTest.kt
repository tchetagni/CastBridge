package castbridge.play

import castbridge.core.quiz.online.ClientMsg
import castbridge.core.quiz.online.PlayCodec
import castbridge.core.quiz.online.PlayProtocol
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ping/pong WebSocket : nginx coupe un flux muet à 75 s, donc ping toutes les 25 s ; fermeture au bout de 40 s sans rien recevoir (ici, les mêmes règles à l'échelle de la centaine de ms). */
class ControlFramesTest {
    private val servers = ArrayList<PlayServer>()
    private val raws = ArrayList<RawWs>()
    private fun server(cfg: PlayConfig) = PlayServer(cfg).also { it.start(); servers += it }
    private fun raw(srv: PlayServer, autoPong: Boolean = true) = RawWs(srv.port, mapOf("Origin" to "https://bridge.sti-cm.com", "X-Forwarded-For" to "203.0.113.${raws.size + 1}"), autoPong = autoPong).also { raws += it }
    @AfterTest fun stop() { raws.forEach { it.close() }; servers.forEach { it.close() }; raws.clear(); servers.clear() }
    private val fast = PlayConfig(port = 0, trustedProxies = LOOPBACK, pingMs = 150, pongTimeoutMs = 600, tickMs = 50, ticketPubKeys = listOf(TestKeys.pub), trustedKeys = TestRights.trustedKeys, createsPerIpPerHour = 10_000)

    @Test fun defaultsMatchTheRunbook() {
        val d = PlayConfig()
        assertEquals(25_000L, d.pingMs, "ping toutes les 25 s (nginx : 75 s)"); assertEquals(40_000L, d.pongTimeoutMs); assertEquals(25_000L, d.pollMs)
        assertTrue(d.pingMs * 3 <= 75_000L)
    }

    @Test fun serverPingsRegularlyAndAnAnsweringClientStaysConnected() {
        val srv = server(fast)
        val c = raw(srv)
        assertNull(c.closedWithin(1_800), "le client répond aux pings : jamais fermé")
        assertTrue(c.pings.get() >= 6, "pings reçus : ${c.pings.get()}")
    }

    @Test fun silentClientIsClosedYetKeepsItsSeatForResume() {
        val srv = server(fast)
        val tv = raw(srv)
        tv.sendText(PlayCodec.encode(ClientMsg.Hello(PlayProtocol.PROTO, PlayProtocol.CAPS, null, TestKeys.ticket()))); tv.sendText(PlayCodec.encode(TestRights.create()))
        val w = tv.await("welcome")!!
        val awa = raw(srv, autoPong = false)
        awa.sendText(PlayCodec.encode(ClientMsg.Join(w["code"] as String, "Awa", null, dev(), false)))
        val welcome = awa.await("welcome")!!
        val code = awa.closedWithin(4_000)
        assertNotNull(code, "sans pong ni message : fermé par le serveur")
        Thread.sleep(200)
        assertEquals(1, srv.rooms().single().seatCount(), "siège gardé")
        val back = raw(srv)
        back.sendText(PlayCodec.encode(ClientMsg.Resume(welcome["roomId"] as String, welcome["token"] as String, 0)))
        assertEquals(welcome["token"], back.await("welcome")?.get("token"), "reprise possible")
    }

    @Test fun clientCloseFrameIsAnsweredAndTheSlotFreed() {
        val srv = server(PlayConfig(port = 0, trustedProxies = LOOPBACK, maxPerIp = 1, ticketPubKeys = listOf(TestKeys.pub), trustedKeys = TestRights.trustedKeys, createsPerIpPerHour = 10_000))
        val headers = mapOf("Origin" to "https://bridge.sti-cm.com", "X-Forwarded-For" to "203.0.113.99")
        RawWs(srv.port, headers).use { c -> c.send(8, byteArrayOf(0x03, 0xe8.toByte())); assertEquals(1000, c.closedWithin(2_000)) }
        Thread.sleep(300)
        RawWs(srv.port, headers).use { assertEquals(101, it.status) }
    }

    @Test fun fragmentedTextMessageIsReassembled() {
        val srv = server(PlayConfig(port = 0, trustedProxies = LOOPBACK, ticketPubKeys = listOf(TestKeys.pub), trustedKeys = TestRights.trustedKeys, createsPerIpPerHour = 10_000))
        val c = raw(srv)
        val text = PlayCodec.encode(ClientMsg.Join("ZZZZZZZZ", "Awa", null, dev(), false)).toByteArray()
        c.send(1, text.copyOfRange(0, 10), fin = false); c.send(0, text.copyOfRange(10, text.size), fin = true)
        assertEquals("PLAY_BAD_CODE", c.await("error")?.get("reason"))
    }
}
