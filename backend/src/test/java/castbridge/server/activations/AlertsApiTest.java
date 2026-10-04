package castbridge.server.activations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Soft alerts: one open alert per object (repeats only count), acknowledge, decide with a reason, a new alert after the decision; never an automatic revocation. */
class AlertsApiTest extends ActTestBase {
    @Autowired AlertService alertService;
    @Autowired EventLog eventLog;
    @Autowired PlatformTransactionManager tx;

    static final String API = "/api/v1/admin/activations";

    @BeforeEach
    void start() {
        resetModule();
        clock.set(Instant.parse("2026-10-10T08:00:00Z"));
    }

    @AfterEach
    void stop() { clock.reset(); }

    @Test
    void oneOpenAlertPerObjectAndRepeatsOnlyCountNewEvidence() {
        long a = alertService.raise(AlertService.Type.CLONE, "a".repeat(64), "1234567890abcdef", kid(DESK), null, "deux appareils", "ev1");
        long b = alertService.raise(AlertService.Type.CLONE, "a".repeat(64), "1234567890abcdef", kid(DESK), null, "deux appareils", "ev1");
        long c = alertService.raise(AlertService.Type.CLONE, "a".repeat(64), "1234567890abcdef", kid(DESK), null, "trois appareils", "ev2");
        assertEquals(a, b);
        assertEquals(a, c);
        assertEquals(1, jdbc.queryForObject("select count(*) from act_alert", Integer.class));
        assertEquals(2, jdbc.queryForObject("select hits from act_alert", Integer.class), "same evidence = nothing, new evidence = one more hit");
        assertEquals("high", jdbc.queryForObject("select severity from act_alert", String.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from act_event where type = 'ALERT_OPENED'", Integer.class), "one event when the alert opens");
        long other = alertService.raise(AlertService.Type.CLONE, "b".repeat(64), "1234567890abcdef", kid(DESK), null, "autre", "ev1");
        assertNotEquals(a, other);
    }

    @Test
    void severitiesFollowTheDesignTable() {
        assertEquals("critical", AlertService.Type.JOURNAL_BROKEN.severity());
        assertEquals("high", AlertService.Type.UNDECLARED.severity());
        assertEquals("high", AlertService.Type.JOURNAL_GAP.severity());
        assertEquals("high", AlertService.Type.CLONE.severity());
        assertEquals("high", AlertService.Type.OUT_OF_WINDOW.severity());
        assertEquals("medium", AlertService.Type.ENDED_IN_USE.severity());
        assertEquals("high", AlertService.Type.UNKNOWN_KEY.severity());
        assertEquals("low", AlertService.Type.LICENSE_PENDING.severity());
        assertEquals("medium", AlertService.Type.OVER_SEATS.severity());
        assertEquals("high", AlertService.Type.UNDECLARED_COMMAND.severity());
        assertEquals("medium", AlertService.Type.TOOL_STALE.severity());
    }

    @Test
    void ackThenCloseWithAReasonThenANewAlertCanOpen() throws Exception {
        long id = alertService.raise(AlertService.Type.UNDECLARED, "c".repeat(64), "1234567890abcdef", kid(DESK), null, "non déclarée", "e");
        MvcResult list = mvc.perform(adminGet(API + "/alerts?state=OPEN")).andReturn();
        JsonNode items = body(list).get("items");
        assertEquals(1, items.size());
        assertEquals(id, items.get(0).get("id").asLong());

        assertEquals(200, mvc.perform(post(API + "/alerts/" + id + "/ack").header("Authorization", ADMIN)).andReturn().getResponse().getStatus());
        assertEquals("ACK", jdbc.queryForObject("select state from act_alert where id = ?", String.class, id));
        assertEquals("api-token", jdbc.queryForObject("select decided_by from act_alert where id = ?", String.class, id));
        // closing needs a reason
        assertEquals(400, mvc.perform(post(API + "/alerts/" + id + "/close").header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn().getResponse().getStatus());
        assertEquals(400, mvc.perform(post(API + "/alerts/" + id + "/close").header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"  \"}")).andReturn().getResponse().getStatus());
        assertEquals(200, mvc.perform(post(API + "/alerts/" + id + "/close").header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"journal du bureau remonté\"}"))
                .andReturn().getResponse().getStatus());
        var row = jdbc.queryForMap("select * from act_alert where id = ?", id);
        assertEquals("CLOSED", row.get("state"));
        assertEquals("journal du bureau remonté", row.get("reason"));
        assertNull(row.get("open_key"), "the key is freed so that a later repetition opens a NEW alert");
        assertEquals(2, jdbc.queryForObject("select count(*) from act_event where type = 'ALERT_DECIDED'", Integer.class), "ack and close are two decisions");
        assertEquals(409, mvc.perform(post(API + "/alerts/" + id + "/close").header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"encore\"}")).andReturn().getResponse().getStatus());
        assertEquals(409, mvc.perform(post(API + "/alerts/" + id + "/ack").header("Authorization", ADMIN)).andReturn().getResponse().getStatus());
        assertEquals(404, mvc.perform(post(API + "/alerts/987654/ack").header("Authorization", ADMIN)).andReturn().getResponse().getStatus());

        long again = alertService.raise(AlertService.Type.UNDECLARED, "c".repeat(64), "1234567890abcdef", kid(DESK), null, "non déclarée", "e");
        assertNotEquals(id, again);
        assertTrue(eventLog.verify().ok());
    }

    @Test
    void anAlertNeverChangesALicenceOrASeat() {
        String before = jdbc.queryForList("select * from lic_license").toString() + jdbc.queryForList("select * from lic_seat").toString() + jdbc.queryForList("select * from lic_revocation").toString();
        long id = alertService.raise(AlertService.Type.OVER_SEATS, null, null, null, "lic-xyz", "trop de TV", "e");
        new TransactionTemplate(tx).executeWithoutResult(s -> alertService.ack(OWNER, id));
        new TransactionTemplate(tx).executeWithoutResult(s -> alertService.close(OWNER, id, "ok"));
        assertEquals(before, jdbc.queryForList("select * from lic_license").toString() + jdbc.queryForList("select * from lic_seat").toString() + jdbc.queryForList("select * from lic_revocation").toString());
    }
}
