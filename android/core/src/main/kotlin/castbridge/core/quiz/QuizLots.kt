package castbridge.core.quiz

import castbridge.core.lots.LotConsumer
import castbridge.core.lots.LotId
import castbridge.core.lots.LotMeta
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest

/**
 * Quiz « lots » (docs/QUIZ.md, « Lots »): a lot is ONE homogeneous, versioned, signed question pack of one scope (a class, a
 * level, a field, or one region of general knowledge). It is the existing pack format (`manifest.json` + `questions.json`,
 * file `quiz-<scope>-p1-v<version>.quiz.zip`) plus an `index.json` (version, question count, hash of every question) so that
 * an update is cheap to describe and a changed question keeps its id, hence its place in the anti-repetition history.
 * The TV is OFFLINE by assumption: everything here works on local files only.
 */
object QuizLotScopes {
    const val FEATURE = "quiz"
    /** A lot above this size is a build error (tools/quiz-bank): a TV downloads through a flaky phone link. */
    const val MAX_LOT_BYTES = 3L shl 20

    data class Spec(val scope: String, val title: String, val track: Track, val level: String?, val field: String?, val region: Region? = null) {
        val course: String = listOfNotNull(track.key, level, field).joinToString("/")
        fun filter() = QuestionFilter(track, level, field)
        fun matches(q: Question) = q.track == track && q.level == level && q.field == field && (region == null || q.region == region)
    }

    /** The lots that exist today (docs/QUIZ.md table). Other scopes follow the same naming rule, see [scopeFor]. */
    val specs: List<Spec> = listOf(
        Spec("culture-cm", "Culture générale · Cameroun", Track.GENERAL, null, null, Region.CM),
        Spec("culture-afrique", "Culture générale · Afrique", Track.GENERAL, null, null, Region.AF),
        Spec("culture-monde", "Culture générale · Monde", Track.GENERAL, null, null, Region.WORLD),
        Spec("cm2", "Primaire · CM2", Track.PRIMARY, "CM2", null),
        Spec("3e", "Secondaire · 3e", Track.SECONDARY, "3e", null),
        Spec("tle", "Secondaire · Terminale", Track.SECONDARY, "Tle", null),
        Spec("droit-l1", "Supérieur · L1 Droit", Track.HIGHER, "L1", "droit"),
        Spec("eco-l1", "Supérieur · L1 Économie", Track.HIGHER, "L1", "economie"),
        Spec("maths-l1", "Supérieur · L1 Mathématiques", Track.HIGHER, "L1", "mathematiques"),
    )
    private val SAFE_SCOPE = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")
    fun safeScope(s: String) = s.length <= 40 && SAFE_SCOPE.matches(s)

    fun spec(scope: String): Spec? = specs.firstOrNull { it.scope == scope }
    fun title(scope: String) = spec(scope)?.title ?: scope
    val generalScopes: List<String> get() = specs.filter { it.track == Track.GENERAL }.map { it.scope }

    private val FIELD_ALIAS = mapOf("economie" to "eco", "mathematiques" to "maths")
    private fun slug(s: String) = s.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

    /** The naming rule: general knowledge by region, school levels by slug ("3e", "class-1", "lower-sixth"), higher by "<field>-<level>". */
    fun scopeFor(track: Track, level: String?, field: String?, region: Region? = null): String = when (track) {
        Track.GENERAL -> when (region) { Region.AF -> "culture-afrique"; Region.WORLD -> "culture-monde"; else -> "culture-cm" }
        Track.PRIMARY, Track.SECONDARY -> slug(level ?: "general")
        Track.HIGHER -> listOfNotNull(field?.let { FIELD_ALIAS[it] ?: slug(it) }, level?.let(::slug)).joinToString("-").ifEmpty { "superieur" }
    }

    fun scopeOf(q: Question): String = scopeFor(q.track, q.level, q.field, q.region)

    /** Scopes whose questions are drawn for a course (general knowledge draws from three lots: 70/20/10). */
    fun scopesOf(filter: QuestionFilter): List<String> =
        if (filter.track == Track.GENERAL) generalScopes else listOf(scopeFor(filter.track, filter.level, filter.field))

