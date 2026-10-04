package castbridge.server.activations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import castbridge.server.licenses.WireActivation;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/** What a TV reports (POST /api/v1/activations/report, device token): verification, binding, change-only events, clones, no secret kept. */
@ExtendWith(OutputCaptureExtension.class)
class ReportTest extends ActTestBase {
    @Autowired TvRef tvRef;

    long now;

    @BeforeEach
    void start() {
        resetModule();
        clock.set(Instant.parse("2026-10-10T08:00:00Z"));
        now = clock.nowMs();
    }

    @AfterEach
    void stop() { clock.reset(); }

    private JsonNode ok(Install i, String json) throws Exception {
        MvcResult r = report(i, json);
        assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        return body(r);
    }

    @Test
    void aReportLinksTheDeviceAndRecordsTheActivationItCarries(CapturedOutput out) throws Exception {
        var i = install();
        var d = dev();
        String t = trialToken(DESK, d, now - 3_600_000L);
        JsonNode r = ok(i, reportJson(d, now, "TRIAL", List.of(t), null));
        assertEquals(24, r.get("next").asInt());
        assertEquals(1, r.get("accepted").asInt());
        assertEquals(0, r.get("ignored").asInt());

        String ref = tvRef.of(d.code());
        Map<String, Object> k = jdbc.queryForMap("select * from act_key where fp = ?", sha256(t));
        assertEquals("ACTIVATED", k.get("state"));
        assertEquals(ref, k.get("tv_ref"));
        assertEquals("TRIAL", k.get("kind"));
        assertEquals(kid(DESK), k.get("kid"));
        String flags = (String) k.get("flags");
        assertTrue(flags.contains(",seen_on_tv,") && flags.contains(",undeclared,"), "seen but declared by no tool: " + flags);
        assertEquals(now, ((java.sql.Timestamp) k.get("first_seen_tv_at")).getTime());

        Map<String, Object> tv = jdbc.queryForMap("select * from act_tv where tv_ref = ?", ref);
        assertEquals("TRIAL", tv.get("edition"));
        assertEquals("direct", tv.get("last_report_via"));
        assertEquals(1412, ((Number) tv.get("app_code")).intValue());
        assertEquals("0.14.12-beta", tv.get("app_name"));
        assertEquals(sha256(t), tv.get("current_fp"));
        assertEquals(1, jdbc.queryForObject("select count(*) from act_tv_device where tv_ref = ? and device_id = ?", Integer.class, ref, i.id()));
        for (String type : List.of("ACTIVATED", "DEVICE_LINKED", "APP_VERSION")) {
            assertEquals(1, jdbc.queryForObject("select count(*) from act_event where type = ? and tv_ref = ?", Integer.class, type, ref), type);
        }
        assertEquals(1, jdbc.queryForObject("select count(*) from act_report where tv_ref = ?", Integer.class, ref));

        // the token itself is nowhere: not in a table, not in the logs, nor the device token or the whole device code
        assertFalse(out.getAll().contains("cbx1."), "no token in the application log");
        assertFalse(out.getAll().contains(i.token()), "no device token in the application log");
        assertEquals(List.of(), tablesHolding("cbx1."), "no token in any table");
    }

    /** Names of the string columns (module tables) containing the text. */
    private List<String> tablesHolding(String needle) {
        var cols = jdbc.queryForList("select table_name, column_name from information_schema.columns where (table_name like 'act\\_%' or table_name like 'adm\\_read%')"
                + " and data_type in ('CHARACTER VARYING','CHARACTER','CHARACTER LARGE OBJECT','VARCHAR','CHAR')");
        List<String> bad = new java.util.ArrayList<>();
        for (var c : cols) {
            String t = c.get("table_name").toString().toLowerCase(), col = c.get("column_name").toString().toLowerCase();
            if (jdbc.queryForObject("select count(*) from " + t + " where " + col + " like ?", Integer.class, "%" + needle + "%") > 0) bad.add(t + "." + col);
        }
        return bad;
    }

