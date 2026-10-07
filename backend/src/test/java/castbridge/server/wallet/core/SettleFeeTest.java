package castbridge.server.wallet.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Les frais de plateforme d'un règlement (games-G2) : ils sortent de la cagnotte vers {@code SYS:FEE} dans la MÊME transaction, la cagnotte revient à 0, rien ne se crée ni ne se perd ; seul un
 * règlement peut créditer {@code SYS:FEE} (le jeton de résultat ne peut pas en profiter pour enrichir quelqu'un d'autre). Frais nuls : exactement le règlement d'avant.
 */
class SettleFeeTest {
    static final String A = "AAAA-0000-0000-0001", B = "BBBB-0000-0000-0002", C = "CCCC-0000-0000-0003";

    private static MemoryLedger duel(long per) {
        MemoryLedger l = new MemoryLedger();
        l.post(Txn.grant(A, Currency.NDEM, 1_000, "ga")); l.post(Txn.grant(B, Currency.NDEM, 1_000, "gb"));
        l.post(Txn.lock(A, Currency.NDEM, per, 1, "ea")); l.post(Txn.lock(B, Currency.NDEM, per, 1, "eb"));
        return l;
    }

    private static List<Settlement.Line> lines(long per, long payA, long payB) {
        return List.of(new Settlement.Line("ea", A, per, per, payA), new Settlement.Line("eb", B, per, per, payB));
    }

    @Test
    void theFeeLeavesThePotForTheFeeAccountAndEverythingBalances() {
        MemoryLedger l = duel(100);
        // le gagnant B reçoit les deux mises moins 5 % de la cagnotte : 200 − 10 = 190
        l.post(Txn.settle("r1", lines(100, 0, 190), Currency.NDEM, 10));
        assertEquals(900, l.balance(AccountRef.dispo(A, Currency.NDEM)));
        assertEquals(1_090, l.balance(AccountRef.dispo(B, Currency.NDEM)));
        assertEquals(10, l.balance(AccountRef.sys(AccountRef.FEE, Currency.NDEM)));
        assertEquals(0, l.balance(AccountRef.sys(AccountRef.POT, Currency.NDEM)), "la cagnotte revient à 0");
        assertEquals(0, l.sum(Currency.NDEM), "I-1 : la somme de tous les comptes reste nulle");
        assertEquals(0, l.balance(AccountRef.bloque(A, Currency.NDEM)) + l.balance(AccountRef.bloque(B, Currency.NDEM)));
    }

    @Test
    void noFeeIsExactlyTheOldSettlement() {
        MemoryLedger l = duel(100);
        Txn plain = Txn.settle("r2", lines(100, 0, 200), Currency.NDEM);
        assertEquals(plain.contentSha(), Txn.settle("r2", lines(100, 0, 200), Currency.NDEM, 0).contentSha());
        l.post(plain);
        assertEquals(1_100, l.balance(AccountRef.dispo(B, Currency.NDEM)));
        assertEquals(0, l.balance(AccountRef.sys(AccountRef.FEE, Currency.NDEM)));
    }

    @Test
    void aFeeThatDoesNotMatchThePayoutsIsRefusedBecauseThePotMustReturnToZero() {
        MemoryLedger l = duel(100);
        // frais de 10 mais le gagnant reçoit 200 : la cagnotte ne reviendrait pas à 0 (création de 10)
        assertThrows(LedgerException.class, () -> l.post(Txn.settle("r3", lines(100, 0, 200), Currency.NDEM, 10)));
        // frais de 10 et le gagnant reçoit 180 : 10 disparaîtraient
        assertThrows(LedgerException.class, () -> l.post(Txn.settle("r4", lines(100, 0, 180), Currency.NDEM, 10)));
        assertEquals(0, l.balance(AccountRef.sys(AccountRef.FEE, Currency.NDEM)));
        assertEquals(100, l.balance(AccountRef.bloque(A, Currency.NDEM)), "rien n'a bougé : les blocages sont toujours ouverts");
        assertThrows(LedgerException.class, () -> Txn.settle("r5", lines(100, 0, 200), Currency.NDEM, -1));
    }

