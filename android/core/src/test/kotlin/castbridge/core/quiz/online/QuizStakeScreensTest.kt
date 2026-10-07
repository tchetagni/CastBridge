package castbridge.core.quiz.online

import castbridge.core.chess.online.ChessOnlineGame
import castbridge.core.chess.online.ChessServiceCaps
import castbridge.core.chess.online.ChessStakeChoice
import castbridge.core.chess.online.ChessStakeFlow
import castbridge.core.chess.online.ChessWallet
import castbridge.core.chess.online.InMemoryChessStakeStore
import castbridge.core.chess.online.StakeAvailability
import castbridge.core.chess.online.StakeChoice
import castbridge.core.owner.Ed25519Signer
import castbridge.core.wallet.PlayResult
import castbridge.core.wallet.WalletCurrency
import castbridge.core.wallet.ui.EscrowDone
import castbridge.core.wallet.ui.GamePolicyView
import castbridge.core.wallet.ui.SettleDone
import castbridge.core.wallet.ui.SettleLine
import castbridge.core.wallet.ui.WalletMessages
import castbridge.core.wallet.ui.WalletResult
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Le côté TV du Quiz misé (games-G5), PUR : l'automate des écrans (mise à la création, confirmation, salle misée qu'on rejoint), les mises permises (compatibilité : service ou serveur sans `quizStakes`, essai),
 * le choix de mise partagé avec les échecs (un siège de plus au Quiz), les textes (« 20 NDEM », jamais « jetons » seul), et l'enchaînement d'argent ([QuizOnlineStake]) sur un portefeuille scripté.
 */
class QuizStakeScreensTest {
    private val NDEM20 = StakeSpec("NDEM", 20)
    private val MBOKO5 = StakeSpec("MBOKO", 5)
    private fun s(screen: PlayScreen, message: String? = null, intent: PlayIntent? = null, offer: Boolean = false, plan: PlayStakePlan? = null, joinStake: StakeSpec? = null) = PlayFlowState(screen, message, intent, offer, plan, joinStake)

    // ------------------------------------------------------------------ l'automate des écrans

    @Test fun withoutStakesTheMenuCreatesAFreeGameAtOnceAsBefore() {
        assertEquals(s(PlayScreen.OPENING, intent = PlayIntent.Create), PlayFlow.next(PlayFlow.start(PlayTile.Available), PlayEvent.ChooseCreate))
        assertEquals(s(PlayScreen.MENU), PlayFlow.start(PlayTile.Available))
        assertEquals(PlayScreen.MENU, PlayFlow.start(PlayTile.Available, stakeOffer = true).screen)
    }

    @Test fun withStakesCreateOpensTheStakeScreenWhereFreeIsAlwaysPossible() {
        val menu = PlayFlow.start(PlayTile.Available, stakeOffer = true)
        val choice = PlayFlow.next(menu, PlayEvent.ChooseCreate)
        assertEquals(s(PlayScreen.STAKE_CHOICE, offer = true), choice)
        // Libre : la partie s'ouvre tout de suite, sans écran de confirmation
        assertEquals(s(PlayScreen.OPENING, intent = PlayIntent.Create, offer = true), PlayFlow.next(choice, PlayEvent.StakeChosen(null)))
        // une mise : confirmation, puis ouverture avec le plan
        val plan = PlayStakePlan(NDEM20, 2)
        val confirm = PlayFlow.next(choice, PlayEvent.StakeChosen(plan))
        assertEquals(s(PlayScreen.STAKE_CONFIRM, offer = true, plan = plan), confirm)
        assertEquals(s(PlayScreen.OPENING, intent = PlayIntent.CreateStaked(plan), offer = true, plan = plan), PlayFlow.next(confirm, PlayEvent.StakeConfirmed))
        assertEquals(40L, plan.blocked)
        // Retour redescend d'un cran, jamais plus
        assertEquals(PlayScreen.STAKE_CHOICE, PlayFlow.next(confirm, PlayEvent.Back).screen)
        assertEquals(s(PlayScreen.MENU, offer = true), PlayFlow.next(choice, PlayEvent.Back))
    }

