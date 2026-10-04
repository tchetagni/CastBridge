package castbridge.server.activations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import castbridge.server.licenses.ActivationService;
import castbridge.server.licenses.LicenseService;
import castbridge.server.licenses.ProductService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Audit w23-01 M3. The cold archive with removal takes old lines out of act_event: it must not resurrect an erased device code (the erasure lives in its own tombstone table,
 * never archived) nor break the idempotence of the signed journals (the hash of each entry lives in its own table, never archived).
 */
class ColdArchiveTest extends ActTestBase {
    @Autowired ErasureHook hook;
    @Autowired IssuanceTap issuanceTap;
    @Autowired EventLog eventLog;
    @Autowired Archiver archiver;
    @Autowired TvRef tvRef;

    @BeforeEach
    void start() {
        resetModule();
        clock.set(Instant.parse("2026-10-10T08:00:00Z"));
    }

    @AfterEach
    void stop() { clock.reset(); }

    private String issueFor(Dev d, String clientName) {
        try {
            products.get("p-act");
        } catch (castbridge.server.web.ApiException e) {
            products.create(OWNER, new ProductService.NewProduct("p-act", "Produit de suivi", "A_LA_CARTE", null, List.of("learn/test"), null, null, List.of("classe-act")));
        }
        var client = clients.create(OWNER, clientName, "archive@example.invalid", null);
        var lic = licenses.create(OWNER, new LicenseService.NewLicense(null, client.id(), "PAID", 1, null, Instant.now().plusSeconds(86400L * 365), null, null, List.of("p-act")));
        String token = activations.issue(OWNER, new ActivationService.IssueRequest(lic.licenseId(), "tv", d.text(), null, null, null, null), "server-api").text();
        clients.erase(OWNER, client.id(), "demande du client (test)");
        return token;
    }

    @Test
    void anErasedCodeDoesNotComeBackAfterTheColdArchiveRemovedItsErasureLine() throws Exception {
        Dev d = dev();
        String token = issueFor(d, "Client effacé puis archivé");
        var tv = install();
        assertEquals(200, report(tv, reportJson(d, clock.nowMs(), "PRODUCTION", List.of(token), null)).getResponse().getStatus());
        String ref = tvRef.of(d.code());
        assertEquals(d.code(), jdbc.queryForObject("select device_code from act_tv where tv_ref = ?", String.class, ref));
        assertEquals(1, hook.runOnce());
        assertNull(jdbc.queryForObject("select device_code from act_tv where tv_ref = ?", String.class, ref));

        clock.set(Instant.parse("2029-04-10T08:00:00Z"));   // 30 months later: the lines are older than 24 months
        archiver.archive(ActAccess.API_TOKEN, "test archive", true);
        assertEquals(0, jdbc.queryForObject("select count(*) from act_event where type = 'ERASED'", Integer.class), "the erasure line left the database with the archive");

        var tv2 = install();
        assertEquals(200, report(tv2, reportJson(d, clock.nowMs(), "PRODUCTION", List.of(token), null)).getResponse().getStatus());
        assertNull(jdbc.queryForObject("select device_code from act_tv where tv_ref = ?", String.class, ref), "the right to erasure wins over a later report, archive or not");
        assertTrue(eventLog.verify().ok(), eventLog.verify().problem());
    }

    @Test
    void anOldJournalUploadedAgainAfterTheArchiveIsStillADuplicate() throws Exception {
        Dev d = dev();
        String token = trialToken(DESK, d, clock.nowMs() - 3_600_000L);
        String journal = new JournalBuilder(DESK, 1, "desk", clock.nowMs(), 1).entry(clock.nowMs(), "issue", JournalBuilder.issueFields(token, null)).build();
        assertEquals(200, uploadJournal(journal).getResponse().getStatus());
        int issuedEvents = jdbc.queryForObject("select count(*) from act_event where type = 'ISSUED'", Integer.class);
        assertEquals(1, issuedEvents);

        clock.set(Instant.parse("2029-04-10T08:00:00Z"));
        archiver.archive(ActAccess.API_TOKEN, "test archive", true);
        assertEquals(0, jdbc.queryForObject("select count(*) from act_event where type = 'ISSUED'", Integer.class), "the old line was removed");

        var r = uploadJournal(journal);
        String out = r.getResponse().getContentAsString();
        assertTrue(out.contains("DUPLICATE"), "the same batch again is a duplicate, not new events: " + out);
        assertEquals(0, jdbc.queryForObject("select count(*) from act_event where type = 'ISSUED'", Integer.class), "no event written twice");
    }
}
