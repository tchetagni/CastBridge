package castbridge.sender

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
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

private fun time(ms: Long): String { val s = ms / 1000; return "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) }

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
    var seeking by remember { mutableStateOf<Float?>(null) }

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

    Column(Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Lancez « CastBridge TV » sur la TV. La vidéo est copiée entièrement sur la TV : " +
            "la lecture continue même si le téléphone quitte le Wi-Fi.", style = MaterialTheme.typography.bodySmall)

        // --- Target ---
        if (!useManual) {
            if (tvs.isEmpty()) Text("Recherche des TV CastBridge…")
            tvs.forEach { tv ->
                Row(Modifier.fillMaxWidth()) {
                    RadioButton(selectedName == tv.name, onClick = { selectedName = tv.name })
                    Text("${tv.name}  (${tv.host})", Modifier.padding(top = 12.dp))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(useManual, onCheckedChange = { useManual = it })
            OutlinedTextField(manualIp, { manualIp = it }, Modifier.weight(1f), enabled = useManual, singleLine = true,
                label = { Text("IP de la TV (manuelle)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { discovery.restart() }) { Text("Rechercher") }
            OutlinedButton(onClick = { picker.launch(arrayOf("video/*", "audio/*")) }) { Text("Choisir un fichier") }
        }
        Text("Fichier : ${fileName ?: "aucun"}")

        // --- Upload ---
        val busy = upload is UploadService.State.Uploading || upload is UploadService.State.Waiting
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !busy && base != null && fileUri != null && fileName != null, onClick = {
                val target = if (useManual) manualIp.trim() else selectedName!!
                runCatching {
                    UploadService.start(ctx, fileUri!!, fileName!!, target, if (useManual) manualIp.trim() else null)
                }.onFailure { message = "Impossible de démarrer l'envoi : ${it.message}" }
            }) { Text("Envoyer et lire") }
            if (busy) OutlinedButton(onClick = { UploadService.cancel(ctx) }) { Text("Annuler") }
        }
        when (val u = upload) {
            is UploadService.State.Uploading -> {
                LinearProgressIndicator({ u.sent.toFloat() / u.total }, Modifier.fillMaxWidth())
                Text("Envoi : ${size(u.sent)} / ${size(u.total)}")
            }
            is UploadService.State.Waiting -> {
                LinearProgressIndicator({ u.sent.toFloat() / u.total }, Modifier.fillMaxWidth())
                Text("En attente du réseau, reprise automatique à ${size(u.sent)} (${u.reason})")
            }
            is UploadService.State.Done -> Text("« ${u.job.fileName} » est sur la TV, lecture lancée.")
            is UploadService.State.Failed -> Text("Échec : ${u.reason}", color = MaterialTheme.colorScheme.error)
            UploadService.State.Idle -> {}
        }

        // --- TV state & controls ---
        if (base != null && !reachable) Text("TV injoignable — si une vidéo est en cours, elle continue sur la TV.")
        info?.let { i ->
            if (i.name != null && i.state != "idle") {
                Text("${if (i.state == "playing") "▶" else "❚❚"} ${i.name}", style = MaterialTheme.typography.titleMedium)
                if (i.dur > 0) {
                    Slider(seeking ?: i.pos.toFloat(), { seeking = it }, valueRange = 0f..i.dur.toFloat(),
                        onValueChangeFinished = { val t = seeking?.toLong() ?: 0; seeking = null; cmd("Seek") { seek(t) } })
                    Text("${time(i.pos)} / ${time(i.dur)}")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { cmd("Recul") { seek(maxOf(0, i.pos - 10_000)) } }) { Text("-10 s") }
                    Button(onClick = { cmd("Pause") { if (i.state == "playing") pause() else resume() } }) {
                        Text(if (i.state == "playing") "Pause" else "Lecture")
                    }
                    Button(onClick = { cmd("Avance") { seek(i.pos + 10_000) } }) { Text("+10 s") }
                    OutlinedButton(onClick = { cmd("Stop") { stop() } }) { Text("Stop") }
                }
            }
            Text("Sur la TV (${size(i.free)} libres) :", style = MaterialTheme.typography.titleSmall)
            LazyColumn(Modifier.weight(1f)) {
                items(i.files) { (name, sz) ->
                    ListItem(headlineContent = { Text(name) }, supportingContent = { Text(size(sz)) },
                        trailingContent = {
                            Row {
                                TextButton(onClick = { cmd("Lire") { play(name) } }) { Text("Lire") }
                                TextButton(onClick = { cmd("Supprimer") { delete(name) } }) { Text("Supprimer") }
                            }
                        })
                }
            }
        }
        if (message.isNotEmpty()) Text(message, color = MaterialTheme.colorScheme.error)
    }
}
