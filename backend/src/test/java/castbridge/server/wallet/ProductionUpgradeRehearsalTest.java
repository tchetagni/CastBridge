package castbridge.server.wallet;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import castbridge.server.CastbridgeApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * REPETITION DE LA MISE A JOUR DE PRODUCTION (serveur 1.2.0 : migration V62, module portefeuille) sur un VRAI MySQL 8.4, avec les reglages du conteneur {@code castbridge-db}
 * (docker-compose.yml : memes options, meme classement serveur). Sauté sans Docker. Ce que la production fera, dans l'ordre : base au niveau V61 avec des lignes réelles, démarrage
 * du nouveau code (Flyway applique V62), module éteint (défaut) puis allumé ; au besoin retour arrière {@code tools/wallet/rollback-V62.sql}, lancé avec le client {@code mysql} comme le fera
 * l'opérateur. Les clés sont des clés de TEST engendrées ici. Les mesures (durée) sont écrites sur la sortie standard.
 */
@Testcontainers(disabledWithoutDocker = true)
class ProductionUpgradeRehearsalTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withCommand("--innodb-buffer-pool-size=128M", "--innodb-log-buffer-size=8M", "--performance-schema=OFF", "--max-connections=40", "--table-open-cache=400",
                    "--tmp-table-size=16M", "--max-heap-table-size=16M", "--skip-name-resolve", "--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_ai_ci",
                    "--innodb-redo-log-capacity=16M")
            .withTmpFs(Map.of("/var/lib/mysql", "rw,size=768m"));

    static final ObjectMapper JSON = new ObjectMapper();
    static final String TV_TOKEN = "rehearsal-tv-token-0123456789-abcdefghijklmnopqrstuvwxyz";
    static final List<String> WALLET_TABLES = WalletMigrationTest.TABLES;
    static final String URL_OPTIONS = "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true";

    // ------------------------------------------------------------------ outils

    /** Une base neuve (le propriétaire de la base de production a tous les droits sur elle seule). */
    static String newDb(String name) throws Exception {
        try (var c = java.sql.DriverManager.getConnection("jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306) + "/" + URL_OPTIONS, "root", MYSQL.getPassword());
             var s = c.createStatement()) {
            s.execute("DROP DATABASE IF EXISTS " + name);
            s.execute("CREATE DATABASE " + name + " CHARACTER SET utf8mb4");
            s.execute("GRANT ALL ON " + name + ".* TO '" + MYSQL.getUsername() + "'@'%'");
        }
        return name;
    }

    static String url(String db) { return "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306) + "/" + db + URL_OPTIONS; }

    static DataSource ds(String db) { return new DriverManagerDataSource(url(db), MYSQL.getUsername(), MYSQL.getPassword()); }

    static Flyway flyway(DataSource ds, String target) {
        var cfg = Flyway.configure().dataSource(ds).locations("classpath:db/migration");
        if (target != null) cfg.target(target);
        return cfg.load();
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static long count(JdbcTemplate j, String table) { return j.queryForObject("SELECT COUNT(*) FROM " + table, Long.class); }

    /** Les tables d'avant V62 (tout sauf le portefeuille et l'historique de Flyway). */
    static List<String> olderTables(JdbcTemplate j) {
        return j.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE' AND table_name NOT LIKE 'wallet\\_%' "
                + "AND table_name <> 'flyway_schema_history' ORDER BY table_name", String.class);
    }

    /** Empreinte de CHAQUE table ancienne : contenu (CHECKSUM TABLE, ligne par ligne) ET définition (SHOW CREATE TABLE). */
    static Map<String, String> fingerprint(JdbcTemplate j) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String t : olderTables(j)) {
            Long sum = j.query("CHECKSUM TABLE `" + t + "`", rs -> rs.next() ? rs.getLong(2) : null);
            String ddl = j.query("SHOW CREATE TABLE `" + t + "`", rs -> rs.next() ? rs.getString(2) : "");
            out.put(t, sum + "|" + count(j, "`" + t + "`") + "|" + sha256(ddl));
        }
        return out;
    }

    /** Lignes réalistes dans les tables existantes au niveau V61 (appareils, compte administrateur, licence payée avec poste actif). */
    static void seedOlderRows(JdbcTemplate j) {
        Timestamp now = Timestamp.from(Instant.parse("2026-09-30T10:00:00Z"));
        j.update("INSERT INTO device (public_id, app, android_id_hash, install_id, token_hash, label, device_name, manufacturer, model, platform, sdk, abi, version_code, version_name, country, first_seen, last_seen) "
                + "VALUES (?, 'tv', ?, ?, ?, 'TV du salon', 'GaiaOS TV', 'Gaia', 'G32', 'android-tv', 28, 'armeabi-v7a', 87, '0.14.30-beta', 'CM', ?, ?)",
                UUID.randomUUID().toString(), sha256("android-1"), UUID.randomUUID().toString(), sha256(TV_TOKEN), now, now);
        j.update("INSERT INTO device (public_id, app, android_id_hash, install_id, token_hash, label, platform, sdk, version_code, version_name, first_seen, last_seen) "
                + "VALUES (?, 'phone', ?, ?, ?, 'Téléphone du propriétaire', 'phone', 34, 41, '1.2.41-beta', ?, ?)",
                UUID.randomUUID().toString(), sha256("android-2"), UUID.randomUUID().toString(), sha256("phone-token"), now, now);
        j.update("INSERT INTO admin_user (username, password_hash, failed_attempts, created_at, role, totp_enabled) VALUES ('proprietaire-essai', 'x', 0, ?, 'OWNER', FALSE)", now);
        j.update("INSERT INTO lic_client (name, contact, created_at, updated_at) VALUES ('Client de répétition', 'essai@example.org', ?, ?)", now, now);
        j.update("INSERT INTO lic_product (product_id, title, kind, created_at) VALUES ('base-tv', 'Base TV', 'A_LA_CARTE', ?)", now);
        j.update("INSERT INTO lic_license (license_id, client_id, kind, state, seats_allowed, start_at, end_at, grace_days, transfer_cap, created_by, created_at, updated_at) "
                + "VALUES ('rehearsal-lic-001', (SELECT id FROM lic_client LIMIT 1), 'PAID', 'ACTIVE', 1, ?, NULL, 14, 0, 'proprietaire-essai', ?, ?)", now, now, now);
        j.update("INSERT INTO lic_license_product (license_pk, product_pk, added_at) VALUES ((SELECT id FROM lic_license LIMIT 1), (SELECT id FROM lic_product LIMIT 1), ?)", now);
        j.update("INSERT INTO lic_seat (license_pk, seat_id, subject, device_code, factors, k, slot_no, state, first_seen, last_seen) "
                + "VALUES ((SELECT id FROM lic_license LIMIT 1), '0123456789abcdef', 'tv', 'AAAA-BBBB-CCCC-DDDD', 'MAC|00112233445566778899aabbccddeeff', 1, 1, 'ACTIVE', ?, ?)", now, now);
    }

    static long walletTables(JdbcTemplate j) {
        String in = String.join(",", WALLET_TABLES.stream().map(t -> "'" + t + "'").toList());
        return j.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name IN (" + in + ")", Long.class);
    }

    /** Un grand livre minimal dans TOUTES les tables du portefeuille liées par clé étrangère, pour que le retour arrière soit essayé avec des écritures présentes. */
    static void seedWallet(JdbcTemplate j) {
        Timestamp now = Timestamp.from(Instant.parse("2026-10-05T08:00:00Z"));
        j.update("INSERT INTO wallet_account (holder, cur, pocket) VALUES ('AAAA-BBBB-CCCC-DDDD', 'NDEM', 'DISPO'), ('SYS:GRANT', 'NDEM', 'SYS')");
        j.update("INSERT INTO wallet_balance (account_id, balance, version, floor_zero) SELECT id, IF(holder LIKE 'SYS:%', -100, 100), 1, holder NOT LIKE 'SYS:%' FROM wallet_account");
        j.update("INSERT INTO wallet_txn (kind, idem_key, content_sha, holder, created_at, actor) VALUES ('GRANT', 'grant:rehearsal:1', ?, 'AAAA-BBBB-CCCC-DDDD', ?, 'system')", sha256("x"), now);
        j.update("INSERT INTO wallet_entry (txn_id, account_id, amount) SELECT (SELECT id FROM wallet_txn LIMIT 1), id, IF(holder LIKE 'SYS:%', -100, 100) FROM wallet_account");
        j.update("INSERT INTO wallet_identity (holder, api_device_id, created_at) VALUES ('AAAA-BBBB-CCCC-DDDD', 1, ?)", now);
        j.update("INSERT INTO wallet_escrow (eid, holder, cur, amount, created_at) VALUES ('e-1', 'AAAA-BBBB-CCCC-DDDD', 'NDEM', 10, ?)", now);
        j.update("INSERT INTO wallet_voucher_batch (batch_id, cur, cnt, issued_at, expires_at, manifest_sha, imported_at) VALUES ('b-1', 'NDEM', 1, ?, ?, ?, ?)", now, now, sha256("m"), now);
        j.update("INSERT INTO wallet_voucher (nonce, batch_id, cur, amount) VALUES ('AAAAAAAAAAAAAAAAAAAA', 'b-1', 'NDEM', 5)");
        j.update("INSERT INTO wallet_license_claim (license_id, holder, claimed_at) VALUES ('rehearsal-lic-001', 'AAAA-BBBB-CCCC-DDDD', ?)", now);
    }

    /** Exécute le fichier de retour arrière avec le client {@code mysql} du conteneur, exactement comme l'opérateur (aucune analyse du fichier par Java). */
    static void runRollbackScript(String db) throws Exception {
        Path script = Path.of("..", "tools", "wallet", "rollback-V62.sql").toAbsolutePath().normalize();
        assertTrue(Files.isRegularFile(script), "tools/wallet/rollback-V62.sql introuvable : " + script);
        MYSQL.copyFileToContainer(org.testcontainers.utility.MountableFile.forHostPath(script), "/tmp/rollback-V62.sql");
        var r = MYSQL.execInContainer("sh", "-c", "MYSQL_PWD='" + MYSQL.getPassword() + "' mysql -u" + MYSQL.getUsername() + " " + db + " < /tmp/rollback-V62.sql");
        assertEquals(0, r.getExitCode(), "le retour arrière doit s'exécuter sans erreur : " + r.getStderr());
    }

    // ------------------------------------------------------------------ (a) V61 -> V62 sur de vraies lignes

    @Test
    void migrationV61ToV62KeepsEveryOldRowAndAddsTheConstrainedTables() throws Exception {
        String db = newDb("rehearsal_migrate");
        DataSource ds = ds(db);
        JdbcTemplate j = new JdbcTemplate(ds);
        flyway(ds, "61").migrate();
        assertEquals(0, walletTables(j), "au niveau V61 il n'y a aucune table du portefeuille");
        assertEquals("61", j.queryForObject("SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success = 1", String.class));
        seedOlderRows(j);
        Map<String, String> before = fingerprint(j);
        assertEquals(49, before.size(), "le schéma ancien est bien présent : " + before.keySet());
        assertTrue(count(j, "device") == 2 && count(j, "lic_seat") == 1);

        long t0 = System.nanoTime();
        flyway(ds, "62").migrate();
        long ms = (System.nanoTime() - t0) / 1_000_000;
        System.out.println("REHEARSAL migration V61 -> V62 (base réaliste) : " + ms + " ms");

        assertEquals(before, fingerprint(j), "AUCUNE table ancienne ne change (contenu et définition identiques)");
        assertEquals(WALLET_TABLES.size(), walletTables(j));
        assertEquals("62", j.queryForObject("SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success = 1", String.class));
        assertTrue(count(j, "wallet_policy") == 39, "politique d'exploitation semée");
        assertEquals(0, count(j, "wallet_txn"), "aucune écriture : le portefeuille démarre vide");

        // contraintes réellement présentes dans MySQL (pas seulement dans le fichier)
        List<String> checks = j.queryForList("SELECT constraint_name FROM information_schema.check_constraints WHERE constraint_schema = DATABASE() AND constraint_name LIKE '%wallet%'", String.class);
        for (String c : List.of("ck_wallet_account_cur", "ck_wallet_account_pocket", "ck_wallet_balance_floor", "ck_wallet_txn_kind", "ck_wallet_entry_amount", "ck_wallet_escrow_state",
                "ck_wallet_escrow_cur", "ck_wallet_escrow_amount", "ck_wallet_admin_grant_cur", "ck_wallet_admin_grant_amount", "ck_wallet_admin_grant_state", "ck_wallet_policy_bounds")) {
            assertTrue(checks.contains(c), "contrainte CHECK absente : " + c + " (présentes : " + checks + ")");
        }
        List<String> uniques = j.queryForList("SELECT constraint_name FROM information_schema.table_constraints WHERE table_schema = DATABASE() AND constraint_type = 'UNIQUE' AND table_name LIKE 'wallet%'", String.class);
        assertTrue(uniques.containsAll(List.of("uq_wallet_account", "uq_wallet_txn_idem", "uq_wallet_admin_grant_idem")), "unicités : " + uniques);
        List<String> fks = j.queryForList("SELECT constraint_name FROM information_schema.referential_constraints WHERE constraint_schema = DATABASE() AND constraint_name LIKE '%wallet%'", String.class);
        assertTrue(fks.containsAll(List.of("fk_wallet_balance_account", "fk_wallet_entry_txn", "fk_wallet_entry_account", "fk_wallet_voucher_batch")), "clés étrangères : " + fks);
        // les clés textuelles sont comparées exactement (ascii_bin) : « Grant:1 » et « grant:1 » sont deux clés
        assertEquals("ascii_bin", j.queryForObject("SELECT collation_name FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'wallet_txn' AND column_name = 'idem_key'", String.class));

        // les contraintes REFUSENT vraiment
        Timestamp now = Timestamp.from(Instant.now());
        refuses(j, "ck_wallet_account_cur", "INSERT INTO wallet_account (holder, cur, pocket) VALUES ('AAAA-BBBB-CCCC-DDDD', 'EUR', 'DISPO')");
        j.update("INSERT INTO wallet_account (holder, cur, pocket) VALUES ('AAAA-BBBB-CCCC-DDDD', 'NDEM', 'DISPO')");
        refuses(j, "uq_wallet_account", "INSERT INTO wallet_account (holder, cur, pocket) VALUES ('AAAA-BBBB-CCCC-DDDD', 'NDEM', 'DISPO')");
        j.update("INSERT INTO wallet_account (holder, cur, pocket) VALUES ('aaaa-bbbb-cccc-dddd', 'NDEM', 'DISPO')");   // casse différente = autre titulaire (ascii_bin)
        refuses(j, "ck_wallet_balance_floor", "INSERT INTO wallet_balance (account_id, balance, version, floor_zero) VALUES ((SELECT MIN(id) FROM wallet_account), -1, 0, TRUE)");
        refuses(j, "fk_wallet_balance_account", "INSERT INTO wallet_balance (account_id, balance, version, floor_zero) VALUES (999999, 0, 0, FALSE)");
        j.update("INSERT INTO wallet_txn (kind, idem_key, content_sha, created_at, actor) VALUES ('GRANT', 'Grant:1', ?, ?, 'system')", sha256("a"), now);
        j.update("INSERT INTO wallet_txn (kind, idem_key, content_sha, created_at, actor) VALUES ('GRANT', 'grant:1', ?, ?, 'system')", sha256("a"), now);
        refuses(j, "uq_wallet_txn_idem", "INSERT INTO wallet_txn (kind, idem_key, content_sha, created_at, actor) VALUES ('GRANT', 'grant:1', '" + sha256("b") + "', NOW(6), 'system')");
        refuses(j, "ck_wallet_txn_kind", "INSERT INTO wallet_txn (kind, idem_key, content_sha, created_at, actor) VALUES ('MINT', 'k', '" + sha256("c") + "', NOW(6), 'system')");
        refuses(j, "ck_wallet_entry_amount", "INSERT INTO wallet_entry (txn_id, account_id, amount) VALUES ((SELECT MIN(id) FROM wallet_txn), (SELECT MIN(id) FROM wallet_account), 0)");
        refuses(j, "ck_wallet_policy_bounds", "UPDATE wallet_policy SET val = 0 WHERE name = 'convert.rate'");
        refuses(j, "ck_wallet_policy_bounds", "UPDATE wallet_policy SET val = 31 WHERE name = 'grant.periodDays'");
    }

    private static void refuses(JdbcTemplate j, String constraint, String sql) {
        DataAccessException e = assertThrows(DataAccessException.class, () -> j.update(sql), "doit être refusé par " + constraint);
        assertTrue(e.getMessage().contains(constraint) || (e.getMostSpecificCause().getMessage() != null && e.getMostSpecificCause().getMessage().contains(constraint)),
                "refus attendu par " + constraint + " mais : " + e.getMostSpecificCause().getMessage());
    }

    @Test
    void applicationStartsOnTheUpgradedDatabaseWithTheModuleDisabledThenEnabledAndTheOldEndpointsStillAnswer() throws Exception {
        String db = newDb("rehearsal_app");
        DataSource ds = ds(db);
        JdbcTemplate j = new JdbcTemplate(ds);
        flyway(ds, "61").migrate();
        seedOlderRows(j);
        long devicesBefore = count(j, "device");
        Path walletKey = WalletTestBase.writeKey(WalletTestBase.pair());   // clé de TEST

        // 1. module ÉTEINT (le défaut du code et de la production) : c'est ce démarrage qui applique V62
        try (ConfigurableApplicationContext ctx = start(db, "false", walletKey)) {
            int port = port(ctx);
            assertEquals(WALLET_TABLES.size(), walletTables(j), "le démarrage a appliqué V62");
            assertEquals(200, get(port, "/api/v1/quiz/questions?size=5", null).statusCode(), "ancien point d'accès : quiz");
            HttpResponse<String> me = get(port, "/api/v1/devices/me", "Bearer " + TV_TOKEN);
            assertEquals(200, me.statusCode(), "l'appareil enregistré AVANT la mise à jour s'authentifie toujours : " + me.body());
            assertEquals(201, post(port, "/api/v1/devices/register", null, "{\"installId\":\"" + UUID.randomUUID() + "\",\"app\":\"tv\",\"versionCode\":88,\"androidIdHash\":\"" + sha256("neuf") + "\"}").statusCode());
            assertEquals(404, get(port, "/api/v1/wallet/policy", "Bearer " + TV_TOKEN).statusCode(), "module éteint : route absente");
            assertEquals(404, get(port, "/api/v1/admin/wallet/reconcile", "Bearer test-admin-token-0123456789-abcdefghijklmnop").statusCode());
        }
        assertEquals(devicesBefore + 1, count(j, "device"));

        // 2. module ALLUMÉ sur la même base (clé de test) : routes présentes, ancien appareil servi, ancien code toujours là
        try (ConfigurableApplicationContext ctx = start(db, "true", walletKey)) {
            int port = port(ctx);
            HttpResponse<String> pol = get(port, "/api/v1/wallet/policy", "Bearer " + TV_TOKEN);
            assertEquals(200, pol.statusCode(), pol.body());
            JsonNode p = JSON.readTree(pol.body());
            assertEquals(1000, p.get("rate").asInt());
            assertTrue(p.get("switches").get("transfer").asBoolean());
            assertEquals(401, get(port, "/api/v1/wallet/policy", "Bearer inconnu").statusCode());
            HttpResponse<String> rec = get(port, "/api/v1/admin/wallet/reconcile", "Bearer test-admin-token-0123456789-abcdefghijklmnop");
            assertEquals(200, rec.statusCode(), rec.body());
            assertTrue(JSON.readTree(rec.body()).get("ok").asBoolean(), "grand livre vide : réconciliation saine");
            assertEquals(200, get(port, "/api/v1/quiz/questions?size=5", null).statusCode());
            assertEquals(200, get(port, "/api/v1/devices/me", "Bearer " + TV_TOKEN).statusCode());
            // sans preuve de liaison (défaut sûr require-bind-proof=true), une synchronisation sans activation n'ouvre rien : refus propre, jamais d'erreur 500
            int sync = post(port, "/api/v1/wallet/sync", "Bearer " + TV_TOKEN, "{\"deviceCode\":\"AAAA-BBBB-CCCC-DDDD\",\"activations\":[]}").statusCode();
            assertTrue(sync >= 400 && sync < 500, "sync sans activation : refus 4xx, reçu " + sync);
        }
        assertEquals(0, count(j, "wallet_txn"), "aucune écriture de portefeuille créée par ces essais");
    }

    private static ConfigurableApplicationContext start(String db, String walletEnabled, Path walletKey) throws Exception {
        Path storage = Files.createTempDirectory("cb-apk-rehearsal");
        List<String> args = new ArrayList<>(List.of("--server.port=0", "--spring.profiles.active=test", "--spring.datasource.url=" + url(db),
                "--spring.datasource.username=" + MYSQL.getUsername(), "--spring.datasource.password=" + MYSQL.getPassword(), "--castbridge.storage-dir=" + storage,
                "--castbridge.wallet.enabled=" + walletEnabled, "--management.server.port=-1"));
        if ("true".equals(walletEnabled)) args.add("--castbridge.wallet.key-file=" + walletKey);
        return org.springframework.boot.SpringApplication.run(CastbridgeApplication.class, args.toArray(String[]::new));
    }

    private static int port(ConfigurableApplicationContext ctx) { return ((ServletWebServerApplicationContext) ctx).getWebServer().getPort(); }

    private static HttpResponse<String> get(int port, String path, String auth) throws Exception {
        var b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET();
        if (auth != null) b.header("Authorization", auth);
        return HttpClient.newHttpClient().send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> post(int port, String path, String auth, String body) throws Exception {
        var b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        if (auth != null) b.header("Authorization", auth);
        return HttpClient.newHttpClient().send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    // ------------------------------------------------------------------ (b) retour arrière

    @Test
    void rollbackScriptRemovesV62EvenWithLedgerRowsLeavesOldDataIntactAndV62CanBeReapplied() throws Exception {
        String db = newDb("rehearsal_rollback");
        DataSource ds = ds(db);
        JdbcTemplate j = new JdbcTemplate(ds);
        flyway(ds, "61").migrate();
        seedOlderRows(j);
        flyway(ds, "62").migrate();
        seedWallet(j);
        assertTrue(count(j, "wallet_entry") > 0 && count(j, "wallet_voucher") > 0);
        Map<String, String> before = fingerprint(j);

        runRollbackScript(db);

        assertEquals(0, walletTables(j), "toutes les tables du portefeuille ont disparu");
        assertEquals(0L, j.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version = '62'", Long.class), "la ligne de V62 a quitté l'historique de Flyway (sinon V62 ne se rejouerait jamais)");
        assertEquals("61", j.queryForObject("SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success = 1", String.class));
        assertEquals(before, fingerprint(j), "les données et la définition des tables anciennes sont intactes");
        // le script est rejouable (DROP IF EXISTS) : un deuxième passage ne casse rien
        runRollbackScript(db);

        flyway(ds, "62").migrate();
        assertEquals(WALLET_TABLES.size(), walletTables(j), "V62 se rejoue proprement après le retour arrière");
        assertTrue(count(j, "wallet_policy") == 39);
        assertEquals(0, count(j, "wallet_txn"), "le grand livre repart vide : le retour arrière efface bien les écritures");
        assertEquals(before, fingerprint(j));
    }

    /** Contre-épreuve (la règle « la ligne de l'historique doit partir avec les tables ») : sans elle, V62 ne se rejoue pas et le module ne démarrerait plus. */
    @Test
    void droppingTheTablesWithoutRemovingTheHistoryRowWouldLeaveAnUnusableDatabase() throws Exception {
        String db = newDb("rehearsal_noclean");
        DataSource ds = ds(db);
        JdbcTemplate j = new JdbcTemplate(ds);
        flyway(ds, "62").migrate();
        String script = Files.readString(Path.of("..", "tools", "wallet", "rollback-V62.sql"));
        assertTrue(script.contains("DELETE FROM flyway_schema_history WHERE version = '62'"), "le script retire la ligne de V62");
        for (String t : WALLET_TABLES.reversed()) j.execute("DROP TABLE IF EXISTS " + t);   // l'erreur à ne pas faire : tables retirées, historique laissé
        flyway(ds, "62").migrate();
        assertEquals(0, walletTables(j), "Flyway croit V62 appliquée : il ne recrée rien");
        assertNotEquals(WALLET_TABLES.size(), walletTables(j));
        // et le module allumé échouerait au premier appel : la procédure de réparation du runbook est donc la seule sortie
        j.update("DELETE FROM flyway_schema_history WHERE version = '62'");
        flyway(ds, "62").migrate();
        assertEquals(WALLET_TABLES.size(), walletTables(j), "réparation documentée : supprimer la ligne puis migrer");
    }

    /** Retour à l'image précédente (deploy-server.sh --rollback) alors que V62 est déjà dans l'historique : l'ancien code (migrations jusqu'à V61) doit démarrer, Flyway ne doit pas refuser. */
    @Test
    void theOldCodeFlywayAcceptsADatabaseThatAlreadyHasV62() throws Exception {
        String db = newDb("rehearsal_oldcode");
        DataSource ds = ds(db);
        flyway(ds, "62").migrate();
        Path old = Files.createTempDirectory("migrations-1.1.0");
        try (Stream<Path> files = Files.list(Path.of("src", "main", "resources", "db", "migration"))) {
            for (Path f : files.filter(p -> p.getFileName().toString().endsWith(".sql")).toList()) {
                String v = f.getFileName().toString().substring(1, f.getFileName().toString().indexOf("__"));
                if (Integer.parseInt(v) <= 61) Files.copy(f, old.resolve(f.getFileName()));
            }
        }
        assertFalse(Files.exists(old.resolve("V62__wallet.sql")), "le jeu de l'ancien code s'arrête à V61");
        Flyway oldCode = Flyway.configure().dataSource(ds).locations("filesystem:" + old).load();   // réglages par défaut, comme Spring Boot avec spring.flyway.locations seul
        var result = assertDoesNotThrow(oldCode::migrate, "l'ancien code doit démarrer sur une base qui a V62 (migration future ignorée)");
        assertEquals(0, result.migrations.size());
        assertDoesNotThrow(oldCode::validate);
    }

    // ------------------------------------------------------------------ (c) durée avec 100 000 appareils

    @Test
    void migrationTimingWithOneHundredThousandDevices() throws Exception {
        String db = newDb("rehearsal_timing");
        DataSource ds = ds(db);
        flyway(ds, "61").migrate();
        try (var single = new SingleConnectionDataSource(url(db), MYSQL.getUsername(), MYSQL.getPassword(), true)) {
            JdbcTemplate j = new JdbcTemplate(single);
            j.execute("SET SESSION cte_max_recursion_depth = 200000");
            j.update("INSERT INTO device (public_id, app, android_id_hash, install_id, token_hash, label, platform, version_code, version_name, first_seen, last_seen) "
                    + "WITH RECURSIVE n(i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < 100000) "
                    + "SELECT UUID(), IF(i % 10 = 0, 'phone', 'tv'), SHA2(CONCAT('a', i), 256), UUID(), SHA2(CONCAT('t', i), 256), CONCAT('appareil ', i), 'android-tv', 87, '0.14.30-beta', NOW(6), NOW(6) FROM n");
            j.update("INSERT INTO device_install (device_id, install_id, first_seen, last_seen) SELECT id, install_id, first_seen, last_seen FROM device");
            assertEquals(100_000, count(j, "device"));
            seedOlderRows(j);
            Map<String, String> before = fingerprint(j);

            long t0 = System.nanoTime();
            flyway(ds, "62").migrate();
            long ms = (System.nanoTime() - t0) / 1_000_000;
            System.out.println("REHEARSAL migration V62 avec 100 000 appareils (+100 000 installations) : " + ms + " ms");

            assertTrue(ms < 30_000, "V62 ne copie ni ne modifie aucune ligne existante : durée bornée à 30 s, mesurée " + ms + " ms");
            assertEquals(before, fingerprint(j), "100 000 appareils : rien n'a bougé");
            assertEquals(WALLET_TABLES.size(), walletTables(j));
        }
    }

    // ------------------------------------------------------------------ configuration CIBLE de production : licences ET portefeuille allumés, preuve de liaison exigée

    private static String bindJson(java.security.KeyPair install, String code, String publicId) {
        long at = System.currentTimeMillis();
        String sig = Base64.getEncoder().encodeToString(Acts.sign(install, "castbridge-wallet-bind-v1\n" + code + "\n" + publicId + "\n" + at));
        return "{\"key\":\"" + WalletTestBase.rawPublic(install) + "\",\"at\":" + at + ",\"sig\":\"" + sig + "\"}";
    }

    private static long balanceOf(JdbcTemplate j, String holder, String cur) {
        Long v = j.queryForObject("SELECT COALESCE(SUM(b.balance), 0) FROM wallet_balance b JOIN wallet_account a ON a.id = b.account_id WHERE a.holder = ? AND a.cur = ?", Long.class, holder, cur);
        return v == null ? 0 : v;
    }

    private static JsonNode syncBound(int port, String auth, String publicId, Acts.Tv tv, java.security.KeyPair install, String... activations) throws Exception {
        String acts = String.join(",", java.util.Arrays.stream(activations).map(a -> "\"" + a + "\"").toList());
        HttpResponse<String> r = post(port, "/api/v1/wallet/sync", auth,
                "{\"deviceCode\":\"" + tv.code() + "\",\"activations\":[" + acts + "],\"bind\":" + bindJson(install, tv.code(), publicId) + "}");
        assertEquals(200, r.statusCode(), r.body());
        return JSON.readTree(r.body());
    }

    private static String kidOf(java.security.KeyPair p) { return castbridge.server.licenses.LicenseKeyring.kidOf(WalletTestBase.rawPublicBytes(p)); }

    /**
     * La configuration que le coordinateur déploiera : module des licences ALLUMÉ avec routes publiques, clé de signature des licences dans un dossier de secrets (clé de TEST engendrée ici),
     * portefeuille ALLUMÉ avec sa clé, clé du serveur reconnue par le portefeuille (essai et production), preuve de liaison EXIGÉE (défaut), TOTP exigé (défaut), plafond de transfert de licence 0
     * (défaut). Aucun drapeau n'abaisse une protection. Sur une base montée de V61 avec des lignes réelles : ce que l'allumage écrit, la clé publique, la liste de révocation, puis le parcours
     * d'une TV d'essai (clé d'essai, sans licence) et d'une TV de production dont la licence n'est pas encore enregistrée puis l'est.
     */
    @Test
    void targetProductionConfigurationLicencesAndWalletOnWithBindProofRequired() throws Exception {
        String db = newDb("rehearsal_target");
        DataSource ds = ds(db);
        JdbcTemplate j = new JdbcTemplate(ds);
        flyway(ds, "61").migrate();
        seedOlderRows(j);

        Path secrets = Files.createTempDirectory("cb-secrets-rehearsal");
        java.security.KeyPair server = WalletTestBase.pair();                        // clé de TEST de la « licence du serveur »
        Files.copy(WalletTestBase.writeKey(server), secrets.resolve("license-signing.key"));
        Files.writeString(secrets.resolve("license-totp.key"), Base64.getEncoder().encodeToString(new java.security.SecureRandom().generateSeed(32)) + "\n");
        Files.writeString(secrets.resolve("license-audit.key"), Base64.getEncoder().encodeToString(new java.security.SecureRandom().generateSeed(32)) + "\n");
        Path walletKey = WalletTestBase.writeKey(WalletTestBase.pair());
        String trusted = "serveur:" + WalletTestBase.rawPublic(server) + ":ISSUE_TRIAL+ISSUE_PRODUCTION+REACTIVATE";   // jamais SUPER_UNLIMITED, TRANSFER ni COMMAND_OPEN_ALL

        flyway(ds, "62").migrate();   // V62 appliquée AVANT l'allumage pour mesurer ce que l'allumage seul écrit
        List<String> watched = List.of("lic_client", "lic_license", "lic_seat", "lic_issuance", "lic_revocation", "lic_audit", "lic_event", "admin_user", "device");
        Map<String, Long> licBefore = new LinkedHashMap<>();
        watched.forEach(t -> licBefore.put(t, count(j, t)));

        Path storage = Files.createTempDirectory("cb-apk-rehearsal");
        List<String> args = List.of("--server.port=0", "--spring.profiles.active=test", "--spring.datasource.url=" + url(db), "--spring.datasource.username=" + MYSQL.getUsername(),
                "--spring.datasource.password=" + MYSQL.getPassword(), "--castbridge.storage-dir=" + storage, "--management.server.port=-1",
                "--castbridge.licenses.enabled=true", "--castbridge.licenses.public-routes=true", "--castbridge.licenses.secrets-dir=" + secrets,
                "--castbridge.wallet.enabled=true", "--castbridge.wallet.key-file=" + walletKey, "--castbridge.wallet.trusted-keys=" + trusted);
        try (ConfigurableApplicationContext ctx = org.springframework.boot.SpringApplication.run(CastbridgeApplication.class, args.toArray(String[]::new))) {
            int port = port(ctx);
            var env = ctx.getEnvironment();
            // aucune protection abaissée : les défauts sûrs sont ceux qui s'appliquent
            assertEquals("true", env.getProperty("castbridge.licenses.require-totp"));
            assertEquals("true", env.getProperty("castbridge.wallet.require-bind-proof"));
            assertEquals("0", env.getProperty("castbridge.licenses.default-transfer-cap"));
            assertEquals("manual", env.getProperty("castbridge.licenses.trial-issuance"));

            // ce que l'allumage écrit : rien dans les tables de licences (les écritures viennent des actions du propriétaire)
            Map<String, Long> after = new LinkedHashMap<>();
            watched.forEach(t -> after.put(t, count(j, t)));
            System.out.println("REHEARSAL allumage des licences, lignes avant -> après : " + licBefore + " -> " + after);
            for (String t : List.of("lic_client", "lic_license", "lic_seat", "lic_issuance", "lic_revocation", "lic_event")) assertEquals(licBefore.get(t), after.get(t), t + " inchangée par l'allumage");

            String admin = "Bearer test-admin-token-0123456789-abcdefghijklmnop";
            JsonNode signing = JSON.readTree(get(port, "/api/v1/admin/licenses/signing", admin).body());
            assertTrue(signing.get("keyLoaded").asBoolean());
            assertEquals(WalletTestBase.rawPublic(server), signing.get("publicKey").asText(), "la clé publique lue sur le serveur est celle du fichier");
            assertEquals(kidOf(server), signing.get("kid").asText());
            String scopes = signing.get("scopes").toString();
            assertFalse(scopes.contains("SUPER_UNLIMITED") || scopes.contains("TRANSFER") || scopes.contains("COMMAND_OPEN_ALL"), "portée de la clé du serveur : " + scopes);

            HttpResponse<String> rev = get(port, "/api/v1/revocations", null);
            assertEquals(200, rev.statusCode(), "routes publiques allumées : la liste de révocation est servie");
            assertTrue(rev.body().startsWith("cbx1"), "liste signée : " + rev.body().substring(0, Math.min(20, rev.body().length())));

            // TV d'essai : clé d'essai, AUCUNE licence : elle reçoit son attribution d'essai (preuve de liaison exigée et fournie)
            long now = System.currentTimeMillis();
            Acts.Tv trialTv = Acts.Tv.random();
            JsonNode reg = JSON.readTree(post(port, "/api/v1/devices/register", null, "{\"installId\":\"" + UUID.randomUUID() + "\",\"app\":\"tv\",\"versionCode\":88,\"androidIdHash\":\"" + sha256("essai") + "\"}").body());
            String auth = "Bearer " + reg.get("deviceToken").asText(), pubId = reg.get("deviceId").asText();
            java.security.KeyPair install = WalletTestBase.pair();
            // sans preuve de liaison : refus (la TV 0.14.31, sans portefeuille, ne signe pas)
            int noProof = post(port, "/api/v1/wallet/sync", auth, "{\"deviceCode\":\"" + trialTv.code() + "\",\"activations\":[\"" + Acts.trialDays(server, trialTv, now, 30) + "\"]}").statusCode();
            assertTrue(noProof >= 400 && noProof < 500, "sans preuve de liaison : refus 4xx, reçu " + noProof);
            assertEquals(0, balanceOf(j, trialTv.code(), "NDEM"));
            syncBound(port, auth, pubId, trialTv, install, Acts.trialDays(server, trialTv, now, 30));
            assertEquals(100, balanceOf(j, trialTv.code(), "NDEM"), "attribution d'essai (politique grant.trial.ndem)");
            syncBound(port, auth, pubId, trialTv, install, Acts.trialDays(server, trialTv, now, 30));
            assertEquals(100, balanceOf(j, trialTv.code(), "NDEM"), "un second contact ne recrédite pas (idempotence)");

            // TV de production : clé de production, AUCUNE licence enregistrée à la main. Changé avec w23-05 (constat du 2026-10-04) : avant, « licence en attente » et rien de crédité tant que le
            // propriétaire n'avait pas saisi la licence ; maintenant la synchronisation avec la preuve de liaison ouvre elle-même la licence et le poste (clé du serveur, ce matériel) : illimitée,
            // 5 000 NDEM + 50 MBOKO dès le premier contact, rejeu sans double versement
            Acts.Tv prodTv = Acts.Tv.random();
            JsonNode reg2 = JSON.readTree(post(port, "/api/v1/devices/register", null, "{\"installId\":\"" + UUID.randomUUID() + "\",\"app\":\"tv\",\"versionCode\":88,\"androidIdHash\":\"" + sha256("prod") + "\"}").body());
            String auth2 = "Bearer " + reg2.get("deviceToken").asText(), pubId2 = reg2.get("deviceId").asText();
            java.security.KeyPair install2 = WalletTestBase.pair();
            String act = Acts.production(server, prodTv, now, List.of());
            JsonNode pending = syncBound(port, auth2, pubId2, prodTv, install2, act);
            assertFalse(pending.get("notices").toString().contains("LICENSE_PENDING"), pending.toString());
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM lic_license WHERE license_id = 'lic-test' AND created_by LIKE 'report:%'", Long.class), "licence ouverte par la notification");
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM lic_seat WHERE device_code = ? AND state = 'ACTIVE'", Long.class, prodTv.code()));
            assertEquals(5000, balanceOf(j, prodTv.code(), "NDEM"));
            assertEquals(50, balanceOf(j, prodTv.code(), "MBOKO"));
            JsonNode paid = syncBound(port, auth2, pubId2, prodTv, install2, act);
            assertFalse(paid.get("notices").toString().contains("LICENSE_PENDING"), paid.toString());
            assertEquals(5000, balanceOf(j, prodTv.code(), "NDEM"), "rejeu : aucun double versement");

            HttpResponse<String> rec = get(port, "/api/v1/admin/wallet/reconcile", admin);
            assertEquals(200, rec.statusCode());
            assertTrue(JSON.readTree(rec.body()).get("ok").asBoolean(), "grand livre cohérent après les attributions : " + rec.body());
            assertEquals(200, get(port, "/api/v1/quiz/questions?size=5", null).statusCode());
        }
    }
}
