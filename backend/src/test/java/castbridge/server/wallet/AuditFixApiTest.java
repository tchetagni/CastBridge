package castbridge.server.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import castbridge.server.licenses.Envelope;
import castbridge.server.licenses.WireActivation;
import castbridge.server.wallet.core.AccountRef;
import castbridge.server.wallet.core.Currency;
import castbridge.server.wallet.core.Txn;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

/** Preuves de l'audit Opus de w22-02 au niveau des routes (H2, H3, M3, M4) et tests qui tuent les mutations survivantes. */
@org.springframework.context.annotation.Import(AuditFixApiTest.LicensesOn.class)
class AuditFixApiTest extends WalletTestBase {
    /** Les licences sont lues (module des licences « allumé » pour la lecture seule du portefeuille) sans démarrer tout le module des licences. */
    @org.springframework.boot.test.context.TestConfiguration
    static class LicensesOn {
        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        LicenseFacts licenseFactsOn(JdbcTemplate jdbc) { return new JdbcLicenseFacts(jdbc, true); }
    }

    static final Instant T0 = Instant.parse("2026-10-04T09:00:00Z");
    static final long NOW = T0.toEpochMilli();

    @Autowired JdbcTemplate jdbc;
    @Autowired JdbcLedger ledger;
    @Autowired WalletPolicyService policies;

    private final SnapshotSigner verifier = new SnapshotSigner(WalletKey.fromFile(WALLET_KEY_FILE.toString()), null);

    @BeforeEach
    void freeze() { clock.freezeAt(T0); }

    private MvcResult sync(String auth, Acts.Tv tv, String... activations) throws Exception {
        ObjectNode b = json.createObjectNode().put("deviceCode", tv.code());
        ArrayNode a = b.putArray("activations");
        for (String s : activations) a.add(s);
        return mvc.perform(post("/api/v1/wallet/sync").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(b.toString())).andReturn();
    }

    private JsonNode syncOk(String auth, Acts.Tv tv, String... activations) throws Exception {
        MvcResult r = sync(auth, tv, activations);
        assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        return body(r);
    }

    private Map<String, Object> snap(JsonNode r) { return verifier.verify(r.get("snapshot").asText()).orElseThrow(() -> new AssertionError("cbw1 non vérifié : " + r)); }

    private static long n(Map<String, Object> s, String k) { return ((Number) s.get(k)).longValue(); }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> flags(Map<String, Object> s) { return (Map<String, Object>) s.get("flags"); }

    private long clientId() {
        jdbc.update("INSERT INTO lic_client (name, created_at, updated_at) VALUES ('Client de test', ?, ?)", Timestamp.from(T0), Timestamp.from(T0));
        return jdbc.queryForObject("SELECT MAX(id) FROM lic_client", Long.class);
    }

    private void licence(Acts.Tv tv) {
        String id = "lic-api-" + tv.code().toLowerCase();
        jdbc.update("INSERT INTO lic_license (license_id, client_id, kind, state, seats_allowed, start_at, end_at, grace_days, transfer_cap, created_by, created_at, updated_at) "
                + "VALUES (?, ?, 'PAID', 'ACTIVE', 1, ?, NULL, 14, 2, 'test', ?, ?)", id, clientId(), Timestamp.from(T0), Timestamp.from(T0), Timestamp.from(T0));
        long pk = jdbc.queryForObject("SELECT id FROM lic_license WHERE license_id = ?", Long.class, id);
        jdbc.update("INSERT INTO lic_seat (license_pk, seat_id, subject, device_code, factors, k, slot_no, state, first_seen, last_seen) VALUES (?, ?, 'tv', ?, '', 1, 1, 'ACTIVE', ?, ?)", pk,
                String.format("%016x", pk), tv.code(), Timestamp.from(T0), Timestamp.from(T0));
    }

    // ---- H3 ----

    @Test
    void p5_aStrangerWithoutActivationMustNotReadSomeoneElsesWallet() throws Exception {
        Registered owner = registerApp("tv"), stranger = registerApp("tv");
        Acts.Tv tv = Acts.Tv.random();
        syncOk(owner.auth(), tv, Acts.trialDays(ISSUER, tv, NOW, 30));
        MvcResult r = sync(stranger.auth(), tv);
        String text = r.getResponse().getContentAsString();
        assertEquals(409, r.getResponse().getStatus(), text);
        assertFalse(text.contains("cbw1"), "aucun instantané signé pour un appareil sans activation de cette identité");
        assertFalse(text.contains("history") || text.contains("contributions"), text);
        assertEquals("ACTIVATE", body(r).get("details").get(0).asText());
    }

