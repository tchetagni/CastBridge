package castbridge.receiver.wallet

import android.content.SharedPreferences
import castbridge.core.chess.online.ChessStakeStore
import castbridge.core.chess.online.ChessWallet
import castbridge.core.chess.online.PendingEscrow
import castbridge.core.chess.online.SavedSeat
import castbridge.core.net.JsonLite
import castbridge.core.quiz.online.StakeSpec
import castbridge.core.wallet.WalletCurrency
import castbridge.core.wallet.ui.EscrowDone
import castbridge.core.wallet.ui.SettleDone
import castbridge.core.wallet.ui.WalletResult

/**
 * Ce que les parties MISÉES de la TV partagent (échecs en ligne et Quiz en ligne, games-G5) : le pont vers le portefeuille ([WalletHub]) et la mémoire persistante d'un jeu. Aucune décision ici : tout
 * l'enchaînement d'argent est du cœur (`ChessStakeFlow`, testé par JVM) ; ces deux pièces ne font que brancher le réseau et les préférences.
 */

/** Le portefeuille vu par l'enchaînement d'une partie misée (fil de travail, bloquant) : blocage de `mise × sièges` pour un jeu, règlement d'un résultat signé. */
internal object StakeWalletBridge : ChessWallet {
    override fun lockEscrow(cur: WalletCurrency, per: Long, game: String, idem: String, seats: Int): WalletResult<EscrowDone> = WalletHub.escrowBlocking(cur, per, game, idem, seats)
    override fun settle(token: String): WalletResult<SettleDone> = WalletHub.settleBlocking(token)
}

/**
 * Blocage en attente, clés d'idempotence, siège gardé et résultats signés non réglés d'UN jeu (un fichier de préférences par jeu : les échecs et le Quiz ne se marchent jamais dessus). Des secrets de portée
 * étroite (un siège, un blocage) dans le dossier privé de l'application ; jamais journalisés. Une valeur illisible est oubliée (jamais une exception qui bloquerait l'écran). Le blocage en attente garde
 * ses sièges et son jeu (un blocage de Quiz ne sert pas à une autre mise ni à un autre jeu) ; une valeur d'avant ces champs est lue comme « un siège, échecs ».
 */
internal class StakePrefsStore(private val p: SharedPreferences) : ChessStakeStore {
    private fun obj(key: String): Map<String, Any?>? = p.getString(key, null)?.let { runCatching { JsonLite.obj(it) }.getOrNull() }
    private fun put(key: String, v: Any?) { p.edit().apply { if (v == null) remove(key) else putString(key, JsonLite.write(v)) }.apply() }

    override fun pendingEscrow(): PendingEscrow? = obj("escrow")?.let { m ->
        runCatching {
            PendingEscrow(m["cur"] as String, (m["per"] as Number).toLong(), m["cbe1"] as String, m["eid"] as String, (m["exp"] as Number).toLong(),
                seats = (m["seats"] as? Number)?.toInt() ?: 1, game = m["game"] as? String ?: "chess")
        }.getOrNull()
    }
    override fun savePendingEscrow(p: PendingEscrow?) = put("escrow", p?.let { linkedMapOf("cur" to it.cur, "per" to it.per, "cbe1" to it.cbe1, "eid" to it.eid, "exp" to it.expMs, "seats" to it.seats, "game" to it.game) })

    override fun lockKey(cur: String, per: Long): String? = p.getString("lockkey_${cur}_$per", null)
    override fun saveLockKey(cur: String, per: Long, key: String?) { p.edit().apply { if (key == null) remove("lockkey_${cur}_$per") else putString("lockkey_${cur}_$per", key) }.apply() }

    override fun savedSeat(): SavedSeat? = obj("seat")?.let { m ->
        runCatching {
            val stake = (m["stake"] as? Map<*, *>)?.let { StakeSpec(it["cur"] as String, (it["per"] as Number).toLong()) }
            SavedSeat(m["room"] as String, m["token"] as String, m["name"] as String, m["code"] as String, m["color"] as? String, stake, m["escrow"] as? String, (m["at"] as Number).toLong())
        }.getOrNull()
    }
    override fun saveSeat(s: SavedSeat?) = put("seat", s?.let {
        linkedMapOf("room" to it.roomId, "token" to it.token, "name" to it.name, "code" to it.code, "color" to it.color,
            "stake" to it.stake?.let { st -> linkedMapOf("cur" to st.cur, "per" to st.per) }, "escrow" to it.escrowId, "at" to it.savedAtMs)
    })

    override fun pendingResults(): List<String> = p.getString("results", null)?.let { s -> runCatching { (JsonLite.parse(s) as List<*>).mapNotNull { it as? String } }.getOrNull() }.orEmpty()
    override fun addResult(token: String) {
        val all = pendingResults().toMutableList()
        if (token !in all) { all += token; while (all.size > ChessStakeStore.MAX_RESULTS) all.removeAt(0) }
        put("results", all)
    }
    override fun removeResult(token: String) { val all = pendingResults().toMutableList(); all.remove(token); put("results", all.takeIf { it.isNotEmpty() }) }
}
