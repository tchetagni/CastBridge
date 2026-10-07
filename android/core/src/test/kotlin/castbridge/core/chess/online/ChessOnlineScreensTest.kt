package castbridge.core.chess.online

import castbridge.core.quiz.online.HostEdition
import castbridge.core.quiz.online.PlayRelay
import castbridge.core.quiz.online.StakeSpec
import castbridge.core.wallet.ui.GamePolicyView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** « Échecs › En ligne » : la porte, ce que le service annonce, les mises permises, l'écran de création, les textes. Tout est pur : l'écran Canvas ne décide rien. */
class ChessOnlineScreensTest {
    private val caps = ChessServiceCaps(chess = true, stakes = true)
    private fun tile(flag: Boolean = true, edition: HostEdition = HostEdition.PROD, internet: Boolean = true, child: Boolean = false, clock: Boolean = false, c: ChessServiceCaps? = caps, reachable: Boolean? = true, verifiable: Boolean = true) =
        ChessOnlineGate.tile(flag, edition, internet, child, clock, c, reachable, verifiable)

    // ------------------------------------------------------------------ service

    @Test fun theServiceSaysItHostsChessAndStakesOnItsCapsPage() {
        val c = ChessServiceCaps.parse("""{"name":"play-v1","proto":1,"caps":["relay","chess","stakes"],"chess":true,"stakes":true,"revocations":"on"}""")!!
        assertTrue(c.chess && c.stakes && !c.revocationsOff)
        val off = ChessServiceCaps.parse("""{"name":"play-v1","caps":["relay"]}""")!!
        assertFalse(off.chess, "un service plus ancien : pas d'échecs"); assertFalse(off.stakes)
        val chessOnly = ChessServiceCaps.parse("""{"name":"play-v1","caps":["chess"],"chess":true,"stakes":false}""")!!
        assertTrue(chessOnly.chess); assertFalse(chessOnly.stakes)
    }

    @Test fun anotherServiceOrGarbageIsNotTrusted() {
        assertNull(ChessServiceCaps.parse(null)); assertNull(ChessServiceCaps.parse("")); assertNull(ChessServiceCaps.parse("<html>proxy</html>"))
        assertNull(ChessServiceCaps.parse("""{"name":"autre","chess":true}"""))
    }

    // ------------------------------------------------------------------ la porte

    @Test fun theTileIsHiddenWithoutTheSwitchAndOpenWhenEverythingIsThere() {
        assertEquals(ChessOnlineTile.Hidden, tile(flag = false))
        assertEquals(ChessOnlineTile.Available, tile())
        assertEquals(ChessOnlineTile.Available, tile(edition = HostEdition.TRIAL), "l'essai joue en ligne, sans mise")
        assertEquals(ChessOnlineTile.Available, tile(c = null, reachable = null), "service pas encore interrogé : la TV essaiera")
    }

    @Test fun everyBlockedTileSaysWhyInPlainFrench() {
        fun reason(t: ChessOnlineTile) = (t as ChessOnlineTile.Blocked).reason
        assertTrue(reason(tile(edition = HostEdition.NONE)).contains("Activez"))
        assertEquals(ChessOnlineGate.MSG_ACTIVATION_FILE, reason(tile(verifiable = false)))
        assertEquals(ChessOnlineGate.MSG_CHILD, reason(tile(child = true)))
        assertEquals(ChessOnlineGate.MSG_NO_INTERNET, reason(tile(internet = false)))
        assertEquals(ChessOnlineGate.MSG_SERVICE_DOWN, reason(tile(reachable = false)))
        assertEquals(ChessOnlineGate.MSG_NOT_OPEN, reason(tile(c = ChessServiceCaps(chess = false, stakes = false))))
        assertNotNull(reason(tile(clock = true)))
    }

