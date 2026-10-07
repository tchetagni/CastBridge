package castbridge.core.chess.online

import castbridge.core.net.JsonLite
import castbridge.core.quiz.online.StakeSpec
import castbridge.core.wallet.WalletCurrency
import castbridge.core.wallet.ui.EscrowDone
import castbridge.core.wallet.ui.SettleDone
import castbridge.core.wallet.ui.WalletMessages
import castbridge.core.wallet.ui.WalletResult
import java.util.Base64

/**
 * Ce que la TV demande à l'API portefeuille pour une partie misée (option B de la conception W22 § 3.3) ; implémenté côté TV par `WalletHub`, par un faux dans les tests. BLOQUANT : à appeler hors du fil de
 * l'écran. La TV ne crée jamais un jeton : elle obtient un blocage signé, puis poste le résultat signé du service. Partagé par les échecs en ligne et par le Quiz misé (games-G5).
 */
interface ChessWallet {
    /**
     * Bloque [per] [cur] par siège, pour [seats] sièges (1 aux échecs ; 1 à 8 au Quiz misé : une mise par siège, payée par le compte de la TV), pour une partie de [game], avec la clé d'idempotence [idem]
     * (rejouée telle quelle sur une coupure : l'API rend le MÊME blocage).
     */
    fun lockEscrow(cur: WalletCurrency, per: Long, game: String, idem: String, seats: Int = 1): WalletResult<EscrowDone>
    /** Poste le résultat signé `cbr1` à l'API (idempotent). */
    fun settle(token: String): WalletResult<SettleDone>
}

/**
 * Un blocage obtenu et pas encore employé par une salle : il reste valable jusqu'à [expMs] ; une TV qui échoue à ouvrir sa partie le réutilise au lieu d'en bloquer un second. Il n'est réutilisé que pour le
 * MÊME jeu, la même monnaie, la même mise et le même nombre de [seats] (sièges qui misent).
 */
data class PendingEscrow(val cur: String, val per: Long, val cbe1: String, val eid: String, val expMs: Long, val seats: Int = 1, val game: String = "chess") {
    /** Jamais le blocage signé dans un journal ni dans un message d'échec de test. */
    override fun toString() = "PendingEscrow(cur=$cur, per=$per, eid=$eid, expMs=$expMs)"
}

/** La mémoire de la TV pour les parties misées : blocage en attente, clé d'idempotence d'un essai en cours, résultats signés pas encore réglés. Persistée (préférences) côté TV, en mémoire dans les tests. */
interface ChessStakeStore {
    fun pendingEscrow(): PendingEscrow?
    fun savePendingEscrow(p: PendingEscrow?)
    /** Clé d'idempotence d'un essai de blocage de cette monnaie et de cette mise qui n'a pas reçu de réponse du serveur ; null s'il n'y en a pas. */
    fun lockKey(cur: String, per: Long): String?
    fun saveLockKey(cur: String, per: Long, key: String?)
    /** Le siège de la partie en ligne en cours (pour y revenir après une fermeture de l'application) ; null s'il n'y en a pas. */
    fun savedSeat(): SavedSeat?
    fun saveSeat(s: SavedSeat?)
    /** Résultats `cbr1` reçus et pas encore réglés (au plus [MAX_RESULTS]). */
    fun pendingResults(): List<String>
    fun addResult(token: String)
    fun removeResult(token: String)

    companion object { const val MAX_RESULTS = 8 }
}

/**
 * Le siège d'une partie en ligne en cours : de quoi la reprendre (`resume{roomId, token}`) si l'application a été fermée ; gardé [KEEP_MS] au plus (une salle finie reste consultable 5 minutes côté
 * service). Le jeton n'est qu'un secret de siège : il n'est jamais journalisé ([toString] ne le montre pas).
 */
data class SavedSeat(val roomId: String, val token: String, val name: String, val code: String, val color: String?, val stake: StakeSpec?, val escrowId: String?, val savedAtMs: Long) {
    override fun toString() = "SavedSeat(room=$roomId)"
    fun fresh(nowMs: Long) = nowMs - savedAtMs in 0..KEEP_MS
    companion object { const val KEEP_MS = 6 * 60_000L }
}

