package castbridge.core.owner

import castbridge.core.owner.ActivationRoutePlan.Ble
import castbridge.core.owner.ActivationRoutePlan.Bt
import castbridge.core.owner.ActivationRoutePlan.Cause
import castbridge.core.owner.ActivationRoutePlan.Effect
import castbridge.core.owner.ActivationRoutePlan.Event
import castbridge.core.owner.ActivationRoutePlan.Facts
import castbridge.core.owner.ActivationRoutePlan.Found
import castbridge.core.owner.ActivationRoutePlan.LineState
import castbridge.core.owner.ActivationRoutePlan.Phase
import castbridge.core.owner.ActivationRoutePlan.Route
import castbridge.core.owner.ActivationRoutePlan.Run
import castbridge.core.trust.DeviceRequestParse
import castbridge.core.trust.TvDeviceRequest
import castbridge.core.tv.WdCode
import kotlin.test.*

/**
 * La voie « Bluetooth sans appairage » dans le plan d'« Activer la TV » (act-bt, DESIGN-ACTIVATION-SIMPLE § 7) : entre le réseau local et le réseau direct (qui peut couper le Wi-Fi de la TV), bornée à 25 s,
 * sautée avec sa cause quand elle est impossible, ses causes en français, l'ordre final des voies. Temps simulé. Les chronologies d'avant cette voie sont dans `ActivationRoutePlanTest`.
 */
class ActivationBlePlanTest {
    private val code = "482913"
    private fun facts(api: Int = 34, wifiOn: Boolean = true, onWifi: Boolean = true, bt: Bt = Bt.PAIRED, ble: Ble = Ble.SEARCH) = Facts(code, api, wifiOn, onWifi, bt, ble)
    private fun start(f: Facts = facts(), now: Long = 1_000) = ActivationRoutePlan.start(f, now)
    private fun Run.on(e: Event) = ActivationRoutePlan.reduce(this, e)
    private val fp = Fingerprints(mapOf(FactorKind.FLASH to "0a1b2c3d4e5f60718293a4b5c6d7e8f9", FactorKind.SYSTEM_SERIAL to "aaaaaaaabbbbbbbbccccccccdddddddd"))
    private val request: TvDeviceRequest = (LockedRequestRoute.parse(OwnerFrames.deviceInfo(DeviceCode.of(fp), fp, null, ByteArray(32) { it.toByte() })) as DeviceRequestParse.Ok).request
    private fun ble(name: String = "CastBridge TV salon") = Found(Route.BLE, name, null, request)
    private fun allTexts(r: Run) = listOf(r.toString(), r.facts.toString(), r.headline()) + r.lines().map { it.text } + listOfNotNull(r.hint(), (r.phase as? Phase.Failed)?.message)

    private val bleStart = 11_000L                                              // the local network gives up at 1 000 + 10 s
    private val bleEnd = bleStart + ActivationRoutePlan.BLE_MS
    private val groupEnd = bleEnd + ActivationRoutePlan.GROUP_MS
    private val bluetoothEnd = groupEnd + ActivationRoutePlan.BT_MS

    // ------------------------------------------------------------------ l'ordre et les bornes

    @Test fun theOrderIsLocalNetworkThenBluetoothWithoutPairingThenDirectNetworkThenPairedBluetooth() {
        var s = start()
        val tried = ArrayList<Route>(); s.effects.filterIsInstance<Effect.Try>().forEach { tried += it.route }
        var t = 1_000L
        while (s.run.phase !is Phase.Failed) { t += 500; s = s.run.on(Event.Tick(t)); s.effects.filterIsInstance<Effect.Try>().forEach { tried += it.route } }
        assertEquals(listOf(Route.LAN, Route.BLE, Route.GROUP, Route.BLUETOOTH), tried, "chaque voie une fois, dans cet ordre")
        assertEquals(listOf(Route.LAN, Route.BLE, Route.GROUP, Route.BLUETOOTH), Route.values().toList())
        assertEquals("Bluetooth sans appairage", Route.BLE.label); assertEquals("Bluetooth appairé", Route.BLUETOOTH.label)
        assertTrue(t >= bluetoothEnd - 500, "le dernier échec arrive à la borne du Bluetooth appairé : $t")
    }

