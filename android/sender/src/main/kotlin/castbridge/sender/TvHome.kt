@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package castbridge.sender

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import castbridge.core.tv.LibraryLogic
import castbridge.core.tv.LibrarySections
import castbridge.core.tv.Pin
import castbridge.core.trust.TvAuth
import castbridge.core.tv.TvClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** "CastBridge TV" tab: the task-oriented home by default; the former screen (channels, manual IP, SSH, storage, APK) under "Avancé". */
@Composable
fun TvHub() {
    var advanced by rememberSaveable { mutableStateOf(false) }
    var bluetooth by rememberSaveable { mutableStateOf(false) }      // « Passerelle Bluetooth » > choose the TV: Avancé opens on its Bluetooth part
    if (advanced) Column(Modifier.fillMaxSize()) {
        TextButton(onClick = { advanced = false; bluetooth = false }, Modifier.padding(start = 8.dp)) { Icon(Icons.Filled.ArrowBack, null); Spacer(Modifier.width(6.dp)); Text("Accueil") }
        TvHubAdvanced(if (bluetooth) Channel.BLUETOOTH else Channel.WIFI)
    } else TvHome(onAdvanced = { advanced = true }, onBluetooth = { bluetooth = true; advanced = true })
}

/** The TV the home talks to (chosen once in the first-connection assistant). */
internal class HomeTv(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("castbridge_home", Context.MODE_PRIVATE)
    var name: String? get() = sp.getString("tv", null); set(v) { PinIo.save(sp) { it.putString("tv", v) } }
    /** Last address the home TV answered at with its name (R-10: tells « X (2) » of the same TV from the other TV of the same model). */
    var host: String? get() = sp.getString("host", null); set(v) { PinIo.save(sp) { it.putString("host", v) } }
    /** The Wi-Fi name the TV last said (`wifi` of /api/info): lets the home say « pas sur le Wi-Fi de la TV » while the TV cannot be reached. */
    var wifi: String? get() = sp.getString("wifi", null); set(v) { PinIo.save(sp) { if (v == null) it.remove("wifi") else it.putString("wifi", v) } }
}

/** The one discreet line under the TV state (castbridge.core.ux.NetworkNames): names, verdict, and the link that asks for the permission / opens the settings. */
@Composable
internal fun WifiLine(tvWifi: String?, tvHost: String?) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val sp = remember { ctx.getSharedPreferences("castbridge_home", Context.MODE_PRIVATE) }
    var tick by remember { mutableIntStateOf(0) }                       // bumped after the system answer / when coming back from the settings
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val o = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) tick++ }
        lifecycle.addObserver(o); onDispose { lifecycle.removeObserver(o) }
    }
    var explain by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }
    val state by produceState<Triple<Pair<String, Int>?, String?, Int>>(Triple(null, null, -1), tick) {
        while (true) {
            value = withContext(Dispatchers.IO) { Triple(phoneWifiAddress(ctx), phoneWifiName(ctx), tick) }
            delay(4000)
        }
    }
    val granted = ctx.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
    val asked = sp.getBoolean("loc_asked", false)
    var a: android.content.Context = ctx; while (a is android.content.ContextWrapper && a !is android.app.Activity) a = a.baseContext
    val rationale = (a as? android.app.Activity)?.shouldShowRequestPermissionRationale(android.Manifest.permission.ACCESS_FINE_LOCATION) == true
    val access = when {
        granted -> castbridge.core.ux.NetworkNames.LocationAccess.GRANTED
        asked && !rationale -> castbridge.core.ux.NetworkNames.LocationAccess.BLOCKED       // refused for good: only the app settings can give it
        else -> castbridge.core.ux.NetworkNames.LocationAccess.CAN_ASK
    }
    val lan = state.first
    val line = castbridge.core.ux.NetworkNames.phoneLine(tvWifi, castbridge.core.ux.NetworkNames.sameSubnet(lan?.first, lan?.second ?: 0, tvHost),
        access, state.second.takeIf { granted }, onWifi = lan != null) ?: return
    fun start(i: Intent) { runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    line.text?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (line.warn) Cb.warning else cs.onSurfaceVariant) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        line.linkText?.let { t ->
            TextButton(onClick = {
                when (line.action) {
                    castbridge.core.ux.NetworkNames.NetAction.ASK_PERMISSION -> explain = true
                    castbridge.core.ux.NetworkNames.NetAction.OPEN_APP_SETTINGS -> start(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
                    castbridge.core.ux.NetworkNames.NetAction.OPEN_LOCATION_SETTINGS -> start(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    null -> {}
                }
            }, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)) { Text(t, style = MaterialTheme.typography.bodySmall) }
        }
        if (line.changeWifi) TextButton(onClick = { start(changeWifiIntent()) }) { Text("Changer de Wi-Fi", style = MaterialTheme.typography.bodySmall) }
    }
    if (explain) AlertDialog(onDismissRequest = { explain = false }, title = { Text("Voir le Wi-Fi de ce téléphone") },
        text = { Text(castbridge.core.ux.NetworkNames.WHY_LOCATION) },
        confirmButton = { TextButton(onClick = {
            explain = false; sp.edit().putBoolean("loc_asked", true).apply()
            ask.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION))
        }) { Text("Continuer") } },
        dismissButton = { TextButton(onClick = { explain = false }) { Text("Pas maintenant") } })
}

/** Wi-Fi choice: the system panel on Android 10+, else the Wi-Fi settings. */
internal fun changeWifiIntent(): Intent =
    if (android.os.Build.VERSION.SDK_INT >= 29) Intent(android.provider.Settings.Panel.ACTION_WIFI) else Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)

/** The name of the Wi-Fi this phone is on, null without the location permission / with the location off (« <unknown ssid> »). */
@Suppress("DEPRECATION")
internal fun phoneWifiName(ctx: Context): String? = runCatching {
    castbridge.core.ux.NetworkNames.cleanSsid(ctx.applicationContext.getSystemService(android.net.wifi.WifiManager::class.java)?.connectionInfo?.ssid)
}.getOrNull()

