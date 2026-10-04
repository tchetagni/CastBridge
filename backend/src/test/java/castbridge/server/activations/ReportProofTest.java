package castbridge.server.activations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import castbridge.server.licenses.LicenseKeyring;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Audit w23-01 H1 and M5. A report is only believed when it PROVES the device: a verified activation token whose factors give the reported device code, or a signature by the
 * install key of the TV, bound to the code at first contact (like the wallet bind proof). An unproven report changes nothing (edition, open_all_until, TRIAL_RESET, commands,
 * clone marks, device links). The per-device limit is checked BEFORE the global budget is consumed.
 */
class ReportProofTest extends ActTestBase {
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

    /** A phone installation (app = phone): registered through the same public route, so any app can get a device token. */
    private Install phoneInstall() throws Exception {
        String id = UUID.randomUUID().toString();
        String report = """
                {"installId":"%s","androidIdHash":"%s","app":"phone","versionCode":7,"versionName":"0.7","channel":"stable","abi":"arm64-v8a","supportedAbis":["arm64-v8a"],
                 "sdk":34,"platform":"android","manufacturer":"X","model":"P1"}""".formatted(id, "%064x".formatted(RND.nextLong() & Long.MAX_VALUE));
        MvcResult r = mvc.perform(post("/api/v1/devices/register").contentType(MediaType.APPLICATION_JSON).content(report).header("X-Test-Country", "cm")).andExpect(status().isCreated()).andReturn();
        JsonNode b = body(r);
        String pub = b.get("deviceId").asText();
        return new Install(pub, jdbc.queryForObject("select id from device where public_id = ?", Long.class, pub), b.get("deviceToken").asText());
    }

    private static String poisonedJson(Dev victim, long at) {
        long year2099 = Instant.parse("2099-01-01T00:00:00Z").toEpochMilli();
        return "{\"v\":1,\"deviceCode\":\"" + victim.code() + "\",\"app\":{\"code\":1412,\"name\":\"0.14.12-beta\"},\"activations\":[],\"state\":{\"edition\":\"PRODUCTION\",\"usageTo\":null,\"super\":false,"
                + "\"openAllUntil\":" + year2099 + ",\"unlockUntil\":" + year2099 + ",\"trialResets\":1000,\"installedAt\":{},\"commands\":[[\"open_all\",\"deadbeef\"," + at + ",30],[\"unlock\",\"cafebabe\"," + at + ",30]]},\"at\":" + at + "}";
    }

    private int nothingWritten() {
        return jdbc.queryForObject("select (select count(*) from act_tv) + (select count(*) from act_tv_device) + (select count(*) from act_command) + (select count(*) from act_report)"
                + " + (select count(*) from act_event) + (select count(*) from act_key) + (select count(*) from act_alert)", Integer.class);
    }

