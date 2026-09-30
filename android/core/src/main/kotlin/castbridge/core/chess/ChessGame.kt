package castbridge.core.chess

/**
 * What happens when the move countdown runs out:
 * - [COMPETITION]: the player who did not move in time loses (standard « perte au temps »; drawn if the opponent
 *   could not mate any more, FIDE 6.9);
 * - [PRACTICE]: nobody loses on time: a random legal move is played for him (« coup joué d'office ») and the game
 *   goes on. Chess has no « pass », so this is the only way to keep the countdown meaningful without ending the game.
 */
enum class ClockMode(val label: String, val rule: String) {
    COMPETITION("Compétition", "Temps dépassé = partie perdue"),
    PRACTICE("Entraînement", "Temps dépassé = un coup est joué d'office"),
}

/** The move countdown: every player has at most [MAX_SECONDS] (60 s) per move, whatever is asked. */
object MoveTimer {
    val CHOICES = intArrayOf(10, 20, 30, 45, 60)
    const val MIN_SECONDS = 10
    const val MAX_SECONDS = 60
    const val DEFAULT_SECONDS = 30

    /** Any requested value is brought into 10..60 s: never more than a minute. */
    fun clamp(seconds: Int): Int = seconds.coerceIn(MIN_SECONDS, MAX_SECONDS)

    /** The next choice after [seconds] (the remote's ◀ ▶ cycle through the list). */
    fun next(seconds: Int, step: Int): Int {
        val i = CHOICES.indexOfFirst { it >= clamp(seconds) }.coerceAtLeast(0)
        return CHOICES[(i + step + CHOICES.size) % CHOICES.size]
    }
}

/**
 * One game: the position, the moves in SAN and UCI, the countdown, draw offers, resignation and the result.
 * The clock is the host's (TV or relay): clients never send times, only moves. Not thread-safe (the room locks).
 */
class ChessGame(
    perMoveSeconds: Int = MoveTimer.DEFAULT_SECONDS,
    val mode: ClockMode = ClockMode.COMPETITION,
    val startFen: String = Position.START_FEN,
    now: Long,
    private val random: java.util.Random = java.util.Random(),
) {
    enum class Play { OK, ILLEGAL, NOT_YOUR_TURN, STALE, OVER }

    val perMoveMs: Long = MoveTimer.clamp(perMoveSeconds) * 1000L
    val position: Position = Position.fromFen(startFen)
    val san = ArrayList<String>()
    val uci = ArrayList<String>()
    var result: GameResult? = null; private set
    var turnStartedAt = now; private set
    /** Colour that offered a draw, pending until the other side answers or moves. */
    var drawOffer: Int? = null; private set
    /** Plies (indexes into [uci]) that were played « d'office » at the end of the countdown (practice mode). */
    val autoPlayed = HashSet<Int>()

    val over get() = result != null
    val turn get() = position.side
    /** Half-moves played since the start of the game (the number a client sends with its move). */
    val ply get() = uci.size

    /** Set while the clock is stopped (pause menu of a game played only at the TV). */
    var pausedAt: Long? = null; private set
    val paused get() = pausedAt != null

    fun remainingMs(now: Long): Long = if (over) 0 else (perMoveMs - ((pausedAt ?: now) - turnStartedAt)).coerceIn(0, perMoveMs)

    /** Stops the countdown (the host decides when it is allowed: never with a remote opponent waiting). */
    fun pause(now: Long): Boolean { if (over || paused) return false; pausedAt = now; return true }

    /** Restarts the countdown where it stopped. */
    fun resume(now: Long): Boolean { val p = pausedAt ?: return false; turnStartedAt += now - p; pausedAt = null; return true }

    /**
     * Plays [move] (UCI) for [color]. [expectedPly] (optional) protects against a late retry of an old move: it must
     * equal [ply]. The host validates everything: it is the only anti-cheat that matters.
     */
    fun play(move: String, color: Int, now: Long, expectedPly: Int? = null): Play {
        if (over) return Play.OVER
        if (expectedPly != null && expectedPly != ply) return Play.STALE
        if (color != position.side) return Play.NOT_YOUR_TURN
        resume(now)
        if (remainingMs(now) <= 0) { tick(now); return if (over) Play.OVER else Play.STALE }
        val m = position.parseUci(move)
        if (m == 0) return Play.ILLEGAL
        apply(m, now)
        return Play.OK
    }

    private fun apply(m: Int, now: Long) {
        san += San.of(position, m)
        uci += Move.uci(m)
        position.make(m)
        turnStartedAt = now
        // a move answers a pending offer made by the opponent (declined); one's own offer stays until answered
        if (drawOffer != null && drawOffer != position.side xor 1) drawOffer = null
        Rules.status(position)?.let { result = it }
    }

    /** Clock check (the host calls it often). Returns true if something changed. */
    fun tick(now: Long): Boolean {
        if (over || paused || now - turnStartedAt < perMoveMs) return false
        when (mode) {
            ClockMode.COMPETITION -> { result = Rules.timeout(position, position.side); turnStartedAt = now }
            ClockMode.PRACTICE -> {
                val legal = position.legalMoves()
                autoPlayed += ply
                apply(legal[random.nextInt(legal.size)], now)
            }
        }
        return true
    }

    fun resign(color: Int): Boolean {
        if (over) return false
        result = GameResult.win(color xor 1, EndReason.RESIGNATION); return true
    }

    fun offerDraw(color: Int): Boolean {
        if (over || drawOffer == color) return false
        if (drawOffer == color xor 1) return answerDraw(color, true)      // both offered: agreed
        drawOffer = color; return true
    }

    fun answerDraw(color: Int, accept: Boolean): Boolean {
        if (over || drawOffer != color xor 1) return false
        drawOffer = null
        if (accept) result = GameResult.draw(EndReason.AGREEMENT)
        return true
    }

    /** Ends the game without a winner (e.g. the TV closes the room). */
    fun abandon() { if (!over) result = GameResult.draw(EndReason.ABANDONED) }

    /** Takes back the last [plies] half-moves (practice against the computer). The countdown starts again. */
    fun undo(plies: Int, now: Long): Boolean {
        if (plies <= 0 || plies > uci.size) return false
        repeat(plies) { position.unmake(); san.removeAt(san.size - 1); autoPlayed.remove(uci.size - 1); uci.removeAt(uci.size - 1) }
        result = null; drawOffer = null; turnStartedAt = now; pausedAt = null
        return true
    }

    fun lastMove(): String? = uci.lastOrNull()

    fun pgn(white: String, black: String, event: String = "Partie sur CastBridge TV", date: String? = null): String =
        Pgn.write(linkedMapOf("Event" to event, "Site" to "CastBridge TV", "Date" to (date ?: "????.??.??"), "White" to white, "Black" to black,
            "TimeControl" to "${perMoveMs / 1000}s par coup").also { if (result != null) it["Termination"] = result!!.reason.text },
            san, result?.pgn ?: "*", startFen)
}
