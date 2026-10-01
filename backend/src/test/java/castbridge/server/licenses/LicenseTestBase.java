package castbridge.server.licenses;

import castbridge.server.ApiTestBase;
import castbridge.server.licenses.ActivationSigner.SignerScope;
import castbridge.server.licenses.DeviceIdentity.Factor;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Licence module switched ON, with a server key, a TOTP key and two trusted offline-tool keys (desktop: every scope; owner phone: every scope but
 * REGISTRY, like docs/ACTIVATION-FORMAT.md recommends), all generated here at random (nothing secret in the repository, no real client data).
 * Each test class gets a fresh database.
 */
public abstract class LicenseTestBase extends ApiTestBase {
    static final SecureRandom RND = new SecureRandom();
    static final Ed25519PrivateKeyParameters DESKTOP = key();
    static final Ed25519PrivateKeyParameters PHONE = key();
    static final Ed25519PrivateKeyParameters STRANGER = key();
    /** A trusted key kept for the tests that revoke a key (a revocation is global and permanent in the shared test database). */
    static final Ed25519PrivateKeyParameters SPARE = key();
    static final byte[] SERVER_SEED = new byte[32];

    static {
        RND.nextBytes(SERVER_SEED);
    }

    static final List<SignerScope> ALL_SCOPES = List.of(SignerScope.values());
    static final List<SignerScope> PHONE_SCOPES = ALL_SCOPES.stream().filter(s -> s != SignerScope.REGISTRY).toList();

    static Ed25519PrivateKeyParameters key() {
        byte[] seed = new byte[32];
        RND.nextBytes(seed);
        return new Ed25519PrivateKeyParameters(seed, 0);
    }

