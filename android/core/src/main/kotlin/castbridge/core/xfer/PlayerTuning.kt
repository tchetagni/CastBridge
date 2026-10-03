package castbridge.core.xfer

/**
 * R-16 : réglage de libVLC pour les petits processeurs quand une vidéo joue pendant une copie. Table de décision PURE ; l'activité n'en fait que des
 * options libVLC (`:drop-late-frames`, `:avcodec-skiploopfilter=`...) sur le média, et ne rouvre le fichier qu'une fois par palier de détresse.
 * Pensé pour TOUT TYPE de TV : AUCUN nom de décodeur ni de fabricant ici ; ce que la TV sait faire arrive par [Facts.hwCapable], mesuré à
 * l'exécution par `CodecCapabilityProbe` (couche fine, MediaCodecList) et traduit par [CodecMime].
 *
 * Règles :
 *  - décodage MATÉRIEL : forcé SEULEMENT si la sonde le déclare capable pour ce codec et cette résolution ([Facts.hwCapable] = true) ; « auto » (MediaCodec
 *    d'abord, libVLC repasse seul au logiciel) si on ne sait pas (null) ; logiciel si la sonde dit qu'aucun décodeur matériel ne convient (false) ou si
 *    l'utilisateur l'a coupé ([Facts.hwAvailable] = false). Le repli automatique existant (une erreur => un essai en logiciel, `hwOverride`) reste ;
 *  - TOUJOURS : on LÂCHE les images en retard plutôt que de figer l'image pendant que le son joue ([Tuning.dropLateFrames], [Tuning.skipFrames]) ;
 *    c'est la cause établie du gel (l'ancien `--no-drop-late-frames --no-skip-frames` faisait attendre libVLC chaque image) ; avec une copie en cours
 *    (ou une détresse), sur le chemin logiciel, on laisse un coeur au reste du système ([Tuning.threads] = coeurs - 1, au plus [MAX_THREADS]) ;
 *  - détresse durable (palier 2, [VideoStallDetector.sustained]), chemin logiciel ou inconnu : filtre de boucle sauté, images non de référence sautées,
 *    cache plus long (borné par la mémoire de la TV) ; l'IDCT sautée seulement en dernier recours (codec lourd au-dessus de 720p) ;
 *  - JAMAIS au démarrage : le filtre de boucle, les images de référence et l'IDCT sautés dégradent la qualité. Au repos : seuls les réglages d'images en retard changent.
 * Valeurs libVLC : skiploopfilter 0 aucun / 4 tous ; skip-frame et skip-idct 0 défaut / 1 non-référence. Effet NON mesuré sur une vraie TV (P-52).
 */
object PlayerTuning {
    /**
     * [distress] : 0 rien, 1 image figée ou images perdues, 2 durable ([PlaybackHealth.videoDistress]). [codec] : fourcc de libVLC, null = pas encore connu.
     * [hwCapable] : la sonde du système dit qu'un décodeur matériel traite ce codec à cette taille (true), aucun (false), on ne sait pas (null).
     * [maxMemoryBytes] : `Runtime.maxMemory()`. [is64Bit] : informatif (la logique ne dépend que des coeurs et de la mémoire).
     */
    data class Facts(val codec: String?, val width: Int, val height: Int, val hwAvailable: Boolean, val copyRunning: Boolean, val distress: Int, val cores: Int,
                     val hwCapable: Boolean? = null, val maxMemoryBytes: Long = 192L shl 20, val is64Bit: Boolean = false)

    /**
     * 0 = « ne pas toucher » pour les entiers (le défaut de libVLC), [fileCachingMs] 0 = laisser la valeur de [PlaybackPriority].
     * [hw] = demander le décodage matériel ; [forceHw] = sans repli de libVLC (seulement si la sonde l'a déclaré capable ; le repli de l'app reste).
     */
    data class Tuning(val hw: Boolean, val dropLateFrames: Boolean, val skipFrames: Boolean, val skipLoopFilter: Int, val skipFrame: Int, val skipIdct: Int,
                      val threads: Int, val fileCachingMs: Int, val forceHw: Boolean = false)

    /** Codecs lourds en logiciel (MPEG-4 ASP, MS-MPEG4, WMV, MPEG-2...) : seul l'IDCT sautée en dépend (liste de prudence). */
    private val SOFTWARE_LIKELY = setOf("mp4v", "divx", "div3", "div4", "dx50", "xvid", "fmp4", "mpg4", "3iv2", "wmv1", "wmv2", "wmv3", "wvc1", "vc1",
        "mpgv", "mp2v", "h263", "flv1", "mjpg", "vp6f", "rv40", "mp41", "mp42", "mp43")
    private const val HD_PIXELS = 1280 * 720
    const val MAX_THREADS = 4

