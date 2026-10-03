package castbridge.core.tv

import castbridge.core.ux.DisplayTexts
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Affichage de la vidéo sur CastBridge-TV : où va l'image dans l'écran. Table de décision PURE (l'activité n'en fait que des appels libVLC
 * `setVideoScale`, rejoués à chaque événement `Vout` et à chaque changement de taille). Aucun modèle de TV ici : la taille réelle de la dalle
 * (720p, 1080p, 4K, 21:9...) arrive en paramètre, lue à l'exécution par l'activité.
 *
 * Modes (défaut [Mode.FIT]) :
 *  - FIT « Ajusté à l'écran » : le plus grand possible, proportions vraies (ni déformation ni rognage) ; des bandes seulement si le format diffère de celui de la dalle ;
 *  - FILL « Remplir l'écran » : couvre toute la dalle, le surplus est rogné au centre, jamais étiré ;
 *  - STRETCH « Étirer » : remplit la dalle sans respecter le format (déforme : l'écran le dit) ;
 *  - NATIVE « Natif » : taille native 1:1, centrée, sans mise à l'échelle ; réduite (et seulement réduite) si plus grande que la dalle.
 * Le format de pixel du fichier (SAR) et la rotation sont toujours honorés : un AVI anamorphique ne paraît pas écrasé.
 */
object VideoFit {
    enum class Mode(val key: String) { FIT("fit"), FILL("fill"), STRETCH("stretch"), NATIVE("native") }
    val DEFAULT = Mode.FIT

    /** Les gestes libVLC (`MediaPlayer.ScaleType`) : meilleur ajustement, remplissage rogné, étirement, taille d'origine. */
    enum class Scale { BEST_FIT, FIT_SCREEN, FILL, ORIGINAL }

    /** Tolérance (0,5 %) en dessous de laquelle deux formats sont « les mêmes » : 853x480 remplit une dalle 16:9 sans bande d'un pixel. */
    private const val SAME = 0.005

    /**
     * [shownW] x [shownH] : taille de l'image à l'écran, en pixels de la dalle, posée en ([x], [y]) (négatif = rogné) ; [barsX]/[barsY] : bande totale
     * (gauche + droite / haut + bas) ; [cropX]/[cropY] : rognage total. [aspectRatio] : toujours null, libVLC lit lui-même le SAR de la piste
     * (jamais de format forcé). [naturalW] x [naturalH] : taille « vraie » du fichier, SAR et rotation compris.
     */
    data class Plan(val mode: Mode, val scale: Scale, val aspectRatio: String?, val shownW: Int, val shownH: Int, val x: Int, val y: Int,
                    val barsX: Int, val barsY: Int, val cropX: Int, val cropY: Int, val distorts: Boolean, val naturalW: Int, val naturalH: Int) {
        val noBars get() = barsX == 0 && barsY == 0
        val scaled get() = scale != Scale.ORIGINAL
    }

    fun parse(key: String?): Mode? = Mode.values().firstOrNull { it.key == key?.trim()?.lowercase(Locale.ROOT) }

    /** Rotation en degrés d'après `Media.VideoTrack.orientation` de libVLC (EXIF-like : 4..7 = quart de tour, 2 et 3 = demi-tour). */
    fun rotationOf(orientation: Int): Int = when (orientation) { 4, 5, 6, 7 -> 90; 2, 3 -> 180; else -> 0 }

    /** Réglage du fichier d'abord, puis celui de l'utilisateur, puis [DEFAULT]. */
    fun effective(fileOverride: String?, global: String?): Mode = parse(fileOverride) ?: parse(global) ?: DEFAULT

    fun decide(videoW: Int, videoH: Int, sarNum: Int, sarDen: Int, rotation: Int, panelW: Int, panelH: Int, mode: Mode): Plan {
        if (videoW <= 0 || videoH <= 0 || panelW <= 0 || panelH <= 0) {
            // Taille encore inconnue : on laisse libVLC ajuster, sans rien inventer.
            return Plan(mode, if (mode == Mode.STRETCH) Scale.FILL else Scale.BEST_FIT, null, max(panelW, 0), max(panelH, 0), 0, 0, 0, 0, 0, 0, false, 0, 0)
        }
        val sar = if (sarNum > 0 && sarDen > 0) sarNum.toDouble() / sarDen else 1.0
        val rot = ((rotation % 360) + 360) % 360
        val turned = rot in 45..134 || rot in 225..314
        val dw0 = videoW * sar; val dh0 = videoH.toDouble()
        val dw = if (turned) dh0 else dw0; val dh = if (turned) dw0 else dh0
        val nw = dw.roundToInt().coerceAtLeast(1); val nh = dh.roundToInt().coerceAtLeast(1)
        val sameAspect = abs((dw / dh) / (panelW.toDouble() / panelH) - 1) <= SAME

        fun plan(scale: Scale, w: Int, h: Int, distorts: Boolean = false): Plan =
            Plan(mode, scale, null, w, h, (panelW - w) / 2, (panelH - h) / 2, max(panelW - w, 0), max(panelH - h, 0), max(w - panelW, 0), max(h - panelH, 0), distorts, nw, nh)
        fun fit(): Plan {
            if (sameAspect) return plan(Scale.BEST_FIT, panelW, panelH)
            val s = min(panelW / dw, panelH / dh)
            return plan(Scale.BEST_FIT, min((dw * s).roundToInt(), panelW), min((dh * s).roundToInt(), panelH))
        }
        return when (mode) {
            Mode.FIT -> fit()
            Mode.FILL -> if (sameAspect) plan(Scale.FIT_SCREEN, panelW, panelH) else {
                val s = max(panelW / dw, panelH / dh)
                plan(Scale.FIT_SCREEN, max((dw * s).roundToInt(), panelW), max((dh * s).roundToInt(), panelH))
            }
            Mode.STRETCH -> plan(Scale.FILL, panelW, panelH, distorts = !sameAspect)
            Mode.NATIVE -> if (nw <= panelW && nh <= panelH) plan(Scale.ORIGINAL, nw, nh) else fit()
        }
    }

    /** « Affichage : Ajusté à l'écran 1920×1080 → 1280×720 » (taille du fichier → taille à l'écran). */
    fun infoLine(p: Plan, videoW: Int, videoH: Int, panelW: Int, panelH: Int): String {
        val to = if (p.mode == Mode.FILL) "${panelW}×$panelH" else "${p.shownW}×${p.shownH}"
        val extra = if (p.distorts) " (image déformée)" else ""
        return "Affichage : ${DisplayTexts.label(p.mode)} ${videoW}×$videoH → $to$extra"
    }
}

/**
 * Qualité d'image, honnête et prudente pour les petits processeurs. Décision PURE ; l'activité ne fait que passer [Quality.options] à libVLC.
 * La détresse ([Facts.distress] > 0, voir `PlayerTuning`/R-16) GAGNE TOUJOURS : aucune option de qualité alors. Rien n'est forcé de ce qui dégrade
 * (pas de RV16, pas de `--android-display-chroma`), rien n'est ajouté au mode Natif (aucun filtre, aucune mise à l'échelle logicielle).
 * Les options décoder (`:avcodec-*`, boucle, threads) restent à `PlayerTuning` : ici on ne les touche jamais.
 */
object PictureQuality {
    enum class Cpu { LOW, NORMAL }
    /** Un processeur de 4 coeurs ou moins est « faible » (la TV de référence) ; la logique ne lit que le nombre de coeurs. */
    fun cpuClass(cores: Int) = if (cores <= 4) Cpu.LOW else Cpu.NORMAL

    /**
     * [interlaced] : null = la piste ne le dit pas. [hwDecoder] : un décodeur matériel (surface opaque) traite la piste ; null = on ne sait pas.
     * [softwareScaler] : l'image passe par le convertisseur logiciel de libVLC (swscale) ; false/null = chemin matériel/surface.
     * [highDynamic] : 10 bits / HDR si connu.
     */
    data class Facts(val mode: VideoFit.Mode, val codec: String?, val width: Int, val height: Int, val interlaced: Boolean?, val hwDecoder: Boolean?,
                     val softwareScaler: Boolean?, val cores: Int, val copyRunning: Boolean, val distress: Int, val panelW: Int, val panelH: Int,
                     val highDynamic: Boolean? = null)

    /** [deinterlace] : null = ne rien demander, 0 coupé, 1 forcé, -1 automatique (libVLC ne filtre que ce que le décodeur marque entrelacé). */
    data class Quality(val deinterlace: Int?, val deinterlaceMode: String?, val swscaleMode: Int?, val notes: List<String>) {
        /** Options de média libVLC ; vide = rien à toucher. */
        val options: List<String> get() = buildList {
            deinterlace?.let { add(":deinterlace=$it") }
            deinterlaceMode?.let { add(":deinterlace-mode=$it") }
            swscaleMode?.let { add(":swscale-mode=$it") }
        }
    }

    /** `--swscale-mode` de libVLC : 2 = bicubique (défaut), 9 = lanczos. */
    const val SWSCALE_LANCZOS = 9

    fun decide(f: Facts): Quality {
        val notes = ArrayList<String>()
        if (f.highDynamic == true) notes += "10 bits / HDR : pas de tone mapping dans libVLC 3.6 pour Android ; l'image dépend du décodeur et de la TV"
        if (f.distress > 0) return Quality(0, null, null, notes + "Lecture allégée : aucun filtre d'image")
        val native = f.mode == VideoFit.Mode.NATIVE
        val weak = cpuClass(f.cores) == Cpu.LOW || f.copyRunning
        val di: Int; var diMode: String? = null
        when {
            f.interlaced == true -> { di = 1; diMode = if (weak) "blend" else "yadif" }
            f.interlaced == null && !native -> { di = -1; diMode = if (weak) "blend" else "yadif" }
            else -> di = 0
        }
        if (di != 0 && f.hwDecoder == true) notes += "Désentrelacement non garanti sur une surface matérielle (non vérifié)"
        // Mise à l'échelle logicielle de meilleure qualité : jamais sur un petit processeur, jamais pendant une copie, jamais en Natif, jamais sur le chemin matériel.
        val sw = if (!native && f.softwareScaler == true && !weak) SWSCALE_LANCZOS else null
        return Quality(di, diMode, sw, notes)
    }
}
