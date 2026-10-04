package castbridge.play.poc

import castbridge.play.LOOPBACK
import castbridge.play.PlayConfig
import castbridge.play.PlayPageController
import castbridge.play.PlayServer
import castbridge.play.RevocationsMode
import castbridge.play.TestKeys
import castbridge.play.TestRights
import castbridge.play.WsRefused
import castbridge.play.WsWire
import castbridge.play.http
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** w20-04b : aucune page web de jeu. `CASTBRIDGE_PLAY_WEB=0` (défaut) : `/play` informe, aucun navigateur (en-tête `Origin`) n'atteint le jeu, `WEB=1` n'existe qu'en staging. */
class WebClosedTest {
    private val servers = ArrayList<PlayServer>()
    @AfterTest fun stop() { servers.forEach { it.close() } }

    private fun server(web: Boolean = false) = PlayServer(PlayConfig(port = 0, trustedProxies = LOOPBACK, ticketPubKeys = listOf(TestKeys.pub), trustedKeys = TestRights.trustedKeys,
        createsPerIpPerHour = 10_000, webPlay = web, revocationsMode = RevocationsMode.OFF)).also { it.start(); servers += it }

    private fun get(srv: PlayServer, path: String, vararg headers: Pair<String, String>): HttpResponse<String> =
        http.send(HttpRequest.newBuilder(URI("http://127.0.0.1:${srv.port}$path")).also { b -> headers.forEach { (k, v) -> b.header(k, v) } }.GET().build(), HttpResponse.BodyHandlers.ofString())

    private fun post(srv: PlayServer, vararg headers: Pair<String, String>): HttpResponse<String> =
        http.send(HttpRequest.newBuilder(URI("http://127.0.0.1:${srv.port}/play/act")).header("Content-Type", "application/json").also { b -> headers.forEach { (k, v) -> b.header(k, v) } }
            .POST(HttpRequest.BodyPublishers.ofString("""{"t":"pong","id":"x"}""")).build(), HttpResponse.BodyHandlers.ofString())

    @Test fun theGamePageIsReplacedByAStaticInformationPage() {
        val srv = server()
        for (p in listOf("/play", "/play/", "/play/j/K7M2QX4T")) {
            val r = get(srv, p)
            assertEquals(200, r.statusCode(), p)
            assertTrue(r.headers().firstValue("content-type").get().startsWith("text/html"))
            assertFalse(r.body().contains("<script", ignoreCase = true), "aucun script : $p")
            assertFalse(r.body().contains("<form", ignoreCase = true), "aucun formulaire : $p")
            assertTrue(r.body().contains("CastBridge-TV") && r.body().contains("Les parties en ligne se jouent sur CastBridge-TV"))
            assertEquals("no-cache", r.headers().firstValue("cache-control").get())
            assertEquals(PlayPageController.INFO_CSP, r.headers().firstValue("content-security-policy").get())
        }
        assertEquals(200, http.send(HttpRequest.newBuilder(URI("http://127.0.0.1:${srv.port}/play")).method("HEAD", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString()).statusCode())
        assertEquals(404, get(srv, "/play/play.js").statusCode(), "le script de la page de jeu n'est pas servi")
        assertEquals(404, get(srv, "/play/play.css").statusCode(), "la feuille de style de la page de jeu n'est pas servie")
        assertTrue(PlayPageController::class.java.getResourceAsStream("/static/play/info.html")!!.readBytes().size <= 4_096, "info.html ≤ 4 Ko")
    }

    @Test fun aBrowserNeverReachesTheGame() {
        val srv = server()
        val origin = "Origin" to "https://bridge.sti-cm.com"
        val ticket = TestKeys.ticket()
        // WebSocket : refusée même avec une origine de la liste blanche ET un ticket valide (OriginCheck seul la laisserait entrer)
        val refused = assertFailsWith<WsRefused> { WsWire(srv.port, origin = "https://bridge.sti-cm.com", ticket = ticket) }
        assertEquals(403, refused.status)
        assertEquals(403, assertFailsWith<WsRefused> { WsWire(srv.port, origin = "null", ticket = ticket) }.status)
        // repli : POST, flux, long-poll
        val p = post(srv, origin, "X-Play-Ticket" to ticket)
        assertEquals(403, p.statusCode()); assertTrue(p.body().contains("origine refusée"), p.body())
        assertEquals(403, get(srv, "/play/events", origin).statusCode())
        assertEquals(403, get(srv, "/play/state?since=0", origin).statusCode())
        assertEquals(403, post(srv, "Origin" to "null", "X-Play-Ticket" to ticket).statusCode())
        // un client natif (CastBridge-TV : pas d'en-tête Origin) avec un ticket valide, lui, passe
        assertEquals(200, post(srv, "X-Play-Ticket" to TestKeys.ticket()).statusCode(), "la TV n'envoie pas d'Origin")
    }

    @Test fun withWebPlayTheGamePageAndItsOriginsWorkAsBefore() {
        val srv = server(web = true)
        val page = get(srv, "/play")
        assertTrue(page.body().contains("/play/play.js"))
        assertEquals(200, get(srv, "/play/play.js").statusCode())
        assertEquals(200, post(srv, "Origin" to "https://bridge.sti-cm.com").statusCode())
    }

    @Test fun webPlayIsClosedByDefaultAndOpensOnlyWithDirect() {
        val base = mapOf("CASTBRIDGE_PLAY_TRUSTED_PROXIES" to "10.0.0.1/32", "CASTBRIDGE_PLAY_REVOCATIONS_URL" to "https://bridge.sti-cm.com/api/v1/revocations")
        assertFalse(PlayConfig.fromEnv({ base[it] }).webPlay, "défaut : fermé")
        assertFalse(PlayConfig.fromEnv({ (base + ("CASTBRIDGE_PLAY_WEB" to "0"))[it] }).webPlay)
        assertFailsWith<IllegalStateException> { PlayConfig.fromEnv({ (base + ("CASTBRIDGE_PLAY_WEB" to "1"))[it] }) }   // sans DIRECT=1 : démarrage refusé
        assertFailsWith<IllegalStateException> { PlayConfig.fromEnv({ (base + ("CASTBRIDGE_PLAY_WEB" to "oui"))[it] }) }
        val staging = mapOf("CASTBRIDGE_PLAY_DIRECT" to "1", "CASTBRIDGE_PLAY_WEB" to "1")
        assertTrue(PlayConfig.fromEnv({ staging[it] }).webPlay, "staging : DIRECT=1 + WEB=1")
        assertTrue("CASTBRIDGE_PLAY_WEB" in PlayConfig.ENV_NAMES && "CASTBRIDGE_PLAY_MAX_RELAYED_PER_TV" in PlayConfig.ENV_NAMES && "CASTBRIDGE_PLAY_REVOCATIONS" in PlayConfig.ENV_NAMES)
    }
}
