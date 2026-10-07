package castbridge.core.tv

import kotlin.test.*

class WdCodeTest {
    @Test fun aSixDigitCodeGivesAValidDeterministicGroup() {
        val n1 = WdCode.networkName("123456"); val p1 = WdCode.passphrase("123456")
        assertEquals(n1, WdCode.networkName("123456")); assertEquals(p1, WdCode.passphrase("123456"))
        assertTrue(WifiDirect.isValidNetworkName(n1), n1)
        assertTrue(WifiDirect.isValidPassphrase(p1), p1)
        assertEquals(WifiDirect.PASSPHRASE_LENGTH, p1.length)
        assertTrue(WdCode.looksLikeActivationGroup(n1))
    }

    @Test fun theNetworkNameNeverContainsTheCodeAndDiffersPerCode() {
        assertFalse(WdCode.networkName("123456").contains("123456"))
        assertNotEquals(WdCode.networkName("123456"), WdCode.networkName("123457"))
        assertNotEquals(WdCode.passphrase("123456"), WdCode.passphrase("123457"))
    }

    @Test fun onlySixDigitsAreAccepted() {
        assertTrue(WdCode.isValid("000000")); assertFalse(WdCode.isValid("12345")); assertFalse(WdCode.isValid("12345a")); assertFalse(WdCode.isValid("1234567"))
        assertFailsWith<IllegalArgumentException> { WdCode.passphrase("12345") }
    }

    @Test fun theWifiUriIsTheZxingWpaForm() {
        val u = WdCode.wifiUri("654321")
        assertTrue(u.startsWith("WIFI:T:WPA;S:DIRECT-CB-"), u)
        assertTrue(u.endsWith(";;"), u)
        assertTrue(u.contains(";P:" + WdCode.passphrase("654321") + ";"), u)
    }

    @Test fun passphraseCharactersAvoidLookAlikes() {
        val p = (0..999).joinToString("") { WdCode.passphrase(it.toString().padStart(6, '0')) }
        assertFalse(p.any { it in "0O1lI" }, "aucun sosie : 0/O/1/l/I")
    }
}
