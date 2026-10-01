package castbridge.core.remote

import kotlin.test.Test
import kotlin.test.assertEquals

class RouteNameTest {
    @Test fun theLinkIsNamedAfterTheRouteActuallyUsed() {
        assertEquals("Bluetooth", routeName("127.0.0.1"))          // the phone's Bluetooth gateway: the TV's API on the phone's own loopback
        assertEquals("Bluetooth", routeName("localhost"))
        assertEquals("Wi-Fi Direct", routeName("192.168.49.1"))
        assertEquals("Wi-Fi", routeName("192.168.0.121"))
        assertEquals("Wi-Fi", routeName("tv-salon.local"))
        assertEquals("Wi-Fi", routeName("10.0.0.5"))
    }

    @Test fun theTransportReportsThatName() {
        assertEquals("Bluetooth", HttpRemoteTransport("127.0.0.1", 18765, "000000").name)
        assertEquals("Wi-Fi", HttpRemoteTransport("192.168.0.121", 8765, "000000").name)
    }
}
