package castbridge.core.ux

import kotlin.test.*

/**
 * Bascule automatique sur le Bluetooth (constat du 2026-10-03 : TV allumée hors Wi-Fi, appairée et connectée en Bluetooth, passerelle
 * qui répond, et pourtant l'onglet affichait un point gris « Recherche de la TV… »). Décision pure : quand démarrer la passerelle (API seule,
 * boucle locale), quand l'arrêter, quand utiliser 127.0.0.1:18765 comme adresse de la TV, et quel état afficher (vert/orange/rouge).
 */
class BtFallbackTest {
    private val tv = BondedDevice("Smart TV", "74:24:CA:06:00:46")
    private val other = BondedDevice("AndroidTV", "40:8C:4C:1D:4F:86")

    private fun input(
        enabled: Boolean = true, wifiTv: Boolean = false, absentMs: Long = 60_000, target: BondedDevice? = tv, bondedTvs: Int = 1,
        apiRunning: Boolean = false, gatewayTv: String? = null, autoStarted: Boolean = false, sshRunning: Boolean = false,
        answering: Boolean = false, failure: String? = null, sinceAutoStartMs: Long? = null,
    ) = FallbackInput(enabled, wifiTv, absentMs, target, bondedTvs, apiRunning, gatewayTv, autoStarted, sshRunning, answering, failure, sinceAutoStartMs)

    @Test fun tvAbsentFromWifiWithABluetoothTvStartsTheApiGatewayOnly() {
        val d = BtFallback.decide(input())
        assertEquals(FallbackAction.StartApi(tv), d.action)
        assertEquals(LinkLight.ORANGE, d.signal?.light)
        assertFalse(d.viaBluetooth, "pas encore de liaison : l'adresse de la TV ne change qu'une fois la passerelle active")
    }

    @Test fun onceTheGatewayRunsTheTvIsUsedThroughTheLoopbackAndTheTabSaysSoInOrange() {
        val d = BtFallback.decide(input(apiRunning = true, gatewayTv = tv.address.lowercase(), autoStarted = true, answering = true))
        assertEquals(FallbackAction.None, d.action)
        assertTrue(d.viaBluetooth)
        assertEquals("http://127.0.0.1:18765", BtFallback.LOOPBACK_BASE)
        val s = d.signal!!
        assertEquals(LinkLight.ORANGE, s.light)
        assertEquals("Wi-Fi absent · liaison Bluetooth active", s.title)
        assertEquals("Par Bluetooth, l'envoi de fichiers est lent (~100-300 ko/s).", s.slowNote)
        assertNull(s.gesture)
    }

    @Test fun tvAbsentAndNoBluetoothTvIsRedWithTheCauseAndOneGesture() {
        val d = BtFallback.decide(input(target = null, bondedTvs = 0))
        assertEquals(FallbackAction.None, d.action)
        assertFalse(d.viaBluetooth)
        val s = d.signal!!
        assertEquals(LinkLight.RED, s.light)
        assertEquals("TV injoignable : ni Wi-Fi ni Bluetooth", s.title)
        assertNotNull(s.cause)
        assertEquals(Gesture.ADD_TV_BT, s.gesture)
    }

    @Test fun wifiBackStopsTheGatewayItStartedAndGoesBackToWifi() {
        val d = BtFallback.decide(input(wifiTv = true, apiRunning = true, gatewayTv = tv.address, autoStarted = true))
        assertEquals(FallbackAction.Stop, d.action)
        assertFalse(d.viaBluetooth)
        assertNull(d.signal, "le Wi-Fi a son propre état")
    }

    @Test fun wifiBackNeverStopsAGatewayTheOwnerStartedOrOneCarryingSsh() {
        assertEquals(FallbackAction.None, BtFallback.decide(input(wifiTv = true, apiRunning = true, gatewayTv = tv.address, autoStarted = false)).action)
        assertEquals(FallbackAction.None, BtFallback.decide(input(wifiTv = true, apiRunning = true, gatewayTv = tv.address, autoStarted = true, sshRunning = true)).action)
    }

