package castbridge.core

import castbridge.core.tv.WifiDirect
import kotlin.test.*

class WifiDirectTest {
    @Test fun passphraseIsValidAndUnambiguous() {
        repeat(100) {
            val p = WifiDirect.generatePassphrase()
            assertTrue(WifiDirect.isValidPassphrase(p), p)
            assertTrue(p.none { it in "0OIl1" }, p)
        }
        assertNotEquals(WifiDirect.generatePassphrase(), WifiDirect.generatePassphrase())
        assertFailsWith<IllegalArgumentException> { WifiDirect.generatePassphrase(length = 7) }
    }

    @Test fun networkNameFollowsDirectRule() {
        assertTrue(WifiDirect.isValidNetworkName(WifiDirect.networkName()))
        assertTrue(WifiDirect.isValidNetworkName(WifiDirect.networkName("a b;c/d")))
        assertFalse(WifiDirect.isValidNetworkName("MyWifi"))
        assertFalse(WifiDirect.isValidNetworkName("DIRECT-"))
        assertFalse(WifiDirect.isValidNetworkName("DIRECT-" + "x".repeat(40)))
    }

    @Test fun wifiUriEscapesSpecials() {
        assertEquals("WIFI:T:WPA;S:DIRECT-CB-x;P:ab\\;c\\:d;;", WifiDirect.wifiUri("DIRECT-CB-x", "ab;c:d"))
        assertEquals("http://192.168.49.1:8765", WifiDirect.BASE_URL)
    }
}
