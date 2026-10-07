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
import castbridge.core.link.JoinMethod
import castbridge.core.link.WdJoin
import castbridge.core.tv.WdCode
import kotlin.test.*

/**
 * « Code affiché sur la TV » : l'ordre des voies (réseau local, Bluetooth sans appairage, groupe Wi-Fi Direct dérivé du code, Bluetooth appairé), leurs bornes (10 s, 25 s, 50 s, 20 s), la ligne d'état de chaque
 * étape, la cause par voie à l'échec, Android 9 et moins (jonction à la main). Temps simulé : aucun fil, aucune horloge.
 * Ce fichier garde les chronologies d'AVANT la voie sans appairage : son aide `facts()` la met à « éteinte » (`Ble.OFF` : sautée avec sa cause, jamais attendue), donc LAN, groupe et Bluetooth appairé se
 * succèdent aux mêmes instants. La voie sans appairage elle-même est dans `ActivationBlePlanTest` (act-bt).
 * (docs/coordination/DESIGN-ACTIVATION-SIMPLE-2026-10-07.md, ACT-F2, ACT-F3, ACT-NF2, ACT-NF4)
 */
class ActivationRoutePlanTest {
    private val code = "482913"
    private fun facts(api: Int = 34, wifiOn: Boolean = true, onWifi: Boolean = true, bt: Bt = Bt.PAIRED, ble: Ble = Ble.OFF) = Facts(code, api, wifiOn, onWifi, bt, ble)
    private fun lan(name: String = "CastBridge TV salon") = Found(Route.LAN, name, "http://192.168.1.20:8765", null)
    private fun group() = Found(Route.GROUP, WdCode.networkName(code), "http://192.168.49.1:8765", null)
    private fun bt() = Found(Route.BLUETOOTH, "SMART_TV", null, null, btAddress = "AA:BB:CC:DD:EE:FF")

    private fun start(f: Facts = facts(), now: Long = 1_000) = ActivationRoutePlan.start(f, now)
    private fun Run.on(e: Event) = ActivationRoutePlan.reduce(this, e)
    private fun Run.trying() = (phase as Phase.Trying)
    private fun allTexts(r: Run) = listOf(r.toString(), r.facts.toString(), r.headline()) + r.lines().map { it.text } + listOfNotNull(r.hint(), (r.phase as? Phase.Failed)?.message)

    // ------------------------------------------------------------------ l'ordre et les bornes

    // la borne du groupe est celle de la boîte « Se connecter ? » d'Android ailleurs dans l'application (audit I-6 : 20 s étaient trop courts, la TV met jusqu'à 10 s à créer son groupe après un OK)
    private val groupEnd = 11_000L + ActivationRoutePlan.GROUP_MS             // le réseau local a abandonné à 11 000 (départ à 1 000 + 10 s), le groupe 50 s plus tard
    private val bluetoothEnd = groupEnd + ActivationRoutePlan.BT_MS

    @Test fun theBoundsAreTenTwentyFiveFiftyAndTwentySeconds() {
        assertEquals(10_000L, ActivationRoutePlan.LAN_MS); assertEquals(25_000L, ActivationRoutePlan.BLE_MS); assertEquals(50_000L, ActivationRoutePlan.GROUP_MS); assertEquals(20_000L, ActivationRoutePlan.BT_MS)
        assertEquals(listOf(10_000L, 25_000L, 50_000L, 20_000L), Route.values().map(ActivationRoutePlan::boundMs))
        assertEquals(listOf(Route.LAN, Route.BLE, Route.GROUP, Route.BLUETOOTH), Route.values().toList(), "l'ordre : réseau local, Bluetooth sans appairage, groupe, Bluetooth appairé")
    }

    @Test fun theGroupBoundIsTheOneOfTheSystemBoxEverywhereElseInTheApp() {
        assertEquals(WdJoin.joinTimeoutMs(JoinMethod.NETWORK_SPECIFIER), ActivationRoutePlan.boundMs(Route.GROUP), "une seule valeur pour la boîte « Se connecter ? »")
        assertEquals(WdJoin.NETWORK_SPECIFIER_JOIN_MS, ActivationRoutePlan.GROUP_MS)
        assertEquals(50_000L, ActivationRoutePlan.GROUP_MS)
        assertEquals(Phase.Trying(Route.GROUP, 11_000, 50_000), start().run.on(Event.Tick(11_000)).run.phase, "le plan tente le groupe pendant 50 s")
        assertEquals(50_000L, WdJoin.NETWORK_SPECIFIER_JOIN_MS); assertEquals(20_000L, WdJoin.joinTimeoutMs(JoinMethod.P2P_CONNECT), "la jonction P2P garde ses 20 s")
    }

    @Test fun itStartsWithTheLocalNetworkAndAsksTheExecutorToTryIt() {
        val s = start()
        assertEquals(Phase.Trying(Route.LAN, 1_000, 10_000), s.run.phase)
        assertEquals(listOf<Effect>(Effect.Try(Route.LAN, 10_000)), s.effects)
    }

