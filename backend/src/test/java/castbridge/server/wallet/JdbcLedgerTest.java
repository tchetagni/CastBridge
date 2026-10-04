package castbridge.server.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import castbridge.server.wallet.core.AccountRef;
import castbridge.server.wallet.core.Currency;
import castbridge.server.wallet.core.Ledger;
import castbridge.server.wallet.core.LedgerException;
import castbridge.server.wallet.core.MemoryLedger;
import castbridge.server.wallet.core.Txn;
import castbridge.server.wallet.core.WalletReason;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Le grand livre dans la base (H2 en mode MySQL, mêmes migrations Flyway) : mêmes verdicts que l'oracle {@link MemoryLedger}, atomique, sans interblocage non rattrapé. */
class JdbcLedgerTest extends WalletTestBase {
    @Autowired JdbcLedger ledger;
    @Autowired WalletRepository repo;
    @Autowired JdbcTemplate jdbc;

    @Test
    void twoThousandRandomSuitesGiveTheSameVerdictsAndBalancesAsTheOracle() {
        LedgerSuites.Tally st = LedgerSuites.oracle(ledger, new MemoryLedger(), "O", 2_000);
        assertTrue(st.ops > 10_000, "assez d'opérations : " + st.ops);
        assertTrue(st.ok > 5_000 && st.replays > 200 && st.conflicts > 200 && st.insufficient > 500 && st.closed > 20, "couverture : ok " + st.ok + ", rejeux " + st.replays + ", conflits "
                + st.conflicts + ", découverts " + st.insufficient + ", blocages clos " + st.closed);
        assertEquals(0, repo.derivedMismatches(), "I-8 : soldes = Σ écritures");
    }

    @Test
    void sixteenThreadsOfFiveHundredCrossedOperationsKeepEveryInvariant() throws Exception {
        LedgerSuites.concurrency(ledger, "C", 16, 500, repo::derivedMismatches, ledger::retries);
    }

    @Test
    void aRefusalFromTheDatabaseRollsBackEverything() {
        String a = LedgerSuites.id("R", 1, 0), b = LedgerSuites.id("R", 1, 1);
        ledger.post(Txn.grant(a, Currency.NDEM, 50, "r-g-" + a));
        long txns = jdbc.queryForObject("SELECT COUNT(*) FROM wallet_txn", Long.class), entries = jdbc.queryForObject("SELECT COUNT(*) FROM wallet_entry", Long.class);
        // le contrôle du cœur est contourné : c'est le CHECK (solde >= 0) de la base qui doit refuser, et TOUT s'annule (transaction, écritures, autres comptes)
        Txn overdraft = Txn.transfer(a, b, Currency.NDEM, 80, "r-over-" + a);
        assertThrows(DataIntegrityViolationException.class, () -> ledger.postWithoutBalanceCheck(overdraft));
        assertEquals(txns, jdbc.queryForObject("SELECT COUNT(*) FROM wallet_txn", Long.class));
        assertEquals(entries, jdbc.queryForObject("SELECT COUNT(*) FROM wallet_entry", Long.class));
        assertEquals(50, ledger.balance(AccountRef.dispo(a, Currency.NDEM)));
        assertEquals(0, ledger.balance(AccountRef.dispo(b, Currency.NDEM)));
        assertEquals(0, repo.derivedMismatches());
        // la clé n'a pas été consommée
        LedgerException ex = assertThrows(LedgerException.class, () -> ledger.post(overdraft));
        assertEquals(WalletReason.INSUFFICIENT, ex.reason());
        assertFalse(ledger.post(Txn.transfer(a, b, Currency.NDEM, 20, "r-over-" + a)).replayed());
    }

    @Test
    void sixteenThreadsPostingTheSameOperationCreditItOnce() throws Exception {
        String a = LedgerSuites.id("D", 1, 0);
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Ledger.Posted>> fs = new ArrayList<>();
        for (int i = 0; i < 16; i++) fs.add(pool.submit(() -> {
            go.await();
            return ledger.post(Txn.grant(a, Currency.MBOKO, 7, "d-same-" + a));
        }));
        go.countDown();
        int fresh = 0;
        for (Future<Ledger.Posted> f : fs) if (!f.get(120, TimeUnit.SECONDS).replayed()) fresh++;
        pool.shutdown();
        assertEquals(1, fresh, "une seule pose réelle, les quinze autres sont des rejeux");
        assertEquals(7, ledger.balance(AccountRef.dispo(a, Currency.MBOKO)));
    }

    @Test
    void derivedBalanceCheckSeesACorruptedBalance() {
        String a = LedgerSuites.id("V", 1, 0);
        ledger.post(Txn.grant(a, Currency.NDEM, 10, "v-g-" + a));
        assertEquals(0, repo.derivedMismatches());
        jdbc.update("UPDATE wallet_balance SET balance = balance + 1 WHERE account_id = (SELECT id FROM wallet_account WHERE holder = ? AND cur = 'NDEM' AND pocket = 'DISPO')", a);
        assertEquals(1, repo.derivedMismatches());
        jdbc.update("UPDATE wallet_balance SET balance = balance - 1 WHERE account_id = (SELECT id FROM wallet_account WHERE holder = ? AND cur = 'NDEM' AND pocket = 'DISPO')", a);
        assertEquals(0, repo.derivedMismatches());
    }
}
