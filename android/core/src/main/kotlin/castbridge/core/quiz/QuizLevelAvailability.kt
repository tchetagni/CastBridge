package castbridge.core.quiz

import castbridge.core.content.Channel
import castbridge.core.content.ContentState
import castbridge.core.content.PlayPolicy

/** Edition flags of the Quiz on CastBridge-TV: one line to change for a production edition. */
object QuizEdition {
    /**
     * Trial edition (owner, 2026-10-03: « ce sont les questions qui sont réservables, pas les niveaux »): every level has FREE questions
     * and RESERVABLE ones (about 30 %, marked one by one, `reservedCount` in embedded/index.json). True = the reservable questions are playable
     * during the trial and counted on the card (« dont N questions en essai »). False (production) = only the free ones (`freeCount`).
     */
    const val TRIAL_OPEN = true

    /**
     * Owner decision (2026-10-03): questions « en cours de relecture » ARE played, so that every level is filled. Approved questions are
     * drawn first ([QuizBank.drawDetailed]); a rejected or needs-fix question is never played. The marks stay visible ([PlayPolicy.mark]).
     */
    const val REVIEW_PLAYABLE = true

    /** The channel the TV plays on: the server's, except that review questions are playable (see [REVIEW_PLAYABLE]). */
    fun playChannel(server: Channel): Channel = if (REVIEW_PLAYABLE) Channel.BETA else server
}

/**
 * What « Quel niveau ? » / « Quelle filière ? » of CastBridge-TV shows for each level, as pure functions (the screen only draws them).
 * The state depends ONLY on the content really present on this TV: the base bank, the bundled index (index.json only: a level file is
 * never read just to count it) and the installed lots.
 *
 * - AVAILABLE: at least one playable question (the free ones, plus the reservable ones while [QuizEdition.TRIAL_OPEN]).
 * - RESERVED: (rare) every question of the level is reservable and the trial flag is off: « Réservé · en location ».
 * - SOON: nothing at all. No level is reserved as a whole.
 * - Aliases: « SIL » plays the CP questions; « Form 4 » (no lot, no question of its own) plays Form 3 and Form 5 interleaved.
 */
object QuizLevelAvailability {
    enum class Kind { AVAILABLE, RESERVED, SOON }

    data class Counts(val approved: Int, val review: Int) { val total: Int get() = approved + review }

    /** A level without content of its own, served from [sources] (at most [perSource] questions of each, null = all). */
    data class Alias(val level: String, val track: Track, val sources: List<String>, val perSource: Int?, val note: String)

    /** [level] is the catalogue level (for a field: a pseudo level carrying the field's key and label). [trialCount] = reservable questions counted only because of the trial flag. */
    data class State(val level: QuizCatalog.Level, val kind: Kind, val counts: Counts, val alias: Alias? = null, val trialCount: Int = 0)

    /** One level of the index: total, free and reservable questions (an index without the split: everything is free). */
    data class Entry(val count: Int, val free: Int, val reservable: Int)

    /** The bundled index, read without touching any level file. */
    class Index(val levels: List<EmbeddedLevels.Level>, val entries: Map<String, Entry>) {
        fun entry(key: String): Entry = entries[key] ?: Entry(0, 0, 0)

        companion object {
            /** Reads index.json (`count`, and `freeCount` / `reservedCount` when present); a missing or broken index = nothing bundled. */
            fun parse(json: String?): Index {
                if (json == null) return Index(emptyList(), emptyMap())
                val root = runCatching { Json.obj(json) }.getOrNull() ?: return Index(emptyList(), emptyMap())
                val levels = EmbeddedLevels(reader = { json.toByteArray(Charsets.UTF_8) }).levels
                val entries = HashMap<String, Entry>()
                for (o in root["levels"] as? List<*> ?: emptyList<Any?>()) {
                    val m = o as? Map<*, *> ?: continue
                    val key = m["key"] as? String ?: continue
                    val count = (m["count"] as? Number)?.toInt() ?: 0
                    val reservable = ((m["reservedCount"] as? Number)?.toInt() ?: 0).coerceIn(0, count)
                    val free = ((m["freeCount"] as? Number)?.toInt() ?: (count - reservable)).coerceIn(0, count)
                    entries[key] = Entry(count, free, reservable)
                }
                return Index(levels, entries)
            }

            /** The index bundled in the APK. */
            fun bundled(): Index = parse(EmbeddedLevels::class.java.getResourceAsStream("/castbridge/quiz/embedded/index.json")?.use { String(it.readBytes(), Charsets.UTF_8) })
        }
    }

