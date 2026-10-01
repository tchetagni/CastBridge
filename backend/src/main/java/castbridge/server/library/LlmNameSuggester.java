package castbridge.server.library;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Asks an LLM (Messages API: {@code POST llmUrl}, header {@code x-api-key}) to read cleaned file names. The prompt contains only the
 * names ("prison break s01e04"), their extension, a kind hint and a rounded duration: nothing that identifies a person or a device.
 * The answer is parsed defensively (the model may add text around the JSON) and every field is validated before it goes back.
 */
public class LlmNameSuggester implements NameSuggester {
    static final Set<String> KINDS = Set.of("series", "movie", "music", "clip", "course");
    private static final String SYSTEM = """
            Tu aides une application qui range des fichiers vidéo et audio. Tu reçois une liste de noms de fichiers déjà nettoyés
            (par exemple « prison break s01e04 »), avec leur extension, parfois un indice de type et une durée en minutes.
            Pour chaque nom que tu reconnais avec une bonne confiance, donne le type (series, movie, music, clip ou course), le titre
            correctement écrit, et si possible l'année, la saison, l'épisode, le titre de l'épisode, l'artiste.
            N'invente rien : si tu hésites, n'inclus pas l'élément. Réponds UNIQUEMENT par un tableau JSON, sans autre texte, de la forme :
            [{"i":0,"kind":"series","title":"Prison Break","year":null,"season":1,"episode":4,"episodeTitle":"Cut Off","artist":null,"confidence":0.8}]
            """;

    private final String url;
    private final String apiKey;
    private final String model;
    private final HttpClient http;
    private final ObjectMapper json;

    public LlmNameSuggester(String url, String apiKey, String model, ObjectMapper json) {
        this(url, apiKey, model, json, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build());
    }

    LlmNameSuggester(String url, String apiKey, String model, ObjectMapper json, HttpClient http) {
        this.url = url;
        this.apiKey = apiKey;
        this.model = model;
        this.json = json;
        this.http = http;
    }

    @Override public String model() { return model; }

    @Override public boolean available() { return true; }

    @Override
    public List<Suggestion> suggest(String lang, List<Item> items) throws SuggesterException {
        if (items.isEmpty()) return List.of();
        ObjectNode body = json.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", 2048);
        body.put("system", SYSTEM + ("en".equals(lang) ? "Les titres restent dans leur langue d'origine." : ""));
        ArrayNode list = json.createArrayNode();
        for (Item it : items) {
            ObjectNode o = list.addObject();
            o.put("i", it.index());
            o.put("nom", it.text());
            o.put("extension", it.ext());
            if (it.kindHint() != null) o.put("indice", it.kindHint());
            if (it.durationMin() != null) o.put("duree_min", it.durationMin());
        }
        body.putArray("messages").addObject().put("role", "user").put("content", list.toString());
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(25))
                    .header("content-type", "application/json").header("x-api-key", apiKey).header("anthropic-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) throw new SuggesterException("Le modèle a répondu " + res.statusCode());
            return parse(res.body(), items.size());
        } catch (IOException e) {
            throw new SuggesterException("Le modèle est injoignable", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SuggesterException("Interrompu", e);
        }
    }

    /** Extracts the JSON array from the model's text (Messages API: content[0].text) and keeps only valid, bounded suggestions. */
    List<Suggestion> parse(String responseBody, int n) throws SuggesterException {
        try {
            JsonNode root = json.readTree(responseBody);
            StringBuilder text = new StringBuilder();
            for (JsonNode c : root.path("content")) if ("text".equals(c.path("type").asText())) text.append(c.path("text").asText());
            String t = text.toString();
            int a = t.indexOf('['), b = t.lastIndexOf(']');
            if (a < 0 || b <= a) return List.of();
            JsonNode arr = json.readTree(t.substring(a, b + 1));
            List<Suggestion> out = new ArrayList<>();
            for (JsonNode o : arr) {
                Suggestion s = sanitize(o, n);
                if (s != null) out.add(s);
            }
            return out;
        } catch (IOException e) {
            throw new SuggesterException("Réponse du modèle illisible", e);
        }
    }

    static Suggestion sanitize(JsonNode o, int n) {
        if (!o.isObject() || !o.path("i").isInt()) return null;
        int i = o.path("i").asInt();
        String kind = o.path("kind").asText("").toLowerCase();
        String title = clean(o.path("title").asText(""), 100);
        if (i < 0 || i >= n || !KINDS.contains(kind) || title.length() < 2) return null;
        Integer year = bounded(o.path("year"), 1900, 2100);
        Integer season = bounded(o.path("season"), 0, 99);
        Integer episode = bounded(o.path("episode"), 0, 9999);
        String ep = clean(o.path("episodeTitle").asText(""), 100);
        String artist = clean(o.path("artist").asText(""), 80);
        double conf = o.path("confidence").isNumber() ? Math.max(0, Math.min(1, o.path("confidence").asDouble())) : 0.5;
        return new Suggestion(i, kind, title, year, season, episode, ep.length() < 2 ? null : ep, artist.length() < 2 ? null : artist, conf);
    }

    private static Integer bounded(JsonNode n, int min, int max) {
        return n.isInt() && n.asInt() >= min && n.asInt() <= max ? n.asInt() : null;
    }

    /** No control characters, path separators or forbidden file-name characters; bounded length. */
    static String clean(String s, int max) {
        String t = s.replaceAll("[\\p{Cntrl}\\\\/:*?\"<>|]", " ").replaceAll("\\s{2,}", " ").trim();
        return t.length() > max ? t.substring(0, max).trim() : t;
    }
}
