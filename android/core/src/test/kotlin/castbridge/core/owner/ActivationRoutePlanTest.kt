package castbridge.core.owner

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
import castbridge.core.tv.WdCode
import kotlin.test.*

/**
 * « Code affiché sur la TV » : l'ordre des voies (réseau local, groupe Wi-Fi Direct dérivé du code, Bluetooth), leurs bornes (10 s, 20 s, 20 s), la ligne d'état de chaque étape,
 * la cause par voie à l'échec, Android 9 et moins (jonction à la main). Temps simulé : aucun fil, aucune horloge.
 * (docs/coordination/DESIGN-ACTIVATION-SIMPLE-2026-10-07.md, ACT-F2, ACT-F3, ACT-NF2, ACT-NF4)
 */
class ActivationRoutePlanTest {
    private val code = "482913"
    private fun facts(api: Int = 34, wifiOn: Boolean = true, onWifi: Boolean = true, bt: Bt = Bt.PAIRED) = Facts(code, api, wifiOn, onWifi, bt)
    private fun lan(name: String = "CastBridge TV salon") = Found(Route.LAN, name, "http://192.168.1.20:8765", null)
    private fun group() = Found(Route.GROUP, WdCode.networkName(code), "http://192.168.49.1:8765", null)
    private fun bt() = Found(Route.BLUETOOTH, "SMART_TV", null, null, btAddress = "AA:BB:CC:DD:EE:FF")

    private fun start(f: Facts = facts(), now: Long = 1_000) = ActivationRoutePlan.start(f, now)
    private fun Run.on(e: Event) = ActivationRoutePlan.reduce(this, e)
    private fun Run.trying() = (phase as Phase.Trying)
    private fun allTexts(r: Run) = listOf(r.toString(), r.facts.toString(), r.headline()) + r.lines().map { it.text } + listOfNotNull(r.hint(), (r.phase as? Phase.Failed)?.message)

    // ------------------------------------------------------------------ l'ordre et les bornes

    @Test fun theBoundsAreTenTwentyAndTwentySeconds() {
        assertEquals(10_000L, ActivationRoutePlan.LAN_MS); assertEquals(20_000L, ActivationRoutePlan.GROUP_MS); assertEquals(20_000L, ActivationRoutePlan.BT_MS)
        assertEquals(listOf(10_000L, 20_000L, 20_000L), Route.values().map(ActivationRoutePlan::boundMs))
        assertEquals(listOf(Route.LAN, Route.GROUP, Route.BLUETOOTH), Route.values().toList(), "l'ordre : réseau local, groupe, Bluetooth")
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
        assertEquals(Phase.Trying(Route.GROUP, 11_000, 20_000), next.run.phase)
        assertEquals(listOf(Effect.Abort(Route.LAN), Effect.Try(Route.GROUP, 20_000)), next.effects, "l'étape précédente est arrêtée, la suivante commence")
        assertEquals(Cause.Kind.NOT_ANNOUNCED, next.run.causes.getValue(Route.LAN).kind)
    }

    @Test fun theGroupGivesUpAfterTwentySecondsThenBluetoothAfterTwentyMoreThenItFails() {
        var s = start().run.on(Event.Tick(11_000))
        assertEquals(s.run, s.run.on(Event.Tick(30_999)).run)
        s = s.run.on(Event.Tick(31_000))
        assertEquals(Phase.Trying(Route.BLUETOOTH, 31_000, 20_000), s.run.phase)
        assertEquals(listOf(Effect.Abort(Route.GROUP), Effect.Try(Route.BLUETOOTH, 20_000)), s.effects)
        assertEquals(s.run, s.run.on(Event.Tick(50_999)).run)
        val end = s.run.on(Event.Tick(51_000))
        assertIs<Phase.Failed>(end.run.phase)
        assertEquals(listOf<Effect>(Effect.Abort(Route.BLUETOOTH)), end.effects)
    }

    @Test fun aTvFoundOnTheLocalNetworkEndsTheSearchWithoutTryingTheOthers() {
        val s = start().run.on(Event.Reached(lan()))
        assertEquals(Phase.Connected(lan()), s.run.phase)
        assertTrue(s.effects.isEmpty())
        assertEquals(s.run, s.run.on(Event.Tick(999_999)).run, "connecté : plus de borne")
        assertEquals(listOf(LineState.DONE, LineState.UNUSED, LineState.UNUSED), s.run.lines().map { it.state })
    }

    @Test fun theGroupRouteFindsTheTvWhenThereIsNoCommonNetwork() {
        val s = start().run.on(Event.Tick(11_000)).run.on(Event.Reached(group()))
        assertEquals(Phase.Connected(group()), s.run.phase)
        assertEquals(listOf(LineState.FAILED, LineState.DONE, LineState.UNUSED), s.run.lines().map { it.state })
    }

    @Test fun bluetoothIsTheLastResortAndConnects() {
        val s = start(facts(wifiOn = false)).run.on(Event.Reached(bt()))
        assertEquals(Phase.Connected(bt()), s.run.phase)
        assertEquals("SMART_TV", (s.run.phase as Phase.Connected).found.name)
    }

