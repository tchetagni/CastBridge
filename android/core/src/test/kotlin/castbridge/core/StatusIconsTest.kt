package castbridge.core

import castbridge.core.net.NetState
import castbridge.core.status.*
import kotlin.test.*

class StatusIconsTest {
    private var t = 1_000_000L
    private fun model(max: Int = 6) = StatusIconModel({ t }, 20_000, max)
    private val P = IconKind.PHONE

    @Test fun `one phone by Bluetooth and Wi-Fi is one icon with the best technology and a secondary mark`() {
        val m = model()
        m.up(P, "AA:01", Tech.BLUETOOTH, "Pixel de Léa"); m.up(P, "AA:01", Tech.WIFI_LAN, "Pixel de Léa")
        val i = m.snapshot().icons.single()
        assertEquals(Tech.WIFI_LAN, i.tech); assertEquals(Tech.BLUETOOTH, i.secondary); assertEquals(IconState.CONNECTED, i.state)
    }

    @Test fun `best technology order`() {
        val m = model()
        m.up(P, "x", Tech.BLUETOOTH, "a"); m.up(P, "x", Tech.WIFI_DIRECT, "a")
        assertEquals(Tech.WIFI_DIRECT, m.snapshot().icons.single().tech)
        m.up(P, "x", Tech.ETHERNET, "a")
        assertEquals(Tech.ETHERNET, m.snapshot().icons.single().tech)
        assertTrue(Tech.WIFI_LAN.rank > Tech.WIFI_DIRECT.rank && Tech.WIFI_DIRECT.rank > Tech.BLUETOOTH.rank && Tech.SSH_OVER_LAN.rank > Tech.SSH_OVER_BLUETOOTH.rank)
    }

    @Test fun `two phones are two icons and never the same device twice`() {
        val m = model()
        m.up(P, "1", Tech.BLUETOOTH, "A"); m.up(P, "2", Tech.WIFI_LAN, "B"); m.up(P, "1", Tech.BLUETOOTH, "A")
        assertEquals(listOf("A", "B"), m.snapshot().icons.map { it.label })
    }

    @Test fun `a drop is held as degraded then removed after a stable absence, since is kept on return`() {
        val m = model()
        m.up(P, "1", Tech.BLUETOOTH, "A"); val since = m.snapshot().icons.single().since
        t += 5_000; m.down(P, "1", Tech.BLUETOOTH)
        t += 10_000
        assertEquals(IconState.DEGRADED, m.snapshot().icons.single().state)
        t += 5_000; m.up(P, "1", Tech.BLUETOOTH, "A")                      // back inside 20 s
        assertEquals(IconState.CONNECTED, m.snapshot().icons.single().state); assertEquals(since, m.snapshot().icons.single().since)
        m.down(P, "1"); t += 19_999
        assertEquals(1, m.snapshot().icons.size)
        t += 1
        assertTrue(m.snapshot().icons.isEmpty())
        m.up(P, "1", Tech.BLUETOOTH, "A"); assertEquals(t, m.snapshot().icons.single().since)   // new life
    }

    @Test fun `up after the hold but before a snapshot is a new life`() {
        val m = model(); m.up(P, "1", Tech.BLUETOOTH, "A"); m.down(P, "1"); t += 30_000
        m.up(P, "1", Tech.WIFI_LAN, "A")
        assertEquals(t, m.snapshot().icons.single().since); assertNull(m.snapshot().icons.single().secondary)
    }

    @Test fun `losing the best link falls back to the other one without degrading`() {
        val m = model(); m.up(P, "1", Tech.BLUETOOTH, "A"); m.up(P, "1", Tech.WIFI_LAN, "A"); m.down(P, "1", Tech.WIFI_LAN)
        val i = m.snapshot().icons.single()
        assertEquals(Tech.BLUETOOTH, i.tech); assertEquals(IconState.CONNECTED, i.state)
    }

    @Test fun `a lease that is not renewed ends like a drop`() {
        val m = model(); m.up(P, "1", Tech.WIFI_LAN, "A", leaseMs = 60_000)
        t += 59_000; assertEquals(IconState.CONNECTED, m.snapshot().icons.single().state)
        t += 2_000; assertEquals(IconState.DEGRADED, m.snapshot().icons.single().state)
        t += 20_000; assertTrue(m.snapshot().icons.isEmpty())
        m.up(P, "1", Tech.WIFI_LAN, "A", leaseMs = 60_000); t += 30_000; m.up(P, "1", Tech.WIFI_LAN, "A", leaseMs = 60_000); t += 40_000
        assertEquals(IconState.CONNECTED, m.snapshot().icons.single().state)   // renewed
    }

    @Test fun `cap shows six then a plus N chip and keeps errors visible`() {
        val m = model()
        for (i in 1..8) m.up(P, "p$i", Tech.WIFI_LAN, "P$i")
        var b = m.snapshot(); assertEquals(6, b.icons.size); assertEquals(2, b.hidden); assertEquals(8, b.all.size)
        m.up(P, "p8", Tech.WIFI_LAN, "P8", state = IconState.ERROR)
        b = m.snapshot(); assertTrue(b.icons.any { it.label == "P8" }); assertEquals(6, b.icons.size)
        assertEquals(b.icons.map { it.id }, b.icons.map { it.id }.distinct())
    }

