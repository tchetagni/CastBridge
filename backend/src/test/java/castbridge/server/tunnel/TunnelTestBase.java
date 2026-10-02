package castbridge.server.tunnel;

import castbridge.server.licenses.LicenseKeyring;
import castbridge.server.licenses.LicenseTestBase;
import castbridge.server.licenses.WireActivation;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Tunnel module ON, files in a temporary folder, throwaway keys only (nothing real: no key, no activation, no device of a client). */
public abstract class TunnelTestBase extends LicenseTestBase {
    static final long DAY = 86_400_000L;
    static final SecureRandom R = new SecureRandom();

    static Path tmp() {
        try {
            return Files.createTempDirectory("cb-tunnel");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void tunnelProps(DynamicPropertyRegistry r) {
        Path dir = tmp(); // one folder per test class (= per Spring context)
        r.add("castbridge.tunnel.enabled", () -> "true");
        r.add("castbridge.tunnel.authorized-keys-file", () -> dir.resolve("authorized_keys").toString());
        r.add("castbridge.tunnel.experts-file", () -> dir.resolve("experts.json").toString());
        r.add("castbridge.tunnel.experts-authorized-keys-file", () -> dir.resolve("experts_authorized_keys").toString());
    }

    @Autowired protected TunnelService tunnel;
    @Autowired protected ExpertsService experts;

    /** A valid, random ssh-ed25519 public key (blob = type + 32 bytes). */
    static String sshKey() {
        byte[] k = new byte[32];
        R.nextBytes(k);
        ByteBuffer b = ByteBuffer.allocate(4 + 11 + 4 + 32);
        b.putInt(11).put("ssh-ed25519".getBytes(StandardCharsets.US_ASCII)).putInt(32).put(k);
        return "ssh-ed25519 " + Base64.getEncoder().encodeToString(b.array());
    }

    /** A signed activation token for a TV (kind trial|production, subject tv|phone) signed by any key. */
    protected static String activation(Ed25519PrivateKeyParameters signer, String kind, String subject, Dev d, String license) {
        long now = System.currentTimeMillis();
        List<String> rights = kind.equals("trial") ? List.of("usage|duree|" + now + "|" + (now + 30 * DAY)) : List.of();
        var f = new WireActivation.Fields(kind, subject, LicenseKeyring.kidOf(signer.generatePublicKey().getEncoded()), 1, "aabbccdd" + Long.toHexString(R.nextLong() & 0xffffffffL), now, now,
                now + 48 * 3_600_000L, license, WireActivation.defaultSeat(license, d.fp()), 4, d.fp(), rights);
        Ed25519Signer s = new Ed25519Signer();
        s.init(true, signer);
        byte[] m = WireActivation.payload(f).getBytes(StandardCharsets.UTF_8);
        s.update(m, 0, m.length);
        return WireActivation.token(f, s.generateSignature());
    }

    protected static String enrollBody(String activation, String key, String code) {
        return "{\"activation\":\"" + activation + "\",\"sshPublicKey\":\"" + key + "\",\"deviceCode\":\"" + code + "\"}";
    }

    /** Enrols and returns the JSON answer, asserting the HTTP status. */
    protected com.fasterxml.jackson.databind.JsonNode enrolled(String body, String ip, int expected) throws Exception {
        MvcResult res = mvc.perform(MockMvcRequestBuilders.post("/api/v1/tunnel/enroll").contentType(MediaType.APPLICATION_JSON).content(body).with(r -> {
            r.setRemoteAddr(ip);
            return r;
        })).andReturn();
        org.junit.jupiter.api.Assertions.assertEquals(expected, res.getResponse().getStatus(), res.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return json.readTree(res.getResponse().getContentAsByteArray());
    }

    /** Production activation (signed by the desktop key) of a fresh device, enrolled with a fresh ssh key; returns {code, key, port}. */
    protected record Tv(Dev dev, String key, String activation, int port) {
        String code() { return dev.code(); }
    }

    protected Tv enrollNewTv(String kind) throws Exception {
        Dev d = dev();
        String key = sshKey();
        String act = activation(castbridge.server.licenses.LicenseTestAccess.desktop(), kind, "tv", d, "lic-" + Long.toHexString(R.nextLong() & 0xffffff));
        var n = enrolled(enrollBody(act, key, d.code()), "10.0.0." + (1 + R.nextInt(200)), 200);
        return new Tv(d, key, act, n.get("port").asInt());
    }

    protected String tvFile() throws IOException { return Files.readString(tunnel.authorizedKeysFile()); }

    protected String expertsFile() throws IOException { return Files.readString(tunnel.expertsAuthorizedKeysFile()); }

    protected static List<String> lines(String file) { return file.lines().filter(l -> !l.startsWith("#") && !l.isBlank()).toList(); }
}
