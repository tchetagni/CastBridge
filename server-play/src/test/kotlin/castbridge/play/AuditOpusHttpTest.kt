package castbridge.play

import castbridge.core.quiz.online.ClientMsg
import castbridge.core.quiz.online.PlayCodec
import castbridge.core.quiz.online.PlayProtocol
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Audit Opus, couche HTTP du service sur un VRAI port : H-4 (long-polls d'une session et d'une adresse), M-6 (ticket jugé à la création de session), M-1 (taille du POST de repli),
 * honnêteté de `/play/health` et du sondage de la TV (révocations éteintes, preuve de possession).
 */
class AuditOpusHttpTest {
    private val servers = ArrayList<PlayServer>()
    @AfterTest fun stop() { servers.forEach { it.close() }; servers.clear() }

    private fun server(vararg tweak: Pair<String, Any>): PlayServer {
        val m = tweak.toMap()
        val cfg = PlayConfig(webPlay = false, revocationsMode = RevocationsMode.OFF, port = 0, trustedProxies = LOOPBACK, ticketPubKeys = listOf(TestKeys.pub), trustedKeys = TestRights.trustedKeys,
            createsPerIpPerHour = 10_000, requireProof = m["requireProof"] as Boolean? ?: false, maxConnections = m["maxConnections"] as Int? ?: 3_000, maxPerIp = m["maxPerIp"] as Int? ?: 24,
            pollMs = m["pollMs"] as Long? ?: 25_000, maxHeldPerAddress = m["maxHeld"] as Int? ?: 48, createsPer48PerHour = 1_000_000)
        return PlayServer(cfg).start().also { servers += it }
    }