    @Test fun theLocalNetworkGivesUpThenBluetoothWithoutPairingIsTriedForTwentyFiveSeconds() {
        val s = start()
        assertEquals(s.run, s.run.on(Event.Tick(bleStart - 1)).run)
        val next = s.run.on(Event.Tick(bleStart))
        assertEquals(Phase.Trying(Route.BLE, bleStart, 25_000), next.run.phase)
        assertEquals(listOf(Effect.Abort(Route.LAN), Effect.Try(Route.BLE, 25_000)), next.effects)
        assertEquals(next.run, next.run.on(Event.Tick(bleEnd - 1)).run, "24,999 s : la voie est encore tentée")
        val group = next.run.on(Event.Tick(bleEnd))
        assertEquals(Phase.Trying(Route.GROUP, bleEnd, 50_000), group.run.phase)
        assertEquals(listOf(Effect.Abort(Route.BLE), Effect.Try(Route.GROUP, 50_000)), group.effects)
        assertEquals(Cause(Cause.Kind.BLE_NOT_FOUND), group.run.causes.getValue(Route.BLE), "sans rien d'entendu : « aucune TV ne s'annonce avec ce code »")
    }

    @Test fun theBluetoothWithoutPairingComesBeforeTheDirectNetworkWhichMayCutTheTvsWifi() {
        val s = start(facts(wifiOn = false))                                       // Wi-Fi du téléphone éteint : seules les voies Bluetooth restent, la voie sans appairage d'abord
        assertEquals(Phase.Trying(Route.BLE, 1_000, 25_000), s.run.phase)
        assertEquals(listOf<Effect>(Effect.Try(Route.BLE, 25_000)), s.effects)
        assertEquals(Cause.Kind.WIFI_OFF, s.run.causes.getValue(Route.LAN).kind)
        val after = s.run.on(Event.Tick(1_000 + 25_000))
        assertEquals(Phase.Trying(Route.BLUETOOTH, 26_000, 20_000), after.run.phase, "le groupe Wi-Fi Direct est sauté (Wi-Fi éteint) : le Bluetooth appairé suit")
    }

    @Test fun theBoundCoversTheScanAndOneAttemptOnATv() {
        assertEquals(25_000L, ActivationRoutePlan.BLE_MS)
        assertTrue(BleSearch.SCAN_MS + BleSearch.ATTEMPT_MS <= ActivationRoutePlan.BLE_MS, "10 s de balayage + 12 s sur une TV tiennent dans 25 s")
        assertEquals(listOf(10_000L, 25_000L, 50_000L, 20_000L), Route.values().map(ActivationRoutePlan::boundMs))
    }

    @Test fun aDefinitiveFailureOfTheRouteMovesOnAtOnce() {
        val s = start().run.on(Event.Tick(bleStart)).run.on(Event.Failed(Route.BLE, Cause(Cause.Kind.BLE_NOT_FOUND), bleStart + 10_500))
        assertEquals(Phase.Trying(Route.GROUP, bleStart + 10_500, 50_000), s.run.phase)
        assertEquals(listOf(Effect.Abort(Route.BLE), Effect.Try(Route.GROUP, 50_000)), s.effects)
    }

    @Test fun aTvFoundWithoutPairingEndsTheSearchAndIsNamed() {
        val s = start().run.on(Event.Tick(bleStart)).run.on(Event.Reached(ble()))
        assertEquals(Phase.Connected(ble()), s.run.phase)
        assertTrue(s.effects.isEmpty())
        assertEquals(listOf(LineState.FAILED, LineState.DONE, LineState.UNUSED, LineState.UNUSED), s.run.lines().map { it.state })
        assertEquals("Bluetooth sans appairage : TV trouvée (CastBridge TV salon).", s.run.lines()[1].text, "pas d'adresse IP à dire : la TV est jointe par Bluetooth")
        assertNull(ble().address()); assertNull(s.run.hint())
        assertEquals("TV trouvée : CastBridge TV salon", s.run.headline())
        assertEquals(s.run, s.run.on(Event.Tick(9_999_999)).run, "connecté : plus de borne")
    }

