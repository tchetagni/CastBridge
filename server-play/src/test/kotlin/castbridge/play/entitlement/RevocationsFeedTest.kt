package castbridge.play.entitlement

import castbridge.core.owner.RevocationNotice
import castbridge.core.owner.RevocationState
import castbridge.core.quiz.online.HostEdition
import castbridge.play.TestRights
import java.io.File
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

    @Test fun theFeedIsNotUsableBeforeTheFirstListAndNotAfterTwentyFourHoursWithoutAFetch() {
        assertFalse(feed.usable(), "aucune liste acceptée : le service ne juge pas les droits")
        body = list(now); assertTrue(feed.refresh()); assertTrue(feed.usable())
        clock = now + 23 * 3_600_000L; assertTrue(feed.usable())
        clock = now + 25 * 3_600_000L; assertFalse(feed.usable(), "plus de 24 h sans liste réussie")
        body = list(clock); assertTrue(feed.refresh()); assertTrue(feed.usable())
    }

    @Test fun theLastValidListIsPersistedAtomicallyAndReadAgainAtStartup() {
        val dir = java.nio.file.Files.createTempDirectory("rev").toFile()
        val file = File(dir, "revocations.txt")
        val a = RevocationsFeed(ring, { body }, { clock }, file = file)
        body = list(now, setOf("k-persisted")); assertTrue(a.refresh())
        assertTrue(file.isFile); assertTrue(dir.list()!!.all { !it.endsWith(".tmp") }, "aucun fichier temporaire laissé")
        val b = RevocationsFeed(ring, { null }, { clock }, file = file)
        assertEquals(setOf("k-persisted"), b.current().keys, "relue au démarrage")
        assertTrue(b.usable(), "la liste relue compte, tant qu'elle a moins de 24 h")
        body = list(now - 5_000, emptySet())
        val c = RevocationsFeed(ring, { body }, { clock }, file = file)
        assertFalse(c.refresh(), "issuedAt monotone : une liste plus ancienne que celle relue est refusée")
        // fichier falsifié ou signé par un inconnu : ignoré, jamais cru
        file.writeText(list(now + 1, setOf("k-evil"), TestRights.rogue))
        assertTrue(RevocationsFeed(ring, { null }, { clock }, file = file).current().keys.isEmpty())
        assertFalse(RevocationsFeed(ring, { null }, { clock }, file = file).usable())
    }
}