    private fun get(srv: PlayServer, path: String, conn: String? = null, xff: String? = null): CompletableFuture<HttpResponse<String>> {
        val b = HttpRequest.newBuilder(URI("http://127.0.0.1:${srv.port}$path")).timeout(Duration.ofSeconds(8)).GET()
        Cred.apply(b, conn); xff?.let { b.header("X-Forwarded-For", it) }
        return http.sendAsync(b.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun post(srv: PlayServer, text: String, conn: String? = null, ticket: String? = null, xff: String? = null): HttpResponse<String> {
        val b = HttpRequest.newBuilder(URI("http://127.0.0.1:${srv.port}/play/act")).header("Content-Type", "application/json").timeout(Duration.ofSeconds(8)).POST(HttpRequest.BodyPublishers.ofString(text))
        Cred.apply(b, conn); ticket?.let { b.header("X-Play-Ticket", it) }; xff?.let { b.header("X-Forwarded-For", it) }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun hello(ticket: String = TestKeys.ticket()) = PlayCodec.encode(ClientMsg.Hello(PlayProtocol.PROTO, PlayProtocol.CAPS, null, ticket))

    /** Une session de repli de TV (ticket au premier POST) ; rend son identifiant. */
    private fun session(srv: PlayServer, xff: String): String {
        val r = post(srv, hello(), ticket = TestKeys.ticket(), xff = xff)
        assertEquals(200, r.statusCode(), r.body())
        return Cred.of(r)!!
    }

    // ---------------------------------------------------------------- H-4

    @Test fun oneSessionCannotExhaustTheServiceWithConcurrentLongPolls() {
        val srv = server("maxConnections" to 5, "maxPerIp" to 2, "pollMs" to 3_000L)
        val conn = session(srv, "203.0.113.1")
        val polls = (1..12).map { get(srv, "/play/state?since=0", conn, "203.0.113.1") }
        Thread.sleep(500)
        val caps = try { get(srv, "/play/.well-known/caps", xff = "203.0.113.77").get(3, java.util.concurrent.TimeUnit.SECONDS) } catch (e: Exception) { fail("un autre client n'obtient plus de réponse : une session a épuisé les sockets du service ($e)") }
        assertEquals(200, caps.statusCode())
        polls.forEach { runCatching { it.get(6, java.util.concurrent.TimeUnit.SECONDS) } }
    }

    @Test fun heldSocketsPerAddressAreCappedAndIpv6IsGroupedBySixtyFour() {
        val srv = server("maxHeld" to 3, "maxPerIp" to 10, "pollMs" to 2_500L)
        // quatre sessions de la MÊME adresse /64 (quatre /128 différentes)
        val addrs = listOf("2001:db8:1:2::1", "2001:db8:1:2::2", "2001:db8:1:2::3", "2001:db8:1:2::4")
        val conns = addrs.map { session(srv, it) }
        val held = (0..2).map { get(srv, "/play/state?since=0", conns[it], addrs[it]) }
        Thread.sleep(400)
        val fourth = get(srv, "/play/state?since=0", conns[3], addrs[3]).get(5, java.util.concurrent.TimeUnit.SECONDS)
        assertEquals(429, fourth.statusCode(), "la 4e socket tenue de la même adresse (/64) est refusée : ${fourth.body()}")
        // une autre adresse n'est pas touchée
        val other = session(srv, "2001:db8:9:9::1")
        val ok = get(srv, "/play/state?since=0", other, "2001:db8:9:9::1")
        Thread.sleep(300)
        assertTrue(!ok.isDone || ok.get().statusCode() == 200, "une autre adresse tient sa socket")
        held.forEach { runCatching { it.get(6, java.util.concurrent.TimeUnit.SECONDS) } }
    }

    // ---------------------------------------------------------------- M-6

    @Test fun theTicketIsJudgedWhenTheSessionIsCreatedNotOnEveryPost() {
        val srv = server()
        val conn = session(srv, "203.0.113.5")
        val pong = post(srv, PlayCodec.encode(ClientMsg.Pong("p1")), conn = conn, ticket = null, xff = "203.0.113.5")
        assertEquals(200, pong.statusCode(), "la session existe : son secret de 128 bits suffit, un ticket échu en cours de partie ne la coupe pas : ${pong.body()}")
        val anon = post(srv, hello(), ticket = null, xff = "203.0.113.6")
        assertEquals(403, anon.statusCode(), "créer une session sans ticket valide reste refusé")
        val expired = post(srv, hello(), ticket = TestKeys.ticket(iat = System.currentTimeMillis() - 3_600_000L), xff = "203.0.113.6")
        assertEquals(403, expired.statusCode())
    }

    // ---------------------------------------------------------------- M-1

    @Test fun theFallbackPostAcceptsALargeCreateAndStillRefusesHugeBodies() {
        val srv = server()
        val big = PlayCodec.encode(ClientMsg.Create(null, "DUEL", "cbx1." + "A".repeat(6_000)))
        val r = post(srv, big, ticket = TestKeys.ticket(), xff = "203.0.113.8")
        assertEquals(200, r.statusCode(), "un create de ≈ 6 100 caractères (école : 20 achats) passe par le POST de repli, seul transport de la TV : ${r.body()}")
        val huge = PlayCodec.encode(ClientMsg.Create(null, "DUEL", "cbx1." + "A".repeat(4_000))).replace("DUEL", "DUEL" + " ".repeat(9_500))
        assertEquals(413, post(srv, huge, ticket = TestKeys.ticket(), xff = "203.0.113.9").statusCode(), "au-delà du plafond, 413")
    }

    // ---------------------------------------------------------------- honnêteté de la santé et du sondage

    @Test fun healthAndCapsSayHonestlyWhatIsNotEnforced() {
        val srv = server()
        val health = get(srv, "/play/health").get(3, java.util.concurrent.TimeUnit.SECONDS).body()
        assertTrue(health.contains("\"revocations\":\"disabled\""), health)
        assertTrue(health.contains("\"revocationsEnforced\":false"), "la santé dit que les révocations ne sont PAS appliquées : $health")
        assertTrue(health.contains("\"proof\":\"optional\""), "requireProof=false (migration) est dit : $health")
        val strict = server("requireProof" to true)
        assertTrue(get(strict, "/play/health").get(3, java.util.concurrent.TimeUnit.SECONDS).body().contains("\"proof\":\"required\""))
        val caps = get(srv, "/play/.well-known/caps").get(3, java.util.concurrent.TimeUnit.SECONDS).body()
        assertTrue(caps.contains("\"revocations\":\"off\""), "le sondage de la TV apprend que les révocations sont éteintes : $caps")
        assertTrue(caps.contains("\"maxCreateBytes\":8192"), caps)
    }
}
