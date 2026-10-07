package castbridge.receiver

import android.content.Context
import android.content.SharedPreferences
import castbridge.core.chess.ChessRelayClient
import castbridge.core.games.RoomStage
import castbridge.core.chess.online.ChessOnlineGame
import castbridge.core.chess.online.ChessOnlineGate
import castbridge.core.chess.online.ChessOnlineTile
import castbridge.core.chess.online.ChessStakeFlow
import castbridge.core.chess.online.ChessStakeStore
import castbridge.core.chess.online.ChessWallet
import castbridge.core.chess.online.OnlineChessHost
import castbridge.core.chess.online.PendingEscrow
import castbridge.core.chess.online.SavedSeat
import castbridge.core.chess.online.StakeAvailability
import castbridge.core.net.JsonLite
import castbridge.core.quiz.online.PlayRelay
import castbridge.core.quiz.online.StakeSpec
import castbridge.core.wallet.WalletCurrency
import castbridge.core.wallet.ui.EscrowDone
import castbridge.core.wallet.ui.SettleDone
import castbridge.core.wallet.ui.WalletIdem
import castbridge.core.wallet.ui.WalletResult
import castbridge.receiver.quiz.PlayHub
import castbridge.receiver.wallet.WalletHub

/**
 * Câblage Android des échecs EN LIGNE de la TV (chantier games-G2) : AUCUNE décision ici. La porte, la mise permise, l'écran de création, les textes et tout l'enchaînement d'une partie misée sont du cœur
 * (`ChessOnline.kt`, `ChessStakeFlow.kt`, `ChessOnlineGame.kt`, testés par JVM) ; ce fichier ne fait que brancher le réseau (la même liaison que le Quiz en ligne : [PlayHub.newGameSession]), le portefeuille
 * ([WalletHub]) et la mémoire persistante des préférences. Vérifié par compilation, puis sur la TV (parcours P-88).
 *
 * Seule une TV activée et connectée à Internet joue ; les téléphones du foyer REGARDENT par elle ([OnlineChessHost], routes `/chess` de la TV) et ne parlent jamais au service.
 * Aucun jeton de siège, blocage `cbe1` ni résultat `cbr1` n'est journalisé.
 */
object ChessOnlineHub {
    private const val PREFS = "castbridge_chess_online"

    /** La partie en ligne en cours (une seule), ou null. */
    @Volatile var game: ChessOnlineGame? = null; private set

    /** La vitrine des téléphones du foyer pendant la partie : les routes `/chess` de la TV la servent à la place de la salle maison. */
    fun activeHost(): OnlineChessHost? = game?.host?.takeIf { it.roomStage != RoomStage.CLOSED }

    // ------------------------------------------------------------------ la porte et les mises permises

    /**
     * La tuile « En ligne » : l'interrupteur est celui du Quiz en ligne (réglages de la TV), la capacité vient du service lui-même ([PlayHub.chessCaps]) ; sans Internet elle reste proposée si un
     * téléphone synchronisé peut en donner (relay-R1 : la TV compte comme EN LIGNE dès que son réseau est `via_relay`, le téléphone n'est qu'un tuyau).
     */
    fun tile(ctx: Context): ChessOnlineTile = ChessOnlineGate.tile(
        flagOn = PlayHub.flagOn(ctx), edition = PlayHub.edition(), hasInternet = PlayHub.hasInternet(ctx), childProfile = ParentalHub.kidHomeActive(),
        clockDoubt = ActivationCenter.clockSuspended(), caps = PlayHub.chessCaps, serviceReachable = PlayHub.serviceUp(), verifiableActivation = PlayHub.activationText() != null,
        relay = if (PlayHub.hasInternet(ctx)) PlayRelay.UNKNOWN else TvNet.relayAvailability())

    /** Le portefeuille et la politique du jeu, relus à l'ouverture de l'écran (l'API reste l'autorité de chaque blocage). */
    fun prepare(ctx: Context) {
        WalletHub.init(ctx)
        if (WalletHub.flag() && WalletHub.activated()) { WalletHub.loadPolicy { }; WalletHub.refresh(castbridge.core.wallet.ui.WalletSyncSchedule.Trigger.OPEN) }
    }

    fun availability(): StakeAvailability {
        val snap = WalletHub.snapshot()
        return StakeAvailability.of(PlayHub.edition(), PlayHub.chessCaps, walletKnown = snap != null && WalletHub.activated() && WalletHub.flag(),
            stakesN = snap?.flags?.stakesN == true, stakesM = snap?.flags?.stakesM == true, frozen = snap?.flags?.frozen == true, policy = WalletHub.policy?.games?.get("chess"))
    }

