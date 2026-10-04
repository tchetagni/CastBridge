package castbridge.server.wallet;

import java.time.Duration;
import java.time.Instant;

/**
 * Limite de RATTRAPAGE des tranches d'une licence qui n'apparaît au serveur que tard, par la notification d'une activation hors ligne (conception W23-B § 5.2, décision D-W23B-5,
 * règles du propriétaire du 2026-10-04). Soit {@code R} la première notification qui a ouvert ou rattaché la licence et {@code s} le début de la période (ou de l'ouverture illimitée) :
 * <ul>
 *   <li>{@code s ≥ R − 90 jours} : versée automatiquement ;</li>
 *   <li>{@code R − 366 jours ≤ s < R − 90 jours} : versée seulement si l'émission est DÉCLARÉE (journal, registre, émission du serveur) ou acceptée par le propriétaire, sinon RETENUE
 *       (ni perdue ni versée : elle sera versée, une fois, dès la déclaration) ;</li>
 *   <li>{@code s < R − 366 jours} : JAMAIS (durée maximale de la phase hors ligne).</li>
 * </ul>
 * Les périodes futures ne sont jamais retenues (elles commencent après {@code R}). Une période d'un intervalle suspendu n'est jamais due (autre règle : {@link LicenseSpanBook}).
 * La clé d'idempotence {@code grant:lic:<licence>:<monnaie>:p<k>} du grand livre garantit une seule pose par (licence, période), même sous deux notifications simultanées.
 */
public final class CatchUpPolicy {
    public static final Duration AUTOMATIC = Duration.ofDays(90);
    public static final Duration MAXIMUM = Duration.ofDays(366);

    public enum Verdict { PAY, HELD, NEVER }

    private CatchUpPolicy() {}

    public static Verdict judge(Instant periodStart, Instant firstNotifiedAt, boolean declared) {
        if (periodStart == null || firstNotifiedAt == null) return Verdict.PAY;
        if (!periodStart.isBefore(firstNotifiedAt.minus(AUTOMATIC))) return Verdict.PAY;
        if (periodStart.isBefore(firstNotifiedAt.minus(MAXIMUM))) return Verdict.NEVER;
        return declared ? Verdict.PAY : Verdict.HELD;
    }
}
