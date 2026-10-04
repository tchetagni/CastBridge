package castbridge.core.tv

/**
 * Reprise de lecture (wtv-01 n°1) : pur. On propose « Reprendre à 55:04 / Recommencer » ; le choix par défaut est de reprendre.
 * On n'en propose pas sous 30 s (le début) ni au-delà de 95 % (le générique).
 */
object ResumePolicy {
    const val MIN_MS = 30_000L
    const val MAX_PERCENT = 95
    enum class Choice { RESUME, RESTART }
    val DEFAULT = Choice.RESUME

    /** Faut-il proposer la reprise ? [durMs] <= 0 (durée inconnue) : seule la règle des 30 s s'applique. */
    fun offer(posMs: Long, durMs: Long): Boolean = posMs >= MIN_MS && (durMs <= 0 || posMs * 100 <= durMs * MAX_PERCENT)

    fun label(posMs: Long) = "Reprendre à ${LibraryLogic.clock(posMs)}"
    const val RESTART_LABEL = "Recommencer"

    /** La position de départ selon le choix ; une position qu'on n'aurait pas proposée repart de 0. */
    fun startFor(choice: Choice, posMs: Long, durMs: Long): Long = if (choice == Choice.RESUME && offer(posMs, durMs)) posMs else 0L
}