    fun balanceNdem(): Long? = WalletHub.snapshot()?.n
    fun balanceMboko(): Long? = WalletHub.snapshot()?.m

    /**
     * Une ligne d'information, sans alarme, sur ce que fait la TV pour avoir Internet : « Partie par relais : liaison lente » quand elle ne l'a que par le tuyau d'un téléphone (une position d'échecs
     * tient en 1 à 2 Ko : la partie reste jouable), ou la demande de tuyau en cours (« Demande d'Internet au téléphone… », ou pourquoi aucun téléphone ne peut) ; null sinon.
     */
    fun networkLine(): String? = PlayHub.relayLine() ?: PlayHub.requestLine()

    // ------------------------------------------------------------------ la partie

    /** Une partie neuve (la précédente est fermée) : client du service, portefeuille, mémoire persistante et vitrine des téléphones. */
    @Synchronized fun newGame(ctx: Context): ChessOnlineGame {
        closeGame()
        val app = ctx.applicationContext
        PlayHub.probe()    // le service est sondé en arrière-plan : la porte d'une partie suivante le saura
        val store = PrefsStore(app.getSharedPreferences(PREFS, Context.MODE_PRIVATE))
        val flow = ChessStakeFlow(Bridge, store, newKey = { WalletIdem.newKey() })
        val client = ChessRelayClient(newSession = { PlayHub.newGameSession(app) }, activation = { PlayHub.activationText() }, deviceHash = { PlayHub.deviceHash() },
            beat = { PlayHub.keepPipeAlive() })      // relay-R1 : la partie tient la demande de tuyau vivante tant que la TV est sans Internet
        return ChessOnlineGame(client, flow, store, OnlineChessHost(), clock = { ActivationCenter.now() }).also { game = it }
    }

    /** Le siège gardé d'une partie en cours (application fermée pendant la partie) : de quoi proposer « Reprendre », sans rien ouvrir. */
    fun savedSeat(ctx: Context): SavedSeat? =
        PrefsStore(ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)).savedSeat()?.takeIf { it.fresh(ActivationCenter.now()) }

    /** Reposte les résultats signés gardés (règlements restés en attente faute de connexion). Bloquant : fil de travail. */
    fun retryPendingSettlements(ctx: Context): Int {
        val store = PrefsStore(ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE))
        if (store.pendingResults().isEmpty()) return 0
        return ChessStakeFlow(Bridge, store, newKey = { WalletIdem.newKey() }).retryPending().size
    }

    /** Ferme la partie [g] (par défaut la courante) ; ne touche jamais une partie plus récente que celle qu'on ferme. */
    @Synchronized fun closeGame(g: ChessOnlineGame? = game) {
        g?.let { runCatching { it.close() } }
        if (g == null || game === g) game = null
    }

    // ------------------------------------------------------------------ portefeuille (fil de travail, bloquant)

    private object Bridge : ChessWallet {
        override fun lockEscrow(cur: WalletCurrency, per: Long, game: String, idem: String): WalletResult<EscrowDone> = WalletHub.escrowBlocking(cur, per, game, idem)
        override fun settle(token: String): WalletResult<SettleDone> = WalletHub.settleBlocking(token)
    }

    // ------------------------------------------------------------------ mémoire persistante (préférences privées de l'application)

    /**
     * Blocage en attente, clés d'idempotence, siège gardé et résultats signés non réglés. Des secrets de portée étroite (un siège, un blocage) dans le dossier privé de l'application ; jamais journalisés.
     * Une valeur illisible est oubliée (jamais une exception qui bloquerait l'écran).
     */
    private class PrefsStore(private val p: SharedPreferences) : ChessStakeStore {
        private fun obj(key: String): Map<String, Any?>? = p.getString(key, null)?.let { runCatching { JsonLite.obj(it) }.getOrNull() }
        private fun put(key: String, v: Any?) { p.edit().apply { if (v == null) remove(key) else putString(key, JsonLite.write(v)) }.apply() }

        override fun pendingEscrow(): PendingEscrow? = obj("escrow")?.let { m ->
            runCatching { PendingEscrow(m["cur"] as String, (m["per"] as Number).toLong(), m["cbe1"] as String, m["eid"] as String, (m["exp"] as Number).toLong()) }.getOrNull()
        }
        override fun savePendingEscrow(p: PendingEscrow?) = put("escrow", p?.let { linkedMapOf("cur" to it.cur, "per" to it.per, "cbe1" to it.cbe1, "eid" to it.eid, "exp" to it.expMs) })

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
}