    @Test fun theLocalNetworkGivesUpAfterTenSecondsExactlyThenTheGroupIsTried() {
        val s = start()
        assertEquals(s.run, s.run.on(Event.Tick(10_999)).run, "9,999 s : rien ne change")
        val next = s.run.on(Event.Tick(11_000))
        assertEquals(Phase.Trying(Route.GROUP, 11_000, 50_000), next.run.phase)
        assertEquals(listOf(Effect.Abort(Route.LAN), Effect.Try(Route.GROUP, 50_000)), next.effects, "l'étape précédente est arrêtée, la suivante commence")
        assertEquals(Cause.Kind.NOT_ANNOUNCED, next.run.causes.getValue(Route.LAN).kind)
    }

    @Test fun theGroupGivesUpAfterFiftySecondsThenBluetoothAfterTwentyMoreThenItFails() {
        var s = start().run.on(Event.Tick(11_000))
        assertEquals(s.run, s.run.on(Event.Tick(60_999)).run, "49,999 s : le groupe est encore tenté")
        s = s.run.on(Event.Tick(61_000))
        assertEquals(Phase.Trying(Route.BLUETOOTH, 61_000, 20_000), s.run.phase)
        assertEquals(listOf(Effect.Abort(Route.GROUP), Effect.Try(Route.BLUETOOTH, 20_000)), s.effects)
        assertEquals(s.run, s.run.on(Event.Tick(80_999)).run)
        val end = s.run.on(Event.Tick(81_000))
        assertIs<Phase.Failed>(end.run.phase)
        assertEquals(listOf<Effect>(Effect.Abort(Route.BLUETOOTH)), end.effects)
    }

    @Test fun aTvFoundOnTheLocalNetworkEndsTheSearchWithoutTryingTheOthers() {
        val s = start().run.on(Event.Reached(lan()))
        assertEquals(Phase.Connected(lan()), s.run.phase)
        assertTrue(s.effects.isEmpty())
        assertEquals(s.run, s.run.on(Event.Tick(999_999)).run, "connecté : plus de borne")
        assertEquals(listOf(LineState.DONE, LineState.UNUSED, LineState.UNUSED, LineState.UNUSED), s.run.lines().map { it.state })
    }

    @Test fun theGroupRouteFindsTheTvWhenThereIsNoCommonNetwork() {
        val s = start().run.on(Event.Tick(11_000)).run.on(Event.Reached(group()))
        assertEquals(Phase.Connected(group()), s.run.phase)
        assertEquals(listOf(LineState.FAILED, LineState.SKIPPED, LineState.DONE, LineState.UNUSED), s.run.lines().map { it.state }, "le Bluetooth sans appairage éteint (aide de ce fichier) est sauté avec sa cause")
    }

    @Test fun bluetoothIsTheLastResortAndConnects() {
        val s = start(facts(wifiOn = false)).run.on(Event.Reached(bt()))
        assertEquals(Phase.Connected(bt()), s.run.phase)
        assertEquals("SMART_TV", (s.run.phase as Phase.Connected).found.name)
    }

    @Test fun aDefinitiveFailureMovesOnAtOnceWithoutWaitingForTheBound() {
        val s = start().run.on(Event.Failed(Route.LAN, Cause(Cause.Kind.CLOSED), 2_500))
        assertEquals(Phase.Trying(Route.GROUP, 2_500, 50_000), s.run.phase)
        assertEquals(Cause.Kind.CLOSED, s.run.causes.getValue(Route.LAN).kind)
        assertEquals(listOf(Effect.Abort(Route.LAN), Effect.Try(Route.GROUP, 50_000)), s.effects)
    }

    @Test fun eventsOfARouteThatIsNotTheCurrentOneAreIgnored() {
        val r = start().run
        assertEquals(r, r.on(Event.Reached(group())).run, "le groupe n'est pas en cours")
        assertEquals(r, r.on(Event.Failed(Route.BLUETOOTH, Cause(Cause.Kind.BT_OFF), 2_000)).run)
        assertEquals(r, r.on(Event.Observed(Route.GROUP, Cause(Cause.Kind.CODE_REFUSED, "x"))).run)
        assertEquals(r, r.on(Event.UserJoined(2_000)).run, "pas de jonction manuelle en attente")
        val connected = r.on(Event.Reached(lan())).run
        assertEquals(connected, connected.on(Event.Failed(Route.LAN, Cause(Cause.Kind.TIMEOUT), 3_000)).run, "une fois connecté, un échec tardif ne défait rien")
        assertEquals(connected, connected.on(Event.Reached(group())).run)
    }

    // ------------------------------------------------------------------ ce que l'étape a vu compte au moment de la borne

    @Test fun whatTheTvSaidIsTheCauseWhenTheBoundIsReached() {
        var s = start().run.on(Event.Observed(Route.LAN, Cause(Cause.Kind.CODE_REFUSED, "CastBridge TV salon")))
        s = s.run.on(Event.Tick(11_000))
        assertEquals(Cause(Cause.Kind.CODE_REFUSED, "CastBridge TV salon"), s.run.causes.getValue(Route.LAN))
    }

