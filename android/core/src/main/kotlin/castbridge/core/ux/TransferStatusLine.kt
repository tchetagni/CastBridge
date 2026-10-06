package castbridge.core.ux

import castbridge.core.xfer.PlaybackAwareCopyPolicy

/** Par quelle voie la copie avance réellement (dite à l'usager). */
enum class CopyRouteKind(val label: String) { WIFI("par le Wi-Fi"), WIFI_DIRECT("par le Wi-Fi Direct"), BLUETOOTH("par le Bluetooth") }

/** Les faits d'un envoi, relevés par l'écran ou le service (minces) ; toutes les décisions de texte et de couleur sont dans [TransferStatusLine]. */
data class TransferFacts(
    val percent: Int?, val route: CopyRouteKind?,
    /** Des octets avancent (progression vue sur le téléphone OU sur la TV il y a peu). */
    val copying: Boolean, val slowed: Boolean, val waiting: Boolean, val failure: String?,
    /** Fichier lisible pendant la copie (« Copier et lire », fichier qui grandit). */
    val playable: Boolean = false,
    /** R-21 : ce que le moteur de copie mesure (envoyé / confirmé, débits sur 10 s) ; null = rien de mesuré (TV ancienne, copie classique). */
    val link: LinkFacts? = null)

/**
 * R-21 : les mesures d'un lien de copie. [sentBps] / [confirmedBps] = octets écrits sur le socket / confirmés par la TV par seconde, sur 10 s ;
 * [remainingBytes] = ce qui reste à faire confirmer. Les pourcentages sont ceux du moteur (0 à 100).
 */
data class LinkFacts(val sentPercent: Int, val confirmedPercent: Int, val sentBps: Long, val confirmedBps: Long, val remainingBytes: Long) {
    /** Débit utile : ce que le lien porte réellement (le plus haut des deux). */
    val rateBps: Long get() = maxOf(sentBps, confirmedBps)
    /** La TV répond, mais le lien est sous 1 Mo/s. */
    val slowLink: Boolean get() = rateBps in 1 until TransferStatusLine.SLOW_LINK_BPS
    /** Le lien va bien, ce sont les confirmations qui traînent (disque de la TV). */
    val diskLag: Boolean get() = sentBps >= TransferStatusLine.SLOW_LINK_BPS && confirmedBps * 2 < sentBps && sentPercent - confirmedPercent >= 5
    /** Deux chiffres seulement quand ils diffèrent d'au moins 2 points. */
    val twoNumbers: Boolean get() = sentPercent - confirmedPercent >= 2
}

data class StatusLine(val text: String, val level: SignalLevel)

/**
 * R-18 : UNE ligne d'état pour l'écran de diffusion ET la notification (même texte, mêmes couleurs : [SignalLevel]).
 * Priorité : copie en cours (vert ; orange si ralentie, R-15 ; orange si le Wi-Fi de la TV est lent ou si son disque traîne, R-21) > copie ralentie seule >
 * vraie raison d'échec (rouge) > attente de la TV (orange).
 * Une copie qui avance n'est jamais « en attente » ni « introuvable ». Un échec réel sans copie garde sa raison (codes R-17).
 */
object TransferStatusLine {
    const val WAITING = "En attente de la TV (reprise automatique)"
    const val PREPARING = "Préparation de l'envoi"
    const val PLAYABLE = "Lecture possible"
    const val DISK_SLOW = "La TV écrit lentement (disque)"
    /** Sous ce débit mesuré (octets/s, sur 10 s), le Wi-Fi de la TV est dit lent. */
    const val SLOW_LINK_BPS = 1_000_000L

    /** « ~300 Ko/s » : arrondi à 10 Ko/s, jamais moins de 10 (pas de fausse précision). */
    fun rateText(bps: Long): String = "~${((bps + 5_000) / 10_000 * 10).coerceAtLeast(10)} Ko/s"

    /** « ~20 min restantes », « ~45 s restantes », « ~3 h restantes » ; null quand l'estimation ne vaut plus rien (rien de mesuré, plus de 48 h). */
    fun remainingText(remainingBytes: Long, bps: Long): String? {
        if (bps <= 0 || remainingBytes <= 0) return null
        val s = (remainingBytes + bps - 1) / bps
        return when {
            s > 48 * 3600 -> null
            s < 90 -> "~${maxOf(s, 5)} s restantes"
            s < 90 * 60 -> "~${(s + 30) / 60} min restantes"
            else -> "~${(s + 1800) / 3600} h restantes"
        }
    }

    fun of(f: TransferFacts): StatusLine = when {
        f.copying -> {
            val parts = ArrayList<String>()
            val l = f.link
            val slowLink = !f.slowed && l != null && l.slowLink
            val diskLag = !f.slowed && !slowLink && l != null && l.diskLag
            if (f.playable) parts += PLAYABLE
            if (slowLink) parts += "Wi-Fi de la TV lent : ${rateText(l!!.rateBps)}" + (remainingText(l.remainingBytes, l.rateBps)?.let { " · $it" } ?: "")
            else if (diskLag) parts += DISK_SLOW
            else parts += "Copie en cours"
            if (l != null && l.twoNumbers) { parts += "${l.sentPercent.coerceIn(0, 100)} % envoyés"; parts += "${l.confirmedPercent.coerceIn(0, 100)} % confirmés" }
            else if (!slowLink && !diskLag) f.percent?.let { parts += "${it.coerceIn(0, 100)} %" }
            if (!slowLink) f.route?.let { parts += it.label }       // « Wi-Fi de la TV lent » dit déjà la voie
            if (f.slowed) parts += PlaybackAwareCopyPolicy.SLOWED_TEXT
            StatusLine(parts.joinToString(" · "), if (f.slowed || slowLink || diskLag) SignalLevel.ORANGE else SignalLevel.GREEN)
        }
        f.slowed -> StatusLine(PlaybackAwareCopyPolicy.SLOWED_TEXT, SignalLevel.ORANGE)
        f.failure != null -> StatusLine(f.failure, SignalLevel.RED)
        f.waiting -> StatusLine(WAITING, SignalLevel.ORANGE)
        else -> StatusLine(PREPARING, SignalLevel.BLACK)
    }

    fun forScreen(f: TransferFacts): String = of(f).text
    fun forNotification(f: TransferFacts): String = of(f).text
}
