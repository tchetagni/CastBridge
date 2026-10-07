package castbridge.core.relay

import castbridge.core.connect.NetState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * R-47 (audit anti-régression 2026-10-07 b, I-13, CONFIRMÉ, moitié TV ; la moitié téléphone est R-33) : la garde de coût REL-F7 était percée côté TV. `phoneMetered` était global et
 * volatil : remis à `null` à chaque coupure (le téléphone se reconnecte sans renvoyer son état), `null` AUTORISAIT le fond, et le `RELAY_STATE` de n'importe quel téléphone l'écrasait. La
 * mise à jour de l'APK (~40 Mo) ou les paquets de questions passaient par les données mobiles, mangeaient les 5 Mo du jour, puis le jeu était refusé jusqu'à minuit.
 */
class TvBulkGateTest {
    private val a = "AA:AA:AA:AA:AA:AA"
    private val b = "BB:BB:BB:BB:BB:BB"
    private fun allowed(g: TvBulkGate, net: NetState = NetState.VIA_RELAY, game: Boolean = false, phone: String? = a, pipe: Int = 1) = g.bulkAllowed(net, game, phone, pipe)

    @Test fun aPhoneThatHasSaidNothingIsBilledSoNoBackgroundDownloadLeaves() {
        val g = TvBulkGate()
        assertFalse(allowed(g), "R-47 : téléphone inconnu ⇒ aucun téléchargement de fond (avant : `null` autorisait le fond)")
        assertNull(g.metered(a, 1))
    }

    @Test fun aPhoneOnWifiLetsTheBackgroundTasksGoAndAPhoneOnMobileDataDoesNot() {
        val g = TvBulkGate()
        g.said(a, pipe = 1, metered = false)
        assertTrue(allowed(g), "téléphone en Wi-Fi, aucune partie : la mise à jour part")
        g.said(a, pipe = 1, metered = true)                                  // il passe sur données mobiles pendant que le tuyau est ouvert
        assertFalse(allowed(g), "données mobiles : le gros volume attend")
        g.said(a, pipe = 1, metered = false)
        assertTrue(allowed(g), "de nouveau sur Wi-Fi")
    }

    @Test fun aGameInProgressKeepsThePipePreciousWhateverTheNetwork() {
        val g = TvBulkGate(); g.said(a, 1, metered = false)
        assertFalse(allowed(g, game = true), "partie en cours ⇒ refusé, même sur Wi-Fi")
        assertTrue(allowed(g, game = false))
    }

    @Test fun theValueIsPerPhoneAndNoOtherPhoneOverwritesIt() {
        val g = TvBulkGate()
        g.said(a, 1, metered = false)
        g.said(b, 1, metered = true)                                         // le RELAY_STATE d'un AUTRE téléphone n'écrase plus rien
        assertTrue(allowed(g, phone = a), "le tuyau est celui de A, sur Wi-Fi")
        assertFalse(allowed(g, phone = b), "et si c'était B qui tenait le tuyau : données mobiles")
        assertEquals(false, g.metered(a, 1)); assertEquals(true, g.metered(b, 1))
    }

    @Test fun whenThePipeIsCutTheValueBecomesUnknownAgain() {
        val g = TvBulkGate(); g.said(a, 1, metered = false)
        assertTrue(allowed(g))
        g.pipeCut()
        assertNull(g.metered(a, 1), "remise à « inconnue » à la coupure du tuyau")
        assertFalse(allowed(g), "le téléphone se reconnecte sans avoir (encore) redit son réseau : facturé")
        g.said(a, 1, metered = false)                                        // il le redit après sa poignée de main
        assertTrue(allowed(g))
    }

    @Test fun whatAPhoneSaidOfAnEarlierPipeDoesNotCountForTheNewOne() {
        // même sans passage par pipeCut (liaison remplacée par le même téléphone) : l'identité du tuyau a changé, l'ancienne réponse ne vaut plus
        val g = TvBulkGate(); g.said(a, pipe = 1, metered = false)
        assertTrue(allowed(g, pipe = 1))
        assertFalse(allowed(g, pipe = 2), "nouveau tuyau, rien dit dessus : facturé")
        assertNull(g.metered(a, 2))
    }

    @Test fun anAnswerGivenOnAnotherPipeIsNeverTakenForTheCurrentOne() {
        val g = TvBulkGate(); g.said(a, pipe = 5, metered = false)
        assertFalse(allowed(g, pipe = 1), "dit sur le tuyau 5, le tuyau en place est le 1")
    }

    @Test fun noPhoneAtAllIsNotAnAnswer() {
        val g = TvBulkGate(); g.said(a, 1, metered = false)
        assertFalse(allowed(g, phone = null, pipe = -1), "aucun téléphone attaché : sur VIA_RELAY c'est inconnu, donc facturé")
        assertNull(g.metered(null, -1))
    }

    @Test fun onlyAPhonesPipeHoldsTheBackgroundTasksBack() {
        val g = TvBulkGate()                                                  // rien dit par personne
        for (game in listOf(true, false)) {
            assertTrue(allowed(g, NetState.DIRECT, game, phone = null, pipe = -1), "le réseau propre de la TV ne les retient jamais (partie=$game)")
            assertTrue(allowed(g, NetState.NONE, game, phone = null, pipe = -1), "sans Internet elles échouent d'elles-mêmes (partie=$game)")
        }
    }

    @Test fun theDecisionIsTheCommonRuleOfTheTvAndThePhone() {
        // la même table que RelayCost.tvBulkAllowed, sur toutes les combinaisons : une seule définition
        val g = TvBulkGate()
        for (net in NetState.values()) for (game in listOf(true, false)) for (m in listOf(null, true, false)) {
            val gate = TvBulkGate(); if (m != null) gate.said(a, 1, m)
            assertEquals(RelayCost.tvBulkAllowed(net, game, m), gate.bulkAllowed(net, game, a, 1), "net=$net partie=$game metered=$m")
        }
        assertFalse(g.bulkAllowed(NetState.VIA_RELAY, false, a, 1))
    }
}
