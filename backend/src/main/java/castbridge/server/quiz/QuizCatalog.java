package castbridge.server.quiz;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Tracks, levels and fields: the same catalog as the TV app (android/core castbridge.core.quiz.QuizCatalog on the
 * feat/tv-quiz branch). Field keys are ASCII ("economie", "mathematiques"); accented input is accepted and normalized.
 */
public final class QuizCatalog {
    private QuizCatalog() {}

    public static final List<String> TRACKS = List.of("general", "primary", "secondary", "higher");
    public static final Set<String> REGIONS = Set.of("CM", "AF", "WORLD");
    public static final Set<String> STATUSES = Set.of("draft", "reviewed", "rejected");

    /** level key → track */
    public static final Map<String, String> LEVELS;
    public static final List<String> FIELDS = List.of("droit", "economie", "mathematiques", "physique", "psychologie", "geographie",
            "litterature", "histoire", "informatique", "chimie", "biologie", "philosophie", "sociologie");

    static {
        Map<String, String> m = new LinkedHashMap<>();
        for (String l : List.of("SIL", "CP", "CE1", "CE2", "CM1", "CM2")) m.put(l, "primary");
        for (int i = 1; i <= 6; i++) m.put("Class " + i, "primary");
        for (String l : List.of("6e", "5e", "4e", "3e", "2nde", "1re", "Tle")) m.put(l, "secondary");
        for (int i = 1; i <= 5; i++) m.put("Form " + i, "secondary");
        m.put("Lower Sixth", "secondary");
        m.put("Upper Sixth", "secondary");
        for (String l : List.of("L1", "L2", "L3")) m.put(l, "higher");
        LEVELS = java.util.Collections.unmodifiableMap(m);
    }

    /** "Économie" → "economie"; null/blank → null. */
    public static String fieldKey(String s) {
        if (s == null || s.isBlank()) return null;
        String n = Normalizer.normalize(s.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("\\s+", "-");
    }

    /** Case-insensitive lookup of a level key ("form1" and "Form 1" → "Form 1"); null if unknown. */
    public static String levelKey(String s) {
        if (s == null || s.isBlank()) return null;
        String want = s.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        for (String k : LEVELS.keySet()) if (k.replaceAll("\\s+", "").toLowerCase(Locale.ROOT).equals(want)) return k;
        return null;
    }

    public static List<String> levels(String track) {
        List<String> out = new ArrayList<>();
        LEVELS.forEach((k, t) -> { if (t.equals(track)) out.add(k); });
        return out;
    }

    /** Text normalized for duplicate detection: trimmed, lower case, single spaces. */
    public static String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
