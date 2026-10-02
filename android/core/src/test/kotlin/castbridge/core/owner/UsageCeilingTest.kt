package castbridge.core.owner

import castbridge.core.lots.Right
import kotlin.test.*

private const val DAY = 24L * 3600 * 1000
private const val T = 1_800_000_000_000L

/** The usage ceiling (`usage|duree|from|to`): a trial or a production activation stops counting at its end; none = no ceiling. */
class UsageCeilingTest {
    private val fp = DeviceIdentity.fingerprints(RawFactors(flashSerial = "FLASHSERIAL1", flashCid = "cid-1", ethernetMac = "AA:BB:CC:00:11:22", systemSerial = "SYS12345",
        wifiMac = "10:20:30:40:50:60", wifiSysfsPath = "/sys/devices/platform/soc/ffe03000.sd/mmc_host/mmc1/net/wlan0", bluetoothAddress = "11:22:33:44:55:66"))
    private val issuer = ActivationIssuer(Ed25519Signer(ByteArray(32) { (it + 3).toByte() }))

    private fun activation(kind: ActivationKind, vararg rights: Right): Activation = Activation.decode(issuer.issue(ActivationIssuer.Request(kind, DeviceCode.of(fp), fp, T,
        rights = rights.toList(), license = if (kind == ActivationKind.TRIAL) Activation.TRIAL_LICENSE else "lic-1", seat = SeatIds.of(if (kind == ActivationKind.TRIAL) "trial" else "lic-1", fp))).token)!!

    @Test fun theLineSurvivesTheSignedRoundTrip() {
        val a = activation(ActivationKind.PRODUCTION, Right.Purchase("p-cm2", listOf("cm2"), T), Right.Usage(T, T + 30 * DAY))
        assertEquals(Right.Usage(T, T + 30 * DAY), a.rights.filterIsInstance<Right.Usage>().single())
        assertEquals("usage|duree|$T|${T + 30 * DAY}", Activation.rightLine(a.rights.filterIsInstance<Right.Usage>().single()))
    }

    @Test fun aTrialStopsCountingAtItsEndAndTheTvLocksAgain() {
        val a = activation(ActivationKind.TRIAL, Right.Usage(T, T + 30 * DAY))
        assertTrue(TvGate.evaluate(listOf(a), emptyList(), T + 29 * DAY).keyInstalled, "inside the ceiling the trial opens")
        val after = TvGate.evaluate(listOf(a), emptyList(), T + 30 * DAY + 1000)
        assertFalse(after.keyInstalled); assertEquals("Activation terminée", after.label)
    }

    @Test fun aProductionActivationWithACeilingEndsButOneWithoutNeverDoes() {
        val capped = activation(ActivationKind.PRODUCTION, Right.Purchase("p-cm2", listOf("cm2"), T), Right.Usage(T, T + 90 * DAY))
        val free = activation(ActivationKind.PRODUCTION, Right.Purchase("p-cm2", listOf("cm2"), T))
        assertTrue(TvGate.evaluate(listOf(capped), emptyList(), T + 89 * DAY).access.purchased.contains("cm2"))
        assertFalse(TvGate.evaluate(listOf(capped), emptyList(), T + 91 * DAY).keyInstalled)
        assertTrue(TvGate.evaluate(listOf(free), emptyList(), T + 3000 * DAY).access.purchased.contains("cm2"), "no ceiling = as before")
        // a renewal (a second activation without the ceiling) keeps the TV open once the first one has ended
        assertTrue(TvGate.evaluate(listOf(capped, free), emptyList(), T + 91 * DAY).access.purchased.contains("cm2"))
    }

    @Test fun theIssuerRefusesAnOutOfBoundsCeiling() {
        val e = assertFailsWith<IssueException> { activation(ActivationKind.PRODUCTION, Right.Purchase("p", listOf("b"), T), Right.Usage(T, T + 4000 * DAY)) }
        assertTrue("hors bornes" in e.message!!)
        assertFailsWith<IssueException> { activation(ActivationKind.PRODUCTION, Right.Purchase("p", listOf("b"), T), Right.Usage(T, T)) }
    }
}
