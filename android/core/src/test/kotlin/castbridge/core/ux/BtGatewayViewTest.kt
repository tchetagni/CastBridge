package castbridge.core.ux

import kotlin.test.*

/**
 * Passerelle Bluetooth plus accessible (demande du propriétaire du 2026-10-03) : quand montrer le bouton, quelle TV est
 * présélectionnée, les textes, la barre visible depuis tous les onglets et les lignes à taper sur le Mac. L'écran ne fait que
 * dessiner ce que ces fonctions renvoient.
 */
class BtGatewayViewTest {
    private val tv = BondedDevice("Smart TV", "74:24:CA:06:00:46")
    private val buds = BondedDevice("Papa’s AirPods", "AC:C9:06:3D:86:E6")
    private val mac = BondedDevice("TCHETAGNI’s MacBook Pro", "5C:E9:1E:8C:6E:70")
    private val car = BondedDevice("CarKit_blink", "00:58:76:10:29:2F")

    // ---- Quand montrer le bouton, et quelle TV présélectionner ----

    @Test fun unreachableTvWithOneBondedTvShowsTheButtonWithThatTvPreselected() {
        val o = BtGatewayView.offer(tvReachable = false, bonded = listOf(buds, tv, mac, car), knownTvs = emptySet(), running = false)
        assertTrue(o is GatewayOffer.Start, "$o")
        assertEquals(tv, o.preselected)
        assertTrue(o.oneTap)
        assertEquals("Passerelle Bluetooth", o.title)
        assertEquals("Pas de Wi-Fi ? Relier la TV par Bluetooth", o.hint)
        assertEquals("Démarrer la passerelle vers Smart TV", o.button)
    }

    @Test fun aTvAlreadySavedInCastBridgeIsPreselectedEvenWithAnOddName() {
        val odd = BondedDevice("Salon", "11:22:33:44:55:66")
        val o = BtGatewayView.offer(false, listOf(buds, odd, tv), knownTvs = setOf("11:22:33:44:55:66"), running = false)
        assertTrue(o is GatewayOffer.Start)
        assertEquals(odd, o.preselected)
        // address compared without regard to case (Android gives upper case, saved lists may not)
        val o2 = BtGatewayView.offer(false, listOf(odd, buds), knownTvs = setOf("11:22:33:44:55:66".lowercase()), running = false)
        assertEquals(odd, (o2 as GatewayOffer.Start).preselected)
    }

    @Test fun theCastBridgeServiceOrATvClassMakesADeviceATv() {
        val box = BondedDevice("Box-4K", "AA:BB:CC:DD:EE:01", tvClass = true)
        val cb = BondedDevice("GaiaOS", "AA:BB:CC:DD:EE:02", castBridgeService = true)
        assertEquals(box, (BtGatewayView.offer(false, listOf(buds, box), emptySet(), false) as GatewayOffer.Start).preselected)
        assertEquals(cb, (BtGatewayView.offer(false, listOf(buds, cb), emptySet(), false) as GatewayOffer.Start).preselected)
    }

    @Test fun twoPossibleTvsAskToChooseInsteadOfGuessing() {
        val tv2 = BondedDevice("AndroidTV", "40:8C:4C:1D:4F:86")
        val o = BtGatewayView.offer(false, listOf(tv, tv2), emptySet(), false)
        assertTrue(o is GatewayOffer.Start)
        assertNull(o.preselected)
        assertFalse(o.oneTap)
        assertEquals(listOf(tv, tv2), o.candidates)
        assertEquals("Choisir la TV et démarrer", o.button)
        // but a TV saved in CastBridge wins over a mere name
        val o2 = BtGatewayView.offer(false, listOf(tv, tv2), setOf(tv2.address), false) as GatewayOffer.Start
        assertEquals(tv2, o2.preselected)
    }

    @Test fun noBondedTvLeadsToAddingTheTvByBluetooth() {
        val o = BtGatewayView.offer(false, listOf(buds, mac, car), emptySet(), false)
        assertTrue(o is GatewayOffer.AddTv, "$o")
        assertEquals("Ajouter ma TV (Bluetooth)", o.button)
        assertEquals("Pas de Wi-Fi ? Relier la TV par Bluetooth", o.hint)
        assertTrue(BtGatewayView.offer(false, emptyList(), emptySet(), false) is GatewayOffer.AddTv)
    }

    @Test fun headphonesCarKitsAndComputersAreNeverTakenForATv() {
        for (d in listOf(buds, mac, car, BondedDevice("HOCO EQ9", "EF:7E:58:FE:FB:36"), BondedDevice("Tvx-headset", "01:02:03:04:05:06")))
            assertFalse(BtGatewayView.looksLikeTv(d, emptySet()), d.name)
        for (n in listOf("Smart TV", "AndroidTV", "TV Salon", "CastBridge TV 720", "[TV] Samsung 7 Series", "MiTV-AXFR0"))
            assertTrue(BtGatewayView.looksLikeTv(BondedDevice(n, "00:00:00:00:00:00"), emptySet()), n)
    }

    @Test fun reachableTvShowsTheButtonOnlyWhenABondedTvExists() {
        assertTrue(BtGatewayView.offer(true, listOf(buds, tv), emptySet(), false) is GatewayOffer.Start)
        assertEquals(GatewayOffer.Hidden, BtGatewayView.offer(true, listOf(buds, mac), emptySet(), false))
    }

