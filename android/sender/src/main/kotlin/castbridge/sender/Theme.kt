package castbridge.sender

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Dark, blue-accented palette in the spirit of BubbleUPnP. */
private val Scheme = darkColorScheme(
    primary = Color(0xFF33B5E5),
    onPrimary = Color(0xFF00212D),
    primaryContainer = Color(0xFF12384A),
    onPrimaryContainer = Color(0xFFCDEFFC),
    secondary = Color(0xFFFFA726),
    background = Color(0xFF121212),
    onBackground = Color(0xFFEDEDED),
    surface = Color(0xFF1C1C1C),
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF262626),
    onSurfaceVariant = Color(0xFFA8A8A8),
    outline = Color(0xFF3A3A3A),
    error = Color(0xFFFF6B6B),
)

@Composable
fun CastTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = Scheme, content = content)
