package castbridge.core

import castbridge.core.quiz.*
import java.io.File
import kotlin.random.Random
import kotlin.test.*

class QuizBankTest {
    private val bank = EmbeddedQuestionSource().bank()

    @Test fun bundledBanksAreValid() {
        for (r in EmbeddedQuestionSource.DEFAULT_RESOURCES) {
            val text = javaClass.getResourceAsStream(r)?.use { String(it.readBytes(), Charsets.UTF_8) } ?: fail("missing $r")
            val b = QuizBank.parse(text)
            assertEquals(emptyList(), b.validate(), "problems in $r")
        }
        assertEquals(emptyList(), bank.validate(), "problems once merged (duplicate ids across files?)")
    }

    @Test fun generalKnowledgeHasEnoughQuestionsPerRegionAndLevel() {
        val g = bank.playable.filter { it.track == Track.GENERAL }
        val by = g.groupingBy { it.region }.eachCount()
        assertTrue(g.size >= 180, "at least 180 playable general questions (${g.size})")
        assertTrue(by.getValue(Region.CM) >= 126, "CM ${by[Region.CM]}")
        assertTrue(by.getValue(Region.AF) >= 36, "AF ${by[Region.AF]}")
        assertTrue(by.getValue(Region.WORLD) >= 18, "WORLD ${by[Region.WORLD]}")
        // each difficulty must have enough of each region for several games without repeats
        for (d in 1..5) for (r in Region.values()) assertTrue(g.count { it.region == r && it.difficulty == d } >= 3, "region $r difficulty $d")
    }

    @Test fun choicesAndAnswersAreSane() {
        for (q in bank.all) {
            assertEquals(4, q.choices.size, q.id)
            assertEquals(4, q.choices.map { it.trim().lowercase() }.toSet().size, "distinct choices ${q.id}")
            assertTrue(q.answer in 0..3, q.id)
            assertTrue(q.question.trim().endsWith("?"), "question mark ${q.id}")
            assertTrue(q.choices.none { it.lowercase().startsWith("toutes ces") || it.lowercase().startsWith("aucune de") }, "no order-dependent choice ${q.id}")
        }
        // the right answer is not always at the same place in the file
        val positions = bank.all.groupingBy { it.answer }.eachCount()
        assertEquals(4, positions.size)
    }

    @Test fun drawKeeps70_20_10AndRisingDifficulty() {
        repeat(200) { seed ->
            val qs = bank.draw(15, seed.toLong())
            assertEquals(15, qs.size)
            assertEquals(15, qs.map { it.id }.toSet().size, "no repeat in a game")
            val by = qs.groupingBy { it.region }.eachCount()
            assertTrue((by[Region.CM] ?: 0) in 10..11, "CM ${by[Region.CM]} seed $seed")
            assertEquals(3, by[Region.AF], "AF seed $seed")
            assertTrue((by[Region.WORLD] ?: 0) in 1..2, "WORLD seed $seed")
            assertEquals(qs.map { it.difficulty }.sorted(), qs.map { it.difficulty }, "difficulty never goes down (seed $seed)")
            assertTrue(qs.first().difficulty <= 2 && qs.last().difficulty >= 4, "a real climb (seed $seed)")
            assertTrue(qs.none { it.review }, "review questions are out by default")
        }
    }

    @Test fun quotasFor10And15() {
        assertEquals(mapOf(Region.CM to 7, Region.AF to 2, Region.WORLD to 1), QuizBank.quotas(10, Random(1)))
        val seen = (0 until 50).map { QuizBank.quotas(15, Random(it.toLong())) }.toSet()
        assertEquals(setOf(mapOf(Region.CM to 11, Region.AF to 3, Region.WORLD to 1), mapOf(Region.CM to 10, Region.AF to 3, Region.WORLD to 2)), seen)
    }

    @Test fun drawIsReproducibleAndShufflesChoices() {
        assertEquals(bank.draw(15, 42).map { it.id to it.choices }, bank.draw(15, 42).map { it.id to it.choices })
        assertNotEquals(bank.draw(15, 42).map { it.id }, bank.draw(15, 43).map { it.id })
        // shuffling keeps the right answer's text
        val original = bank.all.associateBy { it.id }
        for (q in bank.draw(15, 7)) assertEquals(original.getValue(q.id).choices[original.getValue(q.id).answer], q.choices[q.answer])
        // over many draws the right answer lands on every letter
        val letters = (0 until 30).flatMap { bank.draw(15, it.toLong()) }.map { it.answer }.toSet()
        assertEquals(setOf(0, 1, 2, 3), letters)
    }

    @Test fun noRepeatWithinASession() {
        val asked = HashSet<String>()
        repeat(4) { i ->
            val qs = bank.draw(15, 100L + i, asked)
            assertTrue(qs.none { it.id in asked }, "game $i repeats a question already asked")
            asked += qs.map { it.id }
        }
    }

    @Test fun reviewQuestionsExcludedUnlessAsked() {
        val b = QuizBank(listOf(q("a", review = true), q("b"), q("c", difficulty = 2)))
        assertEquals(listOf("b", "c"), b.draw(5, 1, filter = QuestionFilter.GENERAL).map { it.id }.sorted())
        assertEquals(3, b.draw(5, 1, includeReview = true).size)
    }

