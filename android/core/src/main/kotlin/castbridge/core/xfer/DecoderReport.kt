package castbridge.core.xfer

import java.util.Locale

/**
 * R-16 : le relevé honnête « quel décodeur a réellement servi ? » de l'écran d'infos du lecteur. libVLC 3.6.5 ne dit PAS s'il a choisi MediaCodec ou
 * libavcodec : on affiche donc ce qui est SÛR (le codec du fichier, ce que l'app a demandé, ce que la sonde du système dit de la TV, les images
 * affichées et perdues comptées par libVLC) et, séparément, un INDICE (le temps de processeur de l'app par image affichée) clairement marqué comme tel.
 * Pur : aucune E/S.
 */
object DecoderReport {
    /** Au-dessous : décodeur matériel probable ; au-dessus : logiciel probable (par image de 720p ; ramené à la taille réelle). Indicatif, à étalonner sur le terrain (P-52). */
    const val HW_MS_PER_FRAME = 3.0
    const val SW_MS_PER_FRAME = 10.0
    private const val PIXELS_720P = 1280.0 * 720.0

    fun verdict(cpuMsPerFrame: Double?, width: Int, height: Int): String {
        if (cpuMsPerFrame == null) return "indice indisponible (pas encore assez d'images)"
        val scale = if (width > 0 && height > 0) (width * height) / PIXELS_720P else 1.0
        val per720 = cpuMsPerFrame / scale.coerceAtLeast(0.1)
        val v = String.format(Locale.FRANCE, "%.1f ms de processeur par image (ramené à 720p : %.1f)", cpuMsPerFrame, per720)
        return when {
            per720 <= HW_MS_PER_FRAME -> "matériel probable, $v"
            per720 >= SW_MS_PER_FRAME -> "logiciel probable, $v"
            else -> "indéterminé, $v"
        }
    }

    fun lines(codec: String?, width: Int, height: Int, tuning: PlayerTuning.Tuning?, capable: Boolean?, cpuMsPerFrame: Double?, displayed: Int, lost: Int, stage: Int): List<String> {
        val out = ArrayList<String>()
        val mime = CodecMime.mimeOf(codec)
        out += "Codec vidéo : ${codec?.takeIf { it.isNotBlank() } ?: "?"}" + (mime?.let { " ($it)" } ?: "") + if (width > 0) ", ${width}x$height" else ""
        out += "Décodeur demandé : " + when {
            tuning == null -> "pas encore décidé"
            tuning.forceHw -> "matériel forcé (la TV déclare un décodeur pour ce format et cette taille ; repli logiciel de l'app si échec)"
            tuning.hw -> "automatique (matériel d'abord, libVLC repasse seul au logiciel)"
            else -> "logiciel (libavcodec)"
        }
        out += "Capacité matérielle de la TV : " + when (capable) {
            true -> "un décodeur matériel traite ce format à cette taille"
            false -> "aucun décodeur matériel pour ce format : logiciel"
            null -> "inconnue"
        }
        out += "Décodeur réellement utilisé : non lisible dans libVLC ; " + verdict(cpuMsPerFrame, width, height)
        val total = displayed + lost
        out += "Images : $displayed affichées, $lost perdues" + if (total > 0) String.format(Locale.FRANCE, " (%.1f %%)", lost * 100.0 / total) else ""
        if (stage > 0) out += "Lecture allégée : palier $stage (images en retard abandonnées" + if (stage >= 2) ", filtre de boucle sauté)" else ")"
        return out
    }
}
