package castbridge.core.tv

/**
 * Minuteur d'arrêt (wtv-01 n°3) : pur. Options 15/30/60/90 min ou « fin de la vidéo ». À l'échéance : fondu du son de 10 s puis pause
 * (jamais d'extinction de la TV : l'état final est PAUSE, il n'existe aucune action d'arrêt de l'appareil).
 */
class SleepTimer private constructor(private val endsAtMs: Long, val endOfVideo: Boolean) {
    enum class Phase { RUNNING, FADING, EXPIRED }

    companion object {
        const val FADE_MS = 10_000L
        val MINUTES = listOf(15, 30, 60, 90)
        /** Libellés des choix, dans l'ordre ; le dernier est « fin de la vidéo ». */
        val LABELS = MINUTES.map { "$it min" } + "Fin de la vidéo"

        fun minutes(m: Int, nowMs: Long): SleepTimer? = if (m in MINUTES) SleepTimer(nowMs + m * 60_000L, false) else null
        fun endOfVideo() = SleepTimer(Long.MAX_VALUE, true)
        /** Choix [index] de [LABELS] (null : hors liste). */
        fun choice(index: Int, nowMs: Long): SleepTimer? = when (index) { in MINUTES.indices -> minutes(MINUTES[index], nowMs); MINUTES.size -> endOfVideo(); else -> null }
    }

    /** Temps restant avant la pause ; « fin de la vidéo » : ce qui reste de la vidéo. */
    fun remainingMs(nowMs: Long, posMs: Long = 0, durMs: Long = 0): Long =
        if (endOfVideo) (durMs - posMs).coerceAtLeast(0) else (endsAtMs - nowMs).coerceAtLeast(0)

    fun phase(nowMs: Long, posMs: Long = 0, durMs: Long = 0): Phase {
        if (endOfVideo && durMs <= 0) return Phase.RUNNING
        val r = remainingMs(nowMs, posMs, durMs)
        return when { r <= 0 -> Phase.EXPIRED; r <= FADE_MS -> Phase.FADING; else -> Phase.RUNNING }
    }

    /** Facteur de volume 0..1 : 1 jusqu'au fondu, puis décroît linéairement jusqu'à 0 à l'échéance. */
    fun volumeFactor(nowMs: Long, posMs: Long = 0, durMs: Long = 0): Float {
        if (endOfVideo && durMs <= 0) return 1f
        return (remainingMs(nowMs, posMs, durMs).toFloat() / FADE_MS).coerceIn(0f, 1f)
    }

    /** Compte à rebours discret : « Arrêt dans 14:59 » (arrondi à la seconde supérieure). */
    fun countdown(nowMs: Long, posMs: Long = 0, durMs: Long = 0): String {
        val s = (remainingMs(nowMs, posMs, durMs) + 999) / 1000
        return "Arrêt dans " + LibraryLogic.clock(s * 1000)
    }
}
