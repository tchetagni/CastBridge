package castbridge.server.wallet.core;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Échéancier des attributions (conception W22 § 1.2) : périodes de {@code periodDays} jours depuis l'ancre, une tranche par période et par monnaie, au
 * montant de la MEILLEURE édition valide à l'instant de début de la période (ILLIMITÉE > PRODUCTION > ESSAI) ; ouverture illimitée une fois par identité.
 * Une édition SUPER couvrant l'instant ne reçoit rien. Calcul paresseux : toutes les tranches dues jusqu'à {@code now}, moins celles déjà inscrites.
 * Les montants viennent de {@link WalletPolicy}, jamais d'ailleurs.
 */
public final class GrantSchedule {
    private static final int MAX_PERIODS = 10_000; // borne de sûreté (≈ 820 ans de périodes de 30 jours)

    /** Une monnaie et son montant dans une tranche. */
    public record Amount(Currency currency, long amount) {}

    /** Tranche due : clé d'idempotence (ex. {@code grant:<id>:NDEM:p3} ou {@code grant:<id>:open-unlimited}) et montants à créditer. */
    public record Due(String key, List<Amount> amounts) {
        public Txn toTxn(String id) {
            Map<Currency, Long> m = new LinkedHashMap<>();
            for (Amount a : amounts) m.put(a.currency(), a.amount());
            return Txn.grantMulti(id, m, key);
        }
    }

    private GrantSchedule() {}

    public static String periodKey(String id, Currency cur, int k) { return "grant:" + id + ":" + cur + ":p" + k; }

    public static String openKey(String id) { return "grant:" + id + ":open-unlimited"; }

    public static List<Due> due(String id, WalletPolicy policy, Instant anchor, List<EditionSpan> spans, Set<String> alreadyGranted, Instant now) {
        List<Due> out = new ArrayList<>();
        if (now.isBefore(anchor)) return out;
        // ouverture illimitée : une fois par identité, pour toujours
        if (!alreadyGranted.contains(openKey(id)) && !coveredBySuper(spans, now)) {
            boolean unlimitedNow = spans.stream().anyMatch(s -> s.edition() == Edition.UNLIMITED && s.covers(now));
            if (unlimitedNow) {
                List<Amount> open = amounts(policy.openNdem(), policy.openMboko());
                if (!open.isEmpty()) out.add(new Due(openKey(id), open));
            }
        }
        Duration period = Duration.ofDays(policy.periodDays());
        for (int k = 0; k < MAX_PERIODS; k++) {
            Instant start = anchor.plus(period.multipliedBy(k));
            if (start.isAfter(now)) break;
            Edition best = best(spans, start);
            long ndem = 0, mboko = 0;
            if (best == Edition.TRIAL) { ndem = policy.trialNdem(); }
            else if (best == Edition.PRODUCTION) { ndem = policy.productionNdem(); mboko = policy.productionMboko(); }
            else if (best == Edition.UNLIMITED) { ndem = policy.unlimitedNdem(); mboko = policy.unlimitedMboko(); }
            if (ndem > 0 && !alreadyGranted.contains(periodKey(id, Currency.NDEM, k))) out.add(new Due(periodKey(id, Currency.NDEM, k), List.of(new Amount(Currency.NDEM, ndem))));
            if (mboko > 0 && !alreadyGranted.contains(periodKey(id, Currency.MBOKO, k))) out.add(new Due(periodKey(id, Currency.MBOKO, k), List.of(new Amount(Currency.MBOKO, mboko))));
        }
        return out;
    }

    private static List<Amount> amounts(long ndem, long mboko) {
        List<Amount> l = new ArrayList<>();
        if (ndem > 0) l.add(new Amount(Currency.NDEM, ndem));
        if (mboko > 0) l.add(new Amount(Currency.MBOKO, mboko));
        return l;
    }

    private static boolean coveredBySuper(List<EditionSpan> spans, Instant t) {
        return spans.stream().anyMatch(s -> s.edition() == Edition.SUPER && s.covers(t));
    }

    /** Meilleure édition valide à {@code t} ; NONE si aucune ou si SUPER (aucune attribution automatique pour la TV du propriétaire). */
    static Edition best(List<EditionSpan> spans, Instant t) {
        if (coveredBySuper(spans, t)) return Edition.NONE;
        Edition best = Edition.NONE;
        for (EditionSpan s : spans) if (s.covers(t) && s.edition().rank() > best.rank()) best = s.edition();
        return best;
    }
}
