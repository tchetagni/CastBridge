package castbridge.core.tv

/**
 * Boucle A-B (wtv-01 n°4) : pure. Un appui pose A, le suivant pose B (la boucle démarre), le troisième l'efface.
 * Si B est posé AVANT A (B < A), les deux sont échangés ; B = A (ou à moins de 1 s) est refusé (boucle vide).
 */
data class LoopAB(val aMs: Long? = null, val bMs: Long? = null) {
    val active: Boolean get() = aMs != null && bMs != null
    val waitingForB: Boolean get() = aMs != null && bMs == null

    /** L'appui suivant sur « Boucle A-B » à la position [posMs]. */
    fun press(posMs: Long): LoopAB = when {
        aMs == null -> LoopAB(posMs.coerceAtLeast(0), null)
        bMs == null -> {
            val a = aMs
            if (Math.abs(posMs - a) < MIN_SPAN_MS) this
            else LoopAB(minOf(a, posMs), maxOf(a, posMs))
        }
        else -> LoopAB()
    }

    /** À appeler à chaque tick du lecteur : la position où revenir quand la lecture a atteint B (null : rien à faire). */
    fun jumpBack(posMs: Long): Long? = if (active && posMs >= bMs!!) aMs else null

    fun label(): String = when {
        active -> "Boucle A-B : ${LibraryLogic.clock(aMs!!)} - ${LibraryLogic.clock(bMs!!)}"
        waitingForB -> "Boucle A-B : A à ${LibraryLogic.clock(aMs!!)}, choisir B"
        else -> "Boucle A-B : non"
    }

    companion object { const val MIN_SPAN_MS = 1000L }
}
