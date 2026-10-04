package castbridge.server.wallet.core;

/** Édition lue dans l'activation signée de la TV. Le rang sert à choisir la meilleure édition valide (conception § 1.2). */
public enum Edition {
    NONE(0), TRIAL(1), PRODUCTION(2), UNLIMITED(3), SUPER(-1);

    private final int rank;

    Edition(int rank) { this.rank = rank; }

    /** Rang d'attribution : ILLIMITÉE > PRODUCTION > ESSAI ; NONE et SUPER n'attribuent rien (rang ≤ 0). */
    public int rank() { return rank; }
}
