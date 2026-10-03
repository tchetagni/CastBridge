package castbridge.core

import castbridge.core.content.Channel
import castbridge.core.quiz.*
import castbridge.core.quiz.QuizLevelAvailability.Counts
import castbridge.core.quiz.QuizLevelAvailability.Index
import castbridge.core.quiz.QuizLevelAvailability.Kind
import kotlin.test.*

/**
 * « Quel niveau ? » of CastBridge-TV: every level with content is open (docs/agent-reports/quiz-niveaux.md). The decisions are
 * pure functions (QuizLevelAvailability); the screen only draws them. Review questions are playable, approved ones come first.
 */
class QuizLevelAvailabilityTest {
    // ---- index fixtures: the two forms of embedded/index.json
    private fun lvl(key: String, level: String?, track: String, count: Int) =
        """{"key":"$key","level":${level?.let { "\"$it\"" } ?: "null"},"track":"$track","count":$count,"file":"$key.json","lots":["$key"]}"""
    private fun indexJson(levels: List<String>, reservedNotEmbedded: List<String>, reservedLevels: List<String>? = null): String {
        fun arr(l: List<String>) = l.joinToString(",", "[", "]") { "\"$it\"" }
        return """{"v":1,"seed":"t","levels":[${levels.joinToString(",")}],"reservedNotEmbedded":${arr(reservedNotEmbedded)}""" +
            (reservedLevels?.let { ""","reservedLevels":${arr(it)}""" } ?: "") + "}"
    }
    private val freeLevels = listOf(lvl("cp", "CP", "primary", 2000), lvl("form-3", "Form 3", "secondary", 2000), lvl("form-5", "Form 5", "secondary", 2000),
        lvl("class-1", "Class 1", "primary", 2000), lvl("culture-generale", null, "general", 2000))
    /** Today's form: the reserved family is not embedded. */
    private val indexNow = Index.parse(indexJson(freeLevels, listOf("l1", "l2", "l3", "tle")))
    /** Trial edition: everything embedded, reservedNotEmbedded empty, the family named by reservedLevels. */
    private val trialLevels = freeLevels + listOf(lvl("tle", "Tle", "secondary", 800), lvl("l1", "L1", "higher", 600), lvl("l2", "L2", "higher", 500), lvl("l3", "L3", "higher", 400))
    private val indexTrial = Index.parse(indexJson(trialLevels, emptyList(), listOf("l1", "l2", "l3", "tle")))
    /** Everything embedded and nothing declared reserved. */
    private val indexOpen = Index.parse(indexJson(trialLevels, emptyList()))

    private fun none(f: QuestionFilter) = Counts(0, 0)
    private fun fake(vararg c: Pair<String, Counts>): (QuestionFilter) -> Counts { val m = c.toMap(); return { f -> m[f.level] ?: Counts(0, 0) } }
    private fun state(i: Index, level: String, track: Track, bank: (QuestionFilter) -> Counts = ::none, trial: Boolean = true) =
        QuizLevelAvailability.states(track, i, bank, trial).first { it.level.key == level }
    private fun text(s: QuizLevelAvailability.State) = QuizLevelAvailability.cardText(s, goal = 30)

    // ---- the table of states

    @Test fun baseOnlyCm2StaysAvailableAsToday() {
        val s = state(indexNow, "CM2", Track.PRIMARY, fake("CM2" to Counts(20, 0)))
        assertEquals(Kind.AVAILABLE, s.kind); assertEquals(20, s.counts.total); assertEquals(20, s.counts.approved); assertEquals(0, s.counts.review)
        assertTrue("20 questions" in text(s) && "parties sans répétition (objectif 30)" in text(s), text(s))
        assertFalse(QuizLevelAvailability.REVIEW_NOTE in text(s), "approved content carries no review note")
    }

    @Test fun anEmbeddedLevelIsAvailableWithItsIndexCountAndTheReviewNote() {
        val s = state(indexNow, "CP", Track.PRIMARY)
        assertEquals(Kind.AVAILABLE, s.kind); assertEquals(2000, s.counts.total); assertEquals(0, s.counts.approved); assertEquals(2000, s.counts.review)
        val t = text(s)
        assertTrue("2000 questions" in t && "30 parties sans répétition garanties" in t, t)
        assertTrue(QuizLevelAvailability.REVIEW_NOTE in t, "the note is small but never hidden: $t")
        assertNotEquals(QuizLevelAvailability.SOON_TEXT, t)
    }

