package castbridge.sender

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import castbridge.core.tv.Pin
import castbridge.core.tv.ResumableUpload
import castbridge.core.tv.TvClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Transport used to reach CastBridge TV. */
enum class Channel(val label: String) { WIFI("Wi-Fi"), BLUETOOTH("Bluetooth"), WIFI_DIRECT("Wi-Fi Direct") }

/** "CastBridge TV" tab: pick the channel, then use the matching screen. */
@Composable
fun TvHub() {
    var channel by rememberSaveable { mutableStateOf(Channel.WIFI) }
    Column(Modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Channel.values().forEachIndexed { i, c ->
                SegmentedButton(channel == c, { channel = c }, SegmentedButtonDefaults.itemShape(i, Channel.values().size)) {
                    Text(c.label, maxLines = 1)
                }
            }
        }
        when (channel) {
            Channel.WIFI -> TvScreen(extra = { AdminPanel(it) })
            Channel.BLUETOOTH -> BtScreen()
            Channel.WIFI_DIRECT -> WifiDirectScreen()
        }
    }
}

/** System page picker returning the chosen document and its display name. */
@Composable
fun rememberFilePicker(onPicked: (Uri, String) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val name = runCatching {
                ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
                }
            }.getOrNull() ?: uri.lastPathSegment ?: "video"
            onPicked(uri, name)
        }
    }
    return { launcher.launch(arrayOf("video/*", "audio/*", "*/*")) }
}

fun formatSize(b: Long) = when {
    b >= 1L shl 30 -> "%.1f Go".format(b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> "%.0f Mo".format(b / (1L shl 20).toDouble())
    else -> "${b / 1024} ko"
}

fun hasBtPermission(ctx: Context) =
    Build.VERSION.SDK_INT < 31 || ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

/** Bluetooth channel: send a file to a paired TV. Slower than Wi-Fi (a few hundred ko/s to ~1 Mo/s), but needs no network. */
@SuppressLint("MissingPermission")
@Composable
fun BtScreen() {
    val ctx = LocalContext.current
    val pins = remember { PinStore(ctx) }
    var granted by remember { mutableStateOf(hasBtPermission(ctx)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val adapter = remember { ctx.getSystemService(BluetoothManager::class.java)?.adapter }
    var refresh by remember { mutableIntStateOf(0) }
    val devices = remember(granted, refresh) {
        if (granted) runCatching { adapter?.bondedDevices.orEmpty().map { (it.name ?: it.address) to it.address } }.getOrDefault(emptyList())
        else emptyList()
    }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var pin by remember(selected) { mutableStateOf(pins.get(selected?.let { "bt:$it" })) }
    var fileUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by rememberSaveable { mutableStateOf<String?>(null) }
    val pick = rememberFilePicker { u, n -> fileUri = u; fileName = n }
    val state by BtUploadService.state.collectAsState()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Envoi par Bluetooth : appairez d'abord le téléphone avec la TV (réglages Bluetooth). " +
            "Sur la TV, ouvrez CastBridge TV (MENU > rendre visible si besoin). Plus lent que le Wi-Fi, mais sans réseau.",
            style = MaterialTheme.typography.bodySmall)
        when {
            adapter == null -> Text("Ce téléphone n'a pas de Bluetooth.", color = MaterialTheme.colorScheme.error)
            !granted -> Button(onClick = { ask.launch(Manifest.permission.BLUETOOTH_CONNECT) }) { Text("Autoriser le Bluetooth") }
            !adapter.isEnabled -> Text("Activez le Bluetooth du téléphone.", color = MaterialTheme.colorScheme.error)
            else -> {
                if (devices.isEmpty()) Text("Aucun appareil appairé.")
                devices.forEach { (name, addr) ->
                    Row(Modifier.fillMaxWidth()) {
                        RadioButton(selected == addr, onClick = { selected = addr })
                        Text("$name  ($addr)", Modifier.padding(top = 12.dp))
                    }
                }
                OutlinedButton(onClick = { refresh++ }) { Text("Actualiser") }
            }
        }
        PinField(pins, selected?.let { "bt:$it" }, pin, { pin = it }, Modifier.fillMaxWidth())
        OutlinedButton(onClick = pick) { Text("Choisir un fichier") }
        Text("Fichier : ${fileName ?: "aucun"}")
        val busy = state is ResumableUpload.State.Uploading || state is ResumableUpload.State.Waiting
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !busy && granted && selected != null && Pin.isValidFormat(pin) && fileUri != null && fileName != null,
                onClick = { BtUploadService.start(ctx, fileUri!!, fileName!!, selected!!, pin) }) { Text("Envoyer") }
            if (busy) OutlinedButton(onClick = { BtUploadService.cancel(ctx) }) { Text("Annuler") }
        }
        when (val u = state) {
            is ResumableUpload.State.Uploading -> {
                LinearProgressIndicator({ u.sent.toFloat() / u.total }, Modifier.fillMaxWidth())
                Text("Envoi : ${formatSize(u.sent)} / ${formatSize(u.total)}")
            }
            is ResumableUpload.State.Waiting -> {
                LinearProgressIndicator({ u.sent.toFloat() / u.total }, Modifier.fillMaxWidth())
                Text("Liaison perdue, reprise automatique à ${formatSize(u.sent)} (${u.reason})")
            }
            ResumableUpload.State.Done -> Text("Fichier reçu par la TV (dans son dossier de l'app).")
            is ResumableUpload.State.Failed -> Text("Échec : ${u.reason}", color = MaterialTheme.colorScheme.error)
            null -> {}
        }
    }
}

