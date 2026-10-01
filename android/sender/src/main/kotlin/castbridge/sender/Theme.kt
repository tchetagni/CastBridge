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
import castbridge.core.brand.BrandTokens as T

/** Appearance chosen in Réglages: the dark theme is the default of the app, the light one is available, or follow the system. */
enum class ThemeMode { DARK, LIGHT, SYSTEM }

object ThemePrefs {
    private const val FILE = "castbridge_ui"
    private const val KEY = "theme"
    var mode by mutableStateOf(ThemeMode.DARK); private set

    fun load(ctx: Context) {
        mode = runCatching { ThemeMode.valueOf(ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null) ?: "DARK") }.getOrDefault(ThemeMode.DARK)
    }

    fun set(ctx: Context, m: ThemeMode) {
        mode = m
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, m.name).apply()
    }
}

/** Dark theme of the charte (branding/design-tokens.json): deep indigo background, gold primary. */
private val DarkScheme = darkColorScheme(
    primary = Color(T.Dark.PRIMARY), onPrimary = Color(T.Dark.ON_PRIMARY),
    primaryContainer = Color(T.Dark.SURFACE_HIGH), onPrimaryContainer = Color(T.Dark.TEXT_HIGH),
    secondary = Color(T.Dark.SECONDARY), onSecondary = Color(T.Dark.ON_SECONDARY),
    secondaryContainer = Color(T.Dark.SURFACE_HIGH), onSecondaryContainer = Color(T.Dark.TEXT_HIGH),
    tertiary = Color(T.Dark.ACCENT), onTertiary = Color(T.Dark.ON_PRIMARY),
    tertiaryContainer = Color(T.Dark.SURFACE_HIGH), onTertiaryContainer = Color(T.Dark.TEXT_HIGH),
    background = Color(T.Dark.BACKGROUND), onBackground = Color(T.Dark.TEXT_HIGH),
    surface = Color(T.Dark.SURFACE), onSurface = Color(T.Dark.TEXT_HIGH),
    surfaceVariant = Color(T.Dark.SURFACE_HIGH), onSurfaceVariant = Color(T.Dark.TEXT_MEDIUM),
    surfaceTint = Color(T.Dark.PRIMARY),
    surfaceContainerLowest = Color(T.Dark.BACKGROUND), surfaceContainerLow = Color(T.Dark.BACKGROUND_ELEVATED),
    surfaceContainer = Color(T.Dark.SURFACE), surfaceContainerHigh = Color(T.Dark.SURFACE_HIGH), surfaceContainerHighest = Color(T.Dark.SURFACE_HIGH),
    outline = Color(T.Dark.TEXT_LOW), outlineVariant = Color(T.Dark.OUTLINE),
    error = Color(T.Semantic.ERROR_DARK), onError = Color(T.Dark.ON_SECONDARY),
    errorContainer = Color(T.Dark.SURFACE_HIGH), onErrorContainer = Color(T.Semantic.ERROR_DARK),
    scrim = Color.Black,
)

/** Light theme of the charte (contrast-corrected values, v1.1). */
private val LightScheme = lightColorScheme(
    primary = Color(T.Light.PRIMARY), onPrimary = Color(T.Light.ON_PRIMARY),
    primaryContainer = Color(T.Light.SURFACE_VARIANT), onPrimaryContainer = Color(T.Light.TEXT_HIGH),
    secondary = Color(T.Light.SECONDARY), onSecondary = Color(T.Light.ON_SECONDARY),
    secondaryContainer = Color(T.Light.SURFACE_VARIANT), onSecondaryContainer = Color(T.Light.TEXT_HIGH),
    tertiary = Color(T.Light.ACCENT), onTertiary = Color(T.Light.ON_PRIMARY),
    tertiaryContainer = Color(T.Light.SURFACE_VARIANT), onTertiaryContainer = Color(T.Light.TEXT_HIGH),
    background = Color(T.Light.BACKGROUND), onBackground = Color(T.Light.TEXT_HIGH),
    surface = Color(T.Light.SURFACE), onSurface = Color(T.Light.TEXT_HIGH),
    surfaceVariant = Color(T.Light.SURFACE_VARIANT), onSurfaceVariant = Color(T.Light.TEXT_MEDIUM),
    surfaceTint = Color(T.Light.PRIMARY),
    surfaceContainerLowest = Color(T.Light.SURFACE), surfaceContainerLow = Color(T.Light.BACKGROUND),
    surfaceContainer = Color(T.Light.SURFACE_VARIANT), surfaceContainerHigh = Color(T.Light.SURFACE_VARIANT), surfaceContainerHighest = Color(T.Light.OUTLINE),
    outline = Color(T.Light.TEXT_LOW), outlineVariant = Color(T.Light.OUTLINE),
    error = Color(T.Semantic.ERROR_LIGHT), onError = Color.White,
    errorContainer = Color(T.Light.SURFACE_VARIANT), onErrorContainer = Color(T.Semantic.ERROR_LIGHT),
)

/** Status colours of the charte (success / warning / error / info) in the variant of the current theme. */
object Cb {
    private val dark @Composable get() = MaterialTheme.colorScheme.background.let { it.red + it.green + it.blue < 1.5f }
    val success @Composable get() = Color(if (dark) T.Semantic.SUCCESS_DARK else T.Semantic.SUCCESS_LIGHT)
    val warning @Composable get() = Color(if (dark) T.Semantic.WARNING_DARK else T.Semantic.WARNING_LIGHT)
    val error @Composable get() = Color(if (dark) T.Semantic.ERROR_DARK else T.Semantic.ERROR_LIGHT)
    val info @Composable get() = Color(if (dark) T.Semantic.INFO_DARK else T.Semantic.INFO_LIGHT)
    /** Brand hue for big elements and icons only (light theme: primaryBrand, too pale for small text). */
    val brand get() = Color(T.Dark.PRIMARY)
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
    MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, typography = CbTypography, shapes = CbShapes, content = content)
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
