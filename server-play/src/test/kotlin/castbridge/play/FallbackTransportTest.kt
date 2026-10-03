package castbridge.play

import castbridge.core.quiz.Json
import castbridge.core.quiz.online.ClientMsg
import castbridge.core.quiz.online.PlayCodec
import castbridge.core.quiz.online.PlayProtocol
import java.net.Socket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Les deux replis (SSE + POST, long-poll) parlent le MÊME codec que le WebSocket et rattrapent une coupure (proxy qui coupe, réseau mobile). */
class FallbackTransportTest {
    private val servers = ArrayList<PlayServer>()
    private val wires = ArrayList<Wire>()
    private fun server(cfg: PlayConfig = PlayConfig(port = 0, ticketPubKeys = listOf(TestKeys.pub))) = PlayServer(cfg).also { it.start(); servers += it }
    @AfterTest fun stop() { wires.forEach { it.close() }; servers.forEach { it.close() }; wires.clear(); servers.clear() }
    private fun <W : Wire> W.keep(): W { wires += this; return this }
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

    private fun host(srv: PlayServer): Pair<WsWire, Map<*, *>> {
        val tv = WsWire(srv.port, xff = "203.0.113.1").keep()
        tv.send(PlayCodec.encode(ClientMsg.Hello(PlayProtocol.PROTO, PlayProtocol.CAPS, null, TestKeys.ticket())))
        tv.send(PlayCodec.encode(ClientMsg.Create(null, "DUEL")))
        return tv to tv.await("welcome")!!
    }

    @Test fun wsCutThenSseResumeKeepsTheSeatAndReplaysWhatWasMissed() {
        val srv = server()
        val (tv, w) = host(srv)
        val awa = WsWire(srv.port, xff = "203.0.113.2").keep()
        awa.send(PlayCodec.encode(ClientMsg.Join(w["code"] as String, "Awa", null, null, false)))
        val welcome = awa.await("welcome")!!
        val lastSeq = (awa.await("state")!!["seq"] as Number).toLong()
        // « le proxy coupe » : la session WebSocket disparaît sans au revoir
        awa.abort(); Thread.sleep(300)
        assertEquals(1, srv.rooms().single().seatCount(), "le siège est gardé")
        tv.send(PlayCodec.encode(ClientMsg.Act(null, "mode", null, "MILLIONAIRE", 1)))   // un évènement que Awa manque
        Thread.sleep(200)
        // le client bascule en SSE et reprend avec le même jeton
        val sse = SseWire(srv.port, xff = "203.0.113.2").keep()
        sse.send(PlayCodec.encode(ClientMsg.Resume(welcome["roomId"] as String, welcome["token"] as String, lastSeq)))
        assertEquals(welcome["token"], sse.await("welcome")!!["token"], "même siège")
        assertNotNull(sse.await("replay"), "les évènements manqués sont rejoués avant l'état")
        assertEquals("MILLIONAIRE", sse.await("state")!!.m("view").m("settings")["mode"])
    }

    @Test fun sseNamesEventsByTypeAndNumbersThem() {
        val srv = server()
        val (_, w) = host(srv)
        val post = SseWire.post(srv.port, PlayCodec.encode(ClientMsg.Join(w["code"] as String, "Awa", null, null, false)), null, "https://bridge.sti-cm.com", "203.0.113.2")
        val conn = (Json.parse(post.body()) as Map<*, *>)["conn"] as String
        val req = HttpRequest.newBuilder(URI("http://127.0.0.1:${srv.port}/play/events?token=$conn")).header("X-Forwarded-For", "203.0.113.2").build()
        val resp = http.send(req, HttpResponse.BodyHandlers.ofLines())
        assertEquals("text/event-stream; charset=utf-8", resp.headers().firstValue("content-type").get())
        assertEquals("no", resp.headers().firstValue("x-accel-buffering").get())
        val lines = resp.body().limit(14).toList()
        assertTrue(lines.any { it == "event: welcome" } && lines.any { it == "event: state" } && lines.any { it == "id: 1" }, "$lines")
        resp.body().close()
    }

    @Test fun longPollKeepsMessagesUntilAcknowledgedAndAnswersEmptyAfterTheDelay() {
        val srv = server(PlayConfig(port = 0, pollMs = 400, ticketPubKeys = listOf(TestKeys.pub)))
        val (_, w) = host(srv)
        val post = SseWire.post(srv.port, PlayCodec.encode(ClientMsg.Join(w["code"] as String, "Awa", null, null, false)), null, "https://bridge.sti-cm.com", "203.0.113.2")
        val conn = (Json.parse(post.body()) as Map<*, *>)["conn"] as String
        fun poll(since: Long) = Json.parse(http.send(HttpRequest.newBuilder(URI("http://127.0.0.1:${srv.port}/play/state?token=$conn&since=$since")).header("X-Forwarded-For", "203.0.113.2").build(),
            HttpResponse.BodyHandlers.ofString()).body()) as Map<*, *>
        val first = poll(0); val msgs = first["msgs"] as List<*>
        assertTrue(msgs.size >= 2 && (msgs[0] as Map<*, *>)["t"] == "welcome")
        assertEquals(msgs.size, (poll(0)["msgs"] as List<*>).size, "réponse perdue : le même contenu est rendu tant que since n'avance pas")
        val next = (first["next"] as Number).toLong()
        val t0 = System.currentTimeMillis()
        val empty = poll(next)
        assertTrue((empty["msgs"] as List<*>).isEmpty() && (empty["next"] as Number).toLong() == next, "rien de neuf : liste vide, même curseur")
        assertTrue(System.currentTimeMillis() - t0 >= 350, "le long-poll attend (ici 400 ms au lieu de 25 s)")
    }

