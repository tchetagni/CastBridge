package castbridge.core.chess

/**
 * Pieces are small ints: type 1..6 (pawn, knight, bishop, rook, queen, king) with bit 3 set for black.
 * 0 = empty square.
 */
object Piece {
    const val EMPTY = 0
    const val PAWN = 1
    const val KNIGHT = 2
    const val BISHOP = 3
    const val ROOK = 4
    const val QUEEN = 5
    const val KING = 6
    const val WHITE = 0
    const val BLACK = 1

    fun type(p: Int) = p and 7
    fun color(p: Int) = p shr 3
    fun of(type: Int, color: Int) = type or (color shl 3)

    /** FEN letter: upper case for white. */
    fun char(p: Int): Char {
        val c = " pnbrqk"[type(p)]
        return if (color(p) == WHITE) c.uppercaseChar() else c
    }

    fun fromChar(c: Char): Int {
        val t = " pnbrqk".indexOf(c.lowercaseChar())
        require(t > 0) { "bad piece '$c'" }
        return of(t, if (c.isUpperCase()) WHITE else BLACK)
    }
}

/**
 * Squares use the 0x88 layout: index = rank * 16 + file, a1 = 0, h1 = 7, a8 = 112. Off-board test: (sq and 0x88) != 0.
 */
object Sq {
    fun of(file: Int, rank: Int) = rank * 16 + file
    fun file(sq: Int) = sq and 7
    fun rank(sq: Int) = sq shr 4
    fun ok(sq: Int) = sq and 0x88 == 0
    fun name(sq: Int): String = "${'a' + file(sq)}${'1' + rank(sq)}"
    fun parse(s: String): Int {
        require(s.length == 2 && s[0] in 'a'..'h' && s[1] in '1'..'8') { "bad square '$s'" }
        return of(s[0] - 'a', s[1] - '1')
    }
    /** 0..63 index (a1 = 0, h8 = 63), for tables. */
    fun to64(sq: Int) = rank(sq) * 8 + file(sq)
    fun from64(i: Int) = of(i and 7, i shr 3)
}

/**
 * A move packed in an Int: from (7 bits) | to (7 bits) << 7 | promotion piece type (3 bits) << 14 | flags << 17.
 * 0 is "no move".
 */
object Move {
    const val CAPTURE = 1
    const val EN_PASSANT = 2
    const val CASTLE = 4
    const val DOUBLE_PUSH = 8

    fun make(from: Int, to: Int, promo: Int = 0, flags: Int = 0) = from or (to shl 7) or (promo shl 14) or (flags shl 17)
    fun from(m: Int) = m and 0x7f
    fun to(m: Int) = (m shr 7) and 0x7f
    fun promo(m: Int) = (m shr 14) and 7
    fun flags(m: Int) = m shr 17
    fun isCapture(m: Int) = flags(m) and CAPTURE != 0
    fun isCastle(m: Int) = flags(m) and CASTLE != 0
    fun isEnPassant(m: Int) = flags(m) and EN_PASSANT != 0

    /** Long algebraic « UCI » form: e2e4, e7e8q. */
    fun uci(m: Int): String = Sq.name(from(m)) + Sq.name(to(m)) + (if (promo(m) != 0) " pnbrqk"[promo(m)].toString() else "")
}

/**
 * A chess position with complete rules (castling, en passant, promotion, check) and reversible make/unmake,
 * incremental Zobrist hash and the history needed for repetitions. Not thread-safe: one owner at a time
 * (the AI searches on its own [copy]).
 */
class Position private constructor() {
    val board = IntArray(128)
    var side = Piece.WHITE; private set
    /** Castling rights: 1 = white O-O, 2 = white O-O-O, 4 = black O-O, 8 = black O-O-O. */
    var castling = 0; private set
    /** En passant target square (the square the capturing pawn moves to), or -1. */
    var ep = -1; private set
    var halfmove = 0; private set
    var fullmove = 1; private set
    var hash = 0L; private set
    private val kings = IntArray(2)

