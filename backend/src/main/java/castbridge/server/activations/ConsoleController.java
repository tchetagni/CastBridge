package castbridge.server.activations;

import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Option A of D-W23-3 (off by default: 404): the challenge and the session of the owner phone console. Public routes: what authenticates is the signature of the phone key. */
@RestController
@RequestMapping("/api/v1/activations/console")
public class ConsoleController {
    private final ConsoleSession sessions;

    public ConsoleController(ConsoleSession sessions) { this.sessions = sessions; }

    @GetMapping("/challenge")
    public ResponseEntity<Map<String, Object>> challenge(@RequestParam(required = false) String kid) {
        String c = sessions.challenge(kid);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("challenge", c, "expiresInSeconds", 120));
    }

    public record SessionRequest(String kid, String challenge, String signature) {}

    @PostMapping("/session")
    public ResponseEntity<Map<String, Object>> session(@RequestBody SessionRequest b) {
        ConsoleSession.Opened o = sessions.open(b.kid(), b.challenge(), b.signature());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("token", o.token(), "expiresInSeconds", o.expiresInSeconds(), "scopes", List.of("ACT_READ", "ACT_JOURNAL_UPLOAD")));
    }
}
