package castbridge.core.tv

/**
 * Réglages d'image (wtv-01 n°5) : purs. TOUT est désactivé par défaut (valeurs neutres) : chaque filtre coûte du processeur sur une TV 32 bits.
 * Les valeurs sont en pour cent (100 = neutre) ; libVLC reçoit [vlcLuma]/[vlcContrast]... (1,00 = neutre). Bornes : voir [Bounds].
 */
data class PictureTuning(
    val brightness: Int = 100,
    val contrast: Int = 100,
    val saturation: Int = 100,
    val gamma: Int = 100,
    val zoom: Int = 100,
    val panX: Int = 0,
    val panY: Int = 0,
    val deinterlace: Boolean = false,
    val rotation: Int = 0,
) {
    enum class Field(val key: String, val label: String, val min: Int, val max: Int, val step: Int, val neutral: Int) {
        BRIGHTNESS("b", "Luminosité", 50, 150, 10, 100),
        CONTRAST("c", "Contraste", 50, 150, 10, 100),
        SATURATION("s", "Saturation", 0, 200, 10, 100),
        GAMMA("g", "Gamma", 50, 300, 10, 100),
        ZOOM("z", "Zoom", 100, 300, 25, 100),
        PAN_X("x", "Déplacement horizontal", -100, 100, 10, 0),
        PAN_Y("y", "Déplacement vertical", -100, 100, 10, 0);
        fun clamp(v: Int) = v.coerceIn(min, max)
    }

    companion object {
        val ROTATIONS = listOf(0, 90, 180, 270)
        fun get(t: PictureTuning, f: Field) = when (f) {
            Field.BRIGHTNESS -> t.brightness; Field.CONTRAST -> t.contrast; Field.SATURATION -> t.saturation; Field.GAMMA -> t.gamma
            Field.ZOOM -> t.zoom; Field.PAN_X -> t.panX; Field.PAN_Y -> t.panY
        }
        /** Une valeur hors bornes est ramenée dans les bornes ; un déplacement sans zoom est sans effet (remis à 0). */
        fun set(t: PictureTuning, f: Field, v: Int): PictureTuning {
            val c = f.clamp(v)
            val r = when (f) {
                Field.BRIGHTNESS -> t.copy(brightness = c); Field.CONTRAST -> t.copy(contrast = c); Field.SATURATION -> t.copy(saturation = c)
                Field.GAMMA -> t.copy(gamma = c); Field.ZOOM -> t.copy(zoom = c); Field.PAN_X -> t.copy(panX = c); Field.PAN_Y -> t.copy(panY = c)
            }
            return if (r.zoom == 100) r.copy(panX = 0, panY = 0) else r
        }
        fun withRotation(t: PictureTuning, deg: Int): PictureTuning = t.copy(rotation = ((deg % 360) + 360) % 360 / 90 * 90)
        fun decode(s: String?): PictureTuning {
            var t = PictureTuning()
            for (tok in s.orEmpty().split(',')) {
                if (tok.length < 2) continue
                val n = tok.substring(1).toIntOrNull() ?: continue
                val k = tok[0].toString()
                Field.values().firstOrNull { it.key == k }?.let { t = set(t, it, n) }
                if (k == "d") t = t.copy(deinterlace = n == 1)
                if (k == "r") t = withRotation(t, n)
            }
            return t
        }
    }

    /** Un filtre de traitement d'image est actif : c'est ce qui coûte du processeur (zoom et rotation sont de simples transformations). */
    val filtersActive: Boolean get() = brightness != 100 || contrast != 100 || saturation != 100 || gamma != 100 || deinterlace
    val isDefault: Boolean get() = this == PictureTuning()

    /** Ce que fait la garde de performance : coupe les filtres, garde le zoom et la rotation. */
    fun withoutFilters() = copy(brightness = 100, contrast = 100, saturation = 100, gamma = 100, deinterlace = false)

    val vlcBrightness get() = brightness / 100f
    val vlcContrast get() = contrast / 100f
    val vlcSaturation get() = saturation / 100f
    val vlcGamma get() = gamma / 100f

    /** Zoom, déplacement et rotation sont de simples transformations de la vue (aucun filtre) ; [w] x [h] : la dalle. */
    data class ViewTransform(val scale: Float, val translateX: Float, val translateY: Float, val rotation: Float)

    fun viewTransform(w: Int, h: Int): ViewTransform {
        val z = zoom / 100f
        // tourné de 90/270 degrés, l'image doit rentrer dans la dalle : on la réduit du rapport de la dalle
        val fitRot = if (rotation % 180 == 90 && w > 0 && h > 0) minOf(w / h.toFloat(), h / w.toFloat()) else 1f
        return ViewTransform(z * fitRot, panX / 100f * w * (z - 1f) / 2f, panY / 100f * h * (z - 1f) / 2f, rotation.toFloat())
    }

    /** Options libVLC du filtre « adjust » et du désentrelacement forcé : AUCUNE tant que rien n'est réglé (rien ne change au repos). */
    fun mediaOptions(): List<String> = buildList {
        if (brightness != 100 || contrast != 100 || saturation != 100 || gamma != 100) {
            add(":video-filter=adjust")
            if (brightness != 100) add(":brightness=$vlcBrightness"); if (contrast != 100) add(":contrast=$vlcContrast")
            if (saturation != 100) add(":saturation=$vlcSaturation"); if (gamma != 100) add(":gamma=$vlcGamma")
        }
        if (deinterlace) { add(":deinterlace=1"); add(":deinterlace-mode=auto") }
    }

    fun encode(): String = buildList {
        if (brightness != 100) add("b$brightness"); if (contrast != 100) add("c$contrast"); if (saturation != 100) add("s$saturation")
        if (gamma != 100) add("g$gamma"); if (zoom != 100) add("z$zoom"); if (panX != 0) add("x$panX"); if (panY != 0) add("y$panY")
        if (deinterlace) add("d1"); if (rotation != 0) add("r$rotation")
    }.joinToString(",")
}

/**
 * Garde de performance (wtv-01 §1) : si le décodeur perd des images alors qu'un filtre d'image est actif, le filtre se coupe seul (et le dit).
 * Pure : on lui donne les compteurs CUMULÉS de libVLC (images affichées, images perdues) ; elle compare ce qui s'est passé depuis [arm].
 */
class PerformanceGuard(private val minFrames: Int = 120, private val maxLostPercent: Int = 10) {
    private var baseShown = 0; private var baseLost = 0
    var tripped = false; private set

    /** À appeler quand un filtre vient d'être activé (ou un nouveau fichier) : repart des compteurs actuels. */
    fun arm(displayed: Int, lost: Int) { baseShown = displayed; baseLost = lost; tripped = false }

    /** Vrai UNE seule fois : il faut couper les filtres maintenant. */
    fun check(displayed: Int, lost: Int, filtersActive: Boolean): Boolean {
        if (!filtersActive || tripped) return false
        val shown = displayed - baseShown; val gone = lost - baseLost
        if (shown + gone < minFrames) return false
        if (gone * 100 <= (shown + gone) * maxLostPercent) return false
        tripped = true
        return true
    }

    companion object { const val MESSAGE = "Filtres d'image coupés : la TV perdait des images" }
}
