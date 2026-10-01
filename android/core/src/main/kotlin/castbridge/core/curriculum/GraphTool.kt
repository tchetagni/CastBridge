package castbridge.core.curriculum

import castbridge.core.learn.LessonJson
import castbridge.core.learn.LearnTool
import castbridge.core.learn.PackBuilder
import java.io.File
import kotlin.system.exitProcess

/**
 * tools/content-graph-check · gradle :core:checkContentGraph
 *   check <content dir> [--require-content]   validates the JSON files of content/graph (+ scopes.json) and the content that names a skill
 * <content dir> = repository `content/` (graph in content/graph, Apprendre packs in content/learn).
 */
object GraphTool {
    @JvmStatic
    fun main(args: Array<String>) {
        System.setOut(java.io.PrintStream(java.io.FileOutputStream(java.io.FileDescriptor.out), true, "UTF-8"))
        System.setErr(java.io.PrintStream(java.io.FileOutputStream(java.io.FileDescriptor.err), true, "UTF-8"))
        if (args.size < 2 || args[0] != "check") { System.err.println("usage: check <content dir> [--require-content]"); exitProcess(2) }
        exitProcess(check(File(args[1]), "--require-content" in args))
    }

    fun load(content: File): SkillGraph {
        val dir = File(content, "graph")
        val scopes = SkillGraph.parseScopes(File(dir, "scopes.json").readText())
        val skills = dir.listFiles { f -> f.name.endsWith(".json") && f.name != "scopes.json" }.orEmpty().sortedBy { it.name }
            .filter { it.name != "paths.json" && !it.name.startsWith("langue-") }.flatMap { SkillGraph.parseDomain(it.readText(), it.name) }
        return SkillGraph(skills, scopes)
    }

    /** Lessons and exercises of the Apprendre packs that name a skill. */
    fun attached(content: File): Map<String, Attached> {
        val lessons = HashMap<String, Int>(); val ex = HashMap<String, MutableList<Attached.Item>>()
        for (d in LearnTool.packDirs(File(content, "learn"))) {
            val p = runCatching { LessonJson.parsePack(PackBuilder.sources(d).filterKeys { it.endsWith(".json") }.mapValues { String(it.value, Charsets.UTF_8) }) }.getOrNull() ?: continue
            p.lessons.forEach { l -> l.skill?.let { lessons.merge(it, 1, Int::plus) } }
            p.exercises.forEach { x -> x.skill?.let { ex.getOrPut(it) { ArrayList() } += Attached.Item(x.tier, x.difficulty) } }
        }
        return (lessons.keys + ex.keys).associateWith { Attached(lessons[it] ?: 0, ex[it].orEmpty()) }
    }

    /** Learner paths (content/graph/paths.json) are ORDERED SETS of lots: no duplicate, every lot an existing scope that serves that feature. */
    @Suppress("UNCHECKED_CAST")
    fun pathErrors(content: File, graph: SkillGraph): List<String> {
        val f = File(content, "graph/paths.json"); if (!f.isFile) return listOf("content/graph/paths.json manquant")
        val e = ArrayList<String>()
        val paths = castbridge.core.quiz.Json.obj(f.readText())["paths"] as? List<Map<String, Any?>> ?: return listOf("paths.json : \"paths\" manquant")
        for (p in paths) {
            val id = p["id"] as? String ?: "?"; val lots = (p["lots"] as? List<Any?>).orEmpty().map { it.toString() }
            if (lots.toSet().size != lots.size) e += "parcours $id: lot en double (un parcours est un ensemble ordonné de lots)"
            for (l in lots) {
                val (feature, scope) = l.split(':').let { it.getOrElse(0) { "" } to it.getOrElse(1) { "" } }
                val sc = graph.scopeById[scope]
                if (sc == null) e += "parcours $id: scope $scope inconnu"
                else if (feature !in sc.feature.split('|')) e += "parcours $id: le scope $scope ne sert pas la fonction $feature"
            }
        }
        return e
    }

    fun check(content: File, requireContent: Boolean): Int {
        val graph = try { load(content) } catch (t: Throwable) { System.err.println("ERREUR: ${t.message}"); return 1 }
        val r = ContentGraphValidator(graph, attached(content), requireContent).validate()
        val pe = pathErrors(content, graph)
        r.stats.forEach(::println)
        r.warnings.forEach { println("AVERTISSEMENT: $it") }
        (r.errors + pe).forEach { System.err.println("ERREUR: $it") }
        println(if (r.ok && pe.isEmpty()) "Graphe valide." else "${r.errors.size + pe.size} erreur(s).")
        return if (r.ok && pe.isEmpty()) 0 else 1
    }
}
