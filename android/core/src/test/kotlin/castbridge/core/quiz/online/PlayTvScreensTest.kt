package castbridge.core.quiz.online

import castbridge.core.connect.Routes
import castbridge.core.owner.TvAccess
import castbridge.core.ux.SignalLevel
import kotlin.test.*

/**
 * w20-05 (POC) : les règles PURES des écrans « Partie Internet » de CastBridge-TV (le dessin Android n'est vérifié que par compilation).
 * Rien n'est visible, et aucune connexion ne part, tant que le drapeau `quiz.online` est éteint.
 */
class PlayTvScreensTest {
    private val green = SafetySign.of(SafetyFacts(PlayScope.INTERNET))

    // ---------------------------------------------------------------- drapeau
    @Test fun flagPriorityIsSettingThenSignedOrderThenCompiledDefault() {
        assertEquals("quiz.online", QuizOnlineFlag.NAME)
        assertTrue(QuizOnlineFlag.COMPILED_DEFAULT, "décision du propriétaire 2026-10-04 : allumé par défaut, dans tous les builds")
        assertTrue(QuizOnlineFlag.enabled(null, null, QuizOnlineFlag.COMPILED_DEFAULT))
        assertTrue(QuizOnlineFlag.enabled(true, false, false)); assertFalse(QuizOnlineFlag.enabled(false, true, true))
        assertTrue(QuizOnlineFlag.enabled(null, true, false)); assertFalse(QuizOnlineFlag.enabled(null, false, true))
        assertTrue(QuizOnlineFlag.enabled(null, null, true)); assertFalse(QuizOnlineFlag.enabled(null, null, false))
    }


    // ---------------------------------------------------------------- porte : drapeau + activation + Internet + profil
    @Test fun flagOffMeansNothingVisibleAndNoNetworkWhateverTheRest() {
        for (e in HostEdition.values()) for (net in listOf(true, false)) for (kid in listOf(true, false)) for (clock in listOf(true, false)) {
            val t = PlayGate.tile(false, e, net, kid, clock)
            assertEquals(PlayTile.Hidden, t, "drapeau éteint ⇒ invisible ($e $net $kid $clock)")
            assertFalse(PlayGate.mayOpenNetwork(t))
        }
    }

    @Test fun activatedAdultWithInternetSeesTheTile() {
        for (e in listOf(HostEdition.PROD, HostEdition.TRIAL, HostEdition.GRACE)) {
            val t = PlayGate.tile(true, e, true, false)
            assertEquals(PlayTile.Available, t, "$e")
            assertTrue(PlayGate.mayOpenNetwork(t))
        }
    }

    @Test fun eachMissingConditionGivesItsReasonAndNoNetwork() {
        assertEquals(PlayTile.Blocked(PlayRules.MSG_ACTIVATE), PlayGate.tile(true, HostEdition.NONE, true, false))
        assertEquals(PlayTile.Blocked(PlayRules.MSG_CHILD), PlayGate.tile(true, HostEdition.PROD, true, true))
        assertEquals(PlayTile.Blocked("Connexion Internet requise"), PlayGate.tile(true, HostEdition.PROD, false, false))
        assertEquals(PlayGate.MSG_NO_INTERNET, "Connexion Internet requise")
        assertEquals(PlayTile.Blocked("Quiz en ligne : service indisponible"), PlayGate.tile(true, HostEdition.PROD, true, false, serviceUp = false))
        assertEquals(PlayGate.MSG_SERVICE_DOWN, "Quiz en ligne : service indisponible")
        assertEquals(PlayTile.Blocked(TvAccess.CHECK_CLOCK_LABEL), PlayGate.tile(true, HostEdition.PROD, true, false, clockDoubt = true))
        listOf(PlayTile.Blocked("x"), PlayTile.Hidden).forEach { assertFalse(PlayGate.mayOpenNetwork(it)) }
    }