    @Test fun withoutInternetTheEntryStaysOpenWhenASyncedPhoneCanGiveAPipeAndSaysWhyOtherwise() {
        // relay-R1 : une TV sans Internet reste « en ligne » dès qu'un téléphone synchronisé peut lui ouvrir un tuyau ; l'interrupteur et l'activation passent toujours avant
        fun t(relay: PlayRelay, internet: Boolean = false, edition: HostEdition = HostEdition.PROD, flag: Boolean = true) =
            ChessOnlineGate.tile(flag, edition, internet, childProfile = false, clockDoubt = false, caps = null, serviceReachable = null, relay = relay)
        assertEquals(ChessOnlineTile.Available, t(PlayRelay.POSSIBLE))
        assertEquals(ChessOnlineTile.Blocked(castbridge.core.relay.RelayText.NO_PHONE), t(PlayRelay.NO_PHONE))
        assertEquals(ChessOnlineTile.Blocked(castbridge.core.relay.RelayText.OLD_PHONE), t(PlayRelay.OLD_PHONE))
        assertEquals(ChessOnlineTile.Blocked(ChessOnlineGate.MSG_NO_INTERNET), t(PlayRelay.UNKNOWN))
        assertEquals(ChessOnlineTile.Available, t(PlayRelay.NO_PHONE, internet = true), "avec Internet le fait du relais ne compte pas")
        assertEquals(ChessOnlineTile.Hidden, t(PlayRelay.POSSIBLE, flag = false), "interrupteur éteint : rien")
        assertTrue(t(PlayRelay.POSSIBLE, edition = HostEdition.NONE) is ChessOnlineTile.Blocked, "TV non activée : jamais un tuyau")
    }

    @Test fun aBlockedTileNeverOpensTheNetwork() {
        listOf(tile(flag = false), tile(edition = HostEdition.NONE), tile(child = true), tile(internet = false), tile(reachable = false), tile(c = ChessServiceCaps(false, false)), tile(clock = true))
            .forEach { assertFalse(ChessOnlineGate.mayOpenNetwork(it), it.toString()) }
        assertTrue(ChessOnlineGate.mayOpenNetwork(tile()))
    }

    // ------------------------------------------------------------------ mises permises

    private fun policy(enabled: Boolean = true, n: List<Long> = listOf(10, 20, 50, 100, 200), m: List<Long> = listOf(1, 2, 5, 10)) = GamePolicyView(enabled, n, m, 0, 3, 10, 15)
    private fun avail(edition: HostEdition = HostEdition.PROD, c: ChessServiceCaps? = caps, wallet: Boolean = true, n: Boolean = true, m: Boolean = true, frozen: Boolean = false, p: GamePolicyView? = policy()) =
        StakeAvailability.of(edition, c, wallet, n, m, frozen, p)

    @Test fun aTrialTvPlaysFreeGamesOnlyEvenWithTokens() {
        val a = avail(edition = HostEdition.TRIAL) as StakeAvailability.FreeOnly
        assertTrue(a.reason.contains("libres seulement"), a.reason)
        assertTrue(avail(edition = HostEdition.NONE) is StakeAvailability.FreeOnly)
    }

    @Test fun stakesAreOfferedOnlyWhenTheServiceTheWalletAndThePolicyAllowThem() {
        assertEquals(StakeAvailability.Allowed(listOf(10, 20, 50, 100, 200), listOf(1, 2, 5, 10)), avail())
        assertTrue(avail(c = ChessServiceCaps(true, false)) is StakeAvailability.FreeOnly, "le service coupe les mises")
        assertTrue(avail(wallet = false) is StakeAvailability.FreeOnly, "aucun instantané signé")
        assertTrue(avail(frozen = true) is StakeAvailability.FreeOnly, "compte gelé")
        assertTrue(avail(p = policy(enabled = false)) is StakeAvailability.FreeOnly, "mises aux échecs coupées par l'exploitation")
        assertEquals(StakeAvailability.Allowed(emptyList(), listOf(1, 2, 5, 10)), avail(n = false), "une monnaie coupée n'est pas proposée")
        assertTrue(avail(n = false, m = false) is StakeAvailability.FreeOnly)
    }

