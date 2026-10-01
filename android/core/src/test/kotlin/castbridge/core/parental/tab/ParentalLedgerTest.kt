package castbridge.core.parental.tab

import castbridge.core.parental.MemoryInboxPersistence
import kotlin.test.*

class ParentalLedgerTest {
    private val d1 = "2026-09-28"; private val d2 = "2026-09-29"

    @Test fun duplicateDeliveryCountsOnce() {
        val l = newLedger()
        val r = daily("r1", d1, ms(d1, 20), play = 30, events = listOf(ev("a", ms(d1, 10), "video", "Dessin animé", 20)))
        assertEquals(1, l.absorb(listOf(r)))
        assertEquals(0, l.absorb(listOf(r)))
        // the same event inside a second (overlapping) report of the same day: still one event
        l.absorb(listOf(daily("r2", d1, ms(d1, 23), play = 45, events = listOf(ev("a", ms(d1, 10), "video", "Dessin animé", 20), ev("b", ms(d1, 21), "game", "Échecs", 10)))))
        assertEquals(2, l.events().size)
        assertEquals(45, l.facts().single().play)                 // the newest report of the day wins, not the sum
    }

    @Test fun outOfOrderGivesTheSameResult() {
        val a = daily("r1", d1, ms(d1, 20), play = 30); val b = daily("r2", d1, ms(d1, 23), play = 45)
        val l1 = newLedger(); l1.absorb(listOf(a)); l1.absorb(listOf(b))
        val l2 = newLedger(); l2.absorb(listOf(b)); l2.absorb(listOf(a))
        assertEquals(l1.facts(), l2.facts())
        assertEquals(45, l2.facts().single().play)
    }

    @Test fun gapStaysAbsentNotZero() {
        val l = newLedger(); l.absorb(listOf(daily("r1", "2026-09-25", ms("2026-09-25"), play = 10), daily("r2", "2026-09-29", ms("2026-09-29"), play = 20)))
        val s = ReportAggregator.summarize(l.facts(), l.events(), Period.custom(java.time.LocalDate.parse("2026-09-25"), java.time.LocalDate.parse("2026-09-29")), "p1", UTC)
        assertEquals(2, s.coveredDays); assertEquals(3, s.missingDays.size)
        assertEquals(30, s.measured.value)
        assertNull(s.days[1].knownMin)                            // 26 sept: nobody reported: null, never 0
        assertTrue(s.coverageText().contains("incomplète"))
    }

    @Test fun noReportMeansUnavailableFigures() {
        val l = newLedger()
        val s = ReportAggregator.summarize(l.facts(), l.events(), Period.week(java.time.LocalDate.parse(d1)), null, UTC)
        assertNull(s.measured.value); assertEquals(Quality.UNAVAILABLE, s.measured.quality); assertEquals("indisponible", s.measured.text())
        assertNull(s.blocks.value); assertNull(s.otherApps.value)
    }

    @Test fun weeklyTotalFillsAGapButDailyKeepsItsDetail() {
        val l = newLedger()
        l.absorb(listOf(weekly("w1", ms("2026-09-30"), mapOf(d1 to 50L, d2 to 20L))))
        var f = l.facts().first { it.day == d1 }
        assertFalse(f.kindsKnown); assertEquals(50, f.totalMin); assertNull(f.appsMin)          // total only: apps unknown, not zero
        l.absorb(listOf(daily("r1", d1, ms(d1, 20), play = 30, apps = 5)))
        f = l.facts().first { it.day == d1 }
        assertTrue(f.kindsKnown); assertEquals(30, f.play); assertEquals(50, f.totalMin); assertEquals(15, f.unsplitMin)   // late usage counted, kept apart
    }

