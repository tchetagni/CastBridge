package castbridge.core.brand

/**
 * Palette EFFECTIVE de la marque en cours (marque blanche, docs/WHITE-LABEL.md § Couleurs).
 *
 * Sans surcharge (CastBridge) chaque valeur est EXACTEMENT la constante de [BrandTokens] : le build par défaut est identique
 * (BrandPaletteTest le prouve). Avec une marque, [init] reçoit les clés `color.*` de brand.properties et les valeurs sont
 * dérivées des RÔLES fournis (voir le tableau du document). Les palettes de sous-marque (Quiz des Millions, Échecs, Apprendre)
 * ne passent pas par ici : elles restent celles de [BrandTokens].
 */
object BrandPalette {
    @Volatile private var c: Map<String, Int> = emptyMap()

    /** Vrai si au moins un rôle de couleur est fourni par la marque. */
    val isCustom: Boolean get() = c.isNotEmpty()

    /** [spec] : « clé=#RRGGBB;clé=#RRGGBB » (clés sans le préfixe `color.`). Vide = CastBridge. */
    fun init(spec: String) {
        c = spec.split(';').mapNotNull {
            val kv = it.split('=', limit = 2)
            if (kv.size != 2) null else parse(kv[1].trim())?.let { v -> kv[0].trim() to v }
        }.toMap()
    }

    fun parse(hex: String): Int? {
        val h = hex.removePrefix("#")
        return when (h.length) { 6 -> h.toLongOrNull(16)?.let { (0xFF000000L or it).toInt() }; 8 -> h.toLongOrNull(16)?.toInt(); else -> null }
    }

    private fun r(x: Int) = (x shr 16) and 255
    private fun g(x: Int) = (x shr 8) and 255
    private fun b(x: Int) = x and 255

    /** [a] sur [bg] à la proportion [t] (0..1) de [a]. */
    fun mix(a: Int, bg: Int, t: Double): Int {
        fun ch(f: (Int) -> Int) = Math.round(f(a) * t + f(bg) * (1 - t)).toInt()
        return (0xFF shl 24) or (ch(::r) shl 16) or (ch(::g) shl 8) or ch(::b)
    }

