package castbridge.server.wallet.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import castbridge.server.wallet.core.Currency;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Blocage sans résultat : rendu à {@code exp + 6 h} (30 min + 6 h après la pose), à la synchronisation du titulaire ou par la réconciliation ; un résultat arrivé après est refusé. */
class EscrowExpiryTest extends OpsTestBase {

    private JsonNode syncAt(Tv tv, Duration after) throws Exception {
        clock.freezeAt(T0.plus(after));
        return sync(tv);
    }

    @Test
    void anEscrowWithoutResultIsGivenBackAtExpPlusSixHoursOnTheNextSync() throws Exception {
        Tv tv = trialTv();
        String eid = escrow(tv, "NDEM", 10, 2, "exp-0001").json().get("eid").asText();
        assertEquals(80, bal(tv, Currency.NDEM));
        JsonNode early = syncAt(tv, Duration.ofHours(6).plusMinutes(29).plusSeconds(59));
        assertEquals(80, bal(tv, Currency.NDEM), "à 6 h 29 min 59 s : trop tôt");
        assertEquals(20, locked(tv, Currency.NDEM));
        assertEquals(0, early.get("contributions").get("escrow-expiry").get("refunded").size());
        JsonNode late = syncAt(tv, Duration.ofHours(6).plusMinutes(31));
        assertEquals(100, bal(tv, Currency.NDEM));
        assertEquals(0, locked(tv, Currency.NDEM));
        assertEquals("REFUNDED", jdbc.queryForObject("SELECT state FROM wallet_escrow WHERE eid = ?", String.class, eid));
        assertEquals(eid, late.get("contributions").get("escrow-expiry").get("refunded").get(0).asText());
        assertEquals(1, newTxns("ESCROW_REFUND"));
        syncAt(tv, Duration.ofHours(7));
        assertEquals(1, newTxns("ESCROW_REFUND"), "un seul rendu");
        assertEquals(100, bal(tv, Currency.NDEM));
        assertReconciled();
    }

    @Test
    void aResultArrivingAfterTheRefundIsRefusedAndNothingIsSettled() throws Exception {
        Tv a = trialTv(), b = trialTv();
        String ea = escrow(a, "NDEM", 10, 2, "exp-0002").json().get("eid").asText();
        String eb = escrow(b, "NDEM", 10, 2, "exp-0002").json().get("eid").asText();
        syncAt(a, Duration.ofHours(7));   // seul le blocage de A est rendu à sa synchronisation ; B n'a pas synchronisé
        assertEquals(100, bal(a, Currency.NDEM));
        assertEquals(20, locked(b, Currency.NDEM));
        String t = cbr1(RESULT, rid(200), "NDEM", 10, "END", List.of(new Line(ea, a.code(), 20, 10), new Line(eb, b.code(), 20, 30)));
        Reply r = settle(t);
        assertEquals(409, r.status());
        assertEquals("RESULT_AFTER_REFUND", r.reason());
        assertEquals(100, bal(a, Currency.NDEM));
        assertEquals(80, bal(b, Currency.NDEM), "tout ou rien : B n'est pas réglé");
        assertEquals(20, locked(b, Currency.NDEM));
        assertEquals(0, newTxns("SETTLE"));
        assertEquals(409, settle(t).status(), "le rejeu reçoit le même refus");
        // B sera rendu à sa propre échéance (ici : par la réconciliation)
        JsonNode done = body(mvc.perform(post("/api/v1/admin/wallet/escrow-expiry").header("Authorization", ADMIN)).andReturn());
        assertEquals(1, done.get("refunded").asInt());
        assertEquals(100, bal(b, Currency.NDEM));
        assertReconciled();
    }

    @Test
    void aResultThatArrivesBeforeTheExpiryStillSettles() throws Exception {
        Tv a = trialTv(), b = trialTv();
        String ea = escrow(a, "NDEM", 10, 1, "exp-0003").json().get("eid").asText();
        String eb = escrow(b, "NDEM", 10, 1, "exp-0003").json().get("eid").asText();
        clock.freezeAt(T0.plus(Duration.ofHours(6)));
        assertEquals(200, settle(cbr1(RESULT, rid(201), "NDEM", 10, "END", List.of(new Line(ea, a.code(), 10, 20), new Line(eb, b.code(), 10, 0)))).status());
        syncAt(a, Duration.ofHours(8));
        assertEquals(0, newTxns("ESCROW_REFUND"), "un blocage réglé n'est jamais rendu");
        assertEquals(110, bal(a, Currency.NDEM));
    }

    @Test
    void theAdminRouteIsReservedToTheAdministrator() throws Exception {
        assertEquals(401, mvc.perform(post("/api/v1/admin/wallet/escrow-expiry")).andReturn().getResponse().getStatus());
        assertTrue(mvc.perform(post("/api/v1/admin/wallet/escrow-expiry").header("Authorization", ADMIN)).andReturn().getResponse().getStatus() == 200);
    }
}
