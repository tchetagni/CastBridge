package castbridge.play.poc.client

import castbridge.core.quiz.EmbeddedQuestionSource
import castbridge.core.quiz.online.PlayTvSession
import castbridge.core.quiz.online.ServerRoom
import castbridge.core.quiz.online.ClientMsg
import castbridge.play.Bot
import castbridge.play.WsWire
import castbridge.play.dev
import castbridge.play.LOOPBACK
import castbridge.play.PlayConfig
import castbridge.play.PlayServer
import castbridge.play.TestKeys
import castbridge.play.TestRights
import java.io.File
import java.net.InetSocketAddress
import kotlin.test.*

/**
 * Critère de fluidité du propriétaire (DESIGN-W20-AMENDEMENT § 2.7) : un Duel complet à 8 joueurs relayés est fluide à 40 kbps : question affichée ≤ 1,5 s après son envoi,
 * révélation et classement ≤ 2 s, `ack` d'un relais ≤ 1,5 s. Simulateur : [SlowSocksProxy] à 5 Ko/s dans CHAQUE sens, RTT 800 ms ± 200 ms de gigue (400 ± 100 ms par sens),
 * une coupure de 5 s (« changement de cellule ») à la question 5 (la question de la coupure et la suivante sont exclues de la mesure ; la partie doit finir pour tous),
 * devant une [KeepAliveFront] (le rôle de nginx). La mesure se fait sur l'horloge du test (même JVM que le service) : « envoi serveur » = instant où le service écrit
 * l'évènement à l'entrée de la liaison ; « réception » = instant où la TV le lit.
 *

 * Résultat (voir docs/agent-reports/sonnet-w20-05a.md) : avec la coalescence, question (≤ 1,5 s), révélation et classement (≤ 2 s) TIENNENT ; l'ack ≤ 1,5 s ne tient PAS (médiane ≈ 1,5 s, pointe ≈ 2,6 s) : plancher
 * physique de 0,9 s (RTT + envoi) + attente derrière le `state` en cours d'envoi (2,8 Ko = 0,56 s à 5 Ko/s) + rafale de réponses sur 5 Ko/s. Les tests ci-dessous bornent donc l'ack à 5 s au lieu d'affirmer le critère.
 *
 * La coalescence « dernier état seulement » est celle de w20-04b (service) ; tant qu'elle n'est pas fusionnée, le simulateur la pose au point de congestion
 * ([SlowSocksProxy.coalesceStates]) : même effet, un `state` encore en file est remplacé par le suivant. Le test SANS coalescence montre que le critère de révélation échoue.
 */
class EdgeFluidityTest {
    private val servers = ArrayList<PlayServer>(); private val proxies = ArrayList<SlowSocksProxy>(); private val fronts = ArrayList<KeepAliveFront>(); private val tvs = ArrayList<SimTv>()
    private val bank = EmbeddedQuestionSource().bank()
    private val CUT_INDEX = 4

    @AfterTest fun stop() { tvs.forEach { it.stop() }; proxies.forEach { it.close() }; fronts.forEach { it.close() }; servers.forEach { it.close() } }

    private fun server(webPlay: Boolean = true): PlayServer {
        val cfg = PlayConfig(requireProof = false, port = 0, trustedProxies = LOOPBACK, ticketPubKeys = listOf(TestKeys.pub), trustedKeys = TestRights.trustedKeys, createsPerIpPerHour = 10_000,
            createsPerIdentityPerDay = 10_000, createsPer48PerHour = 100_000, maxPerIp = 200, maxPerIpShared = 200, connPerMinute = 100_000, connPerSecond = 10_000,
            revocationsMode = castbridge.play.RevocationsMode.OFF, webPlay = webPlay)   // WEB=0 (production) : la TV entre par tvJoin (ticket + activation), seul chemin qui accorde le siège relais ; WEB=1 : joueurs distants (WebSocket sans TV)
        return PlayServer(cfg, settings = ServerRoom.Settings(duelCount = 10, duelQuestionMs = 20_000)).also { it.start(); servers += it }
    }

