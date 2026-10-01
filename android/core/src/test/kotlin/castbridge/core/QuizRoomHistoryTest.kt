package castbridge.core

import castbridge.core.quiz.*
import kotlin.test.*

/** The room feeds and uses the anti-repetition histories (host for solo, phones for Duel / candidate). */
class QuizRoomHistoryTest {
    private var now = 1_000L
    private val filter = QuestionFilter(Track.SECONDARY, "3e")
    private val course = filter.courseKey
    private fun bank(n: Int) = QuizBank((0 until n).map {
        Question("s-$it", Region.WORLD, "Cat", 1 + it % 5, "Question $it ?", listOf("a", "b", "c", "d"), 0, "e", "s", track = Track.SECONDARY, level = "3e")
    })
    private fun room(n: Int, book: QuizHistoryBook) =
        QuizRoom(bank(n), clock = { now }, random = java.util.Random(3), autoTick = false, histories = book)

    @Test fun soloGameIsRecordedInTheHostsHistoryQuestionByQuestion() {
        val book = QuizHistoryBook(null)
        val r = room(200, book)
        assertTrue(r.configure(QuizRoom.Mode.MILLIONAIRE, QuizRoom.Play.FRIENDS, filter))
        assertNull(r.startGame(seed = 1))
        assertEquals(1, book.host.games(course))
        assertEquals(1, book.host.entries(course), "only the question on screen is used up")
        val g = r.game!!
        val qid = g.question.id
        assertTrue(r.hostAct("select", g.question.answer)); assertTrue(r.hostAct("confirm"))
        now += r.suspenseMs; r.tick()
        assertTrue(r.hostAct("next"))
        assertEquals(2, book.host.entries(course))
        assertEquals(1, book.host.age(course, qid), "asked in the game just played = 1 game ago")
    }

    @Test fun nextGameNeverRepeatsTheLastOnesWhileTheBankIsBigEnough() {
        val book = QuizHistoryBook(null)
        val r = room(450, book)
        r.configure(QuizRoom.Mode.DUEL, QuizRoom.Play.PRACTICE, filter)
        val p = r.join(r.code, "Ali", null, "device-abcdef01").player!!
        val asked = HashSet<String>()
        repeat(40) { g ->
            assertNull(r.startGame(seed = g.toLong()), "game $g")
            val d = r.duel!!
            // play the 10 questions out: skip through every phase
            var guard = 0
            while (r.duel!!.phase != QuizDuel.Phase.FINISHED && guard++ < 200) { r.hostSkip(); now += 10_000; r.tick(); r.touch(p) }
            val ids = d.questions.map { it.id }
            assertTrue(ids.none { it in asked }, "game $g repeats a question: ${ids.filter { it in asked }}")
            asked += ids
            r.backToLobby()
        }
        assertEquals(40, book.profile("dev:device-abcdef01").games(course))
        assertEquals(0, book.host.games(course), "the host is not used when a phone is identified")
        assertNull(r.lastDrawReport)
    }

    @Test fun candidateOnPhoneUsesThePhonesHistoryAndASmallBankIsSignalled() {
        val book = QuizHistoryBook(null)
        val r = room(20, book)
        r.configure(QuizRoom.Mode.MILLIONAIRE, QuizRoom.Play.FRIENDS, filter)
        val cand = r.join(r.code, "Candidat", null, "phone-12345678").player!!
        r.setCandidate(cand.id)
        assertNull(r.startGame(seed = 1))
        assertEquals(1, book.profile("dev:phone-12345678").games(course))
        assertEquals(0, book.host.games(course))
        assertNull(r.lastDrawReport, "first game: nothing to repeat yet")
        r.hostAct("walk"); r.backToLobby()
        assertNull(r.startGame(seed = 2))
        // 20 questions, only the first shown of game 1 is recorded: still a clean game
        val f = r.freshness()
        assertEquals(1, f.capacityGames)
        assertFalse(f.sufficient)
    }

    @Test fun phonesWithoutDeviceIdFallBackToTheHost() {
        val book = QuizHistoryBook(null)
        val r = room(100, book)
        r.configure(QuizRoom.Mode.DUEL, QuizRoom.Play.FRIENDS, filter)
        r.join(r.code, "Anonyme")                      // no device id: no stable profile
        r.touch(r.players().first())
        assertNull(r.startGame(seed = 4))
        assertEquals(1, book.host.games(course))
    }
}
