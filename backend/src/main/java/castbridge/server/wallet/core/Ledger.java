package castbridge.server.wallet.core;

import java.util.Optional;

/**
 * Grand livre à double entrée (conception W22 § 3.1). Une transaction est posée en bloc ou pas du tout ; {@code replayed} = la même clé d'opération
 * avec le même contenu avait déjà été posée (rien n'a changé). Implémentation de référence : {@link MemoryLedger}.
 */
public interface Ledger {
    enum EscrowStatus { OPEN, SETTLED, REFUNDED }

    /** Blocage de mise connu du grand livre : à régler ou à rendre une seule fois (I-6). */
    record EscrowState(String eid, String holder, Currency currency, long amount, EscrowStatus status) {}

    record Posted(boolean replayed) {}

    /**
     * Pose la transaction, atomiquement. Même clé + même empreinte : {@code replayed = true}. Même clé + autre empreinte : {@code IDEM_CONFLICT}.
     * Tout compte de joueur qui deviendrait négatif : {@code INSUFFICIENT}, rien n'est écrit.
     */
    Posted post(Txn txn);

    long balance(AccountRef account);

    /** Somme de tous les comptes d'une monnaie (toujours 0 : I-1). */
    long sum(Currency currency);

    Optional<EscrowState> escrow(String eid);
}
