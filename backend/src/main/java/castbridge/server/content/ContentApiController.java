package castbridge.server.content;

import castbridge.server.devices.Device;
import castbridge.server.devices.DeviceService;
import castbridge.server.web.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Content validation API (docs/CONTENT-VALIDATION.md). Devices: POST /api/v1/content/reports (device token). Admin (bearer token,
 * /api/v1/admin/**): the index import, the decisions as records, the progress and the content budget.
 */
@RestController
public class ContentApiController {
    static final int MAX_REPORTS_BODY = 128 << 10;
    static final int MAX_INDEX_BODY = 64 << 20;

    private final ContentService content;
    private final ContentBudget budget;
    private final DeviceService devices;
    private final com.fasterxml.jackson.databind.ObjectMapper json;

    public ContentApiController(ContentService content, ContentBudget budget, DeviceService devices, com.fasterxml.jackson.databind.ObjectMapper json) {
        this.content = content;
        this.budget = budget;
        this.devices = devices;
        this.json = json;
    }

    @PostMapping("/api/v1/content/reports")
    public ResponseEntity<ContentService.ReportResult> reports(@RequestHeader(name = "Authorization", required = false) String authorization,
                                                               HttpServletRequest req) throws IOException {
        Device device = devices.authenticate(authorization).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED,
                "Jeton d'appareil inconnu : réenregistrez l'appareil (POST /api/v1/devices/register)"));
        byte[] body = req.getInputStream().readNBytes(MAX_REPORTS_BODY + 1);
        if (body.length > MAX_REPORTS_BODY) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "Envoi trop volumineux");
        JsonNode root;
        try {
            root = json.readTree(body);
        } catch (IOException e) {
            throw ApiException.badRequest("JSON illisible");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(content.ingestReports(device, root));
    }

    /** Body: one JSON per line (output of tools/content-validation/cbvalidate.py index). */
    @PostMapping(value = "/api/v1/admin/content/index", consumes = MediaType.ALL_VALUE)
    public ContentService.ImportResult importIndex(HttpServletRequest req) throws IOException {
        List<String> lines = new ArrayList<>();
        long size = 0;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(req.getInputStream(), StandardCharsets.UTF_8))) {
            String l;
            while ((l = r.readLine()) != null) {
                size += l.length() + 1;
                if (size > MAX_INDEX_BODY) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "Index trop volumineux (64 Mo au plus)");
                lines.add(l);
            }
        }
        return content.importIndex(lines);
    }

    /** The decisions in the record format of tools/content-validation (append them to content/validation/*.jsonl, then run apply). */
    @GetMapping(value = "/api/v1/admin/content/records", produces = "application/x-ndjson;charset=UTF-8")
    public String records() { return content.exportRecords(); }

    @GetMapping("/api/v1/admin/content/progress")
    public Map<String, Object> progress() {
        return Map.of("lots", content.progress(false), "classes", content.progress(true));
    }

    /** Content budget; {@code ?strict=true} answers 507 above the limit (so that a script or CI fails). */
    @GetMapping("/api/v1/admin/content/budget")
    public ResponseEntity<ContentBudget.Report> budget(@org.springframework.web.bind.annotation.RequestParam(defaultValue = "false") boolean strict) {
        ContentBudget.Report r = budget.report();
        return ResponseEntity.status(strict && "fail".equals(r.status()) ? HttpStatus.INSUFFICIENT_STORAGE : HttpStatus.OK)
                .cacheControl(CacheControl.noStore()).body(r);
    }
}
