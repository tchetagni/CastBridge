package castbridge.core.relay

import castbridge.core.gateway.GwTarget
import java.net.InetAddress
import java.net.Socket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** relay-R1 § 4 : le tuyau n'ouvre que vers les hôtes CastBridge et refuse le réseau local du téléphone (inventaire I-5). */
class RelayScopeTest {
    private fun ip(s: String) = InetAddress.getByName(s)

    /** Un « téléphone » : DNS et connexions simulés, on note ce qui a été ouvert. */
    private class Phone(val dns: Map<String, List<String>>) {
        val opened = ArrayList<String>()
        val resolved = ArrayList<String>()
        val dialer = RelayDialer(
            resolve = { h -> resolved += h; (dns[h] ?: throw java.net.UnknownHostException(h)).map { InetAddress.getByName(it) } },
            open = { a, p -> opened += "${a.hostAddress}:$p"; Socket() },
        )
    }

    @Test fun onlyCastBridgeHostsOnTheirPortsAreAllowed() {
        assertTrue(RelayScope.hostAllowed("bridge.sti-cm.com"))
        assertTrue(RelayScope.hostAllowed("BRIDGE.sti-cm.com"), "casse ignorée")
        assertTrue(RelayScope.hostAllowed("bridge.sti-cm.com."), "point final ignoré")
        for (h in listOf("example.com", "connectivitycheck.gstatic.com", "speed.cloudflare.com", "evil-bridge.sti-cm.com", "bridge.sti-cm.com.evil.net", "sti-cm.com", "", "127.0.0.1", "192.168.1.10", "::1", "localhost", "bridge.sti-cm.com@evil.net"))
            assertFalse(RelayScope.hostAllowed(h), h)
        assertTrue(RelayScope.portAllowed(443) && RelayScope.portAllowed(2200))
        for (p in listOf(0, 22, 53, 80, 8080, 8765, 2222, 65535, -1)) assertFalse(RelayScope.portAllowed(p), "port $p")
    }

    @Test fun theGameHostIsInTheListToo() {
        assertTrue(RelayScope.HOSTS.contains(RelayScope.SERVER_HOST))
        assertTrue(RelayScope.HOSTS.contains(RelayScope.PLAY_HOST), "l'hôte du service de jeu est dans la liste du cœur")
    }

    @Test fun localAndPrivateAddressesAreRecognised() {
        for (a in listOf("127.0.0.1", "127.1.2.3", "0.0.0.0", "10.0.0.5", "172.16.0.1", "172.31.255.255", "192.168.1.1", "169.254.1.1", "100.64.0.1", "100.127.255.254", "224.0.0.1", "::1", "fe80::1", "fc00::1", "fd12:3456::1", "::"))
            assertTrue(RelayScope.isLocalOrPrivate(ip(a)), a)
        for (a in listOf("79.143.185.145", "8.8.8.8", "100.63.255.255", "100.128.0.1", "172.32.0.1", "2001:4860:4860::8888", "2a00:1450::1"))
            assertFalse(RelayScope.isLocalOrPrivate(ip(a)), a)
    }

    @Test fun anAllowedHostIsOpenedByItsResolvedAddress() {
        val p = Phone(mapOf("bridge.sti-cm.com" to listOf("79.143.185.145")))
        p.dialer.connect(GwTarget("bridge.sti-cm.com", 443)).close()
        assertEquals(listOf("79.143.185.145:443"), p.opened)
        assertEquals(listOf("bridge.sti-cm.com"), p.resolved, "une seule résolution : l'adresse vérifiée est celle qu'on ouvre")
    }

    @Test fun aForeignHostIsRefusedBeforeAnyLookupOrConnection() {
        val p = Phone(mapOf("example.com" to listOf("93.184.216.34")))
        assertFailsWith<SecurityException> { p.dialer.connect(GwTarget("example.com", 443)) }
        assertFailsWith<SecurityException> { p.dialer.connect(GwTarget("192.168.1.1", 443)) }
        assertFailsWith<SecurityException> { p.dialer.connect(GwTarget("bridge.sti-cm.com", 22)) }
        assertTrue(p.opened.isEmpty() && p.resolved.isEmpty(), "ni DNS ni socket pour un refus")
    }

    @Test fun anAllowedNameThatResolvesToTheLocalNetworkIsRefused() {
        // DNS rebinding : le nom est le bon, la réponse vise la box ou le téléphone lui-même
        for (bad in listOf("192.168.1.1", "10.0.0.2", "127.0.0.1", "169.254.169.254", "fd00::1")) {
            val p = Phone(mapOf("bridge.sti-cm.com" to listOf("79.143.185.145", bad)))
            assertFailsWith<SecurityException>(bad) { p.dialer.connect(GwTarget("bridge.sti-cm.com", 443)) }
            assertTrue(p.opened.isEmpty(), "aucune adresse n'est ouverte si l'une d'elles est locale ($bad)")
        }
    }

    @Test fun nextAddressIsTriedWhenOneFailsAndTheLastErrorComesOut() {
        val tried = ArrayList<String>()
        val d = RelayDialer(resolve = { listOf(ip("79.143.185.145"), ip("79.143.185.146")) }, open = { a, _ -> tried += a.hostAddress; if (tried.size == 1) throw java.io.IOException("refusé") else Socket() })
        d.connect(GwTarget("bridge.sti-cm.com", 443)).close()
        assertEquals(listOf("79.143.185.145", "79.143.185.146"), tried)
        val none = RelayDialer(resolve = { listOf(ip("79.143.185.145")) }, open = { _, _ -> throw java.io.IOException("injoignable") })
        assertFailsWith<java.io.IOException> { none.connect(GwTarget("bridge.sti-cm.com", 443)) }
    }

    @Test fun anUnknownNameIsAnUnknownHostError() {
        val d = RelayDialer(resolve = { throw java.net.UnknownHostException(it) }, open = { _, _ -> Socket() })
        assertFailsWith<java.net.UnknownHostException> { d.connect(GwTarget("bridge.sti-cm.com", 443)) }
    }
}
