package castbridge.sender

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.os.Process
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import castbridge.core.trust.DiagReport
import castbridge.core.trust.DiagStep
import castbridge.core.trust.LinkAction
import castbridge.core.trust.LinkMachine
import castbridge.core.trust.LinkState
import castbridge.core.trust.LinkView
import castbridge.core.trust.PairStep
import castbridge.core.trust.RouteKind
import castbridge.core.trust.Tone
import castbridge.core.trust.Candidates
import castbridge.core.trust.SavedTv
import castbridge.core.trust.TvCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Samsung "Dual App" / Secure Folder / work-profile copies run as another Android user and are refused Bluetooth access. */
fun isSecondaryUserCopy() = Process.myUid() / 100000 != 0

private val GOOD = Color(0xFF35C08A)      // success, branding/design-tokens.json
private val WARN = Color(0xFFF5B025)      // primary / warning

/** What a card shows when the link is not connected yet: the state machine's own wording, one button, steady (no flapping). */
private val idleView = LinkMachine().view(LinkMachine.Model())

/** State of the plug-and-play link, with its indicator dot, for the home screen. Text, tone and the single action come from [LinkView]. */
@Composable
fun TvLinkStatus(link: LinkUi, onAdd: () -> Unit, onManage: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    var diag by remember { mutableStateOf(false) }
    val view = when (link) {
        LinkUi.NoTv -> idleView
        is LinkUi.Connected -> link.view ?: LinkMachine().view(LinkMachine.Model(shown = LinkState.Connected(RouteKind.of(link.session.route), link.session.tv.name), tvName = link.session.tv.name))
        is LinkUi.Status -> link.view
    }
    val tv: SavedTv? = when (link) { is LinkUi.Connected -> link.session.tv; is LinkUi.Status -> link.tv; else -> null }
    // same signalling as CastBridge-TV (castbridge.core.ux.TvSignal): green works, orange degraded, red cannot, black inactive
    val level = castbridge.core.ux.TvSignal.phoneLevel(view.state)
    val dot = if (level == castbridge.core.ux.SignalLevel.BLACK) cs.outline else Color(castbridge.core.ux.SignalColors.of(level))
    fun startIt(intent: Intent) { runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    fun act(a: LinkAction) = when (a) {
        LinkAction.ADD_TV, LinkAction.PAIR, LinkAction.ENTER_CODE -> onAdd()
        LinkAction.REASSOCIATE -> { tv?.let { TvLinkManager.requestReassociate(it.address) }; onAdd() }
        LinkAction.RETRY -> TvLinkManager.retryNow()
        LinkAction.ENABLE_BLUETOOTH -> startIt(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        LinkAction.GRANT_PERMISSION -> startIt(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
        LinkAction.REMOVE_BOND -> startIt(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))      // the documented way: removeBond() is unreliable on Android 14
        LinkAction.NONE -> {}
    }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(dot))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(view.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 2)
            Text(view.detail, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            view.hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = WARN) }
        }
        Column(horizontalAlignment = Alignment.End) {
            if (view.action != LinkAction.NONE) Button(onClick = { act(view.action) }) { Text(view.action.label) }
            Row {
                TextButton(onClick = onManage) { Text("Mes TV") }
                if (link != LinkUi.NoTv) TextButton(onClick = { diag = true }) { Text("Diagnostic") }
            }
        }
    }
    if (diag) DiagnosticDialog(onDismiss = { diag = false })
}