class InMemoryChessStakeStore : ChessStakeStore {
    private var seat: SavedSeat? = null
    private var escrow: PendingEscrow? = null
    private val keys = HashMap<String, String>()
    private val results = ArrayList<String>()
    @Synchronized override fun pendingEscrow() = escrow
    @Synchronized override fun savePendingEscrow(p: PendingEscrow?) { escrow = p }
    @Synchronized override fun lockKey(cur: String, per: Long) = keys["$cur:$per"]
    @Synchronized override fun saveLockKey(cur: String, per: Long, key: String?) { if (key == null) keys.remove("$cur:$per") else keys["$cur:$per"] = key }
    @Synchronized override fun savedSeat() = seat
    @Synchronized override fun saveSeat(s: SavedSeat?) { seat = s }
    @Synchronized override fun pendingResults() = results.toList()
    @Synchronized override fun addResult(token: String) { if (token !in results) { results += token; while (results.size > ChessStakeStore.MAX_RESULTS) results.removeAt(0) } }
    @Synchronized override fun removeResult(token: String) { results.remove(token) }
}

/** Ce que la TV lit d'un résultat `cbr1` pour l'AFFICHER (sans vérifier la signature : l'API la vérifie, et c'est sa réponse qui fait foi pour le solde). */
data class ChessResultSummary(val rid: String, val room: String, val kind: String, val cur: String, val per: Long, val lines: List<Line>) {
    data class Line(val eid: String, val id: String, val used: Long, val pay: Long)

    /** Le gain NET avant frais du blocage [eid] (celui de cette TV) : positif = elle gagne ; négatif = elle perd ; 0 = nulle ou interruption (mise rendue) ; null = ce blocage n'est pas dans ce résultat. */
    fun netOf(eid: String): Long? = lines.firstOrNull { it.eid == eid }?.let { if (kind == "ABORT") 0L else it.pay - it.used }
    val aborted: Boolean get() = kind == "ABORT"

    companion object {
        /** Lit un jeton `cbr1.<charge>.<signature>` ; null s'il n'a pas la forme attendue. Aucune confiance : affichage seulement. */
        fun of(token: String?): ChessResultSummary? = runCatching {
            val parts = token!!.split('.')
            if (parts.size != 3 || parts[0] != "cbr1") return null
            val m = JsonLite.obj(String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8))
            val lines = (m["lines"] as List<*>).map { r -> val l = r as List<*>; Line(l[0] as String, l[1] as String, (l[2] as Number).toLong(), (l[3] as Number).toLong()) }
            ChessResultSummary(m["rid"] as String, m["room"] as String, m["kind"] as String, m["cur"] as String, (m["per"] as Number).toLong(), lines)
        }.getOrNull()
    }
}

/**
 * L'enchaînement d'une partie misée côté TV, PUR (réseau et mémoire injectés) : bloquer la mise (ou réutiliser un blocage encore valable), la porter au service (par [ChessRelayClient]), puis régler le résultat
 * signé que le service renvoie. Règles : un blocage = une partie (un échec d'ouverture ne le consomme pas : [PendingEscrow] le garde pour la partie suivante) ; la clé d'idempotence d'un essai sans réponse du
 * serveur est rejouée telle quelle (jamais deux blocages pour un clic) ; un résultat non réglé reste gardé (hors ligne) et est reposté plus tard ; la TV ne calcule jamais un solde.
 */
class ChessStakeFlow(private val wallet: ChessWallet, private val store: ChessStakeStore, private val newKey: () -> String, private val game: String = "chess") {
    sealed class Lock {
        data class Ok(val escrow: PendingEscrow, val reused: Boolean) : Lock()
        /** [network] : injoignable (on peut réessayer) ; sinon un refus du serveur, avec son texte français. */
        data class Failed(val text: String, val network: Boolean, val reason: String?) : Lock()
    }

    /**
     * Un blocage valable de ce jeu, de cette monnaie, de cette mise et de ce nombre de sièges (au moins [MIN_LEFT_MS] de validité restante : la partie doit pouvoir s'ouvrir). Un blocage fait pour un autre
     * nombre de sièges ne sert pas : l'API a posé exactement `mise × sièges`.
     */
    fun reusable(spec: StakeSpec, nowMs: Long, seats: Int = 1): PendingEscrow? =
        store.pendingEscrow()?.takeIf { it.cur == spec.cur && it.per == spec.per && it.seats == seats && it.game == game && it.expMs - nowMs >= MIN_LEFT_MS }

