package castbridge.sender

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import castbridge.core.tv.LibraryLogic
import castbridge.core.tv.LibrarySections
import castbridge.core.tv.Pin
import castbridge.core.trust.TvAuth
import castbridge.core.tv.TvClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** "CastBridge TV" tab: the task-oriented home by default; the former screen (channels, manual IP, SSH, storage, APK) under "Avancé". */
@Composable
fun TvHub() {
    var advanced by rememberSaveable { mutableStateOf(false) }
    if (advanced) Column(Modifier.fillMaxSize()) {
        TextButton(onClick = { advanced = false }, Modifier.padding(start = 8.dp)) { Icon(Icons.Filled.ArrowBack, null); Spacer(Modifier.width(6.dp)); Text("Accueil") }
        TvHubAdvanced()
    } else TvHome(onAdvanced = { advanced = true })
}

/** The TV the home talks to (chosen once in the first-connection assistant). */
internal class HomeTv(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("castbridge_home", Context.MODE_PRIVATE)
    var name: String? get() = sp.getString("tv", null); set(v) { sp.edit().putString("tv", v).apply() }
}

/**
 * « Ouvrir avec CastBridge » asks the main screen to open on the CastBridge TV tab, on « Ajouter ma TV » or on the code entry
 * (the existing assistant « Trouvons votre TV »). One-shot: consumed by [MainActivity] (tab) and [TvHome] (screen).
 */
object TvHomeRequest {
    const val EXTRA = "castbridge.open"
    const val TV = "tv"; const val ADD_TV = "add_tv"; const val ENTER_PIN = "enter_pin"
    val pending = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
}

