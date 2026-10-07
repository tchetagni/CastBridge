package castbridge.server.wallet.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Les fenêtres calendaires des plafonds de parties gagnées : heure d'Africa/Douala (UTC+1, sans heure d'été), semaine lundi-dimanche, mois civil. */
class WinWindowsTest {
    private static WinWindows.Windows at(String utc) { return WinWindows.of(Instant.parse(utc)); }

    @Test
    void aMiddayInDoualaGivesTheWindowsThatContainIt() {
        WinWindows.Windows w = at("2026-10-07T12:00:00Z");   // mercredi
        assertEquals(Instant.parse("2026-10-06T23:00:00Z"), w.dayStart(), "minuit à Douala = 23:00 UTC la veille");
        assertEquals(Instant.parse("2026-10-07T23:00:00Z"), w.nextDay());
        assertEquals(Instant.parse("2026-10-04T23:00:00Z"), w.weekStart(), "lundi 05/10 00:00 à Douala");
        assertEquals(Instant.parse("2026-10-11T23:00:00Z"), w.nextWeek(), "lundi 12/10");
        assertEquals(Instant.parse("2026-09-30T23:00:00Z"), w.monthStart());
        assertEquals(Instant.parse("2026-10-31T23:00:00Z"), w.nextMonth(), "1er novembre 00:00 à Douala");
    }

    @Test
    void sundayNightBelongsToTheOldWeekAndMondayStartsANewOne() {
        WinWindows.Windows sundayLate = at("2026-10-04T22:59:59Z");    // dimanche 23:59:59 à Douala
        WinWindows.Windows mondayFirst = at("2026-10-04T23:00:00Z");   // lundi 00:00:00 à Douala
        assertEquals(Instant.parse("2026-09-27T23:00:00Z"), sundayLate.weekStart());
        assertEquals(Instant.parse("2026-10-04T23:00:00Z"), mondayFirst.weekStart());
        assertEquals(mondayFirst.weekStart(), sundayLate.nextWeek());
    }

    @Test
    void theLastSecondOfAMonthAndTheFirstOfTheNext() {
        assertEquals(Instant.parse("2026-09-30T23:00:00Z"), at("2026-10-01T10:00:00Z").monthStart());
        assertEquals(Instant.parse("2026-08-31T23:00:00Z"), at("2026-09-30T22:59:59Z").monthStart(), "le 30/09 à 23:59 à Douala est encore en septembre");
        assertEquals(Instant.parse("2026-09-30T23:00:00Z"), at("2026-09-30T23:00:00Z").monthStart(), "le 1er octobre 00:00 à Douala");
        assertEquals(Instant.parse("2026-12-31T23:00:00Z"), at("2026-12-15T10:00:00Z").nextMonth(), "décembre : le mois suivant est janvier 2027");
    }

    @Test
    void theTextsAreThoseOfTheChallengeAndTheMostConstrainingCapWins() {
        WinWindows.Windows w = at("2026-10-04T09:00:00Z");   // dimanche 04/10
        assertTrue(WinWindows.reached(w, 2, 9, 14, 3, 10, 15).isEmpty());
        assertTrue(WinWindows.reached(w, 100, 100, 100, 0, 0, 0).isEmpty(), "0 = sans plafond");
        assertEquals("Limite atteinte : 3 parties gagnées aujourd'hui. Prochaine partie avec mise possible demain à 00:00.", WinWindows.reached(w, 3, 3, 3, 3, 10, 15).get().text());
        assertEquals("Limite atteinte : 1 partie gagnée aujourd'hui. Prochaine partie avec mise possible demain à 00:00.", WinWindows.reached(w, 1, 1, 1, 1, 0, 0).get().text());
        assertEquals("Limite atteinte : 10 parties gagnées cette semaine. Prochaine partie avec mise possible lundi 05/10 à 00:00.", WinWindows.reached(w, 0, 10, 10, 3, 10, 15).get().text());
        WinWindows.Reached all = WinWindows.reached(w, 3, 10, 15, 3, 10, 15).get();
        assertEquals("MONTH", all.window());
        assertEquals("Limite atteinte : 15 parties gagnées ce mois-ci. Prochaine partie avec mise possible le 01/11 à 00:00.", all.text());
        assertEquals(w.nextMonth(), all.reopensAt());
        assertFalse(WinWindows.reached(w, 3, 3, 3, 4, 11, 16).isPresent());
    }
}