    @Test
    void p5_theBoundDeviceWithoutActivationReadsOnlyAndNoContributorIsCalled() throws Exception {
        Registered owner = registerApp("tv");
        Acts.Tv tv = Acts.Tv.random();
        syncOk(owner.auth(), tv, Acts.trialDays(ISSUER, tv, NOW, 30));
        JsonNode r = syncOk(owner.auth(), tv);
        assertEquals(100, n(snap(r), "n"));
        assertEquals(0, r.get("contributions").size(), "les contributeurs ne servent que sur une identité prouvée par une activation de ce contact");
        assertFalse(r.get("edition").get("boundOther").asBoolean(), "l'appareil lié n'est pas « une autre TV »");
        assertEquals("ACTIVATE", r.get("notices").get(0).get("reason").asText());
        assertEquals(1, r.get("notices").size());
        Map<String, Object> f = flags(snap(r));
        assertEquals(false, f.get("stakesN"), "aucune mise sans activation acceptée à ce contact");
        assertEquals(false, f.get("stakesM"));
    }

    @Test
    void p5_aLicenceActiveButNoAcceptedActivationAllowsNoStake() throws Exception {
        Registered owner = registerApp("tv");
        Acts.Tv tv = Acts.Tv.random();
        licence(tv);
        Map<String, Object> s1 = snap(syncOk(owner.auth(), tv, Acts.production(ISSUER, tv, NOW, List.of())));
        assertEquals(true, flags(s1).get("stakesM"), "activation acceptée + licence ACTIVE : mise MBOKO permise");
        // cbx1 révoquée : la licence est toujours ACTIVE, mais l'activation présentée n'est pas acceptée
        Map<String, Object> s2 = snap(syncOk(owner.auth(), tv));
        assertEquals(false, flags(s2).get("stakesN"));
        assertEquals(false, flags(s2).get("stakesM"));
    }

    // ---- M3 ----

