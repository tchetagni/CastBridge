package castbridge.core.connect

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Consigne du coordinateur (hygiène R4) : la TV sondait `connectivitycheck.gstatic.com` toutes les 60 s (10 à 30 s hors ligne), y compris À TRAVERS le téléphone : ~1 Mo par jour
 * de données mobiles et un réveil radio chaque minute, contraire à la discrétion et à REL-F7. Règle : la vie du tuyau se déduit de ses trames PING/ACK ; aucune sonde par le
 * téléphone sauf demande explicite ou changement d'état ; cible le serveur du projet ; au moins 5 minutes entre deux sondes automatiques ; la jambe directe aussi à 5 minutes.
 */
class NetProbePlanTest {
    private val MIN = 60_000L

    // ------------------------------------------------------------------ jambe directe de la TV

    @Test fun theDirectLegIsProbedEveryFiveMinutesAndNotEveryMinute() {
        val p = NetProbePlan()
        var t = 1_000_000L
        assertTrue(p.directDue(t, manual = false), "la première fois : tout de suite")
        p.directDone(t)
        for (k in 1..4) { t += MIN; assertFalse(p.directDue(t, manual = false), "$k minute(s) après : pas de sonde") }
        t += MIN
        assertTrue(p.directDue(t, manual = false), "cinq minutes après")
    }

    @Test fun theSameCadenceHoldsWhileOffline() {
        // une sonde qui échoue ne rend pas la TV bavarde : les rechutes de 10-30 s d'avant sont finies
        val p = NetProbePlan()
        var t = 5_000_000L
        p.directDone(t)
        var probes = 0
        repeat(60) { t += 10_000; if (p.directDue(t, false)) { probes++; p.directDone(t) } }     // 10 minutes par pas de 10 s
        assertEquals(2, probes, "deux sondes en dix minutes")
    }

    @Test fun aNetworkEventProbesAtOnceThenReturnsToTheCadence() {
        val p = NetProbePlan(); var t = 1_000_000L
        p.directDone(t)
        t += 20_000
        assertFalse(p.directDue(t, false))
        p.markChanged()                                      // le réseau de la TV est apparu ou a disparu
        assertTrue(p.directDue(t, false))
        p.directDone(t)
        assertFalse(p.directDue(t + 1_000, false))
    }

    @Test fun aFailedRealCallOnTheOwnNetworkProbesAtOnceAndIsRemembered() {
        // R-41 (I-2) : un appel réel au serveur a échoué sur le réseau propre : la sonde est due au prochain tour (pas dans 5 minutes), et la date de l'échec est gardée
        val p = NetProbePlan(); var t = 1_000_000L
        p.directDone(t)
        t += 20_000
        assertFalse(p.directDue(t, false)); assertEquals(0L, p.directFailedAt())
        p.directFailureSeen(t)
        assertTrue(p.directDue(t, false))
        assertEquals(t, p.directFailedAt(), "la date reste connue après la sonde : elle sert à annuler la preuve d'un contact plus ancien")
        p.directDone(t + 1_000)
        assertFalse(p.directDue(t + 2_000, false), "une seule sonde par échec : la cadence reprend")
        assertEquals(t, p.directFailedAt())
    }

    @Test fun aManualTestAlwaysProbes() {
        val p = NetProbePlan(); p.directDone(1_000_000)
        assertTrue(p.directDue(1_000_001, manual = true))
        p.directDone(1_000_001)
        assertTrue(p.directDue(1_000_002, manual = true), "tant que l'utilisateur le demande")
    }

    // ------------------------------------------------------------------ jambe par le téléphone

    private fun NetProbePlan.relay(now: Long, attach: Long = 1, connected: Boolean = true, allowed: Boolean = true, manual: Boolean = false, demand: Boolean = false) =
        relayDue(now, allowed, connected, attach, manual, demand)

    @Test fun nothingGoesThroughThePhoneWhenThereIsNoPipe() {
        val p = NetProbePlan()
        assertFalse(p.relay(1_000_000, connected = false))
        assertFalse(p.relay(1_000_000, connected = false, manual = true), "même sur demande : pas de téléphone, pas de sonde")
    }

    @Test fun aNewPipeIsConfirmedOnceThenLeftAlone() {
        val p = NetProbePlan(); var t = 1_000_000L
        assertTrue(p.relay(t), "changement d'état : le téléphone vient de se brancher")
        p.relayDone(t, attachId = 1, ok = true)
        var probes = 0
        repeat(24 * 60) { t += MIN; if (p.relay(t)) probes++ }                  // 24 heures de tuyau ouvert, une minute par pas
        assertEquals(0, probes, "aucune sonde périodique par le téléphone : sa vie se déduit des PING")
    }

    @Test fun aNewAttachmentIsANewStateChange() {
        val p = NetProbePlan(); p.relayDone(1_000_000, 1, ok = true)
        assertFalse(p.relay(1_000_100, attach = 1))
        assertTrue(p.relay(1_000_100, attach = 2), "un autre téléphone, ou le même qui se reconnecte")
    }

    @Test fun aManualTestIsAnExplicitRequest() {
        val p = NetProbePlan(); p.relayDone(1_000_000, 1, ok = true)
        assertTrue(p.relay(1_000_100, manual = true))
    }

    @Test fun aFailedRealCallThroughThePipeAsksForAConfirmation() {
        val p = NetProbePlan(); p.relayDone(1_000_000, 1, ok = true)
        assertFalse(p.relay(1_000_100))
        p.relayFailureSeen()
        assertTrue(p.relay(1_000_200), "un appel réel a échoué à travers le tuyau : on vérifie, une fois")
        p.relayDone(1_000_200, 1, ok = true)
        assertFalse(p.relay(1_000_300), "la vérification a eu lieu : le drapeau est consommé")
    }

    @Test fun aFailedConfirmationIsRetriedSlowlyWithoutDemandAndFasterWhileAnOperationWaits() {
        val p = NetProbePlan(); var t = 1_000_000L
        p.relayDone(t, 1, ok = false)
        t += 29_000
        assertFalse(p.relay(t, demand = false)); assertFalse(p.relay(t, demand = true), "moins de 30 s : jamais")
        t += 2_000                                                              // 31 s
        assertTrue(p.relay(t, demand = true), "une opération attend (demande explicite) : 30 s")
        assertFalse(p.relay(t, demand = false), "sans opération qui attend : cinq minutes")
        t += 5 * MIN
        assertTrue(p.relay(t, demand = false))
    }

    @Test fun theConsentGatesAutomaticProbesButNotAnExplicitTest() {
        val p = NetProbePlan()
        assertFalse(p.relay(1_000_000, allowed = false), "rien ne part avant l'écran d'information (consentement)")
        assertTrue(p.relay(1_000_000, allowed = false, manual = true), "l'utilisateur lance le test lui-même")
    }

    @Test fun theFloorBetweenAutomaticProbesThroughThePhoneIsFiveMinutes() {
        val p = NetProbePlan(); var t = 1_000_000L
        p.relayDone(t, 1, ok = false)
        var probes = 0
        repeat(59) { t += 10_000; if (p.relay(t)) { probes++; p.relayDone(t, 1, ok = false) } }       // 9 min 50 s de tuyau sans Internet derrière
        assertEquals(1, probes, "au plus une sonde automatique par tranche de cinq minutes")
    }
}
