package castbridge.server.tunnel;

import castbridge.server.web.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public routes of the remote administration (docs/REMOTE-TUNNEL.md): a CastBridge-TV enrols its tunnel; the TVs and tools read the owner's signed experts list. */
@RestController
@RequestMapping("/api/v1/tunnel")
public class TunnelController {
    private final TunnelService tunnel;
    private final ObjectMapper json;

    public TunnelController(TunnelService tunnel, ObjectMapper json) {
        this.tunnel = tunnel;
        this.json = json;
    }

    @PostMapping("/enroll")
    public ResponseEntity<Map<String, Object>> enroll(@RequestBody TunnelService.EnrollRequest req, HttpServletRequest http) {
        TunnelService.Enrolled e = tunnel.enroll(req, http.getRemoteAddr());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(e.wire());
    }

    /** The experts list as the owner signed it (relayed unchanged; 404 when none is published). Its signature is checked by whoever uses it. */
    @GetMapping("/experts")
    public ResponseEntity<byte[]> experts() throws IOException {
        tunnel.requireEnabled();
        Path f = tunnel.expertsFile();
        if (!Files.isRegularFile(f)) throw ApiException.notFound("Liste des experts non publiée sur le serveur");
        byte[] raw = Files.readAllBytes(f);
        try {
            JsonNode n = json.readTree(raw);
            if (n == null || !n.path("experts").isArray() || !n.hasNonNull("signature") || !n.hasNonNull("generatedAt")) throw new IllegalArgumentException("incomplete");
        } catch (IOException | IllegalArgumentException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Liste des experts illisible sur le serveur");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).contentType(new MediaType("application", "json", StandardCharsets.UTF_8)).body(raw);
    }
}