    const val RESERVED_TEXT = "Réservé · en location"
    const val SOON_TEXT = "bientôt"
    const val REVIEW_NOTE = "contenu en cours de relecture"
    /** Form 4 takes this many questions from each of Form 3 and Form 5 (a level is about 2000 questions, one level in memory at a time). */
    const val FORM4_PER_SOURCE = 1000

    val aliases: List<Alias> = listOf(
        Alias("SIL", Track.PRIMARY, listOf("CP"), null, "mêmes questions que le CP"),
        Alias("Form 4", Track.SECONDARY, listOf("Form 3", "Form 5"), FORM4_PER_SOURCE, "questions des Form 3 et Form 5"),
    )

    /**
     * Picker level key -> key of its bundled file in index.json (aliases excluded). Tle, L1, L2, L3 are listed for when their
     * content is bundled; the test checks the table against the real index.
     */
    val EMBEDDED_KEYS: Map<String, String> = linkedMapOf(
        "CP" to "cp", "CE1" to "ce1", "CE2" to "ce2", "CM1" to "cm1", "CM2" to "cm2",
        "Class 1" to "class-1", "Class 2" to "class-2", "Class 3" to "class-3", "Class 4" to "class-4", "Class 5" to "class-5", "Class 6" to "class-6",
        "6e" to "6e", "5e" to "5e", "4e" to "4e", "3e" to "3e", "2nde" to "2nde", "1re" to "1re", "Tle" to "tle",
        "Form 1" to "form-1", "Form 2" to "form-2", "Form 3" to "form-3", "Form 5" to "form-5",
        "Lower Sixth" to "lower-sixth", "Upper Sixth" to "upper-sixth",
        "L1" to "l1", "L2" to "l2", "L3" to "l3",
    )

    /** The alias a game filter is served through (a field never is). */
    fun aliasFor(f: QuestionFilter): Alias? = if (f.field != null) null else aliases.firstOrNull { it.level == f.level && it.track == f.track }

    /** Playable content of [f] in [bank] (what the picker shows: approved, and under review; rejected and needs-fix are never counted). */
    fun countsOf(bank: QuizBank, f: QuestionFilter): Counts {
        var a = 0; var r = 0
        for (q in bank.all) if (f.matches(q)) when (PlayPolicy.stateOf(q)) {
            ContentState.VALIDATED -> a++
            ContentState.REVIEW -> r++
            else -> {}
        }
        return Counts(a, r)
    }

    /** Content of one real level: the bank (base + installed lots) and the bundled file, as far as the index tells. [Pair.second] = trial-only questions. */
    private fun levelCounts(level: QuizCatalog.Level, index: Index, bank: (QuestionFilter) -> Counts, trialOpen: Boolean): Pair<Counts, Int> {
        val b = bank(QuestionFilter(level.track, level.key))
        val e = EMBEDDED_KEYS[level.key]?.let { index.entry(it) } ?: Entry(0, 0, 0)
        val embedded = e.free + if (trialOpen) e.reservable else 0
        // an installed lot of the same level replaces the bundled questions of the same id: not added, the larger one counts
        val total = maxOf(b.total, embedded)
        val approved = minOf(b.approved, total)
        return Counts(approved, total - approved) to (if (trialOpen && embedded >= b.total) e.reservable else 0)
    }

    /** Only reservable questions and the trial closed: the level cannot be played without a rental. */
    private fun onlyReservable(level: QuizCatalog.Level, index: Index, trialOpen: Boolean): Boolean {
        val e = EMBEDDED_KEYS[level.key]?.let { index.entry(it) } ?: return false
        return !trialOpen && e.free == 0 && e.reservable > 0
    }

    /** The state of each level of [levels] (the catalogue's levels of a track). */
    fun states(levels: List<QuizCatalog.Level>, index: Index, bank: (QuestionFilter) -> Counts, trialOpen: Boolean = QuizEdition.TRIAL_OPEN): List<State> =
        levels.map { l ->
            val alias = aliases.firstOrNull { it.level == l.key && it.track == l.track }
            if (alias != null) {
                val parts = alias.sources.map { s ->
                    val sl = QuizCatalog.levels.firstOrNull { it.key == s && it.track == l.track } ?: return@map Counts(0, 0) to 0
                    val (c, t) = levelCounts(sl, index, bank, trialOpen)
                    val cap = alias.perSource ?: Int.MAX_VALUE
                    if (c.total <= cap) c to t else Counts(minOf(c.approved, cap), cap - minOf(c.approved, cap)) to minOf(t, cap)
                }
                val c = Counts(parts.sumOf { it.first.approved }, parts.sumOf { it.first.review })
                State(l, if (c.total > 0) Kind.AVAILABLE else Kind.SOON, c, alias, parts.sumOf { it.second })
            } else {
                val (c, trial) = levelCounts(l, index, bank, trialOpen)
                State(l, kindOf(c.total, onlyReservable(l, index, trialOpen)), c, trialCount = trial)
            }
        }

