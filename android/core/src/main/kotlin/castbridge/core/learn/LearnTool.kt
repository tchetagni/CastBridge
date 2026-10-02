package castbridge.core.learn

import java.io.File
import kotlin.system.exitProcess

/**
 * Command line of the content authors and of the build (tools/build-learn-packs, gradle :core:buildLearnPacks):
 *
 *   check <content dir> [pack id…]          validate the sources, print errors, warnings and the coverage table
 *   build <content dir> <out dir>           build every pack zip (+ catalog.json) into <out dir>
 *   embed <content dir> <out dir>           build the packs listed in <content dir>/embedded.txt (full) + the base packs (BaseContent: the first
 *                                           fiches of every other pack), as app resources
 *   lots <content dir> <out dir> [--update] [--date=YYYY-MM-DD]
 *                                           build the lots (one zip per class, docs/LEARN.md § Lots) + lots-catalog.json; fails if a
 *                                           lot exceeds 3 MB or if its content changed without --update (which bumps its version)
 *   review <content dir> <file>             write the review report per lot (what the teachers must check)
 *
 * <content dir> holds one folder per pack (pack.json, lessons/<name>.json, media/).
 */
object LearnTool {
    @JvmStatic
    fun main(args: Array<String>) {
        if (args.size < 2) { System.err.println("usage: check|build|embed <content dir> [out dir]"); exitProcess(2) }
        val content = File(args[1])
        val code = when (args[0]) {
            "check" -> check(content, args.drop(2).toSet())
            "build" -> build(content, File(args.getOrElse(2) { "build/learn-packs" }), null)
            "embed" -> embed(content, File(args[2]))
            "lots" -> lots(content, File(args[2]), args.drop(3))
            "review" -> { File(args[2]).writeText(LearnReview.report(content), Charsets.UTF_8); println("rapport de relecture : ${args[2]}"); 0 }
            else -> { System.err.println("commande inconnue ${args[0]}"); 2 }
        }
        exitProcess(code)
    }

    fun packDirs(content: File): List<File> = content.listFiles { f -> f.isDirectory && File(f, "pack.json").isFile }?.sortedBy { it.name }.orEmpty()

    fun embeddedIds(content: File): Set<String> = File(content, "embedded.txt").takeIf { it.isFile }?.readLines()
        ?.map { it.substringBefore('#').trim() }?.filter { it.isNotEmpty() }?.toSet().orEmpty()

    /** Every lesson id of every pack (prerequisites may point to another pack). */
    fun allLessonIds(content: File): Set<String> = packDirs(content).flatMap { d ->
        runCatching { LessonJson.parsePack(PackBuilder.sources(d).filterKeys { it.endsWith(".json") }.mapValues { String(it.value, Charsets.UTF_8) }).lessons.map { it.id } }.getOrDefault(emptyList())
    }.toSet()

    fun check(content: File, only: Set<String> = emptySet()): Int {
        val known = allLessonIds(content)
        var errors = 0
        val rows = ArrayList<String>()
        for (d in packDirs(content)) {
            if (only.isNotEmpty() && d.name !in only) continue
            val texts = PackBuilder.sources(d).filterKeys { it.endsWith(".json") }.mapValues { String(it.value, Charsets.UTF_8) }
            val pack = try { LessonJson.parsePack(texts) } catch (e: IllegalArgumentException) {
                println("✗ ${d.name}: ${e.message}"); errors++; continue
            }
            val rep = LessonValidator(known).validate(pack)
            if (pack.id != d.name) { println("✗ ${d.name}: le dossier doit porter l'id du pack (${pack.id})"); errors++ }
            rep.errors.forEach { println("✗ ${pack.id}: $it") }
            rep.warnings.forEach { println("! ${pack.id}: $it") }
            errors += rep.errors.size
            rows += Coverage.row(pack)
        }
        println()
        println(Coverage.HEADER)
        rows.forEach { println(it) }
        println(if (errors == 0) "\nOK" else "\n$errors erreur(s)")
        return if (errors == 0) 0 else 1
    }

