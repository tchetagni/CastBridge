package castbridge.server.licenses;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Manual check of a REAL restore (docs/LICENSE-ADMIN.md, "Restaurer"): skipped unless -Dcb.restore.url=jdbc:mysql://… (with
 * -Dcb.restore.user and -Dcb.restore.password) points at the restored database. Verifies the audit chain and prints the counts.
 */
@EnabledIfSystemProperty(named = "cb.restore.url", matches = ".+")
class LicenseRestoreCheckTest {

    @Test
    void restoredDatabaseHasAVerifiableAuditChain() {
        var ds = new DriverManagerDataSource(System.getProperty("cb.restore.url"), System.getProperty("cb.restore.user", ""), System.getProperty("cb.restore.password", ""));
        var db = new JdbcTemplate(ds);
        var audit = new AuditLog(db, new LicenseProperties(true, false, null, Path.of("/nonexistent"), null, null, null, null, null, null, null, null, null));
        var v = audit.verify();
        System.out.println("RESTORE audit rows=" + v.rows() + " ok=" + v.ok() + " head=" + audit.headHash() + " licences=" + db.queryForObject("select count(*) from lic_license", Integer.class)
                + " postes=" + db.queryForObject("select count(*) from lic_seat", Integer.class) + " emissions=" + db.queryForObject("select count(*) from lic_issuance", Integer.class));
        assertThat(v.ok()).as(String.valueOf(v.problem())).isTrue();
        assertThat(v.rows()).isGreaterThan(0);
    }
}