    /**
     * The order in which the framework's LotPlanner should fill a TV (and a phone) for this profile, most useful first:
     * 1. the lots of the profile's own course; 2. general knowledge (every family member plays it); 3. the courses already
     * played here ([played], course keys as in [courseKey], most recent first); 4. the neighbouring levels of the same
     * track (nearest first); 5. every other lot we know. Without a profile: general knowledge then the rest.
     */
    fun quizPriority(track: Track? = null, level: String? = null, field: String? = null, played: List<String> = emptyList()): List<String> {
        val out = LinkedHashSet<String>()
        if (track != null && track != Track.GENERAL) out += scopeFor(track, level, field)
        out += generalScopes
        for (c in played) filterOfCourse(c)?.let { out += scopesOf(it) }
        if (track != null && track != Track.GENERAL) {
            val levels = QuizCatalog.levels(track).map { it.key }
            val at = levels.indexOf(level)
            if (at >= 0) levels.withIndex().filter { it.index != at }.sortedBy { Math.abs(it.index - at) }.forEach { out += scopeFor(track, it.value, field) }
        }
        specs.forEach { out += it.scope }
        return out.toList()
    }
}

/** `index.json` of a lot: the version, the number of questions and a short hash of each, so that two versions can be compared. */
object QuizLotIndex {
    class Index(val scope: String, val version: Int, val count: Int, val contentHash: String, val hashes: Map<String, String>)
    class Diff(val added: Set<String>, val changed: Set<String>, val removed: Set<String>) {
        val empty: Boolean get() = added.isEmpty() && changed.isEmpty() && removed.isEmpty()
    }

    private fun sha256(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    /** First 8 hex digits of SHA-256 of the question's fields joined by U+001F (choices by U+001E); tools/quiz-bank/qb/lots.py computes the same. */
    fun questionHash(m: Map<String, Any?>): String {
        fun s(k: String) = (m[k] as? String).orEmpty()
        val choices = (m["choices"] as? List<*>).orEmpty().joinToString("\u001e") { it as? String ?: "" }
        val fields = listOf(s("id"), s("track").ifEmpty { "general" }, s("level"), s("field"), s("region"), s("category"), ((m["difficulty"] as? Number)?.toLong() ?: 0).toString(),
            s("question"), choices, ((m["answer"] as? Number)?.toLong() ?: -1).toString(), s("explanation"), s("source"), s("status"), s("verif"), s("lang").ifEmpty { "fr" })
        return sha256(fields.joinToString("\u001f")).take(8)
    }

    fun contentHash(hashes: Map<String, String>): String = sha256(hashes.toSortedMap().entries.joinToString("") { "${it.key}:${it.value}\n" })

    fun parse(bytes: ByteArray): Index? = runCatching {
        val m = Json.obj(String(bytes, Charsets.UTF_8))
        @Suppress("UNCHECKED_CAST")
        val q = (m["q"] as? Map<String, Any?>).orEmpty().mapValues { it.value as? String ?: error("hash") }
        Index(m["scope"] as String, (m["version"] as Number).toInt(), (m["count"] as Number).toInt(), m["contentHash"] as String, q)
    }.getOrNull()

    /** What changed from [old] to [new]; the ids stay the same for a changed question (its history slot is kept). */
    fun diff(old: Index?, new: Index): Diff {
        val o = old?.hashes.orEmpty()
        return Diff(new.hashes.keys.filter { it !in o }.toSet(), new.hashes.filter { (k, v) -> o[k] != null && o[k] != v }.keys, o.keys.filter { it !in new.hashes }.toSet())
    }
}

/** Reads and checks a lot file: the pack checks, then the index (every question hash, count, content hash) and the scope of every question. */
object QuizLotFormat {
    class Lot(val scope: String, val version: Int, val manifest: Map<String, Any?>, val bank: QuizBank, val index: QuizLotIndex.Index)

