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
                          val name: String?, val pos: Long, val dur: Long)

private fun parseInfo(j: String): TvInfo {
    val files = Regex("\\{\"name\":\"((?:[^\"\\\\]|\\\\.)*)\",\"size\":(\\d+)\\}").findAll(j)
        .map { it.groupValues[1].replace("\\\"", "\"").replace("\\\\", "\\") to it.groupValues[2].toLong() }.toList()
    val player = j.substringAfter("\"player\":", "{}")
    return TvInfo(files, TvClient.num(j, "free") ?: 0, TvClient.str(player, "state") ?: "idle",
        TvClient.str(player, "name"), TvClient.num(player, "pos") ?: 0, TvClient.num(player, "dur") ?: 0)
}

private fun size(b: Long) = when {
    b >= 1L shl 30 -> "%.1f Go".format(b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> "%.0f Mo".format(b / (1L shl 20).toDouble())
    else -> "${b / 1024} ko"
}

/** "CastBridge TV" tab: send the whole video to the TV app, which then plays it from its own storage. */
@Composable
fun TvScreen() {
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

    LaunchedEffect(tvs) { if (selectedName == null && tvs.size == 1) selectedName = tvs[0].name }

    val base: String? = if (useManual) manualIp.trim().takeIf { it.isNotEmpty() }
        ?.let { if (':' in it) "http://$it" else "http://$it:8765" }
    else tvs.firstOrNull { it.name == selectedName }?.base
    val client = base?.let { TvClient(it) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            fileUri = uri
            fileName = runCatching {
                ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
                }
            }.getOrNull() ?: uri.lastPathSegment
        }
    }

    // Poll TV state; tolerate outages (the TV keeps playing on its own).
    LaunchedEffect(base) {
        info = null
        while (client != null) {
            val r = withContext(Dispatchers.IO) { runCatching { parseInfo(client.info()) } }
            r.onSuccess { info = it; reachable = true }.onFailure { reachable = false }
            delay(1000)
        }
    }

    fun cmd(label: String, block: TvClient.() -> Unit) {
        val c = client ?: return
        scope.launch {
            withContext(Dispatchers.IO) { runCatching { c.block() } }
                .onFailure { message = "$label : ${it.message}" }.onSuccess { message = "" }
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
            if (!useManual) {
                if (tvs.isEmpty()) item {
                    Text("Recherche des TV CastBridge…", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = cs.onSurfaceVariant)
                }
                items(tvs) { tv -> DeviceRow(tv.name, tv.host, selectedName == tv.name) { selectedName = tv.name } }
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(useManual, onCheckedChange = { useManual = it })
                    OutlinedTextField(manualIp, { manualIp = it }, Modifier.weight(1f), enabled = useManual, singleLine = true,
                        label = { Text("IP de la TV (manuelle)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                }
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
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !busy && base != null && fileUri != null && fileName != null, onClick = {
                        val target = if (useManual) manualIp.trim() else selectedName!!
                        runCatching {
                            UploadService.start(ctx, fileUri!!, fileName!!, target, if (useManual) manualIp.trim() else null)
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
                    if (base != null && !reachable) Text("TV injoignable — si une vidéo est en cours, elle continue sur la TV.",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    if (message.isNotEmpty()) Text(message, color = cs.error)
                }
            }

            // --- Files stored on the TV ---
            info?.let { i ->
                item { SectionHeader("Sur la TV · ${size(i.free)} libres") }
                items(i.files) { (name, sz) ->
                    ListItem(
                        modifier = Modifier.clickable { cmd("Lire") { play(name) } },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = { Artwork(40.dp) },
                        headlineContent = { Text(name, maxLines = 1) },
                        supportingContent = { Text(size(sz)) },
                        trailingContent = {
                            IconButton({ cmd("Supprimer") { delete(name) } }) { Icon(Icons.Filled.Delete, "Supprimer") }
                        })
                }
            }
        }
        current?.let { i ->
            MiniPlayer(i.name!!, "CastBridge TV", i.state == "playing", if (i.dur > 0) i.pos.toFloat() / i.dur else 0f,
                onToggle = { cmd("Pause") { if (i.state == "playing") pause() else resume() } },
                onStop = { cmd("Stop") { stop() } }, onOpen = { showPlayer = true })
        }
    }
    if (showPlayer && current != null) {
        NowPlayingSheet(current.name!!, "CastBridge TV", current.state == "playing", current.pos, current.dur,
            onSeek = { ms -> cmd("Seek") { seek(ms) } },
            onToggle = { cmd("Pause") { if (current.state == "playing") pause() else resume() } },
            onSkip = { d -> cmd("Seek") { seek((current.pos + d * 1000L).coerceAtLeast(0)) } },
            onStop = { cmd("Stop") { stop() }; showPlayer = false }, onDismiss = { showPlayer = false })
    }
}
