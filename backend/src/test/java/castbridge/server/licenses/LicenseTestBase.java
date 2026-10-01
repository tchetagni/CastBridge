package castbridge.server.licenses;

import castbridge.server.ApiTestBase;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Licence module switched ON, with a server key, a TOTP key and two trusted offline-tool keys, all generated here at random
 * (nothing secret in the repository, no real client data). Each test class gets a fresh database.
 */
public abstract class LicenseTestBase extends ApiTestBase {
    static final SecureRandom RND = new SecureRandom();
    static final Ed25519PrivateKeyParameters DESKTOP = key();
    static final Ed25519PrivateKeyParameters PHONE = key();
    static final Ed25519PrivateKeyParameters STRANGER = key();

    static Ed25519PrivateKeyParameters key() {
        byte[] seed = new byte[32];
        RND.nextBytes(seed);
        return new Ed25519PrivateKeyParameters(seed, 0);
    }

    @DynamicPropertySource
    static void licenses(DynamicPropertyRegistry r) {
        try {
            Path secrets = Files.createTempDirectory("cb-secrets");
            byte[] seed = new byte[32];
            RND.nextBytes(seed);
            Files.writeString(secrets.resolve("license-signing.key"), Base64.getEncoder().encodeToString(seed));
            r.add("castbridge.licenses.enabled", () -> "true");
            r.add("castbridge.licenses.public-routes", () -> "true");
            r.add("castbridge.licenses.secrets-dir", secrets::toString);
            byte[] totp = new byte[32];
            RND.nextBytes(totp);
            r.add("castbridge.licenses.totp-key", () -> Base64.getEncoder().encodeToString(totp));
            r.add("castbridge.licenses.ledger-keys", () -> pub("desktop", DESKTOP) + "," + pub("phone", PHONE));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String pub(String tool, Ed25519PrivateKeyParameters k) {
        byte[] p = k.generatePublicKey().getEncoded();
        return LicenseKeyring.kidOf(p) + ":" + tool + ":" + Base64.getEncoder().encodeToString(p);
    }

    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected LicenseService licenses;
    @Autowired protected ActivationService activations;
    @Autowired protected ClientService clients;
    @Autowired protected ProductService products;
    @Autowired protected AuditLog audit;
    @Autowired protected LedgerService ledger;
    @Autowired protected AbuseService abuse;
    @Autowired protected LicenseAccounts accounts;

    protected static final Actor OWNER = new Actor("owner-test", Role.OWNER, "api", true);
    protected static final Actor SUPPORT = new Actor("support-test", Role.SUPPORT, "web", true);
    protected static final Actor READONLY = new Actor("read-test", Role.READONLY, "web", true);

    private static final AtomicInteger SEQ = new AtomicInteger();

    /** A valid, unique device code. */
    protected static String code() {
        String s = String.format("%016X", 0x1000_0000_0000_0000L + SEQ.incrementAndGet() * 7919L + (RND.nextInt(1 << 20)));
        s = s.replace('O', '0').replace('I', '1');
        return s.substring(0, 4) + "-" + s.substring(4, 8) + "-" + s.substring(8, 12) + "-" + s.substring(12, 16);
    }

    protected ClientService.ClientRow client() { return clients.create(OWNER, "Client de test " + SEQ.incrementAndGet(), "test@example.invalid", null); }

    protected LicenseService.LicenseRow license(int seats, Instant end) {
        return licenses.create(OWNER, new LicenseService.NewLicense(null, client().id(), "PAID", seats, null, end, null, null, List.of()));
    }

    protected LicenseService.LicenseRow license(int seats) { return license(seats, Instant.now().plusSeconds(86400L * 365)); }

    protected ActivationService.Activation issue(String licenseId, String device) {
        return activations.issue(OWNER, new ActivationService.IssueRequest(licenseId, device, null, null, null), "server-api");
    }

    // ------------------------------------------------------------------ ledger files

    protected ObjectNode issuanceEntry(String licenseId, String device, String kind, String nonce, String fp, Instant at) {
        ObjectNode e = json.createObjectNode();
        e.put("t", "issuance");
        e.put("licenseId", licenseId);
        e.put("deviceCode", device);
        e.put("kind", kind);
        e.put("kid", "0123456789abcdef");
        e.put("nonce", nonce);
        e.put("issuedAt", at.toString());
        e.putNull("expiresAt");
        e.put("fingerprint", fp);
        return e;
    }

    protected ObjectNode transferEntry(String licenseId, String from, String to, Instant at) {
        ObjectNode e = json.createObjectNode();
        e.put("t", "transfer");
        e.put("licenseId", licenseId);
        e.put("from", from);
        e.put("to", to);
        e.put("signedBy", "desktop");
        e.put("at", at.toString());
        return e;
    }

    protected static String hex(int n) { return HexFormat.of().formatHex(java.util.Arrays.copyOf(Hashing.sha256(("n" + n + RND.nextLong()).getBytes()), 16)); }

    protected static String fp(String s) { return Hashing.sha256Hex(s + RND.nextLong()); }

    protected byte[] ledgerFile(String tool, Ed25519PrivateKeyParameters signer, String toolInPayload, List<ObjectNode> entries) throws IOException {
        ObjectNode payload = json.createObjectNode();
        payload.put("format", LedgerService.FORMAT);
        payload.put("tool", toolInPayload);
        payload.put("exportedAt", Instant.now().toString());
        ArrayNode arr = payload.putArray("entries");
        entries.forEach(arr::add);
        byte[] bytes = json.writeValueAsBytes(payload);
        Ed25519Signer s = new Ed25519Signer();
        s.init(true, signer);
        s.update(bytes, 0, bytes.length);
        ObjectNode env = json.createObjectNode();
        env.put("v", 1);
        env.put("kid", LicenseKeyring.kidOf(signer.generatePublicKey().getEncoded()));
        env.put("tool", tool);
        env.put("payload", Base64.getEncoder().encodeToString(bytes));
        env.put("sig", Base64.getEncoder().encodeToString(s.generateSignature()));
        return json.writeValueAsBytes(env);
    }

    @BeforeEach
    void clean() {
        // each test creates its own licences and clients: nothing to reset, rows are independent
    }
}