    @Test fun profileRenamedAndDeleted() {
        val l = newLedger()
        l.absorb(listOf(daily("r1", d1, ms(d1), name = "Léa"), daily("r2", d2, ms(d2), name = "Léa-Rose")))
        assertEquals("Léa-Rose", l.profileName("TV salon", "p1"))
        l.absorb(listOf(daily("r0", "2026-09-20", ms("2026-09-20"), name = "Ancien nom")))      // late old report must not undo the rename
        assertEquals("Léa-Rose", l.profileName("TV salon", "p1"))
        l.markProfiles("TV salon", emptyList())
        assertTrue(l.profileName("TV salon", "p1").contains("profil supprimé"))
        assertTrue(l.facts().isNotEmpty())                                                       // history kept
        l.markProfiles("TV salon", listOf("p1")); assertFalse(l.profileName("TV salon", "p1").contains("supprimé"))
    }

    @Test fun supervisionBecomingInactiveMidPeriod() {
        val l = newLedger()
        l.absorb(listOf(daily("r1", d1, ms(d1), apps = 20, sup = "active"), daily("r2", d2, ms(d2), apps = 0, sup = "unauthorized")))
        val s = ReportAggregator.summarize(l.facts(), l.events(), Period.custom(java.time.LocalDate.parse(d1), java.time.LocalDate.parse(d2)), "p1", UTC)
        assertEquals(Quality.BEST_EFFORT, s.otherApps.quality); assertEquals(20, s.otherApps.value)
        assertTrue(s.otherApps.note!!.contains("1 jour(s) sur 2"))
        assertNull(s.days[1].appsMin)                                                            // second day: unknown, not zero
        assertEquals(listOf("active", "unauthorized"), l.supervisionLog().map { it.state })
    }

    @Test fun supervisionInactiveEverywhereGivesUnavailableApps() {
        val l = newLedger(); l.absorb(listOf(daily("r1", d1, ms(d1), play = 12, apps = 0, sup = "unavailable")))
        val s = ReportAggregator.summarize(l.facts(), l.events(), Period.day(java.time.LocalDate.parse(d1)), "p1", UTC)
        assertEquals(Quality.UNAVAILABLE, s.otherApps.quality); assertNull(s.otherApps.value)
        assertEquals(Quality.MEASURED, s.measured.quality); assertEquals(12, s.measured.value)  // CastBridge-TV stays complete
    }

    @Test fun purgeIsRememberedAndPerProfile() {
        val l = newLedger()
        val r1 = daily("r1", d1, ms(d1), pid = "p1", at = 1000); val r2 = daily("r2", d1, ms(d1), pid = "p2", name = "Tom", at = 1000)
        l.absorb(listOf(r1, r2)); l.purge("TV salon", "p1")
        assertEquals(listOf("p2"), l.facts().map { it.profileId })
        // the same reports absorbed again (inbox not purged) do not bring p1 back; a report received later does
        l.absorb(listOf(r1.copy(id = "r1b")))
        assertEquals(listOf("p2"), l.facts().map { it.profileId })
        l.absorb(listOf(daily("r3", d2, ms(d2), pid = "p1", at = System.currentTimeMillis() + 10_000)))
        assertTrue(l.facts().any { it.profileId == "p1" })
        l.purge(); assertTrue(l.isEmpty())
        l.absorb(listOf(r2.copy(id = "r2b"))); assertTrue(l.isEmpty())
    }

    @Test fun sameProfileIdOnTwoTvsStaysSeparate() {
        val l = newLedger()
        l.absorb(listOf(daily("a", d1, ms(d1), pid = "c1", name = "Léa", tv = "TV salon", play = 10), daily("b", d1, ms(d1), pid = "c1", name = "Tom", tv = "TV chambre", play = 20)))
        assertEquals("Léa", l.profileName("TV salon", "c1")); assertEquals("Tom", l.profileName("TV chambre", "c1"))
        assertEquals(10, ReportAggregator.summarize(l.facts(), l.events(), Period.day(java.time.LocalDate.parse(d1)), "c1", UTC, "TV salon").measured.value)
        l.markProfiles("TV salon", emptyList()); assertTrue(l.profileName("TV salon", "c1").contains("supprimé")); assertFalse(l.profileName("TV chambre", "c1").contains("supprimé"))
        l.purge("TV salon"); assertEquals(listOf("TV chambre"), l.facts().map { it.tv }); assertEquals(1, l.tvList().size)
    }

