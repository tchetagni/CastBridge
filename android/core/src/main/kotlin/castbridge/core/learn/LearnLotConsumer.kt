package castbridge.core.learn

import castbridge.core.lots.LotConsumer
import castbridge.core.lots.LotId
import castbridge.core.lots.LotMeta
import castbridge.core.quiz.Json
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipFile

/**
 * Installs « Apprendre » lots (feature "learn") into [root]: `<root>/<scope>/v<version>/{lot.zip, meta.json}`.
 *
 * - [install] verifies the file against its [LotMeta] (size, sha256, minAppVersion), then the whole lot ([LotReader]), writes it
 *   to a staging folder and renames the folder into place (atomic). The previous version stays untouched until then, and is
 *   deleted only afterwards: a corrupted, truncated or interrupted install never loses what works (rollback = do nothing).
 * - A version is immutable: the same version with another sha256, or an older version than the installed one, is refused.
 * - Nothing here uses the network: lots arrive as files (from the server by the phone, from the phone by the TV).
 *
 * [appVersion]: version code of the app; a lot asking for a newer app is refused (kept for later).
 */
class LearnLotConsumer(
    private val root: File,
    private val signatures: PackSignatures = PackSignatures(),
    private val appVersion: Int = Int.MAX_VALUE,
    private val now: () -> Long = System::currentTimeMillis,
) : LotConsumer {
    override val feature = LotFormat.FEATURE

    /** An installed lot: what the screens show (version, size, age of the data). */
    class Installed(val meta: LotMeta, val date: String, val installedAt: Long, val file: File)

    /** Why the last [install] was refused (for the Données screen); null after a success. */
    @Volatile var lastError: String? = null; private set

    @Synchronized override fun install(meta: LotMeta, data: File): Boolean {
        lastError = null
        fun no(why: String): Boolean { lastError = why; return false }
        if (meta.id.feature != feature) return no("ce lot n'est pas un lot Apprendre")
        val scope = meta.id.scope
        if (!LearnScopes.valid(scope)) return no("classe « $scope » invalide")
        if (meta.minAppVersion > appVersion) return no("nécessite une version plus récente de l'application")
        if (!data.isFile || data.length() != meta.bytes) return no("taille du lot différente de celle annoncée")
        if (data.length() > LotFormat.MAX_LOT_BYTES * 2) return no("lot trop gros")
        if (!runCatching { LotFormat.sha256(data) == meta.sha256 }.getOrDefault(false)) return no("empreinte sha256 différente (fichier abîmé ou altéré)")
        val current = installedOne(scope)
        if (current != null) {
            if (current.meta.version == meta.version) return if (current.meta.sha256 == meta.sha256) true else no("la version ${meta.version} existe déjà avec un contenu différent")
            if (current.meta.version > meta.version) return no("une version plus récente (${current.meta.version}) est déjà installée")
        }
        val lot = try { LotReader.read(data, signatures, otherLessons(scope)) } catch (e: LotReader.Refused) { return no(e.message ?: "lot refusé") }
        if (lot.index.scope != scope || lot.index.version != meta.version) return no("le lot (${lot.index.scope} v${lot.index.version}) ne correspond pas à sa description")
        val dir = File(root, scope)
        val stage = File(dir, ".stage-${meta.version}-${now()}")
        val target = File(dir, "v${meta.version}")
        try {
            stage.mkdirs()
            data.copyTo(File(stage, LOT_FILE), overwrite = true)
            File(stage, META_FILE).writeText(Json.write(linkedMapOf("feature" to feature, "scope" to scope, "version" to meta.version, "bytes" to meta.bytes,
                "sha256" to meta.sha256, "title" to meta.title.ifBlank { lot.index.title }, "minAppVersion" to meta.minAppVersion, "date" to lot.index.date, "installedAt" to now())), Charsets.UTF_8)
            if (target.exists()) target.deleteRecursively()
            if (!stage.renameTo(target)) throw IOException("renommage impossible")
        } catch (e: IOException) {
            stage.deleteRecursively()
            return no("écriture impossible : ${e.message}")
        }
        // only now that the new version is in place: the old ones and any leftovers of interrupted installs
        dir.listFiles()?.filter { it != target }?.forEach { it.deleteRecursively() }
        return true
    }

    @Synchronized override fun remove(id: LotId) {
        if (id.feature == feature && LearnScopes.valid(id.scope)) File(root, id.scope).deleteRecursively()
    }

    @Synchronized override fun installed(): List<LotMeta> = installedAll().map { it.meta }

    /** Installed lots with their data date, ordered like the classes of school. */
    @Synchronized fun installedAll(): List<Installed> =
        root.listFiles { f -> f.isDirectory && LearnScopes.valid(f.name) }.orEmpty().mapNotNull { installedOne(it.name) }.sortedBy { order(it.meta.id.scope) }

    @Synchronized fun installedOne(scope: String): Installed? {
        if (!LearnScopes.valid(scope)) return null
        val versions = File(root, scope).listFiles { f -> f.isDirectory && f.name.matches(Regex("v\\d+")) }.orEmpty().sortedByDescending { it.name.drop(1).toInt() }
        for (d in versions) {                                    // a damaged newest folder falls back to the previous one
            val i = readInstalled(scope, d)
            if (i != null) return i
        }
        return null
    }

    /** Bytes taken on disk by every installed lot (the framework's budget uses [LotMeta.bytes]; this is the check). */
    fun diskBytes(): Long = installedAll().sumOf { it.file.length() }

    /** Index of an installed lot (lessons, titles, hashes), or null. */
    fun index(scope: String): LotIndex? = installedOne(scope)?.let { i ->
        synchronized(idx) { idx.getOrPut("${i.meta.id.scope}@${i.meta.sha256}") { runCatching { LotReader.index(i.file) }.getOrNull() ?: return null } }
    }
    private val idx = HashMap<String, LotIndex>()

    /** Content hash of a lesson in the installed lots (progress compatibility: « mise à jour »), null if unknown. */
    fun lessonHash(lesson: String): String? = installedAll().firstNotNullOfOrNull { index(it.meta.id.scope)?.hashOf(lesson) }

    private fun readInstalled(scope: String, d: File): Installed? = runCatching {
        val lot = File(d, LOT_FILE); val m = Json.obj(File(d, META_FILE).readText(Charsets.UTF_8))
        val meta = LotMeta(LotId(feature, scope), (m["version"] as Number).toInt(), (m["bytes"] as Number).toLong(), m["sha256"] as String, m["title"] as? String ?: "",
            (m["minAppVersion"] as? Number)?.toInt() ?: 0)
        if (!lot.isFile || lot.length() != meta.bytes || meta.version != d.name.drop(1).toInt()) null
        else Installed(meta, m["date"] as? String ?: "", (m["installedAt"] as? Number)?.toLong() ?: 0, lot)
    }.getOrNull()

    private fun otherLessons(scope: String): Set<String> = emptySet()

    private fun order(scope: String): Int = LearnScopes.ladders.values.flatMap { it.flatten() }.indexOf(scope).let { if (it < 0) Int.MAX_VALUE else it }

    companion object {
        const val LOT_FILE = "lot.zip"
        const val META_FILE = "meta.json"
    }
}

