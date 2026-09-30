package castbridge.sender

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * CastBridge Design System colors derived from branding/design-tokens.json.
 */
object CastBridgeColors {
    // Brand primary palette
    val Gold = Color(0xFFF5B025)
    val GoldLight = Color(0xFFFFE1A6)
    val Green = Color(0xFF2E9E6B)
    val Orange = Color(0xFFFF8A3D)
    val NavyDark = Color(0xFF0A0F1E)
    val NavySurface = Color(0xFF151D37)
    val NavySurfaceHigh = Color(0xFF1B2542)
    val NavyOutline = Color(0xFF2A3550)

    // Sub-brands
    val QuizCoral = Color(0xFFFF5C39)
    val QuizAmber = Color(0xFFFFB020)
    val QuizTeal = Color(0xFF27C7B0)
    val QuizBackground = Color(0xFF0B1B1E)

    val ChessGreen = Color(0xFF2FA96B)
    val ChessGold = Color(0xFFF2C14E)
    val ChessCopper = Color(0xFFE07B39)
    val ChessBackground = Color(0xFF0C1B14)

    val LearnBlue = Color(0xFF3B82F6)
    val LearnGold = Color(0xFFF59E0B)
    val LearnCyan = Color(0xFF06B6D4)
    val LearnBackground = Color(0xFF070D1E)
}

/** Dark palette - primary for TV, ambient for mobile. */
private val DarkScheme = darkColorScheme(
    primary = Color(0xFFF5B025),
    onPrimary = Color(0xFF171204),
    primaryContainer = Color(0xFF2E2207),
    onPrimaryContainer = Color(0xFFFFE1A6),
    secondary = Color(0xFF2E9E6B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF133E2B),
    onSecondaryContainer = Color(0xFF8EE4B9),
    tertiary = Color(0xFFFF8A3D),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF4A2207),
    onTertiaryContainer = Color(0xFFFFD1B8),
    background = Color(0xFF0A0F1E),
    onBackground = Color(0xFFF4F6FB),
    surface = Color(0xFF151D37),
    onSurface = Color(0xFFF4F6FB),
    surfaceVariant = Color(0xFF1B2542),
    onSurfaceVariant = Color(0xFFB7C0D4),
    outline = Color(0xFF2A3550),
    outlineVariant = Color(0xFF1B2542),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF370B0B),
)

/** Light palette for phone daytime usage. */
private val LightScheme = lightColorScheme(
    primary = Color(0xFFB7791F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFDE8C4),
    onPrimaryContainer = Color(0xFF3F2700),
    secondary = Color(0xFF1F8A5C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD0F3E2),
    onSecondaryContainer = Color(0xFF002D1A),
    tertiary = Color(0xFFE4692E),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF7F8FC),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFEDF0F7),
    onSurfaceVariant = Color(0xFF3E4A61),
    outline = Color(0xFFD7DCE7),
    error = Color(0xFFC5343A),
    onError = Color(0xFFFFFFFF),
)

@Composable
fun CastTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) = MaterialTheme(
    colorScheme = if (darkTheme) DarkScheme else LightScheme,
    content = content
)
