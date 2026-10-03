package castbridge.core.quiz.online

/**
 * Temps d'aller-retour (RTT) mesuré par le SERVEUR, par connexion (ping/pong), et compensation d'équité (DESIGN-W20 § 2.6).
 * Jamais d'horloge de client. Pure.
 *
 * Audit Opus de w20-03 (I2) : un client peut RETARDER ses pongs pour gonfler son RTT et gagner du temps. Le RTT retenu est donc le MINIMUM des
 * [WINDOW] derniers échantillons (un seul bon échantillon récent suffit à le ramener à la vérité), la compensation d'un joueur plafonne à
 * `min(rtt, 200 ms) / 2` (100 ms au plus) et le terme de relais de la TV à `min(rtt, 400 ms)`.
 */
class RttBook {
    private val samples = HashMap<String, ArrayDeque<Long>>()

    /** Un échantillon (ms), borné à [0, 2 000] ; seuls les [WINDOW] derniers sont gardés. */
    fun sample(conn: String, ms: Long) {
        val q = samples.getOrPut(conn) { ArrayDeque() }
        q.addLast(ms.coerceIn(0, MAX_RTT_MS))
        while (q.size > WINDOW) q.removeFirst()
    }

    /** RTT retenu : la plus petite des [WINDOW] dernières mesures (0 si inconnu). */
    fun rtt(conn: String): Long = samples[conn]?.minOrNull() ?: 0L

    fun known(conn: String) = conn in samples
    fun forget(conn: String) { samples.remove(conn) }

    /** Temps rendu à un joueur : `min(rtt, 200 ms) / 2`, soit 100 ms au plus. */
    fun compensationMs(conn: String): Long = compensation(rtt(conn))

    /** Grâce après la clôture : `min(rtt, 1 000 ms)`. */
    fun graceMs(conn: String): Long = minOf(rtt(conn), MAX_GRACE_MS)

    companion object {
        const val WINDOW = 8
        const val MAX_RTT_MS = 2_000L
        /** RTT au-delà duquel la compensation ne grandit plus. */
        const val MAX_COMPENSATION_RTT_MS = 200L
        const val MAX_RELAY_RTT_MS = 400L
        const val MAX_GRACE_MS = 1_000L

        private fun compensation(rttMs: Long) = minOf(rttMs.coerceIn(0, MAX_RTT_MS), MAX_COMPENSATION_RTT_MS) / 2

        /** Temps depuis l'ouverture pour un joueur ordinaire : `arrivée − ouverture − min(rtt, 200)/2`, jamais négatif. */
        fun elapsed(arrivalMinusOpenMs: Long, rttMs: Long): Long = (arrivalMinusOpenMs - compensation(rttMs)).coerceAtLeast(0)

        /**
         * Joueur relayé par la TV : `max(localElapsedMono, tempsServeur − min(rttTV, 400))`. La TV ne peut qu'AUGMENTER le temps d'un joueur,
         * jamais le réduire sous ce que le réseau prouve (et elle ne peut pas en retrancher plus de 400 ms).
         */
        fun relayed(localElapsedMono: Long, arrivalMinusOpenMs: Long, rttTvMs: Long): Long =
            maxOf(localElapsedMono.coerceAtLeast(0), arrivalMinusOpenMs - rttTvMs.coerceIn(0, MAX_RELAY_RTT_MS)).coerceAtLeast(0)
    }
}
