package castbridge.core.chess

import castbridge.core.quiz.Json
import java.security.SecureRandom
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A chess room hosted by the TV (like the quiz room): a 4-digit code and a QR code to join from a phone, a random token
 * per player (reconnection), any number of spectators. Each colour is played by the TV remote, a phone or the
 * computer ([Seat]). The TV is the referee: every move is checked by the engine and the countdown is the TV's own
 * (clients only send moves). Thread-safe: HTTP threads, the TV's UI thread, the AI thread and the ticker all go through
 * [lock]; every change bumps [version] and wakes waiters (long-poll / Server-Sent Events).
 */
class ChessRoom(
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    private val random: java.util.Random = SecureRandom(),
    val maxPlayers: Int = 10,
    autoTick: Boolean = true,
    /** Where the computer thinks (a background thread on the TV; the calling thread in tests). */
    private val aiExecutor: Executor = Executors.newSingleThreadExecutor { r -> Thread(r, "chess-ai").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 } },
    private val ai: ChessAi = ChessAi(),
) {
    enum class Seat(val label: String) { REMOTE("Télécommande"), PHONE("Téléphone"), AI("Ordinateur") }
    enum class Stage { LOBBY, PLAYING, FINISHED, CLOSED }
    enum class Join { OK, BAD_CODE, FULL, CLOSED, BAD_NAME }
    enum class Act { OK, IGNORED, ILLEGAL, NOT_YOUR_TURN, STALE, FORBIDDEN, BAD_REQUEST, UNKNOWN_PLAYER, CLOSED }

    class Player(val id: String, val token: String, var name: String) {
        var lastSeen = 0L
        var streams = 0
        var left = false
    }
    data class JoinResult(val status: Join, val player: Player? = null)

    val lock = Object()
    val code: String = "%04d".format(random.nextInt(10_000))
    @Volatile var version = 1L; private set
    var stage = Stage.LOBBY; private set
    /** Who plays each colour (index = Piece.WHITE / Piece.BLACK). */
    val seats = arrayOf(Seat.REMOTE, Seat.AI)
    /** Player id sitting on a PHONE seat, per colour. */
    private val seated = arrayOfNulls<String>(2)
    var level = 3; private set
    var perMoveSeconds = MoveTimer.DEFAULT_SECONDS; private set
    var mode = ClockMode.COMPETITION; private set
    var game: ChessGame? = null; private set
    private var gameNo = 0
    private val players = LinkedHashMap<String, Player>()
    private val byToken = HashMap<String, Player>()
    private var nextId = 1
    private var aiStop: AtomicBoolean? = null
    private var aiPly = -1
    /** Short message for all screens (« L'ordinateur accepte la nulle », « Temps écoulé : coup joué d'office »…). */
    private var notice: String? = null
    @Volatile var onChange: (() -> Unit)? = null
    private val ticker: ScheduledExecutorService? = if (!autoTick) null else
        Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "chess-tick").apply { isDaemon = true } }.also {
            it.scheduleWithFixedDelay({ runCatching { tick() } }, 200, 200, TimeUnit.MILLISECONDS)
        }

    fun now() = clock()

    // ---------------------------------------------------------------- players

    fun join(code: String?, name: String?, token: String? = null): JoinResult = synchronized(lock) {
        if (stage == Stage.CLOSED) return JoinResult(Join.CLOSED)
        if (code?.trim() != this.code) return JoinResult(Join.BAD_CODE)
        token?.let { t -> byToken[t]?.let { p ->
            p.left = false; p.lastSeen = now(); cleanName(name)?.let { n -> if (n != p.name) p.name = unique(n, p) }
            changed(); return JoinResult(Join.OK, p)
        } }
        val n = cleanName(name) ?: return JoinResult(Join.BAD_NAME)
        if (players.values.count { !it.left } >= maxPlayers) return JoinResult(Join.FULL)
        val p = Player("p${nextId++}", newToken(), unique(n, null)).also { it.lastSeen = now() }
        players[p.id] = p; byToken[p.token] = p
        autoSeat(p)
        changed()
        JoinResult(Join.OK, p)
    }

    /** A newcomer takes a free phone seat (White first) while no game is running; otherwise he watches. */
    private fun autoSeat(p: Player) {
        if (stage == Stage.PLAYING) return
        for (c in 0..1) if (seats[c] == Seat.PHONE && seated[c] == null) { seated[c] = p.id; return }
    }

    fun player(token: String?): Player? = token?.let { synchronized(lock) { byToken[it] } }
    fun players(): List<Player> = synchronized(lock) { players.values.filter { !it.left } }
    fun isConnected(p: Player, now: Long = now()) = !p.left && (p.streams > 0 || now - p.lastSeen < PRESENCE_MS)
    fun touch(p: Player) = synchronized(lock) { p.lastSeen = now() }
    fun streamOpened(p: Player) = synchronized(lock) { p.streams++; p.lastSeen = now(); changed() }
    fun streamClosed(p: Player) = synchronized(lock) { p.streams = maxOf(0, p.streams - 1); p.lastSeen = now(); changed() }

    fun leave(token: String?): Boolean = synchronized(lock) {
        val p = token?.let { byToken[it] } ?: return false
        p.left = true
        if (stage != Stage.PLAYING) for (c in 0..1) if (seated[c] == p.id) seated[c] = null
        changed(); true
    }

    /** Colour of [p] in the current seating, or null for a spectator. */
    fun colorOf(p: Player?): Int? = p?.let { pl -> (0..1).firstOrNull { seats[it] == Seat.PHONE && seated[it] == pl.id } }

    // ---------------------------------------------------------------- host (TV) settings and actions

    /**
     * Settings chosen on the TV. [perMoveSeconds] is clamped to 10..60 s. Allowed in the lobby or after a game.
     * The computer never plays both sides.
     */
    fun configure(white: Seat, black: Seat, level: Int = this.level, perMoveSeconds: Int = this.perMoveSeconds,
                  mode: ClockMode = this.mode): Boolean = synchronized(lock) {
        if (stage == Stage.PLAYING || stage == Stage.CLOSED) return false
        require(!(white == Seat.AI && black == Seat.AI)) { "l'ordinateur ne joue pas contre lui-même" }
        seats[0] = white; seats[1] = black
        for (c in 0..1) if (seats[c] != Seat.PHONE) seated[c] = null
        this.level = level.coerceIn(1, 8)
        this.perMoveSeconds = MoveTimer.clamp(perMoveSeconds)
        this.mode = mode
        // players already there fill the phone seats
        players.values.filter { !it.left && colorOf(it) == null }.forEach { autoSeat(it) }
        stage = Stage.LOBBY
        changed(); true
    }

    /** Swaps the two colours (seats and the players on them). */
    fun swapColors(): Boolean = synchronized(lock) {
        if (stage == Stage.PLAYING || stage == Stage.CLOSED) return false
        val s = seats[0]; seats[0] = seats[1]; seats[1] = s
        val p = seated[0]; seated[0] = seated[1]; seated[1] = p
        changed(); true
    }

    /** Why the game cannot start yet (plain French for the TV), or null if it can. */
    fun missing(): String? = synchronized(lock) {
        val t = now()
        val need = (0..1).filter { seats[it] == Seat.PHONE && (seated[it] == null || players[seated[it]]?.let { p -> isConnected(p, t) } != true) }
        when (need.size) {
            0 -> null
            2 -> "En attente de deux joueurs sur téléphone"
            else -> "En attente du joueur des ${colorName(need[0], plural = true)} sur téléphone"
        }
    }

    /** Starts a new game with the current settings. Returns null when started, else why not. */
    fun start(startFen: String = Position.START_FEN, force: Boolean = false): String? = synchronized(lock) {
        if (stage == Stage.PLAYING) return "Une partie est déjà en cours."
        if (stage == Stage.CLOSED) return "La salle est fermée."
        if (!force) missing()?.let { return it }
        cancelAi()
        gameNo++
        game = ChessGame(perMoveSeconds, mode, startFen, now(), random)
        notice = null
        stage = Stage.PLAYING
        changed()
        maybeStartAi()
        null
    }

    /** Back to the lobby (same code, same players) to change the settings. */
    fun backToLobby(): Boolean = synchronized(lock) {
        if (stage == Stage.CLOSED) return false
        cancelAi()
        game?.abandon()
        stage = Stage.LOBBY; notice = null
        players.values.removeAll { it.left }; byToken.values.removeAll { it.left }
        for (c in 0..1) if (seated[c] != null && seated[c] !in players) seated[c] = null
        changed(); true
    }

    fun close() {
        synchronized(lock) {
            if (stage == Stage.CLOSED) return
            cancelAi()
            game?.abandon()
            stage = Stage.CLOSED; changed()
        }
        ticker?.shutdownNow()
    }

    /** A move from the remote: only for a colour played at the TV. */
    fun hostMove(uci: String): Act = synchronized(lock) {
        val g = game ?: return Act.IGNORED
        if (stage != Stage.PLAYING) return Act.IGNORED
        if (seats[g.turn] != Seat.REMOTE) return Act.NOT_YOUR_TURN
        playFor(g, g.turn, uci, null)
    }

    /**
     * Resignation from the TV: in a game against the computer or a phone, the remote's colour resigns; with two
     * players at the TV, the side to move.
     */
    fun hostResign(): Boolean = synchronized(lock) {
        val g = game ?: return false
        val c = remoteColor(g) ?: return false
        g.resign(c).also { if (it) finish() }
    }

    /**
     * Draw from the TV. Two players at the TV: agreed at once (both are in front of the screen). Against the computer:
     * it accepts only if it stands worse. Against a phone: an offer the phone accepts or declines.
     */
    fun hostOfferDraw(): Boolean = synchronized(lock) {
        val g = game ?: return false
        if (g.over) return false
        val c = remoteColor(g) ?: return false
        val other = seats[c xor 1]
        when (other) {
            Seat.REMOTE -> { g.offerDraw(c); g.answerDraw(c xor 1, true); finish(); true }
            Seat.AI -> {
                val evalForAi = ChessAi.evaluate(g.position).let { if (g.position.side == (c xor 1)) it else -it }
                if (evalForAi <= -AI_DRAW_MARGIN || g.position.halfmove >= 40) { g.offerDraw(c); g.answerDraw(c xor 1, true); finish(); true }
                else { notice = "L'ordinateur refuse la nulle et continue la partie."; changed(); false }
            }
            Seat.PHONE -> g.offerDraw(c).also { if (it) { notice = null; changed() } }
        }
    }

    /** The remote answers a draw offered by a phone. */
    fun hostAnswerDraw(accept: Boolean): Boolean = synchronized(lock) {
        val g = game ?: return false
        val c = remoteColor(g) ?: return false
        g.answerDraw(c, accept).also { if (it) { if (g.over) finish() else changed() } }
    }

    /**
     * Takes back the last move of the remote's player against the computer (and the computer's answer), only in a
     * game against the computer. The countdown starts again.
     */
    fun hostUndo(): Boolean = synchronized(lock) {
        val g = game ?: return false
        val human = (0..1).firstOrNull { seats[it] == Seat.REMOTE } ?: return false
        if (seats[human xor 1] != Seat.AI || g.ply == 0) return false
        cancelAi()
        // back to the last position where the human was to move, one move earlier
        val plies = if (g.turn == human) 2 else 1
        if (plies > g.ply) return false
        if (!g.undo(plies, now())) return false
        stage = Stage.PLAYING; notice = "Coup annulé"
        changed(); maybeStartAi(); true
    }

    private fun remoteColor(g: ChessGame): Int? = when {
        seats[0] == Seat.REMOTE && seats[1] == Seat.REMOTE -> g.turn
        seats[0] == Seat.REMOTE -> 0
        seats[1] == Seat.REMOTE -> 1
        else -> null
    }

    // ---------------------------------------------------------------- player actions (HTTP)

    /**
     * A phone's command: move (arg = UCI, ply = the game's ply the move answers), resign, draw (arg = offer / accept /
     * decline), sit (arg = w / b: take a free phone seat before the game), stand.
     */
    fun act(token: String?, action: String, arg: String? = null, ply: Int? = null): Act = synchronized(lock) {
        val p = token?.let { byToken[it] } ?: return Act.UNKNOWN_PLAYER
        if (stage == Stage.CLOSED) return Act.CLOSED
        p.lastSeen = now()
        val color = colorOf(p)
        val g = game
        when (action) {
            "move" -> {
                if (g == null || stage != Stage.PLAYING) return Act.IGNORED
                if (color == null) return Act.FORBIDDEN
                if (arg.isNullOrBlank()) return Act.BAD_REQUEST
                playFor(g, color, arg, ply)
            }
            "resign" -> {
                if (g == null || stage != Stage.PLAYING || color == null) return Act.FORBIDDEN
                if (g.resign(color)) { finish(); Act.OK } else Act.IGNORED
            }
            "draw" -> {
                if (g == null || stage != Stage.PLAYING || color == null) return Act.FORBIDDEN
                val ok = when (arg) {
                    "offer" -> if (seats[color xor 1] == Seat.AI) { notice = "L'ordinateur refuse la nulle."; false } else g.offerDraw(color)
                    "accept" -> g.answerDraw(color, true)
                    "decline" -> g.answerDraw(color, false)
                    else -> return Act.BAD_REQUEST
                }
                if (g.over) finish() else changed()
                if (ok) Act.OK else Act.IGNORED
            }
            "sit" -> {
                if (stage == Stage.PLAYING) return Act.FORBIDDEN
                val c = when (arg) { "w" -> 0; "b" -> 1; else -> return Act.BAD_REQUEST }
                if (seats[c] != Seat.PHONE) return Act.FORBIDDEN
                val holder = seated[c]?.let { players[it] }
                if (holder != null && holder !== p && isConnected(holder)) return Act.FORBIDDEN
                for (k in 0..1) if (seated[k] == p.id) seated[k] = null
                seated[c] = p.id; changed(); Act.OK
            }
            "stand" -> {
                if (stage == Stage.PLAYING) return Act.FORBIDDEN
                for (k in 0..1) if (seated[k] == p.id) seated[k] = null
                changed(); Act.OK
            }
            else -> Act.BAD_REQUEST
        }
    }

    private fun playFor(g: ChessGame, color: Int, uci: String, ply: Int?): Act {
        val r = g.play(uci, color, now(), ply)
        return when (r) {
            ChessGame.Play.OK -> { notice = null; if (g.over) finish() else { changed(); maybeStartAi() }; Act.OK }
            ChessGame.Play.ILLEGAL -> Act.ILLEGAL
            ChessGame.Play.NOT_YOUR_TURN -> Act.NOT_YOUR_TURN
            ChessGame.Play.STALE -> { if (g.over) finish(); Act.STALE }
            ChessGame.Play.OVER -> { if (stage == Stage.PLAYING) finish(); Act.IGNORED }
        }
    }

    private fun finish() {
        cancelAi()
        stage = Stage.FINISHED
        changed()
    }

    // ---------------------------------------------------------------- computer

    private fun maybeStartAi() {
        val g = game ?: return
        if (stage != Stage.PLAYING || g.over || seats[g.turn] != Seat.AI || aiPly == g.ply) return
        val stop = AtomicBoolean(false)
        aiStop = stop; aiPly = g.ply
        val snapshot = g.position.copy()
        val ply = g.ply; val no = gameNo
        val allowance = ChessAi.allowance(g.perMoveMs)
        val lv = level
        changed()
        aiExecutor.execute {
            val r = runCatching { ai.think(snapshot, lv, allowance, stop) }.getOrNull()
            synchronized(lock) {
                if (stop.get() || no != gameNo || game !== g || g.ply != ply || stage != Stage.PLAYING) return@synchronized
                aiStop = null; aiPly = -1
                val move = r?.move?.takeIf { it != 0 }?.let(Move::uci) ?: g.position.legalMoves().firstOrNull()?.let(Move::uci)
                if (move != null) playFor(g, g.turn, move, ply) else changed()
            }
        }
    }

    private fun cancelAi() { aiStop?.set(true); aiStop = null; aiPly = -1 }

    val aiThinking: Boolean get() = synchronized(lock) { aiStop != null }

    // ---------------------------------------------------------------- time

    /** Countdown: the TV's clock decides; run every 200 ms. */
    fun tick() {
        synchronized(lock) {
            if (stage != Stage.PLAYING) return
            val g = game ?: return
            val before = g.ply
            if (g.tick(now())) {
                if (g.over) finish()
                else {
                    if (g.ply > before) notice = "Temps écoulé : un coup a été joué d'office (${g.san.last()})"
                    cancelAi(); changed(); maybeStartAi()
                }
            }
        }
    }

    // ---------------------------------------------------------------- change notification

    private fun changed() {
        version++
        lock.notifyAll()
        runCatching { onChange?.invoke() }
    }

    fun awaitChange(since: Long, timeoutMs: Long): Long = synchronized(lock) {
        val end = System.nanoTime() / 1_000_000 + timeoutMs
        while (version <= since && stage != Stage.CLOSED) {
            val left = end - System.nanoTime() / 1_000_000
            if (left <= 0) break
            lock.wait(left)
        }
        version
    }

    // ---------------------------------------------------------------- views

    private fun seatName(c: Int): String = when (seats[c]) {
        Seat.AI -> "Ordinateur · niveau $level"
        Seat.REMOTE -> if (seats[c xor 1] == Seat.REMOTE) "Joueur ${colorName(c, false)}" else "Télécommande"
        Seat.PHONE -> seated[c]?.let { players[it]?.name } ?: "Téléphone (libre)"
    }

    /** Names for the PGN and the end screen. */
    fun names(): Pair<String, String> = synchronized(lock) { seatName(0) to seatName(1) }

    /**
     * The shared state format (the same for the TV, the phones, the web page and the Internet relay: see
     * docs/CHESS.md). [token] null = the TV itself. Legal moves are only listed for whoever is to move.
     */
    fun view(token: String?): Map<String, Any?> = synchronized(lock) {
        val me = token?.let { byToken[it] }
        val t = now()
        val g = game
        val myColor = colorOf(me)
        val toMove = g?.takeIf { !it.over && stage == Stage.PLAYING }?.turn
        val mine = toMove != null && ((me == null && seats[toMove] == Seat.REMOTE) || (me != null && myColor == toMove))
        linkedMapOf(
            "v" to version,
            "protocol" to PROTOCOL,
            "stage" to stage.name,
            "code" to code,
            "settings" to linkedMapOf("perMoveSeconds" to perMoveSeconds, "mode" to mode.name, "modeLabel" to mode.label,
                "modeRule" to mode.rule, "level" to level, "white" to seats[0].name, "black" to seats[1].name),
            "white" to sideMap(0, t), "black" to sideMap(1, t),
            "me" to me?.let { linkedMapOf("id" to it.id, "name" to it.name, "color" to myColor?.let(::colorKey)) },
            "spectators" to players.values.count { !it.left && colorOf(it) == null },
            "players" to players.values.filter { !it.left }.map { linkedMapOf("id" to it.id, "name" to it.name, "connected" to isConnected(it, t), "color" to colorOf(it)?.let(::colorKey)) },
            "fen" to (g?.position?.fen() ?: Position.START_FEN),
            "startFen" to (g?.startFen ?: Position.START_FEN),
            "ply" to (g?.ply ?: 0),
            "turn" to colorKey(g?.turn ?: 0),
            "check" to (g?.position?.inCheck() == true),
            "san" to (g?.san?.toList() ?: emptyList<String>()),
            "uci" to (g?.uci?.toList() ?: emptyList<String>()),
            "lastMove" to g?.lastMove(),
            "auto" to (g?.autoPlayed?.sorted() ?: emptyList<Int>()),
            "legal" to (if (mine) g!!.position.legalMoves().map(Move::uci) else emptyList<String>()),
            "clock" to linkedMapOf("perMoveMs" to (g?.perMoveMs ?: perMoveSeconds * 1000L),
                "remainingMs" to (if (g != null && stage == Stage.PLAYING) g.remainingMs(t) else null),
                "running" to (g != null && stage == Stage.PLAYING && !g.over), "turn" to colorKey(g?.turn ?: 0)),
            "drawOffer" to g?.drawOffer?.let(::colorKey),
            "thinking" to (aiStop != null),
            "notice" to notice,
            "result" to g?.result?.let { r -> linkedMapOf("winner" to r.winner?.let(::colorKey), "reason" to r.reason.name, "text" to r.text, "pgn" to r.pgn) },
            "pgn" to (if (g != null && g.over) g.pgn(seatName(0), seatName(1)) else null),
        )
    }

    private fun sideMap(c: Int, t: Long): Map<String, Any?> {
        val p = seated[c]?.let { players[it] }
        return linkedMapOf("kind" to seats[c].name, "name" to seatName(c), "connected" to when (seats[c]) {
            Seat.PHONE -> p?.let { isConnected(it, t) } ?: false
            else -> true
        }, "level" to (if (seats[c] == Seat.AI) level else null), "playerId" to p?.id)
    }

    fun viewJson(token: String?): String = Json.write(view(token))

    // ---------------------------------------------------------------- helpers

    private fun newToken(): String = ByteArray(16).also { random.nextBytes(it) }.joinToString("") { "%02x".format(it) }

    private fun unique(n: String, self: Player?): String {
        val taken = players.values.filter { it !== self && !it.left }.map { it.name.lowercase() }.toSet()
        if (n.lowercase() !in taken) return n
        var i = 2
        while ("$n $i".lowercase() in taken) i++
        return "$n $i"
    }

    companion object {
        const val PROTOCOL = 1
        const val PRESENCE_MS = 35_000L
        const val MAX_NAME = 16
        /** The computer accepts a draw when it stands at least this much worse (centipawns). */
        const val AI_DRAW_MARGIN = 50

        fun colorKey(c: Int) = if (c == Piece.WHITE) "w" else "b"
        fun colorName(c: Int, plural: Boolean) = if (c == Piece.WHITE) (if (plural) "Blancs" else "Blancs") else (if (plural) "Noirs" else "Noirs")

        fun cleanName(n: String?): String? = n?.filter { !it.isISOControl() && it != '<' && it != '>' }?.trim()
            ?.replace(Regex("\\s+"), " ")?.take(MAX_NAME)?.trim()?.takeIf { it.isNotEmpty() }
    }
}
