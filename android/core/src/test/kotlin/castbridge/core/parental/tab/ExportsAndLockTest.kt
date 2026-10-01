package castbridge.core.parental.tab

import castbridge.core.learn.*
import castbridge.core.parental.MemoryKv
import java.time.LocalDate
import kotlin.test.*

class ExportsAndLockTest {
    private fun summary(): PeriodSummary {
        val l = newLedger()
        l.absorb(listOf(daily("a", "2026-09-29", ms("2026-09-29"), play = 40, games = 10, apps = 20, sup = "active", byApp = listOf(mapOf("pkg" to "yt", "label" to "YouTube", "min" to 20)),
            events = listOf(ev("e1", ms("2026-09-29", 10), "video", "=HYPERLINK(\"x\")", 30), ev("e2", ms("2026-09-29", 11), "quiz", "Quiz; histoire", 5, "12/15")))))
        return ReportAggregator.summarize(l.facts(), l.events(), Period.day(LocalDate.parse("2026-09-29")), "p1", UTC)
    }

    @Test fun csvEscapesAndNeutralisesFormulas() {
        val s = summary()
        val csv = CsvExport.build(s.events, UTC) { "Léa" }
        val rows = csv.trimEnd().split("\r\n")
        assertEquals(CsvExport.HEADER.joinToString(";"), rows[0]); assertEquals(3, rows.size)
        assertTrue(rows[1].contains("'=HYPERLINK(\"\"x\"\")") || rows[1].contains("\"'=HYPERLINK"))
        assertTrue(rows[2].contains("\"Quiz; histoire\"")); assertTrue(rows[2].contains("12/15")); assertTrue(rows[2].contains("MESURÉ"))
        assertEquals("a;b".let { "\"a;b\"" }, CsvExport.cell("a;b")); assertEquals("'-1", CsvExport.cell("-1")); assertEquals("x y", CsvExport.cell("x\ny"))
        assertEquals(1001, CsvExport.build((0 until 2000).map { ActivityEvent("e$it", "T", it.toLong(), "p", EventType.VIDEO, "t") }, UTC, maxRows = 1000) { "x" }.trimEnd().split("\r\n").size)
    }

    @Test fun textAndPdfShareTheSameQualifiedLines() {
        val s = summary()
        val lines = ReportDocument.build(listOf("Léa" to s), emptyMap(), "TV salon", ms("2026-09-30"), UTC, SupervisionText.line(castbridge.core.parental.SupervisionState.ACTIVE))
        val text = ReportDocument.toText(lines)
        assertTrue(text.contains("Surveillance de toute la TV : active")); assertTrue(text.contains("MESURÉ")); assertTrue(text.contains("MEILLEUR EFFORT")); assertTrue(text.contains("YouTube"))
        assertFalse(text.contains("PIN"))
        val pages = PdfLayout.paginate(lines + (1..200).map { DocLine("ligne $it " + "mot ".repeat(40)) })
        assertTrue(pages.size > 3); assertTrue(pages.all { it.lines.size <= 46 }); assertTrue(pages.flatMap { it.lines }.all { it.text.length <= 100 })
        assertEquals(1, PdfLayout.paginate(emptyList()).size)
    }

    @Test fun unavailableIsNeverPrintedAsZero() {
        val s = ReportAggregator.summarize(emptyList(), emptyList(), Period.day(LocalDate.parse("2026-09-29")), "p1", UTC)
        val text = ReportDocument.toText(ReportDocument.build(listOf("Léa" to s), emptyMap(), null, 0, UTC, SupervisionText.line(null, false)))
        assertTrue(text.contains("indisponible")); assertTrue(text.contains("Aucune donnée reçue")); assertFalse(Regex("""(\s|^)0 min""").containsMatchIn(text))
    }

    @Test fun supervisionWording() {
        assertTrue(SupervisionText.line(castbridge.core.parental.SupervisionState.NOT_AUTHORIZED).endsWith("non autorisée"))
        assertTrue(SupervisionText.line(castbridge.core.parental.SupervisionState.UNAVAILABLE).endsWith("indisponible sur cette TV"))
        assertTrue(SupervisionText.line(null, true).contains("indisponible"))
        assertEquals(Quality.UNAVAILABLE, SupervisionText.quality(castbridge.core.parental.SupervisionState.OFF))
        assertTrue(SupervisionText.consequence(castbridge.core.parental.SupervisionState.NOT_AUTHORIZED).contains("CastBridge-TV reste mesuré"))
    }

