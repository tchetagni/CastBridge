package castbridge.core

import castbridge.core.quiz.*
import java.io.File
import kotlin.test.*

/** « La répétition d'une question demande au moins 300 parties » (docs/QUIZ.md § Règle des 300 parties). */
class QuizNoRepeatTest {
    private fun q(id: String, region: Region, d: Int, track: Track = Track.GENERAL, level: String? = null, field: String? = null) =
        Question(id, region, "Cat", d, "Question $id ?", listOf("a", "b", "c", "d"), 0, "e", "s", track = track, level = level, field = field)

    /** A general bank with exactly 70/20/10 and difficulties spread evenly. */
    private fun generalBank(total: Int): QuizBank {
        val cm = total * 70 / 100; val af = total * 20 / 100; val w = total - cm - af
        val qs = ArrayList<Question>()
        for ((r, n) in listOf(Region.CM to cm, Region.AF to af, Region.WORLD to w)) for (i in 0 until n) qs += q("${r.name}-$i", r, 1 + i % 5)
        return QuizBank(qs)
    }

    private fun schoolBank(total: Int) = QuizBank((0 until total).map { q("s-$it", Region.WORLD, 1 + it % 5, Track.SECONDARY, "3e") })

    private val school3e = QuestionFilter(Track.SECONDARY, "3e")

    /** Plays [games] games in a row with a history; returns per-question the game numbers it was asked at. */
    private fun simulate(bank: QuizBank, filter: QuestionFilter, games: Int, history: QuizHistory = QuizHistory(),
                         reports: MutableList<QuizBank.DrawReport>? = null): Map<String, List<Int>> {
        val course = filter.courseKey
        val seen = HashMap<String, MutableList<Int>>()
        for (g in 1..games) {
            val d = bank.drawDetailed(15, seed = g * 7919L, filter = filter, history = history)
            reports?.add(d.report)
            history.beginGame(course)
            history.record(course, d.questions.map { it.id })
            d.questions.forEach { seen.getOrPut(it.id) { ArrayList() } += g }
        }
        return seen
    }

    private fun minGap(seen: Map<String, List<Int>>): Int =
        seen.values.filter { it.size > 1 }.minOfOrNull { l -> l.zipWithNext { a, b -> b - a }.min() } ?: Int.MAX_VALUE

    @Test fun noRepeatOver300GamesWithExactly300x15SchoolQuestions() {
        val seen = simulate(schoolBank(4500), school3e, 300)
        assertEquals(4500, seen.size, "every question of the bank is asked once in 300 games")
        assertTrue(seen.values.all { it.size == 1 }, "no question twice in 300 games")
    }

    @Test fun noRepeatOver300GamesGeneralKnowledgeKeepingQuotas() {
        val bank = generalBank(4500)
        val history = QuizHistory()
        val reports = ArrayList<QuizBank.DrawReport>()
        val seen = simulate(bank, QuestionFilter.GENERAL, 300, history, reports)
        assertTrue(seen.values.all { it.size == 1 }, "repeated: ${seen.filter { it.value.size > 1 }.keys.take(5)}")
        assertTrue(reports.all { it.clean }, "no repeat, no broken quota")
    }

    @Test fun minimalGapIsAtLeast300OverMoreThanOneCycle() {
        val bank = generalBank(5000)
        val seen = simulate(bank, QuestionFilter.GENERAL, 900)
        val gap = minGap(seen)
        assertTrue(seen.values.any { it.size > 1 }, "the bank is exhausted and reused after a cycle")
        assertTrue(gap >= 300, "smallest gap between two askings = $gap games")
        val school = simulate(schoolBank(5000), school3e, 900)
        assertTrue(minGap(school) >= 300, "school: ${minGap(school)}")
    }

    @Test fun quotas70_20_10AndClimbSurviveTheHistory() {
        val bank = generalBank(4700)
        val history = QuizHistory()
        for (g in 1..120) {
            val qs = bank.draw(15, g.toLong(), history = history)
            val by = qs.groupingBy { it.region }.eachCount()
            assertTrue((by[Region.CM] ?: 0) in 10..11, "CM game $g: $by")
            assertEquals(3, by[Region.AF], "AF game $g")
            assertTrue((by[Region.WORLD] ?: 0) in 1..2, "WORLD game $g: $by")
            assertEquals(qs.map { it.difficulty }.sorted(), qs.map { it.difficulty })
            history.beginGame("general"); history.record("general", qs.map { it.id })
        }
    }

