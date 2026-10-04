package castbridge.server.licenses;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Second audit Opus w23-05, LOW-C : les scripts de retour arrière {@code U66}, {@code U65}, {@code U64} sont EXÉCUTÉS sur un vrai MySQL 8.4 (l'auditeur n'a trouvé qu'un test qui lisait leur texte).
 * Ils sont REJOUABLES (un second passage ne casse rien) et effacent eux-mêmes les lignes de {@code flyway_schema_history} : V64, V65 et V66 se réappliquent ensuite sans réparation manuelle.
 * Un échec de V64 à mi-chemin se répare par {@code U64} seul (ligne en échec comprise).
 */
@Testcontainers(disabledWithoutDocker = true)
class MigrationRollbackMySqlTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withCommand("--innodb-buffer-pool-size=128M", "--performance-schema=OFF", "--character-set-server=utf8mb4", "--innodb-redo-log-capacity=16M")
            .withTmpFs(java.util.Map.of("/var/lib/mysql", "rw,size=512m"));

    private static String url() { return MYSQL.getJdbcUrl() + "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true&allowMultiQueries=false"; }

    private static Flyway flyway() { return Flyway.configure().dataSource(url(), MYSQL.getUsername(), MYSQL.getPassword()).locations("classpath:db/migration").cleanDisabled(false).load(); }

    private static void script(Connection c, String name) throws Exception {
        ScriptUtils.executeSqlScript(c, new ClassPathResource("db/rollback/" + name));
    }

    private static long one(Connection c, String sql) throws Exception {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static long historyRows(Connection c) throws Exception { return one(c, "SELECT COUNT(*) FROM flyway_schema_history WHERE version IN ('64','65','66')"); }

    private static long columns(Connection c, String table, String column) throws Exception {
        return one(c, "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '" + table + "' AND COLUMN_NAME = '" + column + "'");
    }

    private static long tables(Connection c, String table) throws Exception {
        return one(c, "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '" + table + "'");
    }

    @Test
    void u66U65U64RunTwiceDeleteTheirFlywayRowsAndTheMigrationsApplyAgain() throws Exception {
        flyway().clean();
        assertTrue(flyway().migrate().migrationsExecuted > 0);
        try (Connection c = DriverManager.getConnection(url(), MYSQL.getUsername(), MYSQL.getPassword())) {
            assertEquals(3, historyRows(c), "V64, V65 et V66 appliquées");
            assertEquals(1, columns(c, "wallet_identity", "expected_install_fp"));
            for (int pass = 1; pass <= 2; pass++) {   // le second passage prouve que les scripts sont rejouables
                script(c, "U66__install_key_binding_rollback.sql");
                script(c, "U65__registration_hardening_rollback.sql");
                script(c, "U64__activation_registration_rollback.sql");
                assertEquals(0, historyRows(c), "passage " + pass + " : les trois lignes Flyway sont effacées par les scripts eux-mêmes");
                assertEquals(0, columns(c, "wallet_identity", "expected_install_fp"), "passage " + pass);
                assertEquals(0, columns(c, "lic_registration", "ik_signed"), "passage " + pass);
                assertEquals(0, tables(c, "lic_registration"), "passage " + pass);
                assertEquals(0, tables(c, "wallet_period_claim"), "passage " + pass);
                assertEquals(0, tables(c, "lic_key_gate"), "passage " + pass);
                assertEquals(1, one(c, "SELECT success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1"), "la dernière ligne est un succès (V63)");
            }
        }
        assertEquals(3, flyway().migrate().migrationsExecuted, "V64, V65 et V66 se réappliquent sans réparation manuelle");
        try (Connection c = DriverManager.getConnection(url(), MYSQL.getUsername(), MYSQL.getPassword())) {
            assertEquals(3, historyRows(c));
            assertEquals(1, columns(c, "wallet_identity", "expected_install_fp"));
            assertEquals(1, tables(c, "lic_registration"));
        }
    }

    @Test
    void aHalfAppliedV64IsRepairedByU64AloneRowsInFailureIncluded() throws Exception {
        flyway().clean();
        try (Connection c = DriverManager.getConnection(url(), MYSQL.getUsername(), MYSQL.getPassword())) {
            flyway().migrate();
            script(c, "U66__install_key_binding_rollback.sql");
            script(c, "U65__registration_hardening_rollback.sql");
            script(c, "U64__activation_registration_rollback.sql");
            // l'état d'un V64 interrompu : table partielle et ligne Flyway en échec
            try (Statement st = c.createStatement()) {
                st.execute("CREATE TABLE lic_registration (fp CHAR(64) NOT NULL PRIMARY KEY)");
                st.execute("INSERT INTO flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, execution_time, success) "
                        + "VALUES ((SELECT COALESCE(MAX(r), 0) + 1 FROM (SELECT installed_rank AS r FROM flyway_schema_history) t), '64', 'activation registration', 'SQL', 'V64__activation_registration.sql', 1, 'test', 1, 0)");
            }
            script(c, "U64__activation_registration_rollback.sql");
            assertEquals(0, one(c, "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '64'"), "la ligne en échec est effacée par U64 lui-même");
            assertEquals(0, tables(c, "lic_registration"));
        }
        assertEquals(3, flyway().migrate().migrationsExecuted);
    }
}
