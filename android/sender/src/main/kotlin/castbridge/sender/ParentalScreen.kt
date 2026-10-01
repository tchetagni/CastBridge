package castbridge.sender

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import castbridge.core.parental.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * « Contrôle parental » in the phone's Réglages: the same rules as on the TV, piloted through the TV's API (TV PIN in the header,
 * parental PIN in the request body only, never in a URL). The parental PIN is kept in memory while this screen is open and forgotten
 * afterwards. The activity report is fetched from the TV and shown here: nothing goes to a server (docs/PARENTAL.md).
 */
@Composable
fun ParentalSection() {
    val ctx = LocalContext.current
    Text("Contrôle parental", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Text("Vidéos adaptées à l'âge, horaires, temps d'écran, rapport d'activité : un écran dédié.", style = MaterialTheme.typography.bodySmall)
    OutlinedButton({ ParentalActivity.open(ctx) }) { Text("Ouvrir le contrôle parental") }
}

/** The dedicated screen (see [ParentalActivity]): the TV's rules, behind the parental PIN. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentalScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Contrôle parental") }, navigationIcon = { IconButton(onClose) { Icon(Icons.Filled.ArrowBack, "Retour") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ParentalTvPicker(ctx) { client -> ParentalPanel(client) }
        }
    }
}

private fun savedAddress(ctx: Context) = ctx.getSharedPreferences("castbridge_parental_phone", Context.MODE_PRIVATE)

/** The TV of the home tab (found on the network), or an address typed by hand (emulators, « ma TV n'apparaît pas »). */
@Composable
internal fun ParentalTvPicker(ctx: Context, content: @Composable (ParentalClient) -> Unit) {
    // The TV this phone is already linked to (plug and play): its address and token, no discovery and no PIN to type.
    val link by TvLinkManager.state.collectAsState()
    (link as? LinkUi.Connected)?.session?.let { s ->
        val b = s.base
        if (b != null && castbridge.core.trust.TvAuth.isUsable(s.credential)) {
            val client = remember(b, s.credential) { ParentalClient(b, s.credential) }
            content(client); return
        }
        if (b == null) {
            Text("${s.tv.name} est joignable par Bluetooth seulement : le contrôle parental a besoin du Wi-Fi de la maison. Connectez le téléphone et la TV au même Wi-Fi.",
                color = MaterialTheme.colorScheme.error)
            return
        }
    }
    val name = remember { ctx.getSharedPreferences("castbridge_home", Context.MODE_PRIVATE).getString("tv", null) }
    val pins = remember { PinStore(ctx) }
    val discovery = remember { TvDiscovery(ctx) }
    DisposableEffect(Unit) { discovery.start(); onDispose { discovery.stop() } }
    val tvs by discovery.tvs.collectAsState()
    val found = tvs.firstOrNull { it.name == name } ?: tvs.firstOrNull()
    var manual by remember { mutableStateOf(savedAddress(ctx).getString("addr", "") ?: "") }
    var manualPin by remember { mutableStateOf("") }
    var useManual by remember { mutableStateOf(false) }
    var base: String? = null; var pin = ""
    if (found != null && !useManual) { base = found.base; pin = pins.get(found.name) }
    else if (useManual && manual.isNotBlank()) { base = "http://" + manual.trim().removePrefix("http://").let { if (it.contains(':')) it else "$it:8765" }; pin = manualPin }
    if (found == null || useManual) {
        Text(if (found == null) "Recherche de la TV sur le Wi-Fi… Si elle n'apparaît pas, tapez son adresse (affichée dans « Connexion & réglages » sur la TV)." else "Adresse de la TV tapée à la main.",
            style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(manual, { manual = it.trim(); savedAddress(ctx).edit().putString("addr", manual).apply() }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Adresse de la TV (exemple 192.168.1.20)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
        if (manual.isNotBlank()) {
            useManual = true
            OutlinedTextField(manualPin, { manualPin = it.filter { c -> c.isDigit() }.take(6) }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Code de connexion de la TV (6 chiffres)") }, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
        }
    }
    if (found != null && pin.isEmpty() && !useManual) { Text("Ajoutez d'abord la TV dans l'onglet « CastBridge TV » (bouton « Ajouter ma TV »), ou entrez son code de connexion.", color = MaterialTheme.colorScheme.error); return }
    if (base == null || !castbridge.core.trust.TvAuth.isUsable(pin)) return
    val client = remember(base, pin) { ParentalClient(base, pin) }
    content(client)
}

/** Runs a call off the main thread and turns a failure into the TV's own French words. */
internal suspend fun <T> io(f: () -> T): Result<T> = withContext(Dispatchers.IO) { runCatching(f) }

internal fun why(e: Throwable): String = when {
    e is ParentalError && e.code == 404 -> "Cette TV n'a pas encore le contrôle parental : mettez à jour l'app CastBridge TV."
    e is ParentalError -> e.message.orEmpty()
    else -> castbridge.core.trust.LinkText.failure(e)       // a French sentence, never the exception text
}

@Composable
internal fun ParentalPanel(client: ParentalClient, initialPin: String? = null) {
    val scope = rememberCoroutineScope()
    var status by remember(client) { mutableStateOf<Map<String, Any?>?>(null) }
    var msg by remember(client) { mutableStateOf<String?>(null) }
    var pin by remember(client) { mutableStateOf("") }            // parental PIN, memory only
    var entry by remember(client) { mutableStateOf("") }
    var loaded by remember(client) { mutableStateOf<ParentalClient.Loaded?>(null) }
    var busy by remember(client) { mutableStateOf(false) }
    var newPin1 by remember { mutableStateOf("") }
    var newPin2 by remember { mutableStateOf("") }

    fun reload() = scope.launch { io { client.status() }.onSuccess { status = it; msg = null }.onFailure { msg = why(it) } }
    LaunchedEffect(client) { reload() }
    // opened from the Parental tab: the PIN of the session is used, the parent is not asked twice
    LaunchedEffect(client) { if (initialPin != null) io { client.load(initialPin) }.onSuccess { loaded = it; pin = initialPin } }

    msg?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    val s = status ?: run { if (msg == null) LinearProgressIndicator(Modifier.fillMaxWidth()); return }

    if (s["pinSet"] != true) {
        Text("Protégez les enfants : vidéos adaptées à leur âge, horaires, temps d'écran, écrans réservés aux parents. Rien n'est bloqué tant que vous n'avez pas créé de code parental (4 à 6 chiffres, différent du code de la TV).",
            style = MaterialTheme.typography.bodySmall)
        PinInput(newPin1, { newPin1 = it }, "Nouveau code parental")
        PinInput(newPin2, { newPin2 = it }, "Confirmez le code")
        Button(enabled = !busy && newPin1.isNotEmpty(), onClick = {
            if (newPin1 != newPin2) { msg = "Les deux codes sont différents."; return@Button }
            busy = true
            scope.launch {
                io { client.createPin(newPin1) }.onSuccess { pin = newPin1; newPin1 = ""; newPin2 = ""; msg = null }.onFailure { msg = why(it) }
                busy = false; reload()
                io { client.load(pin) }.onSuccess { loaded = it }
            }
        }) { Text("Créer le code parental") }
        Text("Si vous oubliez ce code, seul l'administrateur de la TV (code de connexion) peut l'effacer, ou l'effacement des données de l'app TV.", style = MaterialTheme.typography.bodySmall)
        return
    }

    val l = loaded
    if (l == null) {
        Text("Saisissez le code parental pour voir et modifier les règles.", style = MaterialTheme.typography.bodySmall)
        PinInput(entry, { entry = it }, "Code parental")
        Button(enabled = !busy && entry.isNotEmpty(), onClick = {
            busy = true
            scope.launch {
                io { client.load(entry) }.onSuccess { loaded = it; pin = entry; entry = ""; msg = null }
                    .onFailure { e -> entry = ""; msg = if (e is ParentalError && e.code == 429) "Trop d'essais : code bloqué, réessayez dans ${e.retryAfter ?: 60} s." else why(e) }
                busy = false
            }
        }) { Text("Ouvrir") }
        AdminZone(client, scope) { msg = it; reload() }
        return
    }

    Editor(client, l, pin, scope, onMsg = { msg = it }, onReload = { reload() }, onLoaded = { loaded = it }, onForget = { loaded = null; pin = "" })
    AdminZone(client, scope) { msg = it; loaded = null; pin = ""; reload() }
}

@Composable
internal fun PinInput(v: String, onV: (String) -> Unit, label: String) =
    OutlinedTextField(v, { onV(it.filter { c -> c.isDigit() }.take(ParentalPins.MAX)) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(label) },
        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))

@Composable
internal fun Picker(label: String, options: List<String>, selected: Int, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, Modifier.fillMaxWidth()) { Text("$label : ${options.getOrNull(selected) ?: "—"}", maxLines = 1) }
        DropdownMenu(open, { open = false }) { options.forEachIndexed { i, o -> DropdownMenuItem(text = { Text(o) }, onClick = { open = false; onPick(i) }) } }
    }
}

@Composable
internal fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) =
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Checkbox(checked, onChange); Text(label, style = MaterialTheme.typography.bodyMedium) }