    @Test fun tooSmallBankRepeatsTheLongestUnseenFirstAndSaysSo() {
        val bank = schoolBank(20)
        val history = QuizHistory()
        var previous: Set<String> = emptySet()
        for (g in 1..10) {
            val d = bank.drawDetailed(15, g.toLong(), filter = school3e, history = history)
            val ids = d.questions.map { it.id }.toSet()
            if (g > 1) {
                // the 5 questions left out of the previous game are the longest unseen: they are all in this game
                val leftOut = bank.all.map { it.id }.toSet() - previous
                assertTrue(ids.containsAll(leftOut), "game $g must start with the longest-unseen questions")
                assertEquals(if (g == 2) 10 else 15, d.report.repeats, "20 questions only: game 2 has 5 fresh, then all are repeats (game $g)")
                assertNotNull(d.report.shortestGap)
            } else assertTrue(d.report.clean)
            history.beginGame(school3e.courseKey); history.record(school3e.courseKey, ids)
            previous = ids
        }
        // never fewer questions than the bank offers
        assertEquals(15, bank.draw(15, 1, filter = school3e, history = history).size)
    }

    @Test fun smallBankNeverPicksARandomRecentQuestionWhenAnOlderOneExists() {
        val bank = schoolBank(40)
        val history = QuizHistory()
        val course = school3e.courseKey
        // 40 questions, 15 per game: games 1 and 2 use 30 fresh ones, game 3 must take the 10 unseen + the 5 OLDEST (from game 1)
        val g1 = bank.draw(15, 1, filter = school3e, history = history).map { it.id }; history.beginGame(course); history.record(course, g1)
        val g2 = bank.draw(15, 2, filter = school3e, history = history).map { it.id }; history.beginGame(course); history.record(course, g2)
        assertTrue(g1.intersect(g2.toSet()).isEmpty())
        val g3 = bank.draw(15, 3, filter = school3e, history = history).map { it.id }
        assertEquals(10, g3.count { it !in g1 && it !in g2 })
        assertEquals(5, g3.count { it in g1 }, "the five repeats come from the oldest game")
        assertEquals(0, g3.count { it in g2 })
    }

    @Test fun generalKeepsQuotasWhenOneRegionRunsDry() {
        val qs = (0 until 40).map { q("CM-$it", Region.CM, 1 + it % 5) } + (0 until 12).map { q("AF-$it", Region.AF, 1 + it % 5) } +
            (0 until 6).map { q("W-$it", Region.WORLD, 1 + it % 5) }
        val bank = QuizBank(qs)
        val history = QuizHistory()
        for (g in 1..20) {
            val d = bank.drawDetailed(15, g.toLong(), history = history)
            val by = d.questions.groupingBy { it.region }.eachCount()
            assertEquals(3, by[Region.AF], "game $g")
            assertTrue((by[Region.CM] ?: 0) in 10..11 && (by[Region.WORLD] ?: 0) in 1..2, "quotas kept: $by")
            history.beginGame("general"); history.record("general", d.questions.map { it.id })
        }
    }

    @Test fun sessionExcludeStillWorksAndDoesNotNeedAHistory() {
        val bank = schoolBank(100)
        val first = bank.draw(15, 1, filter = school3e).map { it.id }.toSet()
        val second = bank.draw(15, 2, exclude = first, filter = school3e).map { it.id }.toSet()
        assertTrue(first.intersect(second).isEmpty())
    }

    @Test fun remainingFreshAndCapacity() {
        val bank = generalBank(4500)
        val f0 = bank.remainingFresh(QuestionFilter.GENERAL)
        assertEquals(4500, f0.pool); assertEquals(4500, f0.fresh)
        assertEquals(300, f0.capacityGames)
        assertTrue(f0.sufficient)
        val h = QuizHistory()
        for (g in 1..10) { val qs = bank.draw(15, g.toLong(), history = h); h.beginGame("general"); h.record("general", qs.map { it.id }) }
        val f1 = bank.remainingFresh(QuestionFilter.GENERAL, h)
        assertEquals(4500 - 150, f1.fresh)
        assertTrue(f1.gamesLeft in 286..290, "games left ${f1.gamesLeft}")
        val small = QuizBank((0 until 200).map { q("x$it", Region.CM, 1 + it % 5) }).remainingFresh(QuestionFilter.GENERAL)
        assertFalse(small.sufficient)
        val sc = schoolBank(450).remainingFresh(school3e)
        assertEquals(30, sc.capacityGames)
        assertEquals(0, QuizBank(emptyList()).remainingFresh(school3e).capacityGames)
    }

    // ------------------------------------------------------------ persistence

    private fun tmp(): File = File.createTempFile("qhist", "").apply { delete(); mkdirs(); deleteOnExit() }

