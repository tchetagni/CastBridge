package castbridge.core.connect

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * R-41 (I-2) : la TV écoute `NET_CAPABILITY_VALIDATED`. `onCapabilitiesChanged` part aussi à chaque variation de débit ou de signal : seul un CHANGEMENT du verdict d'ensemble réveille la
 * boucle réseau, sinon une sonde partirait à chaque variation du Wi-Fi.
 */
class ValidationWatchTest {
    @Test fun theFirstReportOnlySetsTheReference() {
        val w = ValidationWatch<String>()
        assertFalse(w.reported("wifi", false), "l'arrivée du réseau a déjà demandé une revérification")
        assertFalse(ValidationWatch<String>().reported("wifi", true), "même un premier rapport « validé »")
    }

    @Test fun aChangeOfTheVerdictWakesTheLoopAndNothingElseDoes() {
        val w = ValidationWatch<String>()
        w.reported("wifi", false)
        assertFalse(w.reported("wifi", false), "débit ou signal changé, même verdict : rien")
        assertTrue(w.reported("wifi", true), "le système valide le réseau (Internet de la box revenu) : on relit tout de suite")
        assertFalse(w.reported("wifi", true), "variation de débit : rien")
        assertTrue(w.reported("wifi", false), "le système ne valide plus (la box a perdu Internet) : on relit tout de suite")
    }

    @Test fun theVerdictIsAboutTheSetOfNetworksNotTheLastOneReported() {
        val w = ValidationWatch<String>()
        w.reported("wifi", true); w.reported("eth", false)
        assertFalse(w.reported("eth", false), "le câble n'est pas validé mais le Wi-Fi l'est : verdict d'ensemble inchangé")
        assertFalse(w.reported("wifi", true), "chaque rapport de l'un ne fait pas basculer le verdict à cause de l'autre")
        assertFalse(w.reported("eth", true), "deux réseaux validés : toujours « validé »")
        assertFalse(w.reported("wifi", false), "le câble reste validé")
        assertTrue(w.reported("eth", false), "plus aucun réseau validé : changement")
    }

    @Test fun aLostNetworkChangesTheVerdictOnlyWhenItWasTheLastValidatedOne() {
        val w = ValidationWatch<String>()
        w.reported("wifi", true); w.reported("eth", true)
        assertFalse(w.lost("eth"), "le Wi-Fi reste validé")
        assertTrue(w.lost("wifi"), "plus aucun réseau : le verdict passe à « non validé »")
        assertFalse(w.lost("wifi"), "déjà perdu : rien de plus")
        assertFalse(w.reported("wifi", false), "il revient, pas encore validé : même verdict (non validé)")
        assertTrue(w.reported("wifi", true))
    }
}
