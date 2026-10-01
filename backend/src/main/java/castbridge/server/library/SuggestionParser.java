package castbridge.server.library;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Strict validation of what the model answers. The answer must be ONE JSON object {@code {"v":1,"results":[...]}} and nothing else (at most the
 * ```json fence some models add around it): prose around the JSON, a second value, a wrong version or a wrong shape reject the WHOLE answer.
 * Inside a valid answer, every result is validated on its own: unknown keys, wrong types (a year written as text), out-of-range index,
 * unknown kind, duplicate index, unusable title: the result is dropped and counted in {@code rejected}; the others are kept.
 */
public final class SuggestionParser {
    public static final int SCHEMA_VERSION = 1;
    static final Set<String> KINDS = Set.of("series", "movie", "music", "clip", "course");
    private static final Set<String> ROOT_KEYS = Set.of("v", "results");
    private static final Set<String> ITEM_KEYS = Set.of("i", "kind", "title", "year", "season", "episode", "episodeTitle", "artist", "confidence");

    public record Parsed(List<NameSuggester.Suggestion> suggestions, int rejected) {}

    private SuggestionParser() {}

    public static Parsed parse(ObjectMapper json, String text, int n) throws NameSuggester.SuggesterException {
        String t = text == null ? "" : text.strip();
        if (t.startsWith("```")) {                                   // one fenced block, nothing before or after
            int nl = t.indexOf('\n');
            if (nl < 0 || !t.endsWith("```") || t.lastIndexOf("```") == 0) throw bad("bloc de code incomplet");
            t = t.substring(nl + 1, t.length() - 3).strip();
        }
        JsonNode root;
        try {
            root = json.copy().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(t);
        } catch (JsonProcessingException e) {
            throw bad("ce n'est pas un JSON seul");
        }
        if (root == null || !root.isObject()) throw bad("l'objet racine est attendu");
        for (var it = root.fieldNames(); it.hasNext(); ) if (!ROOT_KEYS.contains(it.next())) throw bad("clé inconnue à la racine");
        if (!root.path("v").isInt() || root.path("v").asInt() != SCHEMA_VERSION) throw bad("version du schéma");
        JsonNode arr = root.path("results");
        if (!arr.isArray()) throw bad("results : liste attendue");
        if (arr.size() > n) throw bad("plus de résultats que de noms");
        List<NameSuggester.Suggestion> out = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        int rejected = 0;
        for (JsonNode o : arr) {
            NameSuggester.Suggestion s = item(o, n);
            if (s == null || !seen.add(s.index())) rejected++; else out.add(s);
        }
        return new Parsed(out, rejected);
    }

    private static NameSuggester.SuggesterException bad(String why) {
        return new NameSuggester.SuggesterException("Réponse du modèle non conforme (" + why + ")");
    }

    static NameSuggester.Suggestion item(JsonNode o, int n) {
        if (!o.isObject()) return null;
        for (var it = o.fieldNames(); it.hasNext(); ) if (!ITEM_KEYS.contains(it.next())) return null;
        if (!o.path("i").isInt()) return null;
        int i = o.path("i").asInt();
        if (i < 0 || i >= n) return null;
        if (!o.path("kind").isTextual() || !KINDS.contains(o.path("kind").asText().toLowerCase())) return null;
        if (!o.path("title").isTextual()) return null;
        String title = clean(o.path("title").asText(), 100);
        if (title.length() < 2) return null;
        Integer year = optInt(o, "year", 1900, 2100), season = optInt(o, "season", 0, 99), episode = optInt(o, "episode", 0, 9999);
        if (isBad(year) || isBad(season) || isBad(episode)) return null;
        String ep = optText(o, "episodeTitle", 100), artist = optText(o, "artist", 80);
        if ("\u0000".equals(ep) || "\u0000".equals(artist)) return null;
        double conf = 0.5;
        if (o.has("confidence") && !o.get("confidence").isNull()) {
            if (!o.get("confidence").isNumber()) return null;
            conf = Math.max(0, Math.min(1, o.get("confidence").asDouble()));
        }
        return new NameSuggester.Suggestion(i, o.path("kind").asText().toLowerCase(), title, year, season, episode, ep, artist, conf);
    }

    private static boolean isBad(Integer v) { return v != null && v == Integer.MIN_VALUE; }

    /** null when absent / null; {@link Integer#MIN_VALUE} when present but not an integer in range (the whole result is then refused). */
    private static Integer optInt(JsonNode o, String key, int min, int max) {
        JsonNode v = o.get(key);
        if (v == null || v.isNull()) return null;
        if (!v.isInt() || v.asInt() < min || v.asInt() > max) return Integer.MIN_VALUE;
        return v.asInt();
    }

    private static String optText(JsonNode o, String key, int max) {
        JsonNode v = o.get(key);
        if (v == null || v.isNull()) return null;
        if (!v.isTextual()) return "\u0000";
        String c = clean(v.asText(), max);
        return c.length() < 2 ? null : c;
    }

    /** No control characters, path separators or forbidden file-name characters; bounded length. */
    static String clean(String s, int max) {
        String t = s.replaceAll("[\\p{Cntrl}\\\\/:*?\"<>|]", " ").replaceAll("\\s{2,}", " ").trim();
        return t.length() > max ? t.substring(0, max).trim() : t;
    }
}
