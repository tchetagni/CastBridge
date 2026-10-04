package castbridge.server.wallet.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** Tests de propriétés du grand livre : 10 000 suites aléatoires (graines 1..10 000), invariants I-1 à I-4 et I-6 vérifiés après chaque opération. */
class LedgerPropertyTest {
    static final String[] IDS = {"AAAA-0000-0000-0001", "BBBB-0000-0000-0002", "CCCC-0000-0000-0003", "DDDD-0000-0000-0004", "EEEE-0000-0000-0005"};
    static final int SEEDS = 10_000;

    record Game(Currency cur, long per, List<Settlement.Escrow> escrows) {}

    /** Compteurs de couverture (pour que le test ne puisse pas passer à vide). */
    static final class Stats {
        long ops, grants, converts, transfers, locks, settles, refunds, replays, conflicts, insufficients, doubleCloses, fees;
    }

    @Test
    void conservationHoldsForRandomSequences() {
        Stats st = new Stats();
        for (long seed = 1; seed <= SEEDS; seed++) runSequence(seed, st);
        assertTrue(st.grants > 10_000 && st.converts > 10_000 && st.transfers > 10_000, "couverture : attributions, conversions, transferts");
        assertTrue(st.locks > 5_000 && st.settles > 2_000 && st.refunds > 1_000, "couverture : blocages, règlements, rendus");
        assertTrue(st.replays > 5_000 && st.conflicts > 2_000 && st.insufficients > 2_000 && st.doubleCloses > 500 && st.fees > 100, "couverture : rejeux, conflits, refus, fermetures doubles, frais");
    }

    // ---- une suite ----

    private static void runSequence(long seed, Stats st) {
        Random r = new Random(seed);
        long rate = r.nextInt(10) < 6 ? 1000 : 1 + r.nextInt(5000);
        int fee = r.nextInt(10) < 6 ? 0 : r.nextInt(2001);
        WalletPolicy pol = WalletPolicy.defaults().withRate(rate).withReverseFeeBp(fee);
        MemoryLedger l = new MemoryLedger();
        List<Txn> posted = new ArrayList<>();
        List<Game> games = new ArrayList<>();
        AtomicLong counter = new AtomicLong();
        int n = 1 + r.nextInt(200);
        for (int i = 0; i < n; i++) {
            String ctx = "graine " + seed + ", opération " + i;
            st.ops++;
            switch (r.nextInt(14)) {
                case 0, 1 -> { post(l, posted, Txn.grant(id(r), cur(r), 1 + r.nextInt(3000), "g" + counter.incrementAndGet()), ctx); st.grants++; }
                case 2 -> post(l, posted, Txn.voucher(id(r), cur(r), 1 + r.nextInt(500), "n" + counter.incrementAndGet()), ctx);
                case 3, 4 -> {
                    try {
                        post(l, posted, Txn.convert(id(r), Conversion.Direction.N2M, 1 + r.nextInt(5), pol, "c" + counter.incrementAndGet()), ctx);
                    } catch (LedgerException e) { assertEquals(WalletReason.INSUFFICIENT, e.reason(), ctx); st.insufficients++; }
                    st.converts++;
                }
                case 5, 6 -> {
                    try {
                        post(l, posted, Txn.convert(id(r), Conversion.Direction.M2N, 1 + r.nextInt(5), pol, "c" + counter.incrementAndGet()), ctx);
                    } catch (LedgerException e) { assertEquals(WalletReason.INSUFFICIENT, e.reason(), ctx); st.insufficients++; }
                    st.converts++;
                }
                case 7 -> {
                    String a = id(r), b = id(r);
                    if (!a.equals(b)) {
                        try { post(l, posted, Txn.transfer(a, b, cur(r), 1 + r.nextInt(800), "x" + counter.incrementAndGet()), ctx); }
                        catch (LedgerException e) { assertEquals(WalletReason.INSUFFICIENT, e.reason(), ctx); st.insufficients++; }
                        st.transfers++;
                    }
                }
                case 8 -> { openGame(r, l, posted, games, counter, st, ctx); }
                case 9 -> { settleGame(r, l, posted, games, counter, st, ctx); }
                case 10 -> { refundOne(r, l, posted, games, st, ctx); }
                case 11 -> {
                    long delta = r.nextInt(2001) - 1000;
                    if (delta == 0) delta = 7;
                    try { post(l, posted, Txn.adjust(id(r), cur(r), delta, "a" + counter.incrementAndGet()), ctx); }
                    catch (LedgerException e) { assertEquals(WalletReason.INSUFFICIENT, e.reason(), ctx); st.insufficients++; }
                }
                case 12 -> hostile(r, l, posted, games, st, ctx);
                default -> hostile(r, l, posted, games, st, ctx);
            }
            checkInvariants(l, rate, ctx);
        }
        if (fee > 0) st.fees++;
    }

