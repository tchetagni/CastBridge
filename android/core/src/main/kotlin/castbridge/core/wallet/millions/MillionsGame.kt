package castbridge.core.wallet.millions

import castbridge.core.quiz.Json
import castbridge.core.wallet.WalletFormats
import castbridge.core.wallet.millions.MillionsJournal.End
import castbridge.core.wallet.millions.MillionsJournal.Entry
import castbridge.core.wallet.millions.MillionsJournal.Kind

/**
 * Une partie du « Défi des 10 000 » : règles pures, sans écran, sans réseau, sans solde (conception W22 § 13.1). Distincte du Millionnaire local (`QuizRoom`) : seul le type de question est comparable.
 *
 * Règles : mise [stakeInPlay] prise au [start] ; 15 questions ; UNE erreur (ou un délai dépassé) = fin, gain 0 (aucun palier de sécurité) ; un seul 50:50 par partie, dont les deux réponses retirées sont
 * DÉSIGNÉES par le pack ([MillionsQuestion.fifty]) ; [withdraw] seulement à l'état [State.AtStop] (juste après une bonne réponse aux questions 5, 8, 10, 13) ; abandon ([forfeit]) = gain 0.
 * Cette classe ne crédite rien : [gain] est le gain AFFICHÉ « en attente » ; seul le serveur crédite, après avoir vérifié le journal ([toJournal]).
 *
 * Temps : [answer] reçoit les millisecondes écoulées depuis l'affichage (le même chiffre va dans le journal) ; [mono] (horloge monotone injectée) ne sert qu'à [elapsedSinceShown], [tvTime] (heure de la TV,
 * `TvClock`) qu'au début et à la fin du journal. Une coupure de courant : [snapshot] à chaque réponse, [restore] à la reprise : la MÊME question est reproposée (on ne peut pas en changer en coupant), le
 * 50:50 déjà utilisé le reste ; le compte à rebours de la question en cours repart (borné de toute façon à [timeSec] par le journal).
 */
