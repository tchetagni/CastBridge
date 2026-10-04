package castbridge.core.tv

/**
 * Sauts du lecteur (wtv-01 n°2) : purs. GAUCHE/DROITE = le pas choisi (10, 20 ou 30 s, 10 par défaut) ; appui long ou deux appuis rapides = trois fois le pas (30 s par défaut).
 * La table des touches de [PlayerRemote] n'est pas modifiée : le câblage remplace seulement la valeur des deux actions de saut de 10 s.
 */
object PlayerSeek {
    val STEPS_S = listOf(10, 20, 30)
    const val DEFAULT_STEP_S = 10
    const val DOUBLE_PRESS_MS = 400L

    fun step(sec: Int): Int = if (sec in STEPS_S) sec else DEFAULT_STEP_S
    fun smallMs(stepSec: Int): Long = step(stepSec) * 1000L
    fun bigMs(stepSec: Int): Long = step(stepSec) * 3000L
    /** Ce qu'il reste à sauter quand un petit saut a déjà été fait (appui long ou second appui) : le total vaut [bigMs]. */
    fun followUpMs(stepSec: Int): Long = bigMs(stepSec) - smallMs(stepSec)

    /** Le saut (positif) pour GAUCHE/DROITE selon l'appui. */
    fun amountMs(stepSec: Int, longPress: Boolean, doublePress: Boolean): Long = if (longPress || doublePress) bigMs(stepSec) else smallMs(stepSec)

    /** La nouvelle position après un saut de [deltaMs] (signé) : jamais avant 0, ni après la fin quand la durée est connue. */
    fun target(posMs: Long, deltaMs: Long, durMs: Long): Long {
        val t = (posMs + deltaMs).coerceAtLeast(0)
        return if (durMs > 0) t.coerceAtMost((durMs - 1000).coerceAtLeast(0)) else t
    }
}

/** Détecte « deux appuis rapides » sur la même touche. */
class DoublePress(private val windowMs: Long = PlayerSeek.DOUBLE_PRESS_MS) {
    private var lastKey = Int.MIN_VALUE; private var lastAt = Long.MIN_VALUE
    /** Enregistre un appui ; vrai s'il suit de près un appui sur la même touche (le triple appui recommence à zéro). */
    fun register(key: Int, nowMs: Long): Boolean {
        val dbl = key == lastKey && lastAt != Long.MIN_VALUE && nowMs - lastAt <= windowMs
        if (dbl) { lastKey = Int.MIN_VALUE; lastAt = Long.MIN_VALUE } else { lastKey = key; lastAt = nowMs }
        return dbl
    }
}
