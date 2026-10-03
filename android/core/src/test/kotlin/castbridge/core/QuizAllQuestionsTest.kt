package castbridge.core

import castbridge.core.quiz.*
import kotlin.test.*

/**
 * Toutes les questions du dépôt sont embarquées (docs/agent-reports/quiz-toutes-les-questions.md) : 217 494 questions, réservables PAR QUESTION
 * (≈ 30 %), chargées à la demande par fichier de lot : une partie ne lit que quelques fichiers (≤ 2000 questions, ≤ 3 Mo de JSON).
 */
class QuizAllQuestionsTest {
    private val res = "/castbridge/quiz/"
    private fun resource(p: String): ByteArray? = EmbeddedLevels::class.java.getResourceAsStream(p)?.use { it.readBytes() }

    /** A loader that records what it reads (path relative to the quiz resources) and how many bytes. */
    private class Spy(val res: String) {
        val reads = ArrayList<String>(); var bytes = 0L
        val reader: (String) -> ByteArray? = { p ->
            reads += p.removePrefix(res)
            EmbeddedLevels::class.java.getResourceAsStream(p)?.use { it.readBytes() }?.also { if (!p.endsWith("index.json")) bytes += it.size }
        }
        fun reset() { reads.clear(); bytes = 0 }
    }

    private fun levels(trial: Boolean = true, rot: Rotation = Rotation.Memory(0), spy: Spy? = null) =
        EmbeddedLevels(reader = spy?.reader ?: ::resource, rotation = rot, trialOpen = trial)

    @Test fun theIndexAnnounces217494QuestionsInTheTwentyEightLevels() {
        val spy = Spy(res)
        val lv = levels(spy = spy)
        assertEquals(28, lv.levels.size, lv.levels.map { it.key }.toString())
        assertEquals(217_494, lv.totalCount())
        assertEquals(listOf("embedded/index.json"), spy.reads, "counting never reads a level file")
        for (l in lv.levels) {
            assertEquals(l.count, l.freeCount + l.reservedCount, l.key)
            assertEquals(l.freeCount, l.files.sumOf { it.count }, l.key)
            assertEquals(l.reservedCount, l.reservedFiles.sumOf { it.count }, l.key)
            assertTrue(l.files.all { it.name.startsWith("embedded/") } && l.reservedFiles.all { it.name.startsWith("embedded-reserved/") }, l.key)
            assertTrue(l.freeCount > 0 && l.reservedCount > 0, "${l.key}: no level is reserved as a whole")
        }
        assertEquals(setOf("tle", "l1", "l2", "l3"), lv.levels.map { it.key }.toSet() intersect setOf("tle", "l1", "l2", "l3"))
    }

    @Test fun aboutThirtyPercentOfEveryLevelIsReservable() {
        for (l in EmbeddedLevels(rotation = Rotation.Memory(0)).levels) {
            val pct = 100.0 * l.reservedCount / l.count
            assertTrue(pct in 29.0..31.0, "${l.key}: $pct %")
        }
    }

    @Test fun aGameNeverLoadsMoreThan2000QuestionsNorMoreThan3MbOfJson() {
        for (trial in listOf(true, false)) {
            val spy = Spy(res)
            val lv = levels(trial, spy = spy)
            for (l in lv.levels) {
                for (g in 0 until 5) {
                    spy.reset()
                    val bank = lv.load(l)
                    assertTrue(bank.all.size in 1..2000, "${l.key} (trial=$trial) game $g: ${bank.all.size} questions")
                    assertTrue(spy.bytes <= 3_000_000, "${l.key} game $g: ${spy.bytes} bytes of JSON")
                    assertEquals(bank.all.size, lv.lastLoad.questions, l.key)
                    lv.advance(l)
                }
            }
        }
    }

    @Test fun neverAWholeLevelInMemory() {
        val spy = Spy(res)
        val lv = levels(spy = spy)
        val l2 = lv.levels.first { it.key == "l2" }
        assertTrue(l2.count > 20_000)
        spy.reset()
        val b = lv.load(l2)
        assertTrue(b.all.size <= 2000 && b.all.size < l2.count / 5, "${b.all.size} of ${l2.count}")
        assertTrue(spy.reads.size < (l2.files.size + l2.reservedFiles.size) / 3, spy.reads.toString())
    }

    @Test fun rotationReachesEveryQuestionOfALevelAfterNGames() {
        for (key in listOf("l2", "culture-generale", "cp")) {
            val lv = levels(rot = Rotation.Memory(7))
            val l = lv.levels.first { it.key == key }
            val nFiles = l.files.size + l.reservedFiles.size
            val seen = HashSet<String>()
            var games = 0
            while (seen.size < l.count && games <= nFiles) {
                lv.load(l).all.forEach { seen += it.id }
                lv.advance(l); games++
            }
            assertEquals(l.count, seen.size, "$key: every question reached after $games games (files: $nFiles)")
            assertTrue(games <= nFiles, "$key: $games games for $nFiles files")
        }
    }