    @Test fun anUnprobedServiceIsTriedAndAnAnsweringOneIsAvailableAndTheTileIsNeverBlank() {
        assertEquals(PlayTile.Available, PlayGate.tile(true, HostEdition.TRIAL, true, false, serviceUp = null))
        assertEquals(PlayTile.Available, PlayGate.tile(true, HostEdition.PROD, true, false, serviceUp = true))
        for (e in HostEdition.values()) for (net in listOf(true, false)) for (kid in listOf(true, false)) for (up in listOf(null, true, false)) {
            val t = PlayGate.tile(true, e, net, kid, false, up)
            assertTrue(t is PlayTile.Available || (t is PlayTile.Blocked && t.reason.isNotBlank()), "jamais un écran vide : $e $net $kid $up")
        }
    }

    @Test fun reasonsComeInAFixedOrderActivationClockChildInternet() {
        assertEquals(PlayTile.Blocked(PlayGate.MSG_NO_INTERNET), PlayGate.tile(true, HostEdition.PROD, false, false, serviceUp = false), "sans Internet, on ne dit pas « service indisponible »")
        assertEquals(PlayTile.Blocked(PlayRules.MSG_ACTIVATE), PlayGate.tile(true, HostEdition.NONE, false, true, true))
        assertEquals(PlayTile.Blocked(TvAccess.CHECK_CLOCK_LABEL), PlayGate.tile(true, HostEdition.PROD, false, true, true))
        assertEquals(PlayTile.Blocked(PlayRules.MSG_CHILD), PlayGate.tile(true, HostEdition.PROD, false, true))
    }

    @Test fun editionIsDerivedFromTheLockTrialAndGrace() {
        assertEquals(HostEdition.NONE, PlayGate.editionOf(locked = true, trial = true, grace = true))
        assertEquals(HostEdition.TRIAL, PlayGate.editionOf(false, true, false))
        assertEquals(HostEdition.GRACE, PlayGate.editionOf(false, false, true))
        assertEquals(HostEdition.PROD, PlayGate.editionOf(false, false, false))
    }

    // ---------------------------------------------------------------- saisie du code de salle
    @Test fun entryNormalisesAndIgnoresWhatIsNotACodeSymbol() {
        var e = RoomCodeEntry()
        assertEquals("____-____", e.display())
        for (c in "k7m2") e = e.append(c)
        assertEquals("K7M2-____", e.display())
        e = e.append('U').append('-').append(' ').append('!')   // ni U, ni tiret, ni espace, ni signe : ignorés
        assertEquals("K7M2-____", e.display())
        e = e.append('Q'); assertEquals("K7M2-Q___", e.display())
        assertEquals("1", RoomCodeEntry().append('I').chars); assertEquals("1", RoomCodeEntry().append('l').chars); assertEquals("0", RoomCodeEntry().append('O').chars)
    }

    @Test fun entryStopsAtEightAndSubmitsOnlyWhenComplete() {
        var e = RoomCodeEntry()
        for (c in "K7M2QX4") e = e.append(c)
        assertFalse(e.complete); assertNull(e.code())
        e = e.append('T')
        assertTrue(e.complete); assertEquals("K7M2QX4T", e.code()); assertEquals("K7M2-QX4T", e.display())
        assertEquals(e, e.append('Z'), "au-delà de huit : ignoré")
    }

    @Test fun backspaceRemovesTheLastSymbolAndNeverFailsOnEmpty() {
        assertEquals(RoomCodeEntry(), RoomCodeEntry().backspace())
        assertEquals("K7", RoomCodeEntry("K7M").backspace().chars)
    }

    @Test fun keypadIsExactlyTheThirtyTwoSymbolsInFourRowsOfEight() {
        val k = RoomCodeEntry.keypad()
        assertEquals(4, k.size); assertTrue(k.all { it.size == 8 })
        assertEquals(RoomCode.ALPHABET, k.flatten().joinToString(""))
        assertTrue(k.flatten().none { it in "ILOU" })
    }

    // ---------------------------------------------------------------- automate des écrans
    private fun s(screen: PlayScreen, message: String? = null, intent: PlayIntent? = null) = PlayFlowState(screen, message, intent)

