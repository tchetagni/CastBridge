package castbridge.server.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** V63 s'applique sur une base vide ET sur une base au niveau V61 (V62 est réservée à la télémétrie W21) ; {@code rollback-V63.sql} la retire. */
class WalletMigrationTest {
    static final List<String> TABLES = List.of("wallet_account", "wallet_balance", "wallet_txn", "wallet_entry", "wallet_identity", "wallet_escrow", "wallet_result", "wallet_recv_code",
            "wallet_policy", "wallet_voucher_batch", "wallet_voucher");

    private static DriverManagerDataSource db() {
        return new DriverManagerDataSource("jdbc:h2:mem:mig-" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
    }

    private static long tables(JdbcTemplate j) {
        String in = String.join(",", TABLES.stream().map(t -> "'" + t + "'").toList());
        return j.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_name IN (" + in + ")", Long.class);
    }

    private static long olderColumns(JdbcTemplate j) {
        return j.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_name LIKE 'lic_%' OR table_name = 'device'", Long.class);
    }

    @Test
    void v63AppliesOnAnEmptyDatabase() {
        DriverManagerDataSource ds = db();
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        JdbcTemplate j = new JdbcTemplate(ds);
        assertEquals(TABLES.size(), tables(j));
        assertEquals(63, j.queryForObject("SELECT MAX(CAST(\"version\" AS INT)) FROM \"flyway_schema_history\" WHERE \"version\" IS NOT NULL", Integer.class));
        assertTrue(j.queryForObject("SELECT COUNT(*) FROM wallet_policy", Long.class) >= 20, "la politique est semée par la migration");
    }

    @Test
    void v63AppliesOnADatabaseAtLevelV61AndTheOlderTablesAreUntouched() {
        DriverManagerDataSource ds = db();
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("61").load().migrate();
        JdbcTemplate j = new JdbcTemplate(ds);
        assertEquals(0, tables(j));
        long before = olderColumns(j);
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        assertEquals(TABLES.size(), tables(j));
        assertEquals(before, olderColumns(j), "aucune table existante n'est modifiée");
    }

    @Test
    void rollbackScriptRemovesEverythingAndLeavesTheRestIntact() throws IOException {
        DriverManagerDataSource ds = db();
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        JdbcTemplate j = new JdbcTemplate(ds);
        String script = Files.readString(Path.of("..", "tools", "wallet", "rollback-V63.sql"));
        assertTrue(script.contains("seulement si aucune écriture réelle"), "en-tête d'avertissement");
        String sql = String.join("\n", script.lines().filter(l -> !l.isBlank() && !l.trim().startsWith("--")).toList());
        for (String stmt : sql.split(";")) if (!stmt.isBlank()) j.execute(stmt.trim());
        assertEquals(0, tables(j));
        assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'lic_license'", Long.class), "le reste du schéma est intact");
        // la ligne de V63 a quitté l'historique de Flyway : la migration peut être rejouée plus tard
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        assertEquals(TABLES.size(), tables(j));
    }
}
