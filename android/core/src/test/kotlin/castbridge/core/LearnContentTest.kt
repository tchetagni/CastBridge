package castbridge.core

import castbridge.core.learn.*
import castbridge.core.quiz.QuizBank
import java.io.File
import kotlin.test.*

/** Every pack source of the repository (content/learn) is valid, covers what docs/LEARN.md promises, and builds. */
class LearnContentTest {
    private val content = File(System.getProperty("learn.content") ?: "../../content/learn")
    private val dirs = LearnTool.packDirs(content)
    private val known = LearnTool.allLessonIds(content)
    private val packs: List<Pack> by lazy {
        dirs.map { d -> LessonJson.parsePack(PackBuilder.sources(d).filterKeys { it.endsWith(".json") }.mapValues { String(it.value, Charsets.UTF_8) }) }
    }

    private val SMALL_PACKS = setOf("bepc-svt", "bepc-histoire-geo", "bepc-english", "bac-philosophie", "gceal-maths", "licence-droit")

    @Test fun contentFolderExists() {
        assertTrue(content.isDirectory, "content/learn not found at $content")
        assertTrue(dirs.size >= 19, "at least 19 packs (${dirs.map { it.name }})")
    }

    @Test fun everyPackIsValid() {
        for ((d, p) in dirs.zip(packs)) {
            assertEquals(d.name, p.id, "folder name = pack id")
            val r = LessonValidator(known).validate(p)
            assertEquals(emptyList(), r.errors, "errors in ${p.id}")
        }
        val all = packs.flatMap { p -> p.exercises.map { it.id } + p.lessons.map { it.id } }
        assertEquals(all.size, all.toSet().size, "ids are unique across packs")
    }

    /** Coverage promised for the POC: 3 subjects × 4 exams, ~4 fiches + 20 corrected exercises + 1 mock exam each. */
    @Test fun examCoverage() {
        val exam = packs.filter { it.exam != null }
        for (x in listOf("CEP", "BEPC", "GCE-OL")) assertTrue(exam.count { it.exam == x } >= 3, "3 subjects for $x (${exam.filter { it.exam == x }.map { it.id }})")
        assertTrue(exam.count { it.exam == "BAC" || it.exam == "PROBATOIRE" } >= 3, "3 subjects for Probatoire/Bac")
        for (p in exam) {
            // packs added with the lots (to review, smaller): 3 fiches; the original packs keep 4
            assertTrue(p.lessons.size >= if (p.id in SMALL_PACKS) 3 else 4, "${p.id}: fiches (${p.lessons.size})")
            assertTrue(p.exercises.count { it.tier != ExerciseTier.SELFCHECK } >= 20, "${p.id}: ≥ 20 exercises")
            assertTrue(p.mockExams.isNotEmpty(), "${p.id}: a mock exam")
            for (m in p.mockExams) assertEquals(20.0, m.exerciseIds.sumOf { p.exercise(it)!!.totalPoints }, 0.01, "${p.id} ${m.id} out of 20")
        }
    }

    @Test fun answersAreConsistent() {
        for (p in packs) for (x in p.exercises.flatMap { listOf(it) + it.parts }) when (x.kind) {
            ExerciseKind.MCQ -> {
                assertTrue(x.answerIndex in x.choices.indices, x.id)
                // the right answer, marked with the marking engine, gets all the points and a wrong one none
                assertTrue(Marking.mark(x, Answer.Choice(x.answerIndex)).correct, x.id)
                x.choices.indices.filter { it != x.answerIndex }.forEach { assertFalse(Marking.mark(x, Answer.Choice(it)).correct, x.id) }
            }
            ExerciseKind.NUMERIC -> {
                val shown = Scene.fmt(x.answerNumber!!)
                assertTrue(Marking.mark(x, Answer.Number(shown)).correct, "${x.id}: typing $shown is right")
            }
            ExerciseKind.TRUE_FALSE -> assertTrue(Marking.mark(x, Answer.Bool(x.answerBool!!)).correct, x.id)
            ExerciseKind.MATCHING -> assertTrue(Marking.mark(x, Answer.Pairs(x.pairs.toMap())).correct, x.id)
            else -> {}
        }
    }