    @Test fun rotationIsDeterministicForTheSameStartAndDiffersFromGameToGame() {
        val a = levels(rot = Rotation.Memory(3)); val b = levels(rot = Rotation.Memory(3))
        val l = a.levels.first { it.key == "6e" }
        val g1 = a.selection(l).map { it.name }
        assertEquals(g1, b.selection(l).map { it.name })
        a.advance(l)
        val g2 = a.selection(l).map { it.name }
        assertTrue(g1.intersect(g2.toSet()).isEmpty(), "two consecutive games use different files")
        assertEquals(g1, run { val c = levels(rot = Rotation.Memory(3)); c.selection(c.levels.first { it.key == "6e" }).map { it.name } })
    }

    @Test fun aGameMixesTheLotsOfALevel() {
        val lv = levels()
        val l = lv.levels.first { it.key == "2nde" }
        assertTrue(l.lots.size > 5)
        assertTrue(lv.selection(l).map { it.lot }.toSet().size >= 2, "a game is not one subject only")
        val cg = lv.levels.first { it.key == "culture-generale" }
        assertEquals(3, cg.lots.size)
        val regions = lv.load(cg).all.map { it.region }.toSet()
        assertTrue(regions.size >= 2, regions.toString())
    }

    @Test fun withTrialClosedOnlyFreeQuestionsAreEverLoaded() {
        val spy = Spy(res)
        val lv = levels(trial = false, spy = spy)
        val l = lv.levels.first { it.key == "cp" }
        val reservedIds = levels().let { all -> l.reservedFiles.flatMap { all.loadFile(l, it).map { q -> q.id } }.toSet() }
        assertTrue(reservedIds.size > 1000)
        val seen = HashSet<String>()
        repeat(l.files.size + 2) { lv.load(l).all.forEach { seen += it.id }; lv.advance(l) }
        assertEquals(l.freeCount, seen.size, "every free question, and only those")
        assertTrue(seen.none { it in reservedIds })
        assertTrue(spy.reads.none { it.startsWith("embedded-reserved/") }, "no reserved file is even opened")
        assertEquals(l.freeCount, lv.openCount(l)); assertEquals(l.count, levels(true).openCount(l))
    }

    @Test fun withTrialOpenReservedQuestionsArePlayable() {
        val lv = levels(trial = true)
        val l = lv.levels.first { it.key == "ce1" }
        val reservedIds = l.reservedFiles.flatMap { lv.loadFile(l, it).map { q -> q.id } }.toSet()
        val seen = HashSet<String>()
        repeat(l.files.size + l.reservedFiles.size + 1) { lv.load(l).all.forEach { seen += it.id }; lv.advance(l) }
        assertTrue(seen.any { it in reservedIds })
        assertEquals(l.count, seen.size)
    }

    @Test fun aFieldFilterLoadsOnlyTheLotsOfThatField() {
        val spy = Spy(res)
        val lv = levels(spy = spy)
        val l = lv.levels.first { it.key == "l1" }
        val b = lv.load(l, "droit")
        assertTrue(b.all.isNotEmpty() && b.all.all { it.field == "droit" }, "only droit")
        assertTrue(spy.reads.filter { it != "embedded/index.json" }.all { "/droit-l1" in it }, spy.reads.toString())
    }

    @Test fun aMissingReservedFolderIsTolerated() {
        val lv = EmbeddedLevels(reader = { p -> if ("embedded-reserved" in p) null else resource(p) }, rotation = Rotation.Memory(0), trialOpen = true)
        val l = lv.levels.first { it.key == "cm1" }
        repeat(8) { assertTrue(lv.load(l).all.isNotEmpty()); lv.advance(l) }
    }

    @Test fun questionsKeepTheirReviewStatusAndTheirLotIds() {
        val lv = levels()
        for (key in listOf("cp", "tle", "l3", "culture-generale", "form-5")) {
            val l = lv.levels.first { it.key == key }
            val qs = lv.load(l).all
            assertEquals(qs.size, qs.map { it.id }.toSet().size, key)
            assertTrue(qs.all { it.status == "review" && it.review && it.choices.size == 4 && it.answer in 0..3 }, key)
            assertEquals(emptyList(), lv.load(l).validate().take(3), key)
        }
    }

