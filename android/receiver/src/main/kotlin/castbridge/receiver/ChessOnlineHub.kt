package castbridge.receiver

import android.content.Context
import castbridge.core.chess.ChessRelayClient
import castbridge.core.games.RoomStage
import castbridge.core.chess.online.ChessOnlineGame
import castbridge.core.chess.online.ChessOnlineGate
import castbridge.core.chess.online.ChessOnlineTile
import castbridge.core.chess.online.ChessStakeFlow
import castbridge.core.chess.online.OnlineChessHost
import castbridge.core.chess.online.SavedSeat
import castbridge.core.chess.online.StakeAvailability
import castbridge.core.quiz.online.PlayRelay
import castbridge.core.wallet.ui.WalletIdem
import castbridge.receiver.quiz.PlayHub
import castbridge.receiver.wallet.StakePrefsStore
import castbridge.receiver.wallet.StakeWalletBridge
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
        val store = StakePrefsStore(app.getSharedPreferences(PREFS, Context.MODE_PRIVATE))
        val flow = ChessStakeFlow(StakeWalletBridge, store, newKey = { WalletIdem.newKey() })
        val client = ChessRelayClient(newSession = { PlayHub.newGameSession(app) }, activation = { PlayHub.activationText() }, deviceHash = { PlayHub.deviceHash() },
            beat = { PlayHub.keepPipeAlive() })      // relay-R1 : la partie tient la demande de tuyau vivante tant que la TV est sans Internet
        return ChessOnlineGame(client, flow, store, OnlineChessHost(), clock = { ActivationCenter.now() }).also { game = it }
    }

    /** Le siège gardé d'une partie en cours (application fermée pendant la partie) : de quoi proposer « Reprendre », sans rien ouvrir. */
    fun savedSeat(ctx: Context): SavedSeat? =
        StakePrefsStore(ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)).savedSeat()?.takeIf { it.fresh(ActivationCenter.now()) }

    /** Reposte les résultats signés gardés (règlements restés en attente faute de connexion). Bloquant : fil de travail. */
    fun retryPendingSettlements(ctx: Context): Int {
        val store = StakePrefsStore(ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE))
        if (store.pendingResults().isEmpty()) return 0
        return ChessStakeFlow(StakeWalletBridge, store, newKey = { WalletIdem.newKey() }).retryPending().size
    }

    /** Ferme la partie [g] (par défaut la courante) ; ne touche jamais une partie plus récente que celle qu'on ferme. */
    @Synchronized fun closeGame(g: ChessOnlineGame? = game) {
        g?.let { runCatching { it.close() } }
        if (g == null || game === g) game = null
    }

    // Le pont vers le portefeuille et la mémoire persistante (préférences privées) sont PARTAGÉS avec le Quiz misé (games-G5) : `StakeWalletBridge` et `StakePrefsStore` (receiver/wallet/StakeStores.kt).
}
