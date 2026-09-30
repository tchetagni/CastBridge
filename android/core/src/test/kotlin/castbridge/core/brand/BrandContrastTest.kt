package castbridge.core.brand

import castbridge.core.quiz.Json
import java.io.File
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Charte graphique: WCAG contrast of every text/background pair declared in branding/design-tokens.json ("contrast.pairs"),
 * and the generated BrandTokens.kt in sync with the JSON (run `python3 branding/tools/gen_tokens.py` after editing the JSON).
 */
class BrandContrastTest {
    private val dir = File(System.getProperty("branding.dir") ?: error("branding.dir not set"))
    @Suppress("UNCHECKED_CAST")
    private val tokens = Json.obj(File(dir, "design-tokens.json").readText())

    @Suppress("UNCHECKED_CAST")
    private fun hex(path: String): String {
        var n: Any? = tokens["color"]
        for (p in path.split('.')) n = (n as Map<String, Any?>)[p] ?: error("unknown token $path")
        return (n as Map<String, Any?>)["value"] as String
    }

    private fun lin(c: Int): Double { val s = c / 255.0; return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4) }
    private fun lum(h: String): Double {
        val v = h.removePrefix("#").toInt(16)
        return 0.2126 * lin(v shr 16 and 255) + 0.7152 * lin(v shr 8 and 255) + 0.0722 * lin(v and 255)
    }
    private fun ratio(a: String, b: String): Double { val x = lum(a); val y = lum(b); return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05) }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun everyDeclaredPairMeetsItsWcagThreshold() {
        val pairs = (tokens["contrast"] as Map<String, Any?>)["pairs"] as List<Map<String, Any?>>
        assertTrue(pairs.size > 40, "the charte declares its pairs")
        val failures = pairs.mapNotNull { p ->
            val fg = hex(p["fg"] as String); val bg = hex(p["bg"] as String)
            val min = if (p["kind"] == "large") 3.0 else 4.5
            val r = ratio(fg, bg)
            if (r + 1e-9 < min) "${p["fg"]} $fg on ${p["bg"]} $bg = ${"%.2f".format(r)} < $min" else null
        }
        assertTrue(failures.isEmpty(), "contrast below WCAG AA:\n" + failures.joinToString("\n"))
    }

    @Test
    fun theFormerFailingPairsAreFixedAndKeepTheirBrandHueForBigElements() {
        assertTrue(ratio(hex("light.primary"), "#FFFFFF") >= 4.5)
        assertTrue(ratio(hex("light.accent"), "#FFFFFF") >= 4.5)
        assertTrue(ratio(hex("light.onSecondary"), hex("light.secondary")) >= 4.5)
        assertTrue(ratio(hex("dark.onSecondary"), hex("dark.secondary")) >= 4.5)
        assertTrue(ratio(hex("dark.textLow"), hex("dark.surface")) >= 4.5)
        assertEquals("#B7791F", hex("light.primaryBrand"))
        assertEquals("#E4692E", hex("light.accentBrand"))
    }

    @Test
    fun generatedKotlinMatchesTheJson() {
        fun argb(h: String) = (0xFF000000L or h.removePrefix("#").toLong(16)).toInt()
        assertEquals(argb(hex("dark.background")), BrandTokens.Dark.BACKGROUND)
        assertEquals(argb(hex("dark.primary")), BrandTokens.Dark.PRIMARY)
        assertEquals(argb(hex("dark.textLow")), BrandTokens.Dark.TEXT_LOW)
        assertEquals(argb(hex("dark.focusRing")), BrandTokens.Dark.FOCUS_RING)
        assertEquals(argb(hex("light.primary")), BrandTokens.Light.PRIMARY)
        assertEquals(argb(hex("brand.quizDesMillions.primary")), BrandTokens.QuizDesMillions.PRIMARY)
        assertEquals(argb(hex("brand.echecs.background")), BrandTokens.Echecs.BACKGROUND)
        assertEquals(argb(hex("brand.apprendre.surface")), BrandTokens.Apprendre.SURFACE)
        assertEquals(argb(hex("semantic.success.light")), BrandTokens.Semantic.SUCCESS_LIGHT)
    }
}
