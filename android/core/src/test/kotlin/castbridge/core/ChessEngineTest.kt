package castbridge.core

import castbridge.core.chess.*
import kotlin.test.*

/** Move generation proven by perft on the reference positions (chessprogramming.org « Perft Results »), plus the rules. */
class ChessEngineTest {
    private fun perft(p: Position, depth: Int): Long {
        if (depth == 0) return 1
        val buf = IntArray(256)
        val n = p.pseudo(buf)
        var total = 0L
        for (i in 0 until n) {
            if (!p.make(buf[i])) continue
            assertEquals(p.freshHash(), p.hash, "incremental hash after ${Move.uci(buf[i])}")
            total += if (depth == 1) 1 else perft(p, depth - 1)
            p.unmake()
        }
        return total
    }

    private fun check(fen: String, vararg expected: Long) {
        val p = Position.fromFen(fen)
        val before = p.fen()
        for ((i, e) in expected.withIndex()) assertEquals(e, perft(p, i + 1), "perft ${i + 1} of $fen")
        assertEquals(before, p.fen(), "make/unmake restores the position")
    }

    @Test fun perftStartPosition() = check(Position.START_FEN, 20, 400, 8902, 197281)

    @Test fun perftKiwipete() = check("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1", 48, 2039, 97862, 4085603)

    @Test fun perftPosition3EnPassantAndChecks() = check("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1", 14, 191, 2812, 43238, 674624)

    @Test fun perftPosition4PromotionsAndCastling() {
        check("r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1", 6, 264, 9467, 422333)
        check("r2q1rk1/pP1p2pp/Q4n2/bbp1p3/Np6/1B3NBn/pPPP1PPP/R3K2R b KQ - 0 1", 6, 264, 9467, 422333)   // mirrored
    }

    @Test fun perftPosition5() = check("rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8", 44, 1486, 62379)

    @Test fun perftPosition6() = check("r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10", 46, 2079, 89890)

    @Test fun fenRoundTripAndValidation() {
        for (f in listOf(Position.START_FEN, "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
            "8/8/8/4k3/8/8/8/4K3 b - - 12 40", "rnbqkbnr/ppp1p1pp/8/3pPp2/8/8/PPPP1PPP/RNBQKBNR w KQkq f6 0 3"))
            assertEquals(f, Position.fromFen(f).fen())
        for (bad in listOf("", "8/8/8/8/8/8/8/8 w - - 0 1", "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP w KQkq - 0 1",
            "rnbqkbnr/pppppppp/9/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", "4k3/8/8/8/8/8/8/4K2R x - - 0 1",
            "4k2R/8/8/8/8/8/8/4K3 w - - 0 1" /* side not to move in check */))
            assertFailsWith<IllegalArgumentException>(bad) { Position.fromFen(bad) }
    }

    @Test fun castlingRulesAndRights() {
        val p = Position.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        val ucis = p.legalMoves().map(Move::uci)
        assertTrue("e1g1" in ucis && "e1c1" in ucis)
        // through an attacked square: not allowed
        val q = Position.fromFen("r3k2r/8/8/8/8/8/5r2/R3K2R w KQkq - 0 1")
        assertFalse("e1g1" in q.legalMoves().map(Move::uci), "f1 attacked")
        assertFalse("e1c1" in Position.fromFen("r3k2r/8/8/8/8/8/3r4/R3K2R w KQkq - 0 1").legalMoves().map(Move::uci), "d1 attacked")
        assertFalse("e1g1" in Position.fromFen("4k3/8/8/8/8/8/8/4K2R w - - 0 1").legalMoves().map(Move::uci), "no right")
        assertFalse("e1g1" in Position.fromFen("4k3/8/8/8/4r3/8/8/4K2R w K - 0 1").legalMoves().map(Move::uci), "in check")
        // O-O-O with b1 attacked is legal (only the king's path matters)
        assertTrue("e1c1" in Position.fromFen("1r2k3/8/8/8/8/8/8/R3K3 w Q - 0 1").legalMoves().map(Move::uci))
        // moving the rook loses the right; capturing a rook removes the opponent's
        p.make(p.parseUci("h1h8"))
        assertEquals("r3k2R/8/8/8/8/8/8/R3K3 b Qq - 0 1", p.fen())
        // castling moves the rook, unmake puts it back
        val c = Position.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        c.make(c.parseUci("e1g1")); assertEquals("r3k2r/8/8/8/8/8/8/R4RK1 b kq - 1 1", c.fen())
        c.unmake(); assertEquals("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1", c.fen())
    }