private fun eta(left: Long, bps: Long): String {
    if (bps <= 0 || left <= 0) return ""
    val s = left / bps
    return when { s >= 3600 -> "encore ${s / 3600} h ${s / 60 % 60} min"; s >= 60 -> "encore ${s / 60} min"; else -> "encore quelques secondes" }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvHome(onAdvanced: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val home = remember { HomeTv(ctx) }
    val pins = remember { PinStore(ctx) }
    val discovery = remember { TvDiscovery(ctx) }
    DisposableEffect(Unit) { discovery.start(); onDispose { discovery.stop() } }
    val tvs by discovery.tvs.collectAsState()
    var tvName by remember { mutableStateOf(home.name) }
    var pin by remember(tvName) { mutableStateOf(pins.get(tvName)) }
    // Plug and play: the TV this phone is trusted by connects by itself (Bluetooth HELLO, then Wi-Fi with this phone's token).
    val link by TvLinkManager.state.collectAsState()
    val session = (link as? LinkUi.Connected)?.session
    var adding by rememberSaveable { mutableStateOf(false) }
    var managing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { TvLinkManager.start(ctx) }
    var wizard by rememberSaveable { mutableStateOf(TvLinkManager.saved.list().isEmpty() && (tvName == null || !TvAuth.isUsable(pins.get(tvName)))) }
    val tv = tvs.firstOrNull { it.name == tvName }
    val client = session?.base?.let { TvClient(it, session.credential) }
        ?: tv?.takeIf { TvAuth.isUsable(pin) || TvLinkManager.saved.list().isEmpty() }?.let { TvClient(it.base, pin) }
    // sent by « Ouvrir avec CastBridge »: « Ajouter ma TV » or the code entry of the assistant
    val request by TvHomeRequest.pending.collectAsState()
    LaunchedEffect(request) {
        when (request) {
            TvHomeRequest.ADD_TV -> { adding = true; TvHomeRequest.pending.value = null }
            TvHomeRequest.ENTER_PIN -> { wizard = true; TvHomeRequest.pending.value = null }
            TvHomeRequest.TV -> TvHomeRequest.pending.value = null
        }
    }

    if (adding) {
        AddTvFlow(onClose = { adding = false }, onAdded = { t ->
            val n = t.mdns ?: t.name
            home.name = n; tvName = n; RemotePrefs(ctx).tvName = n; adding = false; wizard = false
        })
        return
    }
    if (wizard) {
        FirstConnection(tvs, onRetry = { discovery.restart() }, onAdvanced = onAdvanced, onAddTv = { adding = true }) { name, code ->
            home.name = name; pins.put(name, code); tvName = name; pin = code; wizard = false
        }
        return
    }
    if (managing) ManageTvsDialog(onDismiss = { managing = false }, onAdd = { adding = true })

    val upload by UploadService.state.collectAsState()
    val avg by UploadService.average.collectAsState()
    var info by remember { mutableStateOf<castbridge.core.tv.TvInfo?>(null) }
    var items by remember { mutableStateOf<List<TvLibItem>>(emptyList()) }
    var reachable by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var showLibrary by remember { mutableStateOf(false) }
    var showExchange by remember { mutableStateOf(false) }
    var showPlayer by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    LaunchedEffect(client?.base, client?.pin) {
        var n = 0
        while (client != null) {
            val r = withContext(Dispatchers.IO) { runCatching { castbridge.core.tv.TvInfo.parse(client.info()) } }
            reachable = r.isSuccess; info = r.getOrNull() ?: info
            if ((r.exceptionOrNull() as? TvClient.HttpError)?.code == 401) {
                if (TvAuth.isToken(client.pin)) { TvLinkManager.poke(); msg = "Reconnexion à la TV…"; delay(3000); continue }   // token expired or revoked: HELLO again
                msg = "Le code de la TV a changé : saisissez-le à nouveau."; wizard = true; break
            }
            if (n++ % 5 == 0) withContext(Dispatchers.IO) { runCatching { items = TvLibraryParser.parse(client.library()) } }
            delay(2000)
        }
    }
    var moveNext by remember { mutableStateOf(false) }       // the next picked file is moved (deleted from the phone once on the TV)
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty() || (tvName == null && session == null)) return@rememberLauncherForActivityResult
        val move = moveNext; moveNext = false
        var queued = 0
        uris.forEach { uri ->
            // Keep write access when the provider gives it: a move deletes the original once the TV holds it.
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
                .onFailure { runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
            val (name, size) = OpenWithActivity.describe(ctx, uri)
            // Little room on the TV: play while the file arrives instead of storing it all first (one file only).
            val progressive = uris.size == 1 && (info?.let { size > 0 && it.free < size * 2 } ?: false)
            if (session != null) {
                runCatching { TransferQueue.enqueue(ctx, uri, name, size, move, autoPlay = uris.size == 1, progressive = progressive); queued++ }
                    .onFailure { msg = "Impossible de mettre l'envoi en file : ${it.message}" }
            } else if (uris.size == 1) {
                runCatching { UploadService.start(ctx, uri, name, tvName!!, null, pin, progressive, move = move) }
                    .onFailure { msg = "Impossible de démarrer l'envoi : ${it.message}" }
            } else msg = "Plusieurs fichiers : ajoutez d'abord la TV (association) pour utiliser la file d'attente."
        }
        if (queued > 1) msg = "$queued fichiers ajoutés à la file d'attente : ils partent l'un après l'autre."
        else if (queued == 1 && session?.base == null) msg = "Envoi par Bluetooth (plus lent que le Wi-Fi)"
    }
    fun cmd(f: TvClient.() -> Unit) = scope.launch {
        val c = client ?: return@launch
        val e = withContext(Dispatchers.IO) { runCatching { c.f() }.exceptionOrNull() }
        msg = (e as? TvClient.HttpError)?.message?.substringAfter(": ")?.let { TvClient.str(it, "message") ?: TvClient.str(it, "error") } ?: e?.message
    }

    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // The TV
        if (TvLinkManager.saved.list().isNotEmpty()) {
            TvLinkStatus(link, onAdd = { adding = true }, onManage = { managing = true })
            if (reachable) info?.let { Text("${formatSize(it.free)} libres sur la TV", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant) }
        } else Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(if (reachable) Cb.success else cs.outline))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(tvName?.removePrefix("CastBridge TV ")?.ifBlank { "Ma TV" } ?: "Ma TV", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(if (reachable) "Connectée" + (info?.let { " · ${formatSize(it.free)} libres" } ?: "") else "Recherche de la TV… (même Wi-Fi, app CastBridge TV installée)",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            TextButton(onClick = { wizard = true }) { Text("Changer") }
        }

        // Big progress while sending
        val u = upload
        val bt by BtUploadService.state.collectAsState()
        val btRoute by BtUploadService.route.collectAsState()
        val b = bt
        val btActive = u !is UploadService.State.Uploading && u !is UploadService.State.Waiting &&
            (b is castbridge.core.tv.ResumableUpload.State.Uploading || b is castbridge.core.tv.ResumableUpload.State.Waiting)
        // Bluetooth has no speed meter of its own: measured from the progress since the transfer started (shown after 3 s).
        val btSent = (b as? castbridge.core.tv.ResumableUpload.State.Uploading)?.sent ?: (b as? castbridge.core.tv.ResumableUpload.State.Waiting)?.sent
        var btFrom by remember { mutableStateOf(0L to 0L) }
        LaunchedEffect(btActive) { if (btActive) btFrom = System.currentTimeMillis() to (btSent ?: 0L) }
        val btAvg = if (btActive && btSent != null) (System.currentTimeMillis() - btFrom.first).let { dt ->
            if (dt > 3_000 && btSent > btFrom.second) (btSent - btFrom.second) * 1000 / dt else 0L } else 0L
        val shownAvg = if (btActive) btAvg else avg
        AnimatedVisibility(u is UploadService.State.Uploading || u is UploadService.State.Waiting || btActive, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            val (sent, total, name, waiting) = when {
                u is UploadService.State.Uploading -> listOf(u.sent, u.total, u.job.fileName, null)
                u is UploadService.State.Waiting -> listOf(u.sent, u.total, u.job.fileName, u.reason)
                // sent by Bluetooth (no common Wi-Fi): same card, same percentage and time left
                b is castbridge.core.tv.ResumableUpload.State.Uploading -> listOf(b.sent, b.total, "Envoi par ${btRoute ?: "Bluetooth"}", null)
                b is castbridge.core.tv.ResumableUpload.State.Waiting -> listOf(b.sent, b.total, "Envoi par ${btRoute ?: "Bluetooth"}", b.reason)
                else -> listOf(0L, 1L, "", null)
            }
            sent as Long; total as Long
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Envoi vers la TV", style = MaterialTheme.typography.labelLarge, color = cs.primary)
                    Text(name as String, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${sent * 100 / total.coerceAtLeast(1)} %", fontSize = 44.sp, fontWeight = FontWeight.Bold)
                    LinearProgressIndicator({ sent.toFloat() / total.coerceAtLeast(1) }, Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)))
                    Text(if (waiting != null) "En pause : $waiting — reprise automatique" else listOf(eta(total - sent, shownAvg), if (shownAvg > 0) "${formatSize(shownAvg)}/s" else "").filter { it.isNotEmpty() }.joinToString("  ·  "),
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    TextButton(onClick = { if (btActive) BtUploadService.cancel(ctx) else UploadService.cancel(ctx) }) { Text("Annuler l'envoi") }
                }
            }
        }
        (u as? UploadService.State.Done)?.let { Text("« ${LibraryLogic.title(it.job.fileName)} » est sur la TV ✓", style = MaterialTheme.typography.bodyMedium, color = Cb.success) }
        (u as? UploadService.State.Failed)?.let { Text(it.reason, style = MaterialTheme.typography.bodyMedium, color = cs.error) }

        // Queue of the files waiting to be sent (several files, « Ouvrir avec », …): one at a time, cancel one by one.
        val queue by TransferQueue.items.collectAsState()
        val shownQueue = queue.filter { it.status == castbridge.core.tv.QueueStatus.WAITING || it.status == castbridge.core.tv.QueueStatus.RUNNING || it.status == castbridge.core.tv.QueueStatus.FAILED }
        AnimatedVisibility(shownQueue.isNotEmpty()) {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val waiting = shownQueue.count { it.status == castbridge.core.tv.QueueStatus.WAITING }
                    Text("File d'attente des envois" + if (waiting > 0) " · $waiting en attente" else "", style = MaterialTheme.typography.labelLarge, color = cs.primary)
                    shownQueue.forEach { q ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(LibraryLogic.title(q.name), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                Text(when (q.status) {
                                    castbridge.core.tv.QueueStatus.RUNNING -> "En cours" + if (q.move) " (déplacement)" else ""
                                    castbridge.core.tv.QueueStatus.WAITING -> "En attente" + if (q.move) " (déplacement)" else "" + if (q.size > 0) " · ${formatSize(q.size)}" else ""
                                    else -> "Échec : ${q.error ?: "envoi interrompu"}"
                                }, style = MaterialTheme.typography.bodySmall, color = if (q.status == castbridge.core.tv.QueueStatus.FAILED) cs.error else cs.onSurfaceVariant)
                            }
                            if (q.status != castbridge.core.tv.QueueStatus.FAILED) TextButton(onClick = { TransferQueue.cancel(ctx, q.id) }) { Text("Annuler") }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (waiting > 1) TextButton(onClick = { TransferQueue.cancelWaiting() }) { Text("Annuler les envois en attente") }
                        if (shownQueue.any { it.status == castbridge.core.tv.QueueStatus.FAILED }) TextButton(onClick = { TransferQueue.clearFinished() }) { Text("Effacer les échecs") }
                    }
                }
            }
        }

        // Series in « Titre / Saison » folders (virtual: nothing moves), automatic after each send, or on demand with a confirmation and an undo
        var autoClass by remember { mutableStateOf(SeriesClassifying.auto(ctx)) }
        var confirmPlan by remember { mutableStateOf<List<castbridge.core.library.agent.SeriesClassifier.Move>?>(null) }
        var undoable by remember { mutableStateOf(SeriesClassifying.last.isNotEmpty()) }
        val toClassify = remember(items) { SeriesClassifying.preview(items.map { it.name to it.folder }) }
        if (client != null && items.isNotEmpty()) ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Classer les séries par titre et saison", style = MaterialTheme.typography.titleSmall)
                        Text("Ex. Prison Break › Saison 01. Aucun fichier n'est déplacé ni copié.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                    Switch(autoClass, { autoClass = it; SeriesClassifying.setAuto(ctx, it) })
                }
                Text(if (autoClass) "Automatique à la fin de chaque envoi." else "Automatique : désactivé.", style = MaterialTheme.typography.bodySmall)
                if (toClassify.isNotEmpty()) {
                    val folders = toClassify.map { it.folder }.toSet()
                    Text("${toClassify.size} épisode(s) en vrac à ranger dans ${folders.size} dossier(s).", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { confirmPlan = toClassify }) { Text("Classer maintenant") }
                }
                if (undoable) TextButton(onClick = {
                    scope.launch {
                        val n = withContext(Dispatchers.IO) { SeriesClassifying.undoLast(client) }
                        undoable = false; msg = "$n fichier(s) remis à la racine."
                        withContext(Dispatchers.IO) { runCatching { items = TvLibraryParser.parse(client.library()) } }
                    }
                }) { Text("Annuler le dernier classement") }
            }
        }
        confirmPlan?.let { plan ->
            AlertDialog(
                onDismissRequest = { confirmPlan = null },
                title = { Text("Classer ${plan.size} épisode(s) ?") },
                text = { Column {
                    plan.groupBy { it.folder }.entries.take(8).forEach { (f, l) -> Text("• $f : ${l.size}", style = MaterialTheme.typography.bodyMedium) }
                    Text("Seuls les fichiers à la racine sont classés ; vos dossiers existants ne sont pas touchés. Annulable.", style = MaterialTheme.typography.bodySmall)
                } },
                confirmButton = { TextButton(onClick = {
                    confirmPlan = null
                    scope.launch {
                        val (ok, ko) = withContext(Dispatchers.IO) { SeriesClassifying.apply(client!!, plan) }
                        undoable = ok > 0
                        msg = if (ko == 0) "$ok épisode(s) classé(s)." else "$ok classé(s), $ko échec(s) : réessayez."
                        withContext(Dispatchers.IO) { runCatching { items = TvLibraryParser.parse(client!!.library()) } }
                    }
                }) { Text("Classer") } },
                dismissButton = { TextButton(onClick = { confirmPlan = null }) { Text("Annuler") } },
            )
        }

        // Now playing
        val now = info?.takeIf { it.playing != null && it.state != "idle" }
        AnimatedVisibility(now != null) {
            now?.let { n ->
                Card(Modifier.fillMaxWidth().clickable { showPlayer = true }, colors = CardDefaults.cardColors(containerColor = cs.primaryContainer)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Tv, null, tint = cs.primary)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text("Sur la TV", style = MaterialTheme.typography.labelMedium, color = cs.primary)
                            Text(LibraryLogic.title(n.playing!!), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                            if (n.dur > 0) LinearProgressIndicator({ n.pos.toFloat() / n.dur }, Modifier.fillMaxWidth().padding(top = 6.dp))
                        }
                        IconButton({ RemoteActivity.open(ctx) }) { Icon(cbv(R.drawable.ic_cb_telecommande), "Télécommande") }
                        IconButton({ cmd { if (n.state == "playing") pause() else resume() } }) { Icon(if (n.state == "playing") cbv(R.drawable.ic_cb_pause) else cbv(R.drawable.ic_cb_lecture), "Lecture / pause") }
                    }
                }
            }
        }

        // Tasks
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Task(cbv(R.drawable.ic_cb_envoyer), "Envoyer une vidéo", "Copiée : elle reste aussi sur le téléphone", Modifier.weight(1f)) { PhoneConnect.feature("send"); moveNext = false; pick.launch(arrayOf("video/*", "audio/*")) }
            Task(cbv(R.drawable.ic_cb_deplacer_vers_tv), "Déplacer vers la TV", "Libère la place du téléphone", Modifier.weight(1f)) { PhoneConnect.feature("move"); moveNext = true; pick.launch(arrayOf("video/*", "audio/*")) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Task(Icons.Filled.PlayCircle, "Regarder sur la TV", if (now != null) "Lecture en cours" else "Choisir une vidéo", Modifier.weight(1f)) {
                PhoneConnect.feature("watch_on_tv")
                if (now != null) showPlayer = true else showLibrary = true
            }
            Task(cbv(R.drawable.ic_cb_telecommande), "Télécommande", "Flèches, OK, volume, clavier", Modifier.weight(1f)) { PhoneConnect.feature("remote"); RemoteActivity.open(ctx) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Task(cbv(R.drawable.ic_cb_bibliotheque), "Bibliothèque de la TV", "${items.size} fichier(s)", Modifier.weight(1f)) { PhoneConnect.feature("tv_library"); showLibrary = true }
            Task(Icons.Filled.SwapVert, "Échanger des fichiers", "Dans les deux sens", Modifier.weight(1f)) { PhoneConnect.feature("file_exchange"); showExchange = true }
        }

        // Continue watching
        val resume = LibrarySections.build(items).firstOrNull { it.id == LibrarySections.RESUME }?.items.orEmpty()
            .ifEmpty { LibraryLogic.sortNewestFirst(items.filter { it.type != castbridge.core.tv.MediaType.OTHER }, { it.mtime }, { it.name }).take(8) }
        if (resume.isNotEmpty() && client != null) {
            Text(if (resume.any { it.resumeMs > 0 && !it.watched }) "Reprendre sur la TV" else "Récemment ajoutés", style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(resume, key = { "${it.volume}:${it.name}" }) { i -> Poster(client, i) { cmd { play(i.name, if (i.watched) 0 else i.resumeMs) } } }
            }
        }
        msg?.let { Text(it, color = cs.error, style = MaterialTheme.typography.bodySmall) }

        // Advanced
        HorizontalDivider()
        ListItem(modifier = Modifier.clickable(onClick = onAdvanced),
            leadingContent = { Icon(cbv(R.drawable.ic_cb_reglages), null) },
            headlineContent = { Text("Avancé") },
            supportingContent = { Text("Adresse manuelle, Bluetooth, Wi-Fi Direct, passerelle SSH, stockage de la TV, installation d'applications") },
            trailingContent = { Icon(Icons.Filled.ChevronRight, null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent))
    }

    if (showLibrary && client != null) TvLibraryDialog(client, onDismiss = { showLibrary = false },
        onDownload = { i -> DownloadService.start(ctx, client.base, client.pin, i.name, i.size); showLibrary = false; showExchange = true })
    if (showExchange && client != null) TvTransferDialog(client, onDismiss = { showExchange = false })
    val n = info
    val playing = n?.playing
    if (showPlayer && client != null && n != null && playing != null) {
        NowPlayingSheet(LibraryLogic.title(playing), "Sur la TV", n.state == "playing" || n.state == "buffering", n.pos, n.dur,
            onSeek = { ms -> cmd { seek(ms) } }, onToggle = { cmd { if (n.state == "playing") pause() else resume() } },
            onSkip = { d -> cmd { seek((n.pos + d * 1000L).coerceAtLeast(0)) } },
            onStop = { cmd { stop() }; showPlayer = false }, onDismiss = { showPlayer = false }, onSettings = { showSettings = true },
            onRemote = { RemoteActivity.open(ctx) })
    }
    if (showSettings && client != null) TvPlayerSettingsSheet(client) { showSettings = false }
}