    @Test fun withoutTheBluetoothPermissionTheButtonLeadsToTheChoiceScreen() {
        val o = BtGatewayView.offer(false, emptyList(), emptySet(), false, canListBonded = false)
        assertTrue(o is GatewayOffer.Start, "$o")
        assertNull(o.preselected)
        assertFalse(o.oneTap)
    }

    @Test fun runningGatewayReplacesTheButtonByItsCard() {
        assertEquals(GatewayOffer.Running, BtGatewayView.offer(false, listOf(tv), emptySet(), running = true))
        assertEquals(GatewayOffer.Running, BtGatewayView.offer(true, emptyList(), emptySet(), running = true))
    }

    @Test fun oneTapStartIsLoopbackWithApiAndSsh() {
        val s = BtGatewayView.oneTapStart()
        assertTrue(s.api); assertTrue(s.ssh)
        assertFalse(s.exposeLan, "jamais exposée sur le réseau par le bouton rapide")
    }

    // ---- Les lignes à taper sur le Mac ----

    @Test fun runningShowsTheExactMacLinesForSshAndApi() {
        val c = BtGatewayView.macCommands(ssh = true, api = true)
        assertEquals(listOf(
            "adb forward tcp:2222 tcp:2222",
            "ssh -p 2222 tv@127.0.0.1",
            "adb forward tcp:18765 tcp:18765",
            "curl -H 'X-CB-Pin: <code>' http://127.0.0.1:18765/api/hello",
        ), c.map { it.line })
        c.forEach { assertTrue(it.label.isNotBlank()); assertFalse('\n' in it.line) }
        assertEquals(listOf("adb forward tcp:2222 tcp:2222", "ssh -p 2222 tv@127.0.0.1"), BtGatewayView.macCommands(ssh = true, api = false).map { it.line })
        assertEquals(2, BtGatewayView.macCommands(ssh = false, api = true).size)
        assertTrue(BtGatewayView.macCommands(ssh = false, api = false).isEmpty())
    }

    @Test fun thePinPlaceholderIsNeverReplacedByAValue() {
        val api = BtGatewayView.macCommands(ssh = false, api = true).last()
        assertTrue(BtGatewayView.PIN_PLACEHOLDER in api.line)
        assertEquals("<code>", BtGatewayView.PIN_PLACEHOLDER)
        // what « Copier » puts on the clipboard: the line as shown, the placeholder kept
        assertEquals(api.line, BtGatewayView.forClipboard(api.line))
        // a real code that would slip into a line is masked again before it leaves the app
        assertEquals("curl -H 'X-CB-Pin: <code>' http://127.0.0.1:18765/api/hello",
            BtGatewayView.forClipboard("curl -H 'X-CB-Pin: 482913' http://127.0.0.1:18765/api/hello"))
        assertEquals("curl -H \"X-CB-Pin:<code>\" http://127.0.0.1:18765/x",
            BtGatewayView.forClipboard("curl -H \"X-CB-Pin:tok_ABC.def-1\" http://127.0.0.1:18765/x"))
        assertFalse(BtGatewayView.macCommands(true, true).any { Regex("\\d{4,}").containsMatchIn(it.line.replace("2222", "").replace("18765", "").replace("127.0.0.1", "")) })
    }

    @Test fun lanExposureIsSaidWithTheSshAddressOfThePhone() {
        val lines = BtGatewayView.macCommands(ssh = true, api = true, lanSsh = true)
        assertEquals(5, lines.size)
        assertEquals("ssh -p 2222 tv@<adresse du téléphone>", lines[2].line)
    }

    // ---- La barre visible depuis tous les onglets ----

    @Test fun theStripSaysTheGatewayRunsAndOffersToStopIt() {
        val g = BtGatewayView.glance(running = true, tv = "Smart TV", ssh = true, api = true, lan = false)
        assertNotNull(g)
        assertEquals("Passerelle Bluetooth active vers Smart TV", g.title)
        assertEquals("Mac : SSH 2222 · API 18765 (boucle locale)", g.detail)
        assertEquals("Arrêter", g.action)
        assertFalse(g.warning)
        val lan = BtGatewayView.glance(true, "Smart TV", ssh = true, api = false, lan = true)!!
        assertTrue(lan.warning)
        assertTrue("réseau" in lan.detail)
        assertNull(BtGatewayView.glance(running = false, tv = "Smart TV", ssh = true, api = true, lan = false))
    }

    @Test fun theStripShowsOnEveryTabButTheHomeWhichHasTheCard() {
        val g = BtGatewayView.glance(true, "Smart TV", true, true, false)
        assertTrue(BtGatewayView.stripVisible(onHome = false, glance = g))
        assertFalse(BtGatewayView.stripVisible(onHome = true, glance = g))
        assertFalse(BtGatewayView.stripVisible(onHome = false, glance = null))
    }

    @Test fun textsAreFrenchShortAndWithoutJargon() {
        val texts = listOf(BtGatewayView.TITLE, BtGatewayView.HINT, BtGatewayView.RUNNING_HELP, BtGatewayView.ADD_TV_HELP, BtGatewayView.CHOOSE_HELP)
        for (t in texts) {
            assertTrue(t.isNotBlank()); assertFalse('\n' in t)
            for (j in listOf("RFCOMM", "loopback", "localhost", "tunnel", "socket", "gateway")) assertFalse(j in t, "jargon « $j » dans : $t")
        }
        assertTrue(BtGatewayView.HINT.split(' ').size <= 10)
    }
}