    fun states(track: Track, index: Index, bank: (QuestionFilter) -> Counts, trialOpen: Boolean = QuizEdition.TRIAL_OPEN): List<State> =
        states(QuizCatalog.levels(track), index, bank, trialOpen)

    /** The « Quelle filière ? » step: [count] gives the playable content of one field (the level is chosen, so its file may be read). */
    fun fieldStates(track: Track, count: (String) -> Counts): List<State> =
        QuizCatalog.fields.map { f ->
            val c = count(f.key)
            State(QuizCatalog.Level(f.key, f.label, track), kindOf(c.total, false), c)
        }

    private fun kindOf(total: Int, onlyReservable: Boolean) = when {
        total > 0 -> Kind.AVAILABLE
        onlyReservable -> Kind.RESERVED
        else -> Kind.SOON
    }

    /**
     * The long text of a level: « N questions · ≈ P parties sans répétition (objectif G) » (or « G parties sans répétition garanties »)
     * then the notes (alias, « dont N questions en essai », relecture), each on its own line; « Réservé · en location » or « bientôt »
     * without content. P is the capacity of the pool for [perGame] questions a game (the player's history is not read: the picker never loads a level).
     */
    fun cardText(s: State, goal: Int, perGame: Int = 15): String = when (s.kind) {
        Kind.RESERVED -> RESERVED_TEXT
        Kind.SOON -> SOON_TEXT
        Kind.AVAILABLE -> {
            val n = s.counts.total
            val games = n / perGame
            val head = if (games >= goal) "$n questions · $goal parties sans répétition garanties" else "$n questions · ≈ $games parties sans répétition (objectif $goal)"
            val review = when {
                s.counts.review == 0 -> null
                s.counts.approved == 0 -> REVIEW_NOTE
                else -> "dont ${s.counts.review} en cours de relecture"
            }
            listOfNotNull(head, s.alias?.note, if (s.trialCount > 0) "dont ${s.trialCount} questions en essai" else null, review).joinToString("\n")
        }
    }

    /** Thousands grouped with a no-break space: « 2 000 ». */
    private fun readable(n: Int): String = n.toString().reversed().chunked(3).joinToString(" ").reversed()

    /** The ONE short line of a fixed-width card: « 2 000 questions », « Réservé » or « bientôt » (the long text is [detailText]). */
    fun compactText(s: State): String = when (s.kind) {
        Kind.RESERVED -> "Réservé"
        Kind.SOON -> SOON_TEXT
        Kind.AVAILABLE -> if (s.counts.total == 1) "1 question" else "${readable(s.counts.total)} questions"
    }

    /** The single detail line under the grid, following the focused card: « CP : 2000 questions · … · contenu en cours de relecture ». */
    fun detailText(s: State, goal: Int, perGame: Int = 15): String = s.level.label + " : " + cardText(s, goal, perGame).replace("\n", " · ")

    /**
     * The questions of an alias level, built from its sources one after the other (the previous source is released before the next
     * is read: [supply] returns one source level's questions): at most [Alias.perSource] of each, evenly spread over the ids,
     * interleaved source by source, retagged with the alias level and a distinct id (so an installed lot of a source never replaces them).
     * Deterministic: the same sources give the same questions in the same order.
     */
    fun compose(alias: Alias, supply: (String) -> List<Question>): List<Question> {
        val kept = alias.sources.map { s ->
            val all = supply(s).sortedBy { it.id }
            val cap = alias.perSource?.let { minOf(it, all.size) } ?: all.size
            List(cap) { i -> all[(i.toLong() * all.size / maxOf(cap, 1)).toInt()] }
        }
        val out = ArrayList<Question>(kept.sumOf { it.size })
        for (i in 0 until (kept.maxOfOrNull { it.size } ?: 0)) for (k in kept) k.getOrNull(i)?.let {
            out += it.copy(id = it.id + "@" + alias.level.replace(' ', '-'), track = alias.track, level = alias.level)
        }
        return out
    }
}
