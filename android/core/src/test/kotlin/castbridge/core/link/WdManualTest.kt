package castbridge.core.link

import castbridge.core.link.WdManualView.Action
import castbridge.core.link.WdManualView.Cause
import castbridge.core.link.WdManualView.Facts
import castbridge.core.link.WdManualView.Start
import castbridge.core.link.WdManualView.View
import castbridge.core.tv.WifiDirect
import castbridge.core.ux.SignalLevel
import kotlin.test.*

/**
 * Le bouton « Wi-Fi Direct » (docs/agent-reports/wd-manual-button.md) : la table de décision (chaque cause, un réseau commun, TV d'essai, profil enfant),
 * la ligne d'état, la session manuelle (10 minutes hors écran, horloge simulée) et le contournement du seuil et de la pause, jamais du code.
 */
class WdManualTest {
    private val ok = Facts(btBonded = true, btLinked = true, credentialValid = true, tvOffersWd = true, api = 34, permission = WdPermission.GRANTED)

    // ------------------------------------------------------------------ la table

    @Test fun everythingInPlaceShowsTheButton() {
        assertEquals(View.Ready(confirmLan = false, askPermission = false), WdManualView.of(ok))
        assertEquals(Start.Go, WdManualView.start(ok, lanConfirmed = false))
    }

    @Test fun noTvShowsNothing() { assertEquals(View.Hidden, WdManualView.of(ok.copy(hasTv = false))) }

    @Test fun notBondedSaysPairFirst() {
        val v = WdManualView.of(ok.copy(btBonded = false, btLinked = false)) as View.Disabled
        assertEquals(Cause.NO_BOND, v.cause); assertEquals("Associez d'abord la TV en Bluetooth", v.text); assertEquals(Action.PAIR_BLUETOOTH, v.action)
    }

    @Test fun bondedButLinkNotAnsweringIsNotTheSameCause() {
        val v = WdManualView.of(ok.copy(btLinked = false)) as View.Disabled
        assertEquals(Cause.BT_NOT_LINKED, v.cause); assertEquals(Action.RETRY_LINK, v.action)
    }

    @Test fun noCredentialSaysEnterTheCode() {
        val v = WdManualView.of(ok.copy(credentialValid = false)) as View.Disabled
        assertEquals(Cause.NO_CODE, v.cause); assertEquals("Entrez le code de la TV", v.text); assertEquals(Action.ENTER_CODE, v.action)
    }

    @Test fun tvWithoutTheCapabilityOrThatDoesNotSayIt() {
        for (cap in listOf(false, null)) {
            val v = WdManualView.of(ok.copy(tvOffersWd = cap)) as View.Disabled
            assertEquals(Cause.NO_WD, v.cause); assertEquals("Cette TV ne propose pas Wi-Fi Direct", v.text); assertEquals(Action.NONE, v.action)
        }
    }

    @Test fun trialTvIsClosedWhateverElse() {
        assertEquals(Cause.TRIAL, (WdManualView.of(ok.copy(trialTv = true)) as View.Disabled).cause)
        assertEquals(Cause.TRIAL, (WdManualView.of(ok.copy(tvWdError = WifiDirect.Err.TRIAL)) as View.Disabled).cause)
    }

    @Test fun oldPhoneCannotJoin() {
        assertEquals(Cause.PHONE_TOO_OLD, (WdManualView.of(ok.copy(api = 28)) as View.Disabled).cause)
        assertIs<View.Ready>(WdManualView.of(ok.copy(api = 29, permission = WdPermission.NOT_NEEDED)))
    }

    @Test fun permissionIsAskedJustInTimeOnceThenExplained() {
        val first = WdManualView.of(ok.copy(permission = WdPermission.NOT_ASKED)) as View.Ready
        assertTrue(first.askPermission)
        assertEquals(Start.AskPermission, WdManualView.start(ok.copy(permission = WdPermission.NOT_ASKED), false))
        val denied = WdManualView.of(ok.copy(permission = WdPermission.DENIED)) as View.Disabled
        assertEquals(Cause.PERMISSION_DENIED, denied.cause); assertEquals("Autorisez les appareils à proximité", denied.text); assertEquals(Action.GRANT_PERMISSION, denied.action)
        assertEquals(Cause.PERMISSION_PENDING, (WdManualView.of(ok.copy(permission = WdPermission.ASKING)) as View.Disabled).cause)
        assertIs<View.Ready>(WdManualView.of(ok.copy(api = 31, permission = WdPermission.NOT_NEEDED)))
    }