    @Test fun aTvThatAppearsAfterARefusalStillWins() {
        val r = start().run.on(Event.Observed(Route.LAN, Cause(Cause.Kind.CODE_REFUSED, "TV du voisin"))).run.on(Event.Reached(lan("TV salon")))
        assertEquals(Phase.Connected(lan("TV salon")), r.run.phase)
    }

    // ------------------------------------------------------------------ ce que le téléphone sait avant d'essayer

    @Test fun withTheWifiOffOnlyBluetoothRemainsAndStartsAtOnce() {
        val s = start(facts(wifiOn = false))
        assertEquals(Phase.Trying(Route.BLUETOOTH, 1_000, 20_000), s.run.phase)
        assertEquals(listOf<Effect>(Effect.Try(Route.BLUETOOTH, 20_000)), s.effects)
        assertEquals(Cause.Kind.WIFI_OFF, s.run.causes.getValue(Route.LAN).kind)
        assertEquals(Cause.Kind.WIFI_OFF, s.run.causes.getValue(Route.GROUP).kind)
        assertEquals(listOf(LineState.SKIPPED, LineState.SKIPPED, LineState.SKIPPED, LineState.ACTIVE), s.run.lines().map { it.state }, "les voies impossibles sont sautées d'un coup, avec leur cause")
    }

    @Test fun notBeingOnAnyWifiSkipsOnlyTheLocalNetwork() {
        val s = start(facts(onWifi = false))
        assertEquals(Phase.Trying(Route.GROUP, 1_000, 50_000), s.run.phase)
        assertEquals(Cause.Kind.NOT_ON_WIFI, s.run.causes.getValue(Route.LAN).kind)
    }

    @Test fun bluetoothThatCannotWorkIsSkippedWithItsCauseAndNeverWaitedFor() {
        for ((bt, kind) in listOf(Bt.OFF to Cause.Kind.BT_OFF, Bt.NO_PERMISSION to Cause.Kind.BT_PERMISSION, Bt.NO_ADAPTER to Cause.Kind.BT_NO_ADAPTER)) {
            val end = start(facts(bt = bt)).run.on(Event.Tick(11_000)).run.on(Event.Tick(groupEnd))
            val failed = assertIs<Phase.Failed>(end.run.phase, "$bt")
            assertEquals(kind, end.run.causes.getValue(Route.BLUETOOTH).kind)
            assertTrue(end.effects.none { it is Effect.Try }, "aucune étape Bluetooth lancée")
            assertTrue(failed.message.contains("Bluetooth"))
        }
    }

    @Test fun withNothingPossibleAtAllItFailsAtOnceWithTheThreeCauses() {
        val s = start(facts(wifiOn = false, bt = Bt.OFF))
        val failed = assertIs<Phase.Failed>(s.run.phase)
        assertTrue(s.effects.isEmpty())
        for (r in Route.values()) assertTrue(failed.message.contains(r.label), "la voie « ${r.label} » est nommée : ${failed.message}")
    }

    // ------------------------------------------------------------------ Android 9 et moins : le groupe se rejoint à la main

    @Test fun beforeAndroid10TheGroupBecomesAnInstructionWithTheDerivedNameAndPassword() {
        val s = start(facts(api = 28)).run.on(Event.Tick(11_000))
        val p = assertIs<Phase.ManualJoin>(s.run.phase)
        assertEquals(WdCode.networkName(code), p.ssid); assertEquals(WdCode.passphrase(code), p.passphrase)
        assertEquals(listOf<Effect>(Effect.Abort(Route.LAN)), s.effects, "aucune jonction automatique avant Android 10")
        val text = s.run.lines()[2].text
        assertTrue(WdCode.networkName(code) in text && WdCode.passphrase(code) in text, "le mot de passe dérivé est affiché en clair sur le téléphone : $text")
        assertTrue(text.startsWith("Réseau de la TV") && "Connectez le téléphone au Wi-Fi « ${WdCode.networkName(code)} »" in text, text)
        assertEquals(LineState.ASKING, s.run.lines()[2].state)
        assertEquals(s.run, s.run.on(Event.Tick(500_000)).run, "attente de l'usager : aucune borne qui tourne")
    }

    @Test fun theManualJoinThenProbesTheGroupFor50Seconds() {
        val waiting = start(facts(api = 26)).run.on(Event.Tick(11_000)).run
        val go = waiting.on(Event.UserJoined(40_000))
        assertEquals(Phase.Trying(Route.GROUP, 40_000, 50_000), go.run.phase)
        assertEquals(listOf<Effect>(Effect.Try(Route.GROUP, 50_000, manual = true)), go.effects)
        val late = go.run.on(Event.Tick(90_000))
        assertEquals(Phase.Trying(Route.BLUETOOTH, 90_000, 20_000), late.run.phase)
        assertEquals(Cause.Kind.NOT_JOINED, late.run.causes.getValue(Route.GROUP).kind)
        assertEquals(listOf(Effect.Abort(Route.GROUP), Effect.Try(Route.BLUETOOTH, 20_000)), late.effects)
    }