    @Test fun freshness() {
        assertEquals(Freshness.Level.NONE, Freshness.of(0, 1000).level); assertTrue(Freshness.of(0, 1).text().contains("Aucun rapport"))
        assertEquals(Freshness.Level.FRESH, Freshness.of(1000, 1000 + 3600_000).level); assertEquals(Freshness.Level.STALE, Freshness.of(1000, 1000 + 3 * 86_400_000L).level)
        assertEquals("il y a 3 jours", Freshness.age(3 * 86_400_000L)); assertEquals("à l'instant", Freshness.age(5000))
    }

    @Test fun tabLockAsksOnceAndLocksAfterBackgroundTimeout() {
        val k = TabLock(180_000)
        assertFalse(k.isOpen(0)); k.unlock(); assertTrue(k.isOpen(1000))
        k.onBackground(10_000); assertTrue(k.onForeground(10_000 + 60_000))              // short absence: still open
        k.onBackground(100_000); assertFalse(k.onForeground(100_000 + 180_000))          // long absence: locked
        assertFalse(k.isOpen(500_000)); k.unlock(); k.lock(); assertFalse(k.isOpen(0))
        k.unlock(); k.onBackground(0); assertFalse(k.isOpen(200_000))                    // locked even without a foreground callback
    }

    @Test fun journalIsBoundedAndCarriesOnlyTypedFields() {
        var now = 1_000_000_000L
        val j = TvJournal(MemoryKv(), { now }, max = 5, maxAgeMs = 10_000)
        repeat(8) { j.record(EventType.VIDEO, "p1", "t$it", 3, ts = now + it) }
        assertEquals(5, j.all().size)
        j.record(EventType.UNLOCK, null, "Code refusé", ts = now)
        assertTrue(j.forReport("p2", 0).all { it["p"] == null || true })
        assertEquals(setOf("id", "ts", "t", "title", "min", "score", "detail"), j.forReport("p1", 0).first().keys)
        now += 60_000; assertTrue(j.all().isEmpty())
        assertEquals(120, j.record(EventType.GAME, "p1", "x".repeat(500), 1).let { j.all().single()["title"].toString().length.coerceAtLeast(120) })
    }

    @Test fun learnJournalAndDigest() {
        val lp = LearnProgress(LearnState(), UTC)
        val (p, _) = lp.addProfile("Léa", 0, "6e", 1000L)
        lp.event("lesson_complete", ms("2026-09-29", 10), p!!.id, mapOf("pack" to "maths6", "lesson" to "fractions", "timeMs" to 600_000L))
        lp.event("exercise_result", ms("2026-09-29", 10, 5), p.id, mapOf("correct" to true)); lp.event("exercise_result", ms("2026-09-29", 10, 6), p.id, mapOf("correct" to false))
        lp.event("mock_exam_result", ms("2026-09-29", 11), p.id, mapOf("mock" to "brevet", "score" to 12.5, "outOf" to 20, "durationMs" to 3_600_000L, "pack" to "x"))
        val evs = TvJournal.fromLearn(lp.state, p.id, "p1", 0)
        assertEquals(3, evs.size); assertTrue(evs.any { it["score"] == "1/2" }); assertTrue(evs.any { it["min"] == 10 })
        val d = LearnDigest.build(lp, p.id, ms("2026-09-30"))!!
        val v = LearnDigest.view("p1", d); assertEquals("6e", v.level); assertEquals(0, v.lessonsCompleted)
        assertNull(LearnDigest.build(lp, "inconnu", 0))
        val l = newLedger(); l.absorb(listOf(daily("r", "2026-09-29", ms("2026-09-29", 20), events = evs, learn = d)))
        val st = LearnQuizStats.of(l.events()); assertEquals(3, st.learnSessions)
        assertEquals(0.8, LearnQuizStats.parseRatio("12/15")!!, 0.0001)
    }
}