    @Test fun aPlanOutsideTheSeatBoundsIsIgnoredAndAConfirmWithoutAPlanDoesNothing() {
        val choice = s(PlayScreen.STAKE_CHOICE, offer = true)
        for (seats in listOf(0, 9, -1)) assertEquals(choice, PlayFlow.next(choice, PlayEvent.StakeChosen(PlayStakePlan(NDEM20, seats))), "$seats sièges")
        assertEquals(choice, PlayFlow.next(choice, PlayEvent.StakeChosen(PlayStakePlan(StakeSpec("NDEM", 0), 1))))
        assertEquals(PlayScreen.STAKE_CONFIRM, PlayFlow.next(choice, PlayEvent.StakeChosen(PlayStakePlan(NDEM20, 8))).screen)
        assertEquals(s(PlayScreen.STAKE_CONFIRM, offer = true), PlayFlow.next(s(PlayScreen.STAKE_CONFIRM, offer = true), PlayEvent.StakeConfirmed))
        // un évènement hors de son écran ne change rien
        assertEquals(choice, PlayFlow.next(choice, PlayEvent.StakeConfirmed))
        assertEquals(s(PlayScreen.MENU), PlayFlow.next(s(PlayScreen.MENU), PlayEvent.StakeConfirmed))
    }

    @Test fun aStakedRoomThatYouJoinAsksForYourStakeOnTheSameLinkThenOpens() {
        val joining = s(PlayScreen.OPENING, intent = PlayIntent.Join("K7M2QX4T"), offer = true)
        val ask = PlayFlow.next(joining, PlayEvent.NeedsStake(NDEM20))
        assertEquals(s(PlayScreen.JOIN_STAKE, intent = PlayIntent.Join("K7M2QX4T"), offer = true, joinStake = NDEM20), ask)
        val go = PlayFlow.next(ask, PlayEvent.JoinWithSeats(3))
        assertEquals(s(PlayScreen.OPENING, intent = PlayIntent.Join("K7M2QX4T"), offer = true, plan = PlayStakePlan(NDEM20, 3)), go)
        // après le blocage, un second refus de mise n'ouvre pas un second écran (une seule fois par ouverture)
        assertEquals(go, PlayFlow.next(go, PlayEvent.NeedsStake(NDEM20)))
        // une création ne demande jamais « votre mise » : le service ne répond pas ainsi à un create
        val creating = s(PlayScreen.OPENING, intent = PlayIntent.Create)
        assertEquals(creating, PlayFlow.next(creating, PlayEvent.NeedsStake(NDEM20)))
        for (seats in listOf(0, 9)) assertEquals(ask, PlayFlow.next(ask, PlayEvent.JoinWithSeats(seats)))
        assertEquals(s(PlayScreen.MENU, offer = true), PlayFlow.next(ask, PlayEvent.Back), "renoncer : retour au menu (la liaison laissée ouverte est refermée par l'écran)")
        assertEquals(PlayScreen.LOST, PlayFlow.next(ask, PlayEvent.LinkLost).screen)
        assertEquals(PlayScreen.FAILED, PlayFlow.next(ask, PlayEvent.Failed("x")).screen)
    }

    @Test fun theStakeOfferSurvivesEveryTripBackToTheMenuAndFlagOffClosesTheNewScreens() {
        val menu = PlayFlow.start(PlayTile.Available, stakeOffer = true)
        assertTrue(PlayFlow.next(PlayFlow.next(menu, PlayEvent.ChooseJoin), PlayEvent.Back).stakeOffer)
        val failed = PlayFlow.next(PlayFlow.next(PlayFlow.next(menu, PlayEvent.ChooseJoin), PlayEvent.CodeSubmitted("K7M2QX4T")), PlayEvent.Failed("refusé"))
        assertEquals(PlayScreen.FAILED, failed.screen)
        assertEquals(s(PlayScreen.MENU, offer = true), PlayFlow.next(failed, PlayEvent.Back))
        for (sc in PlayScreen.values()) assertEquals(PlayScreen.CLOSED, PlayFlow.next(s(sc, offer = true), PlayEvent.FlagOff).screen, "$sc")
    }

