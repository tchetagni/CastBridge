package castbridge.core.tv

import castbridge.core.library.agent.FileRef
import castbridge.core.library.agent.Kind
import castbridge.core.library.agent.Labels
import castbridge.core.library.agent.Media
import castbridge.core.library.agent.NameParser
import castbridge.core.library.agent.Namer
import castbridge.core.library.agent.Origin
import castbridge.core.library.agent.SafeName
import java.io.File

/**
 * Rangement réel à la réception (docs/LIBRARY-AGENT.md, « Rangement à la réception »; docs/STORAGE.md).
 *
 * Pure (no disk, no clock, no network): from what a phone sent (name, MIME, size, optional metadata) to the real folder and the clean name
 * the file gets on the TV volume (`Films`, `Séries/<Titre>/Saison NN`, `Musique`, `Photos`, `Captures`, `Documents`, `Applications`,
 * `Archives`, `Cours`, `Famille`, and `À trier` as the safe default), using the same [NameParser] / [Namer] as the phone's library assistant,
 * so the phone and the TV always agree. Only what is sure is renamed: a name that does not parse well keeps its own name (cleaned) in « À trier ».
 *
 * Rules that never bend: a name stays unique on the whole TV (a collision gets " (2)", " (3)"… and nothing is ever overwritten); every path goes through
 * [UsbPaths] / [SafeName] (no `..`, no separator, depth and length limits, 255 bytes per segment); a file larger than the volume's file system can hold (FAT32: 4 GB)
 * is never placed; an installer or a package that another feature reads by its flat name is [Result.keepFlat] (never moved on reception).
 */
object Filing {
    const val FAT32_MAX = (4L shl 30) - 1
    /** Under this confidence of the parser the name is not trusted: the file goes to « À trier » and keeps its own name. */
    const val MIN_CONFIDENCE = 0.7
    /** A film is recognised by its year (0.8 and more); a title with only quality tags (« One.Piece.1045.VOSTFR », 0.7) is not trusted as a film. */
    const val MOVIE_CONFIDENCE = 0.8
    private const val NAME_LIMIT = 250
    private const val MAX_SUFFIX = 999

    enum class Category { FILMS, SERIES, MUSIC, PHOTOS, CAPTURES, DOCUMENTS, APPS, ARCHIVES, COURSES, FAMILY, TO_SORT }

    /** Optional extra knowledge: duration of the video (helps the parser), "2024-03-15" date of the file for camera names without a date. */
    data class Meta(val durationMs: Long = 0, val mtimeDate: String? = null)

    /** [folder] relative to the volume's folder ("" = root, only for [keepFlat]); [name] the complete clean name; [rule] why (parser rule id, or "flat"/"default"). */
    data class Result(val category: Category, val folder: String, val name: String, val rule: String, val keepFlat: Boolean = false, val note: String = "")

    /** Where a file goes on a given volume, name made unique and valid for the file system. [rel] = "Films/Titre (2010).mkv". */
    data class Placement(val folder: String, val name: String, val rel: String, val renamedForCollision: Boolean)

    /** Top-level folders of every language the app writes: what the index re-reads from the disk. */
    val ROOTS: Set<String> by lazy {
        listOf("fr", "en").flatMap { lang ->
            Labels(lang).let { listOf(it.movies, it.series, it.music, it.photos, it.captures, it.documents, it.apps, it.archives, it.courses, it.family, it.toSort) }
        }.toSet()
    }

    private val PACKAGES = listOf(".learn.zip", ".lot.zip", ".quiz.zip")
    private val APP_FAMILY = setOf("apk", "xapk", "apks", "apkm", "aab")
    private val MIME_EXT = mapOf(
        "video/mp4" to "mp4", "video/x-matroska" to "mkv", "video/webm" to "webm", "video/quicktime" to "mov", "video/x-msvideo" to "avi", "video/3gpp" to "3gp", "video/mpeg" to "mpg",
        "audio/mpeg" to "mp3", "audio/mp4" to "m4a", "audio/aac" to "aac", "audio/flac" to "flac", "audio/ogg" to "ogg", "audio/opus" to "opus", "audio/wav" to "wav", "audio/x-wav" to "wav",
        "image/jpeg" to "jpg", "image/png" to "png", "image/webp" to "webp", "image/heic" to "heic", "image/gif" to "gif",
        "application/pdf" to "pdf", "application/zip" to "zip", "application/vnd.android.package-archive" to "apk", "text/plain" to "txt",
    )

