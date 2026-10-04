package castbridge.server.wallet.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class GrantScheduleTest {
    static final String ID = "AAAA-0000-0000-0001";
    static final Instant ANCHOR = Instant.parse("2026-01-01T00:00:00Z");
    static final WalletPolicy POL = WalletPolicy.defaults();

    static Instant day(long d) { return ANCHOR.plus(d, ChronoUnit.DAYS); }

    static EditionSpan span(Edition e, long from, Long to) { return new EditionSpan(e, day(from), to == null ? null : day(to)); }

    static List<GrantSchedule.Due> due(List<EditionSpan> spans, long now, Set<String> already) {
        return GrantSchedule.due(ID, POL, ANCHOR, spans, already, day(now));
    }

    static String render(GrantSchedule.Due d) {
        String suffix = d.key().substring(("grant:" + ID + ":").length());
        return suffix + "|" + d.amounts().stream().map(a -> a.currency() + "=" + a.amount()).collect(Collectors.joining(","));
    }

    @Test
    void sharedVectors() throws Exception {
        JsonNode cases = PotSplitVectorsTest.vectors().get("grantSchedule");
        assertTrue(cases.size() >= 10);
        for (JsonNode c : cases) {
            List<EditionSpan> spans = new ArrayList<>();
            for (JsonNode s : c.get("spans")) spans.add(span(Edition.valueOf(s.get(0).asText()), s.get(1).asLong(), s.get(2).isNull() ? null : s.get(2).asLong()));
            Set<String> already = new HashSet<>();
            for (JsonNode a : c.get("already")) already.add("grant:" + ID + ":" + a.asText());
            List<GrantSchedule.Due> got = due(spans, c.get("now").asLong(), already);
            String name = c.get("name").asText();
            if (c.has("expectCount")) {
                long count = got.stream().flatMap(d -> d.amounts().stream()).count();
                assertEquals(c.get("expectCount").asLong(), count, name);
            } else {
                List<String> expect = new ArrayList<>();
                for (JsonNode e : c.get("expect")) expect.add(e.asText());
                assertEquals(expect, got.stream().map(GrantScheduleTest::render).toList(), name);
            }
        }
    }

    private static long trancheCount(List<GrantSchedule.Due> dues, Currency c) {
        return dues.stream().filter(d -> d.key().contains(":" + c + ":p")).count();
    }

    @Test
    void trialSevenDaysIsOneTranche() {
        assertEquals(1, trancheCount(due(List.of(span(Edition.TRIAL, 0, 7L)), 7, Set.of()), Currency.NDEM));
    }

    @Test
    void productionPeriodsMatchTheRuleOfMonths() {
        // N = ceil(jours / 30) : 90 j ⇒ 3 ; 365 j ⇒ 13
        assertEquals(3, trancheCount(due(List.of(span(Edition.PRODUCTION, 0, 90L)), 400, Set.of()), Currency.NDEM));
        assertEquals(3, trancheCount(due(List.of(span(Edition.PRODUCTION, 0, 90L)), 400, Set.of()), Currency.MBOKO));
        assertEquals(13, trancheCount(due(List.of(span(Edition.PRODUCTION, 0, 365L)), 500, Set.of()), Currency.NDEM));
        for (int days = 1; days <= 400; days++) {
            long expected = (days + 29) / 30;
            assertEquals(expected, trancheCount(due(List.of(span(Edition.PRODUCTION, 0, (long) days)), 1000, Set.of()), Currency.MBOKO), days + " jours");
        }
    }

    @Test
    void unlimitedGivesOpeningPlusOneTranchePerPeriodAndOpeningOnlyOnce() {
        List<GrantSchedule.Due> d = due(List.of(span(Edition.UNLIMITED, 0, null)), 65, Set.of());
        assertEquals("open-unlimited|NDEM=5000,MBOKO=50", render(d.get(0)));
        assertEquals(3, trancheCount(d, Currency.NDEM));
        Set<String> keys = d.stream().map(GrantSchedule.Due::key).collect(Collectors.toSet());
        // une autre clé illimitée plus tard : l'ouverture ne se redonne pas
        List<GrantSchedule.Due> later = due(List.of(span(Edition.UNLIMITED, 0, null), span(Edition.UNLIMITED, 200, null)), 200, keys);
        assertFalse(later.stream().anyMatch(x -> x.key().endsWith("open-unlimited")));
    }

    @Test
    void trialThenProductionGivesProductionAmountsFromTheFirstCoveredPeriod() {
        List<String> r = due(List.of(span(Edition.TRIAL, 0, 30L), span(Edition.PRODUCTION, 30, 120L)), 95, Set.of()).stream().map(GrantScheduleTest::render).toList();
        assertEquals("NDEM:p0|NDEM=100", r.get(0));
        assertEquals("NDEM:p1|NDEM=1000", r.get(1));
        assertEquals("MBOKO:p1|MBOKO=10", r.get(2));
    }

    @Test
    void overlappingKeysNeverGiveTwoTranchesForOnePeriod() {
        Random rnd = new Random(11);
        Edition[] eds = {Edition.TRIAL, Edition.PRODUCTION, Edition.UNLIMITED};
        for (int i = 0; i < 2000; i++) {
            List<EditionSpan> spans = new ArrayList<>();
            int n = 1 + rnd.nextInt(4);
            for (int j = 0; j < n; j++) {
                long from = rnd.nextInt(300);
                spans.add(span(eds[rnd.nextInt(3)], from, rnd.nextBoolean() ? null : from + 1 + rnd.nextInt(200)));
            }
            List<GrantSchedule.Due> d = due(spans, rnd.nextInt(600), Set.of());
            Set<String> keys = new HashSet<>();
            for (GrantSchedule.Due x : d) assertTrue(keys.add(x.key()), "clé en double : " + x.key() + " " + spans);
            // exactement une clé par (monnaie, période) : le nombre de clés distinctes = le nombre de couples
            assertEquals(d.size(), keys.size());
        }
    }

    @Test
    void tvSyncedAfterThreeMonthsOfflineGetsAllDueTranchesExactlyOnce() {
        MemoryLedger l = new MemoryLedger();
        List<EditionSpan> spans = List.of(span(Edition.PRODUCTION, 0, 365L));
        Set<String> posted = new HashSet<>();
        List<GrantSchedule.Due> d = due(spans, 100, posted);
        assertEquals(8, d.size()); // 4 périodes (J0, J30, J60, J90) × 2 monnaies
        for (GrantSchedule.Due x : d) { l.post(x.toTxn(ID)); posted.add(x.key()); }
        assertEquals(4000, l.balance(AccountRef.dispo(ID, Currency.NDEM)));
        assertEquals(40, l.balance(AccountRef.dispo(ID, Currency.MBOKO)));
        // deuxième synchronisation au même instant : rien de plus ; et même sans la mémoire de l'appelant, le grand livre rejoue sans doubler
        assertTrue(due(spans, 100, posted).isEmpty());
        for (GrantSchedule.Due x : d) assertTrue(l.post(x.toTxn(ID)).replayed());
        assertEquals(4000, l.balance(AccountRef.dispo(ID, Currency.NDEM)));
        // plus tard : seulement les nouvelles périodes
        List<GrantSchedule.Due> next = due(spans, 130, posted);
        assertEquals(2, next.size());
        assertEquals("NDEM:p4|NDEM=1000", render(next.get(0)));
    }

    @Test
    void superAndNoneGetNothing() {
        assertTrue(due(List.of(span(Edition.SUPER, 0, null)), 500, Set.of()).isEmpty());
        assertTrue(due(List.of(span(Edition.NONE, 0, null)), 500, Set.of()).isEmpty());
        assertTrue(due(List.of(), 500, Set.of()).isEmpty());
    }

    @Test
    void revocationAtTGivesNoTrancheForAPeriodStartingAfterT() {
        Random rnd = new Random(5);
        for (int i = 0; i < 1000; i++) {
            long t = 1 + rnd.nextInt(400);
            List<GrantSchedule.Due> d = due(List.of(span(Edition.PRODUCTION, 0, t)), 1000, Set.of());
            for (GrantSchedule.Due x : d) {
                int k = Integer.parseInt(x.key().substring(x.key().lastIndexOf('p') + 1));
                assertTrue(30L * k < t, "période " + k + " commence après la révocation à J" + t);
            }
        }
    }

    @Test
    void amountsComeFromThePolicyOnly() {
        WalletPolicy custom = new WalletPolicy(1000, 0, 1, 1000, 1, 100, 10_000, 100, 30, 7, 11, 3, 13, 4, 17, 5);
        List<GrantSchedule.Due> d = GrantSchedule.due(ID, custom, ANCHOR, List.of(span(Edition.UNLIMITED, 0, null)), Set.of(), day(1));
        assertEquals("open-unlimited|NDEM=17,MBOKO=5", render(d.get(0)));
        assertEquals("NDEM:p0|NDEM=13", render(d.get(1)));
        assertEquals("MBOKO:p0|MBOKO=4", render(d.get(2)));
    }
}