    @Test fun theSafetySignOfTheNewScreens() {
        val session = SafetySign.of(SafetyFacts(PlayScope.INTERNET))
        for (sc in listOf(PlayScreen.STAKE_CHOICE, PlayScreen.STAKE_CONFIRM)) assertEquals(SafetySign.of(SafetyFacts(PlayScope.TV_ONLY)), PlayBanner.sign(sc, session), "$sc : rien ne sort de la maison")
        assertEquals(session, PlayBanner.sign(PlayScreen.JOIN_STAKE, session), "la liaison au service est ouverte")
    }

    // ------------------------------------------------------------------ les mises permises

    private val policy = GamePolicyView(true, listOf(10, 20, 50), listOf(1, 2), 0, 3, 10, 15)
    private val allCaps = ChessServiceCaps(chess = true, stakes = true, quizStakes = true)

    private fun quiz(edition: HostEdition = HostEdition.PROD, caps: ChessServiceCaps? = allCaps, wallet: Boolean = true, n: Boolean = true, m: Boolean = true, frozen: Boolean = false, p: GamePolicyView? = policy, loaded: Boolean = true) =
        StakeAvailability.ofQuiz(edition, caps, wallet, n, m, frozen, p, loaded)

    @Test fun anOldServiceOrAnOldServerOffersOnlyFreeWithTheUpdateLine() {
        val update = StakeAvailability.FreeOnly(StakeAvailability.MSG_QUIZ_UPDATE)
        assertEquals("Mises NDEM/MBOKO : mettez à jour", StakeAvailability.MSG_QUIZ_UPDATE)
        assertEquals(update, quiz(caps = ChessServiceCaps(chess = true, stakes = true)), "service avec les mises des échecs mais sans `quizStakes`")
        assertEquals(update, quiz(caps = ChessServiceCaps(chess = false, stakes = false)), "service plus ancien encore")
        assertEquals(update, quiz(caps = null), "service qui n'a pas répondu à la sonde : on ne devine pas")
        assertEquals(update, quiz(p = null, loaded = true), "serveur dont la politique ne liste pas le jeu `quiz`")
        assertTrue(quiz(p = null, loaded = false) is StakeAvailability.Allowed, "politique pas encore lue : l'échelle de repli du propriétaire")
    }

    @Test fun trialNoActivationNoWalletFrozenOrSwitchedOffFallBackToFree() {
        assertEquals(StakeAvailability.FreeOnly(StakeAvailability.MSG_TRIAL), quiz(edition = HostEdition.TRIAL))
        assertEquals(StakeAvailability.FreeOnly(StakeAvailability.MSG_ACTIVATE), quiz(edition = HostEdition.NONE))
        assertEquals(StakeAvailability.FreeOnly(StakeAvailability.MSG_NO_WALLET), quiz(wallet = false))
        assertEquals(StakeAvailability.FreeOnly(StakeAvailability.MSG_FROZEN), quiz(frozen = true))
        assertEquals(StakeAvailability.FreeOnly(StakeAvailability.MSG_QUIZ_OFF), quiz(p = policy.copy(enabled = false)))
        assertEquals(StakeAvailability.FreeOnly(StakeAvailability.MSG_SERVICE_OFF), quiz(n = false, m = false))
        assertFalse(StakeAvailability.MSG_QUIZ_OFF.contains("échecs"), StakeAvailability.MSG_QUIZ_OFF)
    }

    @Test fun theScaleComesFromThePolicyOrTheOwnersDefault() {
        assertEquals(StakeAvailability.Allowed(listOf(10, 20, 50), listOf(1, 2)), quiz())
        assertEquals(StakeAvailability.Allowed(listOf(10, 20, 50, 100, 200), listOf(1, 2, 5, 10)), quiz(p = null, loaded = false), "NDEM 10/20/50/100/200, MBOKO 1/2/5/10 par défaut")
        assertEquals(StakeAvailability.Allowed(emptyList(), listOf(1, 2)), quiz(n = false), "NDEM coupé par l'instantané : seul MBOKO")
        assertEquals(StakeAvailability.Allowed(listOf(10, 20, 50), emptyList()), quiz(m = false))
    }

