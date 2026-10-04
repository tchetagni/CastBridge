package castbridge.server.activations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** Every read of the administration is audited: one line per read, chained, no secret in it; the long poll writes one line per tracking session. */
class ReadAuditTest extends ActTestBase {
    @Autowired RequestMappingHandlerMapping mapping;
    @Autowired ReadAudit readAudit;
    @Autowired TvRef tvRef;
    @Autowired EventLog eventLog;

    static final String PREFIX = "/api/v1/admin/activations";

    @BeforeEach
    void start() {
        resetModule();
        clock.set(Instant.parse("2026-10-10T08:00:00Z"));
    }

    @AfterEach
    void stop() { clock.reset(); }

    private int lines() { return jdbc.queryForObject("select count(*) from adm_read_audit", Integer.class); }

    /** GET routes of the module, by reflection on the handler mapping. */
    private Set<String> getRoutes() {
        Set<String> routes = new TreeSet<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> e : mapping.getHandlerMethods().entrySet()) {
            if (e.getKey().getMethodsCondition().getMethods().stream().noneMatch(m -> m.asHttpMethod() == HttpMethod.GET)) continue;
            for (String p : e.getKey().getPathPatternsCondition().getPatternValues()) if (p.startsWith(PREFIX)) routes.add(p);
        }
        return routes;
    }

    @Test
    void everyReadRouteOfTheModuleIsAuditedExactlyOnce() throws Exception {
        var d = dev();
        String ref = tvRef.of(d.code());
        jdbc.update("insert into act_tv (tv_ref, device_code, trial_resets, api_devices, android_ids, reco, alerts_open) values (?,?,0,0,0,'NEVER',0)", ref, d.code());
        String fp = sha256("une activation");
        jdbc.update("insert into act_key (fp, tag, form, kind, subject, state, state_at, flags, super) values (?,?,?,?,?,?,?,?,false)", fp, fp.substring(0, 8), "ENVELOPE", "TRIAL", "tv", "EMISE",
                java.sql.Timestamp.from(clock.now()), "");
        Set<String> routes = getRoutes();
        Set<String> expected = new TreeSet<>(List.of("activations", "activations/{fp}", "tvs", "tvs/{deviceCode}", "dashboard", "alerts", "tools", "changes", "export", "checkpoints", "integrity",
                "read-audit").stream().map(s -> PREFIX + "/" + s).toList());
        assertEquals(expected, routes, "the read routes of the design (a removed or an unlisted route fails here)");

        List<String> audited = new ArrayList<>();
        for (String route : routes) {
            if (route.endsWith("/changes")) continue;   // the long poll is audited once per tracking session (below)
            String url = route.replace("{fp}", fp).replace("{deviceCode}", d.code());
            if (route.endsWith("/export")) url += "?what=activations&format=csv";
            int before = lines();
            MvcResult r = mvc.perform(adminGet(url)).andReturn();
            assertEquals(200, r.getResponse().getStatus(), url + " -> " + r.getResponse().getContentAsString());
            assertEquals(before + 1, lines(), url + " must write exactly one audit line");
            audited.add(route);
        }
        assertEquals(routes.size() - 1, audited.size());
        assertTrue(readAudit.verify().ok(), "the chain of the reads is intact");
    }

    @Test
    void theLongPollWritesOneLinePerTrackingSessionNotOnePerTurn() throws Exception {
        int before = lines();
        for (int i = 0; i < 3; i++) {
            MvcResult r = mvc.perform(adminGet(PREFIX + "/changes?after=0&wait=0")).andReturn();
            if (r.getRequest().isAsyncStarted()) r = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch(r)).andReturn();
            assertEquals(200, r.getResponse().getStatus());
        }
        assertEquals(before + 1, lines(), "three turns of the same session = one line");
        clock.set(Instant.ofEpochMilli(clock.nowMs()).plus(Duration.ofMinutes(5)));
        MvcResult r = mvc.perform(adminGet(PREFIX + "/changes?after=0&wait=0")).andReturn();
        if (r.getRequest().isAsyncStarted()) mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch(r)).andReturn();
        assertEquals(before + 2, lines(), "a new session after a pause");
    }

    @Test
    void theLineCarriesTheRouteTheFiltersAndTheTargetButNeverASecretOrAWholeCode() throws Exception {
        var d = dev();
        String ref = tvRef.of(d.code());
        jdbc.update("insert into act_tv (tv_ref, device_code, trial_resets, api_devices, android_ids, reco, alerts_open) values (?,?,0,0,0,'NEVER',0)", ref, d.code());
        mvc.perform(adminGet(PREFIX + "/activations?state=ACTIVATED&q=abc12345&limit=7")).andExpect(status().isOk());
        Map<String, Object> row = jdbc.queryForMap("select * from adm_read_audit order by id desc limit 1");
        assertEquals(PREFIX + "/activations", row.get("route"));
        String params = (String) row.get("params");
        assertTrue(params.contains("state=ACTIVATED") && params.contains("q=abc12345") && params.contains("limit=7"), params);
        assertFalse((Boolean) row.get("export"));

        mvc.perform(adminGet(PREFIX + "/activations?license=cbx1.AAAA.BBBB" + "x".repeat(400))).andExpect(status().isOk());
        String p2 = jdbc.queryForObject("select params from adm_read_audit order by id desc limit 1", String.class);
        assertFalse(p2.contains("cbx1."), "a token typed in a filter is never recorded");
        assertTrue(p2.length() <= 300);

        mvc.perform(adminGet(PREFIX + "/tvs/" + d.code().toLowerCase().replace("-", " "))).andExpect(status().isOk());
        Map<String, Object> tvRow = jdbc.queryForMap("select * from adm_read_audit order by id desc limit 1");
        assertEquals(ref, tvRow.get("target"), "the TV is designated by its reference");
        for (String col : List.of("params", "target", "route", "actor")) {
            String v = (String) tvRow.get(col);
            assertTrue(v == null || !v.contains(d.code()), col + " must not hold the whole device code");
        }

        mvc.perform(adminGet(PREFIX + "/export?what=activations&format=jsonl")).andExpect(status().isOk());
        assertTrue((Boolean) jdbc.queryForMap("select * from adm_read_audit order by id desc limit 1").get("export"));
    }

    @Test
    void anUnknownFicheIsStillALineSoThatProbingShows() throws Exception {
        int before = lines();
        mvc.perform(adminGet(PREFIX + "/activations/" + "f".repeat(64))).andExpect(status().isNotFound());
        assertEquals(before + 1, lines());
        assertEquals(0, jdbc.queryForObject("select rows_rendered from adm_read_audit order by id desc limit 1", Integer.class));
    }

    @Test
    void readsAreLimitedTo120AMinutePerActor() throws Exception {
        clock.set(Instant.parse("2026-10-10T09:00:00Z"));
        int refused = 0;
        for (int i = 0; i < 130; i++) {
            int s = mvc.perform(adminGet(PREFIX + "/dashboard")).andReturn().getResponse().getStatus();
            if (s == 429) refused++;
            else assertEquals(200, s);
        }
        assertEquals(10, refused);
        clock.set(Instant.parse("2026-10-10T09:01:30Z"));
        assertEquals(200, mvc.perform(adminGet(PREFIX + "/dashboard")).andReturn().getResponse().getStatus());
    }
}
