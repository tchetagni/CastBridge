package castbridge.server.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import castbridge.server.wallet.core.AccountRef;
import castbridge.server.wallet.core.Conversion;
import castbridge.server.wallet.core.Currency;
import castbridge.server.wallet.core.Entry;
import castbridge.server.wallet.core.Ledger;
import castbridge.server.wallet.core.LedgerException;
import castbridge.server.wallet.core.MemoryLedger;
import castbridge.server.wallet.core.Pocket;
import castbridge.server.wallet.core.Settlement;
import castbridge.server.wallet.core.Txn;
import castbridge.server.wallet.core.TxnKind;
import castbridge.server.wallet.core.WalletPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Suites aléatoires rejouées sur l'oracle ({@link MemoryLedger}) et sur le grand livre testé (JDBC) : mêmes verdicts, mêmes soldes, mêmes blocages ;
 * et le test de concurrence. Partagé entre les tests H2 et le test MySQL (Testcontainers).
 */
final class LedgerSuites {
    private LedgerSuites() {}

    static final List<String> SYSTEM = List.of(AccountRef.GRANT, AccountRef.VOUCHER, AccountRef.CONVERT, AccountRef.FEE, AccountRef.ADJUST, AccountRef.POT);

    static final class Tally {
        long ops, ok, replays, refused, conflicts, insufficient, closed, other;
    }

    /** Identité propre à la suite {@code seed} (les deux grands livres sont durables d'une suite à l'autre : jamais deux suites sur les mêmes comptes). */
    static String id(String tag, long seed, int i) {
        return String.format("%s%03X-%04X-%04X-%04X", tag, 0xA00 + i, (seed >>> 16) & 0xFFFF, seed & 0xFFFF, 0x5EED);
    }

    static String apply(Ledger l, Txn t) {
        try {
            return l.post(t).replayed() ? "REPLAY" : "OK";
        } catch (LedgerException e) {
            return e.reason().name();
        }
    }

    private static String both(Ledger oracle, Ledger sut, Txn t, String ctx, Tally st, List<Txn> posted) {
        String a = apply(oracle, t), b = apply(sut, t);
        assertEquals(a, b, ctx + " : même verdict sur le grand livre testé et sur l'oracle (" + t.kind() + " " + t.idemKey() + ")");
        st.ops++;
        switch (a) {
            case "OK" -> { st.ok++; posted.add(t); }
            case "REPLAY" -> st.replays++;
            case "INSUFFICIENT" -> st.insufficient++;
            case "IDEM_CONFLICT" -> st.conflicts++;
            case "ESCROW_CLOSED" -> st.closed++;
            default -> st.other++;
        }
        return a;
    }

