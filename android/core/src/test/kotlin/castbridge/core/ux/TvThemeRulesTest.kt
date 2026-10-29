package castbridge.core.ux

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TvThemeRulesTest {
    @Test fun lightIsTheDefaultAndParseIsLenient() {
        assertEquals(TvTheme.LIGHT, TvTheme.DEFAULT)
        assertEquals(TvTheme.LIGHT, TvTheme.parse(null))
        assertEquals(TvTheme.LIGHT, TvTheme.parse("n'importe quoi"))
        assertEquals(TvTheme.DARK, TvTheme.parse("dark"))
        assertEquals(TvTheme.LIGHT, TvTheme.parse(TvTheme.LIGHT.key))
        assertEquals(TvTheme.DARK, TvTheme.LIGHT.other)
    }

    @Test fun menusFollowTheThemeButThePlayerAndMediaOverlaysStayDark() {
        val light = TvThemeRules.palette(TvTheme.LIGHT, TvPlace.MENU)
        val dark = TvThemeRules.palette(TvTheme.DARK, TvPlace.MENU)
        assertNotEquals(light.bg, dark.bg)
        for (t in TvTheme.entries) for (place in listOf(TvPlace.PLAYER, TvPlace.MEDIA_OVERLAY))
            assertEquals(dark.bg, TvThemeRules.palette(t, place).bg, "$t $place doit rester sombre")
        assertTrue(TvThemeRules.luminance(light.bg) > 0.8 && TvThemeRules.luminance(dark.bg) < 0.05)
    }

    @Test fun lightUsesThePhoneTokens() {
        val p = TvThemeRules.palette(TvTheme.LIGHT)
        assertEquals(castbridge.core.brand.BrandTokens.Light.BACKGROUND, p.bg)
        assertEquals(castbridge.core.brand.BrandTokens.Light.PRIMARY, p.accent)
        assertTrue(p.ringDp > TvThemeRules.palette(TvTheme.DARK).ringDp, "anneau plus épais sur fond clair")
    }

    @Test fun contrastMath() {
        assertEquals(21.0, TvThemeRules.contrast(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.01)
        assertEquals(1.0, TvThemeRules.contrast(0xFF123456.toInt(), 0xFF123456.toInt()), 0.001)
    }

    @Test fun everyPaletteMeetsWcagAA() {
        val fails = mutableListOf<String>()
        for (t in TvTheme.entries) for (pair in TvThemeRules.pairs(TvThemeRules.palette(t))) {
            val c = TvThemeRules.contrast(pair.fg, pair.bg)
            println("TV-CONTRASTE $t ${pair.name} = ${"%.2f".format(c)} (min ${pair.min})")
            if (c < pair.min) fails += "$t ${pair.name} ${"%.2f".format(c)} < ${pair.min}"
        }
        assertTrue(fails.isEmpty(), fails.joinToString("\n"))
    }

    @Test fun statusColoursStayDistinguishableOnLight() {
        val p = TvThemeRules.palette(TvTheme.LIGHT)
        val hues = listOf(p.sigGreen, p.sigOrange, p.sigRed)
        for (i in hues.indices) for (j in i + 1 until hues.size)
            assertTrue(TvThemeRules.contrast(hues[i], hues[j]) >= 1.0 && hues[i] != hues[j])
    }
}
