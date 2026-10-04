package castbridge.server.activations;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
            assertEquals(0, names.stream().filter(n -> n.startsWith("V62__") || n.startsWith("V63__") || n.startsWith("V64__")).count(), "V62-V64 belong to other work");
        }
    }
}
