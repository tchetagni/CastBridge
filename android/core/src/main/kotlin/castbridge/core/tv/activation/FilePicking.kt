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

/** Decision on a chosen file; [Keys] are the key-shaped pieces of the whole text ([KeyScan.candidates]), in reading order, still to be verified one by one by the activation parser. */
sealed class PickResult(val message: String) {
    object Cancelled : PickResult("Aucun fichier choisi")
    object Nothing : PickResult("Aucun fichier choisi")
    object Unreadable : PickResult("Impossible de lire ce fichier : Android refuse l'accès (autorisez l'accès aux fichiers) ou la clé a été retirée")
    object Empty : PickResult("Ce fichier est vide : ce n'est pas une activation")
    object TooBig : PickResult("Ce fichier est trop gros (${KeyScan.MAX_FILE_BYTES / 1024} Kio au plus) : ce n'est pas une activation")
    object NotText : PickResult("Ce fichier n'est pas du texte : ce n'est pas une activation")
    object NoKey : PickResult(KeyScan.NO_KEY)
    class Keys(val candidates: List<String>) : PickResult("Clé trouvée : vérification…")
}

/** Which chooser buttons the activation screen shows, and what a chosen file means. Pure: the Android side only feeds it facts. */
object PickerPlan {
    /** A chosen text file is read up to 256 Kio and searched line by line for the key ([KeyScan]). */
    const val MAX_BYTES = KeyScan.MAX_FILE_BYTES
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
                ActivationLookup.firstLine(b) == null -> PickResult.Empty
                else -> KeyScan.candidates(String(b, Charsets.UTF_8)).let { if (it.isEmpty()) PickResult.NoKey else PickResult.Keys(it) }
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

/** [action] = a row that opens something (« allow all files », system explorer); [info] = an explanation row (not selectable); [reason] = why a listed file cannot be chosen (shown greyed). */
data class BrowseRow(val label: String, val isDir: Boolean, val selectable: Boolean, val size: Long = 0, val action: Boolean = false, val info: Boolean = false, val reason: String? = null)

/** Why a listed file cannot be chosen as an activation, from its name and size only (the content is checked once chosen): null = choosable. */
object FileKinds {
    const val NOT_TEXT = "pas du texte"
    const val TOO_BIG = "trop gros"
    const val EMPTY = "vide"

    /** Extensions that are never text (video, image, sound, archive, application, office document). */
    private val BINARY = setOf(
        "mp4", "mkv", "avi", "mov", "m4v", "webm", "ts", "mpg", "mpeg", "3gp", "wmv", "flv",
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "tif", "tiff", "ico",
        "mp3", "wav", "aac", "flac", "ogg", "opus", "m4a", "wma", "amr",
        "zip", "rar", "7z", "gz", "tgz", "bz2", "xz", "tar", "iso", "img", "bin", "dmg",
        "apk", "apks", "xapk", "aab", "exe", "msi", "dex", "so", "jar", "class", "obb",
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "epub",
        "db", "sqlite", "learn", "cbhash", "torrent",
    )

    fun refusal(name: String, size: Long, maxPick: Long): String? = when {
        name.lastIndexOf('.') > 0 && name.substringAfterLast('.').lowercase() in BINARY -> NOT_TEXT
        size > maxPick -> TOO_BIG
        size <= 0 -> EMPTY
        else -> null
    }

