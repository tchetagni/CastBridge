package castbridge.sender.agent

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.contentDescription
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
    val scope = rememberCoroutineScope()
    val m = remember { AssistantModel(ctx, client, scope).also { if (client == null) it.source = Origin.PHONE } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            m.pickedTree(uri)
        }
    }

    val title = when (m.step) {
        Step.TRASH -> "Corbeille CastBridge"; Step.HISTORY -> "Historique"; Step.SETTINGS -> "Réglages de l'assistant"; else -> "Ranger ma bibliothèque"
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)) {
        // A full-screen Dialog is laid out for the whole display but its window starts below the status bar: the bottom would be
        // cut by that height (the bottom buttons of the plan disappeared). Keep that height free at the bottom.
        val statusBar = remember { ctx.resources.getIdentifier("status_bar_height", "dimen", "android").let { id -> if (id > 0) ctx.resources.getDimensionPixelSize(id) / ctx.resources.displayMetrics.density else 24f } }
        Surface(Modifier.fillMaxSize().padding(bottom = statusBar.dp), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton({ when (m.step) { Step.INTRO -> onDismiss(); Step.ANALYZING -> { m.cancelWork() }; Step.RUNNING -> m.cancelWork(); else -> m.step = Step.INTRO } }) {
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
                Box(Modifier.weight(1f).fillMaxWidth()) {
                when (m.step) {
                    Step.INTRO -> Intro(m, client != null) { picker.launch(null) }
                    Step.ANALYZING -> Working("Analyse en cours", m.progress.message.ifEmpty { phaseText(m.progress) }, m.progress.fraction, "Annuler") { m.cancelWork() }
                    Step.PLAN -> PlanView(m)
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
        m.message?.let { Text(it, color = cs.error) }
        Button({ m.analyze() }, Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = if (m.source == Origin.TV) tvAvailable else m.phoneTree != null) {
            Icon(Icons.Filled.Search, null); Spacer(Modifier.width(8.dp)); Text("Analyser", style = MaterialTheme.typography.titleMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ m.loadBin(); m.step = Step.TRASH }, Modifier.weight(1f), enabled = if (m.source == Origin.TV) tvAvailable else m.phoneTree != null) { Icon(Icons.Filled.DeleteSweep, null); Spacer(Modifier.width(6.dp)); Text("Corbeille") }
            OutlinedButton({ m.refreshHistory(); m.step = Step.HISTORY }, Modifier.weight(1f)) { Icon(Icons.Filled.Undo, null); Spacer(Modifier.width(6.dp)); Text("Annuler un rangement") }
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

// ---------------------------------------------------------------------------------------------------------------

@Composable
private fun PlanView(m: AssistantModel) {
    val cs = MaterialTheme.colorScheme
    val a = m.analysis ?: return
    val plan = m.plan
    var confirmTrash by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Change?>(null) }
    var showSent by remember { mutableStateOf(false) }
    val nSel = m.selected.size
    val trashSel = m.selectedTrash

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 190.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = cs.secondaryContainer)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (plan.changes.isEmpty()) "Rien à ranger" else "${plan.changes.size} changement(s) proposé(s)", style = MaterialTheme.typography.titleMedium)
                        Text("${a.stats.files} fichiers lus · ${a.stats.wellNamed.coerceAtLeast(0)} déjà bien rangés" +
                            (if (a.stats.duplicateGroups > 0) " · ${a.stats.duplicateGroups} doublon(s) = ${size(a.stats.duplicateBytes)}" else ""), style = MaterialTheme.typography.bodyMedium)
                        a.insights.forEach { Text("• ${it.text}", style = MaterialTheme.typography.bodySmall) }
                        plan.notes.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                        if (a.stats.aiUsed > 0) {
                            Text("${a.stats.aiUsed} nom(s) ont été proposés avec l'aide de l'IA (jamais cochés d'avance).", style = MaterialTheme.typography.bodySmall)
                            m.sentPreview?.let { TextButton({ showSent = true }, contentPadding = PaddingValues(0.dp)) { Text("Voir ce qui a été envoyé au serveur") } }
                        }
                    }
                }
            }
            if (plan.changes.isNotEmpty()) item {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton({ m.selectAllSafe() }) { Text("Tout accepter") }
                    TextButton({ m.selectNone() }) { Text("Tout décocher") }
                    TextButton({ m.selectDefault() }) { Text("Conseillés") }
                }
                Text("« Tout accepter » ne coche jamais les mises à la corbeille : elles se cochent une par une.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            section("Renommer et classer", plan.renames, m, onEdit = { editing = it })
            section("Déplacer vers la clé USB", plan.moves, m, onEdit = {})
            section("Doublons à mettre dans la corbeille", plan.trash.filter { it.why != TrashWhy.WATCHED_OLD }, m, onEdit = {})
            section("Libérer de l'espace (déjà vus depuis longtemps)", plan.trash.filter { it.why == TrashWhy.WATCHED_OLD }, m, onEdit = {})
            if (plan.skipped.isNotEmpty()) item {
                var open by remember { mutableStateOf(false) }
                Column {
                    TextButton({ open = !open }, contentPadding = PaddingValues(0.dp)) { Text("${plan.skipped.size} fichier(s) laissés de côté ${if (open) "▲" else "▼"}") }
                    if (open) plan.skipped.take(50).forEach { Text("• ${it.file.name} : ${it.reason}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant) }
                }
            }
        }
        Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), tonalElevation = 3.dp) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // circumstantial: a long copy to the USB key while the TV is usually being watched
                val hour = remember { java.time.LocalTime.now().hour }
                if (plan.moves.any { it.id in m.selected } && a.habits.isBusy(hour))
                    Text("La TV est souvent utilisée à cette heure : les déplacements vers la clé peuvent être longs. Vous pouvez les lancer plus tard.", style = MaterialTheme.typography.bodySmall, color = cs.primary)
                if (trashSel.isNotEmpty()) Text("${trashSel.size} fichier(s) iront dans la corbeille (${size(trashSel.sumOf { it.bytes })}). Vous devrez confirmer.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ m.step = Step.INTRO }, Modifier.heightIn(min = 52.dp)) { Text("Fermer") }
                    Button({ if (trashSel.isNotEmpty()) confirmTrash = true else m.apply(false) }, Modifier.weight(1f).heightIn(min = 52.dp), enabled = nSel > 0) {
                        Text(if (nSel == 0) "Rien de coché" else "Appliquer $nSel changement(s)")
                    }
                }
            }
        }
    }

    if (confirmTrash) AlertDialog(
        onDismissRequest = { confirmTrash = false },
        icon = { Icon(Icons.Filled.DeleteSweep, null) },
        title = { Text("Mettre ${trashSel.size} fichier(s) dans la corbeille ?") },
        text = { Text("Ils quittent la bibliothèque (${size(trashSel.sumOf { it.bytes })}) mais restent dans la « Corbeille CastBridge » pendant 30 jours : vous pouvez les récupérer à tout moment avec « Corbeille » ou « Annuler un rangement ». Rien n'est effacé définitivement.") },
        confirmButton = { TextButton({ confirmTrash = false; m.apply(true) }) { Text("Mettre à la corbeille") } },
        dismissButton = { TextButton({ confirmTrash = false }) { Text("Annuler") } })
    editing?.let { c -> EditDialog(c, onDismiss = { editing = null }) { name -> m.edit(c.id, name).also { err -> if (err == null) editing = null } } }
    if (showSent) AlertDialog(onDismissRequest = { showSent = false }, title = { Text("Envoyé au serveur CastBridge") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text("Uniquement ces noms nettoyés (aucun contenu, dossier, taille ni identifiant) :", style = MaterialTheme.typography.bodySmall); Spacer(Modifier.height(8.dp)); Text(m.sentPreview.orEmpty()) } },
        confirmButton = { TextButton({ showSent = false }) { Text("Fermer") } })
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(title: String, list: List<Change>, m: AssistantModel, onEdit: (Change) -> Unit) {
    if (list.isEmpty()) return
    item(key = "h:$title") { Text("$title · ${list.size}", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
    items(list, key = { it.id }) { c -> ChangeRow(c, c.id in m.selected, { m.toggle(c.id, it) }, onEdit, { m.ignore(c) }) }
}

@Composable
private fun ChangeRow(c: Change, checked: Boolean, onChecked: (Boolean) -> Unit, onEdit: (Change) -> Unit, onIgnore: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    val before = if (c.file.folder.isNotEmpty()) c.file.folder + "/" + c.file.name else c.file.name
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = cs.surface)) {
        Row(Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { onChecked(!checked) }.padding(vertical = 6.dp, horizontal = 4.dp), verticalAlignment = Alignment.Top) {
            Checkbox(checked, null, Modifier.padding(12.dp).semantics { contentDescription = "Appliquer : ${c.file.name}" })
            Column(Modifier.weight(1f).padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(before, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    textDecoration = if (c.type == ChangeType.TRASH) null else TextDecoration.LineThrough)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (c.type == ChangeType.TRASH) Icons.Filled.Delete else Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(16.dp), tint = if (c.type == ChangeType.TRASH) cs.error else cs.primary)
                    Spacer(Modifier.width(4.dp))
                    Text(when (c.type) { ChangeType.MOVE -> "Clé USB (${size(c.bytes)})"; ChangeType.TRASH -> "Corbeille CastBridge (${size(c.bytes)})"; else -> c.after },
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Text(c.reason, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                if (c.source == Source.AI) AssistChip({}, label = { Text("Proposé par l'IA : à vérifier") }, modifier = Modifier.heightIn(min = 32.dp))
                else if (c.source == Source.LEARNED) Text("D'après vos corrections précédentes", style = MaterialTheme.typography.labelSmall, color = cs.primary)
            }
            Box {
                IconButton({ menu = true }) { Icon(Icons.Filled.MoreVert, "Plus d'actions pour ${c.file.name}") }
                DropdownMenu(menu, { menu = false }) {
                    if (c.type == ChangeType.RENAME) DropdownMenuItem({ Text("Modifier le nom") }, { menu = false; onEdit(c) }, leadingIcon = { Icon(Icons.Filled.Edit, null) })
                    DropdownMenuItem({ Text("Ne plus toucher à ce fichier") }, { menu = false; onIgnore() }, leadingIcon = { Icon(Icons.Filled.Block, null) })
                }
            }
        }
    }
}

@Composable
private fun EditDialog(c: Change, onDismiss: () -> Unit, onSave: (String) -> String?) {
    var text by remember { mutableStateOf(c.toName ?: c.file.name) }
    var err by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Modifier le nom") },
        text = {
            Column {
                OutlinedTextField(text, { text = it; err = null }, Modifier.fillMaxWidth(), isError = err != null, supportingText = { Text(err ?: "L'assistant retiendra votre choix pour les fichiers du même titre.") },
                    label = { Text("Nom du fichier") }, keyboardOptions = KeyboardOptions.Default)
            }
        },
        confirmButton = { TextButton({ err = onSave(text) }) { Text("Enregistrer") } },
        dismissButton = { TextButton(onDismiss) { Text("Annuler") } })
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
    val s = AgentStore.settings
    var auto by remember { mutableStateOf(s.autoRename) }
    var ai by remember { mutableStateOf(s.aiEnabled) }
    var askAi by remember { mutableStateOf(false) }
    var learned by remember { mutableStateOf(AgentStore.learned.size()) }
    var cleared by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingSwitch("Ranger automatiquement les nouveaux envois", "Quand vous envoyez un fichier à la TV, il est renommé seulement si les règles sont sûres (séries, films, vidéos WhatsApp). Jamais de dossier, jamais de suppression. Chaque renommage est noté et annulable.", auto) { auto = it; s.autoRename = it }
        HorizontalDivider()
        SettingSwitch(AiConsent.TITLE, "Désactivée par défaut. Activée, elle n'envoie que des noms nettoyés pour les cas que les règles ne comprennent pas.", ai) { if (it) askAi = true else { ai = false; s.revokeAi() } }
        Text("Ce qui serait envoyé : un nom nettoyé comme « prison break s01e04 », son extension, sa durée arrondie, la langue de l'application. Jamais le contenu, les dossiers, les tailles, vos vidéos personnelles, ni un identifiant de l'appareil.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        HorizontalDivider()
        Text("Ce que l'assistant a appris de vos corrections : $learned règle(s), stockées sur ce téléphone uniquement.", style = MaterialTheme.typography.bodyMedium)
        OutlinedButton({ AgentStore.learned.clear(); learned = 0 }, enabled = learned > 0) { Text("Effacer ce qu'il a appris") }
        OutlinedButton({ AgentStore.journal.clear(); m.history = emptyList(); cleared = true }) { Text("Effacer l'historique des rangements") }
        if (cleared) Text("Historique effacé (les annulations ne sont plus possibles).", style = MaterialTheme.typography.bodySmall)
        OutlinedButton({ s.phoneTreeUri = null; m.phoneTree = null }, enabled = m.phoneTree != null) { Text("Oublier le dossier du téléphone") }
    }
    if (askAi) AlertDialog({ askAi = false }, icon = { Icon(Icons.Filled.CloudQueue, null) }, title = { Text(AiConsent.TITLE) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) { AiConsent.paragraphs.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) } } },
        confirmButton = { TextButton({ askAi = false; ai = true; s.grantAi() }) { Text("J'accepte, activer") } },
        dismissButton = { TextButton({ askAi = false }) { Text("Non merci") } })
}

@Composable
private fun SettingSwitch(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Switch) { onChange(!on) }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleSmall); Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.width(12.dp))
        Switch(on, null)
    }
}
