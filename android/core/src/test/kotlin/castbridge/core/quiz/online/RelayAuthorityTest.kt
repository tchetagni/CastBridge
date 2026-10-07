package castbridge.core.quiz.online

import castbridge.core.quiz.EmbeddedQuestionSource
import castbridge.core.quiz.Json
import castbridge.core.quiz.QuizRoom
import kotlin.test.*

/** `RelayAuthority` : la TV relaie ses téléphones locaux vers le service avec le temps mesuré sur SON horloge monotone (DESIGN-W20-AMENDEMENT § 2.5). */
class RelayAuthorityTest {
    private var now = 10_000L
    private val t = ScriptedTransport()
    private var seat = 0
    private val session = PlayTvSession(clock = { now }, transports = { t }, ticket = { "cbp1.ticket-de-test" })

    init {
        t.reply = { m ->
            when (m) {
                is ClientMsg.Create -> t.push(ServerMsg.Welcome(1, "room-1", "K7M2QX4T", "tv-token", PlayRole.HOST, null, 1, emptyList()))
                is ClientMsg.Join -> if (m.token == null) { seat++; t.push(ServerMsg.Welcome(2, "room-1", "K7M2QX4T", "seat-$seat", PlayRole.PLAYER, "p$seat", 1, emptyList())) }
                else -> {}
            }
        }
        session.start("dev-tv-000001", "cbx1.activation", PlayTvSession.Intent.Create("TV A", "DUEL"))
    }

    private fun question(opensAt: Long = 20_000L, serverNow: Long = 18_500L, id: String = "q1", index: Int = 0) =
        ServerMsg.Question(3, id, index, 10, "Capitale du Cameroun ?", listOf("Douala", "Yaoundé", "Garoua", "Bafoussam"), opensAt, serverNow, 15_000L)

    @Suppress("UNCHECKED_CAST") private fun Map<String, Any?>.m(k: String) = this[k] as Map<String, Any?>

    @Test fun phoneAnswerBecomesRelayActWithLocalElapsed() {
        val relay = RelayAuthority(session, { now })
        val joined = relay.join(null, "Awa", null, "dev-phone-0001")
        assertEquals(QuizRoom.Join.OK, joined.status)
        t.push(question())                     // reçue à now = 10 000 ; ouverture annoncée dans 1 500 ms (RTT inconnu = 0)
        now = 12_300L                          // le téléphone répond 800 ms après l'ouverture locale (11 500)
        assertEquals(QuizRoom.Act.OK, relay.act(joined.player!!.token, "answer", "q1", 1, null))
        val sent = t.all<ClientMsg.RelayAct>().single()
        assertEquals("seat-1", sent.token, "le jeton du SIÈGE serveur, jamais le jeton local")
        assertEquals(800L, sent.localElapsedMono)
        assertEquals("q1", sent.questionId); assertEquals(1, sent.choice)
        assertNotEquals("seat-1", joined.player.token, "le téléphone ne connaît que son jeton local")
    }

    @Test fun answerBeforeLocalOpeningIsNotSentAndDoubleAnswerIsIdempotent() {
        val relay = RelayAuthority(session, { now })
        val tok = relay.join(null, "Awa", null, "dev-phone-0001").player!!.token
        t.push(question())
        now = 11_000L                          // avant l'ouverture locale (11 500)
        assertEquals(QuizRoom.Act.IGNORED, relay.act(tok, "answer", "q1", 1, null))
        assertTrue(t.all<ClientMsg.RelayAct>().isEmpty())
        now = 12_000L
        assertEquals(QuizRoom.Act.OK, relay.act(tok, "answer", "q1", 1, null))
        assertEquals(QuizRoom.Act.OK, relay.act(tok, "answer", "q1", 1, null), "même réponse : idempotent")
        assertEquals(QuizRoom.Act.FORBIDDEN, relay.act(tok, "answer", "q1", 2, null), "pas de seconde chance")
        assertEquals(1, t.all<ClientMsg.RelayAct>().size, "une seule réponse part au service")
        assertEquals(QuizRoom.Act.UNKNOWN_PLAYER, relay.act("inconnu", "answer", "q1", 1, null))
        assertEquals(QuizRoom.Act.BAD_REQUEST, relay.act(tok, "answer", "q1", 7, null))
    }

