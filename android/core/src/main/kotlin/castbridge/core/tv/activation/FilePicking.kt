package castbridge.core.tv.activation

import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/** What the screen got from a file chosen by the owner (built-in explorer or system picker). The bytes are at most [PickerPlan.MAX_BYTES] + 1 long (bounded read). */
sealed class PickInput {
    object Cancelled : PickInput()
    /** The chooser answered with nothing usable (no file, no stream). */
    object Nothing : PickInput()
    /** The file could not be opened (storage permission, removed key). */
    object Unreadable : PickInput()
    class Bytes(val bytes: ByteArray) : PickInput()
}

/** Decision on a chosen file; a [Key] is the first line, still to be verified by the activation parser. */
sealed class PickResult(val message: String) {
    object Cancelled : PickResult("Aucun fichier choisi")
    object Nothing : PickResult("Aucun fichier choisi")
    object Unreadable : PickResult("Impossible de lire ce fichier : Android refuse l'accès (autorisez l'accès aux fichiers) ou la clé a été retirée")
    object Empty : PickResult("Ce fichier est vide : ce n'est pas une activation")
    object TooBig : PickResult("Ce fichier est trop gros (16 Kio au plus) : ce n'est pas une activation")
    object NotText : PickResult("Ce fichier n'est pas du texte : ce n'est pas une activation")
    class Key(val line: String) : PickResult("Clé trouvée : vérification…")
}

/** Which chooser buttons the activation screen shows, and what a chosen file means. Pure: the Android side only feeds it facts. */
object PickerPlan {
    const val MAX_BYTES = 16_384
    const val NO_SYSTEM_PICKER = "Cet appareil n'a pas d'explorateur de fichiers système : utilisez l'explorateur de CastBridge-TV, le téléphone (Bluetooth) ou collez la clé"

    enum class Chooser { BUILT_IN, SYSTEM }

    /** The built-in explorer is ALWAYS offered first (a poor box has no system one); the system picker only if an app can handle it. */
    fun choosers(systemPickerAvailable: Boolean): List<Chooser> = if (systemPickerAvailable) listOf(Chooser.BUILT_IN, Chooser.SYSTEM) else listOf(Chooser.BUILT_IN)

    /** The line to show when the system picker was asked for and nothing handles it. */
    fun systemPickerMissing(): String = NO_SYSTEM_PICKER

    /** `content://com.android.externalstorage.documents/document/<volumeId>%3ADownload`, or null when the id is not a plain volume id (never built from anything else). */
    fun initialUri(volumeId: String?): String? =
        volumeId?.takeIf { Regex("^[A-Za-z0-9-]{4,32}$").matches(it) }?.let { "content://com.android.externalstorage.documents/document/$it%3ADownload" }

    fun decide(input: PickInput): PickResult = when (input) {
        PickInput.Cancelled -> PickResult.Cancelled
        PickInput.Nothing -> PickResult.Nothing
        PickInput.Unreadable -> PickResult.Unreadable
        is PickInput.Bytes -> {
            val b = input.bytes
            when {
                b.size > MAX_BYTES -> PickResult.TooBig
                b.isEmpty() -> PickResult.Empty
                !isText(b) -> PickResult.NotText
                else -> ActivationLookup.firstLine(b)?.let { PickResult.Key(it) } ?: PickResult.Empty
            }
        }
    }

