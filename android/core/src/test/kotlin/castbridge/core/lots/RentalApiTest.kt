package castbridge.core.lots

import castbridge.core.owner.*
import castbridge.core.net.JsonLite
import java.io.File
import kotlin.test.*

private const val DAY = 24L * 3600 * 1000
private const val T = 1_800_000_000_000L
private const val INSTALL = "0123456789abcdef"

/**
 * The whole path of a rented lot over the TV's API, in process: an activation carries the rental (its key wrapped for this TV), the lot arrives SEALED through the normal chunked upload,
 * /api/rental/install opens it with the contract key, the normal store verifies and installs it, the ledger registers it as rented, and the sweep deletes it when the rental ends,
 * except in an account activated by the super administrator code.
 */
class RentalApiTest {
    private val fp = DeviceIdentity.fingerprints(RawFactors(flashSerial = "FLASHSERIAL1", flashCid = "cid-1", ethernetMac = "AA:BB:CC:00:11:22", systemSerial = "SYS12345",
        wifiMac = "10:20:30:40:50:60", wifiSysfsPath = "/sys/devices/platform/soc/ffe03000.sd/mmc_host/mmc1/net/wlan0", bluetoothAddress = "11:22:33:44:55:66"))
    private val other = DeviceIdentity.fingerprints(RawFactors(flashSerial = "OTHERFLASH", flashCid = "cid-9", ethernetMac = "AA:BB:CC:99:99:99", systemSerial = "SYS99999"))
    private val signer = Ed25519Signer(ByteArray(32) { (it + 7).toByte() })
    private val issuer = ActivationIssuer(signer)
    private val master = ByteArray(32) { (it * 3 + 1).toByte() }
    private val license = "lic-secret-1"

    private inner class Tv(var wall: Long = T) {
        val dir: File = Kit.tmp()
        val vault = RentalVault(File(dir, "rental"))
        val ledger = RentalLedger(File(dir, "rental"), TvClock(), RentalConfig(), { wall })
        val learn = FakeConsumer("learn"); val quiz = FakeConsumer("quiz")
        val store = TvLotStore(File(dir, "lots"), mapOf("learn" to learn, "quiz" to quiz), listOf(Kit.pub), 10, { 0 }, LotBudget.TV_MAX_BYTES, { wall })
        val installed = ArrayList<Activation>()
        val sweeper = RentalSweeper(ledger, vault, TvRentedLots(store), { installed.toList() }, { emptySet() }, { wall })
        val lotApi = TvLotApi(store)
        val api = RentalApi(store, ledger, vault, { installed.toList() }, sweeper, installId = { INSTALL })
        fun rent() { ledger.markRented(contractKey(), id, meta, LotFamilies.explicit(emptySet(), setOf("learn:cm2"))) }
        fun install(a: Activation) { installed += a; ledger.install(a, installed.toList(), fp, vault) }
        fun upload(name: String, sealed: ByteArray) = lotApi.handleBody("/api/lots/upload", "POST", mapOf("name" to name, "offset" to "0", "total" to sealed.size.toString()), sealed)!!
        fun rentalInstall(name: String, contract: String, catalog: String) = api.handleBody("/api/rental/install", "POST", mapOf("name" to name, "contract" to contract), catalog.toByteArray())!!
    }

    private fun rentalRight(days: Int, device: Fingerprints = fp, product: String = "loc-cm2", start: Long = T): Right.Rental {
        val key = RentalKeys.rentalKey(master, license, SeatIds.of(license, device), product, start)
        return Right.Rental(product, listOf("classe-cm2"), start, start, days, 0L, 0, 0, RentalKeys.makeBox(device, DeviceIdentity.kFor(device.n), key, product, start))
    }
    private fun hourRight(usageMinutes: Int, days: Int = 30, product: String = "loc-cm2", device: Fingerprints = fp): Right.Rental {
        val key = RentalKeys.rentalKey(master, license, SeatIds.of(license, device), product, T)
        return Right.Rental(product, listOf("classe-cm2"), T, T, days, 0L, usageMinutes, 3, RentalKeys.makeBox(device, DeviceIdentity.kFor(device.n), key, product, T))
    }
    private fun activation(vararg rights: Right, at: Long = T): Activation = Activation.decode(issuer.issue(ActivationIssuer.Request(ActivationKind.PRODUCTION, DeviceCode.of(fp), fp, at,
        rights = rights.toList(), license = license, seat = SeatIds.of(license, fp), windowHours = 48)).token)!!
    private fun contractKey(product: String = "loc-cm2", start: Long = T) = RentalEngine.contractKey(product, start)
    private fun key() = RentalKeys.rentalKey(master, license, SeatIds.of(license, fp), "loc-cm2", T)