    @Test fun aTvWhoseRequestCouldNotBeReadIsFoundAllTheSame() {
        val f = Found(Route.BLE, "CastBridge TV salon", null, null, note = Cause(Cause.Kind.UNREADABLE))
        val s = start().run.on(Event.Tick(bleStart)).run.on(Event.Reached(f))
        assertEquals(Phase.Connected(f), s.run.phase)
        assertNull(s.run.hint(), "la ligne « mettez la TV à jour » est pour les TV qui ne savent pas lire (0.14.43)")
    }

    @Test fun eventsOfTheRouteWhenItIsNotTheCurrentOneAreIgnored() {
        val r = start().run
        assertEquals(r, r.on(Event.Reached(ble())).run, "la voie sans appairage n'est pas en cours")
        assertEquals(r, r.on(Event.Failed(Route.BLE, Cause(Cause.Kind.BLE_NOT_FOUND), 2_000)).run)
        assertEquals(r, r.on(Event.Observed(Route.BLE, Cause(Cause.Kind.CODE_REFUSED))).run)
        val connected = r.on(Event.Reached(Found(Route.LAN, "TV", "http://192.168.1.20:8765", null))).run
        assertEquals(connected, connected.on(Event.Failed(Route.BLE, Cause(Cause.Kind.BLE_CONNECT), 3_000)).run)
        assertEquals(connected, connected.on(Event.Lost(Route.BLE, 3_000)).run, "aucune liaison tenue : rien à perdre")
    }

    // ------------------------------------------------------------------ une voie impossible est sautée avec sa cause, jamais attendue

    @Test fun anImpossibleRouteIsSkippedWithItsCauseAndTheDirectNetworkStartsAtOnce() {
        val cases = listOf(Ble.OFF to Cause.Kind.BT_OFF, Ble.NO_PERMISSION to Cause.Kind.BLE_PERMISSION, Ble.NO_ADAPTER to Cause.Kind.BT_NO_ADAPTER, Ble.NO_LE to Cause.Kind.BLE_UNSUPPORTED)
        for ((fact, kind) in cases) {
            val s = start(facts(ble = fact)).run.on(Event.Tick(bleStart))
            assertEquals(kind, s.run.causes.getValue(Route.BLE).kind, "$fact")
            assertEquals(Phase.Trying(Route.GROUP, bleStart, 50_000), s.run.phase, "$fact : le groupe commence à l'instant même")
            assertEquals(listOf(Effect.Abort(Route.LAN), Effect.Try(Route.GROUP, 50_000)), s.effects, "$fact : aucune étape sans appairage lancée")
            assertEquals(LineState.SKIPPED, s.run.lines()[1].state)
        }
    }

    @Test fun theCausesAreInFrenchOneSentenceEach() {
        val ssid = WdCode.networkName(code)
        fun t(k: Cause.Kind, d: String? = null) = Cause(k, d).text(Route.BLE, ssid)
        assertEquals("Le Bluetooth du téléphone est éteint.", t(Cause.Kind.BT_OFF))
        assertEquals("L'autorisation « Appareils à proximité » est refusée : sans elle CastBridge ne peut pas trouver la TV sans appairage. Donnez-la dans les réglages d'Android.", t(Cause.Kind.BLE_PERMISSION))
        assertEquals("Aucune TV ne s'annonce en Bluetooth avec ce code : relisez les 6 chiffres affichés sur la TV, et vérifiez qu'elle affiche son écran d'activation (CastBridge-TV à jour).", t(Cause.Kind.BLE_NOT_FOUND))
        assertEquals("La TV s'annonce, mais la liaison Bluetooth n'a pas abouti : rapprochez le téléphone de la TV, puis touchez « Réessayer ».", t(Cause.Kind.BLE_CONNECT))
        assertEquals("La TV s'annonce, mais la liaison Bluetooth n'a pas abouti (essayé : L2CAP, RFCOMM) : rapprochez le téléphone de la TV, puis touchez « Réessayer ».", t(Cause.Kind.BLE_CONNECT, "essayé : L2CAP, RFCOMM"))
        assertEquals("La TV s'annonce, mais la liaison Bluetooth n'a pas abouti (canal : L2CAP) : rapprochez le téléphone de la TV, puis touchez « Réessayer ».", t(Cause.Kind.BLE_CONNECT, "canal : L2CAP"))
        assertEquals("La TV a refusé ce code : relisez les 6 chiffres affichés sur l'écran de la TV.", t(Cause.Kind.CODE_REFUSED))
        assertEquals("Trop de codes faux : la TV attend 60 s avant de reprendre.", t(Cause.Kind.LOCKED_OUT, "60"), "5 refus de suite")
        assertEquals("Ce téléphone ne sait pas chercher les appareils Bluetooth basse consommation : la voie sans appairage est impossible ici.", t(Cause.Kind.BLE_UNSUPPORTED))
        assertEquals("Android a refusé la recherche Bluetooth (trop de recherches de suite ?) : attendez 30 secondes, puis touchez « Réessayer ».", t(Cause.Kind.BLE_SCAN_FAILED), "Android limite les balayages : 5 en 30 secondes")
        assertEquals("Les conditions d'usage ne sont pas encore acceptées sur la TV : cochez la case sur son écran d'activation.", t(Cause.Kind.TERMS))
        assertEquals("Mettez la TV à jour pour l'activation sans réseau.", ActivationRoutePlan.UPDATE_LINE)
        assertTrue(ActivationRoutePlan.UPDATE_LINE in t(Cause.Kind.NEEDS_UPDATE))
    }

