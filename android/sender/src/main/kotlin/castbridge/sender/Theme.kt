package castbridge.sender

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import castbridge.core.brand.Brand
import castbridge.core.brand.BrandPalette as P
import castbridge.core.brand.BrandTokens as T

/** Appearance chosen in Réglages: the dark theme is the default of the app, the light one is available, or follow the system. */
enum class ThemeMode { DARK, LIGHT, SYSTEM }

object ThemePrefs {
    private const val FILE = "castbridge_ui"
    private const val KEY = "theme"
    var mode by mutableStateOf(ThemeMode.DARK); private set

    fun load(ctx: Context) {
        // Sans choix de l'utilisateur : le thème par défaut de la marque (CastBridge = sombre).
        val brandDefault = runCatching { ThemeMode.valueOf(Brand.phoneTheme) }.getOrDefault(ThemeMode.DARK)
        mode = runCatching { ThemeMode.valueOf(ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null) ?: brandDefault.name) }.getOrDefault(brandDefault)
    }

    fun set(ctx: Context, m: ThemeMode) {
        mode = m
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, m.name).apply()
    }
}

/** Dark theme of the charte (branding/design-tokens.json): deep indigo background, gold primary. */
private fun darkScheme() = darkColorScheme(
    primary = Color(P.Dark.PRIMARY), onPrimary = Color(P.Dark.ON_PRIMARY),
    primaryContainer = Color(P.Dark.SURFACE_HIGH), onPrimaryContainer = Color(P.Dark.TEXT_HIGH),
    secondary = Color(P.Dark.SECONDARY), onSecondary = Color(P.Dark.ON_SECONDARY),
    secondaryContainer = Color(P.Dark.SURFACE_HIGH), onSecondaryContainer = Color(P.Dark.TEXT_HIGH),
    tertiary = Color(P.Dark.ACCENT), onTertiary = Color(P.Dark.ON_PRIMARY),
    tertiaryContainer = Color(P.Dark.SURFACE_HIGH), onTertiaryContainer = Color(P.Dark.TEXT_HIGH),
    background = Color(P.Dark.BACKGROUND), onBackground = Color(P.Dark.TEXT_HIGH),
    surface = Color(P.Dark.SURFACE), onSurface = Color(P.Dark.TEXT_HIGH),
    surfaceVariant = Color(P.Dark.SURFACE_HIGH), onSurfaceVariant = Color(P.Dark.TEXT_MEDIUM),
    surfaceTint = Color(P.Dark.PRIMARY),
    surfaceContainerLowest = Color(P.Dark.BACKGROUND), surfaceContainerLow = Color(P.Dark.BACKGROUND_ELEVATED),
    surfaceContainer = Color(P.Dark.SURFACE), surfaceContainerHigh = Color(P.Dark.SURFACE_HIGH), surfaceContainerHighest = Color(P.Dark.SURFACE_HIGH),
    outline = Color(P.Dark.TEXT_LOW), outlineVariant = Color(P.Dark.OUTLINE),
    error = Color(P.Semantic.ERROR_DARK), onError = Color(P.Dark.ON_SECONDARY),
    errorContainer = Color(P.Dark.SURFACE_HIGH), onErrorContainer = Color(P.Semantic.ERROR_DARK),
    scrim = Color.Black,
)

