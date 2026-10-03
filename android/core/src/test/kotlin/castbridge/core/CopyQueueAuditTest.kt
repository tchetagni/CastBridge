package castbridge.core

import castbridge.core.lots.MemoryQueueStore
import castbridge.core.net.JsonLite
import castbridge.core.tv.MoveInbox
import castbridge.core.tv.QueueCancel
import castbridge.core.tv.QueueRefused
import castbridge.core.tv.QueueStatus
import castbridge.core.tv.TransferQueueModel
import castbridge.core.tv.UploadSlot
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** R-09, correctifs de l'audit Opus de 92b91a1 : réservation atomique, dédoublonnage par TV, annulation avant lancement, suppressions de déplacement en file. */
class CopyQueueAuditTest {

    // (1) réservation atomique de l'envoi : deux départs simultanés, un seul gagne
    @Test fun twoConcurrentReservationsOnlyOneWins() {
        val slot = UploadSlot()
        val n = 6
        repeat(200) { round ->
            val barrier = CyclicBarrier(n)
            val winners = AtomicInteger(); val tokens = java.util.Collections.synchronizedList(ArrayList<Long>())
            val threads = (0 until n).map { i -> Thread { barrier.await(5, TimeUnit.SECONDS); slot.tryReserve("f$i")?.let { winners.incrementAndGet(); tokens += it } } }
            threads.forEach { it.start() }; threads.forEach { it.join(5_000) }
            assertEquals(1, winners.get(), "tour $round : un seul envoi réserve la TV")
            assertTrue(slot.held())
            assertTrue(slot.release(tokens.single())); assertFalse(slot.held())
        }
    }

    @Test fun aLateReleaseOfAnOlderUploadNeverFreesTheCurrentOne() {
        val slot = UploadSlot()
        val t1 = slot.tryReserve("a")!!
        assertNull(slot.tryReserve("b"), "le perdant est refusé explicitement")
        assertTrue(slot.release(t1))
        val t2 = slot.tryReserve("b")!!
        assertFalse(slot.release(t1), "fin tardive de l'ancien envoi")
        assertTrue(slot.held()); assertTrue(slot.holds(t2)); assertEquals("b", slot.holder())
    }

    // (2) dédoublonnage par (uri, TV) et refus explicite si l'action diffère
    @Test fun theSameFileToAnotherTvIsTwoItems() {
        val m = TransferQueueModel()
        val a = m.enqueue("content://v", "v.mkv", 1, false, tvName = "Salon")
        val b = m.enqueue("content://v", "v.mkv", 1, false, tvName = "Chambre")
        assertNotEquals(a.id, b.id, "jamais l'élément d'une autre TV")
        val c = m.enqueue("content://v", "v.mkv", 1, false, linkTv = "AA:BB")
        val d = m.enqueue("content://v", "v.mkv", 1, false, linkTv = "CC:DD")
        assertNotEquals(c.id, d.id)
        val h1 = m.enqueue("content://w", "w.mkv", 1, false, tvName = "x", host = "192.0.2.1:8765")
        val h2 = m.enqueue("content://w", "w.mkv", 1, false, tvName = "x", host = "192.0.2.2:8765")
        assertNotEquals(h1.id, h2.id)
        assertEquals(a.id, m.enqueue("content://v", "v.mkv", 1, false, tvName = "Salon").id, "même fichier, même TV : un seul élément")
    }

    @Test fun aWaitingCopyThenMoveIsRefusedWithItsReason() {
        val m = TransferQueueModel()
        m.enqueue("content://v", "v.mkv", 1, false, tvName = "Salon")
        val e = assertFailsWith<QueueRefused> { m.enqueue("content://v", "v.mkv", 1, true, tvName = "Salon") }
        assertTrue(e.message!!.contains("déjà dans la file en copie"), e.message)
        m.enqueue("content://m", "m.mkv", 1, true)
        val e2 = assertFailsWith<QueueRefused> { m.enqueue("content://m", "m.mkv", 1, false) }
        assertTrue(e2.message!!.contains("déjà dans la file en déplacement"), e2.message)
    }

