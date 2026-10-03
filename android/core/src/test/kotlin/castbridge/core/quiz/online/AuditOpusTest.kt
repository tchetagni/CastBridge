package castbridge.core.quiz.online

import castbridge.core.quiz.*
import castbridge.core.quiz.Json
import java.util.Random
import kotlin.test.*

/** Audit Opus de w20-07 : classement par siège, faux positifs de BotScore, pseudonymes (faux positifs et marques tonales), codes faux, journal. */
class AuditOpusTest {
    private val bank = EmbeddedQuestionSource().bank()
    private fun room(count: Int = 10) = ServerRoom("r1", PlayScope.INTERNET, bank, Random(7), createdAt = 0, settings = ServerRoom.Settings(duelCount = count))

    // ---- B1 / I6 : classement par siège, `ranked` seulement en fin de partie et pour le siège concerné ----

    private fun welcomeOf(os: List<ServerRoom.Out>) = os.map { it.msg }.filterIsInstance<ServerMsg.Welcome>().single()

    @Suppress("UNCHECKED_CAST")
    private fun roomView(r: ServerRoom, name: String, token: String, conn: String): Map<String, Any?> =
        r.handle(conn, ClientMsg.Join(r.code, name, token, null, false), 1_000_000).filter { it.to == conn }.map { it.msg }.filterIsInstance<ServerMsg.State>().last().view["room"] as Map<String, Any?>

    @Test fun oneFlaggedIntruderRemovesOnlyThatSeatFromTheRankingAndRankedIsOnlyShownAtTheEnd() {
        val r = room()
        r.handle("tv", ClientMsg.Create(null, "DUEL"), 0)
        val robot = welcomeOf(r.handle("c0", ClientMsg.Join(r.code, "Intrus", null, dv(), false), 0, "203.0.113.5"))
        val humans = (1..7).map { i -> welcomeOf(r.handle("c$i", ClientMsg.Join(r.code, "Joueur$i", null, dv(), false), 0, "203.0.113.${5 + i}")) }
        r.handle("tv", ClientMsg.Act(null, "mode", null, "DUEL", 1), 0)
        var now = 1_000L
        r.handle("tv", ClientMsg.Act(null, "start", null, "3", 2), now)
        val done = HashSet<String>()
        val rnd = Random(3)
        var midGameRankedKeySeen = false
        var guard = 0
        while (r.phase() != ServerRoom.State.FINISHED && guard++ < 60_000) {
            now += 50
            val outs = r.tick(now)
            if (r.phase() != ServerRoom.State.FINISHED) for (o in outs) (o.msg as? ServerMsg.State)?.let { if ((it.view["room"] as Map<*, *>).containsKey("ranked")) midGameRankedKeySeen = true }
            val q = r.currentQuestionId(); val d = r.table(0).room.duel
            if (q != null && d != null && r.table(0).duelPhase() == QuizDuel.Phase.QUESTION) {
                val opens = r.table(0).opensAtServerMs
                if (now >= opens + 100 && done.add("c0$q")) r.handle("c0", ClientMsg.Act(q, "answer", d.question.answer, null, 9), now)
                for (i in 1..7) if (now >= opens + 2_000 + (i * 331L + d.index * 97L) % 2_500 && done.add("c$i$q"))
                    r.handle("c$i", ClientMsg.Act(q, "answer", if (rnd.nextInt(10) < 7) d.question.answer else (d.question.answer + 1) % 4, null, 9), now)
            }
        }
        assertEquals(ServerRoom.State.FINISHED, r.phase())
        val flagged = r.botResults().filterValues { it.flagged }
        assertEquals(1, flagged.size, "seul l'intrus est signalé : ${r.botResults()}")
        assertFalse(midGameRankedKeySeen, "aucune vue ne porte `ranked` avant la fin de la partie (oracle pour régler un robot)")
        assertEquals(false, roomView(r, "Intrus", robot.token, "x0")["ranked"], "le siège signalé sort du classement")
        assertEquals(BotScore.NEUTRAL_TEXT, roomView(r, "Intrus", robot.token, "x0b")["rankNote"])
        humans.forEachIndexed { i, h -> assertEquals(true, roomView(r, "Joueur${i + 1}", h.token, "y$i")["ranked"], "les 7 autres restent classés (siège ${i + 1})") }
    }

