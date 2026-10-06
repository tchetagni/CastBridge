package castbridge.core.xfer

/**
 * R-20 : les réglages de la copie qui ne dépendent que de chiffres (purs, testés en JVM), jamais d'Android.
 *
 *  - [streams] / [blockSize] : ce que la TV annonce dans ses caps (`maxStreams`, `blockSize` OPTIONNEL : une TV 0.14.37 ne le dit pas, le téléphone garde
 *    alors ses valeurs). Un profil « ressources faibles » de la TV tient donc en deux nombres que le téléphone respecte.
 *  - [NotificationGate] : une notification de la file n'est republiée que si son texte ou son pourcentage a changé, et au plus une fois par seconde.
 *  - [Backoff] : attente exponentielle bornée (jamais de boucle serrée, jamais de boucle sans fin : l'appelant compte ses échecs).
 */
object CopyTuning {
    /** Flux simultanés : le plus petit de ce que la TV annonce (caps, begin), jamais moins de 1. */
    fun streams(capsMax: Int, beginMax: Int): Int = minOf(capsMax, beginMax).coerceAtLeast(1)

    /** Taille de bloc : celle de la politique par taille de fichier, réduite si la TV en annonce une plus petite (MIN_BLOCK..MAX_BLOCK, multiple d'une tranche). */
    fun blockSize(fileSize: Long, tvBlock: Int?): Int {
        val own = Manifest.blockSizeFor(fileSize)
        if (tvBlock == null || tvBlock <= 0) return own
        val asked = (tvBlock / Manifest.SLICE * Manifest.SLICE).coerceIn(Manifest.MIN_BLOCK, Manifest.MAX_BLOCK)
        return minOf(own, asked)
    }

    /** Délais : connexion 5 s, lecture 15 s (la fin de copie, qui relit le fichier sur la TV, garde 60 s). */
    const val CONNECT_TIMEOUT_MS = 5_000
    const val READ_TIMEOUT_MS = 15_000
    const val FINISH_READ_TIMEOUT_MS = 60_000
    /** Échecs de liaison consécutifs (begin / finish / caps) avant de rendre la main à la file (qui attend la TV, R-19) : environ 10 minutes de réessais espacés. */
    const val MAX_UNREACHABLE_MS = 10 * 60_000L
}

/** A change of text waits at least [minGapMs] since the last post, a change of percentage alone at least [pctGapMs] (never more posts than the old 2 s tick). */
class NotificationGate(private val minGapMs: Long = 1_000, private val pctGapMs: Long = 2_000) {
    private var lastText: String? = null
    private var lastPct = -1
    private var lastAt = Long.MIN_VALUE / 2
    @Volatile var posted = 0; private set

    /** True when the notification must be posted now ([pct] null = no bar). */
    @Synchronized fun shouldPost(text: String, pct: Int?, nowMs: Long): Boolean {
        val p = pct ?: -1
        if (text == lastText && p == lastPct) return false
        if (nowMs - lastAt < (if (text == lastText) pctGapMs else minGapMs)) return false
        lastText = text; lastPct = p; lastAt = nowMs; posted++
        return true
    }
    /** A foreground start always shows its notification: forget what was posted. */
    @Synchronized fun reset() { lastText = null; lastPct = -1; lastAt = Long.MIN_VALUE / 2 }
}

object Backoff {
    /** baseMs x 2^attempt, at most maxMs (attempt 0 = the first wait). */
    fun delayMs(attempt: Int, baseMs: Long = 2_000, maxMs: Long = 30_000): Long = (baseMs shl attempt.coerceIn(0, 20)).coerceAtMost(maxMs).coerceAtLeast(baseMs.coerceAtMost(maxMs))
}
