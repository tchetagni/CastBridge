package castbridge.core.tv

/**
 * Marque-pages d'un fichier (wtv-01 n°4) : jusqu'à 20, triés, sans doublon à moins d'une seconde. Pur ; mémorisés avec les autres réglages du fichier
 * (clé « bm », positions en secondes séparées par des virgules).
 */
data class Bookmarks(val marksMs: List<Long> = emptyList()) {
    companion object {
        const val MAX = 20
        const val NEAR_MS = 1000L
        fun decode(s: String?): Bookmarks = Bookmarks(s.orEmpty().split(',').mapNotNull { it.toLongOrNull() }.filter { it >= 0 }.map { it * 1000 }.distinct().sorted().take(MAX))
    }

    val full: Boolean get() = marksMs.size >= MAX

    /** Ajoute un marque-page ici ; inchangé si la liste est pleine ou s'il y en a déjà un à moins d'une seconde. */
    fun add(posMs: Long): Bookmarks {
        val p = posMs.coerceAtLeast(0) / 1000 * 1000
        if (full || marksMs.any { Math.abs(it - p) < NEAR_MS }) return this
        return Bookmarks((marksMs + p).sorted())
    }

    fun remove(index: Int): Bookmarks = if (index in marksMs.indices) Bookmarks(marksMs.filterIndexed { i, _ -> i != index }) else this

    /** Le prochain marque-page strictement après [posMs] (marge d'une seconde), ou null. */
    fun next(posMs: Long): Long? = marksMs.firstOrNull { it > posMs + NEAR_MS }
    fun previous(posMs: Long): Long? = marksMs.lastOrNull { it < posMs - NEAR_MS }

    fun encode(): String = marksMs.joinToString(",") { (it / 1000).toString() }
    fun labels(): List<String> = marksMs.mapIndexed { i, m -> "Marque-page ${i + 1} : ${LibraryLogic.clock(m)}" }
}