    // (3) annulation avant le lancement
    @Test fun cancelDecisions() {
        assertEquals(QueueCancel.Action.REMOVE, QueueCancel.onCancel(QueueStatus.WAITING, launched = false))
        assertEquals(QueueCancel.Action.MARK, QueueCancel.onCancel(QueueStatus.RUNNING, launched = false))
        assertEquals(QueueCancel.Action.STOP_UPLOAD, QueueCancel.onCancel(QueueStatus.RUNNING, launched = true))
        assertEquals(QueueCancel.Action.NOTHING, QueueCancel.onCancel(QueueStatus.DONE, launched = true))
        assertFalse(QueueCancel.mayLaunch(cancelAsked = true), "annulé juste avant launch() : rien ne part")
        assertTrue(QueueCancel.mayLaunch(cancelAsked = false))
        assertEquals(QueueCancel.Action.STOP_UPLOAD, QueueCancel.afterLaunch(cancelAsked = true), "annulé entre launch() et son enregistrement : l'envoi est arrêté")
        assertEquals(QueueCancel.Action.NOTHING, QueueCancel.afterLaunch(cancelAsked = false))
        assertFalse(QueueCancel.mayDeleteMoved(cancelled = true, tvHoldsCompleteCopy = true), "déplacement annulé : aucune demande de suppression")
        assertTrue(QueueCancel.mayDeleteMoved(cancelled = false, tvHoldsCompleteCopy = true))
        assertFalse(QueueCancel.mayDeleteMoved(cancelled = false, tvHoldsCompleteCopy = false))
    }

    @Test fun aRunningFileCancelledBeforeItsLaunchIsMarkedForTheRunner() {
        val m = TransferQueueModel()
        val a = m.enqueue("u1", "a", 1, false)
        assertTrue(m.start(a.id))
        assertTrue(m.cancel(a.id), "le fichier en cours : le coureur décide")
        assertTrue(m.cancelAsked(a.id))
        assertFalse(QueueCancel.mayLaunch(m.cancelAsked(a.id)))
        m.finishCancelled(a.id)
        assertFalse(m.cancelAsked(a.id))
    }

    @Test fun aFileCancelledBetweenNextAndStartIsNeverStarted() {
        val m = TransferQueueModel()
        val a = m.enqueue("u1", "a", 1, false)
        val n = m.next()!!
        m.cancel(a.id)
        assertFalse(m.start(n.id), "déjà annulé : le coureur le saute")
        assertEquals(QueueStatus.CANCELLED, m.item(a.id)!!.status)
    }

    // mineur 6 : deux déplacements terminés ne s'écrasent pas
    @Test fun twoFinishedMovesWaitInTurnForTheirDeletion() {
        val inbox = MoveInbox<String>()
        inbox.offer("a.mkv"); inbox.offer("b.mkv")
        assertEquals("a.mkv", inbox.head()); assertEquals(2, inbox.size())
        assertEquals("b.mkv", inbox.done(), "le suivant devient la tête")
        assertNull(inbox.done()); assertEquals(0, inbox.size())
    }

    // mineur 8 + « rien de secret » renforcé
    @Test fun theSavedQueueKeepsTheTvIdentityAndHoldsNoCredential() {
        val pin = "771234"; val token = "tk_9f8e7d6c5b4a"
        val creds = mapOf("Salon" to pin, "AA:BB" to token)            // ce que l'appelant garde en mémoire
        val store = MemoryQueueStore()
        val m = TransferQueueModel(store = store)
        m.enqueue("content://a", "a.mkv", 1, false, tvName = "Salon", host = "192.0.2.1:8765")
        m.enqueue("content://b", "b.mkv", 1, true, linkTv = "AA:BB")
        assertEquals("AA:BB", TransferQueueModel(store = store).items().last().linkTv, "l'identité de la TV survit au redémarrage")
        val text = store.text!!
        creds.values.forEach { v -> assertFalse(text.contains(v), "valeur secrète écrite dans la file") }
        val allowed = setOf("id", "uri", "name", "size", "move", "autoPlay", "progressive", "status", "error", "playOnTv", "ordered", "tvName", "host",
            "target", "enqueuedAt", "attempts", "linkTv")
        @Suppress("UNCHECKED_CAST")
        (JsonLite.obj(text)["items"] as List<Map<String, Any?>>).forEach { assertTrue(allowed.containsAll(it.keys), "champ inattendu : ${it.keys - allowed}") }
    }
}