    @Test fun thePermissionSentenceShownOnceBeforeTheRequestIsTheOwnersWords() {
        assertEquals("Appareils à proximité : pour trouver la TV sans appairage", ActivationRoutePlan.BLE_PERMISSION_WHY)
        assertTrue("Position" in ActivationRoutePlan.BLE_PERMISSION_WHY_LOCATION && "ne s'en sert pas" in ActivationRoutePlan.BLE_PERMISSION_WHY_LOCATION)
    }

    // ------------------------------------------------------------------ ce que la TV a dit compte à la borne

    @Test fun whatTheTvSaidIsTheCauseWhenTheBoundIsReached() {
        for (seen in listOf(Cause(Cause.Kind.CODE_REFUSED), Cause(Cause.Kind.LOCKED_OUT, "60"), Cause(Cause.Kind.TERMS), Cause(Cause.Kind.CLOSED), Cause(Cause.Kind.BLE_CONNECT))) {
            val s = start().run.on(Event.Tick(bleStart)).run.on(Event.Observed(Route.BLE, seen)).run.on(Event.Tick(bleEnd))
            assertEquals(seen, s.run.causes.getValue(Route.BLE))
        }
    }

    @Test fun aTvThatAppearsAfterARefusalStillWins() {
        val s = start().run.on(Event.Tick(bleStart)).run.on(Event.Observed(Route.BLE, Cause(Cause.Kind.CODE_REFUSED))).run.on(Event.Reached(ble()))
        assertEquals(Phase.Connected(ble()), s.run.phase)
    }

    @Test fun theActiveLineSaysWhatIsHappeningAndHowLongItMayTake() {
        val r = start().run.on(Event.Tick(bleStart)).run
        val l = r.lines()[1]
        assertEquals(LineState.ACTIVE, l.state)
        assertEquals("Bluetooth sans appairage : recherche de la TV (25 s au plus)…", l.text)
    }

    // ------------------------------------------------------------------ l'échec final : quatre voies, chacune avec sa cause

    @Test fun theFinalFailureNamesFourRoutesInOrderWithTheirOwnCauses() {
        var s = start().run.on(Event.Tick(bleStart)).run.on(Event.Observed(Route.BLE, Cause(Cause.Kind.CODE_REFUSED))).run.on(Event.Tick(bleEnd))
        s = s.run.on(Event.Tick(groupEnd)); s = s.run.on(Event.Tick(bluetoothEnd))
        val m = assertIs<Phase.Failed>(s.run.phase).message
        val bullets = m.lines().filter { it.startsWith("• ") }
        assertEquals(4, bullets.size, m)
        assertTrue(bullets[0].startsWith("• Réseau local : "), bullets[0])
        assertEquals("• Bluetooth sans appairage : La TV a refusé ce code : relisez les 6 chiffres affichés sur l'écran de la TV.", bullets[1])
        assertTrue(bullets[2].startsWith("• Réseau de la TV (Wi-Fi Direct) : ") || bullets[2].startsWith("• Réseau de la TV introuvable"), bullets[2])
        assertTrue(bullets[3].startsWith("• Bluetooth appairé : "), bullets[3])
        assertTrue(m.lines().last().startsWith("Relisez les 6 chiffres affichés sur la TV"), m)
    }

