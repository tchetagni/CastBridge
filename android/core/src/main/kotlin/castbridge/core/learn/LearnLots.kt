package castbridge.core.learn

import castbridge.core.lots.LotId
import castbridge.core.lots.LotMeta
import castbridge.core.quiz.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * « Apprendre » lots (docs/LEARN.md § Lots). A lot = ONE zip holding the packs of ONE class: `lot.json` (the index: scope,
 * version, date, content hash, and for every pack its sha256 and for every lesson a content hash) + `packs/<id>-v<n>.learn.zip`
 * (the existing packs, stored as they are, so every pack keeps its own manifest, sha256 and signature checks).
 */
data class LotIndex(
    val scope: String, val title: String, val version: Int, val date: String, val contentHash: String, val packs: List<PackEntry>,
) {
    data class PackEntry(val id: String, val version: Int, val file: String, val sha256: String, val size: Long, val lessons: List<LessonEntry>)
    data class LessonEntry(val id: String, val title: String, val hash: String)

    val lessonCount get() = packs.sumOf { it.lessons.size }
    fun hashOf(lesson: String): String? = packs.firstNotNullOfOrNull { p -> p.lessons.firstOrNull { it.id == lesson }?.hash }

    fun json(): String = Json.write(linkedMapOf(
        "format" to LotFormat.VERSION, "feature" to LotFormat.FEATURE, "scope" to scope, "title" to title, "version" to version, "date" to date,
        "contentHash" to contentHash,
        "packs" to packs.map { p -> linkedMapOf("id" to p.id, "version" to p.version, "file" to p.file, "sha256" to p.sha256, "size" to p.size,
            "lessons" to p.lessons.map { linkedMapOf("id" to it.id, "title" to it.title, "hash" to it.hash) }) },
    ))

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun parse(text: String): LotIndex {
            val r = try { Json.obj(text) } catch (e: Json.ParseError) { throw IllegalArgumentException("lot.json illisible (${e.message})") }
            fun Map<String, Any?>.s(k: String) = this[k] as? String ?: throw IllegalArgumentException("lot.json : « $k » manquant")
            fun Map<String, Any?>.n(k: String) = (this[k] as? Number)?.toLong() ?: throw IllegalArgumentException("lot.json : « $k » manquant")
            fun Map<String, Any?>.list(k: String) = (this[k] as? List<Any?>).orEmpty().map { it as? Map<String, Any?> ?: throw IllegalArgumentException("lot.json : « $k » invalide") }
            require((r["format"] as? Number)?.toInt() == LotFormat.VERSION) { "lot.json : format non pris en charge" }
            require(r.s("feature") == LotFormat.FEATURE) { "lot.json : ce n'est pas un lot Apprendre" }
            return LotIndex(r.s("scope"), r.s("title"), r.n("version").toInt(), r.s("date"), r.s("contentHash"),
                r.list("packs").map { p -> PackEntry(p.s("id"), p.n("version").toInt(), p.s("file"), p.s("sha256"), p.n("size"),
                    p.list("lessons").map { LessonEntry(it.s("id"), it.s("title"), it.s("hash")) }) })
        }
    }
}

object LotFormat {
    const val VERSION = 1
    const val FEATURE = "learn"
    const val INDEX = "lot.json"
    const val SUFFIX = ".lot.zip"
    /** No lot may exceed this (builder refuses): the TV must always be able to hold several. */
    const val MAX_LOT_BYTES = 3L shl 20
    /** Limits when reading a lot (hostile input). */
    const val MAX_ENTRIES = 64
    const val MAX_FILE = 16L shl 20
    const val MAX_TOTAL = 64L shl 20
    private val PACK_ENTRY = Regex("packs/[A-Za-z0-9][A-Za-z0-9._-]{0,80}\\.learn\\.zip")
    fun safeEntry(name: String) = name == INDEX || PACK_ENTRY.matches(name)
    fun fileName(scope: String, version: Int) = "learn-$scope-v$version$SUFFIX"
    fun sha256(b: ByteArray): String = PackFormat.sha256(b)
    fun sha256(f: File): String {
        val d = MessageDigest.getInstance("SHA-256"); val buf = ByteArray(32 shl 10)
        f.inputStream().use { i -> while (true) { val n = i.read(buf); if (n < 0) break; d.update(buf, 0, n) } }
        return d.digest().joinToString("") { "%02x".format(it) }
    }
}

