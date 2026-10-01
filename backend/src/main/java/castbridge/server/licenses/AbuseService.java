package castbridge.server.licenses;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Abuse signals shown on the dashboard. They ALERT, they never block: every decision (suspend, release, revoke) is the
 * owner's. All queries are aggregates with LIMIT (the JVM is capped at 512 MB).
 */
@Service
public class AbuseService {
    private final JdbcTemplate jdbc;
    private final LicenseProperties props;

    public AbuseService(JdbcTemplate jdbc, LicenseProperties props) {
        this.jdbc = jdbc;
        this.props = props;
    }

    public record Alert(String type, String severity, String subject, String detail) {}

    /** Notes that a device code was seen from a phone (device id) or an IP: only a truncated hash of the source is kept. */
    @Transactional
    public void sighting(String deviceCode, String channel, String sourceValue) {
        if (sourceValue == null || sourceValue.isBlank()) return;
        jdbc.update("INSERT INTO lic_sighting (device_code, source_ref, channel, seen_at) VALUES (?,?,?,?)", deviceCode, Hashing.tag("sighting|" + channel + "|" + sourceValue, 16),
                channel, Timestamp.from(Instant.now()));
    }

    public List<Alert> alerts() {
        List<Alert> out = new ArrayList<>();
        Instant now = Instant.now();
        // 1. the same device code holds an ACTIVE seat in several licences
        for (Map<String, Object> r : jdbc.queryForList("SELECT device_code, COUNT(DISTINCT license_pk) AS n FROM lic_seat WHERE state = 'ACTIVE' AND anonymized = FALSE"
                + " GROUP BY device_code HAVING COUNT(DISTINCT license_pk) > 1 LIMIT 50")) {
            out.add(new Alert("DUPLICATE_DEVICE", "warn", DeviceIdentity.masked((String) r.get("device_code")), "Même appareil sur " + r.get("n") + " licences actives"));
        }
        // 2. one device code seen from several phones (>= 2) or several IP addresses (>= 3) within 24 h
        for (Map<String, Object> r : jdbc.queryForList("SELECT device_code, channel, COUNT(DISTINCT source_ref) AS n FROM lic_sighting WHERE seen_at >= ?"
                + " GROUP BY device_code, channel HAVING (channel = 'phone' AND COUNT(DISTINCT source_ref) >= 2) OR (channel = 'ip' AND COUNT(DISTINCT source_ref) >= 3) LIMIT 50", Timestamp.from(now.minus(Duration.ofHours(24))))) {
            boolean phone = "phone".equals(r.get("channel"));
            out.add(new Alert("MULTI_SOURCE", "warn", DeviceIdentity.masked((String) r.get("device_code")),
                    "Vu depuis " + r.get("n") + (phone ? " téléphones" : " adresses IP") + " en 24 h"));
        }
        // 3. seats above the quota, or an import waiting on a quota decision
        for (Map<String, Object> r : jdbc.queryForList("SELECT l.license_id, l.seats_allowed, COUNT(s.id) AS used FROM lic_license l JOIN lic_seat s ON s.license_pk = l.id AND s.state = 'ACTIVE'"
                + " GROUP BY l.id, l.license_id, l.seats_allowed HAVING COUNT(s.id) > l.seats_allowed LIMIT 50")) {
            out.add(new Alert("OVER_QUOTA", "bad", (String) r.get("license_id"), r.get("used") + " postes pour " + r.get("seats_allowed") + " autorisés"));
        }
        for (Map<String, Object> r : jdbc.queryForList("SELECT license_id, COUNT(*) AS n FROM lic_conflict WHERE status = 'OPEN' AND type = 'OVER_QUOTA' GROUP BY license_id LIMIT 50")) {
            out.add(new Alert("OVER_QUOTA_PENDING", "bad", String.valueOf(r.get("license_id")), r.get("n") + " dépassement(s) de postes importé(s) en attente de décision"));
        }
        // 4. bursts of issuances
        for (Map<String, Object> r : jdbc.queryForList("SELECT l.license_id, COUNT(*) AS n FROM lic_issuance i JOIN lic_license l ON l.id = i.license_pk WHERE i.issued_at >= ?"
                + " GROUP BY l.id, l.license_id HAVING COUNT(*) >= ? LIMIT 50", Timestamp.from(now.minus(Duration.ofMinutes(10))), props.burstPer10Min())) {
            out.add(new Alert("ISSUANCE_BURST", "warn", (String) r.get("license_id"), r.get("n") + " émissions en 10 minutes"));
        }
        // 5. repeated transfers: at the yearly cap, or two within 30 days
        for (Map<String, Object> r : jdbc.queryForList("SELECT l.license_id, l.transfer_cap, COUNT(*) AS n FROM lic_transfer t JOIN lic_license l ON l.id = t.license_pk"
                + " WHERE t.accepted = TRUE AND t.at >= ? GROUP BY l.id, l.license_id, l.transfer_cap HAVING COUNT(*) >= l.transfer_cap LIMIT 50", Timestamp.from(now.minus(Duration.ofDays(365))))) {
            out.add(new Alert("TRANSFER_CAP", "warn", (String) r.get("license_id"), r.get("n") + " transferts en 12 mois (plafond " + r.get("transfer_cap") + ")"));
        }
        for (Map<String, Object> r : jdbc.queryForList("SELECT l.license_id, COUNT(*) AS n FROM lic_transfer t JOIN lic_license l ON l.id = t.license_pk WHERE t.accepted = TRUE AND t.at >= ?"
                + " GROUP BY l.id, l.license_id HAVING COUNT(*) >= 2 LIMIT 50", Timestamp.from(now.minus(Duration.ofDays(30))))) {
            out.add(new Alert("TRANSFER_REPEAT", "warn", (String) r.get("license_id"), r.get("n") + " transferts en 30 jours"));
        }
        return out;
    }