    @Test fun `order is stable whatever the arrival order and state`() {
        val m = model()
        m.up(IconKind.DOWNLOAD, "", Tech.NONE, "dl"); m.up(IconKind.SSH, "", Tech.SSH_OVER_LAN, "SSH"); m.setInternet(NetState.INTERNET_WIFI)
        t += 10; m.up(P, "b", Tech.BLUETOOTH, "B"); t += 10; m.up(P, "a", Tech.BLUETOOTH, "A")
        val order = m.snapshot().icons.map { it.kind to it.label }
        assertEquals(listOf(IconKind.INTERNET, P, P, IconKind.SSH, IconKind.DOWNLOAD), order.map { it.first })
        assertEquals(listOf("B", "A"), order.filter { it.first == P }.map { it.second })    // by arrival, not by name
        m.down(P, "b"); assertEquals(order, m.snapshot().icons.map { it.kind to it.label })   // degraded keeps its place
    }

    @Test fun `internet is one entry that follows NetState`() {
        val m = model()
        m.setInternet(NetState.CHECKING); assertEquals(IconState.CONNECTING, m.snapshot().icons.single().state)
        m.setInternet(NetState.INTERNET_ETHERNET)
        var i = m.snapshot().icons.single(); assertEquals(Tech.ETHERNET, i.tech); assertEquals(IconState.CONNECTED, i.state)
        m.setInternet(NetState.INTERNET_VIA_PHONE); assertEquals(Tech.BLUETOOTH, m.snapshot().icons.single().tech)
        t += 100_000; m.setInternet(NetState.NONE); i = m.snapshot().icons.single()
        assertEquals(IconState.ERROR, i.state); assertEquals(t, i.since); assertEquals(1, m.snapshot().icons.size)
    }

    @Test fun `ssh sessions merge into one entry with a count and the best path`() {
        val m = model()
        m.setSsh(0); assertTrue(m.snapshot().icons.isEmpty())
        m.setSsh(3, viaBluetooth = 1)
        var i = m.snapshot().icons.single(); assertEquals(IconKind.SSH, i.kind); assertEquals(3, i.count)
        assertEquals(Tech.SSH_OVER_LAN, i.tech); assertEquals(Tech.SSH_OVER_BLUETOOTH, i.secondary)
        m.setSsh(1, viaBluetooth = 1); i = m.snapshot().icons.single(); assertEquals(Tech.SSH_OVER_BLUETOOTH, i.tech); assertEquals(1, i.count); assertNull(i.secondary)
        m.setSsh(0); t += 21_000; assertTrue(m.snapshot().icons.isEmpty())
    }

    @Test fun `labels are sanitized`() {
        assertEquals("Pixel de Léa", StatusIconModel.cleanLabel("  Pixel \n\t de\u0000 Léa‮ ", "x"))
        assertEquals("x", StatusIconModel.cleanLabel("\u0007​  ", "x"))
        val long = StatusIconModel.cleanLabel("a".repeat(100), "x")
        assertEquals(StatusIconModel.MAX_LABEL, long.length); assertTrue(long.endsWith("…"))
        val emoji = StatusIconModel.cleanLabel("😀".repeat(40), "x"); assertEquals(StatusIconModel.MAX_LABEL, emoji.codePointCount(0, emoji.length))
        val m = model(); m.up(P, "1", Tech.BLUETOOTH, "A\u0000B\nC"); assertEquals("AB C", m.snapshot().icons.single().label)
    }

    @Test fun `api json has no token, pin or address`() {
        val m = model(); m.setInternet(NetState.INTERNET_WIFI)
        m.up(P, "AA:BB:CC:DD:EE:FF", Tech.BLUETOOTH, "Léa \"phone\""); m.up(P, "AA:BB:CC:DD:EE:FF", Tech.WIFI_LAN, "Léa \"phone\"")
        val j = StatusIconModel.json(m.snapshot(), t)
        assertFalse("AA:BB" in j); assertFalse("ref" in j); assertFalse("token" in j.lowercase()); assertFalse("pin" in j.lowercase())
        assertTrue("""{"kind":"phone","technology":"wifi_lan","secondary":"bluetooth","label":"Léa \"phone\"","state":"connected"""" in j)
        assertTrue(j.startsWith("""{"connections":[{"kind":"internet""")); assertTrue(""""hidden":0""" in j)
    }

    @Test fun `tech from ip`() { assertEquals(Tech.WIFI_DIRECT, Tech.fromIp("192.168.49.12")); assertEquals(Tech.WIFI_LAN, Tech.fromIp("192.168.1.9")); assertEquals(Tech.WIFI_LAN, Tech.fromIp(null)) }

    @Test fun `chip text and since text are French and carry more than colour`() {
        val m = model(); m.up(P, "1", Tech.BLUETOOTH, "Léa"); m.setSsh(2)
        assertEquals("Léa · Bluetooth", m.snapshot().icons[0].text()); assertEquals("SSH ×2 · SSH (Wi-Fi)", m.snapshot().icons[1].text())
        m.down(P, "1"); assertEquals("Léa · Bluetooth · reconnexion", m.snapshot().icons[0].text())
        val i = m.snapshot().icons[0]
        assertEquals("depuis moins d'une minute", i.sinceText(i.since + 30_000)); assertEquals("depuis 5 min", i.sinceText(i.since + 300_000)); assertEquals("depuis 1 h 2 min", i.sinceText(i.since + 3_720_000))
    }
}
