package castbridge.core.trust

import castbridge.core.tv.BtProtocol
import kotlin.test.*

class LinkRefusalsTest {
    private var t = 1_000L
    private val p = MemoryTrustPersistence()
    private fun store() = LinkRefusals(p) { t }

    @Test fun recordsTheLatestRefusalPerTv() {
        val s = store()
        s.record("AA:BB", BtProtocol.ERR_UNTRUSTED); t = 2_000; s.record("CC:DD", BtProtocol.ERR_DENIED); t = 3_000; s.record("AA:BB", BtProtocol.ERR_SPACE)
        assertEquals(RefusalRecord(BtProtocol.ERR_SPACE, 3_000), s.latest("AA:BB"))
        assertEquals(RefusalRecord(BtProtocol.ERR_DENIED, 2_000), s.latest("CC:DD"))
        assertEquals(RefusalRecord(BtProtocol.ERR_SPACE, 3_000), s.latestAny())
        assertNull(s.latest("EE"))
    }

    @Test fun survivesARestartAndClears() {
        store().record("AA:BB", 8)
        val again = store()
        assertEquals(8, again.latest("AA:BB")?.code)
        again.clear("AA:BB")
        assertNull(store().latest("AA:BB"))
        again.record("X", 8); again.clearAll(); assertNull(store().latestAny())
    }

    @Test fun damagedTextIsIgnoredNotFatal() {
        p.text = "n'importe quoi\n\t\t\nAA\t8\tpas-un-nombre\nBB\t8\t5"
        val s = store()
        assertEquals(RefusalRecord(8, 5), s.latest("BB"))
        assertNull(s.latest("AA"))
    }

    @Test fun keyWithSeparatorsCannotCorruptTheStore() {
        val s = store(); s.record("A\tB\nC", 8)
        assertEquals(8, store().latest("A\tB\nC")?.code)
    }

    @Test fun storeIsBounded() {
        val s = store(); repeat(50) { t++; s.record("TV$it", 8) }
        assertTrue((p.text ?: "").lines().size <= 16)
        assertNotNull(store().latest("TV49")); assertNull(store().latest("TV0"))
    }
}
