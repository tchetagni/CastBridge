package castbridge.core.tv

import castbridge.core.dl.Json
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Un espace par TV sur la clé : `<clé>/Download/CastBridge/<id>/{Bibliotheque,Telechargements,Medias,index}`
 * (docs/coordination/DESIGN-USB-ESPACE-PAR-TV-2026-11-04.md). L'identifiant dérive du code d'appareil (jamais écrit en clair) ; une TV ne lit
 * ni n'écrit jamais dans l'espace d'une autre. Pur : aucune dépendance Android.
 */
object UsbSpace {
    const val MARK = ".espace"
    const val CLAIM = ".adoption"
    const val ID_LENGTH = 12
    const val MIN_FREE_BYTES = 1L shl 20
    const val CLAIM_STALE_MS = 24L * 3600 * 1000
    private const val MAX_MARK_BYTES = 1024
    private val ID = Regex("^[0-9a-f]{12}$")

    sealed class Ensured {
        class Ready(val dir: File) : Ensured()
        object ReadOnly : Ensured()
        object Full : Ensured()
        class Failed(val reason: String) : Ensured()
    }

    /** Majuscules, lettres et chiffres seulement (tirets, espaces ignorés). */
    fun normalize(code: String): String = code.filter { it.isLetterOrDigit() }.uppercase()

    /** 12 premiers hexadécimaux de SHA-256(`castbridge-espace-v1|` + code normalisé) ; null si le code est vide. */
    fun idOf(deviceCode: String): String? {
        val n = normalize(deviceCode)
        if (n.isEmpty()) return null
        val d = MessageDigest.getInstance("SHA-256").digest("castbridge-espace-v1|$n".toByteArray(Charsets.UTF_8))
        return d.joinToString("") { "%02x".format(it) }.take(ID_LENGTH)
    }

    fun isId(name: String): Boolean = ID.matches(name)
    fun dirOf(root: File, id: String): File = File(root, id)

    /** (identifiant, date de création) de la marque de [dir] ; null si absente, liée, trop grosse ou illisible. */
    fun readMark(dir: File): Pair<String, Long?>? {
        val f = File(dir, MARK)
        if (!f.isFile || UsbPaths.isSymlink(f) || f.length() > MAX_MARK_BYTES) return null
        return try {
            val m = Json.parse(f.readText(Charsets.UTF_8)) as? Map<*, *> ?: return null
            val id = m["id"] as? String ?: return null
            id to (m["createdMs"] as? Number)?.toLong()
        } catch (e: Exception) { null }
    }

    private fun complete(dir: File) = dir.isDirectory && UsbLayout.SUBDIRS.all { File(dir, it).isDirectory }

    /**
     * Crée (si besoin) l'espace de [id] et sa marque. Jamais sur une clé en lecture seule ([writable] faux) ni pleine ([free] sous
     * [MIN_FREE_BYTES]) : dans ces cas rien n'est créé, sauf si l'espace existe déjà entier (alors lisible, `Ready`).
     */
    fun ensure(root: File, id: String, nowMs: Long, writable: Boolean = true, free: Long = Long.MAX_VALUE): Ensured {
        if (!isId(id)) return Ensured.Failed("invalid space id")
        val dir = dirOf(root, id)
        if (UsbPaths.isSymlink(root) || UsbPaths.isSymlink(dir)) return Ensured.Failed("symbolic link")
        val mark = readMark(dir)
        if (dir.isDirectory && mark != null) {
            if (mark.first != id) return Ensured.Failed("mark does not match the folder")
            if (complete(dir)) return Ensured.Ready(dir)
        } else if (dir.exists()) {
            val others = dir.list().orEmpty().filter { it !in UsbLayout.SUBDIRS && it != MARK }
            if (!dir.isDirectory || others.isNotEmpty()) return Ensured.Failed("folder under my id is not mine")
        }
        if (!writable) return Ensured.ReadOnly
        if (free in 0 until MIN_FREE_BYTES) return Ensured.Full
        return try {
            if (!(dir.isDirectory || dir.mkdirs())) return Ensured.ReadOnly
            if (mark == null) {
                val text = Json.write(linkedMapOf<String, Any?>("id" to id, "createdMs" to StoreIndex.plausible(nowMs)))
                AtomicFile.write(File(dir, MARK), text.toByteArray(Charsets.UTF_8))
            }
            if (UsbLayout.ensure(dir)) Ensured.Ready(dir) else Ensured.ReadOnly
        } catch (e: IOException) { Ensured.Failed(e.message ?: e.javaClass.simpleName) }
        catch (e: SecurityException) { Ensured.ReadOnly }
    }

    /** Identifiants des espaces d'AUTRES TV (dossier à nom d'identifiant portant une marque qui le confirme). Ne regarde rien d'autre dans ces dossiers. */
    fun foreign(root: File, myId: String): List<String> =
        (try { root.listFiles() } catch (e: SecurityException) { null }).orEmpty()
            .filter { it.isDirectory && !UsbPaths.isSymlink(it) && isId(it.name) && it.name != myId && readMark(it)?.first == it.name }
            .map { it.name }.sorted()

