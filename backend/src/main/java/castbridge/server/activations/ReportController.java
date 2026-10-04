package castbridge.server.activations;

import castbridge.server.devices.Device;
import castbridge.server.devices.DeviceService;
import castbridge.server.web.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public (CastBridge-TV): {@code POST /api/v1/activations/report} with {@code Authorization: Bearer <device token>} (same mechanism as the play ticket). The TV says which
 * activations it carries (at most 4 tokens, 8 192 characters each, 16 KB in all), its edition, its version and the owner commands still active. Tokens are verified and
 * thrown away (see {@link ActivationObserver}). One report per 10 minutes per device, 3 000 a minute for everybody. The answer {@code {"next": hours, "accepted", "ignored"}}
 * lets the server slow the reports or cut them ({@code next = 0}) without delivering an APK. Not a statistic: a licence function, outside the usage consent.
 */
@RestController
@RequestMapping("/api/v1/activations")
public class ReportController {
    static final int MAX_BODY = 16 * 1024, MAX_TOKENS = 4, MAX_TOKEN_CHARS = 8192, MAX_COMPACT = 2;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> VIAS = Set.of("direct");

    private final DeviceService devices;
    private final ActivationObserver observer;
    private final ActivationsPolicy policy;

    public ReportController(DeviceService devices, ActivationObserver observer, ActivationsPolicy policy) {
        this.devices = devices;
        this.observer = observer;
        this.policy = policy;
    }

    @PostMapping("/report")
    public ResponseEntity<Map<String, Object>> report(@RequestHeader(name = "Authorization", required = false) String authorization, HttpServletRequest req) throws IOException {
        Device d = devices.authenticate(authorization).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Jeton d'appareil inconnu"));
        if (d.blocked) throw new ApiException(HttpStatus.FORBIDDEN, "Cet appareil est bloqué par l'administrateur");
        byte[] raw = req.getInputStream().readNBytes(MAX_BODY + 1);
        if (raw.length > MAX_BODY) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "Rapport trop gros (16 Ko au plus)");
        JsonNode n;
        try {
            n = JSON.readTree(raw);
        } catch (IOException e) {
            throw ApiException.badRequest("Rapport illisible : du JSON est attendu");
        }
        if (n == null || !n.isObject() || n.path("v").asInt(0) != 1) throw ApiException.badRequest("Rapport : version 1 attendue");
        String code = n.path("deviceCode").asText("");
        if (TvRef.canonical(code) == null) throw ApiException.badRequest("Code d'appareil invalide : 16 caractères au format XXXX-XXXX-XXXX-XXXX, avec son caractère de contrôle");
        List<String> tokens = strings(n.path("activations"), MAX_TOKENS, MAX_TOKEN_CHARS, "activations");
        List<String> compact = strings(n.path("compact"), MAX_COMPACT, 200, "compact");
        JsonNode st = n.path("state");
        Map<String, Long> installed = new LinkedHashMap<>();
        st.path("installedAt").fields().forEachRemaining(e -> {
            if (installed.size() < 8 && e.getKey().matches("[0-9a-f]{8}") && e.getValue().canConvertToLong()) installed.put(e.getKey(), e.getValue().asLong());
        });
        List<ActivationObserver.Cmd> cmds = new ArrayList<>();
        for (JsonNode c : st.path("commands")) {
            if (cmds.size() >= 16) break;
            if (c.isArray() && c.size() >= 4) cmds.add(new ActivationObserver.Cmd(c.get(0).asText(""), c.get(1).asText(""), c.get(2).asLong(0), (int) Math.max(0, Math.min(c.get(3).asLong(0), 3660))));
        }
        JsonNode app = n.path("app");
        ActivationObserver.ReportState state = new ActivationObserver.ReportState(code, app.path("code").canConvertToInt() && app.has("code") ? app.path("code").asInt() : null,
                app.path("name").isTextual() ? app.path("name").asText() : null, st.path("edition").asText("NONE"), st.path("usageTo").canConvertToLong() && !st.path("usageTo").isNull() ? st.path("usageTo").asLong() : null,
                st.path("super").asBoolean(false), st.path("openAllUntil").asLong(0), st.path("unlockUntil").asLong(0), (int) Math.max(0, Math.min(st.path("trialResets").asLong(0), 1000)), installed, cmds,
                n.path("at").asLong(0), compact);
        policy.limit("report-all", 3000, Duration.ofMinutes(1), "rapports de toutes les TV");
        policy.limit("report:" + d.id, 1, Duration.ofMinutes(10), "un rapport par appareil");
        ActivationObserver.Observation o = observer.observe(d.id, tokens, state, "direct");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("next", policy.get(ActivationsPolicy.REPORT_NEXT_HOURS));
        out.put("accepted", o.accepted());
        out.put("ignored", o.ignored());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(out);
    }

    private static List<String> strings(JsonNode a, int max, int maxChars, String what) {
        List<String> out = new ArrayList<>();
        if (a.isMissingNode() || a.isNull()) return out;
        if (!a.isArray() || a.size() > max) throw ApiException.badRequest(what + " : " + max + " au plus");
        for (JsonNode t : a) {
            if (!t.isTextual() || t.asText().length() > maxChars) throw ApiException.badRequest(what + " : texte de " + maxChars + " caractères au plus");
            out.add(t.asText().trim());
        }
        return out;
    }
}