    /** Rejoue {@code suites} suites (graines 1..suites) ; chaque suite : 1..40 opérations sur 3 identités à elle. */
    static Tally oracle(Ledger sut, MemoryLedger oracle, String tag, int suites) {
        Tally st = new Tally();
        List<String> allIds = new ArrayList<>();
        // la base peut déjà porter d'autres tests : les comptes système se comparent en VARIATION depuis le départ de cette série
        java.util.Map<String, Long> sysStart = new java.util.HashMap<>();
        for (Currency c : Currency.values()) for (String h : SYSTEM) sysStart.put(h + c, sut.balance(AccountRef.sys(h, c)));
        for (long seed = 1; seed <= suites; seed++) {
            Random r = new Random(seed * 7919);
            String[] ids = {id(tag, seed, 0), id(tag, seed, 1), id(tag, seed, 2)};
            allIds.addAll(List.of(ids));
            long rate = r.nextInt(10) < 6 ? 1000 : 1 + r.nextInt(5000);
            int fee = r.nextInt(10) < 6 ? 0 : r.nextInt(2001);
            WalletPolicy pol = WalletPolicy.defaults().withRate(rate).withReverseFeeBp(fee);
            List<Txn> posted = new ArrayList<>();
            List<List<Settlement.Escrow>> games = new ArrayList<>();
            List<Currency> gameCur = new ArrayList<>();
            List<Long> gamePer = new ArrayList<>();
            AtomicLong n = new AtomicLong();
            // chaque suite démarre financée (sinon presque tout serait un découvert) : attributions et un bon par identité
            for (int i = 0; i < ids.length; i++) {
                both(oracle, sut, Txn.grant(ids[i], Currency.NDEM, 500 + r.nextInt(3000), tag + seed + "-fn" + i), "graine " + seed + " (départ)", st, posted);
                both(oracle, sut, Txn.grant(ids[i], Currency.MBOKO, 20 + r.nextInt(100), tag + seed + "-fm" + i), "graine " + seed + " (départ)", st, posted);
            }
            int count = 1 + r.nextInt(15);
            for (int i = 0; i < count; i++) {
                String ctx = "graine " + seed + ", opération " + i;
                String k = tag + seed + "-" + n.incrementAndGet();
                switch (r.nextInt(17)) {
                    case 0, 1 -> both(oracle, sut, Txn.grant(pick(r, ids), cur(r), 1 + r.nextInt(3000), "g" + k), ctx, st, posted);
                    case 2 -> both(oracle, sut, Txn.voucher(pick(r, ids), cur(r), 1 + r.nextInt(500), "n" + k), ctx, st, posted);
                    case 3, 4 -> both(oracle, sut, Txn.convert(pick(r, ids), Conversion.Direction.N2M, 1 + r.nextInt(5), pol, "c" + k), ctx, st, posted);
                    case 5, 6 -> both(oracle, sut, Txn.convert(pick(r, ids), Conversion.Direction.M2N, 1 + r.nextInt(5), pol, "c" + k), ctx, st, posted);
                    case 7 -> {
                        String a = pick(r, ids), b = pick(r, ids);
                        if (!a.equals(b)) both(oracle, sut, Txn.transfer(a, b, cur(r), 1 + r.nextInt(800), "x" + k), ctx, st, posted);
                    }
                    case 8 -> {
                        Currency c = cur(r);
                        long per = 1 + r.nextInt(50);
                        List<Settlement.Escrow> es = new ArrayList<>();
                        for (int t = 0; t < 2 + r.nextInt(2); t++) {
                            String id = pick(r, ids);
                            int kk = 1 + r.nextInt(4);
                            String eid = "lock:" + id + ":" + k + "-" + t;
                            if (both(oracle, sut, Txn.lock(id, c, per, kk, eid), ctx, st, posted).equals("OK")) es.add(new Settlement.Escrow(eid, id, kk, per * kk));
                        }
                        if (!es.isEmpty()) { games.add(es); gameCur.add(c); gamePer.add(per); }
                    }
                    case 9 -> {
                        if (games.isEmpty()) break;
                        int g = r.nextInt(games.size());
                        List<Settlement.Escrow> open = openOnes(oracle, games.get(g));
                        if (open.isEmpty()) break;
                        List<Settlement.Seat> seats = new ArrayList<>();
                        for (Settlement.Escrow e : open) for (int s = 0, present = r.nextInt(e.k() + 1); s < present; s++) seats.add(new Settlement.Seat(e.eid(), s, r.nextInt(4)));
                        List<Settlement.Line> lines = Settlement.compute(gameCur.get(g), gamePer.get(g), open, seats, r.nextInt(4) == 0 ? Settlement.Kind.ABORT : Settlement.Kind.END);
                        both(oracle, sut, Txn.settle("r" + k, lines, gameCur.get(g)), ctx, st, posted);
                    }
                    case 10 -> {
                        if (games.isEmpty()) break;
                        int g = r.nextInt(games.size());
                        List<Settlement.Escrow> open = openOnes(oracle, games.get(g));
                        if (open.isEmpty()) break;
                        Settlement.Escrow e = open.get(r.nextInt(open.size()));
                        both(oracle, sut, Txn.refund(e.eid(), e.id(), gameCur.get(g), e.amount()), ctx, st, posted);
                    }
                    case 11 -> {
                        long delta = r.nextInt(2001) - 1000;
                        both(oracle, sut, Txn.adjust(pick(r, ids), cur(r), delta == 0 ? 7 : delta, "a" + k), ctx, st, posted);
                    }
                    case 12 -> {
                        // rejeu exact, puis même clé et contenu doublé (IDEM_CONFLICT)
                        if (posted.isEmpty()) break;
                        Txn t = posted.get(r.nextInt(posted.size()));
                        both(oracle, sut, t, ctx, st, posted);
                        List<Entry> doubled = new ArrayList<>();
                        for (Entry e : t.entries()) doubled.add(new Entry(e.account(), e.amount() * 2));
                        both(oracle, sut, new Txn(t.kind(), t.idemKey(), doubled, t.refs()), ctx, st, posted);
                    }
                    case 13 -> {
                        // découvert
                        String a = pick(r, ids), b = pick(r, ids);
                        if (a.equals(b)) break;
                        Currency c = cur(r);
                        both(oracle, sut, Txn.transfer(a, b, c, oracle.balance(AccountRef.dispo(a, c)) + 1, "over" + k), ctx, st, posted);
                    }
                    default -> hostileEscrow(r, oracle, sut, ids, games, gameCur, k, ctx, st, posted);
                }
            }
            for (String id : ids) for (Currency c : Currency.values()) for (Pocket p : new Pocket[] {Pocket.DISPO, Pocket.BLOQUE}) {
                AccountRef a = new AccountRef(id, c, p);
                assertEquals(oracle.balance(a), sut.balance(a), "graine " + seed + " : solde de " + a.key());
            }
            for (List<Settlement.Escrow> g : games) for (Settlement.Escrow e : g) {
                assertEquals(oracle.escrow(e.eid()), sut.escrow(e.eid()), "graine " + seed + " : blocage " + e.eid());
            }
        }
        for (Currency c : Currency.values()) {
            assertEquals(0, sut.sum(c), "I-1 : conservation " + c);
            assertEquals(oracle.sum(c), sut.sum(c));
            for (String h : SYSTEM) {
                AccountRef a = AccountRef.sys(h, c);
                assertEquals(oracle.balance(a), sut.balance(a) - sysStart.get(h + c), "compte système " + a.key());
            }
        }
        return st;
    }

