package castbridge.sender

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import castbridge.core.link.StateLine
import castbridge.core.link.WdDiagnostic
import castbridge.core.link.WdManualView
import castbridge.core.net.BoundRoute
import castbridge.core.trust.LinkSession
import castbridge.core.trust.SavedTv
import castbridge.core.tv.TvClient
import castbridge.core.ux.SignalLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

/*
 * Le bouton « Wi-Fi Direct » de la carte TV (accueil de CastBridge) : écran mince. Tout ce qui décide est dans core ([WdManualView], [WdManualLease],
 * [WdDiagnostic]) ; ici on montre la vue, on appelle `AutoWifiDirect.startNow` / `stopManual`, on copie le relevé. Le code de la TV n'est lu que pour le
 * passer à la mécanique (jamais affiché, jamais copié).
 */

/** Carte « Wi-Fi Direct » de l'onglet CastBridge TV : bouton, une ligne explicative, la cause quand il est grisé, la ligne d'état honnête, le relevé de terrain. */
@Composable
fun WdManualCard(tv: SavedTv?, session: LinkSession?, credential: String?, onPair: () -> Unit, onEnterCode: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val state by AutoWifiDirect.state.collectAsState()
    val manual by AutoWifiDirect.manual.collectAsState()
    val report by AutoWifiDirect.report.collectAsState()
    val measuring by AutoWifiDirect.measuring.collectAsState()
    var tick by remember { mutableIntStateOf(0) }                 // l'autorisation ou le Wi-Fi du téléphone peuvent changer hors de l'app
    var confirmLan by remember { mutableStateOf(false) }
    // la session manuelle tient 10 minutes hors de cet écran : il dit à l'exécutant quand il est devant l'usager
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        AutoWifiDirect.screenVisible(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) AutoWifiDirect.screenVisible(true) else if (e == Lifecycle.Event.ON_PAUSE) AutoWifiDirect.screenVisible(false) }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o); AutoWifiDirect.screenVisible(false) }
    }
    LaunchedEffect(Unit) { while (true) { delay(2_000); tick++ } }

    val view = remember(tick, state, manual, tv, session, credential) { WdManualView.of(AutoWifiDirect.manualFacts(ctx, tv, session, credential)) }
    if (view == WdManualView.View.Hidden || tv == null) return

    fun go(lanConfirmed: Boolean) = scope.launch {
        val r = AutoWifiDirect.startNow(ctx, tv, credential.orEmpty(), session, userAsked = true, lanConfirmed = lanConfirmed)
        if (r == WdManualView.Start.ConfirmLan) confirmLan = true
    }
    fun open(i: Intent) { runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (view) {
                WdManualView.View.Hidden -> {}
                is WdManualView.View.Disabled -> {
                    Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Icon(Icons.Filled.WifiTethering, null); Spacer(Modifier.width(8.dp)); Text(WdManualView.TITLE)
                    }
                    Text(WdManualView.HINT, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    Text(view.text, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFE06A00))
                    view.actionLabel?.let { label -> TextButton(onClick = {
                        when (view.action) {
                            WdManualView.Action.PAIR_BLUETOOTH -> onPair()
                            WdManualView.Action.ENTER_CODE -> onEnterCode()
                            WdManualView.Action.RETRY_LINK -> TvLinkManager.retryNow()
                            WdManualView.Action.GRANT_PERMISSION -> open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
                            WdManualView.Action.OPEN_WIFI -> open(Intent(if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_WIFI else Settings.ACTION_WIFI_SETTINGS))
                            WdManualView.Action.NONE -> {}
                        }
                    }, modifier = Modifier.heightIn(min = 48.dp)) { Text(label) } }
                }
                is WdManualView.View.Ready -> {
                    Button(onClick = { go(lanConfirmed = false) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Icon(Icons.Filled.WifiTethering, null); Spacer(Modifier.width(8.dp)); Text(WdManualView.TITLE)
                    }
                    Text(WdManualView.HINT, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    view.last?.let { StateLineText(it) }
                }
                is WdManualView.View.Working -> {
                    StateLineText(view.line)
                    OutlinedButton(onClick = { AutoWifiDirect.stopManual() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Annuler") }
                }
                is WdManualView.View.Active -> {
                    FilledTonalButton(onClick = { AutoWifiDirect.stopManual() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Icon(Icons.Filled.WifiTethering, null); Spacer(Modifier.width(8.dp)); Text(WdManualView.ACTIVE_TITLE)
                    }
                    StateLineText(view.line)
                    view.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant) }
                }
            }
            if (manual || report != null) {
                if (measuring) Text("Mesure en cours (environ 10 secondes)…", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                report?.let { r ->
                    Text(r, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    OutlinedButton(onClick = {
                        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("CastBridge", "CastBridge · relevé Wi-Fi Direct\n$r"))
                        Toast.makeText(ctx, "Rapport copié", Toast.LENGTH_SHORT).show()
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Icon(Icons.Filled.ContentCopy, null); Spacer(Modifier.width(8.dp)); Text("Copier le rapport")
                    }
                }
            }
        }
    }

    if (confirmLan) AlertDialog(
        onDismissRequest = { confirmLan = false },
        title = { Text(WdManualView.TITLE) },
        text = { Text(WdManualView.LAN_CONFIRM) },
        confirmButton = { TextButton(onClick = { confirmLan = false; go(lanConfirmed = true) }) { Text("Utiliser Wi-Fi Direct") } },
        dismissButton = { TextButton(onClick = { confirmLan = false }) { Text("Garder le réseau commun") } },
    )
}

/** La ligne d'état honnête : mot + texte, couleur de la signalétique (vert marche, orange dégradé, rouge cause). */
@Composable
private fun StateLineText(l: StateLine) {
    val cs = MaterialTheme.colorScheme
    val c = when (l.level) { SignalLevel.GREEN -> Color(0xFF2E9E5B); SignalLevel.ORANGE -> Color(0xFFE06A00); SignalLevel.RED -> cs.error; SignalLevel.BLACK -> cs.onSurfaceVariant }
    Text(l.level.word + " · " + l.text, style = MaterialTheme.typography.bodyMedium, color = c)
    l.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant) }
}

