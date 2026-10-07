package castbridge.core.gateway

import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Un serveur qui PARLE LE PREMIER (la bannière d'un serveur SSH, que le tunnel d'assistance traverse) envoie ses octets dès l'ouverture : le téléphone les transmet derrière
 * `OPEN_OK`, et la TV doit déjà avoir enregistré le flux quand ils arrivent, sinon ils sont perdus et le client attend sans fin. (Course trouvée en écrivant les tests du relais :
 * le flux n'était enregistré qu'après la réponse SOCKS.) Ici le « téléphone » est faux et envoie `OPEN_OK`, `DATA` et `EOF` d'un seul souffle, comme un serveur qui parle tout de suite.
 */
class GatewaySpeaksFirstTest {
    private val banner = "SSH-2.0-CastBridge-test\r\n"
    private val entry = Entry({ it == "123456" }, port = 0)
    private var socksPort = 0

    @BeforeTest fun setUp() { socksPort = entry.startSocks() }
    @AfterTest fun tearDown() { entry.stop() }

    private fun attachFakePhone() {
        ServerSocket(0).use { ss ->
            val a = Socket("127.0.0.1", ss.localPort); val b = ss.accept()
            val phone = Mux(a.getInputStream(), a.getOutputStream())
            thread(isDaemon = true) { entry.attach(Mux(b.getInputStream(), b.getOutputStream()), "phone") }
            thread(isDaemon = true) {
                runCatching {
                    phone.write(Frame(Gw.HELLO, 0, (Gw.MAGIC + "123456").toByteArray())); phone.read()
                    while (true) {
                        val f = phone.read()
                        if (f.type == Gw.OPEN) {
                            phone.write(Frame(Gw.OPEN_OK, f.stream))
                            phone.write(Frame(Gw.DATA, f.stream, banner.toByteArray()))      // le serveur a parlé tout de suite
                            phone.write(Frame(Gw.EOF, f.stream))
                        }
                    }
                }
            }
        }
        repeat(100) { if (entry.connected) return; Thread.sleep(20) }
        fail("gateway never attached")
    }

    @Test fun theBannerOfAServerThatSpeaksFirstAlwaysArrives() {
        attachFakePhone()
        repeat(60) { i ->
            Socket(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort))).use { s ->
                s.soTimeout = 3_000
                s.connect(InetSocketAddress.createUnresolved("ssh.exemple.cm", 2200), 5_000)
                assertEquals(banner, String(s.getInputStream().readBytes()), "connexion n° $i")
            }
        }
    }
}
