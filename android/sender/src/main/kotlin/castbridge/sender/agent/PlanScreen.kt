package castbridge.sender.agent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import castbridge.core.library.agent.*
import castbridge.sender.Cb

private fun size(b: Long) = castbridge.core.library.agent.Text.size(b)

/** Step 2 of 3, « Vérifier » : filters, collapsible groups, before → after lines, « pourquoi ? », manual correction. Nothing changes here. */
@Composable
fun PlanScreen(m: AssistantModel) {
    val cs = MaterialTheme.colorScheme
    val a = m.analysis ?: return
    val plan = m.plan
    val counts = remember(plan) { PlanView.counts(plan) }
    val groups = remember(plan, m.filter) { PlanView.groups(plan, m.filter) }
    var open by remember { mutableStateOf(setOf<String>()) }
    var pages by remember { mutableStateOf(mapOf<String, Int>()) }
    var why by remember { mutableStateOf(setOf<String>()) }
    var editing by remember { mutableStateOf<Change?>(null) }
    var showSent by remember { mutableStateOf(false) }
    var showAdvice by remember { mutableStateOf(false) }
    LaunchedEffect(m.analysis) { groups.firstOrNull()?.let { open = open + it.id } }
    val nSel = m.effective.size

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 150.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item(key = "summary") {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = cs.secondaryContainer)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (plan.changes.isEmpty()) "Rien à ranger" else "${plan.changes.size} changement(s) proposé(s)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                        Text("${a.stats.files} fichiers lus · ${a.stats.wellNamed.coerceAtLeast(0)} déjà bien rangés" +
                            (if (a.stats.duplicateGroups > 0) " · ${a.stats.duplicateGroups} doublon(s) = ${size(a.stats.duplicateBytes)}" else ""), style = MaterialTheme.typography.bodyMedium)
                        if (a.insights.isNotEmpty() || plan.notes.isNotEmpty()) {
                            TextButton({ showAdvice = !showAdvice }, contentPadding = PaddingValues(0.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                                Text(if (showAdvice) "Masquer les conseils" else "Voir les conseils (${a.insights.size + plan.notes.size})")
                            }
                            if (showAdvice) {
                                a.insights.forEach { Text("• ${it.text}", style = MaterialTheme.typography.bodySmall) }
                                plan.notes.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                        if (a.stats.aiUsed > 0) {
                            Text("${a.stats.aiUsed} nom(s) ont été proposés avec l'aide de l'IA (jamais cochés d'avance).", style = MaterialTheme.typography.bodySmall)
                            m.sentPreview?.let { TextButton({ showSent = true }, contentPadding = PaddingValues(0.dp), modifier = Modifier.heightIn(min = 48.dp)) { Text("Voir ce qui a été envoyé au serveur") } }
                        }
                    }
                }
            }
            if (m.refining) item(key = "refining") {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = cs.surfaceVariant)) {
                    Row(Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        val p = m.refineProgress
                        Column(Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }) {
                            Text("Recherche des doublons en cours…", style = MaterialTheme.typography.titleSmall)
                            Text(if (p.total > 0) "${minOf(p.done, p.total)} sur ${p.total}" + (p.message.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "") else "Les premiers résultats sont déjà là.", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        TextButton({ m.skipRefining() }) { Text("Passer") }
                    }
                }
            }
            m.learnedNote?.let { note ->
                item(key = "learned") {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = cs.surfaceVariant)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Filled.School, null, tint = cs.primary); Spacer(Modifier.width(10.dp))
                            Text(note, style = MaterialTheme.typography.bodySmall, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        }
                    }
                }
            }
            if (plan.changes.isNotEmpty()) {
                item(key = "filters") {
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (f in PlanFilter.values()) {
                            val n = counts[f] ?: 0
                            if (f != PlanFilter.ALL && n == 0) continue
                            FilterChip(m.filter == f, { m.filter = f }, label = { Text("${f.label} · $n") },
                                leadingIcon = if (m.filter == f) ({ Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) }) else null,
                                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Filtre ${f.label}, $n changement(s)" + if (m.filter == f) ", sélectionné" else "" })
                        }
                    }
                }
                item(key = "quick") {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        TextButton({ m.selectDefault() }) { Text("Conseillés") }
                        TextButton({ m.selectAllSafe() }) { Text("Tout cocher") }
                        TextButton({ m.selectNone() }) { Text("Tout décocher") }
                    }
                    Text("« Tout cocher » ne coche jamais les mises à la corbeille : elles se cochent une par une.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
            if (plan.changes.isNotEmpty() && groups.isEmpty()) item(key = "nogroup") { Text("Aucun changement dans ce filtre.", Modifier.padding(vertical = 16.dp), style = MaterialTheme.typography.bodyLarge) }
            for (g in groups) {
                val isOpen = g.id in open
                item(key = "h:${g.id}") {
                    GroupHeader(g, isOpen, m.selected, onToggleOpen = { open = if (isOpen) open - g.id else open + g.id },
                        onTick = { all -> m.toggleMany(PlanView.safeIds(g), !all) })
                }
                if (isOpen) {
                    val n = pages[g.id] ?: 1
                    val shown = g.page(n)
                    items(shown, key = { it.id }) { c ->
                        ChangeRow(c, c.id in m.selected, c.id in why, { m.toggle(c.id, it) }, { why = if (c.id in why) why - c.id else why + c.id }, { editing = c }, { m.ignore(c) })
                    }
                    if (shown.size < g.changes.size) item(key = "more:${g.id}") {
                        TextButton({ pages = pages + (g.id to n + 1) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text("Afficher ${minOf(PlanView.PAGE, g.changes.size - shown.size)} de plus (reste ${g.changes.size - shown.size})")
                        }
                    }
                }
            }
            if (plan.skipped.isNotEmpty()) item(key = "skipped") {
                var more by remember { mutableStateOf(false) }
                Column {
                    TextButton({ more = !more }, contentPadding = PaddingValues(0.dp), modifier = Modifier.heightIn(min = 48.dp)) { Text("${plan.skipped.size} fichier(s) laissés de côté ${if (more) "▲" else "▼"}") }
                    if (more) plan.skipped.take(50).forEach { Text("• ${it.file.name} : ${it.reason}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant) }
                }
            }
        }
        BottomBar(m, nSel, a)
    }

    editing?.let { c -> EditDialog(c, onDismiss = { editing = null }) { name -> m.edit(c.id, name).also { err -> if (err == null) editing = null } } }
    if (showSent) AlertDialog(onDismissRequest = { showSent = false }, title = { Text("Envoyé au serveur CastBridge") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text("Uniquement ces noms nettoyés (aucun contenu, dossier, taille ni identifiant) :", style = MaterialTheme.typography.bodySmall); Spacer(Modifier.height(8.dp)); Text(m.sentPreview.orEmpty()) } },
        confirmButton = { TextButton({ showSent = false }) { Text("Fermer") } })
}

