package castbridge.server.licenses;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import castbridge.server.web.ApiException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * What the TV and the phone may ask the server (routes OFF by default, switch castbridge.licenses.public-routes):
 * <ul>
 *   <li>GET /api/v1/revocations : the signed list of revoked licences / devices / issuances (a device applies it at its next online check);</li>
 *   <li>GET /api/v1/entitlements/me?deviceCode=… : what the licence of this device code says now (state, bouquets, end), after the existing
 *       device authentication; it also records a sighting for the abuse alerts.</li>
 * </ul>
 * No route exists for the offline phase: activations travel by Bluetooth, USB file or typing.
 */
@Service
public class RevocationService {
    private final JdbcTemplate jdbc;
    private final LicenseKeyring keyring;
    private final ObjectMapper json;
    private final LicenseService licenses;
    private final AbuseService abuse;
    private final LicenseProperties props;

    public RevocationService(JdbcTemplate jdbc, LicenseKeyring keyring, ObjectMapper json, LicenseService licenses, AbuseService abuse, LicenseProperties props) {
        this.jdbc = jdbc;
        this.keyring = keyring;
        this.json = json;
        this.licenses = licenses;
        this.abuse = abuse;
        this.props = props;
    }

    /** {"v":1,"kid","payload":base64({"generatedAt","seq","revoked":[…]}),"sig":base64} ; seq = id of the last revocation (a device keeps the highest it saw). */
    public ObjectNode signedList() {
        if (!keyring.present()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Liste de révocation indisponible : aucune clé de signature serveur");
        ObjectNode payload = json.createObjectNode();
        payload.put("generatedAt", Instant.now().toString());
        Long seq = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM lic_revocation", Long.class);
        payload.put("seq", seq);
        ArrayNode list = payload.putArray("revoked");
        jdbc.query("SELECT * FROM lic_revocation ORDER BY id LIMIT 20000", rs -> {
            ObjectNode e = list.addObject();
            if (rs.getString("license_id") != null) e.put("licenseId", rs.getString("license_id"));
            if (rs.getString("device_code") != null) e.put("deviceCode", rs.getString("device_code"));
            if (rs.getString("kid") != null) e.put("kid", rs.getString("kid"));
            if (rs.getString("nonce") != null) e.put("nonce", rs.getString("nonce"));
            e.put("at", rs.getTimestamp("revoked_at").toInstant().toString());
        });
        byte[] bytes;
        try {
            bytes = json.writeValueAsBytes(payload);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        ObjectNode env = json.createObjectNode();
        env.put("v", 1);
        env.put("kid", keyring.kid());
        env.put("payload", Base64.getEncoder().encodeToString(bytes));
        env.put("sig", Base64.getEncoder().encodeToString(keyring.sign(bytes)));
        return env;
    }

    /** Entitlement of a device code. Unknown code or no licence: entitled=false (no information leaks about other codes). */
    public Map<String, Object> entitlement(String rawCode, String deviceSource, String ip) {
        String code = DeviceCode.normalize(rawCode);
        if (deviceSource != null) abuse.sighting(code, "phone", deviceSource);
        if (ip != null) abuse.sighting(code, "ip", ip);
        List<Map<String, Object>> found = jdbc.queryForList("SELECT l.license_id FROM lic_seat s JOIN lic_license l ON l.id = s.license_pk WHERE s.device_code = ? AND s.state = 'ACTIVE'"
                + " ORDER BY s.last_seen DESC LIMIT 1", code);
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("deviceCode", code);
        out.put("trialIssuance", props.trialIssuance());
        if (found.isEmpty()) {
            out.put("entitled", false);
            return out;
        }
        LicenseService.LicenseRow l = licenses.get((String) found.get(0).get("license_id"));
        String eff = l.effectiveState();
        out.put("entitled", eff.equals("ACTIVE") || eff.equals("GRACE"));
        out.put("licenseId", l.licenseId());
        out.put("state", eff);
        out.put("endAt", l.endAt());
        out.put("graceDays", l.graceDays());
        out.put("seats", Map.of("allowed", l.seatsAllowed(), "used", l.seatsUsed()));
        out.put("products", licenses.productsOf(l.id()).stream().map(LicenseService.ProductRef::productId).toList());
        jdbc.update("UPDATE lic_seat SET last_seen = ? WHERE device_code = ? AND state = 'ACTIVE'", Timestamp.from(Instant.now()), code);
        return out;
    }
}