    @Test fun phoneWifiOffIsExplainedWithOneAction() {
        val v = WdManualView.of(ok.copy(phoneWifiOn = false)) as View.Disabled
        assertEquals(Cause.PHONE_WIFI_OFF, v.cause); assertEquals(Action.OPEN_WIFI, v.action)
    }

    @Test fun causesHaveAnOrderAndOnlyOneIsShown() {
        val worst = Facts(btBonded = false, btLinked = false, credentialValid = false, tvOffersWd = false, api = 20, permission = WdPermission.DENIED, phoneWifiOn = false, trialTv = true)
        assertEquals(Cause.NO_BOND, (WdManualView.of(worst) as View.Disabled).cause)
        assertEquals(Cause.NO_CODE, (WdManualView.of(worst.copy(btBonded = true, btLinked = true)) as View.Disabled).cause)
    }

    @Test fun aWorkingCommonNetworkAsksBeforeBeingReplaced() {
        val f = ok.copy(lanAlive = true)
        assertEquals(View.Ready(confirmLan = true, askPermission = false), WdManualView.of(f))
        assertEquals(Start.ConfirmLan, WdManualView.start(f, lanConfirmed = false))
        assertEquals(Start.Go, WdManualView.start(f, lanConfirmed = true))
        assertEquals("Un réseau commun fonctionne déjà : utiliser quand même Wi-Fi Direct ?", WdManualView.LAN_CONFIRM)
    }

    @Test fun childProfileOnTheTvDoesNotBlockTheLinkButton() {
        assertEquals(View.Ready(confirmLan = false, askPermission = false), WdManualView.of(ok.copy(tvChildProfile = true)))
    }

    @Test fun aLinkCauseNeverClaimsTheWdCause() {
        // a stale « wifi_off » of an earlier automatic try must not lock the button: the user retries by hand
        assertIs<View.Ready>(WdManualView.of(ok.copy(tvWdError = WifiDirect.Err.WIFI_OFF)))
    }

    // ------------------------------------------------------------------ la ligne d'état

    @Test fun stateLineFollowsTheSignalColours() {
        val working = listOf(WdClient.State.Requesting(1), WdClient.State.Joining(1, 20_000, null, 8765), WdClient.State.Probing("http://192.168.49.1:8765", 1, 0))
        for (s in working) {
            val v = WdManualView.of(ok.copy(state = s, manual = true)) as View.Working
            assertEquals(SignalLevel.ORANGE, v.line.level); assertEquals("Mise en place…", v.line.text)
        }
        val up = WdManualView.of(ok.copy(state = WdClient.State.Up("http://192.168.49.1:8765", 1, 1), manual = true)) as View.Active
        assertEquals(SignalLevel.GREEN, up.line.level); assertEquals("Par Wi-Fi Direct", up.line.text); assertEquals(WdManualView.MANUAL_KEEP, up.detail)
        assertEquals("Wi-Fi Direct actif · Arrêter", WdManualView.ACTIVE_TITLE)
        val auto = WdManualView.of(ok.copy(state = WdClient.State.Up("http://192.168.49.1:8765", 1, 1), manual = false)) as View.Active
        assertTrue(auto.detail!!.contains("30 s"))
    }

    @Test fun aFailureIsRedWithItsCauseAndTheButtonStaysUsable() {
        val v = WdManualView.of(ok.copy(state = WdClient.State.Failed(WdClient.Fail.TV_NO_GROUP, 5, WifiDirect.Err.WIFI_OFF))) as View.Ready
        assertEquals(SignalLevel.RED, v.last!!.level); assertEquals("Le Wi-Fi de la TV est éteint.", v.last!!.text)
        assertEquals(Start.Go, WdManualView.start(ok.copy(state = WdClient.State.Failed(WdClient.Fail.LOST, 5)), false))
    }

    @Test fun anActiveSessionIsNeverStartedTwice() {
        assertEquals(Start.AlreadyUp, WdManualView.start(ok.copy(state = WdClient.State.Up("b", 1, 1)), true))
        assertEquals(Start.AlreadyUp, WdManualView.start(ok.copy(state = WdClient.State.Requesting(1)), true))
    }

