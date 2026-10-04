package castbridge.play.poc.client

import castbridge.core.quiz.EmbeddedQuestionSource
import castbridge.core.quiz.QuizRoom
import castbridge.core.quiz.online.PlayTvSession
import castbridge.core.quiz.online.ServerRoom
import castbridge.core.quiz.online.TvLink
import castbridge.core.ux.SignalLevel
import castbridge.play.LOOPBACK
import castbridge.play.PlayConfig
import castbridge.play.PlayServer
import castbridge.play.TestKeys
import castbridge.play.TestRights
import java.net.InetSocketAddress
import kotlin.test.*

/**
 * Le cœur client de la TV contre le VRAI service sur une vraie socket (port aléatoire, 127.0.0.1) : `PlayHttpTransport` (SSE + POST), `PlayTvSession`, `RelayAuthority`,
 * et des téléphones simulés qui ne parlent qu'à `/quiz` de leur TV. La TV derrière la « passerelle Bluetooth » est un [SlowSocksProxy] (+300 ms par sens) précédé d'une
 * [KeepAliveFront] (le rôle de nginx : la TV garde sa connexion ouverte, le service répond `Connection: close`).
 *
 * w20-04b est fusionné : une TV invitée reçoit le siège relais et les tests de deux TV tournent pour de bon.
 */
class TvClientLoopbackTest {
    private val servers = ArrayList<PlayServer>(); private val proxies = ArrayList<SlowSocksProxy>(); private val fronts = ArrayList<KeepAliveFront>(); private val tvs = ArrayList<SimTv>()
    private val bank = EmbeddedQuestionSource().bank()

    @AfterTest fun stop() { tvs.forEach { it.stop() }; proxies.forEach { it.close() }; fronts.forEach { it.close() }; servers.forEach { it.close() } }

    private fun server(duelCount: Int = 3, questionMs: Long = 8_000, fallbackIdleMs: Long = 40_000, webPlay: Boolean = true): PlayServer {
        val cfg = PlayConfig(requireProof = false, port = 0, trustedProxies = LOOPBACK, ticketPubKeys = listOf(TestKeys.pub), trustedKeys = TestRights.trustedKeys, createsPerIpPerHour = 10_000,
            createsPerIdentityPerDay = 10_000, createsPer48PerHour = 100_000, maxPerIp = 200, maxPerIpShared = 200, connPerMinute = 100_000, connPerSecond = 10_000, fallbackIdleMs = fallbackIdleMs,
            revocationsMode = castbridge.play.RevocationsMode.OFF, webPlay = webPlay)   // WEB=0 (production) : la TV entre par tvJoin (ticket + activation), seul chemin qui accorde le siège relais ; WEB=1 : joueurs distants (WebSocket sans TV)
        return PlayServer(cfg, settings = ServerRoom.Settings(duelCount = duelCount, duelQuestionMs = questionMs)).also { it.start(); servers += it }
    }

    /** Un « Internet par la passerelle » : proxy lent devant une façade keep-alive devant le service. */
    private fun gateway(srv: PlayServer, latencyMs: Long = 300, bytesPerSec: Long = 0): SlowSocksProxy {
        val front = KeepAliveFront(InetSocketAddress("127.0.0.1", srv.port)).also { fronts += it }
        return SlowSocksProxy(InetSocketAddress("127.0.0.1", front.port), latencyMs = latencyMs, bytesPerSec = bytesPerSec).also { proxies += it }
    }

    private fun tv(name: String, srv: PlayServer, proxy: SlowSocksProxy? = null, autoSkip: Boolean = false) = SimTv(name, srv.port, bank, proxy?.port, autoSkip).also { tvs += it }

