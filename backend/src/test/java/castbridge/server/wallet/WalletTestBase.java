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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Module portefeuille allumé, clé « portefeuille » de test, émetteur d'activations de test (TEST SEULEMENT : jamais une clé réelle). */
@Import(WalletTestBase.TestBeans.class)
public abstract class WalletTestBase extends ApiTestBase {
    public static final KeyPair WALLET = pair();
    public static final KeyPair ISSUER = pair();
    static final Path WALLET_KEY_FILE = writeKey(WALLET);

    @Autowired protected WalletModuleConfig.WalletClock clock;

    @DynamicPropertySource
    static void wallet(DynamicPropertyRegistry r) {
        r.add("castbridge.wallet.enabled", () -> "true");
        r.add("castbridge.wallet.key-file", WALLET_KEY_FILE::toString);
        r.add("castbridge.wallet.trusted-keys", () -> "issuer:" + rawPublic(ISSUER) + ":ISSUE_TRIAL+ISSUE_PRODUCTION+SUPER_UNLIMITED+REVOKE");
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
