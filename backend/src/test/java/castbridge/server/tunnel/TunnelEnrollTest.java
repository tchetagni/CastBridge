package castbridge.server.tunnel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import castbridge.server.licenses.LicenseKeyring;
import castbridge.server.licenses.LicenseTestAccess;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** POST /api/v1/tunnel/enroll: the activation is verified like the TV does, one stable port per device, the authorized_keys line is exact, revocation, probe. */
class TunnelEnrollTest extends TunnelTestBase {

    @Test void trialAndProductionAreEnrolledWithExactLines() throws Exception {
        Tv a = enrollNewTv("trial");
        Tv b = enrollNewTv("production");
        assertNotEquals(a.port(), b.port());
        assertTrue(a.port() >= 22100 && a.port() <= 22999);
        String file = tvFile();
        assertTrue(lines(file).contains("restrict,port-forwarding,permitlisten=\"127.0.0.1:" + a.port() + "\",command=\"/bin/false\" " + a.key() + " tv-" + a.code()), file);
        assertTrue(lines(file).contains("restrict,port-forwarding,permitlisten=\"127.0.0.1:" + b.port() + "\",command=\"/bin/false\" " + b.key() + " tv-" + b.code()), file);
        assertEquals("trial", jdbc.queryForObject("select edition from tunnel_device where device_code = ?", String.class, a.code()));
        assertEquals("production", jdbc.queryForObject("select edition from tunnel_device where device_code = ?", String.class, b.code()));
    }

