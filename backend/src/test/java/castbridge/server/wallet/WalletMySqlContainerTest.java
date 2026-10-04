package castbridge.server.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import castbridge.server.wallet.core.MemoryLedger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * L'oracle et le test de concurrence rejoués contre un vrai MySQL 8.4 (l'image et les réglages de production) : les verrous {@code FOR UPDATE} de H2 ne prouvent pas ceux de
 * MySQL. Sauté sans Docker : le rapport de la livraison dit s'il a tourné. Même outillage que {@code MySqlContainerTest}.
 */
@Testcontainers(disabledWithoutDocker = true)
class WalletMySqlContainerTest extends WalletTestBase {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withCommand("--innodb-buffer-pool-size=128M", "--performance-schema=OFF", "--character-set-server=utf8mb4", "--innodb-redo-log-capacity=16M")
            .withTmpFs(java.util.Map.of("/var/lib/mysql", "rw,size=512m"));

    @Autowired JdbcLedger ledger;
    @Autowired WalletRepository repo;

    @Test
    void oracleOnMySql() {
        LedgerSuites.Tally st = LedgerSuites.oracle(ledger, new MemoryLedger(), "M", 2_000);
        assertTrue(st.ops > 10_000);
        assertEquals(0, repo.derivedMismatches());
    }

    @Test
    void concurrencyOnMySql() throws Exception {
        LedgerSuites.concurrency(ledger, "N", 16, 500, repo::derivedMismatches, ledger::retries);
    }
}
