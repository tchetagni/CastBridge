package castbridge.core.learn

import castbridge.core.quiz.Json
import java.io.File

/**
 * « Contenu de base » (docs/LEARN.md § Contenu de base): on a fresh TV with no lot, EVERY class and EVERY subject offers a few
 * foundational fiches, so that the class mode (« mode classe ») has something to show everywhere.
 *
 * A base pack is DERIVED at build time from a full pack of content/learn (nothing is copied by hand, the sources are not edited):
 * the first [LESSONS_PER_PACK] fiches by chapter order, with their exercises, self-check questions and figures (the blocks are kept
 * as they are), no mock exam. It is a regular pack: id `base-<full pack id>`, title « … (contenu de base) », same version, level,
 * subject and exam as its source, and the SAME lesson and exercise ids (so the progress of a student carries over to the full lessons).
 * The flag is the id prefix: [isBase].
 *
 * Override: a base pack disappears from [LearnLibrary.packs] as soon as its full pack is available (installed lot, pack on a drive) or
 * as soon as a lot of its class is installed; its ids then resolve to the full pack ([LearnLibrary.ref]). So a lot never shows the
 * same lesson twice.
 */
object BaseContent {
    const val PREFIX = "base-"
    const val TITLE_SUFFIX = " (contenu de base)"
    /** Fiches kept per full pack (the first ones by chapter order). 2 keeps the TV starter small (see docs/LEARN.md). */
    const val LESSONS_PER_PACK = 2
    /** What the screens say of a class that only has the base content. */
    const val CLASS_LABEL = "Contenu de base — leçons complètes à recevoir du téléphone"

    fun isBase(id: String) = id.startsWith(PREFIX)
    fun idFor(fullPackId: String) = PREFIX + fullPackId
    /** The full pack a base pack comes from, or null when [id] is not a base id. */
    fun fullIdOf(id: String): String? = if (isBase(id)) id.removePrefix(PREFIX) else null
    fun title(m: PackManifest) = m.title.removeSuffix(TITLE_SUFFIX)

    /** One base pack to build: its source folder, the lot (scope) it belongs to and the derived sources. */
    class Plan(val dir: File, val scope: String, val subject: String, val files: Map<String, ByteArray>)

    /**
     * The base packs of [content]: every pack of scopes.txt that is not embedded in full, except for a (scope, subject) that an
     * embedded full pack already covers. Sorted by pack id (reproducible).
     */
    fun plan(content: File, lessons: Int = LESSONS_PER_PACK): List<Plan> {
        val table = File(content, "scopes.txt").takeIf { it.isFile }?.let { LearnScopes.parseTable(it.readText()) } ?: throw IllegalStateException("content/learn/scopes.txt absent")
        val full = LearnTool.embeddedIds(content)
        fun subjectOf(d: File) = Json.obj(File(d, "pack.json").readText(Charsets.UTF_8))["subject"] as? String ?: ""
        val dirs = LearnTool.packDirs(content)
        val covered = dirs.filter { it.name in full }.map { (table.scopeOf(it.name) ?: "") to subjectOf(it) }.toSet()
        return dirs.filter { it.name !in full }.mapNotNull { d ->
            val scope = table.scopeOf(d.name) ?: throw IllegalStateException("pack ${d.name} sans lot dans scopes.txt")
            val subject = subjectOf(d)
            if ((scope to subject) in covered) null
            else derive(PackBuilder.sources(d), lessons)?.let { Plan(d, scope, subject, it) }
        }
    }

    /** The base sources of a full pack (path → bytes), or null when it has no lesson. */
    @Suppress("UNCHECKED_CAST")
    fun derive(files: Map<String, ByteArray>, lessons: Int = LESSONS_PER_PACK): Map<String, ByteArray>? {
        val pack = Json.obj(String(files["pack.json"] ?: return null, Charsets.UTF_8))
        val id = pack["id"] as? String ?: return null
        val chapters = (pack["chapters"] as? List<Any?>).orEmpty().mapNotNull { it as? Map<String, Any?> }
        val order = chapters.withIndex().associate { (i, c) -> c["id"] as? String to ((c["order"] as? Number)?.toInt() ?: i) }
        class L(val m: Map<String, Any?>, val chapter: String, val key: Int, val rank: Int)
        val exercises = LinkedHashMap<String, Pair<Map<String, Any?>, String?>>()      // id → (exercise, chapter default of its file)
        val all = ArrayList<L>()
        for ((path, bytes) in files.toSortedMap()) {
            if (!path.startsWith("lessons/") || !path.endsWith(".json")) continue
            val f = Json.obj(String(bytes, Charsets.UTF_8)); val def = f["chapter"] as? String
            for (e in (f["exercises"] as? List<Any?>).orEmpty().mapNotNull { it as? Map<String, Any?> }) (e["id"] as? String)?.let { exercises[it] = e to def }
            for (l in (f["lessons"] as? List<Any?>).orEmpty().mapNotNull { it as? Map<String, Any?> }) {
                val ch = l["chapter"] as? String ?: def ?: continue
                all += L(l, ch, order[ch] ?: Int.MAX_VALUE, all.size)
            }
        }
        val picked = all.sortedWith(compareBy({ it.key }, { it.rank })).take(lessons)
        if (picked.isEmpty()) return null
        val lessonIds = picked.map { it.m["id"] as String }.toSet()

        val exIds = LinkedHashSet<String>()
        fun strs(v: Any?) = (v as? List<Any?>).orEmpty().filterIsInstance<String>()
        val outLessons = picked.map { l ->
            for (b in (l.m["blocks"] as? List<Any?>).orEmpty().mapNotNull { it as? Map<String, Any?> }) if (b["type"] == "exercise") (b["ref"] as? String)?.let { exIds += it }
            exIds += strs(l.m["exercises"]); exIds += strs(l.m["selfCheck"])
            val m = LinkedHashMap(l.m)
            m["chapter"] = l.chapter
            if (l.m.containsKey("prerequisites")) m["prerequisites"] = strs(l.m["prerequisites"]).filter { it in lessonIds }   // the base is self-contained
            m
        }
        val outEx = exIds.mapNotNull { exercises[it] }.map { (e, def) ->
            val m = LinkedHashMap(e)
            if (m["chapter"] == null && def != null) m["chapter"] = def
            if ((m["lesson"] as? String) !in lessonIds) m.remove("lesson")
            m
        }
        val usedChapters = (picked.map { it.chapter } + outEx.mapNotNull { it["chapter"] as? String }).toSet()

        val title = (pack["title"] as? String ?: id)
        val p = LinkedHashMap(pack)
        p["id"] = idFor(id)
        p["title"] = title.removeSuffix(TITLE_SUFFIX) + TITLE_SUFFIX
        p["description"] = "Contenu de base : les ${picked.size} première(s) fiche(s) de « $title », avec leurs exercices. Les leçons complètes arrivent par lot depuis le téléphone."
        p["chapters"] = chapters.filter { it["id"] in usedChapters }
        p["mockExams"] = emptyList<Any?>()
        p["baseOf"] = id
        val lessonsFile = linkedMapOf<String, Any?>("lessons" to outLessons, "exercises" to outEx)
        return sortedMapOf(
            "pack.json" to Json.write(p).toByteArray(Charsets.UTF_8),
            "lessons/base.json" to Json.write(lessonsFile).toByteArray(Charsets.UTF_8),
        )
    }
}