/** « Diagnostic Bluetooth »: every step with OK / KO and the exact next action, and a copyable report without any secret. */
@Composable
fun DiagnosticDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val steps = remember { mutableStateListOf<DiagStep>() }
    var report by remember { mutableStateOf<DiagReport?>(null) }
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { report = TvLinkManager.diagnose { s -> steps.add(s) } } }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Diagnostic Bluetooth") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                steps.toList().forEach { st ->
                    Row(verticalAlignment = Alignment.Top) {
                        val (icon, tint) = when (st.status) {
                            DiagStep.Status.OK -> Icons.Filled.CheckCircle to GOOD
                            DiagStep.Status.KO -> Icons.Filled.ErrorOutline to cs.error
                            DiagStep.Status.SKIPPED -> Icons.Filled.RemoveCircleOutline to cs.outline
                            DiagStep.Status.INFO -> Icons.Filled.Info to cs.primary
                        }
                        Icon(icon, null, Modifier.size(20.dp), tint = tint); Spacer(Modifier.width(8.dp))
                        Column {
                            Text(st.label + if (st.detail.isNotEmpty()) " : " + st.detail else "", style = MaterialTheme.typography.bodyMedium)
                            if (st.status == DiagStep.Status.KO && st.next != null) Text("→ " + st.next!!.title + ". " + st.next!!.detail, style = MaterialTheme.typography.bodySmall, color = cs.error)
                        }
                    }
                }
                if (report == null) Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text("Vérification en cours…") }
                else Text(if (report!!.ok) "Tout est en ordre." else "Premier problème : ${report!!.firstFailure!!.label}.", fontWeight = FontWeight.SemiBold)
            }
        },
        confirmButton = {
            TextButton(enabled = report != null, onClick = {
                val cm = ctx.getSystemService(ClipboardManager::class.java)
                runCatching { cm?.setPrimaryClip(ClipData.newPlainText("Diagnostic Bluetooth", report!!.text())) }
            }) { Text("Copier le rapport") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fermer") } })
}

/** « Ajouter ma TV »: permissions and help, finding the TV, Android's pairing, then the owner's OK on the TV. */
@SuppressLint("MissingPermission")
@Composable
fun AddTvFlow(onClose: () -> Unit, onAdded: (SavedTv) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val (granted, askUi) = rememberBtPermission()
    val adapter = remember { ctx.getSystemService(BluetoothManager::class.java)?.adapter }
    var btOn by remember { mutableStateOf(runCatching { adapter?.isEnabled }.getOrNull() == true) }
    val enable = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { btOn = runCatching { adapter?.isEnabled }.getOrNull() == true }
    val finder = remember { BtFinder(ctx) }
    val found by finder.found.collectAsState()
    val scanning by finder.scanning.collectAsState()
    var step by remember { mutableStateOf<PairStep?>(null) }
    var chosen by remember { mutableStateOf<TvCandidate?>(null) }
    var showOthers by remember { mutableStateOf(false) }
    var round by remember { mutableIntStateOf(0) }
    val cs = MaterialTheme.colorScheme

    DisposableEffect(granted, btOn, round) {
        if (granted && btOn) finder.start()
        onDispose { finder.stop() }
    }

    fun pair(c: TvCandidate) {
        chosen = c; step = PairStep.Bonding
        finder.stop()
        scope.launch(Dispatchers.IO) { TvLinkManager.pair(c) { step = it } }
    }

    // « Réassocier »: the TV was forgotten locally, the whole flow starts by itself (guided stale-bond repair, waiting for « Ajouter un téléphone »)
    LaunchedEffect(granted, btOn) {
        if (granted && btOn && step == null) TvLinkManager.takeReassociate()?.let { tv -> pair(TvCandidate(tv.address, tv.name, bonded = true, hasCbt1 = true)) }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
            Text("Ajouter ma TV", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }
        if (isSecondaryUserCopy()) Card(colors = CardDefaults.cardColors(containerColor = cs.errorContainer)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Cette copie de l'app ne peut pas utiliser le Bluetooth", fontWeight = FontWeight.SemiBold, color = cs.onErrorContainer)
                Text("Vous utilisez une copie « Dual App » (ou Dossier sécurisé). Samsung lui interdit le Bluetooth. Ouvrez CastBridge depuis l'icône d'origine, ou saisissez le code de la TV à la place.",
                    style = MaterialTheme.typography.bodyMedium, color = cs.onErrorContainer)
            }
        }
        val st = step
        when {
            adapter == null -> Text("Ce téléphone n'a pas de Bluetooth. Saisissez le code de la TV à la place.", color = cs.error)
            !granted -> askUi()
            !btOn -> {
                Text("Le Bluetooth du téléphone est éteint.", style = MaterialTheme.typography.bodyLarge)
                Button(onClick = { runCatching { enable.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) } }) { Text("Activer le Bluetooth") }
            }
            st != null -> PairProgress(st, chosen, onRetry = { step = null; round++ }, onCancel = { step = null; chosen = null }, onDone = onAdded)
            else -> {
                Text("1.  Sur la TV, ouvrez CastBridge TV puis « Ajouter un téléphone ».\n2.  Choisissez votre TV dans la liste.\n3.  Comparez le code affiché sur les deux écrans, puis validez sur la TV avec la télécommande.",
                    style = MaterialTheme.typography.bodyMedium)
                val tvs = Candidates.tvs(found)
                val others = Candidates.others(found)
                if (scanning && tvs.isEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)); Text("Recherche de la TV…", color = cs.onSurfaceVariant)
                }
                tvs.forEach { c ->
                    ElevatedCard(Modifier.fillMaxWidth().clickable { pair(c) }) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Tv, null, tint = cs.primary); Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(c.name.ifBlank { "TV CastBridge" }, style = MaterialTheme.typography.titleMedium)
                                Text(if (c.bonded) "Déjà associée en Bluetooth" else "Visible à proximité", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                            }
                            Icon(Icons.Filled.ChevronRight, null)
                        }
                    }
                }
                if (!scanning && tvs.isEmpty()) Text("Aucune TV CastBridge trouvée. Vérifiez que la TV affiche « Ajouter un téléphone » (compte à rebours en cours) et que son Bluetooth est allumé.",
                    color = cs.onSurfaceVariant)
                OutlinedButton(onClick = { round++ }) { Icon(Icons.Filled.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Chercher à nouveau") }
                if (others.isNotEmpty()) {
                    TextButton(onClick = { showOthers = !showOthers }) { Text(if (showOthers) "Masquer les autres appareils" else "Ma TV n'apparaît pas (${others.size} autre(s) appareil(s))") }
                    if (showOthers) others.forEach { c ->
                        ListItem(modifier = Modifier.clickable { pair(c) }, headlineContent = { Text(c.name.ifBlank { c.address }) },
                            supportingContent = { Text(if (c.hasCbt1 == false) "N'est pas une TV CastBridge" else "Vérification en cours…") },
                            leadingContent = { Icon(Icons.Filled.Bluetooth, null) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
                    }
                }
            }
        }
        TextButton(onClick = onClose) { Text("Saisir le code de la TV à la place") }
    }
}