/**
 * Content hash of every lesson, from the raw JSON of a pack (`lessons/<name>.json`): the lesson, plus the exercises and
 * self-check questions it lists. Review status, author and reviewer notes are not content: a teacher's approval never
 * flags a lesson as « mise à jour » for the students. Whitespace and layout of the source do not matter either.
 */
object LessonHashes {
    private val META = setOf("status", "author", "reviewNotes", "review", "reviewNote")

    @Suppress("UNCHECKED_CAST")
    fun of(files: Map<String, String>): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for ((path, text) in files.toSortedMap()) {
            if (!path.startsWith("lessons/") || !path.endsWith(".json")) continue
            val root = Json.obj(text)
            fun clean(v: Any?): Any? = when (v) {
                is Map<*, *> -> v.entries.filter { it.key !in META }.associateTo(LinkedHashMap()) { it.key to clean(it.value) }
                is List<*> -> v.map { clean(it) }
                else -> v
            }
            val ex = (root["exercises"] as? List<Any?>).orEmpty().mapNotNull { it as? Map<String, Any?> }.associateBy { it["id"] as? String }
            for (l in (root["lessons"] as? List<Any?>).orEmpty().mapNotNull { it as? Map<String, Any?> }) {
                val id = l["id"] as? String ?: continue
                val ids = ((l["exercises"] as? List<Any?>).orEmpty() + (l["selfCheck"] as? List<Any?>).orEmpty()).filterIsInstance<String>()
                val payload = Json.write(listOf(root["chapter"], clean(l), ids.map { clean(ex[it]) }))
                out[id] = LotFormat.sha256(payload.toByteArray(Charsets.UTF_8)).take(16)
            }
        }
        return out
    }
}

/** A lot read and fully verified: its index and its verified packs. */
class VerifiedLot(val index: LotIndex, val packs: Map<String, VerifiedPack>)

object LotReader {
    class Refused(msg: String) : Exception(msg)

    /** Index only (cheap: listing installed lots). */
    fun index(f: File): LotIndex = try {
        ZipFile(f).use { z -> val e = z.getEntry(LotFormat.INDEX) ?: throw Refused("lot.json absent"); LotIndex.parse(String(limited(z.getInputStream(e), 4L shl 20), Charsets.UTF_8)) }
    } catch (e: IOException) { throw Refused("archive illisible (${e.message})") } catch (e: IllegalArgumentException) { throw Refused(e.message ?: "index invalide") }

    /**
     * Opens and checks a lot: safe entries and limits, every pack of the index present with the right size and sha256, no extra
     * file, every pack verified by [PackReader] (manifest hashes, signature, content + [LessonValidator]), lesson hashes of the
     * index equal to the recomputed ones. A lot is self-contained: prerequisites may only point inside it (or to [known]).
     */
    fun read(f: File, signatures: PackSignatures = PackSignatures(), known: Set<String> = emptySet()): VerifiedLot {
        val files = LinkedHashMap<String, ByteArray>()
        try {
            ZipFile(f).use { z ->
                var total = 0L
                val entries = z.entries().toList()
                if (entries.size > LotFormat.MAX_ENTRIES) throw Refused("trop de fichiers dans le lot")
                for (e in entries) {
                    if (e.isDirectory) continue
                    if (!LotFormat.safeEntry(e.name)) throw Refused("chemin interdit dans le lot : ${e.name}")
                    if (files.containsKey(e.name)) throw Refused("fichier en double : ${e.name}")
                    val b = limited(z.getInputStream(e), LotFormat.MAX_FILE)
                    total += b.size; if (total > LotFormat.MAX_TOTAL) throw Refused("lot trop gros")
                    files[e.name] = b
                }
            }
        } catch (e: IOException) { throw Refused("archive illisible (${e.message})") }
        val idx = try { LotIndex.parse(String(files.remove(LotFormat.INDEX) ?: throw Refused("lot.json absent"), Charsets.UTF_8)) }
        catch (e: IllegalArgumentException) { throw Refused(e.message ?: "index invalide") }
        if (!LearnScopes.valid(idx.scope)) throw Refused("scope « ${idx.scope} » invalide")
        val declared = idx.packs.associateBy { "packs/${it.file}" }
        files.keys.firstOrNull { it !in declared }?.let { throw Refused("fichier non déclaré dans le lot : $it") }
        val lotLessons = idx.packs.flatMap { p -> p.lessons.map { it.id } }.toSet() + known
        val packs = LinkedHashMap<String, VerifiedPack>()
        for ((path, p) in declared) {
            val b = files[path] ?: throw Refused("pack manquant : ${p.file}")
            if (b.size.toLong() != p.size || LotFormat.sha256(b) != p.sha256) throw Refused("pack altéré : ${p.file}")
            val vp = try { PackReader.read(b, signatures, lotLessons) } catch (e: PackReader.Refused) { throw Refused("${p.id} : ${e.message}") }
            if (vp.manifest.id != p.id || vp.manifest.version != p.version) throw Refused("${p.file} ne correspond pas à l'index")
            val hashes = LessonHashes.of(vp.files.filterKeys { it.endsWith(".json") }.mapValues { String(it.value, Charsets.UTF_8) })
            if (hashes != p.lessons.associate { it.id to it.hash }) throw Refused("${p.id} : empreintes des fiches différentes de l'index (lot altéré ?)")
            packs[p.id] = vp
        }
        return VerifiedLot(idx, packs)
    }