    private static void openGame(Random r, MemoryLedger l, List<Txn> posted, List<Game> games, AtomicLong counter, Stats st, String ctx) {
        Currency c = cur(r);
        long per = 1 + r.nextInt(50);
        List<Settlement.Escrow> es = new ArrayList<>();
        int tvs = 2 + r.nextInt(2);
        for (int t = 0; t < tvs; t++) {
            String id = id(r);
            int k = 1 + r.nextInt(4);
            String eid = "lock:" + id + ":" + counter.incrementAndGet();
            try {
                post(l, posted, Txn.lock(id, c, per, k, eid), ctx);
                es.add(new Settlement.Escrow(eid, id, k, per * k));
                st.locks++;
            } catch (LedgerException e) { assertEquals(WalletReason.INSUFFICIENT, e.reason(), ctx); st.insufficients++; }
        }
        if (!es.isEmpty()) games.add(new Game(c, per, es));
    }

    private static List<Settlement.Escrow> openOnes(MemoryLedger l, Game g) {
        List<Settlement.Escrow> open = new ArrayList<>();
        for (Settlement.Escrow e : g.escrows()) if (l.escrow(e.eid()).orElseThrow().status() == Ledger.EscrowStatus.OPEN) open.add(e);
        return open;
    }

    private static void settleGame(Random r, MemoryLedger l, List<Txn> posted, List<Game> games, AtomicLong counter, Stats st, String ctx) {
        if (games.isEmpty()) return;
        Game g = games.get(r.nextInt(games.size()));
        List<Settlement.Escrow> open = openOnes(l, g);
        if (open.isEmpty()) return;
        List<Settlement.Seat> seats = new ArrayList<>();
        for (Settlement.Escrow e : open) {
            int present = r.nextInt(e.k() + 1);
            for (int s = 0; s < present; s++) seats.add(new Settlement.Seat(e.eid(), s, r.nextInt(4)));
        }
        List<Settlement.Line> lines = Settlement.compute(g.cur(), g.per(), open, seats, r.nextInt(4) == 0 ? Settlement.Kind.ABORT : Settlement.Kind.END);
        post(l, posted, Txn.settle("r" + counter.incrementAndGet(), lines, g.cur()), ctx);
        st.settles++;
    }

    private static void refundOne(Random r, MemoryLedger l, List<Txn> posted, List<Game> games, Stats st, String ctx) {
        if (games.isEmpty()) return;
        Game g = games.get(r.nextInt(games.size()));
        List<Settlement.Escrow> open = openOnes(l, g);
        if (open.isEmpty()) return;
        Settlement.Escrow e = open.get(r.nextInt(open.size()));
        post(l, posted, Txn.refund(e.eid(), e.id(), g.cur(), e.amount()), ctx);
        st.refunds++;
    }

