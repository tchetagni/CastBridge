package castbridge.server.activations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import castbridge.server.licenses.ActivationService;
import castbridge.server.licenses.DeviceIdentity.Factor;
import castbridge.server.licenses.LedgerService;
import castbridge.server.licenses.LicenseService;
import castbridge.server.licenses.RegistryEvent;
import castbridge.server.licenses.WireActivation;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** The read-only taps on the licence module: server issuances, signed registry imports, revocations and the licence audit, followed by cursors, never written to. */
class TapsTest extends ActTestBase {
    @Autowired IssuanceTap issuanceTap;
    @Autowired LicenseAuditTap auditTap;
    @Autowired LedgerService ledger;
    @Autowired TvRef tvRef;
    @Autowired EventLog eventLog;

    @BeforeEach
    void start() {
        resetModule();
        clock.set(Instant.now());
    }

    @AfterEach
    void stop() { clock.reset(); }

    private LicenseService.LicenseRow newLicense(int seats) {
        serverIssuedProduction(dev());   // makes sure the product exists
        var client = clients.create(OWNER, "Client taps " + SEQ.incrementAndGet(), "taps@example.invalid", null);
        return licenses.create(OWNER, new LicenseService.NewLicense(null, client.id(), "PAID", seats, null, Instant.now().plusSeconds(86400L * 365), null, null, List.of("p-act")));
    }

    @Test
    void aServerIssuanceBecomesAnIssuedEventAndAnInventoryRowOnce() {
        Dev d = dev();
        var lic = newLicense(2);
        String token = activations.issue(OWNER, new ActivationService.IssueRequest(lic.licenseId(), "tv", d.text(), null, null, null, null), "server-api").text();
        resetModule();     // only this issuance is of interest (the helper above issued another one)
        int first = issuanceTap.runOnce();
        assertTrue(first >= 1);
        Map<String, Object> k = jdbc.queryForMap("select * from act_key where fp = ?", sha256(token));
        assertEquals("PRODUCTION", k.get("kind"));
        assertEquals(lic.licenseId(), k.get("license_id"));
        assertEquals(tvRef.of(d.code()), k.get("tv_ref"));
        assertTrue(((String) k.get("flags")).contains(",server_issued,"));
        assertEquals("EMISE", k.get("state"));
        assertEquals(kid(keyringSigner()), k.get("kid"));
        int ev = events();
        assertEquals(0, issuanceTap.runOnce(), "the cursor remembers: nothing new");
        assertEquals(ev, events());
        assertEquals(1, jdbc.queryForObject("select count(*) from act_event where type = 'ISSUED' and fp = ?", Integer.class, sha256(token)));
    }

    private Ed25519PrivateKeyParameters keyringSigner() { return new Ed25519PrivateKeyParameters(SERVER_SEED, 0); }

    // ---- registry files, built like LicenseTestBase does (every secret is a throwaway key generated at random)

    private static RegistryEvent event(Ed25519PrivateKeyParameters signer, String type, long at, List<String> fields, Map<Factor, String> factors) {
        String kid = kid(signer);
        List<String> lines = new ArrayList<>(List.of(RegistryEvent.FORMAT, "type=" + type, "kid=" + kid, "at=" + at));
        lines.addAll(fields);
        Map<Factor, String> sorted = new EnumMap<>(Factor.class);
        sorted.putAll(factors);
        sorted.forEach((f, h) -> lines.add("factor=" + f.name() + "|" + h));
        String text = String.join("\n", lines);
        return new RegistryEvent(kid, text, Base64.getEncoder().encodeToString(sign(signer, text)));
    }

    private byte[] file(List<RegistryEvent> events) throws IOException {
        ObjectNode root = json.createObjectNode();
        root.put("format", LedgerService.FORMAT);
        ArrayNode arr = root.putArray("events");
        for (RegistryEvent e : events) {
            ObjectNode n = arr.addObject();
            n.put("id", e.id());
            n.put("kid", e.kid());
            n.put("text", e.text());
            n.put("signature", e.signature());
        }
        return json.writeValueAsBytes(root);
    }

