package castbridge.core.owner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Console 1.2.44: the owner phone signs `ik` into a production activation and shows the fingerprint to compare. TEST KEYS only. */
class ConsoleInstallKeyTest {
    private val fp = DeviceIdentity.fingerprints(RawFactors(flashSerial = "FLASHSERIAL1", flashCid = "cid-1", ethernetMac = "AA:BB:CC:00:11:22", systemSerial = "SYS12345",
        wifiMac = "10:20:30:40:50:60", wifiSysfsPath = "/sys/devices/platform/soc/ffe03000.sd/mmc_host/mmc1/net/wlan0", bluetoothAddress = "11:22:33:44:55:66"))
    private val signer = Ed25519Signer(ByteArray(32) { (it + 21).toByte() })
    private val tvKey = Ed25519Signer(ByteArray(32) { (it + 77).toByte() }).publicKey
    private val code = DeviceCode.of(fp)
    private fun info(key: ByteArray?) = OwnerFrames.parseDeviceInfo(OwnerFrames.deviceInfo(code, fp, null, key))
    private fun issue(key: ByteArray?) = ActivationIssuer(signer).issue(ActivationIssuer.Request(ActivationKind.PRODUCTION, code, fp, issuedAt = 1_800_000_000_000L, license = "lic-abcdef0123", installKey = key))

    @Test fun beforeShowsTheFingerprintOfTheKeyForProduction() {
        val n = assertNotNull(ConsoleInstallKey.before(info(tvKey), true))
        assertFalse(n.warning); assertTrue(ActivationBinding.fingerprint(tvKey) in n.text)
    }

    @Test fun beforeWarnsWhenTheTvSendsNoKey() = assertTrue(assertNotNull(ConsoleInstallKey.before(info(null), true)).warning)

    @Test fun trialAndBareCodesSayNothing() { assertNull(ConsoleInstallKey.before(info(tvKey), false)); assertNull(ConsoleInstallKey.before(null, true)) }

    @Test fun afterReadsTheKeyBackFromTheSignedActivation() {
        assertTrue(ActivationBinding.fingerprint(tvKey) in assertNotNull(ConsoleInstallKey.after(issue(tvKey))))
        assertNull(ConsoleInstallKey.after(issue(null)))
        assertEquals(tvKey.toList(), ActivationBinding.installKeyOf(issue(tvKey).activation)!!.toList())
    }
}
