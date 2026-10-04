package castbridge.play

import castbridge.core.quiz.online.ClientMsg
import castbridge.core.quiz.online.PlayCodec
import castbridge.core.quiz.online.PlayProtocol
import java.net.Socket
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** I6 : à l'arrêt (SIGTERM) le service annonce la maintenance aux salles, refuse les nouvelles salles, laisse un délai de grâce puis ferme. */
class DrainTest {
    private val servers = ArrayList<PlayServer>()
    private val wires = ArrayList<Wire>()
    @AfterTest fun stop() { wires.forEach { it.close() }; servers.forEach { it.close() } }
    private fun host(srv: PlayServer, ip: String): Pair<WsWire, Map<*, *>> {
        val tv = WsWire(srv.port, xff = ip).also { wires += it }
        tv.send(PlayCodec.encode(ClientMsg.Hello(PlayProtocol.PROTO, PlayProtocol.CAPS, null, TestKeys.ticket())))
        tv.send(PlayCodec.encode(TestRights.create()))
        return tv to tv.await("welcome")!!
    }

    @Test fun drainingRefusesNewRoomsTellsTheExistingOnesAndClosesAfterTheGrace() {
        val srv = PlayServer(PlayConfig(webPlay = true, revocationsMode = castbridge.play.RevocationsMode.OFF, port = 0, trustedProxies = LOOPBACK, ticketPubKeys = listOf(TestKeys.pub), trustedKeys = TestRights.trustedKeys, createsPerIpPerHour = 10_000)).start().also { servers += it }
        val (tv, w) = host(srv, "203.0.113.1")
        val p = WsWire(srv.port, xff = "203.0.113.2").also { wires += it }
        p.send(PlayCodec.encode(ClientMsg.Join(w["code"] as String, "Awa", null, dev(), false))); assertNotNull(p.await("welcome"))
        srv.hub.startDrain()
        for (c in listOf(tv, p)) {
            val e = c.await("error")!!
            assertEquals("PLAY_MAINTENANCE", e["reason"]); assertEquals(true, e["retryable"])
            assertTrue((e["message"] as String).contains("maintenance"), "message en français : ${e["message"]}")
        }
        val late = WsWire(srv.port, xff = "203.0.113.3").also { wires += it }
        late.send(PlayCodec.encode(ClientMsg.Hello(PlayProtocol.PROTO, PlayProtocol.CAPS, null, TestKeys.ticket()))); late.send(PlayCodec.encode(TestRights.create()))
        assertEquals("PLAY_MAINTENANCE", late.await("error")?.get("reason"))
        assertEquals(1, srv.rooms().size, "aucune nouvelle salle pendant l'arrêt")
        val t0 = System.currentTimeMillis()
        srv.drain(400)
        assertTrue(System.currentTimeMillis() - t0 >= 350, "le délai de grâce est respecté")
        assertFailsWith<java.io.IOException> { Socket("127.0.0.1", srv.port).use { } }
    }
}
