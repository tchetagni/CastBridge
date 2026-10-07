package castbridge.desktop

import castbridge.core.owner.Activation
import castbridge.core.owner.DeviceCode
import castbridge.core.owner.Envelope
import castbridge.core.owner.Fingerprints
import castbridge.core.owner.Subject
import castbridge.core.tv.activation.ActivationLookup
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.AccessDeniedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * A refusal (a rule: [refusal] is true and NOTHING was written) or a failure (the media: some files may already be on the key) of the delivery on a client's USB key.
 * The message is French and says what to do (docs/ACTIVATION-TOOLS.md § 10).
 */
class UsbKeyException(message: String, val refusal: Boolean = true) : Exception(message)

/**
 * Is [dir] the ROOT of a mounted volume this tool may write on? Null = yes, otherwise the French reason. [dir] exists and is a folder. A temporary folder is not a mount point: the tests
 * give their own rule, the real one is [SystemVolumes].
 */
fun interface VolumeRule { fun refusal(dir: File): String? }

/** The two things of the machine the delivery depends on, replaceable by the tests: what a volume is, and the flush of the pending writes. */
class UsbPlatform(val volumeRule: VolumeRule = SystemVolumes, val sync: () -> Unit = ::flushDisks)

fun isWindows(): Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

/**
 * Best-effort flush of every pending write to the media, after each file was flushed on its own (`FileChannel.force`): `sync` on macOS and Linux (it also writes the folders that were just
 * created, which FAT needs). Windows has no such command: each file went through FlushFileBuffers and the key must be ejected before it is pulled.
 */
fun flushDisks() {
    if (isWindows()) return
    try {
        val p = ProcessBuilder("sync").redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
        if (!p.waitFor(60, TimeUnit.SECONDS)) p.destroyForcibly()
    } catch (e: Exception) {
        // no `sync` on this system: the per-file flush has been done
    }
}

/** The real rule: the ROOT of a mounted volume (a mount point on macOS and Linux, a drive letter on Windows), never the system disk, never a folder of a disk. */
object SystemVolumes : VolumeRule {
    /** Where a key is on each system (shown in every refusal). */
    const val WHERE = "Mac : /Volumes/NOM, Windows : E:\\, Linux : /media/vous/NOM"

    override fun refusal(dir: File): String? {
        val real = try { dir.toPath().toRealPath() } catch (e: IOException) { return "« ${dir.path} » est illisible (${e.message}) : donnez le chemin du volume de la clé ($WHERE)" }
        return if (isWindows()) windowsRefusal(real.toString(), System.getenv("SystemDrive")) else unixRefusal(real, dir.path)
    }

    /** Pure text, so that the Windows rule is tested on any system. [path] is the real path, [systemDrive] the value of %SystemDrive% (« C: »). */
    fun windowsRefusal(path: String, systemDrive: String?): String? {
        val m = Regex("^([A-Za-z]):[\\\\/]?(.*)$").matchEntire(path)
            ?: return "« $path » n'est pas un volume local (un lecteur comme E:\\ est attendu, pas un dossier réseau) : donnez le lecteur de la clé ($WHERE)"
        val drive = m.groupValues[1].uppercase()
        if (m.groupValues[2].isNotEmpty())
            return "« $path » n'est pas la racine d'un volume (c'est un dossier du lecteur $drive:) : donnez le lecteur de la clé lui-même, par exemple $drive:\\"
        if (systemDrive != null && systemDrive.take(1).uppercase() == drive)
            return "$drive:\\ est le disque du système, pas une clé USB : donnez le lecteur de la clé ($WHERE)"
        return null
    }

    private fun unixRefusal(real: Path, shown: String): String? {
        if (real.parent == null) return "« $shown » est le disque du système (racine /), pas une clé USB : donnez le chemin du volume de la clé ($WHERE)"
        if (!isMountPoint(real)) return "« $shown » n'est pas la racine d'un volume monté (c'est un dossier d'un disque) : donnez le volume de la clé lui-même ($WHERE)"
        return null
    }

