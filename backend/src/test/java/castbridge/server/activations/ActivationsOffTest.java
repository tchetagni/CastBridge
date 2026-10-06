package castbridge.server.activations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import castbridge.server.ApiTestBase;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/** The module is OFF by default: every route answers 404, nothing is written, nothing is scheduled. */
class ActivationsOffTest extends ApiTestBase {
    @Autowired JdbcTemplate jdbc;

    @Test
    void everyRouteIs404WhenTheModuleIsOff() throws Exception {
        for (String p : new String[] {"dashboard", "activations", "activations/" + "a".repeat(64), "tvs", "tvs/ABCD-ABCD-ABCD-ABCD", "alerts", "tools", "changes", "export?what=activations&format=csv",
                "checkpoints", "integrity", "read-audit"}) {
            mvc.perform(get("/api/v1/admin/activations/" + p).header("Authorization", ADMIN)).andExpect(status().isNotFound());
        }
        mvc.perform(post("/api/v1/admin/activations/journal").header("Authorization", ADMIN).contentType(MediaType.TEXT_PLAIN).content("x")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/admin/activations/archive").header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/admin/activations/alerts/1/ack").header("Authorization", ADMIN)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/activations/report").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/activations/console/challenge?kid=" + "a".repeat(16))).andExpect(status().isNotFound());
        assertEquals(0, jdbc.queryForObject("select count(*) from adm_read_audit", Integer.class), "a 404 is not a read");
    }

    @Test
    void theMigrationCreatedTheTablesAndTheDefaultPolicy() {
        assertEquals(1, jdbc.queryForObject("select count(*) from act_event_head where id = 1", Integer.class));
        assertEquals(72, jdbc.queryForObject("select val from act_policy where name = 'undeclared_grace_hours'", Integer.class));
        assertEquals(7, jdbc.queryForObject("select val from act_policy where name = 'journal_gap_days'", Integer.class));
        assertEquals(14, jdbc.queryForObject("select val from act_policy where name = 'tool_stale_days'", Integer.class));
        assertEquals(30, jdbc.queryForObject("select val from act_policy where name = 'silent_production_days'", Integer.class));
        assertEquals(14, jdbc.queryForObject("select val from act_policy where name = 'silent_trial_days'", Integer.class));
        assertEquals(24, jdbc.queryForObject("select val from act_policy where name = 'report_next_hours'", Integer.class));
    }

    @Test
    void onlyV63IsAddedByThisModule() throws Exception {
        Path dir = Path.of("src", "main", "resources", "db", "migration");
        try (var files = Files.list(dir)) {
            var names = files.map(f -> f.getFileName().toString()).toList();
            assertEquals(1, names.stream().filter(n -> n.startsWith("V63__")).count());
            assertTrue(names.contains("V63__activation_tracking.sql"));
            // « plus haut + 1, jamais de trou » (db/migration/README.md): V63 follows V62 (wallet), no hole before it, nothing of this module above it
            assertTrue(names.stream().anyMatch(n -> n.startsWith("V62__")), "V62 (wallet) exists: V63 is highest + 1");
            // changed with w23-05: V64 (activation_registration, licences module: table lic_registration) is the next number, « plus haut + 1 » ; this module adds nothing above V63 itself
            assertEquals(1, names.stream().filter(n -> n.startsWith("V64__")).count());
            assertTrue(names.contains("V64__activation_registration.sql"));
            // changed with the w23-05 audit corrections: V65 (registration_hardening: licences and wallet tables, columns of lic_registration) is the next number; this module adds nothing above V63 itself
            assertEquals(1, names.stream().filter(n -> n.startsWith("V65__")).count());
            assertTrue(names.contains("V65__registration_hardening.sql"));
            // changed with the second w23-05 audit (HIGH-A): V66 (install_key_binding: wallet_identity.expected_install_fp) is the next number; nothing above it
            assertEquals(1, names.stream().filter(n -> n.startsWith("V66__")).count());
            assertTrue(names.contains("V66__install_key_binding.sql"));
            // changed with the closure of the transfer paths: V67 (transfer_cap_zero, licences module) is the next number; nothing above it
            assertEquals(1, names.stream().filter(n -> n.startsWith("V67__")).count());
            assertTrue(names.contains("V67__transfer_cap_zero.sql"));
            assertEquals(0, names.stream().filter(n -> n.matches("V6[8-9]__.*")).count(), "V68 and above are not this module's");
        }
    }
}
