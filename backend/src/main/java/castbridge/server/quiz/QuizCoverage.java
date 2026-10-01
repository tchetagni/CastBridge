package castbridge.server.quiz;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;

/**
 * « Banque suffisante pour N parties sans répétition » per course (docs/QUIZ.md, règle des 300 parties): the questions
 * published on the server plus the question packs the server offers, against the goal of 300 games of 15 questions
 * (4 500 questions, 70/20/10 Cameroon/Africa/World for general knowledge).
 */
@Service
public class QuizCoverage {
    public record Row(String course, String label, long server, long packs, long total, int games, int targetGames, boolean enough, long missing,
                      Map<String, Long> byRegion) {}

    private final QuestionRepository questions;
    private final QuizPackService packs;

    public QuizCoverage(QuestionRepository questions, QuizPackService packs) {
        this.questions = questions;
        this.packs = packs;
    }

    public List<Row> rows() {
        Map<String, Map<String, Long>> server = new TreeMap<>(), inPacks = new TreeMap<>();
        for (Object[] r : questions.coverageRows()) {
            String course = course((String) r[0], (String) r[1], (String) r[2]);
            server.computeIfAbsent(course, k -> new LinkedHashMap<>()).merge((String) r[3], (Long) r[4], Long::sum);
        }
        for (QuizPackService.Pack p : this.packs.packs()) {
            Map<String, Long> m = inPacks.computeIfAbsent(p.course(), k -> new LinkedHashMap<>());
            if (p.byRegion().isEmpty()) m.merge("WORLD", (long) p.questions(), Long::sum);
            else p.byRegion().forEach((k, v) -> m.merge(k, (long) v, Long::sum));
        }
        List<String> courses = new ArrayList<>(server.keySet());
        inPacks.keySet().stream().filter(c -> !courses.contains(c)).forEach(courses::add);
        courses.sort(Comparator.comparingInt(QuizCoverage::order).thenComparing(c -> c));
        List<Row> out = new ArrayList<>();
        for (String c : courses) {
            Map<String, Long> s = server.getOrDefault(c, Map.of()), pk = inPacks.getOrDefault(c, Map.of());
            Map<String, Long> all = new LinkedHashMap<>();
            for (String reg : List.of("CM", "AF", "WORLD")) all.put(reg, s.getOrDefault(reg, 0L) + pk.getOrDefault(reg, 0L));
            long total = all.values().stream().mapToLong(Long::longValue).sum();
            boolean general = c.equals("general");
            Map<String, Integer> asInt = new LinkedHashMap<>();
            all.forEach((k, v) -> asInt.put(k, v.intValue()));
            int games = QuizPackService.games(general ? "general" : "x", asInt, (int) total);
            long need = (long) QuizPackService.TARGET_GAMES * QuizPackService.PER_GAME;
            long missing = general
                    ? Math.max(0, (long) (0.7 * need) - all.get("CM")) + Math.max(0, (long) (0.2 * need) - all.get("AF")) + Math.max(0, (long) (0.1 * need) - all.get("WORLD"))
                    : Math.max(0, need - total);
            out.add(new Row(c, label(c), s.values().stream().mapToLong(Long::longValue).sum(), pk.values().stream().mapToLong(Long::longValue).sum(),
                    total, games, QuizPackService.TARGET_GAMES, games >= QuizPackService.TARGET_GAMES, missing, all));
        }
        return out;
    }

    static String course(String track, String level, String field) {
        StringBuilder b = new StringBuilder(track);
        if (level != null) b.append('/').append(level);
        if (field != null) b.append('/').append(field);
        return b.toString();
    }

    private static int order(String course) {
        return course.equals("general") ? 0 : course.startsWith("primary") ? 1 : course.startsWith("secondary") ? 2 : 3;
    }

    private static final Map<String, String> FIELDS = Map.of("droit", "Droit", "economie", "Économie", "mathematiques", "Mathématiques", "physique", "Physique",
            "informatique", "Informatique", "histoire", "Histoire", "geographie", "Géographie", "chimie", "Chimie");

    static String label(String course) {
        String[] p = course.split("/");
        String track = switch (p[0]) {
            case "general" -> "Culture générale";
            case "primary" -> "Primaire";
            case "secondary" -> "Secondaire";
            case "higher" -> "Supérieur";
            default -> p[0];
        };
        StringBuilder b = new StringBuilder(track);
        for (int i = 1; i < p.length; i++) b.append(" · ").append(FIELDS.getOrDefault(p[i], p[i]));
        return b.toString();
    }
}
