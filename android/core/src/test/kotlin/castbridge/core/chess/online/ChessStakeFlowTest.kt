package castbridge.core.chess.online

import castbridge.core.quiz.online.StakeSpec
import castbridge.core.wallet.WalletCurrency
import castbridge.core.wallet.ui.EscrowDone
import castbridge.core.wallet.ui.SettleDone
import castbridge.core.wallet.ui.SettleLine
import castbridge.core.wallet.ui.WalletMessages
import castbridge.core.wallet.ui.WalletResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Les décisions d'argent de la TV, une à une : réutiliser un blocage, rejouer la clé, garder ou lâcher un résultat selon la réponse de l'API. Aucun réseau : un portefeuille scripté. */
class ChessStakeFlowTest {
    private class Wallet : ChessWallet {
        var lock: () -> WalletResult<EscrowDone> = { WalletResult.Ok(EscrowDone("cbe1.a.b", "EidAAAAAAAAAAAAAAAAAAA", 0L, 10_000_000L, false, null)) }
        var settle: (String) -> WalletResult<SettleDone> = { WalletResult.Ok(SettleDone("r".repeat(32), "END", "NDEM", "chess", 0, listOf(SettleLine("EidAAAAAAAAAAAAAAAAAAA", "AAAA-AAAA-AAAA-AAAA", 20, 40, 0)))) }
        val keys = ArrayList<String>()
        var settles = 0
        override fun lockEscrow(cur: WalletCurrency, per: Long, game: String, idem: String): WalletResult<EscrowDone> { keys += idem; return lock() }
        override fun settle(token: String): WalletResult<SettleDone> { settles++; return settle.invoke(token) }
    }

    private fun fail(status: Int?, reason: String?, network: Boolean = false) = WalletResult.Fail(
        if (network) WalletMessages.offline() else WalletMessages.of(status ?: 500, reason), status, reason, network)

    private val wallet = Wallet()
    private val store = InMemoryChessStakeStore()
    private var n = 0
    private val flow = ChessStakeFlow(wallet, store, { "key-${n++}" })
    private val NDEM20 = StakeSpec("NDEM", 20)

    @Test fun aLockedStakeIsKeptThenReusedWhileItHasEnoughValidityLeft() {
        val first = flow.lock(NDEM20, 1_000L) as ChessStakeFlow.Lock.Ok
        assertFalse(first.reused); assertEquals(1, wallet.keys.size)
        val again = flow.lock(NDEM20, 2_000L) as ChessStakeFlow.Lock.Ok
        assertTrue(again.reused); assertEquals(1, wallet.keys.size, "aucun second blocage"); assertEquals(first.escrow, again.escrow)
        // un autre montant, une autre monnaie : un autre blocage
        assertFalse((flow.lock(StakeSpec("NDEM", 50), 2_000L) as ChessStakeFlow.Lock.Ok).reused)
        assertFalse((flow.lock(StakeSpec("MBOKO", 5), 2_000L) as ChessStakeFlow.Lock.Ok).reused)
    }

    @Test fun aStakeThatIsAboutToExpireIsNotReusedBecauseTheRoomWouldRefuseIt() {
        flow.lock(NDEM20, 1_000L)
        val nearlyOver = 10_000_000L - ChessStakeFlow.MIN_LEFT_MS + 1
        assertFalse((flow.lock(NDEM20, nearlyOver) as ChessStakeFlow.Lock.Ok).reused)
        assertEquals(2, wallet.keys.size)
    }

    @Test fun anUsedStakeIsForgottenSoItIsNeverCarriedToTwoRooms() {
        flow.lock(NDEM20, 1_000L)
        flow.escrowUsed()
        assertNull(store.pendingEscrow())
        assertFalse((flow.lock(NDEM20, 1_500L) as ChessStakeFlow.Lock.Ok).reused)
    }

    @Test fun aRejectedStakeIsForgottenToo() {
        flow.lock(NDEM20, 1_000L)
        flow.escrowRejected()
        assertNull(store.pendingEscrow())
    }

    @Test fun aLockWithoutAnswerReplaysTheSameKeyButARefusalStartsFresh() {
        wallet.lock = { fail(null, null, network = true) }
        val a = flow.lock(NDEM20, 1_000L) as ChessStakeFlow.Lock.Failed
        assertTrue(a.network)
        flow.lock(NDEM20, 1_100L)
        assertEquals(wallet.keys[0], wallet.keys[1], "coupure : la MÊME clé d'idempotence")
        wallet.lock = { fail(409, "INSUFFICIENT") }
        val r = flow.lock(NDEM20, 1_200L) as ChessStakeFlow.Lock.Failed
        assertFalse(r.network); assertEquals("INSUFFICIENT", r.reason); assertTrue(r.text.isNotBlank())
        wallet.lock = { fail(409, "INSUFFICIENT") }
        flow.lock(NDEM20, 1_300L)
        assertNotEquals(wallet.keys[2], wallet.keys[3], "un refus du serveur est définitif : l'essai suivant a sa propre clé")
    }