    /** The name a phone sent, reduced to one safe path segment (never a path): last segment, no control characters, trimmed. */
    fun baseName(raw: String): String =
        raw.substringAfterLast('/').substringAfterLast('\\').filter { it.code >= 0x20 && it.code != 0x7f }.trim().trimEnd('.', ' ')

    /**
     * Files another feature finds by their FLAT name (phone installs an APK it just sent, packs of « Apprendre », lots, quiz packs, and their proofs):
     * they are never filed on reception. « Ranger ma bibliothèque » may file the installers later, on demand.
     */
    fun keepsFlat(name: String): Boolean {
        val l = name.lowercase()
        if (PACKAGES.any { l.endsWith(it) }) return true
        if (l.substringAfterLast('.', "") in APP_FAMILY) return true
        return castbridge.core.lots.LotNames.parseFileName(name.removeSuffix(castbridge.core.lots.LotNames.PROOF_SUFFIX)) != null
    }

    private fun top(c: Category, l: Labels) = when (c) {
        Category.FILMS -> l.movies; Category.SERIES -> l.series; Category.MUSIC -> l.music; Category.PHOTOS -> l.photos
        Category.CAPTURES -> l.captures; Category.DOCUMENTS -> l.documents; Category.APPS -> l.apps; Category.ARCHIVES -> l.archives
        Category.COURSES -> l.courses; Category.FAMILY -> l.family; Category.TO_SORT -> l.toSort
    }

    /** Which category, which folder and which clean name for [rawName]. Deterministic, idempotent (classifying the result again changes nothing). */
    fun classify(rawName: String, mime: String? = null, size: Long = 0, meta: Meta = Meta(), lang: String = "fr", currentYear: Int = java.time.LocalDate.now().year): Result {
        val l = Labels(lang)
        var name = baseName(rawName).ifEmpty { "fichier" }
        if (NameParser.splitExt(name).second.isEmpty()) MIME_EXT[mime?.substringBefore(';')?.trim()?.lowercase()]?.let { name = "$name.$it" }
        if (keepsFlat(name)) {
            val app = name.substringAfterLast('.', "").lowercase() in APP_FAMILY
            return Result(if (app) Category.APPS else Category.TO_SORT, "", name, "flat", keepFlat = true)
        }
        val p = NameParser.parse(name, "", meta.durationMs, currentYear)
        val sure = p.confidence >= MIN_CONFIDENCE
        val prop = Namer.propose(p, FileRef(Origin.TV, name, size), l, mtimeDate = meta.mtimeDate)
        val ext = p.ext
        // The proposed name is used only when it is valid and keeps the extension; else the file keeps its own (cleaned) name.
        val proposed = prop.name.takeIf { SafeName.checkName(it) == null && it.substringAfterLast('.', "").lowercase() == ext.lowercase() }
        val own = ownName(name)
        fun folderOf(c: Category, sub: String? = null) = SafeName.folder(listOfNotNull(top(c, l), sub))
        fun r(c: Category, folder: String, n: String?, note: String = "") = Result(c, folder, n ?: own, p.rule, note = note)
        fun toSort(note: String = "") = Result(Category.TO_SORT, folderOf(Category.TO_SORT), own, p.rule.ifEmpty { "default" }, note = note)

        return when (p.kind) {
            Kind.SERIES -> if (sure && p.title.isNotBlank() && (p.episode != null || p.date != null) && prop.folder.startsWith(l.series + "/") && SafeName.checkFolder(prop.folder) == null)
                r(Category.SERIES, prop.folder, proposed) else toSort("série non identifiée")
            Kind.MOVIE -> if (p.confidence >= MOVIE_CONFIDENCE) r(Category.FILMS, folderOf(Category.FILMS), proposed) else toSort("film non identifié")
            Kind.MUSIC -> r(Category.MUSIC, folderOf(Category.MUSIC), proposed.takeIf { sure })
            Kind.CLIP -> r(Category.MUSIC, folderOf(Category.MUSIC, l.clips), proposed.takeIf { sure })
            Kind.COURSE -> r(Category.COURSES, prop.folder.takeIf { it.startsWith(l.courses) && SafeName.checkFolder(it) == null } ?: folderOf(Category.COURSES), proposed.takeIf { sure })
            Kind.PERSONAL, Kind.PHOTO -> when {
                p.origin == "Capture" || p.origin == "Écran" -> r(Category.CAPTURES, folderOf(Category.CAPTURES), proposed)
                p.media == Media.IMAGE || p.kind == Kind.PHOTO -> r(Category.PHOTOS, folderOf(Category.PHOTOS), proposed)
                else -> r(Category.FAMILY, folderOf(Category.FAMILY), proposed)
            }
            Kind.DOCUMENT -> r(Category.DOCUMENTS, folderOf(Category.DOCUMENTS), null)
            Kind.APP -> r(Category.APPS, folderOf(Category.APPS), null)
            Kind.ARCHIVE -> r(Category.ARCHIVES, folderOf(Category.ARCHIVES), null)
            Kind.UNKNOWN -> when (p.media) {
                Media.IMAGE -> r(Category.PHOTOS, folderOf(Category.PHOTOS), null)
                Media.AUDIO -> r(Category.MUSIC, folderOf(Category.MUSIC), null)
                Media.DOC -> r(Category.DOCUMENTS, folderOf(Category.DOCUMENTS), null)
                else -> toSort()
            }
        }
    }

