package castbridge.core.chess.online

import castbridge.core.chess.ChessAct
import castbridge.core.chess.ChessRelayClient
import castbridge.core.chess.ChessSession
import castbridge.core.chess.ChessStakeRequired
import castbridge.core.chess.ChessTransportException
import castbridge.core.chess.ClockMode
import castbridge.core.quiz.online.PlayErrors
import castbridge.core.quiz.online.StakeSpec

/**
 * Une partie d'échecs EN LIGNE de CETTE TV (chantier games-G2) : relie le client du service ([ChessRelayClient]), la mise ([ChessStakeFlow] : blocage signé par l'API, règlement du résultat signé par le
 * service) et la vitrine des téléphones du foyer ([OnlineChessHost]). L'écran ne décide rien : il appelle [create] / [join] / [resume], lit [view], et affiche [settlement]. Toute la logique de mise est ici, PURE
 * (réseau, mémoire et horloge injectés) :
 * - une mise se bloque AVANT d'ouvrir la salle (un refus du portefeuille n'ouvre rien) ; un échec d'ouverture ne consomme pas le blocage (il resservira) ; un blocage employé est effacé ;
 * - rejoindre une salle misée : le service dit la mise ([Opened.NeedsStake]), la TV bloque la sienne puis rappelle [joinWithStake] sur la MÊME liaison (aucun nouveau ticket) ;
 * - le résultat signé reçu du service est gardé puis réglé auprès de l'API (hors ligne : gardé et reposté plus tard, le collecteur du serveur étant la voie de secours) ; l'écran dit le gain net APRÈS frais
 *   d'après la réponse de l'API, jamais d'après un calcul de la TV ;
 * - le siège ([SavedSeat]) est gardé pour revenir après une fermeture de l'application (60 s avant le forfait ; une partie finie reste consultable 5 minutes).
 * Les appels sont BLOQUANTS (réseau) : à lancer hors du fil de l'écran.
 */
