package castbridge.core.quiz.online

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * relay-R1 § 5 : quand la TV joue par le tuyau d'un téléphone, ses requêtes au service portent l'en-tête INFORMATIF `X-CB-Via: relay` (le service n'en tire aucune autorité) ;
 * rien d'autre ne change dans le protocole du jeu.
 */
class PlayHttpTransportViaRelayTest {
    private val seen = CopyOnWriteArrayList<Pair<String, String?>>()      // (chemin, valeur de X-CB-Via)
    private var server: HttpServer? = null
    private val transports = ArrayList<PlayHttpTransport>()

    private fun start(): Int {
        val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        s.executor = Executors.newCachedThreadPool { r -> Thread(r).apply { isDaemon = true } }
        s.createContext("/play/act") { ex ->
            ex.requestBody.readBytes(); seen += "act" to ex.requestHeaders.getFirst("X-CB-Via")
            val body = "{\"ok\":true}".toByteArray()
            ex.responseHeaders.add("Set-Cookie", "__Host-cbp=sess123; HttpOnly; Secure")
            ex.sendResponseHeaders(200, body.size.toLong()); ex.responseBody.use { it.write(body) }
        }
        s.createContext("/play/events") { ex ->
            seen += "events" to ex.requestHeaders.getFirst("X-CB-Via")
            ex.responseHeaders.add("Content-Type", "text/event-stream"); ex.sendResponseHeaders(200, 0)
            runCatching { Thread.sleep(3_000) }; ex.close()
        }
        s.start(); server = s
        return s.address.port
    }

    @AfterTest fun stop() { transports.forEach { runCatching { it.close() } }; server?.stop(0) }

    private fun waitFor(ms: Long = 3_000, cond: () -> Boolean): Boolean { val end = System.currentTimeMillis() + ms; while (System.currentTimeMillis() < end) { if (cond()) return true; Thread.sleep(10) }; return cond() }

    private fun transport(port: Int, relay: () -> Boolean) =
        PlayHttpTransport("http://127.0.0.1:$port", "T-first", proxy = { null }, clock = System::currentTimeMillis, sleeper = {}, viaRelay = relay).also { transports += it }

    @Test fun overTheRelayEveryRequestSaysSo() {
        val t = transport(start()) { true }
        t.send(PlayCodec.encode(ClientMsg.Hello(PlayProtocol.PROTO, PlayProtocol.CAPS, null, "T-first")))
        assertTrue(waitFor { seen.any { it.first == "act" } && seen.any { it.first == "events" } })
        assertTrue(seen.all { it.second == "relay" }, seen.toString())
    }

    @Test fun withoutTheRelayNoSuchHeaderIsSent() {
        val t = transport(start()) { false }
        t.send(PlayCodec.encode(ClientMsg.Hello(PlayProtocol.PROTO, PlayProtocol.CAPS, null, "T-first")))
        assertTrue(waitFor { seen.any { it.first == "act" } })
        assertTrue(seen.all { it.second == null }, seen.toString())
    }

    @Test fun theHeaderFollowsTheNetworkAtEachRequest() {
        var relay = false
        val t = transport(start()) { relay }
        t.send(PlayCodec.encode(ClientMsg.Hello(PlayProtocol.PROTO, PlayProtocol.CAPS, null, "T-first")))
        assertTrue(waitFor { seen.count { it.first == "act" } == 1 })
        assertNull(seen.first { it.first == "act" }.second)
        relay = true                                       // le Wi-Fi est tombé : la partie continue par le téléphone
        t.send(PlayCodec.encode(ClientMsg.Pong("p1")))
        assertTrue(waitFor { seen.count { it.first == "act" } == 2 })
        assertEquals("relay", seen.filter { it.first == "act" }[1].second)
    }

    @Test fun theNamesAreTheDocumentedOnes() {
        assertEquals("X-CB-Via", PlayHttpTransport.VIA_HEADER)
        assertEquals("relay", PlayHttpTransport.VIA_RELAY)
    }
}