    // undo stack: the move, the captured piece, castling, ep, halfmove, and the hash before the move
    private var sp = 0
    private var stMove = IntArray(256)
    private var stInfo = IntArray(256)
    private var stHash = LongArray(256)
    /** Hashes of the positions before the moves of this game that precede [sp] (imported from a PGN / game history). */
    val plies get() = sp

    fun king(color: Int) = kings[color]
    fun pieceAt(sq: Int) = board[sq]

    fun copy(): Position {
        val p = Position()
        System.arraycopy(board, 0, p.board, 0, 128)
        p.side = side; p.castling = castling; p.ep = ep; p.halfmove = halfmove; p.fullmove = fullmove; p.hash = hash
        p.kings[0] = kings[0]; p.kings[1] = kings[1]
        p.sp = sp
        p.stMove = stMove.copyOf(); p.stInfo = stInfo.copyOf(); p.stHash = stHash.copyOf()
        return p
    }

    // ------------------------------------------------------------------ attacks

    /** Is [sq] attacked by a piece of [by]? */
    fun attacked(sq: Int, by: Int): Boolean {
        // pawns: a white pawn attacks up (+15, +17), so it sits at sq - 15 / sq - 17
        if (by == Piece.WHITE) {
            val a = sq - 15; val b = sq - 17
            if (Sq.ok(a) && board[a] == W_PAWN) return true
            if (Sq.ok(b) && board[b] == W_PAWN) return true
        } else {
            val a = sq + 15; val b = sq + 17
            if (Sq.ok(a) && board[a] == B_PAWN) return true
            if (Sq.ok(b) && board[b] == B_PAWN) return true
        }
        val kn = Piece.of(Piece.KNIGHT, by)
        for (d in KNIGHT_D) { val t = sq + d; if (Sq.ok(t) && board[t] == kn) return true }
        val kg = Piece.of(Piece.KING, by)
        for (d in KING_D) { val t = sq + d; if (Sq.ok(t) && board[t] == kg) return true }
        val q = Piece.of(Piece.QUEEN, by); val r = Piece.of(Piece.ROOK, by); val bi = Piece.of(Piece.BISHOP, by)
        for (d in ROOK_D) {
            var t = sq + d
            while (Sq.ok(t)) { val p = board[t]; if (p != 0) { if (p == r || p == q) return true; break }; t += d }
        }
        for (d in BISHOP_D) {
            var t = sq + d
            while (Sq.ok(t)) { val p = board[t]; if (p != 0) { if (p == bi || p == q) return true; break }; t += d }
        }
        return false
    }

    fun inCheck(color: Int = side) = attacked(kings[color], color xor 1)

    // ------------------------------------------------------------------ move generation

