package castbridge.sender.player

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.IntentCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import castbridge.core.phone.CastSource
import castbridge.core.phone.FolderPlaylist
import castbridge.core.phone.MediaKind
import castbridge.core.phone.StreamType
import castbridge.core.phone.SubtitleTypes
import castbridge.core.tv.SubtitleFinder

/** One file (or link) the phone player shows. */
data class PlayItem(
    val uri: Uri,
    val name: String,
    val mime: String?,
    val kind: MediaKind,
    val size: Long = -1,
    /** External subtitle files found next to it or chosen by the user. */
    val subtitles: List<Uri> = emptyList(),
    /** MediaStore folder, used to list the neighbours ("suivant / précédent"). */
    val bucketId: String? = null,
    /** Set when the item is the TV's own copy (/stream/), after a move: not resumable locally, not castable again live. */
    val fromTv: Boolean = false,
) {
    val isWeb get() = uri.scheme == "http" || uri.scheme == "https"
    val castSource get() = CastSource(kind, uri.scheme.orEmpty(), uri.authority, size)

    fun toMediaItem(ctx: Context): MediaItem {
        val subs = subtitles.mapIndexedNotNull { i, s ->
            val n = nameOf(ctx, s) ?: s.lastPathSegment.orEmpty()
            val mime = SubtitleTypes.mimeOf(n) ?: return@mapIndexedNotNull null
            MediaItem.SubtitleConfiguration.Builder(s).setMimeType(mime).setLabel(n)
                .setLanguage(SubtitleTypes.language(name, n)).setSelectionFlags(if (i == 0) androidx.media3.common.C.SELECTION_FLAG_DEFAULT else 0).build()
        }
        val b = MediaItem.Builder().setUri(uri).setMediaId(uri.toString()).setSubtitleConfigurations(subs)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(name.substringBeforeLast('.').ifEmpty { name }).setDisplayTitle(name)
                .setIsPlayable(true).setIsBrowsable(false)
                .setMediaType(if (kind == MediaKind.AUDIO) MediaMetadata.MEDIA_TYPE_MUSIC else MediaMetadata.MEDIA_TYPE_VIDEO)
                .setExtras(ResumeTracker.extras(name, size, kind.name, noResume = fromTv)).build())
        if (isWeb) when (StreamType.of(uri.toString(), mime)) {
            StreamType.HLS -> b.setMimeType(MimeTypes.APPLICATION_M3U8)
            StreamType.DASH -> b.setMimeType(MimeTypes.APPLICATION_MPD)
            StreamType.PROGRESSIVE -> {}
        }
        return b.build()
    }
}

