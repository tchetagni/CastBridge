package castbridge.receiver

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import castbridge.core.tv.ImportEntry
import castbridge.core.tv.ImportResult
import castbridge.core.tv.UsbImport
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Imports videos from a USB drive into the app's private folder, in the background, with progress on
 * the waiting screen. No storage permission is involved, only what Android grants an app:
 *  (a) the app's own folder on each secondary volume (getExternalFilesDirs), which a PC can fill, and
 *  (b) any folder the user picks with the system document picker (ACTION_OPEN_DOCUMENT_TREE), if the TV has one.
 */
class UsbImporter(private val act: Activity, private val dir: File, private val status: (String?) -> Unit) {
    private val running = AtomicBoolean(false)
    @Volatile private var cancel = false
    @Volatile var message: String = "inactif"; private set

    fun isRunning() = running.get()
    fun cancel() { cancel = true }

    /** App folders on removable volumes (index 0 is the internal/primary one). */
    fun volumeRoots(): List<File> = runCatching {
        act.getExternalFilesDirs(null).drop(1).filterNotNull()
            .filter { Environment.getExternalStorageState(it) == Environment.MEDIA_MOUNTED }
    }.getOrDefault(emptyList())

    /** (a) Scan the app folders of the mounted removable volumes. Returns a message for the UI. */
    fun importFromVolumes(): String {
        val roots = volumeRoots()
        if (roots.isEmpty()) return "Aucune clé USB détectée. Branchez la clé (elle doit être reconnue par la TV)."
        val entries = UsbImport.scan(roots)
        if (entries.isEmpty()) return "Aucune vidéo trouvée dans ${roots.joinToString { it.absolutePath }}. " +
            "Copiez-y les vidéos depuis un PC, ou utilisez « choisir un dossier »."
        return start(entries)
    }

    /** (b) Open the system folder picker. */
    fun launchPicker(requestCode: Int): String? = try {
        act.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), requestCode); null
    } catch (e: ActivityNotFoundException) {
        "Cette TV n'a pas de sélecteur de fichiers Android : utilisez le scan des volumes (dossier de l'app sur la clé) " +
            "ou l'envoi par Wi-Fi / Bluetooth."
    } catch (e: Exception) { "Sélecteur indisponible : ${e.message}" }

    fun importTree(tree: Uri): String {
        val entries = try { listTree(tree) } catch (e: Exception) { return "Lecture du dossier impossible : ${e.message}" }
        if (entries.isEmpty()) return "Aucune vidéo dans ce dossier."
        return start(entries)
    }

    private fun listTree(tree: Uri): List<ImportEntry> {
        val out = ArrayList<ImportEntry>()
        val cr = act.contentResolver
        fun walk(docId: String, depth: Int) {
            if (depth > 8) return
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
            cr.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE), null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0); val name = c.getString(1) ?: continue; val mime = c.getString(2) ?: ""
                    if (name.startsWith(".")) continue
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) walk(id, depth + 1)
                    else if (UsbImport.isVideo(name) || mime.startsWith("video/")) {
                        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                        out += ImportEntry(name, if (c.isNull(3)) -1 else c.getLong(3)) {
                            cr.openInputStream(uri) ?: throw java.io.IOException("ouverture impossible")
                        }
                    }
                }
            }
        }
        walk(DocumentsContract.getTreeDocumentId(tree), 0)
        return out.filter { it.size > 0 }
    }

    private fun start(entries: List<ImportEntry>): String {
        if (!running.compareAndSet(false, true)) return "Import déjà en cours."
        cancel = false
        val total = entries.sumOf { it.size }
        message = "en cours : ${entries.size} fichier(s)"
        thread(name = "usb-import") {
            try {
                var last = -1
                val r: ImportResult = UsbImport.copyAll(entries, dir, cancelled = { cancel }) { p ->
                    val pct = (p.bytesDone * 100 / p.bytesTotal.coerceAtLeast(1)).toInt()
                    if (pct != last) {
                        last = pct
                        message = "copie ${p.index}/${p.count} ${p.name} (total $pct %)"
                        status("USB : $message")
                    }
                }
                message = (if (r.cancelled) "annulé : " else "terminé : ") +
                    "${r.copied} copié(s), ${r.skipped} déjà présent(s), ${r.failed.size} échec(s)" +
                    (r.failed.firstOrNull()?.let { " — $it" } ?: "")
                status("USB : $message")
            } catch (e: Throwable) {
                Log.w(TAG, "import failed", e)
                message = "erreur : ${e.message}"; status("USB : $message")
            } finally { running.set(false) }
        }
        return "Import lancé : ${entries.size} fichier(s), ${total shr 20} Mo. La progression s'affiche sur l'écran d'attente."
    }

    companion object { private const val TAG = "UsbImporter" }
}
