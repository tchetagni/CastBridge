package castbridge.server.wallet;

import castbridge.server.web.ApiException;
import castbridge.server.wallet.core.WalletPolicy;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Politique d'exploitation lue de {@code wallet_policy} à CHAQUE usage (taux, frais, bornes de mise, plafonds, montants d'attribution, interrupteurs) : rien n'est codé en dur
 * ailleurs (conception § 1.3). Toute écriture est bornée par les colonnes {@code min_value} / {@code max_value} puis revalidée par {@link WalletPolicy} (bornes croisées).
 * Contient aussi les débits d'écriture (30 par minute et par identité, 600 par minute au total, réglables).
 */
@Service
@WalletModuleConfig.Enabled
public class WalletPolicyService {
    private final WalletRepository repo;
    private final WalletModuleConfig.WalletClock clock;
    private final Limiter limiter;

    public WalletPolicyService(WalletRepository repo, WalletModuleConfig.WalletClock clock, WalletProperties props) {
        this.repo = repo;
        this.clock = clock;
        this.limiter = new Limiter(props.writesPerMinutePerIdentity(), props.writesPerMinuteGlobal());
    }

    /** Interrupteurs d'exploitation (actifs par défaut : une valeur absente ou différente de 0 est « actif » ; seul 0 coupe). */
    public record Switches(boolean stakesNdem, boolean stakesMboko, boolean transfer, boolean convert, boolean vouchers, boolean settle) {}

    public WalletPolicy get() {
        Map<String, WalletRepository.PolicyRow> r = repo.policyRows();
        WalletPolicy d = WalletPolicy.defaults();
        return new WalletPolicy(v(r, "convert.rate", d.rate()), (int) v(r, "convert.reverseFeeBp", d.reverseFeeBp()),
                v(r, "stake.minPerSeat.NDEM", d.stakeMinNdem()), v(r, "stake.maxPerSeat.NDEM", d.stakeMaxNdem()), v(r, "stake.minPerSeat.MBOKO", d.stakeMinMboko()), v(r, "stake.maxPerSeat.MBOKO", d.stakeMaxMboko()),
                v(r, "transfer.dailyCap.NDEM", d.transferCapNdem()), v(r, "transfer.dailyCap.MBOKO", d.transferCapMboko()), (int) v(r, "grant.periodDays", d.periodDays()),
                v(r, "grant.trial.ndem", d.trialNdem()), v(r, "grant.production.ndem", d.productionNdem()), v(r, "grant.production.mboko", d.productionMboko()),
                v(r, "grant.unlimited.ndem", d.unlimitedNdem()), v(r, "grant.unlimited.mboko", d.unlimitedMboko()), v(r, "grant.open.ndem", d.openNdem()), v(r, "grant.open.mboko", d.openMboko()));
    }

    public Switches switches() {
        Map<String, WalletRepository.PolicyRow> r = repo.policyRows();
        return new Switches(on(r, "switch.stakes.NDEM"), on(r, "switch.stakes.MBOKO"), on(r, "switch.transfer"), on(r, "switch.convert"), on(r, "switch.vouchers"), on(r, "switch.settle"));
    }

    /**
     * Plafonds des dons de l'administration (audit H2) : par don, par jour glissant et par heure (toutes monnaies, tous administrateurs) ; au-delà d'un plafond de montant, un SECOND
     * administrateur doit approuver. Valeurs absentes = les valeurs de lancement.
     */
    public record AdminCaps(long grantMax, long dailyMax, int perHour) {}

    public AdminCaps adminCaps(castbridge.server.wallet.core.Currency cur) {
        Map<String, WalletRepository.PolicyRow> r = repo.policyRows();
        boolean n = cur == castbridge.server.wallet.core.Currency.NDEM;
        return new AdminCaps(v(r, "admin.grantMax." + cur.name(), n ? 10_000 : 100), v(r, "admin.dailyMax." + cur.name(), n ? 100_000 : 1_000), (int) v(r, "admin.grantsPerHour", 10));
    }

    /** Limites des transferts d'un compte d'ESSAI (audit w22-05, M5) : plafond du jour par monnaie, âge minimal du compte, donateurs distincts par destinataire et par 24 h, plafond du couple émetteur-destinataire. */
    public record TrialLimits(long dailyNdem, long dailyMboko, int minAgeHours, int maxDonors, long pairNdem, long pairMboko) {}

