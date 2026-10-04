package castbridge.server.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import castbridge.server.wallet.core.AccountRef;
import castbridge.server.wallet.core.Currency;
import castbridge.server.wallet.core.Txn;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * H2 de l'audit : la création de valeur par l'administration ne tient plus à un seul jeton porteur. Second facteur TOTP du module des licences (compte nommé, code à usage unique),
 * plafonds par don et par jour dans la table de politique, règle des deux personnes au-delà, entrée d'audit chaînée.
 */
class AuditFixAdminTest extends WalletTestBase {
    static final Instant T0 = Instant.parse("2026-10-04T09:00:00Z");

    @Autowired JdbcTemplate jdbc;
    @Autowired JdbcLedger ledger;
    @Autowired WalletPolicyService policies;

    private String tvCode;

    @BeforeEach
    void identity() {
        clock.freezeAt(T0);
        jdbc.update("DELETE FROM wallet_admin_grant");
        tvCode = Acts.Tv.random().code();
        jdbc.update("INSERT INTO wallet_identity (holder, api_device_id, created_at) VALUES (?, 1, ?)", tvCode, java.sql.Timestamp.from(T0));
    }

    @AfterEach
    void restorePolicy() {
        policies.set("admin.grantMax.NDEM", 10_000, "test");
        policies.set("admin.dailyMax.NDEM", 100_000, "test");
        policies.set("admin.grantsPerHour", 10, "test");
    }

    private String grantJson(long amount, String idem) {
        return "{\"identity\":\"" + tvCode + "\",\"currency\":\"NDEM\",\"amount\":" + amount + ",\"reason\":\"Geste commercial\"" + (idem == null ? "" : ",\"idem\":\"" + idem + "\"") + "}";
    }

