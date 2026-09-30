package castbridge.core.chess

/** Standard Algebraic Notation (SAN) in and out, with English piece letters (K Q R B N) as in PGN. */
object San {
    /** SAN of legal move [m] in [pos] (with + or #). */
    fun of(pos: Position, m: Int): String {
        val sb = StringBuilder()
        val from = Move.from(m); val to = Move.to(m)
        val p = pos.pieceAt(from)
        val t = Piece.type(p)
        if (Move.isCastle(m)) sb.append(if (to > from) "O-O" else "O-O-O")
        else {
            if (t == Piece.PAWN) {
                if (Move.isCapture(m)) sb.append('a' + Sq.file(from)).append('x')
                sb.append(Sq.name(to))
                if (Move.promo(m) != 0) sb.append('=').append(" PNBRQK"[Move.promo(m)])
            } else {
                sb.append(" PNBRQK"[t])
                val rivals = pos.legalMoves().filter { it != m && Move.to(it) == to && pos.pieceAt(Move.from(it)) == p }
                if (rivals.isNotEmpty()) {
                    val sameFile = rivals.any { Sq.file(Move.from(it)) == Sq.file(from) }
                    val sameRank = rivals.any { Sq.rank(Move.from(it)) == Sq.rank(from) }
                    if (!sameFile) sb.append('a' + Sq.file(from))
                    else if (!sameRank) sb.append('1' + Sq.rank(from))
                    else sb.append(Sq.name(from))
                }
                if (Move.isCapture(m)) sb.append('x')
                sb.append(Sq.name(to))
            }
        }
        if (pos.make(m)) {
            if (pos.inCheck()) sb.append(if (pos.hasLegalMove()) '+' else '#')
            pos.unmake()
        }
        return sb.toString()
    }

    private fun norm(s: String): String = s.trim().replace("0", "O").replace("=", "").replace("+", "").replace("#", "")
        .replace("!", "").replace("?", "").replace("e.p.", "").trim()

    /** Legal move for a SAN (tolerant: 0-0, e8Q, missing +/#, trailing !?) or a UCI string; 0 if none. */
    fun parse(pos: Position, text: String): Int {
        val n = norm(text)
        if (n.isEmpty()) return 0
        val legal = pos.legalMoves()
        for (m in legal) if (norm(of(pos, m)) == n) return m
        // promotion letter in lower case (e8q), or a piece letter in lower case except b (bishop vs b-file)
        val up = if (n.length >= 3 && n.last() in "qrnb" && n[n.length - 2].isDigit()) n.dropLast(1) + n.last().uppercaseChar() else n
        if (up != n) for (m in legal) if (norm(of(pos, m)) == up) return m
        return pos.parseUci(text)
    }
}

enum class Outcome { WHITE_WINS, BLACK_WINS, DRAW }

/** Why a game ended. [text] is the French wording shown on the screens. */
enum class EndReason(val text: String) {
    CHECKMATE("Échec et mat"),
    STALEMATE("Pat"),
    TIMEOUT("Temps dépassé"),
    TIMEOUT_DRAW("Temps dépassé, mais l'adversaire ne peut plus mater"),
    RESIGNATION("Abandon"),
    FIFTY_MOVES("Règle des 50 coups"),
    THREEFOLD("Triple répétition"),
    INSUFFICIENT("Matériel insuffisant"),
    AGREEMENT("Nulle d'un commun accord"),
    ABANDONED("Partie interrompue"),
}

data class GameResult(val outcome: Outcome, val reason: EndReason) {
    val winner: Int? get() = when (outcome) { Outcome.WHITE_WINS -> Piece.WHITE; Outcome.BLACK_WINS -> Piece.BLACK; Outcome.DRAW -> null }
    /** PGN result token. */
    val pgn: String get() = when (outcome) { Outcome.WHITE_WINS -> "1-0"; Outcome.BLACK_WINS -> "0-1"; Outcome.DRAW -> "1/2-1/2" }
    /** e.g. « Échec et mat : les Blancs gagnent », « Pat : partie nulle ». */
    val text: String get() = reason.text + " : " + when (outcome) {
        Outcome.WHITE_WINS -> "les Blancs gagnent"; Outcome.BLACK_WINS -> "les Noirs gagnent"; Outcome.DRAW -> "partie nulle"
    }

    companion object {
        fun win(color: Int, reason: EndReason) = GameResult(if (color == Piece.WHITE) Outcome.WHITE_WINS else Outcome.BLACK_WINS, reason)
        fun draw(reason: EndReason) = GameResult(Outcome.DRAW, reason)
    }
}