    @DynamicPropertySource
    static void licenses(DynamicPropertyRegistry r) {
        try {
            Path secrets = Files.createTempDirectory("cb-secrets");
            Files.writeString(secrets.resolve("license-signing.key"), Base64.getEncoder().encodeToString(SERVER_SEED));
            r.add("castbridge.licenses.enabled", () -> "true");
            r.add("castbridge.licenses.public-routes", () -> "true");
            r.add("castbridge.licenses.secrets-dir", secrets::toString);
            byte[] totp = new byte[32];
            RND.nextBytes(totp);
            r.add("castbridge.licenses.totp-key", () -> Base64.getEncoder().encodeToString(totp));
            r.add("castbridge.licenses.trusted-keys", () -> trusted("desktop", DESKTOP, ALL_SCOPES) + "," + trusted("phone", PHONE, PHONE_SCOPES) + "," + trusted("spare", SPARE, ALL_SCOPES) + vectorKeys());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The test keys of tools/activation/test-vectors.json as extra trusted keys (names v-desk, v-phone, v-server, v-support; "rogue" stays unknown). */
    static String vectorKeys() {
        try {
            var vectors = new com.fasterxml.jackson.databind.ObjectMapper().readTree(Files.readString(Path.of("..", "tools", "activation", "test-vectors.json")));
            StringBuilder sb = new StringBuilder();
            for (var k : vectors.get("keys")) {
                String name = k.get("name").asText();
                if (name.equals("rogue")) continue;
                List<String> scopes = new ArrayList<>();
                k.get("scopes").forEach(x -> scopes.add(x.asText()));
                sb.append(",v-").append(name).append(':').append(k.get("publicKey").asText()).append(':').append(String.join("+", scopes));
            }
            return sb.toString();
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }

    static String trusted(String name, Ed25519PrivateKeyParameters k, List<SignerScope> scopes) {
        byte[] p = k.generatePublicKey().getEncoded();
        return name + ":" + Base64.getEncoder().encodeToString(p) + ":" + String.join("+", scopes.stream().map(Enum::name).toList());
    }

    static String kid(Ed25519PrivateKeyParameters k) { return LicenseKeyring.kidOf(k.generatePublicKey().getEncoded()); }

    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected LicenseService licenses;
    @Autowired protected ActivationService activations;
    @Autowired protected ClientService clients;
    @Autowired protected ProductService products;
    @Autowired protected AuditLog audit;
    @Autowired protected LedgerService ledger;
    @Autowired protected AbuseService abuse;
    @Autowired protected LicenseAccounts accounts;
    @Autowired protected LicenseKeyring keyring;
    @Autowired protected RegistryStore registry;

    protected static final Actor OWNER = new Actor("owner-test", Role.OWNER, "api", true);
    protected static final Actor SUPPORT = new Actor("support-test", Role.SUPPORT, "web", true);
    protected static final Actor READONLY = new Actor("read-test", Role.READONLY, "web", true);

    private static final AtomicInteger SEQ = new AtomicInteger();

    // ------------------------------------------------------------------ devices

    /** A device with random factor fingerprints (never raw hardware values), its code and its "demande d'appareil" text. */
    public record Dev(Map<Factor, String> fp) {
        public Dev {
            fp = new EnumMap<>(fp);
        }

        public String code() { return DeviceIdentity.code(fp); }

        public String text() {
            List<String> l = new ArrayList<>(List.of("code=" + code(), "k=" + DeviceIdentity.kFor(fp.size())));
            fp.forEach((f, h) -> l.add("factor=" + f.name() + "|" + h));
            return String.join("\n", l);
        }

        public DeviceIdentity.Request request() { return DeviceIdentity.parseRequest(text()); }

        /** The same hardware with one module replaced (still 4 of 5 factors in common). */
        public Dev withModuleChanged(Factor f) {
            Map<Factor, String> m = new EnumMap<>(fp);
            m.put(f, rnd32());
            return new Dev(m);
        }
    }

    static String rnd32() {
        byte[] b = new byte[16];
        RND.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    protected static Dev dev() {
        Map<Factor, String> m = new EnumMap<>(Factor.class);
        for (Factor f : Factor.values()) m.put(f, rnd32());
        return new Dev(m);
    }

    // ------------------------------------------------------------------ licences

    protected ClientService.ClientRow client() { return clients.create(OWNER, "Client de test " + SEQ.incrementAndGet(), "test@example.invalid", null); }

    /** A paid licence with an à-la-carte product (rights to issue), ending in a year. */
    protected LicenseService.LicenseRow license(int seats, Instant end) {
        ensureProducts();
        return licenses.create(OWNER, new LicenseService.NewLicense(null, client().id(), "PAID", seats, null, end, null, null, List.of("p-test")));
    }

    protected LicenseService.LicenseRow license(int seats) { return license(seats, Instant.now().plusSeconds(86400L * 365)); }

    protected void ensureProducts() {
        try {
            products.get("p-test");
        } catch (castbridge.server.web.ApiException e) {
            products.create(OWNER, new ProductService.NewProduct("p-test", "Produit de test", "A_LA_CARTE", null, List.of("learn/test"), null, null, List.of("classe-test")));
        }
    }

    protected ActivationService.Activation issue(String licenseId, Dev d) {
        return activations.issue(OWNER, new ActivationService.IssueRequest(licenseId, "tv", d.text(), null, null, null), "server-api");
    }

    // ------------------------------------------------------------------ registry files (docs/ACTIVATION-FORMAT.md § 8-9)

    /** Signs a registry event with a test key: the text is built exactly as the tools do. */
    protected static RegistryEvent event(Ed25519PrivateKeyParameters signer, String type, long at, List<String> fields, Map<Factor, String> factors) {
        String kid = kid(signer);
        List<String> lines = new ArrayList<>(List.of(RegistryEvent.FORMAT, "type=" + type, "kid=" + kid, "at=" + at));
        lines.addAll(fields);
        Map<Factor, String> sorted = new EnumMap<>(Factor.class);
        sorted.putAll(factors);
        sorted.forEach((f, h) -> lines.add("factor=" + f.name() + "|" + h));
        String text = String.join("\n", lines);
        Ed25519Signer s = new Ed25519Signer();
        s.init(true, signer);
        byte[] b = text.getBytes(StandardCharsets.UTF_8);
        s.update(b, 0, b.length);
        return new RegistryEvent(kid, text, Base64.getEncoder().encodeToString(s.generateSignature()));
    }

    protected static RegistryEvent licenseEvent(Ed25519PrivateKeyParameters k, long at, String license, int seats, int cap) {
        return event(k, "license", at, List.of("license=" + license, "seats=" + seats, "maxTransfersPerYear=" + cap), Map.of());
    }

    protected static RegistryEvent issueEvent(Ed25519PrivateKeyParameters k, long at, String license, String seat, String subject, String kind, Dev d, String nonce) {
        return event(k, "issue", at, List.of("license=" + license, "seat=" + seat, "subject=" + subject, "kind=" + kind, "nonce=" + nonce, "notAfter=" + (at + 30 * 86_400_000L),
                "k=" + DeviceIdentity.kFor(d.fp().size())), d.fp());
    }

    protected static RegistryEvent transferEvent(Ed25519PrivateKeyParameters k, long at, String license, String seat, Dev to, String nonce) {
        return event(k, "transfer", at, List.of("license=" + license, "seat=" + seat, "k=" + DeviceIdentity.kFor(to.fp().size()), "nonce=" + nonce), to.fp());
    }

    protected static RegistryEvent revokeSeatEvent(Ed25519PrivateKeyParameters k, long at, String license, String seat) {
        return event(k, "revoke", at, List.of("target=seat", "value=" + license + "|" + seat), Map.of());
    }

    protected static RegistryEvent revokeKeyEvent(Ed25519PrivateKeyParameters k, long at, String kidToRevoke) {
        return event(k, "revoke", at, List.of("target=key", "value=" + kidToRevoke), Map.of());
    }

    protected static Ed25519PrivateKeyParameters serverKey() { return new Ed25519PrivateKeyParameters(SERVER_SEED, 0); }

    protected byte[] registryFile(List<RegistryEvent> events) throws IOException {
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

    protected LedgerService.ImportReport importAuto(List<RegistryEvent> events) throws IOException { return ledger.importLedger(OWNER, registryFile(events), false, true); }

    protected LedgerService.ImportReport importReview(List<RegistryEvent> events) throws IOException { return ledger.importLedger(OWNER, registryFile(events), false, false); }

    protected static long ms(Instant i) { return i.toEpochMilli(); }

    protected static String nonce() { return rnd32(); }

    /** The seat id of a device in a licence (default derivation). */
    protected static String seatOf(String wireLicense, Dev d) { return WireActivation.defaultSeat(wireLicense, d.fp()); }
}
