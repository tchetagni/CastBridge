package castbridge.core.lots

import castbridge.core.owner.*
import java.io.File
import kotlin.test.*

/** W16 (w16-02): minutes of use counted per contract, attributed to the lot really open, persisted every minute, reported without personal data. Fake wall clock, fake monotone clock, real files in a temp folder. */
class RentalLedgerUsageTest {
    private val day = 24L * 3600 * 1000
    private val min = 60_000L
    private val t0 = 1_800_000_000_000L
    private val cm2 = LotId("learn", "cm2")
    private val cm1 = LotId("learn", "cm1")
    private val fp = DeviceIdentity.fingerprints(RawFactors(flashSerial = "FLASHSERIAL1", flashCid = "cid-1", ethernetMac = "AA:BB:CC:00:11:22", systemSerial = "SYS12345",
        wifiMac = "10:20:30:40:50:60", wifiSysfsPath = "/sys/devices/platform/soc/ffe03000.sd/mmc_host/mmc1/net/wlan0", bluetoothAddress = "11:22:33:44:55:66"))
    private val families = LotFamilies.explicit(emptySet(), setOf("learn:cm2", "learn:cm1"))

    private fun rental(product: String, usage: Int, days: Int = 30) = Right.Rental(product, listOf("classe-$product"), t0, t0, days, 0L, usage, 3, "box")
    private fun act(vararg r: Right.Rental) = Activation(ActivationKind.PRODUCTION, Subject.TV, "k", 1, "n", t0, t0, t0 + 400 * day, "lic-secret", "seat-secret", 1, emptyMap(), r.toList(), "sig")

    /** A TV: ledger in a temp folder, the activations installed, the lots rented (cm2 under the first contract, cm1 under the second when there are two). */
    private inner class Tv(vararg rights: Right.Rental) {
        var wall = t0
        val dir: File = Kit.tmp()
        val vault = RentalVault(File(dir, "r"))
        var ledger = RentalLedger(File(dir, "r"), TvClock(), RentalConfig(), { wall })
        val acts = rights.map { act(it) }
        val keys = rights.map { "${it.productId}@$t0" }
        init {
            acts.forEach { ledger.install(it, acts, fp, vault) }
            ledger.markRented(keys[0], cm2, LotMeta(cm2, 1, 10, "a".repeat(64), "CM2"), families)
            if (keys.size > 1) ledger.markRented(keys[1], cm1, LotMeta(cm1, 1, 10, "b".repeat(64), "CM1"), families)
        }
        fun feed(t: Tick) { if (t.lot != null && t.minutes > 0) ledger.recordUsage(t.lot, t.minutes, acts) }
        /** What a restart sees: the same folder read again by a brand-new ledger. */
        fun reboot() { ledger = RentalLedger(File(dir, "r"), TvClock(), RentalConfig(), { wall }) }
        fun used(i: Int) = ledger.usedMinutes(keys[i])
    }

    @Test fun minutesGoToTheContractCoveringTheOpenLot() {
        val tv = Tv(rental("loc-cm2", 600), rental("loc-cm1", 600)); val m = UseMeter()
        m.open(cm2, 0); tv.feed(m.tick(3 * min))
        tv.feed(m.open(cm1, 3 * min)); tv.feed(m.tick(5 * min))
        tv.feed(m.close(5 * min))
        assertEquals(3L, tv.used(0), "cm2 opened 3 minutes: only the first contract")
        assertEquals(2L, tv.used(1), "cm1 opened 2 minutes: only the second contract, not the first one")
    }

    @Test fun dayContractCountsMinutesWithoutEverExpiringByUsage() {
        val tv = Tv(rental("loc-cm2", 0, days = 7))
        repeat(10) { tv.ledger.recordUsage(cm2, 1440, tv.acts) }
        val s = tv.ledger.status(tv.acts).single()
        assertEquals(14400L, tv.used(0), "measured")
        assertEquals(RentalState.ACTIVE, s.state); assertNull(s.reason)
        assertEquals(RentalUnit.DAYS, s.contract.unit)
        assertEquals(Pair(14400L, 0L), tv.ledger.usageOf(tv.keys[0], tv.acts))
    }