    @Test fun historySurvivesARestart() {
        val dir = tmp()
        val book = QuizHistoryBook(dir)
        val h = book.host
        h.beginGame("general"); h.record("general", listOf("a", "b", "c"))
        h.beginGame("general"); h.record("general", listOf("d"))
        h.beginGame("primary/CM2"); h.record("primary/CM2", listOf("z"))
        book.save(QuizHistoryBook.HOST); book.flush()
        val again = QuizHistoryBook(dir).host
        assertEquals(2, again.games("general"))
        assertEquals(1, again.games("primary/CM2"))
        assertEquals(2, again.age("general", "a"))          // asked in game 1, the next game is number 3: 2 games ago
        assertEquals(1, again.age("general", "d"))
        assertEquals(QuestionHistory.NEVER, again.age("general", "zzz"))
        assertEquals(1, again.age("primary/CM2", "z"))
        assertTrue(book.diskBytes() < 1000)
    }

    @Test fun damagedFileFallsBackToTheBackupOrIsLenient() {
        val dir = tmp()
        val book = QuizHistoryBook(dir)
        book.host.beginGame("general"); book.host.record("general", listOf("a"))
        book.save("host"); book.flush()
        book.host.beginGame("general"); book.host.record("general", listOf("b"))
        book.save("host"); book.flush()                      // now there is a .bak with game 1
        val file = dir.listFiles { f -> f.name.endsWith(".qh") }!!.single()
        file.writeText("CBQH1\n@general 2\na 1\nb ")          // power cut in the middle of a line
        val lenient = QuizHistoryBook(dir).host
        assertEquals(2, lenient.games("general"))
        assertEquals(2, lenient.age("general", "a"))
        file.writeText("garbage\u0000\u0001")
        val viaBackup = QuizHistoryBook(dir).host
        assertEquals(1, viaBackup.games("general"), "main file unusable: the previous version is used")
        file.delete(); File(file.path + ".bak").delete()
        assertEquals(0, QuizHistoryBook(dir).host.games("general"))
    }

    @Test fun oldEntriesArePrunedSoTheFileStaysSmall() {
        val h = QuizHistory()
        for (g in 1..1000) { h.beginGame("general"); h.record("general", (0 until 15).map { "q${g}_$it" }) }
        assertTrue(h.entries("general") <= 300 * 15, "kept ${h.entries("general")}")
        val bytes = h.serialize().toByteArray().size
        assertTrue(bytes < 150_000, "300 games of 15 questions = $bytes bytes")
    }

    @Test fun profilesAreLimitedAndEvicted() {
        val dir = tmp()
        val book = QuizHistoryBook(dir, maxProfiles = 3)
        for (i in 1..6) {
            book.profile("dev:phone$i").also { it.beginGame("general"); it.record("general", listOf("a")) }
            book.save("dev:phone$i"); book.flush(); Thread.sleep(15)
        }
        assertEquals(3, dir.listFiles { f -> f.name.endsWith(".qh") }!!.size)
    }

    @Test fun multiplayerUsesTheUnionOfThePlayersHistories() {
        val bank = schoolBank(60)
        val book = QuizHistoryBook(null)
        val course = school3e.courseKey
        val a = book.profile("dev:a"); val b = book.profile("dev:b")
        val asA = bank.all.take(15).map { it.id }; val asB = bank.all.drop(15).take(15).map { it.id }
        a.beginGame(course); a.record(course, asA)
        b.beginGame(course); b.record(course, asB)
        val view = book.viewFor(listOf("dev:a", "dev:b"))
        assertEquals(1, view.age(course, asA[0])); assertEquals(1, view.age(course, asB[0]))
        val game = bank.draw(15, 5, filter = school3e, history = view).map { it.id }
        assertTrue(game.none { it in asA || it in asB }, "neither player has seen any of these")
        // alone, player A may be asked B's questions
        val alone = bank.draw(30, 5, filter = school3e, history = book.viewFor(listOf("dev:a"))).map { it.id }
        assertTrue(alone.none { it in asA })
        // no identified player: the host's history
        book.host.beginGame(course); book.host.record(course, bank.all.takeLast(15).map { it.id })
        assertEquals(1, book.viewFor(emptyList()).age(course, bank.all.last().id))
    }

    @Test fun coursesAreIndependent() {
        val h = QuizHistory()
        h.beginGame("general"); h.record("general", listOf("x"))
        assertEquals(QuestionHistory.NEVER, h.age("primary/CM2", "x"))
        assertEquals("general", QuestionFilter.GENERAL.courseKey)
        assertEquals("primary/CM2", QuestionFilter(Track.PRIMARY, "CM2").courseKey)
        assertEquals("higher/L1/droit", QuestionFilter(Track.HIGHER, "L1", "droit").courseKey)
    }
}
