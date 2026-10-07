@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package castbridge.sender

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import castbridge.core.remote.RemotePlan
import castbridge.core.remote.KeyAction
import castbridge.core.remote.RemoteGlobal
import castbridge.core.remote.RemoteKey
import castbridge.core.remote.RemoteSession
import castbridge.core.remote.TextDiff
import castbridge.core.remote.TextMode
import castbridge.core.remote.TouchpadMapper
import castbridge.core.tv.Pin
import castbridge.core.trust.TvAuth
import castbridge.core.tv.ReceiverServer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.hypot

private enum class Pad(val label: String) { KEYS("Touches"), TOUCH("Pavé tactile"), DIGITS("Chiffres") }

/** Vibration of a key, if the user wants it. */
private fun View.tick(on: Boolean, long: Boolean = false) {
    if (on) performHapticFeedback(if (long) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.KEYBOARD_TAP)
}

/**
 * Press handling shared by every key: "down" as soon as the finger touches (lowest latency; the TV sees a real press, so a
 * held OK becomes a long press there), repeats every 90 ms after 400 ms for keys that repeat (arrows, volume, ±10 s…), "up"
 * when the finger leaves.
 */
@Composable
private fun Modifier.remoteKey(k: RemoteKey, pressed: MutableState<Boolean>? = null): Modifier {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val haptics = remember { RemotePrefs(ctx).haptics }
    return this.pointerInput(k) {
        awaitEachGesture {
            awaitFirstDown().consume()
            pressed?.value = true
            view.tick(haptics)
            RemoteController.key(k, KeyAction.DOWN)
            val rep: Job? = if (k.repeatable) scope.launch {
                delay(400); var n = 1
                while (isActive) { RemoteController.key(k, KeyAction.DOWN, n); if (n % 4 == 0) view.tick(haptics); n++; delay(90) }
            } else null
            waitForUpOrCancellation()
            rep?.cancel()
            pressed?.value = false
            RemoteController.key(k, KeyAction.UP)
        }
    }
}

@Composable
private fun RemoteKeyButton(k: RemoteKey, icon: ImageVector?, modifier: Modifier = Modifier, text: String? = null, size: Int = 56, accent: Boolean = false) {
    // A key no active route can carry is hidden (the space stays, so the layout does not jump): over Bluetooth, or with the smart remote's strategy.
    val avail = availableKeys.value
    val available = SmartRemote.available.collectAsState().value
    if ((avail != null && k !in avail) || (available != null && k !in available)) { Spacer(modifier.size(size.dp)); return }
    val pressed = remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    Box(
        modifier.size(size.dp).clip(CircleShape)
            .background(if (pressed.value) cs.primary.copy(alpha = 0.45f) else if (accent) cs.primaryContainer else cs.surfaceVariant)
            .remoteKey(k, pressed).semantics { contentDescription = k.label },
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) Icon(icon, null, Modifier.size((size * 0.5f).dp), tint = cs.onSurface)
        else Text(text ?: k.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

/** Big round D-pad: four arrows around a central OK. One gesture = one zone (chosen where the finger lands). */
@Composable
private fun DPad(sizeDp: Int) {
    val cs = MaterialTheme.colorScheme
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val haptics = remember { RemotePrefs(ctx).haptics }
    var zone by remember { mutableStateOf<RemoteKey?>(null) }
    Box(
        Modifier.size(sizeDp.dp).clip(CircleShape).background(cs.surfaceVariant)
            .border(BorderStroke(1.dp, cs.outline), CircleShape)
            .semantics { contentDescription = "Pavé directionnel : haut, bas, gauche, droite, OK au centre" }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val d = awaitFirstDown(); d.consume()
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val v = d.position - c
                    val r = hypot(v.x, v.y)
                    val k = when {
                        r < size.width * 0.21f -> RemoteKey.DPAD_CENTER
                        else -> {
                            val a = Math.toDegrees(atan2(v.y.toDouble(), v.x.toDouble()))   // 0 = right, 90 = down
                            when { a >= -45 && a < 45 -> RemoteKey.DPAD_RIGHT; a >= 45 && a < 135 -> RemoteKey.DPAD_DOWN; a >= -135 && a < -45 -> RemoteKey.DPAD_UP; else -> RemoteKey.DPAD_LEFT }
                        }
                    }
                    zone = k
                    view.tick(haptics)
                    RemoteController.key(k, KeyAction.DOWN)
                    val rep: Job? = if (k.repeatable) scope.launch {
                        delay(400); var n = 1
                        while (isActive) { RemoteController.key(k, KeyAction.DOWN, n); if (n % 4 == 0) view.tick(haptics); n++; delay(90) }
                    } else null
                    waitForUpOrCancellation()
                    rep?.cancel(); zone = null
                    RemoteController.key(k, KeyAction.UP)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val hi = cs.primary.copy(alpha = 0.35f)
        Canvas(Modifier.fillMaxSize()) {
            val start = when (zone) { RemoteKey.DPAD_RIGHT -> -45f; RemoteKey.DPAD_DOWN -> 45f; RemoteKey.DPAD_LEFT -> 135f; RemoteKey.DPAD_UP -> 225f; else -> null }
            if (start != null) drawArc(hi, start, 90f, useCenter = true, topLeft = Offset.Zero, size = Size(size.width, size.height))
        }
        val arrow = (sizeDp * 0.16f).dp
        Icon(Icons.Filled.KeyboardArrowUp, null, Modifier.align(Alignment.TopCenter).padding(top = 10.dp).size(arrow), tint = cs.onSurface)
        Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp).size(arrow), tint = cs.onSurface)
        Icon(Icons.Filled.KeyboardArrowLeft, null, Modifier.align(Alignment.CenterStart).padding(start = 10.dp).size(arrow), tint = cs.onSurface)
        Icon(Icons.Filled.KeyboardArrowRight, null, Modifier.align(Alignment.CenterEnd).padding(end = 10.dp).size(arrow), tint = cs.onSurface)
        Box(Modifier.size((sizeDp * 0.40f).dp).clip(CircleShape)
            .background(if (zone == RemoteKey.DPAD_CENTER) cs.primary else cs.primaryContainer), contentAlignment = Alignment.Center) {
            Text("OK", fontSize = (sizeDp * 0.09f).sp, fontWeight = FontWeight.Bold, color = if (zone == RemoteKey.DPAD_CENTER) cs.onPrimary else cs.onPrimaryContainer)
        }
    }
}