    /** Pseudo-legal moves (may leave the king in check) into [out]; returns the count. [capturesOnly] for quiescence. */
    fun pseudo(out: IntArray, capturesOnly: Boolean = false): Int {
        var n = 0
        val us = side; val them = us xor 1
        val fwd = if (us == Piece.WHITE) 16 else -16
        val startRank = if (us == Piece.WHITE) 1 else 6
        val promoRank = if (us == Piece.WHITE) 7 else 0
        for (r in 0 until 8) for (f in 0 until 8) {
            val from = r * 16 + f
            val p = board[from]
            if (p == 0 || Piece.color(p) != us) continue
            when (Piece.type(p)) {
                Piece.PAWN -> {
                    val one = from + fwd
                    if (Sq.ok(one) && board[one] == 0) {
                        if (Sq.rank(one) == promoRank) {
                            for (pr in PROMOS) out[n++] = Move.make(from, one, pr)
                        } else if (!capturesOnly) {
                            out[n++] = Move.make(from, one)
                            val two = one + fwd
                            if (r == startRank && board[two] == 0) out[n++] = Move.make(from, two, 0, Move.DOUBLE_PUSH)
                        }
                    }
                    for (dx in intArrayOf(-1, 1)) {
                        val t = one + dx
                        if (!Sq.ok(t)) continue
                        val c = board[t]
                        if (c != 0 && Piece.color(c) == them) {
                            if (Sq.rank(t) == promoRank) for (pr in PROMOS) out[n++] = Move.make(from, t, pr, Move.CAPTURE)
                            else out[n++] = Move.make(from, t, 0, Move.CAPTURE)
                        } else if (t == ep) out[n++] = Move.make(from, t, 0, Move.CAPTURE or Move.EN_PASSANT)
                    }
                }
                Piece.KNIGHT -> n = steps(from, KNIGHT_D, us, out, n, capturesOnly)
                Piece.KING -> {
                    n = steps(from, KING_D, us, out, n, capturesOnly)
                    if (!capturesOnly) n = castles(from, us, out, n)
                }
                Piece.BISHOP -> n = slides(from, BISHOP_D, us, out, n, capturesOnly)
                Piece.ROOK -> n = slides(from, ROOK_D, us, out, n, capturesOnly)
                Piece.QUEEN -> { n = slides(from, ROOK_D, us, out, n, capturesOnly); n = slides(from, BISHOP_D, us, out, n, capturesOnly) }
            }
        }
        return n
    }

    private fun steps(from: Int, dirs: IntArray, us: Int, out: IntArray, n0: Int, capturesOnly: Boolean): Int {
        var n = n0
        for (d in dirs) {
            val t = from + d
            if (!Sq.ok(t)) continue
            val c = board[t]
            if (c == 0) { if (!capturesOnly) out[n++] = Move.make(from, t) }
            else if (Piece.color(c) != us) out[n++] = Move.make(from, t, 0, Move.CAPTURE)
        }
        return n
    }

    private fun slides(from: Int, dirs: IntArray, us: Int, out: IntArray, n0: Int, capturesOnly: Boolean): Int {
        var n = n0
        for (d in dirs) {
            var t = from + d
            while (Sq.ok(t)) {
                val c = board[t]
                if (c == 0) { if (!capturesOnly) out[n++] = Move.make(from, t) }
                else { if (Piece.color(c) != us) out[n++] = Move.make(from, t, 0, Move.CAPTURE); break }
                t += d
            }
        }
        return n
    }

    private fun castles(from: Int, us: Int, out: IntArray, n0: Int): Int {
        var n = n0
        val them = us xor 1
        val home = if (us == Piece.WHITE) 4 else 116
        if (from != home) return n
        val ks = if (us == Piece.WHITE) 1 else 4
        val qs = if (us == Piece.WHITE) 2 else 8
        val rook = Piece.of(Piece.ROOK, us)
        if (castling and ks != 0 && board[home + 1] == 0 && board[home + 2] == 0 && board[home + 3] == rook &&
            !attacked(home, them) && !attacked(home + 1, them) && !attacked(home + 2, them))
            out[n++] = Move.make(home, home + 2, 0, Move.CASTLE)
        if (castling and qs != 0 && board[home - 1] == 0 && board[home - 2] == 0 && board[home - 3] == 0 && board[home - 4] == rook &&
            !attacked(home, them) && !attacked(home - 1, them) && !attacked(home - 2, them))
            out[n++] = Move.make(home, home - 2, 0, Move.CASTLE)
        return n
    }

    /** All legal moves (allocates: for the UI and the rules; the search uses [pseudo] + [make]). */
    fun legalMoves(): IntArray {
        val buf = IntArray(256)
        val n = pseudo(buf)
        var k = 0
        for (i in 0 until n) if (make(buf[i])) { unmake(); buf[k++] = buf[i] }
        return buf.copyOf(k)
    }

