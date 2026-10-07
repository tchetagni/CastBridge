package castbridge.core.quiz.online

import castbridge.core.connect.NetState
import castbridge.core.relay.RelayText

/** La liaison du tuyau Bluetooth telle que la TV l'a mesurée : [rttMs] aller-retour (PING), [kbps] meilleur débit récent, [samples] nombre de PING gardés. */
data class RelayLink(val rttMs: Long?, val kbps: Long?, val samples: Int = 0)

/**
 * Garde les mesures de la liaison (relay-R1 § 5) : la médiane des derniers PING (un pic isolé ne change rien) et le meilleur débit des dernières fenêtres de vrai trafic
 * (la capacité de la liaison, pas la moyenne d'une pause). Pur : la TV échantillonne ([castbridge.core.gateway.Entry.ping], compteurs d'octets du lien), ceci résume.
 */
class RelayLinkMeter(private val keep: Int = 8) {
    private val pings = ArrayDeque<Long>()
    private val rates = ArrayDeque<Long>()

    @Synchronized fun pingSample(ms: Long) {
        pings.addLast(ms.coerceIn(0, MAX_RTT_MS))
        while (pings.size > keep) pings.removeFirst()
    }

    /** [bytes] échangés pendant [ms] de trafic réel ; sous 4 Kio ou sans durée, c'est du bruit (accusés, PING) et rien n'est retenu. */
    @Synchronized fun throughputSample(bytes: Long, ms: Long) {
        if (bytes < MIN_WINDOW_BYTES || ms <= 0) return
        rates.addLast(bytes * 8 / ms)
        while (rates.size > RATE_WINDOWS) rates.removeFirst()
    }

    @Synchronized fun profile(): RelayLink =
        RelayLink(pings.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }, rates.maxOrNull(), pings.size)

    @Synchronized fun reset() { pings.clear(); rates.clear() }

    companion object {
        const val MAX_RTT_MS = 10_000L
        const val MIN_WINDOW_BYTES = 4_096L
        const val RATE_WINDOWS = 5
    }
}

/**
 * Le jeu en ligne par relais (DESIGN-RELAIS § 2.5, décision du propriétaire : « le jeu en ligne doit s'adapter dès qu'il existe au moins un relais »). La TV compte comme
 * EN LIGNE dès que [NetState] est `via_relay` ; le téléphone n'est qu'un tuyau. Ce que la TV adapte, SANS rien changer au protocole du service :
 * - la fenêtre de réponse de SES téléphones locaux est allongée de la latence mesurée, bornée par la grâce que le service accorde lui-même ([RttBook.MAX_GRACE_MS]) ;
 * - les délais de connexion et de réponse de son transport grandissent avec la latence, bornés ;
 * - la courbe de réouverture d'une session perdue est plus espacée (le tuyau met des secondes à revenir) ; la limite de 60 s est celle du service ([PlayTvSession.LOST_AFTER_MS]) ;
 * - les horodatages restent ceux du SERVEUR (règle 11 : jamais l'horloge de la TV) ; la connexion au service est ouverte avant le choix de l'utilisateur ([Rules.warmUp]) ;
 * - une ligne d'information, sans alarme ; aucune pénalité de classement liée au relais.
 */
object PlayRelayProfile {
    enum class Quality { UNKNOWN, GOOD, SLOW, VERY_SLOW }

    /** Latence supposée tant qu'aucun PING n'a répondu. */
    const val ASSUMED_RTT_MS = 400L
    const val RESUME_FREE_MS = PlayTvSession.LOST_AFTER_MS
    private const val BASE_CONNECT_MS = 10_000
    private const val BASE_POST_MS = 20_000
    private val RELAY_CURVE_SEC = intArrayOf(0, 3, 6, 12, 20, 30)

    class Rules(
        val relay: Boolean, val quality: Quality,
        /** Ajoutée à la fenêtre de réponse du service pour les téléphones locaux de la TV. */
        val answerExtraMs: Long,
        val connectTimeoutMs: Int, val postReadTimeoutMs: Int,
        val curveSec: IntArray,
        val warmUp: Boolean,
        /** Dite dans le bandeau de la partie ; null hors relais. */
        val line: String?,
        val rankingPenalty: Int = 0,
    )

    fun quality(l: RelayLink): Quality {
        val rtt = l.rttMs; val kbps = l.kbps
        if (rtt == null && kbps == null) return Quality.UNKNOWN
        if ((rtt != null && rtt > 1_500) || (kbps != null && kbps < 20)) return Quality.VERY_SLOW
        if ((rtt == null || rtt <= 300) && (kbps == null || kbps >= 200)) return Quality.GOOD
        return Quality.SLOW
    }

    fun rules(net: NetState, link: RelayLink): Rules {
        if (net != NetState.VIA_RELAY) return Rules(false, quality(link), 0L, BASE_CONNECT_MS, BASE_POST_MS, PlayTvSession.CURVE_SEC, false, null)
        val rtt = (link.rttMs ?: ASSUMED_RTT_MS).coerceAtLeast(0L)
        return Rules(
            relay = true, quality = quality(link),
            answerExtraMs = minOf(rtt, RttBook.MAX_GRACE_MS),
            connectTimeoutMs = (BASE_CONNECT_MS + 4 * rtt).coerceAtMost(25_000L).toInt(),
            postReadTimeoutMs = (BASE_POST_MS + 8 * rtt).coerceAtMost(45_000L).toInt(),
            curveSec = RELAY_CURVE_SEC.copyOf(), warmUp = true, line = RelayText.PLAY_LINE,
        )
    }

    /** La fenêtre que voient les téléphones de la maison : celle du service plus la rallonge (aucune hors relais). */
    fun windowFor(serverWindowMs: Long, rules: Rules): Long = serverWindowMs + rules.answerExtraMs

    /** Une coupure de moins de [RESUME_FREE_MS] se rattrape par `resume` sans conséquence ; au-delà le service abandonne la table (règle du service, pas du relais). */
    fun gameAbandoned(outageMs: Long): Boolean = outageMs >= RESUME_FREE_MS
}