    private fun waitFor(what: String, timeoutMs: Long = 30_000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) { if (cond()) return; Thread.sleep(40) }
        fail("délai dépassé : $what")
    }

    private fun create(t: SimTv, device: String): String {
        t.session.start(device, TestRights.PROD, PlayTvSession.Intent.Create(null, "DUEL"))
        try { waitFor("assise") { t.session.seated } } catch (e: AssertionError) { fail("${t.name} non assise : erreur ${t.session.authority.lastErrorOrNull()}, transports ${t.taps.map { it.state to it.failure }}, link ${t.session.link}") }
        return t.session.authority.code!!
    }

    @Test fun hostTvBehindTheGatewayPlaysADuelWithItsPhonesThroughACutAndComesBack() {
        assertTrue(KeepAlive.effective, "http.keepAlive doit être vrai pour les mesures")
        val srv = server(); val gw = gateway(srv)
        val a = tv("TV A", srv, gw, autoSkip = true)
        val code = create(a, "dev-tv-a-000001")
        val awa = a.addPhone("Awa", 150); val bello = a.addPhone("Bello", 250) { it != 1 }   // Bello se trompe à la question 2
        awa.join(code); bello.join(code)
        assertEquals(200, awa.joinStatus); assertEquals(200, bello.joinStatus)
        assertEquals(2, a.relay.phoneCount())
        assertEquals(SignalLevel.ORANGE, a.session.safety().level, "par la passerelle : orange (dégradé, pas en panne)")
        assertEquals("Internet par le téléphone (Bluetooth) · lent", a.session.safety().text)

        a.session.authority.act(null, "start", null, null, "5")
        waitFor("question 2 ouverte") { a.session.questionClock?.let { qc -> awa.lastView?.let { v -> ((v["duel"] as? Map<*, *>)?.get("index") as? Number)?.toInt() == 1 } } == true }
        gw.cut(15_000)                                                      // « la passerelle coupe » en pleine question 2
        waitFor("liaison en reprise", 10_000) { a.session.link is TvLink.Resuming }
        val orangeDuringCut = a.session.safety()
        assertEquals(SignalLevel.ORANGE, orangeDuringCut.level); assertTrue(orangeDuringCut.text.contains("en reprise"), orangeDuringCut.text)

        waitFor("fin de la partie pour la TV et ses téléphones", 120_000) { a.finalRanking != null && awa.finalRanking != null && bello.finalRanking != null }
        assertEquals(TvLink.Online, a.session.link, "la liaison est revenue")
        assertEquals(a.finalRanking, awa.finalRanking); assertEquals(a.finalRanking, bello.finalRanking, "même classement sur la TV et ses deux téléphones")
        assertEquals(setOf("Awa", "Bello"), a.finalRanking!!.toSet())
        // la question manquée (index 1) vaut 0 pour ceux qui n'ont pas pu répondre à temps
        for (p in listOf(awa, bello)) assertEquals(0L, ((p.outcomes[1]?.get("points") as? Number)?.toLong()) ?: 0L, "${p.name} : 0 à la question manquée")
        // jamais rouge pendant la partie (orange puis retour), jamais « perdue »
        assertTrue(a.linkSamples.none { it.second.startsWith("RED") || it.second.startsWith("BLACK") }, a.linkSamples.toString())
        assertTrue(a.linkSamples.any { it.second.startsWith("ORANGE") && it.second.contains("reprise") }, a.linkSamples.toString())
        assertTrue(gw.connections.get() > 0, "le trafic de la TV est bien passé par la passerelle")
        java.io.File("build").mkdirs()
        java.io.File("build/loopback-latencies-gateway.txt").writeText(latencyLine("derrière le simulateur (+300 ms par sens, coupure de 15 s)", a) + "\n")
    }

    /** Latences observées en boucle locale, sans puis avec le simulateur (+300 ms par sens) : écrites dans build/loopback-latencies.txt pour le rapport. */
    private fun latencyLine(label: String, a: SimTv): String {
        val acks = a.taps.flatMap { it.ackLatency }.map { it.second }.sorted()
        return "$label : RTT estimé par la TV ${a.session.rttMs()} ms · acks de relais ${acks.size} (médiane ${acks.getOrNull(acks.size / 2)} ms, max ${acks.lastOrNull()} ms)"
    }

    @Test fun directLoopbackLatenciesAreTinyAndTheSimulatorAddsItsDelay() {
        val srv = server(duelCount = 3, questionMs = 8_000)
        val direct = tv("TV A", srv, autoSkip = true)
        val code = create(direct, "dev-tv-a-000005")
        val p1 = direct.addPhone("Awa", 100); val p2 = direct.addPhone("Bello", 150)
        p1.join(code); p2.join(code)
        direct.session.authority.act(null, "start", null, null, "5")
        waitFor("fin de la partie directe", 90_000) { direct.finalRanking != null && p1.finalRanking != null && p2.finalRanking != null }
        val acks = direct.taps.flatMap { it.ackLatency }.map { it.second }.sorted()
        assertTrue(acks.isNotEmpty() && acks[acks.size / 2] < 400, "en boucle locale, un ack revient en moins de 400 ms : $acks")
        java.io.File("build").mkdirs()
        java.io.File("build/loopback-latencies.txt").writeText(latencyLine("direct (boucle locale)", direct) + "\n")
    }

    @Test fun serverSessionLostDuringALongCutIsResumedWithResume() {
        val srv = server(fallbackIdleMs = 2_500); val gw = gateway(srv, latencyMs = 100)
        val a = tv("TV A", srv, gw)
        create(a, "dev-tv-a-000002")
        val seatBefore = a.session.authority.token
        gw.cut(6_000)                                                      // plus longtemps que `fallbackIdleMs` : le service oublie la session de repli
        waitFor("liaison en reprise", 10_000) { a.session.link is TvLink.Resuming }
        waitFor("liaison revenue par resume", 40_000) { a.session.link == TvLink.Online && a.session.attempts >= 1 }
        assertTrue(a.taps.size >= 2, "un NOUVEAU transport a été ouvert (410 ⇒ nouvelle session + resume)")
        assertEquals(seatBefore, a.session.authority.token, "même siège après la reprise")
        assertEquals(1, srv.rooms().size)
        assertTrue(a.session.link == TvLink.Online && !a.session.stopped)
    }

    @Test fun noSecretOrTicketInAnyUrlSeenOnTheLink() {
        val srv = server(); val gw = gateway(srv, latencyMs = 50)
        val a = tv("TV A", srv, gw)
        create(a, "dev-tv-a-000003")
        Thread.sleep(500)
        val lines = gw.requestLines.toList()
        assertTrue(lines.isNotEmpty())
        assertTrue(lines.all { it == "POST /play/act" || it == "GET /play/events" || it.startsWith("GET /play/state?since=") }, lines.toString())
        assertTrue(lines.none { it.contains("cbp") || it.contains("Host-") }, lines.toString())
    }

    @Test fun twoTvsEachWithTwoRelayedPhonesPlayADuelAndTvBSurvivesACutBehindTheGateway() {
        val srv = server(webPlay = false); val gw = gateway(srv)
        val a = tv("TV A", srv, autoSkip = true); val b = tv("TV B", srv, gw)
        val code = create(a, "dev-tv-a-000004")
        b.session.start("dev-tv-b-000004", TestRights.PROD, PlayTvSession.Intent.Join(code, "TV B"))
        waitFor("TV B assise") { b.session.seated }
        val a1 = a.addPhone("Awa", 150); val a2 = a.addPhone("Aïcha", 250); val b1 = b.addPhone("Bello", 200); val b2 = b.addPhone("Bintou", 300) { it != 1 }
        a1.join(code); a2.join(code); b1.join(code)
        assertEquals(200, b1.joinStatus, "une TV invitée reçoit le siège relais (w20-04b)")
        b2.join(code)
        a.session.authority.act(null, "start", null, null, "5")
        waitFor("question 2 ouverte", 30_000) { b1.lastView?.let { v -> ((v["duel"] as? Map<*, *>)?.get("index") as? Number)?.toInt() == 1 } == true }
        gw.cut(15_000)
        waitFor("fin de la partie pour tous", 120_000) { listOf(a, b).all { it.finalRanking != null } && listOf(a1, a2, b1, b2).all { it.finalRanking != null } }
        val ranking = a.finalRanking
        assertEquals(setOf("Awa", "Aïcha", "Bello", "Bintou"), ranking!!.toSet())
        for (x in listOf(b.finalRanking, a1.finalRanking, a2.finalRanking, b1.finalRanking, b2.finalRanking)) assertEquals(ranking, x)
        assertEquals(0L, ((b2.outcomes[1]?.get("points") as? Number)?.toLong()) ?: 0L, "Bintou : 0 à la question manquée")
        assertTrue(b.linkSamples.any { it.second.startsWith("ORANGE") }, b.linkSamples.toString())
        assertTrue(b.linkSamples.none { it.second.startsWith("RED") })
    }
}
