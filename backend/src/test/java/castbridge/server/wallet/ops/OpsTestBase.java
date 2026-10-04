package castbridge.server.wallet.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import castbridge.server.licenses.LicenseKeyring;
import castbridge.server.wallet.Acts;
import castbridge.server.wallet.JdbcLedger;
import castbridge.server.wallet.WalletTestBase;
import castbridge.server.wallet.core.AccountRef;
import castbridge.server.wallet.core.Currency;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.Signature;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Outils des tests des opérations du portefeuille (w22-05) : TV d'essai ou de production (licence RÉELLE dans lic_license / lic_seat), appels HTTP, fabrique de {@code cbr1} signés par une clé
 * de résultat DE TEST (jamais une clé réelle), vérification de {@code cbe1}. Débits d'écriture relevés : les tests de débit ont leur propre classe.
 */
@org.springframework.test.context.TestPropertySource(properties = {"castbridge.wallet.writes-per-minute-per-identity=100000", "castbridge.wallet.writes-per-minute-global=1000000", "castbridge.wallet.settle-per-minute=100000"})
public abstract class OpsTestBase extends WalletTestBase {
    public static final KeyPair RESULT = pair();
    public static final KeyPair RESULT_2 = pair();
    public static final KeyPair STRANGER = pair();
    public static final Instant T0 = Instant.parse("2026-10-04T09:00:00Z");
    public static final long NOW = T0.toEpochMilli();

    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected JdbcLedger ledger;

    @DynamicPropertySource
    static void ops(DynamicPropertyRegistry r) {
        r.add("castbridge.wallet.play-result-pubkeys", () -> rawPublic(RESULT) + "," + rawPublic(RESULT_2));
        r.add("castbridge.licenses.enabled", () -> "true");
    }

    private final java.util.Map<String, Long> baseline = new java.util.HashMap<>();

    @BeforeEach
    void freezeTheClock() {
        clock.freezeAt(T0);
        baseline.clear();
        for (String k : List.of("TRANSFER", "SETTLE", "ESCROW_LOCK", "ESCROW_REFUND", "CONVERT")) baseline.put(k, txns(k));
    }

    /** Nombre de transactions de ce genre posées depuis le début du test en cours (la base est partagée par les tests d'une classe). */
    protected long newTxns(String kind) { return txns(kind) - baseline.get(kind); }

    /** Une TV connue du portefeuille : jeton d'appareil, matériel de test, activations présentables. */
    public record Tv(String auth, Acts.Tv hw, List<String> activations, boolean production) {
        public String code() { return hw.code(); }
    }

    protected Tv trialTv() throws Exception {
        String auth = registerTv();
        Acts.Tv hw = Acts.Tv.random();
        Tv tv = new Tv(auth, hw, List.of(Acts.trialDays(ISSUER, hw, NOW, 30)), false);
        sync(tv);
        return tv;
    }

    /** TV de production : activation de production (identité seulement) + LICENCE du serveur (sans fin si {@code end} est null). */
    protected Tv productionTv(Instant end, int graceDays, String state) throws Exception {
        String auth = registerTv();
        Acts.Tv hw = Acts.Tv.random();
        Instant start = end == null || end.isAfter(T0) ? T0.minusSeconds(86_400) : end.minusSeconds(30L * 86_400);
        license(hw, "lic-" + hw.code().toLowerCase(), state, start, end, graceDays);
        Tv tv = new Tv(auth, hw, List.of(Acts.production(ISSUER, hw, NOW, List.of())), true);
        sync(tv);
        return tv;
    }

    protected Tv productionTv() throws Exception { return productionTv(T0.plusSeconds(365L * 86_400), 14, "ACTIVE"); }