    // ------------------------------------------------------------------ contourne le seuil et la pause, jamais le code

    @Test fun manualStartBypassesTheThresholdAndTheBackoffButAutomaticDoesNot() {
        val now = 1_000_000L
        val auto = BulkRoute.Facts(bytes = 1L shl 20, lanAlive = false, btConnected = true, api = 34, permission = WdPermission.GRANTED, now = now)
        assertEquals(BulkRoute.Decision.UseBt(BulkRoute.Why.SMALL), BulkRoute.decide(auto), "automatique : 1 Mo reste en Bluetooth")
        assertEquals(BulkRoute.Decision.UseBt(BulkRoute.Why.BACKOFF), BulkRoute.decide(auto.copy(bytes = 200L shl 20, backoffUntil = now + 60_000)), "automatique : pause de 10 min")
        assertEquals(BulkRoute.Decision.UseBt(BulkRoute.Why.SETTING_OFF), BulkRoute.decide(auto.copy(bytes = 200L shl 20, autoWifiDirect = false)))
        // le bouton : mêmes circonstances, il part (les Facts du bouton ne portent ni taille, ni pause, ni réglage automatique)
        assertEquals(Start.Go, WdManualView.start(ok, lanConfirmed = false))
    }

    @Test fun manualStartNeverBypassesTheCredentialTheLinkOrTheCapability() {
        assertEquals(Start.Refuse(Cause.NO_CODE), WdManualView.start(ok.copy(credentialValid = false), lanConfirmed = true))
        assertEquals(Start.Refuse(Cause.NO_BOND), WdManualView.start(ok.copy(btBonded = false), lanConfirmed = true))
        assertEquals(Start.Refuse(Cause.BT_NOT_LINKED), WdManualView.start(ok.copy(btLinked = false), lanConfirmed = true))
        assertEquals(Start.Refuse(Cause.NO_WD), WdManualView.start(ok.copy(tvOffersWd = false), lanConfirmed = true))
        assertEquals(Start.Refuse(Cause.TRIAL), WdManualView.start(ok.copy(trialTv = true), lanConfirmed = true))
    }

    // ------------------------------------------------------------------ la session manuelle : 10 minutes hors écran

    @Test fun manualSessionLivesTenMinutesAwayFromTheScreen() {
        var t = 0L
        var lease = WdManualLease().seen(t, onScreen = true)
        t += 3_600_000; lease = lease.seen(t, true)
        assertFalse(lease.expired(t), "à l'écran, sans limite")
        lease = lease.seen(t, false)                                   // l'usager quitte l'écran de la TV
        t += WdManualLease.AWAY_MS - 1; lease = lease.seen(t, false)
        assertFalse(lease.expired(t))
        t += 1; lease = lease.seen(t, false)
        assertTrue(lease.expired(t), "10 minutes exactes hors écran")
    }

    @Test fun comingBackRestartsTheTimerAndABusyCopyKeepsTheGroup() {
        var t = 0L
        var lease = WdManualLease().seen(t, false)
        t += 9 * 60_000; lease = lease.seen(t, true)                    // retour à 9 minutes
        assertNull(lease.awaySince)
        t += 5_000; lease = lease.seen(t, false)
        t += 9 * 60_000; lease = lease.seen(t, false)
        assertFalse(lease.expired(t), "9 minutes depuis le dernier départ")
        t += 61_000; lease = lease.seen(t, false)
        assertTrue(lease.expired(t))
        assertFalse(lease.expired(t, busy = true), "une copie en cours garde le groupe")
    }

    @Test fun automaticIdleReleaseIsUntouchedByTheManualButton() {
        // le chemin automatique : 30 s de file vide rendent le groupe (l'exécutant ne passe busy=true que pour une session manuelle)
        val up = WdClient.State.Up("http://192.168.49.1:8765", since = 0, lastUse = 0)
        val r = WdClient.reduce(up, WdClient.Event.Tick(WdClient.IDLE_RELEASE_MS, busy = false))
        assertEquals(WdClient.State.Off, r.state)
        val kept = WdClient.reduce(up, WdClient.Event.Tick(WdClient.IDLE_RELEASE_MS, busy = true))
        assertIs<WdClient.State.Up>(kept.state)
    }
}
