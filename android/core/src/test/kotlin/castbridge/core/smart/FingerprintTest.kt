package castbridge.core.smart

import castbridge.core.remote.smart.*
import kotlin.test.*

class FingerprintTest {
    private fun id(h: TvHints) = TvIdentifier.identify(h)

    @Test fun referenceCvteTvIsRecognisedFromMdnsAndPorts() {
        val fp = id(TvHints(
            host = "192.168.1.20",
            mdns = listOf(MdnsRecord("_share._tcp", "BytelloRemoteServer", mapOf("websocket_port" to "8125", "http_port" to "9909", "device_name" to "SMART_TV"), 8125),
                MdnsRecord("_maxhubmobile._tcp", "TV-1234")),
            openPorts = setOf(8125, 9909, 8765, 5555),
        ))
        assertEquals(Vendor.CVTE, fp.vendor)
        assertTrue(fp.confidence > 0.9, "confiance ${fp.confidence}")
        assertEquals(StrategyIds.CVTE, fp.candidates.first { it != StrategyIds.CASTBRIDGE })
        assertEquals("SMART_TV", fp.model)
        assertTrue(fp.evidence.any { "_share._tcp" in it })
    }

    @Test fun castBridgeTvIsAlwaysFirst() {
        val fp = id(TvHints(mdns = listOf(MdnsRecord("_castbridge._tcp.", "CastBridge TV Salon", mapOf("role" to "receiver"), 8765)),
            openPorts = setOf(8765, 8125), upnp = UpnpInfo(manufacturer = "Roku")))
        assertEquals(StrategyIds.CASTBRIDGE, fp.candidates.first())
        assertEquals(Vendor.CASTBRIDGE, fp.vendor)
    }

    @Test fun samsungFromSsdpAndPorts() {
        val up = Upnp.parseDescription(SAMSUNG_XML, "http://192.168.1.30:9197/dmr")
        val fp = id(TvHints(upnp = up, openPorts = setOf(8001, 8002, 9197), mac = "00:12:47:AA:BB:CC"))
        assertEquals(Vendor.SAMSUNG, fp.vendor); assertEquals("Tizen", fp.family); assertEquals("UE55TU8000", fp.model)
        assertEquals(StrategyIds.SAMSUNG, fp.candidates.first())
        assertTrue(StrategyIds.DLNA in fp.candidates, "le moteur UPnP reste un secours")
        assertTrue(fp.candidates.indexOf(StrategyIds.SAMSUNG) < fp.candidates.indexOf(StrategyIds.DLNA))
    }

    @Test fun lgFromServerHeaderAndBluetoothName() {
        val fp = id(TvHints(upnp = UpnpInfo(server = "Linux/4.4 UPnP/1.0 webOS/5.0"), bluetoothName = "[LG] webOS TV OLED55C1", openPorts = setOf(3000, 3001)))
        assertEquals(Vendor.LG, fp.vendor); assertEquals("webOS", fp.family); assertEquals(StrategyIds.LG, fp.candidates.first())
    }

    @Test fun rokuFromEcpPortAndServer() {
        val fp = id(TvHints(http = listOf(HttpSignature(8060, server = "Roku/12.5.0 UPnP/1.0 Roku/12.5.0")), openPorts = setOf(8060)))
        assertEquals(Vendor.ROKU, fp.vendor); assertTrue(fp.confidence > 0.9)
    }

    @Test fun sonyBraviaViaIrccServiceButAndroidTvStaysACandidate() {
        val up = UpnpInfo(manufacturer = "Sony Corporation", modelName = "BRAVIA 4K", serviceTypes = listOf("urn:schemas-sony-com:service:IRCC:1", "urn:schemas-upnp-org:service:AVTransport:1", "urn:schemas-upnp-org:service:RenderingControl:1"))
        val fp = id(TvHints(upnp = up, mdns = listOf(MdnsRecord("_androidtvremote2._tcp", "BRAVIA")), openPorts = setOf(80, 6466, 6467)))
        assertEquals(Vendor.SONY, fp.vendor, "le fabricant l'emporte sur la plateforme")
        assertEquals(StrategyIds.SONY, fp.candidates.first())
        assertTrue(StrategyIds.ANDROID_TV in fp.candidates)
    }

