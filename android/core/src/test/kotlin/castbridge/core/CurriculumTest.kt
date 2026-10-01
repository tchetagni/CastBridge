package castbridge.core

import castbridge.core.curriculum.*
import castbridge.core.learn.ExerciseTier
import castbridge.core.learn.LessonJson
import castbridge.core.quiz.Question
import castbridge.core.quiz.QuizBank
import castbridge.core.quiz.Region
import java.io.File
import kotlin.test.*

class CurriculumTest {
    private val content = File(System.getProperty("graph.content") ?: "../../content")

    // ---- the real graph ----
    @Test fun committedGraphIsValid() {
        val g = GraphTool.load(content)
        val r = ContentGraphValidator(g, GraphTool.attached(content)).validate()
        assertEquals(emptyList(), r.errors)
        assertTrue(g.skills.size >= 300, "several hundred skills (${g.skills.size})")
        for (d in listOf("mathematiques", "francais", "english", "physique-chimie", "svt", "histoire-geo-ecm", "informatique"))
            assertTrue(g.skills.any { it.domain == d }, "domain $d seeded")
        for (l in Level.values()) for (d in listOf("mathematiques", "physique-chimie")) assertTrue(g.skills.any { it.domain == d && it.level == l }, "$d has ${l.key}")
    }

    @Test fun everySkillLotExistsAndCarriesItsLevel() {
        val g = GraphTool.load(content)
        for (s in g.skills) for (l in s.lots) assertTrue(s.level in g.scopeById.getValue(l).levels, "${s.id} in $l")
        // lots are never both TV-eligible and media
        assertTrue(g.scopes.filter { it.kind == "media" }.none { it.tv })
    }

    // ---- validator on small broken graphs ----
    private val scopes = listOf(Scope("cm2", "CM2", "CM2", "fr", 6, "class", setOf(Level.N0, Level.N1), true), Scope("cm2-exc", "x", "x", "fr", 6, "class", setOf(Level.N2, Level.N3, Level.N4), true))
    private fun sk(id: String, lv: Level = Level.N1, pre: List<String> = emptyList(), lots: List<String> = listOf("cm2"), years: List<Int> = listOf(6)) =
        Skill(id, "math", "t", "t", lv, pre, years, listOf("cm2"), emptyList(), lots, 20, emptyList())
    private fun errs(vararg s: Skill, attached: Map<String, Attached> = emptyMap()) =
        ContentGraphValidator(SkillGraph(s.toList(), scopes), attached, minPerLevel = 0, requireN4 = emptySet()).validate().errors

    @Test fun detectsCycle() {
        val e = errs(sk("math.aaa", pre = listOf("math.bbb")), sk("math.bbb", pre = listOf("math.aaa")))
        assertTrue(e.any { "cycle" in it }, e.toString())
    }
    @Test fun detectsMissingPrereqOrphanAndBadLot() {
        val e = errs(sk("math.aaa", pre = listOf("math.zzz")), sk("math.bbb"), sk("math.ccc", lots = listOf("nope")), sk("math.ddd", lots = emptyList()))
        assertTrue(e.any { "math.zzz introuvable" in it }); assertTrue(e.any { "orpheline" in it })
        assertTrue(e.any { "« nope » inconnu" in it }); assertTrue(e.any { "aucun scope" in it })
    }
    @Test fun detectsLevelAndScopeMismatch() {
        val e = errs(sk("math.low"), sk("math.hi", Level.N2, listOf("math.low"), lots = listOf("cm2")))
        assertTrue(e.any { "absent du scope cm2" in it }, e.toString())
        val e2 = errs(sk("math.hi", Level.N2, lots = listOf("cm2-exc")), sk("math.low", pre = listOf("math.hi")))
        assertTrue(e2.any { "> N1" in it }, e2.toString())
    }
    @Test fun ladderMustBeContinuous() {
        val e = errs(sk("math.n1"), sk("math.n3", Level.N3, listOf("math.n1"), lots = listOf("cm2-exc")))
        assertTrue(e.any { "discontinue" in it }, e.toString())
    }
    @Test fun attachedContentNeedsLessonAndThreeExercisesAndHarderTiers() {
        val n2 = sk("math.n2", Level.N2, listOf("math.n1"), lots = listOf("cm2-exc"))
        val base = arrayOf(sk("math.n1", pre = listOf("math.n1b")), sk("math.n1b"), n2)
        fun item(t: ExerciseTier, d: Int) = Attached.Item(t, d)
        assertTrue(errs(*base, attached = mapOf("math.n2" to Attached(1, listOf(item(ExerciseTier.EXAM, 3), item(ExerciseTier.EXAM, 3), item(ExerciseTier.EXAM, 3))))).any { "excellence" in it })
        assertTrue(errs(*base, attached = mapOf("math.n2" to Attached(0, listOf(item(ExerciseTier.EXCELLENCE_CM, 4))))).let { e -> e.any { "aucune leçon" in it } && e.any { "≥ 3 attendus" in it } })
        assertEquals(emptyList(), errs(*base, attached = mapOf("math.n2" to Attached(1, List(3) { item(ExerciseTier.EXCELLENCE_CM, 4) }))))
    }

