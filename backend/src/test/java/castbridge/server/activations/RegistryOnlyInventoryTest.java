package castbridge.server.activations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import castbridge.server.licenses.DeviceIdentity.Factor;
import castbridge.server.licenses.LedgerService;
import castbridge.server.licenses.RegistryEvent;
import castbridge.server.licenses.WireActivation;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
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

/**
 * Audit w23-01 M8. An emission known only by the signed registry (until the tools send journals, every emission of the desk and of the phone arrives this way) gets an inventory
 * row with a substitute fingerprint, so that the decision of the owner applies: a code never seen on a TV 48 h after its emission is EXPIRED_UNUSED. The row is merged into the
 * real one when a TV reports the token (no duplicate).
 */
class RegistryOnlyInventoryTest extends ActTestBase {
    @Autowired IssuanceTap issuanceTap;
    @Autowired LicenseAuditTap auditTap;
    @Autowired LedgerService ledger;
    @Autowired Reconciler reconciler;

    @BeforeEach
    void start() {
        resetModule();
        clock.set(Instant.now());
    }

    @AfterEach
    void stop() { clock.reset(); }

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
    void aRegistryOnlyEmissionIsInTheInventoryAndExpiresUnusedAfter48Hours() throws Exception {
        Dev d = dev();
        String license = "lic-regonly0001";
        long at = System.currentTimeMillis() - 3_600_000L;
        String nonce = rnd32().substring(0, 16);
        String seat = WireActivation.defaultSeat(license, d.fp());
        var events = List.of(
                event(DESK, "license", at, List.of("license=" + license, "seats=2", "maxTransfersPerYear=2"), Map.of()),
                event(DESK, "issue", at + 1, List.of("license=" + license, "seat=" + seat, "subject=tv", "kind=production", "nonce=" + nonce, "notAfter=" + (at + 48 * 3_600_000L), "k=" + d.k()), d.fp()));
        assertTrue(ledger.importLedger(OWNER, file(events), false, true).applied() >= 1);
        auditTap.runOnce();
        issuanceTap.runOnce();
        Map<String, Object> k = jdbc.queryForMap("select * from act_key where license_id = ?", license);
        assertEquals("EMISE", k.get("state"), "emitted, window open");
        assertEquals(nonce, k.get("nonce"));
        assertEquals("REGISTRY", k.get("form"));
        assertTrue(((String) k.get("flags")).contains(",declared_registry,"));
        // 49 h after the emission nobody has seen it
        clock.set(Instant.ofEpochMilli(at).plusSeconds(49 * 3600));
        reconciler.reconcileAll();
        assertEquals("EXPIRED_UNUSED", jdbc.queryForObject("select state from act_key where license_id = ?", String.class, license));
        // the API lists it
        assertTrue(mvc.perform(adminGet("/api/v1/admin/activations/activations?state=EXPIRED_UNUSED")).andReturn().getResponse().getContentAsString().contains(license));
    }

    @Test
    void theRowIsMergedWhenATvReportsTheRealToken() throws Exception {
        Dev d = dev();
        String license = "lic-regmerge001";
        long at = System.currentTimeMillis() - 3_600_000L;
        String nonce = rnd32().substring(0, 16);
        String seat = WireActivation.defaultSeat(license, d.fp());
        assertTrue(ledger.importLedger(OWNER, file(List.of(
                event(DESK, "license", at, List.of("license=" + license, "seats=2", "maxTransfersPerYear=2"), Map.of()),
                event(DESK, "issue", at + 1, List.of("license=" + license, "seat=" + seat, "subject=tv", "kind=production", "nonce=" + nonce, "notAfter=" + (at + 48 * 3_600_000L), "k=" + d.k()), d.fp()))), false, true).applied() >= 1);
        auditTap.runOnce();
        issuanceTap.runOnce();
        assertEquals(1, jdbc.queryForObject("select count(*) from act_key where license_id = ?", Integer.class, license));
        WireActivation.Fields f = new WireActivation.Fields("production", "tv", kid(DESK), at, nonce, at, at, at + 48 * 3_600_000L, license, seat, d.k(), d.fp(), List.of());
        String token = WireActivation.token(f, sign(DESK, WireActivation.payload(f)));
        clock.set(Instant.ofEpochMilli(at + 3_600_000L));
        assertEquals(200, report(install(), reportJson(d, clock.nowMs(), "PRODUCTION", List.of(token), null)).getResponse().getStatus());
        assertEquals(1, jdbc.queryForObject("select count(*) from act_key where license_id = ?", Integer.class, license), "the substitute row was merged, no duplicate");
        Map<String, Object> k = jdbc.queryForMap("select * from act_key where license_id = ?", license);
        assertEquals(sha256(token), k.get("fp"));
        assertEquals("ACTIVATED", k.get("state"));
        assertTrue(((String) k.get("flags")).contains(",declared_registry,"));
        assertFalse(((String) k.get("flags")).contains(",undeclared,"));
        issuanceTap.runOnce();
        assertEquals(1, jdbc.queryForObject("select count(*) from act_key where license_id = ?", Integer.class, license), "replaying the tap does not bring the substitute back");
    }
}
