package castbridge.core.curriculum

import castbridge.core.learn.ExerciseTier

/** What the validator knows about content attached to a skill (lessons, exercises, quiz questions that name it). */
data class Attached(val lessons: Int = 0, val exercises: List<Item> = emptyList(), val questions: List<Item> = emptyList()) {
    data class Item(val tier: ExerciseTier?, val difficulty: Int)
}

/**
 * Checks the skill graph (docs/CONTENT-ARCHITECTURE.md § 4): acyclic, prerequisites exist, no orphan skill, every skill in
 * at least one existing lot scope that carries its level, prerequisites never above the skill (level or class), a
 * continuous ladder (an N2+ skill needs a prerequisite at most one level lower), level coverage per domain, and — once
 * content is attached — ≥ 1 lesson, ≥ 3 exercises across tiers, and the HARDER tiers for N2-N4.
 */
class ContentGraphValidator(
    private val graph: SkillGraph,
    private val attached: Map<String, Attached> = emptyMap(),
    /** Content is mandatory for every skill (publish gate); otherwise missing content is only counted. */
    private val requireContent: Boolean = false,
    /** Domains that must define N4 (world-class standard). */
    private val requireN4: Set<String> = setOf("mathematiques", "physique-chimie"),
    private val minPerLevel: Int = 3,
) {
    class Report(val errors: List<String>, val warnings: List<String>, val stats: List<String>) { val ok get() = errors.isEmpty() }

    fun validate(): Report {
        val e = ArrayList<String>(); val w = ArrayList<String>()
        val byId = HashMap<String, Skill>()
        for (s in graph.skills) if (byId.put(s.id, s) != null) e += "compétence ${s.id}: id en double"
        val scopes = graph.scopeById
        graph.scopes.groupBy { it.id }.filter { it.value.size > 1 }.forEach { e += "scope ${it.key}: en double" }
        graph.scopes.forEach { if (!SkillGraph.SCOPE.matches(it.id)) e += "scope ${it.id}: identifiant invalide (a-z0-9-, ≤ 32)" }

        for (s in graph.skills) {
            val x = "compétence ${s.id}"
            if (!SkillGraph.ID.matches(s.id)) e += "$x: identifiant invalide (<préfixe>.<slug>)"
            if (s.titleFr.isBlank() || s.titleEn.isBlank()) e += "$x: titre fr et en obligatoires"
            if (s.minutes !in 1..240) e += "$x: durée estimée ${s.minutes} min hors 1..240"
            if (s.years.isEmpty()) e += "$x: années (class mapping) manquantes"
            if (s.lots.isEmpty()) e += "$x: aucun scope de lot"
            if (s.id in s.prereq) e += "$x: prérequis de lui-même"
            if (s.prereq.toSet().size != s.prereq.size) e += "$x: prérequis en double"
            for (l in s.lots) {
                val sc = scopes[l]
                if (sc == null) e += "$x: scope de lot « $l » inconnu (content/graph/scopes.json)"
                else if (s.level !in sc.levels) e += "$x: niveau ${s.level.key} absent du scope $l (${sc.levels.joinToString { it.key }})"
            }
            for (p in s.prereq) {
                val ps = byId[p]
                if (ps == null) { e += "$x: prérequis $p introuvable"; continue }
                if (ps.level > s.level) e += "$x: prérequis $p de niveau ${ps.level.key} > ${s.level.key}"
                if (ps.firstYear > s.firstYear) e += "$x: prérequis $p introduit en année ${ps.firstYear} > ${s.firstYear}"
            }
            val sameDomain = s.prereq.mapNotNull { byId[it] }.filter { it.domain == s.domain }
            if (s.level >= Level.N2 && sameDomain.none { it.level.ordinal >= s.level.ordinal - 1 })
                e += "$x: ${s.level.key} sans prérequis de niveau ≥ ${Level.values()[s.level.ordinal - 1].key} dans le domaine (échelle discontinue)"
            if (s.level == Level.N1 && s.prereq.isEmpty() && s.firstYear > 1) w += "$x: N1 sans prérequis (année ${s.firstYear})"
        }

        // cycles (Kahn) over known prerequisites
        val indeg = HashMap<String, Int>(); val deps = HashMap<String, MutableList<String>>()
        for (s in graph.skills) { indeg.putIfAbsent(s.id, 0) }
        for (s in graph.skills) for (p in s.prereq) if (p in byId) { indeg[s.id] = (indeg[s.id] ?: 0) + 1; deps.getOrPut(p) { ArrayList() } += s.id }
        val q = ArrayDeque(indeg.filter { it.value == 0 }.keys); var seen = 0
        while (q.isNotEmpty()) { val n = q.removeFirst(); seen++; for (d in deps[n].orEmpty()) { indeg[d] = indeg.getValue(d) - 1; if (indeg[d] == 0) q += d } }
        if (seen < indeg.size) e += "le graphe a un cycle (compétences concernées: ${indeg.filter { it.value > 0 }.keys.sorted().take(8).joinToString()})"

        // orphans: neither a prerequisite nor a dependent
        val used = HashSet<String>(); graph.skills.forEach { s -> if (s.prereq.isNotEmpty()) { used += s.id; used += s.prereq } }
        graph.skills.filter { it.id !in used }.forEach { e += "compétence ${it.id}: orpheline (ni prérequis ni dépendante)" }

        // level coverage per domain
        for ((d, ss) in graph.skills.groupBy { it.domain }) {
            val n = ss.groupingBy { it.level }.eachCount()
            for (l in listOf(Level.N0, Level.N1, Level.N2, Level.N3)) if ((n[l] ?: 0) < minPerLevel) e += "domaine $d: ${n[l] ?: 0} compétence(s) ${l.key} (minimum $minPerLevel)"
            if ((n[Level.N4] ?: 0) == 0) { if (d in requireN4) e += "domaine $d: niveau N4 (excellence mondiale) obligatoire" else w += "domaine $d: pas encore de N4" }
        }

        // attached content
        var without = 0
        for (s in graph.skills) {
            val a = attached[s.id]
            if (a == null || (a.lessons == 0 && a.exercises.isEmpty() && a.questions.isEmpty())) { without++; continue }
            val x = "compétence ${s.id}"
            if (a.lessons < 1) e += "$x: aucune leçon (≥ 1 attendue)"
            val items = a.exercises
            if (items.size < 3) e += "$x: ${items.size} exercice(s) (≥ 3 attendus)"
            if (items.map { it.tier }.toSet().size < 2 && s.level <= Level.N1) w += "$x: exercices d'un seul palier"
            if (s.level >= Level.N2 && items.isNotEmpty()) {
                val hard = items.count { (it.tier?.excellence == true) && it.difficulty in LevelScale.difficultyWindow(s.level) }
                if (hard * 10 < items.size * 6) e += "$x: ${s.level.key} doit porter surtout des paliers d'excellence (difficulté ${LevelScale.difficultyWindow(s.level)}) : $hard/${items.size}"
                if (s.level >= Level.N3 && items.none { it.tier == ExerciseTier.EXCELLENCE_MONDE }) e += "$x: ${s.level.key} sans exercice excellence-monde"
            }
        }
        if (without > 0) {
            val msg = "$without compétence(s) sans contenu rattaché sur ${graph.skills.size}"
            if (requireContent) e += msg else w += msg
        }
        for (k in attached.keys) if (k !in byId) e += "contenu rattaché à une compétence inconnue: $k"

        return Report(e, w, stats())
    }

    private fun stats(): List<String> {
        val out = ArrayList<String>()
        out += "%-22s %5s %5s %5s %5s %5s %6s".format("domaine", "N0", "N1", "N2", "N3", "N4", "total")
        for ((d, ss) in graph.skills.groupBy { it.domain }.toSortedMap()) {
            val n = ss.groupingBy { it.level }.eachCount()
            out += "%-22s %5d %5d %5d %5d %5d %6d".format(d, n[Level.N0] ?: 0, n[Level.N1] ?: 0, n[Level.N2] ?: 0, n[Level.N3] ?: 0, n[Level.N4] ?: 0, ss.size)
        }
        out += "total: ${graph.skills.size} compétences, ${graph.skills.map { it.minutes }.sum() / 60} h estimées, ${graph.scopes.size} scopes de lot"
        return out
    }
}
