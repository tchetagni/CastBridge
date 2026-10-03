package castbridge.play

import castbridge.core.quiz.Json
import castbridge.core.quiz.online.ClientMsg
import castbridge.core.quiz.online.PlayCodec
import castbridge.core.quiz.online.PlayProtocol
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Routes HTTP : page, ressources, santé, capacités ; rien sous /api/ ; santé sans IP, sans code de salle, sans jeton ; page sans dépendance externe et ≤ 60 Ko. */
class HttpRoutesTest {
    private val servers = ArrayList<PlayServer>()
    private val closeables = ArrayList<Wire>()
    private fun server() = PlayServer(PlayConfig(port = 0, trustedProxies = LOOPBACK, ticketPubKeys = listOf(TestKeys.pub), trustedKeys = TestRights.trustedKeys, createsPerIpPerHour = 10_000)).also { it.start(); servers += it }
    @AfterTest fun stop() { closeables.forEach { it.close() }; servers.forEach { it.close() } }
    private val http = HttpClient.newHttpClient()
    private fun get(srv: PlayServer, path: String, method: String = "GET"): HttpResponse<String> =
        http.send(HttpRequest.newBuilder(URI("http://127.0.0.1:${srv.port}$path")).method(method, HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString())

    @Test fun pageAndResourcesAreServedWithStrictHeaders() {
        val srv = server()
        for (p in listOf("/play", "/play/", "/play/j/K7M2-QX4T", "/play/j/k7m2qx4t")) {
            val r = get(srv, p)
            assertEquals(200, r.statusCode(), p); assertTrue(r.headers().firstValue("content-type").get().startsWith("text/html"))
            assertTrue(r.body().contains("/play/play.js") && r.body().contains("lang=\"fr\""))
            val csp = r.headers().firstValue("content-security-policy").get()
            assertTrue(csp.contains("default-src 'none'") && csp.contains("script-src 'self'") && !csp.contains("unsafe-inline"), csp)
            assertEquals("nosniff", r.headers().firstValue("x-content-type-options").get())
        }
        assertEquals(200, get(srv, "/play/play.js").statusCode()); assertEquals(200, get(srv, "/play/play.css").statusCode())
        assertEquals(404, get(srv, "/play/j/%3Cscript%3E").statusCode(), "le code de l'adresse n'est jamais accepté s'il n'est pas sûr")
        assertEquals(200, get(srv, "/play", "HEAD").statusCode())
    }

    @Test fun pageHasNoExternalDependencyAndFitsSixtyKilobytes() {
        val srv = server()
        val parts = listOf("/play", "/play/play.js", "/play/play.css").map { get(srv, it).body() }
        assertTrue(parts.sumOf { it.toByteArray().size } <= 60 * 1024, "page + script + style ≤ 60 Ko")
        val all = parts.joinToString("\n")
        assertFalse(Regex("(src|href)=\"https?://|@import|url\\(\\s*['\"]?https?:|fetch\\(['\"`]https?:|new (WebSocket|EventSource)\\(['\"`](wss?|https?):", RegexOption.IGNORE_CASE).containsMatchIn(all), "aucune ressource externe")
        assertFalse(Regex("<script[^>]*>[^<]").containsMatchIn(parts[0]), "aucun script en ligne (CSP)")
        assertFalse(all.contains("/quiz/api"), "plus de transport /quiz/api/*")
        assertFalse(Regex("\\bsender\\b|\\breceiver\\b").containsMatchIn(parts[0] + parts[2]), "CastBridge / CastBridge-TV, jamais sender / receiver")
        assertTrue(parts[1].contains("sessionStorage") && parts[1].contains("/play/ws") && parts[1].contains("/play/events") && parts[1].contains("/play/state"))
        assertTrue(parts[1].contains("13 ans"), "case « 13 ans ou plus ou un parent m'accompagne »")
        assertFalse(Regex("level\\s*=\\s*['\"](GREEN|ORANGE|RED)").containsMatchIn(parts[1]), "le bandeau Partie sûre n'est jamais calculé par la page")
    }

    @Test fun healthIsJsonWithoutIpCodeOrToken() {
        val srv = server()
        val tv = WsWire(srv.port, xff = "203.0.113.77").also { closeables += it }
        tv.send(PlayCodec.encode(ClientMsg.Hello(PlayProtocol.PROTO, PlayProtocol.CAPS, null, TestKeys.ticket()))); tv.send(PlayCodec.encode(TestRights.create()))
        val w = tv.await("welcome")!!
        val r = get(srv, "/play/health")
        assertEquals(200, r.statusCode()); assertEquals("no-store", r.headers().firstValue("cache-control").get())
        val o = Json.parse(r.body()) as Map<*, *>
        assertEquals("ok", o["status"]); assertEquals(1, (o["rooms"] as Number).toInt()); assertEquals(1, (o["connections"] as Number).toInt())
        for (k in listOf("version", "proto", "memoryUsedMb", "memoryMaxMb", "uptimeSec", "maxRooms", "maxConnections")) assertTrue(k in o, "clé $k")
        assertFalse(r.body().contains("203.0.113.77") || r.body().contains("127.0.0.1"), "aucune adresse IP")
        assertFalse(r.body().contains(w["code"] as String) || r.body().contains(w["token"] as String) || r.body().contains(w["roomId"] as String), "ni code, ni jeton, ni identifiant de salle")
    }

    @Test fun capsEndpointDescribesTheProtocol() {
        val srv = server()
        val o = Json.parse(get(srv, "/play/.well-known/caps").body()) as Map<*, *>
        assertEquals("play-v1", o["name"]); assertEquals(1, (o["proto"] as Number).toInt()); assertEquals(PlayProtocol.CAPS, o["caps"])
        assertEquals(listOf("ws", "sse", "longpoll"), o["transports"]); assertEquals(25, (o["pingSeconds"] as Number).toInt())
    }

    @Test fun nothingUnderApiAndNoListingOfQuestions() {
        val srv = server()
        for (p in listOf("/api", "/api/v1/updates", "/api/play", "/quiz/api/state", "/play/questions", "/play/api/state", "/", "/play/../api", "/play/lots")) assertEquals(404, get(srv, p).statusCode(), p)
        assertEquals(400, get(srv, "/play/ws", "GET").statusCode(), "sans en-têtes d'upgrade : 400, pas de page")
    }
}
