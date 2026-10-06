package castbridge.server;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The deferred-orders flows of {@link OrdersApiTest} against a real MySQL 8.4 with the production JDBC parameters. OrderService read DATETIME columns with a raw
 * {@code (Timestamp)} cast: fine on H2, a ClassCastException on MySQL with Connector/J 9.x (java.time.LocalDateTime).
 */
@Testcontainers(disabledWithoutDocker = true)
class OrdersMySqlTest extends OrdersApiTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withCommand("--innodb-buffer-pool-size=128M", "--performance-schema=OFF", "--character-set-server=utf8mb4", "--innodb-redo-log-capacity=16M")
            .withTmpFs(java.util.Map.of("/var/lib/mysql", "rw,size=512m"));

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> MYSQL.getJdbcUrl() + "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true");
        r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);
    }
    // The concurrent-release tests inherited from OrdersApiTest now run here too (OrderService.release: counter row created before the transaction, locked first, bounded retry).
}
