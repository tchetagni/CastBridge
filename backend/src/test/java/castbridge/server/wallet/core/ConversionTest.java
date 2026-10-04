package castbridge.server.wallet.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ConversionTest {
    static final String ID = "AAAA-0000-0000-0001";
    static final AccountRef N = AccountRef.dispo(ID, Currency.NDEM), M = AccountRef.dispo(ID, Currency.MBOKO);

    @Test
    void sharedVectors() throws Exception {
        JsonNode cases = PotSplitVectorsTest.vectors().get("conversion");
        assertTrue(cases.size() >= 6);
        for (JsonNode c : cases) {
            WalletPolicy p = WalletPolicy.defaults().withRate(c.get("rate").asLong()).withReverseFeeBp(c.get("feeBp").asInt());
            Conversion.Quote q = Conversion.quote(Conversion.Direction.valueOf(c.get("dir").asText()), c.get("q").asLong(), p);
            String name = c.get("name").asText();
            assertEquals(c.get("gross").asLong(), q.ndemGross(), name);
            assertEquals(c.get("fee").asLong(), q.fee(), name);
            assertEquals(c.get("net").asLong(), q.ndemNet(), name);
        }
    }

    @Test
    void roundTripAtZeroFeeRestoresEveryAccount() {
        MemoryLedger l = new MemoryLedger();
        WalletPolicy p = WalletPolicy.defaults();
        l.post(Txn.grant(ID, Currency.NDEM, 5000, "g1"));
        var before = l.snapshot();
        l.post(Txn.convert(ID, Conversion.Direction.N2M, 3, p, "c1"));
        assertEquals(2000, l.balance(N));
        assertEquals(3, l.balance(M));
        l.post(Txn.convert(ID, Conversion.Direction.M2N, 3, p, "c2"));
        // tous les comptes identiques au départ (SYS:CONVERT revenu à 0 dans les deux monnaies)
        assertEquals(before, l.snapshot());
    }

    @Test
    void withFeePlayerLosesExactlyFAndSysFeeGainsF() {
        MemoryLedger l = new MemoryLedger();
        WalletPolicy p = WalletPolicy.defaults().withReverseFeeBp(150);
        l.post(Txn.grant(ID, Currency.NDEM, 5000, "g1"));
        l.post(Txn.convert(ID, Conversion.Direction.N2M, 2, p, "c1"));
        l.post(Txn.convert(ID, Conversion.Direction.M2N, 2, p, "c2"));
        long f = 30; // ceil(2000 × 150 / 10 000)
        assertEquals(5000 - f, l.balance(N));
        assertEquals(0, l.balance(M));
        assertEquals(f, l.balance(AccountRef.sys(AccountRef.FEE, Currency.NDEM)));
    }

    @Test
    void neverAGainOver1000Draws() {
        Random r = new Random(42);
        for (int i = 0; i < 1000; i++) {
            long rate = 1 + r.nextInt(3000);
            int fee = r.nextInt(2001);
            WalletPolicy p = WalletPolicy.defaults().withRate(rate).withReverseFeeBp(fee);
            long q = 1 + r.nextInt(20);
            long start = rate * q + r.nextInt(5000);
            MemoryLedger l = new MemoryLedger();
            l.post(Txn.grant(ID, Currency.NDEM, start, "g"));
            l.post(Txn.convert(ID, Conversion.Direction.N2M, q, p, "a"));
            l.post(Txn.convert(ID, Conversion.Direction.M2N, q, p, "b"));
            long f = oracleFee(rate * q, fee);
            assertEquals(start - f, l.balance(N), "N→M→N : perte exacte f, tirage " + i);
            assertEquals(f, l.balance(AccountRef.sys(AccountRef.FEE, Currency.NDEM)));
            assertTrue(l.balance(N) <= start, "jamais de gain");
            // sens inverse d'abord : MBOKO → NDEM puis NDEM → MBOKO ne rend jamais plus de MBOKO
            MemoryLedger l2 = new MemoryLedger();
            l2.post(Txn.grant(ID, Currency.MBOKO, q, "g"));
            l2.post(Txn.convert(ID, Conversion.Direction.M2N, q, p, "b"));
            long got = l2.balance(N);
            assertEquals(rate * q - oracleFee(rate * q, fee), got);
            long back = got / rate;
            if (back > 0) l2.post(Txn.convert(ID, Conversion.Direction.N2M, back, p, "a"));
            assertTrue(l2.balance(M) <= q, "jamais de gain en MBOKO");
            assertTrue(l2.balance(N) + l2.balance(M) * rate <= rate * q, "jamais de gain en valeur");
        }
    }

    /** Oracle indépendant : plafond exact en BigDecimal de gross × fee / 10 000. */
    static long oracleFee(long gross, int feeBp) {
        return new BigDecimal(gross).multiply(new BigDecimal(feeBp)).divide(new BigDecimal(10_000), 0, RoundingMode.CEILING).longValueExact();
    }

    @Test
    void feeIsCeilingNotFloor() {
        for (long gross : new long[] {999, 1000, 1001, 7, 123_456}) {
            for (int fee : new int[] {1, 7, 150, 1999, 2000}) {
                long rate = gross;
                Conversion.Quote q = Conversion.quote(Conversion.Direction.M2N, 1, WalletPolicy.defaults().withRate(rate).withReverseFeeBp(fee));
                assertEquals(oracleFee(gross, fee), q.fee(), "gross " + gross + " fee " + fee);
            }
        }
    }

    @Test
    void negativeReverseFeeIsRefusedAndOutOfRangeValuesToo() {
        WalletPolicy d = WalletPolicy.defaults();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> d.withReverseFeeBp(-1));
        assertTrue(ex.getMessage().contains("jamais être plus favorable"));
        assertThrows(IllegalArgumentException.class, () -> d.withReverseFeeBp(2001));
        assertThrows(IllegalArgumentException.class, () -> d.withRate(0));
        assertThrows(IllegalArgumentException.class, () -> d.withRate(1_000_001));
        assertEquals(1000, d.rate());
        assertEquals(0, d.reverseFeeBp());
    }

    @Test
    void ndemToMbokoWithLessThanRateIsInsufficient() {
        MemoryLedger l = new MemoryLedger();
        l.post(Txn.grant(ID, Currency.NDEM, 999, "g"));
        var before = l.snapshot();
        LedgerException ex = assertThrows(LedgerException.class, () -> l.post(Txn.convert(ID, Conversion.Direction.N2M, 1, WalletPolicy.defaults(), "c")));
        assertEquals(WalletReason.INSUFFICIENT, ex.reason());
        assertEquals(before, l.snapshot());
    }

    @Test
    void conversionIsIdempotentAndDirectionIsPartOfTheContent() {
        MemoryLedger l = new MemoryLedger();
        WalletPolicy p = WalletPolicy.defaults();
        l.post(Txn.grant(ID, Currency.NDEM, 3000, "g"));
        l.post(Txn.grant(ID, Currency.MBOKO, 5, "g2"));
        assertEquals(false, l.post(Txn.convert(ID, Conversion.Direction.N2M, 1, p, "conv:x:1")).replayed());
        assertEquals(true, l.post(Txn.convert(ID, Conversion.Direction.N2M, 1, p, "conv:x:1")).replayed());
        assertEquals(2000, l.balance(N));
        LedgerException ex = assertThrows(LedgerException.class, () -> l.post(Txn.convert(ID, Conversion.Direction.M2N, 1, p, "conv:x:1")));
        assertEquals(WalletReason.IDEM_CONFLICT, ex.reason());
        assertEquals(2000, l.balance(N));
    }

    @Test
    void anyHolderMayHoldMbokoByConversion() {
        // règle du propriétaire : détenir et recevoir est permis à tous ; seule la mise MBOKO est réservée à la production
        MemoryLedger l = new MemoryLedger();
        l.post(Txn.grant(ID, Currency.NDEM, 1000, "g"));
        l.post(Txn.convert(ID, Conversion.Direction.N2M, 1, WalletPolicy.defaults(), "c"));
        assertEquals(1, l.balance(M));
        assertTrue(StakeRules.refusal(Edition.TRIAL, Currency.MBOKO, false).isPresent()); // le grand livre ignore l'édition : la règle de mise est ailleurs
    }
}