    fun hasLegalMove(): Boolean {
        val buf = IntArray(256)
        val n = pseudo(buf)
        for (i in 0 until n) if (make(buf[i])) { unmake(); return true }
        return false
    }

    /** The legal move matching from/to/promotion, or 0. A missing promotion piece defaults to a queen. */
    fun find(from: Int, to: Int, promo: Int = 0): Int {
        for (m in legalMoves()) if (Move.from(m) == from && Move.to(m) == to && (Move.promo(m) == promo || (promo == 0 && Move.promo(m) == Piece.QUEEN))) return m
        return 0
    }

    /** Parses « e2e4 » / « e7e8q » among the legal moves; 0 if illegal or malformed. */
    fun parseUci(s: String): Int {
        val t = s.trim().lowercase()
        if (t.length !in 4..5) return 0
        return runCatching {
            val promo = if (t.length == 5) " pnbrqk".indexOf(t[4]).takeIf { it in 2..5 } ?: return 0 else 0
            val from = Sq.parse(t.substring(0, 2)); val to = Sq.parse(t.substring(2, 4))
            legalMoves().firstOrNull { Move.from(it) == from && Move.to(it) == to && Move.promo(it) == promo } ?: 0
        }.getOrDefault(0)
    }

    // ------------------------------------------------------------------ make / unmake

    /**
     * Plays a pseudo-legal move. Returns false (and leaves the position unchanged) if it would leave one's own king
     * in check.
     */
    fun make(m: Int): Boolean {
        val from = Move.from(m); val to = Move.to(m); val fl = Move.flags(m)
        val p = board[from]
        val us = side
        val capSq = if (fl and Move.EN_PASSANT != 0) (if (us == Piece.WHITE) to - 16 else to + 16) else to
        val captured = board[capSq]
        push(m, captured)
        var h = hash
        if (ep >= 0 && epHashed) h = h xor Z_EP[Sq.file(ep)]
        h = h xor Z_CASTLE[castling]
        // move the piece
        board[from] = 0; h = h xor zp(p, from)
        if (captured != 0) { board[capSq] = 0; h = h xor zp(captured, capSq) }
        val placed = if (Move.promo(m) != 0) Piece.of(Move.promo(m), us) else p
        board[to] = placed; h = h xor zp(placed, to)
        if (Piece.type(p) == Piece.KING) {
            kings[us] = to
            if (fl and Move.CASTLE != 0) {
                val (rf, rt) = if (to > from) (from + 3) to (from + 1) else (from - 4) to (from - 1)
                val rook = board[rf]
                board[rf] = 0; board[rt] = rook
                h = h xor zp(rook, rf) xor zp(rook, rt)
            }
        }
        castling = castling and CASTLE_MASK[from] and CASTLE_MASK[to]
        h = h xor Z_CASTLE[castling]
        ep = if (fl and Move.DOUBLE_PUSH != 0) (from + to) / 2 else -1
        epHashed = ep >= 0 && epCapturable(ep, us xor 1)
        if (epHashed) h = h xor Z_EP[Sq.file(ep)]
        halfmove = if (Piece.type(p) == Piece.PAWN || captured != 0) 0 else halfmove + 1
        if (us == Piece.BLACK) fullmove++
        side = us xor 1
        h = h xor Z_SIDE
        hash = h
        if (attacked(kings[us], side)) { unmake(); return false }
        return true
    }

    /** Is the en passant square usable by a pawn of [by] (only then does it count for repetitions)? */
    private fun epCapturable(epSq: Int, by: Int): Boolean {
        val pawn = Piece.of(Piece.PAWN, by)
        val row = if (by == Piece.WHITE) epSq - 16 else epSq + 16
        return (Sq.ok(row - 1) && board[row - 1] == pawn) || (Sq.ok(row + 1) && board[row + 1] == pawn)
    }
    private var epHashed = false

