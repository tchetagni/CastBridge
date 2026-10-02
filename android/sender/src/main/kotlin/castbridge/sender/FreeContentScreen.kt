package castbridge.sender

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import castbridge.core.free.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * « Contenus libres (CC BY-SA) » : the PHONE downloads the public archive of the free contents (server `/api/v1/free-content`, resumable, checked with its sha256),
 * or builds it locally from the contents embedded in the app (offline). No activation, no pairing, no TV needed. Saved in Download/CastBridge.
 * Entry point: Réglages > « Contenus libres (CC BY-SA) ». Pure logic: castbridge.core.free (DownloadPlan, FreeContentInfo).
 */
class FreeContentActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CastTheme { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { FreeContentScreen(onClose = { finish() }) } } }
    }

    companion object { fun open(ctx: Context) = ctx.startActivity(Intent(ctx, FreeContentActivity::class.java)) }
}

private class Saved(val uri: Uri, val shown: String)

private object FreeSave {
    fun dateStamp() = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())

    /** Copies [src] to Download/CastBridge (MediaStore on API 29+, plain file before). */
    fun toDownloads(ctx: Context, src: File, name: String): Saved {
        if (Build.VERSION.SDK_INT >= 29) {
            val v = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name); put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/CastBridge"); put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v) ?: throw java.io.IOException("Impossible de créer le fichier dans Téléchargements.")
            try {
                ctx.contentResolver.openOutputStream(uri)!!.use { o -> FileInputStream(src).use { it.copyTo(o, 64 * 1024) } }
                ctx.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) { runCatching { ctx.contentResolver.delete(uri, null, null) }; throw e }
            val shown = ctx.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: name
            return Saved(uri, "Téléchargements/CastBridge/$shown")
        }
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "CastBridge")
        if (!(dir.isDirectory || dir.mkdirs())) throw java.io.IOException("Impossible de créer ${dir.path}.")
        var out = File(dir, name); var n = 1
        while (out.exists()) out = File(dir, name.removeSuffix(".zip") + "-${n++}.zip")
        src.copyTo(out)
        return Saved(FileProvider.getUriForFile(ctx, ctx.packageName + ".freeshare", out), "Download/CastBridge/${out.name}")
    }
}