    private MvcResult grant(Admin who, long amount, String idem) throws Exception {
        MockHttpServletRequestBuilder b = post("/api/v1/admin/wallet/grant").header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON).content(grantJson(amount, idem));
        return mvc.perform(who == null ? b : who.sign(b)).andReturn();
    }

    private long ndem() { return ledger.balance(AccountRef.dispo(tvCode, Currency.NDEM)); }

    @Test
    void p8_theBearerTokenAloneCreatesNothing() throws Exception {
        assertEquals(403, grant(null, 100, null).getResponse().getStatus());
        assertEquals(0, ndem());
    }

    @Test
    void p8_aWrongOrReplayedTotpCodeIsRefused() throws Exception {
        Admin a = newAdmin();
        MockHttpServletRequestBuilder wrong = post("/api/v1/admin/wallet/grant").header("Authorization", ADMIN).header("X-Admin-User", a.name).header("X-Totp", "000000")
                .contentType(MediaType.APPLICATION_JSON).content(grantJson(100, null));
        assertEquals(403, mvc.perform(wrong).andReturn().getResponse().getStatus());
        String code = a.code();
        MockHttpServletRequestBuilder ok = post("/api/v1/admin/wallet/grant").header("Authorization", ADMIN).header("X-Admin-User", a.name).header("X-Totp", code)
                .contentType(MediaType.APPLICATION_JSON).content(grantJson(100, null));
        assertEquals(200, mvc.perform(ok).andReturn().getResponse().getStatus());
        assertEquals(100, ndem());
        MockHttpServletRequestBuilder replay = post("/api/v1/admin/wallet/grant").header("Authorization", ADMIN).header("X-Admin-User", a.name).header("X-Totp", code)
                .contentType(MediaType.APPLICATION_JSON).content(grantJson(100, null));
        assertEquals(403, mvc.perform(replay).andReturn().getResponse().getStatus(), "un code TOTP ne sert qu'une fois");
        assertEquals(100, ndem());
    }

    @Test
    void p8_anAccountWithoutTotpOrWithoutTheOwnerRoleCannotGrant() throws Exception {
        assertEquals(403, grant(newAdmin("OWNER", false), 100, null).getResponse().getStatus(), "TOTP non activé");
        Admin support = newAdmin("SUPPORT", true);
        assertEquals(403, grant(support, 100, null).getResponse().getStatus(), "le rôle support ne crée pas de valeur");
        assertEquals(403, mvc.perform(post("/api/v1/admin/wallet/grant").header("Authorization", ADMIN).header("X-Admin-User", "inconnu").header("X-Totp", "123456")
                .contentType(MediaType.APPLICATION_JSON).content(grantJson(100, null))).andReturn().getResponse().getStatus());
        assertEquals(0, ndem());
    }

    @Test
    void p8_theActorOfTheGrantIsTheNamedAdministratorAndTheAuditIsChained() throws Exception {
        Admin a = newAdmin();
        MvcResult r = grant(a, 250, "named-1");
        assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        assertEquals("admin:" + a.name, jdbc.queryForObject("SELECT actor FROM wallet_txn WHERE idem_key = 'adj:admin:named-1'", String.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM lic_audit WHERE action = 'WALLET_GRANT' AND actor = ? AND target_id = ?", Long.class, a.name, tvCode),
                "une entrée du journal d'audit chaîné, au nom de l'administrateur");
    }

    @Test
    void p8_aGrantAboveThePerGrantCapWaitsForASecondAdministrator() throws Exception {
        Admin first = newAdmin(), second = newAdmin();
        MvcResult r = grant(first, 10_001, "big-1");
        assertEquals(202, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        JsonNode body = body(r);
        assertEquals("PENDING", body.get("status").asText());
        long id = body.get("requestId").asLong();
        assertEquals(0, ndem(), "rien n'est posé tant qu'un second administrateur n'a pas approuvé");
        // le demandeur ne s'approuve pas lui-même
        assertEquals(403, mvc.perform(first.sign(post("/api/v1/admin/wallet/grant/" + id + "/approve").header("Authorization", ADMIN))).andReturn().getResponse().getStatus());
        assertEquals(0, ndem());
        // un administrateur sans TOTP valide non plus
        assertEquals(403, mvc.perform(post("/api/v1/admin/wallet/grant/" + id + "/approve").header("Authorization", ADMIN).header("X-Admin-User", second.name).header("X-Totp", "000000"))
                .andReturn().getResponse().getStatus());
        assertEquals(0, ndem());
        MvcResult ok = mvc.perform(second.sign(post("/api/v1/admin/wallet/grant/" + id + "/approve").header("Authorization", ADMIN))).andReturn();
        assertEquals(200, ok.getResponse().getStatus(), ok.getResponse().getContentAsString());
        assertEquals(10_001, ndem());
        assertEquals("admin:" + first.name, jdbc.queryForObject("SELECT actor FROM wallet_txn WHERE idem_key = 'adj:admin:big-1'", String.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM lic_audit WHERE action = 'WALLET_GRANT_APPROVE' AND actor = ?", Long.class, second.name));
        // approuver deux fois ne pose pas deux fois
        Admin third = newAdmin();
        assertEquals(409, mvc.perform(third.sign(post("/api/v1/admin/wallet/grant/" + id + "/approve").header("Authorization", ADMIN))).andReturn().getResponse().getStatus());
        assertEquals(10_001, ndem());
    }

    @Test
    void p8_theDailyCapAlsoSendsTheNextGrantToApproval() throws Exception {
        policies.set("admin.grantMax.NDEM", 100, "test");
        policies.set("admin.dailyMax.NDEM", 150, "test");
        assertEquals(200, grant(newAdmin(), 100, null).getResponse().getStatus());
        MvcResult r = grant(newAdmin(), 100, null);
        assertEquals(202, r.getResponse().getStatus(), "100 + 100 dépasse le plafond journalier de 150 : approbation requise");
        assertEquals(100, ndem());
    }

    @Test
    void p8_aRejectedRequestPostsNothingAndTooManyGrantsPerHourAreRefused() throws Exception {
        Admin first = newAdmin(), second = newAdmin();
        long id = body(grant(first, 10_001, null)).get("requestId").asLong();
        assertEquals(200, mvc.perform(second.sign(post("/api/v1/admin/wallet/grant/" + id + "/reject").header("Authorization", ADMIN))).andReturn().getResponse().getStatus());
        assertEquals(0, ndem());
        assertEquals(409, mvc.perform(newAdmin().sign(post("/api/v1/admin/wallet/grant/" + id + "/approve").header("Authorization", ADMIN))).andReturn().getResponse().getStatus());
        policies.set("admin.grantsPerHour", 2, "test");
        assertEquals(200, grant(newAdmin(), 1, null).getResponse().getStatus());
        assertEquals(429, grant(newAdmin(), 1, null).getResponse().getStatus(), "2 par heure : la demande refusée du début et le premier don comptent déjà");
        assertTrue(ndem() <= 1);
    }

    // ---- règle du propriétaire : seuls NDEM et MBOKO circulent ----

    @Test
    void onlyNdemAndMbokoCanBeTransferredOrGranted() throws Exception {
        assertEquals(java.util.Set.of("NDEM", "MBOKO"), java.util.Arrays.stream(Currency.values()).map(Enum::name).collect(java.util.stream.Collectors.toSet()),
                "toute nouvelle monnaie ou tout nouvel actif transférable doit être décidé par le propriétaire");
        for (String bad : new String[] {"LICENSE", "LICENCE", "ACTIVATION", "EDITION", "VOUCHER", "ndem"}) {
            org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> jdbc.update("INSERT INTO wallet_account (holder, cur, pocket) VALUES ('SYS:TEST', ?, 'DISPO')", bad), "compte en " + bad);
            org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> jdbc.update("INSERT INTO wallet_escrow (eid, holder, cur, amount, created_at) VALUES (?, 'SYS:TEST', ?, 1, ?)", "e-" + bad, bad, java.sql.Timestamp.from(T0)), "blocage en " + bad);
            MvcResult r = mvc.perform(newAdmin().sign(post("/api/v1/admin/wallet/grant").header("Authorization", ADMIN).contentType(MediaType.APPLICATION_JSON)
                    .content(grantJson(1, null).replace("NDEM", bad)))).andReturn();
            assertEquals(400, r.getResponse().getStatus(), "don en " + bad);
        }
        assertFalse(jdbc.queryForList("SELECT cur FROM wallet_account WHERE cur NOT IN ('NDEM', 'MBOKO')", String.class).iterator().hasNext());
        // un transfert de NDEM, lui, reste libre
        String other = Acts.Tv.random().code();
        ledger.post(Txn.adjust(tvCode, Currency.NDEM, 5, "adj:admin:xfer-seed-" + tvCode), "admin:test", tvCode, "test");
        ledger.post(Txn.transfer(tvCode, other, Currency.NDEM, 5, "xfer-" + tvCode));
        assertEquals(5, ledger.balance(AccountRef.dispo(other, Currency.NDEM)));
    }
}
