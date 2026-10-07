@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package castbridge.sender.agent

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import castbridge.core.library.agent.*
import castbridge.core.library.agent.State as OpState
import castbridge.core.tv.TvClient
import java.text.DateFormat
import java.util.Date

private fun size(b: Long) = castbridge.core.library.agent.Text.size(b)

/**
 * « Ranger ma bibliothèque » : l'assistant de rangement (docs/LIBRARY-AGENT.md).
 * Lit, comprend, PROPOSE ; c'est l'utilisateur qui coche et valide. Aucune suppression sans confirmation explicite
 * (et jamais définitive : la « Corbeille CastBridge » garde les fichiers 30 jours).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryAssistantDialog(client: TvClient?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { AgentStore.init(ctx) }
    AgentStore.init(ctx)
    // the work lives in AssistantHost (process scope): closing the dialog or turning the phone does not stop an analysis
    val m = remember { AssistantHost.modelFor(ctx, client) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            m.pickedTree(uri)
        }
    }

    val title = when (m.step) {
        Step.TRASH -> "Corbeille CastBridge"; Step.HISTORY -> "Historique"; Step.SETTINGS -> "Réglages de l'assistant"; else -> "Ranger ma bibliothèque"
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true, dismissOnBackPress = false, dismissOnClickOutside = false)) {
        BackHandler { back(m, onDismiss) }
        // A full-screen Dialog is laid out for the whole display but its window starts below the status bar: the bottom would be
        // cut by that height (the bottom buttons of the plan disappeared). Keep that height free at the bottom.
        val statusBar = remember { ctx.resources.getIdentifier("status_bar_height", "dimen", "android").let { id -> if (id > 0) ctx.resources.getDimensionPixelSize(id) / ctx.resources.displayMetrics.density else 24f } }
        Surface(Modifier.fillMaxSize().padding(bottom = statusBar.dp), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton({ back(m, onDismiss) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour")
                        }
                    },
                    actions = {
                        if (m.step == Step.INTRO || m.step == Step.PLAN) {
                            IconButton({ m.refreshHistory(); m.step = Step.HISTORY }) { Icon(Icons.Filled.History, "Historique et annulation") }
                            IconButton({ m.step = Step.SETTINGS }) { Icon(Icons.Filled.Settings, "Réglages de l'assistant") }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface))
                if (m.step.wizard > 0) WizardSteps(m.step.wizard)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                when (m.step) {
                    Step.INTRO -> Intro(m, client != null) { picker.launch(null) }
                    Step.ANALYZING -> Analyzing(m)
                    Step.PLAN -> PlanScreen(m)
                    Step.RECAP -> RecapView(m)
                    Step.RUNNING -> Working("Rangement en cours", m.running.name.ifEmpty { "Préparation…" }, if (m.running.total > 0) m.running.index.toFloat() / m.running.total else 0f, "Arrêter après ce fichier") { m.cancelWork() }
                    Step.DONE -> DoneView(m, onDismiss)
                    Step.TRASH -> BinView(m)
                    Step.HISTORY -> HistoryView(m)
                    Step.SETTINGS -> SettingsView(m)
                }
                }
            }
        }
    }
}

private fun phaseText(p: Progress) = when (p.phase) {
    Phase.READ -> "Lecture de la bibliothèque…"
    Phase.UNDERSTAND -> "Compréhension des noms (${p.done}/${p.total})…"
    Phase.FINGERPRINT -> "Recherche des doublons : ${p.message}"
    Phase.AI -> "Aide de l'IA pour les noms difficiles…"
    Phase.PLAN -> "Préparation de la proposition…"
    Phase.DONE -> "Terminé"
}

// ---------------------------------------------------------------------------------------------------------------

@Composable
private fun Intro(m: AssistantModel, tvAvailable: Boolean, pickFolder: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ai = AgentStore.settings.aiEnabled
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(Icons.Filled.AutoAwesome, null, tint = cs.primary, modifier = Modifier.size(40.dp))
        Text("Un coup de main pour ranger", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text("L'assistant lit les noms de vos fichiers (jamais ce qu'il y a dedans) et vous propose : des noms plus clairs, des doublons à retirer, de la place à libérer. " +
            "Il ne change rien sans votre accord.", style = MaterialTheme.typography.bodyLarge)
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Que faut-il ranger ?", style = MaterialTheme.typography.titleMedium)
                SourceRow(m.source == Origin.TV, tvAvailable, "La bibliothèque de la TV", if (tvAvailable) "Mémoire de la TV et clé USB" else "TV non connectée") { m.source = Origin.TV }
                SourceRow(m.source == Origin.PHONE, true, "Un dossier du téléphone", m.phoneTree?.let { "Dossier choisi : " + (it.lastPathSegment?.substringAfterLast(':') ?: "") } ?: "Vous choisissez le dossier, rien d'autre n'est lu") { m.source = Origin.PHONE }
                if (m.source == Origin.PHONE) {
                    OutlinedButton(pickFolder, Modifier.padding(start = 48.dp)) { Icon(Icons.Filled.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text(if (m.phoneTree == null) "Choisir un dossier" else "Changer de dossier") }
                    Text("Sur Android 11 et plus, choisissez un sous-dossier (Films, Séries…) : le système n'autorise pas tout le dossier Téléchargements.",
                        Modifier.padding(start = 48.dp), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = cs.surfaceVariant)) {
            Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Icon(if (ai) Icons.Filled.CloudQueue else Icons.Filled.PhoneAndroid, null)
                Column {
                    Text(if (ai) "Aide de l'IA : activée" else "Tout reste sur votre téléphone", style = MaterialTheme.typography.titleSmall)
                    Text(if (ai) "Pour les noms que les règles ne comprennent pas, des noms nettoyés (jamais le contenu) sont envoyés au serveur CastBridge. Vous pouvez le désactiver dans les réglages."
                    else "L'analyse se fait sans réseau avec des règles intégrées. L'aide de l'IA est facultative et désactivée (réglages).", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        m.last?.let { l ->
            val now = remember { System.currentTimeMillis() }
            Text("Dernière analyse (${if (l.origin == Origin.TV) "TV" else "téléphone"}) : ${l.ago(now)} · ${l.files} fichiers, ${l.toRename} à ranger" +
                (if (l.duplicates > 0) ", ${l.duplicates} doublon(s)" else "") + ". Une nouvelle analyse ne relit que ce qui est nouveau.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        m.message?.let { Text(it, color = cs.error) }
        Button({ m.analyze() }, Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = if (m.source == Origin.TV) tvAvailable else m.phoneTree != null) {
            Icon(Icons.Filled.Search, null); Spacer(Modifier.width(8.dp)); Text("Analyser", style = MaterialTheme.typography.titleMedium)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton({ m.loadBin(); m.step = Step.TRASH }, enabled = if (m.source == Origin.TV) tvAvailable else m.phoneTree != null) { Icon(Icons.Filled.DeleteSweep, null); Spacer(Modifier.width(6.dp)); Text("Corbeille") }
            OutlinedButton({ m.refreshHistory(); m.step = Step.HISTORY }) { Icon(Icons.Filled.Undo, null); Spacer(Modifier.width(6.dp)); Text("Annuler un rangement") }
        }
    }
}

@Composable
private fun SourceRow(selected: Boolean, enabled: Boolean, title: String, sub: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, null, enabled = enabled)
        Spacer(Modifier.width(12.dp))
        Column { Text(title, style = MaterialTheme.typography.bodyLarge); Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun Working(title: String, detail: String, fraction: Float, cancelLabel: String, onCancel: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        if (fraction > 0f) LinearProgressIndicator({ fraction.coerceIn(0f, 1f) }, Modifier.fillMaxWidth().semantics { contentDescription = "Avancement ${(fraction * 100).toInt()} pour cent" })
        else LinearProgressIndicator(Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Text(detail, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onCancel) { Text(cancelLabel) }
    }
}

/** Back arrow / back key: one step back in the guided path, never a surprise (a running job is stopped only on purpose). */
private fun back(m: AssistantModel, onDismiss: () -> Unit) {
    when (m.step) {
        Step.INTRO -> onDismiss()
        Step.ANALYZING, Step.RUNNING -> m.cancelWork()
        Step.RECAP -> m.step = Step.PLAN
        Step.DONE -> { m.step = Step.INTRO; m.analysis = null }
        else -> m.step = Step.INTRO
    }
}