    @Test fun mixedContentSaysHowMuchIsUnderReview() {
        val s = state(indexNow, "CM2", Track.PRIMARY, fake("CM2" to Counts(20, 0)))
        val both = state(indexNow, "CP", Track.PRIMARY, fake("CP" to Counts(20, 5)))
        assertEquals(2000, both.counts.total); assertEquals(20, both.counts.approved)
        assertTrue("1980" in text(both) && "en cours de relecture" in text(both), text(both))
        assertEquals(Kind.AVAILABLE, s.kind)
    }

    @Test fun silIsAnAliasOfCpAndSaysSo() {
        val s = state(indexNow, "SIL", Track.PRIMARY)
        assertEquals(Kind.AVAILABLE, s.kind); assertEquals(2000, s.counts.total)
        assertEquals(listOf("CP"), s.alias?.sources)
        assertTrue("mêmes questions que le CP" in text(s), text(s))
    }

    @Test fun silHasNothingWhenCpHasNothing() {
        val idx = Index.parse(indexJson(listOf(lvl("form-3", "Form 3", "secondary", 2000)), listOf("l1")))
        assertEquals(Kind.SOON, state(idx, "SIL", Track.PRIMARY).kind)
    }

    @Test fun form4IsServedFromForm3AndForm5Interleaved() {
        val s = state(indexNow, "Form 4", Track.SECONDARY)
        assertEquals(Kind.AVAILABLE, s.kind)
        assertEquals(listOf("Form 3", "Form 5"), s.alias?.sources)
        assertEquals(2 * QuizLevelAvailability.FORM4_PER_SOURCE, s.counts.total)
        assertTrue("questions des Form 3 et Form 5" in text(s), text(s))
    }

    @Test fun form4NeedsBothSources() {
        val idx = Index.parse(indexJson(listOf(lvl("form-3", "Form 3", "secondary", 2000)), emptyList()))
        assertEquals(1000, state(idx, "Form 4", Track.SECONDARY).counts.total, "only what exists: Form 3's share")
    }

    @Test fun reservedLevelsWithNoContentAreReservedNotSoon() {
        for ((lv, tr) in listOf("L1" to Track.HIGHER, "L2" to Track.HIGHER, "L3" to Track.HIGHER, "Tle" to Track.SECONDARY)) {
            val s = state(indexNow, lv, tr)
            assertEquals(Kind.RESERVED, s.kind, lv)
            assertEquals("Réservé · en location", text(s))
            assertNotEquals("bientôt", text(s))
        }
    }

    @Test fun anInstalledRentalLotOpensAReservedLevel() {
        val s = state(indexNow, "Tle", Track.SECONDARY, fake("Tle" to Counts(0, 40)))
        assertEquals(Kind.AVAILABLE, s.kind); assertEquals(40, s.counts.total)
        assertFalse(s.trial, "the content comes from a rental, not from the trial flag")
        assertFalse(QuizLevelAvailability.TRIAL_NOTE in text(s))
    }

    @Test fun aTrulyUnknownLevelIsSoon() {
        val odd = QuizCatalog.Level("Zz", "Zz", Track.PRIMARY)
        val s = QuizLevelAvailability.states(listOf(odd), indexNow, ::none).single()
        assertEquals(Kind.SOON, s.kind); assertEquals("bientôt", text(s))
    }

    @Test fun trialFlagOpensTheReservedFamilyWhenItsContentIsBundledAndSaysEssai() {
        for ((lv, tr, n) in listOf(Triple("Tle", Track.SECONDARY, 800), Triple("L1", Track.HIGHER, 600), Triple("L3", Track.HIGHER, 400))) {
            val s = state(indexTrial, lv, tr)
            assertEquals(Kind.AVAILABLE, s.kind, lv); assertEquals(n, s.counts.total); assertTrue(s.trial)
            assertTrue(QuizLevelAvailability.TRIAL_NOTE in text(s), text(s))
        }
        // production edition: the flag is off, the bundled content of a reserved level is ignored
        val prod = state(indexTrial, "Tle", Track.SECONDARY, trial = false)
        assertEquals(Kind.RESERVED, prod.kind); assertEquals("Réservé · en location", text(prod))
    }

