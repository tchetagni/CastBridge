package castbridge.server.wallet.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import castbridge.server.wallet.core.Currency;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Propriété de conservation : des centaines d'opérations aléatoires (blocages, règlements valides, truqués ou rejoués, abandons, échéances, conversions, transferts, rejeux) sur plusieurs TV ;
 * à chaque étape les invariants I-1, I-3 et I-8 de la réconciliation tiennent, aucun solde n'est négatif, et un règlement ne change jamais la somme détenue par ses participants.
 */
class OpsConservationTest extends OpsTestBase {

    private long held(List<Tv> tvs, Currency c) {
        long s = 0;
        for (Tv t : tvs) s += bal(t, c) + locked(t, c);
        return s;
    }

    @Test
    void randomOperationsKeepTheLedgerConservativeAndNeverNegative() throws Exception {
        Random rnd = new Random(20261004L);
        List<Tv> tvs = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Tv t = productionTv();
            adminGrant(t.code(), "NDEM", 5_000);
            tvs.add(t);
        }
        int rid = 1000, idem = 0, refusedSettles = 0, settled = 0, refunded = 0;
        for (int step = 0; step < 160; step++) {
            int op = rnd.nextInt(10);
            if (op <= 3) {
                // une partie : 2 ou 3 TV, même monnaie, même mise
                String cur = rnd.nextInt(3) == 0 ? "MBOKO" : "NDEM";
                long per = cur.equals("NDEM") ? 1 + rnd.nextInt(50) : 1 + rnd.nextInt(2);
                int n = 2 + rnd.nextInt(2);
                List<Tv> players = new ArrayList<>(tvs);
                java.util.Collections.shuffle(players, rnd);
                players = players.subList(0, n);
                List<Line> lines = new ArrayList<>();
                long sumUsed = 0;
                List<String> eids = new ArrayList<>();
                List<Integer> present = new ArrayList<>();
                boolean allLocked = true;
                List<Tv> locked = new ArrayList<>();
                for (Tv p : players) {
                    int k = 1 + rnd.nextInt(3);
                    Reply e = escrow(p, cur, per, k, "g-" + (idem++));
                    if (e.status() != 200) {
                        assertEquals(409, e.status(), e.json().toString());
                        assertEquals("INSUFFICIENT", e.reason());
                        allLocked = false;
                        continue;
                    }
                    int pr = 1 + rnd.nextInt(k);
                    eids.add(e.json().get("eid").asText());
                    present.add(pr);
                    locked.add(p);
                    sumUsed += per * pr;
                }
                if (locked.isEmpty()) continue;
                Currency c = Currency.valueOf(cur);
                long before = held(tvs, c);
                boolean abort = rnd.nextInt(4) == 0;
                long remaining = sumUsed;
                for (int i = 0; i < locked.size(); i++) {
                    long used = abort ? 0 : per * present.get(i);
                    long pay = abort ? 0 : (i == locked.size() - 1 ? remaining : (remaining == 0 ? 0 : (long) rnd.nextInt((int) Math.min(remaining, Integer.MAX_VALUE) + 1)));
                    if (!abort) remaining -= pay;
                    lines.add(new Line(eids.get(i), locked.get(i).code(), used, pay));
                }
                int mode = rnd.nextInt(6);
                String r = "r" + (rid++);
                String ridHex = rid(rid);
                if (mode == 0) {
                    // truqué : un utilisé payé en trop
                    List<Line> bad = new ArrayList<>(lines);
                    Line l0 = bad.get(0);
                    bad.set(0, new Line(l0.eid(), l0.id(), l0.used(), l0.pay() + 1));
                    assertTrue(settle(cbr1(RESULT, ridHex, cur, per, abort ? "ABORT" : "END", bad)).status() >= 400);
                    refusedSettles++;
                    assertEquals(before, held(tvs, c), "un résultat refusé ne bouge rien");
                } else if (mode == 1 && allLocked) {
                    // laissé sans résultat : rendu à l'échéance
                    clock.freezeAt(clock.now().plus(Duration.ofHours(7)));
                    for (Tv p : locked) sync(p);
                    refunded += locked.size();
                    Reply late = settle(cbr1(RESULT, ridHex, cur, per, abort ? "ABORT" : "END", lines));
                    assertEquals(409, late.status());
                    assertEquals("RESULT_AFTER_REFUND", late.reason());
                } else {
                    String tok = cbr1(RESULT, ridHex, cur, per, abort ? "ABORT" : "END", lines);
                    Reply s = settle(tok);
                    assertEquals(200, s.status(), s.json().toString());
                    settled++;
                    assertEquals(before, held(tvs, c), "un règlement conserve la somme des participants");
                    if (rnd.nextBoolean()) assertEquals(s.json(), settle(tok).json());
                }
            } else if (op == 4 || op == 5) {
                Tv t = tvs.get(rnd.nextInt(tvs.size()));
                String dir = rnd.nextBoolean() ? "N2M" : "M2N";
                Reply r = convert(t, dir, 1 + rnd.nextInt(3), "c-" + (idem++));
                assertTrue(r.status() == 200 || (r.status() == 409 && r.reason().equals("INSUFFICIENT")), r.json().toString());
            } else if (op <= 8) {
                Tv from = tvs.get(rnd.nextInt(tvs.size()));
                Tv to = tvs.get(rnd.nextInt(tvs.size()));
                if (from == to) continue;
                String cur = rnd.nextInt(4) == 0 ? "MBOKO" : "NDEM";
                String code = receiveCode(to).json().get("code").asText();
                String key = "x-" + (idem++);
                long amt = 1 + rnd.nextInt(cur.equals("NDEM") ? 400 : 3);
                Reply r = transfer(from, code, cur, amt, key);
                assertTrue(r.status() == 200 || r.status() == 409 || r.status() == 429, r.json().toString());
                if (r.status() == 200) assertEquals(200, transfer(from, code, cur, amt, key).status(), "rejeu");
            } else {
                clock.freezeAt(clock.now().plus(Duration.ofMinutes(1 + rnd.nextInt(20))));
            }
            if (step % 20 == 19) assertReconciled();
            for (Currency c : Currency.values()) {
                for (Tv t : tvs) {
                    assertTrue(bal(t, c) >= 0 && locked(t, c) >= 0);
                }
            }
        }
        assertReconciled();
        assertEquals(0, ledger.sum(Currency.NDEM));
        assertEquals(0, ledger.sum(Currency.MBOKO));
        assertEquals(0, sys("SYS:POT", "NDEM"));
        assertEquals(0, sys("SYS:POT", "MBOKO"));
        assertTrue(settled > 5, "la propriété a vraiment réglé des parties : " + settled);
        assertTrue(refusedSettles > 0 && refunded > 0, "refus et échéances exercés");
        // plus aucun blocage ouvert après que tout a été réglé ou échu
        clock.freezeAt(clock.now().plus(Duration.ofHours(8)));
        for (Tv t : tvs) sync(t);
        assertEquals(0, count("SELECT COUNT(*) FROM wallet_escrow WHERE state = 'OPEN'"));
        assertReconciled();
    }
}
