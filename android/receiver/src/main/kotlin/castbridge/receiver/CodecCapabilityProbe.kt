package castbridge.receiver

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import castbridge.core.xfer.CodecMime

/**
 * R-16 : ce que CETTE TV sait décoder en matériel, demandé au système à l'exécution (`MediaCodecList`), pour n'importe quel modèle : aucun nom de
 * décodeur ni de fabricant dans le code. Couche fine : la décision est `castbridge.core.xfer.PlayerTuning.decide`, le pont fourcc -> MIME est
 * `castbridge.core.xfer.CodecMime`. Ne lève jamais : une sonde en échec répond « inconnu » (null).
 */
object CodecCapabilityProbe {
    /** Aucune ligne de la liste n'est gardée : `MediaCodecList` est lu à chaque demande (quelques dizaines de microsecondes, une fois par fichier). */
    private fun decoders(mime: String): List<MediaCodecInfo> =
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { info -> !info.isEncoder && info.supportedTypes.any { it.equals(mime, true) } }

    private fun isHardware(i: MediaCodecInfo): Boolean =
        if (Build.VERSION.SDK_INT >= 29) i.isHardwareAccelerated && !i.isSoftwareOnly else !CodecMime.isSoftwareName(i.name)

    private fun sizeOk(i: MediaCodecInfo, mime: String, w: Int, h: Int): Boolean {
        if (w <= 0 || h <= 0) return true
        val caps = runCatching { i.getCapabilitiesForType(mime).videoCapabilities }.getOrNull() ?: return true
        return runCatching { caps.isSizeSupported(w, h) || caps.isSizeSupported(h, w) }.getOrDefault(true)
    }

    /** true = un décodeur matériel traite [mime] à [width]x[height] (0 = taille inconnue), false = aucun, null = le système n'a pas répondu. */
    fun hardware(mime: String, width: Int, height: Int): Boolean? = runCatching {
        decoders(mime).filter(::isHardware).any { sizeOk(it, mime, width, height) }
    }.getOrNull()

    /** Une ligne lisible pour l'écran d'infos : les décodeurs matériels que le système annonce pour ce type (noms lus à l'exécution). */
    fun describe(mime: String): String = runCatching {
        val hw = decoders(mime).filter(::isHardware)
        if (hw.isEmpty()) "aucun décodeur matériel déclaré pour $mime" else "décodeurs matériels déclarés pour $mime : " + hw.joinToString(", ") { it.name }
    }.getOrDefault("système sans réponse")
}