    @Test fun theManualJoinCanBeSkippedToGoToBluetooth() {
        val s = start(facts(api = 28)).run.on(Event.Tick(11_000)).run.on(Event.UserSkipped(15_000))
        assertEquals(Phase.Trying(Route.BLUETOOTH, 15_000, 20_000), s.run.phase)
        assertEquals(Cause.Kind.MANUAL_SKIPPED, s.run.causes.getValue(Route.GROUP).kind)
    }

    @Test fun fromAndroid10TheGroupIsJoinedByTheApp() {
        for (api in listOf(29, 30, 31, 33, 34, 35)) {
            val s = start(facts(api = api)).run.on(Event.Tick(11_000))
            assertEquals(Phase.Trying(Route.GROUP, 11_000, 50_000), s.run.phase, "api $api")
            assertFalse((s.effects.last() as Effect.Try).manual)
        }
        assertEquals(29, ActivationRoutePlan.GROUP_MIN_API)
    }

    // ------------------------------------------------------------------ le Bluetooth demande parfois une validation de l'usager

    @Test fun anAndroidPairingDialogGivesBluetoothTimeToBeConfirmed() {
        var s = start(facts(wifiOn = false)).run.on(Event.Pairing(5_000))
        assertEquals(Phase.Trying(Route.BLUETOOTH, 1_000, ActivationRoutePlan.BT_PAIRING_MS), s.run.phase)
        assertEquals(s.run, s.run.on(Event.Tick(1_000 + 89_999)).run)
        s = s.run.on(Event.Tick(1_000 + 90_000))
        assertIs<Phase.Failed>(s.run.phase)
        assertTrue(ActivationRoutePlan.BT_PAIRING_MS > ActivationRoutePlan.BT_MS)
        assertEquals(s.run.lines()[3].state, LineState.FAILED)
    }

    @Test fun aPairingNoticeOnAnotherRouteChangesNothing() {
        val r = start().run
        assertEquals(r, r.on(Event.Pairing(2_000)).run)
    }

    // ------------------------------------------------------------------ les lignes d'état

    @Test fun theActiveLineSaysWhatIsHappeningAndHowLongItMayTake() {
        val r = start().run
        val l = r.lines()
        assertEquals(listOf(LineState.ACTIVE, LineState.WAITING, LineState.WAITING, LineState.WAITING), l.map { it.state })
        assertTrue("10 s" in l[0].text && l[0].text.startsWith("Réseau local"), l[0].text)
        val g = r.on(Event.Tick(11_000)).run.lines()[2]
        assertTrue("(50 s au plus)" in g.text && WdCode.networkName(code) in g.text, g.text)
        val b = start(facts(wifiOn = false)).run.lines()[3]
        assertTrue("20 s" in b.text && b.text.startsWith("Bluetooth appairé"), b.text)
        assertEquals("Recherche de la TV…", r.headline().take(19))
    }

    @Test fun theHeadlineSaysTheTvThatWasFoundAndTheFailureOtherwise() {
        assertEquals("TV trouvée : CastBridge TV salon", start().run.on(Event.Reached(lan())).run.headline())
        val failed = start(facts(wifiOn = false, bt = Bt.OFF)).run
        assertEquals((failed.phase as Phase.Failed).message, failed.headline())
        assertEquals("Saisissez le code affiché sur la TV.", Run(facts(), emptyList(), -1, Phase.Idle).headline())
    }

    // ------------------------------------------------------------------ l'échec final : une cause par voie, en français

    @Test fun theFinalFailureNamesEveryRouteAndItsOwnCause() {
        var s = start().run.on(Event.Observed(Route.LAN, Cause(Cause.Kind.CODE_REFUSED, "CastBridge TV salon"))).run.on(Event.Tick(11_000))
        s = s.run.on(Event.Tick(groupEnd))
        s = s.run.on(Event.Tick(bluetoothEnd))
        val m = assertIs<Phase.Failed>(s.run.phase).message
        assertTrue(m.startsWith("La TV n'a pas pu être jointe avec ce code."), m)
        assertTrue("Réseau local : La TV « CastBridge TV salon » a refusé ce code" in m, m)
        assertTrue("Réseau de la TV (Wi-Fi Direct) : " in m && WdCode.networkName(code) in m, m)
        assertTrue("Bluetooth appairé : " in m, m)
        assertTrue("Bluetooth sans appairage : Le Bluetooth du téléphone est éteint." in m, "la voie sans appairage, ici éteinte, dit sa cause : $m")
        assertTrue(m.lines().last().startsWith("Relisez les 6 chiffres affichés sur la TV"), "le conseil final : relire le code : $m")
        assertTrue(m.lines().count { it.startsWith("• ") } == 4, m)
    }