    fun softwareLikely(codec: String?): Boolean = codec?.trim()?.lowercase() in SOFTWARE_LIKELY

    fun decide(f: Facts): Tuning {
        val active = f.copyRunning || f.distress > 0
        val sustained = f.distress >= 2
        val heavy = softwareLikely(f.codec) && f.width.toLong() * f.height > HD_PIXELS
        val hw = f.hwAvailable && f.hwCapable != false
        val forceHw = hw && f.hwCapable == true
        val libavcodec = !forceHw                              // only the software (or unknown/auto) path runs libavcodec: its knobs are moot on a forced hardware decoder
        return Tuning(
            hw = hw, forceHw = forceHw,
            dropLateFrames = true, skipFrames = true,               // never a frozen picture while the audio plays (was --no-drop-late-frames, see R-16)
            skipLoopFilter = if (sustained && libavcodec) 4 else 0,
            skipFrame = if (sustained && libavcodec) 1 else 0,
            skipIdct = if (sustained && libavcodec && heavy) 1 else 0,
            threads = if (active && libavcodec) (f.cores - 1).coerceIn(1, MAX_THREADS) else 0,
            fileCachingMs = if (sustained) PlaybackPriority.fileCachingMs(true, f.maxMemoryBytes).toInt() else 0,
        )
    }
}

/**
 * R-16 : pont PUR entre le fourcc que libVLC annonce pour une piste vidéo et le type MIME qu'Android (`MediaCodecList`) connaît. Aucun nom de décodeur :
 * la sonde ([castbridge.receiver.CodecCapabilityProbe]) interroge le système à l'exécution, pour n'importe quelle TV.
 */
object CodecMime {
    private val MIME = mapOf(
        "mp4v" to "video/mp4v-es", "divx" to "video/mp4v-es", "dx50" to "video/mp4v-es", "xvid" to "video/mp4v-es", "fmp4" to "video/mp4v-es",
        "mpg4" to "video/mp4v-es", "3iv2" to "video/mp4v-es",
        "h264" to "video/avc", "avc1" to "video/avc", "x264" to "video/avc",
        "hevc" to "video/hevc", "hev1" to "video/hevc", "hvc1" to "video/hevc", "h265" to "video/hevc",
        "mpgv" to "video/mpeg2", "mp2v" to "video/mpeg2", "mpeg" to "video/mpeg2",
        "vp80" to "video/x-vnd.on2.vp8", "vp90" to "video/x-vnd.on2.vp9", "vp09" to "video/x-vnd.on2.vp9",
        "h263" to "video/3gpp", "s263" to "video/3gpp",
        "av01" to "video/av01",
    )
    /** MS-MPEG4 v1-3 (DivX 3), WMV 1/2, FLV, VP6, RealVideo : pas de type MIME Android, donc jamais de décodeur matériel système (décodage logiciel). */
    private val NO_ANDROID_DECODER = setOf("div3", "div4", "div5", "mp41", "mp42", "mp43", "mpg3", "wmv1", "wmv2", "flv1", "vp6f", "vp6a", "rv10", "rv20", "rv30", "rv40")

    fun mimeOf(codec: String?): String? = MIME[codec?.trim()?.lowercase()]
    fun noAndroidDecoder(codec: String?): Boolean = codec?.trim()?.lowercase() in NO_ANDROID_DECODER

    /** Un décodeur logiciel d'après son NOM (avant Android 10, sans `isHardwareAccelerated`) : conventions génériques d'Android, pas de fabricant. */
    fun isSoftwareName(name: String): Boolean {
        val n = name.lowercase()
        return n.startsWith("c2.android.") || n.startsWith("omx.google.") || n.startsWith("c2.google.") || n.endsWith(".sw") || n.contains(".sw.")
    }

    /**
     * true = un décodeur matériel traite ce codec à cette taille, false = aucun, null = on ne sait pas (codec inconnu de la table, sonde en échec).
     * [probe] reçoit le type MIME et répond comme `CodecCapabilityProbe.hardware`.
     */
    fun hwCapable(codec: String?, probe: (String) -> Boolean?): Boolean? {
        if (codec.isNullOrBlank()) return null
        if (noAndroidDecoder(codec)) return false
        val mime = mimeOf(codec) ?: return null
        return runCatching { probe(mime) }.getOrNull()
    }
}
