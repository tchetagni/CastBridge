package castbridge.server;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import castbridge.server.telemetry.Kpi;
import castbridge.server.telemetry.KpiService;
import castbridge.server.telemetry.TelemetryService;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

class TelemetryApiTest extends ApiTestBase {
    @Autowired JdbcTemplate jdbc;
    @Autowired KpiService kpi;
    @Autowired TelemetryService telemetry;

    private record Dev(String id, String token) {}

    private Dev register(String consent, String model) throws Exception {
        JsonNode r = body(mvc.perform(post("/api/v1/devices/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"installId\":\"" + UUID.randomUUID() + "\",\"app\":\"tv\",\"versionCode\":8,\"versionName\":\"0.6\","
                        + "\"platform\":\"android-tv\",\"manufacturer\":\"Hisense\",\"model\":\"" + model + "\",\"consent\":\"" + consent + "\"}"))
                .andExpect(status().isCreated()).andReturn());
        return new Dev(r.get("deviceId").asText(), r.get("deviceToken").asText());
    }

    private static String ev(String name, String props) {
        return "{\"id\":\"" + UUID.randomUUID() + "\",\"ts\":" + Instant.now().toEpochMilli() + ",\"sessionId\":\"" + SESSION + "\",\"name\":\""
                + name + "\",\"props\":" + props + "}";
    }

    private static final String SESSION = UUID.randomUUID().toString();

