package castbridge.core.tv.activation

import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException

/** Where a candidate file lives: the shared Download folder of a volume (not readable by the app under scoped storage) or the app's own folder of that volume (always readable). */
enum class Place { DOWNLOAD, OWN_DIR }

/** What was found at one candidate path. Never carries the content. */
enum class Probe { ABSENT, UNREADABLE, EMPTY, TOO_BIG, NOT_VALID, WRONG_DEVICE, EXPIRED, ACCEPTED }

/** The verdict the receiver gives to the first line of a readable file (it maps `ActivationResult`; the core never sees the key's text again). */
enum class Verdict { ACCEPTED, NOT_VALID, WRONG_DEVICE, EXPIRED }

data class Candidate(val file: File, val volumeId: String?, val place: Place)
data class VolumeFact(val id: String, val readOnly: Boolean)
data class ProbeFact(val place: Place, val volumeId: String?, val probe: Probe)
/** [names] null = listing denied; [exists] false = the folder is not there. */
data class DirFact(val place: Place, val volumeId: String?, val exists: Boolean, val names: List<String>?)

data class LookupFacts(val volumes: List<VolumeFact>, val probes: List<ProbeFact>, val dirs: List<DirFact>)

/** The only door to the file system of the lookup: read-only by construction (no write method exists). Tests replace it. */
interface LookupFs {
    fun isDir(f: File): Boolean
    /** Names in [dir], null when the system refuses to list. */
    fun list(dir: File): List<String>?
    /** File size; -1 when it does not exist. May throw SecurityException. */
    fun length(f: File): Long
    /** The first [max] bytes; throws FileNotFoundException (EACCES) or SecurityException when refused. */
    fun read(f: File, max: Int): ByteArray
}

object RealLookupFs : LookupFs {
    override fun isDir(f: File) = runCatching { f.isDirectory }.getOrDefault(false)
    override fun list(dir: File): List<String>? = try { dir.list()?.toList() } catch (e: SecurityException) { null }
    override fun length(f: File): Long = if (f.isFile) f.length() else -1
    override fun read(f: File, max: Int): ByteArray = FileInputStream(f).use { castbridge.core.util.BoundedRead.readAll(it, max) }
}

/**
 * Lookup of the `activation` file on the volumes of the TV. Read-only, text only (first non-empty line, 16 kB cap), the file is never executed, only the first line is handed to [run]'s verifier.
 * Candidates: `<Download>/CastBridge/activation`, `<Download>/activation`, then `<app files dir of each volume>/activation` and `.../CastBridge/activation`.
 */
object ActivationLookup {
    const val FILE_NAME = "activation"
    const val MAX_BYTES = 16_384
    /** Shown to the owner (the real path is `<clé>/Android/data/castbridge.receiver/files/activation`). */
    const val OWN_DIR_TEXT = "Android/data/castbridge.receiver/files"

    fun candidates(downloadDirs: List<Pair<File, String?>>, ownDirs: List<Pair<File, String?>>): List<Candidate> {
        val out = LinkedHashMap<String, Candidate>()
        for ((d, v) in downloadDirs) for (rel in listOf("CastBridge/$FILE_NAME", FILE_NAME)) File(d, rel).let { out.putIfAbsent(it.path, Candidate(it, v, Place.DOWNLOAD)) }
        for ((d, v) in ownDirs) for (rel in listOf(FILE_NAME, "CastBridge/$FILE_NAME")) File(d, rel).let { out.putIfAbsent(it.path, Candidate(it, v, Place.OWN_DIR)) }
        return out.values.toList()
    }

    /** The folders whose names are listed to spot a badly named file (the CastBridge folder and the folder itself, for each place). */
    fun dirsToList(downloadDirs: List<Pair<File, String?>>, ownDirs: List<Pair<File, String?>>): List<Triple<File, String?, Place>> =
        (downloadDirs.flatMap { (d, v) -> listOf(Triple(File(d, "CastBridge"), v, Place.DOWNLOAD), Triple(d, v, Place.DOWNLOAD)) } +
            ownDirs.flatMap { (d, v) -> listOf(Triple(d, v, Place.OWN_DIR), Triple(File(d, "CastBridge"), v, Place.OWN_DIR)) }).distinctBy { it.first.path }

    /** First non-empty line of [bytes] (BOM, CRLF tolerated), or null if there is none. */
    fun firstLine(bytes: ByteArray): String? =
        String(bytes, Charsets.UTF_8).removePrefix("﻿").lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }

    private fun denied(e: Exception) = e is SecurityException || (e is FileNotFoundException && e.message.orEmpty().let { "EACCES" in it || "Permission denied" in it })

    /** State of one candidate before any verification; [line] is the first line when readable. */
    private fun read(fs: LookupFs, f: File): Pair<Probe, String?> {
        try {
            val len = fs.length(f)
            if (len > MAX_BYTES) return Probe.TOO_BIG to null
            val bytes = fs.read(f, MAX_BYTES + 1)
            if (bytes.size > MAX_BYTES) return Probe.TOO_BIG to null
            val line = firstLine(bytes) ?: return Probe.EMPTY to null
            return Probe.NOT_VALID to line         // placeholder: replaced by the verdict
        } catch (e: FileNotFoundException) { return (if (denied(e)) Probe.UNREADABLE else Probe.ABSENT) to null
        } catch (e: SecurityException) { return Probe.UNREADABLE to null
        } catch (e: IOException) { return Probe.UNREADABLE to null }
    }

    class Outcome(val facts: LookupFacts, val accepted: Boolean, val decided: Boolean)

    /**
     * Walks [candidates] in order. A readable file is handed to [verify]; the first ACCEPTED wins (a refused or empty file does not hide a later good one).
     * [verify] is called at most once per file and its argument must never be logged.
     */
    fun run(
        candidates: List<Candidate>, dirs: List<Triple<File, String?, Place>>, volumes: List<VolumeFact>,
        fs: LookupFs = RealLookupFs, verify: (String) -> Verdict,
    ): Outcome {
        val probes = ArrayList<ProbeFact>(); var accepted = false; var decided = false
        for (c in candidates) {
            val (p, line) = read(fs, c.file)
            val probe = if (line == null) p else when (verify(line)) {
                Verdict.ACCEPTED -> Probe.ACCEPTED; Verdict.WRONG_DEVICE -> Probe.WRONG_DEVICE; Verdict.EXPIRED -> Probe.EXPIRED; Verdict.NOT_VALID -> Probe.NOT_VALID
            }
            probes += ProbeFact(c.place, c.volumeId, probe)
            if (probe == Probe.ACCEPTED) { accepted = true; decided = true; break }
            if (probe in setOf(Probe.NOT_VALID, Probe.WRONG_DEVICE, Probe.EXPIRED)) decided = true
        }
        val dirFacts = if (accepted) emptyList() else dirs.map { (d, v, pl) ->
            if (!fs.isDir(d)) DirFact(pl, v, false, null) else DirFact(pl, v, true, fs.list(d))
        }
        return Outcome(LookupFacts(volumes, probes, dirFacts), accepted, decided)
    }
}