    @Test fun anOlderServerWithoutPolicyGivesTheOwnersDefaultScale() {
        val a = avail(p = null) as StakeAvailability.Allowed
        assertEquals(listOf(10L, 20L, 50L, 100L, 200L), a.ndem); assertEquals(listOf(1L, 2L, 5L, 10L), a.mboko)
    }

    // ------------------------------------------------------------------ l'écran de création

    @Test fun theChoiceStartsFreeAndCyclesThroughTheOfferedCurrenciesAndAmounts() {
        val c = ChessStakeChoice(avail(), balanceNdem = 150, balanceMboko = 3)
        assertEquals(ChessStakeChoice.Mode.FREE, c.mode); assertNull(c.stake()); assertNull(c.warning()); assertNull(c.balanceLine()); assertTrue(c.affordable())
        c.cycleMode(1)
        assertEquals(ChessStakeChoice.Mode.NDEM, c.mode); assertEquals(StakeSpec("NDEM", 10), c.stake())
        c.cycleAmount(1); c.cycleAmount(1)
        assertEquals(StakeSpec("NDEM", 50), c.stake()); assertEquals("50 NDEM par joueur", c.amountText())
        assertEquals("Votre solde : 150 NDEM", c.balanceLine()); assertEquals("Cagnotte : 100 NDEM pour le gagnant", c.potLine())
        assertEquals(ChessOnlineTexts.ABANDON_WARNING, c.warning()); assertTrue(c.affordable())
        c.cycleAmount(2)
        assertEquals(StakeSpec("NDEM", 200), c.stake()); assertFalse(c.affordable(), "200 > 150")
        c.cycleMode(1)
        assertEquals(ChessStakeChoice.Mode.MBOKO, c.mode); assertEquals(StakeSpec("MBOKO", 1), c.stake(), "changer de monnaie repart du plus petit palier")
        c.cycleAmount(-1)
        assertEquals(StakeSpec("MBOKO", 10), c.stake(), "‹ fait le tour de la liste")
        c.cycleMode(1)
        assertEquals(ChessStakeChoice.Mode.FREE, c.mode); assertNull(c.stake())
    }

    @Test fun whenOnlyFreeGamesAreAllowedTheChoiceHasOnlyFreeAndSaysWhy() {
        val c = ChessStakeChoice(avail(edition = HostEdition.TRIAL), null, null)
        assertEquals(listOf(ChessStakeChoice.Mode.FREE), c.modes)
        c.cycleMode(1); c.cycleAmount(1)
        assertEquals(ChessStakeChoice.Mode.FREE, c.mode); assertNull(c.stake())
        assertEquals(StakeAvailability.MSG_TRIAL, c.freeOnlyReason())
    }

    @Test fun anUnknownBalanceLeavesTheJudgementToTheApi() {
        val c = ChessStakeChoice(avail(), null, null)
        c.cycleMode(1)
        assertTrue(c.affordable()); assertNull(c.balanceLine())
    }

    // ------------------------------------------------------------------ textes

    @Test fun theEndOfGameTextsAreSpecificAndNeverAmbiguous() {
        assertEquals("Vous gagnez 1 840 NDEM", ChessOnlineTexts.outcome("NDEM", 1840, false).replace(' ', ' ').replace(' ', ' '))
        assertEquals("Vous perdez 20 NDEM", ChessOnlineTexts.outcome("NDEM", -20, false))
        assertEquals("Partie nulle : mise rendue", ChessOnlineTexts.outcome("NDEM", 0, false))
        assertEquals("Partie interrompue : mise rendue", ChessOnlineTexts.outcome("MBOKO", 0, true), "une interruption n'est jamais appelée nulle")
        assertTrue(ChessOnlineTexts.stakeLine(StakeSpec("NDEM", 20)).contains("cagnotte 40 NDEM"))
        assertTrue(ChessOnlineTexts.joinStakeQuestion(StakeSpec("MBOKO", 5)).contains("5 MBOKO"))
        assertTrue(ChessOnlineTexts.ABANDON_WARNING.contains("60 s"))
        assertTrue(ChessOnlineTexts.ABANDON_WARNING.length <= 100, "tient sur deux lignes de la TV")
    }

