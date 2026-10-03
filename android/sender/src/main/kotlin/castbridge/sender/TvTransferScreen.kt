package castbridge.sender

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import castbridge.core.tv.ResumableDownload
import castbridge.core.tv.TvClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private data class Picked(val uri: Uri, val name: String, val size: Long)

private fun rate(bps: Long) = if (bps <= 0) "-" else "${formatSize(bps)}/s"
private fun eta(left: Long, bps: Long): String {
    if (bps <= 0 || left <= 0) return ""
    val s = left / bps
    return " · reste ~" + if (s >= 3600) "${s / 3600} h ${s / 60 % 60} min" else if (s >= 60) "${s / 60} min ${s % 60} s" else "$s s"
}

/**
 * "Échange de fichiers": both directions at full speed. Phone -> TV: any file (videos, documents, APK), destination volume
 * chosen per transfer, the TV's pre-flight says how much will stay free afterwards (1 GB minimum, else a precise refusal
 * before any byte). TV -> phone: download to Téléchargements/CastBridge or a chosen folder, resumable. Instant and average speed.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TvTransferDialog(client: TvClient, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var storage by remember { mutableStateOf<TvStorage?>(null) }
    var tvFiles by remember { mutableStateOf<List<TvLibItem>>(emptyList()) }
    var dest by remember { mutableStateOf("auto") }
    var picked by remember { mutableStateOf<List<Picked>>(emptyList()) }
    var checks by remember { mutableStateOf<Map<String, TvClient.StorageCheck?>>(emptyMap()) }
    var msg by remember { mutableStateOf<String?>(null) }
    var tree by remember { mutableStateOf<String?>(null) }
    val upload by UploadService.state.collectAsState()
    val speed by UploadService.speed.collectAsState()
    val average by UploadService.average.collectAsState()
    val dl by DownloadService.progress.collectAsState()
    val host = client.base.removePrefix("http://")

    LaunchedEffect(client) {
        while (true) {
            withContext(Dispatchers.IO) {
                runCatching { storage = TvStorageParser.parse(client.storage()) }
                runCatching { tvFiles = TvLibraryParser.parse(client.library()) }
            }
            delay(5000)
        }
    }
    // Pre-flight of every picked file against the chosen destination (nothing is sent yet).
    LaunchedEffect(picked, dest) {
        checks = picked.associate { it.name to null }
        checks = withContext(Dispatchers.IO) { picked.associate { p -> p.name to runCatching { client.checkStorage(p.name, p.size, volume = dest) }.getOrNull() } }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        picked = uris.map { u ->
            runCatching { ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            var name = u.lastPathSegment ?: "fichier"; var size = 0L
            runCatching {
                ctx.contentResolver.query(u, null, null, null, null)?.use { c ->
                    if (c.moveToFirst()) { name = c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) ?: name; size = c.getLong(c.getColumnIndexOrThrow(OpenableColumns.SIZE)) }
                }
            }
            Picked(u, name, size)
        }
        msg = null
    }
    val pickTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { u ->
        if (u != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            tree = u.toString()
        }
    }
    // R-09: one file after the other through the transfer queue (it used to be a chain held by this dialog: a failure stopped every file behind it,
    // closing the dialog stopped the chain, and a file started while another upload ran was dropped in silence)
    fun startAll() {
        var n = 0; var last: TransferQueue.Ticket? = null
        picked.forEach { p ->
            runCatching { last = TransferQueue.add(ctx, p.uri, p.name, p.size, move = false, tvName = host, credential = client.pin, host = host, target = dest); n++ }
                .onFailure { msg = "Impossible de mettre « ${p.name} » en file : ${it.message}" }
        }
        if (n > 1) msg = "$n fichiers ajoutés à la file d'attente : ils partent l'un après l'autre (suivi ci-dessous et dans la notification)."
        else if (n == 1) msg = last?.takeIf { it.queued }?.text ?: "Envoi en cours."
        picked = emptyList()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column {
                TopAppBar(title = { Text("Échange de fichiers") },
                    navigationIcon = { IconButton(onDismiss) { Icon(Icons.Filled.ArrowBack, "Retour") } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface))
                Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // ------------------------------------------------ phone -> TV
                    Text("Du téléphone vers la TV", style = MaterialTheme.typography.titleMedium)
                    Text("Tout type de fichier (vidéos, documents, APK : ils apparaissent dans « Autres fichiers » de la bibliothèque). Envoi en flux continu " +
                        "à pleine vitesse, repris après une coupure. La TV exige qu'il reste au moins 1 Go libre sur le volume à la fin du transfert.",
                        style = MaterialTheme.typography.bodySmall)
                    Text("Destination sur la TV", style = MaterialTheme.typography.labelLarge)
                    val vols = storage?.volumes.orEmpty().filter { it.present }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(dest == "auto", { dest = "auto" }, { Text("Automatique") })
                        vols.filter { it.writable }.forEach { v ->
                            FilterChip(dest == v.id, { dest = v.id }, { Text("${v.label} · ${if (v.free >= 0) formatSize(v.free) else "?"} libres") })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { pick.launch(arrayOf("*/*")) }) { Icon(Icons.Filled.AttachFile, null); Spacer(Modifier.width(6.dp)); Text("Choisir des fichiers") }
                        val allOk = picked.isNotEmpty() && picked.all { checks[it.name]?.ok == true }
                        Button(enabled = allOk, onClick = { startAll() }) { Icon(cbv(R.drawable.ic_cb_envoyer), null); Spacer(Modifier.width(6.dp)); Text("Envoyer") }
                    }
                    picked.forEach { p ->
                        val c = checks[p.name]
                        Text("• ${p.name} (${formatSize(p.size)})", style = MaterialTheme.typography.bodyMedium)
                        when {
                            c == null -> Text("  vérification de l'espace…", style = MaterialTheme.typography.bodySmall)
                            c.ok -> Text("  -> ${c.label ?: c.volume} : il restera ${if (c.freeAfter >= 0) formatSize(c.freeAfter) else "?"} libres après le transfert" +
                                (if (c.warnings.isNotEmpty()) "\n  " + c.warnings.joinToString("\n  ") else ""), style = MaterialTheme.typography.bodySmall)
                            else -> Text("  ${c.message}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        if (c != null && c.options.size > 1) Text(c.options.joinToString("   ") { o ->
                            "${o.label} : ${if (o.freeAfter >= 0) formatSize(o.freeAfter) else "?"} après" + if (o.ok) "" else " (non)" },
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    when (val u = upload) {
                        is UploadService.State.Uploading -> run {
                            LinearProgressIndicator({ u.sent.toFloat() / u.total.coerceAtLeast(1) }, Modifier.fillMaxWidth())
                            Text("${u.job.fileName} : ${formatSize(u.sent)} / ${formatSize(u.total)} · ${rate(speed)} (moyenne ${rate(average)})${eta(u.total - u.sent, average)}",
                                style = MaterialTheme.typography.bodySmall)
                            val destVol = vols.firstOrNull { it.id == UploadService.check.value?.volume }
                            if (destVol != null && destVol.writeBps in 1..(average * 12 / 10 + 1)) Text("Débit limité par l'écriture de ${destVol.label} (~${formatSize(destVol.writeBps)}/s mesurés).",
                                style = MaterialTheme.typography.labelSmall)
                        }
                        is UploadService.State.Waiting -> Text("En attente, reprise automatique à ${formatSize(u.sent)} (${u.reason})", style = MaterialTheme.typography.bodySmall)
                        else -> {}
                    }
                    msg?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    TransferQueueCard()

                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    // ------------------------------------------------ TV -> phone
                    Text("De la TV vers le téléphone", style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(tree == null, { tree = null }, { Text("Téléchargements/CastBridge") })
                        FilterChip(tree != null, { pickTree.launch(null) }, { Text(if (tree != null) "Dossier choisi" else "Choisir un dossier…") })
                    }
                    val d = dl
                    d.job?.let { j ->
                        when (val s = d.state) {
                            is ResumableDownload.State.Downloading -> {
                                LinearProgressIndicator({ if (s.total > 0) s.got.toFloat() / s.total else 0f }, Modifier.fillMaxWidth())
                                Text("${j.name} : ${formatSize(s.got)} / ${if (s.total > 0) formatSize(s.total) else "?"} · ${rate(d.speed)} (moyenne ${rate(d.average)})${eta(s.total - s.got, d.average)}",
                                    style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { DownloadService.cancel(ctx) }) { Text("Annuler") }
                            }
                            is ResumableDownload.State.Waiting -> Text("${j.name} : en attente de la TV, reprise à ${formatSize(s.got)} (${s.reason})", style = MaterialTheme.typography.bodySmall)
                            is ResumableDownload.State.Done -> Text("${j.name} enregistré : ${d.savedAs ?: ""}", style = MaterialTheme.typography.bodySmall)
                            is ResumableDownload.State.Failed -> {
                                Text("${j.name} : ${s.reason}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                TextButton(onClick = { DownloadService.start(ctx, client.base, client.pin, j.name, j.size, j.tree) }) { Text("Reprendre") }
                            }
                            null -> Text("${j.name} : démarrage…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (tvFiles.isEmpty()) Text("Aucun fichier sur la TV.", style = MaterialTheme.typography.bodySmall)
                    tvFiles.forEach { f ->
                        ListItem(headlineContent = { Text(f.name, maxLines = 2) },
                            supportingContent = { Text("${formatSize(f.size)} · ${f.volumeLabel}") },
                            trailingContent = {
                                IconButton(enabled = d.state !is ResumableDownload.State.Downloading, onClick = { DownloadService.start(ctx, client.base, client.pin, f.name, f.size, tree) }) {
                                    Icon(cbv(R.drawable.ic_cb_telechargements), "Télécharger")
                                }
                            })
                    }
                    Text("Les téléchargements reprennent là où ils se sont arrêtés (même fichier, même dossier). Les envois vers la TV sont faits en une seule connexion : " +
                        "sur cette TV, l'écriture de la clé USB (~2 Mo/s mesurés) limite en général le débit avant le Wi-Fi.", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