    @Test fun hourContractEndsByUsageAtItsBudget() {
        val tv = Tv(rental("loc-cm2", 5))
        val m = UseMeter(); m.open(cm2, 0)
        tv.feed(m.tick(4 * min)); assertEquals(RentalState.ACTIVE, tv.ledger.status(tv.acts).single().state)
        tv.feed(m.tick(5 * min)); assertEquals(RentalState.EXPIRED, tv.ledger.status(tv.acts).single().state)
        assertEquals(ExpiryReason.USAGE, tv.ledger.status(tv.acts).single().reason)
    }

    @Test fun aUsableDayPassCoveringTheLotIsMeasuredAndTheHoursStayIntact() {
        val tv = Tv(rental("loc-cm2", 600), rental("loc-cm1", 0, days = 7))
        tv.ledger.markRented(tv.keys[1], cm2, LotMeta(cm2, 1, 10, "a".repeat(64), "CM2"), families)      // both contracts cover cm2
        tv.ledger.recordUsage(cm2, 10, tv.acts)
        assertEquals(0L, tv.used(0), "the customer does not burn hours while a day pass covers the lot")
        assertEquals(10L, tv.used(1), "the day pass is only measured")
    }

    @Test fun anExpiredDayPassLetsTheHoursBeDebited() {
        val tv = Tv(rental("loc-cm2", 600), rental("loc-cm1", 0, days = 7))
        tv.ledger.markRented(tv.keys[1], cm2, LotMeta(cm2, 1, 10, "a".repeat(64), "CM2"), families)
        tv.wall = t0 + 8 * day                                                                            // the 7-day pass is over, the 30-day hourly contract is not
        tv.ledger.recordUsage(cm2, 10, tv.acts)
        assertEquals(10L, tv.used(0)); assertEquals(0L, tv.used(1))
    }

    @Test fun eightHoursWithoutARentalDoNotBecomeCreditForADoubleCount() {
        val tv = Tv(rental("loc-cm2", 6000)); val first = UseMeter()
        first.open(cm2, 0); tv.ledger.recordTick(first.tick(min), min, tv.acts)                           // one rented minute, then 8 h of standby or free lots with the process alive
        val h8 = 8 * 60 * min; val a = UseMeter(); val b = UseMeter()
        a.open(cm2, h8); b.open(cm2, h8)
        for (i in 1..60) {
            if (i % 10 == 0) { a.input(h8 + i * min); b.input(h8 + i * min) }
            tv.ledger.recordTick(a.tick(h8 + i * min), h8 + i * min, tv.acts); tv.ledger.recordTick(b.tick(h8 + i * min), h8 + i * min, tv.acts)
        }
        assertTrue(tv.used(0) in 61L..72L, "1 + 60 real minutes, at most the slack too many (not 121): ${tv.used(0)}")
    }

    @Test fun theGuardAlsoHoldsAfterARebootThroughRecordTick() {
        val tv = Tv(rental("loc-cm2", 6000)); val m = UseMeter(); m.open(cm2, 5000 * min)
        for (i in 1..3) tv.ledger.recordTick(m.tick(5000 * min + i * min), 5000 * min + i * min, tv.acts)
        assertEquals(3L, tv.used(0))
        tv.reboot()                                                                                       // new ledger (empty guard), the monotone clock restarts near 0
        val a = UseMeter(); val b = UseMeter(); a.open(cm2, 0); b.open(cm2, 0)
        for (i in 1..60) {
            if (i % 10 == 0) { a.input(i * min); b.input(i * min) }
            tv.ledger.recordTick(a.tick(i * min), i * min, tv.acts); tv.ledger.recordTick(b.tick(i * min), i * min, tv.acts)
        }
        assertTrue(tv.used(0) in 63L..75L, "3 + 60 real minutes, at most the slack too many: ${tv.used(0)}")
    }

    @Test fun usedNeverExceedsTheBudget() {
        val tv = Tv(rental("loc-cm2", 5))
        tv.ledger.recordUsage(cm2, 1440, tv.acts)
        assertEquals(5L, tv.used(0))
        assertEquals(Pair(5L, 5L), tv.ledger.usageOf(tv.keys[0], tv.acts))
    }

    @Test fun twoMetersOnTheSameLotDoNotDoubleCount() {
        val tv = Tv(rental("loc-cm2", 6000)); val a = UseMeter(); val b = UseMeter()
        a.open(cm2, 0); b.open(cm2, 0)
        for (i in 1..60) {
            if (i % 10 == 0) { a.input(i * min); b.input(i * min) }          // keys keep both meters alive: 120 minutes are offered for 60 minutes of time
            tv.ledger.recordTick(a.tick(i * min), i * min, tv.acts); tv.ledger.recordTick(b.tick(i * min), i * min, tv.acts)
        }
        assertTrue(tv.used(0) in 60L..72L, "120 minutes were offered for 60 minutes of time: ${tv.used(0)}")
    }

