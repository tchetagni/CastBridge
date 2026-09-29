package castbridge.sender

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import castbridge.core.tv.LibraryEntry
import castbridge.core.tv.LibraryLogic
import castbridge.core.tv.LibrarySections
import castbridge.core.tv.MediaType
import castbridge.core.tv.TvClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** One file of the TV library as sent by GET /api/library. */
data class TvLibItem(
    override val name: String, override val title: String, val size: Long, override val mtime: Long, val volume: String, val volumeLabel: String,
    val volumeKind: String, val durationMs: Long, override val resumeMs: Long, override val watched: Boolean, override val playedAtMs: Long,
    val hasThumb: Boolean, val playing: Boolean,
) : LibraryEntry {
    override val type: MediaType get() = MediaType.of(name)
}

object TvLibraryParser {
    fun parse(json: String): List<TvLibItem> {
        val a = JSONObject(json).optJSONArray("files") ?: return emptyList()
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            TvLibItem(o.getString("name"), o.optString("title", o.getString("name")), o.optLong("size"), o.optLong("mtime"), o.optString("volume"),
                o.optString("volumeLabel"), o.optString("kind"), o.optLong("durationMs"), o.optLong("resumeMs"), o.optBoolean("watched"),
                o.optLong("playedAt"), o.optBoolean("hasThumb"), o.optBoolean("playing"))
        }
    }
}

/** Thumbnails fetched from /api/thumb on demand, kept in a bounded memory cache (8 MB) for the whole app. */
object TvThumbs {
    private val cache = object : LruCache<String, ImageBitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    private val asked = java.util.Collections.synchronizedSet(HashSet<String>())

    fun key(base: String, i: TvLibItem) = "$base|${i.volume}|${i.name}|${i.size}|${i.mtime}"
    fun cached(k: String): ImageBitmap? = cache.get(k)

    /** Loads a thumbnail (null = not ready yet: the TV starts making it; the library poll will say when it is there). */
    suspend fun load(client: TvClient, i: TvLibItem): ImageBitmap? {
        val k = key(client.base, i)
        cache.get(k)?.let { return it }
        if (!i.hasThumb && !asked.add(k)) return null       // already asked the TV to make it: wait for hasThumb
        return withContext(Dispatchers.IO) {
            val bytes = runCatching { client.thumb(i.name, i.volume) }.getOrNull() ?: return@withContext null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()?.also { cache.put(k, it) }
        }
    }
}