/** Touchpad: slide = arrows (one per ~40 dp), tap = OK, press and hold = long OK. */
@Composable
private fun TouchPad(modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val view = LocalView.current
    val ctx = LocalContext.current
    val haptics = remember { RemotePrefs(ctx).haptics }
    val step = with(LocalDensity.current) { 40.dp.toPx() }
    val mapper = remember(step) { TouchpadMapper(step) }
    val scope = rememberCoroutineScope()
    var last by remember { mutableStateOf<RemoteKey?>(null) }
    Box(modifier.clip(RoundedCornerShape(24.dp)).background(cs.surfaceVariant).border(BorderStroke(1.dp, cs.outline), RoundedCornerShape(24.dp))
        .semantics { contentDescription = "Pavé tactile : glissez pour déplacer la sélection, touchez pour OK" }
        .pointerInput(step) {
            awaitEachGesture {
                val d = awaitFirstDown(); d.consume()
                mapper.reset()
                var moved = 0f
                var longDone = false
                val slop = viewConfiguration.touchSlop
                val long = scope.launch { delay(600); if (moved < slop) { longDone = true; view.tick(haptics, long = true); RemoteController.key(RemoteKey.DPAD_CENTER, KeyAction.LONG); last = RemoteKey.DPAD_CENTER } }
                while (true) {
                    val ev = awaitPointerEvent()
                    val ch = ev.changes.firstOrNull() ?: break
                    if (!ch.pressed) break
                    val delta = ch.positionChange(); ch.consume()
                    moved += hypot(delta.x, delta.y)
                    for (k in mapper.move(delta.x, delta.y)) { view.tick(haptics); RemoteController.key(k); last = k }
                }
                long.cancel()
                if (!longDone && moved < slop) { view.tick(haptics); RemoteController.key(mapper.tap()); last = RemoteKey.DPAD_CENTER }
                mapper.reset()
            }
        }, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.TouchApp, null, Modifier.size(48.dp), tint = cs.onSurfaceVariant)
            Text("Glissez pour vous déplacer\nTouchez pour OK · appui long = OK long", textAlign = TextAlign.Center, color = cs.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium)
            last?.let { Text(it.label, Modifier.padding(top = 8.dp), color = cs.primary, style = MaterialTheme.typography.titleMedium) }
        }
    }
}

