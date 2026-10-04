package castbridge.server.licenses;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import castbridge.server.wallet.Acts;
import castbridge.server.wallet.WalletTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.Signature;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Les trois modules allumés ensemble (licences, suivi des activations, portefeuille), comme en production : la preuve de possession de la TV est EXIGÉE
 * ({@code require-bind-proof=true}, aucun raccourci de test). Toutes les clés sont fabriquées ici au hasard (jamais une clé réelle ; {@code ~/.castbridge-signing} n'est pas lu).
 * Le portefeuille ne fait confiance qu'à {@link #ISSUER} ; les licences font confiance à {@link #ISSUER} (production + essai), à {@link #REACT} (réactivation seule) et à {@link #TRIAL_ONLY}.
 */
@org.springframework.test.context.TestPropertySource(properties = "castbridge.wallet.require-bind-proof=true")
public abstract class RegistrarTestBase extends WalletTestBase {
    public static final KeyPair REACT = pair();
    public static final KeyPair TRIAL_ONLY = pair();
    public static final KeyPair STRANGER = pair();
    /** Clés réservées à un test chacune (une révocation ou un plafond par clé est permanent dans la base partagée d'une classe). */
    public static final KeyPair BURNED = pair(), RATE = pair();
    /** L'outil du propriétaire (bureau) qui signe les événements du registre. */
    public static final KeyPair TOOL = pair();
    public static final Instant T0 = Instant.parse("2026-10-04T09:00:00Z");
    public static final long NOW = T0.toEpochMilli();
    public static final long HOUR = 3_600_000L, DAY = Acts.DAY;

    @DynamicPropertySource
    static void registrar(DynamicPropertyRegistry r) {
        try {
            Path secrets = Files.createTempDirectory("cb-reg-secrets");
            byte[] k = new byte[32];
            new java.security.SecureRandom().nextBytes(k);
            Files.write(secrets.resolve("act-ref.key"), k);
            new java.security.SecureRandom().nextBytes(k);
            Files.write(secrets.resolve("act-audit.key"), k);
            new java.security.SecureRandom().nextBytes(k);
            Files.writeString(secrets.resolve("act-checkpoint.key"), Base64.getEncoder().encodeToString(k));
            Path archive = Files.createTempDirectory("cb-reg-archive");
            r.add("castbridge.licenses.enabled", () -> "true");
            r.add("castbridge.licenses.secrets-dir", secrets::toString);
            r.add("castbridge.licenses.trusted-keys", () -> "issuer:" + rawPublic(ISSUER) + ":ISSUE_TRIAL+ISSUE_PRODUCTION+SUPER_UNLIMITED+REVOKE,react:" + rawPublic(REACT) + ":REACTIVATE,trialonly:"
                    + rawPublic(TRIAL_ONLY) + ":ISSUE_TRIAL,burned:" + rawPublic(BURNED) + ":ISSUE_PRODUCTION,rate:" + rawPublic(RATE) + ":ISSUE_PRODUCTION,tool:"
                    + rawPublic(TOOL) + ":ISSUE_TRIAL+ISSUE_PRODUCTION+REGISTRY+REVOKE");
            r.add("castbridge.activations.enabled", () -> "true");
            r.add("castbridge.activations.archive-dir", archive::toString);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected ReportedActivationRegistrar registrar;
    @Autowired protected LedgerService ledger;
    @Autowired protected LicenseService licenses;

    @org.junit.jupiter.api.BeforeEach
    void freezeTheClock() {
        clock.freezeAt(T0);
        // le plafond par clé compte les licences ouvertes dans la base partagée de la classe : chaque test repart de zéro (les licences, elles, restent)
        jdbc.update("DELETE FROM lic_registration WHERE kid <> 'none'");
    }

    // ------------------------------------------------------------------ activations de production de test

    /** Un matériel de test : deux empreintes aléatoires, donc un code d'appareil propre. */
    protected static Acts.Tv tv() { return Acts.Tv.random(); }

    /**
     * Une production signée par {@code issuer}, fenêtre d'installation de 48 h depuis {@code issuedAt} ; {@code days} = durée de la clé (droit {@code usage}), null = illimitée ;
     * {@code seat} null = poste par défaut (dérivé du matériel).
     */
    protected static String production(KeyPair issuer, Acts.Tv tv, String license, String seat, long issuedAt, Integer days) {
        List<String> rights = days == null ? List.of() : List.of("usage|duree|" + issuedAt + "|" + (issuedAt + days * DAY));
        return production(issuer, tv, license, seat, issuedAt, rights, UUID.randomUUID().toString().replace("-", "").substring(0, 16));
    }

    protected static String production(KeyPair issuer, Acts.Tv tv, String license, String seat, long issuedAt, List<String> rights, String nonce) {
        String kid = LicenseKeyring.kidOf(rawPublicBytes(issuer));
        WireActivation.Fields f = new WireActivation.Fields("production", "tv", kid, 1, nonce, issuedAt, issuedAt, issuedAt + 48 * HOUR, license,
                seat != null ? seat : WireActivation.defaultSeat(license, tv.factors()), DeviceIdentity.kFor(tv.factors().size()), tv.factors(), rights);
        return WireActivation.token(f, Acts.sign(issuer, WireActivation.payload(f)));
    }

    protected static String bind(KeyPair install, String code, String publicId, long at) {
        try {
            Signature s = Signature.getInstance("Ed25519");
            s.initSign(install.getPrivate());
            s.update(("castbridge-wallet-bind-v1\n" + code + "\n" + publicId + "\n" + at).getBytes(StandardCharsets.UTF_8));
            return "{\"key\":\"" + rawPublic(install) + "\",\"at\":" + at + ",\"sig\":\"" + Base64.getEncoder().encodeToString(s.sign()) + "\"}";
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------ appels HTTP

    /** {@code POST /api/v1/wallet/sync} exactement comme la TV 0.14.32 : code, activations, preuve de possession. */
    protected MvcResult sync(Registered dev, KeyPair install, Acts.Tv tv, String... activations) throws Exception {
        ObjectNode b = json.createObjectNode().put("deviceCode", tv.code());
        ArrayNode a = b.putArray("activations");
        for (String s : activations) a.add(s);
        if (install != null) b.set("bind", json.readTree(bind(install, tv.code(), dev.publicId(), clock.now().toEpochMilli())));
        return mvc.perform(post("/api/v1/wallet/sync").header("Authorization", dev.auth()).contentType(MediaType.APPLICATION_JSON).content(b.toString())).andReturn();
    }

    protected JsonNode ok(MvcResult r) throws Exception {
        org.junit.jupiter.api.Assertions.assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        return body(r);
    }

    protected static List<String> noticeReasons(JsonNode sync) {
        List<String> l = new ArrayList<>();
        sync.path("notices").forEach(n -> l.add(n.path("reason").asText()));
        return l;
    }

    protected long balance(String code, String cur) {
        Long v = jdbc.queryForObject("SELECT COALESCE(SUM(b.balance), 0) FROM wallet_balance b JOIN wallet_account a ON a.id = b.account_id WHERE a.holder = ? AND a.cur = ?", Long.class, code, cur);
        return v == null ? 0 : v;
    }

    protected long count(String sql, Object... args) {
        Long v = jdbc.queryForObject(sql, Long.class, args);
        return v == null ? 0 : v;
    }

    // ------------------------------------------------------------------ registre de l'outil (docs/ACTIVATION-FORMAT.md § 8-9)

    protected static RegistryEvent registryEvent(String type, long at, List<String> fields, java.util.Map<DeviceIdentity.Factor, String> factors) { return registryEvent(TOOL, type, at, fields, factors); }

    protected static RegistryEvent registryEvent(KeyPair signer, String type, long at, List<String> fields, java.util.Map<DeviceIdentity.Factor, String> factors) {
        String kid = LicenseKeyring.kidOf(rawPublicBytes(signer));
        List<String> lines = new ArrayList<>(List.of(RegistryEvent.FORMAT, "type=" + type, "kid=" + kid, "at=" + at));
        lines.addAll(fields);
        java.util.EnumMap<DeviceIdentity.Factor, String> sorted = new java.util.EnumMap<>(DeviceIdentity.Factor.class);
        sorted.putAll(factors);
        sorted.forEach((f, h) -> lines.add("factor=" + f.name() + "|" + h));
        String text = String.join("\n", lines);
        return new RegistryEvent(kid, text, Base64.getEncoder().encodeToString(Acts.sign(signer, text)));
    }

    protected static RegistryEvent licenseEvent(long at, String license, int seats) { return licenseEvent(TOOL, at, license, seats); }

    protected static RegistryEvent licenseEvent(KeyPair signer, long at, String license, int seats) {
        return registryEvent(signer, "license", at, List.of("license=" + license, "seats=" + seats, "maxTransfersPerYear=0"), java.util.Map.of());
    }

    protected static RegistryEvent issueEvent(long at, String license, String seat, Acts.Tv tv, String nonce) { return issueEvent(TOOL, at, license, seat, tv, nonce); }

    protected static RegistryEvent issueEvent(KeyPair signer, long at, String license, String seat, Acts.Tv tv, String nonce) {
        return registryEvent(signer, "issue", at, List.of("license=" + license, "seat=" + seat, "subject=tv", "kind=production", "nonce=" + nonce, "notAfter=" + (at + 48 * HOUR),
                "k=" + DeviceIdentity.kFor(tv.factors().size())), tv.factors());
    }

    protected LedgerService.ImportReport importRegistry(List<RegistryEvent> events) throws IOException {
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
        return ledger.importLedger(OWNER, json.writeValueAsBytes(root), false, true);
    }

    protected static final Actor OWNER = new Actor("owner-test", Role.OWNER, "api", true);
    protected static final Actor SUPPORT = new Actor("support-test", Role.SUPPORT, "web", true);

    @Autowired protected ClientService clients;

    protected static String nonce16() { return UUID.randomUUID().toString().replace("-", "").substring(0, 16); }

    protected static String licenseId() { return "lic-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10); }
}