    @Test fun oneMeterIsNeverHeldBackByTheGuard() {
        val tv = Tv(rental("loc-cm2", 6000), rental("loc-cm1", 6000)); val m = UseMeter()
        m.open(cm2, 0); var now = 0L
        for (i in 1..90) { now = i * min; tv.ledger.recordTick(m.tick(now), now, tv.acts); if (i % 10 == 0) m.input(now) }
        tv.ledger.recordTick(m.open(cm1, now + 30_000), now + 30_000, tv.acts)
        tv.ledger.recordTick(m.close(now + 90_000), now + 90_000, tv.acts)
        assertEquals(90L, tv.used(0)); assertEquals(1L, tv.used(1))
    }

    @Test fun anUnrentedLotCountsNothing() {
        val tv = Tv(rental("loc-cm2", 600))
        tv.ledger.recordUsage(LotId("learn", "langues-fr"), 10, tv.acts)
        assertEquals(0L, tv.used(0), "a free lot (never rented) leaves every contract untouched")
        tv.feed(UseMeter().also { it.open(LotId("learn", "langues-fr"), 0) }.tick(5 * min))
        assertEquals(0L, tv.used(0))
    }

    @Test fun everyMinuteIsOnDiskBeforeTheNextTick() {
        val tv = Tv(rental("loc-cm2", 600)); val m = UseMeter(); m.open(cm2, 0)
        for (i in 1..12) {
            tv.feed(m.tick(i * min))
            val onDisk = RentalLedger(File(tv.dir, "r"), TvClock(), RentalConfig(), { tv.wall }).usedMinutes(tv.keys[0])   // a brand-new ledger reads the file
            assertEquals(i.toLong(), onDisk, "minute $i is already in rentals.json")
        }
    }

    @Test fun powerCutLosesAtMostOneMinute() {
        val tv = Tv(rental("loc-cm2", 600)); val m = UseMeter(); m.open(cm2, 0)
        var now = 0L
        while (now < 7 * min + 30_000) { now += 10_000; tv.wall += 10_000; if (now % min == 0L) tv.feed(m.tick(now)) }   // the cut comes 7 min 30 s in
        tv.reboot()                                                                                                    // power cut: new ledger from the file, meter gone
        val lost = now / min - tv.used(0)
        assertTrue(lost in 0..1, "lost $lost minutes")
        assertEquals(7L, tv.used(0))
    }

    @Test fun noDoubleCountAfterRebootOrPowerCut() {
        val tv = Tv(rental("loc-cm2", 600)); var m = UseMeter(); m.open(cm2, 5000 * min)
        tv.feed(m.tick(5003 * min)); assertEquals(3L, tv.used(0))
        tv.reboot(); m = UseMeter()                                      // elapsedRealtime restarts near 0
        tv.feed(m.tick(0)); tv.feed(m.tick(2 * min))
        assertEquals(3L, tv.used(0), "the meter is empty until the screen reopens: nothing added, nothing repeated")
        m.open(cm2, 3 * min); tv.feed(m.tick(4 * min))
        assertEquals(4L, tv.used(0))
    }

    @Test fun wallClockRolledBackNeitherLosesNorAddsMinutes() {
        val tv = Tv(rental("loc-cm2", 600), rental("loc-cm1", 0, days = 7))
        tv.wall = t0 + 5 * day; tv.ledger.observe()                      // the TV saw day 5
        tv.wall = t0                                                     // somebody sets the clock back 5 days
        assertEquals(RentalState.SUSPENDED, tv.ledger.status(tv.acts).first().state)
        val m = UseMeter(); m.open(cm2, 0); tv.feed(m.tick(3 * min))
        assertEquals(3L, tv.used(0), "usage is counted even with the clock in doubt (it is the second ceiling)")
        tv.wall = t0 + 5 * day + 2 * min; tv.feed(m.tick(5 * min))
        assertEquals(5L, tv.used(0), "the wall clock jumping forward again changes nothing: the count follows the monotone clock")
    }