@Composable
private fun DigitPad() {
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEach { r -> Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) { r.forEach { d -> RemoteKeyButton(RemoteKey.parse(d)!!, null, size = 72) } } }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            RemoteKeyButton(RemoteKey.DEL, Icons.Filled.Backspace, size = 72)
            RemoteKeyButton(RemoteKey.NUM_0, null, size = 72)
            RemoteKeyButton(RemoteKey.DPAD_CENTER, null, text = "OK", size = 72, accent = true)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
/** Keys worth showing right now; null = all (Wi-Fi: the TV explains refusals itself). */
private val availableKeys = mutableStateOf<Set<RemoteKey>?>(null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { RemotePrefs(ctx) }
    val pins = remember { PinStore(ctx) }
    val discovery = remember { TvDiscovery(ctx) }
    DisposableEffect(Unit) { discovery.start(); onDispose { discovery.stop() } }
    val tvs by discovery.tvs.collectAsState()
    var tvName by rememberSaveable { mutableStateOf(prefs.tvName) }
    var manualHost by rememberSaveable { mutableStateOf(prefs.manualHost) }
    var bt by rememberSaveable { mutableStateOf(prefs.btFallback) }
    val link by TvLinkManager.state.collectAsState()
    val trustedTv = remember(link) { TvLinkManager.saved.default() }
    val tv: RemoteTv? = when {
        // a TV this phone is trusted by: found over Bluetooth, no PIN, Wi-Fi address from HELLO, Bluetooth as the fallback
        trustedTv != null && (tvName == null || tvName == trustedTv.mdns || tvName == trustedTv.name) ->
            (link as? LinkUi.Connected)?.session?.base?.let { b -> java.net.URI(b) }.let { u ->
                RemoteTv(trustedTv.mdns ?: trustedTv.name, u?.host ?: trustedTv.lastIps.firstOrNull(), u?.port ?: trustedTv.port, trustedTv.address)
            }
        tvName?.startsWith("manual:") == true && manualHost != null -> RemoteTv("manual:$manualHost", manualHost, prefs.manualPort, bt)
        tvName == "bt" && bt != null -> RemoteTv("bt", null, btAddress = bt)
        else -> tvs.firstOrNull { it.name == tvName }?.let { RemoteTv(it.name, it.host, it.port, bt) }
    }
    var pin by remember(tv?.pinKey) { mutableStateOf(tv?.let { pins.get(it.pinKey) }.orEmpty()) }
    var chooser by remember { mutableStateOf(false) }
    var keyboard by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var pad by rememberSaveable { mutableStateOf(Pad.KEYS) }
    var wholeTv by rememberSaveable { mutableStateOf(prefs.wholeTv) }
    var volumeKeys by remember { mutableStateOf(prefs.volumeKeys) }
    var haptics by remember { mutableStateOf(prefs.haptics) }
    var background by remember { mutableStateOf(prefs.background) }

    val status by RemoteController.status.collectAsState()
    val st by RemoteController.tvState.collectAsState()
    val notice by RemoteController.notice.collectAsState()
    val rtt by RemoteController.lastRtt.collectAsState()
    val smartKeys by SmartRemote.available.collectAsState()
    val smartUi by SmartRemote.ui.collectAsState()
    LaunchedEffect(Unit) { SmartRemote.resume(ctx) }

    LaunchedEffect(link, tv?.pinKey) { tv?.let { pins.get(it.pinKey) }.orEmpty().let { p -> if (TvAuth.isToken(p) && p != pin) pin = p } }
    LaunchedEffect(status.link) { if (status.link == RemoteSession.Link.BAD_PIN && TvAuth.isToken(pin)) TvLinkManager.poke() }
    LaunchedEffect(tv, pin) {
        if (tv != null && TvAuth.isUsable(pin)) RemoteController.connect(ctx, tv, pin, bt) else RemoteController.disconnect()
    }
    LaunchedEffect(tvName, tvs) { if (tvName == null && tvs.size == 1) { tvName = tvs[0].name; prefs.tvName = tvName } }
    LaunchedEffect(Unit) { if (tvName == null && tvs.isEmpty()) { delay(4000); if (tvName == null) chooser = true } }

    var routes by rememberSaveable { mutableStateOf(false) }
    val viaBluetooth = status.via == "Bluetooth" || prefs.btOnly
    val keysNow = st?.let { t -> if (viaBluetooth) RemotePlan.availableKeys(t.castbridgeFront, t.systemConnected && wholeTv, t.vendorAvailable && wholeTv) else null }
    SideEffect { availableKeys.value = keysNow }
    if (routes) { BtRoutesScreen { routes = false }; return }
    val cs = MaterialTheme.colorScheme
    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = {
                    Column(Modifier.clickable { chooser = true }) {
                        Text("Télécommande", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        Text(tv?.label ?: "Choisir la TV ▾", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = { IconButton(onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Fermer") } },
                actions = {
                    // R-23: badge + « ⋮ » only (the title is also a « choose the TV » button); the badge is capped so the title keeps its room
                    if (smartKeys != null) Text(smartUi.activeLabel ?: "Ma TV", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 96.dp).padding(end = 4.dp))
                    else Box(Modifier.widthIn(max = 120.dp)) { LinkBadge(status, rtt) }
                    IconButton({ menu = true }) { Icon(Icons.Filled.MoreVert, castbridge.core.ux.UxLabels.OPTIONS) }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text(castbridge.core.ux.UxLabels.PICK_TV) }, onClick = { menu = false; chooser = true },
                            leadingIcon = { Icon(Icons.Filled.Tv, null) })
                        DropdownMenuItem(text = { Text("Boutons de volume du téléphone → TV") }, onClick = { volumeKeys = !volumeKeys; prefs.volumeKeys = volumeKeys },
                            trailingIcon = { Checkbox(volumeKeys, null) })
                        DropdownMenuItem(text = { Text("Garder la télécommande en arrière-plan") }, onClick = {
                            background = !background; prefs.background = background
                            if (background) RemoteService.start(ctx) else RemoteService.stop(ctx)
                        }, trailingIcon = { Checkbox(background, null) })
                        DropdownMenuItem(text = { Text("Vibrer à chaque touche") }, onClick = { haptics = !haptics; prefs.haptics = haptics },
                            trailingIcon = { Checkbox(haptics, null) })
                        DropdownMenuItem(text = { Text("Ma TV · voies Bluetooth…") }, onClick = { menu = false; routes = true })
                        DropdownMenuItem(text = { Text("Ma TV : autres marques, stratégies, test…") }, onClick = { menu = false; MyTvActivity.open(ctx) })
                        DropdownMenuItem(text = { Text(castbridge.core.trust.TvDeviceRequestTexts.TITLE) }, onClick = { menu = false; TvDeviceRequestActivity.open(ctx) })
                        DropdownMenuItem(text = { Text("Aide « toute la TV » sur la TV") }, onClick = { menu = false; RemoteController.setup() })
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.surface),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (smartKeys == null && (tv != null && !TvAuth.isUsable(pin) || status.link == RemoteSession.Link.BAD_PIN && !TvAuth.isToken(pin))) {
                Text(if (status.link == RemoteSession.Link.BAD_PIN) (status.message ?: "Code refusé") else "Saisissez le code affiché sur la TV",
                    color = if (status.link == RemoteSession.Link.BAD_PIN) cs.error else cs.onSurface)
                PinField(pins, tv?.pinKey, pin, { pin = it }, Modifier.fillMaxWidth())
            }
            if (smartKeys == null && status.link == RemoteSession.Link.OFFLINE && tv != null && TvAuth.isUsable(pin))
                Text(status.message ?: "TV injoignable", color = cs.error, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            // A refusal from the TV (e.g. "no CastBridge screen in front"), shown for 6 s.
            notice?.let { (at, m) ->
                var visible by remember(at) { mutableStateOf(true) }
                LaunchedEffect(at) { delay(6000); visible = false }
                if (visible) Surface(color = cs.secondary.copy(alpha = 0.15f), shape = RoundedCornerShape(12.dp)) {
                    Text(m, Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall, color = cs.onSurface)
                }
            }
            RemoteController.statusLine(status, prefs.btOnly)?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant) }
            WholeTvRow(wholeTv, st) { on -> wholeTv = on; prefs.wholeTv = on; RemoteController.setWholeTv(on) }

            // Rangée système : la sixième touche, « TV » (OpenTv.kt), fait passer CastBridge-TV devant l'application affichée sur la TV, comme une touche YouTube ou Netflix.
            // Les touches suivent la largeur (jamais sous 40 dp, jamais collées) ; avec une autre stratégie (« Ma TV ») la touche n'existe pas.
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val n = if (smartKeys == null) 6 else 5
                val ks = ((maxWidth.value - 8f * (n + 1)) / n).toInt().coerceIn(40, 56)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    RemoteKeyButton(RemoteKey.BACK, Icons.AutoMirrored.Filled.ArrowBack, size = ks)
                    RemoteKeyButton(RemoteKey.HOME, Icons.Filled.Home, size = ks)
                    RemoteKeyButton(RemoteKey.MENU, Icons.Filled.Menu, size = ks)
                    RemoteKeyButton(RemoteKey.INFO, Icons.Filled.Info, size = ks)
                    Box(Modifier.size(ks.dp).clip(CircleShape).background(if (st?.textField == true) cs.primary.copy(alpha = 0.5f) else cs.surfaceVariant)
                        .clickable { keyboard = true }.semantics { contentDescription = "Clavier : saisir du texte sur la TV" }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Keyboard, null, Modifier.size((ks / 2).dp))
                    }
                    if (smartKeys == null) OpenTvKey(ks)
                }
            }
            OpenTvLine(Modifier.fillMaxWidth())
            if (wholeTv && st?.systemConnected == true) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                SystemButton(Icons.Filled.Tv, "Accueil TV") { RemoteController.global(RemoteGlobal.HOME) }
                SystemButton(Icons.Filled.ViewCarousel, "Récents") { RemoteController.global(RemoteGlobal.RECENTS) }
                SystemButton(Icons.Filled.Notifications, "Notifs") { RemoteController.global(RemoteGlobal.NOTIFICATIONS) }
                SystemButton(cbv(R.drawable.ic_cb_reglages), "Réglages") { RemoteController.global(RemoteGlobal.QUICK_SETTINGS) }
            }

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Pad.values().forEachIndexed { i, p -> SegmentedButton(pad == p, { pad = p }, SegmentedButtonDefaults.itemShape(i, Pad.values().size)) { Text(p.label, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) } }
            }
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val side = minOf(maxWidth.value * 0.82f, 320f).toInt()
                val need = when (pad) { Pad.KEYS, Pad.TOUCH -> RemoteKey.DPAD_UP; Pad.DIGITS -> RemoteKey.NUM_1 }
                if (smartKeys != null && need !in smartKeys!!) Text("Ce pavé n'existe pas avec « ${smartUi.activeLabel ?: "cette stratégie"} » (voir Ma TV).",
                    color = cs.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
                else when (pad) {
                    Pad.KEYS -> DPad(side)
                    Pad.TOUCH -> TouchPad(Modifier.size(minOf(maxWidth.value, 360f).dp, side.dp))
                    Pad.DIGITS -> DigitPad()
                }
            }

            // Playback: six keys must never touch each other, so their sizes follow the width (gaps stay >= 8 dp on a 320 dp phone)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val f = ((maxWidth.value - 8f * 7) / 288f).coerceIn(0.85f, 1f)
                fun d(v: Int) = (v * f).toInt().coerceAtLeast(40)      // LOW (audit 2026-10-07): a key is never under 40 dp
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    RemoteKeyButton(RemoteKey.PREVIOUS, Icons.Filled.SkipPrevious, size = d(44))
                    RemoteKeyButton(RemoteKey.REWIND, cbv(R.drawable.ic_cb_recul_10s), size = d(48))
                    RemoteKeyButton(RemoteKey.PLAY_PAUSE, cbv(R.drawable.ic_cb_lecture), size = d(60), accent = true)
                    RemoteKeyButton(RemoteKey.FAST_FORWARD, cbv(R.drawable.ic_cb_avance_10s), size = d(48))
                    RemoteKeyButton(RemoteKey.NEXT, Icons.Filled.SkipNext, size = d(44))
                    RemoteKeyButton(RemoteKey.STOP, Icons.Filled.Stop, size = d(44))
                }
            }
            // Volume and channels
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                RemoteKeyButton(RemoteKey.VOLUME_DOWN, Icons.AutoMirrored.Filled.VolumeDown)
                RemoteKeyButton(RemoteKey.VOLUME_MUTE, Icons.AutoMirrored.Filled.VolumeOff)
                RemoteKeyButton(RemoteKey.VOLUME_UP, Icons.AutoMirrored.Filled.VolumeUp)
                RemoteKeyButton(RemoteKey.CHANNEL_DOWN, null, text = "CH−")
                RemoteKeyButton(RemoteKey.CHANNEL_UP, null, text = "CH+")
            }
            st?.let { s ->
                val vol = when { s.volumeFixed -> "volume fixe (réglé par la TV/l'ampli)"; s.muted == true -> "muet"; s.volume != null -> "volume ${s.volume} %"; else -> "" }
                val where = when { s.castbridgeFront -> "CastBridge à l'écran" + (s.screen?.let { " (${screenName(it)})" } ?: ""); else -> "CastBridge n'est pas à l'écran" }
                Text(listOf(where, vol).filter { it.isNotEmpty() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (chooser) TvChooser(tvs, tvName, manualHost, bt, onDismiss = { chooser = false }) { name, host, port, btAddr ->
        tvName = name; prefs.tvName = name
        if (host != null) { manualHost = host; prefs.manualHost = host; prefs.manualPort = port }
        bt = btAddr; prefs.btFallback = btAddr
        chooser = false
    }
    if (keyboard) KeyboardSheet(st?.textField == true) { keyboard = false }
}

private fun screenName(s: String) = when (s) {
    "PlayerActivity" -> "accueil / lecteur"; "QuizActivity" -> "quiz"; "ChessActivity" -> "échecs"; "DownloadsActivity" -> "téléchargements"
    "RemoteSetupActivity" -> "aide télécommande"; else -> s
}

@Composable
private fun LinkBadge(s: RemoteSession.Status, rtt: Long?) {
    // signal colour from the shared core rules (green / blue / orange when slow / red); the word stays in the text
    val verdict = castbridge.core.tv.status.StatusRules.phoneLink(s.link == RemoteSession.Link.CONNECTED, s.link == RemoteSession.Link.CONNECTING,
        s.link == RemoteSession.Link.OFFLINE, s.rttMs ?: rtt)
    val color = androidx.compose.ui.graphics.Color(castbridge.core.tv.status.StatusPalette.dotLight(verdict.level))
    val text = when (s.link) {
        RemoteSession.Link.CONNECTED -> "${s.via ?: ""}${(s.rttMs ?: rtt)?.let { " · $it ms" } ?: ""}".ifBlank { verdict.word } + if (verdict.level == castbridge.core.tv.status.StatusLevel.WARN) " · ${verdict.word}" else ""
        RemoteSession.Link.CONNECTING -> "connexion…"
        RemoteSession.Link.OFFLINE -> "hors ligne"
        RemoteSession.Link.BAD_PIN -> "code ?"
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 4.dp).semantics { contentDescription = "Liaison : $text" }) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SystemButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val view = LocalView.current
    Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable { view.tick(true); onClick() }.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null); Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

/** « CastBridge seulement » / « Toute la TV », with the TV's accessibility state and a way to get the help on the TV. */
@Composable
private fun WholeTvRow(on: Boolean, st: RemoteTvState?, onChange: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Piloter toute la TV", style = MaterialTheme.typography.titleSmall)
                    Text(when {
                        st == null -> "État inconnu (TV non connectée)"
                        !st.systemAvailable && !st.systemConnected && !st.vendorAvailable -> "Indisponible sur cette TV. " + (st.systemReason ?: "") + " La télécommande pilote CastBridge, le volume et le muet."
                        st.vendorAvailable -> "Toute la TV (service du fabricant) : prêt. Flèches, OK, Retour, Accueil et volume dans les autres applications, même sans Wi-Fi."
                        st.systemConnected -> "Actif sur la TV (accessibilité) : Retour, Accueil, flèches et OK aussi hors de CastBridge"
                        st.systemEnabled -> "Activé sur la TV, démarrage…"
                        else -> "Inactif : la télécommande pilote CastBridge seulement. À activer une fois sur la TV."
                    }, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                Switch(on && (st?.systemAvailable != false || st.systemConnected || st.vendorAvailable), onChange, enabled = st == null || st.systemAvailable || st.systemConnected || st.vendorAvailable)
            }
            if (st != null && st.systemAvailable && !st.systemConnected && on)
                TextButton(onClick = { RemoteController.setup() }) { Text("Afficher la marche à suivre sur la TV") }
        }
    }
}

