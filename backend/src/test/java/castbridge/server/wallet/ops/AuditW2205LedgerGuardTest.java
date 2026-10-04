package castbridge.server.wallet.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import castbridge.server.wallet.core.AccountRef;
import castbridge.server.wallet.core.Currency;
import castbridge.server.wallet.core.Ledger;
import castbridge.server.wallet.core.LedgerException;
import castbridge.server.wallet.core.Txn;
import castbridge.server.wallet.core.WalletReason;
import java.sql.Timestamp;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** M2 : la garde de {@code JdbcLedger.post(..., InTx)} s'exécute dans la MÊME transaction, après verrous, idempotence et découvert, avant les écritures, jamais sur un rejeu. */
class AuditW2205LedgerGuardTest extends OpsTestBase {
    private static final String CODE = "R0000000G0";

    private String id(int n) { return String.format("CA%02d-0000-0000-%04X", n, (int) (System.nanoTime() & 0xFFFF)); }

    private void freshCode(String holder) {
        jdbc.update("DELETE FROM wallet_recv_code WHERE code = ?", CODE);
        jdbc.update("INSERT INTO wallet_recv_code (code, holder, exp_at) VALUES (?, ?, ?)", CODE, holder, Timestamp.from(T0.plusSeconds(600)));
    }

    @Test
    void aGuardThatThrowsRollsBackItsOwnWritesAndTheTransaction() {
        String a = id(1), b = id(2);
        ledger.post(Txn.grant(a, Currency.NDEM, 100, "g-" + a));
        freshCode(b);
        long txns = txns("TRANSFER");
        LedgerException e = assertThrows(LedgerException.class, () -> ledger.post(Txn.transfer(a, b, Currency.NDEM, 10, "xfer-guard-1"), "tv", a, null, j -> {
            j.update("UPDATE wallet_recv_code SET used_at = ?, used_key = ? WHERE code = ?", Timestamp.from(T0), "xfer-guard-1", CODE);
            throw new LedgerException(WalletReason.CODE_UNKNOWN);
        }));
        assertEquals(WalletReason.CODE_UNKNOWN, e.reason());
        assertEquals(0, count("SELECT COUNT(*) FROM wallet_recv_code WHERE code = ? AND used_at IS NOT NULL", CODE), "l'écriture de la garde est annulée avec la transaction");
        assertEquals(txns, txns("TRANSFER"), "aucune transaction posée");
        assertEquals(100, ledger.balance(AccountRef.dispo(a, Currency.NDEM)));
    }

    @Test
    void theGuardRunsAfterTheLocksAndBeforeTheWritesAndOnlyOnce() {
        String a = id(3), b = id(4);
        ledger.post(Txn.grant(a, Currency.NDEM, 100, "g-" + a));
        AtomicInteger runs = new AtomicInteger();
        long[] seen = new long[2];
        Txn t = Txn.transfer(a, b, Currency.NDEM, 10, "xfer-guard-2");
        Ledger.Posted first = ledger.post(t, "tv", a, null, j -> {
            runs.incrementAndGet();
            seen[0] = j.queryForObject("SELECT COUNT(*) FROM wallet_txn WHERE idem_key = ?", Long.class, "xfer-guard-2");
        });
        assertFalse(first.replayed());
        assertEquals(1, runs.get());
        assertEquals(0, seen[0], "la garde passe AVANT l'écriture de la transaction");
        assertEquals(1, count("SELECT COUNT(*) FROM wallet_txn WHERE idem_key = ?", "xfer-guard-2"));
        Ledger.Posted replay = ledger.post(t, "tv", a, null, j -> runs.incrementAndGet());
        assertTrue(replay.replayed());
        assertEquals(1, runs.get(), "jamais exécutée sur un rejeu");
    }

    @Test
    void theGuardDoesNotRunWhenTheBalanceIsInsufficient() {
        String a = id(5), b = id(6);
        ledger.post(Txn.grant(a, Currency.NDEM, 5, "g-" + a));
        AtomicInteger runs = new AtomicInteger();
        LedgerException e = assertThrows(LedgerException.class, () -> ledger.post(Txn.transfer(a, b, Currency.NDEM, 10, "xfer-guard-3"), "tv", a, null, j -> runs.incrementAndGet()));
        assertEquals(WalletReason.INSUFFICIENT, e.reason());
        assertEquals(0, runs.get(), "le découvert est refusé avant la garde");
    }
}