    @Test fun chessAvailabilityIsUnchangedByTheQuizCapability() {
        assertEquals(StakeAvailability.Allowed(listOf(10, 20, 50), listOf(1, 2)), StakeAvailability.of(HostEdition.PROD, ChessServiceCaps(true, true), true, true, true, false, policy))
        assertEquals(StakeAvailability.FreeOnly(StakeAvailability.MSG_SERVICE_OFF), StakeAvailability.of(HostEdition.PROD, ChessServiceCaps(true, false), true, true, true, false, policy))
        assertEquals(StakeAvailability.FreeOnly(StakeAvailability.MSG_GAME_OFF), StakeAvailability.of(HostEdition.PROD, ChessServiceCaps(true, true), true, true, true, false, policy.copy(enabled = false)))
    }

    @Test fun theCapsAreReadFromTheServiceAnswer() {
        val full = ChessServiceCaps.parse("""{"name":"play-v1","caps":["play1","chess","stakes","quizStakes"],"chess":true,"stakes":true,"quizStakes":true}""")!!
        assertTrue(full.quizStakes && full.stakes && full.chess)
        val onlyCap = ChessServiceCaps.parse("""{"name":"play-v1","caps":["play1","quizStakes"]}""")!!
        assertTrue(onlyCap.quizStakes, "la capacité seule suffit (même lecture que `stakes`)")
        val old = ChessServiceCaps.parse("""{"name":"play-v1","caps":["play1","chess","stakes"],"chess":true,"stakes":true}""")!!
        assertFalse(old.quizStakes, "un service d'avant games-G5 ne dit pas `quizStakes`")
        assertNull(ChessServiceCaps.parse("<html>"))
    }

    // ------------------------------------------------------------------ le choix de mise (partagé avec les échecs)

    private fun choice(maxSeats: Int = 8, ndem: Long? = 1_000, mboko: Long? = 12) =
        StakeChoice(StakeAvailability.Allowed(listOf(10, 20, 50), listOf(1, 2)), ndem, mboko, maxSeats = maxSeats, shared = true, abandonWarning = QuizStakeTexts.LEAVE_WARNING)

    @Test fun theQuizChoiceOffersFreeNdemMbokoThenTheAmountThenTheSeats() {
        val c = choice()
        assertEquals(listOf(ChessStakeChoice.Mode.FREE, ChessStakeChoice.Mode.NDEM, ChessStakeChoice.Mode.MBOKO), c.modes)
        assertEquals("Libre (sans mise)", c.modeText()); assertNull(c.stake()); assertNull(c.warning()); assertNull(c.blocked()); assertTrue(c.affordable())
        c.cycleMode(1)
        assertEquals(StakeSpec("NDEM", 10), c.stake()); assertEquals("10 NDEM par joueur", c.amountText())
        c.cycleAmount(1); assertEquals(StakeSpec("NDEM", 20), c.stake())
        assertTrue(c.multiSeat); assertEquals(1, c.seats); assertEquals("1 joueur de cette TV mise", c.seatsText())
        c.cycleSeats(1); c.cycleSeats(1)
        assertEquals(3, c.seats); assertEquals("3 joueurs de cette TV misent", c.seatsText()); assertEquals(60L, c.blocked())
        assertEquals("Mise bloquée : 60 NDEM · cagnotte partagée selon le classement", c.potLine())
        assertEquals(QuizStakeTexts.LEAVE_WARNING, c.warning())
        assertEquals("Votre solde : 1\u202f000 NDEM", c.balanceLine())
    }