    @Test fun enPassantOnlyRightAfterTheDoublePushAndNotIntoCheck() {
        val p = Position.start()
        for (m in listOf("e2e4", "a7a6", "e4e5", "d7d5")) assertTrue(p.make(p.parseUci(m)), m)
        assertEquals("d6", Sq.name(p.ep))
        val ep = p.parseUci("e5d6")
        assertTrue(Move.isEnPassant(ep))
        assertEquals("exd6", San.of(p, ep))
        p.make(ep)
        assertEquals(0, p.pieceAt(Sq.parse("d5")), "the captured pawn is removed")
        p.unmake(); assertEquals(Piece.of(Piece.PAWN, Piece.BLACK), p.pieceAt(Sq.parse("d5")))
        // one move later the right is gone
        p.make(p.parseUci("h2h3")); p.make(p.parseUci("h7h6"))
        assertEquals(0, p.parseUci("e5d6"))
        // horizontal pin: taking en passant would expose the king
        val pin = Position.fromFen("8/8/8/KPp4r/8/8/8/7k w - c6 0 2")
        assertEquals(0, pin.parseUci("b5c6"))
    }

    @Test fun promotionWithChoiceAndSan() {
        val p = Position.fromFen("3r3k/4P3/8/8/8/8/8/K7 w - - 0 1")
        val promos = p.legalMoves().filter { Move.from(it) == Sq.parse("e7") }.map(Move::uci).toSet()
        assertEquals(setOf("e7e8q", "e7e8r", "e7e8b", "e7e8n", "e7d8q", "e7d8r", "e7d8b", "e7d8n"), promos)
        assertEquals("e8=Q+", San.of(p, p.parseUci("e7e8q")))
        assertEquals("exd8=N", San.of(p, p.parseUci("e7d8n")))
        assertEquals(p.parseUci("e7e8n"), San.parse(p, "e8=N"))
        assertEquals(p.parseUci("e7e8q"), San.parse(p, "e8Q"))
        assertEquals(p.parseUci("e7e8r"), San.parse(p, "e8r"))
        p.make(p.parseUci("e7e8r"))
        assertEquals(Piece.of(Piece.ROOK, Piece.WHITE), p.pieceAt(Sq.parse("e8")))
        p.unmake(); assertEquals(Piece.of(Piece.PAWN, Piece.WHITE), p.pieceAt(Sq.parse("e7")))
    }

    @Test fun sanDisambiguationChecksAndMates() {
        val p = Position.fromFen("4k3/8/8/8/8/8/K7/R6R w - - 0 1")
        assertEquals("Rad1", San.of(p, p.parseUci("a1d1")))
        assertEquals("Rhf1", San.of(p, p.parseUci("h1f1")))
        val r = Position.fromFen("4k3/8/8/R7/8/8/8/R3K3 w - - 0 1")
        assertEquals("R1a3", San.of(r, r.parseUci("a1a3")))
        assertEquals("R5a3", San.of(r, r.parseUci("a5a3")))
        val q = Position.fromFen("5k2/8/8/8/Q6Q/8/8/Q3K3 w - - 0 1")
        assertEquals("Qa4d4", San.of(q, q.parseUci("a4d4")), "a1 shares the file, h4 the rank")
        assertEquals("Qhe4", San.of(q, q.parseUci("h4e4")))
        assertEquals("Q1d4", San.of(q, q.parseUci("a1d4")))
        val q2 = Position.fromFen("5k2/8/8/8/Q7/8/8/Q2QK3 w - - 0 1")
        assertEquals("Qa1d4", San.of(q2, q2.parseUci("a1d4")), "file and rank both shared")
        val m = Position.fromFen("6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1")
        assertEquals("Ra8#", San.of(m, m.parseUci("a1a8")))
        assertEquals("O-O", San.of(Position.fromFen("4k3/8/8/8/8/8/8/4K2R w K - 0 1"), Position.fromFen("4k3/8/8/8/8/8/8/4K2R w K - 0 1").parseUci("e1g1")))
        val s = Position.start()
        assertEquals(s.parseUci("g1f3"), San.parse(s, "Nf3"))
        assertEquals(s.parseUci("e2e4"), San.parse(s, "e4!?"))
        assertEquals(0, San.parse(s, "Ke2"))
        assertEquals(0, San.parse(s, "zz"))
    }