    /** Bloque la mise de [seats] sièges (1 aux échecs) ou réutilise un blocage encore valable pour exactement cela. */
    fun lock(spec: StakeSpec, nowMs: Long, seats: Int = 1): Lock {
        reusable(spec, nowMs, seats)?.let { return Lock.Ok(it, reused = true) }
        val cur = WalletCurrency.values().firstOrNull { it.name == spec.cur } ?: return Lock.Failed("Monnaie inconnue", false, null)
        // la clé d'idempotence d'un essai sans réponse est gardée PAR (jeu, monnaie, sièges, mise) : rejouée telle quelle elle redonne le même blocage, jamais un contenu différent (IDEM_CONFLICT) ; les clés des échecs gardent leur forme d'avant
        val keyCur = if (game == "chess" && seats == 1) spec.cur else "$game.${spec.cur}.k$seats"
        val key = store.lockKey(keyCur, spec.per) ?: newKey().also { store.saveLockKey(keyCur, spec.per, it) }
        return when (val r = wallet.lockEscrow(cur, spec.per, game, key, seats)) {
            is WalletResult.Ok -> {
                store.saveLockKey(keyCur, spec.per, null)
                val p = PendingEscrow(spec.cur, spec.per, r.value.cbe1, r.value.eid, r.value.exp, seats, game)
                store.savePendingEscrow(p)
                Lock.Ok(p, reused = false)
            }
            is WalletResult.Fail -> {
                if (!r.network) store.saveLockKey(keyCur, spec.per, null)   // un refus du serveur est définitif pour cet essai ; sans réponse, la MÊME clé sera rejouée
                Lock.Failed(r.shown.text, r.network, r.reason)
            }
        }
    }

    /** Le service a assis la TV avec ce blocage : il est employé, il ne se réutilise plus. */
    fun escrowUsed() { store.savePendingEscrow(null) }

    /** Le service a refusé l'ouverture : le blocage n'est pas consommé, il reste en attente pour la prochaine partie (aucune action). */
    fun openingFailed() {}

    /** Le service dit que ce blocage ne vaut pas pour cette partie (`STAKE_ESCROW_INVALID` : échu, déjà employé, autre TV) : on l'oublie, un nouveau sera demandé (l'ancien sera rendu par l'API à son échéance). */
    fun escrowRejected() { store.savePendingEscrow(null) }

    sealed class Settled {
        data class Done(val done: SettleDone) : Settled()
        /** Pas de connexion : le résultat reste gardé, la TV le reposte plus tard. */
        object Later : Settled() { override fun toString() = "Later" }
        /** L'API refuse ce résultat (rendu à l'échéance, blocage déjà réglé…) : inutile de le renvoyer. */
        data class Refused(val text: String, val reason: String?) : Settled()
    }

    /** Un résultat signé vient d'arriver du service : on le garde puis on le règle. */
    fun onResult(token: String): Settled { store.addResult(token); return settle(token) }

    /** Règle un résultat gardé : réglé ou refusé = retiré de la mémoire ; sans réponse du serveur = gardé. */
    fun settle(token: String): Settled = when (val r = wallet.settle(token)) {
        is WalletResult.Ok -> { store.removeResult(token); Settled.Done(r.value) }
        is WalletResult.Fail -> if (r.network || r.status == null || r.status >= 500 || r.status == 429 || r.status == 403) Settled.Later else { store.removeResult(token); Settled.Refused(r.shown.text, r.reason) }
    }

    /** Reposte tous les résultats gardés (au démarrage de l'écran, après une coupure) ; rend ce qui a été réglé. */
    fun retryPending(): List<SettleDone> = store.pendingResults().mapNotNull { (settle(it) as? Settled.Done)?.done }

    /** La ligne de règlement du blocage [eid] (celui de CETTE TV) : gain net APRÈS frais (pay − fee − used), pour le texte de fin ; null si ce blocage n'y est pas. */
    fun netAfterFees(done: SettleDone, eid: String): Long? = done.lines.firstOrNull { it.eid == eid }?.let { if (done.kind == "ABORT") 0L else it.pay - it.fee - it.used }

    companion object {
        /** Un blocage dont il reste moins que cela est jugé trop court pour ouvrir une partie (le service vérifie l'échéance à l'entrée). */
        const val MIN_LEFT_MS = 2 * 60_000L

        /** Les textes d'un refus du serveur pour un blocage sont ceux de [WalletMessages]. */
        fun refusalText(status: Int, reason: String?, message: String? = null) = WalletMessages.of(status, reason, message).text
    }
}