    @Test fun seatsWrapBetweenOneAndTheBlockedMaximumAndTheBalanceCoversMiseTimesSeats() {
        val c = choice(maxSeats = 8, ndem = 100)
        c.cycleMode(1); c.cycleAmount(2)                       // 50 NDEM
        c.cycleSeats(-1); assertEquals(8, c.seats, "1 − 1 revient à 8")
        c.cycleSeats(1); assertEquals(1, c.seats)
        assertTrue(c.affordable()); c.cycleSeats(1); assertTrue(c.affordable(), "2 × 50 = 100 ≤ 100")
        c.cycleSeats(1); assertFalse(c.affordable(), "3 × 50 = 150 > 100 : le solde ne couvre pas, la TV le dit avant de bloquer")
        val one = choice(maxSeats = 1); one.cycleMode(1); one.cycleSeats(1); assertEquals(1, one.seats); assertFalse(one.multiSeat)
        // sans solde connu, l'API juge
        assertTrue(choice(ndem = null).also { it.cycleMode(1); it.cycleSeats(1) }.affordable())
    }

    @Test fun theChessChoiceKeepsItsOneSeatAndItsPotLine() {
        val c = ChessStakeChoice(StakeAvailability.Allowed(listOf(10, 20), listOf(1)), 100L, 5L)
        c.cycleMode(1); c.cycleAmount(1)
        c.cycleSeats(1); assertEquals(1, c.seats, "aux échecs : un siège")
        assertEquals("Cagnotte : 40 NDEM pour le gagnant", c.potLine())
        assertEquals(castbridge.core.chess.online.ChessOnlineTexts.ABANDON_WARNING, c.warning())
    }

    // ------------------------------------------------------------------ les textes

    @Test fun everyStakeIsWrittenWithItsCurrencyNeverAsBareTokens() {
        assertEquals("Mise : 20 NDEM par joueur", QuizStakeTexts.stakeLine(NDEM20))
        assertEquals("Mise : 5 MBOKO par joueur", QuizStakeTexts.stakeLine(MBOKO5))
        assertEquals("Mise : 1\u202f000 NDEM par joueur", QuizStakeTexts.stakeLine(StakeSpec("NDEM", 1_000)))
        assertEquals("Libre", QuizStakeTexts.FREE)
        val stake = mapOf("cur" to "NDEM", "per" to 20L, "pot" to 80L)
        assertEquals("Mise : 20 NDEM par joueur · cagnotte 80 NDEM", QuizStakeTexts.roomLine(stake))
        assertNull(QuizStakeTexts.roomLine(null)); assertNull(QuizStakeTexts.roomLine(emptyMap<String, Any?>()))
        assertEquals("Ici : 2 joueurs · 2 mises bloquées", QuizStakeTexts.hereLine(2, 2)); assertEquals("Ici : aucun joueur · 1 mise bloquée", QuizStakeTexts.hereLine(0, 1))
        assertEquals("20 NDEM par joueur · 2 joueurs ici · bloqué : 40 NDEM · votre solde 150 NDEM", QuizStakeTexts.confirmSubtitle(PlayStakePlan(NDEM20, 2), 150))
        assertEquals("5 MBOKO par joueur · 1 joueur ici · bloqué : 5 MBOKO", QuizStakeTexts.confirmSubtitle(PlayStakePlan(MBOKO5, 1), null))
        assertTrue(QuizStakeTexts.joinQuestion(NDEM20).startsWith("Cette partie se joue avec une mise de 20 NDEM par joueur"))
        for (t in listOf(QuizStakeTexts.stakeLine(NDEM20), QuizStakeTexts.LEAVE_WARNING, QuizStakeTexts.ONE_TV_ONLY, QuizStakeTexts.NO_WATCH, QuizStakeTexts.joinQuestion(MBOKO5), QuizStakeTexts.CREATE_TITLE, QuizStakeTexts.JOIN_TITLE))
            assertFalse(Regex("(?i)jetons? (virtuels?|sans)").containsMatchIn(t), t)
    }