    @Test fun idleFallbackSessionIsClosedButTheSeatSurvivesForResume() {
        val srv = server(PlayConfig(port = 0, fallbackIdleMs = 600, tickMs = 50, ticketPubKeys = listOf(TestKeys.pub)))
        val (_, w) = host(srv)
        val sse = SseWire(srv.port, xff = "203.0.113.2").keep()
        sse.send(PlayCodec.encode(ClientMsg.Join(w["code"] as String, "Awa", null, null, false)))
        val welcome = sse.await("welcome")!!
        sse.close()   // plus de flux ni de requête
        Thread.sleep(1_500)
        val gone = SseWire.post(srv.port, PlayCodec.encode(ClientMsg.Pong("x")), sse.conn, "https://bridge.sti-cm.com", "203.0.113.2")
        assertEquals(410, gone.statusCode(), "session de repli terminée")
        assertEquals(1, srv.rooms().single().seatCount())
        val again = PollWire(srv.port, xff = "203.0.113.2").keep()
        again.send(PlayCodec.encode(ClientMsg.Resume(welcome["roomId"] as String, welcome["token"] as String, 0)))
        assertEquals(welcome["token"], again.await("welcome")!!["token"])
    }

    @Test fun postLimitsAndWrongMethods() {
        val srv = server()
        fun raw(req: String): String = Socket("127.0.0.1", srv.port).use { s -> s.getOutputStream().write(req.toByteArray()); s.getInputStream().readNBytes(400).toString(Charsets.UTF_8).lineSequence().first() }
        val h = "Host: x\r\nOrigin: https://bridge.sti-cm.com\r\n"
        assertEquals("HTTP/1.1 411 Length Required", raw("POST /play/act HTTP/1.1\r\n$h\r\n"))
        assertEquals("HTTP/1.1 413 Payload Too Large", raw("POST /play/act HTTP/1.1\r\n${h}Content-Length: 5000\r\n\r\n" + "x".repeat(10)))
        assertEquals("HTTP/1.1 405 Method Not Allowed", raw("GET /play/act HTTP/1.1\r\n$h\r\n"))
        assertEquals("HTTP/1.1 405 Method Not Allowed", raw("POST /play/health HTTP/1.1\r\n${h}Content-Length: 0\r\n\r\n"))
        assertEquals("HTTP/1.1 410 Gone", raw("GET /play/events?token=inconnu HTTP/1.1\r\n$h\r\n"))
        assertEquals("HTTP/1.1 410 Gone", raw("POST /play/act HTTP/1.1\r\n${h}X-Play-Conn: inconnu\r\nContent-Length: 2\r\n\r\n{}"))
        assertEquals("HTTP/1.1 431 Request Header Fields Too Large", raw("GET /play HTTP/1.1\r\nX: ${"a".repeat(9_000)}\r\n\r\n"))
        assertEquals("HTTP/1.1 400 Bad Request", raw("n'importe quoi\r\n\r\n"))
    }

    @Test fun postRateLimitDropsTheFallbackSession() {
        val srv = server()
        val first = SseWire.post(srv.port, PlayCodec.encode(ClientMsg.Pong("x")), null, "https://bridge.sti-cm.com", "203.0.113.4")
        val conn = (Json.parse(first.body()) as Map<*, *>)["conn"] as String
        val codes = (1..40).map { SseWire.post(srv.port, PlayCodec.encode(ClientMsg.Pong("x")), conn, "https://bridge.sti-cm.com", "203.0.113.4").statusCode() }
        assertTrue(429 in codes, "débit dépassé : 429 $codes")
        assertEquals(410, codes.last(), "puis la session est rompue (reprise par resume)")
    }

    @Test fun fallbackAndWebSocketGiveTheSameAnswersToTheSameMessages() {
        val srv = server()
        val (_, w) = host(srv)
        val code = w["code"] as String
        val msgs = listOf(PlayCodec.encode(ClientMsg.Join("ZZZZZZZZ", "Awa", null, null, false)), PlayCodec.encode(ClientMsg.Pong("x")),
            """{"t":"inconnu"}""", """{"t":"act","action":"fly"}""", PlayCodec.encode(ClientMsg.Join(code, "Awa", null, null, false)))
        fun run(w: Wire): List<String> {
            msgs.forEach { w.send(it) }
            val seen = ArrayList<String>(); val end = System.currentTimeMillis() + 2_000
            while (System.currentTimeMillis() < end && seen.count { it == "welcome" } == 0) w.next(300)?.let { seen += ((Json.parse(it) as Map<*, *>).let { o -> if (o["t"] == "error") "error:" + o["reason"] else o["t"] as String }) }
            return seen.filter { it == "welcome" || it.startsWith("error:") }
        }
        val a = run(WsWire(srv.port, xff = "203.0.113.20").keep()); val b = run(SseWire(srv.port, xff = "203.0.113.21").keep()); val c = run(PollWire(srv.port, xff = "203.0.113.22").keep())
        assertEquals(listOf("error:PLAY_BAD_CODE", "error:FORBIDDEN", "error:UNSUPPORTED", "error:BAD_REQUEST", "welcome"), a)
        assertEquals(a, b); assertEquals(a, c)
    }
}