    @Test
    void theFeeAccountIsOnlyEverCreditedByASettlementNeverDebited() {
        MemoryLedger l = duel(100);
        // un règlement forgé à la main qui DÉBITE SYS:FEE (10) pour payer 10 de plus au gagnant : refusé (« aucun gain hors des blocages listés »), rien ne bouge
        AccountRef pot = AccountRef.sys(AccountRef.POT, Currency.NDEM), fee = AccountRef.sys(AccountRef.FEE, Currency.NDEM);
        Txn mint = new Txn(TxnKind.SETTLE, "settle:mint", List.of(
                new Entry(AccountRef.bloque(A, Currency.NDEM), -100), new Entry(AccountRef.bloque(B, Currency.NDEM), -100),
                new Entry(pot, 100), new Entry(pot, 100), new Entry(pot, -210), new Entry(AccountRef.dispo(B, Currency.NDEM), 210),
                new Entry(fee, -10), new Entry(pot, 10)), List.of("ea", "eb"));
        assertThrows(LedgerException.class, () -> l.post(mint));
        assertEquals(0, l.balance(fee));
        assertEquals(100, l.balance(AccountRef.bloque(A, Currency.NDEM)));
        // et un blocage ou un rendu ne crédite jamais SYS:FEE
        Txn refundToFee = new Txn(TxnKind.ESCROW_REFUND, "refund:ea", List.of(new Entry(AccountRef.bloque(A, Currency.NDEM), -100), new Entry(fee, 100)), List.of("ea"));
        assertThrows(LedgerException.class, () -> l.post(refundToFee));
        assertEquals(0, l.balance(fee));
    }

    @Test
    void aThirdPartyNeverReceivesAnythingFromASettlement() {
        MemoryLedger l = duel(50);
        l.post(Txn.grant(C, Currency.NDEM, 500, "gc"));
        // un tiers (C) ne peut pas recevoir une part d'un règlement dont il n'a pas de blocage
        Txn theft = Txn.settle("r6", List.of(new Settlement.Line("ea", A, 50, 50, 0), new Settlement.Line("eb", B, 50, 50, 90)), Currency.NDEM, 10);
        l.post(theft);
        assertEquals(1_040, l.balance(AccountRef.dispo(B, Currency.NDEM)));
        assertEquals(500, l.balance(AccountRef.dispo(C, Currency.NDEM)));
    }

    @Test
    void fiveThousandRandomDuelsWithRandomFeesNeverCreateOrLoseAnything() {
        Random r = new Random(2026);
        for (int i = 0; i < 5_000; i++) {
            long per = 1 + r.nextInt(200);
            MemoryLedger l = duel(per);
            long fee = 0;
            long pa, pb;
            switch (r.nextInt(4)) {
                case 0 -> { fee = (2 * per) * r.nextInt(2_001) / 10_000; pa = 2 * per - fee; pb = 0; }
                case 1 -> { fee = (2 * per) * r.nextInt(2_001) / 10_000; pa = 0; pb = 2 * per - fee; }
                case 2 -> { pa = per; pb = per; }
                default -> { pa = 0; pb = 0; }
            }
            if (pa == 0 && pb == 0) {
                l.post(Txn.refund("ea", A, Currency.NDEM, per)); l.post(Txn.refund("eb", B, Currency.NDEM, per));
            } else {
                l.post(Txn.settle("r" + i, lines(per, pa, pb), Currency.NDEM, fee));
            }
            long players = l.balance(AccountRef.dispo(A, Currency.NDEM)) + l.balance(AccountRef.dispo(B, Currency.NDEM));
            long feeAccount = l.balance(AccountRef.sys(AccountRef.FEE, Currency.NDEM));
            assertEquals(2_000, players + feeAccount, "tirage " + i + " : ce que les joueurs détiennent plus les frais = ce qu'ils avaient");
            assertTrue(feeAccount >= 0 && l.balance(AccountRef.sys(AccountRef.POT, Currency.NDEM)) == 0);
            assertEquals(0, l.sum(Currency.NDEM));
        }
    }
}