/** Reading what other apps hand to the player ("Ouvrir avec", "Partager") and what the phone holds (MediaStore). */
object Media {
    /** Items from a VIEW / SEND / SEND_MULTIPLE intent, in the order given. Empty = nothing playable. */
    fun fromIntent(ctx: Context, intent: Intent): List<PlayItem> {
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                ?: intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.takeIf { it.startsWith("http://") || it.startsWith("https://") }?.let(Uri::parse))
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> listOfNotNull(intent.data)
        }
        val single = uris.size == 1
        return uris.mapNotNull { u -> describe(ctx, u, if (single) intent.type else null, intent.getStringExtra(EXTRA_TITLE)) }
            .filter { it.kind != MediaKind.OTHER }
    }

    const val EXTRA_TITLE = "castbridge.title"
    const val EXTRA_POS = "castbridge.pos"
    const val EXTRA_CAST = "castbridge.cast"

    fun describe(ctx: Context, uri: Uri, typeHint: String?, title: String? = null): PlayItem? {
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme !in setOf("content", "file", "http", "https")) return null
        var name: String? = title; var size = -1L; var bucket: String? = null
        if (scheme == "content") runCatching {
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { if (name == null) name = c.getString(it) }
                    c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { if (!c.isNull(it)) size = c.getLong(it) }
                    c.getColumnIndex("bucket_id").takeIf { it >= 0 }?.let { bucket = c.getString(it) }
                }
            }
        }
        if (scheme == "file") uri.path?.let { java.io.File(it) }?.let { f -> if (name == null) name = f.name; if (f.length() > 0) size = f.length() }
        val n = name ?: uri.lastPathSegment?.substringAfterLast('/') ?: "média"
        val mime = typeHint?.takeIf { it != "*/*" && it != "application/octet-stream" }
            ?: runCatching { ctx.contentResolver.getType(uri) }.getOrNull()
        var kind = MediaKind.of(mime, n)
        if (kind == MediaKind.OTHER && (scheme == "http" || scheme == "https")) kind = MediaKind.VIDEO   // a link opened with the player: try it
        return PlayItem(uri, n, mime, kind, size, bucketId = bucket)
    }

    fun hasMediaPermission(ctx: Context): Boolean {
        fun g(p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
        return if (Build.VERSION.SDK_INT >= 33) g(Manifest.permission.READ_MEDIA_VIDEO) || g(Manifest.permission.READ_MEDIA_AUDIO) ||
            g(Manifest.permission.READ_MEDIA_IMAGES) || (Build.VERSION.SDK_INT >= 34 && g(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED))
        else g(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /** The MediaStore row of a content URI, from itself (Gallery) or through its document id (Files) or its name + size. */
    private fun mediaStoreRow(ctx: Context, item: PlayItem): Pair<Uri, String?>? {
        val u = item.uri
        if (u.authority == MediaStore.AUTHORITY) return u to item.bucketId
        if (Build.VERSION.SDK_INT >= 29 && DocumentsContract.isDocumentUri(ctx, u))
            runCatching { MediaStore.getMediaUri(ctx, u) }.getOrNull()?.let { m -> return m to bucketOf(ctx, m) }
        if (item.size <= 0) return null
        // Last resort: the same name and size in the MediaStore (WhatsApp, file managers with their own provider...).
        val files = MediaStore.Files.getContentUri("external")
        return runCatching {
            ctx.contentResolver.query(files, arrayOf(MediaStore.MediaColumns._ID, "bucket_id"),
                "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.SIZE}=?", arrayOf(item.name, item.size.toString()), null)?.use { c ->
                if (c.moveToFirst()) ContentUris.withAppendedId(files, c.getLong(0)) to c.getString(1) else null
            }
        }.getOrNull()
    }

    private fun bucketOf(ctx: Context, m: Uri): String? = runCatching {
        ctx.contentResolver.query(m, arrayOf("bucket_id"), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

    private fun collection(kind: MediaKind): Uri = when (kind) {
        MediaKind.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        MediaKind.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        else -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    }

    /**
     * The folder playlist around [item] (same kind, natural order), with the subtitle files found next to each video.
     * Without the media permission (or for a web link) only [item] itself.
     */
    fun folder(ctx: Context, item: PlayItem): Pair<List<PlayItem>, Int> {
        if (item.isWeb || item.fromTv || !hasMediaPermission(ctx)) return listOf(withSubtitles(ctx, item, null)) to 0
        val (row, bucket) = mediaStoreRow(ctx, item) ?: return listOf(item) to 0
        val bucketId = bucket ?: return listOf(item) to 0
        val coll = collection(item.kind)
        val siblings = mutableListOf<PlayItem>()
        runCatching {
            ctx.contentResolver.query(coll, arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.MIME_TYPE), "bucket_id=?", arrayOf(bucketId), null)?.use { c ->
                while (c.moveToNext()) {
                    val u = ContentUris.withAppendedId(coll, c.getLong(0))
                    val n = c.getString(1) ?: continue
                    siblings += PlayItem(u, n, c.getString(3), MediaKind.of(c.getString(3), n), c.getLong(2), bucketId = bucketId)
                }
            }
        }
        val rowId = row.lastPathSegment
        // The current file keeps the URI it was opened with (the permission granted by the other app is on that one).
        val current = FolderPlaylist.Entry(item.uri.toString(), item.name, item.kind)
        val entries = siblings.filter { it.uri.lastPathSegment != rowId }.map { FolderPlaylist.Entry(it.uri.toString(), it.name, it.kind) }
        val (order, idx) = FolderPlaylist.build(entries, current)
        val byId = siblings.associateBy { it.uri.toString() } + (item.uri.toString() to item.copy(bucketId = bucketId))
        val subs = if (item.kind == MediaKind.VIDEO) subtitlesIn(ctx, bucketId) else emptyMap()
        return order.mapNotNull { e -> byId[e.id]?.let { withSubtitles(ctx, it, subs) } } to idx
    }

    /**
     * Subtitle files of a folder. Android 11+ indexes .srt/.ass/.vtt as MEDIA_TYPE_SUBTITLE (readable with the video
     * permission on most phones); older ones list them as plain files. Name -> URI.
     */
    private fun subtitlesIn(ctx: Context, bucketId: String): Map<String, Uri> = runCatching {
        val files = MediaStore.Files.getContentUri("external")
        val out = LinkedHashMap<String, Uri>()
        ctx.contentResolver.query(files, arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
            "bucket_id=? AND (${MediaStore.MediaColumns.DISPLAY_NAME} LIKE '%.srt' OR ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE '%.ass' OR " +
                "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE '%.ssa' OR ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE '%.vtt')", arrayOf(bucketId), null)?.use { c ->
            while (c.moveToNext()) c.getString(1)?.let { out[it] = ContentUris.withAppendedId(files, c.getLong(0)) }
        }
        out
    }.getOrDefault(emptyMap())

    private fun withSubtitles(ctx: Context, item: PlayItem, subs: Map<String, Uri>?): PlayItem {
        if (item.kind != MediaKind.VIDEO) return item
        val found = subs?.let { m -> SubtitleFinder.find(item.name, m.keys).mapNotNull { m[it] } }.orEmpty()
            .ifEmpty { fileSiblingSubs(item) }
        return if (found.isEmpty()) item else item.copy(subtitles = (item.subtitles + found).distinct())
    }

    /** file:// videos (old file managers): look at the directory itself. */
    private fun fileSiblingSubs(item: PlayItem): List<Uri> {
        if (item.uri.scheme != "file") return emptyList()
        val f = item.uri.path?.let { java.io.File(it) } ?: return emptyList()
        val names = f.parentFile?.list()?.toList() ?: return emptyList()
        return SubtitleFinder.find(f.name, names).filter { SubtitleTypes.mimeOf(it) != null }.map { Uri.fromFile(java.io.File(f.parentFile, it)) }
    }
}

fun nameOf(ctx: Context, uri: Uri): String? = if (uri.scheme == "content") runCatching {
    ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
}.getOrNull() else uri.lastPathSegment

fun isReadable(ctx: Context, uri: Uri): Boolean = when (uri.scheme) {
    "http", "https" -> true
    else -> runCatching { ctx.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false }.getOrDefault(false)
}