    /** The own name of a file, kept when it is already valid on every volume; else cleaned (forbidden characters, trailing dots, length). */
    private fun ownName(name: String): String {
        if (SafeName.checkName(name) == null) return name
        val dot = name.lastIndexOf('.').takeIf { it > 0 && name.length - it <= 12 } ?: name.length
        val ext = name.substring(dot).removePrefix(".")
        val cleaned = SafeName.fileName(name.substring(0, dot), ext)
        return if (SafeName.checkName(cleaned) == null) cleaned else "fichier" + if (ext.isEmpty()) "" else ".$ext"
    }

    /** Null if the size fits the file system, else why not (French). */
    fun sizeRefusal(size: Long, fs: Fs): String? =
        if (size > fs.maxFileBytes) "trop gros pour ${fs.label} (4 Go maximum) : le fichier reste où il est" else null

    /**
     * The final place on a volume with file system [fs]. [taken] says whether a NAME (any folder, any volume: one name space) is already used;
     * [self] is the name the file has right now (a file never collides with itself). Returns null when the file must not be placed
     * ([sizeRefusal], an unsafe path, or too many collisions): the caller then leaves it where it is.
     */
    fun place(r: Result, fs: Fs, size: Long, self: String? = null, taken: (String) -> Boolean): Placement? {
        if (r.keepFlat || sizeRefusal(size, fs) != null) return null
        if (r.folder.isNotEmpty() && SafeName.checkFolder(r.folder) != null) return null
        val segments = if (r.folder.isEmpty()) emptyList() else r.folder.split('/').map { NameRules.store(it, if (fs == Fs.UNKNOWN) Fs.EXFAT else fs) }
        val folder = segments.joinToString("/")
        val strict = if (fs == Fs.UNKNOWN) Fs.EXFAT else fs
        fun free(n: String) = self != null && n.equals(self, ignoreCase = true) || !taken(n)
        var i = 1
        var n = variant(r.name, i, strict)
        while (!free(n)) {
            if (++i > MAX_SUFFIX) return null
            n = variant(r.name, i, strict)
        }
        val rel = if (folder.isEmpty()) n else "$folder/$n"
        if (rel.length > UsbPaths.MAX_PATH_CHARS || rel.split('/').size > UsbPaths.MAX_DEPTH || rel.split('/').any { UsbPaths.badSegment(it) != null }) return null
        return Placement(folder, n, rel, i > 1)
    }

    /** [name] with " (i)" before the extension (i = 1: the name itself), valid for [fs] and within the byte limit. */
    fun variant(name: String, i: Int, fs: Fs): String {
        if (i <= 1) return NameRules.store(name, fs)
        val dot = name.lastIndexOf('.').takeIf { it > 0 && name.length - it <= 12 } ?: name.length
        val ext = name.substring(dot); var stem = name.substring(0, dot)
        val tag = " ($i)"
        while ((stem + tag + ext).toByteArray(Charsets.UTF_8).size > NAME_LIMIT && stem.isNotEmpty()) stem = stem.substring(0, stem.offsetByCodePoints(stem.length, -1)).trimEnd('.', ' ')
        return NameRules.store(stem + tag + ext, fs)
    }

    /** The names a file of [r] would have been filed under (the clean name, then " (2)"…): how a file is recognised when the index of original names was lost. */
    fun candidates(r: Result, fs: Fs, count: Int = 5): List<String> =
        if (r.keepFlat) emptyList() else (1..count).map { variant(r.name, it, if (fs == Fs.UNKNOWN) Fs.EXFAT else fs) }

