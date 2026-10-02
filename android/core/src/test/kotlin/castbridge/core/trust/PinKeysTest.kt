package castbridge.core.trust

import kotlin.test.*

class PinKeysTest {
    private data class Row(val label: String, val name: String?, val mdns: String?, val bt: String?, val host: List<String>, val port: Int?, val keys: List<String>)
    private val rows = listOf(
        Row("tout", "Salon", "CastBridge TV Salon", "AA:BB:CC", listOf("192.168.0.5"), 8765, listOf("Salon", "CastBridge TV Salon", "bt:AA:BB:CC", "192.168.0.5:8765")),
        Row("hôte sans port : 8765", "Salon", null, null, listOf("192.168.0.5"), null, listOf("Salon", "192.168.0.5:8765")),
        Row("port propre", null, null, null, listOf("10.0.0.2"), 9000, listOf("10.0.0.2:9000")),
        Row("rien", null, null, null, emptyList(), null, emptyList()),
        Row("doublon nom = mdns", "Salon", "Salon", null, emptyList(), null, listOf("Salon")),
        Row("vides ignorés", " ", "", "", listOf(""), null, emptyList()),
        Row("Bluetooth seul", null, null, "11:22", emptyList(), null, listOf("bt:11:22")),
        Row("nom avec espaces", " Salon ", null, null, emptyList(), null, listOf("Salon")),
    )

    @Test fun keysOf() { for (r in rows) assertEquals(r.keys, PinKeys.keysOf(r.name, r.mdns, r.bt, r.host, r.port), r.label) }

    @Test fun severalIps() {
        assertEquals(listOf("Salon", "192.168.0.5:8765", "10.0.0.9:8765"), PinKeys.keysOf("Salon", null, null, listOf("192.168.0.5", "10.0.0.9", "192.168.0.5"), null))
    }

    @Test fun hostnameAndIpv6() {
        assertEquals(listOf("tv.local:8765"), PinKeys.keysOf(null, null, null, listOf("tv.local"), null))
        assertEquals("tv.local", PinKeys.normalize("tv.local"))
        assertEquals(listOf("[fe80::1]:9000"), PinKeys.keysOf(null, null, null, listOf("fe80::1"), 9000))
        assertEquals("fe80::1", PinKeys.normalize("fe80::1"))
    }

    @Test fun lookupFindsLegacyBareHostEntry() {
        val k = PinKeys.lookupKeys("Salon", null, null, listOf("192.168.0.5"), 8765)
        assertTrue("192.168.0.5" in k)           // legacy: TvScreen.kt:77 stored under the bare host
        assertTrue("192.168.0.5:8765" in k)
        assertEquals(k.distinct(), k)
        assertEquals(PinKeys.keysOf("Salon", null, null, listOf("192.168.0.5"), 8765), k.take(2))   // normalized first
    }

    @Test fun lookupWithoutHostsEqualsKeys() {
        assertEquals(PinKeys.keysOf("Salon", "CastBridge TV Salon", "AA", emptyList(), null), PinKeys.lookupKeys("Salon", "CastBridge TV Salon", "AA", emptyList(), null))
    }

    @Test fun normalize() {
        assertEquals("192.168.0.5:8765", PinKeys.normalize("192.168.0.5"))
        assertEquals("192.168.0.5:8765", PinKeys.normalize(" 192.168.0.5 "))
        assertEquals("192.168.0.5:9000", PinKeys.normalize("192.168.0.5:9000"))
        assertEquals("bt:AA:BB", PinKeys.normalize("bt:AA:BB"))
        assertEquals("CastBridge TV Salon", PinKeys.normalize("CastBridge TV Salon"))
        assertEquals("Salon", PinKeys.normalize("Salon"))
        assertEquals("", PinKeys.normalize("  "))
    }

    @Test fun normalizeIsIdempotent() { for (k in listOf("1.2.3.4", "1.2.3.4:1", "Salon", "bt:X")) assertEquals(PinKeys.normalize(k), PinKeys.normalize(PinKeys.normalize(k))) }

    // ---- PinFallback (R-01, second half)
    @Test fun tokenIsUsedWhenLive() {
        val c = PinFallback.choose("cbt_x", "1234", trustedTv = true, tokenRefused = false)
        assertEquals(PinFallback.Source.TOKEN, c.source); assertEquals("cbt_x", c.credential); assertNull(c.reason)
    }

    @Test fun refusedTokenOfTrustedTvNeverFallsBackToOldPin() {
        for (token in listOf(null, "cbt_x")) {
            val c = PinFallback.choose(token, "1234", trustedTv = true, tokenRefused = true)
            assertEquals(PinFallback.Source.NONE, c.source); assertEquals("", c.credential); assertEquals(PinFallback.REFUSED, c.reason)
        }
    }

    @Test fun pinStillUsedWhenNothingTrusts() {
        assertEquals("1234", PinFallback.choose(null, "1234", trustedTv = false, tokenRefused = false).credential)
        assertEquals("1234", PinFallback.choose(null, "1234", trustedTv = true, tokenRefused = false).credential)   // session not up yet, nothing refused
        assertEquals("1234", PinFallback.choose(null, "1234", trustedTv = false, tokenRefused = true).credential)
    }

    @Test fun nothingAtAllAsksForTheCode() {
        val c = PinFallback.choose(null, "", trustedTv = false, tokenRefused = false)
        assertEquals(PinFallback.Source.NONE, c.source); assertEquals(PinFallback.NEEDS_CODE, c.reason)
        assertEquals(PinFallback.NEEDS_CODE, PinFallback.choose("", "  ", trustedTv = true, tokenRefused = false).reason)
    }
}