    @Test fun everyFormulaAndTextParsesAndEveryFigureDraws() {
        for (p in packs) for (l in p.lessons) {
            val deck = LessonDeck(p, l)
            assertTrue(deck.pages.size >= 3, l.id)
            for (i in deck.pages.indices) deck.spoken(i)             // TextToSpeech text never throws
            for (b in l.blocks) when (b) {
                is Block.Illustration -> assertTrue(Scene.build(b.figure).ops.isNotEmpty(), l.id)
                is Block.Formula -> Tex.parse(b.tex)
                else -> {}
            }
        }
    }

    /** « Aucun texte superposé »: in every figure of the content, no label covers another one (estimated glyph widths). */
    @Test fun figureTextsDoNotOverlap() {
        val bad = ArrayList<String>()
        fun check(where: String, f: Figure) {
            val sc = Scene.build(f)
            val texts = sc.ops.filterIsInstance<Op.Text>()
            val boxes = sc.textBoxes(0.5)
            for (i in boxes.indices) for (j in i + 1 until boxes.size) {
                val a = boxes[i]; val b = boxes[j]
                val w = minOf(a[2], b[2]) - maxOf(a[0], b[0]); val h = minOf(a[3], b[3]) - maxOf(a[1], b[1])
                if (w <= 0 || h <= 0) continue
                val smaller = minOf((a[2] - a[0]) * (a[3] - a[1]), (b[2] - b[0]) * (b[3] - b[1]))
                if (w * h > 0.2 * smaller) bad += "$where: « ${texts[i].text} » / « ${texts[j].text} »"
            }
        }
        for (p in packs) {
            for (l in p.lessons) l.blocks.forEachIndexed { k, b -> when (b) {
                is Block.Illustration -> check("${l.id} bloc $k", b.figure)
                is Block.Example -> b.figure?.let { check("${l.id} bloc $k", it) }
                else -> {}
            } }
            for (x in p.exercises) (listOf(x) + x.parts).forEach { q -> q.figure?.let { check(q.id, it) } }
        }
        assertEquals(emptyList(), bad, "overlapping labels")
    }

    @Test fun prerequisitesExistAndHaveNoCycle() {
        val all = packs.flatMap { it.lessons }.associateBy { it.id }
        for (l in all.values) for (pr in l.prerequisites) assertNotNull(all[pr], "${l.id} → $pr")
        fun reach(id: String, seen: Set<String>): Boolean = all[id]?.prerequisites.orEmpty().any { it in seen || reach(it, seen + it) }
        for (l in all.values) assertFalse(reach(l.id, setOf(l.id)), "cycle through ${l.id}")
    }

    @Test fun noVideoIsEmbedded() {
        for (p in packs) for (l in p.lessons) for (b in l.blocks) if (b is Block.Video) assertNull(b.src, "${l.id}: videos are referenced by an administrator, never shipped")
    }

    @Test fun selfChecksPlayInTheQuiz() {
        val bank = LearnQuiz.bank(packs)
        assertTrue(bank.all.size >= 100, "self-check questions exported to the quiz (${bank.all.size})")
        assertEquals(emptyList(), bank.validate())
    }

    @Test fun everyPackBuildsReproduciblyAndReadsBack() {
        for (d in dirs) {
            val b1 = PackBuilder.build(PackBuilder.sources(d), knownLessons = known)
            val b2 = PackBuilder.build(PackBuilder.sources(d), knownLessons = known)
            assertEquals(b1.manifest.json(), b2.manifest.json(), "reproducible manifest for ${d.name}")
            val v = PackReader.read(b1.bytes)
            assertEquals(d.name, v.pack.id)
            assertTrue(b1.bytes.size < 1 shl 20, "${d.name}: a text pack stays under 1 MB zipped (${b1.bytes.size})")
        }
    }

    /** The socle embedded in the app stays small (budget 5 MB compressed; the whole content is far below). */
    @Test fun embeddedBudget() {
        val src = EmbeddedLessonSource()
        val refs = src.list()
        assertTrue(refs.isNotEmpty(), "embedded packs present in the resources")
        assertEquals(LearnTool.embeddedIds(content), refs.map { it.id }.filterNot { BaseContent.isBase(it) }.toSet(), "the full embedded packs are those of embedded.txt (+ the base packs, see BaseContentTest)")
        var total = 0L
        for (r in refs) { total += r.bytes().use { it.readBytes().size.toLong() }; src.open(r) }
        assertTrue(total < 5L shl 20, "embedded content $total bytes > 5 MB budget")
        println("embedded packs: ${refs.map { it.id }} = $total bytes")
    }
}