    @Test fun theStakeIsReadFromTheServiceRefusalAndNothingElseIsTrusted() {
        assertEquals(NDEM20, QuizStakeTexts.specOf(mapOf("game" to "quiz", "cur" to "NDEM", "per" to 20L)))
        assertEquals(NDEM20, QuizStakeTexts.specOf(mapOf("cur" to "NDEM", "per" to 20)))
        assertNull(QuizStakeTexts.specOf(null)); assertNull(QuizStakeTexts.specOf(mapOf("cur" to "EUR", "per" to 20L))); assertNull(QuizStakeTexts.specOf(mapOf("cur" to "NDEM", "per" to 0L)))
        assertNull(QuizStakeTexts.specOf(mapOf("cur" to "NDEM"))); assertNull(QuizStakeTexts.specOf(mapOf("cur" to "NDEM", "per" to "vingt")))
    }

    // ------------------------------------------------------------------ l'enchaînement d'argent

    private class Wallet : ChessWallet {
        var lock: () -> WalletResult<EscrowDone> = { WalletResult.Ok(EscrowDone("cbe1.a.b", "EidAAAAAAAAAAAAAAAAAAA", 0L, 10_000_000L, false, null)) }
        var settle: (String) -> WalletResult<SettleDone> = { WalletResult.Ok(SettleDone("r".repeat(32), "END", "NDEM", "quiz", 0, listOf(SettleLine("EidAAAAAAAAAAAAAAAAAAA", "AAAA-AAAA-AAAA-AAAA", 40, 42, 0)))) }
        val locks = ArrayList<Triple<String, Int, String>>()
        var settles = 0
        override fun lockEscrow(cur: WalletCurrency, per: Long, game: String, idem: String, seats: Int): WalletResult<EscrowDone> { locks += Triple(game, seats, idem); return lock() }
        override fun settle(token: String): WalletResult<SettleDone> { settles++; return settle.invoke(token) }
    }

    private val wallet = Wallet()
    private val store = InMemoryChessStakeStore()
    private var n = 0
    private val changes = ArrayList<ChessOnlineGame.Settlement>()
    private val stake = QuizOnlineStake(ChessStakeFlow(wallet, store, { "key-${n++}" }, game = "quiz"), clock = { 1_000L }, async = { it() }).also { it.onChange = { changes += it.settlement } }
    private val signer = Ed25519Signer(MessageDigest.getInstance("SHA-256").digest("castbridge-quiz-stake-test".toByteArray()))
    private val EID = "EidAAAAAAAAAAAAAAAAAAA"
    private val ME = "AAAA-AAAA-AAAA-AAAA"
    private val EID_B = "EidBBBBBBBBBBBBBBBBBBB"
    private val OTHER = "BBBB-BBBB-BBBB-BBBB"
    /** Un résultat `cbr1` signé de la salle `r1` : [rid] = un caractère hexadécimal répété (un identifiant par test). */
    private fun result(kind: PlayResult.Kind, rid: Char, vararg lines: PlayResult.Line) =
        PlayResult.sign(PlayResult(signer.keyId, rid.toString().repeat(32), "r1", "quiz", WalletCurrency.NDEM, 20, kind, 5L, lines.toList()), signer)

    @Test fun theStakeIsBlockedForMiseTimesSeatsWithTheGameAndKeptForTheSamePlanOnly() {
        val plan = PlayStakePlan(NDEM20, 2)
        val first = stake.lock(plan) as ChessStakeFlow.Lock.Ok
        assertFalse(first.reused); assertEquals(Triple("quiz", 2, "key-0"), wallet.locks.single(), "le jeu et les deux sièges sont dits à l'API")
        assertEquals(2, first.escrow.seats); assertEquals("quiz", first.escrow.game)
        assertTrue((stake.lock(plan) as ChessStakeFlow.Lock.Ok).reused, "un blocage encore valable pour exactement la même mise sert encore")
        assertEquals(1, wallet.locks.size)
        val other = stake.lock(PlayStakePlan(NDEM20, 3)) as ChessStakeFlow.Lock.Ok
        assertFalse(other.reused, "trois sièges ≠ deux sièges : l'API a posé exactement mise × sièges")
        assertEquals(Triple("quiz", 3, "key-1"), wallet.locks.last())
        assertEquals(plan.copy(seats = 3), stake.plan)
    }