    @Test fun theFinalAdviceFollowsTheCauses() {
        fun advice(cause: Cause?): String {
            var s = start()
            if (cause != null) s = s.run.on(Event.Observed(Route.LAN, cause))
            s = s.run.on(Event.Tick(11_000)); s = s.run.on(Event.Tick(groupEnd)); s = s.run.on(Event.Tick(bluetoothEnd))
            return (s.run.phase as Phase.Failed).message.lines().last()
        }
        assertTrue(advice(null).startsWith("Vérifiez que la TV est allumée et affiche son écran d'activation de CastBridge-TV"), advice(null))
        assertTrue(advice(Cause(Cause.Kind.LOCKED_OUT, "30")).startsWith("Attendez quelques minutes"))
        assertTrue(advice(Cause(Cause.Kind.CLOSED)).startsWith("Attendez quelques minutes"))
        assertTrue(advice(Cause(Cause.Kind.TERMS)).startsWith("Acceptez les conditions d'usage sur la TV"))
        assertTrue(advice(Cause(Cause.Kind.CODE_REFUSED, "TV")).startsWith("Relisez les 6 chiffres"))
    }

    // ------------------------------------------------------------------ la liaison au groupe tombe

    @Test fun theGroupLinkDroppingWhileConnectedIsSaidAndNeverSilent() {
        val connected = start().run.on(Event.Tick(11_000)).run.on(Event.Reached(group())).run
        val s = connected.on(Event.Lost(Route.GROUP, 40_000))
        val f = assertIs<Phase.Failed>(s.run.phase)
        assertTrue("interrompue" in f.message && "Réessayer" in f.message, f.message)
        assertEquals(listOf<Effect>(Effect.Abort(Route.GROUP)), s.effects)
        assertFalse(code in f.message)
        // another route's loss, or a loss before anything was joined, changes nothing for a connected run
        assertEquals(connected, connected.on(Event.Lost(Route.LAN, 40_000)).run)
        assertEquals(connected, connected.on(Event.Lost(Route.BLUETOOTH, 40_000)).run)
        val viaLan = start().run.on(Event.Reached(lan())).run
        assertEquals(viaLan, viaLan.on(Event.Lost(Route.GROUP, 3_000)).run, "connecté par le réseau local : la perte du groupe ne le concerne pas")
    }

    @Test fun theGroupDroppingWhileTryingFailsThatRouteAtOnce() {
        val trying = start().run.on(Event.Tick(11_000)).run
        val s = trying.on(Event.Lost(Route.GROUP, 12_000))
        assertEquals(Phase.Trying(Route.BLUETOOTH, 12_000, 20_000), s.run.phase)
        assertEquals(Cause.Kind.NOT_JOINED, s.run.causes.getValue(Route.GROUP).kind)
        assertEquals(trying, trying.on(Event.Lost(Route.BLUETOOTH, 12_000)).run)
    }

    @Test fun eachCauseHasItsOwnFrenchSentenceAndNoneIsEmpty() {
        val seen = HashSet<String>()
        for (k in Cause.Kind.values()) for (route in Route.values()) {
            val t = Cause(k, if (k == Cause.Kind.LOCKED_OUT) "37" else "CastBridge TV salon").text(route, WdCode.networkName(code))
            assertTrue(t.isNotBlank() && t.first().isUpperCase() && t.endsWith("."), "$k/$route : « $t »")
            assertFalse(code in t)
            seen += t
        }
        assertTrue(seen.size >= Cause.Kind.values().size, "une phrase par cause")
        assertTrue("37 s" in Cause(Cause.Kind.LOCKED_OUT, "37").text(Route.LAN, "x"))
    }

    @Test fun anOldTvIsToldToUpdateForTheNetworklessActivation() {
        val update = ActivationRoutePlan.UPDATE_LINE
        assertEquals("Mettez la TV à jour pour l'activation sans réseau.", update)
        assertTrue(update in Cause(Cause.Kind.NOT_JOINED).text(Route.GROUP, WdCode.networkName(code)), "groupe introuvable : la TV n'est peut-être pas à jour")
        val old = Found(Route.LAN, "CastBridge TV salon", "http://192.168.1.20:8765", null, note = Cause(Cause.Kind.NEEDS_UPDATE))
        val r = start().run.on(Event.Reached(old)).run
        assertEquals(update, r.hint(), "TV 0.14.43 jointe par le réseau local : voies (a) et (c) + la ligne de mise à jour")
        assertNull(start().run.on(Event.Reached(lan())).run.hint())
        assertNull(start().run.hint())
    }

    @Test fun cancellingStopsTheCurrentRouteAndLeavesNothingRunning() {
        val s = start().run.on(Event.Cancel)
        assertEquals(Phase.Cancelled, s.run.phase)
        assertEquals(listOf<Effect>(Effect.Abort(Route.LAN)), s.effects)
        assertEquals(s.run, s.run.on(Event.Tick(99_999)).run)
        assertTrue(s.run.on(Event.Cancel).effects.isEmpty())
        val c = start().run.on(Event.Reached(lan())).run.on(Event.Cancel)
        assertEquals(Phase.Cancelled, c.run.phase)
        assertTrue(c.effects.isEmpty(), "connecté : rien à arrêter ici, l'exécutant libère la liaison")
    }

