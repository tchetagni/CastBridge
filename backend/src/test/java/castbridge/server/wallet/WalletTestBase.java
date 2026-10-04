package castbridge.server.wallet;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import castbridge.server.ApiTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Module portefeuille allumé, clé « portefeuille » de test, émetteur d'activations de test (TEST SEULEMENT : jamais une clé réelle). */
@Import(WalletTestBase.TestBeans.class)
@org.springframework.test.context.TestPropertySource(properties = "castbridge.wallet.require-bind-proof=false")
public abstract class WalletTestBase extends ApiTestBase {
    public static final KeyPair WALLET = pair();
    public static final KeyPair ISSUER = pair();
    static final Path WALLET_KEY_FILE = writeKey(WALLET);

    static final byte[] TOTP_KEY = new byte[32];

    static {
        new java.security.SecureRandom().nextBytes(TOTP_KEY);
    }

    @Autowired protected WalletModuleConfig.WalletClock clock;
    @Autowired protected org.springframework.jdbc.core.JdbcTemplate baseJdbc;
    @Autowired protected castbridge.server.licenses.TotpVault totpVault;

    @DynamicPropertySource
    static void wallet(DynamicPropertyRegistry r) {
        r.add("castbridge.wallet.enabled", () -> "true");
        r.add("castbridge.wallet.key-file", WALLET_KEY_FILE::toString);
        r.add("castbridge.wallet.trusted-keys", () -> "issuer:" + rawPublic(ISSUER) + ":ISSUE_TRIAL+ISSUE_PRODUCTION+SUPER_UNLIMITED+REVOKE");
        // second facteur des administrateurs (module des licences) : clé de coffre de test ; la preuve de possession de la TV est exigée par les seuls tests qui la couvrent
        r.add("castbridge.licenses.totp-key", () -> Base64.getEncoder().encodeToString(TOTP_KEY));
    }

    /** Un administrateur de test : compte OWNER avec TOTP activé (secret chiffré par le coffre des licences). Un code n'est valable qu'une fois : chaque administrateur n'en a que quelques-uns par fenêtre. */
    protected final class Admin {
        final String name;
        final byte[] secret = castbridge.server.licenses.Totp.newSecret();
        private long last = -1;

        Admin(String name, String role, boolean totp) {
            this.name = name;
            baseJdbc.update("INSERT INTO admin_user (username, password_hash, failed_attempts, created_at, role, totp_enabled, totp_secret_enc) VALUES (?, 'x', 0, ?, ?, ?, ?)", name,
                    java.sql.Timestamp.from(java.time.Instant.now()), role, totp, totp ? totpVault.seal(secret) : null);
        }

        /** Le prochain code valable (pas de recul : un code déjà accepté est refusé). */
        String code() {
            long now = castbridge.server.licenses.Totp.stepAt(java.time.Instant.now().getEpochSecond());
            long step = Math.max(last + 1, now - 1);
            if (step > now + 1) throw new IllegalStateException("plus de code TOTP disponible dans cette fenêtre pour " + name);
            last = step;
            return castbridge.server.licenses.Totp.code(secret, step);
        }

        MockHttpServletRequestBuilder sign(MockHttpServletRequestBuilder b) { return b.header("X-Admin-User", name).header("X-Totp", code()); }
    }

    private static final java.util.concurrent.atomic.AtomicInteger ADMINS = new java.util.concurrent.atomic.AtomicInteger();

    protected Admin newAdmin() { return new Admin("admin-test-" + ADMINS.incrementAndGet(), "OWNER", true); }

    protected Admin newAdmin(String role, boolean totp) { return new Admin("admin-test-" + ADMINS.incrementAndGet(), role, totp); }

    /** Une application enregistrée : l'en-tête {@code Authorization} et l'identifiant public. */
    protected record Registered(String auth, String publicId) {}

    protected Registered registerApp(String app) throws Exception {
        String hash = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        JsonNode reg = body(mvc.perform(MockMvcRequestBuilders.post("/api/v1/devices/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"installId\":\"" + UUID.randomUUID() + "\",\"app\":\"" + app + "\",\"versionCode\":7,\"androidIdHash\":\"" + hash + "\"}"))
                .andExpect(status().isCreated()).andReturn());
        return new Registered("Bearer " + reg.get("deviceToken").asText(), reg.get("deviceId").asText());
    }

    /** Un contributeur de synchronisation de test, pour prouver que l'interface publiée ici est appelée (w22-05 et w22-06 publieront les leurs). */
    @TestConfiguration
    static class TestBeans {
        @Bean
        SyncContributor testContributor() {
            return new SyncContributor() {
                @Override public String name() { return "test"; }

                @Override public Object contribute(SyncContext ctx) { return java.util.Map.of("identity", ctx.identity()); }
            };
        }
    }

    public static KeyPair pair() {
        try {
            return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static byte[] rawPublicBytes(KeyPair p) {
        byte[] der = p.getPublic().getEncoded();
        return Arrays.copyOfRange(der, der.length - 32, der.length);
    }

    public static String rawPublic(KeyPair p) { return Base64.getEncoder().encodeToString(rawPublicBytes(p)); }

    public static Path writeKey(KeyPair p) {
        try {
            Path f = Files.createTempFile("wallet", ".key");
            Files.writeString(f, "-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(p.getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----\n");
            return f;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Enregistre une TV et rend l'en-tête {@code Authorization} de son jeton d'appareil. */
    protected String registerTv() throws Exception {
        String hash = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        JsonNode reg = body(mvc.perform(MockMvcRequestBuilders.post("/api/v1/devices/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"installId\":\"" + UUID.randomUUID() + "\",\"app\":\"tv\",\"versionCode\":7,\"androidIdHash\":\"" + hash + "\"}"))
                .andExpect(status().isCreated()).andReturn());
        return "Bearer " + reg.get("deviceToken").asText();
    }
}