/** Volume and system information of the TV (Wi-Fi / Wi-Fi Direct channels). */
@Composable
fun AdminPanel(client: TvClient) {
    var sys by remember(client.base) { mutableStateOf<String?>(null) }
    var vol by remember(client.base) { mutableStateOf<Float?>(null) }
    var touched by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf(false) }

    LaunchedEffect(client) {
        while (true) {
            val r = withContext(Dispatchers.IO) { runCatching { client.sysinfo() } }
            r.onSuccess { sys = it; if (!touched) vol = TvClient.num(it, "volume")?.toFloat() }
            // Stop on a refused PIN: retries would trigger the TV's lockout.
            if (r.exceptionOrNull().let { it is TvClient.HttpError && it.code == 401 }) break
            delay(5000)
        }
    }
    val j = sys ?: return
    StoragePanel(client)
    HorizontalDivider()
    Text("Volume de la TV", style = MaterialTheme.typography.titleSmall)
    vol?.let { v ->
        Slider(v, { vol = it; touched = true }, valueRange = 0f..100f, onValueChangeFinished = {
            val p = vol?.toInt() ?: 0
            scope.launch(Dispatchers.IO) { runCatching { client.setVolume(p) } }; touched = false
        })
        Text("${v.toInt()} %")
    } ?: Text("Volume indisponible sur cette TV.")
    Text("Informations système", style = MaterialTheme.typography.titleSmall)
    val battery = TvClient.num(j, "battery")
    val up = (TvClient.num(j, "uptime") ?: 0) / 60000
    Text("Modèle : ${TvClient.str(j, "model")}\nAndroid : ${TvClient.str(j, "android")}\nIP : ${TvClient.str(j, "ip") ?: "?"}\n" +
        "Batterie : ${battery?.let { "$it %" } ?: "aucune"}\nAllumée depuis : ${up / 60} h ${up % 60} min\nApp TV : ${TvClient.str(j, "app")}\n" +
        "Mémoire de l'app : ${TvClient.num(j, "pssMb") ?: "?"} Mo, RAM libre : ${TvClient.num(j, "memAvailMb") ?: "?"} / ${TvClient.num(j, "memTotalMb") ?: "?"} Mo")
    UpdatePanel(client)
    OutlinedButton(onClick = { confirm = true }) { Text("Redémarrer l'app TV") }
    Text("Redémarre l'application CastBridge TV uniquement : une app ne peut pas redémarrer la TV ni changer ses réglages système.",
        style = MaterialTheme.typography.bodySmall)
    if (confirm) AlertDialog(onDismissRequest = { confirm = false },
        confirmButton = { TextButton(onClick = { confirm = false; scope.launch(Dispatchers.IO) { runCatching { client.restart() } } }) { Text("Redémarrer") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Annuler") } },
        title = { Text("Redémarrer l'app TV ?") })
}


/** Sends one or several APKs to the TV over Wi-Fi and asks the TV to install them (no USB stick): updates of CastBridge TV or any app. */
@Composable
fun UpdatePanel(client: TvClient) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val upload by UploadService.state.collectAsState()
    var picked by remember { mutableStateOf<List<Pair<Uri, String>>>(emptyList()) }
    var info by remember(client.base) { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf("") }
    var next by remember { mutableStateOf(-1) }                       // index of the APK being uploaded, -1 = idle
    val host = client.base.removePrefix("http://")

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        picked = uris.map { u ->
            runCatching { ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            u to (runCatching {
                ctx.contentResolver.query(u, null, null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
                }
            }.getOrNull() ?: u.lastPathSegment ?: "app.apk")
        }
        msg = ""; next = -1
    }

    fun refresh() = scope.launch {
        info = withContext(Dispatchers.IO) { runCatching { client.updateInfo() }.getOrNull() } ?: info
    }
    LaunchedEffect(client.base) { refresh() }

    fun startUpload(i: Int) {
        val (uri, name) = picked[i]
        next = i; msg = "Envoi de $name (${i + 1}/${picked.size})…"
        runCatching { UploadService.start(ctx, uri, name, host, host, client.pin, progressive = false, autoPlay = false) }
            .onFailure { next = -1; msg = "Impossible de démarrer l'envoi : ${it.message}" }
    }

    // Upload the APKs one after the other, then ask the TV to install them all.
    LaunchedEffect(upload) {
        val u = upload
        if (next < 0 || next >= picked.size) return@LaunchedEffect
        if (u is UploadService.State.Done && u.job.fileName == picked[next].second) {
            if (next + 1 < picked.size) { startUpload(next + 1); return@LaunchedEffect }
            next = -1
            val names = picked.map { it.second }
            msg = "Envoyé. Installation demandée à la TV…"
            val r = withContext(Dispatchers.IO) { runCatching { client.installApks(names) } }
            r.onFailure { e ->
                msg = (e as? TvClient.HttpError)?.let { h -> TvClient.str(h.message.orEmpty().substringAfter(": "), "error") ?: h.message } ?: "Échec : ${e.message}"
                return@LaunchedEffect
            }
            msg = "Installation lancée : validez-la à l'écran de la TV avec la télécommande si elle le demande."
            // Follow the outcome; when it is this app that gets replaced, the TV goes away for a moment.
            repeat(90) {
                delay(2000)
                val j = withContext(Dispatchers.IO) { runCatching { client.updateInfo() }.getOrNull() }
                if (j == null) { msg = "La TV ne répond plus (normal si CastBridge TV vient d'être mise à jour) : rouvrez « CastBridge TV » sur la TV puis appuyez sur Actualiser."; return@LaunchedEffect }
                info = j
                when (TvClient.str(j, "status")) {
                    "failed", "permission" -> { msg = TvClient.str(j, "message").orEmpty(); return@LaunchedEffect }
                    "done" -> { msg = "Installation terminée."; return@LaunchedEffect }
                    "confirm" -> msg = TvClient.str(j, "message").orEmpty()
                }
            }
        } else if (u is UploadService.State.Failed) { next = -1; msg = "Envoi échoué : ${u.reason}" }
    }

    HorizontalDivider()
    Text("Installer des APK sur la TV (par Wi-Fi)", style = MaterialTheme.typography.titleSmall)
    info?.let {
        Text("CastBridge TV installée : ${TvClient.str(it, "versionName")} (code ${TvClient.num(it, "versionCode")})" +
            if (it.contains("\"canInstall\":false")) "\nÀ faire une fois sur la TV : autoriser « Installer des apps inconnues » pour CastBridge TV." else "",
            style = MaterialTheme.typography.bodySmall)
    }
    val busy = upload is UploadService.State.Uploading || upload is UploadService.State.Waiting
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { launcher.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream", "*/*")) }) { Text("Choisir des APK") }
        Button(enabled = !busy && next < 0 && picked.isNotEmpty() && picked.all { it.second.endsWith(".apk", true) },
            onClick = { startUpload(0) }) { Text("Envoyer et installer") }
        TextButton(onClick = { refresh() }) { Text("Actualiser") }
    }
    picked.forEach { Text("• ${it.second}", style = MaterialTheme.typography.bodySmall) }
    if (picked.any { !it.second.endsWith(".apk", true) }) Text("Seuls les fichiers .apk sont acceptés (pour un .xapk/.apks, extrayez les .apk).", style = MaterialTheme.typography.bodySmall)
    (upload as? UploadService.State.Uploading)?.takeIf { next >= 0 }?.let {
        LinearProgressIndicator({ it.sent.toFloat() / it.total }, Modifier.fillMaxWidth())
    }
    if (msg.isNotEmpty()) Text(msg, style = MaterialTheme.typography.bodyMedium)
    Text("Plusieurs fichiers d'une même application (APK fractionnés) sont installés ensemble. Une mise à jour d'une app déjà installée doit avoir la même signature. La TV demande une confirmation à l'écran pour chaque application.",
        style = MaterialTheme.typography.bodySmall)
}
