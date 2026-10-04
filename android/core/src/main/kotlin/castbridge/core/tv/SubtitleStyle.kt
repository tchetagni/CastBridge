package castbridge.core.tv

/**
 * Style des sous-titres (wtv-01 n°7) : pur. Couleur, contour/ombre, position verticale, encodage des caractères, police de repli.
 * Le style par défaut ne produit AUCUNE option libVLC (rien ne change). Le moteur de sous-titres relit ces options à sa création : le lecteur est rouvert à la même position.
 */
data class SubtitleStyle(
    val color: Color = Color.WHITE,
    val outline: Outline = Outline.NORMAL,
    /** Distance au bord bas de l'écran, en pour cent de la hauteur (0..40). */
    val bottomPercent: Int = 0,
    val encoding: String = "auto",
    val font: String = "default",
) {
    enum class Color(val key: String, val label: String, val rgb: Int) { WHITE("w", "Blanc", 0xFFFFFF), YELLOW("y", "Jaune", 0xFFFF00), GREEN("g", "Vert", 0x00FF00), CYAN("c", "Cyan", 0x00FFFF), ORANGE("o", "Orange", 0xFF8000) }
    enum class Outline(val key: String, val label: String, val thickness: Int, val shadow: Int) {
        NONE("n", "Aucun", 0, 0), THIN("t", "Fin", 2, 0), NORMAL("m", "Normal", 4, 128), THICK("k", "Épais", 6, 128), SHADOW("s", "Ombre seule", 0, 255)
    }
    class Enc(val key: String, val label: String)

    companion object {
        const val MAX_BOTTOM = 40
        /** auto = libVLC choisit ; les autres forcent l'encodage du fichier .srt. */
        val ENCODINGS = listOf(Enc("auto", "Automatique"), Enc("UTF-8", "UTF-8"), Enc("ISO-8859-1", "Latin-1"), Enc("Windows-1252", "Windows-1252"), Enc("ISO-8859-15", "Latin-9 (avec €)"), Enc("UTF-16", "UTF-16"))
        val FONTS = listOf("default" to "Police du système", "sans-serif" to "Sans empattement", "serif" to "Avec empattement", "monospace" to "Chasse fixe")
        const val PREVIEW = "Aperçu : « Où est passé l'été ? » (é è ç à ù €)"
        fun clampBottom(p: Int) = p.coerceIn(0, MAX_BOTTOM)
        fun decode(s: String?): SubtitleStyle {
            var t = SubtitleStyle()
            for (tok in s.orEmpty().split(',')) {
                if (tok.length < 2) continue
                val v = tok.substring(1)
                when (tok[0]) {
                    'c' -> Color.values().firstOrNull { it.key == v }?.let { t = t.copy(color = it) }
                    'o' -> Outline.values().firstOrNull { it.key == v }?.let { t = t.copy(outline = it) }
                    'p' -> v.toIntOrNull()?.let { t = t.copy(bottomPercent = clampBottom(it)) }
                    'e' -> ENCODINGS.firstOrNull { it.key == v }?.let { t = t.copy(encoding = it.key) }
                    'f' -> FONTS.firstOrNull { it.first == v }?.let { t = t.copy(font = it.first) }
                }
            }
            return t
        }
    }

    val isDefault: Boolean get() = this == SubtitleStyle()

    /** Options libVLC (au format « --option=valeur ») ; aucune quand tout est par défaut. [panelHeightPx] : hauteur de la dalle, pour la position. */
    fun options(panelHeightPx: Int = 720): List<String> = buildList {
        if (color != Color.WHITE) add("--freetype-color=${color.rgb}")
        if (outline != Outline.NORMAL) { add("--freetype-outline-thickness=${outline.thickness}"); add("--freetype-shadow-opacity=${outline.shadow}") }
        if (bottomPercent > 0) add("--sub-margin=${panelHeightPx * clampBottom(bottomPercent) / 100}")
        if (encoding != "auto") add("--subsdec-encoding=$encoding")
        if (font != "default") add("--freetype-font=$font")
    }

    fun encode(): String = buildList {
        if (color != Color.WHITE) add("c${color.key}"); if (outline != Outline.NORMAL) add("o${outline.key}")
        if (bottomPercent > 0) add("p$bottomPercent"); if (encoding != "auto") add("e$encoding"); if (font != "default") add("f$font")
    }.joinToString(",")
}