object Rules {
    /**
     * Result decided by the board alone, or null if play goes on: mate, stalemate, insufficient material, threefold
     * repetition and the 50-move rule. The two last ones are applied automatically (no claim needed on a TV).
     */
    fun status(pos: Position): GameResult? {
        if (!pos.hasLegalMove()) return if (pos.inCheck()) GameResult.win(pos.side xor 1, EndReason.CHECKMATE) else GameResult.draw(EndReason.STALEMATE)
        if (pos.insufficientMaterial()) return GameResult.draw(EndReason.INSUFFICIENT)
        if (pos.repetitions() >= 3) return GameResult.draw(EndReason.THREEFOLD)
        if (pos.halfmove >= 100) return GameResult.draw(EndReason.FIFTY_MOVES)
        return null
    }

    /**
     * The side to move ran out of time: it loses, unless the opponent could not mate by any series of legal moves
     * (FIDE 6.9), then the game is drawn.
     */
    fun timeout(pos: Position, flagged: Int): GameResult =
        if (pos.canMate(flagged xor 1)) GameResult.win(flagged xor 1, EndReason.TIMEOUT) else GameResult.draw(EndReason.TIMEOUT_DRAW)
}

/** A game read from PGN. [moves] are in UCI form, replayable from [startFen]. */
data class PgnGame(val tags: Map<String, String>, val startFen: String, val moves: List<String>, val result: String)

object Pgn {
    private val TAG = Regex("""\[\s*(\w+)\s+"((?:[^"\\]|\\.)*)"\s*]""")

    /** A PGN of the game [sanMoves] played from [startFen]. Tags in the standard order (Seven Tag Roster first). */
    fun write(tags: Map<String, String>, sanMoves: List<String>, result: String = "*", startFen: String = Position.START_FEN): String {
        val sb = StringBuilder()
        val all = LinkedHashMap<String, String>()
        for (k in listOf("Event", "Site", "Date", "Round", "White", "Black")) all[k] = tags[k] ?: if (k == "Date") "????.??.??" else "?"
        all["Result"] = result
        if (startFen != Position.START_FEN) { all["SetUp"] = "1"; all["FEN"] = startFen }
        for ((k, v) in tags) if (k !in all) all[k] = v
        for ((k, v) in all) sb.append('[').append(k).append(" \"").append(v.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"]\n")
        sb.append('\n')
        val start = Position.fromFen(startFen)
        var number = start.fullmove
        var white = start.side == Piece.WHITE
        val tokens = ArrayList<String>()
        for ((i, s) in sanMoves.withIndex()) {
            if (white) tokens += "$number. $s" else { if (i == 0) tokens += "$number... $s" else tokens += s; number++ }
            white = !white
        }
        tokens += result
        var line = 0
        for ((i, t) in tokens.withIndex()) {
            if (i > 0) { if (line + 1 + t.length > 79) { sb.append('\n'); line = 0 } else { sb.append(' '); line++ } }
            sb.append(t); line += t.length
        }
        sb.append('\n')
        return sb.toString()
    }

    /** Reads the first game of a PGN: tags, then moves (comments, variations, NAGs and move numbers skipped). */
    fun read(text: String): PgnGame {
        val tags = LinkedHashMap<String, String>()
        TAG.findAll(text).forEach { tags[it.groupValues[1]] = it.groupValues[2].replace("\\\"", "\"").replace("\\\\", "\\") }
        val body = StringBuilder()
        val movetext = text.lines().filterNot { it.trimStart().startsWith("[") || it.trimStart().startsWith("%") }.joinToString("\n")
        var depth = 0; var comment = false; var lineComment = false
        for (c in movetext) {
            when {
                lineComment -> if (c == '\n') { lineComment = false; body.append(' ') }
                comment -> if (c == '}') comment = false
                c == '{' -> comment = true
                c == ';' -> lineComment = true
                c == '(' -> depth++
                c == ')' -> depth = maxOf(0, depth - 1)
                depth > 0 -> {}
                else -> body.append(c)
            }
        }
        val fen = if (tags["SetUp"] == "1" || tags["FEN"] != null) tags["FEN"] ?: Position.START_FEN else Position.START_FEN
        val pos = Position.fromFen(fen)
        val moves = ArrayList<String>()
        var result = tags["Result"] ?: "*"
        for (raw in body.split(Regex("\\s+"))) {
            var tok = raw.trim()
            if (tok.isEmpty()) continue
            if (tok in RESULTS) { result = tok; break }
            if (tok.startsWith("$")) continue
            tok = tok.replace(Regex("^\\d+\\.+"), "")          // "12." "12..." "1.e4"
            if (tok.isEmpty()) continue
            val m = San.parse(pos, tok)
            require(m != 0) { "PGN : coup illégal ou illisible « $tok » (coup ${moves.size / 2 + 1})" }
            moves += Move.uci(m)
            pos.make(m)
        }
        return PgnGame(tags, fen, moves, result)
    }

    private val RESULTS = setOf("1-0", "0-1", "1/2-1/2", "*")
}