    fun build(content: File, out: File, only: Set<String>?): Int {
        out.mkdirs()
        val known = allLessonIds(content)
        val built = ArrayList<PackManifest>()
        val table = File(content, "scopes.txt").takeIf { it.isFile }?.let { LearnScopes.parseTable(it.readText()) }
        for (d in packDirs(content)) {
            if (only != null && d.name !in only) continue
            val b = try { PackBuilder.build(PackBuilder.sources(d), knownLessons = known) } catch (e: Exception) {
                System.err.println("✗ ${d.name}: ${e.message}"); return 1
            }
            File(out, b.manifest.fileName).writeBytes(b.bytes)
            built += b.manifest
            println("✓ ${b.manifest.fileName}  ${b.bytes.size} octets (${b.manifest.size} décompressés), ${b.manifest.lessons} fiches, ${b.manifest.exercises} exercices")
        }
        // index of the folder: what the app (embedded) or the server (catalog) lists without opening the zips
        File(out, "catalog.json").writeText(LearnCatalogFile.write(built.map { LearnCatalogFile.Entry(it, it.fileName, scope = table?.scopeOf(it.id)) }))
        return 0
    }

    /** The app resources: the full embedded packs, then one base pack per other pack (see [BaseContent]); catalog.json lists them all with their lot. */
    fun embed(content: File, out: File): Int {
        out.mkdirs()
        val full = embeddedIds(content)
        val table = File(content, "scopes.txt").takeIf { it.isFile }?.let { LearnScopes.parseTable(it.readText()) }
        val known = allLessonIds(content)
        val entries = ArrayList<LearnCatalogFile.Entry>()
        var bytes = 0L
        for (d in packDirs(content)) {
            if (d.name !in full) continue
            val b = try { PackBuilder.build(PackBuilder.sources(d), knownLessons = known) } catch (e: Exception) { System.err.println("✗ ${d.name}: ${e.message}"); return 1 }
            File(out, b.manifest.fileName).writeBytes(b.bytes); bytes += b.bytes.size
            entries += LearnCatalogFile.Entry(b.manifest, b.manifest.fileName, scope = table?.scopeOf(d.name))
            println("✓ ${b.manifest.fileName}  ${b.bytes.size} octets (${b.manifest.size} décompressés), ${b.manifest.lessons} fiches, ${b.manifest.exercises} exercices")
        }
        var baseBytes = 0L; var baseRaw = 0L; var baseLessons = 0; var baseCount = 0
        val plans = try { BaseContent.plan(content) } catch (e: Exception) { System.err.println("✗ contenu de base : ${e.message}"); return 1 }
        for (pl in plans) {
            val b = try { PackBuilder.build(pl.files) } catch (e: Exception) { System.err.println("✗ ${pl.dir.name} (contenu de base): ${e.message}"); return 1 }
            File(out, b.manifest.fileName).writeBytes(b.bytes)
            baseBytes += b.bytes.size; baseRaw += b.manifest.size; baseLessons += b.manifest.lessons; baseCount++
            entries += LearnCatalogFile.Entry(b.manifest, b.manifest.fileName, scope = pl.scope)
        }
        File(out, "catalog.json").writeText(LearnCatalogFile.write(entries.sortedBy { it.manifest.id }))
        println("contenu de base : $baseCount packs, $baseLessons fiches, $baseRaw octets décompressés, $baseBytes octets en zip ; packs complets embarqués : $bytes octets")
        return 0
    }

