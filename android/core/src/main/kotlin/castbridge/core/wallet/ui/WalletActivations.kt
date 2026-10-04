package castbridge.core.wallet.ui

/**
 * Les activations `cbx1` que la TV joint à `POST /sync` : au plus 4, au plus 8 192 caractères en tout (limites du serveur). On joint d'abord ce qui décide de l'édition (droit « super », puis
 * activation de production, puis essai, puis le reste), les plus récentes en premier ; une activation trop grosse pour le reste de la place est sautée, pas la suivante.
 */
object WalletActivations {
    const val MAX_COUNT = 4
    const val MAX_CHARS = 8_192

    /** [priority] : 0 droit « super », 1 production, 2 essai, 3 autre (plus petit = plus utile). */
    data class Candidate(val token: String, val priority: Int, val issuedAt: Long)

    fun pick(all: List<Candidate>): List<String> {
        val out = ArrayList<String>(); var total = 0
        for (c in all.filter { it.token.isNotBlank() }.sortedWith(compareBy<Candidate> { it.priority }.thenByDescending { it.issuedAt })) {
            if (out.size >= MAX_COUNT) break
            val t = c.token.trim()
            if (t in out || total + t.length > MAX_CHARS) continue
            out += t; total += t.length
        }
        return out
    }
}