    @Test fun theOldIndexWithOneFilePerLevelStillWorks() {
        val row = """["q1","WORLD","cat",1,"Question ?",["a","b","c","d"],0,"expl",0,null,"review",null]"""
        val file = """{"v":1,"level":"CP","track":"primary","sources":["s"],"q":[$row]}"""
        val index = """{"v":1,"seed":"t","levels":[{"key":"cp","level":"CP","track":"primary","count":1,"file":"cp.json","lots":["cp"]}],"reservedNotEmbedded":["tle"]}"""
        val lv = EmbeddedLevels(reader = { p -> when { p.endsWith("embedded/index.json") -> index.toByteArray(); p.endsWith("embedded/cp.json") -> file.toByteArray(); else -> null } })
        val l = lv.levels.single()
        assertEquals(1, l.count); assertEquals(1, l.freeCount); assertEquals(0, l.reservedCount)
        assertEquals(listOf("q1"), lv.load(l).all.map { it.id })
    }

    @Test fun theBaseBankIsUnchangedAndTheLoadedSubsetReplacesThePreviousLevel() {
        val spy = Spy(res)
        val src = EmbeddedQuestionSource(levels = levels(spy = spy))
        assertEquals(320, src.bank().all.size)
        val cm2 = QuestionFilter(Track.PRIMARY, "CM2")
        val b = src.bankFor(cm2)
        assertSame(b, src.bankFor(cm2), "kept while the level does not change")
        assertTrue(b.all.size in 321..2320)
        val n = spy.reads.size
        src.bankFor(QuestionFilter(Track.PRIMARY, "CP"))
        assertTrue(spy.reads.size > n)
        assertEquals(320, src.bank().all.size)
        val l1 = QuestionFilter(Track.HIGHER, "L1", "droit")
        assertNotSame(src.bank(), src.bankFor(l1), "L1 now has bundled questions")
    }

    @Test fun aNewGameMovesToOtherFilesAndTheIdsStayStableAcrossGames() {
        val src = EmbeddedQuestionSource(levels = levels())
        val f = QuestionFilter(Track.SECONDARY, "6e")
        src.newGame(f)                       // first game: the sample already loaded for it is kept
        val a = src.bankFor(f)
        src.newGame(f)                       // next game: other files
        val b = src.bankFor(f)
        assertNotSame(a, b)
        val ia = a.all.filter { it.level == "6e" }.map { it.id }.toSet(); val ib = b.all.filter { it.level == "6e" }.map { it.id }.toSet()
        assertTrue(ia.isNotEmpty() && ib.isNotEmpty() && ia.intersect(ib).isEmpty(), "disjoint samples: ids are unique per question, so the anti-repetition by id keeps working")
    }

    @Test fun anIdleSourceMovesOnByItself() {
        var now = 0L
        val src = EmbeddedQuestionSource(levels = levels(), clock = { now }, idleMs = 60_000)
        val f = QuestionFilter(Track.PRIMARY, "CE2")
        val a = src.bankFor(f); now += 1000
        assertSame(a, src.bankFor(f))
        now += 120_000
        assertNotSame(a, src.bankFor(f))
    }

    @Test fun anInstalledLotWithTheSameIdsIsNotDuplicated() {
        val src = EmbeddedQuestionSource(levels = levels())
        val f = QuestionFilter(Track.PRIMARY, "CM1")
        val own = src.bankFor(f).all.filter { it.level == "CM1" }.take(5)
        assertEquals(5, own.size)
        val file = java.io.File.createTempFile("qcache", ".json").apply { deleteOnExit() }
        val cached = CachedQuestionSource(file, src)
        val json = Json.write(linkedMapOf("version" to 2, "questions" to own.map { q ->
            linkedMapOf("id" to q.id, "track" to "primary", "level" to "CM1", "region" to "WORLD", "category" to q.category, "difficulty" to q.difficulty,
                "question" to q.question + " (révisée)", "choices" to q.choices, "answer" to q.answer, "explanation" to q.explanation, "source" to "s", "status" to "approved") }))
        assertNull(cached.update(json))
        val b = cached.bankFor(f)
        assertEquals(b.all.size, b.all.map { it.id }.toSet().size, "no duplicate id")
        assertTrue(own.all { q -> b.all.single { it.id == q.id }.question.endsWith("(révisée)") })
    }

    @Test fun roomsSignalTheStartOfEachGameToTheSource() {
        val signals = ArrayList<QuestionFilter>()
        val room = QuizRoom(EmbeddedQuestionSource().bank(), autoTick = false, bankFor = { EmbeddedQuestionSource().bank() }, onNewGame = { signals += it })
        room.startGame(1L)
        assertEquals(1, signals.size)
    }
}