/** This phone's IPv4 address and prefix on its active Wi-Fi network (LinkProperties: no permission, no SSID), null = not on Wi-Fi. */
internal fun phoneWifiAddress(ctx: Context): Pair<String, Int>? = runCatching {
    val cm = ctx.getSystemService(android.net.ConnectivityManager::class.java)
    cm.allNetworks.firstNotNullOfOrNull { n ->
        if (cm.getNetworkCapabilities(n)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) != true) null
        else cm.getLinkProperties(n)?.linkAddresses?.firstOrNull { it.address is java.net.Inet4Address && !it.address.isLoopbackAddress }
            ?.let { it.address.hostAddress!! to it.prefixLength }
    }
}.getOrNull()

/**
 * « Ouvrir avec CastBridge » asks the main screen to open on the CastBridge TV tab, on « Ajouter ma TV » or on the code entry
 * (the existing assistant « Trouvons votre TV »). One-shot: consumed by [MainActivity] (tab) and [TvHome] (screen).
 */
object TvHomeRequest {
    const val EXTRA = "castbridge.open"
    const val TV = "tv"; const val ADD_TV = "add_tv"; const val ENTER_PIN = "enter_pin"
    val pending = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
}

private fun eta(left: Long, bps: Long): String {
    if (bps <= 0 || left <= 0) return ""
    val s = left / bps
    return when { s >= 3600 -> "encore ${s / 3600} h ${s / 60 % 60} min"; s >= 60 -> "encore ${s / 60} min"; else -> "encore quelques secondes" }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvHome(onAdvanced: () -> Unit, onBluetooth: () -> Unit = onAdvanced) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val home = remember { HomeTv(ctx) }
    val pins = remember { PinStore(ctx) }
    val discovery = remember { TvDiscovery(ctx) }
    DisposableEffect(Unit) { discovery.start(); onDispose { discovery.stop() } }
    val tvs by discovery.tvs.collectAsState()
    var tvName by remember { mutableStateOf(home.name) }
    var pin by remember(tvName) { mutableStateOf(pins.get(tvName)) }
    // Plug and play: the TV this phone is trusted by connects by itself (Bluetooth HELLO, then Wi-Fi with this phone's token).
    val link by TvLinkManager.state.collectAsState()
    val session = (link as? LinkUi.Connected)?.session
    var adding by rememberSaveable { mutableStateOf(false) }
    var managing by remember { mutableStateOf(false) }
    // the one message line of the home (castbridge.core.ux.HomeNotices): near the top, red only for a problem, dismissible; declared before the early returns so the wizard can show it
    var msg by remember { mutableStateOf<castbridge.core.ux.UiNotice?>(null) }
    LaunchedEffect(Unit) { TvLinkManager.start(ctx) }
    var wizard by rememberSaveable { mutableStateOf(TvLinkManager.saved.list().isEmpty() && (tvName == null || !TvAuth.isUsable(pins.get(tvName)))) }
    // R-10: the remembered TV at its remembered address (also renamed « (2) »); an exact name elsewhere may be the other TV of the same model: the user chooses
    val seen = tvs.map { castbridge.core.trust.HomeTvMatch.Seen(it.name, it.host, it.port) }
    val match = castbridge.core.trust.HomeTvMatch.pick(tvName, home.host, seen)
    val ambiguous = session == null && castbridge.core.trust.HomeTvMatch.ambiguous(tvName, home.host, seen)
    val tv = match?.let { m -> tvs.firstOrNull { it.name == m.tv.name && it.host == m.tv.host } }
    // TV not on the Wi-Fi: the switch to Bluetooth (castbridge.core.ux.BtFallback) starts the API gateway and gives its loopback as the TV's address
    val fallback by rememberBtFallback(tvs.filter { tvName == null || it.name == tvName }.map { it.base }, session?.base?.let { "127.0.0.1" !in it } == true)
    val client = session?.base?.let { TvClient(it, session.credential) }
        ?: TvClient(castbridge.core.ux.BtFallback.LOOPBACK_BASE, pin).takeIf { fallback.viaBluetooth && tvName != null && TvAuth.isUsable(pin) && TvLinkManager.saved.list().isEmpty() }
        ?: tv?.takeIf { TvAuth.isUsable(pin) || TvLinkManager.saved.list().isEmpty() }?.let { TvClient(it.base, pin) }
    // sent by « Ouvrir avec CastBridge »: « Ajouter ma TV » or the code entry of the assistant
    val request by TvHomeRequest.pending.collectAsState()
    LaunchedEffect(request) {
        when (request) {
            TvHomeRequest.ADD_TV -> { adding = true; TvHomeRequest.pending.value = null }
            TvHomeRequest.ENTER_PIN -> { wizard = true; TvHomeRequest.pending.value = null }
            TvHomeRequest.TV -> TvHomeRequest.pending.value = null
        }
    }

    if (adding) {
        AddTvFlow(onClose = { adding = false }, onAdded = { t ->
            val n = t.mdns ?: t.name
            home.name = n; tvName = n; RemotePrefs(ctx).tvName = n; adding = false; wizard = false
        })
        return
    }
    if (wizard) {
        FirstConnection(tvs, onRetry = { discovery.restart() }, onAdvanced = onAdvanced, onAddTv = { adding = true }, notice = msg?.takeIf { it.error }?.text,
            gateway = { BtGatewayHomeBlock(fallback.signal, tvReachable = false, onAddTv = { adding = true }, onChoose = onBluetooth, showAddTv = false) },
            kept = { t -> pins.pinOnly(t.name) }) { t, code ->
            home.name = t.name; home.host = t.host.takeUnless { castbridge.core.trust.PinBook.isLoopback(it) }; pins.put(t.name, code); tvName = t.name; pin = code; wizard = false; msg = null
        }
        return
    }
    if (managing) ManageTvsDialog(onDismiss = { managing = false }, onAdd = { adding = true })

    val upload by UploadService.state.collectAsState()
    val avg by UploadService.average.collectAsState()
    var info by remember { mutableStateOf<castbridge.core.tv.TvInfo?>(null) }
    var items by remember { mutableStateOf<List<TvLibItem>>(emptyList()) }
    var reachable by remember { mutableStateOf(false) }
    var showLibrary by remember { mutableStateOf(false) }
    var showExchange by remember { mutableStateOf(false) }
    var showPlayer by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    LaunchedEffect(client?.base, client?.pin) {
        reachable = false
        info = null
        var linked = false
        while (client != null) {
            val seenAs = session?.tv?.let { it.mdns ?: it.name } ?: tvName
            val r = withContext(Dispatchers.IO) { runCatching { castbridge.core.tv.TvInfo.parse(client.info()) }
                // R-19: where this screen shows « Connectée » is where the upload looks too (the screen and the upload say the same thing)
                .also { if (it.isSuccess && seenAs != null) UploadService.tvSeenAt(ctx, seenAs, client.base) } }
            reachable = r.isSuccess; info = r.getOrNull() ?: info
            r.getOrNull()?.let { if (home.wifi != it.wifi) home.wifi = it.wifi }          // an older TV (or a cable) says nothing: the line then stays silent
            if (r.isSuccess && msg == castbridge.core.ux.HomeNotices.info("Reconnexion à la TV…")) msg = null      // reconnected: the line goes away by itself
            // R-10: the code was accepted at this address: every screen key of this TV (its address, a « (2) » name) finds the same code next time
            if (r.isSuccess && !linked && session == null && tv != null && tvName != null && !TvAuth.isToken(client.pin) && !castbridge.core.trust.PinBook.isLoopback(tv.host)) {
                linked = true
                pins.link(tvName!!, "${tv.host}:${tv.port}"); if (home.host != tv.host) home.host = tv.host
            }
            val err = r.exceptionOrNull() as? TvClient.HttpError
            if (err?.code == 401) {
                if (TvAuth.isToken(client.pin)) { TvLinkManager.poke(); msg = castbridge.core.ux.HomeNotices.info("Reconnexion à la TV…"); delay(3000); continue }   // token expired or revoked: HELLO again
                // R-10: « too many tries » is not « the code changed »: wait for the TV, never re-ask (the right code is refused while locked)
                val reply = castbridge.core.trust.TvAuthReply.of(err.code, err.message)
                if (reply is castbridge.core.trust.TvAuthReply.Kind.Locked) {
                    tvName?.let { pins.locked(it, reply.retryAfterSec) }
                    msg = castbridge.core.ux.HomeNotices.info(castbridge.core.trust.CredentialDecision.locked(reply.retryAfterSec)); delay(reply.retryAfterSec.coerceIn(5, 120) * 1000); continue
                }
                // the TV refused this code: kept but never sent again by itself, only when name AND address agree (a name-only match may be another TV)
                if (castbridge.core.trust.HomeTvMatch.refuseOn401(match)) tvName?.let { k -> client.pin?.let { pins.refused(k, it) } }
                msg = castbridge.core.ux.HomeNotices.error(castbridge.core.trust.CredentialDecision.PIN_CHANGED); wizard = true; break
            }
            delay(2000)
        }
    }
    // A slow USB scan must never hold up the connection indicator. Discard data from the previous TV.
    LaunchedEffect(client?.base, client?.pin) {
        items = emptyList()
        while (client != null) {
            val fresh = withContext(Dispatchers.IO) { runCatching { TvLibraryParser.parse(client.library()) }.getOrNull() }
            if (fresh != null) items = fresh
            delay(10_000)
        }
    }
    var moveNext by rememberSaveable { mutableStateOf(false) }       // the next picked file is moved (deleted from the phone once on the TV)
    var pendingFiles by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var playAfterCopy by rememberSaveable { mutableStateOf(false) }
    var extraTools by rememberSaveable { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        playAfterCopy = false
        pendingFiles = ArrayList(uris.map { it.toString() })
    }
    fun enqueueSelected() {
        val uris = pendingFiles.map(Uri::parse)
        if (uris.isEmpty()) return
        // the link dropped while the picker was open: say it (the files used to be dropped without a word)
        if (!castbridge.core.ux.HomeNotices.canPick(session != null, tvName)) { msg = castbridge.core.ux.HomeNotices.pickBlocked(TvLinkManager.saved.list().size); return }
        pendingFiles = arrayListOf()
        val move = moveNext; moveNext = false
        var queued = 0
        var refused: String? = null
        uris.forEach { uri ->
            // Keep write access when the provider gives it: a move deletes the original once the TV holds it.
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
                .onFailure { runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
            val (name, size) = OpenWithActivity.describe(ctx, uri)
            // Little room on the TV: play while the file arrives instead of storing it all first (one file only).
            val progressive = uris.size == 1 && (info?.let { size > 0 && it.free < size * 2 } ?: false)
            // R-09: the code path goes through the queue as well (a second file used to be dropped, several files refused)
            var last: TransferQueue.Ticket? = null
            if (session != null) {
                runCatching { last = TransferQueue.add(ctx, uri, name, size, move, autoPlay = playAfterCopy && uris.size == 1, progressive = progressive && playAfterCopy); queued++ }
                    .onFailure { refused = "Impossible de mettre l'envoi en file : ${it.message}" }
            } else {
                runCatching { last = TransferQueue.add(ctx, uri, name, size, move, autoPlay = playAfterCopy && uris.size == 1, progressive = progressive && playAfterCopy, tvName = tvName!!, credential = pin); queued++ }
                    .onFailure { refused = "Impossible de mettre l'envoi en file : ${it.message}" }
            }
            if (uris.size == 1) last?.takeIf { it.queued }?.let { msg = castbridge.core.ux.HomeNotices.info(it.text) }
        }
        if (queued > 1) msg = castbridge.core.ux.HomeNotices.queued(queued, refused)
        else if (queued == 0) refused?.let { msg = castbridge.core.ux.HomeNotices.error(it) }
        else if (queued == 1 && refused != null) msg = castbridge.core.ux.HomeNotices.queued(1, refused)
        else if (queued == 1 && session != null && session.base == null) msg = castbridge.core.ux.HomeNotices.info("Envoi par Bluetooth (plus lent que le Wi-Fi)")
    }
    fun cmd(f: TvClient.() -> Unit) = scope.launch {
        val c = client ?: run { msg = castbridge.core.ux.HomeNotices.needsTv("Commande de lecture"); return@launch }
        val e = withContext(Dispatchers.IO) { runCatching { c.f() }.exceptionOrNull() }
        msg = ((e as? TvClient.HttpError)?.message?.substringAfter(": ")?.let { TvClient.str(it, "message") ?: TvClient.str(it, "error") } ?: e?.message)
            ?.let { castbridge.core.ux.HomeNotices.error(it) }
    }

    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // « Ouvrir sur la TV » (OpenTv.kt) : CastBridge-TV passe devant l'application qui est à l'écran de la TV ; déjà devant, le bouton ouvre la télécommande
        Text("Ma TV", style = MaterialTheme.typography.headlineLarge)
        Text("Vos fichiers et votre télécommande, au même endroit.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        // The connection has its own stable surface; technical maintenance stays below the primary actions.
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (TvLinkManager.saved.list().isNotEmpty()) {
            TvLinkStatus(link, onAdd = { adding = true }, onManage = { managing = true })
            if (reachable) info?.let { Text("${formatSize(it.free)} libres sur la TV", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant) }
        } else Row(verticalAlignment = Alignment.CenterVertically) {
            val sig = fallback.signal      // no misleading grey: orange Bluetooth only, red unreachable (castbridge.core.ux.BtFallback)
            Box(Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(when {
                reachable && fallback.viaBluetooth -> Cb.warning; reachable -> Cb.success
                sig?.light == castbridge.core.ux.LinkLight.RED -> cs.error; sig?.light == castbridge.core.ux.LinkLight.ORANGE -> Cb.warning; else -> cs.outline }))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(tvName?.removePrefix("CastBridge TV ")?.ifBlank { "Ma TV" } ?: "Ma TV", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(if (reachable) "Connectée" + (if (fallback.viaBluetooth) " par Bluetooth" else "") + (info?.let { " · ${formatSize(it.free)} libres" } ?: "")
                    else if (ambiguous) "Votre TV n'est plus à son adresse, ou une autre TV du même modèle est là : touchez « Changer » pour la choisir (son code est gardé)."
                    else sig?.title ?: "Recherche de la TV…",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            TextButton(onClick = { wizard = true }) { Text("Changer") }
        }

        // Which Wi-Fi: the TV says its network name; the phone's own name needs the location permission (asked only by a tap here); « the same » = names when both known, else the subnet
        WifiLine(info?.wifi ?: home.wifi, home.host ?: tv?.host)
        }
        }

        // Wi-Fi absent: the honest state (orange Bluetooth / red neither) and the « Passerelle Bluetooth » button, right under the TV
        if (extraTools && (!reachable || fallback.signal != null)) BtGatewayHomeBlock(fallback.signal, reachable, onAddTv = { adding = true }, onChoose = onBluetooth)

        // The message line: right under the TV, where it is seen (it used to sit below the posters, out of sight)
        msg?.let { n ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(n.text, Modifier.weight(1f), color = if (n.error) cs.error else cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                IconButton({ msg = null }) { Icon(Icons.Filled.Close, "Fermer le message") }
            }
        }

        fun pickFor(move: Boolean) {
            if (!castbridge.core.ux.HomeNotices.canPick(session != null, tvName)) { msg = castbridge.core.ux.HomeNotices.pickBlocked(TvLinkManager.saved.list().size); return }
            moveNext = move; pick.launch(arrayOf("video/*", "audio/*"))
        }
        Button(onClick = { PhoneConnect.feature("send"); pickFor(move = false) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp)) {
            Icon(cbv(R.drawable.ic_cb_envoyer), null)
            Spacer(Modifier.width(12.dp))
            Text("Envoyer des fichiers", style = MaterialTheme.typography.titleMedium)
        }
        TaskPair(
            first = { m -> Task(cbv(R.drawable.ic_cb_telecommande), "Télécommande", "Piloter la TV", m) { PhoneConnect.feature("remote"); RemoteActivity.open(ctx) } },
            second = { m -> Task(cbv(R.drawable.ic_cb_bibliotheque), "Bibliothèque TV", if (reachable) "Vos vidéos et fichiers" else "En attente de connexion", m) {
                PhoneConnect.feature("tv_library")
                if (client == null) msg = castbridge.core.ux.HomeNotices.needsTv("Bibliothèque de la TV") else showLibrary = true
            } },
        )

        // Big progress while sending
        val u = upload
        val bt by BtUploadService.state.collectAsState()
        val btRoute by BtUploadService.route.collectAsState()
        val b = bt
        val btActive = u !is UploadService.State.Uploading && u !is UploadService.State.Waiting &&
            (b is castbridge.core.tv.ResumableUpload.State.Uploading || b is castbridge.core.tv.ResumableUpload.State.Waiting)
        // Bluetooth has no speed meter of its own: measured from the progress since the transfer started (shown after 3 s).
        val btSent = (b as? castbridge.core.tv.ResumableUpload.State.Uploading)?.sent ?: (b as? castbridge.core.tv.ResumableUpload.State.Waiting)?.sent
        var btFrom by remember { mutableStateOf(0L to 0L) }
        LaunchedEffect(btActive) { if (btActive) btFrom = System.currentTimeMillis() to (btSent ?: 0L) }
        val btAvg = if (btActive && btSent != null) (System.currentTimeMillis() - btFrom.first).let { dt ->
            if (dt > 3_000 && btSent > btFrom.second) (btSent - btFrom.second) * 1000 / dt else 0L } else 0L
        val shownAvg = if (btActive) btAvg else avg
        val sendCardShown = u is UploadService.State.Uploading || u is UploadService.State.Waiting || btActive
        AnimatedVisibility(sendCardShown, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            val (sent, total, name, waiting) = when {
                u is UploadService.State.Uploading -> listOf(u.sent, u.total, u.job.fileName, null)
                u is UploadService.State.Waiting -> listOf(u.sent, u.total, u.job.fileName, u.reason)
                // sent by Bluetooth (no common Wi-Fi): same card, same percentage and time left
                b is castbridge.core.tv.ResumableUpload.State.Uploading -> listOf(b.sent, b.total, "Envoi par ${btRoute ?: "Bluetooth"}", null)
                b is castbridge.core.tv.ResumableUpload.State.Waiting -> listOf(b.sent, b.total, "Envoi par ${btRoute ?: "Bluetooth"}", b.reason)
                else -> listOf(0L, 1L, "", null)
            }
            sent as Long; total as Long
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NotificationHintRow()           // R-52 : Android jette les notifications de CastBridge ⇒ le dire ici, avec « Autoriser » (rien quand tout va bien)
                    Text(if (waiting != null) "Copie en attente" else "Copie en cours", style = MaterialTheme.typography.labelLarge, color = if (waiting != null) Cb.warning else Cb.info)
                    Text(name as String, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${(sent.toDouble() * 100 / total.coerceAtLeast(1)).toInt().coerceIn(0, 100)} %", fontSize = 44.sp, fontWeight = FontWeight.Bold)
                    LinearProgressIndicator({ (sent.toFloat() / total.coerceAtLeast(1)).coerceIn(0f, 1f) }, Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)), color = if (waiting != null) Cb.warning else Cb.info)
                    Text("${formatSize(sent)} sur ${formatSize(total)}", style = MaterialTheme.typography.bodyMedium)
                    Text(if (waiting != null) "$waiting\nLa copie attend avant de poursuivre. Vous pouvez consulter les détails dans la file ci-dessous." else listOf(eta(total - sent, shownAvg), if (shownAvg > 0) "${formatSize(shownAvg)}/s" else "").filter { it.isNotEmpty() }.joinToString("  ·  "),
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    var confirmCancel by remember { mutableStateOf(false) }
                    TextButton(onClick = { confirmCancel = true }) { Text("Annuler la copie", color = cs.error) }
                    if (confirmCancel) AlertDialog(onDismissRequest = { confirmCancel = false },
                        title = { Text("Annuler cette copie ?") }, text = { Text("Les fichiers d’origine restent sur le téléphone. Les autres fichiers de la file ne sont pas annulés.") },
                        confirmButton = { TextButton(onClick = {
                            confirmCancel = false
                            val active = TransferQueue.items.value.firstOrNull { it.status == castbridge.core.tv.QueueStatus.RUNNING }
                            if (active != null) TransferQueue.cancel(ctx, active.id)
                            else if (btActive) BtUploadService.cancel(ctx) else UploadService.cancel(ctx)
                        }) { Text("Annuler la copie") } },
                        dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("Continuer") } })
                }
            }
        }
        (u as? UploadService.State.Done)?.let { Text("« ${LibraryLogic.title(it.job.fileName)} » est sur la TV ✓", style = MaterialTheme.typography.bodyMedium, color = Cb.success) }
        (u as? UploadService.State.Failed)?.let { Text(it.reason, style = MaterialTheme.typography.bodyMedium, color = cs.error) }

        // Queue of the files waiting to be sent (several files, « Ouvrir avec », « Copier et lire », …): one at a time, cancel one by one, retry a failure.
        TransferQueueCard(showNotificationHint = !sendCardShown)      // R-52 : le même texte une seule fois à l'écran (la carte d'envoi le porte quand elle est là)
        TextButton(onClick = { RecentCopiesActivity.open(ctx) }) { Text("Dernières copies") }

        val now = info?.takeIf { it.playing != null && it.state != "idle" }
        // Now playing
        AnimatedVisibility(now != null) {
            now?.let { n ->
                Card(Modifier.fillMaxWidth().clickable { showPlayer = true }, colors = CardDefaults.cardColors(containerColor = cs.primaryContainer)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Tv, null, tint = cs.primary)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text("Sur la TV", style = MaterialTheme.typography.labelMedium, color = cs.primary)
                            Text(LibraryLogic.title(n.playing!!), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                            if (n.dur > 0) LinearProgressIndicator({ n.pos.toFloat() / n.dur }, Modifier.fillMaxWidth().padding(top = 6.dp))
                        }
                        IconButton({ RemoteActivity.open(ctx) }) { Icon(cbv(R.drawable.ic_cb_telecommande), "Télécommande") }
                        IconButton({ cmd { if (n.state == "playing") pause() else resume() } }) { Icon(if (n.state == "playing") cbv(R.drawable.ic_cb_pause) else cbv(R.drawable.ic_cb_lecture), "Lecture / pause") }
                    }
                }
            }
        }

        // Continue watching
        val resume = LibrarySections.build(items).firstOrNull { it.id == LibrarySections.RESUME }?.items.orEmpty()
            .ifEmpty { LibraryLogic.sortNewestFirst(items.filter { it.type != castbridge.core.tv.MediaType.OTHER }, { it.mtime }, { it.name }).take(8) }
        if (resume.isNotEmpty() && client != null) {
            Text(if (resume.any { it.resumeMs > 0 && !it.watched }) "Reprendre sur la TV" else "Récemment ajoutés", style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(resume, key = { "${it.volume}:${it.name}" }) { i -> Poster(client, i) { cmd { play(i.name, if (i.watched) 0 else i.resumeMs) } } }
            }
        }

        TextButton(onClick = { extraTools = !extraTools }) {
            Text(if (extraTools) "Masquer les autres actions" else "Autres actions et connexion")
            Icon(if (extraTools) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
        }
        if (extraTools) {
        OpenTvButton(onAlready = { RemoteActivity.open(ctx) })
        TaskPair(
            first = { m -> Task(cbv(R.drawable.ic_cb_deplacer_vers_tv), castbridge.core.ux.SendWay.MOVE.label, castbridge.core.ux.SendWays.HOME_MOVE_HINT, m) { PhoneConnect.feature("move"); pickFor(move = true) } },
            second = { m -> Task(Icons.Filled.SwapVert, "Échanger des fichiers", "Téléphone et TV", m) { if (client == null) msg = castbridge.core.ux.HomeNotices.needsTv("Échanger des fichiers") else showExchange = true } },
        )
        // Series in « Titre / Saison » folders (virtual: nothing moves), automatic after each send, or on demand with a confirmation and an undo
        var autoClass by remember { mutableStateOf(SeriesClassifying.auto(ctx)) }
        var confirmPlan by remember { mutableStateOf<List<castbridge.core.library.agent.SeriesClassifier.Move>?>(null) }
        var undoable by remember { mutableStateOf(SeriesClassifying.last.isNotEmpty()) }
        val toClassify = remember(items) { SeriesClassifying.preview(items.map { it.name to it.folder }) }
        if (client != null && items.isNotEmpty()) ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Classer les séries par titre et saison", style = MaterialTheme.typography.titleSmall)
                        Text("Ex. Prison Break › Saison 01. Aucun fichier n'est déplacé ni copié.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                    Switch(autoClass, { autoClass = it; SeriesClassifying.setAuto(ctx, it) })
                }
                Text(if (autoClass) "Automatique à la fin de chaque envoi." else "Automatique : désactivé.", style = MaterialTheme.typography.bodySmall)
                if (toClassify.isNotEmpty()) {
                    val folders = toClassify.map { it.folder }.toSet()
                    Text("${toClassify.size} épisode(s) en vrac à ranger dans ${folders.size} dossier(s).", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { confirmPlan = toClassify }) { Text("Classer maintenant") }
                }
                if (undoable) TextButton(onClick = {
                    scope.launch {
                        val n = withContext(Dispatchers.IO) { SeriesClassifying.undoLast(client) }
                        undoable = false; msg = castbridge.core.ux.HomeNotices.info("$n fichier(s) remis à la racine.")
                        withContext(Dispatchers.IO) { runCatching { items = TvLibraryParser.parse(client.library()) } }
                    }
                }) { Text("Annuler le dernier classement") }
            }
        }
        confirmPlan?.let { plan ->
            AlertDialog(
                onDismissRequest = { confirmPlan = null },
                title = { Text("Classer ${plan.size} épisode(s) ?") },
                text = { Column {
                    plan.groupBy { it.folder }.entries.take(8).forEach { (f, l) -> Text("• $f : ${l.size}", style = MaterialTheme.typography.bodyMedium) }
                    Text("Seuls les fichiers à la racine sont classés ; vos dossiers existants ne sont pas touchés. Annulable.", style = MaterialTheme.typography.bodySmall)
                } },
                confirmButton = { TextButton(onClick = {
                    confirmPlan = null
                    scope.launch {
                        val (ok, ko) = withContext(Dispatchers.IO) { SeriesClassifying.apply(client!!, plan) }
                        undoable = ok > 0
                        msg = if (ko == 0) castbridge.core.ux.HomeNotices.info("$ok épisode(s) classé(s).") else castbridge.core.ux.HomeNotices.error("$ok classé(s), $ko échec(s) : réessayez.")
                        withContext(Dispatchers.IO) { runCatching { items = TvLibraryParser.parse(client!!.library()) } }
                    }
                }) { Text("Classer") } },
                dismissButton = { TextButton(onClick = { confirmPlan = null }) { Text("Annuler") } },
            )
        }

        // R-14 (owner 2026-10-03): the explicit « Wi-Fi Direct » button, with its cause when it cannot start (castbridge.core.link.WdManualView)
        WdManualCard(tv = session?.tv ?: TvLinkManager.savedFor(tvName), session = session, credential = session?.credential ?: pin,
            onPair = { adding = true }, onEnterCode = { wizard = true })

        // TV reachable but bonded in Bluetooth: the gateway stays at hand (for the Mac), lower down
        if (reachable && fallback.signal == null) BtGatewayCard(tvReachable = true, onAddTv = { adding = true }, onChoose = onBluetooth)

        // Advanced
        HorizontalDivider()
        ListItem(modifier = Modifier.clickable(onClick = onAdvanced),
            leadingContent = { Icon(cbv(R.drawable.ic_cb_reglages), null) },
            headlineContent = { Text("Avancé") },
            supportingContent = { Text("Adresse manuelle, Bluetooth, Wi-Fi Direct, passerelle SSH, stockage de la TV, installation d'applications") },
            trailingContent = { Icon(Icons.Filled.ChevronRight, null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent))
        }
    }

    if (pendingFiles.isNotEmpty()) AlertDialog(
        onDismissRequest = { pendingFiles = arrayListOf() },
        title = { Text(if (moveNext) "Déplacer vers la TV" else "Envoyer à la TV") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(session?.tv?.name ?: tvName ?: "Votre TV", style = MaterialTheme.typography.titleMedium)
                Text("${pendingFiles.size} fichier(s) sélectionné(s)")
                Text("Destination : selon le réglage de stockage de la TV.")
                if (reachable) info?.let { Text("${formatSize(it.free)} libres actuellement. L’espace sera vérifié au démarrage.", style = MaterialTheme.typography.bodySmall) }
                if (moveNext) Text("Les originaux seront supprimés du téléphone après vérification de la copie sur la TV.", color = cs.error)
                if (pendingFiles.size == 1) {
                    Text("Après la copie", style = MaterialTheme.typography.titleSmall)
                    listOf(false to "Garder dans la bibliothèque", true to "Lire sur la TV").forEach { (play, label) ->
                        Row(Modifier.fillMaxWidth().clickable { playAfterCopy = play }.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = playAfterCopy == play, onClick = { playAfterCopy = play })
                            Text(label, Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { enqueueSelected() }) { Text(if (moveNext) "Déplacer" else "Commencer la copie") } },
        dismissButton = { TextButton(onClick = { pendingFiles = arrayListOf() }) { Text("Retour") } },
    )
    if (showLibrary && client != null) TvLibraryDialog(client, onDismiss = { showLibrary = false },
        onDownload = { i -> DownloadService.start(ctx, client.base, client.pin, i.name, i.size); showLibrary = false; showExchange = true })
    if (showExchange && client != null) TvTransferDialog(client, onDismiss = { showExchange = false })
    val n = info
    val playing = n?.playing
    if (showPlayer && client != null && n != null && playing != null) {
        NowPlayingSheet(LibraryLogic.title(playing), "Sur la TV", n.state == "playing" || n.state == "buffering", n.pos, n.dur,
            onSeek = { ms -> cmd { seek(ms) } }, onToggle = { cmd { if (n.state == "playing") pause() else resume() } },
            onSkip = { d -> cmd { seek((n.pos + d * 1000L).coerceAtLeast(0)) } },
            onStop = { cmd { stop() }; showPlayer = false }, onDismiss = { showPlayer = false }, onSettings = { showSettings = true },
            onRemote = { RemoteActivity.open(ctx) })
    }
    if (showSettings && client != null) TvPlayerSettingsSheet(client) { showSettings = false }
}

@Composable
private fun TaskPair(first: @Composable (Modifier) -> Unit, second: @Composable (Modifier) -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 360.dp || fontScale > 1.2f) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                first(Modifier.fillMaxWidth())
                second(Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                first(Modifier.weight(1f).fillMaxHeight())
                second(Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun Task(icon: ImageVector, title: String, sub: String, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    ElevatedCard(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp).fillMaxWidth()) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surface))),
                contentAlignment = Alignment.Center) { Icon(icon, null, tint = cs.primary, modifier = Modifier.size(26.dp)) }
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun Poster(client: TvClient, i: TvLibItem, onClick: () -> Unit) {
    val k = TvThumbs.key(client.base, i)
    var img by remember(k) { mutableStateOf(TvThumbs.cached(k)) }
    LaunchedEffect(k, i.hasThumb) { if (img == null) img = TvThumbs.load(client, i) }
    Column(Modifier.width(168.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            img?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } ?: Icon(Icons.Filled.Movie, null)
            Icon(Icons.Filled.PlayCircle, null, Modifier.size(40.dp), tint = Color.White.copy(alpha = 0.9f))
            if (i.resumeMs > 0 && !i.watched && i.durationMs > 0)
                LinearProgressIndicator({ LibraryLogic.progress(i.resumeMs, i.durationMs) }, Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomCenter))
        }
        Text(i.title, Modifier.padding(6.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
    }
}

