package castbridge.core.wallet.ui

import castbridge.core.wallet.WalletCurrency
import castbridge.core.wallet.WalletView

/**
 * Les montants en toutes lettres, jusqu'à 999 999 999 999 (le plafond des montants du portefeuille est 10¹²), français standard : « quatre-vingts », « deux cent mille », « quatre-vingts millions ».
 * Pour 0..10 000 le texte est identique à celui de `FrenchNumbers` (test de cohérence) ; celui-ci va plus loin, car une conversion de 12 000 NDEM doit se confirmer en lettres aussi.
 */
object AmountWords {
    private val UNITS = listOf("zéro", "un", "deux", "trois", "quatre", "cinq", "six", "sept", "huit", "neuf", "dix", "onze", "douze", "treize", "quatorze", "quinze", "seize", "dix-sept", "dix-huit", "dix-neuf")
    private val TENS = listOf("", "", "vingt", "trente", "quarante", "cinquante", "soixante")
    const val MAX = 999_999_999_999L

    fun words(n: Long): String {
        require(n in 0..MAX) { "montant hors de 0..$MAX" }
        if (n == 0L) return "zéro"
        val milliards = (n / 1_000_000_000L).toInt()
        val millions = ((n / 1_000_000L) % 1000).toInt()
        val milliers = ((n / 1000L) % 1000).toInt()
        val reste = (n % 1000).toInt()
        val parts = ArrayList<String>()
        // « million » et « milliard » sont des noms (pluriel : « quatre-vingts millions ») ; « mille » est invariable (« quatre-vingt mille », « deux cent mille »)
        if (milliards > 0) parts += below1000(milliards, true) + " milliard" + (if (milliards > 1) "s" else "")
        if (millions > 0) parts += below1000(millions, true) + " million" + (if (millions > 1) "s" else "")
        if (milliers > 0) parts += if (milliers == 1) "mille" else below1000(milliers, false) + " mille"
        if (reste > 0) parts += below1000(reste, true)
        return parts.joinToString(" ")
    }

    /** [final] : le groupe termine le nombre ou précède un nom (« deux cents », « quatre-vingts » prennent leur s). */
    private fun below1000(n: Int, final: Boolean): String {
        if (n < 100) return below100(n, final)
        val h = n / 100; val r = n % 100
        val head = if (h == 1) "cent" else "${UNITS[h]} cent" + if (r == 0 && final) "s" else ""
        return if (r == 0) head else "$head ${below100(r, final)}"
    }

    private fun below100(n: Int, final: Boolean): String = when {
        n < 20 -> UNITS[n]
        n < 70 -> TENS[n / 10] + when (n % 10) { 0 -> ""; 1 -> " et un"; else -> "-${UNITS[n % 10]}" }
        n < 80 -> "soixante" + if (n == 71) " et onze" else "-${UNITS[n - 60]}"
        n == 80 -> if (final) "quatre-vingts" else "quatre-vingt"
        else -> "quatre-vingt-${UNITS[n - 80]}"
    }

    /** « 3 450 NDEM (trois mille quatre cent cinquante) » : le montant en chiffres ET en lettres, pour toute confirmation. */
    fun confirmation(n: Long, cur: WalletCurrency): String = "${WalletView.thousands(n)} ${cur.name} (${words(n)})"
}
