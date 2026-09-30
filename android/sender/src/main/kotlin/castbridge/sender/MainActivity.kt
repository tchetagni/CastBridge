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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import castbridge.core.upnp.Didl
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CastTheme { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { Root() } } }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun Root() {
        var tab by rememberSaveable { mutableStateOf(0) }
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Column {
                    TopAppBar(
                        title = { Text("CastBridge") },
                        navigationIcon = { Icon(Icons.Filled.Cast, null, Modifier.padding(start = 16.dp, end = 8.dp), tint = MaterialTheme.colorScheme.primary) },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                    )
                    // scrollable: four tabs never squeeze or wrap their labels on a narrow phone
                    ScrollableTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface, edgePadding = 0.dp) {
                        Tab(tab == 0, onClick = { tab = 0 }, text = { Text("TV DLNA", maxLines = 1) })
                        Tab(tab == 1, onClick = { tab = 1 }, text = { Text("CastBridge TV", maxLines = 1) })
                        Tab(tab == 2, onClick = { tab = 2 }, text = { Text("Quiz", maxLines = 1) })
                        Tab(tab == 3, onClick = { tab = 3 }, text = { Text("Échecs", maxLines = 1) })
                        Tab(tab == 4, onClick = { tab = 4 }, text = { Text("Sur le téléphone", maxLines = 1) })
                    }
                }
            },
            bottomBar = { castbridge.sender.player.CastMiniBar(Modifier.navigationBarsPadding()) },
        ) { pad -> Box(Modifier.padding(pad).fillMaxSize()) { when (tab) { 0 -> App(); 1 -> TvHub(); 2 -> QuizScreen(); 3 -> ChessScreen(); else -> castbridge.sender.player.PhoneLibraryScreen() } } }
        MoveHandler()
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
        var paused by remember { mutableStateOf(false) }
        var pos by remember { mutableStateOf(0L) }
        var dur by remember { mutableStateOf(0L) }
        var showPlayer by remember { mutableStateOf(false) }

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
        fun stopPlayback() = scope.launch {
            runCatching { Upnp.stop(selected!!) }
            playing = false; paused = false; pos = 0; showPlayer = false; status = "Arrêté"
            stopService(Intent(this@MainActivity, ServerService::class.java))
        }
        fun toggle() { scope.launch { runCatching { if (paused) Upnp.resume(selected!!) else Upnp.pause(selected!!) }.onSuccess { paused = !paused } } }
        fun cast() {
            val r = selected ?: return; val uri = fileUri ?: return
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
                    .onSuccess { playing = true; paused = false; status = "" }
                    .onFailure { status = "Échec : ${it.message}" }
            }
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

        Column(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.weight(1f)) {
                item {
                    SectionHeader("Lecteurs") {
                        IconButton({ search() }, enabled = !searching) { Icon(Icons.Filled.Refresh, "Rechercher") }
                    }
                }
                if (searching) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                items(renderers) { r -> DeviceRow(r.name, "DLNA / UPnP", selected == r) { selected = r } }
                item { SectionHeader("Bibliothèque") }
                item {
                    ListItem(
                        modifier = Modifier.clickable { picker.launch(arrayOf("video/*", "audio/*", "image/*")) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = { Icon(Icons.Filled.VideoLibrary, null, tint = MaterialTheme.colorScheme.primary) },
                        headlineContent = { Text(fileName, maxLines = 1) },
                        supportingContent = { Text("Toucher pour choisir un fichier") },
                    )
                }
                if (status.isNotEmpty()) item {
                    Text(status, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium,
                        color = if (status.startsWith("Échec") || status.startsWith("Seek")) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (playing) {
                MiniPlayer(fileName, selected?.name.orEmpty(), !paused, if (dur > 0) pos.toFloat() / dur else 0f,
                    ::toggle, { stopPlayback() }) { showPlayer = true }
            } else {
                Button(enabled = selected != null && fileUri != null, onClick = ::cast,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Icon(Icons.Filled.Cast, null); Spacer(Modifier.width(8.dp)); Text("Diffuser")
                }
            }
            if (playing) HandoffButton(fileUri, fileName, pos, dur) {
                scope.launch {
                    runCatching { Upnp.stop(selected!!) }
                    playing = false; paused = false; showPlayer = false; pos = 0; status = "Lecture poursuivie sur la TV CastBridge"
                    stopService(Intent(this@MainActivity, ServerService::class.java))
                }
            }
        }
        if (showPlayer && playing) {
            NowPlayingSheet(fileName, selected?.name.orEmpty(), !paused, pos * 1000, dur * 1000,
                onSeek = { ms -> scope.launch { runCatching { Upnp.seek(selected!!, ms / 1000) }.onFailure { status = "Seek : ${it.message}" } } },
                onToggle = ::toggle,
                onSkip = { d -> scope.launch { runCatching { Upnp.seek(selected!!, (pos + d).coerceIn(0, maxOf(dur, 0))) } } },
                onStop = { stopPlayback() }, onDismiss = { showPlayer = false })
        }
    }
}
