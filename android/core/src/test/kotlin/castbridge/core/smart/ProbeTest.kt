package castbridge.core.smart

import castbridge.core.remote.smart.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.test.*

class ProbeTest {
    @Test fun portProbeOnlyConnects() {
        FakeHttp().use { h ->
            val open = PortProbe.open("127.0.0.1", listOf(h.port, 1), 300)
            assertEquals(setOf(h.port), open)
            assertTrue(h.requests.isEmpty(), "aucune requête envoyée")
        }
    }

    /** A UDP responder standing for the TV (answers to an M-SEARCH sent to it directly). */
    private fun responder(location: String, server: String = "Roku/9 UPnP/1.0"): Pair<DatagramSocket, Thread> {
        val s = DatagramSocket(0, InetAddress.getByName("127.0.0.1"))
        val t = Thread {
            val buf = ByteArray(1024); val p = DatagramPacket(buf, buf.size)
            runCatching {
                s.receive(p)
                assertTrue(String(p.data, 0, p.length).startsWith("M-SEARCH * HTTP/1.1"))
                val a = "HTTP/1.1 200 OK\r\nSERVER: $server\r\nLOCATION: $location\r\nST: upnp:rootdevice\r\n\r\n".toByteArray()
                s.send(DatagramPacket(a, a.size, p.socketAddress))
            }
        }.apply { isDaemon = true; start() }
        return s to t
    }

    @Test fun ssdpReadsTheDescriptionOfTheChosenTv() {
        FakeHttp { 200 to FingerprintTest.SAMSUNG_XML }.use { h ->
            val (sock, _) = responder("${h.url}/dd.xml")
            val info = Ssdp.discover("127.0.0.1", InetSocketAddress("127.0.0.1", sock.localPort), listenMs = 1500)!!
            assertEquals("Samsung Electronics", info.manufacturer); assertEquals("UE55TU8000", info.modelName); assertEquals("Roku/9 UPnP/1.0", info.server)
            assertEquals("${h.url}/upnp/control/AVTransport1", info.controlUrls["urn:schemas-upnp-org:service:AVTransport:1"])
            sock.close()
        }
    }

    @Test fun ssdpNeverFollowsALocationOnAnotherHost() {
        FakeHttp().use { h ->
            val (sock, _) = responder("http://10.9.9.9:80/dd.xml")
            val info = Ssdp.discover("127.0.0.1", InetSocketAddress("127.0.0.1", sock.localPort), listenMs = 1500)!!
            assertNull(info.manufacturer); assertEquals("Roku/9 UPnP/1.0", info.server); assertTrue(h.requests.isEmpty())
            sock.close()
        }
    }

    @Test fun ssdpSilenceIsNull() {
        val dead = DatagramSocket(0, InetAddress.getByName("127.0.0.1"))
        assertNull(Ssdp.discover("127.0.0.1", InetSocketAddress("127.0.0.1", dead.localPort), listenMs = 500)); dead.close()
    }

    @Test fun gatherThenIdentifyEndToEnd() {
        FakeHttp { 200 to FingerprintTest.SAMSUNG_XML }.use { h ->
            val (sock, _) = responder("${h.url}/dd.xml", "Samsung/1.0 UPnP/1.0")
            val hints = TvProbe.gather("127.0.0.1", ports = listOf(h.port, 8001), group = InetSocketAddress("127.0.0.1", sock.localPort))
            val fp = TvIdentifier.identify(hints)
            assertEquals(Vendor.SAMSUNG, fp.vendor); assertEquals(StrategyIds.SAMSUNG, fp.candidates.first())
            sock.close()
        }
    }
}
