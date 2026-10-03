package castbridge.play

import castbridge.core.quiz.Json
import castbridge.core.quiz.EmbeddedQuestionSource
import castbridge.core.quiz.online.ClientMsg
import castbridge.core.quiz.online.PlayTiming
import castbridge.core.quiz.online.ServerRoom
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** Un joueur prévu pour une partie : nom, transport, qualité (réussit les `10 − rang` premières questions), lenteur. */
class Seat(val name: String, val wire: Wire, val rank: Int, val delayMs: Long, val cheatAtIndex: Int = -1)

/** Une partie complète sur le service (Duel de 10 questions à graine) et les vérifications communes aux tests de boucle locale. */
class GameRun(val srv: PlayServer, val tvWire: Wire, val seats: List<Seat>, val seed: Long = 5) {
    val bank = EmbeddedQuestionSource().bank()
    lateinit var tv: Bot
    lateinit var players: List<Bot>

    fun expectedRanking() = seats.sortedBy { it.rank }.map { it.name }

    /** Joue la partie jusqu'au bout (limite 120 s). */
    fun play(): GameRun {
        tv = Bot(null, tvWire, bank, host = true)
        hostStart(tv, TestKeys.ticket())
        val welcome = tvWait { tv.roomCode }
        players = seats.map { s ->
            val b = Bot(s.name, s.wire, bank, correct = { i -> i < 10 - s.rank }, delayMs = s.delayMs, cheatAtIndex = s.cheatAtIndex)
            b.send(ClientMsg.Join(welcome, s.name, null, dev(), false))
            b
        }
        for (p in players) tvWait("${p.name} sans siège") { p.token }
        tv.send(ClientMsg.Act(null, "start", null, seed.toString(), 99))
        (players + tv).forEach { it.waitFinished(120_000) }
        return this
    }

    private fun <T : Any> tvWait(what: String = "salle non créée", f: () -> T?): T {
        val end = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < end) { f()?.let { return it }; Thread.sleep(20) }
        fail("$what (erreurs TV ${tv.errors}, transport ${tvWire.failure})")
    }

    fun stop() { (players + tv).forEach { it.stop() } }

    fun room(): ServerRoom = srv.rooms().single()

    /** Toutes les propriétés du cahier : classement, délai, fuites, équité. */
    fun verify() {
        // 1. même classement attendu pour tous, quel que soit le transport
        for (p in players + tv) assertEquals(expectedRanking(), p.finalRanking, "classement vu par ${p.name ?: "la TV"}")
        // 2. délai entre questions : 10 annonces, la première sans délai, les suivantes 1 à 2 s avant l'ouverture, sur l'horloge du serveur
        for (p in players) {
            assertEquals((0..9).toList(), p.seen.keys.sorted(), "${p.name} a vu les 10 questions")
            val first = p.seen.getValue(0)
            assertTrue(Math.abs(first.opensAtServerMs - first.serverNowMs) <= 300, "question 0 sans délai")
            for (i in 1..9) {
                val q = p.seen.getValue(i)
                val gap = q.opensAtServerMs - q.serverNowMs
                assertTrue(gap in PlayTiming.MIN_GAP_MS..PlayTiming.MAX_GAP_MS, "${p.name} question $i : délai $gap ms hors de 1000..2000")
            }
            // tous les clients reçoivent le MÊME opensAtServerMs (synchronisation)
            for (i in 0..9) assertEquals(players[0].seen.getValue(i).opensAtServerMs, p.seen.getValue(i).opensAtServerMs, "même ouverture, question $i")
        }
        // 3. aucune fuite de réponse avant la clôture
        for (p in players + tv) checkNoLeak(p)
        // 4. équité : jamais compté avant l'ouverture, jamais plus vite que la vérité ; borne rtt/2 − 400 ms (ici RTT ≈ 0 : aucune compensation)
        val log = room().table(0).answerLog()
        assertTrue(log.isNotEmpty())
        for (r in log) {
            val opens = players[0].seen.getValue(r.questionIndex).opensAtServerMs
            assertTrue(r.arrivedAtServerMs >= opens, "réponse comptée arrivée AVANT l'ouverture (question ${r.questionIndex})")
            val truth = r.arrivedAtServerMs - opens
            assertTrue(r.elapsedMs in 0..truth, "temps compté ${r.elapsedMs} > vérité $truth")
            assertTrue(r.elapsedMs >= truth - 400, "temps compté ${r.elapsedMs} trop court pour une vérité de $truth (borne rtt/2 plafonnée à 400 ms)")
        }
        assertEquals(seats.size * 10, log.size, "chaque joueur compté une fois par question")
        // 5. la triche (réponse avant l'ouverture) est refusée TOO_EARLY et jamais comptée
        for (s in seats.filter { it.cheatAtIndex >= 0 }) {
            val p = players.first { it.name == s.name }
            assertEquals("TOO_EARLY", p.ackOf(p.earlyAckSeq.get()), "réponse anticipée de ${s.name}")
            assertEquals(1, log.count { it.playerId == p.playerId && it.questionIndex == s.cheatAtIndex }, "une seule réponse comptée (celle après l'ouverture)")
        }
    }

    private fun checkNoLeak(p: Bot) {
        for (raw in p.raws) {
            val o = Json.parse(raw) as Map<*, *>
            when (o["t"]) {
                "question" -> assertTrue("answer" !in o && "explanation" !in o, "annonce de question avec réponse : ${raw.take(200)}")
                "state" -> {
                    val duel = (o["view"] as Map<*, *>)["duel"] as Map<*, *>?
                    if (duel != null && duel["phase"] == "QUESTION") {
                        val q = duel["question"] as Map<*, *>
                        assertEquals(null, q["answer"], "réponse dans l'état pendant la question (${p.name})")
                        assertEquals(null, q["explanation"], "explication dans l'état pendant la question")
                        assertEquals(null, duel["outcome"]); assertEquals(null, duel["distribution"])
                    }
                }
                "reveal" -> assertNotNull(o["answer"])
                else -> assertTrue("answer" !in o, "clé answer dans un message ${o["t"]}")
            }
        }
        // une révélation n'arrive jamais avant son annonce
        val order = p.raws.map { (Json.parse(it) as Map<*, *>) }.filter { it["t"] == "question" || it["t"] == "reveal" }.map { it["t"] to (it["questionId"] as String) }
        val announced = HashSet<String>()
        for ((t, id) in order) { if (t == "question") announced += id else assertTrue(id in announced, "révélation de $id avant son annonce") }
    }
}
