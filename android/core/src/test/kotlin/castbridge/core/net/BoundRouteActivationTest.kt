package castbridge.core.net

import castbridge.core.lots.HttpTvTransport
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.net.URLConnection
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.*

/**
 * « Activer la TV » par le groupe Wi-Fi Direct d'activation : l'envoi de la clé ([HttpTvTransport]) part par le réseau du groupe comme la lecture de la demande ([castbridge.core.tv.TvClient]),
 * et l'activation ne retire jamais la liaison d'un autre groupe ([BoundRoute.release]).
 */
class BoundRouteActivationTest {
    private fun binding(opened: MutableList<String> = mutableListOf()) = object : BoundRoute.Binding {
        override fun open(url: URL): URLConnection { opened += url.host; return url.openConnection() }
        override fun bind(s: Socket) {}
    }

    @Test fun aBindingIsReleasedOnlyByItsOwner() {
        val mine = binding(); val other = binding()
        BoundRoute.set("192.168.49.", mine)
        try {
            BoundRoute.release(other)
            assertTrue(BoundRoute.applies("192.168.49.1"), "la liaison d'un autre n'est pas retirée")
            BoundRoute.release(mine)
            assertFalse(BoundRoute.applies("192.168.49.1"))
            BoundRoute.release(mine)                                  // une seconde fois : sans effet, sans erreur
            BoundRoute.set("192.168.49.", other)
            BoundRoute.release(mine)
            assertTrue(BoundRoute.applies("192.168.49.1"), "une ancienne liaison ne retire pas la nouvelle")
        } finally { BoundRoute.clear() }
    }

    @Test fun theKeyIsSentThroughTheBoundRouteWithTheCodeAsPin() {
        val pin = AtomicReference<String?>(); val bodySize = AtomicReference<Int>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/activation/install") { ex ->
            pin.set(ex.requestHeaders.getFirst("X-CB-Pin")); bodySize.set(ex.requestBody.readBytes().size)
            val out = """{"installed":true,"label":"Version complète"}""".toByteArray()
            ex.sendResponseHeaders(200, out.size.toLong()); ex.responseBody.use { it.write(out) }
        }
        server.start()
        val opened = mutableListOf<String>()
        BoundRoute.set("127.0.0.", binding(opened))                    // the test stands the group's prefix in for loopback
        try {
            val r = HttpTvTransport("http://127.0.0.1:${server.address.port}", "482913").call("POST", "/api/activation/install", emptyMap(), "cbx1.AAAA.BBBB".toByteArray())
            assertEquals(200, r.status)
            assertEquals(listOf("127.0.0.1"), opened, "l'envoi passe par la liaison du groupe")
            assertEquals("482913", pin.get()); assertEquals("cbx1.AAAA.BBBB".length, bodySize.get())
            opened.clear()
            BoundRoute.clear()
            assertEquals(200, HttpTvTransport("http://127.0.0.1:${server.address.port}", "482913").call("POST", "/api/activation/install", emptyMap(), "k".toByteArray()).status)
            assertTrue(opened.isEmpty(), "sans liaison, une connexion s'ouvre comme avant")
        } finally { BoundRoute.clear(); server.stop(0) }
    }

    @Test fun anAddressOutsideTheGroupIsNeverBound() {
        val opened = mutableListOf<String>()
        BoundRoute.set("192.168.49.", binding(opened))
        try {
            BoundRoute.open(URL("http://192.168.1.20:8765/api/hello"))
            BoundRoute.open(URL("http://192.168.49.1:8765/api/hello"))
            assertEquals(listOf("192.168.49.1"), opened, "le réseau local ordinaire et Internet ne passent pas par le groupe")
        } finally { BoundRoute.clear() }
    }
}
