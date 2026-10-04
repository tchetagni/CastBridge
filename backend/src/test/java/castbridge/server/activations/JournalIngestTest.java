package castbridge.server.activations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/** The signed journal of an offline tool, through POST /api/v1/admin/activations/journal: projection, replay, holes, rewrites, bad keys. */
class JournalIngestTest extends ActTestBase {
    @Autowired Reconciler reconciler;
    @Autowired TvRef tvRef;
    @Autowired EventLog eventLog;

    final String deskKid = kid(DESK);
    long now;

    @BeforeEach
    void start() {
        resetModule();
        clock.set(Instant.parse("2026-10-10T08:00:00Z"));
        now = clock.nowMs();
    }

    @AfterEach
    void stop() { clock.reset(); }

    private JsonNode send(String token, int expectedStatus) throws Exception {
        MvcResult r = uploadJournal(token);
        assertEquals(expectedStatus, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        return body(r);
    }

    private JournalBuilder desk(long seq, long from) { return new JournalBuilder(DESK, seq, "desk", now, from); }

    @Test
    void aValidBatchIsStoredProjectedAndChained() throws Exception {
        var d1 = dev();
        var d2 = dev();
        String t1 = trialToken(DESK, d1, now);
        String compactFp = sha256("compact-key-" + d2.code());
        long startHour = (now - 1_767_225_600_000L) / 3_600_000L;
        var j = desk(1, 1).entry(now + 1000, "issue", JournalBuilder.issueFields(t1, null))
                .entry(now + 2000, "deliver", "fp=" + sha256(t1), "way=bt", "tv=ok")
                .entry(now + 3000, "compact", "fp=" + compactFp, "form=compact", "kind=trial", "device=" + d2.code(), "windowStartHour=" + startHour, "set=0");
        String token = j.build();
        JsonNode r = send(token, 200);
        assertEquals("OK", r.get("status").asText());
        assertEquals(3, r.get("accepted").asInt());

        Map<String, Object> k = jdbc.queryForMap("select * from act_key where fp = ?", sha256(t1));
        assertEquals("ENVELOPE", k.get("form"));
        assertEquals("TRIAL", k.get("kind"));
        assertEquals("ACTIVATED", k.get("state"), "a Bluetooth delivery acknowledged by the TV counts as a sighting");
        assertEquals(tvRef.of(d1.code()), k.get("tv_ref"));
        assertEquals(sha256(t1).substring(0, 8), k.get("tag"));
        assertEquals(deskKid, k.get("kid"));
        String flags = (String) k.get("flags");
        assertTrue(flags.contains(",declared_journal,") && flags.contains(",delivered_bt,"), flags);
        assertEquals(1, jdbc.queryForObject("select count(*) from act_tv where tv_ref = ? and device_code = ?", Integer.class, tvRef.of(d1.code()), d1.code()));

        Map<String, Object> c = jdbc.queryForMap("select * from act_key where fp = ?", compactFp);
        assertEquals("COMPACT", c.get("form"));
        assertEquals("EMISE", c.get("state"));
        assertEquals(tvRef.of(d2.code()), c.get("tv_ref"));

        Map<String, Object> tool = jdbc.queryForMap("select * from act_tool where kid = ?", deskKid);
        assertEquals("DESK", tool.get("tool"));
        assertEquals(3L, ((Number) tool.get("last_entry_n")).longValue());
        assertEquals(j.lastHash(), tool.get("last_entry_hash"));
        assertEquals(1L, ((Number) tool.get("last_batch_seq")).longValue());
        assertEquals(token, jdbc.queryForObject("select text from act_journal_batch where kid = ?", String.class, deskKid), "the signed batch is kept as received");
        assertEquals("OK", jdbc.queryForObject("select status from act_journal_batch where kid = ?", String.class, deskKid));

        for (String type : List.of("ISSUED", "DELIVERED", "ACTIVATED", "ISSUED_COMPACT", "JOURNAL_BATCH")) {
            assertTrue(jdbc.queryForObject("select count(*) from act_event where type = ?", Integer.class, type) >= 1, type);
        }
        assertTrue(eventLog.verify().ok());
        // the history designates TVs by tv_ref only
        assertEquals(0, jdbc.queryForObject("select count(*) from act_event where before_json like ? or after_json like ? or actor like ?", Integer.class, "%" + d1.code() + "%", "%" + d1.code() + "%", "%" + d1.code() + "%"));
    }

    @Test
    void replayingABatchChangesNothing() throws Exception {
        String token = desk(1, 1).entry(now, "deliver", "fp=" + "a".repeat(64), "way=qr", "tv=-").entry(now + 1, "refused", "reason=quota", "device=" + dev().code()).build();
        send(token, 200);
        int before = events();
        JsonNode r = send(token, 200);
        assertEquals("DUPLICATE", r.get("status").asText());
        assertEquals(0, r.get("accepted").asInt());
        assertEquals(2, r.get("duplicate").asInt());
        assertEquals(before, events());
    }

    @Test
    void aHoleRaisesTheAlertAfterSevenDaysAndClosesWhenTheMissingBatchArrives() throws Exception {
        var b1 = desk(1, 1).entry(now, "deliver", "fp=" + "1".repeat(64), "way=bt", "tv=ok").entry(now + 1, "deliver", "fp=" + "2".repeat(64), "way=bt", "tv=ok");
        var b2 = desk(2, 3).prev(b1.lastHash()).entry(now + 2, "deliver", "fp=" + "3".repeat(64), "way=bt", "tv=ok").entry(now + 3, "deliver", "fp=" + "4".repeat(64), "way=bt", "tv=ok");
        var b3 = desk(3, 5).prev(b2.lastHash()).entry(now + 4, "deliver", "fp=" + "5".repeat(64), "way=bt", "tv=ok");
        send(b1.build(), 200);
        JsonNode r = send(b3.build(), 200);
        assertEquals(3, r.get("gap").get("from").asInt());
        assertEquals(4, r.get("gap").get("to").asInt());
        assertEquals(1, jdbc.queryForObject("select count(*) from act_journal_gap where kid = ?", Integer.class, deskKid));

        clock.set(Instant.ofEpochMilli(now).plus(Duration.ofDays(6)));
        reconciler.reconcileAll();
        assertEquals(0, alerts("JOURNAL_GAP"), "the grace period of 7 days is not over");
        clock.set(Instant.ofEpochMilli(now).plus(Duration.ofDays(8)));
        reconciler.reconcileAll();
        assertEquals(1, alerts("JOURNAL_GAP"));
        String detail = jdbc.queryForObject("select detail from act_alert where type = 'JOURNAL_GAP'", String.class);
        assertTrue(detail.contains("3-4"), detail);

        // the missing batch (a lower batch number, but it fills the hole)
        JsonNode filled = send(b2.build(), 200);
        assertEquals(2, filled.get("accepted").asInt());
        assertNull(filled.get("gap"));
        reconciler.reconcileAll();
        assertEquals(0, alerts("JOURNAL_GAP"));
        assertEquals(0, jdbc.queryForObject("select count(*) from act_journal_gap", Integer.class));
        assertEquals("CLOSED", jdbc.queryForObject("select state from act_alert where type = 'JOURNAL_GAP'", String.class));
    }

    @Test
    void aRewrittenEntryGoesToQuarantine() throws Exception {
        var b1 = desk(1, 1).entry(now, "deliver", "fp=" + "1".repeat(64), "way=bt", "tv=ok").entry(now + 1, "deliver", "fp=" + "2".repeat(64), "way=bt", "tv=ok")
                .entry(now + 2, "deliver", "fp=" + "3".repeat(64), "way=bt", "tv=ok");
        send(b1.build(), 200);
        int ev = events();
        var forged = desk(2, 3).prev(b1.hashAt(1)).entry(now + 2, "deliver", "fp=" + "3".repeat(64), "way=bt", "tv=-");
        JsonNode r = send(forged.build(), 422);
        assertEquals("QUARANTINE", r.get("status").asText());
        assertEquals("REWRITTEN", r.get("reason").asText());
        assertEquals("QUARANTINE", jdbc.queryForObject("select status from act_journal_batch where seq = 2 and kid = ?", String.class, deskKid));
        assertEquals(1, alerts("JOURNAL_BROKEN"));
        assertEquals(3L, jdbc.queryForObject("select last_entry_n from act_tool where kid = ?", Long.class, deskKid), "a quarantined batch changes nothing");
        // only the quarantine alert and the batch line were written, no entry of the forged batch
        assertEquals(0, jdbc.queryForObject("select count(*) from act_event where idem_key like ? and type = 'DELIVERED' and after_json like '%tv=-%'", Integer.class, "J:" + deskKid + ":3%"));
        assertTrue(events() - ev <= 2);
    }

    @Test
    void aBatchThatDoesNotFollowTheLastKnownEntryIsQuarantined() throws Exception {
        var b1 = desk(1, 1).entry(now, "deliver", "fp=" + "1".repeat(64), "way=bt", "tv=ok").entry(now + 1, "deliver", "fp=" + "2".repeat(64), "way=bt", "tv=ok");
        send(b1.build(), 200);
        var b2 = desk(2, 3).prev("e".repeat(64)).entry(now + 2, "deliver", "fp=" + "3".repeat(64), "way=bt", "tv=ok");
        JsonNode r = send(b2.build(), 422);
        assertEquals("PREV_MISMATCH", r.get("reason").asText());
        assertEquals(1, alerts("JOURNAL_BROKEN"));
        assertEquals(2L, jdbc.queryForObject("select last_entry_n from act_tool where kid = ?", Long.class, deskKid));
    }

    @Test
    void unknownRevokedAndForgedSignaturesAreQuarantined() throws Exception {
        JsonNode unknown = send(new JournalBuilder(STRANGER, 1, "desk", now, 1).entry(now, "deliver", "fp=" + "1".repeat(64), "way=bt", "tv=ok").build(), 422);
        assertEquals("UNKNOWN_KEY", unknown.get("reason").asText());
        licenses.revokeKey(OWNER, kid(BURNED), "clé perdue (test)");
        JsonNode revoked = send(new JournalBuilder(BURNED, 1, "desk", now, 1).entry(now, "deliver", "fp=" + "1".repeat(64), "way=bt", "tv=ok").build(), 422);
        assertEquals("REVOKED_KEY", revoked.get("reason").asText());
        JsonNode forged = send(desk(1, 1).entry(now, "deliver", "fp=" + "1".repeat(64), "way=bt", "tv=ok").build(true), 422);
        assertEquals("BAD_SIGNATURE", forged.get("reason").asText());
        assertEquals(3, jdbc.queryForObject("select count(*) from act_journal_batch where status = 'QUARANTINE'", Integer.class));
        assertTrue(alerts("JOURNAL_BROKEN") >= 3);
        assertEquals(0, jdbc.queryForObject("select count(*) from act_key", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from act_tool where last_entry_n > 0", Integer.class));
    }

    @Test
    void theBatchNumberMustGrowForNewEntries() throws Exception {
        var b1 = desk(5, 1).entry(now, "deliver", "fp=" + "1".repeat(64), "way=bt", "tv=ok");
        send(b1.build(), 200);
        var b2 = desk(4, 2).prev(b1.lastHash()).entry(now + 1, "deliver", "fp=" + "2".repeat(64), "way=bt", "tv=ok");
        JsonNode r = send(b2.build(), 409);
        assertEquals("REJECT", r.get("status").asText());
        assertEquals("SEQ_NOT_INCREASING", r.get("reason").asText());
    }

    @Test
    void garbageIs400AndNothingIsWritten() throws Exception {
        int ev = events();
        send("garbage", 400);
        send("", 400);
        send(trialToken(DESK, dev(), now), 400);
        assertEquals(ev, events());
    }

    @Test
    void commandsUpdateTheTvAndAreRememberedForTheReconciliation() throws Exception {
        var d = dev();
        var j = desk(1, 1)
                .entry(now, "command", "power=open_all", "action=-", "bundles=3", "lots=-", "days=30", "clamped=0", "device=" + d.code(), "challenge=0a1b2c3d", "result=ok")
                .entry(now + 1, "command", "power=support", "action=reset-trial", "bundles=0", "lots=-", "days=0", "clamped=0", "device=" + d.code(), "challenge=0a1b2c3e", "result=ok")
                .entry(now + 2, "command", "power=unlock", "action=-", "bundles=1", "lots=-", "days=7", "clamped=1", "device=" + d.code(), "challenge=0a1b2c3f", "result=refused:bad-challenge");
        send(j.build(), 200);
        String ref = tvRef.of(d.code());
        Map<String, Object> tv = jdbc.queryForMap("select * from act_tv where tv_ref = ?", ref);
        assertEquals(now + 30 * 86_400_000L, ((java.sql.Timestamp) tv.get("open_all_until")).getTime());
        assertEquals(1, ((Number) tv.get("trial_resets")).intValue());
        assertNull(tv.get("unlock_until"), "a refused command changes nothing on the TV");
        assertEquals(1, jdbc.queryForObject("select count(*) from act_event where type = 'COMMAND_OPEN_ALL' and tv_ref = ?", Integer.class, ref));
        assertEquals(1, jdbc.queryForObject("select count(*) from act_event where type = 'TRIAL_RESET' and tv_ref = ?", Integer.class, ref));
        assertEquals(1, jdbc.queryForObject("select count(*) from act_command where tv_ref = ? and challenge = '0a1b2c3d' and declared = true", Integer.class, ref));
        assertFalse(jdbc.queryForList("select * from act_command where tv_ref = ?", ref).isEmpty());
        assertNotNull(tv.get("device_code"));
    }
}