    private fun lin(v: Int): Double { val s = v / 255.0; return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4) }
    fun luminance(x: Int) = 0.2126 * lin(r(x)) + 0.7152 * lin(g(x)) + 0.0722 * lin(b(x))
    fun contrast(a: Int, bg: Int): Double { val l1 = luminance(a); val l2 = luminance(bg); return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05) }

    /** [fg] assombri (vers le noir) jusqu'à ≥ 4,5:1 sur [bg] : pour un usage en TEXTE d'une couleur de remplissage. */
    fun readableOn(fg: Int, bg: Int): Int {
        var x = fg; var i = 0
        while (contrast(x, bg) < 4.5 && i++ < 40) x = mix(x, 0xFF000000.toInt(), 0.92)
        return x
    }

    /** [fg] éclairci (vers le blanc) jusqu'à ≥ 4,5:1 sur [bg] : usage en TEXTE sur fond sombre. */
    fun readableOnDark(fg: Int, bg: Int): Int {
        var x = fg; var i = 0
        while (contrast(x, bg) < 4.5 && i++ < 40) x = mix(x, 0xFFFFFFFF.toInt(), 0.92)
        return x
    }

    private const val WHITE = 0xFFFFFFFF.toInt()
    private fun k(key: String) = c[key]

    object Light {
        val BACKGROUND get() = k("background") ?: BrandTokens.Light.BACKGROUND
        val SURFACE get() = k("background") ?: BrandTokens.Light.SURFACE
        val SURFACE_VARIANT get() = k("background")?.let { bg -> mix(k("primary") ?: bg, bg, 0.07) } ?: BrandTokens.Light.SURFACE_VARIANT
        val OUTLINE get() = k("background")?.let { bg -> mix(TEXT_HIGH, bg, 0.2) } ?: BrandTokens.Light.OUTLINE
        val TEXT_HIGH get() = k("text") ?: BrandTokens.Light.TEXT_HIGH
        val TEXT_MEDIUM get() = k("text")?.let { mix(it, BACKGROUND, 0.82) } ?: BrandTokens.Light.TEXT_MEDIUM
        val TEXT_LOW get() = k("text")?.let { mix(it, BACKGROUND, 0.72) } ?: BrandTokens.Light.TEXT_LOW
        val PRIMARY get() = k("primary") ?: BrandTokens.Light.PRIMARY
        val ON_PRIMARY get() = k("onPrimary") ?: BrandTokens.Light.ON_PRIMARY
        /** Remplissages vert / cyan : le texte posé dessus est le texte foncé de la marque. */
        val SECONDARY get() = k("success") ?: BrandTokens.Light.SECONDARY
        val ON_SECONDARY get() = if (k("success") != null) TEXT_HIGH else BrandTokens.Light.ON_SECONDARY
        val ACCENT get() = k("info") ?: BrandTokens.Light.ACCENT
        val ON_ACCENT get() = if (k("info") != null) TEXT_HIGH else BrandTokens.Light.ON_PRIMARY
        val FOCUS_RING get() = k("focus")?.let { PRIMARY } ?: BrandTokens.Light.FOCUS_RING
    }

    /** Thème sombre du téléphone ET écrans de la TV (fond marine de la marque). */
    object Dark {
        val BACKGROUND get() = k("tvBackground") ?: BrandTokens.Dark.BACKGROUND
        val BACKGROUND_ELEVATED get() = k("tvBackground")?.let { mix(it, SURFACE, 0.5) } ?: BrandTokens.Dark.BACKGROUND_ELEVATED
        val SURFACE get() = k("tvSurface") ?: BrandTokens.Dark.SURFACE
        val SURFACE_HIGH get() = k("tvSurface2") ?: BrandTokens.Dark.SURFACE_HIGH
        val OUTLINE get() = k("tvBackground")?.let { mix(WHITE, SURFACE_HIGH, 0.15) } ?: BrandTokens.Dark.OUTLINE
        val TEXT_HIGH get() = k("tvBackground")?.let { WHITE } ?: BrandTokens.Dark.TEXT_HIGH
        val TEXT_MEDIUM get() = k("tvBackground")?.let { mix(WHITE, it, 0.78) } ?: BrandTokens.Dark.TEXT_MEDIUM
        val TEXT_LOW get() = k("tvBackground")?.let { mix(WHITE, it, 0.58) } ?: BrandTokens.Dark.TEXT_LOW
        /** Accent lisible sur fond sombre (texte, sélection, progression) : `tvAccent`, sinon la couleur de focus. */
        val PRIMARY get() = k("tvAccent") ?: k("focus") ?: BrandTokens.Dark.PRIMARY
        val ON_PRIMARY get() = k("tvBackground") ?: BrandTokens.Dark.ON_PRIMARY
        val SECONDARY get() = k("success") ?: BrandTokens.Dark.SECONDARY
        val ON_SECONDARY get() = k("tvBackground") ?: BrandTokens.Dark.ON_SECONDARY
        val ACCENT get() = k("info") ?: BrandTokens.Dark.ACCENT
        val FOCUS_RING get() = k("focus") ?: BrandTokens.Dark.FOCUS_RING
    }

    object Semantic {
        val SUCCESS_DARK get() = k("success")?.let { readableOnDark(it, Dark.SURFACE_HIGH) } ?: BrandTokens.Semantic.SUCCESS_DARK
        val SUCCESS_LIGHT get() = k("success")?.let { readableOn(it, Light.BACKGROUND) } ?: BrandTokens.Semantic.SUCCESS_LIGHT
        val INFO_DARK get() = k("info")?.let { readableOnDark(it, Dark.SURFACE_HIGH) } ?: BrandTokens.Semantic.INFO_DARK
        val INFO_LIGHT get() = k("info")?.let { readableOn(it, Light.BACKGROUND) } ?: BrandTokens.Semantic.INFO_LIGHT
        // Avertissement, erreur, éteint, inconnu : sémantique universelle, jamais recolorée.
        val WARNING_DARK get() = BrandTokens.Semantic.WARNING_DARK
        val WARNING_LIGHT get() = BrandTokens.Semantic.WARNING_LIGHT
        val ERROR_DARK get() = BrandTokens.Semantic.ERROR_DARK
        val ERROR_LIGHT get() = BrandTokens.Semantic.ERROR_LIGHT
    }
}
