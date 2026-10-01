package castbridge.server.content;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Vocabulary and rules of the content validation (docs/CONTENT-VALIDATION.md); the same as android/core castbridge.core.content
 * and tools/content-validation: states, legal moves, report reasons, id and hash formats, the suspicious-score function.
 */
public final class ContentRules {
    private ContentRules() {}

    public static final Set<String> KINDS = Set.of("question", "lesson", "exercise");
    public static final List<String> STATES = List.of("review", "validated", "needs-fix", "rejected");
    public static final Map<String, String> STATE_LABELS = Map.of("review", "À relire", "validated", "Validé", "needs-fix", "À corriger", "rejected", "Rejeté");
    private static final Map<String, Set<String>> NEXT = Map.of(
            "review", Set.of("validated", "rejected", "needs-fix"),
            "validated", Set.of("review", "needs-fix", "rejected"),
            "needs-fix", Set.of("review", "rejected"),
            "rejected", Set.of("review", "needs-fix"));
    public static final Map<String, String> REASONS = Map.of("wrong_answer", "Réponse fausse", "ambiguous", "Question ambiguë",
            "language", "Faute de langue", "out_of_scope", "Hors programme", "difficulty", "Trop facile ou trop difficile", "other", "Autre");
    public static final Pattern ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.:+-]{0,63}");
    public static final Pattern HASH = Pattern.compile("[0-9a-f]{16}");
    public static final int MAX_NOTE = 200;

    /** Spellings accepted for a state (sources, CSV of reviewers); null if unknown. */
    public static String state(String raw) {
        if (raw == null) return null;
        return switch (raw.trim().toLowerCase()) {
            case "review", "draft", "beta", "reviewed", "à relire", "a relire" -> "review";
            case "validated", "approved", "validé", "valide" -> "validated";
            case "rejected", "rejeté", "rejete" -> "rejected";
            case "needs-fix", "needs_fix", "needsfix", "à corriger", "a corriger" -> "needs-fix";
            default -> null;
        };
    }

    public static boolean canMove(String from, String to) {
        return from.equals(to) || NEXT.getOrDefault(from, Set.of()).contains(to);
    }

    /** No control character, one space between words, at most 200 characters. */
    public static String cleanNote(String s, int max) {
        if (s == null) return "";
        String t = s.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "").replaceAll("\\s+", " ").trim();
        return t.length() > max ? t.substring(0, max).trim() : t;
    }

    // ------------------------------------------------------------------ suspicious score (same function and vectors as QualitySignals.kt)

    public static final int MIN_SHOWN = 30, REPORTS_FOR_MAX = 5, REPORTS_FLAG = 3;
    public static final double FLAG_AT = 0.5;
    private static final double[] EXPECTED = {0.90, 0.78, 0.65, 0.50, 0.35};

    public record Suspicion(double score, List<String> reasons) {
        public boolean flagged() { return score >= FLAG_AT; }
    }

    public static double expected(int difficulty) { return EXPECTED[Math.max(1, Math.min(5, difficulty)) - 1]; }

    /** 95 % Wilson interval {low, high}. */
    public static double[] wilson(long correct, long shown) {
        if (shown == 0) return new double[] {0, 1};
        double z = 1.96, n = shown, p = correct / n, d = 1 + z * z / n;
        double centre = (p + z * z / (2 * n)) / d;
        double half = z * Math.sqrt(p * (1 - p) / n + z * z / (4 * n * n)) / d;
        return new double[] {Math.max(0, centre - half), Math.min(1, centre + half)};
    }

    /** @param difficulty 1..5, null = unknown (then 3) */
    public static Suspicion suspicion(Integer difficulty, long shown, long correct, long reports) {
        List<String> reasons = new java.util.ArrayList<>();
        double rate = 0;
        if (shown >= MIN_SHOWN) {
            double exp = expected(difficulty == null ? 3 : difficulty);
            double[] w = wilson(correct, shown);
            if (w[1] < exp) {
                rate = Math.min(1, (exp - w[1]) / 0.30);
                reasons.add("réussite trop basse (" + pct(correct, shown) + " % pour " + Math.round(100 * exp) + " % attendus)");
            } else if (w[0] > exp) {
                rate = Math.min(1, (w[0] - exp) / 0.30) / 2;
                reasons.add("réussite trop haute (" + pct(correct, shown) + " % pour " + Math.round(100 * exp) + " % attendus)");
            }
            if (w[1] < 0.25) {
                rate = 1;
                reasons.add("réussite sous le hasard : clé de réponse probablement fausse");
            }
        }
        double rep = Math.min(1, (double) reports / REPORTS_FOR_MAX);
        if (reports > 0) reasons.add(reports + " signalement(s)");
        double score = Math.min(1, 0.6 * rate + 0.4 * rep);
        if (reports >= REPORTS_FLAG) score = Math.max(score, FLAG_AT);
        return new Suspicion(score, reasons);
    }

    private static long pct(long c, long n) { return Math.round(100.0 * c / n); }
}
