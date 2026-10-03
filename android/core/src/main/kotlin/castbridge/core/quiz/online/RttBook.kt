package castbridge.core.quiz.online

/**
 * Temps d'aller-retour (RTT) mesuré par le SERVEUR, par connexion (ping/pong), et compensation d'équité (DESIGN-W20 § 2.6).
 * Jamais d'horloge de client. Pure.
 */
class RttBook(private val alpha: Double = 0.3) {
    private val rtt = HashMap<String, Double>()

    /** Un échantillon (ms) : le premier fixe la valeur, les suivants la lissent (EWMA). Borné à [0, 2 000]. */
    fun sample(conn: String, ms: Long) {
        val v = ms.coerceIn(0, MAX_RTT_MS).toDouble()
        rtt[conn] = rtt[conn]?.let { alpha * v + (1 - alpha) * it } ?: v
    }

    /** RTT lissé (0 si inconnu). */
    fun rtt(conn: String): Long = Math.round(rtt[conn] ?: 0.0)

    fun known(conn: String) = conn in rtt
    fun forget(conn: String) { rtt.remove(conn) }

    /** Temps rendu à un joueur : `min(rtt/2, 400 ms)`. */
    fun compensationMs(conn: String): Long = minOf(rtt(conn) / 2, MAX_COMPENSATION_MS)

    /** Grâce après la clôture : `min(rtt, 1 000 ms)`. */
    fun graceMs(conn: String): Long = minOf(rtt(conn), MAX_GRACE_MS)

    companion object {
        const val MAX_RTT_MS = 2_000L
        const val MAX_COMPENSATION_MS = 400L
        const val MAX_GRACE_MS = 1_000L

        /** Temps depuis l'ouverture pour un joueur ordinaire : `arrivée − ouverture − min(rtt/2, 400)`, jamais négatif. */
        fun elapsed(arrivalMinusOpenMs: Long, rttMs: Long): Long =
            (arrivalMinusOpenMs - minOf(rttMs.coerceIn(0, MAX_RTT_MS) / 2, MAX_COMPENSATION_MS)).coerceAtLeast(0)

        /**
         * Joueur relayé par la TV : `max(localElapsedMono, tempsServeur − rttTV)`. La TV ne peut qu'AUGMENTER le temps d'un joueur,
         * jamais le réduire sous ce que le réseau prouve.
         */
        fun relayed(localElapsedMono: Long, arrivalMinusOpenMs: Long, rttTvMs: Long): Long =
            maxOf(localElapsedMono.coerceAtLeast(0), arrivalMinusOpenMs - rttTvMs.coerceIn(0, MAX_RTT_MS)).coerceAtLeast(0)
    }
}