    /** A mount point lives on another device than its parent folder (`st_dev`); without the `unix` view, the file stores are compared. */
    private fun isMountPoint(real: Path): Boolean {
        val parent = real.parent ?: return true
        val dev = unixDev(real); val parentDev = unixDev(parent)
        if (dev != null && parentDev != null) return dev != parentDev
        return try { Files.getFileStore(real) != Files.getFileStore(parent) } catch (e: IOException) { false }
    }

    private fun unixDev(p: Path): Long? = try { Files.getAttribute(p, "unix:dev") as? Long } catch (e: Exception) { null }
}

/** The cause of an I/O error in a few French words (the system's own text is English, and an access error only carries a path). */
private fun ioReason(e: Exception): String {
    val raw = (e as? FileSystemException)?.reason?.takeIf { it.isNotBlank() } ?: e.message ?: e.javaClass.simpleName
    return when {
        e is AccessDeniedException -> "accès refusé"
        e is FileAlreadyExistsException -> "existe déjà"
        e is NoSuchFileException -> "introuvable"
        raw.contains("read-only", ignoreCase = true) -> "lecture seule"
        raw.contains("no space", ignoreCase = true) -> "clé pleine"
        raw.contains("permission denied", ignoreCase = true) || raw.contains("not permitted", ignoreCase = true) -> "accès refusé"
        else -> raw
    }
}

/** What FAT, exFAT and NTFS accept as a name (the Windows rules, the strictest): no character of `" * / : < > ? \ |`, no control character, no final dot or space, no reserved device name. */
object FatNames {
    private const val FORBIDDEN = "\"*/:<>?\\|"
    private val RESERVED = setOf("CON", "PRN", "AUX", "NUL") + (1..9).flatMap { listOf("COM$it", "LPT$it") }

    /** Why [segment] cannot be the name of a file or a folder, or null. */
    fun problem(segment: String): String? = when {
        segment.isEmpty() || segment == "." || segment == ".." -> "nom vide ou réservé"
        segment.any { it < ' ' || it in FORBIDDEN } -> "caractère interdit"
        segment.endsWith(".") || segment.endsWith(" ") -> "point ou espace en fin de nom"
        segment.substringBefore('.').uppercase() in RESERVED -> "nom réservé de Windows"
        segment.length > 255 -> "nom trop long"
        else -> null
    }

    /** Checks every segment of a relative path written with `/`. */
    fun checkPath(relative: String) {
        for (s in relative.split('/')) problem(s)?.let { throw IllegalStateException("Nom impossible sur FAT, exFAT ou NTFS : « $s » dans « $relative » ($it)") }
    }
}

/**
 * The activation written on a client's USB key at the places CastBridge-TV reads (docs/TV-ACTIVATION-CLE-USB.md, voie C) :
 * `Android/data/castbridge.receiver/files/activation` (the folder of the application, the only one every Android version lets it read), `Download/CastBridge/activation`,
 * `activation` at the root (what the file explorer of the TV shows first) and a three-line `LISEZMOI-CASTBRIDGE.txt` for the client. The format of the activation is NOT touched: the same
 * line (token + newline) that `emettre` writes in `--sortie`.
 *
 * Order of the work: the volume is checked first (mounted, writable by a real test write), the files already there are looked at BEFORE anything is issued (a refusal must not use a seat),
 * then each file is written and flushed, the disks are synced, and every file is read back (same size, same SHA-256, 16 Kio at most). A TV other than the one of the new activation is never
 * overwritten without `--force`.
 */
class UsbKey private constructor(private val volume: File, private val root: Path, private val platform: UsbPlatform) {
    /** An activation file of the key that this delivery overwrites ([deviceCode] = the TV it was for, null = not an activation CastBridge can read). */
    class Replaced(val relative: String, val deviceCode: String?)

    class Placed(val relative: String, val size: Int, val sha256: String)

    /** What was done: [files] are all read back and identical. */
    class Report(val volume: File, val deviceCode: String, val files: List<Placed>, val replaced: List<Replaced>) {
        val summary: String get() = "${files.size} fichiers écrits sur ${volume.path} pour la TV $deviceCode"
    }

    /** The first line of an activation file, decoded (the TV reads the first non-empty line and nothing else). [content] is what is written: the token and a newline. */
    class Parsed(val line: String, val activation: Activation) {
        val content: String get() = line + "\n"
        val deviceCode: String get() = codeOf(activation)
    }

