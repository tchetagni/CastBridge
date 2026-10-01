package castbridge.sender

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import castbridge.core.tv.Mp4Atoms
import castbridge.core.tv.Pin
import castbridge.core.tv.Progressive
import castbridge.core.tv.TvClient
import castbridge.core.tv.TvInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.FileInputStream

/** Which layout does this MP4/MOV have (index at the start = can be played while it uploads)? */
fun mp4LayoutOf(ctx: Context, uri: Uri, total: Long): Mp4Atoms.Layout = runCatching {
    ctx.contentResolver.openFileDescriptor(uri, "r")!!.use { pfd ->
        FileInputStream(pfd.fileDescriptor).use { fis ->
            Mp4Atoms.layout(total) { off, len ->
                val bb = java.nio.ByteBuffer.allocate(len)
                val n = fis.channel.read(bb, off)
                if (n <= 0) ByteArray(0) else bb.array().copyOf(n)
            }
        }
    }
}.getOrDefault(Mp4Atoms.Layout.UNKNOWN)

/**
 * DLNA plays straight from this phone, so it stops when the phone leaves the network. This action copies the
 * same file to a CastBridge TV, starts playing it there at the current DLNA position as soon as the TV holds
 * enough data beyond it, then stops the DLNA stream: from then on the TV plays from its own storage.
 *
 * A pure DLNA renderer (no CastBridge TV app) cannot survive the phone disconnecting: only its internal
 * buffer keeps playing.
 */
@Composable
fun HandoffButton(fileUri: Uri?, fileName: String?, posSec: Long, durSec: Long, onStopDlna: () -> Unit) {
    val ctx = LocalContext.current
    val discovery = remember { TvDiscovery(ctx) }
    DisposableEffect(Unit) { discovery.start(); onDispose { discovery.stop() } }
    val tvs by discovery.tvs.collectAsState()
    val pins = remember { PinStore(ctx) }
    val tv = tvs.firstOrNull()
    var pin by remember(tv?.name) { mutableStateOf(pins.get(tv?.name)) }
    var running by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    val upload by UploadService.state.collectAsState()
    val pos by rememberUpdatedState(posSec)
    val dur by rememberUpdatedState(durSec)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (tv == null) Text("Continuer sans réseau : aucune TV CastBridge trouvée.", style = MaterialTheme.typography.bodySmall)
        else {
            PinField(pins, tv.name, pin, { pin = it }, Modifier.fillMaxWidth())
            OutlinedButton(enabled = !running && fileUri != null && fileName != null && castbridge.core.trust.TvCredential.isUsable(pin), onClick = {
                running = true; status = "Envoi vers ${tv.name}…"
                UploadService.start(ctx, fileUri!!, fileName!!, tv.name, null, pin, progressive = false, autoPlay = false)
            }) { Text("Continuer sur la TV sans réseau") }
        }
        if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodySmall)
    }

    LaunchedEffect(running) {
        if (!running || tv == null || fileUri == null || fileName == null) return@LaunchedEffect
        val client = TvClient(tv.base, pin)
        var total = 0L; var layout: Mp4Atoms.Layout? = null
        var started = false
        while (running) {
            val u = upload
            if (u is UploadService.State.Failed) { status = "Échec de l'envoi : ${u.reason}"; running = false; break }
            if (u is UploadService.State.Uploading) total = u.total
            if (total > 0 && layout == null) layout = if (Mp4Atoms.isIsoName(fileName)) mp4LayoutOf(ctx, fileUri, total) else Mp4Atoms.Layout.NOT_ISO
            val info = withContext(Dispatchers.IO) { runCatching { TvInfo.parse(client.info()) } }.getOrNull()
            val f = info?.file(fileName)
            if (f != null && total > 0) {
                val moovAtEnd = layout == Mp4Atoms.Layout.MOOV_AT_END
                if (!started) {
                    val want = Progressive.handoffBytes(total, dur * 1000, pos * 1000, 30_000, moovAtEnd, UploadService.speed.value)
                    status = if (moovAtEnd) "Ce fichier MP4 doit être copié en entier avant la reprise sur la TV (${f.received * 100 / total} %). La diffusion continue."
                    else "Copie vers la TV : ${f.received * 100 / total} %, reprise dès ${(want * 100 / total).coerceAtMost(100)} %. La diffusion continue."
                    if (f.received >= want) {
                        val ok = withContext(Dispatchers.IO) { runCatching { client.play(fileName, pos * 1000) }.isSuccess }
                        if (ok) started = true
                    }
                } else {
                    val playing = info.state == "playing" || info.state == "buffering"
                    val ahead = Progressive.reachableMs(info.dur, f.received, total, 0) - info.pos
                    if (playing && (f.complete || ahead >= 20_000)) {
                        onStopDlna()
                        status = "La TV lit maintenant depuis son stockage : le téléphone peut quitter le réseau" +
                            if (!f.complete) " (encore ${ahead / 1000} s d'avance, l'envoi continue tant que le réseau est là)." else "."
                        running = false; break
                    }
                }
            }
            delay(1000)
        }
    }
}