    @Test fun anUnknownCurrencyIsRefusedBeforeAnyCall() {
        val r = flow.lock(StakeSpec("EUR", 20), 1_000L) as ChessStakeFlow.Lock.Failed
        assertFalse(r.network); assertTrue(wallet.keys.isEmpty())
    }

    // ------------------------------------------------------------------ règlement

    @Test fun aSettledResultIsRemovedFromTheMemory() {
        val r = flow.onResult("cbr1.x.y")
        assertTrue(r is ChessStakeFlow.Settled.Done); assertTrue(store.pendingResults().isEmpty())
    }

    @Test fun whatMayComeBackLaterIsKeptAndWhatIsFinalIsDropped() {
        for ((status, keep) in listOf(403 to true, 429 to true, 500 to true, 502 to true, 503 to true, 400 to false, 404 to false, 409 to false, 422 to false)) {
            val s = InMemoryChessStakeStore()
            val f = ChessStakeFlow(wallet.also { it.settle = { fail(status, if (status == 409) "RESULT_AFTER_REFUND" else null) } }, s, { "k" })
            val r = f.onResult("cbr1.$status.sig")
            if (keep) { assertEquals(ChessStakeFlow.Settled.Later, r, "$status"); assertEquals(listOf("cbr1.$status.sig"), s.pendingResults(), "$status") }
            else { assertTrue(r is ChessStakeFlow.Settled.Refused, "$status"); assertTrue(s.pendingResults().isEmpty(), "$status") }
        }
    }

    @Test fun noAnswerKeepsTheResultAndRetryingSettlesIt() {
        wallet.settle = { fail(null, null, network = true) }
        assertEquals(ChessStakeFlow.Settled.Later, flow.onResult("cbr1.a.b"))
        assertEquals(1, store.pendingResults().size)
        assertTrue(flow.retryPending().isEmpty(), "toujours hors ligne : rien de réglé, rien de perdu")
        assertEquals(1, store.pendingResults().size)
        wallet.settle = { WalletResult.Ok(SettleDone("r".repeat(32), "END", "NDEM", "chess", 2, listOf(SettleLine("EidAAAAAAAAAAAAAAAAAAA", "AAAA-AAAA-AAAA-AAAA", 20, 40, 2)))) }
        val done = flow.retryPending()
        assertEquals(1, done.size); assertTrue(store.pendingResults().isEmpty())
        assertEquals(18L, flow.netAfterFees(done.single(), "EidAAAAAAAAAAAAAAAAAAA"), "cagnotte 40 − frais 2 − mise 20")
        assertNull(flow.netAfterFees(done.single(), "EidInconnu"))
    }

    @Test fun anInterruptedGameIsAlwaysZeroNetWhateverTheLines() {
        val aborted = SettleDone("r".repeat(32), "ABORT", "NDEM", "chess", 0, listOf(SettleLine("EidA", "AAAA", 0, 0, 0)))
        assertEquals(0L, flow.netAfterFees(aborted, "EidA"))
    }

    @Test fun theStoreKeepsAtMostEightResultsAndNeverTwiceTheSame() {
        repeat(12) { store.addResult("cbr1.$it.sig") }
        store.addResult("cbr1.11.sig")
        assertEquals(ChessStakeStore.MAX_RESULTS, store.pendingResults().size)
        assertEquals("cbr1.4.sig", store.pendingResults().first(), "les plus anciens partent d'abord")
    }

    @Test fun aSavedSeatNeverPrintsItsSecretAndExpiresAfterSixMinutes() {
        val seat = SavedSeat("room", "seat-secret-token", "TV", "ABCD-EFGH", "w", NDEM20, "EidA", 1_000L)
        assertFalse(seat.toString().contains("seat-secret-token"))
        assertTrue(seat.fresh(1_000L + SavedSeat.KEEP_MS)); assertFalse(seat.fresh(1_001L + SavedSeat.KEEP_MS)); assertFalse(seat.fresh(999L), "une horloge qui recule n'est pas fraîche")
        val p = PendingEscrow("NDEM", 20, "cbe1.secret.sig", "EidA", 5L)
        assertFalse(p.toString().contains("cbe1"))
        assertFalse(EscrowDone("cbe1.secret.sig", "EidA", 1L, 2L, false, "cbw1.s.s").toString().contains("secret"))
    }
}
