package castbridge.core.wallet.ui

import castbridge.core.tokens.FrenchNumbers
import castbridge.core.wallet.WalletCurrency
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AmountWordsTest {
    @Test fun smallAndTricky() {
        val expected = mapOf(
            0L to "zéro", 1L to "un", 16L to "seize", 21L to "vingt et un", 71L to "soixante et onze", 80L to "quatre-vingts", 81L to "quatre-vingt-un", 99L to "quatre-vingt-dix-neuf",
            100L to "cent", 101L to "cent un", 200L to "deux cents", 201L to "deux cent un", 1000L to "mille", 1001L to "mille un", 3450L to "trois mille quatre cent cinquante",
            10_000L to "dix mille", 12_000L to "douze mille", 21_000L to "vingt et un mille", 80_000L to "quatre-vingt mille", 200_000L to "deux cent mille", 999_999L to "neuf cent quatre-vingt-dix-neuf mille neuf cent quatre-vingt-dix-neuf",
        )
        expected.forEach { (n, w) -> assertEquals(w, AmountWords.words(n), "n=$n") }
    }

    @Test fun millionsAndMilliards() {
        assertEquals("un million", AmountWords.words(1_000_000))
        assertEquals("deux millions", AmountWords.words(2_000_000))
        assertEquals("un million deux cent mille", AmountWords.words(1_200_000))
        assertEquals("quatre-vingts millions", AmountWords.words(80_000_000))
        assertEquals("deux cents millions", AmountWords.words(200_000_000))
        assertEquals("un milliard un", AmountWords.words(1_000_000_001))
        assertEquals("deux milliards", AmountWords.words(2_000_000_000))
    }

    @Test fun agreesWithTheExistingReaderUpToTenThousand() {
        for (n in 0..10_000L) assertEquals(FrenchNumbers.words(n), AmountWords.words(n), "n=$n")
    }

    @Test fun negativeIsRefused() { assertFailsWith<IllegalArgumentException> { AmountWords.words(-1) } }

    @Test fun confirmationShowsDigitsAndWords() {
        assertEquals("3 450 NDEM (trois mille quatre cent cinquante)", AmountWords.confirmation(3450, WalletCurrency.NDEM))
        assertEquals("12 MBOKO (douze)", AmountWords.confirmation(12, WalletCurrency.MBOKO))
        // au-delà de 10 000 : encore en lettres (l'ancien texte s'arrêtait là)
        assertEquals("12 000 NDEM (douze mille)", AmountWords.confirmation(12_000, WalletCurrency.NDEM))
    }
}