    private fun limited(i: InputStream, max: Long): ByteArray {
        val out = ByteArrayOutputStream(); val buf = ByteArray(16 shl 10); var n = 0L
        i.use { while (true) { val r = it.read(buf); if (r < 0) break; n += r; if (n > max) throw Refused("fichier trop gros dans le lot"); out.write(buf, 0, r) } }
        return out.toByteArray()
    }
}

/**
 * Builds the lots from the sources of the repository. Versions are not chosen by hand: `content/learn/lots.json` records, per
 * lot, the version, the content hash and the date; a lot whose content hash changed gets version + 1 (only in [update] mode,
 * otherwise the build fails: the committed registry must always match the content). Builds are reproducible.
 */
object LearnLotBuilder {
    data class Entry(val version: Int, val hash: String, val date: String)

    class Registry(val lots: Map<String, Entry>) {
        fun json(): String = Json.write(linkedMapOf("format" to 1, "lots" to lots.toSortedMap().mapValues { linkedMapOf("version" to it.value.version, "hash" to it.value.hash, "date" to it.value.date) })) + "\n"
        companion object {
            @Suppress("UNCHECKED_CAST")
            fun parse(text: String?): Registry {
                if (text.isNullOrBlank()) return Registry(emptyMap())
                val l = Json.obj(text)["lots"] as? Map<String, Any?> ?: return Registry(emptyMap())
                return Registry(l.mapNotNull { (k, v) -> (v as? Map<String, Any?>)?.let { m -> k to Entry((m["version"] as? Number)?.toInt() ?: return@mapNotNull null, m["hash"] as? String ?: "", m["date"] as? String ?: "") } }.toMap())
            }
        }
    }

    class Built(val meta: LotMeta, val bytes: ByteArray, val index: LotIndex, val file: String, val packIds: List<String>)
    class Result(val lots: List<Built>, val registry: Registry, val bumped: List<String>) {
        val totalBytes get() = lots.sumOf { it.bytes.size.toLong() }
    }
    class Failure(msg: String) : Exception(msg)

