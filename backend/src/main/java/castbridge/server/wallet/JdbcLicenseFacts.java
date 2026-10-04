package castbridge.server.wallet;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Lecture JDBC des tables V51 du module des licences ({@code lic_seat} ⇒ {@code lic_license}), sans modifier ni le module ni ses tables. Module éteint
 * ({@code castbridge.licenses.enabled=false}, le défaut) : aucune licence connue, donc « licence en attente d'enregistrement » côté portefeuille. Une licence de genre {@code TRIAL}
 * (essai délivré sous contrôle du propriétaire) n'est pas lue : l'essai se lit dans l'activation {@code cbx1}.
 */
@Component
@WalletModuleConfig.Enabled
public class JdbcLicenseFacts implements LicenseFacts {
    private static final Logger log = LoggerFactory.getLogger(JdbcLicenseFacts.class);
    private final JdbcTemplate jdbc;
    private final boolean licensesEnabled;

    public JdbcLicenseFacts(JdbcTemplate jdbc, @Value("${castbridge.licenses.enabled:false}") boolean licensesEnabled) {
        this.jdbc = jdbc;
        this.licensesEnabled = licensesEnabled;
    }

    @Override
    public List<LicenseView> forDevice(String deviceCode) {
        if (!licensesEnabled) return List.of();
        try {
            return jdbc.query("SELECT l.license_id, l.state, l.start_at, l.end_at, l.grace_days, l.updated_at FROM lic_seat s JOIN lic_license l ON l.id = s.license_pk "
                    + "WHERE s.device_code = ? AND s.state = 'ACTIVE' AND s.subject = 'tv' AND l.kind = 'PAID' ORDER BY l.start_at, l.id", (rs, i) -> new LicenseView(rs.getString("license_id"),
                    State.valueOf(rs.getString("state")), rs.getTimestamp("start_at").toInstant(), rs.getTimestamp("end_at") == null ? null : rs.getTimestamp("end_at").toInstant(),
                    rs.getInt("grace_days"), rs.getTimestamp("updated_at").toInstant()), deviceCode);
        } catch (DataAccessException | IllegalArgumentException e) {
            log.warn("wallet : licences illisibles ({}) : traitées comme absentes", e.getClass().getSimpleName());
            return List.of();
        }
    }
}