    private fun push(m: Int, captured: Int) {
        if (sp == stMove.size) {
            stMove = stMove.copyOf(sp * 2); stInfo = stInfo.copyOf(sp * 2); stHash = stHash.copyOf(sp * 2)
        }
        stMove[sp] = m
        stInfo[sp] = captured or (castling shl 4) or ((ep + 1) shl 8) or ((if (epHashed) 1 else 0) shl 16) or (halfmove shl 17)
        stHash[sp] = hash
        sp++
    }

    fun unmake() {
        check(sp > 0) { "nothing to undo" }
        sp--
        val m = stMove[sp]; val info = stInfo[sp]
        val from = Move.from(m); val to = Move.to(m); val fl = Move.flags(m)
        side = side xor 1
        val us = side
        val captured = info and 15
        castling = (info shr 4) and 15
        ep = ((info shr 8) and 0xff) - 1
        epHashed = (info shr 16) and 1 == 1
        halfmove = info shr 17
        hash = stHash[sp]
        if (us == Piece.BLACK) fullmove--
        val moved = if (Move.promo(m) != 0) Piece.of(Piece.PAWN, us) else board[to]
        board[from] = moved
        board[to] = 0
        if (fl and Move.EN_PASSANT != 0) board[if (us == Piece.WHITE) to - 16 else to + 16] = captured
        else board[to] = captured
        if (Piece.type(moved) == Piece.KING) {
            kings[us] = from
            if (fl and Move.CASTLE != 0) {
                val (rf, rt) = if (to > from) (from + 3) to (from + 1) else (from - 4) to (from - 1)
                board[rf] = board[rt]; board[rt] = 0
            }
        }
    }

    /** « Null move » for the search: passes the turn (never used when in check). */
    fun makeNull() {
        push(0, 0)
        var h = hash
        if (ep >= 0 && epHashed) h = h xor Z_EP[Sq.file(ep)]
        ep = -1; epHashed = false
        side = side xor 1
        hash = h xor Z_SIDE
        halfmove++
    }

    fun unmakeNull() {
        sp--
        val info = stInfo[sp]
        castling = (info shr 4) and 15
        ep = ((info shr 8) and 0xff) - 1
        epHashed = (info shr 16) and 1 == 1
        halfmove = info shr 17
        hash = stHash[sp]
        side = side xor 1
    }

    /** The last move played (0 if none). */
    fun lastMove(): Int = if (sp == 0) 0 else stMove[sp - 1]
    fun moveAt(i: Int): Int = stMove[i]

    // ------------------------------------------------------------------ draws

    /** How many times the current position occurred (this one included), same side to move, same rights. */
    fun repetitions(): Int {
        var count = 1
        var i = sp - 2
        val stop = maxOf(0, sp - halfmove)
        while (i >= stop) { if (stHash[i] == hash) count++; i -= 2 }
        return count
    }

    /** True once any earlier position repeats (the search treats a single repetition as a draw). */
    fun repeatedOnce(): Boolean {
        var i = sp - 2
        val stop = maxOf(0, sp - halfmove)
        while (i >= stop) { if (stHash[i] == hash) return true; i -= 2 }
        return false
    }

    /**
     * Neither side can mate by any series of legal moves: K v K, K+minor v K, K+B v K+B with bishops on the same colour
     * (any number of bishops all on one colour).
     */
    fun insufficientMaterial(): Boolean = !canMate(Piece.WHITE) && !canMate(Piece.BLACK)