    @Test fun ninthPhoneIsRefusedRoomFull() {
        val relay = RelayAuthority(session, { now })
        repeat(8) { assertEquals(QuizRoom.Join.OK, relay.join(null, "J$it", null, "dev-phone-%04d".format(it)).status) }
        assertEquals(QuizRoom.Join.FULL, relay.join(null, "Neuvième", null, "dev-phone-0009").status, "salle complète")
        assertEquals(8, t.all<ClientMsg.Join>().count { it.token == null }, "le 9e ne part même pas au service")
        assertEquals(8, relay.phoneCount())
    }

    @Test fun aStakedTvSeatsAsManyPhonesAsItBlockedStakesAndSaysSoWhenFull() {
        // Quiz misé (games-G5) : la TV n'a bloqué sa mise que pour 2 joueurs ; le 3e téléphone ne part pas au service et la phrase dit pourquoi (pas « 8 joueurs »)
        val relay = RelayAuthority(session, { now }, maxPhones = 2)
        repeat(2) { assertEquals(QuizRoom.Join.OK, relay.join(null, "J$it", null, "dev-phone-%04d".format(it)).status) }
        val third = relay.join(null, "Troisième", null, "dev-phone-0003")
        assertEquals(QuizRoom.Join.FULL, third.status)
        assertEquals("La salle est complète : cette TV n'a bloqué sa mise que pour 2 joueurs.", third.note)
        assertEquals(2, t.all<ClientMsg.Join>().count { it.token == null }, "le 3e ne part même pas au service")
        // une TV qui n'a bloqué qu'un siège : le singulier ; une salle libre garde le message d'avant (aucune note)
        assertEquals("La salle est complète : cette TV n'a bloqué sa mise que pour 1 joueur.", RelayAuthority(session, { now }, maxPhones = 1).also { it.join(null, "Seul", null, "dev-phone-0010") }.join(null, "Autre", null, "dev-phone-0011").note)
        val free = RelayAuthority(session, { now })
        repeat(8) { free.join(null, "L$it", null, "dev-phone-%04d".format(20 + it)) }
        assertNull(free.join(null, "Neuvième", null, "dev-phone-0099").note, "salle libre : le message habituel, sans note")
    }

    @Test fun rejoinWithTheLocalTokenKeepsTheSeatWithoutAskingTheService() {
        val relay = RelayAuthority(session, { now })
        val a = relay.join(null, "Awa", null, "dev-phone-0001").player!!
        val again = relay.join(null, "Awa", a.token, "dev-phone-0001")
        assertEquals(QuizRoom.Join.OK, again.status); assertEquals(a.token, again.player!!.token); assertEquals(a.id, again.player.id)
        assertEquals(1, t.all<ClientMsg.Join>().count { it.token == null })
    }

    /** Une vraie `QuizRoom` en Duel fournit le `state` du siège de la TV (ce que le service enverrait). */
    private fun duelView(): Pair<QuizRoom, Map<String, Any?>> {
        val room = QuizRoom(EmbeddedQuestionSource().bank(), clock = { 1_000L }, random = java.util.Random(3), autoTick = false)
        room.join(room.code, "Awa"); room.join(room.code, "Bello")
        room.setMode(QuizRoom.Mode.DUEL); assertNull(room.startGame(seed = 5))
        return room to room.view(null)
    }

    @Test fun noViewContainsTheRightAnswerBeforeReveal() {
        val (room, view) = duelView()
        val relay = RelayAuthority(session, { now })
        val tok = relay.join(null, "Awa", null, "dev-phone-0001").player!!.token
        t.push(ServerMsg.State(5, view, false))
        val q = room.duel!!.question
        t.push(ServerMsg.Question(6, q.id, 0, room.duel!!.questions.size, q.question, q.choices, 20_000L, 18_500L, 15_000L))
        fun leaks(v: Map<String, Any?>) = Regex("\"answer\":\\d").containsMatchIn(Json.write(v)) || Json.write(v).contains("\"explanation\":\"")
        assertFalse(leaks(relay.view(tok)), Json.write(relay.view(tok)))
        assertFalse(leaks(relay.view(null)))
        // la question suivante annoncée AVANT l'état, juste après la révélation de la précédente : la bonne réponse de la précédente ne passe pas sur la nouvelle
        t.push(ServerMsg.Reveal(7, q.id, 0, q.answer, "parce que"))
        t.push(question(opensAt = 40_000L, serverNow = 38_500L, id = "q-suivante", index = 1))
        val v = relay.view(tok)
        assertEquals("q-suivante", v.m("duel").m("question")["id"])
        assertNull(v.m("duel").m("question")["answer"], "aucune bonne réponse avant `reveal` : ni celle de la question précédente")
        assertEquals("QUESTION", v.m("duel")["phase"])
        // la révélation de CETTE question, elle, passe
        t.push(ServerMsg.Reveal(8, "q-suivante", 1, 2, "voilà"))
        val r = relay.view(tok)
        assertEquals(2L, (r.m("duel").m("question")["answer"] as Number).toLong()); assertEquals("REVEAL", r.m("duel")["phase"])
    }