    // ---- additive difficulty scale ----
    @Test fun unknownTierReadsAsHardestKnown() {
        assertEquals(ExerciseTier.EXCELLENCE_MONDE, ExerciseTier.ofOrHardest("excellence-2030"))
        assertEquals(ExerciseTier.EXAM, ExerciseTier.ofOrHardest("examen"))
        assertNull(ExerciseTier.ofOrHardest(null))
        val json = """{"chapter":"c","exercises":[{"id":"p-1","kind":"truefalse","prompt":"x ?","answer":true,"tier":"excellence-cm","difficulty":4,
            "skill":"math.n2-alg-modelling","level":"N2","calibration":{"N2":0.7},"explanation":"e"},
            {"id":"p-2","kind":"truefalse","prompt":"y ?","answer":true,"tier":"tier-from-the-future","explanation":"e"}]}"""
        val pack = LessonJson.parsePack(mapOf("pack.json" to """{"id":"p","version":1,"title":"t","cursus":"c","level":"l","subject":"s","chapters":[{"id":"c","title":"C"}]}""", "lessons/a.json" to json))
        assertEquals(ExerciseTier.EXCELLENCE_CM, pack.exercise("p-1")!!.tier)
        assertEquals("N2", pack.exercise("p-1")!!.level); assertEquals(0.7, pack.exercise("p-1")!!.calibration["N2"])
        assertEquals(ExerciseTier.HARDEST, pack.exercise("p-2")!!.tier)
    }
    @Test fun windowsAndTiersFollowTheLadder() {
        assertEquals(3..4, LevelScale.difficultyWindow(Level.N2)); assertEquals(5..5, LevelScale.difficultyWindow(Level.N4))
        assertTrue(ExerciseTier.EXCELLENCE_CM in LevelScale.tiersFor(Level.N2)); assertTrue(ExerciseTier.EXAM !in LevelScale.tiersFor(Level.N3))
        assertEquals(Level.N1, LevelScale.itemLevel(null))
    }

    // ---- quiz pick by learner level ----
    private fun q(i: Int, nl: String?, d: Int) = Question("q$i", Region.WORLD, "c", d, "q$i ?", listOf("a", "b", "c", "d"), 0, "e", "s", nlevel = nl)
    private val pool = (0 until 300).map { i -> val nl = listOf("N0", "N1", "N2", "N3", "N4")[i % 5]; q(i, nl, LevelScale.difficultyWindow(Level.of(nl)!!).let { w -> w.first + (i / 5) % (w.last - w.first + 1) }) }

    @Test fun beginnerIsNotCrushedAndExpertNotBored() {
        for ((learner, label) in listOf(Level.N0 to "beginner", Level.N4 to "expert", Level.N2 to "mid")) {
            val picks = LevelPick.pick(pool, learner, 15, seed = 7)
            assertEquals(15, picks.size, label)
            val p = picks.map { LevelPick.expected(it, learner) }
            assertTrue(p.first() >= 0.75, "$label warm-up easy (${p.first()})")
            assertTrue(p.all { it >= 0.30 }, "$label never crushed: ${p.min()}")
            assertTrue(p.last() <= 0.75, "$label still challenged at the end (${p.last()})")
        }
        val expert = LevelPick.pick(pool, Level.N4, 15, seed = 1); val beginner = LevelPick.pick(pool, Level.N0, 15, seed = 1)
        assertTrue(expert.count { it.nlevel in listOf("N3", "N4") } >= 8, "the expert mostly gets N3/N4")
        assertTrue(beginner.count { it.nlevel in listOf("N0", "N1") } >= 10, "the beginner mostly gets N0/N1")
        assertEquals(LevelPick.pick(pool, Level.N2, 10, seed = 5).map { it.id }, LevelPick.pick(pool, Level.N2, 10, seed = 5).map { it.id }, "reproducible")
    }
    @Test fun oldQuestionsWithoutLevelCountAsN1() {
        val old = q(1, null, 5)
        assertEquals(Level.N1, LevelScale.itemLevel(old.nlevel))
        assertTrue(LevelPick.expected(old, Level.N1) > 0.6)
    }
    @Test fun quizBankParsesAndValidatesTheNewFields() {
        val j = """{"version":2,"questions":[{"id":"x1","region":"WORLD","category":"c","difficulty":4,"question":"Q ?","choices":["a","b","c","d"],"answer":1,"explanation":"e","source":"s",
            "skill":"math.n2-alg-modelling","nlevel":"N2","lot":"cm2-exc","calib":{"N2":0.7,"N1":0.3}}]}"""
        val b = QuizBank.parse(j)
        assertEquals("N2", b.all[0].nlevel); assertEquals(0.3, b.all[0].calib["N1"]); assertEquals(emptyList(), b.validate())
        assertTrue(QuizBank.parse(j.replace("\"N2\":0.7", "\"N9\":0.7")).validate().isNotEmpty())
    }

    // ---- placement ----
    private fun run(entry: Level, correctAt: (Level) -> Boolean): Placement.Result {
        val p = Placement(entry); var n = 0
        while (!p.finished && n++ < 40) p.answer(correctAt(p.nextLevel))
        return p.result()
    }
    @Test fun placementFindsTheLevelOfAnyLearner() {
        for (truth in Level.values()) {
            val r = run(Level.N1) { it <= truth }
            assertEquals(truth, r.level, "truth $truth"); assertTrue(r.items <= Placement.MAX_ITEMS)
        }
        assertNull(run(Level.N1) { false }.level); assertEquals("start-n0", run(Level.N1) { false }.action)
    }
    @Test fun exceptionalLearnerIsFastTracked() {
        val r = run(Level.N1) { true }
        assertEquals(Level.N4, r.level); assertTrue(r.fastTracked); assertTrue(r.items <= 8, "few items (${r.items})"); assertEquals("excellence-path", r.action)
    }
}
