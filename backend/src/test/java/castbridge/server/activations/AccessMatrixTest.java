package castbridge.server.activations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import castbridge.server.licenses.Actor;
import castbridge.server.licenses.Role;
import castbridge.server.web.ApiException;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * Access control, one assertion per function x role x second factor (design 6.2): READONLY can neither export nor decide; SUPPORT acknowledges but does not decide;
 * an OWNER without TOTP cannot do a sensitive thing while TOTP is required; the admin bearer token is an OWNER audited as "api-token".
 */
class AccessMatrixTest extends ActTestBase {
    @Autowired ActivationsAdminService admin;
    @Autowired JournalService journals;
    @Autowired Exporter exporter;
    @Autowired Archiver archiver;
    @Autowired AlertService alertService;
    @Autowired ReadAudit readAudit;

    static final Actor OWNER_STRONG = new Actor("o-strong", Role.OWNER, "web", true);
    static final Actor OWNER_WEAK = new Actor("o-weak", Role.OWNER, "web", false);
    static final Actor SUPPORT = new Actor("support", Role.SUPPORT, "web", true);
    static final Actor READONLY = new Actor("reader", Role.READONLY, "web", true);

    @BeforeEach
    void start() {
        resetModule();
        clock.set(Instant.parse("2026-10-10T08:00:00Z"));
    }

    @AfterEach
    void stop() { clock.reset(); }

    /** The functions of the module, each with the permission it needs and a way to call it with a valid request. */
    private Map<ActPermissions.Perm, Consumer<Actor>> functions() {
        Map<ActPermissions.Perm, Consumer<Actor>> m = new LinkedHashMap<>();
        m.put(ActPermissions.Perm.ACT_READ, a -> {
            admin.dashboard(a, null);
            admin.activations(a, Map.of(), null, 10);
            admin.tvs(a, Map.of(), null, 10);
            admin.alerts(a, Map.of(), null, 10);
            admin.tools(a);
            admin.integrity(a);
        });
        m.put(ActPermissions.Perm.ACT_ALERT_ACK, a -> admin.ackAlert(a, newAlert()));
        m.put(ActPermissions.Perm.ACT_ALERT_DECIDE, a -> admin.closeAlert(a, newAlert(), "classée pour le test"));
        m.put(ActPermissions.Perm.ACT_EXPORT, a -> {
            admin.checkpoints(a, null, null);
            admin.readAudit(a, null, 10);
            try {
                exporter.export(a, "activations", "csv", Map.of(), new ByteArrayOutputStream());
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
        });
        m.put(ActPermissions.Perm.ACT_JOURNAL_UPLOAD, a -> journals.upload(a, new JournalBuilder(DESK, SEQ.incrementAndGet(), "desk", clock.nowMs(), 1)
                .entry(clock.nowMs(), "deliver", "fp=" + "1".repeat(64), "way=qr", "tv=-").build(), "web"));
        m.put(ActPermissions.Perm.ACT_ARCHIVE, a -> archiver.archive(a, "essai", false));
        return m;
    }

    private long newAlert() {
        return alertService.raise(AlertService.Type.TOOL_STALE, null, null, kid(DESK), null, "detail-" + SEQ.incrementAndGet(), "ev-" + SEQ.incrementAndGet());
    }

    /** What the design table says (written independently of the code under test). */
    private static boolean allowed(ActPermissions.Perm p, Actor a) {
        boolean sensitive = p == ActPermissions.Perm.ACT_ALERT_DECIDE || p == ActPermissions.Perm.ACT_EXPORT || p == ActPermissions.Perm.ACT_ARCHIVE;
        return switch (a.role()) {
            case OWNER -> !sensitive || a.strong();
            case SUPPORT -> p == ActPermissions.Perm.ACT_READ || p == ActPermissions.Perm.ACT_ALERT_ACK || p == ActPermissions.Perm.ACT_JOURNAL_UPLOAD;
            case READONLY -> p == ActPermissions.Perm.ACT_READ;
        };
    }

    @Test
    void everyFunctionAgainstEveryRoleAndSecondFactor() {
        for (var e : functions().entrySet()) {
            for (Actor a : List.of(OWNER_STRONG, OWNER_WEAK, SUPPORT, READONLY)) {
                String what = e.getKey() + " / " + a.name();
                boolean expected = allowed(e.getKey(), a);
                try {
                    e.getValue().accept(a);
                    assertTrue(expected, what + " must be refused");
                } catch (ApiException ex) {
                    assertEquals(403, ex.status().value(), what);
                    assertTrue(!expected, what + " must be allowed (" + ex.getMessage() + ")");
                }
            }
        }
    }

    @Test
    void theAdminBearerTokenIsAnOwnerAuditedAsApiToken() throws Exception {
        mvc.perform(get("/api/v1/admin/activations/dashboard").header("Authorization", ADMIN)).andExpect(status().isOk());
        Map<String, Object> row = jdbc.queryForMap("select * from adm_read_audit order by id desc limit 1");
        assertEquals("api-token", row.get("actor"));
        assertEquals("OWNER", row.get("role"));
        assertEquals("api", row.get("channel"));
        assertTrue(readAudit.verify().ok());
        mvc.perform(get("/api/v1/admin/activations/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/activations/dashboard").header("Authorization", "Bearer faux-jeton-faux-jeton-faux-jeton-123")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/admin/activations/journal").contentType(MediaType.TEXT_PLAIN).content("x")).andExpect(status().isUnauthorized());
    }

    @Test
    void theTokenExportsAndDecidesBecauseItIsAStrongOwner() throws Exception {
        long id = newAlert();
        mvc.perform(post("/api/v1/admin/activations/alerts/" + id + "/ack").header("Authorization", ADMIN)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/activations/alerts/" + id + "/close").header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"vérifié\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/activations/export?what=activations&format=csv").header("Authorization", ADMIN)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/activations/archive").header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"essai\"}")).andExpect(status().isOk());
        if (jdbc.queryForObject("select count(*) from adm_read_audit", Integer.class) < 1) fail("the export must have been audited");
    }
}
