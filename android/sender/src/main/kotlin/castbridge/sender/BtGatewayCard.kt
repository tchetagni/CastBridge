package castbridge.sender

import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import castbridge.core.ux.BondedDevice
import castbridge.core.ux.BtFallback
import castbridge.core.ux.BtGatewayView
import castbridge.core.ux.FallbackAction
import castbridge.core.ux.FallbackDecision
import castbridge.core.ux.FallbackInput
import castbridge.core.ux.GatewayGlance
import castbridge.core.ux.GatewayOffer
import castbridge.core.ux.Gesture
import castbridge.core.ux.LinkLight
import castbridge.core.ux.LinkSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/*
 * Thin screens for the « Passerelle Bluetooth » made easy to reach (owner, 2026-10-03): the full-width button of the home and of « Trouvons votre
 * TV » / « TV injoignable », the running card with the lines to type on the Mac, the strip over the other tabs, and the automatic switch to
 * Bluetooth when the TV is not on the Wi-Fi. Every decision is castbridge.core.ux.BtGatewayView / BtFallback (tested on the JVM); the gateway
 * itself is BtSshGatewayService, unchanged (loopback by default, LAN exposure opt-in with its warning, the TV still checks the code or token).
 */

/** « Basculer sur Bluetooth quand le Wi-Fi est absent » (on by default) and what the switch remembers between two looks. */
object BtFallbackState {
    private const val PREFS = "castbridge_btgw"
    private const val KEY_AUTO = "auto_bt"
    fun auto(ctx: Context): Boolean = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_AUTO, true)
    fun setAuto(ctx: Context, on: Boolean) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_AUTO, on).apply()
    /** The running gateway was started by the switch (only then does the switch stop it when the Wi-Fi comes back). */
    @Volatile var autoStarted = false
    @Volatile var lastStartAt: Long? = null
    /** The owner pressed « Arrêter »: no automatic restart until the TV is seen on the Wi-Fi again. */
    @Volatile var userStopped = false

    fun stopByUser(ctx: Context) { userStopped = true; autoStarted = false; BtSshGatewayService.stop(ctx) }
}

private const val CB_UUID_PREFIX = "7c5e3b9a-4d2f-4c61-9b0e-cb00000000"
private val TV_CLASSES = setOf(BluetoothClass.Device.AUDIO_VIDEO_VIDEO_DISPLAY_AND_LOUDSPEAKER, BluetoothClass.Device.AUDIO_VIDEO_VIDEO_MONITOR, BluetoothClass.Device.AUDIO_VIDEO_SET_TOP_BOX)

/** The phone's bonded devices as the core reads them (needs the Bluetooth permission; empty without it). */
@SuppressLint("MissingPermission")
internal fun bondedDevices(ctx: Context): List<BondedDevice> = runCatching {
    ctx.getSystemService(BluetoothManager::class.java)?.adapter?.bondedDevices.orEmpty().map { d ->
        BondedDevice(d.name ?: d.address, d.address, tvClass = d.bluetoothClass?.deviceClass in TV_CLASSES,
            castBridgeService = d.uuids.orEmpty().any { it.uuid.toString().startsWith(CB_UUID_PREFIX, ignoreCase = true) })
    }
}.getOrDefault(emptyList())

/** GET <base>/api/hello (no code needed): does a CastBridge TV answer there? */
private fun answers(base: String): Boolean = runCatching {
    (URL("$base/api/hello").openConnection() as HttpURLConnection).run {
        connectTimeout = 2500; readTimeout = 4000
        try { responseCode == 200 && inputStream.bufferedReader().use { it.readText() }.contains("castbridge-tv") } finally { disconnect() }
    }
}.getOrDefault(false)

private fun knownTvs(): Set<String> = runCatching { TvLinkManager.saved.list().map { it.address }.toSet() }.getOrDefault(emptySet())

/**
 * The automatic switch, looked at every few seconds while the CastBridge TV tab is shown. [lanBases]: the TV's Wi-Fi addresses to try (the chosen
 * TV, or every TV found when none is chosen yet); [sessionOnWifi]: the trusted link already reaches it on the Wi-Fi. Returns the current decision:
 * `viaBluetooth` = use [BtFallback.LOOPBACK_BASE] as the TV's address, `signal` = the honest state line (null: the Wi-Fi line stays).
 */
