package castbridge.core.wallet.ui

import castbridge.core.wallet.WalletCurrency

/**
 * La machine à états de l'écran « Mes jetons » (pure, testée sans Android) : Menu -> Convertir / Envoyer / Recevoir / Historique, RETOUR d'un pas à la fois, jamais en cours d'envoi.
 * Une opération reçoit sa clé d'idempotence UNE fois, à la confirmation ; « Réessayer » après une coupure réseau renvoie la MÊME clé (le serveur ne double jamais l'opération) ; un refus du
 * serveur n'est pas réessayable. Un événement qui n'a pas de sens dans l'état courant est ignoré (réponse tardive d'un écran déjà quitté).
 */
object WalletFlow {
    sealed class Op {
        data class Convert(val dir: ConvertDir, val q: Long) : Op()
        data class Send(val cur: WalletCurrency, val code: String, val amt: Long) : Op()
    }
    data class Pending(val op: Op, val idem: String)

    sealed class State {
        object Menu : State()
        object ConvertDirection : State()
        data class ConvertAmount(val dir: ConvertDir) : State()
        data class ConvertConfirm(val dir: ConvertDir, val q: Long) : State()
        object SendCurrency : State()
        data class SendCode(val cur: WalletCurrency) : State()
        data class SendAmount(val cur: WalletCurrency, val code: String) : State()
        data class SendConfirm(val cur: WalletCurrency, val code: String, val amt: Long) : State()
        data class Sending(val pending: Pending) : State()
        data class Done(val message: String) : State()
        /** [pending] non nul : l'opération peut être renvoyée avec la même clé (coupure réseau) ; nul : refus ou chargement raté. */
        data class Failed(val shown: WalletMessages.Shown, val pending: Pending?) : State()
        object ReceiveLoading : State()
        data class ReceiveShown(val code: String, val expMs: Long) : State()
        object HistoryLoading : State()
        data class HistoryShown(val page: HistoryPage, val cached: Boolean) : State()
    }

    sealed class Event {
        data class Open(val op: WalletOp, val gate: OpGate) : Event()
        data class PickDirection(val dir: ConvertDir) : Event()
        data class PickCurrency(val cur: WalletCurrency) : Event()
        data class CodeEntered(val code: String) : Event()
        data class AmountEntered(val amount: Long) : Event()
        object Confirm : Event()
        object Back : Event()
        object Retry : Event()
        data class Succeeded(val message: String) : Event()
        data class FailedWith(val shown: WalletMessages.Shown, val retryable: Boolean) : Event()
        data class ReceiveReady(val code: String, val expMs: Long) : Event()
        data class HistoryReady(val page: HistoryPage, val cached: Boolean) : Event()
    }

    fun reduce(s: State, e: Event, newKey: () -> String): State = when (e) {
        is Event.Open -> if (s == State.Menu && e.gate.allowed) when (e.op) {
            WalletOp.CONVERT -> State.ConvertDirection
            WalletOp.SEND -> State.SendCurrency
            WalletOp.RECEIVE -> State.ReceiveLoading
            WalletOp.HISTORY -> State.HistoryLoading
        } else s
        is Event.PickDirection -> if (s == State.ConvertDirection) State.ConvertAmount(e.dir) else s
        is Event.PickCurrency -> if (s == State.SendCurrency) State.SendCode(e.cur) else s
        is Event.CodeEntered -> if (s is State.SendCode) State.SendAmount(s.cur, e.code) else s
        is Event.AmountEntered -> if (e.amount < 1) s else when (s) {
            is State.ConvertAmount -> State.ConvertConfirm(s.dir, e.amount)
            is State.SendAmount -> State.SendConfirm(s.cur, s.code, e.amount)
            else -> s
        }
        Event.Confirm -> when (s) {
            is State.ConvertConfirm -> State.Sending(Pending(Op.Convert(s.dir, s.q), newKey()))
            is State.SendConfirm -> State.Sending(Pending(Op.Send(s.cur, s.code, s.amt), newKey()))
            else -> s
        }
        Event.Back -> when (s) {
            State.Menu, State.ConvertDirection, State.SendCurrency, is State.Done, is State.Failed, State.ReceiveLoading, is State.ReceiveShown, State.HistoryLoading, is State.HistoryShown -> State.Menu
            is State.ConvertAmount -> State.ConvertDirection
            is State.ConvertConfirm -> State.ConvertAmount(s.dir)
            is State.SendCode -> State.SendCurrency
            is State.SendAmount -> State.SendCode(s.cur)
            is State.SendConfirm -> State.SendAmount(s.cur, s.code)
            is State.Sending -> s                                                   // une opération en cours ne se quitte pas : on attend sa réponse
        }
        Event.Retry -> if (s is State.Failed && s.pending != null) State.Sending(s.pending) else s
        is Event.Succeeded -> if (s is State.Sending) State.Done(e.message) else s
        is Event.FailedWith -> when (s) {
            is State.Sending -> State.Failed(e.shown, if (e.retryable) s.pending else null)
            State.ReceiveLoading, State.HistoryLoading -> State.Failed(e.shown, null)
            else -> s
        }
        is Event.ReceiveReady -> if (s == State.ReceiveLoading) State.ReceiveShown(e.code, e.expMs) else s
        is Event.HistoryReady -> if (s == State.HistoryLoading || s is State.HistoryShown) State.HistoryShown(e.page, e.cached) else s
    }
}
