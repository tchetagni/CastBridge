package castbridge.sender

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import castbridge.core.dl.DownloadsClient
import castbridge.core.dl.LinkParser
import castbridge.core.dl.Source
import castbridge.core.tv.TvClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The TV the user last managed, so that a link shared from the browser goes there without asking again. */
class DlPrefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("castbridge_downloads", Context.MODE_PRIVATE)
    fun remember(base: String, pin: String?) = sp.edit().putString("base", base).putString("pin", pin).apply()
    val base: String? get() = sp.getString("base", null)
    val pin: String? get() = sp.getString("pin", null)
}

/** Entry shown in the CastBridge TV panel (once the PIN is accepted): opens the downloads screen. */
@Composable
fun DownloadsEntry(client: TvClient) {
    val ctx = LocalContext.current
    var open by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(client.base, client.pin) { DlPrefs(ctx).remember(client.base, client.pin) }
    HorizontalDivider()
    ListItem(
        modifier = Modifier.clickable { open = true },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        leadingContent = { Icon(Icons.Filled.Download, null, tint = MaterialTheme.colorScheme.primary) },
        headlineContent = { Text("Téléchargements sur la TV") },
        supportingContent = { Text("La TV télécharge elle-même un lien ou un torrent, même téléphone éteint.") },
        trailingContent = { Icon(Icons.Filled.ChevronRight, null) },
    )
    if (open) Dialog(onDismissRequest = { open = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            DownloadsScreen(DownloadsClient(client.base, client.pin), onClose = { open = false })
        }
    }
}