    @Test fun startFollowsTheGate() {
        assertEquals(PlayScreen.CLOSED, PlayFlow.start(PlayTile.Hidden).screen)
        assertEquals(s(PlayScreen.BLOCKED, "raison"), PlayFlow.start(PlayTile.Blocked("raison")))
        assertEquals(PlayScreen.MENU, PlayFlow.start(PlayTile.Available).screen)
    }

    @Test fun menuToCreateAndJoin() {
        val m = s(PlayScreen.MENU)
        assertEquals(s(PlayScreen.OPENING, intent = PlayIntent.Create), PlayFlow.next(m, PlayEvent.ChooseCreate))
        assertEquals(PlayScreen.ENTER_CODE, PlayFlow.next(m, PlayEvent.ChooseJoin).screen)
        assertEquals(PlayScreen.CLOSED, PlayFlow.next(m, PlayEvent.Back).screen)
    }

    @Test fun codeEntrySubmitsANormalisedCodeOrExplains() {
        val e = s(PlayScreen.ENTER_CODE)
        assertEquals(s(PlayScreen.OPENING, intent = PlayIntent.Join("K7M2QX4T")), PlayFlow.next(e, PlayEvent.CodeSubmitted("k7m2-qx4t")))
        for (bad in listOf("K7M2", null, "", "K7M2QX4U", "K7M2QX4TT")) {
            val n = PlayFlow.next(e, PlayEvent.CodeSubmitted(bad))
            assertEquals(PlayScreen.ENTER_CODE, n.screen, "$bad"); assertNull(n.intent)
            assertEquals("Le code a 8 symboles : XXXX-XXXX.", n.message)
        }
        assertEquals(PlayScreen.MENU, PlayFlow.next(e, PlayEvent.Back).screen)
    }

    @Test fun openingEndsInTheRoomOrInAFailureOrIsCancelled() {
        val o = s(PlayScreen.OPENING, intent = PlayIntent.Create)
        assertEquals(PlayScreen.ROOM, PlayFlow.next(o, PlayEvent.Seated).screen)
        assertEquals(s(PlayScreen.FAILED, "refusé"), PlayFlow.next(o, PlayEvent.Failed("refusé")))
        assertEquals(PlayScreen.MENU, PlayFlow.next(o, PlayEvent.Back).screen)
        assertEquals(LinkCause.LOST_TEXT, PlayFlow.next(o, PlayEvent.LinkLost).message)
    }

    @Test fun leavingTheRoomAsksFirstAndCancelIsTheDefault() {
        val r = s(PlayScreen.ROOM)
        val c = PlayFlow.next(r, PlayEvent.Back)
        assertEquals(PlayScreen.CONFIRM_LEAVE, c.screen)
        assertEquals(PlayScreen.ROOM, PlayFlow.next(c, PlayEvent.Back).screen, "Retour = Annuler")
        assertEquals(PlayScreen.MENU, PlayFlow.next(c, PlayEvent.ConfirmLeave).screen)
        assertEquals(PlayScreen.ROOM, PlayFlow.next(r, PlayEvent.ConfirmLeave).screen, "confirmer sans avoir demandé : sans effet")
    }

    @Test fun linkLossOnTheRoomGoesToLostThenBackToTheQuizMenu() {
        val l = PlayFlow.next(s(PlayScreen.ROOM), PlayEvent.LinkLost)
        assertEquals(s(PlayScreen.LOST, "Partie Internet perdue"), l)
        assertEquals(PlayScreen.CLOSED, PlayFlow.next(l, PlayEvent.Back).screen)
        assertEquals(PlayScreen.LOST, PlayFlow.next(PlayFlow.next(s(PlayScreen.CONFIRM_LEAVE), PlayEvent.LinkLost), PlayEvent.Seated).screen)
    }

    @Test fun aRoomClosedByTheServiceShowsItsReasonThenTheMenu() {
        val f = PlayFlow.next(s(PlayScreen.ROOM), PlayEvent.Failed(PlayReason.PLAY_ROOM_GONE.message))
        assertEquals(s(PlayScreen.FAILED, PlayReason.PLAY_ROOM_GONE.message), f)
        assertEquals(PlayScreen.MENU, PlayFlow.next(f, PlayEvent.Back).screen)
        assertEquals(PlayScreen.CLOSED, PlayFlow.next(s(PlayScreen.BLOCKED, "x"), PlayEvent.Back).screen)
    }

