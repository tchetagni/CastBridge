package castbridge.core.games

import java.security.SecureRandom
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/** Où en est la salle : au salon (on s'installe), en partie, partie finie, fermée. */
enum class RoomStage { LOBBY, PLAYING, FINISHED, CLOSED }

/** Réponse à une demande d'entrée dans la salle. */
enum class JoinStatus { OK, BAD_CODE, FULL, CLOSED, BAD_NAME }

/**
 * Réponse de la salle à une commande d'un joueur. [httpStatus] = le code HTTP des routes (`/jeux/<id>/api/act`, `/chess/api/act`) : OK, IGNORED et STALE (doublon ou retard : rien n'est joué,
 * le client reçoit l'état à jour) répondent 200.
 */
enum class ActResult(val httpStatus: Int) {
    OK(200), IGNORED(200), ILLEGAL(400), NOT_YOUR_TURN(409), STALE(200), FORBIDDEN(409), BAD_REQUEST(400), UNKNOWN_PLAYER(401), CLOSED(410)
}

/** Qui joue une place : la télécommande de la TV, un téléphone de la maison, ou l'ordinateur. */
enum class SeatKind(val label: String) { REMOTE("Télécommande"), PHONE("Téléphone"), AI("Ordinateur") }

/** Une personne entrée dans la salle (avec son téléphone) : son jeton est son seul secret ; il permet de REVENIR à sa place après une coupure. */
class RoomPlayer(val id: String, val token: String, var name: String) {
    var lastSeen = 0L
    var streams = 0
    var left = false
}

data class RoomJoin(val status: JoinStatus, val player: RoomPlayer? = null)

/**
 * Ce dont la couche HTTP (`RoomHttp`) a besoin d'une salle : la même pour les échecs (`/chess/…`) et pour les jeux à règles (`/jeux/<id>/…`). Les noms diffèrent exprès de ceux de
 * `ChessRoom` (`joinRoom`, `roomStage`, `httpAct`) pour que la salle d'échecs garde son API historique.
 */
interface RoomEndpoint {
    val code: String
    val maxPlayers: Int
    val roomStage: RoomStage
    val version: Long
    fun joinRoom(code: String?, name: String?, token: String? = null): RoomJoin
    fun player(token: String?): RoomPlayer?
    fun players(): List<RoomPlayer>
    fun touch(p: RoomPlayer)
    fun leave(token: String?): Boolean
    fun streamOpened(p: RoomPlayer)
    fun streamClosed(p: RoomPlayer)
    fun awaitChange(since: Long, timeoutMs: Long): Long
    /** L'état au format commun (docs/GAMES.md, docs/CHESS.md § 5.1) tel que le voit le porteur de [token] ; null = la TV elle-même. */
    fun viewJson(token: String?): String
    fun httpAct(token: String?, action: String, arg: String?, seq: Int?): ActResult
    /** Membres JSON ajoutés (après une virgule) à la réponse d'entrée : `,"color":"w"`, `,"seat":0`… */
    fun joinReply(p: RoomPlayer): String
}

/**
 * LA SALLE « MAISON » (la TV héberge, comme celle du Quiz) : code à 4 chiffres + QR pour entrer depuis un téléphone, un JETON aléatoire par personne (le code ne sert qu'à entrer ; le
 * jeton fait REVENIR à sa place après une coupure), des spectateurs, [maxPlayers] personnes au plus (10 par défaut), des places qu'on peut changer avant la partie.
 *
 * C'est le socle COMMUN de toutes les salles de la TV : `ChessRoom` (échecs, moteur à lui) et `RulesRoom` (tout jeu écrit comme un [GameRules]) en héritent ; aucun jeu n'écrit sa propre
 * salle. Ce qui est propre à un jeu (qui joue quelle place, les règles, l'horloge, la vue) reste dans la sous-classe.
 *
 * Thread-safe : les fils HTTP, celui de l'écran de la TV, celui de l'ordinateur et le tic de fond passent tous par [lock] ; chaque changement incrémente [version] et réveille ceux qui
 * attendent (long-poll, Server-Sent Events). L'autorité est ici : un client n'envoie jamais que des coups, jamais un temps ni un état.
 *
 * L'ordre de consommation de [random] est FIGÉ (le code d'abord, puis un jeton par arrivant, puis ce que la sous-classe tire) : les tests à graine en dépendent.
 */