    @Test
    void aStrangerInstallationCannotPoisonAVictimTvWithoutProof() throws Exception {
        Dev victim = dev();
        var attacker = install();
        MvcResult r = report(attacker, poisonedJson(victim, now));
        assertEquals(403, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        assertEquals(0, nothingWritten(), "no state change at all from an unproven report");
        // the real TV reports later with its real token: its activation is NOT marked clone, the edition is its own
        String real = trialToken(DESK, victim, now - 3_600_000L);
        var tv = install();
        assertEquals(200, report(tv, reportJson(victim, now, "TRIAL", List.of(real), null)).getResponse().getStatus());
        assertFalse(jdbc.queryForObject("select flags from act_key where fp = ?", String.class, sha256(real)).contains(",clone,"));
        assertEquals("TRIAL", jdbc.queryForObject("select edition from act_tv", String.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from act_event where type = 'TRIAL_RESET'", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from act_command", Integer.class));
    }

    @Test
    void aPhoneInstallationIsRefusedEvenWithAGoodToken() throws Exception {
        Dev d = dev();
        String t = trialToken(DESK, d, now - 3_600_000L);
        var phone = phoneInstall();
        MvcResult r = report(phone, reportJson(d, now, "TRIAL", List.of(t), null));
        assertEquals(403, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        assertEquals(0, nothingWritten());
    }

    @Test
    void aTokenOfAnotherDeviceProvesNothingAndOnlyTheCloneEvidenceIsKept() throws Exception {
        Dev owner = dev(), claimed = dev();
        String t = trialToken(DESK, owner, now - 3_600_000L);
        var i = install();
        MvcResult r = report(i, reportJson(claimed, now, "PRODUCTION", List.of(t), null));
        assertEquals(200, r.getResponse().getStatus());
        assertEquals(0, body(r).get("accepted").asInt());
        assertEquals(0, jdbc.queryForObject("select count(*) from act_tv", Integer.class), "the claimed code is not recorded");
        assertEquals(0, jdbc.queryForObject("select count(*) from act_tv_device", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from act_report", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from act_event where type in ('DEVICE_LINKED','SEEN','TRIAL_RESET','APP_VERSION')", Integer.class));
    }

    // ---- install-key proof, bound at first contact

    private static String proofFor(Ed25519PrivateKeyParameters k, String code, String devicePublicId, long at) {
        String msg = "castbridge-activation-report-v1\n" + code + "\n" + devicePublicId + "\n" + at;
        return "{\"key\":\"" + Base64.getEncoder().encodeToString(k.generatePublicKey().getEncoded()) + "\",\"at\":" + at + ",\"sig\":\"" + Base64.getEncoder().encodeToString(sign(k, msg)) + "\"}";
    }

    private static String withProof(String json, String proof) { return json.substring(0, json.length() - 1) + ",\"proof\":" + proof + "}"; }

    @Test
    void anInstallKeyProofBindsTheTvAtFirstContactAndAnotherKeyIsRefused() throws Exception {
        Dev d = dev();
        var i = install();
        Ed25519PrivateKeyParameters k1 = key(), k2 = key();
        String json = reportJson(d, now, "TRIAL", List.of(), null);
        assertEquals(403, report(i, json).getResponse().getStatus(), "no token and no proof");
        assertEquals(200, report(i, withProof(json, proofFor(k1, d.code(), i.publicId(), now))).getResponse().getStatus());
        assertEquals("TRIAL", jdbc.queryForObject("select edition from act_tv", String.class));
        // another installation of the same TV with the same key keeps working; a different key without a token is refused
        clock.set(Instant.ofEpochMilli(now).plusSeconds(3600));
        var i2 = install();
        assertEquals(200, report(i2, withProof(reportJson(d, clock.nowMs(), "TRIAL", List.of(), null), proofFor(k1, d.code(), i2.publicId(), clock.nowMs()))).getResponse().getStatus());
        clock.set(Instant.ofEpochMilli(now).plusSeconds(7200));
        var i3 = install();
        int before = jdbc.queryForObject("select count(*) from act_event", Integer.class);
        assertEquals(403, report(i3, withProof(reportJson(d, clock.nowMs(), "PRODUCTION", List.of(), null), proofFor(k2, d.code(), i3.publicId(), clock.nowMs()))).getResponse().getStatus());
        assertEquals(before, jdbc.queryForObject("select count(*) from act_event", Integer.class));
        assertEquals("TRIAL", jdbc.queryForObject("select edition from act_tv", String.class));
    }

    @Test
    void aReplayedOrStaleOrWrongCodeProofIsRefused() throws Exception {
        Dev d = dev(), other = dev();
        var i = install(); var j = install();
        Ed25519PrivateKeyParameters k = key();
        String json = reportJson(d, now, "TRIAL", List.of(), null);
        // proof made for another API installation (replayed from i on j)
        assertEquals(403, report(j, withProof(json, proofFor(k, d.code(), i.publicId(), now))).getResponse().getStatus());
        // stale (10 minutes old)
        assertEquals(403, report(i, withProof(json, proofFor(k, d.code(), i.publicId(), now - 600_000L))).getResponse().getStatus());
        // made for another device code
        assertEquals(403, report(i, withProof(json, proofFor(k, other.code(), i.publicId(), now))).getResponse().getStatus());
        assertEquals(0, nothingWritten());
        assertTrue(LicenseKeyring.verify(k.generatePublicKey().getEncoded(), "x".getBytes(StandardCharsets.UTF_8), sign(k, "x")), "self-check of the helper");
    }

    // ---- M5: the per-device limit is checked before the global budget is consumed

    @Test
    void oneDeviceTokenCannotExhaustTheGlobalReportBudget() throws Exception {
        Dev d = dev();
        String t = trialToken(DESK, d, now - 3_600_000L);
        var spammer = install();
        String json = reportJson(d, now, "TRIAL", List.of(t), null);
        int ok = 0, limited = 0;
        for (int n = 0; n < 3_100; n++) {
            int s = report(spammer, json).getResponse().getStatus();
            if (s == 200) ok++; else if (s == 429) limited++;
        }
        assertEquals(1, ok);
        assertEquals(3_099, limited);
        Dev d2 = dev();
        var honest = install();
        assertEquals(200, report(honest, reportJson(d2, now, "TRIAL", List.of(trialToken(DESK, d2, now - 3_600_000L)), null)).getResponse().getStatus(), "the honest TV is not starved by the spammer");
    }
}
