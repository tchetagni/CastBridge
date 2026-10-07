package castbridge.core.relay

import castbridge.core.connect.NetState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * R-33 (audit anti-régression 2026-10-07 b, I-13) : la garde de coût REL-F7 ne laisse plus passer « inconnu » pour « gratuit ».
 *  - règle commune : réseau dont on ne sait pas s'il est facturé = FACTURÉ ;
 *  - TV : les tâches de fond volumineuses (APK de ~40 Mo, paquets de questions) attendent tant que le téléphone relayeur est facturé OU inconnu (la règle à brancher dans `TvNet`) ;
 *  - téléphone : les octets d'un réseau pas SÛREMENT gratuit comptent contre les 5 Mo du jour ; il redit son réseau à la TV après chaque poignée de main et à chaque changement.
 */
class RelayCostTest {
    // ------------------------------------------------------------------ la règle de la TV

    @Test fun anUnknownNetworkIsBilled() {
        assertTrue(RelayCost.billed(null), "inconnu = facturé")
        assertTrue(RelayCost.billed(true))
        assertFalse(RelayCost.billed(false), "seul « le téléphone a dit : non facturé » est gratuit")
    }

    @Test fun theTvHoldsBackBulkTasksOnARelayUnlessThePhoneSaidItIsNotBilled() {
        assertFalse(RelayCost.tvBulkAllowed(NetState.VIA_RELAY, gameActive = false, phoneMetered = null), "R-33 : téléphone jamais entendu (ou remis à inconnu à la coupure) ⇒ facturé ⇒ le fond attend")
        assertFalse(RelayCost.tvBulkAllowed(NetState.VIA_RELAY, gameActive = false, phoneMetered = true))
        assertTrue(RelayCost.tvBulkAllowed(NetState.VIA_RELAY, gameActive = false, phoneMetered = false), "téléphone sur Wi-Fi, aucune partie : la mise à jour part")
        assertFalse(RelayCost.tvBulkAllowed(NetState.VIA_RELAY, gameActive = true, phoneMetered = false), "une partie en cours garde le tuyau précieux")
    }

    @Test fun theTvOwnNetworkIsNeverHeldBack() {
        for (g in listOf(true, false)) for (m in listOf(null, true, false)) assertTrue(RelayCost.tvBulkAllowed(NetState.DIRECT, g, m), "direct, partie=$g, facturé=$m")
        assertTrue(RelayCost.tvBulkAllowed(NetState.NONE, gameActive = false, phoneMetered = null), "sans Internet la tâche échoue d'elle-même : la règle ne l'empêche pas")
    }

    // ------------------------------------------------------------------ la règle du téléphone

    @Test fun theMeteredFlagIsAbsentWhenTheNetworkIsUnknown() {
        assertNull(PhoneNet.NONE.meteredFlag())
        assertEquals(true, PhoneNet.METERED.meteredFlag())
        assertEquals(false, PhoneNet.UNMETERED.meteredFlag())
    }

    @Test fun everyNetworkThatIsNotSurelyFreeCountsAgainstTheCapOfTheDay() {
        assertTrue(RelayCost.countsAgainstCap(PhoneNet.METERED, allowMobile = false))
        assertTrue(RelayCost.countsAgainstCap(PhoneNet.NONE, allowMobile = false), "R-33 : réseau inconnu (données mobiles pas encore validées) = facturé : ses octets comptent")
        assertFalse(RelayCost.countsAgainstCap(PhoneNet.UNMETERED, allowMobile = false))
        for (n in PhoneNet.values()) assertFalse(RelayCost.countsAgainstCap(n, allowMobile = true), "« Données mobiles pour la TV » lève le plafond : rien à compter ($n)")
    }

    // ------------------------------------------------------------------ ce que le téléphone dit à la TV

    private fun said(metered: Boolean) = RelayFrames.State(RelayFrames.Phase.OPEN, metered = metered)

    @Test fun theNetworkIsToldAfterEveryHandshakeBecauseTheTvForgetsItAtEveryDisconnection() {
        val a = MeteredAnnouncer(minGapMs = 5_000)
        a.connected()
        assertEquals(said(true), a.next(PhoneNet.METERED, 1_000), "première poignée de main : la TV ne sait rien")
        assertNull(a.next(PhoneNet.METERED, 20_000), "déjà dit pendant cette liaison")
        a.connected()                                            // le lien est tombé et revenu : la TV a tout oublié
        assertEquals(said(true), a.next(PhoneNet.METERED, 40_000), "R-33 : redit à la reconnexion (avant : jamais, la TV restait à « inconnu »)")
    }

    @Test fun aChangeOfNetworkWhileThePipeIsUpIsToldToo() {
        val a = MeteredAnnouncer(minGapMs = 5_000)
        a.connected()
        assertEquals(said(false), a.next(PhoneNet.UNMETERED, 0))
        assertEquals(said(true), a.next(PhoneNet.METERED, 10_000), "Wi-Fi quitté pour les données mobiles : les tâches de fond de la TV doivent attendre")
        assertNull(a.next(PhoneNet.METERED, 15_000))
        assertEquals(said(false), a.next(PhoneNet.UNMETERED, 30_000))
    }

    @Test fun anUnknownNetworkSaysNothingAndTheTvKeepsWhatItKnew() {
        val a = MeteredAnnouncer(minGapMs = 5_000)
        a.connected()
        assertNull(a.next(PhoneNet.NONE, 1_000), "aucun réseau validé : rien de vrai à dire")
        assertEquals(said(true), a.next(PhoneNet.METERED, 10_000))
        assertNull(a.next(PhoneNet.NONE, 20_000))
        assertNull(a.next(PhoneNet.METERED, 30_000), "la TV sait encore « facturé » : pas de répétition")
    }

    @Test fun aLinkThatReconnectsInALoopDoesNotFloodTheTvWithBluetoothConnections() {
        val a = MeteredAnnouncer(minGapMs = 5_000)
        a.connected(); assertNotNull(a.next(PhoneNet.METERED, 100_000))
        a.connected(); assertNull(a.next(PhoneNet.METERED, 101_000), "dit il y a une seconde : pas de rafale")
        a.connected(); assertNull(a.next(PhoneNet.METERED, 103_000))
        assertNotNull(a.next(PhoneNet.METERED, 105_000), "le relevé suivant (toutes les 5 s) le dit")
        assertNull(a.next(PhoneNet.METERED, 110_000))
    }
}