    @Test
    void anIdenticalReportWritesNoEventAndTheTenMinuteRuleHolds() throws Exception {
        var i = install();
        var d = dev();
        String t = productionToken(PHONE, "lic-abc1234567", d, now - 3_600_000L);
        String json = reportJson(d, now, "PRODUCTION", List.of(t), null);
        ok(i, json);
        int ev = events();
        MvcResult tooSoon = report(i, json);
        assertEquals(429, tooSoon.getResponse().getStatus(), "one report per 10 minutes per device");
        clock.set(Instant.ofEpochMilli(now).plus(Duration.ofMinutes(11)));
        ok(i, json);
        assertEquals(ev, events(), "no change, no event");
        assertEquals(2, jdbc.queryForObject("select count(*) from act_report", Integer.class), "the raw report is kept for 90 days");
        assertEquals(clock.nowMs(), ((java.sql.Timestamp) jdbc.queryForObject("select last_report_at from act_tv", java.sql.Timestamp.class)).getTime());
    }

    @Test
    void anActivationOfAnotherDeviceIsIgnoredAndRaisesACloneAlert() throws Exception {
        var owner = dev();
        var other = dev();
        var i1 = install();
        var i2 = install();
        String t = trialToken(DESK, owner, now - 3_600_000L);
        ok(i1, reportJson(owner, now, "TRIAL", List.of(t), null));
        clock.set(Instant.ofEpochMilli(now).plus(Duration.ofMinutes(30)));
        JsonNode r = ok(i2, reportJson(other, clock.nowMs(), "TRIAL", List.of(t), null));
        assertEquals(0, r.get("accepted").asInt());
        assertEquals(1, r.get("ignored").asInt());
        assertEquals(1, alerts("CLONE"));
        Map<String, Object> a = jdbc.queryForMap("select * from act_alert where type = 'CLONE'");
        assertEquals(sha256(t), a.get("fp"));
        assertEquals(tvRef.of(other.code()), a.get("tv_ref"));
        assertEquals(tvRef.of(owner.code()), jdbc.queryForObject("select tv_ref from act_key where fp = ?", String.class, sha256(t)), "the activation stays attached to its own device");
        assertEquals("high", ((String) a.get("severity")).toLowerCase());
    }

    @Test
    void twoInstallationsWithDifferentHardwareIdsReportingTheSameCodeIsAClone() throws Exception {
        var d = dev();
        var i1 = install("a".repeat(64));
        var i2 = install("b".repeat(64));
        String t = trialToken(DESK, d, now - 3_600_000L);
        ok(i1, reportJson(d, now, "TRIAL", List.of(t), null));
        assertEquals(0, alerts("CLONE"));
        ok(i2, reportJson(d, now, "TRIAL", List.of(t), null));
        assertEquals(1, alerts("CLONE"));
        assertEquals(2, jdbc.queryForObject("select android_ids from act_tv", Integer.class));
    }

    @Test
    void anUnknownKeyAndABadSignatureAreIgnoredWithAnAlert() throws Exception {
        var i = install();
        var d = dev();
        String unknown = trialToken(STRANGER, d, now - 3_600_000L);
        String good = trialToken(DESK, d, now - 3_600_000L);
        String[] parts = good.split("\\.");
        byte[] sig = java.util.Base64.getDecoder().decode(parts[2]);
        sig[3] ^= 1;
        String forged = parts[0] + "." + parts[1] + "." + java.util.Base64.getEncoder().encodeToString(sig);
        JsonNode r = ok(i, reportJson(d, now, "TRIAL", List.of(unknown, forged), null));
        assertEquals(0, r.get("accepted").asInt());
        assertEquals(2, r.get("ignored").asInt());
        assertEquals(1, alerts("UNKNOWN_KEY"));
        assertEquals(1, alerts("BAD_TOKEN"));
        assertEquals(0, jdbc.queryForObject("select count(*) from act_key", Integer.class));
    }

    @Test
    void anActivationInstalledAfterItsWindowOrWithALongWindowIsFlagged() throws Exception {
        var i = install();
        var d = dev();
        long issued = now - 5 * 86_400_000L;
        String late = trialToken(DESK, d, issued);                           // window closed 3 days ago
        String fp8 = sha256(late).substring(0, 8);
        ok(i, reportJson(d, now, "TRIAL", List.of(late), "\"installedAt\":{\"" + fp8 + "\":" + (issued + 4 * 86_400_000L) + "}").replace("\"installedAt\":{},", ""));
        assertEquals(1, alerts("OUT_OF_WINDOW"));
        assertTrue(((String) jdbc.queryForObject("select flags from act_key", String.class)).contains(",out_of_window,"));

        var d2 = dev();
        var i2 = install();
        WireActivation.Fields f = new WireActivation.Fields("trial", "tv", kid(DESK), now, rnd32().substring(0, 16), now, now, now + 72 * 3_600_000L, "trial",
                WireActivation.defaultSeat("trial", d2.fp()), d2.k(), d2.fp(), List.of());
        String longWindow = WireActivation.token(f, sign(DESK, WireActivation.payload(f)));
        ok(i2, reportJson(d2, now, "TRIAL", List.of(longWindow), null));
        assertEquals(2, alerts("OUT_OF_WINDOW"));
    }