    /** One flat file offered to [plan]. [skip] = why it must not be touched (French: protégé, en cours de lecture…), null = free. */
    data class PlanFile(val volumeId: String, val name: String, val size: Long, val fs: Fs, val skip: String? = null, val meta: Meta = Meta())
    data class Move(val volumeId: String, val from: String, val folder: String, val to: String, val category: Category, val rule: String, val note: String = "") {
        val rel: String get() = if (folder.isEmpty()) to else "$folder/$to"
    }
    data class Plan(val moves: List<Move>, val skipped: List<Pair<String, String>>, val unchanged: Int) {
        val byCategory: Map<String, Int> get() = moves.groupingBy { it.folder.substringBefore('/') }.eachCount()
    }

    /**
     * The one-off « Ranger ma bibliothèque »: what each FLAT file would become. Nothing moves here (dry run). A protected, busy, installer or
     * oversized file is [Plan.skipped] with its reason; names are made unique against [taken] AND against the other moves of the plan.
     */
    fun plan(files: List<PlanFile>, lang: String = "fr", currentYear: Int = java.time.LocalDate.now().year, taken: (String) -> Boolean): Plan {
        val moves = ArrayList<Move>(); val skipped = ArrayList<Pair<String, String>>()
        val reserved = HashSet<String>()           // lowercase final names already given by this plan
        fun one(f: PlanFile): String? {                      // null = planned, else why it is skipped
            f.skip?.let { return it }
            val r = classify(f.name, null, f.size, f.meta, lang, currentYear)
            if (r.keepFlat && r.category != Category.APPS) return "reste où il est (utilisé par son nom)"
            val res = if (r.keepFlat) Result(Category.APPS, Labels(lang).apps, f.name, "app") else r
            sizeRefusal(f.size, f.fs)?.let { return it }
            val pl = place(res, f.fs, f.size, f.name) { n -> n.lowercase() in reserved || taken(n) } ?: return "chemin ou nom impossible"
            reserved += pl.name.lowercase()
            moves += Move(f.volumeId, f.name, pl.folder, pl.name, res.category, res.rule, if (pl.renamedForCollision) "nom déjà pris : numéro ajouté" else res.note)
            return null
        }
        for (f in files.sortedBy { it.name.lowercase() }) one(f)?.let { skipped += f.name to it }
        return Plan(moves, skipped, unchanged = 0)
    }

    /** French one-liner for the TV screen and the phone. */
    fun summary(p: Plan): String =
        if (p.moves.isEmpty()) "Rien à ranger : tout est déjà en place." else
            "${p.moves.size} fichier${if (p.moves.size > 1) "s" else ""} à ranger : " + p.byCategory.entries.sortedByDescending { it.value }.joinToString(", ") { "${it.value} dans ${it.key}" } + "."
}

/**
 * `.filing` of a volume folder: which real sub-folder holds each file (`key -> relative path`), and under which ORIGINAL name the phone sent it
 * (`origin -> key`), so that resume and « already on the TV » (same name, same size) still recognise a file that was renamed and filed.
 *
 * The key of a file is its clean final name, unique on the TV (one name space): every route keeps working with one flat name. The files
 * are the truth: entries whose file vanished are dropped when read, and a missing index is rebuilt by walking the category folders (bounded).
 * Written atomically (temp file, sync, rename); the entry is written BEFORE the file is renamed, so a power cut leaves either the flat
 * file (still valid, filed later by « Ranger ma bibliothèque ») or the filed file with its entry, never a file the TV cannot find.
 */
class FiledIndex(private val dir: File, private val maxEntries: Int = 50_000) {
    private val rel = LinkedHashMap<String, Pair<String, String>>()      // lowercase key -> (key, relative path)
    private val alias = LinkedHashMap<String, Pair<String, String>>()    // lowercase origin -> (origin, key)
    private val file = File(dir, FILE)
    @Volatile private var loaded = false

    @Synchronized private fun load() {
        if (loaded) return
        loaded = true
        if (file.isFile) {
            runCatching {
                for (l in file.readLines()) {
                    val p = l.split('\t')
                    if (p.size < 3) continue
                    when (p[0]) {
                        "f" -> dec(p[1]).let { k -> rel[k.lowercase()] = k to dec(p[2]) }
                        "a" -> dec(p[1]).let { o -> alias[o.lowercase()] = o to dec(p[2]) }
                    }
                }
            }
        } else adopt()
    }