    /** Opérations hostiles : rejeu (I-4), clé en conflit, découvert, blocage fermé deux fois (I-6) : le grand livre ne doit pas bouger. */
    private static void hostile(Random r, MemoryLedger l, List<Txn> posted, List<Game> games, Stats st, String ctx) {
        Map<AccountRef, Long> before = l.snapshot();
        List<Ledger.EscrowState> escBefore = l.escrows();
        switch (r.nextInt(4)) {
            case 0 -> {
                if (posted.isEmpty()) return;
                Txn t = posted.get(r.nextInt(posted.size()));
                assertTrue(l.post(t).replayed(), ctx + " : un rejeu doit être signalé");
                st.replays++;
            }
            case 1 -> {
                if (posted.isEmpty()) return;
                Txn t = posted.get(r.nextInt(posted.size()));
                List<Entry> doubled = new ArrayList<>();
                for (Entry e : t.entries()) doubled.add(new Entry(e.account(), e.amount() * 2));
                Txn other = new Txn(t.kind(), t.idemKey(), doubled, t.refs());
                LedgerException ex = assertThrows(LedgerException.class, () -> l.post(other), ctx + " : même clé, autre contenu");
                assertEquals(WalletReason.IDEM_CONFLICT, ex.reason(), ctx);
                st.conflicts++;
            }
            case 2 -> {
                String a = id(r), b = id(r);
                if (a.equals(b)) return;
                Currency c = cur(r);
                long have = l.balance(AccountRef.dispo(a, c));
                Txn t = Txn.transfer(a, b, c, have + 1, "over:" + r.nextLong());
                LedgerException ex = assertThrows(LedgerException.class, () -> l.post(t), ctx + " : découvert");
                assertEquals(WalletReason.INSUFFICIENT, ex.reason(), ctx);
                st.insufficients++;
            }
            default -> {
                for (Game g : games) for (Settlement.Escrow e : g.escrows()) {
                    Ledger.EscrowState s = l.escrow(e.eid()).orElseThrow();
                    if (s.status() == Ledger.EscrowStatus.OPEN) continue;
                    Txn second = s.status() == Ledger.EscrowStatus.SETTLED
                            ? Txn.refund(e.eid(), e.id(), g.cur(), e.amount())
                            : Txn.settle("late:" + e.eid(), List.of(new Settlement.Line(e.eid(), e.id(), e.amount(), e.amount(), e.amount())), g.cur());
                    LedgerException ex = assertThrows(LedgerException.class, () -> l.post(second), ctx + " : un blocage ne sert qu'une fois");
                    assertEquals(WalletReason.ESCROW_CLOSED, ex.reason(), ctx);
                    st.doubleCloses++;
                    assertEquals(before, l.snapshot(), ctx);
                    assertEquals(escBefore, l.escrows(), ctx);
                    return;
                }
                return;
            }
        }
        assertEquals(before, l.snapshot(), ctx + " : rien ne doit changer");
        assertEquals(escBefore, l.escrows(), ctx + " : les blocages non plus");
    }

    private static void post(MemoryLedger l, List<Txn> posted, Txn t, String ctx) {
        Ledger.Posted p = l.post(t);
        assertFalse(p.replayed(), ctx + " : première pose");
        posted.add(t);
    }

