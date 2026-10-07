package castbridge.core.chess.online

import castbridge.core.chess.ChessHost
import castbridge.core.chess.ChessRoom
import castbridge.core.quiz.Json
import java.security.SecureRandom

/**
 * La vitrine d'une partie EN LIGNE pour les téléphones du FOYER (chantier games-G2) : la TV joue sa place sur le service ; ses téléphones regardent la partie par ELLE (code à 4 chiffres + page `/chess`
 * de la TV, comme la salle maison [ChessRoom]) et ne parlent JAMAIS au service. La TV y dépose chaque vue que le service lui envoie ([update]) ; la vitrine la re-sert aux téléphones, SANS rien qui leur
 * permette d'agir ou d'espionner : pas de coups légaux (ils ne jouent pas : la place est celle de la TV), pas d'identifiant de salle, aucun jeton de siège, aucun blocage ni résultat signé.
 *
 * Un téléphone peut seulement REGARDER ; toute commande est refusée ([ChessRoom.Act.FORBIDDEN]). Même contrat de présence et d'attente que [ChessRoom] pour [castbridge.core.chess.ChessHttp] (SSE et long-poll).
 */
class OnlineChessHost(
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    private val random: java.util.Random = SecureRandom(),
    override val maxPlayers: Int = 10,
) : ChessHost {
    private val lock = Object()
    /** Le code à 4 chiffres que les téléphones du foyer saisissent (celui de la salle EN LIGNE reste à la TV). */
    val code: String = "%04d".format(random.nextInt(10_000))
    @Volatile override var version = 1L; private set
    override var stage = ChessRoom.Stage.LOBBY; private set
    private var latest: Map<String, Any?> = emptyMap()
    private var latestAt = 0L
    private val players = LinkedHashMap<String, ChessRoom.Player>()
    private val byToken = HashMap<String, ChessRoom.Player>()
    private var nextId = 1
    @Volatile var onChange: (() -> Unit)? = null

    /** La TV dépose la vue que le service lui a envoyée (format commun des échecs, voir docs/CHESS.md § 5). */
    fun update(view: Map<String, Any?>) {
        synchronized(lock) {
            if (stage == ChessRoom.Stage.CLOSED) return
            latest = view; latestAt = clock()
            stage = when (view["stage"]) { "PLAYING" -> ChessRoom.Stage.PLAYING; "FINISHED" -> ChessRoom.Stage.FINISHED; "CLOSED" -> ChessRoom.Stage.CLOSED; else -> ChessRoom.Stage.LOBBY }
            changed()
        }
    }

    /** La partie en ligne est finie pour cette TV : les téléphones sont prévenus (flux fermés). */
    fun close() {
        synchronized(lock) {
            if (stage == ChessRoom.Stage.CLOSED) return
            stage = ChessRoom.Stage.CLOSED; changed()
        }
    }

    override fun join(code: String?, name: String?, token: String?): ChessRoom.JoinResult = synchronized(lock) {
        if (stage == ChessRoom.Stage.CLOSED) return ChessRoom.JoinResult(ChessRoom.Join.CLOSED)
        if (code?.trim() != this.code) return ChessRoom.JoinResult(ChessRoom.Join.BAD_CODE)
        token?.let { t -> byToken[t]?.let { p ->
            p.left = false; p.lastSeen = clock(); ChessRoom.cleanName(name)?.let { n -> if (n != p.name) p.name = unique(n, p) }
            changed(); return ChessRoom.JoinResult(ChessRoom.Join.OK, p)
        } }
        val n = ChessRoom.cleanName(name) ?: return ChessRoom.JoinResult(ChessRoom.Join.BAD_NAME)
        if (players.values.count { !it.left } >= maxPlayers) return ChessRoom.JoinResult(ChessRoom.Join.FULL)
        val p = ChessRoom.Player("p${nextId++}", newToken(), unique(n, null)).also { it.lastSeen = clock() }
        players[p.id] = p; byToken[p.token] = p
        changed()
        ChessRoom.JoinResult(ChessRoom.Join.OK, p)
    }

    override fun player(token: String?): ChessRoom.Player? = token?.let { synchronized(lock) { byToken[it] } }
    override fun players(): List<ChessRoom.Player> = synchronized(lock) { players.values.filter { !it.left } }
    override fun touch(p: ChessRoom.Player) = synchronized(lock) { p.lastSeen = clock() }
    override fun streamOpened(p: ChessRoom.Player) = synchronized(lock) { p.streams++; p.lastSeen = clock(); changed() }
    override fun streamClosed(p: ChessRoom.Player) = synchronized(lock) { p.streams = maxOf(0, p.streams - 1); p.lastSeen = clock(); changed() }

    override fun leave(token: String?): Boolean = synchronized(lock) {
        val p = token?.let { byToken[it] } ?: return false
        p.left = true; changed(); true
    }

    /** Les téléphones du foyer ne tiennent aucune place : ils regardent. */
    override fun colorOf(p: ChessRoom.Player?): Int? = null

    /** Un téléphone ne commande rien : la place en ligne est celle de la TV (télécommande). */
    override fun act(token: String?, action: String, arg: String?, ply: Int?): ChessRoom.Act = synchronized(lock) {
        when {
            stage == ChessRoom.Stage.CLOSED -> ChessRoom.Act.CLOSED
            token == null || byToken[token]?.takeIf { !it.left } == null -> ChessRoom.Act.UNKNOWN_PLAYER
            else -> ChessRoom.Act.FORBIDDEN
        }
    }

    override fun viewJson(token: String?): String = Json.write(view(token))

    override fun awaitChange(since: Long, timeoutMs: Long): Long = synchronized(lock) {
        val end = System.nanoTime() / 1_000_000 + timeoutMs
        while (version <= since && stage != ChessRoom.Stage.CLOSED) {
            val left = end - System.nanoTime() / 1_000_000
            if (left <= 0) break
            lock.wait(left)
        }
        version
    }

    /**
     * La vue servie à un téléphone : celle de la TV, ASSAINIE. Retirés : `legal` (il ne joue pas), `room` (identifiant de salle, rien à faire côté téléphone), `me` (ni siège ni couleur : un spectateur).
     * Gardés : position, coups, pendule (corrigée du temps écoulé depuis la dernière vue), résultat, mise (montant et cagnotte, pas de blocage).
     */
    fun view(token: String?): Map<String, Any?> = synchronized(lock) {
        val me = token?.let { byToken[it] }
        val t = clock()
        val out = LinkedHashMap<String, Any?>(latest)
        out["v"] = version
        out["code"] = code
        out["me"] = me?.let { linkedMapOf("id" to it.id, "name" to it.name, "color" to null) }
        out["players"] = players.values.filter { !it.left }.map { linkedMapOf("id" to it.id, "name" to it.name, "connected" to (it.streams > 0 || t - it.lastSeen < ChessRoom.PRESENCE_MS), "color" to null) }
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

    private fun changed() {
        version++
        lock.notifyAll()
        runCatching { onChange?.invoke() }
    }

    private fun newToken(): String = ByteArray(16).also { random.nextBytes(it) }.joinToString("") { "%02x".format(it) }

    private fun unique(n: String, self: ChessRoom.Player?): String {
        val taken = players.values.filter { it !== self && !it.left }.map { it.name.lowercase() }.toSet()
        if (n.lowercase() !in taken) return n
        var i = 2
        while ("$n $i".lowercase() in taken) i++
        return "$n $i"
    }
}