    /**
     * Could [color] still mate with its material? (A lone king or a king and a single knight or bishops of one
     * colour cannot, except with help from the opponent's pieces: kept simple and on the safe side — FIDE « dead
     * position » is broader.)
     */
    fun canMate(color: Int): Boolean {
        var knights = 0; var bishopsLight = 0; var bishopsDark = 0
        var oppBishopsLight = 0; var oppBishopsDark = 0; var oppOther = 0
        for (r in 0 until 8) for (f in 0 until 8) {
            val p = board[r * 16 + f]
            if (p == 0) continue
            val t = Piece.type(p)
            if (Piece.color(p) == color) when (t) {
                Piece.PAWN, Piece.ROOK, Piece.QUEEN -> return true
                Piece.KNIGHT -> knights++
                Piece.BISHOP -> if ((r + f) % 2 == 0) bishopsDark++ else bishopsLight++
            } else when (t) {
                Piece.BISHOP -> if ((r + f) % 2 == 0) oppBishopsDark++ else oppBishopsLight++
                Piece.KING -> {}
                else -> oppOther++
            }
        }
        val minors = knights + bishopsLight + bishopsDark
        if (minors == 0) return false
        if (knights >= 2 || (knights >= 1 && bishopsLight + bishopsDark >= 1)) return true
        if (bishopsLight > 0 && bishopsDark > 0) return true
        if (knights == 1) return oppOther > 0 || oppBishopsLight + oppBishopsDark > 0   // helpmate needs a blocker
        // bishops all on one colour: mate needs an opponent piece to block, and not a bishop of the same colour only
        return oppOther > 0 || (if (bishopsLight > 0) oppBishopsDark > 0 else oppBishopsLight > 0)
    }

    // ------------------------------------------------------------------ FEN

    fun fen(): String {
        val sb = StringBuilder()
        for (r in 7 downTo 0) {
            var empty = 0
            for (f in 0 until 8) {
                val p = board[r * 16 + f]
                if (p == 0) empty++ else { if (empty > 0) { sb.append(empty); empty = 0 }; sb.append(Piece.char(p)) }
            }
            if (empty > 0) sb.append(empty)
            if (r > 0) sb.append('/')
        }
        sb.append(if (side == Piece.WHITE) " w " else " b ")
        if (castling == 0) sb.append('-') else {
            if (castling and 1 != 0) sb.append('K'); if (castling and 2 != 0) sb.append('Q')
            if (castling and 4 != 0) sb.append('k'); if (castling and 8 != 0) sb.append('q')
        }
        sb.append(' ').append(if (ep >= 0) Sq.name(ep) else "-")
        sb.append(' ').append(halfmove).append(' ').append(fullmove)
        return sb.toString()
    }

    private fun computeHash(): Long {
        var h = 0L
        for (sq in 0 until 128) if (Sq.ok(sq) && board[sq] != 0) h = h xor zp(board[sq], sq)
        h = h xor Z_CASTLE[castling]
        if (ep >= 0 && epHashed) h = h xor Z_EP[Sq.file(ep)]
        if (side == Piece.BLACK) h = h xor Z_SIDE
        return h
    }

    /** Recomputed from scratch (tests: the incremental hash must always match). */
    fun freshHash(): Long = computeHash()