    @Test fun schoolTracksDrawOnlyTheirLevel() {
        val levels = listOf(QuestionFilter(Track.PRIMARY, "CM2"), QuestionFilter(Track.SECONDARY, "3e"), QuestionFilter(Track.SECONDARY, "Tle"),
            QuestionFilter(Track.HIGHER, "L1", "droit"), QuestionFilter(Track.HIGHER, "L1", "economie"), QuestionFilter(Track.HIGHER, "L1", "mathematiques"))
        for (f in levels) {
            assertTrue(bank.count(f) >= 10, "at least 10 questions for ${f.label} (${bank.count(f)})")
            val qs = bank.draw(15, 3, filter = f)
            assertTrue(qs.isNotEmpty() && qs.all { f.matches(it) }, f.label)
            assertEquals(qs.map { it.difficulty }.sorted(), qs.map { it.difficulty })
        }
        assertEquals(0, bank.count(QuestionFilter(Track.PRIMARY, "SIL")), "an empty level is simply « bientôt »")
        assertTrue(bank.draw(15, 1, filter = QuestionFilter(Track.PRIMARY, "SIL")).isEmpty())
    }

    @Test fun parseRejectsBadInputClearly() {
        assertFailsWith<Json.ParseError> { QuizBank.parse("""{"version":99,"questions":[]}""") }
        assertFailsWith<Json.ParseError> { QuizBank.parse("""{"questions":[{"id":"x"}]}""") }
        val bad = QuizBank(listOf(q("x").copy(choices = listOf("a", "a", "b", "c")), q("x"), q("y").copy(answer = 4)))
        val errs = bad.validate()
        assertTrue(errs.any { "choix en double" in it } && errs.any { "id en double" in it } && errs.any { "index de réponse" in it }, errs.toString())
        // a server status other than "approved" keeps the question out of games
        val b = QuizBank.parse("""{"version":2,"questions":[{"id":"u1","region":"CM","category":"C","difficulty":1,"question":"Q ?",""" +
            """"choices":["a","b","c","d"],"answer":0,"explanation":"e","source":"s","status":"draft","updatedAt":"2026-09-30"}]}""")
        assertTrue(b.all.single().review)
    }

    companion object {
        fun q(id: String, region: Region = Region.CM, difficulty: Int = 1, review: Boolean = false, answer: Int = 0) =
            Question(id, region, "Test", difficulty, "Question $id ?", listOf("a$id", "b$id", "c$id", "d$id"), answer, "parce que", "test", review)
    }
}

class QuestionSourceTest {
    private val dir = kotlin.io.path.createTempDirectory("qsrc").toFile()
    @AfterTest fun clean() { dir.deleteRecursively() }

    private val fallback = object : QuestionSource {
        override val origin = "test"
        override fun bank() = QuizBank(listOf(QuizBankTest.q("a"), QuizBankTest.q("b")))
    }

    private fun serverJson(vararg ids: String, text: String = "Nouvelle") = """{"version":2,"questions":[""" + ids.joinToString(",") {
        """{"id":"$it","region":"AF","category":"C","difficulty":2,"question":"$text $it ?","choices":["1","2","3","4"],"answer":1,""" +
            """"explanation":"e","source":"serveur","status":"approved","updatedAt":"2026-09-30T10:00:00Z","lang":"fr"}"""
    } + "]}"

    @Test fun offlineFallbackWhenNoCache() {
        val s = CachedQuestionSource(File(dir, "cache.json"), fallback)
        assertEquals(listOf("a", "b"), s.bank().all.map { it.id })
    }

    @Test fun serverQuestionsMergeAndReplaceById() {
        val s = CachedQuestionSource(File(dir, "cache.json"), fallback)
        assertNull(s.update(serverJson("b", "c")))
        val all = s.bank().all.associateBy { it.id }
        assertEquals(setOf("a", "b", "c"), all.keys)
        assertEquals(Region.AF, all.getValue("b").region, "the server's version wins")
        assertEquals("2026-09-30T10:00:00Z", all.getValue("c").updatedAt)
        // a new instance (app restart) reads the cache file
        assertEquals(3, CachedQuestionSource(File(dir, "cache.json"), fallback).bank().all.size)
    }

    @Test fun badOrOversizedCacheIsRefusedAndIgnored() {
        val f = File(dir, "cache.json")
        val s = CachedQuestionSource(f, fallback, maxBytes = 600)
        assertNotNull(s.update(serverJson("c", "d", "e")), "too big for the bound")
        assertNotNull(s.update("{not json"))
        assertNotNull(s.update("""{"version":2,"questions":[{"id":"z","region":"CM","category":"C","difficulty":9,"question":"Q ?","choices":["1","1","3","4"],"answer":0,"explanation":"e","source":"s"}]}"""))
        assertFalse(f.exists())
        f.writeText("garbage")                                   // corrupted on disk: still works offline
        assertEquals(2, CachedQuestionSource(f, fallback).bank().all.size)
    }
}
