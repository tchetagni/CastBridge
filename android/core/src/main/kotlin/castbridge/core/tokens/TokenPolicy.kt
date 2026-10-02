package castbridge.core.tokens

/** Les trois commodités du Millionnaire payables en jetons (mêmes noms que `quiz.Boost`, sans en dépendre). [wire] est la valeur inscrite au porte-jetons. */
enum class TokenItem(val wire: String) {
    SECOND_CHANCE("second-chance"), EXTRA_JOKER("extra-joker"), SWAP_QUESTION("swap-question");

    companion object { fun ofWire(w: String): TokenItem? = values().firstOrNull { it.wire == w } }
}

/**
 * Prix en jetons lus dans la grille signée (`cost.secondChance`, `cost.extraJoker`, `cost.swapQuestion`). Copie locale : `PriceGrid.settings()` (w5-01) n'est pas encore fusionné ; le branchement
 * se fera en construisant cette classe depuis la grille. Défauts de la conception : 5 / 2 / 3.
 */
data class TokenSettings(val secondChance: Long = 5, val extraJoker: Long = 2, val swapQuestion: Long = 3) {
    init { require(secondChance in 1..MAX && extraJoker in 1..MAX && swapQuestion in 1..MAX) { "prix en jetons hors bornes" } }
    companion object { const val MAX = 1000L; val DEFAULT = TokenSettings() }
}

/** Politique des commodités : coûts (grille), plafonds par partie (fixes, conception § 6.1), textes français. */
class TokenPolicy(private val settings: TokenSettings = TokenSettings.DEFAULT) {
    fun cost(item: TokenItem): Long = when (item) { TokenItem.SECOND_CHANCE -> settings.secondChance; TokenItem.EXTRA_JOKER -> settings.extraJoker; TokenItem.SWAP_QUESTION -> settings.swapQuestion }

    fun maxPerGame(item: TokenItem): Int = when (item) { TokenItem.SECOND_CHANCE -> 1; TokenItem.EXTRA_JOKER -> 2; TokenItem.SWAP_QUESTION -> 1 }

    fun label(item: TokenItem): String = when (item) { TokenItem.SECOND_CHANCE -> "Seconde chance"; TokenItem.EXTRA_JOKER -> "Joker en plus"; TokenItem.SWAP_QUESTION -> "Changer de question" }

    fun explain(item: TokenItem): String = when (item) {
        TokenItem.SECOND_CHANCE -> "Après une mauvaise réponse, continuez la partie depuis la question ratée (une fois par partie)."
        TokenItem.EXTRA_JOKER -> "Rejouez un 50:50 ou un Avis du public déjà utilisé (deux fois au plus par partie)."
        TokenItem.SWAP_QUESTION -> "Remplacez la question en cours par une autre du même niveau (une fois par partie)."
    }

    /** « Utiliser 5 jetons (cinq) pour « Seconde chance » ? Solde : 23 jetons » : nombre en chiffres ET en lettres. */
    fun confirmText(item: TokenItem, balance: Long): String {
        val c = cost(item)
        return "Utiliser ${tokens(c)} (${FrenchNumbers.words(c)}) pour « ${label(item)} » ? Solde : ${tokens(balance)}"
    }

    private fun tokens(n: Long) = if (n in 0..1) "$n jeton" else "$n jetons"

    companion object {
        /** Clé d'opération d'un achat : `quiz:<partie>:<article>:<numéro d'achat dans la partie>` (idempotence de `QuizBoosts.charge` : même clé = même achat). */
        fun opKey(gameId: String, item: TokenItem, purchaseNo: Int): String {
            require(purchaseNo >= 1 && Regex("^[A-Za-z0-9._-]{1,48}$").matches(gameId)) { "identifiant de partie ou numéro d'achat invalide" }
            return "quiz:$gameId:${item.wire}:$purchaseNo"
        }

        /** Préfixe commun des achats d'un article dans une partie (pour `TokenWallet.spendCount`). */
        fun opPrefix(gameId: String, item: TokenItem): String = opKey(gameId, item, 1).removeSuffix("1")
    }
}

/** Les nombres en toutes lettres (0..10 000), français standard : « soixante et onze », « quatre-vingts », « deux cent un », « mille ». */
object FrenchNumbers {
    private val UNITS = listOf("zéro", "un", "deux", "trois", "quatre", "cinq", "six", "sept", "huit", "neuf", "dix", "onze", "douze", "treize", "quatorze", "quinze", "seize", "dix-sept", "dix-huit", "dix-neuf")
    private val TENS = listOf("", "", "vingt", "trente", "quarante", "cinquante", "soixante")

    fun words(n: Long): String { require(n in 0..10_000) { "hors de 0..10 000" }; return words(n.toInt()) }

    fun words(n: Int): String {
        require(n in 0..10_000)
        return when {
            n == 10_000 -> "dix mille"
            n >= 1000 -> (if (n / 1000 == 1) "mille" else "${below1000(n / 1000, false)} mille") + (if (n % 1000 == 0) "" else " " + below1000(n % 1000, true))
            else -> below1000(n, true)
        }
    }

    /** [final] : le groupe termine le nombre (« deux cents », « quatre-vingts » prennent leur s). */
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
}

/** Allocation quotidienne d'un profil enfant (le cœur du contrôle parental de w5-18, testé en JVM) : 0 = code parental à chaque dépense. */
data class KidAllowance(val dailyLimit: Int, val spentToday: Int, val day: String) {
    init { require(dailyLimit >= 0 && spentToday >= 0) }

    /** Vrai si [cost] jetons tiennent dans l'allocation du jour (jamais avec une allocation de 0). */
    fun canSpend(cost: Long): Boolean = dailyLimit > 0 && cost >= 1 && spentToday.toLong() + cost <= dailyLimit

    /** Le compteur après une dépense de [cost] (à appeler après un `SpendResult.Ok` non rejoué). */
    fun after(cost: Long): KidAllowance = copy(spentToday = (spentToday.toLong() + cost).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())

    /** Le compteur remis à zéro si [today] n'est pas le jour enregistré. */
    fun forDay(today: String): KidAllowance = if (today == day) this else copy(spentToday = 0, day = today)
}
