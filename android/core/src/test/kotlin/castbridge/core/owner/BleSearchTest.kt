package castbridge.core.owner

import castbridge.core.btact.BtActAd
import castbridge.core.btact.BtActClient
import castbridge.core.btact.BtActWire
import castbridge.core.owner.ActivationRoutePlan.Cause
import castbridge.core.owner.BleSearch.Attempt
import kotlin.test.*

/**
 * Les règles de la voie « Bluetooth sans appairage » côté téléphone (act-bt) : quelles annonces BLE sont essayées (le tag du code tapé, jamais une TV de voisin), dans quel ordre, quand le balayage
 * s'arrête, et ce que dit chaque réponse de la TV (les mots de la route HTTP). Pur : le balayage et les connexions Android ne font que relayer.
 */
class BleSearchTest {
    private val code = "482913"
    private fun seen(id: String, rssi: Int, c: String? = code, psm: Int = 0x81, payload: ByteArray? = c?.let { BtActAd.payload(it, psm) }) = BleSearch.Sighting(id, rssi, payload)

    // ------------------------------------------------------------------ qui est essayé

    @Test fun onlyAnAdvertisementWithTheTagOfTheTypedCodeIsATv() {
        val c = BleSearch.Collector(code)
        assertTrue(c.add(seen("tv", -60), 0))
        assertFalse(c.add(seen("neighbour", -40, c = "111111"), 0), "la TV d'un voisin (un autre code, un autre tag) n'est pas approchée : aucun code faux compté chez elle")
        assertFalse(c.add(seen("typo", -40, c = "482914"), 0), "un code mal tapé ne désigne personne")
        assertFalse(c.add(seen("noRecord", -30, payload = null), 0), "une annonce sans notre enregistrement (une montre, des écouteurs)")
        assertFalse(c.add(seen("garbage", -30, payload = ByteArray(5)), 0))
        assertFalse(c.add(seen("v2", -30, payload = BtActAd.payload(code, 3).also { it[0] = 2 }), 0), "une autre version de l'annonce n'est pas devinée")
        assertEquals(listOf("tv"), c.candidates().map { it.id })
    }

    @Test fun theStrongestSignalComesFirstOneEntryPerDeviceAtMostThree() {
        val c = BleSearch.Collector(code)
        c.add(seen("a", -80), 0); c.add(seen("b", -50), 0); c.add(seen("c", -65), 0); c.add(seen("d", -30), 0)
        assertEquals(listOf("d", "b", "c"), c.candidates().map { it.id }, "du plus fort au plus faible, trois au plus")
        c.add(seen("a", -20), 10)
        assertEquals(listOf("a", "d", "b"), c.candidates().map { it.id }, "une annonce plus forte du même appareil remplace l'ancienne")
        c.add(seen("a", -90), 20)
        assertEquals(-20, c.candidates().first().rssi, "une plus faible ne remplace rien")
        assertEquals(3, BleSearch.MAX_CANDIDATES)
    }

    @Test fun thePsmComesFromTheAdvertisementAndZeroMeansNoL2capChannel() {
        val c = BleSearch.Collector(code)
        c.add(seen("with", -50, psm = 0x93), 0); c.add(seen("without", -60, psm = 0), 0)
        assertEquals(mapOf("with" to 0x93, "without" to 0), c.candidates().associate { it.id to it.psm })
    }

    // ------------------------------------------------------------------ par quel canal

    @Test fun theChannelsAreTriedL2capFirstThenRfcommAndRfcommAloneWithoutAPsmOrBeforeAndroid10() {
        for (api in listOf(29, 30, 33, 34, 35)) assertEquals(listOf(BleSearch.Channel.L2CAP, BleSearch.Channel.RFCOMM), BleSearch.channels(api, 0x81), "api $api")
        for (api in listOf(26, 27, 28)) assertEquals(listOf(BleSearch.Channel.RFCOMM), BleSearch.channels(api, 0x81), "api $api : pas de L2CAP avant Android 10")
        for (api in listOf(28, 29, 34)) assertEquals(listOf(BleSearch.Channel.RFCOMM), BleSearch.channels(api, 0), "api $api : l'annonce ne donne pas de PSM")
        assertEquals(29, BleSearch.L2CAP_MIN_API)
    }

    // ------------------------------------------------------------------ quand le balayage s'arrête

