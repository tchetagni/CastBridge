package castbridge.server.telemetry;

import castbridge.server.devices.Device;
import castbridge.server.devices.DeviceService;
import castbridge.server.web.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /api/v1/events/batch: up to 500 usage events, JSON, optionally gzip-compressed (Content-Encoding: gzip),
 * authenticated by the device token. Replaying a batch is harmless (events are de-duplicated on their UUID).
 */
@RestController
@RequestMapping("/api/v1/events")
public class TelemetryController {
    /** Decompressed size limit (also a guard against gzip bombs). */
    static final int MAX_BODY = 2 << 20;

    private final TelemetryService service;
    private final DeviceService devices;
    private final ObjectMapper json;

    public TelemetryController(TelemetryService service, DeviceService devices, ObjectMapper json) {
        this.service = service;
        this.devices = devices;
        this.json = json;
    }

    @PostMapping("/batch")
    public ResponseEntity<TelemetryService.Result> batch(@RequestHeader(name = "Authorization", required = false) String authorization,
                                                         HttpServletRequest req) throws IOException {
        Device device = devices.authenticate(authorization).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED,
                "Jeton d'appareil inconnu : réenregistrez l'appareil (POST /api/v1/devices/register)"));
        byte[] body;
        InputStream in = req.getInputStream();
        String enc = req.getHeader("Content-Encoding");
        if (enc != null && enc.toLowerCase().contains("gzip")) {
            try {
                in = new GZIPInputStream(in);
            } catch (IOException e) {
                throw ApiException.badRequest("Corps gzip invalide");
            }
        } else if (enc != null && !enc.isBlank() && !enc.equalsIgnoreCase("identity")) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content-Encoding accepté : gzip");
        }
        try (InputStream body0 = in) {
            body = body0.readNBytes(MAX_BODY + 1);
        } catch (IOException e) {
            throw ApiException.badRequest("Corps illisible (gzip tronqué ?)");
        }
        if (body.length > MAX_BODY) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "Lot trop volumineux (2 Mo décompressés au plus)");
        JsonNode batch;
        try {
            batch = json.readTree(body);
        } catch (IOException e) {
            throw ApiException.badRequest("JSON illisible");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.ingest(device, batch));
    }
}
