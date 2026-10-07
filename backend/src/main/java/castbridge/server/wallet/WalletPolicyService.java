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

    // ---- politique des jeux misés (games-G2 : les échecs ; games-G5 : le Quiz) ----

    /**
     * Sièges qui misent par TV, pour chaque jeu misé connu : une CONSTANTE du code (pas une ligne de la table de politique, car elle décrit la forme du jeu et non un réglage d'exploitation) : aux échecs
     * une mise par TV, au Quiz jusqu'à 8 (les téléphones relayés de la TV et sa télécommande). Les clés de cette table SONT la liste des jeux misés connus : ajouter un jeu, c'est ajouter une entrée ici,
     * ses lignes {@code game.<jeu>.*} dans une migration, et sa FORME de règlement dans {@code SettleService}.
     */
    private static final Map<String, Integer> SEATS = Map.of("chess", 1, "quiz", 8);

    /** Les jeux dont le service arbitre des parties misées ; chacun a ses lignes {@code game.<jeu>.*} dans la table de politique. */
    public static final java.util.Set<String> GAMES = SEATS.keySet();

    /**
     * Politique d'un jeu misé : [enabled] (interrupteur du jeu), échelle de mises par monnaie ([scaleNdem], [scaleMboko] : paliers non nuls, triés), frais de plateforme en points de base
     * ({@code feeBp}, 0 au lancement, prélevés sur la cagnotte d'une partie DÉCISIVE seulement), plafonds de parties GAGNÉES par identité et par fenêtre calendaire d'Africa/Douala
     * ({@code capDay}, {@code capWeek}, {@code capMonth} ; 0 = sans plafond), et [seats] = nombre maximal de sièges qui misent par TV (constante du jeu, voir {@link #SEATS}).
     */
    public record GamePolicy(String game, boolean enabled, java.util.List<Long> scaleNdem, java.util.List<Long> scaleMboko, int feeBp, int capDay, int capWeek, int capMonth, int seats) {
        public java.util.List<Long> scale(castbridge.server.wallet.core.Currency cur) { return cur == castbridge.server.wallet.core.Currency.NDEM ? scaleNdem : scaleMboko; }
    }

    /** La politique de ce jeu, lue à CHAQUE usage ; vide si le jeu n'est pas un jeu misé connu. Une ligne absente prend la valeur de lancement (les mêmes pour tous les jeux). */
    public java.util.Optional<GamePolicy> game(String game) {
        if (game == null || !GAMES.contains(game)) return java.util.Optional.empty();
        Map<String, WalletRepository.PolicyRow> r = repo.policyRows();
        String p = "game." + game + ".";
        return java.util.Optional.of(new GamePolicy(game, on(r, p + "switch"), tiers(r, p + "tier.NDEM.", java.util.List.of(10L, 20L, 50L, 100L, 200L)), tiers(r, p + "tier.MBOKO.", java.util.List.of(1L, 2L, 5L, 10L)),
                (int) v(r, p + "feeBp", 0), (int) v(r, p + "cap.win.day", 3), (int) v(r, p + "cap.win.week", 10), (int) v(r, p + "cap.win.month", 15), SEATS.get(game)));
    }

    /** Les paliers {@code <préfixe>1..8} non nuls, triés et sans doublon ; si aucune ligne n'existe, les valeurs de lancement. */
    private static java.util.List<Long> tiers(Map<String, WalletRepository.PolicyRow> r, String prefix, java.util.List<Long> fallback) {
        boolean any = false;
        java.util.TreeSet<Long> out = new java.util.TreeSet<>();
        for (int i = 1; i <= 8; i++) {
            WalletRepository.PolicyRow row = r.get(prefix + i);
            if (row == null) continue;
            any = true;
            if (row.value() > 0) out.add(row.value());
        }
        return any ? java.util.List.copyOf(out) : fallback;
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