/** « 1 Analyser — 2 Vérifier — 3 Appliquer » : where the user is, and that nothing is changed before step 3. */
@Composable
private fun WizardSteps(current: Int) {
    val cs = MaterialTheme.colorScheme
    val names = listOf("Analyser", "Vérifier", "Appliquer")
    val big = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).semantics(mergeDescendants = true) { contentDescription = "Étape $current sur 3 : ${names[current - 1]}" },
        verticalAlignment = Alignment.CenterVertically) {
        names.forEachIndexed { i, n ->
            val k = i + 1
            val done = k < current; val now = k == current
            Surface(shape = androidx.compose.foundation.shape.CircleShape, color = if (now) cs.primary else if (done) cs.secondary else cs.surfaceVariant, modifier = Modifier.size(28.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    if (done) Icon(Icons.Filled.Check, null, Modifier.size(16.dp), tint = cs.onSecondary)
                    else Text("$k", style = MaterialTheme.typography.labelLarge, color = if (now) cs.onPrimary else cs.onSurfaceVariant)
                }
            }
            // with a very large system font only the current step keeps its name (the number and the check still say where we are)
            if (!big || now) { Spacer(Modifier.width(6.dp))
                Text(n, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false, fontWeight = if (now) FontWeight.Bold else FontWeight.Normal, color = if (now) cs.onSurface else cs.onSurfaceVariant) }
            if (k < 3) HorizontalDivider(Modifier.weight(1f).padding(horizontal = 8.dp), color = if (done) cs.secondary else cs.outlineVariant)
        }
    }
}

