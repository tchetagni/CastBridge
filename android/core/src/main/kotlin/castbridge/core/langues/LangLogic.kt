package castbridge.core.langues

import java.text.Normalizer

/** Answer checking for typed language exercises (dictation, cloze, translate, order). Pure, no I/O. */
object LangMarking {
    /** NFKC (full-width → ASCII), lower case, no punctuation (Latin and CJK), collapsed spaces; accents removed unless [strictAccents]. */
    fun normalize(s: String, strictAccents: Boolean = false): String {
        var t = Normalizer.normalize(s, Normalizer.Form.NFKC).lowercase()
        if (!strictAccents) t = Normalizer.normalize(t, Normalizer.Form.NFD).filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
        return t.filter { it.isLetterOrDigit() || it.isWhitespace() }.trim().replace(Regex("\\s+"), " ")
    }

    /** CJK text has no spaces between words: compare without spaces when either side contains an ideograph or a kana. */
    private fun squash(s: String) = if (s.any { Character.UnicodeScript.of(it.code) in cjkScripts }) s.replace(" ", "") else s
    private val cjkScripts = setOf(Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA)

    fun matches(response: String, x: LangExercise, strictAccents: Boolean = false): Boolean = when (x.kind) {
        LangExerciseKind.MCQ, LangExerciseKind.TRUEFALSE -> response.trim().toIntOrNull() == x.correct
        LangExerciseKind.MATCH -> false  // matched pair by pair by the screen (pairs are exact)
        LangExerciseKind.SPEAK, LangExerciseKind.WRITE -> false  // self-assessed with the model answer
        else -> x.answers.any { squash(normalize(it, strictAccents)) == squash(normalize(response, strictAccents)) }
    }

    /** Tone marks matter in pinyin: a learner typing « ma » for « mǎ » is close but not right (partial credit decided by the screen). */
    fun sameIgnoringTones(a: String, b: String) = normalize(a) == normalize(b) && a != b
}

/** Leitner boxes for vocabulary (spaced repetition): intervals in days, box 0 = new/failed. Day numbers are caller-provided (local epoch day). */
object Srs {
    val intervals = intArrayOf(0, 1, 3, 7, 16, 35, 90)
    data class Card(val id: String, val box: Int = 0, val dueDay: Int = 0)

    /** [grade]: 0 = failed, 1 = hard (stay), 2 = easy (up one box). */
    fun review(c: Card, grade: Int, today: Int): Card {
        val box = when (grade) { 0 -> 0; 1 -> c.box; else -> minOf(c.box + 1, intervals.lastIndex) }
        return c.copy(box = box, dueDay = today + intervals[box].coerceAtLeast(if (grade == 0) 0 else 1))
    }
    fun due(cards: List<Card>, today: Int, limit: Int = 20): List<Card> = cards.filter { it.dueDay <= today }.sortedWith(compareBy({ it.box }, { it.dueDay }, { it.id })).take(limit)
}

/** Placement test (docs/LANGUES.md § 1.5): staircase from A1 upward, 3 items per level and skill; a level is reached with ≥ 2 of 3 right (≥ 67 %) and every lower level too. */
object Placement {
    data class Answer(val level: LangLevel, val skill: LangSkill, val correct: Boolean)
    data class Result(val bySkill: Map<LangSkill, LangLevel>, val overall: LangLevel)

    const val ITEMS_PER_STEP = 3
    const val PASS = 2

    /** The level to ask next for [skill], or null when the staircase stopped (failed a step) or reached the top. */
    fun nextLevel(answers: List<Answer>, skill: LangSkill): LangLevel? {
        var level = LangLevel.A1
        while (true) {
            val here = answers.filter { it.skill == skill && it.level == level }
            if (here.size < ITEMS_PER_STEP) return level
            if (here.count { it.correct } < PASS) return null
            level = level.next() ?: return null
        }
    }

    fun evaluate(answers: List<Answer>): Result {
        val by = LangSkill.entries.associateWith { s ->
            var reached = LangLevel.A0
            var level: LangLevel? = LangLevel.A1
            while (level != null) {
                val here = answers.filter { it.skill == s && it.level == level }
                if (here.size < ITEMS_PER_STEP || here.count { it.correct } < PASS) break
                reached = level; level = level.next()
            }
            reached
        }
        // Overall = average of the four skills, rounded down: one weak skill does not hide the others, but the profile keeps them apart.
        val avg = by.values.sumOf { it.ordinal } / by.size
        return Result(by, LangLevel.entries[avg])
    }
}

/** LangSkill graph of one language (`graph/langue-<code>.json`): nodes with prerequisites; must be acyclic, prerequisites never above the node's level. */
data class LangSkillNode(val id: String, val level: LangLevel, val skill: LangSkill?, val title: String, val requires: List<String>, val unit: String?)
data class LangSkillGraph(val lang: Lang, val nodes: List<LangSkillNode>) {
    private val byId = nodes.associateBy { it.id }
    fun available(mastered: Set<String>): List<LangSkillNode> = nodes.filter { it.id !in mastered && mastered.containsAll(it.requires) }
    fun errors(): List<String> {
        val e = ArrayList<String>()
        if (byId.size != nodes.size) e += "identifiants de nœuds en double"
        for (n in nodes) for (r in n.requires) {
            val p = byId[r]
            if (p == null) e += "nœud ${n.id} : prérequis « $r » inconnu" else if (p.level > n.level) e += "nœud ${n.id} : prérequis ${p.id} d'un niveau plus haut"
        }
        val state = HashMap<String, Int>()
        fun visit(id: String): Boolean {
            when (state[id]) { 1 -> return true; 2 -> return false }
            state[id] = 1
            val cyc = byId[id]?.requires?.any { byId.containsKey(it) && visit(it) } ?: false
            state[id] = 2; return cyc
        }
        if (nodes.any { visit(it.id) }) e += "cycle dans le graphe"
        return e
    }

    companion object {
        /** Reads `content/graph/langue-<code>.json` (the content-architecture domain format; extra fields `cefr` and `skill`). The N-level of the shared ladder is ignored here: a language is laid out on the CEFR. */
        fun parse(text: String): LangSkillGraph {
            val root = castbridge.core.quiz.Json.obj(text)
            val lang = Lang.of((root["prefix"] as? String)) ?: throw IllegalArgumentException("graphe : langue (prefix) inconnue")
            @Suppress("UNCHECKED_CAST") val raw = root["skills"] as? List<Map<String, Any?>> ?: throw IllegalArgumentException("graphe : skills manquant")
            return LangSkillGraph(lang, raw.map { n ->
                @Suppress("UNCHECKED_CAST") val title = (n["title"] as? Map<String, Any?>)?.get("fr") as? String ?: throw IllegalArgumentException("graphe : titre fr manquant")
                LangSkillNode(n["id"] as String, LangLevel.of(n["cefr"] as? String) ?: throw IllegalArgumentException("graphe : niveau cefr inconnu"), LangSkill.of(n["skill"] as? String),
                    title, (n["prereq"] as? List<*>)?.filterIsInstance<String>().orEmpty(), (n["unit"] as? String))
            })
        }
    }
}
