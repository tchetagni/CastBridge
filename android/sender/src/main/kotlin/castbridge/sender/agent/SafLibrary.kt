package castbridge.sender.agent

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.DocumentsContract
import castbridge.core.library.agent.FileRef
import castbridge.core.library.agent.LibraryOps
import castbridge.core.library.agent.LibrarySnapshot
import castbridge.core.library.agent.Loc
import castbridge.core.library.agent.OpResult
import castbridge.core.library.agent.Origin
import castbridge.core.library.agent.Stat
import castbridge.core.library.agent.VolumeInfo

/**
 * The phone's own files, reached ONLY through a folder the user picked with the system picker (Storage Access Framework):
 * no storage permission is requested, and nothing outside that folder can be read or changed. Folders are real here.
 *
 * Reading gathers metadata only (name, size, date, folder, and the duration of videos / audio read from their header by
 * the system's MediaMetadataRetriever); the content of a file is never read for the agent.
 */
class SafLibrary(private val ctx: Context, private val tree: Uri) {
    private val cr: ContentResolver get() = ctx.contentResolver
    private val rootId: String = DocumentsContract.getTreeDocumentId(tree)

    private data class Doc(val id: String, val name: String, val mime: String, val size: Long, val mtime: Long) {
        val dir get() = mime == DocumentsContract.Document.MIME_TYPE_DIR
    }

    private fun docUri(id: String) = DocumentsContract.buildDocumentUriUsingTree(tree, id)

