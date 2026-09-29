package castbridge.core

import castbridge.core.tv.*
import java.io.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.random.Random
import kotlin.test.*

/** Bluetooth as the control link that switches the data to Wi-Fi (CBTN), and the buffered RFCOMM receive path. */
class BtLinkTest {
    private val dir = kotlin.io.path.createTempDirectory("btlink").toFile()
    private val guard = PinGuard("482913")
    @AfterTest fun tearDown() { dir.deleteRecursively() }

    /** One in-memory "RFCOMM" connection served by a TV thread; returns (client input, client output, TV result). */
    private fun link(negotiate: ((Boolean) -> LinkInfo)?): Triple<InputStream, OutputStream, LinkedBlockingQueue<Any>> {
        val c2s = PipedOutputStream(); val tvIn = PipedInputStream(c2s, 1 shl 16)
        val s2c = PipedOutputStream(); val clIn = PipedInputStream(s2c, 1 shl 16)
        val res = LinkedBlockingQueue<Any>()
        thread(isDaemon = true) {
            try { res.put(BtProtocol.serve(dir, tvIn, s2c, guard, "AA:BB", 0, negotiate = negotiate)) } catch (e: Exception) { res.put(e) }
            finally { runCatching { s2c.close() } }
        }
        return Triple(clIn, c2s, res)
    }

    @Test fun negotiationGivesAddressesAndWifiDirect() {
        var asked: Boolean? = null
        val (i, o, res) = link { want -> asked = want; LinkInfo(8765, listOf("192.168.1.20", "10.0.0.5"), "DIRECT-CB-CastBridge", "pass w0rd", "192.168.49.1") }
        val info = BtProtocol.negotiate(i, o, "482913", wantWifiDirect = true)
        assertEquals(true, asked)
        assertEquals(LinkInfo(8765, listOf("192.168.1.20", "10.0.0.5"), "DIRECT-CB-CastBridge", "pass w0rd", "192.168.49.1"), info)
        assertEquals(BtProtocol.OK, res.poll(3, TimeUnit.SECONDS))
    }

    @Test fun wrongPinAndOldTvs() {
        val (i, o, _) = link { LinkInfo(8765, emptyList()) }
        assertEquals(BtProtocol.ERR_PIN, assertFailsWith<BtProtocol.Refused> { BtProtocol.negotiate(i, o, "000000", false) }.code)
        val (i2, o2, _) = link(null)                                        // a TV without CBTN answers like an old one
        assertEquals(BtProtocol.ERR_MAGIC, assertFailsWith<BtProtocol.Refused> { BtProtocol.negotiate(i2, o2, "482913", false) }.code)
    }

    @Test fun cbt1StillWorksOnATvThatNegotiates() {
        val data = Random(3).nextBytes(700_000)
        val (i, o, res) = link { LinkInfo(8765, emptyList()) }
        BtProtocol.send(i, o, "f.bin", data.size.toLong(), "482913", { off -> ByteArrayInputStream(data, off.toInt(), data.size - off.toInt()) })
        assertEquals(BtProtocol.OK, res.poll(3, TimeUnit.SECONDS))
        assertContentEquals(data, File(dir, "f.bin").readBytes())
    }

    @Test fun linkInfoNeverCarriesHostNames() {
        val d = LinkInfo.decode("port=99999\nip=192.168.1.2\nip=evil.example.com\nip=1.2.3.4.5\nwd.ssid=DIRECT-x\nwd.pass=p\nwd.ip=host\nfuture=1")
        assertEquals(8765, d.port, "invalid port -> default"); assertEquals(listOf("192.168.1.2"), d.ips)
        assertEquals("DIRECT-x", d.wdSsid); assertNull(d.wdIp)
        assertEquals(LinkInfo(8765, emptyList()), LinkInfo.decode(LinkInfo(8765, emptyList()).encode()))
    }

    @Test fun plannerPrefersSharedWifiThenWifiDirectThenBluetooth() {
        val info = LinkInfo(8765, listOf("192.168.1.20", "10.0.0.5"), "DIRECT-CB-X", "secret123")
        assertEquals(listOf(LinkPlanner.Route.Lan("http://10.0.0.5:8765"), LinkPlanner.Route.Direct("DIRECT-CB-X", "secret123", "http://192.168.49.1:8765"), LinkPlanner.Route.Bluetooth),
            LinkPlanner.plan(info, { it.contains("10.0.0.5") }, canJoinWifiDirect = true))
        assertEquals(listOf(LinkPlanner.Route.Bluetooth), LinkPlanner.plan(info, { false }, canJoinWifiDirect = false))
        assertEquals(listOf(LinkPlanner.Route.Bluetooth), LinkPlanner.plan(null, { true }, true), "old TV: Bluetooth only")
    }

    @Test fun wifiDirectIsStartedForAPhoneOnlyWhenItCannotDisturbAnything() {
        assertTrue(LinkPlanner.mayStartWifiDirect(requested = true, enabledByOwner = false, tvHasNetwork = false), "no network on the TV: nothing to disturb")
        assertTrue(LinkPlanner.mayStartWifiDirect(true, enabledByOwner = true, tvHasNetwork = true))
        assertFalse(LinkPlanner.mayStartWifiDirect(true, enabledByOwner = false, tvHasNetwork = true), "the TV's own Wi-Fi comes first")
        assertFalse(LinkPlanner.mayStartWifiDirect(false, true, false))
    }

    @Test fun httpRouteGivesUpToFallBackToBluetooth() {
        var tries = 0
        val st = ResumableUpload("x.bin", 10, { tries++; "http://127.0.0.1:9" }, { ByteArrayInputStream(ByteArray(10)) }, sleep = { }, giveUpAfter = 3).run { }
        assertEquals(ResumableUpload.State.Failed("liaison perdue"), st); assertEquals(4, tries)
    }

    @Test fun bufferedReceiveKeepsEverythingThatArrivedWhenTheLinkBreaks() {
        val data = Random(5).nextBytes(900_000)
        val (i, o, res) = link(null)
        val cut = 300_123
        val out = object : OutputStream() {
            var n = 0
            override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
            override fun write(b: ByteArray, off: Int, len: Int) {
                // Header bytes first (4 + 6 + 2 + name + 8), then the file: break the link after [cut] file bytes.
                if (n + len > cut + 25) { val k = (cut + 25 - n).coerceAtLeast(0); o.write(b, off, k); n += k; o.close(); throw IOException("lost") }
                o.write(b, off, len); n += len
            }
            override fun flush() = o.flush()
        }
        assertFailsWith<IOException> { BtProtocol.send(i, out, "g.bin", data.size.toLong(), "482913", { off -> ByteArrayInputStream(data, off.toInt(), data.size - off.toInt()) }) }
        assertTrue(res.poll(3, TimeUnit.SECONDS) is IOException)
        val part = File(dir, "g.bin.part")
        assertEquals(cut.toLong(), part.length(), "the 256 kB buffer was flushed on the broken link")
        assertContentEquals(data.copyOf(cut), part.readBytes())
        val (i2, o2, res2) = link(null)                                     // resume from the part
        BtProtocol.send(i2, o2, "g.bin", data.size.toLong(), "482913", { off -> ByteArrayInputStream(data, off.toInt(), data.size - off.toInt()) })
        assertEquals(BtProtocol.OK, res2.poll(3, TimeUnit.SECONDS))
        assertContentEquals(data, File(dir, "g.bin").readBytes())
    }
}
