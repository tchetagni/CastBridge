package castbridge.core.brand

import castbridge.core.brand.BrandTokens as T
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BrandPaletteTest {
    private val eduplay = "background=#FFFFFF;focus=#00B8F0;info=#00B8F0;onPrimary=#FFFFFF;primary=#0750B8;success=#48B800;text=#082A59;tvAccent=#00B8F0;tvBackground=#082A59;tvSurface=#0E3A75;tvSurface2=#12457F"

    @Before @After fun reset() { BrandPalette.init("") }

    /** Garantie « CastBridge identique » : sans surcharge, chaque rôle vaut exactement l'ancienne constante. */
    @Test fun defaultPaletteEqualsOldConstants() {
        val d = BrandPalette.Dark; val l = BrandPalette.Light; val s = BrandPalette.Semantic
        assertEquals(T.Dark.BACKGROUND, d.BACKGROUND); assertEquals(T.Dark.BACKGROUND_ELEVATED, d.BACKGROUND_ELEVATED)
        assertEquals(T.Dark.SURFACE, d.SURFACE); assertEquals(T.Dark.SURFACE_HIGH, d.SURFACE_HIGH); assertEquals(T.Dark.OUTLINE, d.OUTLINE)
        assertEquals(T.Dark.TEXT_HIGH, d.TEXT_HIGH); assertEquals(T.Dark.TEXT_MEDIUM, d.TEXT_MEDIUM); assertEquals(T.Dark.TEXT_LOW, d.TEXT_LOW)
        assertEquals(T.Dark.PRIMARY, d.PRIMARY); assertEquals(T.Dark.ON_PRIMARY, d.ON_PRIMARY)
        assertEquals(T.Dark.SECONDARY, d.SECONDARY); assertEquals(T.Dark.ON_SECONDARY, d.ON_SECONDARY)
        assertEquals(T.Dark.ACCENT, d.ACCENT); assertEquals(T.Dark.FOCUS_RING, d.FOCUS_RING)
        assertEquals(T.Light.BACKGROUND, l.BACKGROUND); assertEquals(T.Light.SURFACE, l.SURFACE); assertEquals(T.Light.SURFACE_VARIANT, l.SURFACE_VARIANT)
        assertEquals(T.Light.OUTLINE, l.OUTLINE); assertEquals(T.Light.TEXT_HIGH, l.TEXT_HIGH); assertEquals(T.Light.TEXT_MEDIUM, l.TEXT_MEDIUM)
        assertEquals(T.Light.TEXT_LOW, l.TEXT_LOW); assertEquals(T.Light.PRIMARY, l.PRIMARY); assertEquals(T.Light.ON_PRIMARY, l.ON_PRIMARY)
        assertEquals(T.Light.SECONDARY, l.SECONDARY); assertEquals(T.Light.ON_SECONDARY, l.ON_SECONDARY)
        assertEquals(T.Light.ACCENT, l.ACCENT); assertEquals(T.Light.ON_PRIMARY, l.ON_ACCENT); assertEquals(T.Light.FOCUS_RING, l.FOCUS_RING)
        assertEquals(T.Semantic.SUCCESS_DARK, s.SUCCESS_DARK); assertEquals(T.Semantic.SUCCESS_LIGHT, s.SUCCESS_LIGHT)
        assertEquals(T.Semantic.INFO_DARK, s.INFO_DARK); assertEquals(T.Semantic.INFO_LIGHT, s.INFO_LIGHT)
        assertEquals(T.Semantic.WARNING_DARK, s.WARNING_DARK); assertEquals(T.Semantic.ERROR_LIGHT, s.ERROR_LIGHT)
        assertTrue(!BrandPalette.isCustom)
    }

    @Test fun defaultBrandPhoneThemeIsDark() { Brand.init("castbridge", "CastBridge", "CastBridge-TV", "CastBridge", "", ""); assertEquals("DARK", Brand.phoneTheme) }

    @Test fun eduplayRolesApplied() {
        BrandPalette.init(eduplay)
        assertTrue(BrandPalette.isCustom)
        assertEquals(0xFF0750B8.toInt(), BrandPalette.Light.PRIMARY)
        assertEquals(0xFF082A59.toInt(), BrandPalette.Dark.BACKGROUND)
        assertEquals(0xFF00B8F0.toInt(), BrandPalette.Dark.FOCUS_RING)
        assertEquals(0xFF48B800.toInt(), BrandPalette.Dark.SECONDARY)
    }

    private fun ok(fg: Int, bg: Int, min: Double, what: String) = assertTrue("$what : ${"%.2f".format(BrandPalette.contrast(fg, bg))} < $min", BrandPalette.contrast(fg, bg) >= min)

    @Test fun eduplayContrast() {
        BrandPalette.init(eduplay)
        val l = BrandPalette.Light; val d = BrandPalette.Dark; val s = BrandPalette.Semantic
        for ((n, fg) in listOf("text" to l.TEXT_HIGH, "medium" to l.TEXT_MEDIUM, "low" to l.TEXT_LOW, "primary" to l.PRIMARY, "successText" to s.SUCCESS_LIGHT, "infoText" to s.INFO_LIGHT))
            ok(fg, l.BACKGROUND, 4.5, "clair $n sur fond"); 
        ok(l.TEXT_HIGH, l.SURFACE_VARIANT, 4.5, "clair texte sur surface variante")
        ok(l.ON_PRIMARY, l.PRIMARY, 4.5, "clair onPrimary/primary")
        ok(l.ON_SECONDARY, l.SECONDARY, 4.5, "clair texte sur vert"); ok(l.ON_ACCENT, l.ACCENT, 4.5, "clair texte sur cyan")
        for ((n, fg) in listOf("texte" to d.TEXT_HIGH, "medium" to d.TEXT_MEDIUM, "low" to d.TEXT_LOW, "accent" to d.PRIMARY, "vert" to s.SUCCESS_DARK, "cyan" to s.INFO_DARK)) {
            ok(fg, d.BACKGROUND, 4.5, "sombre $n sur fond"); ok(fg, d.SURFACE, 4.5, "sombre $n sur carte")
        }
        ok(d.TEXT_HIGH, d.SURFACE_HIGH, 4.5, "sombre texte sur carte focus"); ok(d.TEXT_MEDIUM, d.SURFACE_HIGH, 4.5, "sombre medium sur carte focus")
        ok(d.ON_PRIMARY, d.PRIMARY, 4.5, "sombre onPrimary/accent"); ok(d.ON_SECONDARY, d.SECONDARY, 4.5, "sombre texte sur vert")
        ok(d.FOCUS_RING, d.BACKGROUND, 3.0, "anneau de focus"); ok(d.FOCUS_RING, d.SURFACE_HIGH, 3.0, "anneau sur carte")
        // Blanc sur le bleu de marque (boutons TV)
        ok(0xFFFFFFFF.toInt(), 0xFF0750B8.toInt(), 4.5, "blanc sur bleu")
    }
}
