package castbridge.core.tv.activation

import java.io.File

/** Can CastBridge-TV read the shared Download folder of the USB key (files written by a PC or another app)? Android 11+ says no without « all files access ». */
enum class StorageAccess { GRANTED, MISSING }

/** The permission rule, pure: the Android side only feeds it the three facts. */
object StoragePermission {
    /**
     * API 33+: only « all files access » ([managerGranted]) opens Download. API 30..32: that, or READ_EXTERNAL_STORAGE ([readGranted]). Below 30: READ_EXTERNAL_STORAGE.
     * [managerGranted] is only meaningful from API 30 (pass false before).
     */
    fun decide(sdk: Int, managerGranted: Boolean, readGranted: Boolean): StorageAccess = when {
        sdk >= 33 -> if (managerGranted) StorageAccess.GRANTED else StorageAccess.MISSING
        sdk >= 30 -> if (managerGranted || readGranted) StorageAccess.GRANTED else StorageAccess.MISSING
        else -> if (readGranted) StorageAccess.GRANTED else StorageAccess.MISSING
    }
}

/** Which file NAMES are taken for the activation file, by whom. Names only: the content is always checked by the parser. */
object ActivationNames {
    private const val BASE = ActivationLookup.FILE_NAME
    private val AUTO = Regex("^$BASE(\\.txt|_[A-Za-z0-9._-]{0,60}\\.txt)$", RegexOption.IGNORE_CASE)
    private val PICK_EXT = setOf("txt", "text", "key", "act", "lic")

    /** Names the automatic search reads in the app's own folder (besides the exact `activation`): `activation.txt`, `activation_*.txt`. */
    fun isAutoName(name: String): Boolean = AUTO.matches(name)

    /** Files the explorer puts first: name starting with « activation », no extension or a text-like one, not empty, within [max] bytes. Binaries and big files never. */
    fun isOffered(name: String, size: Long, max: Long = ActivationLookup.MAX_BYTES.toLong()): Boolean {
        if (!name.startsWith(BASE, ignoreCase = true) || size !in 1..max) return false
        val ext = name.substringAfterLast('.', "")
        return ext.isEmpty() || ext.lowercase() in PICK_EXT
    }

    /** Extra candidates of the automatic search: auto names found in the listed OWN folders only ([dirs] and [facts] are parallel). */
    fun autoCandidates(dirs: List<Triple<File, String?, Place>>, facts: List<DirFact>): List<Candidate> =
        dirs.zip(facts).filter { (d, f) -> d.third == Place.OWN_DIR && f.names != null }
            .flatMap { (d, f) -> f.names!!.filter { it != BASE && isAutoName(it) }.sorted().map { Candidate(File(d.first, it), d.second, Place.OWN_DIR) } }
}

/** One mounted volume's readable drop folder: [dir] is its real path, [names] what is in it (null = cannot list). */
data class DropFolder(val volumeId: String?, val dir: String, val names: List<String>?)

/** Owner-facing lines about the folder where a file can always be dropped. Built from paths and file NAMES only: no content, no key. */
object DropFolders {
    const val MAX_NAMES = 6
    private const val MAX_NAME_LEN = 40

    /** `Android/data/castbridge.receiver/files/` from a real path (the part the owner browses to from the volume root). */
    fun relative(dir: String): String = (dir.substringAfter("/Android/", "").takeIf { it.isNotEmpty() }?.let { "Android/$it" } ?: ActivationLookup.OWN_DIR_TEXT).trimEnd('/') + "/"

    private fun safe(n: String) = n.filter { it.code >= 0x20 && it.code != 0x7f }.let { if (it.length > MAX_NAME_LEN) it.take(MAX_NAME_LEN - 1) + "…" else it }

    fun lines(folders: List<DropFolder>): List<String> = folders.flatMap { f ->
        val who = if (f.volumeId == null) "Stockage interne" else "Clé ${f.volumeId}"
        val first = "$who : déposez le fichier « ${ActivationLookup.FILE_NAME} » dans ${relative(f.dir)}"
        val names = f.names
        val seen = when {
            names == null -> "Android refuse de lister ce dossier"
            names.isEmpty() -> "dossier vide pour CastBridge-TV"
            else -> "CastBridge-TV y voit : " + names.sorted().take(MAX_NAMES).joinToString(", ") { safe(it) } + if (names.size > MAX_NAMES) " et ${names.size - MAX_NAMES} autre(s)" else ""
        }
        listOf(first, "   $seen")
    }
}

/** Where the explorer opens first. */
object ExplorerStart {
    /**
     * With the permission, the useful Download places first; without it Download looks empty to the app, so the app's own folder (the only readable one) comes first.
     * [ownDirs] are the app's own folders of the volumes, [roots] the volume roots.
     */
    fun places(roots: List<BrowseRoot>, ownDirs: List<File>, access: StorageAccess): List<File> {
        val downloads = roots.flatMap { listOf(File(it.dir, "Download/CastBridge"), File(it.dir, "Download")) }
        return if (access == StorageAccess.MISSING) ownDirs + downloads else downloads + ownDirs
    }
}

/** Texts of the permission rows of the explorer. */
object AccessTexts {
    const val ASK_ALL = "Autoriser l'accès à tous les fichiers"
    const val NO_SCREEN = "Ce boîtier n'a pas l'écran d'autorisation : utilisez le téléphone (Bluetooth) ou le dossier de l'application"
    const val SYSTEM = "Ouvrir l'explorateur du système"

    /** On top of the list while Android (11 and later, without « all files access ») hides from the app the files other apps or a computer put on the drive. */
    fun hidden(settingsScreen: Boolean, systemPicker: Boolean): String {
        val ways = listOfNotNull(if (settingsScreen) "« $ASK_ALL »" else null, if (systemPicker) "l'explorateur du système" else null)
        return "Android cache ici une partie des fichiers à CastBridge-TV. " +
            (if (ways.isEmpty()) "Utilisez le téléphone ou le dossier de l'application." else "Pour tout voir : " + ways.joinToString(" ou ") + ".")
    }
}
