package castbridge.sender

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import castbridge.core.connect.ConsentText
import castbridge.core.connect.ServerLink
import castbridge.core.connect.ServerUrl
import castbridge.core.net.JsonLite
import castbridge.core.telemetry.Consent
import castbridge.core.update.UpdateSchedule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun whenText(ms: Long): String = if (ms <= 0) "jamais" else SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date(ms))

/** Recomposes the caller whenever the server link changed (status, progress, consent). */
@Composable
fun rememberLinkVersion(): Int = PhoneConnect.version.collectAsState().value

/** The consent text of docs/TELEMETRY.md (paragraphs with their titles). */
@Composable
private fun ConsentParagraphs() {
    ConsentText.paragraphs.forEach { (title, text) ->
        if (title != null) Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Information screen of the first launch: nothing is sent to the server before it is answered. */
@Composable
fun ConsentScreen(onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { PhoneConnect.screens.enter("onboarding") }
    fun choose(usage: Boolean) = scope.launch {
        withContext(Dispatchers.IO) { runCatching { PhoneConnect.link.setConsent(usage) } }
        PhoneConnect.agent.post { tick() }
        PhoneConnect.changed()
        onDone()
    }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Icon(Icons.Filled.PrivacyTip, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            Text(ConsentText.TITLE, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            ConsentParagraphs()
        }
        Button(onClick = { choose(true) }, Modifier.fillMaxWidth().padding(top = 12.dp)) { Text(ConsentText.ACCEPT) }
        OutlinedButton(onClick = { choose(false) }, Modifier.fillMaxWidth().padding(top = 6.dp)) { Text(ConsentText.ESSENTIAL_ONLY) }
    }
}

/** A mandatory update (server policy): the app stays behind this screen until it is installed. */
@Composable
fun MandatoryUpdateScreen(u: ServerLink.UpdateStatus) {
    val m = u.manifest
    Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Filled.SystemUpdate, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Text("Mise à jour obligatoire", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text("Cette version de CastBridge n'est plus prise en charge. Installez la version ${m?.versionName ?: ""} pour continuer.",
            style = MaterialTheme.typography.bodyMedium)
        m?.notes?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        UpdateProgress(u)
        PhoneConnect.updater.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Button(onClick = { PhoneConnect.agent.post { if (update.phase == ServerLink.Phase.READY || update.phase == ServerLink.Phase.INSTALLING) offerInstall(true) else checkUpdate(UpdateSchedule.Trigger.USER) } },
            enabled = u.phase != ServerLink.Phase.DOWNLOADING && u.phase != ServerLink.Phase.CHECKING, modifier = Modifier.fillMaxWidth()) {
            Text(if (u.phase == ServerLink.Phase.READY || u.phase == ServerLink.Phase.INSTALLING) "Installer" else "Réessayer")
        }
    }
}

@Composable
private fun UpdateProgress(u: ServerLink.UpdateStatus) {
    Text(u.message.ifEmpty { "—" }, style = MaterialTheme.typography.bodyMedium)
    if (u.phase == ServerLink.Phase.DOWNLOADING && u.total > 0) {
        LinearProgressIndicator({ u.done.toFloat() / u.total }, Modifier.fillMaxWidth())
        Text("${formatSize(u.done)} / ${formatSize(u.total)}", style = MaterialTheme.typography.bodySmall)
    } else if (u.phase == ServerLink.Phase.CHECKING || u.phase == ServerLink.Phase.INSTALLING) LinearProgressIndicator(Modifier.fillMaxWidth())
}

/** True when the app must stay behind [MandatoryUpdateScreen]. */
fun mustUpdate(u: ServerLink.UpdateStatus): Boolean = u.manifest != null && u.mandatory &&
    u.phase in setOf(ServerLink.Phase.DOWNLOADING, ServerLink.Phase.READY, ServerLink.Phase.INSTALLING, ServerLink.Phase.FAILED)

/** « Réglages »: privacy, updates, connection, advanced (server address, the TV's server). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onClose: () -> Unit) {
    val v = rememberLinkVersion()
    LaunchedEffect(Unit) { PhoneConnect.feature("settings"); PhoneConnect.screens.enter("settings") }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(title = { Text("Réglages") }, navigationIcon = { IconButton(onClose) { Icon(Icons.Filled.ArrowBack, "Retour") } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface))
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrivacySection(v)
                    HorizontalDivider()
                    UpdatesSection(v)
                    HorizontalDivider()
                    ConnectionSection(v)
                    HorizontalDivider()
                    AdvancedSection()
                }
            }
        }
    }
}

@Composable
private fun Title(t: String) = Text(t, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

@Composable
private fun Line(k: String, v: String) {
    Column { Text(k, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(v, style = MaterialTheme.typography.bodyMedium) }
}

@Composable
private fun PrivacySection(@Suppress("UNUSED_PARAMETER") v: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val st = PhoneConnect.state
    var reread by remember { mutableStateOf(false) }
    var data by remember { mutableStateOf<String?>(null) }
    var confirmErase by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    Title("Confidentialité")
    val usage = st.consent == Consent.USAGE && !st.needsConsent
    Line("Votre choix", if (st.needsConsent) "pas encore fait" else if (usage) "essentiel + statistiques d'usage" else "seulement l'essentiel" +
        (if (st.consentAt > 0) " (le ${whenText(st.consentAt)})" else ""))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Statistiques d'usage", style = MaterialTheme.typography.bodyLarge)
            Text("Fonctionnalités utilisées, durées, réussite des envois… jamais vos fichiers.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(usage, { v ->
            scope.launch { withContext(Dispatchers.IO) { runCatching { PhoneConnect.link.setConsent(v) } }; PhoneConnect.changed() }
        })
    }
    TextButton(onClick = { reread = true }) { Text("Relire l'information") }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(enabled = !busy, onClick = {
            busy = true; msg = null
            scope.launch {
                val r = withContext(Dispatchers.IO) { runCatching { PhoneConnect.link.myData() } }
                busy = false
                r.onSuccess { data = pretty(it) }.onFailure { msg = "Impossible de lire vos données : ${it.message}" }
            }
        }) { Text("Mes données") }
        OutlinedButton(enabled = !busy, onClick = { confirmErase = true }) { Text("Effacer mes données") }
    }
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    msg?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

    if (reread) AlertDialog(onDismissRequest = { reread = false }, confirmButton = { TextButton({ reread = false }) { Text("Fermer") } },
        title = { Text(ConsentText.TITLE) }, text = { Column(Modifier.verticalScroll(rememberScrollState())) { ConsentParagraphs() } })
    data?.let { d ->
        AlertDialog(onDismissRequest = { data = null }, title = { Text("Mes données sur le serveur") },
            text = { SelectionContainer { Text(d, Modifier.verticalScroll(rememberScrollState()), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) } },
            confirmButton = { TextButton({ data = null }) { Text("Fermer") } },
            dismissButton = { TextButton({
                runCatching { ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, d), "Partager mes données")) }
            }) { Text("Partager") } })
    }
    if (confirmErase) AlertDialog(onDismissRequest = { confirmErase = false },
        title = { Text("Effacer mes données ?") },
        text = { Text("Le serveur supprime la fiche de ce téléphone, son historique et ses statistiques. Ce téléphone reçoit ensuite un nouvel identifiant et l'écran d'information s'affiche à nouveau.") },
        confirmButton = { TextButton({
            confirmErase = false; busy = true
            scope.launch {
                val r = withContext(Dispatchers.IO) { runCatching { PhoneConnect.link.eraseMyData() } }
                busy = false
                msg = r.exceptionOrNull()?.let { "Effacement impossible (réseau ?) : ${it.message}" }
                PhoneConnect.changed()
            }
        }) { Text("Effacer") } },
        dismissButton = { TextButton({ confirmErase = false }) { Text("Annuler") } })
}

private fun pretty(json: String): String = runCatching {
    fun w(v: Any?, ind: String): String = when (v) {
        is Map<*, *> -> if (v.isEmpty()) "{}" else v.entries.joinToString(",\n", "{\n", "\n$ind}") { "$ind  ${JsonLite.quote(it.key.toString())}: ${w(it.value, "$ind  ")}" }
        is List<*> -> if (v.isEmpty()) "[]" else v.joinToString(",\n", "[\n", "\n$ind]") { "$ind  ${w(it, "$ind  ")}" }
        else -> JsonLite.write(v)
    }
    w(JsonLite.parse(json), "")
}.getOrDefault(json)

@Composable
private fun UpdatesSection(@Suppress("UNUSED_PARAMETER") v: Int) {
    LaunchedEffect(Unit) { PhoneConnect.feature("updates", "menu") }
    val u = PhoneConnect.link.update
    val st = PhoneConnect.state
    Title("Mises à jour")
    Line("Version installée", "${PhoneConnect.versionName} (${PhoneConnect.versionCode})")
    Line("Dernière vérification", whenText(st.updateSchedule.lastCheckAt))
    UpdateProgress(u)
    PhoneConnect.updater.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    u.manifest?.let { m ->
        Line("Version disponible", "${m.versionName} (${m.versionCode})" + if (u.mandatory) " — obligatoire" else "")
        if (m.notes.isNotBlank()) Text(m.notes, style = MaterialTheme.typography.bodySmall)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(enabled = !st.needsConsent && u.phase != ServerLink.Phase.CHECKING && u.phase != ServerLink.Phase.DOWNLOADING,
            onClick = { PhoneConnect.agent.post { checkUpdate(UpdateSchedule.Trigger.USER) } }) { Text("Vérifier maintenant") }
        if (u.file != null && (u.phase == ServerLink.Phase.READY || u.phase == ServerLink.Phase.INSTALLING))
            Button(onClick = { PhoneConnect.agent.post { offerInstall(true) } }) { Text("Installer") }
    }
}

@Composable
private fun ConnectionSection(@Suppress("UNUSED_PARAMETER") v: Int) {
    val st = PhoneConnect.state
    Title("Connexion")
    Line("Identifiant de l'appareil (pour le retrouver dans l'administration)", st.shortId ?: "pas encore enregistré")
    Line("Serveur", st.baseUrl + if (st.customServer) " (modifié)" else "")
    Line("Dernier contact", whenText(st.lastContactAt) + (st.lastContactMessage?.let { " — $it" } ?: ""))
    if (st.blocked) Text("Appareil bloqué par l'administrateur : pas de mise à jour.", color = MaterialTheme.colorScheme.error)
    if (st.channel == "beta") Line("Canal", "bêta (choisi par l'administrateur)")
}

@Composable
private fun AdvancedSection() {
    var open by remember { mutableStateOf(false) }
    ListItem(modifier = Modifier.fillMaxWidth(), headlineContent = { Text("Avancé") },
        supportingContent = { Text("Adresse du serveur, serveur de la TV") },
        trailingContent = { IconButton({ open = !open }) { Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null) } },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent))
    if (!open) return
    val st = PhoneConnect.state
    var url by remember { mutableStateOf(st.baseUrl) }
    var msg by remember { mutableStateOf<String?>(null) }
    OutlinedTextField(url, { url = it.trim() }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Adresse du serveur (HTTPS)") })
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = {
            msg = try { st.baseUrl = url; url = st.baseUrl; PhoneConnect.agent.post { tick() }; "Enregistré" } catch (e: IllegalArgumentException) { e.message }
            PhoneConnect.changed()
        }) { Text("Enregistrer") }
        OutlinedButton(onClick = { st.baseUrl = ServerUrl.DEFAULT; url = ServerUrl.DEFAULT; msg = "Serveur officiel"; PhoneConnect.agent.post { tick() }; PhoneConnect.changed() }) {
            Text("Serveur officiel")
        }
    }
    msg?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    HorizontalDivider()
    TvServerPanel()
}

/** The TV of the home tab (name + PIN remembered by the first-connection assistant), found again on the network. */
private fun homeTvName(ctx: Context): String? = ctx.getSharedPreferences("castbridge_home", Context.MODE_PRIVATE).getString("tv", null)

/** PIN-protected call to the TV's /api/server routes (same header as TvClient). */
private fun tvCall(base: String, pin: String, method: String, path: String): String {
    val c = URL(base + path).openConnection() as HttpURLConnection
    try {
        c.requestMethod = method; c.connectTimeout = 4000; c.readTimeout = 30_000
        c.setRequestProperty("X-CB-Pin", pin)
        if (method == "POST") { c.doOutput = true; c.setFixedLengthStreamingMode(0); c.outputStream.close() }
        val code = c.responseCode
        val text = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code >= 400) throw java.io.IOException(runCatching { JsonLite.obj(text)["error"] as? String ?: JsonLite.obj(text)["message"] as? String }.getOrNull() ?: "HTTP $code")
        return text
    } finally { c.disconnect() }
}

@Composable
private fun TvServerPanel() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val name = remember { homeTvName(ctx) }
    val pin = remember(name) { PinStore(ctx).get(name) }
    val discovery = remember { TvDiscovery(ctx) }
    DisposableEffect(Unit) { discovery.start(); onDispose { discovery.stop() } }
    val tvs by discovery.tvs.collectAsState()
    val tv = tvs.firstOrNull { it.name == name }
    var info by remember { mutableStateOf<Map<String, Any?>?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var url by remember { mutableStateOf("") }
    Title("Serveur de la TV")
    if (name == null || pin.isEmpty()) { Text("Aucune TV choisie : connectez d'abord une TV dans l'onglet « CastBridge TV ».", style = MaterialTheme.typography.bodySmall); return }
    if (tv == null) { Text("Recherche de « $name » sur le réseau…", style = MaterialTheme.typography.bodySmall); return }
    fun load() = scope.launch {
        withContext(Dispatchers.IO) { runCatching { JsonLite.obj(tvCall(tv.base, pin, "GET", "/api/server")) } }
            .onSuccess { info = it; if (url.isEmpty()) url = it["baseUrl"] as? String ?: "" }
            .onFailure { msg = "La TV ne répond pas (${it.message}) : version trop ancienne ?" }
    }
    fun act(path: String, done: String) = scope.launch {
        msg = "…"
        msg = withContext(Dispatchers.IO) { runCatching { tvCall(tv.base, pin, "POST", path) } }
            .fold({ done }, { "Refusé : ${it.message}" })
        load()
    }
    LaunchedEffect(tv.base) { load() }
    info?.let { i ->
        @Suppress("UNCHECKED_CAST") val up = i["update"] as? Map<String, Any?>
        @Suppress("UNCHECKED_CAST") val quiz = i["quiz"] as? Map<String, Any?>
        Line("TV", "${tv.name.removePrefix("CastBridge TV ")} — version ${i["versionName"] ?: "?"}")
        Line("Identifiant de la TV", i["shortId"] as? String ?: "pas encore enregistrée")
        Line("Serveur utilisé par la TV", "${i["baseUrl"]}" + if (i["customServer"] == true) " (modifié)" else "")
        Line("Dernier contact", whenText((i["lastContactAt"] as? Number)?.toLong() ?: 0) + ((i["lastContactMessage"] as? String)?.let { " — $it" } ?: ""))
        if (i["needsConsent"] == true) Text("La TV attend encore la réponse à l'écran d'information (sur la TV).", style = MaterialTheme.typography.bodySmall)
        up?.let { Line("Mises à jour de la TV", (it["message"] as? String).orEmpty().ifEmpty { "—" }) }
        quiz?.let { Line("Questions du quiz", ((it["message"] as? String) ?: "—") + " · ${(it["serverQuestions"] as? Number)?.toInt() ?: 0} du serveur") }
    }
    OutlinedTextField(url, { url = it.trim() }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Adresse du serveur pour la TV (vide = officiel)") })
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = {
            val problem = url.takeIf { it.isNotEmpty() }?.let { ServerUrl.problem(it) }
            if (problem != null) msg = problem else act("/api/server/url?url=" + URLEncoder.encode(url, "UTF-8"), "Adresse enregistrée sur la TV")
        }) { Text("Enregistrer sur la TV") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { act("/api/server/check-update", "Vérification lancée sur la TV") }) { Text("Vérifier les mises à jour de la TV") }
    }
    OutlinedButton(onClick = { act("/api/server/quiz-sync", "Mise à jour des questions lancée sur la TV") }) { Text("Mettre à jour les questions du quiz de la TV") }
    msg?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
}
