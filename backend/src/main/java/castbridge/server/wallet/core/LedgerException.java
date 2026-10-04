package castbridge.server.wallet.core;

/** Refus du grand livre : toujours accompagné d'un motif fermé ; aucune écriture n'a été faite. */
public class LedgerException extends RuntimeException {
    private final WalletReason reason;

    public LedgerException(WalletReason reason) { this(reason, reason.text()); }

    public LedgerException(WalletReason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public WalletReason reason() { return reason; }
}
