package castbridge.server.wallet.ops;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import castbridge.server.licenses.LicenseKeyring;
import castbridge.server.wallet.WalletTestBase;
import org.junit.jupiter.api.Test;

/** F10 : une clé de résultat égale à la clé « portefeuille » est une erreur d'exploitation, refusée au démarrage. */
class AuditW2205KeysTest {
    @Test
    void aResultKeyEqualToTheWalletKeyIsRefusedAtStartup() {
        String walletPub = java.util.Base64.getEncoder().encodeToString(WalletTestBase.rawPublicBytes(WalletTestBase.WALLET));
        String walletKid = LicenseKeyring.kidOf(WalletTestBase.rawPublicBytes(WalletTestBase.WALLET));
        assertThrows(IllegalStateException.class, () -> new PlayResultKeys(walletPub).assertNotWalletKey(walletKid));
        String other = java.util.Base64.getEncoder().encodeToString(WalletTestBase.rawPublicBytes(WalletTestBase.ISSUER));
        assertDoesNotThrow(() -> new PlayResultKeys(other).assertNotWalletKey(walletKid));
        assertDoesNotThrow(() -> new PlayResultKeys("").assertNotWalletKey(walletKid));
    }
}
