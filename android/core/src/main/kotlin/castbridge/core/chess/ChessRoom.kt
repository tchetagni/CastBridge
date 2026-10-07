package castbridge.core.chess

import castbridge.core.games.ActResult
import castbridge.core.games.GameRoom
import castbridge.core.games.RoomPlayer
import castbridge.core.games.RoomStage
import castbridge.core.quiz.Json
import java.security.SecureRandom
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A chess room hosted by the TV (like the quiz room): a 4-digit code and a QR code to join from a phone, a random token
 * per player (reconnection), any number of spectators. Each colour is played by the TV remote, a phone or the
 * computer ([Seat]). The TV is the referee: every move is checked by the engine and the countdown is the TV's own
 * (clients only send moves). Thread-safe: HTTP threads, the TV's UI thread, the AI thread and the ticker all go through
 * [lock]; every change bumps [version] and wakes waiters (long-poll / Server-Sent Events).
 *
 * Since the games platform (docs/GAMES.md) the room machinery (code, tokens, seats, presence, spectators, change notification,
 * ticker, close) is [GameRoom], shared with every game; this class keeps what is chess: the colours, the engine, the countdown,
 * draw offers, the computer and the chess view. Its API and behaviour are unchanged (`ChessGoldenTest` pins them byte for byte):
 * [Stage], [Join] and [Act] are a compatibility facade over the generic `RoomStage`, `JoinStatus` and `ActResult` (same names,
 * pinned equal by `GameRoomFacadeTest`), to be dropped when chess becomes a `GameRules` (chantier games-G2).
 */
