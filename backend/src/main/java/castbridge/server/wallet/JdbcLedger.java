package castbridge.server.wallet;

import castbridge.server.wallet.core.AccountRef;
import castbridge.server.wallet.core.Currency;
import castbridge.server.wallet.core.Entry;
import castbridge.server.wallet.core.Ledger;
import castbridge.server.wallet.core.LedgerException;
import castbridge.server.wallet.core.Pocket;
import castbridge.server.wallet.core.Txn;
import castbridge.server.wallet.core.TxnKind;
import castbridge.server.wallet.core.WalletReason;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Le grand livre de {@code core} tenu dans MySQL (conception W22 § 3.1) : mêmes invariants et même sémantique que {@link castbridge.server.wallet.core.MemoryLedger} (l'oracle).
 * UNE transaction SQL par {@link #post} : verrous {@code SELECT … FOR UPDATE} sur {@code wallet_balance} dans l'ordre CROISSANT des {@code account_id} (jamais d'interblocage entre
 * deux poses), clé d'idempotence {@code UNIQUE(idem_key)} + empreinte comparée sur doublon, écritures, puis soldes ; toute violation annule TOUT. Un interblocage ou une course sur
 * une clé est rattrapé par un réessai borné (3). {@link #post} ne se compose pas dans une transaction extérieure (le réessai serait faux) : chaque appel est atomique à lui seul.
 * Montants : entiers (BIGINT), jamais de flottant.
 */
@Component
@WalletModuleConfig.Enabled
public class JdbcLedger implements Ledger {
    static final int MAX_ATTEMPTS = 3;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final TransactionTemplate accountTx;
    private final WalletModuleConfig.WalletClock clock;
    private final Map<String, Long> accountIds = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong retries = new java.util.concurrent.atomic.AtomicLong();

    public JdbcLedger(JdbcTemplate jdbc, PlatformTransactionManager tm, WalletModuleConfig.WalletClock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.tx = new TransactionTemplate(tm);
        this.accountTx = new TransactionTemplate(tm);
        this.accountTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Nombre de réessais depuis le démarrage (interblocages, délais de verrou, courses sur une clé) : métrique d'exploitation, et preuve de test qu'aucun interblocage n'a eu lieu. */
    public long retries() { return retries.get(); }

    @Override public Posted post(Txn txn) { return post(txn, "system", null, null); }

    /** {@code actor} : tv | play | admin:&lt;nom&gt; | system ; {@code holder} et {@code reason} sont de simples étiquettes d'audit (reason : motif de l'administrateur). */
    public Posted post(Txn txn, String actor, String holder, String reason) { return attempt(txn, actor, holder, reason, true); }

    /** Ceinture et bretelles : saute le contrôle de découvert du cœur pour prouver que le CHECK de la base refuse aussi, et que tout s'annule. Tests seulement. */
    Posted postWithoutBalanceCheck(Txn txn) { return attempt(txn, "system", null, null, false); }

    private Posted attempt(Txn txn, String actor, String holder, String reason, boolean checkBalances) {
        RuntimeException last = null;
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            try {
                return postOnce(txn, actor, holder, reason, checkBalances);
            } catch (ConcurrencyFailureException | DuplicateKeyException e) {
                retries.incrementAndGet();
                last = e;   // interblocage, délai de verrou ou course sur une clé unique : on recommence (la relecture voit le gagnant : rejeu ou conflit)
            }
        }
        throw last;
    }

    private Posted postOnce(Txn txn, String actor, String holder, String reason, boolean checkBalances) {
        TreeMap<AccountRef, Long> net = new TreeMap<>();
        for (Entry e : txn.entries()) net.merge(e.account(), e.amount(), Long::sum);
        Map<AccountRef, Long> ids = ensureAccounts(net.keySet());
        return tx.execute(status -> {
            // 1. verrous dans l'ordre croissant des account_id : toute pose prend les mêmes verrous dans le même ordre
            List<AccountRef> byId = new ArrayList<>(net.keySet());
            byId.sort((a, b) -> Long.compare(ids.get(a), ids.get(b)));
            Map<AccountRef, Long> before = new HashMap<>();
            for (AccountRef a : byId) {
                before.put(a, jdbc.queryForObject("SELECT balance FROM wallet_balance WHERE account_id = ? FOR UPDATE", Long.class, ids.get(a)));
            }
            // 2. idempotence (I-4) : même clé + même empreinte = rejeu ; même clé + autre empreinte = conflit
            List<String> prior = jdbc.queryForList("SELECT content_sha FROM wallet_txn WHERE idem_key = ?", String.class, txn.idemKey());
            if (!prior.isEmpty()) {
                if (prior.get(0).equals(txn.contentSha())) return new Posted(true);
                throw new LedgerException(WalletReason.IDEM_CONFLICT);
            }
            // 3. forme des blocages (I-5, I-6) puis découvert (I-2)
            Map<String, EscrowState> escrowUpdates = checkShape(txn, net);
            if (checkBalances) {
                for (Map.Entry<AccountRef, Long> n : net.entrySet()) {
                    AccountRef a = n.getKey();
                    if (!a.isSystem() && before.get(a) + n.getValue() < 0) {
                        throw new LedgerException(WalletReason.INSUFFICIENT, WalletReason.INSUFFICIENT.text(before.get(a) + " " + a.currency()));
                    }
                }
            }
            // 4. écritures : transaction, écritures immuables, soldes, blocages
            Timestamp now = Timestamp.from(clock.now());
            GeneratedKeyHolder key = new GeneratedKeyHolder();
            jdbc.update(con -> {
                PreparedStatement ps = con.prepareStatement("INSERT INTO wallet_txn (kind, idem_key, content_sha, holder, ref, reason, created_at, actor) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, txn.kind().name());
                ps.setString(2, txn.idemKey());
                ps.setString(3, txn.contentSha());
                ps.setString(4, holder);
                ps.setString(5, txn.refs().isEmpty() ? null : trunc(String.join(",", txn.refs()), 64));
                ps.setString(6, reason == null ? null : trunc(reason, 200));
                ps.setTimestamp(7, now);
                ps.setString(8, trunc(actor, 32));
                return ps;
            }, key);
            long txnId = key.getKey().longValue();
            jdbc.batchUpdate("INSERT INTO wallet_entry (txn_id, account_id, amount) VALUES (?, ?, ?)", txn.entries(), txn.entries().size(),
                    (ps, e) -> { ps.setLong(1, txnId); ps.setLong(2, ids.get(e.account())); ps.setLong(3, e.amount()); });
            for (Map.Entry<AccountRef, Long> n : net.entrySet()) {
                if (n.getValue() == 0) continue;
                jdbc.update("UPDATE wallet_balance SET balance = ?, version = version + 1 WHERE account_id = ?", before.get(n.getKey()) + n.getValue(), ids.get(n.getKey()));
            }
            for (EscrowState s : escrowUpdates.values()) {
                if (txn.kind() == TxnKind.ESCROW_LOCK) {
                    jdbc.update("INSERT INTO wallet_escrow (eid, holder, cur, amount, state, created_at) VALUES (?, ?, ?, ?, 'OPEN', ?)", s.eid(), s.holder(), s.currency().name(), s.amount(), now);
                } else {
                    String rid = txn.kind() == TxnKind.SETTLE && txn.idemKey().startsWith("settle:") ? txn.idemKey().substring(7) : null;
                    int n = jdbc.update("UPDATE wallet_escrow SET state = ?, settled_rid = ? WHERE eid = ? AND state = 'OPEN'", s.status().name(), rid, s.eid());
                    if (n != 1) throw new LedgerException(WalletReason.ESCROW_CLOSED);
                }
            }
            return new Posted(false);
        });
    }

    private static String trunc(String s, int max) { return s.length() <= max ? s : s.substring(0, max); }

    /** Crée les comptes manquants (compte + solde à 0 dans la même petite transaction) ; une course sur l'unicité est rattrapée par une relecture. */
    private Map<AccountRef, Long> ensureAccounts(Set<AccountRef> accounts) {
        Map<AccountRef, Long> ids = new HashMap<>();
        for (AccountRef a : accounts) {
            Long id = accountIds.get(a.key());
            if (id == null) {
                id = findAccount(a);
                if (id == null) {
                    try {
                        id = accountTx.execute(s -> {
                            GeneratedKeyHolder k = new GeneratedKeyHolder();
                            jdbc.update(con -> {
                                PreparedStatement ps = con.prepareStatement("INSERT INTO wallet_account (holder, cur, pocket) VALUES (?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
                                ps.setString(1, a.holder());
                                ps.setString(2, a.currency().name());
                                ps.setString(3, a.pocket().name());
                                return ps;
                            }, k);
                            long newId = k.getKey().longValue();
                            jdbc.update("INSERT INTO wallet_balance (account_id, balance, version, floor_zero) VALUES (?, 0, 0, ?)", newId, !a.isSystem());
                            return newId;
                        });
                    } catch (DuplicateKeyException e) {
                        id = findAccount(a);
                    }
                }
                if (id == null) throw new IllegalStateException("compte introuvable après création : " + a.key());
                accountIds.put(a.key(), id);
            }
            ids.put(a, id);
        }
        return ids;
    }

    private Long findAccount(AccountRef a) {
        List<Long> r = jdbc.queryForList("SELECT id FROM wallet_account WHERE holder = ? AND cur = ? AND pocket = ?", Long.class, a.holder(), a.currency().name(), a.pocket().name());
        return r.isEmpty() ? null : r.get(0);
    }

    @Override public long balance(AccountRef account) {
        List<Long> r = jdbc.queryForList("SELECT b.balance FROM wallet_account a JOIN wallet_balance b ON b.account_id = a.id WHERE a.holder = ? AND a.cur = ? AND a.pocket = ?", Long.class,
                account.holder(), account.currency().name(), account.pocket().name());
        return r.isEmpty() ? 0 : r.get(0);
    }

    @Override public long sum(Currency currency) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(b.balance), 0) FROM wallet_account a JOIN wallet_balance b ON b.account_id = a.id WHERE a.cur = ?", Long.class, currency.name());
    }

    @Override public Optional<EscrowState> escrow(String eid) {
        List<EscrowState> r = jdbc.query("SELECT eid, holder, cur, amount, state FROM wallet_escrow WHERE eid = ?", (rs, i) -> escrowOf(rs), eid);
        return r.stream().findFirst();
    }

    private static EscrowState escrowOf(ResultSet rs) throws java.sql.SQLException {
        return new EscrowState(rs.getString("eid"), rs.getString("holder"), Currency.valueOf(rs.getString("cur")), rs.getLong("amount"), EscrowStatus.valueOf(rs.getString("state")));
    }

    /** Dernière écriture (plus grand identifiant) des comptes de joueur de cette identité : le {@code seq} de l'instantané. 0 si aucune. */
    public long lastEntryId(String holder) {
        Long v = jdbc.queryForObject("SELECT MAX(e.id) FROM wallet_entry e JOIN wallet_account a ON a.id = e.account_id WHERE a.holder = ?", Long.class, holder);
        return v == null ? 0 : v;
    }

    // ---- règles de forme : port fidèle de MemoryLedger.checkShape, l'état des blocages vient de la base (verrouillé) ----

    private LedgerException bad(String why) { return new LedgerException(WalletReason.BAD_TXN, "Transaction invalide : " + why); }

    private EscrowState lockedEscrow(String eid) {
        List<EscrowState> r = jdbc.query("SELECT eid, holder, cur, amount, state FROM wallet_escrow WHERE eid = ? FOR UPDATE", (rs, i) -> escrowOf(rs), eid);
        return r.isEmpty() ? null : r.get(0);
    }

    private Map<String, EscrowState> checkShape(Txn txn, Map<AccountRef, Long> net) {
        boolean escrowKind = txn.kind() == TxnKind.ESCROW_LOCK || txn.kind() == TxnKind.SETTLE || txn.kind() == TxnKind.ESCROW_REFUND;
        if (!escrowKind) {
            if (!txn.refs().isEmpty()) throw bad("Références de blocage interdites pour ce genre");
            for (AccountRef a : net.keySet()) {
                if (a.pocket() == Pocket.BLOQUE) throw bad("Seuls les blocages touchent la poche BLOQUE");
                if (a.holder().equals(AccountRef.POT)) throw bad("Seuls les règlements touchent SYS:POT");
            }
            return Map.of();
        }
        if (txn.refs().isEmpty() || new HashSet<>(txn.refs()).size() != txn.refs().size()) throw bad("Références de blocage absentes ou en double");
        Currency cur = net.keySet().iterator().next().currency();
        for (AccountRef a : net.keySet()) if (a.currency() != cur) throw bad("Un blocage ne porte que sur une monnaie");
        Map<String, EscrowState> updates = new LinkedHashMap<>();
        if (txn.kind() == TxnKind.ESCROW_LOCK) {
            if (txn.refs().size() != 1) throw bad("Un blocage à la fois");
            String eid = txn.refs().get(0);
            if (!jdbc.queryForList("SELECT eid FROM wallet_escrow WHERE eid = ?", String.class, eid).isEmpty()) throw new LedgerException(WalletReason.IDEM_CONFLICT, "Ce blocage existe déjà : " + eid);
            if (net.size() != 2) throw bad("Blocage : deux comptes exactement");
            String holder = null;
            long locked = 0;
            for (Map.Entry<AccountRef, Long> n : net.entrySet()) {
                AccountRef a = n.getKey();
                if (a.isSystem()) throw bad("Blocage : comptes de joueur seulement");
                if (holder != null && !holder.equals(a.holder())) throw bad("Blocage : un seul titulaire");
                holder = a.holder();
                if (a.pocket() == Pocket.BLOQUE) locked = n.getValue();
                else if (a.pocket() != Pocket.DISPO) throw bad("Blocage : de DISPO vers BLOQUE");
            }
            if (locked < 1 || !Long.valueOf(-locked).equals(net.get(AccountRef.dispo(holder, cur)))) throw bad("Blocage : DISPO −a et BLOQUE +a");
            updates.put(eid, new EscrowState(eid, holder, cur, locked, EscrowStatus.OPEN));
            return updates;
        }
        Map<String, Long> unlock = new HashMap<>();
        for (String eid : txn.refs()) {
            EscrowState s = lockedEscrow(eid);
            if (s == null) throw new LedgerException(WalletReason.ESCROW_UNKNOWN);
            if (s.status() != EscrowStatus.OPEN) throw new LedgerException(WalletReason.ESCROW_CLOSED);
            if (s.currency() != cur) throw bad("Monnaie différente de celle du blocage");
            unlock.merge(s.holder(), s.amount(), Long::sum);
            updates.put(eid, new EscrowState(eid, s.holder(), cur, s.amount(), txn.kind() == TxnKind.SETTLE ? EscrowStatus.SETTLED : EscrowStatus.REFUNDED));
        }
        Set<String> allowed = new HashSet<>(unlock.keySet());
        for (Map.Entry<AccountRef, Long> n : net.entrySet()) {
            AccountRef a = n.getKey();
            if (a.holder().equals(AccountRef.POT)) {
                if (txn.kind() != TxnKind.SETTLE || n.getValue() != 0) throw bad("SYS:POT doit revenir à 0 dans le règlement");
            } else if (a.isSystem() || !allowed.contains(a.holder()) || (a.pocket() != Pocket.DISPO && a.pocket() != Pocket.BLOQUE)) {
                throw bad("Aucun gain hors des blocages listés");
            }
        }
        for (Map.Entry<String, Long> u : unlock.entrySet()) {
            Long b = net.get(AccountRef.bloque(u.getKey(), cur));
            if (b == null || b != -u.getValue()) throw bad("BLOQUE doit baisser exactement du montant bloqué de " + u.getKey());
            if (txn.kind() == TxnKind.ESCROW_REFUND) {
                Long d = net.get(AccountRef.dispo(u.getKey(), cur));
                if (d == null || d.longValue() != u.getValue().longValue()) throw bad("Rendu : tout le blocage revient en DISPO");
            }
        }
        return updates;
    }
}
