package castbridge.core.chess

import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/**
 * The computer opponent: negamax alpha-beta with iterative deepening, principal variation search, quiescence,
 * null-move pruning, late-move reductions, killer/history ordering and a transposition table of fixed size
 * (two LongArrays: 16 bytes per entry, 4 MiB by default, never more than 16 MiB).
 *
 * Levels 1..8 bound the depth and the time; levels 1-4 add random noise to the root scores (a human-like
 * « weaker » play that still never misses a mate the depth can see). Thinking time is always ≤ [HARD_CAP_MS] (3 s)
 * and ≤ the time the caller allows (the move countdown minus a margin). Runs on the caller's thread: callers keep it
 * off the UI thread. One search at a time per instance.
 */
class ChessAi(ttBits: Int = DEFAULT_TT_BITS, private val random: java.util.Random = java.util.Random(),
              private val clock: () -> Long = { System.nanoTime() / 1_000_000 }) {

    data class Level(val n: Int, val maxDepth: Int, val timeMs: Long, val noiseCp: Int) {
        val label: String get() = "Niveau $n"
    }

    data class Result(val move: Int, val score: Int, val depth: Int, val nodes: Long, val timeMs: Long, val fromBook: Boolean) {
        val uci: String get() = Move.uci(move)
    }

    private val bits = ttBits.coerceIn(10, MAX_TT_BITS)
    private val mask = (1 shl bits) - 1
    private val ttKey = LongArray(1 shl bits)
    private val ttData = LongArray(1 shl bits)
    /** Bytes held by the transposition table (the only large allocation). */
    val memoryBytes: Long = (1L shl bits) * 16

    private lateinit var pos: Position
    private var nodes = 0L
    private var deadline = 0L
    private var stopFlag: AtomicBoolean? = null
    private var aborted = false
    private val moveBuf = Array(MAX_PLY + 1) { IntArray(256) }
    private val scoreBuf = Array(MAX_PLY + 1) { IntArray(256) }
    private val killers = Array(MAX_PLY + 1) { IntArray(2) }
    private val history = IntArray(16 * 128)

    /**
     * Chooses a move for the side to move in [position] (not modified: the search works on a copy that keeps the
     * game history, for repetitions). [maxTimeMs] = what the caller allows (e.g. the move countdown − 2 s).
     * [stop] aborts early (the best move found so far is returned). Returns move 0 only if there is no legal move.
     */
    fun think(position: Position, level: Int, maxTimeMs: Long = HARD_CAP_MS, stop: AtomicBoolean? = null, useBook: Boolean = true): Result {
        val lv = level(level)
        val start = clock()
        val legal = position.legalMoves()
        if (legal.isEmpty()) return Result(0, 0, 0, 0, 0, false)
        if (useBook) OpeningBook.pick(position, random)?.let { return Result(it, 0, 0, 0, clock() - start, true) }
        if (legal.size == 1) return Result(legal[0], 0, 0, 0, clock() - start, false)
        val budget = minOf(lv.timeMs, HARD_CAP_MS, maxOf(MIN_TIME_MS, maxTimeMs))
        pos = position.copy()
        nodes = 0; aborted = false; stopFlag = stop
        deadline = start + budget
        history.fill(0); killers.forEach { it.fill(0) }
        return if (lv.noiseCp > 0) noisyRoot(legal, lv, start, budget) else searchRoot(lv, start, budget, legal)
    }

    /** Levels 1-4: every root move gets an exact score at each depth, then noise decides between close moves. */
    private fun noisyRoot(legal: IntArray, lv: Level, start: Long, budget: Long): Result {
        var scores = IntArray(legal.size)
        var done = 0
        for (depth in 1..lv.maxDepth) {
            val s = IntArray(legal.size)
            var complete = true
            for ((i, m) in legal.withIndex()) {
                pos.make(m)
                s[i] = -search(depth - 1, 1, -INF, INF, true)
                pos.unmake()
                if (aborted) { complete = false; break }
            }
            if (!complete) break
            scores = s; done = depth
            if (clock() - start > budget / 2) break
        }
        if (done == 0) return Result(legal[random.nextInt(legal.size)], 0, 0, nodes, clock() - start, false)
        var best = 0; var bestNoisy = Int.MIN_VALUE
        for (i in legal.indices) {
            val sc = scores[i]
            // mates are never blurred: a mate in reach is always played, a forced loss always avoided if possible
            val noisy = if (abs(sc) >= MATE_BOUND) sc * 4 else sc + random.nextInt(2 * lv.noiseCp + 1) - lv.noiseCp
            if (noisy > bestNoisy) { bestNoisy = noisy; best = i }
        }
        return Result(legal[best], scores[best], done, nodes, clock() - start, false)
    }

    private fun searchRoot(lv: Level, start: Long, budget: Long, legal: IntArray): Result {
        var bestMove = legal[0]; var bestScore = 0; var depthDone = 0
        for (depth in 1..lv.maxDepth) {
            var alpha = -INF; val beta = INF
            var iterBest = 0; var iterScore = -INF
            val moves = orderRoot(legal, bestMove)
            for ((i, m) in moves.withIndex()) {
                pos.make(m)
                var sc: Int
                if (i == 0) sc = -search(depth - 1, 1, -beta, -alpha, true)
                else {
                    sc = -search(depth - 1, 1, -alpha - 1, -alpha, true)
                    if (!aborted && sc > alpha) sc = -search(depth - 1, 1, -beta, -alpha, true)
                }
                pos.unmake()
                if (aborted) break
                if (sc > iterScore) { iterScore = sc; iterBest = m }
                if (sc > alpha) alpha = sc
            }
            if (iterBest != 0) { bestMove = iterBest; bestScore = iterScore }   // a partial iteration still searched the best move first
            if (aborted) break
            depthDone = depth
            if (abs(bestScore) >= MATE_BOUND) break                             // a forced mate found: no need to go deeper
            if (clock() - start > budget / 2) break                             // the next depth would not finish in time
        }
        return Result(bestMove, bestScore, depthDone, nodes, clock() - start, false)
    }

    private fun orderRoot(legal: IntArray, first: Int): IntArray {
        val l = legal.toMutableList()
        if (l.remove(first)) l.add(0, first)
        return l.toIntArray()
    }

    private fun timeUp(): Boolean {
        if (aborted) return true
        if (nodes and 1023L == 0L && (clock() >= deadline || stopFlag?.get() == true)) aborted = true
        return aborted
    }

    private fun search(depth0: Int, ply: Int, alpha0: Int, beta: Int, allowNull: Boolean): Int {
        nodes++
        if (timeUp()) return 0
        if (pos.halfmove >= 100 || pos.repeatedOnce() || pos.insufficientMaterial()) return 0
        if (ply >= MAX_PLY) return evaluate(pos)
        var alpha = alpha0
        val inCheck = pos.inCheck()
        val depth = if (inCheck) depth0 + 1 else depth0
        if (depth <= 0) return quiesce(ply, alpha, beta, 0)

        // transposition table
        val idx = (pos.hash.toInt() xor (pos.hash ushr 32).toInt()) and mask
        var ttMove = 0
        if (ttKey[idx] == pos.hash) {
            val d = ttData[idx]
            ttMove = (d and 0x1FFFFF).toInt()
            val ttDepth = ((d ushr 37) and 0xFF).toInt()
            if (ttDepth >= depth) {
                var sc = (((d ushr 21) and 0xFFFF).toInt()) - 32768
                if (sc >= MATE_BOUND) sc -= ply else if (sc <= -MATE_BOUND) sc += ply
                when (((d ushr 45) and 3).toInt()) {
                    EXACT -> return sc
                    LOWER -> if (sc >= beta) return sc
                    UPPER -> if (sc <= alpha) return sc
                }
            }
        }

        // null move: if passing still keeps us above beta, this line is too good for the opponent to allow
        if (allowNull && !inCheck && depth >= 3 && ply > 0 && hasPieces(pos.side) && evaluate(pos) >= beta) {
            pos.makeNull()
            val sc = -search(depth - 3, ply + 1, -beta, -beta + 1, false)
            pos.unmakeNull()
            if (aborted) return 0
            if (sc >= beta) return beta
        }

        val moves = moveBuf[ply]; val scores = scoreBuf[ply]
        val n = pos.pseudo(moves)
        for (i in 0 until n) scores[i] = orderScore(moves[i], ttMove, ply)
        var legal = 0
        var best = -INF; var bestMove = 0
        for (i in 0 until n) {
            // selection sort step: bring the most promising remaining move to i
            var bi = i
            for (j in i + 1 until n) if (scores[j] > scores[bi]) bi = j
            val m = moves[bi]; moves[bi] = moves[i]; moves[i] = m
            val s = scores[bi]; scores[bi] = scores[i]; scores[i] = s
            if (!pos.make(m)) continue
            legal++
            val quiet = !Move.isCapture(m) && Move.promo(m) == 0
            var sc: Int
            if (legal == 1) sc = -search(depth - 1, ply + 1, -beta, -alpha, true)
            else {
                // late move reduction for quiet moves far down the list
                val reduce = if (quiet && legal > 4 && depth >= 3 && !inCheck && !pos.inCheck()) 1 else 0
                sc = -search(depth - 1 - reduce, ply + 1, -alpha - 1, -alpha, true)
                if (!aborted && sc > alpha && reduce > 0) sc = -search(depth - 1, ply + 1, -alpha - 1, -alpha, true)
                if (!aborted && sc > alpha && sc < beta) sc = -search(depth - 1, ply + 1, -beta, -alpha, true)
            }
            pos.unmake()
            if (aborted) return 0
            if (sc > best) { best = sc; bestMove = m }
            if (sc > alpha) {
                alpha = sc
                if (alpha >= beta) {
                    if (quiet) {
                        val k = killers[ply]
                        if (k[0] != m) { k[1] = k[0]; k[0] = m }
                        val h = pos.pieceAt(Move.from(m)) * 128 + Move.to(m)
                        history[h] = minOf(history[h] + depth * depth, 50_000)
                    }
                    break
                }
            }
        }
        if (legal == 0) return if (inCheck) -MATE + ply else 0
        val flag = if (best >= beta) LOWER else if (best > alpha0) EXACT else UPPER
        store(idx, bestMove, best, depth, flag, ply)
        return best
    }

    private fun store(idx: Int, move: Int, score0: Int, depth: Int, flag: Int, ply: Int) {
        var score = score0
        if (score >= MATE_BOUND) score += ply else if (score <= -MATE_BOUND) score -= ply
        val old = ttData[idx]
        if (ttKey[idx] == pos.hash && ((old ushr 37) and 0xFF).toInt() > depth && flag != EXACT) return
        ttKey[idx] = pos.hash
        ttData[idx] = (move.toLong() and 0x1FFFFF) or ((score + 32768).toLong() and 0xFFFF shl 21) or
            ((depth.coerceIn(0, 255)).toLong() shl 37) or (flag.toLong() shl 45)
    }

    private fun quiesce(ply: Int, alpha0: Int, beta: Int, qd: Int): Int {
        nodes++
        if (timeUp()) return 0
        var alpha = alpha0
        val stand = evaluate(pos)
        if (stand >= beta) return stand
        if (stand > alpha) alpha = stand
        if (ply >= MAX_PLY || qd > 12) return stand
        val moves = moveBuf[ply]; val scores = scoreBuf[ply]
        val n = pos.pseudo(moves, capturesOnly = true)
        for (i in 0 until n) scores[i] = mvvLva(moves[i])
        var best = stand
        for (i in 0 until n) {
            var bi = i
            for (j in i + 1 until n) if (scores[j] > scores[bi]) bi = j
            val m = moves[bi]; moves[bi] = moves[i]; moves[i] = m
            val s = scores[bi]; scores[bi] = scores[i]; scores[i] = s
            // delta pruning: even winning this piece cannot lift us to alpha
            if (Move.promo(m) == 0 && stand + victimValue(m) + 200 < alpha) continue
            if (!pos.make(m)) continue
            val sc = -quiesce(ply + 1, -beta, -alpha, qd + 1)
            pos.unmake()
            if (aborted) return 0
            if (sc > best) best = sc
            if (sc > alpha) { alpha = sc; if (alpha >= beta) break }
        }
        return best
    }

    private fun victimValue(m: Int): Int =
        if (Move.isEnPassant(m)) VALUE[Piece.PAWN] else VALUE[Piece.type(pos.pieceAt(Move.to(m)))]

    private fun mvvLva(m: Int): Int {
        val victim = if (Move.isEnPassant(m)) Piece.PAWN else Piece.type(pos.pieceAt(Move.to(m)))
        val attacker = Piece.type(pos.pieceAt(Move.from(m)))
        return VALUE[victim] * 10 - VALUE[attacker] / 10 + (if (Move.promo(m) != 0) VALUE[Move.promo(m)] * 10 else 0)
    }

    private fun orderScore(m: Int, ttMove: Int, ply: Int): Int = when {
        m == ttMove -> 10_000_000
        Move.isCapture(m) || Move.promo(m) != 0 -> 1_000_000 + mvvLva(m)
        killers[ply][0] == m -> 900_000
        killers[ply][1] == m -> 800_000
        else -> history[pos.pieceAt(Move.from(m)) * 128 + Move.to(m)]
    }

    private fun hasPieces(color: Int): Boolean {
        for (r in 0 until 8) for (f in 0 until 8) {
            val p = pos.pieceAt(r * 16 + f)
            if (p != 0 && Piece.color(p) == color && Piece.type(p) in Piece.KNIGHT..Piece.QUEEN) return true
        }
        return false
    }

    companion object {
        const val DEFAULT_TT_BITS = 18            // 2^18 entries × 16 bytes = 4 MiB
        const val MAX_TT_BITS = 20                // 16 MiB: the upper bound asked for this 1 GB TV
        /** Never think longer than this, whatever the level and the countdown. */
        const val HARD_CAP_MS = 3_000L
        const val MIN_TIME_MS = 100L
        const val MAX_PLY = 64
        const val INF = 1_000_000
        const val MATE = 32_000
        const val MATE_BOUND = MATE - 1_000
        private const val EXACT = 0
        private const val LOWER = 1
        private const val UPPER = 2

        val LEVELS: List<Level> = listOf(
            Level(1, 1, 250, 200), Level(2, 2, 400, 120), Level(3, 3, 700, 60), Level(4, 4, 1_000, 25),
            Level(5, 5, 1_500, 0), Level(6, 7, 2_000, 0), Level(7, 9, 2_500, 0), Level(8, 40, 3_000, 0),
        )
        fun level(n: Int): Level = LEVELS[n.coerceIn(1, 8) - 1]

        /**
         * Time the AI may use when every move has [perMoveMs] on the clock: at most a third of it (a slow device and
         * the network never make it lose on time), and never more than [HARD_CAP_MS].
         */
        fun allowance(perMoveMs: Long): Long = minOf(HARD_CAP_MS, maxOf(MIN_TIME_MS, perMoveMs / 3))

        val VALUE = intArrayOf(0, 100, 320, 330, 500, 900, 0)
        private val PHASE = intArrayOf(0, 0, 1, 1, 2, 4, 0)

        // Piece-square tables (Tomasz Michniewski's « simplified evaluation function »), from White's side, rank 8 first.
        private val PST_PAWN = intArrayOf(
            0, 0, 0, 0, 0, 0, 0, 0, 50, 50, 50, 50, 50, 50, 50, 50, 10, 10, 20, 30, 30, 20, 10, 10, 5, 5, 10, 25, 25, 10, 5, 5,
            0, 0, 0, 20, 20, 0, 0, 0, 5, -5, -10, 0, 0, -10, -5, 5, 5, 10, 10, -20, -20, 10, 10, 5, 0, 0, 0, 0, 0, 0, 0, 0)
        private val PST_KNIGHT = intArrayOf(
            -50, -40, -30, -30, -30, -30, -40, -50, -40, -20, 0, 0, 0, 0, -20, -40, -30, 0, 10, 15, 15, 10, 0, -30, -30, 5, 15, 20, 20, 15, 5, -30,
            -30, 0, 15, 20, 20, 15, 0, -30, -30, 5, 10, 15, 15, 10, 5, -30, -40, -20, 0, 5, 5, 0, -20, -40, -50, -40, -30, -30, -30, -30, -40, -50)
        private val PST_BISHOP = intArrayOf(
            -20, -10, -10, -10, -10, -10, -10, -20, -10, 0, 0, 0, 0, 0, 0, -10, -10, 0, 5, 10, 10, 5, 0, -10, -10, 5, 5, 10, 10, 5, 5, -10,
            -10, 0, 10, 10, 10, 10, 0, -10, -10, 10, 10, 10, 10, 10, 10, -10, -10, 5, 0, 0, 0, 0, 5, -10, -20, -10, -10, -10, -10, -10, -10, -20)
        private val PST_ROOK = intArrayOf(
            0, 0, 0, 0, 0, 0, 0, 0, 5, 10, 10, 10, 10, 10, 10, 5, -5, 0, 0, 0, 0, 0, 0, -5, -5, 0, 0, 0, 0, 0, 0, -5,
            -5, 0, 0, 0, 0, 0, 0, -5, -5, 0, 0, 0, 0, 0, 0, -5, -5, 0, 0, 0, 0, 0, 0, -5, 0, 0, 0, 5, 5, 0, 0, 0)
        private val PST_QUEEN = intArrayOf(
            -20, -10, -10, -5, -5, -10, -10, -20, -10, 0, 0, 0, 0, 0, 0, -10, -10, 0, 5, 5, 5, 5, 0, -10, -5, 0, 5, 5, 5, 5, 0, -5,
            0, 0, 5, 5, 5, 5, 0, -5, -10, 5, 5, 5, 5, 5, 0, -10, -10, 0, 5, 0, 0, 0, 0, -10, -20, -10, -10, -5, -5, -10, -10, -20)
        private val PST_KING_MG = intArrayOf(
            -30, -40, -40, -50, -50, -40, -40, -30, -30, -40, -40, -50, -50, -40, -40, -30, -30, -40, -40, -50, -50, -40, -40, -30, -30, -40, -40, -50, -50, -40, -40, -30,
            -20, -30, -30, -40, -40, -30, -30, -20, -10, -20, -20, -20, -20, -20, -20, -10, 20, 20, 0, 0, 0, 0, 20, 20, 20, 30, 10, 0, 0, 10, 30, 20)
        private val PST_KING_EG = intArrayOf(
            -50, -40, -30, -20, -20, -30, -40, -50, -30, -20, -10, 0, 0, -10, -20, -30, -30, -10, 20, 30, 30, 20, -10, -30, -30, -10, 30, 40, 40, 30, -10, -30,
            -30, -10, 30, 40, 40, 30, -10, -30, -30, -10, 20, 30, 30, 20, -10, -30, -30, -30, 0, 0, 0, 0, -30, -30, -50, -30, -30, -30, -30, -30, -30, -50)
        private val PST = arrayOf(IntArray(64), PST_PAWN, PST_KNIGHT, PST_BISHOP, PST_ROOK, PST_QUEEN, PST_KING_MG)

        /** Static evaluation in centipawns from the side to move's point of view. */
        fun evaluate(p: Position): Int {
            var mg = 0; var kingMg = 0; var kingEg = 0; var phase = 0
            val bishops = IntArray(2); val material = IntArray(2); var pawns = 0
            for (r in 0 until 8) for (f in 0 until 8) {
                val pc = p.pieceAt(r * 16 + f)
                if (pc == 0) continue
                val t = Piece.type(pc); val c = Piece.color(pc)
                val idx = if (c == Piece.WHITE) (7 - r) * 8 + f else r * 8 + f
                val sign = if (c == Piece.WHITE) 1 else -1
                phase += PHASE[t]
                material[c] += VALUE[t]
                if (t == Piece.BISHOP) bishops[c]++
                if (t == Piece.PAWN) pawns++
                if (t == Piece.KING) { kingMg += sign * PST_KING_MG[idx]; kingEg += sign * PST_KING_EG[idx] }
                else mg += sign * (VALUE[t] + PST[t][idx])
            }
            val ph = minOf(phase, 24)
            var score = mg + (kingMg * ph + kingEg * (24 - ph)) / 24
            if (bishops[0] >= 2) score += 30
            if (bishops[1] >= 2) score -= 30
            // mop-up: with a clear material edge and no pawns left, drive the lone king to the edge and come closer
            val diff = material[0] - material[1]
            if (pawns == 0 && abs(diff) >= 400) {
                val strong = if (diff > 0) Piece.WHITE else Piece.BLACK
                val weakK = p.king(strong xor 1); val strongK = p.king(strong)
                val cf = Sq.file(weakK); val cr = Sq.rank(weakK)
                val centre = maxOf(3 - cf, cf - 4) + maxOf(3 - cr, cr - 4)
                val dist = abs(Sq.file(weakK) - Sq.file(strongK)) + abs(Sq.rank(weakK) - Sq.rank(strongK))
                val bonus = centre * 10 + (14 - dist) * 4
                score += if (strong == Piece.WHITE) bonus else -bonus
            }
            score += if (p.side == Piece.WHITE) 10 else -10          // tempo
            return if (p.side == Piece.WHITE) score else -score
        }
    }

    private fun evaluate(p: Position) = Companion.evaluate(p)
}