    private val id = LotId("learn", "cm2")
    private val data = Kit.bytes(7, 6000)
    private val meta = Kit.meta("learn", "cm2", 1, data)
    private val catalog = Kit.sign(listOf(meta)).toJson()
    private val name = LotNames.fileName(id, 1)

    @Test fun aSealedLotIsOpenedInstalledRegisteredAndDeletedWhenTheRentalEnds() {
        val tv = Tv(); val a = activation(rentalRight(days = 2)); tv.install(a)
        assertTrue(tv.vault.hasKey(contractKey()), "the key of the rental came out of the activation box")
        assertEquals(200, tv.upload(name, RentalKeys.seal(key(), id, 1, data)).status)
        val r = tv.rentalInstall(name, contractKey(), catalog)
        assertEquals(200, r.status, r.json)
        assertTrue(id in tv.learn.held, "the plain lot is installed in the feature")
        assertEquals(listOf("learn:cm2"), tv.ledger.rec(contractKey())!!.lots.toList())
        val live = JsonLite.obj(tv.api.statusJson())
        assertEquals(false, live["superUnlimited"]); assertEquals("ACTIVE", ((live["rentals"] as List<*>)[0] as Map<*, *>)["state"])
        // two days and a bit later: the rental has ended, the sweep deletes the key and the lot by itself
        tv.wall = T + 2 * DAY + 1000
        val s = tv.api.handle("/api/rental/sweep", "POST", emptyMap())!!
        assertEquals(200, s.status, s.json)
        assertFalse(id in tv.learn.held, "the rented lot is deleted automatically at the end")
        assertFalse(tv.vault.hasKey(contractKey()), "and its key is destroyed")
    }

    @Test fun anAccountActivatedBySuperUnlimitedKeepsTheRentedLotForGood() {
        val tv = Tv(); tv.install(activation(rentalRight(days = 2), Right.Super("super-illimite", T)))
        tv.upload(name, RentalKeys.seal(key(), id, 1, data)); assertEquals(200, tv.rentalInstall(name, contractKey(), catalog).status)
        tv.wall = T + 400 * DAY
        tv.api.handle("/api/rental/sweep", "POST", emptyMap())
        assertTrue(id in tv.learn.held, "nothing is deleted in a SUPER_UNLIMITED account"); assertTrue(tv.vault.hasKey(contractKey()))
        val st = JsonLite.obj(tv.api.statusJson())
        assertEquals(true, st["superUnlimited"]); assertEquals("ACTIVE", ((st["rentals"] as List<*>)[0] as Map<*, *>)["state"])
    }

    @Test fun aLotSealedForAnotherTvOrWithoutItsRentalIsRefusedAndNothingIsInstalled() {
        val tv = Tv(); tv.install(activation(rentalRight(days = 2)))
        val foreign = RentalKeys.rentalKey(master, license, SeatIds.of(license, other), "loc-cm2", T)          // another TV's key
        tv.upload(name, RentalKeys.seal(foreign, id, 1, data))
        val r = tv.rentalInstall(name, contractKey(), catalog)
        assertEquals(422, r.status); assertTrue("illisible" in r.json); assertTrue(tv.learn.held.isEmpty())
        // a rental this TV does not hold
        tv.upload(name, RentalKeys.seal(key(), id, 1, data))
        assertEquals(422, tv.rentalInstall(name, contractKey("autre"), catalog).status); assertTrue(tv.learn.held.isEmpty())
        // a lot that does not match the signed catalog is refused by the normal store (and never marked rented)
        val tampered = Kit.sign(listOf(Kit.meta("learn", "cm2", 1, Kit.bytes(8, 6000)))).toJson()
        tv.upload(name, RentalKeys.seal(key(), id, 1, data))
        assertEquals(422, tv.rentalInstall(name, contractKey(), tampered).status); assertTrue(tv.learn.held.isEmpty())
        assertTrue(tv.ledger.rec(contractKey())!!.lots.isEmpty())
    }