/** Step 1 while reading: progress, what is already found ("au fil de l'eau"), cancel. */
@Composable
private fun Analyzing(m: AssistantModel) {
    val cs = MaterialTheme.colorScheme
    val p = m.progress
    val l = m.live
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Lecture en cours", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite })
        if (p.fraction > 0f) LinearProgressIndicator({ p.fraction.coerceIn(0f, 1f) }, Modifier.fillMaxWidth().semantics { contentDescription = "Avancement ${(p.fraction * 100).toInt()} pour cent" })
        else LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(p.message.ifEmpty { phaseText(p) }, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (l.files > 0 || l.toRename > 0) ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Déjà trouvé", style = MaterialTheme.typography.titleSmall)
                Text("${l.files} fichiers dans ${l.folders} dossier(s)" + if (l.toRename > 0) " · ${l.toRename} nom(s) à améliorer" else "", style = MaterialTheme.typography.bodyMedium)
                l.examples.forEach { (from, to) ->
                    Text(from, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("→ $to", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Text("Vous pouvez fermer cet écran : la lecture continue. Rien n'est modifié.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        OutlinedButton({ m.cancelWork() }, Modifier.heightIn(min = 48.dp)) { Text("Annuler") }
    }
}

/** Step 3, before anything happens: exactly what will be done, and the explicit confirmation of every trash. */
@Composable
private fun RecapView(m: AssistantModel) {
    val cs = MaterialTheme.colorScheme
    val eff = m.effective
    val chosen = m.plan.changes.filter { it.id in eff }
    val ren = chosen.filter { it.type == ChangeType.RENAME }
    val mov = chosen.filter { it.type == ChangeType.MOVE }
    val tr = chosen.filter { it.type == ChangeType.TRASH }
    var confirmTrash by remember { mutableStateOf(false) }
    val ok = tr.isEmpty() || confirmTrash
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Prêt à appliquer", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text("Voici exactement ce qui va se passer. Rien n'a encore été modifié.", style = MaterialTheme.typography.bodyLarge)
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (ren.isNotEmpty()) RecapLine(Icons.Filled.DriveFileRenameOutline, "${ren.size} fichier(s) renommé(s)" + (ren.count { it.toFolder != null }.takeIf { it > 0 }?.let { ", dont $it rangé(s) dans un dossier" } ?: ""))
                if (mov.isNotEmpty()) RecapLine(Icons.Filled.Usb, "${mov.size} fichier(s) déplacé(s) vers la clé USB (${size(mov.sumOf { it.bytes })})")
                if (tr.isNotEmpty()) RecapLine(Icons.Filled.Delete, "${tr.size} fichier(s) mis à la corbeille (${size(tr.sumOf { it.bytes })})", cs.error)
                RecapLine(Icons.Filled.Undo, "Tout reste annulable : « Annuler un rangement » remet les noms et les places d'avant.")
            }
        }
        if (tr.isNotEmpty()) ElevatedCard(Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = cs.surfaceVariant)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Mises à la corbeille", style = MaterialTheme.typography.titleSmall)
                tr.take(6).forEach { Text("• ${it.file.name}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                if (tr.size > 6) Text("… et ${tr.size - 6} autre(s)", style = MaterialTheme.typography.bodySmall)
                Text("Ils quittent la bibliothèque mais restent récupérables 30 jours dans la « Corbeille CastBridge ». Rien n'est effacé définitivement.", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Switch) { confirmTrash = !confirmTrash }, verticalAlignment = Alignment.CenterVertically) {
                    Text("Je confirme la mise à la corbeille de ces ${tr.size} fichier(s)", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(12.dp)); Switch(confirmTrash, null)
                }
            }
        }
        m.message?.let { Text(it, color = cs.error) }
        Button({ m.apply(tr.isNotEmpty() && confirmTrash) }, Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = ok && chosen.isNotEmpty()) {
            Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text(if (ok) "Appliquer maintenant" else "Confirmez la corbeille pour continuer", style = MaterialTheme.typography.titleMedium)
        }
        OutlinedButton({ m.step = Step.PLAN }, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Retour à la vérification") }
    }
}

@Composable
private fun RecapLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary) {
    Row(verticalAlignment = Alignment.Top) { Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(10.dp)); Text(text, style = MaterialTheme.typography.bodyMedium) }
}

