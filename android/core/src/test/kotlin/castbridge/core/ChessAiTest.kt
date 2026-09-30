package castbridge.core

import castbridge.core.chess.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.*

class ChessAiTest {
    private val ai = ChessAi(random = java.util.Random(1))

    private fun mated(p: Position) = Rules.status(p)?.reason == EndReason.CHECKMATE

    @Test fun findsMateInOneAtEveryLevel() {
        for (fen in listOf("6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1", "r1bqkb1r/pppp1ppp/2n2n2/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 4 4",
            "r5k1/8/8/8/8/8/5PPP/6K1 b - - 0 1")) {
            for (level in 1..8) {
                val p = Position.fromFen(fen)
                val r = ai.think(p, level, useBook = false)
                assertTrue(r.move in p.legalMoves(), "legal")
                p.make(r.move)
                assertTrue(mated(p), "level $level mates in one in $fen, played ${r.uci}")
            }
        }
    }

    @Test fun findsMateInTwoFromLevelThree() {
        for (fen in listOf("7k/8/8/8/8/8/R7/1R4K1 w - - 0 1", "kbK5/pp6/1P6/8/8/8/8/R7 w - - 0 1")) {
            for (level in 3..8) {
                val p = Position.fromFen(fen)
                val r = ai.think(p, level, useBook = false)
                assertTrue(r.score >= ChessAi.MATE_BOUND, "level $level sees the mate in $fen (score ${r.score}, ${r.uci})")
                p.make(r.move)
                // whatever the defence, a mate in one follows
                for (reply in p.legalMoves()) {
                    p.make(reply)
                    val m = ai.think(p, level, useBook = false)
                    p.make(m.move)
                    assertTrue(mated(p), "level $level: after ${r.uci} ${Move.uci(reply)}, ${m.uci} mates")
                    p.unmake(); p.unmake()
                }
            }
        }
    }

    @Test fun takesAHangingQueenEvenAtLevelOne() {
        val p = Position.fromFen("4k3/8/8/3q4/8/8/8/3RK3 w - - 0 1")
        repeat(5) { assertEquals("d1d5", ChessAi(random = java.util.Random(it.toLong())).think(p, 1, useBook = false).uci) }
    }

    @Test fun onlyLegalMovesInSelfPlayAndTheGameEnds() {
        for (seed in 1..3L) {
            val a = ChessAi(ttBits = 14, random = java.util.Random(seed))
            val p = Position.start()
            var plies = 0
            while (Rules.status(p) == null && plies < 160) {
                val r = a.think(p, level = if (plies % 2 == 0) 2 else 1, maxTimeMs = 200)
                assertTrue(r.move in p.legalMoves(), "legal move at ply $plies: ${r.uci} in ${p.fen()}")
                assertTrue(p.make(r.move))
                plies++
            }
            assertTrue(plies > 4)
        }
    }

    @Test fun respectsTheTimeAndNeverExceedsThreeSeconds() {
        val mid = "r1bq1rk1/pp2bppp/2n1pn2/2pp4/2PP4/2NBPN2/PP3PPP/R1BQ1RK1 w - - 0 8"
        for ((level, limit) in listOf(8 to 400L, 7 to 800L, 6 to 10_000L)) {
            val t0 = System.nanoTime()
            val r = ai.think(Position.fromFen(mid), level, maxTimeMs = limit, useBook = false)
            val ms = (System.nanoTime() - t0) / 1_000_000
            assertTrue(ms <= minOf(limit, ChessAi.HARD_CAP_MS) + 300, "level $level took $ms ms for a $limit ms allowance")
            assertTrue(r.move in Position.fromFen(mid).legalMoves())
            assertTrue(r.depth >= 1, "at least one full iteration")
        }
        assertEquals(3_000, ChessAi.allowance(60_000))
        assertEquals(3_000, ChessAi.allowance(10_000))
        assertTrue(ChessAi.allowance(10_000) < 10_000 && ChessAi.allowance(1_000) <= 1_000 / 3 + ChessAi.MIN_TIME_MS)
        assertTrue(ChessAi.LEVELS.all { it.timeMs <= ChessAi.HARD_CAP_MS })
    }

    /** Speed reference printed in the test log (the TV's 32-bit ARM is expected ~10-20× slower than a desktop JVM). */
    @Test fun speedReference() {
        val r = ChessAi().think(Position.fromFen("r1bq1rk1/pp2bppp/2n1pn2/2pp4/2PP4/2NBPN2/PP3PPP/R1BQ1RK1 w - - 0 8"), 8, maxTimeMs = 1_500, useBook = false)
        println("chess AI speed: depth ${r.depth}, ${r.nodes} nodes in ${r.timeMs} ms = ${r.nodes * 1000 / maxOf(1, r.timeMs)} nodes/s, move ${r.uci}")
        assertTrue(r.depth >= 4)
    }

    @Test fun stopFlagAbortsQuickly() {
        val stop = AtomicBoolean(true)
        val t0 = System.nanoTime()
        val r = ai.think(Position.start(), 8, stop = stop, useBook = false)
        assertTrue((System.nanoTime() - t0) / 1_000_000 < 500)
        assertTrue(r.move in Position.start().legalMoves())
    }

    @Test fun transpositionTableStaysWithin16MiB() {
        assertEquals(4L shl 20, ChessAi().memoryBytes)
        assertEquals(16L shl 20, ChessAi(ttBits = 30).memoryBytes, "capped")
    }

    @Test fun openingBookIsLegalAndUsed() {
        assertTrue(OpeningBook.LINES.size >= 30)
        assertTrue(OpeningBook.size() > 150, "positions: ${OpeningBook.size()}")
        val p = Position.start()
        val r = ai.think(p, 5)
        assertTrue(r.fromBook)
        assertTrue(r.uci in setOf("e2e4", "d2d4", "c2c4", "g1f3"))
        // out of book: a normal search
        assertFalse(ai.think(Position.fromFen("4k3/8/8/8/8/8/4P3/4K3 w - - 0 1"), 5).fromBook)
    }

    @Test fun evaluationIsSymmetric() {
        for (fen in listOf(Position.START_FEN, "r1bq1rk1/pp2bppp/2n1pn2/2pp4/2PP4/2NBPN2/PP3PPP/R1BQ1RK1 w - - 0 8")) {
            val p = Position.fromFen(fen)
            val mirrored = mirror(fen)
            assertEquals(ChessAi.evaluate(p), ChessAi.evaluate(Position.fromFen(mirrored)), fen)
        }
    }

    private fun mirror(fen: String): String {
        val parts = fen.split(' ')
        val rows = parts[0].split('/').reversed().joinToString("/") { r -> r.map { if (it.isLetter()) (if (it.isUpperCase()) it.lowercaseChar() else it.uppercaseChar()) else it }.joinToString("") }
        val side = if (parts[1] == "w") "b" else "w"
        val castle = if (parts[2] == "-") "-" else parts[2].map { if (it.isUpperCase()) it.lowercaseChar() else it.uppercaseChar() }.sortedBy { "KQkq".indexOf(it) }.joinToString("")
        return "${rows} $side $castle - 0 1"
    }
}
