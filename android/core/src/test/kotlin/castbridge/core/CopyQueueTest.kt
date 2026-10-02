package castbridge.core

import castbridge.core.lots.MemoryQueueStore
import castbridge.core.tv.QueueRules
import castbridge.core.tv.QueueStatus
import castbridge.core.tv.QueueTexts
import castbridge.core.tv.TransferQueueModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * R-09 « La file d'attente copie ne fonctionne pas » : les règles pures de la file d'envoi du téléphone (CastBridge).
 *  - « Copier et lire » pendant une copie est mis en file et le dit (« Ajouté à la file : n° 2 »), jamais refusé ;
 *  - règle d'ordre : le fichier en cours n'est jamais interrompu ; un « Copier et lire » passe devant les simples copies en attente (FIFO entre eux) ;
 *  - un échec ne bloque pas les suivants, garde sa cause et peut être réessayé (il repart en fin de file) ;
 *  - la file survit à la mort du processus (magasin + horloge factices) : l'envoi en cours attend de nouveau, rien de secret n'est écrit.
 */
class CopyQueueTest {
    private var clock = 1_000L
    private fun q(store: MemoryQueueStore? = null) = TransferQueueModel(now = { clock }, store = store)

    @Test fun copyAndPlayWhileACopyRunsIsQueuedAndSaysItsPlace() {
        val m = q(); val a = m.enqueue("u1", "a.mkv", 10, false); m.start(a.id)
        val b = m.enqueue("u2", "b.mkv", 10, false, playOnTv = true, ordered = true)
        assertEquals(QueueStatus.WAITING, m.item(b.id)!!.status, "mis en file, pas refusé")
        assertEquals(2, m.position(b.id))
        val said = m.admitted(b.id)
        assertTrue(said.startsWith("Ajouté à la file : n° 2"), said)
        assertTrue("a.mkv" in said, "dit ce qu'il attend : $said")
        assertEquals(1, m.position(a.id))
    }

    @Test fun aFileAddedWhenNothingRunsStartsAtOnce() {
        val m = q(); val a = m.enqueue("u1", "a.mkv", 10, false)
        assertEquals(1, m.position(a.id))
        assertFalse(m.admitted(a.id).startsWith("Ajouté à la file"), m.admitted(a.id))
        assertEquals(a.id, m.next()!!.id)
    }

    @Test fun thePlayItemJumpsTheWaitingCopiesButNeverInterruptsTheRunningOne() {
        val m = q()
        val a = m.enqueue("u1", "a", 1, false); m.start(a.id)
        val b = m.enqueue("u2", "b", 1, false)
        val c = m.enqueue("u3", "c", 1, false, playOnTv = true)
        val d = m.enqueue("u4", "d", 1, false, playOnTv = true)
        assertNull(m.next(), "le fichier en cours n'est jamais interrompu")
        assertEquals(2, m.position(c.id), "« Copier et lire » passe devant la copie b")
        assertEquals(3, m.position(d.id)); assertEquals(4, m.position(b.id))
        m.finish(a.id, true)
        assertEquals(c.id, m.next()!!.id); m.start(c.id); m.finish(c.id, true)
        assertEquals(d.id, m.next()!!.id, "FIFO entre les « Copier et lire »"); m.start(d.id); m.finish(d.id, true)
        assertEquals(b.id, m.next()!!.id)
    }

    @Test fun aFailedFileKeepsItsCauseDoesNotBlockAndCanBeRetried() {
        val m = q()
        val a = m.enqueue("u1", "a", 1, false); val b = m.enqueue("u2", "b", 1, false)
        m.start(a.id); m.finish(a.id, false, "Un autre fichier du même nom est déjà sur la TV.")
        assertEquals("Un autre fichier du même nom est déjà sur la TV.", m.item(a.id)!!.error)
        assertEquals(b.id, m.next()!!.id, "l'échec ne bloque pas le suivant")
        m.start(b.id)
        assertTrue(m.retry(a.id), "« Réessayer »")
        val again = m.item(a.id)!!
        assertEquals(QueueStatus.WAITING, again.status); assertNull(again.error); assertEquals(1, again.attempts)
        assertEquals(2, m.position(a.id), "repart en fin de file")
        assertFalse(m.retry(b.id), "on ne réessaie pas un fichier en cours")
        m.finish(b.id, true)
        assertEquals(a.id, m.next()!!.id)
    }

    @Test fun aReleasedFileWaitsAgainAtItsPlace() {
        val m = q(); val a = m.enqueue("u1", "a", 1, false); val b = m.enqueue("u2", "b", 1, false)
        m.start(a.id); m.release(a.id)
        assertEquals(QueueStatus.WAITING, m.item(a.id)!!.status)
        assertEquals(a.id, m.next()!!.id, "il repart le premier quand l'app revient")
        assertEquals(2, m.position(b.id))
    }

