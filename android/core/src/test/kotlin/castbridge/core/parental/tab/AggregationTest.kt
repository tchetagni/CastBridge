package castbridge.core.parental.tab

import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.*

class AggregationTest {
    private fun d(s: String) = LocalDate.parse(s)

    @Test fun periodsAndPrevious() {
        val w = Period.week(d("2026-09-30"))                      // Wednesday
        assertEquals(d("2026-09-28"), w.from); assertEquals(d("2026-10-04"), w.to); assertEquals(7, w.dayCount)
        assertEquals(d("2026-09-21"), w.previous().from)
        val m = Period.month(d("2026-03-15")); assertEquals(d("2026-03-31"), m.to); assertEquals(d("2026-02-01"), m.previous().from); assertEquals(d("2026-02-28"), m.previous().to)
        val c = Period.custom(d("2026-09-10"), d("2026-09-01")); assertEquals(10, c.dayCount); assertEquals(d("2026-08-22"), c.previous().from)
        assertEquals(Period.MAX_DAYS, Period.custom(d("2020-01-01"), d("2026-01-01")).dayCount)
    }

    @Test fun dstDayHas23HoursAndMidnightBoundariesFollowTheZone() {
        val paris = ZoneId.of("Europe/Paris")
        val spring = Period.day(d("2026-03-29"))
        assertEquals(23 * 3600_000L, spring.endMs(paris) - spring.startMs(paris))
        val autumn = Period.day(d("2026-10-25")); assertEquals(25 * 3600_000L, autumn.endMs(paris) - autumn.startMs(paris))
        // 23:30 Paris on the 28th belongs to the 28th, 00:30 Paris on the 29th belongs to the 29th, whatever UTC says
        val late = ms("2026-03-28", 23, 30, paris); val early = ms("2026-03-29", 0, 30, paris)
        assertEquals(d("2026-03-28"), Clock.dayOf(late, paris)); assertEquals(d("2026-03-29"), Clock.dayOf(early, paris))
        assertTrue(Period.day(d("2026-03-29")).containsTs(early, paris)); assertFalse(Period.day(d("2026-03-29")).containsTs(late, paris))
    }

    @Test fun eventsAcrossMidnightAndZones() {
        val ny = ZoneId.of("America/New_York")
        // 03:00 UTC on the 29th is still the 28th in New York
        val e = ActivityEvent("a", "TV", ms("2026-09-29", 3), "p1", EventType.VIDEO, "x", 10)
        assertTrue(Period.day(d("2026-09-28")).containsTs(e.ts, ny)); assertFalse(Period.day(d("2026-09-28")).containsTs(e.ts, UTC))
        // a 90 minute video from 23:30 spreads over two hours-of-day and two weekdays
        val hm = Heatmap.build(listOf(ActivityEvent("v", "TV", ms("2026-09-28", 23, 30), "p1", EventType.VIDEO, "film", 90)), UTC)   // Monday
        assertEquals(30, hm.min(0, 23)); assertEquals(60, hm.min(1, 0)); assertEquals(90, hm.totalMin())
    }

    @Test fun heatmapPeakLateNightLongest() {
        val ev = listOf(
            ActivityEvent("1", "TV", ms("2026-09-28", 20), "p1", EventType.VIDEO, "A", 60), ActivityEvent("2", "TV", ms("2026-09-29", 20, 30), "p1", EventType.GAME, "B", 30),
            ActivityEvent("3", "TV", ms("2026-09-29", 23), "p1", EventType.VIDEO, "C", 45), ActivityEvent("4", "TV", ms("2026-09-30", 7), "p1", EventType.LEARN, "D", 20),
            ActivityEvent("5", "TV", ms("2026-09-30", 9), "p1", EventType.VIDEO, "E", null))
        val hm = Heatmap.build(ev, UTC)
        assertEquals(20, hm.peakHours(1).single().first); assertEquals(45, hm.lateNightMin())
        assertEquals(listOf("A", "C"), UseStats.longest(ev, 2).map { it.title })
        assertNull(Heatmap.build(emptyList(), UTC).lateNightMin()); assertTrue(Heatmap.build(emptyList(), UTC).peakHours().isEmpty())
        assertEquals(60 + 30 + 45 + 20, hm.totalMin())                       // the untimed event adds nothing (unknown, not 0 minutes)
    }

    @Test fun heatmapOnDstFallBackCountsEveryRealMinute() {
        val paris = ZoneId.of("Europe/Paris")
        val start = ms("2026-10-25", 0, 30, paris)            // 22:30 UTC the day before
        val hm = Heatmap.build(listOf(ActivityEvent("v", "TV", start, "p1", EventType.VIDEO, "nuit", 240)), paris)
        assertEquals(240, hm.totalMin())                                    // 4 real hours although the wall clock shows 3 hours of difference
        assertTrue(Heatmap.build(listOf(ActivityEvent("v", "TV", start, "p1", EventType.VIDEO, "interminable", 100_000)), paris).totalMin() <= Heatmap.MAX_EVENT_MIN)
    }