    // ------------------------------------------------------------------ ACT-NF2 : jamais le code dans un texte

    @Test fun theCodeNeverAppearsInAnyTextOrInAnyStateDump() {
        val runs = mutableListOf<Run>()
        var s = start(facts(api = 28)); runs += s.run
        s = s.run.on(Event.Observed(Route.LAN, Cause(Cause.Kind.CODE_REFUSED, "TV"))); runs += s.run
        s = s.run.on(Event.Tick(11_000)); runs += s.run
        s = s.run.on(Event.UserJoined(12_000)); runs += s.run
        s = s.run.on(Event.Tick(12_000 + ActivationRoutePlan.GROUP_MS)); runs += s.run
        s = s.run.on(Event.Tick(12_000 + ActivationRoutePlan.GROUP_MS + ActivationRoutePlan.BT_MS)); runs += s.run
        runs += start().run.on(Event.Reached(lan())).run
        runs += start().run.on(Event.Tick(11_000)).run.on(Event.Tick(groupEnd)).run               // le réseau de la TV introuvable (Bluetooth en cours)
        runs += runs.last().on(Event.Tick(bluetoothEnd)).run                                       // … puis l'échec final
        for (r in runs) for (t in allTexts(r)) assertFalse(code in t, "le code ne doit figurer dans aucun texte : $t")
        assertFalse(code in Facts(code, 34, true, true, Bt.PAIRED).toString())
        assertFalse(WdCode.passphrase(code) in Phase.ManualJoin(WdCode.networkName(code), WdCode.passphrase(code)).toString(), "le mot de passe dérivé ne va pas non plus dans un journal")
    }

    @Test fun theFactsRefuseAMalformedCode() {
        for (bad in listOf("", "12345", "1234567", "12345a", "abcdef", " 48291")) assertFailsWith<IllegalArgumentException>(bad) { Facts(bad, 34, true, true, Bt.PAIRED) }
    }

    // ------------------------------------------------------------------ messages de Bluetooth (les textes de TvBluetooth.with)

    @Test fun bluetoothFailureMessagesBecomeTypedCauses() {
        fun k(m: String?) = ActivationRoutePlan.btCause(m).kind
        assertEquals(Cause.Kind.BT_OFF, k("Bluetooth désactivé sur ce téléphone"))
        assertEquals(Cause.Kind.BT_NO_ADAPTER, k("Bluetooth indisponible"))
        assertEquals(Cause.Kind.BT_PAIRING, k("Appairage non terminé : validez le code sur la TV et sur le téléphone, puis réessayez"))
        assertEquals(Cause.Kind.BT_NO_CHANNEL, k("La TV est jointe mais n'annonce pas le canal d'activation (2 service(s) CastBridge). Ouvrez CastBridge-TV"))
        assertEquals(Cause.Kind.BT_NO_CHANNEL, k("Cette TV ne répond pas au canal d'activation (version trop ancienne ?)"))
        assertEquals(Cause.Kind.BT_NO_CHANNEL, k("Connexion impossible : ouvrez CastBridge-TV (version 0.14.2 ou plus) sur la TV, puis réessayez"))
        assertEquals(Cause.Kind.TIMEOUT, k(null))
        assertEquals(Cause.Kind.TIMEOUT, k("autre chose"))
        assertFalse("autre chose" in ActivationRoutePlan.btCause("autre chose").toString(), "un message inconnu n'est jamais recopié")
    }

    @Test fun aTvNameFromTheNetworkIsCleanedBeforeItIsShown() {
        assertEquals("CastBridge TV salon", ActivationRoutePlan.cleanName("  CastBridge   TV\u0000 salon \n"))
        assertEquals("TV", ActivationRoutePlan.cleanName("\u202ETV\u202C"), "les marques de sens d'écriture sont retirées")
        assertEquals("CastBridge-TV", ActivationRoutePlan.cleanName(""), "un nom vide devient le nom de l'application")
        assertEquals(40, ActivationRoutePlan.cleanName("x".repeat(500)).length)
    }

    // ------------------------------------------------------------------ « Réseau de la TV introuvable » : la TV attend peut-être un geste (act-tv-2, ActivationGroupPolicy)
    // Une TV déjà reliée à un Wi-Fi ne crée PAS le groupe d'activation d'emblée : son écran montre le code seul et la ligne « Le téléphone n'est pas sur ce Wi-Fi ? » (OK : réseau direct), qui le crée à la demande.

    private val notFoundLine = "Réseau de la TV introuvable. Si la TV affiche « Le téléphone n'est pas sur ce Wi-Fi ? », appuyez sur OK sur la TV, puis « Réessayer »."