/** Light theme of the charte (contrast-corrected values, v1.1). */
private fun lightScheme() = lightColorScheme(
    primary = Color(P.Light.PRIMARY), onPrimary = Color(P.Light.ON_PRIMARY),
    primaryContainer = Color(P.Light.SURFACE_VARIANT), onPrimaryContainer = Color(P.Light.TEXT_HIGH),
    secondary = Color(P.Light.SECONDARY), onSecondary = Color(P.Light.ON_SECONDARY),
    secondaryContainer = Color(P.Light.SURFACE_VARIANT), onSecondaryContainer = Color(P.Light.TEXT_HIGH),
    tertiary = Color(P.Light.ACCENT), onTertiary = Color(P.Light.ON_ACCENT),
    tertiaryContainer = Color(P.Light.SURFACE_VARIANT), onTertiaryContainer = Color(P.Light.TEXT_HIGH),
    background = Color(P.Light.BACKGROUND), onBackground = Color(P.Light.TEXT_HIGH),
    surface = Color(P.Light.SURFACE), onSurface = Color(P.Light.TEXT_HIGH),
    surfaceVariant = Color(P.Light.SURFACE_VARIANT), onSurfaceVariant = Color(P.Light.TEXT_MEDIUM),
    surfaceTint = Color(P.Light.PRIMARY),
    surfaceContainerLowest = Color(P.Light.SURFACE), surfaceContainerLow = Color(P.Light.BACKGROUND),
    surfaceContainer = Color(P.Light.SURFACE_VARIANT), surfaceContainerHigh = Color(P.Light.SURFACE_VARIANT), surfaceContainerHighest = Color(P.Light.CARD),
    outline = Color(P.Light.TEXT_LOW), outlineVariant = Color(P.Light.OUTLINE),
    error = Color(P.Semantic.ERROR_LIGHT), onError = Color.White,
    errorContainer = Color(P.Light.SURFACE_VARIANT), onErrorContainer = Color(P.Semantic.ERROR_LIGHT),
)

/** Status colours of the charte (success / warning / error / info) in the variant of the current theme. */
object Cb {
    private val dark @Composable get() = MaterialTheme.colorScheme.background.let { it.red + it.green + it.blue < 1.5f }
    val success @Composable get() = Color(if (dark) P.Semantic.SUCCESS_DARK else P.Semantic.SUCCESS_LIGHT)
    val warning @Composable get() = Color(if (dark) P.Semantic.WARNING_DARK else P.Semantic.WARNING_LIGHT)
    val error @Composable get() = Color(if (dark) P.Semantic.ERROR_DARK else P.Semantic.ERROR_LIGHT)
    val info @Composable get() = Color(if (dark) P.Semantic.INFO_DARK else P.Semantic.INFO_LIGHT)
    /** Brand hue for big elements and icons only (light theme: primaryBrand, too pale for small text). */
    val brand get() = Color(P.Dark.PRIMARY)
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun family(res: Int, vararg weights: Int) = FontFamily(weights.map { w ->
    Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
})

private val Inter = family(R.font.inter, 400, 500, 600, 700)
private val Bricolage = family(R.font.bricolage_grotesque, 500, 700, 800)

/** Mobile type scale of the charte: display 32/800, headline 28/700, title 22/700, body 16/400, caption 12/500 (sp). Titles in Bricolage Grotesque, text in Inter. */
private val CbTypography = Typography(
    displayLarge = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, lineHeight = 48.sp),
    displayMedium = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 36.sp, lineHeight = 44.sp),
    displaySmall = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, lineHeight = 38.sp),
    headlineLarge = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 35.sp),
    headlineSmall = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 31.sp),
    titleLarge = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 29.sp),
    titleMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)

/** Radii of the charte: sm 8, md 12, lg 16, xl 24. */
private val CbShapes = Shapes(
    extraSmall = RoundedCornerShape(T.RADIUS_SM_DP.dp), small = RoundedCornerShape(T.RADIUS_SM_DP.dp),
    medium = RoundedCornerShape(T.RADIUS_MD_DP.dp), large = RoundedCornerShape(T.RADIUS_LG_DP.dp), extraLarge = RoundedCornerShape(T.RADIUS_XL_DP.dp),
)

@Composable
fun CastTheme(content: @Composable () -> Unit) {
    val dark = when (ThemePrefs.mode) { ThemeMode.DARK -> true; ThemeMode.LIGHT -> false; ThemeMode.SYSTEM -> isSystemInDarkTheme() }
    MaterialTheme(colorScheme = if (dark) remember { darkScheme() } else remember { lightScheme() }, typography = CbTypography, shapes = CbShapes, content = content)
}

/** Status and navigation bars in the colour of the current theme (dark or light), icons readable on them. */
@Composable
fun SyncSystemBars() {
    val view = androidx.compose.ui.platform.LocalView.current
    val bg = MaterialTheme.colorScheme.background
    val dark = bg.red + bg.green + bg.blue < 1.5f
    if (!view.isInEditMode) androidx.compose.runtime.SideEffect {
        val w = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        w.statusBarColor = bg.toArgb(); w.navigationBarColor = bg.toArgb()
        androidx.core.view.WindowCompat.getInsetsController(w, view).apply { isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark }
    }
}
