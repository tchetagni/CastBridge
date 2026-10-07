package castbridge.sender.player

import castbridge.sender.cbv

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.util.LruCache
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import castbridge.core.phone.CastAction
import castbridge.core.phone.MediaKind
import castbridge.core.phone.ResumeBook
import castbridge.sender.fmtTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One MediaStore row of the "Sur le téléphone" library. */
data class LibEntry(val item: PlayItem, val dateAdded: Long, val durationMs: Long, val bucketName: String?)
data class Folder(val id: String, val name: String, val count: Int, val cover: LibEntry)

enum class LibTab(val label: String) { RECENT("Récents"), VIDEO("Vidéos"), MUSIC("Musique"), PHOTO("Photos"), FOLDERS("Dossiers") }

/** What the user allowed: everything, a selection (Android 14 "photos et vidéos sélectionnées"), or nothing. */
enum class Access { FULL, PARTIAL, NONE }

fun mediaAccess(ctx: Context): Access {
    fun g(p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    return when {
        Build.VERSION.SDK_INT < 33 -> if (g(Manifest.permission.READ_EXTERNAL_STORAGE)) Access.FULL else Access.NONE
        g(Manifest.permission.READ_MEDIA_VIDEO) || g(Manifest.permission.READ_MEDIA_IMAGES) || g(Manifest.permission.READ_MEDIA_AUDIO) ->
            if (g(Manifest.permission.READ_MEDIA_VIDEO) && g(Manifest.permission.READ_MEDIA_IMAGES)) Access.FULL
            else if (Build.VERSION.SDK_INT >= 34 && g(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)) Access.PARTIAL else Access.FULL
        Build.VERSION.SDK_INT >= 34 && g(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> Access.PARTIAL
        else -> Access.NONE
    }
}

private fun mediaPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_AUDIO)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

/** "Sur le téléphone": the phone's videos, music and photos (MediaStore), recent first or by folder, with search. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhoneLibraryScreen() {
    val ctx = LocalContext.current
    var access by remember { mutableStateOf(mediaAccess(ctx)) }
    var version by remember { mutableIntStateOf(0) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { access = mediaAccess(ctx); version++ }
    // Permissions can change in the settings; files come and go (a move to the TV deletes one): refresh on both.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) { val a = mediaAccess(ctx); if (a != access) { access = a; version++ } } }
        owner.lifecycle.addObserver(o)
        val obs = object : ContentObserver(Handler(Looper.getMainLooper())) { override fun onChange(selfChange: Boolean) { version++ } }
        runCatching { ctx.contentResolver.registerContentObserver(MediaStore.Files.getContentUri("external"), true, obs) }
        onDispose { owner.lifecycle.removeObserver(o); ctx.contentResolver.unregisterContentObserver(obs) }
    }

    if (access == Access.NONE) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Filled.PermMedia, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text("Vos vidéos, musiques et photos", style = MaterialTheme.typography.titleLarge)
            Text("CastBridge les lit ici et les envoie à la TV. Rien ne quitte le téléphone sans votre choix.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Button({ ask.launch(mediaPermissions()) }) { Text("Autoriser l'accès") }
            TextButton({ openAppSettings(ctx) }) { Text("Réglages de l'application") }
        }
        return
    }

    var tab by rememberSaveableInt(0)
    var query by remember { mutableStateOf("") }
    var folder by remember { mutableStateOf<Folder?>(null) }
    var menuFor by remember { mutableStateOf<LibEntry?>(null) }
    var castFor by remember { mutableStateOf<Pair<LibEntry, CastAction?>?>(null) }
    val t = LibTab.entries[tab]
    val entries by produceState<List<LibEntry>?>(null, t, query, folder, version, access) {
        value = withContext(Dispatchers.IO) { runCatching { MediaQueries.list(ctx, if (folder != null) null else t, folder?.id, query.trim()) }.getOrDefault(emptyList()) }
    }
    val folders by produceState<List<Folder>?>(null, t, version, access, query) {
        value = if (t == LibTab.FOLDERS) withContext(Dispatchers.IO) { runCatching { MediaQueries.folders(ctx, query.trim()) }.getOrDefault(emptyList()) } else null
    }

    Column(Modifier.fillMaxSize()) {
        if (access == Access.PARTIAL) Surface(color = MaterialTheme.colorScheme.primaryContainer) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Accès limité aux photos et vidéos que vous avez choisies.", style = MaterialTheme.typography.bodyMedium)
                Row {
                    TextButton({ ask.launch(mediaPermissions()) }) { Text("Modifier la sélection") }
                    TextButton({ openAppSettings(ctx) }) { Text("Tout autoriser (réglages)") }
                }
            }
        }
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), singleLine = true,
            placeholder = { Text("Rechercher sur le téléphone") }, leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Filled.Close, "Effacer") } })
        if (folder == null) ScrollableTabRow(tab, edgePadding = 8.dp, containerColor = MaterialTheme.colorScheme.background) {
            LibTab.entries.forEachIndexed { i, x -> Tab(tab == i, { tab = i }, text = { Text(x.label, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }) }
        } else Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton({ folder = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Dossiers") }
            Text(folder!!.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        if (t == LibTab.FOLDERS && folder == null) {
            val fs = folders
            if (fs == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            else if (fs.isEmpty()) Empty("Aucun dossier")
            else LazyVerticalGrid(GridCells.Adaptive(150.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp)) {
                items(fs, key = { it.id }) { f ->
                    Column(Modifier.padding(4.dp).clip(RoundedCornerShape(12.dp)).combinedClickable(onClick = { folder = f })) {
                        Thumb(f.cover.item, Modifier.fillMaxWidth().aspectRatio(1.3f))
                        Text(f.name, Modifier.padding(horizontal = 4.dp, vertical = 2.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        Text("${f.count} élément${if (f.count > 1) "s" else ""}", Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            val list = entries
            if (list == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            else if (list.isEmpty()) Empty(if (query.isNotEmpty()) "Rien ne correspond à « $query »" else if (access == Access.PARTIAL) "Aucun élément choisi : touchez « Modifier la sélection »." else "Rien ici")
            else LazyVerticalGrid(GridCells.Adaptive(if (t == LibTab.MUSIC && folder == null) 400.dp else 112.dp), Modifier.fillMaxSize(),
                contentPadding = PaddingValues(6.dp)) {
                items(list, key = { it.item.uri.toString() },
                    span = { e -> if (e.item.kind == MediaKind.AUDIO) GridItemSpan(maxLineSpan) else GridItemSpan(1) }) { e ->
                    Box {
                        val open = { openInPlayer(ctx, e.item) }
                        if (e.item.kind == MediaKind.AUDIO) AudioRow(e, Modifier.combinedClickable(onClick = open, onLongClick = { menuFor = e }))
                        else Tile(e, Modifier.combinedClickable(onClick = open, onLongClick = { menuFor = e }))
                        DropdownMenu(menuFor == e, { menuFor = null }) {
                            DropdownMenuItem({ Text("Ouvrir") }, { menuFor = null; open() }, leadingIcon = { Icon(cbv(castbridge.sender.R.drawable.ic_cb_lecture), null) })
                            DropdownMenuItem({ Text(castbridge.core.ux.SendWays.MENU_PLAY_ON_TV) }, { menuFor = null; castFor = e to null }, leadingIcon = { Icon(cbv(castbridge.sender.R.drawable.ic_cb_caster), null) })
                            DropdownMenuItem({ Text(castbridge.core.ux.SendWay.COPY_AND_PLAY.label + "…") }, { menuFor = null; castFor = e to CastAction.COPY }, leadingIcon = { Icon(cbv(castbridge.sender.R.drawable.ic_cb_copier), null) })
                            DropdownMenuItem({ Text(castbridge.core.ux.SendWay.MOVE.label + "…") }, { menuFor = null; castFor = e to CastAction.MOVE }, leadingIcon = { Icon(cbv(castbridge.sender.R.drawable.ic_cb_deplacer_vers_tv), null) })
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
    castFor?.let { (e, only) ->
        val resume = ResumeTracker.book(ctx).get(ResumeBook.key(e.item.name, e.item.size, e.item.uri.toString()))
        CastSheet(e.item, resume, e.durationMs, only) { castFor = null }
    }
}

@Composable
private fun rememberSaveableInt(init: Int) = androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(init) }

@Composable
private fun Empty(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Tile(e: LibEntry, modifier: Modifier) {
    Column(Modifier.padding(3.dp).clip(RoundedCornerShape(10.dp)).then(modifier)) {
        Box {
            Thumb(e.item, Modifier.fillMaxWidth().aspectRatio(1f))
            if (e.item.kind == MediaKind.VIDEO) {
                Text(if (e.durationMs > 0) fmtTime(e.durationMs) else "vidéo", Modifier.align(Alignment.BottomEnd).padding(4.dp)
                    .background(Color(0xAA000000), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp),
                    color = Color.White, style = MaterialTheme.typography.labelSmall)
                Icon(Icons.Filled.PlayCircleOutline, null, Modifier.align(Alignment.Center).size(28.dp), tint = Color.White.copy(alpha = 0.8f))
            }
        }
        if (e.item.kind == MediaKind.VIDEO) Text(e.item.name, Modifier.padding(horizontal = 2.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun AudioRow(e: LibEntry, modifier: Modifier) {
    ListItem(modifier = modifier, colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Thumb(e.item, Modifier.size(52.dp).clip(RoundedCornerShape(8.dp))) },
        headlineContent = { Text(e.item.name.substringBeforeLast('.'), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(listOfNotNull(e.bucketName, e.durationMs.takeIf { it > 0 }?.let(::fmtTime)).joinToString(" · ")) })
}

private val thumbs = LruCache<String, ImageBitmap>(300)

@Composable
fun Thumb(item: PlayItem, modifier: Modifier) {
    val ctx = LocalContext.current
    val key = item.uri.toString()
    val bmp by produceState(thumbs.get(key), key) {
        if (value == null) value = withContext(Dispatchers.IO) { loadThumb(ctx, item)?.also { thumbs.put(key, it) } }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(when (item.kind) { MediaKind.AUDIO -> Icons.Filled.MusicNote; MediaKind.IMAGE -> Icons.Filled.Image; else -> Icons.Filled.Movie },
            null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Suppress("DEPRECATION")
private fun loadThumb(ctx: Context, item: PlayItem): ImageBitmap? = runCatching {
    val cr = ctx.contentResolver
    if (Build.VERSION.SDK_INT >= 29) cr.loadThumbnail(item.uri, Size(320, 320), null).asImageBitmap()
    else {
        val id = ContentUris.parseId(item.uri)
        when (item.kind) {
            MediaKind.VIDEO -> MediaStore.Video.Thumbnails.getThumbnail(cr, id, MediaStore.Video.Thumbnails.MINI_KIND, null)
            MediaKind.IMAGE -> MediaStore.Images.Thumbnails.getThumbnail(cr, id, MediaStore.Images.Thumbnails.MINI_KIND, null)
            else -> null
        }?.asImageBitmap()
    }
}.getOrNull()

fun openInPlayer(ctx: Context, item: PlayItem) {
    ctx.startActivity(Intent(ctx, PlayerActivity::class.java).setAction(Intent.ACTION_VIEW).setDataAndType(item.uri, item.mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
}

private fun openAppSettings(ctx: Context) {
    runCatching { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))) }
}

/** MediaStore queries of the library (IO thread). */
object MediaQueries {
    private const val LIMIT = 400

    private fun collection(t: LibTab?): Uri = when (t) {
        LibTab.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        LibTab.MUSIC -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        LibTab.PHOTO -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        else -> MediaStore.Files.getContentUri("external")
    }

    private fun typeFilter(t: LibTab?): String? = when (t) {
        LibTab.VIDEO, LibTab.MUSIC, LibTab.PHOTO -> null
        else -> "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO},${MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO},${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE})"
    }

    /** Items of a tab (or of a folder when [bucket] is set), newest first, filtered by name. */
    fun list(ctx: Context, t: LibTab?, bucket: String?, search: String): List<LibEntry> {
        val coll = collection(t)
        val sel = mutableListOf<String>(); val args = mutableListOf<String>()
        typeFilter(t)?.let { sel += it }
        bucket?.let { sel += "bucket_id=?"; args += it }
        if (search.isNotEmpty()) { sel += "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"; args += "%$search%" }
        val proj = mutableListOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE, MediaStore.MediaColumns.DATE_ADDED, "bucket_id", "bucket_display_name")
        val hasDuration = Build.VERSION.SDK_INT >= 29 || t == LibTab.VIDEO || t == LibTab.MUSIC
        if (hasDuration) proj += "duration"
        val out = mutableListOf<LibEntry>()
        ctx.contentResolver.query(coll, proj.toTypedArray(), sel.joinToString(" AND ").ifEmpty { null }, args.toTypedArray(),
            "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { c ->
            while (c.moveToNext() && out.size < LIMIT) {
                val name = c.getString(1) ?: continue
                val mime = c.getString(3)
                val kind = MediaKind.of(mime, name)
                if (kind == MediaKind.OTHER) continue
                val id = c.getLong(0)
                // Files rows are re-addressed through their own collection (thumbnails, deletion requests, grants).
                val base = if (coll == MediaStore.Files.getContentUri("external")) when (kind) {
                    MediaKind.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    MediaKind.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    else -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else coll
                val item = PlayItem(ContentUris.withAppendedId(base, id), name, mime, kind, c.getLong(2), bucketId = c.getString(5))
                out += LibEntry(item, c.getLong(4), if (hasDuration) c.getLong(7) else 0, c.getString(6))
            }
        }
        return out
    }

    fun folders(ctx: Context, search: String): List<Folder> {
        val all = list(ctx, null, null, "").let { if (it.size >= LIMIT) listAll(ctx) else it }
        return all.filter { it.item.bucketId != null }.groupBy { it.item.bucketId!! }
            .map { (id, es) -> Folder(id, es.first().bucketName ?: "Dossier", es.size, es.first()) }
            .filter { search.isEmpty() || it.name.contains(search, ignoreCase = true) }
            .sortedByDescending { it.cover.dateAdded }
    }

    /** Folders need every row, not the 400 newest: a light query (no duration). */
    private fun listAll(ctx: Context): List<LibEntry> {
        val coll = MediaStore.Files.getContentUri("external")
        val out = mutableListOf<LibEntry>()
        ctx.contentResolver.query(coll, arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_ADDED, "bucket_id", "bucket_display_name"), typeFilter(null), null, "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1) ?: continue
                val kind = MediaKind.of(c.getString(2), name)
                val base = when (kind) { MediaKind.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI; MediaKind.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    MediaKind.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI; else -> continue }
                out += LibEntry(PlayItem(ContentUris.withAppendedId(base, c.getLong(0)), name, c.getString(2), kind, bucketId = c.getString(4)), c.getLong(3), 0, c.getString(5))
            }
        }
        return out
    }
}