    @Test fun turningTheFlagOffClosesEveryScreenAndIllegalEventsChangeNothing() {
        for (sc in PlayScreen.values()) assertEquals(PlayScreen.CLOSED, PlayFlow.next(s(sc), PlayEvent.FlagOff).screen, "$sc")
        val menu = s(PlayScreen.MENU)
        assertEquals(menu, PlayFlow.next(menu, PlayEvent.Seated)); assertEquals(menu, PlayFlow.next(menu, PlayEvent.LinkLost))
        assertEquals(menu, PlayFlow.next(menu, PlayEvent.CodeSubmitted("K7M2QX4T")))
        val closed = s(PlayScreen.CLOSED)
        assertEquals(closed, PlayFlow.next(closed, PlayEvent.ChooseCreate))
    }

    // ---------------------------------------------------------------- messages de liaison
    @Test fun linkScreenMapsEveryCauseToContinueDegradedOrLeave() {
        assertEquals(LinkView(LinkAction.CONTINUE, null), PlayLinkScreen.of(TvLink.Online, Routes.Via.DIRECT, false))
        assertEquals(LinkView(LinkAction.CONTINUE, null), PlayLinkScreen.of(TvLink.Online, null, false))
        assertEquals(LinkView(LinkAction.DEGRADED, LinkCause.GATEWAY_TEXT), PlayLinkScreen.of(TvLink.Online, Routes.Via.GATEWAY, false))
        assertEquals(LinkView(LinkAction.DEGRADED, "Internet · liaison en reprise (3 s)"), PlayLinkScreen.of(TvLink.Resuming(3), Routes.Via.DIRECT, false))
        assertEquals(LinkAction.DEGRADED, PlayLinkScreen.of(TvLink.Resuming(59), null, false).action)
        assertEquals(LinkView(LinkAction.LEAVE, LinkCause.LOST_TEXT), PlayLinkScreen.of(TvLink.Lost, null, false))
        assertEquals(LinkView(LinkAction.LEAVE, LinkCause.TLS_TEXT), PlayLinkScreen.of(TvLink.Online, Routes.Via.DIRECT, true))
        assertEquals(LinkView(LinkAction.DEGRADED, LinkCause.NO_NETWORK_TEXT), PlayLinkScreen.of(TvLink.Online, null, false, tvHasNetwork = false))
    }

    @Test fun linkScreenAgreesWithTheBannerLevelForEveryCombination() {
        val links = listOf(TvLink.Online, TvLink.Resuming(5), TvLink.Lost)
        for (l in links) for (via in listOf(null, Routes.Via.DIRECT, Routes.Via.GATEWAY)) for (tls in listOf(true, false)) for (net in listOf(true, false)) {
            val v = PlayLinkScreen.of(l, via, tls, net)
            val lowered = LinkCause.lower(green, via, l, net, tls)
            when (v.action) {
                LinkAction.CONTINUE -> assertEquals(SignalLevel.GREEN, lowered.level, "$l $via $tls $net")
                LinkAction.DEGRADED -> { assertTrue(lowered.level == SignalLevel.ORANGE || lowered.level == SignalLevel.BLACK, "$l $via $tls $net"); assertEquals(lowered.text, v.message, "$l $via $tls $net") }
                LinkAction.LEAVE -> { assertTrue(tls || l == TvLink.Lost, "$l $via $tls $net"); assertTrue(v.message in setOf(LinkCause.TLS_TEXT, LinkCause.LOST_TEXT), "$l $via $tls $net") }
            }
            assertEquals(tls || l == TvLink.Lost, v.action == LinkAction.LEAVE, "perte ou certificat ⇒ on quitte, jamais autrement : $l $via $tls $net")
        }
    }