private fun human(b: Long): String = when {
    b >= 1L shl 30 -> "%.1f Go".format(b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> "%.0f Mo".format(b / (1L shl 20).toDouble())
    else -> "${b / 1024} ko"
}

private fun speed(b: Long) = if (b >= 1L shl 20) "%.1f Mo/s".format(b / (1L shl 20).toDouble()) else "${b / 1024} ko/s"

private fun eta(s: Long) = when {
    s < 0 -> ""
    s >= 3600 -> "${s / 3600} h ${s % 3600 / 60} min"
    s >= 60 -> "${s / 60} min"
    else -> "$s s"
}

/** What was pasted, in words: shown under the field before sending. */
fun describeLink(text: String): Pair<Boolean, String> = when (val r = LinkParser.parse(text)) {
    is LinkParser.Result.Bad -> false to r.message
    is LinkParser.Result.Ok -> when (val s = r.source) {
        is Source.Magnet -> true to ("Torrent" + (s.name?.let { " : $it" } ?: "") + (s.size?.let { " (${human(it)})" } ?: ""))
        is Source.Url -> true to ("Fichier : " + (LinkParser.nameFromUrl(s.uris[0]) ?: LinkParser.display(s.uris[0])))
        else -> true to "Lien reconnu"
    }
}

/** Full downloads screen: add a link or a .torrent, follow progress, act on each download, watch finished ones on the TV. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(dc: DownloadsClient, onClose: () -> Unit, initialLink: String? = null) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val cs = MaterialTheme.colorScheme
    var state by remember { mutableStateOf<DownloadsClient.State?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var link by rememberSaveable { mutableStateOf(initialLink.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    var showAbout by remember { mutableStateOf<String?>(null) }
    var filesOf by remember { mutableStateOf<DownloadsClient.Task?>(null) }
    var limitOf by remember { mutableStateOf<DownloadsClient.Task?>(null) }
    var removeOf by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    fun run(block: suspend () -> Unit) = scope.launch {
        busy = true
        try { block(); tick++ } catch (e: DownloadsClient.Refused) { message = e.message } catch (e: Exception) { message = "TV injoignable : ${e.message}" }
        busy = false
    }
    fun io(f: () -> Unit) = run { withContext(Dispatchers.IO) { f() } }

    LaunchedEffect(dc.base, tick) {
        while (true) {
            val r = withContext(Dispatchers.IO) { runCatching { dc.state() } }
            r.onSuccess { state = it; error = null }.onFailure { error = (it as? DownloadsClient.Refused)?.message ?: "TV injoignable (${it.message})" }
            if ((r.exceptionOrNull() as? DownloadsClient.Refused)?.http == 401) break      // never hammer the TV's PIN lockout
            delay(2000)
        }
    }

    val torrentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) run {
            val (name, bytes) = withContext(Dispatchers.IO) {
                val n = runCatching { ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null } }.getOrNull() ?: "fichier.torrent"
                n to ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            }
            if (bytes.size > 4 shl 20) { message = "Fichier trop gros pour un .torrent."; return@run }
            val note = withContext(Dispatchers.IO) { dc.upload(name, bytes) }
            message = note ?: "Envoyé à la TV : le téléchargement commence."
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("Téléchargements sur la TV")
                    state?.takeIf { it.down > 0 }?.let { Text("↓ ${speed(it.down)}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant) }
                }
            },
            navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
            actions = {
                IconButton(onClick = { showSettings = true }) { Icon(Icons.Filled.Speed, "Réglages") }
                IconButton(onClick = { run { showAbout = withContext(Dispatchers.IO) { dc.about() } } }) { Icon(Icons.Filled.Info, "À propos") }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.surface),
        )
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            error?.let { e -> item { Text(e, color = cs.error) } }
            val s = state
            if (s != null && !s.engineAvailable) item {
                Card(colors = CardDefaults.cardColors(containerColor = cs.surfaceVariant)) {
                    Text("Le moteur de téléchargement n'est pas inclus dans cette version de CastBridge TV. Installez une version qui le contient (voir docs/DOWNLOADS.md).",
                        Modifier.padding(16.dp))
                }
            } else if (s != null && s.engineMessage.isNotEmpty()) item { Text(s.engineMessage, color = cs.secondary) }

            if (s != null && !s.warningAccepted) item {
                Card(colors = CardDefaults.cardColors(containerColor = cs.primaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Avant de commencer", fontWeight = FontWeight.Bold)
                        Text(s.warning)
                        Button(onClick = { io { dc.accept() } }) { Text("J'ai compris") }
                    }
                }
            }

            // ---- add ----
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(link, { link = it }, Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text("Lien à télécharger") }, placeholder = { Text("https://…  ou  magnet:?…") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        trailingIcon = {
                            IconButton(onClick = { clipboard.getText()?.text?.let { t -> link = DownloadsClient.findLink(t) ?: t.trim() } }) {
                                Icon(Icons.Filled.ContentPaste, "Coller")
                            }
                        })
                    if (link.isNotBlank()) {
                        val (ok, what) = describeLink(link)
                        Text(what, style = MaterialTheme.typography.bodySmall, color = if (ok) cs.onSurfaceVariant else cs.error)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = !busy && link.isNotBlank() && describeLink(link).first && s?.warningAccepted == true, onClick = {
                            val l = link.trim()
                            run {
                                val note = withContext(Dispatchers.IO) { dc.add(l) }
                                link = ""; message = note ?: "C'est parti : la TV télécharge."
                            }
                        }) { Icon(Icons.Filled.Download, null); Spacer(Modifier.width(8.dp)); Text("Télécharger sur la TV") }
                        OutlinedButton(enabled = !busy && s?.warningAccepted == true,
                            onClick = { torrentPicker.launch(arrayOf("application/x-bittorrent", "application/metalink4+xml", "application/octet-stream", "*/*")) }) {
                            Text(".torrent")
                        }
                    }
                    message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = cs.secondary) }
                }
            }

            // ---- in progress ----
            if (s != null) {
                if (s.tasks.isEmpty()) item { Text("Rien en cours.", color = cs.onSurfaceVariant) }
                else item { SectionHeader("En cours") }
                items(s.tasks, key = { it.id }) { t ->
                    TaskCard(t,
                        onPause = { io { dc.pause(t.id) } }, onResume = { io { dc.resume(t.id) } },
                        onRemove = { removeOf = t.id to t.name }, onFiles = { filesOf = t }, onTop = { io { dc.priority(t.id, "top") } },
                        onLimit = { limitOf = t })
                }
                if (s.done.isNotEmpty()) item { SectionHeader("Terminés") }
                items(s.done, key = { "done-" + it.id }) { d ->
                    Card(colors = CardDefaults.cardColors(containerColor = cs.surface)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(d.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("${human(d.size)} · ${d.volumeLabel}" + if (d.files.isEmpty()) " · aucune vidéo" else "",
                                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                d.files.firstOrNull()?.let { f ->
                                    Button(onClick = { run { withContext(Dispatchers.IO) { dc.play(f) }; message = "Lecture lancée sur la TV." } }) {
                                        Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Regarder sur la TV")
                                    }
                                }
                                TextButton(onClick = { removeOf = d.id to d.name }) { Text("Supprimer") }
                            }
                        }
                    }
                }
            }
        }
    }

    removeOf?.let { (id, name) ->
        AlertDialog(onDismissRequest = { removeOf = null }, title = { Text("Supprimer « $name » ?") },
            text = { Text("Vous pouvez garder ce qui est déjà sur la TV, ou tout effacer.") },
            confirmButton = { TextButton(onClick = { removeOf = null; io { dc.remove(id, true) } }) { Text("Tout effacer") } },
            dismissButton = { TextButton(onClick = { removeOf = null; io { dc.remove(id, false) } }) { Text("Retirer de la liste") } })
    }
    showAbout?.let { txt ->
        AlertDialog(onDismissRequest = { showAbout = null }, title = { Text("À propos") },
            text = { Text(txt, Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { showAbout = null }) { Text("Fermer") } })
    }
    filesOf?.let { t -> FilesDialog(dc, t, onDone = { filesOf = null; tick++ }) }
    limitOf?.let { t ->
        val choices = listOf(0L to "Sans limite", (512L shl 10) to "512 ko/s", (1L shl 20) to "1 Mo/s", (3L shl 20) to "3 Mo/s")
        AlertDialog(onDismissRequest = { limitOf = null }, title = { Text("Vitesse de « ${t.name} »") },
            text = {
                Column { choices.forEach { (v, label) ->
                    TextButton(onClick = { limitOf = null; io { dc.limit(t.id, v) } }, Modifier.fillMaxWidth()) { Text(label) }
                } }
            },
            confirmButton = { TextButton(onClick = { limitOf = null }) { Text("Fermer") } })
    }
    if (showSettings) state?.let { s -> SettingsDialog(s, onDismiss = { showSettings = false }) { down, seeding ->
        showSettings = false; io { dc.settings(downLimit = down, seeding = seeding) } } }
}

