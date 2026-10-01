package castbridge.server.library;

import castbridge.server.devices.Device;
import castbridge.server.devices.DeviceService;
import castbridge.server.web.ApiError;
import castbridge.server.web.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /api/v1/library/suggest: the optional AI layer of the phone's library assistant (docs/LIBRARY-AGENT.md).
 *
 * <p>Receives ONLY cleaned file names and minimal metadata ({@code {"consent":"library-ai-v1","lang":"fr","items":[{"i":0,"t":"prison break
 * s01e04","x":"mkv","k":"series","d":45}]}}). It never receives a file, a path, a size, a date or an identifier of the device in the
 * body; the device is recognised by its token ({@code Authorization: Bearer}, like every device route) only to refuse blocked devices
 * and to limit the rate. Names are neither stored nor logged (the access log keeps the path only). Without an LLM key the answer is
 * {@code {"available":false,"suggestions":[]}} and nothing is called.
 */
@RestController
@RequestMapping("/api/v1/library")
public class LibrarySuggestController {
    static final String CONSENT = "library-ai-v1";
    private static final Set<String> KIND_HINTS = Set.of("series", "movie", "music", "clip", "course");
    private static final Pattern EXT = Pattern.compile("[a-z0-9]{1,5}");
    private static final Pattern URL_OR_MAIL = Pattern.compile("(?i)(?:https?://|www\\.)\\S+|\\S+@\\S+\\.\\S+");
    private static final Pattern LONG_DIGITS = Pattern.compile("\\d{6,}");

    private final DeviceService devices;
    private final NameSuggester suggester;
    private final SuggestLimiter limiter;
    private final int maxItems;

    public LibrarySuggestController(DeviceService devices, NameSuggester suggester, LibrarySuggestProperties props) {
        this.devices = devices;
        this.suggester = suggester;
        this.maxItems = props.maxItems();
        this.limiter = new SuggestLimiter(props.perHour(), System::currentTimeMillis);
    }

    @Scheduled(fixedDelay = 600_000)
    void evict() { limiter.evictIdle(); }

    @PostMapping("/suggest")
    public ResponseEntity<?> suggest(@RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                                     @RequestBody JsonNode body, HttpServletRequest req) {
        Device d = devices.authenticate(authorization).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED,
                "Jeton d'appareil inconnu : réenregistrez l'appareil (POST /api/v1/devices/register)"));
        if (d.blocked) throw new ApiException(HttpStatus.FORBIDDEN, "Cet appareil est bloqué par l'administrateur");
        if (!CONSENT.equals(body.path("consent").asText(null)))
            throw ApiException.badRequest("consent : l'accord séparé « " + CONSENT + " » est obligatoire (aide de l'IA)");
        String lang = body.path("lang").asText("fr");
        if (!lang.equals("fr") && !lang.equals("en")) throw ApiException.badRequest("lang : fr ou en");
        JsonNode arr = body.path("items");
        if (!arr.isArray()) throw ApiException.badRequest("items : liste attendue");
        if (arr.size() > maxItems) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "items : " + maxItems + " noms au plus par demande");
        List<NameSuggester.Item> items = new ArrayList<>();
        for (JsonNode o : arr) items.add(item(o, arr.size()));
        long wait = limiter.tryAcquire(d.id);
        if (wait > 0) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, Long.toString(wait))
                    .cacheControl(CacheControl.noStore()).body(ApiError.of(429, "Trop de demandes : réessayez dans " + wait + " s", List.of(), req.getRequestURI()));
        }
        List<NameSuggester.Suggestion> out;
        if (!suggester.available() || items.isEmpty()) out = List.of();
        else {
            try {
                out = suggester.suggest(lang, items);
            } catch (NameSuggester.SuggesterException e) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Le modèle n'a pas répondu : réessayez plus tard");
            }
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("model", suggester.model());
        res.put("available", suggester.available());
        res.put("suggestions", out.stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("i", s.index()); m.put("kind", s.kind()); m.put("title", s.title()); m.put("year", s.year()); m.put("season", s.season());
            m.put("episode", s.episode()); m.put("episodeTitle", s.episodeTitle()); m.put("artist", s.artist()); m.put("confidence", s.confidence());
            return m;
        }).toList());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(res);
    }

    private NameSuggester.Item item(JsonNode o, int n) {
        int i = o.path("i").isInt() ? o.path("i").asInt() : -1;
        if (i < 0 || i >= n) throw ApiException.badRequest("items[].i : position invalide");
        String t = o.path("t").asText("");
        if (t.length() > 120) throw ApiException.badRequest("items[].t : 120 caractères au plus");
        t = LONG_DIGITS.matcher(URL_OR_MAIL.matcher(t).replaceAll(" ")).replaceAll(" ").replaceAll("[\\p{Cntrl}\\\\/]", " ").replaceAll("\\s{2,}", " ").trim();
        if (t.length() < 2) throw ApiException.badRequest("items[].t : nom trop court");
        String x = o.path("x").asText("").toLowerCase();
        if (!EXT.matcher(x).matches()) throw ApiException.badRequest("items[].x : extension invalide");
        String k = o.path("k").isNull() || o.path("k").isMissingNode() ? null : o.path("k").asText();
        if (k != null && !KIND_HINTS.contains(k)) throw ApiException.badRequest("items[].k : type inconnu");
        Integer dur = o.path("d").isInt() && o.path("d").asInt() >= 0 && o.path("d").asInt() <= 600 ? o.path("d").asInt() : null;
        return new NameSuggester.Item(i, t, x, k, dur);
    }
}