    @Test fun theScanStopsAtTenSecondsOrEightHundredMillisecondsAfterTheFirstMatch() {
        val c = BleSearch.Collector(code)
        assertFalse(c.scanDone(1_000, 1_000 + BleSearch.SCAN_MS - 1))
        assertTrue(c.scanDone(1_000, 1_000 + BleSearch.SCAN_MS), "10 s sans rien : on arrête")
        c.add(seen("neighbour", -40, c = "111111"), 2_000)
        assertFalse(c.scanDone(1_000, 2_900), "une TV de voisin n'arrête pas le balayage")
        c.add(seen("tv", -60), 3_000)
        assertEquals(3_000L, c.firstAtMs)
        assertFalse(c.scanDone(1_000, 3_000 + BleSearch.SETTLE_MS - 1))
        assertTrue(c.scanDone(1_000, 3_000 + BleSearch.SETTLE_MS), "0,8 s après la première TV : une autre du même tag aurait été entendue")
        c.add(seen("tv2", -70), 3_500)
        assertEquals(3_000L, c.firstAtMs, "l'instant de la première ne bouge pas")
        assertEquals(10_000L, BleSearch.SCAN_MS); assertEquals(800L, BleSearch.SETTLE_MS); assertEquals(12_000L, BleSearch.ATTEMPT_MS)
    }

    // ------------------------------------------------------------------ ce que dit chaque réponse

    @Test fun whatATvSaidBecomesTheCauseOfTheRouteInTheHttpRoutesWords() {
        assertEquals(Cause(Cause.Kind.CODE_REFUSED), BleSearch.causeOf(Attempt.Refused(BtActWire.Err.BAD_CODE)))
        assertEquals(Cause(Cause.Kind.LOCKED_OUT, "60"), BleSearch.causeOf(Attempt.Refused(BtActWire.Err.LOCKED, 60)))
        assertEquals(Cause(Cause.Kind.CLOSED), BleSearch.causeOf(Attempt.Refused(BtActWire.Err.CLOSED)))
        assertEquals(Cause(Cause.Kind.TERMS), BleSearch.causeOf(Attempt.Refused(BtActWire.Err.TERMS)))
        assertEquals(Cause(Cause.Kind.NEEDS_UPDATE), BleSearch.causeOf(Attempt.Refused(BtActWire.Err.VERSION)))
        assertEquals(Cause(Cause.Kind.BLE_CONNECT), BleSearch.causeOf(Attempt.Refused(BtActWire.Err.UNAVAILABLE)))
        assertEquals(Cause(Cause.Kind.BLE_CONNECT), BleSearch.causeOf(Attempt.Refused(BtActWire.Err.BAD_MESSAGE)))
        assertEquals(Cause(Cause.Kind.BLE_CONNECT), BleSearch.causeOf(Attempt.Lost()))
        assertEquals(Cause(Cause.Kind.BLE_CONNECT), BleSearch.causeOf(Attempt.NoConnection()))
        // which channel connected or was tried goes on the screen, for the person who tests on a real box
        assertEquals(Cause(Cause.Kind.BLE_CONNECT, "canal : L2CAP"), BleSearch.causeOf(Attempt.Lost(BleSearch.Channel.L2CAP)))
        assertEquals(Cause(Cause.Kind.BLE_CONNECT, "canal : RFCOMM"), BleSearch.causeOf(Attempt.Lost(BleSearch.Channel.RFCOMM)))
        assertEquals(Cause(Cause.Kind.BLE_CONNECT, "essayé : L2CAP, RFCOMM"), BleSearch.causeOf(Attempt.NoConnection(listOf(BleSearch.Channel.L2CAP, BleSearch.Channel.RFCOMM))))
        assertEquals(Cause(Cause.Kind.BLE_CONNECT, "essayé : RFCOMM"), BleSearch.causeOf(Attempt.NoConnection(listOf(BleSearch.Channel.RFCOMM))))
    }

    @Test fun everyOutcomeOfTheHandshakeIsAnAttemptOrReady() {
        val refused = assertIs<Attempt.Refused>(BleSearch.attemptOf(BtActClient.Connect.Refused(BtActWire.Err.LOCKED, 42)))
        assertEquals(BtActWire.Err.LOCKED, refused.err); assertEquals(42L, refused.seconds)
        assertIs<Attempt.Lost>(BleSearch.attemptOf(BtActClient.Connect.NotProved), "une TV qui ne prouve pas le code n'est pas une TV de confiance")
        assertEquals(BleSearch.Channel.L2CAP, (BleSearch.attemptOf(BtActClient.Connect.LinkLost, BleSearch.Channel.L2CAP) as Attempt.Lost).channel, "le canal sur lequel le lien a cassé")
        assertNull((BleSearch.attemptOf(BtActClient.Connect.LinkLost) as Attempt.Lost).channel)
    }

