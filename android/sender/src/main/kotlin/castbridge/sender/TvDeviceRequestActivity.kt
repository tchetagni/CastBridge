package castbridge.sender

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import castbridge.core.trust.DeviceRequestResult
import castbridge.core.trust.TvDeviceRequestReader
import castbridge.core.trust.TvDeviceRequestTexts
import castbridge.core.tv.TvClient
import castbridge.core.tv.TvDeviceRequestApi

/**
 * « Demande d'appareil de la TV » (docs/TV-DEMANDE-APPAREIL.md), consumer-grade and READ-ONLY: the phone reads from the TV only what the owner's tools need to
 * build an activation key (code, k, factors' fingerprints, the installation's public key) and copies or shares it. It is reachable by any phone the TV trusts
 * (the link kept by [TvLinkManager]) or that already holds the TV's code ([PinStore]); otherwise NOTHING is sent and the screen says why. No owner command,
 * no PIN, token or key is ever shown or put in the text.
 */
class TvDeviceRequestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CastTheme { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { DeviceRequestScreen(onClose = ::finish) } } }
    }

    companion object { fun open(ctx: Context) = ctx.startActivity(Intent(ctx, TvDeviceRequestActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** What this phone may present to its TV: (Wi-Fi base, credential), the trusted link first, then a code already kept for the default TV. Null parts = not authorised. */
private fun authorisation(ctx: Context): Pair<String?, String?> {
    val pins = PinStore(ctx)                                  // also initialises [TvLinkManager]
    val ui = TvLinkManager.state.value
    (ui as? LinkUi.Connected)?.session?.let { s -> s.base?.let { return it to s.credential } }
    val tv = (ui as? LinkUi.Status)?.tv ?: TvLinkManager.saved.default()
    val ip = tv?.lastIps?.firstOrNull() ?: return null to null
    val pin = pins.pinOnly(tv.address)
    return if (pin.isNotBlank()) "http://$ip:${tv.port}" to pin else null to null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceRequestScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var result by remember { mutableStateOf<DeviceRequestResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf<String?>(null) }
    val handler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    fun load() {
        if (busy) return
        val (base, cred) = authorisation(ctx)
        if (!TvDeviceRequestReader.authorised(base, cred)) { result = DeviceRequestResult.Refused(TvDeviceRequestTexts.NOT_AUTHORISED); return }   // nothing is sent
        busy = true; copied = null
        Thread {
            val r = TvDeviceRequestReader.read(base, cred) { b, c -> TvClient(b, c).raw("GET", TvDeviceRequestApi.PATH) }
            handler.post { result = r; busy = false }
        }.start()
    }
    LaunchedEffect(Unit) { load() }
    fun copy(label: String, text: String) {
        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, text))
        copied = TvDeviceRequestTexts.COPIED
    }
    Scaffold(containerColor = cs.background, topBar = {
        TopAppBar(title = { Text(TvDeviceRequestTexts.TITLE, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }, navigationIcon = { IconButton(onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Fermer") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.surface))
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(TvDeviceRequestTexts.INTRO, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            if (busy) Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text("Lecture de la demande de la TV…") }
            when (val r = result) {
                is DeviceRequestResult.Shown -> {
                    val req = r.request
                    // the whole request, line breaks kept, whole fingerprints in a monospace font (scrolls sideways rather than cutting)
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp).horizontalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            req.viewLines().forEach { Text(it, style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)) }
                            Text("Texte copié :", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                            Text(req.fullText(), style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp), color = cs.onSurfaceVariant)
                        }
                    }
                    Button(onClick = { copy("Demande d'appareil CastBridge", req.fullText()) }, modifier = Modifier.fillMaxWidth()) { Text(TvDeviceRequestTexts.COPY_FULL) }
                    Text(TvDeviceRequestTexts.EXPLAIN_FULL, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    OutlinedButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, TvDeviceRequestTexts.TITLE).putExtra(Intent.EXTRA_TEXT, req.fullText())
                        ctx.startActivity(Intent.createChooser(send, TvDeviceRequestTexts.SHARE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }, modifier = Modifier.fillMaxWidth()) { Text(TvDeviceRequestTexts.SHARE) }
                    OutlinedButton(onClick = { copy("Demande d'appareil CastBridge (serveur)", req.serverText()) }, modifier = Modifier.fillMaxWidth()) { Text(TvDeviceRequestTexts.COPY_SERVER) }
                    Text(TvDeviceRequestTexts.EXPLAIN_SERVER, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    copied?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = cs.primary) }
                    OutlinedButton(enabled = !busy, onClick = { load() }) { Text("Relire") }
                }
                is DeviceRequestResult.Refused -> {
                    Text(r.message, style = MaterialTheme.typography.bodyMedium, color = cs.error)
                    OutlinedButton(enabled = !busy, onClick = { load() }) { Text("Réessayer") }
                }
                null -> {}
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
