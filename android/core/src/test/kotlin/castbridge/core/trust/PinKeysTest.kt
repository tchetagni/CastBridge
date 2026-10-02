package castbridge.core.trust

import kotlin.test.*

class PinKeysTest {
    private data class Row(val label: String, val name: String?, val mdns: String?, val bt: String?, val host: String?, val port: Int?, val keys: List<String>)
    private val rows = listOf(
        Row("tout", "Salon", "CastBridge TV Salon", "AA:BB:CC", "192.168.0.5", 8765, listOf("Salon", "CastBridge TV Salon", "bt:AA:BB:CC", "192.168.0.5:8765")),
        Row("hôte sans port : 8765", "Salon", null, null, "192.168.0.5", null, listOf("Salon", "192.168.0.5:8765")),
        Row("port propre", null, null, null, "10.0.0.2", 9000, listOf("10.0.0.2:9000")),
        Row("rien", null, null, null, null, null, emptyList()),
        Row("doublon nom = mdns", "Salon", "Salon", null, null, null, listOf("Salon")),
        Row("vides ignorés", " ", "", "", "", null, emptyList()),
        Row("Bluetooth seul", null, null, "11:22", null, null, listOf("bt:11:22")),
        Row("nom avec espaces", " Salon ", null, null, null, null, listOf("Salon")),
    )

    @Test fun keysOf() { for (r in rows) assertEquals(r.keys, PinKeys.keysOf(r.name, r.mdns, r.bt, r.host, r.port), r.label) }

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
}
