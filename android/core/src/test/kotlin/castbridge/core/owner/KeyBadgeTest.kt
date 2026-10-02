package castbridge.core.owner

import castbridge.core.lots.Right
import java.time.ZoneId
import kotlin.test.*

private const val DAY = 24L * 3600 * 1000
private const val T = 1_800_000_000_000L

class KeyBadgeTest {
    private val fp = DeviceIdentity.fingerprints(RawFactors(flashSerial = "FLASHSERIAL1", flashCid = "cid-1", ethernetMac = "AA:BB:CC:00:11:22", systemSerial = "SYS12345",
        wifiMac = "10:20:30:40:50:60", wifiSysfsPath = "/sys/devices/platform/soc/ffe03000.sd/mmc_host/mmc1/net/wlan0", bluetoothAddress = "11:22:33:44:55:66"))
    private val issuer = ActivationIssuer(Ed25519Signer(ByteArray(32) { (it + 9).toByte() }))
    private val utc = ZoneId.of("UTC")
    private fun act(kind: ActivationKind, vararg rights: Right) = Activation.decode(issuer.issue(ActivationIssuer.Request(kind, DeviceCode.of(fp), fp, T, rights = rights.toList(),
        license = if (kind == ActivationKind.TRIAL) "trial" else "lic-1", seat = SeatIds.of(if (kind == ActivationKind.TRIAL) "trial" else "lic-1", fp))).token)!!

    @Test fun trialShowsItsEndAndTheLotsWindow() {
        val b = KeyBadge.of(listOf(act(ActivationKind.TRIAL, Right.Usage(T, T + 30 * DAY))), T + DAY, zone = utc)
        assertEquals("ESSAI", b.title); assertTrue(b.lines[0].startsWith("Clé valable jusqu'au "), b.text); assertTrue("12 h" in b.text)
    }

    @Test fun productionIsUnlimitedWithoutAUsageCeilingAndEndsWithOne() {
        val unlimited = act(ActivationKind.PRODUCTION, Right.Purchase("p-cm2", listOf("cm2"), T))
        assertEquals("Clé illimitée", KeyBadge.of(listOf(unlimited), T + 900 * DAY, zone = utc).lines[0])
        val capped = act(ActivationKind.PRODUCTION, Right.Purchase("p-cm2", listOf("cm2"), T), Right.Usage(T, T + 62 * DAY))
        assertEquals("PRODUCTION", KeyBadge.of(listOf(capped), T + DAY, zone = utc).title)
        val ended = KeyBadge.of(listOf(capped), T + 63 * DAY, zone = utc)
        assertTrue(ended.ended); assertEquals("ACTIVATION TERMINÉE", ended.title)
    }

    @Test fun upgradingATrialToProductionShowsTheFullVersion() {
        val trial = act(ActivationKind.TRIAL, Right.Usage(T, T + 30 * DAY))
        val before = KeyBadge.of(listOf(trial), T + DAY, zone = utc)
        assertEquals("ESSAI", before.title); assertTrue(TrialPolicy.UPGRADE_LABEL in before.text, before.text)
        val prod = act(ActivationKind.PRODUCTION, Right.Purchase("p-cm2", listOf("cm2"), T))
        val after = KeyBadge.of(listOf(trial, prod), T + DAY, zone = utc)
        assertEquals("PRODUCTION", after.title); assertFalse(TrialPolicy.UPGRADE_LABEL in after.text)
        assertEquals("upgrade", TrialPolicy.UPGRADE_TILE); assertEquals("Passer en production", TrialPolicy.UPGRADE_LABEL)
    }

    @Test fun superAndEmptyStates() {
        assertEquals("SUPER ILLIMITÉ", KeyBadge.of(listOf(act(ActivationKind.PRODUCTION, Right.Super("super-illimite", T))), T, zone = utc).title)
        assertTrue(KeyBadge.of(emptyList(), T).ended)
    }
}

class TrialPolicyTest {
    private val fp = DeviceIdentity.fingerprints(RawFactors(flashSerial = "FLASHSERIAL1", flashCid = "cid-1", ethernetMac = "AA:BB:CC:00:11:22", systemSerial = "SYS12345",
        wifiMac = "10:20:30:40:50:60", wifiSysfsPath = "/sys/devices/platform/soc/ffe03000.sd/mmc_host/mmc1/net/wlan0", bluetoothAddress = "11:22:33:44:55:66"))
    private val issuer = ActivationIssuer(Ed25519Signer(ByteArray(32) { (it + 9).toByte() }))
    private fun act(kind: ActivationKind, vararg rights: Right) = Activation.decode(issuer.issue(ActivationIssuer.Request(kind, DeviceCode.of(fp), fp, T, rights = rights.toList(),
        license = if (kind == ActivationKind.TRIAL) "trial" else "lic-1", seat = SeatIds.of(if (kind == ActivationKind.TRIAL) "trial" else "lic-1", fp))).token)!!

    @Test fun onlyATrialKeyRestrictsTheEdition() {
        assertTrue(TvGate.evaluate(listOf(act(ActivationKind.TRIAL, Right.Usage(T, T + 30 * DAY))), emptyList(), T).trial)
        assertFalse(TvGate.evaluate(listOf(act(ActivationKind.PRODUCTION, Right.Purchase("p", listOf("b"), T))), emptyList(), T).trial)
        assertFalse(TvGate.evaluate(listOf(act(ActivationKind.TRIAL, Right.Usage(T, T + DAY)), act(ActivationKind.PRODUCTION, Right.Purchase("p", listOf("b"), T))), emptyList(), T).trial, "a production key lifts it")
    }

    @Test fun streamingAndSudokuStayCopyAndMoveGoAway() {
        assertTrue(TrialPolicy.gameAllowed("sudoku")); assertFalse(TrialPolicy.gameAllowed("chess")); assertFalse(TrialPolicy.gameAllowed("quiz"))
        assertTrue(TrialPolicy.tileAllowed("remote")); for (t in listOf("library", "receive", "usb", "downloads", "langues")) assertFalse(TrialPolicy.tileAllowed(t), t); for (t in listOf("learn", "games", "bluetooth")) assertTrue(TrialPolicy.tileAllowed(t), t)
        for (p in listOf("/api/transfer/start", "/api/part", "/api/storage/move", "/api/storage/move/cancel", "/api/delete", "/api/rename", "/api/folders/set", "/quiz/api/join")) assertTrue(TrialPolicy.routeBlocked(p), p)
        for (p in listOf("/api/hello", "/api/play", "/api/playurl", "/api/pause", "/api/info", "/api/rental", "/api/lots/upload", "/api/learn/state")) assertFalse(TrialPolicy.routeBlocked(p), p)
    }
}
