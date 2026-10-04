package castbridge.server.activations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import castbridge.server.licenses.ActivationService;
import castbridge.server.licenses.LicenseService;
import castbridge.server.licenses.ProductService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** The right to erasure of the licence module wipes the readable device code from the tracker; the chain of the history stays intact and verifiable. */
class ErasureTest extends ActTestBase {
    @Autowired ErasureHook hook;
    @Autowired IssuanceTap issuanceTap;
    @Autowired EventLog eventLog;
    @Autowired TvRef tvRef;

    @BeforeEach
    void start() {
        resetModule();
        clock.set(Instant.now());
    }

    @AfterEach
    void stop() { clock.reset(); }

    @Test
    void anErasedClientsDeviceCodeDisappearsButNotTheChain() throws Exception {
        Dev d = dev();
        try {
            products.get("p-act");
        } catch (castbridge.server.web.ApiException e) {
            products.create(OWNER, new ProductService.NewProduct("p-act", "Produit de suivi", "A_LA_CARTE", null, List.of("learn/test"), null, null, List.of("classe-act")));
        }
        var client = clients.create(OWNER, "Client à effacer", "effacer@example.invalid", null);
        var lic = licenses.create(OWNER, new LicenseService.NewLicense(null, client.id(), "PAID", 1, null, Instant.now().plusSeconds(86400L * 365), null, null, List.of("p-act")));
        String token = activations.issue(OWNER, new ActivationService.IssueRequest(lic.licenseId(), "tv", d.text(), null, null, null, null), "server-api").text();
        issuanceTap.runOnce();
        String ref = tvRef.of(d.code());
        // the same activation also declared by a signed journal, which keeps the device code in its signed text
        String seat = jdbc.queryForObject("select seat_id from lic_seat s join lic_license l on l.id = s.license_pk where l.license_id = ?", String.class, lic.licenseId());
        String journal = new JournalBuilder(DESK, 1, "desk", clock.nowMs(), 1).entry(clock.nowMs(), "issue", JournalBuilder.issueFields(token, null)).build();
        assertEquals(200, uploadJournal(journal).getResponse().getStatus());
        assertEquals(d.code(), jdbc.queryForObject("select device_code from act_tv where tv_ref = ?", String.class, ref));
        assertTrue(jdbc.queryForObject("select text from act_journal_batch", String.class).length() > 0);
        assertEquals(200, mvc.perform(adminGet("/api/v1/admin/activations/tvs/" + d.code())).andReturn().getResponse().getStatus());
        assertEquals(0, hook.runOnce(), "nobody erased yet");

        clients.erase(OWNER, client.id(), "demande du client (test)");
        assertEquals(1, hook.runOnce());
        assertNull(jdbc.queryForObject("select device_code from act_tv where tv_ref = ?", String.class, ref));
        assertEquals(1, jdbc.queryForObject("select count(*) from act_event where type = 'ERASED' and tv_ref = ?", Integer.class, ref));
        assertFalse(jdbc.queryForObject("select text from act_journal_batch", String.class).contains(d.code()), "the signed journal no longer holds the code");
        assertEquals(1, jdbc.queryForObject("select count(*) from act_key where tv_ref = ?", Integer.class, ref), "the activation row stays, designated by tv_ref");
        EventLog.Verification v = eventLog.verify();
        assertTrue(v.ok(), "the chain does not depend on the erased column: " + v.problem());
        mvc.perform(adminGet("/api/v1/admin/activations/tvs/" + d.code())).andExpect(status().isNotFound());
        assertEquals(0, hook.runOnce(), "nothing left to erase");
        assertEquals(seat.length(), 16);
    }
}