/** Typing on the TV: what is typed here goes live into the field that has the focus on the TV (diff: letters added, DEL for removed ones). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KeyboardSheet(fieldOnTv: Boolean, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf("") }
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = cs.background) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Saisir sur la TV", style = MaterialTheme.typography.titleLarge)
            Text(if (fieldOnTv) "Un champ de saisie est sélectionné sur la TV : ce que vous tapez y apparaît aussitôt."
                else "Sélectionnez d'abord un champ sur la TV (flèches puis OK), par exemple la recherche ou le lien d'un téléchargement.",
                style = MaterialTheme.typography.bodySmall, color = if (fieldOnTv) cs.primary else cs.onSurfaceVariant)
            OutlinedTextField(value, { nv ->
                val d = TextDiff.between(value, nv)
                repeat(minOf(d.deletes, 200)) { RemoteController.key(RemoteKey.DEL) }
                if (d.insert.isNotEmpty()) d.insert.chunked(castbridge.core.remote.RemoteApi.MAX_TEXT).forEach { RemoteController.text(it.filter { c -> c >= ' ' }) }
                value = nv
            }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Texte") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { RemoteController.key(RemoteKey.ENTER); value = "" }))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = { RemoteController.key(RemoteKey.ENTER); value = "" }) { Text("Entrée") }
                OutlinedButton(onClick = { RemoteController.key(RemoteKey.DEL) }) { Icon(Icons.Filled.Backspace, "Effacer un caractère") }
                OutlinedButton(onClick = { RemoteController.text("", TextMode.CLEAR); value = "" }) { Text("Vider le champ") }
            }
        }
    }
}

/** Which TV: those found on the Wi-Fi (mDNS), an address typed by hand, and an optional paired Bluetooth TV as a fallback. */
@SuppressLint("MissingPermission")
@Composable
private fun TvChooser(tvs: List<Tv>, current: String?, manualHost: String?, bt: String?, onDismiss: () -> Unit,
                      onPick: (name: String, host: String?, port: Int, bt: String?) -> Unit) {
    val ctx = LocalContext.current
    var host by remember { mutableStateOf(manualHost.orEmpty()) }
    var btSel by remember { mutableStateOf(bt) }
    val (granted, askUi) = rememberBtPermission()
    val paired = remember(granted) {
        if (!granted) emptyList() else runCatching {
            ctx.getSystemService(BluetoothManager::class.java)?.adapter?.bondedDevices.orEmpty().map { (it.name ?: it.address) to it.address }
        }.getOrDefault(emptyList())
    }
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onDismiss) { Text("Fermer") } }, title = { Text("Choisir la TV") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Sur le Wi-Fi", style = MaterialTheme.typography.labelLarge)
            if (tvs.isEmpty()) Text("Recherche… (même Wi-Fi, app CastBridge TV installée)", style = MaterialTheme.typography.bodySmall)
            tvs.forEach { t ->
                DeviceRow(t.name.removePrefix("CastBridge TV ").ifBlank { t.name }, "${t.host}:${t.port}", t.name == current) { onPick(t.name, null, ReceiverServer.PORT, btSel) }
            }
            Text("Adresse manuelle", style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(host, { host = it.trim() }, Modifier.weight(1f), singleLine = true, placeholder = { Text("192.168.1.20:8765") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                TextButton(enabled = host.isNotBlank(), onClick = {
                    val h = host.substringBefore(':'); val p = host.substringAfter(':', "").toIntOrNull() ?: ReceiverServer.PORT
                    onPick("manual:$h", h, p, btSel)
                }) { Text("OK") }
            }
            Text("Secours Bluetooth (sans réseau commun)", style = MaterialTheme.typography.labelLarge)
            Text("Si la TV n'est pas joignable par le Wi-Fi, la télécommande passe par le Bluetooth (TV appairée, CastBridge TV ouverte). " +
                "Pendant ce temps, les envois de fichiers par Bluetooth attendent.", style = MaterialTheme.typography.bodySmall)
            if (!granted) askUi()
            paired.forEach { (n, a) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(btSel == a, { btSel = a })
                    Text(n, Modifier.weight(1f))
                    TextButton({ onPick("bt", null, ReceiverServer.PORT, a) }) { Text("Seul") }
                }
            }
            if (paired.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(btSel == null, { btSel = null }); Text("Pas de secours Bluetooth") }
            if (current != null && btSel != bt) TextButton({
                onPick(current, null, ReceiverServer.PORT, btSel)
            }) { Text("Enregistrer le secours Bluetooth") }
        }
    })
}