private enum class Step { IDLE, ASKING, DOWNLOADING, BUILDING, DONE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreeContentScreen(onClose: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<FreeContentInfo?>(null) }
    var infoError by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(true) }
    var step by remember { mutableStateOf(Step.IDLE) }
    var done by remember { mutableStateOf(0L) }
    var total by remember { mutableStateOf(0L) }
    var message by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf<Saved?>(null) }
    var ask by remember { mutableStateOf(false) }
    val cancel = remember { AtomicBoolean(false) }

    fun transport() = HttpFreeTransport(PhoneConnect.state.baseUrl)
    fun workDir() = File(ctx.filesDir, "free-content").also { it.mkdirs() }
    fun metered() = (ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).isActiveNetworkMetered

    fun check() {
        checking = true; infoError = null
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { FreeContentInfo.parse(transport().info()) } }
            info = r.getOrNull(); infoError = r.exceptionOrNull()?.let { "Serveur injoignable ou réponse invalide (${it.message ?: "erreur"})." }; checking = false
        }
    }
    LaunchedEffect(Unit) { check() }

    fun download(i: FreeContentInfo) {
        cancel.set(false); message = null; done = 0; total = i.sizeBytes; step = Step.DOWNLOADING
        scope.launch {
            val out = withContext(Dispatchers.IO) {
                val part = File(workDir(), i.fileName + ".part")
                when (val r = DownloadPlan.run(transport(), i, part, { d, t -> done = d; total = t }, { cancel.get() })) {
                    is FreeOutcome.Done -> runCatching { FreeSave.toDownloads(ctx, r.file, i.fileName).also { r.file.delete() } }
                        .fold({ it to null }, { null to "Enregistrement impossible : ${it.message ?: "erreur"}." })
                    is FreeOutcome.Interrupted -> null to r.message
                    is FreeOutcome.Cancelled -> null to "Téléchargement annulé. Vous pourrez le reprendre : ${FreeSizes.format(r.bytes)} déjà reçus."
                    is FreeOutcome.Failed -> null to r.message
                }
            }
            saved = out.first; message = out.second; step = if (saved != null) Step.DONE else Step.IDLE
        }
    }

    fun exportLocal() {
        cancel.set(false); message = null; done = 0; total = 0; step = Step.BUILDING
        scope.launch {
            val out = withContext(Dispatchers.IO) {
                val tmp = File(workDir(), "local-export.zip.part")
                try {
                    val src = EmbeddedFreeSource()
                    val date = FreeSave.dateStamp()
                    val res = tmp.outputStream().buffered(64 * 1024).use { FreeContentExporter.export(src, it, src.families(), date = date, progress = { d, t -> done = d; total = t }, cancelled = { cancel.get() }) }
                    val s = FreeSave.toDownloads(ctx, tmp, FreeExportFiles.fileName(date))
                    s to res.skippedLine().takeIf { it.isNotEmpty() }
                } catch (e: FreeExportCancelled) { null to "Export annulé."
                } catch (e: Exception) { null to (e.message ?: "Export impossible.")
                } finally { tmp.delete() }
            }
            saved = out.first; message = out.second; step = if (saved != null) Step.DONE else Step.IDLE
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Contenus libres (CC BY-SA)") },
            navigationIcon = { IconButton(onClose) { Icon(Icons.Filled.ArrowBack, "Retour") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface))
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Ce sont les contenus de CastBridge placés sous licence ${FreeLicense.NAME} (cours, langues…). Vous pouvez les copier, les partager et les adapter, " +
                "à condition de citer les auteurs et de partager vos modifications sous la même licence. Aucune activation, aucun appairage et aucune TV ne sont nécessaires : " +
                "l'archive (un fichier ZIP) est enregistrée dans Téléchargements/CastBridge.", style = MaterialTheme.typography.bodyMedium)
            val i = info
            when {
                checking -> Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Vérification auprès du serveur…") }
                i != null && i.downloadable -> Text("Archive disponible : ${FreeSizes.format(i.sizeBytes)}" + (if (i.generatedAt.isNotBlank()) ", créée le ${i.generatedAt.take(10)}" else "") + ", licence ${i.licence}.", style = MaterialTheme.typography.bodyMedium)
                i != null -> Text("L'archive des contenus libres n'est pas encore publiée sur le serveur.", color = MaterialTheme.colorScheme.error)
                else -> Text(infoError.orEmpty(), color = MaterialTheme.colorScheme.error)
            }
            val busy = step == Step.DOWNLOADING || step == Step.BUILDING
            if (busy) {
                if (total > 0) LinearProgressIndicator(progress = { FreeSizes.percent(done, total) / 100f }, modifier = Modifier.fillMaxWidth()) else LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(if (total > 0) "${FreeSizes.percent(done, total)} % — ${FreeSizes.format(done)} sur ${FreeSizes.format(total)}" else "Préparation…", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { cancel.set(true) }) { Text("Annuler") }
            } else {
                Button(enabled = i != null && i.downloadable, modifier = Modifier.fillMaxWidth(), onClick = {
                    val inf = i ?: return@Button
                    if (FreeSizes.askBeforeMobile(inf.sizeBytes, metered())) ask = true else download(inf)
                }) { Text("Télécharger tous les contenus libres") }
                if (!checking && (i == null || !i.downloadable)) {
                    OutlinedButton(onClick = { check() }) { Text("Réessayer") }
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { exportLocal() }) { Text("Exporter les contenus libres inclus dans l'application") }
                    Text("Sans Internet : crée l'archive à partir des contenus libres déjà présents dans CastBridge.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            message?.let { Text(it, color = if (step == Step.DONE) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            saved?.takeIf { step == Step.DONE }?.let { s ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Archive enregistrée", style = MaterialTheme.typography.titleSmall)
                        Text(s.shown, style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { runCatching { ctx.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }) { Text("Ouvrir le dossier") }
                            OutlinedButton(onClick = {
                                val send = Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM, s.uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                runCatching { ctx.startActivity(Intent.createChooser(send, "Partager les contenus libres").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            }) { Text("Partager") }
                        }
                    }
                }
            }
            HorizontalDivider()
            Text("Licence ${FreeLicense.NAME} : ${FreeLicense.URL}. Le fichier ZIP contient le texte de la licence, la liste des auteurs (ATTRIBUTION.md) et l'empreinte de chaque fichier.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    val inf = info
    if (ask && inf != null) AlertDialog(onDismissRequest = { ask = false },
        title = { Text("Utiliser vos données mobiles ?") },
        text = { Text("Le téléchargement fait ${FreeSizes.format(inf.sizeBytes)} et vous êtes sur données mobiles. Il peut être repris s'il est interrompu. Continuer ?") },
        confirmButton = { TextButton({ ask = false; download(inf) }) { Text("Télécharger") } },
        dismissButton = { TextButton({ ask = false }) { Text("Plus tard (Wi-Fi)") } })
}

/** Entry point (Réglages): opens the « Contenus libres » screen. */
@Composable
fun FreeContentEntry(modifier: Modifier = Modifier) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    OutlinedButton(onClick = { FreeContentActivity.open(ctx) }, modifier = modifier) { Text("Contenus libres (CC BY-SA)") }
}