    @Throws(QuizPackFormat.PackError::class)
    fun read(file: File, expectScope: String? = null): Lot {
        val c = QuizPackFormat.read(file)
        val raw = c.index ?: throw QuizPackFormat.PackError("index.json manquant : ce n'est pas un lot")
        val index = QuizLotIndex.parse(raw) ?: throw QuizPackFormat.PackError("index.json illisible")
        @Suppress("UNCHECKED_CAST")
        val lot = c.manifest["lot"] as? Map<String, Any?> ?: throw QuizPackFormat.PackError("manifeste sans bloc « lot »")
        val scope = lot["scope"] as? String ?: throw QuizPackFormat.PackError("lot sans thème")
        val version = (c.manifest["version"] as? Number)?.toInt() ?: throw QuizPackFormat.PackError("lot sans version")
        if (!QuizLotScopes.safeScope(scope)) throw QuizPackFormat.PackError("nom de thème refusé")
        if (expectScope != null && scope != expectScope) throw QuizPackFormat.PackError("le lot est celui de « $scope », pas de « $expectScope »")
        if (index.scope != scope || index.version != version) throw QuizPackFormat.PackError("index d'un autre lot ou d'une autre version")
        val raws = try { Json.obj(String(c.questionsRaw!!, Charsets.UTF_8)).let { (it["questions"] as List<*>).map { q -> @Suppress("UNCHECKED_CAST") (q as Map<String, Any?>) } } }
            catch (e: Exception) { throw QuizPackFormat.PackError("questions illisibles") }
        val actual = raws.associate { (it["id"] as? String).orEmpty() to QuizLotIndex.questionHash(it) }
        if (actual.size != raws.size || actual != index.hashes || index.count != actual.size) throw QuizPackFormat.PackError("l'index ne correspond pas aux questions")
        if (QuizLotIndex.contentHash(actual) != index.contentHash) throw QuizPackFormat.PackError("empreinte du contenu invalide")
        c.bank.all.firstOrNull { QuizLotScopes.scopeOf(it) != scope }?.let { throw QuizPackFormat.PackError("question ${it.id} hors du thème « $scope »") }
        return Lot(scope, version, c.manifest, c.bank, index)
    }
}

/**
 * The Quiz side of the lots framework: installs verified lot files into [dir] (one folder per scope), atomically, keeping
 * the previous version for [rollback]. Pure JVM, no network, no Android. A lot is refused unless: it is a quiz lot of the
 * announced scope and version, its size and SHA-256 match the [LotMeta], its structure/index/questions are valid, its
 * signature is valid ([signatureOf] gives the Ed25519 signature of the lot's catalog entry, the same one that signs packs:
 * [QuizPackInfo.canonicalPayload] with `id = scope`, `part = 1`, `parts = 1`), it is not older than what is installed and
 * it fits [maxBytes]. Whatever happens, the lot that was installed before stays usable (a power cut in the middle included).
 */
class QuizLotConsumer(
    val dir: File,
    private val publicKeys: List<String> = castbridge.core.update.UpdateKeys.PUBLIC_KEYS,
    private val requireSignature: Boolean = true,
    private val signatureOf: (LotMeta) -> String? = { null },
    /** Quiz lots together, in bytes on disk (the framework's planner shares the 10 MB of the TV with Apprendre). */
    val maxBytes: Long = Long.MAX_VALUE,
    private val appVersion: Int = Int.MAX_VALUE,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Called after every change of the installed lots (the question source rebuilds its bank). */
    private val onChanged: () -> Unit = {},
) : LotConsumer {
    override val feature: String = QuizLotScopes.FEATURE

    sealed class Result {
        /** [replaced] = the version it replaced (null = first install or the same lot again), [diff] = what changed in the questions. */
        data class Ok(val replaced: Int?, val diff: QuizLotIndex.Diff?) : Result()
        data class Refused(val reason: String) : Result()
    }

    private val lock = Any()
    private val banks = HashMap<String, Pair<String, QuizBank>>()
    @Volatile private var merged: QuizBank? = null
    /** Test hook: called at named steps of an installation; throwing simulates a crash there. */
    internal var failAt: (String) -> Unit = {}

    init { recover() }

    private fun d(scope: String) = File(dir, scope)
    private class Stored(val meta: LotMeta, val questions: Int, val installedAt: Long)

    private fun read(json: File): Stored? = runCatching {
        val m = Json.obj(json.readText(Charsets.UTF_8))
        @Suppress("UNCHECKED_CAST")
        val x = m["meta"] as Map<String, Any?>
        Stored(LotMeta(LotId(feature, x["scope"] as String), (x["version"] as Number).toInt(), (x["bytes"] as Number).toLong(), x["sha256"] as String,
            x["title"] as String, (x["minAppVersion"] as? Number)?.toInt() ?: 0), (m["questions"] as Number).toInt(), (m["installedAt"] as? Number)?.toLong() ?: 0L)
    }.getOrNull()

    private fun write(json: File, meta: LotMeta, questions: Int) {
        json.writeText(Json.write(linkedMapOf("meta" to linkedMapOf("scope" to meta.id.scope, "version" to meta.version, "bytes" to meta.bytes, "sha256" to meta.sha256,
            "title" to meta.title, "minAppVersion" to meta.minAppVersion), "questions" to questions, "installedAt" to clock())), Charsets.UTF_8)
    }

    private fun current(scope: String): Stored? {
        val z = File(d(scope), "lot.zip"); val s = read(File(d(scope), "lot.json")) ?: return null
        return s.takeIf { z.isFile && z.length() == it.meta.bytes }
    }

    /** Completes or undoes what a crash interrupted: a new lot staged but not in place, or the old one moved away but not replaced. */
    private fun recover() = synchronized(lock) {
        for (sd in dir.listFiles { f -> f.isDirectory }.orEmpty()) {
            val z = File(sd, "lot.zip"); val j = File(sd, "lot.json"); val nz = File(sd, "new.zip"); val nj = File(sd, "new.json")
            if (current(sd.name) == null) {
                if (z.isFile && !j.isFile && nj.isFile && nz.length() == 0L) nj.renameTo(j)      // json step of a swap
                if (current(sd.name) == null) {
                    z.delete(); j.delete()
                    val pz = File(sd, "prev.zip"); val pj = File(sd, "prev.json")
                    if (pz.isFile && pj.isFile) { pz.renameTo(z); pj.renameTo(j) }               // back to the previous version
                }
            }
            nz.delete(); nj.delete()
            if (current(sd.name) == null && !File(sd, "prev.zip").isFile) sd.deleteRecursively()
        }
    }

    override fun install(meta: LotMeta, data: File): Boolean = installDetailed(meta, data) is Result.Ok

    fun installDetailed(meta: LotMeta, data: File): Result = synchronized(lock) {
        fun no(why: String): Result = Result.Refused(why)
        val scope = meta.id.scope
        if (meta.id.feature != feature) return no("ce n'est pas un lot du Quiz")
        if (!QuizLotScopes.safeScope(scope)) return no("nom de thème refusé")
        if (meta.minAppVersion > appVersion) return no("lot trop récent pour cette version de l'app")
        if (!data.isFile) return no("fichier absent")
        if (data.length() != meta.bytes) return no("taille ${data.length()} au lieu de ${meta.bytes} octets")
        if (!QuizPackFormat.sha256(data).equals(meta.sha256, ignoreCase = true)) return no("empreinte SHA-256 différente (fichier corrompu ou modifié)")
        val lot = try { QuizLotFormat.read(data, scope) } catch (e: QuizPackFormat.PackError) { return no(e.message ?: "lot invalide") }
        if (lot.version != meta.version) return no("version du lot (${lot.version}) différente de celle annoncée (${meta.version})")
        if (requireSignature) {
            val sig = signatureOf(meta) ?: return no("signature absente : lot ignoré")
            val info = QuizPackInfo(scope, (lot.manifest["track"] as? String).orEmpty(), lot.manifest["level"] as? String, lot.manifest["field"] as? String, 1, 1, meta.version,
                fileName(scope, meta.version), meta.bytes, meta.sha256.lowercase(), lot.bank.all.size, null, sig)
            if (publicKeys.none { info.signatureValid(it) }) return no("signature invalide : lot ignoré")
        }
        val cur = current(scope)
        if (cur != null) {
            if (meta.version < cur.meta.version) return no("une version plus récente (${cur.meta.version}) est déjà installée")
            if (meta.version == cur.meta.version) return if (cur.meta.sha256.equals(meta.sha256, true)) Result.Ok(null, null) else no("même version, contenu différent")
        }
        val others = installed().filter { it.id.scope != scope }.sumOf { it.bytes }
        if (others + meta.bytes > maxBytes) return no("budget de ${maxBytes / 1_000_000} Mo dépassé : aucun lot retiré sans votre accord")
        val oldIndex = cur?.let { runCatching { QuizLotFormat.read(File(d(scope), "lot.zip"), scope).index }.getOrNull() }

        val sd = d(scope); sd.mkdirs()
        val z = File(sd, "lot.zip"); val j = File(sd, "lot.json"); val pz = File(sd, "prev.zip"); val pj = File(sd, "prev.json")
        val nz = File(sd, "new.zip"); val nj = File(sd, "new.json")
        var movedZip = false; var movedJson = false
        try {
            FileOutputStream(nz).use { out -> data.inputStream().use { it.copyTo(out) }; out.fd.sync() }
            if (nz.length() != meta.bytes) throw IOException("copie incomplète")
            write(nj, meta, lot.bank.all.size)
            failAt("staged")
            if (cur != null) {
                pz.delete(); pj.delete()
                if (!z.renameTo(pz)) throw IOException("sauvegarde de l'ancienne version impossible")
                movedZip = true
                if (!j.renameTo(pj)) throw IOException("sauvegarde de l'ancienne version impossible")
                movedJson = true
            }
            failAt("backedUp")
            if (!nz.renameTo(z)) throw IOException("mise en place impossible")
            failAt("zipInPlace")
            if (!nj.renameTo(j)) throw IOException("mise en place impossible")
        } catch (e: Exception) {
            nz.delete(); nj.delete()
            if (cur == null) sd.deleteRecursively()
            else if (movedZip || movedJson) {                                                        // the old lot comes back
                z.delete(); j.delete()
                if (movedZip) pz.renameTo(z)
                if (movedJson) pj.renameTo(j)
            }
            return no("installation annulée, l'ancienne version est conservée : ${e.message}")
        }
        banks.remove(scope); merged = null
        onChanged()
        return Result.Ok(cur?.meta?.version, QuizLotIndex.diff(oldIndex, lot.index))
    }

    /** Puts the previous version of [id] back (a bad lot found after the fact). false = there is no previous version. */
    fun rollback(id: LotId): Boolean = synchronized(lock) {
        val sd = d(id.scope); val pz = File(sd, "prev.zip"); val pj = File(sd, "prev.json")
        if (id.feature != feature || !pz.isFile || read(pj)?.takeIf { it.meta.bytes == pz.length() } == null) return false
        File(sd, "lot.zip").delete(); File(sd, "lot.json").delete()
        if (!pz.renameTo(File(sd, "lot.zip")) || !pj.renameTo(File(sd, "lot.json"))) { recover(); return false }
        banks.remove(id.scope); merged = null; onChanged(); true
    }

    override fun remove(id: LotId) {
        if (id.feature != feature || !QuizLotScopes.safeScope(id.scope)) return
        synchronized(lock) { d(id.scope).deleteRecursively(); banks.remove(id.scope); merged = null }
        onChanged()
    }

    override fun installed(): List<LotMeta> = synchronized(lock) {
        dir.listFiles { f -> f.isDirectory }.orEmpty().sortedBy { it.name }.mapNotNull { current(it.name)?.meta }
    }

    fun version(scope: String): Int? = synchronized(lock) { current(scope)?.meta?.version }
    /** When this lot was installed (ms), for the « données à jour depuis… » line. */
    fun installedAt(scope: String): Long? = synchronized(lock) { current(scope)?.installedAt?.takeIf { it > 0 } }
    fun usedBytes(): Long = installed().sumOf { it.bytes }
    fun hasPrevious(scope: String): Boolean = File(d(scope), "prev.zip").isFile

    /** Every question of the installed lots (a damaged lot is skipped, and listed as not installed after the next [installed]). */
    fun bank(): QuizBank = merged ?: synchronized(lock) {
        merged ?: run {
            val byId = LinkedHashMap<String, Question>()
            for (m in installed()) bankOf(m)?.all?.forEach { byId[it.id] = it }
            QuizBank(byId.values.toList())
        }.also { merged = it }
    }

    private fun bankOf(m: LotMeta): QuizBank? =
        banks[m.id.scope]?.takeIf { it.first == m.sha256 }?.second
            ?: runCatching { QuizLotFormat.read(File(d(m.id.scope), "lot.zip"), m.id.scope).bank }.getOrNull()?.also { banks[m.id.scope] = m.sha256 to it }

    companion object {
        fun fileName(scope: String, version: Int) = "quiz-$scope-p1-v$version${QUIZ_PACK_SUFFIX}"
    }
}