    /** Vrai si un des quatre anciens dossiers à plat de [root] contient au moins un fichier (les dossiers vides ne sont pas des données). */
    fun hasLegacy(root: File): Boolean {
        fun anyFile(d: File, depth: Int): Boolean {
            if (depth > UsbPaths.MAX_DEPTH) return false
            for (f in d.listFiles().orEmpty()) {
                if (UsbPaths.isSymlink(f)) continue
                if (f.isFile || (f.isDirectory && anyFile(f, depth + 1))) return true
            }
            return false
        }
        return UsbLayout.SUBDIRS.any { val d = File(root, it); d.isDirectory && !UsbPaths.isSymlink(d) && anyFile(d, 1) }
    }
}

/**
 * Adoption des anciennes données à plat (`CastBridge/{Bibliotheque,...}`) par l'espace de CETTE TV : DÉPLACEMENT par renommage atomique dans le
 * même volume. Jamais de copie suivie d'une suppression, jamais de suppression de donnée, jamais d'écrasement ; l'état est dans le système de
 * fichiers, donc [run] se reprend à n'importe quelle étape sans perte ni doublon.
 */
class UsbAdoption(
    private val root: File,
    private val id: String,
    private val fs: Fs,
    private val writable: Boolean = true,
    /** Point d'injection de test : appelé avec « étape:chemin » avant chaque étape ; peut lancer IOException (clé retirée). */
    private val fault: (String) -> Unit = {},
    private val now: () -> Long = System::currentTimeMillis,
    private val free: (File) -> Long = { it.usableSpace },
) {
    data class Move(val rel: String, val size: Long)
    data class Skipped(val rel: String, val reason: String)
    data class Plan(val moves: List<Move>, val conflicts: List<Skipped>, val ignored: List<Skipped>, val truncated: Boolean) {
        val bytes: Long get() = moves.sumOf { it.size }
    }
    enum class Status { NOTHING, NOT_CONFIRMED, DONE, DONE_WITH_CONFLICTS, REFUSED_READ_ONLY, REFUSED_FULL, REFUSED_CLAIMED, INTERRUPTED, FAILED }
    data class Report(val status: Status, val moved: Int, val conflicts: List<Skipped>, val ignored: List<Skipped>, val failures: List<Skipped>)

    private val space get() = UsbSpace.dirOf(root, id)

    /** Ce qui serait déplacé, sans rien toucher (montré à l'utilisateur avant la confirmation). */
    fun plan(maxEntries: Int = 100_000): Plan {
        val moves = ArrayList<Move>(); val conflicts = ArrayList<Skipped>(); val ignored = ArrayList<Skipped>()
        val seen = HashSet<String>(); var entries = 0; var truncated = false

        fun walk(dir: File, rel: String, depth: Int) {
            val list = try { dir.listFiles() } catch (e: SecurityException) { null } ?: return
            for (f in list.sortedBy { it.name }) {
                if (truncated) return
                if (++entries > maxEntries) { truncated = true; return }
                val path = "$rel/${f.name}"
                when {
                    UsbPaths.isSymlink(f) -> ignored += Skipped(path, "symbolic link")
                    UsbPaths.badSegment(f.name) != null -> ignored += Skipped(path, UsbPaths.badSegment(f.name)!!)
                    f.isDirectory -> if (depth + 1 > UsbPaths.MAX_DEPTH) ignored += Skipped(path, "too deep") else walk(f, path, depth + 1)
                    f.isFile -> {
                        val key = if (fs.caseInsensitive) path.lowercase() else path
                        when {
                            UsbPaths.isExecutable(f.name) -> ignored += Skipped(path, "executable ignored")
                            f.length() > fs.maxFileBytes -> ignored += Skipped(path, "file too large for ${fs.label}")
                            !seen.add(key) -> conflicts += Skipped(path, "name differs only by case")
                            File(space, path).exists() -> conflicts += Skipped(path, "a file with that name is already in this TV's space")
                            else -> moves += Move(path, f.length())
                        }
                    }
                }
            }
        }
        for (s in UsbLayout.SUBDIRS) {
            val d = File(root, s)
            if (d.isDirectory && !UsbPaths.isSymlink(d)) walk(d, s, 2)
        }
        return Plan(moves, conflicts, ignored, truncated)
    }

    private fun claimFile() = File(root, UsbSpace.CLAIM)

    private fun readClaim(): Pair<String, Long>? {
        val f = claimFile()
        if (!f.isFile || UsbPaths.isSymlink(f) || f.length() > 1024) return null
        return try {
            val m = Json.parse(f.readText(Charsets.UTF_8)) as? Map<*, *> ?: return null
            (m["id"] as? String ?: return null) to ((m["startedMs"] as? Number)?.toLong() ?: return null)
        } catch (e: Exception) { null }
    }

    /** Vrai si cette TV détient maintenant la revendication (créée en exclusivité, reprise si c'est la sienne ou si elle est périmée). */
    private fun acquireClaim(): Boolean {
        val f = claimFile()
        val body = Json.write(linkedMapOf<String, Any?>("id" to id, "startedMs" to now())).toByteArray(Charsets.UTF_8)
        for (attempt in 0..1) {
            try {
                Files.createFile(f.toPath()); java.io.FileOutputStream(f).use { it.write(body); it.fd.sync() }
                return true
            } catch (e: FileAlreadyExistsException) {
                val c = readClaim()
                val mine = c?.first == id
                val age = if (c != null) now() - c.second else now() - f.lastModified()
                if (!mine && age in 0 until UsbSpace.CLAIM_STALE_MS) return false
                if (mine) return true
                if (!f.delete()) return false                                  // périmée : on la remplace, puis on réessaie en exclusivité
            }
        }
        return false
    }

    private fun report(s: Status, moved: Int = 0, p: Plan? = null, failures: List<Skipped> = emptyList()) =
        Report(s, moved, p?.conflicts.orEmpty(), p?.ignored.orEmpty(), failures)

    /** Exécute l'adoption. [confirmed] = « Adopter » explicitement choisi (jamais par défaut). [alive] : la clé est-elle toujours là ? */
    fun run(confirmed: Boolean, alive: () -> Boolean = { true }): Report {
        if (!confirmed) return report(Status.NOT_CONFIRMED)
        val p = plan()
        if (p.truncated) return report(Status.FAILED, p = p, failures = listOf(Skipped("", "too many entries")))
        if (p.moves.isEmpty() && p.conflicts.isEmpty()) {
            tidy()
            if (readClaim()?.first == id) claimFile().delete()                       // reprise d'une adoption coupée juste avant la levée de la revendication
            return report(Status.NOTHING, p = p)
        }
        try {
            if (!writable) return report(Status.REFUSED_READ_ONLY, p = p)
            if (free(root) < UsbSpace.MIN_FREE_BYTES) return report(Status.REFUSED_FULL, p = p)
            fault("claim")
            if (!acquireClaim()) return report(Status.REFUSED_CLAIMED, p = p)
            fault("mark")
            when (val e = UsbSpace.ensure(root, id, now(), writable, free(root))) {
                is UsbSpace.Ensured.Ready -> {}
                UsbSpace.Ensured.ReadOnly -> return report(Status.REFUSED_READ_ONLY, p = p)
                UsbSpace.Ensured.Full -> return report(Status.REFUSED_FULL, p = p)
                is UsbSpace.Ensured.Failed -> return report(Status.FAILED, p = p, failures = listOf(Skipped("", e.reason)))
            }
            var moved = 0; val failures = ArrayList<Skipped>()
            for (m in p.moves) {
                fault("move:${m.rel}")
                if (!alive()) return report(Status.INTERRUPTED, moved, p, failures)
                val src = File(root, m.rel); val dst = UsbPaths.resolve(space, m.rel)
                if (dst == null) { failures += Skipped(m.rel, "unsafe path"); continue }
                if (!src.isFile || UsbPaths.isSymlink(src)) continue                      // déjà déplacé (reprise) ou disparu
                if (dst.exists()) { failures += Skipped(m.rel, "destination appeared"); continue }
                fault("mkdir:${m.rel}")
                if (!(dst.parentFile.isDirectory || dst.parentFile.mkdirs())) { failures += Skipped(m.rel, "cannot create folder"); continue }
                try {
                    Files.move(src.toPath(), dst.toPath(), StandardCopyOption.ATOMIC_MOVE); moved++
                } catch (e: AtomicMoveNotSupportedException) { failures += Skipped(m.rel, "atomic move unavailable")      // jamais de copie de repli
                } catch (e: IOException) { if (!alive()) return report(Status.INTERRUPTED, moved, p, failures); failures += Skipped(m.rel, e.message ?: "move failed") }
            }
            fault("cleanup")
            tidy()
            fault("release")
            if (readClaim()?.first == id) claimFile().delete()
            val s = if (failures.isEmpty() && p.conflicts.isEmpty()) Status.DONE else Status.DONE_WITH_CONFLICTS
            return report(s, moved, p, failures)
        } catch (e: IOException) {
            return report(Status.INTERRUPTED, p = p)
        }
    }

    /** Retire les dossiers VIDES de l'ancienne arborescence (jamais récursif sur du contenu). */
    private fun tidy() {
        fun prune(d: File) {
            for (c in d.listFiles().orEmpty()) if (c.isDirectory && !UsbPaths.isSymlink(c)) prune(c)
            d.delete()                                                                 // échoue (sans effet) si non vide
        }
        for (s in UsbLayout.SUBDIRS) { val d = File(root, s); if (d.isDirectory && !UsbPaths.isSymlink(d)) prune(d) }
    }
}