    @Test fun anIndexWhereNothingIsReservedWorksToo() {
        assertEquals(Kind.AVAILABLE, state(indexOpen, "Tle", Track.SECONDARY).kind)
        assertFalse(state(indexOpen, "Tle", Track.SECONDARY).trial, "not reserved: no « essai » note")
        val empty = Index.parse(indexJson(freeLevels, emptyList()))
        assertEquals(Kind.SOON, state(empty, "Tle", Track.SECONDARY).kind, "no content and not reserved = bientôt")
    }

    @Test fun theReservedListIsReadFromTheIndexNeverHardCoded() {
        val idx = Index.parse(indexJson(freeLevels, listOf("cp")))
        assertEquals(setOf("cp"), idx.reserved)
        assertEquals(setOf("l1", "tle"), Index.parse(indexJson(freeLevels, listOf("tle"), listOf("l1", "tle"))).reserved, "reservedLevels wins when present")
        assertEquals(emptySet(), Index.parse(null).reserved); assertEquals(emptySet(), Index.parse("{broken").reserved)
    }

    @Test fun fieldStepKeepsTheSameStates() {
        val counts: (String) -> Counts = { f -> if (f == "droit") Counts(0, 120) else Counts(0, 0) }
        val trial = QuizLevelAvailability.fieldStates(Track.HIGHER, "L1", indexTrial, counts, trialOpen = true)
        assertEquals(QuizCatalog.fields.size, trial.size)
        assertEquals(Kind.AVAILABLE, trial.first { it.level.key == "droit" }.kind)
        assertEquals(Kind.RESERVED, trial.first { it.level.key == "physique" }.kind, "level reserved by the family, field without content")
        val soon = QuizLevelAvailability.fieldStates(Track.HIGHER, "L1", indexOpen, counts, trialOpen = true)
        assertEquals(Kind.SOON, soon.first { it.level.key == "physique" }.kind)
        assertTrue(text(trial.first { it.level.key == "droit" }).contains("120 questions"))
    }

    // ---- the real catalogue, the real index, the real base bank

    private val realIndex = Index.bundled()
    private val baseBank = EmbeddedQuestionSource().bank()
    private fun realBase(f: QuestionFilter) = QuizLevelAvailability.countsOf(baseBank, f)

    @Test fun noLevelOfAnyTrackShowsBientot() {
        for (t in listOf(Track.PRIMARY, Track.SECONDARY, Track.HIGHER)) for (trial in listOf(true, false)) {
            val ss = QuizLevelAvailability.states(t, realIndex, ::realBase, trial)
            assertEquals(QuizCatalog.levels(t).map { it.key }, ss.map { it.level.key })
            for (s in ss) {
                assertNotEquals(Kind.SOON, s.kind, "${s.level.key} must not be « bientôt »")
                assertNotEquals("bientôt", text(s), s.level.key)
            }
        }
    }

    @Test fun everyFreeLevelIsAvailableWithItsFullCount() {
        for (t in listOf(Track.PRIMARY, Track.SECONDARY)) for (s in QuizLevelAvailability.states(t, realIndex, ::realBase, QuizEdition.TRIAL_OPEN)) {
            if (s.level.key in setOf("Tle")) continue
            assertEquals(Kind.AVAILABLE, s.kind, s.level.key)
            assertTrue(s.counts.total >= 1000, "${s.level.key}: ${s.counts}")
        }
    }

    @Test fun cm2KeepsItsBaseQuestionsAndGainsTheEmbeddedOnes() {
        val s = state(realIndex, "CM2", Track.PRIMARY, ::realBase)
        assertEquals(Kind.AVAILABLE, s.kind); assertTrue(s.counts.total >= 2000); assertTrue(s.counts.approved >= 10)
    }

    // ---- level key mapping

    private val pickerKeys = QuizCatalog.levels.map { it.key }

