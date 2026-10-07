package castbridge.sender

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import castbridge.core.remote.BtRoute
import castbridge.core.remote.KeyAction
import castbridge.core.remote.RemoteDiagnostics
import castbridge.core.remote.RemoteKey
import castbridge.core.remote.RemoteSession
import castbridge.core.remote.RemoteTarget
import castbridge.core.remote.RouteStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * « Ma TV » : the three Bluetooth routes of the remote (docs/REMOTE.md) — state, a reversible test (volume + then −, then
 * "did you see the volume change?"), the "Bluetooth exclusivement" option and a copiable diagnostic without secrets.
 */
@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BtRoutesScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { RemotePrefs(ctx) }
    val status by RemoteController.status.collectAsState()
    val st by RemoteController.tvState.collectAsState()
    val hidState by BtHidRemote.state.collectAsState()
    val (btOk, btUi) = rememberBtPermission()
    val scope = rememberCoroutineScope()
    var btOnly by remember { mutableStateOf(prefs.btOnly) }
    var hidOn by remember { mutableStateOf(prefs.hidEnabled) }
    var results by remember { mutableStateOf(BtRoute.values().associateWith { prefs.routeResult(it) }) }
    var asking by remember { mutableStateOf<BtRoute?>(null) }
    var copied by remember { mutableStateOf(false) }
    val viaBt = status.link == RemoteSession.Link.CONNECTED && status.via == "Bluetooth"

    fun shown(r: BtRoute): RouteStatus = when (r) {
        BtRoute.NATIVE -> results[r] ?: if (viaBt) RouteStatus.AVAILABLE else RouteStatus.TO_TEST
        BtRoute.VENDOR -> if (st == null) results[r] ?: RouteStatus.TO_TEST else if (st?.vendorAvailable == true) results[r] ?: RouteStatus.AVAILABLE else RouteStatus.UNSUPPORTED
        BtRoute.HID -> when {
            !BtHidRemote.supported || hidState == BtHidRemote.State.UNSUPPORTED -> RouteStatus.UNSUPPORTED
            results[r] != null -> results[r]!!
            hidState == BtHidRemote.State.CONNECTED -> RouteStatus.TO_TEST
            else -> RouteStatus.AVAILABLE
        }
    }

    /** Volume + then volume −, through the route under test; then the owner says whether the TV reacted. */
    fun test(r: BtRoute) {
        scope.launch {
            withContext(Dispatchers.Default) {
                val (up, down) = RemoteKey.VOLUME_UP to RemoteKey.VOLUME_DOWN
                when (r) {
                    BtRoute.NATIVE -> { RemoteController.keyVia(up, RemoteTarget.APP); delay(700); RemoteController.keyVia(down, RemoteTarget.APP) }
                    BtRoute.VENDOR -> { RemoteController.keyVia(up, RemoteTarget.SYSTEM); delay(700); RemoteController.keyVia(down, RemoteTarget.SYSTEM) }
                    BtRoute.HID -> {
                        val was = RemoteController.hid.enabled
                        RemoteController.hid.enabled = true
                        RemoteController.hid.send(up, KeyAction.PRESS); delay(700); RemoteController.hid.send(down, KeyAction.PRESS)
                        RemoteController.hid.enabled = was
                    }
                }
            }
            asking = r
        }
    }
    fun record(r: BtRoute, ok: Boolean) {
        val v = if (ok) RouteStatus.CONFIRMED else RouteStatus.FAILED
        prefs.setRouteResult(r, v); results = results + (r to v)
        if (r == BtRoute.HID) RemoteController.hid.confirmed = ok
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Ma TV · Bluetooth", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }, navigationIcon = { IconButton(onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Fermer") } })
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!btOk) btUi()
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Bluetooth exclusivement", style = MaterialTheme.typography.titleSmall)
                        Text("La télécommande n'utilise jamais le Wi-Fi : utile quand le Wi-Fi de la TV est défaillant. Choisissez une TV appairée comme secours Bluetooth.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(btOnly, { btOnly = it; prefs.btOnly = it; RemoteController.restart(ctx) })
                }
            }

            RouteCard("A · CastBridge par Bluetooth", shown(BtRoute.NATIVE),
                "Pilote CastBridge TV (écrans, volume, muet) sans aucune adresse IP. " + (RemoteController.statusLine(status, btOnly) ?: "Non connectée en Bluetooth pour l'instant."),
                testEnabled = viaBt, onTest = { test(BtRoute.NATIVE) })
            RouteCard("B · Toute la TV (service du fabricant)", shown(BtRoute.VENDOR),
                "CastBridge TV relaie vos touches au service de télécommande de la TV : flèches, OK, Retour, Accueil, volume dans les autres applications, sans accessibilité ni Wi-Fi. " +
                    if (st == null) "Connectez-vous d'abord à la TV." else if (st?.vendorAvailable == true) "Service détecté sur la TV." else "Ce service n'existe pas sur cette TV.",
                testEnabled = st?.vendorAvailable == true, onTest = { test(BtRoute.VENDOR) })
            RouteCard("C · Le téléphone comme clavier Bluetooth", shown(BtRoute.HID),
                "Aucune app sur la TV : le téléphone se déclare clavier et télécommande. Désactivé tant que vous n'avez pas confirmé par un test que votre TV réagit. État : ${hidState.label}.",
                testEnabled = hidState == BtHidRemote.State.CONNECTED, onTest = { test(BtRoute.HID) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Activer cette voie", Modifier.weight(1f))
                    Switch(hidOn, { on ->
                        hidOn = on; prefs.hidEnabled = on; RemoteController.hid.enabled = on && results[BtRoute.HID] == RouteStatus.CONFIRMED
                        if (on) BtHidRemote.start(ctx) else BtHidRemote.stop()
                    }, enabled = btOk && BtHidRemote.supported)
                }
                if (hidOn && hidState != BtHidRemote.State.OFF) {
                    val paired = remember(hidState) {
                        if (btOk) ctx.getSystemService(BluetoothManager::class.java)?.adapter?.bondedDevices.orEmpty().toList() else emptyList()
                    }
                    Text("Choisissez la TV (déjà appairée) ou appairez-la depuis le menu Bluetooth de la TV en cherchant « CastBridge ».", style = MaterialTheme.typography.bodySmall)
                    paired.forEach { d -> TextButton({ BtHidRemote.connect(d) }) { Text("Connecter à ${d.name ?: "appareil"}") } }
                }
            }

            OutlinedButton(onClick = {
                val routes = BtRoute.values().associateWith { shown(it) }
                val version = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"
                val notes = listOf("Liaison : ${status.link} ${status.via ?: ""}", "Relais du fabricant (TV) : ${st?.vendorAvailable ?: "inconnu"}", "Clavier Bluetooth : ${hidState.label}", "Android ${Build.VERSION.SDK_INT}")
                (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("Diagnostic CastBridge", RemoteDiagnostics.report(version, btOnly, routes, notes)))
                copied = true
            }, modifier = Modifier.fillMaxWidth()) { Text(if (copied) "Diagnostic copié (sans code ni adresse)" else "Copier le diagnostic") }
        }
    }

    asking?.let { r ->
        AlertDialog(onDismissRequest = { asking = null }, title = { Text("Avez-vous vu le volume changer ?") },
            text = { Text("Le test a envoyé volume + puis volume − par « ${r.label} » (effet net nul).") },
            confirmButton = { TextButton({ record(r, true); asking = null }) { Text("Oui") } },
            dismissButton = { TextButton({ record(r, false); asking = null }) { Text("Non") } })
    }
}

@Composable
private fun RouteCard(title: String, state: RouteStatus, text: String, testEnabled: Boolean, onTest: () -> Unit, extra: @Composable ColumnScope.() -> Unit = {}) {
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.surface, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Text(state.label, style = MaterialTheme.typography.labelMedium,
                    color = when (state) { RouteStatus.CONFIRMED -> cs.primary; RouteStatus.FAILED, RouteStatus.UNSUPPORTED -> cs.error; else -> cs.onSurfaceVariant })
            }
            Text(text, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            extra()
            Button(onTest, enabled = testEnabled) { Text("Tester cette voie") }
        }
    }
}
