package castbridge.core.ux

/**
 * Cadence des repeints de CastBridge-TV (docs/agent-reports/tv-perf.md, R-11). Pur, sans horloge : l'appelant passe le temps.
 * Le petit processeur de la TV (4×A55, 720p) ne doit pas redessiner l'écran ni recalculer une ligne d'état plus souvent que l'œil ne le voit.
 */

/**
 * Au plus une exécution par [intervalMs]. [admit] répond 0 = exécuter maintenant (et l'intervalle repart), sinon le nombre de ms à attendre :
 * l'appelant programme alors UNE exécution différée (rien n'est perdu, la dernière valeur est affichée).
 */
class Throttle(private val intervalMs: Long) {
    init { require(intervalMs >= 0) { "intervalle négatif : $intervalMs" } }
    private var last = Long.MIN_VALUE

    @Synchronized fun admit(nowMs: Long): Long {
        if (last == Long.MIN_VALUE || nowMs - last >= intervalMs) { last = nowMs; return 0 }
        return intervalMs - (nowMs - last)
    }
}

/** Vrai seulement quand la valeur diffère de la précédente : on ne repeint (setText = nouvelle mise en page) que ce qui a changé. */
class StateGate<T> {
    private var has = false
    private var last: T? = null

    @Synchronized fun changed(value: T): Boolean {
        if (has && last == value) return false
        has = true; last = value; return true
    }

    /** L'écran a été reconstruit : la prochaine valeur est repeinte. */
    @Synchronized fun reset() { has = false; last = null }
}

/**
 * Le zoom lent du fond de l'accueil (1 → 1,12 en 30 s, puis retour), échantillonné par pas de [STEP_MS] au lieu de 60 images/s :
 * un pas déplace l'image de moins de 2 px à 1280 px, invisible, et l'écran entier n'est plus redessiné à chaque image.
 */
object SlowZoomCurve {
    const val STEP_MS = 250L
    const val PERIOD_MS = 30_000L
    const val MAX = 1.12f

    fun scaleAt(elapsedMs: Long): Float {
        if (elapsedMs <= 0) return 1f
        val p = elapsedMs % (2 * PERIOD_MS)
        val f = if (p <= PERIOD_MS) p.toFloat() / PERIOD_MS else (2 * PERIOD_MS - p).toFloat() / PERIOD_MS
        return 1f + (MAX - 1f) * f
    }
}

/** Le fond animé du Quiz : ~12 images/s (lent), ~8 images/s pendant une question (projecteurs ralentis), fluide pendant l'éclair de 0,9 s. */
object StagePace {
    fun delayMs(calm: Boolean, flashing: Boolean): Long = when {
        flashing -> 30
        calm -> 120
        else -> 80
    }
}