/** The installed lots as a [LessonSource] (put it before the starter packs: same pack, highest version wins, then the first source). */
class LearnLotSource(private val consumer: LearnLotConsumer) : LessonSource {
    override val origin = "lots"
    private val cache = HashMap<String, List<PackRef>>()

    @Synchronized override fun list(): List<PackRef> = consumer.installedAll().flatMap { i ->
        val key = "${i.meta.id.scope}@${i.meta.version}@${i.meta.sha256}"
        cache.getOrPut(key) {
            runCatching {
                val idx = LotReader.index(i.file)
                idx.packs.mapNotNull { p ->
                    val bytes = { read(i.file, "packs/${p.file}") }
                    val m = runCatching { bytes().use { PackReader.manifest(it) } }.getOrNull() ?: return@mapNotNull null
                    PackRef(m, "lot ${i.meta.id.scope}", null, bytes).also { it.lot = i.meta.id.scope }
                }
            }.getOrDefault(emptyList())
        }
    }

    override fun open(ref: PackRef) = ref.bytes().use { PackReader.read(it) }

    private fun read(lot: File, entry: String): InputStream = ZipFile(lot).use { z ->
        val e = z.getEntry(entry) ?: throw IOException("$entry absent du lot")
        ByteArrayInputStream(z.getInputStream(e).use { it.readBytes() })
    }
}

/**
 * What « Apprendre » shows as « Mes classes »: every class the device holds (an installed lot, or the starter packs of the
 * app), with where its data comes from and how old it is, and the lessons of each. Pure; the update status against the server
 * comes from the framework's catalog (see [LearnClassStatus]).
 */
class LearnLotCatalog(private val consumer: LearnLotConsumer, private val starter: LessonSource? = null) {
    data class ClassInfo(val scope: String, val title: String, val meta: LotMeta?, val date: String?, val fromStarter: Boolean, val packs: Int, val lessons: Int)
    data class LessonEntry(val pack: String, val id: String, val title: String, val hash: String)

