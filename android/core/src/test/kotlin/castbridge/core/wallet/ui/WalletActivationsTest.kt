package castbridge.core.wallet.ui

import castbridge.core.wallet.ui.WalletActivations.Candidate
import kotlin.test.Test
import kotlin.test.assertEquals

class WalletActivationsTest {
    private fun c(t: String, p: Int, at: Long) = Candidate(t, p, at)

    @Test fun bestPriorityFirstThenNewest() {
        val all = listOf(c("rental-old", 3, 1), c("prod", 1, 5), c("super", 0, 2), c("rental-new", 3, 9), c("trial", 2, 7))
        assertEquals(listOf("super", "prod", "trial", "rental-new"), WalletActivations.pick(all))
    }

    @Test fun atMostFourDistinctNonBlank() {
        val all = (1..9).map { c("t$it", 1, it.toLong()) } + c("t9", 1, 99) + c("  ", 0, 1) + c("", 0, 1)
        val p = WalletActivations.pick(all)
        assertEquals(4, p.size); assertEquals(p.distinct(), p)
        assertEquals(listOf("t9", "t8", "t7", "t6"), p)
    }

    @Test fun totalSizeStaysUnderTheServerLimit() {
        val big = "x".repeat(5_000)
        val p = WalletActivations.pick(listOf(c("a$big", 0, 3), c("b$big", 1, 2), c("small", 2, 1)))
        assertEquals(listOf("a$big", "small"), p)                                          // la deuxième grosse ne tient pas : on saute, la petite passe
        assertEquals(true, p.sumOf { it.length } <= WalletActivations.MAX_CHARS)
    }

    @Test fun noActivationNoToken() { assertEquals(emptyList(), WalletActivations.pick(emptyList())) }
}