    private fun refused(message: String) = UsbKeyException(message, refusal = true)
    private fun failed(message: String) = UsbKeyException(message, refusal = false)

    private fun resolve(relative: String): Path = relative.split('/').fold(root) { p, s -> p.resolve(s) }

    /**
     * Looks at what the key already holds, writes nothing. Refuses a folder or a file where a folder is needed (the tool never deletes anything), and an `activation` of ANOTHER TV or that is
     * not an activation, unless [force]. Returns the files that would be replaced by [force] (empty = nothing foreign there; the same TV is simply renewed).
     */
    fun check(deviceCode: String, force: Boolean): List<Replaced> {
        for (rel in ALL_FILES) blocker(rel)?.let { throw refused("$it Rien n'a été écrit.") }
        val replaced = ArrayList<Replaced>()
        for (rel in ACTIVATION_FILES) {
            val p = resolve(rel)
            if (!Files.isRegularFile(p)) continue
            when (val found = look(p)) {
                is Found.Tv -> if (found.code != deviceCode) replaced += Replaced(rel, found.code)
                Found.Leftover -> {}                              // what an interrupted delivery of this tool leaves: nothing worth keeping, replaced without a word
                Found.Unknown -> replaced += Replaced(rel, null)
            }
        }
        if (replaced.isNotEmpty() && !force) throw refused(foreignMessage(replaced))
        return replaced
    }

    private fun foreignMessage(r: List<Replaced>): String {
        val known = r.filter { it.deviceCode != null }
        val unknown = r.filter { it.deviceCode == null }
        val parts = ArrayList<String>()
        if (known.isNotEmpty()) parts += "une activation d'une AUTRE TV (code d'appareil ${known.map { it.deviceCode }.distinct().joinToString(", ")}) dans ${known.joinToString(", ") { it.relative }}"
        if (unknown.isNotEmpty()) parts += "un fichier « activation » qui n'est pas une activation CastBridge lisible (${unknown.joinToString(", ") { it.relative }})"
        return "La clé contient déjà ${parts.joinToString(" et ")}. Rien n'a été écrit. Pour l'écraser quand même : --force."
    }

    /** What stops a file from being written: a file where a folder is needed, a folder where the file goes. Null when the way is free (or empty). */
    private fun blocker(relative: String): String? {
        var p = root
        val parts = relative.split('/')
        for ((i, s) in parts.withIndex()) {
            p = p.resolve(s)
            if (!Files.exists(p)) return null
            val shown = parts.take(i + 1).joinToString("/")
            if (i < parts.lastIndex && !Files.isDirectory(p)) return "« $shown » est un fichier sur la clé, pas un dossier : renommez-le ou déplacez-le (l'outil ne supprime rien)."
            if (i == parts.lastIndex && Files.isDirectory(p)) return "« $shown » est un dossier sur la clé : renommez-le ou supprimez-le (l'outil ne supprime rien)."
        }
        return null
    }

    /** What an `activation` file already on the key is. */
    private sealed class Found {
        /** An activation of the TV [code]. */
        class Tv(val code: String) : Found()
        /** Nothing a person wrote: empty, blanks or zeros (what a key pulled too early holds), or a CastBridge key that is cut short (it does not even decode as an envelope). */
        object Leftover : Found()
        /** Anything else: a personal file, or an intact CastBridge message that is not an activation. Never overwritten without `--force`. */
        object Unknown : Found()
    }

    private fun look(p: Path): Found {
        val bytes = try { Files.newInputStream(p).use { it.readNBytes(MAX_BYTES + 1) } } catch (e: IOException) { return Found.Unknown }
        if (bytes.size > MAX_BYTES) return Found.Unknown
        if (bytes.all { it == 0.toByte() || (it.toInt() and 0xff).toChar().isWhitespace() }) return Found.Leftover
        val line = ActivationLookup.firstLine(bytes) ?: return Found.Leftover
        Activation.decode(line)?.let { return Found.Tv(codeOf(it)) }
        return if (line.startsWith(Envelope.PREFIX + ".") && Envelope.decode(line) == null) Found.Leftover else Found.Unknown
    }

