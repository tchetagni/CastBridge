package castbridge.server.licenses;

import castbridge.server.web.ApiException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Les activations de production notifiées qui attendent le propriétaire (W23-05) : {@code GET /api/v1/admin/licenses/registrations} (jamais un jeton : empreinte, clé, licence, motif, code
 * masqué) et {@code POST /api/v1/admin/licenses/registrations/{fp}/decision} avec {@code {"accept":true|false,"reason":"…"}} (motif obligatoire, journal d'audit chaîné). Même garde que
 * le reste de {@code /api/v1/admin/licenses/**} : module des licences allumé (sinon 404), jeton d'administration (rôle propriétaire).
 */
@RestController
@RequestMapping("/api/v1/admin/licenses/registrations")
public class RegistrationDecisionController {
    private final ReportedActivationRegistrar registrar;
    private final LicenseProperties props;

    public RegistrationDecisionController(ReportedActivationRegistrar registrar, LicenseProperties props) {
        this.registrar = registrar;
        this.props = props;
    }

    @GetMapping
    public Map<String, Object> pending(@RequestParam(required = false) Integer limit) {
        Actor.token().require(Role.Permission.LICENSE_READ, props.requireTotp());
        List<ReportedActivationRegistrar.Pending> items = registrar.pending(limit == null ? 100 : limit);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", items);
        return out;
    }

    public record DecisionBody(Boolean accept, String reason) {}

    @PostMapping("/{fp}/decision")
    public ResponseEntity<Map<String, Object>> decide(@PathVariable String fp, @RequestBody DecisionBody b) {
        if (b == null || b.accept() == null) throw ApiException.badRequest("Décision : {\"accept\":true|false,\"reason\":\"…\"} attendu");
        if (!fp.matches("[0-9a-f]{64}")) throw ApiException.badRequest("Empreinte invalide (64 chiffres hexadécimaux)");
        ReportedActivationRegistrar.Registration r = registrar.decide(Actor.token(), fp, b.accept(), b.reason(), Instant.now());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", r.status().name());
        out.put("reason", r.reason());
        out.put("licenseId", r.licenseId());
        out.put("seatId", r.seatId());
        return ResponseEntity.ok(out);
    }
}
