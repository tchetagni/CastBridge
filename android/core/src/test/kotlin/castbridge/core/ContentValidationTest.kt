package castbridge.core

import castbridge.core.content.Channel
import castbridge.core.content.ContentHash
import castbridge.core.content.ContentKind
import castbridge.core.content.ContentReport
import castbridge.core.content.ContentState
import castbridge.core.content.ItemStat
import castbridge.core.content.ItemStatsCollector
import castbridge.core.content.PlayPolicy
import castbridge.core.content.QualitySignals
import castbridge.core.content.ReportQueue
import castbridge.core.content.ReportReason
import castbridge.core.content.ValidationLedger
import castbridge.core.content.ValidationRecord
import castbridge.core.learn.Exercise
import castbridge.core.learn.ExerciseKind
import castbridge.core.learn.Lesson
import castbridge.core.learn.LessonJson
import castbridge.core.learn.ReviewStatus
import castbridge.core.quiz.Question
import castbridge.core.quiz.QuizBank
import castbridge.core.quiz.QuestionFilter
import castbridge.core.quiz.Region
import castbridge.core.quiz.Track
import castbridge.core.telemetry.Consent
import castbridge.core.telemetry.EventQueue
import castbridge.core.telemetry.Telemetry
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContentValidationTest {
    private fun q(id: String, review: Boolean = false, status: String? = null, verif: String? = null) = Question(id, Region.CM, "Géo", 2,
        "Capitale du Cameroun ?", listOf("Douala", "Yaoundé", "Garoua", "Bafoussam"), 1, "Yaoundé est la capitale politique.", "src",
        review = review, status = status, verif = verif)

    private fun tmp() = File.createTempFile("cbt", ".jsonl").also { it.delete(); it.deleteOnExit() }

    // ------------------------------------------------------------------ policy

    @Test fun policyMatrix() {
        val m = mapOf(
            ContentState.VALIDATED to (true to true), ContentState.REVIEW to (false to true),
            ContentState.NEEDS_FIX to (false to false), ContentState.REJECTED to (false to false))
        for ((s, e) in m) {
            assertEquals(e.first, PlayPolicy.isPlayable(s, Channel.STABLE), "stable $s")
            assertEquals(e.second, PlayPolicy.isPlayable(s, Channel.BETA), "beta $s")
        }
        assertEquals("bêta : non validé", PlayPolicy.mark(ContentState.REVIEW, Channel.BETA))
        assertNull(PlayPolicy.mark(ContentState.REVIEW, Channel.STABLE))
        assertNull(PlayPolicy.mark(ContentState.VALIDATED, Channel.BETA))
    }

    @Test fun channelComesFromTheServerKey() {
        assertEquals(Channel.BETA, Channel.of("beta")); assertEquals(Channel.BETA, Channel.of(" BETA "))
        assertEquals(Channel.STABLE, Channel.of("stable")); assertEquals(Channel.STABLE, Channel.of(null)); assertEquals(Channel.STABLE, Channel.of("nightly"))
    }

    @Test fun quizBankFollowsTheChannelAndStableIsTheCurrentBehaviour() {
        val bank = QuizBank(listOf(q("ok"), q("rev", review = true), q("bad", review = true, status = "rejected"), q("fix", status = "needs-fix"),
            q("comp", verif = "computed")))
        assertEquals(setOf("ok", "comp"), bank.playable.map { it.id }.toSet())               // default = stable = as before
        val beta = bank.forChannel(Channel.BETA)
        assertEquals(setOf("ok", "rev", "comp"), beta.playable.map { it.id }.toSet())
        assertEquals(Channel.BETA, beta.merge(QuizBank(emptyList())).channel)
        assertEquals("bêta : non validé", PlayPolicy.mark(q("rev", review = true), Channel.BETA))
        assertEquals("bêta : non validé", PlayPolicy.mark(q("comp", verif = "computed"), Channel.BETA))   // computed, never read by a human
        assertNull(PlayPolicy.mark(q("ok"), Channel.BETA))
    }

    @Test fun quizParseKeepsTheStatusAndBlocksRejectedAndNeedsFix() {
        fun row(id: String, extra: String) = """{"id":"$id","region":"CM","category":"c","difficulty":2,"question":"Q $id ?","choices":["a","b","c","d"],"answer":0,"explanation":"e","source":"s"$extra}"""
        val json = """{"version":2,"questions":[${row("a", ",\"status\":\"approved\"")},${row("b", ",\"status\":\"review\"")},${row("c", ",\"status\":\"needs-fix\"")},${row("d", ",\"status\":\"rejected\",\"verif\":\"computed\"")},${row("e", ",\"status\":\"review\",\"verif\":\"computed\"")}]}"""
        val bank = QuizBank.parse(json, computedPlayable = true)
        assertEquals(listOf(ContentState.VALIDATED, ContentState.REVIEW, ContentState.NEEDS_FIX, ContentState.REJECTED, ContentState.VALIDATED), bank.all.map { PlayPolicy.stateOf(it) })
        assertEquals(setOf("a", "e"), bank.playable.map { it.id }.toSet())
        assertEquals(setOf("a", "b", "e"), bank.forChannel(Channel.BETA).playable.map { it.id }.toSet())
    }

    @Test fun learnPolicy() {
        val ex = Exercise("e", "c", ExerciseKind.MCQ, "p")
        val exReview = ex.copy(review = true)
        assertEquals(ContentState.VALIDATED, PlayPolicy.stateOf(ex)); assertEquals(ContentState.REVIEW, PlayPolicy.stateOf(exReview))
        assertEquals(ContentState.REJECTED, PlayPolicy.stateOf(ex.copy(state = ContentState.REJECTED)))
        val l = Lesson("l", "c", "t", emptyList())
        assertEquals(ContentState.REVIEW, PlayPolicy.stateOf(l)); assertEquals(ContentState.VALIDATED, PlayPolicy.stateOf(l.copy(status = ReviewStatus.VALIDATED)))
        assertEquals(ContentState.NEEDS_FIX, PlayPolicy.stateOf(l.copy(status = ReviewStatus.VALIDATED, state = ContentState.NEEDS_FIX)))
        // stable today still shows content under review (legacy, with its « à vérifier » mark); strict stable hides it
        assertTrue(PlayPolicy.isVisible(l, Channel.STABLE)); assertFalse(PlayPolicy.isVisible(l, Channel.STABLE, legacyStable = false))
        assertTrue(PlayPolicy.isVisible(l, Channel.BETA)); assertEquals("bêta : non validé", PlayPolicy.mark(l, Channel.BETA))
        assertFalse(PlayPolicy.isVisible(ex.copy(state = ContentState.REJECTED), Channel.BETA)); assertFalse(PlayPolicy.isVisible(ex.copy(state = ContentState.NEEDS_FIX), Channel.STABLE))
    }

    @Test fun learnSourcesReadTheState() {
        val pack = LessonJson.parsePack(mapOf(
            "pack.json" to """{"format":1,"id":"p","version":1,"title":"P","lang":"fr","cursus":"fr","level":"CM2","subject":"maths","chapters":[{"id":"c","title":"C"}]}""",
            "lessons/a.json" to """{"chapter":"c","lessons":[{"id":"l","title":"L","state":"needs-fix","blocks":[]}],"exercises":[{"id":"e","kind":"truefalse","prompt":"p","answer":true,"state":"rejected"}]}"""))
        assertEquals(ContentState.NEEDS_FIX, pack.lessons[0].state); assertEquals(ContentState.REJECTED, pack.exercises[0].state)
    }

    // ------------------------------------------------------------------ lifecycle and ledger

    @Test fun lifecycleTransitions() {
        for (s in ContentState.values()) assertTrue(s.canMoveTo(s))
        assertTrue(ContentState.REVIEW.canMoveTo(ContentState.VALIDATED)); assertTrue(ContentState.REVIEW.canMoveTo(ContentState.REJECTED)); assertTrue(ContentState.REVIEW.canMoveTo(ContentState.NEEDS_FIX))
        assertTrue(ContentState.NEEDS_FIX.canMoveTo(ContentState.REVIEW)); assertFalse(ContentState.NEEDS_FIX.canMoveTo(ContentState.VALIDATED))
        assertFalse(ContentState.REJECTED.canMoveTo(ContentState.VALIDATED)); assertTrue(ContentState.REJECTED.canMoveTo(ContentState.NEEDS_FIX))
        assertTrue(ContentState.VALIDATED.canMoveTo(ContentState.NEEDS_FIX))
        assertEquals(ContentState.VALIDATED, ContentState.of("approved")); assertEquals(ContentState.REVIEW, ContentState.of("beta")); assertNull(ContentState.of("x"))
    }

    private val h1 = "4aba34d897c8b24d"
    private fun rec(id: String, st: ContentState, hash: String? = null, note: String = "", date: String = "2026-10-01") =
        ValidationRecord(id, ContentKind.QUESTION, st, "M. Dupont", date, note, hash)

    @Test fun ledgerAppliesRecordsAndRefusesBadOnes() {
        val l = ValidationLedger()
        assertFalse(l.add(rec("a", ContentState.VALIDATED)))                       // no hash
        assertFalse(l.add(rec("a", ContentState.REJECTED)))                        // no note
        assertTrue(l.add(rec("a", ContentState.REJECTED, note = "réponse fausse")))
        assertFalse(l.add(rec("a", ContentState.VALIDATED, h1)))                   // rejected -> validated is not a move
        assertTrue(l.add(rec("a", ContentState.NEEDS_FIX, note = "rouvert")))
        assertTrue(l.add(rec("a", ContentState.REVIEW)))
        assertTrue(l.add(rec("a", ContentState.VALIDATED, h1)))
        assertEquals(3, l.errors.size)
        assertEquals(ContentState.VALIDATED, l.effective("a", h1).state)
        assertEquals(ContentState.REVIEW, l.effective("unknown", h1).state)
        assertFalse(l.add(rec("b", ContentState.REVIEW, date = "01/10/2026")))
    }

    @Test fun aDecisionOnAnotherHashIsStale() {
        val l = ValidationLedger(listOf(rec("a", ContentState.VALIDATED, h1)))
        val e = l.effective("a", "0000000000000000")
        assertEquals(ContentState.REVIEW, e.state); assertTrue(e.stale)
        assertEquals(ContentState.VALIDATED, l.effective("a", null).state)         // hash unknown: decision trusted
    }

    @Test fun recordsRoundTripAsJsonLines() {
        val r = rec("a", ContentState.NEEDS_FIX, note = "ambiguë \"deux réponses\"")
        assertEquals(r, ValidationRecord.parse(r.toJson()))
        val f = tmp(); f.writeText("# commentaire\n${r.toJson()}\n\nnot json\n")
        val l = ValidationLedger.load(listOf(f))
        assertEquals(ContentState.NEEDS_FIX, l.effective("a", null).state); assertEquals(1, l.errors.size)
    }

    // ------------------------------------------------------------------ hashes (same vectors as tools/content-validation)

    @Test fun hashVectorsMatchThePythonTool() {
        val qu = q("x").copy(question = "Capitale du Cameroun ?")
        assertEquals("4aba34d897c8b24d", ContentHash.question(qu))
        val shuffled = qu.copy(choices = listOf("Yaoundé", "Douala", "Bafoussam", "Garoua"), answer = 0)
        assertEquals("4aba34d897c8b24d", ContentHash.question(shuffled))
        assertNotEquals(ContentHash.question(qu), ContentHash.question(qu.copy(answer = 0)))
        val mcq = Exercise("e", "c", ExerciseKind.MCQ, "2 + 2 = ?", choices = listOf("3", "4", "5", "22"), answerIndex = 1, explanation = "On additionne.")
        assertEquals("a3ab444d79169a75", ContentHash.exercise(mcq))
        assertEquals("023f4fbad5e10aab", ContentHash.exercise(Exercise("e", "c", ExerciseKind.NUMERIC, "Aire d'un carré de côté 1,5 m", answerNumber = 2.25, tolerance = 0.01, explanation = "c×c")))
        assertEquals("4cfc1f36d4769884", ContentHash.exercise(Exercise("e", "c", ExerciseKind.TRUE_FALSE, "Vrai ?", answerBool = true)))
        assertEquals("eefcb410223673c8", ContentHash.exercise(Exercise("e", "c", ExerciseKind.MATCHING, "Associe", pairs = listOf("a" to "1", "b" to "2"))))
        val open = Exercise("o", "c", ExerciseKind.OPEN, "Explique", model = "Parce que")
        assertEquals("13f6506e1935c47c", ContentHash.exercise(Exercise("p", "c", ExerciseKind.PROBLEM, "Problème", parts = listOf(mcq, open))))
    }

    // ------------------------------------------------------------------ reports

    private fun report(item: String = "q1", reason: ReportReason = ReportReason.WRONG_ANSWER, note: String? = "faux", now: Long = 1_000_000L, id: String = java.util.UUID.randomUUID().toString()) =
        ContentReport.create(ContentKind.QUESTION, item, reason, note, h1, "quiz/3e", 1, now, Channel.BETA, id)

    @Test fun reportIsCleanedAndBounded() {
        val r = report(note = "  a\u0000b\u0007\n\n c   " + "x".repeat(500))!!
        assertTrue(r.note.length <= ContentReport.MAX_NOTE); assertTrue(r.note.startsWith("ab c")); assertTrue(r.note.none { it.isISOControl() })
        assertNull(report(item = "bad id with spaces")); assertNull(report(item = "../../etc"))
        assertNull(ContentReport.create(ContentKind.LESSON, "l", ReportReason.OTHER, null, "not-a-hash", null, null, 1, Channel.BETA))
        assertFalse(r.toJson().contains("deviceId"))                                // no identity in the report itself
        assertEquals(6, ReportReason.values().size)
    }

    @Test fun queueDedupesBoundsAndRateLimits() {
        var now = 1_000_000L
        val q = ReportQueue(tmp(), maxReports = 5, maxPerHour = 4, minGapMs = 2_000, clock = { now })
        assertEquals(ReportQueue.Add.ACCEPTED, q.add(report("q1")))
        assertEquals(ReportQueue.Add.DUPLICATE, q.add(report("q1")))                // same item + reason + hash
        now += 100
        assertEquals(ReportQueue.Add.RATE_LIMITED, q.add(report("q2")))             // too fast
        now += 3_000; assertEquals(ReportQueue.Add.ACCEPTED, q.add(report("q1", ReportReason.AMBIGUOUS)))   // other reason counts
        now += 3_000; assertEquals(ReportQueue.Add.ACCEPTED, q.add(report("q2")))
        now += 3_000; assertEquals(ReportQueue.Add.ACCEPTED, q.add(report("q3")))
        now += 3_000; assertEquals(ReportQueue.Add.RATE_LIMITED, q.add(report("q4")))   // 4 per hour
        now += ReportQueue.HOUR; assertEquals(ReportQueue.Add.ACCEPTED, q.add(report("q4")))
        assertEquals(ReportQueue.Add.INVALID, q.add(null))
        assertEquals(5, q.count())
        now += 3_000; q.add(report("q5")); now += 3_000; q.add(report("q6"))
        assertEquals(5, q.count()); assertEquals("q2", q.pending().first().itemId)   // bounded: the oldest are dropped
        assertFalse(q.pending(10).any { it.itemId == "q1" && it.reason == ReportReason.WRONG_ANSWER })
    }

    @Test fun queueSurvivesRestartAndAcknowledgements() {
        val f = tmp(); var now = 5_000_000L
        val q = ReportQueue(f, clock = { now })
        val a = report("q1", id = "11111111-aaaa")!!; q.add(a); now += 5_000; val b = report("q2", id = "22222222-bbbb")!!; q.add(b)
        val again = ReportQueue(f, clock = { now })
        assertEquals(listOf("q1", "q2"), again.pending().map { it.itemId })
        again.remove(listOf(a.id)); assertEquals(listOf("q2"), ReportQueue(f).pending().map { it.itemId })
    }

    @Test fun tvHandsItsReportsToThePhone() {
        var now = 9_000_000L
        val tv = ReportQueue(tmp(), clock = { now }); val phone = ReportQueue(tmp(), clock = { now })
        tv.add(report("q1")); now += 5_000; tv.add(report("q2", ReportReason.LANGUAGE))
        val acked = phone.importHandoff(tv.handoff())
        assertEquals(2, acked.size); assertEquals(2, phone.count())
        tv.remove(acked); assertEquals(0, tv.count())
        assertEquals(2, phone.importHandoff("[" + phone.pending().joinToString(",") { it.toJson() } + "]").size)   // replays are harmless
        assertEquals(2, phone.count())
        assertTrue(phone.importHandoff("not json").isEmpty()); assertTrue(phone.importHandoff("[{\"kind\":\"x\"}]").isEmpty()); assertEquals(2, phone.count())
        assertTrue(phone.importHandoff("[" + "{}".repeat(1) + "]" + " ".repeat(200_000)).isEmpty())   // too big
    }

    // ------------------------------------------------------------------ stats

    @Test fun collectorSendsIdsAndNumbersOnlyAndOnlyWithConsent() {
        val file = tmp(); var consent = Consent.ESSENTIAL
        val t = Telemetry("tv", 1, EventQueue(file), { consent })
        val c = ItemStatsCollector()
        c.record(ContentKind.QUESTION, "q1", true, 4000); c.record(ContentKind.QUESTION, "q1", false, 6000); c.recordReport(ContentKind.QUESTION, "q1")
        c.record(ContentKind.QUESTION, "bad id", true, 1)                            // refused
        assertEquals(1, c.size())
        assertEquals(0, c.flush(t)); assertEquals(0, c.size()); assertFalse(file.isFile && file.length() > 0)   // no consent: nothing queued, nothing kept
        consent = Consent.USAGE
        c.record(ContentKind.QUESTION, "q1", true, 4000); c.record(ContentKind.QUESTION, "q1", false, 6000); c.recordReport(ContentKind.QUESTION, "q1")
        assertEquals(1, c.flush(t))
        val line = file.readText()
        assertTrue(line.contains("\"name\":\"content_stat\"")); assertTrue(line.contains("\"item\":\"q1\"")); assertTrue(line.contains("\"shown\":2"))
        assertTrue(line.contains("\"correct\":1")); assertTrue(line.contains("\"ms\":10000")); assertTrue(line.contains("\"reports\":1"))
    }

    @Test fun itemStatMath() {
        val s = ItemStat(4, 3, 20_000) + ItemStat(6, 3, 10_000, 2)
        assertEquals(ItemStat(10, 6, 30_000, 2), s); assertEquals(0.6, s.successRate!!, 1e-9); assertEquals(3_000L, s.averageMs); assertNull(ItemStat().successRate)
    }

    @Test fun suspiciousScore() {
        // not enough data: success rate ignored; reports still count
        assertEquals(0.0, QualitySignals.suspicion(3, ItemStat(10, 0, 0)).score, 1e-9)
        // a wrong key: well below chance with enough displays → flagged, strong reason
        val wrong = QualitySignals.suspicion(2, ItemStat(100, 10, 0)); assertTrue(wrong.flagged); assertTrue(wrong.reasons.any { "hasard" in it })
        // as expected for the difficulty: not flagged
        for ((d, p) in listOf(1 to 0.90, 2 to 0.78, 3 to 0.65, 4 to 0.50, 5 to 0.35)) assertFalse(QualitySignals.suspicion(d, ItemStat(200, (200 * p).toInt(), 0)).flagged, "difficulty $d")
        // far from expectation: a very easy question that nobody gets right
        assertTrue(QualitySignals.suspicion(1, ItemStat(60, 30, 0)).flagged)
        // too easy is suspicious but less than too hard
        val easy = QualitySignals.suspicion(5, ItemStat(100, 95, 0)); val hard = QualitySignals.suspicion(1, ItemStat(100, 60, 0))
        assertTrue(easy.score > 0.0 && easy.score < hard.score)
        // reports alone: 3 flag whatever the rate, 1 does not
        assertTrue(QualitySignals.suspicion(3, ItemStat(5, 5, 0, 3)).flagged); assertFalse(QualitySignals.suspicion(3, ItemStat(5, 5, 0, 1)).flagged)
        // the score stays in 0..1 and grows with reports
        val a = QualitySignals.suspicion(3, ItemStat(100, 20, 0, 0)).score; val b = QualitySignals.suspicion(3, ItemStat(100, 20, 0, 9)).score
        assertTrue(a in 0.0..1.0 && b in 0.0..1.0 && b > a)
        val (lo, hi) = QualitySignals.wilson(50, 100); assertTrue(lo < 0.5 && hi > 0.5 && lo > 0.39 && hi < 0.61)
    }
}