    // ---------------------------------------------------------------- bandeau
    @Test fun bannerShowsExactlyTheReceivedViewWithFormWordAndHere() {
        val m = PlayBannerModel.of(green, 3)
        assertEquals("◎ Internet", m.scopeLine); assertEquals(SignalLevel.GREEN, m.level); assertEquals("Partie sûre", m.word)
        assertEquals(green.text, m.text); assertEquals("Ici : 3 joueurs · 5 places libres", m.hereLine)
        assertTrue(m.description.contains("Internet") && m.description.contains("Partie sûre") && m.description.contains(green.text))
        assertNull(PlayBannerModel.of(green, null).hereLine)
    }

    @Test fun bannerNeverRecomputesALevelItPassesTheReceivedOneThrough() {
        val views = listOf(
            green,
            SafetySign.of(SafetyFacts(PlayScope.INTERNET, rttMs = 2_000)),
            SafetySign.of(SafetyFacts(PlayScope.INTERNET, tls = TlsState.INVALID)),
            SafetySign.of(SafetyFacts(PlayScope.INTERNET, tvHasInternet = false)),
            LinkCause.lower(green, Routes.Via.GATEWAY, TvLink.Online, true),
        )
        for (v in views) {
            val m = PlayBannerModel.of(v, 0)
            assertEquals(v.level, m.level); assertEquals(v.text, m.text); assertEquals(v.action, m.action); assertEquals(v.word, m.word)
            assertTrue(m.word.isNotBlank(), "toujours un mot, jamais la couleur seule")
        }
        assertEquals(SignalLevel.ORANGE, PlayBannerModel.of(views.last(), 1).level)
    }

    @Test fun bannerSignDependsOnTheScreen() {
        val session = LinkCause.lower(green, Routes.Via.GATEWAY, TvLink.Online, true)
        for (sc in listOf(PlayScreen.MENU, PlayScreen.ENTER_CODE)) {
            val v = PlayBanner.sign(sc, session)!!
            assertEquals(PlayScope.TV_ONLY, v.scope, "avant toute ouverture, rien ne sort : « TV seule »"); assertEquals("TV seule", v.scope.label)
        }
        for (sc in listOf(PlayScreen.OPENING, PlayScreen.ROOM, PlayScreen.CONFIRM_LEAVE, PlayScreen.LOST)) assertEquals(session, PlayBanner.sign(sc, session), "$sc")
        assertEquals(PlayScope.INTERNET, PlayBanner.sign(PlayScreen.OPENING, null)!!.scope)
        for (sc in listOf(PlayScreen.FAILED, PlayScreen.BLOCKED, PlayScreen.CLOSED)) assertNull(PlayBanner.sign(sc, session), "$sc")
    }

    // ---------------------------------------------------------------- sièges relayés
    @Test fun relaySeatsTextCountsAndNeverOverflows() {
        assertEquals("Ici : aucun joueur · 8 places libres", RelaySeatsText.here(0))
        assertEquals("Ici : 1 joueur · 7 places libres", RelaySeatsText.here(1))
        assertEquals("Ici : 7 joueurs · 1 place libre", RelaySeatsText.here(7))
        assertEquals("Ici : 8 joueurs · complet", RelaySeatsText.here(8))
        assertEquals("Ici : 8 joueurs · complet", RelaySeatsText.here(9), "jamais plus que le plafond")
        assertEquals("Ici : aucun joueur · 8 places libres", RelaySeatsText.here(-3))
        assertEquals("Ici : 2 joueurs · 2 places libres", RelaySeatsText.here(2, max = 4))
    }

    // ---------------------------------------------------------------- erreurs
    @Test fun everyKnownReasonShowsItsOwnFrenchMessage() {
        for (r in PlayReason.values()) {
            val t = PlayErrors.text(r.name)
            assertTrue(t.startsWith(r.message), "${r.name} : $t")
            if (!r.retryable || r.retryAfterMs == 0L) assertEquals(r.message, t, r.name)
        }
        assertTrue(PlayErrors.text(PlayReason.PLAY_TLS_INVALID.name).contains("certificat"))
        assertFalse(PlayErrors.text(PlayReason.PLAY_TLS_INVALID.name).contains("continuer"), "aucun contournement proposé")
    }