class ChessOnlineGame(
    val client: ChessRelayClient,
    private val stakes: ChessStakeFlow?,
    private val store: ChessStakeStore,
    val host: OnlineChessHost? = null,
    private val clock: () -> Long = { System.currentTimeMillis() },
    /** Où se règle un résultat (réseau) : un fil à part par défaut ; les tests le font tourner sur place. */
    private val async: (() -> Unit) -> Unit = { r -> Thread(r, "chess-online-settle").apply { isDaemon = true }.start() },
) {
    sealed class Opened {
        data class Seated(val session: ChessSession, val stake: StakeSpec?, val resumed: Boolean = false) : Opened()
        /** La salle est misée et la TV n'a pas de blocage : bloquer [spec] puis [joinWithStake] (ou [abandonJoin]). */
        data class NeedsStake(val spec: StakeSpec) : Opened()
        /** [network] : Internet injoignable (réessayer plus tard) ; sinon un refus lisible. */
        data class Failed(val text: String, val network: Boolean = false) : Opened()
    }

    /** Où en est le règlement de la mise, pour l'écran de fin et « Mes jetons ». */
    sealed class Settlement {
        object None : Settlement() { override fun toString() = "None" }
        /** Résultat reçu, règlement en cours : [text] dit déjà ce qui est attendu (avant frais). */
        data class Pending(val text: String) : Settlement()
        /** Réglé par l'API : [net] = gain (+) ou perte (−) APRÈS frais ; [text] à afficher. */
        data class Done(val text: String, val net: Long, val cur: String, val aborted: Boolean) : Settlement()
        /** Pas de connexion : le résultat est gardé et sera renvoyé. */
        data class Later(val text: String) : Settlement()
        /** L'API refuse ce résultat (rendu à l'échéance, déjà réglé autrement) : plus rien à renvoyer. */
        data class Refused(val text: String) : Settlement()
    }

    @Volatile var session: ChessSession? = null; private set
    @Volatile var stake: StakeSpec? = null; private set
    @Volatile var settlement: Settlement = Settlement.None; private set
    /** Rappel (n'importe quel fil) à chaque changement de vue, de liaison ou de règlement : l'écran se redessine. */
    @Volatile var onChange: (() -> Unit)? = null
    /** Le blocage employé par CETTE TV dans la partie courante (pour retrouver sa ligne dans un résultat). */
    @Volatile private var myEscrow: String? = null
    private var finishedSeen = false

    init {
        client.onChange = { onClientChange() }
        client.onResult = { token -> async { handleResult(token) } }
    }

    private fun onClientChange() {
        val v = client.latest()
        if (v.isNotEmpty()) {
            host?.update(v)
            if (v["stage"] == "FINISHED" && !finishedSeen) {
                finishedSeen = true
                // une partie LIBRE finie n'a plus rien à reprendre ; une partie misée garde son siège jusqu'au règlement (le résultat peut être manqué)
                if (stake == null) store.saveSeat(null)
            }
        }
        onChange?.invoke()
    }

    /** La dernière vue du service (format commun des échecs). */
    fun view(): Map<String, Any?> = client.latest()

    // ------------------------------------------------------------------ ouverture

    /** Crée la partie ; [stake] non nul : bloque la mise d'abord (réutilise un blocage encore valable). */
    fun create(name: String, perMoveSeconds: Int, color: String, mode: ClockMode, stake: StakeSpec?): Opened {
        var escrow: PendingEscrow? = null
        if (stake != null) {
            val flow = stakes ?: return Opened.Failed(StakeAvailability.MSG_NO_WALLET)
            when (val l = flow.lock(stake, clock())) {
                is ChessStakeFlow.Lock.Ok -> escrow = l.escrow
                is ChessStakeFlow.Lock.Failed -> return Opened.Failed(l.text, l.network)
            }
        }
        return open(name, stake, escrow, resumed = false) { client.createGame(name, perMoveSeconds, color, mode, stake, escrow?.cbe1) }
    }

    /** Entre dans la partie du code (ou la regarde). Une salle misée répond [Opened.NeedsStake] (sauf pour un spectateur : il ne mise pas). */
    fun join(code: String, name: String, spectate: Boolean = false): Opened =
        open(name, null, null, resumed = false) { client.joinGame(code, name, null, spectate) }

    /** Rejoint la salle misée [spec] avec une mise bloquée (même liaison que [join]) ; un refus du portefeuille referme la liaison laissée ouverte. */
    fun joinWithStake(code: String, name: String, spec: StakeSpec): Opened {
        val flow = stakes ?: run { client.cancelOpening(); return Opened.Failed(StakeAvailability.MSG_NO_WALLET) }
        val lock = flow.lock(spec, clock())
        if (lock !is ChessStakeFlow.Lock.Ok) {
            client.cancelOpening()
            return Opened.Failed((lock as ChessStakeFlow.Lock.Failed).text, lock.network)
        }
        return open(name, spec, lock.escrow, resumed = false) { client.joinGame(code, name, lock.escrow.cbe1) }
    }

    /** Renonce à rejoindre une salle misée (la liaison laissée ouverte est refermée ; aucun blocage n'a été pris). */
    fun abandonJoin() { client.cancelOpening() }

    /** Revient dans la partie gardée (application fermée en cours de partie), ou null s'il n'y en a pas (ou plus). */
    fun resume(): Opened? {
        val seat = store.savedSeat()?.takeIf { it.fresh(clock()) } ?: run { store.saveSeat(null); return null }
        val r = open(seat.name, seat.stake, null, resumed = true, savedEscrow = seat.escrowId) { client.resumeGame(seat.roomId, seat.token, seat.name) }
        // la salle n'existe plus ou le siège est purgé : plus rien à reprendre ; une coupure réseau laisse le siège pour un nouvel essai
        if (r is Opened.Failed && !r.network) store.saveSeat(null)
        return r
    }

    private fun open(name: String, stake: StakeSpec?, escrow: PendingEscrow?, resumed: Boolean, savedEscrow: String? = null, call: () -> ChessSession): Opened {
        finishedSeen = false
        return try {
            val s = call()
            this.stake = stake; myEscrow = escrow?.eid ?: savedEscrow; session = s
            if (escrow != null) stakes?.escrowUsed()
            if (s.roomId.isNotEmpty() && s.token.isNotEmpty()) store.saveSeat(SavedSeat(s.roomId, s.token, name, s.code, s.color, stake, myEscrow, clock()))
            settlement = Settlement.None
            host?.update(client.latest())
            Opened.Seated(s, stake, resumed)
        } catch (e: ChessStakeRequired) {
            Opened.NeedsStake(e.spec)
        } catch (e: ChessTransportException) {
            // le blocage n'a rien coûté s'il n'a pas servi : il reste en attente pour la partie suivante ([ChessStakeFlow.reusable]) ; sauf si le service dit qu'il ne vaut pas : on l'oublie
            if (escrow != null && e.reason == "STAKE_ESCROW_INVALID") stakes?.escrowRejected()
            Opened.Failed(e.message ?: PlayErrors.GENERIC, network = e.status == 503 || e.status == 504)
        } catch (e: Exception) {
            Opened.Failed(PlayErrors.GENERIC, network = true)
        }
    }

    // ------------------------------------------------------------------ commandes (bloquantes)

    fun move(uci: String): ChessAct {
        val ply = (client.latest()["ply"] as? Number)?.toInt() ?: 0
        return client.move(session ?: throw ChessTransportException(410, "La partie n'existe plus."), uci, ply)
    }
    fun resign(): ChessAct = client.resign(session ?: throw ChessTransportException(410, "La partie n'existe plus.")).also { if (it.ok) awaitResultIfStaked() }
    fun draw(action: String): ChessAct = client.draw(session ?: throw ChessTransportException(410, "La partie n'existe plus."), action)
    /** L'hôte renonce avant l'arrivée de l'adversaire : le service rend la mise (résultat ABORT, réglé par [handleResult]). */
    fun cancel(): ChessAct = client.cancel().also { if (it.ok) awaitResultIfStaked() }

    /**
     * Le résultat signé suit l'accusé dans le même envoi (accusé, dernière position, résultat) : une TV qui s'en va aussitôt pourrait fermer la liaison avant de l'avoir reçu. Une partie MISÉE l'attend donc
     * quelques secondes ; le service en garde de toute façon une copie pour le collecteur de l'hôte (c'est la voie de secours, plus lente).
     */
    private fun awaitResultIfStaked() {
        if (stake == null) return
        val end = System.currentTimeMillis() + RESULT_WAIT_MS
        while (client.resultToken == null && System.currentTimeMillis() < end) Thread.sleep(50)
    }

    /**
     * Quitte la partie : le siège gardé est effacé SAUF si une mise attend son résultat (le règlement ne doit pas se perdre). Une partie misée tout juste finie dont le résultat signé n'est pas encore
     * arrivé l'attend quelques secondes avant de fermer la liaison (il suit la dernière position dans le même envoi) ; le service en garde de toute façon une copie pour son collecteur.
     */
    fun leave() {
        if (settlement == Settlement.None && client.latest()["stage"] == "FINISHED") awaitResultIfStaked()
        val s = session
        if (stake == null || settlement is Settlement.Done || settlement is Settlement.Refused) store.saveSeat(null)
        if (s != null) client.leave(s) else client.cancelOpening()
        host?.close()
    }

    fun close() { client.close(); host?.close() }

    // ------------------------------------------------------------------ règlement

    /** Un résultat signé arrive du service : le garder puis le régler. Public pour les reprises (résultats gardés d'une partie précédente) et les tests. */
    fun handleResult(token: String) {
        val flow = stakes ?: return
        val sum = ChessResultSummary.of(token)
        val cur = sum?.cur ?: stake?.cur.orEmpty()
        val mine = myEscrow
        val before = if (sum != null && mine != null) sum.netOf(mine) else null
        publish(Settlement.Pending(before?.let { ChessOnlineTexts.outcome(cur, it, sum?.aborted == true) + " · " + ChessOnlineTexts.SETTLING } ?: ChessOnlineTexts.SETTLING))
        when (val r = flow.onResult(token)) {
            is ChessStakeFlow.Settled.Done -> {
                val net = mine?.let { flow.netAfterFees(r.done, it) } ?: before ?: 0L
                val aborted = r.done.kind == "ABORT"
                store.saveSeat(null)
                publish(Settlement.Done(ChessOnlineTexts.outcome(r.done.cur, net, aborted) + " · " + ChessOnlineTexts.SETTLED, net, r.done.cur, aborted))
            }
            ChessStakeFlow.Settled.Later ->
                publish(Settlement.Later(before?.let { ChessOnlineTexts.outcome(cur, it, sum?.aborted == true) + " · " + ChessOnlineTexts.SETTLE_LATER } ?: ChessOnlineTexts.SETTLE_LATER))
            is ChessStakeFlow.Settled.Refused -> { store.saveSeat(null); publish(Settlement.Refused(ChessOnlineTexts.SETTLE_REFUSED + " (" + r.text + ")")) }
        }
    }

    /** Reposte les résultats gardés (ouverture de l'écran, retour de la connexion) ; rend le nombre de règlements faits. */
    fun retryPending(): Int = stakes?.retryPending()?.size ?: 0

    private fun publish(s: Settlement) { settlement = s; onChange?.invoke() }

    companion object { const val RESULT_WAIT_MS = 4_000L }
}
