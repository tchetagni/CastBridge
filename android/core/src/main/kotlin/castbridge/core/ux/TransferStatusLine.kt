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
    val playable: Boolean = false)

data class StatusLine(val text: String, val level: SignalLevel)

/**
 * R-18 : UNE ligne d'état pour l'écran de diffusion ET la notification (même texte, mêmes couleurs : [SignalLevel]).
 * Priorité : copie en cours (vert ; orange si ralentie, R-15) > copie ralentie seule > vraie raison d'échec (rouge) > attente de la TV (orange).
 * Une copie qui avance n'est jamais « en attente » ni « introuvable ». Un échec réel sans copie garde sa raison (codes R-17).
 */
object TransferStatusLine {
    const val WAITING = "En attente de la TV (reprise automatique)"
    const val PREPARING = "Préparation de l'envoi"
    const val PLAYABLE = "Lecture possible"

    fun of(f: TransferFacts): StatusLine = when {
        f.copying -> {
            val parts = ArrayList<String>()
            if (f.playable) parts += PLAYABLE
            parts += "Copie en cours"
            f.percent?.let { parts += "${it.coerceIn(0, 100)} %" }
            f.route?.let { parts += it.label }
            if (f.slowed) parts += PlaybackAwareCopyPolicy.SLOWED_TEXT
            StatusLine(parts.joinToString(" · "), if (f.slowed) SignalLevel.ORANGE else SignalLevel.GREEN)
        }
        f.slowed -> StatusLine(PlaybackAwareCopyPolicy.SLOWED_TEXT, SignalLevel.ORANGE)
        f.failure != null -> StatusLine(f.failure, SignalLevel.RED)
        f.waiting -> StatusLine(WAITING, SignalLevel.ORANGE)
        else -> StatusLine(PREPARING, SignalLevel.BLACK)
    }

    fun forScreen(f: TransferFacts): String = of(f).text
    fun forNotification(f: TransferFacts): String = of(f).text
}