/** French lines for the activation screen, built only from facts: paths, counts and file NAMES that match a strict whitelist, never any content or key text. */
object ActivationLookupReport {
    const val MAX_LINES = 6
    private const val DL = "Download/CastBridge"

    /** Names that look like a badly named `activation` file; anything else starting with « activation » is reported without being echoed. */
    private val MISNAMED = Regex("^activation( ?\\(\\d{1,3}\\)| - cop(ie|y)| cop(ie|y))?(\\.[a-z0-9]{1,5}){0,2}$", RegexOption.IGNORE_CASE)

    /** Misnamed files among [names] (exact `activation` excluded), as owner-facing lines. */
    fun misnamed(names: List<String>): List<String> {
        val out = ArrayList<String>(); var odd = false
        for (n in names.sorted()) {
            if (n == ActivationLookup.FILE_NAME || !n.startsWith(ActivationLookup.FILE_NAME, ignoreCase = true)) continue
            if (!MISNAMED.matches(n)) { odd = true; continue }
            out += if (n.lowercase().endsWith(".zip")) "Fichier « $n » : décompressez-le, puis nommez le fichier obtenu « activation »"
            else "Fichier trouvé sous le nom « $n » : renommez-le « activation »"
        }
        if (odd) out += "Un fichier dont le nom commence par « activation » n'a pas le nom exact : nommez-le « activation » (sans extension)"
        return out
    }

    fun lines(f: LookupFacts): List<String> {
        val out = ArrayList<String>()
        val keys = f.volumes
        out += when {
            keys.isEmpty() -> "Aucune clé USB détectée : branchez-la sur la TV"
            else -> (if (keys.size == 1) "Clé détectée : " else "Clés détectées : ") +
                keys.take(3).joinToString(", ") { it.id + if (it.readOnly) " (lecture seule)" else "" } + if (keys.size > 3) " et ${keys.size - 3} autre(s)" else ""
        }
        val seen = f.probes.any { it.probe == Probe.ACCEPTED }
        if (f.dirs.isNotEmpty()) {
            val dl = f.dirs.filter { it.place == Place.DOWNLOAD && it.exists && it.volumeId != null }
            if (dl.isNotEmpty()) out += "dossier $DL : " + count(dl)
            val own = f.dirs.filter { it.place == Place.OWN_DIR && it.exists && it.volumeId != null }
            if (own.isNotEmpty()) out += "dossier ${ActivationLookup.OWN_DIR_TEXT} : " + count(own)
        }
        if (!seen) f.dirs.filter { it.names != null }.flatMap { misnamed(it.names!!) }.distinct().take(2).forEach { out += it }
        out += result(f.probes.map { it.probe })
        return out.take(MAX_LINES)
    }

    private fun count(dirs: List<DirFact>): String {
        val listed = dirs.filter { it.names != null }
        if (listed.isEmpty()) return "Android refuse de lister son contenu"
        val n = listed.sumOf { it.names!!.size }
        return when (n) { 0 -> "aucun élément visible"; 1 -> "1 élément visible"; else -> "$n éléments visibles" }
    }

    private fun result(p: List<Probe>): String {
        val s = p.toSet()
        return when {
            Probe.ACCEPTED in s -> "fichier « activation » : trouvé, vérification de la clé…"
            Probe.WRONG_DEVICE in s -> "fichier « activation » : lu, mais cette clé est celle d'une autre TV (vérifiez le code d'appareil donné)"
            Probe.EXPIRED in s -> "fichier « activation » : lu, mais la clé est périmée (valable 48 h) : demandez-en une nouvelle"
            Probe.NOT_VALID in s -> "fichier « activation » : lu, mais ce n'est pas une clé valable (texte incomplet ou abîmé)"
            Probe.UNREADABLE in s -> "fichier « activation » : présent mais Android refuse de le lire : déposez-le dans ${ActivationLookup.OWN_DIR_TEXT}/activation, ou utilisez le téléphone"
            Probe.TOO_BIG in s -> "fichier « activation » : trop gros (16 ko au plus), ce n'est pas une clé"
            Probe.EMPTY in s -> "fichier « activation » : vide"
            else -> "fichier « activation » : introuvable (Android ne laisse pas CastBridge-TV lire un fichier déposé dans Download : déposez-le ici : ${ActivationLookup.OWN_DIR_TEXT}/activation, ou utilisez le téléphone)"
        }
    }
}
