package castbridge.sender

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import castbridge.core.parental.ChildProfile
import castbridge.core.parental.SupervisionState
import castbridge.core.parental.tab.*
import kotlinx.coroutines.launch

// (e) Rapports, (f) Exports, (g) Alertes, (h) Données of the Parental tab. See ParentalTab.kt for the shell and docs/PARENTAL.md for the rules.

/** The blocks of a report: all profiles together (when several) and each profile. (title, tv, profile id) */
private fun blocksOf(env: Env): List<Triple<String, String?, String?>> {
    val t = env.targets()
    val each = t.map { (tv, id) -> Triple(env.name(tv, id), tv, id) }
    return if (env.profile == null && t.size > 1) listOf(Triple("Tous les profils", env.tv, null as String?)) + each else each
}

// ---------------------------------------------------------------------------------------------------------------- (e) reports

@Composable
internal fun ReportsSection(env: Env) {
    rememberPeriod(env) { ps ->
        val p = ps.period
        val blocks = blocksOf(env)
        if (blocks.isEmpty()) { EmptyState("Aucun rapport reçu : rien à comparer pour l'instant."); return@rememberPeriod }
        blocks.forEach { (title, tv, id) -> ReportBlock(env, title, tv, id, p) }
    }
}

@Composable
private fun ReportBlock(env: Env, title: String, tv: String?, id: String?, p: Period) {
    val s = env.summary(p, tv, id)
    val prev = env.summary(p.previous(), tv, id)
    PCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Sub(s.coverageText())
        if (s.coveredDays > 0 && s.missingDays.isNotEmpty()) Sub("Jours sans rapport : " + s.missingDays.take(10).joinToString(", ") { it.toString() } + if (s.missingDays.size > 10) "…" else "")
        FigureRow("CastBridge-TV (total)", s.measured)
        FigureRow("   Vidéos", s.play); FigureRow("   Jeux et Apprendre", s.games); FigureRow("   Téléchargements", s.downloads)
        FigureRow("Autres applications de la TV", s.otherApps, approx = true)
        val d = Trends.measuredDelta(s, prev)
        Text(d.text(), style = MaterialTheme.typography.bodyMedium)
        if (d.note.isNotBlank() && d.dir != Dir.UNKNOWN) Sub(d.note)
        if (s.coveredDays > 0) {
            val bars = s.days.map { r -> Bar(r.day.dayOfMonth.toString(), if (r.covered) r.measuredMin.toFloat() else null, r.appsMin?.toFloat()) }
            Text("Temps par jour : CastBridge-TV (plein) + autres applications estimées (empilées) ; pointillés = pas de rapport", style = MaterialTheme.typography.labelMedium)
            BarChart(bars, "Temps par jour. " + s.days.joinToString("; ") { r -> "${r.day} : ${r.knownMin?.let(Fmt::min) ?: "pas de rapport"}" })
            LineChart(s.days.map { it.knownMin?.toFloat() }, "Tendance du temps total par jour")
        }
        if (id != null) GoalsBlock(s)
        if (s.events.any { it.id.startsWith("j:") }) {
            TopList("Vidéos les plus regardées", ReportAggregator.top(s.events, setOf(EventType.VIDEO), 5))
            TopList("Jeux", ReportAggregator.top(s.events, setOf(EventType.GAME), 5))
            TopList("Apprendre et Quiz", ReportAggregator.top(s.events, setOf(EventType.LEARN, EventType.QUIZ), 5))
            TopList("Téléchargements", ReportAggregator.top(s.events, setOf(EventType.DOWNLOAD), 5))
        } else Sub("Journal détaillé de CastBridge-TV non reçu sur cette période : pas de listes de titres, de carte horaire ni de séances.")
        if (s.apps.isNotEmpty()) { Text("Applications les plus utilisées (meilleur effort)", style = MaterialTheme.typography.labelMedium); s.apps.take(5).forEach { Text("• ${it.label} : ~${Fmt.min(it.min)}", style = MaterialTheme.typography.bodySmall) } }
        UsagePattern(env, s, tv, id)
        FigureRow("Blocages", s.blocks, Long::toString)
        FigureRow("Tentatives de déverrouillage", s.unlockAttempts, Long::toString)
        FigureRow("Alertes de manipulation de la surveillance", s.tamper, Long::toString)
    }
}