@Composable
private fun TaskCard(t: DownloadsClient.Task, onPause: () -> Unit, onResume: () -> Unit, onRemove: () -> Unit, onFiles: () -> Unit, onTop: () -> Unit,
                     onLimit: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val bad = t.state in setOf("error", "waiting_space", "waiting_drive")
    Card(colors = CardDefaults.cardColors(containerColor = cs.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (t.kind == "magnet" || t.kind == "torrent") Icons.Filled.Hub else Icons.Filled.Link, null, tint = cs.primary)
                Spacer(Modifier.width(8.dp))
                Text(t.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (t.total > 0) LinearProgressIndicator({ (t.done.toFloat() / t.total).coerceIn(0f, 1f) }, Modifier.fillMaxWidth())
            else if (t.state == "downloading" || t.state == "connecting" || t.state == "metadata") LinearProgressIndicator(Modifier.fillMaxWidth())
            val parts = buildList {
                if (t.total > 0) add("${human(t.done)} sur ${human(t.total)}")
                if (t.down > 0) add(speed(t.down))
                eta(t.eta).takeIf { it.isNotEmpty() }?.let { add("reste $it") }
                if (t.connections > 0) add(if (t.kind == "magnet" || t.kind == "torrent") "${t.connections} sources" else "${t.connections} connexions")
            }
            Text(t.label + (if (parts.isNotEmpty()) " · " + parts.joinToString(" · ") else ""), style = MaterialTheme.typography.bodySmall,
                color = if (bad) cs.error else cs.onSurfaceVariant)
            t.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (bad) cs.error else cs.onSurfaceVariant) }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (t.canPause) IconButton(onClick = onPause) { Icon(Icons.Filled.Pause, "Pause") }
                if (t.canResume) IconButton(onClick = onResume) { Icon(if (t.state == "error") Icons.Filled.Refresh else Icons.Filled.PlayArrow, "Reprendre") }
                if (t.state == "queued" || t.state == "paused") IconButton(onClick = onTop) { Icon(Icons.Filled.VerticalAlignTop, "Passer en premier") }
                if (t.files > 1) TextButton(onClick = onFiles) { Text("Fichiers (${t.files})") }
                if (t.canPause) TextButton(onClick = onLimit) { Text("Vitesse") }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onRemove) { Icon(Icons.Filled.Delete, "Supprimer") }
            }
        }
    }
}

@Composable
private fun FilesDialog(dc: DownloadsClient, t: DownloadsClient.Task, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var files by remember { mutableStateOf<List<DownloadsClient.FileEntry>?>(null) }
    var chosen by remember { mutableStateOf(setOf<Int>()) }
    var err by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(t.id) {
        withContext(Dispatchers.IO) { runCatching { dc.files(t.id) } }
            .onSuccess { files = it; chosen = it.filter { f -> f.selected }.map { f -> f.index }.toSet() }
            .onFailure { err = it.message }
    }
    AlertDialog(onDismissRequest = onDone, title = { Text("Fichiers à télécharger") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                files?.forEach { f ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(f.index in chosen, { c -> chosen = if (c) chosen + f.index else chosen - f.index })
                        Text("${f.path} (${human(f.length)})", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = chosen.isNotEmpty(), onClick = {
                scope.launch { withContext(Dispatchers.IO) { runCatching { dc.select(t.id, chosen.sorted()) } }; onDone() }
            }) { Text("Valider") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Annuler") } })
}

@Composable
private fun SettingsDialog(s: DownloadsClient.State, onDismiss: () -> Unit, onSave: (Long, Boolean) -> Unit) {
    val choices = listOf(0L to "Sans limite", (1L shl 20) to "1 Mo/s", (3L shl 20) to "3 Mo/s", (5L shl 20) to "5 Mo/s", (10L shl 20) to "10 Mo/s")
    var down by remember { mutableStateOf(s.downLimit) }
    var seeding by remember { mutableStateOf(s.seeding) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Réglages des téléchargements") },
        text = {
            Column {
                Text("Vitesse maximale (pour laisser de la bande passante aux autres appareils)", style = MaterialTheme.typography.bodySmall)
                choices.forEach { (v, label) ->
                    Row(Modifier.fillMaxWidth().clickable { down = v }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(down == v, { down = v }); Text(label)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(seeding, { seeding = it }); Spacer(Modifier.width(8.dp))
                    Text("Continuer à partager un torrent terminé (jusqu'à avoir rendu autant que reçu, 2 h maximum)", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(down, seeding) }) { Text("Enregistrer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } })
}
