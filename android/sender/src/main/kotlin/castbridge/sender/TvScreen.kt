package castbridge.sender

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

private fun time(ms: Long): String { val s = ms / 1000; return "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) }

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
    var badPin by remember { mutableStateOf<String?>(null) }
    var seeking by remember { mutableStateOf<Float?>(null) }
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

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Lancez « CastBridge TV » sur la TV. La vidéo est copiée entièrement sur la TV : " +
            "la lecture continue même si le téléphone quitte le Wi-Fi.", style = MaterialTheme.typography.bodySmall)

        // --- Target ---
        if (!useManual && fixedBase == null) {
            if (tvs.isEmpty()) Text("Recherche des TV CastBridge…")
            tvs.forEach { tv ->
                Row(Modifier.fillMaxWidth()) {
                    RadioButton(selectedName == tv.name, onClick = { selectedName = tv.name })
                    Text("${tv.name}  (${tv.host})", Modifier.padding(top = 12.dp))
                }
            }
        }
        if (fixedBase == null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(useManual, onCheckedChange = { useManual = it })
            OutlinedTextField(manualIp, { manualIp = it }, Modifier.weight(1f), enabled = useManual, singleLine = true,
                label = { Text("IP de la TV (manuelle)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
        }
        PinField(pins, pinKey, pin, { pin = it }, Modifier.fillMaxWidth())
        badPin?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { discovery.restart() }) { Text("Rechercher") }
            OutlinedButton(onClick = { picker.launch(arrayOf("video/*", "audio/*")) }) { Text("Choisir un fichier") }
        }
        Text("Fichier : ${fileName ?: "aucun"}")

        // --- Upload ---
        val busy = upload is UploadService.State.Uploading || upload is UploadService.State.Waiting
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(progressive, onCheckedChange = { progressive = it }, enabled = !busy)
            Text("Lire pendant l'envoi (le fichier n'a pas besoin de tenir en entier sur la TV avant de démarrer)", Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !busy && base != null && fileUri != null && fileName != null, onClick = {
                val target = if (useManual) manualIp.trim() else selectedName ?: fixedBase!!
                runCatching {
                    UploadService.start(ctx, fileUri!!, fileName!!, target, fixedBase?.removePrefix("http://") ?: if (useManual) manualIp.trim() else null, pin, progressive)
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

        notice?.let { Text(it, color = MaterialTheme.colorScheme.tertiary) }

        // --- TV state & controls ---
        if (base != null && !reachable) Text("TV injoignable — si une vidéo est en cours, elle continue sur la TV.")
        info?.let { i ->
            if (i.name != null && i.state != "idle") {
                val running = i.state == "playing" || i.state == "buffering"
                val partial = i.received[i.name]                       // bytes received if the file is still arriving
                val total = i.files.firstOrNull { it.first == i.name }?.second ?: 0L
                Text("${if (running) "▶" else "❚❚"} ${i.name}" + if (i.state == "buffering") "  (mise en mémoire tampon)" else "",
                    style = MaterialTheme.typography.titleMedium)
                if (partial != null && total > 0) {
                    val ahead = (castbridge.core.tv.Progressive.reachableMs(i.dur, partial, total, 0) - i.pos).coerceAtLeast(0)
                    Text("Envoi ${partial * 100 / total} % : encore ${if (ahead >= 90_000) "${ahead / 60_000} min" else "${ahead / 1000} s"} de lecture sans réseau")
                    val videoRate = if (i.dur > 0) total * 1000 / i.dur else 0L
                    if (speed > 0) Text("Envoi ${size(speed)}/s, vidéo ${size(videoRate)}/s" +
                        if (castbridge.core.tv.Progressive.willStall(total, i.dur, speed)) " : trop lent, la lecture va s'interrompre" else "")
                    if (castbridge.core.tv.Progressive.willStall(total, i.dur, speed))
                        OutlinedButton(onClick = { UploadService.switchToFullPreload(); cmd("Stop") { stop() } }) { Text("Basculer en préchargement complet") }
                }
                if (i.dur > 0) {
                    Slider(seeking ?: i.pos.toFloat(), { seeking = it }, valueRange = 0f..i.dur.toFloat(),
                        onValueChangeFinished = {
                            var t = seeking?.toLong() ?: 0; seeking = null
                            // Nothing exists on the TV beyond what was received: clamp (backward seeks stay free).
                            if (partial != null && total > 0) {
                                val max = castbridge.core.tv.Progressive.reachableMs(i.dur, partial, total)
                                if (t > max) { t = max; message = "En attente de l'envoi : position limitée à ${time(max)}" }
                            }
                            cmd("Seek") { seek(t) }
                        })
                    Text("${time(i.pos)} / ${time(i.dur)}")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { cmd("Recul") { seek(maxOf(0, i.pos - 10_000)) } }) { Text("-10 s") }
                    Button(onClick = { cmd("Pause") { if (running) pause() else resume() } }) {
                        Text(if (running) "Pause" else "Lecture")
                    }
                    Button(onClick = { cmd("Avance") { seek(i.pos + 10_000) } }) { Text("+10 s") }
                    OutlinedButton(onClick = { cmd("Stop") { stop() } }) { Text("Stop") }
                }
            }
            Text("Sur la TV (${size(i.free)} libres) :", style = MaterialTheme.typography.titleSmall)
            Column {
                i.files.forEach { (name, sz) ->
                    ListItem(headlineContent = { Text(name) },
                        supportingContent = { Text(i.received[name]?.let { r -> "${size(r)} / ${size(sz)} reçus (${r * 100 / sz.coerceAtLeast(1)} %)" } ?: size(sz)) },
                        trailingContent = {
                            Row {
                                TextButton(onClick = { cmd("Lire") { play(name) } }) { Text("Lire") }
                                TextButton(onClick = { cmd("Supprimer") { delete(name) } }) { Text("Supprimer") }
                            }
                        })
                }
            }
        }
        client?.let { extra(it) }
        if (message.isNotEmpty()) Text(message, color = MaterialTheme.colorScheme.error)
    }
}
