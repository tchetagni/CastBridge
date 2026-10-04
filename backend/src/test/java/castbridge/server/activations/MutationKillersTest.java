package castbridge.server.activations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import castbridge.server.licenses.ActivationService;
import castbridge.server.licenses.Actor;
import castbridge.server.licenses.LicenseService;
import castbridge.server.licenses.ProductService;
import castbridge.server.licenses.Role;
import castbridge.server.web.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Tests that kill the five mutations that survived the first audit (audit w23-01, « Mutations » M6 to M10): a blocked device is refused, the secret parameters never reach the audit
 * line, an erased code is not put back by a later report, a phone console session never goes beyond reading and uploading, a batch that fills a hole must fit the batch after it.
 * (M10 is also a vector of journal-vectors.json: gap-fill-next-mismatch, consumed by JournalVectorsTest.)
 */
class MutationKillersTest extends ActTestBase {
    @Autowired ErasureHook hook;
    @Autowired TvRef tvRef;

    @BeforeEach
    void start() {
        resetModule();
        clock.set(Instant.parse("2026-10-10T08:00:00Z"));
    }

    @AfterEach
    void stop() { clock.reset(); }

    /** M6: ReportController no longer refused a device blocked by the administrator. */
    @Test
    void aBlockedDeviceIsRefusedWith403AndNothingIsRecorded() throws Exception {
        Dev d = dev();
        String t = trialToken(DESK, d, clock.nowMs() - 3_600_000L);
        var i = install();
        jdbc.update("update device set blocked = true where public_id = ?", i.publicId());
        var r = report(i, reportJson(d, clock.nowMs(), "TRIAL", List.of(t), null));
        assertEquals(403, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        assertEquals(0, jdbc.queryForObject("select count(*) from act_tv", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from act_key", Integer.class));
    }

    /** M7: ReadAudit no longer dropped the secret parameters. */
    @Test
    void theSecretParametersNeverReachTheAuditLine() {
        Map<String, String[]> p = new java.util.LinkedHashMap<>();
        for (String k : List.of("token", "Authorization", "password", "secret", "api_key", "signature", "challenge", "totp", "bearer")) p.put(k, new String[] {"valeur-secrete-" + k});
        p.put("state", new String[] {"ACTIVATED"});
        p.put("cursor", new String[] {"abc"});
        assertEquals("state=ACTIVATED", ReadAudit.normalize(p));
    }

    /** M8: the guard of the erasure (a later source never puts the code back). */
    @Test
    void anErasedCodeIsNotPutBackByALaterReport() throws Exception {
        Dev d = dev();
        try {
            products.get("p-act");
        } catch (ApiException e) {
            products.create(OWNER, new ProductService.NewProduct("p-act", "Produit de suivi", "A_LA_CARTE", null, List.of("learn/test"), null, null, List.of("classe-act")));
        }
        var client = clients.create(OWNER, "Client effacé (mutation)", "mut@example.invalid", null);
        var lic = licenses.create(OWNER, new LicenseService.NewLicense(null, client.id(), "PAID", 1, null, Instant.now().plusSeconds(86400L * 365), null, null, List.of("p-act")));
        String token = activations.issue(OWNER, new ActivationService.IssueRequest(lic.licenseId(), "tv", d.text(), null, null, null, null), "server-api").text();
        assertEquals(200, report(install(), reportJson(d, clock.nowMs(), "PRODUCTION", List.of(token), null)).getResponse().getStatus());
        clients.erase(OWNER, client.id(), "demande du client (test)");
        assertEquals(1, hook.runOnce());
        String ref = tvRef.of(d.code());
        assertNull(jdbc.queryForObject("select device_code from act_tv where tv_ref = ?", String.class, ref));
        clock.set(Instant.ofEpochMilli(clock.nowMs()).plusSeconds(3 * 3600));
        assertEquals(200, report(install(), reportJson(d, clock.nowMs(), "PRODUCTION", List.of(token), null)).getResponse().getStatus());
        assertNull(jdbc.queryForObject("select device_code from act_tv where tv_ref = ?", String.class, ref), "a report after the erasure does not bring the readable code back");
    }

    /** M9: the channel restriction of a phone console session lives in ActPermissions.require, not only in allows (ConsoleSessionTest only tested allows). */
    @Test
    void aPhoneChannelNeverGoesBeyondReadingAndUploadingAJournal() {
        Actor phone = new Actor("console-tel", Role.OWNER, "phone", true);
        for (ActPermissions.Perm p : ActPermissions.Perm.values()) {
            boolean allowed = p == ActPermissions.Perm.ACT_READ || p == ActPermissions.Perm.ACT_JOURNAL_UPLOAD;
            if (allowed) {
                ActPermissions.require(phone, p, true);
                assertTrue(ActPermissions.allows(phone, p));
            } else {
                ApiException e = assertThrows(ApiException.class, () -> ActPermissions.require(phone, p, true), p + " must be refused to a phone console session");
                assertEquals(403, e.status().value());
                assertFalse(ActPermissions.allows(phone, p));
            }
        }
    }

    /** M10 through the API: a batch that fills a hole but does not fit the batch AFTER it is quarantined (the verifier alone is covered by the vector gap-fill-next-mismatch). */
    @Test
    void aBatchFillingAHoleMustFitTheNextBatchThroughTheApi() throws Exception {
        String dev = dev().code();
        long t = clock.nowMs();
        var b1 = new JournalBuilder(DESK, 1, "desk", t, 1).entry(t, "refused", "reason=x", "device=" + dev).entry(t + 1, "refused", "reason=x", "device=" + dev);
        String n3 = new JournalBuilder(DESK, 3, "desk", t, 5).prev("d".repeat(64)).entry(t + 4, "refused", "reason=x", "device=" + dev).build();
        String n2 = new JournalBuilder(DESK, 2, "desk", t, 3).prev(b1.lastHash()).entry(t + 2, "refused", "reason=x", "device=" + dev).entry(t + 3, "refused", "reason=x", "device=" + dev).build();
        assertEquals(200, uploadJournal(b1.build()).getResponse().getStatus());
        assertEquals(200, uploadJournal(n3).getResponse().getStatus());
        var r = uploadJournal(n2);
        assertEquals(422, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        assertTrue(r.getResponse().getContentAsString().contains("PREV_MISMATCH"));
    }
}
