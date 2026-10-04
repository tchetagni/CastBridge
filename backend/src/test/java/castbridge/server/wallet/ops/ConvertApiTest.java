package castbridge.server.wallet.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import castbridge.server.wallet.core.Currency;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** {@code POST /api/v1/wallet/convert} : deux sens à 1 000 NDEM = 1 MBOKO, frais lus dans {@code wallet_policy} à chaque appel, idempotence, interrupteur. */
class ConvertApiTest extends OpsTestBase {

    @AfterEach
    void restorePolicy() {
        setPolicy("convert.rate", 1000);
        setPolicy("convert.reverseFeeBp", 0);
        setPolicy("switch.convert", 1);
    }

    /** Production (1 000 NDEM, 10 MBOKO) + 9 000 NDEM : 10 000 NDEM, 10 MBOKO. */
    private Tv rich() throws Exception {
        Tv tv = productionTv();
        adminGrant(tv.code(), "NDEM", 9_000);
        assertEquals(10_000, bal(tv, Currency.NDEM));
        return tv;
    }

    @Test
    void ndemToMboko_thenBack_returnsToTheStartingBalances() throws Exception {
        Tv tv = rich();
        Reply up = convert(tv, "N2M", 5, "cvt-0001");
        assertEquals(200, up.status(), up.json().toString());
        assertEquals(1000, up.json().get("rate").asLong());
        assertEquals(0, up.json().get("reverseFeeBp").asInt());
        assertEquals(5000, up.json().get("ndemGross").asLong());
        assertEquals(0, up.json().get("fee").asLong());
        assertTrue(up.json().get("snapshot").asText().startsWith("cbw1."));
        assertEquals(5_000, bal(tv, Currency.NDEM));
        assertEquals(15, bal(tv, Currency.MBOKO));
        Reply down = convert(tv, "M2N", 5, "cvt-0002");
        assertEquals(200, down.status(), down.json().toString());
        assertEquals(0, down.json().get("fee").asLong());
        assertEquals(5_000, down.json().get("ndemNet").asLong());
        assertEquals(10_000, bal(tv, Currency.NDEM), "soldes de départ retrouvés (frais 0 %)");
        assertEquals(10, bal(tv, Currency.MBOKO));
        assertEquals(0, sys("SYS:FEE", "NDEM"));
        assertEquals(2, newTxns("CONVERT"));
        assertReconciled();
    }

    @Test
    void aReverseFeeOf200BpIsReadFromThePolicyAtEveryCall() throws Exception {
        Tv tv = rich();
        long feeBefore = sys("SYS:FEE", "NDEM");
        setPolicy("convert.reverseFeeBp", 200);
        Reply r = convert(tv, "M2N", 5, "cvt-0010");
        assertEquals(200, r.status(), r.json().toString());
        assertEquals(200, r.json().get("reverseFeeBp").asInt());
        assertEquals(5000, r.json().get("ndemGross").asLong());
        assertEquals(100, r.json().get("fee").asLong());
        assertEquals(4900, r.json().get("ndemNet").asLong());
        assertEquals(10_000 + 4_900, bal(tv, Currency.NDEM));
        assertEquals(5, bal(tv, Currency.MBOKO));
        assertEquals(feeBefore + 100, sys("SYS:FEE", "NDEM"), "SYS:FEE +100");
        // le sens N→M n'a jamais de frais, et un taux changé s'applique au prochain appel
        setPolicy("convert.rate", 500);
        Reply up = convert(tv, "N2M", 2, "cvt-0011");
        assertEquals(500, up.json().get("rate").asLong());
        assertEquals(1000, up.json().get("ndemGross").asLong());
        assertEquals(0, up.json().get("fee").asLong());
        assertReconciled();
    }

    @Test
    void theSameIdemWithTheOtherDirectionIsAConflictAndTheSameRequestIsAReplay() throws Exception {
        Tv tv = rich();
        Reply first = convert(tv, "N2M", 2, "cvt-0020");
        assertEquals(200, first.status());
        assertFalse(first.json().get("replayed").asBoolean());
        long n = bal(tv, Currency.NDEM), m = bal(tv, Currency.MBOKO);
        Reply replay = convert(tv, "N2M", 2, "cvt-0020");
        assertEquals(200, replay.status());
        assertTrue(replay.json().get("replayed").asBoolean());
        assertEquals(n, bal(tv, Currency.NDEM));
        assertEquals(m, bal(tv, Currency.MBOKO));
        Reply other = convert(tv, "M2N", 2, "cvt-0020");
        assertEquals(409, other.status());
        assertEquals("IDEM_CONFLICT", other.reason());
        assertEquals(409, convert(tv, "N2M", 3, "cvt-0020").status(), "autre quantité, même clé");
        assertEquals(n, bal(tv, Currency.NDEM));
        assertEquals(1, newTxns("CONVERT"));
    }

    @Test
    void insufficientBalancesBoundsAndTheSwitch() throws Exception {
        Tv tv = productionTv();   // 1 000 NDEM, 10 MBOKO
        Reply poor = convert(tv, "N2M", 2, "cvt-0030");
        assertEquals(409, poor.status());
        assertEquals("INSUFFICIENT", poor.reason());
        assertEquals("INSUFFICIENT", convert(tv, "M2N", 11, "cvt-0031").reason());
        assertEquals(400, convert(tv, "N2M", 0, "cvt-0032").status());
        assertEquals(400, convert(tv, "N2M", 2_000_000_000L, "cvt-0033").status());
        assertEquals(400, convert(tv, "SIDEWAYS", 1, "cvt-0034").status());
        assertEquals(400, convert(tv, "M2N", 1, "x").status());
        assertEquals(1000, bal(tv, Currency.NDEM));
        setPolicy("switch.convert", 0);
        Reply off = convert(tv, "M2N", 1, "cvt-0035");
        assertEquals(409, off.status());
        assertEquals("CONVERT_SUSPENDED", off.reason());
        assertEquals(10, bal(tv, Currency.MBOKO));
        setPolicy("switch.convert", 1);
        assertEquals(200, convert(tv, "M2N", 1, "cvt-0036").status());
    }

    @Test
    void aTrialTvConvertsBothWays() throws Exception {
        Tv tv = trialTv();
        adminGrant(tv.code(), "NDEM", 2_000);   // 2 100 NDEM
        assertEquals(200, convert(tv, "N2M", 2, "cvt-0040").status(), "l'essai peut détenir des MBOKO par conversion");
        assertEquals(2, bal(tv, Currency.MBOKO));
        assertEquals(200, convert(tv, "M2N", 1, "cvt-0041").status());
        assertEquals(1, bal(tv, Currency.MBOKO));
        assertReconciled();
    }

    @Test
    void anotherDevicesTokenIsRefused() throws Exception {
        Tv tv = rich();
        Reply r = postJson(registerTv(), "/api/v1/wallet/convert", req(tv).put("dir", "N2M").put("q", 1).put("idem", "cvt-0050"));
        assertEquals(403, r.status());
        assertEquals("BOUND_OTHER_TV", r.reason());
        assertEquals(401, postJson(null, "/api/v1/wallet/convert", req(tv).put("dir", "N2M").put("q", 1).put("idem", "cvt-0051")).status());
        assertEquals(10_000, bal(tv, Currency.NDEM));
    }
}