    @Test fun everyEmbeddedLevelMapsToExactlyOnePickerLevelAndConversely() {
        val levels = EmbeddedLevels().levels
        for (l in levels.filter { it.level != null }) {
            val owners = QuizCatalog.levels.filter { it.key == l.level && it.track == l.track }
            assertEquals(1, owners.size, "${l.key} -> ${l.level}")
            assertEquals(l.key, QuizLevelAvailability.EMBEDDED_KEYS[owners.single().key], "table says ${l.key} for ${owners.single().key}")
        }
        assertEquals(listOf("culture-generale"), levels.filter { it.level == null }.map { it.key }, "only general knowledge has no picker level")
        // the table: every picker level is a table entry or an alias, nothing twice, nothing foreign
        val aliasLevels = QuizLevelAvailability.aliases.map { it.level }
        assertEquals(pickerKeys.toSet(), QuizLevelAvailability.EMBEDDED_KEYS.keys + aliasLevels)
        assertTrue(QuizLevelAvailability.EMBEDDED_KEYS.keys.none { it in aliasLevels })
        assertEquals(QuizLevelAvailability.EMBEDDED_KEYS.size, QuizLevelAvailability.EMBEDDED_KEYS.values.toSet().size, "one embedded key per picker level")
        assertEquals(setOf("SIL", "Form 4"), aliasLevels.toSet())
        // what the game draws from agrees with the table
        for (l in QuizCatalog.levels) QuizLevelAvailability.EMBEDDED_KEYS[l.key]?.let { key ->
            EmbeddedLevels().levelFor(QuestionFilter(l.track, l.key))?.let { assertEquals(key, it.key, l.key) }
        }
        // the levels absent from the index are the aliases and the reserved family, nothing else
        val inIndex = levels.mapNotNull { it.level }.toSet()
        val reserved = realIndex.reserved
        for (k in pickerKeys.filter { it !in inIndex }) assertTrue(k in aliasLevels || k.lowercase() in reserved, "$k is neither embedded, nor an alias, nor reserved")
    }

    // ---- aliases at the source

    @Test fun composeRetagsAndInterleavesDeterministically() {
        fun q(id: String, level: String) = Question(id, Region.CM, "c", 1, "Q $id", listOf("a", "b", "c", "d"), 0, "e", "s", review = true, track = Track.SECONDARY, level = level, status = "review")
        val three = (1..6).map { q("t$it", "Form 3") }; val five = (1..6).map { q("f$it", "Form 5") }
        val alias = QuizLevelAvailability.aliases.first { it.level == "Form 4" }
        val small = alias.copy(perSource = 3)
        val out = QuizLevelAvailability.compose(small) { name -> if (name == "Form 3") three else five }
        assertEquals(6, out.size); assertTrue(out.all { it.level == "Form 4" && it.track == Track.SECONDARY })
        assertEquals(6, out.map { it.id }.toSet().size)
        assertTrue(out.none { it.id in (three + five).map { x -> x.id } }, "ids differ from the real levels, so an installed lot never replaces them")
        assertEquals(listOf("Form 3", "Form 5", "Form 3", "Form 5", "Form 3", "Form 5"), out.map { if (it.id.startsWith("t")) "Form 3" else "Form 5" }.map { it })
        assertEquals(out.map { it.id }, QuizLevelAvailability.compose(small) { name -> if (name == "Form 3") three else five }.map { it.id }, "deterministic")
        assertEquals(Question::class, out.first()::class)
        assertEquals(emptyList(), QuizLevelAvailability.compose(small) { emptyList() })
    }

    @Test fun silAndForm4AreDrawnFromRealQuestionsOneLevelAtATime() {
        val reads = ArrayList<String>()
        val src = EmbeddedQuestionSource(levels = EmbeddedLevels(reader = { p -> reads += p.substringAfterLast('/'); EmbeddedLevels::class.java.getResourceAsStream(p)?.use { it.readBytes() } }))
        val sil = QuestionFilter(Track.PRIMARY, "SIL")
        val bs = src.bankFor(sil).forChannel(Channel.BETA)
        assertTrue(bs.count(sil) >= 2000, "SIL = the CP content")
        assertEquals(listOf("index.json", "cp.json"), reads.toList(), "SIL reads the CP file only")
        val d = bs.draw(15, seed = 3, filter = sil)
        assertEquals(15, d.size); assertEquals(15, d.map { it.id }.toSet().size); assertTrue(d.all { it.level == "SIL" })
        reads.clear()
        val f4 = QuestionFilter(Track.SECONDARY, "Form 4")
        val b4 = src.bankFor(f4).forChannel(Channel.BETA)
        assertEquals(listOf("form-3.json", "form-5.json"), reads.toList(), "Form 4 reads its two sources one after the other")
        assertEquals(2 * QuizLevelAvailability.FORM4_PER_SOURCE, b4.count(f4))
        val d4 = b4.draw(15, seed = 4, filter = f4)
        assertEquals(15, d4.map { it.id }.toSet().size)
        reads.clear(); src.bankFor(f4); assertTrue(reads.isEmpty(), "the same level again is not re-read")
    }

