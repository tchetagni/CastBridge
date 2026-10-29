package castbridge.core.ux

import castbridge.core.trust.PinCheck
import castbridge.core.trust.SendChoices
import castbridge.core.trust.SendFacts
import castbridge.core.trust.SendRoute
import castbridge.core.ux.OpenWithCause.Fix
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Les causes d'un échec d'« Ouvrir avec », écrites dans la fenêtre avec une seule action. */
class OpenWithCauseTest {
    private val tv = OpenWithCause.Facts("SMART_TV", tvHost = "192.168.0.121", tvWifi = "Orange-0415_2G", phoneIp = "192.168.0.108", phonePrefix = 24)

    @Test fun `same network and the TV silent says to switch it on`() {
        val c = OpenWithCause.unreachable(tv)
        assertEquals("SMART_TV ne répond pas à 192.168.0.121 : allumez la TV et ouvrez CastBridge-TV.", c.text); assertEquals(Fix.RETRY, c.fix)
        assertEquals("SMART_TV est introuvable sur le réseau : allumez la TV et ouvrez CastBridge-TV.", OpenWithCause.unreachable(tv.copy(tvHost = null)).text)
    }

    @Test fun `another network is proven by the names, else by the subnet`() {
        val byName = OpenWithCause.unreachable(tv.copy(phoneIp = "192.168.43.5", phoneSsid = "Mallorie network"))
        assertEquals("Ce téléphone est sur Mallorie network, la TV sur Orange-0415_2G", byName.text); assertEquals(Fix.CHANGE_WIFI, byName.fix)
        val byIp = OpenWithCause.unreachable(tv.copy(phoneIp = "192.168.1.23"))
        assertEquals("Ce téléphone n'est pas sur le réseau de la TV (téléphone en 192.168.1.x, TV en 192.168.0.x)", byIp.text); assertEquals(Fix.CHANGE_WIFI, byIp.fix)
        // the same names win over a different-looking address: no false accusation
        assertEquals(Fix.RETRY, OpenWithCause.unreachable(tv.copy(phoneIp = "10.0.0.5", phoneSsid = "Orange-0415_2G")).fix)
    }

    @Test fun `no Wi-Fi on the phone`() {
        val c = OpenWithCause.unreachable(tv.copy(phoneIp = null))
        assertEquals("Ce téléphone n'est pas connecté au Wi-Fi : connectez-le à Orange-0415_2G pour joindre SMART_TV.", c.text); assertEquals(Fix.CHANGE_WIFI, c.fix)
    }

    @Test fun `the TV's own words are kept`() {
        val c = OpenWithCause.startFailure(tv, "Plus de place sur la TV"); assertEquals("Impossible de démarrer : Plus de place sur la TV", c.text)
        assertEquals(OpenWithCause.unreachable(tv), OpenWithCause.startFailure(tv, null))
    }

    @Test fun `a stuck link never stays at verification`() {
        val base = SendFacts(savedCount = 1, defaultName = "SMART_TV", stepView = castbridge.core.trust.LinkStart.checking("SMART_TV"), pinTvName = "CastBridge TV SMART_TV", pinStored = true, pinCheck = PinCheck.UNKNOWN)
        assertEquals(SendRoute.QUEUE, SendChoices.decide(base.copy(linkPendingMs = 3000)).route)
        val stuck = SendChoices.decide(base.copy(linkPendingMs = 20_000, pinCheck = PinCheck.UNREACHABLE, cause = "SMART_TV ne répond pas à 192.168.0.121 : allumez la TV et ouvrez CastBridge-TV."))
        assertEquals(SendRoute.PIN_UPLOAD, stuck.route); assertTrue(stuck.status.startsWith("SMART_TV ne répond pas à 192.168.0.121"))
        // the code path verified by address: the copy leaves
        assertEquals("TV : SMART_TV", SendChoices.decide(base.copy(linkPendingMs = 20_000, pinCheck = PinCheck.OK)).status)
    }
}
