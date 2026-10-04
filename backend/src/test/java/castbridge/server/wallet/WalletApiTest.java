package castbridge.server.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/** Routes {@code /api/v1/wallet/**} et {@code /api/v1/admin/wallet/**} de bout en bout (MockMvc, H2 en mode MySQL, mêmes migrations). */
class WalletApiTest extends WalletTestBase {
    static final Instant T0 = Instant.parse("2026-10-04T09:00:00Z");
    static final long NOW = T0.toEpochMilli();

    @Autowired JdbcTemplate jdbc;

    private final SnapshotSigner verifier = new SnapshotSigner(WalletKey.fromFile(WALLET_KEY_FILE.toString()), null);

    @BeforeEach
    void freeze() { clock.freezeAt(T0); }

    private JsonNode sync(String auth, Acts.Tv tv, String... activations) throws Exception {
        ObjectNode b = json.createObjectNode().put("deviceCode", tv.code());
        ArrayNode a = b.putArray("activations");
        for (String s : activations) a.add(s);
        return body(mvc.perform(post("/api/v1/wallet/sync").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(b.toString())).andExpect(status().isOk()).andReturn());
    }

    private Map<String, Object> snap(JsonNode syncResponse) {
        return verifier.verify(syncResponse.get("snapshot").asText()).orElseThrow(() -> new AssertionError("cbw1 non vérifié : " + syncResponse));
    }

    private static long n(Map<String, Object> s, String k) { return ((Number) s.get(k)).longValue(); }

