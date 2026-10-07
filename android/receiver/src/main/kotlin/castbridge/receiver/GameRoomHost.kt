package castbridge.receiver

import android.util.Log
import castbridge.core.games.GameCatalog
import castbridge.core.games.GameJournal
import castbridge.core.games.GameJournalLog
import castbridge.core.games.GamesHttp
import castbridge.core.games.RoomStage
import castbridge.core.games.RulesRoom
import castbridge.core.tv.ReceiverServer
import castbridge.receiver.wallet.WalletHub
import java.util.concurrent.Executors

/**
 * La salle de jeu de cette TV (une au plus, comme [ChessHub] pour les échecs) : ouverte par [GameActivity], servie aux téléphones par les routes publiques `/jeux/<id>/…` ([GamesHttp], sans
 * PIN : code de salle et jeton de joueur) que [TvService] ajoute à `CombinedRoutes`. Garde aussi les derniers JOURNAUX de partie, signés par la clé d'installation de la TV (borné à 20, en
 * mémoire, rien de personnel et aucune main : docs/GAMES.md). Aucun réseau sortant : tout reste sur la TV et le Wi-Fi de la maison.
 */
object GameRoomHost {
    private const val TAG = "GameRoomHost"
    @Volatile var room: RulesRoom<*, *>? = null
        private set

    /** Les routes publiques `/jeux` pour le serveur HTTP de la TV (un jeu « bientôt » répond mais n'a jamais de salle). */
    val http = GamesHttp({ id -> room?.takeIf { it.rules.id == id } })

    private val journals = GameJournalLog(max = 20)
    private val signing = Executors.newSingleThreadExecutor { r -> Thread(r, "cb-game-journal").apply { isDaemon = true } }

    /** Ouvre la salle du jeu [gameId] (la précédente est fermée) ; null si le jeu n'a pas de règles (« bientôt » : rien n'est lancé). */
    @Synchronized fun open(gameId: String): RulesRoom<*, *>? {
        val r = GameCatalog.newRoom(gameId) ?: return null
        room?.close()
        r.onFinished = { signJournal(r) }
        room = r
        return r
    }

    @Synchronized fun close(r: RulesRoom<*, *>?) {
        r?.close()
        if (room === r) room = null
    }

    /** L'adresse de la page des téléphones (celle du QR code), ou null sans réseau local. */
    fun joinUrl(r: RulesRoom<*, *>): String? = TvService.localIp()?.let { "http://$it:${ReceiverServer.PORT}/jeux/${r.rules.id}?code=${r.code}" }

    /** Une partie est finie : son journal est signé de la clé d'installation de la TV et gardé (hors du fil de la salle : la clé est lue dans un fichier). */
    private fun signJournal(r: RulesRoom<*, *>) {
        signing.execute {
            runCatching {
                val signer = GameJournal.signerOf(WalletHub.installSigner() ?: return@runCatching)
                val j = r.journal(signer.keyId) ?: return@runCatching
                journals.add(GameJournalLog.Item(j.gameId, j.rulesId, j.t1, GameJournal.sign(j, signer)))
            }.onFailure { Log.w(TAG, "journal de partie non gardé (${it.javaClass.simpleName})") }      // jamais le contenu d'une partie dans le journal de la TV
        }
    }

    /** `GET /api/games/room?game=<id>` : la salle ouverte de ce jeu (code, étape, nombre de personnes, adresse de la page des téléphones), pour l'app du téléphone. */
    fun roomJson(gameId: String?): String {
        val r = room?.takeIf { it.rules.id == gameId && it.roomStage != RoomStage.CLOSED } ?: return "{\"open\":false}"
        return "{\"open\":true,\"game\":${ReceiverServer.q(r.rules.id)},\"code\":\"${r.code}\",\"stage\":\"${r.roomStage}\",\"players\":${r.players().size}," +
            "\"url\":${ReceiverServer.q(joinUrl(r) ?: "")}}"
    }

    /** `GET /api/games/journals[?id=<gameId>]` : la liste des derniers journaux (sans le jeton signé) ou, avec `id`, le jeton signé de ce journal. */
    fun journalsJson(id: String?): String {
        val items = journals.items()
        if (id != null) return items.firstOrNull { it.gameId == id }?.let { "{\"gameId\":${ReceiverServer.q(it.gameId)},\"token\":${ReceiverServer.q(it.token)}}" } ?: "{\"error\":\"journal inconnu\"}"
        return "{\"journals\":[" + items.joinToString(",") { "{\"gameId\":${ReceiverServer.q(it.gameId)},\"game\":${ReceiverServer.q(it.rulesId)},\"endedAt\":${it.endedAtMs},\"size\":${it.token.length}}" } + "]}"
    }
}
