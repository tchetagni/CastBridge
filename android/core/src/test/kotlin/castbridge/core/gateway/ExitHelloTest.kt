package castbridge.core.gateway

import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * R-36 / R-33 (audit anti-régression 2026-10-07 b, I-8 et I-13) : la passerelle du téléphone ([Exit]) dit à son appelant que la TV a RÉPONDU à la poignée de main (HELLO_OK) : c'est le
 * moment où une connexion à l'ancien UUID `…0002` est prouvée être la passerelle d'une TV ancienne ([GatewayService.PhoneChoice.helloAnswered]) et où le téléphone redit son réseau à la TV.
 * Une connexion qui n'obtient pas de HELLO_OK (code refusé, bannière d'un serveur SSH, lien fermé) ne le déclenche jamais.
 */
class ExitHelloTest {
    private class Link(val phone: Mux, val tv: Mux, val tvSocket: Socket)

    /** Un lien d'octets comme RFCOMM : deux prises reliées en boucle locale. */
    private fun link(): Link {
        ServerSocket(0).use { ss ->
            val a = Socket("127.0.0.1", ss.localPort); val b = ss.accept()
            return Link(Mux(a.getInputStream(), a.getOutputStream()), Mux(b.getInputStream(), b.getOutputStream()), b)
        }
    }

    @Test fun theCallbackRunsAfterTheTvAnsweredHelloOk() {
        val l = link()
        val order = CopyOnWriteArrayList<String>()
        val called = CountDownLatch(1)
        thread(isDaemon = true) { l.tv.read(); order += "tv:answers"; l.tv.write(Frame(Gw.HELLO_OK, 0)) }
        thread(isDaemon = true) { runCatching { Exit(l.phone, "------", onHello = { order += "phone:onHello"; called.countDown() }).run() } }
        assertTrue(called.await(10, TimeUnit.SECONDS), "onHello jamais appelé")
        assertEquals(listOf("tv:answers", "phone:onHello"), order.toList(), "après la réponse de la TV, pas avant")
        l.tvSocket.close()
    }

    @Test fun aRefusedHelloNeverCallsIt() {
        val l = link()
        var called = false
        thread(isDaemon = true) { l.tv.read(); l.tv.write(Frame(Gw.HELLO_ERR, 0, "PIN incorrect".toByteArray())) }
        val e = runCatching { Exit(l.phone, "000000", onHello = { called = true }).run() }.exceptionOrNull()
        assertTrue(e is IOException && e.message?.contains("PIN") == true, e.toString())
        assertFalse(called, "un code refusé n'est pas une passerelle")
        l.tvSocket.close()
    }

    @Test fun anythingButHelloOkNeverCallsIt() {
        // la TV est en réalité un serveur SSH (ancien UUID …0002 d'une TV À JOUR) : il répond autre chose qu'un HELLO_OK
        val l = link()
        var called = false
        thread(isDaemon = true) { l.tv.read(); l.tv.write(Frame(99, 0, "SSH-2.0-sshd".toByteArray())) }
        val e = runCatching { Exit(l.phone, "------", onHello = { called = true }).run() }.exceptionOrNull()
        assertTrue(e is IOException, e.toString())
        assertFalse(called)
        l.tvSocket.close()
    }

    @Test fun aLinkClosedByTheTvBeforeAnyAnswerNeverCallsIt() {
        val l = link()
        var called = false
        thread(isDaemon = true) { l.tv.read(); l.tvSocket.close() }
        val e = runCatching { Exit(l.phone, "------", onHello = { called = true }).run() }.exceptionOrNull()
        assertTrue(e is IOException, e.toString())
        assertFalse(called)
    }

    @Test fun aCallbackThatThrowsDoesNotBreakTheGateway() {
        val l = link()
        val pinged = CountDownLatch(1)
        thread(isDaemon = true) {
            l.tv.read(); l.tv.write(Frame(Gw.HELLO_OK, 0))
            l.tv.write(Frame(Gw.PING, 0))
            if (l.tv.read().type == Gw.PING) pinged.countDown()
        }
        thread(isDaemon = true) { runCatching { Exit(l.phone, "------", onHello = { throw IllegalStateException("boom") }).run() } }
        assertTrue(pinged.await(10, TimeUnit.SECONDS), "la passerelle continue de répondre après un rappel qui plante")
        l.tvSocket.close()
    }
}
