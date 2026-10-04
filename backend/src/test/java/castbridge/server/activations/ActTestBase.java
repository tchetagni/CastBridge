package castbridge.server.activations;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import castbridge.server.ApiTestBase;
import castbridge.server.licenses.ActivationService;
import castbridge.server.licenses.Actor;
import castbridge.server.licenses.ClientService;
import castbridge.server.licenses.DeviceIdentity;
import castbridge.server.licenses.DeviceIdentity.Factor;
import castbridge.server.licenses.LicenseKeyring;
import castbridge.server.licenses.LicenseService;
import castbridge.server.licenses.ProductService;
import castbridge.server.licenses.Role;
import castbridge.server.licenses.WireActivation;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Activation tracking module switched ON. Everything secret is generated here at random (nothing in the repository): the licence server key, the
 * secrets of the module (act-ref.key, act-audit.key, act-checkpoint.key) and the throwaway keys of the offline tools. One fresh database per class.
 */
public abstract class ActTestBase extends ApiTestBase {
    static final SecureRandom RND = new SecureRandom();
    static final Ed25519PrivateKeyParameters DESK = key();
    static final Ed25519PrivateKeyParameters PHONE = key();
    static final Ed25519PrivateKeyParameters SPARE = key();
    /** Burned by the tests that revoke a key (a revocation is permanent in the shared database). */
    static final Ed25519PrivateKeyParameters BURNED = key();
    static final Ed25519PrivateKeyParameters STRANGER = key();
    static final byte[] SERVER_SEED = new byte[32];
    static final byte[] CHECKPOINT_SEED = new byte[32];
    static final byte[] AUDIT_KEY = new byte[32];
    static Path SECRETS;
    static final String ALL = "ISSUE_TRIAL+ISSUE_PRODUCTION+COMMAND_SUPPORT+COMMAND_UNLOCK+COMMAND_OPEN_ALL+TRANSFER+REVOKE+REGISTRY+REACTIVATE+POLICY";

    static {
        RND.nextBytes(SERVER_SEED);
        RND.nextBytes(CHECKPOINT_SEED);
        RND.nextBytes(AUDIT_KEY);
    }

    static Ed25519PrivateKeyParameters key() {
        byte[] seed = new byte[32];
        RND.nextBytes(seed);
        return new Ed25519PrivateKeyParameters(seed, 0);
    }

