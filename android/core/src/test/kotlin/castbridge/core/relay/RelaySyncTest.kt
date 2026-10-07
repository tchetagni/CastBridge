package castbridge.core.relay

import castbridge.core.trust.SavedTv
import castbridge.core.trust.TrustRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * R-30 (audit anti-régression 2026-10-07 b, B2), seconde moitié : « synchronisé » pour la politique du relais ne dépend plus du seul jeton VIVANT. Un téléphone réveillé après 12 h par
 * « appareil connecté » (CastBridge fermé) a un jeton expiré ; la TV est pourtant enregistrée (« Ajouter ma TV ») : il ne doit pas répondre « non synchronisé ».
 */
class RelaySyncTest {
    private val tv = SavedTv("AA:BB:CC:DD:EE:01", "SMART_TV")
    private val liveToken = TrustRegistry.TOKEN_PREFIX + "a".repeat(64)

    @Test fun aSavedTvWithAnExpiredTokenAndNoCodeIsStillSynchronized() {
        assertTrue(RelaySync.synced(tv, ""), "jeton expiré (rien lu), aucun code tapé : le cas normal d'un téléphone réveillé après 12 h")
        assertTrue(RelaySync.synced(tv, null))
    }

    @Test fun aTvThePhoneDoesNotKnowIsSynchronizedOnlyByAUsableCredential() {
        assertFalse(RelaySync.synced(null, ""), "TV inconnue ou oubliée entre le réveil et la décision, aucun identifiant")
        assertFalse(RelaySync.synced(null, null))
        assertFalse(RelaySync.synced(null, "12345"), "un code de cinq chiffres n'est pas un code")
        assertFalse(RelaySync.synced(null, TrustRegistry.TOKEN_PREFIX + "tronque"), "un jeton tronqué n'est pas utilisable")
        assertTrue(RelaySync.synced(null, "482913"), "un code de six chiffres gardé")
        assertTrue(RelaySync.synced(null, liveToken), "un jeton vivant")
    }

    @Test fun theSavedTvAndTheCredentialAgree() {
        assertTrue(RelaySync.synced(tv, "482913"))
        assertTrue(RelaySync.synced(tv, liveToken))
        assertEquals(setOf(true), setOf(RelaySync.synced(tv, ""), RelaySync.synced(tv, "482913"), RelaySync.synced(tv, liveToken)))
    }

    @Test fun theDecisionOfThePolicyFollowsIt() {
        // bout à bout avec la politique : TV enregistrée, jeton expiré, Wi-Fi : le tuyau s'ouvre ; TV inconnue sans identifiant : « nosync »
        fun decide(synced: Boolean) = RelayPolicy.decide(RelayInput(synced = synced, optedOut = false, net = PhoneNet.UNMETERED, allowMobile = false, usedTodayBytes = 0, need = PipeNeed.PLAY))
        assertEquals(RelayDecision.Open(null), decide(RelaySync.synced(tv, "")))
        assertEquals(RelayDecision.Refuse(RelayReason.NOT_SYNCED), decide(RelaySync.synced(null, "")))
    }
}