/** First connection: find the TV (same Wi-Fi), then type its code once. */
@Composable
private fun FirstConnection(tvs: List<Tv>, onRetry: () -> Unit, onAdvanced: () -> Unit, onAddTv: () -> Unit, notice: String? = null,
                            gateway: @Composable () -> Unit = {}, kept: (Tv) -> String = { "" }, onDone: (Tv, String) -> Unit) {
    val scope = rememberCoroutineScope()
    var chosen by remember { mutableStateOf<Tv?>(null) }
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    var showHelp by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(Modifier.size(72.dp).clip(RoundedCornerShape(22.dp)).background(cs.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Tv, null, Modifier.size(40.dp), tint = cs.primary)
        }
        notice?.let { Text(it, color = cs.error) }
        val c = chosen
        if (c == null) {
            Text("Votre téléphone.\nVotre TV.", style = MaterialTheme.typography.headlineLarge)
            Text("Reliez-les une fois pour envoyer vos vidéos et piloter la TV.", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("1. Ouvrez CastBridge sur la TV", style = MaterialTheme.typography.titleMedium)
                    Text("2. Choisissez Recevoir", style = MaterialTheme.typography.titleMedium)
                    Text("3. Ajoutez-la ici", style = MaterialTheme.typography.titleMedium)
                }
            }
            Button(onClick = onAddTv, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Filled.Add, null); Spacer(Modifier.width(8.dp)); Text("Ajouter ma TV")
            }
            Text("Association Bluetooth, sans code à saisir.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            HorizontalDivider()
            Text("Ou se connecter en Wi-Fi", style = MaterialTheme.typography.titleMedium)
            Text("Placez les deux appareils sur le même réseau, puis choisissez votre TV.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            if (tvs.isEmpty()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.Search, null, tint = cs.onSurfaceVariant)
                Text("Aucune TV détectée pour le moment.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
            tvs.forEach { t ->
                // R-10: the user chose the TV; a code already kept for that exact name is offered (hidden), confirmed by « Se connecter »
                ElevatedCard(Modifier.fillMaxWidth().clickable { chosen = t; error = null; code = kept(t).takeIf { Pin.isValidFormat(it) }.orEmpty() }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Tv, null, tint = cs.primary); Spacer(Modifier.width(12.dp))
                        Text(t.name.removePrefix("CastBridge TV ").ifBlank { t.name }, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Icon(Icons.Filled.ChevronRight, null)
                    }
                }
            }
            TextButton(onClick = onRetry) { Text("Chercher à nouveau") }
            TextButton(onClick = { showHelp = !showHelp }) { Text("Ma TV n’apparaît pas") }
            if (showHelp) {
                Text("Vérifiez que CastBridge-TV est ouvert. Vous pouvez aussi connecter les appareils sans box Wi-Fi.", style = MaterialTheme.typography.bodyMedium)
                gateway()
                OutlinedButton(onClick = onAdvanced) { Text("Autres moyens de connexion") }
            }
        } else {
            Text("Saisissez le code de la TV", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("Sur la TV, ouvrez Recevoir, puis Code PIN. Vous le trouverez aussi dans Connexion et réglages. " +
                "Vous ne le saisirez qu'une fois.", textAlign = TextAlign.Center, color = cs.onSurfaceVariant)
            OutlinedTextField(code, { v -> code = v.filter { it.isDigit() }.take(Pin.LENGTH); error = null }, singleLine = true,
                textStyle = MaterialTheme.typography.headlineMedium.copy(textAlign = TextAlign.Center, letterSpacing = 8.sp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.width(240.dp), isError = error != null,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
            error?.let { Text(it, color = cs.error) }
            if (error?.startsWith("TV injoignable") == true) gateway()      // « TV injoignable » : the Bluetooth way, right there
            Button(enabled = Pin.isValidFormat(code) && !checking, onClick = {
                checking = true
                scope.launch {
                    val r = withContext(Dispatchers.IO) { runCatching { TvClient(c.base, code).info() } }
                    checking = false
                    r.onSuccess { onDone(c, code) }.onFailure { e ->
                        error = if ((e as? TvClient.HttpError)?.code == 401) (if ("locked" in e.message.orEmpty()) "Trop d'essais : attendez une minute" else "Code incorrect")
                            else "TV injoignable : ${e.message}"
                    }
                }
            }, modifier = Modifier.fillMaxWidth(0.7f)) { if (checking) CircularProgressIndicator(Modifier.size(18.dp)) else Text("Se connecter") }
            TextButton(onClick = { chosen = null }) { Text("Choisir une autre TV") }
        }
    }
}

/**
 * The transfer queue (R-09): each file with its place (« n° 2 »), « Copier et lire » marked, the cause of a failure with « Réessayer »,
 * and why the queue is not moving when it is not (another upload, Android refusing a background start).
 */
@Composable
fun TransferQueueCard(showNotificationHint: Boolean = true) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val queue by TransferQueue.items.collectAsState()
    val note by TransferQueue.note.collectAsState()
    val wdLine by AutoWifiDirect.line.collectAsState()
    // R-12: a file not copied because the TV already holds the same content stays shown with its line and « Copier quand même » (once)
    val shownQueue = queue.filter { it.status == castbridge.core.tv.QueueStatus.WAITING || (showNotificationHint && it.status == castbridge.core.tv.QueueStatus.RUNNING) || it.status == castbridge.core.tv.QueueStatus.FAILED ||
        (it.status == castbridge.core.tv.QueueStatus.DONE && it.note != null) }
    val order = castbridge.core.tv.QueueRules.runOrder(queue)
    AnimatedVisibility(shownQueue.isNotEmpty()) {
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (showNotificationHint) NotificationHintRow()           // R-52 : Android jette les notifications de CastBridge ⇒ le dire, avec « Autoriser »
                val waiting = shownQueue.count { it.status == castbridge.core.tv.QueueStatus.WAITING }
                Text("File d'attente des envois" + if (waiting > 0) " · $waiting en attente" else "", style = MaterialTheme.typography.labelLarge, color = cs.primary)
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant) }
                // R-14: the honest route line (« Par Wi-Fi Direct (automatique) » green, « Bluetooth seulement : lent » orange, red only when nothing works)
                wdLine?.let { l ->
                    val c = when (l.level) { castbridge.core.ux.SignalLevel.GREEN -> androidx.compose.ui.graphics.Color(0xFF2E9E5B)
                        castbridge.core.ux.SignalLevel.ORANGE -> androidx.compose.ui.graphics.Color(0xFFE06A00); castbridge.core.ux.SignalLevel.RED -> cs.error
                        castbridge.core.ux.SignalLevel.BLACK -> cs.onSurfaceVariant }
                    Text(l.level.word + " · " + l.text, style = MaterialTheme.typography.bodySmall, color = c)
                    l.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant) }
                }
                // in the order they will run: the running one, the waiting ones (« Copier et lire » first), then the failures
                val sorted = shownQueue.filter { it.status == castbridge.core.tv.QueueStatus.RUNNING } + order + shownQueue.filter { it.status == castbridge.core.tv.QueueStatus.FAILED } +
                    shownQueue.filter { it.status == castbridge.core.tv.QueueStatus.DONE }
                sorted.forEach { q ->
                    val n = castbridge.core.tv.QueueRules.position(queue, q.id)
                    val kind = " · " + castbridge.core.ux.QueueGlances.kind(q)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text((if (n > 0) "$n. " else "") + LibraryLogic.title(q.name), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                            Text(when (q.status) {
                                castbridge.core.tv.QueueStatus.RUNNING -> "En cours$kind"
                                castbridge.core.tv.QueueStatus.WAITING -> if (q.preparing) castbridge.core.tv.ReshareTexts.preparing(q.prep) else "En attente$kind" + if (q.size > 0) " · ${formatSize(q.size)}" else ""
                                castbridge.core.tv.QueueStatus.DONE -> q.note ?: "Terminé"
                                else -> q.error?.let { if (it.startsWith("Échec") || it.startsWith("Copie ") || q.anchor == castbridge.core.tv.Anchor.RESHARE) it else "Échec : $it" } ?: "Échec : envoi interrompu"
                            }, style = MaterialTheme.typography.bodySmall, color = if (q.status == castbridge.core.tv.QueueStatus.FAILED) cs.error else cs.onSurfaceVariant)
                        }
                        if (q.status == castbridge.core.tv.QueueStatus.FAILED && q.anchor == castbridge.core.tv.Anchor.RESHARE)
                            TextButton(onClick = { ctx.startActivity(android.content.Intent(ctx, ReselectActivity::class.java)) }) { Text(castbridge.core.tv.ReshareTexts.PICK_BUTTON) }
                        else if (q.status == castbridge.core.tv.QueueStatus.FAILED) TextButton(onClick = { TransferQueue.retry(ctx, q.id) }) { Text("Réessayer") }
                        else if (q.status == castbridge.core.tv.QueueStatus.DONE) TextButton(onClick = { TransferQueue.copyAnyway(ctx, q.id) }) { Text(castbridge.core.tv.DedupTexts.COPY_ANYWAY) }
                        else TextButton(onClick = { TransferQueue.cancel(ctx, q.id) }) { Text("Annuler") }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (waiting > 1) TextButton(onClick = { TransferQueue.cancelWaiting(ctx) }) { Text("Annuler les envois en attente") }
                    if (shownQueue.any { it.status == castbridge.core.tv.QueueStatus.FAILED || it.status == castbridge.core.tv.QueueStatus.DONE }) TextButton(onClick = { TransferQueue.clearFinished(ctx) }) { Text("Effacer les terminés") }
                }
            }
        }
    }
}