@Composable
fun rememberBtFallback(lanBases: List<String>, sessionOnWifi: Boolean): State<FallbackDecision> {
    val ctx = LocalContext.current
    val (granted, _) = rememberBtPermission()
    val decision = remember { mutableStateOf(FallbackDecision(FallbackAction.None, false, null)) }
    val bases by rememberUpdatedState(lanBases)
    val onWifi by rememberUpdatedState(sessionOnWifi)
    LaunchedEffect(granted) {
        var lastWifi = System.currentTimeMillis()
        while (true) {
            val now = System.currentTimeMillis()
            val st = BtSshGatewayService.state.value
            val wifi = onWifi || withContext(Dispatchers.IO) { bases.filterNot { "127.0.0.1" in it }.any { answers(it) } }
            if (wifi) { lastWifi = now; BtFallbackState.userStopped = false }
            if (!st.running) BtFallbackState.autoStarted = false
            val bonded = if (granted) withContext(Dispatchers.IO) { bondedDevices(ctx) } else emptyList()
            val known = knownTvs()
            val answering = st.api.running && withContext(Dispatchers.IO) { answers(BtFallback.LOOPBACK_BASE) }
            val d = BtFallback.decide(FallbackInput(
                enabled = BtFallbackState.auto(ctx) && !BtFallbackState.userStopped, wifiTv = wifi, absentMs = now - lastWifi,
                target = BtGatewayView.preferred(bonded, known), bondedTvs = BtGatewayView.candidates(bonded, known).size,
                apiRunning = st.api.running, gatewayTv = BtSshGatewayService.apiTunnelTv(), autoStarted = BtFallbackState.autoStarted,
                sshRunning = st.ssh.running, answering = answering, failure = st.api.message ?: st.message,
                sinceAutoStartMs = BtFallbackState.lastStartAt?.let { now - it }))
            when (val a = d.action) {
                is FallbackAction.StartApi -> {
                    BtFallbackState.autoStarted = true; BtFallbackState.lastStartAt = now
                    runCatching { BtSshGatewayService.start(ctx, a.tv.address, a.tv.name, exposeLan = false, ssh = false, api = true) }
                }
                FallbackAction.Stop -> { BtFallbackState.autoStarted = false; BtSshGatewayService.stop(ctx) }
                FallbackAction.None -> {}
            }
            decision.value = d
            delay(3000)
        }
    }
    return decision
}

/** The state line of the switch: orange (Bluetooth only, slower) or red (cause and one gesture). */
@Composable
fun LinkSignalLine(s: LinkSignal?, onGesture: (Gesture) -> Unit) {
    s ?: return
    val cs = MaterialTheme.colorScheme
    val tone = when (s.light) { LinkLight.GREEN -> Cb.success; LinkLight.ORANGE -> Cb.warning; LinkLight.RED -> cs.error; LinkLight.BLACK -> cs.onSurface }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(12.dp), shape = RoundedCornerShape(6.dp), color = tone) {}
            Spacer(Modifier.width(10.dp))
            Text(s.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = tone)
            s.gesture?.let { g -> TextButton(onClick = { onGesture(g) }) { Text(g.label) } }
        }
        s.cause?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant) }
        s.slowNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant) }
    }
}

/** What a gesture of the state line does (thin: the choice was made by BtFallback). */
fun runGesture(ctx: Context, g: Gesture, onAddTv: () -> Unit, onChoose: () -> Unit) {
    when (g) {
        Gesture.ADD_TV_BT -> onAddTv()
        Gesture.START_GATEWAY -> onChoose()
        Gesture.STOP_OTHER -> BtFallbackState.stopByUser(ctx)
        Gesture.RETRY -> {
            BtFallbackState.lastStartAt = null; BtFallbackState.userStopped = false
            if (BtFallbackState.autoStarted && !BtSshGatewayService.state.value.ssh.running) { BtFallbackState.autoStarted = false; BtSshGatewayService.stop(ctx) }
        }
    }
}

/** The home's block when the TV does not answer on the Wi-Fi: the state line of the switch, then the button or the running card. */
@Composable
fun BtGatewayHomeBlock(signal: LinkSignal?, tvReachable: Boolean, onAddTv: () -> Unit, onChoose: () -> Unit, showAddTv: Boolean = true) {
    val ctx = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LinkSignalLine(signal) { g -> runGesture(ctx, g, onAddTv, onChoose) }
        BtGatewayCard(tvReachable, onAddTv, onChoose, showAddTv)
    }
}

/**
 * « Passerelle Bluetooth » on the home and on « Trouvons votre TV » / « TV injoignable »: a full-width button with one line of explanation, one tap
 * when a single bonded TV is known (API + SSH, loopback only); while it runs, its card with the lines to type on the Mac and « Arrêter ».
 */
