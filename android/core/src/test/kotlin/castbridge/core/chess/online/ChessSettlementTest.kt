package castbridge.core.chess.online

import castbridge.core.chess.EndReason
import castbridge.core.chess.GameResult
import castbridge.core.chess.Piece
import castbridge.core.owner.Ed25519Signer
import castbridge.core.owner.KeyRing
import castbridge.core.wallet.PlayResult
import castbridge.core.wallet.Verdict
import castbridge.core.wallet.WalletCurrency
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Le règlement des échecs misés, pur : gagnant = deux mises, nulle = remboursement, forfait = mise perdue, interruption = tout rendu ; toujours Σ payé = Σ utilisé. */
class ChessSettlementTest {
    private val signer = Ed25519Signer(MessageDigest.getInstance("SHA-256").digest("castbridge-chess-settlement-test".toByteArray()))
    private val ring = KeyRing(listOf(signer.trusted()))
    private val w = ChessEscrow("EidWWWWWWWWWWWWWWWWWWW", "WWWW-WWWW-WWWW-WWWW", 50)
    private val b = ChessEscrow("EidBBBBBBBBBBBBBBBBBBB", "BBBB-BBBB-BBBB-BBBB", 50)

    private fun lines(o: GameResult?, white: ChessEscrow? = w, black: ChessEscrow? = b) =
        ChessSettlement.result(signer.keyId, "room-1", WalletCurrency.NDEM, 50, 123L, white, black, o).lines.map { Triple(it.id.first(), it.used, it.pay) }

    @Test fun everyDecisiveReasonPaysTheWinnerBothStakes() {
        for (reason in listOf(EndReason.CHECKMATE, EndReason.TIMEOUT, EndReason.RESIGNATION, EndReason.FORFEIT)) {
            assertEquals(listOf(Triple('W', 50L, 100L), Triple('B', 50L, 0L)), lines(GameResult.win(Piece.WHITE, reason)), "$reason")
            assertEquals(listOf(Triple('W', 50L, 0L), Triple('B', 50L, 100L)), lines(GameResult.win(Piece.BLACK, reason)), "$reason")
        }
    }

    @Test fun everyDrawRefundsBothStakes() {
        for (reason in listOf(EndReason.AGREEMENT, EndReason.STALEMATE, EndReason.FIFTY_MOVES, EndReason.THREEFOLD, EndReason.INSUFFICIENT, EndReason.TIMEOUT_DRAW))
            assertEquals(listOf(Triple('W', 50L, 50L), Triple('B', 50L, 50L)), lines(GameResult.draw(reason)), "$reason")
    }

    @Test fun anInterruptedGameUsesNothingAndRefundsEveryEscrowThatExists() {
        assertEquals(listOf(Triple('W', 0L, 0L), Triple('B', 0L, 0L)), lines(null))
        assertEquals(listOf(Triple('W', 0L, 0L)), lines(null, black = null), "l'hôte seul : son blocage est rendu")
        val r = ChessSettlement.result(signer.keyId, "room-1", WalletCurrency.NDEM, 50, 1L, w, b, null)
        assertEquals(PlayResult.Kind.ABORT, r.kind)
    }

    @Test fun conservationHoldsInEveryCaseAndTheResultIsSignedAndReadBack() {
        val cases = listOf(GameResult.win(Piece.WHITE, EndReason.CHECKMATE), GameResult.win(Piece.BLACK, EndReason.FORFEIT), GameResult.draw(EndReason.AGREEMENT), null)
        for (c in cases) {
            val r = ChessSettlement.result(signer.keyId, "room-9", WalletCurrency.MBOKO, 5, 77L, w, b, c)
            assertEquals(r.lines.sumOf { it.used }, r.lines.sumOf { it.pay }, "Σ payé = Σ utilisé")
            assertTrue(r.check())
            val token = PlayResult.sign(r, signer)
            val back = PlayResult.verify(token, ring)
            assertTrue(back is Verdict.Accepted && back.value == r, "lu et vérifié tel quel : $c")
        }
    }

    @Test fun aDecisiveResultNeedsBothEscrows() {
        assertFailsWith<IllegalArgumentException> { lines(GameResult.win(Piece.WHITE, EndReason.CHECKMATE), black = null) }
        assertFailsWith<IllegalArgumentException> { ChessSettlement.result(signer.keyId, "r", WalletCurrency.NDEM, 50, 1, null, null, null) }
    }

    @Test fun theResultIdOfARoomIsDeterministicHexAndDiffersBetweenRooms() {
        val a = ChessSettlement.ridOf("0123456789abcdef0123456789abcdef")
        assertEquals(a, ChessSettlement.ridOf("0123456789abcdef0123456789abcdef"))
        assertTrue(Regex("^[0-9a-f]{32}$").matches(a)); assertNotEquals(a, ChessSettlement.ridOf("fedcba9876543210fedcba9876543210"))
    }

    @Test fun netGainIsWhatTheScreensShow() {
        val mate = GameResult.win(Piece.WHITE, EndReason.CHECKMATE)
        assertEquals(50L, ChessSettlement.net(50, mate, Piece.WHITE)); assertEquals(-50L, ChessSettlement.net(50, mate, Piece.BLACK))
        assertEquals(0L, ChessSettlement.net(50, GameResult.draw(EndReason.STALEMATE), Piece.BLACK))
    }

    @Test fun theDefaultScaleIsTheOwnersScale() {
        assertEquals(listOf(10L, 20L, 50L, 100L, 200L), ChessStakeScale.of(WalletCurrency.NDEM))
        assertEquals(listOf(1L, 2L, 5L, 10L), ChessStakeScale.of(WalletCurrency.MBOKO))
    }
}
