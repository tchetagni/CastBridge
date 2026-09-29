package castbridge.core.quiz

import castbridge.core.quiz.Json.long
import castbridge.core.quiz.Json.str

/**
 * Best scores kept on the TV (solo play against oneself, no network needed): one board per way of playing and
 * parcours (« Millionnaire · Culture générale », « Entraînement · 3e »…), at most [perBoard] entries each.
 * Pure and serializable: the app stores [toJson] in its preferences.
 */
class HighScores(entries: List<Entry> = emptyList(), private val perBoard: Int = 10) {
    data class Entry(val board: String, val name: String, val score: Long, val detail: String, val atMs: Long)

    private val list = ArrayList(entries)

    /** Adds [e]; returns its rank on its board (1 = record) or null if it is not good enough to be kept. */
    @Synchronized fun add(e: Entry): Int? {
        list += e
        val board = sorted(e.board)
        val kept = board.take(perBoard).toSet()
        list.removeAll { it.board == e.board && it !in kept }
        return board.indexOf(e).takeIf { it in 0 until perBoard }?.plus(1)
    }

    @Synchronized fun top(board: String, n: Int = 5): List<Entry> = sorted(board).take(n)

    /** Boards that have scores, most recently played first. */
    @Synchronized fun boards(): List<String> = list.sortedByDescending { it.atMs }.map { it.board }.distinct()

    /** Best score first; for equal scores the older one stays ahead (it was there first). */
    private fun sorted(board: String) = list.filter { it.board == board }.sortedWith(compareByDescending<Entry> { it.score }.thenBy { it.atMs })

    @Synchronized fun toJson(): String = Json.write(mapOf("version" to 1, "scores" to list.map {
        linkedMapOf("board" to it.board, "name" to it.name, "score" to it.score, "detail" to it.detail, "at" to it.atMs)
    }))

    companion object {
        /** Reads what [toJson] wrote; anything unreadable gives an empty table (never blocks the game). */
        fun fromJson(s: String?, perBoard: Int = 10): HighScores = runCatching {
            @Suppress("UNCHECKED_CAST")
            val l = Json.obj(s ?: return HighScores(perBoard = perBoard))["scores"] as List<Map<String, Any?>>
            HighScores(l.mapNotNull { m ->
                Entry(m.str("board") ?: return@mapNotNull null, m.str("name") ?: "", m.long("score") ?: 0, m.str("detail") ?: "", m.long("at") ?: 0)
            }, perBoard)
        }.getOrElse { HighScores(perBoard = perBoard) }
    }
}