@Composable
private fun PairProgress(step: PairStep, tv: TvCandidate?, onRetry: () -> Unit, onCancel: () -> Unit, onDone: (SavedTv) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        when (step) {
            PairStep.Bonding -> {
                CircularProgressIndicator()
                Text("Association avec ${tv?.name ?: "la TV"}", style = MaterialTheme.typography.titleMedium)
                Text(step.advice.detail, textAlign = TextAlign.Center, color = cs.onSurfaceVariant)
                TextButton(onClick = onCancel) { Text("Annuler") }
            }
            PairStep.StaleBond -> {
                Icon(Icons.Filled.BluetoothDisabled, null, Modifier.size(48.dp), tint = WARN)
                Text(step.advice.title, style = MaterialTheme.typography.titleMedium)
                Text(step.advice.detail, textAlign = TextAlign.Center, color = cs.onSurfaceVariant)
                Button(onClick = { runCatching { ctx.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }) { Text(step.advice.action.label) }
                Text("Cet écran continue tout seul dès que l'association est supprimée.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                TextButton(onClick = onCancel) { Text("Annuler") }
            }
            is PairStep.WaitingTvWindow -> {
                CircularProgressIndicator()
                Text(step.advice.title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Text(step.advice.detail, textAlign = TextAlign.Center, color = cs.onSurfaceVariant)
                TextButton(onClick = onCancel) { Text("Annuler") }
            }
            PairStep.WaitingOwner -> {
                CircularProgressIndicator()
                Text(step.advice.title, style = MaterialTheme.typography.titleMedium)
                Text(step.advice.detail, textAlign = TextAlign.Center, color = cs.onSurfaceVariant)
            }
            is PairStep.Done -> {
                Icon(Icons.Filled.CheckCircle, null, Modifier.size(64.dp), tint = GOOD)
                Text(step.advice.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(step.advice.detail, textAlign = TextAlign.Center, color = cs.onSurfaceVariant)
                Button(onClick = { onDone(step.session.tv) }) { Text("Terminer") }
            }
            is PairStep.Failed -> {
                Icon(Icons.Filled.ErrorOutline, null, Modifier.size(56.dp), tint = cs.error)
                Text(step.advice.title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Text(step.advice.detail, textAlign = TextAlign.Center)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (step.canRetry) Button(onClick = onRetry) { Text("Réessayer") }
                    OutlinedButton(onClick = onCancel) { Text("Annuler") }
                }
            }
        }
    }
}

/** « Mes TV »: which TV is the default, forget one. Several TVs are allowed; the default one connects at startup. */
@Composable
fun ManageTvsDialog(onDismiss: () -> Unit, onAdd: () -> Unit) {
    var version by remember { mutableIntStateOf(0) }
    val tvs = remember(version) { TvLinkManager.saved.list() }
    val def = remember(version) { TvLinkManager.saved.default()?.address }
    var confirm by remember { mutableStateOf<SavedTv?>(null) }
    var diag by remember { mutableStateOf(false) }
    if (diag) DiagnosticDialog(onDismiss = { diag = false })
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Mes TV") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (tvs.isEmpty()) Text("Aucune TV ajoutée.")
                tvs.forEach { t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(t.address == def, { TvLinkManager.makeDefault(t.address); version++ })
                        Column(Modifier.weight(1f)) {
                            Text(t.name, maxLines = 1)
                            Text(if (t.address == def) "TV par défaut" else "Toucher pour en faire la TV par défaut", style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton({ confirm = t }) { Icon(Icons.Filled.Delete, "Oublier ${t.name}") }
                    }
                }
                if (tvs.size > 1) Text("La TV par défaut est celle à laquelle le téléphone se connecte à l'ouverture.", style = MaterialTheme.typography.bodySmall)
                if (tvs.isNotEmpty()) TextButton(onClick = { diag = true }) { Text("Diagnostic Bluetooth") }
            }
        },
        confirmButton = { TextButton(onClick = { onDismiss(); onAdd() }) { Text("Ajouter une TV") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fermer") } })
    confirm?.let { t ->
        AlertDialog(onDismissRequest = { confirm = null }, title = { Text("Oublier ${t.name} ?") },
            text = { Text("Le téléphone ne se connectera plus à cette TV sans code. Pour retirer aussi ce téléphone de la TV : écran « Ajouter un téléphone » de la TV › Retirer.") },
            confirmButton = { TextButton(onClick = { TvLinkManager.forget(t.address); confirm = null; version++ }) { Text("Oublier") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Annuler") } })
    }
}
