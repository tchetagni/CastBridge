package castbridge.core.ux

import castbridge.core.brand.BrandTokens

/** Thème de CastBridge-TV : CLAIR par défaut (comme le téléphone, mêmes jetons [BrandTokens.Light]), SOMBRE au choix. */
enum class TvTheme(val key: String, val label: String) {
    LIGHT("light", "Clair"), DARK("dark", "Sombre");

    val other get() = if (this == LIGHT) DARK else LIGHT

    companion object {
        val DEFAULT = LIGHT
        const val PREF = "theme"
        fun parse(s: String?): TvTheme = entries.firstOrNull { it.key == s } ?: DEFAULT
    }
}

/** Où l'on est : les menus suivent le thème ; le lecteur et les superpositions sur une image ou une vidéo restent sombres, quel que soit le thème. */
enum class TvPlace { MENU, PLAYER, MEDIA_OVERLAY;
    val alwaysDark get() = this != MENU
}

/** Les couleurs (ARGB) d'un écran de la TV. [ringDp] : épaisseur de l'anneau de focus (plus épaisse sur fond clair). */
class TvPalette(
    val bg: Int, val bgElevated: Int, val card: Int, val cardFocus: Int, val outline: Int,
    val text: Int, val text2: Int, val text3: Int,
    val accent: Int, val onAccent: Int, val ring: Int, val ringDp: Int,
    val good: Int, val goodText: Int, val warn: Int, val error: Int, val info: Int,
    /** Pastilles de signalétique vert / orange / rouge / noir (formes distinctes : cercle, triangle, carré, anneau), lisibles sur le fond du thème. */
    val sigGreen: Int, val sigOrange: Int, val sigRed: Int, val sigBlackFill: Int, val sigBlackOutline: Int,
)

object TvThemeRules {
    private val DARK_PALETTE = BrandTokens.Dark.let { d ->
        TvPalette(
            bg = d.BACKGROUND, bgElevated = d.BACKGROUND_ELEVATED, card = d.SURFACE, cardFocus = d.SURFACE_HIGH, outline = d.OUTLINE,
            text = d.TEXT_HIGH, text2 = d.TEXT_MEDIUM, text3 = d.TEXT_LOW,
            accent = d.PRIMARY, onAccent = d.ON_PRIMARY, ring = d.FOCUS_RING, ringDp = BrandTokens.FOCUS_RING_WIDTH_DP,
            good = d.SECONDARY, goodText = BrandTokens.Semantic.SUCCESS_DARK, warn = BrandTokens.Semantic.WARNING_DARK,
            error = BrandTokens.Semantic.ERROR_DARK, info = BrandTokens.Semantic.INFO_DARK,
            sigGreen = SignalColors.GREEN, sigOrange = SignalColors.ORANGE, sigRed = SignalColors.RED,
            sigBlackFill = SignalColors.BLACK_FILL, sigBlackOutline = SignalColors.BLACK_OUTLINE,
        )
    }

    /** Les mêmes jetons que le téléphone. Cartes un cran plus sombres que le fond (SURFACE_VARIANT), carte focalisée blanche ; anneau ambre foncé de 4 dp. */
    private val LIGHT_PALETTE = BrandTokens.Light.let { l ->
        TvPalette(
            bg = l.BACKGROUND, bgElevated = l.SURFACE_VARIANT, card = l.SURFACE_VARIANT, cardFocus = l.SURFACE, outline = l.OUTLINE,
            text = l.TEXT_HIGH, text2 = l.TEXT_MEDIUM, text3 = l.TEXT_LOW,
            accent = l.PRIMARY, onAccent = l.ON_PRIMARY, ring = l.PRIMARY, ringDp = BrandTokens.FOCUS_RING_WIDTH_DP + 1,
            good = l.SECONDARY, goodText = BrandTokens.Semantic.SUCCESS_LIGHT, warn = 0xFF8A5A00.toInt(),   // WARNING_LIGHT assombri : 4,5:1 exigé sur les cartes (SURFACE_VARIANT) aussi
            
            error = BrandTokens.Semantic.ERROR_LIGHT, info = BrandTokens.Semantic.INFO_LIGHT,
            sigGreen = 0xFF1C7C53.toInt(), sigOrange = 0xFFB54708.toInt(), sigRed = 0xFFC5343A.toInt(),
            sigBlackFill = BrandTokens.Semantic.OFF_LIGHT, sigBlackOutline = BrandTokens.Semantic.OFF_RING_LIGHT,
        )
    }

    fun palette(theme: TvTheme, place: TvPlace = TvPlace.MENU): TvPalette =
        if (place.alwaysDark || theme == TvTheme.DARK) DARK_PALETTE else LIGHT_PALETTE

    /** Luminance relative WCAG d'une couleur ARGB (l'alpha est ignoré). */
    fun luminance(argb: Int): Double {
        fun ch(v: Int): Double { val c = v / 255.0; return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4) }
        return 0.2126 * ch((argb shr 16) and 0xFF) + 0.7152 * ch((argb shr 8) and 0xFF) + 0.0722 * ch(argb and 0xFF)
    }

    /** Rapport de contraste WCAG entre deux couleurs opaques (de 1 à 21). */
    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a); val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    const val MIN_TEXT = 4.5
    const val MIN_RING = 3.0

    class Pair(val name: String, val fg: Int, val bg: Int, val min: Double)

    /** Les paires texte/fond (4,5:1) et anneau/fond (3:1) que la TV affiche réellement avec cette palette. */
    fun pairs(p: TvPalette): List<Pair> = buildList {
        for ((bn, b) in listOf("bg" to p.bg, "card" to p.card, "cardFocus" to p.cardFocus, "bgElevated" to p.bgElevated)) {
            add(Pair("text/$bn", p.text, b, MIN_TEXT)); add(Pair("text2/$bn", p.text2, b, MIN_TEXT)); add(Pair("text3/$bn", p.text3, b, MIN_TEXT))
            add(Pair("accent/$bn", p.accent, b, MIN_TEXT)); add(Pair("goodText/$bn", p.goodText, b, MIN_TEXT))
            add(Pair("warn/$bn", p.warn, b, MIN_TEXT)); add(Pair("error/$bn", p.error, b, MIN_TEXT)); add(Pair("info/$bn", p.info, b, MIN_TEXT))
        }
        for ((bn, b) in listOf("bg" to p.bg, "card" to p.card)) {
            add(Pair("sigGreen/$bn", p.sigGreen, b, MIN_TEXT)); add(Pair("sigOrange/$bn", p.sigOrange, b, MIN_TEXT)); add(Pair("sigRed/$bn", p.sigRed, b, MIN_TEXT))
            add(Pair("sigBlackOutline/$bn", p.sigBlackOutline, b, MIN_RING))
        }
        add(Pair("onAccent/accent", p.onAccent, p.accent, MIN_TEXT))
        for ((bn, b) in listOf("bg" to p.bg, "card" to p.card, "cardFocus" to p.cardFocus)) add(Pair("ring/$bn", p.ring, b, MIN_RING))
    }
}