    /**
     * Writes the four files and reads them back. Throws [UsbKeyException]: a refusal (another TV there, no [force]) before anything is written, a failure (the media) otherwise, with how many
     * files are already on the key; the same command can simply be run again.
     */
    fun write(activation: Parsed, force: Boolean): Report {
        val code = activation.deviceCode
        val replaced = check(code, force)           // again: the key may have changed since the first look
        val data = activation.content.toByteArray(Charsets.UTF_8)
        if (data.size > MAX_BYTES) throw refused("Le fichier d'activation fait ${data.size} octets : 16 Kio au plus.")
        val jobs = ACTIVATION_FILES.map { it to data } + (README_NAME to README.toByteArray(Charsets.UTF_8))
        val done = ArrayList<Placed>()
        for ((rel, bytes) in jobs) {
            try { writeOne(rel, bytes) } catch (e: IOException) { throw failed(writeFailure("Écriture impossible : « $rel » (${reason(e)})", done.size, jobs.size)) }
            done += Placed(rel, bytes.size, sha256(bytes))
        }
        platform.sync()
        for ((i, p) in done.withIndex()) verify(p, jobs[i].second)
        return Report(volume, code, done, replaced)
    }

    private fun writeFailure(what: String, written: Int, total: Int) =
        "$what. La clé est peut-être pleine, protégée ou débranchée : $written fichier(s) sur $total écrit(s). Corrigez la cause puis relancez la même commande (rien n'est effacé, les fichiers déjà écrits sont simplement remplacés)."

    private fun verifyFailure(what: String) =
        "$what. La clé est peut-être abîmée, ou a été débranchée avant la fin. Essayez un autre port ou une autre clé, puis relancez la même commande (les fichiers sont remplacés, rien n'est effacé)."

    private fun reason(e: IOException): String = ioReason(e)

    private fun writeOne(relative: String, data: ByteArray) {
        val target = resolve(relative)
        Files.createDirectories(target.parent)
        if (!target.parent.toRealPath().startsWith(root)) throw IOException("le dossier sort de la clé (lien symbolique)")
        FileChannel.open(target, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING).use { ch ->
            val buf = ByteBuffer.wrap(data)
            while (buf.hasRemaining()) ch.write(buf)
            ch.force(true)
        }
    }

    /** Reads the file back from the key: same size (16 Kio at most for an activation), same SHA-256. */
    private fun verify(placed: Placed, expected: ByteArray) {
        val p = resolve(placed.relative)
        val size = try { Files.size(p) } catch (e: IOException) { throw failed(verifyFailure("Relecture impossible : « ${placed.relative} » (${reason(e)})")) }
        if (size != expected.size.toLong() || (placed.relative in ACTIVATION_FILES && size > MAX_BYTES))
            throw failed(verifyFailure("Vérification échouée : « ${placed.relative} » relu avec $size octets au lieu de ${expected.size}"))
        val back = try { Files.readAllBytes(p) } catch (e: IOException) { throw failed(verifyFailure("Relecture impossible : « ${placed.relative} » (${reason(e)})")) }
        if (sha256(back) != placed.sha256) throw failed(verifyFailure("Vérification échouée : « ${placed.relative} » relu avec un contenu différent de l'original"))
    }

