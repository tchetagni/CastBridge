package castbridge.core.chess.online

import castbridge.core.chess.ChessAct
import castbridge.core.chess.ChessTransportException
import castbridge.core.quiz.online.StakeSpec
import castbridge.core.wallet.ui.WalletMessages
import castbridge.core.wallet.ui.WalletResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * La partie en ligne de la TV de bout en bout, SANS socket : deux TV (vrai [castbridge.core.chess.ChessRelayClient], vraie [castbridge.core.quiz.online.PlayTvSession], vraie [ChessServerRoom]) reliées par
 * [ChessLoopback]. Le portefeuille est factice (il règle comme l'API : cagnotte, frais en points de base, rendu sur interruption). Les chemins réseau réels sont éprouvés dans `server-play`.
 */
class ChessOnlineGameTest {
    private val loop = ChessLoopback()
    private val a = TestTv("A", "AAAA-AAAA-AAAA-AAAA", loop)
    private val b = TestTv("B", "BBBB-BBBB-BBBB-BBBB", loop)
    private val NDEM20 = StakeSpec("NDEM", 20)

    private fun seated(o: ChessOnlineGame.Opened): ChessOnlineGame.Opened.Seated { assertTrue(o is ChessOnlineGame.Opened.Seated, o.toString()); return o as ChessOnlineGame.Opened.Seated }
    private fun ok(a: ChessAct) { assertEquals("OK", a.result, a.toString()) }

    /** 1. f3 e5 2. g4 Dh4# : les Noirs (B) matent. */
    private fun foolsMate() {
        ok(a.game.move("f2f3")); ok(b.game.move("e7e5")); ok(a.game.move("g2g4")); ok(b.game.move("d8h4"))
    }

    // ------------------------------------------------------------------ partie libre

    @Test fun aFreeGameIsPlayedByTwoTvsAndNothingIsSettled() {
        val s = seated(a.create(color = "white")).session
        assertEquals("w", s.color); assertEquals(0, a.wallet.locks, "une partie libre ne bloque rien")
        val j = seated(b.game.join(s.code, "TV B")).session
        assertEquals("b", j.color)
        assertEquals("PLAYING", a.game.view()["stage"]); assertEquals("PLAYING", b.game.view()["stage"])
        foolsMate()
        val v = b.game.view()
        assertEquals("FINISHED", v["stage"]); assertEquals("b", (v["result"] as Map<*, *>)["winner"])
        assertTrue(a.results.isEmpty() && b.results.isEmpty(), "aucun résultat signé pour une partie libre")
        assertEquals(0, a.wallet.settles + b.wallet.settles)
        assertNull(a.store.savedSeat(), "une partie libre finie n'a plus rien à reprendre")
        assertEquals(ChessOnlineGame.Settlement.None, a.game.settlement)
    }

    @Test fun theCodeIsNormalisedAndASpectatorWatchesWithoutStaking() {
        val s = seated(a.create(stake = NDEM20)).session
        val c = TestTv("C", "CCCC-CCCC-CCCC-CCCC", loop)
        val w = seated(c.game.join(s.code.lowercase().replace("-", " "), "TV Salon", spectate = true))
        assertEquals(0, c.wallet.locks, "un spectateur ne mise pas")
        assertEquals("LOBBY", c.game.view()["stage"])
        assertNull(w.stake)
    }

    // ------------------------------------------------------------------ partie misée

    @Test fun aStakedGameIsLockedPlayedAndSettledWithTheNetResultOfEachTv() {
        val s = seated(a.create(stake = NDEM20)).session
        assertEquals(1, a.wallet.locks); assertNull(a.store.pendingEscrow(), "le blocage employé est effacé")
        assertNotNull(a.store.savedSeat()!!.escrowId)
        // B apprend la mise, bloque la sienne, puis revient SUR LA MÊME liaison (aucun nouveau ticket)
        val need = b.game.join(s.code, "TV B")
        assertEquals(ChessOnlineGame.Opened.NeedsStake(NDEM20), need)
        assertEquals(0, b.wallet.locks, "rien n'est bloqué avant que le joueur ait accepté")
        seated(b.game.joinWithStake(s.code, "TV B", NDEM20))
        assertEquals(1, b.wallet.locks); assertEquals(1, b.transports, "même connexion, même ticket")
        foolsMate()
        assertEquals(1, a.results.size); assertEquals(1, b.results.size)
        val sa = a.game.settlement as ChessOnlineGame.Settlement.Done
        val sb = b.game.settlement as ChessOnlineGame.Settlement.Done
        assertEquals(-20L, sa.net); assertEquals(20L, sb.net)
        assertTrue(sa.text.startsWith("Vous perdez 20 NDEM"), sa.text); assertTrue(sb.text.startsWith("Vous gagnez 20 NDEM"), sb.text)
        assertEquals(1, a.wallet.settles); assertEquals(1, b.wallet.settles)
        assertNull(a.store.savedSeat()); assertNull(b.store.savedSeat())
        assertTrue(a.store.pendingResults().isEmpty() && b.store.pendingResults().isEmpty(), "réglé : plus rien à renvoyer")
    }

    @Test fun theWinnerSeesHisGainAfterThePlatformFee() {
        val tvC = TestTv("C", "CCCC-CCCC-CCCC-CCCC", loop)   // frais de 10 % (1000 points de base) pour le gagnant
        val tvD = TestTv("D", "DDDD-DDDD-DDDD-DDDD", loop, feeBp = 1_000)
        val s = seated(tvC.create(stake = NDEM20, color = "white")).session
        assertTrue(tvD.game.join(s.code, "TV D") is ChessOnlineGame.Opened.NeedsStake)
        seated(tvD.game.joinWithStake(s.code, "TV D", NDEM20))
        ok(tvC.game.move("f2f3")); ok(tvD.game.move("e7e5")); ok(tvC.game.move("g2g4")); ok(tvD.game.move("d8h4"))
        val won = tvD.game.settlement as ChessOnlineGame.Settlement.Done
        assertEquals(16L, won.net, "cagnotte 40 − frais 4 − mise 20")
        assertTrue(won.text.startsWith("Vous gagnez 16 NDEM"), won.text)
    }

    @Test fun aDrawGivesTheStakesBack() {
        val s = seated(a.create(stake = NDEM20)).session
        b.game.join(s.code, "TV B"); b.game.joinWithStake(s.code, "TV B", NDEM20)
        ok(a.game.draw("offer")); ok(b.game.draw("accept"))
        val sa = a.game.settlement as ChessOnlineGame.Settlement.Done
        assertEquals(0L, sa.net); assertTrue(sa.text.startsWith("Partie nulle : mise rendue"), sa.text)
    }

    @Test fun resigningLosesTheStake() {
        val s = seated(a.create(stake = NDEM20)).session
        b.game.join(s.code, "TV B"); b.game.joinWithStake(s.code, "TV B", NDEM20)
        ok(a.game.resign())
        assertEquals(-20L, (a.game.settlement as ChessOnlineGame.Settlement.Done).net)
        assertEquals(20L, (b.game.settlement as ChessOnlineGame.Settlement.Done).net)
    }

    @Test fun theHostWhoCancelsBeforeAnyoneComesGetsTheStakeBack() {
        seated(a.create(stake = NDEM20))
        ok(a.game.cancel())
        val sa = a.game.settlement as ChessOnlineGame.Settlement.Done
        assertTrue(sa.aborted); assertEquals(0L, sa.net); assertTrue(sa.text.startsWith("Partie interrompue : mise rendue"), sa.text)
    }

    @Test fun anAbsentTvForfeitsAfterSixtySecondsAndTheOtherSettlesTheWin() {
        val s = seated(a.create(stake = NDEM20, seconds = 60)).session
        b.game.join(s.code, "TV B"); b.game.joinWithStake(s.code, "TV B", NDEM20)
        loop.dropConnection("A")
        loop.tick(59_000L); assertEquals("PLAYING", b.game.view()["stage"])
        assertNotNull(castbridge.core.chess.online.ChessOnlineTexts.awayLine(b.game.view(), 0L), "B voit le décompte du forfait")
        loop.tick(2_000L)
        assertEquals("FINISHED", b.game.view()["stage"])
        assertEquals(20L, (b.game.settlement as ChessOnlineGame.Settlement.Done).net)
        assertNotNull(loop.spooled.singleOrNull(), "le service garde une copie du résultat pour le collecteur")
    }

    // ------------------------------------------------------------------ refus et coupures

    @Test fun aWalletRefusalOpensNothingAndSaysWhy() {
        a.wallet.refuseLock = WalletResult.Fail(WalletMessages.of(409, "STAKE_WIN_CAP", "Limite atteinte : vous avez gagné 3 parties aujourd'hui. Prochaine partie avec mise : demain à 00:00."), 409, "STAKE_WIN_CAP", false)
        val r = a.create(stake = NDEM20) as ChessOnlineGame.Opened.Failed
        assertTrue(r.text.startsWith("Limite atteinte"), r.text); assertFalse(r.network)
        assertEquals(0, a.transports, "aucune connexion ouverte"); assertNull(loop.room)
    }

    @Test fun aLockWithoutAnAnswerIsRetriedWithTheSameKeyNeverTwoBlocks() {
        a.wallet.offline = true
        val first = a.create(stake = NDEM20) as ChessOnlineGame.Opened.Failed
        assertTrue(first.network)
        a.wallet.offline = false
        seated(a.create(stake = NDEM20))
        assertEquals(2, a.wallet.lockKeys.size); assertEquals(a.wallet.lockKeys[0], a.wallet.lockKeys[1], "la même clé d'idempotence est rejouée")
    }

    @Test fun anOpeningFailureKeepsTheLockedStakeForTheNextGame() {
        loop.refuseNext = "PLAY_BUSY"
        val first = a.create(stake = NDEM20)
        assertTrue(first is ChessOnlineGame.Opened.Failed, first.toString())
        assertNotNull(a.store.pendingEscrow(), "le blocage n'a pas servi : il est gardé")
        seated(a.create(stake = NDEM20))
        assertEquals(1, a.wallet.locks, "le second essai réutilise le blocage : pas de second blocage")
        assertNull(a.store.pendingEscrow())
    }

    @Test fun anInvalidEscrowIsForgottenSoANewOneIsAsked() {
        loop.refuseNext = "STAKE_ESCROW_INVALID"
        assertTrue(a.create(stake = NDEM20) is ChessOnlineGame.Opened.Failed)
        assertNull(a.store.pendingEscrow())
        seated(a.create(stake = NDEM20))
        assertEquals(2, a.wallet.locks)
    }

    @Test fun aWalletRefusalWhenJoiningClosesTheOpenLink() {
        val s = seated(a.create(stake = NDEM20)).session
        assertTrue(b.game.join(s.code, "TV B") is ChessOnlineGame.Opened.NeedsStake)
        b.wallet.refuseLock = WalletResult.Fail(WalletMessages.of(409, "INSUFFICIENT", null, 5), 409, "INSUFFICIENT", false)
        val r = b.game.joinWithStake(s.code, "TV B", NDEM20) as ChessOnlineGame.Opened.Failed
        assertTrue(r.text.contains("insuffisant", ignoreCase = true), r.text)
        assertTrue(b.client.link().let { it == castbridge.core.quiz.online.TvLink.Lost || b.client.latest().isEmpty() })
        assertEquals("LOBBY", a.game.view()["stage"], "la partie de l'hôte attend toujours")
    }

    @Test fun anUnreadableCodeIsRefusedBeforeAnyNetwork() {
        val r = b.game.join("12", "TV B") as ChessOnlineGame.Opened.Failed
        assertTrue(r.text.contains("8 symboles"), r.text); assertEquals(0, b.transports)
    }

    @Test fun aResultThatCannotBeSettledNowIsKeptAndSentAgainLater() {
        val s = seated(a.create(stake = NDEM20)).session
        b.game.join(s.code, "TV B"); b.game.joinWithStake(s.code, "TV B", NDEM20)
        b.wallet.offline = true
        foolsMate()
        val later = b.game.settlement as ChessOnlineGame.Settlement.Later
        assertTrue(later.text.contains("Règlement en attente"), later.text)
        assertEquals(1, b.store.pendingResults().size, "le résultat signé est gardé")
        assertNotNull(b.store.savedSeat(), "le siège aussi : le règlement ne doit pas se perdre")
        b.wallet.offline = false
        assertEquals(1, b.game.retryPending())
        assertTrue(b.store.pendingResults().isEmpty())
        assertEquals(1, b.wallet.settledRids.size)
    }

    @Test fun anApiRefusalOfTheResultIsShownAndNotSentAgain() {
        val s = seated(a.create(stake = NDEM20)).session
        b.game.join(s.code, "TV B"); b.game.joinWithStake(s.code, "TV B", NDEM20)
        // le règlement de B est refusé par l'API (par exemple : la partie a déjà été rendue à l'échéance)
        b.wallet.refuseSettle = WalletResult.Fail(WalletMessages.of(409, "RESULT_AFTER_REFUND"), 409, "RESULT_AFTER_REFUND", false)
        foolsMate()
        val refused = b.game.settlement as ChessOnlineGame.Settlement.Refused
        assertTrue(refused.text.contains("Règlement refusé"), refused.text)
        assertTrue(b.store.pendingResults().isEmpty(), "un refus définitif n'est pas renvoyé")
        assertTrue(a.game.settlement is ChessOnlineGame.Settlement.Done, "l'autre TV règle normalement")
    }

    // ------------------------------------------------------------------ reprise

    @Test fun anApplicationClosedMidGameComesBackToItsSeat() {
        val s = seated(a.create(stake = NDEM20)).session
        b.game.join(s.code, "TV B"); b.game.joinWithStake(s.code, "TV B", NDEM20)
        ok(a.game.move("e2e4"))
        val saved = a.store.savedSeat()!!
        // l'application de A est fermée : plus de client ; une NOUVELLE TV (même mémoire) reprend avec le siège gardé
        a.game.client.close(); loop.dropConnection("A")
        loop.tick(5_000L)
        val a2 = TestTv("A", "AAAA-AAAA-AAAA-AAAA", loop)
        a2.store.saveSeat(saved)
        val r = a2.game.resume()
        assertTrue(r is ChessOnlineGame.Opened.Seated && r.resumed, r.toString())
        assertEquals("PLAYING", a2.game.view()["stage"]); assertEquals(1, (a2.game.view()["ply"] as Number).toInt())
        ok(b.game.move("e7e5"))
        ok(a2.game.move("g1f3"))
    }

    @Test fun aSavedSeatTooOldOrForAGoneRoomIsForgotten() {
        a.store.saveSeat(SavedSeat("0123456789abcdef0123456789abcdef", "tok", "TV A", "ABCD-EFGH", "w", null, null, savedAtMs = 1L))
        assertNull(a.game.resume(), "plus de 6 minutes : rien à reprendre"); assertNull(a.store.savedSeat())
        // récent mais la salle n'existe plus côté service
        a.store.saveSeat(SavedSeat("0123456789abcdef0123456789abcdef", "tok", "TV A", "ABCD-EFGH", "w", null, null, savedAtMs = 5_000_000L))
        seated(b.create())                                  // une autre salle existe, pas celle-ci
        val r = a.game.resume()
        assertTrue(r is ChessOnlineGame.Opened.Failed, r.toString())
        assertNull(a.store.savedSeat(), "salle disparue : le siège gardé est effacé")
    }

    // ------------------------------------------------------------------ téléphones du foyer

    @Test fun householdPhonesWatchThroughTheTvAndNeverSeeASecretOrCommand() {
        val s = seated(a.create(stake = NDEM20)).session
        b.game.join(s.code, "TV B"); b.game.joinWithStake(s.code, "TV B", NDEM20)
        val p = a.host.joinRoom(a.host.code, "Papa", null).player!!
        ok(a.game.move("e2e4"))
        val v = a.host.view(p.token)
        assertEquals("PLAYING", v["stage"]); assertEquals(1, (v["ply"] as Number).toInt())
        assertEquals(emptyList<String>(), v["legal"], "un téléphone ne joue pas")
        assertFalse(v.containsKey("room"), "aucun identifiant de salle sur un téléphone")
        val json = a.host.viewJson(p.token)
        for (secret in listOf("cbe1", "cbr1", "token", s.token, a.store.savedSeat()!!.token)) assertFalse(json.contains(secret), "« $secret » ne doit pas paraître dans la vue d'un téléphone")
        assertEquals(castbridge.core.games.ActResult.FORBIDDEN, a.host.httpAct(p.token, "move", "e7e5", 1))
        assertEquals("NDEM", ((v["stake"] as Map<*, *>)["cur"]))
    }
}