    @Test fun philipsAndVizioAndAndroidTv() {
        assertEquals(Vendor.PHILIPS, id(TvHints(upnp = UpnpInfo(manufacturer = "TP Vision"), openPorts = setOf(1925, 1926))).vendor)
        assertEquals(Vendor.VIZIO, id(TvHints(upnp = UpnpInfo(manufacturer = "VIZIO Inc."), openPorts = setOf(7345))).vendor)
        val a = id(TvHints(mdns = listOf(MdnsRecord("_androidtvremote2._tcp.", "Salon")), openPorts = setOf(6466, 6467)))
        assertEquals(Vendor.ANDROID_TV, a.vendor); assertEquals("Android TV", a.family); assertEquals(StrategyIds.ANDROID_TV, a.candidates.first())
    }

    @Test fun weakEvidenceStaysUnknownAndNeverInventsAStrategy() {
        val none = id(TvHints(host = "192.168.1.9"))
        assertEquals(Vendor.UNKNOWN, none.vendor); assertEquals(0.0, none.confidence); assertTrue(none.candidates.isEmpty())
        // an OUI alone is weak: below the identification threshold, but it still ranks the right strategy
        val oui = id(TvHints(mac = "b0:a7:37:11:22:33"))
        assertEquals(Vendor.UNKNOWN, oui.vendor); assertTrue(oui.confidence < 0.3)
        assertTrue(oui.evidence.any { "OUI" in it })
        assertNull(Oui.vendorOf("de:ad:be:ef:00:01")); assertNull(Oui.vendorOf(null))
        assertEquals(Vendor.ROKU, Oui.vendorOf("B0-A7-37-00-00-01"))
    }

    @Test fun identificationIsDeterministic() {
        val h = TvHints(upnp = UpnpInfo(manufacturer = "Samsung"), openPorts = setOf(8001))
        assertEquals(id(h), id(h))
    }

    @Test fun descriptionXmlParserReadsDeviceAndControlUrls() {
        val up = Upnp.parseDescription(DLNA_XML, "http://10.0.0.5:49152/description.xml", server = "Linux UPnP/1.0")
        assertEquals("ACME", up.manufacturer); assertEquals("Renderer 3", up.friendlyName); assertEquals("Linux UPnP/1.0", up.server)
        assertEquals("http://10.0.0.5:49152/AVT/control", up.controlUrls["urn:schemas-upnp-org:service:AVTransport:1"])
        assertEquals("http://10.0.0.5:49152/RC/control", up.controlUrls["urn:schemas-upnp-org:service:RenderingControl:1"])
        // a hostile description must not be interpreted (no entity expansion, nothing thrown)
        val evil = Upnp.parseDescription("<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY a SYSTEM \"file:///etc/passwd\">]><root><device><manufacturer>&a;</manufacturer></device></root>")
        assertEquals("&a;", evil.manufacturer)
    }

    @Test fun ssdpHeadersParse() {
        val h = Upnp.parseSsdpHeaders("HTTP/1.1 200 OK\r\nSERVER: Roku/9 UPnP/1.0\r\nLocation: http://1.2.3.4:8060/\r\nST: upnp:rootdevice\r\n\r\n")
        assertEquals("Roku/9 UPnP/1.0", h["server"]); assertEquals("http://1.2.3.4:8060/", h["location"])
    }

    companion object {
        const val SAMSUNG_XML = """<?xml version="1.0"?><root xmlns="urn:schemas-upnp-org:device-1-0"><device><deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
<friendlyName>[TV] Samsung 8 Series (55)</friendlyName><manufacturer>Samsung Electronics</manufacturer><modelName>UE55TU8000</modelName>
<serviceList><service><serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType><controlURL>/upnp/control/AVTransport1</controlURL></service>
<service><serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType><controlURL>/upnp/control/RenderingControl1</controlURL></service></serviceList></device></root>"""
        const val DLNA_XML = """<?xml version="1.0"?><root xmlns="urn:schemas-upnp-org:device-1-0"><device><friendlyName>Renderer 3</friendlyName><manufacturer>ACME</manufacturer><modelName>R3</modelName>
<serviceList><service><serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType><controlURL>/AVT/control</controlURL></service>
<service><serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType><controlURL>/RC/control</controlURL></service></serviceList></device></root>"""
    }
}