    @Test
    void commandsAndTrialResetsAreRecordedOnChangeOnly() throws Exception {
        var i = install();
        var d = dev();
        String t = trialToken(DESK, d, now - 3_600_000L);
        String json = "{\"v\":1,\"deviceCode\":\"" + d.code() + "\",\"app\":{\"code\":1412,\"name\":\"0.14.12-beta\"},\"activations\":[\"" + t + "\"],\"state\":{\"edition\":\"TRIAL\","
                + "\"usageTo\":null,\"super\":false,\"openAllUntil\":" + (now + 30 * 86_400_000L) + ",\"unlockUntil\":0,\"trialResets\":2,\"installedAt\":{},"
                + "\"commands\":[[\"open_all\",\"0a1b2c3d\"," + now + ",30]]},\"at\":" + now + "}";
        ok(i, json);
        String ref = tvRef.of(d.code());
        assertEquals(1, jdbc.queryForObject("select count(*) from act_event where type = 'COMMAND_OPEN_ALL' and tv_ref = ?", Integer.class, ref));
        assertEquals(2, jdbc.queryForObject("select count(*) from act_event where type = 'TRIAL_RESET' and tv_ref = ?", Integer.class, ref));
        assertEquals(2, jdbc.queryForObject("select trial_resets from act_tv where tv_ref = ?", Integer.class, ref));
        Map<String, Object> c = jdbc.queryForMap("select * from act_command where tv_ref = ?", ref);
        assertEquals(false, c.get("declared"));
        assertEquals(true, c.get("reported"));
        int ev = events();
        clock.set(Instant.ofEpochMilli(now).plus(Duration.ofHours(25)));
        ok(i, json);
        assertEquals(ev, events());
    }

    @Test
    void sizeCountAndAuthenticationLimits() throws Exception {
        var i = install();
        var d = dev();
        String t = trialToken(DESK, d, now - 3_600_000L);
        // 401: no device token, or an unknown one
        assertEquals(401, mvc.perform(post("/api/v1/activations/report").contentType(MediaType.APPLICATION_JSON).content(reportJson(d, now, "TRIAL", List.of(t), null))).andReturn().getResponse().getStatus());
        assertEquals(401, mvc.perform(post("/api/v1/activations/report").header("Authorization", "Bearer nope").contentType(MediaType.APPLICATION_JSON)
                .content(reportJson(d, now, "TRIAL", List.of(t), null))).andReturn().getResponse().getStatus());
        // 400: more than 4 tokens, a bad device code
        assertEquals(400, report(i, reportJson(d, now, "TRIAL", List.of(t, t, t, t, t), null)).getResponse().getStatus());
        assertEquals(400, report(i, reportJson(d, now, "TRIAL", List.of(t), null).replace(d.code(), "0000-0000-0000-000U")).getResponse().getStatus());
        // 413: more than 16 KB
        String big = reportJson(d, now, "TRIAL", List.of(t), "\"pad\":\"" + "x".repeat(17_000) + "\"");
        assertEquals(413, report(i, big).getResponse().getStatus());
        assertEquals(0, jdbc.queryForObject("select count(*) from act_report", Integer.class));
    }

    @Test
    void theServerCanSlowOrCutTheReports() throws Exception {
        var i = install();
        var d = dev();
        String t = trialToken(DESK, d, now - 3_600_000L);
        jdbc.update("update act_policy set val = 0 where name = 'report_next_hours'");
        try {
            JsonNode r = ok(i, reportJson(d, now, "TRIAL", List.of(t), null));
            assertEquals(0, r.get("next").asInt(), "next = 0 cuts the reports");
            assertNull(r.get("revocations"));
        } finally {
            jdbc.update("update act_policy set val = 24 where name = 'report_next_hours'");
        }
    }
}
