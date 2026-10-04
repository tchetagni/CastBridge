package castbridge.server.wallet.core;

/** Genres de transaction (liste fermée, conception W22 § 3.1). */
public enum TxnKind { GRANT, CONVERT, TRANSFER, ESCROW_LOCK, SETTLE, ESCROW_REFUND, VOUCHER, ADJUST }
