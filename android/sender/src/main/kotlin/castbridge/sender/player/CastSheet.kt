package castbridge.sender.player

import android.bluetooth.BluetoothManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import castbridge.core.phone.CastAction
import castbridge.core.phone.CastPlan
import castbridge.core.phone.TargetKind
import castbridge.core.tv.Pin
import castbridge.core.tv.ResumableUpload
import castbridge.core.tv.TvClient
import castbridge.core.tv.WifiDirect
import castbridge.sender.BtCastPrefs
import castbridge.sender.BtUploadService
import castbridge.sender.DirectLink
import castbridge.sender.PinField
import castbridge.sender.PinStore
import castbridge.sender.Renderer
import castbridge.sender.SectionHeader
import castbridge.sender.Tv
import castbridge.sender.TvDiscovery
import castbridge.sender.TvStorageParser
import castbridge.sender.TvVolume
import castbridge.sender.UploadService
import castbridge.sender.Upnp
import castbridge.sender.rememberBtPermission
import kotlinx.coroutines.launch

/**
 * "Diffuser sur": CastBridge TVs found on the network (mDNS, remembered PIN) with the three ways to send, and DLNA TVs
 * (live only). [only] restricts the choices (long press "Copier / Déplacer vers la TV" in the phone library).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CastSheet(item: PlayItem, posMs: Long, durMs: Long, only: CastAction? = null, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val discovery = remember { TvDiscovery(ctx) }
    DisposableEffect(Unit) { discovery.start(); onDispose { discovery.stop() } }
    val tvs by discovery.tvs.collectAsState()
    val pins = remember { PinStore(ctx) }
    val btPrefs = remember { BtCastPrefs(ctx) }
    var renderers by remember { mutableStateOf<List<Renderer>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf<String?>(null) }
    val upload by UploadService.state.collectAsState()
    val busy = upload is UploadService.State.Uploading || upload is UploadService.State.Waiting
    val btUpload by BtUploadService.state.collectAsState()
    val btBusy = btUpload is ResumableUpload.State.Uploading || btUpload is ResumableUpload.State.Waiting
    val btGranted = rememberBtPermission().first
    val btDevices = remember(btGranted) {
        if (btGranted) runCatching {
            val bm = ctx.getSystemService(BluetoothManager::class.java)
            bm?.adapter?.bondedDevices.orEmpty().map { (it.name ?: it.address) to it.address }
        }.getOrDefault(emptyList()) else emptyList()
    }

    fun searchDlna() = scope.launch {
        searching = true
        renderers = runCatching { Upnp.discover(ctx) }.getOrDefault(emptyList())
        searching = false
    }
    LaunchedEffect(Unit) { searchDlna() }
    LaunchedEffect(tvs.size, btDevices) {
        if (expanded == null) {
            if (tvs.size == 1) expanded = tvs[0].name
            // No Wi-Fi TV found: auto-prioritize the remembered Bluetooth TV (a previous cast), so the copy is one tap away.
            else if (tvs.isEmpty() && btPrefs.lastAddress != null && btDevices.any { it.second == btPrefs.lastAddress })
                expanded = "bt:${btPrefs.lastAddress}"
        }
    }

    fun go(target: CastTarget, action: CastAction, dest: String? = null) {
        if (target is CastTarget.Bt) btPrefs.lastAddress = target.address
        CastSession.start(ctx, target, action, item, posMs, durMs, dest)
        onDismiss()
    }

    val src = item.castSource
    // A rotation while the sheet opens (the player turns to landscape) leaves it stuck at its peek: a new sheet per orientation.
    val conf = androidx.compose.ui.platform.LocalConfiguration.current
    key(conf.orientation, conf.screenWidthDp) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface) {
        LazyColumn(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            item {
                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Cast, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Diffuser sur", style = MaterialTheme.typography.titleLarge)
                        Text(item.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { SectionHeader("TV CastBridge") }
            if (tvs.isEmpty()) item {
                val btReady = btPrefs.lastAddress != null && btDevices.any { it.second == btPrefs.lastAddress }
                Text(if (btReady) "Aucune TV sur le Wi-Fi — Bluetooth priorisé (ci-dessous)."
                    else "Recherche… (la TV doit avoir CastBridge ouvert, sur le même Wi-Fi)",
                    Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(tvs, key = { "cb:" + it.name }) { tv ->
                var pin by remember(tv.name) { mutableStateOf(pins.get(tv.name)) }
                val open = expanded == tv.name
                ListItem(
                    modifier = Modifier.clickable { expanded = if (open) null else tv.name },
                    colors = ListItemDefaults.colors(containerColor = if (open) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
                    leadingContent = { Icon(Icons.Filled.Tv, null, tint = MaterialTheme.colorScheme.primary) },
                    headlineContent = { Text(tv.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text("CastBridge · ${tv.host}") },
                    trailingContent = { Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null) },
                )
                if (open) Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val pinOk = Pin.isValidFormat(pin)
                    if (!pinOk) PinField(pins, tv.name, pin, { pin = it }, Modifier.fillMaxWidth())
                    // Destination volume for the copy/move (LIVE has none). Fetched from the TV once the PIN is valid.
                    var dest by remember(tv.name) { mutableStateOf("auto") }
                    var volumes by remember(tv.name) { mutableStateOf<List<TvVolume>>(emptyList()) }
                    LaunchedEffect(tv.base, pinOk) {
                        if (pinOk) volumes = runCatching {
                            TvStorageParser.parse(TvClient(tv.base, pin).storage()).volumes.filter { it.present && it.writable }
                        }.getOrDefault(emptyList())
                    }
                    if (pinOk && volumes.size > 1) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Destination :", style = MaterialTheme.typography.labelMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(dest == "auto", { dest = "auto" }, { Text("Auto") })
                                volumes.forEach { v ->
                                    FilterChip(dest == v.id, { dest = v.id }, { Text(v.label) })
                                }
                            }
                        }
                    }
                    val actions = CastPlan.actions(TargetKind.CASTBRIDGE, src).filter { only == null || it == only }
                    for (a in actions) {
                        val needsUpload = a != CastAction.LIVE
                        ActionButton(icon(a), a.label, subtitle(a, item), enabled = pinOk && !(needsUpload && busy)) {
                            go(CastTarget.Box(tv, pin), a, if (needsUpload) dest else null)
                        }
                    }
                    if (busy) Text("Un envoi vers la TV est déjà en cours : copie et déplacement reviendront à la fin.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (actions.isEmpty()) Text("Rien à proposer pour ce fichier.", style = MaterialTheme.typography.bodySmall)
                }
            }
            if ((only == null || only == CastAction.COPY || only == CastAction.MOVE) && btGranted && btDevices.isNotEmpty()) {
                item { SectionHeader("TV Bluetooth (appairée)") }
                items(btDevices, key = { "bt:" + it.second }) { (devName, addr) ->
                    var pin by remember(addr) { mutableStateOf(pins.get("bt:$addr")) }
                    val open = expanded == "bt:$addr"
                    ListItem(
                        modifier = Modifier.clickable { expanded = if (open) null else "bt:$addr" },
                        colors = ListItemDefaults.colors(containerColor = if (open) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
                        leadingContent = { Icon(Icons.Filled.Bluetooth, null, tint = MaterialTheme.colorScheme.primary) },
                        headlineContent = { Text(devName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text("Sans Wi-Fi commun : envoie le fichier en Bluetooth, puis la TV le lit") },
                        trailingContent = { Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null) },
                    )
                    if (open) Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val pinOk = Pin.isValidFormat(pin)
                        if (!pinOk) PinField(pins, "bt:$addr", pin, { pin = it }, Modifier.fillMaxWidth())
                        val actions = CastPlan.actions(TargetKind.CASTBRIDGE, src).filter { it != CastAction.LIVE && (only == null || it == only) }
                        for (a in actions) {
                            ActionButton(icon(a), a.label, btSubtitle(a, item), enabled = pinOk && !btBusy) {
                                go(CastTarget.Bt(addr, pin, devName), a)
                            }
                        }
                        if (actions.isEmpty()) Text("Rien à proposer pour ce fichier en Bluetooth.", style = MaterialTheme.typography.bodySmall)
                        if (btBusy) Text("Un envoi Bluetooth est déjà en cours.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (DirectLink.state == DirectLink.State.Connected) {
                item { SectionHeader("TV Wi-Fi Direct") }
                item {
                    val wdPinKey = WifiDirect.BASE_URL
                    var pin by remember(wdPinKey) { mutableStateOf(pins.get(wdPinKey)) }
                    val open = expanded == "wd"
                    ListItem(
                        modifier = Modifier.clickable { expanded = if (open) null else "wd" },
                        colors = ListItemDefaults.colors(containerColor = if (open) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
                        leadingContent = { Icon(Icons.Filled.Wifi, null, tint = MaterialTheme.colorScheme.primary) },
                        headlineContent = { Text("CastBridge TV (Wi-Fi Direct)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text("${WifiDirect.GROUP_OWNER_IP} · sans routeur") },
                        trailingContent = { Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null) },
                    )
                    if (open) Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val pinOk = Pin.isValidFormat(pin)
                        if (!pinOk) PinField(pins, wdPinKey, pin, { pin = it }, Modifier.fillMaxWidth())
                        val actions = CastPlan.actions(TargetKind.CASTBRIDGE, src).filter { only == null || it == only }
                        for (a in actions) {
                            ActionButton(icon(a), a.label, subtitle(a, item), enabled = pinOk && !busy) {
                                go(CastTarget.Box(Tv("CastBridge TV (Wi-Fi Direct)", WifiDirect.GROUP_OWNER_IP, 8765), pin), a)
                            }
                        }
                        if (actions.isEmpty()) Text("Rien à proposer pour ce fichier.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (only == null || only == CastAction.LIVE) {
                item {
                    SectionHeader("TV DLNA") {
                        IconButton({ searchDlna() }, enabled = !searching) { Icon(Icons.Filled.Refresh, "Rechercher") }
                    }
                }
                if (searching) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) }
                if (!searching && renderers.isEmpty()) item {
                    Text("Aucune TV DLNA trouvée (même Wi-Fi ? isolation des clients ?)", Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(renderers, key = { "dlna:" + it.avtControlUrl }) { r ->
                    val ok = CastPlan.actions(TargetKind.DLNA, src).isNotEmpty()
                    ListItem(
                        modifier = Modifier.clickable(enabled = ok) { go(CastTarget.Dlna(r), CastAction.LIVE) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = { Icon(Icons.Filled.Tv, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        headlineContent = { Text(r.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text("DLNA · lecture en direct depuis le téléphone") },
                        trailingContent = { Icon(Icons.Filled.PlayArrow, null) },
                    )
                }
            }
        }
    }
    }
}

private fun icon(a: CastAction): ImageVector = when (a) {
    CastAction.LIVE -> Icons.Filled.CastConnected
    CastAction.COPY -> Icons.Filled.ContentCopy
    CastAction.MOVE -> Icons.Filled.DriveFileMove
}

private fun subtitle(a: CastAction, item: PlayItem): String = when (a) {
    CastAction.LIVE -> if (item.isWeb) "La TV ouvre le lien elle-même" else "La TV lit depuis le téléphone, qui doit rester sur le Wi-Fi"
    CastAction.COPY -> if (item.kind == castbridge.core.phone.MediaKind.IMAGE) "Garder une copie sur la TV"
        else "La lecture passe sur la TV dès qu'elle a assez d'avance ; le téléphone peut ensuite partir"
    CastAction.MOVE -> "Comme « Copier », puis supprimé du téléphone une fois la copie vérifiée (taille exacte)"
}

private fun btSubtitle(a: CastAction, item: PlayItem): String = when (a) {
    CastAction.COPY -> if (item.kind == castbridge.core.phone.MediaKind.IMAGE) "Garder une copie sur la TV (Bluetooth)"
        else "Envoie le fichier en Bluetooth (ou Wi-Fi/Wi-Fi Direct si possible), puis la TV le lit."
    CastAction.MOVE -> "Comme « Copier » par Bluetooth, puis supprimé du téléphone une fois la copie vérifiée (taille exacte)."
    else -> ""
}

@Composable
private fun ActionButton(icon: ImageVector, title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
