package castbridge.server.activations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import castbridge.server.licenses.Actor;
import castbridge.server.licenses.Role;
import castbridge.server.web.ApiException;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Audit w23-01 H2 and L2. The audit of a read is written BEFORE the data is returned and the read FAILS CLOSED (503, no data) when the line cannot be written; a streamed
 * export is audited before its first byte (an interrupted download is a line too); the services published for the web pages (w23-02) audit themselves; a refused read leaves a
 * trace; a compact key typed in a filter is never recopied.
 */
class ReadAuditFailClosedTest extends ActTestBase {
    @Autowired ActivationsAdminService admin;
    @Autowired Exporter exporter;
    @Autowired TvRef tvRef;

    static final String PREFIX = "/api/v1/admin/activations";
    static final Actor READER = new Actor("lecteur-test", Role.READONLY, "web", true);

    @BeforeEach
    void start() {
        resetModule();
        clock.set(Instant.parse("2026-10-10T08:00:00Z"));
    }

    @AfterEach
    void stop() {
        clock.reset();
        try {
            jdbc.execute("ALTER TABLE adm_read_audit_away RENAME TO adm_read_audit");
        } catch (RuntimeException e) { /* it was not renamed */ }
    }

    private int lines() { return jdbc.queryForObject("select count(*) from adm_read_audit", Integer.class); }

    @Test
    void aReadIsRefusedAndReturnsNoDataWhenItsAuditLineCannotBeWritten() throws Exception {
        var d = dev();
        jdbc.update("insert into act_tv (tv_ref, device_code, trial_resets, api_devices, android_ids, reco, alerts_open) values (?,?,0,0,0,'NEVER',0)", tvRef.of(d.code()), d.code());
        jdbc.execute("ALTER TABLE adm_read_audit RENAME TO adm_read_audit_away");
        for (String url : List.of("/tvs", "/tvs/" + d.code(), "/activations", "/dashboard", "/alerts", "/tools", "/integrity", "/checkpoints", "/read-audit", "/export?what=tvs&format=jsonl")) {
            MvcResult r = mvc.perform(adminGet(PREFIX + url)).andReturn();
            String out = r.getResponse().getContentAsString();
            assertEquals(503, r.getResponse().getStatus(), url + " -> " + out);
            assertFalse(out.contains(tvRef.of(d.code())) || out.contains("\"items\"") || out.contains("\"timeline\"") || out.contains("\"edition\"") || out.contains("\"events\""), url + " must not return data when it cannot be audited");
        }
    }

    @Test
    void anInterruptedExportIsAuditedBecauseTheLineIsWrittenBeforeTheFirstByte() throws Exception {
        for (int i = 0; i < 5; i++) {
            Dev d = dev();
            jdbc.update("insert into act_tv (tv_ref, device_code, trial_resets, api_devices, android_ids, reco, alerts_open) values (?,?,0,0,0,'NEVER',0)", tvRef.of(d.code()), d.code());
        }
        int before = lines();
        OutputStream cut = new OutputStream() {
            int n;
            @Override public void write(int b) throws IOException { if (++n > 50) throw new IOException("client gone"); }
            @Override public void write(byte[] b, int off, int len) throws IOException { throw new IOException("client gone"); }
        };
        assertThrows(IOException.class, () -> exporter.export(ActAccess.API_TOKEN, "tvs", "jsonl", new HashMap<>(), cut));
        assertEquals(before + 1, lines(), "the export left one line although it was cut");
        Map<String, Object> row = jdbc.queryForMap("select * from adm_read_audit order by id desc limit 1");
        assertEquals(true, row.get("export"));
        assertEquals(5, ((Number) row.get("rows_rendered")).intValue(), "the line carries the bound of the export");
    }

    @Test
    void theServicesPublishedForTheWebPagesAuditThemselves() {
        var d = dev();
        String ref = tvRef.of(d.code());
        jdbc.update("insert into act_tv (tv_ref, device_code, trial_resets, api_devices, android_ids, reco, alerts_open) values (?,?,0,0,0,'NEVER',0)", ref, d.code());
        int before = lines();
        admin.tvs(ActAccess.API_TOKEN, new HashMap<>(), null, 5);
        assertEquals(before + 1, lines(), "tvs");
        admin.tv(ActAccess.API_TOKEN, d.code());
        assertEquals(before + 2, lines(), "tv fiche");
        admin.activations(ActAccess.API_TOKEN, new HashMap<>(), null, 5);
        admin.alerts(ActAccess.API_TOKEN, new HashMap<>(), null, 5);
        admin.tools(ActAccess.API_TOKEN);
        admin.dashboard(ActAccess.API_TOKEN, null);
        admin.integrity(ActAccess.API_TOKEN);
        admin.checkpoints(ActAccess.API_TOKEN, null, null);
        admin.readAudit(ActAccess.API_TOKEN, null, 5);
        assertEquals(before + 9, lines(), "one line per call, with no HTTP request around");
        assertEquals(ref, jdbc.queryForObject("select target from adm_read_audit where route like '%/tvs/{deviceCode}'", String.class));
    }

    @Test
    void aRefusedReadLeavesATrace() {
        int before = lines();
        assertThrows(ApiException.class, () -> exporter.export(READER, "activations", "csv", new HashMap<>(), new java.io.ByteArrayOutputStream()));
        assertEquals(before + 1, lines(), "a refused export is a line");
        Map<String, Object> row = jdbc.queryForMap("select * from adm_read_audit order by id desc limit 1");
        assertEquals("lecteur-test", row.get("actor"));
        assertEquals(0, ((Number) row.get("rows_rendered")).intValue());
        assertTrue(((String) row.get("params")).contains("denied=403"), (String) row.get("params"));
    }

    @Test
    void aCompactKeyTypedInAFilterIsNeverRecopied() throws Exception {
        String compact = java.util.Base64.getEncoder().encodeToString(new byte[82]) + "AbC";
        for (String param : List.of("license", "sort", "whatever")) {
            mvc.perform(adminGet(PREFIX + "/activations?" + param + "=" + compact.replace("+", "%2B").replace("/", "%2F").replace("=", "%3D")));
            String p = jdbc.queryForObject("select params from adm_read_audit order by id desc limit 1", String.class);
            assertFalse(p.contains(compact.substring(0, 24)), param + " must not be recopied: " + p);
        }
        // a short, legitimate value stays readable
        mvc.perform(adminGet(PREFIX + "/activations?q=abc12345"));
        assertTrue(jdbc.queryForObject("select params from adm_read_audit order by id desc limit 1", String.class).contains("q=abc12345"));
    }
}
