package castbridge.sender

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import castbridge.core.tv.TvClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class TvInfo(val files: List<Pair<String, Long>>, val free: Long, val state: String,
                          val name: String?, val pos: Long, val dur: Long, val received: Map<String, Long> = emptyMap())

private fun parseInfo(j: String): TvInfo {
    val i = castbridge.core.tv.TvInfo.parse(j)
    return TvInfo(i.files.map { it.name to it.size }, i.free, i.state, i.playing, i.pos, i.dur,
        i.files.filter { !it.complete }.associate { it.name to it.received })
}

private fun size(b: Long) = when {
    b >= 1L shl 30 -> "%.1f Go".format(b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> "%.0f Mo".format(b / (1L shl 20).toDouble())
    else -> "${b / 1024} ko"
}

/** "CastBridge TV" tab: send the whole video to the TV app, which then plays it from its own storage. */
@Composable
fun TvScreen(fixedBase: String? = null, extra: @Composable (TvClient) -> Unit = {}) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val discovery = remember { TvDiscovery(ctx) }
    DisposableEffect(Unit) { discovery.start(); onDispose { discovery.stop() } }
    val tvs by discovery.tvs.collectAsState()
    val upload by UploadService.state.collectAsState()

    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    var manualIp by rememberSaveable { mutableStateOf("") }
    var useManual by rememberSaveable { mutableStateOf(false) }
    var fileUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by rememberSaveable { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<TvInfo?>(null) }
    var reachable by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf("") }
    var showPlayer by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var badPin by remember { mutableStateOf<String?>(null) }
    var fileSize by remember { mutableStateOf(0L) }
    var progressive by rememberSaveable { mutableStateOf(false) }
    val notice by UploadService.notice.collectAsState()
    val speed by UploadService.speed.collectAsState()

    LaunchedEffect(tvs) { if (selectedName == null && tvs.size == 1) selectedName = tvs[0].name }

    val base: String? = fixedBase ?: if (useManual) manualIp.trim().takeIf { it.isNotEmpty() }
        ?.let { if (':' in it) "http://$it" else "http://$it:8765" }
    else tvs.firstOrNull { it.name == selectedName }?.base
    val pinKey = fixedBase ?: if (useManual) manualIp.trim().takeIf { it.isNotEmpty() } else selectedName
    val pins = remember { PinStore(ctx) }
    var pin by remember(pinKey) { mutableStateOf(pins.get(pinKey)) }
    val client = base?.let { TvClient(it, pin.takeIf { p -> p.isNotEmpty() }) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            fileUri = uri
            fileSize = runCatching {
                ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getLong(c.getColumnIndexOrThrow(OpenableColumns.SIZE)) else 0L
                }
            }.getOrNull() ?: 0L
            // Little room on the TV: default to playing while the file arrives instead of storing it all first.
            progressive = info?.let { fileSize > 0 && it.free < fileSize * 2 } ?: false
            fileName = runCatching {
                ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
                }
            }.getOrNull() ?: uri.lastPathSegment
        }
    }

    // Poll TV state; tolerate outages (the TV keeps playing on its own).
    LaunchedEffect(base, pin) {
        info = null
        badPin = null
        // Never poll with an incomplete PIN or after a refusal: failures count toward the TV's 60 s lockout.
        while (client != null && castbridge.core.tv.Pin.isValidFormat(pin) && badPin == null) {
            val r = withContext(Dispatchers.IO) { runCatching { parseInfo(client.info()) } }
            r.onSuccess { info = it; reachable = true; badPin = null }.onFailure {
                badPin = (it as? TvClient.HttpError)?.takeIf { e -> e.code == 401 }?.let { e -> if ("locked" in e.message.orEmpty()) "Trop d'essais : TV verrouillée 60 s" else "PIN incorrect" }
                reachable = badPin != null
            }
            delay(1000)
        }
    }

    fun cmd(label: String, block: TvClient.() -> Unit) {
        val c = client ?: return
        scope.launch {
            withContext(Dispatchers.IO) { runCatching { c.block() } }
                .onFailure {
                    val e = it as? TvClient.HttpError
                    message = if (e?.code == 409 && "buffering" in e.message.orEmpty())
                        "Pas encore assez de données reçues pour démarrer la lecture : patientez quelques secondes."
                    else "$label : ${it.message}"
                }.onSuccess { message = "" }
        }
    }

    val busy = upload is UploadService.State.Uploading || upload is UploadService.State.Waiting
    val cs = MaterialTheme.colorScheme
    val current = info?.takeIf { it.name != null && it.state != "idle" }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f)) {
            item {
                Text("Lancez « CastBridge TV » sur la TV. La vidéo est copiée entièrement sur la TV : " +
                    "la lecture continue même si le téléphone quitte le Wi-Fi.",
                    Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }

            // --- Target ---
            item {
                SectionHeader("Lecteurs") {
                    IconButton({ discovery.restart() }) { Icon(Icons.Filled.Refresh, "Rechercher") }
                }
            }
            if (!useManual && fixedBase == null) {
                if (tvs.isEmpty()) item {
                    Text("Recherche des TV CastBridge…", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = cs.onSurfaceVariant)
                }
                items(tvs) { tv -> DeviceRow(tv.name, tv.host, selectedName == tv.name) { selectedName = tv.name } }
            }
            if (fixedBase == null) item {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(useManual, onCheckedChange = { useManual = it })
                    OutlinedTextField(manualIp, { manualIp = it }, Modifier.weight(1f), enabled = useManual, singleLine = true,
                        label = { Text("IP de la TV (manuelle)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                }
            }

            item {
                PinField(pins, pinKey, pin, { pin = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
                badPin?.let { Text(it, Modifier.padding(horizontal = 16.dp), color = cs.error) }
            }

            // --- Library / upload ---
            item { SectionHeader("Bibliothèque") }
            item {
                ListItem(
                    modifier = Modifier.clickable { picker.launch(arrayOf("video/*", "audio/*")) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = { Icon(Icons.Filled.VideoLibrary, null, tint = cs.primary) },
                    headlineContent = { Text(fileName ?: "Aucun fichier", maxLines = 1) },
                    supportingContent = { Text("Toucher pour choisir un fichier") },
                )
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(progressive, onCheckedChange = { progressive = it }, enabled = !busy)
                    Text("Lire pendant l'envoi (le fichier n'a pas besoin de tenir en entier sur la TV)",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !busy && base != null && fileUri != null && fileName != null, onClick = {
                        val target = if (useManual) manualIp.trim() else selectedName ?: fixedBase!!
                        runCatching {
                            UploadService.start(ctx, fileUri!!, fileName!!, target,
                                fixedBase?.removePrefix("http://") ?: if (useManual) manualIp.trim() else null, pin, progressive)
                        }.onFailure { message = "Impossible de démarrer l'envoi : ${it.message}" }
                    }) { Icon(Icons.Filled.CloudUpload, null); Spacer(Modifier.width(8.dp)); Text("Envoyer et lire") }
                    if (busy) OutlinedButton(onClick = { UploadService.cancel(ctx) }) { Text("Annuler") }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    when (val u = upload) {
                        is UploadService.State.Uploading -> {
                            LinearProgressIndicator({ u.sent.toFloat() / u.total }, Modifier.fillMaxWidth())
                            Text("Envoi : ${size(u.sent)} / ${size(u.total)}", style = MaterialTheme.typography.bodySmall)
                        }
                        is UploadService.State.Waiting -> {
                            LinearProgressIndicator({ u.sent.toFloat() / u.total }, Modifier.fillMaxWidth())
                            Text("En attente du réseau, reprise automatique à ${size(u.sent)} (${u.reason})",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        is UploadService.State.Done -> Text("« ${u.job.fileName} » est sur la TV, lecture lancée.",
                            style = MaterialTheme.typography.bodySmall)
                        is UploadService.State.Failed -> Text("Échec : ${u.reason}", color = cs.error)
                        UploadService.State.Idle -> {}
                    }
                    notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.tertiary) }
                    if (base != null && !reachable) Text("TV injoignable — si une vidéo est en cours, elle continue sur la TV.",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    if (message.isNotEmpty()) Text(message, color = cs.error)
                }
            }

            // --- Files stored on the TV ---
            info?.let { i ->
                current?.let { c ->
                    val partial = i.received[c.name]
                    val total = i.files.firstOrNull { it.first == c.name }?.second ?: 0L
                    if (partial != null && total > 0) item {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            val ahead = (castbridge.core.tv.Progressive.reachableMs(c.dur, partial, total, 0) - c.pos).coerceAtLeast(0)
                            Text("Envoi ${partial * 100 / total} % : encore ${if (ahead >= 90_000) "${ahead / 60_000} min" else "${ahead / 1000} s"} de lecture sans réseau",
                                style = MaterialTheme.typography.bodySmall)
                            val videoRate = if (c.dur > 0) total * 1000 / c.dur else 0L
                            val stall = castbridge.core.tv.Progressive.willStall(total, c.dur, speed)
                            if (speed > 0) Text("Envoi ${size(speed)}/s, vidéo ${size(videoRate)}/s" +
                                if (stall) " : trop lent, la lecture va s'interrompre" else "", style = MaterialTheme.typography.bodySmall)
                            if (stall) OutlinedButton(onClick = { UploadService.switchToFullPreload(); cmd("Stop") { stop() } }) {
                                Text("Basculer en préchargement complet")
                            }
                        }
                    }
                }
                item { SectionHeader("Sur la TV · ${size(i.free)} libres") }
                items(i.files) { (name, sz) ->
                    ListItem(
                        modifier = Modifier.clickable { cmd("Lire") { play(name) } },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = { Artwork(40.dp) },
                        headlineContent = { Text(name, maxLines = 1) },
                        supportingContent = { Text(i.received[name]?.let { r -> "${size(r)} / ${size(sz)} reçus (${r * 100 / sz.coerceAtLeast(1)} %)" } ?: size(sz)) },
                        trailingContent = {
                            IconButton({ cmd("Supprimer") { delete(name) } }) { Icon(Icons.Filled.Delete, "Supprimer") }
                        })
                }
            }
        }
        client?.let { c -> Column(Modifier.padding(horizontal = 16.dp)) { extra(c) } }
        current?.let { i ->
            MiniPlayer(i.name!!, "CastBridge TV", i.state == "playing" || i.state == "buffering", if (i.dur > 0) i.pos.toFloat() / i.dur else 0f,
                onToggle = { cmd("Pause") { if (i.state == "playing" || i.state == "buffering") pause() else resume() } },
                onStop = { cmd("Stop") { stop() } }, onOpen = { showPlayer = true })
        }
    }
    if (showPlayer && current != null) {
        NowPlayingSheet(current.name!!, "CastBridge TV", current.state == "playing" || current.state == "buffering", current.pos, current.dur,
            onSeek = { ms ->
                // Nothing exists on the TV beyond what was received: clamp (backward seeks stay free).
                val partial = current.received[current.name]
                val total = info?.files?.firstOrNull { it.first == current.name }?.second ?: 0L
                var t = ms
                if (partial != null && total > 0) {
                    val max = castbridge.core.tv.Progressive.reachableMs(current.dur, partial, total)
                    if (t > max) { t = max; message = "En attente de l'envoi : position limitée à ${fmtTime(max)}" }
                }
                cmd("Seek") { seek(t) }
            },
            onToggle = { cmd("Pause") { if (current.state == "playing" || current.state == "buffering") pause() else resume() } },
            onSkip = { d -> cmd("Seek") { seek((current.pos + d * 1000L).coerceAtLeast(0)) } },
            onStop = { cmd("Stop") { stop() }; showPlayer = false }, onDismiss = { showPlayer = false },
            onSettings = { showSettings = true })
    }
    if (showSettings && client != null) TvPlayerSettingsSheet(client) { showSettings = false }
}
