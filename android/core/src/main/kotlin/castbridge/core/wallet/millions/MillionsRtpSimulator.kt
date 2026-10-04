package castbridge.core.wallet.millions

/**
 * Simulateur de rendement du Défi (conception W22 § 13.2) : programmation dynamique du joueur OPTIMAL pour une échelle et 15 probabilités de réussite SUPPOSÉES (rien n'est mesuré : seuls les journaux
 * réels trancheront). Sert aux tests et à l'administration (choisir l'échelle, régler la cible de rendement) ; aucune influence sur une partie.
 *
 * État : (nombre de bonnes réponses k, joker disponible). À chaque question le joueur peut utiliser le 50:50 (probabilité relevée selon le [Joker]) ; à chaque palier il peut s'arrêter. [rtp] =
 * gain moyen / mise (1,0 = la plateforme ne gagne ni ne perd).
 */
object MillionsRtpSimulator {
    /** Les quatre façons de modéliser le 50:50 (le chiffre du rendement en dépend beaucoup). */
    enum class Joker {
        /** Pas de joker. */
        NONE,
        /** « Devinette » : le joueur SAIT avec s = (p − 0,25)/0,75, sinon devine 1 sur 4 ; le 50:50 le fait deviner 1 sur 2. */
        GUESS,
        /** « Moitié de l'erreur retirée » : p + (1 − p)/2. */
        HALF,
        /** « Question sûre » : le 50:50 rend la question certaine (p = 1). */
        SURE,
    }

    /** Probabilité de réussite avec le 50:50 sur une question de probabilité [p] sans lui. */
    fun boosted(model: Joker, p: Double): Double = when (model) {
        Joker.NONE -> p
        Joker.GUESS -> { val s = ((p - 0.25) / 0.75).coerceIn(0.0, 1.0); s + (1 - s) / 2 }
        Joker.HALF -> p + (1 - p) / 2
        Joker.SURE -> 1.0
    }

    /**
     * Rendement (gain moyen / [stake]) du joueur optimal. [p] : 15 probabilités de réussite (question 1..15). [stopAt] non nul : arrêt IMPOSÉ à ce palier (ou jamais avant la question 15 si 15) ;
     * nul : arrêt optimal aux paliers [stops].
     */
    fun rtp(values: List<Long>, stake: Long, p: List<Double>, joker: Joker, stopAt: Int? = null, stops: Set<Int> = MillionsLadder.DEFAULT_STOPS): Double {
        require(values.size == MillionsLadder.QUESTIONS && p.size == MillionsLadder.QUESTIONS && stake > 0) { "15 gains, 15 probabilités, mise > 0" }
        require(p.all { it in 0.0..1.0 }) { "probabilités entre 0 et 1" }
        require(stopAt == null || stopAt in stops || stopAt == MillionsLadder.QUESTIONS) { "arrêt imposé : un palier ou la question 15" }
        val n = MillionsLadder.QUESTIONS
        // v[k][j] : espérance de gain après k bonnes réponses, j = 1 si le joker est encore disponible
        val v = Array(n + 1) { DoubleArray(2) }
        v[n][0] = values[n - 1].toDouble(); v[n][1] = v[n][0]
        for (k in n - 1 downTo 0) for (j in 0..1) {
            val plain = p[k] * v[k + 1][j]
            val cont = if (j == 1 && joker != Joker.NONE) maxOf(plain, boosted(joker, p[k]) * v[k + 1][0]) else p[k] * v[k + 1][if (joker == Joker.NONE) 0 else j]
            val gain = if (k >= 1) values[k - 1].toDouble() else 0.0
            v[k][j] = when {
                k == 0 -> cont
                stopAt != null -> if (k == stopAt) gain else cont
                k in stops -> maxOf(gain, cont)
                else -> cont
            }
        }
        return v[0][if (joker == Joker.NONE) 0 else 1] / stake
    }
}
