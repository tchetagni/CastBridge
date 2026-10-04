package castbridge.core.quiz.online

import kotlin.test.*

/** L'automate de la TV : ticket demandé une fois, règle 11, reprise par `resume`, courbe 0-2-4-8-15-30 s, perte à 60 s exactement (horloge injectée). */
class PlayTvSessionTest {
    private var now = 100_000L
    private val made = ArrayList<ScriptedTransport>()
    private var tickets = 0
    /** Les transports ouverts APRÈS le premier naissent fermés (service injoignable) sauf si [reopenState] dit autrement. */
    private var reopenState = PlayTransport.Status.CLOSED
    private val opens = ArrayList<Pair<Long, String?>>()

    private fun welcomeOnCreate(t: ScriptedTransport) { t.reply = { m -> if (m is ClientMsg.Create) t.push(ServerMsg.Welcome(1, "room-1", "K7M2QX4T", "tv-token", PlayRole.HOST, null, 1, emptyList())) } }
    private val session = PlayTvSession(clock = { now }, transports = { tk ->
        opens += now to tk
        ScriptedTransport().also { t -> if (made.isNotEmpty()) t.state = reopenState else welcomeOnCreate(t); made += t }
    }, ticket = { tickets++; "cbp1.ticket-$tickets" })

    private fun start(intent: PlayTvSession.Intent = PlayTvSession.Intent.Create("TV A", "DUEL")) = session.start("dev-tv-000001", "cbx1.act", intent)

    @Test fun ticketIsAskedOnceAndHelloThenCreateCarryTheProofs() {
        start()
        assertEquals(1, tickets)
        val hello = made[0].all<ClientMsg.Hello>().single()
        assertEquals("cbp1.ticket-1", hello.ticket); assertEquals("dev-tv-000001", hello.deviceHash)
        assertEquals("cbp1.ticket-1", opens.single().second, "le transport reçoit le ticket pour son premier POST")
        assertEquals("cbx1.act", made[0].all<ClientMsg.Create>().single().activation)
        assertTrue(session.seated); assertEquals(TvLink.Online, session.link)
    }

    @Test fun joinSendsTheCode() {
        start(PlayTvSession.Intent.Join("K7M2QX4T", "TV B"))
        val j = made[0].all<ClientMsg.Join>().single()
        assertEquals("K7M2QX4T", j.code); assertEquals("TV B", j.name); assertNull(j.token)
    }

    @Test fun rule11SubtractsHalfTheRttOnTheClient() {
        start()
        made[0].rtt(800)                                      // RTT de la TV : 800 ms
        made[0].push(ServerMsg.Question(2, "q1", 0, 10, "?", listOf("a", "b", "c", "d"), opensAtServerMs = 51_500, serverNowMs = 50_000, windowMs = 15_000))
        val qc = session.questionClock!!
        // annonce partie à 50 000 (serveur), vue ≈ 50 400 : il reste 1 100 ms avant l'ouverture, pas 1 500
        assertEquals(now + 1_100, qc.opensAtLocalMono); assertFalse(qc.startNow); assertEquals(1_100L, qc.waitMs)
        // annonce TARDIVE : l'ouverture est déjà passée sur l'horloge du serveur ⇒ la TV démarre aussitôt et la fenêtre est raccourcie
        made[0].push(ServerMsg.Question(3, "q2", 1, 10, "?", listOf("a", "b", "c", "d"), opensAtServerMs = 59_900, serverNowMs = 59_600, windowMs = 15_000))
        val late = session.questionClock!!
        assertTrue(late.startNow); assertEquals(now - 100, late.opensAtLocalMono); assertEquals(14_900L, late.remainingMs)
    }

    @Test fun lostLinkResumesOnTheCurveAndIsLostAtSixtySecondsExactly() {
        start()
        made[0].push(ServerMsg.State(7, mapOf("stage" to "PLAYING"), false))
        val t0 = now
        made[0].state = PlayTransport.Status.CLOSED          // session de service perdue (410)
        val attemptAt = ArrayList<Long>()
        var last = 0
        while (now - t0 <= 61_000 && !session.stopped) {
            session.tick()
            if (session.attempts > last) { attemptAt += now - t0; last = session.attempts }
            now += 500
        }
        assertEquals(listOf(0L, 2_000L, 6_000L, 14_000L, 29_000L, 59_000L), attemptAt, "courbe 0, 2, 4, 8, 15, 30 s entre deux tentatives")
        assertTrue(session.stopped)
        assertEquals(TvLink.Lost, session.link)
        assertEquals(1 + session.attempts, tickets, "un ticket frais par NOUVELLE session de service (le service en exige un valide à chaque POST sans Origin)")
        assertTrue(opens.drop(1).all { it.second != null })
    }