    private static byte[] gzip(String s) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(bos)) {
            gz.write(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return bos.toByteArray();
    }

    private JsonNode send(Dev d, List<String> events, boolean compress) throws Exception {
        String batch = "{\"app\":\"tv\",\"versionCode\":8,\"events\":[" + String.join(",", events) + "]}";
        var req = post("/api/v1/events/batch").header("Authorization", "Bearer " + d.token()).contentType(MediaType.APPLICATION_JSON);
        if (compress) req = req.header("Content-Encoding", "gzip").content(gzip(batch));
        else req = req.content(batch);
        return body(mvc.perform(req).andExpect(status().isOk()).andReturn());
    }

    @Test
    void ingestionDedupWhitelistConsentAndKpis() throws Exception {
        Dev a = register("usage", "43A4K"), b = register("essential", "32A4"), c = register("usage", "55U7");
        List<String> events = new ArrayList<>(List.of(
                ev("session_start", "{}"),
                ev("feature_used", "{\"feature\":\"quiz\",\"source\":\"tile\"}"),
                ev("feature_used", "{\"feature\":\"quiz\",\"source\":\"remote\"}"),
                ev("feature_used", "{\"feature\":\"quiz\",\"source\":\"tile\",\"extra\":\"dropped\"}"),
                ev("feature_used", "{\"feature\":\"library\",\"source\":\"tile\"}"),
                ev("screen_view", "{\"screen\":\"quiz\"}"),
                ev("screen_time", "{\"screen\":\"quiz\",\"ms\":60000}"),
                ev("cast_end", "{\"channel\":\"wifi\",\"mode\":\"copy\",\"bytes\":10485760,\"ms\":10000,\"kbps\":8192,\"ok\":true}"),
                ev("cast_end", "{\"channel\":\"bluetooth\",\"mode\":\"copy\",\"bytes\":0,\"ms\":5000,\"ok\":false,\"error\":\"timeout\"}"),
                ev("playback_start", "{\"codec\":\"h264\",\"resolution\":\"1080p\",\"hw\":true,\"source\":\"internal\"}"),
                ev("playback_end", "{\"ms\":1800000,\"pct\":75.5,\"codec\":\"h264\",\"resolution\":\"1080p\",\"ok\":true}"),
                ev("quiz_game", "{\"mode\":\"millionaire\",\"track\":\"general\",\"players\":3,\"score\":12,\"ms\":600000}"),
                ev("quiz_answer", "{\"question\":\"cm-geo-001\",\"correct\":true,\"ms\":4200}"),
                ev("chess_game", "{\"mode\":\"ai\",\"ai_level\":3,\"result\":\"win\",\"moves\":42,\"ms\":900000}"),
                ev("download", "{\"type\":\"http\",\"bytes\":1000000,\"ms\":20000,\"ok\":true}"),
                ev("gateway_session", "{\"ms\":120000,\"bytes\":5000000}"),
                ev("connectivity_check", "{\"via\":\"bluetooth\",\"ok\":true,\"latency_ms\":320}"),
                ev("update_install", "{\"from\":7,\"to\":8,\"ok\":true}"),
                ev("error", "{\"screen\":\"library\",\"type\":\"io\",\"message\":\"cannot open /storage/emulated/0/Movies/Mon film.mkv via http://x.y/z\"}"),
                ev("session_end", "{\"ms\":1900000}")));
        JsonNode r = send(a, events, true);
        assertEquals(events.size(), r.get("accepted").asInt(), r.toString());
        assertEquals(0, r.get("rejected").asInt());

        // replay (lost answer): nothing is counted twice
        JsonNode again = send(a, events, false);
        assertEquals(0, again.get("accepted").asInt());
        assertEquals(events.size(), again.get("duplicates").asInt());

        // refused: forbidden key, unknown event, unknown feature, bad id; unknown keys were dropped; message scrubbed
        JsonNode bad = send(a, List.of(ev("feature_used", "{\"feature\":\"quiz\",\"filename\":\"film.mkv\"}"), ev("mystery", "{}"),
                ev("feature_used", "{\"feature\":\"teleport\"}"), "{\"id\":\"x\",\"ts\":1,\"name\":\"session_start\"}",
                ev("cast_end", "{\"channel\":\"carrier-pigeon\"}")), false);
        assertEquals(5, bad.get("rejected").asInt(), bad.toString());
        assertTrue(bad.get("errors").toString().contains("propriété interdite : filename"));
        long id = jdbc.queryForObject("select id from device where public_id = ?", Long.class, a.id());
        assertFalse(jdbc.queryForList("select props from telemetry_event where device_id = ?", String.class, id).stream().anyMatch(p -> p != null && p.contains("dropped")));
        String msg = jdbc.queryForObject("select props from telemetry_event where device_id = ? and name = 'error'", String.class, id);
        assertFalse(msg.contains("Mon film") || msg.contains("/storage") || msg.contains("http://"), msg);

        // essential consent only: usage events refused, errors kept
        JsonNode rb = send(b, List.of(ev("feature_used", "{\"feature\":\"quiz\"}"), ev("error", "{\"type\":\"io\",\"message\":\"x\"}")), false);
        assertEquals(1, rb.get("accepted").asInt());
        assertTrue(rb.get("errors").get(0).get("reason").asText().contains("non consenties"));

        send(c, List.of(ev("feature_used", "{\"feature\":\"library\",\"source\":\"menu\"}"),
                ev("cast_end", "{\"channel\":\"wifi\",\"mode\":\"move\",\"bytes\":100,\"ms\":10,\"ok\":true}")), false);

        // KPIs
        LocalDate today = LocalDate.now(CastbridgeApplication.ZONE);
        Kpi.Filter f = new Kpi.Filter(today.minusDays(29), today, "tv", null, null, null, null, null);
        KpiService.Features feats = kpi.features(f);
        assertEquals(3, feats.activeDevices());
        KpiService.FeatureRow quiz = feats.rows().get(0);
        assertEquals("quiz", quiz.feature());
        assertEquals(3, quiz.uses());
        assertEquals(1, quiz.devices());
        assertEquals(60000, quiz.timeMs());
        assertEquals("nouveau", quiz.trend());
        KpiService.FeatureRow library = feats.rows().get(1);
        assertEquals("library", library.feature());
        assertEquals(2, library.devices());
        assertEquals("67 %", library.share());
        assertEquals(0, feats.rows().stream().filter(x -> x.feature().equals("chess")).findFirst().orElseThrow().uses(), "unused features listed too");
        // filters: model
        assertEquals(1, kpi.features(new Kpi.Filter(today.minusDays(29), today, "tv", null, null, null, null, "55U7")).activeDevices());

        Kpi.Section cast = kpi.section("cast", f);
        assertEquals("3", cast.tiles().get(0).value());
        assertEquals("33 %", cast.tiles().get(2).value(), "1 failure out of 3");
        Kpi.Section fleet = kpi.section("parc", f);
        assertEquals("3", fleet.tiles().get(0).value(), "DAU");
        assertEquals("3", fleet.tiles().get(4).value(), "new devices");
        for (List<String> s : Kpi.SECTIONS) assertEquals(s.get(0), kpi.section(s.get(0), f).key());
        Kpi.Section quizKpi = kpi.section("quiz", f);
        assertEquals("1", quizKpi.tiles().get(0).value());

        // admin API and pages
        mvc.perform(get("/api/v1/admin/kpi/features").param("app", "tv").header("Authorization", ADMIN)).andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].feature").value("quiz"));
        mvc.perform(get("/api/v1/admin/kpi/cast").header("Authorization", ADMIN)).andExpect(jsonPath("$.tables[0].rows.length()").value(2));
        mvc.perform(get("/admin").with(user("esaie").roles("WEBADMIN"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Fonctionnalités les plus utilisées")))
                .andExpect(content().string(containsString("data-chart=")));
        for (List<String> s : Kpi.SECTIONS)
            mvc.perform(get("/admin/kpi").param("section", s.get(0)).with(user("esaie").roles("WEBADMIN"))).andExpect(status().isOk());
        mvc.perform(get("/admin/kpi/export").param("section", "fonctionnalites").with(user("esaie").roles("WEBADMIN"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Quiz;tv;3;1")));
        mvc.perform(get("/admin/devices/" + a.id()).with(user("esaie").roles("WEBADMIN"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Chronologie de l'usage")));

        // right of access, then right to erasure from the app
        mvc.perform(get("/api/v1/devices/me").header("Authorization", "Bearer " + a.token())).andExpect(status().isOk())
                .andExpect(jsonPath("$.usageConsent").value(true)).andExpect(jsonPath("$.events.length()").value(events.size()));
        mvc.perform(delete("/api/v1/devices/me").header("Authorization", "Bearer " + a.token())).andExpect(status().isNoContent());
        assertEquals(0, jdbc.queryForObject("select count(*) from telemetry_event where device_id = ?", Integer.class, id));
        assertEquals(0, jdbc.queryForObject("select count(*) from kpi_feature_day where device_id = ?", Integer.class, id));
        assertEquals(0, jdbc.queryForObject("select count(*) from device where id = ?", Integer.class, id));
        mvc.perform(post("/api/v1/events/batch").header("Authorization", "Bearer " + a.token()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"events\":[]}")).andExpect(status().isUnauthorized());
    }

    @Test
    void limitsAndRetention() throws Exception {
        Dev a = register("usage", "X1");
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 501; i++) many.add(ev("session_start", "{}"));
        mvc.perform(post("/api/v1/events/batch").header("Authorization", "Bearer " + a.token()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"events\":[" + String.join(",", many) + "]}")).andExpect(status().isPayloadTooLarge());
        mvc.perform(post("/api/v1/events/batch").header("Authorization", "Bearer " + a.token()).header("Content-Encoding", "gzip")
                .contentType(MediaType.APPLICATION_JSON).content("pas du gzip")).andExpect(status().isBadRequest());

        send(a, List.of(ev("feature_used", "{\"feature\":\"usb\"}"), ev("cast_end", "{\"channel\":\"wifi\",\"ok\":true}")), false);
        long id = jdbc.queryForObject("select id from device where public_id = ?", Long.class, a.id());
        // an event older than 13 months must go; the anonymous daily counters are rebuilt from what remains
        LocalDate old = LocalDate.now(CastbridgeApplication.ZONE).minusDays(400);
        jdbc.update("update telemetry_event set stat_day = ? where device_id = ? and name = 'feature_used'", old, id);
        telemetry.nightly();
        assertEquals(1, jdbc.queryForObject("select count(*) from telemetry_event where device_id = ?", Integer.class, id),
                () -> jdbc.queryForList("select name, stat_day, props from telemetry_event where device_id = ?", id).toString());
        LocalDate today = LocalDate.now(CastbridgeApplication.ZONE);
        assertEquals(jdbc.queryForObject("select count(*) from telemetry_event where name = 'cast_end' and stat_day = ?", Integer.class, today),
                jdbc.queryForObject("select sum(events) from kpi_event_day where name = 'cast_end' and stat_day = ?", Integer.class, today));
    }
}