    @Test fun endingsMateStalemateRepetitionFiftyAndMaterial() {
        val fool = Position.start()
        for (s in listOf("f3", "e5", "g4", "Qh4#")) assertTrue(fool.make(San.parse(fool, s)), s)
        assertEquals(GameResult.win(Piece.BLACK, EndReason.CHECKMATE), Rules.status(fool))

        assertEquals(EndReason.STALEMATE, Rules.status(Position.fromFen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1"))?.reason)

        val rep = Position.start()
        repeat(2) { for (s in listOf("Nf3", "Nf6", "Ng1", "Ng8")) { assertNull(Rules.status(rep)); rep.make(San.parse(rep, s)) } }
        assertEquals(3, rep.repetitions())
        assertEquals(EndReason.THREEFOLD, Rules.status(rep)?.reason)

        val fifty = Position.fromFen("4k3/8/8/8/8/8/8/R3K3 w - - 99 80")
        assertNull(Rules.status(fifty))
        fifty.make(fifty.parseUci("a1a2"))
        assertEquals(EndReason.FIFTY_MOVES, Rules.status(fifty)?.reason)
        // mate on the 100th half-move wins, it is not a draw
        assertEquals(EndReason.CHECKMATE, Rules.status(Position.fromFen("R5k1/5ppp/8/8/8/8/8/6K1 b - - 100 80"))?.reason)

        for (f in listOf("8/8/4k3/8/8/4K3/8/8 w - - 0 1", "8/8/4k3/8/8/4K3/5N2/8 w - - 0 1", "8/8/4k3/8/8/4K3/5B2/8 w - - 0 1",
            "8/2b5/4k3/8/8/4K3/7B/8 w - - 0 1" /* both bishops on dark squares */))
            assertEquals(EndReason.INSUFFICIENT, Rules.status(Position.fromFen(f))?.reason, f)
        for (f in listOf("8/8/4k3/8/8/4K3/5P2/8 w - - 0 1", "8/8/4k3/8/8/4K3/4NN2/8 w - - 0 1", "8/3b4/4k3/8/8/4K3/7B/8 w - - 0 1"))
            assertNull(Rules.status(Position.fromFen(f)), f)
    }

    @Test fun timeoutLosesUnlessTheOpponentCannotMate() {
        assertEquals(GameResult.win(Piece.BLACK, EndReason.TIMEOUT), Rules.timeout(Position.start(), Piece.WHITE))
        // white flags, but black has a bare king: draw
        assertEquals(EndReason.TIMEOUT_DRAW, Rules.timeout(Position.fromFen("4k3/8/8/8/8/8/8/R3K3 w - - 0 1"), Piece.WHITE).reason)
        assertEquals(Outcome.WHITE_WINS, Rules.timeout(Position.fromFen("4k3/8/8/8/8/8/8/R3K3 b - - 0 1"), Piece.BLACK).outcome)
    }

    @Test fun pgnWriteAndReadBack() {
        val p = Position.start()
        val sans = ArrayList<String>()
        for (s in listOf("e4", "e5", "Nf3", "Nc6", "Bb5", "a6", "Ba4", "Nf6", "O-O", "Be7")) { val m = San.parse(p, s); sans += San.of(p, m); p.make(m) }
        val pgn = Pgn.write(mapOf("Event" to "Partie sur la TV", "White" to "Esaie", "Black" to "Ordinateur (niveau 3)"), sans, "*")
        assertTrue(pgn.contains("[White \"Esaie\"]") && pgn.contains("1. e4 e5 2. Nf3 Nc6") && pgn.contains("5. O-O Be7 *"))
        val g = Pgn.read(pgn)
        assertEquals("Esaie", g.tags["White"])
        assertEquals(10, g.moves.size)
        assertEquals("e1g1", g.moves[8])
        // comments, variations, NAGs, results, and a FEN start
        val tricky = """
            [Event "Test"]
            [SetUp "1"]
            [FEN "4k3/8/8/8/8/8/4P3/4K3 w - - 0 1"]
            1. e4 {le pion avance} Kd7 (1... Ke7 2. e5) 2. e5 $1 ; commentaire
            Kc6 1/2-1/2
        """.trimIndent()
        val t = Pgn.read(tricky)
        assertEquals(listOf("e2e4", "e8d7", "e4e5", "d7c6"), t.moves)
        assertEquals("1/2-1/2", t.result)
        // black to move first: « 1... » numbering
        assertTrue(Pgn.write(emptyMap(), listOf("Kd7"), "*", "4k3/8/8/8/4P3/8/8/4K3 b - - 0 1").contains("1... Kd7 *"))
        assertFailsWith<IllegalArgumentException> { Pgn.read("1. e4 e5 2. Ke3") }
    }

    @Test fun uciParsingRejectsIllegalAndMalformed() {
        val p = Position.start()
        assertNotEquals(0, p.parseUci("e2e4"))
        for (bad in listOf("e2e5", "e7e5", "", "zz", "e2e4qq", "e1e2", "e2e4k")) assertEquals(0, p.parseUci(bad), bad)
    }
}
