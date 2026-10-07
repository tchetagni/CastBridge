package castbridge.core.xfer

/** R-21 : les raisons, en français, d'une copie qui s'arrête parce que la TV ne peut pas écrire (jamais une boucle de renvoi). */
object CopyCauses {
    const val READONLY = "La TV ne peut pas écrire : le support est en lecture seule (vérifiez la clé USB de la TV) : corrigez puis relancez"
    const val IO = "La TV ne peut pas écrire sur son stockage (erreur d'écriture, clé USB défaillante ou débranchée) : vérifiez la clé de la TV puis relancez"
    private const val STALLED = "Le disque de la TV ne répond plus (clé USB bloquée ou pleine) : débranchez-la puis rebranchez-la, puis relancez"
    private const val UNKNOWN = "La TV reçoit les blocs mais ne les enregistre pas (disque de la TV bloqué ou plein) : vérifiez sa clé USB puis relancez"

    /** M6 : le texte d'une TV verrouillée (403 avec `locked`/`trial`) : ce n'est PAS « code requis ». */
    const val LOCKED = "La TV est verrouillée (édition d'essai ou non activée) : activez CastBridge-TV puis relancez"

    /** M6 : l'assembleur a été fermé en parallèle (`closeIdle`, une autre voie, la fin de session) : une écriture interrompue, jamais une panne de disque. */
    fun isClosedChannel(e: Throwable?): Boolean {
        var t = e; var depth = 0
        while (t != null && depth++ < 6) { if (t is java.nio.channels.ClosedChannelException) return true; t = t.cause }
        return false
    }

    /** Un même bloc renvoyé plusieurs fois, rien de confirmé : [cause] est celle que la TV a donnée (`stalled`), ou null. */
    fun noConfirm(cause: String?): String = if (cause == "stalled") STALLED else UNKNOWN
}

/**
 * R-21 : débit mesuré sur une fenêtre glissante ([windowMs], 10 s) à partir d'échantillons « octets cumulés à l'instant t ». Pur.
 * [bytesPerSec] est 0 tant que la fenêtre n'a pas deux échantillons distants d'au moins une seconde.
 */
class LinkSpeed(private val windowMs: Long = 10_000) {
    private val samples = ArrayDeque<Pair<Long, Long>>()
    @Synchronized fun add(nowMs: Long, cumulativeBytes: Long) {
        if (samples.isNotEmpty() && cumulativeBytes < samples.last().second) samples.clear()      // un compteur reparti de zéro (nouvelle session)
        samples.addLast(nowMs to cumulativeBytes)
        while (samples.size > 2 && nowMs - samples[1].first >= windowMs) samples.removeFirst()
    }
    @Synchronized fun bytesPerSec(): Long {
        if (samples.size < 2) return 0
        val (t0, b0) = samples.first(); val (t1, b1) = samples.last()
        return if (t1 - t0 >= 1_000) ((b1 - b0) * 1000 / (t1 - t0)).coerceAtLeast(0) else 0
    }
}