    @Test fun theConfirmationLineShowsTheAmountAndTheBalanceWhenKnown() {
        assertEquals("20 NDEM par joueur · votre solde 150 NDEM", ChessOnlineTexts.stakeSubtitle(StakeSpec("NDEM", 20), 150))
        assertEquals("5 MBOKO par joueur", ChessOnlineTexts.stakeSubtitle(StakeSpec("MBOKO", 5), null))
    }

    @Test fun everyServiceAnswerHasAPlainTextOrNoneAtAll() {
        for (ok in listOf("OK", "IGNORED", "SAME")) assertNull(ChessOnlineTexts.ackText(ok), ok)
        val shown = listOf("ILLEGAL", "NOT_YOUR_TURN", "STALE", "OVER", "CLOSED", "FORBIDDEN", "UNKNOWN_PLAYER", "BAD_REQUEST", "UN_CODE_FUTUR").map { ChessOnlineTexts.ackText(it) }
        shown.forEach { assertNotNull(it); assertFalse(it!!.contains("_"), "jamais un code brut : $it") }
        assertTrue(shown.all { !it.isNullOrBlank() }, "aucun texte vide")
    }

    @Test fun theForfeitCountdownRunsFromTheViewAndOnlyWhileTheOpponentIsAway() {
        val view = mapOf("stage" to "PLAYING", "me" to mapOf("color" to "w"), "room" to mapOf("forfeitMs" to 60_000L, "away" to mapOf("w" to null, "b" to 10_000L)))
        assertEquals("Adversaire déconnecté : forfait dans 50 s", ChessOnlineTexts.awayLine(view, 0L))
        assertEquals("Adversaire déconnecté : forfait dans 45 s", ChessOnlineTexts.awayLine(view, 5_000L))
        assertEquals("Adversaire déconnecté : forfait dans 0 s", ChessOnlineTexts.awayLine(view, 90_000L), "jamais négatif")
        assertNull(ChessOnlineTexts.awayLine(view + ("stage" to "FINISHED"), 0L))
        assertNull(ChessOnlineTexts.awayLine(mapOf("stage" to "PLAYING", "me" to mapOf("color" to "b"), "room" to mapOf("away" to mapOf("w" to null, "b" to 10_000L))), 0L), "c'est moi qui suis absent : la liaison le dit, pas ce texte")
        assertNull(ChessOnlineTexts.awayLine(mapOf("stage" to "PLAYING", "me" to mapOf("color" to null), "room" to mapOf("away" to mapOf("w" to 1L))), 0L), "un spectateur ne voit pas de décompte")
    }

    // ------------------------------------------------------------------ lecture d'un résultat signé (affichage seulement)

    @Test fun aSignedResultIsReadForDisplayAndGarbageIsNot() {
        val loop = ChessLoopback()
        val a = TestTv("A", "AAAA-AAAA-AAAA-AAAA", loop); val b = TestTv("B", "BBBB-BBBB-BBBB-BBBB", loop)
        val s = (a.create(StakeSpec("NDEM", 20)) as ChessOnlineGame.Opened.Seated).session
        b.game.join(s.code, "B"); b.game.joinWithStake(s.code, "B", StakeSpec("NDEM", 20))
        a.game.resign()
        val sum = ChessResultSummary.of(b.results.single())!!
        assertEquals("END", sum.kind); assertEquals("NDEM", sum.cur); assertEquals(20L, sum.per)
        val mineB = b.store.savedSeat()?.escrowId ?: b.game.let { sum.lines.first { it.pay > it.used }.eid }
        assertEquals(20L, sum.netOf(mineB)); assertNull(sum.netOf("EidInconnu")); assertFalse(sum.aborted)
        assertNull(ChessResultSummary.of(null)); assertNull(ChessResultSummary.of("cbr1.x")); assertNull(ChessResultSummary.of("cbe1.a.b")); assertNull(ChessResultSummary.of("cbr1.!!!.zz"))
    }
}