@Composable
fun BtGatewayCard(tvReachable: Boolean, onAddTv: () -> Unit, onChoose: () -> Unit, showAddTv: Boolean = true) {
    val ctx = LocalContext.current
    val (granted, _) = rememberBtPermission()
    val st by BtSshGatewayService.state.collectAsState()
    val bonded = remember(granted, st.running) { if (granted) bondedDevices(ctx) else emptyList() }
    when (val offer = BtGatewayView.offer(tvReachable, bonded, knownTvs(), st.running, canListBonded = granted)) {
        GatewayOffer.Hidden -> {}
        GatewayOffer.Running -> RunningCard(st)
        is GatewayOffer.AddTv -> if (showAddTv) StartButton(offer.button, offer.hint, BtGatewayView.ADD_TV_HELP, st.message, onAddTv)
        is GatewayOffer.Start -> StartButton(offer.button, offer.hint, if (offer.preselected == null && offer.candidates.size > 1) BtGatewayView.CHOOSE_HELP else null, st.message) {
            offer.preselected?.let { d ->
                val s = BtGatewayView.oneTapStart()
                BtFallbackState.userStopped = false; BtFallbackState.autoStarted = false
                BtSshGatewayService.start(ctx, d.address, d.name, s.exposeLan, s.ssh, s.api)
            } ?: onChoose()
        }
    }
}

/** The full-width button with its icon and its one line of explanation. */
@Composable
private fun StartButton(button: String, hint: String, help: String?, error: String?, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Button(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Icon(Icons.Filled.Bluetooth, null); Spacer(Modifier.width(10.dp))
            Text(button, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(hint, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        help?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant) }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.error) }
    }
}

/** The running gateway: where it goes, the lines to type on the Mac (each with « Copier », the code always as <code>), « Arrêter ». */
@Composable
private fun RunningCard(st: BtSshGatewayService.State) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val lan = st.ssh.listen.contains("0.0.0.0")
            val g = BtGatewayView.glance(true, st.tv, st.ssh.running, st.api.running, lan)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.BluetoothConnected, null, tint = cs.primary); Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(g?.title ?: BtGatewayView.TITLE, style = MaterialTheme.typography.titleSmall)
                    g?.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (g.warning) cs.error else cs.onSurfaceVariant) }
                }
                OutlinedButton(onClick = { BtFallbackState.stopByUser(ctx) }) { Text("Arrêter") }
            }
            val cmds = BtGatewayView.macCommands(st.ssh.running, st.api.running, lan && st.ssh.running)
            if (cmds.isNotEmpty()) Text(BtGatewayView.RUNNING_HELP, style = MaterialTheme.typography.bodySmall)
            cmds.forEach { c ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(c.label, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                        Text(c.line, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    TextButton(onClick = {
                        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("CastBridge", BtGatewayView.forClipboard(c.line)))
                        Toast.makeText(ctx, "Copié", Toast.LENGTH_SHORT).show()
                    }) { Icon(Icons.Filled.ContentCopy, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Copier") }
                }
            }
            st.ssh.message?.let { Text("SSH : $it", style = MaterialTheme.typography.bodySmall, color = cs.error) }
            // started by the switch (API only): the SSH for the Mac is one tap away, still on the loopback only
            if (!st.ssh.running) BtSshGatewayService.apiTunnelTv()?.let { addr ->
                TextButton(onClick = { BtSshGatewayService.start(ctx, addr, st.tv, exposeLan = false, ssh = true, api = true) }) { Text("Ajouter le SSH (pour le Mac)") }
            }
            AutoSwitchRow()
        }
    }
}

/** « Basculer sur Bluetooth quand le Wi-Fi est absent »: on by default; turning it off stops a gateway the switch started. */
@Composable
fun AutoSwitchRow() {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(BtFallbackState.auto(ctx)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Basculer sur Bluetooth quand le Wi-Fi est absent", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Switch(on, { v ->
            on = v; BtFallbackState.setAuto(ctx, v)
            if (!v && BtFallbackState.autoStarted && !BtSshGatewayService.state.value.ssh.running) { BtFallbackState.autoStarted = false; BtSshGatewayService.stop(ctx) }
        })
    }
}

/** The gateway is running: one line over every tab but the home (which has the card), with « Arrêter » (same pattern as the queue strip). */
@Composable
fun GatewayStrip(g: GatewayGlance, onOpen: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    Surface(color = if (g.warning) cs.errorContainer else cs.tertiaryContainer, modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.BluetoothConnected, null, Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(g.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(g.detail, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = { BtFallbackState.stopByUser(ctx) }) { Text(g.action) }
        }
    }
}
