package castbridge.play.entitlement

import castbridge.core.owner.RevocationNotice
import castbridge.core.owner.RevocationState
import castbridge.core.quiz.online.HostEdition
import castbridge.play.TestRights
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** La liste signée des révocations relue toutes les 15 min : crue seulement signée par une clé de confiance REVOKE, jamais plus ancienne que la précédente, dernière liste gardée en cas d'échec. */
class RevocationsFeedTest {
    private val now = System.currentTimeMillis()
    private var clock = now
    private var body: String? = null
    private val ring = TrustedIssuers.parse(TestRights.trustedSpec).ring
    private val feed = RevocationsFeed(ring, { body }, { clock })
    private fun list(at: Long, keys: Set<String> = emptySet(), signer: castbridge.core.owner.Ed25519Signer = TestRights.issuer) = RevocationNotice.issue(signer, at, RevocationState(keys = keys))

    @Test fun aSignedListIsTakenAndRevokesTheHostEndToEnd() {
        val eval = HostRightsEvaluator(ring, feed::current)
        assertEquals(HostEdition.PROD, eval.evaluate(TestRights.CODE, listOf(TestRights.PROD), now).edition)
        body = list(now, setOf(TestRights.issuer.keyId))
        assertTrue(feed.refresh())
        assertEquals(HostEdition.NONE, eval.evaluate(TestRights.CODE, listOf(TestRights.PROD), now).edition, "la clé émettrice est révoquée : plus de salle")
    }

    @Test fun aForgedTamperedOrGarbageListIsIgnoredAndTheLastValidOneKeepsApplying() {
        body = list(now, setOf("k-1")); assertTrue(feed.refresh())
        for (bad in listOf(list(now + 1, setOf("k-2"), TestRights.rogue), list(now + 2, setOf("k-3")).dropLast(3) + "AAA", "n'importe quoi", "", null)) {
            body = bad
            assertFalse(feed.refresh(), "refusée : $bad")
        }
        assertEquals(setOf("k-1"), feed.current().keys, "la dernière liste valide s'applique encore")
    }

    @Test fun anOlderListIsRefusedSoARollbackCannotUnrevoke() {
        body = list(now, setOf("k-new")); assertTrue(feed.refresh())
        body = list(now - 10_000, emptySet()); assertFalse(feed.refresh())
        assertEquals(setOf("k-new"), feed.current().keys)
    }

    @Test fun theOrangeNoticeAppearsAfterAnHourWithoutARefresh() {
        assertNull(feed.staleNotice())
        clock = now + 61 * 60_000L
        assertEquals("Révocations non rafraîchies", feed.staleNotice())
        body = list(clock); assertTrue(feed.refresh())
        assertNull(feed.staleNotice())
        clock += 59 * 60_000L; assertNull(feed.staleNotice())
        clock += 2 * 60_000L; assertEquals("Révocations non rafraîchies", feed.staleNotice())
    }

    @Test fun theFetcherRefusesPlainHttpToARemoteHost() {
        assertTrue(runCatching { RevocationsFeed.httpFetcher("http://bridge.sti-cm.com/api/v1/revocations") }.isFailure)
        assertTrue(runCatching { RevocationsFeed.httpFetcher("https://bridge.sti-cm.com/api/v1/revocations") }.isSuccess)
    }
}