    @Test fun aTrialSampleIsNeverRentedAndIsRemovedAgain() {
        val tv = Tv(); tv.install(activation(rentalRight(days = 2)))
        val trialMeta = meta.copy(edition = Edition.TRIAL)
        val trialCat = Kit.sign(listOf(trialMeta)).toJson()
        tv.upload(name, RentalKeys.seal(key(), id, 1, data))
        val r = tv.rentalInstall(name, contractKey(), trialCat)
        assertEquals(422, r.status, r.json); assertFalse(id in tv.learn.held, "refused as rented, so the installed copy is taken back")
    }

    @Test fun statusCarriesUnitAndUsage() {
        val tv = Tv(); tv.install(activation(hourRight(720))); tv.rent()
        tv.ledger.recordUsage(LotId("learn", "cm2"), 400, tv.installed)
        val r = (JsonLite.obj(tv.api.statusJson())["rentals"] as List<*>)[0] as Map<*, *>
        assertEquals("hours", r["unit"]); assertEquals(400L, (r["usedMinutes"] as Number).toLong()); assertEquals(720L, (r["maxUsageMinutes"] as Number).toLong())
        assertEquals(320L, (r["remainingUsageMinutes"] as Number).toLong()); assertEquals(T, (r["period"] as Number).toLong()); assertEquals(T, (r["startsAt"] as Number).toLong())
        assertFalse(r.containsKey("reason"), "no reason while the rental runs (null entries are not written)")
        for (old in listOf("contract", "product", "bundles", "state", "usable", "remainingMs", "message", "endsAt", "lots", "keyInSafe")) assertTrue(r.containsKey(old), "old key « $old » unchanged")
        // the budget is spent: the reason appears
        tv.ledger.recordUsage(LotId("learn", "cm2"), 400, tv.installed)
        val e = (JsonLite.obj(tv.api.statusJson())["rentals"] as List<*>)[0] as Map<*, *>
        assertEquals("EXPIRED", e["state"]); assertEquals("USAGE", e["reason"])
        // a rental in days: measured, no budget
        val d = Tv(); d.install(activation(rentalRight(days = 7)))
        val dr = (JsonLite.obj(d.api.statusJson())["rentals"] as List<*>)[0] as Map<*, *>
        assertEquals("days", dr["unit"]); assertEquals(0L, (dr["maxUsageMinutes"] as Number).toLong())
    }

    @Test fun usageRouteReturnsTheReport() {
        val tv = Tv(); tv.install(activation(hourRight(720))); tv.rent()
        tv.ledger.recordUsage(LotId("learn", "cm2"), 90, tv.installed)
        val r = tv.api.handle("/api/rental/usage", "GET", emptyMap())!!
        assertEquals(200, r.status); assertTrue(r.mime.startsWith("text/plain"), "plain text, not JSON: ${r.mime}")
        assertEquals(r.json, String(r.bytes!!, Charsets.UTF_8), "the same text in both forms")
        val lines = r.json.lines().filter { it.isNotEmpty() }
        assertEquals("castbridge-rental-usage-v1", lines[0]); assertEquals("install=$INSTALL", lines[1])
        assertTrue(lines[2].startsWith("contract=loc-cm2@$T|unit=hours|used=90|max=720|state=ACTIVE"), lines[2])
        for (secret in listOf(license, SeatIds.of(license, fp))) assertFalse(secret in r.json, "no licence, no seat in the statement")
        assertEquals(405, tv.api.handle("/api/rental/usage", "POST", emptyMap())!!.status)
        // without an installation id the TV says so instead of inventing one
        val noId = RentalApi(tv.store, tv.ledger, tv.vault, { tv.installed.toList() }, tv.sweeper)
        assertEquals(404, noId.handle("/api/rental/usage", "GET", emptyMap())!!.status)
    }
}