    @Test fun retryAfterIsSaidOnlyForRetryableReasons() {
        assertEquals(PlayReason.PLAY_BUSY.message + " Nouvel essai possible dans 15 s.", PlayErrors.text(PlayReason.PLAY_BUSY.name))
        assertEquals(PlayReason.PLAY_BUSY.message + " Nouvel essai possible dans 30 s.", PlayErrors.text(PlayReason.PLAY_BUSY.name, 30_000L))
        assertEquals(PlayReason.PLAY_ROOM_GONE.message, PlayErrors.text(PlayReason.PLAY_ROOM_GONE.name, 30_000L))
    }

    @Test fun unknownOrMissingReasonsNeverCrashAndNeverEchoHostileText() {
        val generic = PlayErrors.text(null)
        assertTrue(generic.isNotBlank() && generic.length <= 200)
        assertEquals(generic, PlayErrors.text("")); assertEquals(generic, PlayErrors.text("   "))
        val future = PlayErrors.text("PLAY_FUTURE_THING")
        assertTrue(future.contains("PLAY_FUTURE_THING") && future.startsWith("Le service de jeu a refusé la demande"), future)
        val hostile = PlayErrors.text("<script>alert(1)</script>\n\u0000" + "x".repeat(500))
        assertFalse(hostile.contains("<") || hostile.contains("script") || hostile.contains("\n"), hostile)
        assertTrue(hostile.length <= 200, "${hostile.length}")
        assertTrue(PlayErrors.text(PlayProtocol.UNSUPPORTED).contains("mettez"))
        assertTrue(PlayErrors.text(PlayProtocol.BAD_REQUEST).isNotBlank()); assertTrue(PlayErrors.text(PlayProtocol.FORBIDDEN).isNotBlank())
    }

    @Test fun ticketHttpStatusesGetDistinctFrenchTexts() {
        assertTrue(PlayErrors.ticketHttp(401).contains("pas reconnue"))
        assertEquals(PlayReason.PLAY_TICKET_REFUSED.message, PlayErrors.ticketHttp(403))
        assertTrue(PlayErrors.ticketHttp(429).contains("Trop de demandes"))
        assertTrue(PlayErrors.ticketHttp(503).contains("pas disponible"))
        val other = PlayErrors.ticketHttp(500)
        assertTrue(other.contains("500") && other.isNotBlank())
        assertEquals(5, listOf(401, 403, 429, 503, 500).map { PlayErrors.ticketHttp(it) }.toSet().size)
    }

    @Test fun ticketReplyParsingIsTolerantAndStrict() {
        val ok = PlayTicketReply.parse(200, """{"ticket":"cbp1.abc.def","expiresAt":1234567,"ttlSeconds":600,"future":{"x":1}}""")
        assertEquals(PlayTicketReply.Ok("cbp1.abc.def", 1234567L), ok)
        for (bad in listOf("""{"expiresAt":1}""", """{"ticket":""}""", "not json", "", null, """{"ticket":"a b"}""", """{"ticket":"${"x".repeat(1_300)}"}""", "[1]")) {
            val r = PlayTicketReply.parse(200, bad)
            assertTrue(r is PlayTicketReply.Refused && r.text.isNotBlank(), "$bad")
        }
        assertEquals(PlayTicketReply.Refused(PlayErrors.ticketHttp(403)), PlayTicketReply.parse(403, """{"ticket":"cbp1.a.b"}"""))
        assertEquals(PlayTicketReply.Refused(PlayErrors.ticketHttp(503)), PlayTicketReply.parse(503, null))
    }

    @Test fun noScreenTextEverMentionsAWebPageOrAQrCode() {
        val texts = listOf(PlayErrors.NO_INTERNET, PlayErrors.text(null), RelaySeatsText.here(3), LinkCause.GATEWAY_TEXT) + PlayReason.values().map { PlayErrors.text(it.name) }
        texts.forEach { assertFalse(it.contains("http") || it.contains("QR", ignoreCase = true), it) }
    }
}
