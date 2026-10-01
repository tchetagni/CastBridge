package castbridge.core.policy

import castbridge.core.owner.OwnerFrames
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.*

class OrderTransportTest {
    @Test fun serverJsonIsParsedStrictlyAndAcksCarryNothingButTechnicalFields() {
        val f = HttpOrderServer.parseFetched("""{"cursor":7,"orders":[{"tv":"AAAA-BBBB-CCCC-DDDD","token":"cbx1.a.b"}]}""")!!
        assertEquals(7, f.cursor); assertEquals(ServerOrder("AAAA-BBBB-CCCC-DDDD", "cbx1.a.b"), f.orders.single())
        assertNull(HttpOrderServer.parseFetched("{}")); assertNull(HttpOrderServer.parseFetched("<html>"))
        val a = OrderAck("0123456789abcdef", 3, "aabbccdd", AckReason.APPLIED, 2, 1000)
        val body = HttpOrderServer.acksBody(listOf(PendingAck("AAAA-BBBB-CCCC-DDDD", a)))
        assertTrue(body.contains("kid=0123456789abcdef") && body.contains("result=applied") && !body.contains("param"))
    }

    @Test fun streamLinkWritesTheHandshakeThenFramesAndReadsOrReturnsNullWhenQuiet() {
        val out = ByteArrayOutputStream()
        val reply = OrderFrames.need("0011aabb", 5)
        val link = StreamOrderLink(ByteArrayInputStream(reply), out, quietMs = 40, sleep = { })
        link.write(OrderFrames.hello(emptyMap()))
        val bytes = out.toByteArray(); assertEquals("CBTO", String(bytes, 0, 4)); assertEquals(OrderFrames.HELLO, bytes[4].toInt())
        assertEquals(OrderFrames.NEED, link.read()!!.type); assertNull(link.read(), "quiet")
        assertEquals(OwnerFrames.MAGIC, "CBTO")
    }
}
