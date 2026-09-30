package castbridge.sender

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import castbridge.core.tv.TvClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One line of the activity log. */
private data class LogLine(val ts: Long, val type: String, val label: String)

private fun parseLog(j: String): List<LogLine> = runCatching {
    val a = org.json.JSONObject(j).optJSONArray("entries") ?: return emptyList()
    (0 until a.length()).map { i ->
        val o = a.getJSONObject(i)
        LogLine(o.optLong("ts"), o.optString("type"), o.optString("label"))
    }
}.getOrDefault(emptyList())

/** Administered from the phone: read the activity log and set the parental rules (docs: control parental). */
@Composable
fun ParentalPanel(client: TvClient) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var ppin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var log by remember { mutableStateOf<List<LogLine>>(emptyList()) }
    var enabled by remember { mutableStateOf(false) }
    var requirePin by remember { mutableStateOf(false) }
    var after by remember { mutableStateOf("22:00") }
    var before by remember { mutableStateOf("06:00") }
    var msg by remember { mutableStateOf<String?>(null) }

    var dailyLimit by remember { mutableStateOf("") }
    var usedMin by remember { mutableStateOf(0) }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        runCatching { client.parentalStatus() }.onSuccess { j -> status = j }.onFailure { e -> error = e.message }
    }
    LaunchedEffect(client) { while (true) { refresh(); delay(5000) } }

    fun setError(e: Throwable) {
        msg = (e as? TvClient.HttpError)?.let { h -> TvClient.str(h.message.orEmpty().substringAfter(": "), "message") ?: TvClient.str(h.message.orEmpty().substringAfter(": "), "error") } ?: e.message
    }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Text("Contrôle parental", style = MaterialTheme.typography.titleSmall)
        // Read the current rules once the status arrives.
        val s = status
        if (s.isNotEmpty() && s.contains("\"enabled\":")) {
            LaunchedEffect(s) {
                runCatching {
                    val o = castbridge.core.quiz.Json.obj(s)
                    enabled = o["enabled"] == true
                    requirePin = o["requirePin"] == true
                    (o["blockedAfter"] as? String)?.let { after = it }
                    (o["blockedBefore"] as? String)?.let { before = it }
                    dailyLimit = ((o["dailyLimit"] as? Number)?.toInt()?.takeIf { it > 0 })?.toString() ?: ""
                    usedMin = (o["usedMinutes"] as? Number)?.toInt() ?: 0
                }
            }
        }
        Text("Le téléphone administre le contrôle parental de la TV : historique d'activité et règles. Code parental requis pour modifier.",
            style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(ppin, { ppin = it }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Code parental actuel (par défaut : 4444)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(newPin, { newPin = it }, Modifier.weight(1f), singleLine = true,
                label = { Text("Nouveau code") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
            Button(onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        runCatching { client.parentalSetPin(ppin, newPin) }
                            .onSuccess { msg = "Code modifié avec succès."; ppin = newPin; newPin = "" }
                            .onFailure { setError(it) }
                    }
                }
            }) { Text("Définir") }
        }

        Spacer(Modifier.height(8.dp))
        Text("Règles", style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(enabled, { enabled = it }); Text("Contrôle parental actif") }
        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(requirePin, { requirePin = it }, enabled = enabled); Text("Demander le code pour lire une vidéo") }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(after, { after = it }, Modifier.width(100.dp), singleLine = true, label = { Text("Bloquer après") }, enabled = enabled)
            OutlinedTextField(before, { before = it }, Modifier.width(100.dp), singleLine = true, label = { Text("jusqu'à") }, enabled = enabled)
            Text("(fenêtre calme)", style = MaterialTheme.typography.bodySmall)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(dailyLimit, { dailyLimit = it }, Modifier.width(100.dp), singleLine = true, label = { Text("Max") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), enabled = enabled)
            Text("minutes par jour (vide = illimité, utilisé = $usedMin min)", style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = {
            scope.launch {
                withContext(Dispatchers.IO) {
                    val limit = dailyLimit.toIntOrNull() ?: 0
                    runCatching { client.parentalSetRestrictions(ppin, enabled, after.takeIf { it.isNotBlank() }, before.takeIf { it.isNotBlank() }, requirePin, limit) }
                        .onSuccess { msg = "Réglages enregistrés." }.onFailure { setError(it) }
                    refresh()
                }
            }
        }) { Text("Enregistrer les réglages") }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                scope.launch { withContext(Dispatchers.IO) { runCatching { client.parentalLog(ppin) }.onSuccess { log = parseLog(it) }.onFailure { setError(it) } } }
            }) { Text("Voir l'historique") }
            OutlinedButton(onClick = {
                scope.launch { withContext(Dispatchers.IO) { runCatching { client.parentalClear(ppin) }.onSuccess { log = emptyList(); msg = "Historique effacé." }.onFailure { setError(it) } } }
            }) { Text("Effacer") }
        }
        Button(onClick = {
            scope.launch { withContext(Dispatchers.IO) { runCatching { client.parentalUnlock(ppin) }.onSuccess { msg = "Lecture déverrouillée pour 2 heures." }.onFailure { setError(it) } } }
        }) { Text("Déverrouiller la lecture") }
        msg?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
        error?.let { Text("TV : $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }

        if (log.isNotEmpty()) {
            Text("Historique (${log.size})", style = MaterialTheme.typography.labelLarge)
            log.asReversed().take(200).forEach { l ->
                Text("${fmtLog(l.ts)}  ${l.label}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun fmtLog(ts: Long): String = runCatching {
    java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.FRENCH).format(java.util.Date(ts))
}.getOrDefault("?")

/** Full-screen dialog wrapping [ParentalPanel], opened from the "CastBridge TV" tab. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentalDialog(client: TvClient, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column {
                TopAppBar(title = { Text("Contrôle parental") },
                    navigationIcon = { IconButton(onDismiss) { Icon(Icons.Filled.ArrowBack, "Retour") } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface))
                Box(Modifier.weight(1f)) { ParentalPanel(client) }
            }
        }
    }
}