    private void adminGrant(String id, String cur, long amount, String reason) throws Exception {
        mvc.perform(post("/api/v1/admin/wallet/grant").header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON)
                .content("{\"identity\":\"" + id + "\",\"currency\":\"" + cur + "\",\"amount\":" + amount + ",\"reason\":\"" + reason + "\"}")).andExpect(status().isOk());
    }

    @Test
    void trialSyncCreditsTheTrancheAndReturnsAVerifiableSnapshot() throws Exception {
        String auth = registerTv();
        Acts.Tv tv = Acts.Tv.random();
        JsonNode r = sync(auth, tv, Acts.trialDays(ISSUER, tv, NOW, 30));
        Map<String, Object> s = snap(r);
        assertEquals(tv.code(), s.get("id"));
        assertEquals("TRIAL", s.get("ed"));
        assertEquals(100, n(s, "n"));
        assertEquals(0, n(s, "m"));
        assertEquals(0, n(s, "nb"));
        assertEquals(NOW, n(s, "at"));
        @SuppressWarnings("unchecked") Map<String, Object> flags = (Map<String, Object>) s.get("flags");
        assertEquals(false, flags.get("frozen"));
        assertEquals(true, flags.get("stakesN"));
        assertEquals(false, flags.get("stakesM"), "l'essai ne mise jamais de MBOKO");
        assertEquals(1, r.get("history").size());
        assertEquals("Attribution mensuelle", r.get("history").get(0).get("label").asText());
        assertEquals(100, r.get("history").get(0).get("amount").asLong());
        assertEquals("NDEM", r.get("history").get(0).get("currency").asText());
        assertEquals(0, r.get("notices").size());
        assertTrue(r.get("contributions").isObject());
        // un deuxième sync ne change rien, y compris le seq
        long seq = n(s, "seq");
        assertTrue(seq > 0);
        Map<String, Object> s2 = snap(sync(auth, tv, Acts.trialDays(ISSUER, tv, NOW, 30)));
        assertEquals(100, n(s2, "n"));
        assertEquals(seq, n(s2, "seq"));
    }

    @Test
    void snapshotSeqGrowsWithEveryLedgerWriteAndTheTokenCannotBeTampered() throws Exception {
        String auth = registerTv();
        Acts.Tv tv = Acts.Tv.random();
        JsonNode r = sync(auth, tv, Acts.trialDays(ISSUER, tv, NOW, 30));
        long seq1 = n(snap(r), "seq");
        adminGrant(tv.code(), "NDEM", 25, "Geste commercial");
        Map<String, Object> s2 = snap(sync(auth, tv, Acts.trialDays(ISSUER, tv, NOW, 30)));
        assertTrue(n(s2, "seq") > seq1);
        assertEquals(125, n(s2, "n"));
        String token = r.get("snapshot").asText();
        String[] p = token.split("\\.");
        char c = p[1].charAt(10) == 'A' ? 'B' : 'A';
        assertTrue(verifier.verify(p[0] + "." + p[1].substring(0, 10) + c + p[1].substring(11) + "." + p[2]).isEmpty());
    }

    @Test
    void productionActivationWithoutLicenseIsPendingAndCreditsNothing() throws Exception {
        String auth = registerTv();
        Acts.Tv tv = Acts.Tv.random();
        JsonNode r = sync(auth, tv, Acts.production(ISSUER, tv, NOW, List.of()));
        Map<String, Object> s = snap(r);
        assertEquals("NONE", s.get("ed"));
        assertEquals(0, n(s, "n"));
        assertEquals(0, n(s, "m"));
        assertEquals("LICENSE_PENDING", r.get("notices").get(0).get("reason").asText());
        assertEquals("Licence en attente d'enregistrement", r.get("notices").get(0).get("text").asText());
        assertEquals("PENDING", r.get("edition").get("license").asText());
    }

    @Test
    void noValidActivationForAnUnknownIdentityIsRefused() throws Exception {
        String auth = registerTv();
        Acts.Tv tv = Acts.Tv.random();
        ObjectNode b = json.createObjectNode().put("deviceCode", tv.code());
        b.putArray("activations");
        JsonNode e = body(mvc.perform(post("/api/v1/wallet/sync").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(b.toString()))
                .andExpect(status().isConflict()).andReturn());
        assertEquals("Activez la TV pour recevoir des jetons", e.get("message").asText());
        assertEquals("ACTIVATE", e.get("details").get(0).asText());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM wallet_identity WHERE holder = ?", Long.class, tv.code()), "rien n'est ouvert sur la parole de la TV");
    }

    @Test
    void anotherTvsActivationOpensNothing() throws Exception {
        String auth = registerTv();
        Acts.Tv mine = Acts.Tv.random(), other = Acts.Tv.random();
        ObjectNode b = json.createObjectNode().put("deviceCode", mine.code());
        b.putArray("activations").add(Acts.trialDays(ISSUER, other, NOW, 30));
        mvc.perform(post("/api/v1/wallet/sync").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(b.toString())).andExpect(status().isConflict());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM wallet_identity WHERE holder IN (?, ?)", Long.class, mine.code(), other.code()));
    }

    @Test
    void clockDoubtWithholdsTheTranche() throws Exception {
        String auth = registerTv();
        Acts.Tv tv = Acts.Tv.random();
        String future = Acts.trialDays(ISSUER, tv, NOW + 25 * 3_600_000L, 30), prod = Acts.production(ISSUER, tv, NOW, List.of());
        // identité inconnue : rien n'est ouvert, motif CLOCK
        ObjectNode b = json.createObjectNode().put("deviceCode", tv.code());
        b.putArray("activations").add(future).add(prod);
        JsonNode e = body(mvc.perform(post("/api/v1/wallet/sync").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(b.toString())).andExpect(status().isConflict()).andReturn());
        assertEquals("Vérifiez l'heure de la TV", e.get("message").asText());
        assertEquals("CLOCK", e.get("details").get(0).asText());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM wallet_identity WHERE holder = ?", Long.class, tv.code()));
        // identité connue : lecture seule, aucune tranche de plus, notice CLOCK
        assertEquals(100, n(snap(sync(auth, tv, Acts.trialDays(ISSUER, tv, NOW, 30))), "n"));
        clock.freezeAt(T0.plusSeconds(40 * 86_400));
        JsonNode r = sync(auth, tv, Acts.trialDays(ISSUER, tv, NOW + 41 * 86_400_000L + 25 * 3_600_000L, 30));
        assertEquals(100, n(snap(r), "n"));
        assertEquals("CLOCK", r.get("notices").get(0).get("reason").asText());
        assertEquals("Vérifiez l'heure de la TV", r.get("notices").get(0).get("text").asText());
    }

    @Test
    void anotherApiDeviceReadsButIsNotBound() throws Exception {
        String first = registerTv(), second = registerTv();
        Acts.Tv tv = Acts.Tv.random();
        String act = Acts.trialDays(ISSUER, tv, NOW, 30);
        sync(first, tv, act);
        long bound = jdbc.queryForObject("SELECT api_device_id FROM wallet_identity WHERE holder = ?", Long.class, tv.code());
        JsonNode r = sync(second, tv, act);
        assertEquals(100, n(snap(r), "n"), "lecture permise");
        assertEquals("BOUND_OTHER_TV", r.get("notices").get(0).get("reason").asText());
        assertTrue(r.get("edition").get("boundOther").asBoolean());
        assertEquals(bound, jdbc.queryForObject("SELECT api_device_id FROM wallet_identity WHERE holder = ?", Long.class, tv.code()));
    }

    @Test
    void historyPagesOf50WithFrenchLabelsAndMaskedCounterparty() throws Exception {
        String auth = registerTv();
        Acts.Tv tv = Acts.Tv.random(), friend = Acts.Tv.random();
        sync(auth, tv, Acts.trialDays(ISSUER, tv, NOW, 30));
        for (int i = 0; i < 60; i++) adminGrant(tv.code(), "NDEM", 1 + i, "Ligne " + i);
        JsonNode p1 = body(mvc.perform(get("/api/v1/wallet/history").header("Authorization", auth)).andExpect(status().isOk()).andReturn());
        assertEquals(50, p1.get("lines").size());
        long lastId = p1.get("lines").get(49).get("id").asLong();
        assertTrue(p1.get("lines").get(0).get("id").asLong() > lastId, "plus récent d'abord");
        assertEquals("Don de l'administrateur", p1.get("lines").get(0).get("label").asText());
        assertEquals(lastId, p1.get("next").asLong());
        JsonNode p2 = body(mvc.perform(get("/api/v1/wallet/history").param("before", String.valueOf(lastId)).header("Authorization", auth)).andExpect(status().isOk()).andReturn());
        assertEquals(11, p2.get("lines").size(), "60 dons + 1 attribution = 61 lignes");
        assertTrue(p2.get("next").isNull());
        // contrepartie masquée d'un transfert
        sync(registerTv(), friend, Acts.trialDays(ISSUER, friend, NOW, 30));
        jdbc.update("UPDATE wallet_txn SET kind = kind WHERE 1 = 0");
        castbridge.server.wallet.core.Txn t = castbridge.server.wallet.core.Txn.transfer(tv.code(), friend.code(), castbridge.server.wallet.core.Currency.NDEM, 5, "api-xfer-" + tv.code());
        ledger.post(t);
        JsonNode p3 = body(mvc.perform(get("/api/v1/wallet/history").header("Authorization", auth)).andReturn());
        JsonNode line = p3.get("lines").get(0);
        assertEquals("Transfert envoyé", line.get("label").asText());
        assertEquals(-5, line.get("amount").asLong());
        assertEquals("TV …" + friend.code().replace("-", "").substring(12), line.get("counterparty").asText());
        assertFalse(line.toString().contains(friend.code()), "le code complet de la contrepartie n'est jamais montré");
    }

    @Autowired JdbcLedger ledger;

    @Test
    void policyIsPublishedForDisplay() throws Exception {
        String auth = registerTv();
        JsonNode p = body(mvc.perform(get("/api/v1/wallet/policy").header("Authorization", auth)).andExpect(status().isOk()).andReturn());
        assertEquals(1000, p.get("rate").asLong());
        assertEquals(0, p.get("reverseFeeBp").asInt());
        assertEquals(1000, p.get("stake").get("NDEM").get("max").asLong());
        assertTrue(p.get("switches").get("stakesNdem").asBoolean(), "interrupteurs d'exploitation actifs par défaut");
        assertTrue(p.get("switches").get("convert").asBoolean());
        assertTrue(p.get("switches").get("transfer").asBoolean());
        assertTrue(p.get("switches").get("vouchers").asBoolean());
    }

    @Test
    void walletRoutesNeedADeviceToken() throws Exception {
        mvc.perform(post("/api/v1/wallet/sync").contentType(MediaType.APPLICATION_JSON).content("{\"deviceCode\":\"x\"}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/wallet/history")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/wallet/policy")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/wallet/sync").header("Authorization", "Bearer nope").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminGrantNeedsTheAdminTokenAReasonAndAKnownIdentity() throws Exception {
        String auth = registerTv();
        Acts.Tv tv = Acts.Tv.random();
        sync(auth, tv, Acts.trialDays(ISSUER, tv, NOW, 30));
        String ok = "{\"identity\":\"" + tv.code() + "\",\"currency\":\"MBOKO\",\"amount\":3,\"reason\":\"Remboursement du lot\",\"idem\":\"adm-1\"}";
        mvc.perform(post("/api/v1/admin/wallet/grant").contentType(MediaType.APPLICATION_JSON).content(ok)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/admin/wallet/grant").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(ok)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/admin/wallet/grant").header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON).content(ok.replace("Remboursement du lot", "  "))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/admin/wallet/grant").header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON).content(ok.replace("\"amount\":3", "\"amount\":0"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/admin/wallet/grant").header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON).content(ok.replace("MBOKO", "EURO"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/admin/wallet/grant").header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON).content(ok.replace(tv.code(), Acts.Tv.random().code()))).andExpect(status().isNotFound());
        JsonNode done = body(mvc.perform(post("/api/v1/admin/wallet/grant").header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON).content(ok)).andExpect(status().isOk()).andReturn());
        assertFalse(done.get("replayed").asBoolean());
        assertEquals(3, done.get("balance").asLong());
        // même clé, même contenu : rejeu ; même clé, autre montant : refus
        assertTrue(body(mvc.perform(post("/api/v1/admin/wallet/grant").header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON).content(ok)).andReturn()).get("replayed").asBoolean());
        mvc.perform(post("/api/v1/admin/wallet/grant").header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON).content(ok.replace("\"amount\":3", "\"amount\":4"))).andExpect(status().isConflict());
        assertEquals("admin-token", jdbc.queryForObject("SELECT actor FROM wallet_txn WHERE idem_key = 'adj:admin:adm-1'", String.class).replaceFirst("^admin:", ""));
        assertEquals("Remboursement du lot", jdbc.queryForObject("SELECT reason FROM wallet_txn WHERE idem_key = 'adj:admin:adm-1'", String.class));
    }

    @Test
    void reconcileReportsTheInvariantsAndSeesACorruptedBalance() throws Exception {
        String auth = registerTv();
        Acts.Tv tv = Acts.Tv.random();
        sync(auth, tv, Acts.trialDays(ISSUER, tv, NOW, 30));
        mvc.perform(get("/api/v1/admin/wallet/reconcile")).andExpect(status().isUnauthorized());
        JsonNode ok = body(mvc.perform(get("/api/v1/admin/wallet/reconcile").header("Authorization", ADMIN)).andExpect(status().isOk()).andReturn());
        assertTrue(ok.get("ok").asBoolean(), ok.toString());
        assertEquals(0, ok.get("i1").get("NDEM").asLong());
        assertEquals(0, ok.get("i1").get("MBOKO").asLong());
        assertEquals(0, ok.get("i8").get("mismatches").asLong());
        assertTrue(ok.get("i3").get("massNDEM").asLong() >= 100);
        jdbc.update("UPDATE wallet_balance SET balance = balance + 1 WHERE account_id = (SELECT id FROM wallet_account WHERE holder = ? AND cur = 'NDEM' AND pocket = 'DISPO')", tv.code());
        JsonNode bad = body(mvc.perform(get("/api/v1/admin/wallet/reconcile").header("Authorization", ADMIN)).andExpect(status().isOk()).andReturn());
        assertFalse(bad.get("ok").asBoolean());
        assertEquals(1, bad.get("i8").get("mismatches").asLong());
        assertTrue(bad.get("i1").get("NDEM").asLong() != 0 || bad.get("i3").get("ok").asBoolean() == false, "la masse ne colle plus");
        jdbc.update("UPDATE wallet_balance SET balance = balance - 1 WHERE account_id = (SELECT id FROM wallet_account WHERE holder = ? AND cur = 'NDEM' AND pocket = 'DISPO')", tv.code());
    }

    @Test
    void syncContributorsAreCalledAndTheirAnswerIsReturned() throws Exception {
        String auth = registerTv();
        Acts.Tv tv = Acts.Tv.random();
        JsonNode r = sync(auth, tv, Acts.trialDays(ISSUER, tv, NOW, 30));
        assertTrue(r.get("contributions").has("test"), "le contributeur de test publié par ce module de test répond sous son nom");
        assertEquals(tv.code(), r.get("contributions").get("test").get("identity").asText());
    }
}