    // ---- B2 : faux positifs de BotScore ----

    private fun trace(seat: String, ip: String? = null, device: String? = null, n: Int = 10, ms: (Int) -> Long, ok: (Int) -> Boolean = { true }, choice: (Int) -> Int = { 0 }) =
        BotScore.Trace(seat, ip, device, (0 until n).map { BotScore.Answer(it, ms(it), ok(it), choice = choice(it)) })

    @Test fun aFastHonestPupilOnEasyQuestionsIsNotFlagged() {
        val rnd = Random(5)
        // la question est affichée 1,5 s avant l'ouverture : l'élève touche à l'ouverture, 200 à 290 ms, toutes justes
        val s = BotScore.score(trace("e", ms = { 200L + rnd.nextInt(90) }))
        assertTrue(s.score < BotScore.THRESHOLD, "élève rapide : $s")
        // des temps ramenés à 0 par la compensation du RTT ne font pas une « régularité de robot »
        assertFalse("BOT_UNIFORM" in BotScore.score(trace("z", ms = { 0L }, ok = { it % 2 == 0 })).reasons)
    }

    @Test fun twoPupilsOfTheSameSchoolAreNotTwins() {
        val times = (0 until 10).map { 2_000L + it * 150 }
        val a = trace("a", ip = "203.0.113.5", device = "dA", ms = { times[it] }, choice = { it % 4 })
        val b = trace("b", ip = "203.0.113.5", device = "dB", ms = { times[it] + 20 }, choice = { it % 4 })
        assertTrue(BotScore.scoreAll(listOf(a, b)).values.none { "BOT_TWINS" in it.reasons }, "même adresse seule : jamais jumeaux")
        val c = trace("c", ip = "198.51.100.1", device = "dA", ms = { times[it] }, choice = { it % 4 })
        assertTrue(BotScore.scoreAll(listOf(a, c)).values.all { "BOT_TWINS" in it.reasons }, "même appareil : jumeaux")
    }

    // ---- I1 : faux positifs de la liste de mots ----

    private fun ok(raw: String) { assertTrue(Pseudonym.check(raw) is Pseudonym.Result.Ok, "« $raw » doit passer : ${Pseudonym.check(raw)}") }
    private fun no(raw: String) { assertTrue(Pseudonym.check(raw) is Pseudonym.Result.Refused, "« $raw » doit être refusé") }

    @Test fun countriesAndOrdinaryNamesPassButTheSlurDoesNot() {
        listOf("Niger", "Nigeria", "Nigérian", "Nigérien", "Nigeriane", "Sasha Wolf", "Fagot", "Dick", "Prof", "Pedo", "Anal").forEach { ok(it) }
        listOf("nigger", "N1gger", "niggers", "nigga", "faggot", "f4ggot", "ashawo", "Ashawo", "c.o.n", "f.u.c.k", "d i c k h e a d").forEach { no(it) }
    }

    // ---- I2 : marques tonales des langues camerounaises ; Zalgo ----

    @Test fun toneMarksOfCameroonianLanguagesPassZalgoDoesNot() {
        ok("Mbɔ̀ng"); ok("Ɛ́kwɛ́"); ok("ŋ̀gwɔ́"); ok("Aı́cha")
        no("Z̀́̂̃algo"); no("́abc"); no("a" + "́".repeat(4))
    }

    // ---- mineur : écritures mélangées, ı sans point, petites capitales, cyrillique ----

    @Test fun mixedScriptsAndLookAlikesAreRefused() {
        no("pеdo"); no("mеrde")                       // latin + cyrillique
        no("ıdiот"); no("вitch"); no("οAdmin")
        no("sᴀlope"); no("ᴍerde")
        no("вітсн")                    // « вітсн » entièrement cyrillique, lu « bitch » : forme normalisée = bitch
        ok("Иван")                          // « Иван » : une seule écriture, accepté
    }

    // ---- mineur : U+2028 / U+2029 ----

    @Test fun jsonWriterEscapesLineSeparators() {
        val s = Json.write(mapOf("a" to "x y z"))
        assertFalse(' ' in s || ' ' in s, s)
    }
}