// ---------------------------------------------------------------------------------------------------------------

@Composable
private fun DoneView(m: AssistantModel, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val r = m.result
    val u = m.undone
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(if (u != null) Icons.Filled.Undo else Icons.Filled.CheckCircle, null, tint = cs.primary, modifier = Modifier.size(40.dp))
        if (r != null) {
            Text(if (r.failed == 0) "C'est rangé" else "Rangement terminé avec des problèmes", style = MaterialTheme.typography.headlineSmall)
            Text("${r.done} fait(s) · ${r.skipped} laissé(s) de côté · ${r.failed} en erreur", style = MaterialTheme.typography.bodyLarge)
            r.reports.filter { it.state != OpState.DONE || it.note != null }.forEach {
                Text("• ${it.name} : ${it.note ?: it.state.name.lowercase()}", style = MaterialTheme.typography.bodySmall, color = if (it.state == OpState.FAILED) cs.error else cs.onSurfaceVariant)
            }
        }
        m.message?.let { Text(it, color = cs.error) }
        if (u != null) {
            Text("${u.restored} restauré(s), ${u.failed} impossible(s) à restaurer", style = MaterialTheme.typography.titleMedium)
            u.reports.filter { it.state == OpState.FAILED }.forEach { Text("• ${it.name} : ${it.note}", style = MaterialTheme.typography.bodySmall, color = cs.error) }
        }
        if (r != null && u == null && r.done > 0) OutlinedButton({ m.undo() }, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Icon(Icons.Filled.Undo, null); Spacer(Modifier.width(8.dp)); Text("Annuler tout ce rangement") }
        Button({ m.step = Step.INTRO; m.analysis = null }, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Terminer") }
        Text("Vous pourrez toujours annuler plus tard depuis « Annuler un rangement ». Les fichiers mis à la corbeille y restent 30 jours.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
    }
}

@Composable
private fun BinView(m: AssistantModel) {
    var confirmAll by remember { mutableStateOf(false) }
    var confirmOne by remember { mutableStateOf<BinItem?>(null) }
    val fmt = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Les fichiers mis à la corbeille par l'assistant restent récupérables 30 jours" + (if (m.source == Origin.PHONE) " (dossier « ${SafLibrary.TRASH_NAME} » de votre dossier choisi)." else " (sur la TV)."), style = MaterialTheme.typography.bodyMedium)
        m.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        if (m.binBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (!m.binBusy && m.bin.isEmpty()) Text("La corbeille est vide.", style = MaterialTheme.typography.bodyLarge)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(m.bin, key = { it.id }) { b ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(b.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(size(b.size) + if (b.expiresAt > 0) " · effacé automatiquement le ${fmt.format(Date(b.expiresAt))}" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton({ m.restore(b) }) { Text("Restaurer") }
                        IconButton({ confirmOne = b }) { Icon(Icons.Filled.DeleteForever, "Supprimer définitivement ${b.name}", tint = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
        if (m.bin.isNotEmpty()) OutlinedButton({ confirmAll = true }, Modifier.fillMaxWidth()) { Text("Vider la corbeille…", color = MaterialTheme.colorScheme.error) }
    }
    if (confirmAll) AlertDialog({ confirmAll = false }, title = { Text("Supprimer définitivement ?") },
        text = { Text("Les ${m.bin.size} fichier(s) de la corbeille (${size(m.bin.sumOf { it.size })}) seront effacés pour de bon. Cette action ne peut pas être annulée.") },
        confirmButton = { TextButton({ confirmAll = false; m.purge(null) }) { Text("Supprimer définitivement", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton({ confirmAll = false }) { Text("Garder") } })
    confirmOne?.let { b -> AlertDialog({ confirmOne = null }, title = { Text("Supprimer définitivement ?") },
        text = { Text("« ${b.name} » sera effacé pour de bon. Cette action ne peut pas être annulée.") },
        confirmButton = { TextButton({ confirmOne = null; m.purge(b) }) { Text("Supprimer définitivement", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton({ confirmOne = null }) { Text("Garder") } }) }
}

@Composable
private fun HistoryView(m: AssistantModel) {
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    var confirm by remember { mutableStateOf<AssistantModel.RunSummary?>(null) }
    LaunchedEffect(Unit) { m.refreshHistory() }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Chaque rangement est noté ici. « Annuler » remet les noms et les emplacements d'avant, et sort de la corbeille ce qui y avait été mis (sans jamais écraser un fichier).", style = MaterialTheme.typography.bodyMedium)
        m.undone?.let { Text("${it.restored} élément(s) restauré(s)" + if (it.failed > 0) ", ${it.failed} impossible(s) : " + it.reports.filter { r -> r.state == OpState.FAILED }.joinToString { r -> "${r.name} (${r.note})" } else "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
        m.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (m.history.isEmpty()) Text("Aucun rangement pour l'instant.", style = MaterialTheme.typography.bodyLarge)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(m.history, key = { it.runId }) { h ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(fmt.format(Date(h.at)) + if (h.runId.startsWith("phone")) " · téléphone" else " · TV", style = MaterialTheme.typography.titleSmall)
                        Text(buildString {
                            append("${h.renamed} renommé(s)/classé(s)"); if (h.moved > 0) append(", ${h.moved} déplacé(s)"); if (h.trashed > 0) append(", ${h.trashed} à la corbeille")
                            if (h.undone > 0) append(" · ${h.undone} déjà annulé(s)")
                        }, style = MaterialTheme.typography.bodyMedium)
                        if (h.undoable > 0) TextButton({ confirm = h }) { Icon(Icons.Filled.Undo, null); Spacer(Modifier.width(6.dp)); Text("Annuler ce rangement") }
                    }
                }
            }
        }
    }
    confirm?.let { h -> AlertDialog({ confirm = null }, title = { Text("Annuler ce rangement ?") },
        text = { Text("Les ${h.undoable} changement(s) seront défaits, du plus récent au plus ancien. Un nom déjà pris ne sera jamais écrasé.") },
        confirmButton = { TextButton({ confirm = null; m.undo(h.runId) }) { Text("Annuler le rangement") } },
        dismissButton = { TextButton({ confirm = null }) { Text("Garder") } }) }
}

@Composable
private fun SettingsView(m: AssistantModel) {
    val ctx = LocalContext.current
    val s = AgentStore.settings
    var auto by remember { mutableStateOf(s.autoRename) }
    var fileTree by remember { mutableStateOf(s.fileTree) }
    var ai by remember { mutableStateOf(s.aiEnabled) }
    var askAi by remember { mutableStateOf(false) }
    var proactive by remember { mutableStateOf(s.proactiveNotify) }
    var noPerm by remember { mutableStateOf(false) }
    var learned by remember { mutableStateOf(AgentStore.learned.size()) }
    var cleared by remember { mutableStateOf(false) }
    var cacheCleared by remember { mutableStateOf(false) }
    var sample by remember { mutableStateOf("Prison.Break.S01E04.720p.HDTV.x264-RARBG.mkv") }
    val cs = MaterialTheme.colorScheme
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { proactive = true; s.proactiveNotify = true; AgentProactive.sync(ctx); noPerm = false } else { proactive = false; noPerm = true }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // R-13: the category tree of new copies, ON by default (the owner expects Films/…, Séries/Titre/Saison… on the TV and its USB key)
        SettingSwitch("Classer les nouveaux envois dans des dossiers", "Activé par défaut : chaque copie vers la TV (et sa clé USB) va dans Films, Séries/Titre/Saison, Musique, Photos/AAAA-MM ou Documents ; ce qui vient de la TV va dans Téléchargements/CastBridge, classé de même. Désactivez pour garder les nouveaux fichiers à la racine.", fileTree) { fileTree = it; s.fileTree = it }
        SettingSwitch("Renommer automatiquement les nouveaux envois", "Désactivé par défaut. Quand vous envoyez un fichier à la TV, il est renommé dès le téléphone seulement si les règles sont sûres (séries, films, vidéos WhatsApp). Jamais de suppression. Chaque renommage est noté et annulable.", auto) { auto = it; s.autoRename = it }
        // what the option would do, without sending anything: a field to try a name
        OutlinedTextField(sample, { sample = it }, Modifier.fillMaxWidth(), label = { Text("Essayer un nom de fichier") }, singleLine = true,
            supportingText = {
                val r = remember(sample) { AutoRename.nameFor(sample, learned = AgentStore.learned) }
                Text(if (r != null) "Serait envoyé sous : $r" else "Laissé tel quel (les règles ne sont pas assez sûres)")
            })
        val autoCount = remember(auto) { AgentStore.journal.entries().count { it.runId.startsWith("tv-auto") && it.state == OpState.DONE } }
        if (autoCount > 0) Text("$autoCount envoi(s) déjà renommé(s) à l'envoi : voir « Annuler un rangement ».", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        HorizontalDivider()
        SettingSwitch("Suggestions par notification", "Désactivé par défaut. Une notification silencieuse au plus par semaine (« 37 fichiers à ranger »), calculée sur ce téléphone pour le dossier que vous avez choisi. Elle ouvre l'assistant, elle ne change rien. Le conseil discret dans la bibliothèque de la TV reste là dans tous les cas.", proactive) { on ->
            if (!on) { proactive = false; s.proactiveNotify = false; AgentProactive.sync(ctx) }
            else if (AgentProactive.canNotify(ctx)) { proactive = true; s.proactiveNotify = true; AgentProactive.sync(ctx) }
            else notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        if (noPerm) Text("Android a refusé les notifications pour CastBridge : l'option reste désactivée.", style = MaterialTheme.typography.bodySmall, color = cs.error)
        if (proactive && m.phoneTree == null) Text("Choisissez d'abord un dossier du téléphone (écran d'accueil de l'assistant) : c'est lui que la notification surveille.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        HorizontalDivider()
        SettingSwitch(AiConsent.TITLE, "Désactivée par défaut. Activée, elle n'envoie que des noms nettoyés pour les cas que les règles ne comprennent pas.", ai) { if (it) askAi = true else { ai = false; s.revokeAi() } }
        Text("Ce qui serait envoyé : un nom nettoyé comme « prison break s01e04 », son extension, sa durée arrondie, la langue de l'application. Jamais le contenu, les dossiers, les tailles, vos vidéos personnelles, ni un identifiant de l'appareil.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        HorizontalDivider()
        Text("Ce que l'assistant a appris de vos corrections : $learned règle(s), stockées sur ce téléphone uniquement.", style = MaterialTheme.typography.bodyMedium)
        OutlinedButton({ AgentStore.learned.clear(); learned = 0 }, Modifier.heightIn(min = 48.dp), enabled = learned > 0) { Text("Effacer ce qu'il a appris") }
        OutlinedButton({ AgentStore.journal.clear(); m.history = emptyList(); cleared = true }, Modifier.heightIn(min = 48.dp)) { Text("Effacer l'historique des rangements") }
        if (cleared) Text("Historique effacé (les annulations ne sont plus possibles).", style = MaterialTheme.typography.bodySmall)
        Text("Pour aller plus vite, l'assistant garde la durée des vidéos et une empreinte des fichiers déjà lus (${AgentStore.cache.size()} entrée(s)), sur ce téléphone uniquement.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        OutlinedButton({ AgentStore.cache.clear(); cacheCleared = true }, Modifier.heightIn(min = 48.dp)) { Text("Effacer cette mémoire d'analyse") }
        if (cacheCleared) Text("Effacée : la prochaine analyse relira tout.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton({ s.phoneTreeUri = null; m.phoneTree = null }, Modifier.heightIn(min = 48.dp), enabled = m.phoneTree != null) { Text("Oublier le dossier du téléphone") }
    }
    if (askAi) AlertDialog({ askAi = false }, icon = { Icon(Icons.Filled.CloudQueue, null) }, title = { Text(AiConsent.TITLE) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) { AiConsent.paragraphs.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) } } },
        confirmButton = { TextButton({ askAi = false; ai = true; s.grantAi() }) { Text("J'accepte, activer") } },
        dismissButton = { TextButton({ askAi = false }) { Text("Non merci") } })
}

@Composable
private fun SettingSwitch(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(value = on, role = Role.Switch, onValueChange = onChange), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleSmall); Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.width(12.dp))
        Switch(on, null)
    }
}
