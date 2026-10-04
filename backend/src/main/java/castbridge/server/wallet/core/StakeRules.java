package castbridge.server.wallet.core;

import java.util.Optional;

/**
 * Qui peut miser quoi (règle du propriétaire, conception W22 § 1.1, R-E7). MBOKO : production, illimitée, ou grâce de production seulement
 * (on peut en détenir et en recevoir sans cela). NDEM : toute TV activée en ligne (essai, production, grâce, illimitée, super), pas une TV sans activation.
 */
public final class StakeRules {
    private StakeRules() {}

    /** {@code grace} = production échue encore dans sa période de grâce. */
    public static boolean mayStake(Edition edition, Currency cur, boolean grace) { return refusal(edition, cur, grace).isEmpty(); }

    /** Motif du refus, ou vide si la mise est permise. */
    public static Optional<WalletReason> refusal(Edition edition, Currency cur, boolean grace) {
        if (cur == Currency.MBOKO) {
            if (edition == Edition.PRODUCTION || edition == Edition.UNLIMITED || grace) return Optional.empty();
            return Optional.of(edition == Edition.TRIAL ? WalletReason.TRIAL_NO_MBOKO : WalletReason.ACTIVATE);
        }
        if (edition != Edition.NONE || grace) return Optional.empty();
        return Optional.of(WalletReason.ACTIVATE);
    }
}
