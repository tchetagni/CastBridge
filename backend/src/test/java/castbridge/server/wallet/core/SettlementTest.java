package castbridge.server.wallet.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class SettlementTest {
    static final String[] IDS = {"AAAA-0000-0000-0001", "BBBB-0000-0000-0002", "CCCC-0000-0000-0003", "DDDD-0000-0000-0004"};

    @Test
    void sumPayEqualsSumUsedOver5000Draws() {
        Random r = new Random(99);
        for (int i = 0; i < 5000; i++) {
            long per = 1 + r.nextInt(100);
            int tvs = 1 + r.nextInt(4);
            List<Settlement.Escrow> es = new ArrayList<>();
            List<Settlement.Seat> seats = new ArrayList<>();
            for (int t = 0; t < tvs; t++) {
                int k = 1 + r.nextInt(4);
                es.add(new Settlement.Escrow("e" + t, IDS[t], k, per * k));
                int present = r.nextInt(k + 2); // parfois plus de sièges que de places misées : plafonné à k
                for (int s = 0; s < present; s++) seats.add(new Settlement.Seat("e" + t, s, r.nextInt(4)));
            }
            for (Settlement.Kind kind : Settlement.Kind.values()) {
                List<Settlement.Line> lines = Settlement.compute(Currency.NDEM, per, es, seats, kind);
                long used = lines.stream().mapToLong(Settlement.Line::used).sum(), pay = lines.stream().mapToLong(Settlement.Line::pay).sum();
                assertEquals(used, pay, "tirage " + i + " " + kind);
                for (int t = 0; t < tvs; t++) {
                    long present = Math.min(es.get(t).k(), seatsOf(seats, "e" + t));
                    assertEquals(per * present, lines.get(t).used(), "used = per × min(k, présents)");
                    if (kind == Settlement.Kind.ABORT) assertEquals(lines.get(t).used(), lines.get(t).pay(), "ABORT rend tout");
                    assertTrue(lines.get(t).used() <= lines.get(t).amount());
                }
                Settlement.check(lines, es);
            }
        }
    }

    private static long seatsOf(List<Settlement.Seat> seats, String eid) { return seats.stream().filter(s -> s.eid().equals(eid)).count(); }

    @Test
    void ledgerKeepsMassOver5000SettledGames() {
        Random r = new Random(123);
        for (int i = 0; i < 5000; i++) {
            MemoryLedger l = new MemoryLedger();
            long per = 1 + r.nextInt(60);
            int tvs = 2 + r.nextInt(3);
            List<Settlement.Escrow> es = new ArrayList<>();
            List<Settlement.Seat> seats = new ArrayList<>();
            for (int t = 0; t < tvs; t++) {
                int k = 1 + r.nextInt(3);
                l.post(Txn.grant(IDS[t], Currency.MBOKO, per * 3, "g" + t));
                l.post(Txn.lock(IDS[t], Currency.MBOKO, per, k, "e" + t));
                es.add(new Settlement.Escrow("e" + t, IDS[t], k, per * k));
                for (int s = 0, present = r.nextInt(k + 1); s < present; s++) seats.add(new Settlement.Seat("e" + t, s, r.nextInt(3)));
            }
            List<Settlement.Line> lines = Settlement.compute(Currency.MBOKO, per, es, seats, i % 5 == 0 ? Settlement.Kind.ABORT : Settlement.Kind.END);
            l.post(Txn.settle("r", lines, Currency.MBOKO));
            long total = 0;
            for (int t = 0; t < tvs; t++) total += l.balance(AccountRef.dispo(IDS[t], Currency.MBOKO));
            assertEquals(per * 3 * tvs, total, "les joueurs détiennent exactement ce qu'ils avaient : aucune création");
            assertEquals(0, l.sum(Currency.MBOKO));
        }
    }

    @Test
    void abortGivesEverythingBack() {
        List<Settlement.Escrow> es = List.of(new Settlement.Escrow("e0", IDS[0], 2, 40), new Settlement.Escrow("e1", IDS[1], 1, 20));
        List<Settlement.Seat> seats = List.of(new Settlement.Seat("e0", 0, 9), new Settlement.Seat("e0", 1, 1), new Settlement.Seat("e1", 0, 0));
        List<Settlement.Line> lines = Settlement.compute(Currency.NDEM, 20, es, seats, Settlement.Kind.ABORT);
        assertEquals(40, lines.get(0).pay());
        assertEquals(20, lines.get(1).pay());
    }

    @Test
    void nobodyScoredGivesStakesBack() {
        List<Settlement.Escrow> es = List.of(new Settlement.Escrow("e0", IDS[0], 1, 20), new Settlement.Escrow("e1", IDS[1], 1, 20));
        List<Settlement.Seat> seats = List.of(new Settlement.Seat("e0", 0, 0), new Settlement.Seat("e1", 0, 0));
        List<Settlement.Line> lines = Settlement.compute(Currency.NDEM, 20, es, seats, Settlement.Kind.END);
        assertEquals(20, lines.get(0).pay());
        assertEquals(20, lines.get(1).pay());
    }

    @Test
    void absentSeatsAreNotChargedAndTheRestIsReturned() {
        // k = 3 sièges misés mais un seul présent : used = 1 mise ; les 2 autres reviennent en DISPO au règlement
        List<Settlement.Escrow> es = List.of(new Settlement.Escrow("e0", IDS[0], 3, 30), new Settlement.Escrow("e1", IDS[1], 1, 10));
        List<Settlement.Seat> seats = List.of(new Settlement.Seat("e0", 0, 5), new Settlement.Seat("e1", 0, 3));
        List<Settlement.Line> lines = Settlement.compute(Currency.NDEM, 10, es, seats, Settlement.Kind.END);
        assertEquals(10, lines.get(0).used());
        assertEquals(14, lines.get(0).pay()); // 20 × 70 %
        assertEquals(6, lines.get(1).pay());
    }

    @Test
    void checkRefusesUnknownDuplicateAndUnbalanced() {
        List<Settlement.Escrow> es = List.of(new Settlement.Escrow("e0", IDS[0], 1, 10), new Settlement.Escrow("e1", IDS[1], 1, 10));
        Settlement.Line a = new Settlement.Line("e0", IDS[0], 10, 10, 15), b = new Settlement.Line("e1", IDS[1], 10, 10, 5);
        Settlement.check(List.of(a, b), es); // conforme
        assertEquals(WalletReason.ESCROW_UNKNOWN, assertThrows(LedgerException.class, () -> Settlement.check(List.of(a, new Settlement.Line("zz", IDS[1], 10, 10, 5)), es)).reason());
        assertThrows(LedgerException.class, () -> Settlement.check(List.of(a, a), es));
        assertEquals(WalletReason.UNBALANCED, assertThrows(LedgerException.class, () -> Settlement.check(List.of(a, new Settlement.Line("e1", IDS[1], 10, 10, 6)), es)).reason());
        assertThrows(LedgerException.class, () -> Settlement.check(List.of(new Settlement.Line("e0", IDS[0], 10, 11, 11)), es)); // used > amount
        assertThrows(LedgerException.class, () -> Settlement.check(List.of(new Settlement.Line("e0", IDS[1], 10, 10, 10)), es)); // mauvais titulaire
    }
}
