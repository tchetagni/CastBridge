package castbridge.core.wallet.ui

import castbridge.core.wallet.WalletCurrency
import castbridge.core.wallet.ui.WalletFlow.Event
import castbridge.core.wallet.ui.WalletFlow.Op
import castbridge.core.wallet.ui.WalletFlow.Pending
import castbridge.core.wallet.ui.WalletFlow.State
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class WalletFlowTest {
    private var n = 0
    private fun key() = "k${++n}"
    private val ok = OpGate(true, null)
    private val no = OpGate(false, "Connexion Internet nécessaire")
    private fun step(s: State, e: Event) = WalletFlow.reduce(s, e, ::key)
    private val net = WalletMessages.offline()
    private val page = HistoryPage(emptyList(), null)

    @Test fun aClosedGateKeepsTheMenu() {
        for (op in WalletOp.values()) assertEquals(State.Menu, step(State.Menu, Event.Open(op, no)), op.name)
        assertEquals(0, n)
    }

    @Test fun convertWalksDirectionAmountConfirmThenSendsWithOneKey() {
        var s: State = step(State.Menu, Event.Open(WalletOp.CONVERT, ok))
        assertEquals(State.ConvertDirection, s)
        s = step(s, Event.PickDirection(ConvertDir.N2M)); assertEquals(State.ConvertAmount(ConvertDir.N2M), s)
        assertEquals(s, step(s, Event.AmountEntered(0)))                                  // zéro : on reste
        assertEquals(s, step(s, Event.AmountEntered(-3)))
        s = step(s, Event.AmountEntered(12)); assertEquals(State.ConvertConfirm(ConvertDir.N2M, 12), s)
        assertEquals(0, n)                                                                // aucune clé avant la confirmation
        s = step(s, Event.Confirm)
        assertEquals(State.Sending(Pending(Op.Convert(ConvertDir.N2M, 12), "k1")), s)
        s = step(s, Event.Succeeded("Conversion faite")); assertEquals(State.Done("Conversion faite"), s)
        assertEquals(State.Menu, step(s, Event.Back))
    }

    @Test fun backStepsOneScreenAtATimeAndNeverLeavesASendingOperation() {
        val confirm = State.ConvertConfirm(ConvertDir.M2N, 5)
        assertEquals(State.ConvertAmount(ConvertDir.M2N), step(confirm, Event.Back))
        assertEquals(State.ConvertDirection, step(State.ConvertAmount(ConvertDir.M2N), Event.Back))
        assertEquals(State.Menu, step(State.ConvertDirection, Event.Back))
        val sending = State.Sending(Pending(Op.Convert(ConvertDir.M2N, 5), "k9"))
        assertEquals(sending, step(sending, Event.Back))
        assertEquals(sending, step(sending, Event.Confirm))                                // pas de double envoi
    }

    @Test fun aNetworkFailureKeepsTheSameKeyForTheRetry() {
        val sending = State.Sending(Pending(Op.Convert(ConvertDir.N2M, 3), "kA"))
        val failed = step(sending, Event.FailedWith(net, retryable = true))
        assertEquals(State.Failed(net, Pending(Op.Convert(ConvertDir.N2M, 3), "kA")), failed)
        val again = step(failed, Event.Retry)
        assertEquals(sending, again)                                                       // MÊME clé : le serveur ne double pas l'opération
        assertEquals(0, n)
    }

    @Test fun aRefusalCannotBeRetriedAndBackGoesToTheMenu() {
        val sending = State.Sending(Pending(Op.Send(WalletCurrency.NDEM, "R123-4567-89", 50), "kB"))
        val shown = WalletMessages.of(409, "CODE_EXPIRED")
        val failed = step(sending, Event.FailedWith(shown, retryable = false))
        assertEquals(State.Failed(shown, null), failed)
        assertEquals(failed, step(failed, Event.Retry))
        assertEquals(State.Menu, step(failed, Event.Back))
    }

    @Test fun eachNewOperationGetsItsOwnKey() {
        fun run(): String {
            var s: State = step(State.Menu, Event.Open(WalletOp.CONVERT, ok))
            s = step(s, Event.PickDirection(ConvertDir.N2M)); s = step(s, Event.AmountEntered(1)); s = step(s, Event.Confirm)
            return (s as State.Sending).pending.idem
        }
        val a = run(); val b = run()
        assertNotEquals(a, b)
    }

    @Test fun sendAsksCurrencyThenCodeThenAmountThenConfirms() {
        var s: State = step(State.Menu, Event.Open(WalletOp.SEND, ok))
        assertEquals(State.SendCurrency, s)
        s = step(s, Event.PickCurrency(WalletCurrency.MBOKO)); assertEquals(State.SendCode(WalletCurrency.MBOKO), s)
        s = step(s, Event.CodeEntered("R123-4567-89")); assertEquals(State.SendAmount(WalletCurrency.MBOKO, "R123-4567-89"), s)
        assertEquals(State.SendCode(WalletCurrency.MBOKO), step(s, Event.Back))
        s = step(s, Event.AmountEntered(4)); assertEquals(State.SendConfirm(WalletCurrency.MBOKO, "R123-4567-89", 4), s)
        s = step(s, Event.Confirm)
        assertEquals(State.Sending(Pending(Op.Send(WalletCurrency.MBOKO, "R123-4567-89", 4), "k1")), s)
    }

    @Test fun receiveAndHistoryLoadThenShow() {
        var s: State = step(State.Menu, Event.Open(WalletOp.RECEIVE, ok)); assertEquals(State.ReceiveLoading, s)
        s = step(s, Event.ReceiveReady("R123-4567-89", 5L)); assertEquals(State.ReceiveShown("R123-4567-89", 5L), s)
        assertEquals(State.Menu, step(s, Event.Back))
        assertEquals(State.Failed(net, null), step(State.ReceiveLoading, Event.FailedWith(net, true)))
        var h: State = step(State.Menu, Event.Open(WalletOp.HISTORY, ok)); assertEquals(State.HistoryLoading, h)
        h = step(h, Event.HistoryReady(page, cached = true)); assertEquals(State.HistoryShown(page, true), h)
        assertEquals(State.Menu, step(h, Event.Back))
        assertEquals(State.Failed(net, null), step(State.HistoryLoading, Event.FailedWith(net, true)))
    }

    @Test fun olderHistoryPagesReplaceTheShownPage() {
        val first = State.HistoryShown(page, cached = false)
        val more = HistoryPage(listOf(HistoryLine(1, "GRANT", "NDEM", 1, 1, "Attribution mensuelle", null)), null)
        assertEquals(State.HistoryShown(more, false), step(first, Event.HistoryReady(more, cached = false)))
    }

    @Test fun eventsThatMakeNoSenseHereAreIgnored() {
        assertEquals(State.Menu, step(State.Menu, Event.Confirm))
        assertEquals(State.Menu, step(State.Menu, Event.Succeeded("x")))
        assertEquals(State.Menu, step(State.Menu, Event.Back))
        assertEquals(State.ConvertDirection, step(State.ConvertDirection, Event.AmountEntered(5)))
        assertEquals(State.Menu, step(State.Menu, Event.FailedWith(net, true)))
        assertTrue(n == 0)
    }

    @Test fun failedInTheMiddleOfTheMenuOpensNothing() {
        // un refus tardif (réponse d'une opération déjà quittée) ne rouvre pas un écran
        assertEquals(State.Done("ok"), step(State.Done("ok"), Event.FailedWith(net, true)))
    }
}