/**
 * Les mesures sur le groupe Wi-Fi Direct (l'exécutant de [WdDiagnostic.Probe]) : `GET /api/hello` sur l'adresse du groupe, une lecture de 2 Mo (`/stream/`,
 * plage d'un fichier de la mémoire interne, puis d'un fichier de la clé USB s'il y en a un) et `GET /api/net`. Toute mesure impossible (TV d'essai, profil
 * enfant, aucun fichier, erreur) rend null : le relevé écrit « inconnu ». Le code ne sert qu'à s'authentifier ([TvClient]).
 */
class WdFieldProbe(private val base: String, credential: String) : WdDiagnostic.Probe {
    private val client = TvClient(base, credential)
    private val items: List<TvLibItem>? by lazy { runCatching { TvLibraryParser.parse(client.library()) }.getOrNull() }

    override fun hello(): Boolean = runCatching {
        val c = BoundRoute.open(URL("$base/api/hello")) as HttpURLConnection
        c.connectTimeout = 2_000; c.readTimeout = 2_000
        c.responseCode == 200 && c.inputStream.use { it.readBytes() }.decodeToString().contains("castbridge-tv")
    }.getOrDefault(false)

    override fun networkSample() = items?.filter { it.volumeKind != "usb" && it.size >= MIN_FILE }?.maxByOrNull { it.size }?.let { sample(it.name) }
    override fun usbSample() = items?.filter { it.volumeKind == "usb" && it.size >= MIN_FILE }?.maxByOrNull { it.size }?.let { sample(it.name) }

    override fun tvNetLink(): String? = runCatching { Regex("\"link\"\\s*:\\s*\"(\\w+)\"").find(client.raw("GET", "/api/net"))?.groupValues?.get(1) }.getOrNull()

    /** Lit jusqu'à [WdDiagnostic.SAMPLE_BYTES] ; le temps compte à partir du premier bloc reçu (la latence de la requête n'est pas du débit). */
    private fun sample(name: String): WdDiagnostic.Sample? = runCatching {
        client.openRange(name, 0).input.use { ins ->
            val buf = ByteArray(64 * 1024)
            if (ins.read(buf) < 0) return@use null
            val t0 = android.os.SystemClock.elapsedRealtime()
            var total = 0L
            while (total < WdDiagnostic.SAMPLE_BYTES) { val r = ins.read(buf); if (r < 0) break; total += r }
            WdDiagnostic.Sample(total, android.os.SystemClock.elapsedRealtime() - t0)
        }
    }.getOrNull()

    private companion object { const val MIN_FILE = 4L shl 20 }
}
