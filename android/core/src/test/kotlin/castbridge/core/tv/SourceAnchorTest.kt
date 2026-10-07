package castbridge.core.tv

import castbridge.core.lots.MemoryQueueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** R-22 : un fichier partagé depuis Telegram doit survivre à la mort du processus (ancrage durable, reprise, une seule notification). */
class SourceAnchorTest {
    private val MB = 1024L * 1024
    private val GB = 1024 * MB
    private fun f(persisted: Boolean = false, media: Boolean = false, size: Long = 348 * MB, free: Long = 50 * GB) = SourceAnchor.Facts(persisted, media, size, free)

    @Test fun orderIsPersistedThenMediaStoreThenCacheThenReshare() {
        assertEquals(Anchor.PERSISTED, SourceAnchor.choose(f(persisted = true, media = true)))
        assertEquals(Anchor.MEDIASTORE, SourceAnchor.choose(f(media = true)))
        assertEquals(Anchor.CACHE, SourceAnchor.choose(f()))
        assertEquals(Anchor.RESHARE, SourceAnchor.choose(f(free = 1 * GB)))
    }

    @Test fun cacheCopyBounds() {
        assertTrue(CacheGuard.fits(348 * MB, 50 * GB))
        assertTrue(CacheGuard.fits(100, 400), "exactement 25 %")
        assertFalse(CacheGuard.fits(101, 400), "au-delà de 25 %")
        assertFalse(CacheGuard.fits(3 * GB, 100 * GB), "plus de 2 Go")
        assertTrue(CacheGuard.fits(2 * GB, 100 * GB))
        assertFalse(CacheGuard.fits(0, 100 * GB), "taille inconnue")
        assertFalse(CacheGuard.fits(10, 0))
    }

    @Test fun reshareReasonSaysWhy() {
        assertTrue("inconnue" in SourceAnchor.reshareReason(0))
        assertTrue("2 Go" in SourceAnchor.reshareReason(3 * GB))
        assertTrue("place" in SourceAnchor.reshareReason(500 * MB))
    }

    private fun cand(n: String, s: Long, u: String) = MediaMatch.Candidate(u, n, s)

    @Test fun mediaMatchNeedsNameSizeAndHead() {
        val head = ByteArray(MediaMatch.HEAD_BYTES) { (it % 251).toByte() }
        val other = head.copyOf().also { it[65_000] = 9 }
        val all = listOf(cand("a.mp4", 10, "u-size"), cand("b.mp4", 5, "u-name"), cand("a.mp4", 5, "u-twin"), cand("a.mp4", 5, "u-ok"))
        assertEquals(listOf("u-twin", "u-ok"), MediaMatch.candidates(all, "a.mp4", 5).map { it.uri })
        // même nom et même taille mais début différent : refusé ; le suivant, identique, est pris
        val picked = MediaMatch.pick(all, "a.mp4", 5, head) { c -> if (c.uri == "u-twin") other else head }
        assertEquals("u-ok", picked?.uri)
        assertNull(MediaMatch.pick(all, "a.mp4", 5, head) { other }, "jamais sur nom+taille seuls")
        assertNull(MediaMatch.pick(all, "a.mp4", 5, head) { null }, "illisible = pas trouvé")
        assertNull(MediaMatch.pick(all, "a.mp4", 0, head) { head }, "taille inconnue : pas de correspondance")
        assertFalse(MediaMatch.sameHead(ByteArray(0), ByteArray(0)))
    }

    @Test fun resumeAcceptsOnlyAUniqueNameSizeMatch() {
        val one = listOf(cand("a.mp4", 5, "u1"), cand("a.mp4", 6, "u2"))
        assertEquals("u1", MediaMatch.unique(one, "a.mp4", 5)?.uri)
        assertNull(MediaMatch.unique(one + cand("a.mp4", 5, "u3"), "a.mp4", 5), "deux fichiers pareils : ambigu")
        assertNull(MediaMatch.unique(one, "zzz.mp4", 5))
    }

    @Test fun aCopyInProgressIsNeverAnOrphan() {
        val m = TransferQueueModel(); val a = m.enqueue("u1", "a", 10, false)
        m.preparing(a.id, 30)
        assertEquals(setOf("${a.id}"), m.cacheReferenced { it })
    }