    @DynamicPropertySource
    static void activations(DynamicPropertyRegistry r) {
        try {
            SECRETS = Files.createTempDirectory("cb-act-secrets");
            Files.writeString(SECRETS.resolve("license-signing.key"), Base64.getEncoder().encodeToString(SERVER_SEED));
            byte[] ref = new byte[32];
            RND.nextBytes(ref);
            Files.write(SECRETS.resolve("act-ref.key"), ref);
            Files.write(SECRETS.resolve("act-audit.key"), AUDIT_KEY);
            Files.writeString(SECRETS.resolve("act-checkpoint.key"), Base64.getEncoder().encodeToString(CHECKPOINT_SEED));
            Path archive = Files.createTempDirectory("cb-act-archive");
            r.add("castbridge.licenses.enabled", () -> "true");
            r.add("castbridge.licenses.public-routes", () -> "true");
            r.add("castbridge.licenses.secrets-dir", SECRETS::toString);
            byte[] totp = new byte[32];
            RND.nextBytes(totp);
            r.add("castbridge.licenses.totp-key", () -> Base64.getEncoder().encodeToString(totp));
            r.add("castbridge.licenses.trusted-keys", () -> trusted("desktop", DESK) + "," + trusted("phone", PHONE) + "," + trusted("spare", SPARE) + "," + trusted("burned", BURNED));
            r.add("castbridge.activations.enabled", () -> "true");
            r.add("castbridge.activations.archive-dir", archive::toString);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String trusted(String name, Ed25519PrivateKeyParameters k) {
        return name + ":" + Base64.getEncoder().encodeToString(k.generatePublicKey().getEncoded()) + ":" + ALL;
    }

    static String kid(Ed25519PrivateKeyParameters k) { return LicenseKeyring.kidOf(k.generatePublicKey().getEncoded()); }

    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected ActClock clock;
    @Autowired protected ActivationsPolicy policy;
    @Autowired protected LicenseService licenses;
    @Autowired protected ClientService clients;
    @Autowired protected ProductService products;
    @Autowired protected ActivationService activations;
    @Autowired protected LicenseKeyring keyring;

    protected static final Actor OWNER = new Actor("owner-test", Role.OWNER, "api", true);

    protected static final AtomicInteger SEQ = new AtomicInteger(1000);

    // ------------------------------------------------------------------ devices (hardware identity) and API installations

    public record Dev(Map<Factor, String> fp) {
        public Dev { fp = new EnumMap<>(fp); }

        public String code() { return DeviceIdentity.code(fp); }

        public String text() {
            List<String> l = new ArrayList<>(List.of("code=" + code(), "k=" + DeviceIdentity.kFor(fp.size())));
            fp.forEach((f, h) -> l.add("factor=" + f.name() + "|" + h));
            return String.join("\n", l);
        }

        public int k() { return DeviceIdentity.kFor(fp.size()); }
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

    /** An installed CastBridge-TV known to the API: its public id, numeric id and bearer token. */
    public record Install(String publicId, long id, String token) {}

    protected Install install() throws Exception { return install(null); }

    protected Install install(String androidIdHash) throws Exception {
        String id = UUID.randomUUID().toString();
        String hash = androidIdHash != null ? androidIdHash : "%064x".formatted(RND.nextLong() & Long.MAX_VALUE);
        String report = """
                {"installId":"%s","androidIdHash":"%s","app":"tv","versionCode":1412,"versionName":"0.14.12-beta","channel":"stable","abi":"armeabi-v7a","supportedAbis":["armeabi-v7a"],
                 "sdk":34,"platform":"android-tv","manufacturer":"Gaia","model":"G32"}""".formatted(id, hash);
        MvcResult r = mvc.perform(post("/api/v1/devices/register").contentType(MediaType.APPLICATION_JSON).content(report).header("X-Test-Country", "cm"))
                .andExpect(status().isCreated()).andReturn();
        JsonNode b = body(r);
        String pub = b.get("deviceId").asText();
        long num = jdbc.queryForObject("select id from device where public_id = ?", Long.class, pub);
        return new Install(pub, num, b.get("deviceToken").asText());
    }

    // ------------------------------------------------------------------ signed activation tokens (what the tools sign, what a TV reports)

    static byte[] sign(Ed25519PrivateKeyParameters k, String text) {
        Ed25519Signer s = new Ed25519Signer();
        s.init(true, k);
        byte[] b = text.getBytes(StandardCharsets.UTF_8);
        s.update(b, 0, b.length);
        return s.generateSignature();
    }

    /** A signed activation token, 48 h window from {@code issuedAt}; production without usage right = unlimited, trial = implicit 30 days. */
    protected static String activation(Ed25519PrivateKeyParameters k, boolean trial, String license, String seat, Dev d, long issuedAtMs, long seq, List<String> rights) {
        WireActivation.Fields f = new WireActivation.Fields(trial ? "trial" : "production", "tv", kid(k), seq, rnd32().substring(0, 16), issuedAtMs, issuedAtMs, issuedAtMs + 48 * 3_600_000L,
                license, seat, d.k(), d.fp(), rights);
        return WireActivation.token(f, sign(k, WireActivation.payload(f)));
    }

    protected static String trialToken(Ed25519PrivateKeyParameters k, Dev d, long issuedAtMs) {
        return activation(k, true, "trial", WireActivation.defaultSeat("trial", d.fp()), d, issuedAtMs, issuedAtMs, List.of());
    }

    protected static String productionToken(Ed25519PrivateKeyParameters k, String license, Dev d, long issuedAtMs) {
        return activation(k, false, license, WireActivation.defaultSeat(license, d.fp()), d, issuedAtMs, issuedAtMs, List.of());
    }

    static String sha256(String s) { return HexFormat.of().formatHex(castbridge.server.licenses.Hashing.sha256(s.getBytes(StandardCharsets.UTF_8))); }

    /** Parsed fields of a token, for the tests that build journal entries from it. */
    static WireActivation.Fields fields(String token) { return WireActivation.decode(token).fields(); }

    // ------------------------------------------------------------------ licences (to give the server an issuance of its own)

    protected String serverIssuedProduction(Dev d) {
        try {
            products.get("p-act");
        } catch (castbridge.server.web.ApiException e) {
            products.create(OWNER, new ProductService.NewProduct("p-act", "Produit de suivi", "A_LA_CARTE", null, List.of("learn/test"), null, null, List.of("classe-act")));
        }
        var client = clients.create(OWNER, "Client de suivi " + SEQ.incrementAndGet(), "act@example.invalid", null);
        var lic = licenses.create(OWNER, new LicenseService.NewLicense(null, client.id(), "PAID", 2, null, Instant.now().plusSeconds(86400L * 365), null, null, List.of("p-act")));
        return activations.issue(OWNER, new ActivationService.IssueRequest(lic.licenseId(), "tv", d.text(), null, null, null, null), "server-api").text();
    }

    // ------------------------------------------------------------------ HTTP helpers

    protected static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder adminGet(String path) {
        return MockMvcRequestBuilders.get(path).header("Authorization", ADMIN);
    }

    /** POST of a signed journal (the cbx1 text as the body). */
    protected MvcResult uploadJournal(String token) throws Exception {
        return mvc.perform(post("/api/v1/admin/activations/journal").header("Authorization", ADMIN).contentType(MediaType.TEXT_PLAIN).content(token)).andReturn();
    }

    /** POST of a TV report. */
    protected MvcResult report(Install i, String json) throws Exception {
        return mvc.perform(post("/api/v1/activations/report").header("Authorization", "Bearer " + i.token()).contentType(MediaType.APPLICATION_JSON).content(json)).andReturn();
    }

    protected static String reportJson(Dev d, long atMs, String edition, List<String> tokens, String extraState) {
        StringBuilder sb = new StringBuilder("{\"v\":1,\"deviceCode\":\"").append(d.code()).append("\",\"app\":{\"code\":1412,\"name\":\"0.14.12-beta\"},\"activations\":[");
        for (int i = 0; i < tokens.size(); i++) sb.append(i > 0 ? "," : "").append('"').append(tokens.get(i)).append('"');
        sb.append("],\"state\":{\"edition\":\"").append(edition).append("\",\"usageTo\":null,\"super\":false,\"openAllUntil\":0,\"unlockUntil\":0,\"trialResets\":0,\"installedAt\":{},\"commands\":[]")
                .append(extraState == null ? "" : "," + extraState).append("},\"at\":").append(atMs).append('}');
        return sb.toString();
    }

    protected int events() { return jdbc.queryForObject("select count(*) from act_event", Integer.class); }

    protected int alerts(String type) { return jdbc.queryForObject("select count(*) from act_alert where type = ? and state <> 'CLOSED'", Integer.class, type); }

    /** Wipes the module's own tables (never the licence tables), to replay sources into an empty module. */
    protected void resetModule() {
        for (String t : List.of("act_command", "act_reg_issue", "act_journal_gap", "act_report", "act_tv_device", "act_alert", "act_journal_batch", "act_key", "act_tv", "act_tool", "act_event", "adm_read_audit",
                "act_daily", "act_tv_monthly", "act_checkpoint", "act_archive", "act_cursor", "act_journal_entry", "act_erased", "act_tv_key")) {
            jdbc.update("delete from " + t);
        }
        jdbc.update("update act_event_head set last_id = 0, last_hash = ?", "0".repeat(64));
        policy.resetLimits();
        clock.setTapLag(java.time.Duration.ZERO);   // the taps read a row at once (audit M7: the lag is exercised by CursorLagTest)
    }
}
