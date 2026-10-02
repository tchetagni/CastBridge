package castbridge.core.lots

import castbridge.core.owner.*
import java.io.File
import java.io.IOException
import kotlin.test.*

private const val DAY = 24L * 3600 * 1000
private const val T = 1_800_000_000_000L

/** The phone's delivery of rented lots, against a TV simulated in process (same objects as [RentalApiTest], reached through a [TvTransport]). */
class RentalDeliveryTest {
    private val fp = DeviceIdentity.fingerprints(RawFactors(flashSerial = "FLASHSERIAL1", flashCid = "cid-1", ethernetMac = "AA:BB:CC:00:11:22", systemSerial = "SYS12345",
        wifiMac = "10:20:30:40:50:60", wifiSysfsPath = "/sys/devices/platform/soc/ffe03000.sd/mmc_host/mmc1/net/wlan0", bluetoothAddress = "11:22:33:44:55:66"))
    private val issuer = ActivationIssuer(Ed25519Signer(ByteArray(32) { (it + 7).toByte() }))
    private val master = ByteArray(32) { (it * 3 + 1).toByte() }
    private val license = "lic-secret-1"

    private inner class Tv(var wall: Long = T) : TvTransport {
        val dir: File = Kit.tmp()
        val vault = RentalVault(File(dir, "rental"))
        val ledger = RentalLedger(File(dir, "rental"), TvClock(), RentalConfig(), { wall })
        val learn = FakeConsumer("learn"); val quiz = FakeConsumer("quiz")
        val store = TvLotStore(File(dir, "lots"), mapOf("learn" to learn, "quiz" to quiz), listOf(Kit.pub), 10, { 0 }, LotBudget.TV_MAX_BYTES, { wall })
        val installed = ArrayList<Activation>()
        val sweeper = RentalSweeper(ledger, vault, TvRentedLots(store), { installed.toList() }, { emptySet() }, { wall })
        val exts = listOf<castbridge.core.tv.ApiExtension>(TvLotApi(store), RentalApi(store, ledger, vault, { installed.toList() }, sweeper))
        val log = ArrayList<String>()
        var failAfterUploads = Int.MAX_VALUE
        var uploads = 0
        fun install(a: Activation) { installed += a; ledger.install(a, installed.toList(), fp, vault) }
        override fun call(method: String, path: String, params: Map<String, String>, body: ByteArray?): TvReply {
            log += "$method $path " + params.entries.joinToString(",") { "${it.key}=${it.value}" }
            if (path == "/api/lots/upload" && ++uploads > failAfterUploads) throw IOException("link down")
            for (e in exts) {
                val r = if (body != null && e.wantsBody(path)) e.handleBody(path, method, params, body) else e.handle(path, method, params)
                if (r != null) return TvReply(r.status, r.json)
            }
            return TvReply(404, "{}")
        }
    }

    private fun rentalRight(days: Int, product: String = "loc-cm2"): Right.Rental {
        val key = RentalKeys.rentalKey(master, license, SeatIds.of(license, fp), product, T)
        return Right.Rental(product, listOf("classe-cm2"), T, T, days, 0L, 0, 0, RentalKeys.makeBox(fp, DeviceIdentity.kFor(fp.n), key, product, T))
    }
    private fun activation(vararg rights: Right): Activation = Activation.decode(issuer.issue(ActivationIssuer.Request(ActivationKind.PRODUCTION, DeviceCode.of(fp), fp, T,
        rights = rights.toList(), license = license, seat = SeatIds.of(license, fp), windowHours = 48)).token)!!
    private val contract = RentalEngine.contractKey("loc-cm2", T)
    private fun key() = RentalKeys.rentalKey(master, license, SeatIds.of(license, fp), "loc-cm2", T)

    private class Fixture(val ids: List<LotId>, val datas: List<ByteArray>, val metas: List<LotMeta>, val catalog: String)
    private fun fixture(vararg sizes: Int): Fixture {
        val ids = sizes.indices.map { LotId("learn", "cm${it + 1}") }
        val datas = sizes.mapIndexed { i, s -> Kit.bytes(i + 3, s) }
        val metas = ids.indices.map { Kit.meta("learn", ids[it].scope, 1, datas[it]) }
        return Fixture(ids, datas, metas, Kit.sign(metas).toJson())
    }
    private fun sealed(f: Fixture) = f.ids.indices.map { SealedLot.ofBytes(LotNames.fileName(f.ids[it], 1), RentalKeys.seal(key(), f.ids[it], 1, f.datas[it])) }

    @Test fun deliversEveryLotInChunksAndReportsProgress() {
        val tv = Tv(); tv.install(activation(rentalRight(3)))
        val f = fixture(1_300_000, 4000)
        val seen = ArrayList<Triple<String, Long, Long>>()
        val rep = RentalDelivery(tv).deliver(contract, f.catalog, sealed(f), progress = { n, s, t -> seen += Triple(n, s, t) })
        assertTrue(rep.complete, rep.summary); assertEquals(2, rep.installed)
        assertEquals(f.ids.toSet(), tv.learn.held.keys)
        val chunks = tv.log.count { it.startsWith("POST /api/lots/upload") && "cm1" in it }
        assertEquals(3, chunks, "1.3 MB sealed = 3 chunks of at most 512 KB")
        val big = seen.filter { "cm1" in it.first }
        assertEquals(0L, big.first().second); assertEquals(big.first().third, big.last().second)
        assertTrue(big.map { it.second }.zipWithNext().all { (a, b) -> b >= a }, "progress never goes back")
        assertTrue("2 lot(s) installé(s)" in rep.summary, rep.summary)
        assertEquals(2, RentalDelivery(tv).status().find(contract)!!.lots.size)
    }