    companion object {
        const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
        private val W_PAWN = Piece.of(Piece.PAWN, Piece.WHITE)
        private val B_PAWN = Piece.of(Piece.PAWN, Piece.BLACK)
        val KNIGHT_D = intArrayOf(33, 31, 18, 14, -33, -31, -18, -14)
        val KING_D = intArrayOf(1, -1, 16, -16, 15, 17, -15, -17)
        val ROOK_D = intArrayOf(1, -1, 16, -16)
        val BISHOP_D = intArrayOf(15, 17, -15, -17)
        private val PROMOS = intArrayOf(Piece.QUEEN, Piece.ROOK, Piece.BISHOP, Piece.KNIGHT)
        private val CASTLE_MASK = IntArray(128) { 15 }.also {
            it[0] = 15 and 2.inv(); it[7] = 15 and 1.inv(); it[4] = 15 and 3.inv()
            it[112] = 15 and 8.inv(); it[119] = 15 and 4.inv(); it[116] = 15 and 12.inv()
        }

        // Zobrist keys from a fixed seed (same hashes on every device: the opening book relies on it)
        private val Z_PIECE: LongArray
        private val Z_CASTLE: LongArray
        private val Z_EP: LongArray
        private val Z_SIDE: Long
        init {
            var s = 0x9E3779B97F4A7C15uL.toLong()
            fun next(): Long { s += 0x9E3779B97F4A7C15uL.toLong(); var z = s; z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L; z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L; return z xor (z ushr 31) }
            Z_PIECE = LongArray(16 * 64) { next() }
            Z_CASTLE = LongArray(16) { next() }
            Z_EP = LongArray(8) { next() }
            Z_SIDE = next()
        }
        private fun zp(p: Int, sq: Int) = Z_PIECE[p * 64 + Sq.to64(sq)]

        fun start(): Position = fromFen(START_FEN)

        /** Parses a FEN (the move counters are optional). Throws IllegalArgumentException if it is not a valid position. */
        fun fromFen(fen: String): Position {
            val parts = fen.trim().split(Regex("\\s+"))
            require(parts.size >= 4) { "FEN incomplète" }
            val p = Position()
            val rows = parts[0].split('/')
            require(rows.size == 8) { "FEN : 8 rangées attendues" }
            val kingsSeen = IntArray(2)
            for ((i, row) in rows.withIndex()) {
                val r = 7 - i
                var f = 0
                for (c in row) {
                    if (c.isDigit()) f += c - '0'
                    else {
                        require(f < 8) { "FEN : rangée trop longue" }
                        val pc = Piece.fromChar(c)
                        p.board[Sq.of(f, r)] = pc
                        if (Piece.type(pc) == Piece.KING) { kingsSeen[Piece.color(pc)]++; p.kings[Piece.color(pc)] = Sq.of(f, r) }
                        if (Piece.type(pc) == Piece.PAWN) require(r in 1..6) { "FEN : pion sur la première ou la dernière rangée" }
                        f++
                    }
                }
                require(f == 8) { "FEN : rangée ${8 - i} de longueur $f" }
            }
            require(kingsSeen[0] == 1 && kingsSeen[1] == 1) { "FEN : il faut exactement un roi de chaque couleur" }
            p.side = when (parts[1]) { "w" -> Piece.WHITE; "b" -> Piece.BLACK; else -> throw IllegalArgumentException("FEN : trait « ${parts[1]} »") }
            var c = 0
            if (parts[2] != "-") for (ch in parts[2]) c = c or when (ch) { 'K' -> 1; 'Q' -> 2; 'k' -> 4; 'q' -> 8; else -> throw IllegalArgumentException("FEN : roque « $ch »") }
            // drop rights that the board contradicts (king or rook not at home)
            if (p.board[4] != Piece.of(Piece.KING, 0)) c = c and 3.inv()
            if (p.board[7] != Piece.of(Piece.ROOK, 0)) c = c and 1.inv()
            if (p.board[0] != Piece.of(Piece.ROOK, 0)) c = c and 2.inv()
            if (p.board[116] != Piece.of(Piece.KING, 1)) c = c and 12.inv()
            if (p.board[119] != Piece.of(Piece.ROOK, 1)) c = c and 4.inv()
            if (p.board[112] != Piece.of(Piece.ROOK, 1)) c = c and 8.inv()
            p.castling = c
            p.ep = if (parts[3] == "-") -1 else Sq.parse(parts[3])
            if (p.ep >= 0) {
                require(Sq.rank(p.ep) == (if (p.side == Piece.WHITE) 5 else 2)) { "FEN : case en passant impossible" }
                p.epHashed = p.epCapturable(p.ep, p.side)
            }
            p.halfmove = parts.getOrNull(4)?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            p.fullmove = parts.getOrNull(5)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
            require(!p.attacked(p.kings[p.side xor 1], p.side)) { "FEN : le camp qui n'a pas le trait est en échec" }
            p.hash = p.computeHash()
            return p
        }
    }
}
