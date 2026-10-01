package castbridge.core.curriculum

import castbridge.core.quiz.Json
import castbridge.core.quiz.Json.int
import castbridge.core.quiz.Json.str

/** One atomic, assessable skill of the curriculum graph (content/graph/DOMAIN.json, docs/CONTENT-ARCHITECTURE.md § 4). */
data class Skill(
    val id: String, val domain: String, val titleFr: String, val titleEn: String, val level: Level,
    val prereq: List<String>, val years: List<Int>, val classes: List<String>, val exams: List<String>,
    /** Lot scopes (content/graph/scopes.json): where the lessons / exercises / questions of this skill live. */
    val lots: List<String>, val minutes: Int, val tags: List<String>,
) {
    val firstYear: Int get() = years.minOrNull() ?: 0
}

/** A lot scope (class, class-excellence, theme or phone-only media lot): the `scope` of a [castbridge.core.lots.LotId]. */
data class Scope(val id: String, val titleFr: String, val titleEn: String, val lang: String, val ord: Int, val kind: String, val levels: Set<Level>, val tv: Boolean, val feature: String = "learn|quiz")

class SkillGraph(val skills: List<Skill>, val scopes: List<Scope>) {
    val byId: Map<String, Skill> by lazy { skills.associateBy { it.id } }
    val scopeById: Map<String, Scope> by lazy { scopes.associateBy { it.id } }

    companion object {
        /** `<domain prefix>.<slug>` */
        val ID = Regex("^[a-z]{2,6}\\.[a-z0-9][a-z0-9-]{1,48}$")
        val SCOPE = Regex("^[a-z0-9][a-z0-9-]{0,31}$")

        @Suppress("UNCHECKED_CAST")
        private fun list(m: Map<String, Any?>, k: String): List<Any?> = m[k] as? List<Any?> ?: emptyList()
        private fun strs(m: Map<String, Any?>, k: String) = list(m, k).mapNotNull { it as? String }
        @Suppress("UNCHECKED_CAST")
        private fun title(m: Map<String, Any?>, w: String): Pair<String, String> {
            val t = m["title"] as? Map<String, Any?> ?: throw Json.ParseError("$w : \"title\" {fr,en} manquant")
            return (t.str("fr") ?: "") to (t.str("en") ?: "")
        }

        /** Parses one domain file. */
        @Suppress("UNCHECKED_CAST")
        fun parseDomain(json: String, fileName: String = "graph"): List<Skill> {
            val root = Json.obj(json)
            val domain = root.str("domain") ?: throw Json.ParseError("$fileName : \"domain\" manquant")
            return list(root, "skills").mapIndexed { i, o ->
                val m = o as? Map<String, Any?> ?: throw Json.ParseError("$fileName : compétence #$i invalide")
                val id = m.str("id") ?: throw Json.ParseError("$fileName : compétence #$i sans id")
                val w = "$fileName : $id"
                val (fr, en) = title(m, w)
                val lv = Level.of(m.str("level")) ?: throw Json.ParseError("$w : niveau « ${m.str("level")} » inconnu (N0..N4)")
                Skill(id, domain, fr, en, lv, strs(m, "prereq"), list(m, "years").mapNotNull { (it as? Number)?.toInt() }, strs(m, "classes"),
                    strs(m, "exams"), strs(m, "lots"), m.int("minutes") ?: 0, strs(m, "tags"))
            }
        }

        @Suppress("UNCHECKED_CAST")
        fun parseScopes(json: String): List<Scope> = list(Json.obj(json), "scopes").mapIndexed { i, o ->
            val m = o as? Map<String, Any?> ?: throw Json.ParseError("scopes.json : entrée #$i invalide")
            val id = m.str("id") ?: throw Json.ParseError("scopes.json : entrée #$i sans id")
            val (fr, en) = title(m, "scopes.json : $id")
            Scope(id, fr, en, m.str("lang") ?: "fr", m.int("ord") ?: 0, m.str("kind") ?: "class",
                strs(m, "levels").mapNotNull { Level.of(it) }.toSet(), (m["tv"] as? Boolean) ?: true, m.str("feature") ?: "learn|quiz")
        }
    }
}
