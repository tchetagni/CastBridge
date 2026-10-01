package castbridge.server.quiz;

import castbridge.server.web.ApiException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin (bearer token): edit the question bank, bulk import/export in JSON or CSV. */
@RestController
@RequestMapping("/api/v1/admin/quiz")
public class AdminQuizController {
    private static final int MAX_IMPORT_BYTES = 20 << 20;

    private final QuizService service;
    private final ObjectMapper json;
    private final QuizCoverage coverage;

    public AdminQuizController(QuizService service, ObjectMapper json, QuizCoverage coverage) {
        this.service = service;
        this.json = json;
        this.coverage = coverage;
    }

    /** « Banque suffisante pour N parties sans répétition » per course (published questions + question packs). */
    @GetMapping("/coverage")
    public Map<String, Object> coverage() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("targetGames", QuizPackService.TARGET_GAMES);
        out.put("perGame", QuizPackService.PER_GAME);
        out.put("courses", coverage.rows());
        return out;
    }

    @GetMapping("/questions")
    public Map<String, Object> list(@RequestParam(required = false) String status, @RequestParam(required = false) String track,
                                    @RequestParam(required = false) String level, @RequestParam(required = false) String field,
                                    @RequestParam(required = false) String region, @RequestParam(required = false) String q,
                                    @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        Page<Question> res = service.search(status, track, level, field, region, q, page, size);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("page", res.getNumber());
        out.put("size", res.getSize());
        out.put("totalPages", res.getTotalPages());
        out.put("total", res.getTotalElements());
        out.put("questions", res.map(QuestionDto::of).getContent());
        return out;
    }

    @GetMapping("/questions/{uuid}")
    public ResponseEntity<QuestionDto> get(@PathVariable String uuid) {
        Question q = service.get(uuid);
        return ResponseEntity.ok().eTag("\"" + q.getVersion() + "\"").body(QuestionDto.of(q));
    }

    @PostMapping("/questions")
    public ResponseEntity<QuestionDto> create(@RequestBody QuestionDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(QuestionDto.of(service.create(dto)));
    }

    @PutMapping("/questions/{uuid}")
    public QuestionDto update(@PathVariable String uuid, @RequestBody QuestionDto dto) {
        return QuestionDto.of(service.update(uuid, dto));
    }

    @PostMapping("/questions/{uuid}/status")
    public QuestionDto status(@PathVariable String uuid, @RequestParam String value) {
        return QuestionDto.of(service.setStatus(uuid, value.trim().toLowerCase()));
    }

    @DeleteMapping("/questions/{uuid}")
    public ResponseEntity<Void> delete(@PathVariable String uuid) {
        service.delete(uuid);
        return ResponseEntity.noContent().build();
    }

    /**
     * Body: the JSON exchange format ({"version":2,"questions":[…]} or a bare array) or CSV (format=csv or a text/csv
     * Content-Type). {@code dryRun=true} only validates. {@code defaultStatus} applies to questions without status.
     */
    @PostMapping("/import")
    public QuizService.ImportReport importQuestions(HttpServletRequest req,
                                                    @RequestParam(required = false) String format,
                                                    @RequestParam(defaultValue = "false") boolean dryRun,
                                                    @RequestParam(defaultValue = "draft") String defaultStatus) throws IOException {
        byte[] body = req.getInputStream().readNBytes(MAX_IMPORT_BYTES + 1);
        if (body.length > MAX_IMPORT_BYTES) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "Import limité à 20 Mo");
        String text = new String(body, StandardCharsets.UTF_8);
        String ct = req.getContentType() == null ? "" : req.getContentType().toLowerCase();
        boolean csv = "csv".equalsIgnoreCase(format) || (format == null && ct.startsWith("text/csv"));
        if (!QuizCatalog.STATUSES.contains(defaultStatus)) throw ApiException.badRequest("defaultStatus : draft, reviewed ou rejected");
        List<QuestionDto> batch = csv ? QuizCsv.read(text) : parseJson(text);
        return service.importAll(batch, dryRun, defaultStatus);
    }

    List<QuestionDto> parseJson(String text) {
        try {
            JsonNode root = json.readTree(text);
            JsonNode list = root != null && root.isObject() ? root.get("questions") : root;
            if (list == null || !list.isArray()) throw ApiException.badRequest("JSON attendu : {\"questions\":[…]} ou un tableau de questions");
            if (root.isObject() && root.has("version") && root.get("version").asInt() > QuizService.FORMAT_VERSION)
                throw ApiException.badRequest("Version de format " + root.get("version").asInt() + " non prise en charge (max "
                        + QuizService.FORMAT_VERSION + ")");
            return json.convertValue(list, new TypeReference<List<QuestionDto>>() {});
        } catch (IOException | IllegalArgumentException e) {
            throw ApiException.badRequest("JSON illisible ou champ de type inattendu");
        }
    }

    @GetMapping("/export")
    public ResponseEntity<?> export(@RequestParam(defaultValue = "json") String format, @RequestParam(required = false) String status,
                                    @RequestParam(defaultValue = ",") String sep) {
        List<QuestionDto> all = service.export(status);
        String stamp = LocalDate.now(castbridge.server.CastbridgeApplication.ZONE).toString();
        if ("csv".equalsIgnoreCase(format)) {
            char c = ";".equals(sep) ? ';' : ',';
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"questions-" + stamp + ".csv\"")
                    .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                    .body(QuizCsv.write(all, c));
        }
        if (!"json".equalsIgnoreCase(format)) throw ApiException.badRequest("format : json ou csv");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("version", QuizService.FORMAT_VERSION);
        out.put("questions", all);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"questions-" + stamp + ".json\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(out);
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() { return service.stats(); }
}
