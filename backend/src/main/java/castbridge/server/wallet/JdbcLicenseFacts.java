package castbridge.server.wallet;

import castbridge.server.licenses.EnvelopeVerifier;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Lecture JDBC des tables V51/V52 du module des licences ({@code lic_seat} ⇒ {@code lic_license}, {@code lic_audit}, {@code lic_revocation}), sans modifier ni le module ni ses tables.
 * Module éteint ({@code castbridge.licenses.enabled=false}, le défaut) : aucune licence connue, donc « licence en attente d'enregistrement » côté portefeuille. Une licence de genre
 * {@code TRIAL} (essai délivré sous contrôle du propriétaire) n'est pas lue : l'essai se lit dans l'activation {@code cbx1}.
 */
@Component
@WalletModuleConfig.Enabled
public class JdbcLicenseFacts implements LicenseFacts {
    private static final Logger log = LoggerFactory.getLogger(JdbcLicenseFacts.class);
    private final JdbcTemplate jdbc;
    private final boolean licensesEnabled;
    private volatile Cached cached;

    public JdbcLicenseFacts(JdbcTemplate jdbc, @Value("${castbridge.licenses.enabled:false}") boolean licensesEnabled) {
        this.jdbc = jdbc;
        this.licensesEnabled = licensesEnabled;
    }

    private static Instant inst(java.sql.ResultSet rs, String col) throws java.sql.SQLException {
        Timestamp t = rs.getTimestamp(col);
        return t == null ? null : t.toInstant();
    }

    @Override
    public List<LicenseView> forDevice(String deviceCode) {
        if (!licensesEnabled) return List.of();
        try {
            Map<String, LicenseView> byId = new LinkedHashMap<>();
            jdbc.query("SELECT l.license_id, l.state, l.start_at, l.end_at, l.grace_days, l.updated_at, s.released_at, s.state AS seat_state FROM lic_seat s JOIN lic_license l ON l.id = s.license_pk "
                    + "WHERE s.device_code = ? AND s.subject = 'tv' AND l.kind = 'PAID' AND (s.state = 'ACTIVE' OR (s.state = 'RELEASED' AND l.state = 'REVOKED')) ORDER BY l.start_at, l.id", rs -> {
                String seatState = rs.getString("seat_state");
                LicenseView v = new LicenseView(rs.getString("license_id"), State.valueOf(rs.getString("state")), rs.getTimestamp("start_at").toInstant(), inst(rs, "end_at"), rs.getInt("grace_days"),
                        rs.getTimestamp("updated_at").toInstant(), "RELEASED".equals(seatState) ? inst(rs, "released_at") : null);
                LicenseView old = byId.get(v.licenseId());
                if (old == null || (old.seatReleasedAt() != null && v.seatReleasedAt() == null)) byId.put(v.licenseId(), v);   // un poste ACTIVE prime sur un poste libéré de la même licence
            }, deviceCode);
            return new ArrayList<>(byId.values());
        } catch (DataAccessException | IllegalArgumentException e) {
            log.warn("wallet : licences illisibles ({}) : traitées comme absentes", e.getClass().getSimpleName());
            return List.of();
        }
    }

    @Override
    public Notification notification(String licenseId) {
        if (!licensesEnabled) return null;
        try {
            Timestamp first = jdbc.queryForObject("SELECT MIN(r.registered_at) FROM lic_registration r JOIN lic_license l ON l.license_id = r.license_id WHERE r.license_id = ? AND l.created_by LIKE 'report:%' "
                    + "AND r.status IN ('REGISTERED', 'ATTACHED') AND r.registered_at IS NOT NULL", Timestamp.class, licenseId);
            if (first == null) return null;   // licence du propriétaire, du registre ou du serveur : aucune limite (comportement d'avant)
            Long declared = jdbc.queryForObject("SELECT COUNT(*) FROM lic_registration WHERE license_id = ? AND declared = TRUE", Long.class, licenseId);
            if (declared == null || declared == 0) {
                declared = jdbc.queryForObject("SELECT COUNT(*) FROM lic_issuance i JOIN lic_license l ON l.id = i.license_pk JOIN lic_registration r ON r.license_id = l.license_id AND r.nonce = i.nonce AND r.kid = i.kid "
                        + "WHERE l.license_id = ? AND i.source <> 'REPORT'", Long.class, licenseId);
            }
            return new Notification(first.toInstant(), declared != null && declared > 0);
        } catch (DataAccessException e) {
            log.warn("wallet : notification de licence illisible ({}) : aucune limite appliquée", e.getClass().getSimpleName());
            return null;
        }
    }

    @Override
    public Set<String> installKeys(String licenseId) {
        if (!licensesEnabled) return Set.of();
        try {
            return new HashSet<>(jdbc.queryForList("SELECT DISTINCT install_pub FROM lic_registration WHERE license_id = ? AND ik_signed = TRUE AND status IN ('REGISTERED', 'ATTACHED') AND install_pub IS NOT NULL",
                    String.class, licenseId));
        } catch (DataAccessException e) {
            log.warn("wallet : clés d'installation de licence illisibles ({}) : aucune restriction appliquée", e.getClass().getSimpleName());
            return Set.of();
        }
    }

