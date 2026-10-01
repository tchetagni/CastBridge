package castbridge.sender

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
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
import castbridge.core.trust.BtUnavailable
import castbridge.core.trust.Candidates
import castbridge.core.trust.SavedTv
import castbridge.core.trust.TvCandidate
import castbridge.core.tv.LinkPlanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Samsung "Dual App" / Secure Folder / work-profile copies run as another Android user and are refused Bluetooth access. */
fun isSecondaryUserCopy() = Process.myUid() / 100000 != 0

private val GOOD = Color(0xFF35C08A)      // success, branding/design-tokens.json
private val WARN = Color(0xFFF5B025)      // primary / warning

/** One-line state of the plug-and-play link, with its indicator dot, for the home screen. */
@Composable
fun TvLinkStatus(link: LinkUi, onAdd: () -> Unit, onManage: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val (dot, title, sub) = when (link) {
        LinkUi.NoTv -> Triple(cs.outline, "Aucune TV ajoutée", "Ajoutez votre TV : pas de code à saisir.")
        is LinkUi.Connecting -> Triple(WARN, "Connexion à ${link.tv.name}…", "Recherche de la TV par Bluetooth")
        is LinkUi.Connected -> Triple(GOOD, "${link.session.tv.name} connectée", when (link.session.route) {
            is LinkPlanner.Route.Lan -> "Par le Wi-Fi de la maison"
            is LinkPlanner.Route.Direct -> "Par Wi-Fi Direct"
            else -> "Par Bluetooth seulement : plus lent (Wi-Fi différent ?)"
        })
        is LinkUi.Absent -> Triple(cs.outline, "${link.tv.name} est introuvable",
            if (link.failures >= 3) "Allumez la TV et restez à proximité. Si vous avez réinstallé CastBridge-TV, réassociez la TV."
            else "Allumez la TV et restez à proximité : la connexion est automatique.")
        is LinkUi.Refused -> Triple(cs.error, link.tv.name, link.message)
        is LinkUi.BluetoothProblem -> Triple(cs.error, "Bluetooth indisponible", when (link.reason) {
            BtUnavailable.Reason.OFF -> "Activez le Bluetooth du téléphone."
            BtUnavailable.Reason.NO_PERMISSION -> "Autorisez « Appareils à proximité » pour CastBridge."
            BtUnavailable.Reason.NO_ADAPTER -> "Ce téléphone n'a pas de Bluetooth."
        })
    }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(dot))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        val needsAdd = link == LinkUi.NoTv || (link is LinkUi.Refused && link.needsPairing)
        if (link is LinkUi.Absent && link.failures >= 3) {
            // several failures in a row: most often the TV was reinstalled and forgot this phone: one tap forgets it and starts the pairing again
            Column(horizontalAlignment = Alignment.End) {
                Button(onClick = { TvLinkManager.forget(link.tv.address); onAdd() }) { Text("Réassocier") }
                TextButton(onClick = onManage) { Text("Mes TV") }
            }
        } else TextButton(onClick = if (needsAdd) onAdd else onManage) { Text(if (needsAdd) "Ajouter" else "Mes TV") }
    }
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
    var step by remember { mutableStateOf<TvLinkManager.PairStep?>(null) }
    var chosen by remember { mutableStateOf<TvCandidate?>(null) }
    var showOthers by remember { mutableStateOf(false) }
    var round by remember { mutableIntStateOf(0) }
    val cs = MaterialTheme.colorScheme

    DisposableEffect(granted, btOn, round) {
        if (granted && btOn) finder.start()
        onDispose { finder.stop() }
    }

    fun pair(c: TvCandidate) {
        chosen = c; step = TvLinkManager.PairStep.Bonding
        finder.stop()
        scope.launch(Dispatchers.IO) { TvLinkManager.pair(c) { step = it } }
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
private fun PairProgress(step: TvLinkManager.PairStep, tv: TvCandidate?, onRetry: () -> Unit, onCancel: () -> Unit, onDone: (SavedTv) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        when (step) {
            TvLinkManager.PairStep.Bonding -> {
                CircularProgressIndicator()
                Text("Association avec ${tv?.name ?: "la TV"}", style = MaterialTheme.typography.titleMedium)
                Text("Android affiche un code sur le téléphone et sur la TV. S'ils sont identiques, validez sur les deux.", textAlign = TextAlign.Center, color = cs.onSurfaceVariant)
                TextButton(onClick = onCancel) { Text("Annuler") }
            }
            TvLinkManager.PairStep.WaitingOwner -> {
                CircularProgressIndicator()
                Text("Validez sur la TV", style = MaterialTheme.typography.titleMedium)
                Text("La TV demande « Autoriser ce téléphone à piloter cette TV ? ». Choisissez « Autoriser » avec la télécommande.", textAlign = TextAlign.Center, color = cs.onSurfaceVariant)
            }
            is TvLinkManager.PairStep.Done -> {
                Icon(Icons.Filled.CheckCircle, null, Modifier.size(64.dp), tint = GOOD)
                Text("${step.tv.name} est ajoutée", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Elle se connectera toute seule quand vous ouvrirez l'app, sans code.", textAlign = TextAlign.Center, color = cs.onSurfaceVariant)
                Button(onClick = { onDone(step.tv) }) { Text("Terminer") }
            }
            is TvLinkManager.PairStep.Failed -> {
                Icon(Icons.Filled.ErrorOutline, null, Modifier.size(56.dp), tint = cs.error)
                Text(step.message, textAlign = TextAlign.Center)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (step.retry) Button(onClick = onRetry) { Text("Réessayer") }
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
