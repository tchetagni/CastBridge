package castbridge.sender

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import castbridge.core.parental.ParentalError
import castbridge.core.parental.SupervisionState
import castbridge.core.parental.tab.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * The « Parental » tab of the phone (docs/PARENTAL.md, « Onglet Parental »): everything the TVs reported, on the phone, offline-first.
 * Behind the parental PIN (asked once per session, kept in memory only, auto-lock after a few minutes in the background) and hidden from screenshots.
 * The same content opens from the padlock of the app bar ([ParentalActivity]).
 */
@Composable
fun ParentalTab(onClose: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val act = ctx as? Activity
    DisposableEffect(Unit) {
        act?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (act !is ParentalActivity) act?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    if (ParentalSession.open) ParentalHome(onClose) else ParentalGate(onClose)
}

// ---------------------------------------------------------------------------------------------------------------- gate

@Composable
private fun ParentalGate(onClose: (() -> Unit)?) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            onClose?.let { IconButton(it) { Icon(Icons.Filled.ArrowBack, "Retour") } }
            Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp))
            Text("Contrôle parental", style = MaterialTheme.typography.titleLarge)
        }
        Text("Toute l'activité de la TV, détaillée, sur ce téléphone. Les données restent sur la TV et sur ce téléphone : rien n'est envoyé sur Internet. Le code parental est demandé une fois, puis l'onglet se reverrouille après quelques minutes hors de l'app.",
            style = MaterialTheme.typography.bodySmall)
        ParentalTvPicker(ctx) { client -> GateWithTv(client) }
        DeviceLockEntry()
    }
}

@Composable
private fun GateWithTv(client: castbridge.core.parental.ParentalClient) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var status by remember(client) { mutableStateOf<Map<String, Any?>?>(null) }
    var msg by remember(client) { mutableStateOf<String?>(null) }
    var entry by remember { mutableStateOf("") }
    var pin1 by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(client) { io { client.status() }.onSuccess { status = it; msg = null }.onFailure { msg = why(it) } }
    msg?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    val s = status ?: run { if (msg == null) LinearProgressIndicator(Modifier.fillMaxWidth()); return }

    fun open(pin: String) {
        busy = true
        scope.launch {
            io { client.load(pin) }.onSuccess { l -> ParentalData.ledger(ctx).markProfiles(tvNameOf(ctx), l.config.profiles.map { it.id }); ParentalSession.unlockWithPin(pin); msg = null }
                .onFailure { e -> msg = if (e is ParentalError && e.code == 429) "Trop d'essais : code bloqué, réessayez dans ${e.retryAfter ?: 60} s." else why(e) }
            entry = ""; busy = false
        }
    }

    if (s["pinSet"] != true) {
        Text("Aucun code parental n'existe encore. Créez-le (4 à 6 chiffres, différent du code de la TV) : rien n'est bloqué ni détaillé tant qu'il n'existe pas.", style = MaterialTheme.typography.bodyMedium)
        PinInput(pin1, { pin1 = it }, "Nouveau code parental")
        PinInput(pin2, { pin2 = it }, "Confirmez le code")
        Button(enabled = !busy && pin1.isNotEmpty(), onClick = {
            if (pin1 != pin2) { msg = "Les deux codes sont différents."; return@Button }
            busy = true
            scope.launch {
                val p = pin1
                io { client.createPin(p) }.onSuccess { pin1 = ""; pin2 = ""; open(p) }.onFailure { msg = why(it) }
                busy = false
            }
        }) { Text("Créer le code parental") }
        Text("Si vous oubliez ce code, seul l'administrateur de la TV peut l'effacer.", style = MaterialTheme.typography.bodySmall)
        return
    }
    PinInput(entry, { entry = it }, "Code parental")
    Button(enabled = !busy && entry.isNotEmpty(), onClick = { open(entry) }) { Text("Ouvrir") }
}

