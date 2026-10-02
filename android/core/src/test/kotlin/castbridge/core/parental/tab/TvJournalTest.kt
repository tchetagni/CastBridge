package castbridge.core.parental.tab

import castbridge.core.parental.MemoryKv
import kotlin.test.*

class TvJournalTest {
    @Test fun defaultsAre800EventsAnd14Days() {
        var now = 1_000_000_000_000L
        val j = TvJournal(MemoryKv(), { now })
        assertEquals(800, j.max); assertEquals(14 * 86_400_000L, j.maxAgeMs)
        repeat(850) { j.record(EventType.VIDEO, "p1", "t$it", 1, ts = now + it) }
        assertEquals(800, j.all().size); assertEquals("t849", j.all().last()["title"])
        now += 13 * 86_400_000L; assertEquals(800, j.all().size)
        now += 2 * 86_400_000L; assertTrue(j.all().isEmpty())
    }

    @Test fun newEventTypesHaveFrenchLabelsAndRoundTrip() {
        val j = TvJournal(MemoryKv(), { 5_000L })
        j.record(EventType.SUDOKU, "p1", "Partie de Sudoku", 12)
        j.record(EventType.SCREEN, null, "Écran allumé"); j.record(EventType.CONNECTION, null, "Téléphone")
        assertEquals(listOf("sudoku", "screen", "connection"), j.all().map { it["t"] })
        assertEquals("Sudoku", EventType.of("sudoku")!!.label); assertEquals(EventType.Group.GAMES, EventType.SUDOKU.group)
        assertEquals("Connexion à la TV", EventType.CONNECTION.label)
    }

    @Test fun sessionTrackerAcceptsSudoku() {
        var now = 0L
        val j = TvJournal(MemoryKv(), { now }); val t = SessionTracker(j, { now })
        repeat(8) { now += 15_000; t.tick(EventType.SUDOKU, "Sudoku", "p1", 15_000) }
        t.tick(null, "", null, 0)
        val e = j.all().single(); assertEquals("sudoku", e["t"]); assertEquals(2, (e["min"] as Number).toInt())
    }
}
