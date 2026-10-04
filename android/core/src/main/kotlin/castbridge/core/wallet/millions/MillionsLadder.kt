package castbridge.core.wallet.millions

/**
 * Échelle de gains du « Défi des 10 000 » (conception W22 § 13.2) : [values] = gain TOTAL affiché après réussite de la question 1..15, [stake] = mise (NDEM), [stops] = questions après lesquelles
 * on peut se retirer (5, 8, 10, 13). L'échelle et la mise viennent du pack signé (jamais du code) : [validate] refuse une échelle que le serveur n'aurait pas dû signer.
 * Aucune opération de ce paquet ne crédite quoi que ce soit : l'échelle ne sert qu'à calculer le gain AFFICHÉ et à juger un journal.
 */
class MillionsLadder(val values: List<Long>, val stake: Long, val stops: Set<Int> = DEFAULT_STOPS) {
    /** Texte français de la première règle violée, ou null si l'échelle est valide. */
    fun validate(): String? {
        if (values.size != QUESTIONS) return "L'échelle doit compter exactement $QUESTIONS gains."
        if (values.any { it < 1 || it > MAX }) return "Chaque gain doit être compris entre 1 et 1 000 000 000 NDEM."
        if (stake < 1 || stake > MAX) return "La mise doit être d'au moins 1 NDEM."
        if (stops.isEmpty() || stops.any { it !in 1 until QUESTIONS }) return "Les paliers de retrait doivent être des questions de 1 à ${QUESTIONS - 1}."
        for (i in 1 until QUESTIONS) if (values[i] <= values[i - 1]) return "L'échelle doit être strictement croissante (le gain de la question ${i + 1} ne dépasse pas le précédent)."
        var previous = 0L
        for (i in 0 until QUESTIONS) {
            val step = values[i] - (if (i == 0) 0L else values[i - 1])
            if (step < previous) return "Les incréments de l'échelle doivent être croissants (l'incrément de la question ${i + 1} est plus petit que le précédent)."
            previous = step
        }
        if (values[QUESTIONS - 1] != TOP) return "Le gain de la question $QUESTIONS doit être de 10 000 NDEM."
        if (values[stops.minOrNull()!! - 1] > stake) return "Le gain du premier palier ne peut pas dépasser la mise (au plus la mise est rendue)."
        return null
    }

    /** Gain affiché après réussite de la question [k] (1..15). */
    fun gainAfter(k: Int): Long { require(k in 1..QUESTIONS) { "question 1..$QUESTIONS" }; return values[k - 1] }

    override fun equals(other: Any?) = other is MillionsLadder && values == other.values && stake == other.stake && stops == other.stops
    override fun hashCode() = (values.hashCode() * 31 + stake.hashCode()) * 31 + stops.hashCode()
    override fun toString() = "MillionsLadder(stake=$stake, stops=$stops, values=$values)"

    companion object {
        const val QUESTIONS = 15
        const val TOP = 10_000L
        private const val MAX = 1_000_000_000L
        val DEFAULT_STOPS: Set<Int> = setOf(5, 8, 10, 13)

        /** Échelle B (recommandée, retenue : D-W22-18) : le palier 5 rend exactement la mise. */
        val B: List<Long> = listOf(50, 100, 200, 300, 500, 700, 900, 1200, 1600, 2000, 2700, 3600, 5000, 7000, 10000)
        /** Échelle A (généreuse, ≈ 1,8× : robinet, déconseillée) : pour le simulateur ; elle ne passe pas [validate] (incréments qui baissent à la fin). */
        val A: List<Long> = listOf(100, 200, 400, 600, 1000, 1400, 1800, 2400, 3100, 3900, 5000, 6300, 8000, 9000, 10000)
        /** Échelle C (avantage plateforme) : pour le simulateur ; elle ne passe pas [validate] non plus (incrément 350 puis 340). */
        val C: List<Long> = listOf(40, 90, 170, 260, 430, 600, 770, 1030, 1380, 1720, 2320, 3100, 4300, 6000, 10000)

        val DEFAULT = MillionsLadder(B, 500)
    }
}