    /** Builds every lot into [out]; prints the size of each and the total. */
    fun lots(content: File, out: File, flags: List<String>): Int {
        val update = "--update" in flags
        val date = flags.firstOrNull { it.startsWith("--date=") }?.substringAfter('=') ?: java.time.LocalDate.now().toString()
        val regFile = File(content, "lots.json")
        val r = try {
            LearnLotBuilder.build(content, LearnLotBuilder.Registry.parse(regFile.takeIf { it.isFile }?.readText()), date, update)
        } catch (e: LearnLotBuilder.Failure) { System.err.println("✗ ${e.message}"); return 1 }
        out.mkdirs()
        out.listFiles { f -> f.name.endsWith(LotFormat.SUFFIX) }?.forEach { it.delete() }
        for (b in r.lots) File(out, b.file).writeBytes(b.bytes)
        File(out, "lots-catalog.json").writeText(LearnLotCatalogFile.write(r.lots.map { LearnLotCatalogFile.Entry(it.meta, it.file, it.index.date, it.packIds, it.index.lessonCount) }))
        if (update) regFile.writeText(r.registry.json())
        println("| Lot | Version | Date | Packs | Fiches | Taille |\n|---|---|---|---|---|---|")
        for (b in r.lots) println("| ${b.meta.id.scope} | v${b.meta.version} | ${b.index.date} | ${b.packIds.size} | ${b.index.lessonCount} | ${b.bytes.size} o |")
        println("\n${r.lots.size} lots, total ${r.totalBytes} octets (${"%.2f".format(r.totalBytes / 1048576.0)} Mo ; plafond par lot ${LotFormat.MAX_LOT_BYTES shr 20} Mo, téléphone ${castbridge.core.lots.LotBudget.PHONE_MAX_BYTES shr 20} Mo)" +
            (if (r.bumped.isEmpty()) "" else "\nversions incrémentées : ${r.bumped.joinToString()}"))
        return 0
    }
}

/** Coverage table (exam × subject × fiches / exercises / mock exams), printed by `check` and copied into docs/LEARN.md. */
object Coverage {
    const val HEADER = "| Pack | Examen | Niveau | Matière | Fiches | Exercices (dont auto-éval.) | Épreuves blanches | Illustrations | À vérifier |\n|---|---|---|---|---|---|---|---|---|"
    fun row(p: Pack): String {
        val self = p.exercises.count { it.tier == ExerciseTier.SELFCHECK }
        val ill = p.lessons.sumOf { l -> l.blocks.count { it is Block.Illustration || (it is Block.Example && it.figure != null) } }
        val review = p.exercises.count { it.review || it.parts.any { q -> q.review } } + p.lessons.sumOf { l -> l.blocks.count { it.review } + l.reviewNotes.size }
        return "| ${p.id} | ${p.exam?.let { LearnCatalog.exam(it)?.label } ?: "—"} | ${p.level} | ${LearnCatalog.subject(p.subject)?.label(p.lang) ?: p.subject} | " +
            "${p.lessons.size} | ${p.exercises.size} ($self) | ${p.mockExams.size} | $ill | $review |"
    }
}

/**
 * catalog.json: the list of packs of a folder (embedded resources, a USB drive, the server), so that nothing has to be
 * unzipped to show what exists. The server will serve the same document at GET /api/v1/learn/catalog.
 */
object LearnCatalogFile {
    class Entry(val manifest: PackManifest, val file: String, val url: String? = null, val scope: String? = null)

    fun write(entries: List<Entry>): String = castbridge.core.quiz.Json.write(linkedMapOf(
        "format" to PackFormat.VERSION,
        "packs" to entries.map { e ->
            val m = e.manifest
            linkedMapOf("id" to m.id, "version" to m.version, "title" to m.title, "lang" to m.lang, "cursus" to m.cursus, "level" to m.level,
                "subject" to m.subject, "exam" to m.exam, "status" to m.status, "size" to m.size, "lessons" to m.lessons,
                "exercises" to m.exercises, "mockExams" to m.mockExams, "file" to e.file, "url" to e.url, "scope" to e.scope)
        },
    ))

    data class Item(val id: String, val version: Int, val title: String, val lang: String, val level: String, val subject: String,
                    val exam: String?, val size: Long, val lessons: Int, val exercises: Int, val file: String?, val url: String?, val status: String,
                    val scope: String? = null)

    fun parse(json: String): List<Item> {
        val root = castbridge.core.quiz.Json.obj(json)
        @Suppress("UNCHECKED_CAST")
        return (root["packs"] as? List<Any?>).orEmpty().mapNotNull { o ->
            val m = o as? Map<String, Any?> ?: return@mapNotNull null
            fun s(k: String) = m[k] as? String
            fun n(k: String) = (m[k] as? Number)?.toLong() ?: 0
            Item(s("id") ?: return@mapNotNull null, n("version").toInt(), s("title") ?: "", s("lang") ?: "fr", s("level") ?: "", s("subject") ?: "",
                s("exam"), n("size"), n("lessons").toInt(), n("exercises").toInt(), s("file"), s("url"), s("status") ?: "draft", s("scope"))
        }
    }
}
