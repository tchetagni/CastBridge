package castbridge.server.wallet.core;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Optional;

/**
 * Les fenêtres calendaires des plafonds de parties GAGNÉES par identité (règle du propriétaire du 2026-10-04 pour le Défi, reprise pour les échecs et le Quiz en ligne, comptée PAR JEU : D-W22-22, D-W22-24) :
 * jour 00:00-24:00, semaine lundi-dimanche, mois civil, en heure d'Africa/Douala (UTC+1, sans heure d'été). PUR : l'instant est passé par l'appelant. Même découpage que le compteur scellé de la TV
 * ({@code MillionsWinCaps}), qui est le modèle de ce code.
 */
public final class WinWindows {
    public static final ZoneId DOUALA = ZoneId.of("Africa/Douala");

    private WinWindows() {}

    /** Les début des fenêtres qui contiennent l'instant, et le début des suivantes (réouverture). */
    public record Windows(Instant dayStart, Instant weekStart, Instant monthStart, Instant nextDay, Instant nextWeek, Instant nextMonth, LocalDate nextWeekDate, LocalDate nextMonthDate) {}

    public static Windows of(Instant now) {
        LocalDate date = now.atZone(DOUALA).toLocalDate();
        LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate nextMonday = monday.plusWeeks(1);
        LocalDate firstOfMonth = YearMonth.from(date).atDay(1);
        LocalDate nextMonth = YearMonth.from(date).plusMonths(1).atDay(1);
        return new Windows(start(date), start(monday), start(firstOfMonth), start(date.plusDays(1)), start(nextMonday), start(nextMonth), nextMonday, nextMonth);
    }

    private static Instant start(LocalDate d) { return d.atStartOfDay(DOUALA).toInstant(); }

    /** Un plafond atteint : la phrase exacte à montrer et l'instant de réouverture (début de la fenêtre suivante du plafond atteint le plus contraignant). */
    public record Reached(String window, Instant reopensAt, String text) {}

    /**
     * Le plafond atteint le plus contraignant (réouverture la plus tardive), ou vide si aucun. Un plafond à 0 n'existe pas (« sans plafond »). Le plafond ne se vérifie qu'AVANT la mise : une partie
     * commencée sous le plafond peut toujours être gagnée et réglée (aucune mise perdue injustement).
     */
    public static Optional<Reached> reached(Windows w, int wonDay, int wonWeek, int wonMonth, int capDay, int capWeek, int capMonth) {
        Reached best = null;
        if (capDay > 0 && wonDay >= capDay) best = later(best, new Reached("DAY", w.nextDay(), "Limite atteinte : " + parties(capDay) + " aujourd'hui. Prochaine partie avec mise possible demain à 00:00."));
        if (capWeek > 0 && wonWeek >= capWeek) best = later(best, new Reached("WEEK", w.nextWeek(), "Limite atteinte : " + parties(capWeek) + " cette semaine. Prochaine partie avec mise possible lundi " + fmt(w.nextWeekDate()) + " à 00:00."));
        if (capMonth > 0 && wonMonth >= capMonth) best = later(best, new Reached("MONTH", w.nextMonth(), "Limite atteinte : " + parties(capMonth) + " ce mois-ci. Prochaine partie avec mise possible le " + fmt(w.nextMonthDate()) + " à 00:00."));
        return Optional.ofNullable(best);
    }

    private static Reached later(Reached a, Reached b) { return a == null || !b.reopensAt().isBefore(a.reopensAt()) ? b : a; }

    private static String parties(int n) { return n == 1 ? "1 partie gagnée" : n + " parties gagnées"; }

    private static String fmt(LocalDate d) { return String.format("%02d/%02d", d.getDayOfMonth(), d.getMonthValue()); }
}