    companion object {
        /** What the TV accepts (`ActivationLookup.MAX_BYTES`, 16 Kio). */
        const val MAX_BYTES = ActivationLookup.MAX_BYTES
        const val README_NAME = "LISEZMOI-CASTBRIDGE.txt"

        /** The three places of the activation, in the order they are written: the folder of the application first (readable by the TV on every Android version), then the shared folder, then the root. */
        val OWN_FILE = "${ActivationLookup.OWN_DIR_TEXT}/${ActivationLookup.FILE_NAME}"
        val DOWNLOAD_FILE = "Download/CastBridge/${ActivationLookup.FILE_NAME}"
        val ROOT_FILE = ActivationLookup.FILE_NAME
        val ACTIVATION_FILES = listOf(OWN_FILE, DOWNLOAD_FILE, ROOT_FILE)
        val ALL_FILES = ACTIVATION_FILES + README_NAME

        /** The note for the client: three lines. */
        val README_LINES = listOf(
            "CastBridge-TV : branchez cette clé USB sur la TV (n'importe quelle prise USB).",
            "L'écran d'activation reconnaît la clé tout seul : suivez ce qu'il affiche.",
            "Si rien ne s'affiche, ouvrez l'écran d'activation et choisissez « Chercher la clé USB ».",
        )
        val README: String = README_LINES.joinToString("\n") + "\n"

        init { ALL_FILES.forEach(FatNames::checkPath) }

        /** The readable device code of the TV an activation is for (the factors it signs, hashed like the TV does). */
        fun codeOf(a: Activation): String = DeviceCode.of(Fingerprints(a.factors))

        /**
         * What the owner typed after `--cle-usb`, as a folder. On Windows « E: » means « the current folder of drive E » and « "E:\" » reaches the program as « E:" » (the backslash escapes the
         * quote of cmd.exe): both are read as the root « E:\ ».
         */
        fun volumeArg(text: String, windows: Boolean = isWindows()): File {
            var s = text.trim()
            if (windows) {
                s = s.removeSuffix("\"")
                if (Regex("^[A-Za-z]:$").matches(s)) s += "\\"
            }
            return File(s)
        }

        /** The activation in [bytes] (a file as `emettre` writes it, BOM and CRLF tolerated), for a TV. Refuses what the TV would not read: too big, empty, not a token, a phone's activation. */
        fun parse(bytes: ByteArray): Parsed {
            if (bytes.size > MAX_BYTES) throw UsbKeyException("Le fichier d'activation fait plus de 16 Kio ($MAX_BYTES octets) : ce n'est pas une activation.")
            val line = ActivationLookup.firstLine(bytes) ?: throw UsbKeyException("Le fichier d'activation est vide.")
            val act = Activation.decode(line) ?: throw UsbKeyException("Ce fichier ne contient pas d'activation CastBridge (jeton « cbx1.… » attendu en première ligne).")
            if (act.subject != Subject.TV) throw UsbKeyException("Cette activation est celle d'un téléphone : la clé USB est lue par la TV.")
            return Parsed(line, act)
        }

        /** Opens the key: the folder exists, is the root of a mounted volume ([UsbPlatform.volumeRule]) and takes a real write (a test file, removed at once). Writes nothing else. */
        fun open(volume: File, platform: UsbPlatform = UsbPlatform()): UsbKey {
            if (!volume.exists()) throw UsbKeyException("Clé USB introuvable : « ${volume.path} » n'existe pas. Branchez la clé, attendez qu'elle apparaisse, puis donnez le chemin de son volume (${SystemVolumes.WHERE}).")
            if (!volume.isDirectory) throw UsbKeyException("« ${volume.path} » n'est pas un dossier : donnez le chemin du volume de la clé (${SystemVolumes.WHERE}).")
            platform.volumeRule.refusal(volume)?.let { throw UsbKeyException(it) }
            val root = try { volume.toPath().toRealPath() } catch (e: IOException) { throw UsbKeyException("« ${volume.path} » est illisible (${e.message}).") }
            probe(volume, root)
            return UsbKey(volume, root, platform)
        }

        /** A real write: a one-byte file at the root, flushed, then removed. `canWrite()` lies on read-only mounts and locked cards. */
        private fun probe(volume: File, root: Path) {
            val test = root.resolve(".castbridge-ecriture-" + UUID.randomUUID().toString().take(8) + ".tmp")
            try {
                FileChannel.open(test, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { it.write(ByteBuffer.wrap(byteArrayOf(0x43))); it.force(true) }
            } catch (e: IOException) {
                throw UsbKeyException(notWritable(volume, ioReason(e)))
            } catch (e: SecurityException) {
                throw UsbKeyException(notWritable(volume, "accès refusé"))
            } finally {
                try { Files.deleteIfExists(test) } catch (e: Exception) { /* nothing to do: a stray hidden file of one byte */ }
            }
        }

        private fun notWritable(volume: File, why: String) =
            "La clé « ${volume.path} » n'est pas inscriptible ($why). Vérifiez le verrou de la clé (carte SD), son formatage (macOS n'écrit pas sur une clé NTFS : utilisez FAT32 ou exFAT) " +
                "et, sous macOS, l'autorisation du Terminal pour les volumes amovibles (Réglages Système › Confidentialité et sécurité). Rien n'a été écrit."

        private fun sha256(b: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
    }
}