    @Test fun whenTheLocalNetworkFoundNoTvTheExpiredGroupLineTellsToPressOkOnTheTvThenRetry() {
        val group = start().run.on(Event.Tick(11_000))                                    // réseau local : aucune TV en 10 s ; le groupe est tenté
        assertEquals(Phase.Trying(Route.GROUP, 11_000, 50_000), group.run.phase)
        assertEquals(group.run, group.run.on(Event.Tick(groupEnd - 1)).run, "49,999 s : le groupe est encore tenté")
        val next = group.run.on(Event.Tick(groupEnd))                                     // 50 s : le groupe expire
        assertEquals(Cause(Cause.Kind.GROUP_NOT_FOUND), next.run.causes.getValue(Route.GROUP))
        assertEquals(ActivationRoutePlan.Line(Route.GROUP, LineState.FAILED, notFoundLine), next.run.lines()[2], "la ligne de cette voie est exactement cette phrase")
        // le Bluetooth continue comme aujourd'hui : il commence tout de suite, borné à 20 s
        assertEquals(Phase.Trying(Route.BLUETOOTH, groupEnd, 20_000), next.run.phase)
        assertEquals(listOf(Effect.Abort(Route.GROUP), Effect.Try(Route.BLUETOOTH, 20_000)), next.effects)
        assertEquals(Cause.Kind.NOT_ANNOUNCED, next.run.causes.getValue(Route.LAN).kind)
    }

    @Test fun aPhoneOnNoWifiThatCannotJoinTheGroupGetsTheSameLine() {
        // le cas de P-77 : téléphone en données mobiles, la TV est sur sa box et n'a pas créé le groupe
        val s = start(facts(onWifi = false)).run.on(Event.Tick(1_000 + ActivationRoutePlan.GROUP_MS))
        assertEquals(Cause.Kind.NOT_ON_WIFI, s.run.causes.getValue(Route.LAN).kind)
        assertEquals(Cause.Kind.GROUP_NOT_FOUND, s.run.causes.getValue(Route.GROUP).kind)
        assertEquals(notFoundLine, s.run.lines()[2].text)
    }

    @Test fun theTimeoutThatAndroidReportsAtTheBoundIsTheSameAsTheOneOfTheClock() {
        // le délai de la demande de réseau (WifiNetworkSpecifier) est le même que celui du plan : Android rapporte « indisponible » vers la 20e seconde, parfois avant le tic du plan
        val trying = start().run.on(Event.Tick(11_000)).run
        assertEquals(1_000L, ActivationRoutePlan.GROUP_TIMEOUT_SLACK_MS)
        for (at in listOf(groupEnd - 1_000, groupEnd, groupEnd + 300)) {                                // 49 s (la marge), 50 s, 50,3 s
            val s = trying.on(Event.Failed(Route.GROUP, Cause(Cause.Kind.NOT_JOINED), at))
            assertEquals(Cause.Kind.GROUP_NOT_FOUND, s.run.causes.getValue(Route.GROUP).kind, "at=$at")
            assertEquals(Phase.Trying(Route.BLUETOOTH, at, 20_000), s.run.phase, "at=$at")
        }
        // un refus (l'usager décline la boîte d'Android) ou un échec tout de suite n'est PAS « introuvable » : l'ancienne phrase, qui dit d'accepter la connexion
        for (at in listOf(11_500L, 15_000L, groupEnd - 1_001)) {
            val s = trying.on(Event.Failed(Route.GROUP, Cause(Cause.Kind.NOT_JOINED), at))
            assertEquals(Cause.Kind.NOT_JOINED, s.run.causes.getValue(Route.GROUP).kind, "at=$at")
        }
        // une autre cause d'échec définitive reste ce qu'elle est
        for (k in listOf(Cause.Kind.BUSY, Cause.Kind.GROUP_PERMISSION, Cause.Kind.CODE_REFUSED)) {
            assertEquals(k, trying.on(Event.Failed(Route.GROUP, Cause(k), groupEnd)).run.causes.getValue(Route.GROUP).kind)
        }
    }

    @Test fun whenATvAnsweredOnTheLocalNetworkTheGroupKeepsItsOldSentenceAndNoRetryIsOffered() {
        // la TV est là (elle a refusé le code, attend les conditions…) : le réseau de la TV n'est pas « introuvable », le conseil est celui de la cause
        for (seen in listOf(Cause(Cause.Kind.CODE_REFUSED, "CastBridge TV salon"), Cause(Cause.Kind.LOCKED_OUT, "30"), Cause(Cause.Kind.TERMS), Cause(Cause.Kind.CLOSED), Cause(Cause.Kind.UNREADABLE), Cause(Cause.Kind.NEEDS_UPDATE))) {
            val s = start().run.on(Event.Observed(Route.LAN, seen)).run.on(Event.Tick(11_000)).run.on(Event.Tick(groupEnd))
            assertEquals(seen.kind, s.run.causes.getValue(Route.LAN).kind)
            assertEquals(Cause.Kind.NOT_JOINED, s.run.causes.getValue(Route.GROUP).kind, "${seen.kind}")
            assertFalse(s.run.retryOffered(), "${seen.kind}")
            assertFalse(notFoundLine in s.run.lines().map { it.text })
        }
    }