    @Test fun theFinalAdviceFollowsTheBluetoothCausesToo() {
        fun advice(cause: Cause): String {
            var s = start().run.on(Event.Tick(bleStart)).run.on(Event.Observed(Route.BLE, cause)).run.on(Event.Tick(bleEnd))
            s = s.run.on(Event.Tick(groupEnd)); s = s.run.on(Event.Tick(bluetoothEnd))
            return (s.run.phase as Phase.Failed).message.lines().last()
        }
        assertTrue(advice(Cause(Cause.Kind.LOCKED_OUT, "60")).startsWith("Attendez quelques minutes"))
        assertTrue(advice(Cause(Cause.Kind.TERMS)).startsWith("Acceptez les conditions d'usage sur la TV"))
        assertTrue(advice(Cause(Cause.Kind.CODE_REFUSED)).startsWith("Relisez les 6 chiffres"))
        val perm = start(facts(ble = Ble.NO_PERMISSION, wifiOn = false, bt = Bt.OFF))
        val m = (perm.run.phase as Phase.Failed).message
        assertTrue(m.lines().last().startsWith("Donnez l'autorisation « Appareils à proximité » à CastBridge"), m)
        assertTrue("• Bluetooth sans appairage : L'autorisation « Appareils à proximité » est refusée" in m, m)
    }

    @Test fun withNothingPossibleAtAllItFailsAtOnceAndNamesTheFourRoutes() {
        val s = start(facts(wifiOn = false, bt = Bt.OFF, ble = Ble.OFF))
        val failed = assertIs<Phase.Failed>(s.run.phase)
        assertTrue(s.effects.isEmpty())
        for (r in Route.values()) assertTrue(failed.message.contains(r.label), "la voie « ${r.label} » est nommée : ${failed.message}")
    }

    @Test fun theDirectNetworkGestureRuleIsUnchangedByTheNewRoute() {
        // la TV sur une box, le téléphone en données mobiles, une TV sans la voie sans appairage : le groupe expire, « Réessayer » est proposé tout de suite
        var s = start(facts(onWifi = false))
        assertEquals(Phase.Trying(Route.BLE, 1_000, 25_000), s.run.phase, "le réseau local est sauté (aucun Wi-Fi) : la voie sans appairage commence")
        s = s.run.on(Event.Tick(1_000 + ActivationRoutePlan.BLE_MS))
        assertEquals(Phase.Trying(Route.GROUP, 26_000, 50_000), s.run.phase)
        s = s.run.on(Event.Tick(26_000 + ActivationRoutePlan.GROUP_MS))
        assertEquals(Cause.Kind.GROUP_NOT_FOUND, s.run.causes.getValue(Route.GROUP).kind)
        assertTrue(s.run.retryOffered())
    }

    @Test fun theCodeNeverAppearsInAnyTextOfTheNewRoute() {
        val runs = mutableListOf<Run>()
        var s = start(); runs += s.run
        s = s.run.on(Event.Tick(bleStart)); runs += s.run
        s = s.run.on(Event.Observed(Route.BLE, Cause(Cause.Kind.CODE_REFUSED))); runs += s.run
        s = s.run.on(Event.Tick(bleEnd)); runs += s.run
        s = s.run.on(Event.Tick(groupEnd)); runs += s.run
        s = s.run.on(Event.Tick(bluetoothEnd)); runs += s.run
        runs += start().run.on(Event.Tick(bleStart)).run.on(Event.Reached(ble())).run
        for (f in Ble.values()) runs += start(facts(ble = f)).run
        for (r in runs) for (t in allTexts(r)) assertFalse(code in t, "le code ne doit figurer dans aucun texte : $t")
        assertFalse(code in facts().toString())
        assertTrue("ble=SEARCH" in facts().toString())
    }

    @Test fun cancellingDuringTheRouteStopsIt() {
        val s = start().run.on(Event.Tick(bleStart)).run.on(Event.Cancel)
        assertEquals(Phase.Cancelled, s.run.phase)
        assertEquals(listOf<Effect>(Effect.Abort(Route.BLE)), s.effects)
    }
}