    /** UTF-8 text: valid encoding, no NUL, no control character other than tab, CR, LF. */
    fun isText(b: ByteArray): Boolean {
        val s = try { Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(b)).toString() }
        catch (e: CharacterCodingException) { return false }
        return s.none { it.code < 0x20 && it != '\t' && it != '\r' && it != '\n' || it.code == 0x7f }
    }

    /** Owner-facing text of a verdict on a CHOSEN file (the refusal causes of the parser itself come from its own messages). */
    fun verdictText(v: Verdict): String = when (v) {
        Verdict.ACCEPTED -> "Clé trouvée : vérification…"
        Verdict.NOT_VALID -> "Ce fichier n'est pas une activation CastBridge (texte incomplet, abîmé ou autre fichier)"
        Verdict.WRONG_DEVICE -> "Cette clé est celle d'une autre TV : vérifiez le code d'appareil donné"
        Verdict.EXPIRED -> "Cette clé est périmée (valable 48 h) : demandez-en une nouvelle"
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Built-in file explorer: the pure navigation model (the screen only draws it and sends keys)
// ---------------------------------------------------------------------------------------------------------------

/** A volume at the root of the explorer. [id] null = internal storage. */
data class BrowseRoot(val label: String, val dir: File, val id: String?)
data class BrowseEntry(val name: String, val isDir: Boolean, val size: Long)

/** Read-only access for the explorer: no write, delete or rename exists. [list] null = refused. */
interface BrowseFs {
    fun isDir(f: File): Boolean
    fun list(dir: File): List<BrowseEntry>?
}

object RealBrowseFs : BrowseFs {
    override fun isDir(f: File) = runCatching { f.isDirectory }.getOrDefault(false)
    override fun list(dir: File): List<BrowseEntry>? = try {
        dir.listFiles()?.map { BrowseEntry(it.name, it.isDirectory, if (it.isDirectory) 0 else runCatching { it.length() }.getOrDefault(0)) }
    } catch (e: SecurityException) { null }
}

data class BrowseRow(val label: String, val isDir: Boolean, val selectable: Boolean, val size: Long = 0)
data class BrowseView(val title: String, val rows: List<BrowseRow>, val notice: String?)

sealed class BrowseAction {
    object Redraw : BrowseAction()
    class Picked(val file: File) : BrowseAction()
    class Refused(val message: String) : BrowseAction()
    object Quit : BrowseAction()
}

/**
 * Navigation of the built-in explorer. State = stack of folders; empty stack = the list of volumes (the top). BACK goes to the parent folder, then to the volume list,
 * then quits: it never climbs above a volume root. Folders first, then files up to [MAX_PICK] bytes, then bigger files (shown, refused). Nothing is ever written.
 */
class FileBrowser(private val roots: List<BrowseRoot>, private val fs: BrowseFs = RealBrowseFs, private val maxPick: Long = PickerPlan.MAX_BYTES.toLong()) {
    private val stack = ArrayList<File>()
    private var rows: List<BrowseEntry> = emptyList()
    private var denied = false

    val depth: Int get() = stack.size
    val current: File? get() = stack.lastOrNull()

    /** Opens the first of [places] that exists and can be listed (the useful places first); otherwise stays on the volume list. */
    fun startAt(places: List<File>): Boolean {
        for (p in places) {
            val root = roots.filter { p.path == it.dir.path || p.path.startsWith(it.dir.path + "/") }.maxByOrNull { it.dir.path.length } ?: continue
            if (!fs.isDir(p) || fs.list(p) == null) continue
            stack.clear(); stack += root.dir
            val rel = p.path.removePrefix(root.dir.path).trim('/')
            var d = root.dir
            if (rel.isNotEmpty()) for (seg in rel.split('/')) { d = File(d, seg); stack += d }
            load(); return true
        }
        stack.clear(); load(); return false
    }

    private fun load() {
        denied = false
        val d = stack.lastOrNull()
        rows = if (d == null) emptyList() else {
            val l = try { fs.list(d) } catch (e: SecurityException) { null }
            if (l == null) { denied = true; emptyList() }
            else l.sortedWith(compareBy<BrowseEntry>({ !it.isDir }, { !it.isDir && it.size > maxPick }, { it.name.lowercase() }, { it.name })).take(MAX_ROWS)
        }
    }

    fun view(): BrowseView {
        val d = stack.lastOrNull()
        if (d == null) return BrowseView("Choisir un emplacement", roots.map { BrowseRow(label(it), true, true) }, if (roots.isEmpty()) "Aucun stockage monté : branchez la clé USB" else null)
        val notice = when {
            denied -> "Android refuse de lister ce dossier (permission de stockage) : autorisez l'accès aux fichiers, ou ouvrez Android/data/castbridge.receiver/files"
            rows.isEmpty() -> "Dossier vide"
            else -> null
        }
        return BrowseView(title(d), rows.map { BrowseRow(if (it.isDir) it.name + "/" else it.name, it.isDir, it.isDir || it.size <= maxPick, it.size) }, notice)
    }

    private fun label(r: BrowseRoot) = if (r.id == null) r.label else "${r.label} ${r.id}"
    private fun title(d: File): String { val r = roots.filter { d.path == it.dir.path || d.path.startsWith(it.dir.path + "/") }.maxByOrNull { it.dir.path.length }; return if (r == null) d.path else label(r) + d.path.removePrefix(r.dir.path).ifEmpty { "/" } }

    /** OK on row [index]: enters a folder, picks a file, or refuses a file too big. */
    fun open(index: Int): BrowseAction {
        if (stack.isEmpty()) { val r = roots.getOrNull(index) ?: return BrowseAction.Redraw; stack += r.dir; load(); return BrowseAction.Redraw }
        val e = rows.getOrNull(index) ?: return BrowseAction.Redraw
        val f = File(stack.last(), e.name)
        if (e.isDir) { stack += f; load(); return BrowseAction.Redraw }
        if (e.size > maxPick) return BrowseAction.Refused("Ce fichier est trop gros (${maxPick / 1024} Kio au plus) : ce n'est pas une activation")
        return BrowseAction.Picked(f)
    }

    /** BACK: parent folder, then the volume list, then quit. */
    fun back(): BrowseAction {
        if (stack.isEmpty()) return BrowseAction.Quit
        val top = stack.removeAt(stack.size - 1)
        // leaving a volume root returns to the list of volumes, never to its parent folder
        if (roots.any { it.dir.path == top.path }) stack.clear()
        load(); return BrowseAction.Redraw
    }

    companion object { const val MAX_ROWS = 300 }
}