abstract class GameRoom(
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    protected val random: java.util.Random = SecureRandom(),
    final override val maxPlayers: Int = DEFAULT_MAX_PLAYERS,
    seatCount: Int,
) : RoomEndpoint {
    val lock = Object()
    final override val code: String = "%04d".format(random.nextInt(10_000))
    @Volatile final override var version = 1L; private set
    final override var roomStage = RoomStage.LOBBY; protected set
    @Volatile var onChange: (() -> Unit)? = null

    private val roster = LinkedHashMap<String, RoomPlayer>()
    private val byToken = HashMap<String, RoomPlayer>()
    private var nextId = 1
    /** Pour chaque place, l'identifiant de la personne qui s'y est assise (téléphone) ou null. */
    private val seated = ArrayList<String?>().also { l -> repeat(seatCount) { l += null } }
    private var ticker: ScheduledExecutorService? = null

    fun now() = clock()

    // ---------------------------------------------------------------- à fournir par le jeu

    /** La place [seat] est-elle jouée par un téléphone (donc à prendre par une personne qui entre) ? */
    protected abstract fun isPhoneSeat(seat: Int): Boolean

    /** Le tic de fond (toutes les 200 ms) : horloge, déconnexions, ordinateur. Aussi appelé directement par les tests avec une horloge fausse. */
    abstract fun tick()

    /** Appelé au début de chaque demande d'une personne (entrée, présence) : la salle peut mettre sa pendule à jour avec l'ancien dernier signe de vie AVANT qu'il soit remplacé. */
    protected open fun beforeRequest() {}

    /** Appelé à la fin de chaque demande d'une personne, une fois son signe de vie enregistré : la salle peut dire à son moteur que cette personne est (de nouveau) là. */
    protected open fun afterRequest() {}

    // ---------------------------------------------------------------- personnes

    /**
     * Entre dans la salle. Avec le jeton d'une personne déjà venue : la même personne, la même place (reprise après coupure). Sinon un pseudo est exigé (nettoyé, rendu unique) ; la salle
     * peut être pleine ; un nouvel arrivant prend la première place de téléphone libre tant qu'aucune partie ne tourne, sinon il regarde.
     */
    override fun joinRoom(code: String?, name: String?, token: String?): RoomJoin = synchronized(lock) {
        if (roomStage == RoomStage.CLOSED) return RoomJoin(JoinStatus.CLOSED)
        if (code?.trim() != this.code) return RoomJoin(JoinStatus.BAD_CODE)
        beforeRequest()
        token?.let { t -> byToken[t]?.let { p ->
            p.left = false; p.lastSeen = now(); cleanName(name)?.let { n -> if (n != p.name) p.name = unique(n, p) }
            afterRequest(); changed(); return RoomJoin(JoinStatus.OK, p)
        } }
        val n = cleanName(name) ?: return RoomJoin(JoinStatus.BAD_NAME)
        if (roster.values.count { !it.left } >= maxPlayers) return RoomJoin(JoinStatus.FULL)
        val p = RoomPlayer("p${nextId++}", newToken(), unique(n, null)).also { it.lastSeen = now() }
        roster[p.id] = p; byToken[p.token] = p
        autoSeat(p)
        afterRequest(); changed()
        RoomJoin(JoinStatus.OK, p)
    }

    /** Un arrivant prend la première place de téléphone libre tant qu'aucune partie ne tourne ; sinon il regarde. */
    protected fun autoSeat(p: RoomPlayer) {
        if (roomStage == RoomStage.PLAYING) return
        for (i in seated.indices) if (isPhoneSeat(i) && seated[i] == null) { seated[i] = p.id; return }
    }

    override fun player(token: String?): RoomPlayer? = token?.let { synchronized(lock) { byToken[it] } }
    override fun players(): List<RoomPlayer> = synchronized(lock) { roster.values.filter { !it.left } }
    /** Les personnes présentes (pas parties), pour les vues. À appeler sous [lock]. */
    protected fun activePlayers(): List<RoomPlayer> = roster.values.filter { !it.left }
    protected fun playerById(id: String?): RoomPlayer? = id?.let { roster[it] }

    /** Est-elle là ? Un flux ouvert, ou un signe de vie il y a moins de [PRESENCE_MS]. */
    fun isConnected(p: RoomPlayer, now: Long = now()) = !p.left && (p.streams > 0 || now - p.lastSeen < PRESENCE_MS)
    override fun touch(p: RoomPlayer) = synchronized(lock) { beforeRequest(); p.lastSeen = now(); afterRequest() }
    override fun streamOpened(p: RoomPlayer) = synchronized(lock) { beforeRequest(); p.streams++; p.lastSeen = now(); afterRequest(); changed() }
    override fun streamClosed(p: RoomPlayer) = synchronized(lock) { p.streams = maxOf(0, p.streams - 1); p.lastSeen = now(); changed() }

    /** Quitte la salle : la place est libérée avant la partie, gardée (au jeton) pendant la partie. */
    override fun leave(token: String?): Boolean = synchronized(lock) {
        val p = token?.let { byToken[it] } ?: return false
        p.left = true
        if (roomStage != RoomStage.PLAYING) for (i in seated.indices) if (seated[i] == p.id) seated[i] = null
        changed(); true
    }

    // ---------------------------------------------------------------- places

    val seatCount: Int get() = synchronized(lock) { seated.size }

    /** La place de [p] (index à partir de 0), ou null s'il regarde. */
    fun seatOf(p: RoomPlayer?): Int? = p?.let { pl -> seated.indices.firstOrNull { isPhoneSeat(it) && seated[it] == pl.id } }

    /** La personne assise à la place [seat] (null si libre). À appeler sous [lock]. */
    protected fun holder(seat: Int): RoomPlayer? = seated.getOrNull(seat)?.let { roster[it] }

    /** Nombre de personnes présentes sans place. */
    protected fun spectatorCount(): Int = roster.values.count { !it.left && seatOf(it) == null }

    /** Change le nombre de places ; celles qui disparaissent renvoient leur occupant parmi les spectateurs. */
    protected fun resizeSeats(n: Int) {
        while (seated.size > n) seated.removeAt(seated.size - 1)
        while (seated.size < n) seated.add(null)
    }

    /** Après un changement de réglages : les places qui ne sont plus des places de téléphone sont vidées, puis les personnes déjà là sans place s'assoient. */
    protected fun reseat() {
        for (i in seated.indices) if (!isPhoneSeat(i)) seated[i] = null
        roster.values.filter { !it.left && seatOf(it) == null }.forEach { autoSeat(it) }
    }

    /**
     * [p] prend la place [seat] (avant la partie seulement) : refusé si ce n'est pas une place de téléphone, ou si elle est tenue par quelqu'un d'autre encore présent ; il quitte sa
     * place précédente.
     */
    protected fun sitAt(p: RoomPlayer, seat: Int): Boolean {
        if (roomStage == RoomStage.PLAYING || seat !in seated.indices || !isPhoneSeat(seat)) return false
        val h = holder(seat)
        if (h != null && h !== p && isConnected(h)) return false
        for (k in seated.indices) if (seated[k] == p.id) seated[k] = null
        seated[seat] = p.id; changed()
        return true
    }

    /** [p] se lève (avant la partie seulement) et regarde. */
    protected fun standUp(p: RoomPlayer): Boolean {
        if (roomStage == RoomStage.PLAYING) return false
        for (k in seated.indices) if (seated[k] == p.id) seated[k] = null
        changed()
        return true
    }

    /** Échange les occupants de deux places. */
    protected fun swapSeated(a: Int, b: Int) { val t = seated[a]; seated[a] = seated[b]; seated[b] = t }

    /** Les places de téléphone sans personne, ou dont la personne n'est plus là (pour dire ce qu'on attend). */
    protected fun missingSeats(t: Long = now()): List<Int> =
        seated.indices.filter { isPhoneSeat(it) && (seated[it] == null || roster[seated[it]]?.let { p -> isConnected(p, t) } != true) }

    /** Retour au salon : ceux qui sont partis sont oubliés, leurs places libérées. */
    protected fun pruneLeft() {
        roster.values.removeAll { it.left }; byToken.values.removeAll { it.left }
        for (i in seated.indices) if (seated[i] != null && seated[i] !in roster) seated[i] = null
    }

    // ---------------------------------------------------------------- vie de la salle

    /** Ferme la salle (la TV quitte le jeu) : plus rien n'est accepté, les flux se terminent. */
    fun close() {
        synchronized(lock) {
            if (roomStage == RoomStage.CLOSED) return
            onClosing()
            roomStage = RoomStage.CLOSED; changed()
        }
        ticker?.shutdownNow()
        onClosed()
    }

    /** Sous [lock], juste avant la fermeture : arrêter ce qui tourne (ordinateur, partie). */
    protected open fun onClosing() {}
    /** Hors [lock], après la fermeture : libérer les fils du jeu. */
    protected open fun onClosed() {}

    /** Lance le tic de fond ; à appeler EN DERNIER par le constructeur de la sous-classe (le premier tic arrive après [intervalMs]). */
    protected fun startTicker(name: String, intervalMs: Long = 200) {
        ticker = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, name).apply { isDaemon = true } }.also {
            it.scheduleWithFixedDelay({ runCatching { tick() } }, intervalMs, intervalMs, TimeUnit.MILLISECONDS)
        }
    }

    // ---------------------------------------------------------------- notification des changements

    protected fun changed() {
        version++
        lock.notifyAll()
        runCatching { onChange?.invoke() }
    }

    /** Attend (au plus [timeoutMs]) que [version] dépasse [since] ou que la salle se ferme ; rend la version. */
    override fun awaitChange(since: Long, timeoutMs: Long): Long = synchronized(lock) {
        val end = System.nanoTime() / 1_000_000 + timeoutMs
        while (version <= since && roomStage != RoomStage.CLOSED) {
            val left = end - System.nanoTime() / 1_000_000
            if (left <= 0) break
            lock.wait(left)
        }
        version
    }

    // ---------------------------------------------------------------- utilitaires

    protected fun newToken(): String = ByteArray(16).also { random.nextBytes(it) }.joinToString("") { "%02x".format(it) }

    private fun unique(n: String, self: RoomPlayer?): String {
        val taken = roster.values.filter { it !== self && !it.left }.map { it.name.lowercase() }.toSet()
        if (n.lowercase() !in taken) return n
        var i = 2
        while ("$n $i".lowercase() in taken) i++
        return "$n $i"
    }

    companion object {
        const val DEFAULT_MAX_PLAYERS = 10
        /** Une personne sans flux ouvert est dite absente après ce délai sans signe de vie (le long-poll en donne un toutes les 25 s au plus). */
        const val PRESENCE_MS = 35_000L
        const val MAX_NAME = 16

        /** Le pseudo nettoyé (pas de caractère de contrôle ni de chevron, espaces réduits, 16 caractères), ou null s'il ne reste rien. */
        fun cleanName(n: String?): String? = n?.filter { !it.isISOControl() && it != '<' && it != '>' }?.trim()
            ?.replace(Regex("\\s+"), " ")?.take(MAX_NAME)?.trim()?.takeIf { it.isNotEmpty() }
    }
}
