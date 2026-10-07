package castbridge.core.connect

import castbridge.core.tunnel.TunnelConnectivity
import castbridge.core.tunnel.TunnelPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** relay-R1 : une seule vérité « la TV a Internet, et par où » (direct, via_relay, none), consommée partout. */
class ConnectNetStateTest {
    private fun facts(
        checked: Boolean = true, linkUp: Boolean = true, directValidated: Boolean = false, contactRecent: Boolean = false,
        relayConnected: Boolean = false, relayConfirmed: Boolean = false,
    ) = NetFacts(checked, linkUp, directValidated, contactRecent, relayConnected, relayConfirmed)

    @Test fun directWinsWhenTheOwnNetworkIsValidated() {
        assertEquals(NetState.DIRECT, NetStates.of(facts(directValidated = true)))
        assertEquals(NetState.DIRECT, NetStates.of(facts(directValidated = true, relayConnected = true, relayConfirmed = true)), "le réseau propre passe avant le téléphone")
    }

    @Test fun viaRelayNeedsAnOpenPipeThatWasConfirmed() {
        assertEquals(NetState.VIA_RELAY, NetStates.of(facts(relayConnected = true, relayConfirmed = true)))
        assertEquals(NetState.NONE, NetStates.of(facts(relayConnected = true, relayConfirmed = false)), "téléphone attaché mais sans Internet derrière : pas d'Internet")
        assertEquals(NetState.NONE, NetStates.of(facts(relayConnected = false, relayConfirmed = true)), "plus de téléphone : plus de tuyau")
    }

    @Test fun noneWhenNothingWorks() {
        assertEquals(NetState.NONE, NetStates.of(facts()))
        assertFalse(NetState.NONE.up)
        assertTrue(NetState.DIRECT.up && NetState.VIA_RELAY.up)
    }

    @Test fun aRecentDirectAnswerOfTheServerCountsOnlyWhileTheLinkIsUp() {
        // le système n'a pas « validé » le réseau (Google injoignable) mais le serveur CastBridge répond en direct : c'est de l'Internet
        assertEquals(NetState.DIRECT, NetStates.of(facts(contactRecent = true)))
        assertEquals(NetState.NONE, NetStates.of(facts(linkUp = false, contactRecent = true)), "le câble est débranché depuis : la dernière réponse ne vaut plus")
    }

    @Test fun contactIsRecentOnlyWhenDirectOkAndFresh() {
        val now = 10_000_000L
        assertTrue(NetStates.contactRecent(lastOk = true, via = "direct", lastAt = now - 60_000, now = now))
        assertFalse(NetStates.contactRecent(lastOk = true, via = "passerelle", lastAt = now - 60_000, now = now), "une réponse par le téléphone ne prouve rien sur le réseau propre")
        assertFalse(NetStates.contactRecent(lastOk = false, via = "direct", lastAt = now - 60_000, now = now))
        assertFalse(NetStates.contactRecent(lastOk = true, via = "direct", lastAt = now - NetStates.CONTACT_FRESH_MS - 1, now = now), "trop ancienne")
        assertTrue(NetStates.contactRecent(lastOk = true, via = "direct", lastAt = now - NetStates.CONTACT_FRESH_MS, now = now))
        assertFalse(NetStates.contactRecent(lastOk = true, via = "direct", lastAt = 0, now = now), "jamais joint")
        assertFalse(NetStates.contactRecent(lastOk = true, via = "direct", lastAt = now + 5_000, now = now), "horloge reculée : on ne croit pas une date dans le futur")
        assertFalse(NetStates.contactRecent(lastOk = true, via = null, lastAt = now, now = now))
    }

    @Test fun aDirectFailureAfterTheLastContactCancelsTheProof() {
        // R-41 (I-2) : le battement de cœur direct date de 5 minutes, puis un appel réel a ÉCHOUÉ sur le réseau propre : la box a perdu Internet depuis, la preuve ne vaut plus
        val now = 10_000_000L; val contact = now - 5 * 60_000L
        assertTrue(NetStates.contactRecent(true, "direct", contact, now, directFailedAt = 0L), "aucun échec connu : la preuve vaut")
        assertTrue(NetStates.contactRecent(true, "direct", contact, now, directFailedAt = contact - 1), "un échec ANTÉRIEUR au contact : le contact l'a démenti, la preuve vaut")
        assertTrue(NetStates.contactRecent(true, "direct", contact, now, directFailedAt = contact), "au même instant : on garde la preuve (rien ne dit lequel est venu en dernier)")
        assertFalse(NetStates.contactRecent(true, "direct", contact, now, directFailedAt = contact + 1), "un échec POSTÉRIEUR au contact annule la preuve")
        assertFalse(NetStates.contactRecent(true, "direct", contact, now, directFailedAt = now), "même tout juste maintenant")
        // et la vérité qui en découle : avec le lien relié mais ni validation ni preuve, la TV est hors ligne (et le tuyau peut être demandé)
        val failed = NetStates.contactRecent(true, "direct", contact, now, directFailedAt = now - 1_000)
        assertEquals(NetState.NONE, NetStates.of(facts(directValidated = false, contactRecent = failed)))
        assertFalse(NetStates.reachable(facts(directValidated = false, contactRecent = failed)), "le portefeuille ne se dit plus en ligne")
    }

    @Test fun aNewContactAfterTheFailureBringsTheProofBack() {
        val now = 10_000_000L; val failedAt = now - 10 * 60_000L
        assertTrue(NetStates.contactRecent(true, "direct", lastAt = now - 60_000L, now = now, directFailedAt = failedAt), "le serveur a répondu en direct depuis l'échec : la box a retrouvé Internet")
    }

    @Test fun beforeTheFirstMeasureTheTvDoesNotClaimToBeOffline() {
        assertTrue(NetStates.reachable(facts(checked = false)), "optimisme avant la première mesure (comme l'ancien WalletHub.networkUp)")
        assertFalse(NetStates.reachable(facts(checked = true)))
        assertTrue(NetStates.reachable(facts(checked = true, directValidated = true)))
        assertTrue(NetStates.reachable(facts(checked = true, relayConnected = true, relayConfirmed = true)))
    }

    @Test fun wireNamesAreStable() {
        assertEquals(listOf("direct", "via_relay", "none"), NetState.values().map { it.wire })
        assertEquals(NetState.VIA_RELAY, NetState.fromWire("via_relay"))
        assertNull(NetState.fromWire("wifi"))
        assertNull(NetState.fromWire(null))
    }

    @Test fun theTunnelPathIsDerivedFromTheSameTruth() {
        // plus de définition propre : choose() = path(NetStates.of(...)), et path() lit NetState
        assertEquals(TunnelPath.DIRECT, TunnelConnectivity.path(NetState.DIRECT))
        assertEquals(TunnelPath.GATEWAY, TunnelConnectivity.path(NetState.VIA_RELAY, assistPipe = true), "le tuyau du téléphone, quand une assistance l'a demandé (R-45)")
        assertEquals(TunnelPath.OFFLINE, TunnelConnectivity.path(NetState.VIA_RELAY), "sans assistance demandée le tunnel n'emprunte pas le tuyau")
        assertEquals(TunnelPath.OFFLINE, TunnelConnectivity.path(NetState.NONE))
        for (d in listOf(true, false)) for (c in listOf(true, false)) for (ok in listOf(true, false))
            assertEquals(TunnelConnectivity.path(NetStates.of(d, c, ok)), TunnelConnectivity.choose(d, c, ok), "directOk=$d connected=$c gatewayOk=$ok")
    }
}
