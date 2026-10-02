package castbridge.core.learn

import castbridge.core.tv.TransferRule
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Where lessons come from (like the quiz's QuestionSource), in priority order (docs/LEARN.md § Sources):
 * 1. packs installed on a volume of the TV (USB drive first, then internal memory, or a folder chosen by the user),
 *    in a `CastBridge/Packs/` folder: a pack on a USB drive is usable as is, nothing to copy;
 * 2. the small socle embedded in the app (a few packs zipped in the resources, budget-checked by the tests);
 * 3. later, the online server (bridge.sti-cm.com): [RemoteLessonApi] lists and downloads packs, [PackInstaller] stores them.
 * The same pack id in several places: the highest version wins, then the first source.
 */
interface LessonSource {
    /** Short description for the « Contenus » screen ("embarqué", "clé USB SANDISK"...). */
    val origin: String
    /** Packs available here (manifests only: fast). */
    fun list(): List<PackRef>
    /** Opens and fully verifies a pack (sha256, signature, content). */
    fun open(ref: PackRef): VerifiedPack
}

/** A pack somewhere: its manifest and how to reach its bytes. */
class PackRef(val manifest: PackManifest, val origin: String, val file: File? = null, val bytes: () -> InputStream, val removable: Boolean = false) {
    /** The source that listed this reference (set by [LearnLibrary]). */
    @Volatile var source: LessonSource? = null
    /** The lot (class) this pack belongs to, when its source knows it (installed lot, embedded catalog); else guessed from the level. */
    var lot: String? = null
    val scope: String? get() = lot ?: LearnScopes.guess(manifest)
    val id get() = manifest.id
    val version get() = manifest.version
    override fun toString() = "${manifest.id} v${manifest.version} ($origin)"
}

/** The packs zipped in the app resources (castbridge/learn/embedded/, listed in catalog.json). */
class EmbeddedLessonSource(private val base: String = "/castbridge/learn/embedded/") : LessonSource {
    override val origin = "embarqué"
    private val refs: List<PackRef> by lazy {
        val cat = res("catalog.json")?.use { String(it.readBytes(), Charsets.UTF_8) } ?: return@lazy emptyList()
        LearnCatalogFile.parse(cat).mapNotNull { item ->
            val f = item.file ?: return@mapNotNull null
            val m = runCatching { res(f)?.use { PackReader.manifest(it) } }.getOrNull() ?: return@mapNotNull null
            PackRef(m, origin, null, { res(f) ?: throw IOException("ressource $f absente") }).also { it.lot = item.scope }
        }
    }
    private fun res(name: String): InputStream? = EmbeddedLessonSource::class.java.getResourceAsStream(base + name)
    override fun list() = refs
    override fun open(ref: PackRef) = ref.bytes().use { PackReader.read(it) }
}

/**
 * Packs in folders of the TV's volumes: [dirs] gives (label, folder, removable) each time (drives come and go).
 * Manifests are cached per (path, size, date) so listing a USB drive does not reopen every zip.
 */
class DirectoryLessonSource(
    private val dirs: () -> List<Triple<String, File, Boolean>>,
    private val signatures: PackSignatures = PackSignatures(),
) : LessonSource {
    override val origin = "packs installés"
    private val cache = HashMap<String, Pair<String, PackManifest>>()

    @Synchronized override fun list(): List<PackRef> = dirs().flatMap { (label, dir, removable) ->
        dir.listFiles { f -> f.isFile && f.name.endsWith(PackFormat.SUFFIX) }?.sortedBy { it.name }.orEmpty().mapNotNull { f ->
            val key = f.absolutePath; val sig = "${f.length()}:${f.lastModified()}"
            val m = cache[key]?.takeIf { it.first == sig }?.second ?: runCatching { PackReader.manifest(f) }.getOrNull()?.also { cache[key] = sig to it }
            m?.let { PackRef(it, label, f, { f.inputStream().buffered() }, removable) }
        }
    }

    override fun open(ref: PackRef) = ref.bytes().use { PackReader.read(it, signatures) }
}

/**
 * Future online server (not implemented in the POC, docs/LEARN.md § Serveur). GET /api/v1/learn/catalog returns the
 * catalog.json format; GET /api/v1/learn/packs/{id}/{version} the zip (Range requests for resuming).
 */
interface RemoteLessonApi {
    /** catalog.json of the server, or null when unreachable. */
    fun catalog(): String?
    /** The zip from byte [offset] (resume), or null when unreachable. */
    fun download(id: String, version: Int, offset: Long): InputStream?
}

/**
 * All sources merged. [packs] lists the best version of every pack; [pack] opens one (kept in a tiny cache: the TV has
 * ~1 GB of RAM, a pack is a few hundred kB once parsed). A pack that fails verification is reported in [problems]
 * and skipped: an altered pack is never shown.
 */
class LearnLibrary(private val sources: List<LessonSource>, private val keep: Int = 3) {
    private val opened = LinkedHashMap<String, VerifiedPack>(4, 0.75f, true)
    private val bad = LinkedHashMap<String, String>()

    /** Packs refused (key "id vN (origin)") with the reason. */
    val problems: Map<String, String> @Synchronized get() = LinkedHashMap(bad)

    /** Every pack reference found, all versions and places (the « Contenus » screen). */
    fun all(): List<PackRef> = sources.flatMap { s -> runCatching { s.list() }.getOrDefault(emptyList()).onEach { it.source = s } }

    /**
     * The best reference per pack id: highest version, then the first source (installed before embedded). A base pack ([BaseContent])
     * is left out once its full pack is there, or once a lot of its class is installed: the complete lessons replace the base ones,
     * a lesson is never listed twice.
     */
    fun packs(): List<PackRef> = visible(best())

    private fun best(): List<PackRef> = all().filter { synchronized(this) { bad[it.toString()] == null } }.groupBy { it.id }.values
        .map { refs -> refs.maxWith(compareBy<PackRef> { it.version }.thenByDescending { r -> refs.indexOf(r) }) }

    private fun visible(best: List<PackRef>): List<PackRef> {
        val ids = best.map { it.id }.toSet()
        val lotScopes = best.filter { it.source is LearnLotSource && !BaseContent.isBase(it.id) }.mapNotNull { it.scope }.toSet()
        return best.filterNot { r -> BaseContent.fullIdOf(r.id)?.let { it in ids || r.scope in lotScopes } == true }
    }

    /** A pack by id; the id of a base pack that its full pack replaced gives the full pack (same lesson and exercise ids: the progress carries over). */
    fun ref(id: String): PackRef? {
        val best = best(); val shown = visible(best)
        return shown.firstOrNull { it.id == id } ?: BaseContent.fullIdOf(id)?.let { full -> shown.firstOrNull { it.id == full } }
    }

    @Synchronized fun pack(id: String): Pack? = verified(id)?.pack

    @Synchronized fun verified(id: String): VerifiedPack? {
        val ref = ref(id) ?: return null
        val key = "${ref.id}@${ref.version}@${ref.origin}"
        opened[key]?.let { return it }
        val src = ref.source ?: return null
        return try {
            src.open(ref).also { opened[key] = it; while (opened.size > keep) opened.remove(opened.keys.first()) }
        } catch (e: Exception) {
            bad[ref.toString()] = e.message ?: e.javaClass.simpleName
            null
        }
    }

    /** Packs for an exam / level / subject (manifests only). */
    fun find(exam: String? = null, level: String? = null, subject: String? = null): List<PackRef> =
        packs().filter { (exam == null || it.manifest.exam == exam) && (level == null || it.manifest.level == level) && (subject == null || it.manifest.subject == subject) }

    @Synchronized fun forget() { opened.clear(); bad.clear() }
}

/**
 * Installs a pack (from the phone, the server, or a USB drive) into a `CastBridge/Packs` folder: verified first, written
 * to a temporary file then renamed, older versions of the same pack removed. The destination is the first volume that
 * keeps [minFree] bytes free after the copy (the rule of the phone ↔ TV transfers: 1 GB by default).
 */
class PackInstaller(private val signatures: PackSignatures = PackSignatures()) {
    class Target(val label: String, val dir: File, val free: Long)
    sealed class Result {
        data class Installed(val manifest: PackManifest, val file: File, val where: String) : Result()
        data class Refused(val reason: String) : Result()
    }

    fun install(bytes: ByteArray, targets: List<Target>, minFree: Long = 1L shl 30): Result {
        val vp = try { PackReader.read(bytes, signatures) } catch (e: PackReader.Refused) { return Result.Refused(e.message ?: "pack refusé") }
        val m = vp.manifest
        val t = targets.firstOrNull { TransferRule.ok(it.free, bytes.size.toLong(), minFree) }
            ?: return Result.Refused("pas assez de place : il faut garder ${TransferRule.size(minFree)} libres après l'installation (${TransferRule.size(bytes.size.toLong(), up = true)} à écrire)")
        return try {
            t.dir.mkdirs()
            val dest = File(t.dir, m.fileName); val tmp = File(t.dir, m.fileName + ".part")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(dest)) { dest.delete(); if (!tmp.renameTo(dest)) throw IOException("renommage impossible") }
            // older (or other) versions of the same pack in this folder
            t.dir.listFiles { f -> f.name.startsWith(m.id + "-v") && f.name.endsWith(PackFormat.SUFFIX) && f.name != dest.name }?.forEach { it.delete() }
            Result.Installed(m, dest, t.label)
        } catch (e: IOException) { Result.Refused("écriture impossible : ${e.message}") }
    }

    /** Copies a pack found somewhere (USB drive, library) after checking it. */
    fun installFile(f: File, targets: List<Target>, minFree: Long = 1L shl 30): Result =
        if (f.length() > PackFormat.MAX_UNCOMPRESSED) Result.Refused("fichier trop gros pour un pack") else install(f.readBytes(), targets, minFree)

    /** Deletes a pack file (to free space); embedded packs cannot be removed. */
    fun remove(ref: PackRef): Boolean = ref.file?.takeIf { it.name.endsWith(PackFormat.SUFFIX) }?.delete() == true
}
