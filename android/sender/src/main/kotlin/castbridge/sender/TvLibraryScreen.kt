@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package castbridge.sender

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
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
import castbridge.core.tv.BulkDelete
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
    /** Virtual folder on the TV ("" = the root). */
    override val folder: String = "",
) : LibraryEntry {
    override val type: MediaType get() = MediaType.of(name)
}

/** Le fichier tel que la suppression en masse le voit (logique pure dans :core, castbridge.core.tv.BulkDelete). */
fun TvLibItem.bulk() = BulkDelete.Entry(name, volume, size, mtime, folder, watched, playedAtMs, playing)
private fun TvLibItem.key() = BulkDelete.key(volume, name)

object TvLibraryParser {
    fun parse(json: String): List<TvLibItem> {
        val a = JSONObject(json).optJSONArray("files") ?: return emptyList()
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            TvLibItem(o.getString("name"), o.optString("title", o.getString("name")), o.optLong("size"), o.optLong("mtime"), o.optString("volume"),
                o.optString("volumeLabel"), o.optString("kind"), o.optLong("durationMs"), o.optLong("resumeMs"), o.optBoolean("watched"),
                o.optLong("playedAt"), o.optBoolean("hasThumb"), o.optBoolean("playing"), o.optString("folder"))
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
    var showAssistant by remember { mutableStateOf(false) }       // « Ranger ma bibliothèque » (castbridge.sender.agent)
    var showOrganize by remember { mutableStateOf(false) }        // rangement réel dans des dossiers, fait par CastBridge-TV (docs/STORAGE.md)
    // ---- suppression en masse (décision du 2026-10-27) : cocher, UNE confirmation, suppression définitive faite par la TV
    var selecting by remember { mutableStateOf(false) }
    var bySize by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var bulkMenu by remember { mutableStateOf(false) }
    var folderPick by remember { mutableStateOf(false) }
    var monthsPick by remember { mutableStateOf(false) }
    var confirmBulk by remember { mutableStateOf(false) }
    var bulkProgress by remember { mutableStateOf<String?>(null) }
    var bulkResult by remember { mutableStateOf<String?>(null) }
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
    fun leaveSelection() { selecting = false; bySize = false; selected = emptySet() }
    fun startSelection(pre: Set<String>, sizeOrder: Boolean = false) {
        if (pre.isEmpty() && !sizeOrder) { msg = "Aucun fichier ne correspond."; return }
        selecting = true; bySize = sizeOrder; selected = pre
    }
    fun toggle(i: TvLibItem) { selected = if (i.key() in selected) selected - i.key() else selected + i.key() }
    fun bulkWhy(e: Throwable): String {
        val body = (e as? TvClient.HttpError)?.message?.substringAfter(": ")
        return when {
            (e as? TvClient.HttpError)?.code == 409 -> "Une suppression est déjà en cours sur la TV."
            body != null -> TvClient.str(body, "message") ?: TvClient.str(body, "error") ?: e.message ?: "erreur"
            else -> e.message ?: "erreur"
        }
    }
    /** Le lot part sur la TV (`POST /api/delete/batch`) et la progression est lue ; une TV plus ancienne (404) : fichier par fichier avec `/api/delete`, même affichage. */
    fun runBulk(chosen: List<TvLibItem>) = scope.launch {
        val total = chosen.size
        bulkProgress = BulkDelete.progressText(0, total)
        val text = withContext(Dispatchers.IO) {
            runCatching {
                val job = try { JSONObject(client.deleteBatch(chosen.map { BulkDelete.Target(it.name, it.volume) })).getString("job") }
                    catch (e: TvClient.HttpError) { if (e.code == 404) null else throw e }
                if (job != null) {
                    var out: String? = null
                    while (out == null) {
                        val st = JSONObject(client.deleteBatchStatus(job))
                        val done = st.optInt("done")
                        bulkProgress = BulkDelete.progressText(done, total)
                        if (st.optBoolean("finished")) {
                            val a = st.optJSONArray("skipped")
                            val sk = (0 until (a?.length() ?: 0)).map { k -> a!!.getJSONObject(k).let { BulkDelete.Skip(it.optString("name"), it.optString("reason")) } }
                            out = BulkDelete.resultText(done - sk.size, st.optLong("freed"), sk)
                        } else Thread.sleep(400)
                    }
                    out
                } else {
                    var freed = 0L; var deleted = 0; val sk = ArrayList<BulkDelete.Skip>()
                    chosen.forEachIndexed { k, i ->
                        try { client.delete(i.name, i.volume); freed += i.size; deleted++ }
                        catch (e: TvClient.HttpError) { sk += BulkDelete.Skip(i.name, when (e.code) { 409 -> "moving"; 404 -> "absent"; else -> "error" }) }
                        bulkProgress = BulkDelete.progressText(k + 1, total)
                    }
                    BulkDelete.resultText(deleted, freed, sk)
                }
            }.getOrElse { "Suppression impossible : ${bulkWhy(it)}" }
        }
        bulkProgress = null; bulkResult = text
        leaveSelection(); reload++
    }
    fun play(i: TvLibItem, pos: Long) = act("Lecture") { play(i.name, pos) }.also { msg = "Lecture de « ${i.title} » sur la TV" }

    Dialog(onDismissRequest = { if (bulkProgress != null) Unit else if (selecting) leaveSelection() else onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column {
                if (selecting) {
                    val all = items.orEmpty().map { it.bulk() }
                    TopAppBar(title = { Text(BulkDelete.headline(BulkDelete.count(all, selected), BulkDelete.totalSize(all, selected)), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        navigationIcon = { IconButton({ leaveSelection() }) { Icon(Icons.Filled.Close, "Annuler la sélection") } },
                        actions = {
                            val everything = all.isNotEmpty() && selected.size >= all.size
                            IconButton({ selected = if (everything) emptySet() else all.map { it.key }.toSet() }) {
                                Icon(if (everything) Icons.Filled.Deselect else Icons.Filled.SelectAll, if (everything) "Tout désélectionner" else "Tout sélectionner")
                            }
                            IconButton({ if (selected.isEmpty()) msg = "Rien n'est sélectionné." else confirmBulk = true }) {
                                Icon(Icons.Filled.Delete, "Supprimer définitivement", tint = MaterialTheme.colorScheme.error)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface))
                } else
                TopAppBar(title = { Text("Bibliothèque de la TV", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                    navigationIcon = { IconButton(onDismiss) { Icon(Icons.Filled.ArrowBack, "Retour") } },
                    actions = {
                        // R-23: 2 visible actions at most (AppBarBudget); the « ranger » actions and « Supprimer en masse » go in the « ⋮ » menu
                        var more by remember { mutableStateOf(false) }
                        IconButton({ reload++ }) { Icon(Icons.Filled.Refresh, castbridge.core.ux.UxLabels.REFRESH) }
                        Box {
                            IconButton({ more = true }) { Icon(Icons.Filled.MoreVert, castbridge.core.ux.UxLabels.MORE) }
                            DropdownMenu(more, { more = false }) {
                                DropdownMenuItem(text = { Text(castbridge.core.ux.UxLabels.ASSISTANT) }, leadingIcon = { Icon(Icons.Filled.AutoAwesome, null) }, onClick = { more = false; showAssistant = true })
                                DropdownMenuItem(text = { Text(castbridge.core.ux.UxLabels.ORGANIZE) }, leadingIcon = { Icon(Icons.Filled.CreateNewFolder, null) }, onClick = { more = false; showOrganize = true })
                                DropdownMenuItem(text = { Text("Supprimer en masse") }, leadingIcon = { Icon(Icons.Filled.DeleteSweep, null) }, onClick = { more = false; bulkMenu = true })
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface))
                castbridge.sender.agent.AssistantBanner(client) { showAssistant = true }
                msg?.let { Text(it, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall) }
                error?.let { Text("TV injoignable : $it", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
                val list = items
                if (list == null) { LinearProgressIndicator(Modifier.fillMaxWidth()); return@Column }
                val sections = if (selecting && bySize) {
                    val sorted = list.sortedWith(compareBy<TvLibItem>({ -it.size }, { it.name.lowercase() }))
                    listOf(LibrarySections.Section("size", "Les plus gros d'abord", sorted))
                } else LibrarySections.build(list)
                if (sections.isEmpty()) Text("Aucun fichier sur la TV.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyVerticalGrid(GridCells.Adaptive(156.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp)) {
                    sections.forEach { s ->
                        item(span = { GridItemSpan(maxLineSpan) }, key = "h:${s.id}") { SectionHeader("${s.title} · ${s.items.size}") }
                        items(s.items, key = { "${s.id}:${it.volume}:${it.name}" }) { i ->
                            LibCard(client, i, Modifier.combinedClickable(
                                onClick = { if (selecting) toggle(i) else if (i.type == MediaType.OTHER) menuFor = i else if (i.resumeMs > 0 && !i.watched) resumeFor = i else play(i, 0) },
                                // appui long : le menu du fichier, qui offre « Sélectionner plusieurs » (sélection ouverte : coche / décoche)
                                onLongClick = { if (selecting) toggle(i) else menuFor = i }), if (selecting) i.key() in selected else null)
                        }
                    }
                }
            }
        }
        if (showAssistant) castbridge.sender.agent.LibraryAssistantDialog(client) { showAssistant = false; reload++ }
        if (showOrganize) OrganizeOnTvDialog(client) { showOrganize = false; reload++ }
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
                        if (i.resumeMs > 0 && !i.watched) row(cbv(R.drawable.ic_cb_lecture), "Reprendre à ${LibraryLogic.clock(i.resumeMs)}") { play(i, i.resumeMs) }
                        row(Icons.Filled.Replay, "Lire depuis le début") { play(i, 0) }
                        row(if (i.watched) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (i.watched) "Marquer comme non vu" else "Marquer comme vu") {
                            act("Marquage") { setWatched(i.name, !i.watched) }
                        }
                    }
                    onDownload?.let { d -> row(cbv(R.drawable.ic_cb_telechargements), "Télécharger sur le téléphone") { d(i) } }
                    volumes.filter { it.present && it.writable && it.id != i.volume }.forEach { v ->
                        row(cbv(R.drawable.ic_cb_deplacer_vers_tv), "Déplacer vers ${v.label}") { act("Déplacement") { moveFile(i.name, v.id) }.also { msg = "Déplacement vers ${v.label} lancé" } }
                    }
                    row(Icons.Filled.Checklist, "Sélectionner plusieurs") { startSelection(setOf(i.key())) }
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
        if (bulkMenu) AlertDialog(onDismissRequest = { bulkMenu = false }, title = { Text("Supprimer en masse") },
            text = {
                Column {
                    val e = items.orEmpty().map { it.bulk() }
                    @Composable fun entry(text: String, f: () -> Unit) = TextButton({ bulkMenu = false; f() }, Modifier.fillMaxWidth()) { Text(text, Modifier.fillMaxWidth()) }
                    Text("Ces choix cochent des fichiers ; vous pouvez en décocher avant de confirmer.", style = MaterialTheme.typography.bodySmall)
                    entry("Un dossier…") { folderPick = true }
                    entry("Vidéos déjà vues") { startSelection(BulkDelete.watched(e)) }
                    entry("Non lues depuis…") { monthsPick = true }
                    entry("Les plus gros d'abord") { startSelection(emptySet(), sizeOrder = true) }
                }
            },
            confirmButton = {}, dismissButton = { TextButton({ bulkMenu = false }) { Text("Fermer") } })
        if (folderPick) {
            val fs = BulkDelete.folders(items.orEmpty().map { it.bulk() })
            AlertDialog(onDismissRequest = { folderPick = false }, title = { Text("Quel dossier ?") },
                text = {
                    if (fs.isEmpty()) Text("Aucun dossier dans la bibliothèque.")
                    else Column(Modifier.heightIn(max = 360.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                        for (f in fs) {
                            ListItem(headlineContent = { Text(f.folder) }, supportingContent = { Text("${BulkDelete.files(f.count)} · ${BulkDelete.size(f.bytes)}") },
                                modifier = Modifier.combinedClickable(onClick = { folderPick = false; startSelection(BulkDelete.inFolder(items.orEmpty().map { it.bulk() }, f.folder)) }))
                        }
                    }
                },
                confirmButton = {}, dismissButton = { TextButton({ folderPick = false }) { Text("Annuler") } })
        }
        if (monthsPick) AlertDialog(onDismissRequest = { monthsPick = false }, title = { Text("Non lues depuis…") },
            text = {
                Column {
                    for (m in listOf(1, 3, 6, 12)) TextButton({
                        monthsPick = false
                        startSelection(BulkDelete.notPlayedSince(items.orEmpty().map { it.bulk() }, BulkDelete.monthsToDays(m), System.currentTimeMillis()))
                    }, Modifier.fillMaxWidth()) { Text(if (m == 12) "12 mois (1 an)" else "$m mois", Modifier.fillMaxWidth()) }
                }
            },
            confirmButton = {}, dismissButton = { TextButton({ monthsPick = false }) { Text("Annuler") } })
        if (confirmBulk) {
            val all = items.orEmpty().map { it.bulk() }
            val chosen = items.orEmpty().filter { it.key() in selected }
            AlertDialog(onDismissRequest = { confirmBulk = false }, title = { Text("Suppression définitive") },
                text = { Text(BulkDelete.confirmText(BulkDelete.count(all, selected), BulkDelete.totalSize(all, selected))) },
                // le choix sûr est à gauche, en premier : « Annuler » ; le geste destructeur est en rouge
                dismissButton = { TextButton({ confirmBulk = false }) { Text("Annuler") } },
                confirmButton = { TextButton({ confirmBulk = false; runBulk(chosen) }) { Text("Supprimer définitivement", color = MaterialTheme.colorScheme.error) } })
        }
        bulkProgress?.let { p ->
            AlertDialog(onDismissRequest = {}, title = { Text("Suppression définitive") },
                text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(p); LinearProgressIndicator(Modifier.fillMaxWidth()) } },
                confirmButton = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false))
        }
        bulkResult?.let { r ->
            AlertDialog(onDismissRequest = { bulkResult = null }, title = { Text("Suppression") }, text = { Text(r) },
                confirmButton = { TextButton({ bulkResult = null }) { Text("OK") } })
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
private fun LibCard(client: TvClient, i: TvLibItem, modifier: Modifier, check: Boolean? = null) {
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
            if (i.watched) Badge("VU", Color(0xE61C7C53), Modifier.align(Alignment.TopEnd))
            if (i.playing) Badge("▶ en lecture", cs.primary.copy(alpha = 0.85f), Modifier.align(Alignment.Center))
            if (i.durationMs > 0) Badge(LibraryLogic.clock(i.durationMs), Color.Black.copy(alpha = 0.7f), Modifier.align(Alignment.BottomEnd).padding(bottom = 4.dp))
            val p = if (i.resumeMs > 0 && !i.watched) LibraryLogic.progress(i.resumeMs, i.durationMs) else 0f
            if (p > 0f) LinearProgressIndicator({ p }, Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomCenter))
            if (check != null) Icon(if (check) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, if (check) "Sélectionné" else "Non sélectionné",
                Modifier.align(Alignment.BottomStart).padding(6.dp).size(28.dp).background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
                tint = if (check) cs.primary else Color.White)
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
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var library by remember { mutableStateOf(false) }
    var transfer by remember { mutableStateOf(false) }
    FlowRow(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FilledTonalButton(onClick = { library = true }) { Icon(cbv(R.drawable.ic_cb_bibliotheque), null); Spacer(Modifier.width(6.dp)); Text("Bibliothèque") }
        FilledTonalButton(onClick = { transfer = true }) { Icon(Icons.Filled.SwapVert, null); Spacer(Modifier.width(6.dp)); Text("Échange de fichiers") }
    }
    if (library) TvLibraryDialog(client, onDismiss = { library = false },
        onDownload = { i -> DownloadService.start(ctx, client.base, client.pin, i.name, i.size); library = false; transfer = true })
    if (transfer) TvTransferDialog(client, onDismiss = { transfer = false })
}


/**
 * « Ranger ma bibliothèque » (rangement réel): shows what CastBridge-TV would file where (dry run), and files only after the user says yes.
 * Renames files into category folders on the same drive; nothing is deleted; what the parental control protects is never touched.
 */
@Composable
private fun OrganizeOnTvDialog(client: TvClient, onClose: () -> Unit) {
    var plan by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun why(e: Throwable) = if ((e as? TvClient.HttpError)?.code == 404) "Cette TV est trop ancienne : mettez CastBridge-TV à jour."
        else (e as? TvClient.HttpError)?.message?.substringAfter(": ")?.let { TvClient.str(it, "message") ?: TvClient.str(it, "error") } ?: e.message ?: "erreur"
    LaunchedEffect(client) {
        withContext(Dispatchers.IO) { runCatching { JSONObject(client.organizePlan()) } }.onSuccess { plan = it }.onFailure { error = why(it) }
    }
    val p = plan
    val count = p?.optInt("count") ?: 0
    AlertDialog(onDismissRequest = { if (!busy) onClose() }, title = { Text("Ranger dans des dossiers") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when {
                    result != null -> Text(result!!)
                    error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                    p == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    else -> {
                        Text(p.optString("summary"))
                        val moves = p.optJSONArray("moves")
                        for (k in 0 until minOf(6, moves?.length() ?: 0)) moves!!.getJSONObject(k).let { m ->
                            Text("${m.optString("from")}  ->  ${m.optString("folder")}/${m.optString("to")}", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        if (count > 6) Text("… et ${count - 6} autres", style = MaterialTheme.typography.bodySmall)
                        if (p.optInt("protected") > 0) Text("${p.optInt("protected")} fichier(s) protégé(s) par le contrôle parental : non touchés.", style = MaterialTheme.typography.bodySmall)
                        if (p.optInt("skippedCount") > 0) Text("${p.optInt("skippedCount")} fichier(s) laissés en place (en cours d'utilisation, installateurs, trop gros pour la clé…).", style = MaterialTheme.typography.bodySmall)
                        if (!p.optBoolean("canApply", true)) Text("Un profil enfant est actif sur la TV : le rangement est refusé.", color = MaterialTheme.colorScheme.error)
                        Text("Les fichiers sont seulement déplacés sur la même clé ou mémoire, rien n'est supprimé.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            if (result == null && p != null && count > 0 && p.optBoolean("canApply", true))
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        var moved = 0; var failed = 0
                        val r = withContext(Dispatchers.IO) {
                            runCatching {
                                var left = count
                                while (left > 0) {
                                    val a = JSONObject(client.organizeApply(500))
                                    moved += a.optInt("moved"); failed += a.optInt("failed"); left = a.optInt("remaining")
                                    if (a.optInt("moved") == 0) break
                                }
                            }
                        }
                        busy = false
                        result = r.exceptionOrNull()?.let { "Rangement interrompu : ${why(it)} ($moved fichier(s) rangé(s))." }
                            ?: "$moved fichier(s) rangé(s)" + if (failed > 0) ", $failed laissé(s) en place." else "."
                    }
                }) { Text("Ranger") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text(if (result != null) "Fermer" else "Annuler") } })
}
