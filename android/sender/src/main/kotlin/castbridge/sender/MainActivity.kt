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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
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
        setTheme(R.style.Theme_CastBridge) // leaves the launch theme (splash) for the normal one
        super.onCreate(savedInstanceState)
        setContent { CastTheme { SyncSystemBars(); Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { Gate() } } }
        installFrom(intent)
    }

    override fun onResume() {
        super.onResume()
        TransferQueue.resume(this)                 // R-09: a queue saved before the app was killed (or paused by Android in the background) goes on
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        installFrom(intent)
    }

    /** « Mise à jour prête — Installer » notification: install now (Android asks for confirmation). */
    private fun installFrom(i: Intent?) {
        i?.getStringExtra(TvHomeRequest.EXTRA)?.let { TvHomeRequest.pending.value = it; i.removeExtra(TvHomeRequest.EXTRA) }   // « Ouvrir avec CastBridge »
        if (i?.getBooleanExtra(PhoneUpdater.EXTRA_INSTALL, false) != true) return
        i.removeExtra(PhoneUpdater.EXTRA_INSTALL)
        PhoneConnect.feature("updates", "notification")
        PhoneConnect.agent.post { if (update.file != null) offerInstall(true) else checkUpdate(castbridge.core.update.UpdateSchedule.Trigger.USER) }
    }

    override fun onStart() {
        super.onStart()
        ParentalSession.onForeground()        // the Parental tab locks again after a few minutes in the background
    }

    override fun onStop() {
        super.onStop()
        ParentalSession.onBackground()
        PhoneConnect.screens.leave()
    }

    /** Information screen first (nothing sent before), then a mandatory update if any, then the app. */
    @Composable
    fun Gate() {
        rememberLinkVersion()
        val st = PhoneConnect.state
        var consented by remember { mutableStateOf(!st.needsConsent) }
        if (st.needsConsent) consented = false
        val u = PhoneConnect.link.update
        when {
            !consented -> ConsentScreen { consented = true }
            mustUpdate(u) -> MandatoryUpdateScreen(u)
            else -> Root()
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun Root() {
        // opens on the task home « CastBridge TV », not on « TV DLNA » (castbridge.core.ux.PhoneTabs; order and telemetry ids unchanged)
        var tab by rememberSaveable { mutableStateOf(castbridge.core.ux.PhoneTabs.START) }
        var settings by rememberSaveable { mutableStateOf(false) }
        val home = castbridge.core.ux.PhoneTabs.index(castbridge.core.ux.PhoneTabs.Tab.HOME)
        // screen_time per tab (ids of EventCatalog: the "CastBridge TV" tab is the app's home); the Parental tab is never reported
        LaunchedEffect(tab, settings) { if (!settings) castbridge.core.ux.PhoneTabs.at(tab).screen?.let { PhoneConnect.screens.enter(it) } }
        fun select(i: Int) {
            if (i != tab) castbridge.core.ux.PhoneTabs.at(i).feature?.let { PhoneConnect.feature(it, "tile") }
            tab = i
        }
        val tvRequest by TvHomeRequest.pending.collectAsState()
        LaunchedEffect(tvRequest) { if (tvRequest != null) { settings = false; tab = home } }     // the CastBridge TV tab, where TvHome consumes the request
        // « où en est ma copie » : the send queue, visible from every other tab (the home has the full card)
        val queueItems by TransferQueue.items.collectAsState()
        val queueNote by TransferQueue.note.collectAsState()
        val glance = castbridge.core.ux.QueueGlances.of(queueItems, queueNote)
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            // hidden entry (nothing on screen mentions it): 7 taps on the logo then a long press. Only reacts if this build carries the owner's hash.
                            val seq = remember { castbridge.core.owner.TapSequence() }
                            val hidden = if (castbridge.owner.SuperAdmin.enabled) Modifier.pointerInput(Unit) {
                                detectTapGestures(onTap = { seq.tap() }, onLongPress = { if (seq.longPress()) castbridge.owner.SuperAdmin.open(this@MainActivity) })
                            } else Modifier
                            Box(hidden) { CastBridgeLogo(32.dp) }
                        },
                        actions = {
                            TextButton({ ActivateTvActivity.open(this@MainActivity) }) { Text("Activer la TV") }
                            TextButton({ RentalDeliveryActivity.open(this@MainActivity) }) { Text("Locations") }
                            IconButton({ ParentalActivity.open(this@MainActivity) }) { Icon(Icons.Filled.Lock, "Contrôle parental") }
                            IconButton({ settings = true }) { CbIcon(R.drawable.ic_cb_reglages, "Réglages") }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                    )
                    // scrollable: four tabs never squeeze or wrap their labels on a narrow phone
                    ScrollableTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface, edgePadding = 0.dp) {
                        val ic = Modifier.size(20.dp)
                        LeadingIconTab(tab == 0, onClick = { select(0) }, text = { Text(castbridge.core.ux.PhoneTabs.Tab.DLNA.label, maxLines = 1) }, icon = { CbIcon(R.drawable.ic_cb_caster, null, ic) })
                        LeadingIconTab(tab == 1, onClick = { select(1) }, text = { Text(castbridge.core.ux.PhoneTabs.Tab.HOME.label, maxLines = 1) }, icon = { CbIcon(R.drawable.ic_cb_recevoir_du_telephone, null, ic) })
                        LeadingIconTab(tab == 2, onClick = { select(2) }, text = { Text(castbridge.core.ux.PhoneTabs.Tab.GAMES.label, maxLines = 1) }, icon = { CbIcon(R.drawable.ic_cb_quiz, null, ic) })
                        LeadingIconTab(tab == 3, onClick = { select(3) }, text = { Text(castbridge.core.ux.PhoneTabs.Tab.PHONE.label, maxLines = 1) }, icon = { CbIcon(R.drawable.ic_cb_sur_le_telephone, null, ic) })
                        LeadingIconTab(tab == 4, onClick = { select(4) }, text = { Text(castbridge.core.ux.PhoneTabs.Tab.LEARN.label, maxLines = 1) }, icon = { CbIcon(R.drawable.ic_cb_apprendre, null, ic) })
                        LeadingIconTab(tab == 5, onClick = { select(5) }, text = { Text(castbridge.core.ux.PhoneTabs.Tab.PARENTAL.label, maxLines = 1) }, icon = { Icon(Icons.Filled.Lock, null, ic) })
                    }
                }
            },
            bottomBar = { castbridge.sender.player.CastMiniBar(Modifier.navigationBarsPadding()) },
        ) { pad ->
            Column(Modifier.padding(pad).fillMaxSize()) {
                // sub-brand wordmark (branding/logo) above the Apprendre tab; the Jeux tab shows its own cards
                if (castbridge.core.ux.QueueGlances.stripVisible(onHome = tab == home, glance = glance)) glance?.let { g -> QueueStrip(g) { select(home) } }
                if (tab == 4) SubBrandHeader(R.drawable.logo_apprendre_horizontal, "Apprendre")
                Box(Modifier.weight(1f).fillMaxWidth()) { when (tab) { 0 -> App(); 1 -> TvHub(); 2 -> GamesScreen(); 3 -> castbridge.sender.player.PhoneLibraryScreen(); 5 -> ParentalTab(); else -> LearnScreen() } }
            }
        }
        MoveHandler()
        if (settings) SettingsScreen { settings = false }
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
                val t0 = System.currentTimeMillis()
                PhoneConnect.track("cast_start", mapOf("channel" to "dlna", "mode" to "direct"))
                runCatching { Upnp.play(r, url, didl) }
                    .onSuccess { playing = true; paused = false; status = ""; PhoneConnect.castEnd("dlna", "direct", 0, System.currentTimeMillis() - t0, true) }
                    .onFailure { status = "Échec : ${it.message}"; PhoneConnect.castEnd("dlna", "direct", 0, System.currentTimeMillis() - t0, false, "refused") }
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
                        leadingContent = { Icon(cbv(R.drawable.ic_cb_bibliotheque), null, tint = MaterialTheme.colorScheme.primary) },
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
                    Icon(cbv(R.drawable.ic_cb_caster), null); Spacer(Modifier.width(8.dp)); Text(castbridge.core.ux.SendWay.LIVE.label)
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

/** « Où en est ma copie » : one line over every tab but the home while the send queue holds something (text and tone from QueueGlances). */
@Composable
private fun QueueStrip(g: castbridge.core.ux.QueueGlance, onOpen: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(color = if (g.error) cs.errorContainer else cs.secondaryContainer, modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            CbIcon(R.drawable.ic_cb_envoyer, null, Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(g.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                g.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
            }
            TextButton(onClick = onOpen) { Text(g.action) }
        }
    }
}
