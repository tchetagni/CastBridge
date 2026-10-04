package castbridge.core.wallet.ui

import castbridge.core.wallet.Snapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConvertPreviewTest {
    private fun snap(n: Long, m: Long, nb: Long = 0, mb: Long = 0) = Snapshot("0123456789abcdef", "7K3M-9PQ2-XH4T-V8RM", "PROD", n, nb, m, mb, 7, 1_790_000_000_000L, Snapshot.Flags(false, true, true))
    private fun policy(rate: Long = 1000, bp: Long = 200) = PolicyView(rate, bp, true, true, true, true, true, 5000, 50)

    @Test fun ndemToMbokoCostsRateTimesQuantityWithoutFee() {
        val p = ConvertPreview.of(ConvertDir.N2M, 12, policy(), snap(15_000, 3))
        assertEquals("NDEM", p.payCur); assertEquals(12_000, p.pay); assertEquals("MBOKO", p.gainCur); assertEquals(12, p.gain); assertEquals(0, p.fee)
        assertEquals(15_000, p.beforeN); assertEquals(3, p.beforeM); assertEquals(3_000, p.afterN); assertEquals(15, p.afterM); assertTrue(p.enough)
    }

    @Test fun notEnoughNdem() {
        val p = ConvertPreview.of(ConvertDir.N2M, 16, policy(), snap(15_000, 3))
        assertFalse(p.enough)
    }

    @Test fun mbokoToNdemTakesTheFee() {
        val p = ConvertPreview.of(ConvertDir.M2N, 5, policy(bp = 200), snap(100, 8))
        assertEquals("MBOKO", p.payCur); assertEquals(5, p.pay); assertEquals("NDEM", p.gainCur); assertEquals(4_900, p.gain); assertEquals(100, p.fee)   // 5 000 NDEM - 2 % : cas du rapport w22-05
        assertEquals(3, p.afterM); assertEquals(5_000, p.afterN); assertTrue(p.enough)
    }

    @Test fun feeRoundsAgainstThePlayer() {
        val p = ConvertPreview.of(ConvertDir.M2N, 1, policy(bp = 333), snap(0, 1))
        assertEquals(34, p.fee); assertEquals(966, p.gain)                                 // ceil(1000 × 333 / 10 000) = 34
        assertEquals(0, ConvertPreview.of(ConvertDir.M2N, 1, policy(bp = 0), snap(0, 1)).fee)
    }

    @Test fun notEnoughMboko() { assertFalse(ConvertPreview.of(ConvertDir.M2N, 5, policy(), snap(0, 3)).enough) }

    @Test fun blockedTokensAreNeverSpendable() {
        // 1 MBOKO disponible + 9 bloqués : on ne peut convertir que 1
        assertTrue(ConvertPreview.of(ConvertDir.M2N, 1, policy(), snap(0, 1, mb = 9)).enough)
        assertFalse(ConvertPreview.of(ConvertDir.M2N, 2, policy(), snap(0, 1, mb = 9)).enough)
    }

    @Test fun otherRateIsRead() {
        val p = ConvertPreview.of(ConvertDir.N2M, 2, policy(rate = 500), snap(1000, 0))
        assertEquals(1_000, p.pay); assertEquals(0, p.afterN); assertTrue(p.enough)
    }

    @Test fun overflowIsRefusedNotWrapped() {
        val p = ConvertPreview.of(ConvertDir.N2M, 1_000_000_000L, policy(rate = 1_000_000), snap(1, 0))
        assertFalse(p.enough)
        assertTrue(p.pay > 0)
    }
}