class ChessRoom(
    clock: () -> Long = { System.nanoTime() / 1_000_000 },
    random: java.util.Random = SecureRandom(),
    maxPlayers: Int = 10,
    autoTick: Boolean = true,
    /** Where the computer thinks (a background thread on the TV; the calling thread in tests). */
    private val aiExecutor: Executor = Executors.newSingleThreadExecutor { r -> Thread(r, "chess-ai").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 } },
    private val ai: ChessAi = ChessAi(),
) : GameRoom(clock, random, maxPlayers, seatCount = 2) {
    enum class Seat(val label: String) { REMOTE("Télécommande"), PHONE("Téléphone"), AI("Ordinateur") }
    enum class Stage { LOBBY, PLAYING, FINISHED, CLOSED }
    enum class Join { OK, BAD_CODE, FULL, CLOSED, BAD_NAME }
    enum class Act { OK, IGNORED, ILLEGAL, NOT_YOUR_TURN, STALE, FORBIDDEN, BAD_REQUEST, UNKNOWN_PLAYER, CLOSED }

    data class JoinResult(val status: Join, val player: RoomPlayer? = null)

    /** The room's stage in the chess vocabulary (the generic one is [roomStage]). */
    val stage: Stage get() = Stage.valueOf(roomStage.name)
    /** Who plays each colour (index = Piece.WHITE / Piece.BLACK). */
    val seats = arrayOf(Seat.REMOTE, Seat.AI)
    var level = 3; private set
    var perMoveSeconds = MoveTimer.DEFAULT_SECONDS; private set
    var mode = ClockMode.COMPETITION; private set
    var game: ChessGame? = null; private set
    private var gameNo = 0
    private var aiStop: AtomicBoolean? = null
    private var aiPly = -1
    /** Short message for all screens (« L'ordinateur accepte la nulle », « Temps écoulé : coup joué d'office »…). */
    private var notice: String? = null

    init { if (autoTick) startTicker("chess-tick") }

    override fun isPhoneSeat(seat: Int): Boolean = seats.getOrNull(seat) == Seat.PHONE

    // ---------------------------------------------------------------- players

    fun join(code: String?, name: String?, token: String? = null): JoinResult = joinRoom(code, name, token).let { JoinResult(Join.valueOf(it.status.name), it.player) }

    /** Colour of [p] in the current seating, or null for a spectator. */
    fun colorOf(p: RoomPlayer?): Int? = seatOf(p)

    // ---------------------------------------------------------------- host (TV) settings and actions

    /**
     * Settings chosen on the TV. [perMoveSeconds] is clamped to 10..60 s. Allowed in the lobby or after a game.
     * The computer never plays both sides.
     */
    fun configure(white: Seat, black: Seat, level: Int = this.level, perMoveSeconds: Int = this.perMoveSeconds,
                  mode: ClockMode = this.mode): Boolean = synchronized(lock) {
        if (roomStage == RoomStage.PLAYING || roomStage == RoomStage.CLOSED) return false
        require(!(white == Seat.AI && black == Seat.AI)) { "l'ordinateur ne joue pas contre lui-même" }
        seats[0] = white; seats[1] = black
        this.level = level.coerceIn(1, 8)
        this.perMoveSeconds = MoveTimer.clamp(perMoveSeconds)
        this.mode = mode
        reseat()            // the phone seats keep (or take) their players; the others are emptied
        roomStage = RoomStage.LOBBY
        changed(); true
    }

    /** Swaps the two colours (seats and the players on them). */
    fun swapColors(): Boolean = synchronized(lock) {
        if (roomStage == RoomStage.PLAYING || roomStage == RoomStage.CLOSED) return false
        val s = seats[0]; seats[0] = seats[1]; seats[1] = s
        swapSeated(0, 1)
        changed(); true
    }

    /** Why the game cannot start yet (plain French for the TV), or null if it can. */
    fun missing(): String? = synchronized(lock) {
        val need = missingSeats(now())
        when (need.size) {
            0 -> null
            2 -> "En attente de deux joueurs sur téléphone"
            else -> "En attente du joueur des ${colorName(need[0], plural = true)} sur téléphone"
        }
    }

    /** Starts a new game with the current settings. Returns null when started, else why not. */
    fun start(startFen: String = Position.START_FEN, force: Boolean = false): String? = synchronized(lock) {
        if (roomStage == RoomStage.PLAYING) return "Une partie est déjà en cours."
        if (roomStage == RoomStage.CLOSED) return "La salle est fermée."
        if (!force) missing()?.let { return it }
        cancelAi()
        gameNo++
        game = ChessGame(perMoveSeconds, mode, startFen, now(), random)
        notice = null
        roomStage = RoomStage.PLAYING
        changed()
        maybeStartAi()
        null
    }

    /** Back to the lobby (same code, same players) to change the settings. */
    fun backToLobby(): Boolean = synchronized(lock) {
        if (roomStage == RoomStage.CLOSED) return false
        cancelAi()
        game?.abandon()
        roomStage = RoomStage.LOBBY; notice = null
        pruneLeft()
        changed(); true
    }

    override fun onClosing() { cancelAi(); game?.abandon() }
    override fun onClosed() { (aiExecutor as? java.util.concurrent.ExecutorService)?.shutdownNow() }

    /** A move from the remote: only for a colour played at the TV. */
    fun hostMove(uci: String): Act = synchronized(lock) {
        val g = game ?: return Act.IGNORED
        if (roomStage != RoomStage.PLAYING) return Act.IGNORED
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
        roomStage = RoomStage.PLAYING; notice = "Coup annulé"
        changed(); maybeStartAi(); true
    }

    /** The clock may stop only when nobody plays from a phone (a remote opponent is never kept waiting). */
    val canPause: Boolean get() = seats.none { it == Seat.PHONE }

    /** Pause menu of the TV: stops (true) or restarts (false) the countdown of a game played only at the TV. */
    fun hostPause(pause: Boolean): Boolean = synchronized(lock) {
        val g = game ?: return false
        if (roomStage != RoomStage.PLAYING || !canPause) return false
        val t = now()
        (if (pause) g.pause(t) else g.resume(t)).also { if (it) changed() }
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
        val p = player(token) ?: return Act.UNKNOWN_PLAYER
        if (roomStage == RoomStage.CLOSED) return Act.CLOSED
        p.lastSeen = now()
        val color = colorOf(p)
        val g = game
        when (action) {
            "move" -> {
                if (g == null || roomStage != RoomStage.PLAYING) return Act.IGNORED
                if (color == null) return Act.FORBIDDEN
                if (arg.isNullOrBlank()) return Act.BAD_REQUEST
                playFor(g, color, arg, ply)
            }
            "resign" -> {
                if (g == null || roomStage != RoomStage.PLAYING || color == null) return Act.FORBIDDEN
                if (g.resign(color)) { finish(); Act.OK } else Act.IGNORED
            }
            "draw" -> {
                if (g == null || roomStage != RoomStage.PLAYING || color == null) return Act.FORBIDDEN
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
                if (roomStage == RoomStage.PLAYING) return Act.FORBIDDEN
                val c = when (arg) { "w" -> 0; "b" -> 1; else -> return Act.BAD_REQUEST }
                if (seats[c] != Seat.PHONE) return Act.FORBIDDEN
                if (!sitAt(p, c)) return Act.FORBIDDEN
                Act.OK
            }
            "stand" -> {
                if (!standUp(p)) return Act.FORBIDDEN
                Act.OK
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
            ChessGame.Play.OVER -> { if (roomStage == RoomStage.PLAYING) finish(); Act.IGNORED }
        }
    }

    private fun finish() {
        cancelAi()
        roomStage = RoomStage.FINISHED
        changed()
    }

    override fun httpAct(token: String?, action: String, arg: String?, seq: Int?): ActResult = ActResult.valueOf(act(token, action, arg, seq).name)
    override fun joinReply(p: RoomPlayer): String = ",\"color\":" + (synchronized(lock) { colorOf(p) }?.let { "\"${colorKey(it)}\"" } ?: "null")

    // ---------------------------------------------------------------- computer

    private fun maybeStartAi() {
        val g = game ?: return
        if (roomStage != RoomStage.PLAYING || g.over || seats[g.turn] != Seat.AI || aiPly == g.ply) return
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
                if (stop.get() || no != gameNo || game !== g || g.ply != ply || roomStage != RoomStage.PLAYING) return@synchronized
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
    override fun tick() {
        synchronized(lock) {
            if (roomStage != RoomStage.PLAYING) return
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

    // ---------------------------------------------------------------- views

    private fun seatName(c: Int): String = when (seats[c]) {
        Seat.AI -> "Ordinateur · niveau $level"
        Seat.REMOTE -> when (seats[c xor 1]) {
            Seat.REMOTE -> "Joueur des ${colorName(c, false)}"
            Seat.AI -> "Vous"
            Seat.PHONE -> "Joueur de la TV"
        }
        Seat.PHONE -> holder(c)?.name ?: "Téléphone (libre)"
    }

    /** Names for the PGN and the end screen. */
    fun names(): Pair<String, String> = synchronized(lock) { seatName(0) to seatName(1) }

    /**
     * The shared state format (the same for the TV, the phones, the web page and the Internet relay: see
     * docs/CHESS.md). [token] null = the TV itself. Legal moves are only listed for whoever is to move.
     */
    fun view(token: String?): Map<String, Any?> = synchronized(lock) {
        val me = player(token)
        val t = now()
        val g = game
        val myColor = colorOf(me)
        val toMove = g?.takeIf { !it.over && roomStage == RoomStage.PLAYING }?.turn
        val mine = toMove != null && ((me == null && seats[toMove] == Seat.REMOTE) || (me != null && myColor == toMove))
        linkedMapOf(
            "v" to version,
            "protocol" to PROTOCOL,
            "stage" to roomStage.name,
            "code" to code,
            "settings" to linkedMapOf("perMoveSeconds" to perMoveSeconds, "mode" to mode.name, "modeLabel" to mode.label,
                "modeRule" to mode.rule, "level" to level, "white" to seats[0].name, "black" to seats[1].name),
            "white" to sideMap(0, t), "black" to sideMap(1, t),
            "me" to me?.let { linkedMapOf("id" to it.id, "name" to it.name, "color" to myColor?.let(::colorKey)) },
            "spectators" to spectatorCount(),
            "players" to activePlayers().map { linkedMapOf("id" to it.id, "name" to it.name, "connected" to isConnected(it, t), "color" to colorOf(it)?.let(::colorKey)) },
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
                "remainingMs" to (if (g != null && roomStage == RoomStage.PLAYING) g.remainingMs(t) else null),
                "running" to (g != null && roomStage == RoomStage.PLAYING && !g.over && !g.paused), "paused" to (g?.paused == true),
                "turn" to colorKey(g?.turn ?: 0)),
            "drawOffer" to g?.drawOffer?.let(::colorKey),
            "thinking" to (aiStop != null),
            "notice" to notice,
            "result" to g?.result?.let { r -> linkedMapOf("winner" to r.winner?.let(::colorKey), "reason" to r.reason.name, "text" to r.text, "pgn" to r.pgn) },
            "pgn" to (if (g != null && g.over) g.pgn(seatName(0), seatName(1)) else null),
        )
    }

    private fun sideMap(c: Int, t: Long): Map<String, Any?> {
        val p = holder(c)
        return linkedMapOf("kind" to seats[c].name, "name" to seatName(c), "connected" to when (seats[c]) {
            Seat.PHONE -> p?.let { isConnected(it, t) } ?: false
            else -> true
        }, "level" to (if (seats[c] == Seat.AI) level else null), "playerId" to p?.id)
    }

    override fun viewJson(token: String?): String = Json.write(view(token))

    companion object {
        const val PROTOCOL = 1
        const val PRESENCE_MS = GameRoom.PRESENCE_MS
        const val MAX_NAME = GameRoom.MAX_NAME
        /** The computer accepts a draw when it stands at least this much worse (centipawns). */
        const val AI_DRAW_MARGIN = 50

        fun colorKey(c: Int) = if (c == Piece.WHITE) "w" else "b"
        fun colorName(c: Int, plural: Boolean) = if (c == Piece.WHITE) (if (plural) "Blancs" else "Blancs") else (if (plural) "Noirs" else "Noirs")

        fun cleanName(n: String?): String? = GameRoom.cleanName(n)
    }
}