    fun classes(): List<ClassInfo> {
        val out = LinkedHashMap<String, ClassInfo>()
        for (i in consumer.installedAll()) {
            val idx = consumer.index(i.meta.id.scope)
            out[i.meta.id.scope] = ClassInfo(i.meta.id.scope, i.meta.title.ifBlank { LearnScopes.label(i.meta.id.scope) }, i.meta, i.date.ifBlank { null }, false, idx?.packs?.size ?: 0, idx?.lessonCount ?: 0)
        }
        val refs = runCatching { starter?.list() }.getOrNull().orEmpty().groupBy { it.scope }
        for ((scope, rs) in refs) if (scope != null && scope !in out)
            out[scope] = ClassInfo(scope, LearnScopes.label(scope), null, null, true, rs.size, rs.sumOf { it.manifest.lessons })
        val order = LearnScopes.ladders.values.flatMap { it.flatten() }
        return out.values.sortedBy { order.indexOf(it.scope).let { k -> if (k < 0) Int.MAX_VALUE else k } }
    }

    fun lessons(scope: String): List<LessonEntry> {
        consumer.index(scope)?.let { idx -> return idx.packs.flatMap { p -> p.lessons.map { LessonEntry(p.id, it.id, it.title, it.hash) } } }
        val src = starter ?: return emptyList()
        return src.list().filter { it.scope == scope }.flatMap { r ->
            val vp = runCatching { src.open(r) }.getOrNull() ?: return@flatMap emptyList()
            val h = LessonHashes.of(vp.files.filterKeys { it.endsWith(".json") }.mapValues { String(it.value, Charsets.UTF_8) })
            vp.pack.lessons.map { LessonEntry(r.id, it.id, it.title, h[it.id] ?: "") }
        }
    }
}

/** Status of one class for the phone's « Mes classes » (installed vs the server's catalog vs what the TV holds). */
data class LearnClassStatus(val scope: String, val title: String, val installedVersion: Int?, val installedBytes: Long?, val dataDate: String?,
                            val availableVersion: Int?, val availableBytes: Long?, val onTv: Boolean) {
    val downloaded get() = installedVersion != null
    val updateAvailable get() = installedVersion != null && availableVersion != null && availableVersion > installedVersion

    /** The call to action under the class: « Télécharger 3e – BEPC : 4,2 Mo », « Mettre à jour… », or null when nothing to do. */
    fun action(size: (Long) -> String = LearnFormat::size): String? = when {
        !downloaded && availableBytes != null -> "Télécharger $title : ${size(availableBytes)}"
        updateAvailable && availableBytes != null -> "Mettre à jour $title : ${size(availableBytes)}"
        else -> null
    }

    companion object {
        /** [catalog] = lots offered by the server (the phone's last synchronisation, kept offline), [onTv] = scopes the TV reported holding. */
        fun of(installed: List<LearnLotConsumer.Installed>, catalog: List<LotMeta>, onTv: Set<String>): List<LearnClassStatus> {
            val avail = catalog.filter { it.id.feature == LotFormat.FEATURE }.associateBy { it.id.scope }
            val inst = installed.associateBy { it.meta.id.scope }
            val order = LearnScopes.ladders.values.flatMap { it.flatten() }
            return (avail.keys + inst.keys).distinct().sortedBy { order.indexOf(it).let { k -> if (k < 0) Int.MAX_VALUE else k } }.map { s ->
                LearnClassStatus(s, avail[s]?.title ?: inst[s]?.meta?.title ?: LearnScopes.label(s), inst[s]?.meta?.version, inst[s]?.meta?.bytes, inst[s]?.date?.ifBlank { null },
                    avail[s]?.version, avail[s]?.bytes, s in onTv)
            }
        }
    }
}

/** French wording shared by both apps: sizes (« 4,2 Mo ») and the freshness of the data (« données du 12 sept. »). Never mentions Internet. */
object LearnFormat {
    private val MONTHS = listOf("janv.", "févr.", "mars", "avr.", "mai", "juin", "juil.", "août", "sept.", "oct.", "nov.", "déc.")

    fun size(b: Long): String = when {
        b < 1024 -> "$b o"
        b < 1L shl 20 -> "${(b + 1023) / 1024} Ko"
        else -> String.format(java.util.Locale.ROOT, "%.1f", b / 1048576.0).replace('.', ',') + " Mo"
    }

    /** « données du 12 sept. » from an ISO date (2026-09-12); « données d'origine inconnue » when there is none. */
    fun dataDate(iso: String?): String {
        val d = runCatching { java.time.LocalDate.parse(iso) }.getOrNull() ?: return "date des données inconnue"
        return "données du ${d.dayOfMonth}${if (d.dayOfMonth == 1) "er" else ""} ${MONTHS[d.monthValue - 1]}"
    }
}
