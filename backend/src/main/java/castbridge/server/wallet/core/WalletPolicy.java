package castbridge.server.wallet.core;

/**
 * Table de politique (conception W22 § 1.3, § 1.2, § 5.3) : taux, frais de conversion inverse, bornes de mise, plafond de transfert et montants
 * d'attribution. Rien de tout cela n'est codé en dur ailleurs. Toute valeur hors bornes est refusée par une exception française.
 *
 * @param rate          NDEM pour 1 MBOKO (1..1 000 000, défaut 1 000)
 * @param reverseFeeBp  frais du sens MBOKO → NDEM en points de base (0..2 000, défaut 0 ; jamais négatif : un sens inverse plus favorable est refusé)
 */
public record WalletPolicy(
        long rate, int reverseFeeBp,
        long stakeMinNdem, long stakeMaxNdem, long stakeMinMboko, long stakeMaxMboko,
        long transferCapNdem, long transferCapMboko,
        int periodDays,
        long trialNdem, long productionNdem, long productionMboko,
        long unlimitedNdem, long unlimitedMboko, long openNdem, long openMboko) {

    public static final long MAX_RATE = 1_000_000;
    public static final int MAX_FEE_BP = 2_000;

    public WalletPolicy {
        if (rate < 1 || rate > MAX_RATE) throw new IllegalArgumentException("Taux de conversion hors bornes : 1 à 1 000 000 NDEM pour 1 MBOKO (reçu " + rate + ")");
        if (reverseFeeBp < 0) throw new IllegalArgumentException("Frais de conversion inverse négatifs refusés : le sens inverse ne peut jamais être plus favorable (reçu " + reverseFeeBp + " pb)");
        if (reverseFeeBp > MAX_FEE_BP) throw new IllegalArgumentException("Frais de conversion inverse hors bornes : 0 à 2 000 points de base (reçu " + reverseFeeBp + ")");
        bounds("Mise NDEM", stakeMinNdem, stakeMaxNdem);
        bounds("Mise MBOKO", stakeMinMboko, stakeMaxMboko);
        if (transferCapNdem < 1 || transferCapMboko < 1) throw new IllegalArgumentException("Plafond de transfert hors bornes : au moins 1");
        if (periodDays < 1 || periodDays > 365) throw new IllegalArgumentException("Durée d'une période d'attribution hors bornes : 1 à 365 jours (reçu " + periodDays + ")");
        for (long a : new long[] {trialNdem, productionNdem, productionMboko, unlimitedNdem, unlimitedMboko, openNdem, openMboko}) {
            if (a < 0 || a > 1_000_000_000L) throw new IllegalArgumentException("Montant d'attribution hors bornes : 0 à 1 000 000 000 (reçu " + a + ")");
        }
    }

    private static void bounds(String what, long min, long max) {
        if (min < 1 || max < min || max > 1_000_000_000L) throw new IllegalArgumentException(what + " : bornes invalides (1 ≤ min ≤ max ≤ 1 000 000 000, reçu " + min + ".." + max + ")");
    }

    /** Valeurs de lancement : 1 000 NDEM pour 1 MBOKO, frais 0 %, mises 1..1 000 NDEM et 1..100 MBOKO, plafond 10 000 NDEM / 100 MBOKO par jour. */
    public static WalletPolicy defaults() {
        return new WalletPolicy(1_000, 0, 1, 1_000, 1, 100, 10_000, 100, 30, 100, 1_000, 10, 1_000, 10, 5_000, 50);
    }

    public WalletPolicy withRate(long newRate) {
        return new WalletPolicy(newRate, reverseFeeBp, stakeMinNdem, stakeMaxNdem, stakeMinMboko, stakeMaxMboko, transferCapNdem, transferCapMboko, periodDays,
                trialNdem, productionNdem, productionMboko, unlimitedNdem, unlimitedMboko, openNdem, openMboko);
    }

    public WalletPolicy withReverseFeeBp(int newFeeBp) {
        return new WalletPolicy(rate, newFeeBp, stakeMinNdem, stakeMaxNdem, stakeMinMboko, stakeMaxMboko, transferCapNdem, transferCapMboko, periodDays,
                trialNdem, productionNdem, productionMboko, unlimitedNdem, unlimitedMboko, openNdem, openMboko);
    }

    /** Mise par siège dans les bornes de la monnaie, sinon refus français. */
    public void checkStake(Currency cur, long per) {
        long min = cur == Currency.NDEM ? stakeMinNdem : stakeMinMboko;
        long max = cur == Currency.NDEM ? stakeMaxNdem : stakeMaxMboko;
        if (per < min || per > max) throw new LedgerException(WalletReason.BAD_TXN, "Mise hors bornes : " + min + " à " + max + " " + cur + " par siège");
    }

    /** Plafond simple de transfert : {@code alreadyToday} = total déjà sorti aujourd'hui par cette identité dans cette monnaie. */
    public void checkTransfer(Currency cur, long amount, long alreadyToday) {
        long cap = cur == Currency.NDEM ? transferCapNdem : transferCapMboko;
        if (amount < 1 || alreadyToday < 0 || alreadyToday + amount > cap) throw new LedgerException(WalletReason.DAILY_CAP);
    }
}