@Composable
private fun BottomBar(m: AssistantModel, nSel: Int, a: Analysis) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize()) {
        Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), tonalElevation = 3.dp) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val hour = remember { java.time.LocalTime.now().hour }
                if (m.plan.moves.any { it.id in m.selected } && a.habits.isBusy(hour))
                    Text("La TV est souvent utilisée à cette heure : les déplacements vers la clé peuvent être longs. Vous pouvez les lancer plus tard.", style = MaterialTheme.typography.bodySmall, color = cs.primary)
                val nTrash = m.selectedTrash.size
                Text(if (nSel == 0) "Rien de coché pour l'instant." else "$nSel changement(s) coché(s)" + (if (nTrash > 0) " dont $nTrash mise(s) à la corbeille (${size(m.selectedTrash.sumOf { it.bytes })}) : vous confirmerez à l'étape suivante" else "") + ". Rien n'est encore modifié.",
                    style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ m.step = Step.INTRO }, Modifier.heightIn(min = 52.dp)) { Text("Retour") }
                    Button({ m.toRecap() }, Modifier.weight(1f).heightIn(min = 52.dp), enabled = nSel > 0) {
                        Text(if (nSel == 0) "Cochez au moins un changement" else if (m.refining) "Continuer sans attendre les doublons" else "Continuer")
                        Spacer(Modifier.width(6.dp)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(g: PlanGroup, open: Boolean, selected: Set<String>, onToggleOpen: () -> Unit, onTick: (allTicked: Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val safe = PlanView.safeIds(g)
    val ticked = PlanView.tickedIn(g, selected)
    val allSafe = safe.isNotEmpty() && safe.all { it in selected }
    val someSafe = safe.any { it in selected }
    Surface(Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(12.dp), color = cs.surfaceVariant) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (safe.isNotEmpty()) TriStateCheckbox(if (allSafe) ToggleableState.On else if (someSafe) ToggleableState.Indeterminate else ToggleableState.Off, { onTick(allSafe) },
                Modifier.semantics { contentDescription = "Cocher tout le groupe ${g.title}" })
            else Spacer(Modifier.width(16.dp))
            Row(Modifier.weight(1f).heightIn(min = 56.dp).clickable(onClickLabel = if (open) "Replier ${g.title}" else "Déplier ${g.title}", role = Role.Button, onClick = onToggleOpen).padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(g.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
                    Text("${g.changes.size} fichier(s) · $ticked coché(s)" + if (g.kind == GroupKind.DUPLICATES || g.kind == GroupKind.OLD || g.kind == GroupKind.MOVE) " · ${size(g.bytes)}" else "",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, Modifier.padding(horizontal = 12.dp))
            }
        }
    }
}

@Composable
private fun ChangeRow(c: Change, checked: Boolean, showWhy: Boolean, onChecked: (Boolean) -> Unit, onWhy: () -> Unit, onEdit: (Change) -> Unit, onIgnore: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    val before = if (c.file.folder.isNotEmpty()) c.file.folder + "/" + c.file.name else c.file.name
    val after = when (c.type) { ChangeType.MOVE -> "Clé USB (${size(c.bytes)})"; ChangeType.TRASH -> "Corbeille CastBridge (${size(c.bytes)})"; else -> c.after }
    val sure = Explain.sureness(c)
    val spoken = when (c.type) { ChangeType.RENAME -> "Renommer ${c.file.name} en $after"; ChangeType.MOVE -> "Déplacer ${c.file.name} vers la clé USB"; ChangeType.TRASH -> "Mettre ${c.file.name} à la corbeille" } + ". Confiance : ${sure.label.lowercase()}."
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = cs.surface)) {
        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Checkbox(checked, { onChecked(it) }, Modifier.padding(4.dp).semantics { contentDescription = "Appliquer : $spoken" })
                Column(Modifier.weight(1f).clickable(role = Role.Checkbox) { onChecked(!checked) }.padding(top = 10.dp, bottom = 6.dp).semantics { stateDescription = if (checked) "Coché" else "Décoché" },
                    verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Line("Avant", before, strong = false)
                    Line(if (c.type == ChangeType.TRASH) "Vers" else "Après", after, strong = true, accent = if (c.type == ChangeType.TRASH) cs.error else cs.primary)
                    c.keep?.let { Line("Garde", it.name, strong = false) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val (icon, color) = when (sure) { Sureness.HIGH -> Icons.Filled.CheckCircle to Cb.success; Sureness.MEDIUM -> Icons.Filled.Info to Cb.warning; Sureness.LOW -> Icons.Filled.Warning to Cb.warning }
                        Icon(icon, null, Modifier.size(14.dp), tint = color)
                        Text(sure.label, style = MaterialTheme.typography.labelMedium, color = color)
                        when (c.source) {
                            Source.AI -> Text("Proposé par l'IA : à vérifier", style = MaterialTheme.typography.labelMedium, color = cs.primary)
                            Source.LEARNED -> Text(if (c.reason == "Nom choisi par vous") "Votre nom" else "Appris de vos corrections", style = MaterialTheme.typography.labelMedium, color = cs.primary)
                            else -> {}
                        }
                    }
                }
                Box {
                    IconButton({ menu = true }, Modifier.size(48.dp)) { Icon(Icons.Filled.MoreVert, "Plus d'actions pour ${c.file.name}") }
                    DropdownMenu(menu, { menu = false }) {
                        if (c.type == ChangeType.RENAME) DropdownMenuItem({ Text("Modifier le nom") }, { menu = false; onEdit(c) }, leadingIcon = { Icon(Icons.Filled.Edit, null) })
                        DropdownMenuItem({ Text("Pourquoi cette proposition ?") }, { menu = false; onWhy() }, leadingIcon = { Icon(Icons.Filled.HelpOutline, null) })
                        DropdownMenuItem({ Text("Ne plus toucher à ce fichier") }, { menu = false; onIgnore() }, leadingIcon = { Icon(Icons.Filled.Block, null) })
                    }
                }
            }
            TextButton(onWhy, Modifier.padding(start = 48.dp).heightIn(min = 40.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                Icon(if (showWhy) Icons.Filled.ExpandLess else Icons.Filled.HelpOutline, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                Text(if (showWhy) "Masquer l'explication" else "Pourquoi cette proposition ?", style = MaterialTheme.typography.labelLarge)
            }
            if (showWhy) {
                val w = remember(c) { Explain.of(c) }
                Surface(Modifier.fillMaxWidth().padding(start = 52.dp, end = 12.dp, bottom = 10.dp), shape = RoundedCornerShape(8.dp), color = cs.surfaceVariant) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(w.headline, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        w.lines.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Line(label: String, text: String, strong: Boolean, accent: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Row(verticalAlignment = Alignment.Top) {
        Text(label.uppercase(), Modifier.width(52.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = if (strong) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall, fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal,
            color = if (strong) accent else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun EditDialog(c: Change, onDismiss: () -> Unit, onSave: (String) -> String?) {
    var text by remember { mutableStateOf(c.toName ?: c.file.name) }
    var err by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Modifier le nom") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Avant : ${c.file.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(text, { text = it; err = null }, Modifier.fillMaxWidth(), isError = err != null, supportingText = { Text(err ?: "L'assistant retiendra votre choix : les autres fichiers du même titre seront nommés pareil. Effaçable dans les réglages.") },
                    label = { Text("Nom du fichier") }, keyboardOptions = KeyboardOptions.Default)
            }
        },
        confirmButton = { TextButton({ err = onSave(text) }) { Text("Enregistrer") } },
        dismissButton = { TextButton(onDismiss) { Text("Annuler") } })
}