    @Test fun theVerdictIsTheMostUsefulWordTheTvsSaid() {
        assertEquals(Cause(Cause.Kind.BLE_NOT_FOUND), BleSearch.verdict(emptyList(), 0), "aucune TV entendue avec ce code")
        assertEquals(Cause(Cause.Kind.BLE_NOT_FOUND), BleSearch.verdict(listOf(Attempt.Lost()), 0))
        assertEquals(Cause(Cause.Kind.BLE_CONNECT), BleSearch.verdict(listOf(Attempt.NoConnection(), Attempt.Lost()), 2), "entendue mais pas jointe")
        assertEquals(Cause(Cause.Kind.BLE_CONNECT, "canal : RFCOMM"), BleSearch.verdict(listOf(Attempt.Lost(BleSearch.Channel.RFCOMM)), 1), "la TV entendue, le canal qui a cassé est dit")
        assertEquals(Cause(Cause.Kind.CODE_REFUSED), BleSearch.verdict(listOf(Attempt.Lost(), Attempt.Refused(BtActWire.Err.BAD_CODE)), 2))
        assertEquals(Cause(Cause.Kind.LOCKED_OUT, "55"), BleSearch.verdict(listOf(Attempt.Refused(BtActWire.Err.BAD_CODE), Attempt.Refused(BtActWire.Err.LOCKED, 55)), 2), "un verrou en secondes dit combien attendre")
        assertEquals(Cause(Cause.Kind.TERMS), BleSearch.verdict(listOf(Attempt.Refused(BtActWire.Err.BAD_CODE), Attempt.Refused(BtActWire.Err.TERMS)), 2), "les conditions disent quoi faire sur la TV")
        assertEquals(Cause(Cause.Kind.CLOSED), BleSearch.verdict(listOf(Attempt.Refused(BtActWire.Err.TERMS), Attempt.Refused(BtActWire.Err.CLOSED)), 2))
        assertEquals(Cause(Cause.Kind.NEEDS_UPDATE), BleSearch.verdict(listOf(Attempt.Refused(BtActWire.Err.BAD_CODE), Attempt.Refused(BtActWire.Err.VERSION)), 2))
    }

    // ------------------------------------------------------------------ la clé : les mêmes résultats que les autres voies

    @Test fun theKeyOutcomesAreTheOnesOfTheOtherRoutes() {
        fun r(i: BleSearch.Install) = BleSearch.installResult(i, 7)
        r(BleSearch.Install.Done(BtActClient.Session.Installed.Accepted("Licence 1"))).let { assertEquals(KeyAcquisition.ResultKind.OK, it.kind); assertEquals("Clé : Licence 1.", it.message) }
        assertEquals("", r(BleSearch.Install.Done(BtActClient.Session.Installed.Accepted(""))).message)
        r(BleSearch.Install.Done(BtActClient.Session.Installed.Rejected("Cette clé n'est pas celle de cette TV"))).let { assertEquals(KeyAcquisition.ResultKind.REFUSED, it.kind); assertEquals("Cette clé n'est pas celle de cette TV", it.message) }
        assertEquals(KeyAcquisition.ResultKind.REFUSED, r(BleSearch.Install.Done(BtActClient.Session.Installed.Limit)).kind)
        assertEquals(KeyAcquisition.ResultKind.UNREACHABLE, r(BleSearch.Install.Done(BtActClient.Session.Installed.Lost)).kind, "le lien coupé : la clé reste prête, la TV est peut-être activée")
        assertEquals(KeyAcquisition.ResultKind.CODE_REFUSED, r(BleSearch.Install.Blocked(Attempt.Refused(BtActWire.Err.BAD_CODE))).kind, "« Changer le code »")
        r(BleSearch.Install.Blocked(Attempt.Refused(BtActWire.Err.LOCKED, 60))).let { assertEquals(KeyAcquisition.ResultKind.REFUSED, it.kind); assertTrue("60 s" in it.message, it.message) }
        assertEquals(KeyAcquisition.ResultKind.UNREACHABLE, r(BleSearch.Install.Blocked(Attempt.Lost())).kind)
        assertEquals(KeyAcquisition.ResultKind.UNREACHABLE, r(BleSearch.Install.Blocked(Attempt.NoConnection(listOf(BleSearch.Channel.RFCOMM)))).kind)
        assertEquals(KeyAcquisition.ResultKind.UNREACHABLE, r(BleSearch.Install.NotFound).kind)
        assertEquals(7L, r(BleSearch.Install.NotFound).now)
    }

    @Test fun noToStringCarriesTheCodeOrTheKeyOrTheRequest() {
        val all = listOf(seen("tv", -50).toString(), BleSearch.Collector(code).also { it.add(seen("tv", -50), 0) }.candidates().toString(), Attempt.Refused(BtActWire.Err.BAD_CODE).toString(), Attempt.Lost().toString(), Attempt.NoConnection().toString())
        for (t in all) assertFalse(code in t, t)
    }
}