@Composable
internal fun SwitchRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) =
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.bodyLarge); sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        Switch(checked, onChange)
    }

private val TIMES = (0 until 48).map { it * 30 }

@Composable
private fun Editor(client: ParentalClient, loaded: ParentalClient.Loaded, pin: String, scope: kotlinx.coroutines.CoroutineScope,
                   onMsg: (String?) -> Unit, onReload: () -> Unit, onLoaded: (ParentalClient.Loaded) -> Unit, onForget: () -> Unit) {
    var cfg by remember(loaded) { mutableStateOf(loaded.config) }
    var dirty by remember(loaded) { mutableStateOf(false) }
    var report by remember { mutableStateOf<Map<String, Any?>?>(null) }
    var videos by remember { mutableStateOf<List<Triple<String, String, String?>>>(emptyList()) }
    var newName by remember { mutableStateOf("") }
    var keyword by remember { mutableStateOf("") }
    var changePin by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    var remaining by remember { mutableStateOf((loaded.status["sessionLeftSec"] as? Number)?.toLong() ?: 0L) }
    fun edit(f: (ParentalConfig) -> ParentalConfig) { cfg = f(cfg); dirty = true }
    fun editProfile(id: String, f: (ChildProfile) -> ChildProfile) = edit { c -> c.copy(profiles = c.profiles.map { if (it.id == id) f(it) else it }) }

    LaunchedEffect(Unit) {
        io { client.report(pin) }.onSuccess { report = it }
    }

    SwitchRow("Contrôle parental activé", if (cfg.enabled) "Les règles s'appliquent au profil actif." else "Rien n'est bloqué pour l'instant.", cfg.enabled) { v ->
        if (v && cfg.activeProfile == null) onMsg("Choisissez d'abord le profil actif (et créez-en un si besoin).") else edit { it.copy(enabled = v) }
    }
    val ps = cfg.profiles
    Picker("Profil actif", ps.map { "${it.name} (${it.age.label})" } + "Aucun", ps.indexOfFirst { it.id == cfg.activeProfile }.let { if (it < 0) ps.size else it }) { i -> edit { it.copy(activeProfile = ps.getOrNull(i)?.id) } }

    Text("Profils des enfants", style = MaterialTheme.typography.titleSmall)
    ps.forEach { p ->
        var open by remember(p.id) { mutableStateOf(false) }
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name + if (p.id == cfg.activeProfile) "  (actif)" else "", style = MaterialTheme.typography.titleSmall)
                        Text(listOf(p.age.label, p.window?.let { "de ${it.text()}" } ?: "toute la journée", if (p.dailyLimitMin > 0) "${p.dailyLimitMin} min/jour" else "sans limite").joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton({ open = !open }) { Text(if (open) "Fermer" else "Modifier") }
                }
                if (open) {
                    OutlinedTextField(p.name, { n -> editProfile(p.id) { it.copy(name = n.take(ParentalConfig.MAX_NAME)) } }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Prénom") })
                    Picker("Tranche d'âge", AgeBand.values().map { it.label }, p.age.ordinal) { i -> editProfile(p.id) { it.copy(age = AgeBand.values()[i]) } }
                    SwitchRow("Mode enfant", "Accueil simplifié sur la TV, quitté seulement avec le code.", p.kidMode) { v -> editProfile(p.id) { it.copy(kidMode = v) } }
                    val from = p.window?.fromMin; val to = p.window?.toMin
                    Picker("Autorisé à partir de", listOf("Toute la journée") + TIMES.map { TimeWindow.fmt(it) }, if (from == null) 0 else TIMES.indexOf(from) + 1) { i ->
                        editProfile(p.id) { if (i == 0) it.copy(window = null) else it.copy(window = TimeWindow(TIMES[i - 1], (it.window?.toMin ?: 1200).let { t -> if (t == TIMES[i - 1]) (t + 30) % 1440 else t })) }
                    }
                    if (from != null && to != null) Picker("…jusqu'à", TIMES.map { TimeWindow.fmt(it) }, TIMES.indexOf(to)) { i ->
                        if (TIMES[i] != from) editProfile(p.id) { it.copy(window = TimeWindow(from, TIMES[i])) } else onMsg("L'heure de fin doit être différente du début.")
                    }
                    Picker("Durée par jour", ParentalConfig.LIMIT_CHOICES.map { if (it == 0) "Sans limite" else "$it min" }, ParentalConfig.LIMIT_CHOICES.indexOf(p.dailyLimitMin).coerceAtLeast(0)) { i ->
                        editProfile(p.id) { it.copy(dailyLimitMin = ParentalConfig.LIMIT_CHOICES[i]) }
                    }
                    Text("Le temps et les heures comptent pour :", style = MaterialTheme.typography.labelMedium)
                    UseKind.values().forEach { k -> CheckRow(k.label, k in p.kinds) { on -> editProfile(p.id) { it.copy(kinds = if (on) it.kinds + k else it.kinds - k) } } }
                    Text("Écrans bloqués :", style = MaterialTheme.typography.labelMedium)
                    Category.BLOCKABLE.forEach { c -> CheckRow(c.label, c in p.blocked) { on -> editProfile(p.id) { it.copy(blocked = if (on) it.blocked + c else it.blocked - c) } } }
                    OutlinedButton({ confirmDelete = p.id }) { Text("Supprimer ce profil") }
                }
            }
        }
    }
    if (ps.size < ParentalConfig.MAX_PROFILES) {
        OutlinedTextField(newName, { newName = it.take(ParentalConfig.MAX_NAME) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Prénom du nouveau profil") })
        OutlinedButton(enabled = newName.isNotBlank(), onClick = {
            val id = (1..99).map { "c$it" }.first { id -> ps.none { it.id == id } }
            edit { it.copy(profiles = it.profiles + ChildProfile(id, newName.trim()), activeProfile = it.activeProfile ?: id) }; newName = ""
        }) { Text("Ajouter le profil") }
        val students = loaded.learnProfiles.filter { s -> ps.none { it.learnId == s.first } }
        if (students.isNotEmpty()) OutlinedButton(onClick = {
            edit { c ->
                var list = c.profiles
                for ((lid, name, level) in students) if (list.size < ParentalConfig.MAX_PROFILES)
                    list = list + ChildProfile((1..99).map { "c$it" }.first { id -> list.none { it.id == id } }, name.take(ParentalConfig.MAX_NAME), AgeBand.guessFromLevel(level), learnId = lid)
                c.copy(profiles = list, activeProfile = c.activeProfile ?: list.firstOrNull()?.id)
            }
        }) { Text("Importer les élèves d'Apprendre (${students.size})") }
    }

    HorizontalDivider()
    Text("Classement des vidéos", style = MaterialTheme.typography.titleSmall)
    Text("Une vidéo non classée vaut : ${cfg.unrated.label}. Tous publics, -12, -16 ou adulte.", style = MaterialTheme.typography.bodySmall)
    SwitchRow("Vidéos non classées : adulte", "Activé : une vidéo non classée est réservée aux adultes (recommandé).", cfg.unrated == Rating.ADULT) { v -> edit { it.copy(unrated = if (v) Rating.ADULT else Rating.ALL) } }
    SwitchRow("Vidéo trop âgée : verrouiller", "Activé : visible mais demande le code. Désactivé : masquée de la bibliothèque.", cfg.overAge == OverAge.LOCK) { v -> edit { it.copy(overAge = if (v) OverAge.LOCK else OverAge.HIDE) } }
    cfg.rules.filter { it.kind != RuleKind.FILE }.forEach { r ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${r.kind.label} « ${r.match} » : ${r.rating.label}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton({ edit { c -> c.copy(rules = c.rules - r) } }) { Text("Retirer") }
        }
    }
    OutlinedTextField(keyword, { keyword = it.take(ParentalConfig.MAX_MATCH) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Mot-clé dans le nom d'une vidéo (exemple : horreur)") })
    if (keyword.isNotBlank()) Picker("Classer ce mot-clé", Rating.values().map { it.label }, -1) { i ->
        edit { c -> c.copy(rules = c.rules.filterNot { it.kind == RuleKind.KEYWORD && it.match.equals(keyword.trim(), true) } + RatingRule(RuleKind.KEYWORD, keyword.trim(), Rating.values()[i])) }; keyword = ""
    }
    LaunchedEffect(client) { io { client.videos() }.onSuccess { videos = it } }
    videos.forEach { (name, title, vol) ->
        val r = ParentalRules.ratingOf(cfg, name, vol)
        Picker(title, Rating.values().map { it.label }, r.ordinal) { i ->
            edit { c -> c.copy(rules = c.rules.filterNot { it.kind == RuleKind.FILE && it.match.equals(name, true) } + RatingRule(RuleKind.FILE, name, Rating.values()[i])) }
        }
    }

    Button(enabled = dirty, onClick = {
        scope.launch {
            io { client.save(pin, cfg) }.onSuccess { saved -> onMsg("Enregistré sur la TV."); dirty = false; onLoaded(loaded.copy(config = saved)) }
                .onFailure { e -> onMsg(why(e)); if (e is ParentalError && e.code == 409) io { client.load(pin) }.onSuccess { onLoaded(it) } }
        }
    }) { Text(if (dirty) "Enregistrer sur la TV" else "Enregistré") }

    HorizontalDivider()
    Text("TV déverrouillée", style = MaterialTheme.typography.titleSmall)
    Text("Suspend les règles sur la TV le temps d'une séance des parents (${cfg.sessionMin} minutes).", style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton({ scope.launch { io { client.unlock(pin) }.onSuccess { remaining = (cfg.sessionMin * 60).toLong(); onMsg("TV déverrouillée ${cfg.sessionMin} min.") }.onFailure { onMsg(why(it)) } } }) { Text("Déverrouiller la TV") }
        OutlinedButton({ scope.launch { io { client.lock() }.onSuccess { remaining = 0; onMsg("Règles de nouveau actives sur la TV.") }.onFailure { onMsg(why(it)) } } }) { Text("Reverrouiller") }
    }

    HorizontalDivider()
    Text("Rapport d'activité", style = MaterialTheme.typography.titleSmall)
    Text("Temps passé par profil et contenus bloqués. Ces informations restent sur la TV et sur ce téléphone, rien n'est envoyé à un serveur.", style = MaterialTheme.typography.bodySmall)
    ReportView(report)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton({ scope.launch { io { client.report(pin) }.onSuccess { report = it }.onFailure { onMsg(why(it)) } } }) { Text("Actualiser") }
        OutlinedButton({ scope.launch { io { client.clearHistory(pin) }.onSuccess { onMsg("Historique effacé."); report = null }.onFailure { onMsg(why(it)) } } }) { Text("Effacer l'historique") }
    }

    HorizontalDivider()
    WholeTvPanel(client, cfg.profiles, pin, scope, onMsg)

    HorizontalDivider()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton({ changePin = true }) { Text("Changer le code parental") }
        TextButton(onForget) { Text("Verrouiller cet écran") }
    }

    confirmDelete?.let { id ->
        AlertDialog(onDismissRequest = { confirmDelete = null }, title = { Text("Supprimer ce profil ?") }, text = { Text("Ses règles seront effacées (après « Enregistrer sur la TV »).") },
            confirmButton = { TextButton({ edit { c -> c.copy(profiles = c.profiles.filterNot { it.id == id }, activeProfile = if (c.activeProfile == id) null else c.activeProfile, enabled = c.enabled && c.activeProfile != id) }; confirmDelete = null }) { Text("Supprimer") } },
            dismissButton = { TextButton({ confirmDelete = null }) { Text("Annuler") } })
    }
    if (changePin) {
        var a by remember { mutableStateOf("") }; var b by remember { mutableStateOf("") }; var c2 by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { changePin = false }, title = { Text("Changer le code parental") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { PinInput(a, { a = it }, "Code actuel"); PinInput(b, { b = it }, "Nouveau code"); PinInput(c2, { c2 = it }, "Confirmez le nouveau code") } },
            confirmButton = { TextButton({
                if (b != c2) { onMsg("Les deux nouveaux codes sont différents."); return@TextButton }
                scope.launch { io { client.changePin(a, b) }.onSuccess { onMsg("Code parental changé : saisissez-le à nouveau."); changePin = false; onForget() }.onFailure { onMsg(why(it)) } }
            }) { Text("Changer") } },
            dismissButton = { TextButton({ changePin = false }) { Text("Annuler") } })
    }
}

