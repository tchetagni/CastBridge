package castbridge.server.wallet.core;

/** Une écriture : montant signé (entier) sur un compte. Immuable. */
public record Entry(AccountRef account, long amount) {
    public Entry {
        if (account == null) throw new LedgerException(WalletReason.BAD_TXN, "Écriture sans compte");
    }
}
