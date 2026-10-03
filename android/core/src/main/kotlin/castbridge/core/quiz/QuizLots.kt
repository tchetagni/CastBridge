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
        Spec("cp", "Primaire · CP", Track.PRIMARY, "CP", null),
        Spec("ce1", "Primaire · CE1", Track.PRIMARY, "CE1", null),
        Spec("ce2", "Primaire · CE2", Track.PRIMARY, "CE2", null),
        Spec("cm1", "Primaire · CM1", Track.PRIMARY, "CM1", null),
        Spec("6e", "Secondaire · 6e", Track.SECONDARY, "6e", null),
        Spec("5e", "Secondaire · 5e", Track.SECONDARY, "5e", null),
        Spec("4e", "Secondaire · 4e", Track.SECONDARY, "4e", null),
        Spec("class-1", "Primary (EN) · Class 1", Track.PRIMARY, "Class 1", null),
        Spec("class-2", "Primary (EN) · Class 2", Track.PRIMARY, "Class 2", null),
        Spec("class-3", "Primary (EN) · Class 3", Track.PRIMARY, "Class 3", null),
        Spec("class-4", "Primary (EN) · Class 4", Track.PRIMARY, "Class 4", null),
        Spec("class-5", "Primary (EN) · Class 5", Track.PRIMARY, "Class 5", null),
        Spec("class-6", "Primary (EN) · Class 6", Track.PRIMARY, "Class 6", null),
        Spec("form-1", "Secondary (EN) · Form 1", Track.SECONDARY, "Form 1", null),
        Spec("form-2", "Secondary (EN) · Form 2", Track.SECONDARY, "Form 2", null),
        Spec("form-3", "Secondary (EN) · Form 3", Track.SECONDARY, "Form 3", null),
        Spec("droit-l2", "Supérieur · L2 Droit", Track.HIGHER, "L2", "droit"),
        Spec("droit-l3", "Supérieur · L3 Droit", Track.HIGHER, "L3", "droit"),
        Spec("eco-l2", "Supérieur · L2 Économie", Track.HIGHER, "L2", "economie"),
        Spec("eco-l3", "Supérieur · L3 Économie", Track.HIGHER, "L3", "economie"),
        Spec("maths-l2", "Supérieur · L2 Mathématiques", Track.HIGHER, "L2", "mathematiques"),
        Spec("informatique-l1", "Supérieur · L1 Informatique", Track.HIGHER, "L1", "informatique"),
        Spec("informatique-l2", "Supérieur · L2 Informatique", Track.HIGHER, "L2", "informatique"),
        Spec("informatique-l3", "Supérieur · L3 Informatique", Track.HIGHER, "L3", "informatique"),
        Spec("biologie-l1", "Supérieur · L1 Biologie (santé publique de base)", Track.HIGHER, "L1", "biologie"),
        Spec("sociologie-l1", "Supérieur · L1 Sociologie (méthodologie)", Track.HIGHER, "L1", "sociologie"),
        Spec("2nde-chimie", "Lycée · 2nde · Chimie", Track.SECONDARY, "2nde", "chimie"),
        Spec("1re-chimie", "Lycée · 1re · Chimie", Track.SECONDARY, "1re", "chimie"),
        Spec("tle-chimie", "Lycée · Tle · Chimie", Track.SECONDARY, "Tle", "chimie"),
        Spec("form-5-chimie", "GCE Ordinary Level · Chemistry", Track.SECONDARY, "Form 5", "chimie"),
        Spec("lower-sixth-chimie", "GCE Advanced Level, Lower Sixth · Chemistry", Track.SECONDARY, "Lower Sixth", "chimie"),
        Spec("upper-sixth-chimie", "GCE Advanced Level, Upper Sixth · Chemistry", Track.SECONDARY, "Upper Sixth", "chimie"),
        Spec("2nde-litterature", "Lycée · 2nde · Français / Littérature / Anglais", Track.SECONDARY, "2nde", "litterature"),
        Spec("1re-litterature", "Lycée · 1re · Français / Littérature / Anglais", Track.SECONDARY, "1re", "litterature"),
        Spec("tle-litterature", "Lycée · Tle · Français / Littérature / Anglais", Track.SECONDARY, "Tle", "litterature"),
        Spec("form-5-litterature", "GCE Ordinary Level · English Language and Literature", Track.SECONDARY, "Form 5", "litterature"),
        Spec("lower-sixth-litterature", "GCE Advanced Level, Lower Sixth · English Language and Literature", Track.SECONDARY, "Lower Sixth", "litterature"),
        Spec("upper-sixth-litterature", "GCE Advanced Level, Upper Sixth · English Language and Literature", Track.SECONDARY, "Upper Sixth", "litterature"),
        Spec("2nde-informatique", "Lycée · 2nde · Informatique", Track.SECONDARY, "2nde", "informatique"),
        Spec("1re-informatique", "Lycée · 1re · Informatique", Track.SECONDARY, "1re", "informatique"),
        Spec("tle-informatique", "Lycée · Tle · Informatique", Track.SECONDARY, "Tle", "informatique"),
        Spec("form-5-informatique", "GCE Ordinary Level · Computer Science", Track.SECONDARY, "Form 5", "informatique"),
        Spec("lower-sixth-informatique", "GCE Advanced Level, Lower Sixth · Computer Science", Track.SECONDARY, "Lower Sixth", "informatique"),
        Spec("upper-sixth-informatique", "GCE Advanced Level, Upper Sixth · Computer Science", Track.SECONDARY, "Upper Sixth", "informatique"),
        Spec("2nde-biologie", "Lycée · 2nde · SVT", Track.SECONDARY, "2nde", "biologie"),
        Spec("1re-biologie", "Lycée · 1re · SVT", Track.SECONDARY, "1re", "biologie"),
        Spec("tle-biologie", "Lycée · Tle · SVT", Track.SECONDARY, "Tle", "biologie"),
        Spec("form-5-biologie", "GCE Ordinary Level · Biology", Track.SECONDARY, "Form 5", "biologie"),
        Spec("lower-sixth-biologie", "GCE Advanced Level, Lower Sixth · Biology", Track.SECONDARY, "Lower Sixth", "biologie"),
        Spec("upper-sixth-biologie", "GCE Advanced Level, Upper Sixth · Biology", Track.SECONDARY, "Upper Sixth", "biologie"),
        Spec("2nde-economie", "Lycée · 2nde · Économie", Track.SECONDARY, "2nde", "economie"),
        Spec("1re-economie", "Lycée · 1re · Économie", Track.SECONDARY, "1re", "economie"),
        Spec("tle-economie", "Lycée · Tle · Économie", Track.SECONDARY, "Tle", "economie"),
        Spec("form-5-economie", "GCE Ordinary Level · Economics", Track.SECONDARY, "Form 5", "economie"),
        Spec("lower-sixth-economie", "GCE Advanced Level, Lower Sixth · Economics", Track.SECONDARY, "Lower Sixth", "economie"),
        Spec("upper-sixth-economie", "GCE Advanced Level, Upper Sixth · Economics", Track.SECONDARY, "Upper Sixth", "economie"),
        Spec("2nde-geographie", "Lycée · 2nde · Géographie", Track.SECONDARY, "2nde", "geographie"),
        Spec("1re-geographie", "Lycée · 1re · Géographie", Track.SECONDARY, "1re", "geographie"),
        Spec("tle-geographie", "Lycée · Tle · Géographie", Track.SECONDARY, "Tle", "geographie"),
        Spec("form-5-geographie", "GCE Ordinary Level · Geography", Track.SECONDARY, "Form 5", "geographie"),
        Spec("lower-sixth-geographie", "GCE Advanced Level, Lower Sixth · Geography", Track.SECONDARY, "Lower Sixth", "geographie"),
        Spec("upper-sixth-geographie", "GCE Advanced Level, Upper Sixth · Geography", Track.SECONDARY, "Upper Sixth", "geographie"),
        Spec("form-5-mathematiques", "GCE Ordinary Level · Mathematics", Track.SECONDARY, "Form 5", "mathematiques"),
        Spec("lower-sixth-mathematiques", "GCE Advanced Level, Lower Sixth · Mathematics", Track.SECONDARY, "Lower Sixth", "mathematiques"),
        Spec("upper-sixth-mathematiques", "GCE Advanced Level, Upper Sixth · Mathematics", Track.SECONDARY, "Upper Sixth", "mathematiques"),
        Spec("2nde-mathematiques", "Lycée · 2nde · Mathématiques", Track.SECONDARY, "2nde", "mathematiques"),
        Spec("1re-mathematiques", "Lycée · 1re · Mathématiques", Track.SECONDARY, "1re", "mathematiques"),
        Spec("tle-mathematiques", "Lycée · Tle · Mathématiques", Track.SECONDARY, "Tle", "mathematiques"),
        Spec("2nde-physique", "Lycée · 2nde · Physique", Track.SECONDARY, "2nde", "physique"),
        Spec("1re-physique", "Lycée · 1re · Physique", Track.SECONDARY, "1re", "physique"),
        Spec("tle-physique", "Lycée · Tle · Physique", Track.SECONDARY, "Tle", "physique"),
        Spec("form-5-physique", "GCE Ordinary Level · Physics", Track.SECONDARY, "Form 5", "physique"),
        Spec("lower-sixth-physique", "GCE Advanced Level, Lower Sixth · Physics", Track.SECONDARY, "Lower Sixth", "physique"),
        Spec("upper-sixth-physique", "GCE Advanced Level, Upper Sixth · Physics", Track.SECONDARY, "Upper Sixth", "physique"),
        Spec("1re-histoire", "Lycée · 1re · Histoire", Track.SECONDARY, "1re", "histoire"),
        Spec("tle-histoire", "Lycée · Tle · Histoire", Track.SECONDARY, "Tle", "histoire"),
        Spec("2nde-histoire", "Lycée · 2nde · Histoire", Track.SECONDARY, "2nde", "histoire"),
        Spec("lower-sixth-histoire", "GCE Advanced Level, Lower Sixth · History", Track.SECONDARY, "Lower Sixth", "histoire"),
        Spec("upper-sixth-histoire", "GCE Advanced Level, Upper Sixth · History", Track.SECONDARY, "Upper Sixth", "histoire"),
        Spec("form-5-histoire", "GCE Ordinary Level · History", Track.SECONDARY, "Form 5", "histoire"),
        Spec("2nde-droit", "Lycée · 2nde · ECM", Track.SECONDARY, "2nde", "droit"),
        Spec("1re-droit", "Lycée · 1re · ECM", Track.SECONDARY, "1re", "droit"),
        Spec("tle-droit", "Lycée · Tle · ECM", Track.SECONDARY, "Tle", "droit"),
        Spec("tle-philosophie", "Lycée · Tle · Philosophie", Track.SECONDARY, "Tle", "philosophie"),
        // Supérieur : les 26 cellules niveau × filière restantes (tools/quiz-bank/qb/courses_sup.py), sans lot tant qu'elles n'ont pas de questions
        Spec("physique-l1", "Supérieur · L1 Physique", Track.HIGHER, "L1", "physique"),
        Spec("psychologie-l1", "Supérieur · L1 Psychologie", Track.HIGHER, "L1", "psychologie"),
        Spec("geographie-l1", "Supérieur · L1 Géographie", Track.HIGHER, "L1", "geographie"),
        Spec("litterature-l1", "Supérieur · L1 Littérature", Track.HIGHER, "L1", "litterature"),
        Spec("histoire-l1", "Supérieur · L1 Histoire", Track.HIGHER, "L1", "histoire"),
        Spec("chimie-l1", "Supérieur · L1 Chimie", Track.HIGHER, "L1", "chimie"),
        Spec("philosophie-l1", "Supérieur · L1 Philosophie", Track.HIGHER, "L1", "philosophie"),
        Spec("physique-l2", "Supérieur · L2 Physique", Track.HIGHER, "L2", "physique"),
        Spec("psychologie-l2", "Supérieur · L2 Psychologie", Track.HIGHER, "L2", "psychologie"),
        Spec("geographie-l2", "Supérieur · L2 Géographie", Track.HIGHER, "L2", "geographie"),
        Spec("litterature-l2", "Supérieur · L2 Littérature", Track.HIGHER, "L2", "litterature"),
        Spec("histoire-l2", "Supérieur · L2 Histoire", Track.HIGHER, "L2", "histoire"),
        Spec("chimie-l2", "Supérieur · L2 Chimie", Track.HIGHER, "L2", "chimie"),
        Spec("biologie-l2", "Supérieur · L2 Biologie", Track.HIGHER, "L2", "biologie"),
        Spec("philosophie-l2", "Supérieur · L2 Philosophie", Track.HIGHER, "L2", "philosophie"),
        Spec("sociologie-l2", "Supérieur · L2 Sociologie", Track.HIGHER, "L2", "sociologie"),
        Spec("maths-l3", "Supérieur · L3 Mathématiques", Track.HIGHER, "L3", "mathematiques"),
        Spec("physique-l3", "Supérieur · L3 Physique", Track.HIGHER, "L3", "physique"),
        Spec("psychologie-l3", "Supérieur · L3 Psychologie", Track.HIGHER, "L3", "psychologie"),
        Spec("geographie-l3", "Supérieur · L3 Géographie", Track.HIGHER, "L3", "geographie"),
        Spec("litterature-l3", "Supérieur · L3 Littérature", Track.HIGHER, "L3", "litterature"),
        Spec("histoire-l3", "Supérieur · L3 Histoire", Track.HIGHER, "L3", "histoire"),
        Spec("chimie-l3", "Supérieur · L3 Chimie", Track.HIGHER, "L3", "chimie"),
        Spec("biologie-l3", "Supérieur · L3 Biologie", Track.HIGHER, "L3", "biologie"),
        Spec("philosophie-l3", "Supérieur · L3 Philosophie", Track.HIGHER, "L3", "philosophie"),
        Spec("sociologie-l3", "Supérieur · L3 Sociologie", Track.HIGHER, "L3", "sociologie"),
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
        Track.PRIMARY -> slug(level ?: "general")
        Track.SECONDARY -> listOfNotNull(slug(level ?: "general"), field?.let(::slug)).joinToString("-")   // field = a subject of the lycée (2nde-chimie); none = the whole class (3e, tle)
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
