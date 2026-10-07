package castbridge.server.wallet.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Grand livre en mémoire : implémentation de référence (tests, oracle de w22-02). Toute vérification est faite AVANT la première écriture :
 * un refus ne laisse aucune trace (atomicité). Les soldes des comptes système sont signés et libres ; ceux des joueurs ne descendent jamais sous 0.
 */
public class MemoryLedger implements Ledger {
    private final Map<AccountRef, Long> balances = new HashMap<>();
    private final Map<String, String> idem = new HashMap<>();          // clé d'opération → empreinte
    private final Map<String, EscrowState> escrows = new LinkedHashMap<>();

    @Override public synchronized Posted post(Txn txn) {
        String prior = idem.get(txn.idemKey());
        if (prior != null) {
            if (prior.equals(txn.contentSha())) return new Posted(true);
            throw new LedgerException(WalletReason.IDEM_CONFLICT);
        }
        TreeMap<AccountRef, Long> net = new TreeMap<>();                // ordre fixe de traitement
        for (Entry e : txn.entries()) net.merge(e.account(), e.amount(), Long::sum);
        Map<String, EscrowState> escrowUpdates = checkShape(txn, net);
        for (Map.Entry<AccountRef, Long> n : net.entrySet()) {
            AccountRef a = n.getKey();
            long after = balance(a) + n.getValue();
            if (!a.isSystem() && after < 0) {
                throw new LedgerException(WalletReason.INSUFFICIENT, WalletReason.INSUFFICIENT.text(balance(a) + " " + a.currency()));
            }
        }
        // --- tout est vérifié : écritures ---
        for (Map.Entry<AccountRef, Long> n : net.entrySet()) balances.merge(n.getKey(), n.getValue(), Long::sum);
        idem.put(txn.idemKey(), txn.contentSha());
        escrows.putAll(escrowUpdates);
        return new Posted(false);
    }

    @Override public synchronized long balance(AccountRef account) { return balances.getOrDefault(account, 0L); }

    @Override public synchronized long sum(Currency currency) {
        long s = 0;
        for (Map.Entry<AccountRef, Long> e : balances.entrySet()) if (e.getKey().currency() == currency) s += e.getValue();
        return s;
    }

    @Override public synchronized Optional<EscrowState> escrow(String eid) { return Optional.ofNullable(escrows.get(eid)); }

    /** Instantané des soldes non nuls (tests : comparer avant/après un refus ou un rejeu). */
    public synchronized Map<AccountRef, Long> snapshot() {
        Map<AccountRef, Long> m = new TreeMap<>();
        balances.forEach((k, v) -> { if (v != 0) m.put(k, v); });
        return m;
    }

    public synchronized List<EscrowState> escrows() { return new ArrayList<>(escrows.values()); }

    /** Parcourt les soldes (tests d'invariants, sans copie). */
    public synchronized void forEachBalance(java.util.function.BiConsumer<AccountRef, Long> visitor) { balances.forEach(visitor); }

    // ---- règles de forme : seuls les blocages touchent BLOQUE et SYS:POT ; un blocage ne sert qu'une fois (I-6, I-5) ----

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
            if (escrows.containsKey(eid)) throw new LedgerException(WalletReason.IDEM_CONFLICT, "Ce blocage existe déjà : " + eid);
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
        // règlement ou rendu : chaque référence = un blocage OUVERT ; BLOQUE de chaque titulaire baisse exactement de ses blocages
        Map<String, Long> unlock = new HashMap<>();
        for (String eid : txn.refs()) {
            EscrowState s = escrows.get(eid);
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
            } else if (a.holder().equals(AccountRef.FEE) && a.isSystem() && txn.kind() == TxnKind.SETTLE && n.getValue() > 0) {
                // frais de plateforme du jeu (politique) : seul un règlement les crédite, jamais négatifs ; ils sortent de la cagnotte, qui revient à 0 (la conservation est vérifiée ci-dessus)
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

    private static LedgerException bad(String why) { return new LedgerException(WalletReason.BAD_TXN, "Transaction invalide : " + why); }
}
