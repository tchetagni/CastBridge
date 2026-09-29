package castbridge.sender

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import castbridge.core.upnp.Didl
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { Root() } } }
    }

    @Composable
    fun Root() {
        var tab by rememberSaveable { mutableStateOf(0) }
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            TabRow(selectedTabIndex = tab) {
                Tab(tab == 0, onClick = { tab = 0 }, text = { Text("TV DLNA") })
                Tab(tab == 1, onClick = { tab = 1 }, text = { Text("CastBridge TV") })
            }
            if (tab == 0) App() else TvHub()
        }
    }

    private fun startServer() {
        val i = Intent(this, ServerService::class.java)
        runCatching { startForegroundService(i) }
    }

    @Composable
    fun App() {
        val scope = rememberCoroutineScope()
        var renderers by remember { mutableStateOf(listOf<Renderer>()) }
        var selected by remember { mutableStateOf<Renderer?>(null) }
        var fileUri by remember { mutableStateOf<Uri?>(null) }
        var fileName by remember { mutableStateOf("Aucun fichier") }
        var status by remember { mutableStateOf("") }
        var searching by remember { mutableStateOf(false) }
        var playing by remember { mutableStateOf(false) }
        var pos by remember { mutableStateOf(0L) }
        var dur by remember { mutableStateOf(0L) }
        var seeking by remember { mutableStateOf<Float?>(null) }

        val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                fileUri = uri
                fileName = contentResolver.query(uri, null, null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
                } ?: uri.lastPathSegment.orEmpty()
            }
        }

        fun search() = scope.launch {
            searching = true; status = "Recherche des TV…"
            renderers = runCatching { Upnp.discover(this@MainActivity) }.getOrDefault(emptyList())
            searching = false
            status = if (renderers.isEmpty()) "Aucune TV trouvée (même Wi-Fi ? isolation des clients ?)" else ""
        }
        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
            search()
        }
        // Position polling while playing
        LaunchedEffect(playing, selected) {
            while (playing && selected != null) {
                runCatching { Upnp.position(selected!!) }.onSuccess { (p, d) -> pos = p; dur = d }
                delay(1000)
            }
        }

        Column(Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("CastBridge", style = MaterialTheme.typography.headlineMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { search() }, enabled = !searching) { Text("Rechercher") }
                Button(onClick = { picker.launch(arrayOf("video/*", "audio/*", "image/*")) }) { Text("Choisir un fichier") }
            }
            Text("Fichier : $fileName")
            LazyColumn(Modifier.weight(1f)) {
                items(renderers) { r ->
                    ListItem(
                        headlineContent = { Text(r.name) },
                        trailingContent = { RadioButton(selected == r, onClick = { selected = r }) },
                    )
                }
            }
            if (status.isNotEmpty()) Text(status)
            if (playing && dur > 0) {
                Slider(
                    value = seeking ?: pos.toFloat(), valueRange = 0f..dur.toFloat(),
                    onValueChange = { seeking = it },
                    onValueChangeFinished = {
                        val t = seeking?.toLong() ?: return@Slider
                        seeking = null
                        scope.launch { runCatching { Upnp.seek(selected!!, t) }.onFailure { status = "Seek : ${it.message}" } }
                    })
                Text("${pos / 60}:%02d / ${dur / 60}:%02d".format(pos % 60, dur % 60))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = selected != null && fileUri != null, onClick = {
                    val r = selected!!; val uri = fileUri!!
                    scope.launch {
                        val ip = Upnp.localIp() ?: run { status = "Pas d'adresse Wi-Fi"; return@launch }
                        startServer()
                        var srv = ServerService.server
                        repeat(20) { if (srv == null) { delay(100); srv = ServerService.server } }
                        val s = srv ?: run { status = "Serveur non démarré"; return@launch }
                        val mime = contentResolver.getType(uri) ?: "video/mp4"
                        val ext = fileName.substringAfterLast('.', "mp4")
                        val url = "http://$ip:8089/media/${s.register(uri, mime)}.$ext"
                        val didl = Didl.item(url, fileName, mime, Didl.protocolInfo(mime, MediaServer.FEATURES))
                        status = "Envoi vers ${r.name}…"
                        runCatching { Upnp.play(r, url, didl) }
                            .onSuccess { playing = true; status = "Lecture sur ${r.name}" }
                            .onFailure { status = "Échec : ${it.message}" }
                    }
                }) { Text("Diffuser") }
                Button(enabled = playing, onClick = { scope.launch { runCatching { Upnp.pause(selected!!) } } }) { Text("Pause") }
                Button(enabled = playing, onClick = { scope.launch { runCatching { Upnp.resume(selected!!) } } }) { Text("Lecture") }
                Button(enabled = playing, onClick = {
                    scope.launch {
                        runCatching { Upnp.stop(selected!!) }
                        playing = false; pos = 0; status = "Arrêté"
                        stopService(Intent(this@MainActivity, ServerService::class.java))
                    }
                }) { Text("Stop") }
            }
            if (playing) HandoffButton(fileUri, fileName, pos, dur) {
                scope.launch {
                    runCatching { Upnp.stop(selected!!) }
                    playing = false; pos = 0; status = "Lecture poursuivie sur la TV CastBridge"
                    stopService(Intent(this@MainActivity, ServerService::class.java))
                }
            }
        }
    }
}