    @Test fun usageReportListsEveryContractWithUnitUsedMaxStateReason() {
        val tv = Tv(rental("loc-cm2", 600), rental("loc-cm1", 0, days = 7))
        tv.ledger.recordUsage(cm2, 90, tv.acts); tv.ledger.recordUsage(cm1, 5, tv.acts)
        val nowTv = t0 + 3 * min; tv.wall = nowTv                      // the statement is stamped with the ledger's own clock
        val lines = tv.ledger.usageReport(tv.acts, "0123456789abcdef").lines().filter { it.isNotEmpty() }
        assertEquals(listOf("castbridge-rental-usage-v1", "install=0123456789abcdef",
            "contract=loc-cm1@$t0|unit=days|used=5|max=0|state=ACTIVE|reason=-|endsAt=${t0 + 7 * day}|at=$nowTv",           // sorted by contract: cm1 first
            "contract=loc-cm2@$t0|unit=hours|used=90|max=600|state=ACTIVE|reason=-|endsAt=${t0 + 30 * day}|at=$nowTv"), lines)
    }

    @Test fun usageReportRefusesAnythingButTheInstallKeyId() {
        val tv = Tv(rental("loc-cm2", 600))
        for (bad in listOf("123e4567-e89b-12d3-a456-426614174000", "0123456789ABCDEF", "0123456789abcde", "0123456789abcdef0", "", "0123456789abcdeg"))
            assertFailsWith<IllegalArgumentException>("« $bad » is not the 16 hex of install.key (never the telemetry id)") { tv.ledger.usageReport(tv.acts, bad) }
    }

    @Test fun usageReportAlsoListsOrphanContracts() {
        val tv = Tv(rental("loc-cm2", 600))
        tv.ledger.recordUsage(cm2, 3, tv.acts)
        tv.wall = t0 + min
        val lines = tv.ledger.usageReport(emptyList(), "0123456789abcdef").lines().filter { it.isNotEmpty() }
        assertEquals("contract=loc-cm2@$t0|unit=unknown|used=3|max=0|state=ORPHAN|reason=-|endsAt=0|at=${t0 + min}", lines.last(), "the activation was removed, the minutes are still reported")
        assertEquals(3, lines.size)
    }

    @Test fun usageReportShowsAnEndedContractAndItsReason() {
        val tv = Tv(rental("loc-cm2", 10))
        tv.ledger.recordUsage(cm2, 10, tv.acts)
        val line = tv.ledger.usageReport(tv.acts, "0123456789abcdef").lines().filter { it.startsWith("contract=") }.joinToString("\n")
        assertTrue(line.contains("used=10|max=10|state=EXPIRED|reason=USAGE|"), line)
    }

    @Test fun usageReportHoldsNoLicenceSeatBoxFactorOrProfile() {
        val tv = Tv(rental("loc-cm2", 600))
        tv.ledger.recordUsage(cm2, 5, tv.acts)
        val text = tv.ledger.usageReport(tv.acts, "0123456789abcdef")
        for (secret in listOf("lic-secret", "seat-secret", "box", "sig", "FLASHSERIAL1", "AA:BB:CC", "SYS12345", "10:20:30", "profil", "profile", "child", "enfant"))
            assertFalse(text.contains(secret, ignoreCase = true), "report must not contain « $secret »: $text")
        assertTrue(text.lines().drop(2).filter { it.isNotEmpty() }.all { it.startsWith("contract=") })
    }

    @Test fun anOldRentalsFileStaysReadable() {
        val dir = Kit.tmp(); val r = File(dir, "r"); r.mkdirs()
        File(r, "rentals.json").writeText("""{"clock":{"lastSeen":$t0,"floor":0},"contracts":{"loc-cm2@$t0":{"used":42,"phase":"LIVE","reason":null,"expiredAt":0,"lots":["learn:cm2"],"removed":[]}},"log":[]}""")
        val ledger = RentalLedger(r, TvClock(), RentalConfig(), { t0 })
        assertEquals(42L, ledger.usedMinutes("loc-cm2@$t0")); assertEquals(setOf("learn:cm2"), ledger.rentedLots("loc-cm2@$t0")); assertFalse(ledger.degraded)
        val before = File(r, "rentals.json").readText()
        ledger.recordUsage(cm2, 1, listOf(act(rental("loc-cm2", 600))))
        assertEquals(43L, RentalLedger(r, TvClock(), RentalConfig(), { t0 }).usedMinutes("loc-cm2@$t0"))
        assertFalse(File(r, "rentals.json").readText().contains("lastUse"), "the file format is unchanged: $before")
    }
}
