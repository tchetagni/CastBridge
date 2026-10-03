package castbridge.core

import castbridge.core.lots.StarterBudget
import castbridge.core.quiz.*
import kotlin.test.*

/** The Quiz bundled per level (docs/agent-reports/quiz-embarque.md): 2000+ questions per free level, none for the reserved ones, loaded on demand. */
class QuizEmbeddedLevelsTest {
    private val reserved = setOf("tle", "l1", "l2", "l3")
    private val levels = EmbeddedLevels()

    @Test fun everyFreeLevelHasAtLeast2000Questions() {
        assertEquals(24, levels.levels.size, levels.levels.map { it.key }.toString())
        for (l in levels.levels) {
            assertTrue(l.count >= 2000, "${l.key}: ${l.count}")
            assertEquals(l.count, levels.load(l).all.size, "${l.key}: the index count matches the file")
        }
        assertTrue(levels.levels.any { it.level == null && it.track == Track.GENERAL }, "culture générale")
    }

    @Test fun noReservedLevelIsBundled() {
        assertTrue(levels.levels.none { it.key in reserved || it.level in setOf("Tle", "L1", "L2", "L3") })
        for (l in levels.levels) assertTrue(levels.load(l).all.none { it.level in setOf("Tle", "L1", "L2", "L3") || it.track == Track.HIGHER }, l.key)
        val index = javaClass.getResourceAsStream("/castbridge/quiz/embedded/index.json")!!.use { String(it.readBytes()) }
        assertTrue(reserved.all { "\"$it\"" in index }, "the reserved levels are listed as not embedded")
    }

    @Test fun noDuplicateIdAndStatusIsKept() {
        val seen = HashSet<String>()
        for (l in levels.levels) for (q in levels.load(l).all) {
            assertTrue(seen.add(q.id), "id en double ${q.id}")
            assertEquals("review", q.status, "the review status of the lots is kept (${q.id})")
            assertTrue(q.review, q.id)
            assertEquals(4, q.choices.size); assertTrue(q.answer in 0..3)
        }
        assertTrue(seen.size >= 24 * 2000)
    }

    @Test fun aLevelIsValidAsABank() {
        for (key in listOf("cp", "culture-generale", "2nde", "form-5")) {
            val l = levels.levels.first { it.key == key }
            assertEquals(emptyList(), levels.load(l).validate().take(5), key)
        }
    }

    @Test fun generalKnowledgeKeepsTheThreeRegions() {
        val g = levels.load(levels.levels.first { it.key == "culture-generale" }).all
        val by = g.groupingBy { it.region }.eachCount()
        assertTrue(Region.values().all { (by[it] ?: 0) >= 300 }, by.toString())
        assertTrue((1..5).all { d -> g.any { it.difficulty == d } })
    }

    @Test fun aGameLoadsOnlyItsLevelOnDemandAndReleasesTheOthers() {
        val reads = ArrayList<String>()
        val lv = EmbeddedLevels(reader = { p -> reads += p.substringAfterLast('/'); EmbeddedLevels::class.java.getResourceAsStream(p)?.use { it.readBytes() } })
        val src = EmbeddedQuestionSource(levels = lv)
        val cm2 = QuestionFilter(Track.PRIMARY, "CM2")
        assertEquals(320, src.bank().all.size, "the old bundled bank is unchanged")
        val b = src.bankFor(cm2)
        assertTrue(b.count(cm2, includeReview = true) >= 2000)
        assertEquals(listOf("index.json", "cm2.json"), reads.distinct(), "only the index and the CM2 file were read")
        assertSame(b, src.bankFor(cm2), "kept while the level does not change")
        val cp = src.bankFor(QuestionFilter(Track.PRIMARY, "CP"))
        assertEquals(listOf("index.json", "cm2.json", "cp.json"), reads.distinct())
        assertEquals(src.bank().count(cm2, includeReview = true), cp.count(cm2, includeReview = true), "the CM2 questions of the previous level are released: only the old bank's remain")
        assertEquals(320, src.bank().all.size)
        // a filter without bundled level (a university field, Tle…) = the small bundled bank, nothing loaded
        val before = reads.size
        assertSame(src.bank(), src.bankFor(QuestionFilter(Track.HIGHER, "L1", "droit")))
        assertEquals(before, reads.size)
    }

    @Test fun theServerCacheAndLotsReplaceBundledQuestionsWithTheSameIdInsteadOfDoubling() {
        val src = EmbeddedQuestionSource()
        val cm2 = QuestionFilter(Track.PRIMARY, "CM2")
        val own = src.bankFor(cm2).all.filter { it.level == "CM2" && it.id.startsWith("p2-") }.take(5)
        assertEquals(5, own.size)
        val file = java.io.File.createTempFile("qcache", ".json").apply { deleteOnExit() }
        val cached = CachedQuestionSource(file, src)
        val json = Json.write(linkedMapOf("version" to 2, "questions" to own.map { q ->
            linkedMapOf("id" to q.id, "track" to "primary", "level" to "CM2", "region" to "WORLD", "category" to q.category, "difficulty" to q.difficulty,
                "question" to q.question + " (révisée)", "choices" to q.choices, "answer" to q.answer, "explanation" to q.explanation, "source" to "s", "status" to "approved") }))
        assertNull(cached.update(json))
        val b = cached.bankFor(cm2)
        assertEquals(src.bankFor(cm2).all.size, b.all.size, "same number of questions: replaced, not duplicated")
        assertEquals(b.all.size, b.all.map { it.id }.toSet().size)
        assertTrue(own.all { q -> b.all.single { it.id == q.id }.question.endsWith("(révisée)") })
    }

    @Test fun bundledLevelsDoNotCountInTheStarterBudget() {
        val r = StarterBudget.measure()
        assertTrue(r.items.keys.none { "embedded/" in it && it.startsWith("quiz/") }, r.items.keys.toString())
        val old = listOf("questions.json", "questions-school.json").sumOf { javaClass.getResourceAsStream("/castbridge/quiz/$it")!!.use { s -> s.readBytes().size.toLong() } }
        assertEquals(old, r.quizBytes, "only the existing starter bank counts: the per-level resources are APK resources, not lots")
    }
}