    // ---- counting and drawing

    private fun q(id: String, review: Boolean, d: Int = 1 + id.hashCode().mod(5), status: String? = if (review) "review" else "approved", level: String = "CM2") =
        Question(id, Region.CM, "c", d, "Question $id ?", listOf("a$id", "b$id", "c$id", "d$id"), 0, "e", "s", review = review, track = Track.PRIMARY, level = level, status = status)

    @Test fun countOfThePickerIncludesReviewButNeverRejected() {
        val bank = QuizBank(listOf(q("a1", false), q("a2", false), q("r1", true), q("r2", true), q("r3", true), q("x", true, status = "rejected"), q("n", true, status = "needs-fix")))
        val f = QuestionFilter(Track.PRIMARY, "CM2")
        assertEquals(2, bank.count(f), "the strict count is unchanged")
        assertEquals(Counts(2, 3), QuizLevelAvailability.countsOf(bank, f))
        assertEquals(5, bank.forChannel(Channel.BETA).count(f), "a beta bank plays review but not rejected")
        assertEquals(Counts(0, 0), QuizLevelAvailability.countsOf(bank, QuestionFilter(Track.PRIMARY, "CP")))
    }

    @Test fun approvedQuestionsAreDrawnBeforeReviewOnes() {
        val f = QuestionFilter(Track.PRIMARY, "CM2")
        val enough = QuizBank((1..20).map { q("a$it", false) } + (1..100).map { q("r$it", true) }, Channel.BETA)
        for (seed in 1L..5L) assertTrue(enough.draw(15, seed, filter = f).all { !it.review }, "20 approved are enough for 15: no review question drawn")
        val few = QuizBank((1..10).map { q("a$it", false) } + (1..100).map { q("r$it", true) }, Channel.BETA)
        for (seed in 1L..5L) {
            val d = few.draw(15, seed, filter = f)
            assertEquals(15, d.size); assertEquals(10, d.count { !it.review }, "every approved question first"); assertEquals(5, d.count { it.review })
        }
        val onlyReview = QuizBank((1..30).map { q("r$it", true) }, Channel.BETA)
        assertEquals(15, onlyReview.draw(15, 1, filter = f).size)
    }

    @Test fun theTvPlaysReviewQuestionsWhateverTheServerChannel() {
        assertTrue(QuizEdition.REVIEW_PLAYABLE)
        assertEquals(Channel.BETA, QuizEdition.playChannel(Channel.STABLE))
        assertEquals(Channel.BETA, QuizEdition.playChannel(Channel.BETA))
    }

    @Test fun trialFlagIsOnForTheTrialEdition() { assertTrue(QuizEdition.TRIAL_OPEN) }

    @Test fun aRoomDrawsRealLevelsAfterThePickerWithoutRepeating() {
        val src = EmbeddedQuestionSource()
        val filters = listOf(QuestionFilter(Track.PRIMARY, "CP"), QuestionFilter(Track.PRIMARY, "Class 1"), QuestionFilter(Track.SECONDARY, "6e"),
            QuestionFilter(Track.SECONDARY, "Form 1"), QuestionFilter(Track.SECONDARY, "Lower Sixth"), QuestionFilter.GENERAL,
            QuestionFilter(Track.PRIMARY, "SIL"), QuestionFilter(Track.SECONDARY, "Form 4"))
        for (f in filters) {
            // the source and the room as QuizHub builds them: the stable server channel still plays review questions
            val ch = QuizEdition.playChannel(Channel.STABLE)
            val room = QuizRoom(src.bank().forChannel(ch), autoTick = false, bankFor = { x -> src.bankFor(x).forChannel(ch) })
            assertTrue(room.configure(QuizRoom.Mode.MILLIONAIRE, QuizRoom.Play.FRIENDS, f), f.label)
            assertTrue(room.available() >= 1000, "${f.label}: ${room.available()}")
            assertEquals(room.available(), room.playableCount(f))
            assertNull(room.startGame(seed = 11), f.label)
            val ids = room.game!!.questions.map { it.id }
            assertEquals(15, ids.size, f.label); assertEquals(15, ids.toSet().size, "${f.label}: no repetition")
            room.close()
        }
    }
}