class MillionsGame private constructor(
    val gameId: String, val packId: String, val ladderVersion: Long, val ladder: MillionsLadder, val timeSec: Int, private val tvTime: () -> Long, private val mono: () -> Long,
) {
    sealed class State {
        object Idle : State()
        /** La question [k] (1..15) attend sa réponse. */
        data class Asking(val k: Int) : State()
        /** Bonne réponse à la question [k], un palier : emporter le gain affiché ([withdraw]) ou continuer ([continueGame]). */
        data class AtStop(val k: Int) : State()
        data class Lost(val k: Int, val timeout: Boolean) : State()
        data class Withdrawn(val k: Int, val gain: Long) : State()
        object Won : State()
        /** Abandon pendant la question [k] : gain 0. */
        data class Forfeit(val k: Int) : State()
    }

    var state: State = State.Idle; private set
    private val log = ArrayList<Entry>()
    val entries: List<Entry> get() = log.toList()
    private var fiftyUsed = false
    private var shownQid: String? = null
    private var shown: MillionsQuestion? = null
    private var shownMono = 0L
    private var removed: List<Int> = emptyList()
    private var t0 = 0L
    private var t1 = 0L

    /** Réponses retirées par le 50:50 sur la question en cours (vide si le joker n'a pas servi sur elle). */
    val removedChoices: List<Int> get() = removed

    /** La mise est « en jeu » dès que la partie est commencée ; rien n'est jamais remboursé localement. */
    val stakeInPlay: Long get() = if (state == State.Idle) 0L else ladder.stake

    val isFinished: Boolean get() = state is State.Lost || state is State.Withdrawn || state == State.Won || state is State.Forfeit

    /** Gain AFFICHÉ (en attente de confirmation par le serveur) : 0 tant que la partie n'est pas finie, et pour une erreur, un délai dépassé ou un abandon. */
    val gain: Long get() = when (val s = state) { is State.Withdrawn -> s.gain; State.Won -> ladder.gainAfter(MillionsLadder.QUESTIONS); else -> 0L }

    val end: End? get() = when (val s = state) {
        is State.Lost -> End(if (s.timeout) Kind.TIMEOUT else Kind.WRONG, s.k)
        is State.Withdrawn -> End(Kind.WITHDRAW, s.k)
        State.Won -> End(Kind.WON, 0)
        is State.Forfeit -> End(Kind.FORFEIT, s.k)
        else -> null
    }

    /** Commence la partie (question 1). Faux si elle est déjà commencée. */
    fun start(): Boolean { if (state != State.Idle) return false; t0 = tvTime(); state = State.Asking(1); return true }

    /** Affiche [q] pour la question en cours. Faux si aucune question n'attend, ou si une AUTRE question est déjà imposée (reprise) ou affichée. Réafficher la même question ne relance pas le chrono. */
    fun show(q: MillionsQuestion): Boolean {
        if (state !is State.Asking) return false
        val imposed = shownQid
        if (imposed != null && imposed != q.qid) return false
        if (shown != null) return true
        if (removed.isNotEmpty() && removed != q.fifty) return false     // reprise : le pack présenté doit être celui du 50:50 déjà joué
        shown = q; shownQid = q.qid; shownMono = mono()
        return true
    }

    /** Millisecondes (horloge monotone) depuis l'affichage de la question en cours ; 0 si aucune. */
    fun elapsedSinceShown(): Long = if (shown == null) 0L else maxOf(0L, mono() - shownMono)

    /** Le 50:50 : une fois par partie, sur une question affichée. Rend les deux réponses retirées (désignées par le pack), ou null si refusé. */
    fun fifty(): List<Int>? {
        val q = shown ?: return null
        if (state !is State.Asking || fiftyUsed) return null
        fiftyUsed = true; removed = q.fifty
        return removed
    }

    /**
     * Répond à la question affichée : [choice] 0..3 (ou -1 : pas de réponse, seulement si [msSinceShown] dépasse le temps), [msSinceShown] ≥ 0. Plus de [timeSec] secondes = perdu, quelle que soit la réponse.
     * Une mauvaise réponse = fin, gain 0. Une bonne réponse : victoire à la 15e, [State.AtStop] à un palier, sinon question suivante. Lève [IllegalStateException] si aucune question n'est affichée.
     */
    fun answer(choice: Int, msSinceShown: Long): State {
        val q = shown; val s = state
        check(s is State.Asking && q != null) { "aucune question affichée" }
        require(choice in -1..3) { "choix 0..3" }
        require(msSinceShown in 0..MillionsJournal.MAX_MS) { "délai hors bornes" }
        val timedOut = msSinceShown > timeSec * 1000L
        require(choice != -1 || timedOut) { "« pas de réponse » seulement hors délai" }
        log += Entry(q.qid, choice, removed.isNotEmpty(), msSinceShown)
        shown = null; shownQid = null; removed = emptyList()
        state = when {
            timedOut -> State.Lost(s.k, true)
            choice != q.correct -> State.Lost(s.k, false)
            s.k == MillionsLadder.QUESTIONS -> State.Won
            s.k in ladder.stops -> State.AtStop(s.k)
            else -> State.Asking(s.k + 1)
        }
        if (isFinished) t1 = tvTime()
        return state
    }

    /** Continue après un palier. Faux ailleurs. */
    fun continueGame(): Boolean { val s = state; if (s !is State.AtStop) return false; state = State.Asking(s.k + 1); return true }

    /** Emporte le gain affiché : permis SEULEMENT juste après une bonne réponse à un palier. Faux ailleurs. */
    fun withdraw(): Boolean { val s = state; if (s !is State.AtStop) return false; state = State.Withdrawn(s.k, ladder.gainAfter(s.k)); t1 = tvTime(); return true }

    /** Abandonne pendant une question (gain 0). Faux ailleurs (à un palier : emporter ou continuer). */
    fun forfeit(): Boolean { val s = state; if (s !is State.Asking) return false; state = State.Forfeit(s.k); t1 = tvTime(); return true }

    /** Le journal de la partie FINIE (lève [IllegalStateException] sinon) ; à signer par [MillionsJournal.sign]. */
    fun toJournal(kid: String): MillionsJournal {
        val e = end ?: throw IllegalStateException("partie non terminée")
        return MillionsJournal(kid, gameId, packId, ladderVersion, entries, e, gain, t0, t1)
    }

    /** Instantané compact pour la reprise après coupure (JSON compact : `restore` refuse tout ce qui n'est pas exactement ce format). */
    fun snapshot(): String {
        val (st, k) = when (val s = state) {
            State.Idle -> "IDLE" to 0; is State.Asking -> "ASKING" to s.k; is State.AtStop -> "AT_STOP" to s.k; is State.Lost -> (if (s.timeout) "TIMEOUT" else "LOST") to s.k
            is State.Withdrawn -> "WITHDRAWN" to s.k; State.Won -> "WON" to 0; is State.Forfeit -> "FORFEIT" to s.k
        }
        return Json.write(linkedMapOf(
            "v" to 1L, "gameId" to gameId, "packId" to packId, "lv" to ladderVersion, "st" to st, "k" to k.toLong(),
            "e" to log.map { listOf(it.qid, it.choice.toLong(), if (it.fifty) 1L else 0L, it.ms) },
            "f" to if (fiftyUsed) 1L else 0L, "sq" to (shownQid ?: ""), "rm" to removed.map { it.toLong() }, "t0" to t0, "t1" to t1,
        ))
    }

    companion object {
        /** Une partie neuve (état [State.Idle]) ; [gameId] : 128 bits en 32 hexadécimaux minuscules. */
        fun create(gameId: String, packId: String, ladderVersion: Long, ladder: MillionsLadder, timeSec: Int, tvTime: () -> Long, mono: () -> Long): MillionsGame {
            require(WalletFormats.HEX32.matches(gameId) && WalletFormats.HEX32.matches(packId)) { "identifiants de 128 bits attendus" }
            require(ladder.validate() == null && timeSec in 5..120) { "échelle ou temps invalide" }
            return MillionsGame(gameId, packId, ladderVersion, ladder, timeSec, tvTime, mono)
        }

        /** Reprend une partie depuis [text] (rendu par [snapshot]) ; null si le texte n'est pas exactement ce format, ou s'il ne correspond pas à ce pack, cette échelle, ou à un état cohérent. */
        fun restore(text: String, packId: String, ladderVersion: Long, ladder: MillionsLadder, timeSec: Int, tvTime: () -> Long, mono: () -> Long): MillionsGame? = runCatching {
            if (ladder.validate() != null || timeSec !in 5..120) return null
            val m = Json.parse(text) as? Map<*, *> ?: return null
            if (Json.write(m) != text) return null
            if (m.keys != setOf("v", "gameId", "packId", "lv", "st", "k", "e", "f", "sq", "rm", "t0", "t1") || m["v"] != 1L) return null
            val gameId = (m["gameId"] as? String)?.takeIf { WalletFormats.HEX32.matches(it) } ?: return null
            if (m["packId"] != packId || m["lv"] != ladderVersion) return null
            val k = (m["k"] as? Long)?.toInt() ?: return null
            val rows = (m["e"] as? List<*>)?.map { row ->
                val r = row as? List<*> ?: return null
                if (r.size != 4) return null
                val qid = (r[0] as? String)?.takeIf { WalletFormats.ID.matches(it) } ?: return null
                val c = r[1] as? Long ?: return null; val f = r[2] as? Long ?: return null; val ms = r[3] as? Long ?: return null
                if (c !in -1..3 || f !in 0..1 || ms !in 0..MillionsJournal.MAX_MS) return null
                Entry(qid, c.toInt(), f == 1L, ms)
            } ?: return null
            val n = rows.size
            val state: State = when (m["st"]) {
                "IDLE" -> if (k == 0 && n == 0) State.Idle else return null
                "ASKING" -> if (k in 1..MillionsLadder.QUESTIONS && n == k - 1) State.Asking(k) else return null
                "AT_STOP" -> if (k in ladder.stops && n == k) State.AtStop(k) else return null
                "LOST" -> if (k in 1..MillionsLadder.QUESTIONS && n == k) State.Lost(k, false) else return null
                "TIMEOUT" -> if (k in 1..MillionsLadder.QUESTIONS && n == k) State.Lost(k, true) else return null
                "WITHDRAWN" -> if (k in ladder.stops && n == k) State.Withdrawn(k, ladder.gainAfter(k)) else return null
                "WON" -> if (k == 0 && n == MillionsLadder.QUESTIONS) State.Won else return null
                "FORFEIT" -> if (k in 1..MillionsLadder.QUESTIONS && n == k - 1) State.Forfeit(k) else return null
                else -> return null
            }
            val fiftyUsed = when (m["f"]) { 0L -> false; 1L -> true; else -> return null }
            val sq = m["sq"] as? String ?: return null
            if (sq.isNotEmpty() && (!WalletFormats.ID.matches(sq) || state !is State.Asking)) return null
            val rm = (m["rm"] as? List<*>)?.map { (it as? Long)?.toInt()?.takeIf { x -> x in 0..3 } ?: return null } ?: return null
            if (rm.isNotEmpty() && (rm.size != 2 || rm[0] == rm[1] || sq.isEmpty() || !fiftyUsed)) return null
            val spent = rows.count { it.fifty }
            if (spent > 1 || (spent == 1 && !fiftyUsed) || (spent == 0 && fiftyUsed && rm.isEmpty())) return null
            if (rows.any { it.fifty } && rm.isNotEmpty()) return null
            val t0 = (m["t0"] as? Long)?.takeIf { it >= 0 } ?: return null; val t1 = (m["t1"] as? Long)?.takeIf { it >= 0 } ?: return null
            MillionsGame(gameId, packId, ladderVersion, ladder, timeSec, tvTime, mono).also { g ->
                g.state = state; g.log += rows; g.fiftyUsed = fiftyUsed; g.shownQid = sq.ifEmpty { null }; g.removed = rm; g.t0 = t0; g.t1 = t1
            }
        }.getOrNull()
    }
}
