package castbridge.core.lots

import castbridge.core.owner.*
import kotlin.test.*

private const val DAY = 24L * 3600 * 1000
private const val T = 1_800_000_000_000L

/** The trial KEY's one-time 12 h window of rented lots: carried by the trial activation (never sold as a rental), counted in minutes of use, granted once for the life of the application. */
class TrialWindowTest {
    private val fp = DeviceIdentity.fingerprints(RawFactors(flashSerial = "FLASHSERIAL1", flashCid = "cid-1", ethernetMac = "AA:BB:CC:00:11:22", systemSerial = "SYS12345",
        wifiMac = "10:20:30:40:50:60", wifiSysfsPath = "/sys/devices/platform/soc/ffe03000.sd/mmc_host/mmc1/net/wlan0", bluetoothAddress = "11:22:33:44:55:66"))
    private val issuer = ActivationIssuer(Ed25519Signer(ByteArray(32) { (it + 5).toByte() }))
    private val master = ByteArray(32) { (it * 3 + 1).toByte() }
    private val seat = SeatIds.of(Activation.TRIAL_LICENSE, fp)

    private fun trialKey(at: Long): Activation {
        val window = RentalIssuing.right(RentalSpec(RentalLines.TRIAL_PRODUCT, listOf(Right.ALL_BUNDLE), RentalLines.TRIAL_DAYS, RentalLines.TRIAL_USAGE_MINUTES), at, Activation.TRIAL_LICENSE, seat, fp, master)
        return Activation.decode(issuer.issue(ActivationIssuer.Request(ActivationKind.TRIAL, DeviceCode.of(fp), fp, at, rights = listOf(Right.Usage(at, at + 30 * DAY), window), license = Activation.TRIAL_LICENSE, seat = seat)).token)!!
    }

    private class Tv(var wall: Long = T) {
        val dir = Kit.tmp(); val vault = RentalVault(java.io.File(dir, "r")); val ledger = RentalLedger(java.io.File(dir, "r"), TvClock(), RentalConfig(), { wall })
    }

    @Test fun aTrialKeyCarriesTheWindowAndNothingElse() {
        val a = trialKey(T)
        assertEquals(2, a.rights.size); assertTrue(a.rights.all { Activation.trialRight(it) })
        val rental = a.rights.filterIsInstance<Right.Rental>().single()
        assertEquals(RentalLines.TRIAL_USAGE_MINUTES, rental.maxUsageMinutes); assertEquals(RentalLines.TRIAL_DAYS, rental.durationDays)
    }

    @Test fun theWindowRunsOutByUseAndTheKeyIsGone() {
        val tv = Tv(); val a = trialKey(T); tv.ledger.install(a, listOf(a), fp, tv.vault)
        val key = RentalEngine.contractKey(RentalLines.TRIAL_PRODUCT, T)
        assertTrue(tv.vault.hasKey(key))
        val lot = LotId("learn", "cm2"); val meta = Kit.meta("learn", "cm2", 1, Kit.bytes(1, 100))
        assertNull(tv.ledger.markRented(key, lot, meta, LotFamilies { LotFamily.RESERVED }))
        repeat(11) { tv.ledger.recordUsage(lot, 60, listOf(a)) }                       // 11 h of reading
        assertTrue(tv.ledger.status(listOf(a)).single().usable)
        assertEquals(RentalState.EXPIRED, tv.ledger.recordUsage(lot, 60, listOf(a)).single().state, "the 12th hour ends the window")
    }

    @Test fun theWindowIsGrantedOnceForTheLifeOfTheApplication() {
        val tv = Tv(); val first = trialKey(T); tv.ledger.install(first, listOf(first), fp, tv.vault)
        // the first window ran its course
        tv.ledger.markEnding(tv.ledger.status(listOf(first)).map { it.copy(state = RentalState.EXPIRED, reason = ExpiryReason.USAGE) })
        tv.wall = T + 5 * DAY
        val second = trialKey(T + 5 * DAY)                                               // a NEW trial key, a new period
        val out = tv.ledger.install(second, listOf(first, second), fp, tv.vault)
        assertEquals("essai déjà utilisé sur cette TV", out.values.single())
        assertFalse(tv.vault.hasKey(RentalEngine.contractKey(RentalLines.TRIAL_PRODUCT, T + 5 * DAY)), "no key: no lot can be opened")
    }

    @Test fun anotherRentalProductIsNotAllowedOnATrialKeyAndTheWindowBoundsAreEnforced() {
        val other = RentalIssuing.right(RentalSpec("loc-cm2", listOf("cm2"), 2), T, Activation.TRIAL_LICENSE, seat, fp, master)
        assertFailsWith<IssueException> { issuer.issue(ActivationIssuer.Request(ActivationKind.TRIAL, DeviceCode.of(fp), fp, T, rights = listOf(other), license = Activation.TRIAL_LICENSE, seat = seat)) }
        val long = RentalIssuing.right(RentalSpec(RentalLines.TRIAL_PRODUCT, listOf(Right.ALL_BUNDLE), 3, 2000), T, Activation.TRIAL_LICENSE, seat, fp, master)
        assertNotNull(RentalLines.bounds(long))
    }
}

class BundleRentalDaysTest {
    @Test fun theServerCatalogueCarriesTheLongestRentalOfABundle() {
        val c = BundleCatalog.parse("""{"bundles":[{"id":"cm2","type":"classe","lots":["learn:cm2"],"rentalDays":30},{"id":"cp","type":"classe","lots":["learn:cp"]}]}""")
        assertEquals(30, c.find("cm2")!!.rentalDays); assertEquals(0, c.find("cp")!!.rentalDays, "no limit set by the catalogue")
    }
}
