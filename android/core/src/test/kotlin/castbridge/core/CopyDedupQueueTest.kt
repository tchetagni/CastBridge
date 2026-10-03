package castbridge.core

import castbridge.core.lots.QueueStore
import castbridge.core.tv.QueueStatus
import castbridge.core.tv.TransferQueueModel
import kotlin.test.*

/** R-12 : doublons DANS la file du téléphone (même fichier deux fois, même contenu sous deux noms), sans toucher aux règles R-09. */
class CopyDedupQueueTest {
    private val sha = "d".repeat(64)
    private class Mem : QueueStore { var text: String? = null; override fun load() = text; override fun save(json: String) { this.text = json } }

    @Test fun theSameFileTwiceIsOneItem() {
        val m = TransferQueueModel()
        val a = m.enqueue("content://media/1", "film.mkv", 10, false)
        val b = m.enqueue("content://media/1", "film.mkv", 10, false)
        assertEquals(a.id, b.id); assertEquals(1, m.items().size)
    }

    @Test fun theSameContentUnderTwoNamesCollapsesOnceTheFirstIsSent() {
        val m = TransferQueueModel()
        val a = m.enqueue("content://media/1", "film.mkv", 10, false)
        val b = m.enqueue("content://downloads/9", "film (copie).mkv", 10, false)
        val c = m.enqueue("content://media/3", "autre.mkv", 11, false)
        assertEquals(listOf(b.id), m.sameSize(a.id).map { it.id }, "seul un fichier de même taille vaut d'être haché")
        assertTrue(m.sameSize(c.id).isEmpty())
        m.setHash(a.id, sha); m.setHash(b.id, sha)
        assertNull(m.twinOf(b.id), "le premier n'est pas encore envoyé : pas de jumeau")
        assertTrue(m.start(a.id)); m.finish(a.id, true)
        assertEquals(a.id, m.twinOf(b.id)?.id)
        assertTrue(m.start(b.id))
        m.finishSkipped(b.id, "film.mkv", "Même contenu que « film.mkv »")
        val done = m.item(b.id)!!
        assertEquals(QueueStatus.DONE, done.status); assertEquals("film.mkv", done.heldAs); assertNotNull(done.note)
        assertEquals(c.id, m.next()?.id, "la file continue avec le suivant (R-09)")
    }

    @Test fun anotherHashAnotherTvOrAFailedTwinIsNoTwin() {
        val m = TransferQueueModel()
        val a = m.enqueue("u1", "a.mkv", 10, false, tvName = "Salon")
        val b = m.enqueue("u2", "b.mkv", 10, false, tvName = "Chambre")
        val c = m.enqueue("u3", "c.mkv", 10, false, tvName = "Salon")
        m.setHash(a.id, sha); m.setHash(b.id, sha); m.setHash(c.id, "e".repeat(64))
        m.start(a.id); m.finish(a.id, true)
        assertNull(m.twinOf(b.id), "autre TV")
        assertNull(m.twinOf(c.id), "autre contenu")
        val d = m.enqueue("u4", "d.mkv", 10, false, tvName = "Chambre"); m.setHash(d.id, sha)
        m.start(b.id); m.finish(b.id, false, "coupure")
        assertNull(m.twinOf(d.id), "un envoi échoué n'est pas sur la TV")
        assertFalse(m.items().first { it.id == a.id }.sha256.isNullOrEmpty())
        m.setHash(d.id, "pas un hash")
        assertEquals(sha, m.item(d.id)!!.sha256, "un hash invalide n'est jamais retenu")
    }

    @Test fun copyAnywayIsANewForcedItemAndTheHashSurvivesARestart() {
        val store = Mem()
        val m = TransferQueueModel(store = store)
        val a = m.enqueue("u1", "a.mkv", 10, false)
        m.setHash(a.id, sha); m.start(a.id); m.finishSkipped(a.id, "Films/a.mkv", "Déjà sur la TV")
        val again = m.enqueue("u1", "a.mkv", 10, false, force = true)
        assertNotEquals(a.id, again.id); assertTrue(again.force)
        m.dropNote(a.id); assertNull(m.item(a.id)!!.note, "« Copier quand même » n'est proposé qu'une fois")
        val back = TransferQueueModel(store = store)
        assertEquals(sha, back.item(a.id)!!.sha256); assertEquals("Films/a.mkv", back.item(a.id)!!.heldAs); assertTrue(back.item(again.id)!!.force)
    }
}