    @Test fun aDefinitiveFailureMovesOnAtOnceWithoutWaitingForTheBound() {
        val s = start().run.on(Event.Failed(Route.LAN, Cause(Cause.Kind.CLOSED), 2_500))
        assertEquals(Phase.Trying(Route.GROUP, 2_500, 20_000), s.run.phase)
        assertEquals(Cause.Kind.CLOSED, s.run.causes.getValue(Route.LAN).kind)
        assertEquals(listOf(Effect.Abort(Route.LAN), Effect.Try(Route.GROUP, 20_000)), s.effects)
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
        assertEquals(listOf(LineState.SKIPPED, LineState.SKIPPED, LineState.ACTIVE), s.run.lines().map { it.state })
    }

    @Test fun notBeingOnAnyWifiSkipsOnlyTheLocalNetwork() {
        val s = start(facts(onWifi = false))
        assertEquals(Phase.Trying(Route.GROUP, 1_000, 20_000), s.run.phase)
        assertEquals(Cause.Kind.NOT_ON_WIFI, s.run.causes.getValue(Route.LAN).kind)
    }

    @Test fun bluetoothThatCannotWorkIsSkippedWithItsCauseAndNeverWaitedFor() {
        for ((bt, kind) in listOf(Bt.OFF to Cause.Kind.BT_OFF, Bt.NO_PERMISSION to Cause.Kind.BT_PERMISSION, Bt.NO_ADAPTER to Cause.Kind.BT_NO_ADAPTER)) {
            val end = start(facts(bt = bt)).run.on(Event.Tick(11_000)).run.on(Event.Tick(31_000))
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
        val text = s.run.lines()[1].text
        assertTrue(WdCode.networkName(code) in text && WdCode.passphrase(code) in text, "le mot de passe dérivé est affiché en clair sur le téléphone : $text")
        assertTrue(text.startsWith("Réseau de la TV") && "Connectez le téléphone au Wi-Fi « ${WdCode.networkName(code)} »" in text, text)
        assertEquals(LineState.ASKING, s.run.lines()[1].state)
        assertEquals(s.run, s.run.on(Event.Tick(500_000)).run, "attente de l'usager : aucune borne qui tourne")
    }

    @Test fun theManualJoinThenProbesTheGroupFor20Seconds() {
        val waiting = start(facts(api = 26)).run.on(Event.Tick(11_000)).run
        val go = waiting.on(Event.UserJoined(40_000))
        assertEquals(Phase.Trying(Route.GROUP, 40_000, 20_000), go.run.phase)
        assertEquals(listOf<Effect>(Effect.Try(Route.GROUP, 20_000, manual = true)), go.effects)
        val late = go.run.on(Event.Tick(60_000))
        assertEquals(Phase.Trying(Route.BLUETOOTH, 60_000, 20_000), late.run.phase)
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
            assertEquals(Phase.Trying(Route.GROUP, 11_000, 20_000), s.run.phase, "api $api")
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
        assertEquals(s.run.lines()[2].state, LineState.FAILED)
    }

    @Test fun aPairingNoticeOnAnotherRouteChangesNothing() {
        val r = start().run
        assertEquals(r, r.on(Event.Pairing(2_000)).run)
    }

    // ------------------------------------------------------------------ les lignes d'état

    @Test fun theActiveLineSaysWhatIsHappeningAndHowLongItMayTake() {
        val r = start().run
        val l = r.lines()
        assertEquals(listOf(LineState.ACTIVE, LineState.WAITING, LineState.WAITING), l.map { it.state })
        assertTrue("10 s" in l[0].text && l[0].text.startsWith("Réseau local"), l[0].text)
        val g = r.on(Event.Tick(11_000)).run.lines()[1]
        assertTrue("20 s" in g.text && WdCode.networkName(code) in g.text, g.text)
        val b = start(facts(wifiOn = false)).run.lines()[2]
        assertTrue("20 s" in b.text && b.text.startsWith("Bluetooth"), b.text)
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
        s = s.run.on(Event.Tick(31_000))
        s = s.run.on(Event.Tick(51_000))
        val m = assertIs<Phase.Failed>(s.run.phase).message
        assertTrue(m.startsWith("La TV n'a pas pu être jointe avec ce code."), m)
        assertTrue("Réseau local : La TV « CastBridge TV salon » a refusé ce code" in m, m)
        assertTrue("Réseau de la TV (Wi-Fi Direct) : " in m && WdCode.networkName(code) in m, m)
        assertTrue("Bluetooth : " in m, m)
        assertTrue(m.lines().last().startsWith("Relisez les 6 chiffres affichés sur la TV"), "le conseil final : relire le code : $m")
        assertTrue(m.lines().count { it.startsWith("• ") } == 3, m)
    }

    @Test fun theFinalAdviceFollowsTheCauses() {
        fun advice(cause: Cause?): String {
            var s = start()
            if (cause != null) s = s.run.on(Event.Observed(Route.LAN, cause))
            s = s.run.on(Event.Tick(11_000)); s = s.run.on(Event.Tick(31_000)); s = s.run.on(Event.Tick(51_000))
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
        s = s.run.on(Event.Tick(40_000)); runs += s.run
        s = s.run.on(Event.Tick(70_000)); runs += s.run
        runs += start().run.on(Event.Reached(lan())).run
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
}