@Composable
private fun TopList(title: String, top: List<TitleAgg>) {
    if (top.isEmpty()) return
    Text(title, style = MaterialTheme.typography.labelMedium)
    top.forEach { Text("• ${it.title} — ${it.count} fois${if (it.min > 0) ", ${Fmt.min(it.min)}" else ""}", style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun GoalsBlock(s: PeriodSummary) {
    val g = Goals.check(s)
    if (g.days.all { it.status == GoalStatus.NO_LIMIT || it.status == GoalStatus.UNKNOWN }) { Sub("Objectifs : aucune limite de temps réglée pour ce profil sur cette période."); return }
    Text("Objectifs (limite du jour) contre réalisé", style = MaterialTheme.typography.labelMedium)
    Text("Limite dépassée : ${g.over} j · proche : ${g.near} j · respectée : ${g.ok} j · sans donnée : ${g.unknown} j", style = MaterialTheme.typography.bodyMedium)
    g.days.filter { it.status != GoalStatus.NO_LIMIT }.takeLast(14).forEach { d ->
        Text("${d.day} : ${d.usedMin?.let { (if (d.partial) "au moins " else "") + Fmt.min(it) } ?: "pas de rapport"} / ${Fmt.min(d.limitMin.toLong())} — ${d.status.label}", style = MaterialTheme.typography.bodySmall,
            color = if (d.status == GoalStatus.OVER) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun UsagePattern(env: Env, s: PeriodSummary, tv: String?, id: String?) {
    val hm = remember(s) { Heatmap.build(s.events, env.zone) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { Text("Heures d'utilisation", style = MaterialTheme.typography.labelMedium); QualityTag(if (hm.hasData) Quality.MEASURED else Quality.UNAVAILABLE) }
    if (!hm.hasData) { Sub("Indisponible : aucune activité chronométrée reçue."); return }
    HeatmapView(hm)
    val peaks = hm.peakHours(3)
    Text("Heures de pointe : " + peaks.joinToString(", ") { "${it.first} h (${Fmt.min(it.second)})" }, style = MaterialTheme.typography.bodyMedium)
    Text("Usage tardif (${Heatmap.LATE_FROM} h – ${Heatmap.LATE_TO} h) : ${Fmt.minOrNA(hm.lateNightMin())}", style = MaterialTheme.typography.bodyMedium)
    val window = env.facts.filter { (tv == null || it.tv == tv) && (id == null || it.profileId == id) && it.window != null }.maxByOrNull { it.day }?.window
    UseStats.outsideWindowMin(s.events, window, env.zone)?.let { Text("Hors des heures autorisées ($window) : ${Fmt.min(it)}", style = MaterialTheme.typography.bodyMedium, color = if (it > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) }
    val longest = UseStats.longest(s.events, 3)
    if (longest.isNotEmpty()) { Text("Séances les plus longues", style = MaterialTheme.typography.labelMedium); longest.forEach { Text("• ${ReportDocument.stamp(it.ts, env.zone)} ${it.type.label} ${it.title} : ${Fmt.min((it.durMin ?: 0).toLong())}", style = MaterialTheme.typography.bodySmall) } }
}

// ---------------------------------------------------------------------------------------------------------------- (f) exports

@Composable
internal fun ExportsSection(env: Env) {
    val ctx = LocalContext.current
    var msg by remember { mutableStateOf<String?>(null) }
    Text("Partagez un résumé ou le détail des événements. Rien n'est envoyé nulle part : le fichier est créé sur ce téléphone et remis à la feuille de partage d'Android uniquement quand vous touchez un bouton ; vous choisissez où il va.", style = MaterialTheme.typography.bodySmall)
    rememberPeriod(env) { ps ->
        val p = ps.period
        val blocks = blocksOf(env)
        if (blocks.isEmpty()) { EmptyState("Aucune donnée à exporter."); return@rememberPeriod }
        val state = env.ledger.lastSupervision(env.tv)?.let { SupervisionState.of(it.state) }
        val line = SupervisionText.line(state, env.ledger.lastSupervision(env.tv) != null)
        val lines = ReportDocument.build(blocks.map { (t, tv, id) -> t to env.summary(p, tv, id) }, blocks.associate { (t, tv, id) -> t to env.summary(p.previous(), tv, id) }, env.tv, env.now, env.zone, line)
        PCard {
            Text("Période : ${p.label()} · ${blocks.size} bloc(s)", style = MaterialTheme.typography.titleSmall)
            Sub("Le résumé reprend chaque chiffre avec sa qualité (MESURÉ, MEILLEUR EFFORT, INDISPONIBLE).")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ runCatching { ParentalExport.shareText(ctx, "Rapport parental CastBridge ${p.label()}", ReportDocument.toText(lines)) }.onFailure { msg = "Partage impossible : ${it.message}" } }) { Text("Texte") }
                OutlinedButton({ runCatching { ParentalExport.sharePdf(ctx, "castbridge-rapport-${p.from}.pdf", lines) }.onFailure { msg = "PDF impossible : ${it.message}" } }) { Text("PDF") }
            }
            val evs = env.events.filter { e -> p.containsTs(e.ts, env.zone) && (env.profile == null || e.profileId == env.profile || e.profileId == null) }
            OutlinedButton(enabled = evs.isNotEmpty(), onClick = {
                runCatching { ParentalExport.shareCsv(ctx, "castbridge-evenements-${p.from}.csv", CsvExport.build(evs, env.zone) { e -> env.name(e.tv, e.profileId) }) }.onFailure { msg = "CSV impossible : ${it.message}" }
            }) { Text("CSV des événements (${evs.size})") }
            if (evs.isEmpty()) Sub("Pas d'événement détaillé sur cette période : le CSV est indisponible.")
            msg?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------- (g) alerts

@Composable
internal fun AlertsSection(env: Env) {
    val ctx = LocalContext.current
    var min by remember { mutableStateOf<Severity?>(null) }
    ChipRow(listOf<Severity?>(null, Severity.WARN, Severity.CRITICAL), min, { when (it) { null -> "Toutes"; Severity.WARN -> "Attention et plus"; else -> "Importantes" } }) { min = it }
    val list = AlertHistory.of(env.events.filter { env.profile == null || it.profileId == env.profile || it.profileId == null }, min)
    if (list.isEmpty()) Sub("Aucune alerte reçue.")
    list.take(100).forEach { e ->
        val sev = e.severity ?: Severity.INFO
        PCard {
            Text("${when (sev) { Severity.CRITICAL -> "⛔ "; Severity.WARN -> "⚠ "; else -> "ℹ " }}${sev.label} · ${ReportDocument.stamp(e.ts, env.zone)} · ${env.name(e.tv, e.profileId)}", style = MaterialTheme.typography.labelMedium,
                color = if (sev == Severity.CRITICAL) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            Text(e.title, style = MaterialTheme.typography.bodyMedium)
            Sub("Action : " + AlertHistory.action(e))
        }
    }
    HorizontalDivider()
    H("Alertes et rapports envoyés à ce téléphone")
    Text(when (ParentalInbox.designated) {
        true -> "Ce téléphone est désigné pour recevoir les rapports de la TV."
        false -> "Ce téléphone n'est PAS désigné : la TV ne lui envoie rien. Désignez-le ci-dessous (téléphone du parent, jamais celui d'un enfant)."
        null -> "Statut inconnu : ce téléphone n'a pas encore joint la TV en Bluetooth."
    }, style = MaterialTheme.typography.bodyMedium)
    val pin = ParentalSession.pin()
    if (pin == null) { Sub("Les réglages des rapports se modifient avec le code parental et la TV allumée (indisponible en lecture seule)."); return }
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf<String?>(null) }
    msg?.let { Sub(it) }
    ParentalTvPicker(ctx) { client ->
        var profiles by remember(client) { mutableStateOf<List<ChildProfile>?>(null) }
        LaunchedEffect(client) { io { client.load(pin) }.onSuccess { profiles = it.config.profiles }.onFailure { msg = why(it) } }
        profiles?.let { ReportsBlock(client, it, pin, scope) { m -> msg = m } } ?: LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

// ---------------------------------------------------------------------------------------------------------------- (h) data

@Composable
internal fun DataSection(env: Env, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val ledger = env.ledger
    var confirm by remember { mutableStateOf<Triple<String, String?, String?>?>(null) }     // label, tv, profile id
    var typed by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    val (events, facts, _) = ledger.sizes()

    PCard {
        H("Conservation")
        Picker("Garder les données", Retention.CHOICES.map { "$it jours" }, Retention.CHOICES.indexOf(ledger.retention.days).coerceAtLeast(0)) { ParentalData.setRetention(ctx, Retention.CHOICES[it]) }
        Sub("$events événement(s) et $facts jour(s) gardés sur ce téléphone. Au-delà de la durée choisie ou de ${ledger.retention.maxEvents} événements, les plus anciens sont supprimés automatiquement.")
    }
    PCard {
        H("Synchronisation")
        val tvs = ledger.tvList()
        if (tvs.isEmpty()) Text("Aucune TV n'a encore envoyé de rapport.", style = MaterialTheme.typography.bodyMedium)
        tvs.forEach { Text("${it.name} : dernier rapport reçu ${Freshness.age(env.now - it.lastReceivedAt)} (${ReportDocument.stamp(it.lastReceivedAt, env.zone)})", style = MaterialTheme.typography.bodyMedium) }
        Text("Dernier contact réussi (Bluetooth) : " + if (ParentalInbox.lastSyncAt > 0) ReportDocument.stamp(ParentalInbox.lastSyncAt, env.zone) else "jamais", style = MaterialTheme.typography.bodyMedium)
        ParentalInbox.lastError?.let { Sub("Dernier essai : $it") }
        PullButton(env)
        Sub("Les rapports arrivent dès que le téléphone et la TV peuvent se parler (Bluetooth, ou Wi-Fi quand la TV est liée) ; aucun serveur, aucun Internet.")
    }
    PCard {
        H("Purge")
        if (ParentalSession.readOnly) Sub("Lecture seule : la purge demande le code parental (ouvrez l'onglet avec le code, TV allumée).")
        else {
            Sub("Efface les données de ce téléphone (pas celles de la TV). Le code parental est redemandé.")
            ledger.profileList().forEach { p -> OutlinedButton({ confirm = Triple("le profil ${p.name}", p.tv, p.id) }) { Text("Purger ${p.name}${if (ledger.tvList().size > 1) " (${p.tv})" else ""}") } }
            if (ledger.tvList().size > 1) ledger.tvList().forEach { t -> OutlinedButton({ confirm = Triple("toutes les données de ${t.name}", t.name, null) }) { Text("Purger la TV ${t.name}") } }
            Button({ confirm = Triple("TOUTES les données parentales de ce téléphone", null, null) }) { Text("Tout purger") }
        }
        msg?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
    PCard {
        H("Vie privée")
        Text("Les données parentales restent sur la TV et sur ce téléphone, jamais sur un serveur. Elles ne quittent ce téléphone que par vos propres boutons de partage. Le code parental n'est ni enregistré ni écrit dans un journal.", style = MaterialTheme.typography.bodySmall)
    }
    confirm?.let { (label, tv, id) ->
        AlertDialog(onDismissRequest = { confirm = null; typed = "" }, title = { Text("Purger $label ?") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Cette action est définitive sur ce téléphone. Saisissez le code parental pour confirmer."); PinInput(typed, { typed = it }, "Code parental") } },
            confirmButton = { TextButton({
                if (!ParentalSession.confirms(typed)) { msg = "Code parental incorrect : rien n'a été effacé."; typed = ""; confirm = null; return@TextButton }
                ledger.purge(tv, id)
                if (tv == null) ParentalInbox.inbox.clear()
                ParentalData.changed(); msg = "Données purgées : $label."; typed = ""; confirm = null; if (tv == null || id == null) onChanged()
            }) { Text("Purger") } },
            dismissButton = { TextButton({ confirm = null; typed = "" }) { Text("Annuler") } })
    }
}
