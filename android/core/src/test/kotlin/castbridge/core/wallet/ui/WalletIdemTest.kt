package castbridge.core.wallet.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WalletIdemTest {
    /** Le motif du serveur (`EscrowService.IDEM`) : 3 à 64 caractères parmi A-Z a-z 0-9 . _ : - */
    private val serverPattern = Regex("^[A-Za-z0-9._:-]{3,64}$")

    @Test fun keysFitTheServerPatternAndNeverRepeat() {
        val keys = (1..500).map { WalletIdem.newKey() }
        keys.forEach { assertTrue(serverPattern.matches(it), it) }
        assertEquals(500, keys.toSet().size)
    }

    @Test fun keyIsRecognisableAsComingFromTheTv() { assertTrue(WalletIdem.newKey().startsWith("tv-")) }
}
