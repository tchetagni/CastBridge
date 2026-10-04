package castbridge.server.wallet.core;

/**
 * Formules de conversion (conception W22 § 3.1), à la demande du joueur, dans les deux sens, symétriques : {@code rate} NDEM pour 1 MBOKO.
 * Sens N→M : le joueur paie {@code rate·q} NDEM et reçoit {@code q} MBOKO. Sens M→N : il paie {@code q} MBOKO et reçoit {@code rate·q − f} NDEM
 * avec {@code f = ceil(rate·q·fee)}, {@code fee = reverseFeeBp / 10 000} (l'arrondi va toujours contre le joueur ; l'écart est rapporté à SYS:FEE).
 */
public final class Conversion {
    public static final long MAX_Q = 1_000_000_000L;

    public enum Direction { N2M, M2N }

    /** @param mboko q ; @param ndemGross rate·q ; @param fee f (0 en N→M) ; @param ndemNet ce que touche (M→N) ou paie (N→M) le joueur en NDEM */
    public record Quote(Direction direction, long mboko, long ndemGross, long fee, long ndemNet) {}

    private Conversion() {}

    public static Quote quote(Direction dir, long q, WalletPolicy policy) {
        if (dir == null || policy == null) throw new LedgerException(WalletReason.BAD_TXN, "Conversion incomplète");
        if (q < 1 || q > MAX_Q) throw new LedgerException(WalletReason.BAD_TXN, "Quantité de MBOKO à convertir hors bornes : 1 à " + MAX_Q);
        long gross = policy.rate() * q;                        // ≤ 10^6 · 10^9 : tient dans un long
        if (dir == Direction.N2M) return new Quote(dir, q, gross, 0, gross);
        long fee = ceilDiv(gross * policy.reverseFeeBp(), 10_000L); // ≤ 10^15 · 2 000 : tient dans un long
        return new Quote(dir, q, gross, fee, gross - fee);
    }

    /** Division entière par excès, nombres positifs. */
    static long ceilDiv(long a, long b) { return (a + b - 1) / b; }
}