@Composable
private fun Task(icon: ImageVector, title: String, sub: String, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    ElevatedCard(modifier.clickable(onClick = onClick), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(18.dp).fillMaxWidth()) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surface))),
                contentAlignment = Alignment.Center) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(30.dp)) }
            Spacer(Modifier.height(14.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun Poster(client: TvClient, i: TvLibItem, onClick: () -> Unit) {
    val k = TvThumbs.key(client.base, i)
    var img by remember(k) { mutableStateOf(TvThumbs.cached(k)) }
    LaunchedEffect(k, i.hasThumb) { if (img == null) img = TvThumbs.load(client, i) }
    Column(Modifier.width(168.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            img?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } ?: Icon(Icons.Filled.Movie, null)
            Icon(Icons.Filled.PlayCircle, null, Modifier.size(40.dp), tint = Color.White.copy(alpha = 0.9f))
            if (i.resumeMs > 0 && !i.watched && i.durationMs > 0)
                LinearProgressIndicator({ LibraryLogic.progress(i.resumeMs, i.durationMs) }, Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomCenter))
        }
        Text(i.title, Modifier.padding(6.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
    }
}

/** First connection: find the TV (same Wi-Fi), then type its code once. */
@Composable
private fun FirstConnection(tvs: List<Tv>, onRetry: () -> Unit, onAdvanced: () -> Unit, onAddTv: () -> Unit, onDone: (String, String) -> Unit) {
    val scope = rememberCoroutineScope()
    var chosen by remember { mutableStateOf<Tv?>(null) }
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.Tv, null, Modifier.size(72.dp), tint = cs.primary)
        val c = chosen
        if (c == null) {
            Text("Trouvons votre TV", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Button(onClick = onAddTv, modifier = Modifier.fillMaxWidth(0.9f)) { Icon(Icons.Filled.Bluetooth, null); Spacer(Modifier.width(8.dp)); Text("Ajouter ma TV (Bluetooth, sans code)") }
            Text("La façon la plus simple : pas d'adresse, pas de code à saisir. Sinon, avec le code de la TV :", textAlign = TextAlign.Center, color = cs.onSurfaceVariant)
            Text("Ouvrez l'app CastBridge TV sur la TV. Le téléphone et la TV doivent être sur le même Wi-Fi.", textAlign = TextAlign.Center, color = cs.onSurfaceVariant)
            if (tvs.isEmpty()) { CircularProgressIndicator(); Text("Recherche…", color = cs.onSurfaceVariant) }
            tvs.forEach { t ->
                ElevatedCard(Modifier.fillMaxWidth().clickable { chosen = t; error = null }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Tv, null, tint = cs.primary); Spacer(Modifier.width(12.dp))
                        Text(t.name.removePrefix("CastBridge TV ").ifBlank { t.name }, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Icon(Icons.Filled.ChevronRight, null)
                    }
                }
            }
            TextButton(onClick = onRetry) { Text("Chercher à nouveau") }
            TextButton(onClick = onAdvanced) { Text("Ma TV n'apparaît pas (adresse manuelle, Bluetooth, sans box)") }
        } else {
            Text("Saisissez le code de la TV", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("Il est affiché sur la TV : écran d'accueil, en haut (touche OK sur « code » pour l'afficher en entier), ou MENU > Connexion & réglages. " +
                "Vous ne le saisirez qu'une fois.", textAlign = TextAlign.Center, color = cs.onSurfaceVariant)
            OutlinedTextField(code, { v -> code = v.filter { it.isDigit() }.take(Pin.LENGTH); error = null }, singleLine = true,
                textStyle = MaterialTheme.typography.headlineMedium.copy(textAlign = TextAlign.Center, letterSpacing = 8.sp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.width(240.dp), isError = error != null)
            error?.let { Text(it, color = cs.error) }
            Button(enabled = Pin.isValidFormat(code) && !checking, onClick = {
                checking = true
                scope.launch {
                    val r = withContext(Dispatchers.IO) { runCatching { TvClient(c.base, code).info() } }
                    checking = false
                    r.onSuccess { onDone(c.name, code) }.onFailure { e ->
                        error = if ((e as? TvClient.HttpError)?.code == 401) (if ("locked" in e.message.orEmpty()) "Trop d'essais : attendez une minute" else "Code incorrect")
                            else "TV injoignable : ${e.message}"
                    }
                }
            }, modifier = Modifier.fillMaxWidth(0.7f)) { if (checking) CircularProgressIndicator(Modifier.size(18.dp)) else Text("Se connecter") }
            TextButton(onClick = { chosen = null }) { Text("Choisir une autre TV") }
        }
    }
}
