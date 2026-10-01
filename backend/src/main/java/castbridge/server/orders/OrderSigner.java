package castbridge.server.orders;

/**
 * The server's signing key for orders (scope `policy`, never `transfer` nor `open`; docs/ORDRES.md § Clés). The module of the licences can provide its own implementation (same key as
 * its activation signer); the default one, [ConfiguredOrderSigner], reads a PEM/DER Ed25519 key from CASTBRIDGE_ORDERS_KEY_FILE (a secret, never in the image or the logs).
 */
public interface OrderSigner {
    boolean enabled();
    /** First 16 hex digits of SHA-256 of the raw public key. */
    String keyId();
    byte[] sign(byte[] message);
}