    @Override
    public List<Window> windows(String licenseId) {
        if (!licensesEnabled) return null;
        try {
            Integer grace = jdbc.query("SELECT grace_days FROM lic_license WHERE license_id = ? AND created_by LIKE 'report:%'", rs -> rs.next() ? rs.getInt(1) : null, licenseId);
            if (grace == null) return null;
            Timestamp licenceEnd = jdbc.queryForObject("SELECT end_at FROM lic_license WHERE license_id = ?", Timestamp.class, licenseId);
            Long extended = jdbc.queryForObject("SELECT COUNT(*) FROM lic_audit WHERE action = 'LICENSE_EXTEND' AND target_type = 'LICENSE' AND target_id = ?", Long.class, licenseId);
            List<Window> raw = new ArrayList<>();
            jdbc.query("SELECT issued_at, usage_from, usage_to, unlimited FROM lic_registration WHERE license_id = ? AND status IN ('REGISTERED', 'ATTACHED') AND signer_creates = TRUE "
                    + "ORDER BY COALESCE(usage_from, issued_at)", rs -> {
                Timestamp from = rs.getTimestamp("usage_from") != null ? rs.getTimestamp("usage_from") : rs.getTimestamp("issued_at");
                Timestamp to = rs.getBoolean("unlimited") || rs.getTimestamp("usage_to") == null ? null : rs.getTimestamp("usage_to");
                raw.add(new Window(from.toInstant(), to == null ? null : to.toInstant()));
            }, licenseId);
            if (raw.isEmpty()) return null;
            List<Window> merged = new ArrayList<>();
            Window cur = null;
            for (Window w : raw) {
                if (cur == null) {
                    cur = w;
                    continue;
                }
                boolean bridged = cur.toExclusive() == null || !w.from().isAfter(cur.toExclusive().plusSeconds(grace * 86_400L));
                if (!bridged) {
                    merged.add(cur);
                    cur = w;
                    continue;
                }
                Instant end = cur.toExclusive() == null || w.toExclusive() == null ? null : (w.toExclusive().isAfter(cur.toExclusive()) ? w.toExclusive() : cur.toExclusive());
                cur = new Window(cur.from(), end);
            }
            // la prolongation du PROPRIÉTAIRE (LICENSE_EXTEND) prime sur les clés signées : la dernière fenêtre suit les dates PROPRES de la licence (second audit w23-05, MEDIUM-A) ; sans elle, le service
            // payé s'arrêtait à la fin du dernier jeton alors que la licence restait ACTIVE jusqu'à la date décidée
            if (extended != null && extended > 0) {
                Instant end = licenceEnd == null ? null : licenceEnd.toInstant();
                if (cur.toExclusive() != null && (end == null || end.isAfter(cur.toExclusive()))) cur = new Window(cur.from(), end);
            }
            merged.add(cur);
            return merged;
        } catch (DataAccessException e) {
            log.warn("wallet : fenêtres de licence illisibles ({}) : aucune restriction appliquée", e.getClass().getSimpleName());
            return null;
        }
    }

    @Override
    public List<StateEvent> history(String licenseId) {
        try {
            return jdbc.query("SELECT action, at FROM lic_audit WHERE target_type = 'LICENSE' AND target_id = ? AND action IN ('LICENSE_SUSPEND', 'LICENSE_RESUME', 'LICENSE_REVOKE', 'LICENSE_EXPIRE', 'LICENSE_EXTEND') "
                    + "ORDER BY id", (rs, i) -> new StateEvent(rs.getString("action"), rs.getTimestamp("at").toInstant()), licenseId);
        } catch (DataAccessException e) {
            log.warn("wallet : journal d'audit des licences illisible ({}) : dates d'état figées à la première observation", e.getClass().getSimpleName());
            return List.of();
        }
    }

    private record Cached(long count, long maxId, EnvelopeVerifier.Revocations value) {}

    /** Relue seulement quand {@code lic_revocation} change (nombre de lignes et plus grand identifiant) : une requête légère par lecture. */
    @Override
    public EnvelopeVerifier.Revocations revocations() {
        try {
            Map<String, Object> head = jdbc.queryForMap("SELECT COUNT(*) AS n, COALESCE(MAX(id), 0) AS m FROM lic_revocation");
            long n = ((Number) head.get("n")).longValue(), m = ((Number) head.get("m")).longValue();
            Cached c = cached;
            if (c != null && c.count() == n && c.maxId() == m) return c.value();
            Set<String> keys = new HashSet<>();
            Map<String, Long> seats = new HashMap<>();
            jdbc.query("SELECT license_id, seat_id, kid, revoked_at FROM lic_revocation ORDER BY id LIMIT 50000", rs -> {
                if (rs.getString("kid") != null) keys.add(rs.getString("kid"));
                else seats.merge(rs.getString("license_id") + "|" + rs.getString("seat_id"), rs.getTimestamp("revoked_at").getTime(), Math::max);
            });
            EnvelopeVerifier.Revocations r = new EnvelopeVerifier.Revocations(Set.copyOf(keys), Map.copyOf(seats));
            cached = new Cached(n, m, r);
            return r;
        } catch (DataAccessException e) {
            log.warn("wallet : révocations des licences illisibles ({}) : dernière liste connue conservée", e.getClass().getSimpleName());
            Cached c = cached;
            return c == null ? EnvelopeVerifier.Revocations.none() : c.value();
        }
    }
}