/** TV unreachable (off, out of Wi-Fi): the TV cannot check the PIN, so the phone's own screen lock opens the figures in read-only mode. */
@Composable
private fun DeviceLockEntry() {
    val ctx = LocalContext.current
    val km = remember { ctx.getSystemService(KeyguardManager::class.java) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r -> if (r.resultCode == Activity.RESULT_OK) ParentalSession.unlockReadOnly() }
    HorizontalDivider()
    Text("TV éteinte ou hors de portée ?", style = MaterialTheme.typography.titleSmall)
    Text("Le code parental est vérifié par la TV. Sans elle, vous pouvez lire les données déjà reçues avec le verrouillage d'écran de ce téléphone (lecture seule : pas de purge, pas de réglage de la TV).", style = MaterialTheme.typography.bodySmall)
    if (km?.isDeviceSecure == true) OutlinedButton({ km.createConfirmDeviceCredentialIntent("Contrôle parental", "Lecture seule des données reçues")?.let { launcher.launch(it) } }) { Text("Ouvrir en lecture seule") }
    else Text("Ce téléphone n'a pas de verrouillage d'écran : définissez-en un dans les réglages d'Android pour pouvoir lire les données sans la TV.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

/** The name the TV gives its reports: the one already known when there is a single TV, else the name of the linked TV. */
internal fun tvNameOf(ctx: Context): String {
    val known = ParentalData.ledger(ctx).tvList()
    if (known.size == 1) return known[0].name
    return (TvLinkManager.state.value as? LinkUi.Connected)?.session?.tv?.name ?: known.firstOrNull()?.name ?: "TV"
}

// ---------------------------------------------------------------------------------------------------------------- shell

/** What every section reads: the local copy, filtered by the TV and the profile the parent chose. */
internal class Env(val ctx: Context, val ledger: ParentalLedger, val facts: List<DayFact>, val events: List<ActivityEvent>, val tv: String?, val profileTv: String?, val profile: String?, val zone: ZoneId, val now: Long) {
    fun name(tv: String, id: String?) = ledger.profileName(tv, id)
    /** (tv, id) of the profiles to show: the chosen one, or every profile of the chosen TV (or of all TVs). */
    fun targets(): List<Pair<String, String>> =
        if (profile != null) listOf((profileTv ?: ledger.profileList().firstOrNull { it.id == profile }?.tv ?: "") to profile)
        else ledger.profileList().filter { tv == null || it.tv == tv }.map { it.tv to it.id }
    fun summary(period: Period, tv: String?, id: String?) = ReportAggregator.summarize(facts, events, period, id, zone, tv)
}

private val SECTIONS = listOf("Tableau de bord", "Activité", "Par application", "Apprendre et Quiz", "Rapports", "Exports", "Alertes", "Données", "Règles de la TV")

@Composable
private fun ParentalHome(onClose: (() -> Unit)?) {
    val ctx = LocalContext.current
    val ledger = remember { ParentalData.ledger(ctx) }
    val ver = ParentalData.version
    LaunchedEffect(Unit) { while (true) { ParentalData.refresh(ctx); delay(10_000) } }
    var section by rememberSaveable { mutableStateOf(0) }
    var tv by rememberSaveable { mutableStateOf<String?>(null) }
    var profileKey by rememberSaveable { mutableStateOf<String?>(null) }          // "tv|id"
    val zone = remember { ZoneId.systemDefault() }
    val now = remember(ver, section) { System.currentTimeMillis() }
    val facts = remember(ver) { ledger.facts() }
    val events = remember(ver) { ledger.events() }
    val tvs = ledger.tvList()
    val profiles = ledger.profileList().filter { tv == null || it.tv == tv }
    val sel = profileKey?.let { k -> profiles.firstOrNull { it.key == k } }
    val env = Env(ctx, ledger, facts.filter { tv == null || it.tv == tv }, events.filter { tv == null || it.tv == tv }, tv, sel?.tv, sel?.id, zone, now)

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            onClose?.let { IconButton(it) { Icon(Icons.Filled.ArrowBack, "Retour") } }
            Text(if (ParentalSession.readOnly) "Contrôle parental (lecture seule)" else "Contrôle parental", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            TextButton({ ParentalSession.lockNow() }) { Icon(Icons.Filled.Lock, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Verrouiller") }
        }
        ScrollableTabRow(selectedTabIndex = section, containerColor = MaterialTheme.colorScheme.surface, edgePadding = 8.dp) {
            SECTIONS.forEachIndexed { i, t -> Tab(section == i, { section = i }, text = { Text(t, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }) }
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FreshnessBanner(env)
            if (tvs.size > 1) ChipRow(listOf<String?>(null) + tvs.map { it.name }, tv, { it ?: "Toutes les TV" }) { tv = it; profileKey = null }
            if (profiles.isNotEmpty() && section < 7) ChipRow(listOf<String?>(null) + profiles.map { it.key }, profileKey, { k -> if (k == null) "Tous les profils" else profiles.first { it.key == k }.let { p -> p.name + if (p.deleted) " (supprimé)" else "" } }) { profileKey = it }
            when (section) {
                0 -> DashboardSection(env)
                1 -> ActivitySection(env)
                2 -> AppsSection(env)
                3 -> LearnSection(env)
                4 -> ReportsSection(env)
                5 -> ExportsSection(env)
                6 -> AlertsSection(env)
                7 -> DataSection(env, onChanged = { tv = null; profileKey = null })
                else -> RulesSection(env)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------- shared pieces

@Composable
internal fun <T> ChipRow(items: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { it -> FilterChip(selected == it, { onPick(it) }, label = { Text(label(it), maxLines = 1) }) }
    }
}

@Composable
internal fun PCard(content: @Composable ColumnScope.() -> Unit) =
    ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content) }

/** A figure with its quality tag. A number is only written when there is one: otherwise « indisponible » and the reason. */
@Composable
internal fun FigureRow(label: String, f: Figure, unit: (Long) -> String = Fmt::min, approx: Boolean = false) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(f.value?.let { (if (approx) "~" else "") + unit(it) } ?: "indisponible", style = MaterialTheme.typography.titleSmall)
            QualityTag(f.quality)
        }
        f.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
internal fun Sub(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
internal fun H(text: String) = Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

/** Age of the data and supervision of the whole TV: on top of every screen (docs/PARENTAL.md, « Hors ligne d'abord »). */
@Composable
private fun FreshnessBanner(env: Env) {
    val tvs = env.ledger.tvList().filter { env.tv == null || it.name == env.tv }
    val fr = Freshness.of(tvs.maxOfOrNull { it.lastReceivedAt } ?: 0L, env.now)
    val obs = env.ledger.lastSupervision(env.tv)
    val state = obs?.let { SupervisionState.of(it.state) }
    PCard {
        Text(fr.text(), style = MaterialTheme.typography.bodyMedium, color = if (fr.level == Freshness.Level.STALE || fr.level == Freshness.Level.NONE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        if (ParentalInbox.lastSyncAt > 0) Sub("Dernier contact réussi avec la TV (Bluetooth) : ${Freshness.age(env.now - ParentalInbox.lastSyncAt)}")
        if (fr.level == Freshness.Level.STALE) Sub("Les données ont plusieurs jours : la TV était peut-être éteinte ou hors de portée. Les périodes sans rapport sont signalées comme incomplètes.")
        Text(SupervisionText.line(state, obs != null), style = MaterialTheme.typography.bodyMedium,
            color = if (state == SupervisionState.NOT_AUTHORIZED || state == SupervisionState.UNAVAILABLE || obs == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        Sub(SupervisionText.consequence(state) + (obs?.let { " (état du ${ReportDocument.stamp(it.ts, env.zone)})" } ?: ""))
    }
}

@Composable
private fun rememberLiveClient(): castbridge.core.parental.ParentalClient? {
    val link by TvLinkManager.state.collectAsState()
    val s = (link as? LinkUi.Connected)?.session ?: return null
    val b = s.base ?: return null
    if (!castbridge.core.trust.TvAuth.isUsable(s.credential)) return null
    return remember(b, s.credential) { castbridge.core.parental.ParentalClient(b, s.credential) }
}

/**
 * « Demander les rapports à la TV »: pulls over every route available right now (the Bluetooth delivery of the outbox, and the live report over
 * Wi-Fi when the phone is linked to the TV and the PIN is known), into the same local copy. Nothing goes to a server.
 */
@Composable
internal fun PullButton(env: Env, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val client = rememberLiveClient()
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Button(enabled = !busy, onClick = {
            busy = true
            scope.launch { msg = pullReports(env.ctx, client, ParentalSession.pin(), tvNameOf(env.ctx)); busy = false }
        }) { Text(if (busy) "Demande en cours…" else "Demander les rapports à la TV") }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        msg?.let { Sub(it) }
    }
}

internal suspend fun pullReports(ctx: Context, client: castbridge.core.parental.ParentalClient?, pin: String?, tvName: String): String {
    val parts = ArrayList<String>()
    val bt = io { ParentalInbox.sync(ctx) }
    parts += "Bluetooth : " + (bt.getOrElse { why(it) } ?: "rapports reçus")
    if (client != null && pin != null) {
        io {
            val cfg = client.load(pin).config
            LiveReport.toReports(tvName, client.report(pin, 14), cfg.profiles, System.currentTimeMillis()) to cfg.profiles.map { it.id }
        }.onSuccess { (reports, ids) -> val l = ParentalData.ledger(ctx); l.absorb(reports); l.markProfiles(tvName, ids); parts += "Wi-Fi : rapport reçu" }
            .onFailure { parts += "Wi-Fi : " + why(it) }
    } else parts += if (client == null) "Wi-Fi : TV non liée en Wi-Fi" else "Wi-Fi : code parental requis (mode lecture seule)"
    ParentalData.refresh(ctx); ParentalData.changed()
    return parts.joinToString(" · ")
}

@Composable
internal fun EmptyState(text: String) = PCard { Text(text, style = MaterialTheme.typography.bodyMedium) }

internal fun dayLabel(d: LocalDate) = d.toString()

// ---------------------------------------------------------------------------------------------------------------- (a) dashboard

@Composable
private fun DashboardSection(env: Env) {
    val today = LocalDate.now(env.zone)
    val targets = env.targets()
    if (targets.isEmpty()) {
        EmptyState("Aucun rapport reçu de la TV pour l'instant. Sur la TV : Contrôle parental > Rapports, désignez ce téléphone ; ou liez-le à la TV en Wi-Fi puis demandez les rapports.")
        PullButton(env); return
    }
    targets.forEach { (tv, id) ->
        val fs = env.facts.filter { it.tv == tv && it.profileId == id }
        val day = if (fs.any { Clock.parseDay(it.day) == today }) today else fs.mapNotNull { Clock.parseDay(it.day) }.maxOrNull()
        PCard {
            Text(env.name(tv, id), style = MaterialTheme.typography.titleMedium)
            if (day == null) { Text("Aucun jour reçu pour ce profil.", style = MaterialTheme.typography.bodyMedium); return@PCard }
            if (day != today) Text("Pas de rapport pour aujourd'hui (le résumé du jour part le soir, ou demandez-le ci-dessous). Dernier jour reçu : ${dayLabel(day)}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            else Text("Aujourd'hui (${dayLabel(day)}), selon le dernier rapport reçu.", style = MaterialTheme.typography.bodySmall)
            val s = env.summary(Period.day(day), tv, id)
            val g = Goals.check(s).days.first()
            FigureRow("CastBridge-TV", s.measured)
            FigureRow("Autres applications de la TV", s.otherApps, approx = true)
            val used = g.usedMin
            if (g.limitMin > 0 && used != null) {
                val frac = (used.toFloat() / g.limitMin).coerceIn(0f, 1f)
                LinearProgressIndicator(progress = { frac }, Modifier.fillMaxWidth().semantics { contentDescription = "Temps d'écran ${Fmt.min(used)} sur ${Fmt.min(g.limitMin.toLong())}" }, color = if (g.status == GoalStatus.OVER) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                Text("${if (g.partial) "Au moins " else ""}${Fmt.min(used)} sur ${Fmt.min(g.limitMin.toLong())} : ${g.status.label}", style = MaterialTheme.typography.bodyMedium)
                if (g.partial) Sub("Les autres applications ne sont pas mesurées : le temps réel peut être plus élevé.")
            } else if (g.limitMin <= 0) Sub("Pas de limite de temps réglée pour ce profil.")
            val kinds = listOf(s.play to "Vidéos", s.games to "Jeux et Apprendre", s.downloads to "Téléchargements", s.otherApps to "Autres apps")
            val cols = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.outline)
            val txt = kinds.joinToString(", ") { (f, n) -> "$n ${f.value?.let(Fmt::min) ?: "indisponible"}" }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Donut(kinds.mapIndexed { i, (f, _) -> (f.value ?: 0L).toFloat() to cols[i] }, "Répartition du temps : $txt")
                Column { kinds.forEachIndexed { i, (f, n) -> Text("● $n : ${f.value?.let(Fmt::min) ?: "indisponible"}", style = MaterialTheme.typography.bodySmall, color = cols[i]) } }
            }
            val top = ReportAggregator.top(s.events, setOf(EventType.VIDEO, EventType.GAME, EventType.LEARN, EventType.QUIZ, EventType.DOWNLOAD), 3)
            if (top.isEmpty()) Sub("Détail des activités (titres, durées) non reçu pour ce jour.") else { Text("Activités principales", style = MaterialTheme.typography.labelMedium); top.forEach { Text("• ${it.type.label} : ${it.title}${if (it.min > 0) " (${Fmt.min(it.min)})" else ""}", style = MaterialTheme.typography.bodySmall) } }
            FigureRow("Blocages", s.blocks, Long::toString)
            val alerts = s.events.count { it.type == EventType.ALERT }
            Text("Alertes : $alerts", style = MaterialTheme.typography.bodyMedium)
        }
    }
    PullButton(env)
}

// ---------------------------------------------------------------------------------------------------------------- (b) timeline

@Composable
private fun ActivitySection(env: Env) {
    var groups by remember { mutableStateOf(emptySet<EventType.Group>()) }
    var shown by remember { mutableStateOf(60) }
    var detail by remember { mutableStateOf<ActivityEvent?>(null) }
    val all = env.events.filter { e -> (env.profile == null || e.profileId == env.profile || e.profileId == null) && (groups.isEmpty() || e.type.group in groups) }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(groups.isEmpty(), { groups = emptySet() }, label = { Text("Tout") })
        EventType.Group.values().forEach { g -> FilterChip(g in groups, { groups = if (g in groups) groups - g else groups + g; shown = 60 }, label = { Text(g.label, maxLines = 1) }) }
    }
    if (all.isEmpty()) {
        EmptyState(if (env.events.isEmpty()) "Aucun événement détaillé reçu. La TV envoie le journal de CastBridge-TV (vidéos, jeux, Apprendre, Quiz, téléchargements…) avec son résumé quotidien ; les totaux par jour sont dans « Rapports »." else "Aucun événement pour ce filtre.")
        return
    }
    val f = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.FRANCE).apply { timeZone = java.util.TimeZone.getTimeZone(env.zone) } }
    all.take(shown).groupBy { Clock.dayOf(it.ts, env.zone) }.forEach { (d, list) ->
        Text(dayLabel(d) + " — " + list.size + " événement(s)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        list.forEach { e ->
            Row(Modifier.fillMaxWidth().clickable { detail = e }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(f.format(java.util.Date(e.ts)), style = MaterialTheme.typography.labelMedium)
                Column(Modifier.weight(1f)) {
                    Text("${e.type.label} : ${e.title}", style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                    Sub(listOfNotNull(env.name(e.tv, e.profileId).takeIf { env.profile == null }, e.durMin?.let { Fmt.min(it.toLong()) }, e.score).joinToString(" · "))
                }
                QualityTag(e.quality)
            }
        }
    }
    if (all.size > shown) OutlinedButton({ shown += 60 }) { Text("Afficher plus (${all.size - shown} restants)") }
    detail?.let { e ->
        AlertDialog(onDismissRequest = { detail = null }, title = { Text("${e.type.label} : ${e.title}") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Quand : ${ReportDocument.stamp(e.ts, env.zone)}"); Text("Profil : ${env.name(e.tv, e.profileId)}"); Text("TV : ${e.tv}")
                Text("Durée : ${e.durMin?.let { Fmt.min(it.toLong()) } ?: "non mesurée"}"); e.score?.let { Text("Résultat : $it") }; e.detail?.let { Text(it) }
                e.severity?.let { Text("Gravité : ${it.label}") }; QualityTag(e.quality)
            } }, confirmButton = { TextButton({ detail = null }) { Text("Fermer") } })
    }
}

// ---------------------------------------------------------------------------------------------------------------- period selector

internal class PeriodState(val kind: PeriodKind, val ref: LocalDate, val customDays: Int) {
    val period: Period get() = if (kind == PeriodKind.CUSTOM) Period.custom(ref.minusDays(customDays - 1L), ref) else Period.of(kind, ref)
}

@Composable
internal fun PeriodBar(kind: PeriodKind, ref: LocalDate, customDays: Int, today: LocalDate, onKind: (PeriodKind) -> Unit, onRef: (LocalDate) -> Unit, onCustom: (Int) -> Unit) {
    ChipRow(PeriodKind.values().toList(), kind, { if (it == PeriodKind.CUSTOM) "Plage" else it.label }) { onKind(it) }
    if (kind == PeriodKind.CUSTOM) Picker("Derniers jours", listOf(7, 14, 30, 60, 90).map { "$it jours" }, listOf(7, 14, 30, 60, 90).indexOf(customDays).coerceAtLeast(0)) { onCustom(listOf(7, 14, 30, 60, 90)[it]) }
    val p = PeriodState(kind, ref, customDays).period
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton({ onRef(when (kind) { PeriodKind.DAY -> ref.minusDays(1); PeriodKind.WEEK -> ref.minusWeeks(1); PeriodKind.MONTH -> ref.minusMonths(1); PeriodKind.CUSTOM -> ref.minusDays(customDays.toLong()) }) }) { Text("◀ Avant") }
        Text(p.label(), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        TextButton(enabled = p.to.isBefore(today), onClick = { onRef(minOf(today, when (kind) { PeriodKind.DAY -> ref.plusDays(1); PeriodKind.WEEK -> ref.plusWeeks(1); PeriodKind.MONTH -> ref.plusMonths(1); PeriodKind.CUSTOM -> ref.plusDays(customDays.toLong()) })) }) { Text("Après ▶") }
    }
}

/** The period chosen by the parent, kept while the tab is open (shared by Par application, Rapports and Exports). */
@Composable
internal fun rememberPeriod(env: Env, content: @Composable (PeriodState) -> Unit) {
    val today = LocalDate.now(env.zone)
    var kind by rememberSaveable { mutableStateOf(PeriodKind.WEEK) }
    var refDay by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    var custom by rememberSaveable { mutableStateOf(14) }
    val ref = LocalDate.ofEpochDay(refDay)
    PeriodBar(kind, ref, custom, today, { kind = it }, { refDay = it.toEpochDay() }, { custom = it })
    content(PeriodState(kind, ref, custom))
}

// ---------------------------------------------------------------------------------------------------------------- (c) per app

@Composable
private fun AppsSection(env: Env) {
    Text("Minutes par application. Les applications autres que CastBridge-TV sont vues par la surveillance de toute la TV, quand elle est active et autorisée : c'est un MEILLEUR EFFORT, jamais une mesure exacte.", style = MaterialTheme.typography.bodySmall)
    rememberPeriod(env) { ps ->
        val p = ps.period
        val targets = env.targets()
        val s = env.summary(p, env.tv, env.profile)
        Sub(s.coverageText())
        FigureRow("Autres applications (total)", s.otherApps, approx = true)
        val bars = s.days.map { Bar(it.day.dayOfMonth.toString(), it.appsMin?.toFloat()) }
        if (s.coveredDays > 0) BarChart(bars, "Minutes des autres applications par jour. Les jours sans donnée sont en pointillés : " + s.days.joinToString { "${it.day} ${it.appsMin?.let(Fmt::min) ?: "indisponible"}" })
        if (s.apps.isEmpty()) { EmptyState("Aucune minute d'application reçue sur cette période. Si la surveillance de toute la TV est inactive ou non autorisée (le cas sur certaines TV), seules les données de CastBridge-TV sont disponibles : voir « Rapports »."); return@rememberPeriod }
        val max = s.apps.first().min.coerceAtLeast(1)
        PCard {
            s.apps.take(30).forEach { a ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(a.label, Modifier.weight(1f), maxLines = 1); Text("~${Fmt.min(a.min)}", style = MaterialTheme.typography.titleSmall); QualityTag(Quality.BEST_EFFORT)
                }
                LinearProgressIndicator(progress = { a.min.toFloat() / max }, Modifier.fillMaxWidth().semantics { contentDescription = "${a.label} : ${Fmt.min(a.min)} sur ${a.days} jour(s)" }, color = MaterialTheme.colorScheme.tertiary)
            }
        }
        if (targets.size > 1 && env.profile == null) Sub("Total de tous les profils. Choisissez un profil pour le détail.")
    }
}

// ---------------------------------------------------------------------------------------------------------------- (d) Apprendre / Quiz

@Composable
private fun LearnSection(env: Env) {
    val targets = env.targets()
    if (targets.isEmpty()) { EmptyState("Aucun profil connu : rien à afficher pour l'instant."); return }
    val since = env.now - 30 * 86_400_000L
    targets.forEach { (tv, id) ->
        val digest = env.ledger.learnDigest(tv, id)
        val ev = env.events.filter { it.tv == tv && it.profileId == id && it.ts >= since }
        val q = LearnQuizStats.of(ev)
        PCard {
            Text(env.name(tv, id), style = MaterialTheme.typography.titleMedium)
            if (digest == null) Text("Pas de résumé « Apprendre » reçu pour ce profil : l'élève n'est pas lié à un profil d'Apprendre, ou la TV n'a pas encore envoyé de rapport avec ce détail (mettez CastBridge-TV à jour).", style = MaterialTheme.typography.bodyMedium)
            else {
                val v = LearnDigest.view(id, digest)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { H("Apprendre"); QualityTag(Quality.MEASURED) }
                Text("Classe : ${v.level ?: "non renseignée"} · fiches terminées : ${v.lessonsCompleted} · étoiles : ${v.stars} · série : ${v.streak} jour(s) de suite", style = MaterialTheme.typography.bodyMedium)
                Text("Temps passé (total) : ${Fmt.min(v.totalMin)} · révisions à faire : ${v.reviewsDue}", style = MaterialTheme.typography.bodyMedium)
                v.subjects.forEach { s ->
                    Text("${s.label} : ${Fmt.min(s.timeMin)} · ${s.lessons} fiche(s) · réussite ${s.successPct?.let { "$it %" } ?: "pas d'exercice"}", style = MaterialTheme.typography.bodySmall)
                    s.successPct?.let { LinearProgressIndicator(progress = { it / 100f }, Modifier.fillMaxWidth().semantics { contentDescription = "${s.label} : réussite $it %" }) }
                }
                if (v.weak.isNotEmpty()) Text("À retravailler : ${v.weak.joinToString(", ")}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) else if (v.subjects.isNotEmpty()) Sub("Aucun point faible repéré (réussite d'au moins ${LearnDigest.WEAK_BELOW_PCT} %).")
                v.mocks.forEach { Text("Épreuve blanche ${it.subject} : ${it.score}", style = MaterialTheme.typography.bodySmall) }
                if (v.badges.isNotEmpty()) Sub("Badges : ${v.badges.joinToString(", ")}")
            }
            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { H("Quiz (30 jours)"); QualityTag(if (ev.any { it.type == EventType.QUIZ }) Quality.MEASURED else Quality.UNAVAILABLE) }
            if (q.games == 0) Sub("Aucune partie de Quiz reçue sur 30 jours (ou journal non reçu).")
            else {
                Text("${q.games} partie(s)${q.bestScore?.let { " · meilleur résultat $it" } ?: ""}", style = MaterialTheme.typography.bodyMedium)
                q.results.take(8).forEach { Text("• ${ReportDocument.stamp(it.ts, env.zone)} ${it.title}${it.score?.let { s -> " : $s" } ?: ""}", style = MaterialTheme.typography.bodySmall) }
            }
            if (q.learnSessions > 0) Sub("Séances d'Apprendre (journal) : ${q.learnSessions}, ${Fmt.min(q.learnMin)} chronométrées.")
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------- the TV's rules

/** The rules of the TV (profiles, hours, limits, per-app rules, whole-TV supervision): the editor of the former padlock screen, with the PIN of the session. */
@Composable
private fun RulesSection(env: Env) {
    if (ParentalSession.readOnly) { EmptyState("Lecture seule : les règles de la TV se modifient avec le code parental, TV allumée. Verrouillez puis rouvrez l'onglet avec le code."); return }
    ParentalTvPicker(env.ctx) { client -> ParentalPanel(client, ParentalSession.pin()) }
}