    /** The owner-facing refusal when such a file is chosen anyway. */
    fun message(reason: String, maxPick: Long): String = when (reason) {
        TOO_BIG -> "Ce fichier est trop gros (${maxPick / 1024} Kio au plus) : ce n'est pas une activation"
        EMPTY -> "Ce fichier est vide : ce n'est pas une activation"
        else -> "Ce fichier n'est pas du texte (vidéo, image, archive…) : ce n'est pas une activation"
    }
}
data class BrowseView(val title: String, val rows: List<BrowseRow>, val notice: String?)

sealed class BrowseAction {
    object Redraw : BrowseAction()
    class Picked(val file: File) : BrowseAction()
    class Refused(val message: String) : BrowseAction()
    /** The owner chose the first row: open the settings screen « all files access ». */
    object AskAccess : BrowseAction()
    /** The owner chose « Explorateur du système »: the activation screen opens the system picker (it shows what Android hides from the app). */
    object SystemPicker : BrowseAction()
    object Quit : BrowseAction()
}

/**
 * Navigation of the built-in explorer. State = stack of folders; empty stack = the list of volumes (the top). BACK goes to the parent folder, then to the volume list,
 * then quits: it never climbs above a volume root. EVERY entry of the folder is listed (hidden ones too, up to [MAX_ROWS]): files named like an activation first, then folders,
 * then the files that can be chosen, then the others greyed with their reason ([FileKinds]: not text, too big, empty). Nothing is ever written.
 */
class FileBrowser(
    private val roots: List<BrowseRoot>, private val fs: BrowseFs = RealBrowseFs, private val maxPick: Long = PickerPlan.MAX_BYTES.toLong(),
    private val access: StorageAccess = StorageAccess.GRANTED, private val settingsScreen: Boolean = true, private val ownPath: String? = null,
    /** An app answers ACTION_OPEN_DOCUMENT on this box: a « Explorateur du système » row is offered while Android hides files. */
    private val systemPicker: Boolean = false,
) {
    /** The permission row (or its explanation), then the system explorer, shown first while the permission is missing. */
    private val top: List<BrowseRow> = when {
        access == StorageAccess.GRANTED -> emptyList()
        settingsScreen -> listOf(BrowseRow(AccessTexts.ASK_ALL, false, true, action = true))
        else -> listOf(BrowseRow(AccessTexts.NO_SCREEN, false, false, info = true))
    } + if (access == StorageAccess.MISSING && systemPicker) listOf(BrowseRow(AccessTexts.SYSTEM, false, true, action = true)) else emptyList()
    /** Entries beyond [MAX_ROWS] in the current folder (said in a last row, never silently dropped). */
    private var moreRows = 0
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
        denied = false; moreRows = 0
        val d = stack.lastOrNull()
        rows = if (d == null) emptyList() else {
            val l = try { fs.list(d) } catch (e: SecurityException) { null }
            if (l == null) { denied = true; emptyList() }
            else l.sortedWith(compareBy<BrowseEntry>({ it.isDir || !ActivationNames.isOffered(it.name, it.size, maxPick) }, { !it.isDir }, { refusal(it) != null }, { it.name.lowercase() }, { it.name }))
                .also { moreRows = (it.size - MAX_ROWS).coerceAtLeast(0) }.take(MAX_ROWS)
        }
    }

    private fun refusal(e: BrowseEntry): String? = if (e.isDir) null else FileKinds.refusal(e.name, e.size, maxPick)

    fun view(): BrowseView {
        val d = stack.lastOrNull()
        // where Android hides files from the app (no « all files access »): said on top of the list, with the ways out
        val hidden = if (access == StorageAccess.MISSING) AccessTexts.hidden(settingsScreen, systemPicker) else null
        if (d == null) return BrowseView("Choisir un emplacement", top + roots.map { BrowseRow(label(it), true, true) }, if (roots.isEmpty()) "Aucun stockage monté : branchez la clé USB" else hidden)
        val notice = when {
            denied -> "Android refuse de lister ce dossier (permission de stockage) : autorisez l'accès aux fichiers, ou ouvrez Android/data/castbridge.receiver/files"
            rows.isEmpty() && access == StorageAccess.MISSING && !inOwnDir(d) -> ActivationLookupReport.missingAccess(ownPath)
            rows.isEmpty() -> "Dossier vide"
            !inOwnDir(d) -> hidden
            else -> null
        }
        val more = if (moreRows > 0) listOf(BrowseRow("… et $moreRows autres éléments non affichés (dossier trop rempli)", false, false, info = true)) else emptyList()
        return BrowseView(title(d), top + rows.map { e -> refusal(e).let { r -> BrowseRow(if (e.isDir) e.name + "/" else e.name, e.isDir, r == null, e.size, reason = r) } } + more, notice)
    }

    private fun inOwnDir(d: File) = ownPath != null && (d.path == ownPath || d.path.startsWith("$ownPath/"))
    private fun label(r: BrowseRoot) = if (r.id == null) r.label else "${r.label} ${r.id}"
    private fun title(d: File): String { val r = roots.filter { d.path == it.dir.path || d.path.startsWith(it.dir.path + "/") }.maxByOrNull { it.dir.path.length }; return if (r == null) d.path else label(r) + d.path.removePrefix(r.dir.path).ifEmpty { "/" } }

    /** OK on row [index]: enters a folder, picks a file, or refuses a file too big. */
    fun open(index: Int): BrowseAction {
        if (index < top.size) return when { !top[index].action -> BrowseAction.Redraw; top[index].label == AccessTexts.SYSTEM -> BrowseAction.SystemPicker; else -> BrowseAction.AskAccess }
        val index = index - top.size
        if (stack.isEmpty()) { val r = roots.getOrNull(index) ?: return BrowseAction.Redraw; stack += r.dir; load(); return BrowseAction.Redraw }
        val e = rows.getOrNull(index) ?: return BrowseAction.Redraw
        val f = File(stack.last(), e.name)
        if (e.isDir) { stack += f; load(); return BrowseAction.Redraw }
        refusal(e)?.let { return BrowseAction.Refused(FileKinds.message(it, maxPick)) }
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

    companion object { const val MAX_ROWS = 2000 }
}