    @Test
    void p7_aRevokedTrialKeyInTheLicenceRevocationTableOpensNothing() throws Exception {
        Registered dev = registerApp("tv");
        Acts.Tv tv = Acts.Tv.random();
        String act = Acts.trialDays(ISSUER, tv, NOW, 30);
        WireActivation.Fields f = WireActivation.fieldsOf(Envelope.decode(act));
        jdbc.update("INSERT INTO lic_revocation (license_id, seat_id, reason, revoked_by, revoked_at) VALUES (?, ?, 'test', 'test', ?)", f.license(), f.seat(), Timestamp.from(T0.plusSeconds(3_600)));
        clock.freezeAt(T0.plusSeconds(7_200));
        MvcResult r = sync(dev.auth(), tv, act);
        assertEquals(409, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM wallet_identity WHERE holder = ?", Long.class, tv.code()));
    }

    @Test
    void p7_aTrialRevokedAfterItsFirstTrancheGivesNoMore() throws Exception {
        Registered dev = registerApp("tv");
        Acts.Tv tv = Acts.Tv.random();
        String act = Acts.trialDays(ISSUER, tv, NOW, 90);
        assertEquals(100, n(snap(syncOk(dev.auth(), tv, act)), "n"));
        WireActivation.Fields f = WireActivation.fieldsOf(Envelope.decode(act));
        jdbc.update("INSERT INTO lic_revocation (license_id, seat_id, reason, revoked_by, revoked_at) VALUES (?, ?, 'test', 'test', ?)", f.license(), f.seat(), Timestamp.from(T0.plusSeconds(10 * 86_400)));
        clock.freezeAt(T0.plusSeconds(70 * 86_400));
        Map<String, Object> s = snap(syncOk(dev.auth(), tv, act));
        assertEquals(100, n(s, "n"), "p1 et p2 (jours 30 et 60) suivent la révocation du jour 10 : rien");
        assertEquals(false, flags(s).get("stakesN"));
    }

    // ---- M4 ----

    @Test
    void p6_theSeqChangesWhenTheTrialEndsSoTheTvDoesNotKeepTheOldState() throws Exception {
        Registered dev = registerApp("tv");
        Acts.Tv tv = Acts.Tv.random();
        String act = Acts.trialDays(ISSUER, tv, NOW, 30);
        Map<String, Object> s1 = snap(syncOk(dev.auth(), tv, act));
        assertEquals("TRIAL", s1.get("ed"));
        clock.freezeAt(T0.plusSeconds(40 * 86_400));
        Map<String, Object> s2 = snap(syncOk(dev.auth(), tv, act));
        assertEquals("NONE", s2.get("ed"));
        assertTrue(n(s2, "seq") > n(s1, "seq"), "seq " + n(s1, "seq") + " -> " + n(s2, "seq") + " : un champ signé a changé");
    }

    @Test
    void p6_theSeqChangesWhenTheIdentityIsFrozenOrTheStakesAreSwitchedOffAndNotOtherwise() throws Exception {
        Registered dev = registerApp("tv");
        Acts.Tv tv = Acts.Tv.random();
        String act = Acts.trialDays(ISSUER, tv, NOW, 30);
        Map<String, Object> s1 = snap(syncOk(dev.auth(), tv, act));
        assertEquals(n(s1, "seq"), n(snap(syncOk(dev.auth(), tv, act)), "seq"), "rien n'a changé : même seq");
        policies.set("switch.stakes.NDEM", 0, "test");
        try {
            Map<String, Object> s2 = snap(syncOk(dev.auth(), tv, act));
            assertEquals(false, flags(s2).get("stakesN"));
            assertTrue(n(s2, "seq") > n(s1, "seq"), "interrupteur de mise coupé : seq croissant");
            jdbc.update("UPDATE wallet_identity SET frozen = TRUE WHERE holder = ?", tv.code());
            Map<String, Object> s3 = snap(syncOk(dev.auth(), tv, act));
            assertEquals(true, flags(s3).get("frozen"));
            assertTrue(n(s3, "seq") > n(s2, "seq"), "gel : seq croissant");
        } finally {
            policies.set("switch.stakes.NDEM", 1, "test");
        }
    }

    // ---- mutations survivantes ----

    @Test
    void mutation1_aPhoneDeviceTokenIsRefusedOnSync() throws Exception {
        Registered phone = registerApp("phone");
        Acts.Tv tv = Acts.Tv.random();
        assertEquals(403, sync(phone.auth(), tv, Acts.trialDays(ISSUER, tv, NOW, 30)).getResponse().getStatus());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM wallet_identity WHERE holder = ?", Long.class, tv.code()));
    }

    @Test
    void mutation2_aDeviceBlockedByTheAdministratorIsRefusedOnSync() throws Exception {
        Registered dev = registerApp("tv");
        jdbc.update("UPDATE device SET blocked = TRUE WHERE public_id = ?", dev.publicId());
        Acts.Tv tv = Acts.Tv.random();
        assertEquals(403, sync(dev.auth(), tv, Acts.trialDays(ISSUER, tv, NOW, 30)).getResponse().getStatus());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM wallet_identity WHERE holder = ?", Long.class, tv.code()));
    }

    @Test
    void mutation3_aFrozenIdentitySignsFrozenAndNoStake() throws Exception {
        Registered dev = registerApp("tv");
        Acts.Tv tv = Acts.Tv.random();
        String act = Acts.trialDays(ISSUER, tv, NOW, 30);
        syncOk(dev.auth(), tv, act);
        jdbc.update("UPDATE wallet_identity SET frozen = TRUE, frozen_reason = 'test' WHERE holder = ?", tv.code());
        Map<String, Object> s = snap(syncOk(dev.auth(), tv, act));
        assertEquals(true, flags(s).get("frozen"));
        assertEquals(false, flags(s).get("stakesN"));
        assertEquals(false, flags(s).get("stakesM"));
    }

    @Test
    void mutation4_theSeqOfOneIdentityIgnoresTheWritesOfAnother() throws Exception {
        Registered da = registerApp("tv"), db = registerApp("tv");
        Acts.Tv a = Acts.Tv.random(), b = Acts.Tv.random();
        String actA = Acts.trialDays(ISSUER, a, NOW, 30), actB = Acts.trialDays(ISSUER, b, NOW, 30);
        long seqA = n(snap(syncOk(da.auth(), a, actA)), "seq");
        syncOk(db.auth(), b, actB);
        ledger.post(Txn.adjust(b.code(), Currency.NDEM, 7, "adj:admin:mut4-" + b.code()), "admin:test", b.code(), "test");
        assertEquals(seqA, n(snap(syncOk(da.auth(), a, actA)), "seq"), "le seq de A ne bouge pas quand B reçoit une écriture");
        assertEquals(100, ledger.balance(AccountRef.dispo(a.code(), Currency.NDEM)));
        assertNotEquals(seqA, n(snap(syncOk(db.auth(), b, actB)), "seq"), "celui de B bouge");
    }
}