    private static void checkInvariants(MemoryLedger l, long rate, String ctx) {
        for (Currency c : Currency.values()) assertEquals(0, l.sum(c), ctx + " : I-1 conservation " + c);
        long[] players = new long[2], system = new long[2], bloque = new long[2];
        l.forEachBalance((a, v) -> {
            int i = a.currency().ordinal();
            if (a.isSystem()) system[i] += v;
            else {
                assertTrue(v >= 0, ctx + " : I-2 solde négatif " + a);
                players[i] += v;
                if (a.pocket() == Pocket.BLOQUE) bloque[i] += v;
            }
        });
        for (Currency c : Currency.values()) {
            assertEquals(-system[c.ordinal()], players[c.ordinal()], ctx + " : I-3 masse en circulation = −Σ comptes système " + c);
            long open = 0;
            for (Ledger.EscrowState s : l.escrows()) if (s.currency() == c && s.status() == Ledger.EscrowStatus.OPEN) open += s.amount();
            assertEquals(open, bloque[c.ordinal()], ctx + " : BLOQUE = Σ blocages ouverts " + c);
            assertEquals(0, l.balance(AccountRef.sys(AccountRef.POT, c)), ctx + " : SYS:POT revient à 0");
        }
        long convN = l.balance(AccountRef.sys(AccountRef.CONVERT, Currency.NDEM)), convM = l.balance(AccountRef.sys(AccountRef.CONVERT, Currency.MBOKO));
        assertEquals(-rate * convM, convN, ctx + " : I-3 valeur conservée au taux, dans les deux sens");
        assertTrue(l.balance(AccountRef.sys(AccountRef.FEE, Currency.NDEM)) >= 0, ctx + " : SYS:FEE ≥ 0");
        assertEquals(0, l.balance(AccountRef.sys(AccountRef.FEE, Currency.MBOKO)), ctx);
    }

    private static String id(Random r) { return IDS[r.nextInt(IDS.length)]; }

    private static Currency cur(Random r) { return r.nextBoolean() ? Currency.NDEM : Currency.MBOKO; }

    // ---- cas déterministes ----

    @Test
    void refusedTransactionWritesNothing() {
        MemoryLedger l = new MemoryLedger();
        l.post(Txn.grant(IDS[0], Currency.NDEM, 100, "g1"));
        Map<AccountRef, Long> before = l.snapshot();
        // trois comptes touchés, le dernier seulement est à découvert : aucune écriture ne doit subsister
        Txn t = new Txn(TxnKind.ADJUST, "multi", List.of(
                new Entry(AccountRef.dispo(IDS[1], Currency.NDEM), 50),
                new Entry(AccountRef.dispo(IDS[0], Currency.NDEM), -150),
                new Entry(AccountRef.sys(AccountRef.ADJUST, Currency.NDEM), 100)), List.of());
        LedgerException ex = assertThrows(LedgerException.class, () -> l.post(t));
        assertEquals(WalletReason.INSUFFICIENT, ex.reason());
        assertEquals(before, l.snapshot());
        assertEquals("Solde insuffisant : 100 NDEM disponibles", ex.getMessage());
        // la clé n'a pas été consommée : la même clé avec un contenu valide passe
        assertFalse(l.post(new Txn(TxnKind.ADJUST, "multi", List.of(new Entry(AccountRef.dispo(IDS[0], Currency.NDEM), 5), new Entry(AccountRef.sys(AccountRef.ADJUST, Currency.NDEM), -5)), List.of())).replayed());
    }

    @Test
    void unbalancedTransactionIsRefused() {
        LedgerException ex = assertThrows(LedgerException.class, () -> new Txn(TxnKind.ADJUST, "k", List.of(new Entry(AccountRef.dispo(IDS[0], Currency.NDEM), 5)), List.of()));
        assertEquals(WalletReason.UNBALANCED, ex.reason());
        // équilibré en NDEM mais pas en MBOKO
        LedgerException ex2 = assertThrows(LedgerException.class, () -> new Txn(TxnKind.ADJUST, "k", List.of(
                new Entry(AccountRef.dispo(IDS[0], Currency.NDEM), 5), new Entry(AccountRef.sys(AccountRef.ADJUST, Currency.NDEM), -5),
                new Entry(AccountRef.dispo(IDS[0], Currency.MBOKO), 1)), List.of()));
        assertEquals(WalletReason.UNBALANCED, ex2.reason());
    }