    public TrialLimits trialLimits() {
        Map<String, WalletRepository.PolicyRow> r = repo.policyRows();
        return new TrialLimits(v(r, "transfer.trial.dailyCap.NDEM", 1_000), v(r, "transfer.trial.dailyCap.MBOKO", 0), (int) v(r, "transfer.trial.minAgeHours", 72), (int) v(r, "transfer.trial.maxDonors", 3),
                v(r, "transfer.trial.pairCap.NDEM", 5_000), v(r, "transfer.trial.pairCap.MBOKO", 50));
    }

    /** Plafonds du règlement (audit w22-05, M3) : par règlement, par 24 h glissantes, seuil d'alerte d'un gain ; par monnaie. */
    public record SettleCaps(long perSettle, long perDay, long alert) {}

    public SettleCaps settleCaps(castbridge.server.wallet.core.Currency cur) {
        Map<String, WalletRepository.PolicyRow> r = repo.policyRows();
        boolean n = cur == castbridge.server.wallet.core.Currency.NDEM;
        return new SettleCaps(v(r, "settle.maxPerSettle." + cur.name(), n ? 20_000 : 1_000), v(r, "settle.dailyMax." + cur.name(), n ? 5_000_000 : 50_000), v(r, "settle.alert." + cur.name(), n ? 200_000 : 200));
    }

    private static long v(Map<String, WalletRepository.PolicyRow> r, String name, long fallback) {
        WalletRepository.PolicyRow row = r.get(name);
        return row == null ? fallback : row.value();
    }

    private static boolean on(Map<String, WalletRepository.PolicyRow> r, String name) {
        WalletRepository.PolicyRow row = r.get(name);
        return row == null || row.value() != 0;
    }

    /** Écrit une valeur de la politique : bornes de la ligne vérifiées, puis cohérence d'ensemble ; refus français sinon, rien n'est écrit. */
    public void set(String name, long value, String by) {
        WalletRepository.PolicyRow row = repo.policyRows().get(name);
        if (row == null) throw ApiException.badRequest("Réglage inconnu : " + name);
        if (value < row.min() || value > row.max()) throw ApiException.badRequest("Réglage " + name + " hors bornes : " + row.min() + " à " + row.max() + " (reçu " + value + ")");
        repo.updatePolicy(name, value, by, clock.now());
        try {
            get();
        } catch (IllegalArgumentException e) {
            repo.updatePolicy(name, row.value(), by, clock.now());
            throw ApiException.badRequest(e.getMessage());
        }
    }

    // ---- débits d'écriture ----

    /** Compte une écriture de l'identité ; 429 au-delà de la limite par identité ou de la limite globale (fenêtre glissante d'une minute, horloge du module). Refusé = non compté. */
    public void checkWrite(String identity) { limiter.check(identity, clock.now().toEpochMilli()); }

    static final class Limiter {
        private static final long WINDOW_MS = 60_000L;
        private static final int MAX_IDENTITIES = 50_000;
        private final int perIdentity, global;
        private final Map<String, ArrayDeque<Long>> byIdentity = new LinkedHashMap<>(16, 0.75f, true);
        private final ArrayDeque<Long> all = new ArrayDeque<>();

        Limiter(int perIdentity, int global) {
            this.perIdentity = perIdentity;
            this.global = global;
        }

        synchronized void check(String identity, long now) {
            while (!all.isEmpty() && now - all.peekFirst() >= WINDOW_MS) all.pollFirst();
            if (byIdentity.size() >= MAX_IDENTITIES && !byIdentity.containsKey(identity)) {
                byIdentity.values().forEach(q -> { while (!q.isEmpty() && now - q.peekFirst() >= WINDOW_MS) q.pollFirst(); });
                byIdentity.values().removeIf(ArrayDeque::isEmpty);
                if (byIdentity.size() >= MAX_IDENTITIES) byIdentity.remove(byIdentity.keySet().iterator().next());
            }
            ArrayDeque<Long> q = byIdentity.computeIfAbsent(identity, k -> new ArrayDeque<>());
            while (!q.isEmpty() && now - q.peekFirst() >= WINDOW_MS) q.pollFirst();
            if (q.size() >= perIdentity) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Trop d'opérations de portefeuille en une minute : réessayez dans un instant", java.util.List.of("RATE_LIMIT"));
            if (all.size() >= global) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Le portefeuille est très sollicité : réessayez dans un instant", java.util.List.of("RATE_LIMIT"));
            q.addLast(now);
            all.addLast(now);
        }
    }
}