    @Test fun aRetriedPlayItemOnlyCopies() {
        val m = q(); val a = m.enqueue("u1", "a", 1, false, playOnTv = true); m.start(a.id); m.finish(a.id, false, "réseau")
        m.retry(a.id)
        assertFalse(m.item(a.id)!!.playOnTv, "plus personne n'attend de le regarder : simple copie")
    }

    @Test fun copyThenCopyAndPlayOfTheSameWaitingFileMakesItThePlayItem() {
        val m = q(); val a = m.enqueue("u1", "a", 1, false); m.start(a.id)
        val b = m.enqueue("u2", "b", 1, false); m.enqueue("u3", "c", 1, false)
        val b2 = m.enqueue("u2", "b", 1, false, playOnTv = true, ordered = true)
        assertEquals(b.id, b2.id, "jamais deux fois le même fichier")
        assertTrue(m.item(b.id)!!.playOnTv); assertTrue(m.item(b.id)!!.ordered)
        assertEquals(2, m.position(b.id))
    }

    @Test fun theQueueSurvivesTheDeathOfTheProcess() {
        val store = MemoryQueueStore()
        val m = q(store)
        val a = m.enqueue("content://a", "a.mkv", 10, false, tvName = "Salon", host = "192.0.2.1:8765", target = "usb-1"); m.start(a.id)
        clock = 2_000L
        val b = m.enqueue("content://b", "b.mkv", 20, true, playOnTv = true, ordered = true)
        val c = m.enqueue("content://c", "c.mkv", 30, false); m.cancel(c.id)
        val d = m.enqueue("content://d", "d.mkv", 40, false)
        assertTrue(store.text != null, "chaque changement est enregistré")

        val back = q(store)                      // nouveau processus
        val items = back.items()
        assertEquals(listOf(a.id, b.id, c.id, d.id), items.map { it.id }, "même ordre")
        val a2 = back.item(a.id)!!
        assertEquals(QueueStatus.WAITING, a2.status, "l'envoi en cours attend de nouveau (reprise par le .part de la TV)")
        assertEquals("Salon", a2.tvName); assertEquals("192.0.2.1:8765", a2.host); assertEquals("usb-1", a2.target); assertEquals(1_000L, a2.enqueuedAt)
        val b2 = back.item(b.id)!!
        assertTrue(b2.move); assertTrue(b2.ordered); assertEquals(20, b2.size)
        assertFalse(b2.playOnTv, "après un redémarrage personne n'attend devant la TV : simple copie")
        assertEquals(QueueStatus.CANCELLED, back.item(c.id)!!.status)
        assertEquals(a.id, back.next()!!.id)
        val e = back.enqueue("content://e", "e.mkv", 1, false)
        assertTrue(e.id > d.id, "les numéros continuent")
        assertFalse(store.text!!.contains("pin", ignoreCase = true) || store.text!!.contains("token", ignoreCase = true), "rien de secret dans la file")
    }

    @Test fun aDamagedStoreGivesAnEmptyQueueNotACrash() {
        val back = q(MemoryQueueStore("{pas du json"))
        assertTrue(back.items().isEmpty())
        assertEquals(1L, back.enqueue("u", "a", 1, false).id)
    }

    @Test fun aLostFileFailsWithItsCauseInFrench() {
        val m = q(); val a = m.enqueue("u1", "a", 1, false); val b = m.enqueue("u2", "b", 1, false)
        m.lost(a.id)
        assertEquals(QueueTexts.SOURCE_LOST, m.item(a.id)!!.error)
        assertEquals(b.id, m.next()!!.id)
    }

    /** Parcours P-39 : trois fichiers à la suite, un échoue, un est « copier et lire ». */
    @Test fun journeyThreeFilesOneFailsOneIsCopyAndPlay() {
        val m = q()
        val a = m.enqueue("u1", "a.mkv", 1, false); m.start(m.next()!!.id)
        val b = m.enqueue("u2", "b.mkv", 1, false)
        val c = m.enqueue("u3", "c.mkv", 1, false, playOnTv = true, ordered = true)
        assertTrue(m.admitted(c.id).startsWith("Ajouté à la file : n° 2"), m.admitted(c.id))
        m.finish(a.id, true)
        val n2 = m.next()!!; assertEquals(c.id, n2.id, "celui qu'on veut regarder part ensuite"); m.start(n2.id)
        m.finish(c.id, false, "Un autre envoi du même nom est déjà en cours sur la TV : réessayez plus tard.")
        val n3 = m.next()!!; assertEquals(b.id, n3.id, "l'échec ne bloque pas"); m.start(n3.id); m.finish(b.id, true)
        assertFalse(m.busy(), "la file est vide : le service peut s'arrêter")
        assertEquals(listOf(QueueStatus.DONE, QueueStatus.DONE, QueueStatus.FAILED), listOf(a, b, c).map { m.item(it.id)!!.status })
        assertTrue(m.retry(c.id)); assertTrue(m.busy(), "« Réessayer » relance la file")
        assertEquals(c.id, QueueRules.next(m.items())!!.id)
    }
}
