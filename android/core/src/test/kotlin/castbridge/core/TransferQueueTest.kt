package castbridge.core

import castbridge.core.tv.QueueStatus
import castbridge.core.tv.TransferQueueModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TransferQueueTest {
    private fun q() = TransferQueueModel()

    @Test fun filesGoOneAtATimeInOrder() {
        val m = q(); val a = m.enqueue("u1", "a.mp4", 10, false); val b = m.enqueue("u2", "b.mp4", 20, true)
        assertEquals(a.id, m.next()!!.id)
        m.start(a.id)
        assertNull(m.next(), "nothing else starts while one runs")
        m.finish(a.id, true)
        assertEquals(b.id, m.next()!!.id)
        assertTrue(m.next()!!.move)
    }

    @Test fun aFailedFileNeverBlocksTheFollowingOnes() {
        val m = q(); val a = m.enqueue("u1", "a", 1, false); val b = m.enqueue("u2", "b", 1, false)
        m.start(a.id); m.finish(a.id, false, "Fichier illisible")
        assertEquals("Fichier illisible", m.items().first { it.id == a.id }.error)
        assertEquals(b.id, m.next()!!.id)
    }

    @Test fun theSameFileIsNotQueuedTwiceWhileWaitingOrRunning() {
        val m = q(); val a = m.enqueue("u1", "a", 1, false)
        assertEquals(a.id, m.enqueue("u1", "a", 1, false).id)
        // R-09 (audit) : la même entrée en DÉPLACEMENT est refusée avec sa raison, plus jamais confondue avec la copie
        kotlin.test.assertFailsWith<castbridge.core.tv.QueueRefused> { m.enqueue("u1", "a", 1, true) }
        m.start(a.id); assertEquals(a.id, m.enqueue("u1", "a", 1, false).id)
        m.finish(a.id, true)
        assertTrue(m.enqueue("u1", "a", 1, false).id != a.id, "after it is done, it can be sent again")
    }

    @Test fun cancelWaitingRemovesAtOnceCancelRunningAsksTheRunner() {
        val m = q(); val a = m.enqueue("u1", "a", 1, false); val b = m.enqueue("u2", "b", 1, false); val c = m.enqueue("u3", "c", 1, false)
        m.start(a.id)
        assertFalse(m.cancel(b.id)); assertEquals(QueueStatus.CANCELLED, m.items().first { it.id == b.id }.status)
        assertTrue(m.cancel(a.id), "a running file needs the runner to stop the transfer")
        m.finishCancelled(a.id)
        assertEquals(c.id, m.next()!!.id)
        assertEquals(1, m.cancelWaiting()); assertNull(m.next()); assertFalse(m.busy())
    }

    @Test fun waitingTextAndHistoryLimit() {
        val m = TransferQueueModel(keepFinished = 3)
        assertNull(m.waitingText())
        repeat(8) { i -> val x = m.enqueue("u$i", "f$i", 1, false); m.start(x.id); m.finish(x.id, true) }
        assertEquals(3, m.items().size, "old finished files are forgotten")
        m.enqueue("w1", "w", 1, false); assertEquals("1 fichier en attente", m.waitingText())
        m.enqueue("w2", "w", 1, false); assertEquals("2 fichiers en attente", m.waitingText())
        m.clearFinished(); assertEquals(2, m.items().size)
    }

    @Test fun aRunnerThatCrashedLeavesNoFileRunningForever() {
        val m = TransferQueueModel()
        val a = m.enqueue("u1", "a.mp4", 1, false); val b = m.enqueue("u2", "b.mp4", 1, false)
        assertTrue(m.start(a.id))
        assertNull(m.next(), "while a runs, nothing else starts: the wedge")
        val gone = m.abandonRunning("Copie impossible : erreur inattendue")
        assertEquals(listOf(a.id), gone.map { it.id })
        assertEquals(QueueStatus.FAILED, m.item(a.id)?.status); assertEquals("Copie impossible : erreur inattendue", m.item(a.id)?.error)
        assertEquals(b.id, m.next()?.id, "the next file goes")
        assertEquals(QueueStatus.WAITING, m.item(b.id)?.status)
        assertTrue(m.abandonRunning("x").isEmpty())
    }
}