    private fun edge(srv: PlayServer, coalesce: Boolean, tolerant: Boolean = false, controlFirst: Boolean = false): SlowSocksProxy {
        val front = KeepAliveFront(InetSocketAddress("127.0.0.1", srv.port), if (tolerant) ({ TestKeys.ticket(lifeMs = 600_000) }) else null).also { fronts += it }
        return SlowSocksProxy(InetSocketAddress("127.0.0.1", front.port), latencyMs = 400, jitterMs = 100, bytesPerSec = 5_000, coalesceStates = coalesce, controlFirst = controlFirst, seed = 7).also { proxies += it }
    }

    private fun waitFor(what: String, timeoutMs: Long, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) { if (cond()) return; Thread.sleep(50) }
        fail("délai dépassé : $what")
    }

    /** Ce qui a été mesuré sur une TV derrière un simulateur. */
    class Measure(val tvName: String, val question: Map<Int, Long>, val reveal: Map<Int, Long>, val revealState: Map<Int, Long>, val ranking: Map<Int, Long>, val acks: List<Pair<Long, Long>>,
                  val ackExcluded: Set<Long>, val gw: SlowSocksProxy, val durationMs: Long, val tv: SimTv) {
        private fun ok(m: Map<Int, Long>, limit: Long, excluded: Set<Int>) = m.filterKeys { it !in excluded }.filterValues { it > limit }
        fun questionViolations(excluded: Set<Int>) = ok(question, 1_500, excluded)
        fun revealViolations(excluded: Set<Int>) = ok(reveal, 2_000, excluded) + ok(revealState, 2_000, excluded)
        fun rankingViolations(excluded: Set<Int>) = ok(ranking, 2_000, excluded)
        val acksKept get() = acks.filter { it.first !in ackExcluded }
        fun ackViolations() = acksKept.filter { it.second > 1_500 }
        fun render(excluded: Set<Int>): String {
            fun stat(m: Map<Int, Long>) = m.filterKeys { it !in excluded }.values.sorted().let { if (it.isEmpty()) "n/a" else "min ${it.first()} · médiane ${it[it.size / 2]} · max ${it.last()} ms (${it.size} mesures)" }
            val ackV = acks.filter { it.first !in ackExcluded }.map { it.second }.sorted()
            val sizes = gw.stateSizes.filter { it.second >= 8 }.map { it.first }
            val sb = StringBuilder("== $tvName (${durationMs / 1000} s) ==\n")
            sb.append("question → TV : ${stat(question)}\n")
            sb.append("reveal → TV : ${stat(reveal)}\nétat de révélation → TV : ${stat(revealState)}\nclassement → TV : ${stat(ranking)}\n")
            sb.append("ack d'un relais : ${if (ackV.isEmpty()) "n/a" else "min ${ackV.first()} · médiane ${ackV[ackV.size / 2]} · max ${ackV.last()} ms (${ackV.size} mesures) · au-delà de 1,5 s : ${ackV.count { it > 1_500 }}/${ackV.size}"}\n")
            sb.append("résultats des acks : ${tv.taps.flatMap { it.ackResults.entries }.groupBy({ it.key }, { it.value }).mapValues { it.value.sum() }} ; durée entre annonces de questions : ${gw.events.filter { it.type == "question" }.map { it.ingressMs }.zipWithNext { x, y -> (y - x) / 1000 }} s\n")
            sb.append("octets du service vers la TV par type (nombre) : " + gw.downByType.entries.sortedByDescending { it.value.get() }.joinToString(" · ") { "${it.key} ${it.value.get()} o (${gw.countByType[it.key]?.get()})" } + "\n")
            sb.append("state à 8 joueurs : " + (if (sizes.isEmpty()) "aucun" else "${sizes.size} états, ${sizes.sorted()[sizes.size / 2]} o (médiane), min ${sizes.min()}, max ${sizes.max()}") + " ; états remplacés (coalescence) : ${gw.droppedStates.get()}\n")
            sb.append("TV vers le service : " + gw.postBodies.entries.joinToString(" · ") { "${it.key} ${gw.postCounts[it.key]?.get()}×" } + " ; total ${gw.upBytes.get()} o\n")
            sb.append("débit moyen descendant ${"%.0f".format(gw.avgDownBytesPerSec())} o/s, pointe ${gw.peakDownBytesPerSec()} o/s ; montant pointe ${gw.peakUpBytesPerSec()} o/s ; connexions SOCKS ${gw.connections.get()}\n")
            return sb.toString()
        }
    }

    private fun measure(name: String, tv: SimTv, gw: SlowSocksProxy, startedAt: Long): Measure {
        val taps = tv.taps.toList()
        fun recv(key: String) = taps.firstNotNullOfOrNull { it.received[key] }
        val q = ArrayList<Long?>(); val r = ArrayList<Long?>()
        for (e in gw.events.filter { it.type == "question" }) q += recv(e.key)?.let { it - e.ingressMs }
        for (e in gw.events.filter { it.type == "reveal" }) r += recv(e.key)?.let { it - e.ingressMs }
        val states = taps.flatMap { it.states }.sortedBy { it.first }
        fun caughtUp(i: Int, pred: (Int, String) -> Boolean) = states.firstOrNull { (_, idx, ph) -> idx > i || (idx == i && pred(idx, ph)) }?.first
        val revState = HashMap<Int, Long>(); val rank = HashMap<Int, Long>()
        for (i in 0..9) {
            gw.phaseIngress["REVEAL|$i"]?.let { ing -> caughtUp(i) { _, ph -> ph != "QUESTION" }?.let { revState[i] = it - ing } }
            (gw.phaseIngress["BOARD|$i"] ?: gw.phaseIngress["FINISHED|$i"])?.let { ing -> caughtUp(i) { _, ph -> ph == "BOARD" || ph == "FINISHED" }?.let { rank[i] = (it - ing).coerceAtLeast(0) } }
        }
        val acks = taps.flatMap { it.ackLatency }
        // exclusion des acks : de la réception de la question de la coupure à celle de la question qui suit la suivante
        val from = taps.firstNotNullOfOrNull { t -> t.received.entries.firstOrNull { it.key == gw.events.filter { e -> e.type == "question" }.getOrNull(CUT_INDEX)?.key }?.value } ?: Long.MAX_VALUE
        val to = taps.firstNotNullOfOrNull { t -> t.received.entries.firstOrNull { it.key == gw.events.filter { e -> e.type == "question" }.getOrNull(CUT_INDEX + 2)?.key }?.value } ?: Long.MAX_VALUE
        val excludedAcks = taps.flatMap { t -> t.sentRelay.entries.filter { it.value in from..to }.map { it.key } }.toSet()
        return Measure(name, q.withIndex().filter { it.value != null }.associate { it.index to it.value!! }, r.withIndex().filter { it.value != null }.associate { it.index to it.value!! }, revState, rank,
            acks, excludedAcks, gw, System.currentTimeMillis() - startedAt, tv)
    }

    /** 1 TV hôte derrière le simulateur : [relayed] téléphones relayés + [bots] joueurs distants (liaison directe). Fonctionne avec le service d'aujourd'hui. */
    private fun playSingleHost(coalesce: Boolean, tolerant: Boolean = false, relayed: Int = 8, bots: Int = 0, label: String = "x", controlFirst: Boolean = false): Pair<Measure, List<SimPhone>> {
        assertTrue(KeepAlive.effective)
        val srv = server(); val gw = edge(srv, coalesce, tolerant, controlFirst)
        val a = SimTv("TV A", srv.port, bank, gw.port, autoSkip = true, ticketOnEveryPost = !tolerant).also { tvs += it }
        a.session.start("dev-tv-a-000101", TestRights.PROD, PlayTvSession.Intent.Create(null, "DUEL"))
        waitFor("TV A assise", 60_000) { a.session.seated }
        val code = a.session.authority.code!!
        val phones = (0 until relayed).map { i -> a.addPhone("Joueur${i + 1}", 200L + 150 * i) { idx -> (idx + i) % 3 != 0 } }
        // joueurs DISTANTS sur liaison directe : ce que serait la TV B vue de la TV A (la TV A reçoit les états de relayed + bots joueurs, n'envoie que ses relayed réponses)
        val remote = (0 until bots).map { i ->
            Bot("Distant${i + 1}", WsWire(srv.port), bank, correct = { idx -> (idx + i) % 2 == 0 }, delayMs = 300L + 150 * i).also { it.send(ClientMsg.Join(code, "Distant${i + 1}", null, dev(), false)) }
        }
        val started = System.currentTimeMillis()
        phones.forEach { it.join(code); assertEquals(200, it.joinStatus, "${it.name} sans siège") }
        assertEquals(relayed, a.relay.phoneCount())
        waitFor("joueurs distants assis", 20_000) { remote.all { it.token != null } }
        a.session.authority.act(null, "start", null, null, "5")
        waitFor("question ${CUT_INDEX + 1} ouverte", 300_000) { phones[0].lastView?.let { v -> ((v["duel"] as? Map<*, *>)?.get("index") as? Number)?.toInt() == CUT_INDEX } == true }
        gw.cut(5_000)
        waitFor("fin de la partie pour la TV et ses téléphones", 900_000) { a.finalRanking != null && phones.all { it.finalRanking != null } }
        remote.forEach { it.stop() }
        assertTrue(gw.connections.get() <= 40, "connexions HTTP gardées vivantes : ${gw.connections.get()} connexions SOCKS pour toute la partie (une par POST = keep-alive inopérant)")
        val m = measure("TV A (hôte : $relayed relayés + $bots distants)", a, gw, started)
        File("build").mkdirs(); File("build/edge-fluidity-$label.txt").writeText(m.render(setOf(CUT_INDEX, CUT_INDEX + 1)))
        return m to phones
    }

    private fun checkGame(m: Measure, phones: List<SimPhone>, players: Int = 8) {
        val excluded = setOf(CUT_INDEX, CUT_INDEX + 1)
        println(m.render(excluded))
        assertEquals(setOf(players), phones.map { it.finalRanking!!.size }.toSet(), "la partie finit pour tous : $players joueurs au classement")
        assertTrue(phones.all { it.finalRanking == m.tv.finalRanking }, "même classement partout")
        assertTrue(m.question.size >= 9 && m.reveal.size >= 9, "toutes les annonces et révélations sont arrivées : ${m.question.size}/${m.reveal.size}")
        assertTrue(m.questionViolations(excluded).isEmpty(), "question affichée > 1,5 s : ${m.questionViolations(excluded)}")
        assertTrue(m.revealViolations(excluded).isEmpty(), "révélation > 2 s : ${m.revealViolations(excluded)}")
        assertTrue(m.rankingViolations(excluded).isEmpty(), "classement > 2 s : ${m.rankingViolations(excluded)}")
    }

    /** Le service d'AUJOURD'HUI (ticket exigé sur chaque POST d'un client sans `Origin`) + coalescence : question, révélation et classement tiennent ; l'ack, non (voir le rapport). */
    @Test fun withCoalescenceQuestionRevealAndRankingAreFluidAt40kbps() {
        val (m, phones) = playSingleHost(coalesce = true, relayed = 4, bots = 4, label = "coalescence-4relayes-4distants")
        checkGame(m, phones, players = 8)
        assertTrue(m.acksKept.size >= 25 && m.acksKept.maxOf { it.second } < 4_500, "acks hors coupure : ${m.acksKept.size}, tous sous 4,5 s, max ${m.acksKept.maxOfOrNull { it.second }} ms (le critère strict de 1,5 s n'est PAS tenu : voir le rapport)")
    }

    /** Hypothèse de service (non fusionnée) : ticket jugé à la seule création de session + commandes devant les `state` en file : q/révélation/classement tiennent ; l'ack ne tient pas non plus à 1,5 s (rafale de 4 réponses sur 5 Ko/s, RTT 0,8 s). */
    @Test fun withCoalescenceTicketOnceAndControlFirstQuestionRevealAndRankingHold() {
        val (m, phones) = playSingleHost(coalesce = true, tolerant = true, relayed = 4, bots = 4, label = "coalescence-ticket-une-fois-4relayes-4distants", controlFirst = true)
        checkGame(m, phones, players = 8)
        assertTrue(m.acksKept.size >= 25 && m.acksKept.maxOf { it.second } < 4_500, "acks hors coupure sous 4,5 s : max ${m.acksKept.maxOfOrNull { it.second }} ms")
    }

    @Test fun withoutCoalescenceTheRevealAndRankingCriterionFails() {
        val (m, _) = playSingleHost(coalesce = false, relayed = 4, bots = 4, label = "sans-coalescence-4relayes-4distants")
        val excluded = setOf(CUT_INDEX, CUT_INDEX + 1)
        println(m.render(excluded))
        val violations = m.revealViolations(excluded).size + m.rankingViolations(excluded).size
        assertTrue(violations > 0, "sans coalescence, la révélation ou le classement dépasse 2 s (mutation : le critère DOIT échouer) ; mesuré :\n${m.render(excluded)}")
    }

    @Test fun twoTvsEachWithFourRelayedPlayersBothBehindTheSimulator() {
        val srv = server(webPlay = false); val gwA = edge(srv, true); val gwB = edge(srv, true)
        val a = SimTv("TV A", srv.port, bank, gwA.port, autoSkip = true).also { tvs += it }; val b = SimTv("TV B", srv.port, bank, gwB.port).also { tvs += it }
        a.session.start("dev-tv-a-000201", TestRights.PROD, PlayTvSession.Intent.Create(null, "DUEL"))
        waitFor("TV A assise", 60_000) { a.session.seated }
        val code = a.session.authority.code!!
        b.session.start("dev-tv-b-000201", TestRights.PROD, PlayTvSession.Intent.Join(code, "TV B"))
        waitFor("TV B assise", 60_000) { b.session.seated }
        val pa = (0 until 4).map { a.addPhone("A${it + 1}", 200L + 150 * it) }; val pb = (0 until 4).map { b.addPhone("B${it + 1}", 250L + 150 * it) }
        pb[0].join(code)
        assertEquals(200, pb[0].joinStatus, "une TV invitée reçoit le siège relais (w20-04b)")
        pa.forEach { it.join(code) }; pb.drop(1).forEach { it.join(code) }
        val started = System.currentTimeMillis()
        a.session.authority.act(null, "start", null, null, "5")
        waitFor("question ${CUT_INDEX + 1}", 300_000) { pb[0].lastView?.let { v -> ((v["duel"] as? Map<*, *>)?.get("index") as? Number)?.toInt() == CUT_INDEX } == true }
        gwA.cut(5_000); gwB.cut(5_000)
        waitFor("fin de la partie pour tous", 900_000) { listOf(a, b).all { it.finalRanking != null } && (pa + pb).all { it.finalRanking != null } }
        val excluded = setOf(CUT_INDEX, CUT_INDEX + 1)
        for ((n, tvGw) in listOf(a to gwA, b to gwB)) {
            val m = measure(n.name, n, tvGw, started); println(m.render(excluded))
            assertTrue(m.questionViolations(excluded).isEmpty(), "${n.name} question > 1,5 s : ${m.questionViolations(excluded)}")
            assertTrue(m.revealViolations(excluded).isEmpty() && m.rankingViolations(excluded).isEmpty(), "${n.name} révélation ou classement > 2 s")
            assertTrue(m.acksKept.isNotEmpty() && m.acksKept.maxOf { it.second } < 4_500, "${n.name} ack : max ${m.acksKept.maxOfOrNull { it.second }} ms (critère strict 1,5 s : ${m.ackViolations().size} dépassements)")
        }
    }
}
