package castbridge.sender.player

import castbridge.sender.cbv

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
import castbridge.sender.PinField
import castbridge.sender.PinStore
import castbridge.sender.Renderer
import castbridge.sender.SectionHeader
import castbridge.sender.TvDiscovery
import castbridge.sender.UploadService
import castbridge.sender.Upnp
import kotlinx.coroutines.launch

/**
 * "Diffuser sur": CastBridge TVs found on the network (mDNS, remembered PIN) with the three ways to send, and DLNA TVs
 * (live only). [only] restricts the choices (long press "Copier / Déplacer vers la TV" in the phone library).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastSheet(item: PlayItem, posMs: Long, durMs: Long, only: CastAction? = null, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val discovery = remember { TvDiscovery(ctx) }
    DisposableEffect(Unit) { discovery.start(); onDispose { discovery.stop() } }
    val tvs by discovery.tvs.collectAsState()
    val pins = remember { PinStore(ctx) }
    var renderers by remember { mutableStateOf<List<Renderer>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf<String?>(null) }
    val upload by UploadService.state.collectAsState()
    val busy = upload is UploadService.State.Uploading || upload is UploadService.State.Waiting

    fun searchDlna() = scope.launch {
        searching = true
        renderers = runCatching { Upnp.discover(ctx) }.getOrDefault(emptyList())
        searching = false
    }
    LaunchedEffect(Unit) { searchDlna() }
    LaunchedEffect(tvs.size) { if (expanded == null && tvs.size == 1) expanded = tvs[0].name }

    fun go(target: CastTarget, action: CastAction) {
        CastSession.start(ctx, target, action, item, posMs, durMs)
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
                    Icon(cbv(castbridge.sender.R.drawable.ic_cb_caster), null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(castbridge.core.ux.SendWays.SHEET_TITLE, style = MaterialTheme.typography.titleLarge)
                        Text(item.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { SectionHeader("TV CastBridge") }
            if (tvs.isEmpty()) item {
                Text("Recherche… (la TV doit avoir CastBridge ouvert, sur le même Wi-Fi)", Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
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
                    val pinOk = castbridge.core.trust.TvCredential.isUsable(pin)   // a trusted phone holds a token, not a 6-digit PIN
                    if (!pinOk) PinField(pins, tv.name, pin, { pin = it }, Modifier.fillMaxWidth())
                    val actions = castbridge.core.ux.SendWays.castOrder(CastPlan.actions(TargetKind.CASTBRIDGE, src).filter { only == null || it == only })
                    for (a in actions) {
                        val needsUpload = a != CastAction.LIVE
                        ActionButton(icon(a), castbridge.core.ux.SendWays.castLabel(a, item.kind), subtitle(a, item), enabled = pinOk && !(needsUpload && busy)) {
                            go(CastTarget.Box(tv, pin), a)
                        }
                    }
                    if (busy) Text("Un envoi vers la TV est déjà en cours : copie et déplacement reviendront à la fin.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (actions.isEmpty()) Text("Rien à proposer pour ce fichier.", style = MaterialTheme.typography.bodySmall)
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
                        trailingContent = { Icon(cbv(castbridge.sender.R.drawable.ic_cb_lecture), null) },
                    )
                }
            }
        }
    }
    }
}

@androidx.compose.runtime.Composable
private fun icon(a: CastAction): ImageVector = when (a) {
    CastAction.LIVE -> cbv(castbridge.sender.R.drawable.ic_cb_caster)
    CastAction.COPY -> cbv(castbridge.sender.R.drawable.ic_cb_copier)
    CastAction.MOVE -> cbv(castbridge.sender.R.drawable.ic_cb_deplacer_vers_tv)
}

/** One explanation line per way, the same words as « Ouvrir avec CastBridge » (castbridge.core.ux.SendWays). */
private fun subtitle(a: CastAction, item: PlayItem): String = castbridge.core.ux.SendWays.castHint(a, item.kind, item.isWeb)

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
