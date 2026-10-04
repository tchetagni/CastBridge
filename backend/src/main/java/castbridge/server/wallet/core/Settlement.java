package castbridge.server.wallet.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Règlement d'une partie misée (conception W22 § 3.3) : à partir des blocages (un par TV, {@code k} sièges misant) et des scores des sièges,
 * calcule pour chaque blocage ce qui a été utilisé et ce qui est payé. Pur : la cagnotte partagée est celle de {@link PotSplit}, puis agrégée par blocage.
 */
public final class Settlement {
    public enum Kind { END, ABORT }

    /** Un blocage ouvert : {@code amount} = mise par siège × k (ce qui est bloqué). */
    public record Escrow(String eid, String id, int k, long amount) {}

    /** Score d'un siège présent (numéro {@code n} ≥ 0 dans la TV du blocage {@code eid}). */
    public record Seat(String eid, int n, int score) {}

    /** Ligne de règlement d'un blocage : {@code amount} bloqué, {@code used} engagé dans la cagnotte, {@code pay} gagné (le non-utilisé est rendu en plus). */
    public record Line(String eid, String id, long amount, long used, long pay) {}

    private Settlement() {}

    /**
     * {@code used = per × min(k, sièges présents)} ; {@code pay} = somme des parts des sièges de ce blocage. ABORT : tout est rendu ({@code pay = used}).
     * Si personne n'a marqué (cagnotte non distribuable), chaque mise est rendue comme en ABORT : la conservation prime.
     */
    public static List<Line> compute(Currency cur, long per, List<Escrow> escrows, List<Seat> seatScores, Kind kind) {
        if (cur == null || kind == null || escrows == null || seatScores == null) throw new LedgerException(WalletReason.BAD_TXN, "Règlement incomplet");
        if (per < 1) throw new LedgerException(WalletReason.BAD_TXN, "Mise par siège invalide");
        Set<String> eids = new HashSet<>();
        for (Escrow e : escrows) {
            if (!eids.add(e.eid())) throw new LedgerException(WalletReason.BAD_TXN, "Blocage en double : " + e.eid());
            if (e.k() < 1 || e.amount() != per * e.k()) throw new LedgerException(WalletReason.BAD_TXN, "Blocage incohérent : " + e.eid());
        }
        Map<String, List<Seat>> seatsOf = new LinkedHashMap<>();
        for (Escrow e : escrows) seatsOf.put(e.eid(), new ArrayList<>());
        Set<String> seen = new HashSet<>();
        for (Seat s : seatScores) {
            List<Seat> list = seatsOf.get(s.eid());
            if (list == null) throw new LedgerException(WalletReason.BAD_TXN, "Siège d'un blocage inconnu : " + s.eid());
            if (s.n() < 0 || !seen.add(s.eid() + "#" + s.n())) throw new LedgerException(WalletReason.BAD_TXN, "Siège invalide ou en double : " + s.eid() + "#" + s.n());
            list.add(s);
        }
        long[] used = new long[escrows.size()];
        LinkedHashMap<String, Integer> scores = new LinkedHashMap<>();
        long pot = 0;
        for (int i = 0; i < escrows.size(); i++) {
            Escrow e = escrows.get(i);
            List<Seat> seats = seatsOf.get(e.eid());
            seats.sort(Comparator.comparingInt(Seat::n));
            int present = Math.min(e.k(), seats.size());
            used[i] = per * present;
            pot += used[i];
            for (int j = 0; j < present; j++) scores.put(e.eid() + "#" + seats.get(j).n(), seats.get(j).score());
        }
        long[] pay = used.clone();
        if (kind == Kind.END && pot > 0) {
            Map<String, Long> split = PotSplit.split(pot, scores);
            long total = 0;
            for (long v : split.values()) total += v;
            if (total == pot) {
                for (int i = 0; i < escrows.size(); i++) {
                    long sum = 0;
                    String prefix = escrows.get(i).eid() + "#";
                    for (Map.Entry<String, Long> en : split.entrySet()) if (en.getKey().startsWith(prefix)) sum += en.getValue();
                    pay[i] = sum;
                }
            } // sinon (personne n'a marqué) : chacun reprend sa mise utilisée
        }
        List<Line> lines = new ArrayList<>();
        for (int i = 0; i < escrows.size(); i++) {
            Escrow e = escrows.get(i);
            lines.add(new Line(e.eid(), e.id(), e.amount(), used[i], pay[i]));
        }
        check(lines, escrows);
        return lines;
    }

    /** Σ pay = Σ used, 0 ≤ used ≤ amount, pay ≥ 0, chaque blocage une fois, aucun blocage inconnu, titulaire et montant conformes au blocage. */
    public static void check(List<Line> lines, List<Escrow> escrows) {
        Map<String, Escrow> known = new LinkedHashMap<>();
        for (Escrow e : escrows) known.put(e.eid(), e);
        Set<String> seen = new HashSet<>();
        long sumUsed = 0, sumPay = 0;
        for (Line l : lines) {
            Escrow e = known.get(l.eid());
            if (e == null) throw new LedgerException(WalletReason.ESCROW_UNKNOWN, "Blocage inconnu dans le règlement : " + l.eid());
            if (!seen.add(l.eid())) throw new LedgerException(WalletReason.BAD_TXN, "Blocage réglé deux fois dans le même règlement : " + l.eid());
            if (!e.id().equals(l.id()) || e.amount() != l.amount()) throw new LedgerException(WalletReason.BAD_TXN, "Ligne de règlement non conforme au blocage : " + l.eid());
            if (l.used() < 0 || l.used() > l.amount() || l.pay() < 0) throw new LedgerException(WalletReason.BAD_TXN, "Montant utilisé ou payé invalide : " + l.eid());
            sumUsed += l.used();
            sumPay += l.pay();
        }
        if (sumUsed != sumPay) throw new LedgerException(WalletReason.UNBALANCED, "Règlement non conservatif : payé " + sumPay + " pour " + sumUsed + " utilisé");
    }
}