    @Test fun outsideWindow() {
        val w = "08:00 à 20:00"
        val ev = listOf(ActivityEvent("1", "TV", ms("2026-09-28", 19, 30), "p1", EventType.VIDEO, "A", 60), ActivityEvent("2", "TV", ms("2026-09-28", 10), "p1", EventType.GAME, "B", 30))
        assertEquals(30, UseStats.outsideWindowMin(ev, w, UTC))
        assertNull(UseStats.outsideWindowMin(ev, null, UTC)); assertNull(UseStats.outsideWindowMin(emptyList(), w, UTC))
        // overnight window 20:00 -> 08:00: 21:50 is inside
        assertEquals(0, UseStats.outsideWindowMin(listOf(ActivityEvent("1", "TV", ms("2026-09-28", 21, 50), "p1", EventType.VIDEO, "A", 10)), "20:00 à 08:00", UTC))
    }

    @Test fun trendsAndComparison() {
        val up = Trends.compare(140, 7, 70, 7); assertEquals(Dir.UP, up.dir); assertEquals(100, up.pct); assertEquals(10, up.deltaMin)
        assertEquals(Dir.DOWN, Trends.compare(35, 7, 70, 7).dir); assertEquals(Dir.FLAT, Trends.compare(71, 7, 70, 7).dir)
        assertEquals(Dir.UNKNOWN, Trends.compare(70, 7, null, 0).dir); assertEquals(Dir.UNKNOWN, Trends.compare(null, 0, 70, 7).dir)
        // 2 days received out of 7 with the same daily average: no fake drop
        assertEquals(Dir.FLAT, Trends.compare(100, 2, 350, 7).dir)
        assertTrue(Trends.compare(100, 2, 70, 7).note.contains("par jour reçu"))
        assertTrue(up.text().contains("▲"))
    }

    @Test fun goalsVersusActual() {
        val l = newLedger()
        l.absorb(listOf(daily("a", "2026-09-28", ms("2026-09-28"), play = 30, limit = 60), daily("b", "2026-09-29", ms("2026-09-29"), play = 50, limit = 60), daily("c", "2026-09-30", ms("2026-09-30"), play = 50, apps = 30, limit = 60),
            daily("e", "2026-10-01", ms("2026-10-01"), play = 10, limit = 0, sup = "unauthorized")))
        val s = ReportAggregator.summarize(l.facts(), l.events(), Period.custom(d("2026-09-28"), d("2026-10-02")), "p1", UTC)
        val g = Goals.check(s)
        assertEquals(listOf(GoalStatus.OK, GoalStatus.NEAR, GoalStatus.OVER, GoalStatus.NO_LIMIT, GoalStatus.UNKNOWN), g.days.map { it.status })
        assertFalse(g.days[0].partial); assertTrue(g.days[3].partial)
        assertEquals(1, g.over); assertEquals(1, g.unknown)
        assertNull(g.days[4].usedMin)
    }

    @Test fun perAppAndTopN() {
        val l = newLedger()
        l.absorb(listOf(daily("a", "2026-09-28", ms("2026-09-28"), apps = 40, byApp = listOf(mapOf("pkg" to "yt", "label" to "YouTube", "min" to 30), mapOf("pkg" to "nf", "label" to "Netflix", "min" to 10))),
            daily("b", "2026-09-29", ms("2026-09-29"), apps = 15, byApp = listOf(mapOf("pkg" to "nf", "label" to "Netflix", "min" to 15)))))
        val s = ReportAggregator.summarize(l.facts(), l.events(), Period.week(d("2026-09-30")), "p1", UTC)
        assertEquals(listOf("YouTube", "Netflix"), s.apps.map { it.label }); assertEquals(25, s.apps[1].min); assertEquals(2, s.apps[1].days)
        val ev = listOf(ActivityEvent("1", "T", 1, "p1", EventType.VIDEO, "Bluey", 10), ActivityEvent("2", "T", 2, "p1", EventType.VIDEO, "Bluey", 15), ActivityEvent("3", "T", 3, "p1", EventType.VIDEO, "Pat", 5))
        val top = ReportAggregator.top(ev, setOf(EventType.VIDEO), 1); assertEquals("Bluey", top.single().title); assertEquals(25, top.single().min); assertEquals(2, top.single().count)
    }

    @Test fun allProfilesSumsAndPerProfileSplits() {
        val l = newLedger()
        l.absorb(listOf(daily("a", "2026-09-29", ms("2026-09-29"), pid = "p1", play = 20), daily("b", "2026-09-29", ms("2026-09-29"), pid = "p2", name = "Tom", play = 40)))
        val all = ReportAggregator.summarize(l.facts(), l.events(), Period.day(d("2026-09-29")), null, UTC)
        assertEquals(60, all.measured.value)
        val per = ReportAggregator.perProfile(l.facts(), l.events(), Period.day(d("2026-09-29")), UTC)
        assertEquals(setOf("p1", "p2"), per.keys); assertEquals(40, per["p2"]!!.measured.value)
    }

    @Test fun unlockAttemptsOnlyWhenTheJournalWasReceived() {
        val l = newLedger()
        l.absorb(listOf(daily("a", "2026-09-29", ms("2026-09-29"), play = 5)))
        assertNull(ReportAggregator.summarize(l.facts(), l.events(), Period.day(d("2026-09-29")), "p1", UTC).unlockAttempts.value)
        l.absorb(listOf(daily("b", "2026-09-29", ms("2026-09-29", 22), play = 5, events = listOf(ev("u1", ms("2026-09-29", 18), "unlock", "Code parental refusé"), ev("u2", ms("2026-09-29", 18, 5), "unlock", "Code parental refusé")))))
        assertEquals(2, ReportAggregator.summarize(l.facts(), l.events(), Period.day(d("2026-09-29")), "p1", UTC).unlockAttempts.value)
    }
}
