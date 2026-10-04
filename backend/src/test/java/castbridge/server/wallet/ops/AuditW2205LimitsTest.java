package castbridge.server.wallet.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Mutations 3 et 5 de l'audit : le débit d'écriture (4 par minute et par identité, la synchronisation en compte une) couvre CHACUNE des routes transfert, conversion et code de réception ;
 * le débit du règlement est PAR ADRESSE (2 par minute).
 */
class AuditW2205LimitsTest extends OpsTestBase {
    @DynamicPropertySource
    static void limits(DynamicPropertyRegistry r) {
        r.add("castbridge.wallet.settle-per-minute", () -> "2");
        r.add("castbridge.wallet.writes-per-minute-per-identity", () -> "4");
    }

    private static final String WRITE_LIMIT = "Trop d'opérations de portefeuille";

    @Test
    void theThirtyFirstWriteIsRefusedOnConvert() throws Exception {
        Tv tv = productionTv();
        for (int i = 0; i < 3; i++) assertEquals(200, convert(tv, "M2N", 1, "lim-cv-" + i).status());
        Reply r = convert(tv, "M2N", 1, "lim-cv-9");
        assertEquals(429, r.status());
        assertEquals("RATE_LIMIT", r.reason(), "F5 : un motif fermé pour les 429 de débit");
        assertTrue(r.message().startsWith(WRITE_LIMIT), r.message());
    }

    @Test
    void theWriteLimitCoversReceiveCodeCreation() throws Exception {
        Tv tv = productionTv();
        for (int i = 0; i < 3; i++) assertEquals(200, receiveCode(tv).status());
        Reply r = receiveCode(tv);
        assertEquals(429, r.status());
        assertTrue(r.message().startsWith(WRITE_LIMIT), "le débit d'écriture, pas CODE_LIMIT : " + r.message());
    }

    @Test
    void theWriteLimitCoversTransfer() throws Exception {
        Tv to = productionTv(), from = productionTv();
        for (int i = 0; i < 3; i++) assertEquals(200, transfer(from, receiveCode(to).json().get("code").asText(), "NDEM", 1, "lim-xf-" + i).status(), "transfert " + i);
        Reply r = transfer(from, "R0000-0000-00", "NDEM", 1, "lim-xf-9");
        assertEquals(429, r.status());
        assertTrue(r.message().startsWith(WRITE_LIMIT), r.message());
    }

    private Reply settleFrom(String address, String token) throws Exception {
        return reply(mvc.perform(post("/api/v1/wallet/settle").contentType(MediaType.TEXT_PLAIN).content(token).with(rq -> {
            rq.setRemoteAddr(address);
            return rq;
        })).andReturn());
    }

    @Test
    void theSettleLimitIsPerAddress() throws Exception {
        for (int i = 0; i < 2; i++) assertEquals(400, settleFrom("10.1.1.1", "pas un résultat").status());
        Reply limited = settleFrom("10.1.1.1", "pas un résultat");
        assertEquals(429, limited.status(), "3e requête de la même adresse");
        assertEquals("RATE_LIMIT", limited.reason());
        assertEquals(400, settleFrom("10.2.2.2", "pas un résultat").status(), "une autre adresse a son propre quota");
        assertEquals(400, settleFrom("10.2.2.2", "pas un résultat").status());
        assertEquals(429, settleFrom("10.2.2.2", "pas un résultat").status());
    }
}