    @Scheduled(cron = "0 40 3 * * *", zone = "Africa/Douala")
    @Transactional
    public void purgeSightings() {
        jdbc.update("DELETE FROM lic_sighting WHERE seen_at < ?", Timestamp.from(Instant.now().minus(Duration.ofDays(90))));
    }

    public Map<String, Object> dashboard() {
        Instant now = Instant.now();
        Timestamp t = Timestamp.from(now);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("activeLicenses", jdbc.queryForObject("SELECT COUNT(*) FROM lic_license WHERE state = 'ACTIVE' AND (end_at IS NULL OR end_at >= ?)", Long.class, t));
        m.put("expiring30", jdbc.queryForObject("SELECT COUNT(*) FROM lic_license WHERE state = 'ACTIVE' AND end_at IS NOT NULL AND end_at >= ? AND end_at < ?", Long.class, t,
                Timestamp.from(now.plus(Duration.ofDays(30)))));
        m.put("suspended", jdbc.queryForObject("SELECT COUNT(*) FROM lic_license WHERE state = 'SUSPENDED'", Long.class));
        m.put("revoked", jdbc.queryForObject("SELECT COUNT(*) FROM lic_license WHERE state = 'REVOKED'", Long.class));
        m.put("seatsUsed", jdbc.queryForObject("SELECT COUNT(*) FROM lic_seat s JOIN lic_license l ON l.id = s.license_pk WHERE s.state = 'ACTIVE' AND l.state IN ('ACTIVE','SUSPENDED')", Long.class));
        m.put("seatsAllowed", jdbc.queryForObject("SELECT COALESCE(SUM(seats_allowed), 0) FROM lic_license WHERE state IN ('ACTIVE','SUSPENDED')", Long.class));
        // distinct trial seats in the registry (own issuances and those imported from the offline tools)
        m.put("trialsIssued", jdbc.queryForObject("SELECT COUNT(DISTINCT seat_id) FROM lic_event WHERE type = 'issue' AND kind = 'trial'", Long.class));
        m.put("openConflicts", jdbc.queryForObject("SELECT COUNT(*) FROM lic_conflict WHERE status = 'OPEN'", Long.class));
        List<Alert> alerts = alerts();
        m.put("alerts", alerts);
        m.put("suspectDuplicates", alerts.stream().filter(a -> a.type().equals("DUPLICATE_DEVICE") || a.type().equals("MULTI_SOURCE")).count());
        return m;
    }
}