    @Test fun neverSendsAgainALotTheTvAlreadyHolds() {
        val tv = Tv(); tv.install(activation(rentalRight(3)))
        val f = fixture(5000, 6000)
        RentalDelivery(tv).deliver(contract, f.catalog, sealed(f)); tv.log.clear()
        val rep = RentalDelivery(tv).deliver(contract, f.catalog, sealed(f))
        assertEquals(listOf(DeliveryOutcome.ALREADY_ON_TV, DeliveryOutcome.ALREADY_ON_TV), rep.results.map { it.outcome })
        assertTrue(tv.log.none { it.startsWith("POST") }, "nothing is posted: " + tv.log)
        assertTrue("2 déjà présent(s)" in rep.summary, rep.summary)
        // a new lot alongside the held ones: only that one travels
        val g = fixture(5000, 6000, 7000); tv.log.clear()
        val rep2 = RentalDelivery(tv).deliver(contract, g.catalog, sealed(g))
        assertEquals(1, rep2.installed)
        assertTrue(tv.log.filter { it.startsWith("POST /api/lots/upload") }.all { "cm3" in it })
    }

    @Test fun resumesAfterAnInterruptionFromWhatTheTvAlreadyHolds() {
        val tv = Tv(); tv.install(activation(rentalRight(3)))
        val f = fixture(1_300_000)
        tv.failAfterUploads = 2
        val r1 = RentalDelivery(tv).deliver(contract, f.catalog, sealed(f))
        assertEquals(DeliveryOutcome.LINK_DOWN, r1.results.single().outcome); assertTrue("reprendra" in r1.summary, r1.summary)
        assertTrue(tv.learn.held.isEmpty())
        tv.failAfterUploads = Int.MAX_VALUE; tv.log.clear()
        val r2 = RentalDelivery(tv).deliver(contract, f.catalog, sealed(f))
        assertTrue(r2.complete, r2.summary); assertTrue(f.ids[0] in tv.learn.held)
        val ups = tv.log.filter { it.startsWith("POST /api/lots/upload") }
        assertEquals(1, ups.size, "only the last chunk is sent again: $ups"); assertTrue("offset=${2 * TvLotApi.CHUNK}" in ups[0], ups[0])
    }

    @Test fun skipsLotsOfAnEndedRentalAndExplainsWhenTheRentalIsNotOnTheTv() {
        val tv = Tv(); tv.install(activation(rentalRight(1)))
        tv.wall = T + 2 * DAY
        tv.api().handle("/api/rental/sweep", "POST", emptyMap())
        val f = fixture(5000); tv.log.clear()
        val rep = RentalDelivery(tv).deliver(contract, f.catalog, sealed(f))
        assertEquals(DeliveryOutcome.SKIPPED_ENDED, rep.results.single().outcome); assertTrue("terminée" in rep.summary, rep.summary)
        assertTrue(tv.log.none { it.startsWith("POST") })
        val none = Tv()
        val rep2 = RentalDelivery(none).deliver(contract, f.catalog, sealed(f))
        assertTrue(rep2.results.isEmpty() && "pas encore activée" in rep2.summary, rep2.summary)
    }

    @Test fun refusesALotOutsideTheSignedCatalogAndReportsTheTvRefusal() {
        val tv = Tv(); tv.install(activation(rentalRight(3)))
        val f = fixture(5000); val other = fixture(5000, 6000)
        val stray = sealed(other)[1]
        val rep = RentalDelivery(tv).deliver(contract, f.catalog, sealed(f) + stray)
        assertEquals(listOf(DeliveryOutcome.INSTALLED, DeliveryOutcome.REFUSED), rep.results.map { it.outcome })
        // a lot sealed with another key: the TV cannot open it and says so
        val bad = SealedLot.ofBytes(LotNames.fileName(f.ids[0], 1), RentalKeys.seal(ByteArray(32) { 9 }, f.ids[0], 1, f.datas[0]))
        val tv2 = Tv(); tv2.install(activation(rentalRight(3)))
        val r2 = RentalDelivery(tv2).deliver(contract, f.catalog, listOf(bad))
        assertEquals(DeliveryOutcome.REFUSED, r2.results.single().outcome); assertTrue("pas chiffré pour cette TV" in r2.results.single().detail, r2.summary)
    }

    @Test fun unreachableTvGivesAFrenchMessage() {
        val rep = RentalDelivery { _, _, _, _ -> throw IOException("down") }.deliver(contract, "{}", emptyList())
        assertEquals("La TV n'est pas à portée.", rep.summary)
        assertFalse(RentalDelivery { _, _, _, _ -> throw IOException("down") }.status().reachable)
    }

    private fun Tv.api() = exts[1]
}