    private fun children(parentId: String): List<Doc> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val out = ArrayList<Doc>()
        runCatching {
            cr.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED), null, null, null)?.use { c ->
                while (c.moveToNext()) out += Doc(c.getString(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(), if (c.isNull(3)) 0 else c.getLong(3), if (c.isNull(4)) 0 else c.getLong(4))
            }
        }
        return out
    }

    /** Resolves a relative folder to its document id ("" = the picked folder). Null when a segment does not exist. */
    private fun folderId(folder: String): String? {
        var id = rootId
        if (folder.isEmpty()) return id
        for (seg in folder.split('/')) id = children(id).firstOrNull { it.dir && it.name.equals(seg, ignoreCase = true) }?.id ?: return null
        return id
    }

    private fun find(loc: Loc): Doc? = folderId(loc.folder)?.let { f -> children(f).firstOrNull { !it.dir && it.name.equals(loc.name, ignoreCase = true) } }

    fun snapshot(maxFiles: Int = 5000, withDurations: Boolean = true, progress: (Int) -> Unit = {}, cancelled: () -> Boolean = { false }): LibrarySnapshot {
        val files = ArrayList<FileRef>()
        fun walk(id: String, path: String, depth: Int) {
            if (depth > 6 || files.size >= maxFiles || cancelled()) return
            for (d in children(id)) {
                if (d.name.startsWith(".")) continue
                if (d.dir) { if (!(depth == 0 && d.name == TRASH_NAME)) walk(d.id, if (path.isEmpty()) d.name else "$path/${d.name}", depth + 1) }
                else if (files.size < maxFiles) files += FileRef(Origin.PHONE, d.name, d.size, d.mtime, "phone", path)
            }
            progress(files.size)
        }
        walk(rootId, "", 0)
        val withDur = if (withDurations) files.mapIndexed { i, f ->
            if (cancelled() || i >= 300 || f.size < 5L shl 20 || !isMedia(f.name)) f else f.copy(durationMs = duration(f))
        } else files
        return LibrarySnapshot(Origin.PHONE, withDur, listOf(volume()), System.currentTimeMillis())
    }

    private fun isMedia(n: String) = castbridge.core.library.agent.NameParser.mediaOf(n.substringAfterLast('.', "")).let {
        it == castbridge.core.library.agent.Media.VIDEO || it == castbridge.core.library.agent.Media.AUDIO
    }

    private fun duration(f: FileRef): Long {
        val d = find(f.loc) ?: return 0
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(ctx, docUri(d.id))
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
        } catch (e: Exception) { 0 } finally { runCatching { mmr.release() } }
    }

    fun volume(): VolumeInfo {
        val st = runCatching { StatFs(Environment.getExternalStorageDirectory().path) }.getOrNull()
        return VolumeInfo("phone", "Téléphone", "phone", st?.availableBytes ?: -1, st?.totalBytes ?: 0, true)
    }

    // ------------------------------------------------------------------ operations (the executor's view of the phone)

    inner class Ops : LibraryOps {
        override val folders = true
        override fun volumes() = listOf(volume())
        override fun stat(loc: Loc): Stat? = find(loc)?.let { Stat(it.size, it.mtime) }
        override fun isPlaying(loc: Loc) = false

        private fun child(parentId: String, name: String) = children(parentId).firstOrNull { it.name.equals(name, ignoreCase = true) }

        override fun mkdirs(volume: String, folder: String): OpResult {
            var id = rootId
            for (seg in folder.split('/').filter { it.isNotEmpty() }) {
                val ex = child(id, seg)
                id = if (ex != null && ex.dir) ex.id else {
                    if (ex != null) return OpResult.Fail("« $seg » existe déjà et n'est pas un dossier")
                    val u = runCatching { DocumentsContract.createDocument(cr, docUri(id), DocumentsContract.Document.MIME_TYPE_DIR, seg) }.getOrNull() ?: return OpResult.Fail("création du dossier « $seg » refusée")
                    DocumentsContract.getDocumentId(u)
                }
            }
            return OpResult.Ok(Loc(volume, folder, ""))
        }

        override fun rename(loc: Loc, newName: String): OpResult {
            val d = find(loc) ?: return OpResult.Fail("introuvable")
            if (child(folderId(loc.folder) ?: return OpResult.Fail("dossier introuvable"), newName) != null) return OpResult.Fail("le nom existe déjà")
            val u = runCatching { DocumentsContract.renameDocument(cr, docUri(d.id), newName) }.getOrNull() ?: return OpResult.Fail("renommage refusé par le système")
            // some providers silently change the name: check what we got
            val got = runCatching { cr.query(u, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } }.getOrNull()
            return if (got == null || got == newName) OpResult.Ok(loc.copy(name = newName)) else OpResult.Ok(loc.copy(name = got))
        }

        override fun moveToFolder(loc: Loc, folder: String): OpResult {
            val d = find(loc) ?: return OpResult.Fail("introuvable")
            val src = folderId(loc.folder) ?: return OpResult.Fail("dossier d'origine introuvable")
            val dst = folderId(folder) ?: return OpResult.Fail("dossier de destination introuvable")
            if (child(dst, loc.name) != null) return OpResult.Fail("le nom existe déjà dans le dossier")
            val u = runCatching { DocumentsContract.moveDocument(cr, docUri(d.id), docUri(src), docUri(dst)) }.getOrNull()
                ?: return OpResult.Fail("ce dossier ne permet pas de déplacer des fichiers")
            return OpResult.Ok(Loc(loc.volume, folder, loc.name))
        }

        override fun moveToVolume(loc: Loc, toVolume: String, onProgress: (Long, Long) -> Unit, cancelled: () -> Boolean): OpResult =
            OpResult.Fail("le téléphone n'a qu'un seul volume ici")

        private fun trashId(): String? {
            val ex = child(rootId, TRASH_NAME)
            if (ex != null) return if (ex.dir) ex.id else null
            return runCatching { DocumentsContract.getDocumentId(DocumentsContract.createDocument(cr, docUri(rootId), DocumentsContract.Document.MIME_TYPE_DIR, TRASH_NAME)) }.getOrNull()
        }

        override fun trash(loc: Loc): OpResult {
            val d = find(loc) ?: return OpResult.Fail("introuvable")
            val src = folderId(loc.folder) ?: return OpResult.Fail("dossier d'origine introuvable")
            val bin = trashId() ?: return OpResult.Fail("impossible de créer « $TRASH_NAME »")
            // a name already in the bin: make ours unique first (the journal keeps the original name)
            var tn = loc.name
            if (child(bin, tn) != null) {
                val dot = tn.lastIndexOf('.').takeIf { it > 0 && tn.length - it <= 6 } ?: tn.length
                var i = 2
                while (child(bin, tn.substring(0, dot) + " ($i)" + tn.substring(dot)) != null) i++
                tn = tn.substring(0, dot) + " ($i)" + tn.substring(dot)
                val r = rename(loc, tn)
                if (r is OpResult.Fail) return r
            }
            val doc = find(loc.copy(name = tn)) ?: return OpResult.Fail("introuvable")
            runCatching { DocumentsContract.moveDocument(cr, docUri(doc.id), docUri(src), docUri(bin)) }.getOrNull()
                ?: return OpResult.Fail("ce dossier ne permet pas de déplacer des fichiers : rien n'a été supprimé")
            return OpResult.Ok(loc, tn)
        }

        override fun restore(trashId: String, original: Loc): OpResult {
            val bin = trashId() ?: return OpResult.Fail("corbeille introuvable")
            val d = child(bin, trashId) ?: return OpResult.Fail("n'est plus dans la corbeille")
            val dst = folderId(original.folder) ?: (mkdirs(original.volume, original.folder).let { folderId(original.folder) }) ?: return OpResult.Fail("dossier d'origine introuvable")
            var name = original.name
            if (child(dst, name) != null) {
                val dot = name.lastIndexOf('.').takeIf { it > 0 && name.length - it <= 6 } ?: name.length
                var i = 1
                name = name.substring(0, dot) + " (restauré)" + name.substring(dot)
                while (child(dst, name) != null) { i++; name = original.name.substring(0, dot) + " (restauré $i)" + original.name.substring(dot) }
            }
            // move back under its bin name, then give it its own name
            if (child(dst, trashId) != null) return OpResult.Fail("un fichier « $trashId » existe déjà à l'emplacement d'origine")
            runCatching { DocumentsContract.moveDocument(cr, docUri(d.id), docUri(bin), docUri(dst)) }.getOrNull() ?: return OpResult.Fail("restauration refusée par le système")
            val back = Loc(original.volume, original.folder, trashId)
            if (trashId != name) { val r = rename(back, name); if (r is OpResult.Fail) return OpResult.Ok(back) }
            return OpResult.Ok(Loc(original.volume, original.folder, name))
        }

        override fun findInTrash(original: Loc): String? = trashId()?.let { child(it, original.name)?.name }

        /** What is in the bin (for the "Corbeille" screen). */
        fun binItems(): List<Pair<String, Long>> = trashId()?.let { b -> children(b).filter { !it.dir }.map { it.name to it.size } }.orEmpty()

        /** Really deletes one item of the bin: only ever called after the user's explicit confirmation. */
        fun purge(name: String): Boolean {
            val d = trashId()?.let { child(it, name) } ?: return false
            return runCatching { DocumentsContract.deleteDocument(cr, docUri(d.id)) }.getOrDefault(false)
        }
    }

    companion object {
        const val TRASH_NAME = "Corbeille CastBridge"
    }
}
