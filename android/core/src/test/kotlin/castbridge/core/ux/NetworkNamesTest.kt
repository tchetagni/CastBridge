package castbridge.core.ux

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import castbridge.core.tv.TvInfo

/** Le nom du réseau sur les accueils : une ligne existante enrichie, rien quand ce n'est pas sûr. */
class NetworkNamesTest {
    private val wifi = TvFacts(lan = LanKind.WIFI, wifiName = "Orange-0415_2G")

    @Test fun `the TV chip gains the Wi-Fi name`() {
        val v = TvSignal.of(wifi)
        assertEquals("Wi-Fi Orange-0415_2G", v.network)
        assertEquals("Prêt à recevoir   ·   Wi-Fi Orange-0415_2G   ·   code 12••••", NetworkNames.chip(v.text, v.network, "12••••"))
    }

    @Test fun `ethernet says cable, no network says nothing`() {
        assertEquals("Câble réseau", TvSignal.of(TvFacts(lan = LanKind.ETHERNET)).network)
        assertNull(TvSignal.of(TvFacts(lan = LanKind.NONE, bluetooth = BtState.OFF)).network)
        assertNull(TvSignal.of(wifi.copy(wifiNoAddress = true)).network)
        assertNull(TvSignal.of(wifi.copy(wifiName = null)).network)
        assertEquals("Prêt à recevoir   ·   code 12", NetworkNames.chip("Prêt à recevoir", null, "12"))
    }

    @Test fun `a long name is cut cleanly and a head that has it is not repeated`() {
        assertEquals("Wi-Fi abcdefghijklmnopqrstuvwx…", NetworkNames.tvPart(wifi.copy(wifiName = "abcdefghijklmnopqrstuvwxyz")))
        assertEquals("abcdefghijklmnopqrstuvwx…", NetworkNames.short("abcdefghijklmnopqrstuvwxyz"))
        assertEquals("Maison", NetworkNames.short("  Maison "))
        assertNull(NetworkNames.short("   "))
        assertNull(NetworkNames.tvPart(wifi, "Wi-Fi Orange-0415_2G : signal faible"))
    }

    @Test fun `the phone says the same network only when the subnet agrees`() {
        assertEquals(true, NetworkNames.sameSubnet("192.168.1.23", 24, "192.168.1.40"))
        assertEquals(false, NetworkNames.sameSubnet("192.168.43.23", 24, "192.168.1.40"))
        assertEquals(true, NetworkNames.sameSubnet("10.0.3.5", 16, "10.0.200.9"))
        assertNull(NetworkNames.sameSubnet(null, 24, "192.168.1.40"))
        assertNull(NetworkNames.sameSubnet("192.168.1.23", 24, "127.0.0.1"))      // Bluetooth relay loopback: not a LAN address
        assertNull(NetworkNames.sameSubnet("192.168.1.23", 0, "192.168.1.40"))
        assertNull(NetworkNames.sameSubnet("192.168.1.23", 24, "tv.local"))
    }

    private val A = NetworkNames.LocationAccess.GRANTED

    @Test fun `without permission the IP verdict stays and a discreet link asks`() {
        val same = NetworkNames.phoneLine("Orange-0415_2G", true, NetworkNames.LocationAccess.CAN_ASK)!!
        assertEquals("Wi-Fi : Orange-0415_2G (le même que la TV)", same.text); assertEquals(false, same.warn); assertEquals(false, same.changeWifi)
        assertEquals(NetworkNames.LINK_SEE, same.linkText); assertEquals(NetworkNames.NetAction.ASK_PERMISSION, same.action)
        val other = NetworkNames.phoneLine("Orange-0415_2G", false, NetworkNames.LocationAccess.CAN_ASK)!!
        assertEquals("Ce téléphone n'est pas sur le Wi-Fi de la TV (Orange-0415_2G)", other.text); assertEquals(true, other.warn); assertEquals(true, other.changeWifi)
        // unknown: only the link
        assertEquals(NetworkNames.Line(null, false, false, NetworkNames.LINK_SEE, NetworkNames.NetAction.ASK_PERMISSION), NetworkNames.phoneLine(null, null, NetworkNames.LocationAccess.CAN_ASK))
    }

    @Test fun `a definitive refusal sends to the app settings`() {
        val l = NetworkNames.phoneLine("Orange-0415_2G", null, NetworkNames.LocationAccess.BLOCKED)!!
        assertNull(l.text); assertEquals(NetworkNames.NetAction.OPEN_APP_SETTINGS, l.action)
        assertEquals(NetworkNames.NetAction.OPEN_APP_SETTINGS, NetworkNames.phoneLine("X", false, NetworkNames.LocationAccess.BLOCKED)!!.action)
    }

    @Test fun `granted but location off asks to switch it on`() {
        val l = NetworkNames.phoneLine("Orange-0415_2G", null, A, phoneSsid = null, onWifi = true)!!
        assertEquals(NetworkNames.LINK_LOCATION_OFF, l.linkText); assertEquals(NetworkNames.NetAction.OPEN_LOCATION_SETTINGS, l.action)
        assertNull(NetworkNames.phoneLine("Orange-0415_2G", null, A, null, onWifi = false))       // not on Wi-Fi at all: nothing to say
        // the IP verdict is kept next to the invitation
        assertEquals("Wi-Fi : Orange-0415_2G (le même que la TV)", NetworkNames.phoneLine("Orange-0415_2G", true, A, null, true)!!.text)
    }

    @Test fun `with the permission the names decide`() {
        val same = NetworkNames.phoneLine("Orange-0415_2G", null, A, "Orange-0415_2G")!!
        assertEquals("Wi-Fi : Orange-0415_2G · même réseau que la TV", same.text); assertEquals(false, same.warn); assertNull(same.action)
        val diff = NetworkNames.phoneLine("Orange-0415_2G", null, A, "Mallorie network")!!
        assertEquals("Ce téléphone est sur Mallorie network, la TV sur Orange-0415_2G", diff.text); assertEquals(true, diff.warn); assertEquals(true, diff.changeWifi)
        // names win over a same-prefix guest network
        assertEquals(true, NetworkNames.phoneLine("Orange-0415_2G", true, A, "Mallorie network")!!.warn)
        // TV silent: the phone's own name only
        assertEquals("Wi-Fi : Mallorie network", NetworkNames.phoneLine(null, null, A, "Mallorie network")!!.text)
    }

    @Test fun `unknown stays silent and ssid is cleaned`() {
        assertNull(NetworkNames.phoneLine(null, null, A, null, onWifi = false))
        assertNull(NetworkNames.phoneLine(null, true, A, null, onWifi = false))
        assertEquals("Maison", NetworkNames.cleanSsid("\"Maison\""))
        assertNull(NetworkNames.cleanSsid("<unknown ssid>")); assertNull(NetworkNames.cleanSsid(null)); assertNull(NetworkNames.cleanSsid(" "))
    }

    @Test fun `the wifi field of the info is optional`() {
        val base = """{"files":[],"free":5,"used":0,"quota":0,"target":"auto","volumes":[],"""
        val tail = """"player":{"state":"idle","name":null,"pos":0,"dur":0},"playlist":[]}"""
        assertEquals("Orange-0415_2G", TvInfo.parse(base + """"wifi":"Orange-0415_2G",""" + tail).wifi)
        assertNull(TvInfo.parse(base + tail).wifi)
    }
}