    @Test void answerShapeAndNoStore() throws Exception {
        Dev d = dev();
        String key = sshKey();
        var res = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/tunnel/enroll").contentType("application/json")
                .content(enrollBody(activation(LicenseTestAccess.desktop(), "production", "tv", d, "lic-shape"), key + " tv@box", d.code().toLowerCase()))).andReturn().getResponse();
        assertEquals(200, res.getStatus(), res.getContentAsString());
        JsonNode n = json.readTree(res.getContentAsByteArray());
        assertEquals("bridge.sti-cm.com", n.get("host").asText());
        assertEquals(2200, n.get("sshPort").asInt());
        assertEquals("cbtunnel", n.get("user").asText());
        assertFalse(n.has("hostKeyFingerprint"), "empty setting = omitted");
        assertTrue(res.getHeader("Cache-Control").contains("no-store"));
        // the comment of the caller never reaches the file
        assertFalse(tvFile().contains("tv@box"));
    }

    @Test void refusals() throws Exception {
        Dev d = dev(), other = dev();
        String key = sshKey();
        String good = activation(LicenseTestAccess.desktop(), "production", "tv", d, "lic-refus");
        // the device code must be the activation's target
        JsonNode e = enrolled(enrollBody(good, key, other.code()), "10.1.0.1", 403);
        assertTrue(e.get("message").asText().contains("ne correspond pas"), e.toString());
        // badly formed code, key, activation
        enrolled(enrollBody(good, key, "ABCD-EFGH-JKMN-PQRS"), "10.1.0.1", 400);
        enrolled(enrollBody(good, "ssh-rsa AAAAB3NzaC1yc2E", d.code()), "10.1.0.1", 400);
        enrolled(enrollBody(good, key + "\\nrestrict ssh-ed25519 AAAA", d.code()), "10.1.0.1", 400);
        enrolled(enrollBody("cbx1.abc.def", key, d.code()), "10.1.0.1", 403);
        enrolled("{\"activation\":\"x\"}", "10.1.0.1", 400);
        // unknown signing key, key without the right scope on a phone activation, a phone activation, a tampered one
        enrolled(enrollBody(activation(LicenseTestAccess.stranger(), "production", "tv", d, "lic-refus"), key, d.code()), "10.1.0.1", 403);
        enrolled(enrollBody(activation(LicenseTestAccess.desktop(), "production", "phone", d, "lic-refus"), key, d.code()), "10.1.0.1", 403);
        String[] p = good.split("\\.");
        enrolled(enrollBody(p[0] + "." + p[1] + "." + Boolean.toString(true), key, d.code()), "10.1.0.1", 403);
        assertEquals(0, jdbc.queryForObject("select count(*) from tunnel_device where device_code = ?", Integer.class, d.code()));
        assertFalse(tvFile().contains(key));
    }

    @Test void revokedSeatAndRevokedKeyAreRefused() throws Exception {
        Dev d = dev();
        String lic = "lic-revoque";
        String act = activation(LicenseTestAccess.desktop(), "production", "tv", d, lic);
        jdbc.update("insert into lic_revocation (license_id, seat_id, reason, revoked_by, revoked_at) values (?,?,?,?,?)", lic, castbridge.server.licenses.WireActivation.defaultSeat(lic, d.fp()),
                "test", "test", Timestamp.from(Instant.now().plusSeconds(5)));
        JsonNode e = enrolled(enrollBody(act, sshKey(), d.code()), "10.2.0.1", 403);
        assertTrue(e.get("message").asText().contains("poste révoqué"), e.toString());

        Dev d2 = dev();
        String act2 = activation(LicenseTestAccess.spare(), "production", "tv", d2, "lic-cle");
        enrolled(enrollBody(act2, sshKey(), d2.code()), "10.2.0.1", 200);
        jdbc.update("insert into lic_revocation (kid, reason, revoked_by, revoked_at) values (?,?,?,?)", LicenseKeyring.kidOf(LicenseTestAccess.spare().generatePublicKey().getEncoded()), "test", "test", Timestamp.from(Instant.now()));
        Dev d3 = dev();
        JsonNode e2 = enrolled(enrollBody(activation(LicenseTestAccess.spare(), "production", "tv", d3, "lic-cle"), sshKey(), d3.code()), "10.2.0.1", 403);
        assertTrue(e2.get("message").asText().contains("clé de signature révoquée"), e2.toString());
    }

    @Test void samePortWhenRepeatedAndKeyRotationNeedsAValidActivation() throws Exception {
        Tv a = enrollNewTv("production");
        int before = jdbc.queryForObject("select count(*) from tunnel_device", Integer.class);
        // idempotent: same device, same key
        JsonNode again = enrolled(enrollBody(a.activation(), a.key(), a.code()), "10.3.0.1", 200);
        assertEquals(a.port(), again.get("port").asInt());
        assertEquals(before, jdbc.queryForObject("select count(*) from tunnel_device", Integer.class));
        // rotation: new key, valid activation -> same port, only the new key remains
        String newKey = sshKey();
        JsonNode rot = enrolled(enrollBody(a.activation(), newKey, a.code()), "10.3.0.1", 200);
        assertEquals(a.port(), rot.get("port").asInt());
        String f = tvFile();
        assertTrue(f.contains(newKey + " tv-" + a.code()));
        assertFalse(f.contains(a.key()));
        assertEquals(1, lines(f).stream().filter(l -> l.endsWith("tv-" + a.code())).count());
        assertEquals(1, jdbc.queryForObject("select count(*) from tunnel_audit where event = 'KEY_ROTATED' and device_code = ?", Integer.class, a.code()));
        // the same new key with a BAD activation changes nothing
        enrolled(enrollBody(activation(LicenseTestAccess.stranger(), "production", "tv", a.dev(), "lic-x"), sshKey(), a.code()), "10.3.0.1", 403);
        assertTrue(tvFile().contains(newKey));
    }

    @Test void adminRevocationKeepsThePortAndBlocksReEnrolment() throws Exception {
        Tv a = enrollNewTv("trial");
        tunnel.revoke(a.code(), "tester");
        assertFalse(tvFile().contains(a.key()));
        JsonNode e = enrolled(enrollBody(a.activation(), a.key(), a.code()), "10.4.0.1", 403);
        assertTrue(e.get("message").asText().contains("désactivé"), e.toString());
        // the port stays reserved: a new device never gets it
        Tv b = enrollNewTv("trial");
        assertNotEquals(a.port(), b.port());
        assertEquals(a.port(), jdbc.queryForObject("select port from tunnel_device where device_code = ?", Integer.class, a.code()));
        tunnel.restore(a.code(), "tester");
        assertTrue(tvFile().contains(a.key() + " tv-" + a.code()));
        assertEquals(a.port(), enrolled(enrollBody(a.activation(), a.key(), a.code()), "10.4.0.1", 200).get("port").asInt());
        assertEquals(1, jdbc.queryForObject("select count(*) from tunnel_audit where event = 'REVOKED' and device_code = ?", Integer.class, a.code()));
    }

    @Test void probeReportsConnectedThenOfflineAndKeepsLastSeen() throws Exception {
        Tv a = enrollNewTv("production");
        try (ServerSocket s = new ServerSocket(a.port(), 1, InetAddress.getLoopbackAddress())) {
            tunnel.probeAll();
            var v = tunnel.views().stream().filter(x -> x.deviceCode().equals(a.code())).findFirst().orElseThrow();
            assertTrue(v.online());
            assertTrue(v.lastSeen() != null);
        } catch (java.net.BindException e) {
            return; // port taken on this machine: nothing to assert
        }
        tunnel.probe(a.code());
        var v = tunnel.views().stream().filter(x -> x.deviceCode().equals(a.code())).findFirst().orElseThrow();
        assertFalse(v.online());
        assertTrue(v.lastSeen() != null, "last time seen online is kept");
        assertTrue(v.command().endsWith("-p " + a.port() + " tv@127.0.0.1"), v.command());
    }

    @Test void serverIssuedProductionActivationIsAccepted() throws Exception {
        var l = license(1);
        Dev d = dev();
        var act = issue(l.licenseId(), d);
        JsonNode n = enrolled(enrollBody(act.text(), sshKey(), d.code()), "10.5.0.1", 200);
        assertEquals("cbtunnel", n.get("user").asText());
        assertEquals("production", jdbc.queryForObject("select edition from tunnel_device where device_code = ?", String.class, d.code()));
    }

    @Test void startupLeavesNoTemporaryFile() throws Exception {
        enrollNewTv("trial");
        try (var st = Files.list(tunnel.authorizedKeysFile().getParent())) {
            assertTrue(st.noneMatch(p -> p.toString().endsWith(".tmp")));
        }
        assertEquals(List.of(), lines(expertsFile()).stream().filter(l -> !l.contains("expert-")).toList());
    }
}