@Composable
private fun ReportView(r: Map<String, Any?>?) {
    if (r == null) { Text("Pas encore de rapport.", style = MaterialTheme.typography.bodySmall); return }
    @Suppress("UNCHECKED_CAST") val days = r["days"] as? List<Map<String, Any?>> ?: emptyList()
    @Suppress("UNCHECKED_CAST") val blocked = r["blocked"] as? List<Map<String, Any?>> ?: emptyList()
    if (days.isEmpty()) Text("Aucune activité enregistrée.", style = MaterialTheme.typography.bodySmall)
    days.forEach { d ->
        Text(d["day"].toString(), style = MaterialTheme.typography.labelMedium)
        @Suppress("UNCHECKED_CAST") (d["profiles"] as? List<Map<String, Any?>>).orEmpty().forEach { p ->
            Text("${p["name"]} : lecture ${p["play"]} min · jeux ${p["games"]} min · téléchargements ${p["downloads"]} min · autres applications ${p["apps"] ?: 0} min", style = MaterialTheme.typography.bodyMedium)
            @Suppress("UNCHECKED_CAST") (p["byApp"] as? List<Map<String, Any?>>).orEmpty().take(6).forEach { a ->
                Text("   ${a["label"]} : ${a["min"]} min", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    @Suppress("UNCHECKED_CAST") (r["supervision"] as? Map<String, Any?>)?.let { Text(it["label"].toString(), style = MaterialTheme.typography.bodySmall) }
    @Suppress("UNCHECKED_CAST") (r["tamper"] as? List<Map<String, Any?>>).orEmpty().take(3).forEach { t ->
        Text("⚠ ${t["what"]}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    if (blocked.isNotEmpty()) {
        Text("Contenus bloqués récemment", style = MaterialTheme.typography.labelMedium)
        val f = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.FRANCE)
        blocked.take(10).forEach { b -> Text("• ${f.format(java.util.Date((b["ts"] as Number).toLong()))} ${b["what"]}", style = MaterialTheme.typography.bodySmall) }
    }
}

/** What only the TV's administrator (TV PIN) can do, without the parental PIN. */
@Composable
private fun AdminZone(client: ParentalClient, scope: kotlinx.coroutines.CoroutineScope, onDone: (String) -> Unit) {
    var dlg by remember { mutableStateOf<String?>(null) }
    HorizontalDivider()
    Text("Administrateur de la TV", style = MaterialTheme.typography.titleSmall)
    Text("Avec le code de la TV, sans le code parental : désactiver le contrôle, ou effacer le code parental oublié.", style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton({ dlg = "disable" }) { Text("Désactiver le contrôle") }
        OutlinedButton({ dlg = "reset" }) { Text("Code oublié…") }
    }
    dlg?.let { what ->
        val reset = what == "reset"
        AlertDialog(onDismissRequest = { dlg = null }, title = { Text(if (reset) "Effacer le code parental ?" else "Désactiver le contrôle ?") },
            text = { Text(if (reset) "Le code parental sera EFFACÉ et le contrôle DÉSACTIVÉ. Les profils et les règles sont gardés ; il faudra créer un nouveau code pour réactiver. Cette action est réservée à l'administrateur de la TV."
                else "Les règles ne s'appliquent plus tant que vous ne réactivez pas le contrôle. Le code parental est gardé.") },
            confirmButton = { TextButton({
                dlg = null
                scope.launch { io { if (reset) client.reset() else client.disable() }.onSuccess { onDone(if (reset) "Code parental effacé, contrôle désactivé." else "Contrôle désactivé.") }.onFailure { onDone(why(it)) } }
            }) { Text(if (reset) "Effacer" else "Désactiver") } },
            dismissButton = { TextButton({ dlg = null }) { Text("Annuler") } })
    }
}