    @Test fun orphansAreFilesNoItemUses() {
        assertEquals(listOf("3", "9"), CacheGuard.orphans(listOf("1", "3", "9"), setOf("1")))
    }

    @Test fun groupedNotificationIsOneTextForManyFiles() {
        val five = (1..5).map { "Ep$it.mp4" }
        assertEquals("CastBridge : 5 fichiers à repartager", ReshareTexts.groupTitle(five))
        assertTrue("Ep1.mp4" in ReshareTexts.groupText(five))
        assertTrue("« Ep1.mp4 » est à repartager" in ReshareTexts.groupTitle(five.take(1)))
        assertEquals("", ReshareTexts.groupTitle(emptyList()))
    }

    @Test fun lostMessageNamesTelegram() {
        val m = ReshareTexts.lost("content://org.telegram.messenger.provider/media/x.mp4")
        assertEquals("Le téléphone n'a plus accès à ce fichier (partagé depuis Telegram) : rouvrez-le avec « Ouvrir avec CastBridge »", m)
        assertTrue("autre application" in ReshareTexts.lost("content://com.foo/x"))
        assertFalse("sender" in m.lowercase())
    }

    @Test fun queueKeepsOriginalAndDurableUriAndSurvivesRestart() {
        val st = MemoryQueueStore(); val m = TransferQueueModel(store = st)
        val a = m.enqueue("content://org.telegram.messenger.provider/a", "a.mp4", 10, false)
        m.preparing(a.id, 0)
        assertNull(m.next(), "en préparation : ne part pas")
        assertEquals(1, m.position(a.id))
        m.preparing(a.id, 40); assertEquals(40, m.item(a.id)!!.prep)
        m.setAnchor(a.id, Anchor.CACHE, "file:///c/queue/1")
        assertEquals(a.id, m.next()!!.id)
        val back = TransferQueueModel(store = st).item(a.id)!!
        assertEquals("file:///c/queue/1", back.source)
        assertEquals("content://org.telegram.messenger.provider/a", back.uri)
        assertEquals(Anchor.CACHE, back.anchor)
        assertEquals(-1, back.prep)
    }

    @Test fun restartDuringPreparationFailsToReshareWithoutLoop() {
        val st = MemoryQueueStore(); val m = TransferQueueModel(store = st)
        val a = m.enqueue("content://org.telegram.messenger.provider/a", "a.mp4", 10, false)
        m.preparing(a.id, 0)
        val back = TransferQueueModel(store = st)
        val x = back.item(a.id)!!
        assertEquals(QueueStatus.FAILED, x.status)
        assertEquals(Anchor.RESHARE, x.anchor)
        assertTrue("Telegram" in x.error!!)
        assertNull(back.next(), "aucune boucle")
    }

    @Test fun reshareThenPickAgainRequeues() {
        val m = TransferQueueModel()
        val a = m.enqueue("content://x/a", "a.mp4", 10, false); val b = m.enqueue("content://x/b", "b.mp4", 10, false)
        m.reshare(a.id, ReshareTexts.lost(a.uri)); m.reshare(b.id, ReshareTexts.lost(b.uri))
        assertEquals(2, m.toReshare().size)
        assertTrue(m.reanchor(a.id, "content://com.android.providers.media.documents/document/video%3A1"))
        assertEquals(1, m.toReshare().size)
        assertEquals(QueueStatus.WAITING, m.item(a.id)!!.status)
        assertEquals(Anchor.PERSISTED, m.item(a.id)!!.anchor)
        assertFalse(m.reanchor(a.id, "x"), "plus en échec")
    }

    @Test fun cacheReferencedIgnoresFinishedItems() {
        val m = TransferQueueModel()
        val a = m.enqueue("u1", "a", 10, false); val b = m.enqueue("u2", "b", 10, false)
        m.setAnchor(a.id, Anchor.CACHE, "file:///c/queue/${a.id}"); m.setAnchor(b.id, Anchor.CACHE, "file:///c/queue/${b.id}")
        m.start(a.id); m.finish(a.id, true)
        assertEquals(setOf("${b.id}"), m.cacheReferenced { u -> u.substringAfterLast('/') })
    }
}