    protected long license(Acts.Tv hw, String licenseId, String state, Instant start, Instant end, int grace) {
        jdbc.update("INSERT INTO lic_client (name, created_at, updated_at) VALUES ('Client de test', ?, ?)", Timestamp.from(T0), Timestamp.from(T0));
        long client = jdbc.queryForObject("SELECT MAX(id) FROM lic_client", Long.class);
        jdbc.update("INSERT INTO lic_license (license_id, client_id, kind, state, seats_allowed, start_at, end_at, grace_days, transfer_cap, created_by, created_at, updated_at) "
                + "VALUES (?, ?, 'PAID', ?, 1, ?, ?, ?, 2, 'test', ?, ?)", licenseId, client, state, Timestamp.from(start), end == null ? null : Timestamp.from(end), grace,
                Timestamp.from(start), Timestamp.from(start));
        long pk = jdbc.queryForObject("SELECT id FROM lic_license WHERE license_id = ?", Long.class, licenseId);
        jdbc.update("INSERT INTO lic_seat (license_pk, seat_id, subject, device_code, factors, k, slot_no, state, first_seen, last_seen) VALUES (?, ?, 'tv', ?, '', 1, 1, 'ACTIVE', ?, ?)",
                pk, "00000000" + String.format("%08x", pk), hw.code(), Timestamp.from(start), Timestamp.from(start));
        return pk;
    }

    protected JsonNode sync(Tv tv) throws Exception {
        ObjectNode b = json.createObjectNode().put("deviceCode", tv.code());
        ArrayNode a = b.putArray("activations");
        tv.activations().forEach(a::add);
        return body(mvc.perform(post("/api/v1/wallet/sync").header("Authorization", tv.auth()).contentType(MediaType.APPLICATION_JSON).content(b.toString()))
                .andExpect(status().isOk()).andReturn());
    }

