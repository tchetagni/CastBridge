package castbridge.sender

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun fmtTime(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun SectionHeader(text: String, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary)
        action?.invoke()
    }
}

/** Rounded gradient tile standing in for album art. */
@Composable
fun Artwork(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size / 8))
            .background(Brush.linearGradient(listOf(Color(0xFF1F6F8B), Color(0xFF12384A)))),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Filled.Movie, null, Modifier.size(size / 2), tint = Color.White.copy(alpha = 0.85f)) }
}

@Composable
fun DeviceRow(name: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = if (selected) cs.primaryContainer else Color.Transparent),
        leadingContent = { Icon(Icons.Filled.Tv, null, tint = if (selected) cs.primary else cs.onSurfaceVariant) },
        headlineContent = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(subtitle) },
        trailingContent = { if (selected) Icon(Icons.Filled.CheckCircle, null, tint = cs.primary) },
    )
}

/** Persistent bar at the bottom of the screen showing what the renderer is playing. */
@Composable
fun MiniPlayer(title: String, subtitle: String, playing: Boolean, progress: Float,
               onToggle: () -> Unit, onStop: () -> Unit, onOpen: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 3.dp) {
        Column {
            LinearProgressIndicator({ progress.coerceIn(0f, 1f) }, Modifier.fillMaxWidth().height(2.dp))
            Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Artwork(48.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                    Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onToggle) { Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Lecture / pause") }
                IconButton(onStop) { Icon(Icons.Filled.Stop, "Stop") }
            }
        }
    }
}

/** Full-screen "now playing" panel: big artwork, seek bar, transport controls. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingSheet(title: String, subtitle: String, playing: Boolean, posMs: Long, durMs: Long,
                    onSeek: (Long) -> Unit, onToggle: () -> Unit, onSkip: (Int) -> Unit,
                    onStop: () -> Unit, onDismiss: () -> Unit) {
    var seeking by remember { mutableStateOf<Float?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Artwork(240.dp)
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (durMs > 0) {
                Slider(seeking ?: posMs.toFloat(), { seeking = it }, valueRange = 0f..durMs.toFloat(),
                    onValueChangeFinished = { seeking?.let { onSeek(it.toLong()) }; seeking = null })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(fmtTime((seeking ?: posMs.toFloat()).toLong()), style = MaterialTheme.typography.labelMedium)
                    Text(fmtTime(durMs), style = MaterialTheme.typography.labelMedium)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                IconButton({ onSkip(-10) }) { Icon(Icons.Filled.Replay10, "-10 s", Modifier.size(32.dp)) }
                FilledIconButton(onToggle, Modifier.size(72.dp)) {
                    Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Lecture / pause", Modifier.size(40.dp))
                }
                IconButton({ onSkip(10) }) { Icon(Icons.Filled.Forward10, "+10 s", Modifier.size(32.dp)) }
            }
            TextButton(onStop) { Icon(Icons.Filled.Stop, null); Spacer(Modifier.width(8.dp)); Text("Arrêter") }
        }
    }
}
