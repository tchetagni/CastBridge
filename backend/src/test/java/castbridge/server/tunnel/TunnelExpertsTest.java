package castbridge.server.tunnel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import castbridge.server.licenses.LicenseKeyring;
import castbridge.server.licenses.LicenseTestAccess;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/** The owner's signed experts list: relayed as is, verified (REGISTRY scope, not older, not revoked) before it opens the server's sshd to the experts, one permitopen per enrolled port. */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TunnelExpertsTest extends TunnelTestBase {
    record Ex(String id, String key, long notAfter) {}

    /** The file as the owner's tool writes it. */
    String list(Ed25519PrivateKeyParameters signer, long generatedAt, List<Ex> experts) {
        TreeMap<String, String> m = new TreeMap<>();
        experts.forEach(e -> m.put(e.id(), e.key() + "|" + e.notAfter()));
        Ed25519Signer s = new Ed25519Signer();
        s.init(true, signer);
        byte[] b = ExpertsService.signedText(generatedAt, m).getBytes(StandardCharsets.UTF_8);
        s.update(b, 0, b.length);
        String arr = experts.stream().map(e -> "{\"id\":\"" + e.id() + "\",\"publicKey\":\"" + e.key() + " " + e.id() + "@pc\",\"notAfter\":" + e.notAfter() + "}").collect(Collectors.joining(","));
        // the publicKey is signed as written in the file (comment included)
        TreeMap<String, String> withComment = new TreeMap<>();
        experts.forEach(e -> withComment.put(e.id(), e.key() + " " + e.id() + "@pc|" + e.notAfter()));
        Ed25519Signer s2 = new Ed25519Signer();
        s2.init(true, signer);
        byte[] b2 = ExpertsService.signedText(generatedAt, withComment).getBytes(StandardCharsets.UTF_8);
        s2.update(b2, 0, b2.length);
        return "{\"generatedAt\":" + generatedAt + ",\"keyId\":\"" + LicenseKeyring.kidOf(signer.generatePublicKey().getEncoded()) + "\",\"experts\":[" + arr + "],\"signature\":\""
                + Base64.getEncoder().encodeToString(s2.generateSignature()) + "\"}";
    }

    void publish(String content) throws Exception {
        Files.createDirectories(tunnel.expertsFile().getParent());
        Files.writeString(tunnel.expertsFile(), content);
    }

    @Test @Order(1) void absentIs404AndNothingOpensForExperts() throws Exception {
        Files.deleteIfExists(tunnel.expertsFile());
        mvc.perform(get("/api/v1/tunnel/experts")).andExpect(status().isNotFound());
        assertEquals(ExpertsService.Outcome.ABSENT, experts.refresh().outcome());
        assertEquals(List.of(), lines(expertsFile()), "no expert at all before a signed list is accepted");
    }

    @Test @Order(2) void signedListIsAcceptedAndOpensOnePortPerTv() throws Exception {
        Tv a = enrollNewTv("trial"), b = enrollNewTv("production");
        String alice = sshKey(), bob = sshKey();
        long now = System.currentTimeMillis();
        publish(list(LicenseTestAccess.desktop(), now, List.of(new Ex("bob", bob, 0), new Ex("alice", alice, now + DAY))));
        var r = experts.refresh();
        assertEquals(ExpertsService.Outcome.ACCEPTED, r.outcome(), r.message());
        List<String> l = lines(expertsFile());
        assertEquals(2, l.size());
        assertEquals("restrict,port-forwarding,permitopen=\"127.0.0.1:" + a.port() + "\",permitopen=\"127.0.0.1:" + b.port() + "\" " + alice + " expert-alice", l.get(0));
        assertTrue(l.get(1).endsWith(" " + bob + " expert-bob"));
        // relayed as is (the signature is for whoever uses it)
        String served = mvc.perform(get("/api/v1/tunnel/experts")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(served.contains("\"signature\""));

        // a TV enrolled later is added to the experts' lines; a revoked one is removed
        Tv c = enrollNewTv("production");
        assertTrue(lines(expertsFile()).get(0).contains("permitopen=\"127.0.0.1:" + c.port() + "\""));
        tunnel.revoke(a.code(), "tester");
        assertFalse(lines(expertsFile()).get(0).contains(":" + a.port() + "\""));
        assertTrue(lines(expertsFile()).get(0).contains(":" + b.port() + "\""));
    }

    @Test @Order(3) void unsignedWrongKeyAndOlderListsAreRefusedAndKeepTheLastGoodOne() throws Exception {
        String before = expertsFile();
        long now = System.currentTimeMillis();
        String evil = sshKey();
        // bad signature (content changed after signing)
        publish(list(LicenseTestAccess.desktop(), now + 10, List.of(new Ex("eve", evil, 0))).replace("eve@pc", "mallory@pc"));
        assertEquals(ExpertsService.Outcome.REJECTED, experts.refresh().outcome());
        // signed by a key that is not trusted
        publish(list(LicenseTestAccess.stranger(), now + 10, List.of(new Ex("eve", evil, 0))));
        assertEquals(ExpertsService.Outcome.REJECTED, experts.refresh().outcome());
        // trusted key WITHOUT the REGISTRY scope
        publish(list(LicenseTestAccess.phone(), now + 10, List.of(new Ex("eve", evil, 0))));
        var noScope = experts.refresh();
        assertEquals(ExpertsService.Outcome.REJECTED, noScope.outcome());
        assertTrue(noScope.message().contains("registre"), noScope.message());
        // unsigned
        publish("{\"generatedAt\":" + (now + 10) + ",\"keyId\":\"" + LicenseKeyring.kidOf(LicenseTestAccess.desktop().generatePublicKey().getEncoded()) + "\",\"experts\":[{\"id\":\"eve\",\"publicKey\":\"" + evil + "\",\"notAfter\":0}]}");
        assertEquals(ExpertsService.Outcome.REJECTED, experts.refresh().outcome());
        // older than the accepted one
        long accepted = Long.parseLong(jdbc.queryForObject("select v from tunnel_meta where k = 'experts_generated_at'", String.class));
        publish(list(LicenseTestAccess.desktop(), accepted - 1, List.of(new Ex("eve", evil, 0))));
        var older = experts.refresh();
        assertEquals(ExpertsService.Outcome.REJECTED, older.outcome());
        assertTrue(older.message().contains("plus ancienne"), older.message());
        // not an ssh key / injected option / duplicate id
        publish(list(LicenseTestAccess.desktop(), now + 20, List.of(new Ex("eve", "ssh-ed25519 AAAA\\nrestrict", 0))));
        assertEquals(ExpertsService.Outcome.REJECTED, experts.refresh().outcome());
        publish(list(LicenseTestAccess.desktop(), now + 20, List.of(new Ex("eve", evil, 0), new Ex("eve", evil, 0))));
        assertEquals(ExpertsService.Outcome.REJECTED, experts.refresh().outcome());
        publish("pas du json");
        assertEquals(ExpertsService.Outcome.REJECTED, experts.refresh().outcome());

        assertFalse(expertsFile().contains(evil));
        assertEquals(before.lines().filter(x -> x.contains("expert-alice")).count(), expertsFile().lines().filter(x -> x.contains("expert-alice")).count(), "the last accepted list still applies");
        assertTrue(experts.status().text().startsWith("Refusée"));
    }

    @Test @Order(4) void revokedSigningKeyAndExpiredExpert() throws Exception {
        long now = System.currentTimeMillis();
        String carol = sshKey(), old = sshKey();
        // an expert whose date has passed is not let in
        publish(list(LicenseTestAccess.desktop(), now + 100, List.of(new Ex("carol", carol, 0), new Ex("old", old, now - 1000))));
        assertEquals(ExpertsService.Outcome.ACCEPTED, experts.refresh().outcome());
        String f = expertsFile();
        assertTrue(f.contains("expert-carol"));
        assertFalse(f.contains("expert-old"), "expired expert");
        // the same list again is accepted (idempotent), a revoked signing key is not
        assertEquals(ExpertsService.Outcome.UNCHANGED, experts.refresh().outcome());
        String dave = sshKey();
        publish(list(LicenseTestAccess.spare(), now + 200, List.of(new Ex("dave", dave, 0))));
        assertEquals(ExpertsService.Outcome.ACCEPTED, experts.refresh().outcome());
        jdbc.update("insert into lic_revocation (kid, reason, revoked_by, revoked_at) values (?,?,?,?)", LicenseKeyring.kidOf(LicenseTestAccess.spare().generatePublicKey().getEncoded()), "t", "t", Timestamp.from(Instant.now()));
        publish(list(LicenseTestAccess.spare(), now + 300, List.of(new Ex("erin", sshKey(), 0))));
        var r = experts.refresh();
        assertEquals(ExpertsService.Outcome.REJECTED, r.outcome());
        assertTrue(r.message().contains("révoquée"), r.message());
        assertFalse(expertsFile().contains("expert-erin"));
    }
}