    @Test fun aRefusedLockKeepsNoPlanAndAnOfflineLockReplaysTheSameKey() {
        val plan = PlayStakePlan(NDEM20, 2)
        wallet.lock = { WalletResult.Fail(WalletMessages.offline(), null, null, true) }
        val f1 = stake.lock(plan) as ChessStakeFlow.Lock.Failed
        assertTrue(f1.network); assertNull(stake.plan)
        stake.lock(plan); assertEquals(listOf("key-0", "key-0"), wallet.locks.map { it.third }, "sans réponse : la MÊME clé est rejouée (jamais deux blocages pour un clic)")
        wallet.lock = { WalletResult.Fail(WalletMessages.of(409, "INSUFFICIENT"), 409, "INSUFFICIENT", false) }
        val f2 = stake.lock(plan) as ChessStakeFlow.Lock.Failed
        assertFalse(f2.network); assertEquals("INSUFFICIENT", f2.reason)
    }

    @Test fun aSignedResultIsKeptThenSettledAndTheTvShowsTheNetAfterFees() {
        stake.lock(PlayStakePlan(NDEM20, 2))
        wallet.settle = { WalletResult.Ok(SettleDone("a".repeat(32), "END", "NDEM", "quiz", 2, listOf(SettleLine(EID, ME, 40, 42, 2), SettleLine(EID_B, OTHER, 20, 18, 0)))) }
        stake.handleResult(result(PlayResult.Kind.END, 'a', PlayResult.Line(EID, ME, 40, 42), PlayResult.Line(EID_B, OTHER, 20, 18)))
        val done = stake.settlement as ChessOnlineGame.Settlement.Done
        assertEquals(0L, done.net, "42 versés − 2 de frais − 40 misés = 0 : la TV lit la réponse de l'API, elle ne calcule jamais un solde")
        assertTrue(changes.first() is ChessOnlineGame.Settlement.Pending, "d'abord « règlement en cours »")
        assertEquals(1, wallet.settles)
        assertTrue(store.pendingResults().isEmpty(), "réglé : plus rien à renvoyer")
    }

    @Test fun anAbortedGameShowsTheStakeBackAndAnOfflineSettlementIsKeptThenRetried() {
        stake.lock(PlayStakePlan(NDEM20, 1))
        val abort = result(PlayResult.Kind.ABORT, 'b', PlayResult.Line(EID, ME, 0, 0))
        wallet.settle = { WalletResult.Fail(WalletMessages.offline(), null, null, true) }
        stake.handleResult(abort)
        val later = stake.settlement as ChessOnlineGame.Settlement.Later
        assertTrue(later.text.contains("Partie interrompue : mise rendue"), later.text)
        assertEquals(listOf(abort), store.pendingResults(), "gardé : renvoyé au retour de la connexion (le service en garde aussi une copie pour son collecteur)")
        wallet.settle = { WalletResult.Ok(SettleDone("b".repeat(32), "ABORT", "NDEM", "quiz", 0, listOf(SettleLine(EID, ME, 0, 0, 0)))) }
        assertEquals(1, stake.retryPending()); assertTrue(store.pendingResults().isEmpty())
    }

    @Test fun aRefusedSettlementIsDroppedWithItsReason() {
        stake.lock(PlayStakePlan(NDEM20, 1))
        wallet.settle = { WalletResult.Fail(WalletMessages.of(409, "RESULT_AFTER_REFUND"), 409, "RESULT_AFTER_REFUND", false) }
        stake.handleResult(result(PlayResult.Kind.ABORT, 'c', PlayResult.Line(EID, ME, 0, 0)))
        assertTrue(stake.settlement is ChessOnlineGame.Settlement.Refused)
        assertTrue(store.pendingResults().isEmpty(), "un refus définitif n'est pas renvoyé")
        assertNotNull(stake.plan)
    }
}
