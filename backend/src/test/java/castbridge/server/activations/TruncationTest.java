package castbridge.server.activations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Audit w23-01 H3. Truncation and rollback of the chains are SEEN: the signed daily checkpoints are verified against the live head and the archive anchors must be signed.
 * A tail removed with the head rewound, a forged anchor, a rewritten checkpoint all make {@code verify()} RED. (What stays invisible to the server alone: a tail cut off AFTER the
 * last checkpoint and before the owner kept it outside the server: that is why the signed lines are exported, docs/ACTIVATION-TRACKING.md.)
 */
class TruncationTest extends ActTestBase {
    @Autowired EventLog log;
    @Autowired ReadAudit readAudit;
    @Autowired Checkpoints checkpoints;
    @Autowired Archiver archiver;
    @Autowired PlatformTransactionManager tx;

    @BeforeEach
    void start() {
        resetModule();
        clock.set(Instant.parse("2026-10-10T08:00:00Z"));
    }

    @AfterEach
    void stop() { clock.reset(); }

    private void events(int n) {
        for (int i = 0; i < n; i++) {
            final int k = i;
            new TransactionTemplate(tx).executeWithoutResult(s -> log.append(new EventLog.NewEvent("ISSUED", clock.nowMs() + k, "f".repeat(64), "abcdef0123456789", "lic-1", "0011223344556677", "TOOL",
                    "0011223344556677", "JOURNAL", null, "{\"k\":" + k + "}", "trunc-" + clock.nowMs() + "-" + k + "-" + System.nanoTime())));
        }
    }

    private void checkpointToday() { checkpoints.create(LocalDate.ofInstant(clock.now(), ZoneOffset.UTC)); }

    @Test
    void aTailRemovedWithTheHeadRewoundIsSeenBecauseASignedCheckpointAttestsTheLongerHistory() {
        events(10);
        checkpointToday();
        assertTrue(log.verify().ok(), "the intact history verifies");
        // the attacker removes the last 3 lines AND rewinds the head to the line 7 (the chain alone is then consistent)
        String hash7 = jdbc.queryForObject("select hash from act_event where id = 7", String.class);
        jdbc.update("delete from act_event where id > 7");
        jdbc.update("update act_event_head set last_id = 7, last_hash = ? where id = 1", hash7);
        var v = log.verify();
        assertFalse(v.ok(), "truncation must be seen");
        assertTrue(v.problem().toLowerCase().contains("tronc") || v.problem().toLowerCase().contains("point de contrôle"), v.problem());
    }

    @Test
    void aTruncationOfTheReadsChainIsSeenToo() throws Exception {
        for (int i = 0; i < 6; i++) readAudit.record("api-token", "OWNER", "api", "/api/v1/admin/activations/dashboard", null, null, 1, false);
        checkpointToday();
        assertTrue(readAudit.verify().ok());
        String hash4 = jdbc.queryForObject("select hash from adm_read_audit where id = 4", String.class);
        jdbc.update("delete from adm_read_audit where id > 4");
        jdbc.update("update act_event_head set last_id = 4, last_hash = ? where id = 2", hash4);
        assertFalse(readAudit.verify().ok());
    }

    @Test
    void aForgedArchiveAnchorCannotHideTheDeletionOfTheWholeHistory() {
        events(6);
        checkpointToday();
        String forged = "ab".repeat(32);
        jdbc.update("insert into act_archive (table_name, from_id, to_id, from_at, to_at, file, sha256, row_count, last_hash, removed, created_at) values ('act_event', 1, 6, ?, ?, 'forged.gz', ?, 6, ?, true, ?)",
                java.sql.Timestamp.from(clock.now()), java.sql.Timestamp.from(clock.now()), "00".repeat(32), forged, java.sql.Timestamp.from(clock.now()));
        jdbc.update("delete from act_event");
        jdbc.update("update act_event_head set last_id = 6, last_hash = ? where id = 1", forged);
        var v = log.verify();
        assertFalse(v.ok(), "an anchor nobody signed must not be believed");
        assertTrue(v.problem().toLowerCase().contains("ancre") || v.problem().toLowerCase().contains("archive"), v.problem());
    }

    @Test
    void aRealSignedArchiveStillVerifiesAndItsAnchorIsSigned() {
        events(5);
        clock.set(Instant.parse("2029-01-01T00:00:00Z"));   // the 5 lines are now older than 24 months
        events(2);
        checkpointToday();
        archiver.archive(ActAccess.API_TOKEN, "test", true);
        assertTrue(jdbc.queryForObject("select count(*) from act_archive where removed = true and signature is not null and sig_kid is not null", Integer.class) >= 1, "the anchor is signed");
        var v = log.verify();
        assertTrue(v.ok(), v.problem());
    }

    @Test
    void aRewrittenCheckpointIsSeen() {
        events(4);
        checkpointToday();
        jdbc.update("update act_checkpoint set event_head = ?", "cd".repeat(32));
        var v = log.verify();
        assertFalse(v.ok());
        assertTrue(v.problem().toLowerCase().contains("point de contrôle"), v.problem());
    }

    @Test
    void thePublicKeyOfTheCheckpointsIsExposedSoTheOwnerKeepsItOutsideTheServer() throws Exception {
        events(2);
        checkpointToday();
        String json = mvc.perform(adminGet("/api/v1/admin/activations/checkpoints")).andReturn().getResponse().getContentAsString();
        var n = this.json.readTree(json);
        assertEquals(44, n.path("publicKey").asText().length(), "base64 of the 32 bytes of the Ed25519 public key: " + json);
        assertEquals(1, n.path("items").size());
    }
}