    @Test fun aGroupThatWasJoinedButWhoseTvIsSilentStaysSilentNotNotFound() {
        val s = start().run.on(Event.Tick(11_000)).run.on(Event.Observed(Route.GROUP, Cause(Cause.Kind.TV_SILENT))).run.on(Event.Tick(groupEnd))
        assertEquals(Cause.Kind.TV_SILENT, s.run.causes.getValue(Route.GROUP).kind)
        assertFalse(s.run.retryOffered())
    }

    @Test fun theGroupJoinedByHandBeforeAndroid10KeepsItsOldSentence() {
        val s = start(facts(api = 28)).run.on(Event.Tick(11_000)).run.on(Event.UserJoined(12_000)).run.on(Event.Tick(12_000 + ActivationRoutePlan.GROUP_MS))
        assertEquals(Cause.Kind.NOT_JOINED, s.run.causes.getValue(Route.GROUP).kind)
        assertFalse(s.run.retryOffered())
    }

    @Test fun theGroupDroppingWhileTryingStaysNotJoinedAsBefore() {
        val s = start().run.on(Event.Tick(11_000)).run.on(Event.Lost(Route.GROUP, 31_000))
        assertEquals(Cause.Kind.NOT_JOINED, s.run.causes.getValue(Route.GROUP).kind)
    }

    @Test fun retryIsOfferedAsSoonAsTheNetworkIsNotFoundWithoutWaitingForBluetooth() {
        val first = start().run
        assertFalse(first.retryOffered(), "le réseau local est tenté")
        val group = first.on(Event.Tick(11_000)).run
        assertFalse(group.retryOffered(), "le groupe est encore tenté : rien à relancer")
        val inBluetooth = group.on(Event.Tick(groupEnd)).run
        assertIs<Phase.Trying>(inBluetooth.phase)
        assertTrue(inBluetooth.retryOffered(), "le Bluetooth est en cours et « Réessayer » est déjà là")
        val ended = inBluetooth.on(Event.Tick(bluetoothEnd)).run
        assertIs<Phase.Failed>(ended.phase)
        assertTrue(ended.retryOffered())
        // la TV est trouvée (par le Bluetooth) ou la recherche annulée : plus rien à relancer
        assertFalse(inBluetooth.on(Event.Reached(bt())).run.retryOffered())
        assertFalse(inBluetooth.on(Event.Cancel).run.retryOffered())
        // l'ancienne phrase n'en propose pas
        assertFalse(start().run.on(Event.Tick(11_000)).run.on(Event.Lost(Route.GROUP, 12_000)).run.retryOffered())
    }

    @Test fun retryingIsOneNewPlanPerGestureNeverALoop() {
        // toute la chronologie, sans geste : le réseau local, le groupe et le Bluetooth ne sont lancés qu'UNE fois, quel que soit le temps qui passe
        var s = start(facts(ble = Ble.SEARCH))
        val effects = ArrayList(s.effects)
        var t = 1_000L
        while (t < 900_000) { t += 500; s = s.run.on(Event.Tick(t)); effects += s.effects }
        assertIs<Phase.Failed>(s.run.phase)
        for (r in Route.values()) assertEquals(1, effects.count { it is Effect.Try && it.route == r }, "${r.label} : une seule fois")
        // « Réessayer » est un geste : un nouveau plan, réseau local puis groupe, qui ne se souvient de rien (l'expiration suivante est un nouveau geste)
        val again = ActivationRoutePlan.start(facts(ble = Ble.SEARCH), 1_000_000)
        assertEquals(listOf<Effect>(Effect.Try(Route.LAN, 10_000)), again.effects)
        assertTrue(again.run.causes.isEmpty() && !again.run.retryOffered())
    }

    @Test fun theFinalFailureCarriesTheLineWithoutTheDoubledRouteName() {
        val end = start(facts(bt = Bt.OFF)).run.on(Event.Tick(11_000)).run.on(Event.Tick(groupEnd))
        val m = assertIs<Phase.Failed>(end.run.phase).message
        assertTrue("• $notFoundLine" in m.lines(), m)
        assertFalse("Réseau de la TV (Wi-Fi Direct) : Réseau de la TV introuvable" in m, m)
        assertEquals(4, m.lines().count { it.startsWith("• ") }, m)
        assertTrue(m.lines().last().startsWith("Vérifiez que la TV est allumée"), "le conseil final ne change pas : $m")
        assertTrue(end.run.retryOffered())
    }

    @Test fun theNotFoundSentenceIsTheSameWhateverTheRouteOrTheNetworkName() {
        for (route in Route.values()) assertEquals(notFoundLine, Cause(Cause.Kind.GROUP_NOT_FOUND).text(route, WdCode.networkName(code)))
        assertFalse(code in notFoundLine)
    }
}