    /** Files found in the category folders when there is no index (lost, or a drive written by an older app): their names become keys. Bounded, never deletes. */
    private fun adopt() {
        if (!dir.isDirectory) return
        var seen = 0
        fun walk(d: File, relDir: String, depth: Int) {
            for (f in d.listFiles().orEmpty().sortedBy { it.name }) {
                if (++seen > maxEntries || UsbPaths.isSymlink(f) || f.name.startsWith(".")) continue
                val r = if (relDir.isEmpty()) f.name else "$relDir/${f.name}"
                if (f.isDirectory) { if (depth < 5 && (depth > 0 || f.name in Filing.ROOTS)) walk(f, r, depth + 1) }
                else if (depth > 0 && f.isFile && !f.name.endsWith(Storage.PART) && !f.name.endsWith(Meta.SUFFIX)) rel.putIfAbsent(f.name.lowercase(), f.name to r)
            }
        }
        walk(dir, "", 0)
        if (rel.isNotEmpty()) save()
    }

    /** Relative path of the file called [name] ("Films/X.mkv"), or null if it is not filed (or its file is gone). */
    @Synchronized fun locate(name: String): String? {
        load()
        val e = rel[name.lowercase()] ?: return null
        if (File(dir, e.second).isFile) return e.second
        if (dir.isDirectory) { rel.remove(name.lowercase()); dropAliasesOf(e.first); save() }   // never prune while the volume is away
        return null
    }

    /** The key of the file the phone sent as [origin] (null if none, or if it is gone). */
    @Synchronized fun keyOfOrigin(origin: String): String? {
        load()
        val k = alias[origin.lowercase()]?.second ?: return null
        return if (locate(k) != null) k else null
    }

    /** The name the phone used for [key] (null if the file kept the name it was sent under). */
    @Synchronized fun originOf(key: String): String? { load(); return alias.values.lastOrNull { it.second.equals(key, true) }?.first }

    @Synchronized fun isFiled(name: String): Boolean { load(); return rel.containsKey(name.lowercase()) }

    /** Records that [key] lives at [relPath]; [origin] (the name the phone used, when it differs) resolves to it. Persists. */
    @Synchronized fun put(key: String, relPath: String, origin: String? = null) {
        load()
        rel[key.lowercase()] = key to relPath
        if (origin != null && !origin.equals(key, ignoreCase = true)) alias[origin.lowercase()] = origin to key
        save()
    }

    @Synchronized fun forget(key: String) {
        load()
        if (rel.remove(key.lowercase()) != null) { dropAliasesOf(key); save() }
    }

    @Synchronized fun renamed(from: String, to: String, newRel: String) {
        load()
        val old = rel.remove(from.lowercase()) ?: return
        rel[to.lowercase()] = to to newRel
        alias.entries.filter { it.value.second.equals(old.first, true) }.forEach { it.setValue(it.value.first to to) }
        save()
    }

    /** key (lowercase) -> origin name, for the files that were renamed on reception. */
    @Synchronized fun origins(): Map<String, String> { load(); return alias.values.associate { it.second.lowercase() to it.first } }

    /** Reads the category folders again and adds the files the index does not know (a cut between a rename and its entry, a drive written elsewhere). */
    @Synchronized fun resync() { load(); val before = rel.size; adopt(); if (rel.size != before) save() }

    /** Every filed file: (key, relative path), existence not checked (the store stats them). */
    @Synchronized fun entries(): List<Pair<String, String>> { load(); return rel.values.toList() }

    @Synchronized fun size(): Int { load(); return rel.size }

    private fun dropAliasesOf(key: String) { alias.entries.removeAll { it.value.second.equals(key, true) } }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
    private fun dec(s: String) = java.net.URLDecoder.decode(s, "UTF-8")

    private fun save() {
        if (!dir.isDirectory) return
        val sb = StringBuilder()
        for ((k, r) in rel.values) sb.append("f\t").append(enc(k)).append('\t').append(enc(r)).append('\n')
        for ((o, k) in alias.values) sb.append("a\t").append(enc(o)).append('\t').append(enc(k)).append('\n')
        runCatching { AtomicFile.write(file, sb.toString().toByteArray(Charsets.UTF_8)) }
    }

    companion object { const val FILE = ".filing" }
}
