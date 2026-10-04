package castbridge.play.poc

import castbridge.core.quiz.online.ClientMsg
import castbridge.core.quiz.online.PlayCodec
import castbridge.play.LOOPBACK
import castbridge.play.PlayConfig
import castbridge.play.PlayServer
import castbridge.play.RevocationsMode
import castbridge.play.TestKeys
import castbridge.play.TestRights
import castbridge.play.WsWire
import castbridge.play.dev
import castbridge.play.http
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** w20-04b : la liste des révocations ne peut plus être désactivée par un réglage de staging ; le mode `off` est explicite, borné et VISIBLE. */
class RevocationsModeTest {
    private val servers = ArrayList<PlayServer>()
    private val wires = ArrayList<WsWire>()
    @AfterTest fun stop() { wires.forEach { it.close() }; servers.forEach { it.close() } }

    private fun server(mode: RevocationsMode, url: String? = null) = PlayServer(PlayConfig(requireProof = false, port = 0, trustedProxies = LOOPBACK, ticketPubKeys = listOf(TestKeys.pub), trustedKeys = TestRights.trustedKeys,
        createsPerIpPerHour = 10_000, webPlay = false, revocationsMode = mode, revocationsUrl = url)).also { it.start(); servers += it }

    private fun tv(srv: PlayServer, tv: TestRights.Tv): WsWire = WsWire(srv.port, origin = null, ticket = TestKeys.ticket(deviceCode = tv.code)).also { wires += it }

    private fun health(srv: PlayServer): String =
        http.send(HttpRequest.newBuilder(URI("http://127.0.0.1:${srv.port}/play/health")).GET().build(), HttpResponse.BodyHandlers.ofString()).body()

    @Test fun directTogetherWithTrustedProxiesIsRefusedAtStartup() {
        val proxies = "CASTBRIDGE_PLAY_TRUSTED_PROXIES" to "10.0.0.1/32"
        val e = assertFailsWith<IllegalStateException> { PlayConfig.fromEnv({ mapOf("CASTBRIDGE_PLAY_DIRECT" to "1", proxies)[it] }) }
        assertTrue(e.message!!.contains("CASTBRIDGE_PLAY_DIRECT") && e.message!!.contains("CASTBRIDGE_PLAY_TRUSTED_PROXIES"), e.message)
        // l'accès direct reste possible SANS proxy de confiance (staging)
        assertEquals(RevocationsMode.ON, PlayConfig.fromEnv({ mapOf("CASTBRIDGE_PLAY_DIRECT" to "1")[it] }).revocationsMode)
    }

    @Test fun offIsExplicitBoundedAndNeverTheDefault() {
        val base = mapOf("CASTBRIDGE_PLAY_TRUSTED_PROXIES" to "10.0.0.1/32")
        assertEquals(RevocationsMode.ON, PlayConfig.fromEnv({ (base + ("CASTBRIDGE_PLAY_REVOCATIONS_URL" to "https://bridge.sti-cm.com/api/v1/revocations"))[it] }).revocationsMode)
        // sans liste ni mode explicite : refusé (fermé)
        assertFailsWith<IllegalStateException> { PlayConfig.fromEnv({ base[it] }) }
        // off : permis pour le POC avec au plus 20 salles ; 21 (ou le défaut de 400) refusé
        val off = base + ("CASTBRIDGE_PLAY_REVOCATIONS" to "off")
        assertFailsWith<IllegalStateException> { PlayConfig.fromEnv({ off[it] }) }
        assertFailsWith<IllegalStateException> { PlayConfig.fromEnv({ (off + ("CASTBRIDGE_PLAY_MAX_ROOMS" to "21"))[it] }) }
        assertEquals(RevocationsMode.OFF, PlayConfig.fromEnv({ (off + ("CASTBRIDGE_PLAY_MAX_ROOMS" to "20"))[it] }).revocationsMode)
        assertFailsWith<IllegalStateException> { PlayConfig.fromEnv({ (off + ("CASTBRIDGE_PLAY_REVOCATIONS" to "peut-etre"))[it] }) }
    }

    @Test fun withRevocationsOnAndNoAcceptedListCreateAndJoinAreRefusedRetryably() {
        val srv = server(RevocationsMode.ON)
        val a = tv(srv, TestRights.tv)
        a.send(PlayCodec.encode(TestRights.create()))
        val refusedCreate = a.await("error")
        assertNotNull(refusedCreate, "create refusé")
        assertEquals("PLAY_MAINTENANCE", refusedCreate["reason"]); assertEquals(true, refusedCreate["retryable"])
        val b = tv(srv, TestRights.otherTv)
        b.send(PlayCodec.encode(ClientMsg.Join("ZZZZZZZZ", "TV Chambre", null, dev(), true, TestRights.activation(device = TestRights.otherTv))))
        val refusedJoin = b.await("error")
        assertNotNull(refusedJoin, "join refusé")
        assertEquals("PLAY_MAINTENANCE", refusedJoin["reason"]); assertEquals(true, refusedJoin["retryable"])
        assertTrue(health(srv).contains("\"revocations\":\"none\""), health(srv))
        assertEquals(0, srv.rooms().size)
    }

    @Test fun withRevocationsOffCreateAndJoinWorkAndTheServiceSaysSo() {
        val captured = ByteArrayOutputStream()
        val before = System.err
        System.setErr(PrintStream(captured, true, "UTF-8"))
        val srv = try { server(RevocationsMode.OFF) } finally { System.setErr(before) }
        val log = captured.toString("UTF-8")
        assertTrue(log.contains("révocations désactivées") && log.contains("\"log.level\":\"warn\""), "avertissement au démarrage : $log")
        assertTrue(health(srv).contains("\"revocations\":\"disabled\""), health(srv))
        val a = tv(srv, TestRights.tv)
        a.send(PlayCodec.encode(TestRights.create()))
        val code = a.await("welcome")!!["code"] as String
        val b = tv(srv, TestRights.otherTv)
        b.send(PlayCodec.encode(ClientMsg.Join(code, "TV Chambre", null, dev(), true, TestRights.activation(device = TestRights.otherTv))))
        assertNotNull(b.await("welcome"), "join accepté en mode off")
        assertEquals(1, srv.rooms().size)
    }
}
