package castbridge.core.quiz.online

import castbridge.core.quiz.*
import kotlin.test.*

/**
 * Le secret des questions : sur 1 000 parties à graine (Duel et Millionnaire, 50:50 compris), AUCUN message émis tant que la question n'est pas
 * close ne contient `answer` / `explanation` ni la bonne réponse sous une autre clé ; le 50:50 ne révèle que de MAUVAISES réponses ; aucun
 * message ne liste plus d'une question.
 */
class NoAnswerLeakTest {
    private val bank = EmbeddedQuestionSource().bank()
    private val maxSize = java.util.TreeMap<String, Int>()
    private val secretKeys = setOf("answer", "explanation", "solution", "right", "correctAnswer", "correct_answer")

    @Suppress("UNCHECKED_CAST")
    private fun walk(v: Any?, path: String, bad: MutableList<String>) {
        when (v) {
            is Map<*, *> -> v.forEach { (k, x) -> if (k.toString() in secretKeys && x != null) bad += "$path.$k=$x"; walk(x, "$path.$k", bad) }
            is List<*> -> v.forEachIndexed { i, x -> walk(x, "$path[$i]", bad) }
        }
    }

    /** Vérifie tous les messages produits tant que la question est OUVERTE ; renvoie le nombre de messages vérifiés. */
    private fun scan(outs: List<ServerRoom.Out>, secretText: String?, answer: Int, where: String): Int {
        var n = 0
        for (o in outs) {
            val json = PlayCodec.encode(o.msg)
            maxSize.merge(o.msg.type, json.toByteArray().size) { a, b -> maxOf(a, b) }
            val bad = ArrayList<String>()
            walk(castbridge.core.quiz.Json.parse(json), "$where:${o.msg.type}", bad)
            assertTrue(bad.isEmpty(), "fuite avant la clôture : $bad")
            assertTrue(o.msg !is ServerMsg.Reveal, "$where : un reveal avant la clôture")
            if (!secretText.isNullOrBlank() && secretText.length > 8) assertFalse(json.contains(secretText), "$where : l'explication est dans ${o.msg.type}")
            assertTrue(Regex("\"choices\"").findAll(json).count() <= 1, "$where : plus d'une question dans ${o.msg.type}")
            n++
        }
        return n
    }

    @Test fun thousandSeededDuelsAndMillionnaireGamesLeakNothingBeforeClosing() {
        var scanned = 0L; var games = 0; var fiftyChecked = 0
        for (seed in 0L until 1_000L) {
            // ---------------- Duel (INTERNET, avec son délai)
            val r = ServerRoom("r$seed", PlayScope.INTERNET, bank, java.util.Random(seed), createdAt = 0, settings = ServerRoom.Settings(duelCount = 4))
            r.handle("tv", ClientMsg.Create(null, "DUEL"), 0)
            r.handle("a", ClientMsg.Join(r.code, "Awa", null, null, false), 0); r.handle("b", ClientMsg.Join(r.code, "Bello", null, null, false), 0)
            r.handle("tv", ClientMsg.Act(null, "mode", null, "DUEL", 1), 0)
            var now = 0L
            var outs = r.handle("tv", ClientMsg.Act(null, "start", null, seed.toString(), 2), now)
            var guard = 0
            while (r.phase() == ServerRoom.State.PLAYING && guard++ < 400) {
                val d = r.table(0).room.duel!!
                val q = d.question
                if (d.phase == QuizDuel.Phase.QUESTION) {
                    scanned += scan(outs, q.explanation, q.answer, "duel#$seed.q${d.index}.start")
                    now = maxOf(now, r.table(0).opensAtServerMs) + 700
                    outs = r.handle("a", ClientMsg.Act(q.id, "answer", q.answer, null, 1), now)       // un seul a répondu : la question est encore ouverte
                    assertEquals(QuizDuel.Phase.QUESTION, r.table(0).duelPhase())
                    scanned += scan(outs, q.explanation, q.answer, "duel#$seed.q${d.index}.afterA")
                    outs = r.tick(now + 100); scanned += scan(outs, q.explanation, q.answer, "duel#$seed.q${d.index}.tick")
                    now += 600
                    outs = r.handle("b", ClientMsg.Act(q.id, "answer", (q.answer + 1) % 4, null, 1), now)   // clôture : les révélations sont permises à partir d'ici
                    assertTrue(outs.any { it.msg is ServerMsg.Reveal }, "duel#$seed : révélation après la clôture")
                } else { now += 500; outs = r.handle("tv", ClientMsg.Act(null, "skip", null, null, 3), now) }
            }
            // ---------------- Millionnaire : 50:50 puis sélection et verrouillage (avant la révélation)
            val m = ServerRoom("m$seed", PlayScope.INTERNET, bank, java.util.Random(seed), createdAt = 0)
            m.handle("tv", ClientMsg.Create(null, "MILLIONAIRE"), 0)
            m.handle("a", ClientMsg.Join(m.code, "Awa", null, null, false), 0)
            m.handle("tv", ClientMsg.Act(null, "mode", null, "MILLIONAIRE", 1), 0)
            var mo = m.handle("tv", ClientMsg.Act(null, "start", null, seed.toString(), 2), 0)
            val g = m.table(0).room.game!!
            val gq = g.question
            scanned += scan(mo, gq.explanation, gq.answer, "mill#$seed.start")
            mo = m.handle("tv", ClientMsg.Act(gq.id, "fifty", null, null, 3), 10)
            scanned += scan(mo, gq.explanation, gq.answer, "mill#$seed.fifty")
            if (g.removed.isNotEmpty()) { fiftyChecked++; assertEquals(2, g.removed.size); assertFalse(gq.answer in g.removed, "mill#$seed : le 50:50 ne retire jamais la bonne réponse") }
            @Suppress("UNCHECKED_CAST") val removedInView = (mo.map { it.msg }.filterIsInstance<ServerMsg.State>().last().view["game"] as Map<String, Any?>)["removed"] as List<*>
            assertFalse(removedInView.contains(gq.answer.toLong()) || removedInView.contains(gq.answer), "mill#$seed : la vue du 50:50 ne contient que de mauvaises réponses")
            val pick = (0..3).first { it != gq.answer && it !in g.removed }
            mo = m.handle("tv", ClientMsg.Act(gq.id, "select", pick, null, 4), 20); scanned += scan(mo, gq.explanation, gq.answer, "mill#$seed.select")
            mo = m.handle("tv", ClientMsg.Act(gq.id, "confirm", null, null, 5), 30); scanned += scan(mo, gq.explanation, gq.answer, "mill#$seed.confirm")
            games += 2
        }
        java.io.File("build").mkdirs()
        java.io.File("build/play-server-sizes.txt").writeText(maxSize.entries.joinToString("\n") { "server ${it.key} ${it.value}" } + "\n")
        assertEquals(2_000, games)
        assertTrue(scanned > 10_000, "assez de messages vérifiés : $scanned")
        assertTrue(fiftyChecked > 900, "le 50:50 a réellement été utilisé : $fiftyChecked")
    }
}
