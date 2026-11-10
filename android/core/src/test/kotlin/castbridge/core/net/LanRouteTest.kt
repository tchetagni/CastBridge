package castbridge.core.net

import java.net.Socket
import java.net.URL
import java.net.URLConnection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Copie sans Internet : le Wi-Fi sans Internet validé n'est plus le réseau par défaut, les sockets vers la TV doivent être liés au Wi-Fi. */
class LanRouteTest {
    @Test fun privateRanges() {
        for (h in listOf("10.0.0.5", "172.16.0.1", "172.31.255.1", "192.168.1.20", "169.254.3.4")) assertTrue(LanAddress.isPrivate(h), h)
        for (h in listOf("8.8.8.8", "172.32.0.1", "172.15.0.1", "192.169.0.1", "bridge.sti-cm.com", "1.2.3", "300.1.1.1", "")) assertFalse(LanAddress.isPrivate(h), h)
    }

    @Test fun subnetMatch() {
        assertTrue(LanAddress.inSubnet("192.168.1.50", "192.168.1.20", 24))
        assertFalse(LanAddress.inSubnet("192.168.2.50", "192.168.1.20", 24))
        assertTrue(LanAddress.inSubnet("192.168.2.50", "192.168.1.20", 16))
        assertFalse(LanAddress.inSubnet("192.168.1.50", "192.168.1.20", 33))
        assertTrue(LanAddress.inSubnet("1.2.3.4", "9.9.9.9", 0))
    }

    private fun binding(log: MutableList<String>, tag: String) = object : BoundRoute.Binding {
        override fun open(url: URL): URLConnection { log += "$tag:open:${url.host}"; return URL("http://127.0.0.1/").openConnection() }
        override fun bind(s: Socket) { log += "$tag:bind" }
    }

    @Test fun lanBindingAppliesToCoveredPrivateHostsOnly() {
        val log = mutableListOf<String>(); val lan = binding(log, "lan")
        BoundRoute.setLan(lan) { LanAddress.inSubnet(it, "192.168.1.20", 24) }
        try {
            assertTrue(BoundRoute.lanApplies("192.168.1.99"))
            assertFalse(BoundRoute.lanApplies("192.168.7.99"))
            assertFalse(BoundRoute.lanApplies("8.8.8.8"))
            BoundRoute.bind("192.168.1.99", Socket()); BoundRoute.bind("8.8.8.8", Socket())
            assertEquals(listOf("lan:bind"), log)
            BoundRoute.releaseLan(lan)
            assertFalse(BoundRoute.lanApplies("192.168.1.99"))
        } finally { BoundRoute.clearLan() }
    }

    @Test fun groupBindingKeepsPriorityOverLan() {
        val log = mutableListOf<String>()
        BoundRoute.set("192.168.49.", binding(log, "grp")); BoundRoute.setLan(binding(log, "lan")) { true }
        try {
            BoundRoute.bind("192.168.49.1", Socket()); BoundRoute.bind("192.168.1.9", Socket())
            assertEquals(listOf("grp:bind", "lan:bind"), log)
        } finally { BoundRoute.clear(); BoundRoute.clearLan() }
    }

    @Test fun staleLanReleaseKeepsNewerBinding() {
        val a = binding(mutableListOf(), "a"); val b = binding(mutableListOf(), "b")
        BoundRoute.setLan(a) { true }; BoundRoute.setLan(b) { true }
        try { BoundRoute.releaseLan(a); assertTrue(BoundRoute.lanApplies("10.0.0.2")) } finally { BoundRoute.clearLan() }
    }
}