    @Test
    void aRegistryImportBecomesEventsAndDeclaresTheActivationWhenItsTokenArrives() throws Exception {
        Dev d = dev(), moved = dev();
        String license = "lic-regimp0001";
        long at = System.currentTimeMillis() - 3_600_000L;
        String nonce = rnd32().substring(0, 16);
        String seat = WireActivation.defaultSeat(license, d.fp());
        var events = List.of(
                event(DESK, "license", at, List.of("license=" + license, "seats=2", "maxTransfersPerYear=2"), Map.of()),
                event(DESK, "issue", at + 1, List.of("license=" + license, "seat=" + seat, "subject=tv", "kind=production", "nonce=" + nonce, "notAfter=" + (at + 48 * 3_600_000L), "k=" + d.k()), d.fp()),
                event(DESK, "transfer", at + 2, List.of("license=" + license, "seat=" + seat, "k=" + moved.k(), "nonce=" + rnd32().substring(0, 16)), moved.fp()));
        var report = ledger.importLedger(OWNER, file(events), false, true);
        assertTrue(report.applied() >= 2, report.toString());
        String licBefore = jdbc.queryForList("select * from lic_license").toString() + jdbc.queryForList("select * from lic_event").toString();
        assertTrue(auditTap.runOnce() >= 2);
        assertTrue(issuanceTap.runOnce() >= 1);

        assertEquals(1, jdbc.queryForObject("select count(*) from act_event where type = 'REGISTRY_IMPORT'", Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from act_event where type = 'ISSUED' and fp is null and license_id = ?", Integer.class, license), "an imported issuance has no token, hence no fp");
        assertEquals(1, jdbc.queryForObject("select count(*) from act_event where type = 'TRANSFERRED' and license_id = ?", Integer.class, license));
        assertEquals(1, jdbc.queryForObject("select count(*) from act_reg_issue where kid = ? and nonce = ?", Integer.class, kid(DESK), nonce));
        assertEquals(0, jdbc.queryForObject("select count(*) from act_key where license_id = ?", Integer.class, license), "no token yet: nothing to put in the inventory");

        // the TV now reports the token: declared by the registry (kid + nonce), so not "undeclared"
        WireActivation.Fields f = new WireActivation.Fields("production", "tv", kid(DESK), at, nonce, at, at, at + 48 * 3_600_000L, license, seat, d.k(), d.fp(), List.of());
        String token = WireActivation.token(f, sign(DESK, WireActivation.payload(f)));
        var install = install();
        clock.set(Instant.ofEpochMilli(at + 3_600_000L));
        assertEquals(200, report(install, reportJson(d, clock.nowMs(), "PRODUCTION", List.of(token), null)).getResponse().getStatus());
        String flags = jdbc.queryForObject("select flags from act_key where fp = ?", String.class, sha256(token));
        assertTrue(flags.contains(",declared_registry,"), flags);
        assertFalse(flags.contains(",undeclared,"), flags);
        assertEquals(licBefore, jdbc.queryForList("select * from lic_license").toString() + jdbc.queryForList("select * from lic_event").toString());
        int ev = events();
        auditTap.runOnce();
        assertEquals(ev, events(), "the cursors remember");
    }

    @Test
    void licenceChangesAndSeatReleasesAreCopiedWithTheirContext() {
        Dev d = dev();
        var lic = newLicense(2);
        activations.issue(OWNER, new ActivationService.IssueRequest(lic.licenseId(), "tv", d.text(), null, null, null, null), "server-api");
        String seat = jdbc.queryForObject("select seat_id from lic_seat s join lic_license l on l.id = s.license_pk where l.license_id = ?", String.class, lic.licenseId());
        resetModule();
        licenses.suspend(OWNER, lic.licenseId(), "impayé (test)");
        licenses.resume(OWNER, lic.licenseId(), "payé (test)");
        licenses.extend(OWNER, lic.licenseId(), Instant.now().plusSeconds(86400L * 400), "prolongation (test)");
        licenses.releaseSeat(OWNER, lic.licenseId(), seat, "poste libéré (test)");
        auditTap.runOnce();
        assertEquals(3, jdbc.queryForObject("select count(*) from act_event where type = 'LICENSE_CHANGED' and license_id = ?", Integer.class, lic.licenseId()));
        assertEquals(1, jdbc.queryForObject("select count(*) from act_event where type = 'SEAT_RELEASED' and license_id = ?", Integer.class, lic.licenseId()));
        String after = jdbc.queryForObject("select after_json from act_event where type = 'LICENSE_CHANGED' and after_json like '%SUSPEND%' and license_id = ?", String.class, lic.licenseId());
        assertTrue(after.contains("SUSPENDED"), after);
        String extend = jdbc.queryForObject("select after_json from act_event where type = 'LICENSE_CHANGED' and after_json like '%EXTEND%' and license_id = ?", String.class, lic.licenseId());
        assertTrue(extend.contains("to="), extend);
        assertEquals("ADMIN", jdbc.queryForObject("select actor_type from act_event where type = 'SEAT_RELEASED'", String.class));
        assertEquals("LICENSE", jdbc.queryForObject("select source from act_event where type = 'SEAT_RELEASED'", String.class));
        assertTrue(eventLog.verify().ok());
    }

    @Test
    void aRevokedKeyOrSeatRevokesTheActivationsItCovers() throws Exception {
        Dev d = dev();
        var lic = newLicense(2);
        String token = activations.issue(OWNER, new ActivationService.IssueRequest(lic.licenseId(), "tv", d.text(), null, null, null, null), "server-api").text();
        Dev e = dev();
        String burnedTrial = trialToken(BURNED, e, System.currentTimeMillis() - 3_600_000L);
        String burnedJournal = new JournalBuilder(BURNED, 1, "desk", clock.nowMs(), 1).entry(clock.nowMs(), "issue", JournalBuilder.issueFields(burnedTrial, null)).build();
        assertEquals(200, uploadJournal(burnedJournal).getResponse().getStatus());
        issuanceTap.runOnce();
        String seat = jdbc.queryForObject("select seat_id from lic_seat s join lic_license l on l.id = s.license_pk where l.license_id = ?", String.class, lic.licenseId());

        licenses.releaseSeat(OWNER, lic.licenseId(), seat, "poste révoqué (test)");
        licenses.revokeKey(OWNER, kid(BURNED), "clé compromise (test)");
        auditTap.runOnce();
        assertEquals("REVOKED", jdbc.queryForObject("select state from act_key where fp = ?", String.class, sha256(token)));
        assertEquals("REVOKED", jdbc.queryForObject("select state from act_key where fp = ?", String.class, sha256(burnedTrial)));
        assertEquals(1, jdbc.queryForObject("select count(*) from act_event where type = 'REVOKED_SEAT' and license_id = ?", Integer.class, lic.licenseId()));
        assertEquals(1, jdbc.queryForObject("select count(*) from act_event where type = 'REVOKED_KEY' and kid = ?", Integer.class, kid(BURNED)));
        int ev = events();
        auditTap.runOnce();
        assertEquals(ev, events());
    }
}
