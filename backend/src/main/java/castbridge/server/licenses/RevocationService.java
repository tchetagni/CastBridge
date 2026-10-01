package castbridge.server.licenses;

import castbridge.server.web.ApiException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * What the TV and the phone may ask the server (routes OFF by default, switch castbridge.licenses.public-routes):
 * <ul>
 *   <li>GET /api/v1/revocations : the signed revocation list {@code cbx1.…} (docs/ACTIVATION-FORMAT.md § 7): revoked keys and revoked seats with their
 *       date; a device merges it into what it already knows and applies it at its next online check;</li>
 *   <li>GET /api/v1/entitlements/me?deviceCode=… : what the licence of this device code says now (state, bouquets, end), after the existing
 *       device authentication; it also records a sighting for the abuse alerts.</li>
 * </ul>
 * No route exists for the offline phase: activations travel by Bluetooth, USB file or typing.
 */
@Service
public class RevocationService {
    private final JdbcTemplate jdbc;
    private final LicenseKeyring keyring;
    private final LicenseService licenses;
    private final AbuseService abuse;
    private final LicenseProperties props;

    public RevocationService(JdbcTemplate jdbc, LicenseKeyring keyring, LicenseService licenses, AbuseService abuse, LicenseProperties props) {
        this.jdbc = jdbc;
        this.keyring = keyring;
        this.licenses = licenses;
        this.abuse = abuse;
        this.props = props;
    }

    /** The signed list as one line of text ({@code cbx1.<payload>.<signature>}). */
    public String signedList() {
        if (!keyring.present()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Liste de révocation indisponible : aucune clé de signature serveur");
        Set<String> keys = new HashSet<>();
        Map<String, Long> seats = new HashMap<>();
        jdbc.query("SELECT license_id, seat_id, kid, revoked_at FROM lic_revocation ORDER BY id LIMIT 50000", rs -> {
            if (rs.getString("kid") != null) keys.add(rs.getString("kid"));
            else seats.merge(rs.getString("license_id") + "|" + rs.getString("seat_id"), rs.getTimestamp("revoked_at").getTime(), Math::max); // the latest date wins
        });
        return RevocationList.token(keyring, Instant.now().toEpochMilli(), keys, seats);
    }

    /** Entitlement of a device code. Unknown code or no licence: entitled=false (no information leaks about other codes). */
    public Map<String, Object> entitlement(String rawCode, String deviceSource, String ip) {
        String code = DeviceIdentity.normalize(rawCode);
        if (deviceSource != null) abuse.sighting(code, "phone", deviceSource);
        if (ip != null) abuse.sighting(code, "ip", ip);
        // the seat that holds this code now, or held it at its latest activation (a replaced module changes the code)
        List<Map<String, Object>> found = jdbc.queryForList("SELECT l.license_id, s.id AS seat_pk FROM lic_seat s JOIN lic_license l ON l.id = s.license_pk WHERE s.state = 'ACTIVE' AND"
                + " (s.device_code = ? OR s.id IN (SELECT i.seat_pk FROM lic_issuance i WHERE i.device_code = ? AND i.seat_pk IS NOT NULL)) ORDER BY s.last_seen DESC LIMIT 1", code, code);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("deviceCode", code);
        out.put("trialIssuance", props.trialIssuance());
        if (found.isEmpty()) {
            out.put("entitled", false);
            return out;
        }
        LicenseService.LicenseRow l = licenses.get((String) found.get(0).get("license_id"));
        String eff = l.effectiveState();
        out.put("entitled", eff.equals("ACTIVE") || eff.equals("GRACE"));
        out.put("licenseId", l.wireId());
        out.put("state", eff);
        out.put("endAt", l.endAt());
        out.put("graceDays", l.graceDays());
        out.put("seats", Map.of("allowed", l.seatsAllowed(), "used", l.seatsUsed()));
        out.put("products", licenses.productsOf(l.id()).stream().map(LicenseService.ProductRef::productId).toList());
        jdbc.update("UPDATE lic_seat SET last_seen = ? WHERE id = ?", Timestamp.from(Instant.now()), found.get(0).get("seat_pk"));
        return out;
    }
}