    private static void hostileEscrow(Random r, MemoryLedger oracle, Ledger sut, String[] ids, List<List<Settlement.Escrow>> games, List<Currency> gameCur, String k, String ctx, Tally st, List<Txn> posted) {
        switch (r.nextInt(4)) {
            case 0 -> {
                // règlement qui paie un tiers
                if (games.isEmpty()) break;
                int g = r.nextInt(games.size());
                for (Settlement.Escrow e : games.get(g)) {
                    if (oracle.escrow(e.eid()).orElseThrow().status() != Ledger.EscrowStatus.OPEN) continue;
                    Currency c = gameCur.get(g);
                    String thief = ids[(java.util.Arrays.asList(ids).indexOf(e.id()) + 1) % ids.length];
                    both(oracle, sut, new Txn(TxnKind.SETTLE, "settle:theft" + k, List.of(new Entry(AccountRef.bloque(e.id(), c), -e.amount()), new Entry(AccountRef.dispo(thief, c), e.amount())), List.of(e.eid())), ctx, st, posted);
                    return;
                }
            }
            case 1 -> {
                // règlement d'un blocage inconnu
                Currency c = cur(r);
                both(oracle, sut, Txn.settle("zz" + k, List.of(new Settlement.Line("lock:" + ids[0] + ":none" + k, ids[0], 10, 10, 10)), c), ctx, st, posted);
            }
            case 2 -> {
                // un autre genre ne vide jamais BLOQUE
                Currency c = cur(r);
                both(oracle, sut, new Txn(TxnKind.ADJUST, "adj:steal" + k, List.of(new Entry(AccountRef.bloque(ids[0], c), -1), new Entry(AccountRef.sys(AccountRef.ADJUST, c), 1)), List.of()), ctx, st, posted);
            }
            default -> {
                // un blocage clos ne sert plus (règlement après rendu, rendu après règlement)
                for (int g = 0; g < games.size(); g++) for (Settlement.Escrow e : games.get(g)) {
                    Ledger.EscrowState s = oracle.escrow(e.eid()).orElseThrow();
                    if (s.status() == Ledger.EscrowStatus.OPEN) continue;
                    Currency c = gameCur.get(g);
                    Txn second = s.status() == Ledger.EscrowStatus.SETTLED ? Txn.refund(e.eid(), e.id(), c, e.amount())
                            : Txn.settle("late" + k, List.of(new Settlement.Line(e.eid(), e.id(), e.amount(), e.amount(), e.amount())), c);
                    both(oracle, sut, second, ctx, st, posted);
                    return;
                }
            }
        }
    }

    private static List<Settlement.Escrow> openOnes(MemoryLedger l, List<Settlement.Escrow> g) {
        List<Settlement.Escrow> open = new ArrayList<>();
        for (Settlement.Escrow e : g) if (l.escrow(e.eid()).orElseThrow().status() == Ledger.EscrowStatus.OPEN) open.add(e);
        return open;
    }

    private static String pick(Random r, String[] ids) { return ids[r.nextInt(ids.length)]; }

    private static Currency cur(Random r) { return r.nextBoolean() ? Currency.NDEM : Currency.MBOKO; }

    // ---- concurrence ----

