package castbridge.server.licenses;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * Migrations on an empty database and on a copy of the V1–V30 schemas (with data), the rollback scripts, and the restore of a
 * dump (the audit chain must still verify after a restore: that is the proof the restored data is exactly what was saved).
 */
class LicenseMigrationTest extends LicenseTestBase {

    private static DriverManagerDataSource h2(String name) {
        return new DriverManagerDataSource("jdbc:h2:mem:" + name + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
    }

    private static Flyway flyway(DriverManagerDataSource ds, String target) {
        return Flyway.configure().dataSource(ds).locations("classpath:db/migration").target(target).load();
    }

    @Test
    void migrateFromTheExistingSchemaKeepsEveryRowAndIsReversible() throws Exception {
        var ds = h2("mig-" + UUID.randomUUID());
        var db = new JdbcTemplate(ds);
        flyway(ds, "30").migrate();
        assertThat(db.queryForObject("select count(*) from information_schema.tables where table_name = 'lic_license'", Integer.class)).isZero();
        // data written by the production versions (V1–V30)
        db.update("insert into admin_user (username, password_hash, failed_attempts, created_at) values ('esaie', 'x', 2, ?)", Timestamp.from(Instant.now()));
        Integer lots = db.queryForObject("select count(*) from lot", Integer.class);

        flyway(ds, "latest").migrate();
        assertThat(db.queryForObject("select role from admin_user where username = 'esaie'", String.class)).isEqualTo("OWNER");
        assertThat(db.queryForObject("select failed_attempts from admin_user where username = 'esaie'", Integer.class)).isEqualTo(2);
        assertThat(db.queryForObject("select totp_enabled from admin_user where username = 'esaie'", Boolean.class)).isFalse();
        assertThat(db.queryForObject("select count(*) from lot", Integer.class)).isEqualTo(lots);
        List<String> tables = db.queryForList("select table_name from information_schema.tables where table_name like 'lic\\_%'", String.class);
        assertThat(tables).contains("lic_client", "lic_product", "lic_license", "lic_seat", "lic_issuance", "lic_transfer", "lic_revocation", "lic_audit", "lic_audit_head",
                "lic_ledger_import", "lic_conflict", "lic_sighting", "lic_license_product", "lic_product_lot", "lic_product_bundle", "lic_seat_alias", "lic_event");
        // uniqueness, foreign keys, indexes exist
        assertThat(db.queryForObject("select count(*) from information_schema.table_constraints where table_name = 'lic_license' and constraint_type = 'UNIQUE'", Integer.class)).isGreaterThanOrEqualTo(1);
        assertThat(db.queryForObject("select count(*) from information_schema.table_constraints where constraint_type = 'FOREIGN KEY' and table_name like 'lic\\_%'", Integer.class)).isGreaterThanOrEqualTo(8);
        assertThat(db.queryForObject("select count(*) from information_schema.indexes where table_name like 'lic\\_%' and index_type_name <> 'PRIMARY KEY'", Integer.class)).isGreaterThanOrEqualTo(8);
        // running again is a no-op
        assertThat(flyway(ds, "latest").migrate().migrationsExecuted).isZero();

        // rollback script (the owner runs it by hand after a backup)
        String rollback = new String(new ClassPathResource("db/rollback/U50-U52__licenses_rollback.sql").getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        try (Connection c = ds.getConnection()) {
            ScriptUtils.executeSqlScript(c, new ByteArrayResource(rollback.getBytes(StandardCharsets.UTF_8)));
        }
        assertThat(db.queryForObject("select count(*) from information_schema.tables where table_name like 'lic\\_%'", Integer.class)).isZero();
        assertThatThrownBy(() -> db.queryForObject("select role from admin_user", String.class)).isNotNull();
        assertThat(db.queryForObject("select username from admin_user", String.class)).isEqualTo("esaie");
        assertThat(db.queryForObject("select count(*) from lot", Integer.class)).isEqualTo(lots);
        // after the documented history clean-up the migrations apply again cleanly
        db.update("delete from flyway_schema_history where version in ('50','51','52')");
        assertThat(flyway(ds, "latest").migrate().migrationsExecuted).isEqualTo(3);
    }

    @Test
    void checkConstraintsRejectInvalidRows() {
        var l = license(2);
        assertThatThrownBy(() -> jdbc.update("update lic_license set seats_allowed = 0 where id = ?", l.id())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update lic_license set state = 'WEIRD' where id = ?", l.id())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into lic_license (license_id, client_id, kind, state, seats_allowed, start_at, created_by, created_at, updated_at) values (?,?,?,?,?,now(),?,now(),now())",
                l.licenseId(), l.clientId(), "PAID", "ACTIVE", 1, "t")).as("licenseId unique").isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        assertThatThrownBy(() -> jdbc.update("insert into lic_license (license_id, client_id, kind, state, seats_allowed, start_at, created_by, created_at, updated_at) values (?,?,?,?,?,now(),?,now(),now())",
                "lic-fk0000-noclient", 987654, "PAID", "ACTIVE", 1, "t")).as("foreign key").isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into lic_revocation (reason, revoked_by, revoked_at) values ('x','y',now())")).as("a revocation targets something").isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        var a = issue(l.licenseId(), dev());
        assertThatThrownBy(() -> jdbc.update("insert into lic_seat (license_pk, seat_id, device_code, factors, slot_no, state, first_seen, last_seen) values (?,?,?,?,NULL,'RELEASED',now(),now())", l.id(), a.seatId(), "AAAA-AAAA-AAAA-AAAA", "x"))
                .as("one seat row per (licence, seat id)").isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }

    @Test
    void aDumpRestoredElsewhereHasTheSameCountsAndAVerifiableAuditChain() throws Exception {
        var l = license(3);
        issue(l.licenseId(), dev());
        issue(l.licenseId(), dev());
        licenses.suspend(OWNER, l.licenseId(), "avant la sauvegarde");
        licenses.resume(OWNER, l.licenseId(), "après la sauvegarde");
        long rows = audit.verify().rows();
        String head = audit.headHash();
        Path dump = Files.createTempFile("cb-dump", ".sql");
        jdbc.execute("SCRIPT TO '" + dump.toString().replace("\\", "/") + "'");

        var ds = h2("restore-" + UUID.randomUUID());
        try (Connection c = ds.getConnection()) {
            ScriptUtils.executeSqlScript(c, new org.springframework.core.io.FileSystemResource(dump));
        }
        var db = new JdbcTemplate(ds);
        var restoredAudit = new AuditLog(db, new LicenseProperties(true, false, null, Path.of("/nonexistent"), null, null, null, null, null, null, null, null, null, null));
        var v = restoredAudit.verify();
        assertThat(v.ok()).as(String.valueOf(v.problem())).isTrue();
        assertThat(v.rows()).isEqualTo(rows);
        assertThat(restoredAudit.headHash()).isEqualTo(head);
        assertThat(db.queryForObject("select count(*) from lic_seat where license_pk = ? and state = 'ACTIVE'", Integer.class, l.id())).isEqualTo(2);
        assertThat(db.queryForObject("select count(*) from lic_license", Integer.class)).isEqualTo(jdbc.queryForObject("select count(*) from lic_license", Integer.class));
        assertThat(db.queryForObject("select count(*) from lic_issuance", Integer.class)).isEqualTo(jdbc.queryForObject("select count(*) from lic_issuance", Integer.class));
        // a restore that lost audit lines is caught by the head row
        db.update("delete from lic_audit where id = (select max(id) from lic_audit)");
        assertThat(restoredAudit.verify().ok()).isFalse();
        Files.deleteIfExists(dump);
        assertThat(List.of(1)).isNotEmpty();
    }
}
