package castbridge.server.wallet.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class StakeRulesTest {
    @Test
    void mbokoOnlyInProductionUnlimitedOrGrace() {
        assertTrue(StakeRules.mayStake(Edition.PRODUCTION, Currency.MBOKO, false));
        assertTrue(StakeRules.mayStake(Edition.UNLIMITED, Currency.MBOKO, false));
        assertTrue(StakeRules.mayStake(Edition.NONE, Currency.MBOKO, true)); // production échue, dans la grâce
        assertFalse(StakeRules.mayStake(Edition.TRIAL, Currency.MBOKO, false));
        assertFalse(StakeRules.mayStake(Edition.NONE, Currency.MBOKO, false));
        assertFalse(StakeRules.mayStake(Edition.SUPER, Currency.MBOKO, false));
        assertEquals(Optional.of(WalletReason.TRIAL_NO_MBOKO), StakeRules.refusal(Edition.TRIAL, Currency.MBOKO, false));
        assertEquals("Mises MBOKO : version complète", WalletReason.TRIAL_NO_MBOKO.text());
    }

    @Test
    void ndemForEveryActivatedEditionButNone() {
        for (Edition e : new Edition[] {Edition.TRIAL, Edition.PRODUCTION, Edition.UNLIMITED, Edition.SUPER}) assertTrue(StakeRules.mayStake(e, Currency.NDEM, false), e.name());
        assertTrue(StakeRules.mayStake(Edition.NONE, Currency.NDEM, true));
        assertFalse(StakeRules.mayStake(Edition.NONE, Currency.NDEM, false));
        assertEquals(Optional.of(WalletReason.ACTIVATE), StakeRules.refusal(Edition.NONE, Currency.NDEM, false));
    }

    @Test
    void stakeBoundsAndTransferCapComeFromThePolicy() {
        WalletPolicy p = WalletPolicy.defaults();
        p.checkStake(Currency.NDEM, 1);
        p.checkStake(Currency.NDEM, 1000);
        p.checkStake(Currency.MBOKO, 100);
        assertThrows(LedgerException.class, () -> p.checkStake(Currency.NDEM, 1001));
        assertThrows(LedgerException.class, () -> p.checkStake(Currency.MBOKO, 101));
        assertThrows(LedgerException.class, () -> p.checkStake(Currency.MBOKO, 0));
        p.checkTransfer(Currency.NDEM, 10_000, 0);
        p.checkTransfer(Currency.MBOKO, 40, 60);
        LedgerException ex = assertThrows(LedgerException.class, () -> p.checkTransfer(Currency.MBOKO, 41, 60));
        assertEquals(WalletReason.DAILY_CAP, ex.reason());
        assertEquals("Plafond du jour atteint : réessayez demain", ex.getMessage());
    }

    @Test
    void reasonsCarryTheFrenchTextsOfTheDesign() {
        assertEquals("Solde insuffisant : 120 NDEM disponibles", WalletReason.INSUFFICIENT.text("120 NDEM"));
        assertEquals("Ce compte est lié à une autre TV", WalletReason.BOUND_OTHER_TV.text());
        assertEquals("Code expiré : demandez-en un nouveau", WalletReason.CODE_EXPIRED.text());
        assertEquals("Vérifiez l'heure de la TV", WalletReason.CLOCK.text());
        assertEquals("Mises suspendues pour maintenance : les parties sans mise restent ouvertes", WalletReason.STAKES_SUSPENDED.text());
    }

    @Test
    void accountsRefuseBadIdentities() {
        assertThrows(LedgerException.class, () -> AccountRef.dispo("pas-une-identite", Currency.NDEM));
        assertThrows(LedgerException.class, () -> AccountRef.sys("SYS:INCONNU", Currency.NDEM));
        AccountRef.dispo("AB12-CD34-EF56-GH78", Currency.NDEM);
    }

    @Test
    void transferToSelfIsRefused() {
        assertThrows(LedgerException.class, () -> Txn.transfer("AAAA-0000-0000-0001", "AAAA-0000-0000-0001", Currency.NDEM, 5, "k"));
    }
}