/**
 * "Bibliothèque de la TV": the same sections as on the TV (Reprendre, Récemment ajoutés, Toutes, Autres fichiers), cards with
 * thumbnail, duration, resume bar, "vu" and volume badges. Tap = play (resume or from the start), long press = actions.
 * [onDownload] (optional) adds "Télécharger sur le téléphone".
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TvLibraryDialog(client: TvClient, onDismiss: () -> Unit, onDownload: ((TvLibItem) -> Unit)? = null) {
    var items by remember { mutableStateOf<List<TvLibItem>?>(null) }
    var volumes by remember { mutableStateOf<List<TvVolume>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var menuFor by remember { mutableStateOf<TvLibItem?>(null) }
    var resumeFor by remember { mutableStateOf<TvLibItem?>(null) }
    var renameFor by remember { mutableStateOf<TvLibItem?>(null) }
    var deleteFor by remember { mutableStateOf<TvLibItem?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(client, reload) {
        while (true) {
            val r = withContext(Dispatchers.IO) { runCatching { TvLibraryParser.parse(client.library()) } }
            r.onSuccess { items = it; error = null }.onFailure { error = it.message }
            if (volumes.isEmpty()) withContext(Dispatchers.IO) { runCatching { volumes = TvStorageParser.parse(client.storage()).volumes } }
            if ((r.exceptionOrNull() as? TvClient.HttpError)?.code == 401) break
            delay(5000)
        }
    }
    fun act(label: String, f: TvClient.() -> Unit) = scope.launch {
        val e = withContext(Dispatchers.IO) { runCatching { client.f() }.exceptionOrNull() }
        if (e != null) msg = e.let { "$label : " + ((it as? TvClient.HttpError)?.message?.substringAfter(": ")?.let { b -> TvClient.str(b, "message") ?: TvClient.str(b, "error") } ?: it.message) }
        reload++
    }
    fun play(i: TvLibItem, pos: Long) = act("Lecture") { play(i.name, pos) }.also { msg = "Lecture de « ${i.title} » sur la TV" }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column {
                TopAppBar(title = { Text("Bibliothèque de la TV") },
                    navigationIcon = { IconButton(onDismiss) { Icon(Icons.Filled.ArrowBack, "Retour") } },
                    actions = { IconButton({ reload++ }) { Icon(Icons.Filled.Refresh, "Actualiser") } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface))
                msg?.let { Text(it, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall) }
                error?.let { Text("TV injoignable : $it", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
                val list = items
                if (list == null) { LinearProgressIndicator(Modifier.fillMaxWidth()); return@Column }
                val sections = LibrarySections.build(list)
                if (sections.isEmpty()) Text("Aucun fichier sur la TV.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyVerticalGrid(GridCells.Adaptive(156.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp)) {
                    sections.forEach { s ->
                        item(span = { GridItemSpan(maxLineSpan) }, key = "h:${s.id}") { SectionHeader("${s.title} · ${s.items.size}") }
                        items(s.items, key = { "${s.id}:${it.volume}:${it.name}" }) { i ->
                            LibCard(client, i, Modifier.combinedClickable(
                                onClick = { if (i.type == MediaType.OTHER) menuFor = i else if (i.resumeMs > 0 && !i.watched) resumeFor = i else play(i, 0) },
                                onLongClick = { menuFor = i }))
                        }
                    }
                }
            }
        }
        resumeFor?.let { i ->
            AlertDialog(onDismissRequest = { resumeFor = null }, title = { Text(i.title) },
                confirmButton = { TextButton({ resumeFor = null; play(i, i.resumeMs) }) { Text("Reprendre à ${LibraryLogic.clock(i.resumeMs)}") } },
                dismissButton = { TextButton({ resumeFor = null; play(i, 0) }) { Text("Depuis le début") } })
        }
        menuFor?.let { i ->
            ModalBottomSheet(onDismissRequest = { menuFor = null }) {
                Column(Modifier.padding(bottom = 24.dp)) {
                    Text(i.title, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleMedium)
                    Text("${formatSize(i.size)} · ${i.volumeLabel}", Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
                    @Composable fun row(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, f: () -> Unit) =
                        ListItem(headlineContent = { Text(text) }, leadingContent = { Icon(icon, null) },
                            modifier = Modifier.combinedClickable(onClick = { menuFor = null; f() }),
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent))
                    if (i.type != MediaType.OTHER) {
                        if (i.resumeMs > 0 && !i.watched) row(Icons.Filled.PlayArrow, "Reprendre à ${LibraryLogic.clock(i.resumeMs)}") { play(i, i.resumeMs) }
                        row(Icons.Filled.Replay, "Lire depuis le début") { play(i, 0) }
                        row(if (i.watched) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (i.watched) "Marquer comme non vu" else "Marquer comme vu") {
                            act("Marquage") { setWatched(i.name, !i.watched) }
                        }
                    }
                    onDownload?.let { d -> row(Icons.Filled.Download, "Télécharger sur le téléphone") { d(i) } }
                    volumes.filter { it.present && it.writable && it.id != i.volume }.forEach { v ->
                        row(Icons.Filled.DriveFileMove, "Déplacer vers ${v.label}") { act("Déplacement") { moveFile(i.name, v.id) }.also { msg = "Déplacement vers ${v.label} lancé" } }
                    }
                    row(Icons.Filled.Edit, "Renommer") { renameFor = i }
                    row(Icons.Filled.Delete, "Supprimer") { deleteFor = i }
                }
            }
        }
        renameFor?.let { i ->
            var to by remember(i) { mutableStateOf(i.name) }
            AlertDialog(onDismissRequest = { renameFor = null }, title = { Text("Renommer") },
                text = { OutlinedTextField(to, { to = it }, singleLine = true) },
                confirmButton = { TextButton({ renameFor = null; if (to.isNotBlank() && to != i.name) act("Renommage") { rename(i.name, to.trim()) } }) { Text("Renommer") } },
                dismissButton = { TextButton({ renameFor = null }) { Text("Annuler") } })
        }
        deleteFor?.let { i ->
            AlertDialog(onDismissRequest = { deleteFor = null }, title = { Text("Supprimer « ${i.title} » ?") },
                text = { Text("${i.name} (${formatSize(i.size)}, ${i.volumeLabel}) sera effacé de la TV.") },
                confirmButton = { TextButton({ deleteFor = null; act("Suppression") { delete(i.name, i.volume) } }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) } },
                dismissButton = { TextButton({ deleteFor = null }) { Text("Annuler") } })
        }
    }
}

@Composable
private fun LibCard(client: TvClient, i: TvLibItem, modifier: Modifier) {
    val k = TvThumbs.key(client.base, i)
    var img by remember(k) { mutableStateOf(TvThumbs.cached(k)) }
    LaunchedEffect(k, i.hasThumb) { if (img == null && i.type != MediaType.OTHER) img = TvThumbs.load(client, i) }
    val cs = MaterialTheme.colorScheme
    Column(modifier.padding(6.dp).clip(RoundedCornerShape(8.dp)).background(cs.surface)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(cs.surfaceVariant), contentAlignment = Alignment.Center) {
            img?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } ?: Icon(
                when (i.type) { MediaType.VIDEO -> Icons.Filled.Movie; MediaType.AUDIO -> Icons.Filled.MusicNote; MediaType.OTHER -> Icons.Filled.Description },
                null, Modifier.size(36.dp), tint = cs.onSurfaceVariant)
            Badge(if (i.volumeKind == "internal") "Interne" else if (i.volumeKind == "saf") "Dossier" else "Clé", cs.primaryContainer, Modifier.align(Alignment.TopStart))
            if (i.watched) Badge("VU", Color(0xFF1B5E20), Modifier.align(Alignment.TopEnd))
            if (i.playing) Badge("▶ en lecture", cs.primary.copy(alpha = 0.85f), Modifier.align(Alignment.Center))
            if (i.durationMs > 0) Badge(LibraryLogic.clock(i.durationMs), Color.Black.copy(alpha = 0.7f), Modifier.align(Alignment.BottomEnd).padding(bottom = 4.dp))
            val p = if (i.resumeMs > 0 && !i.watched) LibraryLogic.progress(i.resumeMs, i.durationMs) else 0f
            if (p > 0f) LinearProgressIndicator({ p }, Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomCenter))
        }
        Text(i.title, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Text(formatSize(i.size), Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
    }
}

@Composable
private fun Badge(text: String, color: Color, modifier: Modifier) {
    Text(text, modifier.padding(4.dp).clip(RoundedCornerShape(4.dp)).background(color).padding(horizontal = 6.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall, color = Color.White)
}

/** Entry points of the "CastBridge TV" tab added by this branch (library, file exchange...), one row of buttons. */
@Composable
fun TvTools(client: TvClient) {
    var library by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = { library = true }) { Icon(Icons.Filled.VideoLibrary, null); Spacer(Modifier.width(6.dp)); Text("Bibliothèque de la TV") }
    }
    if (library) TvLibraryDialog(client, onDismiss = { library = false })
}
