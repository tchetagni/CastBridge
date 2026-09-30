package castbridge.sender.player

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import castbridge.core.phone.MediaKind
import castbridge.sender.Artwork
import castbridge.sender.MiniPlayer
import castbridge.sender.fmtTime
import kotlinx.coroutines.delay

/** Ticks the remote clock so the bar moves smoothly between two polls of the TV. */
@Composable
fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(250); now = System.currentTimeMillis() } }
    return now
}

/** The phone as the TV's remote: play/pause, ±10 s, synchronized bar, TV volume (CastBridge), back to the phone. */
@Composable
fun RemoteControls(r: Remote, modifier: Modifier = Modifier, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val now = rememberNow()
    var seeking by remember { mutableStateOf<Float?>(null) }
    val pos = r.clock.now(now)
    val dur = r.clock.durMs
    Column(modifier.fillMaxSize().background(Color(0xFF0B0B0B)).systemBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Fermer", tint = Color.White) }
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.weight(1f))
        Icon(Icons.Filled.CastConnected, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Text("Sur ${r.target.name}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(r.item.name, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        r.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFBBBBBB), textAlign = TextAlign.Center) }
        if (r.item.kind != MediaKind.IMAGE && r.phase == Remote.Phase.PLAYING) {
            if (dur > 0) {
                Slider(seeking ?: pos.toFloat(), { seeking = it }, valueRange = 0f..dur.toFloat(),
                    onValueChangeFinished = { seeking?.let { CastSession.seekTo(it.toLong()) }; seeking = null })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(fmtTime((seeking ?: pos.toFloat()).toLong()), color = Color.White, style = MaterialTheme.typography.labelMedium)
                    Text(fmtTime(dur), color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                IconButton({ CastSession.skip(-10_000) }) { Icon(Icons.Filled.Replay10, "-10 s", Modifier.size(36.dp), tint = Color.White) }
                FilledIconButton(CastSession::toggle, Modifier.size(76.dp)) {
                    Icon(if (r.clock.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Lecture / pause", Modifier.size(44.dp))
                }
                IconButton({ CastSession.skip(10_000) }) { Icon(Icons.Filled.Forward10, "+10 s", Modifier.size(36.dp), tint = Color.White) }
            }
            r.volume?.let { v ->
                var vol by remember(r.target) { mutableStateOf<Float?>(null) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, "Volume de la TV", tint = Color.White)
                    Slider(vol ?: v.toFloat(), { vol = it }, Modifier.weight(1f).padding(horizontal = 8.dp), valueRange = 0f..100f,
                        onValueChangeFinished = { vol?.let { CastSession.setVolume(it.toInt()) }; vol = null })
                    Text("${(vol ?: v.toFloat()).toInt()} %", color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        if (r.phase == Remote.Phase.STARTING) CircularProgressIndicator()
        Spacer(Modifier.weight(1f))
        if (r.item.kind != MediaKind.IMAGE && r.phase != Remote.Phase.FAILED && r.phase != Remote.Phase.STARTING)
            Button({ CastSession.backToPhone(ctx) }, Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.PhoneAndroid, null); Spacer(Modifier.width(8.dp)); Text("Revenir sur le téléphone")
            }
        OutlinedButton({ if (r.tvHasIt || r.phase == Remote.Phase.STARTING) CastSession.stop(ctx) else CastSession.dismiss(ctx) }, Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.StopCircle, null); Spacer(Modifier.width(8.dp))
            Text(if (r.phase == Remote.Phase.PLAYING || r.phase == Remote.Phase.STARTING) "Arrêter la diffusion" else "Fermer")
        }
    }
}

/** Persistent mini controller while a TV plays something sent from the phone player (app root and player). */
@Composable
fun CastMiniBar(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val r by CastSession.state.collectAsState()
    val now = rememberNow()
    LaunchedEffect(Unit) { CastSession.notices.collect { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show() } }
    val s = r ?: return
    val sub = when (s.phase) {
        Remote.Phase.STARTING -> "Connexion à ${s.target.name}…"
        Remote.Phase.COPYING -> s.message ?: "Copie vers ${s.target.name}…"
        Remote.Phase.PLAYING -> "Sur ${s.target.name}"
        Remote.Phase.ENDED, Remote.Phase.FAILED -> s.message ?: "Terminé"
    }
    val dur = s.clock.durMs
    Box(modifier) { MiniPlayer(s.item.name, sub, s.clock.playing, if (dur > 0) s.clock.now(now).toFloat() / dur else 0f,
        onToggle = { if (s.phase == Remote.Phase.PLAYING) CastSession.toggle() },
        onStop = { if (s.tvHasIt || s.phase == Remote.Phase.STARTING) CastSession.stop(ctx) else CastSession.dismiss(ctx) }) {
        ctx.startActivity(Intent(ctx, PlayerActivity::class.java).setAction(PlayerActivity.ACTION_REMOTE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    } }
}

/** Big artwork + titles shown for music in the phone player. */
@Composable
fun AudioArt(title: String, subtitle: String?, art: androidx.compose.ui.graphics.ImageBitmap?) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (art != null) androidx.compose.foundation.Image(art, null, Modifier.size(260.dp))
        else Artwork(220.dp)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFBBBBBB)) }
    }
}
