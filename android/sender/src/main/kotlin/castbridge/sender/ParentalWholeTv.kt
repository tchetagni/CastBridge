package castbridge.sender

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import castbridge.core.parental.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val TIMES = (0 until 48).map { it * 30 }
private val DAYS = listOf("Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi", "Dimanche")

/**
 * « Toute la TV » in the parental screen of the phone (docs/PARENTAL.md): the real state of the supervision, the per-app rules, the delivery of
 * reports to this phone (designation, hours, per-profile options) and the reports received. Same rules as the TV, piloted through the TV's API:
 * the parental PIN goes only in the body of a POST. Nothing here talks to a server.
 */
@Composable
fun WholeTvPanel(client: ParentalClient, profiles: List<ChildProfile>, pin: String, scope: CoroutineScope, onMsg: (String?) -> Unit) {
    SupervisionBlock(client, onMsg)
    HorizontalDivider()
    AppsBlock(client, profiles, pin, scope, onMsg)
    HorizontalDivider()
    ReportsBlock(client, profiles, pin, scope, onMsg)
}

@Composable
private fun SupervisionBlock(client: ParentalClient, onMsg: (String?) -> Unit) {
    var info by remember(client) { mutableStateOf<Map<String, Any?>?>(null) }
    LaunchedEffect(client) { io { client.supervision() }.onSuccess { info = it } }
    Text("Surveillance de toute la TV", style = MaterialTheme.typography.titleSmall)
    @Suppress("UNCHECKED_CAST") val sup = info?.get("supervision") as? Map<String, Any?>
    @Suppress("UNCHECKED_CAST") val setup = info?.get("setup") as? Map<String, Any?>
    if (sup == null) { Text("État : inconnu (la TV n'a peut-être pas la dernière version).", style = MaterialTheme.typography.bodySmall); return }
    val state = sup["state"] as? String
    val bad = state == "unauthorized" || state == "unavailable"
    Text(sup["label"].toString() + ((sup["detail"] as? String)?.let { " — $it" } ?: ""), style = MaterialTheme.typography.bodyMedium,
        color = if (bad) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
    if (bad) Text("⚠ Les autres applications de la TV ne sont PAS contrôlées en ce moment. Sur la TV : Contrôle parental > Surveillance de toute la TV, et suivez les étapes.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    if (state == "off") Text("Désactivée : seul ce qui se passe dans CastBridge-TV est contrôlé.", style = MaterialTheme.typography.bodySmall)
    if (bad && setup != null) {
        (setup["adbUsage"] as? String)?.takeIf { it.isNotBlank() && setup["usageGranted"] != true }?.let { Text("Sans écran de réglages sur la TV, en adb : $it", style = MaterialTheme.typography.bodySmall) }
    }
    OutlinedButton({ onMsg(null); io2(client) { info = it } }) { Text("Actualiser l'état") }
}

private fun io2(client: ParentalClient, f: (Map<String, Any?>) -> Unit) {
    Thread { runCatching { client.supervision() }.onSuccess { r -> android.os.Handler(android.os.Looper.getMainLooper()).post { f(r) } } }.start()
}

@Composable
private fun AppsBlock(client: ParentalClient, profiles: List<ChildProfile>, pin: String, scope: CoroutineScope, onMsg: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var loaded by remember(client) { mutableStateOf<ParentalClient.AppsLoaded?>(null) }
    var settings by remember(client) { mutableStateOf<AppSettings?>(null) }
    var profile by remember { mutableStateOf(profiles.firstOrNull()?.id) }
    var dirty by remember { mutableStateOf(false) }
    Text("Applications de la TV", style = MaterialTheme.typography.titleSmall)
    Text("Pour chaque enfant : autorisée, bloquée, durée limitée par jour, ou code parental demandé. Le temps passé compte dans la durée quotidienne.", style = MaterialTheme.typography.bodySmall)
    if (!open) {
        OutlinedButton({
            open = true
            scope.launch { io { client.apps(pin) }.onSuccess { loaded = it; settings = it.settings; dirty = false }.onFailure { onMsg(why(it)) } }
        }) { Text("Régler les applications") }
        return
    }
    val l = loaded; val s = settings
    if (l == null || s == null) { LinearProgressIndicator(Modifier.fillMaxWidth()); return }
    if (profiles.isEmpty()) { Text("Créez d'abord un profil enfant.", style = MaterialTheme.typography.bodySmall); return }
    fun edit(f: (AppSettings) -> AppSettings) { settings = f(s); dirty = true }
    SwitchRow("Surveillance de toute la TV", "Active : les applications ci-dessous sont contrôlées (si la TV l'autorise : voir l'état plus haut).", s.supervise) { v -> edit { it.copy(supervise = v) } }
    Picker("Nouvelle application installée", NewAppDefault.values().map { it.label }, s.newApp.ordinal) { i -> edit { it.copy(newApp = NewAppDefault.values()[i]) } }
    val pid = profile?.takeIf { id -> profiles.any { it.id == id } } ?: profiles.first().id
    Picker("Règles de", profiles.map { it.name }, profiles.indexOfFirst { it.id == pid }) { i -> profile = profiles[i].id }
    val newOnes = l.apps.filter { it.isNew }
    if (newOnes.isNotEmpty()) Text("Nouvelles : ${newOnes.joinToString { it.label }}. Réglez-les (ou laissez-les) puis enregistrez : elles sont alors validées.", style = MaterialTheme.typography.bodySmall)
    fun ruleOf(pkg: String) = s.rule(pid, pkg)
    fun setRule(pkg: String, r: AppRule?) = edit { cur ->
        val rest = cur.rules[pid].orEmpty().filterNot { it.pkg == pkg }
        cur.copy(rules = cur.rules + (pid to (if (r == null) rest else rest + r)))
    }
    val states = AppState.values()
    l.apps.forEach { a ->
        val r = ruleOf(a.pkg)
        Column(Modifier.fillMaxWidth()) {
            Text(a.label + if (a.isNew) "  · NOUVELLE" else "", style = MaterialTheme.typography.bodyMedium)
            if (a.never) Text("Toujours autorisée (CastBridge-TV, accueil ou système).", style = MaterialTheme.typography.bodySmall)
            else {
                Picker(a.category.label, states.map { it.label } + "Aucune règle", r?.state?.ordinal ?: states.size) { i ->
                    if (i >= states.size) setRule(a.pkg, null)
                    else setRule(a.pkg, AppRule(a.pkg, states[i], if (states[i] == AppState.LIMITED) (r?.limitMin?.takeIf { it > 0 } ?: 30) else 0, r?.category ?: a.category))
                }
                if (r?.state == AppState.LIMITED) {
                    val opts = listOf(15, 30, 45, 60, 90, 120)
                    Picker("Durée par jour", opts.map { "$it min" }, opts.indexOf(r.limitMin).coerceAtLeast(0)) { i -> setRule(a.pkg, r.copy(limitMin = opts[i])) }
                }
            }
        }
    }
    Button(enabled = dirty, onClick = {
        scope.launch {
            io { client.saveApps(pin, s, newOnes.map { it.pkg }) }.onSuccess { onMsg("Applications enregistrées sur la TV."); dirty = false
                io { client.apps(pin) }.onSuccess { loaded = it; settings = it.settings } }
                .onFailure { e -> onMsg(why(e)); if (e is ParentalError && e.code == 409) io { client.apps(pin) }.onSuccess { loaded = it; settings = it.settings } }
        }
    }) { Text(if (dirty) "Enregistrer sur la TV" else "Enregistré") }
}

@Composable
private fun ReportsBlock(client: ParentalClient, profiles: List<ChildProfile>, pin: String, scope: CoroutineScope, onMsg: (String?) -> Unit) {
    val ctx = LocalContext.current
    ParentalInbox.init(ctx)
    var rl by remember(client) { mutableStateOf<ParentalClient.ReportsLoaded?>(null) }
    var cfg by remember(client) { mutableStateOf<ReportConfig?>(null) }
    var dirty by remember { mutableStateOf(false) }
    var tick by remember { mutableStateOf(0) }
    fun load() = scope.launch { io { client.reportsConfig(pin) }.onSuccess { rl = it; cfg = it.config; dirty = false }.onFailure { onMsg(why(it)) } }
    LaunchedEffect(client) { load() }

    Text("Rapports envoyés au téléphone du parent", style = MaterialTheme.typography.titleSmall)
    Text("Résumé du jour, résumé de la semaine et alertes immédiates, envoyés par Bluetooth au téléphone que vous désignez. Aucun serveur : la TV garde les rapports tant que le téléphone ne les a pas reçus (14 jours au plus).",
        style = MaterialTheme.typography.bodySmall)
    val r = rl; val c = cfg
    if (r == null || c == null) { LinearProgressIndicator(Modifier.fillMaxWidth()); return }

    val me = ParentalInbox.phoneId
    r.phones.forEach { p ->
        SwitchRow(p.name + if (p.id == me) "  (ce téléphone)" else "", if (p.designated) "Reçoit les rapports" else "Ne reçoit rien (le téléphone d'un enfant ne doit pas être désigné)", p.designated) { on ->
            scope.launch {
                io { if (on) client.designate(pin, p.id) else client.removeRecipient(pin, p.id) }.onSuccess { rl = it; cfg = it.config; onMsg(if (on) "${p.name} recevra les rapports." else "${p.name} ne reçoit plus les rapports.")
                    if (p.id == me) { Thread { ParentalInbox.sync(ctx); ParentalInbox.schedule(ctx) }.start() } }.onFailure { onMsg(why(it)) }
            }
        }
    }
    if (r.phones.isEmpty()) Text("Aucun téléphone de confiance sur cette TV : ajoutez-le avec « Ajouter un téléphone » sur la TV.", style = MaterialTheme.typography.bodySmall)
    if (ParentalInbox.designated == false && me != null && r.phones.none { it.id == me && it.designated })
        Text("Ce téléphone n'est pas désigné : cochez-le ci-dessus pour recevoir les rapports.", style = MaterialTheme.typography.bodySmall)

    fun edit(f: (ReportConfig) -> ReportConfig) { cfg = f(c); dirty = true }
    Picker("Résumé quotidien à", TIMES.map { TimeWindow.fmt(it) }, TIMES.indexOf(c.dailyAtMin).coerceAtLeast(0)) { i -> edit { it.copy(dailyAtMin = TIMES[i]) } }
    Picker("Résumé de la semaine", DAYS, c.weeklyDow - 1) { i -> edit { it.copy(weeklyDow = i + 1) } }
    profiles.forEach { p ->
        val o = c.options(p.id)
        fun ch(f: (ProfileReportOptions) -> ProfileReportOptions) = edit { cur -> cur.copy(profiles = cur.profiles + (p.id to f(cur.options(p.id)))) }
        Text(p.name, style = MaterialTheme.typography.labelMedium)
        CheckRow("Résumé quotidien", o.daily) { v -> ch { it.copy(daily = v) } }
        CheckRow("Résumé hebdomadaire", o.weekly) { v -> ch { it.copy(weekly = v) } }
        CheckRow("Alerte : temps ou heures atteints", o.alertLimit) { v -> ch { it.copy(alertLimit = v) } }
        CheckRow("Alerte : application bloquée essayée", o.alertBlocked) { v -> ch { it.copy(alertBlocked = v) } }
        CheckRow("Alerte : surveillance affaiblie (valable pour toute la TV)", o.alertTamper) { v -> ch { it.copy(alertTamper = v) } }
        CheckRow("Alerte : nouvelle application (valable pour toute la TV)", o.alertNewApp) { v -> ch { it.copy(alertNewApp = v) } }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(enabled = dirty, onClick = {
            scope.launch { io { client.saveReportsConfig(pin, c) }.onSuccess { rl = it; cfg = it.config; dirty = false; onMsg("Options des rapports enregistrées sur la TV.") }
                .onFailure { e -> onMsg(why(e)); if (e is ParentalError && e.code == 409) load() } }
        }) { Text(if (dirty) "Enregistrer" else "Enregistré") }
        OutlinedButton({ scope.launch { io { client.reportNow(pin) }.onSuccess { n -> onMsg(if (n == 0) "Aucun téléphone désigné : rien à envoyer." else "$n rapport(s) prêt(s) sur la TV."); load() }.onFailure { onMsg(why(it)) } } }) { Text("Préparer un rapport") }
    }
    OutlinedButton({
        scope.launch {
            val err = io { ParentalInbox.sync(ctx) }.getOrElse { why(it) }
            onMsg(err ?: "Rapports récupérés."); tick++
        }
    }) { Text("Recevoir maintenant") }
    ParentalInbox.lastError?.let { Text("Dernier essai : $it", style = MaterialTheme.typography.bodySmall) }

    InboxList(tick, onChanged = { tick++ })
}

@Composable
private fun InboxList(tick: Int, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    ParentalInbox.init(ctx)
    val list = remember(tick) { ParentalInbox.inbox.history() }
    val unread = remember(tick) { ParentalInbox.inbox.unread() }
    Text("Rapports reçus sur ce téléphone" + if (unread > 0) " ($unread nouveau(x))" else "", style = MaterialTheme.typography.titleSmall)
    if (list.isEmpty()) { Text("Aucun rapport reçu pour l'instant.", style = MaterialTheme.typography.bodySmall); return }
    val f = remember { java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.FRANCE) }
    list.take(30).forEach { r ->
        var open by remember(r.id) { mutableStateOf(false) }
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row { Column(Modifier.weight(1f)) {
                    Text(ReportText.title(r) + if (!r.read) "  •" else "", style = MaterialTheme.typography.titleSmall)
                    Text("${f.format(java.util.Date(r.ts))} · ${ReportText.text(r)}", style = MaterialTheme.typography.bodySmall)
                }; TextButton({ open = !open }) { Text(if (open) "Réduire" else "Détail") } }
                if (open) ReportDetail(r)
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton({ ParentalInbox.inbox.markAllRead(); onChanged() }) { Text("Tout marquer comme lu") }
        OutlinedButton({ ParentalInbox.inbox.clear(); onChanged() }) { Text("Effacer les rapports reçus") }
    }
}

/** Per-profile / per-app view of one report. */
@Composable
private fun ReportDetail(r: StoredReport) {
    @Suppress("UNCHECKED_CAST") val apps = (r.body[if (r.kind == "weekly") "topApps" else "apps"] as? List<Map<String, Any?>>).orEmpty()
    apps.forEach { a -> Text("• ${a["label"]} : ${ReportText.minutes((a["min"] as? Number)?.toLong() ?: 0)}", style = MaterialTheme.typography.bodySmall) }
    @Suppress("UNCHECKED_CAST") (r.body["kinds"] as? Map<String, Any?>)?.let { k ->
        Text("Dans CastBridge-TV : lecture ${k["play"]} min · jeux ${k["games"]} min · téléchargements ${k["downloads"]} min · autres applications ${k["apps"]} min", style = MaterialTheme.typography.bodySmall)
    }
    @Suppress("UNCHECKED_CAST") (r.body["days"] as? List<Map<String, Any?>>)?.forEach { d -> Text("${d["day"]} : ${ReportText.minutes((d["totalMin"] as? Number)?.toLong() ?: 0)}", style = MaterialTheme.typography.bodySmall) }
    @Suppress("UNCHECKED_CAST") (r.body["blocked"] as? List<Map<String, Any?>>).orEmpty().forEach { b -> Text("⛔ ${b["what"]} — ${b["why"]}", style = MaterialTheme.typography.bodySmall) }
    @Suppress("UNCHECKED_CAST") (r.body["newApps"] as? List<Map<String, Any?>>).orEmpty().takeIf { it.isNotEmpty() }?.let { n -> Text("Nouvelles applications : ${n.joinToString { it["label"].toString() }}", style = MaterialTheme.typography.bodySmall) }
    @Suppress("UNCHECKED_CAST") (r.body["tamper"] as? List<Map<String, Any?>>).orEmpty().forEach { t -> Text("⚠ ${t["what"]}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    @Suppress("UNCHECKED_CAST") (r.body["supervision"] as? Map<String, Any?>)?.let { Text(it["label"].toString(), style = MaterialTheme.typography.bodySmall) }
}
