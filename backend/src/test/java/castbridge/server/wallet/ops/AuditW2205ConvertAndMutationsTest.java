package castbridge.server.wallet.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import castbridge.server.wallet.core.Currency;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Audit w22-05 : F2 (rejeu d'une conversion après un changement de frais) et les mutations 1, 2 (gels) qui survivaient. */
class AuditW2205ConvertAndMutationsTest extends OpsTestBase {

    @AfterEach
    void restore() { setPolicy("convert.reverseFeeBp", 0); }

    /** p6 / F2 : réessayer une conversion réussie après un changement de frais rend le résultat ORIGINAL, sans rien reposer. */
    @Test
    void p6_retryAfterAFeeChangeReturnsTheOriginalResult() throws Exception {
        Tv tv = productionTv();
        adminGrant(tv.code(), "MBOKO", 50);
        setPolicy("convert.reverseFeeBp", 100);   // 1 %
        Reply first = convert(tv, "M2N", 5, "p6-0001");
        assertEquals(200, first.status(), first.json().toString());
        JsonNode f = first.json();
        assertEquals(5_000, f.get("ndemGross").asLong());
        assertEquals(50, f.get("fee").asLong());
        assertEquals(4_950, f.get("ndemNet").asLong());
        long ndem = bal(tv, Currency.NDEM), mboko = bal(tv, Currency.MBOKO);
        setPolicy("convert.reverseFeeBp", 200);   // le propriétaire change les frais
        Reply again = convert(tv, "M2N", 5, "p6-0001");
        assertEquals(200, again.status(), "reçu " + again.status() + " " + again.json());
        assertTrue(again.json().get("replayed").asBoolean());
        assertEquals(50, again.json().get("fee").asLong(), "les montants réellement appliqués, pas les frais actuels");
        assertEquals(4_950, again.json().get("ndemNet").asLong());
        assertEquals(5_000, again.json().get("ndemGross").asLong());
        assertEquals(100, again.json().get("reverseFeeBp").asLong(), "les frais d'origine");
        assertEquals(ndem, bal(tv, Currency.NDEM));
        assertEquals(mboko, bal(tv, Currency.MBOKO));
        assertEquals(1, newTxns("CONVERT"));
        // une autre quantité ou un autre sens avec la même clé reste un conflit
        assertEquals("IDEM_CONFLICT", convert(tv, "M2N", 6, "p6-0001").reason());
        assertEquals("IDEM_CONFLICT", convert(tv, "N2M", 5, "p6-0001").reason());
    }

    /** Mutation 1 : une identité gelée ne convertit pas. */
    @Test
    void aFrozenIdentityCannotConvert() throws Exception {
        Tv tv = productionTv();
        long n = bal(tv, Currency.NDEM), m = bal(tv, Currency.MBOKO);
        jdbc.update("UPDATE wallet_identity SET frozen = TRUE WHERE holder = ?", tv.code());
        Reply r = convert(tv, "N2M", 1, "frz-0001");
        assertEquals(409, r.status());
        assertEquals("FROZEN", r.reason());
        assertEquals(n, bal(tv, Currency.NDEM));
        assertEquals(m, bal(tv, Currency.MBOKO));
        assertEquals(0, newTxns("CONVERT"));
    }

    /** Mutation 2 : une identité gelée n'envoie pas, mais reçoit (conception § 5.4). */
    @Test
    void aFrozenSenderCannotTransferButAFrozenRecipientReceives() throws Exception {
        Tv sender = productionTv(), recipient = productionTv();
        String code = receiveCode(recipient).json().get("code").asText();
        jdbc.update("UPDATE wallet_identity SET frozen = TRUE WHERE holder = ?", sender.code());
        Reply r = transfer(sender, code, "NDEM", 10, "frz-0002");
        assertEquals(409, r.status());
        assertEquals("FROZEN", r.reason());
        assertEquals(0, newTxns("TRANSFER"));
        assertEquals(0, count("SELECT COUNT(*) FROM wallet_recv_code WHERE code = ? AND used_at IS NOT NULL", code.replace("-", "")));
        jdbc.update("UPDATE wallet_identity SET frozen = FALSE WHERE holder = ?", sender.code());
        jdbc.update("UPDATE wallet_identity SET frozen = TRUE WHERE holder = ?", recipient.code());
        long before = bal(recipient, Currency.NDEM);
        assertEquals(200, transfer(sender, code, "NDEM", 10, "frz-0003").status(), "un destinataire gelé reçoit");
        assertEquals(before + 10, bal(recipient, Currency.NDEM));
        assertFalse(newTxns("TRANSFER") != 1);
    }
}
