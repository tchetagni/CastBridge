package castbridge.core.chess.online

import castbridge.core.games.ActResult
import castbridge.core.games.GameRoom
import castbridge.core.games.RoomPlayer
import castbridge.core.games.RoomStage
import castbridge.core.quiz.Json
import java.security.SecureRandom

/**
 * La vitrine d'une partie EN LIGNE pour les téléphones du FOYER (chantier games-G2) : la TV joue sa place sur le service ; ses téléphones regardent la partie par ELLE (code à 4 chiffres + page `/chess`
 * de la TV, les mêmes routes que la salle maison) et ne parlent JAMAIS au service. La TV y dépose chaque vue que le service lui envoie ([update]) ; la vitrine la re-sert aux téléphones, SANS rien qui leur
 * permette d'agir ou d'espionner : pas de coups légaux (ils ne jouent pas : la place est celle de la TV), pas d'identifiant de salle, aucun jeton de siège, aucun blocage ni résultat signé.
 *
 * C'est une [GameRoom] SANS place de téléphone (`seatCount = 0`) : le code, les jetons des personnes, la présence, les pseudos uniques, la capacité et l'attente des changements (long-poll, Server-Sent
 * Events) sont ceux de la plateforme de jeux, servis par [castbridge.core.games.RoomHttp] (`/chess`, paramètre de coup `ply`). Un téléphone peut seulement REGARDER : toute commande est refusée
 * ([ActResult.FORBIDDEN]). Aucun fil de fond : la TV pousse les vues, [tick] ne fait rien.
 */
class OnlineChessHost(
    clock: () -> Long = { System.nanoTime() / 1_000_000 },
    random: java.util.Random = SecureRandom(),
    maxPlayers: Int = GameRoom.DEFAULT_MAX_PLAYERS,
) : GameRoom(clock, random, maxPlayers, seatCount = 0) {
    private var latest: Map<String, Any?> = emptyMap()
    private var latestAt = 0L

    /** Personne ne s'assoit : les téléphones du foyer regardent. */
    override fun isPhoneSeat(seat: Int): Boolean = false

    /** Aucun tic de fond : la vitrine n'a pas d'horloge à elle (la pendule est celle du service, corrigée à la lecture). */
    override fun tick() {}

    /** La TV dépose la vue que le service lui a envoyée (format commun des échecs, voir docs/CHESS.md § 5). */
    fun update(view: Map<String, Any?>) {
        synchronized(lock) {
            if (roomStage == RoomStage.CLOSED) return
            latest = view; latestAt = now()
            roomStage = when (view["stage"]) { "PLAYING" -> RoomStage.PLAYING; "FINISHED" -> RoomStage.FINISHED; "CLOSED" -> RoomStage.CLOSED; else -> RoomStage.LOBBY }
            changed()
        }
    }

    /** Un téléphone ne commande rien : la place en ligne est celle de la TV (télécommande). */
    override fun httpAct(token: String?, action: String, arg: String?, seq: Int?): ActResult = synchronized(lock) {
        when {
            roomStage == RoomStage.CLOSED -> ActResult.CLOSED
            player(token)?.takeIf { !it.left } == null -> ActResult.UNKNOWN_PLAYER
            else -> ActResult.FORBIDDEN
        }
    }

    /** Un spectateur n'a pas de couleur. */
    override fun joinReply(p: RoomPlayer): String = ",\"color\":null"

    override fun viewJson(token: String?): String = Json.write(view(token))

    /**
     * La vue servie à un téléphone : celle de la TV, ASSAINIE. Retirés : `legal` (il ne joue pas), `room` (identifiant de salle, rien à faire côté téléphone), `me` (ni siège ni couleur : un spectateur).
     * Gardés : position, coups, pendule (corrigée du temps écoulé depuis la dernière vue), résultat, mise (montant et cagnotte, pas de blocage).
     */
    fun view(token: String?): Map<String, Any?> = synchronized(lock) {
        val me = player(token)
        val t = now()
        val out = LinkedHashMap<String, Any?>(latest)
        out["v"] = version
        out["code"] = code
        out["me"] = me?.let { linkedMapOf("id" to it.id, "name" to it.name, "color" to null) }
        out["players"] = players().map { linkedMapOf("id" to it.id, "name" to it.name, "connected" to isConnected(it, t), "color" to null) }
        out["legal"] = emptyList<String>()
        out.remove("room")
        out["online"] = true
        if (!out.containsKey("stage")) out["stage"] = "LOBBY"
        (latest["clock"] as? Map<*, *>)?.let { c ->
            val running = c["running"] == true
            val rem = (c["remainingMs"] as? Number)?.toLong()
            val adj = LinkedHashMap<String, Any?>()
            c.forEach { (k, v) -> if (k is String) adj[k] = v }
            if (running && rem != null) adj["remainingMs"] = (rem - (t - latestAt)).coerceAtLeast(0)
            out["clock"] = adj
        }
        out
    }
}