    @Test
    void escrowSettleIsAllOrNothingAndOnce() {
        MemoryLedger l = new MemoryLedger();
        l.post(Txn.grant(IDS[0], Currency.MBOKO, 50, "g0"));
        l.post(Txn.grant(IDS[1], Currency.MBOKO, 50, "g1"));
        l.post(Txn.lock(IDS[0], Currency.MBOKO, 10, 2, "lock:a:1"));
        l.post(Txn.lock(IDS[1], Currency.MBOKO, 10, 3, "lock:b:1"));
        List<Settlement.Escrow> es = List.of(new Settlement.Escrow("lock:a:1", IDS[0], 2, 20), new Settlement.Escrow("lock:b:1", IDS[1], 3, 30));
        List<Settlement.Line> lines = Settlement.compute(Currency.MBOKO, 10, es,
                List.of(new Settlement.Seat("lock:a:1", 0, 5), new Settlement.Seat("lock:b:1", 0, 3), new Settlement.Seat("lock:b:1", 1, 0)), Settlement.Kind.END);
        // a : 1 siège utilisé (10) ; b : 2 sièges utilisés (20) ; cagnotte 30 ; a gagne 70 % (21), b 30 % (9)
        assertEquals(10, lines.get(0).used());
        assertEquals(20, lines.get(1).used());
        assertEquals(21, lines.get(0).pay());
        assertEquals(9, lines.get(1).pay());
        // règlement truqué : un blocage inconnu dans la liste => rien ne bouge
        List<Settlement.Line> forged = new ArrayList<>(lines);
        forged.add(new Settlement.Line("lock:zzz:1", IDS[2], 10, 10, 10));
        Map<AccountRef, Long> before = l.snapshot();
        assertThrows(LedgerException.class, () -> l.post(Txn.settle("r1", forged, Currency.MBOKO)));
        assertEquals(before, l.snapshot());
        assertEquals(Ledger.EscrowStatus.OPEN, l.escrow("lock:a:1").orElseThrow().status());
        // règlement correct : une fois
        assertFalse(l.post(Txn.settle("r1", lines, Currency.MBOKO)).replayed());
        assertEquals(50 - 20 + 10 + 21, l.balance(AccountRef.dispo(IDS[0], Currency.MBOKO)));
        assertEquals(50 - 30 + 10 + 9, l.balance(AccountRef.dispo(IDS[1], Currency.MBOKO)));
        assertTrue(l.post(Txn.settle("r1", lines, Currency.MBOKO)).replayed());
        LedgerException again = assertThrows(LedgerException.class, () -> l.post(Txn.settle("r2", lines, Currency.MBOKO)));
        assertEquals(WalletReason.ESCROW_CLOSED, again.reason());
        LedgerException ref = assertThrows(LedgerException.class, () -> l.post(Txn.refund("lock:a:1", IDS[0], Currency.MBOKO, 20)));
        assertEquals(WalletReason.ESCROW_CLOSED, ref.reason());
    }

    @Test
    void settlementCannotPayAnIdentityThatDidNotLock() {
        MemoryLedger l = new MemoryLedger();
        l.post(Txn.grant(IDS[0], Currency.NDEM, 100, "g0"));
        l.post(Txn.lock(IDS[0], Currency.NDEM, 10, 1, "lock:a:1"));
        // règlement qui paie le butin à un tiers : refusé même s'il est équilibré
        Txn theft = new Txn(TxnKind.SETTLE, "settle:theft", List.of(
                new Entry(AccountRef.bloque(IDS[0], Currency.NDEM), -10),
                new Entry(AccountRef.dispo(IDS[3], Currency.NDEM), 10)), List.of("lock:a:1"));
        assertThrows(LedgerException.class, () -> l.post(theft));
        assertEquals(10, l.balance(AccountRef.bloque(IDS[0], Currency.NDEM)));
        // et aucun genre sauf les blocages ne peut vider une poche BLOQUE
        Txn steal2 = new Txn(TxnKind.ADJUST, "adj:steal", List.of(
                new Entry(AccountRef.bloque(IDS[0], Currency.NDEM), -10), new Entry(AccountRef.sys(AccountRef.ADJUST, Currency.NDEM), 10)), List.of());
        assertThrows(LedgerException.class, () -> l.post(steal2));
    }
}