    @Test fun composedViewHasTheKeysThePhonePageReads() {
        val (room, view) = duelView()
        val relay = RelayAuthority(session, { now })
        val tok = relay.join(null, "Awa", null, "dev-phone-0001").player!!.token
        t.push(ServerMsg.State(5, view, false))
        val composed = relay.view(tok)
        val page = RelayAuthorityTest::class.java.getResourceAsStream("/castbridge/quiz/play.html")!!.use { String(it.readBytes(), Charsets.UTF_8) }
        // toutes les clés de premier niveau lues par la page locale (s.xxx / state.xxx) sont là, comme dans la vue de la QuizRoom d'aujourd'hui
        val read = Regex("\\b(?:s|state)\\.([a-zA-Z]+)").findAll(page).map { it.groupValues[1] }.toSet() - setOf("v") + "v"
        val roomKeys = room.view("x").keys
        for (k in read) if (k in roomKeys) assertTrue(k in composed.keys, "clé « $k » lue par play.html et absente de la vue composée")
        for (k in roomKeys) assertTrue(k in composed.keys, "clé « $k » de QuizRoom.view absente de la vue composée")
        assertTrue("safety" in composed.keys)
        // et l'intérieur du Duel que la page lit
        val d = composed.m("duel")
        for (k in listOf("phase", "index", "count", "question", "remainingMs", "answeredCount", "ranking", "myAnswer", "outcome")) assertTrue(k in d.keys, "duel.$k")
        assertEquals("player", composed.m("me")["role"]); assertEquals("Awa", composed.m("me")["name"]); assertEquals("p1", composed.m("me")["id"])
    }

    @Test fun relayAckIsRecordedAndOutcomeIsComposedAtReveal() {
        val (room, view) = duelView()
        val relay = RelayAuthority(session, { now })
        val tok = relay.join(null, "Awa", null, "dev-phone-0001").player!!.token
        t.push(ServerMsg.State(5, view, false))
        val q = room.duel!!.question
        t.push(ServerMsg.Question(6, q.id, 0, 10, q.question, q.choices, 20_000L, 18_500L, 15_000L))
        now = 12_000L
        assertEquals(QuizRoom.Act.OK, relay.act(tok, "answer", q.id, 2, null))
        assertEquals(2L, (relay.view(tok).m("duel")["myAnswer"] as Number).toLong())
        val ref = t.all<ClientMsg.RelayAct>().single().seq
        val before = session.authority.changeCount
        t.push(ServerMsg.Ack(7, ref, "OK"))
        assertTrue(session.authority.changeCount > before, "l'accusé réveille les flux des téléphones")
        // le service révèle : classement avec le gain de ce joueur
        val ranking = listOf(linkedMapOf("id" to "p1", "name" to "Awa", "score" to 800, "rank" to 1, "gained" to 800, "correct" to true))
        val closed = LinkedHashMap(view).also { v -> v["duel"] = LinkedHashMap(view.m("duel")).also { d -> d["phase"] = "REVEAL"; d["ranking"] = ranking
            d["question"] = LinkedHashMap(view.m("duel").m("question")).also { it["answer"] = 2 } } }
        t.push(ServerMsg.State(8, closed, false))
        val out = relay.view(tok).m("duel").m("outcome")
        assertEquals(true, out["correct"]); assertEquals(800L, (out["points"] as Number).toLong()); assertEquals(500L, (out["ms"] as Number).toLong())
    }

    @Test fun serverSeatTokenNeverLeavesTheTv() {
        val (_, view) = duelView()
        val relay = RelayAuthority(session, { now })
        val tok = relay.join(null, "Awa", null, "dev-phone-0001").player!!.token
        t.push(ServerMsg.State(5, view, false))
        val all = Json.write(relay.view(tok)) + Json.write(relay.view(null))
        assertFalse(all.contains("seat-1") || all.contains("tv-token"), "ni jeton de siège ni jeton de la TV dans une vue")
        assertFalse(relay.toString().contains("seat-1"))
    }
}
