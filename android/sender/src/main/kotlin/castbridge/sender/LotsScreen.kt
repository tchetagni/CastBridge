package castbridge.sender

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import castbridge.core.lots.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Small entry point for the Réglages, Apprendre and Jeux screens: a button that opens « Données hors ligne ». */
@Composable
fun LotsEntry(modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = modifier) { Text("Données hors ligne") }
    if (open) LotsScreen { open = false }
}

/**
 * « Données » (docs/LOTS.md): what the phone keeps for « Apprendre » and « Quiz » (up to 100 Mo, downloaded when the phone has
 * Internet), what is waiting for / already on the TV (10 Mo), in plain French. Works fully offline with what is stored.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LotsScreen(onClose: () -> Unit) {
    val v = rememberLinkVersion()
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var skipped by remember { mutableStateOf(emptyList<LotPlanner.Skipped>()) }
    LaunchedEffect(Unit) { PhoneConnect.screens.enter("lots") }
    LaunchedEffect(v) { skipped = withContext(Dispatchers.IO) { LotsRuntime.skipped() } }
    val store = LotsRuntime.store
    val now = System.currentTimeMillis()
    val busy = LotsRuntime.running != null

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(title = { Text("Données hors ligne") }, navigationIcon = { IconButton(onClose) { Icon(Icons.Filled.ArrowBack, "Retour") } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface))
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // ---- storage meter ----
                    val used = store.usedBytes()
                    Text("Sur le téléphone : ${LotStore.mo(used)} sur ${LotStore.mo(store.maxBytes)}", style = MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(progress = { (used.toFloat() / store.maxBytes).coerceIn(0f, 1f) }, Modifier.fillMaxWidth())
                    listOf("learn" to "Apprendre", "quiz" to "Quiz").forEach { (f, label) ->
                        Text("$label : ${LotStore.mo(store.usedBytes(f))}", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        if (LotsRuntime.lastSyncAt > 0) "Dernière mise à jour : ${LotStatusText.age(LotsRuntime.lastSyncAt, now)}" else "Pas encore de mise à jour",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LotsRuntime.lastSyncMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    val prog = LotsRuntime.progress
                    if (prog != null) {
                        val (id, d, t) = prog
                        Text("Téléchargement : ${id.feature} ${id.scope} — ${if (t > 0) d * 100 / t else 0} %", style = MaterialTheme.typography.bodySmall)
                        LinearProgressIndicator(progress = { if (t > 0) (d.toFloat() / t).coerceIn(0f, 1f) else 0f }, Modifier.fillMaxWidth())
                    } else if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    message?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium) }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Wi-Fi uniquement", style = MaterialTheme.typography.bodyLarge)
                            Text("Ne télécharge pas avec vos données mobiles.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(LotsRuntime.wifiOnly, { LotsRuntime.wifiOnly = it; LotsRuntime.schedule(LotsRuntime.appContext) })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = !busy, onClick = { scope.launch { message = withContext(Dispatchers.IO) { LotsRuntime.syncNow(userAsked = true) } } }) { Text("Tout mettre à jour") }
                        OutlinedButton(enabled = !busy, onClick = { scope.launch { message = withContext(Dispatchers.IO) { LotsRuntime.deliverNow(userAsked = true) ?: "Rien à envoyer à la TV" } } }) { Text("Envoyer à la TV") }
                    }
                    HorizontalDivider()
                    Wizard(busy) { message = it }
                    HorizontalDivider()

                    // ---- lots ----
                    Text("Vos données", style = MaterialTheme.typography.titleMedium)
                    val entries = store.list()
                    val missing = LotsRuntime.needs().map { it.id }.filter { id -> entries.none { it.meta.id == id } }
                    if (entries.isEmpty() && missing.isEmpty()) Text("Aucune donnée téléchargée. Choisissez vos classes ci-dessus.", style = MaterialTheme.typography.bodyMedium)
                    (entries.map { it.meta.id } + missing).forEach { id ->
                        val st = LotsRuntime.status(id)
                        val held = store.get(id)
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(held?.meta?.title ?: "${id.feature} ${id.scope}", style = MaterialTheme.typography.titleSmall)
                                held?.let { Text("Version ${it.meta.version} · ${LotStore.mo(it.meta.bytes)} · mis à jour ${LotStatusText.age(it.installedAt, now)}", style = MaterialTheme.typography.bodySmall) }
                                Text(st.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                Text(st.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(enabled = !busy, onClick = { scope.launch { message = withContext(Dispatchers.IO) { LotsRuntime.syncNow(only = id, userAsked = true) } } }) {
                                        Text(if (held == null) "Télécharger" else "Mettre à jour")
                                    }
                                    if (st.stage == LotStage.REFUSED) TextButton(onClick = { LotsRuntime.retry(id) }) { Text("Réessayer") }
                                }
                            }
                        }
                    }
                    HorizontalDivider()

                    // ---- the TV ----
                    Text("Sur la TV", style = MaterialTheme.typography.titleMedium)
                    Text(LotsRuntime.tvBudgetText(), style = MaterialTheme.typography.bodyMedium)
                    Text("La TV n'a jamais besoin d'Internet : c'est ce téléphone qui lui apporte les données, dès qu'elle est à portée.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    skipped.forEach { s ->
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(LotStatusText.skipped(s), style = MaterialTheme.typography.bodyMedium)
                                s.dropSuggestion.forEach { d -> TextButton({ LotsRuntime.dropFromTv(d) }) { Text("Retirer « ${d.feature} ${d.scope} » de la TV") } }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** First synchronisation: which classes/levels to keep, with the estimated size (the list comes from the last verified catalog). */
@Composable
private fun Wizard(busy: Boolean, onMessage: (String?) -> Unit) {
    val scope = rememberCoroutineScope()
    var picked by remember { mutableStateOf(LotsRuntime.selectedScopes) }
    val cat = LotsRuntime.catalog
    val scopes = cat?.lots?.map { it.id.scope }?.distinct()?.sorted().orEmpty()
    Text(if (LotsRuntime.firstSyncDone) "Classes et niveaux gardés" else "Premier téléchargement : choisissez vos classes", style = MaterialTheme.typography.titleMedium)
    if (scopes.isEmpty()) {
        Text("La liste des classes n'est pas encore connue : connectez le téléphone à Internet puis actualisez.", style = MaterialTheme.typography.bodyMedium)
    } else scopes.forEach { s ->
        val size = LotsRuntime.estimate(setOf(s))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(s in picked, { on -> picked = if (on) picked + s else picked - s })
            Text("${s.uppercase()} · ${LotStore.mo(size)}", Modifier.weight(1f))
        }
    }
    val est = LotsRuntime.estimate(picked)
    if (scopes.isNotEmpty()) Text("Taille estimée : ${LotStore.mo(est)} (maximum ${LotStore.mo(LotBudget.PHONE_MAX_BYTES)})", style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(enabled = !busy, onClick = { scope.launch { onMessage(withContext(Dispatchers.IO) { LotsRuntime.refreshCatalog() } ?: "Liste des classes actualisée") } }) { Text("Actualiser la liste") }
        Button(enabled = !busy && picked.isNotEmpty() && est <= LotBudget.PHONE_MAX_BYTES, onClick = {
            LotsRuntime.selectedScopes = picked
            scope.launch { onMessage(withContext(Dispatchers.IO) { LotsRuntime.syncNow(userAsked = true) }) }
        }) { Text("Télécharger") }
    }
}