    @Test fun settingOffMeansNoSwitchAtAll() {
        val d = BtFallback.decide(input(enabled = false))
        assertEquals(FallbackAction.None, d.action)
        assertFalse(d.viaBluetooth)
        assertEquals(LinkLight.RED, d.signal?.light)
        assertEquals(Gesture.START_GATEWAY, d.signal?.gesture)
        // even with a gateway running (started by hand), the setting off keeps the home on the Wi-Fi
        assertFalse(BtFallback.decide(input(enabled = false, apiRunning = true, gatewayTv = tv.address, answering = true)).viaBluetooth)
        // and with the Wi-Fi back nothing is stopped by the switch rule either
        assertEquals(FallbackAction.None, BtFallback.decide(input(enabled = false, wifiTv = true, apiRunning = true, gatewayTv = tv.address, autoStarted = true)).action)
    }

    @Test fun twoTvsOnlyOneBondedPicksTheBondedOne() {
        // the preferred TV comes from BtGatewayView: one bonded TV among the phone's devices is the target
        val target = BtGatewayView.preferred(listOf(BondedDevice("Papa’s AirPods", "AC:C9:06:3D:86:E6"), tv), emptySet())
        assertEquals(tv, target)
        val d = BtFallback.decide(input(target = target))
        assertEquals(FallbackAction.StartApi(tv), d.action)
        // a gateway running to ANOTHER TV is never reused for this one (its code would go to the wrong TV)
        val wrong = BtFallback.decide(input(apiRunning = true, gatewayTv = other.address, answering = true))
        assertFalse(wrong.viaBluetooth)
        assertEquals(FallbackAction.None, wrong.action)
        assertEquals(Gesture.STOP_OTHER, wrong.signal?.gesture)
    }

    @Test fun twoBondedTvsAndNoneKnownAsksToChoose() {
        val d = BtFallback.decide(input(target = null, bondedTvs = 2))
        assertEquals(FallbackAction.None, d.action)
        assertEquals(LinkLight.RED, d.signal?.light)
        assertEquals(Gesture.START_GATEWAY, d.signal?.gesture)
    }

    @Test fun theFirstSecondsAreASearchNotAFailure() {
        val d = BtFallback.decide(input(absentMs = 2_000))
        assertEquals(FallbackAction.None, d.action)
        assertNull(d.signal)
    }

    @Test fun aFailedStartIsNotRetriedInALoopAndSaysWhy() {
        val d = BtFallback.decide(input(sinceAutoStartMs = 5_000, failure = "Bluetooth : la TV ne répond pas"))
        assertEquals(FallbackAction.None, d.action)
        assertEquals(LinkLight.RED, d.signal?.light)
        assertEquals("TV injoignable : ni Wi-Fi ni Bluetooth", d.signal?.title)
        assertEquals("Bluetooth : la TV ne répond pas", d.signal?.cause)
        assertEquals(Gesture.RETRY, d.signal?.gesture)
        // after the pause, one new attempt
        assertEquals(FallbackAction.StartApi(tv), BtFallback.decide(input(sinceAutoStartMs = BtFallback.RETRY_MS)).action)
    }

    @Test fun aRunningGatewayWhoseTvDoesNotAnswerIsRedNotOrange() {
        val d = BtFallback.decide(input(apiRunning = true, gatewayTv = tv.address, autoStarted = true, answering = false, failure = "Service introuvable"))
        assertEquals(LinkLight.RED, d.signal?.light)
        assertEquals(Gesture.RETRY, d.signal?.gesture)
        assertTrue(d.viaBluetooth, "on continue d'essayer par la passerelle")
    }

    @Test fun theCodeIsNeverShownInTheStateLine() {
        val d = BtFallback.decide(input(sinceAutoStartMs = 1_000, failure = "HTTP 401 X-CB-Pin: 482913 refusé"))
        val all = listOfNotNull(d.signal?.title, d.signal?.cause, d.signal?.slowNote).joinToString(" ")
        assertFalse("482913" in all, all)
        assertTrue("<code>" in all)
        assertFalse("482913" in d.toString())
    }
}