    @Test fun retentionByAgeAndSize() {
        var now = ms("2026-09-30")
        val l = ParentalLedger(MemoryInboxPersistence(), Retention(days = 30, maxEvents = 5), { now })
        l.absorb(listOf(daily("old", "2026-07-01", ms("2026-07-01"), blocked = listOf(mapOf("ts" to ms("2026-07-01"), "what" to "x", "why" to "y"))), daily("new", d1, ms(d1), play = 3)))
        assertEquals(listOf(d1), l.facts().map { it.day }); assertTrue(l.events().isEmpty())
        l.absorb(listOf(daily("big", d2, ms(d2), events = (1..20).map { ev("e$it", ms(d2, 8) + it * 1000L, "video", "v$it", 1) })))
        assertEquals(5, l.events().size)
        l.applyRetention(Retention(days = 90)); assertEquals(90, l.retention.days)
    }

    @Test fun persistsAndReloads() {
        val p = MemoryInboxPersistence(); val l = ParentalLedger(p, Retention(), { ms("2026-09-30") })
        l.absorb(listOf(daily("r1", d1, ms(d1), play = 9, events = listOf(ev("a", ms(d1, 9), "quiz", "Quiz", 8, "12/15")), learn = mapOf("streak" to 3)), alert("a1", ms(d1, 15), "tamper", "Surveillance coupée", pid = null)))
        val l2 = ParentalLedger(p, Retention(), { ms("2026-09-30") })
        assertEquals(l.facts(), l2.facts()); assertEquals(l.events(), l2.events()); assertEquals(3, (l2.learnDigest("TV salon", "p1")!!["streak"] as Number).toInt())
        assertEquals(0, l2.absorb(listOf(daily("r1", d1, ms(d1), play = 9))))                    // seen ids survive a restart
    }

    @Test fun alertsBecomeEventsWithSeverityAndAction() {
        val l = newLedger()
        l.absorb(listOf(alert("a1", ms(d1, 15), "tamper", "Surveillance affaiblie", pid = null), alert("a2", ms(d1, 16), "blocked", "Léa a essayé d'ouvrir X"), alert("a3", ms(d1, 17), "limit", "Temps atteint")))
        val h = AlertHistory.of(l.events())
        assertEquals(Severity.CRITICAL, h.first { it.id == "al:a1" }.severity)
        assertTrue(AlertHistory.action(h.first { it.id == "al:a1" }).isNotBlank())
        assertEquals(1, AlertHistory.of(l.events(), Severity.CRITICAL).size)
        assertTrue(h.any { it.type == EventType.BLOCK })
    }

    @Test fun garbageReportsDoNotCrash() {
        val l = newLedger()
        l.absorb(listOf(castbridge.core.parental.StoredReport("x", "TV", 1, "daily", mapOf("day" to "pas-une-date"), false, 1), castbridge.core.parental.StoredReport("y", "TV", 1, "weird", emptyMap(), false, 1)))
        assertTrue(l.facts().isEmpty())
    }

    @Test fun hugeEventCountIsBoundedAndFast() {
        val l = ParentalLedger(MemoryInboxPersistence(), Retention(maxEvents = 20_000), { ms("2026-09-30") })
        val t0 = System.nanoTime()
        val r = (0 until 40).map { i -> daily("r$i", d1, ms(d1) + i, events = (0 until 1000).map { k -> ev("e$i-$k", ms(d1, 1) + (i * 1000L + k) * 100, "video", "Titre ${k % 50}", 1 + k % 30) }) }
        l.absorb(r)
        assertTrue(l.events().size <= 20_000)
        val s = ReportAggregator.summarize(l.facts(), l.events(), Period.day(java.time.LocalDate.parse(d1)), null, UTC)
        Heatmap.build(s.events, UTC)
        assertTrue((System.nanoTime() - t0) / 1_000_000 < 20_000, "too slow")
    }
}
