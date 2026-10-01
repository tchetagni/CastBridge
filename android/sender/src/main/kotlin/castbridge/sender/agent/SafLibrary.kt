package castbridge.sender.agent

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.DocumentsContract
import castbridge.core.library.agent.DocEntry
import castbridge.core.library.agent.DocProvider
import castbridge.core.library.agent.DocTreeLibrary
import castbridge.core.library.agent.DurationCache
import castbridge.core.library.agent.LibrarySnapshot
import castbridge.core.library.agent.VolumeInfo
import castbridge.core.library.agent.WalkProgress

/**
 * The phone's own files, reached ONLY through a folder the user picked with the system picker (Storage Access Framework):
 * no storage permission is requested, and nothing outside that folder can be read or changed. Folders are real here.
 *
 * All the decisions (walking, collisions, trash, restore, undo, caches) are in core [DocTreeLibrary] and tested on the JVM with a simulated
 * provider; this class and [AndroidDocProvider] only translate to `DocumentsContract`.
 */
class SafLibrary(ctx: Context, tree: Uri, durations: DurationCache = DurationCache.NONE) {
    private val lib = DocTreeLibrary(AndroidDocProvider(ctx.applicationContext, tree), durations)

    fun snapshot(maxFiles: Int = 20_000, onProgress: (WalkProgress) -> Unit = {}, cancelled: () -> Boolean = { false }): LibrarySnapshot =
        lib.snapshot(maxFiles = maxFiles, onProgress = onProgress, cancelled = cancelled)

    fun Ops() = lib.Ops()

    companion object { const val TRASH_NAME = DocTreeLibrary.TRASH_NAME }
}

/** `DocumentsContract` over one picked tree (any provider: internal memory, SD card, USB drive, cloud). */
class AndroidDocProvider(private val ctx: Context, private val tree: Uri) : DocProvider {
    private val cr: ContentResolver get() = ctx.contentResolver
    override val rootId: String = DocumentsContract.getTreeDocumentId(tree)

    private val cols = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED, DocumentsContract.Document.COLUMN_FLAGS)

    private fun docUri(id: String) = DocumentsContract.buildDocumentUriUsingTree(tree, id)
    private fun idOf(u: Uri) = DocumentsContract.getDocumentId(u)

    private fun entry(c: android.database.Cursor) = DocEntry(c.getString(0), c.getString(1).orEmpty(), c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR,
        if (c.isNull(3)) 0 else c.getLong(3), if (c.isNull(4)) 0 else c.getLong(4))

    override fun children(parentId: String): List<DocEntry> {
        val out = ArrayList<DocEntry>()
        runCatching {
            cr.query(DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId), cols, null, null, null)?.use { c -> while (c.moveToNext()) out += entry(c) }
        }
        return out
    }

    private fun flags(id: String): Int = runCatching {
        cr.query(docUri(id), arrayOf(DocumentsContract.Document.COLUMN_FLAGS), null, null, null)?.use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getInt(0) else 0 }
    }.getOrNull() ?: 0

    override fun info(id: String): DocEntry? = runCatching {
        cr.query(docUri(id), cols, null, null, null)?.use { c -> if (c.moveToFirst()) entry(c) else null }
    }.getOrNull()

    override fun createDir(parentId: String, name: String): DocEntry? =
        runCatching { DocumentsContract.createDocument(cr, docUri(parentId), DocumentsContract.Document.MIME_TYPE_DIR, name) }.getOrNull()?.let { info(idOf(it)) }

    override fun rename(id: String, newName: String): DocEntry? {
        val u = runCatching { DocumentsContract.renameDocument(cr, docUri(id), newName) }.getOrNull() ?: return null
        return info(idOf(u))        // the provider may have changed the name or the id: report what it really did
    }

    override fun move(id: String, fromParentId: String, toParentId: String): DocEntry? {
        val f = flags(id)
        if (f and DocumentsContract.Document.FLAG_SUPPORTS_MOVE != 0) {
            val u = runCatching { DocumentsContract.moveDocument(cr, docUri(id), docUri(fromParentId), docUri(toParentId)) }.getOrNull()
            if (u != null) return info(idOf(u))
        }
        return copyThenDelete(id, f, toParentId)
    }

    /**
     * For providers that cannot move: copy the bytes, check the size, and only then delete the original. A copy that does not match is deleted
     * and the original is left untouched. Limited to 1 GB (the copy goes through the phone, slowly): bigger files are refused, never half done.
     */
    private fun copyThenDelete(id: String, f: Int, toParentId: String): DocEntry? {
        if (f and DocumentsContract.Document.FLAG_SUPPORTS_DELETE == 0) return null
        val src = info(id) ?: return null
        if (src.isDir || src.size > COPY_LIMIT) return null
        val mime = runCatching { cr.getType(docUri(id)) }.getOrNull() ?: "application/octet-stream"
        val dst = runCatching { DocumentsContract.createDocument(cr, docUri(toParentId), mime, src.name) }.getOrNull() ?: return null
        val ok = runCatching {
            cr.openInputStream(docUri(id))!!.use { inp -> cr.openOutputStream(dst, "w")!!.use { out -> inp.copyTo(out, 256 * 1024) } }
            true
        }.getOrDefault(false)
        val made = info(idOf(dst))
        if (!ok || made == null || made.size != src.size) { runCatching { DocumentsContract.deleteDocument(cr, dst) }; return null }
        if (!runCatching { DocumentsContract.deleteDocument(cr, docUri(id)) }.getOrDefault(false)) { runCatching { DocumentsContract.deleteDocument(cr, dst) }; return null }
        return made
    }

    override fun delete(id: String): Boolean = runCatching { DocumentsContract.deleteDocument(cr, docUri(id)) }.getOrDefault(false)

    override fun durationMs(id: String): Long {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(ctx, docUri(id))
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
        } catch (e: Exception) { 0 } finally { runCatching { mmr.release() } }
    }

    /** Free space and name of the volume the picked folder is on: the internal memory, an SD card or a USB drive (never assumes "the phone"). */
    override fun volume(): VolumeInfo {
        val rootKey = rootId.substringBefore(':')
        var title: String? = null; var free = -1L; var total = 0L
        runCatching {
            cr.query(DocumentsContract.buildRootsUri(tree.authority!!), arrayOf(DocumentsContract.Root.COLUMN_ROOT_ID, DocumentsContract.Root.COLUMN_TITLE,
                DocumentsContract.Root.COLUMN_AVAILABLE_BYTES, DocumentsContract.Root.COLUMN_CAPACITY_BYTES), null, null, null)?.use { c ->
                while (c.moveToNext()) if (c.getString(0) == rootKey) { title = c.getString(1); if (!c.isNull(2)) free = c.getLong(2); if (!c.isNull(3)) total = c.getLong(3) }
            }
        }
        val primary = rootKey == "primary" || tree.authority != "com.android.externalstorage.documents"
        if (free < 0 && primary) runCatching { StatFs(Environment.getExternalStorageDirectory().path).let { free = it.availableBytes; total = it.totalBytes } }
        val label = if (primary) "Téléphone" else (title?.takeIf { it.isNotBlank() } ?: "Carte SD")
        return VolumeInfo("phone", label, if (primary) "phone" else "sd", free, total, true)
    }

    private companion object { const val COPY_LIMIT = 1L shl 30 }
}