    /**
     * {@code threads} fils × {@code ops} opérations croisées (transferts A↔B, blocages suivis d'un règlement ou d'un rendu, conversions, rejeux) sur 4 identités :
     * aucun solde négatif, Σ = 0, aucune erreur autre qu'un refus INSUFFICIENT (un interblocage non rattrapé est une erreur).
     */
    static void concurrency(Ledger l, String tag, int threads, int ops, java.util.function.LongSupplier derivedMismatches, java.util.function.LongSupplier retries) throws Exception {
        String[] ids = {id(tag, 1, 0), id(tag, 1, 1), id(tag, 1, 2), id(tag, 1, 3)};
        long retries0 = retries.getAsLong();
        for (String id : ids) {
            l.post(Txn.grant(id, Currency.NDEM, 100_000, "cg-n-" + id));
            l.post(Txn.grant(id, Currency.MBOKO, 5_000, "cg-m-" + id));
        }
        WalletPolicy pol = WalletPolicy.defaults();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicLong done = new AtomicLong(), refused = new AtomicLong();
        for (int t = 0; t < threads; t++) {
            final int tn = t;
            pool.submit(() -> {
                Random r = new Random(1000 + tn);
                try {
                    start.await();
                    for (int i = 0; i < ops; i++) {
                        String k = tag + "-" + tn + "-" + i;
                        String a = ids[r.nextInt(ids.length)], b = ids[r.nextInt(ids.length)];
                        Currency c = r.nextBoolean() ? Currency.NDEM : Currency.MBOKO;
                        try {
                            switch (r.nextInt(5)) {
                                case 0, 1 -> { if (!a.equals(b)) l.post(Txn.transfer(a, b, c, 1 + r.nextInt(300), "x" + k)); }
                                case 2 -> {
                                    String eid = "lock:" + a + ":" + k;
                                    l.post(Txn.lock(a, c, 5, 2, eid));
                                    l.post(Txn.refund(eid, a, c, 10));
                                }
                                case 3 -> {
                                    String ea = "lock:" + a + ":" + k + "a", eb = "lock:" + b + ":" + k + "b";
                                    if (a.equals(b)) break;
                                    l.post(Txn.lock(a, c, 5, 1, ea));
                                    try {
                                        l.post(Txn.lock(b, c, 5, 1, eb));
                                    } catch (LedgerException refusal) {
                                        // le second blocage refusé (solde insuffisant) : le premier, déjà posé, doit être rendu, sinon des fonds restent bloqués
                                        l.post(Txn.refund(ea, a, c, 5));
                                        throw refusal;
                                    }
                                    List<Settlement.Line> lines = Settlement.compute(c, 5, List.of(new Settlement.Escrow(ea, a, 1, 5), new Settlement.Escrow(eb, b, 1, 5)),
                                            List.of(new Settlement.Seat(ea, 0, 3), new Settlement.Seat(eb, 0, 1)), Settlement.Kind.END);
                                    l.post(Txn.settle("r" + k, lines, c));
                                }
                                default -> {
                                    l.post(Txn.convert(a, r.nextBoolean() ? Conversion.Direction.N2M : Conversion.Direction.M2N, 1, pol, "c" + k));
                                    if (r.nextInt(4) == 0) l.post(Txn.grant(a, Currency.NDEM, 3, "rejeu-" + tn + "-" + (i % 7) + "-" + a));
                                }
                            }
                            done.incrementAndGet();
                        } catch (LedgerException e) {
                            if (e.reason() != castbridge.server.wallet.core.WalletReason.INSUFFICIENT) throw e;
                            refused.incrementAndGet();
                        }
                    }
                } catch (Throwable e) {
                    failure.compareAndSet(null, e);
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(240, TimeUnit.SECONDS), "les fils doivent finir (pas d'interblocage)");
        if (failure.get() != null) throw new AssertionError("erreur sous concurrence : " + failure.get(), failure.get());
        assertTrue(done.get() > threads * ops / 4, "assez d'opérations réussies : " + done.get());
        for (Currency c : Currency.values()) assertEquals(0, l.sum(c), "I-1 Σ = 0 " + c);
        for (String id : ids) for (Currency c : Currency.values()) {
            assertTrue(l.balance(AccountRef.dispo(id, c)) >= 0, "aucun négatif " + id + " " + c);
            assertEquals(0, l.balance(AccountRef.bloque(id, c)), "tout blocage est clos " + id + " " + c);
        }
        assertEquals(0, derivedMismatches.getAsLong(), "I-8 : chaque solde = Σ de ses écritures");
        assertEquals(0, retries.getAsLong() - retries0, "verrous pris dans l'ordre croissant des comptes : aucun interblocage, aucun réessai");
    }
}