    /** Don de fonds de test : compte OWNER + TOTP (audit w22-02 H2), plafonds relevés pour que le don soit appliqué sans second administrateur. */
    protected void adminGrant(String id, String cur, long amount) throws Exception {
        jdbc.update("UPDATE wallet_policy SET val = 1000000000 WHERE name IN ('admin.grantMax.NDEM','admin.grantMax.MBOKO','admin.dailyMax.NDEM','admin.dailyMax.MBOKO')");
        jdbc.update("UPDATE wallet_policy SET val = 1000 WHERE name = 'admin.grantsPerHour'");
        mvc.perform(newAdmin().sign(post("/api/v1/admin/wallet/grant")).header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON)
                .content("{\"identity\":\"" + id + "\",\"currency\":\"" + cur + "\",\"amount\":" + amount + ",\"reason\":\"Fonds de test\"}")).andExpect(status().isOk());
    }

    protected void setPolicy(String name, long value) { jdbc.update("UPDATE wallet_policy SET val = ? WHERE name = ?", value, name); }

    /** Une réponse HTTP : statut et corps JSON (le corps peut être vide). */
    public record Reply(int status, JsonNode json) {
        public String reason() { return json.path("details").path(0).asText(""); }

        public String message() { return json.path("message").asText(""); }
    }

    protected Reply reply(MvcResult r) throws Exception {
        byte[] raw = r.getResponse().getContentAsByteArray();
        return new Reply(r.getResponse().getStatus(), raw.length == 0 ? json.nullNode() : json.readTree(raw));
    }

    protected Reply postJson(String auth, String path, ObjectNode body) throws Exception {
        var rq = post(path).contentType(MediaType.APPLICATION_JSON).content(body.toString());
        if (auth != null) rq = rq.header("Authorization", auth);
        return reply(mvc.perform(rq).andReturn());
    }

    protected ObjectNode req(Tv tv) { return json.createObjectNode().put("deviceCode", tv.code()); }

    protected Reply escrow(Tv tv, String cur, long per, int k, String idem) throws Exception { return escrowAs(tv, tv.auth(), cur, per, k, idem); }

    protected Reply escrowAs(Tv tv, String auth, String cur, long per, int k, String idem) throws Exception {
        return postJson(auth, "/api/v1/wallet/escrow", req(tv).put("cur", cur).put("per", per).put("k", k).put("idem", idem));
    }

    protected Reply convert(Tv tv, String dir, long q, String idem) throws Exception {
        return postJson(tv.auth(), "/api/v1/wallet/convert", req(tv).put("dir", dir).put("q", q).put("idem", idem));
    }

    protected Reply receiveCode(Tv tv) throws Exception { return postJson(tv.auth(), "/api/v1/wallet/receive-code", req(tv)); }

    protected Reply lookup(Tv tv, String code) throws Exception {
        return reply(mvc.perform(get("/api/v1/wallet/receive-code/" + code).param("deviceCode", tv.code()).header("Authorization", tv.auth())).andReturn());
    }

    protected Reply transfer(Tv tv, String code, String cur, long amt, String idem) throws Exception { return transferAs(tv, tv.auth(), code, cur, amt, idem); }

    protected Reply transferAs(Tv tv, String auth, String code, String cur, long amt, String idem) throws Exception {
        return postJson(auth, "/api/v1/wallet/transfer", req(tv).put("code", code).put("cur", cur).put("amt", amt).put("idem", idem));
    }

    protected Reply settle(String token) throws Exception {
        return reply(mvc.perform(post("/api/v1/wallet/settle").contentType(MediaType.TEXT_PLAIN).content(token)).andReturn());
    }

    protected long bal(Tv tv, Currency c) { return ledger.balance(AccountRef.dispo(tv.code(), c)); }

    protected long locked(Tv tv, Currency c) { return ledger.balance(AccountRef.bloque(tv.code(), c)); }

    protected long count(String sql, Object... args) { return jdbc.queryForObject(sql, Long.class, args); }

    protected long txns(String kind) { return count("SELECT COUNT(*) FROM wallet_txn WHERE kind = ?", kind); }

    protected long sys(String holder, String cur) { return count("SELECT COALESCE(SUM(b.balance),0) FROM wallet_account a JOIN wallet_balance b ON b.account_id = a.id WHERE a.holder = ? AND a.cur = ?", holder, cur); }

    protected void assertReconciled() throws Exception {
        JsonNode r = body(mvc.perform(get("/api/v1/admin/wallet/reconcile").header("Authorization", ADMIN)).andReturn());
        assertTrue(r.get("ok").asBoolean(), "I-1, I-3, I-8 : " + r);
        assertEquals(0, r.get("i8").get("mismatches").asLong());
    }

    // ---- cbr1 de test : même format que le service de jeu (vecteurs communs), signé par une clé de test ----

    /** Une ligne de résultat : blocage, titulaire, utilisé, payé. */
    public record Line(String eid, String id, long used, long pay) {}

    public static String cbr1(KeyPair signer, String rid, String cur, long per, String kind, List<Line> lines) {
        String kid = LicenseKeyring.kidOf(rawPublicBytes(signer));
        StringBuilder sb = new StringBuilder("{\"kid\":\"").append(kid).append("\",\"rid\":\"").append(rid).append("\",\"room\":\"ROOM1\",\"game\":\"quiz\",\"cur\":\"").append(cur)
                .append("\",\"per\":").append(per).append(",\"kind\":\"").append(kind).append("\",\"at\":").append(NOW).append(",\"lines\":[");
        for (int i = 0; i < lines.size(); i++) {
            Line l = lines.get(i);
            sb.append(i == 0 ? "" : ",").append("[\"").append(l.eid()).append("\",\"").append(l.id()).append("\",").append(l.used()).append(',').append(l.pay()).append(']');
        }
        return sign(signer, sb.append("]}").toString());
    }

    /** Signe une charge JSON déjà compacte (permet de forger des charges anormales). */
    public static String sign(KeyPair signer, String payloadJson) {
        String b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        try {
            Signature s = Signature.getInstance("Ed25519");
            s.initSign(signer.getPrivate());
            s.update(("castbridge-play-result-v1\ncbr1." + b64).getBytes(StandardCharsets.US_ASCII));
            return "cbr1." + b64 + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(s.sign());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Identifiant de résultat : 128 bits en hexadécimal minuscule. */
    public static String rid(int n) { return String.format("%032x", n); }

    /** Vérifie un cbe1 : signature par la clé « portefeuille », domaine, charge. */
    public static JsonNode verifyCbe1(ObjectMapper om, String token) throws Exception {
        String[] p = token.split("\\.");
        assertEquals(3, p.length);
        assertEquals("cbe1", p[0]);
        Signature s = Signature.getInstance("Ed25519");
        s.initVerify(WALLET.getPublic());
        s.update(("castbridge-wallet-escrow-v1\ncbe1." + p[1]).getBytes(StandardCharsets.US_ASCII));
        assertTrue(s.verify(Base64.getUrlDecoder().decode(p[2])), "signature du cbe1");
        return om.readTree(Base64.getUrlDecoder().decode(p[1]));
    }
}
