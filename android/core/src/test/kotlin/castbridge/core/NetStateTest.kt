package castbridge.core

import castbridge.core.net.LinkKind
import castbridge.core.net.NetState
import castbridge.core.net.NetStateTracker
import castbridge.core.net.netJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NetStateTest {
    private var t = 0L
    private fun NetStateTracker.round(link: LinkKind, direct: Long?, gwConnected: Boolean = false, gw: Long? = null, dt: Long = 60_000): NetState {
        t += dt; return update(link, direct, gwConnected, gw, t)
    }

    @Test fun startsChecking() { assertEquals(NetState.CHECKING, NetStateTracker().state) }

    @Test fun wifiAndEthernetUpAfterOneSuccess() {
        assertEquals(NetState.INTERNET_WIFI, NetStateTracker().round(LinkKind.WIFI, 40))
        assertEquals(NetState.INTERNET_ETHERNET, NetStateTracker().round(LinkKind.ETHERNET, 5))
        assertEquals(NetState.INTERNET_WIFI, NetStateTracker().round(LinkKind.OTHER, 5))
    }

    @Test fun noneNeedsTwoFailedRoundsFromChecking() {
        val n = NetStateTracker()
        assertEquals(NetState.CHECKING, n.round(LinkKind.WIFI, null, dt = 1000))
        assertEquals(NetState.NONE, n.round(LinkKind.WIFI, null, dt = 10_000))
    }

    @Test fun linkUpButProbeFailsIsNeverConnected() {
        val n = NetStateTracker()
        repeat(5) { assertFalse(n.round(LinkKind.WIFI, null).working) }
        assertEquals(NetState.NONE, n.state)
    }

    @Test fun directGoesDownOnlyAfterTwoFailures() {
        val n = NetStateTracker()
        n.round(LinkKind.WIFI, 30)
        assertEquals(NetState.INTERNET_WIFI, n.round(LinkKind.WIFI, null))
        assertEquals(NetState.NONE, n.round(LinkKind.WIFI, null))
    }

    @Test fun oneSuccessBringsItBackAtOnce() {
        val n = NetStateTracker()
        n.round(LinkKind.WIFI, 30); n.round(LinkKind.WIFI, null); n.round(LinkKind.WIFI, null)
        assertEquals(NetState.NONE, n.state)
        assertEquals(NetState.INTERNET_WIFI, n.round(LinkKind.WIFI, 90, dt = 1000))
    }

    @Test fun flappingWifiDoesNotBlink() {
        val n = NetStateTracker()
        n.round(LinkKind.WIFI, 30)
        val seen = mutableSetOf<NetState>()
        repeat(10) { i -> seen += n.round(LinkKind.WIFI, if (i % 2 == 0) null else 50, dt = 10_000) }
        assertEquals(setOf(NetState.INTERNET_WIFI), seen)
    }

    @Test fun gatewayOnly() {
        val n = NetStateTracker()
        assertEquals(NetState.INTERNET_VIA_PHONE, n.round(LinkKind.NONE, null, true, 200))
        assertTrue(n.gatewayAlsoAvailable)
    }

    @Test fun gatewayConnectedButProbeFailsIsNone() {
        val n = NetStateTracker()
        repeat(3) { n.round(LinkKind.NONE, null, true, null) }
        assertEquals(NetState.NONE, n.state)
        assertFalse(n.gatewayAlsoAvailable)
    }

    @Test fun directPreferredWhenBothWork_gatewayStillReported() {
        val n = NetStateTracker()
        assertEquals(NetState.INTERNET_WIFI, n.round(LinkKind.WIFI, 40, true, 300))
        assertTrue(n.gatewayAlsoAvailable)
    }

    @Test fun fallsBackToPhoneWhenDirectDiesAfterDebounceAndMinTime() {
        val n = NetStateTracker()
        n.round(LinkKind.WIFI, 40, true, 300)
        n.round(LinkKind.WIFI, null, true, 300)
        assertEquals(NetState.INTERNET_VIA_PHONE, n.round(LinkKind.WIFI, null, true, 300))
    }

    @Test fun minimumDisplayTimeHoldsAFastChange() {
        val n = NetStateTracker(minShowMs = 8_000)
        n.round(LinkKind.WIFI, 40, true, 300, dt = 0)
        n.round(LinkKind.WIFI, null, true, 300, dt = 1000)
        assertEquals(NetState.INTERNET_WIFI, n.round(LinkKind.WIFI, null, true, 300, dt = 1000))   // 2nd failure, but 2 s after the change
        assertEquals(NetState.INTERNET_VIA_PHONE, n.round(LinkKind.WIFI, null, true, 300, dt = 10_000))
    }

    @Test fun gatewayDisconnectDropsThePhonePath() {
        val n = NetStateTracker()
        n.round(LinkKind.NONE, null, true, 200)
        n.round(LinkKind.NONE, null, false, null)
        assertEquals(NetState.NONE, n.state)
        assertFalse(n.gatewayAlsoAvailable)
    }

    @Test fun pollingIsFastOnlyWhileNotWorking() {
        val n = NetStateTracker()
        assertEquals(10_000, n.nextDelayMs())
        n.round(LinkKind.WIFI, null); n.round(LinkKind.WIFI, null)
        repeat(10) { n.round(LinkKind.WIFI, null) }
        assertEquals(30_000, n.nextDelayMs())
        n.round(LinkKind.WIFI, 20)
        assertEquals(60_000, n.nextDelayMs())
    }

    @Test fun jsonIsAdditiveAndHasNoPersonalData() {
        val n = NetStateTracker(); n.round(LinkKind.ETHERNET, 12, true, 300)
        val j = netJson(n, LinkKind.ETHERNET, 12, true, 300, 5L)
        assertTrue(""""state":"ethernet"""" in j && """"alsoAvailable":true""" in j && """"link":"ethernet"""" in j)
    }
}
