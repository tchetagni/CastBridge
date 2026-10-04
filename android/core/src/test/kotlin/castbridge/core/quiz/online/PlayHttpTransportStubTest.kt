package castbridge.core.quiz.online

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Le transport de la TV contre un faux service HTTP (JDK) : ce que le vrai service ne permet pas de provoquer à volonté (réponse perdue, `pong` lent, horloge).
 * Audit Opus : mutations A (ticket jamais renouvelé), E (envoi d'établissement rejoué après avoir atteint le service) et M-8 (un `pong` lent ne retarde pas les réponses).
 */
class PlayHttpTransportStubTest {
    private class Post(val body: String, val ticket: String?, val atMs: Long)

    private val posts = CopyOnWriteArrayList<Post>()
    private var server: HttpServer? = null
    private val transports = ArrayList<PlayHttpTransport>()
    @Volatile private var behaviour: (Post) -> Unit = {}

    private fun start(): Int {
        val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        s.executor = Executors.newCachedThreadPool { r -> Thread(r).apply { isDaemon = true } }
        s.createContext("/play/act") { ex ->
            val p = Post(ex.requestBody.readBytes().toString(Charsets.UTF_8), ex.requestHeaders.getFirst("X-Play-Ticket"), System.currentTimeMillis())
            posts += p
            runCatching { behaviour(p) }
            val body = "{\"ok\":true}".toByteArray()
            if (posts.size == 1 || p.body.contains("\"t\":\"hello\"")) ex.responseHeaders.add("Set-Cookie", "__Host-cbp=sess123; HttpOnly; Secure")
            ex.sendResponseHeaders(200, body.size.toLong()); ex.responseBody.use { it.write(body) }
        }
        s.createContext("/play/events") { ex ->   // flux muet : la TV écoute, rien n'arrive
            ex.responseHeaders.add("Content-Type", "text/event-stream"); ex.sendResponseHeaders(200, 0)
            runCatching { Thread.sleep(15_000) }; ex.close()
        }
        s.start(); server = s
        return s.address.port
    }

    @AfterTest fun stop() { transports.forEach { runCatching { it.close() } }; server?.stop(0) }

    private fun transport(port: Int, clock: () -> Long = System::currentTimeMillis, refresh: (() -> String?)? = null, readTimeout: Int = 20_000) =
        PlayHttpTransport("http://127.0.0.1:$port", "T-first", proxy = { null }, clock = clock, postReadTimeoutMs = readTimeout, sleeper = {}, refreshTicket = refresh).also { transports += it }

    private fun waitFor(ms: Long = 3_000, cond: () -> Boolean): Boolean { val end = System.currentTimeMillis() + ms; while (System.currentTimeMillis() < end) { if (cond()) return true; Thread.sleep(10) }; return cond() }

    @Test fun theTicketIsRenewedBeforeItsAgeLimit() {
        val port = start(); var now = 0L; var renewed = 0
        val t = transport(port, clock = { now }, refresh = { renewed++; "T-fresh-$renewed" })
        t.send(PlayCodec.encode(ClientMsg.Hello(PlayProtocol.PROTO, PlayProtocol.CAPS, null, "T-first")))
        assertTrue(waitFor { posts.size == 1 }); assertEquals("T-first", posts[0].ticket)
        now = 9 * 60_000L   // 9 minutes de partie : le ticket de 10 minutes touche à sa fin
        t.send(PlayCodec.encode(ClientMsg.Pong("p1")))
        assertTrue(waitFor { posts.size == 2 })
        assertEquals("T-fresh-1", posts[1].ticket, "le POST de la 9e minute porte un ticket neuf")
    }

    @Test fun anEstablishingSendThatReachedTheServiceIsNeverReplayed() {
        val port = start()
        behaviour = { Thread.sleep(1_500) }   // le service a reçu et agit, mais la réponse arrive trop tard pour la TV (liaison EDGE)
        val t = transport(port, readTimeout = 300)
        t.send(PlayCodec.encode(ClientMsg.Create(null, "DUEL", "cbx1.a")))
        assertTrue(waitFor { t.state == PlayTransport.Status.CLOSED }, "l'établissement ne va pas plus loin")
        Thread.sleep(2_500)
        assertEquals(1, posts.size, "un `create` déjà livré n'est pas rejoué : ni salle en double, ni quota d'essai consommé deux fois")
    }

    @Test fun aSlowPongNeverDelaysARelayedAnswer() {
        val port = start()
        val t = transport(port)
        t.send(PlayCodec.encode(ClientMsg.Hello(PlayProtocol.PROTO, PlayProtocol.CAPS, null, "T-first")))
        assertTrue(waitFor { posts.size == 1 })
        behaviour = { p -> if (p.body.contains("\"t\":\"pong\"")) Thread.sleep(2_500) }   // le service (ou la liaison) met 2,5 s à répondre au pong
        t.send(PlayCodec.encode(ClientMsg.Pong("p1")))
        assertTrue(waitFor { posts.size == 2 })
        val sentAt = System.currentTimeMillis()
        t.send(PlayCodec.encode(ClientMsg.RelayAct("seat-1", "q1", 2, 800, 7)))
        assertTrue(waitFor(2_000) { posts.size == 3 }, "la réponse relayée part sans attendre le pong")
        val delay = posts[2].atMs - sentAt
        assertTrue(delay < 800, "la réponse d'un joueur ne doit pas être sérialisée derrière un pong (attendu < 800 ms, mesuré $delay ms)")
    }
}