    fun build(content: File, registry: Registry, today: String, update: Boolean, minAppVersion: Int = 0): Result {
        val table = File(content, "scopes.txt").takeIf { it.isFile }?.let { LearnScopes.parseTable(it.readText()) } ?: throw Failure("content/learn/scopes.txt absent")
        val dirs = LearnTool.packDirs(content).associateBy { it.name }
        dirs.keys.filter { table.scopeOf(it) == null }.let { if (it.isNotEmpty()) throw Failure("pack(s) sans lot dans scopes.txt : ${it.joinToString()}") }
        table.lots.values.flatMap { it.packs }.filter { it !in dirs }.let { if (it.isNotEmpty()) throw Failure("scopes.txt cite des packs absents : ${it.joinToString()}") }
        val built = ArrayList<Built>(); val bumped = ArrayList<String>(); val reg = LinkedHashMap<String, Entry>()
        for (lot in table.lots.values) {
            val sources = lot.packs.associateWith { PackBuilder.sources(dirs.getValue(it)) }
            val lotLessons = sources.values.flatMap { s -> runCatching { LessonJson.parsePack(s.filterKeys { it.endsWith(".json") }.mapValues { String(it.value, Charsets.UTF_8) }).lessons.map { l -> l.id } }.getOrDefault(emptyList()) }.toSet()
            val zips = lot.packs.map { id ->
                try { PackBuilder.build(sources.getValue(id), knownLessons = lotLessons) } catch (e: Exception) { throw Failure("lot ${lot.scope} : ${e.message}") }
            }
            val hash = LotFormat.sha256(zips.joinToString(";") { "${it.manifest.id}:${LotFormat.sha256(it.bytes)}" }.toByteArray()).take(32)
            val old = registry.lots[lot.scope]
            val e = when {
                old != null && old.hash == hash -> old
                !update -> throw Failure("le contenu du lot ${lot.scope} a changé (ou le lot est nouveau) : relancer avec --update pour passer à la version ${(old?.version ?: 0) + 1} et enregistrer content/learn/lots.json")
                else -> Entry((old?.version ?: 0) + 1, hash, today).also { bumped += lot.scope }
            }
            reg[lot.scope] = e
            val idx = LotIndex(lot.scope, lot.title, e.version, e.date, hash, zips.map { z ->
                LotIndex.PackEntry(z.manifest.id, z.manifest.version, z.manifest.fileName, LotFormat.sha256(z.bytes), z.bytes.size.toLong(),
                    LessonHashes.of(PackBuilder.sources(dirs.getValue(z.manifest.id)).filterKeys { it.endsWith(".json") }.mapValues { String(it.value, Charsets.UTF_8) })
                        .let { h -> LearnLotBuilder.titles(sources.getValue(z.manifest.id)).map { (id, t) -> LotIndex.LessonEntry(id, t, h.getValue(id)) } })
            })
            val bytes = zip(idx, zips.map { "packs/${it.manifest.fileName}" to it.bytes })
            if (bytes.size > LotFormat.MAX_LOT_BYTES) throw Failure("le lot ${lot.scope} pèse ${bytes.size} octets (> ${LotFormat.MAX_LOT_BYTES}) : le découper")
            built += Built(LotMeta(LotId(LotFormat.FEATURE, lot.scope), e.version, bytes.size.toLong(), LotFormat.sha256(bytes), lot.title, minAppVersion),
                bytes, idx, LotFormat.fileName(lot.scope, e.version), lot.packs)
        }
        return Result(built, Registry(reg), bumped)
    }

    private fun titles(files: Map<String, ByteArray>): List<Pair<String, String>> =
        LessonJson.parsePack(files.filterKeys { it.endsWith(".json") }.mapValues { String(it.value, Charsets.UTF_8) }).lessons.map { it.id to it.title }

    private fun zip(idx: LotIndex, packs: List<Pair<String, ByteArray>>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            // packs are already compressed: stored, so that the lot is as big as its packs and reads without inflating
            fun put(name: String, b: ByteArray, stored: Boolean) {
                val e = ZipEntry(name); e.time = 1767225600000L
                if (stored) { e.method = ZipEntry.STORED; e.size = b.size.toLong(); e.compressedSize = b.size.toLong(); e.crc = CRC32().also { it.update(b) }.value }
                z.putNextEntry(e); z.write(b); z.closeEntry()
            }
            put(LotFormat.INDEX, idx.json().toByteArray(Charsets.UTF_8), false)
            for ((n, b) in packs.sortedBy { it.first }) put(n, b, true)
        }
        return out.toByteArray()
    }
}

/** lots-catalog.json: the LotMeta list for the server's publish endpoint (feature = learn) + what the screens show. */
object LearnLotCatalogFile {
    class Entry(val meta: LotMeta, val file: String, val date: String, val packs: List<String>, val lessons: Int)

    fun write(entries: List<Entry>): String = Json.write(linkedMapOf("format" to 1, "feature" to LotFormat.FEATURE, "lots" to entries.map { e ->
        linkedMapOf("feature" to e.meta.id.feature, "scope" to e.meta.id.scope, "version" to e.meta.version, "bytes" to e.meta.bytes, "sha256" to e.meta.sha256,
            "title" to e.meta.title, "minAppVersion" to e.meta.minAppVersion, "file" to e.file, "date" to e.date, "packs" to e.packs, "lessons" to e.lessons)
    })) + "\n"

    @Suppress("UNCHECKED_CAST")
    fun parse(json: String): List<LotMeta> = (Json.obj(json)["lots"] as? List<Any?>).orEmpty().mapNotNull { o ->
        val m = o as? Map<String, Any?> ?: return@mapNotNull null
        LotMeta(LotId(m["feature"] as? String ?: return@mapNotNull null, m["scope"] as? String ?: return@mapNotNull null), (m["version"] as? Number)?.toInt() ?: return@mapNotNull null,
            (m["bytes"] as? Number)?.toLong() ?: 0, m["sha256"] as? String ?: "", m["title"] as? String ?: "", (m["minAppVersion"] as? Number)?.toInt() ?: 0)
    }
}
