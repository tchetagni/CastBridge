package castbridge.core

import castbridge.core.quiz.*
import kotlin.test.*

class HighScoresTest {
    private fun e(board: String, score: Long, at: Long, name: String = "TV") = HighScores.Entry(board, name, score, "", at)

    @Test fun keepsTheBestPerBoardAndSurvivesJson() {
        val h = HighScores(perBoard = 3)
        assertEquals(1, h.add(e("M", 100, 1)))
        assertEquals(1, h.add(e("M", 500, 2)), "new record")
        assertEquals(3, h.add(e("M", 50, 3)))
        assertEquals(3, h.add(e("M", 100, 4)), "same score: the older one stays ahead")
        assertNull(h.add(e("M", 10, 5)), "not in the top 3")
        assertEquals(listOf(500L, 100L, 100L), h.top("M").map { it.score })
        h.add(e("P", 7, 6))
        val back = HighScores.fromJson(h.toJson(), perBoard = 3)
        assertEquals(h.top("M"), back.top("M")); assertEquals(listOf("P", "M"), back.boards())
        assertTrue(HighScores.fromJson("{broken").top("M").isEmpty())
        assertTrue(HighScores.fromJson(null).boards().isEmpty())
    }
}

class QuizSoloTest {
    /** Solo at the remote: no player, no network, no HTTP server at all. */
    @Test fun soloMillionaireAndPracticeNeedNoNetwork() {
        var now = 0L
        val r = QuizRoom(EmbeddedQuestionSource().bank(), clock = { now }, autoTick = false)
        assertNull(r.startGame(seed = 1), "a solo Millionaire starts with nobody connected")
        assertTrue(r.hostAct("fifty")); assertTrue(r.hostAct("audience")); assertTrue(r.hostAct("phone", arg = ""))
        assertTrue(r.game!!.audience != null && r.game!!.phone!!.simulated)
        r.hostAct("select", r.game!!.question.answer); r.hostAct("confirm"); now += 5_000; r.tick()
        assertEquals(true, r.game!!.lastCorrect)
        r.backToLobby()
        assertTrue(r.configure(QuizRoom.Mode.MILLIONAIRE, QuizRoom.Play.PRACTICE, QuestionFilter(Track.HIGHER, "L1", "mathematiques")))
        assertNull(r.startGame(seed = 2))
        assertTrue(r.game!!.practice)
        r.close()
    }
}
