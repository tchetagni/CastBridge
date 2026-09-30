package castbridge.server.quiz;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Strict validation of a question, and its normalized form ready to store. */
public final class QuestionValidator {
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final Pattern LANG = Pattern.compile("[a-z]{2}");

    private QuestionValidator() {}

    /** A valid question, normalized (trimmed texts, catalog keys, generated uuid if none). */
    public record Clean(String uuid, String lang, String track, String level, String field, String region, String category,
                        int difficulty, String question, List<String> choices, int answer, String explanation, String source,
                        String reviewStatus, String dedupKey) {

        boolean sameContent(Question q) {
            return q.getLang().equals(lang) && q.getTrack().equals(track) && eq(q.getLevel(), level) && eq(q.getField(), field)
                    && q.getRegion().equals(region) && q.getCategory().equals(category) && q.getDifficulty() == difficulty
                    && q.getText().equals(question) && q.getChoices().equals(choices) && q.getAnswer() == answer
                    && q.getExplanation().equals(explanation) && q.getSource().equals(source) && q.getReviewStatus().equals(reviewStatus);
        }

        void applyTo(Question q) {
            q.setUuid(uuid);
            q.setLang(lang);
            q.setTrack(track);
            q.setLevel(level);
            q.setField(field);
            q.setRegion(region);
            q.setCategory(category);
            q.setDifficulty(difficulty);
            q.setText(question);
            q.setChoices(choices);
            q.setAnswer(answer);
            q.setExplanation(explanation);
            q.setSource(source);
            q.setReviewStatus(reviewStatus);
            q.setDedupKey(dedupKey);
        }

        private static boolean eq(String a, String b) { return a == null ? b == null : a.equals(b); }
    }

    public record Result(Clean clean, List<String> errors) {
        public boolean ok() { return errors.isEmpty(); }
    }

    /**
     * @param defaultStatus status when the input gives none ("draft" for a new question typed by hand)
     */
    public static Result validate(QuestionDto d, String defaultStatus) {
        List<String> e = new ArrayList<>();
        String uuid = d.inputId();
        if (uuid == null) uuid = UUID.randomUUID().toString();
        else if (!ID.matcher(uuid).matches()) e.add("id : 1 à 64 caractères [A-Za-z0-9_-]");

        String lang = d.lang() == null || d.lang().isBlank() ? "fr" : d.lang().trim().toLowerCase();
        if (!LANG.matcher(lang).matches()) e.add("lang : code de langue à 2 lettres attendu (fr, en…)");

        String track = d.track() == null || d.track().isBlank() ? "general" : d.track().trim().toLowerCase();
        if (!QuizCatalog.TRACKS.contains(track)) e.add("track : general, primary, secondary ou higher attendu");

        String level = null;
        if (d.level() != null && !d.level().isBlank()) {
            level = QuizCatalog.levelKey(d.level());
            if (level == null) e.add("level : niveau inconnu « " + d.level().trim() + " »");
            else if (!QuizCatalog.LEVELS.get(level).equals(track)) e.add("level : " + level + " n'appartient pas au parcours " + track);
        } else if (!"general".equals(track) && QuizCatalog.TRACKS.contains(track)) {
            e.add("level : obligatoire pour le parcours " + track);
        }
        if ("general".equals(track) && level != null) e.add("level : pas de niveau pour la culture générale");

        String field = QuizCatalog.fieldKey(d.field());
        if (field != null && !QuizCatalog.FIELDS.contains(field)) e.add("field : filière inconnue « " + d.field().trim() + " »");
        if ("higher".equals(track) && field == null) e.add("field : filière obligatoire pour le supérieur");

        String region = d.region() == null ? "" : d.region().trim().toUpperCase();
        if (!QuizCatalog.REGIONS.contains(region)) e.add("region : CM, AF ou WORLD attendu");

        String category = trim(d.category());
        if (category.isEmpty() || category.length() > 64) e.add("category : 1 à 64 caractères");

        int difficulty = d.difficulty() == null ? 0 : d.difficulty();
        if (difficulty < 1 || difficulty > 5) e.add("difficulty : entre 1 et 5");

        String question = trim(d.question());
        if (question.isEmpty() || question.length() > 1000) e.add("question : 1 à 1000 caractères");

        List<String> choices = d.choices() == null ? List.of() : d.choices().stream().map(QuestionValidator::trim).toList();
        if (choices.size() != 4) e.add("choices : exactement 4 choix");
        else {
            if (choices.stream().anyMatch(c -> c.isEmpty() || c.length() > 500)) e.add("choices : chaque choix fait 1 à 500 caractères");
            Set<String> seen = new HashSet<>();
            for (String c : choices) if (!seen.add(QuizCatalog.norm(c))) e.add("choices : choix en double « " + c + " »");
        }
        int answer = d.answer() == null ? -1 : d.answer();
        if (answer < 0 || answer > 3) e.add("answer : index du bon choix entre 0 et 3");

        String explanation = trim(d.explanation());
        if (explanation.isEmpty() || explanation.length() > 2000) e.add("explanation : 1 à 2000 caractères");
        String source = trim(d.source());
        if (source.isEmpty() || source.length() > 500) e.add("source : 1 à 500 caractères");

        String status = d.inputStatus(defaultStatus);
        if (!QuizCatalog.STATUSES.contains(status)) e.add("reviewStatus : draft, reviewed ou rejected attendu");

        if (!e.isEmpty()) return new Result(null, e);
        String dedup = dedupKey(question, lang, track, level, field);
        return new Result(new Clean(uuid, lang, track, level, field, region, category, difficulty, question, choices, answer,
                explanation, source, status, dedup), List.of());
    }

    /** Same text (normalized), language, track, level and field = same question. */
    static String dedupKey(String question, String lang, String track, String level, String field) {
        String k = QuizCatalog.norm(question) + "|" + lang + "|" + track + "|" + (level == null ? "" : level) + "|" + (field == null ? "" : field);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(k.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String trim(String s) { return s == null ? "" : s.strip(); }
}