    @Test fun lostIsDeclaredAtSixtySecondsExactlyNotBefore() {
        start()
        val t0 = now
        made[0].state = PlayTransport.Status.CONNECTING      // le flux est coupé, la session de service peut revenir
        session.tick()
        now = t0 + 59_999; session.tick()
        assertEquals(TvLink.Resuming(59), session.link); assertFalse(session.stopped)
        now = t0 + 60_000; session.tick()
        assertEquals(TvLink.Lost, session.link); assertTrue(session.stopped)
    }

    @Test fun resumeCarriesLastSeqOnANewTransportAndTheLinkComesBack() {
        reopenState = PlayTransport.Status.OPEN
        start()
        made[0].push(ServerMsg.State(42, mapOf("stage" to "PLAYING"), false))
        made[0].state = PlayTransport.Status.CLOSED
        session.tick()                                       // tentative immédiate (courbe : 0 s)
        assertEquals(2, made.size); assertTrue(session.link is TvLink.Resuming)
        val resume = made[1].all<ClientMsg.Resume>().single()
        assertEquals(ClientMsg.Resume("room-1", "tv-token", 42L), resume)
        assertEquals("cbp1.ticket-2", made[1].all<ClientMsg.Hello>().single().ticket, "la reprise sur une nouvelle session porte un ticket frais")
        made[1].push(ServerMsg.Welcome(43, "room-1", "K7M2QX4T", "tv-token", PlayRole.HOST, null, 1, emptyList()))
        session.tick()
        assertEquals(TvLink.Online, session.link)
        assertEquals(0, session.attempts - 1, "une seule tentative a suffi")
    }

    @Test fun sameSessionStreamReopenAlsoAsksForTheMissedEvents() {
        start()
        made[0].push(ServerMsg.State(5, mapOf("stage" to "PLAYING"), false))
        made[0].state = PlayTransport.Status.CONNECTING      // flux coupé, même session
        now += 3_000; session.tick()
        assertTrue(session.link is TvLink.Resuming)
        made[0].state = PlayTransport.Status.OPEN            // flux rouvert
        session.tick()
        assertEquals(ClientMsg.Resume("room-1", "tv-token", 5L), made[0].all<ClientMsg.Resume>().single(), "resume sur la MÊME session : rattrape ce que la coupure a fait perdre")
        made[0].push(ServerMsg.Replay(6, emptyList()))
        session.tick(); assertEquals(TvLink.Online, session.link)
        assertEquals(1, made.size, "aucun nouveau transport")
    }

    @Test fun neverSeatedMeansANewCreationWithANewTicket() {
        made.clear()
        val s = PlayTvSession(clock = { now }, transports = { tk -> opens += now to tk; ScriptedTransport().also { made += it } }, ticket = { tickets++; "cbp1.ticket-$tickets" })
        s.start("dev-tv-000001", "cbx1.act", PlayTvSession.Intent.Create("TV A", "DUEL"))
        made[0].state = PlayTransport.Status.CLOSED          // la création n'a jamais été confirmée
        s.tick()
        assertEquals(2, tickets, "un ticket ne sert qu'une fois : nouvelle création = nouveau ticket")
        assertEquals(1, made[1].all<ClientMsg.Create>().size)
    }

    @Test fun certificateFailureIsFatalAndRedAndNeverRetried() {
        class Bad : PlayTransport, TransportHealth {
            override var state = PlayTransport.Status.CLOSED
            override val certificateInvalid = true
            override fun send(text: String) {}
            override fun onMessage(listener: (String) -> Unit) {}
        }
        val s = PlayTvSession(clock = { now }, transports = { Bad() }, ticket = { "cbp1.t" })
        s.start("dev-tv-000001", "cbx1.act", PlayTvSession.Intent.Create("TV A", "DUEL"))
        s.tick()
        assertTrue(s.stopped); assertEquals(0, s.attempts)
        val v = s.safety()
        assertEquals(castbridge.core.ux.SignalLevel.RED, v.level); assertTrue(v.text.contains("certificat non valide"))
    }

    @Test fun relayAcksFeedTheRttEstimateAndAreForwarded() {
        start()
        val acks = ArrayList<Pair<Long, String>>(); session.onRelayAck = { r, s -> acks += r to s }
        val ref = session.relayAnswer("seat-1", "q1", 2, 300)
        now += 700
        made[0].push(ServerMsg.Ack(9, ref, "OK"))
        assertEquals(listOf(ref to "OK"), acks)
        assertEquals(700L, session.rttMs(), "l'aller-retour d'un relayAct mesure le RTT de la TV")
    }
}