/**
 * A small embedded opening book: a few dozen main lines of the usual openings (SAN), turned into
 * « position → moves » at first use. The AI picks one of the book moves at random, so games vary.
 */
object OpeningBook {
    val LINES = listOf(
        "e4 e5 Nf3 Nc6 Bb5 a6 Ba4 Nf6 O-O Be7 Re1 b5 Bb3 d6 c3 O-O",          // Espagnole
        "e4 e5 Nf3 Nc6 Bb5 Nf6 O-O Nxe4 d4 Nd6 Bxc6 dxc6 dxe5 Nf5",           // Berlinoise
        "e4 e5 Nf3 Nc6 Bc4 Bc5 c3 Nf6 d3 d6 O-O O-O",                         // Italienne
        "e4 e5 Nf3 Nc6 Bc4 Nf6 d3 Be7 O-O O-O Re1 d6",                        // Deux cavaliers, tranquille
        "e4 e5 Nf3 Nc6 d4 exd4 Nxd4 Nf6 Nxc6 bxc6 e5 Qe7",                    // Écossaise
        "e4 e5 Nf3 Nf6 Nxe5 d6 Nf3 Nxe4 d4 d5 Bd3 Nc6",                       // Petrov
        "e4 e5 Nf3 d6 d4 Nf6 Nc3 Nbd7 Bc4 Be7",                               // Philidor
        "e4 e5 Nc3 Nf6 Nf3 Nc6 Bb5 Bb4 O-O O-O",                              // Quatre cavaliers
        "e4 e5 Bc4 Nf6 d3 c6 Nf3 d5 Bb3",                                     // Partie du fou
        "e4 e5 f4 exf4 Nf3 g5 h4 g4 Ne5",                                     // Gambit du roi
        "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6 Be2 e5 Nb3 Be7",                // Sicilienne Najdorf
        "e4 c5 Nf3 Nc6 d4 cxd4 Nxd4 Nf6 Nc3 e5 Ndb5 d6",                      // Sveshnikov
        "e4 c5 Nf3 e6 d4 cxd4 Nxd4 Nc6 Nc3 Qc7",                              // Taïmanov
        "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 g6 Be3 Bg7 f3 O-O",                // Dragon
        "e4 c5 c3 Nf6 e5 Nd5 d4 cxd4 Nf3 Nc6",                                // Alapin
        "e4 c5 Nc3 Nc6 g3 g6 Bg2 Bg7 d3 d6",                                  // Sicilienne fermée
        "e4 e6 d4 d5 Nc3 Nf6 Bg5 Be7 e5 Nfd7 Bxe7 Qxe7",                      // Française classique
        "e4 e6 d4 d5 e5 c5 c3 Nc6 Nf3 Qb6",                                   // Française avance
        "e4 e6 d4 d5 Nd2 c5 exd5 exd5 Ngf3 Nc6",                              // Française Tarrasch
        "e4 c6 d4 d5 Nc3 dxe4 Nxe4 Bf5 Ng3 Bg6 h4 h6",                        // Caro-Kann
        "e4 c6 d4 d5 e5 Bf5 Nf3 e6 Be2 c5",                                   // Caro-Kann avance
        "e4 d5 exd5 Qxd5 Nc3 Qa5 d4 Nf6 Nf3 Bf5",                             // Scandinave
        "e4 d6 d4 Nf6 Nc3 g6 Nf3 Bg7 Be2 O-O O-O",                            // Pirc
        "e4 g6 d4 Bg7 Nc3 d6 Be3 a6",                                         // Moderne
        "e4 Nf6 e5 Nd5 d4 d6 Nf3 Bg4 Be2 e6",                                 // Alekhine
        "d4 d5 c4 e6 Nc3 Nf6 Bg5 Be7 e3 O-O Nf3 h6",                          // Gambit dame refusé
        "d4 d5 c4 c6 Nf3 Nf6 Nc3 dxc4 a4 Bf5",                                // Slave
        "d4 d5 c4 dxc4 Nf3 Nf6 e3 e6 Bxc4 c5",                                // Gambit dame accepté
        "d4 d5 Nf3 Nf6 Bf4 e6 e3 c5 c3 Nc6",                                  // Système de Londres
        "d4 Nf6 c4 g6 Nc3 Bg7 e4 d6 Nf3 O-O Be2 e5",                          // Est-indienne
        "d4 Nf6 c4 e6 Nc3 Bb4 e3 O-O Bd3 d5 Nf3 c5",                          // Nimzo-indienne
        "d4 Nf6 c4 e6 Nf3 b6 g3 Ba6 b3 Bb4+ Bd2 Be7",                         // Ouest-indienne
        "d4 Nf6 c4 g6 Nc3 d5 cxd5 Nxd5 e4 Nxc3 bxc3 Bg7",                     // Grünfeld
        "d4 Nf6 c4 c5 d5 e6 Nc3 exd5 cxd5 d6",                                // Benoni
        "d4 Nf6 Nf3 e6 Bg5 c5 e3 h6 Bh4 Qb6",                                 // Torre
        "d4 f5 g3 Nf6 Bg2 g6 Nf3 Bg7 O-O O-O",                                // Hollandaise
        "d4 e6 c4 f5 Nc3 Nf6 e3 d5 Nf3 c6",                                   // Hollandaise Stonewall
        "c4 e5 Nc3 Nf6 Nf3 Nc6 g3 d5 cxd5 Nxd5 Bg2 Nb6",                      // Anglaise
        "c4 c5 Nc3 Nc6 g3 g6 Bg2 Bg7 Nf3 e6",                                 // Anglaise symétrique
        "c4 Nf6 Nc3 e6 e4 d5 e5 d4",                                          // Anglaise Mikenas
        "Nf3 d5 g3 Nf6 Bg2 c6 O-O Bg4 d3 Nbd7",                               // Réti / Est-indienne inversée
        "Nf3 Nf6 c4 g6 Nc3 Bg7 e4 d6 d4 O-O",
    )

    private val table: Map<Long, List<Int>> by lazy {
        val t = HashMap<Long, MutableList<Int>>()
        for (line in LINES) {
            val p = Position.start()
            for (san in line.split(' ')) {
                val m = San.parse(p, san)
                require(m != 0) { "livre d'ouvertures : « $san » illégal dans « $line »" }
                t.getOrPut(p.hash) { ArrayList() }.add(m)
                p.make(m)
            }
        }
        t
    }

    /** Number of distinct positions in the book (parses every line: a typo fails here, see the tests). */
    fun size(): Int = table.size

    /** A book move for [pos] (weighted by how many lines play it), or null when out of book. */
    fun pick(pos: Position, random: java.util.Random): Int? {
        val moves = table[pos.hash] ?: return null
        val legal = pos.legalMoves()
        return moves.filter { it in legal }.takeIf { it.isNotEmpty() }?.let { it[random.nextInt(it.size)] }
    }
}
