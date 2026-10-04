package castbridge.server.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import castbridge.server.wallet.core.AccountRef;
import castbridge.server.wallet.core.Currency;
import castbridge.server.wallet.core.LedgerException;
import castbridge.server.wallet.core.Txn;
import castbridge.server.wallet.core.WalletReason;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** Mutation 5 de l'audit : le règlement d'un blocage ne s'applique que sur un blocage encore OUVERT ; la garde {@code n != 1} est prouvée directement. */
class AuditFixLedgerTest extends WalletTestBase {
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager tm;
    @Autowired WalletModuleConfig.WalletClock walletClock;

    /** Un JdbcTemplate qui, juste avant la mise à jour du blocage, le ferme dans la MÊME transaction : la garde « AND state = 'OPEN' » doit alors compter 0 ligne. */
    private final class ClosingTemplate extends JdbcTemplate {
        ClosingTemplate() { super(jdbc.getDataSource()); }

        @Override
        public int update(String sql, Object... args) {
            if (sql.startsWith("UPDATE wallet_escrow SET state = ?")) super.update("UPDATE wallet_escrow SET state = 'REFUNDED' WHERE eid = ?", args[2]);
            return super.update(sql, args);
        }
    }

    @Test
    void mutation5_anEscrowClosedBetweenTheReadAndTheUpdateIsRefusedAndEverythingRollsBack() {
        JdbcLedger spy = new JdbcLedger(new ClosingTemplate(), tm, walletClock);
        String id = LedgerSuites.id("G", 5, 0), eid = "esc-" + id;
        spy.post(Txn.grant(id, Currency.NDEM, 100, "g5-" + id));
        spy.post(Txn.lock(id, Currency.NDEM, 40, 1, eid));
        assertEquals(60, spy.balance(AccountRef.dispo(id, Currency.NDEM)));
        long txns = jdbc.queryForObject("SELECT COUNT(*) FROM wallet_txn", Long.class);
        LedgerException e = assertThrows(LedgerException.class, () -> spy.post(Txn.refund(eid, id, Currency.NDEM, 40)));
        assertEquals(WalletReason.ESCROW_CLOSED, e.reason());
        assertEquals(txns, jdbc.queryForObject("SELECT COUNT(*) FROM wallet_txn", Long.class), "rien n'est posé");
        assertEquals(60, spy.balance(AccountRef.dispo(id, Currency.NDEM)), "le rendu annulé ne crédite pas");
        assertEquals(40, spy.balance(AccountRef.bloque(id, Currency.NDEM)));
    }

    @Autowired WalletRepository repo;
    @Autowired JdbcLedger ledger;

    /**
     * M4 : les quatre soldes et le seq viennent d'UNE instruction, le seq vient des versions des MÊMES lignes que les soldes : sous écritures concurrentes, chaque lecture est cohérente
     * (chaque écriture ajoute 1 au solde ET 1 à la version).
     */
    @Test
    void balancesAndSeqAreReadInOneConsistentView() throws Exception {
        String id = LedgerSuites.id("S", 4, 0);
        jdbc.update("INSERT INTO wallet_identity (holder, api_device_id, created_at) VALUES (?, 1, ?)", id, java.sql.Timestamp.from(java.time.Instant.now()));
        assertEquals(new WalletRepository.SnapshotRead(0, 0, 0, 0, 0), repo.snapshotRead(id), "identité sans écriture");
        ledger.post(Txn.adjust(id, Currency.NDEM, 5, "adj:admin:snap-0-" + id), "admin:test", id, "test");
        WalletRepository.SnapshotRead one = repo.snapshotRead(id);
        assertEquals(5, one.ndem());
        assertEquals(1, one.seq(), "une écriture : une version");
        repo.bumpIfChanged(id, "a");
        repo.bumpIfChanged(id, "a");
        assertEquals(one.seq(), repo.snapshotRead(id).seq(), "premier condensé : pas d'incrément");
        repo.bumpIfChanged(id, "b");
        repo.bumpIfChanged(id, "b");
        assertEquals(one.seq() + 1, repo.snapshotRead(id).seq(), "un condensé différent incrémente une seule fois");
        java.util.concurrent.atomic.AtomicBoolean stop = new java.util.concurrent.atomic.AtomicBoolean();
        Thread writer = new Thread(() -> {
            for (int i = 1; i < 400 && !stop.get(); i++) ledger.post(Txn.adjust(id, Currency.NDEM, 1, "adj:admin:snap-" + i + "-" + id), "admin:test", id, "test");
        });
        writer.start();
        try {
            for (int i = 0; i < 300; i++) {
                WalletRepository.SnapshotRead r = repo.snapshotRead(id);
                assertEquals(r.ndem() - 5, r.seq() - 2, "solde et seq lus ensemble : (solde - 5) = (seq - 1 version - 1 incrément)");
            }
        } finally {
            stop.set(true);
            writer.join();
        }
    }
}
